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
@Table(name = "dr_run")
public class DrRunVO implements InternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "plan_id")
    private long planId;

    @Column(name = "run_type")
    private String runType;

    @Column(name = "state")
    private String state;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_json", length = 65535)
    private String requestJson;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "async_job_id")
    private Long asyncJobId;

    @Column(name = "external_job_ref")
    private String externalJobRef;

    @Column(name = "current_step_name")
    private String currentStepName;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", length = 4096)
    private String errorMessage;

    @Column(name = "started")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date started;

    @Column(name = "completed")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date completed;

    @Column(name = "created")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date created = new Date();

    @Column(name = "updated")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date updated;

    @Column(name = "removed")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date removed;

    protected DrRunVO() {
    }

    public DrRunVO(long planId, String runType) {
        this.planId = planId;
        this.runType = runType;
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

    public String getRunType() {
        return runType;
    }

    public String getState() {
        return state;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public Long getRequestedByUserId() {
        return requestedByUserId;
    }

    public Long getAsyncJobId() {
        return asyncJobId;
    }

    public String getExternalJobRef() {
        return externalJobRef;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Date getStarted() {
        return started;
    }

    public Date getCompleted() {
        return completed;
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

    public void setState(String state) {
        this.state = state;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public void setRequestedByUserId(Long requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public void setAsyncJobId(Long asyncJobId) {
        this.asyncJobId = asyncJobId;
    }

    public void setExternalJobRef(String externalJobRef) {
        this.externalJobRef = externalJobRef;
    }

    public void setCurrentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setStarted(Date started) {
        this.started = started;
    }

    public void setCompleted(Date completed) {
        this.completed = completed;
    }

    public void markUpdated() {
        updated = new Date();
    }

    public void markRemoved() {
        removed = new Date();
        markUpdated();
    }
}
