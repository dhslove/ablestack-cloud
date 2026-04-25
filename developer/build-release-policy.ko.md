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

# ABLESTACK Build / Release Policy

이 문서는 `ablestack-cloud` 저장소의 공식 빌드 운영 기준이다.  
향후 개발용 빌드, 테스트 배포용 빌드, 릴리즈 빌드는 모두 이 문서를 우선 기준으로 삼는다.

## 핵심 원칙

1. 로컬 빌드를 사용하지 않는다.
   - macOS 로컬 빌드
   - 로컬 Docker/Colima/OrbStack 기반 빌드
   - 수동 RPM 패키징
   모두 운영 기준 빌드 경로에서 제외한다.

2. 빌드는 반드시 GitHub Actions를 사용한다.
   - 로컬 빌드는 참고/디버깅 용도로도 표준 경로로 간주하지 않는다.
   - 실제 배포 판단, 테스트 배포, 릴리즈 배포에 쓰이는 산출물은 모두 GitHub Actions 산출물이어야 한다.

3. 빌드는 `개발용 빌드`와 `릴리즈 빌드`로 나눈다.
   - 개발용 빌드: 테스트 스크립트를 돌리지 않는 빠른 패키징 빌드
   - 릴리즈 빌드: 전체 검증 스크립트를 포함한 정식 릴리즈 절차

4. 개발용 빌드와 릴리즈 빌드 모두 GitHub Releases에 업로드해 다운로드 경로를 확보한다.
   - 단순 artifact만으로 끝내지 않는다.
   - 실제 배포에 사용하는 파일은 항상 Release asset 경로로 추적 가능해야 한다.

5. 개발용 빌드는 항상 최신 상태만 유지한다.
   - 테스트 배포가 끝난 개발용 Release는 삭제한다.
   - 다음 개발용 빌드 시 새 Release를 다시 생성한다.
   - 결과적으로 개발용 Release는 상시 1개 최신본만 유지하는 것을 원칙으로 한다.

## 빌드 구분

### 개발용 빌드

목적:
- 기능 확인
- 테스트 배포
- UI/패키징 수정 검증
- 운영 서버 임시 검증

기준:
- 테스트 스크립트를 돌리지 않는다.
- 빠른 RPM 패키징 중심으로 진행한다.
- GitHub Releases에 업로드한다.
- 테스트 배포가 끝나면 Release를 정리한다.

권장 GitHub Actions 기준:
- 개발용 Release: `.github/workflows/dev-release.yml`
- 패키징 백엔드: `.github/workflows/rocky97-rpm.yml`
- 테스트 종료 후 정리: `.github/workflows/dev-release-cleanup.yml`

운영 규칙:
- 개발용 빌드는 `draft` 또는 `prerelease` 성격으로 관리한다.
- 개발용 Release 명칭/태그는 테스트 목적임이 분명해야 한다.
- 테스트 종료 후 아래를 정리한다.
  - GitHub Release
  - 연결된 tag
  - 관련 asset

### 릴리즈 빌드

목적:
- 정식 배포
- 장기 보관
- 재현 가능한 공식 산출물 확보

기준:
- 전체 검증 스크립트를 포함한다.
- 패키징뿐 아니라 정식 릴리즈 절차까지 닫는다.
- GitHub Releases에 업로드하고 보존한다.

권장 GitHub Actions 기준:
- 전체 검증: `.github/workflows/build.yml`
- 필요 시 추가 검증: `.github/workflows/ci.yml`, `.github/workflows/ui.yml`
- 정식 릴리즈 패키징/게시: `.github/workflows/release.yml`

주의:
- 현재 `release.yml`은 최종 패키징과 GitHub Release 게시 절차를 담당한다.
- 릴리즈 승인은 `release.yml` 실행만으로 판단하지 않고, 사전에 필요한 전체 검증 workflow 결과까지 함께 본다.

## 표준 운영 절차

### 개발용 빌드 절차

1. 대상 브랜치에 변경 반영
2. GitHub Actions 개발용 패키징 workflow 실행
   - 표준 workflow: `.github/workflows/dev-release.yml`
3. 생성된 산출물을 GitHub Release에 게시
4. Release asset 경로로 테스트 서버에 배포
5. 기능 검증 수행
6. 검증 완료 후 개발용 Release / tag / asset 삭제
   - 표준 workflow: `.github/workflows/dev-release-cleanup.yml`
7. 다음 개발용 빌드 때 새 Release를 다시 생성

### 릴리즈 빌드 절차

1. 대상 브랜치 확정
2. 전체 검증 workflow 실행
   - 필수 기준: `.github/workflows/build.yml`, `.github/workflows/ui.yml`
3. 검증 통과 확인
4. `release.yml`로 정식 릴리즈 패키징 및 Release 게시
5. Release asset 경로를 공식 다운로드 경로로 사용
6. 릴리즈 기록과 배포 기록 보존

## 금지 사항

- 로컬 머신에서 만든 RPM을 운영 기준 산출물로 사용하지 않는다.
- 로컬 UI 빌드 산출물을 공식 배포 기준으로 삼지 않는다.
- GitHub Actions artifact만 남기고 Release를 생략하지 않는다.
- 개발용 Release를 누적 보관하지 않는다.
- 테스트 미검증 개발용 빌드를 정식 릴리즈로 승격하지 않는다.

## 문서 우선순위

빌드/배포 관련 문서가 여러 개 있을 경우 우선순위는 다음과 같다.

1. 이 문서 `developer/build-release-policy.ko.md`
2. 실제 GitHub Actions workflow 정의
3. 기타 검증/참고 문서

즉, 예전의 로컬 Rocky 검증 문서나 임시 빌드 메모보다 이 문서가 항상 우선한다.

## 현재 기준 해석

- 개발용 빌드: GitHub Actions 기반 빠른 패키징 + GitHub Release 배포 + 테스트 후 정리
- 릴리즈 빌드: GitHub Actions 기반 전체 검증 + 정식 Release 게시
- 로컬 빌드: 비표준, 비운영, 사용하지 않음
