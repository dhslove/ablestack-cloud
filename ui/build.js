#!/usr/bin/env node

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

const fs = require('fs')
const path = require('path')

const CONFIG_PATH = path.resolve(__dirname, './public/config.json')
const VERSION_PATH = '/mnt/version.txt'
const TMP_PATH = CONFIG_PATH + '.tmp'

function readJSON (file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function writeJSONAtomic (file, obj) {
  const text = JSON.stringify(obj, null, 2) + '\n'
  fs.writeFileSync(TMP_PATH, text, 'utf8')
  fs.renameSync(TMP_PATH, file)
}

function getBuildVersion () {
  try {
    // 개행/공백 제거해서 그대로 사용
    const v = fs.readFileSync(VERSION_PATH, 'utf8').trim()
    if (v) return v
  } catch (_) {}
  // fallback: v4.5.0-YYYYMMDD
  const base = 'v4.5.0'
  const d = new Date()
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${base}-${yyyy}${mm}${dd}`
}

const data = readJSON(CONFIG_PATH)
data.buildVersion = getBuildVersion()
writeJSONAtomic(CONFIG_PATH, data)
console.log('[OK] buildVersion:', data.buildVersion)
