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
import com.cloud.ftctl.dao.FtctlProtectionDao;
import com.cloud.ftctl.dao.FtctlProtectionVolumeDao;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.ScopeType;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicVO;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmService;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import com.cloud.utils.Pair;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
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
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement;
import org.apache.cloudstack.outofbandmanagement.dao.OutOfBandManagementDao;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
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
    private NicDao nicDao;
    @Mock
    private UserVmManager userVmManager;
    @Mock
    private UserVmService userVmService;
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
    @Mock
    private FtctlProtectionProvisioningService ftctlProtectionProvisioningService;
    @Mock
    private FtctlProtectionDao ftctlProtectionDao;
    @Mock
    private FtctlProtectionVolumeDao ftctlProtectionVolumeDao;
    @Mock
    private VolumeDao volumeDao;
    @Mock
    private VolumeApiService volumeApiService;

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
        Mockito.lenient().when(userVm.getDisplayName()).thenReturn("Primary VM");
        Mockito.lenient().when(userVm.getHostName()).thenReturn("vm-name");
        Mockito.lenient().when(userVm.getAccountId()).thenReturn(501L);
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

        Mockito.lenient().doAnswer(invocation -> {
            Long vmId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            vmDetails.remove(vmId + ":" + key);
            return null;
        }).when(vmInstanceDetailsDao).removeDetail(Mockito.anyLong(), Mockito.anyString());

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

        Mockito.lenient().when(ftctlProtectionProvisioningService.prepareProtection(Mockito.any())).thenAnswer(invocation -> {
            FtctlProtectionProvisioningRequest request = invocation.getArgument(0);
            return new FtctlProtectionProvisioningContext(
                    FtctlProtectionProvisioningService.BACKEND_LIBVIRT_MANAGED,
                    FtctlProtectionProvisioningService.STATE_READY,
                    request.getSecondaryVmName(),
                    null);
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

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmName("i-2-309-VM");
        protection.setActiveSide("secondary");
        protection.setProvisioningBackend(FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        FtctlCheckCommand checkCommand = new FtctlCheckCommand("vm-name");
        FtctlCheckAnswer answer = new FtctlCheckAnswer(checkCommand, true, "OK", "ok", "healthy", "vm-name", 0, 1,
                false, "not-defined-expected", "cloud-managed");
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(answer);

        FtctlCheckResponse response = ftctlService.getFtctlCheck(cmd);

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("vm-name", getFieldValue(response, "vmName"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals("healthy", getFieldValue(response, "inventoryResult"));
        Assert.assertEquals(Integer.valueOf(0), getFieldValue(response, "primaryRc"));
        Assert.assertEquals(Integer.valueOf(1), getFieldValue(response, "peerRc"));
        Assert.assertEquals(Boolean.FALSE, getFieldValue(response, "peerDomainExpected"));
        Assert.assertEquals("not-defined-expected", getFieldValue(response, "standbyDomainState"));
        Assert.assertEquals("cloud-managed", getFieldValue(response, "provisioningBackend"));

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager).send(Mockito.eq(201L), commandCaptor.capture());
        FtctlCheckCommand sentCommand = (FtctlCheckCommand) commandCaptor.getValue();
        Assert.assertEquals("vm-name", sentCommand.getVmName());
        Assert.assertEquals("i-2-309-VM", sentCommand.getSecondaryVmName());
        Assert.assertEquals("secondary", sentCommand.getActiveSide());
        Assert.assertEquals("cloud-managed", sentCommand.getProvisioningBackend());
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
        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

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
        Mockito.verify(ftctlProtectionDao).update(Mockito.eq(0L), Mockito.same(protection));
        Assert.assertEquals("ft", protection.getMode());
        Assert.assertEquals("protected", protection.getProtectionState());
        Assert.assertEquals("mirroring", protection.getTransportState());
        Assert.assertEquals("primary", protection.getActiveSide());
        Assert.assertEquals("paused", protection.getAdminState());
        Assert.assertEquals("clear", protection.getFencingState());
    }

    @Test
    public void testExecuteFtctlActionRetriesLockedAction() throws Exception {
        FtctlActionCommand actionCommand = new FtctlActionCommand(FtctlActionCommand.Action.PAUSE_PROTECTION, "vm-name");
        FtctlActionAnswer lockedAnswer = new FtctlActionAnswer(actionCommand, false,
                "{\"command\":\"pause\",\"result\":\"locked\",\"lock_file\":\"/run/ablestack-vm-ftctl/lock\"}",
                FtctlActionCommand.Action.PAUSE_PROTECTION, "locked", 20,
                "{\"command\":\"pause\",\"result\":\"locked\",\"lock_file\":\"/run/ablestack-vm-ftctl/lock\"}");
        FtctlActionAnswer actionAnswer = new FtctlActionAnswer(actionCommand, true, "OK",
                FtctlActionCommand.Action.PAUSE_PROTECTION, "ok", 0, "paused");
        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ft", "protected", "mirroring", "primary", "paused", "clear", "",
                "2026-05-06T09:00:00+09:00", 0, 0);
        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(lockedAnswer)
                .thenReturn(actionAnswer)
                .thenReturn(statusAnswer);

        FtctlActionResponse response = ftctlService.executeFtctlAction(101L, FtctlActionCommand.Action.PAUSE_PROTECTION, false);

        Assert.assertEquals("PAUSE_PROTECTION", getFieldValue(response, "action"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals("paused", getFieldValue(response, "adminState"));
        Mockito.verify(agentManager, Mockito.times(3)).send(Mockito.eq(201L), Mockito.any(Command.class));
    }

    @Test
    public void testReleaseFtctlProtectionExpungesStandbyVolumesAndRemovesRows() throws Exception {
        UserVO caller = new UserVO();
        AccountVO account = new AccountVO("admin", 1L, "ROOT", Account.Type.ADMIN, "account-uuid");
        CallContext.register(caller, account);
        try {
            UserVmVO secondaryVm = Mockito.mock(UserVmVO.class);
            Mockito.when(secondaryVm.getId()).thenReturn(202L);
            Mockito.when(secondaryVm.getUuid()).thenReturn("secondary-uuid");
            Mockito.when(secondaryVm.getState()).thenReturn(VirtualMachine.State.Stopped);
            Mockito.when(secondaryVm.getRemoved()).thenReturn(null);
            Mockito.when(userVmDao.findById(202L)).thenReturn(secondaryVm);

            FtctlProtectionVO protection = new FtctlProtectionVO(101L);
            setField(protection, "id", 801L);
            protection.setSecondaryVmId(202L);
            protection.setProvisioningBackend(FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED);
            protection.setActiveSide("primary");
            Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

            FtctlProtectionVolumeVO rootMapping = new FtctlProtectionVolumeVO(801L, 301L);
            setField(rootMapping, "id", 901L);
            rootMapping.setSecondaryVolumeId(401L);
            rootMapping.setDiskLabel("root-0");
            FtctlProtectionVolumeVO dataMapping = new FtctlProtectionVolumeVO(801L, 302L);
            setField(dataMapping, "id", 902L);
            dataMapping.setSecondaryVolumeId(402L);
            dataMapping.setDiskLabel("data-1");
            Mockito.when(ftctlProtectionVolumeDao.listActiveByProtectionId(801L)).thenReturn(List.of(rootMapping, dataMapping));

            VolumeVO rootVolume = Mockito.mock(VolumeVO.class);
            Mockito.when(rootVolume.getId()).thenReturn(401L);
            Mockito.when(rootVolume.getInstanceId()).thenReturn(202L);
            Mockito.when(rootVolume.getState()).thenReturn(Volume.State.Ready);
            Mockito.when(rootVolume.getRemoved()).thenReturn(null);

            VolumeVO dataVolume = Mockito.mock(VolumeVO.class);
            Mockito.when(dataVolume.getId()).thenReturn(402L);
            Mockito.when(dataVolume.getInstanceId()).thenReturn(202L);
            Mockito.when(dataVolume.getState()).thenReturn(Volume.State.Ready);
            Mockito.when(dataVolume.getRemoved()).thenReturn(null);

            Mockito.when(volumeDao.findByInstance(202L)).thenReturn(List.of(rootVolume, dataVolume));
            Mockito.when(volumeDao.findById(401L)).thenReturn(rootVolume);
            Mockito.when(volumeDao.findById(402L)).thenReturn(dataVolume);
            Mockito.when(volumeApiService.destroyVolume(Mockito.anyLong(), Mockito.eq(account), Mockito.eq(true), Mockito.eq(true)))
                    .thenReturn(rootVolume);
            Mockito.when(userVmManager.expunge(secondaryVm)).thenReturn(true);

            FtctlActionCommand actionCommand = new FtctlActionCommand(FtctlActionCommand.Action.UNPROTECT, "vm-name");
            FtctlActionAnswer actionAnswer = new FtctlActionAnswer(actionCommand, true, "OK",
                    FtctlActionCommand.Action.UNPROTECT, "ok", 0, "{\"result\":\"ok\"}");
            FtctlStatusCommand statusCommand = new FtctlStatusCommand("vm-name");
            FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(statusCommand, true, "OK", "ok", "vm-name",
                    "ha", "disabled", "stopped", "primary", "inactive", "clear", "",
                    "2026-05-04T09:30:00+09:00", 0, 0);
            Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                    .thenReturn(actionAnswer)
                    .thenReturn(statusAnswer);

            FtctlActionResponse response = ftctlService.releaseFtctlProtection(101L, true);

            Assert.assertEquals("UNPROTECT", getFieldValue(response, "action"));
            Assert.assertEquals("disabled", getFieldValue(response, "protectionState"));
            Mockito.verify(userVmService).destroyVm(202L, true);
            Mockito.verify(userVmManager).expunge(secondaryVm);
            Mockito.verify(volumeDao).detachVolume(401L);
            Mockito.verify(volumeDao).detachVolume(402L);
            Mockito.verify(volumeApiService).destroyVolume(401L, account, true, true);
            Mockito.verify(volumeApiService).destroyVolume(402L, account, true, true);
            Mockito.verify(ftctlProtectionVolumeDao).remove(901L);
            Mockito.verify(ftctlProtectionVolumeDao).remove(902L);
            Mockito.verify(ftctlProtectionDao).remove(801L);
            ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
            Mockito.verify(agentManager, Mockito.atLeastOnce()).send(Mockito.eq(201L), commandCaptor.capture());
            FtctlActionCommand capturedAction = null;
            for (Command command : commandCaptor.getAllValues()) {
                if (command instanceof FtctlActionCommand) {
                    capturedAction = (FtctlActionCommand) command;
                    break;
                }
            }
            Assert.assertNotNull(capturedAction);
            Assert.assertEquals(240, capturedAction.getWait());
        } finally {
            CallContext.unregister();
        }
    }

    @Test
    public void testConfirmFtctlFenceStartsSecondaryAndContinuesFailover() throws Exception {
        Mockito.when(userVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        vmDetails.put("101:ftctl.peer.host.id", "202");

        UserVmVO secondaryVm = Mockito.mock(UserVmVO.class);
        Mockito.when(secondaryVm.getId()).thenReturn(401L);
        Mockito.when(secondaryVm.getUuid()).thenReturn("secondary-vm-uuid");
        Mockito.when(secondaryVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        Mockito.when(userVmDao.findById(401L)).thenReturn(secondaryVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setMode("ha");
        protection.setBackendMode("shared-blockcopy");
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        FtctlActionAnswer confirmAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FENCE_CONFIRM, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FENCE_CONFIRM, "ok", 0, "manual-fenced");
        FtctlStatusAnswer confirmStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "mirroring", "primary", "active", "manual-fenced", "manual_fencing_required",
                "2026-05-03T00:55:00+09:00", 0, 1);
        FtctlActionAnswer prepareAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER_PREPARE, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER_PREPARE, "ok", 0, "start-ready");
        FtctlStatusAnswer prepareStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "mirroring", "primary", "active", "manual-fenced", "",
                "2026-05-03T00:55:30+09:00", 0, 1);
        FtctlActionAnswer failoverAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER, "ok", 0, "failed-over");
        FtctlStatusAnswer failoverStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failed_over", "failed_over", "secondary", "active", "manual-fenced", "",
                "2026-05-03T00:56:00+09:00", 0, 2);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(confirmAnswer)
                .thenReturn(confirmStatus)
                .thenReturn(prepareAnswer)
                .thenReturn(prepareStatus)
                .thenReturn(failoverAnswer)
                .thenReturn(failoverStatus);
        Mockito.when(userVmManager.startVirtualMachine(Mockito.eq(401L), Mockito.eq(202L),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.isNull()))
                .thenReturn(new Pair<>(secondaryVm, new HashMap<>()));

        FtctlActionResponse response = ftctlService.confirmFtctlFence(101L);

        Assert.assertEquals("FENCE_CONFIRM", getFieldValue(response, "action"));
        Assert.assertEquals("failed_over", getFieldValue(response, "protectionState"));
        Assert.assertEquals("failed_over", getFieldValue(response, "transportState"));
        Assert.assertEquals("secondary", getFieldValue(response, "activeSide"));
        Assert.assertEquals("manual-fenced", getFieldValue(response, "fencingState"));
        Assert.assertEquals("", getFieldValue(response, "lastError"));
        Mockito.verify(userVmManager).startVirtualMachine(Mockito.eq(401L), Mockito.eq(202L),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.isNull());

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager, Mockito.times(6)).send(Mockito.eq(201L), commandCaptor.capture());
        List<Command> commands = commandCaptor.getAllValues();
        Assert.assertEquals(FtctlActionCommand.Action.FENCE_CONFIRM, ((FtctlActionCommand) commands.get(0)).getAction());
        Assert.assertTrue(commands.get(1) instanceof FtctlStatusCommand);
        Assert.assertEquals(FtctlActionCommand.Action.FAILOVER_PREPARE, ((FtctlActionCommand) commands.get(2)).getAction());
        Assert.assertTrue(((FtctlActionCommand) commands.get(2)).isForce());
        Assert.assertTrue(commands.get(3) instanceof FtctlStatusCommand);
        Assert.assertEquals(FtctlActionCommand.Action.FAILOVER, ((FtctlActionCommand) commands.get(4)).getAction());
        Assert.assertTrue(((FtctlActionCommand) commands.get(4)).isForce());
        Assert.assertTrue(commands.get(5) instanceof FtctlStatusCommand);
    }

    @Test
    public void testConfirmFtctlFenceReturnsFinalStateWhenFailoverContinuationIsLockedAfterConvergence() throws Exception {
        Mockito.when(userVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        vmDetails.put("101:ftctl.peer.host.id", "202");

        UserVmVO secondaryVm = Mockito.mock(UserVmVO.class);
        Mockito.when(secondaryVm.getUuid()).thenReturn("secondary-vm-uuid");
        Mockito.when(secondaryVm.getState()).thenReturn(VirtualMachine.State.Running);
        Mockito.when(userVmDao.findById(401L)).thenReturn(secondaryVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setMode("ha");
        protection.setBackendMode("remote-nbd");
        protection.setProvisioningBackend(FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED);
        protection.setProvisioningState(FtctlProtectionProvisioningService.STATE_READY);
        protection.setProtectionState("failing_over");
        protection.setTransportState("mirroring");
        protection.setActiveSide("primary");
        protection.setAdminState("active");
        protection.setFencingState("manual-fenced");
        protection.setLastError("manual_fencing_required");
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        FtctlActionAnswer confirmAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FENCE_CONFIRM, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FENCE_CONFIRM, "ok", 0, "manual-fenced");
        FtctlStatusAnswer confirmStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failed_over", "failed_over", "secondary", "active", "manual-fenced", "",
                "2026-05-06T09:05:00+09:00", 0, 1);
        FtctlActionAnswer prepareAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER_PREPARE, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER_PREPARE, "ok", 0, "already-ready");
        FtctlStatusAnswer prepareStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failed_over", "failed_over", "secondary", "active", "manual-fenced", "",
                "2026-05-06T09:05:30+09:00", 0, 1);
        FtctlActionAnswer lockedFailover = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER, "vm-name"), false,
                "{\"command\":\"failover\",\"result\":\"locked\",\"lock_file\":\"/run/ablestack-vm-ftctl/lock\"}",
                FtctlActionCommand.Action.FAILOVER, "locked", 20,
                "{\"command\":\"failover\",\"result\":\"locked\",\"lock_file\":\"/run/ablestack-vm-ftctl/lock\"}");
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(confirmAnswer)
                .thenReturn(confirmStatus)
                .thenReturn(prepareAnswer)
                .thenReturn(prepareStatus)
                .thenAnswer(invocation -> {
                    protection.setProtectionState("failed_over");
                    protection.setTransportState("failed_over");
                    protection.setActiveSide("secondary");
                    protection.setAdminState("active");
                    protection.setFencingState("manual-fenced");
                    protection.setLastError("");
                    return lockedFailover;
                });

        FtctlActionResponse response = ftctlService.confirmFtctlFence(101L);

        Assert.assertEquals("FENCE_CONFIRM", getFieldValue(response, "action"));
        Assert.assertEquals("ok", getFieldValue(response, "result"));
        Assert.assertEquals(Integer.valueOf(0), getFieldValue(response, "exitCode"));
        Assert.assertEquals("failed_over", getFieldValue(response, "protectionState"));
        Assert.assertEquals("failed_over", getFieldValue(response, "transportState"));
        Assert.assertEquals("secondary", getFieldValue(response, "activeSide"));
        Assert.assertEquals("manual-fenced", getFieldValue(response, "fencingState"));
        Mockito.verify(userVmManager, Mockito.never()).startVirtualMachine(Mockito.anyLong(), Mockito.<Long>any(),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.<String>any());
        Mockito.verify(nicDao, Mockito.never()).update(Mockito.anyLong(), Mockito.any(NicVO.class));
        Mockito.verify(agentManager, Mockito.times(5)).send(Mockito.eq(201L), Mockito.any(Command.class));
    }

    @Test
    public void testConfirmFtctlFenceHandsOffCloudManagedNicIdentityBeforeStartingSecondary() throws Exception {
        Mockito.when(userVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        vmDetails.put("101:ftctl.peer.host.id", "202");

        UserVmVO secondaryVm = Mockito.mock(UserVmVO.class);
        Mockito.when(secondaryVm.getId()).thenReturn(401L);
        Mockito.when(secondaryVm.getUuid()).thenReturn("secondary-vm-uuid");
        Mockito.when(secondaryVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        Mockito.when(userVmDao.findById(401L)).thenReturn(secondaryVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setMode("ha");
        protection.setBackendMode("shared-blockcopy");
        protection.setProvisioningBackend(FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        NicVO primaryNic = nic(425L, 101L, 204L, 0, "00:50:56:b5:5c:28", "10.10.254.90");
        primaryNic.setIPv4Gateway("10.10.0.1");
        primaryNic.setIPv4Netmask("255.255.0.0");
        NicVO secondaryNic = nic(453L, 401L, 204L, 0, "02:01:00:cc:00:64", "10.10.254.242");
        secondaryNic.setIPv4Gateway("10.10.0.1");
        secondaryNic.setIPv4Netmask("255.255.0.0");
        Mockito.when(nicDao.listByVmIdOrderByDeviceId(101L)).thenReturn(List.of(primaryNic));
        Mockito.when(nicDao.listByVmIdOrderByDeviceId(401L)).thenReturn(List.of(secondaryNic));

        FtctlActionAnswer confirmAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FENCE_CONFIRM, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FENCE_CONFIRM, "ok", 0, "manual-fenced");
        FtctlStatusAnswer confirmStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "mirroring", "primary", "active", "manual-fenced", "manual_fencing_required",
                "2026-05-03T00:55:00+09:00", 0, 1);
        FtctlActionAnswer prepareAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER_PREPARE, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER_PREPARE, "ok", 0, "start-ready");
        FtctlStatusAnswer prepareStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "mirroring", "primary", "active", "manual-fenced", "",
                "2026-05-03T00:55:30+09:00", 0, 1);
        FtctlActionAnswer failoverAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER, "ok", 0, "failed-over");
        FtctlStatusAnswer failoverStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failed_over", "failed_over", "secondary", "active", "manual-fenced", "",
                "2026-05-03T00:56:00+09:00", 0, 2);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(confirmAnswer)
                .thenReturn(confirmStatus)
                .thenReturn(prepareAnswer)
                .thenReturn(prepareStatus)
                .thenReturn(failoverAnswer)
                .thenReturn(failoverStatus);
        Mockito.when(userVmManager.startVirtualMachine(Mockito.eq(401L), Mockito.eq(202L),
                        Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.isNull()))
                .thenReturn(new Pair<>(secondaryVm, new HashMap<>()));

        ftctlService.confirmFtctlFence(101L);

        Assert.assertEquals("02:01:00:cc:00:64", primaryNic.getMacAddress());
        Assert.assertEquals("10.10.254.242", primaryNic.getIPv4Address());
        Assert.assertEquals("00:50:56:b5:5c:28", secondaryNic.getMacAddress());
        Assert.assertEquals("10.10.254.90", secondaryNic.getIPv4Address());
        Assert.assertEquals("secondary-owned", vmDetails.get("101:ftctl.nic.identity.state"));

        InOrder inOrder = Mockito.inOrder(nicDao, userVmManager);
        inOrder.verify(nicDao).update(425L, primaryNic);
        inOrder.verify(nicDao).update(453L, secondaryNic);
        inOrder.verify(userVmManager).startVirtualMachine(Mockito.eq(401L), Mockito.eq(202L),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.isNull());
    }

    @Test
    public void testConfirmFtctlFenceRejectsCopyingTransportBeforeSecondaryStart() throws Exception {
        Mockito.when(userVm.getState()).thenReturn(VirtualMachine.State.Stopped);
        vmDetails.put("101:ftctl.peer.host.id", "202");

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setMode("ha");
        protection.setBackendMode("shared-blockcopy");
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        FtctlActionAnswer confirmAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FENCE_CONFIRM, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FENCE_CONFIRM, "ok", 0, "manual-fenced");
        FtctlStatusAnswer confirmStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "copying", "primary", "active", "manual-fenced", "manual_fencing_required",
                "2026-05-03T00:55:00+09:00", 0, 1);
        FtctlActionAnswer prepareAnswer = new FtctlActionAnswer(new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER_PREPARE, "vm-name"), true, "OK",
                FtctlActionCommand.Action.FAILOVER_PREPARE, "ok", 0, "not-ready");
        FtctlStatusAnswer prepareStatus = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "failing_over", "copying", "primary", "active", "manual-fenced", "blockcopy_not_ready_for_failover",
                "2026-05-03T00:55:30+09:00", 0, 1);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(confirmAnswer)
                .thenReturn(confirmStatus)
                .thenReturn(prepareAnswer)
                .thenReturn(prepareStatus);

        try {
            ftctlService.confirmFtctlFence(101L);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("requires transport state mirroring or failed_over"));
        }

        Mockito.verify(userVmManager, Mockito.never()).startVirtualMachine(Mockito.anyLong(), Mockito.<Long>any(),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.<String>any());
        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager, Mockito.times(4)).send(Mockito.eq(201L), commandCaptor.capture());
        List<Command> commands = commandCaptor.getAllValues();
        Assert.assertEquals(FtctlActionCommand.Action.FENCE_CONFIRM, ((FtctlActionCommand) commands.get(0)).getAction());
        Assert.assertTrue(commands.get(1) instanceof FtctlStatusCommand);
        Assert.assertEquals(FtctlActionCommand.Action.FAILOVER_PREPARE, ((FtctlActionCommand) commands.get(2)).getAction());
        Assert.assertTrue(commands.get(3) instanceof FtctlStatusCommand);
    }

    @Test
    public void testConfirmFtctlFenceRejectsRunningPrimary() throws Exception {
        Mockito.when(userVm.getState()).thenReturn(VirtualMachine.State.Running);
        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        try {
            ftctlService.confirmFtctlFence(101L);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("requires primary VM"));
        }
        Mockito.verify(userVmManager, Mockito.never()).startVirtualMachine(Mockito.anyLong(), Mockito.<Long>any(),
                Mockito.<Map<VirtualMachineProfile.Param, Object>>any(), Mockito.<String>any());
    }

    @Test
    public void testGetFtctlProtectionForStandbyVmReturnsPrimaryManagedView() throws Exception {
        UserVmVO standbyVm = Mockito.mock(UserVmVO.class);
        Mockito.when(standbyVm.getId()).thenReturn(401L);
        Mockito.lenient().when(standbyVm.getDisplayName()).thenReturn("Standby VM");
        Mockito.when(userVmDao.findById(401L)).thenReturn(standbyVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setSecondaryVmName("Standby VM");
        Mockito.when(ftctlProtectionDao.findActiveBySecondaryVmId(401L)).thenReturn(protection);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        vmDetails.put("101:ftctl.enabled", "true");
        vmDetails.put("101:ftctl.mode", "ha");
        vmDetails.put("101:ftctl.backend.mode", "shared-blockcopy");

        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "protected", "mirroring", "primary", "active", "clear", "",
                "2026-05-01T17:10:00+09:00", 0, 0);
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenReturn(statusAnswer);

        GetFtctlProtectionCmd cmd = new GetFtctlProtectionCmd();
        setField(cmd, "virtualMachineId", 401L);

        FtctlProtectionResponse response = ftctlService.getFtctlProtection(cmd);

        Assert.assertEquals(Long.valueOf(401L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("standby", getFieldValue(response, "protectionRole"));
        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "primaryVirtualMachineId"));
        Assert.assertEquals("Primary VM", getFieldValue(response, "primaryVirtualMachineName"));
        Assert.assertEquals(Long.valueOf(401L), getFieldValue(response, "secondaryVirtualMachineId"));
        Assert.assertEquals("Standby VM", getFieldValue(response, "secondaryVmName"));
        Assert.assertEquals("protected", getFieldValue(response, "protectionState"));
        Assert.assertEquals("mirroring", getFieldValue(response, "transportState"));
        Mockito.verify(agentManager).send(Mockito.eq(201L), Mockito.any(Command.class));
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.protection.state", "protected", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.transport.state", "mirroring", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.active.side", "primary", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.admin.state", "active", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.fencing.state", "clear", true);
        Mockito.verify(ftctlProtectionDao).update(Mockito.eq(0L), Mockito.same(protection));
        Assert.assertEquals("ha", protection.getMode());
        Assert.assertEquals("protected", protection.getProtectionState());
        Assert.assertEquals("mirroring", protection.getTransportState());
        Assert.assertEquals("primary", protection.getActiveSide());
        Assert.assertEquals("active", protection.getAdminState());
        Assert.assertEquals("clear", protection.getFencingState());
    }

    @Test
    public void testReadOnlyRuntimeApisForStandbyVmUsePrimaryRuntimeProfile() throws Exception {
        UserVmVO standbyVm = Mockito.mock(UserVmVO.class);
        Mockito.when(standbyVm.getId()).thenReturn(401L);
        Mockito.when(userVmDao.findById(401L)).thenReturn(standbyVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        protection.setSecondaryVmName("i-2-401-VM");
        protection.setActiveSide("secondary");
        protection.setProvisioningBackend(FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED);
        Mockito.when(ftctlProtectionDao.findActiveBySecondaryVmId(401L)).thenReturn(protection);

        String itemsJson = "[" +
                "{\"ts\":\"2026-05-03T13:47:10+09:00\",\"vm\":\"vm-name\",\"stage\":\"failover\",\"event\":\"failover.precheck\",\"result\":\"ok\"}" +
                "]";
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class))).thenAnswer(invocation -> {
            Command command = invocation.getArgument(1);
            if (command instanceof FtctlCheckCommand) {
                return new FtctlCheckAnswer((FtctlCheckCommand) command, true, "OK", "ok", "healthy", "vm-name", 0, 0,
                        false, "running", "cloud-managed");
            }
            if (command instanceof FtctlHealthCommand) {
                return new FtctlHealthAnswer((FtctlHealthCommand) command, true, "OK", "ok", "qemu:///system", 0);
            }
            if (command instanceof FtctlEventsCommand) {
                return new FtctlEventsAnswer((FtctlEventsCommand) command, true, "OK", "ok", "vm-name", 1, itemsJson);
            }
            return null;
        });

        GetFtctlCheckCmd checkCmd = new GetFtctlCheckCmd();
        setField(checkCmd, "virtualMachineId", 401L);
        FtctlCheckResponse checkResponse = ftctlService.getFtctlCheck(checkCmd);

        GetFtctlHealthCmd healthCmd = new GetFtctlHealthCmd();
        setField(healthCmd, "virtualMachineId", 401L);
        FtctlHealthResponse healthResponse = ftctlService.getFtctlHealth(healthCmd);

        GetFtctlEventsCmd eventsCmd = new GetFtctlEventsCmd();
        setField(eventsCmd, "virtualMachineId", 401L);
        setField(eventsCmd, "limit", 20);
        FtctlEventsResponse eventsResponse = ftctlService.getFtctlEvents(eventsCmd);

        Assert.assertEquals(Long.valueOf(401L), getFieldValue(checkResponse, "virtualMachineId"));
        Assert.assertEquals("vm-name", getFieldValue(checkResponse, "vmName"));
        Assert.assertEquals("ok", getFieldValue(checkResponse, "result"));
        Assert.assertEquals(Long.valueOf(401L), getFieldValue(healthResponse, "virtualMachineId"));
        Assert.assertEquals(Long.valueOf(201L), getFieldValue(healthResponse, "hostId"));
        Assert.assertEquals("ok", getFieldValue(healthResponse, "result"));
        Assert.assertEquals(Long.valueOf(401L), eventsResponse.getVirtualMachineId());
        Assert.assertEquals("vm-name", eventsResponse.getVmName());
        Assert.assertEquals(Integer.valueOf(1), eventsResponse.getCount());
        Assert.assertEquals("failover.precheck", eventsResponse.getEvents().get(0).getEvent());

        ArgumentCaptor<Command> commandCaptor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager, Mockito.times(3)).send(Mockito.eq(201L), commandCaptor.capture());
        FtctlCheckCommand checkCommand = (FtctlCheckCommand) commandCaptor.getAllValues().get(0);
        Assert.assertEquals("vm-name", checkCommand.getVmName());
        Assert.assertEquals("i-2-401-VM", checkCommand.getSecondaryVmName());
        Assert.assertEquals("secondary", checkCommand.getActiveSide());
        Assert.assertEquals("cloud-managed", checkCommand.getProvisioningBackend());
        Assert.assertTrue(commandCaptor.getAllValues().get(1) instanceof FtctlHealthCommand);
        Assert.assertEquals("vm-name", ((FtctlEventsCommand) commandCaptor.getAllValues().get(2)).getVmName());
        Assert.assertEquals(Integer.valueOf(20), ((FtctlEventsCommand) commandCaptor.getAllValues().get(2)).getLimit());
    }

    @Test
    public void testExecuteFtctlActionRejectsStandbyVm() {
        UserVmVO standbyVm = Mockito.mock(UserVmVO.class);
        Mockito.when(standbyVm.getId()).thenReturn(401L);
        Mockito.when(standbyVm.getUuid()).thenReturn("standby-vm-uuid");
        Mockito.when(userVmDao.findById(401L)).thenReturn(standbyVm);

        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        protection.setSecondaryVmId(401L);
        Mockito.when(ftctlProtectionDao.findActiveBySecondaryVmId(401L)).thenReturn(protection);

        try {
            ftctlService.executeFtctlAction(401L, FtctlActionCommand.Action.PAUSE_PROTECTION, false);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("managed from primary VM"));
        }
        try {
            Mockito.verify(agentManager, Mockito.never()).send(Mockito.anyLong(), Mockito.any(Command.class));
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            throw new AssertionError(e);
        }
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
        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

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
        Assert.assertEquals("libvirt-managed", profileCommand.getProvisioningBackend());
        Assert.assertEquals("Ready", profileCommand.getProvisioningState());
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
        Assert.assertEquals("libvirt-managed", capturedProtectCommand.getContextParam("ftctl.provisioning.backend"));
        Assert.assertEquals("manual-block", capturedProtectCommand.getContextParam("ftctl.fencing.policy"));

        Assert.assertEquals(Long.valueOf(101L), getFieldValue(response, "virtualMachineId"));
        Assert.assertEquals("true", getFieldValue(response, "enabled"));
        Assert.assertEquals("dr", getFieldValue(response, "mode"));
        Assert.assertEquals("remote-nbd", getFieldValue(response, "backendMode"));
        Assert.assertEquals("libvirt-managed", getFieldValue(response, "provisioningBackend"));
        Assert.assertEquals("Ready", getFieldValue(response, "provisioningState"));
        Assert.assertEquals("pool-uuid", getFieldValue(response, "targetStoragePoolId"));
        Assert.assertEquals("pool-name", getFieldValue(response, "targetStoragePoolName"));
        Assert.assertEquals("202", getFieldValue(response, "peerHostId"));
        Assert.assertEquals("host-202", getFieldValue(response, "peerHostName"));
        Assert.assertEquals("protected", getFieldValue(response, "protectionState"));
        Assert.assertEquals("replicating", getFieldValue(response, "transportState"));
        Assert.assertEquals("primary", getFieldValue(response, "activeSide"));
        Mockito.verify(ftctlProtectionDao).update(Mockito.eq(0L), Mockito.any(FtctlProtectionVO.class));
        Assert.assertEquals("dr", protection.getMode());
        Assert.assertEquals("protected", protection.getProtectionState());
        Assert.assertEquals("replicating", protection.getTransportState());
        Assert.assertEquals("primary", protection.getActiveSide());

        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.enabled", "true", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.backend.mode", "remote-nbd", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.provisioning.backend", "libvirt-managed", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.provisioning.state", "Ready", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.target.storage.pool.id", "pool-uuid", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.target.storage.pool.name", "pool-name", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.peer.host.id", "202", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.protection.state", "protected", true);
    }

    @Test
    public void testRegisterCloudManagedRemoteNbdRejectsRelativeDiskMap() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);
        Mockito.doReturn(new FtctlProtectionProvisioningContext(
                FtctlProtectionProvisioningService.BACKEND_CLOUD_MANAGED,
                FtctlProtectionProvisioningService.STATE_READY,
                "i-2-401-VM",
                "sda=relative-target.qcow2"))
                .when(ftctlProtectionProvisioningService).prepareProtection(Mockito.any());

        try {
            ftctlService.registerFtctlProtection(cmd);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("absolute Cloud-managed path"));
        }
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.anyLong(), Mockito.any(Command.class));
    }

    @Test
    public void testRegisterFtctlProtectionNormalizesHostStorageToRemoteNbd() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        setField(cmd, "mode", "ha");
        setField(cmd, "backendMode", "shared-blockcopy");
        setField(cmd, "secondaryTargetDir", null);
        setField(cmd, "remoteNbdExportAddr", null);
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);

        FtctlSyncAnswer clusterAnswer = new FtctlSyncAnswer(new FtctlSyncClusterCommand(), true, "OK", "ok", 0, "cluster-synced");
        FtctlSyncAnswer profileAnswer = new FtctlSyncAnswer(new FtctlSyncProfileCommand(), true, "OK", "ok", 0, "profile-synced");
        FtctlActionCommand protectCommand = new FtctlActionCommand(FtctlActionCommand.Action.PROTECT, "vm-name");
        FtctlActionAnswer protectAnswer = new FtctlActionAnswer(protectCommand, true, "OK",
                FtctlActionCommand.Action.PROTECT, "ok", 0, "protected");
        FtctlStatusAnswer statusAnswer = new FtctlStatusAnswer(new FtctlStatusCommand("vm-name"), true, "OK", "ok", "vm-name",
                "ha", "protected", "replicating", "primary", "running", "clear", "",
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
        Assert.assertEquals("remote-nbd", profileCommand.getBackendMode());
        Assert.assertEquals("secondary-local", profileCommand.getTargetStorageScope());
        Assert.assertEquals("/var/lib/libvirt/images", profileCommand.getSecondaryTargetDir());
        Assert.assertEquals("10.0.0.12:10809", profileCommand.getRemoteNbdExportAddr());

        FtctlActionCommand capturedProtectCommand = (FtctlActionCommand) commandCaptor.getAllValues().get(2);
        Assert.assertEquals("remote-nbd", capturedProtectCommand.getContextParam("ftctl.backend.mode"));
        Assert.assertEquals("secondary-local", capturedProtectCommand.getContextParam("ftctl.target.storage.scope"));

        ArgumentCaptor<FtctlProtectionProvisioningRequest> requestCaptor = ArgumentCaptor.forClass(FtctlProtectionProvisioningRequest.class);
        Mockito.verify(ftctlProtectionProvisioningService).prepareProtection(requestCaptor.capture());
        Assert.assertEquals("remote-nbd", requestCaptor.getValue().getBackendMode());
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.backend.mode", "remote-nbd", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.secondary.target.dir", "/var/lib/libvirt/images", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.remote.nbd.export.addr", "10.0.0.12:10809", true);
    }

    @Test
    public void testRegisterFtctlProtectionPersistsFailureWhenProfileSyncFails() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        HostVO localHost = mockHost(201L, 301L, "10.0.0.11");
        HostVO peerHost = mockHost(202L, 301L, "10.0.0.12");
        Mockito.when(hostDao.findById(201L)).thenReturn(localHost);
        Mockito.when(hostDao.findById(202L)).thenReturn(peerHost);
        FtctlProtectionVO protection = new FtctlProtectionVO(101L);
        Mockito.when(ftctlProtectionDao.findActiveByPrimaryVmId(101L)).thenReturn(protection);

        FtctlSyncAnswer clusterAnswer = new FtctlSyncAnswer(new FtctlSyncClusterCommand(), true, "OK", "ok", 0, "cluster-synced");
        FtctlSyncAnswer profileAnswer = new FtctlSyncAnswer(new FtctlSyncProfileCommand(), false, "ERROR", "profile failed", 2, "profile failed");
        Mockito.when(agentManager.send(Mockito.eq(201L), Mockito.any(Command.class)))
                .thenReturn(clusterAnswer)
                .thenReturn(profileAnswer);

        try {
            ftctlService.registerFtctlProtection(cmd);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("FTCTL profile sync failed"));
        }

        Assert.assertEquals("error", protection.getProtectionState());
        Assert.assertEquals("failed", protection.getTransportState());
        Assert.assertEquals("primary", protection.getActiveSide());
        Assert.assertEquals("active", protection.getAdminState());
        Assert.assertEquals("clear", protection.getFencingState());
        Assert.assertTrue(protection.getLastError().contains("FTCTL profile sync failed"));
        Assert.assertEquals("error", vmDetails.get("101:ftctl.last.protection.state"));
        Assert.assertEquals("failed", vmDetails.get("101:ftctl.last.transport.state"));
        Assert.assertTrue(vmDetails.get("101:ftctl.last.error").contains("FTCTL profile sync failed"));
        Mockito.verify(ftctlProtectionDao).update(Mockito.eq(0L), Mockito.eq(protection));
    }

    @Test
    public void testRegisterFtctlProtectionStopsCloudManagedBeforeAgentSyncWhenProvisioningIsNotReady() throws Exception {
        RegisterFtctlProtectionCmd cmd = buildRegisterCmd();
        setField(cmd, "provisioningBackend", "cloud-managed");
        Mockito.doThrow(new CloudRuntimeException("not ready")).when(ftctlProtectionProvisioningService)
                .prepareProtection(Mockito.any(FtctlProtectionProvisioningRequest.class));

        try {
            ftctlService.registerFtctlProtection(cmd);
            Assert.fail("Expected CloudRuntimeException");
        } catch (CloudRuntimeException e) {
            Assert.assertEquals("not ready", e.getMessage());
        }

        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.provisioning.backend", "cloud-managed", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.provisioning.state", "ProvisioningFailed", true);
        Mockito.verify(vmInstanceDetailsDao).addDetail(101L, "ftctl.last.error", "not ready", true);
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.anyLong(), Mockito.any(Command.class));
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
        Mockito.when(storagePool.getPath()).thenReturn("/var/lib/libvirt/images");
        return storagePool;
    }

    private NicVO nic(long id, long instanceId, long networkId, int deviceId, String macAddress, String ip4Address) {
        NicVO nic = new NicVO("DirectNetworkGuru", instanceId, networkId, VirtualMachine.Type.User);
        setField(nic, "id", id);
        nic.setDeviceId(deviceId);
        nic.setMacAddress(macAddress);
        nic.setIPv4Address(ip4Address);
        return nic;
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
