# Cross-Hypervisor DR Source Site Outage Automatic Recovery Design

## 1. 범위

VMware 원본 사이트 전체 전원 장애 후 복구 시 Cloud UI, API, Backend, Agent, FTCTL, DB가 마지막 durable 기준선을 보존하고 VMware -> ABLESTACK(RBD) 지속 보호를 자동 재개하는 계약을 정의한다.

## 2. 계층별 설계

### UI

- terminal `오류` 대신 `원본 사이트 복구 대기`를 경고 색상으로 표시한다.
- 마지막 durable 시각과 RPO 초과는 계속 표시하되 Full Reseed 실행을 유도하지 않는다.
- 자동 복구는 비동기이므로 화면을 차단하지 않고 기존 polling/cache 갱신으로 상태를 반영한다.

### API / Backend

- ERROR/DEGRADED 계획도 source authority, desired RUNNING, scheduler DEAD 조건이면 `recoverSync` 대상이 될 수 있다.
- 자동 복구 Controller는 원본 사이트가 `CONNECTED`이고 최신 health check가 180초 이내이며, 최신 연속 3개 health check가 모두 정상일 때만 `RECOVER_SYNC` Run을 제출한다. 사이트 점검 주기보다 짧은 freshness 창을 과거 연속성 이력 전체에 적용하지 않는다.
- idempotency key는 Plan UUID와 authority sequence를 사용해 중복 복구를 막는다.
- 복구 요청은 `forceFullReseed=false`이며 Agent/FTCTL이 기존 baseline을 검증한다.

### Agent / FTCTL

- Agent는 기존 비동기 start/status 계약을 유지한다.
- FTCTL은 `DR_SOURCE_SITE_UNAVAILABLE`을 retryable로 반환하고 같은 Cycle sequence로 backoff 재시도한다.
- 복구 성공 시 오류/대기 메타데이터를 지우고 최신 durable 증거를 투영한다.

### DB

- 스키마 추가는 없다. 기존 `dr_plan_runtime`, `dr_run`, `dr_sync_cycle`, 사이트 헬스 이력을 사용한다.
- `dr.scheduler.recovery.enabled=true`로 자동 Controller를 활성화한다.
- 실패 Cycle은 durable 완료 Cycle을 대체하지 않으며 자동 복구 성공 Cycle이 최신 canonical Cycle이 된다.

## 3. AS-IS / TO-BE

| 계층 | AS-IS | TO-BE |
|---|---|---|
| UI | VDDK terminal 오류 | 원본 사이트 복구 대기 |
| API | ERROR 계획 recoverSync 비활성 | 장애 복구 목적의 제한적 활성 |
| Backend | 자동 복구 기본 비활성, 사이트 안정성 미검증 | 최신 점검 freshness와 최신 3회 연속 정상 상태를 분리 검증한 후 비동기 자동 복구 |
| Agent | terminal VDDK 오류 전달 | retryable source outage 전달 |
| FTCTL | 빠른 실패와 StartLimit | 동일 Cycle backoff, baseline 보존 |
| DB | 과거 durable와 terminal 오류 혼재 | 최신 durable 유지 후 복구 Cycle로 원자적 전환 |

## 4. 운영 판정

자동 증분 복구 PASS는 다음을 모두 만족해야 한다.

1. vCenter와 원본 VM CBT가 정상이다.
2. 원본 사이트의 최신 헬스가 freshness 범위 안에 있고, 최신 3회가 연속 `CONNECTED`다. 5분 점검 주기에서도 최신 점검의 신선도만 180초로 판정하고 과거 두 점검은 연속성 증거로 사용한다.
3. RECOVER_SYNC는 하나만 생성되고 비동기로 수락된다.
4. 첫 완료 Cycle은 `CBT_INCREMENTAL` 또는 `NO_CHANGE`다.
5. latest durable sequence가 증가하고 기존 baseline generation이 불필요하게 초기화되지 않는다.
6. Scheduler는 RUNNING/HEALTHY, Plan은 READY 또는 RPO 평가에 따른 DEGRADED다.
