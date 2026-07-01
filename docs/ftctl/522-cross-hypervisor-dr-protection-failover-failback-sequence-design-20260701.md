# Cross Hypervisor DR Protection, Failover, Failback Sequence Design

작성일: 2026-07-01

상위 계획: [520-cross-hypervisor-dr-full-implementation-work-plan-20260701.md](520-cross-hypervisor-dr-full-implementation-work-plan-20260701.md)

관련 설계:

- [521-cross-hypervisor-dr-full-stack-implementation-design-20260701.md](521-cross-hypervisor-dr-full-stack-implementation-design-20260701.md)
- [523-cross-hypervisor-dr-ftctl-engine-driver-design-20260701.md](523-cross-hypervisor-dr-ftctl-engine-driver-design-20260701.md)

## 1. 목적

이 문서는 DR 보호 설정, failover, failback 흐름을 UI/API/Backend/Agent/FTCTL 단위로 고정한다.

모든 방향은 같은 run model을 사용한다.

| 방향 | source | target |
|---|---|---|
| ABLESTACK -> VMware | KVM/QMP dirty bitmap | VMware VDDK/VMDK |
| VMware -> VMware | VMware CBT/VDDK | VMware VDDK/VMDK |
| ABLESTACK -> ABLESTACK | KVM/QMP 또는 기존 FTCTL/xcolo | ABLESTACK RBD/QCOW2 |
| VMware -> ABLESTACK | VMware CBT/VDDK | ABLESTACK RBD/QCOW2 |

## 2. 공통 action lifecycle

```mermaid
stateDiagram-v2
  [*] --> NEW
  NEW --> ENABLED: createDrPlan + enableDrPlan
  ENABLED --> SYNCING: startDrSync
  SYNCING --> READY: target-ready checkpoint
  READY --> TESTING: startDrTestFailover
  TESTING --> READY: stopDrTestFailover
  READY --> FAILING_OVER: startDrFailover
  FAILING_OVER --> FAILED_OVER: target promoted
  FAILED_OVER --> FAILING_BACK: startDrFailback
  FAILING_BACK --> READY: original source promoted
  FAILED_OVER --> REPROTECTING: startDrReprotect
  REPROTECTING --> READY: reverse protection ready
  READY --> DISABLED: disable/release
  SYNCING --> ERROR: engine failure
  FAILING_OVER --> ERROR: promotion failure
  FAILING_BACK --> ERROR: reverse failure
```

## 3. DR 보호 설정 흐름

### 3.1 UI 흐름

1. 사용자가 direction을 선택한다.
2. source site와 VM을 선택한다.
3. target site와 storage/network mapping을 선택한다.
4. RPO/RTO target을 입력한다.
5. preflight를 실행한다.
6. plan을 생성한다.
7. plan을 enable한다.
8. sync를 시작한다.
9. target-ready restore point가 만들어질 때까지 진행률을 표시한다.

### 3.2 Sequence

```mermaid
sequenceDiagram
  participant U as User
  participant UI as Cloud UI
  participant API as Cloud API
  participant B as Backend
  participant A as Mold Agent
  participant F as FTCTL_DR
  participant S as Source Driver
  participant T as Target Driver

  U->>UI: 보호 설정 입력
  UI->>API: checkDrPlanPreflight
  API->>B: validate direction/mapping/worker
  B->>A: FtctlDrPreflightCommand
  A->>F: dr-plan-apply --dry-run
  F->>S: source capability check
  F->>T: target capability check
  F-->>A: preflight result
  A-->>B: answer
  B-->>API: preflight response
  API-->>UI: preflight result
  UI->>API: createDrPlan
  API->>B: persist DrPlan, mapping, policy
  UI->>API: enableDrPlan
  API->>B: mark ENABLED
  UI->>API: startDrSync
  API->>B: create DrRun QUEUED
  B-->>API: accepted run
  API-->>UI: run accepted
  B->>A: FtctlDrActionCommand action=SYNC
  A->>F: dr-sync-start
  F->>S: full seed / incremental source read
  F->>T: write target extents
  F->>F: validate + checkpoint
  F-->>A: status/events
  A-->>B: status report
  B-->>UI: polling shows target-ready
```

### 3.3 Backend step 설계

