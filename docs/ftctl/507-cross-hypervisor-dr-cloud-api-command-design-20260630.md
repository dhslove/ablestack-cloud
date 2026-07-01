# Cross Hypervisor DR Cloud API Command Design

작성일: 2026-06-30

대상 브랜치: `feature/ftctl-cloud-integration`

상위 문서:

- [500-cross-hypervisor-dr-architecture-plan-20260630.md](500-cross-hypervisor-dr-architecture-plan-20260630.md)
- [501-cross-hypervisor-dr-domain-schema-design-20260630.md](501-cross-hypervisor-dr-domain-schema-design-20260630.md)
- [502-cross-hypervisor-dr-adapter-contract-design-20260630.md](502-cross-hypervisor-dr-adapter-contract-design-20260630.md)
- [503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md](503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md)
- [506-cross-hypervisor-dr-cloud-ui-design-20260630.md](506-cross-hypervisor-dr-cloud-ui-design-20260630.md)

## 1. 목적

이 문서는 `Cross Hypervisor DR`의 Cloud API command, response, 권한, async job, 오류 응답 규칙을 구현 가능한 수준으로 정의한다.

기존 문서의 API 후보 목록은 방향성을 설명하기에 충분하지만, 구현자가 `Cmd`, `Response`, service method, UI polling을 바로 만들기에는 부족하다. 이 문서는 그 빈틈을 메운다.

## 2. API 배치 원칙

신규 API는 기존 `disaster-recovery` integration plugin 아래에 둔다.

권장 package:

| 용도 | package |
| --- | --- |
| command | `org.apache.cloudstack.api.command.admin.dr` |
| response | `org.apache.cloudstack.api.response.dr` |
| service interface | `com.cloud.dr` |
| service impl | `com.cloud.dr.orchestrator` 또는 `com.cloud.dr.plan` |

초기 범위는 `RoleType.Admin` 전용으로 둔다. `dr_plan.account_id`, `domain_id`는 향후 account/user API 확장을 위해 저장하지만, Phase 1 public command는 admin command로 시작한다.

API command는 다음 규칙을 따른다.

- 조회 API는 `BaseListCmd` 또는 `BaseCmd`를 사용한다.
- 상태 변경 API는 `BaseAsyncCmd`를 사용한다.
- 장시간 data mover가 필요한 작업은 `DrRun`을 만들고 `jobid`와 `runid`를 모두 UI에 노출한다.
- Cloud async job 성공은 "요청 접수"만 의미해서는 안 된다. 작업을 background `DrRun`으로 넘기는 경우 response에 `runstate`와 `accepted=true`를 명확히 표시한다.
- 작업이 API command 실행 중 실패하면 async job을 실패로 끝내고 `DrRun.state=FAILED`를 남긴다.
- 작업이 command 이후 별도 worker에서 실패하면 `getDrRun`과 `listDrEvents`에서 실패가 반드시 보인다.

## 3. 공통 API resource와 event type

구현 시 `ApiCommandResourceType`에 신규 값을 추가하는 것을 권장한다.

| Resource | 사용처 |
| --- | --- |
| `DrSite` | site create/update/delete/check |
| `DrPlan` | plan create/update/delete/action |
| `DrRun` | run cancel/manual-confirm/progress |
| `DrReplica` | replica adopt/test/cleanup |
| `DrRestorePoint` | restore point 조회/선택 |

기존 enum 추가가 부담스럽거나 상위 API 호환 문제가 있으면 Phase 1에서는 `ApiCommandResourceType.DisasterRecoveryCluster`를 재사용할 수 있다. 단, event description에는 신규 entity type과 uuid를 반드시 포함한다.

신규 event type은 `com.cloud.dr.cluster.DisasterRecoveryClusterEventTypes`를 확장하거나 별도 `CrossHypervisorDrEventTypes`로 분리한다.

