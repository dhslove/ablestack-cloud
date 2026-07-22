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
import java.util.Map.Entry;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.api.response.dr.DrEventResponse;
import org.apache.cloudstack.api.response.dr.DrInventoryOptionResponse;
import org.apache.cloudstack.api.response.dr.DrPlanResponse;
import org.apache.cloudstack.api.response.dr.DrPlanInventoryResponse;
import org.apache.cloudstack.api.response.dr.DrReplicaResponse;
import org.apache.cloudstack.api.response.dr.DrRestorePointResponse;
import org.apache.cloudstack.api.response.dr.DrRunResponse;
import org.apache.cloudstack.api.response.dr.DrRunStepResponse;
import org.apache.cloudstack.api.response.dr.DrSiteHealthCheckResponse;
import org.apache.cloudstack.api.response.dr.DrSiteInventoryResponse;
import org.apache.cloudstack.api.response.dr.DrSiteResponse;

import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrCutoverSessionVO;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanReadiness;
import com.cloud.dr.DrPlanReadinessValidator;
import com.cloud.dr.DrPlanRuntimeVO;
import com.cloud.dr.DrProtectionAuthorityService;
import com.cloud.dr.DrProtectionAuthoritySnapshot;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrSiteHealthCheckVO;
import com.cloud.dr.DrSiteCredentialService;
import com.cloud.dr.DrSiteCredentialVO;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.DrTestSessionVO;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.dao.DrTestSessionDao;
import com.cloud.dr.inventory.DrInventoryOption;
import com.cloud.dr.inventory.DrPlanInventoryResult;
import com.cloud.dr.inventory.DrSiteInventoryResult;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

public class DrResponseGenerator extends ManagerBase {
    private static final Gson GSON = new Gson();
    private static final int MAX_API_MESSAGE_LENGTH = 2048;
    private static final int MAX_DETAILS_STRING_LENGTH = 1024;

    @Inject
    private DrSiteDao drSiteDao;
    @Inject
    private DrPlanDao drPlanDao;
    @Inject
    private DrRunDao drRunDao;
    @Inject
    private DrRunStepDao drRunStepDao;
    @Inject
    private DrTestSessionDao drTestSessionDao;
    @Inject
    private DrCutoverSessionDao drCutoverSessionDao;
    @Inject
    private DrSiteCredentialService drSiteCredentialService;
    @Inject
    private DrPlanReadinessValidator drPlanReadinessValidator;
    @Inject
    private DrProtectionAuthorityService drProtectionAuthorityService;

