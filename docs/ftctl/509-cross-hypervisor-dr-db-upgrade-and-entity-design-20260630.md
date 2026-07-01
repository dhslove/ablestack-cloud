# Cross Hypervisor DR DB Upgrade And Entity Design

작성일: 2026-06-30

대상 브랜치: `feature/ftctl-cloud-integration`

상위 문서:

- [501-cross-hypervisor-dr-domain-schema-design-20260630.md](501-cross-hypervisor-dr-domain-schema-design-20260630.md)
- [507-cross-hypervisor-dr-cloud-api-command-design-20260630.md](507-cross-hypervisor-dr-cloud-api-command-design-20260630.md)
- [508-cross-hypervisor-dr-cloud-backend-wiring-design-20260630.md](508-cross-hypervisor-dr-cloud-backend-wiring-design-20260630.md)

## 1. 목적

이 문서는 `Cross Hypervisor DR` 도메인 모델을 Cloud DB, JPA entity, DAO, upgrade path에 반영하는 구현 설계를 정의한다.

[501 문서](501-cross-hypervisor-dr-domain-schema-design-20260630.md)는 논리 schema를 정의했다. 이 문서는 실제 구현자가 빠뜨리기 쉬운 다음 항목을 보강한다.

- fresh install schema 반영 위치
- upgrade install schema 반영 방식
- VO/DAO class 배치
- FK/index/unique 제약
- uuid와 internal id 변환
- JSON text field 사용 규칙
- `removed` 기반 soft delete와 active unique 처리
- 기존 `disaster_recovery_cluster`, `ftctl_protection`과의 연결

## 2. 반영 대상 파일

Fresh install:

| 파일 | 작업 |
| --- | --- |
| `setup/db/create-schema.sql` | `DROP TABLE IF EXISTS`와 `CREATE TABLE` 추가 |
| `setup/db/create-schema-premium.sql` | 해당 배포에서 사용된다면 동일 반영 |
| `setup/db/create-schema-simulator.sql` | simulator schema가 별도 필요하면 동일 반영 |

Upgrade install:

| 파일 | 작업 |
| --- | --- |
| active version upgrade SQL | 신규 table/index/FK 추가 |
| active upgrade shell | 별도 SQL 파일을 호출하는 방식이면 호출 추가 |

이 repository에는 `setup/db/221to222upgrade.sh` 같은 legacy upgrade entry가 존재한다. 구현 시점의 제품 버전 upgrade convention을 확인한 뒤, fresh schema와 upgrade schema가 반드시 동일한 결과를 만들도록 한다.

검증 원칙:

- fresh install DB와 upgraded DB에서 `SHOW CREATE TABLE` 결과가 의미상 동일해야 한다.
- upgrade SQL은 재실행 시 치명적 실패를 만들지 않도록 table existence와 index existence를 고려한다.
- foreign key 순서를 지켜 parent table이 먼저 생성되어야 한다.

## 3. Table 생성 순서

FK 의존성을 고려한 생성 순서:

1. `dr_site`
2. `dr_site_pair`
3. `dr_plan`
4. `dr_restore_point`
5. `dr_restore_point_artifact`
6. `dr_replica`
7. `dr_replica_disk`
8. `dr_run`
9. `dr_run_step`
10. `dr_event`

DROP 순서는 반대다.

`setup/db/create-schema.sql` 상단의 `DROP TABLE IF EXISTS` 목록에도 반대 순서로 추가한다.

## 4. DDL 기준

### 4.1 공통 컬럼 규칙

모든 신규 table은 다음 규칙을 따른다.

- `id bigint unsigned NOT NULL auto_increment`
- `uuid varchar(40) NOT NULL`
- `created datetime NOT NULL`
- `removed datetime DEFAULT NULL`
- text JSON 컬럼은 `text DEFAULT NULL`
- 상태 enum은 Java enum이 아니라 `varchar(32)` 또는 `varchar(64)`로 저장한다.
- FK는 명시 이름을 둔다.
- uuid unique key를 둔다.

