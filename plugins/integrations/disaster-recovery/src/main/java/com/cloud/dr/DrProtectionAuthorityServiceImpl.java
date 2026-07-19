// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.dao.DrPlanRuntimeDao;
import com.cloud.utils.component.ManagerBase;

public class DrProtectionAuthorityServiceImpl extends ManagerBase implements DrProtectionAuthorityService {
    @Inject
    private DrPlanRuntimeDao drPlanRuntimeDao;

    @Override
    public DrProtectionAuthoritySnapshot getAuthority(long planId) {
        DrPlanRuntimeVO runtime = drPlanRuntimeDao.findByPlanId(planId);
        boolean ready = runtime != null
                && StringUtils.equals(runtime.getProtectionState(), DrConstants.PLAN_STATE_READY)
                && StringUtils.equals(runtime.getFreshnessState(), "WITHIN_RPO")
                && runtime.isSchedulerPidAlive()
                && !runtime.isRpoOverdue()
                && Boolean.TRUE.equals(runtime.getLatestCompletedIncrementalVerified())
                && runtime.getConsecutiveAutomaticReseedCount() == 0
                && !StringUtils.equalsAnyIgnoreCase(runtime.getCurrentCycleState(), "ERROR", "FAILED")
                && StringUtils.isBlank(runtime.getErrorCode());
        return new DrProtectionAuthoritySnapshot(runtime, ready);
    }
}
