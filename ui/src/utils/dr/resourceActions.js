// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import { normalizeActionEligibility } from '@/api/dr'
import { hasDrSourceAuthority } from '@/utils/dr/planState'

const runtimePlanActions = [
  {
    key: 'sync',
    api: 'startDrSync',
    command: 'startDrSync',
    icon: 'sync-outlined',
    label: 'label.dr.action.sync.now'
  },
  {
    key: 'recoversync',
    api: 'recoverDrSync',
    command: 'recoverDrSync',
    icon: 'reload-outlined',
    label: 'label.dr.action.recover.sync'
  },
  {
    key: 'pausesync',
    api: 'pauseDrSync',
    command: 'pauseDrSync',
    icon: 'pause-circle-outlined',
    label: 'label.dr.action.pause.sync'
  },
  {
    key: 'resumesync',
    api: 'resumeDrSync',
    command: 'resumeDrSync',
    icon: 'play-circle-outlined',
    label: 'label.dr.action.resume.sync'
  },
  {
    key: 'testfailover',
    api: 'startDrTestFailover',
    command: 'startDrTestFailover',
    icon: 'experiment-outlined',
    label: 'label.dr.action.test.failover'
  },
  {
    key: 'stoptestfailover',
    api: 'stopDrTestFailover',
    command: 'stopDrTestFailover',
    icon: 'stop-outlined',
    label: 'label.dr.action.test.cleanup',
    danger: true
  },
  {
    key: 'failover',
    api: 'startDrFailover',
    command: 'startDrFailover',
    icon: 'thunderbolt-outlined',
    label: 'label.dr.action.failover',
    danger: true
  },
  {
    key: 'confirmfenceclear',
    api: 'confirmDrFenceClear',
    command: 'confirmDrFenceClear',
    icon: 'safety-outlined',
    label: 'label.dr.action.fence.clear',
    danger: true
  },
  {
    key: 'failback',
    api: 'startDrFailback',
    command: 'startDrFailback',
    icon: 'undo-outlined',
    label: 'label.dr.action.failback',
    danger: true
  },
  {
    key: 'reprotect',
    api: 'startDrReprotect',
    command: 'startDrReprotect',
    icon: 'retweet-outlined',
    label: 'label.dr.action.reprotect'
  },
  {
    key: 'adoptreplica',
    api: 'adoptDrReplica',
    command: 'adoptDrReplica',
    icon: 'safety-certificate-outlined',
    label: 'label.dr.action.adopt.replica',
    danger: true
  },
  {
    key: 'releaseprotection',
    api: 'releaseDrProtection',
    command: 'releaseDrProtection',
    icon: 'delete-outlined',
    label: 'label.dr.action.release.protection',
    danger: true
  },
  {
    key: 'cancelrun',
    api: 'cancelDrRun',
    command: 'cancelDrRun',
    icon: 'close-circle-outlined',
    label: 'label.dr.action.cancel.run',
    danger: true
  }
]

function eligibility (resource) {
  return normalizeActionEligibility(resource?.actioneligibility || resource?.actionEligibility || {})
}

function hasEligibilityEntry (resource, key) {
  const map = eligibility(resource)
  return Object.keys(map).length === 0 || Object.prototype.hasOwnProperty.call(map, key)
}

function isEligible (resource, key) {
  const map = eligibility(resource)
  return Object.keys(map).length === 0 || map[key] === true
}

function isActiveRun (run) {
  return ['QUEUED', 'DISPATCHING', 'ACCEPTED', 'RUNNING', 'CANCEL_REQUESTED'].includes(String(run?.state || '').toUpperCase())
}

function isFailedRun (run) {
  return String(run?.state || '').toUpperCase() === 'FAILED'
}

function hasRuntimeFailure (resource) {
  const runtime = String(resource?.runtimestate || resource?.runtimeState || resource?.lastrun?.runtimestate || '').toUpperCase()
  const worker = String(resource?.lastrun?.workerstate || resource?.lastrun?.workerState || '').toUpperCase()
  const runtimeError = resource?.runtimeerrorcode || resource?.runtimeErrorCode || resource?.lastrun?.runtimeerrorcode
  const runError = isFailedRun(resource?.lastrun) ? resource?.lastrun?.errorcode : null
  return ['ERROR', 'FAILED'].includes(runtime) || worker === 'FAILED' || !!(runtimeError || runError)
}

function boolValue (value) {
  if (value === true || value === 'true') return true
  if (value === false || value === 'false') return false
  return undefined
}

function hasTargetReadyEvidence (resource) {
  const state = String(resource?.effectivestate || resource?.effectiveState || resource?.state || '').toUpperCase()
  if (state !== 'READY' || hasRuntimeFailure(resource)) {
    return false
  }
  const targetVmPresent = boolValue(resource?.targetvmpresent ?? resource?.targetVmPresent)
  const restorePointPresent = boolValue(resource?.restorepointpresent ?? resource?.restorePointPresent)
  const targetMaterialized = boolValue(resource?.targetmaterialized ?? resource?.targetMaterialized)
  if (targetVmPresent === false || restorePointPresent === false || targetMaterialized === false) {
    return false
  }
  return targetVmPresent === true && restorePointPresent === true
}

function commonMenuAction (action) {
  return Object.assign({
    dataView: true,
    listView: true
  }, action)
}

export function buildDrSiteActions () {
  return [
    commonMenuAction({
      api: 'checkDrSite',
      icon: 'api-outlined',
      label: 'label.dr.site.check'
    }),
    commonMenuAction({
      api: 'updateDrSite',
      icon: 'edit-outlined',
      label: 'label.dr.site.edit'
    }),
    commonMenuAction({
      api: 'deleteDrSite',
      icon: 'delete-outlined',
      label: 'label.dr.site.delete',
      disabled: resource => Number(resource?.activeplancount || resource?.activePlanCount || 0) > 0
    })
  ]
}

export function buildDrPlanActions (currentRun = {}) {
  const runtimeActions = runtimePlanActions.map(action => commonMenuAction(Object.assign({}, action, {
    show: resource => hasEligibilityEntry(resource, action.key),
    disabled: resource => {
      if (action.key === 'cancelrun') {
        return !isActiveRun(currentRun) || !currentRun.id
      }
      if (['testfailover', 'failover'].includes(action.key) && !hasTargetReadyEvidence(resource)) {
        return true
      }
      if (['sync', 'recoversync', 'pausesync', 'resumesync', 'testfailover', 'failover'].includes(action.key) && !hasDrSourceAuthority(resource)) {
        return true
      }
      return !isEligible(resource, action.key)
    },
    currentRun
  })))

  return [
    commonMenuAction({
      api: 'updateDrPlan',
      icon: 'edit-outlined',
      label: 'label.dr.plan.edit',
      disabled: resource => !isEligible(resource, 'update')
    }),
    commonMenuAction({
      api: 'deleteDrPlan',
      icon: 'delete-outlined',
      label: 'label.dr.plan.delete',
      disabled: resource => !isEligible(resource, 'delete')
    }),
    ...runtimeActions
  ]
}
