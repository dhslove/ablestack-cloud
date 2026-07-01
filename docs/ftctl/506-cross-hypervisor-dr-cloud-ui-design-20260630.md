# Cross Hypervisor DR Cloud UI Design

작성일: 2026-06-30

대상 브랜치: `feature/ftctl-cloud-integration`

상위 문서:

- [500-cross-hypervisor-dr-architecture-plan-20260630.md](500-cross-hypervisor-dr-architecture-plan-20260630.md)
- [501-cross-hypervisor-dr-domain-schema-design-20260630.md](501-cross-hypervisor-dr-domain-schema-design-20260630.md)
- [502-cross-hypervisor-dr-adapter-contract-design-20260630.md](502-cross-hypervisor-dr-adapter-contract-design-20260630.md)
- [503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md](503-cross-hypervisor-dr-state-machine-and-worker-design-20260630.md)
- [504-cross-hypervisor-dr-phase1-vmware-target-scope-design-20260630.md](504-cross-hypervisor-dr-phase1-vmware-target-scope-design-20260630.md)
- [505-cross-hypervisor-dr-ftctl-v2k-integration-design-20260630.md](505-cross-hypervisor-dr-ftctl-v2k-integration-design-20260630.md)

## 1. 목적

이 문서는 `Cross Hypervisor DR` 기능의 Cloud UI 상세 설계를 정의한다.

범위는 설계까지이며, 이 문서 작성 단계에서는 Vue 컴포넌트, API 모듈, 라우팅, locale, 스타일 구현을 진행하지 않는다. 이후 구현 시에는 이 문서의 화면 구조, 상태 표시, 액션 게이트, 다크모드 스타일 기준을 기반으로 작업한다.

UI 목표는 다음과 같다.

- 기존 `DisasterRecoveryCluster`, `FTCTL` UI의 동작을 깨지 않고 신규 `DrSite`, `DrPlan`, `DrRestorePoint`, `DrReplica`, `DrRun` 모델을 노출한다.
- 사용자가 "현재 보호 가능한가", "목표 사이트에 실제로 부팅 가능한 복제본이 있는가", "장애 전환을 실행해도 되는가"를 한 화면에서 판단할 수 있게 한다.
- 비동기 작업은 클릭 성공이 아니라 Cloud async job, `DrRun`, backend/runtime projection 상태로 최종 성공/실패를 확인한다.
- ABLESTACK/KVM to ABLESTACK/KVM의 기존 FTCTL 성공 경로와 VMware 대상 신규 경로를 같은 UI 언어로 표현하되, 내부 엔진 차이는 명확히 드러낸다.
- 다크모드 대응은 필수 구현 조건으로 둔다. 모든 신규 화면, 테이블, 배지, 진행률, 경고, 빈 상태, 모달은 라이트/다크모드에서 모두 읽기 쉬워야 한다.

## 2. 기존 UI 자산

구현 시 우선 재사용하거나 참고할 기존 파일은 다음과 같다.

| 영역 | 파일 | 활용 방향 |
| --- | --- | --- |
| DR 인프라 메뉴 | `ui/src/config/section/infra/disasterRecovery.js` | 기존 DR cluster 섹션 구조, resource config, action 정의 방식 참고 |
| VM DR 테이블 | `ui/src/views/compute/dr/DRTable.vue` | VM 상세에서 DR 관련 목록을 보여주는 패턴 참고 |
| FTCTL 탭 | `ui/src/views/compute/FtctlTab.vue` | 상태 요약, 액션 게이트, async refresh, 진행률, event 표시 패턴 참고 |
| FTCTL 등록 | `ui/src/views/compute/RegisterFtctlProtection.vue` | 보호 등록 wizard, 대상 호스트/스토리지 선택 UX 참고 |
| 다크모드 상태 | `ui/src/store/getters.js`, `ui/src/store/modules/user.js` | `$store.getters.darkMode` 기반 분기 참고 |
| 전역 테마 | `ui/src/App.vue`, `ui/public/color.less`, `ui/public/css/dark-theme.css` | Ant Design Vue theme token, `body.dark-mode` 스타일 방식 참고 |

