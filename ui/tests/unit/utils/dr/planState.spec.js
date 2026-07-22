// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.

import { isActiveDrRun, isActiveDrSyncCycle, resolveDrPlanState } from '@/utils/dr/planState'

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
})
