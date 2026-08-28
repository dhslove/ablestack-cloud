// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.dr.adapter.ftctl.DrRemoteAgentClient;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.google.gson.JsonArray;

@RunWith(MockitoJUnitRunner.class)
public class DrPlanOwnedTransportServiceImplTest {
    @Mock private AgentManager agentManager;
    @Mock private HostDao hostDao;
    @Mock private DrRemoteAgentClient drRemoteAgentClient;

    @InjectMocks
    private DrPlanOwnedTransportServiceImpl service;

    private DrPlanVO plan;
    private DrRunVO run;
    private HostVO targetHost;

    @Before
    public void setUp() {
        plan = new DrPlanVO("rbd-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setSourceExternalRef("remote-source-vm");
        plan.setTargetWorkerHostId(22L);
        run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        targetHost = Mockito.mock(HostVO.class);
        Mockito.when(targetHost.getId()).thenReturn(22L);
        Mockito.when(targetHost.getUuid()).thenReturn("target-worker-uuid");
        Mockito.when(hostDao.findById(22L)).thenReturn(targetHost);
        Mockito.when(drRemoteAgentClient.isRemoteKvmSource(plan)).thenReturn(true);
    }

    @Test
    public void forwardExportUsesTargetWorkerAndReturnsEndpoints() {
        FtctlDrActionAnswer answer = answer("{\"result\":\"ok\",\"exports\":[{\"device\":\"sda\",\"port\":11833}]}");
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrActionCommand.class)))
                .thenReturn(answer);

        JsonArray exports = service.startForwardTargetExport(plan, run, null);

        Assert.assertEquals(1, exports.size());
        ArgumentCaptor<FtctlDrActionCommand> command = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(22L), command.capture());
        Assert.assertEquals(FtctlDrActionCommand.Action.TARGET_EXPORT_START, command.getValue().getAction());
        Assert.assertEquals("target", command.getValue().getRole());
        Assert.assertEquals("target-worker-uuid", command.getValue().getTargetWorkerUuid());
    }

    @Test
    public void reverseExportUsesOriginalSiteWorkerAndAuxiliaryRole() {
        Mockito.when(drRemoteAgentClient.sourceWorkerUuid(plan)).thenReturn("source-worker-uuid");
        FtctlDrActionAnswer answer = answer(
                "{\"result\":\"ok\",\"exports\":[{\"device\":\"sda\",\"port\":11834}]}");
        Mockito.when(drRemoteAgentClient.execute(Mockito.eq(plan), Mockito.eq("ACTION"),
                Mockito.any(FtctlDrActionCommand.class), Mockito.eq("source-worker-uuid"),
                Mockito.eq(FtctlDrActionAnswer.class)))
                .thenReturn(answer);

        JsonArray exports = service.startReverseTargetExport(plan, run, "{\"request\":{}}");

        Assert.assertEquals(1, exports.size());
        Mockito.verify(drRemoteAgentClient).execute(Mockito.eq(plan), Mockito.eq("ACTION"),
                Mockito.argThat((FtctlDrActionCommand command) -> "reverse-target".equals(command.getRole())
                        && command.getProfileJson().contains("reverseTargetExport")),
                Mockito.eq("source-worker-uuid"), Mockito.eq(FtctlDrActionAnswer.class));
    }

    @Test
    public void testFailoverDrainDoesNotRequestReverseCutoverBaseline() {
        run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_TEST_FAILOVER);
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrActionCommand.class)))
                .thenReturn(answer("{\"result\":\"ok\"}"));

        service.stopForwardTargetExport(plan, run, "{\"request\":{\"actionIntent\":\"TEST_FAILOVER\"}}", 253L);

        ArgumentCaptor<FtctlDrActionCommand> command = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(22L), command.capture());
        Assert.assertNull(command.getValue().getCutoverCheckpointSequence());
    }

    @Test
    public void failoverDrainPreservesReverseCutoverBaselineSequence() {
        run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILOVER);
        Mockito.when(agentManager.easySend(Mockito.eq(22L), Mockito.any(FtctlDrActionCommand.class)))
                .thenReturn(answer("{\"result\":\"ok\"}"));

        service.stopForwardTargetExport(plan, run, null, 253L);

        ArgumentCaptor<FtctlDrActionCommand> command = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(22L), command.capture());
        Assert.assertEquals(Long.valueOf(253L), command.getValue().getCutoverCheckpointSequence());
    }

    @Test
    public void unsupportedRouteDoesNotDispatchTransport() {
        plan = new DrPlanVO("vmware-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);

        Assert.assertEquals(0, service.startForwardTargetExport(plan, run, null).size());
        Mockito.verifyNoInteractions(agentManager);
    }

    private FtctlDrActionAnswer answer(String statusJson) {
        FtctlDrActionAnswer answer = Mockito.mock(FtctlDrActionAnswer.class);
        Mockito.when(answer.getResult()).thenReturn(true);
        Mockito.when(answer.getStatusJson()).thenReturn(statusJson);
        return answer;
    }
}
