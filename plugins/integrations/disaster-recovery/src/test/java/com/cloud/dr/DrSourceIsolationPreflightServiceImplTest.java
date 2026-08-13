// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.vm.dao.UserVmDao;

@RunWith(MockitoJUnitRunner.class)
public class DrSourceIsolationPreflightServiceImplTest {
    @Mock private DrCutoverSessionDao drCutoverSessionDao;
    @Mock private DrReplicaDao drReplicaDao;
    @Mock private DrRunStepDao drRunStepDao;
    @Mock private UserVmDao userVmDao;
    @Mock private AgentManager agentManager;
    @Mock private DrCurrentAuthorityResolver drCurrentAuthorityResolver;

    @InjectMocks
    private DrSourceIsolationPreflightServiceImpl service;

    private DrPlanVO plan;

    @Before
    public void setUp() {
        plan = Mockito.mock(DrPlanVO.class);
        Mockito.when(plan.getId()).thenReturn(41L);
    }

    @Test
    public void acceptsFailedOperationStateWhenCommittedTargetAuthorityIsConsistent() {
        Mockito.when(drCurrentAuthorityResolver.resolve(plan)).thenReturn(authority("TARGET", true));
        DrCutoverSessionVO cutover = Mockito.mock(DrCutoverSessionVO.class);
        Mockito.when(cutover.getCloudAuthorityGeneration()).thenReturn(10L);
        Mockito.when(cutover.getCloudPromotionState()).thenReturn("PROMOTED");
        Mockito.when(cutover.getEngineAckState()).thenReturn("ACKNOWLEDGED");
        Mockito.when(cutover.getSourceFenceState()).thenReturn("ACKNOWLEDGED");
        Mockito.when(cutover.getSourcePowerState()).thenReturn("POWERED_OFF");
        Mockito.when(drCutoverSessionDao.findLatestActiveByPlanId(41L)).thenReturn(cutover);
        Mockito.when(drReplicaDao.listActiveByPlanId(41L)).thenReturn(Collections.emptyList());

        DrSourceIsolationPreflightResult result = service.validate(
                plan, null, DrConstants.RUN_TYPE_FAILBACK);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_TRANSITION_TARGET_NOT_SERVING, result.getErrorCode());
    }

    @Test
    public void rejectsInconsistentTargetAuthority() {
        Mockito.when(drCurrentAuthorityResolver.resolve(plan)).thenReturn(authority("TARGET", false));

        DrSourceIsolationPreflightResult result = service.validate(
                plan, null, DrConstants.RUN_TYPE_FAILBACK);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_TRANSITION_AUTHORITY_INVALID, result.getErrorCode());
    }

    private DrCurrentAuthorityProjection authority(String side, boolean consistent) {
        return new DrCurrentAuthorityProjection(side, "FAILED_OVER_UNPROTECTED", 10L,
                consistent, null, null, null);
    }
}
