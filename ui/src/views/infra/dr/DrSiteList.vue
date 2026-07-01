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
            <router-link :to="{ path: '/drsite' }">{{ $t('label.dr.sites') }}</router-link>
            <span>/</span>
            <span>{{ detailSite.name || detailSite.id || '-' }}</span>
          </div>
        </template>
        <template #extra>
          <a-space wrap>
            <a-button
              v-if="'checkDrSite' in $store.getters.apis"
              size="small"
              :loading="checking"
              @click="checkSite(detailSite)">
              <template #icon><ApiOutlined /></template>
              {{ $t('label.dr.site.check') }}
            </a-button>
            <a-button size="small" @click="fetchDetail">
              <template #icon><ReloadOutlined /></template>
              {{ $t('label.refresh') }}
            </a-button>
          </a-space>
        </template>

        <a-tabs :activeKey="activeTab" :animated="false" @change="changeTab">
          <a-tab-pane key="overview" :tab="$t('label.overview')">
            <div class="cross-dr-overview">
              <div class="cross-dr-overview__kpis">
                <div class="cross-dr-kpi">
                  <div class="cross-dr-kpi__label">{{ $t('label.dr.site.health') }}</div>
                  <div class="cross-dr-kpi__status"><dr-status-pill :status="detailSite.healthstate" /></div>
                  <div class="cross-dr-kpi__meta">{{ detailSite.lastchecked || '-' }}</div>
                </div>
                <div class="cross-dr-kpi">
                  <div class="cross-dr-kpi__label">{{ $t('label.type') }}</div>
                  <div class="cross-dr-kpi__value cross-dr-kpi__value--small">{{ detailSite.sitetype || '-' }}</div>
                  <div class="cross-dr-kpi__meta">{{ detailSite.hypervisortype || '-' }}</div>
                </div>
                <div class="cross-dr-kpi">
                  <div class="cross-dr-kpi__label">{{ $t('label.state') }}</div>
                  <div class="cross-dr-kpi__status"><dr-status-pill :status="detailSite.state" /></div>
                  <div class="cross-dr-kpi__meta">{{ detailSite.credentialref || '-' }}</div>
                </div>
              </div>
              <a-descriptions bordered size="small" :column="descriptionColumn">
                <a-descriptions-item :label="$t('label.id')">{{ detailSite.id }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.name')">{{ detailSite.name || '-' }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.description')">{{ detailSite.description || '-' }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.dr.endpoint')">{{ detailSite.endpoint || '-' }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.zoneid')">{{ detailSite.zoneid || '-' }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.dr.vmware.dc')">{{ detailSite.vmwaredcid || '-' }}</a-descriptions-item>
                <a-descriptions-item :label="$t('label.created')">{{ detailSite.created || '-' }}</a-descriptions-item>
              </a-descriptions>
              <pre v-if="detailSite.capabilities" class="cross-dr-code">{{ detailSite.capabilities }}</pre>
            </div>
          </a-tab-pane>
          <a-tab-pane key="plans" :tab="$t('label.dr.plans')">
            <a-table
              size="small"
              :columns="planColumns"
              :dataSource="sitePlans"
              :rowKey="record => record.id"
              :pagination="{ pageSize: 10 }">
              <template #bodyCell="{ column, record, text }">
                <template v-if="column.key === 'name'">
                  <router-link :to="{ path: '/drplan/' + record.id }">{{ text || record.id }}</router-link>
                </template>
                <template v-else-if="column.key === 'state'">
                  <dr-status-pill :status="text" />
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </template>

    <template v-else>
      <a-card class="cross-dr-panel">
        <template #title>{{ $t('label.dr.sites') }}</template>
        <template #extra>
          <a-space wrap>
            <a-select
              v-model:value="filters.health"
              allowClear
              size="small"
              :placeholder="$t('label.dr.site.health')"
              style="width: 170px">
              <a-select-option v-for="state in healthStates" :key="state" :value="state">{{ state }}</a-select-option>
            </a-select>
            <a-button
              v-if="'createDrSite' in $store.getters.apis"
              type="primary"
              size="small"
              @click="openCreateModal">
              <template #icon><PlusOutlined /></template>
              {{ $t('label.dr.site.add') }}
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
          :dataSource="filteredSites"
          :rowKey="record => record.id"
          :loading="loading"
          :pagination="{ pageSize: 10 }">
          <template #bodyCell="{ column, record, text }">
            <template v-if="column.key === 'name'">
              <router-link :to="{ path: '/drsite/' + record.id }">{{ text || record.id }}</router-link>
            </template>
            <template v-else-if="column.key === 'healthstate' || column.key === 'state'">
              <dr-status-pill :status="text" />
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-button
                v-if="'checkDrSite' in $store.getters.apis"
                size="small"
                :loading="checkingId === record.id"
                @click="checkSite(record)">
                <template #icon><ApiOutlined /></template>
                {{ $t('label.dr.site.check') }}
              </a-button>
            </template>
          </template>
        </a-table>
      </a-card>
    </template>

    <a-modal
      :visible="showCreateModal"
      :title="$t('label.dr.site.add')"
      :confirmLoading="createLoading"
      :okText="$t('label.ok')"
      :cancelText="$t('label.cancel')"
      @ok="createSite"
      @cancel="closeCreateModal">
      <a-form layout="vertical">
        <a-form-item :label="$t('label.name')" required>
          <a-input v-model:value="createForm.name" />
        </a-form-item>
        <a-form-item :label="$t('label.description')">
          <a-input v-model:value="createForm.description" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.type')" required>
              <a-select v-model:value="createForm.sitetype">
                <a-select-option value="MOLD_KVM">MOLD_KVM</a-select-option>
                <a-select-option value="MOLD_VMWARE">MOLD_VMWARE</a-select-option>
                <a-select-option value="VMWARE_DIRECT">VMWARE_DIRECT</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.hypervisor')" required>
              <a-select v-model:value="createForm.hypervisortype">
                <a-select-option value="KVM">KVM</a-select-option>
                <a-select-option value="VMWARE">VMWARE</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item :label="$t('label.dr.endpoint')">
          <a-input v-model:value="createForm.endpoint" />
        </a-form-item>
        <a-form-item :label="$t('label.dr.credential.ref')">
          <a-input v-model:value="createForm.credentialref" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item :label="$t('label.zoneid')">
              <a-input-number v-model:value="createForm.zoneid" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('label.dr.vmware.dc')">
              <a-input-number v-model:value="createForm.vmwaredcid" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script>
