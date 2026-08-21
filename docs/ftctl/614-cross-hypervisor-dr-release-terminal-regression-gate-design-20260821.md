# 614. Cross-Hypervisor DR Release Terminal Regression Gate Design

- 작성일: 2026-08-21
- 상태: 설계 및 구현 완료, 테스트 배포 검증 대기
- 검증 Plan: `ef73f5f3-9740-4bbd-8c9a-74a972e5f19f`
- 적용 범위: Cloud Backend, Agent 응답 계약, FTCTL status, DB projection, CI
- FTCTL 부속 설계: `463-ftctl-dr-release-tombstone-profile-independent-status-contract-20260821.md`

## 1. 오류 원인

RPO deadline 개선이 공통 `dr-status`에 필드를 추가하면서 profile이 존재할 때만
변수를 초기화했다. Release는 정상적으로 profile을 삭제하므로 이후 status 조회가
`DR_STATUS_JSON_INVALID`로 실패했다. Agent의 Release 응답은 이미
`RELEASED / release-completed`였지만 Cloud Run executor는 terminal 결과를
성공으로만 닫고 Plan projection은 다시 실패하는 status polling에 의존했다.

## 2. 종결 계약

```text
Agent RELEASE success evidence
  state=RELEASED
  step=release-completed
  protection_state=UNPROTECTED
  scheduler_state=STOPPED

Cloud terminal projection
  plan.state=UNPROTECTED
  plan.admin_state=DISABLED
  plan.active_side=<release 이전 authority>
  runtime.scheduler_state=STOPPED
  runtime.protection_state=UNPROTECTED
  active replica/restore point=removed
  release run=SUCCEEDED / projection_state=terminal
```

VM, 볼륨, 네트워크는 삭제하거나 전원을 변경하지 않는다.

## 3. Cloud 구현

1. `DrProjectionAdapter.projectTerminalActionResult()`를 terminal action의 typed
   projection 경계로 추가한다.
2. `DrRunExecutorImpl`은 RELEASE 성공 응답을 받은 즉시 terminal projection을
   호출한다.
3. `FtctlDrRuntimeProjectionAdapter`는 `agentAnswer.state`와 `step`을 검증하고,
   강한 release 증거가 있을 때만 Plan과 runtime을 갱신한다.
4. Plan, runtime, replica, restore point 정리는 하나의 Cloud DB transaction으로
   수행한다.
5. 즉시 projection이 일시 실패하면 Run은 `terminal-pending`으로 남고 기존
   periodic status projection이 복구 경로가 된다. FTCTL tombstone status는
   profile과 무관하게 동작하므로 수동 DB 변경이 필요 없다.

## 4. 향후 변경 작업의 필수 규칙

다음 조건은 선택 사항이 아니라 배포 승인 조건이다.

1. 검증된 DR 성공 경로를 불변 계약으로 취급한다.
2. 공통 runtime/status/profile/scheduler/Agent answer/Cloud projection 변경은
   요청 기능 외의 모든 terminal action에 대한 영향 분석을 문서에 남긴다.
3. optional JSON 필드는 producer와 consumer 모두 명시적 기본값을 갖는다.
4. profile present, profile missing, process restart, delayed polling을 포함한
   release 회귀 테스트가 통과해야 한다.
5. Cloud는 Agent terminal 응답과 periodic status 두 경로가 동일 결과로
   수렴하는지 검사한다.
6. Sync, Pause/Resume, Release, Test Failover/Cleanup, Failover, Failback의
   baseline action contract suite가 실패하면 빌드 산출물을 테스트 클러스터에
   배포하지 않는다.
7. 패키지 배포 후 설치 코드 marker, Agent 응답, DB projection, UI 상태를 함께
   확인한다. UI 또는 Run 성공만으로 PASS 판정하지 않는다.
8. 회귀 복구 시 직접 DB 상태 수정으로 결함을 숨기지 않고 정상 projection으로
   기존 Plan을 복구한다.
9. terminal projection은 정규 상태 열만 갱신해서는 안 된다. 같은 DB transaction에서
   `status_json`, scheduler unit/process, worker, transfer, current cycle, RPO/error 증거를
   terminal 상태와 일치하도록 정리해야 한다.
10. API 응답의 정규 상태와 중첩 runtime 상태가 다르면 배포 검증은 실패다. Release 후
    `UNPROTECTED/DISABLED/STOPPED`와 `runtimeState=RELEASED`,
    `runtimeControlState=STOPPED`, `schedulerPidAlive=false`를 함께 확인한다.
11. 이미 검증된 경로의 공통 status/projection 코드를 변경할 때는 기능별 단위 테스트뿐
    아니라 terminal 이후 원시 JSON 잔존 여부를 검사하는 교차 경로 회귀 테스트를 추가한다.

## 5. 테스트 매트릭스

| 테스트 | 기대 결과 |
| --- | --- |
| profile 존재 상태 조회 | READY JSON 유지 |
| Release 실행 | tombstone 생성, scheduler 중지, profile 삭제 |
| profile 없는 plan status | RELEASED/UNPROTECTED/STOPPED |
| status 삭제 후 재조회 | tombstone으로 status 복원 |
| Agent Release 성공 응답 | polling 전에 UNPROTECTED/DISABLED |
| status 일시 장애 | terminal-pending 후 자동 수렴 |
| Release 재호출 | idempotent, VM/스토리지/네트워크 무변경 |
| 이전 READY/RUNNING runtime 보유 후 Release | 정규 열과 status JSON이 함께 RELEASED/STOPPED로 수렴 |
| Release API 응답 | stale scheduler/worker/transfer 필드 미노출 |

## 6. AS-IS / TO-BE

| 레이어 | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL | profile 삭제 후 status 실패 | tombstone 기반 자기 완결 status |
| Agent | 성공 응답은 있으나 Cloud가 종결 증거로 사용하지 않음 | typed terminal evidence 전달 |
| Cloud Backend | polling에만 종결 의존 | 성공 응답 즉시 transaction + polling 복구 |
| DB | Run 성공 뒤 정규 열만 수렴하고 status JSON은 READY/RUNNING 잔존 | Run/Plan/runtime/resource와 원시 JSON 동시 수렴 |
| 배포 | 관련 기능 단위 smoke | 기존 DR 전체 action contract gate |
