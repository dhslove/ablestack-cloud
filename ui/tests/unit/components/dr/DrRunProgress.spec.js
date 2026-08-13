// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0.

import { shallowMount } from '@vue/test-utils'
import DrRunProgress from '@/components/dr/DrRunProgress.vue'

const mountProgress = props => shallowMount(DrRunProgress, {
  props: {
    run: {},
    runtime: {},
    ...props
  },
  global: {
    mocks: {
      $t: (key, values) => values && values.reason ? `${key}:${values.reason}` : key,
      $te: () => false
    }
  }
})

describe('DrRunProgress transfer authority', () => {
  it('keeps a valid run sample when plan runtime is schema zero', () => {
    const wrapper = mountProgress({
      run: {
        transferprogressschemaversion: 2,
        transfercyclesequence: 8,
        transfersamplesequence: 3,
        transferbytestotal: 4096,
        transferbytesprocessed: 1024,
        transferpercent: 25
      },
      runtime: {
        transferprogressschemaversion: 0,
        transferbytestotal: 0,
        transferbytesprocessed: 0,
        transferpercent: 0
      }
    })

    expect(wrapper.vm.transferPercent).toBe(25)
    expect(wrapper.vm.transferBytesProcessed).toBe(1024)
  })

  it('selects the newer valid runtime sample and suppresses transient busy noise', () => {
    const wrapper = mountProgress({
      run: {
        retryable: true,
        errormessage: 'FTCTL engine is busy',
        transferprogressschemaversion: 2,
        transfercyclesequence: 8,
        transfersamplesequence: 3,
        transferbytestotal: 4096,
        transferbytesprocessed: 1024,
        transferpercent: 25
      },
      runtime: {
        transferprogressschemaversion: 2,
        transfercyclesequence: 8,
        transfersamplesequence: 4,
        transferactivitystate: 'COPYING',
        transferbytestotal: 4096,
        transferbytesprocessed: 2048,
        transferpercent: 50,
        transferprogressstale: false
      }
    })

    expect(wrapper.vm.transferPercent).toBe(50)
    expect(wrapper.vm.retryNotice).toBe('')
  })

  it('keeps whole-operation progress consistent with a live transfer sample', () => {
    const wrapper = mountProgress({
      run: {
        runtype: 'SYNC',
        state: 'RUNNING',
        progresspercent: 1,
        transferprogressschemaversion: 2,
        transfercyclesequence: 75,
        transfersamplesequence: 10,
        transferbytestotal: 1000,
        transferbytesprocessed: 220,
        transferpercent: 22
      }
    })

    expect(wrapper.vm.transferPercent).toBe(22)
    expect(wrapper.vm.progress).toBe(76)
  })

  it('does not let an older transfer sample reduce backend workflow progress', () => {
    const wrapper = mountProgress({
      run: {
        runtype: 'SYNC',
        state: 'RUNNING',
        progresspercent: 90,
        transferprogressschemaversion: 2,
        transferbytestotal: 1000,
        transferbytesprocessed: 250,
        transferpercent: 25
      }
    })

    expect(wrapper.vm.progress).toBe(90)
  })
})
