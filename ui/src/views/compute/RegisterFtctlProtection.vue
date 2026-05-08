<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements. See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership. The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied. See the License for the
  specific language governing permissions and limitations
  under the License.
-->
<template>
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit">
        <a-form-item name="mode">
          <template #label>
            <tooltip-label :title="$t('label.mode')" :tooltip="$t('placeholder.ftctl.mode')" />
          </template>
          <a-select v-model:value="form.mode" v-focus="true" @change="handleModeChange">
            <a-select-option value="ha">HA</a-select-option>
            <a-select-option value="dr">DR</a-select-option>
            <a-select-option value="ft">FT</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="peerhostid">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.peer.host.id')" :tooltip="$t('placeholder.ftctl.peer.host.id')" />
          </template>
          <a-select
            v-model:value="form.peerhostid"
            :loading="hostsLoading"
            :placeholder="$t('placeholder.ftctl.peer.host.id')"
            showSearch
            optionFilterProp="label"
            @change="handlePeerHostChange"
            :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
            <a-select-option
              v-for="host in hosts"
              :key="host.id"
              :value="host.id"
              :label="formatHostLabel(host)">
              {{ formatHostLabel(host) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="targetstoragepoolid" v-if="showStorageFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.target.storage.scope')" :tooltip="$t('placeholder.ftctl.target.storage.scope')" />
          </template>
          <a-select
            v-model:value="form.targetstoragepoolid"
            :loading="storagePoolsLoading"
            :placeholder="$t('placeholder.ftctl.target.storage.pool')"
            showSearch
            optionFilterProp="label"
            @change="handleStoragePoolChange"
            :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
            <a-select-option
              v-for="pool in storagePools"
              :key="pool.id"
              :value="pool.id"
              :label="formatStoragePoolLabel(pool)">
              {{ formatStoragePoolLabel(pool) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="secondaryvmname">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.secondary.vm.name')" :tooltip="$t('placeholder.ftctl.secondary.vm.name')" />
          </template>
          <a-input v-model:value="form.secondaryvmname" :placeholder="$t('placeholder.ftctl.secondary.vm.name')" />
        </a-form-item>
        <a-form-item name="backendmode" v-if="showBackendFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.backend.mode')" :tooltip="$t('placeholder.ftctl.backend.mode')" />
          </template>
          <a-select v-model:value="form.backendmode" @change="handleBackendModeChange">
            <a-select-option value="shared-blockcopy" :disabled="requiresRemoteNbdBackend">shared-blockcopy</a-select-option>
            <a-select-option value="remote-nbd">remote-nbd</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="fencingpolicy" v-if="showBackendFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.fencing.policy')" :tooltip="$t('placeholder.ftctl.fencing.policy')" />
          </template>
          <a-select v-model:value="form.fencingpolicy">
            <a-select-option value="manual-block">manual-block</a-select-option>
            <a-select-option value="peer-virsh-destroy">peer-virsh-destroy</a-select-option>
            <a-select-option value="ipmi">ipmi</a-select-option>
          </a-select>
        </a-form-item>
        <a-alert
          v-if="showIpmiFencingInfo"
          class="ftctl-auto-fields"
          :type="peerHostIpmiReady ? 'info' : 'warning'"
          :showIcon="true"
          :message="$t(peerHostIpmiReady ? 'message.ftctl.ipmi.oobm.ready' : 'message.ftctl.ipmi.oobm.missing')"
          :description="ipmiFencingDescription" />
        <a-form-item name="secondarytargetdir" v-if="showRemoteNbdFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.secondary.target.dir')" :tooltip="$t('placeholder.ftctl.secondary.target.dir')" />
          </template>
          <a-input v-model:value="form.secondarytargetdir" placeholder="/secondary/ftctl/<vm>" />
        </a-form-item>
        <a-form-item name="remotenbdexportaddr" v-if="showRemoteNbdFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.remote.nbd.export.address')" :tooltip="$t('placeholder.ftctl.remote.nbd.export.address')" />
          </template>
          <a-input v-model:value="form.remotenbdexportaddr" placeholder="10.0.0.12:10809" />
        </a-form-item>
        <a-alert
          v-if="showFtFields"
          class="ftctl-auto-fields"
          type="info"
          :showIcon="true"
          :message="$t('message.ftctl.ft.storage.auto')"
          :description="ftEndpointSummary" />
        <a-form-item v-if="showFtFields" name="manualxcoloendpoints">
          <a-checkbox v-model:checked="manualXcoloEndpoints">
            {{ $t('label.ftctl.xcolo.manual.endpoints') }}
          </a-checkbox>
        </a-form-item>
        <a-form-item name="xcoloproxyendpoint" v-if="showFtFields && manualXcoloEndpoints">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.xcolo.proxy.endpoint')" :tooltip="$t('placeholder.ftctl.xcolo.proxy.endpoint')" />
          </template>
          <a-input v-model:value="form.xcoloproxyendpoint" placeholder="tcp:10.10.10.21:9000" />
        </a-form-item>
        <a-form-item name="xcolonbdendpoint" v-if="showFtFields && manualXcoloEndpoints">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.xcolo.nbd.endpoint')" :tooltip="$t('placeholder.ftctl.xcolo.nbd.endpoint')" />
          </template>
          <a-input v-model:value="form.xcolonbdendpoint" placeholder="tcp:10.10.20.21:10809" />
        </a-form-item>
        <a-form-item name="xcolomigrateuri" v-if="showFtFields && manualXcoloEndpoints">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.xcolo.migrate.uri')" :tooltip="$t('placeholder.ftctl.xcolo.migrate.uri')" />
          </template>
          <a-input v-model:value="form.xcolomigrateuri" placeholder="tcp:10.10.20.21:9998" />
        </a-form-item>
      </a-form>
      <div class="action-button">
        <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
        <a-button :loading="loading" type="primary" @click="handleSubmit" ref="submit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'RegisterFtctlProtection',
  components: {
    TooltipLabel
  },
  props: {
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      hostsLoading: false,
      hosts: [],
      storagePoolsLoading: false,
      storagePools: [],
      manualXcoloEndpoints: false
    }
  },
  computed: {
    showFtFields () {
      return this.form?.mode === 'ft'
    },
    showBackendFields () {
      return this.form?.mode === 'ha' || this.form?.mode === 'dr'
    },
    showStorageFields () {
      return this.form?.mode === 'ha' || this.form?.mode === 'dr' || this.form?.mode === 'ft'
    },
    showRemoteNbdFields () {
      return this.showBackendFields && this.form?.backendmode === 'remote-nbd'
    },
    requiresRemoteNbdBackend () {
      return this.showBackendFields && this.form?.targetstoragescope === 'secondary-local'
    },
    ftEndpointSummary () {
      if (!this.form?.peerhostid) {
        return this.$t('message.ftctl.ft.endpoint.auto.pending')
      }
      return [
        `${this.$t('label.ftctl.xcolo.proxy.endpoint')}: ${this.form.xcoloproxyendpoint || '-'}`,
        `${this.$t('label.ftctl.xcolo.nbd.endpoint')}: ${this.form.xcolonbdendpoint || '-'}`,
        `${this.$t('label.ftctl.xcolo.migrate.uri')}: ${this.form.xcolomigrateuri || '-'}`
      ].join(' / ')
    },
    selectedPeerHost () {
      return this.hosts.find(host => host.id === this.form?.peerhostid)
    },
    peerHostOobm () {
      return this.selectedPeerHost?.outofbandmanagement || {}
    },
    showIpmiFencingInfo () {
      return this.showBackendFields && this.form?.fencingpolicy === 'ipmi'
    },
    peerHostIpmiReady () {
      const oobm = this.peerHostOobm
      return !!this.form?.peerhostid &&
        String(oobm?.enabled) === 'true' &&
        String(oobm?.driver || '').toLowerCase() === 'ipmitool' &&
        !!oobm?.address &&
        !!oobm?.username
    },
    ipmiFencingDescription () {
      if (!this.form?.peerhostid) {
        return this.$t('message.ftctl.ipmi.oobm.select.peer')
      }
      const oobm = this.peerHostOobm
      return [
        `${this.$t('label.ftctl.ipmi.driver')}: ${oobm?.driver || '-'}`,
        `${this.$t('label.ftctl.ipmi.address')}: ${oobm?.address || '-'}`,
        `${this.$t('label.ftctl.ipmi.port')}: ${oobm?.port || '623'}`,
        `${this.$t('label.username')}: ${oobm?.username || '-'}`
      ].join(' / ')
    }
  },
  created () {
    this.initForm()
    this.fetchHosts()
    this.fetchStoragePools()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        mode: 'ha',
        backendmode: 'shared-blockcopy',
        targetstoragescope: 'shared',
        targetstoragepoolid: null,
        fencingpolicy: 'manual-block',
        peerhostid: null,
        secondaryvmname: this.resource?.name ? `${this.resource.name}-standby` : null,
        secondarytargetdir: null,
        remotenbdexportaddr: null,
        xcoloproxyendpoint: null,
        xcolonbdendpoint: null,
        xcolomigrateuri: null
      })
      this.rules = reactive({
        mode: [{ required: true, message: `${this.$t('label.required')}` }],
        targetstoragepoolid: [{ required: true, message: `${this.$t('label.required')}` }],
        peerhostid: [{ required: true, message: `${this.$t('label.required')}` }]
      })
    },
    handleModeChange (mode) {
      if (mode === 'ft') {
        this.form.backendmode = null
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
        if (this.form.targetstoragepoolid) {
          this.applySelectedStoragePool(this.form.targetstoragepoolid)
        } else if (this.storagePools.length === 1) {
          this.form.targetstoragepoolid = this.storagePools[0].id
          this.applySelectedStoragePool(this.storagePools[0].id)
        }
        this.fetchStoragePools(this.form.peerhostid)
        this.applyPeerHostDefaults(this.form.peerhostid)
      } else {
        if (!this.form.backendmode) {
          this.form.backendmode = 'shared-blockcopy'
        }
        if (this.form.targetstoragepoolid) {
          this.applySelectedStoragePool(this.form.targetstoragepoolid)
        } else if (this.storagePools.length === 1) {
          this.form.targetstoragepoolid = this.storagePools[0].id
          this.applySelectedStoragePool(this.storagePools[0].id)
        }
        this.fetchStoragePools(this.form.peerhostid)
        this.form.xcoloproxyendpoint = null
        this.form.xcolonbdendpoint = null
        this.form.xcolomigrateuri = null
      }
    },
    handleBackendModeChange (backendMode) {
      if (this.form.targetstoragepoolid) {
        this.applySelectedStoragePool(this.form.targetstoragepoolid)
      }
      if (this.requiresRemoteNbdBackend && backendMode !== 'remote-nbd') {
        this.form.backendmode = 'remote-nbd'
        this.applyPeerHostDefaults(this.form.peerhostid)
        return
      }
      if (backendMode === 'shared-blockcopy') {
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
      } else if (backendMode === 'remote-nbd') {
        this.applyPeerHostDefaults(this.form.peerhostid)
      }
    },
    fetchStoragePools (peerHostId = this.form?.peerhostid) {
      if (!this.resource?.zoneid) {
        return
      }
      this.storagePoolsLoading = true
      const peerHost = this.hosts.find(host => host.id === peerHostId)
      const params = {
        zoneid: this.resource.zoneid,
        listall: true,
        page: 1,
        pagesize: 500
      }
      if (peerHost?.id) {
        params.hostid = peerHost.id
      }
      if (peerHost?.clusterid) {
        params.clusterid = peerHost.clusterid
      } else if (this.resource.clusterid) {
        params.clusterid = this.resource.clusterid
      }
      getAPI('listStoragePools', params).then((json) => {
        const pools = json?.liststoragepoolsresponse?.storagepool || []
        this.storagePools = pools.filter(pool => !pool.state || pool.state === 'Up')
        const selectedPool = this.storagePools.find(pool => pool.id === this.form.targetstoragepoolid)
        if (selectedPool) {
          this.applySelectedStoragePool(selectedPool.id)
        } else if (this.storagePools.length === 1 && this.showStorageFields) {
          this.form.targetstoragepoolid = this.storagePools[0].id
          this.applySelectedStoragePool(this.storagePools[0].id)
        } else if (this.showStorageFields) {
          this.form.targetstoragepoolid = null
          this.form.targetstoragescope = 'shared'
        }
      }).catch((error) => {
        this.$notifyError(error)
      }).finally(() => {
        this.storagePoolsLoading = false
      })
    },
    handleStoragePoolChange (poolId) {
      this.applySelectedStoragePool(poolId)
    },
    applySelectedStoragePool (poolId) {
      const pool = this.storagePools.find(item => item.id === poolId)
      this.form.targetstoragescope = this.deriveTargetStorageScope(pool)
      if (this.requiresRemoteNbdBackend) {
        this.form.backendmode = 'remote-nbd'
        this.form.secondarytargetdir = this.deriveSecondaryTargetDir(pool)
        this.applyPeerHostDefaults(this.form.peerhostid)
      } else if (this.form.backendmode === 'shared-blockcopy') {
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
      }
    },
    deriveTargetStorageScope (pool) {
      if (!pool?.scope) {
        return 'shared'
      }
      const scope = String(pool.scope).toLowerCase()
      return scope === 'host' ? 'secondary-local' : scope
    },
    deriveSecondaryTargetDir (pool) {
      const path = pool?.path || pool?.url
      if (!path) {
        return this.form.secondarytargetdir
      }
      return String(path).replace(/\/+$/, '')
    },
    formatStoragePoolLabel (pool) {
      const name = pool?.name || pool?.id
      const scope = pool?.scope || '-'
      const type = pool?.type || pool?.storagetype || pool?.pooltype || '-'
      const cluster = pool?.clustername ? ` / ${pool.clustername}` : ''
      return `${name} (${scope}${cluster}, ${type})`
    },
    formatHostLabel (host) {
      const name = host?.name || host?.ipaddress || host?.id
      const migrationIp = host?.migrationip ? ` / ${host.migrationip}` : ''
      return `${name}${migrationIp} (${host.id})`
    },
    fetchHosts () {
      this.hostsLoading = true
      const params = {
        zoneid: this.resource.zoneid,
        type: 'Routing',
        state: 'Up',
        listall: true,
        details: 'all'
      }
      if (this.resource.clusterid) {
        params.clusterid = this.resource.clusterid
      }
      getAPI('listHosts', params).then((json) => {
        const allHosts = json?.listhostsresponse?.host || []
        this.hosts = allHosts.filter(host => host.hypervisor === 'KVM' && host.id !== this.resource.hostid)
        if (this.hosts.length === 1) {
          this.handlePeerHostChange(this.hosts[0].id)
        }
      }).catch((error) => {
        this.$notifyError(error)
      }).finally(() => {
        this.hostsLoading = false
      })
    },
    handlePeerHostChange (peerHostId) {
      this.form.peerhostid = peerHostId
      this.applyPeerHostDefaults(peerHostId)
      if (this.showStorageFields) {
        this.fetchStoragePools(peerHostId)
      }
    },
    applyPeerHostDefaults (peerHostId) {
      const peerHost = this.hosts.find(host => host.id === peerHostId)
      if (!peerHost) {
        return
      }
      const managementAddress = peerHost.ipaddress || peerHost.name
      const migrationAddress = peerHost.migrationip || peerHost.ipaddress || peerHost.name
      if (this.showFtFields) {
        this.form.xcoloproxyendpoint = this.buildTcpEndpoint(managementAddress, 9000)
        this.form.xcolonbdendpoint = this.buildTcpEndpoint(migrationAddress, 10809)
        this.form.xcolomigrateuri = this.buildTcpEndpoint(migrationAddress, 9998)
      }
      if (this.showRemoteNbdFields) {
        this.form.remotenbdexportaddr = this.buildHostPortEndpoint(managementAddress, 10809)
      }
    },
    buildTcpEndpoint (address, port) {
      return address ? `tcp:${address}:${port}` : null
    },
    buildHostPortEndpoint (address, port) {
      return address ? `${address}:${port}` : null
    },
    closeAction () {
      this.$emit('close-action')
    },
    validateConditionalFields (values) {
      if (this.showRemoteNbdFields && (!values.secondarytargetdir || !values.remotenbdexportaddr)) {
        this.$message.error(this.$t('message.ftctl.validation.remote.nbd.required'))
        return false
      }
      if (this.showFtFields && (!values.xcoloproxyendpoint || !values.xcolonbdendpoint || !values.xcolomigrateuri)) {
        this.$message.error(this.$t('message.ftctl.validation.ft.required'))
        return false
      }
      if (this.showStorageFields && (!values.targetstoragepoolid || !values.targetstoragescope)) {
        this.$message.error(this.$t('message.ftctl.validation.target.storage.required'))
        return false
      }
      if (this.showIpmiFencingInfo && !this.peerHostIpmiReady) {
        this.$message.error(this.$t('message.ftctl.validation.ipmi.oobm.required'))
        return false
      }
      return true
    },
    handleSubmit (e) {
      if (e && e.preventDefault) {
        e.preventDefault()
      }
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        if (!this.validateConditionalFields(values)) {
          return
        }
        const params = {
          virtualmachineid: this.resource.id,
          mode: values.mode,
          provisioningbackend: 'cloud-managed',
          fencingpolicy: values.fencingpolicy
        }
        if (this.showBackendFields) {
          params.backendmode = values.backendmode
        }
        if (this.showStorageFields) {
          params.targetstoragescope = values.targetstoragescope
          params.targetstoragepoolid = values.targetstoragepoolid
        }
        if (values.peerhostid) {
          params.peerhostid = values.peerhostid
        }
        if (values.secondaryvmname) {
          params.secondaryvmname = values.secondaryvmname
        }
        if (this.showRemoteNbdFields) {
          params.secondarytargetdir = values.secondarytargetdir
          params.remotenbdexportaddr = values.remotenbdexportaddr
        }
        if (this.showFtFields) {
          params.xcoloproxyendpoint = values.xcoloproxyendpoint
          params.xcolonbdendpoint = values.xcolonbdendpoint
          params.xcolomigrateuri = values.xcolomigrateuri
        }
        this.loading = true
        postAPI('registerFtctlProtection', params).then((json) => {
          const payload = this.extractRegisterPayload(json)
          const jobId = this.extractJobId(payload)
          if (jobId) {
            this.$pollJob({
              jobId,
              title: this.$t('label.ftctl.protection.configure'),
              description: this.resource?.name || this.resource?.id,
              loadingMessage: `${this.$t('label.ftctl.protection.configure')} ${this.$t('label.in.progress')}`,
              successMessage: this.$t('message.ftctl.protection.saved'),
              errorMessage: `${this.$t('label.ftctl.protection.configure')} ${this.$t('label.failed')}`,
              resourceId: this.resource?.id,
              successMethod: () => {
                this.$emit('refresh-data')
              },
              errorMethod: () => {
                this.$emit('refresh-data')
              },
              catchMethod: () => {
                this.$emit('refresh-data')
              }
            })
          } else {
            this.$message.success(this.$t('message.ftctl.protection.saved'))
          }
          this.$emit('refresh-data')
          this.closeAction()
        }).catch((error) => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }).catch((error) => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    extractRegisterPayload (response) {
      return response?.registerftctlprotectionresponse || response || {}
    },
    extractJobId (payload) {
      return payload?.jobid || payload?.jobId || null
    }
  }
}
</script>

<style lang="scss" scoped>
.form-layout {
  width: 100%;
}

.ftctl-auto-fields {
  margin-bottom: 16px;

  :deep(.ant-alert-message) {
    font-size: 13px;
    line-height: 1.5;
    color: var(--text-color);
  }

  :deep(.ant-alert-description) {
    font-size: 12px;
    line-height: 1.45;
    color: var(--text-color-secondary);
  }

  :deep(.ant-alert-icon) {
    font-size: 14px;
  }
}

:global(.dark) .ftctl-auto-fields,
:global(.night) .ftctl-auto-fields,
:global([data-theme='dark']) .ftctl-auto-fields,
:global(body.dark) .ftctl-auto-fields,
:global(body.night) .ftctl-auto-fields {
  background: rgba(64, 169, 255, 0.1);
  border-color: rgba(64, 169, 255, 0.24);

  :deep(.ant-alert-message) {
    color: rgba(255, 255, 255, 0.82);
  }

  :deep(.ant-alert-description) {
    color: rgba(255, 255, 255, 0.58);
  }
}
</style>