| step | `DrRunStep.name` | 설명 |
|---|---|---|
| 1 | `validate-plan` | direction, engine, state, mapping 검증 |
| 2 | `dispatch-agent` | `FtctlDrActionCommand` 전송 |
| 3 | `apply-profile` | FTCTL profile 저장 |
| 4 | `preflight-source` | source driver 검증 |
| 5 | `preflight-target` | target driver 검증 |
| 6 | `full-seed` | 최초 전체 데이터 전송 |
| 7 | `incremental-sync` | 변경 영역 전송 |
| 8 | `validate-target` | flush/checksum/manifest 검증 |
| 9 | `publish-restore-point` | target-ready restore point 생성 |
| 10 | `update-rpo` | RPO lag 계산 |

### 3.4 방향별 보호 설정 차이

| 방향 | source preflight | target preflight | full seed | incremental |
|---|---|---|---|---|
| ABLESTACK -> VMware | QMP access, disk map, bitmap support | vCenter, datastore, network, VDDK writer | KVM disk export to VMDK | QMP dirty extents |
| VMware -> VMware | vCenter, CBT, VDDK reader | vCenter, datastore, network, VDDK writer | VDDK read to VMDK | CBT changed extents |
| ABLESTACK -> ABLESTACK | QMP/FTCTL profile, storage map | RBD/QCOW2 target, standby VM | 기존 FTCTL seed 또는 QMP export | 기존 xcolo/dirty extents |
| VMware -> ABLESTACK | vCenter, CBT, VDDK reader | ABLESTACK storage/network/VM | VDDK read to RBD/QCOW2 | CBT changed extents |

## 4. Test Failover 흐름

test failover는 운영 checkpoint를 오염시키지 않는다. target snapshot/clone/overlay 기반으로 격리된 VM을 부팅한다.

```mermaid
sequenceDiagram
  participant UI as Cloud UI
  participant API as Cloud API
  participant B as Backend
  participant F as FTCTL_DR
  participant T as Target Driver

  UI->>API: startDrTestFailover(planId, restorePointId)
  API->>B: DrRun TEST_FAILOVER 생성
  B->>F: dr-test-failover
  F->>T: create snapshot/clone/overlay
  F->>T: attach isolated network
  F->>T: boot test VM
  F->>T: guest/network validation
  F-->>B: test result + resource refs
  B-->>UI: polling result
  UI->>API: stopDrTestFailover
  API->>B: DrRun TEST_CLEANUP 생성
  B->>F: dr-test-cleanup
  F->>T: stop/delete test VM and overlay
  F-->>B: cleanup result
```

방향별 target test 방식:

| target | test 방식 |
|---|---|
| VMware | snapshot 또는 linked clone + isolated portgroup |
| ABLESTACK RBD | RBD snapshot/clone + isolated network VM |
| ABLESTACK QCOW2 | qcow2 backing overlay + isolated network VM |

## 5. Planned Failover 흐름

planned failover는 source가 살아 있는 상태를 전제로 한다. final delta sync 후 source를 fence/stop하고 target을 promote한다.

```mermaid
sequenceDiagram
  participant UI as Cloud UI
  participant API as Cloud API
  participant B as Backend
  participant F as FTCTL_DR
  participant S as Source Driver
  participant T as Target Driver

  UI->>API: startDrFailover(mode=planned, finalSync=true)
  API->>B: DrRun FAILOVER 생성
  B->>F: dr-failover --mode planned
  F->>S: quiesce or snapshot source
  F->>S: collect final changed extents
  F->>T: apply final delta
  F->>T: flush and validate target
  F->>S: fence/stop source VM
  F->>T: promote target VM
  F->>T: power on and validate service
  F-->>B: FAILED_OVER report
  B->>B: active_side=TARGET
  B-->>UI: failover complete
```

### 5.1 planned failover step

| step | 설명 |
|---|---|
| `pre-failover-check` | latest target-ready, no active test, capacity, network |
| `source-quiesce` | guest freeze/snapshot or crash-consistent marker |
| `final-delta-capture` | QMP dirty bitmap or VMware CBT final extent |
| `final-delta-apply` | target write and flush |
| `target-validation` | manifest/checksum/boot metadata |
| `source-fence` | source stop/network isolate/operator fence |
| `target-promote` | target VM attach/network/power-on |
| `service-validation` | guest agent/network check |
| `active-side-switch` | Cloud DB active side update |

## 6. Disaster Failover 흐름

disaster failover는 source가 불능일 수 있다. latest durable restore point를 사용한다.

