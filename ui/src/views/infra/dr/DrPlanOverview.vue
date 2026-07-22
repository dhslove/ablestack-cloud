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
    <dr-resource-details-tab
      v-if="showDetails"
      :resource="plan"
      :fields="detailFields" />

    <div v-if="showProtectionSummary" class="cross-dr-overview__kpis">
      <div class="cross-dr-kpi">
        <div class="cross-dr-kpi__label">{{ $t('label.state') }}</div>
        <div class="cross-dr-kpi__status"><dr-status-pill :status="effectiveState" /></div>
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
      v-if="showProtectionSummary && (visibleErrorCode || visibleErrorMessage)"
      type="warning"
      show-icon
      class="cross-dr-risk">
      <template #message>
        <div>{{ visibleErrorCode || $t('label.error') }}</div>
        <div class="cross-dr-risk__body">{{ visibleErrorMessage || '-' }}</div>
      </template>
    </a-alert>

    <dr-run-progress v-if="showProtectionSummary && isActiveRun(currentRun)" :run="currentRun" />
  </div>
</template>

<script>
import DrResourceDetailsTab from '@/components/dr/DrResourceDetailsTab.vue'
import DrRpoKpi from '@/components/dr/DrRpoKpi.vue'
import DrRunProgress from '@/components/dr/DrRunProgress.vue'
import DrStatusPill from '@/components/dr/DrStatusPill.vue'
import { isActiveDrRun, resolveDrPlanState, resolveDrReplicationResumeState } from '@/utils/dr/planState'
import { mixinDevice } from '@/utils/mixin.js'

