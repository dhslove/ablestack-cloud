// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrReplicaDiskDao;
import com.cloud.dr.dao.DrTestDiskDao;
import com.cloud.dr.dao.DrTestSessionDao;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmService;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.UserVmDao;

@RunWith(MockitoJUnitRunner.class)
public class DrTargetMaterializationServiceImplTest {
    @Mock private DrPlanDao drPlanDao;
    @Mock private DrReplicaDao drReplicaDao;
    @Mock private DrReplicaDiskDao drReplicaDiskDao;
    @Mock private DrTestSessionDao drTestSessionDao;
    @Mock private DrTestDiskDao drTestDiskDao;
    @Mock private UserVmDao userVmDao;
    @Mock private UserVmService userVmService;
    @Mock private UserVmManager userVmManager;
    @Mock private VolumeDao volumeDao;
    @Mock private VolumeApiService volumeApiService;
    @Mock private AccountDao accountDao;
    @Mock private PrimaryDataStoreDao primaryDataStoreDao;
    @Mock private DrTargetResourceOwnershipService targetResourceOwnershipService;
    @InjectMocks private DrTargetMaterializationServiceImpl service;

    @Test
    public void retainOperationalVmDoesNotMutateCloudResources() {
        service.validateReleaseDisposition(42L, DrConstants.RELEASE_DISPOSITION_RETAIN_OPERATIONAL_VM);

        Assert.assertTrue(service.cleanupReleasedStandbyTarget(42L, 7L,
                DrConstants.RELEASE_DISPOSITION_RETAIN_OPERATIONAL_VM));
        Mockito.verifyNoInteractions(userVmService, userVmManager, volumeApiService);
    }

    @Test(expected = com.cloud.utils.exception.CloudRuntimeException.class)
    public void targetAuthorityCannotDeleteReplicaResources() {
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        Mockito.when(plan.getActiveSide()).thenReturn(DrConstants.AUTHORITY_SIDE_TARGET);
        Mockito.when(drPlanDao.findById(42L)).thenReturn(plan);

        service.validateReleaseDisposition(42L, DrConstants.RELEASE_DISPOSITION_DELETE_STANDBY_REPLICA);
    }

