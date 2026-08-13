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

package org.apache.cloudstack.api.response;

import org.apache.cloudstack.api.BaseResponse;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class NetBackupRestorePrecheckResponse extends BaseResponse {

    @SerializedName("shouldrestore")
    @Param(description = "Indicates whether the restore should proceed")
    private boolean shouldRestore;

    @SerializedName("skipreason")
    @Param(description = "Reason why the restore should be skipped", since = "4.22.0")
    private String skipReason;

    @SerializedName("vmid")
    @Param(description = "Resolved VM id", since = "4.22.0")
    private Long vmId;

    @SerializedName("vmname")
    @Param(description = "Resolved VM name", since = "4.22.0")
    private String vmName;

    @SerializedName("backupid")
    @Param(description = "Resolved backup database id", since = "4.22.0")
    private Long backupId;

    @SerializedName("backupuuid")
    @Param(description = "Resolved backup uuid", since = "4.22.0")
    private String backupUuid;

    @SerializedName("requestidentifier")
    @Param(description = "Resolved restore request identifier", since = "4.22.0")
    private String requestIdentifier;

    @SerializedName("externalid")
    @Param(description = "Resolved external id", since = "4.22.0")
    private String externalId;

    public boolean isShouldRestore() {
        return shouldRestore;
    }

    public void setShouldRestore(boolean shouldRestore) {
        this.shouldRestore = shouldRestore;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public Long getVmId() {
        return vmId;
    }

    public void setVmId(Long vmId) {
        this.vmId = vmId;
    }

    public String getVmName() {
        return vmName;
    }

    public void setVmName(String vmName) {
        this.vmName = vmName;
    }

    public Long getBackupId() {
        return backupId;
    }

    public void setBackupId(Long backupId) {
        this.backupId = backupId;
    }

    public String getBackupUuid() {
        return backupUuid;
    }

    public void setBackupUuid(String backupUuid) {
        this.backupUuid = backupUuid;
    }

    public String getRequestIdentifier() {
        return requestIdentifier;
    }

    public void setRequestIdentifier(String requestIdentifier) {
        this.requestIdentifier = requestIdentifier;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}
