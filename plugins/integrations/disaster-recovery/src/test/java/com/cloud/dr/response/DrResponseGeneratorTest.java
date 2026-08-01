// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
// The ASF licenses this file to you under the Apache License, Version 2.0.
package com.cloud.dr.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.cloudstack.api.response.dr.DrPlanResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.dr.DrConstants;
import com.cloud.dr.DrActionAvailability;
import com.cloud.dr.DrPlanActionEvaluation;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrPlanRuntimeVO;
import com.cloud.dr.DrProtectionAuthorityService;
import com.cloud.dr.DrProtectionAuthoritySnapshot;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.dao.DrTestSessionDao;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RunWith(MockitoJUnitRunner.class)
public class DrResponseGeneratorTest {
    private static final Gson GSON = new Gson();

    @Mock private DrSiteDao drSiteDao;
    @Mock private DrPlanDao drPlanDao;
    @Mock private DrRunDao drRunDao;
    @Mock private DrTestSessionDao drTestSessionDao;
    @Mock private DrCutoverSessionDao drCutoverSessionDao;
    @Mock private DrProtectionAuthorityService drProtectionAuthorityService;

    @InjectMocks
    private DrResponseGenerator generator;

    @Test
    public void failedReprotectRemainsInRunHistoryWithoutBecomingCurrentPlanError() {
        DrPlanVO plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        DrRunVO run = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_REPROTECT);
        run.setState(DrConstants.RUN_STATE_FAILED);
        run.setErrorCode(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING);
        run.setErrorMessage("Target VM was not running");
        run.setLastStatusJson("{\"state\":\"ERROR\",\"error_code\":\""
                + DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING + "\"}");

