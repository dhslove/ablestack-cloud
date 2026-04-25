# Apache Main 동기화 이력 - 2026-04-17

> 번역 원칙:
> 이 한국어판은 문서 구조, 운영 지침, 배치 계획, 레코드 제목을 한국어로 옮긴 동반 문서입니다.
> SHA, 코드 식별자, 파일 경로, Source Apache commit subject, 세부 검증/충돌/해결 메모는 의미 왜곡을 막기 위해 원문을 유지했습니다.


## 범위

- 리포지토리: `ablestack-cloud`
- 목표:
  - `apache/cloudstack:main` 변경을 `local/main`에 반영
  - 검증된 변경을 `cherry-pick`으로 `local/ablestack-europa`에 전파
- 작업 기준점:
  - `apache/main`: `2d6280b9da` (`2026-04-17 04:35:25 +0530`)
  - `upstream/main`: `a873fb1ff4` (`2026-02-27 16:05:07 +0900`)
  - `origin/main`: `c6263fbf1c` (`2025-12-11 18:41:37 +0900`)
  - `ablestack-europa`: `661722858d` (`2026-04-16 09:03:28 +0900`)
- `upstream/main`과 `apache/main`의 공통 조상:
  - `da85858e93`

## 범위 요약

- `upstream/main` 기준 이후 Apache 전용 커밋:
  - `162` commits total
  - `150` non-merge commits
- `upstream/main..apache/main`의 순 diff 규모:
  - `576` files changed
  - `36908` insertions
  - `7302` deletions
- Apache delta와 Europa의 중첩 hotspot:
  - `plugins`
  - `server`
  - `api`
  - `engine`
  - `ui`

## 운영 원칙

- Apache merge commit을 그대로 미러링하지 않는다.
- 변경은 기능 또는 리스크 경계 기준으로 로컬 커밋으로 재구성한다.
- 모든 로컬 커밋에는 다음이 포함되어야 한다:
  - 변경 요약
  - 소스 Apache 커밋 SHA 목록
  - 예상 기능 영향도
  - 최소 검증 결과
  - Europa cherry-pick 메모
- Europa cherry-pick 중 충돌이 발생하면:
  - 이 문서에 충돌 파일과 충돌 원인을 기록
  - Apache 수정 의도를 유지하면서 Europa 브랜치 관점에서 해결
  - 필요하면 적응 전용 수정은 후속 커밋으로 분리

## 배치 계획

| 배치 | 주제 | 소스 패턴 / 예시 | Europa 리스크 | 상태 |
| --- | --- | --- | --- | --- |
| B00 | Metadata / CI / docs housekeeping | `.asf.yaml`, `.github/*`, `README`, pre-commit, codespell | 낮음 | 계획됨 |
| B01 | 리소스 한도s / 할당량 / reservation | `[22.0]`, `[20.3]`, quota summary, secondary storage limits | 높음 | 계획됨 |
| B02 | 백업 / 볼륨 / 스냅샷 / import flows | backup, restore, import VM, storage pool, snapshot chain | 높음 | 계획됨 |
| B03 | Network / VPC / LB / NSX / VR | static route, HAProxy, load balancer, NSX, VPC cleanup | 높음 | 계획됨 |
| B04 | Hypervisor / KVM / VMware / CKS | NIC enable/disable, Headlamp, SharedMountPoint, migration | 중간 | 계획됨 |
| B05 | UI / UX / config defaults | UI bug fixes, default language, hidden settings | 중간 | 계획됨 |
| B06 | async jobs / account / user / API ergonomics | async job query, API key restructure, account/domain safeguards | 중간 | 계획됨 |

## 커밋 기록 템플릿

### 커밋 ID: `TBD`

- Local branch: `main` or `ablestack-europa`
- 로컬 커밋: `TBD`
- 소스 Apache 커밋:
  - `TBD`
- 요약:
  - `TBD`
- 기능 영향도:
  - `TBD`
- 검증:
  - `TBD`
- Europa cherry-pick 상태:
  - `Pending`
- 충돌 메모:
  - `None`
- 해결 메모:
  - `None`

## 적용 기록

### 기록 001 - EL10 python six compatibility packaging 수정

