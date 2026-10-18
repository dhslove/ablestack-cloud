# Mold Affinity 그룹 기능 검증 및 개선 계획

검증일: 2026-10-19 KST. 대상: 31번 클러스터, 컴퓨트 > Affinity 그룹.
이 문서는 현재 기능 검증 결과와 후속 구현 계획이다. 결함 수정과 배포는 아직 수행하지 않았다.

## 검증 기준과 범위

- 소스 기준: Europa `d9f068bbbba048313b6d38539651d19444c4d7c7`. 별도 `dhslove/affinity-validation-20261019` 작업 트리에서 조사했다.
- 실제 Mold 브라우저 UI, 인증된 Cloud API, 관리 서버의 로컬 DB를 교차 확인했다. SSH 작업은 WSL ext4 작업 디렉터리에서 수행했다.
- UI 기준: **가상머신 메뉴의 목록, 상세, 탭, 탭 안의 콘텐츠 배치, 대화상자 스타일**. 공통 기반에서 이미 일치하는 부분은 유지하며 기능과 가독성의 부족한 부분을 정돈한다.
- admin 및 새로 만든 일반 사용자로 UI에 로그인했다. 프로젝트 그룹의 생성·조회·상세 표시를 확인했다.
- 변경 시험에는 전용 그룹, 일반 사용자 계정 1개, Stopped VM 1개를 사용했다. 선택 손실의 두 번째 재현에서는 기존 Stopped 시험 VM의 대화상자만 열어 선택 후 **취소**했다. 기존 VM에 저장 요청을 보내지 않았다.
- 소스 기준 커밋과 배포 UI의 빌드 커밋을 동일하다고 단정하지 않는다. 배포 UI는 실제 동작 및 아래 index 해시로 별도 식별했다.
- 재현 캡처는 약 901×870 브라우저 뷰포트다. 전체 모바일·접근성 검증 결과는 아니다.

## 1. API ↔ UI 계약 검증

| API | 실제 API 기능 | UI 확인 결과 |
| --- | --- | --- |
| `listAffinityGroupTypes` | 현재 생성 가능 유형 4종 반환 | 생성창의 4종과 현재 일치. 생성창은 API를 조회하지 않고 고정 배열 사용 |
| `createAffinityGroup` | 이름·설명·유형·계정/도메인·프로젝트, 비동기 생성 | admin UI 생성·한글 설명 저장 정상. 일반 사용자 폼에는 소유자 선택 없음. 중복 오류 후 폼이 닫힘 |
| `listAffinityGroups` | id/name/type/keyword/virtualmachineid/account/domainid/projectid, 재귀 조회·페이지 | 이름/유형/키워드·상세·프로젝트 조회 정상. VM 선택창은 미지원 `accountid` 사용. 고급 검색에 미지원 `zoneid` 노출 |
| `updateVMAffinityGroup` | Stopped VM의 그룹 목록 교체, 빈 목록으로 전체 해제, 비동기 작업 | 실제 UI 연결·전체 해제 정상. 비동기 실패를 성공으로 알림. 페이지 간 선택 손실·2페이지 검색 오류 |
| `deleteAffinityGroup` | ID 또는 이름/소유자 범위로 삭제, 비동기 작업 | 일반 사용자 단건 삭제 정상. admin의 필터된 14개 일괄 삭제 14성공/0실패 확인 |

현재 4유형은 `host affinity`, `host anti-affinity`, `non-strict host affinity`, `non-strict host anti-affinity`다. 나머지 3유형도 실제 API 생성의 최종 jobstatus=1을 확인했다. 현재 유형 누락으로 판정하지 않는다.

ID/이름 지정은 같은 기능의 대체 입력 방식이므로 모두 별도 UI를 요구하지 않는다. 그룹 이름·설명 편집 API는 현재 5개 API에 없다. 편집 기능은 UI 누락 결함으로 집계하지 않고, 필요 시 별도 백엔드 계약을 포함하는 확장 후보로 둔다.

