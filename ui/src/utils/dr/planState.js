export function resolveDrPlanState (plan = {}, currentRun = null) {
  const latestRun = currentRun || plan.lastrun || {}
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
