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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import org.apache.cloudstack.api.command.admin.ftctl.ExecuteFtctlDrSiteAgentCommandCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.ftctl.FtctlDrSiteAgentCommandResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrCapabilitiesCommand;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.Gson;

@RunWith(MockitoJUnitRunner.class)
public class FtctlDrSiteAgentBrokerServiceImplTest {
    @Mock private AgentManager agentManager;
    @Mock private HostDao hostDao;
    @Mock private DataCenterDao dataCenterDao;
    @Mock private UserVmDao userVmDao;
    @InjectMocks private FtctlDrSiteAgentBrokerServiceImpl brokerService;

    @Test
    public void executesAllowListedStatusCommandOnResolvedKvmHost() throws Exception {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(42L);
        Mockito.when(host.getUuid()).thenReturn("host-uuid");
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        eligible(host);

        FtctlDrStatusCommand requested = new FtctlDrStatusCommand("plan-uuid", "run-uuid");
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "accepted"));

        FtctlDrSiteAgentCommandResponse response = brokerService.execute(
                "status", new Gson().toJson(requested), "host-uuid");

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager).send(Mockito.eq(42L), command.capture());
        Assert.assertTrue(command.getValue() instanceof FtctlDrStatusCommand);
        Assert.assertEquals("plan-uuid", ((FtctlDrStatusCommand) command.getValue()).getPlanUuid());
        Assert.assertEquals("STATUS", response.getCommandType());
        Assert.assertEquals("host-uuid", response.getWorkerHostUuid());
        Assert.assertTrue(response.getResult());
        Assert.assertEquals("accepted", response.getDetails());
    }

    @Test
    public void executesAllowListedCancelCommandOnResolvedKvmHost() throws Exception {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(42L);
        Mockito.when(host.getUuid()).thenReturn("host-uuid");
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        eligible(host);

        FtctlDrCancelCommand requested = new FtctlDrCancelCommand("plan-uuid", "run-uuid");
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "accepted"));

        FtctlDrSiteAgentCommandResponse response = brokerService.execute(
                "cancel", new Gson().toJson(requested), "host-uuid");

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager).send(Mockito.eq(42L), command.capture());
        Assert.assertTrue(command.getValue() instanceof FtctlDrCancelCommand);
        Assert.assertEquals("plan-uuid", ((FtctlDrCancelCommand) command.getValue()).getPlanUuid());
        Assert.assertEquals("run-uuid", ((FtctlDrCancelCommand) command.getValue()).getRunUuid());
        Assert.assertEquals("CANCEL", response.getCommandType());
        Assert.assertTrue(response.getResult());
    }

    @Test
    public void rewritesTargetExportTransportToTheSiteLocalWorker() throws Exception {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(42L);
        Mockito.when(host.getUuid()).thenReturn("source-host-uuid");
        Mockito.when(host.getPrivateIpAddress()).thenReturn("10.10.22.1");
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        eligible(host);

        FtctlDrActionCommand requested = new FtctlDrActionCommand(
                FtctlDrActionCommand.Action.TARGET_EXPORT_START, "plan-uuid", "run-uuid");
        requested.setRole("reverse-target");
        requested.setProfileJson("{\"transport\":{\"targetHostUuid\":\"target-host-uuid\"," +
                "\"targetHostAddress\":\"10.10.32.3\",\"remoteNbdExportAddress\":\"10.10.32.3\"," +
                "\"exports\":[{\"host\":\"10.10.32.3\"}]}}");
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "accepted"));

        brokerService.execute("ACTION", new Gson().toJson(requested), "deprecated-caller-hint");

        ArgumentCaptor<Command> command = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(agentManager).send(Mockito.eq(42L), command.capture());
        FtctlDrActionCommand dispatched = (FtctlDrActionCommand) command.getValue();
        Assert.assertTrue(dispatched.getProfileJson().contains("\"targetHostUuid\":\"source-host-uuid\""));
        Assert.assertTrue(dispatched.getProfileJson().contains("\"targetHostAddress\":\"10.10.22.1\""));
        Assert.assertTrue(dispatched.getProfileJson().contains("\"remoteNbdExportAddress\":\"10.10.22.1\""));
        Assert.assertFalse(dispatched.getProfileJson().contains("\"exports\""));
        Assert.assertFalse(dispatched.getProfileJson().contains("10.10.32.3"));
    }

    @Test(expected = CloudRuntimeException.class)
    public void rejectsUnknownCommandTypeBeforeAgentDispatch() {
        brokerService.execute("SHELL", "{}", "host-uuid");
    }

    @Test
    public void selectsEligibleWorkerWhenDeprecatedHintIsAbsent() throws Exception {
        HostVO host = host(42L, "auto-host");
        eligible(host);
        FtctlDrCapabilitiesCommand command = new FtctlDrCapabilitiesCommand("plan-uuid", "availability");
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "supported"));

        FtctlDrSiteAgentCommandResponse response = brokerService.execute(
                "CAPABILITIES", new Gson().toJson(command), null);

        Assert.assertEquals("auto-host", response.getWorkerHostUuid());
        Mockito.verify(agentManager).send(Mockito.eq(42L), Mockito.any(Command.class));
    }

    @Test
    public void actionPrefersCurrentVmHostWithoutPersistedWorkerBinding() throws Exception {
        HostVO first = host(41L, "first-host");
        HostVO current = host(42L, "current-host");
        eligible(first, current);
        UserVmVO vm = Mockito.mock(UserVmVO.class);
        Mockito.when(vm.getHostId()).thenReturn(42L);
        Mockito.when(userVmDao.findByUuid("source-vm-uuid")).thenReturn(vm);
        FtctlDrActionCommand command = new FtctlDrActionCommand(
                FtctlDrActionCommand.Action.SYNC, "plan-uuid", "run-uuid");
        command.setContext(Collections.singletonMap("sourceVmUuid", "source-vm-uuid"));
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "accepted"));

        brokerService.execute("ACTION", new Gson().toJson(command), "stale-host-hint");

        Mockito.verify(agentManager).send(Mockito.eq(42L), Mockito.any(Command.class));
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.eq(41L), Mockito.any(Command.class));
    }

    @Test
    public void readOnlyStatusTriesNextWorkerAfterNonAuthoritativeAnswer() throws Exception {
        HostVO first = host(41L, "first-host");
        HostVO second = host(42L, "second-host");
        eligible(first, second);
        FtctlDrStatusCommand command = new FtctlDrStatusCommand("plan-uuid", "run-uuid");
        Mockito.when(agentManager.send(Mockito.eq(41L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), false, "not found"));
        Mockito.when(agentManager.send(Mockito.eq(42L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), true, "found"));

        FtctlDrSiteAgentCommandResponse response = brokerService.execute(
                "STATUS", new Gson().toJson(command), null);

        Assert.assertEquals("second-host", response.getWorkerHostUuid());
        Mockito.verify(agentManager).send(Mockito.eq(41L), Mockito.any(Command.class));
        Mockito.verify(agentManager).send(Mockito.eq(42L), Mockito.any(Command.class));
    }

    @Test
    public void mutatingActionIsNeverRetriedOnAnotherWorker() throws Exception {
        HostVO first = host(41L, "first-host");
        HostVO second = host(42L, "second-host");
        eligible(first, second);
        FtctlDrActionCommand command = new FtctlDrActionCommand(
                FtctlDrActionCommand.Action.SYNC, "plan-uuid", "run-uuid");
        Mockito.when(agentManager.send(Mockito.eq(41L), Mockito.any(Command.class)))
                .thenAnswer(invocation -> new Answer(invocation.getArgument(1), false, "rejected"));

        FtctlDrSiteAgentCommandResponse response = brokerService.execute(
                "ACTION", new Gson().toJson(command), null);

        Assert.assertFalse(response.getResult());
        Assert.assertEquals("first-host", response.getWorkerHostUuid());
        Mockito.verify(agentManager).send(Mockito.eq(41L), Mockito.any(Command.class));
        Mockito.verify(agentManager, Mockito.never()).send(Mockito.eq(42L), Mockito.any(Command.class));
    }

    @Test
    public void workerHostParameterIsAnOptionalCompatibilityHint() throws Exception {
        Field field = ExecuteFtctlDrSiteAgentCommandCmd.class.getDeclaredField("workerHostUuid");
        Parameter parameter = field.getAnnotation(Parameter.class);
        Assert.assertNotNull(parameter);
        Assert.assertFalse(parameter.required());
    }

    @Test
    public void registersBrokerApiWithAlwaysOnFtctlService() {
        Assert.assertTrue(new FtctlServiceImpl().getCommands().contains(ExecuteFtctlDrSiteAgentCommandCmd.class));
    }

    private HostVO host(long id, String uuid) {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(id);
        Mockito.when(host.getUuid()).thenReturn(uuid);
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        return host;
    }

    private void eligible(HostVO... hosts) {
        DataCenterVO zone = Mockito.mock(DataCenterVO.class);
        Mockito.when(zone.getId()).thenReturn(1L);
        Mockito.when(dataCenterDao.listEnabledZones()).thenReturn(Collections.singletonList(zone));
        Mockito.when(hostDao.listAllHostsUpByZoneAndHypervisor(1L, Hypervisor.HypervisorType.KVM))
                .thenReturn(Arrays.asList(hosts));
    }
}