관련 VM 조회 API와 `virtualmachineIds` 응답은 있으나 Affinity 상세에는 멤버 수·멤버 탭·그룹 안에서 연결을 관리하는 흐름이 없다. 현재는 “가상머신 보기”로 이동한 뒤 개별 VM의 작업을 사용해야 한다.

## 2. 실환경에서 재현한 결함 7건

| ID | 우선순위 | 실제 결과와 영향 | 개선 방향 |
| --- | --- | --- | --- |
| AG-D01 | P1 | admin VM의 Affinity 선택창에 타 계정 그룹 노출. 동일 도메인에서 `accountid` 조회는 17개, 올바른 `account` 조회는 해당 사용자 그룹 1개. API 스키마에 accountid 없음 | VM의 계정·도메인·프로젝트 범위로 조회하고 도메인 공유 그룹의 사용 가능성도 반영. 소유자 표시 및 선택 불가 사유 제공 |
| AG-D02 | P1 | 타 계정 그룹을 전용 VM에 저장하면 UI는 “Affinity 그룹을 변경했습니다.” 표시. 실제 jobstatus=2, errorcode=530, VM 연결은 빈 목록 | 비동기 job 완료 후에만 성공 처리. 실패·timeout·결과 불명 구분, 입력 유지, 확정 실패 사유 표시 |
| AG-D03 | P1 | 1페이지 그룹 선택 → 2페이지 추가 선택 → 1페이지 복귀 시 첫 선택 해제. 별도 재현의 전후 체크박스 1→0 저장 | 페이지·검색·정렬 간 ID 선택 보존, 선택 목록/개수 표시, 사용자가 명시적으로 해제할 때만 제거 |
| AG-D04 | P2 | 2페이지에서 존재하는 단일 그룹 이름을 검색하면 “데이터 없음”. 검색 시 내부 page가 2로 유지됨 | 검색·페이지 크기 변경 시 1페이지 초기화, 화면과 API pagination 상태 동기화 |
| AG-D05 | P2 | 중복 생성 시 API 431 알림 뒤 대화상자 종료, 이름·설명·유형 입력 손실 | 실패 시 폼과 초점 유지, 이름 수정 후 재시도. 성공 확정 또는 취소 시에만 닫기 |
| AG-D06 | P2 | 고급 검색에 Zone 제공하지만 listAffinityGroups 스키마는 zoneid 미지원. 무효 Zone UUID를 넣어도 같은 17개 반환 | 지원 API 필터만 노출. Zone 기반 사용 현황이 필요하면 멤버 VM 조회의 별도 조건으로 설계 |
| AG-D07 | P2 | 그룹의 “가상머신 보기” 이동 후 검색 칩에 `label.affinitygroupid`가 그대로 표시 | 한글 필터 제목·그룹 이름 표시, UUID는 복사/세부 정보로 제공 |

AG-D01/D02는 admin에게 허용되지 않는 연결을 제안하고 결과를 잘못 알리는 UI 계약 결함이다. 실제 백엔드는 소유권을 검사해 연결을 거절했고, 일반 사용자 타 계정 삭제도 531로 차단했다.

### 재현 증거

- [API 계약·실제 응답·실패 job·정리 결과](evidence/qualification.json)
- [타 계정이 표시되는 VM 선택창](evidence/05-vm-picker-foreign-account.png)
- [중복 오류와 종료된 생성창](evidence/02-duplicate-closes-form.png)
- [선택 전 캡처](evidence/16-selection-before.png) / [첫 페이지 복귀 후 캡처](evidence/17-selection-after.png)
- [선택 전 전체 AX 상태](evidence/16-selection-before.ax.txt) / [선택 손실 후 전체 AX 상태](evidence/17-selection-after.ax.txt)
- [2페이지 검색의 빈 결과](evidence/08-search-from-page2-empty.png)
- [지원하지 않는 Zone 필터](evidence/03-advanced-search-zone.png)
- [번역되지 않은 연결 VM 필터](evidence/11-members-filter-untranslated.png)

