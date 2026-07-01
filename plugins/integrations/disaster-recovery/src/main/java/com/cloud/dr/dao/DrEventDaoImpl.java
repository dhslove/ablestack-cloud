// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.dr.dao;

import java.util.List;

import com.cloud.dr.DrEventVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrEventDaoImpl extends GenericDaoBase<DrEventVO, Long> implements DrEventDao {

    private final SearchBuilder<DrEventVO> byPlanSearch;
    private final SearchBuilder<DrEventVO> byRunSearch;
    private final SearchBuilder<DrEventVO> byPlanAndEventTypeSearch;

    public DrEventDaoImpl() {
        byPlanSearch = createSearchBuilder();
        byPlanSearch.and("planId", byPlanSearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        byPlanSearch.done();

        byRunSearch = createSearchBuilder();
        byRunSearch.and("runId", byRunSearch.entity().getRunId(), SearchCriteria.Op.EQ);
        byRunSearch.done();

        byPlanAndEventTypeSearch = createSearchBuilder();
        byPlanAndEventTypeSearch.and("planId", byPlanAndEventTypeSearch.entity().getPlanId(), SearchCriteria.Op.EQ);
        byPlanAndEventTypeSearch.and("eventType", byPlanAndEventTypeSearch.entity().getEventType(), SearchCriteria.Op.EQ);
        byPlanAndEventTypeSearch.done();
    }

    @Override
    public List<DrEventVO> listByPlanId(long planId) {
        SearchCriteria<DrEventVO> sc = byPlanSearch.create();
        sc.setParameters("planId", planId);
        return listBy(sc, new Filter(DrEventVO.class, "created", false, null, null));
    }

    @Override
    public List<DrEventVO> listByRunId(long runId) {
        SearchCriteria<DrEventVO> sc = byRunSearch.create();
        sc.setParameters("runId", runId);
        return listBy(sc, new Filter(DrEventVO.class, "created", false, null, null));
    }

    @Override
    public DrEventVO findLatestByPlanIdAndEventType(long planId, String eventType) {
        SearchCriteria<DrEventVO> sc = byPlanAndEventTypeSearch.create();
        sc.setParameters("planId", planId);
        sc.setParameters("eventType", eventType);
        List<DrEventVO> events = listBy(sc, new Filter(DrEventVO.class, "created", false, 0L, 1L));
        return events.isEmpty() ? null : events.get(0);
    }
}
