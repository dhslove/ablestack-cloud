<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License. You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Europa VM NIC 탭 개선 설계

기준 소스: `1c8081dbf4c10374955cd11d293c59fa6a46623a` (`ablestack-europa`).
실제 VM 상세의 NIC 및 볼륨 탭을 읽기 전용으로 확인하고 기존 UI/API/서비스 소스를 대조했다.
이 디렉터리는 설계 산출물만 포함한다. 운영 UI 수정, API 추가, VM/호스트 변경, 배포는 수행하지 않았다.

## 검토 방법

`mockup.html`을 다운로드해 브라우저로 열면 된다. 외부 라이브러리나 서버가 필요하지 않다.
상단 검토 도구에서 다크/라이트, VM 상태, 각 대화상자, 읽기 전용/조회 실패 상태를 선택한다.
행의 `연결 해제`와 드롭다운, 상단 `생성 및 연결`과 `기존 네트워크 연결`을 사용할 수 있다.
샘플 작업은 목업 메모리 안에서만 동작하며 실제 API를 호출하지 않는다. 새로고침하면 예시 데이터로 초기화된다.
스크린샷의 VM 이름, IP, MAC, UUID는 모두 예시다. 계정 비밀번호나 실제 인프라 식별자는 포함하지 않았다.

목업은 사용 흐름·배치·메시지 검토용이며 모든 필드의 검증/종속 선택/API 호출을 구현한 기능이 아니다.
신규 네트워크의 IPv6, VPC ACL, PVLAN, MTU 등 조건부 고급 필드는 구현 시 기존 생성 폼을 보존·재사용한다.

## 현재 문제

- `NicsTab.vue` 상단은 전체 폭의 `VM에 네트워크 추가` 하나다. 신규 생성→연결은 없다.
- `NicsTable.vue`의 `expandedRowRender` 안에 작업 슬롯이 있어 사용자가 행을 펼쳐야 기능을 발견한다.
- 기본 NIC 설정, 제거, IP/MAC, 보조 IP, 활성 상태, 링크 변경은 이미 존재한다. 신규 API가 필요한 기능으로 오해하면 안 된다.
- 기본 NIC의 제거/기본 설정 버튼은 현재 숨겨진다. 기본 NIC 하나뿐인 VM에서는 네트워크 관리가 더 빈약해 보인다.
- NIC의 `enabled`와 `linkstate`는 다른 값이다. 기존 목록 상태는 `enabled`, 상세 링크는 `linkstate`를 사용한다.
- 링크 DOWN에서 IP/넷마스크/게이트웨이를 숨기는 기존 렌더링은 설정 유실로 오해할 수 있다.
- 보조 IP 버튼 조건이 `addIpToNic`을 두 번 확인한다. 추가/해제 각각의 권한을 분리해야 한다.
- `UpdateVmNicLinkState` 실행에는 명확한 API 권한 조건이 없고 GET 헬퍼를 사용한다. 기존 API 명칭은 유지하고 쓰기 요청을 POST로 정리한다.
- 기존 네트워크 후보는 Zone 중심이며 VM 소유자/프로젝트와 페이지네이션을 보강해야 한다. 관리자에게 보인다는 이유로 VM에 연결 가능하다고 가정하지 않는다.

## 볼륨 탭과 같은 구성

`VmVolumesTab.vue`를 기준으로 상단 `생성 및 연결`(primary) → `기존 네트워크 연결` → `새로고침` → 우측 검색 순서로 배치한다.
목록에는 네트워크 이름/유형/기본 배지, 장치 ID, IP/MAC, NIC 활성/링크 상태, 작업을 표시한다.
행 오른쪽에 텍스트 `연결 해제`를 먼저, 드롭다운을 그 다음에 배치한다.
기본 NIC의 연결 해제는 disabled + 사유로 표시하며 상세/보조 IP 등의 조회는 유지한다.
작업을 펼침 행 안에 숨기지 않는다. 네트워크 상세 링크와 NIC 상세는 서로 구분한다.
IP가 없는 L2는 `Cloud IP 관리 안 함`, API 값 미수신은 `확인 불가`로 표시하며 false로 추정하지 않는다.

## 기존 API 연결 계약