    public DrSiteResponse createSiteResponse(DrSiteVO site) {
        DrSiteResponse response = new DrSiteResponse();
        DrSiteCredentialVO configuredCredential = drSiteCredentialService != null ? drSiteCredentialService.findConfiguredCredential(site) : null;
        DrSiteCredentialVO latestCredential = configuredCredential != null ? configuredCredential
                : (drSiteCredentialService != null ? drSiteCredentialService.findLatestCredential(site.getId()) : null);
        response.setObjectName("drsite");
        response.setId(site.getUuid());
        response.setName(site.getName());
        response.setDescription(site.getDescription());
        response.setSiteType(site.getSiteType());
        response.setHypervisorType(site.getHypervisorType());
        response.setEndpoint(site.getEndpoint());
        response.setCredentialRef(maskCredentialRef(site.getCredentialRef()));
        response.setCredentialConfigured(configuredCredential != null);
        if (latestCredential != null) {
            response.setCredentialType(latestCredential.getCredentialType());
            response.setCredentialEndpoint(latestCredential.getEndpoint());
            response.setCredentialPrincipal(latestCredential.getPrincipal());
            response.setCredentialState(latestCredential.getState());
            response.setCredentialLastValidated(latestCredential.getLastValidated());
        } else if (StringUtils.isNotBlank(site.getCredentialRef())) {
            response.setCredentialState("LEGACY_REF");
        } else {
            response.setCredentialState("MISSING");
        }
        response.setActivePlanCount(drPlanDao != null ? drPlanDao.countActiveBySiteId(site.getId()) : 0L);
        response.setZoneId(site.getZoneId());
        response.setZoneExternalId(site.getZoneExternalId());
        response.setZoneName(site.getZoneName());
        response.setVmwareDatacenterId(site.getVmwareDatacenterId());
        response.setVmwareDatacenterExternalId(site.getVmwareDatacenterExternalId());
        response.setVmwareDatacenterName(site.getVmwareDatacenterName());
        response.setState(site.getState());
        response.setHealthState(site.getHealthState());
        JsonObject healthCheck = parseHealthCheck(site.getCapabilitiesJson());
        response.setHealthReasonCode(getString(healthCheck, "reasonCode"));
        response.setHealthMessage(getString(healthCheck, "message"));
        response.setHealthLatencyMs(getLong(healthCheck, "latencyMs"));
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
        response.setOperatingSide(StringUtils.defaultIfBlank(plan.getActiveSide(), "SOURCE"));
        response.setRpoSeconds(plan.getRpoSeconds());
        response.setRtoSeconds(plan.getRtoSeconds());
        response.setScheduleJson(plan.getScheduleJson());
        response.setPolicyJson(plan.getPolicyJson());
        response.setMappingJson(plan.getMappingJson());
        response.setQuiescePolicyJson(plan.getQuiescePolicyJson());
        response.setSourceWorkerHostId(plan.getSourceWorkerHostId());
        response.setTargetWorkerHostId(plan.getTargetWorkerHostId());
        response.setCoordinatorWorkerHostId(plan.getCoordinatorWorkerHostId());
        response.setLastSourceCheckpointAt(plan.getLastSourceCheckpointAt());
        response.setLastTargetDurableAt(plan.getLastTargetDurableAt());
        response.setTargetReadyAt(plan.getTargetReadyAt());
        response.setTargetReadyRpoSeconds(plan.getTargetReadyRpoSeconds());
        response.setLastRunId(plan.getLastRunId());
        DrRunVO latestRun = drRunDao != null ? drRunDao.findLatestByPlanId(plan.getId()) : null;
        JsonObject latestRuntime = new JsonObject();
        String runtimeErrorCode = null;
        if (latestRun != null) {
            response.setLastRun(createRunResponse(latestRun, null, false));
            response.setRuntimeProjectionState(latestRun.getProjectionState());
            latestRuntime = parseObject(latestRun.getLastStatusJson());
            response.setRuntimeState(firstString(latestRuntime, "state"));
            response.setRuntimeStep(firstString(latestRuntime, "step"));
            runtimeErrorCode = resolveRuntimeErrorCode(latestRuntime, latestRun);
            response.setRuntimeErrorCode(runtimeErrorCode);
            Integer controlProtocolVersion = firstInteger(latestRuntime, "control_protocol_version");
            Long controlGeneration = firstLong(latestRuntime, "control_generation");
            Long controlAckGeneration = firstLong(latestRuntime, "control_ack_generation");
            response.setRuntimeControlProtocolVersion(controlProtocolVersion);
            response.setRuntimeControlGeneration(controlGeneration);
            response.setRuntimeControlAckGeneration(controlAckGeneration);
            response.setRuntimeControlState(firstString(latestRuntime, "control_state"));
            response.setRuntimeCycleState(firstString(latestRuntime, "cycle_state"));
            response.setRuntimeTransitionState(firstString(latestRuntime, "transition_state"));
            response.setRuntimeCheckpointLeaseState(firstString(latestRuntime, "checkpoint_lease_state"));
            response.setFailedComponent(firstString(latestRuntime, "failed_component"));
            response.setDataCommitState(firstString(latestRuntime, "data_commit_state"));
            response.setDataCopied(firstBoolean(latestRuntime, "data_copied"));
            response.setMetadataCommitted(firstBoolean(latestRuntime, "metadata_committed"));
            response.setTargetDurable(firstBoolean(latestRuntime, "target_durable"));
            response.setCycleRetryMode(firstString(latestRuntime, "cycle_retry_mode"));
            response.setRuntimeControlReady(controlProtocolVersion != null && controlProtocolVersion >= 2
                    && controlGeneration != null && controlAckGeneration != null && controlAckGeneration >= controlGeneration);
            response.setRuntimeProjectionMessage(summarizeError(runtimeErrorCode,
                    StringUtils.defaultIfBlank(latestRun.getErrorMessage(), latestRun.getCurrentStepName())));
            response.setSourceDiskMapPath(firstString(latestRuntime, "source_disk_map_path"));
            response.setTargetDiskMapPath(firstString(latestRuntime, "target_disk_map_path"));
            response.setDiskMapRole(firstString(latestRuntime, "disk_map_role"));
            response.setTargetDiskCount(firstInteger(latestRuntime, "target_disk_count"));
            response.setTargetDiskInvalidCount(firstInteger(latestRuntime, "target_disk_invalid_count"));
            populateCbtStatus(response, latestRuntime);
            populateSourceOpenStatus(response, latestRuntime);
            populateSourceSnapshotStatus(response, latestRuntime);
        }
        DrCutoverSessionVO cutoverSession = drCutoverSessionDao != null
                ? drCutoverSessionDao.findLatestActiveByPlanId(plan.getId()) : null;
        if (cutoverSession != null) {
            response.setCutoverSessionState(cutoverSession.getState());
            response.setCloudPromotionState(cutoverSession.getCloudPromotionState());
            response.setCutoverTargetPowerState(cutoverSession.getTargetPowerState());
            response.setCutoverBootValidationState(cutoverSession.getBootValidationState());
            response.setEngineAckState(cutoverSession.getEngineAckState());
            response.setCutoverAuthorityGeneration(cutoverSession.getCloudAuthorityGeneration());
            response.setCutoverCompletedAt(cutoverSession.getCompletedAt());
        }
        response.setProtectionPhase(resolveProtectionPhase(plan, cutoverSession));
        response.setLastErrorCode(plan.getLastErrorCode());
        response.setLastErrorMessage(summarizeError(StringUtils.defaultIfBlank(runtimeErrorCode, plan.getLastErrorCode()), plan.getLastErrorMessage()));
        response.setActionEligibility(actionEligibility);
        DrPlanReadiness readiness = null;
        DrProtectionAuthoritySnapshot authority = drProtectionAuthorityService != null
                ? drProtectionAuthorityService.getAuthority(plan.getId()) : null;
        if (authority != null && authority.getRuntime() != null) {
            response.setProtectionState(authority.getProtectionState());
            response.setFreshnessState(authority.getFreshnessState());
            response.setProjectionIntegrityState(authority.getProjectionIntegrityState());
            response.setProjectionIntegrityCode(authority.getProjectionIntegrityCode());
            response.setProjectionIntegritySequence(authority.getProjectionIntegritySequence());
            response.setSchedulerState(authority.getSchedulerState());
            response.setSchedulerDesiredState(authority.getRuntime().getSchedulerDesiredState());
            response.setSchedulerServiceUnit(authority.getRuntime().getSchedulerServiceUnit());
            response.setSchedulerUnitActiveState(authority.getRuntime().getSchedulerUnitActiveState());
            response.setSchedulerUnitSubState(authority.getRuntime().getSchedulerUnitSubState());
            response.setSchedulerRecoveryState(authority.getRuntime().getSchedulerRecoveryState());
            response.setSchedulerRecoveryTrigger(authority.getRuntime().getSchedulerRecoveryTrigger());
            response.setSchedulerPidAlive(authority.isSchedulerPidAlive());
            response.setSchedulerSessionUuid(authority.getSchedulerSessionUuid());
            response.setSchedulerLeaseEpoch(authority.getSchedulerLeaseEpoch());
            response.setAuthoritySequence(authority.getAuthoritySequence());
            response.setSchedulerHealth(authority.getSchedulerHealthState());
            response.setReplicationActivity(authority.getReplicationActivityState());
            response.setActiveWorkerRunUuid(authority.getActiveWorkerRunUuid());
            response.setWorkerHeartbeatAt(authority.getWorkerHeartbeatAt());
            response.setOwnerMatched(authority.isOwnerMatched());
            response.setNormalCutoverReady(authority.isNormalCutoverReady());
            response.setNormalCutoverReason(authority.getNormalCutoverReason());
            response.setAuthorityGeneration(authority.getRuntimeGeneration());
            response.setCurrentCycleSequence(authority.getCurrentCycleSequence());
            response.setCurrentCycleState(authority.getCurrentCycleState());
            response.setCurrentCycleMode(authority.getCurrentCycleMode());
            response.setBaselineState(authority.getBaselineState());
            response.setReseedReason(authority.getReseedReason());
            response.setRpoAgeSeconds(authority.getRpoAgeSeconds());
            response.setRpoOverdue(authority.isRpoOverdue());
            response.setEffectiveState(authority.getProtectionState());
            populateCurrentProtectionControlState(response, authority.getRuntime());
        }
        if (drPlanReadinessValidator != null) {
            readiness = drPlanReadinessValidator.validate(plan);
            response.setReadinessState(readiness.getState());
            response.setReadinessReasonCode(readiness.getReasonCode());
            response.setReadinessMessage(readiness.getMessage());
            response.setExecutionReady(readiness.isExecutionReady()
                    && (authority == null || authority.getRuntime() == null || authority.isNormalCutoverReady()));
            response.setReleaseReady(readiness.isReleaseReady());
            response.setEngineAccepted(readiness.isEngineAccepted());
            response.setTargetMaterialized(readiness.isTargetMaterialized());
            response.setTargetVmPresent(readiness.isTargetVmPresent());
            response.setTargetStoragePresent(readiness.isTargetStoragePresent());
            response.setTargetNetworkPresent(readiness.isTargetNetworkPresent());
            response.setRestorePointPresent(readiness.isRestorePointPresent());
            response.setDurableCheckpointPresent(readiness.isDurableCheckpointPresent());
            response.setReadinessBlockingReasons(readiness.getBlockingReasons());
            response.setReadinessWarnings(readiness.getWarnings());
            if (authority == null || authority.getRuntime() == null) {
                response.setEffectiveState(resolveEffectivePlanState(plan, latestRun, latestRuntime, readiness));
            }
        } else {
            if (authority == null || authority.getRuntime() == null) {
                response.setEffectiveState(resolveEffectivePlanState(plan, latestRun, latestRuntime, null));
            }
        }
        response.setInitialSyncInProgress(isInitialSyncInProgress(latestRun, latestRuntime));
        response.setTargetMaterializationState(resolveTargetMaterializationState(latestRun, latestRuntime, readiness));
        response.setTargetMaterializationMessage(resolveTargetMaterializationMessage(latestRun, latestRuntime, readiness));
        response.setCreated(plan.getCreated());
        response.setRemoved(plan.getRemoved());
        return response;
    }