기존 FTCTL 탭은 유지한다. 신규 Cross Hypervisor DR UI는 FTCTL 탭을 대체하지 않고, FTCTL을 DR engine 중 하나로 projection한다.

## 3. 정보 구조

### 3.1 메뉴 구조

권장 메뉴 구조는 다음과 같다.

| 위치 | 화면 | 설명 |
| --- | --- | --- |
| Infrastructure > Disaster Recovery | DR Sites | Mold/KVM, VMware, remote Mold 같은 DR site 등록 및 상태 확인 |
| Infrastructure > Disaster Recovery | DR Plans | VM 단위 보호 계획 목록, 동기화 상태, RPO, 대상 readiness 확인 |
| Compute > Virtual Machines > VM detail | Disaster Recovery 탭 | 해당 VM에 연결된 `DrPlan` 요약 및 주요 액션 |
| Compute > Virtual Machines > VM detail | Fault Tolerance 탭 | 기존 FTCTL 운영 화면. 변경하지 않음 |

기존 `DisasterRecoveryCluster` 화면은 바로 제거하지 않는다. 초기 구현에서는 기존 화면과 신규 DR Sites/Plans를 병행한다. 기존 화면이 신규 `DrSite`로 통합될 때는 별도 migration 문서를 둔다.

### 3.2 신규 resource config 제안

구현 파일 위치는 다음을 권장한다.

| 목적 | 제안 파일 |
| --- | --- |
| DR Plan resource config | `ui/src/config/section/infra/drPlan.js` |
| DR Site resource config | `ui/src/config/section/infra/drSite.js` |
| DR Plan 목록 | `ui/src/views/infra/dr/DrPlanList.vue` |
| DR Site 목록/상세 | `ui/src/views/infra/dr/DrSiteList.vue`, `DrSiteDetail.vue` |
| DR Plan 생성 wizard | `ui/src/views/infra/dr/DrPlanWizard.vue` |
| DR Plan 상세 overview | `ui/src/views/infra/dr/DrPlanOverview.vue` |
| Restore point tab | `ui/src/views/infra/dr/DrRestorePointsTab.vue` |
| Replica tab | `ui/src/views/infra/dr/DrReplicaTab.vue` |
| Run history tab | `ui/src/views/infra/dr/DrRunsTab.vue` |
| Event tab | `ui/src/views/infra/dr/DrEventsTab.vue` |
| VM detail projection | `ui/src/views/compute/dr/DrPlanVmTab.vue` |
| 공통 상태 배지 | `ui/src/components/dr/DrStatusPill.vue` |
| RPO KPI | `ui/src/components/dr/DrRpoKpi.vue` |
| 진행률 표시 | `ui/src/components/dr/DrRunProgress.vue` |
| 액션 툴바 | `ui/src/components/dr/DrActionToolbar.vue` |
| 토폴로지/경로 표시 | `ui/src/components/dr/DrTopology.vue` |

## 4. 화면 설계

### 4.1 DR Sites 목록

목적은 DR endpoint inventory와 연결 상태를 한눈에 보여주는 것이다.

주요 컬럼:

| 컬럼 | 값 |
| --- | --- |
| Name | `DrSite.name` |
| Type | `MOLD_KVM`, `MOLD_VMWARE`, `VMWARE_DIRECT` |
| Hypervisor | `KVM`, `VMWARE` |
| Endpoint | Mold URL 또는 vCenter URL. credential은 표시하지 않음 |
| Health | `CONNECTED`, `DEGRADED`, `DISCONNECTED`, `UNKNOWN` |
| Last Check | 마지막 연결 점검 시각 |
| Plans | 이 site를 source 또는 target으로 사용하는 active plan 수 |
| Actions | Check, Edit, Disable, Delete |