| UI 기능 | 기존 API / 주요 인자 | 완료 확인 |
|---|---|---|
| 신규 네트워크 생성 | `createNetwork(name, networkofferingid, zoneid, account/domainid 또는 projectid, 유형별 기존 인자)` | 동기 `createnetworkresponse.network.id`를 보존 |
| 기존/신규 네트워크 연결 | `addNicToVirtualMachine(virtualmachineid, networkid, ipaddress?, macaddress?)` | job 완료 후 `listNics`에서 networkid와 신규 NIC id 확인 |
| 연결 해제 | `removeNicFromVirtualMachine(virtualmachineid, nicid)` | job 완료 후 해당 NIC가 목록에서 제거되었는지 확인 |
| 기본 NIC 설정 | `updateDefaultNicForVirtualMachine(virtualmachineid, nicid)` | job 완료 후 대상 `isdefault=true`와 이전 기본 NIC 상태 확인 |
| NIC 활성/비활성 | `updateVmNic(nicid, enabled)` | KVM 전용, `enabled` 재조회 |
| 링크 UP/DOWN | `UpdateVmNicLinkState(virtualmachineid, nicid, linkstate)` | API 대소문자 그대로 사용, `linkstate` 재조회 |
| IPv4/MAC 변경 | `updateVmNicIp(nicid, ipaddress?, macaddress?)` | 변경된 IP/MAC와 재조회 결과 일치 |
| 보조 IP 추가/해제 | `addIpToNic(nicid, ipaddress?, description?)`, `removeIpFromNic(id)` | job 완료 후 secondaryip 재조회 |
| 목록/가용 주소/생성 폼 | `listNics`, `listNetworks`, `listVirtualMachines`, `listVMSnapshots`, `listNetworkOfferings`, `listZones`, `listVPCs`, `listPhysicalNetworks`, `listNetworkACLLists`, `listAvailableGuestIps`, `listPublicIpAddresses` 등 현재 생성 폼에서 사용하는 조회 API | 역할과 유형에 따라 필요한 조회만 수행 |
| 비동기 작업 | `queryAsyncJobResult` / 기존 `$pollJob` | jobid 보유 여부와 결과 상태에 따른 처리 |

`createNetwork`는 이 기준 소스에서 `BaseCmd`이다. 무조건 jobid가 있다고 가정하지 않는다.
`Addr`는 IP 주소로 해석한다. PCI 주소나 임의 게스트 인터페이스 주소 변경 API는 이 설계에 추가하지 않는다.
L2 게스트 OS의 고정 IP 설정은 별도 `IP 구성` 탭의 기존 기능이며 이 NIC 관리 화면과 혼합하지 않는다.

## 제약과 권한

- 각 액션의 API 권한을 별도로 적용한다. 읽기 전용 역할은 조회만 제공한다.
- VM의 Zone/소유 계정·도메인/프로젝트를 기준으로 생성·연결 대상을 고정한다. 공유 네트워크는 소유자와 다를 수 있으므로 기존 접근 권한도 고려한다. 이미 연결된 networkid는 후보에서 제외한다.
- 서버가 최종 권한/가능 여부 판단자다. UI가 확인하지 못한 권한·오퍼링·스냅샷 상태를 임의로 허용하지 않는다. Basic Zone은 NIC 추가/해제/기본 변경 제한을 반영한다(추가의 0 NIC 예외는 기존 서버 계약 유지).
- VM 스냅샷이 있으면 추가/제거/기본 NIC 변경을 제한한다. VM 상태 전이 중 변경을 막고 실행 직전에 최신 VM/NIC/스냅샷을 재확인한다.
- 기본 NIC는 제거할 수 없다. 먼저 다른 NIC를 기본으로 설정해야 하며 숨은 자동 기본 전환이나 자동 중지는 하지 않는다.
- PF/LB/Static NAT 연결이 있는 NIC 제거는 서버에서 거부될 수 있다. 확인 가능한 경우 사전 설명하고 서버 오류를 사용자에게 보존하여 표시한다. UI 전용 변경으로 이 제한을 우회하지 않는다.
- `updateVmNic`는 KVM만 지원한다. `enabled`와 `linkstate` 액션은 명확히 분리하고 하나를 바꾸면서 다른 하나를 암묵적으로 바꾸지 않는다.
- IP/MAC 변경: 오퍼링 서비스가 있으면 VM이 Stopped여야 한다. 모든 MAC 변경을 Running에서 된다고 표시하지 않는다. L2에서 IP 입력을 비활성화한다.
- **MAC만 변경할 때 기존 IPv4를 보존하여 보낸다.** 이 서버 구현은 IP 생략을 IP 변경 요청으로 판단할 수 있다. IP 자동 재할당 의도가 없는 요청에서 IP가 바뀌지 않게 한다. L2처럼 기존 IP가 없는 경우의 MAC-only 동작은 별도 회귀 검증한다.
- External hypervisor 등 현재 지원하지 않는 기능은 기존 제한을 유지한다. 신규 게스트 기능이나 호스트 직접 호출은 추가하지 않는다.
- IPv6 값은 기존 응답 범위에서 읽기 제공한다. IPv6 변경을 지원하지 않는 기존 NIC API에 임의 파라미터를 보내지 않는다.
- 활성/링크 변경, 기본 설정, 해제 대화상자에 대상 VM/NIC와 통신 영향을 표시한다. 특히 기본 NIC의 링크 DOWN 경고를 명확히 한다.

## 단계별 작업과 실패 복구

