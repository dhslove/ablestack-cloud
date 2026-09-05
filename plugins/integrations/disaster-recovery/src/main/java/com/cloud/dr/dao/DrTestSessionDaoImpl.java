// Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.
package com.cloud.dr.dao;

import com.cloud.dr.DrTestSessionVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.UpdateBuilder;

@DB
public class DrTestSessionDaoImpl extends GenericDaoBase<DrTestSessionVO, Long> implements DrTestSessionDao {
    private final SearchBuilder<DrTestSessionVO> activeByRun;
    private final SearchBuilder<DrTestSessionVO> activeByPlan;
    private final SearchBuilder<DrTestSessionVO> byRun;

    public DrTestSessionDaoImpl() {
        activeByRun = createSearchBuilder();
        activeByRun.and("runId", activeByRun.entity().getRunId(), SearchCriteria.Op.EQ);
        activeByRun.and("removed", activeByRun.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByRun.done();
        activeByPlan = createSearchBuilder();
        activeByPlan.and("planId", activeByPlan.entity().getPlanId(), SearchCriteria.Op.EQ);
        activeByPlan.and("removed", activeByPlan.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByPlan.done();
        byRun = createSearchBuilder();
        byRun.and("runId", byRun.entity().getRunId(), SearchCriteria.Op.EQ);
        byRun.done();
    }

    @Override public DrTestSessionVO findActiveByRunId(long runId) {
        SearchCriteria<DrTestSessionVO> sc = activeByRun.create();
        sc.setParameters("runId", runId);
        return findOneBy(sc);
    }

    @Override public DrTestSessionVO findActiveByPlanId(long planId) {
        SearchCriteria<DrTestSessionVO> sc = activeByPlan.create();
        sc.setParameters("planId", planId);
        return findOneBy(sc);
    }

    @Override public DrTestSessionVO findByRunIdIncludingRemoved(long runId) {
        SearchCriteria<DrTestSessionVO> sc = byRun.create();
        sc.setParameters("runId", runId);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public void restoreSoftClosedForMaterialization(DrTestSessionVO session) {
        DrTestSessionVO update = createForUpdate();
        UpdateBuilder builder = getUpdateBuilder(update);
        builder.set(update, "state", session.getState());
        builder.set(update, "cleanupRequired", session.isCleanupRequired());
        builder.set(update, "errorCode", null);
        builder.set(update, "errorMessage", null);
        builder.set(update, "removed", null);
        update(session.getId(), builder, update);
    }
}
