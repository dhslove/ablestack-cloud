# Cross Hypervisor DR FTCTL Runtime Contract Design

작성일: 2026-06-30

대상 브랜치: `feature/ftctl-cloud-integration`

상위 문서:

- [505-cross-hypervisor-dr-ftctl-v2k-integration-design-20260630.md](505-cross-hypervisor-dr-ftctl-v2k-integration-design-20260630.md)
- [507-cross-hypervisor-dr-cloud-api-command-design-20260630.md](507-cross-hypervisor-dr-cloud-api-command-design-20260630.md)
- [508-cross-hypervisor-dr-cloud-backend-wiring-design-20260630.md](508-cross-hypervisor-dr-cloud-backend-wiring-design-20260630.md)
- [509-cross-hypervisor-dr-db-upgrade-and-entity-design-20260630.md](509-cross-hypervisor-dr-db-upgrade-and-entity-design-20260630.md)

## 1. 목적

이 문서는 `Cross Hypervisor DR`에서 Cloud backend와 qemu-side FTCTL runtime 사이의 contract를 정의한다.

기존 FTCTL은 이미 다음 KVM-to-KVM 보호 경로를 검증했다.

- `rbd -> rbd`
- `rbd -> qcow2`
- `qcow2 -> rbd`
- `qcow2 -> qcow2`

신규 DR orchestrator는 이 성공 경로를 변경하지 않는다. Cloud는 FTCTL을 공통 `DrPlan`/`DrRun` 모델 아래로 감싸되, qemu FTCTL runtime의 profile, lock, event, blockcopy, failover/failback 실행 소유권을 침범하지 않는다.

## 2. 책임 경계

| 영역 | Cloud DR | qemu FTCTL |
| --- | --- | --- |
| 사용자 API | `DrPlan`, `DrRun` API 제공 | 직접 제공하지 않음 |
| DB 상태 | plan/run/replica/restore point projection 저장 | Cloud DB 접근 없음 |
| VM/volume 준비 | Cloud VM/volume/resource 생성 | 준비된 target을 사용 |
| runtime profile | profile 생성 요청/metadata 전달 | profile 파일의 source of truth |
| blockcopy/xcolo | action 요청과 결과 projection | 실제 qemu/libvirt/blockcopy/xcolo 수행 |
| failover/failback | command orchestration, Cloud VM lifecycle | runtime finalize, mirror/fence state |
| events | FTCTL events를 읽어 `DrEvent`로 relay | `events.log` JSONL 작성 |
| cleanup | Cloud DB row와 Cloud-created resource cleanup | FTCTL runtime/profile/lock cleanup |

Cloud는 FTCTL profile/state 파일을 DB projection으로 재생성하거나 덮어쓰지 않는다. Projection 실패는 runtime 실패로 단정하지 않는다.

## 3. Cloud 호출 경로

Cloud UI와 API는 qemu host 또는 libvirt를 직접 호출하지 않는다.

호출 경로:

```text
UI
  -> Cloud API Cmd
  -> DrRunService / DrOrchestrator
  -> FtctlDrReplicationEngine or FtctlDrFencingAdapter
  -> existing FtctlService
  -> AgentManager / Mold Agent command
  -> qemu-side FTCTL script
  -> events.log / JSON response
```

금지:

- UI에서 host SSH/libvirt/qemu 직접 호출
- Cloud DB projection으로 FTCTL host state 파일 수정
- DrRun scheduler가 FTCTL timer/reconcile과 같은 VM을 동시에 조작
- projection refresh 실패를 이유로 FTCTL forced cleanup 실행

## 4. qemu-side 관련 파일

qemu repository 기준 구현 영향 가능성이 있는 파일:

| 파일 | 역할 | 변경 원칙 |
| --- | --- | --- |
| `bin/ablestack_vm_ftctl.sh` | CLI entrypoint | 기존 command 호환 유지 |
| `lib/ftctl/profile.sh` | profile load/validation | optional metadata만 추가 |
| `lib/ftctl/events.sh` | events log writer | event field 안정화 |
| `lib/ftctl/state.sh` | runtime state | Cloud projection으로 덮지 않음 |
| `lib/ftctl/orchestrator.sh` | protect/reconcile flow | 기존 성공 경로 변경 금지 |
| `lib/ftctl/blockcopy.sh` | blockcopy backend | rbd/qcow2 성공 경로 보존 |
| `lib/ftctl/failover.sh` | failover/failback | controller boundary 유지 |
| `lib/ftctl/fencing.sh` | manual/automatic fence | confirm/clear event 유지 |
| `lib/ftctl/standby.sh` | standby materialization | Cloud-created target 우선 |
| `lib/ftctl/xcolo.sh` | xcolo runtime | port slot/graph 성공 경로 보존 |

