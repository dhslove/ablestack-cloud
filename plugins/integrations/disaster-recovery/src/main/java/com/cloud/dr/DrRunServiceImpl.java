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

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrCancelAnswer;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.orchestrator.DrOrchestrator;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrRunServiceImpl extends ManagerBase implements DrRunService {
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrOrchestrator drOrchestrator;
    @Inject
    private DrPlanDao drPlanDao;
    @Inject
    private AgentManager agentManager;
    @Inject
    private DrFailbackLifecycleService drFailbackLifecycleService;

    @Override
    public DrRunVO startRun(long planId, String runType, String idempotencyKey, Long requestedByUserId, Long asyncJobId) {
        return startRun(planId, runType, idempotencyKey, requestedByUserId, asyncJobId, null);
    }

    @Override
    public DrRunVO startRun(long planId, String runType, String idempotencyKey, Long requestedByUserId, Long asyncJobId, String requestJson) {
        DrRunVO run = drOrchestrator.createRun(planId, runType, idempotencyKey, requestedByUserId, asyncJobId, requestJson);
        return drOrchestrator.executeRun(run.getId());
    }

    @Override
    public DrRunVO getRun(long runId) {
        return requireRun(runId);
    }

    @Override
    public List<DrRunVO> listRuns(long planId) {
        return drRunDao.listByPlanId(planId);
    }

    @Override
    public DrRunVO findRunByIdempotencyKey(long planId, String idempotencyKey) {
        if (StringUtils.isBlank(idempotencyKey)) {
            return null;
        }
        return drRunDao.findByPlanIdAndIdempotencyKey(planId, idempotencyKey);
    }

    @Override
    public List<DrRunStepVO> listRunSteps(long runId) {
        requireRun(runId);
        return drRunStepDao.listActiveByRunId(runId);
    }

    @Override
    public DrRunVO cancelRun(long runId) {
        DrRunVO run = requireRun(runId);
        if (run.getCompleted() != null) {
            return run;
        }
        if (StringUtils.equals(DrConstants.RUN_STATE_QUEUED, run.getState())) {
            run.setState(DrConstants.RUN_STATE_CANCELED);
            run.setCompleted(new Date());
        } else {
            run.setState(DrConstants.RUN_STATE_CANCEL_REQUESTED);
        }
        run.markUpdated();
        drRunDao.update(runId, run);
        drOrchestrator.recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_CANCELED, DrConstants.EVENT_SEVERITY_WARN,
                DrConstants.EVENT_SOURCE_CLOUD, "DR run cancel requested", null);
        if (StringUtils.equals(DrConstants.RUN_STATE_CANCEL_REQUESTED, run.getState())) {
            if (!cancelFtctlRun(run)) {
                return drRunDao.findById(runId);
            }
            DrPlanVO plan = drPlanDao.findById(run.getPlanId());
            if (StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_FAILBACK)
                    && drFailbackLifecycleService != null
                    && !drFailbackLifecycleService.cancelAndRestoreTargetAuthority(plan, run)) {
                return drRunDao.findById(runId);
            }
            // The original executor context may no longer exist after a management
            // server restart. Requeue the cancellation so the durable run is
            // terminalized instead of remaining an active-run blocker forever.
            drOrchestrator.executeRun(runId);
        }
        return drRunDao.findById(runId);
    }

    private boolean cancelFtctlRun(DrRunVO run) {
        DrPlanVO plan = drPlanDao != null ? drPlanDao.findById(run.getPlanId()) : null;
        if (plan == null || !StringUtils.equalsAnyIgnoreCase(DrConstants.ENGINE_TYPE_FTCTL_DR,
                plan.getEngineType(), plan.getEngineBindingType())) {
            return true;
        }
        Long coordinatorHostId = plan.getCoordinatorWorkerHostId() != null
                ? plan.getCoordinatorWorkerHostId()
                : plan.getSourceWorkerHostId() != null ? plan.getSourceWorkerHostId() : plan.getTargetWorkerHostId();
        if (coordinatorHostId == null || agentManager == null) {
            recordCancelDispatchPending(run, "FTCTL_DR cancel requires an available coordinator Agent");
            return false;
        }
        FtctlDrCancelCommand command = new FtctlDrCancelCommand(plan.getUuid(), run.getUuid());
        Answer rawAnswer = agentManager.easySend(coordinatorHostId, command);
        if (!(rawAnswer instanceof FtctlDrCancelAnswer)) {
            recordCancelDispatchPending(run, "FTCTL_DR cancel returned no typed Agent answer");
            return false;
        }
        FtctlDrCancelAnswer answer = (FtctlDrCancelAnswer) rawAnswer;
        boolean identityMatches = StringUtils.equals(plan.getUuid(), answer.getPlanUuid())
                && StringUtils.equals(run.getUuid(), answer.getRunUuid());
        if (!answer.getResult() || !Boolean.TRUE.equals(answer.getAccepted()) || !identityMatches
                || !hasAuthoritativeCancelTerminal(answer.getOutput())) {
            recordCancelDispatchPending(run, StringUtils.defaultIfBlank(answer.getDetails(),
                    "FTCTL_DR cancel did not return authoritative drained terminal evidence"));
            return false;
        }
        return true;
    }

    private boolean hasAuthoritativeCancelTerminal(String output) {
        if (StringUtils.isBlank(output)) {
            return false;
        }
        try {
            JsonObject payload = JsonParser.parseString(output).getAsJsonObject();
            return payload.has("state") && StringUtils.equals("CANCELED", payload.get("state").getAsString())
                    && payload.has("terminal_authoritative") && payload.get("terminal_authoritative").getAsBoolean()
                    && payload.has("runtime_endpoints_drained") && payload.get("runtime_endpoints_drained").getAsBoolean()
                    && payload.has("transfer_activity_state")
                    && StringUtils.equals("CANCELED", payload.get("transfer_activity_state").getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void recordCancelDispatchPending(DrRunVO run, String message) {
        drOrchestrator.recordEvent(run.getPlanId(), run.getId(), DrConstants.EVENT_RUN_CANCELED,
                DrConstants.EVENT_SEVERITY_WARN, DrConstants.EVENT_SOURCE_CLOUD,
                StringUtils.defaultIfBlank(message, "FTCTL_DR cancel dispatch is pending"), null);
    }

    private DrRunVO requireRun(long runId) {
        DrRunVO run = drRunDao.findById(runId);
        if (run == null || run.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_RUN_NOT_FOUND + ": " + runId);
        }
        return run;
    }
}
