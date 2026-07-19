-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from ablestack-allo to ablestack-bronto
--;

-- Ensure FTCTL protection tables exist for feature releases where the versioned CloudStack upgrade path has already run
CREATE TABLE IF NOT EXISTS `cloud`.`ftctl_protection` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `primary_vm_id` bigint unsigned NOT NULL COMMENT 'Primary VM managed by FTCTL protection',
    `secondary_vm_id` bigint unsigned NULL COMMENT 'Cloud-provisioned standby VM, when available',
    `secondary_vm_name` varchar(255) NULL COMMENT 'FTCTL standby VM name',
    `peer_host_id` bigint unsigned NULL COMMENT 'Peer KVM host used by FTCTL',
    `target_storage_pool_id` bigint unsigned NULL COMMENT 'Target primary storage pool for standby volumes',
    `mode` varchar(32) NOT NULL COMMENT 'FTCTL protection mode',
    `backend_mode` varchar(64) NULL COMMENT 'FTCTL backend mode',
    `provisioning_backend` varchar(64) NOT NULL DEFAULT 'libvirt-managed' COMMENT 'Protection resource provisioning owner',
    `fencing_policy` varchar(64) NULL COMMENT 'FTCTL fencing policy',
    `xcolo_port_allocation_mode` varchar(16) NULL COMMENT 'FTCTL XCOLO port allocation mode',
    `xcolo_port_slot` int NULL COMMENT 'FTCTL XCOLO automatic port allocation slot',
    `admin_state` varchar(64) NULL,
    `provisioning_state` varchar(64) NULL,
    `protection_state` varchar(64) NULL,
    `transport_state` varchar(64) NULL,
    `active_side` varchar(64) NULL,
    `fencing_state` varchar(64) NULL,
    `last_error` varchar(1024) NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ftctl_protection__uuid` (`uuid`),
    KEY `i_ftctl_protection__primary_vm_id` (`primary_vm_id`),
    KEY `i_ftctl_protection__secondary_vm_id` (`secondary_vm_id`),
    KEY `i_ftctl_protection__peer_host_id` (`peer_host_id`),
    KEY `i_ftctl_protection__target_storage_pool_id` (`target_storage_pool_id`),
    CONSTRAINT `fk_ftctl_protection__primary_vm_id` FOREIGN KEY (`primary_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ftctl_protection__secondary_vm_id` FOREIGN KEY (`secondary_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ftctl_protection__peer_host_id` FOREIGN KEY (`peer_host_id`) REFERENCES `host` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_ftctl_protection__target_storage_pool_id` FOREIGN KEY (`target_storage_pool_id`) REFERENCES `storage_pool` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.ftctl_protection', 'xcolo_port_allocation_mode', 'varchar(16) NULL COMMENT "FTCTL XCOLO port allocation mode" AFTER `fencing_policy`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.ftctl_protection', 'xcolo_port_slot', 'int NULL COMMENT "FTCTL XCOLO automatic port allocation slot" AFTER `xcolo_port_allocation_mode`');

