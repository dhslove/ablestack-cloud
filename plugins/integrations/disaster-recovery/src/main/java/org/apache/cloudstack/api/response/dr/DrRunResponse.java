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
