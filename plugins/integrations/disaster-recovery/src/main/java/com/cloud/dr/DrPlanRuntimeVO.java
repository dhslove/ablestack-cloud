// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;

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
@Table(name = "dr_plan_runtime")
public class DrPlanRuntimeVO implements InternalIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "plan_id")
    private long planId;
    @Column(name = "engine_run_uuid")
    private String engineRunUuid;
    @Column(name = "runtime_generation")
    private long runtimeGeneration;
    @Column(name = "scheduler_state")
    private String schedulerState;
    @Column(name = "scheduler_pid_alive")
    private boolean schedulerPidAlive;
    @Column(name = "worker_state")
    private String workerState;
    @Column(name = "current_cycle_sequence")
    private Long currentCycleSequence;
    @Column(name = "current_cycle_state")
    private String currentCycleState;
    @Column(name = "current_cycle_mode")
    private String currentCycleMode;
    @Column(name = "baseline_state")
    private String baselineState;
    @Column(name = "reseed_reason")
    private String reseedReason;
    @Column(name = "last_mode_decision_code")
    private String lastModeDecisionCode;
    @Column(name = "consecutive_automatic_reseed_count")
    private int consecutiveAutomaticReseedCount;
    @Column(name = "latest_completed_cycle_sequence")
    private Long latestCompletedCycleSequence;
    @Column(name = "latest_completed_incremental_verified")
    private Boolean latestCompletedIncrementalVerified;
    @Column(name = "projection_integrity_state")
    private String projectionIntegrityState;
    @Column(name = "projection_integrity_code")
    private String projectionIntegrityCode;
    @Column(name = "projection_integrity_sequence")
    private Long projectionIntegritySequence;
    @Column(name = "protection_state")
    private String protectionState;
    @Column(name = "freshness_state")
    private String freshnessState;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_status_at")
    private Date lastStatusAt;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_source_checkpoint_at")
    private Date lastSourceCheckpointAt;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_target_durable_at")
    private Date lastTargetDurableAt;
    @Column(name = "rpo_age_seconds")
    private Long rpoAgeSeconds;
    @Column(name = "rpo_overdue")
    private boolean rpoOverdue;
    @Column(name = "error_code")
    private String errorCode;
    @Column(name = "error_message", length = 4096)
    private String errorMessage;
    @Column(name = "status_json", length = 16777215, columnDefinition = "MEDIUMTEXT")
    private String statusJson;
    @Temporal(TemporalType.TIMESTAMP)
    private Date created = new Date();
    @Temporal(TemporalType.TIMESTAMP)
    private Date updated;

    protected DrPlanRuntimeVO() {
    }

    public DrPlanRuntimeVO(long planId) {
        this.planId = planId;
    }

    @Override
    public long getId() { return id; }
    public long getPlanId() { return planId; }
    public String getEngineRunUuid() { return engineRunUuid; }
    public long getRuntimeGeneration() { return runtimeGeneration; }
    public String getSchedulerState() { return schedulerState; }
    public boolean isSchedulerPidAlive() { return schedulerPidAlive; }
    public String getWorkerState() { return workerState; }
    public Long getCurrentCycleSequence() { return currentCycleSequence; }
    public String getCurrentCycleState() { return currentCycleState; }
    public String getCurrentCycleMode() { return currentCycleMode; }
    public String getBaselineState() { return baselineState; }
    public String getReseedReason() { return reseedReason; }
    public String getLastModeDecisionCode() { return lastModeDecisionCode; }
    public int getConsecutiveAutomaticReseedCount() { return consecutiveAutomaticReseedCount; }
    public Long getLatestCompletedCycleSequence() { return latestCompletedCycleSequence; }
    public Boolean getLatestCompletedIncrementalVerified() { return latestCompletedIncrementalVerified; }
    public String getProjectionIntegrityState() { return projectionIntegrityState; }
    public String getProjectionIntegrityCode() { return projectionIntegrityCode; }
    public Long getProjectionIntegritySequence() { return projectionIntegritySequence; }
    public String getProtectionState() { return protectionState; }
    public String getFreshnessState() { return freshnessState; }
    public Date getLastStatusAt() { return lastStatusAt; }
    public Date getLastSourceCheckpointAt() { return lastSourceCheckpointAt; }
    public Date getLastTargetDurableAt() { return lastTargetDurableAt; }
    public Long getRpoAgeSeconds() { return rpoAgeSeconds; }
    public boolean isRpoOverdue() { return rpoOverdue; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getStatusJson() { return statusJson; }
    public Date getCreated() { return created; }
    public Date getUpdated() { return updated; }

    public void setEngineRunUuid(String value) { engineRunUuid = value; }
    public void setRuntimeGeneration(long value) { runtimeGeneration = value; }
    public void setSchedulerState(String value) { schedulerState = value; }
    public void setSchedulerPidAlive(boolean value) { schedulerPidAlive = value; }
    public void setWorkerState(String value) { workerState = value; }
    public void setCurrentCycleSequence(Long value) { currentCycleSequence = value; }
    public void setCurrentCycleState(String value) { currentCycleState = value; }
    public void setCurrentCycleMode(String value) { currentCycleMode = value; }
    public void setBaselineState(String value) { baselineState = value; }
    public void setReseedReason(String value) { reseedReason = value; }
    public void setLastModeDecisionCode(String value) { lastModeDecisionCode = value; }
    public void setConsecutiveAutomaticReseedCount(int value) { consecutiveAutomaticReseedCount = value; }
    public void setLatestCompletedCycleSequence(Long value) { latestCompletedCycleSequence = value; }
    public void setLatestCompletedIncrementalVerified(Boolean value) { latestCompletedIncrementalVerified = value; }
    public void setProjectionIntegrityState(String value) { projectionIntegrityState = value; }
    public void setProjectionIntegrityCode(String value) { projectionIntegrityCode = value; }
    public void setProjectionIntegritySequence(Long value) { projectionIntegritySequence = value; }
    public void setProtectionState(String value) { protectionState = value; }
    public void setFreshnessState(String value) { freshnessState = value; }
    public void setLastStatusAt(Date value) { lastStatusAt = value; }
    public void setLastSourceCheckpointAt(Date value) { lastSourceCheckpointAt = value; }
    public void setLastTargetDurableAt(Date value) { lastTargetDurableAt = value; }
    public void setRpoAgeSeconds(Long value) { rpoAgeSeconds = value; }
    public void setRpoOverdue(boolean value) { rpoOverdue = value; }
    public void setErrorCode(String value) { errorCode = value; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public void setStatusJson(String value) { statusJson = value; }
    public void markUpdated() { updated = new Date(); }
}
