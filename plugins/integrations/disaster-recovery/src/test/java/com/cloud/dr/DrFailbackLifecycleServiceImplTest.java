// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
// The ASF licenses this file to you under the Apache License, Version 2.0.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrFailbackSessionDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRunDao;
import com.google.gson.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class DrFailbackLifecycleServiceImplTest {
    @Mock private DrFailbackSessionDao drFailbackSessionDao;
    @Mock private DrPlanDao drPlanDao;
    @Mock private DrRunDao drRunDao;
    @Mock private DrReplicaDao drReplicaDao;
    @Mock private DrEventDao drEventDao;

    @Spy
    @InjectMocks
    private DrFailbackLifecycleServiceImpl service;

    private DrPlanVO plan;
    private DrRunVO run;
    private DrFailbackSessionVO session;

    @Before
    public void setUp() {
        plan = new DrPlanVO("failback-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_COMMIT_VERIFYING);
        plan.setActiveSide("SOURCE");
        run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        session = new DrFailbackSessionVO(plan.getId(), run.getId(), "session-a", "COMMIT_VERIFYING");
        session.setCheckpointSequence(7L);
        session.setCommitOutcome("UNKNOWN");
        session.setEngineAckState("UNKNOWN");

        Mockito.when(drFailbackSessionDao.findActiveByRunId(run.getId())).thenReturn(session);
        Mockito.when(drReplicaDao.listActiveByPlanId(plan.getId())).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    public void acknowledgedRuntimeConvergesWithoutRollback() {
        Mockito.doReturn(true).when(service).cloudPowerStatesMatch(plan);
        JsonObject runtime = runtime("ACKNOWLEDGED");
        runtime.addProperty("control_generation", 12);
        runtime.addProperty("control_ack_generation", 12);
        runtime.addProperty("scheduler_state", "RUNNING");

        DrFailbackSessionVO result = service.reconcile(plan, run, runtime);

        Assert.assertSame(session, result);
        Assert.assertEquals("PROTECTION_RESUMING", session.getState());
        Assert.assertEquals("ACKNOWLEDGED", session.getCommitOutcome());
        Assert.assertEquals("ACKNOWLEDGED", session.getEngineAckState());
        Assert.assertEquals(Long.valueOf(12), session.getSchedulerGeneration());
        Assert.assertEquals(Long.valueOf(12), session.getSchedulerAckGeneration());
        Assert.assertEquals(Long.valueOf(7), session.getResumeBaselineCheckpointSequence());
        Assert.assertEquals(Long.valueOf(8), session.getRequiredPostFailbackCheckpointSequence());
        Assert.assertNotNull(session.getProtectionResumeRequestedAt());
        Assert.assertEquals(DrConstants.PLAN_STATE_SYNCING, plan.getState());
        Assert.assertEquals("SOURCE", plan.getActiveSide());
        Mockito.verify(drEventDao).persist(Mockito.argThat(event ->
                "FAILBACK_AUTHORITY_COMMITTED".equals(event.getEventType())));
    }

    @Test
    public void unknownRuntimeRemainsCommitVerifying() {
        JsonObject runtime = runtime("UNKNOWN");
        runtime.addProperty("error_code", "DR_FAILBACK_COMMIT_ACK_TIMEOUT");

        DrFailbackSessionVO result = service.reconcile(plan, run, runtime);

        Assert.assertSame(session, result);
        Assert.assertEquals("COMMIT_VERIFYING", session.getState());
        Assert.assertEquals("UNKNOWN", session.getCommitOutcome());
        Assert.assertEquals(DrConstants.PLAN_STATE_COMMIT_VERIFYING, plan.getState());
        Mockito.verify(drEventDao, Mockito.never()).persist(Mockito.any());
    }

    @Test
    public void protectionResumeRequiresDurableNextCheckpointAndSessionAck() {
        plan.setActiveSide("SOURCE");
        session.setState("PROTECTION_RESUMING");
        session.setCommitOutcome("ACKNOWLEDGED");
        session.setEngineAckState("ACKNOWLEDGED");
        session.setTargetPowerState("POWERED_OFF");
        session.setSourcePowerState("POWERED_ON");
        session.setResumeBaselineCheckpointSequence(7L);
        session.setRequiredPostFailbackCheckpointSequence(8L);
        JsonObject runtime = runtime("UNKNOWN");
        runtime.addProperty("scheduler_state", "RUNNING");
        runtime.addProperty("scheduler_health", "HEALTHY");
        runtime.addProperty("latest_completed_checkpoint_sequence", 7L);

        Assert.assertFalse(service.protectionResumed(plan, session, runtime));

        runtime.addProperty("latest_completed_checkpoint_sequence", 8L);
        Assert.assertTrue(service.protectionResumed(plan, session, runtime));

        session.setEngineAckState("UNKNOWN");
        Assert.assertFalse(service.protectionResumed(plan, session, runtime));
    }

    private JsonObject runtime(String outcome) {
        JsonObject runtime = new JsonObject();
        runtime.addProperty("state", "SYNCING");
        runtime.addProperty("failback_session_id", "session-a");
        runtime.addProperty("failback_commit_outcome", outcome);
        runtime.addProperty("active_side", "SOURCE");
        runtime.addProperty("target_power_state", "POWERED_OFF");
        runtime.addProperty("source_power_state", "POWERED_ON");
        return runtime;
    }
}
