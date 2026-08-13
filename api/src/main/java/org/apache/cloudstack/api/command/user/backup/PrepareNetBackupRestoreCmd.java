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

package org.apache.cloudstack.api.command.user.backup;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.NetBackupRestorePrecheckResponse;
import org.apache.cloudstack.backup.BackupManager;
import org.apache.cloudstack.backup.NetBackupRestorePrecheckResult;
import org.apache.cloudstack.context.CallContext;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;

@APICommand(name = "prepareNetBackupRestore",
        description = "Claims a NetBackup restore session and returns whether the restore should proceed",
        responseObject = NetBackupRestorePrecheckResponse.class, since = "4.22.0",
        authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})
public class PrepareNetBackupRestoreCmd extends BaseCmd {

    @Inject
    private BackupManager backupManager;

    @Parameter(name = ApiConstants.EXTERNAL_ID,
            type = CommandType.STRING,
            required = false,
            description = "NetBackup backup external ID")
    private String externalId;

    @Parameter(name = ApiConstants.BACKUP_ID,
            type = CommandType.STRING,
            required = false,
            description = "NetBackup backup ID")
    private String backupId;

    @Parameter(name = ApiConstants.JOB_ID,
            type = CommandType.STRING,
            required = false,
            description = "NetBackup restore job ID that triggered this restore notification")
    private String jobId;

    public String getExternalId() {
        return externalId;
    }

    public String getBackupId() {
        return backupId;
    }

    public String getJobId() {
        return jobId;
    }

    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException, NetworkRuleConflictException {
        try {
            final NetBackupRestorePrecheckResult result = backupManager.prepareNetBackupRestore(this);
            final NetBackupRestorePrecheckResponse response = new NetBackupRestorePrecheckResponse();
            response.setResponseName(getCommandName());
            response.setShouldRestore(result.shouldRestore());
            response.setSkipReason(result.getSkipReason());
            response.setVmId(result.getVmId());
            response.setVmName(result.getVmName());
            response.setBackupId(result.getBackupId());
            response.setBackupUuid(result.getBackupUuid());
            response.setRequestIdentifier(result.getRequestIdentifier());
            response.setExternalId(result.getExternalId());
            setResponseObject(response);
        } catch (Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public long getEntityOwnerId() {
        return CallContext.current().getCallingAccount().getId();
    }
}
