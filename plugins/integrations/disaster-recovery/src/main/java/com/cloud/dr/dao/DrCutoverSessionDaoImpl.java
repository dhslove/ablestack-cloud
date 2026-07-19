// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
package com.cloud.dr.dao;

import com.cloud.dr.DrCutoverSessionVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrCutoverSessionDaoImpl extends GenericDaoBase<DrCutoverSessionVO, Long> implements DrCutoverSessionDao {
    private final SearchBuilder<DrCutoverSessionVO> activeByRun;
    public DrCutoverSessionDaoImpl() {
        activeByRun = createSearchBuilder();
        activeByRun.and("runId", activeByRun.entity().getRunId(), SearchCriteria.Op.EQ);
        activeByRun.and("removed", activeByRun.entity().getRemoved(), SearchCriteria.Op.NULL);
        activeByRun.done();
    }
    @Override public DrCutoverSessionVO findActiveByRunId(long runId) {
        SearchCriteria<DrCutoverSessionVO> sc = activeByRun.create(); sc.setParameters("runId", runId); return findOneBy(sc);
    }
}
