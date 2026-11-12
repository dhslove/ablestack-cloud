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

# 병합본 실제 회귀 결과와 확인 범위

## API·권한·자원

양쪽 9역할의 네트워크 API 가용 계약을 직전 기준과 대조했다. 역할 이름을 근거로 기존 허용 권한을 임의 제거하지 않았다. 6역할×Isolated/L2/Shared×코멘트/태그/이벤트의 54개 조회, 관리자 코멘트 가시성, 도메인/프로젝트 범위를 확인했다. 구성원·읽기 전용의 프로젝트 공유 Shared IP 조회는 프로젝트의 NIC 할당 주소만 반환했다. 읽기 전용의 이미 연결된 NIC에 대한 추가 요청은 401/432와 job 미생성으로 거부됐다.

이는 사이트별 API 계약 9건+메타데이터 54건+Shared IP/NIC 3건의 **66건**, 합계 132건이다. 전체 작업 중 호출한 모든 API 횟수나 모든 변경 권한의 전수 재검증 수를 뜻하지 않는다.

| 근거 | 31 | 32 |
| --- | --- | --- |
| 역할 계약·54개 조회 | [결과](evidence/31-role-api-regression.json) | [결과](evidence/32-role-api-regression.json) |
| 프로젝트 Shared IP/거부 | [결과](evidence/31-shared-project-ip-role-regression.json) | [결과](evidence/32-shared-project-ip-role-regression.json) |
| 자원·NIC·실제 domain | [결과](evidence/31-resource-runtime-verification.json) | [결과](evidence/32-resource-runtime-verification.json) |

관리자 UI로 네트워크 설명을 수정하고 jobstatus1을 확인한 뒤 API로 원래 설명을 복원했다. VM NIC 탭에서 전용 L2 시험망을 기본값 변경 없이 연결했다가 그 NIC만 해제했다. 사이트별 update/add/remove job 모두 성공했으며, API·libvirt domain XML·QGA 실제 MAC을 대조했다. 추가 NIC는 게스트에 나타났고 해제 후 사라졌으며 기존 기본 NIC ID/IP/MAC은 유지됐다. 진행 중 버튼과 완료 대화상자도 확인했다.

[UI job 기록](evidence/ui-jobs.json), [31 NIC 추가](evidence/31-ui-hotplug-attached.json)·[해제](evidence/31-ui-hotplug-detached.json), [32 추가](evidence/32-ui-hotplug-attached.json)·[해제](evidence/32-ui-hotplug-detached.json)를 남겼다. 확인용 네트워크·VM을 삭제하지 않았다.

## 실제 DNS·DHCP·NIC·VPN·TLS

스위치에 시험 VLAN 경로가 구성되지 않은 조건을 따라 라우터와 VM을 같은 호스트에 두고 확인했다. LB 백엔드 분산 처리량 측정은 제외했다. 실제 단일 백엔드 TLS 응답 검사는 수행했다.

| 항목 | 31 / 32 결과 |
| --- | --- |
| Shared DNS UDP | 각 12/12 성공 |
| Shared DNS TCP | 각 12/12 성공 |
| 기존 Shared NIC ICMP/SSH | 각 ICMP 5회 및 SSH banner 성공 |
| DHCP 새 DORA 교환 | 실제 NIC/MAC으로 DISCOVER→OFFER→REQUEST→ACK 성공 |
| DHCP 갱신 | ciaddr가 있는 unicast REQUEST→ACK 성공 |
| 게스트 영향 | 기존 interface 주소 불변; 시험 control alias 제거 |
| VPN | 단일 외부 WSL 클라이언트로 순차 접속, IKE/PPP 및 ICMP 5회·SSH 3회 성공 |
| TLS | SNI·Root CA 검증 0, 체인 3개, 실제 HTTPS200·시험 본문 일치 |
| HAProxy | 실제 구문 검사 0, 고급 점검 0 |

DHCP DORA는 **기존 예약 NIC/IP로 새 DHCP 트랜잭션**을 보낸 결과다. 새 VM을 배포해 미예약 주소의 첫 임대를 검사했다는 의미는 아니다. QGA guest-exec 차단 정책은 유지하고 SSH sudo로 패킷을 검사했다. 검증 뒤 DNS/기존 NIC/TLS 검사를 다시 수행해 통신을 확인했다.