실패 job UUID: `764d1387-4966-4863-8ee7-e9ca9e3e96f5`.
전용 VM UUID: `f46fad13-7d36-4b21-900c-4bf715c03d6e`.
D02 성공 알림은 제출 직후 AX 출력으로 관찰했으며 알림 만료 전 스크린샷은 저장하지 못했다. 백엔드 job 응답·빈 VM 연결은 JSON에 보존했다. 스크린샷만으로 실패/성공을 판정하지 않았다.

## 3. 가상머신 UI 표준과의 비교 및 목표

Affinity도 공통 목록·상세 레이아웃, UUID 복사, 소유자 링크, 상세/이벤트 탭과 우클릭 일괄 작업을 이미 사용한다. 다음 항목을 가상머신 메뉴의 동일한 스타일과 동작 기준으로 보완한다.

| 영역 | 현행 확인 | 정돈 목표 |
| --- | --- | --- |
| 목록 | 이름·영문 유형·설명·계정/프로젝트·도메인. 멤버 수 없음 | VM 목록과 같은 도구 모음/버튼 크기·검색·정렬·열 선택·페이지 배치. 이름/유형/설명/연결 VM 수/소유자 표시. 긴 값·빈 값·권한별 열 처리 |
| 상세 | 공통 요약 카드 및 상세 탭 사용. 프로젝트는 요약 카드에 표시되지만 상세 필드 목록에 없음 | VM 상세의 제목/작업 메뉴/요약 정보 스타일. UUID·유형·엄격/선호 정책·소유자·프로젝트·도메인·멤버 수를 일관되게 배치 |
| 탭 | 상세 → 이벤트 | **상세 → 가상머신 → 이벤트**. VM 화면의 탭 위치·간격·라우팅·활성 탭 유지 방식 재사용. VM 전용 탭을 불필요하게 복사하지 않음 |
| 탭 콘텐츠 | 그룹 안의 멤버 관리 표가 없음. 외부 VM 목록으로 이동 | VM 탭의 제목/설명 → 작업 도구 모음 → 표 → 페이지 영역 순서. 상태·호스트·계정/프로젝트를 표시하고 적법한 VM만 연결/해제. 부분 갱신·오류·재시도에서도 성공 데이터 유지 |
| 생성/변경 대화상자 | 단순 폼/체크박스 표. 유형 영어, 선택 요약 없음. 실패 시 폼 종료 | VM 대화상자의 제목·대상 요약·라벨·여백·폭/최대 높이·스크롤·하단 취소/확인 스타일. 선택 요약과 상태 제한, 비동기 진행/실패 표시 |
| 삭제 대화상자 | 단건은 일반 확인 문구. 일괄은 대상 표·성공/실패 요약 제공 | 대상 이름·소유자·연결 VM 수와 연결 해제 영향을 명시. 일괄 결과/재시도와 알림을 정돈하고 결과 표를 가리지 않도록 함 |
| 문구·테마 | 생성은 raw 영문 유형, 검색은 Strict/Non-Strict 표현 | 같은 호스트 배치/다른 호스트 분산, 엄격/선호의 의미를 한글로 일관되게 설명. 라이트/다크·긴 이름·좁은 화면·키보드 검증 |

표준 비교 자료: [VM 상세](evidence/04-vm-detail-standard.png), [Affinity 상세](evidence/10-affinity-detail-standard-gap.png), [일반 사용자 목록](evidence/12-user-scoped-list.png), [삭제 대화상자](evidence/13-delete-dialog-context-gap.png).

## 4. Epic 내 작업 계획과 의존 순서

하위 이슈는 이번 요청에서 생성하지 않고, 아래 작업 단위를 단일 Epic의 범위로 관리한다.

