// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

public class DrSourceIsolationPreflightResult {
    private final boolean ready;
    private final String errorCode;
    private final String message;
    private final String operation;
    private final Long authorityGeneration;
    private final String sourceFenceState;
    private final String sourcePowerState;
    private final String targetPowerState;
    private final String engineEvidenceJson;

    private DrSourceIsolationPreflightResult(boolean ready, String errorCode, String message,
            String operation, Long authorityGeneration, String sourceFenceState,
            String sourcePowerState, String targetPowerState, String engineEvidenceJson) {
        this.ready = ready;
        this.errorCode = errorCode;
        this.message = message;
        this.operation = operation;
        this.authorityGeneration = authorityGeneration;
        this.sourceFenceState = sourceFenceState;
        this.sourcePowerState = sourcePowerState;
        this.targetPowerState = targetPowerState;
        this.engineEvidenceJson = engineEvidenceJson;
    }

    public static DrSourceIsolationPreflightResult success(String operation, Long authorityGeneration,
            String sourceFenceState, String sourcePowerState, String targetPowerState,
            String engineEvidenceJson) {
        return new DrSourceIsolationPreflightResult(true, null, null, operation, authorityGeneration,
                sourceFenceState, sourcePowerState, targetPowerState, engineEvidenceJson);
    }

    public static DrSourceIsolationPreflightResult failure(String errorCode, String message,
            String operation, Long authorityGeneration, String sourceFenceState,
            String sourcePowerState, String targetPowerState, String engineEvidenceJson) {
        return new DrSourceIsolationPreflightResult(false, errorCode, message, operation,
                authorityGeneration, sourceFenceState, sourcePowerState, targetPowerState,
                engineEvidenceJson);
    }

    public boolean isReady() { return ready; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getOperation() { return operation; }
    public Long getAuthorityGeneration() { return authorityGeneration; }
    public String getSourceFenceState() { return sourceFenceState; }
    public String getSourcePowerState() { return sourcePowerState; }
    public String getTargetPowerState() { return targetPowerState; }
    public String getEngineEvidenceJson() { return engineEvidenceJson; }
}
