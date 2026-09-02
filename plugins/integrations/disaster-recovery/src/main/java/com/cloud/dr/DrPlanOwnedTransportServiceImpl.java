// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.dr.adapter.ftctl.DrRemoteAgentClient;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrPlanOwnedTransportServiceImpl extends ManagerBase implements DrPlanOwnedTransportService {
    private static final Gson GSON = new Gson();
    private static final int TRANSITION_WAIT_SECONDS = 45;

    @Inject private AgentManager agentManager;
    @Inject private HostDao hostDao;
    @Inject private DrRemoteAgentClient drRemoteAgentClient;
    @Inject private DrWorkerPlacementService drWorkerPlacementService;

    @Override
    public boolean supports(DrPlanVO plan) {
        return plan != null && drRemoteAgentClient != null
                && drRemoteAgentClient.isRemoteKvmSource(plan)
                && StringUtils.equalsIgnoreCase(plan.getDirection(), DrConstants.DIRECTION_KVM_TO_KVM);
    }

    @Override
    public JsonArray startForwardTargetExport(DrPlanVO plan, DrRunVO run, String profileJson) {
        if (!supports(plan)) {
            return new JsonArray();
        }
        HostVO targetHost = targetHost(plan);
        FtctlDrActionCommand command = command(plan, run, FtctlDrActionCommand.Action.TARGET_EXPORT_START,
                "target", targetHost.getUuid(), profileJson);
        Answer answer = agentManager.easySend(targetHost.getId(), command);
        return requireExports(answer, "Target Agent did not prepare the Plan-owned RBD export");
    }

    @Override
    public JsonArray startReverseTargetExport(DrPlanVO plan, DrRunVO run, String profileJson) {
        if (!supports(plan)) {
            return new JsonArray();
        }
        String workerUuid = null;
        JsonObject profile = parseObject(profileJson);
        objectAt(profile, "request").addProperty("reverseTargetExport", true);
        FtctlDrActionCommand command = command(plan, run, FtctlDrActionCommand.Action.TARGET_EXPORT_START,
                "reverse-target", workerUuid, GSON.toJson(profile));
        Answer answer = drRemoteAgentClient.execute(plan, "ACTION", command,
                workerUuid, FtctlDrActionAnswer.class);
        return requireExports(answer, "Original-site Agent did not prepare the reverse RBD export");
    }

    @Override
    public void stopForwardTargetExport(DrPlanVO plan, DrRunVO run, String profileJson,
            Long checkpointSequence) {
        if (!supports(plan)) {
            return;
        }
        HostVO targetHost = targetHost(plan);
        FtctlDrActionCommand command = command(plan, run, FtctlDrActionCommand.Action.TARGET_EXPORT_STOP,
                "target", targetHost.getUuid(), profileJson);
        // Test Failover drains the mutable FILE writer before sealing the
        // selected checkpoint. It must not ask FTCTL to create the reverse
        // cutover baseline that is owned exclusively by a real Failover.
        if (!StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            command.setCutoverCheckpointSequence(checkpointSequence);
        }
        requireSuccess(agentManager.easySend(targetHost.getId(), command),
                "Target Agent did not stop the Plan-owned RBD export");
    }

    @Override
    public void stopReverseTargetExport(DrPlanVO plan, DrRunVO run) {
        if (!supports(plan)) {
            return;
        }
        String workerUuid = null;
        FtctlDrActionCommand command = command(plan, run, FtctlDrActionCommand.Action.TARGET_EXPORT_STOP,
                "reverse-target", workerUuid, null);
        requireSuccess(drRemoteAgentClient.execute(plan, "ACTION", command,
                workerUuid, FtctlDrActionAnswer.class),
                "Original-site Agent did not drain the reverse RBD export");
    }

    private FtctlDrActionCommand command(DrPlanVO plan, DrRunVO run,
            FtctlDrActionCommand.Action action, String role, String workerUuid, String profileJson) {
        if (run == null || StringUtils.isBlank(run.getUuid())) {
            throw new CloudRuntimeException("DR Run is required for Plan-owned transport transition");
        }
        FtctlDrActionCommand command = new FtctlDrActionCommand(action, plan.getUuid(), run.getUuid());
        command.setActionName(action.name());
        command.setCliCommand(action.getCliCommand());
        command.setRunType(run.getRunType());
        command.setActionIntent(run.getRunType());
        command.setDirection(plan.getDirection());
        command.setRole(role);
        command.setTargetWorkerUuid(workerUuid);
        if (StringUtils.isNotBlank(profileJson)) {
            command.setProfileJson(profileJson);
        }
        command.setWaitForCompletion(true);
        command.setWait(TRANSITION_WAIT_SECONDS);
        return command;
    }

    private HostVO targetHost(DrPlanVO plan) {
        Long hostId = drWorkerPlacementService != null
                ? drWorkerPlacementService.resolveWorkerHostId(plan, DrWorkerRole.TARGET) : null;
        HostVO host = hostId != null ? hostDao.findById(hostId) : null;
        if (host == null || StringUtils.isBlank(host.getUuid())) {
            throw new CloudRuntimeException("DR target worker host is required for Plan-owned transport");
        }
        return host;
    }

    private JsonArray requireExports(Answer answer, String fallback) {
        requireSuccess(answer, fallback);
        if (!(answer instanceof FtctlDrActionAnswer)) {
            throw new CloudRuntimeException(fallback + ": Agent returned no structured export status");
        }
        JsonArray exports = firstArray(parseObject(((FtctlDrActionAnswer) answer).getStatusJson()), "exports");
        if (exports.size() == 0) {
            throw new CloudRuntimeException(fallback + ": Agent returned no RBD export endpoints");
        }
        return exports;
    }

    private void requireSuccess(Answer answer, String fallback) {
        if (answer == null || !answer.getResult()) {
            throw new CloudRuntimeException(StringUtils.defaultIfBlank(
                    answer != null ? answer.getDetails() : null, fallback));
        }
    }

    private JsonObject parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JsonObject();
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            throw new CloudRuntimeException("DR transport profile JSON is invalid", e);
        }
    }

    private JsonObject objectAt(JsonObject parent, String name) {
        JsonElement current = parent.get(name);
        if (current != null && current.isJsonObject()) {
            return current.getAsJsonObject();
        }
        JsonObject child = new JsonObject();
        parent.add(name, child);
        return child;
    }

    private JsonArray firstArray(JsonObject parent, String name) {
        JsonElement value = parent != null ? parent.get(name) : null;
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
