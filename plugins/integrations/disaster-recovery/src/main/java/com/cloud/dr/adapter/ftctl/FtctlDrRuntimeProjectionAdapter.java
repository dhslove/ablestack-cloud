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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrCycleSnapshot;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrCutoverDiskVO;
import com.cloud.dr.DrCutoverSessionVO;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrPlanRuntimeVO;
import com.cloud.dr.DrReplicaDiskVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrSyncCycleVO;
import com.cloud.dr.DrTargetMaterializationService;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrProjectionAdapter;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrCutoverDiskDao;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.dr.dao.DrReplicaDiskDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.utils.DateUtil;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FtctlDrRuntimeProjectionAdapter extends ManagerBase implements DrProjectionAdapter {
    private static final Gson GSON = new Gson();
    private static final int RUNTIME_CREATION_GRACE_SECONDS = 120;
    private static final int WORKER_START_GRACE_SECONDS = 60;
    private static final int STEP_ORDER_RUNTIME_PROJECTION = 30;
    private static final int STATUS_REFRESH_WAIT_SECONDS = 5;
    private static final int DEFAULT_CHECKPOINT_RETENTION = 24;

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
    private DrReplicaDiskDao drReplicaDiskDao;
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrTargetMaterializationService drTargetMaterializationService;
    @Inject
    private DrCutoverSessionDao drCutoverSessionDao;
    @Inject
    private DrCutoverDiskDao drCutoverDiskDao;
    @Inject
    private DrPlanRuntimeDao drPlanRuntimeDao;
    @Inject
    private DrSyncCycleDao drSyncCycleDao;

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

        DrRunVO projectionRun = resolveProjectionRun(plan);
        String runUuid = projectionRun != null ? projectionRun.getUuid() : null;
        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(), runUuid);
        command.setWait(STATUS_REFRESH_WAIT_SECONDS);
        Answer answer = agentManager.easySend(hostId, command);
        if (!(answer instanceof FtctlDrStatusAnswer)) {
            String message = answer != null ? answer.getDetails() : "Agent returned no FTCTL_DR status answer";
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNAVAILABLE, message, GSON.toJson(buildDetails(plan, hostId, null)));
        }

        FtctlDrStatusAnswer status = (FtctlDrStatusAnswer) answer;
        JsonObject details = buildDetails(plan, hostId, status);
        if (!isCorrelatedRuntime(plan, projectionRun, status)) {
            details.addProperty("projectionIgnored", true);
            details.addProperty("reason", "STALE_RUNTIME_IGNORED");
            persistRunProjectionEvent(plan, projectionRun, DrConstants.EVENT_PROJECTION_REFRESH,
                    DrConstants.EVENT_SEVERITY_WARN,
                    "Ignored FTCTL_DR status for a different plan/run", GSON.toJson(details));
            return DrAdapterResult.success("Ignored stale FTCTL_DR runtime status", GSON.toJson(details));
        }
        FtctlDrCycleSnapshot latestCompletedCycle = latestCompletedCycle(status);
        if (!isCoherentCycleSnapshot(plan, status, latestCompletedCycle)) {
            markProjectionIntegrityFailure(plan, latestCompletedCycle, "DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT");
            return DrAdapterResult.retryable("DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT",
                    "FTCTL_DR completed cycle snapshot was not coherent; last-good projection was retained",
                    GSON.toJson(details), STATUS_REFRESH_WAIT_SECONDS);
        }
        JsonObject runtimeStatus = parseObject(status.getStatusJson());
        projectProtectionAuthority(plan, projectionRun, status, runtimeStatus);
        if (!status.getResult() && isStatusBoundaryFailure(status)) {
            markProjectionStale(plan, status);
            return DrAdapterResult.retryable(status.getErrorCode(),
                    statusMessage(status, "FTCTL_DR status failed validation; last-good projection was retained"),
                    GSON.toJson(details), STATUS_REFRESH_WAIT_SECONDS);
        }
        if (!hardwareContractMatches(plan, runtimeStatus)) {
            String message = "FTCTL_DR source hardware fingerprint differs from the persisted DR Plan";
            markPlanProjectionFailed(plan, DrConstants.ERROR_SOURCE_HARDWARE_CHANGED, message);
            return DrAdapterResult.failure(DrConstants.ERROR_SOURCE_HARDWARE_CHANGED, message, GSON.toJson(details));
        }
        if (!status.getResult()) {
            JsonObject runtime = parseObject(status.getStatusJson());
            if (isStatusTimeout(status, runtime)) {
                markProjectionStale(plan, status);
                return DrAdapterResult.retryable(DrConstants.ERROR_PROJECTION_STALE,
                        statusMessage(status, "FTCTL_DR status refresh timed out"),
                        GSON.toJson(details), STATUS_REFRESH_WAIT_SECONDS);
            }
            if (deferRuntimeNotFound(plan, status, runtime)) {
                return DrAdapterResult.success("FTCTL_DR runtime is not created yet; projection will retry", GSON.toJson(details));
            }
            reconcileAcceptedRunFromStatus(plan, status, runtime);
            return DrAdapterResult.failure(StringUtils.defaultIfBlank(status.getErrorCode(), DrConstants.ERROR_ENGINE_ACTION_FAILED),
                    projectionFailureMessage(status.getErrorCode(), status, runtime), GSON.toJson(details));
        }

        updatePlanFromStatus(plan, projectionRun, status);
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

    private void projectProtectionAuthority(DrPlanVO plan, DrRunVO projectionRun, FtctlDrStatusAnswer status,
            JsonObject runtime) {
        Long generation = status.getRuntimeGeneration() != null ? status.getRuntimeGeneration()
                : longValue(runtime, "runtime_generation");
        Long sequence = status.getCurrentCheckpointSequence() != null ? status.getCurrentCheckpointSequence()
                : longValue(runtime, "current_checkpoint_sequence");
        if (generation == null) {
            generation = sequence != null ? sequence : 0L;
        }

        DrPlanRuntimeVO authority = drPlanRuntimeDao.findByPlanId(plan.getId());
        if (authority != null && generation < authority.getRuntimeGeneration()) {
            return;
        }
        if (authority == null) {
            authority = new DrPlanRuntimeVO(plan.getId());
        }

        Date now = new Date();
        Date sourceAt = firstDate(parseDate(status.getLastSourceCheckpointAt()),
                parseDate(stringValue(runtime, "last_source_checkpoint_at")));
        Date durableAt = firstDate(parseDate(status.getLatestCompletedTargetDurableAt()),
                parseDate(status.getLastTargetDurableAt()),
                parseDate(stringValue(runtime, "latest_completed_target_durable_at")),
                parseDate(stringValue(runtime, "last_target_durable_at")));
        String schedulerState = stringValue(runtime, "scheduler_state");
        String workerState = StringUtils.defaultIfBlank(status.getWorkerState(), stringValue(runtime, "worker_state"));
        String cycleState = StringUtils.defaultIfBlank(status.getCurrentCheckpointState(),
                StringUtils.defaultIfBlank(status.getCycleState(), stringValue(runtime, "cycle_state")));
        String cycleMode = StringUtils.defaultIfBlank(status.getCurrentCheckpointCycleType(),
                stringValue(runtime, "current_checkpoint_cycle_type"));
        String requestedMode = StringUtils.defaultIfBlank(status.getCurrentCheckpointRequestedMode(), cycleMode);
        String effectiveMode = StringUtils.defaultIfBlank(status.getCurrentCheckpointEffectiveMode(), requestedMode);
        String modeDecisionCode = StringUtils.defaultIfBlank(status.getCurrentCheckpointModeDecisionCode(),
                stringValue(runtime, "current_checkpoint_mode_decision_code"));
        String baselineState = StringUtils.defaultIfBlank(status.getBaselineState(), stringValue(runtime, "baseline_state"));
        String reseedReason = StringUtils.defaultIfBlank(status.getReseedReason(), stringValue(runtime, "reseed_reason"));
        Boolean pidAlive = status.getSchedulerPidAlive() != null ? status.getSchedulerPidAlive()
                : booleanValue(runtime, "scheduler_pid_alive");
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        String errorMessage = StringUtils.defaultIfBlank(status.getErrorMessage(), stringValue(runtime, "error_message"));
        long rpoAge = durableAt != null ? Math.max(0L, (now.getTime() - durableAt.getTime()) / 1000L) : Long.MAX_VALUE;
        long rpoLimit = plan.getRpoSeconds() != null ? Math.max(1, plan.getRpoSeconds()) : 300L;
        boolean overdue = durableAt == null || rpoAge > rpoLimit + Math.min(30L, Math.max(5L, rpoLimit / 10L));
        boolean runtimeFailed = StringUtils.equalsAnyIgnoreCase(status.getState(), "ERROR", "FAILED")
                || StringUtils.equalsAnyIgnoreCase(cycleState, "ERROR", "FAILED") || StringUtils.isNotBlank(errorCode);
        boolean reseeding = StringUtils.equalsAnyIgnoreCase(cycleMode, "FULL_RESEED", "full-reseed")
                && !StringUtils.equalsAnyIgnoreCase(cycleState, "COMPLETED", "READY");
        boolean schedulerHealthy = Boolean.TRUE.equals(pidAlive)
                && StringUtils.equalsAnyIgnoreCase(schedulerState, "RUNNING", "STARTED", "COMPLETED");
        boolean targetMaterialized = Boolean.TRUE.equals(status.getTargetMaterialized())
                || Boolean.TRUE.equals(booleanValue(runtime, "target_materialized"));

        String protectionState;
        String freshnessState = overdue ? "OVERDUE" : "WITHIN_RPO";
        if (runtimeFailed) {
            protectionState = DrConstants.PLAN_STATE_ERROR;
        } else if (reseeding) {
            protectionState = "RESEEDING";
        } else if (StringUtils.equalsIgnoreCase(status.getState(), "PAUSED")) {
            protectionState = DrConstants.PLAN_STATE_PAUSED;
        } else if (!schedulerHealthy || overdue) {
            protectionState = "DEGRADED";
        } else if (targetMaterialized && durableAt != null) {
            protectionState = DrConstants.PLAN_STATE_READY;
        } else {
            protectionState = DrConstants.PLAN_STATE_SYNCING;
        }

        authority.setEngineRunUuid(status.getRunUuid());
        authority.setRuntimeGeneration(generation);
        authority.setSchedulerState(schedulerState);
        authority.setSchedulerPidAlive(Boolean.TRUE.equals(pidAlive));
        authority.setWorkerState(workerState);
        authority.setCurrentCycleSequence(sequence);
        authority.setCurrentCycleState(cycleState);
        authority.setCurrentCycleMode(cycleMode);
        authority.setBaselineState(baselineState);
        authority.setReseedReason(reseedReason);
        authority.setLastModeDecisionCode(StringUtils.defaultIfBlank(status.getLatestCompletedModeDecisionCode(), modeDecisionCode));
        authority.setConsecutiveAutomaticReseedCount(status.getConsecutiveAutomaticReseedCount() != null
                ? status.getConsecutiveAutomaticReseedCount() : 0);
        authority.setLatestCompletedCycleSequence(status.getLatestCompletedCheckpointSequence());
        authority.setLatestCompletedIncrementalVerified(status.getLatestCompletedIncrementalVerified());
        authority.setProjectionIntegrityState("CONSISTENT");
        authority.setProjectionIntegrityCode(null);
        authority.setProjectionIntegritySequence(status.getLatestCompletedCheckpointSequence());
        authority.setProtectionState(protectionState);
        authority.setFreshnessState(freshnessState);
        authority.setLastStatusAt(now);
        authority.setLastSourceCheckpointAt(sourceAt);
        authority.setLastTargetDurableAt(durableAt);
        authority.setRpoAgeSeconds(durableAt != null ? rpoAge : null);
        authority.setRpoOverdue(overdue);
        authority.setErrorCode(errorCode);
        authority.setErrorMessage(errorMessage);
        authority.setStatusJson(compactRuntimeStatusJson(status.getStatusJson()));
        authority.markUpdated();
        if (authority.getId() == 0) {
            drPlanRuntimeDao.persist(authority);
        } else {
            drPlanRuntimeDao.update(authority.getId(), authority);
        }

        if (sequence != null && StringUtils.isNotBlank(status.getRunUuid())) {
            projectCurrentSyncCycle(plan, projectionRun, status, sequence, requestedMode, effectiveMode, cycleState,
                    baselineState, reseedReason, sourceAt, errorCode, errorMessage);
        }
        if (status.getLatestCompletedCheckpointSequence() != null && StringUtils.isNotBlank(status.getRunUuid())) {
            projectLatestCompletedSyncCycle(plan, projectionRun, status,
                    status.getLatestCompletedCheckpointSequence(), baselineState);
        }
    }

    private void projectCurrentSyncCycle(DrPlanVO plan, DrRunVO projectionRun, FtctlDrStatusAnswer status,
            long sequence, String requestedMode, String effectiveMode, String cycleState, String baselineState,
            String reseedReason, Date sourceAt, String errorCode, String errorMessage) {
        DrSyncCycleVO cycle = drSyncCycleDao.findByPlanRunSequence(plan.getId(), status.getRunUuid(), sequence);
        if (cycle == null) {
            cycle = new DrSyncCycleVO(plan.getId(), status.getRunUuid(), sequence);
            cycle.setStarted(new Date());
        }
        if (cycle.getCompleted() != null) {
            return;
        }
        cycle.setRunId(projectionRun != null ? projectionRun.getId() : null);
        cycle.setRequestedMode(requestedMode);
        cycle.setEffectiveMode(effectiveMode);
        cycle.setState(StringUtils.defaultIfBlank(cycleState, status.getState()));
        cycle.setCommitState(status.getDataCommitState());
        cycle.setBaselineState(baselineState);
        cycle.setReseedReason(reseedReason);
        cycle.setAutomaticReseed(status.getCurrentCheckpointAutomaticReseed());
        cycle.setModeDecisionCode(status.getCurrentCheckpointModeDecisionCode());
        cycle.setInvalidBaselineDiskCount(status.getCurrentCheckpointInvalidBaselineDiskCount());
        cycle.setSourceCheckpointAt(sourceAt);
        cycle.setErrorCode(errorCode);
        cycle.setErrorMessage(errorMessage);
        persistSyncCycle(cycle);
    }

    private void projectLatestCompletedSyncCycle(DrPlanVO plan, DrRunVO projectionRun, FtctlDrStatusAnswer status,
            long sequence, String baselineState) {
        FtctlDrCycleSnapshot snapshot = latestCompletedCycle(status);
        if (!isCoherentCycleSnapshot(plan, status, snapshot) || snapshot == null) {
            return;
        }
        sequence = snapshot.getSequence();
        DrSyncCycleVO cycle = drSyncCycleDao.findByPlanRunSequence(plan.getId(), status.getRunUuid(), sequence);
        if (cycle == null) {
            cycle = new DrSyncCycleVO(plan.getId(), status.getRunUuid(), sequence);
            cycle.setStarted(parseDate(status.getLatestCompletedSourceCheckpointAt()));
        }
        if (cycle.getCompleted() != null && cycle.getBaselineGeneration() != null
                && cycle.getBaselineGeneration().equals(cycle.getSequence())
                && StringUtils.equals(cycle.getCycleToken(), snapshot.getCycleToken())) {
            return;
        }
        Date sourceAt = parseDate(status.getLatestCompletedSourceCheckpointAt());
        Date durableAt = parseDate(status.getLatestCompletedTargetDurableAt());
        cycle.setRunId(projectionRun != null ? projectionRun.getId() : null);
        cycle.setCycleToken(snapshot.getCycleToken());
        cycle.setRequestedMode(StringUtils.defaultIfBlank(snapshot.getRequestedMode(),
                status.getLatestCompletedCheckpointCycleType()));
        cycle.setEffectiveMode(snapshot.getEffectiveMode());
        cycle.setState(StringUtils.defaultIfBlank(snapshot.getState(), "READY"));
        cycle.setCommitState("LOCAL_DURABLE");
        cycle.setBaselineGeneration(snapshot.getBaselineGeneration());
        cycle.setBaselineState(StringUtils.defaultIfBlank(baselineState, "LOCAL_DURABLE"));
        cycle.setReseedReason(status.getLatestCompletedReseedReason());
        cycle.setAutomaticReseed(status.getLatestCompletedAutomaticReseed());
        cycle.setModeDecisionCode(status.getLatestCompletedModeDecisionCode());
        cycle.setInvalidBaselineDiskCount(status.getLatestCompletedInvalidBaselineDiskCount());
        cycle.setIncrementalVerified(snapshot.getIncrementalVerified());
        cycle.setMetricsEstimated(snapshot.getMetricsEstimated());
        cycle.setVirtualBytes(snapshot.getVirtualBytes());
        cycle.setChangedBytes(snapshot.getChangedBytes());
        cycle.setSourceReadBytes(snapshot.getSourceReadBytes());
        cycle.setTargetWrittenBytes(snapshot.getTargetWrittenBytes());
        cycle.setTransferPayloadBytes(snapshot.getTransferPayloadBytes());
        cycle.setChangedExtentCount(snapshot.getChangedExtentCount());
        cycle.setDurationMs(snapshot.getDurationMs());
        cycle.setThroughputBps(snapshot.getThroughputBps());
        cycle.setSourceCheckpointAt(sourceAt);
        cycle.setTargetDurableAt(durableAt);
        cycle.setCompleted(durableAt != null ? durableAt : new Date());
        cycle.setErrorCode(null);
        cycle.setErrorMessage(null);
        persistSyncCycle(cycle);
    }

    private void persistSyncCycle(DrSyncCycleVO cycle) {
        cycle.markUpdated();
        if (cycle.getId() == 0) {
            drSyncCycleDao.persist(cycle);
        } else {
            drSyncCycleDao.update(cycle.getId(), cycle);
        }
    }

    private FtctlDrCycleSnapshot latestCompletedCycle(FtctlDrStatusAnswer status) {
        FtctlDrCycleSnapshot snapshot = status.getLatestCompletedCycle();
        if (snapshot != null || status.getLatestCompletedCheckpointSequence() == null) {
            return snapshot;
        }
        snapshot = new FtctlDrCycleSnapshot();
        snapshot.setPlanUuid(status.getPlanUuid());
        snapshot.setRunUuid(status.getRunUuid());
        snapshot.setSequence(status.getLatestCompletedCheckpointSequence());
        snapshot.setCycleToken(status.getLatestCompletedCycleToken());
        snapshot.setState(status.getLatestCompletedCheckpointState());
        snapshot.setRequestedMode(status.getLatestCompletedRequestedMode());
        snapshot.setEffectiveMode(status.getLatestCompletedEffectiveMode());
        snapshot.setBaselineGeneration(status.getLatestCompletedBaselineGeneration());
        snapshot.setIncrementalVerified(status.getLatestCompletedIncrementalVerified());
        snapshot.setMetricsEstimated(status.getLatestCompletedMetricsEstimated());
        snapshot.setVirtualBytes(status.getLatestCompletedVirtualBytes());
        snapshot.setChangedBytes(status.getLatestCompletedChangedBytes());
        snapshot.setSourceReadBytes(status.getLatestCompletedSourceReadBytes());
        snapshot.setTargetWrittenBytes(status.getLatestCompletedTargetWrittenBytes());
        snapshot.setTransferPayloadBytes(status.getLatestCompletedTransferPayloadBytes());
        snapshot.setChangedExtentCount(status.getLatestCompletedChangedExtentCount());
        snapshot.setDurationMs(status.getLatestCompletedDurationMs());
        snapshot.setThroughputBps(status.getLatestCompletedThroughputBps());
        snapshot.setSourceCheckpointAt(status.getLatestCompletedSourceCheckpointAt());
        snapshot.setTargetDurableAt(status.getLatestCompletedTargetDurableAt());
        return snapshot;
    }

    private boolean isCoherentCycleSnapshot(DrPlanVO plan, FtctlDrStatusAnswer status,
            FtctlDrCycleSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        if (snapshot.getSequence() == null || !StringUtils.equals(plan.getUuid(), snapshot.getPlanUuid())
                || !StringUtils.equals(status.getRunUuid(), snapshot.getRunUuid())) {
            return false;
        }
        String expectedToken = snapshot.getPlanUuid() + ":" + snapshot.getSequence();
        if (StringUtils.isBlank(snapshot.getCycleToken())) {
            snapshot.setCycleToken(expectedToken);
        } else if (!StringUtils.equals(expectedToken, snapshot.getCycleToken())) {
            return false;
        }
        if (snapshot.getBaselineGeneration() != null
                && !snapshot.getBaselineGeneration().equals(snapshot.getSequence())) {
            return false;
        }
        Long changed = snapshot.getChangedBytes();
        Long written = snapshot.getTargetWrittenBytes();
        if (changed != null && written != null && changed == 0L && written == 0L) {
            return StringUtils.isBlank(snapshot.getEffectiveMode())
                    || StringUtils.equalsIgnoreCase(snapshot.getEffectiveMode(), "NO_CHANGE");
        }
        if (changed != null && written != null && (changed > 0L || written > 0L)) {
            return !StringUtils.equalsIgnoreCase(snapshot.getEffectiveMode(), "NO_CHANGE");
        }
        return true;
    }

    private void markProjectionIntegrityFailure(DrPlanVO plan, FtctlDrCycleSnapshot snapshot, String errorCode) {
        DrPlanRuntimeVO runtime = drPlanRuntimeDao.findByPlanId(plan.getId());
        if (runtime == null) {
            runtime = new DrPlanRuntimeVO(plan.getId());
        }
        runtime.setProjectionIntegrityState("INCONSISTENT");
        runtime.setProjectionIntegrityCode(errorCode);
        runtime.setProjectionIntegritySequence(snapshot != null ? snapshot.getSequence() : null);
        runtime.markUpdated();
        if (runtime.getId() == 0) {
            drPlanRuntimeDao.persist(runtime);
        } else {
            drPlanRuntimeDao.update(runtime.getId(), runtime);
        }
    }


    private void updatePlanFromStatus(DrPlanVO plan, DrRunVO projectionRun, FtctlDrStatusAnswer status) {
        boolean changed = false;
        JsonObject runtime = parseObject(status.getStatusJson());
        upsertCutoverSession(plan, projectionRun, status, runtime);
        reconcileCloudManagedTestTarget(plan, projectionRun, status, runtime);
        if (isReleasedRuntime(status, runtime)) {
            cleanupReleasedProjection(plan, status, runtime);
            reconcileAcceptedRunFromStatus(plan, status, runtime);
            return;
        }
        if (preserveTerminalMaterializationFailure(plan, status, runtime)) {
            return;
        }
        String planState = toPlanState(status.getState());
        if (isCutoverReadyRuntime(plan, status, runtime)) {
            try {
                if (!drTargetMaterializationService.ensureTargetPoweredOn(plan.getId())) {
                    reconcileAcceptedRunFromStatus(plan, status, runtime);
                    return;
                }
                runtime.addProperty("state", "FAILED_OVER");
                runtime.addProperty("target_power_state", "POWERED_ON");
                runtime.addProperty("target_promotion_state", "PROMOTED");
                planState = DrConstants.PLAN_STATE_FAILED_OVER;
            } catch (RuntimeException e) {
                plan.setLastErrorCode("DR_TARGET_POWER_ON_FAILED");
                plan.setLastErrorMessage(e.getMessage());
                plan.markUpdated();
                drPlanDao.update(plan.getId(), plan);
                return;
            }
        }
        boolean targetReferencePresent = hasTargetReferenceForDirection(plan);
        boolean durableCheckpointPresent = hasDurableCheckpoint(status, runtime);
        if (StringUtils.equals(planState, DrConstants.PLAN_STATE_READY)
                && (!targetReferencePresent || !durableCheckpointPresent)) {
            planState = DrConstants.PLAN_STATE_SYNCING;
        }
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
            if (targetReferencePresent && durableCheckpointPresent) {
                plan.setTargetReadyAt(targetDurableAt);
            } else {
                plan.setTargetReadyAt(null);
            }
            changed = true;
        }
        if (status.getTargetReadyRpoSeconds() != null) {
            plan.setTargetReadyRpoSeconds(targetReferencePresent ? status.getTargetReadyRpoSeconds() : null);
            changed = true;
        }
        if (StringUtils.isNotBlank(status.getErrorCode())) {
            plan.setLastErrorCode(status.getErrorCode());
            plan.setLastErrorMessage(projectionFailureMessage(status.getErrorCode(), status, runtime));
            changed = true;
        } else if (isHealthyRuntimeProgress(status, runtime) && StringUtils.isNotBlank(plan.getLastErrorCode())) {
            plan.setLastErrorCode(null);
            plan.setLastErrorMessage(null);
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

    private boolean isCutoverReadyRuntime(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        String state = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        return StringUtils.equalsIgnoreCase(plan.getDirection(), "VMWARE_TO_KVM")
                && StringUtils.equals(state, "CUTOVER_READY")
                && StringUtils.equalsIgnoreCase(status.getGuestPreparationState(), "READY");
    }

    private void upsertCutoverSession(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (run == null || !StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_FAILOVER)) {
            return;
        }
        DrCutoverSessionVO session = drCutoverSessionDao.findActiveByRunId(run.getId());
        if (session == null) {
            session = new DrCutoverSessionVO(plan.getId(), run.getId(), run.getRunType(),
                    StringUtils.defaultIfBlank(status.getState(), "PREPARING"));
            session = drCutoverSessionDao.persist(session);
        }
        session.setState(StringUtils.defaultIfBlank(status.getState(), session.getState()));
        session.setCheckpointSequence(longValue(runtime, "test_restore_point_sequence"));
        session.setGuestOsFamily(status.getGuestFamily());
        session.setGuestPreparationState(status.getGuestPreparationState());
        session.setVirtioState(StringUtils.equalsIgnoreCase(status.getGuestPreparationState(), "READY") ? "READY" : status.getGuestPreparationState());
        session.setSecureBootState(stringValue(runtime, "secure_boot_state"));
        session.setDomainName(status.getTestDomainName());
        session.setBootValidationState(status.getTestDomainState());
        session.setCleanupRequired(StringUtils.equalsAnyIgnoreCase(status.getState(), "TESTING", "TEST_RUNNING", "ERROR"));
        session.setDetailsJson(compactRuntimeStatusJson(status.getStatusJson()));
        session.setErrorCode(status.getErrorCode());
        session.setErrorMessage(stringValue(runtime, "error_message"));
        session.markUpdated();
        drCutoverSessionDao.update(session.getId(), session);
        upsertCutoverDisks(session, runtime);
    }

    private void reconcileCloudManagedTestTarget(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (run == null || !StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return;
        }
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        if (StringUtils.equalsAny(runtimeState, "TEST_ARTIFACTS_READY", "ARTIFACTS_READY")) {
            drTargetMaterializationService.enqueueTestMaterialization(plan.getId(), run.getId(), status.getStatusJson());
        }
    }

    private void upsertCutoverDisks(DrCutoverSessionVO session, JsonObject runtime) {
        JsonObject testSession = objectValue(runtime, "test_session");
        JsonObject artifacts = objectValue(testSession, "testArtifacts");
        JsonElement recordsElement = artifacts.get("records");
        if (recordsElement == null || !recordsElement.isJsonArray()) {
            return;
        }
        List<DrCutoverDiskVO> existing = drCutoverDiskDao.listActiveBySessionId(session.getId());
        for (int index = 0; index < recordsElement.getAsJsonArray().size(); index++) {
            JsonElement element = recordsElement.getAsJsonArray().get(index);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject record = element.getAsJsonObject();
            DrCutoverDiskVO disk = null;
            for (DrCutoverDiskVO candidate : existing) {
                if (candidate.getDiskIndex() == index) {
                    disk = candidate;
                    break;
                }
            }
            String type = stringValue(record, "type");
            boolean create = disk == null;
            if (create) {
                disk = new DrCutoverDiskVO(session.getId(), index);
            }
            disk.setProvider(StringUtils.startsWithIgnoreCase(type, "rbd") ? "RBD" : "QCOW2");
            disk.setCheckpointRef(StringUtils.defaultIfBlank(stringValue(record, "backing"), "unknown"));
            disk.setWritableRef(StringUtils.defaultIfBlank(stringValue(record, "path"), stringValue(record, "clone")));
            disk.setRollbackRef(stringValue(record, "snapshot"));
            disk.setState(StringUtils.defaultIfBlank(stringValue(record, "state"), "CREATED"));
            disk.setDetailsJson(GSON.toJson(record));
            disk.markUpdated();
            if (create) {
                drCutoverDiskDao.persist(disk);
            } else {
                drCutoverDiskDao.update(disk.getId(), disk);
            }
        }
    }

    private boolean isReleasedRuntime(FtctlDrStatusAnswer status, JsonObject runtime) {
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String step = StringUtils.defaultIfBlank(status.getStep(), stringValue(runtime, "step"));
        return StringUtils.equals(runtimeState, "RELEASED")
                || StringUtils.equalsIgnoreCase(step, "release-completed");
    }

    private void cleanupReleasedProjection(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (plan == null) {
            return;
        }
        String runtimeJson = compactRuntimeStatusJson(StringUtils.defaultIfBlank(status.getStatusJson(), GSON.toJson(runtime)));
        removeActiveReplicas(plan, runtimeJson);
        removeActiveRestorePoints(plan);

        plan.setState(DrConstants.PLAN_STATE_NEW);
        plan.setActiveSide("SOURCE");
        plan.setTargetReadyAt(null);
        plan.setTargetReadyRpoSeconds(null);
        plan.setLastTargetDurableAt(null);
        plan.setLastErrorCode(null);
        plan.setLastErrorMessage(null);
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
    }

    private void removeActiveReplicas(DrPlanVO plan, String detailsJson) {
        if (drReplicaDao == null || drReplicaDiskDao == null) {
            return;
        }
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null) {
            return;
        }
        for (DrReplicaVO replica : replicas) {
            if (replica == null || replica.getRemoved() != null) {
                continue;
            }
            List<DrReplicaDiskVO> disks = drReplicaDiskDao.listActiveByReplicaId(replica.getId());
            if (disks != null) {
                for (DrReplicaDiskVO disk : disks) {
                    if (disk == null || disk.getRemoved() != null) {
                        continue;
                    }
                    disk.setDetailsJson(detailsJson);
                    disk.markUpdated();
                    drReplicaDiskDao.update(disk.getId(), disk);
                    drReplicaDiskDao.remove(disk.getId());
                }
            }
            replica.setRuntimeStateJson(detailsJson);
            replica.markUpdated();
            drReplicaDao.update(replica.getId(), replica);
            drReplicaDao.remove(replica.getId());
        }
    }

    private void removeActiveRestorePoints(DrPlanVO plan) {
        if (drRestorePointDao == null) {
            return;
        }
        List<DrRestorePointVO> restorePoints = drRestorePointDao.listActiveByPlanId(plan.getId());
        if (restorePoints == null) {
            return;
        }
        for (DrRestorePointVO restorePoint : restorePoints) {
            if (restorePoint == null || restorePoint.getRemoved() != null) {
                continue;
            }
            drRestorePointDao.remove(restorePoint.getId());
        }
    }

    private boolean preserveTerminalMaterializationFailure(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (plan == null || drRunDao == null) {
            return false;
        }
        DrRunVO latestRun = drRunDao.findLatestByPlanId(plan.getId());
        if (latestRun == null || latestRun.getCompleted() == null
                || !StringUtils.equals(latestRun.getState(), DrConstants.RUN_STATE_FAILED)
                || !StringUtils.equals(latestRun.getErrorCode(), DrConstants.ERROR_TARGET_VM_MATERIALIZE_FAILED)) {
            return false;
        }
        boolean changed = false;
        if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_ERROR)) {
            plan.setState(DrConstants.PLAN_STATE_ERROR);
            changed = true;
        }
        if (!StringUtils.equals(plan.getLastErrorCode(), latestRun.getErrorCode())) {
            plan.setLastErrorCode(latestRun.getErrorCode());
            changed = true;
        }
        if (!StringUtils.equals(plan.getLastErrorMessage(), latestRun.getErrorMessage())) {
            plan.setLastErrorMessage(latestRun.getErrorMessage());
            changed = true;
        }
        if (changed) {
            plan.markUpdated();
            drPlanDao.update(plan.getId(), plan);
        }
        return true;
    }

    private boolean isCorrelatedRuntime(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status) {
        if (plan == null || status == null || !StringUtils.equals(plan.getUuid(), status.getPlanUuid())) {
            return false;
        }
        if (run == null || StringUtils.isBlank(status.getRunUuid())) {
            return true;
        }
        return StringUtils.equals(run.getUuid(), status.getRunUuid());
    }

    private boolean hardwareContractMatches(DrPlanVO plan, JsonObject runtime) {
        JsonObject mapping = parseObject(plan != null ? plan.getMappingJson() : null);
        String expected = stringValue(firstObject(objectValue(mapping, "source"), "hardware", "sourceHardware"), "fingerprint");
        String actual = stringValue(runtime, "source_hardware_fingerprint");
        return StringUtils.isBlank(expected) || StringUtils.isBlank(actual) || StringUtils.equals(expected, actual);
    }

    private boolean canRecoverTerminalMaterializationFailure(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (!isHealthyRuntimeProgress(status, runtime)) {
            return false;
        }
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        boolean targetReadyRuntime = StringUtils.equalsAny(runtimeState, "READY", "TARGET_READY")
                || Boolean.TRUE.equals(status != null ? status.getTargetMaterialized() : null)
                || Boolean.TRUE.equals(booleanValue(runtime, "target_materialized"));
        if (!targetReadyRuntime || !hasDurableCheckpoint(status, runtime)) {
            return false;
        }
        if (isExplicitFalse(status != null ? status.getTargetVmPresent() : null, booleanValue(runtime, "target_vm_present"))
                || isExplicitFalse(status != null ? status.getTargetStoragePresent() : null, booleanValue(runtime, "target_storage_present"))
                || isExplicitFalse(status != null ? status.getTargetNetworkPresent() : null, booleanValue(runtime, "target_network_present"))
                || isExplicitFalse(status != null ? status.getRestorePointPresent() : null, booleanValue(runtime, "restore_point_present"))) {
            return false;
        }
        return hasTargetReferenceForDirection(plan);
    }

    private void recoverTerminalMaterializationFailure(DrPlanVO plan, DrRunVO latestRun, FtctlDrStatusAnswer status, JsonObject runtime) {
        String compactStatusJson = compactRuntimeStatusJson(status != null ? status.getStatusJson() : GSON.toJson(runtime));
        recordRunProjectionStep(latestRun, DrConstants.STEP_STATE_SUCCEEDED, 100, compactStatusJson, null, null);
        latestRun.setState(DrConstants.RUN_STATE_SUCCEEDED);
        latestRun.setCompleted(new Date());
        latestRun.setCurrentStepName("runtime-projection");
        latestRun.setProjectionState("recovered");
        latestRun.setProjectionChecked(new Date());
        latestRun.setRetryable(false);
        latestRun.setRetryAfterSeconds(null);
        latestRun.setNextRetryAt(null);
        latestRun.setLastStatusJson(compactStatusJson);
        latestRun.setErrorCode(null);
        latestRun.setErrorMessage(null);
        latestRun.markUpdated();
        drRunDao.update(latestRun.getId(), latestRun);

        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setLastErrorCode(null);
        plan.setLastErrorMessage(null);
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
        updateReplicaRuntimeProjection(plan, status, runtime, DrConstants.REPLICA_STATE_READY, "SOURCE",
                StringUtils.defaultIfBlank(stringValue(runtime, "target_power_state"), DrConstants.REPLICA_POWER_STATE_POWERED_OFF));
        markReplicaDisksReady(plan, compactStatusJson);
        persistRunProjectionEvent(plan, latestRun, DrConstants.EVENT_TARGET_MATERIALIZATION_RECOVERED, DrConstants.EVENT_SEVERITY_INFO,
                "FTCTL_DR target materialization failure was recovered from converged Cloud and runtime state", compactStatusJson);
    }

    private void markReplicaDisksReady(DrPlanVO plan, String detailsJson) {
        if (plan == null || drReplicaDao == null || drReplicaDiskDao == null) {
            return;
        }
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null) {
            return;
        }
        for (DrReplicaVO replica : replicas) {
            if (replica == null || replica.getRemoved() != null) {
                continue;
            }
            List<DrReplicaDiskVO> disks = drReplicaDiskDao.listActiveByReplicaId(replica.getId());
            if (disks == null) {
                continue;
            }
            for (DrReplicaDiskVO disk : disks) {
                if (disk == null || disk.getRemoved() != null) {
                    continue;
                }
                disk.setState(DrConstants.REPLICA_STATE_READY);
                disk.setDetailsJson(detailsJson);
                disk.markUpdated();
                drReplicaDiskDao.update(disk.getId(), disk);
            }
        }
    }

    private boolean isHealthyRuntimeProgress(FtctlDrStatusAnswer status, JsonObject runtime) {
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String workerState = StringUtils.upperCase(stringValue(runtime, "worker_state"), Locale.ROOT);
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        return status.getResult()
                && StringUtils.isBlank(errorCode)
                && !StringUtils.equals(workerState, "FAILED")
                && StringUtils.equalsAny(runtimeState, "RUNNING", "SYNCING", "SEEDING", "READY", "TARGET_READY");
    }

    private void updateReplicaRuntimeProjection(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime,
            String replicaState, String activeSide, String powerState) {
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null || replicas.isEmpty()) {
            return;
        }
        String runtimeJson = compactRuntimeStatusJson(StringUtils.defaultIfBlank(status.getStatusJson(), GSON.toJson(runtime)));
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
        DrRunVO run = resolveProjectionRun(plan);
        if (run == null || run.getCompleted() != null || !isProjectableRunState(run)) {
            return;
        }
        if (isRetryableWorkerCondition(status, runtime)) {
            failRetryableRunFromProjection(plan, run, status, runtime);
            return;
        }
        if (isWorkerFailed(runtime)) {
            failRunFromProjection(plan, run, status, runtime);
            return;
        }
        if (isWorkerStartStalled(run, runtime)) {
            failRetryableRunFromProjection(plan, run, status, runtime);
            return;
        }
        if (isRuntimeError(status, runtime)) {
            failRunFromProjection(plan, run, status, runtime);
            return;
        }
        if (isRunSatisfiedByRuntime(plan, run, status, runtime)) {
            if (StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_TEST_CLEANUP)) {
                drTargetMaterializationService.completeTestCleanup(plan.getId());
            }
            completeRunFromProjection(plan, run, status);
            return;
        }
        if (StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_SYNC)) {
            markSyncTargetPending(plan, run, status, runtime);
        }
    }

    private boolean isProjectableRunState(DrRunVO run) {
        return StringUtils.equalsAny(run.getState(),
                DrConstants.RUN_STATE_ACCEPTED, DrConstants.RUN_STATE_RUNNING, DrConstants.RUN_STATE_RETRYING);
    }

    private DrRunVO resolveProjectionRun(DrPlanVO plan) {
        if (plan == null || drRunDao == null) {
            return null;
        }
        DrRunVO activeRun = drRunDao.findActiveByPlanId(plan.getId());
        if (activeRun != null) {
            return activeRun;
        }
        return drRunDao.findLatestByPlanId(plan.getId());
    }

    private boolean isRetryableWorkerCondition(FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        String result = StringUtils.defaultIfBlank(status.getFtctlResult(), stringValue(runtime, "result"));
        String workerState = StringUtils.upperCase(stringValue(runtime, "worker_state"), Locale.ROOT);
        Boolean retryable = booleanValue(runtime, "retryable");
        return StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_BUSY_RETRYABLE)
                || (Boolean.TRUE.equals(retryable)
                && (StringUtils.equalsAny(workerState, "RETRYING", "WAITING")
                || StringUtils.equalsIgnoreCase(result, "locked")));
    }

    private boolean isWorkerFailed(JsonObject runtime) {
        String workerState = StringUtils.upperCase(stringValue(runtime, "worker_state"), Locale.ROOT);
        String errorCode = stringValue(runtime, "error_code");
        return StringUtils.equals(workerState, "FAILED")
                && !StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_BUSY_RETRYABLE);
    }

    private boolean isWorkerStartStalled(DrRunVO run, JsonObject runtime) {
        String workerState = StringUtils.upperCase(stringValue(runtime, "worker_state"), Locale.ROOT);
        if (!StringUtils.equalsAny(workerState, "STARTING", "STARTED")) {
            return false;
        }
        Date base = firstDate(parseDate(stringValue(runtime, "worker_updated_at")), parseDate(stringValue(runtime, "updated_at")),
                run.getAcceptedAt(), run.getDispatchCompleted(), run.getStarted(), run.getCreated());
        if (base == null) {
            return false;
        }
        long ageSeconds = (System.currentTimeMillis() - base.getTime()) / 1000L;
        return ageSeconds >= WORKER_START_GRACE_SECONDS;
    }

    private boolean isRuntimeError(FtctlDrStatusAnswer status, JsonObject runtime) {
        if (isStatusTimeout(status, runtime)) {
            return false;
        }
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_PROJECTION_STALE)
                || StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_BUSY_RETRYABLE)) {
            return false;
        }
        return !status.getResult() || StringUtils.equalsAny(runtimeState, "ERROR", "FAILED")
                || (StringUtils.isNotBlank(errorCode) && !StringUtils.equalsIgnoreCase(errorCode, "not_found"));
    }

    private boolean isRunSatisfiedByRuntime(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        String runType = StringUtils.upperCase(run.getRunType(), Locale.ROOT);
        String runtimeState = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        String activeSide = StringUtils.upperCase(StringUtils.defaultIfBlank(plan.getActiveSide(), stringValue(runtime, "active_side")), Locale.ROOT);
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_SYNC)) {
            return isSyncTargetReady(plan, status, runtime, runtimeState);
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return drTargetMaterializationService.isTestTargetActive(run.getId());
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
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_CLEANUP)) {
            return drTargetMaterializationService.isTestTargetCleaned(plan.getId())
                    && StringUtils.equalsAny(runtimeState, "READY", "PAUSED");
        }
        return StringUtils.equalsAny(runType, DrConstants.RUN_TYPE_PAUSE_SYNC,
                DrConstants.RUN_TYPE_RESUME_SYNC, DrConstants.RUN_TYPE_RELEASE)
                && StringUtils.equalsAny(runtimeState, "READY", "PAUSED", "RELEASED");
    }

    private void completeRunFromProjection(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status) {
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_SUCCEEDED, 100, compactStatusJson, null, null);
        run.setState(DrConstants.RUN_STATE_SUCCEEDED);
        run.setCompleted(new Date());
        run.setCurrentStepName("runtime-projection");
        run.setEngineAccepted(true);
        if (run.getAcceptedAt() == null) {
            run.setAcceptedAt(new Date());
        }
        run.setProjectionState("succeeded");
        run.setProjectionChecked(new Date());
        run.setRetryable(false);
        run.setRetryAfterSeconds(null);
        run.setNextRetryAt(null);
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_RUN_SUCCEEDED, DrConstants.EVENT_SEVERITY_INFO,
                "FTCTL_DR runtime accepted state completed", compactStatusJson);
    }

    private void failRunFromProjection(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(),
                StringUtils.defaultIfBlank(stringValue(runtime, "error_code"), DrConstants.ERROR_ENGINE_ACTION_FAILED));
        if (StringUtils.equalsIgnoreCase(errorCode, "not_found")) {
            errorCode = DrConstants.ERROR_RUNTIME_NOT_CREATED;
        } else if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_ACTION_FAILED)
                && StringUtils.equalsIgnoreCase(stringValue(runtime, "worker_state"), "FAILED")) {
            errorCode = DrConstants.ERROR_ENGINE_WORKER_FAILED;
        }
        String message = projectionFailureMessage(errorCode, status, runtime);
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_FAILED, 100, compactStatusJson, errorCode, message);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setCompleted(new Date());
        run.setCurrentStepName("runtime-projection");
        run.setProjectionState("failed");
        run.setProjectionChecked(new Date());
        run.setRetryable(false);
        run.setRetryAfterSeconds(null);
        run.setNextRetryAt(null);
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(errorCode);
        run.setErrorMessage(message);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        markPlanProjectionFailed(plan, errorCode, message);
        markReplicaProjectionFailed(plan, status, runtime, errorCode, message);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_RUN_FAILED, DrConstants.EVENT_SEVERITY_ERROR,
                message, compactStatusJson);
    }

    private String projectionFailureMessage(String errorCode, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (StringUtils.isNotBlank(status.getErrorMessage())) {
            return status.getErrorMessage();
        }
        String runtimeMessage = stringValue(runtime, "error_message");
        if (StringUtils.isNotBlank(runtimeMessage)) {
            return runtimeMessage;
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_MOVER_UNAVAILABLE)) {
            return "VMware data mover is not available on the selected FTCTL worker";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_MOVER_FAILED)) {
            return "VMware data mover failed while copying source disk data";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID)) {
            return "VMware data mover could not open the VDDK NBD source graph";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_NBDKIT_FAILED)) {
            return "VMware VDDK nbdkit session failed before data transfer";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_CONNECT_INVALID)) {
            return "VMware VDDK rejected the source connection parameters";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_THUMBPRINT_UNRESOLVED)) {
            return "VMware VDDK requires a vCenter certificate thumbprint for source-open";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_EXPORT_UNAVAILABLE)) {
            return "VMware VDDK NBD export is unavailable for the selected source disk";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_SOURCE_LOCKED)) {
            return "VMware source disk is locked and requires snapshot-backed source-open";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_OPEN_DENIED)) {
            return "VMware VDDK cannot open the selected source disk because access is denied";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_SNAPSHOT_REF_UNRESOLVED)) {
            return "VMware source snapshot was created, but FTCTL_DR could not resolve its MoRef. The source snapshot is marked for cleanup before retry.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_CBT_METRICS_INVALID)) {
            return "Disk data was copied, but FTCTL could not validate the cycle metrics; no checkpoint was committed";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_CBT_LOCAL_COMMIT_FAILED)) {
            return "Disk data was copied, but FTCTL could not commit the local cycle metadata; no checkpoint was published";
        }
        String agentMessage = boundedAgentMessage(status.getDetails());
        if (StringUtils.isNotBlank(agentMessage)) {
            return agentMessage;
        }
        return StringUtils.isNotBlank(errorCode)
                ? "FTCTL_DR runtime reported failure: " + errorCode
                : "FTCTL_DR runtime reported failure";
    }

    private String statusMessage(FtctlDrStatusAnswer status, String defaultMessage) {
        if (status == null) {
            return defaultMessage;
        }
        if (StringUtils.isNotBlank(status.getErrorMessage())) {
            return status.getErrorMessage();
        }
        return StringUtils.defaultIfBlank(boundedAgentMessage(status.getDetails()), defaultMessage);
    }

    private String boundedAgentMessage(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String message = value.trim();
        if (message.length() > 512 || message.contains("\n") || message.contains("\r")
                || message.startsWith("{") || message.startsWith("[")) {
            return null;
        }
        return message;
    }

    private void failRetryableRunFromProjection(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(),
                StringUtils.defaultIfBlank(stringValue(runtime, "error_code"), DrConstants.ERROR_ENGINE_WORKER_STALLED));
        if (StringUtils.equalsIgnoreCase(errorCode, "not_found")) {
            errorCode = DrConstants.ERROR_RUNTIME_NOT_CREATED;
        }
        if (isWorkerStartStalled(run, runtime) && !StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_BUSY_RETRYABLE)) {
            errorCode = DrConstants.ERROR_ENGINE_WORKER_STALLED;
        }
        String message = retryableProjectionMessage(status, runtime, errorCode);
        Integer retryAfter = integerValue(runtime, "retry_after_sec");
        if (retryAfter == null || retryAfter <= 0) {
            retryAfter = STATUS_REFRESH_WAIT_SECONDS;
        }
        Date now = new Date();
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_FAILED, 100, compactStatusJson, errorCode, message);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setCompleted(now);
        run.setCurrentStepName("runtime-worker");
        run.setProjectionState("retryable-failed");
        run.setProjectionChecked(now);
        run.setRetryable(true);
        run.setRetryAfterSeconds(retryAfter);
        run.setNextRetryAt(new Date(now.getTime() + retryAfter * 1000L));
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(errorCode);
        run.setErrorMessage(message);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        markPlanProjectionFailed(plan, errorCode, message);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_RUN_FAILED, DrConstants.EVENT_SEVERITY_WARN,
                message, compactStatusJson);
    }

    private String retryableProjectionMessage(FtctlDrStatusAnswer status, JsonObject runtime, String errorCode) {
        String message = status.getErrorMessage();
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        String holderCommand = stringValue(runtime, "holder_command");
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_BUSY_RETRYABLE)) {
            return StringUtils.isNotBlank(holderCommand)
                    ? "FTCTL_DR worker could not start because another engine command is holding the lock: " + holderCommand
                    : "FTCTL_DR worker could not start because the engine lock is busy";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_ENGINE_WORKER_STALLED)) {
            return "FTCTL_DR asynchronous worker did not advance after it was accepted";
        }
        return "FTCTL_DR asynchronous worker ended before the target was materialized";
    }

    private void markSyncTargetPending(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (run == null || run.getCompleted() != null) {
            return;
        }
        boolean targetReferencePresent = hasTargetReferenceForDirection(plan);
        boolean durablePresent = hasDurableCheckpoint(status, runtime);
        String message = targetReferencePresent
                ? "FTCTL_DR sync is still materializing target restore point"
                : "FTCTL_DR sync has not materialized the target VM reference yet";
        int progress = status.getProgress() != null ? Math.max(1, Math.min(status.getProgress(), 99)) : (durablePresent ? 95 : 75);
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_RUNNING, progress, compactStatusJson, null, message);
        run.setProjectionState(durablePresent && !targetReferencePresent ? "target-materializing" : "syncing");
        run.setProjectionChecked(new Date());
        run.setCurrentStepName(durablePresent && !targetReferencePresent ? "target-materializing" : "runtime-projection");
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        if (durablePresent && !targetReferencePresent && drTargetMaterializationService != null) {
            drTargetMaterializationService.enqueueMaterialization(plan.getId(), run.getId(), compactStatusJson);
        }
    }

    private void recordRunProjectionStep(DrRunVO run, String state, Integer progress, String detailsJson,
            String errorCode, String errorMessage) {
        if (drRunStepDao == null) {
            return;
        }
        DrRunStepVO step = drRunStepDao.findActiveByRunIdAndStepOrder(run.getId(), STEP_ORDER_RUNTIME_PROJECTION);
        if (step == null) {
            step = new DrRunStepVO(run.getId(), "runtime-projection", STEP_ORDER_RUNTIME_PROJECTION);
        }
        step.setState(state);
        step.setProgress(progress);
        step.setDetailsJson(compactRuntimeStatusJson(detailsJson));
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        if (step.getStarted() == null) {
            step.setStarted(new Date());
        }
        if (StringUtils.equalsAny(state, DrConstants.STEP_STATE_SUCCEEDED, DrConstants.STEP_STATE_FAILED, DrConstants.STEP_STATE_CANCELED)) {
            step.setCompleted(new Date());
        }
        step.markUpdated();
        if (step.getId() > 0) {
            drRunStepDao.update(step.getId(), step);
        } else {
            drRunStepDao.persist(step);
        }
    }

    private boolean deferRuntimeNotFound(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        if (!isNotFoundStatus(status, runtime)) {
            return false;
        }
        DrRunVO run = drRunDao != null ? drRunDao.findActiveByPlanId(plan.getId()) : null;
        if (run == null || run.getCompleted() != null) {
            return false;
        }
        if (isWithinRuntimeCreationGrace(run)) {
            markRunProjectionPending(plan, run, status);
            return true;
        }
        return false;
    }

    private boolean isNotFoundStatus(FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        String result = StringUtils.defaultIfBlank(status.getFtctlResult(), stringValue(runtime, "result"));
        return StringUtils.equalsIgnoreCase(errorCode, "not_found") || StringUtils.equalsIgnoreCase(result, "not_found");
    }

    private boolean isStatusTimeout(FtctlDrStatusAnswer status, JsonObject runtime) {
        String errorCode = StringUtils.defaultIfBlank(status.getErrorCode(), stringValue(runtime, "error_code"));
        String result = StringUtils.defaultIfBlank(status.getFtctlResult(), stringValue(runtime, "result"));
        return StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_STATUS_TIMEOUT)
                || StringUtils.equalsIgnoreCase(result, "timeout");
    }

    private boolean isStatusBoundaryFailure(FtctlDrStatusAnswer status) {
        if (status == null || StringUtils.isBlank(status.getErrorCode())) {
            return false;
        }
        return StringUtils.equalsAnyIgnoreCase(status.getErrorCode(),
                "DR_STATUS_INVALID_JSON",
                "DR_STATUS_JSON_INVALID",
                "DR_STATUS_IDENTITY_MISMATCH",
                "DR_STATUS_PAYLOAD_TOO_LARGE",
                "DR_STATUS_TYPE_MISMATCH");
    }

    private boolean isWithinRuntimeCreationGrace(DrRunVO run) {
        Date base = run.getAcceptedAt() != null ? run.getAcceptedAt()
                : (run.getDispatchStarted() != null ? run.getDispatchStarted()
                : (run.getStarted() != null ? run.getStarted() : run.getCreated()));
        if (base == null) {
            return true;
        }
        long ageSeconds = (System.currentTimeMillis() - base.getTime()) / 1000L;
        return ageSeconds < RUNTIME_CREATION_GRACE_SECONDS;
    }

    private void markRunProjectionPending(DrPlanVO plan, DrRunVO run, FtctlDrStatusAnswer status) {
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_RUNNING, 75, compactStatusJson,
                DrConstants.ERROR_RUNTIME_STARTING, "FTCTL_DR runtime has not created status yet");
        run.setProjectionState(DrConstants.ERROR_RUNTIME_STARTING);
        run.setProjectionChecked(new Date());
        run.setCurrentStepName("runtime-projection");
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_PROJECTION_REFRESH, DrConstants.EVENT_SEVERITY_INFO,
                "FTCTL_DR runtime is not created yet; projection will retry", compactStatusJson);
    }

    private void markProjectionStale(DrPlanVO plan, FtctlDrStatusAnswer status) {
        DrRunVO run = drRunDao != null ? drRunDao.findActiveByPlanId(plan.getId()) : null;
        if (run == null || run.getCompleted() != null) {
            return;
        }
        String message = statusMessage(status, "FTCTL_DR status refresh timed out");
        String compactStatusJson = compactRuntimeStatusJson(status.getStatusJson());
        recordRunProjectionStep(run, DrConstants.STEP_STATE_RUNNING, 75, compactStatusJson,
                DrConstants.ERROR_PROJECTION_STALE, message);
        run.setProjectionState(DrConstants.ERROR_PROJECTION_STALE);
        run.setProjectionChecked(new Date());
        run.setCurrentStepName("runtime-status-stale");
        run.setLastStatusJson(compactStatusJson);
        run.setErrorCode(DrConstants.ERROR_PROJECTION_STALE);
        run.setErrorMessage(message);
        run.markUpdated();
        drRunDao.update(run.getId(), run);
        persistRunProjectionEvent(plan, run, DrConstants.EVENT_PROJECTION_REFRESH, DrConstants.EVENT_SEVERITY_WARN,
                message, compactStatusJson);
    }

    private void markPlanProjectionFailed(DrPlanVO plan, String errorCode, String message) {
        DrPlanVO latestPlan = drPlanDao.findById(plan.getId());
        if (latestPlan == null || latestPlan.getRemoved() != null) {
            return;
        }
        latestPlan.setState(DrConstants.PLAN_STATE_ERROR);
        latestPlan.setLastErrorCode(StringUtils.defaultIfBlank(errorCode, DrConstants.ERROR_RUNTIME_NOT_CREATED));
        latestPlan.setLastErrorMessage(message);
        latestPlan.markUpdated();
        drPlanDao.update(latestPlan.getId(), latestPlan);
    }

    private void markReplicaProjectionFailed(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime,
            String errorCode, String message) {
        if (plan == null || drReplicaDao == null) {
            return;
        }
        String runtimeJson = compactRuntimeStatusJson(StringUtils.defaultIfBlank(status != null ? status.getStatusJson() : null, GSON.toJson(runtime)));
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null || replicas.isEmpty()) {
            return;
        }
        for (DrReplicaVO replica : replicas) {
            if (replica == null || replica.getRemoved() != null) {
                continue;
            }
            replica.setState(DrConstants.REPLICA_STATE_ERROR);
            replica.setRuntimeStateJson(runtimeJson);
            replica.markUpdated();
            drReplicaDao.update(replica.getId(), replica);
            markReplicaDisksFailed(replica, runtimeJson, errorCode, message);
        }
    }

    private void markReplicaDisksFailed(DrReplicaVO replica, String runtimeJson, String errorCode, String message) {
        if (replica == null || drReplicaDiskDao == null) {
            return;
        }
        List<DrReplicaDiskVO> disks = drReplicaDiskDao.listActiveByReplicaId(replica.getId());
        if (disks == null || disks.isEmpty()) {
            return;
        }
        JsonObject details = parseObject(runtimeJson);
        details.addProperty("errorCode", errorCode);
        details.addProperty("errorMessage", message);
        String detailsJson = GSON.toJson(details);
        for (DrReplicaDiskVO disk : disks) {
            if (disk == null || disk.getRemoved() != null) {
                continue;
            }
            disk.setState(DrConstants.REPLICA_STATE_ERROR);
            disk.setDetailsJson(detailsJson);
            disk.markUpdated();
            drReplicaDiskDao.update(disk.getId(), disk);
        }
    }

    private void upsertRestorePointFromStatus(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime) {
        FtctlDrCycleSnapshot completedCycle = latestCompletedCycle(status);
        if (!isCoherentCycleSnapshot(plan, status, completedCycle)) {
            markProjectionIntegrityFailure(plan, completedCycle, "DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT");
            return;
        }
        Long completedSequence = status.getLatestCompletedCheckpointSequence();
        if (completedCycle != null) {
            completedSequence = completedCycle.getSequence();
        }
        if (completedSequence == null) {
            completedSequence = parseLong(stringValue(runtime, "latest_completed_checkpoint_sequence"));
        }
        String completedRef = StringUtils.defaultIfBlank(status.getLatestCompletedCheckpointRef(),
                stringValue(runtime, "latest_completed_checkpoint_ref"));
        String completedState = StringUtils.defaultIfBlank(status.getLatestCompletedCheckpointState(),
                stringValue(runtime, "latest_completed_checkpoint_state"));
        if (completedSequence == null || StringUtils.isBlank(completedRef)
                || (!StringUtils.equalsAnyIgnoreCase(completedState, "READY", "COMPLETED", "TARGET_READY"))) {
            return;
        }

        Date targetDurableAt = parseDate(status.getLatestCompletedTargetDurableAt());
        if (targetDurableAt == null) {
            targetDurableAt = parseDate(stringValue(runtime, "latest_completed_target_durable_at"));
        }
        if (targetDurableAt == null) {
            return;
        }

        String checkpointSequence = String.valueOf(completedSequence);
        DrRunVO projectionRun = resolveProjectionRun(plan);
        String sourceSnapshotRef = completedRef;
        byte[] checkpointRefHash = sha256(sourceSnapshotRef);
        if (drPlanDao.acquireInLockTable(plan.getId(), 10) == null) {
            return;
        }
        try {
            DrRestorePointVO restorePoint = drRestorePointDao.findByPlanIdAndCheckpointRefHash(plan.getId(), checkpointRefHash);
            if (restorePoint == null) {
                restorePoint = drRestorePointDao.findByPlanIdAndSourceSnapshotRef(plan.getId(), sourceSnapshotRef);
            }
            if (restorePoint == null) {
                restorePoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
                restorePoint.setSourceSnapshotRef(sourceSnapshotRef);
                restorePoint.setCheckpointRefHash(checkpointRefHash);
                restorePoint.setConsistencyLevel("CRASH_CONSISTENT");
            } else if (restorePoint.getTargetReadyAt() != null
                    && restorePoint.getBaselineGeneration() != null
                    && restorePoint.getBaselineGeneration().equals(completedSequence)
                    && StringUtils.equals(restorePoint.getCycleToken(), completedCycle != null
                            ? completedCycle.getCycleToken() : status.getLatestCompletedCycleToken())) {
                return;
            }

            Date sourceCheckpointAt = parseDate(status.getLatestCompletedSourceCheckpointAt());
            if (sourceCheckpointAt == null) {
                sourceCheckpointAt = parseDate(stringValue(runtime, "latest_completed_source_checkpoint_at"));
            }
            restorePoint.setRunId(projectionRun != null ? projectionRun.getId() : null);
            restorePoint.setCheckpointSequence(parseLong(checkpointSequence));
            restorePoint.setCheckpointCycleType(StringUtils.defaultIfBlank(status.getLatestCompletedCheckpointCycleType(),
                    stringValue(runtime, "latest_completed_checkpoint_cycle_type")));
            restorePoint.setSourceCreated(sourceCheckpointAt);
            restorePoint.setTargetReadyAt(targetDurableAt);
            restorePoint.setTargetReadyRpoSeconds(firstInteger(status.getLatestCompletedTargetReadyRpoSeconds(),
                    integerValue(runtime, "latest_completed_target_ready_rpo_seconds")));
            restorePoint.setEffectiveMode(StringUtils.defaultIfBlank(completedCycle != null
                            ? completedCycle.getEffectiveMode() : status.getLatestCompletedEffectiveMode(),
                    stringValue(runtime, "latest_completed_effective_mode")));
            restorePoint.setRequestedMode(StringUtils.defaultIfBlank(completedCycle != null
                            ? completedCycle.getRequestedMode() : status.getLatestCompletedRequestedMode(),
                    stringValue(runtime, "latest_completed_requested_mode")));
            restorePoint.setAutomaticReseed(status.getLatestCompletedAutomaticReseed() != null
                    ? status.getLatestCompletedAutomaticReseed()
                    : booleanValue(runtime, "latest_completed_automatic_reseed"));
            restorePoint.setModeDecisionCode(StringUtils.defaultIfBlank(status.getLatestCompletedModeDecisionCode(),
                    stringValue(runtime, "latest_completed_mode_decision_code")));
            restorePoint.setReseedReason(StringUtils.defaultIfBlank(status.getLatestCompletedReseedReason(),
                    stringValue(runtime, "latest_completed_reseed_reason")));
            restorePoint.setInvalidBaselineDiskCount(status.getLatestCompletedInvalidBaselineDiskCount() != null
                    ? status.getLatestCompletedInvalidBaselineDiskCount()
                    : integerValue(runtime, "latest_completed_invalid_baseline_disk_count"));
            restorePoint.setIncrementalVerified(status.getLatestCompletedIncrementalVerified() != null
                    ? status.getLatestCompletedIncrementalVerified()
                    : booleanValue(runtime, "latest_completed_incremental_verified"));
            restorePoint.setMetricsEstimated(status.getLatestCompletedMetricsEstimated() != null
                    ? status.getLatestCompletedMetricsEstimated()
                    : booleanValue(runtime, "latest_completed_metrics_estimated"));
            restorePoint.setVirtualBytes(status.getLatestCompletedVirtualBytes() != null
                    ? status.getLatestCompletedVirtualBytes() : longValue(runtime, "latest_completed_virtual_bytes"));
            restorePoint.setChangedBytes(status.getLatestCompletedChangedBytes() != null
                    ? status.getLatestCompletedChangedBytes() : longValue(runtime, "latest_completed_changed_bytes"));
            restorePoint.setSourceReadBytes(status.getLatestCompletedSourceReadBytes() != null
                    ? status.getLatestCompletedSourceReadBytes() : longValue(runtime, "latest_completed_source_read_bytes"));
            restorePoint.setTargetWrittenBytes(status.getLatestCompletedTargetWrittenBytes() != null
                    ? status.getLatestCompletedTargetWrittenBytes() : longValue(runtime, "latest_completed_target_written_bytes"));
            restorePoint.setTransferPayloadBytes(status.getLatestCompletedTransferPayloadBytes() != null
                    ? status.getLatestCompletedTransferPayloadBytes() : longValue(runtime, "latest_completed_transfer_payload_bytes"));
            restorePoint.setChangedExtentCount(status.getLatestCompletedChangedExtentCount() != null
                    ? status.getLatestCompletedChangedExtentCount() : longValue(runtime, "latest_completed_changed_extent_count"));
            restorePoint.setDurationMs(status.getLatestCompletedDurationMs() != null
                    ? status.getLatestCompletedDurationMs() : longValue(runtime, "latest_completed_duration_ms"));
            restorePoint.setThroughputBps(status.getLatestCompletedThroughputBps() != null
                    ? status.getLatestCompletedThroughputBps() : longValue(runtime, "latest_completed_throughput_bps"));
            restorePoint.setBaselineGeneration(completedCycle != null && completedCycle.getBaselineGeneration() != null
                    ? completedCycle.getBaselineGeneration() : longValue(runtime, "latest_completed_baseline_generation"));
            restorePoint.setCycleToken(StringUtils.defaultIfBlank(completedCycle != null
                            ? completedCycle.getCycleToken() : status.getLatestCompletedCycleToken(),
                    stringValue(runtime, "latest_completed_cycle_token")));
            restorePoint.setState("READY");
            restorePoint.markUpdated();
            if (restorePoint.getId() > 0) {
                drRestorePointDao.update(restorePoint.getId(), restorePoint);
            } else {
                drRestorePointDao.persist(restorePoint);
            }
            enforceCheckpointRetention(plan);
        } finally {
            drPlanDao.releaseFromLockTable(plan.getId());
        }
    }

    private void enforceCheckpointRetention(DrPlanVO plan) {
        int retention = DEFAULT_CHECKPOINT_RETENTION;
        JsonObject schedule = parseObject(plan.getScheduleJson());
        Integer configured = firstInteger(integerValue(schedule, "retentionCount"), integerValue(schedule, "retention_count"));
        if (configured != null && configured > 0) {
            retention = Math.min(configured, 1000);
        }
        List<DrRestorePointVO> checkpoints = drRestorePointDao.listActiveByPlanId(plan.getId());
        for (int index = retention; index < checkpoints.size(); index++) {
            DrRestorePointVO expired = checkpoints.get(index);
            expired.markRemoved();
            drRestorePointDao.update(expired.getId(), expired);
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private Long parseLong(String value) {
        try {
            return StringUtils.isBlank(value) ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isSyncTargetReady(DrPlanVO plan, FtctlDrStatusAnswer status, JsonObject runtime, String runtimeState) {
        if (!StringUtils.equalsAny(runtimeState, "READY", "TARGET_READY")) {
            return false;
        }
        if (!hasDurableCheckpoint(status, runtime)) {
            return false;
        }
        if (isExplicitFalse(status != null ? status.getTargetVmPresent() : null, booleanValue(runtime, "target_vm_present"))
                || isExplicitFalse(status != null ? status.getTargetStoragePresent() : null, booleanValue(runtime, "target_storage_present"))
                || isExplicitFalse(status != null ? status.getTargetNetworkPresent() : null, booleanValue(runtime, "target_network_present"))
                || isExplicitFalse(status != null ? status.getRestorePointPresent() : null, booleanValue(runtime, "restore_point_present"))) {
            return false;
        }
        if (!hasTargetReferenceForDirection(plan)) {
            return false;
        }
        return drRestorePointDao != null && drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId()) != null;
    }

    private boolean hasDurableCheckpoint(FtctlDrStatusAnswer status, JsonObject runtime) {
        return parseDate(status != null ? status.getLastTargetDurableAt() : null) != null
                || parseDate(stringValue(runtime, "last_target_durable_at")) != null;
    }

    private boolean hasTargetReferenceForDirection(DrPlanVO plan) {
        if (plan == null || drReplicaDao == null) {
            return false;
        }
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(plan.getId());
        if (replicas == null || replicas.isEmpty()) {
            return false;
        }
        boolean targetAbleStack = StringUtils.endsWithIgnoreCase(plan.getDirection(), "_KVM");
        for (DrReplicaVO replica : replicas) {
            if (replica == null || replica.getRemoved() != null) {
                continue;
            }
            if (targetAbleStack && replica.getTargetVmId() != null) {
                return true;
            }
            if (!targetAbleStack && (StringUtils.isNotBlank(replica.getTargetExternalRef()) || replica.getTargetVmId() != null)) {
                return true;
            }
        }
        return false;
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

    private String compactRuntimeStatusJson(String statusJson) {
        if (StringUtils.isBlank(statusJson)) {
            return null;
        }
        JsonObject runtime = parseObject(statusJson);
        if (runtime.entrySet().isEmpty()) {
            return statusJson;
        }
        JsonObject compact = new JsonObject();
        copyJsonProperty(runtime, compact, "command");
        copyJsonProperty(runtime, compact, "result");
        copyJsonProperty(runtime, compact, "plan_uuid");
        copyJsonProperty(runtime, compact, "run_uuid");
        copyJsonProperty(runtime, compact, "action");
        copyJsonProperty(runtime, compact, "state");
        copyJsonProperty(runtime, compact, "step");
        copyJsonProperty(runtime, compact, "progress");
        copyJsonProperty(runtime, compact, "external_job_ref");
        copyJsonProperty(runtime, compact, "runtime_exists");
        copyJsonProperty(runtime, compact, "profile_exists");
        copyJsonProperty(runtime, compact, "run_exists");
        copyJsonProperty(runtime, compact, "last_source_checkpoint_at");
        copyJsonProperty(runtime, compact, "last_target_durable_at");
        copyJsonProperty(runtime, compact, "target_ready_rpo_seconds");
        copyJsonProperty(runtime, compact, "target_materialized");
        copyJsonProperty(runtime, compact, "target_vm_present");
        copyJsonProperty(runtime, compact, "target_storage_present");
        copyJsonProperty(runtime, compact, "target_network_present");
        copyJsonProperty(runtime, compact, "restore_point_present");
        copyJsonProperty(runtime, compact, "target_vm_id");
        copyJsonProperty(runtime, compact, "target_external_ref");
        copyJsonProperty(runtime, compact, "source_firmware");
        copyJsonProperty(runtime, compact, "source_secure_boot");
        copyJsonProperty(runtime, compact, "source_hardware_fingerprint");
        copyJsonProperty(runtime, compact, "target_boot_type");
        copyJsonProperty(runtime, compact, "target_boot_mode");
        copyJsonProperty(runtime, compact, "target_io_policy");
        copyJsonProperty(runtime, compact, "target_iothreads");
        copyJsonProperty(runtime, compact, "error_code");
        copyJsonProperty(runtime, compact, "error_message");
        copyJsonProperty(runtime, compact, "driver_exit_code");
        copyJsonProperty(runtime, compact, "updated_at");
        copyJsonProperty(runtime, compact, "driver");
        copyJsonProperty(runtime, compact, "driver_state");
        copyJsonProperty(runtime, compact, "disk_map_path");
        copyJsonProperty(runtime, compact, "source_disk_map_path");
        copyJsonProperty(runtime, compact, "target_disk_map_path");
        copyJsonProperty(runtime, compact, "disk_map_role");
        copyJsonProperty(runtime, compact, "target_disk_count");
        copyJsonProperty(runtime, compact, "target_disk_invalid_count");
        copyJsonProperty(runtime, compact, "manifest_path");
        copyJsonProperty(runtime, compact, "checkpoint_path");
        copyJsonProperty(runtime, compact, "cbt_status_path");
        copyJsonProperty(runtime, compact, "source_open_status_path");
        copyJsonProperty(runtime, compact, "source_snapshot_status_path");
        compact.add("cbt_status", compactStatusObject(runtime, "cbt_status"));
        compact.add("source_open", compactStatusObject(runtime, "source_open"));
        compact.add("source_snapshot", compactStatusObject(runtime, "source_snapshot"));
        copyJsonProperty(runtime, compact, "scheduler_state");
        copyJsonProperty(runtime, compact, "scheduler_pid_alive");
        copyJsonProperty(runtime, compact, "runtime_generation");
        copyJsonProperty(runtime, compact, "baseline_state");
        copyJsonProperty(runtime, compact, "reseed_reason");
        copyJsonProperty(runtime, compact, "control_protocol_version");
        copyJsonProperty(runtime, compact, "control_generation");
        copyJsonProperty(runtime, compact, "control_ack_generation");
        copyJsonProperty(runtime, compact, "control_state");
        copyJsonProperty(runtime, compact, "cycle_state");
        copyJsonProperty(runtime, compact, "transition_state");
        copyJsonProperty(runtime, compact, "transition_action");
        copyJsonProperty(runtime, compact, "transition_quiesced_at");
        copyJsonProperty(runtime, compact, "checkpoint_lease_state");
        copyJsonProperty(runtime, compact, "worker_pid");
        copyJsonProperty(runtime, compact, "worker_state");
        copyJsonProperty(runtime, compact, "worker_started_at");
        copyJsonProperty(runtime, compact, "worker_updated_at");
        copyJsonProperty(runtime, compact, "worker_exit_code");
        copyJsonProperty(runtime, compact, "retryable");
        copyJsonProperty(runtime, compact, "retry_after_sec");
        copyJsonProperty(runtime, compact, "lock_file");
        copyJsonProperty(runtime, compact, "holder_pid");
        copyJsonProperty(runtime, compact, "holder_command");
        copyJsonProperty(runtime, compact, "holder_age_sec");
        copyJsonProperty(runtime, compact, "checkpoint_sequence");
        copyJsonProperty(runtime, compact, "active_side");
        copyJsonProperty(runtime, compact, "failover_mode");
        copyJsonProperty(runtime, compact, "failover_session_id");
        copyJsonProperty(runtime, compact, "failover_restore_point_ref");
        copyJsonProperty(runtime, compact, "target_power_state");
        copyJsonProperty(runtime, compact, "target_promotion_state");
        copyJsonProperty(runtime, compact, "source_power_state");
        copyJsonProperty(runtime, compact, "source_promotion_state");
        copyJsonProperty(runtime, compact, "failback_session_id");
        copyJsonProperty(runtime, compact, "failback_restore_point_ref");
        copyJsonProperty(runtime, compact, "reprotect_session_id");
        copyJsonProperty(runtime, compact, "reprotect_restore_point_ref");
        copyJsonProperty(runtime, compact, "reverse_direction");
        copyJsonProperty(runtime, compact, "rto_actual_seconds");
        copyJsonProperty(runtime, compact, "events_offset");
        return GSON.toJson(compact);
    }

    private JsonObject compactStatusObject(JsonObject runtime, String key) {
        JsonObject source = objectValue(runtime, key);
        JsonObject compact = new JsonObject();
        copyJsonProperty(source, compact, "checked");
        copyJsonProperty(source, compact, "ready");
        copyJsonProperty(source, compact, "enabled");
        copyJsonProperty(source, compact, "created");
        copyJsonProperty(source, compact, "cleanupRequired");
        copyJsonProperty(source, compact, "error_code");
        copyJsonProperty(source, compact, "message");
        copyJsonProperty(source, compact, "vmRef");
        copyJsonProperty(source, compact, "snapshotName");
        copyJsonProperty(source, compact, "snapshotRefPresent");
        copyJsonProperty(source, compact, "snapshotRef");
        copyJsonProperty(source, compact, "sourceVmdkPathPresent");
        copyJsonProperty(source, compact, "cbtDiskId");
        copyJsonProperty(source, compact, "sourceDiskRef");
        copyJsonProperty(source, compact, "govcBin");
        copyJsonProperty(source, compact, "resolveMethod");
        copyJsonProperty(source, compact, "checkedAtEpochMs");
        return compact;
    }

    private JsonObject objectValue(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return new JsonObject();
        }
        JsonElement element = object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private JsonObject firstObject(JsonObject object, String... names) {
        if (object == null || names == null) {
            return new JsonObject();
        }
        for (String name : names) {
            JsonObject value = objectValue(object, name);
            if (!value.entrySet().isEmpty()) {
                return value;
            }
        }
        return new JsonObject();
    }

    private void copyJsonProperty(JsonObject source, JsonObject target, String name) {
        if (source == null || target == null || !source.has(name) || source.get(name).isJsonNull()) {
            return;
        }
        JsonElement element = source.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return;
        }
        target.add(name, JsonParser.parseString(GSON.toJson(element)));
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

    private Long longValue(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        try {
            return object.get(name).getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Boolean booleanValue(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        try {
            return object.get(name).getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isExplicitFalse(Boolean first, Boolean second) {
        return Boolean.FALSE.equals(first) || Boolean.FALSE.equals(second);
    }

    private Integer firstInteger(Integer first, Integer fallback) {
        return first != null ? first : fallback;
    }

    private Date firstDate(Date... values) {
        if (values == null) {
            return null;
        }
        for (Date value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
            details.addProperty("targetMaterialized", booleanValue(runtime, "target_materialized"));
            details.addProperty("targetVmPresent", booleanValue(runtime, "target_vm_present"));
            details.addProperty("targetStoragePresent", booleanValue(runtime, "target_storage_present"));
            details.addProperty("targetNetworkPresent", booleanValue(runtime, "target_network_present"));
            details.addProperty("restorePointPresent", booleanValue(runtime, "restore_point_present"));
            details.addProperty("eventsOffset", status.getEventsOffset());
            details.addProperty("errorCode", status.getErrorCode());
            details.addProperty("errorMessage", status.getErrorMessage());
            details.addProperty("failedComponent", status.getFailedComponent());
            details.addProperty("dataCommitState", status.getDataCommitState());
            details.addProperty("dataCopied", status.getDataCopied());
            details.addProperty("metadataCommitted", status.getMetadataCommitted());
            details.addProperty("targetDurable", status.getTargetDurable());
            details.addProperty("cycleRetryMode", status.getCycleRetryMode());
            details.addProperty("exitCode", status.getExitCode());
            details.addProperty("sourceDiskMapPath", StringUtils.defaultIfBlank(status.getSourceDiskMapPath(), stringValue(runtime, "source_disk_map_path")));
            details.addProperty("targetDiskMapPath", StringUtils.defaultIfBlank(status.getTargetDiskMapPath(), stringValue(runtime, "target_disk_map_path")));
            details.addProperty("diskMapRole", StringUtils.defaultIfBlank(status.getDiskMapRole(), stringValue(runtime, "disk_map_role")));
            details.addProperty("targetDiskCount", firstInteger(status.getTargetDiskCount(), integerValue(runtime, "target_disk_count")));
            details.addProperty("targetDiskInvalidCount", firstInteger(status.getTargetDiskInvalidCount(), integerValue(runtime, "target_disk_invalid_count")));
            details.addProperty("workerState", stringValue(runtime, "worker_state"));
            details.addProperty("workerPid", integerValue(runtime, "worker_pid"));
            details.addProperty("workerStartedAt", stringValue(runtime, "worker_started_at"));
            details.addProperty("workerUpdatedAt", stringValue(runtime, "worker_updated_at"));
            details.addProperty("workerExitCode", integerValue(runtime, "worker_exit_code"));
            details.addProperty("controlProtocolVersion", firstInteger(status.getControlProtocolVersion(), integerValue(runtime, "control_protocol_version")));
            details.addProperty("controlGeneration", status.getControlGeneration() != null ? status.getControlGeneration() : parseLong(stringValue(runtime, "control_generation")));
            details.addProperty("controlAckGeneration", status.getControlAckGeneration() != null ? status.getControlAckGeneration() : parseLong(stringValue(runtime, "control_ack_generation")));
            details.addProperty("controlState", StringUtils.defaultIfBlank(status.getControlState(), stringValue(runtime, "control_state")));
            details.addProperty("cycleState", StringUtils.defaultIfBlank(status.getCycleState(), stringValue(runtime, "cycle_state")));
            details.addProperty("transitionState", StringUtils.defaultIfBlank(status.getTransitionState(), stringValue(runtime, "transition_state")));
            details.addProperty("checkpointLeaseState", StringUtils.defaultIfBlank(status.getCheckpointLeaseState(), stringValue(runtime, "checkpoint_lease_state")));
            details.add("sourceSnapshot", compactStatusObject(runtime, "source_snapshot"));
            details.addProperty("retryable", booleanValue(runtime, "retryable"));
            details.addProperty("retryAfterSeconds", integerValue(runtime, "retry_after_sec"));
            details.addProperty("lockFile", stringValue(runtime, "lock_file"));
            details.addProperty("holderPid", integerValue(runtime, "holder_pid"));
            details.addProperty("holderCommand", stringValue(runtime, "holder_command"));
            details.addProperty("holderAgeSeconds", integerValue(runtime, "holder_age_sec"));
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

    private void persistRunProjectionEvent(DrPlanVO plan, DrRunVO run, String eventType, String severity,
            String message, String detailsJson) {
        DrEventVO event = new DrEventVO(eventType, severity, DrConstants.EVENT_SOURCE_FTCTL_DR);
        event.setPlanId(plan.getId());
        event.setRunId(run != null ? run.getId() : null);
        event.setMessage(message);
        event.setDetailsJson(compactRuntimeStatusJson(detailsJson));
        drEventDao.persist(event);
    }
}
