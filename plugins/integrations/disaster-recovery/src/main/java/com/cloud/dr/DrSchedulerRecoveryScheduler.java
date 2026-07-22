// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.dr;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;

import com.cloud.dr.cluster.DisasterRecoveryClusterService;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.GlobalLock;
import com.google.gson.JsonObject;

public class DrSchedulerRecoveryScheduler extends ManagerBase implements Configurable {
    private static final int GLOBAL_LOCK_TIMEOUT_SECONDS = 1;
    private static final long INITIAL_DELAY_SECONDS = 20L;

    public static final ConfigKey<Boolean> DrSchedulerRecoveryEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "dr.scheduler.recovery.enabled", "false",
            "Enable automatic recovery of eligible FTCTL_DR Plan schedulers.", false);
    public static final ConfigKey<Integer> DrSchedulerRecoveryInterval = new ConfigKey<>("Advanced", Integer.class,
            "dr.scheduler.recovery.interval", "30", "DR scheduler recovery evaluation interval in seconds.", false);
    public static final ConfigKey<Integer> DrSchedulerRecoveryBatchSize = new ConfigKey<>("Advanced", Integer.class,
            "dr.scheduler.recovery.batch.size", "25", "Maximum DR scheduler recoveries evaluated per tick.", false);

    @Inject private DrPlanDao drPlanDao;
    @Inject private DrPlanRuntimeDao drPlanRuntimeDao;
    @Inject private DrPlanService drPlanService;
    @Inject private DrRunService drRunService;
    private ScheduledExecutorService executor;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DrSchedulerRecovery"));
        return true;
    }

    @Override
    public boolean start() {
        int interval = Math.max(10, DrSchedulerRecoveryInterval.value());
        executor.scheduleWithFixedDelay(new RecoveryTask(), INITIAL_DELAY_SECONDS, interval, TimeUnit.SECONDS);
        logger.info(String.format("Started DR scheduler recovery controller with interval %s seconds (enabled=%s)",
                interval, DrSchedulerRecoveryEnabled.value()));
        return true;
    }

    @Override
    public boolean stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        return true;
    }

    @Override
    public String getConfigComponentName() {
        return DrSchedulerRecoveryScheduler.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {DrSchedulerRecoveryEnabled, DrSchedulerRecoveryInterval,
                DrSchedulerRecoveryBatchSize};
    }

    private final class RecoveryTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            if (!Boolean.TRUE.equals(DisasterRecoveryClusterService.DisasterRecoveryServiceEnabled.value())
                    || !Boolean.TRUE.equals(DrSchedulerRecoveryEnabled.value())) {
                return;
            }
            GlobalLock lock = GlobalLock.getInternLock("DrSchedulerRecoveryScheduler");
            try {
                if (lock.lock(GLOBAL_LOCK_TIMEOUT_SECONDS)) {
                    try {
                        recoverEligiblePlans();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("Failed to run DR scheduler recovery controller", e);
            } finally {
                lock.releaseRef();
            }
        }
    }

    private void recoverEligiblePlans() {
        List<DrPlanVO> plans = drPlanDao.listActive();
        int remaining = Math.max(1, DrSchedulerRecoveryBatchSize.value());
        for (DrPlanVO plan : plans) {
            if (remaining-- <= 0) {
                break;
            }
            try {
                Map<String, Boolean> eligibility = drPlanService.getActionEligibility(plan.getId());
                if (!Boolean.TRUE.equals(eligibility.get("recoverSync"))) {
                    continue;
                }
                DrPlanRuntimeVO runtime = drPlanRuntimeDao.findByPlanId(plan.getId());
                long authoritySequence = runtime != null ? runtime.getAuthoritySequence() : 0L;
                JsonObject request = new JsonObject();
                request.addProperty("trigger", "AUTO_CONTROLLER");
                request.addProperty("forceFullReseed", false);
                drRunService.startRun(plan.getId(), DrConstants.RUN_TYPE_RECOVER_SYNC,
                        String.format("scheduler-recovery:%s:%s", plan.getUuid(), authoritySequence),
                        null, null, request.toString());
            } catch (RuntimeException e) {
                logger.warn(String.format("Failed to recover DR scheduler for plan %s", plan.getId()), e);
            }
        }
    }
}
