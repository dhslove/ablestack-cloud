// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.FtctlDrCancelAnswer;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.orchestrator.DrOrchestrator;

@RunWith(MockitoJUnitRunner.class)
public class DrRunServiceImplTest {
    @Mock private DrRunDao drRunDao;
    @Mock private DrRunStepDao drRunStepDao;
    @Mock private DrOrchestrator drOrchestrator;
    @Mock private DrPlanDao drPlanDao;
    @Mock private AgentManager agentManager;
    @InjectMocks private DrRunServiceImpl service;

    @Test
    public void activeRunCancellationIsRequeuedForTerminalization() {
        DrRunVO active = new DrRunVO(10L, DrConstants.RUN_TYPE_TEST_FAILOVER);
        active.setState(DrConstants.RUN_STATE_RUNNING);
        Mockito.when(drRunDao.findById(active.getId())).thenReturn(active);

        DrRunVO result = service.cancelRun(active.getId());

        Assert.assertEquals(DrConstants.RUN_STATE_CANCEL_REQUESTED, result.getState());
        Mockito.verify(drOrchestrator).executeRun(active.getId());
    }

    @Test
    public void queuedRunCancellationDoesNotRequireExecutorRedispatch() {
        DrRunVO queued = new DrRunVO(10L, DrConstants.RUN_TYPE_TEST_FAILOVER);
        queued.setState(DrConstants.RUN_STATE_QUEUED);
        Mockito.when(drRunDao.findById(queued.getId())).thenReturn(queued);

        DrRunVO result = service.cancelRun(queued.getId());

        Assert.assertEquals(DrConstants.RUN_STATE_CANCELED, result.getState());
        Assert.assertNotNull(result.getCompleted());
        Mockito.verify(drOrchestrator, Mockito.never()).executeRun(queued.getId());
    }

    @Test
    public void activeFtctlRunIsTerminalizedOnlyAfterEngineAcceptsCancellation() {
        DrRunVO active = new DrRunVO(10L, DrConstants.RUN_TYPE_SYNC);
        active.setState(DrConstants.RUN_STATE_RUNNING);
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        Mockito.when(plan.getUuid()).thenReturn("plan-uuid");
        Mockito.when(plan.getEngineType()).thenReturn(DrConstants.ENGINE_TYPE_FTCTL_DR);
        Mockito.when(plan.getCoordinatorWorkerHostId()).thenReturn(22L);
        Mockito.when(drRunDao.findById(active.getId())).thenReturn(active);
        Mockito.when(drPlanDao.findById(10L)).thenReturn(plan);
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrCancelCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrCancelCommand command = invocation.getArgument(1);
                    return new FtctlDrCancelAnswer(command, true, "accepted", "plan-uuid", active.getUuid(),
                            "canceled", true, null, 0,
                            "{\"state\":\"CANCELED\",\"terminal_authoritative\":true,"
                                    + "\"runtime_endpoints_drained\":true,\"transfer_activity_state\":\"CANCELED\"}");
                });

        DrRunVO result = service.cancelRun(active.getId());

        Assert.assertEquals(DrConstants.RUN_STATE_CANCEL_REQUESTED, result.getState());
        Mockito.verify(drOrchestrator).executeRun(active.getId());
    }

    @Test
    public void acceptedFtctlCancellationWithoutTerminalEvidenceRemainsRequested() {
        DrRunVO active = new DrRunVO(10L, DrConstants.RUN_TYPE_SYNC);
        active.setState(DrConstants.RUN_STATE_RUNNING);
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        Mockito.when(plan.getUuid()).thenReturn("plan-uuid");
        Mockito.when(plan.getEngineType()).thenReturn(DrConstants.ENGINE_TYPE_FTCTL_DR);
        Mockito.when(plan.getCoordinatorWorkerHostId()).thenReturn(22L);
        Mockito.when(drRunDao.findById(active.getId())).thenReturn(active);
        Mockito.when(drPlanDao.findById(10L)).thenReturn(plan);
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrCancelCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrCancelCommand command = invocation.getArgument(1);
                    return new FtctlDrCancelAnswer(command, true, "accepted", "plan-uuid", active.getUuid(),
                            "canceled", true, null, 0, "{\"state\":\"CANCEL_REQUESTED\"}");
                });

        DrRunVO result = service.cancelRun(active.getId());

        Assert.assertEquals(DrConstants.RUN_STATE_CANCEL_REQUESTED, result.getState());
        Mockito.verify(drOrchestrator, Mockito.never()).executeRun(active.getId());
    }

    @Test
    public void rejectedFtctlCancellationRemainsRequestedAndIsNotTerminalized() {
        DrRunVO active = new DrRunVO(10L, DrConstants.RUN_TYPE_SYNC);
        active.setState(DrConstants.RUN_STATE_RUNNING);
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        Mockito.when(plan.getUuid()).thenReturn("plan-uuid");
        Mockito.when(plan.getEngineType()).thenReturn(DrConstants.ENGINE_TYPE_FTCTL_DR);
        Mockito.when(plan.getCoordinatorWorkerHostId()).thenReturn(22L);
        Mockito.when(drRunDao.findById(active.getId())).thenReturn(active);
        Mockito.when(drPlanDao.findById(10L)).thenReturn(plan);
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrCancelCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrCancelCommand command = invocation.getArgument(1);
                    return new FtctlDrCancelAnswer(command, false, "rejected", "plan-uuid", active.getUuid(),
                            "rejected", false, "DR_CANCEL_REJECTED", 1, "{}");
                });

        DrRunVO result = service.cancelRun(active.getId());

        Assert.assertEquals(DrConstants.RUN_STATE_CANCEL_REQUESTED, result.getState());
        Mockito.verify(drOrchestrator, Mockito.never()).executeRun(active.getId());
    }
}
