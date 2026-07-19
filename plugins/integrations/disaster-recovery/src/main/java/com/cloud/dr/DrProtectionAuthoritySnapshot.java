// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;

public class DrProtectionAuthoritySnapshot {
    private final DrPlanRuntimeVO runtime;
    private final boolean normalCutoverReady;

    public DrProtectionAuthoritySnapshot(DrPlanRuntimeVO runtime, boolean normalCutoverReady) {
        this.runtime = runtime;
        this.normalCutoverReady = normalCutoverReady;
    }

    public DrPlanRuntimeVO getRuntime() { return runtime; }
    public String getProtectionState() { return runtime != null ? runtime.getProtectionState() : "UNKNOWN"; }
    public String getFreshnessState() { return runtime != null ? runtime.getFreshnessState() : "UNKNOWN"; }
    public String getSchedulerState() { return runtime != null ? runtime.getSchedulerState() : null; }
    public boolean isSchedulerPidAlive() { return runtime != null && runtime.isSchedulerPidAlive(); }
    public Long getRuntimeGeneration() { return runtime != null ? runtime.getRuntimeGeneration() : null; }
    public Long getCurrentCycleSequence() { return runtime != null ? runtime.getCurrentCycleSequence() : null; }
    public String getCurrentCycleState() { return runtime != null ? runtime.getCurrentCycleState() : null; }
    public String getCurrentCycleMode() { return runtime != null ? runtime.getCurrentCycleMode() : null; }
    public String getBaselineState() { return runtime != null ? runtime.getBaselineState() : null; }
    public String getReseedReason() { return runtime != null ? runtime.getReseedReason() : null; }
    public String getProjectionIntegrityState() { return runtime != null ? runtime.getProjectionIntegrityState() : null; }
    public String getProjectionIntegrityCode() { return runtime != null ? runtime.getProjectionIntegrityCode() : null; }
    public Long getProjectionIntegritySequence() { return runtime != null ? runtime.getProjectionIntegritySequence() : null; }
    public String getErrorCode() { return runtime != null ? runtime.getErrorCode() : null; }
    public String getErrorMessage() { return runtime != null ? runtime.getErrorMessage() : null; }
    public Date getLastStatusAt() { return runtime != null ? runtime.getLastStatusAt() : null; }
    public Date getLastTargetDurableAt() { return runtime != null ? runtime.getLastTargetDurableAt() : null; }
    public Long getRpoAgeSeconds() { return runtime != null ? runtime.getRpoAgeSeconds() : null; }
    public boolean isRpoOverdue() { return runtime == null || runtime.isRpoOverdue(); }
    public boolean isNormalCutoverReady() { return normalCutoverReady; }
}
