<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->
# VM 장치 탭 관리 기능 개선 설계

## 목적과 조사 범위

VM 상세의 장치 탭을 VM 스냅샷 탭과 같은 툴바·단일 테이블 구성으로 정리하고, VM 문맥에서 장치 할당·해제·상세 조회·잔여 설정 점검을 제공한다. vHBA 생성 및 미할당 vHBA 삭제도 관련 흐름에 포함한다.

- 분석 기준: upstream `ablestack-europa`, `e5fb9b5eb7582eece4efe9f9b615c56d748c83a0`.
- 31번 클러스터의 실제 VM 장치 탭에서 PCI/기타, HBA, vHBA, USB, LUN, SCSI의 6개 조회 전용 표와 빈 상태를 확인했다.
- 이번 산출물은 소스 분석과 **정적 이미지 설계**다. 실장치 할당·해제, 런타임 정합성 검증, 제품 구현·배포는 수행하지 않았다. 이미지의 장치와 VM은 예시 데이터다.

## 확인한 현재 동작

`InstanceTab.vue`는 `listVmDeviceAssignments` 결과를 장치 유형별로 분리한다. LUN 설명이 비어 있으면 호스트 LUN 조회를 보완 호출한다. 현재 탭에는 할당/해제 버튼이 없고, 호스트의 `ListHostDevicesTab.vue`와 유형별 `Host*DevicesTransfer.vue`에 관리 기능이 분산되어 있다.

`listVmDeviceAssignments`는 VM 접근 권한을 검사하지만 **DB 할당 기록만 반환**한다. `listVmHostDevices`도 DB 기준이며 실제 연결 상태를 확인하는 API가 아니다. 응답의 `hostid`는 내부 숫자 ID인 반면 여러 관리 API 인자는 UUID 형식이므로, 내부 ID를 그대로 전달하는 구현을 복제하지 않는다. 호스트 UUID 및 해제용 VM 내부 ID의 변환 경로를 명확히 하거나 기존 API 응답/인자 계약을 보완해야 한다.

## 기존 API 매핑

| 기능 | API / 핵심 인자 | 구현상 의미 및 주의점 |
|---|---|---|
| VM 할당 목록 | `listVmDeviceAssignments(virtualmachineid)` | 유형·장치명·설명·hostid·hostname. 소유 VM 접근 검사. 실제 연결 여부 미제공 |
| VM의 호스트 장치 묶음 | `listVmHostDevices(virtualmachineid)` | 역시 DB 기록 기반. 런타임 조회로 대체 사용하지 않음 |
| PCI 후보 | `listHostDevices(id)` | 호스트 장치/할당 정보 |
| USB 후보 | `listHostUsbDevices(id)` | 같은 호스트의 USB 후보 |
| LUN 후보 | `listHostLunDevices(id, lunpathmode)` | single / multipath. 동일 WWID와 SCSI 중복 식별 필요 |
| SCSI 후보 | `listHostScsiDevices(id)` | SCSI 주소, 경로·상세 사용 |
| HBA 후보 | `listHostHbaDevices(id)` | 물리 HBA/NPIV 가능 여부 확인 |
| vHBA 후보 | `listVhbaDevices(hostid, keyword)` | 기존 vHBA 목록 |
| PCI 할당/해제 | `updateHostDevices` | DB 및 extraconfig 변경. 이 메서드에는 즉시 attach/detach 에이전트 호출이 없음 |
| USB/LUN/SCSI/HBA/vHBA 할당/해제 | `updateHostUsbDevices`, `updateHostLunDevices`, `updateHostScsiDevices`, `updateHostHbaDevices`, `updateHostVhbaDevices` | 에이전트 호출 후 할당/설정 갱신. 유형별 응답과 오류 처리 차이 있음 |
| vHBA 생성 | `createVhbaDevice(hostid,parenthbaname,vhbaname,xmlconfig[,wwnn,wwpn])` | 호스트 가상 장치 생성. VM 할당은 별도 단계 |
| vHBA 삭제 | `deleteVhbaDevice(hostid,hostdevicesname 또는 wwnn)` | 호스트의 가상 HBA 자체 삭제. VM 할당 해제와 구별 |

관리 API는 Admin 중심의 기존 권한을 유지하고 실제 API 권한도 검사한다. 일반 사용자는 허용된 VM 할당 조회만 제공하며 관리 버튼을 노출하지 않는다.

