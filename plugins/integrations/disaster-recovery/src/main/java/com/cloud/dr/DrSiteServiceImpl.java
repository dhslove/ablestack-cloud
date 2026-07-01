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
package com.cloud.dr;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.cloud.dr.dao.DrSiteDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;

public class DrSiteServiceImpl extends ManagerBase implements DrSiteService {
    @Inject
    private DrSiteDao drSiteDao;

    @Override
    public DrSiteVO createSite(DrSiteVO site) {
        validateSite(site);
        DrSiteVO existing = drSiteDao.findActiveByName(site.getName());
        if (existing != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_DUPLICATE_SITE + ": site name already exists");
        }

        if (StringUtils.isBlank(site.getState())) {
            site.setState(DrConstants.ADMIN_STATE_ENABLED);
        }
        if (StringUtils.isBlank(site.getHealthState())) {
            site.setHealthState(DrConstants.HEALTH_UNKNOWN);
        }
        return drSiteDao.persist(site);
    }

    @Override
    public DrSiteVO updateSite(long siteId, DrSiteVO update) {
        DrSiteVO site = requireSite(siteId);
        if (update == null) {
            return site;
        }
        if (StringUtils.isNotBlank(update.getName()) && !StringUtils.equals(site.getName(), update.getName())) {
            DrSiteVO existing = drSiteDao.findActiveByName(update.getName());
            if (existing != null && existing.getId() != siteId) {
                throw new InvalidParameterValueException(DrConstants.ERROR_DUPLICATE_SITE + ": site name already exists");
            }
            site.setName(update.getName());
        }
        if (update.getDescription() != null) {
            site.setDescription(update.getDescription());
        }
        if (StringUtils.isNotBlank(update.getSiteType())) {
            site.setSiteType(update.getSiteType());
        }
        if (StringUtils.isNotBlank(update.getHypervisorType())) {
            site.setHypervisorType(update.getHypervisorType());
        }
        if (update.getEndpoint() != null) {
            site.setEndpoint(update.getEndpoint());
        }
        if (update.getCredentialRef() != null) {
            site.setCredentialRef(update.getCredentialRef());
        }
        if (update.getZoneId() != null) {
            site.setZoneId(update.getZoneId());
        }
        if (update.getVmwareDatacenterId() != null) {
            site.setVmwareDatacenterId(update.getVmwareDatacenterId());
        }
        if (StringUtils.isNotBlank(update.getState())) {
            site.setState(update.getState());
        }
        if (StringUtils.isNotBlank(update.getHealthState())) {
            site.setHealthState(update.getHealthState());
        }
        if (update.getCapabilitiesJson() != null) {
            site.setCapabilitiesJson(update.getCapabilitiesJson());
        }
        site.markUpdated();
        drSiteDao.update(siteId, site);
        return drSiteDao.findById(siteId);
    }

    @Override
    public DrSiteVO getSite(long siteId) {
        return requireSite(siteId);
    }

    @Override
    public List<DrSiteVO> listSites() {
        return drSiteDao.listActive();
    }

    @Override
    public boolean deleteSite(long siteId) {
        DrSiteVO site = requireSite(siteId);
        site.markRemoved();
        return drSiteDao.update(siteId, site);
    }

    @Override
    public DrSiteVO checkSite(long siteId) {
        DrSiteVO site = requireSite(siteId);
        site.setLastChecked(new Date());
        if (StringUtils.isBlank(site.getHealthState())) {
            site.setHealthState(DrConstants.HEALTH_UNKNOWN);
        }
        site.markUpdated();
        drSiteDao.update(siteId, site);
        return drSiteDao.findById(siteId);
    }

    private DrSiteVO requireSite(long siteId) {
        DrSiteVO site = drSiteDao.findById(siteId);
        if (site == null || site.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_SITE_NOT_FOUND + ": " + siteId);
        }
        return site;
    }

    private void validateSite(DrSiteVO site) {
        if (site == null) {
            throw new InvalidParameterValueException("DR site is required");
        }
        if (StringUtils.isBlank(site.getName())) {
            throw new InvalidParameterValueException("DR site name is required");
        }
        if (StringUtils.isBlank(site.getSiteType())) {
            throw new InvalidParameterValueException("DR site type is required");
        }
        if (StringUtils.isBlank(site.getHypervisorType())) {
            throw new InvalidParameterValueException("DR site hypervisor type is required");
        }
    }
}