import { notification } from 'ant-design-vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { checkDrSite, createDrSite, getDrSite, listDrPlans, listDrSites } from '@/api/dr'
import { mixinDevice } from '@/utils/mixin.js'

export default {
  name: 'DrSiteList',
  components: {
    DrStatusPill
  },
  mixins: [mixinDevice],
  data () {
    return {
      loading: false,
      checking: false,
      checkingId: '',
      createLoading: false,
      showCreateModal: false,
      sites: [],
      plans: [],
      detailSite: {},
      activeTab: this.$route.query.tab || 'overview',
      filters: {
        health: undefined
      },
      createForm: this.defaultCreateForm(),
      healthStates: ['CONNECTED', 'DEGRADED', 'DISCONNECTED', 'UNKNOWN'],
      columns: [
        { key: 'name', title: this.$t('label.name'), dataIndex: 'name' },
        { key: 'sitetype', title: this.$t('label.type'), dataIndex: 'sitetype' },
        { key: 'hypervisortype', title: this.$t('label.hypervisor'), dataIndex: 'hypervisortype' },
        { key: 'endpoint', title: this.$t('label.dr.endpoint'), dataIndex: 'endpoint', ellipsis: true },
        { key: 'healthstate', title: this.$t('label.dr.site.health'), dataIndex: 'healthstate' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'lastchecked', title: this.$t('label.dr.last.checked'), dataIndex: 'lastchecked' },
        { key: 'actions', title: this.$t('label.actions'), width: 120 }
      ],
      planColumns: [
        { key: 'name', title: this.$t('label.name'), dataIndex: 'name' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'direction', title: this.$t('label.dr.direction'), dataIndex: 'direction' },
        { key: 'enginetype', title: this.$t('label.dr.engine'), dataIndex: 'enginetype' },
        { key: 'targetreadyat', title: this.$t('label.dr.target.ready.at'), dataIndex: 'targetreadyat' }
      ]
    }
  },
  computed: {
    hasListApi () {
      return 'listDrSites' in this.$store.getters.apis
    },
    detailId () {
      return this.$route.params.id || ''
    },
    filteredSites () {
      return this.sites.filter(site => {
        if (this.filters.health && site.healthstate !== this.filters.health) {
          return false
        }
        return true
      })
    },
    sitePlans () {
      return this.plans.filter(plan => plan.sourcesiteid === this.detailSite.id || plan.targetsiteid === this.detailSite.id)
    },
    descriptionColumn () {
      return this.device === 'mobile' ? 1 : 2
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
  methods: {
    defaultCreateForm () {
      return {
        name: '',
        description: '',
        sitetype: 'MOLD_KVM',
        hypervisortype: 'KVM',
        endpoint: '',
        credentialref: '',
        zoneid: undefined,
        vmwaredcid: undefined
      }
    },
    fetchData () {
      if (this.detailId) {
        this.fetchDetail()
      } else {
        this.fetchList()
      }
    },
    fetchList () {
      this.loading = true
      listDrSites().then(result => {
        this.sites = result.items || []
      }).finally(() => {
        this.loading = false
      })
    },
    fetchDetail () {
      if (!this.detailId) {
        return
      }
      this.loading = true
      Promise.all([
        getDrSite(this.detailId).then(site => {
          this.detailSite = site || {}
        }),
        this.fetchPlans()
      ]).finally(() => {
        this.loading = false
      })
    },
    fetchPlans () {
      if (!('listDrPlans' in this.$store.getters.apis)) {
        this.plans = []
        return Promise.resolve()
      }
      return listDrPlans().then(result => {
        this.plans = result.items || []
      })
    },
    changeTab (tab) {
      this.activeTab = tab
      this.$router.replace({ path: this.$route.path, query: Object.assign({}, this.$route.query, { tab }) }).catch(() => {})
    },
    checkSite (site) {
      if (!site?.id) {
        return
      }
      this.checking = true
      this.checkingId = site.id
      checkDrSite(site.id, true).then(result => {
        notification.success({
          message: this.$t('label.dr.site.check'),
          description: result.healthstate || result.state || this.$t('label.success')
        })
        this.fetchData()
      }).finally(() => {
        this.checking = false
        this.checkingId = ''
      })
    },
    openCreateModal () {
      this.createForm = this.defaultCreateForm()
      this.showCreateModal = true
    },
    closeCreateModal () {
      this.showCreateModal = false
    },
    createSite () {
      if (!this.createForm.name || !this.createForm.sitetype || !this.createForm.hypervisortype) {
        notification.warning({
          message: this.$t('label.dr.site.add'),
          description: this.$t('message.dr.required.fields')
        })
        return
      }
      this.createLoading = true
      createDrSite(this.createForm).then(site => {
        notification.success({
          message: this.$t('label.dr.site.add'),
          description: site.name || site.id || this.$t('label.success')
        })
        this.closeCreateModal()
        this.fetchList()
      }).finally(() => {
        this.createLoading = false
      })
    }
  }
}
</script>

<style lang="less">
.cross-dr-code {
  max-height: 260px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border-radius: 6px;
  background: var(--cross-dr-code-bg, #f6f8fa);
  color: var(--cross-dr-text, rgba(0, 0, 0, 0.85));
  white-space: pre-wrap;
  word-break: break-word;
}

body.dark-mode .cross-dr-code {
  --cross-dr-code-bg: rgba(0, 0, 0, 0.24);
  --cross-dr-text: rgba(255, 255, 255, 0.86);
}
</style>
