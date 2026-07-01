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
package com.cloud.dr;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.apache.cloudstack.api.InternalIdentity;

@Entity
@Table(name = "dr_restore_point")
public class DrRestorePointVO implements InternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "plan_id")
    private long planId;

    @Column(name = "source_snapshot_ref")
    private String sourceSnapshotRef;

    @Column(name = "source_created")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date sourceCreated;

    @Column(name = "target_ready_at")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date targetReadyAt;

    @Column(name = "source_rpo_seconds")
    private Integer sourceRpoSeconds;

    @Column(name = "target_ready_rpo_seconds")
    private Integer targetReadyRpoSeconds;

    @Column(name = "consistency_level")
    private String consistencyLevel;

    @Column(name = "restore_point_type")
    private String restorePointType;

    @Column(name = "state")
    private String state;

    @Column(name = "created")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date created = new Date();

    @Column(name = "updated")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date updated;

    @Column(name = "removed")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date removed;

    protected DrRestorePointVO() {
    }

    public DrRestorePointVO(long planId, String restorePointType) {
        this.planId = planId;
        this.restorePointType = restorePointType;
    }

    @Override
    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public long getPlanId() {
        return planId;
    }

    public String getSourceSnapshotRef() {
        return sourceSnapshotRef;
    }

    public Date getSourceCreated() {
        return sourceCreated;
    }

    public Date getTargetReadyAt() {
        return targetReadyAt;
    }

    public Integer getSourceRpoSeconds() {
        return sourceRpoSeconds;
    }

    public Integer getTargetReadyRpoSeconds() {
        return targetReadyRpoSeconds;
    }

    public String getConsistencyLevel() {
        return consistencyLevel;
    }

    public String getRestorePointType() {
        return restorePointType;
    }

    public String getState() {
        return state;
    }

    public Date getCreated() {
        return created;
    }

    public Date getUpdated() {
        return updated;
    }

    public Date getRemoved() {
        return removed;
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

    public void setState(String state) {
        this.state = state;
    }

    public void markUpdated() {
        updated = new Date();
    }

    public void markRemoved() {
        removed = new Date();
        markUpdated();
    }
}
