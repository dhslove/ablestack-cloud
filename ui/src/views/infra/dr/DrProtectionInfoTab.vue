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
  <div class="cross-dr-protection-info">
    <div class="cross-dr-tab-toolbar">
      <span class="cross-dr-protection-info__cache">
        {{ $t('label.dr.protection.view.generated') }}: {{ generated || '-' }}
      </span>
      <dr-status-pill :status="projectionState || 'UNKNOWN'" />
    </div>

    <a-alert
      v-if="lastError"
      type="warning"
      show-icon
      :message="$t('message.dr.protection.view.stale')"
      :description="lastError" />

    <a-alert
      v-if="plan.projectionintegritystate === 'INCONSISTENT'"
      type="error"
      show-icon
      :message="$t('message.dr.projection.integrity.failed')"
      :description="$t('message.dr.projection.integrity.failed.detail', {
        code: plan.projectionintegritycode || '-',
        sequence: plan.projectionintegritysequence || '-'
      })" />

    <a-alert
      v-if="cycleDataCopied && !cycleTargetDurable"
      type="warning"
      show-icon
      :message="$t('message.dr.cycle.uncommitted')" />

    <section class="cross-dr-protection-info__section">
      <dr-plan-overview
        :plan="plan"
        :sourceSite="sourceSite"
        :targetSite="targetSite"
        :currentRun="currentRun"
        :showDetails="false"
        :showProtectionSummary="true" />
    </section>

    <section class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.protection.topology') }}</h3>
      <dr-topology :plan="plan" :sourceSite="sourceSite" :targetSite="targetSite" />
    </section>

    <section class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.control.coordination') }}</h3>
      <a-descriptions size="small" :column="2" bordered>
        <a-descriptions-item :label="$t('label.dr.control.protocol')">
          {{ plan.runtimecontrolprotocolversion || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.control.readiness')">
          <dr-status-pill :status="plan.runtimecontrolready === true ? 'READY' : 'NOT_READY'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.control.state')">
          <dr-status-pill :status="plan.runtimecontrolstate || 'UNKNOWN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.replication.cycle.state')">
          <dr-status-pill :status="plan.runtimecyclestate || 'UNKNOWN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.control.generation')">
          {{ plan.runtimecontrolgeneration || '-' }} / {{ plan.runtimecontrolackgeneration || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.transition.state')">
          <dr-status-pill :status="plan.runtimetransitionstate || 'IDLE'" />
        </a-descriptions-item>
      </a-descriptions>
    </section>

    <section v-if="hasCycleCommitState" class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.cycle.commit') }}</h3>
      <a-descriptions size="small" :column="2" bordered>
        <a-descriptions-item :label="$t('label.dr.data.commit.state')">
          <dr-status-pill :status="plan.datacommitstate || 'UNKNOWN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.cycle.retry.mode')">
          {{ plan.cycleretrymode || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.data.copied')">
          <dr-status-pill :status="cycleDataCopied ? 'READY' : 'NOT_READY'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.metadata.committed')">
          <dr-status-pill :status="cycleMetadataCommitted ? 'READY' : 'NOT_READY'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.target.durable')">
          <dr-status-pill :status="cycleTargetDurable ? 'READY' : 'NOT_READY'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.failed.component')">
          {{ plan.failedcomponent || '-' }}
        </a-descriptions-item>
      </a-descriptions>
    </section>

    <section v-if="hasSourceSnapshotState" class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.source.snapshot.lifecycle') }}</h3>
      <a-descriptions size="small" :column="2" bordered>
        <a-descriptions-item :label="$t('label.state')">
          <dr-status-pill :status="plan.runtimesourcesnapshotlifecyclestate || 'UNKNOWN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.source.snapshot.cleanup.required')">
          <dr-status-pill :status="plan.runtimesourcesnapshotcleanuprequired ? 'REQUIRED' : 'CLEAN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.source.snapshot.active.reference')">
          {{ plan.runtimesourcesnapshotrefpresent ? plan.runtimesourcesnapshotname : '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.source.snapshot.last.reference')">
          {{ plan.runtimesourcesnapshotlastref || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.source.snapshot.cleaned.at')">
          {{ formatEpoch(plan.runtimesourcesnapshotcleanedatepochms) }}
        </a-descriptions-item>
      </a-descriptions>
    </section>

    <section class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.latest.completed.checkpoint') }}</h3>
      <a-descriptions v-if="latestCompletedCheckpoint && latestCompletedCheckpoint.id" size="small" :column="2" bordered>
        <a-descriptions-item :label="$t('label.dr.checkpoint.sequence')">
          {{ latestCompletedCheckpoint.checkpointsequence || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.state')">
          <dr-status-pill :status="latestCompletedCheckpoint.state || 'UNKNOWN'" />
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.last.source.checkpoint')">
          {{ latestCompletedCheckpoint.sourcecreated || '-' }}
        </a-descriptions-item>
        <a-descriptions-item :label="$t('label.dr.last.target.durable')">
          {{ latestCompletedCheckpoint.targetreadyat || '-' }}
        </a-descriptions-item>
      </a-descriptions>
      <a-empty v-else :description="$t('message.dr.no.completed.checkpoint')" />
    </section>

    <section class="cross-dr-protection-info__section">
      <h3>{{ $t('label.dr.replica') }}</h3>
      <a-table
        size="small"
        :columns="columns"
        :dataSource="replicas"
        :rowKey="record => record.uuid || record.id"
        :pagination="false">
        <template #bodyCell="{ column, record, text }">
          <template v-if="column.key === 'state' || column.key === 'powerstate'">
            <dr-status-pill :status="text || 'UNKNOWN'" />
          </template>
          <template v-else-if="column.key === 'targetvmname'">
            {{ text || record.targetexternalref || '-' }}
          </template>
        </template>
      </a-table>
    </section>
  </div>
</template>

<script>
import DrPlanOverview from '@/views/infra/dr/DrPlanOverview.vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import DrTopology from '@/components/dr/DrTopology.vue'

export default {
  name: 'DrProtectionInfoTab',
  components: { DrPlanOverview, DrStatusPill, DrTopology },
  props: {
    plan: { type: Object, required: true },
    sourceSite: { type: Object, default: () => ({}) },
    targetSite: { type: Object, default: () => ({}) },
    currentRun: { type: Object, default: () => ({}) },
    replicas: { type: Array, default: () => [] },
    latestCompletedCheckpoint: { type: Object, default: () => ({}) },
    generated: { type: String, default: '' },
    projectionState: { type: String, default: '' },
    lastError: { type: String, default: '' }
  },
  data () {
    return {
      columns: [
        { key: 'targetvmname', title: this.$t('label.dr.target.vm'), dataIndex: 'targetvmname' },
        { key: 'state', title: this.$t('label.state'), dataIndex: 'state' },
        { key: 'powerstate', title: this.$t('label.dr.power.state'), dataIndex: 'powerstate' },
        { key: 'hypervisortype', title: this.$t('label.hypervisor'), dataIndex: 'hypervisortype' },
        { key: 'activeside', title: this.$t('label.ftctl.active.side'), dataIndex: 'activeside' },
        { key: 'created', title: this.$t('label.created'), dataIndex: 'created' }
      ]
    }
  },
  computed: {
    hasCycleCommitState () {
      return Boolean(this.plan.datacommitstate || this.plan.cycleretrymode || this.plan.datacopied !== undefined)
    },
    hasSourceSnapshotState () {
      return Boolean(this.plan.runtimesourcesnapshotlifecyclestate || this.plan.runtimesourcesnapshotlastref)
    },
    cycleDataCopied () {
      return this.plan.datacopied === true
    },
    cycleMetadataCommitted () {
      return this.plan.metadatacommitted === true
    },
    cycleTargetDurable () {
      return this.plan.targetdurable === true
    }
  },
  methods: {
    formatEpoch (value) {
      const numeric = Number(value)
      if (!Number.isFinite(numeric) || numeric <= 0) return '-'
      return new Date(numeric).toLocaleString()
    }
  }
}
</script>

<style lang="less">
.cross-dr-protection-info {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.cross-dr-protection-info__cache {
  color: var(--cross-dr-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
}

.cross-dr-protection-info__section {
  min-width: 0;
  padding-top: 4px;
  border-top: 1px solid var(--cross-dr-border, #e8e8e8);
}

.cross-dr-protection-info__section h3 {
  margin: 10px 0 14px;
  color: var(--cross-dr-text, rgba(0, 0, 0, 0.85));
  font-size: 14px;
  font-weight: 600;
}

body.dark-mode .cross-dr-protection-info {
  --cross-dr-border: rgba(255, 255, 255, 0.12);
  --cross-dr-text: rgba(255, 255, 255, 0.86);
  --cross-dr-text-secondary: rgba(255, 255, 255, 0.58);
}
</style>