- 로컬 브랜치: `main`
- 로컬 커밋: `a1e520cbbb`
- 소스 Apache 커밋:
  - `80ee7f183f` Fix six package incompatiblity with EL10 (#12799)
- 요약:
  - Add EL packaging requirements for `python3-six` and `python3-protobuf`
  - Bundle compatible `mysql_connector_python` wheels for both Python 3.6 and Python 3.8+
  - Install the matching wheel in `%post management` based on detected Python version
- 기능 영향도:
  - Prevent EL10 package installation/runtime issues caused by Python dependency mismatch
  - Preserve EL8 compatibility by keeping the Python 3.6-compatible connector path
- 검증:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - Staged diff only touches `packaging/el8/cloud.spec`
- Europa cherry-pick 상태:
  - `d1be005ab5`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 002 - xcpng integration test 정리 강화

- 로컬 브랜치: `main`
- 로컬 커밋: `993945b793`
- 소스 Apache 커밋:
  - `7cdcf571fa` Fix xcpng test failures (#12812)
- 요약:
  - Wrap zone, pod, and network preparation/cleanup flows in `try/finally`
  - Re-enable disabled resources even when intermediate test steps fail
  - Reduce cascading failures across integration test scenarios
- 기능 영향도:
  - No runtime product behavior change
  - Improves repeatability of xcpng-related integration tests by preventing leaked disabled resources
- 검증:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - `python3 -m py_compile test/integration/component/maint/test_redundant_router_deployment_planning.py test/integration/smoke/test_public_ip_range.py`
- Europa cherry-pick 상태:
  - `6e0af4c808`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 003 - async jobs 필터ing 기준 리소스 유형 없이 리소스 ID

- 로컬 브랜치: `main`
- 로컬 커밋: `d3e606c989`
- 소스 Apache 커밋:
  - `38abe2df0b` Allow list async jobs by resource type alone (#13011)
- 요약:
  - Allow `listAsyncJobs` to filter by `resourceType` without requiring `resourceId`
  - Only apply the `instanceUuid` filter when a valid `resourceId` is supplied
  - Clarify the validation error when `resourceId` is used without `resourceType`
- 기능 영향도:
  - Expands `listAsyncJobs` API usability for callers that want job lists for a resource class without a specific resource UUID
  - Prevents unnecessary validation failure when only `resourceType` is provided
- 검증:
  - Apache patch required manual conflict resolution on `main` because the surrounding `QueryManagerImpl` method had drifted
  - Planned verification: targeted `server` module compile
- Europa cherry-pick 상태:
  - `d8d95533d9`
- 충돌 메모:
  - `main` lacked the exact Apache context block near the end of the async job search method, causing a patch context conflict
- 해결 메모:
  - Re-applied only the intended resource filter logic immediately before the existing `searchAndCount` call

### 기록 004 - 백업 list keyword 필터 보정

- 로컬 브랜치: `main`
- 로컬 커밋: `f9ac2c3d95`
- 소스 Apache 커밋:
  - `86c9f7bd94` Fix backup list
- 요약:
  - Keep backup name keyword filtering inside the existing `and` condition chain instead of opening a new `or` group
  - Preserve the `backupOfferingId` filter when listing backups with a keyword
- 기능 영향도:
  - Prevents `listBackups` keyword searches from returning rows that bypass the selected backup offering constraint
  - Narrows results to the intended offering-scoped backup set
- 검증:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to a single logical change in `BackupManagerImpl`
- Europa cherry-pick 상태:
  - `8d961d78f9`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 005 - countVgpuVMs prepared statement 순서 수정

- 로컬 브랜치: `main`
- 로컬 커밋: `6937fe8c06`
- 소스 Apache 커밋:
  - `6516f7f1aa` Fix query execution in countVgpuVMs (#12713)
- 요약:
  - Delay preparation and parameter binding of the second vGPU count query until after the legacy query has finished executing
  - Avoid mixing statement preparation/binding across the two query paths
- 기능 영향도:
  - Prevents erroneous query execution in `countVgpuVMs`
  - Improves correctness of aggregated vGPU VM counting used by scheduling or capacity-related paths
- 검증:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to prepared statement ordering changes in `VMInstanceDaoImpl`
- Europa cherry-pick 상태:
  - `866a23eb07`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 006 - 스토리지 풀 reorder 로깅 및 random compatibility

- 로컬 브랜치: `main`
- 로컬 커밋: `7aae7631fe`
- 소스 Apache 커밋:
  - `161b4177c2` Add logs for storage pools reordering (#10419)
- 요약:
  - Improve storage pool allocator logging around reordering, shuffle, disk provisioning, and search start/end
  - Treat `userconcentratedpod_random` the same as `random` in the volume allocation reorder path
- 기능 영향도:
  - Improves observability when debugging allocator decisions and storage pool ordering
  - Preserves random reordering behavior for configurations that still use `userconcentratedpod_random`
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main`
  - Verified the resolved method keeps the Apache condition for `userconcentratedpod_random` and the expanded logging changes
- Europa cherry-pick 상태:
  - `dfd87dee3b`
- 충돌 메모:
  - `main` had diverged in `reorderStoragePoolsBasedOnAlgorithm`, where `userconcentratedpod_random` handling was missing and the log level differed
- 해결 메모:
  - Kept the Apache behavior by routing `userconcentratedpod_random` through the random reorder branch and preserving the newer logging

### 기록 007 - managed 스토리지 복원 host null 방어

- 로컬 브랜치: `main`
- 로컬 커밋: `23e971802a`
- 소스 Apache 커밋:
  - `84676afd5c` Check for null host before proceeding with VM volume operations in managed storage while restoring VM (#12879)
- 요약:
  - Guard managed-storage restore cleanup when the VM host lookup returns `null`
  - Skip detach/delete command construction instead of dereferencing a missing host
- 기능 영향도:
  - Prevents restore-time failures caused by null host dereference during managed storage volume handling
  - Allows the restore flow to exit this cleanup path safely when the previous host record is unavailable
- 검증:
  - Applied as a focused manual port on `main` to avoid unrelated formatting churn from the Apache patch
  - Logic inspected in `handleManagedStorage`
- Europa cherry-pick 상태:
  - `c444e0dfe3`
- 충돌 메모:
  - `N/A on main`; only the functional null-host guard was ported
- 해결 메모:
  - Kept the Apache intent while limiting the local diff to the host-null safety check

### 기록 008 - ACL metadata for 백업-based 복원 및 생성 APIs

- 로컬 브랜치: `main`
- 로컬 커밋: `ada57be8e8`
- 소스 Apache 커밋:
  - `24fd440ee7` Fix create VM from backup
  - `8ce1c9876e` fix restore volume from backup and attach
- 요약:
  - Add `@ACL` metadata to `backupId` in `CreateVMFromBackupCmd`
  - Add `@ACL` metadata to `backupId`, `volumeUuid`, and `vmId` in `RestoreVolumeFromBackupAndAttachToVMCmd`
- 기능 영향도:
  - Improves API ACL enforcement and parameter-level access checks for backup-driven restore/create flows
  - Aligns backup resource parameters with existing ACL-aware command patterns
- 검증:
  - Both Apache patches applied cleanly on `main`
  - Cached diff is limited to ACL annotations and one import addition
- Europa cherry-pick 상태:
  - `0e0f11bf7e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 009 - 차단 백업 deletion 중 pending 복원/생성 jobs

- 로컬 브랜치: `main`
- 로컬 커밋: `30c0421416`
- 소스 Apache 커밋:
  - `7ba5240b31` Block backup deletion while create-VM-from-backup or restore jobs are in progress (#12792)
- 요약:
  - Check for pending async jobs tied to a backup before allowing deletion
  - Block deletion while create-from-backup or restore flows are still running
  - Add unit coverage for the pending-job rejection path
- 기능 영향도:
  - Prevents destructive races between backup deletion and active backup restore/create operations
  - Reduces the chance of partial restore/create failures caused by deleting the source backup mid-flight
- 검증:
  - `BackupManagerImpl` change applied cleanly on `main`
  - `BackupManagerTest` needed a small mock-field merge to accommodate the new `AsyncJobManager` dependency
- Europa cherry-pick 상태:
  - `8cda57843e`
- 충돌 메모:
  - Test file context had diverged because local mocks already included `BackupOfferingDetailsDao` and `DomainHelper`
- 해결 메모:
  - Kept all existing mocks and added `AsyncJobManager` alongside them, then preserved the Apache pending-jobs test case

### 기록 010 - preload 백업 architecture 중 생성-from-백업

- 로컬 브랜치: `main`
- 로컬 커밋: `93c5d9caa9`
- 소스 Apache 커밋:
  - `1ff9eec997` Load arch data for backup from template during create instance from backup (#12801)
- 요약:
  - Load the backup source template or ISO architecture before opening the create-from-backup flow
  - Pre-fill `selectedArchitecture` from backup metadata instead of resetting to the zone default
  - Pass the fetched backup architecture through `CreateVMFromBackup` into `DeployVMFromBackup`
- 기능 영향도:
  - Prevents backup-based instance creation from silently defaulting to the wrong architecture on multi-arch zones
  - Keeps create-from-backup requests aligned with the source template or ISO architecture during restore-driven provisioning
- 검증:
  - Applied cleanly on `main` with changes limited to `DeployVMFromBackup.vue` and `CreateVMFromBackup.vue`
  - Frontend build or lint verification has not been run in this environment yet
- Europa cherry-pick 상태:
  - `e086d987bd`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 011 - honor 백업 command timeout for NAS 생성 및 복원

- 로컬 브랜치: `main`
- 로컬 커밋: `38800d1cb0`
- 소스 Apache 커밋:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- 요약:
  - Use `command.getWait()` as a millisecond timeout for NAS backup create and restore operations
  - Fall back to `commands.timeout` from `LibvirtComputingResource` when the command-specific wait is not set
  - Update restore-side unit mocks so `rsync` failures are asserted through the timeout-aware script path
- 기능 영향도:
  - Prevents NAS backup create and restore flows from timing out too early when backup operations legitimately run longer
  - Aligns KVM backup execution with the configured command timeout behavior already used by other libvirt command wrappers
- 검증:
  - `LibvirtTakeBackupCommandWrapper` and the restore tests applied cleanly on `main`
  - `LibvirtRestoreBackupCommandWrapper` required a manual port because the Apache patch was based on a newer block-device helper structure than the current branch
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `e6d0c25dba`
- 충돌 메모:
  - Apache changed timeout handling inside a newer `replaceBlockDeviceWithBackup` flow, while this branch still keeps the older RBD-only restore helper
- 해결 메모:
  - Ported only the timeout semantics into the current helper layout: `rsync` now uses the timeout-aware script overload and `QemuImg` receives milliseconds directly without altering existing RBD restore behavior

### 기록 012 - 정리 백업 schedule references before schedule deletion

- 로컬 브랜치: `main`
- 로컬 커밋: `656eeb1816`
- 소스 Apache 커밋:
  - `27e4d979f1` Clean up backup references to their schedules when the schedules are deleted (#12401)
- 요약:
  - Null out `backups.backup_schedule_id` before removing a backup schedule row
  - Move backup schedule response construction out of `BackupScheduleDaoImpl` and into `ApiResponseHelper`
  - Drop the unused `backup_interval_type` column from `cloud.backups`
- 기능 영향도:
  - Prevents deleted schedules from leaving stale schedule references behind on existing backups
  - Keeps backup schedule API responses working without coupling DAO code to VM lookup concerns
  - Removes an unused schema column so the backup table matches current runtime behavior
- 검증:
  - Java-side DAO and API response changes applied cleanly on `main`
  - The schema upgrade file required a manual merge because this branch does not yet include unrelated Apache `vm_template` updates that were present in the parent context of the patch
  - Database migration and Maven-based Java tests could not be run because no DB harness and no `mvn`/`mvnw` are available in this environment
- Europa cherry-pick 상태:
  - `f39f239fba`
- 충돌 메모:
  - The Apache patch touched a shared schema upgrade file that has drifted on this branch due to missing earlier upstream statements
- 해결 메모:
  - Ported only the backup-schedule cleanup line into the local schema file and intentionally left unrelated `vm_template` update statements for their own upstream sync commits

### 기록 013 - reserve 백업 및 버킷 limits 중 생성 및 삭제 operations

- 로컬 브랜치: `main`
- 로컬 커밋: `281cc87487`
- 소스 Apache 커밋:
  - `19b4ef1069` server: reserve backup, bucket resource limits during operations
- 요약:
  - Wrap backup create/delete resource checks with `CheckedReservation` for `backup` and `backup_storage`
  - Reserve `bucket` and `object_storage` limits during bucket allocation and deletion paths
  - Extend unit coverage for reservation-aware backup and bucket workflows
- 기능 영향도:
  - Prevents concurrent backup and bucket operations from passing limit checks and only failing after counters are updated too late
  - Keeps backup and object storage counters aligned with the actual success or failure of create/delete operations
- 검증:
  - `BucketApiServiceImpl` and its tests applied cleanly on `main`
  - `BackupManagerImpl` and `BackupManagerTest` required a manual merge because this branch already carries local backup safety changes around pending restore/create jobs
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `9e4148ab98`
- 충돌 메모:
  - Backup manager create/delete paths overlapped with the locally added pending-job guard and related test scaffolding
- 해결 메모:
  - Preserved the local pending backup job protection and merged the Apache reservation-based limit checks around the same backup lifecycle methods

### 기록 014 - review 수정es for 백업 및 버킷 reservation flow

- 로컬 브랜치: `main`
- 로컬 커밋: `ddbed8a9cf`
- 소스 Apache 커밋:
  - `13842a626d` Address reviews
- 요약:
  - Let bucket delete and backup delete paths propagate `ResourceAllocationException`
  - Simplify backup reservation error handling so scheduled-backup alerts trigger only for actual limit exceptions
  - Tighten retention-cleanup method signatures and related unit tests for the reservation-aware backup flow
- 기능 영향도:
  - Prevents reservation failures from being swallowed inside generic runtime exceptions on backup and bucket operations
  - Keeps scheduled backup limit alerts focused on real quota violations instead of unrelated runtime failures
- 검증:
  - All review follow-up files applied cleanly on `main` except `DeleteBucketCmd`
  - `DeleteBucketCmd` needed a small manual merge to keep the local event-detail formatting while adding `ResourceAllocationException` to the API contract
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `d09487d87e`
- 충돌 메모:
  - Local `DeleteBucketCmd` event detail handling had diverged from the Apache parent context
- 해결 메모:
  - Kept the local `getResourceUuid(ApiConstants.ID)` event detail string and added the wider exception signature required by the reservation-aware delete flow

### 기록 015 - validate 버킷 할당량 growth 및 reservations 중 업데이트

- 로컬 브랜치: `main`
- 로컬 커밋: `29ed88dcb8`
- 소스 Apache 커밋:
  - `2511fdffaa` Implement limit validations on updateBucket
- 요약:
  - Move bucket quota-delta handling into a dedicated `updateBucketQuota` helper
  - Use `CheckedReservation` when bucket quota increases so `object_storage` growth is reserved before counters are incremented
  - Keep quota decreases as immediate counter decrements and allocated-size adjustments
- 기능 영향도:
  - Closes the remaining gap where `updateBucket` could increase object-storage quota without reservation-aware limit protection
  - Makes bucket quota updates consistent with the create/delete reservation model introduced in the previous two commits
- 검증:
  - Applied cleanly on `main`
  - Logic review confirms quota increases now reserve `object_storage` before incrementing counts
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `3a96b973dc`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 016 - 강제 secondary 스토리지 limits 중 다운로드 flows

- 로컬 브랜치: `main`
- 로컬 커밋: `0ecd207c64`
- 소스 Apache 커밋:
  - `03dfe4d1f3` secondary storage resource limit for download
- 요약:
  - Track `LIMIT_REACHED` as a first-class download error state and stop persisting bogus size values for failed downloads
  - Use actual downloaded bytes as the fallback template size signal during secondary storage download progress
  - Recalculate secondary storage counts after template registration callbacks so final counters match persisted store-ref state
- 기능 영향도:
  - Prevents template and volume downloads from silently overrunning secondary storage limits during in-progress updates
  - Keeps template store size accounting consistent when download answers only report physical size or fail due to limit exhaustion
- 검증:
  - All download-state, resource-limit, and secondary-storage manager files applied cleanly on `main`
  - `HypervisorTemplateAdapter` required a manual merge because the local branch still used an older callback structure that increments counts before recalculation
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `9db209ed17`
- 충돌 메모:
  - The template callback logic in `HypervisorTemplateAdapter` had diverged around when secondary storage counts are incremented versus recalculated
- 해결 메모:
  - Preserved the local usage-event and increment flow, then added the Apache post-callback secondary-storage recalculation in the current method layout

### 기록 017 - 강제 secondary 스토리지 limits 중 업로드 flows

- 로컬 브랜치: `main`
- 로컬 커밋: `20103f019d`
- 소스 Apache 커밋:
  - `81a8ac8e1f` secondary storage resource limit for upload
- 요약:
  - Add abort-aware upload status polling so the management server can stop uploads after limit failures
  - Reserve `secondary_storage` usage during template and volume upload progress updates
  - Keep upload channels and SSVM-side state in sync when uploads are aborted or fail due to limit exhaustion
- 기능 영향도:
  - Prevents template and volume uploads from continuing after the management server detects secondary storage quota exhaustion
  - Makes upload-side secondary storage accounting consistent with the download-side reservation flow
- 검증:
  - Upload command, SSVM resource handler, and secondary-storage resource changes applied cleanly on `main`
  - `ImageStoreUploadMonitorImpl` required a manual merge because the current branch did not yet carry the reservation/account helper dependencies used by the Apache upload monitor changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `a411135d0f`
- 충돌 메모:
  - Upload monitor imports and injected collaborators had diverged from the Apache parent context
- 해결 메모:
  - Added the Apache reservation/account helper dependencies into the current upload monitor and kept the Apache abort-aware polling flow intact

### 기록 018 - 후속 secondary 스토리지 limit review 수정es

- 로컬 브랜치: `main`
- 로컬 커밋: `28548c2c7f`
- 소스 Apache 커밋:
  - `23b19a9776` review comments
- 요약:
  - Fix null-size and null-account edge cases in download and upload limit updates
  - Add size guards around template copy reservations so secondary storage reservations are only created when template size is known
  - Carry the same Apache review cleanup for project reservation code paths that live in the same upstream commit
- 기능 영향도:
  - Prevents limit-check helpers from miscounting when DB size fields or owner lookups are temporarily null
  - Avoids unnecessary reservation attempts for zone copy operations when template size has not been resolved yet
- 검증:
  - `DownloadListener` and `ImageStoreUploadMonitorImpl` review fixes applied cleanly on `main`
  - `ProjectManagerImpl` and `TemplateManagerImpl` required manual merges because the current branch had older pre-review reservation blocks in those exact sections
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `6f997e5271`
- 충돌 메모:
  - Project ownership transfer and template cross-zone copy logic had drifted around reservation blocks since the Apache review commit was authored
- 해결 메모:
  - Preserved the current branch control flow, then folded in the Apache reservation guards and null-safe helper fixes without broad structural rewrites

### 기록 019 - 방어 스냅샷 copy reservations against concurrency races

- 로컬 브랜치: `main`
- 로컬 커밋: `dad19a2215`
- 소스 Apache 커밋:
  - `8608b4edd0` Fix snapshot copy resource limit concurrency
- 요약:
  - Wrap snapshot copy-to-zone flow with `CheckedReservation` instead of a separate pre-check
  - Pass an explicit `shouldCheckResourceLimits` flag so snapshot-chain copies do not double-reserve secondary storage
  - Update snapshot copy tests to reflect reservation-based behavior instead of direct `checkResourceLimit` mocking
- 기능 영향도:
  - Prevents concurrent snapshot copy operations from passing standalone checks and then racing on secondary storage quota updates
  - Keeps snapshot copy reservations aligned with the real copy lifecycle, including chain-copy and KVM incremental snapshot cases
- 검증:
  - Applied cleanly on `main`
  - Snapshot manager and snapshot copy test updates are limited to reservation flow and test expectation changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick 상태:
  - `06ae354d0b`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 020 - reserve start VM limits 및 host-tag aware reservations

- 로컬 브랜치: `main`
- 로컬 커밋: `46107a4db7`
- 소스 Apache 커밋:
  - `4bcd509193` Fix resource limit reservation and check during StartVirtualMachine
- 요약:
  - Extract the deployment-heavy portion of `startVirtualMachine` into a helper so reservation handling wraps only the limit-sensitive path
  - Replace standalone `checkVmResourceLimit` usage with `CheckedReservation` blocks for `user_vm`, `cpu`, and `memory` during running-only resource counting
  - Preserve current branch GPU accounting by reserving `gpu` resources when the service offering declares GPU capacity
- 기능 영향도:
  - Prevents concurrent `StartVirtualMachine` requests from passing a pre-check and then overshooting runtime VM resource limits during actual deployment
  - Aligns host-tag-aware start reservations with the same tagged resource accounting already used by deploy and destroy flows
  - Keeps GPU quota behavior symmetric on this branch so start reservations do not weaken existing GPU limit enforcement
- 검증:
  - `UserVmManagerImpl` is the only touched source file on `main`
  - The Apache patch required a manual merge because the local branch still carried the monolithic start method and a different VM details DAO field name
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `5c493d982b`
- 충돌 메모:
  - The Apache patch split `startVirtualMachine` into a helper while the local branch still had the older inline deployment flow, so the full method body conflicted
  - The extracted helper referenced `userVmDetailsDao`, but this branch uses `vmInstanceDetailsDao`
  - The pre-existing local `checkVmResourceLimit` path already covered GPU limits, while the Apache reservation patch only reserved VM, CPU, and memory
- 해결 메모:
  - Kept the Apache helper extraction and reservation structure, then adapted the helper to the local DAO field name
  - Added a conditional GPU reservation to preserve the branch's existing resource-limit coverage and keep start/destroy accounting behavior symmetric

### 기록 021 - reserve extension-managed resource details

- 로컬 브랜치: `main`
- 로컬 커밋: `7a5a33cbe7`
- 소스 Apache 커밋:
  - `95816b44e9` extensions: allow reserved resource details
- 요약:
  - Add `reservedresourcedetails` support to create/update/list extension API and UI flows so operators can declare extension-owned VM detail keys
  - Persist reserved detail names in extension hidden details, including built-in defaults for matching in-built extensions such as Proxmox
  - Extend VM detail filtering and VM update validation so non-admin users cannot view or mutate extension-reserved detail keys on extension-backed instances
  - Expose template extension linkage in `user_vm_view` and `UserVmJoinVO` so response filtering can decide which extension reservations apply
- 기능 영향도:
  - Lets extension authors reserve metadata keys that must stay under extension control instead of being visible or editable by tenants
  - Prevents end-user VM detail APIs and responses from leaking or overwriting extension-managed identifiers such as hypervisor-side instance metadata
  - Surfaces the reserved-detail configuration through the extension admin UI and API so the policy is manageable without direct DB edits
- 검증:
  - Most Apache files applied cleanly on `main`, including API constants, extension manager, schema view, and UI changes
  - `UserVmJoinDaoImpl` and `UserVmJoinDaoImplTest` required a manual merge because this branch already carried deploy-as-is response handling through `VMTemplateDao`
  - Maven and UI build execution have not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `99459f7c42`
- 충돌 메모:
  - `UserVmJoinDaoImpl` already injected `VMTemplateDao` for deploy-as-is allowed-details handling in the same field block where Apache added `ExtensionHelper`
  - The corresponding unit test mock block diverged for the same reason
- 해결 메모:
  - Kept the existing deploy-as-is response behavior and injected `ExtensionHelper` alongside `VMTemplateDao`
  - Preserved the Apache reserved-detail filtering logic without changing the branch-specific allowed-details response path

### 기록 022 - harden PVLAN VM setup against null inputs

- 로컬 브랜치: `main`
- 로컬 커밋: `613fcd2a5b`
- 소스 Apache 커밋:
  - `e10c066cc1` Fix NPE during VM setup for pvlan
- 요약:
  - Guard `setupVmForPvlan` against null NIC profiles and null broadcast URIs before dereferencing them
  - Skip PVLAN setup cleanly when the broadcast URI scheme is not `pvlan`
  - Return early when the target host lookup fails instead of continuing into agent command construction
- 기능 영향도:
  - Prevents VM deploy/start/stop paths from failing with a null-pointer exception when PVLAN metadata is incomplete or absent
  - Turns bad or missing PVLAN state into an explicit skip path with diagnostic logging, which is safer for mixed network environments
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `UserVmManagerImpl.setupVmForPvlan`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `ff60935d82`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 023 - validate VM compute details only when the offering 허용s it

- 로컬 브랜치: `main`
- 로컬 커밋: `9a28b608bc`
- 소스 Apache 커밋:
  - `c6936889f5` server: prevent adding vm compute details when not applicable
- 요약:
  - Tighten `validateCustomParameters` so empty custom-parameter maps only fail for dynamic offerings, not for fixed offerings
  - Use `isCustomCpuSpeedSupported()` when validating CPU speed overrides and surface a clearer fixed-speed error message
  - Reject CPU, memory, and CPU speed detail updates up front in `verifyVmLimits` when the current offering is not dynamic
  - Add unit coverage for fixed-offering rejection and constrained custom-offering CPU speed validation
- 기능 영향도:
  - Prevents update flows from silently treating fixed offerings like custom offerings when VM compute detail keys are present
  - Stops non-applicable VM compute detail writes earlier, with more accurate error messages for operators and API callers
  - Keeps dynamic offering validation aligned with the offering's real CPU-speed customization capability on this branch
- 검증:
  - `UserVmManagerImpl` picked up the functional change on `main`
  - `UserVmManagerImplTest` and `KVMGuruTest` required manual merge because this branch already had additional test scaffolding and uses `isLimitCpuUse()` in the KVM guru path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f89d3bf2f0`
- 충돌 메모:
  - `UserVmManagerImpl` import blocks diverged because the local branch already carried lease and snapshot-policy support alongside newer Apache scheduling and reservation imports
  - `KVMGuruTest` conflicted because upstream expected `getLimitCpuUse()` while this branch still exercises `isLimitCpuUse()`
  - `UserVmManagerImplTest` had a long trailing test block unique to this branch, so the upstream helper tests for dynamic offering validation landed inside an end-of-file conflict
- 해결 메모:
  - Kept the Apache validation logic, but preserved local imports needed by lease and snapshot-policy features
  - Retained the Apache config-key preservation in `KVMGuruTest` while binding it to the branch's `isLimitCpuUse()` API
  - Kept all existing local tests and appended the new dynamic-offering validation cases after the current tail section

### 기록 024 - 수정 템플릿 type handling 중 ISO 업로드

- 로컬 브랜치: `main`
- 로컬 커밋: `662c97af3c`
- 소스 Apache 커밋:
  - `c3d6a8cff7` server: fix templatetype during iso upload
- 요약:
  - Treat `GetUploadParamsForIsoCmd` as a user-template upload path so template-type validation returns `TemplateType.USER`
  - Switch the user-VM system-template guard to `TemplateType.SYSTEM.equals(...)` to avoid dereferencing a null template type
- 기능 영향도:
  - Prevents ISO upload-parameter requests from being misclassified as unsupported template-type operations
  - Removes a null-sensitive comparison in the user VM deploy path, making template-type validation safer when template metadata is incomplete
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `TemplateManagerImpl.validateTemplateType` and the system-template gate in `UserVmManagerImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 025 - default 업로드 템플릿 types 및 backfill null 템플릿 records

- 로컬 브랜치: `main`
- 로컬 커밋: `ad19bfc1db`
- 소스 Apache 커밋:
  - `470812100e` server: set template type to ROUTING or USER if template type is not specified when upload a template
- 요약:
  - Default `GetUploadParamsForTemplateCmd` uploads to `ROUTING` or `USER` when `templatetype` is omitted, using the existing `isrouting` flag
  - Reject non-admin upload-parameter requests for non-user template types with a specific API error
  - Backfill `cloud.vm_template.type` from `NULL` to `USER` in the 4.22.0 to 4.22.1 schema migration
- 기능 영향도:
  - Makes template upload-parameter requests behave like template registration, so omitted `templatetype` values no longer fall through as `null`
  - Prevents inconsistent permission handling between upload-parameter generation and later template registration paths
  - Reduces runtime ambiguity for older template rows that still carry a null `type`
- 검증:
  - `TemplateManagerImpl` picked up the functional change on `main`
  - `schema-42200to42210.sql` required a manual merge because this branch already dropped `backup_interval_type` in the same tail section
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - The schema migration tail diverged on `main` because local backup cleanup SQL replaced the upstream context around the new `vm_template.type` backfill
- 해결 메모:
  - Kept the local `backup_interval_type` drop and added only the Apache null-type backfill, without reintroducing unrelated upstream migration context

### 기록 026 - 표시 full 네트워크 오퍼링 labels in add-tier dropdown

- 로컬 브랜치: `main`
- 로컬 커밋: `745a6c679f`
- 소스 Apache 커밋:
  - `120a43648b` set width of dropdown select items for Network Offering during add tier dialog
- 요약:
  - Add a `title` attribute to network offering select options in `VpcTiersTab.vue`
  - Reformat the option markup for readability while keeping the same displayed label text
- 기능 영향도:
  - Lets operators see the full network offering label on hover when the add-tier dropdown truncates long entries
  - Improves network offering selection confidence in crowded UI environments without changing API or backend behavior
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/VpcTiersTab.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 027 - 방어 생성-network global action when zone context is absent

- 로컬 브랜치: `main`
- 로컬 커밋: `03c4c21aba`
- 소스 Apache 커밋:
  - `db83622956` ui: fix create network from global create menu
- 요약:
  - Use optional chaining when reading `resource.zoneid` in `CreateNetwork.vue`
  - Keep the zone filter only for deploy-VM and backup entry points, but avoid dereferencing an absent `resource`
- 기능 영향도:
  - Prevents the global create-network action from throwing when it is opened without a preselected zone context
  - Keeps zone-scoped behavior unchanged for deploy and backup entry paths while making the generic menu entry safer
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/CreateNetwork.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 028 - 라우팅 템플릿-zone deletion back to the 템플릿 list

- 로컬 브랜치: `main`
- 로컬 커밋: `dd51cb66fa`
- 소스 Apache 커밋:
  - `7aa0558c5b` ui: avoid 404 after deleting template zones
- 요약:
  - Redirect `TemplateZones.vue` to `/template` when the delete flow leaves the current detail view without remaining rows
  - Keep the surrounding table and modal behavior unchanged while avoiding a dead back-navigation path
- 기능 영향도:
  - Prevents the UI from landing on a stale detail route after the last template-zone entry is removed
  - Gives operators a predictable post-delete destination instead of depending on browser history state
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/image/TemplateZones.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 029 - 표시 security group selection for Basic zones 및 owner-범위 제한d SG lists

- 로컬 브랜치: `main`
- 로컬 커밋: `6efdb2a2cb`
- 소스 Apache 커밋:
  - `71daf84c9e` Show security group selection in Basic zone VM deployment and fix SG listing for cross-domain deployments
- 요약:
  - Always show the security-group step for Basic zones in `DeployVM.vue`
  - Pass the selected owner context (`domainId`, `account`, `projectId`) into `SecurityGroupSelection.vue`
  - Refresh the listed security groups when the owner context changes, and query `listSecurityGroups` with that owner context instead of always using the current session defaults
- 기능 영향도:
  - Restores security-group selection during VM deployment in Basic zones where the UI previously hid the step
  - Prevents cross-domain deployments from showing the wrong security-group list when the VM owner differs from the logged-in operator
  - Clears stale security-group selections when the deployment owner changes, reducing accidental carry-over between accounts or projects
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/compute/DeployVM.vue` and `ui/src/views/compute/wizard/SecurityGroupSelection.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 030 - 허용 설정 가능한 default UI language

- 로컬 브랜치: `main`
- 로컬 커밋: `824130f850`
- 소스 Apache 커밋:
  - `ed575cc0a1` New config.json variable to set the ACS default language
- 요약:
  - Allow `defaultLanguage` as a GUI theme primitive property and add the sample key to `ui/public/config.json`
  - Use `defaultLanguage` when initializing `TranslationMenu.vue` if the user has no saved `LOCALE`
  - Let `guiTheme.js` propagate a theme- or config-provided default language into runtime config and local storage, then load that language pack
- 기능 영향도:
  - Makes the initial UI locale configurable through static config and dynamic GUI theme customization, instead of hard-coding a single fallback in the header component
  - Aligns first-load language selection across login, theme application, and later locale switches
  - Gives operators a supported way to preseed the portal language for new browsers or cleared storage sessions
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `GuiThemeServiceImpl`, `ui/public/config.json`, `TranslationMenu.vue`, and `ui/src/utils/guiTheme.js`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 031 - 차단 account 및 domain deletion when 삭제-protected VMs remain

- 로컬 브랜치: `main`
- 로컬 커밋: `3883bfda42`
- 소스 Apache 커밋:
  - `b196e97cc3` Prevent deletion of account and domain if either of them has deleted protected instance
- 요약:
  - Add DAO helpers to find active delete-protected VMs by account and by a set of domain IDs
  - Validate account deletion in `AccountManagerImpl` before destructive cleanup starts
  - Validate domain deletion in `DomainManagerImpl` across the full domain hierarchy, including child domains
  - Extend `DomainManagerImplTest` stubs so delete-domain tests continue to model an empty delete-protected VM result set
- 기능 영향도:
  - Prevents operators from deleting an account or domain while delete-protected instances still exist beneath it
  - Makes delete protection effective beyond direct VM delete calls by enforcing the same guard on higher-level ownership cleanup paths
  - Limits false positives to active VMs only by filtering out removed instances in the DAO layer
- 검증:
  - Applied cleanly on `main`
  - The change is limited to `VMInstanceDao`, `VMInstanceDaoImpl`, `AccountManagerImpl`, `DomainManagerImpl`, and `DomainManagerImplTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `None yet`

### 기록 032 - 숨김 해제 및 centralize JavaScript interpretation gating

- 로컬 브랜치: `main`
- 로컬 커밋: `ef05a90e62`
- 소스 Apache 커밋:
  - `9f57a4dd19` Unhide setting `js.interpretation.enabled`
- 요약:
  - Move `js.interpretation.enabled` ownership from `ManagementService` into `JsInterpreterHelper` as a normal configurable system setting
  - Unhide the setting during the `4.22.1.0 -> 4.23.0.0` upgrade by migrating its stored value, category, component, and dynamic flag
  - Replace scattered `ManagementService`/`QuotaService` checks with `JsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` in host, storage, secondary-storage selector, and quota tariff flows
  - Always register the secondary storage selector commands, but gate their JS-bearing parameters at validation time instead of hiding the commands entirely
- 기능 영향도:
  - Makes `js.interpretation.enabled` visible and manageable as a standard system setting instead of a hidden internal knob
  - Centralizes enable/disable enforcement for JS-backed parameters, reducing drift between quota, host-tag, storage-pool, and selector validation paths
  - Preserves safety when JS interpretation is disabled by rejecting only the parameters that require it, rather than removing whole APIs from discovery
- 검증:
  - `QuotaResponseBuilderImpl` required a manual merge on `main` because this branch still had the older `_quotaService.isJsInterpretationEnabled()` activation-rule guard in the same field block where Apache now keeps the quota-summary role set
  - The final `main` state uses `jsInterpreterHelper` for activation-rule validation and removes the old quota-service helper path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `QuotaResponseBuilderImpl` conflicted on `main` because the local branch still carried `checkActivationRulesAllowed()` and `_quotaService.isJsInterpretationEnabled()`-based gating
- 해결 메모:
  - Dropped the old quota-service activation-rule helper and kept the Apache `jsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` checks as the single validation path

### 기록 033 - 지원 async job 조회 기준 resource

- 로컬 브랜치: `main`
- 로컬 커밋: `893222e873`
- 소스 Apache 커밋:
  - `47c5bb8ee7` Support list/query async jobs by resource (#12983)
- 요약:
  - Add `resourceId` and `resourceType` filters to `listAsyncJobs` and `queryAsyncJobResult`
  - Make `ApiCommandResourceType.fromString(...)` case-insensitive for resource-driven async job lookups
  - Add `ResourceIdSupport` to centralize resource UUID parsing, resource-type validation, and access checks for async job resource filters
  - Extend `AsyncJobDao` and `AsyncJobDaoImpl` so async jobs can be resolved by either job id or `(resource type, resource id)`
- 기능 영향도:
  - Allows operators to locate async jobs even when they only know the backing resource UUID and type, not the async job UUID
  - Makes resource-type matching more tolerant of casing differences in API clients
  - Preserves the earlier local `Record 003` behavior that allows `listAsyncJobs` to filter by `resourceType` alone without requiring `resourceId`
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `QueryManagerImpl` because this branch already carries the `Record 003` extension that permits `resourceType`-only async job listing
  - `ApiResponseHelper` merged cleanly after reconciling import drift and retaining the local `Site2SiteVpnManager` injection block
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `QueryManagerImpl` conflicted on `main` because Apache now expects `resourceType` and `resourceId` together for list filtering, while this branch intentionally already supports `resourceType` without `resourceId`
  - `ApiResponseHelper` conflicted on `main` in the import/injection region due unrelated local drift near the same hunk
- 해결 메모:
  - Kept the Apache `queryAsyncJobResult`, DAO lookup, case-insensitive resource-type parsing, and shared resource helper changes
  - Preserved the local `listAsyncJobs` type-only filter so this sync does not silently regress `Record 003`

### 기록 034 - remove unused console proxy command port config

- 로컬 브랜치: `main`
- 로컬 커밋: `f0f0218dcb`
- 소스 Apache 커밋:
  - `feb6076930` Remove unused config consoleproxy.cmd.port (#12807)
- 요약:
  - Remove the unused `ConsoleProxyCmdPort` config key from `ConsoleProxyManager`
  - Stop exposing `consoleproxy.cmd.port` through `ConsoleProxyManagerImpl.getConfigKeys()`
  - Delete stale `consoleproxy.cmd.port` rows during the `4.22.0.0 -> 4.22.1.0` schema upgrade
- 기능 영향도:
  - Removes an unused console proxy setting from the surfaced configuration set
  - Cleans obsolete configuration data during upgrade without changing active console proxy behavior
- 검증:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already re-ordered nearby upgrade statements for `backup_interval_type` removal and `vm_template.type` backfill
  - The resolved schema keeps the local statement order and adds only the `consoleproxy.cmd.port` cleanup delete
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `schema-42200to42210.sql` conflicted on `main` because Apache inserts the config cleanup next to statements this branch already moved and de-duplicated earlier
- 해결 메모:
  - Preserved the local upgrade order, avoided duplicating the existing `backups.backup_interval_type` drop, and inserted only the new configuration cleanup

### 기록 035 - 업데이트 password reset mail 템플릿 default value

- 로컬 브랜치: `main`
- 로컬 커밋: `21c8b313df`
- 소스 Apache 커밋:
  - `5013cf2af6` Fix user password reset mail template value (#12882)
- 요약:
  - Update the `user.password.reset.mail.template` upgrade SQL to the new `{{{resetLink}}}` format
  - Migrate only legacy template values that still use the old `http://{{{resetLink}}}` or `{{{domainUrl}}}{{{resetLink}}}` placeholders
  - Use `CONCAT_WS('\n', ...)` so the stored template matches the multiline string expected by the newer password reset flow
- 기능 영향도:
  - Aligns upgraded deployments with the current password reset mail rendering logic
  - Avoids leaving stale default template values that generate the wrong reset URL in notification emails
- 검증:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already carries later upgrade statements in the same tail region
  - The resolved schema keeps the existing `backup_interval_type` removal and `consoleproxy.cmd.port` cleanup, then appends only the password reset template update block
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `schema-42200to42210.sql` conflicted on `main` because Apache still includes an adjacent `backup_interval_type` drop that is already present in this branch
- 해결 메모:
  - Reused the existing schema tail order and inserted only the new template migration SQL to avoid duplicate DDL

### 기록 036 - 허용 서비스 오퍼링 조회 across cluster host tags

- 로컬 브랜치: `main`
- 로컬 커밋: `75874d825d`
- 소스 Apache 커밋:
  - `b5858029bb` Fix listing service offerings with different host tags (#12919)
- 요약:
  - Add `HostTagsDao.listByClusterId(...)` so service offering search can inspect all host tags defined on hosts inside a VM's current cluster
  - Introduce `allow.different.host.tags.offerings.for.vm.scale` and register it through `UserVmManagerImpl.getConfigKeys()`
  - When the new setting is enabled, extend scale-offering host-tag matching to include any tag found in the VM's current cluster instead of requiring the current offering's exact tag set
  - Surface `hosttags` and `storagetags` columns in the compute offering wizard when the offering payload includes those fields
- 기능 영향도:
  - Lets operators list compatible target offerings for VM scale even when the offering uses a different host-tag subset that is still valid within the VM's cluster
  - Keeps the previous strict host-tag behavior by default unless the new advanced setting is enabled
  - Makes host and storage tag differences visible in the UI during compute offering selection
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The change is limited to host-tag DAO/query plumbing, one new config key registration, two focused `QueryManagerImplTest` cases, and compute-offering wizard column rendering
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 037 - exclude group 스냅샷s from account 스냅샷 resource counts

- 로컬 브랜치: `main`
- 로컬 커밋: `97a11d6e18`
- 소스 Apache 커밋:
  - `7b467496cb` Do not include snapshots with Group type in snapshots resource count (#12945)
- 요약:
  - Exclude `Snapshot.Type.GROUP` entries from `CountSnapshotsByAccount`
  - Apply the exclusion only to snapshot resource counting, leaving the rest of snapshot DAO behavior untouched
- 기능 영향도:
  - Prevents group snapshots from inflating per-account snapshot resource counts
  - Aligns snapshot quota/accounting behavior with the expectation that grouped snapshots should not be counted like regular per-volume snapshots
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the `CountSnapshotsByAccount` search builder and `countSnapshotsForAccount(...)`
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 038 - avoid KVM domain 조회 for non-running VM cheCKS

- 로컬 브랜치: `main`
- 로컬 커밋: `78a25f2a85`
- 소스 Apache 커밋:
  - `273699cf56` kvm: fix wrong CheckVirtualMachineAnswer when vm does not exist (#12928)
- 요약:
  - Only call `domainLookupByName(...)` when the VM power state is `PowerOn`
  - Preserve the paused-domain special case for powered-on VMs, but avoid libvirt domain inspection for powered-off or missing VMs
  - Add focused wrapper tests that cover running, paused, powered-off, unknown-state, null-VNC, and libvirt-exception paths
- 기능 영향도:
  - Prevents `CheckVirtualMachineCommand` from returning the wrong answer path when the VM no longer exists in libvirt
  - Reduces false failures for non-running KVM VMs by skipping unnecessary domain lookup
  - Improves regression coverage for the wrapper's error handling and paused-VM behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the KVM check wrapper and its new unit test class
- Europa cherry-pick 상태:
  - `Pending cherry-pick on ablestack-europa`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 039 - rollback disk 스냅샷s on VM 스냅샷 failure

- 로컬 브랜치: `main`
- 로컬 커밋: `d1ebe5062b`
- 소스 Apache 커밋:
  - `d75acb6efc` Fix rollback disk snapshots on instance snapshot failure (#12949)
- 요약:
  - Add each created disk snapshot to the rollback list before invoking the snapshot strategy
  - Guard rollback cleanup against `null` `SnapshotInfo` and missing `SnapshotVO` rows
  - Keep the VM snapshot unit test aligned with the renamed rollback list parameter
- 기능 영향도:
  - Prevents partially created per-volume snapshots from being left behind when a later VM snapshot disk step fails
  - Makes rollback cleanup tolerant of partially persisted or already-removed snapshot metadata during failure handling
  - Reduces orphaned snapshot state in KVM VM snapshot error paths
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `51d1aa5bb6`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 040 - include 숨김 image-store refs when resolving Xen 스냅샷 chains

- 로컬 브랜치: `main`
- 로컬 커밋: `bb635f652f`
- 소스 Apache 커밋:
  - `2a60305792` Fix snapshot chaining on Xen (#12597)
- 요약:
  - Add a DAO method that lists snapshot-store refs by snapshot id, role, and a set of states
  - Update `DefaultSnapshotStrategy.getSnapshotImageStoreRef(...)` to consider both `Ready` and `Hidden` image-store refs
  - Align the unit test with the new DAO method and remove the redundant null-path stubbing
- 기능 영향도:
  - Preserves Xen incremental snapshot chain lookup even when a parent snapshot is hidden on secondary storage
  - Reduces the chance of losing the expected parent chain and falling back to an incorrect full backup path
  - Keeps snapshot image-store lookup limited to the target zone while widening the acceptable persisted states
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SnapshotDataStoreDaoImpl` because this branch already carried a local `idStateNeqSearch` builder for non-destroyed snapshot lookups
  - The resolved DAO keeps the local `idStateNeqSearch` behavior and adds Apache's `idEqRoleEqStateInSearch` path for `Ready`/`Hidden` image-store lookup
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `bae63ac800`
- 충돌 메모:
  - `SnapshotDataStoreDaoImpl` conflicted on `main` because Apache adds a new state-in search builder in the same initialization block where this branch already introduced `idStateNeqSearch`
- 해결 메모:
  - Preserved the local `idStateNeqSearch` initialization and added the Apache `idEqRoleEqStateInSearch` builder without changing the branch-local non-destroyed lookup behavior

### 기록 041 - pass 스냅샷 CPG on Primera online copy

- 로컬 브랜치: `main`
- 로컬 커밋: `e5e4e63261`
- 소스 Apache 커밋:
  - `8f3c6fad7a` set snapcpg config on copy (#12955)
- 요약:
  - Set `snapCpg` on Primera online copy parameters alongside the destination CPG
  - Leave the existing online copy behavior unchanged apart from propagating the configured snapshot CPG
- 기능 영향도:
  - Ensures Primera online copy operations inherit the configured snapshot CPG instead of relying only on the destination CPG
  - Reduces the risk of copy-time snapshot placement drifting from the storage policy expected by the Primera backend
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `parms.setSnapCPG(snapCpg)` line in `PrimeraAdapter`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `0a94c732c0`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 042 - 범위 제한 persistent network 조회 기준 zone

- 로컬 브랜치: `main`
- 로컬 커밋: `2b5d1dc0d4`
- 소스 Apache 커밋:
  - `b805766f4b` Fix Host setup when persistent networks exist (#12751)
- 요약:
  - Add the data center filter to `PersistentNetworkSearch` in `NetworkDaoImpl`
  - Align the search builder with the existing `getAllPersistentNetworksFromZone(...)` parameter binding
- 기능 영향도:
  - Prevents persistent network discovery for host setup from matching networks that share attributes across different zones
  - Reduces the risk of reusing or counting persistent networks outside the requested data center when broadcast URI values overlap
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `PersistentNetworkSearch.and(\"dc\", ...)` addition in `NetworkDaoImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `64c919e65c`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 043 - compare NSX NAT 삭제 service 기준 name

- 로컬 브랜치: `main`
- 로컬 커밋: `b078081e41`
- 소스 Apache 커밋:
  - `30dd234b00` fix: NsxResource.executeRequest DeleteNsxNatRuleCommand comparison bug (#12833)
- 요약:
  - Add `getNetworkServiceName()` to `DeleteNsxNatRuleCommand`
  - Compare NSX NAT delete service selection by service name instead of object identity
  - Add a focused test that verifies the Port Forwarding delete path reaches the expected NSX API call
- 기능 영향도:
  - Prevents NSX NAT rule deletion from missing the correct branch when the command carries an equivalent service object rather than the same enum instance
  - Improves reliability of Port Forwarding and Static NAT rule cleanup in NSX-backed networks
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the NAT delete command, `NsxResource`, and one focused `NsxResourceTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 7ad9fbc1f0`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 044 - 허용 established 및 related traffic in 라우팅d VR forward chain

- 로컬 브랜치: `main`
- 로컬 커밋: `166bb4304f`
- 소스 Apache 커밋:
  - `1fc4cb90bf` Routed VR: accept packets from related and established connections (#12986)
- 요약:
  - Add an nftables `ct state established,related accept` rule when creating `forward` chains in `CsNetfilter`
  - Leave the existing input/output ICMP allowance behavior unchanged
- 기능 영향도:
  - Prevents routed VR forward chains from dropping reply traffic that belongs to already established or related connections
  - Improves flow continuity for routed guest traffic without widening new-connection exposure
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a 2-line `CsNetfilter.py` change in the `forward` hook path
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 5681e66c42`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 045 - validate zone local-스토리지 enablement before creating file-based pools

- 로컬 브랜치: `main`
- 로컬 커밋: `3c639d46fd`
- 소스 Apache 커밋:
  - `d38c1f8d12` Fix error message while creating local storage pool (#12767)
- 요약:
  - Reuse `isLocalStorageEnabledForZone(...)` when creating local storage
  - Reject file-scheme storage pool creation early when local storage is disabled for the zone
  - Replace duplicated zone-level local-storage checks with the shared helper
- 기능 영향도:
  - Returns a clearer validation failure when a local/file-backed primary storage pool is requested in a zone where local storage is disabled
  - Prevents the create-pool flow from reaching a later, less accurate error path
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in `StorageManagerImpl` because this branch already imports `ArrayUtils` in the same block where Apache adds `BooleanUtils`
  - The resolved file keeps the branch-local imports and preserves Apache's `isLocalStorageEnabledForZone(...)` guard in the create-pool path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 6fadfd9913`
- 충돌 메모:
  - `StorageManagerImpl` conflicted on `main` only in the import section because the branch already had nearby local import additions
- 해결 메모:
  - Kept the existing `ArrayUtils` import and added Apache's `BooleanUtils` import so the shared helper and the local code both compile cleanly

### 기록 046 - enable default SystemVM 템플릿 registration on 4.20.2 -> 4.20.3 upgrade

- 로컬 브랜치: `main`
- 로컬 커밋: `3422f7d5da`
- 소스 Apache 커밋:
  - `e2497cfc4d` backport: default system vm template update implementation (#12935)
- 요약:
  - Remove the empty `updateSystemVmTemplates(...)` override from `Upgrade42020to42030`
  - Let the upgrade path inherit the shared default implementation from `DbUpgradeSystemVmTemplate`
- 기능 영향도:
  - Restores automatic SystemVM template lookup/registration during the 4.20.2.0 -> 4.20.3.0 upgrade path
  - Prevents the upgrade from silently skipping the default SystemVM template update logic for that release jump
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The branch already carried the interface-side default implementation, so the net change is the removal of the stale no-op override in `Upgrade42020to42030`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as a51d5eb639`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 047 - register new SystemVM 템플릿s for non-KVM hypervisors 및 amd64 arch

- 로컬 브랜치: `main`
- 로컬 커밋: `76f1194d8c`
- 소스 Apache 커밋:
  - `6f1aa96b4c` engine/schema: fix new systemvm template is not registered during upgrade if hypervisor is not KVM (#12952)
- 요약:
  - Assign `CPU.CPUArch.amd64` to non-KVM hypervisors in `SystemVmTemplateRegistration.hypervisorList`
  - Update the registration test so VMware template metadata is expected with `amd64` instead of a null/default arch assumption
- 기능 영향도:
  - Prevents upgrade-time SystemVM template registration from skipping VMware, XenServer, Hyper-V, LXC, and OVM3 entries because their architecture was previously unspecified
  - Makes the registration map deterministic for non-KVM hypervisors
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SystemVmTemplateRegistrationTest` because this branch already refactored the test to call `getMetadataTemplateDetails(...)` directly
  - The resolved test keeps the branch-local helper call and adopts Apache's explicit `amd64` expectation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as f0af890272 before backfilling local/main`
- 충돌 메모:
  - `SystemVmTemplateRegistrationTest` conflicted on `main` because the local branch had already changed the VMware metadata lookup helper call while Apache updates the expected arch in the same assertion block
- 해결 메모:
  - Preserved the branch-local helper-based test structure and updated the asserted VMware arch to `CPU.CPUArch.amd64`

### 기록 048 - add CloudStack user-agent headers to 템플릿 다운로드 requests

- 로컬 브랜치: `main`
- 로컬 커밋: `f051bdc876`
- 소스 Apache 커밋:
  - `4ebe3349b7` add user-agent header to template downloader request (#12791)
- 요약:
  - Introduce `HttpClientCloudStackUserAgent` as a shared CloudStack-branded user-agent string provider
  - Set the shared user-agent on HTTP and HEAD requests issued by template downloaders, direct downloads, URL validation helpers, and QCOW2 size probing
  - Reuse a common `UriUtils.USER_AGENT` constant for `HttpURLConnection`-based helper paths
- 기능 영향도:
  - Makes outbound template-download and remote-image probe traffic identify itself consistently to upstream HTTP servers
  - Reduces the chance of providers applying different behavior to anonymous Java HTTP clients during template validation, download, or virtual-size inspection flows
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to downloader/helper call sites plus the new shared user-agent utility class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 1f8ff5cbad after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 049 - apply nexthop static 라우팅s to PBR tables 및 interface ACL chains

- 로컬 브랜치: `main`
- 로컬 커밋: `994506d3ec`
- 소스 Apache 커밋:
  - `83f705ddc5` Static Routes with nexthop non-functional for private gateways (#12859)
- 요약:
  - Add `CsHelper.find_device_for_gateway(...)` to map a gateway IP to the matching router interface subnet
  - Update `CsStaticRoutes` so route add/delete operations touch both the main routing table and the matching interface-specific PBR table when a nexthop belongs to a private gateway subnet
  - Extend `CsAddress` firewall generation to emit the same inbound/outbound ACL chains for nexthop-based static routes as for legacy `ip_address`-based routes
- 기능 영향도:
  - Fixes VPC router traffic drops where static routes configured with a gateway/nexthop were installed only in the main routing table while policy-based routing uses interface-specific tables
  - Restores ACL and PREROUTING/FORWARD rule generation for nexthop-based static routes, so traffic can traverse private gateway paths consistently
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `CsAddress.py`, `CsHelper.py`, and `CsStaticRoutes.py`
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as eb48668e0a after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 050 - skip redundant NSX LB patch operations that trigger 404s

- 로컬 브랜치: `main`
- 로컬 커밋: `aaa8d95329`
- 소스 Apache 커밋:
  - `05c59630e0` fix: LB Creation avoid 404 API errors due to non-needed patches (#12835)
- 요약:
  - Check for existing NSX LB pools before patching and skip the update when the pool members are unchanged
  - Resolve monitor profile paths by direct lookup and create the monitor profile only when it is actually missing
  - Avoid patching an NSX virtual server when it already exists, and add focused regression tests for the skip/patch decision points
- 기능 영향도:
  - Prevents LB create/update flows from sending redundant NSX patch requests that can fail with `404 Not Found` when the target object is already in the desired state
  - Makes NSX LB provisioning more idempotent by distinguishing between genuinely missing objects and objects that already exist with the expected membership or monitor profile
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient.java` and `NsxApiClientTest.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as af6a324516 after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 051 - fetch all NSX paged list results instead of truncating at the first page

- 로컬 브랜치: `main`
- 로컬 커밋: `10751b2efb`
- 소스 Apache 커밋:
  - `e0fe953791` fix: NSX SDK list operations are pageable: the API returns a non-null and non-empty (#12834)
- 요약:
  - Introduce a reusable `PagedFetcher` helper that follows NSX cursor-based pagination and merges items across pages
  - Update `NsxApiClient` list retrieval paths to use complete paged results for sites, enforcement points, locale services, and policy-group members
  - Add focused unit tests that cover single-page, empty-cursor, multi-page, and null-first-page-item flows
- 기능 영향도:
  - Prevents NSX-backed operations from acting on incomplete inventories when the NSX API returns more than one page of results
  - Makes NSX group, locale-service, and enforcement-point lookups deterministic in environments with larger object counts
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient`, the new `PagedFetcher`, and its dedicated test class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 3604e72e77 after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 052 - add only missing PowerFlex MDMs when preparing the KVM SDC client

- 로컬 브랜치: `main`
- 로컬 커밋: `c3d54c8797`
- 소스 Apache 커밋:
  - `71bd26ff7c` PowerFlex/ScaleIO storage - the MDMs validation improvements (#12893)
- 요약:
  - Filter the storage-pool MDM list down to only the addresses that are not already present in the SDC configuration
  - Return a success path when all requested MDMs are already configured, instead of forcing a redundant add flow
  - Report the exact missing MDM addresses when registration still fails after an add attempt
- 기능 영향도:
  - Prevents KVM ScaleIO/PowerFlex pool preparation from misclassifying a partially preconfigured MDM set as fully ready or fully failed based on the first address alone
  - Makes repeated SDC preparation idempotent and easier to troubleshoot when only a subset of MDM endpoints is missing
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `ScaleIOStorageAdaptor.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as a0e88938be after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 053 - skip already-covered upgrade hops when the source version is not explicit in the graph

- 로컬 브랜치: `main`
- 로컬 커밋: `2ce5188d0c`
- 소스 Apache 커밋:
  - `4b7370a601` upgrade: skip the upgrade paths which are not needed (#12881)
- 요약:
  - Filter `DatabaseVersionHierarchy.getPath(...)` results so only upgrade steps with a version strictly newer than the source database version are returned
  - Make the `Usage Server` configuration group insert idempotent in `schema-42000to42010.sql`
  - Add a regression test that verifies `4.20.1.0 -> 4.20.3.0` resolves directly to the `4.20.2.0 -> 4.20.3.0` upgrader
- 기능 영향도:
  - Prevents upgrade planning from replaying obsolete path segments when the exact source version is not a direct node in the version hierarchy
  - Makes the early 4.20 schema path safer on reruns by avoiding duplicate configuration-group insert failures
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the upgrade path resolver, one schema SQL line, and one targeted regression test
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 8e73f1f762 after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 054 - 복원 management server id from cookies after SAML login

- 로컬 브랜치: `main`
- 로컬 커밋: `affd5335ca`
- 소스 Apache 커밋:
  - `d6c39772b2` Set management server id from cookies after saml login (#12858)
- 요약:
  - Add the `managementserverid` cookie to the SAML login response when the login payload carries a management server id
  - Restore that cookie into the UI store during the SAML re-entry path in `permission.js`
- 기능 영향도:
  - Prevents post-SAML UI/API flows from losing the management server affinity that normal login paths already preserve
  - Reduces the chance of follow-up authenticated requests missing the server-id hint immediately after SAML authentication
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `SAMLUtils.java` and `ui/src/permission.js`
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 1aabbb1777 after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 055 - avoid NAS 백업 provider crashes when no running KVM host is available

- 로컬 브랜치: `main`
- 로컬 커밋: `e2bbf6a31f`
- 소스 Apache 커밋:
  - `6ca6aa1c3f` Fix NPE in NASBackupProvider when no running KVM host is available (#12805)
- 요약:
  - Guard `deleteBackup(...)` so it fails with a descriptive runtime exception when no running KVM host can be found in the target zone
  - Short-circuit `syncBackupStorageStats(...)` when there are no repositories or no eligible running KVM host
  - Keep the branch-local `commons-collections4` import while adopting Apache's host-null protections
- 기능 영향도:
  - Prevents the NAS backup sync background task from crashing with a null dereference during host outages or agent reconnect windows
  - Makes forced backup deletion failures explicit when there is no execution host available instead of failing later with an opaque NPE
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in the `CollectionUtils` import because this branch already uses `org.apache.commons.collections4.CollectionUtils`
  - The resolved file preserves Apache's null-host handling and repository-empty early return while keeping the local collections4 import
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as e85e854cd1 after history-doc conflict resolution`
- 충돌 메모:
  - `NASBackupProvider` conflicted on `main` only in the import block due to the branch's existing `commons-collections4` migration
- 해결 메모:
  - Kept the local `org.apache.commons.collections4.CollectionUtils` import and applied the Apache runtime guards unchanged

### 기록 056 - keep Public networks out of multi-CIDR 정리 side effects

- 로컬 브랜치: `main`
- 로컬 커밋: `e8362ab92d`
- 소스 Apache 커밋:
  - `ae455ee193` VPC restart cleanup for Public networks with multi-CIDR data (#12622)
- 요약:
  - Skip `addCidrAndGatewayForIpv4/Ipv6(...)` and matching remove flows for `TrafficType.Public` networks in `ConfigurationManagerImpl`
  - Sanitize legacy Public-network addressing fields in `schema-42200to42210.sql` by nulling network-level CIDR/gateway columns
- 기능 영향도:
  - Prevents Public networks from accumulating comma-separated CIDR and gateway state that later breaks VPC restart cleanup with malformed CIDR parsing
  - Leaves Public network addressing sourced from VLAN/IP-range state rather than duplicating it into network-level fields
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in `schema-42200to42210.sql` because this branch already drops `backup_interval_type` in the same migration tail
  - The resolved migration keeps both the local backup cleanup and the Apache Public-network sanitization SQL
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as f93d0dde9b after history-doc conflict resolution`
- 충돌 메모:
  - `schema-42200to42210.sql` conflicted on `main` because prior local migration work already rewrote the same tail section
- 해결 메모:
  - Preserved the existing `backup_interval_type` drop and inserted only the Apache Public-network cleanup statements beside it

### 기록 057 - propagate forced 삭제 flags across management servers

- 로컬 브랜치: `main`
- 로컬 커밋: `a4baa35318`
- 소스 Apache 커밋:
  - `160876c6d7` Fix: API Thread held forever during force deleting across MS (#12968)
- 요약:
  - Extend `PropagateResourceEventCommand` with `forced` and `forceDeleteStorage` flags
  - Pass those flags through `ResourceManagerImpl.deleteHost(...)`, cross-MS propagation, and peer-side `executeUserRequest(...)`
  - Return peer-side runtime failure details as an explicit failed answer instead of letting the caller wait indefinitely
  - Add focused tests that verify delete-host overloads preserve the force flags
- 기능 영향도:
  - Prevents force-delete host operations routed through another management server from silently losing the force semantics
  - Turns peer-side propagated failures into deterministic API errors instead of threads hanging while waiting for a result that never arrives
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to resource event propagation classes plus focused resource-manager tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as d1abaedd51 after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 058 - retry KVM incremental 스냅샷 rebase after transient image loCKS

- 로컬 브랜치: `main`
- 로컬 커밋: `eac20a6180`
- 소스 Apache 커밋:
  - `7c7b2ae75d` Fix KVM incremental volume snapshot creation (#12666)
- 요약:
  - Add `incremental.snapshot.retry.rebase.wait` to agent properties with a default 60-second backoff
  - Retry the QCOW2 rebase once when the initial rebase fails specifically because another process still holds the image lock
  - Preserve immediate failure behavior for non-lock-related rebase errors
- 기능 영향도:
  - Reduces transient KVM incremental snapshot failures caused by libvirt/qemu still holding the image lock immediately after snapshot operations
  - Keeps other rebase failures explicit instead of masking them behind generic retry behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to agent property definitions and `KVMStorageProcessor`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as ec5184fc2d after history-doc conflict resolution`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 059 - expose richer VM start failure details under an explicit config gate

- 로컬 브랜치: `main`
- 로컬 커밋: `d5c5ff9455`
- 소스 Apache 커밋:
  - `68030df10b` VM start error handling improvements and config to expose error to users (#12894)
- 요약:
  - Add global config `expose.errors.to.user` and use it when deciding whether non-admin users may see detailed VM start errors
  - Track the last known start failure reason through deployment retries so final errors can surface a meaningful cause
  - Preserve the Europa-specific VirtualRouter network-unavailable message, but route its detail exposure through the same config gate
- 기능 영향도:
  - Gives operators and optionally end users clearer VM start failure messages instead of a generic “see management server log” response
  - Makes repeated deployment retries easier to diagnose by surfacing the last concrete failure reason when capacity or resource allocation ultimately fails
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` only in `VirtualMachineManagerImpl.start(...)` because this branch already had a custom VirtualRouter-specific error message
  - The resolved code keeps the local VirtualRouter wording but gates detailed exposure through Apache's new `canExposeError(...)` logic and config key
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as bcc7a07225 after history-doc conflict resolution`
- 충돌 메모:
  - `VirtualMachineManagerImpl` conflicted on `main` where the branch already customized the VirtualRouter resource-unavailable error path
- 해결 메모:
  - Kept the local `The Network for VM ... is unavailable` message and switched its detail exposure to use Apache's `canExposeError(...)` policy

### 기록 060 - expose 및 validate HAProxy idle timeout through load balancer orchestration

- 로컬 브랜치: `main`
- 로컬 커밋: `32d8f85186`
- 소스 Apache 커밋:
  - `6e810989b6` HAProxy Configuration: network.loadbalancer.haproxy.idle.timeout (#12586)
- 요약:
  - Add `network.loadbalancer.haproxy.idle.timeout` as a configurable orchestration setting and propagate it through load balancer command construction
  - Teach `LoadBalancerConfigCommand`/`HAProxyConfigurator` to render `timeout client` and `timeout server` when the idle timeout is positive, and to blank them when explicitly set to `0`
  - Extend HAProxy health checks and tests so the new idle-timeout behavior is validated end to end
- 기능 영향도:
  - Gives operators a supported knob for HAProxy idle timeout without patching systemvm templates or generated configs by hand
  - Keeps runtime and health-check expectations aligned so VR load balancer checks do not falsely fail when the timeout is customized
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Europa cherry-pick required manual conflict resolution in `LoadBalancerConfigCommand` and `HAProxyConfigurator` because this branch already carries `lbConnectTimeout`, `lbClientTimeout`, and `lbServerTimeout` command fields
  - Cached diff spans HAProxy command/config generation, orchestration config plumbing, tests, and the systemvm health check script
  - Maven/UI/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as a8c29e8606 after history-doc conflict resolution`
- 충돌 메모:
  - `LoadBalancerConfigCommand` and `HAProxyConfigurator` conflicted on `ablestack-europa` where local HAProxy timeout customization already occupied the same command/config surfaces
- 해결 메모:
  - Preserved the local `lbConnectTimeout` / `lbClientTimeout` / `lbServerTimeout` fields and layered Apache's `idleTimeout` as a client/server override when present

### 기록 061 - honor 백업 command timeout for NAS 생성 및 복원 flows

- 로컬 브랜치: `main`
- 로컬 커밋: `5803119a11`
- 소스 Apache 커밋:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- 요약:
  - Use `command.wait` when provided, otherwise fall back to `commands.timeout`, for NAS backup create/restore KVM wrappers
  - Apply the resolved timeout to `rsync`, `qemu-img`, and piped backup commands instead of relying on mixed default process timeouts
  - Preserve the branch-local RBD restore helper flow while aligning its timeout handling with Apache's millisecond-based execution model
- 기능 영향도:
  - Prevents long-running NAS backup create/restore operations from timing out too early or ignoring operator-supplied wait values
  - Makes backup command execution more predictable across create and restore paths by using the same timeout source consistently
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `LibvirtRestoreBackupCommandWrapper` because this branch already refactored the RBD restore helper structure
  - The resolved code keeps the local RBD helper layout and applies Apache's timeout fallback logic to both restore and take-backup paths
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as c37ef5e0f1 after history-doc conflict resolution`
- 충돌 메모:
  - `LibvirtRestoreBackupCommandWrapper` conflicted on `main` where the branch already carried a different restore-helper shape around the same timeout-sensitive logic
- 해결 메모:
  - Preserved the local RBD restore helper path and merged Apache's `command.wait -> commands.timeout` fallback plus timeout-aware script execution

### 기록 062 - avoid forcing custom 서비스 오퍼링 changes on VM 스냅샷 revert

- 로컬 브랜치: `main`
- 로컬 커밋: `c778a082d4`
- 소스 Apache 커밋:
  - `b22dbbe2d7` Fix Revert Instance to Snapshot with custom service offering (#12885)
- 요약:
  - Split VM snapshot revert service-offering handling into a boolean "needs change" decision and only perform an upgrade when the snapshot actually differs from the current VM configuration
  - Compare dynamic compute offering CPU, memory, and speed against values stored in VM snapshot details so revert paths do not trigger unnecessary custom offering changes
  - Use snapshot detail values, not live VM detail values, when a service offering change is required during revert
- 기능 영향도:
  - Prevents revert-to-snapshot from forcing an unnecessary service offering change when the current VM already matches the snapshot's custom offering
  - Keeps dynamic custom offering reverts aligned with the snapshot's captured CPU and memory settings instead of whatever the VM happens to expose at revert time
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` because this branch still carried the older inline upgrade flow
  - The resolved code keeps the Apache boolean gate, adds snapshot-detail map extraction, and only upgrades the VM offering inside the revert transaction when the snapshot truly requires it
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as bf875bec57 after history-doc conflict resolution`
- 충돌 메모:
  - `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` conflicted on `main` where the pre-existing logic upgraded the VM offering directly instead of deciding first whether a change was needed
- 해결 메모:
  - Replaced the older direct-upgrade path with Apache's conditional change flow and preserved the branch-local DAO and test wiring already present in these classes

### 기록 063 - 지원 SharedMountPoint 볼륨 cheCKS 중 importVM preflight

- 로컬 브랜치: `main`
- 로컬 커밋: `dae6777b00`
- 소스 Apache 커밋:
  - `b0b3dc91f5` fix: support SharedMountPoint volume checks for importVm (#12946)
- 요약:
  - Extend the KVM `CheckVolumeCommand` wrapper so SharedMountPoint pools are treated as supported when validating volumes for import flows
  - Keep the existing filesystem and NFS handling unchanged while broadening the accepted pool-type list
- 기능 영향도:
  - Prevents import-VM preflight checks from rejecting SharedMountPoint-backed volumes even though the hypervisor path can handle them
  - Reduces false negatives when validating KVM import candidates stored on shared mount primary storage
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the supported pool-type list in `LibvirtCheckVolumeCommandWrapper`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 0f43c06318 after code and history-doc conflict resolution`
- 충돌 메모:
  - `LibvirtCheckVolumeCommandWrapper` conflicted on `ablestack-europa` because the branch already carried `RBD` in the same supported pool-type list
- 해결 메모:
  - Preserved Europa's existing `RBD` support and added Apache's `SharedMountPoint` support alongside it

### 기록 064 - 지원 SharedMountPoint 스토리지 discovery for KVM import 및 unmanage

- 로컬 브랜치: `main`
- 로컬 커밋: `920d6aa0ff`
- 소스 Apache 커밋:
  - `b1bc5380a2` fix: support SharedMountPoint for KVM volume import and unmanage (#12956)
- 요약:
  - Add SharedMountPoint to the supported KVM storage-pool types exposed by the import/unmanage API contract
  - Align the KVM volume listing wrapper's qemu-img-compatible storage-pool list with the same SharedMountPoint support
- 기능 영향도:
  - Lets KVM import and unmanage flows enumerate volumes on SharedMountPoint pools instead of excluding them as unsupported
  - Keeps API-level validation and hypervisor-side storage discovery consistent for the same storage backend
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff updates one API interface constant and one KVM wrapper constant
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as fee04fc506 after code and history-doc conflict resolution`
- 충돌 메모:
  - `LibvirtGetVolumesOnStorageCommandWrapper` conflicted on `ablestack-europa` because the branch already carried the same storage-pool types in a different local list layout
- 해결 메모:
  - Kept Europa's existing `RBD` and `SharedMountPoint` support while accepting the Apache alignment for the API and hypervisor-side constants

### 기록 065 - replace GROUP_CONCAT 백업 볼륨 serialization 및 JSON aggregation

- 로컬 브랜치: `main`
- 로컬 커밋: `4d2401d7e7`
- 소스 Apache 커밋:
  - `4ba4bd33c3` replace GROUP_CONCAT with JSON_ARRAYAGG to avoid errors like Row 19 was cut by GROUP_CONCAT (#12777)
- 요약:
  - Rewrite upgrade SQL that backfills backup volume metadata so it uses `JSON_ARRAYAGG(JSON_OBJECT(...))` instead of string-built `GROUP_CONCAT(...)`
  - Keep the same backup volume payload fields while avoiding truncation-prone string concatenation in large-volume cases
- 기능 영향도:
  - Prevents upgrade-time backup metadata generation from silently truncating long volume lists under MySQL `GROUP_CONCAT` limits
  - Produces structurally valid JSON arrays for `backups.backed_volumes` and `vm_instance.backup_volumes` even when many disks are present
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the SQL migration logic in `schema-42010to42100.sql`
  - Database migration execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 6f1bd5d2f8 after history-doc auto-merge`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 066 - 개선 KVM GPU domain parsing 및 지원 Display controller class

- 로컬 브랜치: `main`
- 로컬 커밋: `a2baf8a85b`
- 소스 Apache 커밋:
  - `416679fae1` Fix domain parsing for GPU & add Display controller in the supported PCI class (#12981)
- 요약:
  - Tighten KVM GPU domain parsing in `LibvirtGpuDef` so discovery handles vendor output more reliably
  - Extend `gpudiscovery.sh` to treat Display controller PCI class entries as GPU-capable devices in addition to the previously supported classes
  - Add focused unit coverage for the updated GPU definition parsing behavior
- 기능 영향도:
  - Improves GPU discovery accuracy on hosts where PCI domain values or `lspci` output formatting previously caused parser failures
  - Enables detection of accelerator cards exposed through the Display controller class, including newer AMD Instinct-style hardware
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff spans the GPU parser, the KVM discovery script, and new `LibvirtGpuDefTest` coverage
  - Maven-based Java test execution and script-level runtime validation have not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as f77bea5384 after history-doc auto-merge`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 067 - avoid custom offering NPEs 중 unmanaged 및 external VM import

- 로컬 브랜치: `main`
- 로컬 커밋: `6683ae7a27`
- 소스 Apache 커밋:
  - `2416db2a44` Fix NPE on external/unmanaged instance import using custom offerings (#12884)
- 요약:
  - Move unmanaged/external KVM import CPU and memory reservation checks into dedicated helper methods that can read values either from the offering or from runtime/import details when the offering is dynamic
  - Add volume reservation helper coverage for external KVM import and wire the reservation lifecycle so conversions/imports close all temporary reservations safely
  - Extend `UnmanagedVMsManagerImplTest` with focused checks for dynamic offering detail parsing and invalid integer detail handling
- 기능 영향도:
  - Prevents unmanaged or external VM import from dereferencing null CPU/memory values when custom offerings rely on runtime/detail-provided sizing
  - Fails earlier and more cleanly when required import detail values are missing or malformed, instead of surfacing a later null-pointer failure
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` because this branch already carries VMware import task tracking, extra-param validation, and adjacent import test coverage
  - The resolved code keeps the branch-local VMware import task flow and extra-param tests while layering Apache's reservation helpers and new custom-offering regression tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as fdfa6bdde5 after history-doc auto-merge`
- 충돌 메모:
  - `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` conflicted on `main` where local import extensions and nearby tests occupied the same import-resource management sections
- 해결 메모:
  - Preserved local VMware import task bookkeeping and merged Apache's null-safe reservation helpers plus detail-parsing tests into the existing import flow

### 기록 068 - 수정 PowerFlex 4.x VM 스냅샷 take/revert handling

- 로컬 브랜치: `main`
- 로컬 커밋: `fa0bb99c76`
- 소스 Apache 커밋:
  - `131ea9f7ac` Fix PowerFlex 4.x issues with take & revert instance snapshots (#12880)
- 요약:
  - Adjust ScaleIO/PowerFlex VM snapshot strategy handling so multi-volume snapshot state updates and revert flows follow the newer PowerFlex 4.x expectations
  - Update the ScaleIO gateway client logic to vary overwrite behavior based on PowerFlex version-specific API semantics
- 기능 영향도:
  - Fixes take/revert VM snapshot behavior for PowerFlex 4.x environments that would otherwise mis-handle multi-volume state updates or use the wrong overwrite semantics
  - Improves compatibility across older and newer PowerFlex API variants without changing unrelated snapshot behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff touches `ScaleIOVMSnapshotStrategy` and `ScaleIOGatewayClientImpl` only
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 3962871697 after history-doc auto-merge`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 069 - 허용 creating a 볼륨 directly on a selected 스토리지 풀

- 로컬 브랜치: `main`
- 로컬 커밋: `87af436ce2`
- 소스 Apache 커밋:
  - `df7ff97271` Create volume on a specified storage pool (#12966)
- 요약:
  - Extend `CreateVolumeCmd` and `VolumeApiServiceImpl` so callers can optionally target a specific storage pool when creating a volume
  - Surface the new pool-selection control in the UI create-volume flow and add the matching user-facing text
- 기능 영향도:
  - Lets operators place a newly created volume on an explicitly chosen storage pool instead of relying entirely on normal planner selection
  - Improves operational control for storage troubleshooting, migration prep, or targeted placement workflows from both API and UI
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff spans the user API command, backend create-volume service path, and `CreateVolume.vue` plus locale text
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as 7562c347d1 after history-doc auto-merge`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 070 - align GitHub Actions checkout step on v6

- 로컬 브랜치: `main`
- 로컬 커밋: `01d5026710`
- 소스 Apache 커밋:
  - `6bcbb008b4` Bump `actions/checkout` to `v6` (#12164)
- 요약:
  - Update GitHub Actions workflows so checkout steps use `actions/checkout@v6`
  - Normalize the same checkout version across the workflow set already carried by this branch
- 기능 영향도:
  - Keeps CI workflow dependencies aligned with the newer checkout action release without changing product runtime behavior
  - Reduces maintenance drift between upstream workflow baselines and this fork's broader workflow matrix
- 검증:
  - Apache cherry-pick on `main` broadened beyond the single upstream workflow because this branch already carries additional workflow files using the same checkout action pin
  - The staged diff only touches `.github/workflows/*.yml`
  - GitHub Actions execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `Applied on ablestack-europa as ec243bba23 after resolving a branch-local workflow/path collision during cherry-pick`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - Applied the same checkout version normalization across the branch-local workflow set rather than limiting the change to upstream's single-file footprint
  - On `ablestack-europa`, preserved the branch-local desktop-service `works.yml` content when a mis-targeted cherry-pick conflict surfaced, and only kept the intended workflow checkout-version updates

### 기록 071 - avoid unnecessary service-offering changes 중 VM 스냅샷 revert

- 로컬 브랜치: `main`
- 로컬 커밋: `8b9a3455a9`
- 소스 Apache 커밋:
  - `b22dbbe2d7` Fix Revert Instance to Snapshot with custom service offering (#12885)
- 요약:
  - Split VM-snapshot revert validation so running instances reject disk-only snapshot revert and stopped instances reject disk-and-memory revert with clearer state-specific messages
  - Refactor service-offering revert logic into `userVmServiceOfferingNeedsChange(...)` so dynamic offerings only trigger a revert-time offering change when CPU, memory, or speed actually differ from the snapshot payload
  - Extend `VMSnapshotManagerTest` with explicit coverage for matching and non-matching dynamic offering details
- 기능 영향도:
  - Avoids unnecessary service-offering update attempts during snapshot revert when a dynamic offering still resolves to the same sizing that the instance already uses
  - Produces more accurate revert validation for state/type combinations, reducing confusing revert failures around custom offerings and memory snapshots
- 검증:
  - Attempting the Apache cherry-pick on `main` showed no remaining code delta after the earlier snapshot revert/custom-offering work already in this branch
  - This local commit therefore records the satisfied upstream state in the history document and backfills the previous record's final SHA
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8d9e5cc095`
- 충돌 메모:
  - `N/A`
- 해결 메모:
  - No additional code merge was required because the current branch state already satisfied the upstream revert/offering behavior

### 기록 072 - 지원 Linstor primary 스토리지 in NAS 백업 복원 flows

- 로컬 브랜치: `main`
- 로컬 커밋: `1fbd528784`
- 소스 Apache 커밋:
  - `03de62bf38` Support Linstor Primary Storage for NAS BnR (#12796)
- 요약:
  - Extend NAS backup restore path building so Linstor-backed volumes use the expected `/dev/drbd/by-res/cs-<uuid>/0` style device path while existing pool types keep their current path conventions
  - Carry restore volume sizes through `RestoreBackupCommand` and use them in the KVM restore wrapper when a Linstor target volume must be created before `qemu-img convert`
  - Update the KVM restore wrapper, script, and focused tests so block-device restore works for both RBD and Linstor while filesystem-backed restores still honor the branch-local timeout handling
- 기능 영향도:
  - Allows NAS backup restore to work against Linstor primary storage instead of assuming only filesystem or RBD-backed volume layouts
  - Preserves existing timeout-controlled restore behavior for non-block storage while adding the extra size/connect steps Linstor requires
- 검증:
  - Apache cherry-pick required a manual conflict resolution on `main` in `LibvirtRestoreBackupCommandWrapper` because this branch already carried NAS timeout handling and earlier restore-path adjustments in the same helper methods
  - The resolved code keeps the branch-local millisecond timeout flow and merges Apache's Linstor block-device handling, restore-size propagation, and updated wrapper tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8c47f676a8`
- 충돌 메모:
  - `LibvirtRestoreBackupCommandWrapper` conflicted where Apache's Linstor support overlapped the branch-local NAS timeout and restore helper changes
- 해결 메모:
  - Kept the branch-local timeout-aware `rsync` and `QemuImg` invocation path, then layered Apache's Linstor-specific device-path, connect, create-target, and raw-attach handling on top

### 기록 073 - 허용 import 및 unmanage of backing-file 볼륨s behind a config gate

- 로컬 브랜치: `main`
- 로컬 커밋: `321effd42c`
- 소스 Apache 커밋:
  - `e93ae1a4f4` New config key "allow.import.volume.with.backing.file" to skip volume backing (#12809)
- 요약:
  - Make `VolumeImportUnmanageService` configurable and add the global advanced setting `allow.import.volume.with.backing.file`
  - Gate backing-file rejection in both volume import/unmanage and unmanaged VM import paths behind the new setting instead of rejecting such volumes unconditionally
  - Expose the new config key from `VolumeImportUnmanageManagerImpl` so the behavior can be toggled without code changes
- 기능 영향도:
  - Gives operators a controlled way to import or unmanage QCOW2 volumes that still reference a backing file when their environment explicitly allows that workflow
  - Preserves the safer default behavior by keeping the check enabled unless the new global setting is turned on
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the service/config surface and the two backing-file validation call sites
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `4227ac97a0`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 074 - harden KVM direct-다운로드 URL handling

- 로컬 브랜치: `main`
- 로컬 커밋: `1a0561f603`
- 소스 Apache 커밋:
  - `0edd577f4b` Fix: KVM Direct Download URL injection
- 요약:
  - Tighten direct-download path handling so generated URL/location strings no longer rely on unsafe concatenation patterns that could be abused by crafted input
  - Keep the fix scoped to the KVM direct-download implementations for standard, metalink, and NFS-backed flows
- 기능 영향도:
  - Reduces the risk of malformed or attacker-controlled download location input influencing direct-download execution paths on KVM
  - Leaves normal template/image direct-download behavior unchanged for valid inputs
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the three direct-download implementation classes
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `939a1f5f1f`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 075 - 유지 camelCase `domainId` handling in login/auth flows

- 로컬 브랜치: `main`
- 로컬 커밋: `7ed9e5f573`
- 소스 Apache 커밋:
  - `56dc11980f` test_accounts.py failure fix - keep the camelCase parameter "domainId" (#12689)
- 요약:
  - Add `ApiServerService.getDomainId(...)` and implement the fallback in `ApiServer` so login/auth flows can read either `domainid` or camelCase `domainId`
  - Update both default login and OAuth login authenticators to use the shared helper instead of reading only the legacy parameter key directly
  - Carry the matching OAuth command test update with the API/auth changes
- 기능 영향도:
  - Prevents login failures for clients or tests that still submit the camelCase `domainId` parameter name
  - Centralizes the parameter fallback so auth entrypoints behave consistently instead of each flow drifting independently
- 검증:
  - Apache cherry-pick required a manual conflict resolution on `main` in `ApiServerService` because this branch already exposes an additional service method on the same interface
  - The resolved code keeps the branch-local interface method and layers Apache's shared `getDomainId(...)` helper plus auth-call-site updates alongside it
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `7d62faab7e`
- 충돌 메모:
  - `ApiServerService` conflicted where Apache added `getDomainId(...)` and the local branch had already added `isPostRequestsAndTimestampsEnforced()`
- 해결 메모:
  - Kept both interface methods and applied Apache's camelCase domain-id fallback everywhere else unchanged

### 기록 076 - add the GitHub Actions ecosystem to Dependabot

- 로컬 브랜치: `main`
- 로컬 커밋: `177ee3b036`
- 소스 Apache 커밋:
  - `cf9bda2050` [CI] Add github-actions ecosystem to Dependabot (#12823)
- 요약:
  - Extend `.github/dependabot.yml` so Dependabot also tracks workflow action versions in the GitHub Actions ecosystem
  - Keep the existing dependency-update structure and intervals intact while adding the extra ecosystem block
- 기능 영향도:
  - Improves CI maintenance coverage by letting Dependabot flag outdated GitHub Action references alongside the existing dependency sources
  - Does not affect product runtime behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.github/dependabot.yml` only
  - GitHub Actions execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `819521ce4e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 077 - refresh codespell 설정 및 hook versions

- 로컬 브랜치: `main`
- 로컬 커밋: `cec3def757`
- 소스 Apache 커밋:
  - `5d61ba3538` [CI] Create `.codespellrc`; upgrade codespell hook; fix typos (#12824)
- 요약:
  - Add a repository-level `.codespellrc` and update the codespell hook configuration in `.pre-commit-config.yaml`
  - Carry the small typo fixes that align the codebase and docs with the refreshed spelling checks
- 기능 영향도:
  - Improves repository linting hygiene and reduces repeated false positives or manual local overrides for spelling checks
  - Does not change product runtime behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff spans `.codespellrc`, `.pre-commit-config.yaml`, and a handful of typo-only source/doc updates
  - Pre-commit execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f38d48b590`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 078 - remove the broken ViserJS attribution link from the UI README

- 로컬 브랜치: `main`
- 로컬 커밋: `c424b9ad2c`
- 소스 Apache 커밋:
  - `9cc6c09b9e` Remove broken ViserJS attribution link from UI README (#12724)
- 요약:
  - Remove the stale/broken ViserJS attribution link from `ui/README.md`
- 기능 영향도:
  - Improves repository documentation accuracy without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `ui/README.md` only
  - Documentation link verification has not been run separately in this environment
- Europa cherry-pick 상태:
  - `40255d1472`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 079 - add code owners for the NSX network elements plugin

- 로컬 브랜치: `main`
- 로컬 커밋: `95024da846`
- 소스 Apache 커밋:
  - `b744824f65` Add code owners for nsx network elements plugin (#12838)
- 요약:
  - Add the NSX network elements plugin paths to `.github/CODEOWNERS`
- 기능 영향도:
  - Improves repository ownership and review routing metadata without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.github/CODEOWNERS` only
  - CODEOWNERS validation has not been run separately in this environment
- Europa cherry-pick 상태:
  - `f703098925`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 080 - clarify isolation-method descriptions for physical network creation

- 로컬 브랜치: `main`
- 로컬 커밋: `315c86c260`
- 소스 Apache 커밋:
  - `faaf7669c5` Update isolation methods description for physical network (#12759)
- 요약:
  - Refresh the isolation-method description text in `CreatePhysicalNetworkCmd` so the API help better reflects the supported physical-network modes
- 기능 영향도:
  - Improves admin/API documentation clarity without changing runtime behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `CreatePhysicalNetworkCmd` only
  - API doc generation has not been run in this environment by request
- Europa cherry-pick 상태:
  - `c41a3f6d59`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 081 - avoid duplicate resource count increments 중 KVM VM import from disk

- 로컬 브랜치: `main`
- 로컬 커밋: `a8996af5f3`
- 소스 Apache 커밋:
  - `497266270b` Cleanup imported VM from disk on failure due to volume allocation + prevent duplicate volume and primary storage increment on import
- 요약:
  - Extend `allocateRawVolume(...)` with an `incrementResourceCount` switch so import flows can allocate temporary/imported volumes without double-counting volume or primary-storage usage
  - Update KVM VM import-from-disk and unmanaged external import paths to pass `false` for those pre-created volumes while keeping normal VM allocation paths on `true`
  - Preserve the local branch's naming, device-id, and `EXTERNAL` image-format handling while adding cleanup on allocation-time `ResourceAllocationException`
- 기능 영향도:
  - Prevents imported VMs from over-incrementing volume or primary storage resource counts during staged disk allocation
  - Improves failure cleanup when a resource-allocation check trips mid-import, reducing leaked partially imported VM state
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `VirtualMachineManagerImpl` and `UnmanagedVMsManagerImpl` because this branch already carries import-flow extensions, custom device naming, and additional template-format handling
  - The resolved code keeps the local import behavior and applies Apache's resource-count guard only to the affected import allocation paths
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `c8f413e09e`
- 충돌 메모:
  - `VirtualMachineManagerImpl` conflicted where Apache added the new `incrementResourceCount` flag and the local branch had already customized data disk naming/device-id handling and `EXTERNAL` format root-volume skipping
  - `UnmanagedVMsManagerImpl` conflicted where Apache's import cleanup adjustments overlapped the branch-local unmanaged/external KVM import extensions
- 해결 메모:
  - Kept the branch-local import flow structure and device naming, added the new boolean flag with `true` for normal allocations and `false` for import-only allocations, and preserved cleanup on allocation failures

### 기록 082 - refresh MinIO canned policy membership when 버킷s are removed

- 로컬 브랜치: `main`
- 로컬 커밋: `3e13406450`
- 소스 Apache 커밋:
  - `7703fdacab` [minio] Handle user's canned policy when a bucket is deleted
- 요약:
  - Add `accountId` to `BucketTO` so bucket deletion has enough context to rebuild the owning account's canned policy
  - Refactor MinIO policy generation into a shared helper and reuse it on both create and delete so bucket membership stays accurate
  - Update the MinIO driver test to cover the new delete-time policy refresh path
- 기능 영향도:
  - Prevents deleted buckets from lingering in the account's MinIO canned policy and avoids stale access rules after bucket removal
  - Keeps create and delete policy management behavior aligned instead of drifting between separate code paths
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to `BucketTO`, the MinIO object-store driver, and its focused test
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `ecfa5d8ea9`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 083 - transition expunging VMs to error when expunge fails

- 로컬 브랜치: `main`
- 로컬 커밋: `d27f93ae8c`
- 소스 Apache 커밋:
  - `bce55945ec` Mark VMs in error state when expunge fails during destroy operation (#12749)
- 요약:
  - Add the `Expunging -> Error` VM state transition for `OperationFailedToError` and use it when expunge fails during destroy
  - Capture external-volume lookup support in `VolumeDao` via `findByExternalUuid(...)`
  - Extend `UserVmManagerImplTest` with focused coverage for the new `transitionExpungingToError(...)` helper behavior
- 기능 영향도:
  - Prevents a failed expunge from leaving user VMs stuck in `Expunging` without a recoverable/error signal
  - Gives external-plugin flows a direct DAO lookup by external UUID without changing existing volume lookups
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `UserVmManagerImplTest` because the local branch already had a large block of adjacent VM configuration and limit-validation tests at the same file tail
  - The resolved test file keeps the local coverage and appends Apache's expunge-to-error tests without dropping either set
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `a5a586e631`
- 충돌 메모:
  - `UserVmManagerImplTest` conflicted where Apache appended new expunge-failure tests and the local branch had already grown an unrelated block of trailing tests
- 해결 메모:
  - Preserved the local test block and added Apache's new transition tests after it, while leaving the service/DAO changes themselves unchanged

### 기록 084 - align 스냅샷 datastore CI 수정es 및 current 숨김-ref search builders

- 로컬 브랜치: `main`
- 로컬 커밋: `5c10b983e7`
- 소스 Apache 커밋:
  - `3b42fbf3b2` Fixing CI failures (#12789)
- 요약:
  - Switch the snapshot datastore search builder used by non-destroyed snapshot lookups from a single-state exclusion to a `NOTIN` builder that also fits the hidden-state filtering path
  - Carry the secondary-storage smoke test polling fix so SSVM readiness waits are more tolerant and less timing-sensitive
- 기능 영향도:
  - Keeps snapshot-store lookups consistent with the newer hidden-ref handling already present in this branch
  - Reduces flaky SSVM readiness failures in the smoke test flow without changing product runtime behavior
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `SnapshotDataStoreDaoImpl` because this branch already carries the hidden-ref `state IN` search builder used by Xen snapshot chaining fixes
  - The resolved code keeps the local `idEqRoleEqStateInSearch` builder and replaces the old single-state exclusion search with Apache's `NOTIN` variant
  - Marvin/integration tests have not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `2e2600d5fc`
- 충돌 메모:
  - `SnapshotDataStoreDaoImpl` conflicted where Apache renamed the non-destroyed-state search builder and the local branch had already added a second builder for hidden snapshot refs
- 해결 메모:
  - Retained both search use-cases by keeping the local hidden-ref builder and adopting Apache's `NOTIN` builder for the generic non-destroyed lookup helpers

### 기록 085 - refresh unmanaged import test expectations after import 정리 changes

- 로컬 브랜치: `main`
- 로컬 커밋: `6e54ade3f9`
- 소스 Apache 커밋:
  - `c6b20b8cc7` Fix failing tests
- 요약:
  - Update `UnmanagedVMsManagerImplTest` mocks to match the expanded `allocateRawVolume(...)` signature used by the import cleanup work
- 기능 영향도:
  - Keeps the unmanaged-import unit tests aligned with the newer import volume-allocation contract without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `UnmanagedVMsManagerImplTest` only
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `c116e07b1e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 086 - remove redundant stubbings from maintenance manager tests

- 로컬 브랜치: `main`
- 로컬 커밋: `35ee3d570a`
- 소스 Apache 커밋:
  - `7f7d0b02e1` Remove unnecessary stubbings in ManagementServerMaintenanceManagerImplTest (#11914) (#12623)
- 요약:
  - Remove redundant Mockito stubbings from `ManagementServerMaintenanceManagerImplTest`
- 기능 영향도:
  - Reduces unit-test noise and brittle stubbing without affecting runtime behavior
- 검증:
  - Attempting the Apache cherry-pick on `main` showed no remaining code delta because the current branch test already matches the simplified stubbing pattern
  - This local commit therefore records the satisfied upstream state in the history document
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `5b9a74fe08`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 087 - apply whitespace 정리 for pre-commit-managed files

- 로컬 브랜치: `main`
- 로컬 커밋: `082d4f4373`
- 소스 Apache 커밋:
  - `5d95bdd0eb` pre-commit trailing whitespace auto clean up (#12841)
- 요약:
  - Apply trailing-whitespace cleanup across the small set of config, README, template, and asset files touched by pre-commit
- 기능 영향도:
  - Reduces lint churn and keeps formatting consistent without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to whitespace-only cleanup and the corresponding `.pre-commit-config.yaml` normalization
  - Pre-commit execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `07fd1ad56c`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 088 - sync `.asf.yaml` collaborator list 업데이트s

- 로컬 브랜치: `main`
- 로컬 커밋: `d8e944983c`
- 소스 Apache 커밋:
  - `608345d165` Update collaborators list in `.asf.yaml`
- 요약:
  - Align the repository's `.asf.yaml` collaborator list with the upstream metadata cleanup
- 기능 영향도:
  - Keeps ASF/GitHub repository metadata in sync without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick 상태:
  - `705777c3f9`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 089 - add upstream contributor metadata to `.asf.yaml`

- 로컬 브랜치: `main`
- 로컬 커밋: `d203c9647a`
- 소스 Apache 커밋:
  - `9bbd32a8ef` Add DaanHoogland to the list of contributors
- 요약:
  - Add the upstream contributor metadata update to `.asf.yaml`
- 기능 영향도:
  - Keeps repository metadata aligned without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick 상태:
  - `43c0263bd1`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 090 - apply the latest upstream `.asf.yaml` metadata adjustment

- 로컬 브랜치: `main`
- 로컬 커밋: `47ff0094d3`
- 소스 Apache 커밋:
  - `d8f748ad0e` Update `.asf.yaml`
- 요약:
  - Apply the remaining upstream `.asf.yaml` metadata adjustment on top of the collaborator/contributor sync
- 기능 영향도:
  - Keeps repository metadata aligned without changing product behavior
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick 상태:
  - `8550a818d4`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 091 - record the 이미 반영된 direct-다운로드 temporary filename 강화 백포트

- 로컬 브랜치: `main`
- 로컬 커밋: `c296a31b33`
- 소스 Apache 커밋:
  - `46a6bbad27` `Fix: KVM Direct Download URL injection (#60)`
- 요약:
  - Record that the current branch already uses UUID-based temporary filenames for direct template downloads instead of reusing the source URL basename
  - Confirm that direct, metalink, and NFS download paths all route through the same temporary filename hardening
- 기능 영향도:
  - Avoids path/filename reuse issues during direct download staging without introducing a duplicate code change
  - Keeps the branch aligned with the older backport while preserving the newer direct-download handling already merged here
- 검증:
  - Comparing the upstream backport against the current branch showed no remaining code delta in `DirectTemplateDownloaderImpl`, `MetalinkDirectTemplateDownloader`, or `NfsDirectTemplateDownloader`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `18234309b0`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 092 - record the 이미 반영된 MinIO canned-policy 삭제 refresh 백포트

- 로컬 브랜치: `main`
- 로컬 커밋: `7dc5086883`
- 소스 Apache 커밋:
  - `3b987f21af` `[20.3] handle user's canned policy when a bucket is deleted`
- 요약:
  - Record that the current branch already refreshes MinIO canned policies after bucket deletion using the bucket owner's account context
  - Confirm that the shared canned-policy regeneration helper is already used on both bucket create and delete paths
- 기능 영향도:
  - Prevents a duplicate backport from re-touching the MinIO object-store driver while preserving the correct post-delete access policy behavior
  - Keeps the history aligned with the upstream maintenance branch that carried the same fix earlier than `apache/main`
- 검증:
  - Comparing the upstream backport against the current branch showed no remaining code delta in `BucketTO`, `MinIOObjectStoreDriverImpl`, or `MinIOObjectStoreDriverImplTest`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `598c6c63a5`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 093 - record the intentionally reverted account netstats lateral-join change

- 로컬 브랜치: `main`
- 로컬 커밋: `6121050871`
- 소스 Apache 커밋:
  - `58916eb608` `Use lateral join (introduced in MySQL 8.0.14) with subquery on user_statistics table in account_view for netstats (#12631)`
- 요약:
  - Record that the current branch intentionally keeps the separate `cloud.account_netstats_view` model rather than reintroducing the lateral-join variant
  - Note that this branch already matches the reverted end state later adopted upstream, so the original lateral-join change should stay unapplied here
- 기능 영향도:
  - Preserves compatibility with the branch's existing account network statistics view structure and avoids reintroducing a change that upstream subsequently backed out
  - Keeps schema/view behavior stable while still tracking the upstream commit history explicitly
- 검증:
  - Comparing the original lateral-join change against the current branch confirmed that `cloud.account_view` still joins `cloud.account_netstats_view` and the standalone `cloud.account_netstats_view.sql` remains present
  - This local commit therefore records the intentional already-reverted state in the history document instead of reapplying the lateral-join change
  - Schema migration execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `37ba105d50`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 094 - avoid wiping 볼륨 size metadata on failed 다운로드 states

- 로컬 브랜치: `main`
- 로컬 커밋: `4a2853d2b4`
- 소스 Apache 커밋:
  - `d0f6730157` `volume download fix`
- 요약:
  - Update the volume-download completion handler to persist size and physical-size metadata only when the download finishes in a non-error state
  - Reuse `VMTemplateStorageResourceAssoc.ERROR_DOWNLOAD_STATES` for the error-path check instead of repeating the individual status comparisons
- 기능 영향도:
  - Prevents failed or abandoned volume downloads from overwriting stored size metadata with invalid values
  - Keeps the error-path handling for download callbacks aligned with the shared error-state list
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to `BaseImageStoreDriverImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f0b2d71f4e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 095 - record the 이미 반영된 unmanaged import test 후속

- 로컬 브랜치: `main`
- 로컬 커밋: `0df74163d9`
- 소스 Apache 커밋:
  - `e8f8aca694` `Fix failing tests`
- 요약:
  - Record that the current branch already carries the `allocateRawVolume(..., anyBoolean())` matcher updates needed by the expanded unmanaged-import allocation signature
  - Note that the follow-up test-only change is already covered by the earlier unmanaged import cleanup work in this branch
- 기능 영향도:
  - Avoids duplicating a no-op test-only backport while keeping the source commit explicitly tracked in the sync history
  - Confirms that unmanaged import tests remain aligned with the boolean-extended allocation method signature
- 검증:
  - Attempting the Apache cherry-pick on `main` produced no remaining staged code delta because `UnmanagedVMsManagerImplTest` already uses the updated matcher signature
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the test change
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `78a9ab466d`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 096 - record the 이미 반영된 KVM incremental 스냅샷 rebase retry 개선ment

- 로컬 브랜치: `main`
- 로컬 커밋: `83fef55597`
- 소스 Apache 커밋:
  - `7c7b2ae75d` `Fix KVM incremental volume snapshot creation (#12666)`
- 요약:
  - Record that the current branch already exposes the incremental snapshot rebase retry wait property and retries rebase operations when libvirt still holds the image lock
  - Confirm that the KVM storage processor already contains the follow-up retry helper and matching agent property wiring
- 기능 영향도:
  - Avoids duplicating an already-integrated KVM snapshot resiliency improvement while still tracking the upstream source commit explicitly
  - Preserves the current retry-on-lock behavior for incremental snapshot rebases without introducing extra divergence
- 검증:
  - Comparing the upstream change against the current branch showed no remaining code delta in `agent.properties`, `AgentProperties`, or `KVMStorageProcessor`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `69efbc0036`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 097 - 수정 revert-to-VM-스냅샷 서비스 오퍼링 changes for custom offerings

- 로컬 브랜치: `main`
- 로컬 커밋: `5a5d654f58`
- 소스 Apache 커밋:
  - `b22dbbe2d7` `Fix Revert Instance to Snapshot with custom service offering (#12885)`
- 요약:
  - Record that the current branch already splits the revert validation messages for running/stopped instance snapshot combinations and already gates service offering changes through `userVmServiceOfferingNeedsChange(...)`
  - Note that the related `VMSnapshotManagerTest` coverage for custom and dynamic offerings is already present in this branch
- 기능 영향도:
  - Avoids duplicating an already-integrated revert-to-snapshot fix while still tracking the upstream source commit explicitly
  - Confirms that custom/dynamic service offering revert handling remains aligned with the upstream behavior already absorbed by this branch
- 검증:
  - Attempting the Apache cherry-pick on `main` surfaced only a comment-context conflict in `VMSnapshotManagerImpl` because the current branch already contained the functional logic and test coverage introduced by the upstream commit
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f3222d5ddf`
- 충돌 메모:
  - `VMSnapshotManagerImpl` conflicted only in the helper documentation block where Apache and the local branch edited the same nearby comment context
- 해결 메모:
  - Kept the local parameter descriptions in the helper comment and recorded the source commit as already satisfied because no functional code delta remained

### 기록 098 - add Headlamp as the preferred Kubernetes dashboard while keeping legacy access guidance

- 로컬 브랜치: `main`
- 로컬 커밋: `10056a6683`
- 소스 Apache 커밋:
  - `18075ae4a9` `Add support for Headlamp dashboard for kubernetes; deprecate legacy kubernetes dashboard (#12776)`
- 요약:
  - Update Kubernetes cluster readiness checks to accept either Headlamp in `kube-system` or the legacy Kubernetes Dashboard namespace
  - Switch the Kubernetes binaries ISO helper and control-node bootstrap flow to install Headlamp by default while preserving a fallback path for legacy dashboard manifests shipped in the ISO
  - Refresh the UI guidance so operators get Headlamp-first access, token creation, and legacy dashboard compatibility instructions side by side
- 기능 영향도:
  - Makes new Kubernetes clusters prefer Headlamp without breaking older clusters that still ship the legacy dashboard manifest
  - Improves operator guidance for dashboard access and token creation across both dashboard generations
- 검증:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the Kubernetes service util, bootstrap YAML, ISO helper script, and dashboard help UI
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `16fdb49f92`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 099 - record the 이미 반영된 생성-볼륨-on-스토리지-pool flow

- 로컬 브랜치: `main`
- 로컬 커밋: `b198b1c4c9`
- 소스 Apache 커밋:
  - `df7ff97271` `Create volume on a specified storage pool (#12966)`
- 요약:
  - Record that the current branch already exposes the `storageid` API parameter, server-side `createVolumeOnStoragePool(...)` path, and the admin UI flow for creating a volume on a selected primary storage pool
  - Confirm that the localized UI strings and create-on-storage toggle are already present in this branch
- 기능 영향도:
  - Avoids duplicating an already-integrated volume-placement enhancement while still tracking the upstream source commit explicitly
  - Confirms that admins can already place newly created volumes on a chosen primary storage pool in this branch
- 검증:
  - Attempting the Apache cherry-pick on `main` produced no remaining staged code delta because `CreateVolumeCmd`, `VolumeApiServiceImpl`, `CreateVolume.vue`, and the related UI labels already contain the upstream behavior
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `1621924b65`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 100 - tighten public IP limit 검증 for dedicated ranges 및 reservation-backed allocation

- 로컬 브랜치: `main`
- 로컬 커밋: `746ac3e1de`
- 소스 Apache 커밋:
  - `9db630932e` `Address public IP limit validations`
- 요약:
  - Allow account/domain VLAN map lookups to accept nullable owners so public IP validation can correctly fall back from account ownership to domain/system-account evaluation
  - Expose the system account through `ApiDBUtils` and teach `CheckedReservation` to use it when a domain-scoped reservation has no concrete account owner
  - Wrap public IP allocation and VLAN range creation in reservation-aware limit checks so dedicated and non-dedicated public IP flows apply the right resource accounting path
- 기능 영향도:
  - Prevents public IP limit validation from miscounting or skipping checks when the allocation path uses domain-scoped ownership or reservation-backed VLAN creation
  - Keeps dedicated public IP reservations from incrementing account public IP counts incorrectly while still enforcing limits for normal allocations
- 검증:
  - Apache cherry-pick required manual conflict resolution on `main` in `ConfigurationManagerImpl` because the local branch already carried additional imports around the VLAN creation path (`DomainHelper`, SystemVM-related managers) in the same import block
  - The resolved code keeps the local imports and applies Apache's nullable VLAN owner lookup plus reservation-backed public IP validation logic
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `00835167b6`
- 충돌 메모:
  - `ConfigurationManagerImpl` conflicted in the import block where Apache introduced reservation-related dependencies and the local branch already had extra local imports nearby
- 해결 메모:
  - Kept the local import set intact and added Apache's reservation-related imports and logic without changing the local VLAN creation flow structure

### 기록 101 - record the 이미 반영된 global 생성-network menu 방어 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `e054627bab`
- 소스 Apache 커밋:
  - `db83622956` `ui: fix create network from global create menu (#12677)`
- 요약:
  - Record that the current branch already guards the global create-network entry path so missing zone/resource context does not break the UI flow
  - Confirm that the earlier UI fix on this branch already covers the same null-safe access pattern intended by the Apache source commit
- 기능 영향도:
  - Avoids duplicating a source commit whose functional outcome is already present in the current UI behavior
  - Keeps the sync history explicit about the upstream source commit that was absorbed by the earlier local UI fix
- 검증:
  - Reverse-applying the Apache patch on the current branch showed the protective logic is already present in `CreateNetwork.vue`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `69055c5eec`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 102 - record the 이미 반영된 템플릿-zone 삭제 redirect source change

- 로컬 브랜치: `main`
- 로컬 커밋: `cddd3442c4`
- 소스 Apache 커밋:
  - `7aa0558c5b` `ui: avoid 404 after deleting template zones (#12681)`
- 요약:
  - Record that the current branch already routes template-zone deletion back to `/template` instead of relying on browser history when the last row disappears
  - Note that the local UI result matches the Apache source intent even though the surrounding formatting and table markup differ
- 기능 영향도:
  - Prevents duplicate reapplication of a UI navigation fix that is already present in the current branch
  - Makes the sync history explicit about the upstream source commit behind the already-absorbed behavior
- 검증:
  - Manual inspection of `TemplateZones.vue` confirmed `handleCancel()` already pushes to `/template` when no rows remain after deletion
  - The remaining diff against the Apache source commit is limited to formatting/context differences, so this record is tracked as already satisfied
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `33cbfa1532`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 103 - record the 이미 반영된 physical-network isolation description source change

- 로컬 브랜치: `main`
- 로컬 커밋: `fc2c7c112f`
- 소스 Apache 커밋:
  - `faaf7669c5` `Update isolation methods description for physical network (#12759)`
- 요약:
  - Record that the current branch already contains the upstream wording cleanup for physical network isolation method descriptions
  - Confirm that the local API help text matches the clarified upstream description for the supported isolation methods
- 기능 영향도:
  - Avoids duplicating a documentation-only API text change that is already present in the current branch
  - Preserves explicit upstream traceability for the wording change in the sync history
- 검증:
  - Reverse-applying the Apache patch on the current branch showed the updated isolation-method description is already present in `CreatePhysicalNetworkCmd`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the change
  - API doc generation has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `a60c247e47`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 104 - record the 이미 반영된 non-KVM SystemVM 템플릿 arch registration source change

- 로컬 브랜치: `main`
- 로컬 커밋: `c873a7a6e1`
- 소스 Apache 커밋:
  - `6f1aa96b4c` `engine/schema: fix new systemvm template is not registered during upgrade if hypervisor is not KVM (#12952)`
- 요약:
  - Record that the current branch already treats non-KVM hypervisors as `amd64` when iterating system VM template registrations during upgrade
  - Confirm that the matching unit-test expectation for VMware/system VM metadata architecture is already present as well
- 기능 영향도:
  - Avoids reapplying a source-level SystemVM upgrade fix whose behavior is already absorbed by the current branch
  - Keeps upstream traceability for the specific source commit that led to the already-present registration behavior
- 검증:
  - Inspection of `SystemVmTemplateRegistration.hypervisorList` and `SystemVmTemplateRegistrationTest` confirmed the non-KVM hypervisors already use `CPU.CPUArch.amd64`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `e245f9e940`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 105 - record the 이미 반영된 EL10 packaging compatibility source change

- 로컬 브랜치: `main`
- 로컬 커밋: `0a6b159a4c`
- 소스 Apache 커밋:
  - `80ee7f183f` `Fix six package incompatiblity with EL10 (#12799)`
- 요약:
  - Record that the current branch already carries the EL10 packaging follow-up by depending on `python3-six` and `python3-protobuf` and by selecting the compatible mysql connector wheel at install time
  - Note that the local branch keeps its `centos7` packaging path and `urllib3` handling while still preserving the upstream EL10 compatibility intent
- 기능 영향도:
  - Avoids duplicating a packaging/source change whose functional result is already present in the branch-specific RPM spec flow
  - Keeps the upstream source commit explicitly tracked despite the local packaging path differing from upstream `el8`
- 검증:
  - Inspection of `packaging/centos7/cloud.spec` confirmed the required Python package dependencies and Python-version-aware mysql connector install logic are already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - RPM build execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f04471f841`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 106 - record the 이미 반영된 백업 schedule 정리 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `193095aede`
- 소스 Apache 커밋:
  - `27e4d979f1` `Clean up backup references to their schedules when the schedules are deleted (#12401)`
- 요약:
  - Record that the current branch already clears backup-to-schedule references during schedule cleanup, moves schedule response assembly into `ApiResponseHelper`, and drops the unused `backup_interval_type` column
  - Confirm that the local branch already preserves the same user-visible API result while keeping the schedule cleanup path consistent
- 기능 영향도:
  - Avoids duplicating a backup schedule cleanup source change that is already present in the current branch
  - Keeps explicit upstream traceability for the source commit behind the existing cleanup behavior
- 검증:
  - Inspection of `schema-42200to42210.sql` and `ApiResponseHelper.createBackupScheduleResponse(...)` confirmed the column removal and response-building logic are already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `27fd9a023e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 107 - record the 이미 반영된 NSX 삭제-NAT comparison source change

- 로컬 브랜치: `main`
- 로컬 커밋: `fd40b07fe3`
- 소스 Apache 커밋:
  - `30dd234b00` `fix: NsxResource.executeRequest DeleteNsxNatRuleCommand comparison bug (#12833)`
- 요약:
  - Record that the current branch already avoids serialized `Network.Service` identity mismatches during NSX NAT deletion by comparing the service name rather than relying on object identity
  - Confirm that the existing NSX delete path already distinguishes `StaticNat` and `PortForwarding` using the stable service-name value
- 기능 영향도:
  - Avoids reapplying a source-level NSX fix whose runtime behavior is already present in the current branch
  - Preserves explicit upstream traceability for the serialized-command comparison bugfix
- 검증:
  - Inspection of `NsxResource.executeRequest(DeleteNsxNatRuleCommand)` confirmed the current branch already compares against `Network.Service.*.getName()`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f5b7edfdc6`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 108 - record the 이미 반영된 SharedMountPoint import 볼륨-check source change

- 로컬 브랜치: `main`
- 로컬 커밋: `f105fcf043`
- 소스 Apache 커밋:
  - `b0b3dc91f5` `fix: support SharedMountPoint volume checks for importVm (#12946)`
- 요약:
  - Record that the current branch already allows `CheckVolumeCommand` on `SharedMountPoint` pools in the KVM wrapper
  - Note that the local branch has since widened the supported set further, so the upstream source change is fully subsumed by the current implementation
- 기능 영향도:
  - Avoids duplicating a KVM import compatibility fix that is already present in the current branch
  - Keeps the upstream source commit explicitly tracked even though the local implementation now supports a superset of storage pool types
- 검증:
  - Inspection of `LibvirtCheckVolumeCommandWrapper.STORAGE_POOL_TYPES_SUPPORTED` confirmed `SharedMountPoint` is already included
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `e99c249814`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 109 - record the 이미 반영된 SharedMountPoint import/unmanage source change

- 로컬 브랜치: `main`
- 로컬 커밋: `2e40988604`
- 소스 Apache 커밋:
  - `b1bc5380a2` `fix: support SharedMountPoint for KVM volume import and unmanage (#12956)`
- 요약:
  - Record that the current branch already supports `SharedMountPoint` in the KVM volume import and unmanage path
  - Confirm that the local implementation already includes the Apache source change and no extra delta remains to apply
- 기능 영향도:
  - Avoids reapplying a KVM volume import/unmanage fix whose behavior is already present in the branch
  - Preserves source-level traceability for the upstream SharedMountPoint enhancement
- 검증:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the SharedMountPoint import/unmanage support is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `9ca8c24dc3`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 110 - record the 이미 반영된 GPU domain parsing source change

- 로컬 브랜치: `main`
- 로컬 커밋: `fa3aad483d`
- 소스 Apache 커밋:
  - `416679fae1` `Fix domain parsing for GPU & add Display controller in the supported PCI class (#12981)`
- 요약:
  - Record that the current branch already carries the improved GPU domain parsing and the added Display controller support in KVM GPU discovery
  - Confirm that the earlier GPU compatibility work in this branch already absorbed the upstream source change and its behavioral outcome
- 기능 영향도:
  - Avoids duplicating a GPU discovery source change whose functionality is already present in the current branch
  - Keeps explicit upstream traceability for the source commit behind the existing GPU parsing behavior
- 검증:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the GPU parsing/discovery update is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `27c1f93f7e`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 111 - record the 이미 반영된 password reset mail 템플릿 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `e521155877`
- 소스 Apache 커밋:
  - `5013cf2af6` `Fix user password reset mail template value (#12882)`
- 요약:
  - Record that the current branch already contains the finalized SQL update for `user.password.reset.mail.template`
  - Confirm that the schema upgrade now rewrites legacy reset-link placeholder formats to the current `{{{resetLink}}}` form
- 기능 영향도:
  - Avoids duplicating a schema-only source change whose final behavior is already present in the current branch
  - Keeps explicit upstream traceability for the password-reset mail template correction
- 검증:
  - Inspection of `schema-42200to42210.sql` confirmed the multiline `CONCAT_WS` update statement for `user.password.reset.mail.template` is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the SQL change
  - Schema migration execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8033c4f682`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 112 - record the 이미 반영된 force-삭제 cross-management-server propagation source change

- 로컬 브랜치: `main`
- 로컬 커밋: `f2222c9a3e`
- 소스 Apache 커밋:
  - `160876c6d7` `Fix: API Thread held forever during force deleting across MS (#12968)`
- 요약:
  - Record that the current branch already propagates `forced` and `forceDeleteStorage` flags through `PropagateResourceEventCommand` for cross-management-server host event handling
  - Confirm that the local `ResourceManagerImpl` path already uses the extended command constructor and peer propagation flow expected by the upstream fix
- 기능 영향도:
  - Avoids duplicating a clustered resource-management fix whose behavior is already present in the current branch
  - Preserves upstream traceability for the force-delete propagation change that prevents stuck API threads across management servers
- 검증:
  - Inspection of `PropagateResourceEventCommand` and `ResourceManagerImpl.propagateResourceEvent(...)` confirmed the current branch already includes the extra force-delete flags and the corresponding propagation path
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `f7fd36e378`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 113 - record the 이미 반영된 백업 및 버킷 review 후속 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `552317a397`
- 소스 Apache 커밋:
  - `13842a626d` `Address reviews`
- 요약:
  - Record that the current branch already contains the review follow-up changes for the backup and bucket reservation work
  - Confirm that the branch already preserves the refined delete-bucket exception contract and the simplified backup/bucket reservation handling introduced after upstream review
- 기능 영향도:
  - Avoids duplicating a source follow-up whose behavioral outcome was already absorbed during the earlier backup and bucket reservation batch
  - Keeps the upstream review-adjustment source commit explicitly tracked in the sync history
- 검증:
  - Current branch state in `BackupManagerImpl`, `BucketApiServiceImpl`, and `DeleteBucketCmd` already reflects the post-review API and reservation behavior documented in the earlier local bucket/backup records
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `cadcb40b42`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 114 - record the 이미 반영된 업데이트버킷 limit-검증 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `876f207ba4`
- 소스 Apache 커밋:
  - `2511fdffaa` `Implement limit validations on updateBucket`
- 요약:
  - Record that the current branch already validates object-storage quota changes during `updateBucket(...)`
  - Confirm that the update flow now uses reservation-backed quota adjustment instead of changing bucket quota without limit checks
- 기능 영향도:
  - Avoids duplicating an upstream source change whose updateBucket resource-limit behavior is already present in the branch
  - Preserves explicit traceability for the source commit behind the existing updateBucket quota validation path
- 검증:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the updateBucket limit-validation change is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8f61fdd046`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 115 - record the 이미 반영된 라우팅d VR related/established source change

- 로컬 브랜치: `main`
- 로컬 커밋: `9bc48de2e4`
- 소스 Apache 커밋:
  - `1fc4cb90bf` `Routed VR: accept packets from related and established connections (#12986)`
- 요약:
  - Record that the current branch already adds the `RELATED,ESTABLISHED` acceptance path to the routed VR forwarding rules
  - Confirm that the current SystemVM network filter flow already reflects the upstream source fix for routed VR traffic handling
- 기능 영향도:
  - Avoids duplicating a SystemVM source change whose routed VR packet-handling behavior is already present in the branch
  - Preserves explicit upstream traceability for the related/established rule addition
- 검증:
  - Inspection of `systemvm/debian/opt/cloud/bin/cs/CsNetfilter.py` confirmed the routed VR state-match rule already uses the `RELATED,ESTABLISHED` handling introduced by the source fix
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM runtime verification has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `3afeeef158`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 116 - record the 이미 반영된 NSX load-balancer patch-suppression source change

- 로컬 브랜치: `main`
- 로컬 커밋: `97c80615e7`
- 소스 Apache 커밋:
  - `05c59630e0` `fix: LB Creation avoid 404 API errors due to non-needed patches (#12835)`
- 요약:
  - Record that the current branch already skips unnecessary NSX LB patch calls when the effective pool/service state is unchanged
  - Confirm that the current NSX client already contains the patch-suppression logic and the related test coverage that avoid 404s on non-needed updates
- 기능 영향도:
  - Avoids duplicating an NSX source change whose runtime behavior is already present in the branch
  - Preserves explicit upstream traceability for the LB patch-suppression fix
- 검증:
  - Inspection of `NsxApiClient` and `NsxApiClientTest` confirmed the current branch already contains the skip-patch logic and its associated tests
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `0550ec4fa1`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 117 - record the 이미 반영된 HAProxy idle-timeout source change

- 로컬 브랜치: `main`
- 로컬 커밋: `8c3f389807`
- 소스 Apache 커밋:
  - `6e810989b6` `HAProxy Configuration: network.loadbalancer.haproxy.idle.timeout (#12586)`
- 요약:
  - Record that the current branch already supports `network.loadbalancer.haproxy.idle.timeout` in the HAProxy configuration flow
  - Confirm that the local implementation already carries the idle-timeout handling and its related health-check/config wiring
- 기능 영향도:
  - Avoids duplicating an HAProxy source change whose behavior is already present in the current branch
  - Preserves explicit upstream traceability for the idle-timeout feature source commit
- 검증:
  - Inspection of `HAProxyConfigurator` confirmed the current branch already processes the idle-timeout value and retains the related handling around HAProxy configuration
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM/runtime verification has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `c4225d4ef2`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 118 - record the 이미 반영된 nexthop static-라우팅 source change

- 로컬 브랜치: `main`
- 로컬 커밋: `ad2b713279`
- 소스 Apache 커밋:
  - `83f705ddc5` `Static Routes with nexthop non-functional for private gateways (#12859)`
- 요약:
  - Record that the current branch already applies nexthop static routes to the required PBR and ACL paths in routed/VPC router handling
  - Confirm that the local SystemVM scripts already carry the shared gateway-device lookup and the related FORWARD rule generation for nexthop routes
- 기능 영향도:
  - Avoids duplicating a routed-network source fix whose behavior is already present in the branch
  - Preserves explicit upstream traceability for the nexthop static-route repair
- 검증:
  - Inspection of `CsAddress.py` and `CsStaticRoutes.py` confirmed the current branch already contains the nexthop route ACL/PBR handling introduced by the source commit
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM runtime verification has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8614ecc20c`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 119 - record the 이미 반영된 백업 pending-job test merge-forward source change

- 로컬 브랜치: `main`
- 로컬 커밋: `ea197ef934`
- 소스 Apache 커밋:
  - `f5e75771bc` `merge forwards fix`
- 요약:
  - Record that the current branch already carries the pending-job backup-delete protection covered by the upstream merge-forward test tweak
  - Note that the exact test method signature differs locally, but the guarded delete-backup behavior and surrounding test coverage are already present
- 기능 영향도:
  - Avoids duplicating a test-only merge-forward adjustment while preserving the fact that the underlying guarded behavior is already covered in the current branch
  - Keeps the upstream follow-up commit explicitly visible in the sync history
- 검증:
  - The current `BackupManagerTest` still covers deletion blocked by pending jobs, and the underlying backup-delete guard is already present from earlier backup reservation work
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the test-only signature tweak
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `8c110c3012`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`

### 기록 120 - record the 이미 반영된 NSX pagination source change

- 로컬 브랜치: `main`
- 로컬 커밋: `0478e72d09`
- 소스 Apache 커밋:
  - `e0fe953791` `fix: NSX SDK list operations are pageable: the API returns a non-null and non-empty (#12834)`
- 요약:
  - Record that the current branch already follows NSX cursor chains and merges paged results through `PagedFetcher`
  - Confirm that the current NSX client already uses the pagination helper for list operations that return `cursor`-based result pages
- 기능 영향도:
  - Avoids duplicating an NSX list-pagination source change whose behavior is already present in the current branch
  - Preserves explicit upstream traceability for the pagination fix that ensures complete NSX datasets are fetched
- 검증:
  - Inspection of `NsxApiClient`, `PagedFetcher`, and `PagedFetcherTest` confirmed the current branch already contains the cursor-following pagination helper and its test coverage
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick 상태:
  - `b22afa163a`
- 충돌 메모:
  - `None observed on main`
- 해결 메모:
  - `N/A`


### 기록 121 - ONTAP primary 스토리지 풀 lifecycle operations

- 로컬 브랜치: `main`
- 로컬 커밋: `02cfc88817`
- 소스 Apache 커밋:
  - `02cfc88817` Create, Delete, Enable, Disable, Enter, Cancel maintenance of Primary StoragePool with ONTAP storage (#12563)
- 요약:
  - Add the ONTAP primary-storage lifecycle operations needed to enable, disable, enter maintenance, cancel maintenance, delete, and create pool flows consistently
  - Align ONTAP pool handling with the newer managed-primary-storage operation model used by the rest of the stack
- 기능 영향도:
  - Expands ONTAP storage administration coverage without changing unrelated storage providers
  - Reduces operator-side workflow gaps when maintaining ONTAP-backed primary storage pools
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `eb4136e17c`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 122 - clone existing offerings 및 업데이트 the clone

- 로컬 브랜치: `main`
- 로컬 커밋: `3ac814b3af`
- 소스 Apache 커밋:
  - `3ac814b3af` Add support to clone existing offerings and update them (#12357)
- 요약:
  - Add support to clone existing offerings instead of recreating equivalent definitions by hand
  - Preserve follow-up update flows so the cloned offering can be adjusted before use
- 기능 영향도:
  - Simplifies offering administration and reduces manual configuration drift
  - Keeps offering copy operations explicit and traceable in API/UI flows
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `36750771df`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 123 - enable SharedMountPoint HA heartbeat for KVM

- 로컬 브랜치: `main`
- 로컬 커밋: `2c0995de98`
- 소스 Apache 커밋:
  - `2c0995de98` KVM: Enable HA heartbeat on ShareMountPoint (#12773)
- 요약:
  - Enable the KVM HA heartbeat path for `SharedMountPoint` primary storage
  - Treat SharedMountPoint more consistently with the HA heartbeat expectations already used by other shared-storage types
- 기능 영향도:
  - Improves host-HA health detection when SharedMountPoint-backed KVM pools are in use
  - Reduces the chance of SharedMountPoint pools being skipped by HA heartbeat handling
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `579a22856f`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 124 - refactor 할당량 summary API assembly

- 로컬 브랜치: `main`
- 로컬 커밋: `c3e41d9bd7`
- 소스 Apache 커밋:
  - `c3e41d9bd7` Refactor Quota Summary API (#10505)
- 요약:
  - Refactor quota summary construction so response assembly is cleaner and easier to extend
  - Keep the observable quota summary behavior while reducing duplication in the response-building path
- 기능 영향도:
  - No intended behavioral change for callers of the Quota Summary API
  - Lowers maintenance cost around future quota-summary enhancements and fixes
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `d983c56298`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 125 - add KVM NIC enable 및 disable API 지원

- 로컬 브랜치: `main`
- 로컬 커밋: `fe17d4d04d`
- 소스 Apache 커밋:
  - `fe17d4d04d` Add API to enable/disable NICs for KVM (#12819)
- 요약:
  - Add the `enabled` state for VM NICs and expose the KVM-specific update flow in API, DB views, and UI
  - Keep the older `linkstate` handling intact while layering the new administrative NIC enable/disable behavior on top
- 기능 영향도:
  - Allows operators to enable or disable supported KVM NICs without removing the NIC
  - Preserves existing Europa link-state and IP/MAC editing behavior while extending the NIC management surface
- 검증:
  - `main` and `europa` both carry the merged API, DB view, hypervisor, and UI changes
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `da920f25bb`
- 충돌 메모:
  - `Nic`, `NicResponse`, `NicVO`, DB views, router join objects, `HypervisorGuruBase`, locale strings, and `NicsTab.vue` all overlapped with Europa-specific link-state work
- 해결 메모:
  - Keep Europa `linkstate`, IP/MAC edit, and UI actions intact while adding the Apache `enabled` field end-to-end
  - Preserve both link-state and enabled-state handling in API responses and KVM NIC update UI flows

### 기록 126 - 차단 CKS-member VM unmanage 또는 reinstall operations

- 로컬 브랜치: `main`
- 로컬 커밋: `db08332010`
- 소스 Apache 커밋:
  - `db08332010` [4.22] Prevent unmanaging or reinstalling a VM if it is part of a CKS cluster (#12800)
- 요약:
  - Prevent VM unmanage and reinstall operations when the VM is still part of a CKS cluster
  - Add the cluster-membership helper needed to guard those flows consistently from the server layer
- 기능 영향도:
  - Protects CKS clusters from destructive lifecycle actions that would leave cluster state inconsistent
  - Keeps operator intent explicit by failing earlier on unsafe VM lifecycle requests
- 검증:
  - `main` and `europa` both carry the guard logic and matching test updates
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `0df62891cd`
- 충돌 메모:
  - `UserVmManagerImpl` and `UserVmManagerImplTest` overlapped with Europa `vBMC` assignment helpers
- 해결 메모:
  - Preserve Europa `allocateVbmcToVM` and `removeVbmcToVM` handling while adding Apache `isVMPartOfAnyCKSCluster(...)`
  - Extend the local test imports instead of dropping existing Europa coverage helpers

### 기록 127 - deduplicate dummy 템플릿s 및 refresh import guest OS mapping

- 로컬 브랜치: `main`
- 로컬 커밋: `2869448c1e`
- 소스 Apache 커밋:
  - `2869448c1e` Fix duplicate dummy templates, and update guest os for dummy template (#12780)
- 요약:
  - Prevent duplicate dummy template creation during KVM import flows
  - Refresh the guest OS used by the default KVM import template so later import matching behaves correctly
- 기능 영향도:
  - Avoids accumulating duplicate dummy templates in import-heavy environments
  - Improves downstream unmanaged-instance import matching for the default KVM template path
- 검증:
  - `main` and `europa` both carry the SQL, storage motion, and unmanaged-import adjustments
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `7d1012e114`
- 충돌 메모:
  - `schema-42200to42210.sql`, `StorageSystemDataMotionStrategy`, and `UnmanagedVMsManagerImpl` overlapped with existing Europa import customizations
- 해결 메모:
  - Keep the local SQL tail and import-template helpers, then layer in the Apache dummy-template deduplication and guest-OS refresh logic
  - Preserve the existing unmanaged import template naming/constants while aligning the guest OS defaults

### 기록 128 - derive VMware-to-KVM import guest OS from source mappings

- 로컬 브랜치: `main`
- 로컬 커밋: `350d2c3ba2`
- 소스 Apache 커밋:
  - `350d2c3ba2` [VMware to KVM] Add guest OS for importing VM based on the source VM OS (#12802)
- 요약:
  - Carry the source guest OS mapping into the VMware-to-KVM import path so the selected guest OS better matches the imported VM
  - Auto-select the mapped guest OS in the import UI when mappings are available
- 기능 영향도:
  - Improves imported-VM accuracy by avoiding an incorrect or generic guest OS selection
  - Reduces manual operator correction during VMware-to-KVM imports
- 검증:
  - `main` and `europa` both carry the guest-OS mapping updates in server and UI flows
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `b0b8f54ea8`
- 충돌 메모:
  - `ImportUnmanagedInstance.vue` and `ManageInstances.vue` overlapped with Europa watcher logic and import-task state customizations
- 해결 메모:
  - Keep Europa resource watchers, task filters, and auto-refresh handling while adding the Apache guest-OS mapping selection logic
  - Preserve local import-task UX state and only layer in the new guest-OS mapping behavior

### 기록 129 - handle ALL-port firewall rules 중 CKS scale 및 삭제

- 로컬 브랜치: `main`
- 로컬 커밋: `1a40fc72de`
- 소스 Apache 커밋:
  - `1a40fc72de` Fix K8s scaling and deletion issue if firewall rule is for ALL ports (#12806)
- 요약:
  - Fix CKS scaling and deletion flows so firewall rules defined for all ports do not break cleanup and update logic
  - Normalize the affected rule handling in the CKS orchestration path
- 기능 영향도:
  - Prevents CKS cluster operations from failing when broad firewall rules are already attached
  - Reduces stuck scale/delete workflows caused by rule-shape assumptions
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `38ed1cbef4`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 130 - reserve secondary 스토리지 resources for 업로드 operations

- 로컬 브랜치: `main`
- 로컬 커밋: `5dac21b5cb`
- 소스 Apache 커밋:
  - `5dac21b5cb` [22.0] secondary storage resource limit for upload
- 요약:
  - Add resource reservation checks for secondary-storage uploads before the transfer starts
  - Align upload flows with the broader secondary-storage quota and reservation model
- 기능 영향도:
  - Prevents overcommitting secondary storage during upload operations
  - Makes quota failures happen earlier and more predictably for upload workflows
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `fa99e94ad7`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 131 - 후속 업로드 monitor reservation merge 정리

- 로컬 브랜치: `main`
- 로컬 커밋: `b3614473ca`
- 소스 Apache 커밋:
  - `b3614473ca` storage: fix upload monitor limit merge cleanup
- 요약:
  - Clean up the upload-monitor follow-up after the reservation-aware secondary-storage changes
  - Keep the upload monitor flow internally consistent after the earlier limit-enforcement backport
- 기능 영향도:
  - Reduces merge-forward drift in the upload monitor path without broadening user-visible behavior
  - Keeps the reservation-aware upload code path coherent for later maintenance
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `d9bdb38905`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 132 - reserve secondary 스토리지 resources for 다운로드 operations

- 로컬 브랜치: `main`
- 로컬 커밋: `79387430f4`
- 소스 Apache 커밋:
  - `79387430f4` [22.0] secondary storage resource limit for download
- 요약:
  - Add resource reservation checks for secondary-storage downloads before data movement begins
  - Pair the download path with the earlier upload-side reservation handling
- 기능 영향도:
  - Prevents download workflows from silently exceeding secondary-storage allocation limits
  - Makes operator-visible failures happen at request time instead of later during transfer
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `461d51a498`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 133 - treat infinite secondary-스토리지 limits correctly 중 업로드 cheCKS

- 로컬 브랜치: `main`
- 로컬 커밋: `d600fdd363`
- 소스 Apache 커밋:
  - `d600fdd363` Consider infinite resources when calculating secondary storage limit for upload operations
- 요약:
  - Honor effectively-infinite secondary-storage limits instead of treating them as bounded upload capacity
  - Keep upload reservation logic consistent with the semantics of unlimited quotas
- 기능 영향도:
  - Prevents false-positive quota failures for accounts or domains with unlimited secondary-storage settings
  - Reduces noisy operator intervention on otherwise valid upload requests
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `1edc102946`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 134 - 수정 ImageStore업로드Monitor 후속 merge issue

- 로컬 브랜치: `main`
- 로컬 커밋: `bc6ac3ef25`
- 소스 Apache 커밋:
  - `bc6ac3ef25` Fixed a merge issue in ImageStoreUploadMonitorImpl
- 요약:
  - Resolve the lingering merge issue in `ImageStoreUploadMonitorImpl` after the upload reservation work
  - Keep the image-store upload monitor aligned with the intended reservation-aware logic
- 기능 영향도:
  - Low-risk internal cleanup that prevents the upload monitor from drifting away from the corrected limit path
  - Helps keep later secondary-storage fixes easier to reason about
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `42d4ca4307`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 135 - 지원 conserve mode on VPC offerings

- 로컬 브랜치: `main`
- 로컬 커밋: `8550d45ae7`
- 소스 Apache 커밋:
  - `8550d45ae7` Add conserve mode for VPC offerings (#12487)
- 요약:
  - Add conserve-mode handling for VPC offerings instead of limiting the behavior to other network-offering classes
  - Expose the related offering behavior consistently in the VPC flow
- 기능 영향도:
  - Lets operators define VPC offerings that conserve resources until services are explicitly required
  - Brings VPC offering behavior closer to the already-supported non-VPC offering model
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `d8646c2c1a`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 136 - 수정 VMware-to-KVM migration instance listing failures

- 로컬 브랜치: `main`
- 로컬 커밋: `59cb77b6f4`
- 소스 Apache 커밋:
  - `59cb77b6f4` [Fix] VMware to KVM migration instances listing failure (#12766)
- 요약:
  - Fix the instance-listing path used by VMware-to-KVM migration discovery so manageable candidates are returned reliably
  - Remove failure cases caused by assumptions in the source-instance listing logic
- 기능 영향도:
  - Prevents VMware-to-KVM migration workflows from failing before import starts
  - Improves operator confidence in source-VM discovery and selection
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `2795390f8c`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 137 - 허용 affinity group selection 중 CKS cluster creation

- 로컬 브랜치: `main`
- 로컬 커밋: `2629d5f5ba`
- 소스 Apache 커밋:
  - `2629d5f5ba` CKS: Allow affinity group selection during cluster creation (#12386)
- 요약:
  - Extend CKS cluster creation so affinity groups can be selected during the initial request
  - Carry the chosen affinity-group settings through the relevant API and UI creation path
- 기능 영향도:
  - Improves placement control for new CKS clusters
  - Reduces the need for follow-up manual adjustments after cluster creation
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `0399cdfe22`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 138 - 정리 system VM public NIC addresses for PublicNetworkGuru

- 로컬 브랜치: `main`
- 로컬 커밋: `ed3d3f22e4`
- 소스 Apache 커밋:
  - `ed3d3f22e4` Clear System VM IP from NICs for PublicNetworkGuru (#11992)
- 요약:
  - Clear stale System VM public-NIC IP information during `PublicNetworkGuru` NIC handling
  - Keep public-network NIC state aligned with the intended system-VM allocation flow
- 기능 영향도:
  - Prevents stale or misleading System VM NIC state from leaking into later network operations
  - Lowers the chance of incorrect PublicNetworkGuru assumptions during NIC orchestration
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `3faa5129f9`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 139 - move API key usage to the latest stored key pair

- 로컬 브랜치: `main`
- 로컬 커밋: `f1104735d2`
- 소스 Apache 커밋:
  - `f1104735d2` API key pair restructure (#9504)
- 요약:
  - Refactor API-key handling to look up the latest stored key pair instead of reading keys directly from the user record in affected flows
  - Update request-signing and autoscale integration paths to use the restructured key lookup model
- 기능 영향도:
  - Aligns runtime behavior with the newer multi-key-pair model
  - Reduces risk of using stale or structurally outdated API/secret key data in server workflows
- 검증:
  - `main` and `europa` both carry the latest-key-pair lookup changes across DAO, server, autoscale, and account paths
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `1a367f0dd5`
- 충돌 메모:
  - `UserAccountDao`, `UserAccountDaoImpl`, `QueryManagerImpl`, `AutoScaleManagerImpl`, `ManagementServerImpl`, `AccountManagerImpl`, `UserVmManagerImpl`, and `AccountManagerImplTest` overlapped with existing Europa extensions
- 해결 메모:
  - Preserve Europa-only helper methods and imports while switching runtime key usage to `ApiDBUtils.searchForLatestUserKeyPair(...)`
  - Keep local Keycloak/Glue/Wall flows and add the Apache key-removal helper where required

### 기록 140 - remove unused VMware-to-KVM convert environment variables

- 로컬 브랜치: `main`
- 로컬 커밋: `f19bcc499e`
- 소스 Apache 커밋:
  - `f19bcc499e` [VMware to KVM Migration] Fix unused convert env vars (#11947)
- 요약:
  - Remove unused conversion-environment plumbing from the VMware-to-KVM migration flow
  - Keep the import path focused on the variables and options that are actually consumed
- 기능 영향도:
  - Low-risk internal cleanup that reduces confusion in the VMware-to-KVM conversion path
  - Makes later conversion-path debugging easier by removing dead configuration branches
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `767aeab043`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 141 - clean up imported VM artifacts on allocation failure

- 로컬 브랜치: `main`
- 로컬 커밋: `c9f0d6e39f`
- 소스 Apache 커밋:
  - `c9f0d6e39f` Cleanup imported VM from disk on failure due to volume allocation + prevent duplicate volume and primary storage increment on import
- 요약:
  - Clean up imported VM artifacts from disk when the workflow fails during volume allocation
  - Prevent duplicate volume and primary-storage resource increments during import failure handling
- 기능 영향도:
  - Reduces leaked imported artifacts and resource-count skew after failed imports
  - Makes VMware-to-KVM and unmanaged import recovery more predictable for operators
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `da94b79294`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 142 - add VDDK-backed VMware-to-KVM migration 지원

- 로컬 브랜치: `main`
- 로컬 커밋: `a8a4d7a362`
- 소스 Apache 커밋:
  - `a8a4d7a362` Added VDDK support in VMware to KVM migrations (#12970)
- 요약:
  - Add VDDK-backed direct VMware-to-KVM conversion support alongside the existing OVF-based flow
  - Extend the API, agent, KVM wrapper, server orchestration, and UI so operators can select VDDK-backed imports when the host supports it
- 기능 영향도:
  - Improves VMware-to-KVM migration flexibility and can reduce intermediate export handling in supported environments
  - Keeps Europa `Ablestack V2K` custom flow intact while exposing Apache VDDK behavior for the standard import path
- 검증:
  - `main` and `europa` both carry the merged VDDK server, KVM wrapper, and UI changes
  - `git diff --check` was clean after conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `9713ecd26f`
- 충돌 메모:
  - `LibvirtConvertInstanceCommandWrapper`, `UnmanagedVMsManagerImpl`, and `ImportUnmanagedInstance.vue` all overlapped with Europa VMware-to-KVM and Ablestack V2K customizations
- 해결 메모:
  - Preserve Europa `Ablestack V2K`, SharedMountPoint/RBD handling, and existing UI import options while adding upstream VDDK controls and server-side support
  - Restore `convertinstancehostid` and `convertinstancepoolid` handling for the Europa V2K path while keeping VDDK-specific behavior confined to the standard VMware-to-KVM flow

### 기록 143 - expose redundant-network restart control in the UI

- 로컬 브랜치: `main`
- 로컬 커밋: `3306995626`
- 소스 Apache 커밋:
  - `3306995626` Enable defining a network as redundant during restart through the UI (#7405)
- 요약:
  - Expose the redundant-network toggle through the UI restart network workflow
  - Bring the restart flow closer to the already-supported API capability
- 기능 영향도:
  - Lets operators request redundant-network behavior during restart without dropping to the API
  - Improves parity between API and UI for network restart operations
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `fd8b981fa5`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 144 - 개선 PowerFlex 및 ScaleIO client initialization handling

- 로컬 브랜치: `main`
- 로컬 커밋: `5bac2c8310`
- 소스 Apache 커밋:
  - `5bac2c8310` PowerFlex/ScaleIO client initialization, authentication and command execution improvements (#12391)
- 요약:
  - Improve PowerFlex/ScaleIO client initialization, authentication, and command execution handling
  - Tighten the provider-side error handling around ScaleIO/PowerFlex operations
- 기능 영향도:
  - Reduces provider-side failures caused by brittle initialization or authentication sequencing
  - Makes storage-provider troubleshooting easier when ScaleIO/PowerFlex commands fail
- 검증:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `c5ebe3d17d`
- 충돌 메모:
  - `None recorded in current sync notes`
- 해결 메모:
  - `N/A`

### 기록 145 - reserve resources before creating 볼륨s

- 로컬 브랜치: `main`
- 로컬 커밋: `091fa8c75c`
- 소스 Apache 커밋:
  - `091fa8c75c` [22.0] resource reservation on volume creation
- 요약:
  - Reserve volume and primary-storage resources before committing volume creation
  - Fail earlier when quota or storage reservations cannot be satisfied instead of allocating partially and rolling back later
- 기능 영향도:
  - Improves quota correctness during volume creation under concurrency
  - Reduces the chance of resource-count drift around failed or racing volume-create requests
- 검증:
  - `main` and `europa` both carry the reservation-aware volume-create flow
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `962821ecbe`
- 충돌 메모:
  - `VolumeApiServiceImpl` overlapped with the Europa `kvdoEnable` volume-create extension
- 해결 메모:
  - Keep the Apache `CheckedReservation` try-with-resources structure and its exception handling
  - Preserve the Europa `kvdoEnable` argument when calling the local `commitVolume(...)` overload

### 기록 146 - 수정 스냅샷 copy reservation concurrency handling

- 로컬 브랜치: `main`
- 로컬 커밋: `ca9227dcc7`
- 소스 Apache 커밋:
  - `ca9227dcc7` Fix snapshot copy resource limit concurrency
- 요약:
  - Correct the snapshot-copy resource-reservation path so concurrent snapshot copy flows do not double-count or race their limit handling
  - Remove the stale duplicate post-processing increments left behind by the older copy path
- 기능 영향도:
  - Improves snapshot copy quota correctness under concurrent zone-copy activity
  - Prevents resource-count skew after copy operations that already reserve and account for snapshot resources earlier in the flow
- 검증:
  - `main` and `europa` both carry the updated snapshot copy reservation flow and test adjustments
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick 상태:
  - `fe03ece06d`
- 충돌 메모:
  - `SnapshotManagerImpl` retained an older duplicate resource-count increment block after the Apache reservation refactor
- 해결 메모:
  - Keep the Apache reservation-aware snapshot copy structure and remove the stale duplicate increment block from the local branch

### Observed Already Satisfied

- `273699cf56` `kvm: fix wrong CheckVirtualMachineAnswer when vm does not exist (#12928)`
  - Current branch state already avoids `domainLookupByName(...)` for non-running VMs and already carries focused wrapper tests for the fixed behavior
  - Treat as already satisfied instead of creating a duplicate local commit
- `7ba5240b31` `Block backup deletion while create-VM-from-backup or restore jobs are in progress (#12792)`
  - Current branch state already blocks backup deletion when pending create-from-backup or restore jobs exist, and the matching regression test coverage is already present in `BackupManagerTest`
  - Treat as already satisfied instead of creating a duplicate local commit
- `abdf926219` `Revert "Use lateral join (introduced in MySQL 8.0.14) with subquery on user_statistics table in account_view for netstats (#12631)" (#12965)`
  - Current branch state already splits network statistics into `cloud.account_netstats_view` and joins that view from `cloud.account_view`
  - Treat as already satisfied instead of creating a duplicate local commit
- `68bd056306` `Support timeout configuration for Create and Restore NAS backup (#12964)`
  - Current branch state already uses the timeout-aware restore wrapper path and test adjustments after the earlier NAS timeout work plus the Linstor restore merge
  - Treat as already satisfied instead of creating a duplicate local commit
- `1ff9eec997` `Load arch data for backup from template during create instance from backup (#12801)`
  - Current branch state already preloads backup architecture in the create-from-backup UI flow and no additional code delta remained when applying the Apache change
  - Treat as already satisfied instead of creating a duplicate local commit
- `e2497cfc4d` `backport: default system vm template update implementation (#12935)`
  - Current branch state already carries the default SystemVM template update implementation after the earlier upgrade-path fixes for 4.20.3 and non-KVM architecture handling
  - Treat as already satisfied instead of creating a duplicate local commit
- `4ba4bd33c3` `replace GROUP_CONCAT with JSON_ARRAYAGG to avoid errors like Row 19 was cut by GROUP_CONCAT (#12777)`
  - Current branch state already uses `JSON_ARRAYAGG` in the affected `schema-42010to42100.sql` upgrade view definitions
  - Treat as already satisfied instead of creating a duplicate local commit
- `8608b4edd0` `Fix snapshot copy resource limit concurrency`
  - Current branch state already wraps snapshot-chain copy reservation in `CheckedReservation` and routes per-snapshot copy through `copySnapshotToZone(..., shouldCheckResourceLimits)`
  - `SnapshotManagerImplTest` no longer stubs the removed direct `checkResourceLimit(...)` call in the covered copy flow
  - Treat as already satisfied instead of creating a duplicate local commit
- `470812100e` `server: set template type to ROUTING or USER if template type is not specified when upload a template (#12768)`
  - Current branch state already handles `GetUploadParamsForTemplateCmd` in `TemplateManagerImpl.validateTemplateType(...)`
  - The `schema-42200to42210.sql` backfill for `vm_template.type IS NULL -> USER` is also already present
  - Treat as already satisfied instead of creating a duplicate local commit
- `e10c066cc1` `Fix NPE during VM setup for pvlan (#12781)`
  - Current branch state already guards `setupVmForPvlan(...)` against null NICs, null broadcast URIs, non-PVLAN schemes, and missing hosts
  - Treat as already satisfied instead of creating a duplicate local commit
- `2359061f66` `api: remove required flag of gatewayid in CreateStaticRouteCmd (#12786)`
  - Current branch state already has `gatewayId` without `required = true`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `59b6c32b60` `[UI] Fix create backup notification (#12903)`
  - Current branch state already uses `label.create.backup` in `StartBackup.vue`
  - Treat as already satisfied instead of creating a duplicate local commit
  - Re-check on `ablestack-europa` before final range reconciliation
- `c6936889f5` `server: prevent adding vm compute details when not applicable (#12637)`
  - Current branch state already contains the Apache validation changes in `validateCustomParameters(...)` and `verifyVmLimits(...)`
  - Matching regression tests are already present in `UserVmManagerImplTest`
  - Treat as already satisfied instead of creating a duplicate local commit

## 세분화된 리소스 한도 배치

### R01 - Backup / Bucket Reservation Core

- Apache 커밋:
  - `19b4ef1069`, `13842a626d`, `2511fdffaa`
- 범위:
  - Reservation-aware limit checks for backup create/delete and bucket alloc/delete/update flows
- Why grouped:
  - These three commits evolve the same operational path from initial reservation support to review fixes and `updateBucket` completion

### R02 - Secondary Storage Transfer Limits

- Apache 커밋:
  - `03dfe4d1f3`, `81a8ac8e1f`
- 범위:
  - Download/upload resource counting and reservation behavior on secondary storage transfer paths
- Why grouped:
  - Both commits govern transient secondary storage consumption during template/image movement and are likely to share supporting context

### R03 - Snapshot Copy Reservation Concurrency

- Apache 커밋:
  - `8608b4edd0`
- 범위:
  - Snapshot copy concurrency handling for resource limit reservations
- 분리 이유:
  - Touches snapshot management only and can be validated independently from backup/object storage flows

### R04 - VM Start Reservation Validation

- Apache 커밋:
  - `4bcd509193`
- 범위:
  - Reservation and limit validation during `StartVirtualMachine`
- 분리 이유:
  - High runtime sensitivity and likely overlap with Europa VM lifecycle customizations

### R05 - Reserved Resource Details Exposure

- Apache 커밋:
  - `95816b44e9`
- 범위:
  - API/UI exposure of reserved resource details for extensions and VM views
- 분리 이유:
  - This is an API/UI visibility change rather than a backend reservation enforcement change

## Initial Candidate Notes

### B00 - Metadata / CI / docs housekeeping

- 검토 대상 Apache 커밋:
  - `608345d165` Update collaborators list in `.asf.yaml`
  - `9cc6c09b9e` Remove broken ViserJS attribution link from UI README
  - `9bbd32a8ef` Add contributor metadata
  - `d8f748ad0e` Update `.asf.yaml`
  - `b744824f65` Add code owners for NSX plugin
  - `6bcbb008b4` Bump `actions/checkout` to `v6`
  - `cf9bda2050` Add github-actions ecosystem to Dependabot
  - `5d95bdd0eb` pre-commit trailing whitespace auto clean up
  - `5d61ba3538` codespell and hook update
- 메모:
  - Safe starter batch for local/main commit workflow
  - Not all commits may need Europa cherry-pick if they do not affect runtime behavior

### B01 - Resource limits / quota / reservation

- 검토 대상 Apache 커밋:
  - `37e3657770`, `003c840817`, `8d269cf5be`, `831ef82ff9`, `1f849caa0b`
  - `3d678e726a`, `d11d182c71`, `4855d40e6e`, `d722415105`, `07c3dc86b2`
  - `89df318164`, `4dd91feb27`, `1593944553`, `7faa1b650b`, `b025e85fc5`
  - `e0ef3a6947`, `06ee2fea76`, `4bcd509193`, `03dfe4d1f3`, `81a8ac8e1f`
  - `360b64ce1e`, `0a4b4c6af0`, `dc7068a135`, `9c0c8da706`, `e8d57d1b0d`
  - `4f93ba888c`, `19b4ef1069`, `2511fdffaa`
- 메모:
  - Highest functional risk area
  - Expect conflicts in `api`, `server`, `engine/schema`, `plugins/database/quota`
  - Duplicate release-line backports must be collapsed into one local change set

### B02 - Backup / volume / snapshot / import flows

- 검토 대상 Apache 커밋:
  - `5d5ee7b689`, `f7f0e75122`, `88a12a801f`, `8ce1c9876e`, `24fd440ee7`
  - `86c9f7bd94`, `8608b4edd0`, `c19630f0a4`, `84676afd5c`, `b22dbbe2d7`
  - `2416db2a44`, `131ea9f7ac`, `6ca6aa1c3f`, `4ebe3349b7`, `e2497cfc4d`
  - `b0b3dc91f5`, `b1bc5380a2`, `03de62bf38`, `7ba5240b31`, `1ff9eec997`
  - `68bd056306`, `7b467496cb`, `2a60305792`, `8f3c6fad7a`, `df7ff97271`
  - `d75acb6efc`, `0c86899cc1`
- 메모:
  - Strong overlap with Europa customizations is likely
  - Storage provider-specific behavior must be reviewed before direct cherry-pick

### B03 - Network / VPC / LB / NSX / VR

- 검토 대상 Apache 커밋:
  - `7ad68aafa5`, `2359061f66`, `27bce46a8e`, `09ee0927e9`, `93239e09f1`
  - `30dd234b00`, `abdf926219`, `ae455ee193`, `1fc4cb90bf`, `05c59630e0`
  - `e0fe953791`, `6e810989b6`, `83f705ddc5`
- 메모:
  - High probability of semantic conflicts on Europa networking behavior
  - Resolve based on current Europa service assumptions and API compatibility

### B04 - Hypervisor / KVM / VMware / CKS

- 검토 대상 Apache 커밋:
  - `6419e1c825`, `9e386a3128`, `8c579538f9`, `7048944883`, `b497f58022`
  - `7107d28db8`, `7c3637a2f5`, `7cdcf571fa`, `c1af36f8fc`, `71bd26ff7c`
  - `18075ae4a9`, `7eea9ed448`, `e297644ce1`, `273699cf56`
- 메모:
  - Medium conflict risk, but runtime validation on KVM and CKS paths is required

### B05 - UI / UX / config defaults

- 검토 대상 Apache 커밋:
  - `120a43648b`, `db83622956`, `7aa0558c5b`, `71daf84c9e`, `59b6c32b60`
  - `9f57a4dd19`, `ed575cc0a1`
- 메모:
  - Good early cherry-pick candidates after metadata batch
  - UI build and localization regression checks are required

### B06 - Async jobs / account / user / API ergonomics

- 검토 대상 Apache 커밋:
  - `74af9b9875`, `470812100e`, `b5858029bb`, `416679fae1`, `b196e97cc3`
  - `47c5bb8ee7`, `38abe2df0b`, `5013cf2af6`, `160876c6d7`, `13842a626d`
- 메모:
  - Review API response compatibility before Europa propagation
