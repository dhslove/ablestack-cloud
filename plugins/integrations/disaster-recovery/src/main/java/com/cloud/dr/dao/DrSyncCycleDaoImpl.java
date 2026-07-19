// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr.dao;

import java.util.List;

import com.cloud.dr.DrSyncCycleVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrSyncCycleDaoImpl extends GenericDaoBase<DrSyncCycleVO, Long> implements DrSyncCycleDao {
    private final SearchBuilder<DrSyncCycleVO> byIdentitySearch;
    private final SearchBuilder<DrSyncCycleVO> byPlanSearch;

    public DrSyncCycleDaoImpl() {
        byIdentitySearch = createSearchBuilder();
        byIdentitySearch.and("planId", byIdentitySearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        byIdentitySearch.and("runUuid", byIdentitySearch.entity().getEngineRunUuid(), SearchCriteria.Op.EQ);
        byIdentitySearch.and("sequence", byIdentitySearch.entity().getSequence(), SearchCriteria.Op.EQ);
        byIdentitySearch.done();

        byPlanSearch = createSearchBuilder();
        byPlanSearch.and("planId", byPlanSearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        byPlanSearch.and("removed", byPlanSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        byPlanSearch.done();
    }

    @Override
    public DrSyncCycleVO findByPlanRunSequence(long planId, String runUuid, long sequence) {
        SearchCriteria<DrSyncCycleVO> sc = byIdentitySearch.create();
        sc.setParameters("planId", planId);
        sc.setParameters("runUuid", runUuid);
        sc.setParameters("sequence", sequence);
        return findOneBy(sc);
    }

    @Override
    public DrSyncCycleVO findLatestByPlanId(long planId) {
        List<DrSyncCycleVO> rows = listByPlanId(planId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<DrSyncCycleVO> listByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = byPlanSearch.create();
        sc.setParameters("planId", planId);
        return listBy(sc, new com.cloud.utils.db.Filter(DrSyncCycleVO.class, "sequence", false, null, null));
    }

    @Override
    public int removeByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = byPlanSearch.create();
        sc.setParameters("planId", planId);
        return remove(sc);
    }
}
