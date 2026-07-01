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
package org.apache.cloudstack.api.response.dr;

import java.util.Date;

import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.dr.DrRestorePointVO;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = DrRestorePointVO.class)
public class DrRestorePointResponse extends BaseResponse {
    @SerializedName("id")
    @Param(description = "the DR restore point ID")
    private String id;

    @SerializedName("planid")
    @Param(description = "the DR plan ID")
    private Long planId;

    @SerializedName("sourcesnapshotref")
    @Param(description = "the source snapshot reference")
    private String sourceSnapshotRef;

    @SerializedName("sourcecreated")
    @Param(description = "the source creation time")
    private Date sourceCreated;

    @SerializedName("targetreadyat")
    @Param(description = "the target-ready time")
    private Date targetReadyAt;

    @SerializedName("sourcerposeconds")
    @Param(description = "the source RPO in seconds")
    private Integer sourceRpoSeconds;

    @SerializedName("targetreadyrposeconds")
    @Param(description = "the target-ready RPO in seconds")
    private Integer targetReadyRpoSeconds;

    @SerializedName("consistencylevel")
    @Param(description = "the consistency level")
    private String consistencyLevel;

    @SerializedName("restorepointtype")
    @Param(description = "the restore point type")
    private String restorePointType;

    @SerializedName("state")
    @Param(description = "the restore point state")
    private String state;

    @SerializedName("created")
    @Param(description = "the creation time")
    private Date created;

    public void setId(String id) {
        this.id = id;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public void setSourceSnapshotRef(String sourceSnapshotRef) {
        this.sourceSnapshotRef = sourceSnapshotRef;
    }

    public void setSourceCreated(Date sourceCreated) {
        this.sourceCreated = sourceCreated;
    }

    public void setTargetReadyAt(Date targetReadyAt) {
        this.targetReadyAt = targetReadyAt;
    }

    public void setSourceRpoSeconds(Integer sourceRpoSeconds) {
        this.sourceRpoSeconds = sourceRpoSeconds;
    }

    public void setTargetReadyRpoSeconds(Integer targetReadyRpoSeconds) {
        this.targetReadyRpoSeconds = targetReadyRpoSeconds;
    }

    public void setConsistencyLevel(String consistencyLevel) {
        this.consistencyLevel = consistencyLevel;
    }

    public void setRestorePointType(String restorePointType) {
        this.restorePointType = restorePointType;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}