`uuid`는 API에 노출되는 id이고, 내부 FK는 numeric `id`를 사용한다.

### 4.2 dr_site

```sql
CREATE TABLE `cloud`.`dr_site` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `site_type` varchar(32) NOT NULL,
  `hypervisor_type` varchar(32) NOT NULL,
  `endpoint` varchar(1024) DEFAULT NULL,
  `credential_ref` varchar(255) DEFAULT NULL,
  `zone_id` bigint unsigned DEFAULT NULL,
  `vmware_dc_id` bigint unsigned DEFAULT NULL,
  `capability_json` text DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `last_check_result` varchar(32) DEFAULT NULL,
  `last_check_message` text DEFAULT NULL,
  `last_check_time` datetime DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_site__uuid` (`uuid`),
  KEY `i_dr_site__type_status` (`site_type`, `hypervisor_type`, `status`),
  KEY `i_dr_site__zone_id` (`zone_id`),
  KEY `i_dr_site__vmware_dc_id` (`vmware_dc_id`)
);
```

Active name unique는 MySQL의 NULL unique 동작 때문에 단순 `UNIQUE(name, removed)`만으로 충분하지 않을 수 있다. 구현 시 아래 중 하나를 선택한다.

- service layer에서 active name 중복을 강제한다.
- `removed` 대신 `removed_epoch` 같은 generated/key column을 추가한다.
- DB 버전이 지원하면 functional unique index를 사용한다.

초기 구현은 service layer 중복 검사를 기본으로 한다.

### 4.3 dr_site_pair

```sql
CREATE TABLE `cloud`.`dr_site_pair` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `name` varchar(255) NOT NULL,
  `source_site_id` bigint unsigned NOT NULL,
  `target_site_id` bigint unsigned NOT NULL,
  `legacy_dr_cluster_id` bigint unsigned DEFAULT NULL,
  `policy_json` text DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_site_pair__uuid` (`uuid`),
  KEY `i_dr_site_pair__source_target` (`source_site_id`, `target_site_id`, `status`),
  KEY `i_dr_site_pair__legacy_dr_cluster_id` (`legacy_dr_cluster_id`),
  CONSTRAINT `fk_dr_site_pair__source_site_id` FOREIGN KEY (`source_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_site_pair__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_site_pair__legacy_dr_cluster_id` FOREIGN KEY (`legacy_dr_cluster_id`) REFERENCES `disaster_recovery_cluster` (`id`) ON DELETE SET NULL
);
```

### 4.4 dr_plan

