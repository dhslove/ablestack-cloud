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

import org.apache.commons.lang3.StringUtils;
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
import com.cloud.dr.dao.DrSyncCycleDao;
import com.cloud.dr.response.DrResponseGenerator;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrProtectionViewServiceImpl extends ManagerBase implements DrProtectionViewService {
    private static final int SNAPSHOT_VERSION = 2;
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
    @Inject private DrSyncCycleDao drSyncCycleDao;
    @Inject private DrProjectionService drProjectionService;
    @Inject private DrProtectionAuthorityService drProtectionAuthorityService;
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
        DrProtectionAuthoritySnapshot authority = drProtectionAuthorityService.getAuthority(planId);
        if (authorityRegressed(existingCache, authority)) {
            DrPlanViewCacheVO update = drPlanViewCacheDao.createForUpdate();
            update.markRefreshFailed("DR_PROTECTION_VIEW_AUTHORITY_REGRESSION",
                    "Protection authority sequence moved backwards");
            drPlanViewCacheDao.update(existingCache.getId(), update);
            return drPlanViewCacheDao.findById(existingCache.getId());
        }

        DrRunVO activeRun = drRunDao.findActiveByPlanId(planId);
        DrRunVO latestOperationRun = drRunDao.findLatestByPlanId(planId);
        List<DrRunStepVO> activeRunSteps = activeRun != null
                ? drRunStepDao.listActiveByRunId(activeRun.getId()) : Collections.emptyList();
        List<DrRunStepVO> latestOperationRunSteps = latestOperationRun != null
                ? drRunStepDao.listActiveByRunId(latestOperationRun.getId()) : Collections.emptyList();
        DrSyncCycleVO currentSyncCycle = authoritativeActiveCycle(
                authority, drSyncCycleDao.findActiveByPlanId(planId));
        DrSyncCycleVO latestCompletedSyncCycle = drSyncCycleDao.findLatestCompletedByPlanId(planId);
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(planId);
        DrRestorePointVO latestCompletedCheckpoint = drRestorePointDao.findLatestTargetReadyByPlanId(planId);
        List<DrEventVO> events = drEventDao.listRecentByPlanId(planId, EVENT_LIMIT, false);

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("version", SNAPSHOT_VERSION);
        snapshot.add("plan", typedJson(plan, DrPlanVO.class));
        snapshot.add("sourceSite", siteJson(drSiteDao.findById(plan.getSourceSiteId())));
        snapshot.add("targetSite", siteJson(drSiteDao.findById(plan.getTargetSiteId())));
        snapshot.add("currentProtectionRuntime", protectionRuntimeJson(authority));

        JsonElement activeRunResponse = runJson(activeRun, activeRunSteps);
        JsonArray activeRunStepResponses = runStepJson(activeRunSteps);
        JsonElement latestOperationRunResponse = runJson(latestOperationRun, latestOperationRunSteps);
        JsonArray latestOperationRunStepResponses = runStepJson(latestOperationRunSteps);
        snapshot.add("activeRun", activeRunResponse);
        snapshot.add("activeRunSteps", activeRunStepResponses);
        snapshot.add("latestOperationRun", latestOperationRunResponse);
        snapshot.add("latestOperationRunSteps", latestOperationRunStepResponses);
        snapshot.add("currentSyncCycle", cycleJson(currentSyncCycle));
        snapshot.add("latestCompletedSyncCycle", cycleJson(latestCompletedSyncCycle));

        // Compatibility aliases for one release. They are history, not current activity.
        snapshot.add("latestRun", latestOperationRunResponse);
        snapshot.add("latestRunSteps", latestOperationRunStepResponses);

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

    private JsonElement runJson(DrRunVO run, List<DrRunStepVO> steps) {
        return run == null ? JsonNull.INSTANCE
                : typedJson(drResponseGenerator.createRunResponse(run, steps, run.isEngineAccepted()), DrRunResponse.class);
    }

    private JsonArray runStepJson(List<DrRunStepVO> steps) {
        JsonArray responses = new JsonArray();
        for (DrRunStepVO step : steps) {
            responses.add(typedJson(drResponseGenerator.createRunStepResponse(step), DrRunStepResponse.class));
        }
        return responses;
    }

    private JsonElement cycleJson(DrSyncCycleVO cycle) {
        if (cycle == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", cycle.getUuid());
        json.addProperty("sequence", cycle.getSequence());
        json.addProperty("state", cycle.getState());
        json.addProperty("requestedMode", cycle.getRequestedMode());
        json.addProperty("effectiveMode", cycle.getEffectiveMode());
        json.addProperty("commitState", cycle.getCommitState());
        json.addProperty("changedBytes", cycle.getChangedBytes());
        json.addProperty("sourceReadBytes", cycle.getSourceReadBytes());
        json.addProperty("targetWrittenBytes", cycle.getTargetWrittenBytes());
        json.addProperty("transferPayloadBytes", cycle.getTransferPayloadBytes());
        json.addProperty("incrementalVerified", cycle.getIncrementalVerified());
        json.addProperty("sourceCheckpointAt", formatDate(cycle.getSourceCheckpointAt()));
        json.addProperty("targetDurableAt", formatDate(cycle.getTargetDurableAt()));
        json.addProperty("started", formatDate(cycle.getStarted()));
        json.addProperty("completed", formatDate(cycle.getCompleted()));
        json.addProperty("errorCode", cycle.getErrorCode());
        json.addProperty("errorMessage", cycle.getErrorMessage());
        return json;
    }

    private DrSyncCycleVO authoritativeActiveCycle(DrProtectionAuthoritySnapshot authority,
            DrSyncCycleVO candidate) {
        if (authority == null || authority.getRuntime() == null || candidate == null) {
            return null;
        }
        DrPlanRuntimeVO runtime = authority.getRuntime();
        if (runtime.getCurrentCycleSequence() == null
                || runtime.getCurrentCycleSequence().longValue() != candidate.getSequence()) {
            return null;
        }
        if (StringUtils.isBlank(runtime.getCurrentCycleState()) || StringUtils.isBlank(candidate.getState())
                || !runtime.getCurrentCycleState().equalsIgnoreCase(candidate.getState())) {
            return null;
        }
        return candidate;
    }

    private JsonElement protectionRuntimeJson(DrProtectionAuthoritySnapshot authority) {
        if (authority == null || authority.getRuntime() == null) {
            return JsonNull.INSTANCE;
        }
        DrPlanRuntimeVO runtime = authority.getRuntime();
        JsonObject json = new JsonObject();
        json.addProperty("runtimeGeneration", runtime.getRuntimeGeneration());
        json.addProperty("authoritySequence", runtime.getAuthoritySequence());
        json.addProperty("protectionState", runtime.getProtectionState());
        json.addProperty("freshnessState", runtime.getFreshnessState());
        json.addProperty("projectionIntegrityState", runtime.getProjectionIntegrityState());
        json.addProperty("schedulerState", runtime.getSchedulerState());
        json.addProperty("schedulerDesiredState", runtime.getSchedulerDesiredState());
        json.addProperty("schedulerServiceUnit", runtime.getSchedulerServiceUnit());
        json.addProperty("schedulerUnitActiveState", runtime.getSchedulerUnitActiveState());
        json.addProperty("schedulerUnitSubState", runtime.getSchedulerUnitSubState());
        json.addProperty("schedulerRecoveryState", runtime.getSchedulerRecoveryState());
        json.addProperty("schedulerRecoveryTrigger", runtime.getSchedulerRecoveryTrigger());
        json.addProperty("schedulerRecoveredAt", formatDate(runtime.getSchedulerRecoveredAt()));
        json.addProperty("schedulerHealth", runtime.getSchedulerHealthState());
        json.addProperty("schedulerPidAlive", runtime.isSchedulerPidAlive());
        json.addProperty("schedulerSessionUuid", runtime.getSchedulerSessionUuid());
        json.addProperty("schedulerLeaseEpoch", runtime.getSchedulerLeaseEpoch());
        json.addProperty("ownerMatched", runtime.isOwnerMatched());
        json.addProperty("replicationActivity", runtime.getReplicationActivityState());
        json.addProperty("workerHeartbeatAt", formatDate(runtime.getWorkerHeartbeatAt()));
        json.addProperty("currentCycleSequence", runtime.getCurrentCycleSequence());
        json.addProperty("currentCycleState", runtime.getCurrentCycleState());
        json.addProperty("currentCycleMode", runtime.getCurrentCycleMode());
        json.addProperty("latestCompletedCycleSequence", runtime.getLatestCompletedCycleSequence());
        json.addProperty("lastSourceCheckpointAt", formatDate(runtime.getLastSourceCheckpointAt()));
        json.addProperty("lastTargetDurableAt", formatDate(runtime.getLastTargetDurableAt()));
        json.addProperty("rpoAgeSeconds", runtime.getRpoAgeSeconds());
        json.addProperty("rpoOverdue", runtime.isRpoOverdue());
        copyRuntimeFields(runtime.getStatusJson(), json);
        return json;
    }

    private void copyRuntimeFields(String statusJson, JsonObject target) {
        if (statusJson == null) {
            return;
        }
        try {
            JsonObject source = JsonParser.parseString(statusJson).getAsJsonObject();
            copyField(source, target, "control_protocol_version", "runtimeControlProtocolVersion");
            copyField(source, target, "control_generation", "runtimeControlGeneration");
            copyField(source, target, "control_ack_generation", "runtimeControlAckGeneration");
            copyField(source, target, "control_state", "runtimeControlState");
            copyField(source, target, "cycle_state", "runtimeCycleState");
            copyField(source, target, "transition_state", "runtimeTransitionState");
            copyField(source, target, "checkpoint_lease_state", "runtimeCheckpointLeaseState");
        } catch (RuntimeException e) {
            logger.warn("Ignoring malformed DR plan runtime status JSON while building protection view", e);
        }
    }

    private void copyField(JsonObject source, JsonObject target, String sourceName, String targetName) {
        if (source.has(sourceName) && !source.get(sourceName).isJsonNull()) {
            target.add(targetName, source.get(sourceName));
        }
    }

    private String formatDate(java.util.Date value) {
        return value == null ? null : GSON.toJsonTree(value).getAsString();
    }

    private boolean authorityRegressed(DrPlanViewCacheVO existingCache, DrProtectionAuthoritySnapshot authority) {
        if (existingCache == null || existingCache.getSnapshotVersion() < SNAPSHOT_VERSION
                || authority == null || authority.getAuthoritySequence() == null) {
            return false;
        }
        try {
            JsonObject snapshot = JsonParser.parseString(existingCache.getSnapshotJson()).getAsJsonObject();
            JsonElement sequence = snapshot.getAsJsonObject("currentProtectionRuntime").get("authoritySequence");
            return sequence != null && !sequence.isJsonNull()
                    && authority.getAuthoritySequence() < sequence.getAsLong();
        } catch (RuntimeException e) {
            return false;
        }
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
