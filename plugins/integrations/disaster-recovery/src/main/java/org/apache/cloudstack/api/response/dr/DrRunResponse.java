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

import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dr.DrRunVO;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DrRunVO.class)
public class DrRunResponse extends BaseResponse {
    @SerializedName("id")
    @Param(description = "the DR run ID")
    private String id;

    @SerializedName("planid")
    @Param(description = "the DR plan ID")
    private String planId;

    @SerializedName("runtype")
    @Param(description = "the DR run type")
    private String runType;

    @SerializedName("state")
    @Param(description = "the DR run state")
    private String state;

    @SerializedName("accepted")
    @Param(description = "whether the request was accepted for asynchronous DR run execution")
    private Boolean accepted;

    @SerializedName("engineaccepted")
    @Param(description = "whether the FTCTL engine accepted the run")
    private Boolean engineAccepted;

    @SerializedName("idempotencykey")
    @Param(description = "the idempotency key")
    private String idempotencyKey;

    @SerializedName("requestedbyuserid")
    @Param(description = "the requesting user ID")
    private Long requestedByUserId;

    @SerializedName("asyncjobid")
    @Param(description = "the CloudStack async job ID")
    private Long asyncJobId;

    @SerializedName("externaljobref")
    @Param(description = "the external engine job reference")
    private String externalJobRef;

    @SerializedName("acceptedat")
    @Param(description = "the FTCTL engine acceptance time")
    private Date acceptedAt;

    @SerializedName("dispatchstarted")
    @Param(description = "the Agent dispatch start time")
    private Date dispatchStarted;

    @SerializedName("dispatchcompleted")
    @Param(description = "the Agent dispatch completion time")
    private Date dispatchCompleted;

    @SerializedName("projectionstate")
    @Param(description = "the latest runtime projection state")
    private String projectionState;

    @SerializedName("projectionchecked")
    @Param(description = "the latest runtime projection check time")
    private Date projectionChecked;

    @SerializedName("runtimestate")
    @Param(description = "the latest FTCTL runtime state")
    private String runtimeState;

    @SerializedName("runtimestep")
    @Param(description = "the latest FTCTL runtime step")
    private String runtimeStep;

    @SerializedName("runtimeerrorcode")
    @Param(description = "the latest FTCTL runtime error code")
    private String runtimeErrorCode;

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

    @SerializedName("workerstate")
    @Param(description = "the latest FTCTL worker state")
    private String workerState;

    @SerializedName("workerexitcode")
    @Param(description = "the latest FTCTL worker exit code")
    private Integer workerExitCode;

    @SerializedName("retryable")
    @Param(description = "whether the run is waiting for a retryable condition to clear")
    private Boolean retryable;

    @SerializedName("retrycount")
    @Param(description = "the number of retry attempts already scheduled")
    private Integer retryCount;

    @SerializedName("retryafterseconds")
    @Param(description = "the retry delay in seconds")
    private Integer retryAfterSeconds;

    @SerializedName("nextretryat")
    @Param(description = "the next retry time")
    private Date nextRetryAt;

    @SerializedName("currentstep")
    @Param(description = "the current step name")
    private String currentStep;

    @SerializedName("progresspercent")
    @Param(description = "the derived progress percentage")
    private Integer progressPercent;

    @SerializedName("errorcode")
    @Param(description = "the error code")
    private String errorCode;

    @SerializedName("errormessage")
    @Param(description = "the error message")
    private String errorMessage;

    @SerializedName("failedcomponent")
    @Param(description = "the runtime component that reported the failure")
    private String failedComponent;

    @SerializedName("datacommitstate")
    @Param(description = "the replication cycle data commit state")
    private String dataCommitState;

    @SerializedName("datacopied")
    @Param(description = "whether disk data was copied for the cycle")
    private Boolean dataCopied;

    @SerializedName("metadatacommitted")
    @Param(description = "whether cycle metadata was committed")
    private Boolean metadataCommitted;

    @SerializedName("targetdurable")
    @Param(description = "whether the cycle is durable on the target")
    private Boolean targetDurable;

    @SerializedName("cycleretrymode")
    @Param(description = "the safe retry mode for the cycle")
    private String cycleRetryMode;

    @SerializedName("started")
    @Param(description = "the run start time")
    private Date started;

    @SerializedName("completed")
    @Param(description = "the run completion time")
    private Date completed;

    @SerializedName("created")
    @Param(description = "the run creation time")
    private Date created;

    @SerializedName("steps")
    @Param(description = "the run steps")
    private List<DrRunStepResponse> steps;

    public void setId(String id) {
        this.id = id;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public void setEngineAccepted(Boolean engineAccepted) {
        this.engineAccepted = engineAccepted;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setRequestedByUserId(Long requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public void setAsyncJobId(Long asyncJobId) {
        this.asyncJobId = asyncJobId;
    }

    public void setExternalJobRef(String externalJobRef) {
        this.externalJobRef = externalJobRef;
    }

    public void setAcceptedAt(Date acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public void setDispatchStarted(Date dispatchStarted) {
        this.dispatchStarted = dispatchStarted;
    }

    public void setDispatchCompleted(Date dispatchCompleted) {
        this.dispatchCompleted = dispatchCompleted;
    }

    public void setProjectionState(String projectionState) {
        this.projectionState = projectionState;
    }

    public void setProjectionChecked(Date projectionChecked) {
        this.projectionChecked = projectionChecked;
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

    public void setWorkerState(String workerState) {
        this.workerState = workerState;
    }

    public void setWorkerExitCode(Integer workerExitCode) {
        this.workerExitCode = workerExitCode;
    }

    public void setRetryable(Boolean retryable) {
        this.retryable = retryable;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setRetryAfterSeconds(Integer retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public void setNextRetryAt(Date nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public void setStarted(Date started) {
        this.started = started;
    }

    public void setCompleted(Date completed) {
        this.completed = completed;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setSteps(List<DrRunStepResponse> steps) {
        this.steps = steps;
    }
}