1. **AG-01 / P1 — 조회 소유권 및 비동기 완료 계약**: D01/D02 수정. account/domain/project 및 공유 그룹 범위, queryAsyncJobResult, 실패·취소·중복 제출·timeout 정책. 다른 기능에서도 쓰는 공통 동작 변경은 회귀 범위를 명시한다.
2. **AG-02 / P1 — 선택·검색·페이지 안정성**: D03/D04 수정. 보이지 않는 페이지의 기존 연결 보존, 1페이지 초기화, 요청 경합 방지, 조회 실패와 빈 결과 구별.
3. **AG-03 / P2 — 생성 폼과 API 기능 정합성**: AG-01 뒤 D05/D06 수정. listAffinityGroupTypes 기반 선택, 입력 길이·공백·중복 안내, 실패 입력 유지, 미지원 검색 조건 제거.
4. **AG-04 / P2 — 멤버 사용 현황과 연결 관리**: AG-01/02 뒤 상세의 연결 VM 수·가상머신 탭 및 연결/해제 흐름 제공. Stopped VM만 변경하고 기존 Running VM은 사유와 이동 경로 표시. 프로젝트·도메인 공유 범위와 페이지 전체 집계를 정확히 처리한다.
5. **AG-05 / P2 — 가상머신 기준 UI 정돈**: 위 비교표 전체와 D07 반영. 공통 컴포넌트/스타일을 재사용하고 탭 콘텐츠/대화상자의 여백·버튼·표·로딩·오류·빈 상태를 통일한다.
6. **AG-06 / P2 — 31번 최종 통합 검증**: AG-01~05 뒤 API ↔ UI ↔ DB 정합성, 실제 UI 전체 경로, 권한/프로젝트/검색·다중 페이지/부분 실패·재시도/라이트·다크·모바일·키보드 회귀. 전용 VM 2개 이상으로 엄격/선호 affinity·anti-affinity의 실제 호스트 배치도 별도 확인한다.

기능적으로 연결되는 기존 Kubernetes/AutoScale Affinity 소비자는 회귀 대상이다. 해당 메뉴를 이번 Epic의 별도 기능 확장 대상으로 확대하지 않는다.

## 5. 완료 조건

- [ ] AG-D01~D07을 같은 재현 절차로 수정 전 실패·수정 후 성공 확인.
- [ ] API가 제공하는 생성/조회/유형/삭제/VM 연결 교체·전체 해제를 UI 경로와 대응시켜 검증.
- [ ] 비동기 jobstatus=1 전 성공 알림 없음. jobstatus=2는 실패 사유·입력 보존·재시도 가능 상태.
- [ ] 계정·프로젝트·도메인 공유 및 사용자 권한을 반영한 후보 목록. 페이지/검색 변경으로 기존 연결을 조용히 제거하지 않음.
- [ ] 가상머신 표준의 목록·상세·탭·탭 콘텐츠·생성/변경/삭제 대화상자 스타일 비교표 충족.
- [ ] 그룹의 연결 VM 수·목록과 실제 API/DB 매핑 일치. 그룹 삭제가 VM 삭제를 뜻하지 않음을 명시하고 연결 해제 영향을 안내.
- [ ] VM 생성 마법사, Stopped/Running 상태, 다중 VM의 실제 배치, 프로젝트/사용자/도메인 관리자·제한 역할, 실패/빈 결과·장시간 응답을 포함한 회귀 근거 저장.
- [ ] 라이트/다크 및 좁은 화면·키보드에서 조작 가능. 조회 갱신 중 기존 데이터·검색·페이지·선택·활성 탭 유지.
- [ ] 전용 fixture의 계정·그룹·VM·볼륨·NIC·매핑 활성 잔여 0과 기존 자원 보존 확인.
- [ ] 구현 단계에서는 필요한 UI/변경 Maven 모듈만 검증한다. Cloud 모듈 빌드는 WSL ext4에서 수행하며, 전체 Cloud 빌드/Actions는 명시 요청이 있을 때만 실행한다.

## 6. 이번 검증에서 정상 확인한 동작

