# Cross Hypervisor DR Cloud Backend Wiring Design

작성일: 2026-06-30

대상 브랜치: `feature/ftctl-cloud-integration`

상위 문서:

- [500-cross-hypervisor-dr-architecture-plan-20260630.md](500-cross-hypervisor-dr-architecture-plan-20260630.md)
- [501-cross-hypervisor-dr-domain-schema-design-20260630.md](501-cross-hypervisor-dr-domain-schema-design-20260630.md)
- [502-cross-hypervisor-dr-adapter-contract-design-20260630.md](502-cross-hypervisor-dr-adapter-contract-design-20260630.md)
- [503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md](503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md)
- [507-cross-hypervisor-dr-cloud-api-command-design-20260630.md](507-cross-hypervisor-dr-cloud-api-command-design-20260630.md)

## 1. 목적

이 문서는 `Cross Hypervisor DR` Cloud backend 구현 시 module, package, service, DAO, Spring bean, transaction, adapter registry를 어디에 배치할지 정의한다.

핵심 목표는 다음과 같다.

- 기존 `disaster-recovery`와 `ftctl-service` 성공 경로를 깨지 않는다.
- 공통 DR orchestrator는 특정 engine 구현을 직접 알지 않는다.
- FTCTL, V2K, VMware adapter는 공통 contract를 구현하고 registry를 통해 연결된다.
- Cloud API async job과 `DrRun`의 역할을 분리한다.
- 구현자가 임의의 위치에 class를 흩뿌리지 않도록 파일 단위 소유권을 정한다.

## 2. Module 소유권

공통 `Cross Hypervisor DR` backend는 기존 `plugins/integrations/disaster-recovery` module이 소유한다.

이유:

- 기존 Mold-to-Mold DR cluster API와 UI resource가 이미 이 module에 있다.
- 신규 `DrSite`, `DrPlan`은 기존 `DisasterRecoveryCluster`를 일반화하는 상위 모델이다.
- VMware target Phase 1은 FTCTL 없이도 구현 가능하다.
- FTCTL은 KVM-to-KVM engine adapter로 연결하면 된다.

권장 module 배치:

| 영역 | Module | 설명 |
| --- | --- | --- |
| 공통 API/도메인/orchestrator | `plugins/integrations/disaster-recovery` | 신규 DR 플랫폼 owner |
| 기존 DR cluster 호환 | `plugins/integrations/disaster-recovery` | 기존 API 유지 및 wrapper |
| FTCTL adapter 구현 | `plugins/integrations/ftctl-service` | 기존 FTCTL service를 감싸는 adapter |
| V2K adapter 구현 | `plugins/integrations/disaster-recovery` 또는 별도 V2K module | 초기에는 service wrapper로 시작 |
| VMware target adapter | `plugins/integrations/disaster-recovery` | vCenter/Mold VMware service 호출 |

`disaster-recovery`가 `ftctl-service`에 직접 compile dependency를 갖지 않는 것을 원칙으로 한다. FTCTL module이 공통 adapter interface에 의존해 optional bean을 제공하는 방식이 더 안전하다.

## 3. Package 구조

`plugins/integrations/disaster-recovery/src/main/java` 하위 권장 package:

| Package | 내용 |
| --- | --- |
| `com.cloud.dr` | service interface, public domain interface |
| `com.cloud.dr.dao` | 신규 DAO interface/impl |
| `com.cloud.dr.model` | enum, lightweight domain DTO |
| `com.cloud.dr.orchestrator` | plan/run orchestration |
| `com.cloud.dr.adapter` | 공통 adapter interfaces |
| `com.cloud.dr.adapter.vmware` | VMware source/target adapter |
| `com.cloud.dr.adapter.kvm` | KVM source/target adapter shell |
| `com.cloud.dr.adapter.v2k` | V2K wrapper adapter |
| `com.cloud.dr.response` | response factory/helper |
| `com.cloud.dr.event` | event 기록 helper |
| `com.cloud.dr.lock` | plan/run lock helper |

API package:

| Package | 내용 |
| --- | --- |
| `org.apache.cloudstack.api.command.admin.dr` | 신규 command class |
| `org.apache.cloudstack.api.response.dr` | 신규 response class |

