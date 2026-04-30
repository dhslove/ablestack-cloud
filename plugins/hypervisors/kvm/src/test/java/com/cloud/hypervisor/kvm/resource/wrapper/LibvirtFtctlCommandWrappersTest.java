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
package com.cloud.hypervisor.kvm.resource.wrapper;

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
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.utils.script.Script;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(MockitoJUnitRunner.class)
public class LibvirtFtctlCommandWrappersTest {

    @Mock
    private LibvirtComputingResource resource;

    @Test
    public void testStatusWrapperBuildsCommandAndParsesJson() {
        LibvirtFtctlStatusCommandWrapper wrapper = new LibvirtFtctlStatusCommandWrapper();
        FtctlStatusCommand command = new FtctlStatusCommand("vm-a");

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"ok\",\"vm\":\"vm-a\",\"mode\":\"dr\",\"protection_state\":\"protected\",\"transport_state\":\"replicating\",\"active_side\":\"primary\",\"admin_state\":\"running\",\"fencing_state\":\"clear\",\"last_error\":\"\",\"last_reconcile_ts\":\"2026-04-18T21:00:00+09:00\",\"rearm_count\":1,\"failover_count\":0}");
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlStatusAnswer);
            FtctlStatusAnswer statusAnswer = (FtctlStatusAnswer) answer;
            Assert.assertTrue(statusAnswer.getResult());
            Assert.assertEquals("ok", statusAnswer.getFtctlResult());
            Assert.assertEquals("vm-a", statusAnswer.getVmName());
            Assert.assertEquals("dr", statusAnswer.getMode());
            Assert.assertEquals("protected", statusAnswer.getProtectionState());
            Assert.assertEquals("replicating", statusAnswer.getTransportState());
            Assert.assertEquals("primary", statusAnswer.getActiveSide());
            Assert.assertEquals("running", statusAnswer.getAdminState());
            Assert.assertEquals("clear", statusAnswer.getFencingState());
            Assert.assertEquals(Integer.valueOf(1), statusAnswer.getRearmCount());
            Assert.assertEquals(Integer.valueOf(0), statusAnswer.getFailoverCount());

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("status");
            Mockito.verify(script).add("--vm", "vm-a");
            Mockito.verify(script).add("--json");
        }
    }

    @Test
    public void testActionWrapperHandlesLockedResult() {
        LibvirtFtctlActionCommandWrapper wrapper = new LibvirtFtctlActionCommandWrapper();
        FtctlActionCommand command = new FtctlActionCommand(FtctlActionCommand.Action.FAILOVER, "vm-a");
        command.setMode("dr");
        command.setPeerUri("qemu+ssh://peer/system");
        command.setProfileName("vm-uuid");
        command.setForce(true);

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"locked\",\"lock_file\":\"/run/lock/ftctl.lock\"}");
            Mockito.when(mock.getExitValue()).thenReturn(20);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlActionAnswer);
            FtctlActionAnswer actionAnswer = (FtctlActionAnswer) answer;
            Assert.assertFalse(actionAnswer.getResult());
            Assert.assertEquals(FtctlActionCommand.Action.FAILOVER, actionAnswer.getAction());
            Assert.assertEquals("locked", actionAnswer.getFtctlResult());
            Assert.assertEquals(Integer.valueOf(20), actionAnswer.getExitCode());
            Assert.assertTrue(actionAnswer.getOutput().contains("locked"));

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("failover");
            Mockito.verify(script).add("--vm", "vm-a");
            Mockito.verify(script).add("--mode", "dr");
            Mockito.verify(script).add("--peer", "qemu+ssh://peer/system");
            Mockito.verify(script).add("--profile", "vm-uuid");
            Mockito.verify(script).add("--force");
            Mockito.verify(script).add("--json");
        }
    }

    @Test
    public void testCheckWrapperBuildsCommandAndParsesJson() {
        LibvirtFtctlCheckCommandWrapper wrapper = new LibvirtFtctlCheckCommandWrapper();
        FtctlCheckCommand command = new FtctlCheckCommand("vm-a");

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"ok\",\"inventory_result\":\"healthy\",\"vm\":\"vm-a\",\"primary_rc\":0,\"peer_rc\":1}");
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlCheckAnswer);
            FtctlCheckAnswer checkAnswer = (FtctlCheckAnswer) answer;
            Assert.assertTrue(checkAnswer.getResult());
            Assert.assertEquals("ok", checkAnswer.getFtctlResult());
            Assert.assertEquals("healthy", checkAnswer.getInventoryResult());
            Assert.assertEquals("vm-a", checkAnswer.getVmName());
            Assert.assertEquals(Integer.valueOf(0), checkAnswer.getPrimaryRc());
            Assert.assertEquals(Integer.valueOf(1), checkAnswer.getPeerRc());

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("check");
            Mockito.verify(script).add("--vm", "vm-a");
            Mockito.verify(script).add("--json");
        }
    }

    @Test
    public void testHealthWrapperBuildsCommandAndParsesJson() {
        LibvirtFtctlHealthCommandWrapper wrapper = new LibvirtFtctlHealthCommandWrapper();
        FtctlHealthCommand command = new FtctlHealthCommand();

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"ok\",\"uri\":\"qemu+ssh://10.0.0.11/system\",\"rc\":0}");
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlHealthAnswer);
            FtctlHealthAnswer healthAnswer = (FtctlHealthAnswer) answer;
            Assert.assertTrue(healthAnswer.getResult());
            Assert.assertEquals("ok", healthAnswer.getFtctlResult());
            Assert.assertEquals("qemu+ssh://10.0.0.11/system", healthAnswer.getUri());
            Assert.assertEquals(Integer.valueOf(0), healthAnswer.getRc());

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("health");
            Mockito.verify(script).add("--json");
        }
    }

    @Test
    public void testEventsWrapperBuildsCommandAndParsesJson() {
        LibvirtFtctlEventsCommandWrapper wrapper = new LibvirtFtctlEventsCommandWrapper();
        FtctlEventsCommand command = new FtctlEventsCommand("vm-a", 5);

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"ok\",\"vm\":\"vm-a\",\"count\":2,\"items\":[{\"ts\":\"2026-04-18T21:30:00+09:00\",\"event\":\"tick\"},{\"ts\":\"2026-04-18T21:31:00+09:00\",\"event\":\"rearm\"}]}");
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlEventsAnswer);
            FtctlEventsAnswer eventsAnswer = (FtctlEventsAnswer) answer;
            Assert.assertTrue(eventsAnswer.getResult());
            Assert.assertEquals("ok", eventsAnswer.getFtctlResult());
            Assert.assertEquals("vm-a", eventsAnswer.getVmName());
            Assert.assertEquals(Integer.valueOf(2), eventsAnswer.getCount());
            Assert.assertTrue(eventsAnswer.getItemsJson().contains("\"event\":\"tick\""));
            Assert.assertTrue(eventsAnswer.getItemsJson().contains("\"event\":\"rearm\""));

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("events");
            Mockito.verify(script).add("--vm", "vm-a");
            Mockito.verify(script).add("--limit", "5");
            Mockito.verify(script).add("--json");
        }
    }

    @Test
    public void testSyncClusterWrapperBuildsInitAndUpsertCommands() {
        LibvirtFtctlSyncClusterCommandWrapper wrapper = new LibvirtFtctlSyncClusterCommandWrapper();
        FtctlSyncClusterCommand command = new FtctlSyncClusterCommand();
        command.setClusterName("cluster-301");
        command.setLocalHostId("201");
        command.setLocalRole("primary");
        command.setLocalManagementIp("192.168.0.11");
        command.setLocalLibvirtUri("qemu+ssh://10.0.0.11/system");
        command.setLocalBlockcopyIp("10.0.0.11");
        command.setLocalXcoloControlIp("10.0.1.11");
        command.setLocalXcoloDataIp("10.0.2.11");
        command.setPeerHostId("202");
        command.setPeerRole("secondary");
        command.setPeerManagementIp("192.168.0.12");
        command.setPeerLibvirtUri("qemu+ssh://10.0.0.12/system");
        command.setPeerBlockcopyIp("10.0.0.12");
        command.setPeerXcoloControlIp("10.0.1.12");
        command.setPeerXcoloDataIp("10.0.2.12");

        AtomicInteger index = new AtomicInteger();
        List<String> outputs = List.of("init-ok", "local-upsert-ok", "peer-upsert-ok");
        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            int current = index.getAndIncrement();
            Mockito.when(mock.execute(Mockito.any())).thenReturn(outputs.get(current));
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlSyncAnswer);
            FtctlSyncAnswer syncAnswer = (FtctlSyncAnswer) answer;
            Assert.assertTrue(syncAnswer.getResult());
            Assert.assertEquals("ok", syncAnswer.getFtctlResult());
            Assert.assertEquals(Integer.valueOf(0), syncAnswer.getExitCode());
            Assert.assertTrue(syncAnswer.getOutput().contains("local-upsert-ok"));
            Assert.assertTrue(syncAnswer.getOutput().contains("peer-upsert-ok"));

            Assert.assertEquals(3, scripts.constructed().size());
            Script initScript = scripts.constructed().get(0);
            Script localUpsert = scripts.constructed().get(1);
            Script peerUpsert = scripts.constructed().get(2);

            Mockito.verify(initScript).add("config");
            Mockito.verify(initScript).add("init-cluster");
            Mockito.verify(initScript).add("--cluster-name", "cluster-301");
            Mockito.verify(initScript).add("--local-host-id", "201");
            Mockito.verify(initScript).add("--json");

            Mockito.verify(localUpsert).add("config");
            Mockito.verify(localUpsert).add("host-upsert");
            Mockito.verify(localUpsert).add("--host-id", "201");
            Mockito.verify(localUpsert).add("--role", "primary");
            Mockito.verify(localUpsert).add("--management-ip", "192.168.0.11");
            Mockito.verify(localUpsert).add("--libvirt-uri", "qemu+ssh://10.0.0.11/system");
            Mockito.verify(localUpsert).add("--blockcopy-ip", "10.0.0.11");
            Mockito.verify(localUpsert).add("--xcolo-control-ip", "10.0.1.11");
            Mockito.verify(localUpsert).add("--xcolo-data-ip", "10.0.2.11");
            Mockito.verify(localUpsert).add("--json");

            Mockito.verify(peerUpsert).add("--host-id", "202");
            Mockito.verify(peerUpsert).add("--role", "secondary");
            Mockito.verify(peerUpsert).add("--management-ip", "192.168.0.12");
            Mockito.verify(peerUpsert).add("--libvirt-uri", "qemu+ssh://10.0.0.12/system");
        }
    }

    @Test
    public void testSyncProfileWrapperBuildsCommandWithOptionalFields() {
        LibvirtFtctlSyncProfileCommandWrapper wrapper = new LibvirtFtctlSyncProfileCommandWrapper();
        FtctlSyncProfileCommand command = new FtctlSyncProfileCommand("vm-a", "ft", "qemu+ssh://peer/system");
        command.setProfileName("vm-uuid");
        command.setDiskMap("vda=rbd:rbd/vm-a-secondary-disk0");
        command.setBackendMode("remote-nbd");
        command.setProvisioningBackend("cloud-managed");
        command.setTargetStorageScope("host");
        command.setSecondaryVmName("vm-a-secondary");
        command.setFencingPolicy("manual-block");
        command.setSecondaryTargetDir("/data/secondary");
        command.setRemoteNbdExportAddr("10.0.0.12:10809");
        command.setXcoloProxyEndpoint("10.0.10.12:7000");
        command.setXcoloNbdEndpoint("10.0.10.12:7001");
        command.setXcoloMigrateUri("tcp:10.0.10.12:4444");
        command.setFencingIpmiPrimaryHost("10.10.10.201");
        command.setFencingIpmiPrimaryPort("623");
        command.setFencingIpmiPrimaryUser("admin-a");
        command.setFencingIpmiPrimaryPassword("password-a");
        command.setFencingIpmiPrimaryInterface("lanplus");
        command.setFencingIpmiSecondaryHost("10.10.10.202");
        command.setFencingIpmiSecondaryPort("624");
        command.setFencingIpmiSecondaryUser("admin-b");
        command.setFencingIpmiSecondaryPassword("password-b");
        command.setFencingIpmiSecondaryInterface("lanplus");

        try (MockedConstruction<Script> scripts = Mockito.mockConstruction(Script.class, (mock, context) -> {
            Mockito.when(mock.execute(Mockito.any())).thenReturn("{\"result\":\"ok\"}");
            Mockito.when(mock.getExitValue()).thenReturn(0);
        })) {
            Answer answer = wrapper.execute(command, resource);

            Assert.assertTrue(answer instanceof FtctlSyncAnswer);
            FtctlSyncAnswer syncAnswer = (FtctlSyncAnswer) answer;
            Assert.assertTrue(syncAnswer.getResult());
            Assert.assertEquals("ok", syncAnswer.getFtctlResult());
            Assert.assertEquals(Integer.valueOf(0), syncAnswer.getExitCode());

            Script script = scripts.constructed().get(0);
            Mockito.verify(script).add("config");
            Mockito.verify(script).add("profile-upsert");
            Mockito.verify(script).add("--vm", "vm-a");
            Mockito.verify(script).add("--mode", "ft");
            Mockito.verify(script).add("--peer", "qemu+ssh://peer/system");
            Mockito.verify(script).add("--profile", "vm-uuid");
            Mockito.verify(script).add("--disk-map", "vda=rbd:rbd/vm-a-secondary-disk0");
            Mockito.verify(script).add("--backend-mode", "remote-nbd");
            Mockito.verify(script).add("--provisioning-backend", "cloud-managed");
            Mockito.verify(script).add("--target-storage-scope", "host");
            Mockito.verify(script).add("--secondary-vm-name", "vm-a-secondary");
            Mockito.verify(script).add("--fencing-policy", "manual-block");
            Mockito.verify(script).add("--fencing-ipmi-primary-host", "10.10.10.201");
            Mockito.verify(script).add("--fencing-ipmi-primary-port", "623");
            Mockito.verify(script).add("--fencing-ipmi-primary-user", "admin-a");
            Mockito.verify(script).add("--fencing-ipmi-primary-password", "password-a");
            Mockito.verify(script).add("--fencing-ipmi-primary-interface", "lanplus");
            Mockito.verify(script).add("--fencing-ipmi-secondary-host", "10.10.10.202");
            Mockito.verify(script).add("--fencing-ipmi-secondary-port", "624");
            Mockito.verify(script).add("--fencing-ipmi-secondary-user", "admin-b");
            Mockito.verify(script).add("--fencing-ipmi-secondary-password", "password-b");
            Mockito.verify(script).add("--fencing-ipmi-secondary-interface", "lanplus");
            Mockito.verify(script).add("--secondary-target-dir", "/data/secondary");
            Mockito.verify(script).add("--remote-nbd-export-addr", "10.0.0.12:10809");
            Mockito.verify(script).add("--xcolo-proxy-endpoint", "10.0.10.12:7000");
            Mockito.verify(script).add("--xcolo-nbd-endpoint", "10.0.10.12:7001");
            Mockito.verify(script).add("--xcolo-migrate-uri", "tcp:10.0.10.12:4444");
            Mockito.verify(script).add("--json");
        }
    }
}
