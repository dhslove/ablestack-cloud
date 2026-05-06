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
package com.cloud.ftctl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.event.UsageEventVO;
import com.cloud.ftctl.dao.FtctlProtectionDao;
import com.cloud.ftctl.dao.FtctlProtectionVolumeDao;
import com.cloud.offering.DiskOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.Storage;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VmDetailConstants;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.UserVmService;
import org.apache.commons.lang3.StringUtils;

public class FtctlProtectionProvisioningServiceImpl extends ManagerBase implements FtctlProtectionProvisioningService {

    private static final String DETAIL_FTCTL_STANDBY_VM = "ftctl.standby.vm";
    private static final String DETAIL_FTCTL_PRIMARY_VM_ID = "ftctl.primary.vm.id";
    private static final String FTCTL_COMPUTE_OFFERING_UNIQUE_NAME = "ABLESTACK.FTCTL.Compute.Custom";
    private static final String FTCTL_ROOT_DISK_OFFERING_UNIQUE_NAME = "ABLESTACK.FTCTL.RootDisk.Custom";
    private static final String FTCTL_DATA_DISK_OFFERING_UNIQUE_NAME = "ABLESTACK.FTCTL.DataDisk.Custom";
    private static final String FTCTL_COMPUTE_OFFERING_NAME = "FTCTL Internal Custom Compute";
    private static final String FTCTL_ROOT_DISK_OFFERING_NAME = "FTCTL Internal Root Disk";
    private static final String FTCTL_DATA_DISK_OFFERING_NAME = "FTCTL Internal Data Disk";
    private static final long GIB_TO_BYTES = 1024L * 1024L * 1024L;
    private static final Set<String> PRIMARY_VM_DETAILS_TO_COPY = Set.of(
            VmDetailConstants.ROOT_DISK_CONTROLLER,
            VmDetailConstants.DATA_DISK_CONTROLLER,
            VmDetailConstants.KVM_SKIP_FORCE_DISK_CONTROLLER,
            ApiConstants.BootType.UEFI.toString(),
            ApiConstants.BootType.BIOS.toString(),
            VmDetailConstants.BOOT_MODE,
            VmDetailConstants.FIRMWARE,
            VmDetailConstants.TPM_VERSION,
            VmDetailConstants.VIRTUAL_TPM_ENABLED,
            VmDetailConstants.VIRTUAL_TPM_MODEL,
            VmDetailConstants.VIRTUAL_TPM_VERSION,
            VmDetailConstants.IO_POLICY,
            VmDetailConstants.IOTHREADS,
            VmDetailConstants.NIC_ADAPTER,
            VmDetailConstants.NIC_MULTIQUEUE_NUMBER,
            VmDetailConstants.NIC_PACKED_VIRTQUEUES_ENABLED,
            VmDetailConstants.KEYBOARD,
            VmDetailConstants.CPU_CORE_PER_SOCKET,
            VmDetailConstants.CPU_THREAD_PER_CORE,
            VmDetailConstants.SOUND,
            VmDetailConstants.VIDEO_HARDWARE,
            VmDetailConstants.VIDEO_HARDWARE_2,
            VmDetailConstants.VIDEO_RAM,
            VmDetailConstants.KVM_GUEST_OS_MACHINE_TYPE
    );

    @Inject
    private FtctlProtectionDao ftctlProtectionDao;
    @Inject
    private FtctlProtectionVolumeDao ftctlProtectionVolumeDao;
    @Inject
    private VolumeDao volumeDao;
    @Inject
    private VolumeApiService volumeApiService;
    @Inject
    private UserVmService userVmService;
    @Inject
    private UserVmDao userVmDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private NicDao nicDao;
    @Inject
    private AccountDao accountDao;
    @Inject
    private DiskOfferingDao diskOfferingDao;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;

    @Override
    public boolean start() {
        ensureFtctlHiddenOfferings();
        logger.info("FTCTL internal hidden offerings are ready");
        return true;
    }

