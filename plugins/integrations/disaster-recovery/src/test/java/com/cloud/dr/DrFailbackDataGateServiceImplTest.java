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
        session.setReplicationDirection(DrConstants.DIRECTION_KVM_TO_VMWARE);
        session.setProviderPair(DrConstants.PROVIDER_PAIR_ABLESTACK_TO_VMWARE);
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
        session.setReplicationDirection(DrConstants.DIRECTION_VMWARE_TO_KVM);
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_ROUTE_DIRECTION_INVALID", result.getErrorCode());
    }

    @Test
    public void blocksForwardProviderEvidence() {
        session.setProviderPair(DrConstants.PROVIDER_PAIR_VMWARE_TO_ABLESTACK);
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_ROUTE_PROVIDER_INVALID", result.getErrorCode());
    }

    @Test
    public void acceptsLegacyProviderStyleDirection() {
        session.setReplicationDirection(DrConstants.PROVIDER_PAIR_ABLESTACK_TO_VMWARE);
        Assert.assertTrue(service.validate(plan, run, session).isReady());
    }

    @Test
    public void blocksMissingGuestCompatibilityEvidence() {
        session.setGuestCompatibilityState(null);
        DrFailbackDataGateResult result = service.validate(plan, run, session);
        Assert.assertFalse(result.isReady());
        Assert.assertEquals("DR_FAILBACK_DATA_EVIDENCE_INCOMPLETE", result.getErrorCode());
    }

    @Test
    public void distinguishesMissingBaselineFromNonDurableBaseline() {
        session.setBaselineGeneration(null);
        Assert.assertEquals("DR_FAILBACK_DATA_EVIDENCE_INCOMPLETE",
                service.validate(plan, run, session).getErrorCode());

        session.setBaselineGeneration(2L);
        session.setBaselineState("INVALID");
        Assert.assertEquals("DR_FAILBACK_BASELINE_NOT_DURABLE",
                service.validate(plan, run, session).getErrorCode());
    }
}
