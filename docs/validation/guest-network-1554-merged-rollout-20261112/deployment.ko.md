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

# 변경 모듈·UI 빌드와 설치 범위

## 소스와 빌드

소스는 `8ad2d1ecd47092fd3bba8d445fd2c639c7f4be7c`로 고정했다. 이전 설치 기준 `f7097be4d98bd94258120694d3a1fcbaf549209e`부터의 변경 파일을 가장 가까운 Maven POM에 매핑했다. [선정 근거](evidence/module-selection.json)와 [빌드 환경](evidence/source-build-preflight.json)을 남겼다. WSL ext4 `/home/ablecloud/work/dhslove/cloud-epic-1554`에서 실행했으며 `/mnt/c` 빌드와 전체 Cloud 빌드·수동 Actions 실행은 하지 않았다.

| 변경 모듈 | 산출물 |
| --- | --- |
| api | cloud-api |
| engine/schema | cloud-engine-schema |
| engine/orchestration | cloud-engine-orchestration |
| server | cloud-server |
| plugins/hypervisors/kvm | cloud-plugin-hypervisor-kvm |
| plugins/storage/sharedfs/storagevm | cloud-plugin-sharedfs-provider-storagevm |
| systemvm·tools | POM 선택 package 및 공통 SystemVM 파일 |

6개 JAR은 `clean install`을 수행했다. SystemVM의 로컬 의존성을 준비하기 위해 console-proxy 부모·rdpconsole·server 3개 모듈도 로컬 빌드했다. 프로필/미설치 의존성 준비 단계의 실패는 빌드 준비 문제로 처리했으며 제품 결함으로 등록하지 않았다. 최종 [JAR 빌드](evidence/modules-build-result.json)·[SystemVM/Tools 빌드](evidence/systemvm-tools-build-result.json)·[의존성 빌드](evidence/systemvm-console-dependency-build.json)는 성공했다.

[집중 Java 회귀](evidence/merge-java-tests.json)는 10 suites 226 tests, failures/errors/skipped 0이다. 모듈 빌드의 skipTests와 별개로 새로 실행했다. 기존 UI 단위 회귀 수치를 이번 재실행으로 합산하지 않았다.

UI는 Node 20.20.2/npm 10.8.2로 새 production build했다. [빌드 결과](evidence/ui-build-result.json), [마커](evidence/ui-build-markers.json), [파일 manifest](evidence/ui-manifest.json)를 대조했다. 빌드 후 생성된 config 메타데이터는 복원했고 제품 소스는 깨끗한 8ad2d1e 상태였다.

- 활성 app: `js/app.9e630348.js`
- index.html SHA256: `aacff8e209a5722ecbdd5ad4682a0fe56ed68f853a0743b20d215e8ec0b5d65b`
- 정적 파일: 689개
- FTCTL 비동기 마커와 project-switch-start/content-breadcrumb 확인

## 관리 서버와 호스트 설치

설치된 flattened 관리 JAR에 변경 모듈 전체 산출물의 5,638개 entry(5,354 classes)를 반영했다. 무관한 199,391개 entry는 보존했고 신규 entry는 154개다. [ZIP·의존성 대조](evidence/candidate-validation.json)에서 새로 누락된 프로젝트 클래스는 0개다. 기존 JAR의 SnapshotImageMetadata 관련 미해결 참조 1개는 기존 상태와 동일하며 전체 Cloud CI 성공으로 대신 판정하지 않았다.

| 관리 서버 | 최종 JAR SHA256 |
| --- | --- |
| 31 | a9bb46116112cf3bd6c0e8e8d820d8f45fb361aa1a291bfc095fe38295642777 |
| 32 | 3ad3dfd62be5fe016b0456cd2325eab63bef5d0501767ce101541d311c0d8931 |

호스트는 실제 설치된 API/server/schema/KVM 4개 모듈 JAR을 각 3대에 반영하고 mold-agent를 순서대로 재시작했다. 관리 서버 전용 orchestration/storagevm은 관리 JAR에 반영했다. 관리 서버 mold 재시작 후 API 로그인과 `/client/` HTTP200을 확인했다. 신규 linkage/BeanCreation 오류는 설치 점검에서 0개다.

SystemVM 공통 `agent.zip`, `cloud-scripts.tgz`, `patch-sysvms.sh`는 양쪽 관리 서버와 호스트 6대에 반영했다. agent.zip SHA256은 `32e4a0ffb6ef65897d296cbd5a8c0e088a2a70e874ae76107603924710c26b64`다. **QCOW2 템플릿 이미지를 새로 빌드하거나 시험 라우터를 재생성한 작업은 아니다.** 유지 중인 라우터의 실제 기능은 아래 통신 검증으로 확인했다.

UI는 활성 `/usr/share/cloudstack-management/webapp`에 정적 파일만 반영했다. WEB-INF/META-INF·사이트 config·DB 설정·관리 JAR을 보존했고 webapp 루트 삭제나 `rsync --delete`를 사용하지 않았다. 양쪽 689개 파일 해시가 manifest와 일치했다.

배포 전 DB dump·원본 JAR·교체 대상 runtime tar·UI backup의 생성/무결성을 확인했다. 원본·DB·접속 정보는 비공개 WSL stage `/home/ablecloud/work/gn1554-rollout-8ad2d1e/backups` 및 각 서버의 해당 배포 stage rollback에 보관했다. 백업 원문을 저장소에 게시하지 않았다.

- [31 관리 서버](evidence/31-management-deployment.json) / [32 관리 서버](evidence/32-management-deployment.json)
- [31 UI](evidence/ui-31-after.json) / [32 UI](evidence/ui-32-after.json)
- [31 최종 관리/UI/호스트 해시](evidence/31-final-deployed-hashes.json) / [32 최종 해시](evidence/32-final-deployed-hashes.json)
- [비공개 백업 무결성 요약](evidence/predeployment-backups.json)
