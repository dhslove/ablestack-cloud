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
  <a-spin :spinning="loadingState">
    <div class="ftctl-tab">
      <a-card size="small" :bordered="true" class="ftctl-tab__section">
        <template #title>FTCTL</template>
        <template #extra>
          <a-space wrap>
            <a-button
              v-if="canConfigureProtection"
              type="primary"
              @click="openProtectionModal"
              :disabled="unsafeVmState">
              Protection
            </a-button>
            <a-button @click="fetchAll" :loading="loadingState">Refresh</a-button>
          </a-space>
        </template>

        <a-alert
          v-if="errorMessage"
          type="warning"
          show-icon
          :message="errorMessage"
          class="ftctl-tab__alert" />

        <a-alert
          v-if="lastAction.message"
          :type="lastAction.success ? 'success' : 'error'"
          show-icon
          class="ftctl-tab__alert">
          <template #message>
            <div>{{ lastAction.message }}</div>
            <div v-if="lastAction.timestamp" class="ftctl-tab__meta">Updated: {{ lastAction.timestamp }}</div>
          </template>
        </a-alert>

        <a-alert
          v-if="operationalSummary.message"
          :type="operationalSummary.type"
          show-icon
          class="ftctl-tab__alert">
          <template #message>
            <div>{{ operationalSummary.message }}</div>
            <div class="ftctl-tab__meta">
              Events: {{ eventStats.total }} | Warnings: {{ eventStats.warn }} | Failures: {{ eventStats.fail }}
            </div>
          </template>
        </a-alert>

        <a-space v-if="canRunActions" wrap class="ftctl-tab__operations">
          <template v-for="action in actionDefinitions" :key="action.api">
            <a-popconfirm
              v-if="action.confirm"
              placement="topRight"
              :title="action.confirmMessage"
              :ok-text="$t('label.yes')"
              :cancel-text="$t('label.no')"
              @confirm="runAction(action.api)">
              <a-button
                size="small"
                :danger="action.danger"
                :disabled="action.disabled"
                :loading="actionLoading[action.api]">
                {{ action.label }}
              </a-button>
            </a-popconfirm>
            <a-button
              v-else
              size="small"
              :disabled="action.disabled"
              :loading="actionLoading[action.api]"
              @click="runAction(action.api)">
              {{ action.label }}
            </a-button>
          </template>
        </a-space>

        <div class="ftctl-tab__summary">
          <div class="ftctl-tab__summary-item">
            <div class="ftctl-tab__summary-label">Protection</div>
            <a-tag v-if="protection.protectionstate" :color="stateTagColor(protection.protectionstate)">{{ protection.protectionstate }}</a-tag>
            <span v-else>-</span>
          </div>
          <div class="ftctl-tab__summary-item">
            <div class="ftctl-tab__summary-label">Transport</div>
            <a-tag v-if="protection.transportstate" :color="stateTagColor(protection.transportstate)">{{ protection.transportstate }}</a-tag>
            <span v-else>-</span>
          </div>
          <div class="ftctl-tab__summary-item">
            <div class="ftctl-tab__summary-label">Active Side</div>
            <a-tag v-if="protection.activeside" :color="sideTagColor(protection.activeside)">{{ protection.activeside }}</a-tag>
            <span v-else>-</span>
          </div>
          <div class="ftctl-tab__summary-item">
            <div class="ftctl-tab__summary-label">Fencing</div>
            <a-tag v-if="protection.fencingstate" :color="stateTagColor(protection.fencingstate)">{{ protection.fencingstate }}</a-tag>
            <span v-else>-</span>
          </div>
        </div>
      </a-card>

      <a-card size="small" :bordered="true" class="ftctl-tab__section" title="Protection Details">
      <a-descriptions bordered :column="descriptionColumn" size="small">
        <a-descriptions-item label="Enabled">
          <a-tag :color="booleanTagColor(protection.enabled)">{{ formatBoolean(protection.enabled) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Mode">
          <a-tag v-if="protection.mode" color="blue">{{ protection.mode }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Backend Mode">{{ protection.backendmode || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Target Storage Scope">{{ protection.targetstoragescope || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Fencing Policy">{{ protection.fencingpolicy || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Peer Host ID">{{ protection.peerhostid || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Secondary VM Name">{{ protection.secondaryvmname || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Secondary Target Dir">{{ protection.secondarytargetdir || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Remote NBD Export Address">{{ protection.remotenbdexportaddr || '-' }}</a-descriptions-item>
        <a-descriptions-item label="X-COLO Proxy Endpoint">{{ protection.xcoloproxyendpoint || '-' }}</a-descriptions-item>
        <a-descriptions-item label="X-COLO NBD Endpoint">{{ protection.xcolonbdendpoint || '-' }}</a-descriptions-item>
        <a-descriptions-item label="X-COLO Migrate URI">{{ protection.xcolomigrateuri || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Protection State">
          <a-tag v-if="protection.protectionstate" :color="stateTagColor(protection.protectionstate)">{{ protection.protectionstate }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Transport State">
          <a-tag v-if="protection.transportstate" :color="stateTagColor(protection.transportstate)">{{ protection.transportstate }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Active Side">
          <a-tag v-if="protection.activeside" :color="sideTagColor(protection.activeside)">{{ protection.activeside }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Admin State">
          <a-tag v-if="protection.adminstate" :color="stateTagColor(protection.adminstate)">{{ protection.adminstate }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Fencing State">
          <a-tag v-if="protection.fencingstate" :color="stateTagColor(protection.fencingstate)">{{ protection.fencingstate }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Last Error">{{ protection.lasterror || '-' }}</a-descriptions-item>
      </a-descriptions>
      </a-card>

      <a-card size="small" :bordered="true" class="ftctl-tab__section" title="Check">
      <a-descriptions bordered :column="descriptionColumn" size="small">
        <a-descriptions-item label="Check Result">
          <a-tag v-if="checkResult.result" :color="stateTagColor(checkResult.result)">{{ checkResult.result }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Inventory Result">
          <a-tag v-if="checkResult.inventoryresult" :color="stateTagColor(checkResult.inventoryresult)">{{ checkResult.inventoryresult }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Primary RC">{{ formatNumber(checkResult.primaryrc) }}</a-descriptions-item>
        <a-descriptions-item label="Peer RC">{{ formatNumber(checkResult.peerrc) }}</a-descriptions-item>
      </a-descriptions>
      </a-card>

      <a-card size="small" :bordered="true" class="ftctl-tab__section" title="Health">
      <a-descriptions bordered :column="descriptionColumn" size="small">
        <a-descriptions-item label="Health Result">
          <a-tag v-if="healthResult.result" :color="stateTagColor(healthResult.result)">{{ healthResult.result }}</a-tag>
          <span v-else>-</span>
        </a-descriptions-item>
        <a-descriptions-item label="Host ID">{{ formatNumber(healthResult.hostid) }}</a-descriptions-item>
        <a-descriptions-item label="URI">{{ healthResult.uri || '-' }}</a-descriptions-item>
        <a-descriptions-item label="RC">{{ formatNumber(healthResult.rc) }}</a-descriptions-item>
      </a-descriptions>
      </a-card>

      <a-card size="small" :bordered="true" class="ftctl-tab__section" title="Events">
      <a-table
        size="small"
        :columns="eventColumns"
        :dataSource="events"
        :pagination="false"
        :rowKey="record => `${record.timestamp || 'na'}-${record.event || 'na'}-${record.stage || 'na'}`"
        :expandable="{ rowExpandable: (record) => hasEventDetails(record) }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'result'">
            <a-tag v-if="record.result" :color="stateTagColor(record.result)">{{ record.result }}</a-tag>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'timestamp'">
            <span>{{ record.timestamp || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'details'">
            <span>{{ summarizeEventDetails(record.details) }}</span>
          </template>
          <template v-else>
            <span>{{ record[column.key] || '-' }}</span>
          </template>
        </template>
        <template #expandedRowRender="{ record }">
          <pre class="ftctl-tab__details">{{ formatEventDetails(record.details) }}</pre>
        </template>
      </a-table>
      </a-card>

      <a-modal
        :visible="showProtectionModal"
        title="FTCTL Protection"
        :maskClosable="false"
        :closable="true"
        :footer="null"
        width="720px"
        @cancel="closeProtectionModal">
        <RegisterFtctlProtection
          :resource="resource"
          @close-action="closeProtectionModal"
          @refresh-data="handleProtectionSaved" />
      </a-modal>
    </div>
  </a-spin>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import eventBus from '@/config/eventBus'
import RegisterFtctlProtection from '@/views/compute/RegisterFtctlProtection.vue'

export default {
  name: 'FtctlTab',
  components: {
    RegisterFtctlProtection
  },
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      loadingState: false,
      errorMessage: null,
      protection: {},
      checkResult: {},
      healthResult: {},
      events: [],
      showProtectionModal: false,
      lastAction: {
        success: false,
        message: null,
        timestamp: null
      },
      actionLoading: {
        pauseFtctlProtection: false,
        resumeFtctlProtection: false,
        failoverFtctlProtection: false,
        failbackFtctlProtection: false,
        confirmFtctlFence: false,
        clearFtctlFence: false
      },
      eventColumns: [
        { title: 'Timestamp', key: 'timestamp', dataIndex: 'timestamp', width: 220 },
        { title: 'Stage', key: 'stage', dataIndex: 'stage', width: 120 },
        { title: 'Event', key: 'event', dataIndex: 'event', width: 220 },
        { title: 'Result', key: 'result', dataIndex: 'result', width: 120 },
        { title: 'Details', key: 'details', dataIndex: 'details' }
      ]
    }
  },
  computed: {
    descriptionColumn () {
      return this.$store.getters.device === 'mobile' ? 1 : 2
    },
    unsafeVmState () {
      return ['Destroyed', 'Expunging', 'Error'].includes(this.resource?.state) || this.resource?.hostcontrolstate === 'Offline'
    },
    supportedVm () {
      return ['Admin'].includes(this.$store.getters.userInfo.roletype) &&
        this.resource?.hypervisor === 'KVM' &&
        this.resource?.vmtype !== 'sharedfsvm'
    },
    protectionEnabled () {
      return this.protection.enabled === true || this.protection.enabled === 'true'
    },
    canConfigureProtection () {
      return 'registerFtctlProtection' in this.$store.getters.apis && this.supportedVm
    },
    canRunActions () {
      return ['pauseFtctlProtection', 'resumeFtctlProtection', 'failoverFtctlProtection', 'failbackFtctlProtection', 'confirmFtctlFence', 'clearFtctlFence']
        .some(api => api in this.$store.getters.apis) && this.supportedVm
    },
    canLoadEvents () {
      return 'getFtctlEvents' in this.$store.getters.apis
    },
    eventStats () {
      const stats = { total: this.events.length, warn: 0, fail: 0 }
      this.events.forEach(event => {
        const result = String(event.result || '').toLowerCase()
        if (result === 'warn') {
          stats.warn += 1
        } else if (result === 'fail' || result === 'error' || result === 'locked' || result === 'timeout') {
          stats.fail += 1
        }
      })
      return stats
    },
    operationalSummary () {
      const protection = String(this.protection.protectionstate || '').toLowerCase()
      const transport = String(this.protection.transportstate || '').toLowerCase()
      const fencing = String(this.protection.fencingstate || '').toLowerCase()

      if (this.eventStats.fail > 0 || ['error', 'failed_over', 'rearm_exhausted'].includes(protection)) {
        return { type: 'error', message: 'FTCTL currently has failure indicators that require operator review.' }
      }
      if (this.eventStats.warn > 0 || ['degraded', 'transient_loss', 'peer_unreachable', 'rearm_pending', 'rearm_backoff'].includes(transport) || ['required', 'failed', 'manual-required'].includes(fencing)) {
        return { type: 'warning', message: 'FTCTL currently has warning indicators. Review recent events before taking action.' }
      }
      if (protection || transport || fencing) {
        return { type: 'info', message: 'FTCTL status is stable based on the latest cached state and recent events.' }
      }
      return { type: null, message: null }
    },
    actionDefinitions () {
      return [
        {
          api: 'pauseFtctlProtection',
          label: 'Pause',
          disabled: !this.actionAvailable('pauseFtctlProtection') || !this.protectionEnabled || this.protection.adminstate === 'paused'
        },
        {
          api: 'resumeFtctlProtection',
          label: 'Resume',
          disabled: !this.actionAvailable('resumeFtctlProtection') || !this.protectionEnabled || this.protection.adminstate !== 'paused'
        },
        {
          api: 'failoverFtctlProtection',
          label: 'Failover',
          danger: true,
          confirm: true,
          confirmMessage: 'Trigger FTCTL failover for this virtual machine?',
          disabled: !this.actionAvailable('failoverFtctlProtection') || !this.protectionEnabled || this.protection.activeside === 'secondary'
        },
        {
          api: 'failbackFtctlProtection',
          label: 'Failback',
          danger: true,
          confirm: true,
          confirmMessage: 'Trigger FTCTL failback for this virtual machine?',
          disabled: !this.actionAvailable('failbackFtctlProtection') || !this.protectionEnabled || this.protection.activeside !== 'secondary'
        },
        {
          api: 'confirmFtctlFence',
          label: 'Confirm Fence',
          confirm: true,
          confirmMessage: 'Confirm FTCTL fence for this virtual machine?',
          disabled: !this.actionAvailable('confirmFtctlFence') || !['required', 'failed', 'manual-required'].includes(String(this.protection.fencingstate || '').toLowerCase())
        },
        {
          api: 'clearFtctlFence',
          label: 'Clear Fence',
          confirm: true,
          confirmMessage: 'Clear FTCTL fence state for this virtual machine?',
          disabled: !this.actionAvailable('clearFtctlFence') || !this.protection.fencingstate || String(this.protection.fencingstate).toLowerCase() === 'clear'
        }
      ]
    }
  },
  created () {
    this.fetchAll()
  },
  methods: {
    actionAvailable (apiName) {
      return apiName in this.$store.getters.apis && this.supportedVm && !this.unsafeVmState
    },
    openProtectionModal () {
      this.showProtectionModal = true
    },
    closeProtectionModal () {
      this.showProtectionModal = false
    },
    handleProtectionSaved () {
      this.closeProtectionModal()
      this.fetchAll()
    },
    formatNumber (value) {
      return value === null || value === undefined || value === '' ? '-' : value
    },
    formatBoolean (value) {
      if (value === true || value === 'true') {
        return 'Enabled'
      }
      if (value === false || value === 'false') {
        return 'Disabled'
      }
      return '-'
    },
    booleanTagColor (value) {
      if (value === true || value === 'true') {
        return 'green'
      }
      if (value === false || value === 'false') {
        return 'default'
      }
      return 'default'
    },
    stateTagColor (value) {
      const normalized = String(value || '').toLowerCase()
      if (['ok', 'protected', 'colo_running', 'mirroring', 'reachable'].includes(normalized)) {
        return 'green'
      }
      if (['warn', 'degraded', 'transient_loss', 'peer_unreachable', 'rearm_pending', 'rearm_backoff', 'failing_over', 'failing_back', 'paused', 'required'].includes(normalized)) {
        return 'orange'
      }
      if (['fail', 'error', 'failed_over', 'rearm_exhausted', 'timeout', 'locked'].includes(normalized)) {
        return 'red'
      }
      if (['clear', 'active'].includes(normalized)) {
        return 'blue'
      }
      return 'blue'
    },
    sideTagColor (value) {
      const normalized = String(value || '').toLowerCase()
      if (normalized === 'primary') {
        return 'blue'
      }
      if (normalized === 'secondary') {
        return 'purple'
      }
      return 'default'
    },
    extractErrorMessage (error, commandName) {
      const responseName = `${commandName.toLowerCase()}response`
      return error?.response?.data?.[responseName]?.errortext || error?.message || `Failed to execute ${commandName}`
    },
    hasEventDetails (record) {
      return !!(record && record.details)
    },
    summarizeEventDetails (details) {
      if (!details) {
        return '-'
      }
      try {
        const parsed = JSON.parse(details)
        const summary = Object.entries(parsed).slice(0, 2).map(([key, value]) => `${key}=${value}`).join(', ')
        return summary || '{}'
      } catch (e) {
        return String(details).length > 80 ? `${String(details).slice(0, 77)}...` : String(details)
      }
    },
    formatEventDetails (details) {
      if (!details) {
        return '-'
      }
      try {
        return JSON.stringify(JSON.parse(details), null, 2)
      } catch (e) {
        return String(details)
      }
    },
    async fetchAll () {
      this.loadingState = true
      this.errorMessage = null
      try {
        await Promise.all([
          this.fetchProtection(),
          this.fetchCheck(),
          this.fetchHealth(),
          this.fetchEvents()
        ])
      } finally {
        this.loadingState = false
      }
    },
    async fetchProtection () {
      if (!this.resource?.id) {
        return
      }
      try {
        const response = await getAPI('getFtctlProtection', { virtualmachineid: this.resource.id })
        this.protection = response?.getftctlprotectionresponse || {}
      } catch (error) {
        this.protection = {}
        this.errorMessage = this.extractErrorMessage(error, 'getFtctlProtection')
      }
    },
    async fetchCheck () {
      if (!this.resource?.id || !('getFtctlCheck' in this.$store.getters.apis)) {
        return
      }
      try {
        const response = await getAPI('getFtctlCheck', { virtualmachineid: this.resource.id })
        this.checkResult = response?.getftctlcheckresponse || {}
      } catch (error) {
        this.checkResult = {}
        this.errorMessage = this.extractErrorMessage(error, 'getFtctlCheck')
      }
    },
    async fetchHealth () {
      if (!this.resource?.id || !('getFtctlHealth' in this.$store.getters.apis)) {
        return
      }
      try {
        const response = await getAPI('getFtctlHealth', { virtualmachineid: this.resource.id })
        this.healthResult = response?.getftctlhealthresponse || {}
      } catch (error) {
        this.healthResult = {}
        this.errorMessage = this.extractErrorMessage(error, 'getFtctlHealth')
      }
    },
    async fetchEvents () {
      if (!this.resource?.id || !this.canLoadEvents) {
        return
      }
      try {
        const response = await getAPI('getFtctlEvents', { virtualmachineid: this.resource.id, limit: 10 })
        this.events = (response?.getftctleventsresponse?.events || []).slice().sort((a, b) => {
          return String(b.timestamp || '').localeCompare(String(a.timestamp || ''))
        })
      } catch (error) {
        this.events = []
        this.errorMessage = this.extractErrorMessage(error, 'getFtctlEvents')
      }
    },
    async runAction (commandName) {
      if (!this.resource?.id || !(commandName in this.$store.getters.apis)) {
        return
      }
      this.actionLoading[commandName] = true
      this.errorMessage = null
      this.lastAction = {
        success: false,
        message: null,
        timestamp: null
      }
      try {
        const response = await postAPI(commandName, { virtualmachineid: this.resource.id })
        const responseName = `${commandName.toLowerCase()}response`
        const payload = response?.[responseName] || {}
        this.applyActionPayload(payload)
        this.$message.success(`${commandName} succeeded`)
        this.lastAction = {
          success: true,
          message: this.buildActionMessage(commandName, payload),
          timestamp: new Date().toLocaleString()
        }
        eventBus.emit('vm-refresh-data')
        await this.fetchAll()
      } catch (error) {
        this.errorMessage = this.extractErrorMessage(error, commandName)
        this.lastAction = {
          success: false,
          message: this.errorMessage,
          timestamp: new Date().toLocaleString()
        }
      } finally {
        this.actionLoading[commandName] = false
      }
    },
    buildActionMessage (commandName, payload) {
      let message = `${commandName} completed`
      if (payload.result) {
        message += ` (${payload.result})`
      }
      const stateParts = []
      if (payload.protectionstate) {
        stateParts.push(`protection=${payload.protectionstate}`)
      }
      if (payload.transportstate) {
        stateParts.push(`transport=${payload.transportstate}`)
      }
      if (payload.activeside) {
        stateParts.push(`active=${payload.activeside}`)
      }
      return stateParts.length > 0 ? `${message} - ${stateParts.join(', ')}` : message
    },
    applyActionPayload (payload) {
      if (!payload || Object.keys(payload).length === 0) {
        return
      }
      if (payload.mode !== undefined) this.protection.mode = payload.mode
      if (payload.protectionstate !== undefined) this.protection.protectionstate = payload.protectionstate
      if (payload.transportstate !== undefined) this.protection.transportstate = payload.transportstate
      if (payload.activeside !== undefined) this.protection.activeside = payload.activeside
      if (payload.lasterror !== undefined) this.protection.lasterror = payload.lasterror
      if (payload.adminstate !== undefined) this.protection.adminstate = payload.adminstate
      if (payload.fencingstate !== undefined) this.protection.fencingstate = payload.fencingstate
    }
  },
  watch: {
    'resource.id': {
      handler (value, oldValue) {
        if (value && value !== oldValue) {
          this.fetchAll()
        }
      }
    },
    loading: {
      immediate: true,
      handler (value) {
        if (value) {
          this.loadingState = true
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.ftctl-tab {
  &__section {
    margin-bottom: 12px;
  }

  &__alert,
  &__operations {
    margin-bottom: 12px;
  }

  &__meta {
    margin-top: 4px;
    font-size: 12px;
    opacity: 0.8;
  }

  &__summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    gap: 12px;
  }

  &__summary-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__summary-label {
    font-size: 12px;
    opacity: 0.8;
  }

  &__details {
    margin: 0;
    padding: 8px;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    background: transparent;
  }
}
</style>
