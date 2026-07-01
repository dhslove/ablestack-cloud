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
  <a-spin :spinning="loading || localLoading">
    <div class="cross-dr-page">
      <div class="cross-dr-tab-toolbar">
        <router-link :to="{ path: '/drplan' }">
          <a-button size="small">
            <template #icon><BranchesOutlined /></template>
            {{ $t('label.dr.plans') }}
          </a-button>
        </router-link>
        <a-button size="small" @click="fetchData">
          <template #icon><ReloadOutlined /></template>
          {{ $t('label.refresh') }}
        </a-button>
      </div>

      <a-empty v-if="plans.length === 0" :description="$t('message.dr.no.vm.plan')" />

      <div v-else class="cross-dr-vm-plans">
        <a-card v-for="plan in plans" :key="plan.id" size="small" class="cross-dr-panel">
          <template #title>
            <router-link :to="{ path: '/drplan/' + plan.id }">{{ plan.name || plan.id }}</router-link>
          </template>
          <template #extra>
            <dr-status-pill :status="plan.state" />
          </template>
          <div class="cross-dr-vm-plan__body">
            <div class="cross-dr-overview__kpis">
              <div class="cross-dr-kpi">
                <div class="cross-dr-kpi__label">{{ $t('label.dr.direction') }}</div>
                <div class="cross-dr-kpi__value cross-dr-kpi__value--small">{{ plan.direction || '-' }}</div>
                <div class="cross-dr-kpi__meta">{{ plan.enginetype || '-' }}</div>
              </div>
              <dr-rpo-kpi
                :label="$t('label.dr.target.rpo')"
                :seconds="plan.targetreadyrposeconds"
                :targetSeconds="plan.rposeconds" />
              <div class="cross-dr-kpi">
                <div class="cross-dr-kpi__label">{{ $t('label.dr.target.ready.at') }}</div>
                <div class="cross-dr-kpi__value cross-dr-kpi__value--small">{{ plan.targetreadyat || '-' }}</div>
                <div class="cross-dr-kpi__meta">{{ plan.enginebindingtype || '-' }} / {{ plan.enginebindingid || '-' }}</div>
              </div>
            </div>
            <dr-action-toolbar
              :plan="plan"
              :loadingAction="actionLoadingPlanId === plan.id ? actionLoading : ''"
              @run-action="action => runPlanAction(action, plan)" />
          </div>
        </a-card>
      </div>
    </div>
  </a-spin>
</template>

<script>
import { notification } from 'ant-design-vue'
import DrActionToolbar from '@/components/dr/DrActionToolbar.vue'
import DrRpoKpi from '@/components/dr/DrRpoKpi.vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { listDrPlans, startDrAction } from '@/api/dr'

export default {
  name: 'DrPlanVmTab',
  components: {
    DrActionToolbar,
    DrRpoKpi,
    DrStatusPill
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
      localLoading: false,
      actionLoading: '',
      actionLoadingPlanId: '',
      plans: []
    }
  },
  watch: {
    resource: {
      deep: true,
      handler () {
        this.fetchData()
      }
    }
  },
  created () {
    this.fetchData()
  },
  methods: {
    fetchData () {
      if (!('listDrPlans' in this.$store.getters.apis)) {
        this.plans = []
        return
      }
      this.localLoading = true
      listDrPlans().then(result => {
        this.plans = (result.items || []).filter(plan => this.isVmPlan(plan))
      }).finally(() => {
        this.localLoading = false
      })
    },
    isVmPlan (plan) {
      const vmKeys = [
        this.resource.id,
        this.resource.instancename,
        this.resource.name,
        this.resource.displayname
      ].filter(Boolean).map(value => String(value))
      return vmKeys.includes(String(plan.sourcevmid)) ||
        vmKeys.includes(String(plan.sourceexternalref)) ||
        vmKeys.includes(String(plan.sourcevmname))
    },
    runPlanAction (action, plan) {
      this.actionLoading = action.command
      this.actionLoadingPlanId = plan.id
      startDrAction(action.command, { planid: plan.id }).then(run => {
        notification.success({
          message: this.$t(action.label),
          description: run.id || run.state || this.$t('label.success')
        })
        this.fetchData()
      }).finally(() => {
        this.actionLoading = ''
        this.actionLoadingPlanId = ''
      })
    }
  }
}
</script>

<style lang="less">
.cross-dr-vm-plans {
  display: grid;
  gap: 12px;
}

.cross-dr-vm-plan__body {
  display: grid;
  gap: 12px;
}
</style>
