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
        <a-form-item name="mode" label="Mode">
          <a-select v-model:value="form.mode" v-focus="true" @change="handleModeChange">
            <a-select-option value="ha">HA</a-select-option>
            <a-select-option value="dr">DR</a-select-option>
            <a-select-option value="ft">FT</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="backendmode" label="Backend Mode" v-if="showBackendFields">
          <a-select v-model:value="form.backendmode" @change="handleBackendModeChange">
            <a-select-option value="shared-blockcopy">shared-blockcopy</a-select-option>
            <a-select-option value="remote-nbd">remote-nbd</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="targetstoragescope" label="Target Storage Scope" v-if="showBackendFields">
          <a-select v-model:value="form.targetstoragescope">
            <a-select-option value="shared">shared</a-select-option>
            <a-select-option value="secondary-local">secondary-local</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="fencingpolicy" label="Fencing Policy">
          <a-select v-model:value="form.fencingpolicy">
            <a-select-option value="manual-block">manual-block</a-select-option>
            <a-select-option value="peer-virsh-destroy">peer-virsh-destroy</a-select-option>
            <a-select-option value="ipmi">ipmi</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="peerhostid" label="Peer Host ID">
          <a-select
            v-model:value="form.peerhostid"
            :loading="hostsLoading"
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
        <a-form-item name="secondaryvmname" label="Secondary VM Name">
          <a-input v-model:value="form.secondaryvmname" />
        </a-form-item>
        <a-form-item name="secondarytargetdir" label="Secondary Target Dir" v-if="showRemoteNbdFields">
          <a-input v-model:value="form.secondarytargetdir" placeholder="/secondary/ftctl/<vm>" />
        </a-form-item>
        <a-form-item name="remotenbdexportaddr" label="Remote NBD Export Address" v-if="showRemoteNbdFields">
          <a-input v-model:value="form.remotenbdexportaddr" placeholder="10.0.0.12" />
        </a-form-item>
        <a-form-item name="xcoloproxyendpoint" label="X-COLO Proxy Endpoint" v-if="showFtFields">
          <a-input v-model:value="form.xcoloproxyendpoint" placeholder="tcp:10.10.10.21:9000" />
        </a-form-item>
        <a-form-item name="xcolonbdendpoint" label="X-COLO NBD Endpoint" v-if="showFtFields">
          <a-input v-model:value="form.xcolonbdendpoint" placeholder="tcp:10.10.20.21:10809" />
        </a-form-item>
        <a-form-item name="xcolomigrateuri" label="X-COLO Migrate URI" v-if="showFtFields">
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

export default {
  name: 'RegisterFtctlProtection',
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
      hosts: []
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
  },
  methods: {
    initForm () {
      this.formRef = ref()
        this.form = reactive({
          mode: 'ha',
          backendmode: 'shared-blockcopy',
          targetstoragescope: 'shared',
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
        peerhostid: [{ required: true, message: `${this.$t('label.required')}` }]
      })
    },
    handleModeChange (mode) {
      if (mode === 'ft') {
        this.form.backendmode = null
        this.form.targetstoragescope = null
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
      } else {
        if (!this.form.backendmode) {
          this.form.backendmode = 'shared-blockcopy'
        }
        if (!this.form.targetstoragescope) {
          this.form.targetstoragescope = this.form.backendmode === 'remote-nbd' ? 'secondary-local' : 'shared'
        }
        this.form.xcoloproxyendpoint = null
        this.form.xcolonbdendpoint = null
        this.form.xcolomigrateuri = null
      }
    },
    handleBackendModeChange (backendMode) {
      if (backendMode === 'remote-nbd') {
        this.form.targetstoragescope = 'secondary-local'
      } else if (backendMode === 'shared-blockcopy') {
        this.form.targetstoragescope = 'shared'
        this.form.secondarytargetdir = null
        this.form.remotenbdexportaddr = null
      }
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
        this.$message.error('remote-nbd requires secondary target directory and export address')
        return false
      }
      if (this.showFtFields && (!values.xcoloproxyendpoint || !values.xcolonbdendpoint || !values.xcolomigrateuri)) {
        this.$message.error('FT mode requires x-colo proxy, NBD, and migrate fields')
        return false
      }
      if (this.showBackendFields && !values.targetstoragescope) {
        this.$message.error('HA/DR mode requires target storage scope')
        return false
      }
      return true
    },
    handleSubmit (e) {
      e.preventDefault()
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
          this.$message.success('FTCTL protection saved')
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
  width: 80vw;
  @media (min-width: 700px) {
    width: 480px;
  }
}
</style>