```sql
CREATE TABLE `cloud`.`dr_plan` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `account_id` bigint unsigned NOT NULL,
  `domain_id` bigint unsigned NOT NULL,
  `source_site_id` bigint unsigned NOT NULL,
  `target_site_id` bigint unsigned NOT NULL,
  `site_pair_id` bigint unsigned DEFAULT NULL,
  `source_vm_id` bigint unsigned DEFAULT NULL,
  `source_external_ref` varchar(1024) DEFAULT NULL,
  `direction` varchar(32) NOT NULL,
  `state` varchar(32) NOT NULL,
  `rpo_seconds` bigint DEFAULT NULL,
  `rto_seconds` bigint DEFAULT NULL,
  `schedule_json` text DEFAULT NULL,
  `storage_mapping_json` text DEFAULT NULL,
  `network_mapping_json` text DEFAULT NULL,
  `compute_mapping_json` text DEFAULT NULL,
  `fencing_policy_json` text DEFAULT NULL,
  `quiesce_policy_json` text DEFAULT NULL,
  `compatibility_json` text DEFAULT NULL,
  `engine_binding_type` varchar(64) DEFAULT NULL,
  `engine_binding_id` bigint unsigned DEFAULT NULL,
  `last_restore_point_id` bigint unsigned DEFAULT NULL,
  `last_target_ready_restore_point_id` bigint unsigned DEFAULT NULL,
  `last_error_code` varchar(128) DEFAULT NULL,
  `last_error_message` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_plan__uuid` (`uuid`),
  KEY `i_dr_plan__account_domain_removed` (`account_id`, `domain_id`, `removed`),
  KEY `i_dr_plan__source_vm_removed` (`source_vm_id`, `removed`),
  KEY `i_dr_plan__source_target_state` (`source_site_id`, `target_site_id`, `state`),
  KEY `i_dr_plan__direction_state` (`direction`, `state`),
  KEY `i_dr_plan__engine_binding` (`engine_binding_type`, `engine_binding_id`),
  CONSTRAINT `fk_dr_plan__account_id` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_plan__domain_id` FOREIGN KEY (`domain_id`) REFERENCES `domain` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_plan__source_site_id` FOREIGN KEY (`source_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_plan__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_plan__site_pair_id` FOREIGN KEY (`site_pair_id`) REFERENCES `dr_site_pair` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dr_plan__source_vm_id` FOREIGN KEY (`source_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL
);
```

`last_restore_point_id`와 `last_target_ready_restore_point_id`는 `dr_restore_point` 생성 후 FK를 추가하면 circular DDL 문제가 생길 수 있다. 초기 구현은 FK 없이 index/DAO validation으로 유지한다.

### 4.5 dr_restore_point

```sql
CREATE TABLE `cloud`.`dr_restore_point` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned NOT NULL,
  `sequence_no` bigint NOT NULL,
  `state` varchar(32) NOT NULL,
  `source_captured_at` datetime DEFAULT NULL,
  `source_ready_at` datetime DEFAULT NULL,
  `target_ready_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `metadata_json` text DEFAULT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_restore_point__uuid` (`uuid`),
  UNIQUE KEY `uk_dr_restore_point__plan_seq` (`plan_id`, `sequence_no`),
  KEY `i_dr_restore_point__plan_state_removed` (`plan_id`, `state`, `removed`),
  KEY `i_dr_restore_point__plan_target_ready` (`plan_id`, `target_ready_at`),
  CONSTRAINT `fk_dr_restore_point__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
);
```

### 4.6 dr_restore_point_artifact

