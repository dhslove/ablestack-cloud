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
package com.cloud.dr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.api.response.dr.DrSiteAgentCommandResponse;

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
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Executes a narrowly allow-listed FTCTL command on a host owned by this Cloud.
 * The caller authenticates through the normal signed Mold API; numeric host IDs
 * never cross the site boundary.
 */
public class DrSiteAgentBrokerServiceImpl extends ManagerBase implements DrSiteAgentBrokerService {
    private static final Gson GSON = new Gson();

    @Inject private AgentManager agentManager;
    @Inject private HostDao hostDao;
    @Inject private DataCenterDao dataCenterDao;
    @Inject private UserVmDao userVmDao;

    @Override
    public DrSiteAgentCommandResponse execute(String commandType, String commandJson, String workerHostUuid) {
        String normalizedType = StringUtils.upperCase(StringUtils.trim(commandType), Locale.ROOT);
        Command command = deserialize(normalizedType, commandJson);
        List<HostVO> candidates = eligibleWorkers();
        preferCurrentVmHost(command, candidates);
        if (candidates.isEmpty()) {
            throw new CloudRuntimeException("No eligible DR site Agent worker is available");
        }
        DispatchResult dispatched = dispatch(normalizedType, command, candidates);
        HostVO host = dispatched.host;
        Answer answer = dispatched.answer;
        if (answer == null) {
            throw new CloudRuntimeException("DR site Agent returned no answer for " + normalizedType);
        }
        DrSiteAgentCommandResponse response = new DrSiteAgentCommandResponse();
        response.setObjectName("drsiteagentcommand");
        response.setCommandType(normalizedType);
        response.setWorkerHostUuid(host.getUuid());
        response.setResult(answer.getResult());
        response.setDetails(answer.getDetails());
        response.setAnswerClass(answer.getClass().getName());
        response.setAnswerJson(GSON.toJson(answer));
        return response;
    }

    private DispatchResult dispatch(String commandType, Command command, List<HostVO> candidates) {
        RuntimeException lastError = null;
        for (HostVO host : candidates) {
            try {
                Answer answer = agentManager.send(host.getId(), command);
                if (answer != null && (isReadOnlyOrCancel(commandType) ? meaningful(answer) : true)) {
                    return new DispatchResult(host, answer);
                }
            } catch (AgentUnavailableException e) {
                lastError = new CloudRuntimeException("DR site Agent worker is unavailable: " + host.getUuid(), e);
            } catch (OperationTimedoutException e) {
                lastError = new CloudRuntimeException("DR site Agent command timed out: " + commandType, e);
            }
            if ("ACTION".equals(commandType) || "REVERSE_PREFLIGHT".equals(commandType)) {
                break;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new CloudRuntimeException("No DR site Agent returned matching evidence for " + commandType);
    }

    private boolean isReadOnlyOrCancel(String commandType) {
        return StringUtils.equalsAny(commandType, "STATUS", "CAPABILITIES", "CANCEL");
    }

    private boolean meaningful(Answer answer) {
        if (!answer.getResult()) {
            return false;
        }
        if (answer instanceof com.cloud.agent.api.FtctlDrStatusAnswer) {
            com.cloud.agent.api.FtctlDrStatusAnswer status = (com.cloud.agent.api.FtctlDrStatusAnswer) answer;
            return StringUtils.isNotBlank(status.getState())
                    && !StringUtils.equalsAnyIgnoreCase(status.getState(), "NOT_FOUND", "UNKNOWN");
        }
        if (answer instanceof com.cloud.agent.api.FtctlDrCancelAnswer) {
            return Boolean.TRUE.equals(((com.cloud.agent.api.FtctlDrCancelAnswer) answer).getAccepted());
        }
        return true;
    }

    private List<HostVO> eligibleWorkers() {
        List<HostVO> result = new ArrayList<HostVO>();
        List<DataCenterVO> zones = dataCenterDao != null ? dataCenterDao.listEnabledZones() : null;
        if (zones != null) {
            for (DataCenterVO zone : zones) {
                List<HostVO> hosts = hostDao.listAllHostsUpByZoneAndHypervisor(zone.getId(),
                        Hypervisor.HypervisorType.KVM);
                if (hosts != null) {
                    for (HostVO host : hosts) {
                        if (host != null && host.getRemoved() == null && Status.Up.equals(host.getStatus())) {
                            result.add(host);
                        }
                    }
                }
            }
        }
        result.sort(Comparator.comparingLong(HostVO::getId));
        return result;
    }

    private void preferCurrentVmHost(Command command, List<HostVO> candidates) {
        if (!(command instanceof FtctlDrActionCommand) || userVmDao == null || candidates.isEmpty()) {
            return;
        }
        String sourceVmUuid = sourceVmUuid((FtctlDrActionCommand) command);
        UserVmVO vm = StringUtils.isNotBlank(sourceVmUuid) ? userVmDao.findByUuid(sourceVmUuid) : null;
        Long hostId = vm != null ? vm.getHostId() : null;
        if (hostId == null) {
            return;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).getId() == hostId.longValue()) {
                HostVO current = candidates.remove(index);
                candidates.add(0, current);
                return;
            }
        }
    }

    private String sourceVmUuid(FtctlDrActionCommand command) {
        String contextSourceVmUuid = command.getContext() != null
                ? StringUtils.trimToNull(command.getContext().get("sourceVmUuid")) : null;
        if (contextSourceVmUuid != null) {
            return contextSourceVmUuid;
        }
        if (StringUtils.isBlank(command.getProfileJson())) {
            return null;
        }
        try {
            JsonObject profile = GSON.fromJson(command.getProfileJson(), JsonObject.class);
            JsonObject source = objectAt(profile, "source");
            JsonObject vm = objectAt(source, "vm");
            String sourceRef = firstString(source, "externalRef", "vmUuid", "sourceVmUuid", "uuid");
            return StringUtils.defaultIfBlank(sourceRef, firstString(vm, "uuid", "id", "externalRef"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonObject objectAt(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private String firstString(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                String text = StringUtils.trimToNull(value.getAsString());
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static class DispatchResult {
        private final HostVO host;
        private final Answer answer;

        DispatchResult(HostVO host, Answer answer) {
            this.host = host;
            this.answer = answer;
        }
    }

    private Command deserialize(String commandType, String commandJson) {
        if (StringUtils.isBlank(commandJson)) {
            throw new CloudRuntimeException("DR site Agent command JSON is required");
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
                throw new CloudRuntimeException("Unsupported DR site Agent command type: " + commandType);
        }
    }
}