## 4. Core service interfaces

### 4.1 DrSiteService

책임:

- `DrSite` CRUD
- credential reference 검증
- connectivity/capability check
- 기존 `DisasterRecoveryCluster`와의 호환 projection

필수 method:

```java
DrSiteResponse createDrSite(CreateDrSiteCmd cmd);
ListResponse<DrSiteResponse> listDrSites(ListDrSitesCmd cmd);
DrSiteResponse getDrSite(GetDrSiteCmd cmd);
DrSiteResponse updateDrSite(UpdateDrSiteCmd cmd);
boolean deleteDrSite(DeleteDrSiteCmd cmd);
DrSiteCheckResponse checkDrSite(CheckDrSiteCmd cmd);
```

### 4.2 DrPlanService

책임:

- `DrPlan` CRUD
- plan validation
- action eligibility 계산
- UI/API response 조립

필수 method:

```java
DrPlanResponse createDrPlan(CreateDrPlanCmd cmd);
ListResponse<DrPlanResponse> listDrPlans(ListDrPlansCmd cmd);
DrPlanResponse getDrPlan(GetDrPlanCmd cmd);
DrPlanResponse updateDrPlan(UpdateDrPlanCmd cmd);
DrPlanResponse enableDrPlan(EnableDrPlanCmd cmd);
DrPlanResponse disableDrPlan(DisableDrPlanCmd cmd);
DrPlanResponse deleteDrPlan(DeleteDrPlanCmd cmd);
Map<String, DrActionEligibility> getActionEligibility(DrPlanVO plan);
```

### 4.3 DrRunService

책임:

- action API를 `DrRun`으로 변환
- idempotency 처리
- run 조회/취소/manual-confirm
- progress/event projection

필수 method:

```java
DrRunResponse startSync(StartDrSyncCmd cmd);
DrRunResponse startTestFailover(StartDrTestFailoverCmd cmd);
DrRunResponse stopTestFailover(StopDrTestFailoverCmd cmd);
DrRunResponse startFailover(StartDrFailoverCmd cmd);
DrRunResponse confirmFenceClear(ConfirmDrFenceClearCmd cmd);
DrRunResponse startFailback(StartDrFailbackCmd cmd);
DrRunResponse startReprotect(StartDrReprotectCmd cmd);
DrRunResponse adoptReplica(AdoptDrReplicaCmd cmd);
DrRunResponse cancelRun(CancelDrRunCmd cmd);
DrRunResponse getDrRun(GetDrRunCmd cmd);
ListResponse<DrRunResponse> listDrRuns(ListDrRunsCmd cmd);
```

### 4.4 DrProjectionService

책임:

- engine runtime state를 `DrReplica.runtime_state_json`, `DrRunStep.details_json`, `DrEvent`로 투영
- stale projection과 engine failure를 구분
- UI refresh에서 필요한 summary를 빠르게 제공

필수 method:

```java
DrPlanRuntimeSummary refreshPlanProjection(long planId, boolean bestEffort);
DrReplicaRuntimeSummary refreshReplicaProjection(long replicaId, boolean bestEffort);
List<DrEventVO> relayEngineEvents(long planId, Long runId, int limit);
```

Projection 실패는 기본적으로 보호 해제나 cleanup을 트리거하지 않는다.

## 5. Orchestrator 구조

### 5.1 DrOrchestrator

`DrOrchestrator`는 action별 high-level flow를 가진다.

| Method | 설명 |
| --- | --- |
| `createRun` | idempotency key 확인 후 `DrRun` 생성 |
| `executeRun` | run type에 따라 adapter 호출 |
| `transitionPlan` | plan state 전이 |
| `transitionReplica` | replica state 전이 |
| `recordStep` | 사람이 읽을 수 있는 step 생성/갱신 |
| `recordEvent` | `DrEvent` 기록 |
| `handleFailure` | retryable/terminal/rollback required 분리 |

`DrOrchestrator`는 FTCTL shell command, vCenter SDK, V2K CLI를 직접 호출하지 않는다. 모든 engine 호출은 adapter를 통한다.

### 5.2 DrRunExecutor

Phase 1 권장:

