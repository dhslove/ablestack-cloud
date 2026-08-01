// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package org.apache.cloudstack.api.response.dr;

import java.util.Date;

import org.apache.cloudstack.api.BaseResponse;

import com.cloud.dr.DrFailbackPreflightResult;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.DrSourceIsolationPreflightResult;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class DrFailbackPreflightResponse extends BaseResponse {
    @SerializedName("ready") @Param(description = "whether failback can be started")
    private boolean ready;
    @SerializedName("errorcode") @Param(description = "typed preflight error code")
    private String errorCode;
    @SerializedName("message") @Param(description = "preflight result message")
    private String message;
    @SerializedName("activesiteid") @Param(description = "active target site ID")
    private String activeSiteId;
    @SerializedName("activesitename") @Param(description = "active target site name")
    private String activeSiteName;
    @SerializedName("activesitetype") @Param(description = "active target site type")
    private String activeSiteType;
    @SerializedName("activesitehealth") @Param(description = "active target site health")
    private String activeSiteHealth;
    @SerializedName("activecredentialstate") @Param(description = "active site credential state")
    private String activeCredentialState;
    @SerializedName("destinationsiteid") @Param(description = "registered source site ID")
    private String destinationSiteId;
    @SerializedName("destinationsitename") @Param(description = "registered source site name")
    private String destinationSiteName;
    @SerializedName("destinationsitetype") @Param(description = "registered source site type")
    private String destinationSiteType;
    @SerializedName("destinationsitehealth") @Param(description = "registered source site health")
    private String destinationSiteHealth;
    @SerializedName("destinationcredentialstate") @Param(description = "destination site credential state")
    private String destinationCredentialState;
    @SerializedName("checkpointid") @Param(description = "latest durable checkpoint ID")
    private String checkpointId;
    @SerializedName("checkpointsequence") @Param(description = "latest durable checkpoint sequence")
    private Long checkpointSequence;
    @SerializedName("checkpointreadyat") @Param(description = "latest durable checkpoint time")
    private Date checkpointReadyAt;
    @SerializedName("authoritygeneration") @Param(description = "committed target authority generation")
    private Long authorityGeneration;
    @SerializedName("sourcefencestate") @Param(description = "source isolation or fence evidence")
    private String sourceFenceState;
    @SerializedName("sourcepowerstate") @Param(description = "source VM power evidence")
    private String sourcePowerState;
    @SerializedName("targetpowerstate") @Param(description = "serving target VM power evidence")
    private String targetPowerState;
    @SerializedName("enginepreflightready") @Param(description = "whether FTCTL transition preflight passed")
    private Boolean enginePreflightReady;

    public static DrFailbackPreflightResponse from(DrFailbackPreflightResult result) {
        DrFailbackPreflightResponse response = new DrFailbackPreflightResponse();
        response.setObjectName("drfailbackpreflight");
        response.ready = result.isReady();
        response.errorCode = result.getErrorCode();
        response.message = result.getMessage();
        response.activeCredentialState = result.getActiveCredentialState();
        response.destinationCredentialState = result.getDestinationCredentialState();
        response.copyActiveSite(result.getActiveSite());
        response.copyDestinationSite(result.getDestinationSite());
        DrRestorePointVO checkpoint = result.getCheckpoint();
        if (checkpoint != null) {
            response.checkpointId = checkpoint.getUuid();
            response.checkpointSequence = checkpoint.getCheckpointSequence();
            response.checkpointReadyAt = checkpoint.getTargetReadyAt();
        }
        DrSourceIsolationPreflightResult transition = result.getTransitionPreflight();
        if (transition != null) {
            response.authorityGeneration = transition.getAuthorityGeneration();
            response.sourceFenceState = transition.getSourceFenceState();
            response.sourcePowerState = transition.getSourcePowerState();
            response.targetPowerState = transition.getTargetPowerState();
            response.enginePreflightReady = transition.isReady();
        }
        return response;
    }

    private void copyActiveSite(DrSiteVO site) {
        if (site == null) {
            return;
        }
        activeSiteId = site.getUuid();
        activeSiteName = site.getName();
        activeSiteType = site.getSiteType();
        activeSiteHealth = site.getHealthState();
    }

    private void copyDestinationSite(DrSiteVO site) {
        if (site == null) {
            return;
        }
        destinationSiteId = site.getUuid();
        destinationSiteName = site.getName();
        destinationSiteType = site.getSiteType();
        destinationSiteHealth = site.getHealthState();
    }
}
