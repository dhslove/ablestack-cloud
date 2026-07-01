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
import org.apache.cloudstack.context.CallContext;

import com.cloud.dr.DrPlanService;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.cluster.DisasterRecoveryClusterEventTypes;
import com.cloud.dr.response.DrResponseGenerator;

@APICommand(name = UpdateDrPlanCmd.APINAME,
        description = "Update a Cross Hypervisor DR plan",
        responseObject = DrPlanResponse.class,
        authorized = {RoleType.Admin})
public class UpdateDrPlanCmd extends BaseAsyncCmd {
    public static final String APINAME = "updateDrPlan";

    @Inject
    private DrPlanService drPlanService;
    @Inject
    private DrResponseGenerator drResponseGenerator;

    @Parameter(name = "id", type = CommandType.UUID, entityType = DrPlanResponse.class, required = true, description = "the DR plan ID")
    private Long id;

    @Parameter(name = "name", type = CommandType.STRING, description = "the DR plan name")
    private String name;

    @Parameter(name = "description", type = CommandType.STRING, description = "the DR plan description")
    private String description;

    @Parameter(name = "sourcevmid", type = CommandType.UUID, entityType = UserVmResponse.class, description = "the source VM ID")
    private Long sourceVmId;

    @Parameter(name = "sourceexternalref", type = CommandType.STRING, description = "the source external reference")
    private String sourceExternalRef;

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

    @Override
    public void execute() throws ServerApiException {
        try {
            DrPlanVO update = new DrPlanVO(name, 0L, 0L, "UPDATE");
            update.setDescription(description);
            update.setSourceVmId(sourceVmId);
            update.setSourceExternalRef(sourceExternalRef);
            update.setEngineType(engineType);
            update.setEngineBindingType(engineBindingType);
            update.setEngineBindingId(engineBindingId);
            update.setRpoSeconds(rpoSeconds);
            update.setRtoSeconds(rtoSeconds);
            update.setScheduleJson(scheduleJson);
            update.setPolicyJson(policyJson);
            update.setMappingJson(mappingJson);
            update.setQuiescePolicyJson(quiescePolicyJson);
            update.setSourceWorkerHostId(sourceWorkerHostId);
            update.setTargetWorkerHostId(targetWorkerHostId);
            update.setCoordinatorWorkerHostId(coordinatorWorkerHostId);
            DrPlanVO plan = drPlanService.updatePlan(id, update);
            DrPlanResponse response = drResponseGenerator.createPlanResponse(plan, drPlanService.getActionEligibility(plan.getId()));
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
        return DisasterRecoveryClusterEventTypes.EVENT_DR_PLAN_UPDATE;
    }

    @Override
    public String getEventDescription() {
        return "Updating DR plan " + id;
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.DisasterRecoveryCluster;
    }

    @Override
    public Long getApiResourceId() {
        return id;
    }
}
