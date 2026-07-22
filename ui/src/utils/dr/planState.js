export const ACTIVE_DR_RUN_STATES = [
  'QUEUED', 'PREPARING', 'DISPATCHING', 'ACCEPTED', 'RUNNING', 'RETRYING', 'CANCEL_REQUESTED'
]

export const ACTIVE_DR_SYNC_CYCLE_STATES = [
  'PREPARING', 'SNAPSHOTTING', 'TRANSFERRING', 'COMMITTING', 'RETRYING', 'RUNNING'
]

export function isActiveDrRun (run = {}) {
  return ACTIVE_DR_RUN_STATES.includes(String(run?.state || '').toUpperCase())
}

export function isActiveDrSyncCycle (cycle = {}) {
  return ACTIVE_DR_SYNC_CYCLE_STATES.includes(String(cycle?.state || '').toUpperCase())
}

export function resolveDrPlanState (plan = {}, currentRun = null) {
  const latestRun = currentRun || plan.lastrun || {}
  const recoveryState = String(plan.schedulerrecoverystate || plan.schedulerRecoveryState || '').toUpperCase()
  if (['PENDING', 'RECOVERING'].includes(recoveryState)) {
    return recoveryState === 'PENDING' ? 'RECOVERY_PENDING' : 'RECOVERING'
  }
  if (recoveryState === 'FAILED') {
    return 'RECOVERY_FAILED'
  }
  const protectionPhase = String(plan.protectionphase || plan.protectionPhase || '').toUpperCase()
  const operatingSide = String(plan.operatingside || plan.operatingSide || plan.activeside || '').toUpperCase()
  if (operatingSide === 'TARGET' && protectionPhase) {
    return protectionPhase
  }
  const schedulerHealth = String(plan.schedulerhealth || '').toUpperCase()
  const ownerMatched = plan.ownermatched
  if (['DEAD', 'OWNER_MISMATCH', 'DUPLICATE_WORKER', 'STALE'].includes(schedulerHealth) ||
      (schedulerHealth && ownerMatched === false)) {
    return 'DEGRADED'
  }
  const protection = String(plan.protectionstate || '').toUpperCase()
  if (protection) {
    return protection
  }
  const effective = String(plan.effectivestate || '').toUpperCase()
  if (effective) {
    return effective
  }
  const runtime = String(plan.runtimestate || latestRun.runtimestate || '').toUpperCase()
  const worker = String(latestRun.workerstate || '').toUpperCase()
  const runState = String(latestRun.state || '').toUpperCase()
  const runtimeError = plan.runtimeerrorcode || latestRun.runtimeerrorcode
  const runError = runState === 'FAILED' ? latestRun.errorcode : null
  if (['ERROR', 'FAILED'].includes(runtime) || worker === 'FAILED' || runtimeError || runError) {
    return 'ERROR'
  }
  const readiness = String(plan.readinessstate || '').toUpperCase()
  const materialization = String(plan.targetmaterializationstate || '').toUpperCase()
  const state = String(plan.state || '').toUpperCase()
  if (readiness === 'TARGET_READY' || materialization === 'TARGET_READY') {
    return 'READY'
  }
  if (readiness === 'TARGET_MATERIALIZING' || materialization === 'TARGET_MATERIALIZING') {
    return state === 'READY' ? 'SYNCING' : 'TARGET_MATERIALIZING'
  }
  if (readiness === 'ENGINE_ACCEPTED') {
    return 'ACCEPTED'
  }
  if (readiness === 'DEGRADED') {
    return 'DEGRADED'
  }
  return state || readiness || 'UNKNOWN'
}

export function hasDrSourceAuthority (plan = {}) {
  const side = String(plan.operatingside || plan.operatingSide || plan.activeside || plan.activeSide || 'SOURCE').toUpperCase()
  const state = String(plan.state || '').toUpperCase()
  return side !== 'TARGET' && state !== 'FAILED_OVER'
}

export function resolveDrReplicationResumeState (plan = {}) {
  const schedulerHealth = String(plan.schedulerhealth || '').toUpperCase()
  const schedulerState = String(plan.schedulerstate || '').toUpperCase()
  const protectionState = String(plan.protectionstate || '').toUpperCase()
  const replicationActivity = String(plan.replicationactivity || '').toUpperCase()
  const controlState = String(plan.controlstate || '').toUpperCase()
  if (['DEAD', 'OWNER_MISMATCH', 'DUPLICATE_WORKER', 'STALE'].includes(schedulerHealth) || plan.ownermatched === false) {
    return 'DEGRADED'
  }
  if (protectionState === 'PAUSED' || schedulerState === 'PAUSED' || controlState === 'PAUSED') {
    return 'PAUSED'
  }
  if (schedulerHealth === 'HEALTHY' && schedulerState === 'RUNNING' && plan.ownermatched !== false) {
    return ['RUNNING', 'TRANSFERRING', 'CHECKPOINTING'].includes(replicationActivity) ? 'RUNNING' : 'RESUMED'
  }
  return 'UNKNOWN'
}
