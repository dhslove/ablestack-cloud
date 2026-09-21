## 코드 변경 상세 계획 — 리뷰용 (2026-09-21)

아래는 **구현 전 설계안**입니다. 실제 소스 변경이나 테스트 클러스터 변경은 이번 작업에 포함하지 않습니다.
소스 확인 기준: `53e1e0e0189b8955ddd2292832325671d2c36927` (Europa 기반 UI 작업 브랜치). 구현 시작 시 최신 upstream과 차이를 다시 확인합니다.

### 사용자 동작 계약
- 기존 백업을 삭제하지 않고 메모리 포함 VM 스냅샷을 생성할 수 있도록 조건부 지원합니다.
- 복원 시 기존 증분 추적 상태를 안전하게 종료하고, **다음 예약 또는 수동 백업을 전체 백업**으로 실행합니다. 복원 직후 백업 자동 실행은 하지 않습니다.
- 기존 복구 지점의 부모/자식 체인과 보관 파일은 유지합니다.
- 생성만 하는 경우에는 가능한 한 백업 세대를 바꾸지 않습니다. 다만 내부 스냅샷과 체크포인트 공존 PoC에서 생성 전 정리가 필수로 확인되면 생성 때도 세대 전환 및 다음 전체 백업이 필요합니다. 이 경우 UI 문구와 동작 계약을 먼저 수정하며, “복원 때만 전체 백업”을 검증 없이 보장하지 않습니다.
- 메모리 스냅샷 보유 중 백업 재개 지원까지 PoC 범위에 포함합니다. 안전한 공존이 검증되지 않으면 제한을 명시하고 기능 완성으로 보고하지 않습니다.

### 1. NAS 제공자 및 증분 부모 선택
파일: `plugins/backup/ablestack-nas/src/main/java/org/apache/cloudstack/backup/AblestackNasBackupProvider.java`
- 변경 메서드: `supportsMemoryVmSnapshot()`, `assignVMToBackupOffering()`, `validateNoKvmFileBasedVmSnapshots()`, `getLatestBackedUpBackup()`, `shouldUseIncrementalBackup()`, `takeBackup()`.
- 재사용: `sealBackupChain()`, `nas.chain.sealed`, `nas.chain.seal.reason`. 현재 코드도 sealed 체인이면 전체 백업을 선택합니다.
- 단순히 supportsMemoryVmSnapshot()을 true로 바꾸지 않습니다. 기능 설정과 VM별 사전 검사/복원 준비 기능이 갖춰진 뒤 제한된 대상에만 활성화합니다.
- 현재 getLatestBackedUpBackup()은 **backupScheduleId별** 부모를 찾습니다. 수동 백업과 여러 예약 모두가 이전 세대의 부모를 재사용하지 않도록 VM 백업 세대와 백업별 세대를 비교합니다.
- 전체 백업 실패 시 전체 백업 필요 상태를 유지합니다. 성공 결과가 요청 당시 세대와 일치할 때만 해당 체인의 새 기준점으로 인정합니다.
- 한 예약의 전체 백업 성공이 다른 예약의 오래된 부모를 유효하게 만들면 안 됩니다.
- 기존 backup restore, 보존/삭제 로직은 과거 세대를 “복원 가능하지만 증분 부모로 사용 불가”로 취급합니다.

### 2. 관리 서버의 복원 조정 및 제공자 계약
대상:
- `engine/storage/snapshot/src/main/java/org/apache/cloudstack/storage/vmsnapshot/DefaultVMSnapshotStrategy.java`: `canHandle(...)`, `revertVMSnapshot()`, 생성/삭제 처리.
- `server/src/main/java/com/cloud/vm/snapshot/VMSnapshotManagerImpl.java`: 생성·복원 요청 사전 검사와 작업 직렬화.
- `api/src/main/java/org/apache/cloudstack/backup/BackupProvider.java` 및 BackupManager 계약.
- `server/src/main/java/org/apache/cloudstack/backup/BackupManagerImpl.java`: 백업 진입점, 예약 실행, 완료 처리 및 복원과의 상호 배제.

