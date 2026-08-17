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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrCycleSnapshot;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrCutoverSessionVO;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrFailbackLifecycleService;
import com.cloud.dr.DrFailbackSessionVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrPlanRuntimeVO;
import com.cloud.dr.DrReplicaDiskVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrSyncCycleVO;
import com.cloud.dr.DrTargetMaterializationService;
import com.cloud.dr.DrTargetPowerOnResult;
import com.cloud.dr.DrTestSessionVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.dao.DrCutoverDiskDao;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrFailbackSessionDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.dr.dao.DrReplicaDiskDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.dr.dao.DrTestSessionDao;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class FtctlDrRuntimeProjectionAdapterTest {

    @Mock
    private AgentManager agentManager;
    @Mock
    private DrPlanDao drPlanDao;
    @Mock
    private DrEventDao drEventDao;
    @Mock
    private DrRestorePointDao drRestorePointDao;
    @Mock
    private DrReplicaDao drReplicaDao;
    @Mock
    private DrReplicaDiskDao drReplicaDiskDao;
    @Mock
    private DrRunDao drRunDao;
    @Mock
    private DrRunStepDao drRunStepDao;
    @Mock
    private DrTargetMaterializationService drTargetMaterializationService;
    @Mock
    private DrCutoverSessionDao drCutoverSessionDao;
    @Mock
    private DrFailbackSessionDao drFailbackSessionDao;
    @Mock
    private DrFailbackLifecycleService drFailbackLifecycleService;
    @Mock
    private DrCutoverDiskDao drCutoverDiskDao;
    @Mock
    private DrPlanRuntimeDao drPlanRuntimeDao;
    @Mock
    private DrSyncCycleDao drSyncCycleDao;
    @Mock
    private DrTestSessionDao drTestSessionDao;
    @Mock
    private UserVmDao userVmDao;
    @Mock
    private VolumeDao volumeDao;

    @InjectMocks
    private FtctlDrRuntimeProjectionAdapter adapter;

    @Before
    public void allowCycleProjectionLock() {
        Mockito.lenient().when(drPlanDao.acquireInLockTable(Mockito.anyLong(), Mockito.anyInt()))
                .thenReturn(new DrPlanVO("projection-lock", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM));
    }

    @Test
    public void terminalTestCleanupProofRequiresArtifactsAndOwnedLeaseRelease() {
        FtctlDrStatusCommand command = new FtctlDrStatusCommand("plan-1", "run-1");
        FtctlDrStatusAnswer status = new FtctlDrStatusAnswer(command, true, "ok");
        status.setTestSessionState("CLEANED");
        status.setTestArtifactsState("CLEANED");
        status.setTestCleanupState("CLEANED");
        status.setCheckpointLeaseState("RELEASED");
        status.setCleanupRequired(false);

        Assert.assertTrue(adapter.hasTerminalTestCleanupProof(status, new JsonObject()));

        status.setCheckpointLeaseState("LEASED");
        Assert.assertFalse(adapter.hasTerminalTestCleanupProof(status, new JsonObject()));
    }

    @Test
    public void liveTransferOverlayPersistsNewerCorrelatedOperationSample() {
        DrPlanVO plan = new DrPlanVO("plan-live-progress", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        DrPlanRuntimeVO authority = new DrPlanRuntimeVO(plan.getId());
        authority.setControlRequestRunUuid(run.getUuid());
        authority.setPlanCycleSequence(8L);
        authority.setTransferProgressSchemaVersion(2);
        authority.setTransferCycleSequence(8L);
        authority.setTransferSampleSequence(2L);
        authority.setTransferBytesTotal(4096L);
        authority.setTransferBytesProcessed(512L);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(authority);

        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(), run.getUuid(),
                FtctlDrStatusCommand.StatusScope.OPERATION);
        FtctlDrStatusAnswer status = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                "ok", "SYNCING", "full-reseed-transfer", 40, null, null, null, null, null, 0, "", "{}");
        status.setTransferProgressSchemaVersion(2);
        status.setTransferCycleSequence(8L);
        status.setTransferSampleSequence(3L);
        status.setTransferActivityState("COPYING");
        status.setTransferPhase("TRANSFER");
        status.setTransferMode("FULL_RESEED");
        status.setTransferBytesTotal(4096L);
        status.setTransferBytesProcessed(1024L);
        status.setTransferPayloadBytes(1024L);
        status.setTransferPercent(25D);
        status.setTransferProgressSampleEpochMs(1_786_000_000_000L);

        adapter.projectLiveTransferOverlay(plan, run, status);

        Assert.assertEquals(Long.valueOf(3L), authority.getTransferSampleSequence());
        Assert.assertEquals(Long.valueOf(1024L), authority.getTransferBytesProcessed());
        Assert.assertEquals(Double.valueOf(25D), authority.getTransferPercent());
        Mockito.verify(drPlanRuntimeDao).update(authority.getId(), authority);
    }

    @Test
    public void liveTransferOverlayRejectsOlderSample() {
        DrPlanVO plan = new DrPlanVO("plan-live-progress", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        DrPlanRuntimeVO authority = new DrPlanRuntimeVO(plan.getId());
        authority.setControlRequestRunUuid(run.getUuid());
        authority.setPlanCycleSequence(8L);
        authority.setTransferProgressSchemaVersion(2);
        authority.setTransferCycleSequence(8L);
        authority.setTransferSampleSequence(4L);
        authority.setTransferBytesTotal(4096L);
        authority.setTransferBytesProcessed(2048L);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(authority);

        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(), run.getUuid(),
                FtctlDrStatusCommand.StatusScope.OPERATION);
        FtctlDrStatusAnswer status = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                "ok", "SYNCING", "full-reseed-transfer", 40, null, null, null, null, null, 0, "", "{}");
        status.setTransferProgressSchemaVersion(2);
        status.setTransferCycleSequence(8L);
        status.setTransferSampleSequence(3L);
        status.setTransferBytesTotal(4096L);
        status.setTransferBytesProcessed(1024L);

        adapter.projectLiveTransferOverlay(plan, run, status);

        Assert.assertEquals(Long.valueOf(4L), authority.getTransferSampleSequence());
        Assert.assertEquals(Long.valueOf(2048L), authority.getTransferBytesProcessed());
        Mockito.verify(drPlanRuntimeDao, Mockito.never()).update(Mockito.anyLong(), Mockito.any());
    }

    @Test
    public void idleRuntimeSummaryConvergesToLatestCompletedCycle() {
        DrPlanRuntimeVO authority = new DrPlanRuntimeVO(43L);
        authority.setTransferProgressSchemaVersion(2);
        authority.setTransferCycleSequence(352L);
        authority.setTransferMode("FULL_RESEED");
        authority.setTransferBytesTotal(107374182400L);
        authority.setTransferBytesProcessed(107374182400L);
        FtctlDrCycleSnapshot snapshot = new FtctlDrCycleSnapshot();
        snapshot.setSequence(528L);
        snapshot.setEffectiveMode("CBT_INCREMENTAL");
        snapshot.setVirtualBytes(107374182400L);
        snapshot.setChangedBytes(79691776L);
        snapshot.setSourceReadBytes(79691776L);
        snapshot.setTargetWrittenBytes(79691776L);
        snapshot.setTransferPayloadBytes(79691776L);
        snapshot.setThroughputBps(10485760L);
        snapshot.setMetricsEstimated(false);
        snapshot.setTargetDurableAt("2026-08-12T05:20:00Z");

        adapter.projectLatestCompletedTransferSummary(authority, snapshot);

        Assert.assertEquals("IDLE", authority.getTransferActivityState());
        Assert.assertEquals(Long.valueOf(528L), authority.getTransferCycleSequence());
        Assert.assertEquals("CBT_INCREMENTAL", authority.getTransferMode());
        Assert.assertEquals(Long.valueOf(79691776L), authority.getTransferBytesProcessed());
        Assert.assertEquals(Double.valueOf(100D), authority.getTransferPercent());
        Assert.assertFalse(authority.getTransferProgressStale());
    }

    @Test
    public void laterDurableCycleConsumesReverseCheckpointAndSupersedesOrphanCycle() {
        DrPlanVO plan = new DrPlanVO("plan-terminal-cycles", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrSyncCycleVO orphan = new DrSyncCycleVO(plan.getId(), "sync-run", 523L);
        orphan.setState("SYNCING");
        DrSyncCycleVO reverse = new DrSyncCycleVO(plan.getId(), "failback-run", 527L);
        reverse.setState("FAILBACK_DATA_READY");
        DrSyncCycleVO completed = new DrSyncCycleVO(plan.getId(), "sync-run", 528L);
        completed.setState("READY");
        completed.setCompleted(new Date());
        Mockito.when(drSyncCycleDao.listIncompleteAtOrBeforeSequence(plan.getId(), 528L, 100))
                .thenReturn(Arrays.asList(orphan, reverse));

        adapter.terminalizeSupersededSyncCycles(plan, completed);

        Mockito.verify(drSyncCycleDao).terminalize(orphan.getId(), "SUPERSEDED",
                "SUPERSEDED_BY_DURABLE_CYCLE", completed.getCompleted());
        Mockito.verify(drSyncCycleDao).terminalize(reverse.getId(), "CONSUMED",
                "CONSUMED_BY_DURABLE_CYCLE", completed.getCompleted());
    }

    @Test
    public void repeatedTerminalProjectionIsIdempotentAfterRestart() {
        DrPlanVO plan = new DrPlanVO("plan-terminal-restart", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrSyncCycleVO completed = new DrSyncCycleVO(plan.getId(), "sync-run", 528L);
        completed.setCompleted(new Date());
        Mockito.when(drSyncCycleDao.listIncompleteAtOrBeforeSequence(plan.getId(), 528L, 100))
                .thenReturn(Collections.emptyList());

        adapter.terminalizeSupersededSyncCycles(plan, completed);
        adapter.terminalizeSupersededSyncCycles(plan, completed);

        Mockito.verify(drSyncCycleDao, Mockito.times(2))
                .listIncompleteAtOrBeforeSequence(plan.getId(), 528L, 100);
        Mockito.verify(drSyncCycleDao, Mockito.never()).terminalize(Mockito.anyLong(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(Date.class));
    }

    @Test
    public void durableCycleImmediatelySupersedesSameSequenceAlias() {
        DrPlanVO plan = new DrPlanVO("plan-same-sequence-alias", 1L, 2L,
                DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrSyncCycleVO alias = new DrSyncCycleVO(plan.getId(), "scheduler-run", 528L);
        ReflectionTestUtils.setField(alias, "id", 527L);
        alias.setState("TRANSFERRING");
        DrSyncCycleVO completed = new DrSyncCycleVO(plan.getId(), "operation-run", 528L);
        completed.setCompleted(new Date());
        Mockito.when(drSyncCycleDao.listIncompleteAtOrBeforeSequence(plan.getId(), 528L, 100))
                .thenReturn(Collections.singletonList(alias));

        adapter.terminalizeSupersededSyncCycles(plan, completed);

        Mockito.verify(drSyncCycleDao).terminalize(alias.getId(), "SUPERSEDED",
                "SUPERSEDED_BY_DURABLE_CYCLE", completed.getCompleted());
    }

    @Test
    public void acceptedCycleOfNonTerminalRunIsNotSupersededByNextIncremental() {
        DrPlanVO plan = new DrPlanVO("plan-pinned-cycle", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        ReflectionTestUtils.setField(plan, "id", 77L);
        DrSyncCycleVO accepted = new DrSyncCycleVO(plan.getId(), "operation-run", 42L);
        ReflectionTestUtils.setField(accepted, "id", 420L);
        accepted.setState("TRANSFERRING");
        DrSyncCycleVO nextCompleted = new DrSyncCycleVO(plan.getId(), "scheduler-run", 43L);
        nextCompleted.setCompleted(new Date());
        DrRunVO child = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        child.setAcceptedCycleSequence(42L);

        Mockito.when(drSyncCycleDao.listIncompleteAtOrBeforeSequence(plan.getId(), 43L, 100))
                .thenReturn(Collections.singletonList(accepted));
        Mockito.when(drRunDao.listByPlanId(plan.getId())).thenReturn(Collections.singletonList(child));

        adapter.terminalizeSupersededSyncCycles(plan, nextCompleted);

        Mockito.verify(drSyncCycleDao, Mockito.never()).terminalize(Mockito.eq(accepted.getId()),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(Date.class));
    }

    @Test
    public void schedulerAndOperationRunUuidsConvergeOnCanonicalPlanSequence() {
        DrPlanVO plan = new DrPlanVO("canonical-cycle-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        ReflectionTestUtils.setField(plan, "id", 77L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        ReflectionTestUtils.setField(run, "id", 88L);
        DrSyncCycleVO canonical = new DrSyncCycleVO(plan.getId(), "scheduler-run", 19L);
        ReflectionTestUtils.setField(canonical, "id", 99L);
        Mockito.when(drSyncCycleDao.findByPlanSequence(plan.getId(), 19L)).thenReturn(canonical);

        FtctlDrStatusAnswer schedulerStatus = new FtctlDrStatusAnswer(
                new FtctlDrStatusCommand(plan.getUuid(), null), true, "ok");
        schedulerStatus.setActiveWorkerRunUuid("scheduler-run");
        adapter.projectCurrentSyncCycle(plan, run, schedulerStatus, 19L, "FULL_RESEED", "FULL_RESEED",
                "TRANSFERRING", "PENDING", null, new Date(), null, null);

        FtctlDrStatusAnswer operationStatus = new FtctlDrStatusAnswer(
                new FtctlDrStatusCommand(plan.getUuid(), "operation-run"), true, "ok");
        operationStatus.setLatestCompletedProducerRunUuid("operation-run");
        operationStatus.setLatestCompletedCheckpointSequence(19L);
        operationStatus.setLatestCompletedSourceCheckpointAt("2026-08-14T01:00:00Z");
        operationStatus.setLatestCompletedTargetDurableAt("2026-08-14T01:00:01Z");
        FtctlDrCycleSnapshot snapshot = new FtctlDrCycleSnapshot();
        snapshot.setPlanUuid(plan.getUuid());
        snapshot.setRunUuid("operation-run");
        snapshot.setSequence(19L);
        snapshot.setCycleToken(plan.getUuid() + ":19");
        snapshot.setState("READY");
        snapshot.setEffectiveMode("NO_CHANGE");
        snapshot.setBaselineGeneration(19L);
        snapshot.setChangedBytes(0L);
        snapshot.setTargetWrittenBytes(0L);
        operationStatus.setLatestCompletedCycle(snapshot);

        DrSyncCycleVO completed = adapter.projectLatestCompletedSyncCycle(plan, run, operationStatus, 19L,
                "LOCAL_DURABLE");

        Assert.assertSame(canonical, completed);
        Assert.assertEquals("scheduler-run", completed.getEngineRunUuid());
        Assert.assertEquals("READY", completed.getState());
        Assert.assertNotNull(completed.getCompleted());
        Mockito.verify(drSyncCycleDao, Mockito.never()).persist(Mockito.any(DrSyncCycleVO.class));
        Mockito.verify(drSyncCycleDao, Mockito.times(2)).update(Mockito.eq(canonical.getId()), Mockito.same(canonical));
    }

    @Test
    public void releaseTombstoneConvergesPlanToDisabledUnprotectedWithoutChangingAuthority() {
        DrPlanVO plan = new DrPlanVO("release-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setActiveSide(DrConstants.AUTHORITY_SIDE_TARGET);
        DrPlanRuntimeVO planRuntime = new DrPlanRuntimeVO(plan.getId());
        String statusJson = "{\"command\":\"dr-status\",\"result\":\"ok\","
                + "\"plan_uuid\":\"release-plan\",\"state\":\"RELEASED\","
                + "\"step\":\"release-completed\",\"active_side\":\"TARGET\","
                + "\"protection_state\":\"UNPROTECTED\",\"scheduler_state\":\"STOPPED\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok",
                            plan.getUuid(), null, "ok", "RELEASED", "release-completed", 100,
                            null, null, null, null, null, 0, null, statusJson);
                    answer.setStatusScope("PLAN_AUTHORITY");
                    answer.setProtectionState("UNPROTECTED");
                    return answer;
                });
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(planRuntime);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.emptyList());
        Mockito.when(drRestorePointDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.emptyList());

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_UNPROTECTED, plan.getState());
        Assert.assertEquals(DrConstants.ADMIN_STATE_DISABLED, plan.getAdminState());
        Assert.assertEquals(DrConstants.AUTHORITY_SIDE_TARGET, plan.getActiveSide());
        Assert.assertEquals("STOPPED", planRuntime.getSchedulerState());
        Assert.assertEquals("UNPROTECTED", planRuntime.getProtectionState());
        Mockito.verify(drPlanDao).update(plan.getId(), plan);
        Mockito.verify(drSyncCycleDao, Mockito.never()).findLatestCompletedByPlanId(plan.getId());
        Mockito.verify(drFailbackLifecycleService, Mockito.never())
                .reconcile(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void refreshPlanProjectionCompletesTestFailoverWithoutDowngradingActiveSession() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_TEST_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrTestSessionVO session = new DrTestSessionVO(plan.getId(), run.getId(), "ACTIVE");

        String statusJson = "{\"state\":\"TEST_ARTIFACTS_READY\",\"worker_state\":\"SUCCEEDED\"," +
                "\"transition_state\":\"TEST_ACTIVE\",\"progress\":100}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "TEST_ARTIFACTS_READY", "test-artifacts-ready", 100,
                    null, null, null, null, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drTestSessionDao.findActiveByRunId(run.getId())).thenReturn(session);
        Mockito.when(drTargetMaterializationService.isTestTargetActive(run.getId())).thenReturn(true);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals("ACTIVE", session.getState());
        Assert.assertEquals(DrConstants.RUN_STATE_SUCCEEDED, run.getState());
        Assert.assertEquals("test-failover-active", run.getCurrentStepName());
        Assert.assertNotNull(run.getCompleted());
        Mockito.verify(drTargetMaterializationService, Mockito.never())
                .enqueueTestMaterialization(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString());
    }

    @Test
    public void refreshPlanProjectionSeparatesAuthorityAndOperationWithoutErasingLastGoodVerification() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_TEST_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrPlanRuntimeVO authority = new DrPlanRuntimeVO(plan.getId());
        authority.setLatestCompletedCycleSequence(42L);
        authority.setLatestCompletedIncrementalVerified(true);

        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(authority);
        Mockito.when(drTargetMaterializationService.isTestTargetActive(run.getId())).thenReturn(true);
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            if (command.getStatusScope() == FtctlDrStatusCommand.StatusScope.PLAN_AUTHORITY) {
                FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                        "ok", "READY", "checkpoint-ready", 100, null, null, null,
                        null, null, 0, "", "{\"scheduler_state\":\"RUNNING\"}");
                answer.setStatusScope("PLAN_AUTHORITY");
                return answer;
            }
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "TEST_ARTIFACTS_READY", "test-artifacts-ready", 100, null, null, null,
                    null, null, 0, "", "{\"state\":\"TEST_ARTIFACTS_READY\"}");
            answer.setStatusScope("OPERATION");
            return answer;
        });

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(Boolean.TRUE, authority.getLatestCompletedIncrementalVerified());
        ArgumentCaptor<FtctlDrStatusCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrStatusCommand.class);
        Mockito.verify(agentManager, Mockito.times(2)).easySend(Mockito.eq(103L), commandCaptor.capture());
        Assert.assertEquals(FtctlDrStatusCommand.StatusScope.PLAN_AUTHORITY,
                commandCaptor.getAllValues().get(0).getStatusScope());
        Assert.assertEquals(FtctlDrStatusCommand.StatusScope.OPERATION,
                commandCaptor.getAllValues().get(1).getStatusScope());
    }

    @Test
    public void refreshPlanProjectionRetainsLastGoodDataForMixedCompletedCycleGeneration() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "READY", "scheduler-completed", 100, null, null, null,
                    null, null, 0, "", "{}");
            answer.setLatestCompletedCheckpointSequence(7L);
            answer.setLatestCompletedCheckpointState("READY");
            answer.setLatestCompletedCycleToken(plan.getUuid() + ":7");
            answer.setLatestCompletedBaselineGeneration(8L);
            answer.setLatestCompletedChangedBytes(0L);
            answer.setLatestCompletedTargetWrittenBytes(0L);
            answer.setLatestCompletedEffectiveMode("NO_CHANGE");
            return answer;
        });

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertFalse(result.isSuccess());
        Assert.assertTrue(result.isRetryable());
        Assert.assertEquals("DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT", result.getErrorCode());
        ArgumentCaptor<DrPlanRuntimeVO> runtime = ArgumentCaptor.forClass(DrPlanRuntimeVO.class);
        Mockito.verify(drPlanRuntimeDao).persist(runtime.capture());
        Assert.assertEquals("INCONSISTENT", runtime.getValue().getProjectionIntegrityState());
        Assert.assertEquals(Long.valueOf(7L), runtime.getValue().getProjectionIntegritySequence());
        Mockito.verify(drSyncCycleDao, Mockito.never()).persist(Mockito.any());
        Mockito.verify(drRestorePointDao, Mockito.never()).persist(Mockito.any());
    }

    @Test
    public void refreshPlanProjectionPersistsFtctlCheckpointRestorePoint() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(9L);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "READY", "scheduler-completed", 100,
                    "2026-07-01T01:05:00Z", "2026-07-01T01:05:02Z", 2,
                    9L, null, 0, "", "{\"current_checkpoint_sequence\":3,\"current_checkpoint_state\":\"TRANSFERRING\","
                            + "\"latest_completed_checkpoint_sequence\":2,\"latest_completed_checkpoint_ref\":\"ftctl:"
                            + plan.getUuid() + ":2\",\"latest_completed_checkpoint_state\":\"READY\","
                            + "\"latest_completed_target_durable_at\":\"2026-07-01T01:05:02Z\"}");
            answer.setCurrentCheckpointSequence(3L);
            answer.setCurrentCheckpointState("TRANSFERRING");
            answer.setLatestCompletedCheckpointSequence(2L);
            answer.setLatestCompletedCheckpointRef("ftctl:" + plan.getUuid() + ":2");
            answer.setLatestCompletedCheckpointState("READY");
            answer.setLatestCompletedProducerRunUuid("sync-producer-run");
            answer.setLatestCompletedSourceCheckpointAt("2026-07-01T01:05:00Z");
            answer.setLatestCompletedTargetDurableAt("2026-07-01T01:05:02Z");
            answer.setLatestCompletedTargetReadyRpoSeconds(2);
            answer.setLatestCompletedEffectiveMode("CBT_INCREMENTAL");
            answer.setLatestCompletedIncrementalVerified(true);
            answer.setLatestCompletedMetricsEstimated(false);
            answer.setLatestCompletedVirtualBytes(107374182400L);
            answer.setLatestCompletedChangedBytes(131072L);
            answer.setLatestCompletedSourceReadBytes(131072L);
            answer.setLatestCompletedTargetWrittenBytes(131072L);
            answer.setLatestCompletedTransferPayloadBytes(131072L);
            answer.setLatestCompletedChangedExtentCount(2L);
            answer.setLatestCompletedDurationMs(25L);
            answer.setLatestCompletedThroughputBps(5242880L);
            answer.setLatestCompletedNbdTeardownState("DRAINED");
            answer.setLatestCompletedNbdTeardownStartedAtEpochMs(1782867901000L);
            answer.setLatestCompletedNbdTeardownCompletedAtEpochMs(1782867902000L);
            answer.setLatestCompletedNbdTeardownDurationMs(1000L);
            answer.setLatestCompletedNbdSourceDeviceCount(1);
            answer.setLatestCompletedNbdTargetDeviceCount(1);
            answer.setLatestCompletedNbdQuarantinedDeviceCount(0);
            answer.setLatestCompletedBaselineGeneration(2L);
            answer.setLatestCompletedCycleToken(plan.getUuid() + ":2");
            return answer;
        });
        Mockito.when(drRestorePointDao.findByPlanIdAndSourceSnapshotRef(plan.getId(), "ftctl:" + plan.getUuid() + ":2")).thenReturn(null);
        Mockito.when(drPlanDao.acquireInLockTable(plan.getId(), 10)).thenReturn(plan);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, plan.getState());
        Assert.assertNotNull(plan.getTargetReadyAt());
        Assert.assertEquals(Integer.valueOf(2), plan.getTargetReadyRpoSeconds());
        Mockito.verify(drPlanDao, Mockito.atLeastOnce()).update(Mockito.eq(plan.getId()), Mockito.same(plan));

        ArgumentCaptor<DrRestorePointVO> restorePointCaptor = ArgumentCaptor.forClass(DrRestorePointVO.class);
        Mockito.verify(drRestorePointDao).persist(restorePointCaptor.capture());
        DrRestorePointVO restorePoint = restorePointCaptor.getValue();
        Assert.assertEquals(plan.getId(), restorePoint.getPlanId());
        Assert.assertEquals("ftctl:" + plan.getUuid() + ":2", restorePoint.getSourceSnapshotRef());
        Assert.assertEquals("FTCTL_DR_CHECKPOINT", restorePoint.getRestorePointType());
        Assert.assertEquals("READY", restorePoint.getState());
        Assert.assertNotNull(restorePoint.getTargetReadyAt());
        Assert.assertEquals("CBT_INCREMENTAL", restorePoint.getEffectiveMode());
        Assert.assertEquals(Boolean.TRUE, restorePoint.getIncrementalVerified());
        Assert.assertEquals(Boolean.FALSE, restorePoint.getMetricsEstimated());
        Assert.assertEquals(Long.valueOf(131072L), restorePoint.getChangedBytes());
        Assert.assertEquals(Long.valueOf(131072L), restorePoint.getTransferPayloadBytes());
        Assert.assertEquals(Long.valueOf(2L), restorePoint.getChangedExtentCount());
        Assert.assertEquals(plan.getUuid() + ":2", restorePoint.getCycleToken());
        ArgumentCaptor<DrSyncCycleVO> cycleCaptor = ArgumentCaptor.forClass(DrSyncCycleVO.class);
        Mockito.verify(drSyncCycleDao, Mockito.atLeastOnce()).persist(cycleCaptor.capture());
        DrSyncCycleVO completedCycle = cycleCaptor.getAllValues().stream()
                .filter(cycle -> cycle.getSequence() == 2L)
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals("DRAINED", completedCycle.getNbdTeardownState());
        Assert.assertEquals(Integer.valueOf(1), completedCycle.getNbdSourceDeviceCount());
        Assert.assertEquals(Integer.valueOf(1), completedCycle.getNbdTargetDeviceCount());
        Assert.assertEquals(0, completedCycle.getNbdQuarantinedDeviceCount());

        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.argThat(event ->
                event != null && DrConstants.EVENT_PROJECTION_REFRESH.equals(event.getEventType())));
    }

    @Test
    public void refreshPlanProjectionRejectsIncrementalCheckpointWithoutNbdDrainEvidence() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                            "ok", "READY", "scheduler-completed", 100, null, null, null,
                            null, null, 0, "", "{}");
                    answer.setLatestCompletedCheckpointSequence(3L);
                    answer.setLatestCompletedCheckpointState("READY");
                    answer.setLatestCompletedCycleToken(plan.getUuid() + ":3");
                    answer.setLatestCompletedBaselineGeneration(3L);
                    answer.setLatestCompletedEffectiveMode("CBT_INCREMENTAL");
                    answer.setLatestCompletedIncrementalVerified(true);
                    answer.setLatestCompletedChangedBytes(4096L);
                    answer.setLatestCompletedTargetWrittenBytes(4096L);
                    return answer;
                });

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("DR_STATUS_CYCLE_SNAPSHOT_INCOHERENT", result.getErrorCode());
        Mockito.verify(drSyncCycleDao, Mockito.never()).persist(Mockito.any());
    }

    @Test
    public void refreshPlanProjectionMarksPlanAndReplicaFailedOver() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setActiveSide("SOURCE");
        replica.setPowerState(DrConstants.REPLICA_POWER_STATE_POWERED_OFF);

        String statusJson = "{\"state\":\"FAILED_OVER\",\"active_side\":\"TARGET\",\"failover_session_id\":\""
                + plan.getUuid() + ":run-failover\",\"failover_mode\":\"planned\",\"failover_restore_point_ref\":\"ftctl:"
                + plan.getUuid() + ":3\",\"checkpoint_sequence\":3,\"target_power_state\":\"POWER_ON_DELEGATED\","
                + "\"target_promotion_state\":\"PROMOTED\",\"rto_actual_seconds\":4}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "FAILED_OVER", "active-side-switch", 100,
                    "2026-07-01T01:10:00Z", "2026-07-01T01:10:03Z", 3,
                    10L, null, 0, "", statusJson);
        });
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));
        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_STATE_FAILED_OVER, replica.getState());
        Assert.assertEquals("TARGET", replica.getActiveSide());
        Assert.assertEquals("POWER_ON_DELEGATED", replica.getPowerState());
        Assert.assertTrue(replica.getRuntimeStateJson().contains("\"failover_session_id\""));
        Assert.assertTrue(result.getDetailsJson().contains("\"failoverMode\":\"planned\""));

        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionMarksPlanAndReplicaReadyAfterFailback() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_FAILED_OVER);
        replica.setActiveSide("TARGET");
        replica.setPowerState("POWER_ON_DELEGATED");
        replica.setTargetVmId(9L);

        String statusJson = "{\"state\":\"READY\",\"active_side\":\"SOURCE\",\"failback_session_id\":\""
                + plan.getUuid() + ":run-failback\",\"failback_restore_point_ref\":\"ftctl:"
                + plan.getUuid() + ":4\",\"checkpoint_sequence\":4,\"source_power_state\":\"POWER_ON_DELEGATED\","
                + "\"source_promotion_state\":\"PROMOTED\",\"reverse_direction\":\"KVM_TO_KVM\","
                + "\"rto_actual_seconds\":7}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "READY", "active-side-restore", 100,
                    "2026-07-01T01:20:00Z", "2026-07-01T01:20:02Z", 2,
                    11L, null, 0, "", statusJson);
        });
        DrFailbackSessionVO failbackSession = new DrFailbackSessionVO(
                plan.getId(), 0L, plan.getUuid() + ":run-failback", "COMPLETED");
        failbackSession.setCheckpointSequence(4L);
        failbackSession.setPostFailbackCheckpointSequence(5L);
        failbackSession.setTargetPowerState("POWERED_OFF");
        failbackSession.setSourcePowerState("POWERED_ON");
        failbackSession.setBootValidationState("POWER_STATE_VALIDATED");
        failbackSession.setEngineAckState("ACKNOWLEDGED");
        Mockito.when(drFailbackLifecycleService.reconcile(
                Mockito.eq(plan), Mockito.isNull(), Mockito.any(JsonObject.class)))
                .thenReturn(failbackSession);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, plan.getState());
        Assert.assertEquals("SOURCE", plan.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("SOURCE", replica.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_POWER_STATE_POWERED_OFF, replica.getPowerState());
        Assert.assertTrue(replica.getRuntimeStateJson().contains("\"failback_session_id\""));
        Assert.assertTrue(result.getDetailsJson().contains("\"failbackSessionId\""));

        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionKeepsTargetActiveAfterReprotect() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_FAILED_OVER);
        replica.setActiveSide("TARGET");
        replica.setPowerState("POWER_ON_DELEGATED");
        replica.setTargetVmId(9L);

        String statusJson = "{\"state\":\"READY\",\"active_side\":\"TARGET\",\"reprotect_session_id\":\""
                + plan.getUuid() + ":run-reprotect\",\"reprotect_restore_point_ref\":\"ftctl:"
                + plan.getUuid() + ":6\",\"checkpoint_sequence\":6,\"target_power_state\":\"POWER_ON_DELEGATED\","
                + "\"target_promotion_state\":\"PROMOTED\",\"reverse_direction\":\"KVM_TO_KVM\","
                + "\"rto_actual_seconds\":5}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "READY", "reprotect-ready", 100,
                    "2026-07-01T02:20:00Z", "2026-07-01T02:20:03Z", 3,
                    12L, null, 0, "", statusJson);
        });
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("TARGET", replica.getActiveSide());
        Assert.assertEquals("POWER_ON_DELEGATED", replica.getPowerState());
        Assert.assertTrue(replica.getRuntimeStateJson().contains("\"reprotect_session_id\""));
        Assert.assertTrue(result.getDetailsJson().contains("\"reprotectSessionId\""));

        Mockito.verify(drPlanDao, Mockito.atLeastOnce()).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionCompletesFailoverOnlyAfterCloudPromotionAndEngineAck() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrCutoverSessionVO session = new DrCutoverSessionVO(plan.getId(), run.getId(), run.getRunType(), "CUTOVER_READY");
        Date now = new Date();

        String manifestSha256 = String.join("", Collections.nCopies(64, "a"));
        String statusJson = "{\"state\":\"CUTOVER_READY\",\"active_side\":\"SOURCE\",\"failover_session_id\":\""
                + plan.getUuid() + ":" + run.getUuid() + "\",\"failover_restore_point_ref\":\"ftctl:"
                + plan.getUuid() + ":7\",\"checkpoint_sequence\":7,\"guest_prep_state\":\"READY\","
                + "\"guestprep_checkpoint_sequence\":7,\"manifest_schema_version\":\"FTCTL_GUESTPREP_MANIFEST_V2\","
                + "\"manifest_sha256\":\"" + manifestSha256 + "\",\"target_disk_count\":1,"
                + "\"source_fence_state\":\"ACKNOWLEDGED\",\"source_power_state\":\"POWERED_OFF\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "CUTOVER_READY", "cutover-ready", 95,
                    "2026-07-01T03:10:00Z", "2026-07-01T03:10:02Z", 2,
                    13L, null, 0, "", statusJson);
            answer.setGuestPreparationState("READY");
            answer.setManifestSchemaVersion("FTCTL_GUESTPREP_MANIFEST_V2");
            answer.setManifestSha256(manifestSha256);
            answer.setGuestPreparationCheckpointSequence(7L);
            answer.setLatestCompletedCheckpointSequence(7L);
            answer.setTargetDiskCount(1);
            return answer;
        });
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "acknowledged"));
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drCutoverSessionDao.findActiveByRunId(run.getId())).thenReturn(session);
        Mockito.when(drTargetMaterializationService.ensureTargetPoweredOn(plan.getId()))
                .thenReturn(new DrTargetPowerOnResult(91L, "target-uuid", "POWERED_ON",
                        "POWER_STATE_VALIDATED", now, now, false));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertEquals("PROMOTED", session.getCloudPromotionState());
        Assert.assertEquals("POWERED_ON", session.getTargetPowerState());
        Assert.assertEquals("ACKNOWLEDGED", session.getEngineAckState());
        Assert.assertNotNull(session.getCompletedAt());
        Assert.assertEquals(DrConstants.RUN_STATE_SUCCEEDED, run.getState());
        Assert.assertEquals("completed", run.getCurrentStepName());
        Assert.assertNull(run.getErrorCode());
        Assert.assertNotNull(run.getCompleted());

        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(103L), commandCaptor.capture());
        FtctlDrActionCommand commit = commandCaptor.getValue();
        Assert.assertEquals("DR_CUTOVER_COMMIT_V2", commit.getCutoverCommitContractVersion());
        Assert.assertEquals(plan.getUuid() + ":" + run.getUuid(), commit.getCutoverEngineSessionId());
        Assert.assertEquals(session.getUuid(), commit.getCutoverCloudSessionId());
        Assert.assertEquals(Long.valueOf(7L), commit.getCutoverCheckpointSequence());
        Assert.assertEquals(Long.valueOf(91L), commit.getCutoverTargetVmId());
        Assert.assertEquals("target-uuid", commit.getCutoverTargetExternalRef());
        Assert.assertEquals("ACKNOWLEDGED", commit.getCutoverSourceFenceState());
        Assert.assertEquals("POWERED_OFF", commit.getCutoverSourcePowerState());
        Assert.assertEquals(64, commit.getCutoverCommitEnvelopeSha256().length());

        ArgumentCaptor<DrRunStepVO> stepCaptor = ArgumentCaptor.forClass(DrRunStepVO.class);
        Mockito.verify(drRunStepDao, Mockito.atLeast(5)).persist(stepCaptor.capture());
        Assert.assertTrue(stepCaptor.getAllValues().stream()
                .anyMatch(step -> "engine-state-reconciliation".equals(step.getStepName())
                        && DrConstants.STEP_STATE_SUCCEEDED.equals(step.getState())));
        Assert.assertTrue(stepCaptor.getAllValues().stream()
                .anyMatch(step -> "completed".equals(step.getStepName())
                        && Integer.valueOf(100).equals(step.getProgress())));

        Mockito.verify(drRunDao).update(Mockito.eq(run.getId()), Mockito.same(run));
        Mockito.verify(drEventDao, Mockito.atLeastOnce()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionFailsAcceptedRunWhenStatusRefreshFails() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, false, "FTCTL_DR status refresh failed", plan.getUuid(), null,
                    "error", "ERROR", "status-refresh", 0,
                    null, null, null, 14L, DrConstants.ERROR_ENGINE_ACTION_FAILED, 2,
                    "status failed", "{\"state\":\"ERROR\",\"error_code\":\"DR_ENGINE_ACTION_FAILED\"}");
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals("runtime-projection", run.getCurrentStepName());
        Assert.assertEquals(DrConstants.ERROR_ENGINE_ACTION_FAILED, run.getErrorCode());
        Assert.assertEquals("FTCTL_DR status refresh failed", run.getErrorMessage());
        Assert.assertNotNull(run.getCompleted());

        ArgumentCaptor<DrRunStepVO> stepCaptor = ArgumentCaptor.forClass(DrRunStepVO.class);
        Mockito.verify(drRunStepDao).persist(stepCaptor.capture());
        DrRunStepVO step = stepCaptor.getValue();
        Assert.assertEquals(DrConstants.STEP_STATE_FAILED, step.getState());
        Assert.assertEquals(DrConstants.ERROR_ENGINE_ACTION_FAILED, step.getErrorCode());

        Mockito.verify(drRunDao).update(Mockito.eq(run.getId()), Mockito.same(run));
        Mockito.verify(drEventDao).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void failbackCommitAckPendingRemainsRunningEvenWithDataTerminal() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-failback-pending", 1L, 2L,
                DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_COMMIT_VERIFYING);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    String runUuid = command.getStatusScope() == FtctlDrStatusCommand.StatusScope.OPERATION
                            ? run.getUuid() : null;
                    String statusJson = "{\"state\":\"SYNCING\",\"step\":\"commit-verifying\","
                            + "\"failback_phase\":\"COMMIT_VERIFYING\","
                            + "\"failback_commit_outcome\":\"UNKNOWN\",\"engine_ack_state\":\"UNKNOWN\","
                            + "\"terminal_source\":\"ENGINE_TERMINAL\",\"terminal_authoritative\":true,"
                            + "\"error_code\":\"DR_FAILBACK_COMMIT_ACK_PENDING\",\"retryable\":true,"
                            + "\"retry_after_sec\":2}";
                    return new FtctlDrStatusAnswer(command, true, "accepted", plan.getUuid(), runUuid,
                            "unknown", "SYNCING", "commit-verifying", 90,
                            null, null, null, 21L, DrConstants.ERROR_FAILBACK_COMMIT_ACK_PENDING,
                            2, "Failback commit acknowledgement is pending verification", statusJson);
                });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_RUNNING, run.getState());
        Assert.assertEquals("commit-verifying", run.getCurrentStepName());
        Assert.assertEquals("lifecycle-pending", run.getProjectionState());
        Assert.assertNull(run.getCompleted());
        Assert.assertNull(run.getErrorCode());
        Assert.assertNull(run.getErrorMessage());
        Assert.assertTrue(run.isRetryable());
        Assert.assertFalse(run.isTerminalAuthoritative());
        Mockito.verify(drRunDao).update(Mockito.eq(run.getId()), Mockito.same(run));
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.argThat(event ->
                DrConstants.EVENT_RUN_FAILED.equals(event.getEventType())));
    }

    @Test
    public void failoverIncompleteCycleEvidenceAbortsAfterBoundedRetriesAndRestoresSourceAuthority() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        run.setRetryCount(2);
        DrCutoverSessionVO session = new DrCutoverSessionVO(
                plan.getId(), run.getId(), run.getRunType(), "CUTOVER_READY");
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setActiveSide("SOURCE");
        replica.setPowerState(DrConstants.REPLICA_POWER_STATE_POWERED_OFF);

        String statusJson = "{\"state\":\"CUTOVER_READY\",\"active_side\":\"SOURCE\","
                + "\"target_power_state\":\"POWERED_OFF\",\"failover_session_id\":\""
                + plan.getUuid() + ":" + run.getUuid() + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, false,
                            "FTCTL_DR latest completed cycle evidence is incomplete", plan.getUuid(), run.getUuid(),
                            "error", "CUTOVER_READY", "cutover-ready", 100,
                            null, null, null, null,
                            "DR_STATUS_CYCLE_EVIDENCE_INCOMPLETE", 65,
                            "incomplete", statusJson);
                    answer.setCycleEvidenceState("INCOMPLETE");
                    answer.setRetryable(true);
                    answer.setRetryAfterSeconds(5);
                    return answer;
                });
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "aborted"));
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drCutoverSessionDao.findActiveByRunId(run.getId())).thenReturn(session);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId()))
                .thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("DR_STATUS_CYCLE_EVIDENCE_INCOMPLETE", result.getErrorCode());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(Integer.valueOf(3), run.getRetryCount());
        Assert.assertEquals("ABORTED", session.getState());
        Assert.assertFalse(session.isCleanupRequired());
        Assert.assertEquals("ABORTED", session.getEngineAckState());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, plan.getState());
        Assert.assertEquals("SOURCE", plan.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("SOURCE", replica.getActiveSide());
        Assert.assertEquals(DrConstants.REPLICA_POWER_STATE_POWERED_OFF, replica.getPowerState());

        ArgumentCaptor<FtctlDrActionCommand> action = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.verify(agentManager).easySend(Mockito.eq(103L), action.capture());
        Assert.assertEquals(FtctlDrActionCommand.Action.FAILOVER_ABORT, action.getValue().getAction());
    }

    @Test
    public void failoverAbortRefusesRunningCloudTarget() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        run.setRetryCount(2);
        DrCutoverSessionVO session = new DrCutoverSessionVO(
                plan.getId(), run.getId(), run.getRunType(), "CUTOVER_READY");
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setActiveSide("SOURCE");
        replica.setPowerState(DrConstants.REPLICA_POWER_STATE_POWERED_OFF);
        UserVmVO targetVm = Mockito.mock(UserVmVO.class);

        String statusJson = "{\"state\":\"CUTOVER_READY\",\"active_side\":\"SOURCE\","
                + "\"target_power_state\":\"POWERED_OFF\",\"failover_session_id\":\""
                + plan.getUuid() + ":" + run.getUuid() + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, false,
                            "FTCTL_DR latest completed cycle evidence is incomplete", plan.getUuid(), run.getUuid(),
                            "error", "CUTOVER_READY", "cutover-ready", 100,
                            null, null, null, null,
                            "DR_STATUS_CYCLE_EVIDENCE_INCOMPLETE", 65,
                            "incomplete", statusJson);
                    answer.setCycleEvidenceState("INCOMPLETE");
                    answer.setRetryable(true);
                    answer.setRetryAfterSeconds(5);
                    return answer;
                });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drCutoverSessionDao.findActiveByRunId(run.getId())).thenReturn(session);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId()))
                .thenReturn(Collections.singletonList(replica));
        Mockito.when(userVmDao.findById(256L)).thenReturn(targetVm);
        Mockito.when(targetVm.getState()).thenReturn(VirtualMachine.State.Running);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals("ABORT_FAILED", session.getState());
        Assert.assertTrue(session.isCleanupRequired());
        Assert.assertEquals("DR_FAILOVER_ABORT_UNSAFE", session.getErrorCode());
        Mockito.verify(agentManager, Mockito.never())
                .easySend(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class));
    }

    @Test
    public void refreshPlanProjectionFailsAcceptedRunWhenRuntimePayloadReportsWorkerFailure() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_SKELETON_READY);

        String statusJson = "{\"state\":\"ERROR\",\"step\":\"ablestack-target-prepare-failed\","
                + "\"worker_state\":\"FAILED\",\"worker_exit_code\":32,"
                + "\"target_materialized\":false,\"target_vm_present\":false,"
                + "\"error_code\":\"" + DrConstants.ERROR_TARGET_DISK_TYPE_INVALID + "\","
                + "\"error_message\":\"ABLESTACK target preparation failed before target VM materialization\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "ERROR", "ablestack-target-prepare-failed", 100,
                    null, null, null, 15L, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals("runtime-projection", run.getCurrentStepName());
        Assert.assertEquals(DrConstants.ERROR_TARGET_DISK_TYPE_INVALID, run.getErrorCode());
        Assert.assertEquals("ABLESTACK target preparation failed before target VM materialization", run.getErrorMessage());
        Assert.assertNotNull(run.getCompleted());
        Assert.assertEquals(DrConstants.PLAN_STATE_ERROR, plan.getState());
        Assert.assertEquals(DrConstants.ERROR_TARGET_DISK_TYPE_INVALID, plan.getLastErrorCode());
        Assert.assertEquals(DrConstants.REPLICA_STATE_ERROR, replica.getState());

        ArgumentCaptor<DrRunStepVO> stepCaptor = ArgumentCaptor.forClass(DrRunStepVO.class);
        Mockito.verify(drRunStepDao).persist(stepCaptor.capture());
        DrRunStepVO step = stepCaptor.getValue();
        Assert.assertEquals(DrConstants.STEP_STATE_FAILED, step.getState());
        Assert.assertEquals(DrConstants.ERROR_TARGET_DISK_TYPE_INVALID, step.getErrorCode());
        Assert.assertTrue(step.getDetailsJson().contains("\"worker_state\":\"FAILED\""));

        Mockito.verify(drRunDao).update(Mockito.eq(run.getId()), Mockito.same(run));
        Mockito.verify(drPlanDao, Mockito.atLeastOnce()).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.atLeastOnce()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void reprotectFailurePreservesCommittedTargetAuthority() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_REPROTECT);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setActiveSide("TARGET");
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());

        String statusJson = "{\"state\":\"ERROR\",\"step\":\"reprotect-preflight\","
                + "\"worker_state\":\"FAILED\",\"worker_exit_code\":20,"
                + "\"error_code\":\"" + DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING + "\","
                + "\"error_message\":\"Target VM is not powered on according to its host Agent\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "ERROR", "reprotect-preflight", 100,
                    null, null, null, 15L, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING, run.getErrorCode());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertNull(plan.getLastErrorCode());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("FAILED_OVER_UNPROTECTED", runtime.getProtectionState());
        Mockito.verify(drReplicaDao, Mockito.never()).update(Mockito.eq(replica.getId()), Mockito.same(replica));
    }

    @Test
    public void rolledBackFailbackPreservesServingTargetAuthority() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        plan.setLastErrorCode("DR_FAILBACK_PROTECTION_RESUME_FAILED");
        plan.setLastErrorMessage("stale failback failure");
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setState(DrConstants.REPLICA_STATE_ERROR);
        replica.setPowerState("POWERED_ON");
        replica.setActiveSide("TARGET");
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());

        String statusJson = "{\"action\":\"dr-failback-abort\",\"state\":\"FAILED_OVER\","
                + "\"active_side\":\"TARGET\",\"source_power_state\":\"POWERED_OFF\","
                + "\"target_power_state\":\"POWERED_ON\",\"failback_commit_outcome\":\"ROLLED_BACK\","
                + "\"rollback_state\":\"COMPLETED\",\"scheduler_state\":\"STOPPED\","
                + "\"error_code\":\"DR_FAILBACK_PROTECTION_RESUME_FAILED\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                            "ok", "ERROR", "failback-aborted", 100,
                            null, null, null, 19L,
                            "DR_FAILBACK_PROTECTION_RESUME_FAILED", 0, "", statusJson);
                });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertNull(plan.getLastErrorCode());
        Assert.assertNull(plan.getLastErrorMessage());
        Assert.assertEquals("FAILED_OVER_UNPROTECTED", runtime.getProtectionState());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("POWERED_ON", replica.getPowerState());
        Assert.assertEquals("TARGET", replica.getActiveSide());
        Mockito.verify(drPlanDao, Mockito.atLeastOnce()).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao, Mockito.atLeastOnce()).update(Mockito.eq(replica.getId()), Mockito.same(replica));
    }

    @Test
    public void completedFailedFailbackRollbackClearsCurrentPlanError() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        plan.setLastErrorCode("DR_REVERSE_SNAPSHOT_OR_NBD_FAILED");
        plan.setLastErrorMessage("stale reverse transfer failure");
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setCompleted(new Date());
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setState(DrConstants.REPLICA_STATE_ERROR);
        replica.setPowerState("POWERED_ON");
        replica.setActiveSide("TARGET");
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());

        String statusJson = "{\"action\":\"dr-failback-abort\",\"state\":\"FAILED_OVER\","
                + "\"active_side\":\"TARGET\",\"source_power_state\":\"POWERED_OFF\","
                + "\"target_power_state\":\"POWERED_ON\",\"failback_commit_outcome\":\"ROLLED_BACK\","
                + "\"rollback_state\":\"COMPLETED\",\"scheduler_state\":\"STOPPED\",\"error_code\":\"\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                            "ok", "FAILED_OVER", "failback-aborted", 100,
                            null, null, null, 19L, null, 0, "", statusJson);
                });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(null);
        Mockito.when(drRunDao.findLatestByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertNotNull(run.getCompleted());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertNull(plan.getLastErrorCode());
        Assert.assertNull(plan.getLastErrorMessage());
        Assert.assertEquals("FAILED_OVER_UNPROTECTED", runtime.getProtectionState());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
    }

    @Test
    public void periodicAuthorityProjectionDoesNotReapplyTerminalReprotectError() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        Date committedAt = new Date(1_000_000L);
        plan.setLastSourceCheckpointAt(new Date(committedAt.getTime() - 209_000L));
        plan.setTargetReadyRpoSeconds(6500);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setPowerState("POWERED_ON");
        replica.setActiveSide("TARGET");
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());
        runtime.setSchedulerState("STOPPED");
        runtime.setSchedulerDesiredState("RUNNING");
        runtime.setSchedulerHealthState("STOPPED");
        runtime.setSchedulerRecoveryState("SUCCEEDED");
        runtime.setReplicationActivityState("STOPPED");
        runtime.setSchedulerPidAlive(false);
        runtime.setOwnerMatched(true);
        runtime.setSchedulerLeaseEpoch(99L);
        runtime.setAuthoritySequence(999L);
        DrCutoverSessionVO cutover = new DrCutoverSessionVO(plan.getId(), 94L,
                DrConstants.RUN_TYPE_FAILOVER, "PROMOTED");
        cutover.setCloudPromotionState("PROMOTED");
        cutover.setTargetPowerState("POWERED_ON");
        cutover.setEngineAckState("ACKNOWLEDGED");
        cutover.setCloudAuthorityGeneration(1L);
        cutover.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        cutover.setCompletedAt(committedAt);

        String statusJson = "{\"action\":\"dr-reprotect\",\"state\":\"ERROR\","
                + "\"active_side\":\"\",\"worker_state\":\"FAILED\","
                + "\"error_code\":\"" + DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                            "ok", "ERROR", "reprotect-not-eligible", 100,
                            null, null, null, 15L,
                            DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING, 0, "", statusJson);
                });
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drCutoverSessionDao.findCurrentAuthorityByPlanId(plan.getId())).thenReturn(cutover);
        Mockito.when(drCutoverSessionDao.findLatestActiveByPlanId(plan.getId())).thenReturn(cutover);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_FAILED_OVER, plan.getState());
        Assert.assertEquals("TARGET", plan.getActiveSide());
        Assert.assertNull(plan.getLastErrorCode());
        Assert.assertEquals(DrConstants.REPLICA_STATE_READY, replica.getState());
        Assert.assertEquals("POWERED_ON", replica.getPowerState());
        Assert.assertEquals("FAILED_OVER_UNPROTECTED", runtime.getProtectionState());
        Assert.assertEquals("STOPPED", runtime.getSchedulerDesiredState());
        Assert.assertEquals("SUPPRESSED", runtime.getSchedulerHealthState());
        Assert.assertEquals(DrConstants.SCHEDULER_RECOVERY_SUPPRESSED, runtime.getSchedulerRecoveryState());
        Assert.assertEquals("STOPPED", runtime.getReplicationActivityState());
        Assert.assertFalse(runtime.isSchedulerPidAlive());
        Assert.assertFalse(runtime.isOwnerMatched());
        Assert.assertEquals(Long.valueOf(209L), runtime.getRpoAgeSeconds());
        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
    }

    @Test
    public void committedTargetProjectionFreezesPlanRpoAtCutover() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.setCoordinatorWorkerHostId(103L);
        Date committedAt = new Date(1_000_000L);
        plan.setLastSourceCheckpointAt(new Date(committedAt.getTime() - 209_000L));
        plan.setTargetReadyRpoSeconds(6500);
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(plan.getId());
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(256L);
        replica.setState(DrConstants.REPLICA_STATE_READY);
        replica.setPowerState("POWERED_ON");
        replica.setActiveSide("TARGET");
        DrReplicaDiskVO replicaDisk = new DrReplicaDiskVO(replica.getId(), "disk-0");
        replicaDisk.setSourceDiskRef("checkpoint-1192");
        replicaDisk.setTargetDiskRef("target-volume");
        replicaDisk.setFormat("raw");
        replicaDisk.setTargetVolumeId(485L);
        VolumeVO targetVolume = Mockito.mock(VolumeVO.class);
        DrCutoverSessionVO cutover = new DrCutoverSessionVO(plan.getId(), 94L,
                DrConstants.RUN_TYPE_FAILOVER, DrConstants.PLAN_STATE_FAILED_OVER);
        cutover.setCheckpointSequence(1192L);
        cutover.setManifestSha256("1110e8073620358b1fc2944dd773f8b69a3af16758fbc32a47dc58d413c81c23");
        cutover.setCloudPromotionState("PROMOTED");
        cutover.setTargetPowerState("POWERED_ON");
        cutover.setEngineAckState("ACKNOWLEDGED");
        cutover.setCloudAuthorityGeneration(1L);
        cutover.setCompletedAt(committedAt);

        String statusJson = "{\"state\":\"FAILED_OVER\",\"active_side\":\"TARGET\","
                + "\"scheduler_state\":\"STOPPED\",\"scheduler_pid_alive\":false}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class)))
                .thenAnswer(invocation -> {
                    FtctlDrStatusCommand command = invocation.getArgument(1);
                    return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                            "ok", "FAILED_OVER", "cloud-promotion-committed", 100,
                            null, null, 6500, 15L, null, 0, "", statusJson);
                });
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(runtime);
        Mockito.when(drCutoverSessionDao.findCurrentAuthorityByPlanId(plan.getId())).thenReturn(cutover);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));
        Mockito.when(drReplicaDiskDao.listActiveByReplicaId(replica.getId()))
                .thenReturn(Collections.singletonList(replicaDisk));
        Mockito.when(drCutoverDiskDao.listActiveBySessionId(cutover.getId())).thenReturn(Collections.emptyList());
        Mockito.when(volumeDao.findById(485L)).thenReturn(targetVolume);
        Mockito.when(targetVolume.getUuid()).thenReturn("target-volume-uuid");
        Mockito.when(targetVolume.getChainInfo()).thenReturn("{\"targetType\":\"rbd\",\"storagePoolType\":\"RBD\"}");

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(Integer.valueOf(209), plan.getTargetReadyRpoSeconds());
        Assert.assertEquals(Long.valueOf(209L), runtime.getRpoAgeSeconds());
        Assert.assertFalse(runtime.isRpoOverdue());
        Mockito.verify(drCutoverDiskDao).persist(Mockito.argThat(disk ->
                disk.getDiskIndex() == 0
                        && "RBD".equals(disk.getProvider())
                        && Long.valueOf(485L).equals(disk.getTargetVolumeId())
                        && "target-volume-uuid".equals(disk.getTargetVolumeUuid())
                        && Long.valueOf(1192L).equals(disk.getCheckpointSequence())
                        && cutover.getManifestSha256().equals(disk.getManifestSha256())));
    }

    @Test
    public void refreshPlanProjectionUsesOperatorReadableMessageForVmwareMoverFailure() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_SKELETON_READY);

        String statusJson = "{\"state\":\"ERROR\",\"step\":\"replication-cycle-failed\","
                + "\"worker_state\":\"FAILED\",\"worker_exit_code\":68,"
                + "\"error_code\":\"" + DrConstants.ERROR_VMWARE_MOVER_FAILED + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "100% | qemu-img convert failed", plan.getUuid(), run.getUuid(),
                    "ok", "ERROR", "replication-cycle-failed", 100,
                    null, null, null, 15L, DrConstants.ERROR_VMWARE_MOVER_FAILED, 0,
                    "100% | qemu-img convert failed", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(DrConstants.ERROR_VMWARE_MOVER_FAILED, run.getErrorCode());
        Assert.assertEquals("VMware data mover failed while copying source disk data", run.getErrorMessage());
        Assert.assertEquals(DrConstants.PLAN_STATE_ERROR, plan.getState());
        Assert.assertEquals(DrConstants.ERROR_VMWARE_MOVER_FAILED, plan.getLastErrorCode());
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.atLeastOnce()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionPreservesCycleCommitFailureAsTypedError() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_SKELETON_READY);

        String errorMessage = "Disk data copied, but cycle metadata validation failed";
        String statusJson = "{\"state\":\"ERROR\",\"step\":\"replication-cycle-failed\","
                + "\"worker_state\":\"FAILED\",\"worker_exit_code\":87,"
                + "\"error_code\":\"" + DrConstants.ERROR_CBT_METRICS_INVALID + "\","
                + "\"error_message\":\"" + errorMessage + "\","
                + "\"failed_component\":\"vmware-mover\","
                + "\"data_commit_state\":\"DATA_COPIED_METADATA_FAILED\","
                + "\"data_copied\":true,\"metadata_committed\":false,\"target_durable\":false,"
                + "\"cycle_retry_mode\":\"RESEED_REQUIRED\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, errorMessage, plan.getUuid(), run.getUuid(),
                    "ok", "ERROR", "replication-cycle-failed", 100,
                    null, null, null, 15L, DrConstants.ERROR_CBT_METRICS_INVALID, 0,
                    errorMessage, statusJson);
            answer.setErrorMessage(errorMessage);
            answer.setFailedComponent("vmware-mover");
            answer.setDataCommitState("DATA_COPIED_METADATA_FAILED");
            answer.setDataCopied(Boolean.TRUE);
            answer.setMetadataCommitted(Boolean.FALSE);
            answer.setTargetDurable(Boolean.FALSE);
            answer.setCycleRetryMode("RESEED_REQUIRED");
            return answer;
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(DrConstants.ERROR_CBT_METRICS_INVALID, run.getErrorCode());
        Assert.assertEquals(errorMessage, run.getErrorMessage());
        Assert.assertFalse(run.getErrorMessage().contains("{\"state\""));
        Assert.assertEquals(DrConstants.PLAN_STATE_ERROR, plan.getState());
        Assert.assertEquals(DrConstants.ERROR_CBT_METRICS_INVALID, plan.getLastErrorCode());
        Assert.assertEquals(errorMessage, plan.getLastErrorMessage());
    }

    @Test
    public void refreshPlanProjectionKeepsInitialSeedPendingRunHealthyWhenTargetVmIsAbsent() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setLastErrorCode(DrConstants.ERROR_TARGET_VM_NOT_FOUND);
        plan.setLastErrorMessage("stale target VM missing projection");
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        run.setErrorCode(DrConstants.ERROR_TARGET_VM_NOT_FOUND);
        run.setErrorMessage("stale target VM missing projection");
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_SKELETON_READY);

        String statusJson = "{\"state\":\"SYNCING\",\"step\":\"full-seed-transfer\",\"progress\":40,"
                + "\"worker_state\":\"RUNNING\",\"target_materialized\":false,"
                + "\"target_vm_present\":false,\"target_storage_present\":true,"
                + "\"target_network_present\":false,\"restore_point_present\":false,"
                + "\"source_open\":{\"ready\":true},\"source_snapshot\":{\"ready\":true},"
                + "\"cbt_status\":{\"enabled\":true}}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "SYNCING", "full-seed-transfer", 40,
                    null, null, null, null, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_ACCEPTED, run.getState());
        Assert.assertEquals("syncing", run.getProjectionState());
        Assert.assertEquals("runtime-projection", run.getCurrentStepName());
        Assert.assertNull(run.getErrorCode());
        Assert.assertNull(run.getErrorMessage());
        Assert.assertNull(run.getCompleted());
        Assert.assertNull(plan.getLastErrorCode());
        Assert.assertNull(plan.getLastErrorMessage());

        ArgumentCaptor<DrRunStepVO> stepCaptor = ArgumentCaptor.forClass(DrRunStepVO.class);
        Mockito.verify(drRunStepDao).persist(stepCaptor.capture());
        DrRunStepVO step = stepCaptor.getValue();
        Assert.assertEquals(DrConstants.STEP_STATE_RUNNING, step.getState());
        Assert.assertEquals(Integer.valueOf(70), step.getProgress());
        Assert.assertNull(step.getErrorCode());
        Assert.assertEquals("FTCTL_DR sync has not materialized the target VM reference yet", step.getErrorMessage());

        Mockito.verify(drRunDao).update(Mockito.eq(run.getId()), Mockito.same(run));
        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionCompletesSyncRunAfterContinuousCycleBecomesDurable() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setTargetVmId(9L);
        DrRestorePointVO restorePoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
        restorePoint.setState("READY");

        String statusJson = "{\"state\":\"SYNCING\",\"step\":\"target-checkpoint-ready\",\"progress\":100,"
                + "\"cycle_state\":\"IDLE\",\"current_checkpoint_state\":\"COMPLETED\","
                + "\"scheduler_pid_alive\":true,\"target_materialized\":true,"
                + "\"target_vm_present\":true,\"target_storage_present\":true,"
                + "\"target_network_present\":true,\"restore_point_present\":true,"
                + "\"last_target_durable_at\":\"2026-07-19T09:20:00Z\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "SYNCING", "target-checkpoint-ready", 100,
                    "2026-07-19T09:19:58Z", "2026-07-19T09:20:00Z", 2,
                    9L, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(restorePoint);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_SUCCEEDED, run.getState());
        Assert.assertEquals("succeeded", run.getProjectionState());
        Assert.assertNotNull(run.getCompleted());
        Assert.assertEquals(DrConstants.PLAN_STATE_SYNCING, plan.getState());
    }

    @Test
    public void refreshPlanProjectionAcceptsLowerSequenceFromNewSchedulerLeaseEpoch() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrPlanRuntimeVO authority = new DrPlanRuntimeVO(plan.getId());
        authority.setEngineRunUuid("previous-run");
        authority.setRuntimeGeneration(24L);
        authority.setSchedulerSessionUuid(plan.getUuid());
        authority.setSchedulerLeaseEpoch(2L);
        authority.setAuthoritySequence(24L);

        String statusJson = "{\"state\":\"SYNCING\",\"runtime_generation\":6,"
                + "\"scheduler_state\":\"RUNNING\",\"scheduler_pid_alive\":true,"
                + "\"scheduler_session_uuid\":\"" + plan.getUuid() + "\","
                + "\"scheduler_lease_epoch\":3,\"authority_sequence\":6,"
                + "\"scheduler_health\":\"HEALTHY\",\"owner_matched\":true,"
                + "\"active_worker_run_uuid\":\"" + run.getUuid() + "\","
                + "\"cycle_state\":\"RUNNING\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), run.getUuid(),
                    "ok", "SYNCING", "incremental-transfer", 40,
                    null, null, null, null, null, 0, "", statusJson);
            answer.setSchedulerSessionUuid(plan.getUuid());
            answer.setSchedulerLeaseEpoch(3L);
            answer.setAuthoritySequence(6L);
            answer.setSchedulerHealth("HEALTHY");
            answer.setSchedulerPidAlive(true);
            answer.setOwnerMatched(true);
            answer.setActiveWorkerRunUuid(run.getUuid());
            return answer;
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanRuntimeDao.findByPlanId(plan.getId())).thenReturn(authority);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(run.getUuid(), authority.getEngineRunUuid());
        Assert.assertEquals(6L, authority.getRuntimeGeneration());
        Assert.assertEquals(3L, authority.getSchedulerLeaseEpoch());
        Assert.assertEquals(6L, authority.getAuthoritySequence());
        Assert.assertTrue(authority.isSchedulerPidAlive());
    }

    @Test
    public void refreshPlanProjectionPreservesVmwareMoverSourceGraphFailure() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);
        DrReplicaVO replica = new DrReplicaVO(plan.getId(), plan.getTargetSiteId());
        replica.setState(DrConstants.REPLICA_STATE_SKELETON_READY);

        String statusJson = "{\"state\":\"ERROR\",\"step\":\"replication-cycle-failed\","
                + "\"worker_state\":\"FAILED\",\"worker_exit_code\":72,"
                + "\"error_code\":\"" + DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "100% | qemu-img source graph invalid", plan.getUuid(), run.getUuid(),
                    "ok", "ERROR", "replication-cycle-failed", 100,
                    null, null, null, 15L, DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID, 0,
                    "100% | qemu-img source graph invalid", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(Collections.singletonList(replica));

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED, run.getState());
        Assert.assertEquals(DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID, run.getErrorCode());
        Assert.assertEquals("VMware data mover could not open the VDDK NBD source graph", run.getErrorMessage());
        Assert.assertEquals(DrConstants.PLAN_STATE_ERROR, plan.getState());
        Assert.assertEquals(DrConstants.ERROR_VMWARE_MOVER_SOURCE_GRAPH_INVALID, plan.getLastErrorCode());
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao, Mockito.atLeastOnce()).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionUsesSyncProducerWhilePollingCompletedTestCleanup() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setRpoSeconds(300);
        DrRunVO cleanupRun = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_TEST_CLEANUP);
        cleanupRun.setState(DrConstants.RUN_STATE_SUCCEEDED);
        DrRunVO syncRun = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);

        String statusJson = "{\"state\":\"READY\",\"scheduler_state\":\"RUNNING\","
                + "\"scheduler_session_uuid\":\"" + plan.getUuid() + "\","
                + "\"scheduler_lease_epoch\":4,\"authority_sequence\":292,"
                + "\"plan_cycle_sequence\":146,\"scheduler_health\":\"HEALTHY\","
                + "\"scheduler_pid_alive\":true,\"owner_matched\":true,"
                + "\"active_worker_run_uuid\":\"" + syncRun.getUuid() + "\","
                + "\"latest_completed_producer_run_uuid\":\"" + syncRun.getUuid() + "\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            FtctlDrStatusAnswer answer = new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), cleanupRun.getUuid(),
                    "ok", "READY", "checkpoint-ready", 100,
                    null, null, null, null, null, 0, "", statusJson);
            answer.setSchedulerSessionUuid(plan.getUuid());
            answer.setSchedulerLeaseEpoch(4L);
            answer.setAuthoritySequence(292L);
            answer.setPlanCycleSequence(146L);
            answer.setSchedulerHealth("HEALTHY");
            answer.setSchedulerPidAlive(true);
            answer.setOwnerMatched(true);
            answer.setActiveWorkerRunUuid(syncRun.getUuid());
            answer.setLatestCompletedProducerRunUuid(syncRun.getUuid());
            answer.setLatestCompletedCheckpointSequence(145L);
            answer.setLatestCompletedCheckpointRef("ftctl:" + plan.getUuid() + ":" + syncRun.getUuid() + ":145");
            answer.setLatestCompletedCheckpointState("READY");
            answer.setLatestCompletedSourceCheckpointAt("2026-07-21T07:16:57Z");
            answer.setLatestCompletedTargetDurableAt("2026-07-21T07:16:59Z");
            answer.setLatestCompletedBaselineGeneration(145L);
            answer.setLatestCompletedCycleToken(plan.getUuid() + ":145");
            answer.setLatestCompletedEffectiveMode("CBT_INCREMENTAL");
            answer.setLatestCompletedChangedBytes(4096L);
            answer.setLatestCompletedTargetWrittenBytes(4096L);
            return answer;
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(null);
        Mockito.when(drRunDao.findLatestByPlanId(plan.getId())).thenReturn(cleanupRun);
        Mockito.when(drRunDao.findByUuid(syncRun.getUuid())).thenReturn(syncRun);
        Mockito.when(drPlanDao.acquireInLockTable(plan.getId(), 10)).thenReturn(plan);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        ArgumentCaptor<DrPlanRuntimeVO> authorityCaptor = ArgumentCaptor.forClass(DrPlanRuntimeVO.class);
        Mockito.verify(drPlanRuntimeDao).persist(authorityCaptor.capture());
        Assert.assertEquals(syncRun.getUuid(), authorityCaptor.getValue().getEngineRunUuid());
        Assert.assertEquals(292L, authorityCaptor.getValue().getAuthoritySequence());
        Assert.assertEquals(Long.valueOf(145L), authorityCaptor.getValue().getLatestCompletedCycleSequence());
        ArgumentCaptor<DrRestorePointVO> restorePointCaptor = ArgumentCaptor.forClass(DrRestorePointVO.class);
        Mockito.verify(drRestorePointDao).persist(restorePointCaptor.capture());
        Assert.assertTrue(restorePointCaptor.getValue().getSourceSnapshotRef().contains(syncRun.getUuid()));
    }

    @Test
    public void acceptedFullReseedCycleCompletesAfterSchedulerAdvancesToNextIncrementalProducer() {
        DrPlanVO plan = new DrPlanVO("plan-41", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        ReflectionTestUtils.setField(plan, "id", 41L);
        DrRunVO run = new DrRunVO(41L, DrConstants.RUN_TYPE_SYNC);
        ReflectionTestUtils.setField(run, "id", 188L);
        run.setRequestJson("{\"mode\":\"FULL_RESEED\",\"forceFullReseed\":true}");

        DrSyncCycleVO acceptedCycle = new DrSyncCycleVO(41L, "scheduler-full-seed", 1140L);
        acceptedCycle.setRunId(147L);
        acceptedCycle.setCycleToken(plan.getUuid() + ":1140");
        acceptedCycle.setRequestedMode("FULL_RESEED");
        acceptedCycle.setEffectiveMode("FULL_RESEED");
        acceptedCycle.setState("READY");
        acceptedCycle.setCommitState("LOCAL_DURABLE");
        acceptedCycle.setCompleted(new Date());
        Mockito.when(drSyncCycleDao.findByPlanSequence(41L, 1140L)).thenReturn(acceptedCycle);

        FtctlDrStatusCommand command = new FtctlDrStatusCommand("plan-41", run.getUuid(),
                FtctlDrStatusCommand.StatusScope.OPERATION);
        FtctlDrStatusAnswer status = new FtctlDrStatusAnswer(command, true, "ok");
        status.setControlRequestRunUuid(run.getUuid());
        status.setTerminalAuthoritative(true);
        status.setTerminalSource("ENGINE_TERMINAL");
        status.setTransferCycleSequence(1140L);
        status.setTransferMode("FULL_RESEED");
        status.setLatestCompletedProducerRunUuid("scheduler-next-cycle");
        status.setLatestCompletedRequestedMode("CBT_INCREMENTAL");
        JsonObject runtime = new JsonObject();
        runtime.addProperty("control_request_run_uuid", run.getUuid());
        runtime.addProperty("terminal_authoritative", true);
        runtime.addProperty("transfer_cycle_sequence", 1140L);
        runtime.addProperty("transfer_mode", "FULL_RESEED");
        runtime.addProperty("latest_completed_producer_run_uuid", "scheduler-next-cycle");

        adapter.bindAcceptedCycleFromControlRequest(plan, run, status, runtime);

        Assert.assertEquals(Long.valueOf(1140L), run.getAcceptedCycleSequence());
        Assert.assertEquals(plan.getUuid() + ":1140", run.getAcceptedCycleToken());
        Mockito.verify(drRunDao).update(run.getId(), run);
        Assert.assertTrue(adapter.isAcceptedFullReseedCycleSatisfied(run, status, runtime));
    }
}
