// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.

import {
  isActiveDrRun,
  isActiveDrSyncCycle,
  reconcileDrPlanProjection,
  reconcileDrRunProjection,
  resolveDrPlanSeverity,
  resolveDrPlanState,
  resolveDrReadinessState,
  resolveDrReplicationResumeState,
  resolveDrRpoPresentation
} from '@/utils/dr/planState'

describe('DR protection state helpers', () => {
  it('does not treat a completed cleanup run as current activity', () => {
    const cleanup = { id: 'cleanup-run', runtype: 'TEST_CLEANUP', state: 'SUCCEEDED' }

    expect(isActiveDrRun(cleanup)).toBe(false)
    expect(resolveDrPlanState({ protectionstate: 'READY' }, null)).toBe('READY')
  })

  it('keeps execution readiness independent from transient protection state', () => {
    const plan = {
      protectionstate: 'SYNCING',
      readinessstate: 'TARGET_READY'
    }

    expect(resolveDrPlanState(plan)).toBe('SYNCING')
    expect(resolveDrReadinessState(plan)).toBe('TARGET_READY')
  })

  it('recognizes active finite runs only', () => {
    expect(isActiveDrRun({ state: 'RUNNING' })).toBe(true)
    expect(isActiveDrRun({ state: 'RETRYING' })).toBe(true)
    expect(isActiveDrRun({ state: 'FAILED' })).toBe(false)
    expect(isActiveDrRun({ state: 'SUCCEEDED' })).toBe(false)
  })

  it('separates active replication cycles from completed cycles', () => {
    expect(isActiveDrSyncCycle({ state: 'TRANSFERRING' })).toBe(true)
    expect(isActiveDrSyncCycle({ state: 'COMMITTING' })).toBe(true)
    expect(isActiveDrSyncCycle({ state: 'READY' })).toBe(false)
    expect(isActiveDrSyncCycle({ state: 'COMPLETED' })).toBe(false)
  })

  it('clears a stale cached active run when the live run is terminal', () => {
    const projection = reconcileDrRunProjection({
      activeRun: { id: 'test-run', state: 'ACCEPTED' },
      latestOperationRun: { id: 'test-run', state: 'ACCEPTED' }
    }, [
      { id: 'test-run', state: 'FAILED', errorcode: 'DR_TEST_CLOUD_MATERIALIZATION_FAILED' }
    ])

    expect(projection.activeRun).toEqual({})
    expect(projection.latestOperationRun.state).toBe('FAILED')
  })

  it('uses a live active run instead of a stale terminal snapshot', () => {
    const projection = reconcileDrRunProjection({
      activeRun: {},
      latestOperationRun: { id: 'old-run', state: 'FAILED' }
    }, [
      { id: 'new-run', state: 'RUNNING' },
      { id: 'old-run', state: 'FAILED' }
    ])

    expect(projection.activeRun.id).toBe('new-run')
    expect(projection.latestOperationRun.id).toBe('new-run')
  })

  it('keeps live action availability when applying a cached plan projection', () => {
    const projection = reconcileDrPlanProjection({
      state: 'READY',
      actionavailability: {
        cancelrun: { applicable: true, enabled: true }
      }
    }, {
      actionavailability: {
        testfailover: { applicable: true, enabled: true },
        cancelrun: { applicable: false, enabled: false }
      }
    })

    expect(projection.state).toBe('READY')
    expect(projection.actionavailability.testfailover.enabled).toBe(true)
    expect(projection.actionavailability.cancelrun.applicable).toBe(false)
  })

  it('does not classify an acknowledged target authority as an error', () => {
    const plan = {
      currentseverity: 'INFO',
      protectionphase: 'FAILED_OVER_UNPROTECTED',
      protectionstate: 'FAILED_OVER_UNPROTECTED'
    }

    expect(resolveDrPlanSeverity(plan)).toBe('INFO')
  })

  it('shows a retryable source outage as waiting instead of a terminal error', () => {
    const plan = {
      schedulerrecoverystate: 'FAILED',
      schedulerhealth: 'WAITING_SOURCE',
      runtimeerrorcode: 'DR_SOURCE_SITE_UNAVAILABLE',
      protectionstate: 'DEGRADED'
    }

    expect(resolveDrPlanState(plan)).toBe('WAITING_SOURCE_RECOVERY')
  })

  it('shows a missing target export as a retryable resource wait', () => {
    const plan = {
      schedulerrecoverystate: 'FAILED',
      schedulerhealth: 'WAITING_RESOURCE',
      runtimeerrorcode: 'DR_TARGET_EXPORT_UNAVAILABLE',
      protectionstate: 'DEGRADED'
    }

    expect(resolveDrPlanState(plan)).toBe('WAITING_RESOURCE')
  })

  it('shows CBT epoch baseline recovery as an active recovery state', () => {
    const plan = {
      schedulerrecoverystate: 'FAILED',
      schedulerhealth: 'RECOVERING_BASELINE',
      replicationactivity: 'RESEEDING',
      runtimeerrorcode: 'DR_CBT_RESEED_REQUIRED',
      protectionstate: 'DEGRADED'
    }

    expect(resolveDrPlanState(plan)).toBe('RECOVERING_BASELINE')
  })

  it('keeps a released plan unprotected when its scheduler is intentionally stopped', () => {
    const plan = {
      state: 'UNPROTECTED',
      adminstate: 'DISABLED',
      schedulerhealth: 'DEAD',
      schedulerstate: 'STOPPED',
      ownermatched: false,
      readinessstate: 'DEGRADED'
    }

    expect(resolveDrPlanState(plan)).toBe('UNPROTECTED')
  })

  it('does not let stale target readiness override a released protection state', () => {
    const plan = {
      state: 'UNPROTECTED',
      protectionstate: 'UNPROTECTED',
      readinessstate: 'TARGET_READY',
      targetmaterializationstate: 'TARGET_READY'
    }

    expect(resolveDrPlanState(plan)).toBe('UNPROTECTED')
  })

  it('shows an operator-canceled scheduler as requiring explicit recovery', () => {
    expect(resolveDrReplicationResumeState({
      schedulerrecoverystate: 'REQUIRED',
      schedulerstate: 'STOPPED',
      schedulerhealth: 'STOPPED'
    })).toBe('RECOVERY_REQUIRED')
  })

  it('uses the frozen cutover RPO supplied by the API', () => {
    const presentation = resolveDrRpoPresentation({
      rpoevaluationmode: 'CUTOVER_FROZEN',
      displayrposeconds: 36,
      rpoasof: '2026-07-30T11:06:33+09:00',
      rpostatus: 'MET'
    })

    expect(presentation.mode).toBe('CUTOVER_FROZEN')
    expect(presentation.seconds).toBe(36)
    expect(presentation.status).toBe('MET')
  })
})
