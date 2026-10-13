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

# R6 31-AS04 HTTP 실패·LB 복구 기록 (#1354)

**서비스 실패를 확인했으며 현재 복구됐다. 근본 원인은 미확정이다.** 원래 R6 시작/종료 시각·실패 표본을 보존하고 읽기 전용 관찰을 계속한다. 이 구간을 무실패24시간 PASS로 처리하거나 #1354를 자동 완료하지 않는다.

| 위치 | 기록 시각(KST) | 결과 |
|---|---|---|
| 31 관리 서버 HTTP | 18:13:46.991 | 3초 응답 timeout |
| 외부 HTTP | 18:13:46.993 | 3초 응답 timeout |
| 외부 HTTP | 18:13:49.658 | 본문 일치200, 약671ms |
| 31 관리 서버 HTTP | 18:13:50.484 /18:13:53.988 | RemoteDisconnected |
| VR HAProxy | 18:13:50 | 유일한 backend Layer4 timeout, active0/no server available |
| VR HAProxy | 18:13:54 | Layer4 check passed, backend UP/active1 |
| 31 관리 서버 HTTP | 18:13:58.990 | 본문 일치200 |

HTTP 기록 시각은 요청 완료 시각이다. 서버·외부 경로의 첫 회복 시각이 다르므로 위 시각 차이를 단일 연속 중단 시간으로 확정하지 않는다. 마지막 성공 표본은 양쪽18:13:38경이며, 당시 실패와 수집 간격을 그대로 보존했다.

## 확인한 경로와 한계

- 현재31-AS04 원본 VM `i-35-397-VM`/172.29.31.184는31-1에서 Running이다. VR `r-395-VM`은31-3에서 Running이고,8084 listener가 해당 게스트8080에 연결된다. HAProxy의 L4 건강 검사 실패와 active0 상태가 서버 측 연결 종료 시각과 일치한다. **확인된 장애 경로는 LB의 단일 backend 일시 불가용**이다. 해당 VM·게스트·호스트·네트워크 중 어떤 원인이 최초 지연을 만들었는지는 확정하지 못했다.
- 게스트 `as01-web.service` PID769는10/11 07:53:19 UTC부터 실행 중/NRestarts0이다. 실패 시각 전후 GET 처리 기록이09:13:39→09:13:50 UTC로 약11초 비어 있다. 이 시간 간격을 VM 정지나 웹 서버 재시작으로 단정하지 않는다.
- HAProxy master PID1033/worker1619697 및 관리 PID646030은 관찰 시작 이후 유지됐다. 실패 시각 부근 Mold 재시작 기록은 없고, AS90 클래스/그룹·프로필·정책·예약 fingerprint도 유지됐다. host kernel·대상 QEMU 로그에서는 해당 구간의 확정 원인을 찾지 못했다. 다른 VM의 agent/QGA 경고는 대상 연결 근거 없이 원인으로 지정하지 않는다.

## 복구 확인

18:50:41 KST 확인 기준31서버1859/1862·외부1861/1862(실패 각3/1),32AS04/VPC는 서버·외부 각1862/1862이다. 새 실패는 추가되지 않았다. 양쪽 API156회/오류0·DB32회/오류0, 원본7VM/ROOT·7그룹 및 Running/LB 각1을 보존했다. AS90·관리 JAR/PID 유지, 수집 경고 로그는 비어 있다.

18:49 실제 UI에서31프로젝트 AS04와32일반 사용자 AS04/VPC의 최신 조회·평가·정상 멤버/LB 등록을 확인했다.32AS04는18:37경 CPU 표본6개로 `COLLECTION_UNAVAILABLE/WAITING`을 관측했으나18:48 평가에서는 표본7개/`MIN_MEMBERS`로 돌아왔다. 결측 값을 정상 표본으로 바꾸지 않았다.

[HTTP 원본 구간](http-window.json), [LB 진단](lb-diagnostic.json), [게스트 서비스 진단](guest-diagnostic.json), [복구 수집/AS·원본 보존](recovery-summary.json), [복구 UI](ui-observations.json).

재시작/설정 변경/장애 주입 없이 증거만 회수했다. PR#1356 MERGED/Epic#1346 Closed/#1354 Open을 유지한다. 관측 기한10/14 16:15:33 KST까지 모니터링을 계속하고 기존 누적 경보를 새 장애로 반복 통지하지 않는다.
