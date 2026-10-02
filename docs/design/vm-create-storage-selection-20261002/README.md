<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

  https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# VM 생성 시 오퍼링에 맞는 기본 스토리지 선택

검토일: 2026-10-02. 검토 기준: upstream Europa f862c21f167d05641c75809d1c64659dcf24c20b.

구현 전 설계 자료다. 현재 VM 생성 화면과 API/배치 코드를 검토했으며, VM 생성·배포·제품 코드 수정은 수행하지 않았다. 목업의 Primary-02/03과 용량은 설명용 예시다.

## 현재 화면 및 코드

- 기존 화면은 계정 → 배포 인프라 → 이미지 → 컴퓨트 오퍼링 → 데이터 디스크 → 네트워크 → SSH → 확장 모드 → 상세의 단계형 폼과 우측 VM 요약이다.
- 오퍼링 표에는 스토리지 태그가 있으나 기본 스토리지 이름·할당/여유 용량·선택 UI는 없다. 확장 모드에도 해당 선택 기능은 없다.
- DeployVM.vue의 storagePoolObjects는 초기 상태에만 선언되어 있고 현재 조회/선택/전송에 사용되지 않는다.
- deployVirtualMachine 사용자/관리자 API에 디스크별 스토리지 UUID 지정 인자가 없다. VmDiskInfo와 데이터 디스크 파서에도 전달 정보가 없다.
- 기존 계정/Global 선호 스토리지는 후보 순서를 바꾸고 나머지 후보를 유지한다. 사용자가 선택한 스토리지에 반드시 생성하는 계약으로 사용할 수 없다.
- listStoragePools는 관리자 API이며 오퍼링 기반 배포 후보 조회 API가 아니다. 전체 목록을 UI에서 태그 하나로 걸러 배치 가능하다고 판단하면 서버 결과와 달라질 수 있다.
- 실제 31번 클러스터에는 glue-gfs 태그의 Primary와 다른 태그의 CLVM/CLVM-NG가 있다. 현재 검토에서 다중 호환 풀 배치가 검증된 것은 아니다.