- API async job 내부에서 짧은 작업을 직접 실행할 수 있다.
- `DrRun`은 DB에 항상 기록한다.
- skeleton VM 생성처럼 수분 내 종료되는 작업은 async job이 terminal까지 기다려도 된다.

Phase 2 이후:

- 장시간 data mover는 별도 executor/scheduler가 `QUEUED` run을 가져간다.
- executor는 active management node에서 한 번만 동작해야 한다.
- job handoff 후 UI는 `getDrRun`으로 progress를 본다.

### 5.3 DrAdapterRegistry

책임:

- `DrPlanDirection`, site type, engine binding에 맞는 adapter 조합 반환
- optional adapter 누락 시 명확한 `DR_ENGINE_UNAVAILABLE` 반환
- adapter capability를 `checkDrSite`와 `createDrPlan` validation에 제공

필수 method:

```java
DrSourceAdapter getSourceAdapter(DrSiteType siteType);
DrReplicationEngine getReplicationEngine(DrPlanDirection direction, String engineBindingType);
DrMaterializer getMaterializer(DrPlanDirection direction, String sourceFormat, String targetFormat);
DrTargetAdapter getTargetAdapter(DrSiteType siteType);
DrFencingAdapter getFencingAdapter(DrPlan plan);
DrAdapterBundle resolve(DrPlan plan);
```

Adapter registry는 startup 시 available adapter 목록을 log에 남긴다.

## 6. Adapter bean 배치

### 6.1 Common adapter interfaces

공통 interface는 `plugins/integrations/disaster-recovery`에 둔다.

- `DrSourceAdapter`
- `DrReplicationEngine`
- `DrMaterializer`
- `DrTargetAdapter`
- `DrFencingAdapter`
- `DrAdapterResult`
- `DrExecutionContext`

### 6.2 VMware adapters

초기 구현 위치:

- `com.cloud.dr.adapter.vmware.VmwareTargetAdapter`
- `com.cloud.dr.adapter.vmware.VmwareSourceAdapter`
- `com.cloud.dr.adapter.vmware.VmwareFencingAdapter`

Phase 1에서는 `VmwareTargetAdapter`가 우선이다.

### 6.3 KVM adapters

초기 구현 위치:

- `com.cloud.dr.adapter.kvm.KvmSourceAdapter`
- `com.cloud.dr.adapter.kvm.KvmTargetAdapter`
- `com.cloud.dr.adapter.kvm.KvmFencingAdapter`

KVM-to-KVM FTCTL 실제 실행은 FTCTL adapter가 담당한다. KVM adapter는 Cloud VM/volume/network metadata 조회를 맡는다.

### 6.4 FTCTL adapter

FTCTL adapter는 `plugins/integrations/ftctl-service`에 둘 것을 권장한다.

권장 class:

- `com.cloud.ftctl.dr.FtctlDrReplicationEngine`
- `com.cloud.ftctl.dr.FtctlDrFencingAdapter`
- `com.cloud.ftctl.dr.FtctlDrProjectionAdapter`

이 class들은 `disaster-recovery`의 공통 interface를 구현한다. 따라서 `ftctl-service` module이 `disaster-recovery` module의 adapter API에 compile dependency를 가질 수 있다. 반대 방향 dependency는 피한다.

FTCTL module이 설치되지 않은 환경에서는 KVM-to-KVM plan action이 `DR_ENGINE_UNAVAILABLE`로 실패해야 한다.

## 7. DAO wiring

신규 DAO:

| DAO | VO |
| --- | --- |
| `DrSiteDao` | `DrSiteVO` |
| `DrSitePairDao` | `DrSitePairVO` |
| `DrPlanDao` | `DrPlanVO` |
| `DrRestorePointDao` | `DrRestorePointVO` |
| `DrRestorePointArtifactDao` | `DrRestorePointArtifactVO` |
| `DrReplicaDao` | `DrReplicaVO` |
| `DrReplicaDiskDao` | `DrReplicaDiskVO` |
| `DrRunDao` | `DrRunVO` |
| `DrRunStepDao` | `DrRunStepVO` |
| `DrEventDao` | `DrEventVO` |

DAO impl은 `GenericDaoBase` 패턴을 따른다.

필수 search method:

