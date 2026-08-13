// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import org.apache.commons.lang3.StringUtils;

import com.cloud.utils.component.ManagerBase;

public class DrFailbackDataGateServiceImpl extends ManagerBase implements DrFailbackDataGateService {
    @Override
    public DrFailbackDataGateResult validate(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session) {
        if (plan == null || run == null || session == null) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_DATA_EVIDENCE_MISSING",
                    "Failback replication evidence is unavailable");
        }
        DrFailbackDataEvidence evidence = DrFailbackDataEvidence.from(session);
        if (!evidence.isComplete()) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_DATA_EVIDENCE_INCOMPLETE",
                    "Durable reverse-data evidence is still being published: "
                            + StringUtils.join(evidence.getMissingFields(), ','));
        }
        DrFailbackRouteContract expectedRoute = DrFailbackRouteContract.forPlan(plan);
        DrFailbackRouteContract actualRoute = DrFailbackRouteContract.normalize(
                session.getReplicationDirection(), session.getProviderPair(), null);
        if (!actualRoute.directionMatches(expectedRoute)) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_ROUTE_DIRECTION_INVALID",
                    "Failback reverse checkpoint direction does not match the DR plan");
        }
        if (!actualRoute.providerPairMatches(expectedRoute)) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_ROUTE_PROVIDER_INVALID",
                    "Failback reverse checkpoint provider path does not match the DR plan");
        }
        if (session.getBaselineGeneration() < 1
                || !StringUtils.equals(session.getBaselineState(), "LOCAL_DURABLE")
                || !StringUtils.equals(session.getTrackerState(), "LOCAL_DURABLE")) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_BASELINE_NOT_DURABLE",
                    "The reverse replication baseline is not durable");
        }
        if (!StringUtils.equals(session.getWriterState(), "DURABLE")
                || !Boolean.TRUE.equals(session.getTargetWritten())
                || !Boolean.TRUE.equals(session.getWriteVerified())) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_TARGET_WRITE_UNVERIFIED",
                    "VMware target writes were not durably verified");
        }
        if (!StringUtils.equalsAny(session.getGuestCompatibilityState(),
                        "ORIGINAL_VMWARE_COMPATIBILITY_PRESERVED", "READY")) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_GUEST_COMPATIBILITY_NOT_READY",
                    "The guest is not prepared to boot on VMware");
        }
        return DrFailbackDataGateResult.ready();
    }
}
