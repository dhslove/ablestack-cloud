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
package com.cloud.dr.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.api.response.dr.DrEventResponse;
import org.apache.cloudstack.api.response.dr.DrPlanResponse;
import org.apache.cloudstack.api.response.dr.DrReplicaResponse;
import org.apache.cloudstack.api.response.dr.DrRestorePointResponse;
import org.apache.cloudstack.api.response.dr.DrRunResponse;
import org.apache.cloudstack.api.response.dr.DrRunStepResponse;
import org.apache.cloudstack.api.response.dr.DrSiteResponse;

import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.utils.component.ManagerBase;

public class DrResponseGenerator extends ManagerBase {
    @Inject
    private DrSiteDao drSiteDao;
    @Inject
    private DrPlanDao drPlanDao;

    public DrSiteResponse createSiteResponse(DrSiteVO site) {
        DrSiteResponse response = new DrSiteResponse();
        response.setObjectName("drsite");
        response.setId(site.getUuid());
        response.setName(site.getName());
        response.setDescription(site.getDescription());
        response.setSiteType(site.getSiteType());
        response.setHypervisorType(site.getHypervisorType());
        response.setEndpoint(site.getEndpoint());
        response.setCredentialRef(maskCredentialRef(site.getCredentialRef()));
        response.setZoneId(site.getZoneId());
        response.setVmwareDatacenterId(site.getVmwareDatacenterId());
        response.setState(site.getState());
        response.setHealthState(site.getHealthState());
        response.setCapabilitiesJson(site.getCapabilitiesJson());
        response.setLastChecked(site.getLastChecked());
        response.setCreated(site.getCreated());
        response.setRemoved(site.getRemoved());
        return response;
    }

    public DrPlanResponse createPlanResponse(DrPlanVO plan, Map<String, Boolean> actionEligibility) {
        DrPlanResponse response = new DrPlanResponse();
        response.setObjectName("drplan");
        response.setId(plan.getUuid());
        response.setName(plan.getName());
        response.setDescription(plan.getDescription());
        response.setSourceSiteId(resolveSiteUuid(plan.getSourceSiteId()));
        response.setTargetSiteId(resolveSiteUuid(plan.getTargetSiteId()));
        response.setSourceVmId(plan.getSourceVmId());
        response.setSourceExternalRef(plan.getSourceExternalRef());
        response.setDirection(plan.getDirection());
        response.setEngineType(plan.getEngineType());
        response.setEngineBindingType(plan.getEngineBindingType());
        response.setEngineBindingId(plan.getEngineBindingId());
        response.setState(plan.getState());
        response.setAdminState(plan.getAdminState());
        response.setActiveSide(plan.getActiveSide());
        response.setRpoSeconds(plan.getRpoSeconds());
        response.setRtoSeconds(plan.getRtoSeconds());
        response.setSourceWorkerHostId(plan.getSourceWorkerHostId());
        response.setTargetWorkerHostId(plan.getTargetWorkerHostId());
        response.setCoordinatorWorkerHostId(plan.getCoordinatorWorkerHostId());
        response.setLastSourceCheckpointAt(plan.getLastSourceCheckpointAt());
        response.setLastTargetDurableAt(plan.getLastTargetDurableAt());
        response.setTargetReadyAt(plan.getTargetReadyAt());
        response.setTargetReadyRpoSeconds(plan.getTargetReadyRpoSeconds());
        response.setLastRunId(plan.getLastRunId());
        response.setLastErrorCode(plan.getLastErrorCode());
        response.setLastErrorMessage(plan.getLastErrorMessage());
        response.setActionEligibility(actionEligibility);
        response.setCreated(plan.getCreated());
        response.setRemoved(plan.getRemoved());
        return response;
    }

    public DrRunResponse createRunResponse(DrRunVO run, List<DrRunStepVO> steps, boolean accepted) {
        DrRunResponse response = new DrRunResponse();
        response.setObjectName("drrun");
        response.setId(run.getUuid());
        response.setPlanId(resolvePlanUuid(run.getPlanId()));
        response.setRunType(run.getRunType());
        response.setState(run.getState());
        response.setAccepted(accepted);
        response.setIdempotencyKey(run.getIdempotencyKey());
        response.setRequestedByUserId(run.getRequestedByUserId());
        response.setAsyncJobId(run.getAsyncJobId());
        response.setExternalJobRef(run.getExternalJobRef());
        response.setCurrentStep(run.getCurrentStepName());
        response.setErrorCode(run.getErrorCode());
        response.setErrorMessage(run.getErrorMessage());
        response.setStarted(run.getStarted());
        response.setCompleted(run.getCompleted());
        response.setCreated(run.getCreated());
        response.setSteps(createRunStepResponses(steps));
        response.setProgressPercent(resolveProgress(steps));
        return response;
    }