이 문서는 Cloud 설계 문서이지만, qemu-side 구현 변경이 필요한 경우 위 파일들의 contract를 기준으로 별도 qemu 문서 또는 patch를 작성한다.

## 5. FTCTL adapter mapping

Cloud backend class:

- `com.cloud.ftctl.dr.FtctlDrReplicationEngine`
- `com.cloud.ftctl.dr.FtctlDrFencingAdapter`
- `com.cloud.ftctl.dr.FtctlDrProjectionAdapter`

Mapping:

| Dr action | Existing FTCTL API/action | qemu runtime 의미 |
| --- | --- | --- |
| `createDrPlan` with import | `getFtctlProtection`, active row lookup | 기존 보호 연결 |
| `startDrSync` | register/protect/status/check 계열 | 보호 시작 또는 상태 확인 |
| `startDrFailover` | `failoverFtctlProtection` | secondary activation |
| `confirmDrFenceClear` | `confirmFtctlFence`, `clearFtctlFence` | manual fence 확인/해제 |
| `startDrFailback` | `failbackFtctlProtection` 또는 `failbackFtctlDrReplica` | source-controller failback |
| `startDrReprotect` | existing reprotect flow | 역할 전환 후 보호 재구성 |
| `adoptDrReplica` | `adoptFtctlDrReplica` | replica-controller disaster recovery |
| `deleteDrPlan` | `releaseFtctlProtection`, `releaseFtctlDrReplicaProtection` | 보호 해제/cleanup |
| projection refresh | `getFtctlCheck`, `getFtctlHealth`, `getFtctlEvents`, status sync | runtime 상태 relay |

`failback`과 `adopt`는 절대 같은 backend method로 합치지 않는다.

## 6. Profile contract

기존 FTCTL profile schema는 하위 호환을 유지한다.

신규 DR metadata가 필요하면 optional field로만 추가한다.

권장 optional fields:

| Field | 의미 |
| --- | --- |
| `FTCTL_PROFILE_DR_PLAN_UUID` | Cloud `DrPlan.uuid` |
| `FTCTL_PROFILE_DR_RUN_UUID` | 현재 Cloud `DrRun.uuid` |
| `FTCTL_PROFILE_DR_ENGINE_BINDING_ID` | `dr_plan.engine_binding_id` 또는 `ftctl_protection.id` |
| `FTCTL_PROFILE_DR_CONTROLLER_MODE` | `source-controller`, `replica-controller` |
| `FTCTL_PROFILE_DR_ACTION` | `sync`, `failover`, `failback`, `adopt`, `release` |

규칙:

- field가 없어도 기존 FTCTL 동작은 동일해야 한다.
- field가 있어도 blockcopy backend 선택이 바뀌면 안 된다.
- profile validation은 unknown optional `FTCTL_PROFILE_DR_*` field를 허용한다.
- Cloud는 qemu host의 profile 파일을 직접 편집하지 않는다.
- profile 생성이 필요한 경우 기존 FTCTL register/protect 경로를 통해 생성한다.

## 7. Event contract

FTCTL `events.log`는 JSON Lines로 유지한다.

Cloud projection이 안정적으로 읽을 수 있도록 다음 field를 권장한다.

| Field | Required | 설명 |
| --- | --- | --- |
| `ts` | yes | ISO timestamp 또는 epoch millis |
| `event` | yes | event name |
| `vm` | yes | VM/domain name |
| `action` | no | protect/failover/failback 등 |
| `phase` | no | blockcopy, xcolo, fencing, cleanup 등 |
| `result` | no | ok/warn/fail/running |
| `state` | no | runtime state |
| `progress` | no | 0-100 |
| `message` | no | 사람이 읽을 요약 |
| `error_code` | no | 표준화 가능한 오류 |
| `detail` | no | structured detail |
| `dr_plan_uuid` | no | Cloud plan uuid |
| `dr_run_uuid` | no | Cloud run uuid |
| `external_ref` | no | lock file, qmp job id, block job id 등 |

Cloud는 unknown field를 무시해야 한다. FTCTL은 기존 field를 제거하지 않는다.

## 8. 필수 event name

Cloud projection을 위해 다음 event는 안정적으로 유지한다.

