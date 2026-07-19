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
package org.apache.cloudstack.api.response.dr;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dr.DrPlanVO;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DrPlanVO.class)
public class DrPlanResponse extends BaseResponse {
    @SerializedName("id")
    @Param(description = "the DR plan ID")
    private String id;

    @SerializedName("name")
    @Param(description = "the DR plan name")
    private String name;

    @SerializedName("description")
    @Param(description = "the DR plan description")
    private String description;

    @SerializedName("sourcesiteid")
    @Param(description = "the source site ID")
    private String sourceSiteId;

    @SerializedName("targetsiteid")
    @Param(description = "the target site ID")
    private String targetSiteId;

    @SerializedName("sourcevmid")
    @Param(description = "the source virtual machine ID")
    private Long sourceVmId;

    @SerializedName("sourceexternalref")
    @Param(description = "the source external reference")
    private String sourceExternalRef;

    @SerializedName("direction")
    @Param(description = "the DR direction")
    private String direction;

    @SerializedName("enginetype")
    @Param(description = "the replication engine type")
    private String engineType;

    @SerializedName("enginebindingtype")
    @Param(description = "the engine binding type")
    private String engineBindingType;

    @SerializedName("enginebindingid")
    @Param(description = "the engine binding ID")
    private Long engineBindingId;

    @SerializedName("state")
    @Param(description = "the DR plan state")
    private String state;

    @SerializedName("effectivestate")
    @Param(description = "the effective DR plan state after runtime projection and readiness evaluation")
    private String effectiveState;

    @SerializedName("protectionstate")
    @Param(description = "the current persisted replication authority state")
    private String protectionState;

    @SerializedName("freshnessstate")
    @Param(description = "the current RPO freshness state")
    private String freshnessState;

    @SerializedName("projectionintegritystate")
    @Param(description = "the completed-cycle projection integrity state")
    private String projectionIntegrityState;

    @SerializedName("projectionintegritycode")
    @Param(description = "the completed-cycle projection integrity reason code")
    private String projectionIntegrityCode;

    @SerializedName("projectionintegritysequence")
    @Param(description = "the completed-cycle sequence evaluated for projection integrity")
    private Long projectionIntegritySequence;

    @SerializedName("schedulerstate")
    @Param(description = "the current FTCTL scheduler state")
    private String schedulerState;

    @SerializedName("schedulerpidalive")
    @Param(description = "true when the scheduler process ownership is verified")
    private Boolean schedulerPidAlive;

    @SerializedName("authoritygeneration")
    @Param(description = "the accepted FTCTL runtime generation")
    private Long authorityGeneration;

    @SerializedName("currentcyclesequence")
    @Param(description = "the current replication cycle sequence")
    private Long currentCycleSequence;

    @SerializedName("currentcyclestate")
    @Param(description = "the current replication cycle state")
    private String currentCycleState;

    @SerializedName("currentcyclemode")
    @Param(description = "the current replication cycle mode")
    private String currentCycleMode;

    @SerializedName("baselinestate")
    @Param(description = "the committed CBT baseline state")
    private String baselineState;

    @SerializedName("reseedreason")
    @Param(description = "the automatic full reseed reason")
    private String reseedReason;

    @SerializedName("rpoageseconds")
    @Param(description = "the age of the latest durable target copy")
    private Long rpoAgeSeconds;

    @SerializedName("rpooverdue")
    @Param(description = "true when the latest durable target copy is outside RPO")
    private Boolean rpoOverdue;

    @SerializedName("adminstate")
    @Param(description = "the DR plan administrative state")
    private String adminState;

    @SerializedName("activeside")
    @Param(description = "the active DR side")
    private String activeSide;

    @SerializedName("rposeconds")
    @Param(description = "the target RPO in seconds")
    private Integer rpoSeconds;

    @SerializedName("rtoseconds")
    @Param(description = "the target RTO in seconds")
    private Integer rtoSeconds;