export default {
  name: 'DrPlanOverview',
  components: {
    DrResourceDetailsTab,
    DrRpoKpi,
    DrRunProgress,
    DrStatusPill
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
    },
    showDetails: {
      type: Boolean,
      default: true
    },
    showProtectionSummary: {
      type: Boolean,
      default: true
    }
  },
  computed: {
    detailFields () {
      const sourceVm = this.plan.sourcevmid || this.plan.sourceexternalref
      return [
        { key: 'id', label: this.$t('label.id'), value: this.plan.id },
        { key: 'name', label: this.$t('label.name'), value: this.plan.name },
        { key: 'description', label: this.$t('label.description'), value: this.plan.description },
        {
          key: 'direction',
          label: this.$t('label.dr.direction'),
          value: this.plan.direction ? this.$t(this.directionLabel(this.plan.direction)) : '-'
        },
        { key: 'activeSide', label: this.$t('label.dr.active.side'), value: this.plan.activeside },
        {
          key: 'sourceVm',
          label: this.$t('label.dr.source.vm'),
          value: sourceVm,
          route: this.plan.sourcevmid ? { path: '/vm/' + this.plan.sourcevmid } : null
        },
        { key: 'sourceWorkerHost', label: this.$t('label.dr.source.worker.host'), value: this.plan.sourceworkerhostid },
        { key: 'targetWorkerHost', label: this.$t('label.dr.target.worker.host'), value: this.plan.targetworkerhostid },
        { key: 'coordinatorWorkerHost', label: this.$t('label.dr.coordinator.worker.host'), value: this.plan.coordinatorworkerhostid },
        { key: 'targetMaterializationState', label: this.$t('label.dr.target.materialization.state'), value: this.plan.targetmaterializationstate },
        { key: 'targetMaterializationMessage', label: this.$t('label.dr.target.materialization.message'), value: this.plan.targetmaterializationmessage },
        { key: 'targetMaterialized', label: this.$t('label.dr.target.materialized'), value: this.booleanLabel(this.plan.targetmaterialized) },
        { key: 'targetVmPresent', label: this.$t('label.dr.target.vm.present'), value: this.booleanLabel(this.plan.targetvmpresent) },
        { key: 'restorePointPresent', label: this.$t('label.dr.restore.point.present'), value: this.booleanLabel(this.plan.restorepointpresent) },
        { key: 'sourceDiskMapPath', label: this.$t('label.dr.source.disk.map'), value: this.plan.sourcediskmappath || this.currentRun.sourcediskmappath },
        { key: 'targetDiskMapPath', label: this.$t('label.dr.target.disk.map'), value: this.plan.targetdiskmappath || this.currentRun.targetdiskmappath },
        { key: 'targetDiskInvalidCount', label: this.$t('label.dr.target.disk.invalid.count'), value: this.firstDefined(this.plan.targetdiskinvalidcount, this.currentRun.targetdiskinvalidcount) },
        { key: 'runtimeCbtEnabled', label: this.$t('label.dr.cbt.status'), value: this.booleanLabel(this.firstDefined(this.plan.runtimecbtenabled, this.currentRun.runtimecbtenabled)) },
        { key: 'runtimeCbtDiskId', label: this.$t('label.dr.cbt.disk.id'), value: this.plan.runtimecbtdiskid || this.currentRun.runtimecbtdiskid },
        { key: 'runtimeCbtMessage', label: this.$t('label.dr.cbt.message'), value: this.plan.runtimecbtmessage || this.currentRun.runtimecbtmessage },
        { key: 'runtimeSourceOpenReady', label: this.$t('label.dr.source.open.status'), value: this.booleanLabel(this.firstDefined(this.plan.runtimesourceopenready, this.currentRun.runtimesourceopenready)) },
        { key: 'runtimeSourceOpenError', label: this.$t('label.dr.source.open.error'), value: this.plan.runtimesourceopenmessage || this.currentRun.runtimesourceopenmessage || this.plan.runtimesourceopenerrorcode || this.currentRun.runtimesourceopenerrorcode },
        { key: 'runtimeSourceSnapshotReady', label: this.$t('label.dr.source.snapshot.status'), value: this.booleanLabel(this.firstDefined(this.plan.runtimesourcesnapshotready, this.currentRun.runtimesourcesnapshotready)) },
        { key: 'runtimeSourceSnapshotName', label: this.$t('label.dr.source.snapshot.name'), value: this.plan.runtimesourcesnapshotname || this.currentRun.runtimesourcesnapshotname },
        { key: 'runtimeSourceSnapshotError', label: this.$t('label.dr.source.snapshot.error'), value: this.plan.runtimesourcesnapshotmessage || this.currentRun.runtimesourcesnapshotmessage || this.plan.runtimesourcesnapshoterrorcode || this.currentRun.runtimesourcesnapshoterrorcode },
        { key: 'readinessReason', label: this.$t('label.dr.readiness.reason'), value: this.plan.readinessmessage || this.plan.readinessreasoncode },
        { key: 'freshnessState', label: this.$t('label.dr.freshness.state'), value: this.plan.freshnessstate },
        { key: 'currentCycle', label: this.$t('label.dr.current.cycle'), value: this.currentCycleLabel },
        { key: 'baselineState', label: this.$t('label.dr.baseline.state'), value: this.plan.baselinestate },
        { key: 'schedulerState', label: this.$t('label.dr.scheduler.state'), value: this.plan.schedulerstate },
        { key: 'schedulerHealth', label: this.$t('label.dr.scheduler.health'), value: this.plan.schedulerhealth },
        { key: 'replicationActivity', label: this.$t('label.dr.replication.activity'), value: this.plan.replicationactivity },
        { key: 'replicationResumeState', label: this.$t('label.dr.replication.resume.state'), value: this.replicationResumeState },
        { key: 'workerHeartbeatAt', label: this.$t('label.dr.scheduler.heartbeat'), value: this.plan.workerheartbeatat },
        { key: 'lastSourceCheckpoint', label: this.$t('label.dr.last.source.checkpoint'), value: this.plan.lastsourcecheckpointat },
        { key: 'lastTargetDurable', label: this.$t('label.dr.last.target.durable'), value: this.plan.lasttargetdurableat },
        { key: 'created', label: this.$t('label.created'), value: this.plan.created }
      ]
    },
    effectiveState () {
      return resolveDrPlanState(this.plan, this.currentRun)
    },
    replicationResumeState () {
      return resolveDrReplicationResumeState(this.plan)
    },
    currentCycleLabel () {
      const values = [this.plan.currentcyclesequence, this.plan.currentcyclemode, this.plan.currentcyclestate]
        .filter(value => value !== undefined && value !== null && String(value).length > 0)
      return values.length ? values.join(' / ') : '-'
    },
    currentRunFailed () {
      return String(this.currentRun.state || '').toUpperCase() === 'FAILED'
    },
    visibleErrorCode () {
      return this.plan.runtimeerrorcode || this.currentRun.runtimeerrorcode || this.plan.lasterrorcode || (this.currentRunFailed ? this.currentRun.errorcode : null)
    },
    visibleErrorMessage () {
      return this.plan.lasterrormessage || (this.currentRunFailed ? this.currentRun.errormessage : null) || this.translatedError(this.visibleErrorCode)
    }
  },
  methods: {
    isActiveRun (run) {
      return isActiveDrRun(run)
    },
    directionLabel (direction) {
      return {
        KVM_TO_KVM: 'label.dr.direction.kvm.to.kvm',
        KVM_TO_VMWARE: 'label.dr.direction.kvm.to.vmware',
        VMWARE_TO_VMWARE: 'label.dr.direction.vmware.to.vmware',
        VMWARE_TO_KVM: 'label.dr.direction.vmware.to.kvm'
      }[direction] || direction || '-'
    },
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
    },
    booleanLabel (value) {
      if (value === true || value === 'true') {
        return this.$t('label.yes')
      }
      if (value === false || value === 'false') {
        return this.$t('label.no')
      }
      return '-'
    },
    firstDefined (...values) {
      return values.find(value => value !== undefined && value !== null && value !== '') ?? '-'
    },
    translatedError (code) {
      if (!code) {
        return ''
      }
      const key = `message.dr.error.${String(code).toLowerCase().replace(/_/g, '.')}`
      return this.$te && this.$te(key) ? this.$t(key) : code
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