| Event type | 발생 command |
| --- | --- |
| `EVENT_DR_SITE_CREATE` | `createDrSite` |
| `EVENT_DR_SITE_UPDATE` | `updateDrSite`, `checkDrSite` |
| `EVENT_DR_SITE_DELETE` | `deleteDrSite` |
| `EVENT_DR_PLAN_CREATE` | `createDrPlan` |
| `EVENT_DR_PLAN_UPDATE` | `updateDrPlan`, `enableDrPlan`, `disableDrPlan` |
| `EVENT_DR_PLAN_DELETE` | `deleteDrPlan` |
| `EVENT_DR_PLAN_SYNC` | `startDrSync` |
| `EVENT_DR_PLAN_TEST_FAILOVER` | `startDrTestFailover`, `stopDrTestFailover` |
| `EVENT_DR_PLAN_FAILOVER` | `startDrFailover`, `adoptDrReplica` |
| `EVENT_DR_PLAN_FAILBACK` | `startDrFailback` |
| `EVENT_DR_PLAN_REPROTECT` | `startDrReprotect` |
| `EVENT_DR_RUN_CANCEL` | `cancelDrRun` |
| `EVENT_DR_FENCE_CONFIRM` | `confirmDrFenceClear` |

## 4. Command class 목록

### 4.1 DrSite commands

| API | Command class | Base class | Response | 설명 |
| --- | --- | --- | --- | --- |
| `createDrSite` | `CreateDrSiteCmd` | `BaseAsyncCmd` | `DrSiteResponse` | DR endpoint 등록 |
| `listDrSites` | `ListDrSitesCmd` | `BaseListCmd` | `ListResponse<DrSiteResponse>` | site 목록/필터 |
| `getDrSite` | `GetDrSiteCmd` | `BaseCmd` | `DrSiteResponse` | 단일 site 상세 |
| `updateDrSite` | `UpdateDrSiteCmd` | `BaseAsyncCmd` | `DrSiteResponse` | endpoint, credential ref, capability 갱신 |
| `deleteDrSite` | `DeleteDrSiteCmd` | `BaseAsyncCmd` | `SuccessResponse` 또는 `DrSiteResponse` | site soft delete |
| `checkDrSite` | `CheckDrSiteCmd` | `BaseAsyncCmd` | `DrSiteCheckResponse` | connectivity/capability preflight |

`createDrSite` parameters:

| Parameter | Required | 설명 |
| --- | --- | --- |
| `name` | yes | site 이름. active site 이름 중복 금지 |
| `description` | no | 설명 |
| `siteType` | yes | `MOLD_KVM`, `MOLD_VMWARE`, `VMWARE_DIRECT` |
| `hypervisorType` | yes | `KVM`, `VMWARE` |
| `endpoint` | conditional | remote Mold URL 또는 vCenter URL |
| `credentialRef` | conditional | secret 직접 값이 아니라 credential reference |
| `zoneId` | conditional | local Mold zone 연결 시 사용 |
| `vmwareDcId` | conditional | Mold-managed VMware datacenter 연결 시 사용 |
| `certificatePolicy` | no | `STRICT`, `TRUST_ON_FIRST_USE`, `INSECURE_ALLOWED` |
| `capabilityJson` | no | 사전 수집 capability snapshot |

`checkDrSite` result는 `DrSite.status`를 무조건 바꾸지 않는다. check가 성공하면 `last_check_*`를 갱신하고, 명시적 `persistStatus=true`가 있을 때만 status를 갱신한다.

### 4.2 DrPlan commands

