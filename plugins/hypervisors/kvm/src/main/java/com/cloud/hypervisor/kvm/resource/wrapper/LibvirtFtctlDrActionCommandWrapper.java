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

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.utils.script.OutputInterpreter;
import com.cloud.utils.script.Script;
import com.google.gson.JsonObject;

@ResourceWrapper(handles = FtctlDrActionCommand.class)
public class LibvirtFtctlDrActionCommandWrapper extends CommandWrapper<FtctlDrActionCommand, Answer, LibvirtComputingResource> {

    private static final int DEFAULT_TIMEOUT_SECONDS = 45;

    @Override
    public Answer execute(FtctlDrActionCommand command, LibvirtComputingResource serverResource) {
        if (command.getAction() == null) {
            return new FtctlDrActionAnswer(command, false, "Missing FTCTL_DR action");
        }
        if (StringUtils.isBlank(command.getPlanUuid())) {
            return new FtctlDrActionAnswer(command, false, "Missing DR plan UUID");
        }
        if (StringUtils.isBlank(command.getRunUuid())) {
            return new FtctlDrActionAnswer(command, false, "Missing DR run UUID");
        }

        File profileFile = null;
        try {
            profileFile = LibvirtFtctlDrCommandHelper.writeProfileJson(command.getPlanUuid(), command.getProfileJson());
            return executeFtctl(command, profileFile);
        } catch (IOException e) {
            return new FtctlDrActionAnswer(command, false, "Unable to write temporary FTCTL_DR profile: " + e.getMessage());
        } finally {
            LibvirtFtctlDrCommandHelper.deleteQuietly(profileFile);
        }
    }

    private FtctlDrActionAnswer executeFtctl(FtctlDrActionCommand command, File profileFile) {
        final long timeout = (long) (command.getWait() > 0 ? command.getWait() : DEFAULT_TIMEOUT_SECONDS) * 1000;
        Script script = new Script("ablestack_vm_ftctl", timeout, logger);
        script.add(command.getAction().getCliCommand());
        LibvirtFtctlDrCommandHelper.addPlanRunArgs(script, command.getPlanUuid(), command.getRunUuid());
        LibvirtFtctlDrCommandHelper.addProfileJsonArg(script, profileFile);
        if (StringUtils.isNotBlank(command.getRole())) {
            script.add("--role");
            script.add(command.getRole());
        }
        if (StringUtils.isNotBlank(command.getMode())) {
            script.add("--mode");
            script.add(command.getMode());
        }
        String restorePointSelector = resolveRestorePointSelector(command);
        if (StringUtils.isNotBlank(restorePointSelector)) {
            script.add("--restore-point");
            script.add(restorePointSelector);
        }
        if (command.isForce()) {
            script.add("--force");
        }
        if (command.isDryRun()) {
            script.add("--dry-run");
        }
        if (!command.isWaitForCompletion()) {
            script.add("--wait=false");
        }
        script.add("--json");

        OutputInterpreter.AllLinesParser parser = new OutputInterpreter.AllLinesParser();
        String result = script.execute(parser);
        String output = LibvirtFtctlWrapperHelper.getOutput(result, parser);
        JsonObject payload = LibvirtFtctlWrapperHelper.parseJsonObject(output);
        int exitValue = script.getExitValue();
        boolean success = exitValue == 0;

        return new FtctlDrActionAnswer(command, success,
                StringUtils.defaultIfBlank(output, success ? "OK" : "FTCTL_DR action failed"),
                command.getAction(), command.getPlanUuid(), command.getRunUuid(),
                LibvirtFtctlDrCommandHelper.getString(payload, "result"),
                LibvirtFtctlDrCommandHelper.getBoolean(payload, "accepted"),
                LibvirtFtctlDrCommandHelper.getString(payload, "state"),
                LibvirtFtctlDrCommandHelper.getString(payload, "step"),
                LibvirtFtctlDrCommandHelper.getInteger(payload, "progress"),
                LibvirtFtctlDrCommandHelper.getString(payload, "external_job_ref"),
                LibvirtFtctlDrCommandHelper.getLong(payload, "events_offset"),
                LibvirtFtctlDrCommandHelper.getString(payload, "error_code"),
                exitValue, output, payload != null ? payload.toString() : null);
    }

    private String resolveRestorePointSelector(FtctlDrActionCommand command) {
        JsonObject request = LibvirtFtctlWrapperHelper.parseJsonObject(command.getRequestJson());
        String restorePointRef = LibvirtFtctlWrapperHelper.getString(request, "restorePointRef");
        if (StringUtils.isNotBlank(restorePointRef)) {
            return restorePointRef;
        }
        if (command.getContext() != null) {
            restorePointRef = command.getContext().get("restorePointRef");
            if (StringUtils.isNotBlank(restorePointRef)) {
                return restorePointRef;
            }
        }
        return command.getRestorePointId() != null ? String.valueOf(command.getRestorePointId()) : null;
    }
}