    public DrRunStepResponse createRunStepResponse(DrRunStepVO step) {
        DrRunStepResponse response = new DrRunStepResponse();
        response.setObjectName("drrunstep");
        response.setId(step.getUuid());
        response.setRunId(step.getRunId());
        response.setStepName(step.getStepName());
        response.setStepOrder(step.getStepOrder());
        response.setState(step.getState());
        response.setProgress(step.getProgress());
        response.setDetailsJson(step.getDetailsJson());
        response.setErrorCode(step.getErrorCode());
        response.setErrorMessage(step.getErrorMessage());
        response.setStarted(step.getStarted());
        response.setCompleted(step.getCompleted());
        return response;
    }

    public DrEventResponse createEventResponse(DrEventVO event) {
        DrEventResponse response = new DrEventResponse();
        response.setObjectName("drevent");
        response.setId(event.getUuid());
        response.setPlanId(event.getPlanId());
        response.setRunId(event.getRunId());
        response.setEventType(event.getEventType());
        response.setSeverity(event.getSeverity());
        response.setSource(event.getSource());
        response.setMessage(event.getMessage());
        response.setDetailsJson(event.getDetailsJson());
        response.setCreated(event.getCreated());
        return response;
    }

    public DrReplicaResponse createReplicaResponse(DrReplicaVO replica) {
        DrReplicaResponse response = new DrReplicaResponse();
        response.setObjectName("drreplica");
        response.setId(replica.getUuid());
        response.setPlanId(replica.getPlanId());
        response.setTargetSiteId(replica.getTargetSiteId());
        response.setTargetVmId(replica.getTargetVmId());
        response.setTargetExternalRef(replica.getTargetExternalRef());
        response.setTargetVmName(replica.getTargetVmName());
        response.setState(replica.getState());
        response.setPowerState(replica.getPowerState());
        response.setHypervisorType(replica.getHypervisorType());
        response.setActiveSide(replica.getActiveSide());
        response.setRuntimeStateJson(replica.getRuntimeStateJson());
        response.setCreated(replica.getCreated());
        return response;
    }

    public DrRestorePointResponse createRestorePointResponse(DrRestorePointVO restorePoint) {
        DrRestorePointResponse response = new DrRestorePointResponse();
        response.setObjectName("drrestorepoint");
        response.setId(restorePoint.getUuid());
        response.setPlanId(restorePoint.getPlanId());
        response.setSourceSnapshotRef(restorePoint.getSourceSnapshotRef());
        response.setSourceCreated(restorePoint.getSourceCreated());
        response.setTargetReadyAt(restorePoint.getTargetReadyAt());
        response.setSourceRpoSeconds(restorePoint.getSourceRpoSeconds());
        response.setTargetReadyRpoSeconds(restorePoint.getTargetReadyRpoSeconds());
        response.setConsistencyLevel(restorePoint.getConsistencyLevel());
        response.setRestorePointType(restorePoint.getRestorePointType());
        response.setState(restorePoint.getState());
        response.setCreated(restorePoint.getCreated());
        return response;
    }

    public List<DrRunStepResponse> createRunStepResponses(List<DrRunStepVO> steps) {
        List<DrRunStepResponse> responses = new ArrayList<DrRunStepResponse>();
        if (steps != null) {
            for (DrRunStepVO step : steps) {
                responses.add(createRunStepResponse(step));
            }
        }
        return responses;
    }

    private Integer resolveProgress(List<DrRunStepVO> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        DrRunStepVO last = steps.get(steps.size() - 1);
        return last.getProgress();
    }

    private String resolveSiteUuid(long siteId) {
        DrSiteVO site = drSiteDao.findById(siteId);
        return site != null ? site.getUuid() : String.valueOf(siteId);
    }

    private String resolvePlanUuid(long planId) {
        DrPlanVO plan = drPlanDao.findById(planId);
        return plan != null ? plan.getUuid() : String.valueOf(planId);
    }

    private String maskCredentialRef(String credentialRef) {
        if (credentialRef == null || credentialRef.length() <= 4) {
            return credentialRef;
        }
        return "****" + credentialRef.substring(credentialRef.length() - 4);
    }
}