    @SerializedName("schedulejson")
    @Param(description = "the sync schedule JSON")
    private String scheduleJson;

    @SerializedName("policyjson")
    @Param(description = "the plan policy JSON")
    private String policyJson;

    @SerializedName("mappingjson")
    @Param(description = "the plan mapping JSON")
    private String mappingJson;

    @SerializedName("quiescepolicyjson")
    @Param(description = "the quiesce policy JSON")
    private String quiescePolicyJson;

    @SerializedName("sourceworkerhostid")
    @Param(description = "the source worker host ID")
    private Long sourceWorkerHostId;

    @SerializedName("targetworkerhostid")
    @Param(description = "the target worker host ID")
    private Long targetWorkerHostId;

    @SerializedName("coordinatorworkerhostid")
    @Param(description = "the coordinator worker host ID")
    private Long coordinatorWorkerHostId;

    @SerializedName("lastsourcecheckpointat")
    @Param(description = "the latest source checkpoint time")
    private Date lastSourceCheckpointAt;

    @SerializedName("lasttargetdurableat")
    @Param(description = "the latest target durable checkpoint time")
    private Date lastTargetDurableAt;

    @SerializedName("targetreadyat")
    @Param(description = "the latest target-ready time")
    private Date targetReadyAt;

    @SerializedName("targetreadyrposeconds")
    @Param(description = "the latest target-ready RPO in seconds")
    private Integer targetReadyRpoSeconds;

    @SerializedName("lastrunid")
    @Param(description = "the last DR run ID")
    private Long lastRunId;

    @SerializedName("lastrun")
    @Param(description = "the latest DR run summary")
    private DrRunResponse lastRun;

    @SerializedName("runtimeprojectionstate")
    @Param(description = "the latest runtime projection state")
    private String runtimeProjectionState;

    @SerializedName("runtimeprojectionmessage")
    @Param(description = "the latest runtime projection message")
    private String runtimeProjectionMessage;

    @SerializedName("runtimestate")
    @Param(description = "the latest FTCTL runtime state")
    private String runtimeState;

    @SerializedName("runtimestep")
    @Param(description = "the latest FTCTL runtime step")
    private String runtimeStep;

    @SerializedName("runtimeerrorcode")
    @Param(description = "the latest FTCTL runtime error code")
    private String runtimeErrorCode;

    @SerializedName("runtimecontrolprotocolversion")
    @Param(description = "the FTCTL DR control protocol version")
    private Integer runtimeControlProtocolVersion;

    @SerializedName("runtimecontrolgeneration")
    @Param(description = "the latest FTCTL DR control request generation")
    private Long runtimeControlGeneration;

    @SerializedName("runtimecontrolackgeneration")
    @Param(description = "the latest FTCTL DR acknowledged control generation")
    private Long runtimeControlAckGeneration;

    @SerializedName("runtimecontrolstate")
    @Param(description = "the latest FTCTL DR scheduler control state")
    private String runtimeControlState;

    @SerializedName("runtimecyclestate")
    @Param(description = "the latest FTCTL DR replication cycle state")
    private String runtimeCycleState;

    @SerializedName("runtimetransitionstate")
    @Param(description = "the latest FTCTL DR transition coordination state")
    private String runtimeTransitionState;

    @SerializedName("runtimecheckpointleasestate")
    @Param(description = "the latest FTCTL DR checkpoint lease state")
    private String runtimeCheckpointLeaseState;

    @SerializedName("runtimecontrolready")
    @Param(description = "true when the FTCTL DR control protocol is ready for coordinated actions")
    private Boolean runtimeControlReady;

    @SerializedName("targetmaterializationstate")
    @Param(description = "the derived target materialization state")
    private String targetMaterializationState;

    @SerializedName("targetmaterializationmessage")
    @Param(description = "the derived target materialization message")
    private String targetMaterializationMessage;

    @SerializedName("initialsyncinprogress")
    @Param(description = "true if the initial DR sync is actively transferring data before target VM materialization")
    private Boolean initialSyncInProgress;