공통 update 인자: `hostid`, `hostdevicesname`, `hostdevicestext`, `xmlconfig`; 할당 시 `virtualmachineid`, 해제 시 이를 생략하고 유형이 지원하는 `currentvmid`로 대상 할당을 지정한다. `currentvmid`는 일부 구현에서 숫자 문자열을 파싱하므로 UUID를 무조건 넣으면 안 된다. PCI의 해제 경로는 이 조건 검사가 동일하지 않아 별도 보완 대상이다. 이 API들은 BaseCmd/BaseListCmd 계열의 동기 응답이므로 존재하지 않는 jobid를 전제로 진행률을 만들지 않는다.

## UI 개선 방향

1. 탭 안의 제목을 제거하고 `장치 할당` → 텍스트 `새로고침` → 유형 필터/검색 순으로 배치한다. 단일 테이블에 유형, 이름/설명, 호스트, 할당 상태, 작업을 표시한다. 데이터가 없어도 탭·툴바는 유지한다.
2. 대표 작업은 `할당 해제` 버튼, 나머지는 드롭다운으로 제공한다. `상세`는 마지막. PCI는 실제 동작을 반영해 `할당 설정 해제` 문구 및 실행 중 비활성화 사유를 제공한다.
3. VM은 현재 상세 대상에 고정한다. 할당 가능 호스트와 장치만 선택한다. 다른 VM 장치 강제 재할당은 제공하지 않는다. 초기 버전은 한 번에 한 장치만 처리한다.
4. 할당 대화상자에서 기존 장치 / vHBA 생성 및 할당을 선택한다. LUN 경로, SCSI 주소, HBA 상하위 관계, USB 식별자, PCI BDF 등 유형에 맞는 정보를 표시한다. XML 직접 편집은 기본 UX에 노출하지 않는다.
5. vHBA 생성·할당은 두 단계로 표시한다. 생성 성공 후 할당 실패 시 생성 장치를 표시하고, 상태 재조회 후 재할당 또는 미할당 vHBA 삭제를 안내한다. 성공한 생성 단계를 무조건 재실행하지 않는다. WWN 자동 생성 지원과 실제 생성 식별자는 기존 생성 응답/호스트 동작을 검증한 뒤 확정한다.
6. `할당 기록`, `확인 필요`, `실제 연결 미확인`을 구별한다. API가 제공하지 않는 연결됨/정상 상태를 추정 표시하지 않는다.
7. **정리의 의미를 분리한다.** 일반 할당 해제 / 잔여 설정 점검 및 정리 / 미할당 vHBA 삭제는 서로 다른 작업이다. VM 탭에서 물리 장치를 삭제하거나 공용 디스크를 지우지 않는다.
8. 정리 가능 여부를 확정할 수 없으면 버튼을 비활성화하고 사유를 표시한다. 조회 실패를 장치 부재로 취급하지 않는다. 목록 진입/새로고침은 자동 해제·정리의 계기가 되어서는 안 된다.

## 선행 보완과 위험 경계

기존 API 재사용을 기본으로 하되 아래 문제를 UI만으로 해결했다고 간주하지 않는다. 새로운 API를 바로 추가하기보다 기존 계약을 보완할지 먼저 결정한다.

- PCI UI는 현재 `updateVirtualMachine`의 extraconfig 변경 후 `updateHostDevices`를 호출한다. 두 단계 사이 실패와 다른 extraconfig 유실을 피하도록 단일 소유 처리 경로를 정하고, 실제 적용 시점 및 정지/시작 후 유지 여부를 검증한다. 기존 `updateHostDevices(xmlconfig)`의 설정 저장 기능 활용 가능성을 검토한다.
- USB 재할당 경로는 이전 VM detach 실패에도 계속 진행하는 코드가 있다. 신규 VM 탭은 이를 실행하지 않도록 하고, 서버에서도 이미 할당된 장치에 대한 경쟁 요청을 차단해야 한다.
- 여러 해제 경로는 `currentvmid`가 없으면 첫 할당을 선택할 수 있다. 대상 VM/호스트/장치의 소유 관계를 서버에서 재검증하며, 타입별 공유 장치 정책을 임의로 변경하지 않는다.
- 기존 USB 선택 UI에는 VM 조회 실패/삭제 상태를 계기로 자동 해제하는 경로가 있다. 신규 목록은 이를 재사용하지 않는다. 사용자 명시 요청과 확정된 근거 없는 정리를 금지한다.
- 호스트 조회는 후보 장치·할당 정보를 제공하지만 VM의 실제 연결 상태를 완전하게 증명하지 않는다. **안전한 잔여 설정 정리는 런타임 미사용 확인 + 소유권 + 설정 범위 검증을 서버에서 보장하는 경로가 마련된 뒤 활성화한다.** 목록에서 사라졌다는 이유만으로 DB 설정을 지우는 기능은 제공하지 않는다.
- USB/LUN/SCSI/HBA/vHBA 에이전트는 virsh attach/detach를 사용한다. 재시작 후 지속성 및 실제 반영은 별도로 검증한다. SCSI 처리의 연관 LUN 변경도 테스트한다.
- 현재 UI의 지원 상태는 타입마다 다르다(PCI/USB/LUN/SCSI: 주로 Running, vHBA: 더 넓은 상태). HBA 서버에는 Running 검사가 있다. 신규 상태 행렬은 이를 그대로 통일하지 않고 유형별로 확정한다. 전이 상태·호스트 미확인·권한 부족·조회 실패는 차단한다.
- VM 스냅샷이 있는 경우 장치 토폴로지 변경은 우선 차단하는 방향을 제안한다. 기존 NIC/볼륨의 스냅샷 조회 가드를 참고하되, 장치 백엔드의 기존 제약이라고 주장하지 않는다. 적용 정책과 서버 측 검증 범위를 구현 전에 확정한다.
- 호스트 OS/관리 네트워크/스토리지에 사용되는 물리 장치, IOMMU 그룹, NPIV 지원, LUN/SCSI 동일 자원 식별 등은 실제 호스트 검증 없이 “할당 가능”으로 확정하지 않는다.