    private String resolveProtectionPhase(DrPlanVO plan, DrCutoverSessionVO session) {
        if (session != null && StringUtils.equalsIgnoreCase(session.getCloudPromotionState(), "PROMOTED")) {
            return StringUtils.equalsIgnoreCase(session.getEngineAckState(), "ACKNOWLEDGED")
                    ? "FAILED_OVER_UNPROTECTED" : "TARGET_PROMOTED_ENGINE_PENDING";
        }
        if (session != null && StringUtils.equalsAnyIgnoreCase(session.getState(), "CUTOVER_READY", "CLOUD_PROMOTED")) {
            return session.getState();
        }
        if (StringUtils.equalsIgnoreCase(plan.getActiveSide(), "TARGET")) {
            return "FAILED_OVER_UNPROTECTED";
        }
        return StringUtils.defaultIfBlank(plan.getState(), "NEW");
    }

    private void populateCurrentProtectionControlState(DrPlanResponse response, DrPlanRuntimeVO runtime) {
        JsonObject currentRuntime = parseObject(runtime != null ? runtime.getStatusJson() : null);
        Integer controlProtocolVersion = firstInteger(currentRuntime, "control_protocol_version");
        Long controlGeneration = firstLong(currentRuntime, "control_generation");
        Long controlAckGeneration = firstLong(currentRuntime, "control_ack_generation");
        response.setRuntimeControlProtocolVersion(controlProtocolVersion);
        response.setRuntimeControlGeneration(controlGeneration);
        response.setRuntimeControlAckGeneration(controlAckGeneration);
        response.setRuntimeControlState(firstString(currentRuntime, "control_state"));
        response.setRuntimeCycleState(StringUtils.defaultIfBlank(runtime.getCurrentCycleState(),
                firstString(currentRuntime, "cycle_state")));
        response.setRuntimeTransitionState(firstString(currentRuntime, "transition_state"));
        response.setRuntimeCheckpointLeaseState(firstString(currentRuntime, "checkpoint_lease_state"));
        response.setRuntimeControlReady(controlProtocolVersion != null && controlProtocolVersion >= 2
                && controlGeneration != null && controlAckGeneration != null
                && controlAckGeneration >= controlGeneration);
    }

