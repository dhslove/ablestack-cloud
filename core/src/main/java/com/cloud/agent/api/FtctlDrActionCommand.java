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
package com.cloud.agent.api;

import java.util.HashMap;
import java.util.Map;

public class FtctlDrActionCommand extends Command {

    public enum Action {
        SYNC("dr-sync-start"),
        PAUSE_SYNC("dr-sync-pause"),
        RESUME_SYNC("dr-sync-resume"),
        TEST_FAILOVER("dr-test-failover"),
        TEST_CLEANUP("dr-test-cleanup"),
        FAILOVER("dr-failover"),
        FAILBACK("dr-failback"),
        REPROTECT("dr-reprotect"),
        RELEASE("dr-release");

        private final String cliCommand;

        Action(String cliCommand) {
            this.cliCommand = cliCommand;
        }

        public String getCliCommand() {
            return cliCommand;
        }
    }

    private Action action;
    private String planUuid;
    private String runUuid;
    private String runType;
    private String direction;
    private String role;
    private String sourceWorkerUuid;
    private String targetWorkerUuid;
    private String coordinatorWorkerUuid;
    private String profileJson;
    private String requestJson;
    private String mode;
    private Long restorePointId;
    private boolean force;
    private boolean dryRun;
    private boolean waitForCompletion;
    private Map<String, String> context = new HashMap<>();

    public FtctlDrActionCommand() {
    }

    public FtctlDrActionCommand(Action action, String planUuid, String runUuid) {
        this.action = action;
        this.planUuid = planUuid;
        this.runUuid = runUuid;
    }

    public Action getAction() {
        return action;
    }

    public String getPlanUuid() {
        return planUuid;
    }

    public String getRunUuid() {
        return runUuid;
    }

    public String getRunType() {
        return runType;
    }

    public void setRunType(String runType) {
        this.runType = runType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSourceWorkerUuid() {
        return sourceWorkerUuid;
    }

    public void setSourceWorkerUuid(String sourceWorkerUuid) {
        this.sourceWorkerUuid = sourceWorkerUuid;
    }

    public String getTargetWorkerUuid() {
        return targetWorkerUuid;
    }

    public void setTargetWorkerUuid(String targetWorkerUuid) {
        this.targetWorkerUuid = targetWorkerUuid;
    }

    public String getCoordinatorWorkerUuid() {
        return coordinatorWorkerUuid;
    }

    public void setCoordinatorWorkerUuid(String coordinatorWorkerUuid) {
        this.coordinatorWorkerUuid = coordinatorWorkerUuid;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(String profileJson) {
        this.profileJson = profileJson;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Long getRestorePointId() {
        return restorePointId;
    }

    public void setRestorePointId(Long restorePointId) {
        this.restorePointId = restorePointId;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public void setWaitForCompletion(boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context == null ? new HashMap<>() : context;
    }

    public void setContextParam(String key, String value) {
        if (key != null && value != null) {
            context.put(key, value);
        }
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
