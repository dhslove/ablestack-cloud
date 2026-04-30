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
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.DetailVO;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
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
import java.util.List;

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

    @Inject
    private VMInstanceDetailsDao vmInstanceDetailsDao;
    @Inject
    private UserVmDao userVmDao;
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

    @Override
    public FtctlProtectionResponse getFtctlProtection(GetFtctlProtectionCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        FtctlProtectionResponse response = buildProtectionResponse(userVm.getId());
        populateRuntimeStateFromAgent(userVm, response);
        response.setObjectName("ftctlprotection");
        return response;
    }

    @Override
    public FtctlProtectionResponse registerFtctlProtection(RegisterFtctlProtectionCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
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

        vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_ENABLED, String.valueOf(true), true);
        vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_MODE, cmd.getMode(), true);
        if (backendMode != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_BACKEND_MODE, backendMode, true);
        }
        vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_PROVISIONING_BACKEND, provisioningContext.getProvisioningBackend(), true);
        vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_PROVISIONING_STATE, provisioningContext.getProvisioningState(), true);
        if (targetStorageScope != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_SCOPE, targetStorageScope, true);
        }
        if (targetStoragePool != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_POOL_ID, targetStoragePool.getUuid(), true);
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_TARGET_STORAGE_POOL_NAME, targetStoragePool.getName(), true);
        }
        if (cmd.getFencingPolicy() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_FENCING_POLICY, cmd.getFencingPolicy(), true);
        }
        if (ipmiFencingConfig != null) {
            persistIpmiFencingDetails(cmd.getVirtualMachineId(), ipmiFencingConfig);
        }
        if (cmd.getPeerHostId() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_PEER_HOST_ID, String.valueOf(cmd.getPeerHostId()), true);
        }
        if (provisioningContext.getSecondaryVmName() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_SECONDARY_VM_NAME, provisioningContext.getSecondaryVmName(), true);
        }
        if (secondaryTargetDir != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_SECONDARY_TARGET_DIR, secondaryTargetDir, true);
        }
        if (remoteNbdExportAddr != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_REMOTE_NBD_EXPORT_ADDR, remoteNbdExportAddr, true);
        }
        if (cmd.getXcoloProxyEndpoint() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_PROXY_ENDPOINT, cmd.getXcoloProxyEndpoint(), true);
        }
        if (cmd.getXcoloNbdEndpoint() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_NBD_ENDPOINT, cmd.getXcoloNbdEndpoint(), true);
        }
        if (cmd.getXcoloMigrateUri() != null) {
            vmInstanceDetailsDao.addDetail(cmd.getVirtualMachineId(), DETAIL_XCOLO_MIGRATE_URI, cmd.getXcoloMigrateUri(), true);
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

        FtctlProtectionResponse response = buildProtectionResponse(userVm.getId());
        populateRuntimeStateFromAgent(userVm, response);
        response.setObjectName("ftctlprotection");
        return response;
    }

    @Override
    public FtctlCheckResponse getFtctlCheck(GetFtctlCheckCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        Long hostId = requireExecutionHostId(userVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlCheckCommand(userVm.getInstanceName()));
            if (!(answer instanceof FtctlCheckAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL check answer type for VM %s", userVm.getUuid()));
            }
            FtctlCheckAnswer checkAnswer = (FtctlCheckAnswer) answer;
            FtctlCheckResponse response = new FtctlCheckResponse();
            response.setObjectName("ftctlcheck");
            response.setVirtualMachineId(userVm.getId());
            response.setVmName(checkAnswer.getVmName());
            response.setResult(checkAnswer.getFtctlResult());
            response.setInventoryResult(checkAnswer.getInventoryResult());
            response.setPrimaryRc(checkAnswer.getPrimaryRc());
            response.setPeerRc(checkAnswer.getPeerRc());
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL check result for VM %s", userVm.getUuid()), e);
        }
    }

    @Override
    public FtctlEventsResponse getFtctlEvents(GetFtctlEventsCmd cmd) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        Long hostId = requireExecutionHostId(userVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlEventsCommand(userVm.getInstanceName(), cmd.getLimit()));
            if (!(answer instanceof FtctlEventsAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL events answer type for VM %s", userVm.getUuid()));
            }
            FtctlEventsAnswer eventsAnswer = (FtctlEventsAnswer) answer;
            FtctlEventsResponse response = new FtctlEventsResponse();
            response.setObjectName("ftctlevents");
            response.setVirtualMachineId(userVm.getId());
            response.setVmName(eventsAnswer.getVmName());
            response.setResult(eventsAnswer.getFtctlResult());
            response.setCount(eventsAnswer.getCount());
            response.setEvents(parseEvents(eventsAnswer.getItemsJson()));
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL events for VM %s", userVm.getUuid()), e);
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
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_PROVISIONING_BACKEND, provisioningBackend, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_PROVISIONING_STATE, FtctlProtectionProvisioningService.STATE_PROVISIONING_FAILED, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_ERROR, e.getMessage(), true);
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
        UserVmVO userVm = validateVirtualMachineExists(cmd.getVirtualMachineId());
        Long hostId = requireExecutionHostId(userVm);
        try {
            Answer answer = agentManager.send(hostId, new FtctlHealthCommand());
            if (!(answer instanceof FtctlHealthAnswer)) {
                throw new CloudRuntimeException(String.format("Unexpected FTCTL health answer type for VM %s", userVm.getUuid()));
            }
            FtctlHealthAnswer healthAnswer = (FtctlHealthAnswer) answer;
            FtctlHealthResponse response = new FtctlHealthResponse();
            response.setObjectName("ftctlhealth");
            response.setVirtualMachineId(userVm.getId());
            response.setHostId(hostId);
            response.setResult(healthAnswer.getFtctlResult());
            response.setUri(healthAnswer.getUri());
            response.setRc(healthAnswer.getRc());
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to get FTCTL health result for VM %s", userVm.getUuid()), e);
        }
    }

    @Override
    public FtctlActionResponse executeFtctlAction(Long virtualMachineId, FtctlActionCommand.Action action, boolean force) throws CloudRuntimeException {
        UserVmVO userVm = validateVirtualMachineExists(virtualMachineId);
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
                persistRuntimeState(userVm.getId(), statusAnswer);
            }
            return response;
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new CloudRuntimeException(String.format("Unable to execute FTCTL action %s for VM %s", action, userVm.getUuid()), e);
        }
    }

    private UserVmVO validateVirtualMachineExists(Long virtualMachineId) {
        UserVmVO userVm = virtualMachineId != null ? userVmDao.findById(virtualMachineId) : null;
        if (userVm == null) {
            throw new CloudRuntimeException(String.format("Unable to find virtual machine with id %s", virtualMachineId));
        }
        return userVm;
    }

    private FtctlProtectionResponse buildProtectionResponse(Long virtualMachineId) {
        FtctlProtectionResponse response = new FtctlProtectionResponse();
        response.setVirtualMachineId(virtualMachineId);
        response.setEnabled(getDetailValue(virtualMachineId, DETAIL_ENABLED));
        response.setMode(getDetailValue(virtualMachineId, DETAIL_MODE));
        response.setBackendMode(getDetailValue(virtualMachineId, DETAIL_BACKEND_MODE));
        response.setProvisioningBackend(getDetailValue(virtualMachineId, DETAIL_PROVISIONING_BACKEND));
        response.setProvisioningState(getDetailValue(virtualMachineId, DETAIL_PROVISIONING_STATE));
        response.setTargetStorageScope(getDetailValue(virtualMachineId, DETAIL_TARGET_STORAGE_SCOPE));
        response.setTargetStoragePoolId(getDetailValue(virtualMachineId, DETAIL_TARGET_STORAGE_POOL_ID));
        response.setTargetStoragePoolName(getDetailValue(virtualMachineId, DETAIL_TARGET_STORAGE_POOL_NAME));
        response.setFencingPolicy(getDetailValue(virtualMachineId, DETAIL_FENCING_POLICY));
        String peerHostId = getDetailValue(virtualMachineId, DETAIL_PEER_HOST_ID);
        response.setPeerHostId(peerHostId);
        response.setPeerHostName(resolvePeerHostName(peerHostId));
        response.setSecondaryVmName(getDetailValue(virtualMachineId, DETAIL_SECONDARY_VM_NAME));
        response.setSecondaryTargetDir(getDetailValue(virtualMachineId, DETAIL_SECONDARY_TARGET_DIR));
        response.setRemoteNbdExportAddr(getDetailValue(virtualMachineId, DETAIL_REMOTE_NBD_EXPORT_ADDR));
        response.setXcoloProxyEndpoint(getDetailValue(virtualMachineId, DETAIL_XCOLO_PROXY_ENDPOINT));
        response.setXcoloNbdEndpoint(getDetailValue(virtualMachineId, DETAIL_XCOLO_NBD_ENDPOINT));
        response.setXcoloMigrateUri(getDetailValue(virtualMachineId, DETAIL_XCOLO_MIGRATE_URI));
        response.setProtectionState(getDetailValue(virtualMachineId, DETAIL_LAST_PROTECTION_STATE));
        response.setTransportState(getDetailValue(virtualMachineId, DETAIL_LAST_TRANSPORT_STATE));
        response.setActiveSide(getDetailValue(virtualMachineId, DETAIL_LAST_ACTIVE_SIDE));
        response.setAdminState(getDetailValue(virtualMachineId, DETAIL_LAST_ADMIN_STATE));
        response.setFencingState(getDetailValue(virtualMachineId, DETAIL_LAST_FENCING_STATE));
        response.setLastError(getDetailValue(virtualMachineId, DETAIL_LAST_ERROR));
        return response;
    }

    private String resolvePeerHostName(String peerHostId) {
        if (peerHostId == null || peerHostId.isBlank()) {
            return null;
        }
        try {
            HostVO host = hostDao.findById(Long.parseLong(peerHostId));
            return host != null ? host.getName() : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getDetailValue(Long virtualMachineId, String key) {
        VMInstanceDetailVO detail = vmInstanceDetailsDao.findDetail(virtualMachineId, key);
        return detail != null ? detail.getValue() : null;
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

    private void populateRuntimeStateFromAgent(UserVmVO userVm, FtctlProtectionResponse response) {
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
            persistRuntimeState(userVm.getId(), statusAnswer);
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

    private void persistRuntimeState(Long virtualMachineId, FtctlStatusAnswer statusAnswer) {
        if (virtualMachineId == null || statusAnswer == null) {
            return;
        }
        if (statusAnswer.getProtectionState() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_PROTECTION_STATE, statusAnswer.getProtectionState(), true);
        }
        if (statusAnswer.getTransportState() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_TRANSPORT_STATE, statusAnswer.getTransportState(), true);
        }
        if (statusAnswer.getActiveSide() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_ACTIVE_SIDE, statusAnswer.getActiveSide(), true);
        }
        if (statusAnswer.getAdminState() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_ADMIN_STATE, statusAnswer.getAdminState(), true);
        }
        if (statusAnswer.getFencingState() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_FENCING_STATE, statusAnswer.getFencingState(), true);
        }
        if (statusAnswer.getLastError() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_LAST_ERROR, statusAnswer.getLastError(), true);
        }
        if (statusAnswer.getMode() != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_MODE, statusAnswer.getMode(), true);
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
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_HOST, config.primary.host, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_HOST, config.secondary.host, true);
        if (config.primary.port != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_PORT, config.primary.port, true);
        }
        if (config.secondary.port != null) {
            vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_PORT, config.secondary.port, true);
        }
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_USER, config.primary.user, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_USER, config.secondary.user, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_PRIMARY_INTERFACE, config.primary.ipmiInterface, true);
        vmInstanceDetailsDao.addDetail(virtualMachineId, DETAIL_FENCING_IPMI_SECONDARY_INTERFACE, config.secondary.ipmiInterface, true);
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
