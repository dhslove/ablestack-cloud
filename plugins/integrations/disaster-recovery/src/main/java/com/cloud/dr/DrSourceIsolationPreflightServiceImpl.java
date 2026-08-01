// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine.PowerState;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrSourceIsolationPreflightServiceImpl extends ManagerBase
        implements DrSourceIsolationPreflightService {
    private static final int STEP_ORDER = 12;
    private static final Gson GSON = new Gson();

    @Inject private DrCutoverSessionDao drCutoverSessionDao;
    @Inject private DrReplicaDao drReplicaDao;
    @Inject private DrRunStepDao drRunStepDao;
    @Inject private UserVmDao userVmDao;
    @Inject private AgentManager agentManager;

    @Override
    public DrSourceIsolationPreflightResult validate(DrPlanVO plan, DrRunVO run, String operation) {
        DrSourceIsolationPreflightResult result = validateInternal(plan, operation);
        recordStep(run, result);
        return result;
    }

    private DrSourceIsolationPreflightResult validateInternal(DrPlanVO plan, String operation) {
        String normalizedOperation = StringUtils.upperCase(operation, Locale.ROOT);
        if (plan == null || !StringUtils.equalsAny(normalizedOperation,
                DrConstants.RUN_TYPE_FAILBACK, DrConstants.RUN_TYPE_REPROTECT)) {
            return failure(DrConstants.ERROR_TRANSITION_PREFLIGHT_INVALID,
                    "A valid FTCTL_DR failback or reprotect preflight is required",
                    normalizedOperation, null, null, null, null, null);
        }
        if (!StringUtils.equals(plan.getState(), DrConstants.PLAN_STATE_FAILED_OVER)
                || !StringUtils.equalsIgnoreCase(plan.getActiveSide(), DrConstants.AUTHORITY_SIDE_TARGET)) {
            return failure(DrConstants.ERROR_TRANSITION_AUTHORITY_INVALID,
                    "Transition requires committed TARGET authority", normalizedOperation,
                    null, null, null, null, null);
        }

        DrCutoverSessionVO cutover = drCutoverSessionDao.findLatestActiveByPlanId(plan.getId());
        if (cutover == null || cutover.getCloudAuthorityGeneration() == null
                || cutover.getCloudAuthorityGeneration() <= 0
                || !StringUtils.equalsIgnoreCase(cutover.getCloudPromotionState(), "PROMOTED")
                || !StringUtils.equalsIgnoreCase(cutover.getEngineAckState(), "ACKNOWLEDGED")) {
            return failure(DrConstants.ERROR_TRANSITION_AUTHORITY_INVALID,
                    "Committed cutover authority evidence is incomplete", normalizedOperation,
                    cutover != null ? cutover.getCloudAuthorityGeneration() : null,
                    cutover != null ? cutover.getSourceFenceState() : null,
                    cutover != null ? cutover.getSourcePowerState() : null, null, null);
        }

        String sourceFenceState = cutover.getSourceFenceState();
        String sourcePowerState = cutover.getSourcePowerState();
        boolean sourcePowerOff = StringUtils.equalsIgnoreCase(sourcePowerState, "POWERED_OFF");
        boolean sourceFenced = StringUtils.equalsAnyIgnoreCase(sourceFenceState,
                "ACKNOWLEDGED", "CONFIRMED", "FENCED", "ISOLATED", "BLOCKED");
        if (!sourcePowerOff && !sourceFenced) {
            return failure(DrConstants.ERROR_SOURCE_ISOLATION_NOT_READY,
                    "Source site is not proven powered off or isolated", normalizedOperation,
                    cutover.getCloudAuthorityGeneration(), sourceFenceState, sourcePowerState,
                    null, null);
        }

        DrReplicaVO replica = servingTargetReplica(plan.getId());
        UserVmVO targetVm = replica != null && replica.getTargetVmId() != null
                ? userVmDao.findById(replica.getTargetVmId()) : null;
        if (targetVm == null || targetVm.getRemoved() != null || targetVm.getHostId() == null) {
            return failure(DrConstants.ERROR_TRANSITION_TARGET_NOT_SERVING,
                    "Serving target VM identity or host assignment is missing", normalizedOperation,
                    cutover.getCloudAuthorityGeneration(), sourceFenceState, sourcePowerState,
                    null, null);
        }
        Answer powerProbe = agentManager.easySend(targetVm.getHostId(),
                new CheckVirtualMachineCommand(targetVm.getInstanceName()));
        if (!(powerProbe instanceof CheckVirtualMachineAnswer) || !powerProbe.getResult()
                || ((CheckVirtualMachineAnswer) powerProbe).getState() != PowerState.PowerOn) {
            return failure(DrConstants.ERROR_TRANSITION_TARGET_NOT_SERVING,
                    "Serving target VM is not powered on according to its host Agent",
                    normalizedOperation, cutover.getCloudAuthorityGeneration(), sourceFenceState,
                    sourcePowerState, "UNKNOWN", null);
        }

        Long coordinatorHostId = firstNonNull(plan.getCoordinatorWorkerHostId(),
                plan.getSourceWorkerHostId(), plan.getTargetWorkerHostId());
        if (coordinatorHostId == null) {
            return failure(DrConstants.ERROR_TRANSITION_ENGINE_PREFLIGHT_FAILED,
                    "FTCTL_DR coordinator host is not configured", normalizedOperation,
                    cutover.getCloudAuthorityGeneration(), sourceFenceState, sourcePowerState,
                    "POWERED_ON", null);
        }
        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(), null,
                FtctlDrStatusCommand.StatusScope.TRANSITION_PREFLIGHT);
        command.setTransitionOperation(normalizedOperation.toLowerCase(Locale.ROOT));
        command.setExpectedAuthoritySide(DrConstants.AUTHORITY_SIDE_TARGET);
        command.setExpectedAuthorityGeneration(cutover.getCloudAuthorityGeneration());
        Answer engineProbe = agentManager.easySend(coordinatorHostId, command);
        String evidenceJson = engineProbe instanceof FtctlDrStatusAnswer
                ? ((FtctlDrStatusAnswer) engineProbe).getStatusJson() : null;
        if (!(engineProbe instanceof FtctlDrStatusAnswer) || !engineProbe.getResult()
                || !engineReady(evidenceJson)) {
            return failure(DrConstants.ERROR_TRANSITION_ENGINE_PREFLIGHT_FAILED,
                    engineProbe != null ? engineProbe.getDetails()
                            : "FTCTL_DR transition preflight returned no answer",
                    normalizedOperation, cutover.getCloudAuthorityGeneration(), sourceFenceState,
                    sourcePowerState, "POWERED_ON", evidenceJson);
        }
        return DrSourceIsolationPreflightResult.success(normalizedOperation,
                cutover.getCloudAuthorityGeneration(), sourceFenceState, sourcePowerState,
                "POWERED_ON", evidenceJson);
    }

    private boolean engineReady(String statusJson) {
        if (StringUtils.isBlank(statusJson)) {
            return false;
        }
        try {
            JsonObject payload = JsonParser.parseString(statusJson).getAsJsonObject();
            return payload.has("ready") && payload.get("ready").getAsBoolean()
                    && StringUtils.equalsIgnoreCase(
                            payload.has("active_side") ? payload.get("active_side").getAsString() : null,
                            DrConstants.AUTHORITY_SIDE_TARGET);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private DrReplicaVO servingTargetReplica(long planId) {
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(planId);
        if (replicas == null) {
            return null;
        }
        for (DrReplicaVO replica : replicas) {
            if (replica != null && replica.getTargetVmId() != null
                    && StringUtils.equalsIgnoreCase(replica.getActiveSide(),
                            DrConstants.AUTHORITY_SIDE_TARGET)) {
                return replica;
            }
        }
        return null;
    }

    private Long firstNonNull(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private DrSourceIsolationPreflightResult failure(String errorCode, String message,
            String operation, Long generation, String fenceState, String sourcePowerState,
            String targetPowerState, String engineEvidenceJson) {
        return DrSourceIsolationPreflightResult.failure(errorCode, message, operation, generation,
                fenceState, sourcePowerState, targetPowerState, engineEvidenceJson);
    }

    private void recordStep(DrRunVO run, DrSourceIsolationPreflightResult result) {
        if (run == null || drRunStepDao == null) {
            return;
        }
        Date now = new Date();
        DrRunStepVO step = drRunStepDao.findActiveByRunIdAndStepOrder(run.getId(), STEP_ORDER);
        if (step == null) {
            step = new DrRunStepVO(run.getId(), "source-isolation-preflight", STEP_ORDER);
            step.setStarted(now);
        }
        step.setState(result.isReady() ? DrConstants.STEP_STATE_SUCCEEDED
                : DrConstants.STEP_STATE_FAILED);
        step.setProgress(100);
        step.setCompleted(now);
        step.setDetailsJson(GSON.toJson(result));
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
