# 625. DR Cutover Authority Commit Serialization And Failback Readiness Convergence

- 작성일: 2026-09-01
- 상태: 구현·스모크 테스트·31번 테스트 배포 완료
- 적용 레이어: Cloud DR Backend, Cloud DB projection
- 비적용 레이어: UI component, API contract, Agent, FTCTL, scheduler, data transfer, VM lifecycle
- 관련 설계: 599, 617, 622, 624

## 1. 목적

Failover 데이터 전송, 대상 VM 기동, 부팅 검증이 모두 완료된 뒤 Cloud가 FTCTL에
TARGET authority를 commit하는 짧은 구간만 보강한다. 같은 Failover Run을 두 projection
worker가 동시에 처리해 서로 다른 commit attempt를 만들거나, 이미 ACK된 session을 늦은
실패 응답이 `RETRY_REQUIRED`로 되돌리는 것을 금지한다.

이 변경은 기존 VMware -> RBD, RBD -> RBD, SharedMountPoint qcow2 -> qcow2의 전송 및
VM lifecycle을 변경하지 않는다. Failback 실행 로직도 변경하지 않는다. Failback preflight가
사용하는 committed TARGET authority가 실제 FTCTL journal과 일치하도록 수렴시키는 것이
유일한 목적이다.

## 2. 관측된 오류

31번 클러스터의 Plan `80966d9e-5224-4b8d-bf93-42f94261d058`에서 다음 불일치가 확인됐다.

| 계층 | 상태 |
| --- | --- |
| 대상 VM/부팅/원본 격리 | `POWERED_ON / POWER_STATE_VALIDATED / ACKNOWLEDGED` |
| FTCTL PLAN_AUTHORITY | `FAILED_OVER / TARGET / PROMOTED / ACKNOWLEDGED`, generation `546` |
| Cloud cutover session | `ENGINE_COMMIT_PENDING / RETRY_REQUIRED / UNKNOWN` |
| UI Failback preflight | `Failback requires committed TARGET authority` |

FTCTL journal에는 같은 authority를 가리키는 정상 ACK가 존재하지만, 경쟁한 다른 envelope가
`DR_CUTOVER_COMMIT_CONFLICT`를 반환한 뒤 Cloud DB의 성공 상태를 실패 상태로 덮을 수 있었다.

## 3. 불변 조건

1. `(plan_id, run_id)`의 활성 cutover session은 하나의 `commit_attempt_id`만 사용한다.
2. attempt와 envelope는 DB 행 잠금 안에서 최초 한 번 확정하고 재시도 시 그대로 사용한다.
3. `ACKNOWLEDGED / PROMOTED`는 단조 증가 terminal 상태다.
4. 늦은 실패 응답은 다른 attempt 또는 terminal session을 변경할 수 없다.
5. FTCTL PLAN_AUTHORITY가 동일 Cloud session UUID와 generation에 대해
   `TARGET / PROMOTED / ACKNOWLEDGED`를 증명하면 Cloud는 session을 자동 복구한다.
6. session 복구는 Plan, Replica, Runtime을 `FAILED_OVER / TARGET`으로 수렴시키며 오류를 지운다.
7. Run 성공은 committed TARGET authority 이후에만 허용한다.
8. 세션 UUID 또는 generation이 다르면 자동 복구하지 않는다.

## 4. 구현

### 4.1 Prepare 단계

`commitCloudOwnedCutover()`는 `dr_cutover_session` 행을 `SELECT ... FOR UPDATE`로 다시 읽는다.
잠긴 행에 attempt가 없을 때만 UUID를 생성한다. 이미 존재하면 전달받은 detached 객체의
값을 사용하지 않고 DB 값을 재사용한다.

### 4.2 응답 적용 단계

Agent 응답을 적용할 때 세션 행을 다시 잠근다.