    @SerializedName("sourcediskmappath")
    @Param(description = "the latest FTCTL source disk map path")
    private String sourceDiskMapPath;

    @SerializedName("targetdiskmappath")
    @Param(description = "the latest FTCTL target disk map path")
    private String targetDiskMapPath;

    @SerializedName("diskmaprole")
    @Param(description = "the active FTCTL disk map role")
    private String diskMapRole;

    @SerializedName("targetdiskcount")
    @Param(description = "the target disk count reported by FTCTL")
    private Integer targetDiskCount;

    @SerializedName("targetdiskinvalidcount")
    @Param(description = "the invalid target disk count reported by FTCTL")
    private Integer targetDiskInvalidCount;

    @SerializedName("runtimecbtenabled")
    @Param(description = "whether VMware CBT is enabled according to the latest FTCTL preflight status")
    private Boolean runtimeCbtEnabled;

    @SerializedName("runtimecbtdiskid")
    @Param(description = "the VMware CBT disk ID resolved by the latest FTCTL preflight status")
    private String runtimeCbtDiskId;

    @SerializedName("runtimecbtmessage")
    @Param(description = "the VMware CBT preflight message reported by FTCTL")
    private String runtimeCbtMessage;

    @SerializedName("runtimecbtgovcbin")
    @Param(description = "the govc binary resolved by the latest FTCTL VMware preflight status")
    private String runtimeCbtGovcBin;

    @SerializedName("runtimecbtcheckedatepochms")
    @Param(description = "the VMware CBT preflight check timestamp in epoch milliseconds")
    private Long runtimeCbtCheckedAtEpochMs;

    @SerializedName("runtimesourceopenready")
    @Param(description = "whether VMware source-open preflight succeeded according to the latest FTCTL status")
    private Boolean runtimeSourceOpenReady;

    @SerializedName("runtimesourceopenerrorcode")
    @Param(description = "the VMware source-open preflight error code reported by FTCTL")
    private String runtimeSourceOpenErrorCode;

    @SerializedName("runtimesourceopenmessage")
    @Param(description = "the VMware source-open preflight message reported by FTCTL")
    private String runtimeSourceOpenMessage;

    @SerializedName("runtimesourcesnapshotready")
    @Param(description = "whether VMware source snapshot preflight succeeded according to the latest FTCTL status")
    private Boolean runtimeSourceSnapshotReady;

    @SerializedName("runtimesourcesnapshoterrorcode")
    @Param(description = "the VMware source snapshot preflight error code reported by FTCTL")
    private String runtimeSourceSnapshotErrorCode;

    @SerializedName("runtimesourcesnapshotmessage")
    @Param(description = "the VMware source snapshot preflight message reported by FTCTL")
    private String runtimeSourceSnapshotMessage;

    @SerializedName("runtimesourcesnapshotname")
    @Param(description = "the VMware source snapshot name used by FTCTL")
    private String runtimeSourceSnapshotName;

    @SerializedName("runtimesourcesnapshotrefpresent")
    @Param(description = "whether FTCTL resolved the VMware source snapshot managed object reference")
    private Boolean runtimeSourceSnapshotRefPresent;

    @SerializedName("runtimesourcesnapshotlifecyclestate")
    @Param(description = "the VMware source snapshot lifecycle state")
    private String runtimeSourceSnapshotLifecycleState;

    @SerializedName("runtimesourcesnapshotcleanuprequired")
    @Param(description = "whether VMware source snapshot cleanup is required")
    private Boolean runtimeSourceSnapshotCleanupRequired;

    @SerializedName("runtimesourcesnapshotlastref")
    @Param(description = "the last VMware source snapshot reference retained for audit")
    private String runtimeSourceSnapshotLastRef;

    @SerializedName("runtimesourcesnapshotcleanedatepochms")
    @Param(description = "the VMware source snapshot cleanup time in epoch milliseconds")
    private Long runtimeSourceSnapshotCleanedAtEpochMs;