```mermaid
sequenceDiagram
  participant UI as Cloud UI
  participant API as Cloud API
  participant B as Backend
  participant F as FTCTL_DR
  participant T as Target Driver

  UI->>API: startDrFailover(mode=disaster, acknowledgement)
  API->>B: validate acknowledgement and target-ready point
  B->>F: dr-failover --mode disaster --restore-point latest
  F->>T: lock selected restore point
  F->>T: promote target VM from durable checkpoint
  F->>T: power on and validate
  F-->>B: target promoted
  B->>B: active_side=TARGET, source_state=UNKNOWN_OR_FAILED
  B-->>UI: failover complete with RPO evidence
```

disaster failover에서는 final delta가 없을 수 있으므로 UI와 event에 실제 사용한 restore point와 RPO lag를 반드시 표시한다.

## 7. Failback 흐름

failback은 단순히 원래 VM을 켜는 작업이 아니다. target에서 운영된 변경 데이터를 original source 쪽으로 reverse sync한 뒤 planned cutback을 수행한다.

```mermaid
sequenceDiagram
  participant UI as Cloud UI
  participant API as Cloud API
  participant B as Backend
  participant F as FTCTL_DR
  participant ACTIVE as Active Target Driver
  participant ORIG as Original Source Driver

  UI->>API: startDrFailback
  API->>B: DrRun FAILBACK 생성
  B->>F: dr-failback
  F->>ACTIVE: create reverse checkpoint source
  F->>ORIG: prepare original site target
  F->>ACTIVE: full seed or reverse incremental
  F->>ORIG: apply reverse extents
  F->>F: create reverse target-ready point
  F->>ACTIVE: final delta + fence active target
  F->>ORIG: promote original source VM
  F-->>B: failback complete
  B->>B: active_side=SOURCE
  B-->>UI: plan READY or REPROTECT_REQUIRED
```

### 7.1 방향별 failback source/target reversal

| original direction | failback data direction |
|---|---|
| ABLESTACK -> VMware | VMware -> ABLESTACK |
| VMware -> VMware | VMware DR -> VMware original |
| ABLESTACK -> ABLESTACK | ABLESTACK DR -> ABLESTACK original |
| VMware -> ABLESTACK | ABLESTACK -> VMware |

즉, failback은 네 방향 driver 조합을 모두 구현해야 완성된다. 한 방향만 구현하면 failback은 반쪽이 된다.

## 8. Reprotect 흐름

reprotect는 현재 active side를 source로 삼아 반대 방향 보호를 새로 구성한다.

```mermaid
flowchart LR
  FAILED["FAILED_OVER<br/>active_side=TARGET"]
  PRE["preflight reverse direction"]
  PROFILE["create/update reverse FTCTL_DR profile"]
  SEED["reverse full seed or checkpoint reuse"]
  SYNC["reverse incremental sync"]
  READY["READY<br/>active side protected"]

  FAILED --> PRE --> PROFILE --> SEED --> SYNC --> READY
```

reprotect 완료 후 사용자는 새 active source에서 다시 failover/failback/test failover를 수행할 수 있어야 한다.

## 9. Release/Cleanup 흐름

release는 운영 VM 삭제가 아니라 보호 관계와 DR runtime 자원을 정리하는 action이다.

정리 대상:

- FTCTL_DR profile
- session lock
- temporary export/socket
- test failover clone/overlay
- stale checkpoint under retention policy
- Cloud `DrRun` active lock
- target replica, 단 force release가 아니면 latest target-ready checkpoint는 보존 가능

forced release는 별도 acknowledgement가 필요하다.

## 10. UI/API/Backend/Agent/FTCTL 책임 분리

| 계층 | 책임 |
|---|---|
| UI | action 요청, 상태 표시, acknowledgement 수집 |
| API | parameter validation, async run 접수 |
| Backend | state machine, lock, worker selection, DB projection |
| Agent | host command 실행, status polling/report |
| FTCTL | source/target driver 실행, 데이터 전송, checkpoint, failover |

UI/API는 long-running 작업을 동기식으로 처리하지 않는다.

## 11. 완료 기준

각 방향마다 아래 evidence가 있어야 완료로 본다.

| evidence | 설명 |
|---|---|
| protection setup | plan, profile, preflight, initial sync 성공 |
| incremental sync | 변경 데이터만 전송된 checkpoint 2개 이상 |
| RPO | target-ready RPO lag 계산 및 UI 표시 |
| test failover | isolated target VM boot + cleanup 성공 |
| planned failover | final delta + target promote 성공 |
| disaster failover | latest durable checkpoint promote 성공 |
| failback | reverse sync + original site promote 성공 |
| reprotect | active side 기준 보호 재구성 성공 |

한 방향이라도 위 항목이 빠지면 4개 방향 동일 수준 구현 완료로 보지 않는다.