상태 표시:

- 연결 실패는 목록 row 전체를 위험색으로 칠하지 않는다. `Health` 배지와 row 하단 보조 텍스트로 원인을 표시한다.
- credential 오류, 인증서 오류, network timeout은 서로 다른 reason code로 보여준다.
- 다크모드에서 위험/경고 배지는 배경만 진하게 하지 말고 텍스트 대비를 보장한다.

### 4.2 DR Site 상세

상세 화면 탭:

| 탭 | 내용 |
| --- | --- |
| Overview | endpoint, hypervisor type, health, last check, supported directions |
| Inventory | target datastore/network 후보, source VM inventory projection |
| Plans | 이 site와 연결된 DR plans |
| Events | site check, credential rotation, adapter error |
| Settings | endpoint, credential reference, certificate policy |

민감 정보 표시 원칙:

- API key, secret key, password, token은 화면에 표시하지 않는다.
- credential reference id는 운영자가 식별할 수 있는 alias만 표시한다.
- secret rotation은 기존 값을 읽어 오는 방식이 아니라 새 credential을 입력해 교체하는 방식으로 설계한다.

### 4.3 DR Plans 목록

목적은 운영자가 보호 상태와 위험 대상을 빠르게 찾는 것이다.

주요 컬럼:

| 컬럼 | 값 |
| --- | --- |
| Protected VM | source VM name, instance name |
| Direction | `KVM_TO_KVM`, `KVM_TO_VMWARE`, `VMWARE_TO_VMWARE`, `VMWARE_TO_KVM` |
| Source Site | source `DrSite.name` |
| Target Site | target `DrSite.name` |
| Plan State | `DrPlan.state` |
| Replica State | `DrReplica.state` |
| Target Ready | latest `DrRestorePoint.state == TARGET_READY` 여부 |
| RPO | source restore point age와 target-ready restore point age를 분리 표시 |
| Last Run | 최근 `DrRun.type`, result, duration |
| Actions | Sync, Test, Failover, Pause/Resume, Delete |

목록 필터:

- Direction
- Source site
- Target site
- Plan state
- Replica state
- Target ready 여부
- RPO threshold 초과 여부
- 마지막 작업 실패 여부

정렬 우선순위:

1. `ERROR`, `DEGRADED`, RPO 초과
2. `SYNCING`, `MATERIALIZING`
3. `READY`
4. `PAUSED`, `DISABLED`

### 4.4 DR Plan 생성 wizard

Wizard는 긴 단일 form이 아니라 단계별 validation과 preflight를 갖는다.

| 단계 | 이름 | 입력/출력 | Validation |
| --- | --- | --- | --- |
| 1 | Source | source site, source VM | VM 상태, snapshot 가능 여부, disk inventory |
| 2 | Target | target site, direction | source/target hypervisor 조합 지원 여부 |
| 3 | Storage Mapping | source disk별 target datastore/pool | 용량, 포맷 변환 가능 여부, thin/thick 정책 |
| 4 | Network Mapping | NIC별 target network | VLAN/portgroup/network offering 매핑 |
| 5 | Policy | RPO 목표, schedule, retention, quiesce, test network, fencing policy | 정책 범위, 충돌 여부 |
| 6 | Preflight | adapter check 결과 | 모든 hard blocker 해소 필요 |
| 7 | Review | 생성 요약, 초기 sync 여부 | destructive action 없음 |

Wizard UX 규칙:

- 단계 이동 시 서버 preflight가 필요한 항목은 async job 결과까지 확인한다.
- "다음" 버튼은 hard blocker가 있으면 비활성화하고 tooltip으로 이유를 표시한다.
- warning은 계속 진행 가능하지만 review 단계에 누적 표시한다.
- VMware target의 경우 datastore/network mapping이 없으면 생성할 수 없다.
- KVM to KVM FTCTL engine의 경우 기존 보호 등록 조건과 충돌하면 생성할 수 없다.
- 다크모드에서 stepper, warning list, preflight result table이 배경과 구분되어야 한다.

