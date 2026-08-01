// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import java.util.Date;

public class DrFailbackPreflightResult {
    private boolean ready;
    private String errorCode;
    private String message;
    private DrSiteVO activeSite;
    private DrSiteVO destinationSite;
    private String activeCredentialState;
    private String destinationCredentialState;
    private DrRestorePointVO checkpoint;
    private DrSourceIsolationPreflightResult transitionPreflight;

    public static DrFailbackPreflightResult success(DrSiteVO activeSite, DrSiteVO destinationSite,
            DrRestorePointVO checkpoint) {
        DrFailbackPreflightResult result = new DrFailbackPreflightResult();
        result.ready = true;
        result.activeSite = activeSite;
        result.destinationSite = destinationSite;
        result.activeCredentialState = DrConstants.CREDENTIAL_STATE_CONFIGURED;
        result.destinationCredentialState = DrConstants.CREDENTIAL_STATE_CONFIGURED;
        result.checkpoint = checkpoint;
        return result;
    }

    public static DrFailbackPreflightResult failure(String errorCode, String message,
            DrSiteVO activeSite, DrSiteVO destinationSite, String activeCredentialState,
            String destinationCredentialState, DrRestorePointVO checkpoint) {
        DrFailbackPreflightResult result = new DrFailbackPreflightResult();
        result.ready = false;
        result.errorCode = errorCode;
        result.message = message;
        result.activeSite = activeSite;
        result.destinationSite = destinationSite;
        result.activeCredentialState = activeCredentialState;
        result.destinationCredentialState = destinationCredentialState;
        result.checkpoint = checkpoint;
        return result;
    }

    public boolean isReady() { return ready; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public DrSiteVO getActiveSite() { return activeSite; }
    public DrSiteVO getDestinationSite() { return destinationSite; }
    public String getActiveCredentialState() { return activeCredentialState; }
    public String getDestinationCredentialState() { return destinationCredentialState; }
    public DrRestorePointVO getCheckpoint() { return checkpoint; }
    public Date getCheckpointReadyAt() { return checkpoint != null ? checkpoint.getTargetReadyAt() : null; }
    public DrSourceIsolationPreflightResult getTransitionPreflight() { return transitionPreflight; }

    public DrFailbackPreflightResult withTransitionPreflight(
            DrSourceIsolationPreflightResult transitionPreflight) {
        this.transitionPreflight = transitionPreflight;
        return this;
    }
}
