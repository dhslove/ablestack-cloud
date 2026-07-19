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
package com.cloud.agent.api;

public class FtctlDrStatusAnswer extends Answer {

    private String planUuid;
    private String runUuid;
    private String ftctlResult;
    private String state;
    private String step;
    private Integer progress;
    private String lastSourceCheckpointAt;
    private String lastTargetDurableAt;
    private Integer targetReadyRpoSeconds;
    private Boolean targetMaterialized;
    private Boolean targetVmPresent;
    private Boolean targetStoragePresent;
    private Boolean targetNetworkPresent;
    private Boolean restorePointPresent;
    private Long eventsOffset;
    private String errorCode;
    private String errorMessage;
    private String failedComponent;
    private String dataCommitState;
    private Boolean dataCopied;
    private Boolean metadataCommitted;
    private Boolean targetDurable;
    private String cycleRetryMode;
    private Integer exitCode;
    private String output;
    private String statusJson;
    private String sourceDiskMapPath;
    private String targetDiskMapPath;
    private String diskMapRole;
    private Integer targetDiskCount;
    private Integer targetDiskInvalidCount;
    private String workerState;
    private Integer workerPid;
    private String workerStartedAt;
    private String workerUpdatedAt;
    private Integer workerExitCode;
    private Boolean retryable;
    private Integer retryAfterSeconds;
    private Long currentCheckpointSequence;
    private String currentCheckpointCycleType;
    private String currentCheckpointRequestedMode;
    private String currentCheckpointEffectiveMode;
    private String currentCheckpointModeDecisionCode;
    private Boolean currentCheckpointAutomaticReseed;
    private Integer currentCheckpointInvalidBaselineDiskCount;
    private String currentCheckpointRef;
    private String currentCheckpointState;
    private Long latestCompletedCheckpointSequence;
    private String latestCompletedCheckpointCycleType;
    private String latestCompletedCheckpointRef;
    private String latestCompletedCheckpointState;
    private String latestCompletedSourceCheckpointAt;
    private String latestCompletedTargetDurableAt;
    private Integer latestCompletedTargetReadyRpoSeconds;
    private String latestCompletedManifestPath;
    private String latestCompletedCheckpointPath;
    private String latestCompletedRequestedMode;
    private String latestCompletedEffectiveMode;
    private String latestCompletedModeDecisionCode;
    private String latestCompletedReseedReason;
    private Boolean latestCompletedAutomaticReseed;
    private Integer latestCompletedInvalidBaselineDiskCount;
    private Boolean latestCompletedIncrementalVerified;
    private Boolean latestCompletedMetricsEstimated;
    private Long latestCompletedVirtualBytes;
    private Long latestCompletedChangedBytes;
    private Long latestCompletedSourceReadBytes;
    private Long latestCompletedTargetWrittenBytes;
    private Long latestCompletedTransferPayloadBytes;
    private Long latestCompletedChangedExtentCount;
    private Long latestCompletedDurationMs;
    private Long latestCompletedThroughputBps;
    private Long latestCompletedBaselineGeneration;
    private String latestCompletedCycleToken;
    private Integer controlProtocolVersion;
    private Long controlGeneration;
    private Long controlAckGeneration;
    private String controlState;
    private String cycleState;
    private String transitionState;
    private String checkpointLeaseState;
    private String guestPreparationState;
    private String guestFamily;
    private String guestPreparationManifestPath;
    private String testDomainName;
    private String testDomainState;
    private String testBootValidationMode;
    private Long runtimeGeneration;
    private Boolean schedulerPidAlive;
    private String baselineState;
    private String reseedReason;
    private Integer consecutiveAutomaticReseedCount;
    private Integer cycleContractVersion;
    private FtctlDrCycleSnapshot currentCycle;
    private FtctlDrCycleSnapshot latestCompletedCycle;

    public FtctlDrStatusAnswer(Command command, boolean success, String details) {
        super(command, success, details);
    }

