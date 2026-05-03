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

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlActionAnswer;
import com.cloud.agent.api.FtctlActionCommand;
import com.cloud.agent.api.FtctlCheckAnswer;
import com.cloud.agent.api.FtctlCheckCommand;
import com.cloud.agent.api.FtctlEventsAnswer;
import com.cloud.agent.api.FtctlEventsCommand;
import com.cloud.agent.api.FtctlHealthAnswer;
import com.cloud.agent.api.FtctlHealthCommand;
import com.cloud.agent.api.FtctlStatusAnswer;
import com.cloud.agent.api.FtctlStatusCommand;
import com.cloud.agent.api.FtctlSyncAnswer;
import com.cloud.agent.api.FtctlSyncClusterCommand;
import com.cloud.agent.api.FtctlSyncProfileCommand;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;
import com.cloud.event.EventVO;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.ftctl.dao.FtctlProtectionDao;
import com.cloud.ftctl.dao.FtctlProtectionVolumeDao;
import com.cloud.host.Host;
import com.cloud.host.DetailVO;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.User;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VmDetailConstants;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlCheckCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlEventsCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlHealthCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlProtectionCmd;
import org.apache.cloudstack.api.command.admin.ftctl.RegisterFtctlProtectionCmd;
import org.apache.cloudstack.api.response.ftctl.FtctlActionResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlCheckResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlEventResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlEventsResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlHealthResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlProtectionResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlProtectionVolumeResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement;
import org.apache.cloudstack.outofbandmanagement.dao.OutOfBandManagementDao;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class FtctlServiceImpl extends ManagerBase implements FtctlService {

    public static final String DETAIL_ENABLED = "ftctl.enabled";
    public static final String DETAIL_MODE = "ftctl.mode";
    public static final String DETAIL_BACKEND_MODE = "ftctl.backend.mode";
    public static final String DETAIL_PROVISIONING_BACKEND = "ftctl.provisioning.backend";
    public static final String DETAIL_PROVISIONING_STATE = "ftctl.provisioning.state";
    public static final String DETAIL_TARGET_STORAGE_SCOPE = "ftctl.target.storage.scope";
    public static final String DETAIL_TARGET_STORAGE_POOL_ID = "ftctl.target.storage.pool.id";
    public static final String DETAIL_TARGET_STORAGE_POOL_NAME = "ftctl.target.storage.pool.name";
    public static final String DETAIL_FENCING_POLICY = "ftctl.fencing.policy";
    public static final String DETAIL_FENCING_IPMI_PRIMARY_HOST = "ftctl.fencing.ipmi.primary.host";
    public static final String DETAIL_FENCING_IPMI_SECONDARY_HOST = "ftctl.fencing.ipmi.secondary.host";
    public static final String DETAIL_FENCING_IPMI_PRIMARY_PORT = "ftctl.fencing.ipmi.primary.port";
    public static final String DETAIL_FENCING_IPMI_SECONDARY_PORT = "ftctl.fencing.ipmi.secondary.port";
    public static final String DETAIL_FENCING_IPMI_PRIMARY_USER = "ftctl.fencing.ipmi.primary.user";
    public static final String DETAIL_FENCING_IPMI_SECONDARY_USER = "ftctl.fencing.ipmi.secondary.user";
    public static final String DETAIL_FENCING_IPMI_PRIMARY_INTERFACE = "ftctl.fencing.ipmi.primary.interface";
    public static final String DETAIL_FENCING_IPMI_SECONDARY_INTERFACE = "ftctl.fencing.ipmi.secondary.interface";
    public static final String DETAIL_PEER_HOST_ID = "ftctl.peer.host.id";
    public static final String DETAIL_SECONDARY_VM_NAME = "ftctl.secondary.vm.name";
    public static final String DETAIL_SECONDARY_TARGET_DIR = "ftctl.secondary.target.dir";
    public static final String DETAIL_REMOTE_NBD_EXPORT_ADDR = "ftctl.remote.nbd.export.addr";
    public static final String DETAIL_XCOLO_PROXY_ENDPOINT = "ftctl.xcolo.proxy.endpoint";
    public static final String DETAIL_XCOLO_NBD_ENDPOINT = "ftctl.xcolo.nbd.endpoint";
    public static final String DETAIL_XCOLO_MIGRATE_URI = "ftctl.xcolo.migrate.uri";
    public static final String DETAIL_LAST_PROTECTION_STATE = "ftctl.last.protection.state";
    public static final String DETAIL_LAST_TRANSPORT_STATE = "ftctl.last.transport.state";
    public static final String DETAIL_LAST_ACTIVE_SIDE = "ftctl.last.active.side";
    public static final String DETAIL_LAST_ADMIN_STATE = "ftctl.last.admin.state";
    public static final String DETAIL_LAST_FENCING_STATE = "ftctl.last.fencing.state";
    public static final String DETAIL_LAST_ERROR = "ftctl.last.error";
    public static final String HOST_DETAIL_ENABLED = "ftctl.enabled";
    public static final String HOST_DETAIL_MANAGEMENT_IP = "ftctl.management.ip";
    public static final String HOST_DETAIL_LIBVIRT_URI = "ftctl.libvirt.uri";
    public static final String HOST_DETAIL_BLOCKCOPY_IP = "ftctl.blockcopy.ip";
    public static final String HOST_DETAIL_XCOLO_CONTROL_IP = "ftctl.xcolo.control.ip";
    public static final String HOST_DETAIL_XCOLO_DATA_IP = "ftctl.xcolo.data.ip";
    private static final String FENCING_POLICY_IPMI = "ipmi";
    private static final String OOBM_DRIVER_IPMITOOL = "ipmitool";
    private static final String DEFAULT_IPMI_INTERFACE = "lanplus";
    private static final ConcurrentMap<String, Object> VM_DETAIL_LOCKS = new ConcurrentHashMap<>();

    @Inject
    private VMInstanceDetailsDao vmInstanceDetailsDao;
    @Inject
    private UserVmDao userVmDao;
    @Inject
    private UserVmManager userVmManager;
    @Inject
    private AgentManager agentManager;
    @Inject
    private HostDetailsDao hostDetailsDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    private OutOfBandManagementDao outOfBandManagementDao;
    @Inject
    private FtctlProtectionProvisioningService ftctlProtectionProvisioningService;
    @Inject
    private FtctlProtectionDao ftctlProtectionDao;
    @Inject
    private FtctlProtectionVolumeDao ftctlProtectionVolumeDao;
    @Inject
    private VolumeDao volumeDao;

    @Override
    public FtctlProtectionResponse getFtctlProtection(GetFtctlProtectionCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        FtctlProtectionResponse response = buildProtectionResponse(userVm);
        UserVmVO runtimeVm = resolveRuntimeVmForProtectionView(userVm);
        if (runtimeVm != null) {
            populateRuntimeStateFromAgent(runtimeVm, response, true);
        }
        response.setObjectName("ftctlprotection");
        return response;
    }

    @Override
    public FtctlProtectionResponse registerFtctlProtection(RegisterFtctlProtectionCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        validatePrimaryProtectionTarget(userVm);
        validateRegisterRequest(cmd);
        StoragePoolVO targetStoragePool = validateTargetStoragePool(cmd, userVm);
        String targetStorageScope = resolveTargetStorageScope(cmd, targetStoragePool);
        String backendMode = resolveBackendMode(cmd, targetStoragePool);
        String provisioningBackend = resolveProvisioningBackend(cmd);
        String secondaryTargetDir = resolveSecondaryTargetDir(cmd, targetStoragePool, backendMode);
        String remoteNbdExportAddr = resolveRemoteNbdExportAddr(cmd, backendMode);
        validateResolvedRegisterRequest(cmd, backendMode, secondaryTargetDir, remoteNbdExportAddr);
        FtctlIpmiFencingConfig ipmiFencingConfig = resolveIpmiFencingConfig(userVm, cmd);
        FtctlProtectionProvisioningContext provisioningContext;
        try {
            provisioningContext = prepareProtection(userVm, cmd, targetStoragePool, backendMode, provisioningBackend);
        } catch (CloudRuntimeException e) {
            persistProvisioningFailure(cmd.getVirtualMachineId(), provisioningBackend, e);
            throw e;
        }

        putVmDetail(cmd.getVirtualMachineId(), DETAIL_ENABLED, String.valueOf(true));
        putVmDetail(cmd.getVirtualMachineId(), DETAIL_MODE, cmd.getMode());
        if (backendMode != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_BACKEND_MODE, backendMode);
        }
        putVmDetail(cmd.getVirtualMachineId(), DETAIL_PROVISIONING_BACKEND, provisioningContext.getProvisioningBackend());
        putVmDetail(cmd.getVirtualMachineId(), DETAIL_PROVISIONING_STATE, provisioningContext.getProvisioningState());
        if (targetStorageScope != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_SCOPE, targetStorageScope);
        }
        if (targetStoragePool != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_POOL_ID, targetStoragePool.getUuid());
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_POOL_NAME, targetStoragePool.getName());
        }
        if (cmd.getFencingPolicy() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_FENCING_POLICY, cmd.getFencingPolicy());
        }
        if (ipmiFencingConfig != null) {
            persistIpmiFencingDetails(cmd.getVirtualMachineId(), ipmiFencingConfig);
        }
        if (cmd.getPeerHostId() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_PEER_HOST_ID, String.valueOf(cmd.getPeerHostId()));
        }
        if (provisioningContext.getSecondaryVmName() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_SECONDARY_VM_NAME, provisioningContext.getSecondaryVmName());
        }
        if (secondaryTargetDir != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_SECONDARY_TARGET_DIR, secondaryTargetDir);
        }
        if (remoteNbdExportAddr != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_REMOTE_NBD_EXPORT_ADDR, remoteNbdExportAddr);
        }
        if (cmd.getXcoloProxyEndpoint() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_PROXY_ENDPOINT, cmd.getXcoloProxyEndpoint());
        }
        if (cmd.getXcoloNbdEndpoint() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_NBD_ENDPOINT, cmd.getXcoloNbdEndpoint());
        }
        if (cmd.getXcoloMigrateUri() != null) {
            putVmDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_MIGRATE_URI, cmd.getXcoloMigrateUri());
        }

        Long hostId = getExecutionHostId(userVm);
        if (hostId != null) {
            syncFtctlContext(userVm, cmd, targetStoragePool, targetStorageScope, backendMode, secondaryTargetDir, remoteNbdExportAddr, ipmiFencingConfig, provisioningContext);
            String peerUri = resolvePeerUri(cmd.getPeerHostId());
            if (peerUri == null || peerUri.isBlank()) {
                throw new CloudRuntimeException(String.format("Missing FTCTL peer libvirt URI on host %s", cmd.getPeerHostId()));
            }
            FtctlActionCommand actionCommand = new FtctlActionCommand(FtctlActionCommand.Action.PROTECT, userVm.getInstanceName());
            actionCommand.setMode(cmd.getMode());
            actionCommand.setPeerUri(peerUri);
            if (backendMode != null) {
                actionCommand.setContextParam("ftctl.backend.mode", backendMode);
            }
            actionCommand.setContextParam("ftctl.provisioning.backend", provisioningContext.getProvisioningBackend());
            if (targetStorageScope != null) {
                actionCommand.setContextParam("ftctl.target.storage.scope", targetStorageScope);
            }
            if (cmd.getFencingPolicy() != null) {
                actionCommand.setContextParam("ftctl.fencing.policy", cmd.getFencingPolicy());
            }
            try {
                Answer answer = agentManager.send(hostId, actionCommand);
                if (!(answer instanceof FtctlActionAnswer) || !answer.getResult()) {
                    throw new CloudRuntimeException(String.format("Unable to register FTCTL protection for VM %s: %s",
                            userVm.getUuid(), answer != null ? answer.getDetails() : "no answer"));
                }
            } catch (AgentUnavailableException | OperationTimedoutException e) {
                throw new CloudRuntimeException(String.format("Unable to execute FTCTL protection on host %s for VM %s",
                        hostId, userVm.getUuid()), e);
            }
        }

        FtctlProtectionResponse response = buildProtectionResponse(userVm);
        populateRuntimeStateFromAgent(userVm, response, true);
        publishFtctlEvent(userVm, EventTypes.EVENT_FTCTL_PROTECTION_REGISTER,
                String.format("Registered FTCTL protection for VM %s", userVm.getUuid()));
        response.setObjectName("ftctlprotection");
        return response;
    }

    @Override
    public FtctlCheckResponse getFtctlCheck(GetFtctlCheckCmd cmd) throws CloudRuntimeException {
        UserVmVO requestedVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        UserVmVO runtimeVm = resolveRuntimeVmForProtectionView(requestedVm);
        Long hostId = requireExecutionHostId(runtimeVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlCheckCommand(runtimeVm.getInstanceName()));
            if (!(answer instanceof FtctlCheckAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL check answer type for VM %s", requestedVm.getUuid()));
            }
            FtctlCheckAnswer checkAnswer = (FtctlCheckAnswer) answer;
            FtctlCheckResponse response = new FtctlCheckResponse();
            response.setObjectName("ftctlcheck");
            response.setVirtualMachineId(requestedVm.getId());
            response.setVmName(checkAnswer.getVmName());
            response.setResult(checkAnswer.getFtctlResult());
            response.setInventoryResult(checkAnswer.getInventoryResult());
            response.setPrimaryRc(checkAnswer.getPrimaryRc());
            response.setPeerRc(checkAnswer.getPeerRc());
            response.setPeerDomainExpected(checkAnswer.getPeerDomainExpected());
            response.setStandbyDomainState(checkAnswer.getStandbyDomainState());
            response.setProvisioningBackend(checkAnswer.getProvisioningBackend());
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL check result for VM %s", requestedVm.getUuid()), e);
        }
    }

    @Override
    public FtctlEventsResponse getFtctlEvents(GetFtctlEventsCmd cmd) throws CloudRuntimeException {
        UserVmVO requestedVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        UserVmVO runtimeVm = resolveRuntimeVmForProtectionView(requestedVm);
        Long hostId = requireExecutionHostId(runtimeVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlEventsCommand(runtimeVm.getInstanceName(), cmd.getLimit()));
            if (!(answer instanceof FtctlEventsAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL events answer type for VM %s", requestedVm.getUuid()));
            }
            FtctlEventsAnswer eventsAnswer = (FtctlEventsAnswer) answer;
            FtctlEventsResponse response = new FtctlEventsResponse();
            response.setObjectName("ftctlevents");
            response.setVirtualMachineId(requestedVm.getId());
            response.setVmName(eventsAnswer.getVmName());
            response.setResult(eventsAnswer.getFtctlResult());
            response.setCount(eventsAnswer.getCount());
            response.setEvents(parseEvents(eventsAnswer.getItemsJson()));
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL events for VM %s", requestedVm.getUuid()), e);
        }
    }

    private void validateRegisterRequest(RegisterFtctlProtectionCmd cmd) {
        if (cmd.getMode() == null || cmd.getMode().isBlank()) {
            throw new CloudRuntimeException("FTCTL mode is required");
        }
        if (("ha".equalsIgnoreCase(cmd.getMode()) || "dr".equalsIgnoreCase(cmd.getMode())) &&
                (cmd.getBackendMode() == null || cmd.getBackendMode().isBlank())) {
            throw new CloudRuntimeException("FTCTL backend mode is required for HA/DR");
        }
        if (isProtectionModeWithTargetStorage(cmd.getMode()) && cmd.getTargetStoragePoolId() == null) {
            throw new CloudRuntimeException("FTCTL target primary storage pool is required for HA/DR/FT");
        }
        if ("ft".equalsIgnoreCase(cmd.getMode()) &&
                (isBlank(cmd.getXcoloProxyEndpoint()) || isBlank(cmd.getXcoloNbdEndpoint()) || isBlank(cmd.getXcoloMigrateUri()))) {
            throw new CloudRuntimeException("FTCTL FT mode requires x-colo proxy, NBD, and migrate fields");
        }
    }

    private void validateResolvedRegisterRequest(RegisterFtctlProtectionCmd cmd, String backendMode, String secondaryTargetDir, String remoteNbdExportAddr) {
        if ("remote-nbd".equalsIgnoreCase(backendMode) && (isBlank(secondaryTargetDir) || isBlank(remoteNbdExportAddr))) {
            throw new CloudRuntimeException("FTCTL remote-nbd requires secondary target directory and export address");
        }
        if ("ft".equalsIgnoreCase(cmd.getMode()) && cmd.getTargetStoragePoolId() == null) {
            throw new CloudRuntimeException("FTCTL FT mode requires a target primary storage pool");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isProtectionModeWithTargetStorage(String mode) {
        return "ha".equalsIgnoreCase(mode) || "dr".equalsIgnoreCase(mode) || "ft".equalsIgnoreCase(mode);
    }

    private String resolveProvisioningBackend(RegisterFtctlProtectionCmd cmd) {
        return StringUtils.defaultIfBlank(cmd.getProvisioningBackend(), FtctlProtectionProvisioningService.BACKEND_LIBVIRT_MANAGED);
    }

    private FtctlProtectionProvisioningContext prepareProtection(UserVmVO userVm, RegisterFtctlProtectionCmd cmd, StoragePoolVO targetStoragePool,
                                                                String backendMode, String provisioningBackend) {
        FtctlProtectionProvisioningRequest request = new FtctlProtectionProvisioningRequest(
                userVm,
                cmd.getPeerHostId(),
                targetStoragePool,
                cmd.getMode(),
                backendMode,
                provisioningBackend,
                cmd.getFencingPolicy(),
                cmd.getSecondaryVmName());
        return ftctlProtectionProvisioningService.prepareProtection(request);
    }

    private void persistProvisioningFailure(Long virtualMachineId, String provisioningBackend, CloudRuntimeException e) {
        putVmDetail(virtualMachineId, DETAIL_PROVISIONING_BACKEND, provisioningBackend);
        putVmDetail(virtualMachineId, DETAIL_PROVISIONING_STATE, FtctlProtectionProvisioningService.STATE_PROVISIONING_FAILED);
        putVmDetail(virtualMachineId, DETAIL_LAST_ERROR, e.getMessage());
    }

    private StoragePoolVO validateTargetStoragePool(RegisterFtctlProtectionCmd cmd, UserVmVO userVm) {
        if (!isProtectionModeWithTargetStorage(cmd.getMode())) {
            return null;
        }
        StoragePoolVO storagePool = primaryDataStoreDao.findById(cmd.getTargetStoragePoolId());
        if (storagePool == null) {
            throw new CloudRuntimeException(String.format("Unable to find FTCTL target primary storage pool with id %s", cmd.getTargetStoragePoolId()));
        }
        if (storagePool.getDataCenterId() != userVm.getDataCenterId()) {
            throw new CloudRuntimeException(String.format("FTCTL target primary storage pool %s is not in VM zone %s",
                    storagePool.getUuid(), userVm.getDataCenterId()));
        }
        return storagePool;
    }

    private String resolveTargetStorageScope(RegisterFtctlProtectionCmd cmd, StoragePoolVO storagePool) {
        if (storagePool != null && storagePool.getScope() != null) {
            switch (storagePool.getScope()) {
                case HOST:
                    return "secondary-local";
                case CLUSTER:
                case ZONE:
                    return "shared";
                default:
                    return storagePool.getScope().name().toLowerCase();
            }
        }
        return cmd.getTargetStorageScope();
    }

    private String resolveBackendMode(RegisterFtctlProtectionCmd cmd, StoragePoolVO storagePool) {
        if (!isBlank(cmd.getBackendMode())) {
            return cmd.getBackendMode();
        }
        if ("ft".equalsIgnoreCase(cmd.getMode())) {
            if (storagePool != null && storagePool.getScope() != null && "HOST".equalsIgnoreCase(storagePool.getScope().name())) {
                return "remote-nbd";
            }
            return "shared-blockcopy";
        }
        return cmd.getBackendMode();
    }

    private String resolveSecondaryTargetDir(RegisterFtctlProtectionCmd cmd, StoragePoolVO storagePool, String backendMode) {
        if (!isBlank(cmd.getSecondaryTargetDir())) {
            return cmd.getSecondaryTargetDir();
        }
        if (!"ft".equalsIgnoreCase(cmd.getMode()) || !"remote-nbd".equalsIgnoreCase(backendMode)) {
            return cmd.getSecondaryTargetDir();
        }
        if (storagePool == null || isBlank(storagePool.getPath())) {
            throw new CloudRuntimeException("FTCTL FT remote-nbd target storage pool path is required");
        }
        return String.format("%s/ftctl", storagePool.getPath().replaceAll("/+$", ""));
    }

    private String resolveRemoteNbdExportAddr(RegisterFtctlProtectionCmd cmd, String backendMode) {
        if (!isBlank(cmd.getRemoteNbdExportAddr())) {
            return cmd.getRemoteNbdExportAddr();
        }
        if (!"ft".equalsIgnoreCase(cmd.getMode()) || !"remote-nbd".equalsIgnoreCase(backendMode)) {
            return cmd.getRemoteNbdExportAddr();
        }
        HostVO peerHost = hostDao.findById(cmd.getPeerHostId());
        if (peerHost == null) {
            throw new CloudRuntimeException(String.format("Unable to resolve FTCTL peer host %s", cmd.getPeerHostId()));
        }
        String exportAddress = resolveHostField(peerHost, HOST_DETAIL_BLOCKCOPY_IP, peerHost.getPrivateIpAddress());
        if (isBlank(exportAddress)) {
            throw new CloudRuntimeException(String.format("Unable to resolve FTCTL remote-nbd export address for peer host %s", cmd.getPeerHostId()));
        }
        return String.format("%s:10809", exportAddress);
    }

    @Override
    public FtctlHealthResponse getFtctlHealth(GetFtctlHealthCmd cmd) throws CloudRuntimeException {
        UserVmVO requestedVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        UserVmVO runtimeVm = resolveRuntimeVmForProtectionView(requestedVm);
        Long hostId = requireExecutionHostId(runtimeVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlHealthCommand());
            if (!(answer instanceof FtctlHealthAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL health answer type for VM %s", requestedVm.getUuid()));
            }
            FtctlHealthAnswer healthAnswer = (FtctlHealthAnswer) answer;
            FtctlHealthResponse response = new FtctlHealthResponse();
            response.setObjectName("ftctlhealth");
            response.setVirtualMachineId(requestedVm.getId());
            response.setHostId(hostId);
            response.setHostName(resolveHostName(hostId));
            response.setResult(healthAnswer.getFtctlResult());
            response.setUri(healthAnswer.getUri());
            response.setRc(healthAnswer.getRc());
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL health result for VM %s", requestedVm.getUuid()), e);
        }
    }

    @Override
    public FtctlActionResponse executeFtctlAction(Long virtualMachineId, FtctlActionCommand.Action action, boolean force) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(virtualMachineId);
        validatePrimaryProtectionTarget(userVm);
        Long hostId = requireExecutionHostId(userVm);
        try {
            FtctlActionCommand actionCommand = new FtctlActionCommand(action, userVm.getInstanceName());
            actionCommand.setForce(force || action == FtctlActionCommand.Action.FAILOVER || action == FtctlActionCommand.Action.FAILBACK);
            Answer answer = agentManager.send(hostId, actionCommand);
            if (!(answer instanceof FtctlActionAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL action answer type for VM %s", userVm.getUuid()));
            }
            FtctlActionAnswer actionAnswer = (FtctlActionAnswer) answer;
            if (!actionAnswer.getResult()) {
                throw new CloudRuntimeException(String.format("FTCTL action %s failed for VM %s: %s",
                        action, userVm.getUuid(), actionAnswer.getDetails()));
            }
            FtctlActionResponse response = new FtctlActionResponse();
            response.setObjectName("ftctlaction");
            response.setVirtualMachineId(userVm.getId());
            response.setVmName(userVm.getInstanceName());
            response.setAction(action.name());
            response.setResult(actionAnswer.getFtctlResult());
            response.setExitCode(actionAnswer.getExitCode());
            response.setOutput(actionAnswer.getOutput());
            FtctlStatusAnswer statusAnswer = fetchRuntimeStatus(userVm);
            if (statusAnswer != null) {
                response.setMode(statusAnswer.getMode());
                response.setProtectionState(statusAnswer.getProtectionState());
                response.setTransportState(statusAnswer.getTransportState());
                response.setActiveSide(statusAnswer.getActiveSide());
                response.setAdminState(statusAnswer.getAdminState());
                response.setFencingState(statusAnswer.getFencingState());
                response.setLastError(statusAnswer.getLastError());
                persistRuntimeState(userVm, statusAnswer);
            }
            publishFtctlEvent(userVm, resolveFtctlActionEventType(action),
                    String.format("Executed FTCTL action %s for VM %s", action.name(), userVm.getUuid()));
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to execute FTCTL action %s for VM %s", action, userVm.getUuid()), e);
        }
    }

    @Override
    public FtctlActionResponse confirmFtctlFence(Long virtualMachineId) throws CloudRuntimeException {
        UserVmVO primaryVm = validateVirtualMachineExists(virtualMachineId);
        validatePrimaryProtectionTarget(primaryVm);
        FtctlProtectionVO protection = ftctlProtectionDao.findActiveByPrimaryVmId(primaryVm.getId());
        if (protection == null) {
            throw new CloudRuntimeException(String.format("Unable to find active FTCTL protection for VM %s", primaryVm.getUuid()));
        }
        validatePrimaryVmStoppedForManualFence(primaryVm);

        executeFtctlAction(primaryVm.getId(), FtctlActionCommand.Action.FENCE_CONFIRM, false);
        protection = validateManualFailoverTransportReady(primaryVm);
        startSecondaryVmForManualFailover(primaryVm, protection);
        FtctlActionResponse response = executeFtctlAction(primaryVm.getId(), FtctlActionCommand.Action.FAILOVER, true);
        response.setAction(FtctlActionCommand.Action.FENCE_CONFIRM.name());
        return response;
    }

    private FtctlProtectionVO validateManualFailoverTransportReady(UserVmVO primaryVm) {
        FtctlProtectionVO protection = ftctlProtectionDao.findActiveByPrimaryVmId(primaryVm.getId());
        if (protection == null) {
            throw new CloudRuntimeException(String.format("Unable to find active FTCTL protection for VM %s", primaryVm.getUuid()));
        }
        if (!requiresManualFailoverTransportReady(protection)) {
            return protection;
        }
        String transportState = StringUtils.trimToEmpty(protection.getTransportState()).toLowerCase(Locale.ROOT);
        if (!"mirroring".equals(transportState) && !"failed_over".equals(transportState)) {
            throw new CloudRuntimeException(String.format(
                    "FTCTL manual fence confirmation requires transport state mirroring or failed_over before starting secondary VM for primary VM %s, current state is %s",
                    primaryVm.getUuid(), StringUtils.defaultIfBlank(protection.getTransportState(), "unknown")));
        }
        return protection;
    }

    private boolean requiresManualFailoverTransportReady(FtctlProtectionVO protection) {
        String mode = StringUtils.trimToEmpty(protection.getMode()).toLowerCase(Locale.ROOT);
        String backendMode = StringUtils.trimToEmpty(protection.getBackendMode()).toLowerCase(Locale.ROOT);
        return "ha".equals(mode) && ("shared-blockcopy".equals(backendMode) || "remote-nbd".equals(backendMode));
    }

    private void validatePrimaryVmStoppedForManualFence(UserVmVO primaryVm) {
        VirtualMachine.State state = primaryVm.getState();
        if (state != VirtualMachine.State.Stopped) {
            throw new CloudRuntimeException(String.format("FTCTL manual fence confirmation requires primary VM %s to be Stopped, current state is %s",
                    primaryVm.getUuid(), state));
        }
    }

    private void startSecondaryVmForManualFailover(UserVmVO primaryVm, FtctlProtectionVO protection) {
        Long secondaryVmId = protection.getSecondaryVmId();
        if (secondaryVmId == null) {
            throw new CloudRuntimeException(String.format("FTCTL protection for VM %s does not have a secondary VM", primaryVm.getUuid()));
        }
        UserVmVO secondaryVm = userVmDao.findById(secondaryVmId);
        if (secondaryVm == null) {
            throw new CloudRuntimeException(String.format("Unable to find FTCTL secondary VM %s for primary VM %s", secondaryVmId, primaryVm.getUuid()));
        }
        if (secondaryVm.getState() == VirtualMachine.State.Running) {
            logger.info(String.format("FTCTL secondary VM %s is already running for primary VM %s", secondaryVm.getUuid(), primaryVm.getUuid()));
            return;
        }
        Long peerHostId = resolvePeerHostId(primaryVm.getId());
        try {
            userVmManager.startVirtualMachine(secondaryVmId, peerHostId, new HashMap<VirtualMachineProfile.Param, Object>(), null);
            publishFtctlEvent(primaryVm, EventTypes.EVENT_FTCTL_PROTECTION_STATE_UPDATE,
                    String.format("Started FTCTL secondary VM %s for primary VM %s after manual fence confirmation",
                            secondaryVm.getUuid(), primaryVm.getUuid()));
        } catch (ConcurrentOperationException | ResourceUnavailableException | InsufficientCapacityException | ResourceAllocationException e) {
            throw new CloudRuntimeException(String.format("Unable to start FTCTL secondary VM %s for primary VM %s",
                    secondaryVm.getUuid(), primaryVm.getUuid()), e);
        }
    }

    private Long resolvePeerHostId(Long primaryVmId) {
        String peerHostId = getDetailValue(primaryVmId, DETAIL_PEER_HOST_ID);
        if (StringUtils.isBlank(peerHostId)) {
            return null;
        }
        try {
            return Long.parseLong(peerHostId);
        } catch (NumberFormatException e) {
            throw new CloudRuntimeException(String.format("Invalid FTCTL peer host id %s for VM %s", peerHostId, primaryVmId), e);
        }
    }

    private UserVmVO validateVirtualMachineExists(Long virtualMachineId) {
        UserVmVO userVm = virtualMachineId != null ? userVmDao.findById(virtualMachineId) : null;
        if (userVm == null) {
            throw new CloudRuntimeException(String.format("Unable to find virtual machine with id %s", virtualMachineId));
        }
        return userVm;
    }

    private void validatePrimaryProtectionTarget(UserVmVO userVm) {
        if (userVm == null) {
            return;
        }
        FtctlProtectionVO protection = ftctlProtectionDao.findActiveBySecondaryVmId(userVm.getId());
        if (protection != null) {
            throw new CloudRuntimeException(String.format("FTCTL standby VM %s is managed from primary VM %s",
                    userVm.getUuid(), protection.getPrimaryVmId()));
        }
    }

    private FtctlProtectionResponse buildProtectionResponse(UserVmVO requestedVm) {
        Long requestedVmId = requestedVm != null ? requestedVm.getId() : null;
        FtctlProtectionVO protection = findActiveProtectionForVm(requestedVmId);
        boolean standbyView = isStandbyProtectionVm(requestedVmId, protection);
        Long primaryVmId = protection != null ? protection.getPrimaryVmId() : requestedVmId;
        Long sourceVmId = primaryVmId;
        FtctlProtectionResponse response = new FtctlProtectionResponse();
        response.setVirtualMachineId(requestedVmId);
        response.setProtectionRole(standbyView ? "standby" : protection != null ? "primary" : null);
        response.setPrimaryVirtualMachineId(primaryVmId);
        response.setPrimaryVirtualMachineName(resolveVmDisplayName(primaryVmId));
        response.setPrimaryVirtualMachineUuid(resolveVmUuid(primaryVmId));
        Long secondaryVmId = protection != null ? protection.getSecondaryVmId() : null;
        response.setSecondaryVirtualMachineId(secondaryVmId);
        response.setSecondaryVirtualMachineUuid(resolveVmUuid(secondaryVmId));
        response.setSecondaryVirtualMachineDisplayName(resolveVmDisplayName(secondaryVmId));
        response.setEnabled(getDetailValue(sourceVmId, DETAIL_ENABLED));
        response.setMode(getDetailValue(sourceVmId, DETAIL_MODE));
        response.setBackendMode(getDetailValue(sourceVmId, DETAIL_BACKEND_MODE));
        response.setProvisioningBackend(getDetailValue(sourceVmId, DETAIL_PROVISIONING_BACKEND));
        response.setProvisioningState(getDetailValue(sourceVmId, DETAIL_PROVISIONING_STATE));
        response.setTargetStorageScope(getDetailValue(sourceVmId, DETAIL_TARGET_STORAGE_SCOPE));
        response.setTargetStoragePoolId(getDetailValue(sourceVmId, DETAIL_TARGET_STORAGE_POOL_ID));
        response.setTargetStoragePoolName(getDetailValue(sourceVmId, DETAIL_TARGET_STORAGE_POOL_NAME));
        response.setFencingPolicy(getDetailValue(sourceVmId, DETAIL_FENCING_POLICY));
        String peerHostId = getDetailValue(sourceVmId, DETAIL_PEER_HOST_ID);
        response.setPeerHostId(peerHostId);
        response.setPeerHostName(resolvePeerHostName(peerHostId));
        response.setSecondaryVmName(StringUtils.defaultIfBlank(protection != null ? protection.getSecondaryVmName() : null,
                getDetailValue(sourceVmId, DETAIL_SECONDARY_VM_NAME)));
        response.setSecondaryTargetDir(getDetailValue(sourceVmId, DETAIL_SECONDARY_TARGET_DIR));
        populateCloudManagedDiskDetails(sourceVmId, protection, response);
        response.setRemoteNbdExportAddr(getDetailValue(sourceVmId, DETAIL_REMOTE_NBD_EXPORT_ADDR));
        response.setXcoloProxyEndpoint(getDetailValue(sourceVmId, DETAIL_XCOLO_PROXY_ENDPOINT));
        response.setXcoloNbdEndpoint(getDetailValue(sourceVmId, DETAIL_XCOLO_NBD_ENDPOINT));
        response.setXcoloMigrateUri(getDetailValue(sourceVmId, DETAIL_XCOLO_MIGRATE_URI));
        response.setProtectionState(getDetailValue(sourceVmId, DETAIL_LAST_PROTECTION_STATE));
        response.setTransportState(getDetailValue(sourceVmId, DETAIL_LAST_TRANSPORT_STATE));
        response.setActiveSide(getDetailValue(sourceVmId, DETAIL_LAST_ACTIVE_SIDE));
        response.setAdminState(getDetailValue(sourceVmId, DETAIL_LAST_ADMIN_STATE));
        response.setFencingState(getDetailValue(sourceVmId, DETAIL_LAST_FENCING_STATE));
        response.setLastError(getDetailValue(sourceVmId, DETAIL_LAST_ERROR));
        return response;
    }

    private FtctlProtectionVO findActiveProtectionForVm(Long virtualMachineId) {
        if (virtualMachineId == null) {
            return null;
        }
        FtctlProtectionVO protection = ftctlProtectionDao.findActiveByPrimaryVmId(virtualMachineId);
        return protection != null ? protection : ftctlProtectionDao.findActiveBySecondaryVmId(virtualMachineId);
    }

    private boolean isStandbyProtectionVm(Long virtualMachineId, FtctlProtectionVO protection) {
        return virtualMachineId != null && protection != null && protection.getSecondaryVmId() != null &&
                protection.getSecondaryVmId().equals(virtualMachineId);
    }

    private UserVmVO resolveRuntimeVmForProtectionView(UserVmVO requestedVm) {
        if (requestedVm == null) {
            return null;
        }
        FtctlProtectionVO protection = findActiveProtectionForVm(requestedVm.getId());
        if (isStandbyProtectionVm(requestedVm.getId(), protection)) {
            return userVmDao.findById(protection.getPrimaryVmId());
        }
        return requestedVm;
    }

    private String resolveVmDisplayName(Long virtualMachineId) {
        UserVmVO vm = virtualMachineId != null ? userVmDao.findById(virtualMachineId) : null;
        return vm != null ? StringUtils.defaultIfBlank(vm.getDisplayName(), vm.getHostName()) : null;
    }

    private String resolveVmUuid(Long virtualMachineId) {
        UserVmVO vm = virtualMachineId != null ? userVmDao.findById(virtualMachineId) : null;
        return vm != null ? vm.getUuid() : null;
    }

    private void populateCloudManagedDiskDetails(Long primaryVmId, FtctlProtectionVO protection, FtctlProtectionResponse response) {
        if (protection == null && primaryVmId != null) {
            protection = ftctlProtectionDao.findActiveByPrimaryVmId(primaryVmId);
        }
        if (protection == null) {
            return;
        }
        List<FtctlProtectionVolumeVO> volumes = ftctlProtectionVolumeDao.listActiveByProtectionId(protection.getId());
        if (volumes == null || volumes.isEmpty()) {
            return;
        }
        List<String> secondaryDiskEntries = new ArrayList<>();
        List<String> diskMapEntries = new ArrayList<>();
        List<FtctlProtectionVolumeResponse> secondaryVolumes = new ArrayList<>();
        for (FtctlProtectionVolumeVO volume : volumes) {
            String secondaryPath = volume.getSecondaryDiskPath();
            VolumeVO secondaryVolume = volume.getSecondaryVolumeId() != null ? volumeDao.findById(volume.getSecondaryVolumeId()) : null;
            if (secondaryVolume != null) {
                FtctlProtectionVolumeResponse volumeResponse = new FtctlProtectionVolumeResponse();
                volumeResponse.setObjectName("ftctlprotectionvolume");
                volumeResponse.setId(secondaryVolume.getUuid());
                volumeResponse.setName(resolveVolumeDisplayName(secondaryVolume.getId()));
                volumeResponse.setPath(secondaryVolume.getPath());
                volumeResponse.setDiskLabel(volume.getDiskLabel());
                secondaryVolumes.add(volumeResponse);
            }
            if (StringUtils.isNotBlank(secondaryPath)) {
                String label = StringUtils.defaultIfBlank(volume.getDiskLabel(), resolveVolumeDisplayName(volume.getPrimaryVolumeId()));
                secondaryDiskEntries.add(String.format("%s=%s", label, secondaryPath));
                diskMapEntries.add(String.format("%s=%s", resolveKvmDiskTarget(primaryVmId, volume.getPrimaryVolumeId()), secondaryPath));
            }
        }
        if (!secondaryDiskEntries.isEmpty()) {
            response.setSecondaryTargetDisk(secondaryDiskEntries.stream().collect(Collectors.joining(";")));
            response.setDiskMap(diskMapEntries.stream().collect(Collectors.joining(";")));
        }
        if (!secondaryVolumes.isEmpty()) {
            response.setSecondaryVolumes(secondaryVolumes);
        }
    }

    private String resolveVolumeDisplayName(long volumeId) {
        VolumeVO volume = volumeDao.findById(volumeId);
        if (volume == null) {
            return String.valueOf(volumeId);
        }
        return StringUtils.defaultIfBlank(volume.getName(), StringUtils.defaultIfBlank(volume.getPath(), String.valueOf(volumeId)));
    }

    private String resolveKvmDiskTarget(Long virtualMachineId, long volumeId) {
        VolumeVO volume = volumeDao.findById(volumeId);
        Long deviceId = volume != null ? volume.getDeviceId() : null;
        if (deviceId == null) {
            return String.valueOf(volumeId);
        }
        String prefix = resolveKvmDiskPrefix(virtualMachineId, volume);
        if (deviceId < 0 || deviceId > 25) {
            return String.format("%s%s", prefix, deviceId);
        }
        return String.format("%s%c", prefix, (char) ('a' + deviceId));
    }

    private String resolveKvmDiskPrefix(Long virtualMachineId, VolumeVO volume) {
        String controller = resolveDiskController(virtualMachineId, volume);
        String normalizedController = StringUtils.defaultString(controller).toLowerCase(Locale.ROOT);
        if (normalizedController.contains("scsi") || normalizedController.contains("sata")) {
            return "sd";
        }
        if (normalizedController.contains("ide")) {
            return "hd";
        }
        return "vd";
    }

    private String resolveDiskController(Long virtualMachineId, VolumeVO volume) {
        if (virtualMachineId == null || volume == null) {
            return null;
        }
        UserVmVO primaryVm = userVmDao.findById(virtualMachineId);
        if (primaryVm == null) {
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
            return details.get(VmDetailConstants.DATA_DISK_CONTROLLER);
        }
        return StringUtils.defaultIfBlank(details.get(VmDetailConstants.DATA_DISK_CONTROLLER),
                details.get(VmDetailConstants.ROOT_DISK_CONTROLLER));
    }

    private String resolvePeerHostName(String peerHostId) {
        if (peerHostId == null || peerHostId.isBlank()) {
            return null;
        }
        try {
            return resolveHostName(Long.parseLong(peerHostId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveHostName(Long hostId) {
        HostVO host = hostId != null ? hostDao.findById(hostId) : null;
        return host != null ? host.getName() : null;
    }

    private String getDetailValue(Long virtualMachineId, String key) {
        VMInstanceDetailVO detail = vmInstanceDetailsDao.findDetail(virtualMachineId, key);
        return detail != null ? detail.getValue() : null;
    }

    private void putVmDetail(Long virtualMachineId, String key, String value) {
        if (virtualMachineId == null || StringUtils.isBlank(key)) {
            return;
        }
        Object lock = VM_DETAIL_LOCKS.computeIfAbsent(String.format("%s:%s", virtualMachineId, key), ignored -> new Object());
        synchronized (lock) {
            vmInstanceDetailsDao.removeDetail(virtualMachineId, key);
            vmInstanceDetailsDao.addDetail(virtualMachineId, key, value, true);
        }
    }

    private Long getExecutionHostId(UserVmVO userVm) {
        if (userVm == null) {
            return null;
        }
        return userVm.getHostId() != null ? userVm.getHostId() : userVm.getLastHostId();
    }

    private Long requireExecutionHostId(UserVmVO userVm) {
        Long hostId = getExecutionHostId(userVm);
        if (hostId == null) {
            throw new CloudRuntimeException(String.format("Unable to resolve execution host for VM %s", userVm != null ? userVm.getUuid() : "unknown"));
        }
        return hostId;
    }

    private String resolvePeerUri(Long peerHostId) {
        if (peerHostId == null) {
            return null;
        }
        DetailVO detail = hostDetailsDao.findDetail(peerHostId, HOST_DETAIL_LIBVIRT_URI);
        if (detail != null && detail.getValue() != null && !detail.getValue().isBlank()) {
            return detail.getValue();
        }
        HostVO host = hostDao.findById(peerHostId);
        if (host == null || host.getPrivateIpAddress() == null || host.getPrivateIpAddress().isBlank()) {
            return null;
        }
        return String.format("qemu+ssh://%s/system", host.getPrivateIpAddress());
    }

    private void populateRuntimeStateFromAgent(UserVmVO userVm, FtctlProtectionResponse response, boolean persistState) {
        FtctlStatusAnswer statusAnswer = fetchRuntimeStatus(userVm);
        if (statusAnswer == null) {
            return;
        }
        response.setEnabled("true");
        if (statusAnswer.getMode() != null && !statusAnswer.getMode().isBlank()) {
            response.setMode(statusAnswer.getMode());
        }
        response.setProtectionState(statusAnswer.getProtectionState());
        response.setTransportState(statusAnswer.getTransportState());
        response.setActiveSide(statusAnswer.getActiveSide());
        response.setAdminState(statusAnswer.getAdminState());
        response.setFencingState(statusAnswer.getFencingState());
        response.setLastError(statusAnswer.getLastError());
        if (persistState) {
            persistRuntimeState(userVm, statusAnswer);
        }
    }

    private FtctlStatusAnswer fetchRuntimeStatus(UserVmVO userVm) {
        Long hostId = getExecutionHostId(userVm);
        if (hostId == null || userVm == null) {
            return null;
        }
        try {
            Answer answer = agentManager.send(hostId, new FtctlStatusCommand(userVm.getInstanceName()));
            if (!(answer instanceof FtctlStatusAnswer) || !answer.getResult()) {
                return null;
            }
            return (FtctlStatusAnswer) answer;
        } catch (AgentUnavailableException | OperationTimedoutException ignored) {
            return null;
        }
    }

    private boolean persistRuntimeState(UserVmVO userVm, FtctlStatusAnswer statusAnswer) {
        if (userVm == null || statusAnswer == null) {
            return false;
        }
        Long virtualMachineId = userVm.getId();
        if (statusAnswer.getProtectionState() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_PROTECTION_STATE, statusAnswer.getProtectionState());
        }
        if (statusAnswer.getTransportState() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_TRANSPORT_STATE, statusAnswer.getTransportState());
        }
        if (statusAnswer.getActiveSide() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_ACTIVE_SIDE, statusAnswer.getActiveSide());
        }
        if (statusAnswer.getAdminState() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_ADMIN_STATE, statusAnswer.getAdminState());
        }
        if (statusAnswer.getFencingState() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_FENCING_STATE, statusAnswer.getFencingState());
        }
        if (statusAnswer.getLastError() != null) {
            putVmDetail(virtualMachineId, DETAIL_LAST_ERROR, statusAnswer.getLastError());
        }
        if (statusAnswer.getMode() != null) {
            putVmDetail(virtualMachineId, DETAIL_MODE, statusAnswer.getMode());
        }
        boolean changed = persistProtectionRuntimeState(userVm, statusAnswer);
        if (changed) {
            publishFtctlEvent(userVm, EventTypes.EVENT_FTCTL_PROTECTION_STATE_UPDATE,
                    String.format("Updated FTCTL runtime state for VM %s: protection=%s, transport=%s, activeSide=%s, admin=%s, fencing=%s",
                            userVm.getUuid(), statusAnswer.getProtectionState(), statusAnswer.getTransportState(),
                            statusAnswer.getActiveSide(), statusAnswer.getAdminState(), statusAnswer.getFencingState()));
        }
        return changed;
    }

    private boolean persistProtectionRuntimeState(UserVmVO userVm, FtctlStatusAnswer statusAnswer) {
        FtctlProtectionVO protection = findActiveProtectionForVm(userVm.getId());
        if (protection == null) {
            return false;
        }
        boolean changed = isProtectionRuntimeStateChanged(protection, statusAnswer);
        if (!changed) {
            return false;
        }
        if (statusAnswer.getMode() != null) {
            protection.setMode(statusAnswer.getMode());
        }
        if (statusAnswer.getAdminState() != null) {
            protection.setAdminState(statusAnswer.getAdminState());
        }
        if (statusAnswer.getProtectionState() != null) {
            protection.setProtectionState(statusAnswer.getProtectionState());
        }
        if (statusAnswer.getTransportState() != null) {
            protection.setTransportState(statusAnswer.getTransportState());
        }
        if (statusAnswer.getActiveSide() != null) {
            protection.setActiveSide(statusAnswer.getActiveSide());
        }
        if (statusAnswer.getFencingState() != null) {
            protection.setFencingState(statusAnswer.getFencingState());
        }
        if (statusAnswer.getLastError() != null) {
            protection.setLastError(statusAnswer.getLastError());
        }
        protection.markUpdated();
        ftctlProtectionDao.update(protection.getId(), protection);
        return true;
    }

    private boolean isProtectionRuntimeStateChanged(FtctlProtectionVO protection, FtctlStatusAnswer statusAnswer) {
        if (protection == null || statusAnswer == null) {
            return false;
        }
        return isChanged(protection.getMode(), statusAnswer.getMode()) ||
                isChanged(protection.getAdminState(), statusAnswer.getAdminState()) ||
                isChanged(protection.getProtectionState(), statusAnswer.getProtectionState()) ||
                isChanged(protection.getTransportState(), statusAnswer.getTransportState()) ||
                isChanged(protection.getActiveSide(), statusAnswer.getActiveSide()) ||
                isChanged(protection.getFencingState(), statusAnswer.getFencingState()) ||
                isChanged(protection.getLastError(), statusAnswer.getLastError());
    }

    private boolean isChanged(String existing, String incoming) {
        return incoming != null && !StringUtils.equals(existing, incoming);
    }

    private String resolveFtctlActionEventType(FtctlActionCommand.Action action) {
        if (action == null) {
            return EventTypes.EVENT_FTCTL_PROTECTION_STATE_UPDATE;
        }
        switch (action) {
            case PROTECT:
                return EventTypes.EVENT_FTCTL_PROTECTION_REGISTER;
            case PAUSE_PROTECTION:
                return EventTypes.EVENT_FTCTL_PROTECTION_PAUSE;
            case RESUME_PROTECTION:
                return EventTypes.EVENT_FTCTL_PROTECTION_RESUME;
            case FAILOVER:
                return EventTypes.EVENT_FTCTL_PROTECTION_FAILOVER;
            case FAILBACK:
                return EventTypes.EVENT_FTCTL_PROTECTION_FAILBACK;
            case FENCE_CONFIRM:
                return EventTypes.EVENT_FTCTL_PROTECTION_FENCE_CONFIRM;
            case FENCE_CLEAR:
                return EventTypes.EVENT_FTCTL_PROTECTION_FENCE_CLEAR;
            default:
                return EventTypes.EVENT_FTCTL_PROTECTION_STATE_UPDATE;
        }
    }

    private void publishFtctlEvent(UserVmVO userVm, String eventType, String description) {
        if (userVm == null || StringUtils.isBlank(eventType)) {
            return;
        }
        try {
            ActionEventUtils.onCompletedActionEvent(User.UID_SYSTEM, userVm.getAccountId(), EventVO.LEVEL_INFO, eventType,
                    description, userVm.getId(), ApiCommandResourceType.VirtualMachine.toString(), 0);
        } catch (RuntimeException e) {
            logger.warn(String.format("Unable to publish FTCTL event %s for VM %s", eventType, userVm.getUuid()), e);
        }
    }

    private void syncFtctlContext(UserVmVO userVm, RegisterFtctlProtectionCmd cmd, StoragePoolVO targetStoragePool,
                                  String targetStorageScope, String backendMode, String secondaryTargetDir,
                                  String remoteNbdExportAddr, FtctlIpmiFencingConfig ipmiFencingConfig,
                                  FtctlProtectionProvisioningContext provisioningContext) {
        Long localHostId = requireExecutionHostId(userVm);
        HostVO localHost = hostDao.findById(localHostId);
        HostVO peerHost = hostDao.findById(cmd.getPeerHostId());
        if (localHost == null || peerHost == null) {
            throw new CloudRuntimeException(String.format("Unable to resolve local or peer host for VM %s", userVm.getUuid()));
        }
        validateFtctlHosts(localHost, peerHost, userVm);

        FtctlSyncClusterCommand clusterCommand = new FtctlSyncClusterCommand();
        clusterCommand.setClusterName(buildClusterName(localHost, userVm));
        clusterCommand.setLocalHostId(String.valueOf(localHostId));
        clusterCommand.setLocalRole("primary");
        clusterCommand.setLocalManagementIp(resolveHostField(localHost, HOST_DETAIL_MANAGEMENT_IP, localHost.getPrivateIpAddress()));
        clusterCommand.setLocalLibvirtUri(resolveHostField(localHost, HOST_DETAIL_LIBVIRT_URI,
                String.format("qemu+ssh://%s/system", localHost.getPrivateIpAddress())));
        clusterCommand.setLocalBlockcopyIp(resolveHostField(localHost, HOST_DETAIL_BLOCKCOPY_IP, localHost.getPrivateIpAddress()));
        clusterCommand.setLocalXcoloControlIp(resolveHostField(localHost, HOST_DETAIL_XCOLO_CONTROL_IP, localHost.getPrivateIpAddress()));
        clusterCommand.setLocalXcoloDataIp(resolveHostField(localHost, HOST_DETAIL_XCOLO_DATA_IP, localHost.getPrivateIpAddress()));
        clusterCommand.setPeerHostId(String.valueOf(cmd.getPeerHostId()));
        clusterCommand.setPeerRole("secondary");
        clusterCommand.setPeerManagementIp(resolveHostField(peerHost, HOST_DETAIL_MANAGEMENT_IP, peerHost.getPrivateIpAddress()));
        clusterCommand.setPeerLibvirtUri(resolveHostField(peerHost, HOST_DETAIL_LIBVIRT_URI,
                String.format("qemu+ssh://%s/system", peerHost.getPrivateIpAddress())));
        clusterCommand.setPeerBlockcopyIp(resolveHostField(peerHost, HOST_DETAIL_BLOCKCOPY_IP, peerHost.getPrivateIpAddress()));
        clusterCommand.setPeerXcoloControlIp(resolveHostField(peerHost, HOST_DETAIL_XCOLO_CONTROL_IP, peerHost.getPrivateIpAddress()));
        clusterCommand.setPeerXcoloDataIp(resolveHostField(peerHost, HOST_DETAIL_XCOLO_DATA_IP, peerHost.getPrivateIpAddress()));

        FtctlSyncProfileCommand profileCommand = new FtctlSyncProfileCommand(userVm.getInstanceName(), cmd.getMode(), clusterCommand.getPeerLibvirtUri());
        profileCommand.setProfileName(userVm.getUuid());
        profileCommand.setBackendMode(backendMode);
        profileCommand.setProvisioningBackend(provisioningContext.getProvisioningBackend());
        profileCommand.setProvisioningState(provisioningContext.getProvisioningState());
        profileCommand.setTargetStorageScope(targetStorageScope);
        if (targetStoragePool != null) {
            profileCommand.setTargetStoragePoolId(targetStoragePool.getUuid());
            profileCommand.setTargetStoragePoolName(targetStoragePool.getName());
            profileCommand.setTargetStoragePoolPath(targetStoragePool.getPath());
            if (targetStoragePool.getPoolType() != null) {
                profileCommand.setTargetStoragePoolType(targetStoragePool.getPoolType().name());
            }
        }
        profileCommand.setDiskMap(provisioningContext.getDiskMap());
        profileCommand.setSecondaryVmName(provisioningContext.getSecondaryVmName());
        profileCommand.setFencingPolicy(cmd.getFencingPolicy());
        profileCommand.setSecondaryTargetDir(secondaryTargetDir);
        profileCommand.setRemoteNbdExportAddr(remoteNbdExportAddr);
        profileCommand.setXcoloProxyEndpoint(cmd.getXcoloProxyEndpoint());
        profileCommand.setXcoloNbdEndpoint(cmd.getXcoloNbdEndpoint());
        profileCommand.setXcoloMigrateUri(cmd.getXcoloMigrateUri());
        if (ipmiFencingConfig != null) {
            ipmiFencingConfig.applyTo(profileCommand);
        }

        executeSyncCommand(localHostId, clusterCommand, "cluster");
        executeSyncCommand(localHostId, profileCommand, "profile");
    }

    private void executeSyncCommand(Long hostId, com.cloud.agent.api.Command command, String syncType) {
        try {
            Answer answer = agentManager.send(hostId, command);
            if (!(answer instanceof FtctlSyncAnswer) || !answer.getResult()) {
                throw new CloudRuntimeException(String.format("FTCTL %s sync failed on host %s: %s",
                        syncType, hostId, answer != null ? answer.getDetails() : "no answer"));
            }
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to execute FTCTL %s sync on host %s", syncType, hostId), e);
        }
    }

    private String buildClusterName(HostVO localHost, UserVmVO userVm) {
        if (localHost != null && localHost.getClusterId() > 0) {
            return String.format("cluster-%s", localHost.getClusterId());
        }
        if (userVm.getDataCenterId() > 0) {
            return String.format("zone-%s", userVm.getDataCenterId());
        }
        return "ftctl-cluster";
    }

    private String resolveHostField(HostVO host, String key, String fallback) {
        if (host == null) {
            return fallback;
        }
        DetailVO detail = hostDetailsDao.findDetail(host.getId(), key);
        if (detail != null && detail.getValue() != null && !detail.getValue().isBlank()) {
            return detail.getValue();
        }
        return fallback;
    }

    private void validateFtctlHosts(HostVO localHost, HostVO peerHost, UserVmVO userVm) {
        if (localHost.getId() == peerHost.getId()) {
            throw new CloudRuntimeException(String.format("FTCTL peer host must differ from execution host for VM %s", userVm.getUuid()));
        }
        if (localHost.getType() != Host.Type.Routing || peerHost.getType() != Host.Type.Routing) {
            throw new CloudRuntimeException(String.format("FTCTL requires routing hosts for VM %s", userVm.getUuid()));
        }
        if (!"KVM".equalsIgnoreCase(String.valueOf(localHost.getHypervisorType())) ||
                !"KVM".equalsIgnoreCase(String.valueOf(peerHost.getHypervisorType()))) {
            throw new CloudRuntimeException(String.format("FTCTL requires KVM hosts for VM %s", userVm.getUuid()));
        }
    }

    private FtctlIpmiFencingConfig resolveIpmiFencingConfig(UserVmVO userVm, RegisterFtctlProtectionCmd cmd) {
        if (!FENCING_POLICY_IPMI.equalsIgnoreCase(StringUtils.trimToEmpty(cmd.getFencingPolicy()))) {
            return null;
        }
        Long localHostId = requireExecutionHostId(userVm);
        Long peerHostId = cmd.getPeerHostId();
        if (peerHostId == null) {
            throw new CloudRuntimeException("FTCTL IPMI fencing requires a peer host");
        }
        FtctlIpmiEndpoint primary = resolveIpmiEndpoint(localHostId, "primary");
        FtctlIpmiEndpoint secondary = resolveIpmiEndpoint(peerHostId, "peer");
        return new FtctlIpmiFencingConfig(primary, secondary);
    }

    private FtctlIpmiEndpoint resolveIpmiEndpoint(Long hostId, String role) {
        OutOfBandManagement oobm = hostId == null ? null : outOfBandManagementDao.findByHost(hostId);
        if (oobm == null) {
            throw new CloudRuntimeException(String.format("FTCTL IPMI fencing requires OOBM configuration on %s host %s", role, hostId));
        }
        if (!oobm.isEnabled()) {
            throw new CloudRuntimeException(String.format("FTCTL IPMI fencing requires enabled OOBM on %s host %s", role, hostId));
        }
        if (!OOBM_DRIVER_IPMITOOL.equalsIgnoreCase(StringUtils.trimToEmpty(oobm.getDriver()))) {
            throw new CloudRuntimeException(String.format("FTCTL IPMI fencing requires ipmitool OOBM driver on %s host %s", role, hostId));
        }
        if (StringUtils.isAnyBlank(oobm.getAddress(), oobm.getUsername(), oobm.getPassword())) {
            throw new CloudRuntimeException(String.format("FTCTL IPMI fencing requires OOBM address, username and password on %s host %s", role, hostId));
        }
        return new FtctlIpmiEndpoint(
                oobm.getAddress(),
                StringUtils.trimToNull(oobm.getPort()),
                oobm.getUsername(),
                oobm.getPassword(),
                DEFAULT_IPMI_INTERFACE);
    }

    private void persistIpmiFencingDetails(Long virtualMachineId, FtctlIpmiFencingConfig config) {
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_HOST, config.primary.host);
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_HOST, config.secondary.host);
        if (config.primary.port != null) {
            putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_PORT, config.primary.port);
        }
        if (config.secondary.port != null) {
            putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_PORT, config.secondary.port);
        }
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_USER, config.primary.user);
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_USER, config.secondary.user);
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_INTERFACE, config.primary.ipmiInterface);
        putVmDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_INTERFACE, config.secondary.ipmiInterface);
    }

    private static class FtctlIpmiFencingConfig {
        private final FtctlIpmiEndpoint primary;
        private final FtctlIpmiEndpoint secondary;

        private FtctlIpmiFencingConfig(FtctlIpmiEndpoint primary, FtctlIpmiEndpoint secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        private void applyTo(FtctlSyncProfileCommand command) {
            command.setFencingIpmiPrimaryHost(primary.host);
            command.setFencingIpmiPrimaryPort(primary.port);
            command.setFencingIpmiPrimaryUser(primary.user);
            command.setFencingIpmiPrimaryPassword(primary.password);
            command.setFencingIpmiPrimaryInterface(primary.ipmiInterface);
            command.setFencingIpmiSecondaryHost(secondary.host);
            command.setFencingIpmiSecondaryPort(secondary.port);
            command.setFencingIpmiSecondaryUser(secondary.user);
            command.setFencingIpmiSecondaryPassword(secondary.password);
            command.setFencingIpmiSecondaryInterface(secondary.ipmiInterface);
        }
    }

    private static class FtctlIpmiEndpoint {
        private final String host;
        private final String port;
        private final String user;
        private final String password;
        private final String ipmiInterface;

        private FtctlIpmiEndpoint(String host, String port, String user, String password, String ipmiInterface) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
            this.ipmiInterface = ipmiInterface;
        }
    }

    private List<FtctlEventResponse> parseEvents(String itemsJson) {
        List<FtctlEventResponse> events = new ArrayList<>();
        if (itemsJson == null || itemsJson.isBlank()) {
            return events;
        }
        try {
            JsonElement element = JsonParser.parseString(itemsJson);
            if (!element.isJsonArray()) {
                return events;
            }
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject object = entry.getAsJsonObject();
                FtctlEventResponse response = new FtctlEventResponse();
                response.setObjectName("ftctlevent");
                response.setTimestamp(getJsonString(object, "ts"));
                response.setScanId(getJsonString(object, "scan_id"));
                response.setVmName(getJsonString(object, "vm"));
                response.setStage(getJsonString(object, "stage"));
                response.setEvent(getJsonString(object, "event"));
                response.setResult(getJsonString(object, "result"));
                response.setRc(getJsonInteger(object, "rc"));
                response.setDetails(object.has("details") && object.get("details").isJsonObject()
                        ? object.getAsJsonObject("details").toString() : null);
                events.add(response);
            }
        } catch (RuntimeException ignored) {
            // Keep empty list when events payload cannot be parsed.
        }
        return events;
    }

    private String getJsonString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private Integer getJsonInteger(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<>();
        if (!FtctlServiceEnabled.value()) {
            return cmdList;
        }
        cmdList.add(GetFtctlProtectionCmd.class);
        cmdList.add(RegisterFtctlProtectionCmd.class);
        cmdList.add(GetFtctlCheckCmd.class);
        cmdList.add(GetFtctlEventsCmd.class);
        cmdList.add(GetFtctlHealthCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.PauseFtctlProtectionCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.ResumeFtctlProtectionCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.FailoverFtctlProtectionCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.FailbackFtctlProtectionCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.ConfirmFtctlFenceCmd.class);
        cmdList.add(org.apache.cloudstack.api.command.admin.ftctl.ClearFtctlFenceCmd.class);
        return cmdList;
    }

    @Override
    public String getConfigComponentName() {
        return FtctlService.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { FtctlServiceEnabled };
    }
}
