// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information.
package com.cloud.dr;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.cloudstack.managed.context.ManagedContextRunnable;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FtctlDrActionAnswer;
import com.cloud.agent.api.FtctlDrActionCommand;
import com.cloud.agent.api.FtctlDrStatusAnswer;
import com.cloud.agent.api.FtctlDrStatusCommand;
import com.cloud.dr.dao.DrEventDao;
import com.cloud.dr.dao.DrCutoverSessionDao;
import com.cloud.dr.dao.DrFailbackSessionDao;
import com.cloud.dr.dao.DrPlanDao;
import com.cloud.dr.dao.DrPlanViewCacheDao;
import com.cloud.dr.dao.DrReplicaDao;
import com.cloud.dr.dao.DrRunDao;
import com.cloud.dr.dao.DrRunStepDao;
import com.cloud.dr.dao.DrSiteDao;
import com.cloud.dr.inventory.DrMoldInventoryClient;
import com.cloud.dr.inventory.DrVmwareInventoryClient;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.UserVmDao;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DrFailbackLifecycleServiceImpl extends ManagerBase implements DrFailbackLifecycleService {
    private static final Gson GSON = new Gson();
    private static final String DATA_READY = "DATA_READY";
    private static final String COMMIT_VERIFYING = "COMMIT_VERIFYING";
    private static final String PROTECTION_RESUMING = "PROTECTION_RESUMING";
    private static final String COMPLETED = "COMPLETED";
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String REJECTED = "REJECTED";
    private static final String ROLLED_BACK = "ROLLED_BACK";
    private static final long RECONCILE_INTERVAL_SECONDS = 5L;
    private static final int RECONCILE_BATCH_SIZE = 25;
    private static final int GLOBAL_LOCK_TIMEOUT_SECONDS = 1;

    @Inject private DrFailbackSessionDao drFailbackSessionDao;
    @Inject private DrPlanDao drPlanDao;
    @Inject private DrRunDao drRunDao;
    @Inject private DrReplicaDao drReplicaDao;
    @Inject private DrPlanViewCacheDao drPlanViewCacheDao;
    @Inject private DrSiteDao drSiteDao;
    @Inject private DrSiteCredentialService drSiteCredentialService;
    @Inject private DrMoldInventoryClient drMoldInventoryClient;
    @Inject private DrVmwareInventoryClient drVmwareInventoryClient;
    @Inject private UserVmDao userVmDao;
    @Inject private UserVmManager userVmManager;
    @Inject private AgentManager agentManager;
    @Inject private DrEventDao drEventDao;
    @Inject private DrCutoverSessionDao drCutoverSessionDao;
    @Inject private DrRunStepDao drRunStepDao;
    @Inject private DrSourceIsolationPreflightService drSourceIsolationPreflightService;
    @Inject private DrFailbackDataGateService drFailbackDataGateService;

    private final Set<Long> inFlightRuns = ConcurrentHashMap.newKeySet();
    private ExecutorService executor;
    private ScheduledExecutorService reconciler;

    @Override
    public boolean start() {
        executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("DrFailbackLifecycle"));
        reconciler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("DrFailbackLifecycleReconciler"));
        reconciler.scheduleWithFixedDelay(new ReconcileTask(), RECONCILE_INTERVAL_SECONDS,
                RECONCILE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public boolean stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (reconciler != null) {
            reconciler.shutdownNow();
            reconciler = null;
        }
        return true;
    }

    private final class ReconcileTask extends ManagedContextRunnable {
        @Override
        protected void runInContext() {
            GlobalLock lock = GlobalLock.getInternLock("DrFailbackLifecycleReconciler");
            try {
                if (lock.lock(GLOBAL_LOCK_TIMEOUT_SECONDS)) {
                    try {
                        reconcilePendingSessions();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (RuntimeException e) {
                logger.warn("Failed to reconcile pending DR failback lifecycles", e);
            } finally {
                lock.releaseRef();
            }
        }
    }

    private void reconcilePendingSessions() {
        Date probeBefore = new Date(System.currentTimeMillis() - RECONCILE_INTERVAL_SECONDS * 1000L);
        List<DrFailbackSessionVO> sessions =
                drFailbackSessionDao.listReconcileCandidates(probeBefore, RECONCILE_BATCH_SIZE);
        for (DrFailbackSessionVO session : sessions) {
            submitLifecycle(session.getRunId());
        }
    }

    @Override
    public DrFailbackSessionVO reconcile(DrPlanVO plan, DrRunVO run, JsonObject runtime) {
        if (plan == null || run == null || !StringUtils.equalsIgnoreCase(run.getRunType(), DrConstants.RUN_TYPE_FAILBACK)) {
            return null;
        }
        String engineSessionId = stringValue(runtime, "failback_session_id");
        String runtimeState = upper(stringValue(runtime, "state"));
        DrFailbackSessionVO session = drFailbackSessionDao.findActiveByRunId(run.getId());
        if (session == null && StringUtils.isNotBlank(engineSessionId)) {
            session = new DrFailbackSessionVO(plan.getId(), run.getId(), engineSessionId,
                    StringUtils.equals(runtimeState, "FAILBACK_DATA_READY") ? DATA_READY : "REVERSE_SYNCING");
            session.setCheckpointSequence(longValue(runtime, "failback_restore_point_sequence"));
            initializeResumeCheckpointContract(session);
            session.setAuthorityGeneration(authorityGeneration(session, run));
            session.setTargetPowerState(defaultValue(stringValue(runtime, "target_power_state"), "POWERED_ON"));
            session.setSourcePowerState(defaultValue(stringValue(runtime, "source_power_state"), "POWERED_OFF"));
            session.setEngineAckState(defaultValue(stringValue(runtime, "engine_ack_state"), "PENDING"));
            session.setCommitOutcome(defaultValue(stringValue(runtime, "failback_commit_outcome"), "PENDING"));
            session.setSchedulerGeneration(longValue(runtime, "control_generation"));
            session.setSchedulerAckGeneration(longValue(runtime, "control_ack_generation"));
            session.setSchedulerState(stringValue(runtime, "scheduler_state"));
            session.setRollbackState(defaultValue(stringValue(runtime, "rollback_state"), "NONE"));
            session.setRollbackGeneration(longValue(runtime, "rollback_generation"));
            updateDataEvidence(session, runtime);
            session.setDetailsJson(GSON.toJson(runtime));
            if (StringUtils.equals(session.getState(), DATA_READY)) {
                session.setDataReadyAt(new Date());
            }
            session = drFailbackSessionDao.persist(session);
            recordEvent(plan, run, "FAILBACK_DATA_READY", "Reverse replication is durable; Cloud lifecycle commit is queued");
        } else if (session != null) {
            session.setDetailsJson(GSON.toJson(runtime));
            updateRuntimeEvidence(session, runtime);
            updateDataEvidence(session, runtime);
            if (StringUtils.equals(runtimeState, "FAILBACK_DATA_READY")
                    && StringUtils.equalsAnyIgnoreCase(session.getState(), "REVERSE_SYNCING", DATA_READY)) {
                session.setState(DATA_READY);
                if (session.getDataReadyAt() == null) {
                    session.setDataReadyAt(new Date());
                }
            }
            session.markUpdated();
            drFailbackSessionDao.update(session.getId(), session);
        }
        if (session == null) {
            return null;
        }

        String commitOutcome = upper(defaultValue(stringValue(runtime, "failback_commit_outcome"),
                session.getCommitOutcome()));
        if (StringUtils.equals(commitOutcome, ACKNOWLEDGED)
                && !StringUtils.equals(session.getState(), PROTECTION_RESUMING)
                && !isTerminal(session.getState())) {
            if (cloudPowerStatesMatch(plan)) {
                acknowledgeCommit(plan, run, session, runtime);
            } else {
                markCommitVerifying(plan, session, null);
                submitLifecycle(run.getId());
            }
        } else if (StringUtils.equals(commitOutcome, ROLLED_BACK) && !isTerminal(session.getState())) {
            session.setState("ABORTED");
            session.setRollbackState("COMPLETED");
            session.setRollbackVerifiedAt(new Date());
            session.markUpdated();
            drFailbackSessionDao.update(session.getId(), session);
            preserveTargetAuthority(plan);
        } else if (StringUtils.equals(session.getState(), DATA_READY)
                || StringUtils.equals(session.getState(), COMMIT_VERIFYING)) {
            submitLifecycle(run.getId());
        } else if (StringUtils.equals(session.getState(), PROTECTION_RESUMING)
                && protectionResumed(plan, session, runtime)) {
            completeLifecycle(plan, run, session, runtime);
        }
        return drFailbackSessionDao.findActiveByRunId(run.getId());
    }

    private void submitLifecycle(long runId) {
        if (executor == null || !inFlightRuns.add(runId)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    executeLifecycle(runId);
                } finally {
                    inFlightRuns.remove(runId);
                }
            });
        } catch (RejectedExecutionException e) {
            inFlightRuns.remove(runId);
        }
    }

    private void executeLifecycle(long runId) {
        DrRunVO run = drRunDao.findById(runId);
        DrFailbackSessionVO session = drFailbackSessionDao.findActiveByRunId(runId);
        if (run == null || session == null || isTerminal(session.getState())) {
            return;
        }
        DrPlanVO plan = drPlanDao.findById(run.getPlanId());
        if (plan == null) {
            failSession(session, "DR_FAILBACK_PLAN_REMOVED", "DR plan was removed during failback");
            return;
        }
        session.setLastProbeAt(new Date());
        session.markUpdated();
        drFailbackSessionDao.update(session.getId(), session);
        if (StringUtils.equals(session.getState(), COMMIT_VERIFYING)) {
            verifyCommitOutcome(plan, run, session);
            return;
        }
        if (StringUtils.equals(session.getState(), PROTECTION_RESUMING)) {
            JsonObject authorityRuntime = fetchStatusRuntime(plan, run,
                    FtctlDrStatusCommand.StatusScope.PLAN_AUTHORITY);
            if (protectionResumed(plan, session, authorityRuntime)) {
                completeLifecycle(plan, run, session, authorityRuntime);
            }
            return;
        }
        DrFailbackDataGateResult dataGate = drFailbackDataGateService.validate(plan, run, session);
        if (!dataGate.isReady()) {
            failSession(session, dataGate.getErrorCode(), dataGate.getMessage());
            recordEvent(plan, run, "FAILBACK_DATA_GATE_BLOCKED", dataGate.getMessage());
            return;
        }
        DrSourceIsolationPreflightResult transitionPreflight =
                drSourceIsolationPreflightService.validate(plan, run, DrConstants.RUN_TYPE_FAILBACK);
        if (!transitionPreflight.isReady()) {
            failSession(session, transitionPreflight.getErrorCode(), transitionPreflight.getMessage());
            recordEvent(plan, run, "FAILBACK_SOURCE_ISOLATION_PREFLIGHT_FAILED",
                    transitionPreflight.getMessage());
            return;
        }
        boolean targetStopped = false;
        boolean sourceStarted = false;
        boolean commitAttempted = false;
        try {
            updateSession(session, "TARGET_STOPPING", null);
            String targetState = ensureTargetPowerState(plan, false);
            targetStopped = true;
            session.setTargetPowerState(targetState);
            session.setTargetStoppedAt(new Date());
            updateSession(session, "TARGET_STOPPED", null);

            updateSession(session, "SOURCE_STARTING", null);
            String sourceState = ensureSourcePowerState(plan, true);
            sourceStarted = true;
            session.setSourcePowerState(sourceState);
            session.setSourcePoweredOnAt(new Date());
            updateSession(session, "SOURCE_BOOT_VALIDATING", null);
            if (!StringUtils.equals(sourceState, "POWERED_ON")) {
                throw new CloudRuntimeException("Source VM did not reach POWERED_ON");
            }
            session.setBootValidationState(validateSourceBootState(plan));
            session.setBootValidatedAt(new Date());
            session.setCommitAttemptId(run.getUuid() + ":" + session.getAuthorityGeneration());
            session.setCommitOutcome("PENDING");
            session.setCommitRequestedAt(new Date());
            initializeResumeCheckpointContract(session);
            updateSession(session, "AUTHORITY_COMMITTING", null);

            commitAttempted = true;
            Answer answer = sendEngineCommand(plan, run, session, FtctlDrActionCommand.Action.FAILBACK_COMMIT);
            String outcome = commitOutcome(answer);
            if (StringUtils.equals(outcome, ACKNOWLEDGED)) {
                if (!cloudPowerStatesMatch(plan)) {
                    markCommitVerifying(plan, session, answer);
                    return;
                }
                acknowledgeCommit(plan, run, session, answerPayload(answer));
                return;
            }
            if (StringUtils.equals(outcome, UNKNOWN)) {
                markCommitVerifying(plan, session, answer);
                return;
            }
            throw new CloudRuntimeException(answer != null ? answer.getDetails()
                    : "Agent returned no failback commit acknowledgement");
        } catch (Exception e) {
            if (commitAttempted && isUncertainFailure(e.getMessage())) {
                markCommitVerifying(plan, session, null);
                return;
            }
            boolean rolledBack = rollbackFailback(plan, run, session, targetStopped, sourceStarted);
            if (rolledBack) {
                session.setErrorCode("DR_FAILBACK_LIFECYCLE_REJECTED");
                session.setErrorMessage(StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
                session.markUpdated();
                drFailbackSessionDao.update(session.getId(), session);
            } else {
                session.setErrorCode("DR_FAILBACK_ROLLBACK_FAILED");
                session.setErrorMessage(StringUtils.defaultIfBlank(session.getErrorMessage(), e.getMessage()));
                session.markUpdated();
                drFailbackSessionDao.update(session.getId(), session);
            }
            recordEvent(plan, run, "FAILBACK_LIFECYCLE_FAILED", session.getErrorMessage());
        }
    }

    private void updateDataEvidence(DrFailbackSessionVO session, JsonObject runtime) {
        if (runtime == null || (!runtime.has("provider_pair") && !runtime.has("tracker_state")
                && !runtime.has("writer_state"))) {
            return;
        }
        session.setReplicationDirection(defaultValue(stringValue(runtime, "reverse_direction"),
                stringValue(runtime, "replication_direction")));
        session.setProviderPair(stringValue(runtime, "provider_pair"));
        session.setBaselineGeneration(longValue(runtime, "baseline_generation"));
        session.setBaselineState(stringValue(runtime, "baseline_state"));
        session.setTrackerState(stringValue(runtime, "tracker_state"));
        session.setWriterState(stringValue(runtime, "writer_state"));
        session.setTargetWritten(booleanValue(runtime, "target_written"));
        session.setWriteVerified(booleanValue(runtime, "write_verified"));
        session.setGuestCompatibilityState(defaultValue(stringValue(runtime, "reverse_guest_compatibility_state"),
                stringValue(runtime, "guest_compatibility_state")));
    }

    private String ensureTargetPowerState(DrPlanVO plan, boolean poweredOn) throws Exception {
        DrReplicaVO replica = firstReplica(plan.getId());
        if (replica == null) {
            throw new CloudRuntimeException("DR target replica was not found");
        }
        UserVmVO localVm = replica.getTargetVmId() != null ? userVmDao.findById(replica.getTargetVmId()) : null;
        if (localVm != null && localVm.getRemoved() == null) {
            return ensureLocalPowerState(localVm, plan.getTargetWorkerHostId(), poweredOn);
        }
        DrSiteVO site = drSiteDao.findById(plan.getTargetSiteId());
        return ensureRemotePowerState(site, replica.getTargetExternalRef(), poweredOn);
    }

    private String ensureSourcePowerState(DrPlanVO plan, boolean poweredOn) throws Exception {
        UserVmVO localVm = plan.getSourceVmId() != null ? userVmDao.findById(plan.getSourceVmId()) : null;
        if (localVm != null && localVm.getRemoved() == null) {
            return ensureLocalPowerState(localVm, plan.getSourceWorkerHostId(), poweredOn);
        }
        DrSiteVO site = drSiteDao.findById(plan.getSourceSiteId());
        return ensureRemotePowerState(site, plan.getSourceExternalRef(), poweredOn);
    }

    private String validateSourceBootState(DrPlanVO plan) {
        DrSiteVO sourceSite = drSiteDao.findById(plan.getSourceSiteId());
        if (sourceSite != null && (StringUtils.equalsAnyIgnoreCase(sourceSite.getHypervisorType(), "VMWARE", "VMware")
                || StringUtils.equalsIgnoreCase(sourceSite.getSiteType(), "VMWARE_DIRECT"))) {
            try (DrResolvedSiteCredential credential = drSiteCredentialService.resolveCredential(sourceSite)) {
                return drVmwareInventoryClient.validateVirtualMachineGuestBoot(credential, plan.getSourceExternalRef());
            }
        }
        return "POWER_STATE_VALIDATED";
    }

    private String ensureLocalPowerState(UserVmVO vm, Long hostId, boolean poweredOn)
            throws ConcurrentOperationException, InsufficientCapacityException,
            ResourceAllocationException, ResourceUnavailableException {
        UserVmVO current = userVmDao.findById(vm.getId());
        if (poweredOn && current.getState() != VirtualMachine.State.Running) {
            userVmManager.startVirtualMachine(current.getId(), hostId,
                    new HashMap<VirtualMachineProfile.Param, Object>(), null);
        } else if (!poweredOn && current.getState() != VirtualMachine.State.Stopped) {
            userVmManager.stopVirtualMachine(current.getId(), true);
        }
        current = userVmDao.findById(vm.getId());
        String state = current != null && current.getState() == VirtualMachine.State.Running
                ? "POWERED_ON" : current != null && current.getState() == VirtualMachine.State.Stopped
                ? "POWERED_OFF" : "UNKNOWN";
        String expected = poweredOn ? "POWERED_ON" : "POWERED_OFF";
        if (!StringUtils.equals(state, expected)) {
            throw new CloudRuntimeException("Cloud VM did not reach " + expected + ": " + vm.getUuid());
        }
        return state;
    }

    private String ensureRemotePowerState(DrSiteVO site, String vmRef, boolean poweredOn) {
        if (site == null || StringUtils.isBlank(vmRef)) {
            throw new CloudRuntimeException("Remote DR site or VM reference is missing");
        }
        try (DrResolvedSiteCredential credential = drSiteCredentialService.resolveCredential(site)) {
            if (StringUtils.equalsAnyIgnoreCase(site.getHypervisorType(), "VMWARE", "VMware")
                    || StringUtils.equalsIgnoreCase(site.getSiteType(), "VMWARE_DIRECT")) {
                return drVmwareInventoryClient.ensureVirtualMachinePowerState(credential, vmRef, poweredOn);
            }
            return drMoldInventoryClient.ensureVirtualMachinePowerState(credential, vmRef, poweredOn);
        }
    }

    protected boolean cloudPowerStatesMatch(DrPlanVO plan) {
        try {
            return StringUtils.equals(readSourcePowerState(plan), "POWERED_ON")
                    && StringUtils.equals(readTargetPowerState(plan), "POWERED_OFF");
        } catch (RuntimeException e) {
            logger.warn(String.format("Could not verify Cloud failback authority for DR plan %s",
                    plan != null ? plan.getId() : null), e);
            return false;
        }
    }

    private String readTargetPowerState(DrPlanVO plan) {
        DrReplicaVO replica = firstReplica(plan.getId());
        if (replica == null) {
            throw new CloudRuntimeException("DR target replica was not found");
        }
        UserVmVO localVm = replica.getTargetVmId() != null ? userVmDao.findById(replica.getTargetVmId()) : null;
        if (localVm != null && localVm.getRemoved() == null) {
            return localPowerState(localVm);
        }
        DrSiteVO site = drSiteDao.findById(plan.getTargetSiteId());
        return readRemotePowerState(site, replica.getTargetExternalRef());
    }

    private String readSourcePowerState(DrPlanVO plan) {
        UserVmVO localVm = plan.getSourceVmId() != null ? userVmDao.findById(plan.getSourceVmId()) : null;
        if (localVm != null && localVm.getRemoved() == null) {
            return localPowerState(localVm);
        }
        DrSiteVO site = drSiteDao.findById(plan.getSourceSiteId());
        return readRemotePowerState(site, plan.getSourceExternalRef());
    }

    private String localPowerState(UserVmVO vm) {
        UserVmVO current = userVmDao.findById(vm.getId());
        if (current == null || current.getRemoved() != null) {
            return "UNKNOWN";
        }
        if (current.getState() == VirtualMachine.State.Running) {
            return "POWERED_ON";
        }
        if (current.getState() == VirtualMachine.State.Stopped) {
            return "POWERED_OFF";
        }
        return "UNKNOWN";
    }

    private String readRemotePowerState(DrSiteVO site, String vmRef) {
        if (site == null || StringUtils.isBlank(vmRef)) {
            return "UNKNOWN";
        }
        try (DrResolvedSiteCredential credential = drSiteCredentialService.resolveCredential(site)) {
            if (StringUtils.equalsAnyIgnoreCase(site.getHypervisorType(), "VMWARE", "VMware")
                    || StringUtils.equalsIgnoreCase(site.getSiteType(), "VMWARE_DIRECT")) {
                return drVmwareInventoryClient.getVirtualMachinePowerState(credential, vmRef);
            }
            return drMoldInventoryClient.getVirtualMachinePowerState(credential, vmRef);
        }
    }

    private Answer sendEngineCommand(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session,
            FtctlDrActionCommand.Action action) {
        return sendEngineCommand(plan, run, session, action, null);
    }

    private Answer sendEngineCommand(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session,
            FtctlDrActionCommand.Action action, String rollbackPhase) {
        Long hostId = firstNonNull(plan.getCoordinatorWorkerHostId(), plan.getTargetWorkerHostId(),
                plan.getSourceWorkerHostId());
        if (hostId == null) {
            throw new CloudRuntimeException("DR coordinator host is not configured");
        }
        FtctlDrActionCommand command = new FtctlDrActionCommand(action, plan.getUuid(), run.getUuid());
        command.setRole("coordinator");
        command.setWaitForCompletion(true);
        command.setContextParam("failbackSessionId", session.getEngineSessionId());
        command.setContextParam("checkpointSequence", String.valueOf(session.getCheckpointSequence()));
        command.setContextParam("authorityGeneration", String.valueOf(session.getAuthorityGeneration()));
        command.setContextParam("targetPowerState", session.getTargetPowerState());
        command.setContextParam("sourcePowerState", session.getSourcePowerState());
        command.setContextParam("bootValidationState", session.getBootValidationState());
        command.setResumeBaselineCheckpointSequence(session.getResumeBaselineCheckpointSequence());
        command.setMinimumCompletedCheckpointSequence(session.getRequiredPostFailbackCheckpointSequence());
        command.setForceImmediateCycle(action == FtctlDrActionCommand.Action.FAILBACK_COMMIT);
        if (StringUtils.isNotBlank(rollbackPhase)) {
            command.setContextParam("rollbackPhase", rollbackPhase);
        }
        return agentManager.easySend(hostId, command);
    }

    private JsonObject fetchStatusRuntime(DrPlanVO plan, DrRunVO run,
            FtctlDrStatusCommand.StatusScope scope) {
        Long hostId = firstNonNull(plan.getCoordinatorWorkerHostId(), plan.getTargetWorkerHostId(),
                plan.getSourceWorkerHostId());
        if (hostId == null) {
            return new JsonObject();
        }
        FtctlDrStatusCommand command = new FtctlDrStatusCommand(plan.getUuid(),
                scope == FtctlDrStatusCommand.StatusScope.OPERATION ? run.getUuid() : null, scope);
        command.setWait(5);
        Answer answer = agentManager.easySend(hostId, command);
        if (!(answer instanceof FtctlDrStatusAnswer)) {
            return new JsonObject();
        }
        JsonObject payload = parseObject(((FtctlDrStatusAnswer) answer).getStatusJson());
        return payload != null ? payload : new JsonObject();
    }

    private boolean rollbackFailback(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session,
            boolean targetStopped, boolean sourceStarted) {
        try {
            session.setRollbackState("FENCING");
            session.setRollbackRequestedAt(new Date());
            updateSession(session, "ROLLBACK_FENCING", null);
            Answer fenceAnswer = sendEngineCommand(plan, run, session,
                    FtctlDrActionCommand.Action.FAILBACK_ABORT, "prepare");
            if (fenceAnswer == null || !fenceAnswer.getResult()) {
                throw new CloudRuntimeException(fenceAnswer != null ? fenceAnswer.getDetails()
                        : "Agent returned no rollback fence acknowledgement");
            }
            JsonObject fencePayload = answerPayload(fenceAnswer);
            session.setRollbackState("FENCED");
            session.setRollbackGeneration(longValue(fencePayload, "rollback_generation"));
            session.setSchedulerGeneration(longValue(fencePayload, "control_generation"));
            session.setSchedulerAckGeneration(longValue(fencePayload, "control_ack_generation"));
            session.setSchedulerState(defaultValue(stringValue(fencePayload, "scheduler_state"), "STOPPED"));
            updateSession(session, "ROLLBACK_POWER_RESTORING", null);

            if (sourceStarted) {
                session.setSourcePowerState(ensureSourcePowerState(plan, false));
            }
            if (targetStopped) {
                session.setTargetPowerState(ensureTargetPowerState(plan, true));
            }
            Answer rollbackAnswer = sendEngineCommand(plan, run, session,
                    FtctlDrActionCommand.Action.FAILBACK_ABORT, "commit");
            if (rollbackAnswer == null || !rollbackAnswer.getResult()) {
                throw new CloudRuntimeException(rollbackAnswer != null ? rollbackAnswer.getDetails()
                        : "Agent returned no rollback commit acknowledgement");
            }
            session.setState("ABORTED");
            session.setCommitOutcome(ROLLED_BACK);
            session.setRollbackState("COMPLETED");
            session.setRollbackVerifiedAt(new Date());
            session.setEngineAckState("ABORTED");
            session.markUpdated();
            drFailbackSessionDao.update(session.getId(), session);
            preserveTargetAuthority(plan);
            return true;
        } catch (Exception e) {
            session.setState("ROLLBACK_FAILED");
            session.setRollbackState("FAILED");
            session.setErrorMessage("Failback failed and rollback was incomplete: " + e.getMessage());
            session.markUpdated();
            drFailbackSessionDao.update(session.getId(), session);
            preserveUncertainAuthority(plan);
            return false;
        }
    }

    private void verifyCommitOutcome(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session) {
        session.setLastProbeAt(new Date());
        Answer answer = sendEngineCommand(plan, run, session, FtctlDrActionCommand.Action.FAILBACK_COMMIT_STATUS);
        JsonObject payload = answerPayload(answer);
        String outcome = upper(stringValue(payload, "failback_commit_outcome"));
        if (StringUtils.equals(outcome, ACKNOWLEDGED)) {
            if (!cloudPowerStatesMatch(plan)) {
                session.setErrorCode("DR_FAILBACK_POWER_STATE_UNVERIFIED");
                session.setErrorMessage("Engine acknowledged failback, but source/target power states are not yet authoritative");
                session.markUpdated();
                drFailbackSessionDao.update(session.getId(), session);
                return;
            }
            acknowledgeCommit(plan, run, session, payload);
            return;
        }
        if (StringUtils.equals(outcome, REJECTED)) {
            rollbackFailback(plan, run, session, true, true);
            return;
        }
        session.setCommitOutcome(UNKNOWN);
        session.setEngineAckState(UNKNOWN);
        session.setState(COMMIT_VERIFYING);
        session.markUpdated();
        drFailbackSessionDao.update(session.getId(), session);
    }

    private void acknowledgeCommit(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session, JsonObject payload) {
        Date now = new Date();
        initializeResumeCheckpointContract(session);
        session.setCommitOutcome(ACKNOWLEDGED);
        session.setCommitVerifiedAt(now);
        session.setEngineAckState(ACKNOWLEDGED);
        session.setEngineAckAt(now);
        session.setSchedulerGeneration(longValue(payload, "control_generation"));
        session.setSchedulerAckGeneration(longValue(payload, "control_ack_generation"));
        session.setSchedulerState(defaultValue(stringValue(payload, "scheduler_state"), "RUNNING"));
        if (session.getProtectionResumeRequestedAt() == null) {
            session.setProtectionResumeRequestedAt(now);
        }
        updateSession(session, PROTECTION_RESUMING, null);
        projectCommittedAuthority(plan);
        recordEvent(plan, run, "FAILBACK_AUTHORITY_COMMITTED",
                "Source VM is running, target VM is stopped, and FTCTL acknowledged source authority");
    }

    private void markCommitVerifying(DrPlanVO plan, DrFailbackSessionVO session, Answer answer) {
        JsonObject payload = answerPayload(answer);
        session.setState(COMMIT_VERIFYING);
        session.setCommitOutcome(UNKNOWN);
        session.setEngineAckState(UNKNOWN);
        session.setSchedulerGeneration(longValue(payload, "control_generation"));
        session.setSchedulerAckGeneration(longValue(payload, "control_ack_generation"));
        session.setSchedulerState(stringValue(payload, "scheduler_state"));
        session.setLastProbeAt(new Date());
        session.setErrorCode("DR_FAILBACK_COMMIT_OUTCOME_UNKNOWN");
        session.setErrorMessage("Failback commit acknowledgement is being verified");
        session.markUpdated();
        drFailbackSessionDao.update(session.getId(), session);

        plan.setState(DrConstants.PLAN_STATE_COMMIT_VERIFYING);
        plan.setActiveSide("SOURCE");
        plan.setLastErrorCode(null);
        plan.setLastErrorMessage(null);
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
    }

    private String commitOutcome(Answer answer) {
        JsonObject payload = answerPayload(answer);
        String outcome = upper(stringValue(payload, "failback_commit_outcome"));
        if (StringUtils.equalsAny(outcome, ACKNOWLEDGED, REJECTED, UNKNOWN, ROLLED_BACK)) {
            return outcome;
        }
        if (answer != null && answer.getResult()) {
            return ACKNOWLEDGED;
        }
        String details = answer != null ? answer.getDetails() : null;
        return isUncertainFailure(details) ? UNKNOWN : REJECTED;
    }

    private JsonObject answerPayload(Answer answer) {
        if (answer instanceof FtctlDrActionAnswer) {
            FtctlDrActionAnswer actionAnswer = (FtctlDrActionAnswer) answer;
            JsonObject payload = parseObject(actionAnswer.getStatusJson());
            if (payload != null) {
                return payload;
            }
            payload = parseObject(actionAnswer.getOutput());
            if (payload != null) {
                return payload;
            }
        }
        JsonObject payload = parseObject(answer != null ? answer.getDetails() : null);
        return payload != null ? payload : new JsonObject();
    }

    private JsonObject parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isUncertainFailure(String message) {
        String normalized = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        return StringUtils.contains(normalized, "timeout")
                || StringUtils.contains(normalized, "timed out")
                || StringUtils.contains(normalized, "stream closed")
                || StringUtils.contains(normalized, "ack")
                || StringUtils.contains(normalized, "outcome requires status verification");
    }

    private void updateRuntimeEvidence(DrFailbackSessionVO session, JsonObject runtime) {
        String outcome = stringValue(runtime, "failback_commit_outcome");
        if (StringUtils.isNotBlank(outcome)) {
            session.setCommitOutcome(upper(outcome));
        }
        session.setSchedulerGeneration(firstNonNull(longValue(runtime, "control_generation"),
                session.getSchedulerGeneration()));
        session.setSchedulerAckGeneration(firstNonNull(longValue(runtime, "control_ack_generation"),
                session.getSchedulerAckGeneration()));
        session.setSchedulerState(defaultValue(stringValue(runtime, "scheduler_state"),
                session.getSchedulerState()));
        session.setRollbackState(defaultValue(stringValue(runtime, "rollback_state"),
                session.getRollbackState()));
        session.setRollbackGeneration(firstNonNull(longValue(runtime, "rollback_generation"),
                session.getRollbackGeneration()));
    }

    private void completeLifecycle(DrPlanVO plan, DrRunVO run, DrFailbackSessionVO session, JsonObject runtime) {
        final long planId = plan.getId();
        final long runId = run.getId();
        final long sessionId = session.getId();
        final Long checkpointSequence = longValue(runtime, "latest_completed_checkpoint_sequence");
        final String runtimeJson = GSON.toJson(runtime);
        Transaction.execute(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction(TransactionStatus status) {
                Date now = new Date();
                DrFailbackSessionVO currentSession = drFailbackSessionDao.findById(sessionId);
                DrRunVO currentRun = drRunDao.findById(runId);
                DrPlanVO currentPlan = drPlanDao.findById(planId);
                if (currentSession == null || currentRun == null || currentPlan == null) {
                    throw new CloudRuntimeException("Failback terminal convergence records are incomplete");
                }
                currentSession.setState(COMPLETED);
                currentSession.setPostFailbackCheckpointSequence(checkpointSequence);
                currentSession.setProtectionResumeVerifiedAt(now);
                currentSession.setCompletedAt(now);
                currentSession.setDetailsJson(runtimeJson);
                currentSession.setErrorCode(null);
                currentSession.setErrorMessage(null);
                currentSession.markUpdated();
                drFailbackSessionDao.update(currentSession.getId(), currentSession);

                currentRun.setState(DrConstants.RUN_STATE_SUCCEEDED);
                currentRun.setCompleted(now);
                currentRun.setCurrentStepName("completed");
                currentRun.setProjectionState("succeeded");
                currentRun.setProjectionChecked(now);
                currentRun.setRetryable(false);
                currentRun.setRetryAfterSeconds(null);
                currentRun.setNextRetryAt(null);
                currentRun.setLastStatusJson(runtimeJson);
                currentRun.setErrorCode(null);
                currentRun.setErrorMessage(null);
                currentRun.markUpdated();
                drRunDao.update(currentRun.getId(), currentRun);

                currentPlan.setState(DrConstants.PLAN_STATE_READY);
                currentPlan.setActiveSide("SOURCE");
                currentPlan.setLastErrorCode(null);
                currentPlan.setLastErrorMessage(null);
                currentPlan.markUpdated();
                drPlanDao.update(currentPlan.getId(), currentPlan);

                DrReplicaVO replica = firstReplica(planId);
                if (replica != null) {
                    replica.setState(DrConstants.REPLICA_STATE_READY);
                    replica.setPowerState(DrConstants.REPLICA_POWER_STATE_POWERED_OFF);
                    replica.setActiveSide("SOURCE");
                    replica.setRuntimeStateJson(runtimeJson);
                    replica.markUpdated();
                    drReplicaDao.update(replica.getId(), replica);
                }

                DrCutoverSessionVO cutover = drCutoverSessionDao.findCurrentAuthorityByPlanId(planId);
                if (cutover != null) {
                    cutover.setState(DrConstants.CUTOVER_STATE_FAILED_BACK);
                    cutover.setAuthorityEndedAt(now);
                    cutover.setAuthorityEndedByRunId(runId);
                    cutover.setCleanupRequired(false);
                    cutover.setErrorCode(null);
                    cutover.setErrorMessage(null);
                    cutover.markUpdated();
                    drCutoverSessionDao.update(cutover.getId(), cutover);
                }

                completeFailbackStep(runId, "data-ready", 100,
                        firstNonNull(currentSession.getDataReadyAt(), currentSession.getCreated()), runtimeJson);
                completeFailbackStep(runId, "target-stop", 110,
                        firstNonNull(currentSession.getTargetStoppedAt(), now), runtimeJson);
                completeFailbackStep(runId, "source-start", 120,
                        firstNonNull(currentSession.getSourcePoweredOnAt(), now), runtimeJson);
                completeFailbackStep(runId, "source-boot-validation", 130,
                        firstNonNull(currentSession.getBootValidatedAt(), now), runtimeJson);
                completeFailbackStep(runId, "authority-commit", 140,
                        firstNonNull(currentSession.getCommitVerifiedAt(), currentSession.getEngineAckAt()), runtimeJson);
                completeFailbackStep(runId, "scheduler-resume", 150, now, runtimeJson);
                completeFailbackStep(runId, "post-checkpoint", 160, now, runtimeJson);
                completeFailbackStep(runId, "completed", 170, now, runtimeJson);

                DrPlanViewCacheVO cache = drPlanViewCacheDao.findByPlanId(planId);
                if (cache != null) {
                    drPlanViewCacheDao.remove(cache.getId());
                }
                return null;
            }
        });
        recordEvent(plan, run, "FAILBACK_COMPLETED",
                "Original-direction protection resumed and produced a durable checkpoint");
    }

    private void completeFailbackStep(long runId, String stepName, int stepOrder, Date completedAt,
            String detailsJson) {
        Date completed = completedAt != null ? completedAt : new Date();
        DrRunStepVO step = drRunStepDao.findActiveByRunIdAndStepName(runId, stepName);
        if (step == null) {
            step = new DrRunStepVO(runId, stepName, stepOrder);
            step.setStarted(completed);
            step.setCompleted(completed);
            step.setState(DrConstants.STEP_STATE_SUCCEEDED);
            step.setProgress(100);
            step.setDetailsJson(detailsJson);
            drRunStepDao.persist(step);
            return;
        }
        step.setState(DrConstants.STEP_STATE_SUCCEEDED);
        step.setProgress(100);
        if (step.getStarted() == null) {
            step.setStarted(completed);
        }
        step.setCompleted(completed);
        step.setDetailsJson(detailsJson);
        step.setErrorCode(null);
        step.setErrorMessage(null);
        step.markUpdated();
        drRunStepDao.update(step.getId(), step);
    }

    boolean protectionResumed(DrPlanVO plan, DrFailbackSessionVO session, JsonObject runtime) {
        Long latest = firstNonNull(longValue(runtime, "latest_completed_checkpoint_sequence"),
                longValue(runtime, "plan_cycle_sequence"));
        Long required = session.getRequiredPostFailbackCheckpointSequence();
        if (required == null && session.getCheckpointSequence() != null) {
            required = session.getCheckpointSequence() + 1L;
        }
        return StringUtils.equalsIgnoreCase(plan.getActiveSide(), "SOURCE")
                && StringUtils.equalsIgnoreCase(stringValue(runtime, "active_side"), "SOURCE")
                && StringUtils.equalsAnyIgnoreCase(stringValue(runtime, "scheduler_state"), "RUNNING", "ACTIVE")
                && StringUtils.equalsAnyIgnoreCase(
                        defaultValue(stringValue(runtime, "scheduler_health"), "HEALTHY"),
                        "HEALTHY", "RUNNING")
                && StringUtils.equalsIgnoreCase(session.getEngineAckState(), ACKNOWLEDGED)
                && StringUtils.equalsIgnoreCase(session.getCommitOutcome(), ACKNOWLEDGED)
                && StringUtils.equalsIgnoreCase(session.getTargetPowerState(), "POWERED_OFF")
                && StringUtils.equalsIgnoreCase(session.getSourcePowerState(), "POWERED_ON")
                && latest != null && required != null && latest >= required;
    }

    private void initializeResumeCheckpointContract(DrFailbackSessionVO session) {
        Long checkpoint = session.getCheckpointSequence();
        if (checkpoint == null) {
            return;
        }
        if (session.getResumeBaselineCheckpointSequence() == null) {
            session.setResumeBaselineCheckpointSequence(checkpoint);
        }
        if (session.getRequiredPostFailbackCheckpointSequence() == null) {
            session.setRequiredPostFailbackCheckpointSequence(checkpoint + 1L);
        }
    }

    private void projectCommittedAuthority(DrPlanVO plan) {
        plan.setState(DrConstants.PLAN_STATE_SYNCING);
        plan.setActiveSide("SOURCE");
        plan.setLastErrorCode(null);
        plan.setLastErrorMessage(null);
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
        DrReplicaVO replica = firstReplica(plan.getId());
        if (replica != null) {
            replica.setState(DrConstants.REPLICA_STATE_READY);
            replica.setPowerState(DrConstants.REPLICA_POWER_STATE_POWERED_OFF);
            replica.setActiveSide("SOURCE");
            replica.markUpdated();
            drReplicaDao.update(replica.getId(), replica);
        }
    }

    private void preserveTargetAuthority(DrPlanVO plan) {
        plan.setState(DrConstants.PLAN_STATE_FAILED_OVER);
        plan.setActiveSide("TARGET");
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
        DrReplicaVO replica = firstReplica(plan.getId());
        if (replica != null) {
            replica.setState(DrConstants.REPLICA_STATE_FAILED_OVER);
            replica.setPowerState("POWERED_ON");
            replica.setActiveSide("TARGET");
            replica.markUpdated();
            drReplicaDao.update(replica.getId(), replica);
        }
    }

    private void preserveUncertainAuthority(DrPlanVO plan) {
        plan.setState(DrConstants.PLAN_STATE_COMMIT_VERIFYING);
        plan.setActiveSide("SOURCE");
        plan.setLastErrorCode("DR_FAILBACK_COMMIT_UNCERTAIN");
        plan.setLastErrorMessage("Failback authority could not be rolled back safely");
        plan.markUpdated();
        drPlanDao.update(plan.getId(), plan);
    }

    private DrReplicaVO firstReplica(long planId) {
        List<DrReplicaVO> replicas = drReplicaDao.listActiveByPlanId(planId);
        return replicas == null || replicas.isEmpty() ? null : replicas.get(0);
    }

    private void updateSession(DrFailbackSessionVO session, String state, String errorCode) {
        session.setState(state);
        session.setErrorCode(errorCode);
        if (errorCode == null) {
            session.setErrorMessage(null);
        }
        session.markUpdated();
        drFailbackSessionDao.update(session.getId(), session);
    }

    private void failSession(DrFailbackSessionVO session, String errorCode, String message) {
        session.setState("FAILED");
        session.setErrorCode(errorCode);
        session.setErrorMessage(message);
        session.markUpdated();
        drFailbackSessionDao.update(session.getId(), session);
    }

    private void recordEvent(DrPlanVO plan, DrRunVO run, String eventType, String message) {
        DrEventVO event = new DrEventVO(eventType,
                StringUtils.endsWith(eventType, "FAILED") ? DrConstants.EVENT_SEVERITY_ERROR : DrConstants.EVENT_SEVERITY_INFO,
                DrConstants.EVENT_SOURCE_CLOUD);
        event.setPlanId(plan.getId());
        event.setRunId(run.getId());
        event.setMessage(message);
        drEventDao.persist(event);
    }

    private boolean isTerminal(String state) {
        return StringUtils.equalsAnyIgnoreCase(state, COMPLETED, "FAILED", "ABORTED", "ROLLBACK_FAILED");
    }

    private long authorityGeneration(DrFailbackSessionVO session, DrRunVO run) {
        return session.getCheckpointSequence() != null ? session.getCheckpointSequence() : run.getId();
    }

    private Long longValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String stringValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        return value != null && !value.isJsonNull() ? StringUtils.trimToNull(value.getAsString()) : null;
    }

    private Boolean booleanValue(JsonObject object, String key) {
        JsonElement value = object != null ? object.get(key) : null;
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String upper(String value) {
        return StringUtils.upperCase(StringUtils.defaultString(value), Locale.ROOT);
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.defaultIfBlank(value, defaultValue);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
