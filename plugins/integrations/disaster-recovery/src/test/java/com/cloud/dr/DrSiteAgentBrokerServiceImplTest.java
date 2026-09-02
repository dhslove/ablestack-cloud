// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import org.apache.cloudstack.api.response.dr.DrSiteAgentCommandResponse;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.FtctlDrCancelAnswer;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.google.gson.Gson;

import org.mockito.Mockito;

public class DrSiteAgentBrokerServiceImplTest {

    @Test
    public void remoteBrokerAllowsTypedCancelCommand() throws Exception {
        AgentManager agentManager = Mockito.mock(AgentManager.class);
        HostDao hostDao = Mockito.mock(HostDao.class);
        DataCenterDao dataCenterDao = Mockito.mock(DataCenterDao.class);
        DataCenterVO zone = Mockito.mock(DataCenterVO.class);
        HostVO host = Mockito.mock(HostVO.class);
        Mockito.when(host.getId()).thenReturn(22L);
        Mockito.when(host.getUuid()).thenReturn("source-host-uuid");
        Mockito.when(host.getStatus()).thenReturn(Status.Up);
        Mockito.when(host.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.KVM);
        Mockito.when(zone.getId()).thenReturn(1L);
        Mockito.when(dataCenterDao.listEnabledZones()).thenReturn(java.util.Collections.singletonList(zone));
        Mockito.when(hostDao.listAllHostsUpByZoneAndHypervisor(1L, Hypervisor.HypervisorType.KVM))
                .thenReturn(java.util.Collections.singletonList(host));
        FtctlDrCancelCommand command = new FtctlDrCancelCommand("plan-uuid", "run-uuid");
        Mockito.when(agentManager.send(Mockito.eq(22L), Mockito.any(FtctlDrCancelCommand.class)))
                .thenReturn(new FtctlDrCancelAnswer(command, true, "canceled", "plan-uuid", "run-uuid",
                        "canceled", true, null, 0, "{\"state\":\"CANCELED\"}"));
        DrSiteAgentBrokerServiceImpl service = new DrSiteAgentBrokerServiceImpl();
        ReflectionTestUtils.setField(service, "agentManager", agentManager);
        ReflectionTestUtils.setField(service, "hostDao", hostDao);
        ReflectionTestUtils.setField(service, "dataCenterDao", dataCenterDao);

        DrSiteAgentCommandResponse response = service.execute("CANCEL", new Gson().toJson(command), "source-host-uuid");

        Assert.assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(response, "result"));
        Assert.assertEquals("CANCEL", ReflectionTestUtils.getField(response, "commandType"));
        Mockito.verify(agentManager).send(Mockito.eq(22L), Mockito.any(FtctlDrCancelCommand.class));
        Mockito.verify(hostDao, Mockito.never()).findByUuid(Mockito.anyString());
    }
}
