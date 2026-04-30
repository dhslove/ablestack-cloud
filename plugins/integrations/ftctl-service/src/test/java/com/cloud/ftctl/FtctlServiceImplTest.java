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
import com.cloud.agent.api.Command;
import com.cloud.agent.api.FtctlActionAnswer;
import com.cloud.agent.api.FtctlActionCommand;
import com.cloud.agent.api.FtctlCheckAnswer;
import com.cloud.agent.api.FtctlCheckCommand;
import com.cloud.agent.api.FtctlEventsCommand;
import com.cloud.agent.api.FtctlEventsAnswer;
import com.cloud.agent.api.FtctlHealthAnswer;
import com.cloud.agent.api.FtctlHealthCommand;
import com.cloud.agent.api.FtctlStatusAnswer;
import com.cloud.agent.api.FtctlStatusCommand;
import com.cloud.agent.api.FtctlSyncAnswer;
import com.cloud.agent.api.FtctlSyncClusterCommand;
import com.cloud.agent.api.FtctlSyncProfileCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.ScopeType;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlCheckCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlEventsCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlHealthCmd;
import org.apache.cloudstack.api.command.admin.ftctl.RegisterFtctlProtectionCmd;
import org.apache.cloudstack.api.response.ftctl.FtctlActionResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlCheckResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlEventResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlEventsResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlHealthResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlProtectionResponse;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement;
import org.apache.cloudstack.outofbandmanagement.dao.OutOfBandManagementDao;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class FtctlServiceImplTest {

    @Mock
    private VMInstanceDetailsDao vmInstanceDetailsDao;
    @Mock
    private UserVmDao userVmDao;
    @Mock
    private AgentManager agentManager;
    @Mock
    private HostDetailsDao hostDetailsDao;
    @Mock
    private HostDao hostDao;
    @Mock
    private PrimaryDataStoreDao primaryDataStoreDao;
    @Mock
    private OutOfBandManagementDao outOfBandManagementDao;

    @InjectMocks
    private FtctlServiceImpl ftctlService;

    private UserVmVO userVm;
    private final Map<String, String> vmDetails = new HashMap<>();
    private final Map<String, String> hostDetails = new HashMap<>();

    @Before
    public void setup() throws Exception {
        userVm = Mockito.mock(UserVmVO.class);
        Mockito.when(userVm.getId()).thenReturn(101L);
        Mockito.when(userVm.getUuid()).thenReturn("vm-uuid");
        Mockito.when(userVm.getInstanceName()).thenReturn("vm-name");
        Mockito.when(userVm.getHostId()).thenReturn(201L);
        Mockito.lenient().when(userVm.getDataCenterId()).thenReturn(401L);
        Mockito.when(userVmDao.findById(101L)).thenReturn(userVm);

        Mockito.lenient().doAnswer(invocation -> {
            Long vmId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String value = invocation.getArgument(2);
            vmDetails.put(vmId + ":" + key, value);
            return null;
        }).when(vmInstanceDetailsDao).addDetail(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());

        Mockito.lenient().when(vmInstanceDetailsDao.findDetail(Mockito.anyLong(), Mockito.anyString())).thenAnswer(invocation -> {
            Long vmId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String value = vmDetails.get(vmId + ":" + key);
            if (value == null) {
                return null;
            }
            VMInstanceDetailVO detail = Mockito.mock(VMInstanceDetailVO.class);
            Mockito.when(detail.getValue()).thenReturn(value);
            return detail;
        });

        Mockito.lenient().when(hostDetailsDao.findDetail(Mockito.anyLong(), Mockito.anyString())).thenAnswer(invocation -> {
            Long hostId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String value = hostDetails.get(hostId + ":" + key);
            if (value == null) {
                return null;
            }
            DetailVO detail = Mockito.mock(DetailVO.class);
            Mockito.when(detail.getValue()).thenReturn(value);
            return detail;
        });
    }

    @Test
    public void testGetFtctlEventsParsesItemsJson() throws Exception {
        GetFtctlEventsCmd cmd = new GetFtctlEventsCmd();
        setField(cmd, "virtualMachineId", 101L);
        setField(cmd, "limit", 10);

        String itemsJson = "[" +
                "{\"ts\":\"2026-04-18T10:00:00+09:00\",\"scan_id\":\"scan-1\",\"vm\":\"vm-name\",\"stage\":\"health\",\"event\":\"reconcile.tick\",\"result\":\"ok\"}," +
                "{\"ts\":\"2026-04-18T10:02:00+09:00\",\"scan_id\":\"scan-2\",\"vm\":\"vm-name\",\"stage\":\"rearm\",\"event\":\"rearm.defer\",\"result\":\"warn\",\"details\":{\"reason\":\"backoff\"}}" +
                "]";
        FtctlEventsCommand eventsCommand = new FtctlEventsCommand("vm-name", 10);
        FtctlEventsAnswer answer = new FtctlEventsAnswer(eventsCommand, true, "OK", "ok", "vm-name", 2, itemsJson);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(answer);

        FtctlEventsResponse response = ftctlService.getFtctlEvents(cmd);

        Assert.assertEquals(Long.valueOf(101L), response.getVirtualMachineId());
        Assert.assertEquals("vm-name", response.getVmName());
        Assert.assertEquals("ok", response.getResult());
        Assert.assertEquals(Integer.valueOf(2), response.getCount());

        List<FtctlEventResponse> events = response.getEvents();
        Assert.assertEquals(2, events.size());
        Assert.assertEquals("health", events.get(0).getStage());
        Assert.assertEquals("reconcile.tick", events.get(0).getEvent());
        Assert.assertEquals("warn", events.get(1).getResult());
        Assert.assertTrue(events.get(1).getDetails().contains("backoff"));
    }

    @Test
    public void testGetFtctlCheckParsesAnswer() throws Exception {
        GetFtctlCheckCmd cmd = new GetFtctlCheckCmd();
        setField(cmd, "virtualMachineId", 101L);

        FtctlCheckCommand checkCommand = new FtctlCheckCommand("vm-name");
        FtctlCheckAnswer answer = new FtctlCheckAnswer(checkCommand, true, "OK", "ok", "healthy", "vm-name", 0, 1);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(answer);

        FtctlCheckResponse response = ftctlService.getFtctlCheck(cmd);

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("vm-name", getFieldValue(response, "vmName"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals("healthy", getFieldValue(response, "inventoryResult"));
        Assert.assertEquals(Integer.valueOf(0), getFieldValue(response, "primaryRc"));
        Assert.assertEquals(Integer.valueOf(1), getFieldValue(response, "peerRc"));
    }

    @Test
    public void testGetFtctlHealthParsesAnswer() throws Exception {
        GetFtctlHealthCmd cmd = new GetFtctlHealthCmd();
        setField(cmd, "virtualMachineId", 101L);

        FtctlHealthCommand healthCommand = new FtctlHealthCommand();
        FtctlHealthAnswer answer = new FtctlHealthAnswer(healthCommand, true, "OK", "ok", "qemu+ssh://10.0.0.11/system", 0);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(answer);

        FtctlHealthResponse response = ftctlService.getFtctlHealth(cmd);

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals(Long.valueOf(201L), getFieldValue(response, "hostId"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals("qemu+ssh://10.0.0.11/system", getFieldValue(response, "uri"));
        Assert.assertEquals(Integer.valueOf(0), getFieldValue(response, "rc"));
    }

    @Test
    public void testExecuteFtctlActionRefreshesRuntimeState() throws Exception {
        FtctlActionCommand actionCommand = new FtctlActionCommand(FtctlActionCommand.Action.PAUSE_PROTECTION, "vm-name");
        FtctlActionAnswer actionAnswer = new FtctlActionAnswer(actionCommand, true, "OK",
                FtctlActionCommand.Action.PAUSE_PROTECTION, "ok", 0, "paused");
        FtctlStatusCommand statusCommand = new FtctlStatusCommand("vm-name");
        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(statusCommand, true, "OK", "ok", "vm-name",
                "ft", "protected", "mirroring", "primary", "paused", "clear", "",
                "2026-04-18T16:55:00+09:00", 2, 0);

        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(actionAnswer)
                .thenReturn(statusAnswer);

        FtctlActionResponse response = ftctlService.executeFtctlAction(101L, FtctlActionCommand.Action.PAUSE_PROTECTION, false);

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("vm-name", getFieldValue(response, "vmName"));
        Assert.assertEquals("PAUSE_PROTECTION", getFieldValue(response, "action"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals(Integer.valueOf(0), getFieldValue(response, "exitCode"));
        Assert.assertEquals("paused", getFieldValue(response, "output"));
        Assert.assertEquals("ft", getFieldValue(response, "mode"));
        Assert.assertEquals("protected", getFieldValue(response, "protectionState"));
        Assert.assertEquals("mirroring", getFieldValue(response, "transportState"));
        Assert.assertEquals("primary", getFieldValue(response, "activeSide"));
        Assert.assertEquals("paused", getFieldValue(response, "adminState"));
        Assert.assertEquals("clear", getFieldValue(response, "fencingState"));

        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.protection.state", "protected", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.transport.state", "mirroring", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.active.side", "primary", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.admin.state", "paused", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.fencing.state", "clear", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.mode", "ft", true);
    }

    @Test
    public void testRegisterFtctlProtectionSyncsContextAndProtectsVm() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);
        hostDetails.put("201:ftctl.management.ip", "192.168.0.11");
        hostDetails.put("202:ftctl.libvirt.uri", "qemu+ssh://peer-ft/system");

        FtctlSyncAnswer clusterAnswer = new FtctlSyncAnswer(new FtctlSyncClusterCommand(), true, "OK", "ok", 0, "cluster-synced");
        FtctlSyncAnswer profileAnswer = new FtctlSyncAnswer(new FtctlSyncProfileCommand(), true, "OK", "ok", 0, "profile-synced");
        FtctlActionCommand protectCommand = new FtctlActionCommand(FtctlActionCommand.Action.PROTECT, "vm-name");
        FtctlActionAnswer protectAnswer = new FtctlActionAnswer(protectCommand, true, "OK",
                FtctlActionCommand.Action.PROTECT, "ok", 0, "protected");
        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "dr", "protected", "replicating", "primary", "running", "clear", "",
                "2026-04-18T18:10:00+09:00", 0, 0);

        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(clusterAnswer)
                .thenReturn(profileAnswer)
                .thenReturn(protectAnswer)
                .thenReturn(statusAnswer);

        FtctlProtectionResponse response = ftctlService.registerFtctlProtection(cmd);

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager, Mockito.times(4)).send(Mockito.eq(201L), commandCaptor.capture());
        List<Command> commands = commandCaptor.getAllValues();
        Assert.assertEquals(4, commands.size());
        Assert.assertTrue(commands.get(0) instanceof FtctlSyncClusterCommand);
        Assert.assertTrue(commands.get(1) instanceof FtctlSyncProfileCommand);
        Assert.assertTrue(commands.get(2) instanceof FtctlActionCommand);
        Assert.assertTrue(commands.get(3) instanceof FtctlStatusCommand);

        FtctlSyncClusterCommand clusterCommand = (FtctlSyncClusterCommand) commands.get(0);
        Assert.assertEquals("cluster-301", clusterCommand.getClusterName());
        Assert.assertEquals("201", clusterCommand.getLocalHostId());
        Assert.assertEquals("primary", clusterCommand.getLocalRole());
        Assert.assertEquals("192.168.0.11", clusterCommand.getLocalManagementIp());
        Assert.assertEquals("qemu+ssh://10.0.0.11/system", clusterCommand.getLocalLibvirtUri());
        Assert.assertEquals("202", clusterCommand.getPeerHostId());
        Assert.assertEquals("secondary", clusterCommand.getPeerRole());
        Assert.assertEquals("qemu+ssh://peer-ft/system", clusterCommand.getPeerLibvirtUri());

        FtctlSyncProfileCommand profileCommand = (FtctlSyncProfileCommand) commands.get(1);
        Assert.assertEquals("vm-name", profileCommand.getVmName());
        Assert.assertEquals("dr", profileCommand.getMode());
        Assert.assertEquals("qemu+ssh://peer-ft/system", profileCommand.getPeerUri());
        Assert.assertEquals("vm-uuid", profileCommand.getProfileName());
        Assert.assertEquals("remote-nbd", profileCommand.getBackendMode());
        Assert.assertEquals("secondary-local", profileCommand.getTargetStorageScope());
        Assert.assertEquals("pool-uuid", profileCommand.getTargetStoragePoolId());
        Assert.assertEquals("pool-name", profileCommand.getTargetStoragePoolName());
        Assert.assertEquals("vm-name-secondary", profileCommand.getSecondaryVmName());
        Assert.assertEquals("manual-block", profileCommand.getFencingPolicy());
        Assert.assertEquals("/data/secondary", profileCommand.getSecondaryTargetDir());
        Assert.assertEquals("10.0.0.12:10809", profileCommand.getRemoteNbdExportAddr());

        FtctlActionCommand capturedProtectCommand = (FtctlActionCommand) commands.get(2);
        Assert.assertEquals(FtctlActionCommand.Action.PROTECT, capturedProtectCommand.getAction());
        Assert.assertEquals("vm-name", capturedProtectCommand.getVmName());
        Assert.assertEquals("dr", capturedProtectCommand.getMode());
        Assert.assertEquals("qemu+ssh://peer-ft/system", capturedProtectCommand.getPeerUri());
        Assert.assertEquals("remote-nbd", capturedProtectCommand.getContextParam("ftctl.backend.mode"));
        Assert.assertEquals("manual-block", capturedProtectCommand.getContextParam("ftctl.fencing.policy"));

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("true", getFieldValue(response, "enabled"));
        Assert.assertEquals("dr", getFieldValue(response, "mode"));
        Assert.assertEquals("remote-nbd", getFieldValue(response, "backendMode"));
        Assert.assertEquals("pool-uuid", getFieldValue(response, "targetStoragePoolId"));
        Assert.assertEquals("pool-name", getFieldValue(response, "targetStoragePoolName"));
        Assert.assertEquals("202", getFieldValue(response, "peerHostId"));
        Assert.assertEquals("host-202", getFieldValue(response, "peerHostName"));
        Assert.assertEquals("protected", getFieldValue(response, "protectionState"));
        Assert.assertEquals("replicating", getFieldValue(response, "transportState"));
        Assert.assertEquals("primary", getFieldValue(response, "activeSide"));

        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.enabled", "true", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.backend.mode", "remote-nbd", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.target.storage.pool.id", "pool-uuid", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.target.storage.pool.name", "pool-name", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.peer.host.id", "202", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.protection.state", "protected", true);
    }

    @Test
    public void testRegisterFtctlProtectionWithIpmiFencingSyncsOobmProfileFields() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        setField(cmd, "fencingPolicy", "ipmi");
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);
        OutOfBandManagement localOobm = mockOobm("10.10.10.201", "623", "admin-a", "password-a");
        OutOfBandManagement peerOobm = mockOobm("10.10.10.202", "624", "admin-b", "password-b");
        Mockito.when(outOfBandManagementDao.findByHost(201L)).thenReturn(localOobm);
        Mockito.when(outOfBandManagementDao.findByHost(202L)).thenReturn(peerOobm);

        FtctlSyncAnswer clusterAnswer = new FtctlSyncAnswer(new FtctlSyncClusterCommand(), true, "OK", "ok", 0, "cluster-synced");
        FtctlSyncAnswer profileAnswer = new FtctlSyncAnswer(new FtctlSyncProfileCommand(), true, "OK", "ok", 0, "profile-synced");
        FtctlActionAnswer protectAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.PROTECT, "vm-name"), true, "OK",
                FtctlActionCommand.Action.PROTECT, "ok", 0, "protected");
        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "dr", "protected", "replicating", "primary", "running", "clear", "",
                "2026-04-18T18:10:00+09:00", 0, 0);

        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(clusterAnswer)
                .thenReturn(profileAnswer)
                .thenReturn(protectAnswer)
                .thenReturn(statusAnswer);

        ftctlService.registerFtctlProtection(cmd);

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager, Mockito.times(4)).send(Mockito.eq(201L), commandCaptor.capture());
        FtctlSyncProfileCommand profileCommand = (FtctlSyncProfileCommand) commandCaptor.getAllValues().get(1);
        Assert.assertEquals("10.10.10.201", profileCommand.getFencingIpmiPrimaryHost());
        Assert.assertEquals("623", profileCommand.getFencingIpmiPrimaryPort());
        Assert.assertEquals("admin-a", profileCommand.getFencingIpmiPrimaryUser());
        Assert.assertEquals("password-a", profileCommand.getFencingIpmiPrimaryPassword());
        Assert.assertEquals("lanplus", profileCommand.getFencingIpmiPrimaryInterface());
        Assert.assertEquals("10.10.10.202", profileCommand.getFencingIpmiSecondaryHost());
        Assert.assertEquals("624", profileCommand.getFencingIpmiSecondaryPort());
        Assert.assertEquals("admin-b", profileCommand.getFencingIpmiSecondaryUser());
        Assert.assertEquals("password-b", profileCommand.getFencingIpmiSecondaryPassword());
        Assert.assertEquals("lanplus", profileCommand.getFencingIpmiSecondaryInterface());
        Mockito.verify(vmInstanceDetailsDao, Mockito.never()).addDetail(Mockito.eq(101L), Mockito.contains("password"), Mockito.anyString(), Mockito.anyBoolean());
    }

    @Test(expected = CloudRuntimeException.class)
    public void testRegisterFtctlProtectionFailsWhenClusterSyncFails() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);

        FtctlSyncClusterCommand clusterCommand = new FtctlSyncClusterCommand();
        FtctlSyncAnswer clusterAnswer = new FtctlSyncAnswer(clusterCommand, false, "cluster failed", "error", 1, "sync failed");
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(clusterAnswer);

        try {
            ftctlService.registerFtctlProtection(cmd);
        } finally {
            Mockito.verify(agentManager, Mockito.times(1)).send(Mockito.eq(201L), Mockito.any(Command.class));
        }
    }

    @Test(expected = CloudRuntimeException.class)
    public void testGetFtctlEventsFailsWhenVmMissing() {
        GetFtctlEventsCmd cmd = new GetFtctlEventsCmd();
        setField(cmd, "virtualMachineId", 999L);
        Mockito.when(userVmDao.findById(999L)).thenReturn(null);
        ftctlService.getFtctlEvents(cmd);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testGetFtctlEventsFailsOnAgentException() throws Exception {
        GetFtctlEventsCmd cmd = new GetFtctlEventsCmd();
        setField(cmd, "virtualMachineId", 101L);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenThrow(new AgentUnavailableException(201L));
        ftctlService.getFtctlEvents(cmd);
    }

    @Test(expected = CloudRuntimeException.class)
    public void testGetFtctlHealthFailsOnAgentTimeout() throws Exception {
        GetFtctlHealthCmd cmd = new GetFtctlHealthCmd();
        setField(cmd, "virtualMachineId", 101L);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenThrow(new OperationTimedoutException(null, 201L, 0L, 0, false));
        ftctlService.getFtctlHealth(cmd);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to set field " + fieldName, e);
        }
    }

    private Object getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to read field " + fieldName, e);
        }
    }

    private RegisterFtctlProtectionCmd buildRegisterCmd() {
        RegisterFtctlProtectionCmd cmd = new RegisterFtctlProtectionCmd();
        setField(cmd, "virtualMachineId", 101L);
        setField(cmd, "mode", "dr");
        setField(cmd, "backendMode", "remote-nbd");
        setField(cmd, "targetStorageScope", "host");
        setField(cmd, "targetStoragePoolId", 501L);
        setField(cmd, "fencingPolicy", "manual-block");
        setField(cmd, "peerHostId", 202L);
        setField(cmd, "secondaryVmName", "vm-name-secondary");
        setField(cmd, "secondaryTargetDir", "/data/secondary");
        setField(cmd, "remoteNbdExportAddr", "10.0.0.12:10809");
        StoragePoolVO storagePool = mockStoragePool();
        Mockito.lenient().when(primaryDataStoreDao.findById(501L)).thenReturn(storagePool);
        return cmd;
    }

    private StoragePoolVO mockStoragePool() {
        StoragePoolVO storagePool = Mockito.mock(StoragePoolVO.class);
        Mockito.when(storagePool.getUuid()).thenReturn("pool-uuid");
        Mockito.when(storagePool.getName()).thenReturn("pool-name");
        Mockito.when(storagePool.getDataCenterId()).thenReturn(401L);
        Mockito.when(storagePool.getScope()).thenReturn(ScopeType.HOST);
        return storagePool;
    }

    private HostVO mockHost(Long id, Long clusterId, String privateIp) {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(id);
        Mockito.when(host.getName()).thenReturn(String.format("host-%s", id));
        Mockito.when(host.getClusterId()).thenReturn(clusterId);
        Mockito.when(host.getPrivateIpAddress()).thenReturn(privateIp);
        Mockito.when(host.getType()).thenReturn(Host.Type.Routing);
        Mockito.when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        return host;
    }

    private OutOfBandManagement mockOobm(String address, String port, String username, String password) {
        OutOfBandManagement oobm = Mockito.mock(OutOfBandManagement.class);
        Mockito.when(oobm.isEnabled()).thenReturn(true);
        Mockito.when(oobm.getDriver()).thenReturn("ipmitool");
        Mockito.when(oobm.getAddress()).thenReturn(address);
        Mockito.when(oobm.getPort()).thenReturn(port);
        Mockito.when(oobm.getUsername()).thenReturn(username);
        Mockito.when(oobm.getPassword()).thenReturn(password);
        return oobm;
    }
}