| Event | 의미 |
| --- | --- |
| `profile.loaded` | profile 로드 성공 |
| `profile.invalid` | profile validation 실패 |
| `inventory.check` | VM/disk/network runtime inventory |
| `inventory.disks` | disk/backend mapping |
| `health.libvirt` | libvirt/qemu 연결 상태 |
| `blockcopy.start` | blockcopy 시작 |
| `blockcopy.progress` | blockcopy 진행률 |
| `blockcopy.ready` | blockcopy ready |
| `blockcopy.verify` | target verification |
| `xcolo.start` | xcolo 시작 |
| `xcolo.ready` | xcolo 준비 완료 |
| `fencing.required` | manual/automatic fencing 필요 |
| `fencing.confirmed` | operator 확인 |
| `fencing.cleared` | fence clear |
| `failover.start` | failover 시작 |
| `failover.done` | failover 완료 |
| `failback.await-command` | Cloud-managed failback 대기 |
| `failback.start` | failback 시작 |
| `failback.done` | failback 완료 |
| `adopt.start` | replica adopt 시작 |
| `adopt.done` | replica adopt 완료 |
| `protection.release.start` | 보호 해제 시작 |
| `protection.release.done` | 보호 해제 완료 |
| `protection.unprotect.force-cleanup-warning` | forced cleanup warning |

기존 event 이름이 이미 다르면 Cloud adapter는 compatibility parser를 제공한다. 다만 신규 event를 추가할 때는 위 이름을 우선한다.

## 9. State projection mapping

### 9.1 FTCTL to DrPlan

| FTCTL state | DrPlan state | 비고 |
| --- | --- | --- |
| no active protection | `CREATED` 또는 no plan | import 여부에 따라 다름 |
| protecting / syncing | `SYNCING` | `DrRun(type=SYNC)`와 연결 |
| protected + mirroring | `READY` | target readiness 검증 필요 |
| paused | `PAUSED` | resume 가능 |
| failover_candidate | `FAILBACK_READY` 또는 `ERROR` | fencing state에 따라 결정 |
| failed_over | `FAILED_OVER` | active side secondary |
| failback_ready | `FAILBACK_READY` | source-controller flow |
| reprotecting | `REPROTECTING` | |
| unrecoverable error | `ERROR` | error_code 저장 |

### 9.2 FTCTL to DrReplica

| FTCTL/runtime | DrReplica state |
| --- | --- |
| standby VM/target exists but not verified | `SKELETON_READY` |
| blockcopy running | `MATERIALIZING` |
| blockcopy ready + verify ok | `TARGET_READY` |
| standby activated | `ACTIVE` |
| stale/missing target | `STALE` |
| runtime failure | `ERROR` |

### 9.3 FTCTL action result to DrRun

| FTCTL result | DrRun state |
| --- | --- |
| `ok` | `SUCCEEDED` |
| `warn` | `SUCCEEDED` with warning event or `ROLLBACK_REQUIRED` if cleanup incomplete |
| `fail` | `FAILED` |
| `locked` | `FAILED` with `DR_ENGINE_BUSY` or retryable `QUEUED` |
| timeout | `FAILED` with retryable flag if engine state unknown |

## 10. Error mapping

| FTCTL condition | Cloud error code |
| --- | --- |
| lock file exists / operation running | `DR_ENGINE_BUSY` |
| profile missing | `DR_ENGINE_PROFILE_MISSING` |
| stale state without profile | `DR_ENGINE_STALE_STATE` |
| libvirt connection failure | `DR_ENGINE_UNAVAILABLE` |
| qemu block job failure | `DR_REPLICATION_FAILED` |
| target verification failed | `DR_TARGET_NOT_READY` |
| manual fence needed | `DR_FENCE_REQUIRED` |
| automatic fence unavailable | `DR_FENCE_POLICY_UNAVAILABLE` |
| Cloud-created target missing | `DR_TARGET_MAPPING_INVALID` |
| forced cleanup warning | `DR_CLEANUP_PARTIAL` |

Cloud adapter는 FTCTL raw message를 보존하되 UI에는 표준 error code와 짧은 message를 우선 제공한다.

## 11. Timer/reconcile 경계

기존 FTCTL timer/reconcile은 유지한다.

규칙:

- Cloud `DrRun`이 destructive action을 실행 중이면 FTCTL timer가 같은 VM에서 auto-rearm 같은 충돌 작업을 하지 않아야 한다.
- FTCTL이 이미 lock을 잡고 있으면 Cloud adapter는 `DR_ENGINE_BUSY`로 변환한다.
- Cloud scheduler는 FTCTL timer를 대체하지 않는다.
- Projection refresh는 read-only action으로 취급한다.
- Cloud-managed failback 대기 상태에서는 FTCTL이 forward path를 auto-rearm하지 않아야 한다.

필요한 경우 FTCTL profile/state에 optional operation marker를 둔다. 단, marker가 없어도 기존 경로는 유지되어야 한다.

## 12. Idempotency

Cloud idempotency:

- `DrRun.idempotency_key`로 중복 action을 막는다.
- 같은 plan에 active destructive run이 있으면 새 action을 거부한다.

