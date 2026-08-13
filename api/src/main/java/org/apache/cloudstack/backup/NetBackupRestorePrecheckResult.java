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

package org.apache.cloudstack.backup;

public class NetBackupRestorePrecheckResult {

    private final boolean shouldRestore;
    private final String skipReason;
    private final Long vmId;
    private final String vmName;
    private final Long backupId;
    private final String backupUuid;
    private final String requestIdentifier;
    private final String externalId;

    public NetBackupRestorePrecheckResult(final boolean shouldRestore, final String skipReason, final Long vmId, final String vmName,
                                          final Long backupId, final String backupUuid, final String requestIdentifier, final String externalId) {
        this.shouldRestore = shouldRestore;
        this.skipReason = skipReason;
        this.vmId = vmId;
        this.vmName = vmName;
        this.backupId = backupId;
        this.backupUuid = backupUuid;
        this.requestIdentifier = requestIdentifier;
        this.externalId = externalId;
    }

    public boolean shouldRestore() {
        return shouldRestore;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public Long getVmId() {
        return vmId;
    }

    public String getVmName() {
        return vmName;
    }

    public Long getBackupId() {
        return backupId;
    }

    public String getBackupUuid() {
        return backupUuid;
    }

    public String getRequestIdentifier() {
        return requestIdentifier;
    }

    public String getExternalId() {
        return externalId;
    }
}