    public DrRunResponse createRunResponse(DrRunVO run, List<DrRunStepVO> steps, boolean accepted) {
        DrRunResponse response = new DrRunResponse();
        response.setObjectName("drrun");
        response.setId(run.getUuid());
        response.setPlanId(resolvePlanUuid(run.getPlanId()));
        response.setRunType(run.getRunType());
        response.setState(run.getState());
        response.setAccepted(accepted || run.isEngineAccepted());
        response.setEngineAccepted(run.isEngineAccepted());
        response.setIdempotencyKey(run.getIdempotencyKey());
        response.setRequestedByUserId(run.getRequestedByUserId());
        response.setAsyncJobId(run.getAsyncJobId());
        response.setExternalJobRef(run.getExternalJobRef());
        response.setAcceptedAt(run.getAcceptedAt());
        response.setDispatchStarted(run.getDispatchStarted());
        response.setDispatchCompleted(run.getDispatchCompleted());
        response.setProjectionState(run.getProjectionState());
        response.setProjectionChecked(run.getProjectionChecked());
        JsonObject runtime = parseObject(run.getLastStatusJson());
        response.setRuntimeState(firstString(runtime, "state"));
        response.setRuntimeStep(firstString(runtime, "step"));
        response.setRuntimeErrorCode(resolveRuntimeErrorCode(runtime, run));
        response.setSourceDiskMapPath(firstString(runtime, "source_disk_map_path"));
        response.setTargetDiskMapPath(firstString(runtime, "target_disk_map_path"));
        response.setDiskMapRole(firstString(runtime, "disk_map_role"));
        response.setTargetDiskCount(firstInteger(runtime, "target_disk_count"));
        response.setTargetDiskInvalidCount(firstInteger(runtime, "target_disk_invalid_count"));
        populateCbtStatus(response, runtime);
        populateSourceOpenStatus(response, runtime);
        populateSourceSnapshotStatus(response, runtime);
        response.setWorkerState(firstString(runtime, "worker_state"));
        response.setWorkerExitCode(firstInteger(runtime, "worker_exit_code"));
        response.setRetryable(run.isRetryable());
        response.setRetryCount(run.getRetryCount());
        response.setRetryAfterSeconds(run.getRetryAfterSeconds());
        response.setNextRetryAt(run.getNextRetryAt());
        response.setCurrentStep(run.getCurrentStepName());
        response.setErrorCode(run.getErrorCode());
        response.setErrorMessage(summarizeError(resolveRuntimeErrorCode(runtime, run), run.getErrorMessage()));
        response.setFailedComponent(firstString(runtime, "failed_component"));
        response.setDataCommitState(firstString(runtime, "data_commit_state"));
        response.setDataCopied(firstBoolean(runtime, "data_copied"));
        response.setMetadataCommitted(firstBoolean(runtime, "metadata_committed"));
        response.setTargetDurable(firstBoolean(runtime, "target_durable"));
        response.setCycleRetryMode(firstString(runtime, "cycle_retry_mode"));
        response.setStarted(run.getStarted());
        response.setCompleted(run.getCompleted());
        response.setCreated(run.getCreated());
        response.setSteps(createRunStepResponses(steps));
        response.setProgressPercent(resolveProgress(steps));
        DrTestSessionVO testSession = drTestSessionDao.findActiveByRunId(run.getId());
        if (testSession != null) {
            response.setTestSessionId(testSession.getUuid());
            response.setTestSessionState(testSession.getState());
            response.setTestVmId(testSession.getTargetVmUuid());
            response.setTestVmName(testSession.getTargetVmName());
            response.setTestNetworkMode(testSession.getNetworkMode());
            response.setTestBootValidationState(testSession.getBootValidationState());
        }
        return response;
    }

    private String resolveRuntimeErrorCode(JsonObject runtime, DrRunVO run) {
        String runtimeErrorCode = firstString(runtime, "error_code");
        if (StringUtils.isNotBlank(runtimeErrorCode)) {
            return runtimeErrorCode;
        }
        return isFailedRun(run) ? run.getErrorCode() : null;
    }

    private boolean isFailedRun(DrRunVO run) {
        return run != null && StringUtils.equals(run.getState(), DrConstants.RUN_STATE_FAILED);
    }

    private boolean isActiveSyncRun(DrRunVO run) {
        return run != null
                && StringUtils.equals(run.getRunType(), DrConstants.RUN_TYPE_SYNC)
                && StringUtils.equalsAny(run.getState(), DrConstants.RUN_STATE_ACCEPTED, DrConstants.RUN_STATE_RUNNING,
                DrConstants.RUN_STATE_RETRYING, DrConstants.RUN_STATE_DISPATCHING, DrConstants.RUN_STATE_QUEUED);
    }