    @Override
    public FtctlProtectionProvisioningContext prepareProtection(FtctlProtectionProvisioningRequest request) throws CloudRuntimeException {
        String provisioningBackend = StringUtils.defaultIfBlank(request.getProvisioningBackend(), BACKEND_LIBVIRT_MANAGED);
        if (BACKEND_LIBVIRT_MANAGED.equalsIgnoreCase(provisioningBackend)) {
            FtctlProtectionVO protection = persistProtectionRecord(request, BACKEND_LIBVIRT_MANAGED, STATE_READY, null);
            persistVolumeRecords(protection, request);
            return new FtctlProtectionProvisioningContext(BACKEND_LIBVIRT_MANAGED, STATE_READY, request.getSecondaryVmName(), null);
        }
        if (BACKEND_CLOUD_MANAGED.equalsIgnoreCase(provisioningBackend)) {
            FtctlProtectionVO protection = persistProtectionRecord(request, BACKEND_CLOUD_MANAGED, STATE_STANDBY_ALLOCATED, null);
            FtctlHiddenOfferings offerings = ensureFtctlHiddenOfferings();
            VolumeVO primaryRootVolume = findPrimaryRootVolume(request);
            VolumeVO standbyRootVolume = ensureCloudManagedStandbyRootVolume(request, protection, offerings.rootDiskOffering, primaryRootVolume);
            UserVmVO standbyVm = ensureCloudManagedStandbyVm(request, protection, offerings.computeOffering, standbyRootVolume, primaryRootVolume);
            protection.setSecondaryVmId(standbyVm.getId());
            protection.setSecondaryVmName(resolveSecondaryVmRuntimeName(standbyVm, request));
            protection.setProvisioningState(STATE_STANDBY_ALLOCATED);
            protection.markUpdated();
            ftctlProtectionDao.update(protection.getId(), protection);
            Map<Long, VolumeVO> standbyVolumesByPrimaryVolumeId = ensureCloudManagedStandbyDataVolumes(request, protection, offerings.dataDiskOffering, standbyVm);
            standbyVolumesByPrimaryVolumeId.put(primaryRootVolume.getId(), standbyRootVolume);
            persistVolumeRecords(protection, request, standbyVm, standbyRootVolume, standbyVolumesByPrimaryVolumeId);
            if (areStandbyVolumeDiskPathsReady(protection, request)) {
                protection.setProvisioningState(STATE_READY);
                protection.setLastError(null);
                protection.markUpdated();
                ftctlProtectionDao.update(protection.getId(), protection);
                return new FtctlProtectionProvisioningContext(BACKEND_CLOUD_MANAGED, STATE_READY, protection.getSecondaryVmName(), buildDiskMap(protection, request));
            }
            String message = String.format("FTCTL cloud-managed provisioning allocated standby VM %s but standby volume disk paths are not ready", standbyVm.getUuid());
            protection.setLastError(message);
            protection.markUpdated();
            ftctlProtectionDao.update(protection.getId(), protection);
            throw new CloudRuntimeException(message);
        }
        throw new CloudRuntimeException(String.format("Unsupported FTCTL provisioning backend: %s", provisioningBackend));
    }

    private FtctlProtectionVO persistProtectionRecord(FtctlProtectionProvisioningRequest request, String provisioningBackend,
                                                      String provisioningState, String lastError) {
        FtctlProtectionVO protection = ftctlProtectionDao.findActiveByPrimaryVmId(request.getPrimaryVm().getId());
        boolean existingRecord = protection != null;
        if (protection == null) {
            protection = new FtctlProtectionVO(request.getPrimaryVm().getId());
        }

        protection.setMode(request.getMode());
        protection.setBackendMode(request.getBackendMode());
        protection.setProvisioningBackend(provisioningBackend);
        protection.setProvisioningState(provisioningState);
        protection.setFencingPolicy(request.getFencingPolicy());
        protection.setPeerHostId(request.getPeerHostId());
        protection.setSecondaryVmName(request.getSecondaryVmName());
        protection.setLastError(lastError);
        if (request.getTargetStoragePool() != null) {
            protection.setTargetStoragePoolId(request.getTargetStoragePool().getId());
        } else {
            protection.setTargetStoragePoolId(null);
        }
        protection.markUpdated();

        if (existingRecord) {
            ftctlProtectionDao.update(protection.getId(), protection);
            return protection;
        }
        return ftctlProtectionDao.persist(protection);
    }

    private void persistVolumeRecords(FtctlProtectionVO protection, FtctlProtectionProvisioningRequest request) {
        persistVolumeRecords(protection, request, null, null);
    }

    private void persistVolumeRecords(FtctlProtectionVO protection, FtctlProtectionProvisioningRequest request, UserVmVO standbyVm, VolumeVO standbyRootVolume) {
        persistVolumeRecords(protection, request, standbyVm, standbyRootVolume, Collections.emptyMap());
    }

