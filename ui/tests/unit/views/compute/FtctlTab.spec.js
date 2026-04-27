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

import { flushPromises, shallowMount } from '@vue/test-utils'
import FtctlTab from '@/views/compute/FtctlTab'
import { getAPI, postAPI } from '@/api'
import eventBus from '@/config/eventBus'

jest.mock('@/api', () => ({
  getAPI: jest.fn(),
  postAPI: jest.fn()
}))

jest.mock('@/config/eventBus', () => ({
  __esModule: true,
  default: {
    emit: jest.fn()
  }
}))

const createWrapper = (apis = {}) => {
  return shallowMount(FtctlTab, {
    props: {
      resource: {
        id: 'vm-1',
        name: 'vm-name'
      },
      loading: false
    },
    global: {
      mocks: {
        $store: {
          getters: {
            apis
          }
        },
        $message: {
          success: jest.fn(),
          error: jest.fn()
        }
      },
      stubs: {
        'a-spin': { template: '<div><slot /></div>' },
        'a-button': { template: '<button><slot /></button>' },
        'a-alert': { template: '<div><slot name="message" /></div>' },
        'a-card': { template: '<div><slot /></div>' },
        'a-tag': { template: '<span><slot /></span>' },
        'a-descriptions': { template: '<div><slot /></div>' },
        'a-descriptions-item': { template: '<div><slot /></div>' },
        'a-divider': true,
        'a-table': { template: '<div><slot /></div>' }
      }
    }
  })
}

describe('Views > compute > FtctlTab.vue', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.spyOn(console, 'warn').mockImplementation(() => {})
    global.window.confirm = jest.fn(() => true)
  })

  it('fetches protection, check, health and events on create', async () => {
    getAPI.mockImplementation((command) => {
      switch (command) {
        case 'getFtctlProtection':
          return Promise.resolve({
            getftctlprotectionresponse: {
              enabled: 'true',
              mode: 'dr',
              protectionstate: 'protected',
              adminstate: 'running',
              fencingstate: 'clear'
            }
          })
        case 'getFtctlCheck':
          return Promise.resolve({
            getftctlcheckresponse: {
              result: 'ok',
              inventoryresult: 'healthy',
              primaryrc: 0,
              peerrc: 1
            }
          })
        case 'getFtctlHealth':
          return Promise.resolve({
            getftctlhealthresponse: {
              result: 'ok',
              hostid: 201,
              uri: 'qemu+ssh://10.0.0.11/system',
              rc: 0
            }
          })
        case 'getFtctlEvents':
          return Promise.resolve({
            getftctleventsresponse: {
              events: [
                { timestamp: '2026-04-19T00:10:00+09:00', event: 'older', result: 'ok' },
                { timestamp: '2026-04-19T00:20:00+09:00', event: 'newer', result: 'warn', details: '{"reason":"backoff"}' }
              ]
            }
          })
        default:
          return Promise.resolve({})
      }
    })

    const wrapper = createWrapper({
      getFtctlCheck: true,
      getFtctlHealth: true,
      getFtctlEvents: true,
      pauseFtctlProtection: true,
      resumeFtctlProtection: true,
      failoverFtctlProtection: true,
      failbackFtctlProtection: true,
      confirmFtctlFence: true,
      clearFtctlFence: true
    })

    await flushPromises()

    expect(getAPI).toHaveBeenCalledWith('getFtctlProtection', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlCheck', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlHealth', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlEvents', { virtualmachineid: 'vm-1', limit: 10 })
    expect(wrapper.vm.protection.mode).toBe('dr')
    expect(wrapper.vm.checkResult.inventoryresult).toBe('healthy')
    expect(wrapper.vm.healthResult.uri).toBe('qemu+ssh://10.0.0.11/system')
    expect(wrapper.vm.events[0].event).toBe('newer')
    expect(wrapper.vm.events[1].event).toBe('older')
    expect(wrapper.vm.showPauseAction).toBe(true)
    expect(wrapper.vm.operationalSummary.type).toBe('warning')
  })

  it('runs action, applies payload and emits refresh event', async () => {
    let protectionState = {
      enabled: 'true',
      mode: 'dr',
      protectionstate: 'protected',
      transportstate: 'replicating',
      activeside: 'primary',
      adminstate: 'running',
      fencingstate: 'clear'
    }
    getAPI.mockImplementation((command) => {
      switch (command) {
        case 'getFtctlProtection':
          return Promise.resolve({
            getftctlprotectionresponse: protectionState
          })
        case 'getFtctlCheck':
          return Promise.resolve({ getftctlcheckresponse: {} })
        case 'getFtctlHealth':
          return Promise.resolve({ getftctlhealthresponse: {} })
        case 'getFtctlEvents':
          return Promise.resolve({ getftctleventsresponse: { events: [] } })
        default:
          return Promise.resolve({})
      }
    })
    postAPI.mockResolvedValue({
      failoverftctlprotectionresponse: {
        result: 'ok',
        protectionstate: 'protected',
        transportstate: 'replicating',
        activeside: 'secondary',
        adminstate: 'running',
        fencingstate: 'clear'
      }
    })

    const wrapper = createWrapper({
      getFtctlCheck: true,
      getFtctlHealth: true,
      getFtctlEvents: true,
      failoverFtctlProtection: true
    })

    await flushPromises()
    protectionState = {
      enabled: 'true',
      mode: 'dr',
      protectionstate: 'protected',
      transportstate: 'replicating',
      activeside: 'secondary',
      adminstate: 'running',
      fencingstate: 'clear'
    }
    await wrapper.vm.runAction('failoverFtctlProtection', true)
    await flushPromises()

    expect(global.window.confirm).toHaveBeenCalled()
    expect(postAPI).toHaveBeenCalledWith('failoverFtctlProtection', { virtualmachineid: 'vm-1' })
    expect(wrapper.vm.protection.activeside).toBe('secondary')
    expect(wrapper.vm.lastAction.success).toBe(true)
    expect(wrapper.vm.lastAction.message).toContain('failoverFtctlProtection completed')
    expect(eventBus.emit).toHaveBeenCalledWith('vm-refresh-data')
  })
})
