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
package com.cloud.dr.adapter.ftctl;

import org.junit.Assert;
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
import com.cloud.agent.api.FtctlDrCapabilitiesAnswer;
import com.cloud.agent.api.FtctlDrCapabilitiesCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrFailbackPreflightResult;
import com.cloud.dr.DrFailbackPreflightService;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrPlanOwnedTransportService;
import com.cloud.dr.DrReprotectAuthoritySpec;
import com.cloud.dr.DrReprotectPreflightResult;
import com.cloud.dr.DrReprotectPreflightService;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrExecutionContext;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.host.dao.HostDao;
import com.cloud.host.HostVO;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

@RunWith(MockitoJUnitRunner.class)
public class FtctlDrUnifiedActionAdapterTest {

    @Test
    public void automaticVmwareThumbprintsAreRefreshableButOperatorPinsAreNot() {
        Assert.assertTrue(FtctlDrUnifiedActionAdapter.shouldRefreshAutoThumbprint("backend-auto"));
        Assert.assertTrue(FtctlDrUnifiedActionAdapter.shouldRefreshAutoThumbprint("backend-auto-refreshed"));
        Assert.assertTrue(FtctlDrUnifiedActionAdapter.shouldRefreshAutoThumbprint("backend-auto-fallback"));
        Assert.assertFalse(FtctlDrUnifiedActionAdapter.shouldRefreshAutoThumbprint("runtime"));
        Assert.assertFalse(FtctlDrUnifiedActionAdapter.shouldRefreshAutoThumbprint(null));
    }

    @Mock
    private AgentManager agentManager;
    @Mock
    private HostDao hostDao;
    @Mock
    private DrRestorePointDao drRestorePointDao;
    @Mock
    private DrReprotectPreflightService drReprotectPreflightService;
    @Mock
    private DrFailbackPreflightService drFailbackPreflightService;
    @Mock
    private DrRemoteAgentClient drRemoteAgentClient;
    @Mock
    private DrPlanOwnedTransportService drPlanOwnedTransportService;

    @InjectMocks
    private FtctlDrUnifiedActionAdapter adapter;