    @SerializedName("lasterrorcode")
    @Param(description = "the last error code")
    private String lastErrorCode;

    @SerializedName("lasterrormessage")
    @Param(description = "the last error message")
    private String lastErrorMessage;

    @SerializedName("failedcomponent")
    @Param(description = "the runtime component that reported the latest failure")
    private String failedComponent;

    @SerializedName("datacommitstate")
    @Param(description = "the current replication cycle data commit state")
    private String dataCommitState;

    @SerializedName("datacopied")
    @Param(description = "whether disk data was copied for the current cycle")
    private Boolean dataCopied;

    @SerializedName("metadatacommitted")
    @Param(description = "whether cycle metadata was committed")
    private Boolean metadataCommitted;

    @SerializedName("targetdurable")
    @Param(description = "whether the current cycle is durable on the target")
    private Boolean targetDurable;

    @SerializedName("cycleretrymode")
    @Param(description = "the safe retry mode for the current replication cycle")
    private String cycleRetryMode;

    @SerializedName("actioneligibility")
    @Param(description = "the backend calculated action eligibility map")
    private Map<String, Boolean> actionEligibility;

    @SerializedName("readinessstate")
    @Param(description = "the backend calculated DR plan readiness state")
    private String readinessState;

    @SerializedName("readinessreasoncode")
    @Param(description = "the backend calculated DR plan readiness reason code")
    private String readinessReasonCode;

    @SerializedName("readinessmessage")
    @Param(description = "the backend calculated DR plan readiness message")
    private String readinessMessage;

    @SerializedName("executionready")
    @Param(description = "true if the plan can start a protection sync")
    private Boolean executionReady;

    @SerializedName("releaseready")
    @Param(description = "true if the plan has runtime resources that can be released")
    private Boolean releaseReady;

    @SerializedName("engineaccepted")
    @Param(description = "true if the DR engine has accepted the plan or runtime resources exist")
    private Boolean engineAccepted;

    @SerializedName("targetmaterialized")
    @Param(description = "true if the target VM/storage/network and restore point are materialized")
    private Boolean targetMaterialized;

    @SerializedName("targetvmpresent")
    @Param(description = "true if the target VM reference exists")
    private Boolean targetVmPresent;

    @SerializedName("targetstoragepresent")
    @Param(description = "true if the target storage/checkpoint evidence exists")
    private Boolean targetStoragePresent;

    @SerializedName("targetnetworkpresent")
    @Param(description = "true if the target network evidence exists")
    private Boolean targetNetworkPresent;

    @SerializedName("restorepointpresent")
    @Param(description = "true if a target-ready restore point exists")
    private Boolean restorePointPresent;

    @SerializedName("durablecheckpointpresent")
    @Param(description = "true if a durable target checkpoint exists")
    private Boolean durableCheckpointPresent;

    @SerializedName("readinessblockingreasons")
    @Param(description = "blocking readiness reason codes")
    private List<String> readinessBlockingReasons;

    @SerializedName("readinesswarnings")
    @Param(description = "non-blocking readiness warnings")
    private List<String> readinessWarnings;

    @SerializedName("created")
    @Param(description = "the creation date")
    private Date created;

    @SerializedName("removed")
    @Param(description = "the removal date")
    private Date removed;

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSourceSiteId(String sourceSiteId) {
        this.sourceSiteId = sourceSiteId;
    }

    public void setTargetSiteId(String targetSiteId) {
        this.targetSiteId = targetSiteId;
    }

    public void setSourceVmId(Long sourceVmId) {
        this.sourceVmId = sourceVmId;
    }

