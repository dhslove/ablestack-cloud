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

import java.util.Locale;

import javax.inject.Inject;

import org.apache.cloudstack.api.response.ftctl.FtctlDrSiteAgentCommandResponse;
import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrCapabilitiesCommand;
import com.cloud.agent.api.FtctlDrCancelCommand;
import com.cloud.agent.api.FtctlDrReversePreflightCommand;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Executes a narrow set of FTCTL DR commands on a host owned by this Cloud.
 * Plan lifecycle authority remains with the signed API caller's Cloud.
 */
public class FtctlDrSiteAgentBrokerServiceImpl extends ManagerBase implements FtctlDrSiteAgentBrokerService {
    private static final Gson GSON = new Gson();

    @Inject private AgentManager agentManager;
    @Inject private HostDao hostDao;

    @Override
    public FtctlDrSiteAgentCommandResponse execute(String commandType, String commandJson, String workerHostUuid) {
        String normalizedType = StringUtils.upperCase(StringUtils.trim(commandType), Locale.ROOT);
        HostVO host = hostDao.findByUuid(StringUtils.trim(workerHostUuid));
        if (host == null || host.getRemoved() != null) {
            throw new CloudRuntimeException("FTCTL DR site worker host was not found: " + workerHostUuid);
        }
        if (!Status.Up.equals(host.getStatus()) || !Hypervisor.HypervisorType.KVM.equals(host.getHypervisorType())) {
            throw new CloudRuntimeException("FTCTL DR site worker must be an Up KVM host: " + workerHostUuid);
        }
        Command command = deserialize(normalizedType, commandJson);
        prepareSiteLocalCommand(command, host);
        Answer answer;
        try {
            answer = agentManager.send(host.getId(), command);
        } catch (AgentUnavailableException e) {
            throw new CloudRuntimeException("FTCTL DR site worker is unavailable: " + workerHostUuid, e);
        } catch (OperationTimedoutException e) {
            throw new CloudRuntimeException("FTCTL DR site command timed out: " + normalizedType, e);
        }
        if (answer == null) {
            throw new CloudRuntimeException("FTCTL DR site worker returned no answer for " + normalizedType);
        }
        FtctlDrSiteAgentCommandResponse response = new FtctlDrSiteAgentCommandResponse();
        response.setObjectName("ftctldrsiteagentcommand");
        response.setCommandType(normalizedType);
        response.setWorkerHostUuid(host.getUuid());
        response.setResult(answer.getResult());
        response.setDetails(answer.getDetails());
        response.setAnswerClass(answer.getClass().getName());
        response.setAnswerJson(GSON.toJson(answer));
        return response;
    }

    private void prepareSiteLocalCommand(Command command, HostVO host) {
        if (!(command instanceof FtctlDrActionCommand) || host == null) {
            return;
        }
        FtctlDrActionCommand actionCommand = (FtctlDrActionCommand) command;
        if (actionCommand.getAction() != FtctlDrActionCommand.Action.TARGET_EXPORT_START) {
            return;
        }
        if (StringUtils.isBlank(host.getPrivateIpAddress())) {
            throw new CloudRuntimeException("FTCTL DR target export worker address is required: " + host.getUuid());
        }
        JsonObject profile = parseObject(actionCommand.getProfileJson());
        JsonObject transport = objectAt(profile, "transport");
        transport.addProperty("targetHostUuid", host.getUuid());
        transport.addProperty("targetHostAddress", host.getPrivateIpAddress());
        transport.addProperty("remoteNbdExportAddress", host.getPrivateIpAddress());
        transport.remove("exports");
        actionCommand.setProfileJson(GSON.toJson(profile));
    }

    private JsonObject parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            throw new CloudRuntimeException("FTCTL DR action profile JSON is invalid", e);
        }
    }

    private JsonObject objectAt(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        if (value != null && value.isJsonObject()) {
            return value.getAsJsonObject();
        }
        JsonObject child = new JsonObject();
        object.add(key, child);
        return child;
    }

    private Command deserialize(String commandType, String commandJson) {
        if (StringUtils.isBlank(commandJson)) {
            throw new CloudRuntimeException("FTCTL DR site command JSON is required");
        }
        switch (commandType) {
            case "ACTION":
                return GSON.fromJson(commandJson, FtctlDrActionCommand.class);
            case "STATUS":
                return GSON.fromJson(commandJson, FtctlDrStatusCommand.class);
            case "CAPABILITIES":
                return GSON.fromJson(commandJson, FtctlDrCapabilitiesCommand.class);
            case "CANCEL":
                return GSON.fromJson(commandJson, FtctlDrCancelCommand.class);
            case "REVERSE_PREFLIGHT":
                return GSON.fromJson(commandJson, FtctlDrReversePreflightCommand.class);
            default:
                throw new CloudRuntimeException("Unsupported FTCTL DR site command type: " + commandType);
        }
    }
}