| API | Command class | Base class | Response | 설명 |
| --- | --- | --- | --- | --- |
| `createDrPlan` | `CreateDrPlanCmd` | `BaseAsyncCmd` | `DrPlanResponse` | VM 보호 계획 생성 |
| `listDrPlans` | `ListDrPlansCmd` | `BaseListCmd` | `ListResponse<DrPlanResponse>` | plan 목록/필터 |
| `getDrPlan` | `GetDrPlanCmd` | `BaseCmd` | `DrPlanResponse` | 단일 plan 상세 |
| `updateDrPlan` | `UpdateDrPlanCmd` | `BaseAsyncCmd` | `DrPlanResponse` | mapping/policy 수정 |
| `enableDrPlan` | `EnableDrPlanCmd` | `BaseAsyncCmd` | `DrPlanResponse` | 보호 활성화 |
| `disableDrPlan` | `DisableDrPlanCmd` | `BaseAsyncCmd` | `DrPlanResponse` | 보호 비활성화 |
| `deleteDrPlan` | `DeleteDrPlanCmd` | `BaseAsyncCmd` | `DrPlanResponse` | plan 제거 및 선택 cleanup |

`createDrPlan` parameters:

| Parameter | Required | 설명 |
| --- | --- | --- |
| `name` | yes | plan 이름 |
| `sourceSiteId` | yes | source site uuid |
| `targetSiteId` | yes | target site uuid |
| `sourceVmId` | conditional | Mold/KVM source VM uuid |
| `sourceExternalRef` | conditional | VMware source MoRef 등 |
| `direction` | yes | `KVM_TO_KVM`, `KVM_TO_VMWARE`, `VMWARE_TO_VMWARE`, `VMWARE_TO_KVM` |
| `rpoSeconds` | no | 목표 RPO |
| `rtoSeconds` | no | 목표 RTO |
| `scheduleJson` | no | sync schedule |
| `storageMappingJson` | conditional | disk/datastore/pool mapping |
| `networkMappingJson` | conditional | NIC/network/portgroup mapping |
| `computeMappingJson` | no | resource pool/host/service offering mapping |
| `fencingPolicyJson` | no | manual/automatic fencing policy |
| `quiescePolicyJson` | no | QGA/VMware Tools consistency policy |
| `enable` | no | 생성 후 즉시 enable |
| `dryRun` | no | preflight만 수행 |
| `idempotencyKey` | no | retry 중복 방지 |

필수 validation:

- source와 target site가 모두 active여야 한다.
- direction과 site type 조합이 `DrAdapterRegistry`에서 지원되어야 한다.
- 동일 source VM에 active `DrPlan` 또는 active `ftctl_protection`이 있으면 기본적으로 거부한다.
- `KVM_TO_KVM`에서 기존 FTCTL 보호를 연결하는 경우 `importExistingFtctlProtection=true` 같은 명시 parameter가 있어야 한다.
- mapping JSON은 문자열로 받더라도 backend에서 구조 검증을 수행해야 한다.

`listDrPlans` filters:

| Filter | 설명 |
| --- | --- |
| `id` | plan uuid |
| `name` | 이름 like 검색 |
| `sourceSiteId` | source site uuid |
| `targetSiteId` | target site uuid |
| `sourceVmId` | Cloud VM uuid |
| `direction` | plan direction |
| `state` | plan state |
| `replicaState` | latest replica state |
| `targetReady` | latest target-ready restore point 존재 여부 |
| `rpoExceeded` | target-ready RPO threshold 초과 |
| `engineBindingType` | `FTCTL`, `V2K`, `VMWARE_NATIVE`, `KVM_SNAPSHOT` |
| `page`, `pagesize` | `BaseListCmd` 표준 pagination |

### 4.3 Action commands