| 근거 | 위치 |
| --- | --- |
| 생성 폼과 디스크 전송 경로 | [DeployVM.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/ui/src/views/compute/DeployVM.vue#L2614) |
| ISO 오퍼링은 ROOT, 템플릿 오퍼링은 DATA | [BaseDeployVMCmd.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/api/src/main/java/org/apache/cloudstack/api/command/user/vm/BaseDeployVMCmd.java#L118) |
| 데이터 디스크 파서 | [BaseDeployVMCmd.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/api/src/main/java/org/apache/cloudstack/api/command/user/vm/BaseDeployVMCmd.java#L589) |
| 기존 용량 응답 | [StoragePoolResponse.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/api/src/main/java/org/apache/cloudstack/api/response/StoragePoolResponse.java#L80) |
| 할당 용량에 예약 용량 포함 | [StoragePoolJoinDaoImpl.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/server/src/main/java/com/cloud/api/query/dao/StoragePoolJoinDaoImpl.java#L151) |
| 배포 스토리지 후보 조회 | [DeploymentPlanningManagerImpl.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/server/src/main/java/com/cloud/deploy/DeploymentPlanningManagerImpl.java#L1836) |
| 선호 풀 후 다른 후보 유지 | [DeploymentPlanningManagerImpl.java](https://github.com/ablecloud-team/ablestack-cloud/blob/f862c21f167d05641c75809d1c64659dcf24c20b/server/src/main/java/com/cloud/deploy/DeploymentPlanningManagerImpl.java#L1944) |

## 화면 개선안

1. 컴퓨트 오퍼링/유효 루트 디스크 오퍼링 아래에 **루트 디스크의 기본 스토리지**를 배치한다. 루트 오퍼링 무시를 사용하면 변경된 오퍼링으로 다시 조회한다.
2. 데이터 디스크 오퍼링과 크기 아래에 **데이터 디스크의 기본 스토리지**를 배치한다. 데이터 없음이면 숨기고, 여러 데이터 디스크는 각 디스크에서 선택한다.
3. 기존 표·라디오 선택과 상단 도구 모음을 사용한다. **업데이트 아이콘 + 업데이트**, 이름 검색을 제공한다. 버튼 모양과 주 액션인 우측 파란 VM 시작 버튼을 유지한다.
4. 우측 VM 요약에 루트/데이터별 스토리지 이름, 크기, 직접/자동 선택 여부를 표시한다. 요약이 길면 내용 영역을 스크롤하고 생성 버튼을 같은 요약 하단에 유지한다.
5. 기존 자동 배치를 기본값으로 보존한다. 직접 선택한 풀은 최초 생성/배치의 필수 조건이며 불가능하면 이유를 표시한다.
6. 조회 중 기존 표/선택을 유지하고 바뀐 행만 갱신한다. 화면 전체를 비우거나 최초 조회 완료 전에 호환 불가 경고를 표시하지 않는다.

### 용량 표시

| 열 | 의미 |
| --- | --- |
| 기본 스토리지 | 이름, 종류, 범위, 상태 |
| 총 용량 | 해당 스토리지가 보고하는 용량. 종류별 의미를 서버 계약에 명시 |
| 할당 용량 | 논리 할당 용량과 예약 용량 |
| 물리 여유 | 실제 총량/사용량을 확인할 수 있는 풀의 물리 여유. 미확인은 — |
| 추가 할당 가능 | 기존 배치 정책의 오버프로비저닝·예약·임계치·선택한 전체 디스크 조건을 적용한 가용량 |

31번 SharedMountPoint의 할당 용량과 실제 사용량은 크게 다르다. 총 용량에서 할당 용량을 뺀 값을 물리 여유로 표시하지 않는다. 관리형 스토리지 등에서 capacitybytes를 물리 총량으로 일괄 가정하지 않고, 물리 여유는 드라이버/통계 근거가 있을 때만 제공한다. 서버 바이트 값을 일관된 단위로 변환하고 조회 시각을 제공한다.

추가 할당 가능 용량은 UI 단순 계산으로 만들지 않는다. 같은 풀의 루트/데이터 디스크와 VM 여러 대를 합산하고 템플릿/스냅샷 크기 및 필요한 여유를 기존 서버 검사와 일치시킨다. 표시 값이 용량 예약을 보장하지 않으므로 생성 시점에 다시 검사한다.

## 서버 구현 방향

- 계정/프로젝트 및 호출자 권한을 확인하는 배포 후보 조회 API를 제공한다. 일반 사용자에게 관리자 전체 풀 조회 권한을 부여하지 않고 허용된 후보·필요한 용량 정보만 반환한다.
- 실제 allocator/배포 검증을 재사용한다. 유효 오퍼링 태그, 공유/로컬 범위, Zone/Pod/Cluster/Host, 하이퍼바이저, 사용/점검 상태, 접근 그룹, provisioning/형식, 암호화, 용량/IOPS를 함께 판정한다. 호스트 태그와 스토리지 태그를 혼동하지 않는다.
- 루트·데이터 풀에 공통으로 배포 가능한 호스트가 있는지 확인한다. 서로 다른 클러스터/로컬 풀을 독립 선택해 불가능한 조합을 만들지 않는다.
- 생성 API → VM/볼륨 생성 정보 → 배포 계획 → 볼륨 allocator로 디스크별 UUID를 전달한다. 제안 인자는 rootstorageid, datadisksdetails[i].storageid이며 **현재 지원 인자가 아니다**. 구현 시 실제 계약을 확정한다.
- 단일 diskofferingid/size, 다중 datadisksdetails, 자식 템플릿 datadiskofferinglist 경로를 모두 지원한다. 디스크와 UUID는 device ID/자식 템플릿 ID에 안정적으로 대응시키고 KMS·사용자 지정 크기/IOPS를 유지한다.
- ISO 배포에서 diskofferingid는 루트 볼륨이다. 템플릿용 DATA 선택을 그대로 적용하지 않는다. 기존 볼륨/스냅샷의 배치 의미도 유지하고 선택 UI를 이유로 자동 이동하지 않는다.
- 명시한 UUID는 최초 배치의 강제 조건으로 필터링한다. 선호 순서 조정으로 처리하지 않는다. 권한·오퍼링·전체 용량을 생성/최초 시작 직전에 재검증한다.
- 비동기 재시도 및 생성만 하기→최초 시작에도 선택 조건을 보존한다. 무통보 대체 풀 배치를 금지한다. 이후 명시적 마이그레이션은 별도 기존 권한/정책에 따르며 최초 선택으로 영구 차단하지 않는다.
- UUID 미지정 요청은 기존 자동 배치와 호환성을 유지한다.

## 상태 및 갱신

| 상황 | 처리 |
| --- | --- |
| 최초 조회/업데이트 중 | 조회 상태, 이전 표/선택 유지. 미확인을 0/충분으로 표시하지 않음 |
| 호환 후보 없음 | 성공한 조회 결과에만 불일치 안내. 배포 가능 조합이 없으면 생성 차단 |
| 용량 부족/상태 변경 | 선택 불가와 이유 표시. 이전 선택 유지 및 재선택 안내. 자동 대체 금지 |
| 목록/용량 조회 실패 | 표 안에 제품 메시지, 상단 업데이트로 재시도. 직접 선택 유효성 확인 전 생성 차단 |
| 이미지/오퍼링/크기/인프라/VM 개수 변경 | 재조회. 유효 선택 유지, 무효 선택은 재선택 요구. 늦은 응답 무시 |
| 데이터 디스크 해제 | 데이터 풀 선택 제거, 루트 선택 유지 |
| 생성 시 재검증 실패 | 실패 이유와 기존 입력/선택 보존 |

## 단계별 구현 및 완료 기준

- [ ] 후보/용량/디스크별 UUID API 계약과 권한 확정.
- [ ] 서버 후보 조회와 최초 배치 강제 조건 구현, WSL ext4에서 관련 Maven 변경 모듈 빌드.
- [ ] 기존 VM 생성 UI의 표/선택/요약/조회 상태 구현 및 UI 빌드.
- [ ] 테스트 클러스터에 변경 모듈과 정적 UI 배포, 일반/다크 모드 검증.
- [ ] 호환 풀 두 개 이상에서 실제 루트/데이터 볼륨 pool ID와 호스트 디스크 경로가 선택한 풀과 일치함을 검증.
- [ ] 태그 불일치, 다른 클러스터/로컬 조합, 용량 부족, 권한 위반, 점검/삭제 풀, 용량 경쟁, 동일 풀 합산 초과 거절 및 무통보 대체 배치 금지 검증.
- [ ] 자동 선택, 템플릿/ISO, 루트 오퍼링 무시, 데이터 없음/여러 개, KMS, 사용자 지정 크기/IOPS, VM 여러 대, 생성만 하기→최초 시작 회귀 검증.
- [ ] 조회 갱신 중 입력/선택 보존, 화면 깜빡임 없음, 선택 불가 사유, 업데이트 아이콘·문구 확인.

전체 Cloud 빌드는 이번 설계 범위가 아니다. 구현 단계도 변경 모듈 빌드를 우선하고 전체 빌드는 별도 요청이 있을 때만 수행한다.

## 목업

기존 단계 번호를 유지한다. 루트/데이터 이미지는 해당 선택 영역에 집중하여 다른 단계를 생략한 화면이며 단계 제거 요구가 아니다. 예외 이미지는 상태별 비교 자료이며 별도 제품 화면이 아니다.

| 이미지 | 일반 모드 | 다크 모드 |
| --- | --- | --- |
| 루트 선택 | [이미지](images/root-storage-light.jpg) | [이미지](images/root-storage-dark.jpg) |
| 데이터 선택/용량 부족 | [이미지](images/data-storage-light.jpg) | [이미지](images/data-storage-dark.jpg) |
| 예외 상태 | [이미지](images/states-light.jpg) | [이미지](images/states-dark.jpg) |

현재 화면: [current-ui.jpg](images/current-ui.jpg).

정적 목업: [mockup.html](mockup.html). 로컬 HTTP 서버에서 ?theme=light&part=root, ?theme=dark&part=data, ?theme=dark&view=states로 확인한다. 서버/API 연결이나 VM 생성 동작은 없다.
