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
-- Schema upgrade from 4.22.0.0 to 4.22.1.0
--;

-- Add vm_id column to usage_event table for volume usage events
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.usage_event','vm_id', 'bigint UNSIGNED NULL COMMENT "VM ID associated with volume usage events"');
CALL `cloud_usage`.`IDEMPOTENT_ADD_COLUMN`('cloud_usage.usage_event','vm_id', 'bigint UNSIGNED NULL COMMENT "VM ID associated with volume usage events"');

-- Add vm_id column to cloud_usage.usage_volume table
CALL `cloud_usage`.`IDEMPOTENT_ADD_COLUMN`('cloud_usage.usage_volume','vm_id', 'bigint UNSIGNED NULL COMMENT "VM ID associated with the volume usage"');

ALTER TABLE `cloud`.`template_store_ref` MODIFY COLUMN `download_url` varchar(2048);

UPDATE `cloud`.`alert` SET type = 33 WHERE name = 'ALERT.VR.PUBLIC.IFACE.MTU';
UPDATE `cloud`.`alert` SET type = 34 WHERE name = 'ALERT.VR.PRIVATE.IFACE.MTU';

-- Update configuration 'kvm.ssh.to.agent' description and is_dynamic fields
UPDATE `cloud`.`configuration` SET description = 'True if the management server will restart the agent service via SSH into the KVM hosts after or during maintenance operations', is_dynamic = 1 WHERE name = 'kvm.ssh.to.agent';
-- Drops the unused "backup_interval_type" column of the "cloud.backups" table
ALTER TABLE `cloud`.`backups` DROP COLUMN `backup_interval_type`;

-- Sanitize legacy network-level addressing fields for Public networks
UPDATE `cloud`.`networks`
SET `broadcast_uri` = NULL,
	`gateway` = NULL,
	`cidr` = NULL,
	`ip6_gateway` = NULL,
	`ip6_cidr` = NULL
WHERE `traffic_type` = 'Public';

-- Update existing vm_template records with NULL type to "USER"
UPDATE `cloud`.`vm_template` SET `type` = 'USER' WHERE `type` IS NULL;

-- remove unused config item
DELETE FROM `cloud`.`configuration` WHERE name = 'consoleproxy.cmd.port';

-- Update `user.password.reset.mail.template` configuration value to match new logic
UPDATE `cloud`.`configuration`
SET value = CONCAT_WS('\n', 'Hello {{username}}!', 'You have requested to reset your password. Please click the following link to reset your password:', '{{{resetLink}}}', 'If you did not request a password reset, please ignore this email.', '', 'Regards,', 'The CloudStack Team')
WHERE name = 'user.password.reset.mail.template'
  AND value IN (CONCAT_WS('\n', 'Hello {{username}}!', 'You have requested to reset your password. Please click the following link to reset your password:', 'http://{{{resetLink}}}', 'If you did not request a password reset, please ignore this email.', '', 'Regards,', 'The CloudStack Team'), CONCAT_WS('\n', 'Hello {{username}}!', 'You have requested to reset your password. Please click the following link to reset your password:', '{{{domainUrl}}}{{{resetLink}}}', 'If you did not request a password reset, please ignore this email.', '', 'Regards,', 'The CloudStack Team'));

UPDATE `cloud`.`vm_template` SET guest_os_id = 99 WHERE name = 'kvm-default-vm-import-dummy-template';

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
    `zone_id` bigint unsigned NULL,
    `vmware_datacenter_id` bigint unsigned NULL,
    `state` varchar(64) NULL,
    `health_state` varchar(64) NULL,
    `capabilities_json` text NULL,
    `last_checked` datetime NULL,
    `created` datetime NOT NULL,
    `updated` datetime NULL,
    `removed` datetime NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dr_site__uuid` (`uuid`),
    KEY `i_dr_site__name` (`name`),
    KEY `i_dr_site__zone_id` (`zone_id`),
    CONSTRAINT `fk_dr_site__zone_id` FOREIGN KEY (`zone_id`) REFERENCES `data_center` (`id`) ON DELETE SET NULL
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
    KEY `i_dr_plan__engine_binding` (`engine_binding_type`, `engine_binding_id`),
    KEY `i_dr_plan__state` (`state`),
    CONSTRAINT `fk_dr_plan__source_site_id` FOREIGN KEY (`source_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_plan__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_dr_plan__source_vm_id` FOREIGN KEY (`source_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'active_side', 'varchar(16) NULL AFTER `admin_state`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'source_worker_host_id', 'bigint unsigned NULL AFTER `quiesce_policy_json`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'target_worker_host_id', 'bigint unsigned NULL AFTER `source_worker_host_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'coordinator_worker_host_id', 'bigint unsigned NULL AFTER `target_worker_host_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'last_source_checkpoint_at', 'datetime NULL AFTER `coordinator_worker_host_id`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.dr_plan', 'last_target_durable_at', 'datetime NULL AFTER `last_source_checkpoint_at`');

CREATE TABLE IF NOT EXISTS `cloud`.`dr_restore_point` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `plan_id` bigint unsigned NOT NULL,
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
    KEY `i_dr_restore_point__plan_id` (`plan_id`),
    KEY `i_dr_restore_point__target_ready_at` (`target_ready_at`),
    CONSTRAINT `fk_dr_restore_point__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

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
    CONSTRAINT `fk_dr_run_step__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

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