| API | Command class | Base class | Run type | Response |
| --- | --- | --- | --- | --- |
| `startDrSync` | `StartDrSyncCmd` | `BaseAsyncCmd` | `SYNC` | `DrRunResponse` |
| `startDrTestFailover` | `StartDrTestFailoverCmd` | `BaseAsyncCmd` | `TEST_FAILOVER` | `DrRunResponse` |
| `stopDrTestFailover` | `StopDrTestFailoverCmd` | `BaseAsyncCmd` | `TEST_CLEANUP` | `DrRunResponse` |
| `startDrFailover` | `StartDrFailoverCmd` | `BaseAsyncCmd` | `FAILOVER` | `DrRunResponse` |
| `confirmDrFenceClear` | `ConfirmDrFenceClearCmd` | `BaseAsyncCmd` | `FENCE_CONFIRM` | `DrRunResponse` |
| `startDrFailback` | `StartDrFailbackCmd` | `BaseAsyncCmd` | `FAILBACK` | `DrRunResponse` |
| `startDrReprotect` | `StartDrReprotectCmd` | `BaseAsyncCmd` | `REPROTECT` | `DrRunResponse` |
| `adoptDrReplica` | `AdoptDrReplicaCmd` | `BaseAsyncCmd` | `ADOPT` | `DrRunResponse` |
| `cancelDrRun` | `CancelDrRunCmd` | `BaseAsyncCmd` | existing run | `DrRunResponse` |

공통 action parameters:

| Parameter | Required | 설명 |
| --- | --- | --- |
| `planId` | yes | plan uuid |
| `restorePointId` | conditional | failover/test 대상 restore point |
| `replicaId` | conditional | adopt/test cleanup 대상 replica |
| `idempotencyKey` | no | 중복 실행 방지 |
| `dryRun` | no | 실행 대신 preflight |
| `force` | no | forced cleanup/adopt 등 위험 액션 |
| `acknowledgement` | conditional | 위험 액션 확인 문구 |
| `reason` | no | operator가 남긴 실행 사유 |

위험 action은 `force=true`만으로 실행하지 않는다. `acknowledgement`가 action별 expected phrase와 일치해야 한다.

`startDrFailover` 추가 parameters:

| Parameter | 설명 |
| --- | --- |
| `disaster` | source controller가 제어 불가한 재해 전환 여부 |
| `skipSourceFenceRequest` | pre-fenced 상태일 때만 허용 |
| `testNetworkOnly` | production network 연결 없이 검증 |

`startDrFailback`은 source-controller failback 전용이다. source controller가 unavailable이면 `adoptDrReplica` 또는 `startDrFailover(disaster=true)`로 분리한다.

### 4.4 Query commands

| API | Command class | Base class | Response |
| --- | --- | --- | --- |
| `listDrRestorePoints` | `ListDrRestorePointsCmd` | `BaseListCmd` | `ListResponse<DrRestorePointResponse>` |
| `listDrReplicas` | `ListDrReplicasCmd` | `BaseListCmd` | `ListResponse<DrReplicaResponse>` |
| `listDrRuns` | `ListDrRunsCmd` | `BaseListCmd` | `ListResponse<DrRunResponse>` |
| `getDrRun` | `GetDrRunCmd` | `BaseCmd` | `DrRunResponse` |
| `listDrRunSteps` | `ListDrRunStepsCmd` | `BaseListCmd` | `ListResponse<DrRunStepResponse>` |
| `listDrEvents` | `ListDrEventsCmd` | `BaseListCmd` | `ListResponse<DrEventResponse>` |

`listDrEvents` filters:

- `planId`
- `runId`
- `severity`
- `source`
- `since`
- `until`
- `page`
- `pagesize`

`listDrEvents`는 qemu/FTCTL raw event 전체를 무제한 반환하지 않는다. response는 summary 중심이며 raw payload는 `includeDetails=true`일 때만 반환한다.

## 5. Response object 설계

### 5.1 DrSiteResponse

필수 field:

- `id`
- `name`
- `description`
- `siteType`
- `hypervisorType`
- `endpoint`
- `zoneId`
- `vmwareDcId`
- `status`
- `lastCheckResult`
- `lastCheckMessage`
- `lastCheckTime`
- `capabilities`
- `created`
- `removed`

표시 금지:

- password
- secret key
- API key 원문
- private key 원문
- vCenter session token

credential은 `credentialAlias`, `credentialRefMasked`, `credentialUpdated` 정도만 표시한다.

### 5.2 DrPlanResponse

필수 field:

- `id`
- `name`
- `description`
- `state`
- `direction`
- `sourceSite`
- `targetSite`
- `sourceVmId`
- `sourceVmName`
- `sourceExternalRef`
- `rpoSeconds`
- `rtoSeconds`
- `sourceLatestRestorePoint`
- `targetReadyRestorePoint`
- `sourceRpoAgeSeconds`
- `targetReadyRpoAgeSeconds`
- `replica`
- `currentRun`
- `lastRun`
- `actionEligibility`
- `engineBindingType`
- `engineBindingId`
- `created`
- `removed`

`actionEligibility`는 UI가 임의로 상태 조합을 재해석하지 않도록 backend가 제공한다.

예시:

```json
{
  "failover": {
    "enabled": false,
    "reasonCode": "DR_TARGET_NOT_READY",
    "message": "No target-ready restore point exists."
  }
}
```

### 5.3 DrRunResponse

필수 field:

- `id`
- `planId`
- `runType`
- `state`
- `requestedBy`
- `idempotencyKey`
- `externalJobRef`
- `currentStep`
- `progressPercent`
- `progressMessage`
- `errorCode`
- `errorMessage`
- `retryable`
- `created`
- `started`
- `finished`
- `steps`

Cloud async job id와 external engine job id는 분리한다.

| Field | 의미 |
| --- | --- |
| Cloud `jobid` | CloudStack API async job id |
| `DrRun.id` | DR orchestrator 실행 id |
| `externalJobRef` | VMware task MoRef, V2K workdir, FTCTL event ref 등 |

### 5.4 DrReplicaResponse

필수 field:

- `id`
- `planId`
- `state`
- `targetSiteId`
- `targetVmId`
- `targetVmName`
- `targetExternalRef`
- `targetPowerState`
- `latestRestorePointId`
- `latestTargetReadyAt`
- `diskSummary`
- `networkSummary`
- `runtimeState`

### 5.5 DrRestorePointResponse

필수 field:

- `id`
- `planId`
- `sequenceNo`
- `state`
- `sourceCapturedAt`
- `sourceReadyAt`
- `targetReadyAt`
- `expiresAt`
- `sizeBytes`
- `sourceLagSeconds`
- `targetLagSeconds`
- `artifactCount`
- `artifacts`

## 6. Async job와 DrRun 관계

### 6.1 기본 정책

`BaseAsyncCmd`는 CloudStack API의 사용자 경험과 audit를 담당하고, `DrRun`은 DR runtime 실행 상태의 source of truth가 된다.

권장 흐름:

1. API command validation
2. `DrRun` 생성 또는 idempotency hit 조회
3. Cloud async job event 기록
4. 짧은 preflight는 command 안에서 수행
5. 장시간 작업은 `DrRunExecutor`가 수행
6. response는 `DrRunResponse` 반환
7. UI는 `queryAsyncJobResult`와 `getDrRun`을 모두 확인

### 6.2 job result 규칙

| 상황 | Cloud async job result | DrRun state |
| --- | --- | --- |
| validation 실패 | failed | run 없음 또는 `FAILED` |
| preflight 실패 | failed | `FAILED` |
| run 생성 후 worker handoff 성공 | success with `accepted=true` | `QUEUED` 또는 `RUNNING` |
| command가 terminal까지 기다리는 짧은 작업 성공 | success | `SUCCEEDED` |
| worker에서 나중에 실패 | async job은 기존 결과 유지 | `FAILED`, event에 원인 |

UI는 `accepted=true`인 job success를 최종 보호 성공으로 표시하면 안 된다.

## 7. 오류 응답

표준 오류 field:

- `errorCode`
- `errorText`
- `retryable`
- `actionable`
- `runId`
- `stepName`
- `externalJobRef`

주요 오류 코드:

| Code | 의미 | Retry |
| --- | --- | --- |
| `DR_SITE_NOT_FOUND` | site 없음 | no |
| `DR_SITE_UNAVAILABLE` | endpoint 연결 불가 | yes |
| `DR_CREDENTIAL_INVALID` | credential 오류 | no |
| `DR_PLAN_NOT_FOUND` | plan 없음 | no |
| `DR_PLAN_STATE_INVALID` | 현재 상태에서 action 불가 | no |
| `DR_RUN_ALREADY_ACTIVE` | 같은 plan에 active run 존재 | yes |
| `DR_IDEMPOTENCY_REPLAY` | 기존 run 재사용 | yes |
| `DR_TARGET_MAPPING_INVALID` | datastore/network mapping 오류 | no |
| `DR_TARGET_NOT_READY` | target-ready restore point 없음 | no |
| `DR_ENGINE_BUSY` | FTCTL/V2K/VMware task busy | yes |
| `DR_FENCE_REQUIRED` | manual fencing 필요 | no |
| `DR_SOURCE_CONTROLLER_UNAVAILABLE` | source-controller failback 불가 | no |
| `DR_PROJECTION_STALE` | 상태 projection 갱신 실패 | yes |
| `DR_PERMISSION_DENIED` | 권한 부족 | no |

## 8. 기존 API 호환

기존 API는 유지한다.

| 기존 API | 신규 관계 |
| --- | --- |
| `createDisasterRecoveryCluster` | `DrSitePair` 생성 wrapper 후보 |
| `getDisasterRecoveryClusterList` | 기존 UI 유지. 신규 UI는 `listDrSites/listDrPlans` 사용 |
| `promoteDisasterRecoveryCluster` | 신규 `startDrFailover`와 의미가 다르므로 바로 대체하지 않음 |
| `resyncDisasterRecoveryCluster` | plan 단위 `startDrSync`로 점진 전환 |
| FTCTL `registerFtctlProtection` | KVM-to-KVM `createDrPlan` 내부 adapter 또는 import 경로 |
| FTCTL `failoverFtctlProtection` | `startDrFailover` 내부 adapter |
| FTCTL `failbackFtctlProtection` | source-controller `startDrFailback` 내부 adapter |
| FTCTL `adoptFtctlDrReplica` | replica-controller `adoptDrReplica` 내부 adapter |

기존 API와 신규 API가 같은 VM을 동시에 조작하지 않도록 `createDrPlan`과 FTCTL register path 양쪽에서 active protection/plan conflict를 확인한다.

## 9. API 수용 기준

- 모든 신규 command는 command class, response class, service method가 1:1로 추적된다.
- 모든 상태 변경 command는 `BaseAsyncCmd` 또는 명시적 예외 사유를 가진다.
- 모든 list command는 pagination을 지원한다.
- 모든 action command는 `idempotencyKey`를 받을 수 있다.
- UI는 `jobid`, `runid`, `runstate`를 모두 받을 수 있다.
- 위험 action은 `force`와 `acknowledgement`를 함께 요구한다.
- API response에 secret 원문이 포함되지 않는다.
- 기존 `DisasterRecoveryCluster`와 FTCTL API는 제거하거나 semantic을 바꾸지 않는다.

## 10. AS-IS / TO-BE 요약

| 구분 | AS-IS | TO-BE |
| --- | --- | --- |
| API 표면 | 기존 DR cluster API와 FTCTL API가 기능별로 분리 | `DrSite`, `DrPlan`, `DrRun`, `DrReplica`, `DrRestorePoint` API로 공통화 |
| action 실행 | 기능별 command가 각 service를 직접 호출 | action command가 `DrRunService`를 통해 run을 만들고 orchestrator에 위임 |
| async 결과 | Cloud async job 성공만 보고 최종 성공으로 오해 가능 | `jobid`, `runid`, `runstate`, `accepted`를 함께 반환 |
| 권한/resource | 기존 resource type으로 충분히 표현하기 어려움 | `DrSite`, `DrPlan`, `DrRun`, `DrReplica`, `DrRestorePoint` resource type 추가 |
| 오류 응답 | 엔진별 메시지가 섞임 | 표준 `DR_*` error code, retryable, actionable, stepName으로 표현 |
