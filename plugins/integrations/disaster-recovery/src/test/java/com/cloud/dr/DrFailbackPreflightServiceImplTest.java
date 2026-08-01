// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrSiteDao;

@RunWith(MockitoJUnitRunner.class)
public class DrFailbackPreflightServiceImplTest {
    @Mock private DrPlanDao drPlanDao;
    @Mock private DrSiteDao drSiteDao;
    @Mock private DrRestorePointDao drRestorePointDao;
    @Mock private DrSiteCredentialService drSiteCredentialService;
    @Mock private DrSourceIsolationPreflightService drSourceIsolationPreflightService;

    @InjectMocks
    private DrFailbackPreflightServiceImpl service;

    private DrPlanVO plan;
    private DrSiteVO sourceSite;
    private DrSiteVO targetSite;
    private DrRestorePointVO checkpoint;

    @Before
    public void setUp() {
        plan = new DrPlanVO("failback-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        sourceSite = site("source-vcenter", "VMWARE_DIRECT", DrConstants.HYPERVISOR_TYPE_VMWARE);
        targetSite = site("target-mold", "MOLD_KVM", DrConstants.HYPERVISOR_TYPE_KVM);
        checkpoint = new DrRestorePointVO(plan.getId(), "FTCTL_DR_CHECKPOINT");
        checkpoint.setTargetReadyAt(new Date());

        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drSiteDao.findById(1L)).thenReturn(sourceSite);
        Mockito.when(drSiteDao.findById(2L)).thenReturn(targetSite);
        Mockito.when(drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId())).thenReturn(checkpoint);
        Mockito.when(drSiteCredentialService.hasUsableCredential(sourceSite)).thenReturn(true);
        Mockito.when(drSiteCredentialService.hasUsableCredential(targetSite)).thenReturn(true);
        Mockito.when(drSourceIsolationPreflightService.validate(
                Mockito.eq(plan), Mockito.isNull(), Mockito.eq(DrConstants.RUN_TYPE_FAILBACK)))
                .thenReturn(DrSourceIsolationPreflightResult.success(
                        DrConstants.RUN_TYPE_FAILBACK, 3L, "ACKNOWLEDGED",
                        "POWERED_OFF", "POWERED_ON", "{\"ready\":true}"));
    }

    @Test
    public void derivesFailbackRouteFromRegisteredSites() {
        DrFailbackPreflightResult result = service.validate(plan.getId());

        Assert.assertTrue(result.isReady());
        Assert.assertSame(targetSite, result.getActiveSite());
        Assert.assertSame(sourceSite, result.getDestinationSite());
        Assert.assertSame(checkpoint, result.getCheckpoint());
    }

    @Test
    public void rejectsFailbackWhenTargetAuthorityIsNotCommitted() {
        plan.setActiveSide("SOURCE");

        DrFailbackPreflightResult result = service.validate(plan);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_FAILBACK_REQUIRES_TARGET_ACTIVE, result.getErrorCode());
    }

    @Test
    public void rejectsFailbackWhenDestinationCredentialIsMissing() {
        Mockito.when(drSiteCredentialService.hasUsableCredential(sourceSite)).thenReturn(false);

        DrFailbackPreflightResult result = service.validate(plan);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_FAILBACK_CREDENTIAL_NOT_READY, result.getErrorCode());
        Assert.assertEquals("MISSING", result.getDestinationCredentialState());
    }

    @Test
    public void propagatesInternalSourceIsolationFailure() {
        Mockito.when(drSourceIsolationPreflightService.validate(
                Mockito.eq(plan), Mockito.isNull(), Mockito.eq(DrConstants.RUN_TYPE_FAILBACK)))
                .thenReturn(DrSourceIsolationPreflightResult.failure(
                        DrConstants.ERROR_SOURCE_ISOLATION_NOT_READY,
                        "Source site is not isolated", DrConstants.RUN_TYPE_FAILBACK,
                        3L, "PENDING", "POWERED_ON", "POWERED_ON", null));

        DrFailbackPreflightResult result = service.validate(plan);

        Assert.assertFalse(result.isReady());
        Assert.assertEquals(DrConstants.ERROR_SOURCE_ISOLATION_NOT_READY, result.getErrorCode());
        Assert.assertNotNull(result.getTransitionPreflight());
    }

    private DrSiteVO site(String name, String siteType, String hypervisorType) {
        DrSiteVO site = new DrSiteVO(name, siteType, hypervisorType);
        site.setHealthState(DrConstants.HEALTH_CONNECTED);
        return site;
    }
}