### 4.5 DR Plan 상세

상세 화면은 상단 summary band와 하단 탭 구조를 사용한다. 상단 summary band는 중첩 card를 피하고, 한 줄 KPI와 상태 callout을 조합한다.

상단 summary:

| 영역 | 내용 |
| --- | --- |
| Identity | plan name, source VM, direction |
| State | plan state, replica state, active side |
| RPO | source latest age, target-ready latest age |
| Current Run | run type, current step, progress percent |
| Readiness | bootable replica, network mapped, storage mapped, last preflight |
| Risk | last error, stale projection, fence required |

상세 탭:

| 탭 | 내용 |
| --- | --- |
| Overview | topology, KPI, readiness checklist |
| Restore Points | restore point 목록, target materialization 상태 |
| Replica | target VM/disk/network projection, bootability |
| Runs | sync/test/failover/failback/reprotect run history |
| Events | engine events, adapter events, Cloud async job 결과 |
| Settings | policy, mapping, retention, notification |

### 4.6 VM 상세 Disaster Recovery 탭

VM 상세 탭은 운영자가 특정 VM에서 바로 보호 상태를 확인하기 위한 projection 화면이다.

표시 내용:

- 연결된 `DrPlan`이 없으면 생성 CTA를 제공한다.
- 연결된 `DrPlan`이 있으면 plan summary, RPO, target readiness, 최근 run, 주요 액션을 표시한다.
- FTCTL 기반 plan은 기존 Fault Tolerance 탭으로 이동할 수 있는 링크를 제공한다.
- VM이 stopped, destroyed, expunging 상태면 보호 생성 또는 sync action을 비활성화한다.

기존 `Fault Tolerance` 탭은 FTCTL runtime 진단과 cloud-managed action 중심으로 유지한다. 신규 DR 탭은 DR orchestrator 관점의 plan 중심 화면으로 둔다.

## 5. 액션 설계

### 5.1 공통 액션 툴바

액션은 아이콘과 텍스트를 함께 사용한다. 비활성 상태는 disabled button만 두지 않고 tooltip 또는 inline reason을 제공한다.

| 액션 | 조건 | 결과 |
| --- | --- | --- |
| Sync Now | `ENABLED`, `READY`, `ERROR` 일부 복구 가능 상태 | `DrRun(type=SYNC)` 생성 |
| Test Failover | target-ready restore point 존재, test network mapping 존재 | 격리된 target test VM 또는 test mode 실행 |
| Failover | target-ready restore point 존재, hard blocker 없음 | `DrRun(type=FAILOVER)` 생성 |
| Confirm Fence Clear | manual fencing required 상태 | fence clear 확인 run 또는 FTCTL fence API 호출 |
| Failback | failed-over 후 source 복구 확인, failback 지원 direction | `DrRun(type=FAILBACK)` 생성 |
| Reprotect | failover/adopt 이후 새 source/target 확정 | `DrRun(type=REPROTECT)` 생성 |
| Pause | active sync 중단 가능 | plan state `PAUSED` |
| Resume | `PAUSED` | plan state 복구 및 sync 재개 |
| Delete | active run 없음 | plan 제거 또는 보호 해제 |

### 5.2 상태별 액션 게이트

| Plan State | 허용 액션 | 비활성 사유 예시 |
| --- | --- | --- |
| `CREATED` | Edit, Delete, Enable | preflight 미완료 |
| `ENABLED` | Sync Now, Pause, Delete | target-ready restore point 없음 |
| `SYNCING` | View Run, Pause 일부 | 진행 중인 run 존재 |
| `READY` | Sync Now, Test Failover, Failover, Pause, Delete | hard blocker 없음 |
| `TESTING` | Stop Test, View Run | test failover 종료 필요 |
| `FAILED_OVER` | Failback, Reprotect, Adopt, View Run | source 복구 확인 필요 |
| `FAILBACK_READY` | Failback, Reprotect | manual fence clear 필요 |
| `REPROTECTING` | View Run | reprotect 진행 중 |
| `PAUSED` | Resume, Delete | plan 일시 중지 |
| `ERROR` | Retry, Sync Now 일부, Delete | 원인 확인 필요 |

