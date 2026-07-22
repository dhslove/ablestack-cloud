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
    @Mock private DrTestSessionDao drTestSessionDao;
    @Mock private DrTestDiskDao drTestDiskDao;
    @Mock private UserVmDao userVmDao;
    @Mock private UserVmService userVmService;
    @Mock private UserVmManager userVmManager;
    @Mock private VolumeDao volumeDao;
    @Mock private VolumeApiService volumeApiService;
    @Mock private AccountDao accountDao;
    @Mock private PrimaryDataStoreDao primaryDataStoreDao;
    @InjectMocks private DrTargetMaterializationServiceImpl service;

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
        Mockito.when(disk.getProvider()).thenReturn("RBD");
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
}
