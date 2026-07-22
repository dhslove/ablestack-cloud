// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr.dao;

import java.util.List;

import com.cloud.dr.DrSyncCycleVO;
import com.cloud.utils.db.GenericDao;

public interface DrSyncCycleDao extends GenericDao<DrSyncCycleVO, Long> {
    DrSyncCycleVO findByPlanRunSequence(long planId, String runUuid, long sequence);
    DrSyncCycleVO findActiveByPlanId(long planId);
    DrSyncCycleVO findLatestCompletedByPlanId(long planId);
    DrSyncCycleVO findLatestByPlanId(long planId);
    List<DrSyncCycleVO> listByPlanId(long planId);
    int removeByPlanId(long planId);
}
