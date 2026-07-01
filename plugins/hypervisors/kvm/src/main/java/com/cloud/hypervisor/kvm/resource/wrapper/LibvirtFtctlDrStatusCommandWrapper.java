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

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.gson.JsonObject;

@ResourceWrapper(handles = FtctlDrStatusCommand.class)
public class LibvirtFtctlDrStatusCommandWrapper extends CommandWrapper<FtctlDrStatusCommand, Answer, LibvirtComputingResource> {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Override
    public Answer execute(FtctlDrStatusCommand command, LibvirtComputingResource serverResource) {
        if (StringUtils.isBlank(command.getPlanUuid())) {
            return new FtctlDrStatusAnswer(command, false, "Missing DR plan UUID");
        }

        final long timeout = (long) (command.getWait() > 0 ? command.getWait() : DEFAULT_TIMEOUT_SECONDS) * 1000;
        Script script = new Script("ablestack_vm_ftctl", timeout, logger);
        script.add("dr-status");
        LibvirtFtctlDrCommandHelper.addPlanRunArgs(script, command.getPlanUuid(), command.getRunUuid());
        if (command.getEventsOffset() != null) {
            script.add("--events-offset");
            script.add(String.valueOf(command.getEventsOffset()));
        }
        script.add("--json");

        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
        String result = script.execute(parser);
        String output = LibvirtFtctlWrapperHelper.getOutput(result, parser);
        JsonObject payload = LibvirtFtctlWrapperHelper.parseJsonObject(output);
        int exitValue = script.getExitValue();
        boolean success = exitValue == 0;

        return new FtctlDrStatusAnswer(command, success,
                StringUtils.defaultIfBlank(output, success ? "OK" : "FTCTL_DR status failed"),
                command.getPlanUuid(), command.getRunUuid(),
                LibvirtFtctlDrCommandHelper.getString(payload, "result"),
                LibvirtFtctlDrCommandHelper.getString(payload, "state"),
                LibvirtFtctlDrCommandHelper.getString(payload, "step"),
                LibvirtFtctlDrCommandHelper.getInteger(payload, "progress"),
                LibvirtFtctlDrCommandHelper.getString(payload, "last_source_checkpoint_at"),
                LibvirtFtctlDrCommandHelper.getString(payload, "last_target_durable_at"),
                LibvirtFtctlDrCommandHelper.getInteger(payload, "target_ready_rpo_seconds"),
                LibvirtFtctlDrCommandHelper.getLong(payload, "events_offset"),
                LibvirtFtctlDrCommandHelper.getString(payload, "error_code"),
                exitValue, output, payload != null ? payload.toString() : null);
    }
}
