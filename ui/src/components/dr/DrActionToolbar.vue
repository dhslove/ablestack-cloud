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
  <a-space
    :class="['cross-dr-action-toolbar', {
      'cross-dr-action-toolbar--compact': compact,
      'cross-dr-action-toolbar--dataview': dataView
    }]"
    wrap>
    <template v-for="action in visibleActions" :key="action.key">
      <a-tooltip :title="disabledReason(action)">
        <a-popconfirm
          v-if="action.danger && !action.modal"
          :title="$t(action.confirmMessage)"
          :ok-text="$t('label.yes')"
          :cancel-text="$t('label.no')"
          :disabled="isDisabled(action)"
          @confirm="run(action)">
          <a-button
            size="small"
            :type="dataView ? 'text' : 'default'"
            :shape="dataView ? null : 'circle'"
            :class="{ 'action-button-item': dataView, 'action-button-item--dataview': dataView }"
            :danger="action.danger"
            :disabled="isDisabled(action)"
            :loading="loadingAction === action.command">
            <template #icon><component :is="action.icon" /></template>
            <span v-if="!compact || dataView">{{ $t(action.label) }}</span>
          </a-button>
        </a-popconfirm>
        <a-button
          v-else
          size="small"
          :type="dataView ? 'text' : 'default'"
          :shape="dataView ? null : 'circle'"
          :class="{ 'action-button-item': dataView, 'action-button-item--dataview': dataView }"
          :danger="action.danger"
          :disabled="isDisabled(action)"
          :loading="loadingAction === action.command"
          @click="run(action)">
          <template #icon><component :is="action.icon" /></template>
          <span v-if="!compact || dataView">{{ $t(action.label) }}</span>
        </a-button>
      </a-tooltip>
    </template>
  </a-space>
</template>

<script>
import { normalizeActionEligibility } from '@/api/dr'

export default {
  name: 'DrActionToolbar',
  props: {
    plan: {
      type: Object,
      required: true
    },
    loadingAction: {
      type: String,
      default: ''
    },
    compact: {
      type: Boolean,
      default: false
    },
    dataView: {
      type: Boolean,
      default: false
    },
    currentRun: {
      type: Object,
      default: () => ({})
    }
  },
  emits: ['run-action'],
  data () {
    return {
      actions: [
        {
          key: 'sync',
          command: 'startDrSync',
          icon: 'SyncOutlined',
          label: 'label.dr.action.sync.now'
        },
        {
          key: 'pausesync',
          command: 'pauseDrSync',
          icon: 'PauseCircleOutlined',
          label: 'label.dr.action.pause.sync'
        },
        {
          key: 'resumesync',
          command: 'resumeDrSync',
          icon: 'PlayCircleOutlined',
          label: 'label.dr.action.resume.sync'
        },
        {
          key: 'testfailover',
          command: 'startDrTestFailover',
          icon: 'ExperimentOutlined',
          label: 'label.dr.action.test.failover'
        },
        {
          key: 'stoptestfailover',
          command: 'stopDrTestFailover',
          icon: 'StopOutlined',
          label: 'label.dr.action.test.cleanup',
          danger: true,
          modal: true
        },
        {
          key: 'failover',
          command: 'startDrFailover',
          icon: 'ThunderboltOutlined',
          label: 'label.dr.action.failover',
          danger: true,
          modal: true,
          confirmMessage: 'message.dr.confirm.failover'
        },
        {
          key: 'confirmfenceclear',
          command: 'confirmDrFenceClear',
          icon: 'SafetyOutlined',
          label: 'label.dr.action.fence.clear',
          danger: true,
          modal: true
        },
        {
          key: 'failback',
          command: 'startDrFailback',
          icon: 'UndoOutlined',
          label: 'label.dr.action.failback',
          danger: true,
          modal: true,
          confirmMessage: 'message.dr.confirm.failback'
        },
        {
          key: 'reprotect',
          command: 'startDrReprotect',
          icon: 'RetweetOutlined',
          label: 'label.dr.action.reprotect'
        },
        {
          key: 'adoptreplica',
          command: 'adoptDrReplica',
          icon: 'SafetyCertificateOutlined',
          label: 'label.dr.action.adopt.replica',
          danger: true,
          modal: true,
          confirmMessage: 'message.dr.confirm.adopt.replica'
        },
        {
          key: 'releaseprotection',
          command: 'releaseDrProtection',
          icon: 'DeleteOutlined',
          label: 'label.dr.action.release.protection',
          danger: true,
          modal: true,
          confirmMessage: 'message.dr.confirm.release.protection'
        },
        {
          key: 'cancelrun',
          command: 'cancelDrRun',
          icon: 'CloseCircleOutlined',
          label: 'label.dr.action.cancel.run',
          danger: true,
          modal: true
        }
      ]
    }
  },
  computed: {
    eligibility () {
      return normalizeActionEligibility(this.plan.actioneligibility || this.plan.actionEligibility || {})
    },
    visibleActions () {
      return this.actions.filter(action => this.isVisible(action))
    }
  },
  methods: {
    hasApi (command) {
      return command in this.$store.getters.apis
    },
    hasEligibilityMap () {
      return Object.keys(this.eligibility).length > 0
    },
    hasEligibilityEntry (action) {
      return Object.prototype.hasOwnProperty.call(this.eligibility, action.key)
    },
    isVisible (action) {
      return !this.hasEligibilityMap() || this.hasEligibilityEntry(action)
    },
    isEligible (action) {
      if (action.key === 'cancelrun') {
        return this.isActiveRun(this.currentRun) && !!this.currentRun.id
      }
      return this.eligibility[action.key] === true
    },
    isDisabled (action) {
      return !this.hasApi(action.command) || !this.isEligible(action)
    },
    disabledReason (action) {
      if (!this.hasApi(action.command)) {
        return this.$t('message.dr.action.api.unavailable')
      }
      if (['pausesync', 'resumesync', 'testfailover', 'stoptestfailover', 'failover', 'releaseprotection'].includes(action.key) &&
          this.plan.runtimecontrolready === false) {
        return this.$t('message.dr.action.control.not.ready')
      }
      if (!this.isEligible(action)) {
        return this.$t('message.dr.action.not.eligible')
      }
      return ''
    },
    run (action) {
      if (this.isDisabled(action)) {
        return
      }
      this.$emit('run-action', Object.assign({}, action, { currentRun: this.currentRun || {} }))
    },
    isActiveRun (run) {
      return ['QUEUED', 'PREPARING', 'DISPATCHING', 'ACCEPTED', 'RUNNING', 'RETRYING', 'CANCEL_REQUESTED'].includes(String(run?.state || '').toUpperCase())
    }
  }
}
</script>

<style lang="less">
.cross-dr-action-toolbar {
  max-width: 100%;
}

.cross-dr-action-toolbar--dataview {
  display: flex;
  flex-direction: column;
  width: max-content;
  min-width: 220px;
  max-width: 300px;
}

.cross-dr-action-toolbar--dataview .ant-space-item {
  width: 100%;
}

.cross-dr-action-toolbar--dataview .ant-btn {
  justify-content: flex-start;
  width: 100%;
  margin-left: 0 !important;
}

.cross-dr-action-toolbar--dataview .ant-btn span + span {
  margin-left: 8px;
}

.cross-dr-action-toolbar .ant-btn:not(.ant-btn-circle) {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