| DAO | Method |
| --- | --- |
| `DrSiteDao` | `findByUuid`, `findActiveByName`, `listByTypeAndStatus` |
| `DrPlanDao` | `findByUuid`, `findActiveBySourceVmId`, `listByState`, `listBySourceTargetSite`, `findByEngineBinding` |
| `DrRestorePointDao` | `findLatestByPlanId`, `findLatestTargetReadyByPlanId`, `listByPlanId` |
| `DrReplicaDao` | `findActiveByPlanId`, `findByTargetVmId`, `findByTargetExternalRef` |
| `DrRunDao` | `findActiveByPlanId`, `findByIdempotencyKey`, `listByPlanId`, `listQueuedRuns` |
| `DrRunStepDao` | `listByRunId`, `findByRunIdAndStepName` |
| `DrEventDao` | `listByPlanRunAndTime`, `deleteExpired` |

## 8. Spring context

`plugins/integrations/disaster-recovery/src/main/resources/META-INF/cloudstack/disaster-recovery/spring-disaster-recovery-context.xml`에 신규 bean을 추가한다.

필수 bean:

```xml
<bean id="drSiteServiceImpl" class="com.cloud.dr.orchestrator.DrSiteServiceImpl" />
<bean id="drPlanServiceImpl" class="com.cloud.dr.orchestrator.DrPlanServiceImpl" />
<bean id="drRunServiceImpl" class="com.cloud.dr.orchestrator.DrRunServiceImpl" />
<bean id="drOrchestratorImpl" class="com.cloud.dr.orchestrator.DrOrchestratorImpl" />
<bean id="drRunExecutorImpl" class="com.cloud.dr.orchestrator.DrRunExecutorImpl" />
<bean id="drAdapterRegistryImpl" class="com.cloud.dr.adapter.DrAdapterRegistryImpl" />
<bean id="drProjectionServiceImpl" class="com.cloud.dr.orchestrator.DrProjectionServiceImpl" />
<bean id="drResponseGenerator" class="com.cloud.dr.response.DrResponseGenerator" />
```

DAO bean도 같은 context에 추가한다.

FTCTL adapter bean은 `plugins/integrations/ftctl-service/src/main/resources/META-INF/cloudstack/ftctl-service/spring-ftctl-service-context.xml`에 추가한다.

```xml
<bean id="ftctlDrReplicationEngine" class="com.cloud.ftctl.dr.FtctlDrReplicationEngine" />
<bean id="ftctlDrFencingAdapter" class="com.cloud.ftctl.dr.FtctlDrFencingAdapter" />
<bean id="ftctlDrProjectionAdapter" class="com.cloud.ftctl.dr.FtctlDrProjectionAdapter" />
```

## 9. Transaction boundary

### 9.1 createDrPlan

Transaction 안에서 수행:

1. source/target site row lock
2. active plan/protection conflict check
3. mapping JSON validation result 저장
4. `dr_plan` insert
5. optional `dr_replica` skeleton intent insert
6. event 기록

Transaction 밖에서 수행:

- remote endpoint connectivity check
- vCenter inventory refresh
- FTCTL runtime status read

외부 호출을 DB transaction 안에 오래 잡아두지 않는다.

### 9.2 start action

Transaction 안에서 수행:

1. `DrPlan` row lock
2. active run conflict check
3. idempotency key lookup
4. `DrRun` insert
5. first `DrRunStep` insert
6. plan state transition to transient state

Transaction 밖에서 수행:

- adapter preflight
- engine command
- long-running polling

### 9.3 failure handling

Adapter failure 발생 시:

- `DrRun.state=FAILED`
- current step `FAILED`
- `DrPlan.state=ERROR` 또는 이전 stable state 유지
- `DrEvent.severity=ERROR`
- retryable 여부 저장

Projection refresh failure는 기본적으로 `DrPlan.state=ERROR`로 바꾸지 않는다. `DrPlan.last_error_code=DR_PROJECTION_STALE` 같은 보조 상태만 갱신한다.

## 10. Locking

Plan 단위 lock은 두 겹으로 둔다.

1. DB row lock: `DrPlanDao.lockRow(planId, true)` 계열
2. active run guard: 같은 plan에 terminal이 아닌 `DrRun`이 있으면 destructive run 생성 거부