    private boolean isInitialSyncInProgress(DrRunVO run, JsonObject runtime) {
        if (!isActiveSyncRun(run) || StringUtils.isNotBlank(firstString(runtime, "error_code"))) {
            return false;
        }
        String runtimeState = StringUtils.upperCase(firstString(runtime, "state"));
        String runtimeStep = StringUtils.lowerCase(firstString(runtime, "step"));
        return StringUtils.equalsAny(runtimeState, "SYNCING", "RUNNING", "SEEDING")
                || StringUtils.contains(runtimeStep, "seed")
                || StringUtils.contains(runtimeStep, "transfer");
    }

    private String resolveTargetMaterializationState(DrRunVO run, JsonObject runtime, DrPlanReadiness readiness) {
        if (readiness != null && DrPlanReadiness.STATE_TARGET_READY.equals(readiness.getState())) {
            return "TARGET_READY";
        }
        if (readiness != null && DrPlanReadiness.STATE_DEGRADED.equals(readiness.getState())) {
            return "DEGRADED";
        }
        if (!isInitialSyncInProgress(run, runtime)) {
            return readiness != null ? readiness.getState() : null;
        }
        Boolean durableCheckpointPresent = firstBoolean(runtime, "restore_point_present");
        Boolean targetVmPresent = firstBoolean(runtime, "target_vm_present");
        if (Boolean.TRUE.equals(durableCheckpointPresent) && !Boolean.TRUE.equals(targetVmPresent)) {
            return "TARGET_MATERIALIZING";
        }
        return "INITIAL_SEEDING";
    }

    private String resolveTargetMaterializationMessage(DrRunVO run, JsonObject runtime, DrPlanReadiness readiness) {
        String state = resolveTargetMaterializationState(run, runtime, readiness);
        if (StringUtils.equals(state, "TARGET_READY")) {
            return "Target recovery resources are ready";
        }
        if (StringUtils.equals(state, "TARGET_MATERIALIZING")) {
            return "Initial seed has durable data and the target VM is being materialized";
        }
        if (StringUtils.equals(state, "INITIAL_SEEDING")) {
            return "Initial seed is transferring data; the target VM is not expected yet";
        }
        return readiness != null ? readiness.getMessage() : null;
    }

    private String resolveEffectivePlanState(DrPlanVO plan, DrRunVO latestRun, JsonObject runtime, DrPlanReadiness readiness) {
        if (isActiveSyncRun(latestRun)) {
            return DrConstants.PLAN_STATE_SYNCING;
        }
        if (latestRun != null && StringUtils.equals(latestRun.getState(), DrConstants.RUN_STATE_FAILED)) {
            return DrConstants.PLAN_STATE_ERROR;
        }
        String runtimeState = StringUtils.upperCase(firstString(runtime, "state"));
        String workerState = StringUtils.upperCase(firstString(runtime, "worker_state"));
        String runtimeErrorCode = firstString(runtime, "error_code");
        if (StringUtils.equalsAny(runtimeState, "ERROR", "FAILED")
                || StringUtils.equals(workerState, "FAILED")
                || StringUtils.isNotBlank(runtimeErrorCode)) {
            return DrConstants.PLAN_STATE_ERROR;
        }
        if (readiness != null) {
            if (DrPlanReadiness.STATE_TARGET_READY.equals(readiness.getState())) {
                return DrConstants.PLAN_STATE_READY;
            }
            if (DrPlanReadiness.STATE_TARGET_MATERIALIZING.equals(readiness.getState())) {
                return DrConstants.PLAN_STATE_SYNCING;
            }
            if (DrPlanReadiness.STATE_DEGRADED.equals(readiness.getState())) {
                return DrConstants.PLAN_STATE_ERROR;
            }
        }
        return plan != null ? plan.getState() : null;
    }

    private void populateCbtStatus(DrPlanResponse response, JsonObject runtime) {
        JsonObject cbtStatus = firstObject(runtime, "cbt_status");
        response.setRuntimeCbtEnabled(firstBoolean(cbtStatus, "enabled"));
        response.setRuntimeCbtDiskId(StringUtils.defaultIfBlank(firstString(cbtStatus, "cbtDiskId"), firstString(cbtStatus, "sourceDiskRef")));
        response.setRuntimeCbtMessage(firstString(cbtStatus, "message"));
        response.setRuntimeCbtGovcBin(firstString(cbtStatus, "govcBin"));
        response.setRuntimeCbtCheckedAtEpochMs(firstLong(cbtStatus, "checkedAtEpochMs"));
    }

    private void populateCbtStatus(DrRunResponse response, JsonObject runtime) {
        JsonObject cbtStatus = firstObject(runtime, "cbt_status");
        response.setRuntimeCbtEnabled(firstBoolean(cbtStatus, "enabled"));
        response.setRuntimeCbtDiskId(StringUtils.defaultIfBlank(firstString(cbtStatus, "cbtDiskId"), firstString(cbtStatus, "sourceDiskRef")));
        response.setRuntimeCbtMessage(firstString(cbtStatus, "message"));
        response.setRuntimeCbtGovcBin(firstString(cbtStatus, "govcBin"));
        response.setRuntimeCbtCheckedAtEpochMs(firstLong(cbtStatus, "checkedAtEpochMs"));
    }