- 성공: 현재 attempt가 전송한 attempt와 같을 때만 ACK terminal로 승격한다.
- 실패: 현재 attempt가 같고 아직 ACK되지 않은 경우에만 `RETRY_REQUIRED`를 기록한다.
- 이미 ACK: 실패 응답을 폐기하고 성공 terminal projection을 계속한다.
- attempt 불일치: 기존 authority를 보존하고 해당 응답을 stale로 취급한다.

### 4.3 FTCTL ACK 재조정

PLAN_AUTHORITY status에서 아래 튜플이 모두 일치할 때만 Cloud session을 복구한다.

```text
active_side                 = TARGET
target_promotion_state      = PROMOTED
engine_ack_state            = ACKNOWLEDGED
cloud_cutover_session_id    = dr_cutover_session.uuid
cloud_authority_generation  = dr_cutover_session.cloud_authority_generation
```

복구는 DB 수동 변경 없이 projection refresh가 수행한다. 다른 session 또는 generation의 과거
journal은 절대 현재 authority로 승격하지 않는다.

## 5. 테스트

필수 단위·스모크 테스트:

1. 같은 session prepare를 반복해도 commit attempt가 바뀌지 않는다.
2. ACK된 session에 늦은 실패 응답을 적용해도 terminal 상태와 오류 없음이 유지된다.
3. Cloud가 `RETRY_REQUIRED`여도 동일 FTCTL session/generation ACK가 있으면 자동 수렴한다.
4. 다른 session UUID 또는 generation의 ACK는 무시한다.
5. 기존 정상 cutover 성공, 실제 commit 실패 시 SOURCE authority 유지 테스트가 통과한다.
6. Failback preflight는 복구 전 차단되고 복구 후 `ready=true`가 된다.

## 6. 배포 및 재검증

Cloud DR integration 모듈 테스트 후 테스트 릴리즈 패키지를 31번 관리 서버에 배포한다.
배포 후 서비스와 `/client/`를 확인하고 projection refresh로 현재 Plan이 다음 상태로 자동
수렴하는지만 확인한다.

```text
Plan              FAILED_OVER / TARGET
Cutover session   FAILED_OVER / PROMOTED / ACKNOWLEDGED
Replica           READY / TARGET / POWERED_ON
Failback preflight ready=true
```

실제 Failback 실행은 사용자가 UI에서 수행한다.

## 7. 구현 및 배포 증거

- Cloud 코드 커밋: `8bb5108c4c265b21986c0adbf0b7a4383f17cb43`
- GitHub Actions 테스트 릴리스: run `33449271367`, 전체 작업 `success`
- 배포 패키지:
  - `cloudstack-common-4.23.0.0-Mold.Europa.202608312307.1`
  - `cloudstack-management-4.23.0.0-Mold.Europa.202608312307.1`
- 비배포 범위: Cloud UI, usage, Agent, FTCTL, scheduler, 전송 경로, VM lifecycle
- 배포 후 관리 서비스: `mold=active`, `/client/=HTTP 200`, `WEB-INF=present`
- 자동 재투영 결과:
  - Plan `FAILED_OVER / TARGET`, 오류 필드 없음
  - Cutover session `FAILED_OVER / PROMOTED / ACKNOWLEDGED`, generation `546`, 오류 필드 없음
  - Replica `READY / TARGET / POWERED_ON`
  - Runtime `FAILED_OVER_UNPROTECTED`, 오류 필드 없음
- `getDrFailbackPreflight` 결과: `ready=true`; `AUTHORITY`, `SOURCE_RUNTIME`,
  `TARGET_RUNTIME`, `FTCTL_TRANSITION`, `REVERSE_DATA` 모두 `READY`
- 이번 Plan의 역방향 기준선은 아직 없으므로 preflight가 선택한 모드는
  `FULL_RESEED`, 예상 가상 용량은 `100 GiB`다. 이는 authority 수렴 결함과 별개인
  기존 Failback 데이터 경로의 정상 초기 시드 판정이다.

실제 Failback 실행과 완료 판정은 사용자 UI 재검증으로 남긴다.
