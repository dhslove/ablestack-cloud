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
package com.cloud.dr;

import java.util.Date;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.dr.adapter.DrAdapterRegistry;
import com.cloud.dr.adapter.DrReplicationEngine;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrSiteDao;

@RunWith(MockitoJUnitRunner.class)
public class DrPlanServiceImplTest {
    @Mock
    private DrPlanDao drPlanDao;
    @Mock
    private DrRunDao drRunDao;
    @Mock
    private DrSiteDao drSiteDao;
    @Mock
    private DrAdapterRegistry drAdapterRegistry;
    @Mock
    private DrReplicationEngine replicationEngine;

    @InjectMocks
    private DrPlanServiceImpl service;

    @Test
    public void vmwarePhase1AllowsOnlySyncBeforeTargetReady() {
        DrPlanVO plan = new DrPlanVO("kvm-to-vmware", 1L, 2L, DrConstants.DIRECTION_KVM_TO_VMWARE);
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setEngineType(DrConstants.ENGINE_TYPE_VMWARE_PHASE1);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_VMWARE_PHASE1);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drAdapterRegistry.getReplicationEngine(DrConstants.ENGINE_TYPE_VMWARE_PHASE1, DrConstants.ENGINE_BINDING_TYPE_VMWARE_PHASE1))
                .thenReturn(replicationEngine);

        Map<String, Boolean> eligibility = service.getActionEligibility(plan.getId());

        Assert.assertTrue(eligibility.get("sync"));
        Assert.assertFalse(eligibility.get("testFailover"));
        Assert.assertFalse(eligibility.get("failover"));
        Assert.assertFalse(eligibility.get("failback"));
        Assert.assertFalse(eligibility.get("reprotect"));
        Assert.assertFalse(eligibility.get("adoptReplica"));
        Assert.assertTrue(eligibility.get("migrationOnly"));
    }

    @Test
    public void ftctlEligibilityReflectsSupportedActionSurface() {
        DrPlanVO plan = new DrPlanVO("kvm-ftctl", 1L, 2L, "KVM_TO_KVM");
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drAdapterRegistry.getReplicationEngine(DrConstants.ENGINE_TYPE_FTCTL, DrConstants.ENGINE_BINDING_TYPE_FTCTL))
                .thenReturn(replicationEngine);

        Map<String, Boolean> eligibility = service.getActionEligibility(plan.getId());

        Assert.assertTrue(eligibility.get("sync"));
        Assert.assertFalse(eligibility.get("testFailover"));
        Assert.assertFalse(eligibility.get("stopTestFailover"));
        Assert.assertTrue(eligibility.get("failover"));
        Assert.assertTrue(eligibility.get("confirmFenceClear"));
        Assert.assertTrue(eligibility.get("failback"));
        Assert.assertTrue(eligibility.get("reprotect"));
        Assert.assertTrue(eligibility.get("adoptReplica"));
        Assert.assertFalse(eligibility.get("releaseProtection"));
        Assert.assertFalse(eligibility.get("cancelRun"));
    }

    @Test
    public void v2kIsMigrationOnlyAndDoesNotExposeDrActions() {
        DrPlanVO plan = new DrPlanVO("vmware-to-kvm", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setEngineType(DrConstants.ENGINE_TYPE_V2K);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_V2K);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drAdapterRegistry.getReplicationEngine(DrConstants.ENGINE_TYPE_V2K, DrConstants.ENGINE_BINDING_TYPE_V2K))
                .thenReturn(replicationEngine);

        Map<String, Boolean> eligibility = service.getActionEligibility(plan.getId());

        Assert.assertFalse(eligibility.get("sync"));
        Assert.assertFalse(eligibility.get("testFailover"));
        Assert.assertFalse(eligibility.get("failover"));
        Assert.assertFalse(eligibility.get("failback"));
        Assert.assertFalse(eligibility.get("reprotect"));
        Assert.assertFalse(eligibility.get("adoptReplica"));
        Assert.assertTrue(eligibility.get("migrationOnly"));
    }

    @Test
    public void ftctlDrEligibilityExposesContinuousDrActionsWhenTargetReady() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setTargetReadyAt(new Date());
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drAdapterRegistry.getReplicationEngine(DrConstants.ENGINE_TYPE_FTCTL_DR, DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR))
                .thenReturn(replicationEngine);

        Map<String, Boolean> eligibility = service.getActionEligibility(plan.getId());

        Assert.assertTrue(eligibility.get("sync"));
        Assert.assertTrue(eligibility.get("pauseSync"));
        Assert.assertFalse(eligibility.get("resumeSync"));
        Assert.assertTrue(eligibility.get("testFailover"));
        Assert.assertTrue(eligibility.get("failover"));
        Assert.assertFalse(eligibility.get("failback"));
        Assert.assertFalse(eligibility.get("reprotect"));
        Assert.assertFalse(eligibility.get("adoptReplica"));
        Assert.assertTrue(eligibility.get("releaseProtection"));
        Assert.assertFalse(eligibility.get("migrationOnly"));
    }

    @Test
    public void ftctlDrEligibilityAllowsFailbackAfterReprotectKeepsTargetActive() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setAdminState(DrConstants.ADMIN_STATE_ENABLED);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("TARGET");
        plan.setTargetReadyAt(new Date());
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);
        Mockito.when(drAdapterRegistry.getReplicationEngine(DrConstants.ENGINE_TYPE_FTCTL_DR, DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR))
                .thenReturn(replicationEngine);

        Map<String, Boolean> eligibility = service.getActionEligibility(plan.getId());

        Assert.assertTrue(eligibility.get("failback"));
        Assert.assertFalse(eligibility.get("reprotect"));
        Assert.assertTrue(eligibility.get("releaseProtection"));
    }

    @Test
    public void ftctlDrAllowsAllFourDirectionsAtPlanValidation() {
        assertFtctlDrPlanCanBeCreated(DrConstants.DIRECTION_KVM_TO_KVM, DrConstants.HYPERVISOR_TYPE_KVM, DrConstants.HYPERVISOR_TYPE_KVM);
        assertFtctlDrPlanCanBeCreated(DrConstants.DIRECTION_KVM_TO_VMWARE, DrConstants.HYPERVISOR_TYPE_KVM, DrConstants.HYPERVISOR_TYPE_VMWARE);
        assertFtctlDrPlanCanBeCreated(DrConstants.DIRECTION_VMWARE_TO_VMWARE, DrConstants.HYPERVISOR_TYPE_VMWARE, DrConstants.HYPERVISOR_TYPE_VMWARE);
        assertFtctlDrPlanCanBeCreated(DrConstants.DIRECTION_VMWARE_TO_KVM, DrConstants.HYPERVISOR_TYPE_VMWARE, DrConstants.HYPERVISOR_TYPE_KVM);
    }

    private void assertFtctlDrPlanCanBeCreated(String direction, String sourceHypervisor, String targetHypervisor) {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-" + direction, 1L, 2L, direction);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        Mockito.when(drSiteDao.findById(1L)).thenReturn(new DrSiteVO("source", "PRIMARY", sourceHypervisor));
        Mockito.when(drSiteDao.findById(2L)).thenReturn(new DrSiteVO("target", "SECONDARY", targetHypervisor));
        Mockito.when(drPlanDao.persist(plan)).thenReturn(plan);

        DrPlanVO created = service.createPlan(plan);

        Assert.assertEquals(DrConstants.ENGINE_TYPE_FTCTL_DR, created.getEngineType());
        Assert.assertEquals(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR, created.getEngineBindingType());
        Assert.assertEquals("SOURCE", created.getActiveSide());
    }
}
