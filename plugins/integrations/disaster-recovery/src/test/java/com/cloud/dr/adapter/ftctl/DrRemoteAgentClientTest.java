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
package com.cloud.dr.adapter.ftctl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrCancelAnswer;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrResolvedSiteCredential;
import com.cloud.dr.DrSiteCredentialService;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.inventory.DrMoldInventoryClient;

public class DrRemoteAgentClientTest {

    @Test
    public void sourceWorkerUuidRefreshesAndPersistsCurrentRemoteWorker() {
        DrRemoteAgentClient client = new DrRemoteAgentClient();
        DrSiteDao siteDao = Mockito.mock(DrSiteDao.class);
        DrPlanDao planDao = Mockito.mock(DrPlanDao.class);
        DrSiteCredentialService credentialService = Mockito.mock(DrSiteCredentialService.class);
        DrMoldInventoryClient inventoryClient = Mockito.mock(DrMoldInventoryClient.class);
        DrResolvedSiteCredential credential = Mockito.mock(DrResolvedSiteCredential.class);
        DrSiteVO site = Mockito.mock(DrSiteVO.class);
        ReflectionTestUtils.setField(client, "drSiteDao", siteDao);
        ReflectionTestUtils.setField(client, "drPlanDao", planDao);
        ReflectionTestUtils.setField(client, "drSiteCredentialService", credentialService);
        ReflectionTestUtils.setField(client, "drMoldInventoryClient", inventoryClient);

        DrPlanVO plan = new DrPlanVO("remote-source", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        ReflectionTestUtils.setField(plan, "id", 42L);
        plan.setSourceExternalRef("source-vm-uuid");
        plan.setMappingJson("{\"source\":{\"hardware\":{}}}");
        Map<String, String> hardware = new LinkedHashMap<String, String>();
        hardware.put("sourceHostUuid", "current-source-host-uuid");
        Mockito.when(siteDao.findById(1L)).thenReturn(site);
        Mockito.when(credentialService.resolveCredential(site)).thenReturn(credential);
        Mockito.when(credential.hasSecrets()).thenReturn(true);
        Mockito.when(inventoryClient.getVirtualMachineHardware(credential, "source-vm-uuid"))
                .thenReturn(hardware);
        Mockito.when(planDao.update(42L, plan)).thenReturn(true);

        Assert.assertEquals("current-source-host-uuid", client.sourceWorkerUuid(plan));
        Assert.assertTrue(plan.getMappingJson().contains("\"sourceWorkerHostUuid\":\"current-source-host-uuid\""));
        Assert.assertTrue(plan.getMappingJson().contains("\"sourceHostUuid\":\"current-source-host-uuid\""));
        Mockito.verify(planDao).update(42L, plan);
        Mockito.verify(credential).close();
    }

    @Test
    public void sourceWorkerUuidUsesDurableMappingWhenCurrentInventoryIsUnavailable() {
        DrRemoteAgentClient client = new DrRemoteAgentClient();
        DrSiteDao siteDao = Mockito.mock(DrSiteDao.class);
        DrPlanDao planDao = Mockito.mock(DrPlanDao.class);
        DrSiteCredentialService credentialService = Mockito.mock(DrSiteCredentialService.class);
        DrMoldInventoryClient inventoryClient = Mockito.mock(DrMoldInventoryClient.class);
        ReflectionTestUtils.setField(client, "drSiteDao", siteDao);
        ReflectionTestUtils.setField(client, "drPlanDao", planDao);
        ReflectionTestUtils.setField(client, "drSiteCredentialService", credentialService);
        ReflectionTestUtils.setField(client, "drMoldInventoryClient", inventoryClient);

        DrPlanVO plan = new DrPlanVO("remote-source", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setSourceExternalRef("source-vm-uuid");
        plan.setMappingJson("{\"sourceWorkerHostUuid\":\"durable-source-host-uuid\"}");
        Mockito.when(siteDao.findById(1L)).thenReturn(Mockito.mock(DrSiteVO.class));
        Mockito.when(credentialService.resolveCredential(Mockito.any(DrSiteVO.class)))
                .thenThrow(new IllegalStateException("source site temporarily unavailable"));

        Assert.assertEquals("durable-source-host-uuid", client.sourceWorkerUuid(plan));
        Mockito.verifyNoInteractions(planDao);
    }

    @Test
    public void sourceTransitionRunUuidIsDeterministicAndSchemaSafe() {
        String planUuid = "7ec74483-8554-415d-ac56-f62f8b17fbd0";
        String parentRunUuid = "d64fdb24-9fb3-49ad-82de-2556db63698b";

        String first = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.RESUME_SYNC);
        String second = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.RESUME_SYNC);
        String pause = DrRemoteAgentClient.sourceTransitionRunUuid(planUuid, parentRunUuid,
                FtctlDrActionCommand.Action.PAUSE_SYNC);

        Assert.assertEquals(first, second);
        Assert.assertNotEquals(first, pause);
        Assert.assertEquals(36, first.length());
        Assert.assertEquals(first, UUID.fromString(first).toString());
    }

