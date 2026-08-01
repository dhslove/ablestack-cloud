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
})
