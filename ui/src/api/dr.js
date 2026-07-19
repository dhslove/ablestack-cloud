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

import { getAPI, postAPI } from '@/api'

const listKeys = {
  listDrSites: ['listdrsitesresponse', 'drsite'],
  listDrSiteHealthChecks: ['listdrsitehealthchecksresponse', 'drsitehealthcheck'],
  listDrPlans: ['listdrplansresponse', 'drplan'],
  listDrRuns: ['listdrrunsresponse', 'drrun'],
  listDrRunSteps: ['listdrrunstepsresponse', 'drrunstep'],
  listDrEvents: ['listdreventsresponse', 'drevent'],
  listDrReplicas: ['listdrreplicasresponse', 'drreplica'],
  listDrRestorePoints: ['listdrrestorepointsresponse', 'drrestorepoint'],
  listDrSyncCheckpoints: ['listdrsynccheckpointsresponse', 'drrestorepoint']
}

const objectKeys = {
  getDrSite: ['getdrsiteresponse', 'drsite'],
  getDrPlan: ['getdrplanresponse', 'drplan'],
  getDrProtectionView: ['getdrprotectionviewresponse', 'drprotectionview'],
  getDrRun: ['getdrrunresponse', 'drrun'],
  createDrSite: ['createdrsiteresponse', 'drsite'],
  discoverDrSiteInventory: ['discoverdrsiteinventoryresponse', 'drsiteinventory'],
  discoverDrPlanInventory: ['discoverdrplaninventoryresponse', 'drplaninventory'],
  previewDrPlanSpec: ['previewdrplanspecresponse', 'drplanspecpreview'],
  createDrPlan: ['createdrplanresponse', 'drplan'],
  updateDrSite: ['updatedrsiteresponse', 'drsite'],
  updateDrPlan: ['updatedrplanresponse', 'drplan'],
  checkDrSite: ['checkdrsiteresponse', 'drsite'],
  startDrSync: ['startdrsyncresponse', 'drrun'],
  pauseDrSync: ['pausedrsyncresponse', 'drrun'],
  resumeDrSync: ['resumedrsyncresponse', 'drrun'],
  startDrTestFailover: ['startdrtestfailoverresponse', 'drrun'],
  stopDrTestFailover: ['stopdrtestfailoverresponse', 'drrun'],
  startDrFailover: ['startdrfailoverresponse', 'drrun'],
  confirmDrFenceClear: ['confirmdrfenceclearresponse', 'drrun'],
  startDrFailback: ['startdrfailbackresponse', 'drrun'],
  startDrReprotect: ['startdrreprotectresponse', 'drrun'],
  adoptDrReplica: ['adoptdrreplicaresponse', 'drrun'],
  releaseDrProtection: ['releasedrprotectionresponse', 'drrun'],
  cancelDrRun: ['canceldrrunresponse', 'drrun']
}

export function extractDrList (response, command) {
  const [responseKey, itemKey] = listKeys[command] || [command.toLowerCase() + 'response', '']
  const payload = response?.[responseKey] || {}
  return {
    items: payload?.[itemKey] || [],
    count: payload?.count || 0
  }
}

export function extractDrObject (response, command) {
  const [responseKey, itemKey] = objectKeys[command] || [command.toLowerCase() + 'response', '']
  const payload = response?.[responseKey] || {}
  return payload?.[itemKey] || payload || {}
}

export function extractJobId (response, command) {
  const responseKey = command.toLowerCase() + 'response'
  return response?.[responseKey]?.jobid || response?.jobid || ''
}