    private void populateSourceOpenStatus(DrPlanResponse response, JsonObject runtime) {
        JsonObject status = firstObject(runtime, "source_open");
        response.setRuntimeSourceOpenReady(firstBoolean(status, "ready"));
        response.setRuntimeSourceOpenErrorCode(firstString(status, "error_code"));
        response.setRuntimeSourceOpenMessage(summarizeError(firstString(status, "error_code"), firstString(status, "message")));
    }

    private void populateSourceOpenStatus(DrRunResponse response, JsonObject runtime) {
        JsonObject status = firstObject(runtime, "source_open");
        response.setRuntimeSourceOpenReady(firstBoolean(status, "ready"));
        response.setRuntimeSourceOpenErrorCode(firstString(status, "error_code"));
        response.setRuntimeSourceOpenMessage(summarizeError(firstString(status, "error_code"), firstString(status, "message")));
    }

    private void populateSourceSnapshotStatus(DrPlanResponse response, JsonObject runtime) {
        JsonObject status = firstObject(runtime, "source_snapshot");
        response.setRuntimeSourceSnapshotReady(firstBoolean(status, "ready"));
        response.setRuntimeSourceSnapshotErrorCode(firstString(status, "error_code"));
        response.setRuntimeSourceSnapshotMessage(summarizeError(firstString(status, "error_code"), firstString(status, "message")));
        response.setRuntimeSourceSnapshotName(firstString(status, "snapshotName"));
        response.setRuntimeSourceSnapshotRefPresent(firstBoolean(status, "snapshotRefPresent"));
        response.setRuntimeSourceSnapshotLifecycleState(firstString(status, "lifecycleState"));
        response.setRuntimeSourceSnapshotCleanupRequired(firstBoolean(status, "cleanupRequired"));
        response.setRuntimeSourceSnapshotLastRef(firstString(status, "lastSnapshotRef"));
        response.setRuntimeSourceSnapshotCleanedAtEpochMs(firstLong(status, "cleanedAtEpochMs"));
    }