위험 액션:

- Failover, Failback, Adopt, Delete, forced cleanup은 confirm modal을 사용한다.
- confirm modal은 예상 영향, source/target controller, 선택한 restore point, 네트워크 격리 여부를 표시한다.
- 재해 상황의 `Adopt/Promote`와 source controller가 살아 있는 `Failback`은 같은 버튼으로 합치지 않는다.

## 6. 진행률과 이벤트

### 6.1 진행률 표시

진행 중인 `DrRun`은 다음 계층으로 표시한다.

| 계층 | 예시 |
| --- | --- |
| Run summary | `SYNC`, `MATERIALIZE`, `FAILOVER`, `FAILBACK` |
| Step progress | snapshot, transfer, convert, register target VM, preflight, power on |
| Engine detail | FTCTL blockcopy, V2K conversion, VMware snapshot export |
| Runtime evidence | async job id, host task id, qemu/ftctl event id |

진행률 원칙:

- backend가 percent를 제공하지 못하면 가짜 percent를 만들지 않는다.
- 단계 기반 진행률은 `currentStep / totalStep`과 "estimated" 표시를 분리한다.
- refresh 중에는 기존 데이터를 지우지 않고 작은 loading indicator만 표시한다.
- 마지막 성공 데이터와 현재 refresh 실패를 분리해서 표시한다.

### 6.2 이벤트 표시

Event tab은 사람이 읽을 수 있는 event stream을 제공한다.

필드:

- time
- severity
- source: `CLOUD`, `FTCTL`, `VMWARE`, `V2K`, `MOLD_AGENT`
- run id
- step id
- message
- raw reference id

표시 원칙:

- raw log 전체를 기본 노출하지 않는다.
- 상세 drawer에서 raw payload를 접힌 상태로 제공한다.
- 다크모드에서 code block과 raw payload 영역은 별도 token을 사용한다.

## 7. API 연동 설계

UI는 host 또는 qemu engine을 직접 호출하지 않는다. 모든 동작은 Cloud API를 통해 backend로 전달한다.

권장 API 매핑:

| UI 동작 | Cloud API |
| --- | --- |
| DR site 목록 | `listDrSites` |
| DR site 생성 | `createDrSite` |
| DR site 수정 | `updateDrSite` |
| DR site check | `checkDrSite` |
| DR plan 목록 | `listDrPlans` |
| DR plan 생성 | `createDrPlan` |
| DR plan 수정 | `updateDrPlan` |
| DR plan 삭제 | `deleteDrPlan` |
| 수동 sync | `startDrSync` |
| test failover | `startDrTestFailover` |
| failover | `startDrFailover` |
| failback | `startDrFailback` |
| reprotect | `startDrReprotect` |
| run 조회 | `listDrRuns`, `getDrRun` |
| restore point 조회 | `listDrRestorePoints` |
| replica 조회 | `listDrReplicas` |
| event 조회 | `listDrEvents` |

Async job 처리:

- action API가 `jobid`를 반환하면 UI는 job 완료까지 추적한다.
- job 성공 후에도 `DrRun` 또는 plan projection을 다시 조회해 최종 상태를 확인한다.
- job 실패는 modal close나 button loading 해제만으로 끝내지 않고 action result alert에 표시한다.
- polling timeout은 실패로 단정하지 않고 "상태 확인 필요"로 표시한 뒤 수동 refresh를 제공한다.

## 8. 다크모드 및 스타일 상세 설계

### 8.1 필수 원칙

