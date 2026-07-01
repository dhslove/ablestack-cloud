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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrExecutionContext;
import com.cloud.host.dao.HostDao;

@RunWith(MockitoJUnitRunner.class)
public class FtctlDrUnifiedActionAdapterTest {

    @Mock
    private AgentManager agentManager;
    @Mock
    private HostDao hostDao;

    @InjectMocks
    private FtctlDrUnifiedActionAdapter adapter;

    @Test
    public void syncDispatchesToCoordinatorWorkerAndReturnsAcceptedRun() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_SYNC,
                "{\"mode\":\"planned\",\"remoteMoldSecretKey\":\"top-secret\",\"dryRun\":false}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.SYNC,
                    plan.getUuid(), run.getUuid(), "accepted", true, "SYNCING", "dispatch",
                    1, "ftctl-job-1", 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"remoteMoldSecretKey\":\"top-secret\",\"state\":\"SYNCING\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        Assert.assertEquals("ftctl-job-1", result.getExternalJobRef());
        Mockito.verify(agentManager).send(Mockito.eq(103L), Mockito.any(FtctlDrActionCommand.class));

        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.SYNC, command.getAction());
        Assert.assertEquals(DrConstants.RUN_TYPE_SYNC, command.getRunType());
        Assert.assertEquals(DrConstants.DIRECTION_VMWARE_TO_KVM, command.getDirection());
        Assert.assertEquals("coordinator", command.getRole());
        Assert.assertEquals("101", command.getSourceWorkerUuid());
        Assert.assertEquals("102", command.getTargetWorkerUuid());
        Assert.assertEquals("103", command.getCoordinatorWorkerUuid());
        Assert.assertFalse(command.isWaitForCompletion());
        Assert.assertEquals("planned", command.getMode());
        Assert.assertTrue(command.getProfileJson().contains("\"engine\":\"FTCTL_DR\""));
        Assert.assertFalse(command.getProfileJson().contains("top-secret"));
        Assert.assertFalse(command.getRequestJson().contains("top-secret"));
        Assert.assertEquals("REDACTED", command.getContext().get("remoteMoldSecretKey"));
        Assert.assertFalse(result.getDetailsJson().contains("top-secret"));
    }

    @Test
    public void testFailoverDispatchesRestorePointReferenceToFtctlProfile() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_TEST_FAILOVER,
                "{\"restorePointId\":9,\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":2\"}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.TEST_FAILOVER,
                    plan.getUuid(), run.getUuid(), "accepted", true, "TESTING", "test-session-ready",
                    100, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"TESTING\",\"test_restore_point_ref\":\"ftctl:" + plan.getUuid() + ":2\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.TEST_FAILOVER, command.getAction());
        Assert.assertEquals(Long.valueOf(9L), command.getRestorePointId());
        Assert.assertTrue(command.getProfileJson().contains("\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":2\""));
        Assert.assertTrue(command.getRequestJson().contains("\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":2\""));
    }

    @Test
    public void failoverDispatchesModeRestorePointAndFinalSyncToFtctlProfile() throws Exception {
        DrPlanVO plan = ftctlDrPlan();
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILOVER,
                "{\"mode\":\"planned\",\"restorePointId\":12,\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":3\",\"finalSync\":true}");
        ArgumentCaptor<FtctlDrActionCommand> commandCaptor = ArgumentCaptor.forClass(FtctlDrActionCommand.class);
        Mockito.when(agentManager.send(Mockito.eq(103L), commandCaptor.capture())).thenAnswer(invocation -> {
            FtctlDrActionCommand command = invocation.getArgument(1);
            return new FtctlDrActionAnswer(command, true, "accepted", FtctlDrActionCommand.Action.FAILOVER,
                    plan.getUuid(), run.getUuid(), "accepted", true, "RUNNING", "failover-worker-started",
                    15, run.getUuid(), 0L, null, 0, "{\"result\":\"accepted\"}",
                    "{\"state\":\"RUNNING\",\"failover_restore_point_ref\":\"ftctl:" + plan.getUuid() + ":3\"}");
        });

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertTrue(result.isSuccess());
        Assert.assertFalse(result.isTerminal());
        FtctlDrActionCommand command = commandCaptor.getValue();
        Assert.assertEquals(FtctlDrActionCommand.Action.FAILOVER, command.getAction());
        Assert.assertEquals("planned", command.getMode());
        Assert.assertEquals(Long.valueOf(12L), command.getRestorePointId());
        Assert.assertFalse(command.isWaitForCompletion());
        Assert.assertTrue(command.getProfileJson().contains("\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":3\""));
        Assert.assertTrue(command.getProfileJson().contains("\"finalSync\":true"));
        Assert.assertTrue(command.getRequestJson().contains("\"restorePointRef\":\"ftctl:" + plan.getUuid() + ":3\""));
        Assert.assertTrue(command.getRequestJson().contains("\"finalSync\":true"));
    }

    @Test
    public void missingWorkerHostFailsBeforeAgentDispatch() throws Exception {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_KVM_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        DrRunVO run = run(DrConstants.RUN_TYPE_FAILOVER, "{\"mode\":\"disaster\"}");

        DrAdapterResult result = adapter.execute(new DrExecutionContext(plan, run));

        Assert.assertFalse(result.isSuccess());
        Assert.assertEquals(DrConstants.ERROR_TARGET_MAPPING_INVALID, result.getErrorCode());
        Mockito.verifyNoInteractions(agentManager);
    }

    private DrPlanVO ftctlDrPlan() {
        DrPlanVO plan = new DrPlanVO("ftctl-dr-plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setEngineType(DrConstants.ENGINE_TYPE_FTCTL_DR);
        plan.setEngineBindingType(DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR);
        plan.setSourceVmId(101L);
        plan.setSourceExternalRef("vmware-vm-01");
        plan.setActiveSide("SOURCE");
        plan.setRpoSeconds(30);
        plan.setRtoSeconds(300);
        plan.setSourceWorkerHostId(101L);
        plan.setTargetWorkerHostId(102L);
        plan.setCoordinatorWorkerHostId(103L);
        plan.setPolicyJson("{\"compression\":true}");
        plan.setMappingJson("{\"diskPolicy\":\"same-order\"}");
        return plan;
    }

    private DrRunVO run(String runType, String requestJson) {
        DrRunVO run = new DrRunVO(0L, runType);
        run.setState(DrConstants.RUN_STATE_QUEUED);
        run.setRequestJson(requestJson);
        return run;
    }
}
