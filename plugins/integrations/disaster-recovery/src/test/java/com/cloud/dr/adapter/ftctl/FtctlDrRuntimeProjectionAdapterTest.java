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

import java.util.Collections;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrCutoverSessionVO;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrPlanRuntimeVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrTargetMaterializationService;
import com.cloud.dr.DrTargetPowerOnResult;
import com.cloud.dr.DrTestSessionVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.dao.DrCutoverDiskDao;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.dr.dao.DrReplicaDiskDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.dr.dao.DrTestSessionDao;

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
    private DrCutoverDiskDao drCutoverDiskDao;
    @Mock
    private DrPlanRuntimeDao drPlanRuntimeDao;
    @Mock
    private DrSyncCycleDao drSyncCycleDao;
    @Mock
    private DrTestSessionDao drTestSessionDao;

    @InjectMocks
    private FtctlDrRuntimeProjectionAdapter adapter;

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

        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.argThat(event ->
                event != null && DrConstants.EVENT_PROJECTION_REFRESH.equals(event.getEventType())));
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
                + "\"manifest_sha256\":\"" + manifestSha256 + "\",\"target_disk_count\":1}";
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
        Assert.assertEquals(Integer.valueOf(40), step.getProgress());
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
}