- 유형 4종의 실제 생성 완료, admin UI 생성과 한글 설명 저장, 상세 조회.
- 키워드·이름·ID·유형·프로젝트 조회 및 그룹→VM/VM→그룹 조회 대응.
- 그룹별 생성 이벤트, 일반 사용자 자신의 그룹만 표시, 일반 사용자의 타 계정 삭제 차단.
- Stopped 전용 VM의 UI 연결·초기 선택 복원·전체 해제와 실제 API 매핑 반영.
- 일반 사용자 UI 단건 삭제, admin 우클릭 일괄 삭제 14건과 최종 API/DB 정리.
- 프로젝트 그룹 상세의 소유 프로젝트 표시는 정상이다. 프로젝트 전체 UI 연결/삭제 경로의 합격으로 확대하지 않는다.

이번에는 실제 VM 시작·호스트 배치/장애·용량 부족·VM 생성 마법사·Running 변경 버튼 제어·다중 Zone·도메인 관리자/제한 역할·전체 모바일/라이트 테마 매트릭스를 수행하지 않았다. AG-06의 후속 완료 조건으로 남긴다. 전체 Cloud 빌드나 CI도 실행하지 않았다.

## 7. 정리와 환경 보존

- 생성·삭제한 전용 그룹은 최초 16개와 선택 손실 재현 보강용 8개다. 모두 제거했다.
- 전용 계정 1개와 Stopped VM 1개, ROOT 볼륨 1개를 제거했다. VM은 시작하지 않았고 ROOT는 Allocated 상태로 pool/path가 없었다.
- API와 DB에서 전용 그룹/활성 계정/활성 VM/활성 볼륨 및 전용·고아 매핑 각각 **0**.
- 기존 그룹 **4개(계정 3 + 프로젝트 1)의 전체 응답 동일**, 기존 VM **119개의 ID·이름·Affinity 목록 비교 동일**.
- DB의 삭제 이력은 남아 있다. 전용 VM 646은 removed가 있는 Expunging, ROOT 722는 removed가 있는 Destroy이며 host/pool/path는 NULL이다. 물리 삭제 이력이 완전히 없어졌다는 의미가 아니다.
- Mold active, WEB-INF 존재, /client/ HTTP 200. UI index SHA256은 전후 `96034ffd66057b3411783b5091a6e808abb5c4ac0505aecdb4605ef3803612e6`로 동일.
- UI/JAR/호스트 패키지 배포·서비스 재시작·DB 직접 변경은 수행하지 않았다. 기존 FTCTL 소스 변경도 보존했다.
- [UI 일괄 삭제 결과](evidence/14-ui-bulk-cleanup.png), [정리 후 기존 목록](evidence/15-final-original-list.png). 기본 보기에는 계정 그룹 3개이며 프로젝트 그룹은 별도 범위다.

## 소스 근거

- [Affinity 메뉴·검색·상세·탭·작업 설정](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/config/section/compute.js#L1625)
- [조회 accountid와 검색 페이지 처리](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/ChangeAffinity.vue#L109)
- [페이지 선택 처리](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/ChangeAffinity.vue#L44)
- [비동기 완료를 기다리지 않는 성공 처리](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/ChangeAffinity.vue#L145)
- [유형 고정 배열](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/CreateAffinityGroup.vue#L86), [실패 후에도 finally에서 종료](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/CreateAffinityGroup.vue#L145)
- [조회 API 정의](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/api/src/main/java/org/apache/cloudstack/api/command/user/affinitygroup/ListAffinityGroupsCmd.java#L30), [비동기 변경 API](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/api/src/main/java/org/apache/cloudstack/api/command/user/affinitygroup/UpdateVMAffinityGroupCmd.java#L48)
- [Stopped 및 소유권 백엔드 검증](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/server/src/main/java/org/apache/cloudstack/affinity/AffinityGroupServiceImpl.java#L433)
- [가상머신 탭 표준](https://github.com/dhslove/ablestack-cloud/blob/d9f068bbbba048313b6d38539651d19444c4d7c7/ui/src/views/compute/InstanceTab.vue#L18)