    @Test
    public void deleteStandbyReplicaRemovesOnlyOwnedStoppedTargetResources() throws Exception {
        long planId = 42L;
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        DrReplicaVO replica = Mockito.mock(DrReplicaVO.class);
        DrReplicaDiskVO disk = Mockito.mock(DrReplicaDiskVO.class);
        UserVmVO sourceVm = Mockito.mock(UserVmVO.class);
        UserVmVO targetVm = Mockito.mock(UserVmVO.class);
        AccountVO owner = Mockito.mock(AccountVO.class);
        VolumeVO volume = Mockito.mock(VolumeVO.class);

        Mockito.when(plan.getUuid()).thenReturn("release-plan");
        Mockito.when(plan.getActiveSide()).thenReturn(DrConstants.AUTHORITY_SIDE_SOURCE);
        Mockito.when(plan.getState()).thenReturn(DrConstants.PLAN_STATE_READY);
        Mockito.when(plan.getSourceVmId()).thenReturn(101L);
        Mockito.when(drPlanDao.findById(planId)).thenReturn(plan);
        Mockito.when(replica.getId()).thenReturn(11L);
        Mockito.when(replica.getTargetVmId()).thenReturn(91L);
        Mockito.when(drReplicaDao.listActiveByPlanId(planId)).thenReturn(Collections.singletonList(replica));
        Mockito.when(drReplicaDiskDao.listActiveByReplicaId(11L)).thenReturn(Collections.singletonList(disk));
        Mockito.when(userVmDao.findById(101L)).thenReturn(sourceVm);
        Mockito.when(sourceVm.getAccountId()).thenReturn(1L);
        Mockito.when(accountDao.findById(1L)).thenReturn(owner);
        Mockito.when(userVmDao.findById(91L)).thenReturn(targetVm);
        Mockito.when(targetVm.getId()).thenReturn(91L);
        Mockito.when(targetVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        Mockito.when(userVmManager.expunge(targetVm)).thenReturn(true);
        Mockito.when(disk.getId()).thenReturn(12L);
        Mockito.when(disk.getTargetVolumeId()).thenReturn(92L);
        Mockito.when(disk.getTargetDiskRef()).thenReturn("rbd/release-volume");
        Mockito.when(volumeDao.findById(92L)).thenReturn(volume);
        Mockito.when(volume.getId()).thenReturn(92L);

        Assert.assertTrue(service.cleanupReleasedStandbyTarget(planId, 7L,
                DrConstants.RELEASE_DISPOSITION_DELETE_STANDBY_REPLICA));

        InOrder order = Mockito.inOrder(userVmService, userVmManager, volumeApiService);
        order.verify(userVmService).destroyVm(91L, true);
        order.verify(userVmManager).expunge(targetVm);
        order.verify(volumeApiService).destroyVolume(92L, owner, true, true);
        Mockito.verify(targetResourceOwnershipService, Mockito.atLeastOnce()).assertVmOwnedBy(plan, replica, targetVm);
    }

    @Test
    public void ensureTargetPoweredOnReturnsDurableEvidenceForRunningReplica() throws Exception {
        long planId = 42L;
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        DrReplicaVO replica = Mockito.mock(DrReplicaVO.class);
        UserVmVO targetVm = Mockito.mock(UserVmVO.class);
        Mockito.when(drPlanDao.findById(planId)).thenReturn(plan);
        Mockito.when(replica.getTargetVmId()).thenReturn(91L);
        Mockito.when(drReplicaDao.listActiveByPlanId(planId)).thenReturn(Collections.singletonList(replica));
        Mockito.when(userVmDao.findById(91L)).thenReturn(targetVm);
        Mockito.when(targetVm.getId()).thenReturn(91L);
        Mockito.when(targetVm.getUuid()).thenReturn("target-uuid");
        Mockito.when(targetVm.getState()).thenReturn(VirtualMachine.State.Running);

        DrTargetPowerOnResult result = service.ensureTargetPoweredOn(planId);

        Assert.assertTrue(result.isReady());
        Assert.assertTrue(result.isAlreadyRunning());
        Assert.assertEquals("POWERED_ON", result.getPowerState());
        Assert.assertEquals("POWER_STATE_VALIDATED", result.getBootValidationState());
        Assert.assertNotNull(result.getPowerOnAt());
        Assert.assertNotNull(result.getBootValidatedAt());
        Mockito.verify(userVmManager, Mockito.never()).startVirtualMachine(Mockito.anyLong(), Mockito.any(),
                Mockito.anyMap(), Mockito.any());
    }

    @Test
    public void cleanupExpungesTestVmBeforeDestroyingItsVolume() throws Exception {
        long planId = 35L;
        long cleanupRunId = 65L;
        DrTestSessionVO session = Mockito.mock(DrTestSessionVO.class);
        DrTestDiskVO disk = Mockito.mock(DrTestDiskVO.class);
        UserVmVO testVm = Mockito.mock(UserVmVO.class);
        VolumeVO volume = Mockito.mock(VolumeVO.class);
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        AccountVO owner = Mockito.mock(AccountVO.class);
        StoragePoolVO pool = Mockito.mock(StoragePoolVO.class);

        Mockito.when(session.getId()).thenReturn(1L);
        Mockito.when(session.getState()).thenReturn(DrTestSessionState.ACTIVE);
        Mockito.when(session.getTargetVmId()).thenReturn(254L);
        Mockito.when(drTestSessionDao.findActiveByPlanId(planId)).thenReturn(session);
        Mockito.when(userVmDao.findById(254L)).thenReturn(testVm);
        Mockito.when(testVm.getId()).thenReturn(254L);
        Mockito.when(userVmManager.expunge(testVm)).thenReturn(true);
        Mockito.when(drPlanDao.findById(planId)).thenReturn(plan);
        Mockito.when(accountDao.findById(1L)).thenReturn(owner);
        Mockito.when(disk.getId()).thenReturn(2L);
        Mockito.when(disk.getArtifactRef()).thenReturn("rbd:rbd/test-clone");
        Mockito.when(disk.getTargetVolumeId()).thenReturn(483L);
        Mockito.when(drTestDiskDao.listActiveBySessionId(1L)).thenReturn(Collections.singletonList(disk));
        Mockito.when(volumeDao.findById(483L)).thenReturn(volume);
        Mockito.when(volume.getId()).thenReturn(483L);
        Mockito.when(volume.getPoolId()).thenReturn(1L);
        Mockito.when(volume.getPath()).thenReturn("rbd/test-clone");
        Mockito.when(primaryDataStoreDao.findById(1L)).thenReturn(pool);
        Mockito.when(pool.getPoolType()).thenReturn(com.cloud.storage.Storage.StoragePoolType.RBD);
        Mockito.when(pool.getPath()).thenReturn("rbd");

        Assert.assertTrue(service.cleanupTestTarget(planId, cleanupRunId));

        InOrder order = Mockito.inOrder(userVmService, userVmManager, volumeApiService);
        order.verify(userVmService).destroyVm(254L, true);
        order.verify(userVmManager).expunge(testVm);
        order.verify(volumeApiService).destroyVolume(483L, owner, true, true);
        Mockito.verify(volume).setPath("test-clone");
        Mockito.verify(volumeDao).update(483L, volume);
    }

    @Test
    public void cleanupNormalizesLegacySharedMountPointTestVolumePath() throws Exception {
        long planId = 36L;
        long cleanupRunId = 66L;
        DrTestSessionVO session = Mockito.mock(DrTestSessionVO.class);
        DrTestDiskVO disk = Mockito.mock(DrTestDiskVO.class);
        VolumeVO volume = Mockito.mock(VolumeVO.class);
        DrPlanVO plan = Mockito.mock(DrPlanVO.class);
        AccountVO owner = Mockito.mock(AccountVO.class);
        StoragePoolVO pool = Mockito.mock(StoragePoolVO.class);

        Mockito.when(session.getId()).thenReturn(3L);
        Mockito.when(session.getState()).thenReturn("FAILED");
        Mockito.when(drTestSessionDao.findActiveByPlanId(planId)).thenReturn(session);
        Mockito.when(drPlanDao.findById(planId)).thenReturn(plan);
        Mockito.when(accountDao.findById(1L)).thenReturn(owner);
        Mockito.when(disk.getId()).thenReturn(4L);
        Mockito.when(disk.getArtifactRef()).thenReturn(
                "/run/ablestack-vm-ftctl/dr-runtime/plans/plan/test-sessions/run-artifacts/test-disk.qcow2");
        Mockito.when(disk.getTargetVolumeId()).thenReturn(484L);
        Mockito.when(drTestDiskDao.listActiveBySessionId(3L)).thenReturn(Collections.singletonList(disk));
        Mockito.when(volumeDao.findById(484L)).thenReturn(volume);
        Mockito.when(volume.getId()).thenReturn(484L);
        Mockito.when(volume.getPoolId()).thenReturn(2L);
        Mockito.when(volume.getPath()).thenReturn(
                "/run/ablestack-vm-ftctl/dr-runtime/plans/plan/test-sessions/run-artifacts/test-disk.qcow2");
        Mockito.when(primaryDataStoreDao.findById(2L)).thenReturn(pool);
        Mockito.when(pool.getPoolType()).thenReturn(com.cloud.storage.Storage.StoragePoolType.SharedMountPoint);
        Mockito.when(pool.getPath()).thenReturn("/mnt/glue-gfs");

        Assert.assertTrue(service.cleanupTestTarget(planId, cleanupRunId));

        Mockito.verify(volume).setPath("test-disk.qcow2");
        Mockito.verify(volumeDao).update(484L, volume);
        Mockito.verify(volumeApiService).destroyVolume(484L, owner, true, true);
    }

    @Test
    public void testFailoverUsesPersistedDefaultNetworkWhenGroupRequestOmitsNetworkId() {
        DrResolvedTargetPlacement placement = new DrResolvedTargetPlacement();
        DrResolvedNetworkMapping mapped = new DrResolvedNetworkMapping();
        mapped.setNetworkId("network-uuid");
        mapped.setNetworkLocalId("204");
        mapped.setRole("default");
        mapped.setName("L2-Network");
        placement.addNetwork(mapped);

        Long resolved = service.applyTestNetwork(placement, "ISOLATED", null);

        Assert.assertEquals(Long.valueOf(204L), resolved);
        Assert.assertEquals(1, placement.getNetworks().size());
        Assert.assertEquals("network-uuid", placement.getNetworks().get(0).getNetworkId());
        Assert.assertEquals("204", placement.getNetworks().get(0).getNetworkLocalId());
    }

    @Test
    public void explicitTestNetworkOverridesPersistedPlanNetwork() {
        DrResolvedTargetPlacement placement = new DrResolvedTargetPlacement();
        DrResolvedNetworkMapping mapped = new DrResolvedNetworkMapping();
        mapped.setNetworkLocalId("204");
        placement.addNetwork(mapped);

        Long resolved = service.applyTestNetwork(placement, "ISOLATED", 205L);

        Assert.assertEquals(Long.valueOf(205L), resolved);
        Assert.assertEquals(1, placement.getNetworks().size());
        Assert.assertEquals("205", placement.getNetworks().get(0).getNetworkLocalId());
    }

    @Test
    public void failbackRunCannotOwnTargetMaterialization() {
        DrPlanVO plan = new DrPlanVO("failback-owner-guard", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide(DrConstants.AUTHORITY_SIDE_TARGET);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_FAILBACK);

        Assert.assertFalse(service.isMaterializationOwnerRun(plan, run));
    }

    @Test
    public void completedProtectionProducerCanOwnSourceSideReconciliation() {
        DrPlanVO plan = new DrPlanVO("sync-owner", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide(DrConstants.AUTHORITY_SIDE_SOURCE);
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_RECOVER_SYNC);

        Assert.assertTrue(service.isMaterializationOwnerRun(plan, run));
    }
}
