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
  <a-spin :spinning="blockingLoadingState">
    <div class="ftctl-tab">
      <a-card size="small" :bordered="true" class="ftctl-tab__section">
        <template #title>{{ $t('label.ftctl.fault.protection') }}</template>
        <template #extra>
          <a-space wrap>
            <a-button
              v-if="canConfigureProtection && !protectionConfigured"
              type="primary"
              @click="openProtectionModal"
              :disabled="unsafeVmState || loadingState">
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

        <a-alert
          v-if="standbyProtectionView"
          type="info"
          show-icon
          class="ftctl-tab__alert">
          <template #message>
            <div>{{ $t('message.ftctl.standby.view') }}</div>
            <div class="ftctl-tab__meta">
              {{ $t('message.ftctl.standby.view.desc') }}
              <router-link
                v-if="protection.primaryvirtualmachineid"
                :to="{ path: '/vm/' + primaryVmRouteId }">
                {{ primaryVmDisplay }}
              </router-link>
            </div>
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

          <div v-if="syncProgressVisible" class="ftctl-tab__progress">
            <div class="ftctl-tab__progress-head">
              <div>
                <div class="ftctl-tab__summary-label">Block Copy Progress</div>
                <div class="ftctl-tab__progress-meta">
                  {{ syncProgressDirection }} | {{ formatBytes(syncCopiedBytes) }} / {{ formatBytes(syncTotalBytes) }}
                  <span v-if="syncProgressUpdated"> | {{ syncProgressUpdated }}</span>
                  <span v-if="refreshingProgress" class="ftctl-tab__progress-refreshing">
                    <SyncOutlined spin />
                  </span>
                </div>
                <div v-if="thinStatusDetails.length" class="ftctl-tab__progress-meta">
                  <span v-for="detail in thinStatusDetails" :key="detail.key">
                    {{ detail.label }}: {{ detail.value }}
                  </span>
                </div>
              </div>
              <a-tag :color="syncReady ? 'green' : 'blue'">{{ syncReady ? 'ready' : 'copying' }}</a-tag>
            </div>
            <a-progress :percent="syncProgressPercent" :status="syncProgressStatus" />
            <div v-if="syncProgressDisks.length" class="ftctl-tab__progress-disks">
              <div v-for="disk in syncProgressDisks" :key="disk.device || disk.target" class="ftctl-tab__progress-disk">
                <div class="ftctl-tab__progress-disk-label">
                  <span>{{ disk.target || disk.device }}</span>
                  <span>{{ formatPercent(disk.percent) }}%</span>
                </div>
                <a-progress :percent="normalizePercent(disk.percent)" :show-info="false" size="small" />
                <div v-if="diskRuntimeDetails(disk).length" class="ftctl-tab__progress-disk-runtime">
                  <div v-for="detail in diskRuntimeDetails(disk)" :key="detail.key" class="ftctl-tab__progress-disk-runtime-row">
                    <span class="ftctl-tab__progress-disk-runtime-key">{{ detail.label }}</span>
                    <span class="ftctl-tab__progress-disk-runtime-value">{{ detail.value }}</span>
                  </div>
                </div>
              </div>
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
            <a-descriptions-item :label="$t('label.ftctl.protection.role')">
              <a-tag v-if="protection.protectionrole" :color="protectionRoleColor">{{ protectionRoleLabel }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.primary.vm')">
              <router-link
                v-if="protection.primaryvirtualmachineid"
                :to="{ path: '/vm/' + primaryVmRouteId }">
                {{ primaryVmDisplay }}
              </router-link>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.enabled')">
              <a-tag :color="booleanTagColor(protection.enabled)">{{ formatBoolean(protection.enabled) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.mode')">
              <a-tag v-if="protection.mode" color="blue">{{ protection.mode }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.backend.mode')">{{ protection.backendmode || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.provisioning.backend')">{{ protection.provisioningbackend || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.provisioning.state')">
              <a-tag v-if="protection.provisioningstate" :color="stateTagColor(protection.provisioningstate)">{{ protection.provisioningstate }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.target.storage.scope')">{{ protection.targetstoragescope || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.target.storage.pool')">{{ protection.targetstoragepoolname || protection.targetstoragepoolid || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.fencing.policy')">{{ protection.fencingpolicy || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.peer.host')">{{ peerHostDisplay }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.secondary.vm.name')">
              <router-link
                v-if="secondaryVmRouteId"
                :to="{ path: '/vm/' + secondaryVmRouteId }">
                {{ secondaryVmDisplay }}
              </router-link>
              <span v-else>{{ secondaryVmDisplay }}</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.secondary.target.disk')">
              <div v-if="secondaryVolumeItems.length" class="ftctl-tab__link-list">
                <div v-for="volume in secondaryVolumeItems" :key="volume.id || volume.name || volume.path" class="ftctl-tab__link-list-item">
                  <router-link
                    v-if="volume.id"
                    :to="{ path: '/volume/' + volume.id }">
                    {{ volume.name }}
                  </router-link>
                  <span v-else>{{ volume.name }}</span>
                </div>
              </div>
              <span v-else>{{ secondaryTargetDiskDisplay }}</span>
            </a-descriptions-item>
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
            <a-descriptions-item label="Block Copy Progress">
              <span v-if="syncProgressVisible">{{ syncProgressPercent }}% ({{ formatBytes(syncCopiedBytes) }} / {{ formatBytes(syncTotalBytes) }})</span>
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
              <a-tag v-if="checkResult.result" :color="stateTagColor(checkResult.result)">{{ formatStatusValue(checkResult.result) }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.inventory.result')">
              <a-tag v-if="checkResult.inventoryresult" :color="stateTagColor(checkResult.inventoryresult)">{{ formatStatusValue(checkResult.inventoryresult) }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.primary.rc')">
              <a-tag v-if="checkResult.primaryrc !== undefined && checkResult.primaryrc !== null && checkResult.primaryrc !== ''" :color="executionStateTagColor(primaryExecutionState)">{{ primaryExecutionState }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.peer.rc')">
              <a-tag v-if="checkResult.peerrc !== undefined && checkResult.peerrc !== null && checkResult.peerrc !== ''" :color="executionStateTagColor(peerExecutionState)">{{ peerExecutionState }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.ftctl.health')">
          <a-descriptions bordered :column="descriptionColumn" size="small">
            <a-descriptions-item :label="$t('label.ftctl.health.result')">
              <a-tag v-if="healthResult.result" :color="stateTagColor(healthResult.result)">{{ formatStatusValue(healthResult.result) }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.host.id')">{{ healthHostDisplay }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.uri')">{{ healthResult.uri || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('label.ftctl.rc')">
              <a-tag v-if="healthResult.rc !== undefined && healthResult.rc !== null && healthResult.rc !== ''" :color="executionStateTagColor(healthExecutionState)">{{ healthExecutionState }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <a-card size="small" :bordered="true" class="ftctl-tab__section" :title="$t('label.events')">
          <a-table
            size="small"
            :columns="eventColumns"
            :dataSource="events"
            :pagination="false"
            :rowKey="record => `${record.timestamp || record.ts || 'na'}-${record.event || 'na'}-${record.stage || 'na'}`"
            :expandable="{ rowExpandable: (record) => hasEventDetails(record) }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'result'">
                <a-tag v-if="record.result" :color="stateTagColor(record.result)">{{ record.result }}</a-tag>
                <span v-else>-</span>
              </template>
              <template v-else-if="column.key === 'timestamp'">
                <span>{{ record.timestamp || record.ts || '-' }}</span>
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
      initialLoadComplete: false,
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
      syncRefreshTimer: null,
      refreshingProgress: false,
      runtimeRefreshing: false,
      syncRefreshCount: 0,
      actionLoading: {
        pauseFtctlProtection: false,
        resumeFtctlProtection: false,
        failoverFtctlProtection: false,
        failbackFtctlProtection: false,
        confirmFtctlFence: false,
        clearFtctlFence: false,
        releaseFtctlProtection: false
      }
    }
  },
  computed: {
    blockingLoadingState () {
      return this.loadingState && !this.initialLoadComplete
    },
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
    standbyProtectionView () {
      return String(this.protection.protectionrole || '').toLowerCase() === 'standby'
    },
    primaryVmDisplay () {
      return this.protection.primaryvirtualmachinename || this.protection.primaryvirtualmachineid || '-'
    },
    primaryVmRouteId () {
      return this.protection.primaryvirtualmachineuuid || this.protection.primaryvirtualmachineid
    },
    secondaryVmDisplay () {
      return this.protection.secondaryvirtualmachinedisplayname || this.protection.secondaryvmname || '-'
    },
    secondaryVmRouteId () {
      return this.protection.secondaryvirtualmachineuuid || this.protection.secondaryvirtualmachineid
    },
    protectionRoleLabel () {
      const role = String(this.protection.protectionrole || '').toLowerCase()
      if (role === 'standby') {
        return this.$t('label.ftctl.standby.vm')
      }
      if (role === 'primary') {
        return this.$t('label.ftctl.primary.vm')
      }
      return this.protection.protectionrole || '-'
    },
    protectionRoleColor () {
      return this.standbyProtectionView ? 'purple' : 'blue'
    },
    canConfigureProtection () {
      return 'registerFtctlProtection' in this.$store.getters.apis && this.supportedVm && !this.standbyProtectionView
    },
    canRunActions () {
      return ['pauseFtctlProtection', 'resumeFtctlProtection', 'failoverFtctlProtection', 'failbackFtctlProtection', 'confirmFtctlFence', 'clearFtctlFence', 'releaseFtctlProtection']
        .some(api => api in this.$store.getters.apis) && this.supportedVm && !this.standbyProtectionView
    },
    canLoadEvents () {
      return 'getFtctlEvents' in this.$store.getters.apis
    },
    eventStats () {
      const stats = { total: this.events.length, warn: 0, fail: 0 }
      this.events.forEach(event => {
        if (this.isExpectedFailoverSteadyEvent(event)) {
          return
        }
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

      if (this.eventStats.fail > 0 || ['error', 'rearm_exhausted'].includes(protection)) {
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
    syncProgressObject () {
      const raw = this.protection.syncprogressjson
      if (!raw) {
        return {}
      }
      if (typeof raw === 'object') {
        return raw
      }
      try {
        return JSON.parse(raw)
      } catch (e) {
        return {}
      }
    },
    syncProgressVisible () {
      return this.protection.syncprogresspercent !== undefined ||
        this.syncProgressObject.percent !== undefined
    },
    syncProgressPercent () {
      return this.normalizePercent(this.protection.syncprogresspercent ?? this.syncProgressObject.percent)
    },
    syncCopiedBytes () {
      return this.protection.synccopiedbytes ?? this.syncProgressObject.copied_bytes
    },
    syncTotalBytes () {
      return this.protection.synctotalbytes ?? this.syncProgressObject.total_bytes
    },
    syncReady () {
      const ready = this.protection.syncready ?? this.syncProgressObject.ready
      return ready === true || ready === 'true'
    },
    syncProgressDirection () {
      return this.protection.syncdirection || this.syncProgressObject.direction || 'forward'
    },
    syncProgressUpdated () {
      return this.protection.syncupdated || this.syncProgressObject.updated || ''
    },
    syncProgressStatus () {
      const transport = String(this.protection.transportstate || '').toLowerCase()
      if (['failed', 'lost'].includes(transport) || String(this.protection.protectionstate || '').toLowerCase() === 'error') {
        return 'exception'
      }
      return this.syncReady || this.syncProgressPercent >= 100 ? 'success' : 'active'
    },
    syncProgressDisks () {
      return Array.isArray(this.syncProgressObject.disks) ? this.syncProgressObject.disks : []
    },
    thinStatusDetails () {
      const details = []
      if (this.syncProgressObject.thin_preserve) {
        details.push({
          key: 'thinPreserve',
          label: 'Thin Preserve',
          value: this.syncProgressObject.thin_preserve
        })
      }
      if (this.syncProgressObject.rbd_parent_flattened) {
        details.push({
          key: 'rbdParentFlattened',
          label: 'RBD Parent Flattened',
          value: this.syncProgressObject.rbd_parent_flattened
        })
      }
      if (this.syncProgressObject.last_thin_preserve_reason) {
        details.push({
          key: 'thinPreserveReason',
          label: 'Reason',
          value: this.syncProgressObject.last_thin_preserve_reason
        })
      }
      return details
    },
    primaryExecutionState () {
      return this.executionStateFromReturnCode(this.checkResult.primaryrc, 'primary')
    },
    peerExecutionState () {
      return this.executionStateFromReturnCode(this.checkResult.peerrc, 'peer')
    },
    healthExecutionState () {
      return this.executionStateFromReturnCode(this.healthResult.rc, 'health')
    },
    peerHostDisplay () {
      return this.protection.peerhostname || this.protection.peerhostid || '-'
    },
    secondaryVolumeItems () {
      return this.normalizeList(this.protection.secondaryvolumes).map(volume => {
        return {
          id: volume.id,
          name: volume.name || volume.path || '-',
          path: volume.path,
          disklabel: volume.disklabel
        }
      }).filter(volume => volume.name && volume.name !== '-')
    },
    secondaryTargetDiskDisplay () {
      const target = this.protection.secondarytargetdisk || this.protection.secondarytargetdiskpath || this.protection.diskmap
      if (target) {
        return target
      }
      if (this.protection.secondarytargetdir) {
        return this.protection.secondaryvmname
          ? `${String(this.protection.secondarytargetdir).replace(/\/+$/, '')}/${this.protection.secondaryvmname}`
          : this.protection.secondarytargetdir
      }
      if (this.protection.targetstoragepoolname && this.protection.secondaryvmname) {
        return `${this.protection.targetstoragepoolname} / ${this.protection.secondaryvmname}`
      }
      return this.protection.targetstoragepoolname || this.protection.targetstoragepoolid || '-'
    },
    healthHostDisplay () {
      return this.healthResult.hostname || '-'
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
        },
        {
          api: 'releaseFtctlProtection',
          label: this.$t('label.ftctl.release.protection'),
          icon: 'DeleteOutlined',
          danger: true,
          confirm: true,
          confirmMessage: this.$t('message.ftctl.confirm.release'),
          disabled: !this.actionAvailable('releaseFtctlProtection') || !this.protectionEnabled || activeSide === 'secondary'
        }
      ]
    }
  },
  created () {
    this.fetchAll()
  },
  beforeUnmount () {
    this.stopSyncAutoRefresh()
  },
  methods: {
    actionAvailable (apiName) {
      return apiName in this.$store.getters.apis && this.supportedVm && !this.standbyProtectionView && !this.unsafeVmState && this.protectionConfigured
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
    normalizePercent (value) {
      const percent = Number(value)
      if (Number.isNaN(percent)) {
        return 0
      }
      return Math.max(0, Math.min(100, Number(percent.toFixed(1))))
    },
    formatPercent (value) {
      return this.normalizePercent(value).toFixed(1)
    },
    formatBytes (value) {
      const bytes = Number(value)
      if (Number.isNaN(bytes) || bytes < 0) {
        return '-'
      }
      const gib = bytes / 1024 / 1024 / 1024
      return `${gib.toFixed(1)} GiB`
    },
    formatNbdEndpoint (disk) {
      if (disk.nbd_endpoint) {
        return disk.nbd_endpoint
      }
      if (disk.nbd_host && disk.nbd_port) {
        return disk.nbd_export_name
          ? `${disk.nbd_host}:${disk.nbd_port}/${disk.nbd_export_name}`
          : `${disk.nbd_host}:${disk.nbd_port}`
      }
      if (disk.nbd_uri) {
        return String(disk.nbd_uri).replace(/^nbd:\/\//, '')
      }
      return ''
    },
    diskRuntimeDetails (disk) {
      const details = []
      const endpoint = this.formatNbdEndpoint(disk)
      if (endpoint) {
        details.push({
          key: 'nbd',
          label: this.$t('label.ftctl.nbd.endpoint'),
          value: endpoint
        })
      }
      if (disk.secondary_path) {
        details.push({
          key: 'secondaryPath',
          label: this.$t('label.ftctl.secondary.path'),
          value: disk.secondary_path
        })
      }
      const status = [disk.status, disk.io_status].filter(Boolean).join(' / ')
      if (status) {
        details.push({
          key: 'status',
          label: this.$t('label.ftctl.blockjob.status'),
          value: status
        })
      }
      return details
    },
    normalizeList (value) {
      if (!value) {
        return []
      }
      if (Array.isArray(value)) {
        return value
      }
      if (Array.isArray(value.ftctlprotectionvolume)) {
        return value.ftctlprotectionvolume
      }
      return [value]
    },
    formatStatusValue (value) {
      const normalized = String(value || '').toLowerCase()
      if (normalized === 'ok' || normalized === 'healthy') {
        return 'OK'
      }
      if (normalized === 'warn' || normalized === 'warning') {
        return 'WARN'
      }
      if (['fail', 'failed', 'error', 'err', 'timeout'].includes(normalized)) {
        return 'ERR'
      }
      return value || '-'
    },
    executionStateFromReturnCode (value, side) {
      const rc = Number(value)
      if (Number.isNaN(rc)) {
        return '-'
      }
      if (rc === 0) {
        return 'Started'
      }
      if (side === 'peer' && rc === 1 && this.expectedCloudManagedPeerStopped()) {
        return 'Stopped'
      }
      if (side === 'primary' && rc === 1 && this.expectedCloudManagedPrimaryStopped()) {
        return 'Stopped'
      }
      return 'Error'
    },
    isCloudManagedProvisioningBackend () {
      return String(this.checkResult.provisioningbackend || this.protection.provisioningbackend || '').toLowerCase() === 'cloud-managed'
    },
    isCloudManagedFailedOver () {
      const protection = String(this.protection.protectionstate || '').toLowerCase()
      const transport = String(this.protection.transportstate || '').toLowerCase()
      const activeSide = String(this.protection.activeside || '').toLowerCase()
      const fencing = String(this.protection.fencingstate || '').toLowerCase()
      return this.isCloudManagedProvisioningBackend() &&
        protection === 'failed_over' &&
        transport === 'failed_over' &&
        activeSide === 'secondary' &&
        ['manual-fenced', 'fenced', 'source-fenced'].includes(fencing)
    },
    expectedCloudManagedPeerStopped () {
      return this.isCloudManagedProvisioningBackend() &&
        String(this.checkResult.standbydomainstate || '').toLowerCase() === 'not-defined-expected'
    },
    expectedCloudManagedPrimaryStopped () {
      return this.isCloudManagedFailedOver() &&
        Number(this.checkResult.peerrc) === 0 &&
        String(this.checkResult.standbydomainstate || '').toLowerCase() === 'running'
    },
    isExpectedFailoverSteadyEvent (event) {
      if (!this.isCloudManagedFailedOver()) {
        return false
      }
      const result = String(event?.result || '').toLowerCase()
      if (!['fail', 'error'].includes(result)) {
        return false
      }
      return String(event?.stage || '').toLowerCase() === 'inventory' &&
        String(event?.event || '').toLowerCase() === 'inventory.disks'
    },
    executionStateTagColor (status) {
      if (status === 'Started') {
        return 'green'
      }
      if (status === 'Stopped') {
        return 'blue'
      }
      if (status === 'Error') {
        return 'red'
      }
      return 'default'
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
      if (normalized === 'failed_over') {
        return 'blue'
      }
      if (['fail', 'error', 'rearm_exhausted', 'timeout', 'locked'].includes(normalized)) {
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
    extractActionResponsePayload (response, commandName) {
      const responseName = `${commandName.toLowerCase()}response`
      return response?.[responseName] || response || {}
    },
    extractJobPayload (result, commandName) {
      const responseName = `${commandName.toLowerCase()}response`
      const jobResult = result?.jobresult || {}
      return jobResult?.[responseName] || jobResult || {}
    },
    extractJobId (payload) {
      return payload?.jobid || payload?.jobId || null
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
    extractNestedPayload (response, responseKey, objectKey) {
      const payload = response?.[responseKey] || response || {}
      const value = payload?.[objectKey] || payload
      return Array.isArray(value) ? (value[0] || {}) : (value || {})
    },
    extractProtectionPayload (response) {
      return this.extractNestedPayload(response, 'getftctlprotectionresponse', 'ftctlprotection')
    },
    async fetchAll (options = {}) {
      const silent = options?.silent === true
      const refreshRuntime = options?.refreshRuntime === true
      if (!silent && !this.initialLoadComplete) {
        this.loadingState = true
      }
      this.errorMessage = null
      try {
        await this.fetchProtection({ silent, refreshRuntime })
        if (this.protectionConfigured) {
          this.fetchRuntimeData({ silent: true }).catch(error => {
            if (!silent) {
              this.errorMessage = this.extractErrorMessage(error, 'fetchRuntimeData')
            }
          })
        } else {
          this.checkResult = {}
          this.healthResult = {}
          this.events = []
        }
      } finally {
        this.initialLoadComplete = true
        if (!silent) {
          this.loadingState = false
        }
        this.updateSyncAutoRefresh()
      }
    },
    async fetchRuntimeData (options = {}) {
      if (this.runtimeRefreshing) {
        return
      }
      this.runtimeRefreshing = true
      try {
        await Promise.all([
          this.fetchCheck(options),
          this.fetchHealth(options),
          this.fetchEvents(options)
        ])
      } finally {
        this.runtimeRefreshing = false
      }
    },
    async fetchProtection (options = {}) {
      if (!this.resource?.id) {
        return
      }
      try {
        const params = { virtualmachineid: this.resource.id }
        if (options?.refreshRuntime === true) {
          params.refreshruntime = true
        }
        const response = await getAPI('getFtctlProtection', params)
        this.protection = Object.assign({}, this.extractProtectionPayload(response))
      } catch (error) {
        if (!options.silent) {
          this.protection = {}
          this.errorMessage = this.extractErrorMessage(error, 'getFtctlProtection')
        }
      }
    },
    async fetchCheck (options = {}) {
      if (!this.resource?.id || !('getFtctlCheck' in this.$store.getters.apis)) {
        return
      }
      try {
        const response = await getAPI('getFtctlCheck', { virtualmachineid: this.resource.id })
        this.checkResult = this.extractNestedPayload(response, 'getftctlcheckresponse', 'ftctlcheck')
      } catch (error) {
        if (!options.silent) {
          this.checkResult = {}
          this.errorMessage = this.extractErrorMessage(error, 'getFtctlCheck')
        }
      }
    },
    async fetchHealth (options = {}) {
      if (!this.resource?.id || !('getFtctlHealth' in this.$store.getters.apis)) {
        return
      }
      try {
        const response = await getAPI('getFtctlHealth', { virtualmachineid: this.resource.id })
        this.healthResult = this.extractNestedPayload(response, 'getftctlhealthresponse', 'ftctlhealth')
      } catch (error) {
        if (!options.silent) {
          this.healthResult = {}
          this.errorMessage = this.extractErrorMessage(error, 'getFtctlHealth')
        }
      }
    },
    async fetchEvents (options = {}) {
      if (!this.resource?.id || !this.canLoadEvents) {
        return
      }
      try {
        const response = await getAPI('getFtctlEvents', { virtualmachineid: this.resource.id, limit: 10 })
        const payload = this.extractNestedPayload(response, 'getftctleventsresponse', 'ftctlevents')
        this.events = (payload.events || []).map(event => {
          return Object.assign({}, event, {
            timestamp: event.timestamp || event.ts
          })
        }).sort((a, b) => {
          return String(b.timestamp || '').localeCompare(String(a.timestamp || ''))
        })
      } catch (error) {
        if (!options.silent) {
          this.events = []
          this.errorMessage = this.extractErrorMessage(error, 'getFtctlEvents')
        }
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
        const payload = this.extractActionResponsePayload(response, commandName)
        const jobId = this.extractJobId(payload)
        if (jobId) {
          this.startActionJobPolling(commandName, jobId)
          this.lastAction = {
            success: true,
            message: `${this.actionLabel(commandName)} ${this.$t('label.started')} (${jobId})`,
            timestamp: new Date().toLocaleString()
          }
          await this.fetchAll({ silent: true })
          return
        }
        this.applyActionPayload(payload)
        this.$message.success(`${this.actionLabel(commandName)} ${this.$t('label.succeeded')}`)
        this.lastAction = {
          success: true,
          message: this.buildActionMessage(commandName, payload),
          timestamp: new Date().toLocaleString()
        }
        if (this.shouldRefreshParentVm(commandName)) {
          eventBus.emit('vm-refresh-data')
        }
        await this.fetchAll({ silent: true })
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
    startActionJobPolling (commandName, jobId) {
      const actionLabel = this.actionLabel(commandName)
      this.$pollJob({
        jobId,
        title: actionLabel,
        description: this.resource?.name || this.resource?.displayname || this.resource?.id,
        loadingMessage: `${actionLabel} ${this.$t('label.in.progress')}`,
        successMessage: `${actionLabel} ${this.$t('label.succeeded')}`,
        errorMessage: `${actionLabel} ${this.$t('label.failed')}`,
        resourceId: this.resource?.id,
        successMethod: async (result) => {
          const payload = this.extractJobPayload(result, commandName)
          this.applyActionPayload(payload)
          this.lastAction = {
            success: true,
            message: this.buildActionMessage(commandName, payload),
            timestamp: new Date().toLocaleString()
          }
          if (this.shouldRefreshParentVm(commandName)) {
            eventBus.emit('vm-refresh-data')
          }
          await this.fetchAll({ silent: true })
        },
        errorMethod: async (result) => {
          this.lastAction = {
            success: false,
            message: result?.jobresult?.errortext || `${actionLabel} ${this.$t('label.failed')}`,
            timestamp: new Date().toLocaleString()
          }
          await this.fetchAll({ silent: true })
        },
        catchMethod: async () => {
          await this.fetchAll({ silent: true })
        }
      })
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
      if (payload.syncprogresspercent !== undefined) this.protection.syncprogresspercent = payload.syncprogresspercent
      if (payload.synccopiedbytes !== undefined) this.protection.synccopiedbytes = payload.synccopiedbytes
      if (payload.synctotalbytes !== undefined) this.protection.synctotalbytes = payload.synctotalbytes
      if (payload.syncready !== undefined) this.protection.syncready = payload.syncready
      if (payload.syncdirection !== undefined) this.protection.syncdirection = payload.syncdirection
      if (payload.syncupdated !== undefined) this.protection.syncupdated = payload.syncupdated
      if (payload.syncprogressjson !== undefined) this.protection.syncprogressjson = payload.syncprogressjson
    },
    shouldRefreshParentVm (commandName) {
      return ['failoverFtctlProtection', 'failbackFtctlProtection', 'confirmFtctlFence', 'releaseFtctlProtection'].includes(commandName)
    },
    updateSyncAutoRefresh () {
      const transport = String(this.protection.transportstate || '').toLowerCase()
      if (['copying', 'reverse_syncing'].includes(transport)) {
        this.startSyncAutoRefresh()
      } else {
        this.stopSyncAutoRefresh()
      }
    },
    startSyncAutoRefresh () {
      if (this.syncRefreshTimer) {
        return
      }
      this.syncRefreshTimer = setInterval(() => {
        if (!this.loadingState && !this.refreshingProgress && this.resource?.id) {
          this.fetchSyncProgress()
        }
      }, 10000)
    },
    async fetchSyncProgress () {
      if (!this.resource?.id || this.refreshingProgress) {
        return
      }
      this.refreshingProgress = true
      try {
        await this.fetchProtection({ silent: true })
        this.syncRefreshCount += 1
      } finally {
        this.refreshingProgress = false
        this.updateSyncAutoRefresh()
      }
    },
    stopSyncAutoRefresh () {
      if (!this.syncRefreshTimer) {
        return
      }
      clearInterval(this.syncRefreshTimer)
      this.syncRefreshTimer = null
    }
  },
  watch: {
    'resource.id': {
      handler (value, oldValue) {
        if (value && value !== oldValue) {
          this.initialLoadComplete = false
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

  &__progress {
    margin-top: 12px;
    padding: 14px 16px;
    border: 1px solid rgba(127, 127, 127, 0.18);
    border-radius: 6px;
    background: rgba(24, 144, 255, 0.045);
  }

  &__progress-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 8px;
  }

  &__progress-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    margin-top: 3px;
    font-size: 12px;
    color: rgba(0, 0, 0, 0.62);
  }

  &__progress-refreshing {
    display: inline-flex;
    align-items: center;
    margin-left: 6px;
    color: #1890ff;
  }

  &__progress-disks {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 10px 14px;
    margin-top: 10px;
  }

  &__progress-disk-label {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 2px;
    font-size: 12px;
  }

  &__progress-disk-runtime {
    margin-top: 6px;
    display: flex;
    flex-direction: column;
    gap: 3px;
    font-size: 12px;
  }

  &__progress-disk-runtime-row {
    display: grid;
    grid-template-columns: minmax(82px, 0.32fr) minmax(0, 1fr);
    gap: 8px;
    align-items: baseline;
  }

  &__progress-disk-runtime-key {
    font-weight: 600;
    color: rgba(0, 0, 0, 0.58);
  }

  &__progress-disk-runtime-value {
    min-width: 0;
    overflow-wrap: anywhere;
    font-family: monospace;
    color: rgba(0, 0, 0, 0.72);
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

  &__link-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__link-list-item {
    line-height: 1.45;
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
</style>

<style lang="scss">
body.dark-mode .ftctl-tab {
  color: rgba(255, 255, 255, 0.82);

  .ant-card {
    color: rgba(255, 255, 255, 0.82);
  }

  .ant-card-head {
    color: rgba(255, 255, 255, 0.88);
    border-color: rgba(255, 255, 255, 0.12);
  }

  .ant-card-head-title {
    color: rgba(255, 255, 255, 0.88);
  }

  .ant-alert-info {
    background: rgba(24, 144, 255, 0.12);
    border-color: rgba(64, 169, 255, 0.32);
  }

  .ant-alert-message,
  .ant-alert-description {
    color: rgba(255, 255, 255, 0.84);
  }

  .ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous):not([disabled]) {
    color: rgba(255, 255, 255, 0.82);
    border-color: rgba(255, 255, 255, 0.28);
    background: rgba(255, 255, 255, 0.055);
  }

  .ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous):not([disabled]):hover {
    color: #69c0ff;
    border-color: #69c0ff;
    background: rgba(24, 144, 255, 0.12);
  }

  .ant-btn-dangerous:not([disabled]) {
    color: #ff7875;
    border-color: #ff7875;
    background: rgba(255, 77, 79, 0.12);
  }

  .ant-btn[disabled] {
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

  .ftctl-tab__progress {
    border-color: rgba(64, 169, 255, 0.18);
    background: rgba(24, 144, 255, 0.09);
  }

  .ftctl-tab__progress-meta,
  .ftctl-tab__progress-refreshing,
  .ftctl-tab__progress-disk-label,
  .ftctl-tab__progress-disk-runtime-key {
    color: rgba(255, 255, 255, 0.68);
  }

  .ftctl-tab__progress-disk-runtime-value {
    color: rgba(255, 255, 255, 0.78);
  }

  .ftctl-tab__summary .ant-tag,
  .ant-descriptions-item-content .ant-tag {
    border-color: rgba(255, 255, 255, 0.18);
  }

  .ant-descriptions-bordered .ant-descriptions-item-label,
  .ant-table-thead > tr > th {
    color: rgba(255, 255, 255, 0.86);
    background: rgba(255, 255, 255, 0.065);
  }

  .ant-descriptions-bordered .ant-descriptions-item-content,
  .ant-table-tbody > tr > td {
    color: rgba(255, 255, 255, 0.78);
    background: rgba(255, 255, 255, 0.02);
  }

  .ant-descriptions-bordered .ant-descriptions-view,
  .ant-descriptions-bordered .ant-descriptions-row,
  .ant-descriptions-bordered .ant-descriptions-item-label,
  .ant-descriptions-bordered .ant-descriptions-item-content,
  .ant-table-thead > tr > th,
  .ant-table-tbody > tr > td {
    border-color: rgba(255, 255, 255, 0.12);
  }

  .ant-empty-description {
    color: rgba(255, 255, 255, 0.62);
  }
}
</style>
