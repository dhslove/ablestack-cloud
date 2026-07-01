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

public class FtctlDrStatusCommand extends Command {

    private String planUuid;
    private String runUuid;
    private Long eventsOffset;

    public FtctlDrStatusCommand() {
    }

    public FtctlDrStatusCommand(String planUuid, String runUuid) {
        this.planUuid = planUuid;
        this.runUuid = runUuid;
    }

    public String getPlanUuid() {
        return planUuid;
    }

    public String getRunUuid() {
        return runUuid;
    }

    public Long getEventsOffset() {
        return eventsOffset;
    }

    public void setEventsOffset(Long eventsOffset) {
        this.eventsOffset = eventsOffset;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }
}
