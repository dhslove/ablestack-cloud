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
        <template #title>{{ $t('label.ftctl.fault.protection') }}</template>
        <template #extra>
          <a-space wrap>
            <a-button
              v-if="canConfigureProtection"
              type="primary"
              @click="openProtectionModal"
              :disabled="unsafeVmState || protectionConfigured">
              <template #icon><SafetyCertificateOutlined /></template>
              {{ $t('label.ftctl.protection.configure') }}
            </a-button>
            <a-button @click="fetchAll" :loading="loadingState">
              <template #icon><SyncOutlined /></template>
              {{ $t('label.refresh') }}
            </a-button>
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
            <div v-if="lastAction.timestamp" class="ftctl-tab__meta">{{ $t('label.updated') }}: {{ lastAction.timestamp }}</div>
          </template>
        </a-alert>

        <template v-if="protectionConfigured">
          <a-alert
            v-if="operationalSummary.message"
            :type="operationalSummary.type"
            show-icon
            class="ftctl-tab__alert">
            <template #message>
              <div>{{ operationalSummary.message }}</div>
              <div class="ftctl-tab__meta">
                {{ $t('label.events') }}: {{ eventStats.total }} |
                {{ $t('label.ftctl.warnings') }}: {{ eventStats.warn }} |
                {{ $t('label.ftctl.failures') }}: {{ eventStats.fail }}
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
                  <template #icon><component :is="action.icon" /></template>
                  {{ action.label }}
                </a-button>
              </a-popconfirm>
              <a-button
                v-else
                size="small"
                :disabled="action.disabled"
                :loading="actionLoading[action.api]"
                @click="runAction(action.api)">
                <template #icon><component :is="action.icon" /></template>
                {{ action.label }}
              </a-button>
            </template>
          </a-space>

          <div class="ftctl-tab__summary">
            <div class="ftctl-tab__summary-item">
              <div class="ftctl-tab__summary-label">{{ $t('label.ftctl.protection.state') }}</div>
              <a-tag v-if="protection.protectionstate" :color="stateTagColor(protection.protectionstate)">{{ protection.protectionstate }}</a-tag>
              <span v-else>-</span>
            </div>
            <div class="ftctl-tab__summary-item">
              <div class="ftctl-tab__summary-label">{{ $t('label.ftctl.transport.state') }}</div>
              <a-tag v-if="protection.transportstate" :color="stateTagColor(protection.transportstate)">{{ protection.transportstate }}</a-tag>
              <span v-else>-</span>
            </div>
            <div class="ftctl-tab__summary-item">
              <div class="ftctl-tab__summary-label">{{ $t('label.ftctl.active.side') }}</div>
              <a-tag v-if="protection.activeside" :color="sideTagColor(protection.activeside)">{{ protection.activeside }}</a-tag>
              <span v-else>-</span>
            </div>
            <div class="ftctl-tab__summary-item">
              <div class="ftctl-tab__summary-label">{{ $t('label.ftctl.fencing.state') }}</div>
              <a-tag v-if="protection.fencingstate" :color="stateTagColor(protection.fencingstate)">{{ protection.fencingstate }}</a-tag>
              <span v-else>-</span>
            </div>
          </div>
        </template>

        <div v-else class="ftctl-tab__empty-state">
          <SafetyCertificateOutlined class="ftctl-tab__empty-icon" />
          <div>
            <div class="ftctl-tab__empty-title">{{ $t('message.ftctl.protection.not.configured') }}</div>
            <div class="ftctl-tab__empty-description">{{ $t('message.ftctl.protection.not.configured.desc') }}</div>
          </div>
        </div>
      </a-card>

      <template v-if="protectionConfigured">
        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.ftctl.protection.details')">
          <a-descriptions bordered :column="descriptionColumn" size="small">
            <a-descriptions-item :label="$t('label.enabled')">
              <a-tag :color="booleanTagColor(protection.enabled)">{{ formatBoolean(protection.enabled) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.mode')">
              <a-tag v-if="protection.mode" color="blue">{{ protection.mode }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.backend.mode')">{{ protection.backendmode || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.target.storage.scope')">{{ protection.targetstoragescope || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.target.storage.pool')">{{ protection.targetstoragepoolname || protection.targetstoragepoolid || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.fencing.policy')">{{ protection.fencingpolicy || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.peer.host.id')">{{ protection.peerhostid || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.secondary.vm.name')">{{ protection.secondaryvmname || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.secondary.target.dir')">{{ protection.secondarytargetdir || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.remote.nbd.export.address')">{{ protection.remotenbdexportaddr || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.xcolo.proxy.endpoint')">{{ protection.xcoloproxyendpoint || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.xcolo.nbd.endpoint')">{{ protection.xcolonbdendpoint || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.xcolo.migrate.uri')">{{ protection.xcolomigrateuri || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.protection.state')">
              <a-tag v-if="protection.protectionstate" :color="stateTagColor(protection.protectionstate)">{{ protection.protectionstate }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.transport.state')">
              <a-tag v-if="protection.transportstate" :color="stateTagColor(protection.transportstate)">{{ protection.transportstate }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.active.side')">
              <a-tag v-if="protection.activeside" :color="sideTagColor(protection.activeside)">{{ protection.activeside }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.admin.state')">
              <a-tag v-if="protection.adminstate" :color="stateTagColor(protection.adminstate)">{{ protection.adminstate }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.fencing.state')">
              <a-tag v-if="protection.fencingstate" :color="stateTagColor(protection.fencingstate)">{{ protection.fencingstate }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.last.error')">{{ protection.lasterror || '-' }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.ftctl.check')">
          <a-descriptions bordered :column="descriptionColumn" size="small">
            <a-descriptions-item :label="$t('label.ftctl.check.result')">
              <a-tag v-if="checkResult.result" :color="stateTagColor(checkResult.result)">{{ checkResult.result }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.inventory.result')">
              <a-tag v-if="checkResult.inventoryresult" :color="stateTagColor(checkResult.inventoryresult)">{{ checkResult.inventoryresult }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.primary.rc')">{{ formatNumber(checkResult.primaryrc) }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.peer.rc')">{{ formatNumber(checkResult.peerrc) }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.ftctl.health')">
          <a-descriptions bordered :column="descriptionColumn" size="small">
            <a-descriptions-item :label="$t('label.ftctl.health.result')">
              <a-tag v-if="healthResult.result" :color="stateTagColor(healthResult.result)">{{ healthResult.result }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.host.id')">{{ formatNumber(healthResult.hostid) }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.uri')">{{ healthResult.uri || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.rc')">{{ formatNumber(healthResult.rc) }}</a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.events')">
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
      </template>

      <a-modal
        :visible="showProtectionModal"
        :title="$t('label.ftctl.protection.configure')"
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
      }
    }
  },
  computed: {
    eventColumns () {
      return [
        { title: this.$t('label.ftctl.timestamp'), key: 'timestamp', dataIndex: 'timestamp', width: 220 },
        { title: this.$t('label.ftctl.stage'), key: 'stage', dataIndex: 'stage', width: 120 },
        { title: this.$t('label.event'), key: 'event', dataIndex: 'event', width: 220 },
        { title: this.$t('label.ftctl.result'), key: 'result', dataIndex: 'result', width: 120 },
        { title: this.$t('label.details'), key: 'details', dataIndex: 'details' }
      ]
    },
    descriptionColumn () {
      return this.$store.getters.device === 'mobile' ? 1 : 2
    },
    unsafeVmState () {
      return ['Destroyed', 'Expunging', 'Error'].includes(this.resource?.state) || this.resource?.hostcontrolstate === 'Offline'
    },
    supportedVm () {
      return ['Admin'].includes(this.$store.getters.userInfo?.roletype) &&
        this.resource?.hypervisor === 'KVM' &&
        this.resource?.vmtype !== 'sharedfsvm'
    },
    protectionConfigured () {
      return ['enabled', 'mode', 'backendmode', 'protectionstate', 'transportstate', 'activeside', 'adminstate', 'fencingstate']
        .some(field => this.protection[field] !== undefined && this.protection[field] !== null && this.protection[field] !== '')
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
        return { type: 'error', message: this.$t('message.ftctl.status.failure') }
      }
      if (this.eventStats.warn > 0 || ['degraded', 'transient_loss', 'peer_unreachable', 'rearm_pending', 'rearm_backoff'].includes(transport) || ['required', 'failed', 'manual-required'].includes(fencing)) {
        return { type: 'warning', message: this.$t('message.ftctl.status.warning') }
      }
      if (protection || transport || fencing) {
        return { type: 'info', message: this.$t('message.ftctl.status.stable') }
      }
      return { type: null, message: null }
    },
    actionDefinitions () {
      const adminState = String(this.protection.adminstate || '').toLowerCase()
      const activeSide = String(this.protection.activeside || '').toLowerCase()
      const fencingState = String(this.protection.fencingstate || '').toLowerCase()
      return [
        {
          api: 'pauseFtctlProtection',
          label: this.$t('label.ftctl.pause'),
          icon: 'PauseCircleOutlined',
          disabled: !this.actionAvailable('pauseFtctlProtection') || !this.protectionEnabled || adminState === 'paused'
        },
        {
          api: 'resumeFtctlProtection',
          label: this.$t('label.ftctl.resume'),
          icon: 'PlayCircleOutlined',
          disabled: !this.actionAvailable('resumeFtctlProtection') || !this.protectionEnabled || adminState !== 'paused'
        },
        {
          api: 'failoverFtctlProtection',
          label: this.$t('label.ftctl.failover'),
          icon: 'ThunderboltOutlined',
          danger: true,
          confirm: true,
          confirmMessage: this.$t('message.ftctl.confirm.failover'),
          disabled: !this.actionAvailable('failoverFtctlProtection') || !this.protectionEnabled || activeSide === 'secondary'
        },
        {
          api: 'failbackFtctlProtection',
          label: this.$t('label.ftctl.failback'),
          icon: 'UndoOutlined',
          danger: true,
          confirm: true,
          confirmMessage: this.$t('message.ftctl.confirm.failback'),
          disabled: !this.actionAvailable('failbackFtctlProtection') || !this.protectionEnabled || activeSide !== 'secondary'
        },
        {
          api: 'confirmFtctlFence',
          label: this.$t('label.ftctl.confirm.fence'),
          icon: 'CheckCircleOutlined',
          confirm: true,
          confirmMessage: this.$t('message.ftctl.confirm.fence'),
          disabled: !this.actionAvailable('confirmFtctlFence') || !['required', 'failed', 'manual-required'].includes(fencingState)
        },
        {
          api: 'clearFtctlFence',
          label: this.$t('label.ftctl.clear.fence'),
          icon: 'ClearOutlined',
          confirm: true,
          confirmMessage: this.$t('message.ftctl.clear.fence'),
          disabled: !this.actionAvailable('clearFtctlFence') || !fencingState || ['clear', 'cleared'].includes(fencingState)
        }
      ]
    }
  },
  created () {
    this.fetchAll()
  },
  methods: {
    actionAvailable (apiName) {
      return apiName in this.$store.getters.apis && this.supportedVm && !this.unsafeVmState && this.protectionConfigured
    },
    openProtectionModal () {
      this.showProtectionModal = true
    },
    closeProtectionModal () {
      this.showProtectionModal = false
    },
    handleProtectionSaved () {
      this.emitKeepCurrentTab()
      this.closeProtectionModal()
      this.fetchAll()
      setTimeout(this.emitKeepCurrentTab, 250)
      setTimeout(this.emitKeepCurrentTab, 1000)
    },
    emitKeepCurrentTab () {
      this.$emit('keep-current-tab', 'ftctl')
    },
    formatNumber (value) {
      return value === null || value === undefined || value === '' ? '-' : value
    },
    formatBoolean (value) {
      if (value === true || value === 'true') {
        return this.$t('label.enabled')
      }
      if (value === false || value === 'false') {
        return this.$t('label.disabled')
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
      if (['clear', 'cleared', 'active'].includes(normalized)) {
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
    extractProtectionPayload (response) {
      const payload = response?.getftctlprotectionresponse || response || {}
      const protection = payload.ftctlprotection || payload
      return Array.isArray(protection) ? (protection[0] || {}) : (protection || {})
    },
    async fetchAll () {
      this.loadingState = true
      this.errorMessage = null
      try {
        await this.fetchProtection()
        if (this.protectionConfigured) {
          await Promise.all([
            this.fetchCheck(),
            this.fetchHealth(),
            this.fetchEvents()
          ])
        } else {
          this.checkResult = {}
          this.healthResult = {}
          this.events = []
        }
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
        this.protection = Object.assign({}, this.extractProtectionPayload(response))
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
        this.$message.success(`${this.actionLabel(commandName)} ${this.$t('label.succeeded')}`)
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
    actionLabel (commandName) {
      return this.actionDefinitions.find(action => action.api === commandName)?.label || commandName
    },
    buildActionMessage (commandName, payload) {
      let message = `${this.actionLabel(commandName)} ${this.$t('label.completed')}`
      if (payload.result) {
        message += ` (${payload.result})`
      }
      const stateParts = []
      if (payload.protectionstate) {
        stateParts.push(`${this.$t('label.ftctl.protection.state')}=${payload.protectionstate}`)
      }
      if (payload.transportstate) {
        stateParts.push(`${this.$t('label.ftctl.transport.state')}=${payload.transportstate}`)
      }
      if (payload.activeside) {
        stateParts.push(`${this.$t('label.ftctl.active.side')}=${payload.activeside}`)
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

  &__operations {
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(127, 127, 127, 0.18);
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
    margin-top: 14px;
    padding: 14px 16px;
    border: 1px solid rgba(127, 127, 127, 0.18);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.035);
  }

  &__summary-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__summary-label {
    font-size: 12px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.62);
  }

  &__empty-state {
    display: flex;
    align-items: center;
    gap: 16px;
    min-height: 112px;
    padding: 22px 24px;
    border: 1px dashed rgba(127, 127, 127, 0.35);
    border-radius: 6px;
    background: rgba(127, 127, 127, 0.04);
  }

  &__empty-icon {
    font-size: 34px;
    color: #1890ff;
  }

  &__empty-title {
    font-size: 15px;
    font-weight: 600;
  }

  &__empty-description {
    margin-top: 4px;
    opacity: 0.75;
  }

  &__details {
    margin: 0;
    padding: 8px;
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 12px;
    background: transparent;
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-label),
  :deep(.ant-table-thead > tr > th) {
    background: rgba(127, 127, 127, 0.06);
  }

  :deep(.ant-descriptions-view table) {
    width: 100%;
    table-layout: fixed;
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-label) {
    width: 28%;
    min-width: 180px;
    font-weight: 600;
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-content) {
    width: 22%;
    word-break: break-word;
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-content),
  :deep(.ant-table-tbody > tr > td) {
    background: transparent;
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-view),
  :deep(.ant-descriptions-bordered .ant-descriptions-row),
  :deep(.ant-descriptions-bordered .ant-descriptions-item-label),
  :deep(.ant-descriptions-bordered .ant-descriptions-item-content),
  :deep(.ant-table-thead > tr > th),
  :deep(.ant-table-tbody > tr > td) {
    border-color: rgba(127, 127, 127, 0.22);
  }
}

:global(.dark-mode) .ftctl-tab {
  color: rgba(255, 255, 255, 0.82);

  :deep(.ant-card) {
    color: rgba(255, 255, 255, 0.82);
  }

  :deep(.ant-card-head) {
    color: rgba(255, 255, 255, 0.88);
    border-color: rgba(255, 255, 255, 0.12);
  }

  :deep(.ant-card-head-title) {
    color: rgba(255, 255, 255, 0.88);
  }

  :deep(.ant-alert-info) {
    background: rgba(24, 144, 255, 0.12);
    border-color: rgba(64, 169, 255, 0.32);
  }

  :deep(.ant-alert-message),
  :deep(.ant-alert-description) {
    color: rgba(255, 255, 255, 0.84);
  }

  :deep(.ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous):not([disabled])) {
    color: rgba(255, 255, 255, 0.82);
    border-color: rgba(255, 255, 255, 0.28);
    background: rgba(255, 255, 255, 0.055);
  }

  :deep(.ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous):not([disabled]):hover) {
    color: #69c0ff;
    border-color: #69c0ff;
    background: rgba(24, 144, 255, 0.12);
  }

  :deep(.ant-btn-dangerous:not([disabled])) {
    color: #ff7875;
    border-color: #ff7875;
    background: rgba(255, 77, 79, 0.12);
  }

  :deep(.ant-btn[disabled]) {
    color: rgba(255, 255, 255, 0.42);
    border-color: rgba(255, 255, 255, 0.16);
    background: rgba(255, 255, 255, 0.045);
  }

  .ftctl-tab__operations {
    border-bottom-color: rgba(255, 255, 255, 0.12);
  }

  .ftctl-tab__summary {
    border-color: rgba(64, 169, 255, 0.18);
    background: linear-gradient(180deg, rgba(64, 169, 255, 0.075), rgba(255, 255, 255, 0.035));
  }

  .ftctl-tab__summary-label {
    color: rgba(255, 255, 255, 0.68);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-label),
  :deep(.ant-table-thead > tr > th) {
    color: rgba(255, 255, 255, 0.86);
    background: rgba(255, 255, 255, 0.065);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-item-content),
  :deep(.ant-table-tbody > tr > td) {
    color: rgba(255, 255, 255, 0.78);
    background: rgba(255, 255, 255, 0.02);
  }

  :deep(.ant-descriptions-bordered .ant-descriptions-view),
  :deep(.ant-descriptions-bordered .ant-descriptions-row),
  :deep(.ant-descriptions-bordered .ant-descriptions-item-label),
  :deep(.ant-descriptions-bordered .ant-descriptions-item-content),
  :deep(.ant-table-thead > tr > th),
  :deep(.ant-table-tbody > tr > td) {
    border-color: rgba(255, 255, 255, 0.12);
  }

  :deep(.ant-empty-description) {
    color: rgba(255, 255, 255, 0.62);
  }
}
</style>