    public FtctlDrStatusAnswer(Command command, boolean success, String details, String planUuid, String runUuid,
            String ftctlResult, String state, String step, Integer progress, String lastSourceCheckpointAt,
            String lastTargetDurableAt, Integer targetReadyRpoSeconds, Long eventsOffset, String errorCode,
            Integer exitCode, String output, String statusJson) {
        this(command, success, details, planUuid, runUuid, ftctlResult, state, step, progress, lastSourceCheckpointAt,
                lastTargetDurableAt, targetReadyRpoSeconds, null, null, null, null, null, eventsOffset, errorCode,
                exitCode, output, statusJson);
    }

    public FtctlDrStatusAnswer(Command command, boolean success, String details, String planUuid, String runUuid,
            String ftctlResult, String state, String step, Integer progress, String lastSourceCheckpointAt,
            String lastTargetDurableAt, Integer targetReadyRpoSeconds, Boolean targetMaterialized,
            Boolean targetVmPresent, Boolean targetStoragePresent, Boolean targetNetworkPresent,
            Boolean restorePointPresent, Long eventsOffset, String errorCode, Integer exitCode, String output,
            String statusJson) {
        super(command, success, details);
        this.planUuid = planUuid;
        this.runUuid = runUuid;
        this.ftctlResult = ftctlResult;
        this.state = state;
        this.step = step;
        this.progress = progress;
        this.lastSourceCheckpointAt = lastSourceCheckpointAt;
        this.lastTargetDurableAt = lastTargetDurableAt;
        this.targetReadyRpoSeconds = targetReadyRpoSeconds;
        this.targetMaterialized = targetMaterialized;
        this.targetVmPresent = targetVmPresent;
        this.targetStoragePresent = targetStoragePresent;
        this.targetNetworkPresent = targetNetworkPresent;
        this.restorePointPresent = restorePointPresent;
        this.eventsOffset = eventsOffset;
        this.errorCode = errorCode;
        this.exitCode = exitCode;
        this.output = output;
        this.statusJson = statusJson;
    }

    public String getPlanUuid() {
        return planUuid;
    }

    public String getRunUuid() {
        return runUuid;
    }

    public String getFtctlResult() {
        return ftctlResult;
    }

    public String getState() {
        return state;
    }

    public String getStep() {
        return step;
    }

    public Integer getProgress() {
        return progress;
    }

    public String getLastSourceCheckpointAt() {
        return lastSourceCheckpointAt;
    }

    public String getLastTargetDurableAt() {
        return lastTargetDurableAt;
    }

    public Integer getTargetReadyRpoSeconds() {
        return targetReadyRpoSeconds;
    }

    public Boolean getTargetMaterialized() {
        return targetMaterialized;
    }

    public Boolean getTargetVmPresent() {
        return targetVmPresent;
    }

    public Boolean getTargetStoragePresent() {
        return targetStoragePresent;
    }

    public Boolean getTargetNetworkPresent() {
        return targetNetworkPresent;
    }

    public Boolean getRestorePointPresent() {
        return restorePointPresent;
    }

