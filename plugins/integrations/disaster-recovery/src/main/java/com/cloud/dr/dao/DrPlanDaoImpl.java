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

import com.cloud.dr.DrPlanVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrPlanDaoImpl extends GenericDaoBase<DrPlanVO, Long> implements DrPlanDao {

    private final SearchBuilder<DrPlanVO> activeSearch;
    private final SearchBuilder<DrPlanVO> activeBySourceVmSearch;
    private final SearchBuilder<DrPlanVO> activeByEngineBindingSearch;
    private final SearchBuilder<DrPlanVO> activeByStateSearch;

    public DrPlanDaoImpl() {
        activeSearch = createSearchBuilder();
        activeSearch.and("removed", activeSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeSearch.done();

        activeBySourceVmSearch = createSearchBuilder();
        activeBySourceVmSearch.and("sourceVmId", activeBySourceVmSearch.entity().getSourceVmId(), SearchCriteria.Op.EQ);
        activeBySourceVmSearch.and("removed", activeBySourceVmSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeBySourceVmSearch.done();

        activeByEngineBindingSearch = createSearchBuilder();
        activeByEngineBindingSearch.and("engineBindingType", activeByEngineBindingSearch.entity().getEngineBindingType(), SearchCriteria.Op.EQ);
        activeByEngineBindingSearch.and("engineBindingId", activeByEngineBindingSearch.entity().getEngineBindingId(), SearchCriteria.Op.EQ);
        activeByEngineBindingSearch.and("removed", activeByEngineBindingSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByEngineBindingSearch.done();

        activeByStateSearch = createSearchBuilder();
        activeByStateSearch.and("state", activeByStateSearch.entity().getState(), SearchCriteria.Op.EQ);
        activeByStateSearch.and("removed", activeByStateSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByStateSearch.done();
    }

    @Override
    public DrPlanVO findActiveBySourceVmId(long sourceVmId) {
        SearchCriteria<DrPlanVO> sc = activeBySourceVmSearch.create();
        sc.setParameters("sourceVmId", sourceVmId);
        return findOneBy(sc);
    }

    @Override
    public DrPlanVO findActiveByEngineBinding(String engineBindingType, long engineBindingId) {
        SearchCriteria<DrPlanVO> sc = activeByEngineBindingSearch.create();
        sc.setParameters("engineBindingType", engineBindingType);
        sc.setParameters("engineBindingId", engineBindingId);
        return findOneBy(sc);
    }

    @Override
    public List<DrPlanVO> listActive() {
        return listBy(activeSearch.create());
    }

    @Override
    public List<DrPlanVO> listActiveByState(String state) {
        SearchCriteria<DrPlanVO> sc = activeByStateSearch.create();
        sc.setParameters("state", state);
        return listBy(sc);
    }
}