- [31 DHCP DORA·갱신](evidence/31-dhcp-dora-renew-actual.json) / [32 DHCP](evidence/32-dhcp-dora-renew-actual.json)
- [31 최종 DNS·기존 NIC](evidence/31-new-default-shared-dhcp-dns-traffic.json) / [32 최종 통신](evidence/32-new-default-shared-dhcp-dns-traffic.json)
- [31 VPN](evidence/31-ra-merged-8ad-connect.json) / [32 VPN](evidence/32-ra-merged-8ad-connect.json)
- [31 TLS·구문·점검](evidence/31-offering-merged-8ad-live-tls.json) / [32 TLS](evidence/32-offering-merged-8ad-live-tls.json)

VPN 시험 클라이언트는 마지막에 종료했다. 독립 외부 클라이언트 2대의 동시 통신·OVS/PVLAN 실제 격리·미준비 Health Monitor 제공자 조건은 이번 결과에 포함하지 않았다. 기존 독립 조건 이슈 #1636의 범위를 성공으로 바꾸지 않는다.

## UI 표준·프로젝트·반응형·즉시 테마

VM 목록·상세·NIC 탭·기존 네트워크 연결 대화상자를 직접 열어 네트워크 화면과 비교했다. Isolated/Shared/L2 상세, Shared IP/주소 범위, VNF 연관·요약·NIC 링크를 확인했다. 양쪽 UI에서 설명 변경·NIC 추가/해제는 실제로 제출했다. 읽기 전용 계정으로 프로젝트 Shared IP 및 VM NIC를 조회하고 연결·해제 UI 가드를 확인했다. 모든 NIC 보조 작업을 금지했다고 판정한 것은 아니다.

- 탭의 주요 작업 버튼과 업데이트를 설명/표 앞에 배치하고 업데이트는 아이콘+텍스트로 표시.
- 네트워크 업데이트의 오퍼링 조회는 선택 드롭다운 오른쪽 같은 행의 아이콘 버튼. 현재 선택과 입력 보존 확인.
- 송신 규칙 추가는 세로 입력, NIC 대화상자는 대상/네트워크/IP·MAC/기본 NIC 설정 구분. L2 IP 비관리 안내와 IP 입력 비활성.
- 390 모바일의 대화상자/footer·768 태블릿·1280 데스크톱에서 문서 가로 넘침 없음. 넓은 표는 내부 스크롤.
- 768→1600에서 breadcrumb 폭 644→1300으로 새로고침 없이 갱신. 31은 진입·새로고침 후 content scrollTop0→684에도 sticky y78 유지. 32는 기존 경보 높이를 포함한 y151과 content scroll을 확인했으며 새로고침 뒤에도 고정 영역을 유지.
- 31은 Shared 프로젝트→VNF 프로젝트→Shared 왕복, 32는 기본 보기/Shared/VNF 전환과 복귀를 확인. 선택 완료 뒤 dashboard와 해당 프로젝트 화면을 대조.
- 라이트↔다크는 페이지 이동 없이 반영. 다크에서 대화상자/입력/상태/링크 확인 후 양쪽 라이트와 관리자 확인 화면으로 복원.

[DOM·동작 기록](evidence/ui-checks.json)과 [원본 캡처](capture-matrix.ko.md)를 참조한다. 브라우저 제어의 locator 대기·로그인 도메인 형식·중간 애니메이션은 도구/시험 절차에서 정정했고, 그 시도를 제품 결함이나 성공 증거로 계산하지 않았다.

## 별도 관리할 표시 개선·환경 조건

이번 배포 범위의 기능 회귀는 발견되지 않았다. 다음은 배포 차단·중요 결함으로 분류하지 않았다.

1. 양쪽 VM 목록에서 미지정 backupoffering의 빈 `#/backupoffering/undefined` 링크 및 템플릿 형식 USER의 `#/vm/USER` 링크. ListView.vue의 해당 표현은 각각 2026-06/07 변경에서 유래한 기존 공통 목록 표시다. 이번 8ad2d1e 배포를 위해 소스를 수정하지 않았다.
2. 모바일 송신 규칙 표의 여러 헤더가 좁게 줄바꿈된다. 입력 대화상자는 읽을 수 있었고 규칙 추가는 제출하지 않고 취소했다. 별도 P3 표 가독성 개선 후보로 남긴다.
3. 32의 11월 5일 호스트 센서/1일 전 CPU 경보, Wall 모니터링의 간헐적 조회 불가 안내는 기존 환경 상태다. 모니터링을 끄거나 사일런스하지 않았고 제품 오류를 새로 등록하지 않았다.

후속은 #1636의 환경 준비 후 조건별 검증, 별도 #1586 자원 정리, 독립 P3 #1634·#1635 순으로 각 범위에서 진행할 수 있다. P3와 위 표시 후보를 네트워크 Epic의 종료 차단으로 다시 묶지 않는다.
