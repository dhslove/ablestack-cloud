// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.

import {
  drActionReasonMessageKey,
  normalizeActionAvailability,
  resolveDrActionAvailability
} from '@/utils/dr/actionAvailability'

describe('DR action availability', () => {
  it('normalizes camel-case backend keys and reason fields', () => {
    const normalized = normalizeActionAvailability({
      testFailover: {
        applicable: true,
        enabled: false,
        reasoncode: 'DR_ACTION_TARGET_NOT_READY',
        reasonargs: { target: 'vm' }
      }
    })

    expect(normalized.testfailover).toEqual({
      applicable: true,
      enabled: false,
      reasonCode: 'DR_ACTION_TARGET_NOT_READY',
      reasonArgs: { target: 'vm' }
    })
  })

  it('fails closed when neither typed nor legacy state is available', () => {
    expect(resolveDrActionAvailability({ key: 'failover' }, {})).toEqual({
      applicable: false,
      enabled: false,
      reasonCode: 'DR_ACTION_AVAILABILITY_MISSING'
    })
  })

  it('maps stable backend reason codes to locale keys', () => {
    expect(drActionReasonMessageKey('DR_ACTION_TARGET_NOT_READY'))
      .toBe('message.dr.action.target.not.ready')
  })

  it('keeps capability block reason and args for pre-action UI gating', () => {
    const state = resolveDrActionAvailability({ key: 'reprotect' }, {
      actionavailability: {
        reprotect: {
          applicable: true,
          enabled: false,
          reasoncode: 'DR_ACTION_REPROTECT_CONTRACT_UNSUPPORTED',
          reasonargs: { requiredVersion: 'current' }
        }
      }
    })

    expect(state).toEqual({
      applicable: true,
      enabled: false,
      reasonCode: 'DR_ACTION_REPROTECT_CONTRACT_UNSUPPORTED',
      reasonArgs: { requiredVersion: 'current' }
    })
    expect(drActionReasonMessageKey(state.reasonCode))
      .toBe('message.dr.action.reprotect.contract.unsupported')
  })

  it('never exposes cancel after the live run has reached terminal state', () => {
    const staleResource = {
      actionavailability: {
        cancelRun: { applicable: true, enabled: true }
      }
    }

    expect(resolveDrActionAvailability(
      { key: 'cancelrun' },
      staleResource,
      { id: 'terminal-run', state: 'SUCCEEDED' }
    )).toEqual({ applicable: false, enabled: false, reasonCode: '' })
  })
})
