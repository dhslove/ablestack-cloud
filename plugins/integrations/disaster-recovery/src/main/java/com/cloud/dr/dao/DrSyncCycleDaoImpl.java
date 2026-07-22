// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.
package com.cloud.dr.dao;

import java.util.List;

import com.cloud.dr.DrSyncCycleVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrSyncCycleDaoImpl extends GenericDaoBase<DrSyncCycleVO, Long> implements DrSyncCycleDao {
    private final SearchBuilder<DrSyncCycleVO> byIdentitySearch;
    private final SearchBuilder<DrSyncCycleVO> byPlanSearch;
    private final SearchBuilder<DrSyncCycleVO> activeByPlanSearch;
    private final SearchBuilder<DrSyncCycleVO> completedByPlanSearch;

    private static final String[] ACTIVE_STATES = {
            "PREPARING", "SNAPSHOTTING", "TRANSFERRING", "COMMITTING", "RETRYING", "RUNNING"
    };

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

        activeByPlanSearch = createSearchBuilder();
        activeByPlanSearch.and("planId", activeByPlanSearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        activeByPlanSearch.and("states", activeByPlanSearch.entity().getState(), SearchCriteria.Op.IN);
        activeByPlanSearch.and("removed", activeByPlanSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByPlanSearch.done();

        completedByPlanSearch = createSearchBuilder();
        completedByPlanSearch.and("planId", completedByPlanSearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        completedByPlanSearch.and("completed", completedByPlanSearch.entity().getCompleted(), SearchCriteria.Op.NNULL);
        completedByPlanSearch.and("removed", completedByPlanSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        completedByPlanSearch.done();
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
    public DrSyncCycleVO findActiveByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = activeByPlanSearch.create();
        sc.setParameters("planId", planId);
        sc.setParameters("states", (Object[]) ACTIVE_STATES);
        List<DrSyncCycleVO> rows = listBy(sc, new Filter(DrSyncCycleVO.class, "sequence", false, 0L, 1L));
        return rows != null && !rows.isEmpty() ? rows.get(0) : null;
    }

    @Override
    public DrSyncCycleVO findLatestCompletedByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = completedByPlanSearch.create();
        sc.setParameters("planId", planId);
        List<DrSyncCycleVO> rows = listBy(sc, new Filter(DrSyncCycleVO.class, "sequence", false, 0L, 1L));
        return rows != null && !rows.isEmpty() ? rows.get(0) : null;
    }

    @Override
    public List<DrSyncCycleVO> listByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = byPlanSearch.create();
        sc.setParameters("planId", planId);
        return listBy(sc, new Filter(DrSyncCycleVO.class, "sequence", false, null, null));
    }

    @Override
    public int removeByPlanId(long planId) {
        SearchCriteria<DrSyncCycleVO> sc = byPlanSearch.create();
        sc.setParameters("planId", planId);
        return remove(sc);
    }
}
