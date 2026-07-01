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
package org.apache.cloudstack.api.command.admin.dr;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.dr.DrPlanResponse;
import org.apache.cloudstack.api.response.dr.DrSiteResponse;
import org.apache.cloudstack.context.CallContext;

import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanService;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrRunService;
import com.cloud.dr.cluster.DisasterRecoveryClusterEventTypes;
import com.cloud.dr.response.DrResponseGenerator;
import com.google.gson.JsonObject;

@APICommand(name = CreateDrPlanCmd.APINAME,
        description = "Create a Cross Hypervisor DR plan",
        responseObject = DrPlanResponse.class,
        authorized = {RoleType.Admin})
public class CreateDrPlanCmd extends BaseAsyncCmd {
    public static final String APINAME = "createDrPlan";

    @Inject
    private DrPlanService drPlanService;
    @Inject
    private DrRunService drRunService;
    @Inject
    private DrResponseGenerator drResponseGenerator;

    @Parameter(name = "name", type = CommandType.STRING, required = true, description = "the DR plan name")
    private String name;

    @Parameter(name = "description", type = CommandType.STRING, description = "the DR plan description")
    private String description;

    @Parameter(name = "sourcesiteid", type = CommandType.UUID, entityType = DrSiteResponse.class, required = true,
            description = "the source DR site ID")
    private Long sourceSiteId;

    @Parameter(name = "targetsiteid", type = CommandType.UUID, entityType = DrSiteResponse.class, required = true,
            description = "the target DR site ID")
    private Long targetSiteId;

    @Parameter(name = "sourcevmid", type = CommandType.UUID, entityType = UserVmResponse.class,
            description = "the source virtual machine ID")
    private Long sourceVmId;

    @Parameter(name = "sourceexternalref", type = CommandType.STRING, description = "the source external reference")
    private String sourceExternalRef;

    @Parameter(name = "direction", type = CommandType.STRING, required = true, description = "the DR direction")
    private String direction;

    @Parameter(name = "enginetype", type = CommandType.STRING, description = "the engine type")
    private String engineType;

    @Parameter(name = "enginebindingtype", type = CommandType.STRING, description = "the engine binding type")
    private String engineBindingType;

    @Parameter(name = "enginebindingid", type = CommandType.LONG, description = "the engine binding ID")
    private Long engineBindingId;

    @Parameter(name = "rposeconds", type = CommandType.INTEGER, description = "the target RPO in seconds")
    private Integer rpoSeconds;

    @Parameter(name = "rtoseconds", type = CommandType.INTEGER, description = "the target RTO in seconds")
    private Integer rtoSeconds;

    @Parameter(name = "schedulejson", type = CommandType.STRING, description = "the sync schedule JSON")
    private String scheduleJson;

    @Parameter(name = "policyjson", type = CommandType.STRING, description = "the plan policy JSON")
    private String policyJson;

    @Parameter(name = "mappingjson", type = CommandType.STRING, description = "the plan mapping JSON")
    private String mappingJson;

    @Parameter(name = "quiescepolicyjson", type = CommandType.STRING, description = "the quiesce policy JSON")
    private String quiescePolicyJson;

    @Parameter(name = "sourceworkerhostid", type = CommandType.UUID, entityType = HostResponse.class,
            description = "the source FTCTL_DR worker host ID")
    private Long sourceWorkerHostId;

    @Parameter(name = "targetworkerhostid", type = CommandType.UUID, entityType = HostResponse.class,
            description = "the target FTCTL_DR worker host ID")
    private Long targetWorkerHostId;

    @Parameter(name = "coordinatorworkerhostid", type = CommandType.UUID, entityType = HostResponse.class,
            description = "the coordinator FTCTL_DR worker host ID")
    private Long coordinatorWorkerHostId;

    @Parameter(name = "startsync", type = CommandType.BOOLEAN,
            description = "whether to start the initial DR sync asynchronously after creating the plan")
    private Boolean startSync;

    @Override
    public void execute() throws ServerApiException {
        try {
            DrPlanVO plan = new DrPlanVO(name, sourceSiteId, targetSiteId, direction);
            plan.setDescription(description);
            plan.setSourceVmId(sourceVmId);
            plan.setSourceExternalRef(sourceExternalRef);
            plan.setEngineType(engineType);
            plan.setEngineBindingType(engineBindingType);
            plan.setEngineBindingId(engineBindingId);
            plan.setRpoSeconds(rpoSeconds);
            plan.setRtoSeconds(rtoSeconds);
            plan.setScheduleJson(scheduleJson);
            plan.setPolicyJson(policyJson);
            plan.setMappingJson(mappingJson);
            plan.setQuiescePolicyJson(quiescePolicyJson);
            plan.setSourceWorkerHostId(sourceWorkerHostId);
            plan.setTargetWorkerHostId(targetWorkerHostId);
            plan.setCoordinatorWorkerHostId(coordinatorWorkerHostId);
            DrPlanVO created = drPlanService.createPlan(plan);
            if (Boolean.TRUE.equals(startSync)) {
                drRunService.startRun(created.getId(), DrConstants.RUN_TYPE_SYNC, "create-plan-initial-sync:" + created.getUuid(),
                        CallContext.current().getCallingUserId(), null, buildInitialSyncRequestJson());
                created = drPlanService.getPlan(created.getId());
            }
            DrPlanResponse response = drResponseGenerator.createPlanResponse(created, drPlanService.getActionEligibility(created.getId()));
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (RuntimeException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + RESPONSE_SUFFIX;
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccountId();
    }

    @Override
    public String getEventType() {
        return DisasterRecoveryClusterEventTypes.EVENT_DR_PLAN_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "Creating DR plan " + name;
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.DisasterRecoveryCluster;
    }

    private String buildInitialSyncRequestJson() {
        JsonObject request = new JsonObject();
        request.addProperty("source", APINAME);
        request.addProperty("initialProtectionSetup", true);
        request.addProperty("reason", "createDrPlan startSync");
        return request.toString();
    }
}
