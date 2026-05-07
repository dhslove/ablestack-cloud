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

import java.io.BufferedReader;
import java.io.IOException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlActionAnswer;
import com.cloud.agent.api.FtctlActionCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;

@ResourceWrapper(handles = FtctlActionCommand.class)
public class LibvirtFtctlActionCommandWrapper extends CommandWrapper<FtctlActionCommand, Answer, LibvirtComputingResource> {

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private static final class FtctlAllLinesParser extends OutputInterpreter.AllLinesParser {
        @Override
        public String processError(BufferedReader reader) throws IOException {
            String lines = getLines();
            if (StringUtils.isNotBlank(lines)) {
                return lines;
            }
            try {
                return super.processError(reader);
            } catch (IOException e) {
                return e.getMessage();
            }
        }
    }

    @Override
    public Answer execute(FtctlActionCommand command, LibvirtComputingResource serverResource) {
        if (command.getAction() == null) {
            return new FtctlActionAnswer(command, false, "Missing action for ftctl command");
        }
        if (StringUtils.isBlank(command.getVmName())) {
            return new FtctlActionAnswer(command, false, "Missing VM name for ftctl action command");
        }

        final long timeout = (long) (command.getWait() > 0 ? command.getWait() : DEFAULT_TIMEOUT_SECONDS) * 1000;
        Script script = new Script("ablestack_vm_ftctl", timeout, logger);
        script.add(command.getAction().getCliCommand());
        script.add("--vm", command.getVmName());
        if (StringUtils.isNotBlank(command.getMode())) {
            script.add("--mode", command.getMode());
        }
        if (StringUtils.isNotBlank(command.getPeerUri())) {
            script.add("--peer", command.getPeerUri());
        }
        if (StringUtils.isNotBlank(command.getProfileName())) {
            script.add("--profile", command.getProfileName());
        }
        if (command.isForce()) {
            script.add("--force");
        }
        script.add("--json");

        OutputInterpreter.AllLinesParser parser = new FtctlAllLinesParser();
        String result = script.execute(parser);
        String output = LibvirtFtctlWrapperHelper.getOutput(result, parser);
        JsonObject payload = LibvirtFtctlWrapperHelper.parseJsonObject(output);
        int exitValue = script.getExitValue();
        boolean success = exitValue == 0;

        return new FtctlActionAnswer(command, success,
                StringUtils.defaultIfBlank(output, success ? "OK" : "ftctl action failed"),
                command.getAction(),
                LibvirtFtctlWrapperHelper.getString(payload, "result"),
                exitValue,
                output);
    }
}