    private void persistVolumeRecords(FtctlProtectionVO protection, FtctlProtectionProvisioningRequest request, UserVmVO standbyVm, VolumeVO standbyRootVolume,
                                      Map<Long, VolumeVO> standbyVolumesByPrimaryVolumeId) {
        List<VolumeVO> primaryVolumes = volumeDao.findByInstance(request.getPrimaryVm().getId());
        if (primaryVolumes == null || primaryVolumes.isEmpty()) {
            return;
        }
        Map<String, VolumeVO> standbyVolumesByDiskLabel = mapVolumesByDiskLabel(standbyVm);
        for (VolumeVO primaryVolume : primaryVolumes) {
            if (!isProtectedVolumeType(primaryVolume)) {
                continue;
            }
            FtctlProtectionVolumeVO protectionVolume = ftctlProtectionVolumeDao.findActiveByProtectionIdAndPrimaryVolumeId(protection.getId(), primaryVolume.getId());
            boolean existingRecord = protectionVolume != null;
            if (protectionVolume == null) {
                protectionVolume = new FtctlProtectionVolumeVO(protection.getId(), primaryVolume.getId());
            }
            protectionVolume.setPrimaryDiskPath(primaryVolume.getPath());
            String diskLabel = resolveDiskLabel(primaryVolume);
            protectionVolume.setDiskLabel(diskLabel);
            VolumeVO standbyVolume = standbyVolumesByPrimaryVolumeId.get(primaryVolume.getId());
            if (standbyVolume == null) {
                standbyVolume = standbyVolumesByDiskLabel.get(diskLabel);
            }
            if (standbyVolume == null && primaryVolume.getVolumeType() == Volume.Type.ROOT) {
                standbyVolume = standbyRootVolume;
            }
            if (standbyVolume != null) {
                protectionVolume.setSecondaryVolumeId(standbyVolume.getId());
                protectionVolume.setSecondaryDiskPath(standbyVolume.getPath());
            }
            protectionVolume.markUpdated();
            if (existingRecord) {
                ftctlProtectionVolumeDao.update(protectionVolume.getId(), protectionVolume);
            } else {
                ftctlProtectionVolumeDao.persist(protectionVolume);
            }
        }
    }

    private String resolveDiskLabel(VolumeVO volume) {
        String volumeType = "volume";
        if (volume.getVolumeType() == Volume.Type.ROOT) {
            volumeType = "root";
        } else if (volume.getVolumeType() == Volume.Type.DATADISK) {
            volumeType = "data";
        } else if (volume.getVolumeType() != null) {
            volumeType = volume.getVolumeType().name().toLowerCase(Locale.ROOT);
        }
        String deviceId = volume.getDeviceId() != null ? String.valueOf(volume.getDeviceId()) : String.valueOf(volume.getId());
        return String.format("%s-%s", volumeType, deviceId);
    }