CREATE TABLE IF NOT EXISTS `cloud`.`ftctl_protection_volume` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `protection_id` bigint unsigned NOT NULL COMMENT 'FTCTL protection relationship id',
    `primary_volume_id` bigint unsigned NOT NULL COMMENT 'Primary VM volume',
    `secondary_volume_id` bigint unsigned NULL COMMENT 'Cloud-provisioned standby volume, when available',
    `primary_disk_path` varchar(2048) NULL COMMENT 'Primary disk path used by FTCTL',
    `secondary_disk_path` varchar(2048) NULL COMMENT 'Secondary disk path used by FTCTL',
    `disk_label` varchar(255) NULL COMMENT 'Stable disk label for FTCTL disk map',
    `replication_state` varchar(64) NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    KEY `i_ftctl_protection_volume__protection_id` (`protection_id`),
    KEY `i_ftctl_protection_volume__primary_volume_id` (`primary_volume_id`),
    KEY `i_ftctl_protection_volume__secondary_volume_id` (`secondary_volume_id`),
    CONSTRAINT `fk_ftctl_protection_volume__protection_id` FOREIGN KEY (`protection_id`) REFERENCES `ftctl_protection` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ftctl_protection_volume__primary_volume_id` FOREIGN KEY (`primary_volume_id`) REFERENCES `volumes` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_ftctl_protection_volume__secondary_volume_id` FOREIGN KEY (`secondary_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_site` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `name` varchar(255) NOT NULL,
    `description` varchar(1024) NULL,
    `site_type` varchar(64) NOT NULL,
    `hypervisor_type` varchar(64) NOT NULL,
    `endpoint` varchar(2048) NULL,
    `credential_ref` varchar(255) NULL,
    `credential_id` bigint unsigned NULL,
    `zone_id` bigint unsigned NULL,
    `zone_external_id` varchar(255) NULL,
    `zone_name` varchar(255) NULL,
    `vmware_datacenter_id` bigint unsigned NULL,
    `vmware_datacenter_external_id` varchar(255) NULL,
    `vmware_datacenter_name` varchar(255) NULL,
    `state` varchar(64) NULL,
    `effective_mode` varchar(32) NULL,
    `incremental_verified` tinyint(1) NULL,
    `metrics_estimated` tinyint(1) NULL,
    `virtual_bytes` bigint unsigned NULL,
    `changed_bytes` bigint unsigned NULL,
    `source_read_bytes` bigint unsigned NULL,
    `target_written_bytes` bigint unsigned NULL,
    `transfer_payload_bytes` bigint unsigned NULL,
    `changed_extent_count` bigint unsigned NULL,
    `duration_ms` bigint unsigned NULL,
    `throughput_bps` bigint unsigned NULL,
    `baseline_generation` bigint unsigned NULL,
    `cycle_token` varchar(255) NULL,
    `health_state` varchar(64) NULL,
    `capabilities_json` text NULL,
    `last_checked` datetime NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_site__uuid` (`uuid`),
    KEY `i_dr_site__name` (`name`),
    KEY `i_dr_site__credential_id` (`credential_id`),
    KEY `i_dr_site__zone_id` (`zone_id`),
    CONSTRAINT `fk_dr_site__zone_id` FOREIGN KEY (`zone_id`) REFERENCES `data_center` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_site', 'zone_external_id', 'varchar(255) NULL AFTER `zone_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_site', 'zone_name', 'varchar(255) NULL AFTER `zone_external_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_site', 'vmware_datacenter_external_id', 'varchar(255) NULL AFTER `vmware_datacenter_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_site', 'vmware_datacenter_name', 'varchar(255) NULL AFTER `vmware_datacenter_external_id`');

CREATE TABLE IF NOT EXISTS `cloud`.`dr_site_credential` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `site_id` bigint unsigned NOT NULL,
    `credential_type` varchar(64) NOT NULL,
    `endpoint` varchar(2048) NULL,
    `principal` varchar(255) NULL,
    `secret_payload` text NULL,
    `tls_verify` tinyint(1) NOT NULL DEFAULT 1,
    `state` varchar(64) NULL,
    `last_validated` datetime NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_site_credential__uuid` (`uuid`),
    KEY `i_dr_site_credential__site_id` (`site_id`),
    KEY `i_dr_site_credential__state` (`state`),
    CONSTRAINT `fk_dr_site_credential__site_id` FOREIGN KEY (`site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_site_health_check` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `site_id` bigint unsigned NOT NULL,
    `site_uuid` varchar(40) NOT NULL,
    `site_name` varchar(255) NOT NULL,
    `site_type` varchar(64) NOT NULL,
    `hypervisor_type` varchar(64) NOT NULL,
    `endpoint` varchar(2048) NULL,
    `credential_id` bigint unsigned NULL,
    `credential_state` varchar(64) NULL,
    `trigger_type` varchar(64) NOT NULL,
    `health_state` varchar(64) NOT NULL,
    `reason_code` varchar(128) NULL,
    `message` text NULL,
    `latency_ms` bigint NULL,
    `checked_at` datetime NOT NULL,
    `management_server_id` bigint unsigned NULL,
    `job_id` varchar(255) NULL,
    `details_json` text NULL,
    `created` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_site_health_check__uuid` (`uuid`),
    KEY `i_dr_site_health_check__site_checked` (`site_id`, `checked_at`),
    KEY `i_dr_site_health_check__state_checked` (`health_state`, `checked_at`),
    KEY `i_dr_site_health_check__trigger_checked` (`trigger_type`, `checked_at`),
    CONSTRAINT `fk_dr_site_health_check__site_id` FOREIGN KEY (`site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_site_pair` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `name` varchar(255) NOT NULL,
    `source_site_id` bigint unsigned NOT NULL,
    `target_site_id` bigint unsigned NOT NULL,
    `direction` varchar(64) NOT NULL,
    `state` varchar(64) NULL,
    `legacy_dr_cluster_id` bigint unsigned NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_site_pair__uuid` (`uuid`),
    KEY `i_dr_site_pair__source_site_id` (`source_site_id`),
    KEY `i_dr_site_pair__target_site_id` (`target_site_id`),
    KEY `i_dr_site_pair__legacy_dr_cluster_id` (`legacy_dr_cluster_id`),
    CONSTRAINT `fk_dr_site_pair__source_site_id` FOREIGN KEY (`source_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_site_pair__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_plan` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `name` varchar(255) NOT NULL,
    `description` varchar(1024) NULL,
    `source_site_id` bigint unsigned NOT NULL,
    `target_site_id` bigint unsigned NOT NULL,
    `source_vm_id` bigint unsigned NULL,
    `source_external_ref` varchar(2048) NULL,
    `direction` varchar(64) NOT NULL,
    `engine_type` varchar(64) NULL,
    `engine_binding_type` varchar(64) NULL,
    `engine_binding_id` bigint unsigned NULL,
    `state` varchar(64) NULL,
    `admin_state` varchar(64) NULL,
    `active_side` varchar(16) NULL,
    `rpo_seconds` int NULL,
    `rto_seconds` int NULL,
    `schedule_json` text NULL,
    `policy_json` text NULL,
    `mapping_json` text NULL,
    `quiesce_policy_json` text NULL,
    `source_worker_host_id` bigint unsigned NULL,
    `target_worker_host_id` bigint unsigned NULL,
    `coordinator_worker_host_id` bigint unsigned NULL,
    `last_source_checkpoint_at` datetime NULL,
    `last_target_durable_at` datetime NULL,
    `target_ready_at` datetime NULL,
    `target_ready_rpo_seconds` int NULL,
    `last_run_id` bigint unsigned NULL,
    `last_error_code` varchar(128) NULL,
    `last_error_message` text NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_plan__uuid` (`uuid`),
    KEY `i_dr_plan__source_site_id` (`source_site_id`),
    KEY `i_dr_plan__target_site_id` (`target_site_id`),
    KEY `i_dr_plan__source_vm_id` (`source_vm_id`),
    KEY `i_dr_plan__source_site_external_ref_removed` (`source_site_id`, `source_external_ref`(255), `removed`),
    KEY `i_dr_plan__engine_binding` (`engine_binding_type`, `engine_binding_id`),
    KEY `i_dr_plan__state` (`state`),
    CONSTRAINT `fk_dr_plan__source_site_id` FOREIGN KEY (`source_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_plan__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_plan__source_vm_id` FOREIGN KEY (`source_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_dr_plan__source_site_external_ref_removed', 'cloud.dr_plan', '(`source_site_id`, `source_external_ref`(255), `removed`)');

CREATE TABLE IF NOT EXISTS `cloud`.`dr_restore_point` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
    `run_id` bigint unsigned NULL,
    `checkpoint_sequence` bigint unsigned NULL,
    `checkpoint_cycle_type` varchar(32) NULL,
    `checkpoint_ref_hash` binary(32) NULL,
    `source_snapshot_ref` varchar(2048) NULL,
    `source_created` datetime NULL,
    `target_ready_at` datetime NULL,
    `source_rpo_seconds` int NULL,
    `target_ready_rpo_seconds` int NULL,
    `consistency_level` varchar(64) NULL,
    `restore_point_type` varchar(64) NULL,
    `state` varchar(64) NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_restore_point__uuid` (`uuid`),
    UNIQUE KEY `uk_dr_restore_point__plan_checkpoint_hash` (`plan_id`, `checkpoint_ref_hash`),
    KEY `i_dr_restore_point__plan_id` (`plan_id`),
    KEY `i_dr_restore_point__target_ready_at` (`target_ready_at`),
    KEY `i_dr_restore_point__plan_ready_removed` (`plan_id`, `target_ready_at`, `removed`),
    CONSTRAINT `fk_dr_restore_point__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'run_id', 'bigint unsigned NULL AFTER `plan_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'checkpoint_sequence', 'bigint unsigned NULL AFTER `run_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'checkpoint_cycle_type', 'varchar(32) NULL AFTER `checkpoint_sequence`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'checkpoint_ref_hash', 'binary(32) NULL AFTER `checkpoint_cycle_type`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'effective_mode', 'varchar(32) NULL AFTER `state`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'requested_mode', 'varchar(32) NULL AFTER `state`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'automatic_reseed', 'tinyint(1) NULL AFTER `effective_mode`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'mode_decision_code', 'varchar(128) NULL AFTER `automatic_reseed`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'reseed_reason', 'varchar(128) NULL AFTER `mode_decision_code`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'invalid_baseline_disk_count', 'int unsigned NULL AFTER `reseed_reason`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'incremental_verified', 'tinyint(1) NULL AFTER `effective_mode`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'metrics_estimated', 'tinyint(1) NULL AFTER `incremental_verified`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'virtual_bytes', 'bigint unsigned NULL AFTER `metrics_estimated`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'changed_bytes', 'bigint unsigned NULL AFTER `virtual_bytes`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'source_read_bytes', 'bigint unsigned NULL AFTER `changed_bytes`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'target_written_bytes', 'bigint unsigned NULL AFTER `source_read_bytes`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'transfer_payload_bytes', 'bigint unsigned NULL AFTER `target_written_bytes`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'changed_extent_count', 'bigint unsigned NULL AFTER `transfer_payload_bytes`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'duration_ms', 'bigint unsigned NULL AFTER `changed_extent_count`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'throughput_bps', 'bigint unsigned NULL AFTER `duration_ms`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'baseline_generation', 'bigint unsigned NULL AFTER `throughput_bps`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_restore_point', 'cycle_token', 'varchar(255) NULL AFTER `baseline_generation`');
UPDATE `cloud`.`dr_restore_point` rp
JOIN (SELECT `plan_id`, MAX(`id`) AS `run_id` FROM `cloud`.`dr_run` WHERE `removed` IS NULL GROUP BY `plan_id`) latest_run
  ON latest_run.`plan_id` = rp.`plan_id`
SET rp.`run_id` = latest_run.`run_id`
WHERE rp.`removed` IS NULL AND rp.`run_id` IS NULL;
UPDATE `cloud`.`dr_restore_point`
SET `checkpoint_sequence` = CAST(SUBSTRING_INDEX(`source_snapshot_ref`, ':', -1) AS UNSIGNED),
    `checkpoint_cycle_type` = IF(CAST(SUBSTRING_INDEX(`source_snapshot_ref`, ':', -1) AS UNSIGNED) = 1, 'full-seed', 'incremental')
WHERE `removed` IS NULL AND `checkpoint_sequence` IS NULL
  AND `source_snapshot_ref` LIKE 'ftctl:%'
  AND SUBSTRING_INDEX(`source_snapshot_ref`, ':', -1) REGEXP '^[0-9]+$';
UPDATE `cloud`.`dr_restore_point`
SET `checkpoint_ref_hash` = UNHEX(SHA2(`source_snapshot_ref`, 256))
WHERE `removed` IS NULL AND `source_snapshot_ref` IS NOT NULL AND `checkpoint_ref_hash` IS NULL;
UPDATE `cloud`.`dr_restore_point` rp
JOIN (
    SELECT `plan_id`, `checkpoint_ref_hash`, MAX(`id`) AS `keep_id`
    FROM `cloud`.`dr_restore_point`
    WHERE `removed` IS NULL AND `checkpoint_ref_hash` IS NOT NULL
    GROUP BY `plan_id`, `checkpoint_ref_hash`
    HAVING COUNT(*) > 1
) duplicate_checkpoint
  ON duplicate_checkpoint.`plan_id` = rp.`plan_id`
 AND duplicate_checkpoint.`checkpoint_ref_hash` = rp.`checkpoint_ref_hash`
SET rp.`removed` = COALESCE(rp.`removed`, NOW()), rp.`checkpoint_ref_hash` = NULL, rp.`updated` = NOW()
WHERE rp.`id` <> duplicate_checkpoint.`keep_id`;
CALL `cloud`.`IDEMPOTENT_ADD_UNIQUE_KEY`('cloud.dr_restore_point', 'uk_dr_restore_point__plan_checkpoint_hash', '(`plan_id`, `checkpoint_ref_hash`)');
CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_dr_restore_point__plan_ready_removed', 'cloud.dr_restore_point', '(`plan_id`, `target_ready_at`, `removed`)');

CREATE TABLE IF NOT EXISTS `cloud`.`dr_restore_point_artifact` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `restore_point_id` bigint unsigned NOT NULL,
    `artifact_type` varchar(64) NOT NULL,
    `artifact_ref` varchar(2048) NOT NULL,
    `storage_pool_id` bigint unsigned NULL,
    `datastore_ref` varchar(2048) NULL,
    `format` varchar(64) NULL,
    `size_bytes` bigint NULL,
    `checksum` varchar(255) NULL,
    `parent_artifact_id` bigint unsigned NULL,
    `state` varchar(64) NULL,
    `details_json` text NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_restore_point_artifact__uuid` (`uuid`),
    KEY `i_dr_restore_point_artifact__restore_point_id` (`restore_point_id`),
    KEY `i_dr_restore_point_artifact__storage_pool_id` (`storage_pool_id`),
    KEY `i_dr_restore_point_artifact__parent_artifact_id` (`parent_artifact_id`),
    CONSTRAINT `fk_dr_rp_artifact__restore_point_id` FOREIGN KEY (`restore_point_id`) REFERENCES `dr_restore_point` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_rp_artifact__storage_pool_id` FOREIGN KEY (`storage_pool_id`) REFERENCES `storage_pool` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_dr_rp_artifact__parent_artifact_id` FOREIGN KEY (`parent_artifact_id`) REFERENCES `dr_restore_point_artifact` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_replica` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
    `target_site_id` bigint unsigned NOT NULL,
    `target_vm_id` bigint unsigned NULL,
    `target_external_ref` varchar(2048) NULL,
    `target_vm_name` varchar(255) NULL,
    `state` varchar(64) NULL,
    `power_state` varchar(64) NULL,
    `hypervisor_type` varchar(64) NULL,
    `active_side` varchar(64) NULL,
    `runtime_state_json` text NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_replica__uuid` (`uuid`),
    KEY `i_dr_replica__plan_id` (`plan_id`),
    KEY `i_dr_replica__target_site_id` (`target_site_id`),
    KEY `i_dr_replica__target_vm_id` (`target_vm_id`),
    CONSTRAINT `fk_dr_replica__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_replica__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_replica__target_vm_id` FOREIGN KEY (`target_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_replica_disk` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `replica_id` bigint unsigned NOT NULL,
    `source_volume_id` bigint unsigned NULL,
    `target_volume_id` bigint unsigned NULL,
    `source_disk_ref` varchar(2048) NULL,
    `target_disk_ref` varchar(2048) NULL,
    `disk_label` varchar(255) NULL,
    `format` varchar(64) NULL,
    `state` varchar(64) NULL,
    `size_bytes` bigint NULL,
    `details_json` text NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_replica_disk__uuid` (`uuid`),
    KEY `i_dr_replica_disk__replica_id` (`replica_id`),
    KEY `i_dr_replica_disk__source_volume_id` (`source_volume_id`),
    KEY `i_dr_replica_disk__target_volume_id` (`target_volume_id`),
    CONSTRAINT `fk_dr_replica_disk__replica_id` FOREIGN KEY (`replica_id`) REFERENCES `dr_replica` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_replica_disk__source_volume_id` FOREIGN KEY (`source_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_dr_replica_disk__target_volume_id` FOREIGN KEY (`target_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_run` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
    `run_type` varchar(64) NOT NULL,
    `state` varchar(64) NULL,
    `idempotency_key` varchar(255) NULL,
    `request_json` text NULL,
    `requested_by_user_id` bigint unsigned NULL,
    `async_job_id` bigint unsigned NULL,
    `external_job_ref` varchar(2048) NULL,
    `engine_accepted` tinyint(1) NOT NULL DEFAULT 0,
    `accepted_at` datetime NULL,
    `dispatch_started` datetime NULL,
    `dispatch_completed` datetime NULL,
    `projection_state` varchar(64) NULL,
    `projection_checked` datetime NULL,
    `retryable` tinyint(1) NOT NULL DEFAULT 0,
    `retry_count` int NOT NULL DEFAULT 0,
    `retry_after_seconds` int NULL,
    `next_retry_at` datetime NULL,
    `last_status_json` mediumtext NULL,
    `current_step_name` varchar(255) NULL,
    `error_code` varchar(128) NULL,
    `error_message` text NULL,
    `started` datetime NULL,
    `completed` datetime NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_run__uuid` (`uuid`),
    KEY `i_dr_run__plan_id` (`plan_id`),
    KEY `i_dr_run__plan_created` (`plan_id`, `created`),
    KEY `i_dr_run__plan_state_completed` (`plan_id`, `state`, `completed`),
    KEY `i_dr_run__idempotency` (`plan_id`, `idempotency_key`),
    KEY `i_dr_run__async_job_id` (`async_job_id`),
    CONSTRAINT `fk_dr_run__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_run__requested_by_user_id` FOREIGN KEY (`requested_by_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_dr_run__async_job_id` FOREIGN KEY (`async_job_id`) REFERENCES `async_job` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_run_step` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `run_id` bigint unsigned NOT NULL,
    `step_name` varchar(255) NOT NULL,
    `step_order` int NOT NULL DEFAULT 0,
    `state` varchar(64) NULL,
    `progress` int NULL,
    `started` datetime NULL,
    `completed` datetime NULL,
    `details_json` text NULL,
    `error_code` varchar(128) NULL,
    `error_message` text NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_run_step__uuid` (`uuid`),
    KEY `i_dr_run_step__run_id` (`run_id`),
    KEY `i_dr_run_step__run_order` (`run_id`, `step_order`),
    CONSTRAINT `fk_dr_run_step__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE `cloud`.`dr_run`
    ADD COLUMN IF NOT EXISTS `engine_accepted` tinyint(1) NOT NULL DEFAULT 0 AFTER `external_job_ref`,
    ADD COLUMN IF NOT EXISTS `accepted_at` datetime NULL AFTER `engine_accepted`,
    ADD COLUMN IF NOT EXISTS `dispatch_started` datetime NULL AFTER `accepted_at`,
    ADD COLUMN IF NOT EXISTS `dispatch_completed` datetime NULL AFTER `dispatch_started`,
    ADD COLUMN IF NOT EXISTS `projection_state` varchar(64) NULL AFTER `dispatch_completed`,
    ADD COLUMN IF NOT EXISTS `projection_checked` datetime NULL AFTER `projection_state`,
    ADD COLUMN IF NOT EXISTS `retryable` tinyint(1) NOT NULL DEFAULT 0 AFTER `projection_checked`,
    ADD COLUMN IF NOT EXISTS `retry_count` int NOT NULL DEFAULT 0 AFTER `retryable`,
    ADD COLUMN IF NOT EXISTS `retry_after_seconds` int NULL AFTER `retry_count`,
    ADD COLUMN IF NOT EXISTS `next_retry_at` datetime NULL AFTER `retry_after_seconds`,
    ADD COLUMN IF NOT EXISTS `last_status_json` mediumtext NULL AFTER `next_retry_at`,
    ADD INDEX IF NOT EXISTS `i_dr_run__plan_created` (`plan_id`, `created`),
    ADD INDEX IF NOT EXISTS `i_dr_run__plan_state_completed` (`plan_id`, `state`, `completed`);

ALTER TABLE `cloud`.`dr_run_step`
    ADD INDEX IF NOT EXISTS `i_dr_run_step__run_order` (`run_id`, `step_order`);

CREATE TABLE IF NOT EXISTS `cloud`.`dr_event` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NULL,
    `run_id` bigint unsigned NULL,
    `event_type` varchar(128) NOT NULL,
    `severity` varchar(32) NOT NULL,
    `source` varchar(64) NOT NULL,
    `message` text NULL,
    `details_json` text NULL,
    `created` datetime NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_event__uuid` (`uuid`),
    KEY `i_dr_event__plan_id` (`plan_id`),
    KEY `i_dr_event__run_id` (`run_id`),
    KEY `i_dr_event__created` (`created`),
    CONSTRAINT `fk_dr_event__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_dr_event__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_plan_view_cache` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `plan_id` bigint unsigned NOT NULL,
    `snapshot_version` int unsigned NOT NULL DEFAULT 1,
    `snapshot_json` mediumtext NOT NULL,
    `projection_state` varchar(32) NOT NULL DEFAULT 'READY',
    `last_error` varchar(4096) DEFAULT NULL,
    `last_refresh_error_code` varchar(255) DEFAULT NULL,
    `last_refresh_error_message` varchar(4096) DEFAULT NULL,
    `last_refresh_failed_at` datetime DEFAULT NULL,
    `generated_at` datetime NOT NULL,
    `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_plan_view_cache__plan_id` (`plan_id`),
    KEY `i_dr_plan_view_cache__generated_at` (`generated_at`),
    CONSTRAINT `fk_dr_plan_view_cache__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_plan_runtime` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `plan_id` bigint unsigned NOT NULL,
    `engine_run_uuid` varchar(40) DEFAULT NULL,
    `runtime_generation` bigint unsigned NOT NULL DEFAULT 0,
    `scheduler_state` varchar(32) DEFAULT NULL,
    `scheduler_pid_alive` tinyint(1) NOT NULL DEFAULT 0,
    `worker_state` varchar(32) DEFAULT NULL,
    `current_cycle_sequence` bigint unsigned DEFAULT NULL,
    `current_cycle_state` varchar(32) DEFAULT NULL,
    `current_cycle_mode` varchar(32) DEFAULT NULL,
    `baseline_state` varchar(32) DEFAULT NULL,
    `reseed_reason` varchar(128) DEFAULT NULL,
    `last_mode_decision_code` varchar(128) DEFAULT NULL,
    `consecutive_automatic_reseed_count` int unsigned NOT NULL DEFAULT 0,
    `latest_completed_cycle_sequence` bigint unsigned DEFAULT NULL,
    `latest_completed_incremental_verified` tinyint(1) DEFAULT NULL,
    `protection_state` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `freshness_state` varchar(32) NOT NULL DEFAULT 'UNKNOWN',
    `last_status_at` datetime DEFAULT NULL,
    `last_source_checkpoint_at` datetime DEFAULT NULL,
    `last_target_durable_at` datetime DEFAULT NULL,
    `rpo_age_seconds` bigint unsigned DEFAULT NULL,
    `rpo_overdue` tinyint(1) NOT NULL DEFAULT 0,
    `error_code` varchar(128) DEFAULT NULL,
    `error_message` varchar(4096) DEFAULT NULL,
    `status_json` mediumtext,
    `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_plan_runtime__plan_id` (`plan_id`),
    KEY `i_dr_plan_runtime__state_updated` (`protection_state`, `freshness_state`, `updated`),
    CONSTRAINT `fk_dr_plan_runtime__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_sync_cycle` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
    `run_id` bigint unsigned DEFAULT NULL,
    `engine_run_uuid` varchar(40) NOT NULL,
    `sequence` bigint unsigned NOT NULL,
    `requested_mode` varchar(32) DEFAULT NULL,
    `effective_mode` varchar(32) DEFAULT NULL,
    `state` varchar(32) NOT NULL,
    `commit_state` varchar(32) DEFAULT NULL,
    `baseline_generation` bigint unsigned DEFAULT NULL,
    `baseline_state` varchar(32) DEFAULT NULL,
    `reseed_reason` varchar(128) DEFAULT NULL,
    `automatic_reseed` tinyint(1) DEFAULT NULL,
    `mode_decision_code` varchar(128) DEFAULT NULL,
    `invalid_baseline_disk_count` int unsigned DEFAULT NULL,
    `incremental_verified` tinyint(1) DEFAULT NULL,
    `metrics_estimated` tinyint(1) DEFAULT NULL,
    `virtual_bytes` bigint unsigned DEFAULT NULL,
    `changed_bytes` bigint unsigned DEFAULT NULL,
    `source_read_bytes` bigint unsigned DEFAULT NULL,
    `target_written_bytes` bigint unsigned DEFAULT NULL,
    `transfer_payload_bytes` bigint unsigned DEFAULT NULL,
    `changed_extent_count` bigint unsigned DEFAULT NULL,
    `duration_ms` bigint unsigned DEFAULT NULL,
    `throughput_bps` bigint unsigned DEFAULT NULL,
    `source_checkpoint_at` datetime DEFAULT NULL,
    `target_durable_at` datetime DEFAULT NULL,
    `error_code` varchar(128) DEFAULT NULL,
    `error_message` varchar(4096) DEFAULT NULL,
    `started` datetime DEFAULT NULL,
    `completed` datetime DEFAULT NULL,
    `created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated` datetime DEFAULT NULL,
    `removed` datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_sync_cycle__uuid` (`uuid`),
    UNIQUE KEY `uk_dr_sync_cycle__plan_run_sequence` (`plan_id`, `engine_run_uuid`, `sequence`),
    KEY `i_dr_sync_cycle__plan_sequence` (`plan_id`, `sequence`),
    KEY `i_dr_sync_cycle__plan_state_updated` (`plan_id`, `state`, `updated`),
    CONSTRAINT `fk_dr_sync_cycle__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_sync_cycle__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'last_mode_decision_code', 'varchar(128) NULL AFTER `reseed_reason`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'consecutive_automatic_reseed_count', 'int unsigned NOT NULL DEFAULT 0 AFTER `last_mode_decision_code`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'latest_completed_cycle_sequence', 'bigint unsigned NULL AFTER `consecutive_automatic_reseed_count`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'latest_completed_incremental_verified', 'tinyint(1) NULL AFTER `latest_completed_cycle_sequence`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_sync_cycle', 'automatic_reseed', 'tinyint(1) NULL AFTER `reseed_reason`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_sync_cycle', 'mode_decision_code', 'varchar(128) NULL AFTER `automatic_reseed`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_sync_cycle', 'invalid_baseline_disk_count', 'int unsigned NULL AFTER `mode_decision_code`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_sync_cycle', 'cycle_token', 'varchar(255) NULL AFTER `sequence`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'projection_integrity_state', 'varchar(32) NULL AFTER `latest_completed_incremental_verified`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'projection_integrity_code', 'varchar(128) NULL AFTER `projection_integrity_state`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan_runtime', 'projection_integrity_sequence', 'bigint unsigned NULL AFTER `projection_integrity_code`');


ALTER TABLE `cloud`.`dr_plan_view_cache`
    ADD COLUMN IF NOT EXISTS `last_refresh_error_code` varchar(255) DEFAULT NULL AFTER `last_error`,
    ADD COLUMN IF NOT EXISTS `last_refresh_error_message` varchar(4096) DEFAULT NULL AFTER `last_refresh_error_code`,
    ADD COLUMN IF NOT EXISTS `last_refresh_failed_at` datetime DEFAULT NULL AFTER `last_refresh_error_message`;

ALTER TABLE `cloud`.`dr_event`
    ADD INDEX IF NOT EXISTS `i_dr_event__plan_created_id` (`plan_id`, `created`, `id`);

UPDATE `cloud`.`volumes` v
JOIN `cloud`.`dr_replica_disk` d ON d.target_volume_id = v.id AND d.removed IS NULL
JOIN `cloud`.`storage_pool` p ON p.id = v.pool_id AND p.removed IS NULL
SET v.format = 'RAW'
WHERE v.removed IS NULL
  AND UPPER(p.pool_type) = 'RBD'
  AND UPPER(COALESCE(v.format, '')) <> 'RAW';

-- Allow the default Resource Admin role to use Cross Hypervisor DR APIs.
INSERT INTO `cloud`.`role_permissions` (`uuid`, `role_id`, `rule`, `permission`, `sort_order`)
SELECT UUID(), r.id, dr_api.rule, 'ALLOW',
       (SELECT COALESCE(MAX(rp.sort_order), 0) + 1
          FROM `cloud`.`role_permissions` rp
         WHERE rp.role_id = r.id)
FROM `cloud`.`roles` r
JOIN (
    SELECT 'adoptDrReplica' AS rule UNION ALL
    SELECT 'cancelDrRun' UNION ALL
    SELECT 'checkDrSite' UNION ALL
    SELECT 'clearDisasterRecoveryCluster' UNION ALL
    SELECT 'confirmDrFenceClear' UNION ALL
    SELECT 'connectivityTestsDisasterRecovery' UNION ALL
    SELECT 'createDisasterRecoveryCluster' UNION ALL
    SELECT 'createDisasterRecoveryClusterVm' UNION ALL
    SELECT 'createDrPlan' UNION ALL
    SELECT 'createDrSite' UNION ALL
    SELECT 'deleteDisasterRecoveryCluster' UNION ALL
    SELECT 'deleteDisasterRecoveryClusterVm' UNION ALL
    SELECT 'deleteDrPlan' UNION ALL
    SELECT 'deleteDrSite' UNION ALL
    SELECT 'demoteDisasterRecoveryCluster' UNION ALL
    SELECT 'demoteDisasterRecoveryClusterVm' UNION ALL
    SELECT 'discoverDrPlanInventory' UNION ALL
    SELECT 'discoverDrSiteInventory' UNION ALL
    SELECT 'disableDisasterRecoveryCluster' UNION ALL
    SELECT 'disableDrPlan' UNION ALL
    SELECT 'enableDisasterRecoveryCluster' UNION ALL
    SELECT 'enableDrPlan' UNION ALL
    SELECT 'getDisasterRecoveryClusterList' UNION ALL
    SELECT 'getDrPlan' UNION ALL
    SELECT 'getDrProtectionView' UNION ALL
    SELECT 'getDrRun' UNION ALL
    SELECT 'getDrSite' UNION ALL
    SELECT 'listDrEvents' UNION ALL
    SELECT 'listDrPlans' UNION ALL
    SELECT 'listDrReplicas' UNION ALL
    SELECT 'listDrRestorePoints' UNION ALL
    SELECT 'listDrRuns' UNION ALL
    SELECT 'listDrRunSteps' UNION ALL
    SELECT 'listDrSiteHealthChecks' UNION ALL
    SELECT 'listDrSites' UNION ALL
    SELECT 'pauseDrSync' UNION ALL
    SELECT 'previewDrPlanSpec' UNION ALL
    SELECT 'promoteDisasterRecoveryCluster' UNION ALL
    SELECT 'promoteDisasterRecoveryClusterVm' UNION ALL
    SELECT 'releaseDrProtection' UNION ALL
    SELECT 'refreshDrProtectionView' UNION ALL
    SELECT 'resumeDrSync' UNION ALL
    SELECT 'resyncDisasterRecoveryCluster' UNION ALL
    SELECT 'startDisasterRecoveryClusterVm' UNION ALL
    SELECT 'startDrFailback' UNION ALL
    SELECT 'startDrFailover' UNION ALL
    SELECT 'startDrReprotect' UNION ALL
    SELECT 'startDrSync' UNION ALL
    SELECT 'startDrTestFailover' UNION ALL
    SELECT 'stopDisasterRecoveryClusterVm' UNION ALL
    SELECT 'stopDrTestFailover' UNION ALL
    SELECT 'takeSnapshotDisasterRecoveryClusterVm' UNION ALL
    SELECT 'updateDisasterRecoveryCluster' UNION ALL
    SELECT 'updateDisasterRecoveryClusterVm' UNION ALL
    SELECT 'updateDrPlan' UNION ALL
    SELECT 'updateDrSite'
) dr_api
WHERE r.name = 'Resource Admin'
  AND NOT EXISTS (
        SELECT 1
          FROM `cloud`.`role_permissions` existing
         WHERE existing.role_id = r.id
           AND existing.rule = dr_api.rule
      );

-- Add v2k_step column to import_vm_task table for ablestack-v2k workflow status persistence
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','v2k_step', 'varchar(32) DEFAULT ''None'' COMMENT "Ablestack-v2k importing VM task step"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','cluster_id', 'bigint unsigned COMMENT "Cluster ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','service_offering_id', 'bigint unsigned COMMENT "Service offering ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','v2k_target_storage_pool_id', 'bigint unsigned COMMENT "Primary storage pool ID used as ablestack-v2k target"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_cluster_name', 'varchar(255) COMMENT "Source VMware cluster name used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_host_name', 'varchar(255) COMMENT "Source VMware host name used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_id', 'bigint unsigned COMMENT "Existing vCenter ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_username', 'varchar(255) COMMENT "vCenter username used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','vcenter_password', 'varchar(255) COMMENT "vCenter password used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','service_offering_details', 'text COMMENT "Serialized custom service offering details used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','nic_network_map', 'text COMMENT "Serialized NIC selection map used by the import task, including network and optional IP address"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','migration_tool', 'varchar(32) DEFAULT ''legacy'' COMMENT "Migration tool used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_provider', 'varchar(32) COMMENT "Source provider used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_provider', 'varchar(32) COMMENT "Target provider used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_profile', 'varchar(64) COMMENT "Resolved target profile used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_storage_pool_id', 'bigint unsigned COMMENT "Resolved target primary storage pool ID used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_format', 'varchar(16) COMMENT "Resolved target disk format used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_storage_type', 'varchar(32) COMMENT "Resolved target storage type used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_vm_name', 'varchar(255) COMMENT "Target VM name used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_endpoint', 'varchar(255) COMMENT "Source endpoint used by the import task without secrets"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_ref', 'varchar(255) COMMENT "Provider-specific source VM reference used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_inventory_json', 'mediumtext COMMENT "Serialized source VM inventory snapshot"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_context_json', 'mediumtext COMMENT "Serialized non-secret source context"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','source_credential_id', 'bigint unsigned COMMENT "Encrypted credential row used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','target_context_json', 'mediumtext COMMENT "Serialized target context and disk map"');
CALL `cloud`.`IDEMPOTENT_CHANGE_COLUMN`('cloud.import_vm_task', 'source_inventory_json', 'source_inventory_json', 'mediumtext COMMENT "Serialized source VM inventory snapshot"');
CALL `cloud`.`IDEMPOTENT_CHANGE_COLUMN`('cloud.import_vm_task', 'source_context_json', 'source_context_json', 'mediumtext COMMENT "Serialized non-secret source context"');
CALL `cloud`.`IDEMPOTENT_CHANGE_COLUMN`('cloud.import_vm_task', 'target_context_json', 'target_context_json', 'mediumtext COMMENT "Serialized target context and disk map"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','workdir', 'varchar(1024) COMMENT "Tool workdir used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','split_mode', 'varchar(16) COMMENT "Requested split mode used by the import task"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','current_phase', 'varchar(32) COMMENT "Current normalized migration phase"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','migration_state', 'varchar(32) COMMENT "Current normalized migration state"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','migration_step', 'varchar(255) COMMENT "Current normalized migration step"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','cutover_policy', 'varchar(32) COMMENT "Cutover policy used by phase2 or full migration"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','status_json', 'mediumtext COMMENT "Latest normalized migration status payload"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.import_vm_task','error_code', 'varchar(64) COMMENT "Normalized migration error code"');

ALTER TABLE `cloud`.`import_vm_task` CONVERT TO CHARACTER SET utf8mb4;

CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_import_vm_task__zone_tool_state_created', 'cloud.import_vm_task', '(`zone_id`, `migration_tool`, `state`, `created`)');
CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_import_vm_task__zone_source_state_created', 'cloud.import_vm_task', '(`zone_id`, `source_provider`, `state`, `created`)');
CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_import_vm_task__target_phase_state', 'cloud.import_vm_task', '(`target_provider`, `current_phase`, `migration_state`)');
CALL `cloud`.`IDEMPOTENT_ADD_KEY`('i_import_vm_task__source_credential_id', 'cloud.import_vm_task', '(`source_credential_id`)');

CREATE TABLE IF NOT EXISTS `cloud`.`import_vm_task_event`(
    `id` bigint unsigned NOT NULL auto_increment COMMENT 'id',
    `uuid` varchar(40) NOT NULL COMMENT 'UUID',
    `task_id` bigint unsigned NOT NULL COMMENT 'Import VM task ID',
    `event_type` varchar(64) NOT NULL COMMENT 'Import VM task event type',
    `phase` varchar(32) COMMENT 'Migration phase at event time',
    `state` varchar(32) COMMENT 'Migration state at event time',
    `step` varchar(255) COMMENT 'Migration step at event time',
    `message` text COMMENT 'Import VM task event message',
    `payload_json` mediumtext COMMENT 'Serialized event payload without secrets',
    `created` datetime NOT NULL COMMENT 'date created',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_import_vm_task_event__task_id` FOREIGN KEY `fk_import_vm_task_event__task_id` (`task_id`) REFERENCES `import_vm_task`(`id`) ON DELETE CASCADE,
    INDEX `i_import_vm_task_event__task_id_created`(`task_id`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cloud`.`import_vm_task_credential`(
    `id` bigint unsigned NOT NULL auto_increment COMMENT 'id',
    `uuid` varchar(40) NOT NULL COMMENT 'UUID',
    `task_id` bigint unsigned NOT NULL COMMENT 'Import VM task ID',
    `provider` varchar(32) NOT NULL COMMENT 'Credential source provider',
    `credential_type` varchar(32) NOT NULL COMMENT 'Credential type',
    `username_hint` varchar(255) COMMENT 'Non-secret username hint',
    `encrypted_payload` mediumtext NOT NULL COMMENT 'Encrypted credential payload',
    `encryption_version` varchar(32) NOT NULL COMMENT 'Credential encryption version',
    `key_id` varchar(128) COMMENT 'Credential encryption key ID',
    `created` datetime NOT NULL COMMENT 'date created',
    `updated` datetime COMMENT 'date updated if not null',
    `removed` datetime COMMENT 'date removed if not null',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_import_vm_task_credential__task_id` FOREIGN KEY `fk_import_vm_task_credential__task_id` (`task_id`) REFERENCES `import_vm_task`(`id`) ON DELETE CASCADE,
    INDEX `i_import_vm_task_credential__task_id_created`(`task_id`, `created`),
    INDEX `i_import_vm_task_credential__task_id_removed`(`task_id`, `removed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `cloud`.`import_vm_task_event` CONVERT TO CHARACTER SET utf8mb4;
ALTER TABLE `cloud`.`import_vm_task_credential` CONVERT TO CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_cutover_session` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned NOT NULL, `run_id` bigint unsigned NOT NULL, `mode` varchar(16) NOT NULL,
  `checkpoint_sequence` bigint unsigned DEFAULT NULL, `state` varchar(64) NOT NULL,
  `guest_os_family` varchar(32) DEFAULT NULL, `guest_preparation_state` varchar(64) DEFAULT NULL,
  `virtio_state` varchar(32) DEFAULT NULL, `secure_boot_state` varchar(32) DEFAULT NULL,
  `domain_name` varchar(255) DEFAULT NULL, `boot_validation_state` varchar(64) DEFAULT NULL,
  `cleanup_required` tinyint(1) NOT NULL DEFAULT 0, `details_json` mediumtext,
  `error_code` varchar(128) DEFAULT NULL, `error_message` varchar(1024) DEFAULT NULL,
  `created` datetime NOT NULL, `updated` datetime NOT NULL, `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_dr_cutover_session_uuid` (`uuid`),
  KEY `idx_dr_cutover_session_plan_active` (`plan_id`,`removed`), KEY `idx_dr_cutover_session_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `cloud`.`dr_cutover_disk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `session_id` bigint unsigned NOT NULL,
  `disk_index` int unsigned NOT NULL, `provider` varchar(32) NOT NULL,
  `checkpoint_ref` varchar(1024) NOT NULL, `writable_ref` varchar(1024) DEFAULT NULL,
  `rollback_ref` varchar(1024) DEFAULT NULL, `state` varchar(64) NOT NULL,
  `details_json` mediumtext, `created` datetime NOT NULL, `updated` datetime NOT NULL, `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_dr_cutover_disk_session_index` (`session_id`,`disk_index`),
  KEY `idx_dr_cutover_disk_session_active` (`session_id`,`removed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS `cloud`.`dr_test_session` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned NOT NULL, `run_id` bigint unsigned NOT NULL, `cleanup_run_id` bigint unsigned DEFAULT NULL,
  `state` varchar(64) NOT NULL, `network_mode` varchar(32) DEFAULT NULL, `network_id` bigint unsigned DEFAULT NULL,
  `target_vm_id` bigint unsigned DEFAULT NULL, `target_vm_uuid` varchar(40) DEFAULT NULL, `target_vm_name` varchar(255) DEFAULT NULL,
  `checkpoint_sequence` bigint unsigned DEFAULT NULL, `artifact_manifest` mediumtext, `boot_validation_state` varchar(64) DEFAULT NULL,
  `cleanup_required` tinyint(1) NOT NULL DEFAULT 0, `error_code` varchar(128) DEFAULT NULL, `error_message` varchar(1024) DEFAULT NULL,
  `details_json` mediumtext, `created` datetime NOT NULL, `updated` datetime NOT NULL, `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_dr_test_session_uuid` (`uuid`), KEY `idx_dr_test_session_plan_active` (`plan_id`,`removed`),
  KEY `idx_dr_test_session_run` (`run_id`), KEY `idx_dr_test_session_vm` (`target_vm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cloud`.`dr_test_disk` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT, `session_id` bigint unsigned NOT NULL, `disk_index` int NOT NULL,
  `provider` varchar(32) DEFAULT NULL, `artifact_ref` varchar(1024) DEFAULT NULL, `target_volume_id` bigint unsigned DEFAULT NULL,
  `target_volume_uuid` varchar(40) DEFAULT NULL, `state` varchar(64) NOT NULL, `details_json` mediumtext,
  `created` datetime NOT NULL, `updated` datetime NOT NULL, `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_dr_test_disk_session_index` (`session_id`,`disk_index`), KEY `idx_dr_test_disk_volume` (`target_volume_id`),
  CONSTRAINT `fk_dr_test_disk_session` FOREIGN KEY (`session_id`) REFERENCES `dr_test_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