## 대화상자 및 테마 원칙

화면 중앙 정렬. 상단 제목과 하단 확인/취소 영역 고정. 긴 본문만 스크롤. VM/호스트/계정은 레이블과 값을 명확히 분리한다. 입력 폭·좌우 패딩·버튼 높이를 통일한다. 다크/라이트 모두 본문·안내·경고·placeholder·드롭다운·페이지 번호·스크롤바에 테마 색상을 적용한다.

정적 이미지 14장: 목록 다크/라이트, USB/LUN 할당, vHBA 생성·할당, 일반 해제, 잔여 설정 점검 차단, 상세, 부분 실패 결과, 미할당 vHBA 삭제, 빈 목록, PCI 해제 차단, 라이트 대화상자, 스크롤 하단 상태. 이는 동작 목업이나 제품 기능 검증 결과가 아니다.

## 이미지 렌더링 확인

- 실제 브라우저 1280×720에서 전 화면 가로 넘침 없음.
- 대화상자 너비 720px, 중앙 x=640 / y=360. 긴 본문은 최대 높이 672px, 상하 여백 24px.
- LUN 대화상자 본문 scrollTop 0→74px 이동 시 제목 y=25 / 하단 영역 y=625 유지.
- 안내 글자 다크 `#bdc7d3`, 라이트 `#4e6073`. 밝은 바탕의 흰 페이지 버튼 및 검정 안내문 없음. 스크롤바에도 color-scheme 적용.

## 구현 후 수용 기준

- [ ] 6종 목록/빈 목록/오류/권한별 화면과 유형 필터·검색·페이징 검증
- [ ] 각 타입의 정상 할당/해제 및 VM 재시작 후 기록·설정·실제 연결 정합성 검증
- [ ] 동시 할당·다른 VM 점유·오래 열린 대화상자·전이 상태·스냅샷·호스트 장애 차단
- [ ] 해제 대상 식별자 및 UUID/내부 ID 혼동 방지
- [ ] 조회 실패 시 자동 변경 없음; 부분 실패 후 성공 작업 중복 실행 없음
- [ ] vHBA 생성 성공/할당 실패/미할당 삭제와 다른 VM 사용 장치 삭제 방지
- [ ] 잔여 설정 정리의 서버 측 검증 계약 확정 및 검증 완료 전 버튼 비활성화
- [ ] 다크/라이트/긴 이름/작은 화면에서 UI 검증; 제목·버튼 고정과 본문 스크롤 확인

## 소스 근거

https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/ui/src/views/compute/InstanceTab.vue
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/ui/src/views/infra/ListHostDevicesTab.vue
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/ui/src/views/storage/HostDevicesTransfer.vue
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/ui/src/views/storage/HostUsbDevicesTransfer.vue
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/api/src/main/java/org/apache/cloudstack/api/command/user/vm/ListVmDeviceAssignmentsCmd.java
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/api/src/main/java/org/apache/cloudstack/api/command/admin/outofbandmanagement/
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/server/src/main/java/com/cloud/server/ManagementServerImpl.java#L2993
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/server/src/main/java/com/cloud/server/ManagementServerImpl.java#L3461
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/server/src/main/java/com/cloud/server/ManagementServerImpl.java#L4549
https://github.com/ablecloud-team/ablestack-cloud/blob/e5fb9b5eb7582eece4efe9f9b615c56d748c83a0/core/src/main/java/com/cloud/resource/ServerResourceBase.java#L3664
