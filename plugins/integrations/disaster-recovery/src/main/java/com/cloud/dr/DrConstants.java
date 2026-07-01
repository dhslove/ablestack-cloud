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
package com.cloud.dr;

public final class DrConstants {
    public static final String ENGINE_TYPE_FTCTL = "FTCTL";
    public static final String ENGINE_BINDING_TYPE_FTCTL = "FTCTL";
    public static final String ENGINE_TYPE_FTCTL_DR = "FTCTL_DR";
    public static final String ENGINE_BINDING_TYPE_FTCTL_DR = "FTCTL_DR";
    public static final String ENGINE_TYPE_VMWARE_PHASE1 = "VMWARE_PHASE1";
    public static final String ENGINE_BINDING_TYPE_VMWARE_PHASE1 = "VMWARE_PHASE1";
    public static final String ENGINE_TYPE_V2K = "V2K";
    public static final String ENGINE_BINDING_TYPE_V2K = "V2K";

    public static final String DIRECTION_KVM_TO_KVM = "KVM_TO_KVM";
    public static final String DIRECTION_KVM_TO_VMWARE = "KVM_TO_VMWARE";
    public static final String DIRECTION_VMWARE_TO_VMWARE = "VMWARE_TO_VMWARE";
    public static final String DIRECTION_VMWARE_TO_KVM = "VMWARE_TO_KVM";

    public static final String HYPERVISOR_TYPE_KVM = "KVM";
    public static final String HYPERVISOR_TYPE_VMWARE = "VMware";

    public static final String ADMIN_STATE_ENABLED = "ENABLED";
    public static final String ADMIN_STATE_DISABLED = "DISABLED";

    public static final String HEALTH_UNKNOWN = "UNKNOWN";

    public static final String PLAN_STATE_NEW = "NEW";
    public static final String PLAN_STATE_ENABLED = "ENABLED";
    public static final String PLAN_STATE_PHASE1_READY = "PHASE1_READY";
    public static final String PLAN_STATE_SYNCING = "SYNCING";
    public static final String PLAN_STATE_READY = "READY";
    public static final String PLAN_STATE_TESTING = "TESTING";
    public static final String PLAN_STATE_PAUSED = "PAUSED";
    public static final String PLAN_STATE_ERROR = "ERROR";
    public static final String PLAN_STATE_FAILED_OVER = "FAILED_OVER";

    public static final String REPLICA_STATE_NEW = "NEW";
    public static final String REPLICA_STATE_SKELETON_READY = "SKELETON_READY";
    public static final String REPLICA_STATE_PHASE1_READY = "PHASE1_READY";
    public static final String REPLICA_STATE_READY = "READY";
    public static final String REPLICA_STATE_ERROR = "ERROR";
    public static final String REPLICA_STATE_FAILED_OVER = "FAILED_OVER";
    public static final String REPLICA_POWER_STATE_POWERED_OFF = "POWERED_OFF";

    public static final String RUN_STATE_QUEUED = "QUEUED";
    public static final String RUN_STATE_DISPATCHING = "DISPATCHING";
    public static final String RUN_STATE_ACCEPTED = "ACCEPTED";
    public static final String RUN_STATE_RUNNING = "RUNNING";
    public static final String RUN_STATE_CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String RUN_STATE_SUCCEEDED = "SUCCEEDED";
    public static final String RUN_STATE_FAILED = "FAILED";
    public static final String RUN_STATE_CANCELED = "CANCELED";

    public static final String RUN_TYPE_SYNC = "SYNC";
    public static final String RUN_TYPE_PAUSE_SYNC = "PAUSE_SYNC";
    public static final String RUN_TYPE_RESUME_SYNC = "RESUME_SYNC";
    public static final String RUN_TYPE_TEST_FAILOVER = "TEST_FAILOVER";
    public static final String RUN_TYPE_TEST_CLEANUP = "TEST_CLEANUP";
    public static final String RUN_TYPE_FAILOVER = "FAILOVER";
    public static final String RUN_TYPE_FENCE_CONFIRM = "FENCE_CONFIRM";
    public static final String RUN_TYPE_FAILBACK = "FAILBACK";
    public static final String RUN_TYPE_REPROTECT = "REPROTECT";
    public static final String RUN_TYPE_ADOPT = "ADOPT";
    public static final String RUN_TYPE_RELEASE = "RELEASE";