    private void populateSourceSnapshotStatus(DrRunResponse response, JsonObject runtime) {
        JsonObject status = firstObject(runtime, "source_snapshot");
        response.setRuntimeSourceSnapshotReady(firstBoolean(status, "ready"));
        response.setRuntimeSourceSnapshotErrorCode(firstString(status, "error_code"));
        response.setRuntimeSourceSnapshotMessage(summarizeError(firstString(status, "error_code"), firstString(status, "message")));
        response.setRuntimeSourceSnapshotName(firstString(status, "snapshotName"));
        response.setRuntimeSourceSnapshotRefPresent(firstBoolean(status, "snapshotRefPresent"));
        response.setRuntimeSourceSnapshotLifecycleState(firstString(status, "lifecycleState"));
        response.setRuntimeSourceSnapshotCleanupRequired(firstBoolean(status, "cleanupRequired"));
        response.setRuntimeSourceSnapshotLastRef(firstString(status, "lastSnapshotRef"));
        response.setRuntimeSourceSnapshotCleanedAtEpochMs(firstLong(status, "cleanedAtEpochMs"));
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

    private String firstString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return StringUtils.trimToNull(object.get(key).getAsString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonObject firstObject(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return new JsonObject();
        }
        try {
            JsonElement element = object.get(key);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private Integer firstInteger(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Long firstLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Boolean firstBoolean(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            String value = firstString(object, key);
            if (StringUtils.equalsIgnoreCase(value, "true")
                    || StringUtils.equalsIgnoreCase(value, "enabled")
                    || StringUtils.equalsIgnoreCase(value, "yes")) {
                return true;
            }
            if (StringUtils.equalsIgnoreCase(value, "false")
                    || StringUtils.equalsIgnoreCase(value, "disabled")
                    || StringUtils.equalsIgnoreCase(value, "no")) {
                return false;
            }
            return null;
        }
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
        response.setDetailsJson(safeDetailsJson(step.getDetailsJson()));
        response.setErrorCode(step.getErrorCode());
        response.setErrorMessage(summarizeError(step.getErrorCode(), step.getErrorMessage()));
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
        response.setMessage(sanitizeApiString(event.getMessage(), MAX_API_MESSAGE_LENGTH));
        response.setDetailsJson(safeDetailsJson(event.getDetailsJson()));
        response.setCreated(event.getCreated());
        return response;
    }

    public DrSiteHealthCheckResponse createSiteHealthCheckResponse(DrSiteHealthCheckVO history) {
        DrSiteHealthCheckResponse response = new DrSiteHealthCheckResponse();
        response.setObjectName("drsitehealthcheck");
        response.setId(history.getUuid());
        response.setSiteId(history.getSiteUuid());
        response.setSiteName(history.getSiteName());
        response.setSiteType(history.getSiteType());
        response.setHypervisorType(history.getHypervisorType());
        response.setEndpoint(history.getEndpoint());
        response.setCredentialId(history.getCredentialId());
        response.setCredentialState(history.getCredentialState());
        response.setTriggerType(history.getTriggerType());
        response.setHealthState(history.getHealthState());
        response.setReasonCode(history.getReasonCode());
        response.setMessage(sanitizeApiString(history.getMessage(), MAX_API_MESSAGE_LENGTH));
        response.setLatencyMs(history.getLatencyMs());
        response.setCheckedAt(history.getCheckedAt());
        response.setManagementServerId(history.getManagementServerId());
        response.setJobId(history.getJobId());
        response.setDetailsJson(safeDetailsJson(history.getDetailsJson()));
        response.setCreated(history.getCreated());
        return response;
    }

    public DrSiteInventoryResponse createSiteInventoryResponse(DrSiteInventoryResult result) {
        DrSiteInventoryResponse response = new DrSiteInventoryResponse();
        response.setObjectName("drsiteinventory");
        response.setSiteId(result.getSiteId());
        response.setSiteType(result.getSiteType());
        response.setHealthState(result.getHealthState());
        response.setReasonCode(result.getReasonCode());
        response.setMessage(result.getMessage());
        response.setLatencyMs(result.getLatencyMs());
        response.setCheckedAt(result.getCheckedAt());
        response.setZones(createInventoryOptionResponses(result.getZones()));
        response.setVmwareDatacenters(createInventoryOptionResponses(result.getVmwareDatacenters()));
        return response;
    }

    public DrPlanInventoryResponse createPlanInventoryResponse(DrPlanInventoryResult result) {
        DrPlanInventoryResponse response = new DrPlanInventoryResponse();
        response.setObjectName("drplaninventory");
        response.setSourceSiteId(result.getSourceSiteId());
        response.setTargetSiteId(result.getTargetSiteId());
        response.setDirection(result.getDirection());
        response.setHealthState(result.getHealthState());
        response.setReasonCode(result.getReasonCode());
        response.setMessage(result.getMessage());
        response.setLatencyMs(result.getLatencyMs());
        response.setCheckedAt(result.getCheckedAt());
        response.setTargetZone(createInventoryOptionResponse(result.getTargetZone()));
        response.setSourceHardware(result.getSourceHardware());
        response.setSourceWorkloads(createInventoryOptionResponses(result.getSourceWorkloads()));
        response.setSourceDisks(createInventoryOptionResponses(result.getSourceDisks()));
        response.setSourceNics(createInventoryOptionResponses(result.getSourceNics()));
        response.setSourceWorkerHosts(createInventoryOptionResponses(result.getSourceWorkerHosts()));
        response.setTargetWorkerHosts(createInventoryOptionResponses(result.getTargetWorkerHosts()));
        response.setCoordinatorWorkerHosts(createInventoryOptionResponses(result.getCoordinatorWorkerHosts()));
        response.setTargetStorageOptions(createInventoryOptionResponses(result.getTargetStorageOptions()));
        response.setTargetComputeOptions(createInventoryOptionResponses(result.getTargetComputeOptions()));
        response.setTargetServiceOfferings(createInventoryOptionResponses(result.getTargetServiceOfferings()));
        response.setTargetDiskOfferings(createInventoryOptionResponses(result.getTargetDiskOfferings()));
        response.setTargetNetworkOptions(createInventoryOptionResponses(result.getTargetNetworkOptions()));
        response.setTargetFolderOptions(createInventoryOptionResponses(result.getTargetFolderOptions()));
        response.setBlockingReasons(result.getBlockingReasons());
        response.setWarnings(result.getWarnings());
        return response;
    }

    private DrInventoryOptionResponse createInventoryOptionResponse(DrInventoryOption option) {
        if (option == null) {
            return null;
        }
        List<DrInventoryOption> options = new ArrayList<DrInventoryOption>();
        options.add(option);
        List<DrInventoryOptionResponse> responses = createInventoryOptionResponses(options);
        return responses.isEmpty() ? null : responses.get(0);
    }

    public List<DrInventoryOptionResponse> createInventoryOptionResponses(List<DrInventoryOption> options) {
        List<DrInventoryOptionResponse> responses = new ArrayList<DrInventoryOptionResponse>();
        if (options != null) {
            for (DrInventoryOption option : options) {
                DrInventoryOptionResponse response = new DrInventoryOptionResponse();
                response.setObjectName("drinventoryoption");
                response.setId(option.getId());
                response.setValue(option.getValue());
                response.setExternalId(option.getExternalId());
                response.setLocalId(option.getLocalId());
                response.setReferenceType(option.getReferenceType());
                response.setSourceVmId(option.getSourceVmId());
                response.setExternalRef(option.getExternalRef());
                response.setState(option.getState());
                response.setHypervisorType(option.getHypervisorType());
                response.setName(option.getName());
                response.setDescription(option.getDescription());
                response.setType(option.getType());
                response.setSelectable(option.isSelectable());
                response.setDetailsJson(option.getDetails() == null || option.getDetails().isEmpty() ? null : safeDetailsJson(GSON.toJson(option.getDetails())));
                responses.add(response);
            }
        }
        return responses;
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
        response.setRunId(restorePoint.getRunId());
        response.setCheckpointSequence(restorePoint.getCheckpointSequence());
        response.setCheckpointCycleType(restorePoint.getCheckpointCycleType());
        response.setCheckpointRef(restorePoint.getSourceSnapshotRef());
        response.setSourceSnapshotRef(restorePoint.getSourceSnapshotRef());
        response.setSourceCreated(restorePoint.getSourceCreated());
        response.setTargetReadyAt(restorePoint.getTargetReadyAt());
        response.setSourceRpoSeconds(restorePoint.getSourceRpoSeconds());
        response.setTargetReadyRpoSeconds(restorePoint.getTargetReadyRpoSeconds());
        response.setConsistencyLevel(restorePoint.getConsistencyLevel());
        response.setRestorePointType(restorePoint.getRestorePointType());
        response.setState(restorePoint.getState());
        response.setEffectiveMode(restorePoint.getEffectiveMode());
        response.setRequestedMode(restorePoint.getRequestedMode());
        response.setAutomaticReseed(restorePoint.getAutomaticReseed());
        response.setModeDecisionCode(restorePoint.getModeDecisionCode());
        response.setReseedReason(restorePoint.getReseedReason());
        response.setInvalidBaselineDiskCount(restorePoint.getInvalidBaselineDiskCount());
        response.setIncrementalVerified(restorePoint.getIncrementalVerified());
        response.setMetricsEstimated(restorePoint.getMetricsEstimated());
        response.setVirtualBytes(restorePoint.getVirtualBytes());
        response.setChangedBytes(restorePoint.getChangedBytes());
        response.setSourceReadBytes(restorePoint.getSourceReadBytes());
        response.setTargetWrittenBytes(restorePoint.getTargetWrittenBytes());
        response.setTransferPayloadBytes(restorePoint.getTransferPayloadBytes());
        response.setChangedExtentCount(restorePoint.getChangedExtentCount());
        response.setDurationMs(restorePoint.getDurationMs());
        response.setThroughputBps(restorePoint.getThroughputBps());
        response.setBaselineGeneration(restorePoint.getBaselineGeneration());
        response.setCycleToken(restorePoint.getCycleToken());
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

    private String summarizeError(String errorCode, String message) {
        String sanitized = sanitizeApiString(message, MAX_API_MESSAGE_LENGTH);
        if (StringUtils.isNotBlank(sanitized)) {
            return sanitized;
        }
        if (StringUtils.isBlank(errorCode)) {
            return null;
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_CONNECT_INVALID)) {
            return "VMware VDDK rejected the source connection parameters. Check the source VM reference, snapshot reference, vCenter endpoint, and VDDK compatibility.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_THUMBPRINT_UNRESOLVED)) {
            return "VMware VDDK requires the source vCenter certificate thumbprint. Check vCenter 443 reachability and TLS verification settings.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_EXPORT_UNAVAILABLE)) {
            return "VMware VDDK NBD export is unavailable for the selected source disk. Check the source disk path and VDDK transport availability.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_SOURCE_LOCKED)) {
            return "VMware source disk is locked. Use a run snapshot or release the conflicting VMware disk lock before retrying.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_VDDK_OPEN_DENIED)) {
            return "VMware VDDK cannot open the selected source disk because access was denied. Check vCenter privileges and datastore access.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID)) {
            return "VMware data mover could not open the VDDK NBD source graph. Check the source disk path and source-open preflight evidence.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VMWARE_SNAPSHOT_REF_UNRESOLVED)) {
            return "VMware source snapshot was created, but its MoRef could not be resolved. FTCTL marks the snapshot for cleanup before retry.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VDDK_LIBDIR_UNRESOLVED)) {
            return "The selected data-plane worker has no usable VDDK library directory.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_VDDK_LIBRARY_LOAD_FAILED)) {
            return "The selected data-plane worker cannot load the VDDK library through nbdkit.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_CBT_METRICS_INVALID)) {
            return "Disk data was copied, but cycle metrics validation failed. No completed checkpoint was published.";
        }
        if (StringUtils.equalsIgnoreCase(errorCode, DrConstants.ERROR_CBT_LOCAL_COMMIT_FAILED)) {
            return "Disk data was copied, but the local cycle metadata commit failed. No completed checkpoint was published.";
        }
        return sanitizeApiString(errorCode, MAX_API_MESSAGE_LENGTH);
    }

    private String safeDetailsJson(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return GSON.toJson(sanitizeJsonElement(parsed, null));
        } catch (RuntimeException e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("message", sanitizeApiString(json, MAX_DETAILS_STRING_LENGTH));
            fallback.addProperty("rawDetailsRedacted", true);
            fallback.addProperty("parseError", true);
            return GSON.toJson(fallback);
        }
    }

    private JsonElement sanitizeJsonElement(JsonElement element, String key) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (isSensitiveKey(key)) {
            return new JsonPrimitive("<redacted>");
        }
        if (element.isJsonObject()) {
            JsonObject sanitized = new JsonObject();
            for (Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                sanitized.add(entry.getKey(), sanitizeJsonElement(entry.getValue(), entry.getKey()));
            }
            return sanitized;
        }
        if (element.isJsonArray()) {
            JsonArray sanitized = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                sanitized.add(sanitizeJsonElement(item, key));
            }
            return sanitized;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            int maxLength = isVerboseKey(key) ? MAX_DETAILS_STRING_LENGTH : MAX_API_MESSAGE_LENGTH;
            return new JsonPrimitive(sanitizeApiString(element.getAsString(), maxLength));
        }
        return element;
    }

    private boolean isSensitiveKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String lower = StringUtils.lowerCase(key);
        return StringUtils.containsAny(lower, "password", "secret", "token", "apikey", "api_key", "credential");
    }

    private boolean isVerboseKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String lower = StringUtils.lowerCase(key);
        return StringUtils.equalsAny(lower, "output", "stdout", "stderr", "commandoutput", "rawoutput", "details");
    }

    private String sanitizeApiString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
        return StringUtils.abbreviate(StringUtils.trimToNull(sanitized), Math.max(4, maxLength));
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

    private JsonObject parseHealthCheck(String capabilitiesJson) {
        if (StringUtils.isBlank(capabilitiesJson)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(capabilitiesJson);
            if (parsed == null || !parsed.isJsonObject()) {
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement healthCheck = root.get("healthCheck");
            return healthCheck != null && healthCheck.isJsonObject() ? healthCheck.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private Long getLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
