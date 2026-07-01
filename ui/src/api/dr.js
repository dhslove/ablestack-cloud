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
  listDrPlans: ['listdrplansresponse', 'drplan'],
  listDrRuns: ['listdrrunsresponse', 'drrun'],
  listDrRunSteps: ['listdrrunstepsresponse', 'drrunstep'],
  listDrEvents: ['listdreventsresponse', 'drevent'],
  listDrReplicas: ['listdrreplicasresponse', 'drreplica'],
  listDrRestorePoints: ['listdrrestorepointsresponse', 'drrestorepoint']
}

const objectKeys = {
  getDrSite: ['getdrsiteresponse', 'drsite'],
  getDrPlan: ['getdrplanresponse', 'drplan'],
  getDrRun: ['getdrrunresponse', 'drrun'],
  createDrSite: ['createdrsiteresponse', 'drsite'],
  createDrPlan: ['createdrplanresponse', 'drplan'],
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

export function listDrSites (params = {}) {
  return getAPI('listDrSites', params).then(response => extractDrList(response, 'listDrSites'))
}

export function getDrSite (id) {
  return getAPI('getDrSite', { id }).then(response => extractDrObject(response, 'getDrSite'))
}

export function checkDrSite (id, persistStatus = false) {
  return postAPI('checkDrSite', { id, persiststatus: persistStatus }).then(response => extractDrObject(response, 'checkDrSite'))
}

export function createDrSite (params) {
  return postAPI('createDrSite', params).then(response => extractDrObject(response, 'createDrSite'))
}

export function listDrPlans (params = {}) {
  return getAPI('listDrPlans', params).then(response => extractDrList(response, 'listDrPlans'))
}

export function getDrPlan (id) {
  return getAPI('getDrPlan', { id }).then(response => extractDrObject(response, 'getDrPlan'))
}

export function createDrPlan (params) {
  return postAPI('createDrPlan', params).then(response => extractDrObject(response, 'createDrPlan'))
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