    public static final String STEP_STATE_QUEUED = "QUEUED";
    public static final String STEP_STATE_RUNNING = "RUNNING";
    public static final String STEP_STATE_SUCCEEDED = "SUCCEEDED";
    public static final String STEP_STATE_FAILED = "FAILED";
    public static final String STEP_STATE_CANCELED = "CANCELED";

    public static final String EVENT_SEVERITY_INFO = "INFO";
    public static final String EVENT_SEVERITY_WARN = "WARN";
    public static final String EVENT_SEVERITY_ERROR = "ERROR";
    public static final String EVENT_SOURCE_CLOUD = "CLOUD";
    public static final String EVENT_SOURCE_AGENT = "AGENT";
    public static final String EVENT_SOURCE_FTCTL = "FTCTL";
    public static final String EVENT_SOURCE_FTCTL_DR = "FTCTL_DR";
    public static final String EVENT_SOURCE_V2K = "V2K";

    public static final String EVENT_RUN_CREATED = "RUN_CREATED";
    public static final String EVENT_RUN_QUEUED = "RUN_QUEUED";
    public static final String EVENT_RUN_STARTED = "RUN_STARTED";
    public static final String EVENT_RUN_ACCEPTED = "RUN_ACCEPTED";
    public static final String EVENT_RUN_SUCCEEDED = "RUN_SUCCEEDED";
    public static final String EVENT_RUN_FAILED = "RUN_FAILED";
    public static final String EVENT_RUN_CANCELED = "RUN_CANCELED";
    public static final String EVENT_PROTECTION_PREPARED = "PROTECTION_PREPARED";
    public static final String EVENT_PROJECTION_REFRESH = "PROJECTION_REFRESH";

    public static final String ERROR_PLAN_NOT_FOUND = "DR_PLAN_NOT_FOUND";
    public static final String ERROR_SITE_NOT_FOUND = "DR_SITE_NOT_FOUND";
    public static final String ERROR_DUPLICATE_SITE = "DR_DUPLICATE_SITE";
    public static final String ERROR_DUPLICATE_PLAN = "DR_DUPLICATE_PLAN";
    public static final String ERROR_ACTIVE_RUN_EXISTS = "DR_ACTIVE_RUN_EXISTS";
    public static final String ERROR_RUN_NOT_FOUND = "DR_RUN_NOT_FOUND";
    public static final String ERROR_ENGINE_UNAVAILABLE = "DR_ENGINE_UNAVAILABLE";
    public static final String ERROR_FTCTL_PROTECTION_NOT_FOUND = "DR_FTCTL_PROTECTION_NOT_FOUND";
    public static final String ERROR_ACTION_UNSUPPORTED = "DR_ACTION_UNSUPPORTED";
    public static final String ERROR_ENGINE_BUSY = "DR_ENGINE_BUSY";
    public static final String ERROR_ENGINE_ACTION_FAILED = "DR_ENGINE_ACTION_FAILED";
    public static final String ERROR_ENGINE_UNSUPPORTED = "DR_ENGINE_UNSUPPORTED";
    public static final String ERROR_TARGET_UNAVAILABLE = "DR_TARGET_UNAVAILABLE";
    public static final String ERROR_TARGET_MAPPING_INVALID = "DR_TARGET_MAPPING_INVALID";
    public static final String ERROR_WORKER_BINDING_INVALID = "DR_WORKER_BINDING_INVALID";
    public static final String ERROR_TARGET_OWNERSHIP_CONFLICT = "DR_TARGET_OWNERSHIP_CONFLICT";
    public static final String ERROR_TARGET_NOT_READY = "DR_TARGET_NOT_READY";
    public static final String ERROR_CREDENTIAL_INVALID = "DR_CREDENTIAL_INVALID";
    public static final String ERROR_V2K_TASK_NOT_FOUND = "DR_V2K_TASK_NOT_FOUND";
    public static final String ERROR_V2K_PHASE1_REQUIRED = "DR_V2K_PHASE1_REQUIRED";
    public static final String ERROR_V2K_PHASE2_REQUIRED = "DR_V2K_PHASE2_REQUIRED";

    private DrConstants() {
    }
}
