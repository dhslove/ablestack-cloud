// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.vm.VirtualMachine.PowerState;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;

@RunWith(MockitoJUnitRunner.class)
public class DrReprotectPreflightServiceImplTest {
    @Mock private DrCutoverSessionDao drCutoverSessionDao;
    @Mock private DrPlanRuntimeDao drPlanRuntimeDao;
    @Mock private DrReplicaDao drReplicaDao;
    @Mock private DrRestorePointDao drRestorePointDao;
    @Mock private DrRunStepDao drRunStepDao;
    @Mock private DrSyncCycleDao drSyncCycleDao;
    @Mock private UserVmDao userVmDao;
    @Mock private AgentManager agentManager;
    @Mock private DrSourceIsolationPreflightService drSourceIsolationPreflightService;

    @InjectMocks
    private DrReprotectPreflightServiceImpl service;

    @Test
    public void validatesCommittedTargetAuthorityAndAgentPowerState() {
        Fixture fixture = fixture();
        Mockito.when(agentManager.easySend(Mockito.eq(102L), Mockito.any(CheckVirtualMachineCommand.class)))
                .thenAnswer(invocation -> new CheckVirtualMachineAnswer(
                        invocation.getArgument(1), PowerState.PowerOn, null));
        Mockito.when(drSourceIsolationPreflightService.validate(fixture.plan, fixture.run,
                DrConstants.RUN_TYPE_REPROTECT))
                .thenReturn(DrSourceIsolationPreflightResult.success(
                        DrConstants.RUN_TYPE_REPROTECT, 3L, "ACKNOWLEDGED",
                        "POWERED_OFF", "POWERED_ON", "{\"ready\":true}"));

        DrReprotectPreflightResult result = service.validate(fixture.plan, fixture.run);

        Assert.assertTrue(result.isReady());
        Assert.assertEquals("TARGET", result.getAuthoritySpec().getExpectedActiveSide());
        Assert.assertEquals(3L, result.getAuthoritySpec().getAuthorityGeneration());
        Assert.assertEquals(153L, result.getAuthoritySpec().getAuthoritySequenceFloor());
        Assert.assertEquals(17L, result.getAuthoritySpec().getCheckpointSequence());
        Assert.assertEquals(256L, result.getAuthoritySpec().getTargetVmId());
        Mockito.verify(drRunStepDao).persist(Mockito.argThat(step ->
                DrConstants.STEP_STATE_SUCCEEDED.equals(step.getState())
                        && step.getDetailsJson().contains("\"targetInstanceName\":\"i-2-256-VM\"")));
    }

    @Test
    public void rejectsDbRunningTargetWhenAgentReportsPowerOff() {
        Fixture fixture = fixture();
        Mockito.when(agentManager.easySend(Mockito.eq(102L), Mockito.any(CheckVirtualMachineCommand.class)))
                .thenAnswer(invocation -> new CheckVirtualMachineAnswer(
                        invocation.getArgument(1), PowerState.PowerOff, null));

        DrReprotectPreflightResult result = service.validate(fixture.plan, fixture.run);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING, result.getErrorCode());
        Mockito.verify(drRunStepDao).persist(Mockito.argThat(step ->
                DrConstants.STEP_STATE_FAILED.equals(step.getState())
                        && DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING.equals(step.getErrorCode())));
    }

    private Fixture fixture() {
        DrPlanVO plan = new DrPlanVO("reprotect-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_REPROTECT);
        DrCutoverSessionVO cutover = new DrCutoverSessionVO(plan.getId(), 11L, "planned", "PROMOTED");
        cutover.setCheckpointSequence(17L);
        cutover.setCloudPromotionState("PROMOTED");
        cutover.setEngineAckState("ACKNOWLEDGED");
        cutover.setCloudAuthorityGeneration(3L);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setTargetExternalRef("target-vm-uuid");
        replica.setActiveSide("TARGET");
        DrRestorePointVO checkpoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
        checkpoint.setCheckpointSequence(17L);
        UserVmVO targetVm = Mockito.mock(UserVmVO.class);
        Mockito.when(targetVm.getId()).thenReturn(256L);
        Mockito.when(targetVm.getUuid()).thenReturn("target-vm-uuid");
        Mockito.when(targetVm.getInstanceName()).thenReturn("i-2-256-VM");
        Mockito.when(targetVm.getHostId()).thenReturn(102L);

        Mockito.when(drCutoverSessionDao.findLatestActiveByPlanId(plan.getId())).thenReturn(cutover);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(checkpoint);
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());
        runtime.setAuthoritySequence(41L);
        DrSyncCycleVO latestCompleted = new DrSyncCycleVO(plan.getId(), "cycle-run", 61L);
        latestCompleted.setAuthoritySequence(153L);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drSyncCycleDao.findLatestCompletedByPlanId(plan.getId())).thenReturn(latestCompleted);
        Mockito.when(userVmDao.findById(256L)).thenReturn(targetVm);
        return new Fixture(plan, run);
    }

    private static class Fixture {
        private final DrPlanVO plan;
        private final DrRunVO run;

        Fixture(DrPlanVO plan, DrRunVO run) {
            this.plan = plan;
            this.run = run;
        }
    }
}