FTCTL idempotency:

- 이미 같은 target이 준비되어 있으면 재사용 또는 clear conflict를 반환한다.
- blockcopy/xcolo가 이미 running이면 status/progress를 반환하거나 locked를 반환한다.
- release/unprotect는 이미 정리된 상태에서 성공 또는 warning으로 종료해야 한다.

Cloud adapter는 FTCTL의 idempotent success와 "실제 새 작업 시작"을 구분해 event에 남긴다.

## 13. Cleanup contract

Cloud cleanup:

- `dr_plan`, `dr_run`, `dr_replica` soft delete
- Cloud-created standby VM/volume cleanup
- `ftctl_protection` row cleanup은 기존 FTCTL service를 통해 수행

FTCTL cleanup:

- runtime lock 제거
- profile/state 파일 제거
- qemu-nbd/qmp/blockjob cleanup
- xcolo runtime cleanup
- warning event 기록

Forced cleanup:

- UI/API에서 explicit acknowledgement 필요
- FTCTL은 `result=warn`, `forced=true`, `warnings=[...]`를 반환할 수 있다.
- Cloud는 partial cleanup warning을 `DrEvent`와 `DrRun.error_code=DR_CLEANUP_PARTIAL` 또는 warning field로 보존한다.
- Forced cleanup이 성공해도 과거 runtime failure evidence를 삭제하지 않는다.

## 14. qemu-side 변경 필요 여부

Phase 1 Cloud 구현에서 qemu-side 변경이 필요 없는 경우:

- 기존 FTCTL API와 events로 projection이 충분한 KVM-to-KVM import/read-only 표시
- 기존 failover/failback/release API를 그대로 호출하는 action
- 기존 `events.log` parser로 필요한 state를 얻을 수 있는 경우

qemu-side 변경이 필요한 경우:

- `DrRun` correlation을 위해 event에 `dr_plan_uuid`, `dr_run_uuid`가 필요할 때
- existing event에 progress/state/error가 부족해 UI가 오판할 때
- FTCTL timer/reconcile이 Cloud `DrRun`과 충돌할 때
- forced cleanup warning 구조가 표준 JSON으로 나오지 않을 때
- new optional profile field를 허용해야 할 때

qemu-side 변경은 별도 qemu repo 문서와 테스트를 동반한다.

## 15. Test contract

Cloud adapter test:

- FTCTL locked response -> `DR_ENGINE_BUSY`
- FTCTL fail response -> `DrRun.state=FAILED`
- FTCTL warn response -> warning event 보존
- projection stale -> plan destructive cleanup 없음
- existing active `ftctl_protection` import -> `DrPlan.engine_binding_type=FTCTL`
- failback과 adopt action 분리

qemu-side compatibility test:

- 기존 `rbd -> rbd` 보호 PASS 유지
- 기존 `rbd -> qcow2` 보호 PASS 유지
- 기존 `qcow2 -> rbd` 보호 PASS 유지
- 기존 `qcow2 -> qcow2` 보호 PASS 유지
- optional `FTCTL_PROFILE_DR_*` field가 없어도 기존 selftest PASS
- optional `FTCTL_PROFILE_DR_*` field가 있어도 backend 선택 변화 없음
- events.log unknown field가 기존 parser를 깨지 않음

Integration smoke:

1. 기존 FTCTL UI에서 보호 등록/조회가 그대로 동작한다.
2. 신규 DR UI에서 같은 VM에 중복 plan 생성이 거부된다.
3. 기존 FTCTL 보호를 `DrPlan`으로 import하면 runtime state가 projection된다.
4. FTCTL action 실패 시 Cloud async job/DrRun/DrEvent 모두 실패 근거를 보존한다.
5. Projection refresh 실패는 FTCTL cleanup을 유발하지 않는다.

## 16. AS-IS / TO-BE 요약

| 구분 | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL 사용 방식 | FTCTL UI/API가 직접 보호 운영 | 신규 DR는 FTCTL을 KVM-to-KVM engine adapter로 감쌈 |
| Runtime source | qemu FTCTL profile/state/events | 그대로 유지 |
| Cloud DB | FTCTL detail/protection 중심 | `DrPlan`/`DrRun` projection 추가 |
| Event | FTCTL events를 기능별로 소비 | `DrEvent`로 relay하되 raw runtime은 FTCTL이 소유 |
| Failback/adopt | 기존 FTCTL action 분리 | 신규 DR API에서도 source-controller/replica-controller 분리 유지 |
| Cleanup | FTCTL release/forced cleanup | Cloud cleanup과 FTCTL cleanup 책임 분리 |
| qemu 변경 | 기존 성공 경로 검증됨 | optional metadata/event 안정화만 허용 |
