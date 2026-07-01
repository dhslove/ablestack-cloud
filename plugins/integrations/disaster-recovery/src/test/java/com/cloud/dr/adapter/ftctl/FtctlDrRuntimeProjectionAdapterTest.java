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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrEventVO;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrReplicaVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrRunStepVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;

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
    private DrRunDao drRunDao;
    @Mock
    private DrRunStepDao drRunStepDao;

    @InjectMocks
    private FtctlDrRuntimeProjectionAdapter adapter;

    @Test
    public void refreshPlanProjectionPersistsFtctlCheckpointRestorePoint() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setCoordinatorWorkerHostId(103L);

        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "READY", "scheduler-completed", 100,
                    "2026-07-01T01:05:00Z", "2026-07-01T01:05:02Z", 2,
                    9L, null, 0, "", "{\"checkpoint_sequence\":2,\"restore_points_path\":\"/run/restore-points.jsonl\"}");
        });
        Mockito.when(drRestorePointDao.findByPlanIdAndSourceSnapshotRef(plan.getId(), "ftctl:" + plan.getUuid() + ":2")).thenReturn(null);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, plan.getState());
        Assert.assertNotNull(plan.getTargetReadyAt());
        Assert.assertEquals(Integer.valueOf(2), plan.getTargetReadyRpoSeconds());
        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));

        ArgumentCaptor<DrRestorePointVO> restorePointCaptor = ArgumentCaptor.forClass(DrRestorePointVO.class);
        Mockito.verify(drRestorePointDao).persist(restorePointCaptor.capture());
        DrRestorePointVO restorePoint = restorePointCaptor.getValue();
        Assert.assertEquals(plan.getId(), restorePoint.getPlanId());
        Assert.assertEquals("ftctl:" + plan.getUuid() + ":2", restorePoint.getSourceSnapshotRef());
        Assert.assertEquals("FTCTL_DR_CHECKPOINT", restorePoint.getRestorePointType());
        Assert.assertEquals("READY", restorePoint.getState());
        Assert.assertNotNull(restorePoint.getTargetReadyAt());

        Mockito.verify(drEventDao).persist(Mockito.any(DrEventVO.class));
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
        Mockito.when(drRestorePointDao.findByPlanIdAndSourceSnapshotRef(plan.getId(), "ftctl:" + plan.getUuid() + ":3")).thenReturn(null);

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
        Mockito.verify(drEventDao).persist(Mockito.any(DrEventVO.class));
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
        Mockito.verify(drEventDao).persist(Mockito.any(DrEventVO.class));
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

        Mockito.verify(drPlanDao).update(Mockito.eq(plan.getId()), Mockito.same(plan));
        Mockito.verify(drReplicaDao).update(Mockito.eq(replica.getId()), Mockito.same(replica));
        Mockito.verify(drEventDao).persist(Mockito.any(DrEventVO.class));
    }

    @Test
    public void refreshPlanProjectionCompletesAcceptedFailoverRunWhenRuntimeFailedOver() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("SOURCE");
        plan.setCoordinatorWorkerHostId(103L);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILOVER);
        run.setState(DrConstants.RUN_STATE_ACCEPTED);

        String statusJson = "{\"state\":\"FAILED_OVER\",\"active_side\":\"TARGET\",\"failover_session_id\":\""
                + plan.getUuid() + ":run-failover\",\"failover_restore_point_ref\":\"ftctl:"
                + plan.getUuid() + ":7\",\"checkpoint_sequence\":7,\"target_power_state\":\"POWER_ON_DELEGATED\"}";
        Mockito.when(agentManager.easySend(Mockito.eq(103L), Mockito.any(FtctlDrStatusCommand.class))).thenAnswer(invocation -> {
            FtctlDrStatusCommand command = invocation.getArgument(1);
            return new FtctlDrStatusAnswer(command, true, "ok", plan.getUuid(), null,
                    "ok", "FAILED_OVER", "active-side-switch", 100,
                    "2026-07-01T03:10:00Z", "2026-07-01T03:10:02Z", 2,
                    13L, null, 0, "", statusJson);
        });
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(run);

        DrAdapterResult result = adapter.refreshPlanProjection(plan);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(DrConstants.RUN_STATE_SUCCEEDED, run.getState());
        Assert.assertEquals("runtime-projection", run.getCurrentStepName());
        Assert.assertNull(run.getErrorCode());
        Assert.assertNotNull(run.getCompleted());

        ArgumentCaptor<DrRunStepVO> stepCaptor = ArgumentCaptor.forClass(DrRunStepVO.class);
        Mockito.verify(drRunStepDao).persist(stepCaptor.capture());
        DrRunStepVO step = stepCaptor.getValue();
        Assert.assertEquals("runtime-projection", step.getStepName());
        Assert.assertEquals(DrConstants.STEP_STATE_SUCCEEDED, step.getState());
        Assert.assertEquals(Integer.valueOf(100), step.getProgress());

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
}
