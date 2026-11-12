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

# 유지한 최신 확인용 시험 자원

직전 정리에서 남긴 [정확한 ID·이름 목록](evidence/retained-resources.json)을 기준으로 비교했다. 이번 배포 검증에서는 기존 시험망/VM을 삭제하지 않았고 추가 NIC 시험만 되돌렸다. 네트워크 설명도 원래 값으로 복원했다.

| 클러스터 | VM | 네트워크 | VPC | 상태 |
| --- | --- | --- | --- | --- |
| 31 GFS2 | 14 | 16 | 3 | API/libvirt running·NIC 일치 |
| 32 Glue RBD | 15 | 16 | 3 | API/libvirt running·NIC 일치 |
| 합계 | 29 | 32 | 6 | 기존 비시험 자원의 안정 필드도 보존 |

양쪽 기본 검토 프로젝트는 `qa-gn1569-31-shared-reg` / `qa-gn1569-32-shared-reg`다. Shared, Isolated VPN/LB, ConfigDrive L2, 조건부 VPC, Skudonet 프로젝트 및 최신 다중 서브넷 VM을 유지했다. VPN 시험용 외부 클라이언트만 종료했으며 서버측 회귀 자원은 남겼다.

- [31 Shared 확인 화면](http://10.10.31.10:8080/client/#/guestnetwork/87601b32-f211-4609-819f-6d0f4810b788?tab=guest.ip.range)
- [32 Shared 확인 화면](http://10.10.32.10:8080/client/#/guestnetwork/a57a394e-71a6-4748-9fc3-9bcdc40ed3bd?tab=guest.ip.range)
- [31 API·실제 NIC 최종 대조](evidence/31-resource-runtime-verification.json)
- [32 API·실제 NIC 최종 대조](evidence/32-resource-runtime-verification.json)

관리자 화면과 라이트 테마로 복귀했고 임시 viewport override는 reset했다. 브라우저 UI 탭도 양쪽 Shared 확인 화면을 남겼다.
