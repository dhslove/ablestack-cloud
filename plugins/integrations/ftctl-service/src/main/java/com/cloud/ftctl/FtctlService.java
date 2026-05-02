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

import com.cloud.agent.api.FtctlActionCommand;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlCheckCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlEventsCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlHealthCmd;
import org.apache.cloudstack.api.command.admin.ftctl.GetFtctlProtectionCmd;
import org.apache.cloudstack.api.command.admin.ftctl.RegisterFtctlProtectionCmd;
import org.apache.cloudstack.api.response.ftctl.FtctlActionResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlCheckResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlEventsResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlHealthResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlProtectionResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

public interface FtctlService extends PluggableService, Configurable {

    ConfigKey<Boolean> FtctlServiceEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "cloud.ftctl.service.enabled",
            "true",
            "Indicates whether FTCTL integration service plugin is enabled or not.",
            false);

    FtctlProtectionResponse getFtctlProtection(GetFtctlProtectionCmd cmd) throws CloudRuntimeException;

    FtctlProtectionResponse registerFtctlProtection(RegisterFtctlProtectionCmd cmd) throws CloudRuntimeException;

    FtctlCheckResponse getFtctlCheck(GetFtctlCheckCmd cmd) throws CloudRuntimeException;

    FtctlEventsResponse getFtctlEvents(GetFtctlEventsCmd cmd) throws CloudRuntimeException;

    FtctlHealthResponse getFtctlHealth(GetFtctlHealthCmd cmd) throws CloudRuntimeException;

    FtctlActionResponse executeFtctlAction(Long virtualMachineId, FtctlActionCommand.Action action, boolean force) throws CloudRuntimeException;

    FtctlActionResponse confirmFtctlFence(Long virtualMachineId) throws CloudRuntimeException;
}
