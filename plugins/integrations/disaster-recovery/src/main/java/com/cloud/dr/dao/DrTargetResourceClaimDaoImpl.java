// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.
package com.cloud.dr.dao;

import com.cloud.dr.DrTargetResourceClaimVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@DB
public class DrTargetResourceClaimDaoImpl extends GenericDaoBase<DrTargetResourceClaimVO, Long>
        implements DrTargetResourceClaimDao {
    private final SearchBuilder<DrTargetResourceClaimVO> resourceKeySearch;
    private final SearchBuilder<DrTargetResourceClaimVO> roleKeySearch;

    public DrTargetResourceClaimDaoImpl() {
        resourceKeySearch = createSearchBuilder();
        resourceKeySearch.and("activeResourceKey", resourceKeySearch.entity().getActiveResourceKey(), SearchCriteria.Op.EQ);
        resourceKeySearch.done();
        roleKeySearch = createSearchBuilder();
        roleKeySearch.and("activeRoleKey", roleKeySearch.entity().getActiveRoleKey(), SearchCriteria.Op.EQ);
        roleKeySearch.done();
    }

    @Override
    public DrTargetResourceClaimVO findActiveByResourceKey(String activeResourceKey) {
        SearchCriteria<DrTargetResourceClaimVO> sc = resourceKeySearch.create();
        sc.setParameters("activeResourceKey", activeResourceKey);
        return findOneBy(sc);
    }

    @Override
    public DrTargetResourceClaimVO findActiveByRoleKey(String activeRoleKey) {
        SearchCriteria<DrTargetResourceClaimVO> sc = roleKeySearch.create();
        sc.setParameters("activeRoleKey", activeRoleKey);
        return findOneBy(sc);
    }
}
