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
        <a-form-item name="backendmode" v-if="showBackendFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.backend.mode')" :tooltip="$t('placeholder.ftctl.backend.mode')" />
          </template>
          <a-select v-model:value="form.backendmode" @change="handleBackendModeChange">
            <a-select-option value="shared-blockcopy">shared-blockcopy</a-select-option>
            <a-select-option value="remote-nbd">remote-nbd</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="targetstoragepoolid" v-if="showBackendFields">
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
        <a-form-item name="fencingpolicy">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.fencing.policy')" :tooltip="$t('placeholder.ftctl.fencing.policy')" />
          </template>
          <a-select v-model:value="form.fencingpolicy">
            <a-select-option value="manual-block">manual-block</a-select-option>
            <a-select-option value="peer-virsh-destroy">peer-virsh-destroy</a-select-option>
            <a-select-option value="ipmi">ipmi</a-select-option>
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
            :filterOption="(input, option) => option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0">
            <a-select-option
              v-for="host in hosts"
              :key="host.id"
              :label="`${host.name || host.ipaddress} (${host.id})`">
              {{ host.name || host.ipaddress }} ({{ host.id }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="secondaryvmname">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.secondary.vm.name')" :tooltip="$t('placeholder.ftctl.secondary.vm.name')" />
          </template>
          <a-input v-model:value="form.secondaryvmname" :placeholder="$t('placeholder.ftctl.secondary.vm.name')" />
        </a-form-item>
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
        <a-form-item name="xcoloproxyendpoint" v-if="showFtFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.xcolo.proxy.endpoint')" :tooltip="$t('placeholder.ftctl.xcolo.proxy.endpoint')" />
          </template>
          <a-input v-model:value="form.xcoloproxyendpoint" placeholder="tcp:10.10.10.21:9000" />
        </a-form-item>
        <a-form-item name="xcolonbdendpoint" v-if="showFtFields">
          <template #label>
            <tooltip-label :title="$t('label.ftctl.xcolo.nbd.endpoint')" :tooltip="$t('placeholder.ftctl.xcolo.nbd.endpoint')" />
          </template>
          <a-input v-model:value="form.xcolonbdendpoint" placeholder="tcp:10.10.20.21:10809" />
        </a-form-item>
        <a-form-item name="xcolomigrateuri" v-if="showFtFields">
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
import eventBus from '@/config/eventBus'
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
      storagePools: []
    }
  },
  computed: {
    showFtFields () {
      return this.form?.mode === 'ft'
    },
    showBackendFields () {
      return this.form?.mode === 'ha' || this.form?.mode === 'dr'
    },
    showRemoteNbdFields () {
      return this.showBackendFields && this.form?.backendmode === 'remote-nbd'
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
        this.form.targetstoragescope = null
        this.form.targetstoragepoolid = null
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
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
        this.form.xcoloproxyendpoint = null
        this.form.xcolonbdendpoint = null
        this.form.xcolomigrateuri = null
      }
    },
    handleBackendModeChange (backendMode) {
      if (this.form.targetstoragepoolid) {
        this.applySelectedStoragePool(this.form.targetstoragepoolid)
      }
      if (backendMode === 'shared-blockcopy') {
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
      }
    },
    fetchStoragePools () {
      if (!this.resource?.zoneid) {
        return
      }
      this.storagePoolsLoading = true
      const params = {
        zoneid: this.resource.zoneid,
        listall: true,
        pagesize: 500
      }
      if (this.resource.clusterid) {
        params.clusterid = this.resource.clusterid
      }
      getAPI('listStoragePools', params).then((json) => {
        const pools = json?.liststoragepoolsresponse?.storagepool || []
        this.storagePools = pools.filter(pool => !pool.state || pool.state === 'Up')
        if (this.storagePools.length === 1 && this.showBackendFields) {
          this.form.targetstoragepoolid = this.storagePools[0].id
          this.applySelectedStoragePool(this.storagePools[0].id)
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
    },
    deriveTargetStorageScope (pool) {
      if (!pool?.scope) {
        return 'shared'
      }
      return String(pool.scope).toLowerCase()
    },
    formatStoragePoolLabel (pool) {
      const name = pool?.name || pool?.id
      const scope = pool?.scope || '-'
      const type = pool?.type || pool?.storagetype || pool?.pooltype || '-'
      const cluster = pool?.clustername ? ` / ${pool.clustername}` : ''
      return `${name} (${scope}${cluster}, ${type})`
    },
    fetchHosts () {
      this.hostsLoading = true
      const params = {
        zoneid: this.resource.zoneid,
        type: 'Routing',
        state: 'Up',
        listall: true,
        details: 'min'
      }
      if (this.resource.clusterid) {
        params.clusterid = this.resource.clusterid
      }
      getAPI('listHosts', params).then((json) => {
        const allHosts = json?.listhostsresponse?.host || []
        this.hosts = allHosts.filter(host => host.hypervisor === 'KVM' && host.id !== this.resource.hostid)
        if (this.hosts.length === 1) {
          this.form.peerhostid = this.hosts[0].id
        }
      }).catch((error) => {
        this.$notifyError(error)
      }).finally(() => {
        this.hostsLoading = false
      })
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
      if (this.showBackendFields && (!values.targetstoragepoolid || !values.targetstoragescope)) {
        this.$message.error(this.$t('message.ftctl.validation.target.storage.required'))
        return false
      }
      return true
    },
    handleSubmit (e) {
      e?.preventDefault?.()
      if (this.loading) return
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        if (!this.validateConditionalFields(values)) {
          return
        }
        const params = {
          virtualmachineid: this.resource.id,
          mode: values.mode,
          fencingpolicy: values.fencingpolicy
        }
        if (this.showBackendFields) {
          params.backendmode = values.backendmode
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
        postAPI('registerFtctlProtection', params).then(() => {
          this.$message.success(this.$t('message.ftctl.protection.saved'))
          this.$emit('refresh-data')
          eventBus.emit('vm-refresh-data')
          this.closeAction()
        }).catch((error) => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }).catch((error) => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.form-layout {
  width: 100%;
}
</style>