Destructive run:

- `SYNC`
- `TEST_FAILOVER`
- `TEST_CLEANUP`
- `FAILOVER`
- `FAILBACK`
- `REPROTECT`
- `ADOPT`
- `DELETE`

Non-destructive run:

- `CHECK`
- `PROJECTION_REFRESH`
- `EVENT_RELAY`

Non-destructive run도 engine별 lock과 충돌할 수 있으므로 adapter가 `DR_ENGINE_BUSY`를 반환할 수 있어야 한다.

## 11. Existing API compatibility wiring

기존 `DisasterRecoveryClusterServiceImpl`는 바로 제거하지 않는다.

호환 전략:

- 기존 API는 기존 테이블을 계속 사용한다.
- 신규 `DrSitePair`는 기존 `disaster_recovery_cluster.id`를 `legacy_dr_cluster_id`로 참조할 수 있다.
- 기존 cluster API가 신규 테이블을 자동 생성할지는 Phase 1에서 비활성으로 둔다.
- 별도 migration/import command를 만들 경우에만 기존 cluster를 `DrSitePair`로 투영한다.

기존 FTCTL API도 유지한다.

호환 전략:

- 기존 FTCTL UI는 기존 `FtctlService`를 계속 호출한다.
- 신규 DR UI는 `DrPlan` API를 호출한다.
- 같은 VM에 active `DrPlan`과 active `ftctl_protection`이 중복 생성되지 않도록 양쪽 service에서 guard를 둔다.
- 기존 FTCTL 보호를 `DrPlan`으로 연결하려면 명시적 import action이 필요하다.

## 12. Response generation

`DrResponseGenerator`는 다음을 담당한다.

- VO -> API response 변환
- uuid 노출
- secret masking
- action eligibility 포함
- current run/latest run summary 조립
- target-ready RPO 계산
- stale projection warning 포함

API command가 직접 여러 DAO를 호출해 response를 조립하지 않는다.

## 13. Test coverage

필수 unit test:

- `DrPlanServiceImplTest`
- `DrRunServiceImplTest`
- `DrOrchestratorImplTest`
- `DrAdapterRegistryImplTest`
- `DrResponseGeneratorTest`
- `DrSiteServiceImplTest`

필수 scenario:

- 같은 source VM에 중복 plan 생성 거부
- idempotency key 재요청 시 기존 run 반환
- active run 존재 시 destructive action 거부
- projection stale이 plan error로 과격하게 전이되지 않음
- FTCTL adapter missing 시 KVM-to-KVM action이 `DR_ENGINE_UNAVAILABLE`
- VMware target skeleton 생성 실패 시 run/step/event에 원인 보존
- async job success와 `DrRun` terminal state 구분

## 14. 구현 순서

1. VO/DAO/DDL 추가
2. response class와 response generator 추가
3. `DrSiteService` CRUD 추가
4. `DrPlanService` CRUD와 action eligibility 추가
5. `DrRunService`와 idempotency guard 추가
6. `DrAdapterRegistry`와 Noop/VMware target adapter 추가
7. `DrOrchestrator` sync skeleton flow 추가
8. command class 추가
9. UI API wrapper 연결
10. FTCTL adapter bridge 추가

## 15. AS-IS / TO-BE 요약

| 구분 | AS-IS | TO-BE |
| --- | --- | --- |
| Backend owner | 기존 DR cluster service와 FTCTL service가 분리 | `disaster-recovery`가 공통 DR orchestrator 소유 |
| Engine 호출 | 기능별 service가 직접 호출 | adapter registry를 통해 engine 호출 |
| FTCTL 연계 | UI/API가 FTCTL service 직접 사용 | 신규 DR는 FTCTL adapter를 통해 사용, 기존 FTCTL API 유지 |
| 상태 실행 | Cloud async job 중심 | Cloud async job + `DrRun` source of truth |
| Response 조립 | command/service별 개별 조립 | `DrResponseGenerator`로 통일 |
| Lock | 기능별 guard | plan row lock + active run guard |
| Spring wiring | 기존 DR/FTCTL bean만 존재 | DR service/DAO/registry/executor/adapter bean 추가 |