**신규 제안 계약명**(현재 존재하는 메서드가 아님):
- validateMemorySnapshotCompatibility(vm, operation)
- prepareMemorySnapshotRestore(vm, snapshotId, operationId)
- reconcileMemorySnapshotRestore(operationId, observedResult)

NAS 구현 의존성을 snapshot 모듈에 직접 주입하지 않고 백업 관리 서비스/제공자 계약으로 호출합니다. 타 제공자는 기존 정책을 유지합니다. JVM 내부 synchronized만 쓰지 않고 다중 관리 서버에서 동일 VM의 백업·복원·스냅샷·마이그레이션 충돌을 막는 기존 작업 직렬화/DB 잠금 규칙과 통합합니다.

### 3. 영속 상태와 멱등성
신규 논리 상태안:
- VM: backupGeneration, restoreOperationId, restorePhase, nextBackupMode, reason.
- 백업: 생성 당시 generation, 이미 존재하는 chain sealed/reason.
- operationId와 VM 세대를 묶어 재시도 시 동일 복원 준비를 반복 적용하지 않습니다.
- 저장 위치는 기존 VM details/backup_details 재사용을 우선 검토합니다. 원자적 비교·갱신 및 작업 이력을 보장하지 못하면 별도 테이블/DAO와 해당 Europa DB 업그레이드 스크립트를 추가합니다. **이 저장 방식은 구현 전 리뷰 결정 항목입니다.**

복원 순서:
1. VM 단위 작업 잠금 → 실행 중 작업/지원 조건/소유 체크포인트 검사.
2. 복원 준비 상태와 새 세대/전체 백업 필요 상태를 영속화. 준비 완료 전 agent에 변경 명령을 보내지 않음.
3. agent에서 해당 VM의 NAS 소유 체크포인트/비트맵을 검증·정리 후 스냅샷 복원.
4. 성공 시 Cloud VM/스냅샷 상태 갱신. 다음 백업은 새 세대 전체 백업.
5. agent 타임아웃은 결과 미확정으로 기록하고 런타임을 재조회. 미확정 상태에서는 백업 재개 금지.
6. 일부 추적 정보를 정리한 뒤 실패했다면 이전 증분 부모로 되돌리지 않음. 디스크 상태가 확인되면 전체 백업 필요 상태 유지; VM 상태가 불명확하면 복구 필요 상태 유지.

기존 백업 파일/복원 메타데이터 삭제는 보상 동작으로 사용하지 않습니다.

### 4. KVM agent와 NAS 스크립트
대상:
- `core/src/main/java/com/cloud/agent/api/CreateVMSnapshotCommand.java`
- `core/src/main/java/com/cloud/agent/api/RevertToVMSnapshotCommand.java` / Answer
- `core/src/main/java/org/apache/cloudstack/backup/AblestackNasTakeBackupCommand.java`
- `plugins/hypervisors/kvm/src/main/java/com/cloud/hypervisor/kvm/resource/wrapper/LibvirtCreateVMSnapshotCommandWrapper.java`
- 같은 디렉터리 `LibvirtRevertToVMSnapshotCommandWrapper.java`, `LibvirtAblestackNasBackupHelper.java`, `LibvirtAblestackNasTakeBackupCommandWrapper.java`.
- `scripts/vm/hypervisor/kvm/ablestack_nasbackup.sh`.
- `LibvirtComputingResource.java`의 checkpoint 관련 공통 처리와 신규 helper 추출 여부 검토.

