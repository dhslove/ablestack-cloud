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
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.GlobalLock;

public class DrProjectionScheduler extends ManagerBase implements Configurable {
    private static final int GLOBAL_LOCK_TIMEOUT_SECONDS = 1;
    private static final long INITIAL_DELAY_SECONDS = 10L;

    public static final ConfigKey<Boolean> DrProjectionSchedulerEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "dr.projection.scheduler.enabled", "true", "Enable periodic DR runtime projection and protection view caching.", false);
    public static final ConfigKey<Integer> DrProjectionSchedulerInterval = new ConfigKey<>("Advanced", Integer.class,
            "dr.projection.scheduler.interval", "10", "DR runtime projection interval in seconds.", false);
    public static final ConfigKey<Integer> DrProjectionSchedulerBatchSize = new ConfigKey<>("Advanced", Integer.class,
            "dr.projection.scheduler.batch.size", "25", "Maximum DR plans projected in one scheduler tick.", false);

    @Inject private DrPlanDao drPlanDao;
    @Inject private DrProtectionViewService drProtectionViewService;
    private ScheduledExecutorService executor;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("DrProjectionScheduler"));
        return true;
    }

    @Override
    public boolean start() {
        int interval = Math.max(5, DrProjectionSchedulerInterval.value());
        executor.scheduleWithFixedDelay(new ProjectionTask(), INITIAL_DELAY_SECONDS, interval, TimeUnit.SECONDS);
        logger.info(String.format("Started DR projection scheduler with interval %s seconds", interval));
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
        return DrProjectionScheduler.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {DrProjectionSchedulerEnabled, DrProjectionSchedulerInterval, DrProjectionSchedulerBatchSize};
    }

    private final class ProjectionTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            if (!Boolean.TRUE.equals(DisasterRecoveryClusterService.DisasterRecoveryServiceEnabled.value())
                    || !Boolean.TRUE.equals(DrProjectionSchedulerEnabled.value())) {
                return;
            }
            GlobalLock lock = GlobalLock.getInternLock("DrProjectionScheduler");
            try {
                if (lock.lock(GLOBAL_LOCK_TIMEOUT_SECONDS)) {
                    try {
                        projectPlans();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("Failed to run DR projection scheduler", e);
            } finally {
                lock.releaseRef();
            }
        }
    }

    private void projectPlans() {
        List<DrPlanVO> plans = drPlanDao.listActive();
        int remaining = Math.max(1, DrProjectionSchedulerBatchSize.value());
        for (DrPlanVO plan : plans) {
            if (remaining-- <= 0) {
                break;
            }
            try {
                drProtectionViewService.refreshProjectionAndView(plan.getId(), true);
            } catch (RuntimeException e) {
                logger.warn(String.format("Failed to project DR plan %s", plan.getId()), e);
            }
        }
    }
}