    @Test
    public void syncDispatchesToCoordinatorWorkerAndReturnsAcceptedRun() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_SYNC,
                "{\"mode\":\"FULL_RESEED\",\"forceImmediateCycle\":true,\"remoteMoldSecretKey\":\"top-secret\",\"dryRun\":false}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        mockCapabilities();
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.SYNC,
                    plan.getUuid(), run.getUuid(), "accepted", true, "SYNCING", "dispatch",
                    1, "ftctl-job-1", 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"remoteMoldSecretKey\":\"top-secret\",\"state\":\"SYNCING\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        Assert.assertEquals("ftctl-job-1", result.getExternalJobRef());
        Mockito.verify(agentManager).send(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class));

        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.SYNC, command.getAction());
        Assert.assertEquals(DrConstants.RUN_TYPE_SYNC, command.getRunType());
        Assert.assertEquals(DrConstants.DIRECTION_VMWARE_TO_KVM, command.getDirection());
        Assert.assertEquals("coordinator", command.getRole());
        Assert.assertEquals("101", command.getSourceWorkerUuid());
        Assert.assertEquals("102", command.getTargetWorkerUuid());
        Assert.assertEquals("103", command.getCoordinatorWorkerUuid());
        Assert.assertFalse(command.isWaitForCompletion());
        Assert.assertEquals("FULL_RESEED", command.getMode());
        Assert.assertTrue(command.isForceImmediateCycle());
        Assert.assertTrue(command.getProfileJson().contains("\"engine\":\"FTCTL_DR\""));
        Assert.assertFalse(command.getProfileJson().contains("top-secret"));
        Assert.assertFalse(command.getRequestJson().contains("top-secret"));
        Assert.assertEquals("REDACTED", command.getContext().get("remoteMoldSecretKey"));
        Assert.assertFalse(result.getDetailsJson().contains("top-secret"));
    }

    @Test
    public void syncRejectsSemanticErrorEvenWhenAgentTransportSucceeded() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_SYNC, "{\"mode\":\"INCREMENTAL\"}");
        mockCapabilities();
        Mockito.when(agentManager.send(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class))).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "status query completed",
                    FtctlDrActionCommand.Action.SYNC, plan.getUuid(), run.getUuid(), "ok", false,
                    "ERROR", "scheduler-recovery-failed", 100, null, 0L,
                    "DR_RECOVERY_FAILED", 0, "{\"result\":\"ok\"}",
                    "{\"result\":\"ok\",\"accepted\":false,\"state\":\"ERROR\","
                            + "\"error_code\":\"DR_RECOVERY_FAILED\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.isTerminal());
        Assert.assertEquals("DR_RECOVERY_FAILED", result.getErrorCode());
    }

    @Test
    public void crossSiteKvmSyncDispatchesToRemoteSourceAndPreparesTargetWorker() throws Exception {
        DrPlanVO plan = new DrPlanVO("cross-site-kvm-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setSourceExternalRef("source-vm-uuid");
        plan.setActiveSide("SOURCE");
        plan.setTargetWorkerHostId(102L);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setMappingJson("{\"source\":{\"hardware\":{\"sourceHostUuid\":\"source-host-uuid\","
                + "\"instanceName\":\"i-2-332-VM\"}},\"target\":{\"storagePoolType\":\"RBD\"},"
                + "\"disks\":[{\"device\":\"sda\",\"sourcePath\":\"rbd:rbd/source-image\","
                + "\"targetPath\":\"rbd:rbd/target-image\",\"targetStorageRef\":\"target-pool-uuid\","
                + "\"target\":{\"storageRef\":\"target-pool-uuid\",\"path\":\"target-image\",\"format\":\"raw\"}}]}");
        DrRunVO run = run(DrConstants.RUN_TYPE_SYNC, "{\"mode\":\"FULL_RESEED\",\"forceImmediateCycle\":true}");
        HostVO targetHost = Mockito.mock(HostVO.class);
        Mockito.when(targetHost.getUuid()).thenReturn("target-host-uuid");
        Mockito.when(targetHost.getPrivateIpAddress()).thenReturn("10.10.32.2");
        Mockito.when(hostDao.findById(102L)).thenReturn(targetHost);
        Mockito.when(drRemoteAgentClient.isRemoteKvmSource(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.supports(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.startForwardTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString()))
                .thenReturn(exports("10.10.32.2", 12032, "dr-export-sda"));
        Mockito.when(drRemoteAgentClient.execute(Mockito.eq(plan), Mockito.eq("CAPABILITIES"),
                Mockito.isA(FtctlDrCapabilitiesCommand.class), Mockito.eq("source-host-uuid"),
                Mockito.eq(FtctlDrCapabilitiesAnswer.class))).thenAnswer(invocation -> {
                    FtctlDrCapabilitiesAnswer answer = new FtctlDrCapabilitiesAnswer(invocation.getArgument(2), true, "ok");
                    answer.setSupportedFeatures(java.util.Arrays.asList(
                            "control-protocol-v2", "dr-site-agent-rbd-transport-v1"));
                    return answer;
                });
        Mockito.when(drRemoteAgentClient.execute(Mockito.eq(plan), Mockito.eq("ACTION"),
                Mockito.isA(FtctlDrActionCommand.class), Mockito.eq("source-host-uuid"),
                Mockito.eq(FtctlDrActionAnswer.class))).thenAnswer(invocation -> {
                    FtctlDrActionCommand command = invocation.getArgument(2);
                    return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.SYNC,
                            plan.getUuid(), run.getUuid(), "accepted", true, "SYNCING", "dispatch",
                            1, "remote-ftctl-job", 0L, null, 0, "{\"result\":\"accepted\"}",
                            "{\"state\":\"SYNCING\"}");
                });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        Mockito.verify(drPlanOwnedTransportService).startForwardTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString());
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.anyLong(), Mockito.isA(FtctlDrActionCommand.class));
        ArgumentCaptor<FtctlDrActionCommand> actionCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(drRemoteAgentClient).execute(Mockito.eq(plan), Mockito.eq("ACTION"), actionCaptor.capture(),
                Mockito.eq("source-host-uuid"), Mockito.eq(FtctlDrActionAnswer.class));
        FtctlDrActionCommand command = actionCaptor.getValue();
        Assert.assertEquals("source-host-uuid", command.getSourceWorkerUuid());
        Assert.assertTrue(command.getProfileJson().contains("\"mode\":\"site-agent-nbd\""));
        Assert.assertTrue(command.getProfileJson().contains("\"targetHostAddress\":\"10.10.32.2\""));
        Assert.assertTrue(command.getProfileJson().contains("\"name\":\"dr-export-sda\""));
        Assert.assertTrue(command.getRequestJson().contains("\"schedulerTransitionScope\":\"REMOTE_SOURCE\""));
        Assert.assertFalse(command.getProfileJson().contains("sshUser"));
        Assert.assertFalse(command.getProfileJson().contains("moldSecretKey"));
    }

    @Test
    public void crossSiteKvmFailoverKeepsTargetExportAndDispatchesFinalDeltaToRemoteSource() throws Exception {
        DrPlanVO plan = new DrPlanVO("cross-site-kvm-failover", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setSourceExternalRef("source-vm-uuid");
        plan.setActiveSide("SOURCE");
        plan.setTargetWorkerHostId(102L);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setMappingJson("{\"source\":{\"hardware\":{\"sourceHostUuid\":\"source-host-uuid\","
                + "\"instanceName\":\"i-2-332-VM\"}},\"target\":{\"storagePoolType\":\"RBD\"},"
                + "\"disks\":[{\"device\":\"sda\",\"sourcePath\":\"rbd:rbd/source-image\","
                + "\"targetPath\":\"rbd:rbd/target-image\",\"targetStorageRef\":\"target-pool-uuid\","
                + "\"target\":{\"storageRef\":\"target-pool-uuid\",\"path\":\"target-image\",\"format\":\"raw\"}}]}");
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILOVER, "{\"mode\":\"planned\",\"finalSync\":true}");
        DrRestorePointVO checkpoint = checkpoint(plan, "ftctl:" + plan.getUuid() + ":source-run:12");
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(checkpoint);
        HostVO targetHost = Mockito.mock(HostVO.class);
        Mockito.when(targetHost.getUuid()).thenReturn("target-host-uuid");
        Mockito.when(targetHost.getPrivateIpAddress()).thenReturn("10.10.32.2");
        Mockito.when(hostDao.findById(102L)).thenReturn(targetHost);
        Mockito.when(drRemoteAgentClient.isRemoteKvmSource(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.supports(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.startForwardTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString()))
                .thenReturn(exports("10.10.32.2", 12032, "dr-export-sda"));
        Mockito.when(drRemoteAgentClient.execute(Mockito.eq(plan), Mockito.eq("CAPABILITIES"),
                Mockito.isA(FtctlDrCapabilitiesCommand.class), Mockito.eq("source-host-uuid"),
                Mockito.eq(FtctlDrCapabilitiesAnswer.class))).thenAnswer(invocation -> {
                    FtctlDrCapabilitiesAnswer answer = new FtctlDrCapabilitiesAnswer(invocation.getArgument(2), true, "ok");
                    answer.setSupportedFeatures(java.util.Arrays.asList(
                            "control-protocol-v2", "dr-site-agent-rbd-transport-v1"));
                    return answer;
                });
        ArgumentCaptor<FtctlDrActionCommand> sourceCommandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.when(drRemoteAgentClient.execute(Mockito.eq(plan), Mockito.eq("ACTION"), sourceCommandCaptor.capture(),
                Mockito.eq("source-host-uuid"), Mockito.eq(FtctlDrActionAnswer.class))).thenAnswer(invocation -> {
                    FtctlDrActionCommand command = invocation.getArgument(2);
                    return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.FAILOVER,
                            plan.getUuid(), run.getUuid(), "accepted", true, "RUNNING", "final-delta",
                            1, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}", "{\"state\":\"RUNNING\"}");
                });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Mockito.verify(drPlanOwnedTransportService).startForwardTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString());
        Assert.assertEquals(FtctlDrActionCommand.Action.FAILOVER, sourceCommandCaptor.getValue().getAction());
        Assert.assertTrue(sourceCommandCaptor.getValue().getProfileJson().contains("\"mode\":\"site-agent-nbd\""));
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.anyLong(), Mockito.isA(FtctlDrActionCommand.class));
    }

    @Test
    public void crossSiteKvmFailbackPreparesReverseExportOnOriginalSite() throws Exception {
        DrPlanVO plan = new DrPlanVO("cross-site-kvm-failback", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setSourceExternalRef("source-vm-uuid");
        plan.setActiveSide("TARGET");
        plan.setTargetWorkerHostId(102L);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setMappingJson("{\"source\":{\"hardware\":{\"sourceHostUuid\":\"source-host-uuid\","
                + "\"instanceName\":\"i-2-332-VM\"}},\"target\":{\"storagePoolType\":\"RBD\",\"storagePath\":\"rbd\"},"
                + "\"disks\":[{\"device\":\"sda\",\"sourcePath\":\"rbd:rbd/source-image\","
                + "\"targetPath\":\"target-image\",\"targetStorageRef\":\"target-pool-uuid\","
                + "\"targetStoragePath\":\"rbd\",\"targetStorageType\":\"RBD\","
                + "\"target\":{\"storageRef\":\"target-pool-uuid\",\"storagePath\":\"rbd\","
                + "\"storagePoolType\":\"RBD\",\"path\":\"target-image\",\"format\":\"raw\"}}]}");
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILBACK, "{}");
        Mockito.when(drRemoteAgentClient.isRemoteKvmSource(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.supports(plan)).thenReturn(true);
        Mockito.when(drPlanOwnedTransportService.startReverseTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString()))
                .thenReturn(exports("10.10.22.1", 12022, "dr-reverse-sda"));
        Mockito.when(drFailbackPreflightService.validate(plan, run))
                .thenReturn(DrFailbackPreflightResult.success(null, null, null));
        Mockito.when(agentManager.send(Mockito.eq(103L), Mockito.isA(FtctlDrCapabilitiesCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrCapabilitiesAnswer answer = new FtctlDrCapabilitiesAnswer(invocation.getArgument(1), true, "ok");
                    answer.setSupportedFeatures(java.util.Arrays.asList("control-protocol-v2",
                            "dr-transition-preflight-v2", "dr-reverse-site-agent-rbd-transport-v1"));
                    return answer;
                });
        ArgumentCaptor<FtctlDrActionCommand> failbackCommand = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.when(agentManager.send(Mockito.eq(103L), failbackCommand.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.FAILBACK,
                    plan.getUuid(), run.getUuid(), "accepted", true, "FAILBACK_SYNCING", "reverse-transfer",
                    1, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"FAILBACK_SYNCING\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Mockito.verify(drPlanOwnedTransportService).startReverseTargetExport(
                Mockito.eq(plan), Mockito.eq(run), Mockito.anyString());
        Assert.assertEquals(FtctlDrActionCommand.Action.FAILBACK, failbackCommand.getValue().getAction());
        Assert.assertTrue(failbackCommand.getValue().getProfileJson().contains("\"mode\":\"site-agent-nbd\""));
        Assert.assertTrue(failbackCommand.getValue().getProfileJson().contains("dr-reverse-sda"));
        Mockito.verify(agentManager, Mockito.never()).easySend(Mockito.eq(102L), Mockito.any());
    }

    private JsonArray exports(String host, int port, String name) {
        return JsonParser.parseString("[{\"device\":\"sda\",\"host\":\"" + host
                + "\",\"port\":" + port + ",\"name\":\"" + name
                + "\",\"uri\":\"nbd://" + host + ":" + port + "/" + name + "\"}]")
                .getAsJsonArray();
    }

    @Test
    public void testFailoverDispatchesRestorePointReferenceToFtctlProfile() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_TEST_FAILOVER,
                "{\"restorePointId\":9,\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":2\"}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        DrRestorePointVO checkpoint = checkpoint(plan, "ftctl:" + plan.getUuid() + ":run-sync:2");
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(checkpoint);
        mockCapabilities();
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.TEST_PREPARE,
                    plan.getUuid(), run.getUuid(), "accepted", true, "TESTING", "test-session-ready",
                    100, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"TESTING\",\"test_restore_point_ref\":\"ftctl:" + plan.getUuid() + ":2\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.TEST_PREPARE, command.getAction());
        Assert.assertNull(command.getRestorePointId());
        Assert.assertEquals(checkpoint.getSourceSnapshotRef(), command.getCheckpointRef());
        Assert.assertTrue(command.getProfileJson().contains("\"restorePointRef\":\"" + checkpoint.getSourceSnapshotRef() + "\""));
        Assert.assertTrue(command.getRequestJson().contains("\"restorePointRef\":\"" + checkpoint.getSourceSnapshotRef() + "\""));
        Assert.assertTrue(command.getRequestJson().contains("\"checkpointContractVersion\":1"));
        Assert.assertTrue(command.getRequestJson().contains("\"checkpointSequence\":2"));
        Assert.assertTrue(command.getArtifactSpecJson().contains("\"checkpointContractVersion\":1"));
        Assert.assertFalse(command.getRequestJson().contains("restorePointId"));
        Assert.assertEquals("3", command.getArtifactContractVersion());
        Assert.assertTrue(command.getArtifactSpecJson().contains("\"canonicalLocator\":\"rbd:rbd/Rocky10-1-dr-disk-0\""));
    }

    @Test
    public void failoverDispatchesModeRestorePointAndFinalSyncToFtctlProfile() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILOVER,
                "{\"mode\":\"planned\",\"restorePointId\":12,\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":3\",\"finalSync\":true}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        DrRestorePointVO checkpoint = checkpoint(plan, "ftctl:" + plan.getUuid() + ":run-sync:3");
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(checkpoint);
        mockCapabilities();
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.FAILOVER,
                    plan.getUuid(), run.getUuid(), "accepted", true, "RUNNING", "failover-worker-started",
                    15, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"RUNNING\",\"failover_restore_point_ref\":\"ftctl:" + plan.getUuid() + ":3\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.FAILOVER, command.getAction());
        Assert.assertEquals("planned", command.getMode());
        Assert.assertNull(command.getRestorePointId());
        Assert.assertEquals(checkpoint.getSourceSnapshotRef(), command.getCheckpointRef());
        Assert.assertFalse(command.isWaitForCompletion());
        Assert.assertTrue(command.getProfileJson().contains("\"restorePointRef\":\"" + checkpoint.getSourceSnapshotRef() + "\""));
        Assert.assertTrue(command.getProfileJson().contains("\"finalSync\":true"));
        Assert.assertTrue(command.getRequestJson().contains("\"restorePointRef\":\"" + checkpoint.getSourceSnapshotRef() + "\""));
        Assert.assertTrue(command.getRequestJson().contains("\"finalSync\":true"));
    }

    @Test
    public void reprotectDispatchesImmutableTargetAuthorityContract() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        DrRunVO run = run(DrConstants.RUN_TYPE_REPROTECT, "{}");
        DrReprotectAuthoritySpec spec = new DrReprotectAuthoritySpec();
        spec.setPlanUuid(plan.getUuid());
        spec.setRunUuid(run.getUuid());
        spec.setExpectedActiveSide("TARGET");
        spec.setAuthorityGeneration(3L);
        spec.setCutoverSessionId("cutover-1");
        spec.setCheckpointSequence(17L);
        spec.setTargetVmId(256L);
        spec.setTargetExternalRef("target-vm-uuid");
        spec.setTargetInstanceName("i-2-256-VM");
        spec.setTargetPowerState("POWERED_ON");
        spec.setTargetMaterialized(true);
        spec.setTargetPromotionState("PROMOTED");
        Mockito.when(drReprotectPreflightService.validate(plan, run))
                .thenReturn(DrReprotectPreflightResult.success(spec));
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        mockCapabilities();
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.REPROTECT,
                    plan.getUuid(), run.getUuid(), "accepted", true, "REPROTECTING", "worker-started",
                    10, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"REPROTECTING\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.REPROTECT, command.getAction());
        Assert.assertEquals(DrReprotectAuthoritySpec.CONTRACT_VERSION, command.getAuthorityContractVersion());
        Assert.assertTrue(command.getAuthoritySpecJson().contains("\"expectedActiveSide\":\"TARGET\""));
        Assert.assertTrue(command.getAuthoritySpecJson().contains("\"authorityGeneration\":3"));
        Assert.assertTrue(command.getAuthoritySpecJson().contains("\"targetVmId\":256"));
    }

    @Test
    public void failbackStopsBeforeAgentDispatchWhenSitePreflightFails() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILBACK, "{\"force\":true}");
        Mockito.when(drFailbackPreflightService.validate(plan, run)).thenReturn(
                DrFailbackPreflightResult.failure(DrConstants.ERROR_FAILBACK_CREDENTIAL_NOT_READY,
                        "destination credential missing", null, null, "CONFIGURED", "MISSING", null));
        mockCapabilities();

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(DrConstants.ERROR_FAILBACK_CREDENTIAL_NOT_READY, result.getErrorCode());
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.eq(103L), Mockito.isA(FtctlDrActionCommand.class));
    }

    @Test
    public void missingWorkerHostFailsBeforeAgentDispatch() throws Exception {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILOVER, "{\"mode\":\"disaster\"}");

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(DrConstants.ERROR_TARGET_MAPPING_INVALID, result.getErrorCode());
        Mockito.verifyNoInteractions(agentManager);
    }

    private DrPlanVO ftctlDrPlan() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setSourceVmId(101L);
        plan.setSourceExternalRef("vmware-vm-01");
        plan.setActiveSide("SOURCE");
        plan.setRpoSeconds(30);
        plan.setRtoSeconds(300);
        plan.setSourceWorkerHostId(101L);
        plan.setTargetWorkerHostId(102L);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setPolicyJson("{\"compression\":true}");
        plan.setMappingJson("{\"diskPolicy\":\"same-order\",\"target\":{\"storagePoolType\":\"RBD\",\"storagePath\":\"rbd\"},"
                + "\"disks\":[{\"device\":\"sda\",\"capacityBytes\":\"107374182400\",\"target\":{"
                + "\"volumeId\":252,\"path\":\"Rocky10-1-dr-disk-0\",\"storagePoolType\":\"RBD\",\"storagePath\":\"rbd\",\"format\":\"raw\"}}]}");
        return plan;
    }

    private DrRunVO run(String runType, String requestJson) {
        DrRunVO run = new DrRunVO(0L, runType);
        run.setState(DrConstants.RUN_STATE_QUEUED);
        run.setRequestJson(requestJson);
        return run;
    }

    private DrRestorePointVO checkpoint(DrPlanVO plan, String ref) {
        DrRestorePointVO checkpoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
        checkpoint.setSourceSnapshotRef(ref);
        checkpoint.setState("READY");
        checkpoint.setCheckpointSequence(2L);
        checkpoint.setCheckpointCycleType("CBT_INCREMENTAL");
        checkpoint.setCycleToken(plan.getUuid() + ":2");
        checkpoint.setEffectiveMode("CBT_INCREMENTAL");
        checkpoint.setIncrementalVerified(Boolean.TRUE);
        return checkpoint;
    }

    private void mockCapabilities() throws Exception {
        Mockito.when(agentManager.send(Mockito.eq(103L), Mockito.isA(FtctlDrCapabilitiesCommand.class))).thenAnswer(invocation -> {
            FtctlDrCapabilitiesAnswer answer = new FtctlDrCapabilitiesAnswer(invocation.getArgument(1), true, "ok");
            answer.setSupportedFeatures(java.util.Arrays.asList("control-protocol-v2", "guest-preparation-v2",
                    "test-artifact-lifecycle-v2", "test-domain-lifecycle-v1", "cutover-ready-v1",
                    "cutover-manifest-v2", "cutover-preflight-v1"));
            return answer;
        });
    }
}
