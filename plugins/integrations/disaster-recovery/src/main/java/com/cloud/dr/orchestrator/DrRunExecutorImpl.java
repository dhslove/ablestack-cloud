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
package com.cloud.dr.orchestrator;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.inject.Inject;

import org.apache.cloudstack.managed.context.ManagedContextRunnable;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloud.dr.DrConstants;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrProjectionService;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterRegistry;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrExecutionContext;
import com.cloud.dr.adapter.DrFencingAdapter;
import com.cloud.dr.adapter.DrReplicationEngine;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;

public class DrRunExecutorImpl extends ManagerBase implements DrRunExecutor {
    private static final Logger LOGGER = LogManager.getLogger(DrRunExecutorImpl.class);
    private static final String STEP_EXECUTE = "execute";
    private static final String STEP_DISPATCH = "dispatch-agent";

    @Inject
    private DrPlanDao drPlanDao;
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrEventDao drEventDao;
    @Inject
    private DrAdapterRegistry drAdapterRegistry;
    @Inject
    private DrProjectionService drProjectionService;
    @Inject
    private DrProtectionOrchestrator drProtectionOrchestrator;

    private ExecutorService dispatchExecutor;

    @Override
    public boolean start() {
        dispatchExecutor = Executors.newFixedThreadPool(2, new NamedThreadFactory("DrRunDispatcher"));
        return true;
    }

    @Override
    public boolean stop() {
        if (dispatchExecutor != null) {
            dispatchExecutor.shutdownNow();
            dispatchExecutor = null;
        }
        return true;
    }

    @Override
    public void queueRun(DrRunVO run) {
        if (run == null) {
            return;
        }
        final long runId = run.getId();
        ExecutorService executor = dispatchExecutor;
        if (executor == null) {
            executeRunInternal(runId);
            return;
        }
        try {
            executor.submit(new ManagedContextRunnable() {
                @Override
                protected void runInContext() {
                    executeRunInternal(runId);
                }
            });
        } catch (RejectedExecutionException e) {
            DrRunVO latestRun = drRunDao.findById(runId);
            if (latestRun != null) {
                failRun(latestRun, DrConstants.ERROR_ENGINE_BUSY, "DR dispatcher is not accepting new work: " + e.getMessage(), null);
            }
        }
    }

    private void executeRunInternal(long runId) {
        DrRunVO latestRun = drRunDao.findById(runId);
        if (latestRun == null || latestRun.getRemoved() != null || latestRun.getCompleted() != null) {
            return;
        }
        if (StringUtils.equals(DrConstants.RUN_STATE_CANCEL_REQUESTED, latestRun.getState())) {
            cancelRun(latestRun, "DR run was canceled before dispatch");
            return;
        }
        if (!StringUtils.equalsAny(latestRun.getState(), DrConstants.RUN_STATE_QUEUED, DrConstants.RUN_STATE_DISPATCHING,
                DrConstants.RUN_STATE_ACCEPTED)) {
            return;
        }

        DrPlanVO plan = drPlanDao.findById(latestRun.getPlanId());
        if (plan == null || plan.getRemoved() != null) {
            failRun(latestRun, DrConstants.ERROR_PLAN_NOT_FOUND, "DR plan was not found for run " + latestRun.getId(), null);
            return;
        }

        LOGGER.debug("Executing DR run {} of type {} for plan {}", latestRun.getId(), latestRun.getRunType(), latestRun.getPlanId());
        markRunDispatching(latestRun);
        recordStep(latestRun.getId(), STEP_DISPATCH, 5, DrConstants.STEP_STATE_RUNNING, 10, latestRun.getRequestJson(), null, null);
        markRunRunning(latestRun);
        recordEvent(plan.getId(), latestRun.getId(), DrConstants.EVENT_RUN_STARTED, DrConstants.EVENT_SEVERITY_INFO,
                "DR run started", latestRun.getRequestJson());
        recordStep(latestRun.getId(), STEP_EXECUTE, 10, DrConstants.STEP_STATE_RUNNING, 25, latestRun.getRequestJson(), null, null);

        DrAdapterResult result;
        try {
            plan = prepareProtectionResources(plan, latestRun);
            result = executeAdapter(plan, latestRun);
        } catch (RuntimeException e) {
            LOGGER.warn("DR run {} failed before agent dispatch: {}", latestRun.getId(), e.getMessage(), e);
            failRun(latestRun, classifyExecutionError(e), e.getMessage(), null);
            return;
        }
        if (result != null && result.isSuccess()) {
            if (result.isTerminal()) {
                completeRun(plan, latestRun, result);
            } else {
                acceptRun(plan, latestRun, result);
            }
            return;
        }
        String errorCode = result != null ? result.getErrorCode() : DrConstants.ERROR_ENGINE_ACTION_FAILED;
        String message = result != null ? result.getMessage() : "DR action adapter returned no result";
        String detailsJson = result != null ? result.getDetailsJson() : null;
        failRun(latestRun, errorCode, message, detailsJson);
    }

