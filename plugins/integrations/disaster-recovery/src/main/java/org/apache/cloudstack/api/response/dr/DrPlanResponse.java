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

    @SerializedName("lasterrorcode")
    @Param(description = "the last error code")
    private String lastErrorCode;

    @SerializedName("lasterrormessage")
    @Param(description = "the last error message")
    private String lastErrorMessage;

    @SerializedName("actioneligibility")
    @Param(description = "the backend calculated action eligibility map")
    private Map<String, Boolean> actionEligibility;

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

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public void setActionEligibility(Map<String, Boolean> actionEligibility) {
        this.actionEligibility = actionEligibility;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public void setRemoved(Date removed) {
        this.removed = removed;
    }
}
