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
package com.cloud.dr.adapter.ftctl;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrProjectionAdapter;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.utils.DateUtil;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FtctlDrRuntimeProjectionAdapter extends ManagerBase implements DrProjectionAdapter {
    private static final Gson GSON = new Gson();

    @Inject
    private AgentManager agentManager;
    @Inject
    private DrPlanDao drPlanDao;
    @Inject
    private DrEventDao drEventDao;
    @Inject
    private DrRestorePointDao drRestorePointDao;
    @Inject
    private DrReplicaDao drReplicaDao;
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;

    @Override
    public String getEngineType() {
        return DrConstants.ENGINE_TYPE_FTCTL_DR;
    }

    @Override
    public String getEngineBindingType() {
        return DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR;
    }

    @Override
    public DrAdapterResult refreshPlanProjection(DrPlanVO plan) {
        Long hostId = resolveCoordinatorHostId(plan);
        if (hostId == null) {
            String message = "FTCTL_DR projection requires a coordinator, source, or target worker host";
            return DrAdapterResult.failure(DrConstants.ERROR_TARGET_MAPPING_INVALID, message, GSON.toJson(buildDetails(plan, null, null)));
        }

        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(), null);
        command.setWait(30);
        Answer answer = agentManager.easySend(hostId, command);
        if (!(answer instanceof FtctlDrStatusAnswer)) {
            String message = answer != null ? answer.getDetails() : "Agent returned no FTCTL_DR status answer";
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNAVAILABLE, message, GSON.toJson(buildDetails(plan, hostId, null)));
        }

        FtctlDrStatusAnswer status = (FtctlDrStatusAnswer) answer;
        JsonObject details = buildDetails(plan, hostId, status);
        if (!status.getResult()) {
            reconcileAcceptedRunFromStatus(plan, status, parseObject(status.getStatusJson()));
            return DrAdapterResult.failure(StringUtils.defaultIfBlank(status.getErrorCode(), DrConstants.ERROR_ENGINE_ACTION_FAILED),
                    StringUtils.defaultIfBlank(status.getDetails(), "FTCTL_DR status refresh failed"), GSON.toJson(details));
        }

        updatePlanFromStatus(plan, status);
        persistProjectionEvent(plan, details);
        return DrAdapterResult.success("FTCTL_DR runtime projection refreshed", GSON.toJson(details));
    }

    private Long resolveCoordinatorHostId(DrPlanVO plan) {
        if (plan.getCoordinatorWorkerHostId() != null) {
            return plan.getCoordinatorWorkerHostId();
        }
        if (plan.getSourceWorkerHostId() != null) {
            return plan.getSourceWorkerHostId();
        }
        return plan.getTargetWorkerHostId();
    }

    private void updatePlanFromStatus(DrPlanVO plan, FtctlDrStatusAnswer status) {
        boolean changed = false;
        JsonObject runtime = parseObject(status.getStatusJson());
        String planState = toPlanState(status.getState());
        if (StringUtils.isNotBlank(planState) && !StringUtils.equals(planState, plan.getState())) {
            plan.setState(planState);
            changed = true;
        }
        Date sourceCheckpointAt = parseDate(status.getLastSourceCheckpointAt());
        if (sourceCheckpointAt != null) {
            plan.setLastSourceCheckpointAt(sourceCheckpointAt);
            changed = true;
        }
        Date targetDurableAt = parseDate(status.getLastTargetDurableAt());
        if (targetDurableAt != null) {
            plan.setLastTargetDurableAt(targetDurableAt);
            plan.setTargetReadyAt(targetDurableAt);
            changed = true;
        }
        if (status.getTargetReadyRpoSeconds() != null) {
            plan.setTargetReadyRpoSeconds(status.getTargetReadyRpoSeconds());
            changed = true;
        }
        if (StringUtils.isNotBlank(status.getErrorCode())) {
            plan.setLastErrorCode(status.getErrorCode());
            plan.setLastErrorMessage(status.getDetails());
            changed = true;
        }
        if (isFailbackRestoredRuntime(planState, runtime)) {
            if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_READY)) {
                plan.setState(DrConstants.PLAN_STATE_READY);
                changed = true;
            }
            if (!StringUtils.equals(plan.getActiveSide(), "SOURCE")) {
                plan.setActiveSide("SOURCE");
                changed = true;
            }
            updateReplicaRuntimeProjection(plan, status, runtime, DrConstants.REPLICA_STATE_READY,
                    "SOURCE", StringUtils.defaultIfBlank(stringValue(runtime, "target_power_state"),
                            DrConstants.REPLICA_POWER_STATE_POWERED_OFF));
        } else if (isReprotectedRuntime(planState, runtime)) {
            if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_READY)) {
                plan.setState(DrConstants.PLAN_STATE_READY);
                changed = true;
            }
            if (!StringUtils.equals(plan.getActiveSide(), "TARGET")) {
                plan.setActiveSide("TARGET");
                changed = true;
            }
            updateReplicaRuntimeProjection(plan, status, runtime, DrConstants.REPLICA_STATE_READY,
                    "TARGET", StringUtils.defaultIfBlank(stringValue(runtime, "target_power_state"), "POWER_ON_DELEGATED"));
        } else if (isFailedOverRuntime(planState, runtime)) {
            if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_FAILED_OVER)) {
                plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
                changed = true;
            }
            if (!StringUtils.equals(plan.getActiveSide(), "TARGET")) {
                plan.setActiveSide("TARGET");
                changed = true;
            }
            updateReplicaRuntimeProjection(plan, status, runtime, DrConstants.REPLICA_STATE_FAILED_OVER,
                    "TARGET", StringUtils.defaultIfBlank(stringValue(runtime, "target_power_state"), "POWER_ON_DELEGATED"));
        }
        if (changed) {
            plan.markUpdated();
            drPlanDao.update(plan.getId(), plan);
        }
        upsertRestorePointFromStatus(plan, status, runtime);
        reconcileAcceptedRunFromStatus(plan, status, runtime);
    }

    private void updateReplicaRuntimeProjection(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime,
            String replicaState, String activeSide, String powerState) {
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null || replicas.isEmpty()) {
            return;
        }
        String runtimeJson = StringUtils.defaultIfBlank(status.getStatusJson(), GSON.toJson(runtime));
        for (DrReplicaVO replica : replicas) {
            replica.setState(replicaState);
            replica.setActiveSide(activeSide);
            replica.setPowerState(powerState);
            replica.setRuntimeStateJson(runtimeJson);
            replica.markUpdated();
            drReplicaDao.update(replica.getId(), replica);
        }
    }

    private boolean isFailedOverRuntime(String planState, JsonObject runtime) {
        return StringUtils.equals(planState, DrConstants.PLAN_STATE_FAILED_OVER)
                || StringUtils.equalsIgnoreCase(stringValue(runtime, "state"), "FAILED_OVER")
                || StringUtils.equalsIgnoreCase(stringValue(runtime, "state"), "PROMOTED");
    }

    private boolean isFailbackRestoredRuntime(String planState, JsonObject runtime) {
        return StringUtils.equals(planState, DrConstants.PLAN_STATE_READY)
                && StringUtils.equalsIgnoreCase(stringValue(runtime, "active_side"), "SOURCE")
                && (StringUtils.isNotBlank(stringValue(runtime, "failback_session_id"))
                || StringUtils.isNotBlank(stringValue(runtime, "failback_completed_at")));
    }

    private boolean isReprotectedRuntime(String planState, JsonObject runtime) {
        return StringUtils.equals(planState, DrConstants.PLAN_STATE_READY)
                && StringUtils.equalsIgnoreCase(stringValue(runtime, "active_side"), "TARGET")
                && (StringUtils.isNotBlank(stringValue(runtime, "reprotect_session_id"))
                || StringUtils.isNotBlank(stringValue(runtime, "reprotect_completed_at")));
    }

    private void reconcileAcceptedRunFromStatus(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        DrRunVO run = drRunDao != null ? drRunDao.findActiveByPlanId(plan.getId()) : null;
        if (run == null || run.getCompleted() != null || !isProjectableRunState(run)) {
            return;
        }
        if (isRuntimeError(status, runtime)) {
            failRunFromProjection(plan, run, status, runtime);
            return;
        }
        if (isRunSatisfiedByRuntime(plan, run, status, runtime)) {
            completeRunFromProjection(plan, run, status);
        }
    }

    private boolean isProjectableRunState(DrRunVO run) {
        return StringUtils.equalsAny(run.getState(),
                DrConstants.RUN_STATE_ACCEPTED, DrConstants.RUN_STATE_RUNNING, DrConstants.RUN_STATE_DISPATCHING);
    }

    private boolean isRuntimeError(FtctlDrStatusAnswer status, JsonObject runtime) {
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        return !status.getResult() || StringUtils.equalsAny(runtimeState, "ERROR", "FAILED") || StringUtils.isNotBlank(errorCode);
    }

    private boolean isRunSatisfiedByRuntime(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        String runType = StringUtils.upperCase(run.getRunType(), Locale.ROOT);
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String activeSide = StringUtils.upperCase(StringUtils.defaultIfBlank(plan.getActiveSide(), stringValue(runtime, "active_side")), Locale.ROOT);
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_SYNC)) {
            return StringUtils.equalsAny(runtimeState, "SYNCING", "READY", "TARGET_READY", "PAUSED");
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return StringUtils.equals(runtimeState, "TESTING") || StringUtils.isNotBlank(stringValue(runtime, "test_session_id"));
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILOVER)) {
            return StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_FAILED_OVER)
                    || StringUtils.equals(runtimeState, "FAILED_OVER")
                    || StringUtils.isNotBlank(stringValue(runtime, "failover_session_id"));
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILBACK)) {
            return StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_READY)
                    && StringUtils.equals(activeSide, "SOURCE")
                    && StringUtils.isNotBlank(stringValue(runtime, "failback_session_id"));
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_REPROTECT)) {
            return StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_READY)
                    && StringUtils.equals(activeSide, "TARGET")
                    && StringUtils.isNotBlank(stringValue(runtime, "reprotect_session_id"));
        }
        return StringUtils.equalsAny(runType, DrConstants.RUN_TYPE_TEST_CLEANUP, DrConstants.RUN_TYPE_PAUSE_SYNC,
                DrConstants.RUN_TYPE_RESUME_SYNC, DrConstants.RUN_TYPE_RELEASE)
                && StringUtils.equalsAny(runtimeState, "READY", "PAUSED", "RELEASED");
    }

    private void completeRunFromProjection(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status) {
        recordRunProjectionStep(run, DrConstants.STEP_STATE_SUCCEEDED, 100, status.getStatusJson(), null, null);
        run.setState(DrConstants.RUN_STATE_SUCCEEDED);
        run.setCompleted(new Date());
        run.setCurrentStepName("runtime-projection");
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_RUN_SUCCEEDED, DrConstants.EVENT_SEVERITY_INFO,
                "FTCTL_DR runtime accepted state completed", status.getStatusJson());
    }

    private void failRunFromProjection(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(),
                StringUtils.defaultIfBlank(stringValue(runtime, "error_code"), DrConstants.ERROR_ENGINE_ACTION_FAILED));
        String message = StringUtils.defaultIfBlank(status.getDetails(), "FTCTL_DR runtime reported failure");
        recordRunProjectionStep(run, DrConstants.STEP_STATE_FAILED, 100, status.getStatusJson(), errorCode, message);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setCompleted(new Date());
        run.setCurrentStepName("runtime-projection");
        run.setErrorCode(errorCode);
        run.setErrorMessage(message);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_RUN_FAILED, DrConstants.EVENT_SEVERITY_ERROR,
                message, status.getStatusJson());
    }

    private void recordRunProjectionStep(DrRunVO run, String state, Integer progress, String detailsJson,
            String errorCode, String errorMessage) {
        if (drRunStepDao == null) {
            return;
        }
        DrRunStepVO step = new DrRunStepVO(run.getId(), "runtime-projection", 30);
        step.setState(state);
        step.setProgress(progress);
        step.setDetailsJson(detailsJson);
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        step.setStarted(new Date());
        step.setCompleted(new Date());
        drRunStepDao.persist(step);
    }

    private void upsertRestorePointFromStatus(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        Date targetDurableAt = parseDate(status.getLastTargetDurableAt());
        if (targetDurableAt == null) {
            targetDurableAt = parseDate(stringValue(runtime, "last_target_durable_at"));
        }
        if (targetDurableAt == null) {
            return;
        }

        String checkpointSequence = StringUtils.defaultIfBlank(stringValue(runtime, "checkpoint_sequence"),
                StringUtils.defaultIfBlank(stringValue(runtime, "failover_restore_point_sequence"),
                        StringUtils.defaultIfBlank(stringValue(runtime, "failback_restore_point_sequence"),
                                stringValue(runtime, "reprotect_restore_point_sequence"))));
        String checkpointRef = StringUtils.isNotBlank(checkpointSequence) ? "ftctl:" + plan.getUuid() + ":" + checkpointSequence : null;
        String fallbackRef = StringUtils.defaultIfBlank(stringValue(runtime, "test_restore_point_ref"),
                "ftctl:" + plan.getUuid() + ":" + targetDurableAt.getTime());
        String sourceSnapshotRef = StringUtils.defaultIfBlank(stringValue(runtime, "failover_restore_point_ref"),
                StringUtils.defaultIfBlank(stringValue(runtime, "failback_restore_point_ref"),
                        StringUtils.defaultIfBlank(stringValue(runtime, "reprotect_restore_point_ref"),
                                StringUtils.defaultIfBlank(checkpointRef, fallbackRef))));
        DrRestorePointVO restorePoint = drRestorePointDao.findByPlanIdAndSourceSnapshotRef(plan.getId(), sourceSnapshotRef);
        if (restorePoint == null) {
            restorePoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
            restorePoint.setSourceSnapshotRef(sourceSnapshotRef);
            restorePoint.setConsistencyLevel("CRASH_CONSISTENT");
        }

        Date sourceCheckpointAt = parseDate(status.getLastSourceCheckpointAt());
        if (sourceCheckpointAt == null) {
            sourceCheckpointAt = parseDate(stringValue(runtime, "last_source_checkpoint_at"));
        }
        restorePoint.setSourceCreated(sourceCheckpointAt);
        restorePoint.setTargetReadyAt(targetDurableAt);
        restorePoint.setTargetReadyRpoSeconds(firstInteger(status.getTargetReadyRpoSeconds(), integerValue(runtime, "target_ready_rpo_seconds")));
        restorePoint.setState("READY");
        restorePoint.markUpdated();
        if (restorePoint.getId() > 0) {
            drRestorePointDao.update(restorePoint.getId(), restorePoint);
        } else {
            drRestorePointDao.persist(restorePoint);
        }
    }

    private String toPlanState(String runtimeState) {
        String normalized = StringUtils.upperCase(runtimeState, Locale.ROOT);
        if (StringUtils.equalsAny(normalized, "RUNNING", "SYNCING", "SEEDING")) {
            return DrConstants.PLAN_STATE_SYNCING;
        }
        if (StringUtils.equalsAny(normalized, "TARGET_READY", "READY")) {
            return DrConstants.PLAN_STATE_READY;
        }
        if (StringUtils.equalsAny(normalized, "PAUSED")) {
            return DrConstants.PLAN_STATE_PAUSED;
        }
        if (StringUtils.equalsAny(normalized, "TESTING", "TEST_RUNNING")) {
            return DrConstants.PLAN_STATE_TESTING;
        }
        if (StringUtils.equalsAny(normalized, "FAILED_OVER", "PROMOTED")) {
            return DrConstants.PLAN_STATE_FAILED_OVER;
        }
        if (StringUtils.equalsAny(normalized, "ERROR", "FAILED")) {
            return DrConstants.PLAN_STATE_ERROR;
        }
        return null;
    }

    private Date parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return DateUtil.parseTZDateString(value);
        } catch (ParseException e) {
            return DateUtil.parseDateString(TimeZone.getTimeZone("GMT"), value);
        }
    }

    private JsonObject parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private String stringValue(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        return object.get(name).getAsString();
    }

    private Integer integerValue(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer firstInteger(Integer first, Integer fallback) {
        return first != null ? first : fallback;
    }

    private JsonObject buildDetails(DrPlanVO plan, Long hostId, FtctlDrStatusAnswer status) {
        JsonObject details = new JsonObject();
        details.addProperty("engineType", getEngineType());
        details.addProperty("engineBindingType", getEngineBindingType());
        details.addProperty("planId", plan.getId());
        details.addProperty("planUuid", plan.getUuid());
        details.addProperty("agentHostId", hostId);
        if (status != null) {
            JsonObject runtime = parseObject(status.getStatusJson());
            details.addProperty("result", status.getFtctlResult());
            details.addProperty("state", status.getState());
            details.addProperty("step", status.getStep());
            details.addProperty("progress", status.getProgress());
            details.addProperty("lastSourceCheckpointAt", status.getLastSourceCheckpointAt());
            details.addProperty("lastTargetDurableAt", status.getLastTargetDurableAt());
            details.addProperty("targetReadyRpoSeconds", status.getTargetReadyRpoSeconds());
            details.addProperty("eventsOffset", status.getEventsOffset());
            details.addProperty("errorCode", status.getErrorCode());
            details.addProperty("exitCode", status.getExitCode());
            details.addProperty("activeSide", stringValue(runtime, "active_side"));
            details.addProperty("failoverMode", stringValue(runtime, "failover_mode"));
            details.addProperty("failoverSessionId", stringValue(runtime, "failover_session_id"));
            details.addProperty("failoverRestorePointRef", stringValue(runtime, "failover_restore_point_ref"));
            details.addProperty("targetPowerState", stringValue(runtime, "target_power_state"));
            details.addProperty("targetPromotionState", stringValue(runtime, "target_promotion_state"));
            details.addProperty("sourcePowerState", stringValue(runtime, "source_power_state"));
            details.addProperty("sourcePromotionState", stringValue(runtime, "source_promotion_state"));
            details.addProperty("failbackSessionId", stringValue(runtime, "failback_session_id"));
            details.addProperty("failbackRestorePointRef", stringValue(runtime, "failback_restore_point_ref"));
            details.addProperty("reprotectSessionId", stringValue(runtime, "reprotect_session_id"));
            details.addProperty("reprotectRestorePointRef", stringValue(runtime, "reprotect_restore_point_ref"));
            details.addProperty("reverseDirection", stringValue(runtime, "reverse_direction"));
            details.addProperty("rtoActualSeconds", integerValue(runtime, "rto_actual_seconds"));
        }
        return details;
    }

    private void persistProjectionEvent(DrPlanVO plan, JsonObject details) {
        DrEventVO event = new DrEventVO(DrConstants.EVENT_PROJECTION_REFRESH, DrConstants.EVENT_SEVERITY_INFO,
                DrConstants.EVENT_SOURCE_FTCTL_DR);
        event.setPlanId(plan.getId());
        event.setMessage("FTCTL_DR runtime projection refreshed");
        event.setDetailsJson(GSON.toJson(details));
        drEventDao.persist(event);
    }

    private void persistRunProjectionEvent(DrPlanVO plan, DrRunVO run, String eventType, String severity,
            String message, String detailsJson) {
        DrEventVO event = new DrEventVO(eventType, severity, DrConstants.EVENT_SOURCE_FTCTL_DR);
        event.setPlanId(plan.getId());
        event.setRunId(run.getId());
        event.setMessage(message);
        event.setDetailsJson(detailsJson);
        drEventDao.persist(event);
    }
}