    private DrAdapterResult executeAdapter(DrPlanVO plan, DrRunVO run) {
        String engineType = StringUtils.defaultIfBlank(plan.getEngineType(), plan.getEngineBindingType());
        String engineBindingType = StringUtils.defaultIfBlank(plan.getEngineBindingType(), plan.getEngineType());
        DrExecutionContext context = new DrExecutionContext(plan, run);

        if (StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_FENCE_CONFIRM)) {
            DrFencingAdapter adapter = drAdapterRegistry.getFencingAdapter(engineType, engineBindingType);
            if (adapter == null) {
                return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNAVAILABLE, "DR fencing adapter is unavailable", null);
            }
            return adapter.confirmFenceClear(context);
        }

        DrReplicationEngine engine = drAdapterRegistry.getReplicationEngine(engineType, engineBindingType);
        if (engine == null) {
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNAVAILABLE, "DR replication engine is unavailable", null);
        }
        DrAdapterResult validation = engine.validatePlan(plan);
        if (validation != null && !validation.isSuccess()) {
            return validation;
        }
        return engine.execute(context);
    }

    private DrPlanVO prepareProtectionResources(DrPlanVO plan, DrRunVO run) {
        if (!isFtctlDrSyncRun(plan, run) || drProtectionOrchestrator == null) {
            return plan;
        }
        DrPlanVO prepared = drProtectionOrchestrator.prepareSyncRun(plan, run);
        return prepared != null ? prepared : plan;
    }

    private boolean isFtctlDrSyncRun(DrPlanVO plan, DrRunVO run) {
        return plan != null && run != null
                && StringUtils.equalsIgnoreCase(DrConstants.RUN_TYPE_SYNC, run.getRunType())
                && (StringUtils.equalsIgnoreCase(DrConstants.ENGINE_TYPE_FTCTL_DR, plan.getEngineType())
                        || StringUtils.equalsIgnoreCase(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR, plan.getEngineBindingType()));
    }

    private String classifyExecutionError(RuntimeException e) {
        String message = e != null ? e.getMessage() : null;
        if (StringUtils.startsWith(message, DrConstants.ERROR_TARGET_MAPPING_INVALID)) {
            return DrConstants.ERROR_TARGET_MAPPING_INVALID;
        }
        if (StringUtils.startsWith(message, DrConstants.ERROR_WORKER_BINDING_INVALID)) {
            return DrConstants.ERROR_WORKER_BINDING_INVALID;
        }
        return DrConstants.ERROR_ENGINE_ACTION_FAILED;
    }

    private void markRunDispatching(DrRunVO run) {
        run.setState(DrConstants.RUN_STATE_DISPATCHING);
        run.setCurrentStepName(STEP_DISPATCH);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
    }

    private void markRunRunning(DrRunVO run) {
        run.setState(DrConstants.RUN_STATE_RUNNING);
        if (run.getStarted() == null) {
            run.setStarted(new Date());
        }
        run.setCurrentStepName(STEP_EXECUTE);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
    }

    private void completeRun(DrPlanVO plan, DrRunVO run, DrAdapterResult result) {
        DrRunVO latestRun = drRunDao.findById(run.getId());
        if (latestRun != null && StringUtils.equals(DrConstants.RUN_STATE_CANCEL_REQUESTED, latestRun.getState())) {
            cancelRun(latestRun, "DR run completed after cancel request");
            return;
        }
        recordStep(run.getId(), STEP_DISPATCH, 15, DrConstants.STEP_STATE_SUCCEEDED, 100, result.getDetailsJson(), null, null);
        recordStep(run.getId(), STEP_EXECUTE, 20, DrConstants.STEP_STATE_SUCCEEDED, 100, result.getDetailsJson(), null, null);
        run.setState(DrConstants.RUN_STATE_SUCCEEDED);
        run.setCompleted(new Date());
        run.setCurrentStepName("completed");
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        recordEvent(plan.getId(), run.getId(), DrConstants.EVENT_RUN_SUCCEEDED, DrConstants.EVENT_SEVERITY_INFO,
                StringUtils.defaultIfBlank(result.getMessage(), "DR run succeeded"), result.getDetailsJson());
        refreshProjection(plan.getId());
    }

    private void acceptRun(DrPlanVO plan, DrRunVO run, DrAdapterResult result) {
        recordStep(run.getId(), STEP_DISPATCH, 15, DrConstants.STEP_STATE_SUCCEEDED, 100, result.getDetailsJson(), null, null);
        recordStep(run.getId(), STEP_EXECUTE, 20, DrConstants.STEP_STATE_SUCCEEDED, 100, result.getDetailsJson(), null, null);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        run.setCurrentStepName("agent-accepted");
        run.setExternalJobRef(result.getExternalJobRef());
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        recordEvent(plan.getId(), run.getId(), DrConstants.EVENT_RUN_ACCEPTED, DrConstants.EVENT_SEVERITY_INFO,
                StringUtils.defaultIfBlank(result.getMessage(), "DR run accepted by Agent"), result.getDetailsJson());
        refreshProjection(plan.getId());
    }

    private void failRun(DrRunVO run, String errorCode, String message, String detailsJson) {
        recordStep(run.getId(), STEP_EXECUTE, 20, DrConstants.STEP_STATE_FAILED, 100, detailsJson, errorCode, message);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setCompleted(new Date());
        run.setCurrentStepName("failed");
        run.setErrorCode(errorCode);
        run.setErrorMessage(message);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_FAILED, DrConstants.EVENT_SEVERITY_ERROR,
                message, detailsJson);
    }

    private void cancelRun(DrRunVO run, String message) {
        recordStep(run.getId(), STEP_EXECUTE, 20, DrConstants.STEP_STATE_CANCELED, 100, null, null, message);
        run.setState(DrConstants.RUN_STATE_CANCELED);
        run.setCompleted(new Date());
        run.setCurrentStepName("canceled");
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_CANCELED, DrConstants.EVENT_SEVERITY_WARN,
                StringUtils.defaultIfBlank(message, "DR run canceled"), null);
    }

    private void recordStep(long runId, String stepName, int stepOrder, String state, Integer progress, String detailsJson,
            String errorCode, String errorMessage) {
        DrRunStepVO step = new DrRunStepVO(runId, stepName, stepOrder);
        step.setState(state);
        step.setProgress(progress);
        step.setDetailsJson(detailsJson);
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        if (StringUtils.equals(DrConstants.STEP_STATE_RUNNING, state)) {
            step.setStarted(new Date());
        }
        if (StringUtils.equalsAny(state, DrConstants.STEP_STATE_SUCCEEDED, DrConstants.STEP_STATE_FAILED, DrConstants.STEP_STATE_CANCELED)) {
            step.setCompleted(new Date());
        }
        drRunStepDao.persist(step);
    }

    private void recordEvent(Long planId, Long runId, String eventType, String severity, String message, String detailsJson) {
        DrEventVO event = new DrEventVO(eventType, severity, DrConstants.EVENT_SOURCE_CLOUD);
        event.setPlanId(planId);
        event.setRunId(runId);
        event.setMessage(message);
        event.setDetailsJson(detailsJson);
        drEventDao.persist(event);
    }

    private void refreshProjection(long planId) {
        try {
            drProjectionService.refreshPlanProjection(planId, true);
        } catch (RuntimeException e) {
            LOGGER.debug("Ignoring best-effort DR projection refresh failure for plan {}: {}", planId, e.getMessage());
        }
    }
}
