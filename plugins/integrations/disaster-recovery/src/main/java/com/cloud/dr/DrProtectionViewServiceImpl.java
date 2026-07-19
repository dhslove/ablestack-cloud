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

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.api.response.dr.DrEventResponse;
import org.apache.cloudstack.api.response.dr.DrReplicaResponse;
import org.apache.cloudstack.api.response.dr.DrRestorePointResponse;
import org.apache.cloudstack.api.response.dr.DrRunResponse;
import org.apache.cloudstack.api.response.dr.DrRunStepResponse;

import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanViewCacheDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.response.DrResponseGenerator;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class DrProtectionViewServiceImpl extends ManagerBase implements DrProtectionViewService {
    private static final int SNAPSHOT_VERSION = 1;
    private static final int EVENT_LIMIT = 20;
    private static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").serializeNulls().create();

    @Inject private DrPlanDao drPlanDao;
    @Inject private DrPlanViewCacheDao drPlanViewCacheDao;
    @Inject private DrSiteDao drSiteDao;
    @Inject private DrRunDao drRunDao;
    @Inject private DrRunStepDao drRunStepDao;
    @Inject private DrReplicaDao drReplicaDao;
    @Inject private DrRestorePointDao drRestorePointDao;
    @Inject private DrEventDao drEventDao;
    @Inject private DrProjectionService drProjectionService;
    @Inject private DrResponseGenerator drResponseGenerator;

    @Override
    public DrPlanViewCacheVO getProtectionView(long planId) {
        requirePlan(planId);
        DrPlanViewCacheVO cache = drPlanViewCacheDao.findByPlanId(planId);
        return cache != null ? cache : rebuildProtectionView(planId);
    }

    @Override
    public DrPlanViewCacheVO refreshProjectionAndView(long planId, boolean bestEffort) {
        String error = null;
        try {
            // Always request a strict projection result here. This method owns
            // the best-effort fallback and must know when to retain last-good data.
            drProjectionService.refreshPlanProjection(planId, false);
        } catch (RuntimeException e) {
            error = e.getMessage();
            if (!bestEffort) {
                throw e;
            }
            logger.warn(String.format("Failed to refresh DR plan %s before rebuilding its protection view", planId), e);
        }
        return rebuildProtectionView(planId, error);
    }

    @Override
    public DrPlanViewCacheVO rebuildProtectionView(long planId) {
        return rebuildProtectionView(planId, null);
    }

    private DrPlanViewCacheVO rebuildProtectionView(long planId, String projectionError) {
        DrPlanVO plan = requirePlan(planId);
        DrPlanViewCacheVO existingCache = drPlanViewCacheDao.findByPlanId(planId);
        if (projectionError != null && existingCache != null && existingCache.getSnapshotJson() != null) {
            DrPlanViewCacheVO update = drPlanViewCacheDao.createForUpdate();
            update.markRefreshFailed(errorCode(projectionError), projectionError);
            drPlanViewCacheDao.update(existingCache.getId(), update);
            return drPlanViewCacheDao.findById(existingCache.getId());
        }
        DrRunVO latestRun = drRunDao.findLatestByPlanId(planId);
        List<DrRunStepVO> steps = latestRun != null ? drRunStepDao.listActiveByRunId(latestRun.getId()) : Collections.emptyList();
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(planId);
        DrRestorePointVO latestCompletedCheckpoint = drRestorePointDao.findLatestTargetReadyByPlanId(planId);
        List<DrEventVO> events = drEventDao.listRecentByPlanId(planId, EVENT_LIMIT, false);

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("version", SNAPSHOT_VERSION);
        snapshot.add("plan", typedJson(plan, DrPlanVO.class));
        snapshot.add("sourceSite", siteJson(drSiteDao.findById(plan.getSourceSiteId())));
        snapshot.add("targetSite", siteJson(drSiteDao.findById(plan.getTargetSiteId())));
        snapshot.add("latestRun", latestRun == null ? JsonNull.INSTANCE
                : typedJson(drResponseGenerator.createRunResponse(latestRun, steps, latestRun.isEngineAccepted()), DrRunResponse.class));

        JsonArray stepResponses = new JsonArray();
        for (DrRunStepVO step : steps) {
            stepResponses.add(typedJson(drResponseGenerator.createRunStepResponse(step), DrRunStepResponse.class));
        }
        snapshot.add("latestRunSteps", stepResponses);

        JsonArray replicaResponses = new JsonArray();
        for (DrReplicaVO replica : replicas) {
            replicaResponses.add(typedJson(drResponseGenerator.createReplicaResponse(replica), DrReplicaResponse.class));
        }
        snapshot.add("replicas", replicaResponses);

        snapshot.add("latestCompletedCheckpoint", latestCompletedCheckpoint == null ? JsonNull.INSTANCE
                : typedJson(drResponseGenerator.createRestorePointResponse(latestCompletedCheckpoint), DrRestorePointResponse.class));

        JsonArray eventResponses = new JsonArray();
        for (DrEventVO event : events) {
            eventResponses.add(typedJson(drResponseGenerator.createEventResponse(event), DrEventResponse.class));
        }
        snapshot.add("events", eventResponses);

        DrPlanViewCacheVO cache = existingCache;
        if (cache == null) {
            cache = new DrPlanViewCacheVO(planId);
            cache.updateSnapshot(SNAPSHOT_VERSION, GSON.toJson(snapshot), projectionError == null ? "READY" : "STALE", projectionError);
            cache = drPlanViewCacheDao.persist(cache);
        } else {
            DrPlanViewCacheVO update = drPlanViewCacheDao.createForUpdate();
            update.updateSnapshot(SNAPSHOT_VERSION, GSON.toJson(snapshot), projectionError == null ? "READY" : "STALE", projectionError);
            drPlanViewCacheDao.update(cache.getId(), update);
            cache = drPlanViewCacheDao.findById(cache.getId());
        }
        return cache;
    }

    private String errorCode(String projectionError) {
        if (projectionError == null) {
            return null;
        }
        int separator = projectionError.indexOf(':');
        String value = separator > 0 ? projectionError.substring(0, separator) : projectionError;
        return value.length() > 255 ? value.substring(0, 255) : value;
    }

    private JsonElement typedJson(Object value, Class<?> declaredType) {
        return value == null ? JsonNull.INSTANCE : GSON.toJsonTree(value, declaredType);
    }

    private JsonElement siteJson(DrSiteVO site) {
        if (site == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", site.getUuid());
        json.addProperty("uuid", site.getUuid());
        json.addProperty("name", site.getName());
        json.addProperty("siteType", site.getSiteType());
        json.addProperty("hypervisorType", site.getHypervisorType());
        json.addProperty("endpoint", site.getEndpoint());
        json.addProperty("state", site.getState());
        json.addProperty("healthState", site.getHealthState());
        json.addProperty("zoneId", site.getZoneId());
        json.addProperty("zoneName", site.getZoneName());
        json.addProperty("vmwareDatacenterName", site.getVmwareDatacenterName());
        return json;
    }

    private DrPlanVO requirePlan(long planId) {
        DrPlanVO plan = drPlanDao.findById(planId);
        if (plan == null || plan.getRemoved() != null) {
            throw new InvalidParameterValueException(DrConstants.ERROR_PLAN_NOT_FOUND + ": " + planId);
        }
        return plan;
    }
}