        Mockito.when(drRunDao.findLatestByPlanId(plan.getId())).thenReturn(run);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);

        DrPlanResponse response = generator.createPlanResponse(plan, Collections.emptyMap());
        JsonObject json = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();

        Assert.assertFalse(json.has("lasterrorcode"));
        Assert.assertFalse(json.has("lasterrormessage"));
        Assert.assertTrue(json.has("lastrun"));
        Assert.assertEquals(DrConstants.ERROR_REPROTECT_TARGET_RUNTIME_NOT_RUNNING,
                json.getAsJsonObject("lastrun").get("errorcode").getAsString());
    }

    @Test
    public void historicalFailedRunDoesNotBecomeCurrentRuntimeErrorWhenAuthorityIsReady() {
        DrPlanVO plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("SOURCE");
        DrRunVO historicalRun = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_TEST_FAILOVER);
        historicalRun.setState(DrConstants.RUN_STATE_FAILED);
        historicalRun.setErrorCode("DR_GUEST_OS_UNSUPPORTED");
        historicalRun.setErrorMessage("Guest OS was unsupported");
        historicalRun.setLastStatusJson("{\"state\":\"ERROR\",\"error_code\":\"DR_GUEST_OS_UNSUPPORTED\"}");

        DrPlanRuntimeVO runtime = readyRuntime(plan.getId());
        Mockito.when(drRunDao.findLatestByPlanId(plan.getId())).thenReturn(historicalRun);
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(null);
        Mockito.when(drProtectionAuthorityService.getAuthority(plan.getId()))
                .thenReturn(new DrProtectionAuthoritySnapshot(runtime, true));
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);

        DrPlanResponse response = generator.createPlanResponse(plan, Collections.emptyMap());
        JsonObject json = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();

        Assert.assertEquals(DrConstants.PLAN_STATE_READY, json.get("effectivestate").getAsString());
        Assert.assertEquals(DrConstants.PLAN_STATE_READY, json.get("protectionstate").getAsString());
        Assert.assertFalse(json.has("runtimeerrorcode"));
        Assert.assertFalse(json.has("runtimeprojectionmessage"));
        Assert.assertFalse(json.has("lasterrorcode"));
        Assert.assertFalse(json.has("lasterrormessage"));
        Assert.assertEquals(DrConstants.RUN_STATE_FAILED,
                json.getAsJsonObject("lastrun").get("state").getAsString());
        Assert.assertEquals("DR_GUEST_OS_UNSUPPORTED",
                json.getAsJsonObject("lastrun").get("errorcode").getAsString());
    }

    @Test
    public void currentAuthorityFailureRemainsVisibleAsCurrentError() {
        DrPlanVO plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_ERROR);
        plan.setActiveSide("SOURCE");
        DrPlanRuntimeVO runtime = readyRuntime(plan.getId());
        runtime.setProtectionState(DrConstants.PLAN_STATE_ERROR);
        runtime.setErrorCode("DR_CURRENT_RUNTIME_FAILED");
        runtime.setErrorMessage("Current runtime failed");
        runtime.setStatusJson("{\"state\":\"ERROR\",\"error_code\":\"DR_CURRENT_RUNTIME_FAILED\","
                + "\"error_message\":\"Current runtime failed\"}");

        Mockito.when(drProtectionAuthorityService.getAuthority(plan.getId()))
                .thenReturn(new DrProtectionAuthoritySnapshot(runtime, false));

        DrPlanResponse response = generator.createPlanResponse(plan, Collections.emptyMap());
        JsonObject json = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();

        Assert.assertEquals(DrConstants.PLAN_STATE_ERROR, json.get("effectivestate").getAsString());
        Assert.assertEquals("DR_CURRENT_RUNTIME_FAILED", json.get("runtimeerrorcode").getAsString());
        Assert.assertEquals("Current runtime failed", json.get("runtimeprojectionmessage").getAsString());
        Assert.assertEquals("DR_CURRENT_RUNTIME_FAILED", json.get("lasterrorcode").getAsString());
        Assert.assertEquals("Current runtime failed", json.get("lasterrormessage").getAsString());
    }

    @Test
    public void activeRunProvidesCurrentRuntimeWhenAuthorityIsUnavailable() {
        DrPlanVO plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        DrRunVO activeRun = new DrRunVO(plan.getId(), DrConstants.RUN_TYPE_SYNC);
        activeRun.setState(DrConstants.RUN_STATE_RUNNING);
        activeRun.setLastStatusJson("{\"state\":\"SYNCING\",\"step\":\"incremental-transfer\","
                + "\"control_protocol_version\":4,\"control_generation\":8,\"control_ack_generation\":8}");

        Mockito.when(drRunDao.findLatestByPlanId(plan.getId())).thenReturn(activeRun);
        Mockito.when(drRunDao.findActiveByPlanId(plan.getId())).thenReturn(activeRun);
        Mockito.when(drProtectionAuthorityService.getAuthority(plan.getId())).thenReturn(null);
        Mockito.when(drPlanDao.findById(plan.getId())).thenReturn(plan);

        DrPlanResponse response = generator.createPlanResponse(plan, Collections.emptyMap());
        JsonObject json = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();

        Assert.assertEquals("SYNCING", json.get("runtimestate").getAsString());
        Assert.assertEquals("incremental-transfer", json.get("runtimestep").getAsString());
        Assert.assertTrue(json.get("runtimecontrolready").getAsBoolean());
        Assert.assertTrue(json.get("initialsyncinprogress").getAsBoolean());
        Assert.assertFalse(json.has("runtimeerrorcode"));
    }

    @Test
    public void typedActionAvailabilityIsSerializedWithLegacyEligibility() {
        DrPlanVO plan = new DrPlanVO("plan", 1L, 2L, DrConstants.DIRECTION_VMWARE_TO_KVM);
        plan.setState(DrConstants.PLAN_STATE_READY);
        plan.setActiveSide("SOURCE");
        Map<String, Boolean> eligibility = new LinkedHashMap<String, Boolean>();
        eligibility.put("testFailover", false);
        Map<String, DrActionAvailability> availability =
                new LinkedHashMap<String, DrActionAvailability>();
        availability.put("testFailover", new DrActionAvailability(true, false,
                "DR_ACTION_TARGET_NOT_READY", Collections.emptyMap()));

        DrPlanResponse response = generator.createPlanResponse(plan,
                new DrPlanActionEvaluation(eligibility, availability));
        JsonObject json = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();

        Assert.assertFalse(json.getAsJsonObject("actioneligibility")
                .get("testFailover").getAsBoolean());
        JsonObject testFailover = json.getAsJsonObject("actionavailability")
                .getAsJsonObject("testFailover");
        Assert.assertTrue(testFailover.get("applicable").getAsBoolean());
        Assert.assertFalse(testFailover.get("enabled").getAsBoolean());
        Assert.assertEquals("DR_ACTION_TARGET_NOT_READY",
                testFailover.get("reasoncode").getAsString());
    }

    private DrPlanRuntimeVO readyRuntime(long planId) {
        DrPlanRuntimeVO runtime = new DrPlanRuntimeVO(planId);
        runtime.setProtectionState(DrConstants.PLAN_STATE_READY);
        runtime.setFreshnessState("WITHIN_RPO");
        runtime.setSchedulerState("RUNNING");
        runtime.setSchedulerHealthState("HEALTHY");
        runtime.setStatusJson("{\"state\":\"READY\",\"step\":\"target-checkpoint-ready\","
                + "\"control_protocol_version\":4,\"control_generation\":31,\"control_ack_generation\":31}");
        return runtime;
    }
}
