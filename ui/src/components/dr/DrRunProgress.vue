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
  <div class="cross-dr-run-progress">
    <div class="cross-dr-run-progress__head">
      <div>
        <div class="cross-dr-run-progress__title">{{ titleText }}</div>
        <div class="cross-dr-run-progress__meta">
          <span
            v-for="item in metaFields"
            :key="item.label">
            {{ item.label }}: {{ item.value }}
          </span>
        </div>
      </div>
      <dr-status-pill :status="effectiveRunState" />
    </div>

    <a-progress
      v-if="hasProgress"
      :percent="progress"
      :status="progressStatus"
      size="small" />
    <div v-else class="cross-dr-run-progress__unknown">
      {{ $t('message.dr.progress.waiting') }}
    </div>

    <div v-if="retryNotice" class="cross-dr-run-progress__notice">
      {{ retryNotice }}
    </div>

    <div v-if="cbtNotice" class="cross-dr-run-progress__notice cross-dr-run-progress__notice--info">
      {{ cbtNotice }}
    </div>

    <div v-if="normalizedSteps.length" class="cross-dr-run-progress__steps">
      <div
        v-for="step in normalizedSteps"
        :key="step.id || step.stepname || step.steporder"
        class="cross-dr-run-step">
        <div class="cross-dr-run-step__main">
          <span class="cross-dr-run-step__name">{{ step.stepname || '-' }}</span>
          <dr-status-pill :status="step.state" />
        </div>
        <div class="cross-dr-run-step__meta">
          <span v-if="step.progress !== undefined && step.progress !== null">{{ step.progress }}%</span>
          <span v-if="step.errormessage"> | {{ step.errormessage }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import DrStatusPill from '@/components/dr/DrStatusPill.vue'

export default {
  name: 'DrRunProgress',
  components: {
    DrStatusPill
  },
  props: {
    run: {
      type: Object,
      default: () => ({})
    },
    steps: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    titleText () {
      const runType = this.run.runtype || this.run.runType || this.$t('label.dr.run')
      const runId = this.run.id ? ` #${this.run.id}` : ''
      return `${runType}${runId}`
    },
    metaFields () {
      return [
        { label: this.$t('label.dr.current.step'), value: this.run.runtimestep || this.run.currentstep },
        { label: this.$t('label.dr.runtime.state'), value: this.run.runtimestate },
        { label: this.$t('label.dr.test.session.state'), value: this.run.testsessionstate },
        { label: this.$t('label.dr.worker.state'), value: this.run.workerstate },
        { label: this.$t('label.dr.external.job'), value: this.run.externaljobref },
        { label: this.$t('label.dr.retry'), value: this.retryMeta },
        { label: this.$t('label.error.code'), value: this.errorText }
      ].filter(item => item.value)
    },
    retryMeta () {
      if (!this.run.retryable && String(this.run.state || '').toUpperCase() !== 'RETRYING') {
        return ''
      }
      const fields = []
      if (this.run.retrycount !== undefined && this.run.retrycount !== null) {
        fields.push(`#${this.run.retrycount}`)
      }
      if (this.run.retryafterseconds !== undefined && this.run.retryafterseconds !== null) {
        fields.push(`${this.run.retryafterseconds}s`)
      }
      if (this.run.nextretryat) {
        fields.push(this.run.nextretryat)
      }
      return fields.join(' / ') || 'scheduled'
    },
    retryNotice () {
      if (!this.run.retryable && String(this.run.state || '').toUpperCase() !== 'RETRYING') {
        return ''
      }
      const reason = this.run.errormessage || this.errorText || 'FTCTL engine is busy'
      const meta = this.retryMeta ? ` (${this.retryMeta})` : ''
      return this.$t('message.dr.retry.scheduled', { reason, meta })
    },
    cbtNotice () {
      const fields = []
      if (this.run.runtimecbtenabled === true || this.run.runtimecbtenabled === 'true') {
        fields.push(this.$t('label.dr.cbt.enabled'))
      } else if (this.run.runtimecbtenabled === false || this.run.runtimecbtenabled === 'false') {
        fields.push(this.$t('label.dr.cbt.disabled'))
      }
      if (this.run.runtimecbtdiskid) {
        fields.push(`${this.$t('label.dr.cbt.disk.id')}: ${this.run.runtimecbtdiskid}`)
      }
      if (this.run.runtimecbtmessage) {
        fields.push(this.run.runtimecbtmessage)
      }
      if (this.run.runtimecbtgovcbin) {
        fields.push(`${this.$t('label.dr.govc.binary')}: ${this.run.runtimecbtgovcbin}`)
      }
      return fields.length ? `${this.$t('label.dr.cbt.status')}: ${fields.join(' / ')}` : ''
    },
    errorText () {
      const runFailed = String(this.run.state || '').toUpperCase() === 'FAILED'
      const code = this.run.runtimeerrorcode || (runFailed ? this.run.errorcode : null)
      if (!code) {
        return ''
      }
      const key = `message.dr.error.${String(code).toLowerCase().replace(/_/g, '.')}`
      return this.$te && this.$te(key) ? this.$t(key) : code
    },
    progress () {
      const value = Number(this.run.progresspercent)
      if (!Number.isFinite(value)) {
        return this.stateProgress
      }
      return Math.max(0, Math.min(100, Math.round(value)))
    },
    stateProgress () {
      const state = String(this.run.state || '').toUpperCase()
      if (state === 'QUEUED') {
        return 5
      }
      if (state === 'PREPARING') {
        return 10
      }
      if (state === 'DISPATCHING') {
        return 15
      }
      if (state === 'RETRYING') {
        return 25
      }
      if (state === 'ACCEPTED') {
        return 35
      }
      if (state === 'RUNNING' || state === 'CANCEL_REQUESTED') {
        return 60
      }
      if (['SUCCEEDED', 'FAILED', 'CANCELED'].includes(state)) {
        return 100
      }
      return 0
    },
    hasProgress () {
      return (this.run.progresspercent !== undefined && this.run.progresspercent !== null) || this.stateProgress > 0
    },
    progressStatus () {
      const state = String(this.effectiveRunState || '').toUpperCase()
      if (state === 'FAILED') {
        return 'exception'
      }
      if (state === 'SUCCEEDED') {
        return 'success'
      }
      if (state === 'CANCELED') {
        return 'normal'
      }
      return 'active'
    },
    normalizedSteps () {
      return this.steps.length ? this.steps : (this.run.steps || [])
    },
    effectiveRunState () {
      const runtime = String(this.run.runtimestate || '').toUpperCase()
      const worker = String(this.run.workerstate || '').toUpperCase()
      if (['ERROR', 'FAILED'].includes(runtime) || worker === 'FAILED' || this.run.runtimeerrorcode) {
        return 'FAILED'
      }
      return this.run.state || runtime || 'UNKNOWN'
    }
  }
}
</script>

<style lang="less">
.cross-dr-run-progress {
  padding: 12px;
  border: 1px solid var(--cross-dr-border, #e8e8e8);
  border-radius: 6px;
  background: var(--cross-dr-surface, #ffffff);
}

.cross-dr-run-progress__head,
.cross-dr-run-step__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cross-dr-run-progress__title {
  color: var(--cross-dr-text, rgba(0, 0, 0, 0.85));
  font-weight: 600;
  line-height: 22px;
}

.cross-dr-run-progress__meta,
.cross-dr-run-step__meta,
.cross-dr-run-progress__unknown,
.cross-dr-run-progress__notice {
  color: var(--cross-dr-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 12px;
  line-height: 18px;
}

.cross-dr-run-progress__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.cross-dr-run-progress__unknown {
  margin-top: 10px;
}

.cross-dr-run-progress__notice {
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--cross-dr-warning-border, #ffe58f);
  border-radius: 6px;
  background: var(--cross-dr-warning-bg, #fffbe6);
  color: var(--cross-dr-warning-text, #874d00);
}

.cross-dr-run-progress__notice--info {
  border-color: var(--cross-dr-info-border, #91d5ff);
  background: var(--cross-dr-info-bg, #e6f7ff);
  color: var(--cross-dr-info-text, #0050b3);
}

.cross-dr-run-progress__steps {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.cross-dr-run-step {
  padding: 8px 10px;
  border: 1px solid var(--cross-dr-border, #e8e8e8);
  border-radius: 6px;
  background: var(--cross-dr-surface-muted, #fafafa);
}

.cross-dr-run-step__name {
  color: var(--cross-dr-text, rgba(0, 0, 0, 0.85));
  font-weight: 600;
}

body.dark-mode .cross-dr-run-progress,
body.dark-mode .cross-dr-run-step {
  --cross-dr-border: rgba(255, 255, 255, 0.12);
  --cross-dr-surface: rgba(255, 255, 255, 0.04);
  --cross-dr-surface-muted: rgba(255, 255, 255, 0.06);
  --cross-dr-text: rgba(255, 255, 255, 0.86);
  --cross-dr-text-secondary: rgba(255, 255, 255, 0.58);
  --cross-dr-warning-bg: rgba(250, 173, 20, 0.12);
  --cross-dr-warning-border: rgba(250, 173, 20, 0.38);
  --cross-dr-warning-text: #ffe58f;
}
</style>