다크모드 대응은 신규 UI의 필수 완료 조건이다. 구현 완료 판단 시 라이트모드만 확인해서는 안 된다.

구현 원칙:

- 색상은 hardcoded hex를 최소화하고 Ant Design Vue theme token, `color.less`, semantic class를 우선 사용한다.
- mode별 차이가 필요한 경우 `$store.getters.darkMode` 또는 `body.dark-mode .cross-dr-*` selector를 사용한다.
- 동일한 상태는 라이트/다크모드에서 같은 의미 색을 유지하되 대비와 채도만 조정한다.
- 상태 배지, alert, progress, table hover, selected row, disabled button, empty state는 다크모드 개별 검증 항목으로 둔다.
- 다크모드에서 순수 검정 배경 위 순수 흰색 텍스트만 사용하는 단조로운 화면을 만들지 않는다.
- 경고/오류 영역은 배경색보다 border, icon, title, secondary text의 계층으로 읽히게 한다.

### 8.2 Semantic style token

구현 시 다음 semantic token 또는 CSS custom property를 둔다.

| Token | Light 예시 | Dark 예시 | 용도 |
| --- | --- | --- | --- |
| `--cross-dr-surface` | page/card base | elevated dark surface | summary, panel |
| `--cross-dr-surface-muted` | subtle section bg | subtle dark section bg | KPI group, empty state |
| `--cross-dr-border` | neutral border | low contrast dark border | table, panel |
| `--cross-dr-text` | primary text | primary dark text | 제목, 값 |
| `--cross-dr-text-secondary` | secondary text | secondary dark text | 설명, timestamp |
| `--cross-dr-success` | success | success dark adjusted | READY, CONNECTED |
| `--cross-dr-warning` | warning | warning dark adjusted | DEGRADED, RPO lag |
| `--cross-dr-error` | error | error dark adjusted | ERROR, FAILED |
| `--cross-dr-info` | info | info dark adjusted | SYNCING, RUNNING |
| `--cross-dr-progress-track` | track bg | dark track bg | progress bar |
| `--cross-dr-code-bg` | raw payload bg | dark raw payload bg | event raw/detail |

이 token은 구현 시 실제 프로젝트의 theme 변수와 맞춰 정의한다. 문서의 예시값은 고정값이 아니라 역할을 설명하기 위한 것이다.

### 8.3 Component class 제안

신규 UI에는 다음 class prefix를 사용한다.

| Class | 용도 |
| --- | --- |
| `.cross-dr-page` | DR page root |
| `.cross-dr-toolbar` | 목록/상세 상단 액션 영역 |
| `.cross-dr-summary` | 상세 상단 summary band |
| `.cross-dr-kpi` | RPO, readiness, run duration KPI |
| `.cross-dr-status-pill` | 상태 배지 |
| `.cross-dr-risk` | warning/error callout |
| `.cross-dr-topology` | source to target topology |
| `.cross-dr-run-progress` | run progress wrapper |
| `.cross-dr-run-step` | run step row |
| `.cross-dr-event-log` | event stream |
| `.cross-dr-code` | raw payload/code 영역 |

`body.dark-mode .cross-dr-page` 하위에서 필요한 override를 정의한다. 컴포넌트 내부에서 mode별 inline style을 남발하지 않는다.

### 8.4 화면별 다크모드 검증 기준

| 화면 | 검증 기준 |
| --- | --- |
| DR Sites 목록 | table header, row hover, health badge, disconnected reason이 모두 읽힘 |
| DR Site 상세 | endpoint, credential alias, inventory table, event raw payload 대비 확보 |
| DR Plans 목록 | state badge, RPO 초과 warning, action disabled reason tooltip 가독성 |
| DR Plan Wizard | stepper, validation error, preflight warning/error, review summary 대비 확보 |
| DR Plan 상세 | summary band, KPI, topology, progress, risk callout의 계층 구분 |
| VM 상세 DR 탭 | 기존 Compute 화면과 배경/간격/텍스트 톤 일관성 |
| Confirm Modal | 위험 액션 제목, 영향 범위, checkbox/typed confirmation이 명확히 보임 |

