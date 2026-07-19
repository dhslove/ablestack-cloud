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
package com.cloud.dr.adapter.ftctl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrCapabilitiesAnswer;
import com.cloud.agent.api.FtctlDrCapabilitiesCommand;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.DrConstants;
import com.cloud.dr.DrPlanVO;
import com.cloud.dr.DrRestorePointVO;
import com.cloud.dr.DrResolvedSiteCredential;
import com.cloud.dr.DrRunVO;
import com.cloud.dr.DrSiteCredentialService;
import com.cloud.dr.DrSiteVO;
import com.cloud.dr.adapter.DrAdapterResult;
import com.cloud.dr.adapter.DrExecutionContext;
import com.cloud.dr.adapter.DrReplicationEngine;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.dao.DrRestorePointDao;
import com.cloud.dr.health.DrSiteProbeSupport;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.utils.component.ManagerBase;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FtctlDrUnifiedActionAdapter extends ManagerBase implements DrReplicationEngine {
    private static final Logger LOGGER = LogManager.getLogger(FtctlDrUnifiedActionAdapter.class);
    private static final Gson GSON = new Gson();
    private static final int AGENT_ACCEPT_TIMEOUT_SECONDS = 30;
    private static final int VCENTER_THUMBPRINT_TIMEOUT_MS = 10000;
    private static final String TEST_ARTIFACT_CONTRACT_VERSION = "3";

    @Inject
    private AgentManager agentManager;
    @Inject
    private HostDao hostDao;
    @Inject
    private HostDetailsDao hostDetailsDao;
    @Inject
    private DrSiteDao drSiteDao;
    @Inject
    private DrSiteCredentialService drSiteCredentialService;
    @Inject
    private DrRestorePointDao drRestorePointDao;

    @Override
    public String getEngineType() {
        return DrConstants.ENGINE_TYPE_FTCTL_DR;
    }

    @Override
    public String getEngineBindingType() {
        return DrConstants.ENGINE_BINDING_TYPE_FTCTL_DR;
    }

    @Override
    public DrAdapterResult validatePlan(DrPlanVO plan) {
        if (plan == null) {
            return DrAdapterResult.failure(DrConstants.ERROR_PLAN_NOT_FOUND, "FTCTL_DR plan is required", null);
        }
        String direction = StringUtils.upperCase(plan.getDirection(), Locale.ROOT);
        if (!StringUtils.equalsAny(direction, DrConstants.DIRECTION_KVM_TO_KVM, DrConstants.DIRECTION_KVM_TO_VMWARE,
                DrConstants.DIRECTION_VMWARE_TO_VMWARE, DrConstants.DIRECTION_VMWARE_TO_KVM)) {
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_UNSUPPORTED,
                    "FTCTL_DR does not support direction " + plan.getDirection(), GSON.toJson(buildValidationDetails(plan)));
        }
        DrAdapterResult credentialResult = validateVmwareCredentials(plan, direction);
        if (credentialResult != null) {
            return credentialResult;
        }
        DrAdapterResult mappingResult = validateKvmTargetMapping(plan, direction);
        if (mappingResult != null) {
            return mappingResult;
        }
        return DrAdapterResult.success("FTCTL_DR plan contract is valid", GSON.toJson(buildValidationDetails(plan)));
    }

    private DrAdapterResult validateKvmTargetMapping(DrPlanVO plan, String direction) {
        if (!StringUtils.endsWithIgnoreCase(direction, "_KVM")) {
            return null;
        }
        JsonObject mapping = parseObject(plan.getMappingJson());
        JsonObject target = objectAt(mapping, "target");
        JsonArray disks = firstArray(mapping, "disks", "diskMappings", "volumes", "volumeMappings");
        JsonArray missing = new JsonArray();
        if (StringUtils.isBlank(firstString(mapping, "targetStorageRef", "targetDatastoreRef"))
                && StringUtils.isBlank(firstString(target, "storageRef", "storagePoolId", "targetStorageRef"))
                && !diskMappingsProvideTargetStorage(disks)) {
            missing.add("TARGET_STORAGE_REQUIRED");
        }
        if (StringUtils.isBlank(firstString(mapping, "targetComputeRef", "serviceOfferingId"))
                && StringUtils.isBlank(firstString(target, "serviceOfferingId", "serviceOfferingRef", "computeOfferingId"))) {
            missing.add("TARGET_SERVICE_OFFERING_REQUIRED");
        }
        if (StringUtils.isBlank(firstString(mapping, "targetNetworkRef", "networkRef"))
                && firstArray(target, "networks", "networkRefs", "networkMappings").size() == 0) {
            missing.add("TARGET_NETWORK_REQUIRED");
        }
        if (disks.size() == 0) {
            missing.add("DISK_MAPPING_REQUIRED");
        }
        for (int i = 0; i < disks.size(); i++) {
            JsonElement element = disks.get(i);
            if (element == null || !element.isJsonObject()) {
                missing.add("DISK_MAPPING_REQUIRED:" + i);
                continue;
            }
            JsonObject disk = element.getAsJsonObject();
            JsonObject diskTarget = objectAt(disk, "target");
            if (StringUtils.isBlank(firstString(disk, "targetStorageRef", "storageRef"))
                    && StringUtils.isBlank(firstString(diskTarget, "storageRef", "storagePoolId", "targetStorageRef"))) {
                missing.add("TARGET_STORAGE_REQUIRED:" + i);
            }
            if (StringUtils.isBlank(firstString(disk, "targetDiskOfferingId", "diskOfferingId"))
                    && StringUtils.isBlank(firstString(diskTarget, "diskOfferingId", "diskOfferingRef", "offeringId"))) {
                missing.add("TARGET_DISK_OFFERING_REQUIRED:" + i);
            }
        }
        if (missing.size() == 0) {
            return null;
        }
        JsonObject details = buildValidationDetails(plan);
        details.add("blockingReasons", missing);
        return DrAdapterResult.failure(DrConstants.ERROR_TARGET_MAPPING_INVALID,
                "FTCTL_DR target KVM mapping is incomplete: " + missing, GSON.toJson(details));
    }

    private boolean diskMappingsProvideTargetStorage(JsonArray disks) {
        if (disks == null || disks.size() == 0) {
            return false;
        }
        for (int i = 0; i < disks.size(); i++) {
            JsonElement element = disks.get(i);
            if (element == null || !element.isJsonObject()) {
                return false;
            }
            JsonObject disk = element.getAsJsonObject();
            JsonObject target = objectAt(disk, "target");
            if (StringUtils.isBlank(firstNonBlank(firstString(disk, "targetStorageRef", "storageRef"),
                    firstString(target, "storageRef", "storagePoolId", "targetStorageRef")))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public DrAdapterResult execute(DrExecutionContext context) {
        FtctlDrActionCommand.Action action = resolveAction(context.getRun());
        if (action == null) {
            String message = "DR run type " + context.getRun().getRunType() + " is not supported by FTCTL_DR";
            return DrAdapterResult.failure(DrConstants.ERROR_ACTION_UNSUPPORTED, message, GSON.toJson(buildExecutionDetails(context, null, null)));
        }

        Long coordinatorHostId = resolveCoordinatorHostId(context.getPlan());
        if (coordinatorHostId == null) {
            String message = "FTCTL_DR requires a coordinator, source, or target worker host before dispatch";
            return DrAdapterResult.failure(DrConstants.ERROR_TARGET_MAPPING_INVALID, message, GSON.toJson(buildExecutionDetails(context, action, null)));
        }

        DrAdapterResult capabilityResult = validateCapabilities(context, action, coordinatorHostId);
        if (capabilityResult != null) {
            return capabilityResult;
        }

        DrAdapterResult checkpointValidation = validateLatestCheckpoint(context, action);
        if (checkpointValidation != null) {
            return checkpointValidation;
        }
        FtctlDrActionCommand command;
        try {
            command = buildActionCommand(context, action);
        } catch (IllegalArgumentException e) {
            return DrAdapterResult.failure("DR_TEST_ARTIFACT_SPEC_INVALID", e.getMessage(),
                    GSON.toJson(buildExecutionDetails(context, action, coordinatorHostId)));
        }
        try {
            Answer answer = agentManager.send(coordinatorHostId, command);
            return toAdapterResult(context, action, coordinatorHostId, answer);
        } catch (OperationTimedoutException e) {
            LOGGER.warn("Unable to dispatch FTCTL_DR run {} to host {}: {}", context.getRun().getId(), coordinatorHostId, e.getMessage());
            DrAdapterResult acceptedFromStatus = probeAcceptedStatus(context, action, coordinatorHostId);
            if (acceptedFromStatus != null) {
                return acceptedFromStatus;
            }
            return DrAdapterResult.failure(DrConstants.ERROR_AGENT_DISPATCH_TIMEOUT,
                    "Unable to dispatch FTCTL_DR run to Agent: " + e.getMessage(), GSON.toJson(buildExecutionDetails(context, action, coordinatorHostId)));
        } catch (AgentUnavailableException e) {
            LOGGER.warn("FTCTL_DR coordinator Agent is unavailable for run {} on host {}: {}", context.getRun().getId(), coordinatorHostId, e.getMessage());
            return DrAdapterResult.failure(DrConstants.ERROR_AGENT_UNAVAILABLE,
                    "FTCTL_DR coordinator Agent is unavailable: " + e.getMessage(), GSON.toJson(buildExecutionDetails(context, action, coordinatorHostId)));
        }
    }

    private FtctlDrActionCommand buildActionCommand(DrExecutionContext context, FtctlDrActionCommand.Action action) {
        DrPlanVO plan = context.getPlan();
        DrRunVO run = context.getRun();
        JsonObject request = requestJson(run);
        JsonObject redactedRequest = redactJson(request).getAsJsonObject();
        DrRestorePointVO latestCheckpoint = requiresLatestCheckpoint(action)
                ? drRestorePointDao.findLatestTargetReadyByPlanId(plan.getId()) : null;
        redactedRequest.remove("restorePointId");
        if (latestCheckpoint != null) {
            redactedRequest.addProperty("restorePointRef", latestCheckpoint.getSourceSnapshotRef());
        }
        FtctlDrActionCommand command = new FtctlDrActionCommand(action, plan.getUuid(), run.getUuid());
        command.setActionName(action.name());
        command.setCliCommand(action.getCliCommand());
        command.setRunType(run.getRunType());
        command.setDirection(plan.getDirection());
        command.setRole("coordinator");
        command.setSourceWorkerUuid(resolveHostUuid(plan.getSourceWorkerHostId()));
        command.setTargetWorkerUuid(resolveHostUuid(plan.getTargetWorkerHostId()));
        command.setCoordinatorWorkerUuid(resolveHostUuid(resolveCoordinatorHostId(plan)));
        command.setProfileJson(buildProfileJson(plan, run, redactedRequest));
        command.setRequestJson(GSON.toJson(redactedRequest));
        if (action == FtctlDrActionCommand.Action.TEST_PREPARE) {
            command.setArtifactContractVersion(TEST_ARTIFACT_CONTRACT_VERSION);
            command.setArtifactSpecJson(buildTestArtifactSpec(plan, run, latestCheckpoint));
        }
        command.setMode(requestString(request, "mode"));
        command.setCheckpointRef(latestCheckpoint != null ? latestCheckpoint.getSourceSnapshotRef() : null);
        command.setForce(requestBoolean(request, "force", false));
        command.setDryRun(requestBoolean(request, "dryRun", false));
        command.setWaitForCompletion(false);
        command.setWait(AGENT_ACCEPT_TIMEOUT_SECONDS);
        command.setContext(toStringMap(redactedRequest));
        return command;
    }

    private String buildTestArtifactSpec(DrPlanVO plan, DrRunVO run, DrRestorePointVO checkpoint) {
        JsonObject mapping = parseObject(plan.getMappingJson());
        JsonObject mappingTarget = objectAt(mapping, "target");
        JsonArray mappedDisks = firstArray(mapping, "disks", "diskMappings", "volumes", "volumeMappings");
        JsonArray artifactDisks = new JsonArray();
        for (int index = 0; index < mappedDisks.size(); index++) {
            JsonElement element = mappedDisks.get(index);
            if (element == null || !element.isJsonObject()) {
                throw new IllegalArgumentException("DR_TEST_ARTIFACT_SPEC_INVALID: disk mapping " + index + " is not an object");
            }
            JsonObject disk = element.getAsJsonObject();
            JsonObject target = objectAt(disk, "target");
            String storageType = firstNonBlank(firstString(target, "storagePoolType", "type", "targetType"),
                    firstString(mappingTarget, "storagePoolType", "type", "targetType"));
            String storagePath = firstNonBlank(firstString(target, "storagePath", "krbdPath"),
                    firstString(mappingTarget, "storagePath", "krbdPath"));
            String volumeRef = firstNonBlank(firstString(target, "path", "diskRef", "volumePath", "volumeUuid"),
                    firstString(disk, "targetRef", "targetDiskRef", "targetPath"));
            String provider = isRbdStorage(storageType, storagePath, volumeRef) ? "RBD" : "FILE";
            String canonicalLocator = canonicalArtifactLocator(provider, storagePath, volumeRef);
            JsonObject artifactDisk = new JsonObject();
            artifactDisk.addProperty("diskIndex", index);
            artifactDisk.addProperty("device", firstNonBlank(firstString(disk, "device", "label"), "disk" + index));
            artifactDisk.addProperty("provider", provider);
            artifactDisk.addProperty("canonicalLocator", canonicalLocator);
            artifactDisk.addProperty("format", StringUtils.defaultIfBlank(firstString(target, "format"),
                    StringUtils.equals(provider, "RBD") ? "raw" : "qcow2"));
            addLongIfPresent(artifactDisk, "targetVolumeId", firstLong(target, "volumeId", "targetVolumeId"));
            String sizeBytes = firstNonBlank(firstString(target, "capacityBytes", "sizeBytes"),
                    firstString(disk, "capacityBytes", "sizeBytes"));
            if (StringUtils.isNotBlank(sizeBytes)) {
                artifactDisk.addProperty("sizeBytes", sizeBytes);
            }
            artifactDisks.add(artifactDisk);
        }
        if (artifactDisks.size() == 0) {
            throw new IllegalArgumentException("DR_TEST_ARTIFACT_SPEC_INVALID: no mapped target disks");
        }
        JsonObject spec = new JsonObject();
        spec.addProperty("contractVersion", TEST_ARTIFACT_CONTRACT_VERSION);
        spec.addProperty("planUuid", plan.getUuid());
        spec.addProperty("runUuid", run.getUuid());
        if (checkpoint != null) {
            spec.addProperty("checkpointRef", checkpoint.getSourceSnapshotRef());
            spec.addProperty("checkpointSequence", checkpoint.getCheckpointSequence());
        }
        spec.add("disks", artifactDisks);
        return GSON.toJson(spec);
    }

    private boolean isRbdStorage(String storageType, String storagePath, String volumeRef) {
        return StringUtils.containsIgnoreCase(storageType, "RBD")
                || StringUtils.startsWithIgnoreCase(storagePath, "rbd")
                || StringUtils.startsWithIgnoreCase(volumeRef, "rbd:")
                || StringUtils.startsWithIgnoreCase(volumeRef, "/dev/rbd/");
    }

    private String canonicalArtifactLocator(String provider, String storagePath, String volumeRef) {
        String ref = StringUtils.trimToNull(volumeRef);
        if (StringUtils.equals(provider, "RBD")) {
            if (ref != null && StringUtils.startsWithIgnoreCase(ref, "rbd:")) {
                return "rbd:" + StringUtils.removeStartIgnoreCase(ref, "rbd:");
            }
            if (ref != null && StringUtils.startsWith(ref, "/dev/rbd/")) {
                return "rbd:" + StringUtils.removeStart(ref, "/dev/rbd/");
            }
            String pool = StringUtils.trimToNull(storagePath);
            if (pool != null && StringUtils.startsWithIgnoreCase(pool, "rbd:")) {
                pool = StringUtils.removeStartIgnoreCase(pool, "rbd:");
            }
            if (pool != null && StringUtils.startsWith(pool, "/dev/rbd/")) {
                pool = StringUtils.removeStart(pool, "/dev/rbd/");
            }
            if (ref == null || StringUtils.isBlank(pool)) {
                throw new IllegalArgumentException("DR_TEST_ARTIFACT_LOCATOR_INVALID: RBD pool and image are required");
            }
            return StringUtils.contains(ref, "/") ? "rbd:" + ref : "rbd:" + StringUtils.removeEnd(pool, "/") + "/" + ref;
        }
        if (StringUtils.isBlank(ref) || !StringUtils.startsWith(ref, "/")) {
            throw new IllegalArgumentException("DR_TEST_ARTIFACT_LOCATOR_INVALID: file-backed disk requires an absolute path");
        }
        return "file:" + ref;
    }

    private void addLongIfPresent(JsonObject object, String key, Long value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private Long firstLong(JsonObject object, String... keys) {
        String value = firstString(object, keys);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private DrAdapterResult validateLatestCheckpoint(DrExecutionContext context, FtctlDrActionCommand.Action action) {
        if (!requiresLatestCheckpoint(action)) {
            return null;
        }
        DrRestorePointVO latest = drRestorePointDao.findLatestTargetReadyByPlanId(context.getPlan().getId());
        if (latest != null && StringUtils.isNotBlank(latest.getSourceSnapshotRef())) {
            return null;
        }
        return DrAdapterResult.failure(DrConstants.ERROR_TARGET_NOT_READY,
                "The latest synchronized target checkpoint is not ready",
                GSON.toJson(buildExecutionDetails(context, action, resolveCoordinatorHostId(context.getPlan()))));
    }

    private boolean requiresLatestCheckpoint(FtctlDrActionCommand.Action action) {
        return action == FtctlDrActionCommand.Action.TEST_PREPARE || action == FtctlDrActionCommand.Action.FAILOVER;
    }

    private DrAdapterResult validateCapabilities(DrExecutionContext context, FtctlDrActionCommand.Action action, long hostId) {
        FtctlDrCapabilitiesCommand command = new FtctlDrCapabilitiesCommand(context.getPlan().getUuid(), context.getRun().getUuid());
        List<String> requiredActions = new ArrayList<String>();
        requiredActions.add(action.name());
        command.setRequiredActions(requiredActions);
        List<String> requiredCliCommands = new ArrayList<String>();
        requiredCliCommands.add(action.getCliCommand());
        requiredCliCommands.add("dr-status");
        command.setRequiredCliCommands(requiredCliCommands);
        try {
            Answer answer = agentManager.send(hostId, command);
            JsonObject details = buildExecutionDetails(context, action, hostId);
            details.add("capabilityCheck", GSON.toJsonTree(redactedCapabilities(answer)));
            if (!(answer instanceof FtctlDrCapabilitiesAnswer)) {
                String message = answer != null ? answer.getDetails() : "Agent returned no FTCTL_DR capability answer";
                details.addProperty("answerType", answer != null ? answer.getClass().getName() : null);
                return DrAdapterResult.failure(DrConstants.ERROR_AGENT_CAPABILITY_MISMATCH, message, GSON.toJson(details));
            }
            FtctlDrCapabilitiesAnswer capabilities = (FtctlDrCapabilitiesAnswer) answer;
            if (!capabilities.getResult()) {
                String message = StringUtils.defaultIfBlank(capabilities.getDetails(), "FTCTL_DR Agent capability mismatch");
                return DrAdapterResult.failure(DrConstants.ERROR_AGENT_CAPABILITY_MISMATCH, message, GSON.toJson(details));
            }
            if (requiresControlProtocol(action) && !supportsFeature(capabilities.getSupportedFeatures(), "control-protocol-v2")) {
                return DrAdapterResult.failure(DrConstants.ERROR_CONTROL_PROTOCOL_UNSUPPORTED,
                        "FTCTL_DR control protocol v2 is required for coordinated DR actions", GSON.toJson(details));
            }
            if (requiresVmwareGuestPreparation(context, action)) {
                String missingFeature = firstMissingFeature(capabilities.getSupportedFeatures(),
                        "guest-preparation-v2",
                        action == FtctlDrActionCommand.Action.TEST_PREPARE ? "test-artifact-lifecycle-v2" : "cutover-ready-v1");
                if (missingFeature != null) {
                    details.addProperty("missingFeature", missingFeature);
                    return DrAdapterResult.failure(DrConstants.ERROR_AGENT_CAPABILITY_MISMATCH,
                            "FTCTL_DR host does not provide required guest preparation capability: " + missingFeature,
                            GSON.toJson(details));
                }
            }
            return null;
        } catch (OperationTimedoutException e) {
            LOGGER.warn("FTCTL_DR capability check timed out for run {} on host {}: {}", context.getRun().getId(), hostId, e.getMessage());
            return DrAdapterResult.failure(DrConstants.ERROR_AGENT_CAPABILITY_MISMATCH,
                    "FTCTL_DR capability check timed out: " + e.getMessage(), GSON.toJson(buildExecutionDetails(context, action, hostId)));
        } catch (AgentUnavailableException e) {
            LOGGER.warn("FTCTL_DR capability check failed because Agent is unavailable for run {} on host {}: {}",
                    context.getRun().getId(), hostId, e.getMessage());
            return DrAdapterResult.failure(DrConstants.ERROR_AGENT_UNAVAILABLE,
                    "FTCTL_DR coordinator Agent is unavailable: " + e.getMessage(), GSON.toJson(buildExecutionDetails(context, action, hostId)));
        }
    }

    private boolean requiresControlProtocol(FtctlDrActionCommand.Action action) {
        return action != null && action != FtctlDrActionCommand.Action.TARGET_MATERIALIZED;
    }

    private boolean supportsFeature(List<String> features, String requiredFeature) {
        if (features == null) {
            return false;
        }
        for (String feature : features) {
            if (StringUtils.equalsIgnoreCase(feature, requiredFeature)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresVmwareGuestPreparation(DrExecutionContext context, FtctlDrActionCommand.Action action) {
        return context != null && context.getPlan() != null
                && StringUtils.equalsIgnoreCase(context.getPlan().getDirection(), "VMWARE_TO_KVM")
                && (action == FtctlDrActionCommand.Action.TEST_PREPARE || action == FtctlDrActionCommand.Action.FAILOVER);
    }

    private String firstMissingFeature(List<String> features, String... requiredFeatures) {
        for (String requiredFeature : requiredFeatures) {
            if (!supportsFeature(features, requiredFeature)) {
                return requiredFeature;
            }
        }
        return null;
    }

    private DrAdapterResult probeAcceptedStatus(DrExecutionContext context, FtctlDrActionCommand.Action action, long hostId) {
        try {
            FtctlDrStatusCommand statusCommand = new FtctlDrStatusCommand(context.getPlan().getUuid(), context.getRun().getUuid());
            statusCommand.setWait(10);
            Answer answer = agentManager.easySend(hostId, statusCommand);
            if (!(answer instanceof FtctlDrStatusAnswer)) {
                return null;
            }
            FtctlDrStatusAnswer status = (FtctlDrStatusAnswer) answer;
            if (!isAcceptedStatus(status)) {
                return null;
            }
            JsonObject details = buildExecutionDetails(context, action, hostId);
            details.add("statusProbe", GSON.toJsonTree(redactedStatus(status)));
            String externalJobRef = StringUtils.defaultIfBlank(stringValue(parseObject(status.getStatusJson()), "external_job_ref"), context.getRun().getUuid());
            return DrAdapterResult.accepted("FTCTL_DR action " + action + " accepted by Agent after timeout status probe",
                    GSON.toJson(details), externalJobRef);
        } catch (RuntimeException e) {
            LOGGER.debug("Ignoring FTCTL_DR status probe failure for run {} after dispatch timeout: {}",
                    context.getRun().getId(), e.getMessage());
            return null;
        }
    }

    private DrAdapterResult toAdapterResult(DrExecutionContext context, FtctlDrActionCommand.Action action, long hostId, Answer answer) {
        JsonObject details = buildExecutionDetails(context, action, hostId);
        if (!(answer instanceof FtctlDrActionAnswer)) {
            String message = answer != null ? answer.getDetails() : "Agent returned no FTCTL_DR answer";
            details.addProperty("answerType", answer != null ? answer.getClass().getName() : null);
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_ACTION_FAILED, message, GSON.toJson(details));
        }

        FtctlDrActionAnswer actionAnswer = (FtctlDrActionAnswer) answer;
        details.add("agentAnswer", GSON.toJsonTree(redactedAnswer(actionAnswer)));
        if (!actionAnswer.getResult()) {
            JsonObject lockPayload = retryableLockPayload(actionAnswer);
            if (lockPayload != null) {
                details.add("retryableLock", lockPayload);
                Integer retryAfterSeconds = integerValue(lockPayload, "retry_after_sec");
                String holderCommand = stringValue(lockPayload, "holder_command");
                String message = "FTCTL_DR engine is busy";
                if (StringUtils.isNotBlank(holderCommand)) {
                    message += " while " + holderCommand + " is holding the lock";
                }
                return DrAdapterResult.retryable(DrConstants.ERROR_ENGINE_BUSY_RETRYABLE, message, GSON.toJson(details), retryAfterSeconds);
            }
            String errorCode = StringUtils.defaultIfBlank(actionAnswer.getErrorCode(), DrConstants.ERROR_ENGINE_ACTION_FAILED);
            String message = StringUtils.defaultIfBlank(actionAnswer.getDetails(), "FTCTL_DR Agent command failed");
            return DrAdapterResult.failure(errorCode, message, GSON.toJson(details));
        }

        String result = StringUtils.lowerCase(StringUtils.defaultString(actionAnswer.getFtctlResult()), Locale.ROOT);
        boolean accepted = Boolean.TRUE.equals(actionAnswer.getAccepted())
                || StringUtils.equalsAny(result, "accepted", "ok", "success", "delegated", "warn")
                || Integer.valueOf(0).equals(actionAnswer.getExitCode());
        if (!accepted) {
            String message = "FTCTL_DR Agent command did not return an accepted result";
            return DrAdapterResult.failure(DrConstants.ERROR_ENGINE_ACTION_FAILED, message, GSON.toJson(details));
        }

        String externalJobRef = StringUtils.defaultIfBlank(actionAnswer.getExternalJobRef(), context.getRun().getUuid());
        if (isTerminalControlAction(context.getRun())) {
            return DrAdapterResult.success("FTCTL_DR control action " + action + " accepted by Agent", GSON.toJson(details));
        }
        return DrAdapterResult.accepted("FTCTL_DR action " + action + " accepted by Agent", GSON.toJson(details), externalJobRef);
    }

    private boolean isAcceptedStatus(FtctlDrStatusAnswer status) {
        if (status == null) {
            return false;
        }
        JsonObject runtime = parseObject(status.getStatusJson());
        String result = StringUtils.lowerCase(StringUtils.defaultIfBlank(status.getFtctlResult(), stringValue(runtime, "result")), Locale.ROOT);
        String state = StringUtils.upperCase(StringUtils.defaultIfBlank(status.getState(), stringValue(runtime, "state")), Locale.ROOT);
        return status.getResult()
                && (booleanValue(runtime, "accepted")
                || StringUtils.equalsAny(result, "accepted", "ok", "success", "delegated", "warn")
                || StringUtils.equalsAny(state, "SYNCING", "RUNNING", "READY", "TARGET_READY", "PAUSED", "TESTING",
                        "TEST_ARTIFACTS_READY", "ARTIFACTS_READY"));
    }

    private FtctlDrActionCommand.Action resolveAction(DrRunVO run) {
        String runType = StringUtils.upperCase(run.getRunType(), Locale.ROOT);
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_SYNC)) {
            return FtctlDrActionCommand.Action.SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_PAUSE_SYNC)) {
            return FtctlDrActionCommand.Action.PAUSE_SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_RESUME_SYNC)) {
            return FtctlDrActionCommand.Action.RESUME_SYNC;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_FAILOVER)) {
            return FtctlDrActionCommand.Action.TEST_PREPARE;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_TEST_CLEANUP)) {
            return FtctlDrActionCommand.Action.TEST_ARTIFACT_CLEANUP;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILOVER)) {
            return FtctlDrActionCommand.Action.FAILOVER;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_FAILBACK)) {
            return FtctlDrActionCommand.Action.FAILBACK;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_REPROTECT)) {
            return FtctlDrActionCommand.Action.REPROTECT;
        }
        if (StringUtils.equals(runType, DrConstants.RUN_TYPE_RELEASE)) {
            return FtctlDrActionCommand.Action.RELEASE;
        }
        return null;
    }

    private boolean isTerminalControlAction(DrRunVO run) {
        return StringUtils.equalsAny(StringUtils.upperCase(run.getRunType(), Locale.ROOT),
                DrConstants.RUN_TYPE_PAUSE_SYNC, DrConstants.RUN_TYPE_RESUME_SYNC,
                DrConstants.RUN_TYPE_TEST_CLEANUP, DrConstants.RUN_TYPE_RELEASE);
    }

    private Long resolveCoordinatorHostId(DrPlanVO plan) {
        if (plan.getCoordinatorWorkerHostId() != null) {
            return plan.getCoordinatorWorkerHostId();
        }
        if (plan.getSourceWorkerHostId() != null) {
            return plan.getSourceWorkerHostId();
        }
        return plan.getTargetWorkerHostId();
    }

    private String resolveHostUuid(Long hostId) {
        if (hostId == null) {
            return null;
        }
        HostVO host = hostDao != null ? hostDao.findById(hostId) : null;
        return host != null && StringUtils.isNotBlank(host.getUuid()) ? host.getUuid() : String.valueOf(hostId);
    }

    private String buildProfileJson(DrPlanVO plan, DrRunVO run, JsonObject request) {
        JsonObject profile = new JsonObject();
        profile.addProperty("version", 1);
        profile.addProperty("engine", DrConstants.ENGINE_TYPE_FTCTL_DR);
        profile.addProperty("planUuid", plan.getUuid());
        profile.addProperty("runUuid", run.getUuid());
        profile.addProperty("direction", plan.getDirection());
        profile.addProperty("activeSide", plan.getActiveSide());
        profile.addProperty("rpoTargetSeconds", plan.getRpoSeconds());
        profile.addProperty("rtoTargetSeconds", plan.getRtoSeconds());
        DrSiteVO sourceSite = drSiteDao != null ? drSiteDao.findById(plan.getSourceSiteId()) : null;
        DrSiteVO targetSite = drSiteDao != null ? drSiteDao.findById(plan.getTargetSiteId()) : null;
        JsonObject mapping = parseObject(plan.getMappingJson());
        profile.add("source", buildEndpoint(plan.getDirection(), true, sourceSite, plan.getSourceVmId(), plan.getSourceExternalRef()));
        profile.add("target", buildTargetEndpoint(plan.getDirection(), targetSite, mapping));
        profile.add("credentials", buildCredentials(plan, sourceSite, targetSite));
        profile.add("workers", buildWorkers(plan));
        profile.add("policy", parseObject(plan.getPolicyJson()));
        profile.add("mapping", mapping);
        profile.add("schedule", parseObject(plan.getScheduleJson()));
        profile.add("quiescePolicy", parseObject(plan.getQuiescePolicyJson()));
        profile.add("request", request);
        return GSON.toJson(profile);
    }

    private JsonObject buildTargetEndpoint(String direction, DrSiteVO targetSite, JsonObject mapping) {
        JsonObject endpoint = buildEndpoint(direction, false, targetSite, null, null);
        JsonObject targetMapping = objectAt(mapping, "target");
        for (Map.Entry<String, JsonElement> entry : targetMapping.entrySet()) {
            if (!endpoint.has(entry.getKey()) && entry.getValue() != null && !entry.getValue().isJsonNull()) {
                endpoint.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return endpoint;
    }

    private JsonObject buildEndpoint(String direction, boolean source, DrSiteVO site, Long vmId, String externalRef) {
        JsonObject endpoint = new JsonObject();
        boolean vmware = source ? StringUtils.startsWith(direction, "VMWARE_") : StringUtils.endsWith(direction, "_VMWARE");
        endpoint.addProperty("provider", vmware ? "VMWARE" : "ABLESTACK");
        endpoint.addProperty("driver", vmware ? (source ? "VMWARE_CBT" : "VMWARE_VDDK") : (source ? "KVM_QMP" : "ABLESTACK"));
        if (source && vmware) {
            JsonObject cbtPolicy = new JsonObject();
            cbtPolicy.addProperty("required", true);
            cbtPolicy.addProperty("autoEnable", true);
            cbtPolicy.addProperty("failIfPreExistingSnapshots", false);
            endpoint.add("cbtPolicy", cbtPolicy);
        }
        if (site != null) {
            endpoint.addProperty("siteId", site.getId());
            endpoint.addProperty("siteUuid", site.getUuid());
            endpoint.addProperty("siteName", site.getName());
            endpoint.addProperty("siteType", site.getSiteType());
            endpoint.addProperty("hypervisorType", site.getHypervisorType());
            endpoint.addProperty("endpoint", site.getEndpoint());
        }
        if (vmId != null) {
            endpoint.addProperty("vmId", vmId);
        }
        if (StringUtils.isNotBlank(externalRef)) {
            endpoint.addProperty("externalRef", externalRef);
        }
        return endpoint;
    }

    private JsonObject buildCredentials(DrPlanVO plan, DrSiteVO sourceSite, DrSiteVO targetSite) {
        JsonObject credentials = new JsonObject();
        addCredential(credentials, "source", sourceSite, plan, true);
        addCredential(credentials, "target", targetSite, plan, false);
        return credentials;
    }

    private void addCredential(JsonObject credentials, String key, DrSiteVO site, DrPlanVO plan, boolean source) {
        if (drSiteCredentialService == null || site == null) {
            return;
        }
        DrResolvedSiteCredential credential = drSiteCredentialService.resolveCredential(site);
        if (credential != null && credential.hasSecrets()) {
            JsonObject runtime = credential.toRuntimeJson();
            if (source && isVmwareSourcePlan(plan)) {
                enrichVmwareSourceCredential(runtime, plan);
            }
            credentials.add(key, runtime);
        }
    }

    private boolean isVmwareSourcePlan(DrPlanVO plan) {
        return plan != null && StringUtils.startsWithIgnoreCase(plan.getDirection(), "VMWARE_");
    }

    private void enrichVmwareSourceCredential(JsonObject credential, DrPlanVO plan) {
        Long hostId = resolveVmwareDataPlaneHostId(plan);
        if (hostId == null || credential == null) {
            return;
        }
        String libDir = hostDetailValue(hostId, Host.HOST_VDDK_LIB_DIR);
        if (StringUtils.isNotBlank(libDir)) {
            credential.addProperty("vddkLibdir", libDir);
        }
        String version = hostDetailValue(hostId, Host.HOST_VDDK_VERSION);
        if (StringUtils.isNotBlank(version)) {
            credential.addProperty("vddkVersion", version);
        }
        credential.addProperty("dataPlaneHostId", hostId);
        credential.addProperty("dataPlaneHostUuid", resolveHostUuid(hostId));
        enrichVmwareSourceThumbprint(credential);
    }

    private void enrichVmwareSourceThumbprint(JsonObject credential) {
        if (credential == null || booleanValue(credential, "tlsVerify")) {
            return;
        }
        String thumbprint = firstString(credential, "thumbprint", "tlsThumbprint");
        if (StringUtils.isNotBlank(thumbprint)) {
            credential.addProperty("thumbprint", thumbprint);
            if (StringUtils.isBlank(firstString(credential, "thumbprintSource"))) {
                credential.addProperty("thumbprintSource", "runtime");
            }
            return;
        }
        String endpoint = firstString(credential, "endpoint");
        if (StringUtils.isBlank(endpoint)) {
            credential.addProperty("thumbprintPresent", false);
            credential.addProperty("thumbprintSource", "missing-endpoint");
            return;
        }
        try {
            thumbprint = DrSiteProbeSupport.fetchSha1Thumbprint(endpoint, VCENTER_THUMBPRINT_TIMEOUT_MS);
            if (StringUtils.isNotBlank(thumbprint)) {
                credential.addProperty("thumbprint", thumbprint);
                credential.addProperty("thumbprintPresent", true);
                credential.addProperty("thumbprintSource", "backend-auto");
                return;
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to resolve vCenter thumbprint for DR source endpoint {}: {}", endpoint, e.getMessage());
        }
        credential.addProperty("thumbprintPresent", false);
        credential.addProperty("thumbprintSource", "backend-unresolved");
    }

    private Long resolveVmwareDataPlaneHostId(DrPlanVO plan) {
        if (plan == null) {
            return null;
        }
        if (StringUtils.endsWithIgnoreCase(plan.getDirection(), "_KVM") && plan.getTargetWorkerHostId() != null) {
            return plan.getTargetWorkerHostId();
        }
        return resolveCoordinatorHostId(plan);
    }

    private String hostDetailValue(Long hostId, String name) {
        if (hostId == null || StringUtils.isBlank(name) || hostDetailsDao == null) {
            return null;
        }
        DetailVO detail = hostDetailsDao.findDetail(hostId, name);
        return detail != null ? StringUtils.trimToNull(detail.getValue()) : null;
    }

    private JsonObject buildWorkers(DrPlanVO plan) {
        JsonObject workers = new JsonObject();
        workers.addProperty("coordinator", resolveHostUuid(resolveCoordinatorHostId(plan)));
        workers.addProperty("source", resolveHostUuid(plan.getSourceWorkerHostId()));
        workers.addProperty("target", resolveHostUuid(plan.getTargetWorkerHostId()));
        return workers;
    }

    private DrAdapterResult validateVmwareCredentials(DrPlanVO plan, String direction) {
        if (drSiteDao == null || drSiteCredentialService == null) {
            return null;
        }
        if (StringUtils.startsWith(direction, "VMWARE_")) {
            DrSiteVO sourceSite = drSiteDao.findById(plan.getSourceSiteId());
            if (!drSiteCredentialService.hasUsableCredential(sourceSite)) {
                return DrAdapterResult.failure(DrConstants.ERROR_CREDENTIAL_INVALID,
                        "Source VMware DR site requires stored credentials", GSON.toJson(buildValidationDetails(plan)));
            }
        }
        if (StringUtils.endsWith(direction, "_VMWARE")) {
            DrSiteVO targetSite = drSiteDao.findById(plan.getTargetSiteId());
            if (!drSiteCredentialService.hasUsableCredential(targetSite)) {
                return DrAdapterResult.failure(DrConstants.ERROR_CREDENTIAL_INVALID,
                        "Target VMware DR site requires stored credentials", GSON.toJson(buildValidationDetails(plan)));
            }
        }
        return null;
    }

    private JsonObject buildValidationDetails(DrPlanVO plan) {
        JsonObject details = new JsonObject();
        details.addProperty("engineType", getEngineType());
        details.addProperty("engineBindingType", getEngineBindingType());
        if (plan != null) {
            details.addProperty("planId", plan.getId());
            details.addProperty("direction", plan.getDirection());
            details.addProperty("coordinatorWorkerHostId", plan.getCoordinatorWorkerHostId());
            details.addProperty("sourceWorkerHostId", plan.getSourceWorkerHostId());
            details.addProperty("targetWorkerHostId", plan.getTargetWorkerHostId());
        }
        details.addProperty("runtimeDispatchReady", true);
        details.addProperty("statusPollingReady", true);
        return details;
    }

    private JsonObject buildExecutionDetails(DrExecutionContext context, FtctlDrActionCommand.Action action, Long hostId) {
        JsonObject details = new JsonObject();
        details.addProperty("engineType", getEngineType());
        details.addProperty("engineBindingType", getEngineBindingType());
        details.addProperty("runType", context.getRun() != null ? context.getRun().getRunType() : null);
        details.addProperty("action", action != null ? action.name() : null);
        details.addProperty("agentHostId", hostId);
        details.addProperty("agentAcceptTimeoutSeconds", AGENT_ACCEPT_TIMEOUT_SECONDS);
        if (context.getPlan() != null) {
            details.addProperty("planId", context.getPlan().getId());
            details.addProperty("planUuid", context.getPlan().getUuid());
            details.addProperty("direction", context.getPlan().getDirection());
        }
        if (context.getRun() != null) {
            details.addProperty("runId", context.getRun().getId());
            details.addProperty("runUuid", context.getRun().getUuid());
            details.add("request", redactJson(requestJson(context.getRun())));
        }
        return details;
    }

    private JsonObject redactedAnswer(FtctlDrActionAnswer answer) {
        JsonObject object = new JsonObject();
        object.addProperty("action", answer.getAction() != null ? answer.getAction().name() : null);
        object.addProperty("planUuid", answer.getPlanUuid());
        object.addProperty("runUuid", answer.getRunUuid());
        object.addProperty("result", answer.getFtctlResult());
        object.addProperty("accepted", answer.getAccepted());
        object.addProperty("state", answer.getState());
        object.addProperty("step", answer.getStep());
        object.addProperty("progress", answer.getProgress());
        object.addProperty("externalJobRef", answer.getExternalJobRef());
        object.addProperty("eventsOffset", answer.getEventsOffset());
        object.addProperty("errorCode", answer.getErrorCode());
        object.addProperty("exitCode", answer.getExitCode());
        object.add("status", redactJson(parseElement(answer.getStatusJson())));
        return object;
    }

    private JsonObject redactedCapabilities(Answer answer) {
        JsonObject object = new JsonObject();
        if (!(answer instanceof FtctlDrCapabilitiesAnswer)) {
            object.addProperty("result", answer != null && answer.getResult());
            object.addProperty("details", answer != null ? answer.getDetails() : null);
            object.addProperty("answerType", answer != null ? answer.getClass().getName() : null);
            return object;
        }
        FtctlDrCapabilitiesAnswer capabilities = (FtctlDrCapabilitiesAnswer) answer;
        object.addProperty("result", capabilities.getResult());
        object.addProperty("details", capabilities.getDetails());
        object.addProperty("planUuid", capabilities.getPlanUuid());
        object.addProperty("runUuid", capabilities.getRunUuid());
        object.addProperty("ftctlVersion", capabilities.getFtctlVersion());
        object.addProperty("runtimeSchemaVersion", capabilities.getRuntimeSchemaVersion());
        object.addProperty("actionContractVersion", capabilities.getActionContractVersion());
        object.addProperty("actionCommandCodeSource", capabilities.getActionCommandCodeSource());
        object.addProperty("wrapperCodeSource", capabilities.getWrapperCodeSource());
        object.add("supportedActions", GSON.toJsonTree(capabilities.getSupportedActions()));
        object.add("supportedCliCommands", GSON.toJsonTree(capabilities.getSupportedCliCommands()));
        object.add("supportedFeatures", GSON.toJsonTree(capabilities.getSupportedFeatures()));
        object.add("missingActions", GSON.toJsonTree(capabilities.getMissingActions()));
        object.add("missingCliCommands", GSON.toJsonTree(capabilities.getMissingCliCommands()));
        return object;
    }

    private JsonObject redactedStatus(FtctlDrStatusAnswer status) {
        JsonObject object = new JsonObject();
        object.addProperty("planUuid", status.getPlanUuid());
        object.addProperty("runUuid", status.getRunUuid());
        object.addProperty("result", status.getFtctlResult());
        object.addProperty("state", status.getState());
        object.addProperty("step", status.getStep());
        object.addProperty("progress", status.getProgress());
        object.addProperty("eventsOffset", status.getEventsOffset());
        object.addProperty("errorCode", status.getErrorCode());
        object.addProperty("exitCode", status.getExitCode());
        object.add("status", redactJson(parseElement(status.getStatusJson())));
        return object;
    }

    private JsonObject requestJson(DrRunVO run) {
        if (run == null || StringUtils.isBlank(run.getRequestJson())) {
            return new JsonObject();
        }
        JsonElement parsed = parseElement(run.getRequestJson());
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }

    private JsonObject parseObject(String json) {
        JsonElement element = parseElement(json);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private JsonObject objectAt(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private JsonArray firstArray(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object != null ? object.get(key) : null;
            if (value != null && value.isJsonArray()) {
                return value.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object != null ? object.get(key) : null;
            if (value != null && value.isJsonPrimitive()) {
                String result = StringUtils.trimToNull(value.getAsString());
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    private String stringValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && !value.isJsonNull() && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && !value.isJsonNull() && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private Integer integerValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonObject retryableLockPayload(FtctlDrActionAnswer answer) {
        JsonObject payload = firstNonEmptyObject(answer.getStatusJson(), answer.getOutput(), answer.getDetails());
        if (payload.entrySet().isEmpty()) {
            return null;
        }
        String result = StringUtils.lowerCase(stringValue(payload, "result"), Locale.ROOT);
        String command = StringUtils.lowerCase(stringValue(payload, "command"), Locale.ROOT);
        boolean retryable = booleanValue(payload, "retryable");
        boolean locked = StringUtils.equals(result, "locked") || StringUtils.contains(command, "lock")
                || StringUtils.equalsIgnoreCase(stringValue(payload, "error_code"), "locked");
        return retryable && locked ? payload : null;
    }

    private JsonObject firstNonEmptyObject(String... values) {
        for (String value : values) {
            JsonObject object = parseObject(value);
            if (object != null && !object.entrySet().isEmpty()) {
                return object;
            }
        }
        return new JsonObject();
    }

    private JsonElement parseElement(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonElement redactJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new JsonObject();
        }
        if (element.isJsonObject()) {
            JsonObject redacted = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (isSecretKey(entry.getKey())) {
                    redacted.addProperty(entry.getKey(), "REDACTED");
                } else {
                    redacted.add(entry.getKey(), redactJson(entry.getValue()));
                }
            }
            return redacted;
        }
        if (element.isJsonArray()) {
            JsonArray redacted = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                redacted.add(redactJson(item));
            }
            return redacted;
        }
        return element.deepCopy();
    }

    private boolean isSecretKey(String key) {
        String lower = StringUtils.lowerCase(key, Locale.ROOT);
        return StringUtils.contains(lower, "password")
                || StringUtils.contains(lower, "secret")
                || StringUtils.contains(lower, "token")
                || StringUtils.contains(lower, "apikey")
                || StringUtils.contains(lower, "api_key")
                || StringUtils.contains(lower, "credential");
    }

    private Map<String, String> toStringMap(JsonObject object) {
        Map<String, String> values = new java.util.HashMap<String, String>();
        if (object == null) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values.put(entry.getKey(), entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString());
        }
        return values;
    }

    private String requestString(JsonObject request, String key) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsString() : null;
    }

    private Long requestLong(JsonObject request, String key) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsLong() : null;
    }

    private boolean requestBoolean(JsonObject request, String key, boolean defaultValue) {
        JsonElement value = request.get(key);
        return value != null && !value.isJsonNull() ? value.getAsBoolean() : defaultValue;
    }
}