    public Long getEventsOffset() {
        return eventsOffset;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFailedComponent() {
        return failedComponent;
    }

    public void setFailedComponent(String failedComponent) {
        this.failedComponent = failedComponent;
    }

    public String getDataCommitState() {
        return dataCommitState;
    }

    public void setDataCommitState(String dataCommitState) {
        this.dataCommitState = dataCommitState;
    }

    public Boolean getDataCopied() {
        return dataCopied;
    }

    public void setDataCopied(Boolean dataCopied) {
        this.dataCopied = dataCopied;
    }

    public Boolean getMetadataCommitted() {
        return metadataCommitted;
    }

    public void setMetadataCommitted(Boolean metadataCommitted) {
        this.metadataCommitted = metadataCommitted;
    }

    public Boolean getTargetDurable() {
        return targetDurable;
    }

    public void setTargetDurable(Boolean targetDurable) {
        this.targetDurable = targetDurable;
    }

    public String getCycleRetryMode() {
        return cycleRetryMode;
    }

    public void setCycleRetryMode(String cycleRetryMode) {
        this.cycleRetryMode = cycleRetryMode;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getOutput() {
        return output;
    }

    public String getStatusJson() {
        return statusJson;
    }

    public String getSourceDiskMapPath() {
        return sourceDiskMapPath;
    }

    public void setSourceDiskMapPath(String sourceDiskMapPath) {
        this.sourceDiskMapPath = sourceDiskMapPath;
    }

    public String getTargetDiskMapPath() {
        return targetDiskMapPath;
    }

    public void setTargetDiskMapPath(String targetDiskMapPath) {
        this.targetDiskMapPath = targetDiskMapPath;
    }

    public String getDiskMapRole() {
        return diskMapRole;
    }

    public void setDiskMapRole(String diskMapRole) {
        this.diskMapRole = diskMapRole;
    }

    public Integer getTargetDiskCount() {
        return targetDiskCount;
    }

    public void setTargetDiskCount(Integer targetDiskCount) {
        this.targetDiskCount = targetDiskCount;
    }

    public Integer getTargetDiskInvalidCount() {
        return targetDiskInvalidCount;
    }

    public void setTargetDiskInvalidCount(Integer targetDiskInvalidCount) {
        this.targetDiskInvalidCount = targetDiskInvalidCount;
    }

    public String getWorkerState() {
        return workerState;
    }

    public void setWorkerState(String workerState) {
        this.workerState = workerState;
    }

    public Integer getWorkerPid() {
        return workerPid;
    }

    public void setWorkerPid(Integer workerPid) {
        this.workerPid = workerPid;
    }

    public String getWorkerStartedAt() {
        return workerStartedAt;
    }

    public void setWorkerStartedAt(String workerStartedAt) {
        this.workerStartedAt = workerStartedAt;
    }

    public String getWorkerUpdatedAt() {
        return workerUpdatedAt;
    }

    public void setWorkerUpdatedAt(String workerUpdatedAt) {
        this.workerUpdatedAt = workerUpdatedAt;
    }

    public Integer getWorkerExitCode() {
        return workerExitCode;
    }

    public void setWorkerExitCode(Integer workerExitCode) {
        this.workerExitCode = workerExitCode;
    }

    public Boolean getRetryable() {
        return retryable;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Integer retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getCurrentCheckpointSequence() {
        return currentCheckpointSequence;
    }

    public void setCurrentCheckpointSequence(Long currentCheckpointSequence) {
        this.currentCheckpointSequence = currentCheckpointSequence;
    }

    public String getCurrentCheckpointCycleType() {
        return currentCheckpointCycleType;
    }

    public void setCurrentCheckpointCycleType(String currentCheckpointCycleType) {
        this.currentCheckpointCycleType = currentCheckpointCycleType;
    }

    public String getCurrentCheckpointRequestedMode() { return currentCheckpointRequestedMode; }
    public void setCurrentCheckpointRequestedMode(String value) { currentCheckpointRequestedMode = value; }
    public String getCurrentCheckpointEffectiveMode() { return currentCheckpointEffectiveMode; }
    public void setCurrentCheckpointEffectiveMode(String value) { currentCheckpointEffectiveMode = value; }
    public String getCurrentCheckpointModeDecisionCode() { return currentCheckpointModeDecisionCode; }
    public void setCurrentCheckpointModeDecisionCode(String value) { currentCheckpointModeDecisionCode = value; }
    public Boolean getCurrentCheckpointAutomaticReseed() { return currentCheckpointAutomaticReseed; }
    public void setCurrentCheckpointAutomaticReseed(Boolean value) { currentCheckpointAutomaticReseed = value; }
    public Integer getCurrentCheckpointInvalidBaselineDiskCount() { return currentCheckpointInvalidBaselineDiskCount; }
    public void setCurrentCheckpointInvalidBaselineDiskCount(Integer value) { currentCheckpointInvalidBaselineDiskCount = value; }

    public String getCurrentCheckpointRef() {
        return currentCheckpointRef;
    }

    public void setCurrentCheckpointRef(String currentCheckpointRef) {
        this.currentCheckpointRef = currentCheckpointRef;
    }

    public String getCurrentCheckpointState() {
        return currentCheckpointState;
    }

    public void setCurrentCheckpointState(String currentCheckpointState) {
        this.currentCheckpointState = currentCheckpointState;
    }

    public Long getLatestCompletedCheckpointSequence() {
        return latestCompletedCheckpointSequence;
    }

    public void setLatestCompletedCheckpointSequence(Long latestCompletedCheckpointSequence) {
        this.latestCompletedCheckpointSequence = latestCompletedCheckpointSequence;
    }

    public String getLatestCompletedCheckpointCycleType() {
        return latestCompletedCheckpointCycleType;
    }

    public void setLatestCompletedCheckpointCycleType(String latestCompletedCheckpointCycleType) {
        this.latestCompletedCheckpointCycleType = latestCompletedCheckpointCycleType;
    }

    public String getLatestCompletedCheckpointRef() {
        return latestCompletedCheckpointRef;
    }

    public void setLatestCompletedCheckpointRef(String latestCompletedCheckpointRef) {
        this.latestCompletedCheckpointRef = latestCompletedCheckpointRef;
    }

    public String getLatestCompletedCheckpointState() {
        return latestCompletedCheckpointState;
    }

    public void setLatestCompletedCheckpointState(String latestCompletedCheckpointState) {
        this.latestCompletedCheckpointState = latestCompletedCheckpointState;
    }

    public String getLatestCompletedSourceCheckpointAt() {
        return latestCompletedSourceCheckpointAt;
    }

    public void setLatestCompletedSourceCheckpointAt(String latestCompletedSourceCheckpointAt) {
        this.latestCompletedSourceCheckpointAt = latestCompletedSourceCheckpointAt;
    }

    public String getLatestCompletedTargetDurableAt() {
        return latestCompletedTargetDurableAt;
    }

    public void setLatestCompletedTargetDurableAt(String latestCompletedTargetDurableAt) {
        this.latestCompletedTargetDurableAt = latestCompletedTargetDurableAt;
    }

    public Integer getLatestCompletedTargetReadyRpoSeconds() {
        return latestCompletedTargetReadyRpoSeconds;
    }

    public void setLatestCompletedTargetReadyRpoSeconds(Integer latestCompletedTargetReadyRpoSeconds) {
        this.latestCompletedTargetReadyRpoSeconds = latestCompletedTargetReadyRpoSeconds;
    }

    public String getLatestCompletedManifestPath() {
        return latestCompletedManifestPath;
    }

    public void setLatestCompletedManifestPath(String latestCompletedManifestPath) {
        this.latestCompletedManifestPath = latestCompletedManifestPath;
    }

    public String getLatestCompletedCheckpointPath() {
        return latestCompletedCheckpointPath;
    }

    public void setLatestCompletedCheckpointPath(String latestCompletedCheckpointPath) {
        this.latestCompletedCheckpointPath = latestCompletedCheckpointPath;
    }

    public String getLatestCompletedRequestedMode() { return latestCompletedRequestedMode; }
    public void setLatestCompletedRequestedMode(String value) { latestCompletedRequestedMode = value; }

    public String getLatestCompletedEffectiveMode() {
        return latestCompletedEffectiveMode;
    }

    public void setLatestCompletedEffectiveMode(String latestCompletedEffectiveMode) {
        this.latestCompletedEffectiveMode = latestCompletedEffectiveMode;
    }

    public String getLatestCompletedModeDecisionCode() { return latestCompletedModeDecisionCode; }
    public void setLatestCompletedModeDecisionCode(String value) { latestCompletedModeDecisionCode = value; }
    public String getLatestCompletedReseedReason() { return latestCompletedReseedReason; }
    public void setLatestCompletedReseedReason(String value) { latestCompletedReseedReason = value; }
    public Boolean getLatestCompletedAutomaticReseed() { return latestCompletedAutomaticReseed; }
    public void setLatestCompletedAutomaticReseed(Boolean value) { latestCompletedAutomaticReseed = value; }
    public Integer getLatestCompletedInvalidBaselineDiskCount() { return latestCompletedInvalidBaselineDiskCount; }
    public void setLatestCompletedInvalidBaselineDiskCount(Integer value) { latestCompletedInvalidBaselineDiskCount = value; }

    public Boolean getLatestCompletedIncrementalVerified() {
        return latestCompletedIncrementalVerified;
    }

    public void setLatestCompletedIncrementalVerified(Boolean latestCompletedIncrementalVerified) {
        this.latestCompletedIncrementalVerified = latestCompletedIncrementalVerified;
    }

    public Boolean getLatestCompletedMetricsEstimated() {
        return latestCompletedMetricsEstimated;
    }

    public void setLatestCompletedMetricsEstimated(Boolean latestCompletedMetricsEstimated) {
        this.latestCompletedMetricsEstimated = latestCompletedMetricsEstimated;
    }

    public Long getLatestCompletedVirtualBytes() {
        return latestCompletedVirtualBytes;
    }

    public void setLatestCompletedVirtualBytes(Long latestCompletedVirtualBytes) {
        this.latestCompletedVirtualBytes = latestCompletedVirtualBytes;
    }

    public Long getLatestCompletedChangedBytes() {
        return latestCompletedChangedBytes;
    }

    public void setLatestCompletedChangedBytes(Long latestCompletedChangedBytes) {
        this.latestCompletedChangedBytes = latestCompletedChangedBytes;
    }

    public Long getLatestCompletedSourceReadBytes() {
        return latestCompletedSourceReadBytes;
    }

    public void setLatestCompletedSourceReadBytes(Long latestCompletedSourceReadBytes) {
        this.latestCompletedSourceReadBytes = latestCompletedSourceReadBytes;
    }

    public Long getLatestCompletedTargetWrittenBytes() {
        return latestCompletedTargetWrittenBytes;
    }

    public void setLatestCompletedTargetWrittenBytes(Long latestCompletedTargetWrittenBytes) {
        this.latestCompletedTargetWrittenBytes = latestCompletedTargetWrittenBytes;
    }

    public Long getLatestCompletedTransferPayloadBytes() {
        return latestCompletedTransferPayloadBytes;
    }

    public void setLatestCompletedTransferPayloadBytes(Long latestCompletedTransferPayloadBytes) {
        this.latestCompletedTransferPayloadBytes = latestCompletedTransferPayloadBytes;
    }

    public Long getLatestCompletedChangedExtentCount() {
        return latestCompletedChangedExtentCount;
    }

    public void setLatestCompletedChangedExtentCount(Long latestCompletedChangedExtentCount) {
        this.latestCompletedChangedExtentCount = latestCompletedChangedExtentCount;
    }

    public Long getLatestCompletedDurationMs() {
        return latestCompletedDurationMs;
    }

    public void setLatestCompletedDurationMs(Long latestCompletedDurationMs) {
        this.latestCompletedDurationMs = latestCompletedDurationMs;
    }

    public Long getLatestCompletedThroughputBps() {
        return latestCompletedThroughputBps;
    }

    public void setLatestCompletedThroughputBps(Long latestCompletedThroughputBps) {
        this.latestCompletedThroughputBps = latestCompletedThroughputBps;
    }

    public Long getLatestCompletedBaselineGeneration() {
        return latestCompletedBaselineGeneration;
    }

    public void setLatestCompletedBaselineGeneration(Long latestCompletedBaselineGeneration) {
        this.latestCompletedBaselineGeneration = latestCompletedBaselineGeneration;
    }

    public String getLatestCompletedCycleToken() {
        return latestCompletedCycleToken;
    }

    public void setLatestCompletedCycleToken(String latestCompletedCycleToken) {
        this.latestCompletedCycleToken = latestCompletedCycleToken;
    }

    public Integer getCycleContractVersion() { return cycleContractVersion; }
    public void setCycleContractVersion(Integer value) { cycleContractVersion = value; }
    public FtctlDrCycleSnapshot getCurrentCycle() { return currentCycle; }
    public void setCurrentCycle(FtctlDrCycleSnapshot value) { currentCycle = value; }
    public FtctlDrCycleSnapshot getLatestCompletedCycle() { return latestCompletedCycle; }
    public void setLatestCompletedCycle(FtctlDrCycleSnapshot value) { latestCompletedCycle = value; }

    public Integer getControlProtocolVersion() {
        return controlProtocolVersion;
    }

    public void setControlProtocolVersion(Integer controlProtocolVersion) {
        this.controlProtocolVersion = controlProtocolVersion;
    }

    public Long getControlGeneration() {
        return controlGeneration;
    }

    public void setControlGeneration(Long controlGeneration) {
        this.controlGeneration = controlGeneration;
    }

    public Long getControlAckGeneration() {
        return controlAckGeneration;
    }

    public void setControlAckGeneration(Long controlAckGeneration) {
        this.controlAckGeneration = controlAckGeneration;
    }

    public String getControlState() {
        return controlState;
    }

    public void setControlState(String controlState) {
        this.controlState = controlState;
    }

    public String getCycleState() {
        return cycleState;
    }

    public void setCycleState(String cycleState) {
        this.cycleState = cycleState;
    }

    public String getTransitionState() {
        return transitionState;
    }

    public void setTransitionState(String transitionState) {
        this.transitionState = transitionState;
    }

    public String getCheckpointLeaseState() {
        return checkpointLeaseState;
    }

    public void setCheckpointLeaseState(String checkpointLeaseState) {
        this.checkpointLeaseState = checkpointLeaseState;
    }

    public String getGuestPreparationState() {
        return guestPreparationState;
    }

    public void setGuestPreparationState(String guestPreparationState) {
        this.guestPreparationState = guestPreparationState;
    }

    public String getGuestFamily() {
        return guestFamily;
    }

    public void setGuestFamily(String guestFamily) {
        this.guestFamily = guestFamily;
    }

    public String getGuestPreparationManifestPath() {
        return guestPreparationManifestPath;
    }

    public void setGuestPreparationManifestPath(String guestPreparationManifestPath) {
        this.guestPreparationManifestPath = guestPreparationManifestPath;
    }

    public String getTestDomainName() {
        return testDomainName;
    }

    public void setTestDomainName(String testDomainName) {
        this.testDomainName = testDomainName;
    }

    public String getTestDomainState() {
        return testDomainState;
    }

    public void setTestDomainState(String testDomainState) {
        this.testDomainState = testDomainState;
    }

    public String getTestBootValidationMode() {
        return testBootValidationMode;
    }

    public void setTestBootValidationMode(String testBootValidationMode) {
        this.testBootValidationMode = testBootValidationMode;
    }

    public Long getRuntimeGeneration() {
        return runtimeGeneration;
    }

    public void setRuntimeGeneration(Long runtimeGeneration) {
        this.runtimeGeneration = runtimeGeneration;
    }

    public Boolean getSchedulerPidAlive() {
        return schedulerPidAlive;
    }

    public void setSchedulerPidAlive(Boolean schedulerPidAlive) {
        this.schedulerPidAlive = schedulerPidAlive;
    }

    public String getBaselineState() {
        return baselineState;
    }

    public void setBaselineState(String baselineState) {
        this.baselineState = baselineState;
    }

    public String getReseedReason() {
        return reseedReason;
    }

    public void setReseedReason(String reseedReason) {
        this.reseedReason = reseedReason;
    }

    public Integer getConsecutiveAutomaticReseedCount() { return consecutiveAutomaticReseedCount; }
    public void setConsecutiveAutomaticReseedCount(Integer value) { consecutiveAutomaticReseedCount = value; }
}