```sql
CREATE TABLE `cloud`.`dr_restore_point_artifact` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `restore_point_id` bigint unsigned NOT NULL,
  `source_volume_id` bigint unsigned DEFAULT NULL,
  `source_disk_ref` varchar(1024) DEFAULT NULL,
  `artifact_type` varchar(32) NOT NULL,
  `artifact_ref` varchar(2048) DEFAULT NULL,
  `format` varchar(32) DEFAULT NULL,
  `state` varchar(32) NOT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `metadata_json` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_restore_point_artifact__uuid` (`uuid`),
  KEY `i_dr_restore_point_artifact__rp` (`restore_point_id`),
  KEY `i_dr_restore_point_artifact__source_volume` (`source_volume_id`),
  CONSTRAINT `fk_dr_restore_point_artifact__rp` FOREIGN KEY (`restore_point_id`) REFERENCES `dr_restore_point` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_restore_point_artifact__source_volume` FOREIGN KEY (`source_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL
);
```

### 4.7 dr_replica

```sql
CREATE TABLE `cloud`.`dr_replica` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned NOT NULL,
  `target_site_id` bigint unsigned NOT NULL,
  `state` varchar(32) NOT NULL,
  `target_vm_id` bigint unsigned DEFAULT NULL,
  `target_external_ref` varchar(1024) DEFAULT NULL,
  `target_vm_name` varchar(255) DEFAULT NULL,
  `target_power_state` varchar(32) DEFAULT NULL,
  `latest_restore_point_id` bigint unsigned DEFAULT NULL,
  `latest_target_ready_at` datetime DEFAULT NULL,
  `runtime_state_json` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_replica__uuid` (`uuid`),
  KEY `i_dr_replica__plan_removed` (`plan_id`, `removed`),
  KEY `i_dr_replica__target_vm_removed` (`target_vm_id`, `removed`),
  KEY `i_dr_replica__target_external_ref` (`target_external_ref`, `removed`),
  KEY `i_dr_replica__state_ready` (`state`, `latest_target_ready_at`),
  CONSTRAINT `fk_dr_replica__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_replica__target_site_id` FOREIGN KEY (`target_site_id`) REFERENCES `dr_site` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_replica__target_vm_id` FOREIGN KEY (`target_vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dr_replica__latest_restore_point_id` FOREIGN KEY (`latest_restore_point_id`) REFERENCES `dr_restore_point` (`id`) ON DELETE SET NULL
);
```

### 4.8 dr_replica_disk

```sql
CREATE TABLE `cloud`.`dr_replica_disk` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `replica_id` bigint unsigned NOT NULL,
  `source_volume_id` bigint unsigned DEFAULT NULL,
  `target_volume_id` bigint unsigned DEFAULT NULL,
  `target_disk_ref` varchar(2048) DEFAULT NULL,
  `state` varchar(32) NOT NULL,
  `format` varchar(32) DEFAULT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `metadata_json` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_replica_disk__uuid` (`uuid`),
  KEY `i_dr_replica_disk__replica` (`replica_id`),
  KEY `i_dr_replica_disk__target_volume` (`target_volume_id`),
  CONSTRAINT `fk_dr_replica_disk__replica_id` FOREIGN KEY (`replica_id`) REFERENCES `dr_replica` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_dr_replica_disk__source_volume_id` FOREIGN KEY (`source_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dr_replica_disk__target_volume_id` FOREIGN KEY (`target_volume_id`) REFERENCES `volumes` (`id`) ON DELETE SET NULL
);
```

### 4.9 dr_run

```sql
CREATE TABLE `cloud`.`dr_run` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned NOT NULL,
  `run_type` varchar(32) NOT NULL,
  `state` varchar(32) NOT NULL,
  `requested_by` varchar(255) DEFAULT NULL,
  `idempotency_key` varchar(255) DEFAULT NULL,
  `external_job_ref` varchar(1024) DEFAULT NULL,
  `current_step` varchar(255) DEFAULT NULL,
  `progress_percent` int DEFAULT NULL,
  `progress_message` text DEFAULT NULL,
  `error_code` varchar(128) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `rollback_context_json` text DEFAULT NULL,
  `created` datetime NOT NULL,
  `started` datetime DEFAULT NULL,
  `finished` datetime DEFAULT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_run__uuid` (`uuid`),
  KEY `i_dr_run__plan_type_state` (`plan_id`, `run_type`, `state`),
  KEY `i_dr_run__state_created` (`state`, `created`),
  KEY `i_dr_run__plan_idempotency` (`plan_id`, `idempotency_key`),
  CONSTRAINT `fk_dr_run__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE CASCADE
);
```

MySQL unique nullable behavior 때문에 `UNIQUE(plan_id, idempotency_key)`는 `NULL` 중복을 허용한다. 이 동작은 의도에 맞다. 단, non-null key는 service layer와 DB index 모두로 중복을 막는다.

### 4.10 dr_run_step

```sql
CREATE TABLE `cloud`.`dr_run_step` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `run_id` bigint unsigned NOT NULL,
  `step_no` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `state` varchar(32) NOT NULL,
  `progress_percent` int DEFAULT NULL,
  `summary` text DEFAULT NULL,
  `details_json` text DEFAULT NULL,
  `started` datetime DEFAULT NULL,
  `finished` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_run_step__uuid` (`uuid`),
  UNIQUE KEY `uk_dr_run_step__run_step_no` (`run_id`, `step_no`),
  KEY `i_dr_run_step__run_state` (`run_id`, `state`),
  CONSTRAINT `fk_dr_run_step__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE CASCADE
);
```

### 4.11 dr_event

```sql
CREATE TABLE `cloud`.`dr_event` (
  `id` bigint unsigned NOT NULL auto_increment,
  `uuid` varchar(40) NOT NULL,
  `plan_id` bigint unsigned DEFAULT NULL,
  `run_id` bigint unsigned DEFAULT NULL,
  `severity` varchar(32) NOT NULL,
  `source` varchar(32) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `message` text DEFAULT NULL,
  `details_json` text DEFAULT NULL,
  `external_ref` varchar(1024) DEFAULT NULL,
  `created` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dr_event__uuid` (`uuid`),
  KEY `i_dr_event__plan_run_created` (`plan_id`, `run_id`, `created`),
  KEY `i_dr_event__severity_created` (`severity`, `created`),
  CONSTRAINT `fk_dr_event__plan_id` FOREIGN KEY (`plan_id`) REFERENCES `dr_plan` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_dr_event__run_id` FOREIGN KEY (`run_id`) REFERENCES `dr_run` (`id`) ON DELETE SET NULL
);
```

## 5. VO class 설계

권장 위치:

`plugins/integrations/disaster-recovery/src/main/java/com/cloud/dr/`

| VO | Table | 주요 interface |
| --- | --- | --- |
| `DrSiteVO` | `dr_site` | `InternalIdentity` |
| `DrSitePairVO` | `dr_site_pair` | `InternalIdentity` |
| `DrPlanVO` | `dr_plan` | `InternalIdentity`, account/domain getter |
| `DrRestorePointVO` | `dr_restore_point` | `InternalIdentity` |
| `DrRestorePointArtifactVO` | `dr_restore_point_artifact` | `InternalIdentity` |
| `DrReplicaVO` | `dr_replica` | `InternalIdentity` |
| `DrReplicaDiskVO` | `dr_replica_disk` | `InternalIdentity` |
| `DrRunVO` | `dr_run` | `InternalIdentity` |
| `DrRunStepVO` | `dr_run_step` | `InternalIdentity` |
| `DrEventVO` | `dr_event` | `InternalIdentity` |

VO 규칙:

- constructor에서 `uuid = UUID.randomUUID().toString()` 생성
- enum은 Java enum field가 아니라 String field로 시작한다.
- JSON field는 `String`으로 저장하고 parser/helper에서 구조화한다.
- secret 값은 VO에 저장하지 않는다.
- `removed`가 null이 아니면 active 조회에서 제외한다.

## 6. DAO class 설계

권장 위치:

`plugins/integrations/disaster-recovery/src/main/java/com/cloud/dr/dao/`

| DAO interface | DAO impl |
| --- | --- |
| `DrSiteDao` | `DrSiteDaoImpl` |
| `DrSitePairDao` | `DrSitePairDaoImpl` |
| `DrPlanDao` | `DrPlanDaoImpl` |
| `DrRestorePointDao` | `DrRestorePointDaoImpl` |
| `DrRestorePointArtifactDao` | `DrRestorePointArtifactDaoImpl` |
| `DrReplicaDao` | `DrReplicaDaoImpl` |
| `DrReplicaDiskDao` | `DrReplicaDiskDaoImpl` |
| `DrRunDao` | `DrRunDaoImpl` |
| `DrRunStepDao` | `DrRunStepDaoImpl` |
| `DrEventDao` | `DrEventDaoImpl` |

DAO impl은 constructor에서 `SearchBuilder`를 정의한다.

필수 검색:

- uuid lookup
- active lookup with `removed IS NULL`
- plan state lookup
- active run lookup
- latest restore point lookup
- latest target-ready restore point lookup
- engine binding lookup
- event time range lookup

## 7. JSON field 규칙

초기 구현에서는 빠른 개발을 위해 JSON을 `text`로 저장한다.

단, 다음 값은 자주 조회하므로 column으로 승격되어 있다.

- `dr_plan.direction`
- `dr_plan.state`
- `dr_plan.engine_binding_type`
- `dr_plan.engine_binding_id`
- `dr_restore_point.state`
- `dr_restore_point.target_ready_at`
- `dr_replica.state`
- `dr_replica.latest_target_ready_at`
- `dr_run.run_type`
- `dr_run.state`
- `dr_run.progress_percent`
- `dr_event.severity`
- `dr_event.source`

JSON parser helper:

- `DrJsonHelper`
- invalid JSON은 API validation에서 거부
- DB에 저장된 invalid JSON은 response 생성 시 raw string을 그대로 노출하지 않고 warning을 남긴다.

## 8. 기존 모델 연결

| 기존 모델 | 신규 연결 | 규칙 |
| --- | --- | --- |
| `disaster_recovery_cluster` | `dr_site_pair.legacy_dr_cluster_id` | 자동 생성은 Phase 1에서 하지 않음 |
| `ftctl_protection` | `dr_plan.engine_binding_type=FTCTL`, `engine_binding_id` | 기존 성공 경로 보존 |
| `ftctl_protection_volume` | `dr_replica_disk.metadata_json` 또는 projection | 중복 저장 최소화 |
| `vm_instance_details ftctl.*` | `dr_replica.runtime_state_json` projection | source of truth는 FTCTL runtime |
| V2K workdir/manifest | `dr_run.external_job_ref`, `dr_run_step.details_json` | 사용자는 DrRun만 봄 |
| VMware MoRef | `target_external_ref`, `source_external_ref`, artifact ref | 안정 식별자로 저장 |

## 9. Retention과 cleanup

Retention 기본값:

| Table | 정책 |
| --- | --- |
| `dr_restore_point` | plan policy에 따라 만료 |
| `dr_restore_point_artifact` | restore point 만료 시 함께 정리 |
| `dr_run` | 장기 audit 보존, 기본 삭제 없음 |
| `dr_run_step` | run 보존 기간과 동일 |
| `dr_event` | UI event는 30일 기본 보존, audit event는 별도 정책 가능 |

Cleanup worker는 물리 artifact 제거와 DB soft delete를 분리한다.

- 물리 artifact 제거 성공 후 `removed` 갱신
- 물리 artifact 제거 실패 시 `state=FAILED`와 event 기록
- forced cleanup은 warning event를 남기고 DB 삭제/soft delete 범위를 명확히 표시

## 10. Upgrade 검증

SQL 검증:

- fresh schema 생성 성공
- upgrade SQL 적용 성공
- FK 생성 성공
- index 생성 성공
- table drop 순서 문제 없음

DAO 검증:

- 각 DAO bean 로딩
- uuid lookup
- active lookup
- soft delete 후 active lookup 제외
- idempotency key lookup
- latest restore point query
- latest target ready query

호환 검증:

- 기존 `disaster_recovery_cluster` API 영향 없음
- 기존 `ftctl_protection` table 영향 없음
- 기존 FTCTL 보호 row가 있어도 신규 table 생성/upgrade 성공

## 11. 구현 수용 기준

- `setup/db/create-schema.sql` fresh install 경로에 모든 신규 table이 있다.
- active upgrade 경로에 동일 schema 변경이 있다.
- 신규 VO/DAO가 Spring context에 등록된다.
- `git diff --check`와 DB bootstrap smoke가 통과한다.
- `createDrPlan` 전에 DAO로 source VM active plan 중복을 조회할 수 있다.
- `startDrSync` 전에 DAO로 active run 중복을 조회할 수 있다.
- `listDrPlans`가 account/domain/removed/state filter를 사용할 수 있다.
- `listDrEvents`가 plan/run/time filter를 사용할 수 있다.

## 12. AS-IS / TO-BE 요약

| 구분 | AS-IS | TO-BE |
| --- | --- | --- |
| DB 모델 | 기존 DR cluster, FTCTL protection 별도 | `dr_site`, `dr_plan`, `dr_run` 중심 공통 모델 |
| Fresh install | 기존 table만 생성 | `create-schema.sql`에 신규 table 포함 |
| Upgrade | 신규 DR table 없음 | active upgrade path에 DDL 포함 |
| Entity | 기존 VO/DAO만 존재 | 신규 VO/DAO/DaoImpl 추가 |
| 실행 상태 | async job 또는 engine별 상태 | `dr_run`, `dr_run_step`, `dr_event`로 통합 |
| Runtime projection | VM details 또는 engine event에 분산 | `dr_replica.runtime_state_json`와 event로 projection |
| Soft delete | 기존 모델별 개별 처리 | 모든 주요 table `removed` 기준 active 조회 |
