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
  <div class="cross-dr-overview">
    <dr-topology :plan="plan" :sourceSite="sourceSite" :targetSite="targetSite" />

    <div class="cross-dr-overview__kpis">
      <div class="cross-dr-kpi">
        <div class="cross-dr-kpi__label">{{ $t('label.state') }}</div>
        <div class="cross-dr-kpi__status"><dr-status-pill :status="plan.state" /></div>
        <div class="cross-dr-kpi__meta">{{ plan.adminstate || '-' }}</div>
      </div>
      <dr-rpo-kpi
        :label="$t('label.dr.target.rpo')"
        :seconds="plan.targetreadyrposeconds"
        :targetSeconds="plan.rposeconds" />
      <div class="cross-dr-kpi">
        <div class="cross-dr-kpi__label">{{ $t('label.dr.target.ready.at') }}</div>
        <div class="cross-dr-kpi__value cross-dr-kpi__value--small">{{ plan.targetreadyat || '-' }}</div>
        <div class="cross-dr-kpi__meta">{{ $t('label.dr.rto') }}: {{ formatSeconds(plan.rtoseconds) }}</div>
      </div>
      <div class="cross-dr-kpi">
        <div class="cross-dr-kpi__label">{{ $t('label.dr.engine') }}</div>
        <div class="cross-dr-kpi__value cross-dr-kpi__value--small">{{ plan.enginetype || '-' }}</div>
        <div class="cross-dr-kpi__meta">{{ plan.enginebindingtype || '-' }} / {{ plan.enginebindingid || '-' }}</div>
      </div>
    </div>

    <a-alert
      v-if="plan.lasterrorcode || plan.lasterrormessage"
      type="warning"
      show-icon
      class="cross-dr-risk">
      <template #message>
        <div>{{ plan.lasterrorcode || $t('label.error') }}</div>
        <div class="cross-dr-risk__body">{{ plan.lasterrormessage || '-' }}</div>
      </template>
    </a-alert>

    <dr-run-progress v-if="currentRun && currentRun.id" :run="currentRun" />

    <a-descriptions bordered size="small" :column="descriptionColumn">
      <a-descriptions-item :label="$t('label.id')">{{ plan.id }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.name')">{{ plan.name || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.description')">{{ plan.description || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.direction')">{{ plan.direction || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.active.side')">{{ plan.activeside || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.source.vm')">
        <router-link v-if="plan.sourcevmid" :to="{ path: '/vm/' + plan.sourcevmid }">{{ plan.sourcevmid }}</router-link>
        <span v-else>{{ plan.sourceexternalref || '-' }}</span>
      </a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.source.worker.host')">{{ plan.sourceworkerhostid || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.target.worker.host')">{{ plan.targetworkerhostid || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.coordinator.worker.host')">{{ plan.coordinatorworkerhostid || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.last.source.checkpoint')">{{ plan.lastsourcecheckpointat || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.dr.last.target.durable')">{{ plan.lasttargetdurableat || '-' }}</a-descriptions-item>
      <a-descriptions-item :label="$t('label.created')">{{ plan.created || '-' }}</a-descriptions-item>
    </a-descriptions>
  </div>
</template>

<script>
import DrRpoKpi from '@/components/dr/DrRpoKpi.vue'
import DrRunProgress from '@/components/dr/DrRunProgress.vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import DrTopology from '@/components/dr/DrTopology.vue'
import { mixinDevice } from '@/utils/mixin.js'

export default {
  name: 'DrPlanOverview',
  components: {
    DrRpoKpi,
    DrRunProgress,
    DrStatusPill,
    DrTopology
  },
  mixins: [mixinDevice],
  props: {
    plan: {
      type: Object,
      required: true
    },
    sourceSite: {
      type: Object,
      default: () => ({})
    },
    targetSite: {
      type: Object,
      default: () => ({})
    },
    currentRun: {
      type: Object,
      default: () => ({})
    }
  },
  computed: {
    descriptionColumn () {
      return this.device === 'mobile' ? 1 : 2
    }
  },
  methods: {
    formatSeconds (seconds) {
      const value = Number(seconds)
      if (!Number.isFinite(value)) {
        return '-'
      }
      if (value < 60) {
        return `${Math.round(value)}s`
      }
      if (value < 3600) {
        return `${Math.round(value / 60)}m`
      }
      if (value < 86400) {
        return `${Math.round(value / 3600)}h`
      }
      return `${Math.round(value / 86400)}d`
    }
  }
}
</script>

<style lang="less">
.cross-dr-overview {
  display: grid;
  gap: 14px;
}

.cross-dr-overview__kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.cross-dr-kpi__status {
  min-height: 30px;
  display: flex;
  align-items: center;
}

.cross-dr-kpi__value--small {
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

.cross-dr-risk {
  border-radius: 6px;
}

.cross-dr-risk__body {
  margin-top: 4px;
  color: var(--cross-dr-text-secondary, rgba(0, 0, 0, 0.55));
  font-size: 12px;
  line-height: 18px;
}

body.dark-mode .cross-dr-risk__body {
  --cross-dr-text-secondary: rgba(255, 255, 255, 0.64);
}
</style>