변경 내용:
- 명령에 operationId, 예상 세대, 검사된 대상 디스크/체크포인트 식별자를 전달하고 응답으로 처리 단계를 반환하는 방안 검토.
- 호스트에서 실제 VM·디스크 경로·체크포인트·비트맵을 다시 검사. 이름 접두사만으로 소유권 판단하지 않음.
- 알 수 없는 비트맵, 실행 중 block job, 일부 디스크만 준비된 상태는 복원 진행 금지.
- 기존 `removeCheckpointsOnVm()`의 metadata-only 삭제만으로는 비트맵 제거를 보장하지 못하므로 그대로 재사용하지 않음.
- libvirt checkpoint metadata와 QEMU bitmap을 함께 정합성 있게 처리. 실행 중 이미지에 오프라인 qemu-img 수정을 하지 않음.
- 전체 백업 요청은 이전 parent/checkpoint 인수를 비워 전달하고, 성공 후 새 checkpoint만 현재 세대에 연결.
- 구 agent가 새 안전 프로토콜을 지원하지 않으면 활성화하지 않음.
- NVRAM 포함 내부 snapshot QMP capability와 생성·복원 후 XML/디스크/메모리 상태 검증. 외부 메모리 방식은 별도 대안이며 이번 설계에서 확정 구현으로 취급하지 않음.

### 5. UI 및 API 응답
대상:
- `ui/src/views/compute/VmSnapshotsTab.vue`
- `ui/src/utils/vmSnapshotActions.js`
- `ui/src/config/section/compute.js`: createVMSnapshot / revertToVMSnapshot 동작
- 백업 탭 표시 컴포넌트 및 한국어/영어 locale.
- VM/백업 API 응답 DTO 및 응답 생성부: capability, 제한 사유, 다음 백업 모드 표시 필드 추가 검토. UI에서 provider 이름만 보고 지원 가능 여부를 추론하지 않음.

화면 변경:
1. 생성: 기존 백업 보존 및 복원 후 전체 백업 안내. 미지원/충돌 상태는 구체적인 사유 표시.
2. 복원: 디스크·메모리 되돌림, 다음 백업 전체 전환, 기존 백업 보존, 자동 백업 미실행을 명확히 표시.
3. 백업: 복원 후 “다음 백업: 전체 백업” 상태 표시. API의 실제 세대/체인 상태를 반영하고 새로고침/재로그인 후 유지.
4. 다중 예약 일부만 새 전체 백업 완료된 경우 “모든 백업이 증분 가능”으로 표시하지 않음. 표시 범위를 예약/수동 백업별로 구분.
5. 대화상자 중앙 정렬, 제목/하단 버튼 고정, 내용만 스크롤. 아래 목업은 지원 검사 통과한 단일 정책 예시이며 실제 완료 화면이 아님.

### 6. 구현 순서와 검증
- P0: 폐기 가능한 VM에서 내부 메모리 스냅샷/체크포인트 공존, Q35/UEFI QCOW2 NVRAM 복원 PoC.
- P1: 세대 상태·작업 잠금·provider 계약·agent 준비/복원 및 실패 복구.
- P2: NAS 부모 선택 및 전체→증분 전환, 기존 백업 복구 회귀.
- P3: UI/오류 메시지/문서, feature gate를 검증된 조합에만 적용.
- 단위/통합 테스트: 예약 2개+수동 백업, 전체 백업 실패, 응답 유실, agent/관리 서버 재시작, 중복 요청, 알려지지 않은 bitmap, 다중 디스크 부분 실패, 오래된 agent.
- 런타임 테스트: 메모리/디스크/NVRAM 복원, 기존 백업 복구, 복원 후 새 FULL→INCREMENTAL→복구, 라이브마이그레이션 후 동일 검증.
- 빌드: 변경된 Maven 모듈만 WSL ext4 clone에서 빌드. UI 모듈 별도 빌드 및 라이선스 검사. 전체 Cloud 빌드는 이 계획에 포함하지 않음.

**리뷰 핵심:** 초기 지원 범위, 생성 시 체크포인트 공존 실증, 영속 상태 저장 방식, 스냅샷 보유 중 백업 허용 정책을 먼저 확정해야 합니다.

