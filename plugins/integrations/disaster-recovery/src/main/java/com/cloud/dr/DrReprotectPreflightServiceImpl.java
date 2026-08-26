// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.vm.VirtualMachine.PowerState;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.Gson;

public class DrReprotectPreflightServiceImpl extends ManagerBase implements DrReprotectPreflightService {
    private static final int STEP_ORDER_REPROTECT_PREFLIGHT = 15;
    private static final Gson GSON = new Gson();

    @Inject private DrCutoverSessionDao drCutoverSessionDao;
    @Inject private DrPlanRuntimeDao drPlanRuntimeDao;
    @Inject private DrReplicaDao drReplicaDao;
    @Inject private DrRestorePointDao drRestorePointDao;
    @Inject private DrRunStepDao drRunStepDao;
    @Inject private DrSyncCycleDao drSyncCycleDao;
    @Inject private UserVmDao userVmDao;
    @Inject private AgentManager agentManager;
    @Inject private DrSourceIsolationPreflightService drSourceIsolationPreflightService;

    @Override
    public DrReprotectPreflightResult validate(DrPlanVO plan, DrRunVO run) {
        DrReprotectPreflightResult result = validateAuthorityAndRuntime(plan, run);
        recordStep(run, result);
        return result;
    }

    private DrReprotectPreflightResult validateAuthorityAndRuntime(DrPlanVO plan, DrRunVO run) {
        if (plan == null || run == null) {
            return failure(DrConstants.ERROR_REPROTECT_AUTHORITY_INVALID,
                    "DR plan and Reprotect run are required");
        }
        if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_FAILED_OVER)
                || !StringUtils.equalsIgnoreCase(plan.getActiveSide(), "TARGET")) {
            return failure(DrConstants.ERROR_REPROTECT_REQUIRES_TARGET_ACTIVE,
                    "Reprotect requires a failed-over plan with TARGET authority");
        }

        DrCutoverSessionVO cutover = drCutoverSessionDao.findLatestActiveByPlanId(plan.getId());
        if (cutover == null
                || !StringUtils.equalsAnyIgnoreCase(cutover.getState(), "PROMOTED", "COMPLETED", "FAILED_OVER")
                || !StringUtils.equalsIgnoreCase(cutover.getCloudPromotionState(), "PROMOTED")
                || !StringUtils.equalsIgnoreCase(cutover.getEngineAckState(), "ACKNOWLEDGED")
                || cutover.getCloudAuthorityGeneration() == null || cutover.getCloudAuthorityGeneration() <= 0) {
            return failure(DrConstants.ERROR_REPROTECT_CUTOVER_NOT_COMMITTED,
                    "Committed and acknowledged target promotion was not found");
        }

        DrReplicaVO replica = servingTargetReplica(plan.getId());
        if (replica == null || replica.getTargetVmId() == null
                || !StringUtils.equalsIgnoreCase(replica.getActiveSide(), "TARGET")) {
            return failure(DrConstants.ERROR_REPROTECT_TARGET_IDENTITY_INVALID,
                    "Serving target replica identity is missing or does not own TARGET authority");
        }
        UserVmVO targetVm = userVmDao.findById(replica.getTargetVmId());
        if (targetVm == null || targetVm.getRemoved() != null || targetVm.getHostId() == null) {
            return failure(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING,
                    "Serving target VM is missing or is not assigned to a host");
        }

        DrRestorePointVO checkpoint = drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId());
        if (checkpoint == null || checkpoint.getCheckpointSequence() == null
                || cutover.getCheckpointSequence() == null
                || !checkpoint.getCheckpointSequence().equals(cutover.getCheckpointSequence())) {
            return failure(DrConstants.ERROR_REPROTECT_CHECKPOINT_MISMATCH,
                    "Latest durable checkpoint does not match the committed cutover checkpoint");
        }

        Answer answer = agentManager.easySend(targetVm.getHostId(),
                new CheckVirtualMachineCommand(targetVm.getInstanceName()));
        if (!(answer instanceof CheckVirtualMachineAnswer) || !answer.getResult()) {
            return failure(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_UNKNOWN,
                    "Target VM power state could not be verified through its host Agent");
        }
        CheckVirtualMachineAnswer powerAnswer = (CheckVirtualMachineAnswer) answer;
        if (powerAnswer.getState() != PowerState.PowerOn) {
            return failure(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING,
                    "Target VM is not powered on according to its host Agent");
        }

        DrSourceIsolationPreflightResult transitionPreflight =
                drSourceIsolationPreflightService.validate(plan, run, DrConstants.RUN_TYPE_REPROTECT);
        if (!transitionPreflight.isReady()) {
            return failure(transitionPreflight.getErrorCode(), transitionPreflight.getMessage());
        }

        DrReprotectAuthoritySpec spec = new DrReprotectAuthoritySpec();
        spec.setPlanUuid(plan.getUuid());
        spec.setRunUuid(run.getUuid());
        spec.setExpectedActiveSide("TARGET");
        spec.setAuthorityGeneration(cutover.getCloudAuthorityGeneration());
        spec.setAuthoritySequenceFloor(resolveAuthoritySequenceFloor(plan,
                cutover.getCloudAuthorityGeneration()));
        spec.setCutoverSessionId(cutover.getUuid());
        spec.setCheckpointSequence(cutover.getCheckpointSequence());
        spec.setTargetVmId(targetVm.getId());
        spec.setTargetExternalRef(StringUtils.defaultIfBlank(replica.getTargetExternalRef(), targetVm.getUuid()));
        spec.setTargetInstanceName(targetVm.getInstanceName());
        spec.setTargetPowerState("POWERED_ON");
        spec.setTargetMaterialized(true);
        spec.setTargetPromotionState(cutover.getCloudPromotionState());
        spec.setBootValidationState(cutover.getBootValidationState());
        spec.setSourceFenceState(cutover.getSourceFenceState());
        spec.setSourcePowerState(cutover.getSourcePowerState());
        return DrReprotectPreflightResult.success(spec);
    }

    private long resolveAuthoritySequenceFloor(DrPlanVO plan, long authorityGeneration) {
        long floor = authorityGeneration;
        DrPlanRuntimeVO runtime = drPlanRuntimeDao.findByPlanId(plan.getId());
        if (runtime != null) {
            floor = Math.max(floor, runtime.getAuthoritySequence());
        }
        DrSyncCycleVO latestCompleted = drSyncCycleDao.findLatestCompletedByPlanId(plan.getId());
        if (latestCompleted != null && latestCompleted.getAuthoritySequence() != null) {
            floor = Math.max(floor, latestCompleted.getAuthoritySequence());
        }
        return floor;
    }

    private DrReplicaVO servingTargetReplica(long planId) {
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(planId);
        if (replicas == null) {
            return null;
        }
        for (DrReplicaVO replica : replicas) {
            if (replica != null && replica.getTargetVmId() != null
                    && StringUtils.equalsIgnoreCase(replica.getActiveSide(), "TARGET")) {
                return replica;
            }
        }
        return null;
    }

    private DrReprotectPreflightResult failure(String errorCode, String message) {
        return DrReprotectPreflightResult.failure(errorCode, message);
    }

    private void recordStep(DrRunVO run, DrReprotectPreflightResult result) {
        if (run == null || drRunStepDao == null) {
            return;
        }
        Date now = new Date();
        DrRunStepVO step = drRunStepDao.findActiveByRunIdAndStepOrder(run.getId(),
                STEP_ORDER_REPROTECT_PREFLIGHT);
        if (step == null) {
            step = new DrRunStepVO(run.getId(), "reprotect-preflight",
                    STEP_ORDER_REPROTECT_PREFLIGHT);
            step.setStarted(now);
        }
        step.setState(result.isReady() ? DrConstants.STEP_STATE_SUCCEEDED : DrConstants.STEP_STATE_FAILED);
        step.setProgress(100);
        step.setCompleted(now);
        step.setDetailsJson(result.getAuthoritySpec() != null
                ? GSON.toJson(result.getAuthoritySpec()) : null);
        step.setErrorCode(result.getErrorCode());
        step.setErrorMessage(result.getMessage());
        step.markUpdated();
        if (step.getId() == 0) {
            drRunStepDao.persist(step);
        } else {
            drRunStepDao.update(step.getId(), step);
        }
    }
}
