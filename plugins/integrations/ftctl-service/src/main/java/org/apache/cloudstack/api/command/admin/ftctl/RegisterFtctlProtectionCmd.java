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
package org.apache.cloudstack.api.command.admin.ftctl;

import com.cloud.ftctl.FtctlService;
import com.cloud.user.Account;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.HostResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.api.response.ftctl.FtctlProtectionResponse;

import javax.inject.Inject;

@APICommand(name = RegisterFtctlProtectionCmd.APINAME,
        description = "Registers FTCTL protection settings for a virtual machine",
        responseObject = FtctlProtectionResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        authorized = {RoleType.Admin})
public class RegisterFtctlProtectionCmd extends BaseCmd {
    public static final String APINAME = "registerFtctlProtection";

    @Inject
    private FtctlService ftctlService;

    @Parameter(name = "virtualmachineid", type = CommandType.UUID, entityType = UserVmResponse.class,
            required = true, description = "the virtual machine ID")
    private Long virtualMachineId;

    @Parameter(name = "mode", type = CommandType.STRING, required = true, description = "the FTCTL protection mode")
    private String mode;

    @Parameter(name = "backendmode", type = CommandType.STRING, description = "the FTCTL backend mode")
    private String backendMode;

    @Parameter(name = "targetstoragescope", type = CommandType.STRING, description = "the FTCTL target storage scope")
    private String targetStorageScope;

    @Parameter(name = "fencingpolicy", type = CommandType.STRING, description = "the FTCTL fencing policy")
    private String fencingPolicy;

    @Parameter(name = "peerhostid", type = CommandType.UUID, entityType = HostResponse.class,
            required = true,
            description = "the FTCTL peer host ID")
    private Long peerHostId;

    @Parameter(name = "secondaryvmname", type = CommandType.STRING, description = "the FTCTL secondary VM name")
    private String secondaryVmName;

    @Parameter(name = "secondarytargetdir", type = CommandType.STRING, description = "the FTCTL secondary target directory for remote-nbd")
    private String secondaryTargetDir;

    @Parameter(name = "remotenbdexportaddr", type = CommandType.STRING, description = "the FTCTL remote NBD export address")
    private String remoteNbdExportAddr;

    @Parameter(name = "xcoloproxyendpoint", type = CommandType.STRING, description = "the FTCTL x-colo proxy endpoint")
    private String xcoloProxyEndpoint;

    @Parameter(name = "xcolonbdendpoint", type = CommandType.STRING, description = "the FTCTL x-colo NBD endpoint")
    private String xcoloNbdEndpoint;

    @Parameter(name = "xcolomigrateuri", type = CommandType.STRING, description = "the FTCTL x-colo migrate URI")
    private String xcoloMigrateUri;

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public String getMode() {
        return mode;
    }

    public String getBackendMode() {
        return backendMode;
    }

    public String getTargetStorageScope() {
        return targetStorageScope;
    }

    public String getFencingPolicy() {
        return fencingPolicy;
    }

    public Long getPeerHostId() {
        return peerHostId;
    }

    public String getSecondaryVmName() {
        return secondaryVmName;
    }

    public String getSecondaryTargetDir() {
        return secondaryTargetDir;
    }

    public String getRemoteNbdExportAddr() {
        return remoteNbdExportAddr;
    }

    public String getXcoloProxyEndpoint() {
        return xcoloProxyEndpoint;
    }

    public String getXcoloNbdEndpoint() {
        return xcoloNbdEndpoint;
    }

    public String getXcoloMigrateUri() {
        return xcoloMigrateUri;
    }

    @Override
    public void execute() throws ServerApiException {
        FtctlProtectionResponse response = ftctlService.registerFtctlProtection(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