    public void setSourceExternalRef(String sourceExternalRef) {
        this.sourceExternalRef = sourceExternalRef;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public void setEngineBindingType(String engineBindingType) {
        this.engineBindingType = engineBindingType;
    }

    public void setEngineBindingId(Long engineBindingId) {
        this.engineBindingId = engineBindingId;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setEffectiveState(String effectiveState) {
        this.effectiveState = effectiveState;
    }

    public void setProtectionState(String value) { protectionState = value; }
    public void setFreshnessState(String value) { freshnessState = value; }
    public void setProjectionIntegrityState(String value) { projectionIntegrityState = value; }
    public void setProjectionIntegrityCode(String value) { projectionIntegrityCode = value; }
    public void setProjectionIntegritySequence(Long value) { projectionIntegritySequence = value; }
    public void setSchedulerState(String value) { schedulerState = value; }
    public void setSchedulerPidAlive(Boolean value) { schedulerPidAlive = value; }
    public void setAuthorityGeneration(Long value) { authorityGeneration = value; }
    public void setCurrentCycleSequence(Long value) { currentCycleSequence = value; }
    public void setCurrentCycleState(String value) { currentCycleState = value; }
    public void setCurrentCycleMode(String value) { currentCycleMode = value; }
    public void setBaselineState(String value) { baselineState = value; }
    public void setReseedReason(String value) { reseedReason = value; }
    public void setRpoAgeSeconds(Long value) { rpoAgeSeconds = value; }
    public void setRpoOverdue(Boolean value) { rpoOverdue = value; }

    public void setAdminState(String adminState) {
        this.adminState = adminState;
    }

    public void setActiveSide(String activeSide) {
        this.activeSide = activeSide;
    }

    public void setRpoSeconds(Integer rpoSeconds) {
        this.rpoSeconds = rpoSeconds;
    }

    public void setRtoSeconds(Integer rtoSeconds) {
        this.rtoSeconds = rtoSeconds;
    }

    public void setScheduleJson(String scheduleJson) {
        this.scheduleJson = scheduleJson;
    }

    public void setPolicyJson(String policyJson) {
        this.policyJson = policyJson;
    }

    public void setMappingJson(String mappingJson) {
        this.mappingJson = mappingJson;
    }

    public void setQuiescePolicyJson(String quiescePolicyJson) {
        this.quiescePolicyJson = quiescePolicyJson;
    }

    public void setSourceWorkerHostId(Long sourceWorkerHostId) {
        this.sourceWorkerHostId = sourceWorkerHostId;
    }

    public void setTargetWorkerHostId(Long targetWorkerHostId) {
        this.targetWorkerHostId = targetWorkerHostId;
    }

    public void setCoordinatorWorkerHostId(Long coordinatorWorkerHostId) {
        this.coordinatorWorkerHostId = coordinatorWorkerHostId;
    }

    public void setLastSourceCheckpointAt(Date lastSourceCheckpointAt) {
        this.lastSourceCheckpointAt = lastSourceCheckpointAt;
    }

    public void setLastTargetDurableAt(Date lastTargetDurableAt) {
        this.lastTargetDurableAt = lastTargetDurableAt;
    }

    public void setTargetReadyAt(Date targetReadyAt) {
        this.targetReadyAt = targetReadyAt;
    }

    public void setTargetReadyRpoSeconds(Integer targetReadyRpoSeconds) {
        this.targetReadyRpoSeconds = targetReadyRpoSeconds;
    }

    public void setLastRunId(Long lastRunId) {
        this.lastRunId = lastRunId;
    }

    public void setLastRun(DrRunResponse lastRun) {
        this.lastRun = lastRun;
    }

    public void setRuntimeProjectionState(String runtimeProjectionState) {
        this.runtimeProjectionState = runtimeProjectionState;
    }

    public void setRuntimeProjectionMessage(String runtimeProjectionMessage) {
        this.runtimeProjectionMessage = runtimeProjectionMessage;
    }

    public void setRuntimeState(String runtimeState) {
        this.runtimeState = runtimeState;
    }

    public void setRuntimeStep(String runtimeStep) {
        this.runtimeStep = runtimeStep;
    }

    public void setRuntimeErrorCode(String runtimeErrorCode) {
        this.runtimeErrorCode = runtimeErrorCode;
    }

    public void setRuntimeControlProtocolVersion(Integer runtimeControlProtocolVersion) {
        this.runtimeControlProtocolVersion = runtimeControlProtocolVersion;
    }

    public void setRuntimeControlGeneration(Long runtimeControlGeneration) {
        this.runtimeControlGeneration = runtimeControlGeneration;
    }

    public void setRuntimeControlAckGeneration(Long runtimeControlAckGeneration) {
        this.runtimeControlAckGeneration = runtimeControlAckGeneration;
    }

    public void setRuntimeControlState(String runtimeControlState) {
        this.runtimeControlState = runtimeControlState;
    }

    public void setRuntimeCycleState(String runtimeCycleState) {
        this.runtimeCycleState = runtimeCycleState;
    }

    public void setRuntimeTransitionState(String runtimeTransitionState) {
        this.runtimeTransitionState = runtimeTransitionState;
    }

    public void setRuntimeCheckpointLeaseState(String runtimeCheckpointLeaseState) {
        this.runtimeCheckpointLeaseState = runtimeCheckpointLeaseState;
    }

    public void setRuntimeControlReady(Boolean runtimeControlReady) {
        this.runtimeControlReady = runtimeControlReady;
    }

    public void setTargetMaterializationState(String targetMaterializationState) {
        this.targetMaterializationState = targetMaterializationState;
    }

    public void setTargetMaterializationMessage(String targetMaterializationMessage) {
        this.targetMaterializationMessage = targetMaterializationMessage;
    }

    public void setInitialSyncInProgress(Boolean initialSyncInProgress) {
        this.initialSyncInProgress = initialSyncInProgress;
    }

    public void setSourceDiskMapPath(String sourceDiskMapPath) {
        this.sourceDiskMapPath = sourceDiskMapPath;
    }

    public void setTargetDiskMapPath(String targetDiskMapPath) {
        this.targetDiskMapPath = targetDiskMapPath;
    }

    public void setDiskMapRole(String diskMapRole) {
        this.diskMapRole = diskMapRole;
    }

    public void setTargetDiskCount(Integer targetDiskCount) {
        this.targetDiskCount = targetDiskCount;
    }

    public void setTargetDiskInvalidCount(Integer targetDiskInvalidCount) {
        this.targetDiskInvalidCount = targetDiskInvalidCount;
    }

    public void setRuntimeCbtEnabled(Boolean runtimeCbtEnabled) {
        this.runtimeCbtEnabled = runtimeCbtEnabled;
    }

    public void setRuntimeCbtDiskId(String runtimeCbtDiskId) {
        this.runtimeCbtDiskId = runtimeCbtDiskId;
    }

    public void setRuntimeCbtMessage(String runtimeCbtMessage) {
        this.runtimeCbtMessage = runtimeCbtMessage;
    }

    public void setRuntimeCbtGovcBin(String runtimeCbtGovcBin) {
        this.runtimeCbtGovcBin = runtimeCbtGovcBin;
    }

    public void setRuntimeCbtCheckedAtEpochMs(Long runtimeCbtCheckedAtEpochMs) {
        this.runtimeCbtCheckedAtEpochMs = runtimeCbtCheckedAtEpochMs;
    }

    public void setRuntimeSourceOpenReady(Boolean runtimeSourceOpenReady) {
        this.runtimeSourceOpenReady = runtimeSourceOpenReady;
    }

    public void setRuntimeSourceOpenErrorCode(String runtimeSourceOpenErrorCode) {
        this.runtimeSourceOpenErrorCode = runtimeSourceOpenErrorCode;
    }

    public void setRuntimeSourceOpenMessage(String runtimeSourceOpenMessage) {
        this.runtimeSourceOpenMessage = runtimeSourceOpenMessage;
    }

    public void setRuntimeSourceSnapshotReady(Boolean runtimeSourceSnapshotReady) {
        this.runtimeSourceSnapshotReady = runtimeSourceSnapshotReady;
    }

    public void setRuntimeSourceSnapshotErrorCode(String runtimeSourceSnapshotErrorCode) {
        this.runtimeSourceSnapshotErrorCode = runtimeSourceSnapshotErrorCode;
    }

    public void setRuntimeSourceSnapshotMessage(String runtimeSourceSnapshotMessage) {
        this.runtimeSourceSnapshotMessage = runtimeSourceSnapshotMessage;
    }

    public void setRuntimeSourceSnapshotName(String runtimeSourceSnapshotName) {
        this.runtimeSourceSnapshotName = runtimeSourceSnapshotName;
    }

    public void setRuntimeSourceSnapshotRefPresent(Boolean runtimeSourceSnapshotRefPresent) {
        this.runtimeSourceSnapshotRefPresent = runtimeSourceSnapshotRefPresent;
    }

    public void setRuntimeSourceSnapshotLifecycleState(String value) { this.runtimeSourceSnapshotLifecycleState = value; }
    public void setRuntimeSourceSnapshotCleanupRequired(Boolean value) { this.runtimeSourceSnapshotCleanupRequired = value; }
    public void setRuntimeSourceSnapshotLastRef(String value) { this.runtimeSourceSnapshotLastRef = value; }
    public void setRuntimeSourceSnapshotCleanedAtEpochMs(Long value) { this.runtimeSourceSnapshotCleanedAtEpochMs = value; }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public void setFailedComponent(String failedComponent) {
        this.failedComponent = failedComponent;
    }

    public void setDataCommitState(String dataCommitState) {
        this.dataCommitState = dataCommitState;
    }

    public void setDataCopied(Boolean dataCopied) {
        this.dataCopied = dataCopied;
    }

    public void setMetadataCommitted(Boolean metadataCommitted) {
        this.metadataCommitted = metadataCommitted;
    }

    public void setTargetDurable(Boolean targetDurable) {
        this.targetDurable = targetDurable;
    }

    public void setCycleRetryMode(String cycleRetryMode) {
        this.cycleRetryMode = cycleRetryMode;
    }

    public void setActionEligibility(Map<String, Boolean> actionEligibility) {
        this.actionEligibility = actionEligibility;
    }

    public void setReadinessState(String readinessState) {
        this.readinessState = readinessState;
    }

    public void setReadinessReasonCode(String readinessReasonCode) {
        this.readinessReasonCode = readinessReasonCode;
    }

    public void setReadinessMessage(String readinessMessage) {
        this.readinessMessage = readinessMessage;
    }

    public void setExecutionReady(Boolean executionReady) {
        this.executionReady = executionReady;
    }

    public void setReleaseReady(Boolean releaseReady) {
        this.releaseReady = releaseReady;
    }

    public void setEngineAccepted(Boolean engineAccepted) {
        this.engineAccepted = engineAccepted;
    }

    public void setTargetMaterialized(Boolean targetMaterialized) {
        this.targetMaterialized = targetMaterialized;
    }

    public void setTargetVmPresent(Boolean targetVmPresent) {
        this.targetVmPresent = targetVmPresent;
    }

    public void setTargetStoragePresent(Boolean targetStoragePresent) {
        this.targetStoragePresent = targetStoragePresent;
    }

    public void setTargetNetworkPresent(Boolean targetNetworkPresent) {
        this.targetNetworkPresent = targetNetworkPresent;
    }

    public void setRestorePointPresent(Boolean restorePointPresent) {
        this.restorePointPresent = restorePointPresent;
    }

    public void setDurableCheckpointPresent(Boolean durableCheckpointPresent) {
        this.durableCheckpointPresent = durableCheckpointPresent;
    }

    public void setReadinessBlockingReasons(List<String> readinessBlockingReasons) {
        this.readinessBlockingReasons = readinessBlockingReasons;
    }

    public void setReadinessWarnings(List<String> readinessWarnings) {
        this.readinessWarnings = readinessWarnings;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }
}
