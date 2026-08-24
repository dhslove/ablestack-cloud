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

import org.apache.cloudstack.api.command.admin.ftctl.ExecuteFtctlDrSiteAgentCommandCmd;
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
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;

@RunWith(MockitoJUnitRunner.class)
public class FtctlDrSiteAgentBrokerServiceImplTest {
    @Mock private AgentManager agentManager;
    @Mock private HostDao hostDao;
    @InjectMocks private FtctlDrSiteAgentBrokerServiceImpl brokerService;

    @Test
    public void executesAllowListedStatusCommandOnResolvedKvmHost() throws Exception {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(42L);
        Mockito.when(host.getUuid()).thenReturn("host-uuid");
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        Mockito.when(hostDao.findByUuid("host-uuid")).thenReturn(host);

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

    @Test(expected = CloudRuntimeException.class)
    public void rejectsUnknownCommandTypeBeforeAgentDispatch() {
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        Mockito.when(hostDao.findByUuid("host-uuid")).thenReturn(host);
        brokerService.execute("SHELL", "{}", "host-uuid");
    }

    @Test
    public void registersBrokerApiWithAlwaysOnFtctlService() {
        Assert.assertTrue(new FtctlServiceImpl().getCommands().contains(ExecuteFtctlDrSiteAgentCommandCmd.class));
    }
}