    @Test
    public void sourceResumeDispatchesRehydratedProfile() {
        DrRemoteAgentClient client = Mockito.spy(new DrRemoteAgentClient());
        DrPlanVO plan = new DrPlanVO("remote-source", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setMappingJson("{\"source\":{\"hardware\":{\"sourceHostUuid\":\"source-host-uuid\"}}}");
        String profileJson = "{\"planUuid\":\"" + plan.getUuid()
                + "\",\"transport\":{\"mode\":\"site-agent-nbd\",\"exports\":[{\"device\":\"sda\"}]}}";
        FtctlDrActionCommand[] captured = new FtctlDrActionCommand[1];
        Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(2);
            return new FtctlDrActionAnswer(captured[0], true, "resumed");
        }).when(client).execute(Mockito.eq(plan), Mockito.eq("ACTION"),
                Mockito.isA(FtctlDrActionCommand.class), Mockito.eq("source-host-uuid"),
                Mockito.eq(FtctlDrActionAnswer.class));

        FtctlDrActionAnswer answer = client.transitionSourceScheduler(plan,
                FtctlDrActionCommand.Action.RESUME_SYNC, "parent-run", profileJson, 9L, 10L, 676L);

        Assert.assertTrue(answer.getResult());
        Assert.assertNotNull(captured[0]);
        Assert.assertEquals(profileJson, captured[0].getProfileJson());
        Assert.assertEquals("source-host-uuid", captured[0].getSourceWorkerUuid());
        Assert.assertEquals(DrConstants.RUN_TYPE_RESUME_SYNC, captured[0].getRunType());
        Assert.assertEquals(Long.valueOf(9), captured[0].getResumeBaselineCheckpointSequence());
        Assert.assertEquals(Long.valueOf(10), captured[0].getMinimumCompletedCheckpointSequence());
        Assert.assertEquals(Long.valueOf(676), captured[0].getAuthoritySequenceFloor());
    }

    @Test
    public void sourceCancelUsesTypedRemoteAgentContract() {
        DrRemoteAgentClient client = Mockito.spy(new DrRemoteAgentClient());
        DrPlanVO plan = new DrPlanVO("remote-source", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setMappingJson("{\"source\":{\"hardware\":{\"sourceHostUuid\":\"source-host-uuid\"}}}");
        FtctlDrCancelCommand[] captured = new FtctlDrCancelCommand[1];
        Mockito.doAnswer(invocation -> {
            captured[0] = invocation.getArgument(2);
            return new FtctlDrCancelAnswer(captured[0], true, "canceled", plan.getUuid(), "run-uuid",
                    "canceled", true, null, 0, "{\"state\":\"CANCELED\"}");
        }).when(client).execute(Mockito.eq(plan), Mockito.eq("CANCEL"),
                Mockito.isA(FtctlDrCancelCommand.class), Mockito.eq("source-host-uuid"),
                Mockito.eq(FtctlDrCancelAnswer.class));

        FtctlDrCancelAnswer answer = client.cancelSourceRun(plan, "run-uuid");

        Assert.assertTrue(answer.getResult());
        Assert.assertEquals(plan.getUuid(), captured[0].getPlanUuid());
        Assert.assertEquals("run-uuid", captured[0].getRunUuid());
    }
}
