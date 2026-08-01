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
        if (!StringUtils.equals(session.getReplicationDirection(), "ABLESTACK_TO_VMWARE")
                || !StringUtils.equals(session.getProviderPair(), "ABLESTACK_TO_VMWARE")) {
            return DrFailbackDataGateResult.blocked("DR_FAILBACK_DIRECTION_MISMATCH",
                    "Failback requires an ABLESTACK_TO_VMWARE reverse checkpoint");
        }
        if (session.getBaselineGeneration() == null || session.getBaselineGeneration() < 1
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
