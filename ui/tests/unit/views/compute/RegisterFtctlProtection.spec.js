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
import RegisterFtctlProtection from '@/views/compute/RegisterFtctlProtection'
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

const createWrapper = () => {
  return shallowMount(RegisterFtctlProtection, {
    props: {
      resource: {
        id: 'vm-1',
        name: 'vm-name',
        zoneid: 'zone-1',
        clusterid: 'cluster-1',
        hostid: 'host-1'
      }
    },
    global: {
      mocks: {
        $t: (key) => key,
        $message: {
          success: jest.fn(),
          error: jest.fn()
        },
        $notifyError: jest.fn()
      },
      directives: {
        focus: () => {},
        'ctrl-enter': () => {}
      },
      stubs: {
        'a-spin': { template: '<div><slot /></div>' },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<div><slot /></div>' },
        'a-select': { template: '<select><slot /></select>' },
        'a-select-option': { template: '<option><slot /></option>' },
        'a-input': { template: '<input />' },
        'a-button': { template: '<button><slot /></button>' }
      }
    }
  })
}

const mockGetApi = ({ hosts = [], storagePools = [] } = {}) => {
  getAPI.mockImplementation((command) => {
    if (command === 'listHosts') {
      return Promise.resolve({ listhostsresponse: { host: hosts } })
    }
    if (command === 'listStoragePools') {
      return Promise.resolve({ liststoragepoolsresponse: { storagepool: storagePools } })
    }
    return Promise.resolve({})
  })
}

describe('Views > compute > RegisterFtctlProtection.vue', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.spyOn(console, 'warn').mockImplementation(() => {})
  })

  it('fetches KVM hosts and auto-selects single peer host', async () => {
    mockGetApi({
      hosts: [
        { id: 'host-1', hypervisor: 'KVM', name: 'local-host' },
        { id: 'host-2', hypervisor: 'KVM', name: 'peer-host', ipaddress: '10.0.0.12' },
        { id: 'host-3', hypervisor: 'XenServer', name: 'ignored-host' }
      ]
    })

    const wrapper = createWrapper()
    await flushPromises()

    expect(getAPI).toHaveBeenCalledWith('listHosts', {
      zoneid: 'zone-1',
      type: 'Routing',
      state: 'Up',
      listall: true,
      details: 'min',
      clusterid: 'cluster-1'
    })
    expect(getAPI).toHaveBeenCalledWith('listStoragePools', {
      zoneid: 'zone-1',
      listall: true,
      page: 1,
      pagesize: 500,
      clusterid: 'cluster-1'
    })
    expect(wrapper.vm.hosts).toHaveLength(1)
    expect(wrapper.vm.hosts[0].id).toBe('host-2')
    expect(wrapper.vm.form.peerhostid).toBe('host-2')
  })

  it('updates conditional fields when mode and backend change', async () => {
    mockGetApi({
      storagePools: [{ id: 'pool-1', name: 'pool-1', scope: 'HOST', state: 'Up' }]
    })
    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.form.mode = 'ft'
    wrapper.vm.handleModeChange('ft')
    expect(wrapper.vm.form.backendmode).toBe(null)
    expect(wrapper.vm.form.targetstoragescope).toBe(null)
    expect(wrapper.vm.showFtFields).toBe(true)

    wrapper.vm.form.mode = 'dr'
    wrapper.vm.handleModeChange('dr')
    wrapper.vm.form.backendmode = 'remote-nbd'
    wrapper.vm.handleBackendModeChange('remote-nbd')
    expect(wrapper.vm.form.targetstoragepoolid).toBe('pool-1')
    expect(wrapper.vm.form.targetstoragescope).toBe('host')
    expect(wrapper.vm.showRemoteNbdFields).toBe(true)
  })

  it('submits registerFtctlProtection and emits refresh events', async () => {
    mockGetApi({
      hosts: [{ id: 'host-2', hypervisor: 'KVM', name: 'peer-host', ipaddress: '10.0.0.12' }],
      storagePools: [{ id: 'pool-1', name: 'pool-1', scope: 'HOST', state: 'Up' }]
    })
    postAPI.mockResolvedValue({ registerftctlprotectionresponse: { success: true } })

    const wrapper = createWrapper()
    await flushPromises()

    wrapper.vm.form.mode = 'dr'
    wrapper.vm.form.backendmode = 'remote-nbd'
    wrapper.vm.form.targetstoragepoolid = 'pool-1'
    wrapper.vm.form.targetstoragescope = 'host'
    wrapper.vm.form.fencingpolicy = 'manual-block'
    wrapper.vm.form.peerhostid = 'host-2'
    wrapper.vm.form.secondaryvmname = 'vm-name-standby'
    wrapper.vm.form.secondarytargetdir = '/data/secondary'
    wrapper.vm.form.remotenbdexportaddr = '10.0.0.12:10809'
    wrapper.vm.formRef.value = {
      validate: jest.fn().mockResolvedValue(true),
      scrollToField: jest.fn()
    }

    await wrapper.vm.handleSubmit({ preventDefault: jest.fn() })
    await flushPromises()

    expect(postAPI).toHaveBeenCalledWith('registerFtctlProtection', {
      virtualmachineid: 'vm-1',
      mode: 'dr',
      fencingpolicy: 'manual-block',
      backendmode: 'remote-nbd',
      targetstoragescope: 'host',
      targetstoragepoolid: 'pool-1',
      peerhostid: 'host-2',
      secondaryvmname: 'vm-name-standby',
      secondarytargetdir: '/data/secondary',
      remotenbdexportaddr: '10.0.0.12:10809'
    })
    expect(wrapper.emitted('refresh-data')).toBeTruthy()
    expect(wrapper.emitted('close-action')).toBeTruthy()
    expect(eventBus.emit).toHaveBeenCalledWith('vm-refresh-data')
  })
})
