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

import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrExecutionContext;
import com.cloud.dr.adapter.DrReplicationEngine;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FtctlDrUnifiedActionAdapter extends ManagerBase implements DrReplicationEngine {
    private static final Logger LOGGER = LogManager.getLogger(FtctlDrUnifiedActionAdapter.class);
    private static final Gson GSON = new Gson();
    private static final int AGENT_ACCEPT_TIMEOUT_SECONDS = 60;

    @Inject
    private AgentManager agentManager;
    @Inject
    private HostDao hostDao;

    @Override
    public String getEngineType() {
        return DrConstants.ENGINE_TYPE_FTCTL_DR;
    }

    @Override
    public String getEngineBindingType() {
        return DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR;
    }

    @Override
    public DrAdapterResult validatePlan(DrPlanVO plan) {
        if (plan == null) {
            return DrAdapterResult.failure(DrConstants.ERROR_PLAN_NOT_FOUND, "FTCTL_DR plan is required", null);
        }
        String direction = StringUtils.upperCase(plan.getDirection(), Locale.ROOT);
        if (!StringUtils.equalsAny(direction, DrConstants.DIRECTION_KVM_TO_KVM, DrConstants.DIRECTION_KVM_TO_VMWARE,
                DrConstants.DIRECTION_VMWARE_TO_VMWARE, DrConstants.DIRECTION_VMWARE_TO_KVM)) {
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNSUPPORTED,
                    "FTCTL_DR does not support direction " + plan.getDirection(), GSON.toJson(buildValidationDetails(plan)));
        }
        return DrAdapterResult.success("FTCTL_DR plan contract is valid", GSON.toJson(buildValidationDetails(plan)));
    }

    @Override
    public DrAdapterResult execute(DrExecutionContext context) {
        FtctlDrActionCommand.Action action = resolveAction(context.getRun());
        if (action == null) {
            String message = "DR run type " + context.getRun().getRunType() + " is not supported by FTCTL_DR";
            return DrAdapterResult.failure(DrConstants.ERROR_ACTION_UNSUPPORTED, message, GSON.toJson(buildExecutionDetails(context, null, null)));
        }

        Long coordinatorHostId = resolveCoordinatorHostId(context.getPlan());
        if (coordinatorHostId == null) {
            String message = "FTCTL_DR requires a coordinator, source, or target worker host before dispatch";
            return DrAdapterResult.failure(DrConstants.ERROR_TARGET_MAPPING_INVALID, message, GSON.toJson(buildExecutionDetails(context, action, null)));
        }

        FtctlDrActionCommand command = buildActionCommand(context, action);
        try {
            Answer answer = agentManager.send(coordinatorHostId, command);
            return toAdapterResult(context, action, coordinatorHostId, answer);
        } catch (AgentUnavailableException | OperationTimedoutException e) {
            LOGGER.warn("Unable to dispatch FTCTL_DR run {} to host {}: {}", context.getRun().getId(), coordinatorHostId, e.getMessage());
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNAVAILABLE,
                    "Unable to dispatch FTCTL_DR run to Agent: " + e.getMessage(), GSON.toJson(buildExecutionDetails(context, action, coordinatorHostId)));
        }
    }

    private FtctlDrActionCommand buildActionCommand(DrExecutionContext context, FtctlDrActionCommand.Action action) {
        DrPlanVO plan = context.getPlan();
        DrRunVO run = context.getRun();
        JsonObject request = requestJson(run);
        JsonObject redactedRequest = redactJson(request).getAsJsonObject();
        FtctlDrActionCommand command = new FtctlDrActionCommand(action, plan.getUuid(), run.getUuid());
        command.setRunType(run.getRunType());
        command.setDirection(plan.getDirection());
        command.setRole("coordinator");
        command.setSourceWorkerUuid(resolveHostUuid(plan.getSourceWorkerHostId()));
        command.setTargetWorkerUuid(resolveHostUuid(plan.getTargetWorkerHostId()));
        command.setCoordinatorWorkerUuid(resolveHostUuid(resolveCoordinatorHostId(plan)));
        command.setProfileJson(buildProfileJson(plan, run, redactedRequest));
        command.setRequestJson(GSON.toJson(redactedRequest));
        command.setMode(requestString(request, "mode"));
        command.setRestorePointId(requestLong(request, "restorePointId"));
        command.setForce(requestBoolean(request, "force", false));
        command.setDryRun(requestBoolean(request, "dryRun", false));
        command.setWaitForCompletion(false);
        command.setWait(AGENT_ACCEPT_TIMEOUT_SECONDS);
        command.setContext(toStringMap(redactedRequest));
        return command;
    }

    private DrAdapterResult toAdapterResult(DrExecutionContext context, FtctlDrActionCommand.Action action, long hostId, Answer answer) {
        JsonObject details = buildExecutionDetails(context, action, hostId);
        if (!(answer instanceof FtctlDrActionAnswer)) {
            String message = answer != null ? answer.getDetails() : "Agent returned no FTCTL_DR answer";
            details.addProperty("answerType", answer != null ? answer.getClass().getName() : null);
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_ACTION_FAILED, message, GSON.toJson(details));
        }

        FtctlDrActionAnswer actionAnswer = (FtctlDrActionAnswer) answer;
        details.add("agentAnswer", GSON.toJsonTree(redactedAnswer(actionAnswer)));
        if (!actionAnswer.getResult()) {
            String errorCode = StringUtils.defaultIfBlank(actionAnswer.getErrorCode(), DrConstants.ERROR_ENGINE_ACTION_FAILED);
            String message = StringUtils.defaultIfBlank(actionAnswer.getDetails(), "FTCTL_DR Agent command failed");
            return DrAdapterResult.failure(errorCode, message, GSON.toJson(details));
        }

        String result = StringUtils.lowerCase(StringUtils.defaultString(actionAnswer.getFtctlResult()), Locale.ROOT);
        boolean accepted = Boolean.TRUE.equals(actionAnswer.getAccepted())
                || StringUtils.equalsAny(result, "accepted", "ok", "success", "delegated", "warn")
                || Integer.valueOf(0).equals(actionAnswer.getExitCode());
        if (!accepted) {
            String message = "FTCTL_DR Agent command did not return an accepted result";
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_ACTION_FAILED, message, GSON.toJson(details));
        }

        String externalJobRef = StringUtils.defaultIfBlank(actionAnswer.getExternalJobRef(), context.getRun().getUuid());
        if (isTerminalControlAction(context.getRun())) {
            return DrAdapterResult.success("FTCTL_DR control action " + action + " accepted by Agent", GSON.toJson(details));
        }
        return DrAdapterResult.accepted("FTCTL_DR action " + action + " accepted by Agent", GSON.toJson(details), externalJobRef);
    }

    private FtctlDrActionCommand.Action resolveAction(DrRunVO run) {
        String runType = StringUtils.upperCase(run.getRunType(), Locale.ROOT);
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_SYNC)) {
            return FtctlDrActionCommand.Action.SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_PAUSE_SYNC)) {
            return FtctlDrActionCommand.Action.PAUSE_SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_RESUME_SYNC)) {
            return FtctlDrActionCommand.Action.RESUME_SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return FtctlDrActionCommand.Action.TEST_FAILOVER;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_CLEANUP)) {
            return FtctlDrActionCommand.Action.TEST_CLEANUP;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILOVER)) {
            return FtctlDrActionCommand.Action.FAILOVER;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILBACK)) {
            return FtctlDrActionCommand.Action.FAILBACK;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_REPROTECT)) {
            return FtctlDrActionCommand.Action.REPROTECT;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_RELEASE)) {
            return FtctlDrActionCommand.Action.RELEASE;
        }
        return null;
    }

    private boolean isTerminalControlAction(DrRunVO run) {
        return StringUtils.equalsAny(StringUtils.upperCase(run.getRunType(), Locale.ROOT),
                DrConstants.RUN_TYPE_PAUSE_SYNC, DrConstants.RUN_TYPE_RESUME_SYNC,
                DrConstants.RUN_TYPE_TEST_CLEANUP, DrConstants.RUN_TYPE_RELEASE);
    }

    private Long resolveCoordinatorHostId(DrPlanVO plan) {
        if (plan.getCoordinatorWorkerHostId() != null) {
            return plan.getCoordinatorWorkerHostId();
        }
        if (plan.getSourceWorkerHostId() != null) {
            return plan.getSourceWorkerHostId();
        }
        return plan.getTargetWorkerHostId();
    }

    private String resolveHostUuid(Long hostId) {
        if (hostId == null) {
            return null;
        }
        HostVO host = hostDao != null ? hostDao.findById(hostId) : null;
        return host != null && StringUtils.isNotBlank(host.getUuid()) ? host.getUuid() : String.valueOf(hostId);
    }

    private String buildProfileJson(DrPlanVO plan, DrRunVO run, JsonObject request) {
        JsonObject profile = new JsonObject();
        profile.addProperty("version", 1);
        profile.addProperty("engine", DrConstants.ENGINE_TYPE_FTCTL_DR);
        profile.addProperty("planUuid", plan.getUuid());
        profile.addProperty("runUuid", run.getUuid());
        profile.addProperty("direction", plan.getDirection());
        profile.addProperty("activeSide", plan.getActiveSide());
        profile.addProperty("rpoTargetSeconds", plan.getRpoSeconds());
        profile.addProperty("rtoTargetSeconds", plan.getRtoSeconds());
        profile.add("source", buildEndpoint(plan.getDirection(), true, plan.getSourceVmId(), plan.getSourceExternalRef()));
        profile.add("target", buildEndpoint(plan.getDirection(), false, null, null));
        profile.add("workers", buildWorkers(plan));
        profile.add("policy", parseObject(plan.getPolicyJson()));
        profile.add("mapping", parseObject(plan.getMappingJson()));
        profile.add("schedule", parseObject(plan.getScheduleJson()));
        profile.add("quiescePolicy", parseObject(plan.getQuiescePolicyJson()));
        profile.add("request", request);
        return GSON.toJson(profile);
    }

    private JsonObject buildEndpoint(String direction, boolean source, Long vmId, String externalRef) {
        JsonObject endpoint = new JsonObject();
        boolean vmware = source ? StringUtils.startsWith(direction, "VMWARE_") : StringUtils.endsWith(direction, "_VMWARE");
        endpoint.addProperty("provider", vmware ? "VMWARE" : "ABLESTACK");
        endpoint.addProperty("driver", vmware ? (source ? "VMWARE_CBT" : "VMWARE_VDDK") : (source ? "KVM_QMP" : "ABLESTACK"));
        if (vmId != null) {
            endpoint.addProperty("vmId", vmId);
        }
        if (StringUtils.isNotBlank(externalRef)) {
            endpoint.addProperty("externalRef", externalRef);
        }
        return endpoint;
    }

    private JsonObject buildWorkers(DrPlanVO plan) {
        JsonObject workers = new JsonObject();
        workers.addProperty("coordinator", resolveHostUuid(resolveCoordinatorHostId(plan)));
        workers.addProperty("source", resolveHostUuid(plan.getSourceWorkerHostId()));
        workers.addProperty("target", resolveHostUuid(plan.getTargetWorkerHostId()));
        return workers;
    }

    private JsonObject buildValidationDetails(DrPlanVO plan) {
        JsonObject details = new JsonObject();
        details.addProperty("engineType", getEngineType());
        details.addProperty("engineBindingType", getEngineBindingType());
        if (plan != null) {
            details.addProperty("planId", plan.getId());
            details.addProperty("direction", plan.getDirection());
            details.addProperty("coordinatorWorkerHostId", plan.getCoordinatorWorkerHostId());
            details.addProperty("sourceWorkerHostId", plan.getSourceWorkerHostId());
            details.addProperty("targetWorkerHostId", plan.getTargetWorkerHostId());
        }
        details.addProperty("runtimeDispatchReady", true);
        details.addProperty("statusPollingReady", true);
        return details;
    }

    private JsonObject buildExecutionDetails(DrExecutionContext context, FtctlDrActionCommand.Action action, Long hostId) {
        JsonObject details = new JsonObject();
        details.addProperty("engineType", getEngineType());
        details.addProperty("engineBindingType", getEngineBindingType());
        details.addProperty("runType", context.getRun() != null ? context.getRun().getRunType() : null);
        details.addProperty("action", action != null ? action.name() : null);
        details.addProperty("agentHostId", hostId);
        if (context.getPlan() != null) {
            details.addProperty("planId", context.getPlan().getId());
            details.addProperty("planUuid", context.getPlan().getUuid());
            details.addProperty("direction", context.getPlan().getDirection());
        }
        if (context.getRun() != null) {
            details.addProperty("runId", context.getRun().getId());
            details.addProperty("runUuid", context.getRun().getUuid());
            details.add("request", redactJson(requestJson(context.getRun())));
        }
        return details;
    }

    private JsonObject redactedAnswer(FtctlDrActionAnswer answer) {
        JsonObject object = new JsonObject();
        object.addProperty("action", answer.getAction() != null ? answer.getAction().name() : null);
        object.addProperty("planUuid", answer.getPlanUuid());
        object.addProperty("runUuid", answer.getRunUuid());
        object.addProperty("result", answer.getFtctlResult());
        object.addProperty("accepted", answer.getAccepted());
        object.addProperty("state", answer.getState());
        object.addProperty("step", answer.getStep());
        object.addProperty("progress", answer.getProgress());
        object.addProperty("externalJobRef", answer.getExternalJobRef());
        object.addProperty("eventsOffset", answer.getEventsOffset());
        object.addProperty("errorCode", answer.getErrorCode());
        object.addProperty("exitCode", answer.getExitCode());
        object.add("status", redactJson(parseElement(answer.getStatusJson())));
        return object;
    }

    private JsonObject requestJson(DrRunVO run) {
        if (run == null || StringUtils.isBlank(run.getRequestJson())) {
            return new JsonObject();
        }
        JsonElement parsed = parseElement(run.getRequestJson());
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    private JsonObject parseObject(String json) {
        JsonElement element = parseElement(json);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private JsonElement parseElement(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonElement redactJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        if (element.isJsonObject()) {
            JsonObject redacted = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (isSecretKey(entry.getKey())) {
                    redacted.addProperty(entry.getKey(), "REDACTED");
                } else {
                    redacted.add(entry.getKey(), redactJson(entry.getValue()));
                }
            }
            return redacted;
        }
        if (element.isJsonArray()) {
            JsonArray redacted = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                redacted.add(redactJson(item));
            }
            return redacted;
        }
        return element.deepCopy();
    }

    private boolean isSecretKey(String key) {
        String lower = StringUtils.lowerCase(key, Locale.ROOT);
        return StringUtils.contains(lower, "password")
                || StringUtils.contains(lower, "secret")
                || StringUtils.contains(lower, "token")
                || StringUtils.contains(lower, "apikey")
                || StringUtils.contains(lower, "api_key")
                || StringUtils.contains(lower, "credential");
    }

    private Map<String, String> toStringMap(JsonObject object) {
        Map<String, String> values = new java.util.HashMap<String, String>();
        if (object == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString());
        }
        return values;
    }

    private String requestString(JsonObject request, String key) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsString() : null;
    }

    private Long requestLong(JsonObject request, String key) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsLong() : null;
    }

    private boolean requestBoolean(JsonObject request, String key, boolean defaultValue) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsBoolean() : defaultValue;
    }
}
