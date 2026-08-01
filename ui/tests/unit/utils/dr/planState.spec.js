// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.

import {
  isActiveDrRun,
  isActiveDrSyncCycle,
  resolveDrPlanSeverity,
  resolveDrPlanState,
  resolveDrRpoPresentation
} from '@/utils/dr/planState'

describe('DR protection state helpers', () => {
  it('does not treat a completed cleanup run as current activity', () => {
    const cleanup = { id: 'cleanup-run', runtype: 'TEST_CLEANUP', state: 'SUCCEEDED' }

    expect(isActiveDrRun(cleanup)).toBe(false)
    expect(resolveDrPlanState({ protectionstate: 'READY' }, null)).toBe('READY')
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

  it('does not classify an acknowledged target authority as an error', () => {
    const plan = {
      currentseverity: 'INFO',
      protectionphase: 'FAILED_OVER_UNPROTECTED',
      protectionstate: 'FAILED_OVER_UNPROTECTED'
    }

    expect(resolveDrPlanSeverity(plan)).toBe('INFO')
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