    private Map<String, VolumeVO> mapVolumesByDiskLabel(UserVmVO standbyVm) {
        if (standbyVm == null) {
            return Collections.emptyMap();
        }
        List<VolumeVO> standbyVolumes = volumeDao.findByInstance(standbyVm.getId());
        if (standbyVolumes == null || standbyVolumes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, VolumeVO> result = new HashMap<>();
        for (VolumeVO standbyVolume : standbyVolumes) {
            result.put(resolveDiskLabel(standbyVolume), standbyVolume);
        }
        return result;
    }

    private UserVmVO ensureCloudManagedStandbyVm(FtctlProtectionProvisioningRequest request, FtctlProtectionVO protection,
                                                 ServiceOfferingVO computeOffering, VolumeVO standbyRootVolume, VolumeVO primaryRootVolume) {
        UserVmVO existingStandby = findExistingStandbyVm(request, protection);
        if (existingStandby != null) {
            return existingStandby;
        }
        return createCloudManagedStandbyVm(request, computeOffering, standbyRootVolume, primaryRootVolume);
    }

    private UserVmVO findExistingStandbyVm(FtctlProtectionProvisioningRequest request, FtctlProtectionVO protection) {
        if (protection.getSecondaryVmId() != null) {
            UserVmVO standbyVm = userVmDao.findById(protection.getSecondaryVmId());
            if (standbyVm != null) {
                return standbyVm;
            }
        }
        String standbyVmName = resolveSecondaryVmName(request);
        VMInstanceVO vm = vmInstanceDao.findVMByHostNameInZone(standbyVmName, request.getPrimaryVm().getDataCenterId());
        if (vm == null) {
            return null;
        }
        return userVmDao.findById(vm.getId());
    }

    private UserVmVO createCloudManagedStandbyVm(FtctlProtectionProvisioningRequest request, ServiceOfferingVO computeOffering,
                                                 VolumeVO standbyRootVolume, VolumeVO primaryRootVolume) {
        UserVmVO primaryVm = request.getPrimaryVm();
        AccountVO owner = accountDao.findById(primaryVm.getAccountId());
        if (owner == null) {
            throw new CloudRuntimeException(String.format("Unable to find owner account for primary VM %s", primaryVm.getUuid()));
        }
        Map<String, String> standbyVmDetails = buildStandbyVmDetails(primaryVm);
        standbyVmDetails.put(VmDetailConstants.ROOT_DISK_SIZE, String.valueOf(bytesToGiBRoundedUp(primaryRootVolume.getSize())));
        FtctlStandbyDeployVMVolumeCmd deployCmd = new FtctlStandbyDeployVMVolumeCmd(
                owner.getId(),
                owner.getAccountName(),
                owner.getDomainId(),
                primaryVm.getDataCenterId(),
                computeOffering.getId(),
                resolveSecondaryVmName(request),
                resolveSecondaryVmName(request),
                listPrimaryVmNetworkIds(primaryVm),
                request.getPeerHostId(),
                primaryVm.getHypervisorType(),
                standbyRootVolume.getId(),
                standbyVmDetails);
        try {
            UserVm standbyVm = userVmService.createVirtualMachineVolume(deployCmd);
            if (standbyVm == null) {
                throw new CloudRuntimeException(String.format("Failed to allocate FTCTL standby VM for primary VM %s", primaryVm.getUuid()));
            }
            UserVmVO standbyVmVo = userVmDao.findById(standbyVm.getId());
            if (standbyVmVo == null) {
                throw new CloudRuntimeException(String.format("Unable to reload allocated FTCTL standby VM %s", standbyVm.getUuid()));
            }
            return standbyVmVo;
        } catch (InsufficientCapacityException | ResourceUnavailableException | ConcurrentOperationException |
                 ResourceAllocationException e) {
            throw new CloudRuntimeException(String.format("Failed to allocate FTCTL standby VM for primary VM %s: %s", primaryVm.getUuid(), e.getMessage()), e);
        }
    }

    private VolumeVO ensureCloudManagedStandbyRootVolume(FtctlProtectionProvisioningRequest request, FtctlProtectionVO protection,
                                                         DiskOfferingVO rootDiskOffering, VolumeVO primaryRootVolume) {
        FtctlProtectionVolumeVO protectionVolume = ftctlProtectionVolumeDao.findActiveByProtectionIdAndPrimaryVolumeId(protection.getId(), primaryRootVolume.getId());
        if (protectionVolume != null && protectionVolume.getSecondaryVolumeId() != null) {
            VolumeVO existingVolume = volumeDao.findById(protectionVolume.getSecondaryVolumeId());
            if (existingVolume != null) {
                return ensureStandbyRootVolumeMetadata(existingVolume);
            }
        }
        StoragePoolVO targetStoragePool = request.getTargetStoragePool();
        if (targetStoragePool == null) {
            throw new CloudRuntimeException("FTCTL cloud-managed provisioning requires a target storage pool for standby root volume");
        }
        VolumeVO standbyRootVolume = createCloudManagedStandbyVolume(request, rootDiskOffering, String.format("%s-root", resolveSecondaryVmName(request)), primaryRootVolume);
        return ensureStandbyRootVolumeMetadata(standbyRootVolume);
    }

    private VolumeVO ensureStandbyRootVolumeMetadata(VolumeVO standbyRootVolume) {
        if (standbyRootVolume == null) {
            throw new CloudRuntimeException("FTCTL cloud-managed provisioning requires a standby root volume");
        }
        if (standbyRootVolume.getVolumeType() == Volume.Type.ROOT && Long.valueOf(0L).equals(standbyRootVolume.getDeviceId())) {
            return standbyRootVolume;
        }
        standbyRootVolume.setVolumeType(Volume.Type.ROOT);
        standbyRootVolume.setDeviceId(0L);
        volumeDao.update(standbyRootVolume.getId(), standbyRootVolume);
        VolumeVO reloadedVolume = volumeDao.findById(standbyRootVolume.getId());
        if (reloadedVolume == null) {
            throw new CloudRuntimeException(String.format("Unable to reload FTCTL standby root volume %s after metadata update", standbyRootVolume.getUuid()));
        }
        if (reloadedVolume.getVolumeType() != Volume.Type.ROOT) {
            throw new CloudRuntimeException(String.format("FTCTL standby root volume %s is not marked as ROOT", reloadedVolume.getUuid()));
        }
        return reloadedVolume;
    }

    private Map<Long, VolumeVO> ensureCloudManagedStandbyDataVolumes(FtctlProtectionProvisioningRequest request, FtctlProtectionVO protection,
                                                                      DiskOfferingVO dataDiskOffering, UserVmVO standbyVm) {
        List<VolumeVO> primaryVolumes = volumeDao.findByInstance(request.getPrimaryVm().getId());
        if (primaryVolumes == null || primaryVolumes.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, VolumeVO> standbyVolumesByPrimaryVolumeId = new HashMap<>();
        Map<String, VolumeVO> standbyVolumesByDiskLabel = mapVolumesByDiskLabel(standbyVm);
        for (VolumeVO primaryVolume : primaryVolumes) {
            if (primaryVolume.getVolumeType() != Volume.Type.DATADISK) {
                continue;
            }
            VolumeVO standbyVolume = ensureCloudManagedStandbyDataVolume(request, protection, dataDiskOffering, standbyVm, primaryVolume, standbyVolumesByDiskLabel);
            standbyVolumesByPrimaryVolumeId.put(primaryVolume.getId(), standbyVolume);
        }
        return standbyVolumesByPrimaryVolumeId;
    }

    private VolumeVO ensureCloudManagedStandbyDataVolume(FtctlProtectionProvisioningRequest request, FtctlProtectionVO protection, DiskOfferingVO dataDiskOffering,
                                                         UserVmVO standbyVm, VolumeVO primaryVolume, Map<String, VolumeVO> standbyVolumesByDiskLabel) {
        FtctlProtectionVolumeVO protectionVolume = ftctlProtectionVolumeDao.findActiveByProtectionIdAndPrimaryVolumeId(protection.getId(), primaryVolume.getId());
        if (protectionVolume != null && protectionVolume.getSecondaryVolumeId() != null) {
            VolumeVO existingVolume = volumeDao.findById(protectionVolume.getSecondaryVolumeId());
            if (existingVolume != null) {
                return attachStandbyDataVolumeIfNeeded(standbyVm, primaryVolume, existingVolume);
            }
        }
        VolumeVO existingByLabel = standbyVolumesByDiskLabel.get(resolveDiskLabel(primaryVolume));
        if (existingByLabel != null) {
            return attachStandbyDataVolumeIfNeeded(standbyVm, primaryVolume, existingByLabel);
        }
        VolumeVO standbyVolume = createCloudManagedStandbyVolume(request, dataDiskOffering,
                String.format("%s-%s", resolveSecondaryVmName(request), resolveDiskLabel(primaryVolume)), primaryVolume);
        return attachStandbyDataVolumeIfNeeded(standbyVm, primaryVolume, standbyVolume);
    }

    private VolumeVO attachStandbyDataVolumeIfNeeded(UserVmVO standbyVm, VolumeVO primaryVolume, VolumeVO standbyVolume) {
        if (standbyVolume.getInstanceId() != null && standbyVolume.getInstanceId().equals(standbyVm.getId())) {
            return standbyVolume;
        }
        Long deviceId = primaryVolume.getDeviceId();
        if (deviceId == null || deviceId == 0L) {
            throw new CloudRuntimeException(String.format("Unable to determine data disk device id for primary volume %s", primaryVolume.getUuid()));
        }
        volumeDao.attachVolume(standbyVolume.getId(), standbyVm.getId(), deviceId);
        VolumeVO reloadedVolume = volumeDao.findById(standbyVolume.getId());
        return reloadedVolume != null ? reloadedVolume : standbyVolume;
    }

    private VolumeVO createCloudManagedStandbyVolume(FtctlProtectionProvisioningRequest request, DiskOfferingVO diskOffering, String volumeName, VolumeVO primaryVolume) {
        StoragePoolVO targetStoragePool = request.getTargetStoragePool();
        if (targetStoragePool == null) {
            throw new CloudRuntimeException("FTCTL cloud-managed provisioning requires a target storage pool for standby volume");
        }
        AccountVO owner = accountDao.findById(request.getPrimaryVm().getAccountId());
        if (owner == null) {
            throw new CloudRuntimeException(String.format("Unable to find owner account for primary VM %s", request.getPrimaryVm().getUuid()));
        }
        FtctlCreateVolumeCmd createVolumeCmd = new FtctlCreateVolumeCmd(
                owner.getId(),
                owner.getAccountName(),
                owner.getDomainId(),
                diskOffering.getId(),
                volumeName,
                bytesToGiBRoundedUp(primaryVolume.getSize()),
                primaryVolume.getMinIops(),
                primaryVolume.getMaxIops(),
                request.getPrimaryVm().getDataCenterId(),
                targetStoragePool.getId(),
                true);
        try {
            Volume allocatedVolume = volumeApiService.allocVolume(createVolumeCmd);
            if (allocatedVolume == null) {
                throw new CloudRuntimeException(String.format("Failed to allocate FTCTL standby volume for primary VM %s", request.getPrimaryVm().getUuid()));
            }
            createVolumeCmd.setEntityId(allocatedVolume.getId());
            createVolumeCmd.setEntityUuid(allocatedVolume.getUuid());
            Volume createdVolume = volumeApiService.createVolume(createVolumeCmd);
            VolumeVO standbyVolume = volumeDao.findById(createdVolume != null ? createdVolume.getId() : allocatedVolume.getId());
            if (standbyVolume == null) {
                throw new CloudRuntimeException(String.format("Unable to reload FTCTL standby volume %s", allocatedVolume.getUuid()));
            }
            return standbyVolume;
        } catch (ResourceAllocationException e) {
            throw new CloudRuntimeException(String.format("Failed to allocate FTCTL standby volume for primary VM %s: %s", request.getPrimaryVm().getUuid(), e.getMessage()), e);
        }
    }

    private boolean areStandbyVolumeDiskPathsReady(FtctlProtectionVO protection, FtctlProtectionProvisioningRequest request) {
        List<VolumeVO> primaryVolumes = volumeDao.findByInstance(request.getPrimaryVm().getId());
        if (primaryVolumes == null || primaryVolumes.isEmpty()) {
            return false;
        }
        for (VolumeVO primaryVolume : primaryVolumes) {
            if (!isProtectedVolumeType(primaryVolume)) {
                continue;
            }
            FtctlProtectionVolumeVO protectionVolume = ftctlProtectionVolumeDao.findActiveByProtectionIdAndPrimaryVolumeId(protection.getId(), primaryVolume.getId());
            if (protectionVolume == null || protectionVolume.getSecondaryVolumeId() == null ||
                    StringUtils.isBlank(protectionVolume.getPrimaryDiskPath()) || StringUtils.isBlank(protectionVolume.getSecondaryDiskPath())) {
                return false;
            }
        }
        return true;
    }

    private String buildDiskMap(FtctlProtectionVO protection, FtctlProtectionProvisioningRequest request) {
        List<VolumeVO> primaryVolumes = volumeDao.findByInstance(request.getPrimaryVm().getId());
        if (primaryVolumes == null || primaryVolumes.isEmpty()) {
            return null;
        }
        Map<Long, FtctlProtectionVolumeVO> protectionVolumesByPrimaryVolumeId = new HashMap<>();
        List<FtctlProtectionVolumeVO> protectionVolumes = ftctlProtectionVolumeDao.listActiveByProtectionId(protection.getId());
        if (protectionVolumes != null) {
            for (FtctlProtectionVolumeVO protectionVolume : protectionVolumes) {
                protectionVolumesByPrimaryVolumeId.put(protectionVolume.getPrimaryVolumeId(), protectionVolume);
            }
        }
        List<String> entries = new ArrayList<>();
        for (VolumeVO volume : primaryVolumes.stream()
                .filter(this::isProtectedVolumeType)
                .sorted((left, right) -> Long.compare(resolveDeviceIdForSort(left), resolveDeviceIdForSort(right)))
                .collect(java.util.stream.Collectors.toList())) {
            entries.add(buildDiskMapEntry(request, volume, protectionVolumesByPrimaryVolumeId.get(volume.getId())));
        }
        if (entries.isEmpty()) {
            throw new CloudRuntimeException(String.format("FTCTL cloud-managed provisioning did not build a disk map for primary VM %s", request.getPrimaryVm().getUuid()));
        }
        return StringUtils.join(entries, ";");
    }

    private boolean isProtectedVolumeType(VolumeVO volume) {
        return volume != null && (volume.getVolumeType() == Volume.Type.ROOT || volume.getVolumeType() == Volume.Type.DATADISK);
    }

    private String buildDiskMapEntry(FtctlProtectionProvisioningRequest request, VolumeVO primaryVolume, FtctlProtectionVolumeVO protectionVolume) {
        if (protectionVolume == null || StringUtils.isBlank(protectionVolume.getSecondaryDiskPath())) {
            throw new CloudRuntimeException(String.format("FTCTL cloud-managed provisioning is missing a standby volume path for primary volume %s", primaryVolume.getUuid()));
        }
        String secondaryDiskPath = resolveFtctlSecondaryDiskPath(request.getTargetStoragePool(), protectionVolume.getSecondaryDiskPath());
        if (StringUtils.isBlank(secondaryDiskPath)) {
            throw new CloudRuntimeException(String.format("FTCTL cloud-managed provisioning resolved an empty standby path for primary volume %s", primaryVolume.getUuid()));
        }
        return String.format("%s=%s", resolveKvmDiskTarget(request.getPrimaryVm(), primaryVolume), secondaryDiskPath);
    }

    private String resolveFtctlSecondaryDiskPath(StoragePoolVO targetStoragePool, String secondaryDiskPath) {
        String normalizedPath = StringUtils.trimToNull(secondaryDiskPath);
        if (normalizedPath == null || StringUtils.startsWithAny(normalizedPath, "/dev/", "rbd:")) {
            return normalizedPath;
        }
        if (targetStoragePool == null) {
            return normalizedPath;
        }
        if (!Storage.StoragePoolType.RBD.equals(targetStoragePool.getPoolType())) {
            if (StringUtils.startsWith(normalizedPath, "/") || StringUtils.isBlank(targetStoragePool.getPath())) {
                return normalizedPath;
            }
            return String.format("%s/%s", StringUtils.removeEnd(targetStoragePool.getPath(), "/"), StringUtils.removeStart(normalizedPath, "/"));
        }

        String poolName = resolveRbdPoolName(targetStoragePool.getPath());
        if (StringUtils.isBlank(poolName)) {
            throw new CloudRuntimeException(String.format("Unable to resolve RBD pool name for FTCTL target storage pool %s", targetStoragePool.getId()));
        }
        String imageName = StringUtils.stripStart(normalizedPath, "/");
        String poolPrefix = poolName + "/";
        if (imageName.startsWith(poolPrefix)) {
            imageName = imageName.substring(poolPrefix.length());
        }
        if (StringUtils.isBlank(imageName)) {
            throw new CloudRuntimeException(String.format("Unable to resolve RBD image name for FTCTL secondary disk path %s", secondaryDiskPath));
        }
        return String.format("/dev/rbd/%s/%s", poolName, imageName);
    }

    private String resolveRbdPoolName(String poolPath) {
        String normalizedPath = StringUtils.stripEnd(StringUtils.trimToEmpty(poolPath), "/");
        if (normalizedPath.startsWith("rbd://")) {
            normalizedPath = normalizedPath.substring("rbd://".length());
        }
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalizedPath = normalizedPath.substring(lastSlash + 1);
        }
        return normalizedPath;
    }

    private long resolveDeviceIdForSort(VolumeVO volume) {
        return volume.getDeviceId() != null ? volume.getDeviceId() : Long.MAX_VALUE;
    }

    private String resolveKvmDiskTarget(UserVmVO primaryVm, VolumeVO volume) {
        long deviceId = resolveDeviceIdForSort(volume);
        if (deviceId < 0 || deviceId > 25) {
            return String.format("%s%s", resolveKvmDiskPrefix(primaryVm, volume), deviceId);
        }
        return String.format("%s%c", resolveKvmDiskPrefix(primaryVm, volume), (char) ('a' + deviceId));
    }

    private String resolveKvmDiskPrefix(UserVmVO primaryVm, VolumeVO volume) {
        String controller = resolveDiskController(primaryVm, volume);
        String normalizedController = StringUtils.defaultString(controller).toLowerCase(Locale.ROOT);
        if (normalizedController.contains("scsi") || normalizedController.contains("sata")) {
            return "sd";
        }
        if (normalizedController.contains("ide")) {
            return "hd";
        }
        return "vd";
    }

    private String resolveDiskController(UserVmVO primaryVm, VolumeVO volume) {
        if (primaryVm == null || volume == null) {
            return null;
        }
        userVmDao.loadDetails(primaryVm);
        Map<String, String> details = primaryVm.getDetails();
        if (details == null || details.isEmpty()) {
            return null;
        }
        if (volume.getVolumeType() == Volume.Type.ROOT) {
            return details.get(VmDetailConstants.ROOT_DISK_CONTROLLER);
        }
        if (volume.getVolumeType() == Volume.Type.DATADISK) {
            return StringUtils.defaultIfBlank(details.get(VmDetailConstants.DATA_DISK_CONTROLLER),
                    details.get(VmDetailConstants.ROOT_DISK_CONTROLLER));
        }
        return StringUtils.defaultIfBlank(details.get(VmDetailConstants.DATA_DISK_CONTROLLER),
                details.get(VmDetailConstants.ROOT_DISK_CONTROLLER));
    }

    private VolumeVO findPrimaryRootVolume(FtctlProtectionProvisioningRequest request) {
        List<VolumeVO> primaryVolumes = volumeDao.findByInstance(request.getPrimaryVm().getId());
        if (primaryVolumes != null) {
            for (VolumeVO primaryVolume : primaryVolumes) {
                if (primaryVolume.getVolumeType() == Volume.Type.ROOT) {
                    return primaryVolume;
                }
            }
        }
        throw new CloudRuntimeException(String.format("Unable to find primary root volume for FTCTL primary VM %s", request.getPrimaryVm().getUuid()));
    }

    private long bytesToGiBRoundedUp(Long sizeInBytes) {
        if (sizeInBytes == null || sizeInBytes <= 0) {
            return 1L;
        }
        return Math.max(1L, (sizeInBytes + GIB_TO_BYTES - 1L) / GIB_TO_BYTES);
    }

    private String resolveSecondaryVmRuntimeName(UserVmVO standbyVm, FtctlProtectionProvisioningRequest request) {
        if (standbyVm != null) {
            return StringUtils.defaultIfBlank(standbyVm.getInstanceName(),
                    StringUtils.defaultIfBlank(standbyVm.getDisplayName(), resolveSecondaryVmName(request)));
        }
        return resolveSecondaryVmName(request);
    }

    private String resolveSecondaryVmName(FtctlProtectionProvisioningRequest request) {
        return StringUtils.defaultIfBlank(request.getSecondaryVmName(), String.format("%s-standby", request.getPrimaryVm().getHostName()));
    }

    private List<Long> listPrimaryVmNetworkIds(UserVmVO primaryVm) {
        List<NicVO> nics = nicDao.listByVmIdOrderByDeviceId(primaryVm.getId());
        if (nics == null || nics.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> networkIds = new LinkedHashSet<>();
        for (NicVO nic : nics) {
            networkIds.add(nic.getNetworkId());
        }
        return List.copyOf(networkIds);
    }

    private Map<String, String> buildStandbyVmDetails(UserVmVO primaryVm) {
        Map<String, String> details = new HashMap<>();
        details.putAll(resolvePrimaryVmDetailsToCopy(primaryVm));
        details.put(DETAIL_FTCTL_STANDBY_VM, "true");
        details.put(DETAIL_FTCTL_PRIMARY_VM_ID, String.valueOf(primaryVm.getId()));
        details.putAll(resolveStandbyComputeDetails(primaryVm));
        return details;
    }

    private Map<String, String> resolvePrimaryVmDetailsToCopy(UserVmVO primaryVm) {
        userVmDao.loadDetails(primaryVm);
        Map<String, String> primaryDetails = primaryVm.getDetails();
        if (primaryDetails == null || primaryDetails.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> details = new HashMap<>();
        for (String key : PRIMARY_VM_DETAILS_TO_COPY) {
            String value = primaryDetails.get(key);
            if (StringUtils.isNotBlank(value)) {
                details.put(key, value);
            }
        }
        return details;
    }

    private Map<String, String> resolveStandbyComputeDetails(UserVmVO primaryVm) {
        ServiceOfferingVO primaryOffering = serviceOfferingDao.findById(primaryVm.getServiceOfferingId());
        if (primaryOffering == null) {
            throw new CloudRuntimeException(String.format("Unable to find primary VM service offering for VM %s", primaryVm.getUuid()));
        }
        Map<String, String> details = new HashMap<>();
        if (primaryOffering.getCpu() != null) {
            details.put(UsageEventVO.DynamicParameters.cpuNumber.name(), String.valueOf(primaryOffering.getCpu()));
        }
        if (primaryOffering.getSpeed() != null) {
            details.put(UsageEventVO.DynamicParameters.cpuSpeed.name(), String.valueOf(primaryOffering.getSpeed()));
        }
        if (primaryOffering.getRamSize() != null) {
            details.put(UsageEventVO.DynamicParameters.memory.name(), String.valueOf(primaryOffering.getRamSize()));
        }
        if (details.size() == 3) {
            return details;
        }
        userVmDao.loadDetails(primaryVm);
        Map<String, String> primaryDetails = primaryVm.getDetails();
        putIfMissing(details, UsageEventVO.DynamicParameters.cpuNumber.name(), primaryDetails, ApiConstants.CPU_NUMBER);
        putIfMissing(details, UsageEventVO.DynamicParameters.cpuSpeed.name(), primaryDetails, ApiConstants.CPU_SPEED);
        putIfMissing(details, UsageEventVO.DynamicParameters.memory.name(), primaryDetails, ApiConstants.MEMORY);
        return details;
    }

    private void putIfMissing(Map<String, String> target, String targetKey, Map<String, String> source, String alternativeKey) {
        if (target.containsKey(targetKey) || source == null) {
            return;
        }
        String value = StringUtils.defaultIfBlank(source.get(targetKey), source.get(alternativeKey));
        if (StringUtils.isNotBlank(value)) {
            target.put(targetKey, value);
        }
    }

    private FtctlHiddenOfferings ensureFtctlHiddenOfferings() {
        DiskOfferingVO rootDiskOffering = ensureHiddenDiskOffering(FTCTL_ROOT_DISK_OFFERING_UNIQUE_NAME, FTCTL_ROOT_DISK_OFFERING_NAME);
        DiskOfferingVO dataDiskOffering = ensureHiddenDiskOffering(FTCTL_DATA_DISK_OFFERING_UNIQUE_NAME, FTCTL_DATA_DISK_OFFERING_NAME);
        ServiceOfferingVO computeOffering = serviceOfferingDao.findByName(FTCTL_COMPUTE_OFFERING_UNIQUE_NAME);
        if (computeOffering == null) {
            computeOffering = new ServiceOfferingVO(FTCTL_COMPUTE_OFFERING_NAME, null, null, null, null, null, true,
                    "FTCTL internal custom compute offering", true, null, false);
            computeOffering.setUniqueName(FTCTL_COMPUTE_OFFERING_UNIQUE_NAME);
            computeOffering.setDiskOfferingId(rootDiskOffering.getId());
            computeOffering.setCustomized(true);
            computeOffering = serviceOfferingDao.persistDeafultServiceOffering(computeOffering);
        }
        return new FtctlHiddenOfferings(computeOffering, rootDiskOffering, dataDiskOffering);
    }

    private DiskOfferingVO ensureHiddenDiskOffering(String uniqueName, String name) {
        DiskOfferingVO diskOffering = diskOfferingDao.findByUniqueName(uniqueName);
        if (diskOffering != null) {
            return diskOffering;
        }
        diskOffering = new DiskOfferingVO(name, "FTCTL internal hidden disk offering", Storage.ProvisioningType.THIN,
                0L, null, true, null, null, null, DiskOffering.DiskCacheMode.WRITEBACK);
        diskOffering.setUniqueName(uniqueName);
        diskOffering.setCustomizedIops(true);
        diskOffering.setDisplayOffering(false);
        return diskOfferingDao.persistDefaultDiskOffering(diskOffering);
    }

    private static class FtctlHiddenOfferings {
        private final ServiceOfferingVO computeOffering;
        private final DiskOfferingVO rootDiskOffering;
        private final DiskOfferingVO dataDiskOffering;

        private FtctlHiddenOfferings(ServiceOfferingVO computeOffering, DiskOfferingVO rootDiskOffering, DiskOfferingVO dataDiskOffering) {
            this.computeOffering = computeOffering;
            this.rootDiskOffering = rootDiskOffering;
            this.dataDiskOffering = dataDiskOffering;
        }
    }
}