### 8.5 상태 색상 규칙

| 상태 | 의미 색 | UI 표현 |
| --- | --- | --- |
| `READY`, `CONNECTED`, `TARGET_READY` | success | 배지 + 짧은 보조 텍스트 |
| `SYNCING`, `MATERIALIZING`, `RUNNING` | info | progress + 현재 단계 |
| `DEGRADED`, `RPO_EXCEEDED`, `STALE` | warning | warning callout + 해결 힌트 |
| `ERROR`, `FAILED`, `DISCONNECTED` | error | error callout + retry 가능 여부 |
| `PAUSED`, `DISABLED`, `UNKNOWN` | neutral | neutral badge + 다음 액션 안내 |

색상만으로 상태를 구분하지 않는다. 상태 label, icon, tooltip, 보조 문구를 함께 제공한다.

## 9. 반응형 레이아웃

Desktop:

- 목록 화면은 table 중심으로 구성한다.
- 상세 화면은 상단 summary band, 아래 tab content 구조를 사용한다.
- KPI는 4개에서 6개까지 한 줄 배치하되 좁아지면 wrap한다.
- topology는 full-width 영역으로 두고 decorative card 안에 넣지 않는다.

Tablet/Mobile:

- 목록 table은 주요 컬럼만 남기고 row expand로 상세 정보를 제공한다.
- 액션 툴바는 primary action 1개와 overflow menu로 축약한다.
- wizard는 단계 목록을 상단 compact stepper로 표시한다.
- 긴 VM 이름, datastore 이름, network 이름은 줄바꿈 가능해야 하며 버튼 너비를 밀어내면 안 된다.

텍스트 오버플로우:

- 버튼은 icon + 짧은 label을 사용하고 긴 설명은 tooltip/drawer로 보낸다.
- table cell은 `ellipsis`를 기본으로 하되 중요한 error는 두 줄까지 허용한다.
- card 또는 panel 안에서 hero-scale type을 사용하지 않는다.

## 10. 접근성 및 i18n

접근성:

- icon-only button은 tooltip과 `aria-label`을 제공한다.
- keyboard focus outline은 라이트/다크모드 모두에서 보여야 한다.
- destructive modal은 Enter 오작동을 줄이기 위해 명시적 confirm control을 둔다.
- progress는 색상 외에도 percent, step label, status text를 함께 표시한다.

i18n:

- enum raw value만 표시하지 않고 locale key를 둔다.
- 한국어/영어 label을 모두 추가할 수 있는 key 구조를 사용한다.
- action disabled reason도 locale key로 관리한다.

권장 locale key prefix:

- `label.dr.site.*`
- `label.dr.plan.*`
- `label.dr.restorePoint.*`
- `label.dr.replica.*`
- `label.dr.run.*`
- `label.dr.action.*`
- `message.dr.*`

## 11. 에러와 빈 상태

빈 상태:

- DR site가 없으면 site 등록 CTA를 표시한다.
- DR plan이 없으면 plan 생성 CTA를 표시한다.
- target-ready restore point가 없으면 failover 버튼은 비활성화하고 필요한 다음 작업을 보여준다.

에러 상태:

- adapter connection error와 Cloud async job failure를 구분한다.
- projection stale 상태는 "실제 보호 실패"로 단정하지 않고 "최근 상태 확인 실패"로 표시한다.
- run 실패는 실패 step, 원인, 재시도 가능 여부, 관련 event를 함께 보여준다.

## 12. 구현 단계 제안

UI 구현은 다음 순서로 진행한다.