1. 신규 생성: `createNetwork` → 반환 ID 보존 → VM 연결 → 필요 시 기본 NIC 설정.
2. 생성 후 연결 실패: 네트워크를 유지하고 실패 단계만 재시도한다. `deleteNetwork` 자동 실행, 생성 단계 중복 실행 금지.
3. 연결 후 기본 설정 실패: 연결된 NIC를 유지하고 기본 설정 단계만 재시도한다.
4. jobid 수신 후 폴링 실패: 기존 jobid를 다시 조회한다. 동일 쓰기 요청을 다시 보내지 않는다.
5. 쓰기 응답 유실로 jobid/생성 ID가 없으면 `결과 확인 필요` 상태로 둔다. 알려진 네트워크/NIC ID와 최신 조회로 일치 여부를 확인하되 이름만으로 리소스를 확정하지 않는다. 생성 ID를 복구할 수 없는 경우 자동 생성 재시도 금지, 운영자가 기존 네트워크를 확인·선택하게 한다.
6. 성공 표시는 job 성공만으로 끝내지 않고 `listNics`와 VM 요약을 다시 조회한 결과를 기준으로 한다. 단, API 결과가 게스트 OS의 실제 통신까지 보장하는 것은 아니다.
7. 새로고침/탭 이동으로 중복 요청이 생기지 않게 VM/세션/프로젝트 범위의 작업 상태를 관리한다. 대화상자를 닫아도 진행 배너로 다시 연다. 페이지 새로고침 후에는 서버 상태를 먼저 확인한다.
8. 백그라운드 갱신은 기존 행을 유지하며 열린 폼의 입력을 덮어쓰지 않는다. VM/프로젝트 이동 후 이전 비동기 응답은 폐기한다.

## 전체 화면 및 대화상자

- NIC 탭 / 행 작업 메뉴 / 읽기 전용 / 목록 조회 실패.
- 생성 및 연결: Isolated, Shared, L2 변형(기존 생성 폼의 모든 조건부 필드 유지).
- 기존 연결: 후보·IP·MAC·기본 설정, 후보 없음.
- 해제 확인: 대상 상세, 네트워크 자체를 보존한다는 설명.
- 기본 NIC 확인, NIC 활성/비활성, 링크 UP/DOWN 각각의 확인.
- IP/MAC 변경: Running 제한, Stopped 편집, L2 IP 제한.
- 보조 IP 추가·목록 및 별도 해제 확인.
- NIC 상세.
- 진행 중 / 완료 / 부분 성공 / 결과 확인 필요.

## 다크모드·접근성

구현에서는 기존 `--ui-bg-page`, `--ui-bg-surface`, `--ui-text-primary`, `--ui-text-secondary`, `--ui-border` 및 상태 토큰을 사용한다.
목록 헤더·선택 옵션·드롭다운·모달·경고·disabled·포커스까지 라이트/다크를 확인한다.
링크·활성·오류를 색상만으로 구분하지 않는다. 체크박스/레이블, 키보드 메뉴·포커스 복원, Esc 닫기와 모달 포커스 제한을 제공한다.
좁은 화면에서는 툴바를 줄바꿈하고 목록은 가로 스크롤한다. dialog body만 스크롤하고 footer 작업은 유지한다.

## 구현 범위와 검증

변경 예상: `NicsTab.vue`, `NicsTable.vue` 또는 VM 전용 대체 컴포넌트, 기존 CreateNetwork 계열 폼의 반환 ID/submit-handler 지원, UI 작업 상태 helper, `ko_KR.json`/`en.json`, 단위 테스트.
공유 `NicsTable` 변경 시 Desktop/VNF 등 다른 사용처의 회귀를 피한다. Java/API/DB/호스트 코드는 변경하지 않는다.

- [ ] Isolated/Shared/L2 생성→연결 및 기존 네트워크 연결, optional 기본 설정.
- [ ] 네트워크 해제 후 네트워크 객체와 타 VM 연결이 유지됨.
- [ ] 기본 NIC·스냅샷·Basic Zone·상태 전이·PF/LB/Static NAT 오류.
- [ ] KVM enabled와 linkstate 각각 검증, 미지원 hypervisor는 제한.
- [ ] MAC-only에서 IP 보존, 중복/잘못된 MAC/IP, L2와 서비스 오퍼링의 정지 요구.
- [ ] 보조 IP 추가/해제와 권한 독립성.
- [ ] 생성 성공→연결 실패, 연결 성공→기본 설정 실패, job 폴링 실패/응답 유실, 중복 클릭.
- [ ] 네트워크 후보 전체 페이지, 타 계정·프로젝트 누출 방지, 프로젝트 전환 중 응답 폐기.
- [ ] 탭·대화상자 전체 다크/라이트, 작은 화면, 키보드 조작.
- [ ] 변경 UI lint/관련 단위 테스트/UI 빌드. 서버 배포 없이 대상 환경 실검증은 별도 승인된 구현 단계에서 수행.

이번 산출물의 검증: HTML JavaScript 구문 검사 및 브라우저 화면/상호작용 확인. 제품 기능이나 서버 API 실행 성공을 주장하지 않는다.
