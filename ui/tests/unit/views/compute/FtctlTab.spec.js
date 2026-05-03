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
        name: 'vm-name',
        hypervisor: 'KVM',
        vmtype: 'User'
      },
      loading: false
    },
    global: {
      mocks: {
        $store: {
          getters: {
            apis,
            userInfo: {
              roletype: 'Admin'
            }
          }
        },
        $message: {
          success: jest.fn(),
          error: jest.fn()
        },
        $t: (key) => key
      },
      stubs: {
        'a-spin': { template: '<div><slot /></div>' },
        'a-space': { template: '<div><slot /></div>' },
        'a-popconfirm': { template: '<div><slot /></div>' },
        'a-modal': { template: '<div><slot /></div>' },
        'a-button': { template: '<button><slot /></button>' },
        'a-alert': { template: '<div><slot name="message" /></div>' },
        'a-card': { template: '<div><slot /></div>' },
        'a-tag': { template: '<span><slot /></span>' },
        'a-descriptions': { template: '<div><slot /></div>' },
        'a-descriptions-item': { template: '<div><slot /></div>' },
        'a-divider': true,
        'a-table': { template: '<div><slot /></div>' },
        SafetyCertificateOutlined: true,
        SyncOutlined: true,
        PauseCircleOutlined: true,
        PlayCircleOutlined: true,
        ThunderboltOutlined: true,
        UndoOutlined: true,
        CheckCircleOutlined: true,
        ClearOutlined: true
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
              ftctlprotection: {
                enabled: 'true',
                mode: 'dr',
                peerhostname: 'ablecube22-1',
                secondaryvmname: 'i-2-303-VM',
                secondaryvirtualmachineuuid: 'secondary-vm-uuid',
                secondaryvirtualmachinedisplayname: 'vm-name-secondary',
                secondaryvolumes: [
                  { id: 'secondary-root-volume-uuid', name: 'vm-name-secondary-root', path: 'rbd-root-path' },
                  { id: 'secondary-data-volume-uuid', name: 'vm-name-secondary-data', path: 'rbd-data-path' }
                ],
                targetstoragepoolname: 'Primary Storage',
                protectionstate: 'protected',
                adminstate: 'running',
                fencingstate: 'clear'
              }
            }
          })
        case 'getFtctlCheck':
          return Promise.resolve({
            getftctlcheckresponse: {
              ftctlcheck: {
                result: 'ok',
                inventoryresult: 'healthy',
                primaryrc: 0,
                peerrc: 1
              }
            }
          })
        case 'getFtctlHealth':
          return Promise.resolve({
            getftctlhealthresponse: {
              ftctlhealth: {
                result: 'ok',
                hostid: 201,
                hostname: 'ablecube22-3',
                uri: 'qemu+ssh://10.0.0.11/system',
                rc: 0
              }
            }
          })
        case 'getFtctlEvents':
          return Promise.resolve({
            getftctleventsresponse: {
              ftctlevents: {
                events: [
                  { ts: '2026-04-19T00:10:00+09:00', event: 'older', result: 'ok' },
                  { ts: '2026-04-19T00:20:00+09:00', event: 'newer', result: 'warn', details: '{"reason":"backoff"}' }
                ]
              }
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
    expect(wrapper.vm.peerHostDisplay).toBe('ablecube22-1')
    expect(wrapper.vm.secondaryVmDisplay).toBe('vm-name-secondary')
    expect(wrapper.vm.secondaryVmRouteId).toBe('secondary-vm-uuid')
    expect(wrapper.vm.secondaryVolumeItems.map(volume => volume.name)).toEqual(['vm-name-secondary-root', 'vm-name-secondary-data'])
    expect(wrapper.vm.healthHostDisplay).toBe('ablecube22-3')
    expect(wrapper.vm.returnCodeStatus(wrapper.vm.checkResult.primaryrc)).toBe('OK')
    expect(wrapper.vm.returnCodeStatus(wrapper.vm.checkResult.peerrc)).toBe('WARN')
    expect(wrapper.vm.checkResult.inventoryresult).toBe('healthy')
    expect(wrapper.vm.healthResult.uri).toBe('qemu+ssh://10.0.0.11/system')
    expect(wrapper.vm.events[0].event).toBe('newer')
    expect(wrapper.vm.events[0].timestamp).toBe('2026-04-19T00:20:00+09:00')
    expect(wrapper.vm.events[1].event).toBe('older')
    expect(wrapper.vm.canRunActions).toBe(true)
    expect(wrapper.vm.actionDefinitions.find(action => action.api === 'pauseFtctlProtection').disabled).toBe(false)
    expect(wrapper.vm.operationalSummary.type).toBe('warning')
  })

  it('loads read-only runtime data for standby protection view', async () => {
    getAPI.mockImplementation((command) => {
      switch (command) {
        case 'getFtctlProtection':
          return Promise.resolve({
            getftctlprotectionresponse: {
              ftctlprotection: {
                enabled: 'true',
                mode: 'ha',
                protectionrole: 'standby',
                primaryvirtualmachineid: 101,
                primaryvirtualmachineuuid: 'primary-vm-uuid',
                primaryvirtualmachinename: 'r9-01',
                secondaryvirtualmachineid: 308,
                secondaryvirtualmachineuuid: 'standby-vm-uuid',
                secondaryvirtualmachinedisplayname: 'r9-01-standby',
                protectionstate: 'failed_over',
                transportstate: 'failed_over',
                activeside: 'secondary',
                adminstate: 'active',
                fencingstate: 'manual-fenced'
              }
            }
          })
        case 'getFtctlCheck':
          return Promise.resolve({
            getftctlcheckresponse: {
              ftctlcheck: {
                virtualmachineid: 308,
                vmname: 'r9-01',
                result: 'ok',
                inventoryresult: 'healthy',
                primaryrc: 0,
                peerrc: 0
              }
            }
          })
        case 'getFtctlHealth':
          return Promise.resolve({
            getftctlhealthresponse: {
              ftctlhealth: {
                virtualmachineid: 308,
                result: 'ok',
                hostname: 'ablecube22-3',
                uri: 'qemu:///system',
                rc: 0
              }
            }
          })
        case 'getFtctlEvents':
          return Promise.resolve({
            getftctleventsresponse: {
              ftctlevents: {
                virtualmachineid: 308,
                vmname: 'r9-01',
                count: 1,
                events: [
                  { ts: '2026-05-03T13:47:10+09:00', stage: 'failover', event: 'failover.precheck', result: 'ok' }
                ]
              }
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
      failoverFtctlProtection: true
    })

    await flushPromises()

    expect(getAPI).toHaveBeenCalledWith('getFtctlProtection', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlCheck', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlHealth', { virtualmachineid: 'vm-1' })
    expect(getAPI).toHaveBeenCalledWith('getFtctlEvents', { virtualmachineid: 'vm-1', limit: 10 })
    expect(wrapper.vm.standbyProtectionView).toBe(true)
    expect(wrapper.vm.canRunActions).toBe(false)
    expect(wrapper.vm.checkResult.vmname).toBe('r9-01')
    expect(wrapper.vm.healthHostDisplay).toBe('ablecube22-3')
    expect(wrapper.vm.events).toHaveLength(1)
    expect(wrapper.vm.events[0].event).toBe('failover.precheck')
    expect(wrapper.vm.operationalSummary.type).toBe('info')
    expect(wrapper.vm.stateTagColor('failed_over')).toBe('blue')
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
    await wrapper.vm.runAction('failoverFtctlProtection')
    await flushPromises()

    expect(postAPI).toHaveBeenCalledWith('failoverFtctlProtection', { virtualmachineid: 'vm-1' })
    expect(wrapper.vm.protection.activeside).toBe('secondary')
    expect(wrapper.vm.lastAction.success).toBe(true)
    expect(wrapper.vm.lastAction.message).toContain('label.ftctl.failover label.completed')
    expect(eventBus.emit).toHaveBeenCalledWith('vm-refresh-data')
  })
})
