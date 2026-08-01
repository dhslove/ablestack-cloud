// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DrFailbackDataGateServiceImplTest {
    private DrFailbackDataGateServiceImpl service;
    private DrPlanVO plan;
    private DrRunVO run;
    private DrFailbackSessionVO session;

    @Before
    public void setUp() {
        service = new DrFailbackDataGateServiceImpl();
        plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);
        session = new DrFailbackSessionVO(plan.getId(), 1L, "engine-session", "DATA_READY");
        session.setReplicationDirection("ABLESTACK_TO_VMWARE");
        session.setProviderPair("ABLESTACK_TO_VMWARE");
        session.setBaselineGeneration(2L);
        session.setBaselineState("LOCAL_DURABLE");
        session.setTrackerState("LOCAL_DURABLE");
        session.setWriterState("DURABLE");
        session.setTargetWritten(true);
        session.setWriteVerified(true);
        session.setGuestCompatibilityState("ORIGINAL_VMWARE_COMPATIBILITY_PRESERVED");
    }

    @Test
    public void acceptsDurableVerifiedReverseCheckpoint() {
        Assert.assertTrue(service.validate(plan, run, session).isReady());
    }

    @Test
    public void blocksUnverifiedVmwareWriter() {
        session.setWriteVerified(false);
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_TARGET_WRITE_UNVERIFIED", result.getErrorCode());
    }

    @Test
    public void blocksForwardDirectionEvidence() {
        session.setReplicationDirection("VMWARE_TO_ABLESTACK");
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_DIRECTION_MISMATCH", result.getErrorCode());
    }

    @Test
    public void blocksMissingGuestCompatibilityEvidence() {
        session.setGuestCompatibilityState(null);
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_GUEST_COMPATIBILITY_NOT_READY", result.getErrorCode());
    }
}