| 단계 | 작업 | 완료 기준 |
| --- | --- | --- |
| 1 | route/resource config 추가 | menu에서 DR Sites/Plans 접근 가능 |
| 2 | API wrapper 추가 | list/create/action API 호출 가능 |
| 3 | DR Plans 목록 | 필터, 상태 배지, RPO, action disabled reason 표시 |
| 4 | DR Plan 상세 overview | summary, topology, readiness, current run 표시 |
| 5 | DR Plan wizard | source/target/mapping/policy/preflight/review 단계 구현 |
| 6 | run/restore/event tabs | progress, history, raw reference 표시 |
| 7 | VM 상세 DR 탭 | VM에서 plan projection과 주요 액션 표시 |
| 8 | 다크모드 polish | 모든 신규 화면 라이트/다크 스크린샷 검증 |
| 9 | 기존 FTCTL 연계 | FTCTL 기반 plan에서 기존 FTCTL 탭 링크 및 상태 projection 검증 |

## 13. 테스트 기준

수동 검증:

- 라이트모드와 다크모드에서 DR Sites 목록/상세가 모두 정상 표시된다.
- 라이트모드와 다크모드에서 DR Plans 목록/상세/wizard가 모두 정상 표시된다.
- `READY` plan에서 failover action이 활성화되고, target-ready restore point가 없으면 비활성화된다.
- async job 실패 시 UI가 성공으로 오인하지 않고 실패 alert를 표시한다.
- refresh 중 기존 화면 데이터가 전체적으로 사라지지 않는다.
- 기존 `Fault Tolerance` 탭은 신규 UI 추가 후에도 동작과 layout이 유지된다.
- 모바일 폭에서 action button, 긴 VM 이름, error message가 서로 겹치지 않는다.

자동 검증:

- 주요 컴포넌트의 action gating helper unit test를 추가한다.
- status to color/label mapping unit test를 추가한다.
- API response mock 기반 목록/상세 rendering test를 추가한다.
- 가능하면 Playwright로 라이트/다크모드 screenshot smoke를 추가한다.

## 14. AS-IS / TO-BE 요약

| 구분 | AS-IS | TO-BE |
| --- | --- | --- |
| UI 모델 | `DisasterRecoveryCluster`, FTCTL 탭 중심 | `DrSite`, `DrPlan`, `DrRestorePoint`, `DrReplica`, `DrRun` 중심 |
| 보호 단위 | 기존 DR cluster 또는 FTCTL protection에 종속 | VM 단위 `DrPlan`으로 통합 |
| 진행률 | 기능별 개별 표현 | `DrRun`/`DrRunStep` 기반 공통 진행률 |
| 장애 전환 판단 | 기능별 화면을 따로 확인 | target-ready restore point, replica readiness, RPO를 한 화면에서 확인 |
| 액션 게이트 | 화면별 개별 조건 | `DrPlan.state`, `DrReplica.state`, restore point readiness 기반 공통 게이트 |
| FTCTL 연계 | FTCTL 탭이 직접 운영 중심 | FTCTL은 기존 탭 유지, 신규 DR UI에서는 engine projection |
| VMware 연계 | 공통 DR UI 없음 | VMware source/target도 같은 DR plan UX로 표시 |
| 다크모드 | 화면별 부분 대응 | 신규 DR UI 전체에서 token/selector 기반 필수 대응 |
| 비동기 결과 | 클릭/API 성공으로 오해 가능 | async job + `DrRun` + projection 재조회로 최종 상태 확인 |

## 15. 미결정 사항

- 기존 `DisasterRecoveryCluster` 메뉴를 신규 `DR Sites`로 언제 통합할지 결정이 필요하다.
- `DrRun` progress percent를 backend가 직접 제공할지, step 기반 estimated progress로만 표시할지 결정이 필요하다.
- VMware target test failover에서 격리 네트워크를 필수로 강제할지 정책 결정이 필요하다.
- mobile에서 DR Plans 목록을 table 축약형으로 둘지 card list로 전환할지 UX 검증이 필요하다.
- 다크모드 token을 전역 `color.less`에 추가할지, DR UI scoped style로 시작할지 구현 단계에서 결정한다.
