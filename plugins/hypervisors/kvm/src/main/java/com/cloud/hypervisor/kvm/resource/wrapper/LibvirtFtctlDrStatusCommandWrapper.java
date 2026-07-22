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
package com.cloud.hypervisor.kvm.resource.wrapper;

import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrCycleSnapshot;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.gson.JsonObject;

@ResourceWrapper(handles = FtctlDrStatusCommand.class)
public class LibvirtFtctlDrStatusCommandWrapper extends CommandWrapper<FtctlDrStatusCommand, Answer, LibvirtComputingResource> {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int STATUS_HARD_TIMEOUT_SECONDS = 5;
    private static final int STATUS_KILL_AFTER_SECONDS = 2;
    private static final String ERROR_STATUS_TIMEOUT = "DR_STATUS_TIMEOUT";
    private static final String ERROR_STATUS_INVALID_JSON = "DR_STATUS_INVALID_JSON";
    private static final String ERROR_STATUS_IDENTITY_MISMATCH = "DR_STATUS_IDENTITY_MISMATCH";
    private static final String ERROR_STATUS_PAYLOAD_TOO_LARGE = "DR_STATUS_PAYLOAD_TOO_LARGE";
    private static final String ERROR_STATUS_TYPE_MISMATCH = "DR_STATUS_TYPE_MISMATCH";
    private static final String ERROR_STATUS_CYCLE_SNAPSHOT_INCOHERENT = "DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT";
    private static final int MAX_STATUS_BYTES = 256 * 1024;

    @Override
    public Answer execute(FtctlDrStatusCommand command, LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(command.getPlanUuid())) {
            return new FtctlDrStatusAnswer(command, false, "Missing DR plan UUID");
        }

        final int requestedTimeoutSeconds = command.getWait() > 0 ? command.getWait() : DEFAULT_TIMEOUT_SECONDS;
        final int boundedTimeoutSeconds = Math.min(requestedTimeoutSeconds, STATUS_HARD_TIMEOUT_SECONDS);
        final long wrapperTimeout = (long) (boundedTimeoutSeconds + STATUS_KILL_AFTER_SECONDS + 1) * 1000;
        Script script = new Script("timeout", wrapperTimeout, logger);
        script.add("--kill-after=" + STATUS_KILL_AFTER_SECONDS + "s");
        script.add(boundedTimeoutSeconds + "s");
        script.add("ablestack_vm_ftctl");
        script.add("dr-status");
        String requestedRunUuid = command.getStatusScope() == FtctlDrStatusCommand.StatusScope.OPERATION
                ? command.getRunUuid() : null;
        LibvirtFtctlDrCommandHelper.addPlanRunArgs(script, command.getPlanUuid(), requestedRunUuid);
        if (command.getEventsOffset() != null) {
            script.add("--events-offset");
            script.add(String.valueOf(command.getEventsOffset()));
        }
        script.add("--events-limit");
        script.add("0");
        script.add("--json");

        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
        String result = script.execute(parser);
        String output = LibvirtFtctlWrapperHelper.getOutput(result, parser);
        int exitValue = script.getExitValue();
        if (isTimeout(exitValue, result, output)) {
            return timeoutAnswer(command, output, exitValue);
        }
        if (output.getBytes(StandardCharsets.UTF_8).length > MAX_STATUS_BYTES) {
            return validationAnswer(command, ERROR_STATUS_PAYLOAD_TOO_LARGE,
                    "FTCTL_DR status payload exceeded 256 KiB", exitValue);
        }
        JsonObject payload = LibvirtFtctlWrapperHelper.parseSingleJsonObject(output);
        if (payload == null) {
            return validationAnswer(command, ERROR_STATUS_INVALID_JSON,
                    "FTCTL_DR status was not one strict JSON object", exitValue);
        }
        String returnedPlanUuid = LibvirtFtctlDrCommandHelper.getString(payload, "plan_uuid");
        String returnedRunUuid = LibvirtFtctlDrCommandHelper.getString(payload, "run_uuid");
        if (!StringUtils.equals(command.getPlanUuid(), returnedPlanUuid)
                || (command.getStatusScope() == FtctlDrStatusCommand.StatusScope.OPERATION
                && StringUtils.isNotBlank(command.getRunUuid()) && !StringUtils.equals(command.getRunUuid(), returnedRunUuid))) {
            return validationAnswer(command, ERROR_STATUS_IDENTITY_MISMATCH,
                    "FTCTL_DR status identity did not match the requested plan/run", exitValue);
        }
        if (!hasValidStatusTypes(payload)) {
            return validationAnswer(command, ERROR_STATUS_TYPE_MISMATCH,
                    "FTCTL_DR status contained a field with an invalid JSON type", exitValue);
        }
        boolean success = exitValue == 0;
        String payloadPlanUuid = returnedPlanUuid;
        String payloadRunUuid = returnedRunUuid;
        String payloadErrorMessage = LibvirtFtctlDrCommandHelper.getString(payload, "error_message");
        String answerDetails = StringUtils.defaultIfBlank(payloadErrorMessage,
                success ? "OK" : "FTCTL_DR status failed");

        FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, success, answerDetails,
                payloadPlanUuid, payloadRunUuid,
                LibvirtFtctlDrCommandHelper.getString(payload, "result"),
                LibvirtFtctlDrCommandHelper.getString(payload, "state"),
                LibvirtFtctlDrCommandHelper.getString(payload, "step"),
                LibvirtFtctlDrCommandHelper.getInteger(payload, "progress"),
                LibvirtFtctlDrCommandHelper.getString(payload, "last_source_checkpoint_at"),
                LibvirtFtctlDrCommandHelper.getString(payload, "last_target_durable_at"),
                LibvirtFtctlDrCommandHelper.getInteger(payload, "target_ready_rpo_seconds"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "target_materialized"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "target_vm_present"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "target_storage_present"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "target_network_present"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "restore_point_present"),
                LibvirtFtctlDrCommandHelper.getLong(payload, "events_offset"),
                LibvirtFtctlDrCommandHelper.getString(payload, "error_code"),
                exitValue, output, payload != null ? payload.toString() : null);
        answer.setStatusScope(LibvirtFtctlDrCommandHelper.getString(payload, "status_scope"));
        answer.setErrorMessage(payloadErrorMessage);
        answer.setFailedComponent(LibvirtFtctlDrCommandHelper.getString(payload, "failed_component"));
        answer.setDataCommitState(LibvirtFtctlDrCommandHelper.getString(payload, "data_commit_state"));
        answer.setDataCopied(LibvirtFtctlDrCommandHelper.getBoolean(payload, "data_copied"));
        answer.setMetadataCommitted(LibvirtFtctlDrCommandHelper.getBoolean(payload, "metadata_committed"));
        answer.setTargetDurable(LibvirtFtctlDrCommandHelper.getBoolean(payload, "target_durable"));
        answer.setCycleRetryMode(LibvirtFtctlDrCommandHelper.getString(payload, "cycle_retry_mode"));
        answer.setSourceDiskMapPath(LibvirtFtctlDrCommandHelper.getString(payload, "source_disk_map_path"));
        answer.setTargetDiskMapPath(LibvirtFtctlDrCommandHelper.getString(payload, "target_disk_map_path"));
        answer.setDiskMapRole(LibvirtFtctlDrCommandHelper.getString(payload, "disk_map_role"));
        answer.setTargetDiskCount(LibvirtFtctlDrCommandHelper.getInteger(payload, "target_disk_count"));
        answer.setTargetDiskInvalidCount(LibvirtFtctlDrCommandHelper.getInteger(payload, "target_disk_invalid_count"));
        answer.setWorkerState(LibvirtFtctlDrCommandHelper.getString(payload, "worker_state"));
        answer.setWorkerPid(LibvirtFtctlDrCommandHelper.getInteger(payload, "worker_pid"));
        answer.setWorkerStartedAt(LibvirtFtctlDrCommandHelper.getString(payload, "worker_started_at"));
        answer.setWorkerUpdatedAt(LibvirtFtctlDrCommandHelper.getString(payload, "worker_updated_at"));
        answer.setWorkerExitCode(LibvirtFtctlDrCommandHelper.getInteger(payload, "worker_exit_code"));
        answer.setRetryable(LibvirtFtctlDrCommandHelper.getBoolean(payload, "retryable"));
        answer.setRetryAfterSeconds(LibvirtFtctlDrCommandHelper.getInteger(payload, "retry_after_sec"));
        answer.setCurrentCheckpointSequence(LibvirtFtctlDrCommandHelper.getLong(payload, "current_checkpoint_sequence"));
        answer.setCurrentCheckpointCycleType(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_cycle_type"));
        answer.setCurrentCheckpointRequestedMode(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_requested_mode"));
        answer.setCurrentCheckpointEffectiveMode(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_effective_mode"));
        answer.setCurrentCheckpointModeDecisionCode(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_mode_decision_code"));
        answer.setCurrentCheckpointAutomaticReseed(LibvirtFtctlDrCommandHelper.getBoolean(payload, "current_checkpoint_automatic_reseed"));
        answer.setCurrentCheckpointInvalidBaselineDiskCount(LibvirtFtctlDrCommandHelper.getInteger(payload, "current_checkpoint_invalid_baseline_disk_count"));
        answer.setCurrentCheckpointRef(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_ref"));
        answer.setCurrentCheckpointState(LibvirtFtctlDrCommandHelper.getString(payload, "current_checkpoint_state"));
        answer.setLatestCompletedCheckpointSequence(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_checkpoint_sequence"));
        answer.setLatestCompletedCheckpointCycleType(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_checkpoint_cycle_type"));
        answer.setLatestCompletedCheckpointRef(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_checkpoint_ref"));
        answer.setLatestCompletedCheckpointState(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_checkpoint_state"));
        answer.setLatestCompletedProducerRunUuid(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_producer_run_uuid"));
        answer.setLatestCompletedSourceCheckpointAt(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_source_checkpoint_at"));
        answer.setLatestCompletedTargetDurableAt(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_target_durable_at"));
        answer.setLatestCompletedTargetReadyRpoSeconds(LibvirtFtctlDrCommandHelper.getInteger(payload, "latest_completed_target_ready_rpo_seconds"));
        answer.setLatestCompletedManifestPath(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_manifest_path"));
        answer.setLatestCompletedCheckpointPath(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_checkpoint_path"));
        answer.setLatestCompletedRequestedMode(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_requested_mode"));
        answer.setLatestCompletedEffectiveMode(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_effective_mode"));
        answer.setLatestCompletedModeDecisionCode(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_mode_decision_code"));
        answer.setLatestCompletedReseedReason(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_reseed_reason"));
        answer.setLatestCompletedAutomaticReseed(LibvirtFtctlDrCommandHelper.getBoolean(payload, "latest_completed_automatic_reseed"));
        answer.setLatestCompletedInvalidBaselineDiskCount(LibvirtFtctlDrCommandHelper.getInteger(payload, "latest_completed_invalid_baseline_disk_count"));
        answer.setLatestCompletedIncrementalVerified(LibvirtFtctlDrCommandHelper.getBoolean(payload, "latest_completed_incremental_verified"));
        answer.setLatestCompletedMetricsEstimated(LibvirtFtctlDrCommandHelper.getBoolean(payload, "latest_completed_metrics_estimated"));
        answer.setLatestCompletedVirtualBytes(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_virtual_bytes"));
        answer.setLatestCompletedChangedBytes(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_changed_bytes"));
        answer.setLatestCompletedSourceReadBytes(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_source_read_bytes"));
        answer.setLatestCompletedTargetWrittenBytes(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_target_written_bytes"));
        answer.setLatestCompletedTransferPayloadBytes(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_transfer_payload_bytes"));
        answer.setLatestCompletedChangedExtentCount(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_changed_extent_count"));
        answer.setLatestCompletedDurationMs(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_duration_ms"));
        answer.setLatestCompletedThroughputBps(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_throughput_bps"));
        answer.setLatestCompletedBaselineGeneration(LibvirtFtctlDrCommandHelper.getLong(payload, "latest_completed_baseline_generation"));
        answer.setLatestCompletedCycleToken(LibvirtFtctlDrCommandHelper.getString(payload, "latest_completed_cycle_token"));
        answer.setActiveWorkerRunUuid(LibvirtFtctlDrCommandHelper.getString(payload, "active_worker_run_uuid"));
        FtctlDrCycleSnapshot currentCycle = buildCurrentCycleSnapshot(answer, payloadPlanUuid, payloadRunUuid);
        FtctlDrCycleSnapshot latestCompletedCycle = buildLatestCompletedCycleSnapshot(answer, payloadPlanUuid, payloadRunUuid);
        if (!isCoherentLatestCompletedCycle(latestCompletedCycle)) {
            return validationAnswer(command, ERROR_STATUS_CYCLE_SNAPSHOT_INCOHERENT,
                    "FTCTL_DR latest completed cycle identity/generation was incoherent", exitValue);
        }
        answer.setCycleContractVersion(1);
        answer.setCurrentCycle(currentCycle);
        answer.setLatestCompletedCycle(latestCompletedCycle);
        answer.setControlProtocolVersion(LibvirtFtctlDrCommandHelper.getInteger(payload, "control_protocol_version"));
        answer.setControlGeneration(LibvirtFtctlDrCommandHelper.getLong(payload, "control_generation"));
        answer.setControlAckGeneration(LibvirtFtctlDrCommandHelper.getLong(payload, "control_ack_generation"));
        answer.setControlState(LibvirtFtctlDrCommandHelper.getString(payload, "control_state"));
        answer.setCycleState(LibvirtFtctlDrCommandHelper.getString(payload, "cycle_state"));
        answer.setTransitionState(LibvirtFtctlDrCommandHelper.getString(payload, "transition_state"));
        answer.setCheckpointLeaseState(LibvirtFtctlDrCommandHelper.getString(payload, "checkpoint_lease_state"));
        answer.setGuestPreparationState(LibvirtFtctlDrCommandHelper.getString(payload, "guest_prep_state"));
        answer.setGuestFamily(LibvirtFtctlDrCommandHelper.getString(payload, "guest_family"));
        answer.setGuestPreparationManifestPath(LibvirtFtctlDrCommandHelper.getString(payload, "guestprep_manifest_path"));
        answer.setManifestSchemaVersion(LibvirtFtctlDrCommandHelper.getString(payload, "manifest_schema_version"));
        answer.setManifestSha256(LibvirtFtctlDrCommandHelper.getString(payload, "manifest_sha256"));
        answer.setGuestPreparationCheckpointSequence(LibvirtFtctlDrCommandHelper.getLong(payload, "guestprep_checkpoint_sequence"));
        answer.setTestDomainName(LibvirtFtctlDrCommandHelper.getString(payload, "test_domain_name"));
        answer.setTestDomainState(LibvirtFtctlDrCommandHelper.getString(payload, "test_domain_state"));
        answer.setTestBootValidationMode(LibvirtFtctlDrCommandHelper.getString(payload, "test_boot_validation_mode"));
        answer.setRuntimeGeneration(LibvirtFtctlDrCommandHelper.getLong(payload, "runtime_generation"));
        answer.setSchedulerPidAlive(LibvirtFtctlDrCommandHelper.getBoolean(payload, "scheduler_pid_alive"));
        answer.setSchedulerDesiredState(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_desired_state"));
        answer.setSchedulerServiceUnit(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_service_unit"));
        answer.setSchedulerUnitActiveState(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_unit_active_state"));
        answer.setSchedulerUnitSubState(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_unit_sub_state"));
        answer.setSchedulerUnitMainPid(LibvirtFtctlDrCommandHelper.getLong(payload, "scheduler_unit_main_pid"));
        answer.setSchedulerCgroup(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_cgroup"));
        answer.setSchedulerRecoveryState(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_recovery_state"));
        answer.setSchedulerRecoveryTrigger(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_recovery_trigger"));
        answer.setSchedulerRecoveredAt(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_recovered_at"));
        answer.setSchedulerSessionUuid(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_session_uuid"));
        answer.setSchedulerLeaseEpoch(LibvirtFtctlDrCommandHelper.getLong(payload, "scheduler_lease_epoch"));
        answer.setAuthoritySequence(LibvirtFtctlDrCommandHelper.getLong(payload, "authority_sequence"));
        answer.setPlanCycleSequence(LibvirtFtctlDrCommandHelper.getLong(payload, "plan_cycle_sequence"));
        answer.setSchedulerHealth(LibvirtFtctlDrCommandHelper.getString(payload, "scheduler_health"));
        answer.setReplicationActivity(LibvirtFtctlDrCommandHelper.getString(payload, "replication_activity"));
        answer.setProtectionState(LibvirtFtctlDrCommandHelper.getString(payload, "protection_state"));
        answer.setActiveWorkerRunUuid(LibvirtFtctlDrCommandHelper.getString(payload, "active_worker_run_uuid"));
        answer.setActiveWorkerPid(LibvirtFtctlDrCommandHelper.getLong(payload, "active_worker_pid"));
        answer.setActiveWorkerStartTicks(LibvirtFtctlDrCommandHelper.getLong(payload, "active_worker_start_ticks"));
        answer.setWorkerHeartbeatAt(LibvirtFtctlDrCommandHelper.getString(payload, "worker_heartbeat_at"));
        answer.setControlRequestRunUuid(LibvirtFtctlDrCommandHelper.getString(payload, "control_request_run_uuid"));
        answer.setOwnerMatched(LibvirtFtctlDrCommandHelper.getBoolean(payload, "owner_matched"));
        answer.setBaselineState(LibvirtFtctlDrCommandHelper.getString(payload, "baseline_state"));
        answer.setReseedReason(LibvirtFtctlDrCommandHelper.getString(payload, "reseed_reason"));
        answer.setConsecutiveAutomaticReseedCount(LibvirtFtctlDrCommandHelper.getInteger(payload, "consecutive_automatic_reseed_count"));
        return answer;
    }

    private FtctlDrCycleSnapshot buildCurrentCycleSnapshot(FtctlDrStatusAnswer answer, String planUuid, String runUuid) {
        if (answer.getCurrentCheckpointSequence() == null) {
            return null;
        }
        FtctlDrCycleSnapshot snapshot = new FtctlDrCycleSnapshot();
        snapshot.setPlanUuid(planUuid);
        snapshot.setRunUuid(StringUtils.defaultIfBlank(answer.getActiveWorkerRunUuid(), runUuid));
        snapshot.setSequence(answer.getCurrentCheckpointSequence());
        snapshot.setState(answer.getCurrentCheckpointState());
        snapshot.setRequestedMode(answer.getCurrentCheckpointRequestedMode());
        snapshot.setEffectiveMode(answer.getCurrentCheckpointEffectiveMode());
        return snapshot;
    }

    private FtctlDrCycleSnapshot buildLatestCompletedCycleSnapshot(FtctlDrStatusAnswer answer, String planUuid, String runUuid) {
        if (answer.getLatestCompletedCheckpointSequence() == null) {
            return null;
        }
        FtctlDrCycleSnapshot snapshot = new FtctlDrCycleSnapshot();
        snapshot.setPlanUuid(planUuid);
        snapshot.setRunUuid(StringUtils.defaultIfBlank(answer.getLatestCompletedProducerRunUuid(),
                StringUtils.defaultIfBlank(answer.getActiveWorkerRunUuid(), runUuid)));
        snapshot.setSequence(answer.getLatestCompletedCheckpointSequence());
        snapshot.setCycleToken(StringUtils.defaultIfBlank(answer.getLatestCompletedCycleToken(),
                planUuid + ":" + answer.getLatestCompletedCheckpointSequence()));
        snapshot.setState(answer.getLatestCompletedCheckpointState());
        snapshot.setRequestedMode(answer.getLatestCompletedRequestedMode());
        snapshot.setEffectiveMode(answer.getLatestCompletedEffectiveMode());
        snapshot.setBaselineGeneration(answer.getLatestCompletedBaselineGeneration());
        snapshot.setIncrementalVerified(answer.getLatestCompletedIncrementalVerified());
        snapshot.setMetricsEstimated(answer.getLatestCompletedMetricsEstimated());
        snapshot.setVirtualBytes(answer.getLatestCompletedVirtualBytes());
        snapshot.setChangedBytes(answer.getLatestCompletedChangedBytes());
        snapshot.setSourceReadBytes(answer.getLatestCompletedSourceReadBytes());
        snapshot.setTargetWrittenBytes(answer.getLatestCompletedTargetWrittenBytes());
        snapshot.setTransferPayloadBytes(answer.getLatestCompletedTransferPayloadBytes());
        snapshot.setChangedExtentCount(answer.getLatestCompletedChangedExtentCount());
        snapshot.setDurationMs(answer.getLatestCompletedDurationMs());
        snapshot.setThroughputBps(answer.getLatestCompletedThroughputBps());
        snapshot.setSourceCheckpointAt(answer.getLatestCompletedSourceCheckpointAt());
        snapshot.setTargetDurableAt(answer.getLatestCompletedTargetDurableAt());
        return snapshot;
    }

    private boolean isCoherentLatestCompletedCycle(FtctlDrCycleSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        if (snapshot.getSequence() == null || StringUtils.isBlank(snapshot.getPlanUuid())) {
            return false;
        }
        String expectedToken = snapshot.getPlanUuid() + ":" + snapshot.getSequence();
        if (StringUtils.isNotBlank(snapshot.getCycleToken()) && !StringUtils.equals(expectedToken, snapshot.getCycleToken())) {
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

    private boolean hasValidStatusTypes(JsonObject payload) {
        String[] booleans = {"accepted", "target_materialized", "target_vm_present", "target_storage_present",
                "target_network_present", "restore_point_present", "data_copied", "metadata_committed",
                "target_durable", "retryable", "latest_completed_incremental_verified",
                "latest_completed_metrics_estimated", "current_checkpoint_automatic_reseed",
                "latest_completed_automatic_reseed", "scheduler_pid_alive", "owner_matched", "events_truncated"};
        for (String field : booleans) {
            if (!LibvirtFtctlWrapperHelper.isBooleanOrNull(payload, field)) {
                return false;
            }
        }
        String[] numbers = {"progress", "target_ready_rpo_seconds", "events_offset", "events_next_offset",
                "events_invalid_count", "target_disk_count", "target_disk_invalid_count", "worker_pid",
                "worker_exit_code", "retry_after_sec", "current_checkpoint_sequence", "guestprep_checkpoint_sequence",
                "latest_completed_checkpoint_sequence", "latest_completed_target_ready_rpo_seconds",
                "latest_completed_virtual_bytes", "latest_completed_changed_bytes", "latest_completed_source_read_bytes",
                "latest_completed_target_written_bytes", "latest_completed_transfer_payload_bytes",
                "latest_completed_changed_extent_count", "latest_completed_duration_ms",
                "latest_completed_throughput_bps", "latest_completed_baseline_generation",
                "current_checkpoint_invalid_baseline_disk_count", "latest_completed_invalid_baseline_disk_count",
                "consecutive_automatic_reseed_count", "runtime_generation", "scheduler_lease_epoch",
                "authority_sequence", "plan_cycle_sequence", "active_worker_pid", "active_worker_start_ticks",
                "scheduler_unit_main_pid"};
        for (String field : numbers) {
            if (!LibvirtFtctlWrapperHelper.isNumberOrNull(payload, field)) {
                return false;
            }
        }
        return true;
    }

    private Answer validationAnswer(FtctlDrStatusCommand command, String errorCode, String message, int exitValue) {
        JsonObject payload = new JsonObject();
        payload.addProperty("command", "dr-status");
        payload.addProperty("result", "error");
        payload.addProperty("plan_uuid", command.getPlanUuid());
        if (StringUtils.isNotBlank(command.getRunUuid())) {
            payload.addProperty("run_uuid", command.getRunUuid());
        }
        payload.addProperty("accepted", false);
        payload.addProperty("state", "UNKNOWN");
        payload.addProperty("step", "status-validation");
        payload.addProperty("progress", 0);
        payload.addProperty("error_code", errorCode);
        payload.addProperty("error_message", message);
        return new FtctlDrStatusAnswer(command, false, message, command.getPlanUuid(), command.getRunUuid(),
                "error", "UNKNOWN", "status-validation", 0, null, null, null, command.getEventsOffset(),
                errorCode, exitValue, message, payload.toString());
    }

    private boolean isTimeout(int exitValue, String result, String output) {
        String text = StringUtils.defaultString(result) + "\n" + StringUtils.defaultString(output);
        return exitValue == 124 || exitValue == 137 || StringUtils.containsIgnoreCase(text, "timed out");
    }

    private Answer timeoutAnswer(FtctlDrStatusCommand command, String output, int exitValue) {
        String message = "FTCTL_DR status timed out";
        JsonObject payload = new JsonObject();
        payload.addProperty("command", "dr-status");
        payload.addProperty("result", "timeout");
        payload.addProperty("plan_uuid", command.getPlanUuid());
        if (StringUtils.isNotBlank(command.getRunUuid())) {
            payload.addProperty("run_uuid", command.getRunUuid());
        }
        payload.addProperty("accepted", false);
        payload.addProperty("state", "UNKNOWN");
        payload.addProperty("step", "status-timeout");
        payload.addProperty("progress", 0);
        payload.addProperty("error_code", ERROR_STATUS_TIMEOUT);
        payload.addProperty("message", message);
        payload.addProperty("exit_code", exitValue);
        return new FtctlDrStatusAnswer(command, false, message, command.getPlanUuid(), command.getRunUuid(),
                "timeout", "UNKNOWN", "status-timeout", 0, null, null, null, command.getEventsOffset(),
                ERROR_STATUS_TIMEOUT, exitValue, StringUtils.defaultIfBlank(output, message), payload.toString());
    }
}
