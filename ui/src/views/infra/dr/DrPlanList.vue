<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->
<template>
  <div class="cross-dr-page">
    <a-alert
      v-if="!hasListApi"
      type="warning"
      show-icon
      :message="$t('message.dr.api.unavailable')" />

    <template v-else-if="detailId">
      <a-card class="cross-dr-panel" :loading="loading">
        <template #title>
          <div class="cross-dr-heading">
            <router-link :to="{ path: '/drplan' }">{{ $t('label.dr.plans') }}</router-link>
            <span>/</span>
            <span>{{ detailPlan.name || detailPlan.id || '-' }}</span>
          </div>
        </template>
        <template #extra>
          <a-space wrap>
            <dr-action-toolbar
              v-if="detailPlan.id"
              :plan="detailPlan"
              :currentRun="currentRun"
              :loadingAction="actionLoading"
              @run-action="action => runPlanAction(action, detailPlan)" />
            <a-button size="small" @click="fetchDetail">
              <template #icon><ReloadOutlined /></template>
              {{ $t('label.refresh') }}
            </a-button>
          </a-space>
        </template>

        <a-tabs :activeKey="activeTab" :animated="false" @change="changeTab">
          <a-tab-pane key="overview" :tab="$t('label.overview')">
            <dr-plan-overview
              v-if="detailPlan.id"
              :plan="detailPlan"
              :sourceSite="siteById[detailPlan.sourcesiteid] || {}"
              :targetSite="siteById[detailPlan.targetsiteid] || {}"
              :currentRun="currentRun" />
          </a-tab-pane>
          <a-tab-pane key="restorepoints" :tab="$t('label.dr.restore.points')">
            <dr-restore-points-tab v-if="detailPlan.id" :planId="detailPlan.id" />
          </a-tab-pane>
          <a-tab-pane key="replica" :tab="$t('label.dr.replica')">
            <dr-replica-tab v-if="detailPlan.id" :planId="detailPlan.id" />
          </a-tab-pane>
          <a-tab-pane key="runs" :tab="$t('label.dr.runs')">
            <dr-runs-tab v-if="detailPlan.id" :planId="detailPlan.id" />
          </a-tab-pane>
          <a-tab-pane key="events" :tab="$t('label.events')">
            <dr-events-tab v-if="detailPlan.id" :planId="detailPlan.id" :runId="$route.query.runid || ''" />
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </template>

    <template v-else>
      <a-card class="cross-dr-panel">
        <template #title>{{ $t('label.dr.plans') }}</template>
        <template #extra>
          <a-space wrap>
            <a-select
              v-model:value="filters.state"
              allowClear
              size="small"
              :placeholder="$t('label.state')"
              style="width: 150px">
              <a-select-option v-for="state in planStates" :key="state" :value="state">{{ state }}</a-select-option>
            </a-select>
            <a-select
              v-model:value="filters.direction"
              allowClear
              size="small"
              :placeholder="$t('label.dr.direction')"
              style="width: 180px">
              <a-select-option v-for="direction in directions" :key="direction" :value="direction">{{ direction }}</a-select-option>
            </a-select>
            <a-button
              v-if="'createDrPlan' in $store.getters.apis"
              type="primary"
              size="small"
              @click="openCreateModal">
              <template #icon><PlusOutlined /></template>
              {{ $t('label.dr.plan.add') }}
            </a-button>
            <a-button size="small" :loading="loading" @click="fetchList">
              <template #icon><ReloadOutlined /></template>
              {{ $t('label.refresh') }}
            </a-button>
          </a-space>
        </template>
        <a-table
          size="small"
          :columns="columns"
          :dataSource="filteredPlans"
          :rowKey="record => record.id"
          :loading="loading"
          :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record, text }">
            <template v-if="column.key === 'name'">
              <router-link :to="{ path: '/drplan/' + record.id }">{{ text || record.id }}</router-link>
            </template>
            <template v-else-if="column.key === 'state'">
              <dr-status-pill :status="text" />
            </template>
            <template v-else-if="column.key === 'sourcesiteid' || column.key === 'targetsiteid'">
              {{ siteName(text) }}
            </template>
            <template v-else-if="column.key === 'targetreadyrposeconds'">
              <dr-rpo-kpi
                class="cross-dr-table-kpi"
                :label="$t('label.dr.target.rpo')"
                :seconds="record.targetreadyrposeconds"
                :targetSeconds="record.rposeconds" />
            </template>
            <template v-else-if="column.key === 'actions'">
              <dr-action-toolbar
                compact
                :plan="record"
                :loadingAction="actionLoadingPlanId === record.id ? actionLoading : ''"
                @run-action="action => runPlanAction(action, record)" />
            </template>
          </template>
        </a-table>
      </a-card>
    </template>

    <a-modal
      :visible="showCreateModal"
      :title="$t('label.dr.plan.add')"
      :confirmLoading="createLoading"
      :okText="$t('label.ok')"
      :cancelText="$t('label.cancel')"
      @ok="createPlan"
      @cancel="closeCreateModal">
      <a-form layout="vertical">
        <a-form-item :label="$t('label.name')" required>
          <a-input v-model:value="createForm.name" />
        </a-form-item>
        <a-form-item :label="$t('label.description')">
          <a-input v-model:value="createForm.description" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.source.site')" required>
          <a-select v-model:value="createForm.sourcesiteid" showSearch optionFilterProp="label">
            <a-select-option v-for="site in sites" :key="site.id" :value="site.id" :label="site.name">{{ site.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="$t('label.dr.target.site')" required>
          <a-select v-model:value="createForm.targetsiteid" showSearch optionFilterProp="label">
            <a-select-option v-for="site in sites" :key="site.id" :value="site.id" :label="site.name">{{ site.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="$t('label.dr.direction')" required>
          <a-select v-model:value="createForm.direction">
            <a-select-option v-for="direction in directions" :key="direction" :value="direction">{{ direction }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="$t('label.dr.source.vm')">
          <a-input v-model:value="createForm.sourcevmid" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.engine')">
              <a-select v-model:value="createForm.enginetype" @change="onCreateEngineChange">
                <a-select-option
                  v-for="engine in engineOptions"
                  :key="engine.value"
                  :value="engine.value"
                  :disabled="engine.disabled">
                  {{ engine.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.engine.binding.type')">
              <a-input v-model:value="createForm.enginebindingtype" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.engine.binding.id')">
              <a-input-number v-model:value="createForm.enginebindingid" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.rpo')">
              <a-input-number v-model:value="createForm.rposeconds" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.rto')">
              <a-input-number v-model:value="createForm.rtoseconds" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.coordinator.worker.host')">
              <a-input v-model:value="createForm.coordinatorworkerhostid" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.source.worker.host')">
              <a-input v-model:value="createForm.sourceworkerhostid" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.target.worker.host')">
              <a-input v-model:value="createForm.targetworkerhostid" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="$t('label.dr.start.sync.after.create')">
          <a-switch v-model:checked="createForm.startsync" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.mapping.json')">
          <a-textarea v-model:value="createForm.mappingjson" :rows="4" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.schedule.json')">
          <a-textarea v-model:value="createForm.schedulejson" :rows="2" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.policy.json')">
          <a-textarea v-model:value="createForm.policyjson" :rows="2" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.quiesce.policy.json')">
          <a-textarea v-model:value="createForm.quiescepolicyjson" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      :visible="showActionModal"
      :title="actionModalTitle"
      :confirmLoading="actionSubmitting"
      :okText="$t('label.ok')"
      :cancelText="$t('label.cancel')"
      @ok="submitActionModal"
      @cancel="closeActionModal">
      <a-form layout="vertical" class="cross-dr-action-modal">
        <a-alert
          v-if="selectedAction.command"
          type="warning"
          show-icon
          :message="$t('message.dr.async.accepted')" />
        <a-form-item
          v-if="isFailoverAction || isReleaseAction"
          :label="$t('label.dr.action.force')">
          <a-switch v-model:checked="actionForm.force" />
        </a-form-item>
        <a-form-item
          v-if="isFailoverAction"
          :label="$t('label.dr.action.disaster')">
          <a-switch v-model:checked="actionForm.disaster" />
        </a-form-item>
        <a-form-item
          v-if="isFailoverAction && !actionForm.disaster"
          :label="$t('label.dr.action.final.sync')">
          <a-switch v-model:checked="actionForm.finalsync" />
        </a-form-item>
        <a-form-item
          v-if="isFailoverAction"
          :label="$t('label.dr.action.skip.source.fence')">
          <a-switch v-model:checked="actionForm.skipsourcefencerequest" />
        </a-form-item>
        <a-form-item
          v-if="isTestFailoverAction || isFailoverAction"
          :label="$t('label.dr.restore.points')">
          <a-select v-model:value="actionForm.restorepointid" allowClear>
            <a-select-option v-for="restorePoint in actionRestorePoints" :key="restorePoint.id" :value="restorePoint.id">
              {{ restorePointLabel(restorePoint) }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          v-if="isFailbackAction"
          :label="$t('label.dr.failback.target.mold.type')">
          <a-select v-model:value="actionForm.failbacktargetmoldtype">
            <a-select-option value="current">current</a-select-option>
            <a-select-option value="original-primary">original-primary</a-select-option>
            <a-select-option value="new">new</a-select-option>
          </a-select>
        </a-form-item>
        <template v-if="isFailbackAction || isFenceAction">
          <a-form-item :label="$t('label.dr.remote.mold.api.url')">
            <a-input v-model:value="actionForm.remotemoldapiurl" />
          </a-form-item>
          <a-form-item :label="$t('label.dr.remote.mold.api.key')">
            <a-input v-model:value="actionForm.remotemoldapikey" />
          </a-form-item>
          <a-form-item :label="$t('label.dr.remote.mold.secret.key')">
            <a-input-password v-model:value="actionForm.remotemoldsecretkey" />
          </a-form-item>
        </template>
        <template v-if="isFailbackAction">
          <a-form-item :label="$t('label.dr.target.mold.api.url')">
            <a-input v-model:value="actionForm.targetmoldapiurl" />
          </a-form-item>
          <a-form-item :label="$t('label.dr.target.mold.api.key')">
            <a-input v-model:value="actionForm.targetmoldapikey" />
          </a-form-item>
          <a-form-item :label="$t('label.dr.target.mold.secret.key')">
            <a-input-password v-model:value="actionForm.targetmoldsecretkey" />
          </a-form-item>
        </template>
        <a-form-item
          v-if="isAdoptAction"
          :label="$t('label.dr.replica')">
          <a-select v-model:value="actionForm.replicaid" allowClear>
            <a-select-option v-for="replica in actionReplicas" :key="replica.id" :value="replica.id">
              {{ replica.targetvmname || replica.targetexternalref || replica.id }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          v-if="isAdoptAction"
          :label="$t('label.dr.cleanup.transport')">
          <a-switch v-model:checked="actionForm.cleanuptransport" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.action.reason')">
          <a-input v-model:value="actionForm.reason" />
        </a-form-item>
        <a-form-item
          v-if="selectedAction.danger"
          :label="$t('label.dr.action.acknowledgement')">
          <a-input v-model:value="actionForm.acknowledgement" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { notification } from 'ant-design-vue'
import DrActionToolbar from '@/components/dr/DrActionToolbar.vue'
import DrEventsTab from '@/views/infra/dr/DrEventsTab.vue'
import DrPlanOverview from '@/views/infra/dr/DrPlanOverview.vue'
import DrReplicaTab from '@/views/infra/dr/DrReplicaTab.vue'
import DrRestorePointsTab from '@/views/infra/dr/DrRestorePointsTab.vue'
import DrRpoKpi from '@/components/dr/DrRpoKpi.vue'
import DrRunsTab from '@/views/infra/dr/DrRunsTab.vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { createDrPlan, getDrPlan, listDrPlans, listDrReplicas, listDrRestorePoints, listDrRuns, listDrSites, startDrAction } from '@/api/dr'

export default {
  name: 'DrPlanList',
  components: {
    DrActionToolbar,
    DrEventsTab,
    DrPlanOverview,
    DrReplicaTab,
    DrRestorePointsTab,
    DrRpoKpi,
    DrRunsTab,
    DrStatusPill
  },
  data () {
    return {
      loading: false,
      createLoading: false,
      actionLoading: '',
      actionLoadingPlanId: '',
      plans: [],
      sites: [],
      detailPlan: {},
      detailRuns: [],
      activeTab: this.$route.query.tab || 'overview',
      showCreateModal: false,
      showActionModal: false,
      actionSubmitting: false,
      selectedAction: {},
      selectedActionPlan: {},
      actionReplicas: [],
      actionRestorePoints: [],
      actionForm: this.defaultActionForm(),
      runtimePollTimer: null,
      runtimePollInFlight: false,
      runtimePollIntervalMs: 5000,
      filters: {
        state: undefined,
        direction: undefined
      },
      createForm: this.defaultCreateForm(),
      directions: ['KVM_TO_KVM', 'KVM_TO_VMWARE', 'VMWARE_TO_VMWARE', 'VMWARE_TO_KVM'],
      engineOptions: [
        { value: 'FTCTL_DR', label: 'FTCTL_DR' },
        { value: 'FTCTL', label: 'FTCTL' },
        { value: 'VMWARE_PHASE1', label: 'VMWARE_PHASE1' },
        { value: 'V2K', label: 'V2K (migration-only)', disabled: true }
      ],
      planStates: ['CREATED', 'ENABLED', 'SYNCING', 'READY', 'TESTING', 'FAILED_OVER', 'FAILBACK_READY', 'REPROTECTING', 'PAUSED', 'ERROR'],
      columns: [
        { key: 'name', title: this.$t('label.name'), dataIndex: 'name' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'direction', title: this.$t('label.dr.direction'), dataIndex: 'direction' },
        { key: 'sourcesiteid', title: this.$t('label.dr.source.site'), dataIndex: 'sourcesiteid' },
        { key: 'targetsiteid', title: this.$t('label.dr.target.site'), dataIndex: 'targetsiteid' },
        { key: 'targetreadyrposeconds', title: this.$t('label.dr.target.rpo'), dataIndex: 'targetreadyrposeconds' },
        { key: 'enginetype', title: this.$t('label.dr.engine'), dataIndex: 'enginetype' },
        { key: 'actions', title: this.$t('label.actions'), width: 280 }
      ]
    }
  },
  computed: {
    hasListApi () {
      return 'listDrPlans' in this.$store.getters.apis
    },
    detailId () {
      return this.$route.params.id || ''
    },
    siteById () {
      return this.sites.reduce((map, site) => {
        map[site.id] = site
        return map
      }, {})
    },
    filteredPlans () {
      return this.plans.filter(plan => {
        if (this.filters.state && plan.state !== this.filters.state) {
          return false
        }
        if (this.filters.direction && plan.direction !== this.filters.direction) {
          return false
        }
        return true
      })
    },
    currentRun () {
      return this.detailRuns.find(run => this.isActiveRun(run)) || this.detailRuns[0] || {}
    },
    actionModalTitle () {
      return this.selectedAction.label ? this.$t(this.selectedAction.label) : this.$t('label.actions')
    },
    isFailoverAction () {
      return this.selectedAction.command === 'startDrFailover'
    },
    isTestFailoverAction () {
      return this.selectedAction.command === 'startDrTestFailover'
    },
    isFenceAction () {
      return this.selectedAction.command === 'confirmDrFenceClear'
    },
    isFailbackAction () {
      return this.selectedAction.command === 'startDrFailback'
    },
    isAdoptAction () {
      return this.selectedAction.command === 'adoptDrReplica'
    },
    isReleaseAction () {
      return this.selectedAction.command === 'releaseDrProtection'
    }
  },
  watch: {
    '$route.fullPath': function () {
      this.activeTab = this.$route.query.tab || 'overview'
      this.fetchData()
    }
  },
  created () {
    this.fetchData()
  },
  beforeUnmount () {
    this.stopRuntimePolling()
  },
  methods: {
    defaultCreateForm () {
      return {
        name: '',
        description: '',
        sourcesiteid: undefined,
        targetsiteid: undefined,
        direction: 'KVM_TO_KVM',
        sourcevmid: '',
        enginetype: 'FTCTL_DR',
        enginebindingtype: 'FTCTL_DR',
        enginebindingid: undefined,
        rposeconds: 300,
        rtoseconds: 300,
        sourceworkerhostid: '',
        targetworkerhostid: '',
        coordinatorworkerhostid: '',
        mappingjson: '',
        schedulejson: '',
        policyjson: '',
        quiescepolicyjson: '',
        startsync: false
      }
    },
    defaultActionForm () {
      return {
        reason: '',
        acknowledgement: '',
        force: true,
        disaster: false,
        finalsync: true,
        skipsourcefencerequest: false,
        failbacktargetmoldtype: 'current',
        remotemoldapiurl: '',
        remotemoldapikey: '',
        remotemoldsecretkey: '',
        targetmoldapiurl: '',
        targetmoldapikey: '',
        targetmoldsecretkey: '',
        replicaid: undefined,
        restorepointid: undefined,
        cleanuptransport: true
      }
    },
    fetchData () {
      if (this.detailId) {
        this.fetchDetail()
      } else {
        this.stopRuntimePolling()
        this.fetchList()
      }
    },
    fetchSites () {
      if (!('listDrSites' in this.$store.getters.apis)) {
        this.sites = []
        return Promise.resolve()
      }
      return listDrSites().then(result => {
        this.sites = result.items || []
      })
    },
    fetchList () {
      this.loading = true
      Promise.all([
        this.fetchSites(),
        listDrPlans().then(result => {
          this.plans = result.items || []
        })
      ]).finally(() => {
        this.loading = false
      })
    },
    fetchDetail (options = {}) {
      if (!this.detailId) {
        return
      }
      const silent = options.silent === true
      if (!silent) {
        this.loading = true
      }
      const tasks = [
        getDrPlan(this.detailId).then(plan => {
          this.detailPlan = plan || {}
        }),
        this.fetchRuns()
      ]
      if (options.skipSites !== true) {
        tasks.unshift(this.fetchSites())
      }
      return Promise.all(tasks).finally(() => {
        if (!silent) {
          this.loading = false
        }
        this.scheduleRuntimePolling()
      })
    },
    fetchRuns () {
      if (!('listDrRuns' in this.$store.getters.apis)) {
        this.detailRuns = []
        return Promise.resolve()
      }
      return listDrRuns({ planid: this.detailId }).then(result => {
        this.detailRuns = result.items || []
      })
    },
    siteName (siteId) {
      return this.siteById[siteId]?.name || siteId || '-'
    },
    changeTab (tab) {
      this.activeTab = tab
      this.$router.replace({ path: this.$route.path, query: Object.assign({}, this.$route.query, { tab }) }).catch(() => {})
    },
    openCreateModal () {
      this.createForm = this.defaultCreateForm()
      this.showCreateModal = true
      this.fetchSites()
    },
    onCreateEngineChange (engineType) {
      this.createForm.enginebindingtype = engineType
      if (engineType === 'FTCTL') {
        this.createForm.direction = 'KVM_TO_KVM'
      }
    },
    closeCreateModal () {
      this.showCreateModal = false
    },
    createPlan () {
      if (!this.createForm.name || !this.createForm.sourcesiteid || !this.createForm.targetsiteid || !this.createForm.direction) {
        notification.warning({
          message: this.$t('label.dr.plan.add'),
          description: this.$t('message.dr.required.fields')
        })
        return
      }
      this.createLoading = true
      createDrPlan(this.compactPayload(this.createForm)).then(plan => {
        notification.success({
          message: this.$t('label.dr.plan.add'),
          description: this.createForm.startsync
            ? this.$t('message.dr.create.sync.accepted')
            : (plan.name || plan.id || this.$t('label.success'))
        })
        this.closeCreateModal()
        this.fetchList()
      }).finally(() => {
        this.createLoading = false
      })
    },
    compactPayload (payload) {
      return Object.keys(payload || {}).reduce((result, key) => {
        const value = payload[key]
        if (value !== '' && value !== undefined && value !== null) {
          result[key] = value
        }
        return result
      }, {})
    },
    runPlanAction (action, plan) {
      if (this.requiresActionModal(action)) {
        this.openActionModal(action, plan)
        return
      }
      this.executePlanAction(action, plan, {})
    },
    requiresActionModal (action) {
      return ['startDrTestFailover', 'startDrFailover', 'confirmDrFenceClear', 'startDrFailback', 'adoptDrReplica', 'releaseDrProtection', 'cancelDrRun', 'stopDrTestFailover'].includes(action.command)
    },
    openActionModal (action, plan) {
      this.selectedAction = action
      this.selectedActionPlan = plan
      this.actionForm = this.defaultActionForm()
      this.actionReplicas = []
      this.actionRestorePoints = []
      this.showActionModal = true
      if (action.command === 'adoptDrReplica' && 'listDrReplicas' in this.$store.getters.apis) {
        listDrReplicas({ planid: plan.id }).then(result => {
          this.actionReplicas = result.items || []
        })
      }
      if (['startDrTestFailover', 'startDrFailover'].includes(action.command) && 'listDrRestorePoints' in this.$store.getters.apis) {
        listDrRestorePoints({ planid: plan.id }).then(result => {
          this.actionRestorePoints = result.items || []
          if (!this.actionForm.restorepointid && this.actionRestorePoints.length > 0) {
            this.actionForm.restorepointid = this.actionRestorePoints[0].id
          }
        })
      }
    },
    closeActionModal () {
      this.showActionModal = false
      this.selectedAction = {}
      this.selectedActionPlan = {}
      this.actionReplicas = []
      this.actionRestorePoints = []
      this.actionForm = this.defaultActionForm()
    },
    submitActionModal () {
      this.actionSubmitting = true
      this.executePlanAction(this.selectedAction, this.selectedActionPlan, this.buildActionPayload())
        .then(() => {
          this.closeActionModal()
        })
        .finally(() => {
          this.actionSubmitting = false
        })
    },
    buildActionPayload () {
      const payload = {
        reason: this.actionForm.reason || undefined,
        acknowledgement: this.actionForm.acknowledgement || undefined
      }
      if (this.isFailoverAction) {
        payload.force = this.actionForm.force
        payload.disaster = this.actionForm.disaster
        payload.finalsync = !this.actionForm.disaster && this.actionForm.finalsync
        payload.restorepointid = this.actionForm.restorepointid || undefined
        payload.skipsourcefencerequest = this.actionForm.skipsourcefencerequest
      }
      if (this.isReleaseAction) {
        payload.force = this.actionForm.force
      }
      if (this.isFenceAction || this.isFailbackAction) {
        payload.remotemoldapiurl = this.actionForm.remotemoldapiurl || undefined
        payload.remotemoldapikey = this.actionForm.remotemoldapikey || undefined
        payload.remotemoldsecretkey = this.actionForm.remotemoldsecretkey || undefined
      }
      if (this.isFailbackAction) {
        payload.force = this.actionForm.force
        payload.failbacktargetmoldtype = this.actionForm.failbacktargetmoldtype
        payload.targetmoldapiurl = this.actionForm.targetmoldapiurl || undefined
        payload.targetmoldapikey = this.actionForm.targetmoldapikey || undefined
        payload.targetmoldsecretkey = this.actionForm.targetmoldsecretkey || undefined
      }
      if (this.isAdoptAction) {
        payload.replicaid = this.actionForm.replicaid || undefined
        payload.cleanuptransport = this.actionForm.cleanuptransport
      }
      if (this.isTestFailoverAction) {
        payload.restorepointid = this.actionForm.restorepointid || undefined
      }
      return payload
    },
    executePlanAction (action, plan, payload) {
      this.actionLoading = action.command
      this.actionLoadingPlanId = plan.id
      const params = action.command === 'cancelDrRun'
        ? Object.assign({ id: action.currentRun?.id || this.currentRun.id }, payload)
        : Object.assign({ planid: plan.id }, payload)
      return startDrAction(action.command, params).then(run => {
        notification.success({
          message: this.$t(action.label),
          description: run.id || run.state || this.$t('label.success')
        })
        this.applyAcceptedRun(run, plan)
        this.fetchData()
      }).finally(() => {
        this.actionLoading = ''
        this.actionLoadingPlanId = ''
      })
    },
    applyAcceptedRun (run, plan) {
      if (!run || !run.id || !this.detailId || String(plan.id) !== String(this.detailId)) {
        return
      }
      this.detailRuns = [
        run,
        ...this.detailRuns.filter(item => String(item.id) !== String(run.id))
      ]
      this.scheduleRuntimePolling()
    },
    isActiveRun (run) {
      return ['QUEUED', 'DISPATCHING', 'ACCEPTED', 'RUNNING', 'CANCEL_REQUESTED'].includes(String(run?.state || '').toUpperCase())
    },
    isRuntimePlanState (state) {
      return ['SYNCING', 'TESTING'].includes(String(state || '').toUpperCase())
    },
    shouldPollRuntime () {
      return !!this.detailId && !!this.detailPlan.id && (this.isActiveRun(this.currentRun) || this.isRuntimePlanState(this.detailPlan.state))
    },
    scheduleRuntimePolling () {
      if (!this.shouldPollRuntime()) {
        this.stopRuntimePolling()
        return
      }
      if (this.runtimePollTimer) {
        return
      }
      this.runtimePollTimer = window.setInterval(this.pollRuntime, this.runtimePollIntervalMs)
    },
    stopRuntimePolling () {
      if (this.runtimePollTimer) {
        window.clearInterval(this.runtimePollTimer)
        this.runtimePollTimer = null
      }
    },
    pollRuntime () {
      if (this.runtimePollInFlight || !this.detailId) {
        return
      }
      this.runtimePollInFlight = true
      this.fetchDetail({ silent: true, skipSites: true }).finally(() => {
        this.runtimePollInFlight = false
      })
    },
    restorePointLabel (restorePoint) {
      return [
        restorePoint.targetreadyat || restorePoint.created || restorePoint.id,
        restorePoint.sourcesnapshotref || restorePoint.restorepointtype
      ].filter(Boolean).join(' / ')
    }
  }
}
</script>

<style lang="less">
.cross-dr-page {
  display: grid;
  gap: 14px;
}

.cross-dr-panel {
  width: 100%;
}

.cross-dr-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.cross-dr-table-kpi {
  min-width: 110px;
  padding: 6px 8px;
}

.cross-dr-table-kpi .cross-dr-kpi__label {
  display: none;
}

.cross-dr-table-kpi .cross-dr-kpi__value {
  font-size: 14px;
  line-height: 20px;
}

.cross-dr-action-modal {
  display: grid;
  gap: 10px;
}

.cross-dr-action-modal .ant-alert {
  margin-bottom: 4px;
}

body.dark-mode .cross-dr-page {
  --cross-dr-border: rgba(255, 255, 255, 0.12);
  --cross-dr-surface: rgba(255, 255, 255, 0.04);
  --cross-dr-surface-muted: rgba(255, 255, 255, 0.06);
  --cross-dr-text: rgba(255, 255, 255, 0.86);
  --cross-dr-text-secondary: rgba(255, 255, 255, 0.58);
}
</style>
