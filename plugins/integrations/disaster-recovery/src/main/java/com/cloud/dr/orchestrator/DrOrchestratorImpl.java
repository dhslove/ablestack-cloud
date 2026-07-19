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

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.DrConstants;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrTestSessionVO;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrTestSessionDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrOrchestratorImpl extends ManagerBase implements DrOrchestrator {
    private static final String INITIAL_STEP = "prepare";

    @Inject
    private DrPlanDao drPlanDao;
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrEventDao drEventDao;
    @Inject
    private DrRunExecutor drRunExecutor;
    @Inject
    private DrTestSessionDao drTestSessionDao;

    @Override
    public DrRunVO createRun(final long planId, final String runType, final String idempotencyKey, final Long requestedByUserId, final Long asyncJobId) {
        return createRun(planId, runType, idempotencyKey, requestedByUserId, asyncJobId, null);
    }

    @Override
    public DrRunVO createRun(final long planId, final String runType, final String idempotencyKey, final Long requestedByUserId, final Long asyncJobId,
            final String requestJson) {
        return Transaction.execute(new TransactionCallback<DrRunVO>() {
            @Override
            public DrRunVO doInTransaction(TransactionStatus status) {
                DrPlanVO plan = requirePlan(planId);
                if (StringUtils.isNotBlank(idempotencyKey)) {
                    DrRunVO existing = drRunDao.findByPlanIdAndIdempotencyKey(planId, idempotencyKey);
                    if (existing != null) {
                        return existing;
                    }
                }
                DrRunVO activeRun = drRunDao.findActiveByPlanId(planId);
                if (activeRun != null && !allowsConcurrentControlRun(runType)) {
                    throw new InvalidParameterValueException(DrConstants.ERROR_ACTIVE_RUN_EXISTS + ": active run exists for plan " + planId);
                }
                DrRunVO run = new DrRunVO(planId, runType);
                run.setState(DrConstants.RUN_STATE_QUEUED);
                run.setIdempotencyKey(idempotencyKey);
                run.setRequestJson(requestJson);
                run.setRequestedByUserId(requestedByUserId);
                run.setAsyncJobId(asyncJobId);
                run.setCurrentStepName(INITIAL_STEP);
                run = drRunDao.persist(run);

                createRequestedTestSession(plan, run, requestJson);

                DrRunStepVO step = new DrRunStepVO(run.getId(), INITIAL_STEP, 0);
                step.setState(DrConstants.STEP_STATE_QUEUED);
                step.setProgress(0);
                drRunStepDao.persist(step);

                plan.setLastRunId(run.getId());
                plan.markUpdated();
                drPlanDao.update(plan.getId(), plan);

                DrEventVO event = new DrEventVO(DrConstants.EVENT_RUN_CREATED, DrConstants.EVENT_SEVERITY_INFO, DrConstants.EVENT_SOURCE_CLOUD);
                event.setPlanId(planId);
                event.setRunId(run.getId());
                event.setMessage("DR run created");
                drEventDao.persist(event);
                return run;
            }
        });
    }

    private void createRequestedTestSession(DrPlanVO plan, DrRunVO run, String requestJson) {
        if (!StringUtils.equals(run.getRunType(), DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return;
        }
        DrTestSessionVO session = drTestSessionDao.findActiveByRunId(run.getId());
        if (session != null) {
            return;
        }
        DrTestSessionVO active = drTestSessionDao.findActiveByPlanId(plan.getId());
        if (active != null && !StringUtils.equalsAny(active.getState(), "CLEANED", "CLEANUP_FAILED")) {
            throw new InvalidParameterValueException("DR_TEST_SESSION_ACTIVE: another Cloud-managed test session is active");
        }
        JsonObject request = parseRequest(requestJson);
        session = new DrTestSessionVO(plan.getId(), run.getId(), "REQUESTED");
        session.setNetworkMode(stringValue(request, "networkMode"));
        session.setNetworkId(longValue(request, "networkId"));
        session.setRestorePointRef(stringValue(request, "restorePointRef"));
        session.setValidationMode(stringValue(request, "validationMode"));
        session.setBootTimeoutSeconds(integerValue(request, "bootTimeoutSeconds"));
        session.setArtifactContractVersion("3");
        session.setCleanupRequired(false);
        session.setDetailsJson(requestJson);
        drTestSessionDao.persist(session);
    }

    private JsonObject parseRequest(String requestJson) {
        if (StringUtils.isBlank(requestJson)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(requestJson).getAsJsonObject();
        } catch (RuntimeException ignored) {
            return new JsonObject();
        }
    }

    private String stringValue(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private Long longValue(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer integerValue(JsonObject object, String key) {
        try {
            return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean allowsConcurrentControlRun(String runType) {
        return StringUtils.equalsAny(runType, DrConstants.RUN_TYPE_PAUSE_SYNC, DrConstants.RUN_TYPE_RESUME_SYNC,
                DrConstants.RUN_TYPE_RELEASE);
    }

    @Override
    public DrRunVO executeRun(long runId) {
        DrRunVO run = requireRun(runId);
        if (run.getCompleted() != null) {
            return run;
        }
        if (!StringUtils.equals(DrConstants.RUN_STATE_QUEUED, run.getState())) {
            return run;
        }
        recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_QUEUED, DrConstants.EVENT_SEVERITY_INFO, DrConstants.EVENT_SOURCE_CLOUD,
                "DR run accepted for asynchronous executor dispatch", null);
        drRunExecutor.queueRun(run);
        return drRunDao.findById(runId);
    }

    @Override
    public DrPlanVO transitionPlan(long planId, String state, String errorCode, String errorMessage) {
        DrPlanVO plan = requirePlan(planId);
        plan.setState(state);
        plan.setLastErrorCode(errorCode);
        plan.setLastErrorMessage(errorMessage);
        plan.markUpdated();
        drPlanDao.update(planId, plan);
        return drPlanDao.findById(planId);
    }

    @Override
    public DrRunStepVO recordStep(long runId, String stepName, int stepOrder, String state, Integer progress, String detailsJson) {
        requireRun(runId);
        DrRunStepVO step = new DrRunStepVO(runId, stepName, stepOrder);
        step.setState(state);
        step.setProgress(progress);
        step.setDetailsJson(detailsJson);
        if (StringUtils.equals(DrConstants.STEP_STATE_RUNNING, state)) {
            step.setStarted(new Date());
        }
        if (StringUtils.equalsAny(state, DrConstants.STEP_STATE_SUCCEEDED, DrConstants.STEP_STATE_FAILED, DrConstants.STEP_STATE_CANCELED)) {
            step.setCompleted(new Date());
        }
        return drRunStepDao.persist(step);
    }

    @Override
    public DrEventVO recordEvent(Long planId, Long runId, String eventType, String severity, String source, String message, String detailsJson) {
        DrEventVO event = new DrEventVO(eventType, severity, source);
        event.setPlanId(planId);
        event.setRunId(runId);
        event.setMessage(message);
        event.setDetailsJson(detailsJson);
        return drEventDao.persist(event);
    }

    @Override
    public DrRunVO handleFailure(long runId, String errorCode, String errorMessage) {
        DrRunVO run = requireRun(runId);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setCompleted(new Date());
        run.markUpdated();
        drRunDao.update(runId, run);
        recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_FAILED, DrConstants.EVENT_SEVERITY_ERROR, DrConstants.EVENT_SOURCE_CLOUD,
                errorMessage, null);
        return drRunDao.findById(runId);
    }

    private DrPlanVO requirePlan(long planId) {
        DrPlanVO plan = drPlanDao.findById(planId);
        if (plan == null || plan.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_PLAN_NOT_FOUND + ": " + planId);
        }
        return plan;
    }

    private DrRunVO requireRun(long runId) {
        DrRunVO run = drRunDao.findById(runId);
        if (run == null || run.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_RUN_NOT_FOUND + ": " + runId);
        }
        return run;
    }
}