function sleep (ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function extractDrJobObject (jobResult, command) {
  const [responseKey, itemKey] = objectKeys[command] || [command.toLowerCase() + 'response', '']
  const payload = jobResult?.[responseKey] || jobResult || {}
  return payload?.[itemKey] || payload
}

function buildDrJobError (result, jobId, command) {
  const error = new Error(result.jobresult?.errortext || 'Async job failed')
  error.response = { data: { errorresponse: result.jobresult || {} } }
  error.jobid = jobId
  error.command = command
  return error
}

function waitForDrJobObject (jobId, command, options = {}) {
  const intervalMs = options.intervalMs || 1000
  const timeoutMs = options.timeoutMs || 120000
  const startedAt = Date.now()
  const poll = () => getAPI('queryAsyncJobResult', { jobId }).then(response => {
    const result = response?.queryasyncjobresultresponse || {}
    if (result.jobstatus === 1) {
      return extractDrJobObject(result.jobresult, command)
    }
    if (result.jobstatus === 2) {
      throw buildDrJobError(result, jobId, command)
    }
    if (Date.now() - startedAt >= timeoutMs) {
      const error = new Error('Async job timed out')
      error.jobid = jobId
      error.command = command
      error.retryable = true
      throw error
    }
    return sleep(intervalMs).then(poll)
  })
  return poll()
}

function postAndWaitForDrObject (command, params, options = {}) {
  return postAPI(command, params).then(response => {
    const jobId = extractJobId(response, command)
    return jobId
      ? waitForDrJobObject(jobId, command, options)
      : extractDrObject(response, command)
  })
}

export function listDrSites (params = {}) {
  return getAPI('listDrSites', params).then(response => extractDrList(response, 'listDrSites'))
}

export function listDrSiteHealthChecks (params = {}) {
  return getAPI('listDrSiteHealthChecks', params).then(response => extractDrList(response, 'listDrSiteHealthChecks'))
}

export function getDrSite (id) {
  return getAPI('getDrSite', { id }).then(response => extractDrObject(response, 'getDrSite'))
}

export function checkDrSite (id, persistStatus = false) {
  return postAndWaitForDrObject('checkDrSite', { id, persiststatus: persistStatus })
}

export function createDrSite (params) {
  return postAndWaitForDrObject('createDrSite', params)
}

export function discoverDrSiteInventory (params = {}) {
  return postAndWaitForDrObject('discoverDrSiteInventory', params)
}

export function discoverDrPlanInventory (params = {}) {
  return postAndWaitForDrObject('discoverDrPlanInventory', params)
}

export function previewDrPlanSpec (params = {}) {
  return postAPI('previewDrPlanSpec', params).then(response => extractDrObject(response, 'previewDrPlanSpec'))
}

export function updateDrSite (id, params) {
  return postAndWaitForDrObject('updateDrSite', Object.assign({ id }, params))
}

export function deleteDrSite (id) {
  return postAPI('deleteDrSite', { id }).then(response => ({
    jobid: extractJobId(response, 'deleteDrSite'),
    raw: response
  }))
}

export function listDrPlans (params = {}) {
  return getAPI('listDrPlans', params).then(response => extractDrList(response, 'listDrPlans'))
}

export function getDrPlan (id) {
  return getAPI('getDrPlan', { id }).then(response => extractDrObject(response, 'getDrPlan'))
}

export function getDrProtectionView (planId) {
  return getAPI('getDrProtectionView', { planid: planId }).then(response => extractDrObject(response, 'getDrProtectionView'))
}

export function refreshDrProtectionView (planId) {
  return postAPI('refreshDrProtectionView', { planid: planId }).then(response => ({
    jobid: extractJobId(response, 'refreshDrProtectionView'),
    raw: response
  }))
}

export function createDrPlan (params) {
  return postAndWaitForDrObject('createDrPlan', params)
}

export function updateDrPlan (id, params) {
  return postAndWaitForDrObject('updateDrPlan', Object.assign({ id }, params))
}

export function deleteDrPlan (id) {
  return postAPI('deleteDrPlan', { id }).then(response => ({
    jobid: extractJobId(response, 'deleteDrPlan'),
    raw: response
  }))
}

export function listDrRuns (params = {}) {
  return getAPI('listDrRuns', params).then(response => extractDrList(response, 'listDrRuns'))
}

export function getDrRun (id) {
  return getAPI('getDrRun', { id }).then(response => extractDrObject(response, 'getDrRun'))
}

export function listDrRunSteps (params = {}) {
  return getAPI('listDrRunSteps', params).then(response => extractDrList(response, 'listDrRunSteps'))
}

export function listDrEvents (params = {}) {
  return getAPI('listDrEvents', params).then(response => extractDrList(response, 'listDrEvents'))
}

export function listDrReplicas (params = {}) {
  return getAPI('listDrReplicas', params).then(response => extractDrList(response, 'listDrReplicas'))
}

export function listDrRestorePoints (params = {}) {
  return getAPI('listDrRestorePoints', params).then(response => extractDrList(response, 'listDrRestorePoints'))
}

export function listDrSyncCheckpoints (params = {}) {
  return getAPI('listDrSyncCheckpoints', params).then(response => extractDrList(response, 'listDrSyncCheckpoints'))
}

export function startDrAction (command, params) {
  return postAPI(command, params).then(response => extractDrObject(response, command))
}

export function normalizeActionEligibility (eligibility = {}) {
  const normalized = {}
  Object.keys(eligibility || {}).forEach(key => {
    normalized[String(key).toLowerCase()] = eligibility[key] === true || eligibility[key]?.enabled === true
  })
  return normalized
}
