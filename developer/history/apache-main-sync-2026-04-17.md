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

# Apache Main Sync History - 2026-04-17

## Scope

- Repository: `ablestack-cloud`
- Goal:
  - Reflect `apache/cloudstack:main` changes into `local/main`
  - Propagate validated changes to `local/ablestack-europa` by `cherry-pick`
- Working baselines:
  - `apache/main`: `2d6280b9da` (`2026-04-17 04:35:25 +0530`)
  - `upstream/main`: `a873fb1ff4` (`2026-02-27 16:05:07 +0900`)
  - `origin/main`: `c6263fbf1c` (`2025-12-11 18:41:37 +0900`)
  - `ablestack-europa`: `661722858d` (`2026-04-16 09:03:28 +0900`)
- Common ancestor between `upstream/main` and `apache/main`:
  - `da85858e93`

## Range Summary

- Apache-only commits since `upstream/main` baseline:
  - `162` commits total
  - `150` non-merge commits
- Net diff size for `upstream/main..apache/main`:
  - `576` files changed
  - `36908` insertions
  - `7302` deletions
- Europa overlap hotspots with Apache delta:
  - `plugins`
  - `server`
  - `api`
  - `engine`
  - `ui`

## Operating Rules

- Do not mirror Apache merge commits as-is.
- Rebuild changes into local commits grouped by feature or risk boundary.
- Every local commit must include:
  - change summary
  - source Apache commit SHA list
  - expected functional impact
  - minimum verification result
  - Europa cherry-pick notes
- If a conflict happens during Europa cherry-pick:
  - record the conflict file and conflict reason here
  - resolve from the Europa branch perspective while preserving the Apache fix intent
  - separate adaptation-only changes into follow-up commits when needed

## Batch Plan

| Batch | Theme | Source pattern / examples | Europa risk | Status |
| --- | --- | --- | --- | --- |
| B00 | Metadata / CI / docs housekeeping | `.asf.yaml`, `.github/*`, `README`, pre-commit, codespell | Low | Planned |
| B01 | Resource limits / quota / reservation | `[22.0]`, `[20.3]`, quota summary, secondary storage limits | High | Planned |
| B02 | Backup / volume / snapshot / import flows | backup, restore, import VM, storage pool, snapshot chain | High | Planned |
| B03 | Network / VPC / LB / NSX / VR | static route, HAProxy, load balancer, NSX, VPC cleanup | High | Planned |
| B04 | Hypervisor / KVM / VMware / CKS | NIC enable/disable, Headlamp, SharedMountPoint, migration | Medium | Planned |
| B05 | UI / UX / config defaults | UI bug fixes, default language, hidden settings | Medium | Planned |
| B06 | Async jobs / account / user / API ergonomics | async job query, API key restructure, account/domain safeguards | Medium | Planned |

## Commit Record Template

### Commit ID: `TBD`

- Local branch: `main` or `ablestack-europa`
- Local commit: `TBD`
- Source Apache commits:
  - `TBD`
- Summary:
  - `TBD`
- Functional impact:
  - `TBD`
- Validation:
  - `TBD`
- Europa cherry-pick status:
  - `Pending`
- Conflict notes:
  - `None`
- Resolution notes:
  - `None`

## Applied Records

### Record 001 - EL10 python six compatibility packaging fix

- Local branch: `main`
- Local commit: `a1e520cbbb`
- Source Apache commits:
  - `80ee7f183f` Fix six package incompatiblity with EL10 (#12799)
- Summary:
  - Add EL packaging requirements for `python3-six` and `python3-protobuf`
  - Bundle compatible `mysql_connector_python` wheels for both Python 3.6 and Python 3.8+
  - Install the matching wheel in `%post management` based on detected Python version
- Functional impact:
  - Prevent EL10 package installation/runtime issues caused by Python dependency mismatch
  - Preserve EL8 compatibility by keeping the Python 3.6-compatible connector path
- Validation:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - Staged diff only touches `packaging/el8/cloud.spec`
- Europa cherry-pick status:
  - `d1be005ab5`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 002 - xcpng integration test cleanup hardening

- Local branch: `main`
- Local commit: `993945b793`
- Source Apache commits:
  - `7cdcf571fa` Fix xcpng test failures (#12812)
- Summary:
  - Wrap zone, pod, and network preparation/cleanup flows in `try/finally`
  - Re-enable disabled resources even when intermediate test steps fail
  - Reduce cascading failures across integration test scenarios
- Functional impact:
  - No runtime product behavior change
  - Improves repeatability of xcpng-related integration tests by preventing leaked disabled resources
- Validation:
  - Apache patch applied cleanly on `main` with no manual conflict resolution
  - `python3 -m py_compile test/integration/component/maint/test_redundant_router_deployment_planning.py test/integration/smoke/test_public_ip_range.py`
- Europa cherry-pick status:
  - `6e0af4c808`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 003 - async jobs filtering by resource type without resource id

- Local branch: `main`
- Local commit: `d3e606c989`
- Source Apache commits:
  - `38abe2df0b` Allow list async jobs by resource type alone (#13011)
- Summary:
  - Allow `listAsyncJobs` to filter by `resourceType` without requiring `resourceId`
  - Only apply the `instanceUuid` filter when a valid `resourceId` is supplied
  - Clarify the validation error when `resourceId` is used without `resourceType`
- Functional impact:
  - Expands `listAsyncJobs` API usability for callers that want job lists for a resource class without a specific resource UUID
  - Prevents unnecessary validation failure when only `resourceType` is provided
- Validation:
  - Apache patch required manual conflict resolution on `main` because the surrounding `QueryManagerImpl` method had drifted
  - Planned verification: targeted `server` module compile
- Europa cherry-pick status:
  - `d8d95533d9`
- Conflict notes:
  - `main` lacked the exact Apache context block near the end of the async job search method, causing a patch context conflict
- Resolution notes:
  - Re-applied only the intended resource filter logic immediately before the existing `searchAndCount` call

### Record 004 - backup list keyword filter correction

- Local branch: `main`
- Local commit: `f9ac2c3d95`
- Source Apache commits:
  - `86c9f7bd94` Fix backup list
- Summary:
  - Keep backup name keyword filtering inside the existing `and` condition chain instead of opening a new `or` group
  - Preserve the `backupOfferingId` filter when listing backups with a keyword
- Functional impact:
  - Prevents `listBackups` keyword searches from returning rows that bypass the selected backup offering constraint
  - Narrows results to the intended offering-scoped backup set
- Validation:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to a single logical change in `BackupManagerImpl`
- Europa cherry-pick status:
  - `8d961d78f9`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 005 - countVgpuVMs prepared statement ordering fix

- Local branch: `main`
- Local commit: `6937fe8c06`
- Source Apache commits:
  - `6516f7f1aa` Fix query execution in countVgpuVMs (#12713)
- Summary:
  - Delay preparation and parameter binding of the second vGPU count query until after the legacy query has finished executing
  - Avoid mixing statement preparation/binding across the two query paths
- Functional impact:
  - Prevents erroneous query execution in `countVgpuVMs`
  - Improves correctness of aggregated vGPU VM counting used by scheduling or capacity-related paths
- Validation:
  - Apache patch applied cleanly on `main`
  - Cached diff is limited to prepared statement ordering changes in `VMInstanceDaoImpl`
- Europa cherry-pick status:
  - `866a23eb07`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 006 - storage pool reorder logging and random compatibility

- Local branch: `main`
- Local commit: `7aae7631fe`
- Source Apache commits:
  - `161b4177c2` Add logs for storage pools reordering (#10419)
- Summary:
  - Improve storage pool allocator logging around reordering, shuffle, disk provisioning, and search start/end
  - Treat `userconcentratedpod_random` the same as `random` in the volume allocation reorder path
- Functional impact:
  - Improves observability when debugging allocator decisions and storage pool ordering
  - Preserves random reordering behavior for configurations that still use `userconcentratedpod_random`
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main`
  - Verified the resolved method keeps the Apache condition for `userconcentratedpod_random` and the expanded logging changes
- Europa cherry-pick status:
  - `dfd87dee3b`
- Conflict notes:
  - `main` had diverged in `reorderStoragePoolsBasedOnAlgorithm`, where `userconcentratedpod_random` handling was missing and the log level differed
- Resolution notes:
  - Kept the Apache behavior by routing `userconcentratedpod_random` through the random reorder branch and preserving the newer logging

### Record 007 - managed storage restore host null guard

- Local branch: `main`
- Local commit: `23e971802a`
- Source Apache commits:
  - `84676afd5c` Check for null host before proceeding with VM volume operations in managed storage while restoring VM (#12879)
- Summary:
  - Guard managed-storage restore cleanup when the VM host lookup returns `null`
  - Skip detach/delete command construction instead of dereferencing a missing host
- Functional impact:
  - Prevents restore-time failures caused by null host dereference during managed storage volume handling
  - Allows the restore flow to exit this cleanup path safely when the previous host record is unavailable
- Validation:
  - Applied as a focused manual port on `main` to avoid unrelated formatting churn from the Apache patch
  - Logic inspected in `handleManagedStorage`
- Europa cherry-pick status:
  - `c444e0dfe3`
- Conflict notes:
  - `N/A on main`; only the functional null-host guard was ported
- Resolution notes:
  - Kept the Apache intent while limiting the local diff to the host-null safety check

### Record 008 - ACL metadata for backup-based restore and create APIs

- Local branch: `main`
- Local commit: `ada57be8e8`
- Source Apache commits:
  - `24fd440ee7` Fix create VM from backup
  - `8ce1c9876e` fix restore volume from backup and attach
- Summary:
  - Add `@ACL` metadata to `backupId` in `CreateVMFromBackupCmd`
  - Add `@ACL` metadata to `backupId`, `volumeUuid`, and `vmId` in `RestoreVolumeFromBackupAndAttachToVMCmd`
- Functional impact:
  - Improves API ACL enforcement and parameter-level access checks for backup-driven restore/create flows
  - Aligns backup resource parameters with existing ACL-aware command patterns
- Validation:
  - Both Apache patches applied cleanly on `main`
  - Cached diff is limited to ACL annotations and one import addition
- Europa cherry-pick status:
  - `0e0f11bf7e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 009 - block backup deletion during pending restore/create jobs

- Local branch: `main`
- Local commit: `30c0421416`
- Source Apache commits:
  - `7ba5240b31` Block backup deletion while create-VM-from-backup or restore jobs are in progress (#12792)
- Summary:
  - Check for pending async jobs tied to a backup before allowing deletion
  - Block deletion while create-from-backup or restore flows are still running
  - Add unit coverage for the pending-job rejection path
- Functional impact:
  - Prevents destructive races between backup deletion and active backup restore/create operations
  - Reduces the chance of partial restore/create failures caused by deleting the source backup mid-flight
- Validation:
  - `BackupManagerImpl` change applied cleanly on `main`
  - `BackupManagerTest` needed a small mock-field merge to accommodate the new `AsyncJobManager` dependency
- Europa cherry-pick status:
  - `8cda57843e`
- Conflict notes:
  - Test file context had diverged because local mocks already included `BackupOfferingDetailsDao` and `DomainHelper`
- Resolution notes:
  - Kept all existing mocks and added `AsyncJobManager` alongside them, then preserved the Apache pending-jobs test case

### Record 010 - preload backup architecture during create-from-backup

- Local branch: `main`
- Local commit: `93c5d9caa9`
- Source Apache commits:
  - `1ff9eec997` Load arch data for backup from template during create instance from backup (#12801)
- Summary:
  - Load the backup source template or ISO architecture before opening the create-from-backup flow
  - Pre-fill `selectedArchitecture` from backup metadata instead of resetting to the zone default
  - Pass the fetched backup architecture through `CreateVMFromBackup` into `DeployVMFromBackup`
- Functional impact:
  - Prevents backup-based instance creation from silently defaulting to the wrong architecture on multi-arch zones
  - Keeps create-from-backup requests aligned with the source template or ISO architecture during restore-driven provisioning
- Validation:
  - Applied cleanly on `main` with changes limited to `DeployVMFromBackup.vue` and `CreateVMFromBackup.vue`
  - Frontend build or lint verification has not been run in this environment yet
- Europa cherry-pick status:
  - `e086d987bd`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 011 - honor backup command timeout for NAS create and restore

- Local branch: `main`
- Local commit: `38800d1cb0`
- Source Apache commits:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- Summary:
  - Use `command.getWait()` as a millisecond timeout for NAS backup create and restore operations
  - Fall back to `commands.timeout` from `LibvirtComputingResource` when the command-specific wait is not set
  - Update restore-side unit mocks so `rsync` failures are asserted through the timeout-aware script path
- Functional impact:
  - Prevents NAS backup create and restore flows from timing out too early when backup operations legitimately run longer
  - Aligns KVM backup execution with the configured command timeout behavior already used by other libvirt command wrappers
- Validation:
  - `LibvirtTakeBackupCommandWrapper` and the restore tests applied cleanly on `main`
  - `LibvirtRestoreBackupCommandWrapper` required a manual port because the Apache patch was based on a newer block-device helper structure than the current branch
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `e6d0c25dba`
- Conflict notes:
  - Apache changed timeout handling inside a newer `replaceBlockDeviceWithBackup` flow, while this branch still keeps the older RBD-only restore helper
- Resolution notes:
  - Ported only the timeout semantics into the current helper layout: `rsync` now uses the timeout-aware script overload and `QemuImg` receives milliseconds directly without altering existing RBD restore behavior

### Record 012 - clear backup schedule references before schedule deletion

- Local branch: `main`
- Local commit: `656eeb1816`
- Source Apache commits:
  - `27e4d979f1` Clean up backup references to their schedules when the schedules are deleted (#12401)
- Summary:
  - Null out `backups.backup_schedule_id` before removing a backup schedule row
  - Move backup schedule response construction out of `BackupScheduleDaoImpl` and into `ApiResponseHelper`
  - Drop the unused `backup_interval_type` column from `cloud.backups`
- Functional impact:
  - Prevents deleted schedules from leaving stale schedule references behind on existing backups
  - Keeps backup schedule API responses working without coupling DAO code to VM lookup concerns
  - Removes an unused schema column so the backup table matches current runtime behavior
- Validation:
  - Java-side DAO and API response changes applied cleanly on `main`
  - The schema upgrade file required a manual merge because this branch does not yet include unrelated Apache `vm_template` updates that were present in the parent context of the patch
  - Database migration and Maven-based Java tests could not be run because no DB harness and no `mvn`/`mvnw` are available in this environment
- Europa cherry-pick status:
  - `f39f239fba`
- Conflict notes:
  - The Apache patch touched a shared schema upgrade file that has drifted on this branch due to missing earlier upstream statements
- Resolution notes:
  - Ported only the backup-schedule cleanup line into the local schema file and intentionally left unrelated `vm_template` update statements for their own upstream sync commits

### Record 013 - reserve backup and bucket limits during create and delete operations

- Local branch: `main`
- Local commit: `281cc87487`
- Source Apache commits:
  - `19b4ef1069` server: reserve backup, bucket resource limits during operations
- Summary:
  - Wrap backup create/delete resource checks with `CheckedReservation` for `backup` and `backup_storage`
  - Reserve `bucket` and `object_storage` limits during bucket allocation and deletion paths
  - Extend unit coverage for reservation-aware backup and bucket workflows
- Functional impact:
  - Prevents concurrent backup and bucket operations from passing limit checks and only failing after counters are updated too late
  - Keeps backup and object storage counters aligned with the actual success or failure of create/delete operations
- Validation:
  - `BucketApiServiceImpl` and its tests applied cleanly on `main`
  - `BackupManagerImpl` and `BackupManagerTest` required a manual merge because this branch already carries local backup safety changes around pending restore/create jobs
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `9e4148ab98`
- Conflict notes:
  - Backup manager create/delete paths overlapped with the locally added pending-job guard and related test scaffolding
- Resolution notes:
  - Preserved the local pending backup job protection and merged the Apache reservation-based limit checks around the same backup lifecycle methods

### Record 014 - review fixes for backup and bucket reservation flow

- Local branch: `main`
- Local commit: `ddbed8a9cf`
- Source Apache commits:
  - `13842a626d` Address reviews
- Summary:
  - Let bucket delete and backup delete paths propagate `ResourceAllocationException`
  - Simplify backup reservation error handling so scheduled-backup alerts trigger only for actual limit exceptions
  - Tighten retention-cleanup method signatures and related unit tests for the reservation-aware backup flow
- Functional impact:
  - Prevents reservation failures from being swallowed inside generic runtime exceptions on backup and bucket operations
  - Keeps scheduled backup limit alerts focused on real quota violations instead of unrelated runtime failures
- Validation:
  - All review follow-up files applied cleanly on `main` except `DeleteBucketCmd`
  - `DeleteBucketCmd` needed a small manual merge to keep the local event-detail formatting while adding `ResourceAllocationException` to the API contract
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `d09487d87e`
- Conflict notes:
  - Local `DeleteBucketCmd` event detail handling had diverged from the Apache parent context
- Resolution notes:
  - Kept the local `getResourceUuid(ApiConstants.ID)` event detail string and added the wider exception signature required by the reservation-aware delete flow

### Record 015 - validate bucket quota growth with reservations during update

- Local branch: `main`
- Local commit: `29ed88dcb8`
- Source Apache commits:
  - `2511fdffaa` Implement limit validations on updateBucket
- Summary:
  - Move bucket quota-delta handling into a dedicated `updateBucketQuota` helper
  - Use `CheckedReservation` when bucket quota increases so `object_storage` growth is reserved before counters are incremented
  - Keep quota decreases as immediate counter decrements and allocated-size adjustments
- Functional impact:
  - Closes the remaining gap where `updateBucket` could increase object-storage quota without reservation-aware limit protection
  - Makes bucket quota updates consistent with the create/delete reservation model introduced in the previous two commits
- Validation:
  - Applied cleanly on `main`
  - Logic review confirms quota increases now reserve `object_storage` before incrementing counts
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `3a96b973dc`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 016 - enforce secondary storage limits during download flows

- Local branch: `main`
- Local commit: `0ecd207c64`
- Source Apache commits:
  - `03dfe4d1f3` secondary storage resource limit for download
- Summary:
  - Track `LIMIT_REACHED` as a first-class download error state and stop persisting bogus size values for failed downloads
  - Use actual downloaded bytes as the fallback template size signal during secondary storage download progress
  - Recalculate secondary storage counts after template registration callbacks so final counters match persisted store-ref state
- Functional impact:
  - Prevents template and volume downloads from silently overrunning secondary storage limits during in-progress updates
  - Keeps template store size accounting consistent when download answers only report physical size or fail due to limit exhaustion
- Validation:
  - All download-state, resource-limit, and secondary-storage manager files applied cleanly on `main`
  - `HypervisorTemplateAdapter` required a manual merge because the local branch still used an older callback structure that increments counts before recalculation
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `9db209ed17`
- Conflict notes:
  - The template callback logic in `HypervisorTemplateAdapter` had diverged around when secondary storage counts are incremented versus recalculated
- Resolution notes:
  - Preserved the local usage-event and increment flow, then added the Apache post-callback secondary-storage recalculation in the current method layout

### Record 017 - enforce secondary storage limits during upload flows

- Local branch: `main`
- Local commit: `20103f019d`
- Source Apache commits:
  - `81a8ac8e1f` secondary storage resource limit for upload
- Summary:
  - Add abort-aware upload status polling so the management server can stop uploads after limit failures
  - Reserve `secondary_storage` usage during template and volume upload progress updates
  - Keep upload channels and SSVM-side state in sync when uploads are aborted or fail due to limit exhaustion
- Functional impact:
  - Prevents template and volume uploads from continuing after the management server detects secondary storage quota exhaustion
  - Makes upload-side secondary storage accounting consistent with the download-side reservation flow
- Validation:
  - Upload command, SSVM resource handler, and secondary-storage resource changes applied cleanly on `main`
  - `ImageStoreUploadMonitorImpl` required a manual merge because the current branch did not yet carry the reservation/account helper dependencies used by the Apache upload monitor changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `a411135d0f`
- Conflict notes:
  - Upload monitor imports and injected collaborators had diverged from the Apache parent context
- Resolution notes:
  - Added the Apache reservation/account helper dependencies into the current upload monitor and kept the Apache abort-aware polling flow intact

### Record 018 - follow up secondary storage limit review fixes

- Local branch: `main`
- Local commit: `28548c2c7f`
- Source Apache commits:
  - `23b19a9776` review comments
- Summary:
  - Fix null-size and null-account edge cases in download and upload limit updates
  - Add size guards around template copy reservations so secondary storage reservations are only created when template size is known
  - Carry the same Apache review cleanup for project reservation code paths that live in the same upstream commit
- Functional impact:
  - Prevents limit-check helpers from miscounting when DB size fields or owner lookups are temporarily null
  - Avoids unnecessary reservation attempts for zone copy operations when template size has not been resolved yet
- Validation:
  - `DownloadListener` and `ImageStoreUploadMonitorImpl` review fixes applied cleanly on `main`
  - `ProjectManagerImpl` and `TemplateManagerImpl` required manual merges because the current branch had older pre-review reservation blocks in those exact sections
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `6f997e5271`
- Conflict notes:
  - Project ownership transfer and template cross-zone copy logic had drifted around reservation blocks since the Apache review commit was authored
- Resolution notes:
  - Preserved the current branch control flow, then folded in the Apache reservation guards and null-safe helper fixes without broad structural rewrites

### Record 019 - guard snapshot copy reservations against concurrency races

- Local branch: `main`
- Local commit: `dad19a2215`
- Source Apache commits:
  - `8608b4edd0` Fix snapshot copy resource limit concurrency
- Summary:
  - Wrap snapshot copy-to-zone flow with `CheckedReservation` instead of a separate pre-check
  - Pass an explicit `shouldCheckResourceLimits` flag so snapshot-chain copies do not double-reserve secondary storage
  - Update snapshot copy tests to reflect reservation-based behavior instead of direct `checkResourceLimit` mocking
- Functional impact:
  - Prevents concurrent snapshot copy operations from passing standalone checks and then racing on secondary storage quota updates
  - Keeps snapshot copy reservations aligned with the real copy lifecycle, including chain-copy and KVM incremental snapshot cases
- Validation:
  - Applied cleanly on `main`
  - Snapshot manager and snapshot copy test updates are limited to reservation flow and test expectation changes
  - Maven-based Java test execution could not be run because `mvn`/`mvnw` are not available in this environment
- Europa cherry-pick status:
  - `06ae354d0b`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 020 - reserve start VM limits with host-tag aware reservations

- Local branch: `main`
- Local commit: `46107a4db7`
- Source Apache commits:
  - `4bcd509193` Fix resource limit reservation and check during StartVirtualMachine
- Summary:
  - Extract the deployment-heavy portion of `startVirtualMachine` into a helper so reservation handling wraps only the limit-sensitive path
  - Replace standalone `checkVmResourceLimit` usage with `CheckedReservation` blocks for `user_vm`, `cpu`, and `memory` during running-only resource counting
  - Preserve current branch GPU accounting by reserving `gpu` resources when the service offering declares GPU capacity
- Functional impact:
  - Prevents concurrent `StartVirtualMachine` requests from passing a pre-check and then overshooting runtime VM resource limits during actual deployment
  - Aligns host-tag-aware start reservations with the same tagged resource accounting already used by deploy and destroy flows
  - Keeps GPU quota behavior symmetric on this branch so start reservations do not weaken existing GPU limit enforcement
- Validation:
  - `UserVmManagerImpl` is the only touched source file on `main`
  - The Apache patch required a manual merge because the local branch still carried the monolithic start method and a different VM details DAO field name
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `5c493d982b`
- Conflict notes:
  - The Apache patch split `startVirtualMachine` into a helper while the local branch still had the older inline deployment flow, so the full method body conflicted
  - The extracted helper referenced `userVmDetailsDao`, but this branch uses `vmInstanceDetailsDao`
  - The pre-existing local `checkVmResourceLimit` path already covered GPU limits, while the Apache reservation patch only reserved VM, CPU, and memory
- Resolution notes:
  - Kept the Apache helper extraction and reservation structure, then adapted the helper to the local DAO field name
  - Added a conditional GPU reservation to preserve the branch's existing resource-limit coverage and keep start/destroy accounting behavior symmetric

### Record 021 - reserve extension-managed resource details

- Local branch: `main`
- Local commit: `7a5a33cbe7`
- Source Apache commits:
  - `95816b44e9` extensions: allow reserved resource details
- Summary:
  - Add `reservedresourcedetails` support to create/update/list extension API and UI flows so operators can declare extension-owned VM detail keys
  - Persist reserved detail names in extension hidden details, including built-in defaults for matching in-built extensions such as Proxmox
  - Extend VM detail filtering and VM update validation so non-admin users cannot view or mutate extension-reserved detail keys on extension-backed instances
  - Expose template extension linkage in `user_vm_view` and `UserVmJoinVO` so response filtering can decide which extension reservations apply
- Functional impact:
  - Lets extension authors reserve metadata keys that must stay under extension control instead of being visible or editable by tenants
  - Prevents end-user VM detail APIs and responses from leaking or overwriting extension-managed identifiers such as hypervisor-side instance metadata
  - Surfaces the reserved-detail configuration through the extension admin UI and API so the policy is manageable without direct DB edits
- Validation:
  - Most Apache files applied cleanly on `main`, including API constants, extension manager, schema view, and UI changes
  - `UserVmJoinDaoImpl` and `UserVmJoinDaoImplTest` required a manual merge because this branch already carried deploy-as-is response handling through `VMTemplateDao`
  - Maven and UI build execution have not been run yet in this environment by request
- Europa cherry-pick status:
  - `99459f7c42`
- Conflict notes:
  - `UserVmJoinDaoImpl` already injected `VMTemplateDao` for deploy-as-is allowed-details handling in the same field block where Apache added `ExtensionHelper`
  - The corresponding unit test mock block diverged for the same reason
- Resolution notes:
  - Kept the existing deploy-as-is response behavior and injected `ExtensionHelper` alongside `VMTemplateDao`
  - Preserved the Apache reserved-detail filtering logic without changing the branch-specific allowed-details response path

### Record 022 - harden PVLAN VM setup against null inputs

- Local branch: `main`
- Local commit: `613fcd2a5b`
- Source Apache commits:
  - `e10c066cc1` Fix NPE during VM setup for pvlan
- Summary:
  - Guard `setupVmForPvlan` against null NIC profiles and null broadcast URIs before dereferencing them
  - Skip PVLAN setup cleanly when the broadcast URI scheme is not `pvlan`
  - Return early when the target host lookup fails instead of continuing into agent command construction
- Functional impact:
  - Prevents VM deploy/start/stop paths from failing with a null-pointer exception when PVLAN metadata is incomplete or absent
  - Turns bad or missing PVLAN state into an explicit skip path with diagnostic logging, which is safer for mixed network environments
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `UserVmManagerImpl.setupVmForPvlan`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `ff60935d82`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 023 - validate VM compute details only when the offering allows it

- Local branch: `main`
- Local commit: `9a28b608bc`
- Source Apache commits:
  - `c6936889f5` server: prevent adding vm compute details when not applicable
- Summary:
  - Tighten `validateCustomParameters` so empty custom-parameter maps only fail for dynamic offerings, not for fixed offerings
  - Use `isCustomCpuSpeedSupported()` when validating CPU speed overrides and surface a clearer fixed-speed error message
  - Reject CPU, memory, and CPU speed detail updates up front in `verifyVmLimits` when the current offering is not dynamic
  - Add unit coverage for fixed-offering rejection and constrained custom-offering CPU speed validation
- Functional impact:
  - Prevents update flows from silently treating fixed offerings like custom offerings when VM compute detail keys are present
  - Stops non-applicable VM compute detail writes earlier, with more accurate error messages for operators and API callers
  - Keeps dynamic offering validation aligned with the offering's real CPU-speed customization capability on this branch
- Validation:
  - `UserVmManagerImpl` picked up the functional change on `main`
  - `UserVmManagerImplTest` and `KVMGuruTest` required manual merge because this branch already had additional test scaffolding and uses `isLimitCpuUse()` in the KVM guru path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f89d3bf2f0`
- Conflict notes:
  - `UserVmManagerImpl` import blocks diverged because the local branch already carried lease and snapshot-policy support alongside newer Apache scheduling and reservation imports
  - `KVMGuruTest` conflicted because upstream expected `getLimitCpuUse()` while this branch still exercises `isLimitCpuUse()`
  - `UserVmManagerImplTest` had a long trailing test block unique to this branch, so the upstream helper tests for dynamic offering validation landed inside an end-of-file conflict
- Resolution notes:
  - Kept the Apache validation logic, but preserved local imports needed by lease and snapshot-policy features
  - Retained the Apache config-key preservation in `KVMGuruTest` while binding it to the branch's `isLimitCpuUse()` API
  - Kept all existing local tests and appended the new dynamic-offering validation cases after the current tail section

### Record 024 - fix template type handling during ISO upload

- Local branch: `main`
- Local commit: `662c97af3c`
- Source Apache commits:
  - `c3d6a8cff7` server: fix templatetype during iso upload
- Summary:
  - Treat `GetUploadParamsForIsoCmd` as a user-template upload path so template-type validation returns `TemplateType.USER`
  - Switch the user-VM system-template guard to `TemplateType.SYSTEM.equals(...)` to avoid dereferencing a null template type
- Functional impact:
  - Prevents ISO upload-parameter requests from being misclassified as unsupported template-type operations
  - Removes a null-sensitive comparison in the user VM deploy path, making template-type validation safer when template metadata is incomplete
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `TemplateManagerImpl.validateTemplateType` and the system-template gate in `UserVmManagerImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 025 - default upload template types and backfill null template records

- Local branch: `main`
- Local commit: `ad19bfc1db`
- Source Apache commits:
  - `470812100e` server: set template type to ROUTING or USER if template type is not specified when upload a template
- Summary:
  - Default `GetUploadParamsForTemplateCmd` uploads to `ROUTING` or `USER` when `templatetype` is omitted, using the existing `isrouting` flag
  - Reject non-admin upload-parameter requests for non-user template types with a specific API error
  - Backfill `cloud.vm_template.type` from `NULL` to `USER` in the 4.22.0 to 4.22.1 schema migration
- Functional impact:
  - Makes template upload-parameter requests behave like template registration, so omitted `templatetype` values no longer fall through as `null`
  - Prevents inconsistent permission handling between upload-parameter generation and later template registration paths
  - Reduces runtime ambiguity for older template rows that still carry a null `type`
- Validation:
  - `TemplateManagerImpl` picked up the functional change on `main`
  - `schema-42200to42210.sql` required a manual merge because this branch already dropped `backup_interval_type` in the same tail section
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - The schema migration tail diverged on `main` because local backup cleanup SQL replaced the upstream context around the new `vm_template.type` backfill
- Resolution notes:
  - Kept the local `backup_interval_type` drop and added only the Apache null-type backfill, without reintroducing unrelated upstream migration context

### Record 026 - show full network offering labels in add-tier dropdown

- Local branch: `main`
- Local commit: `745a6c679f`
- Source Apache commits:
  - `120a43648b` set width of dropdown select items for Network Offering during add tier dialog
- Summary:
  - Add a `title` attribute to network offering select options in `VpcTiersTab.vue`
  - Reformat the option markup for readability while keeping the same displayed label text
- Functional impact:
  - Lets operators see the full network offering label on hover when the add-tier dropdown truncates long entries
  - Improves network offering selection confidence in crowded UI environments without changing API or backend behavior
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/VpcTiersTab.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 027 - guard create-network global action when zone context is absent

- Local branch: `main`
- Local commit: `03c4c21aba`
- Source Apache commits:
  - `db83622956` ui: fix create network from global create menu
- Summary:
  - Use optional chaining when reading `resource.zoneid` in `CreateNetwork.vue`
  - Keep the zone filter only for deploy-VM and backup entry points, but avoid dereferencing an absent `resource`
- Functional impact:
  - Prevents the global create-network action from throwing when it is opened without a preselected zone context
  - Keeps zone-scoped behavior unchanged for deploy and backup entry paths while making the generic menu entry safer
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/network/CreateNetwork.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 028 - route template-zone deletion back to the template list

- Local branch: `main`
- Local commit: `dd51cb66fa`
- Source Apache commits:
  - `7aa0558c5b` ui: avoid 404 after deleting template zones
- Summary:
  - Redirect `TemplateZones.vue` to `/template` when the delete flow leaves the current detail view without remaining rows
  - Keep the surrounding table and modal behavior unchanged while avoiding a dead back-navigation path
- Functional impact:
  - Prevents the UI from landing on a stale detail route after the last template-zone entry is removed
  - Gives operators a predictable post-delete destination instead of depending on browser history state
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/image/TemplateZones.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 029 - show security group selection for Basic zones and owner-scoped SG lists

- Local branch: `main`
- Local commit: `6efdb2a2cb`
- Source Apache commits:
  - `71daf84c9e` Show security group selection in Basic zone VM deployment and fix SG listing for cross-domain deployments
- Summary:
  - Always show the security-group step for Basic zones in `DeployVM.vue`
  - Pass the selected owner context (`domainId`, `account`, `projectId`) into `SecurityGroupSelection.vue`
  - Refresh the listed security groups when the owner context changes, and query `listSecurityGroups` with that owner context instead of always using the current session defaults
- Functional impact:
  - Restores security-group selection during VM deployment in Basic zones where the UI previously hid the step
  - Prevents cross-domain deployments from showing the wrong security-group list when the VM owner differs from the logged-in operator
  - Clears stale security-group selections when the deployment owner changes, reducing accidental carry-over between accounts or projects
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `ui/src/views/compute/DeployVM.vue` and `ui/src/views/compute/wizard/SecurityGroupSelection.vue`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 030 - allow configurable default UI language

- Local branch: `main`
- Local commit: `824130f850`
- Source Apache commits:
  - `ed575cc0a1` New config.json variable to set the ACS default language
- Summary:
  - Allow `defaultLanguage` as a GUI theme primitive property and add the sample key to `ui/public/config.json`
  - Use `defaultLanguage` when initializing `TranslationMenu.vue` if the user has no saved `LOCALE`
  - Let `guiTheme.js` propagate a theme- or config-provided default language into runtime config and local storage, then load that language pack
- Functional impact:
  - Makes the initial UI locale configurable through static config and dynamic GUI theme customization, instead of hard-coding a single fallback in the header component
  - Aligns first-load language selection across login, theme application, and later locale switches
  - Gives operators a supported way to preseed the portal language for new browsers or cleared storage sessions
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `GuiThemeServiceImpl`, `ui/public/config.json`, `TranslationMenu.vue`, and `ui/src/utils/guiTheme.js`
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 031 - block account and domain deletion when delete-protected VMs remain

- Local branch: `main`
- Local commit: `3883bfda42`
- Source Apache commits:
  - `b196e97cc3` Prevent deletion of account and domain if either of them has deleted protected instance
- Summary:
  - Add DAO helpers to find active delete-protected VMs by account and by a set of domain IDs
  - Validate account deletion in `AccountManagerImpl` before destructive cleanup starts
  - Validate domain deletion in `DomainManagerImpl` across the full domain hierarchy, including child domains
  - Extend `DomainManagerImplTest` stubs so delete-domain tests continue to model an empty delete-protected VM result set
- Functional impact:
  - Prevents operators from deleting an account or domain while delete-protected instances still exist beneath it
  - Makes delete protection effective beyond direct VM delete calls by enforcing the same guard on higher-level ownership cleanup paths
  - Limits false positives to active VMs only by filtering out removed instances in the DAO layer
- Validation:
  - Applied cleanly on `main`
  - The change is limited to `VMInstanceDao`, `VMInstanceDaoImpl`, `AccountManagerImpl`, `DomainManagerImpl`, and `DomainManagerImplTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `None yet`

### Record 032 - unhide and centralize JavaScript interpretation gating

- Local branch: `main`
- Local commit: `ef05a90e62`
- Source Apache commits:
  - `9f57a4dd19` Unhide setting `js.interpretation.enabled`
- Summary:
  - Move `js.interpretation.enabled` ownership from `ManagementService` into `JsInterpreterHelper` as a normal configurable system setting
  - Unhide the setting during the `4.22.1.0 -> 4.23.0.0` upgrade by migrating its stored value, category, component, and dynamic flag
  - Replace scattered `ManagementService`/`QuotaService` checks with `JsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` in host, storage, secondary-storage selector, and quota tariff flows
  - Always register the secondary storage selector commands, but gate their JS-bearing parameters at validation time instead of hiding the commands entirely
- Functional impact:
  - Makes `js.interpretation.enabled` visible and manageable as a standard system setting instead of a hidden internal knob
  - Centralizes enable/disable enforcement for JS-backed parameters, reducing drift between quota, host-tag, storage-pool, and selector validation paths
  - Preserves safety when JS interpretation is disabled by rejecting only the parameters that require it, rather than removing whole APIs from discovery
- Validation:
  - `QuotaResponseBuilderImpl` required a manual merge on `main` because this branch still had the older `_quotaService.isJsInterpretationEnabled()` activation-rule guard in the same field block where Apache now keeps the quota-summary role set
  - The final `main` state uses `jsInterpreterHelper` for activation-rule validation and removes the old quota-service helper path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `QuotaResponseBuilderImpl` conflicted on `main` because the local branch still carried `checkActivationRulesAllowed()` and `_quotaService.isJsInterpretationEnabled()`-based gating
- Resolution notes:
  - Dropped the old quota-service activation-rule helper and kept the Apache `jsInterpreterHelper.ensureInterpreterEnabledIfParameterProvided(...)` checks as the single validation path

### Record 033 - support async job lookup by resource

- Local branch: `main`
- Local commit: `893222e873`
- Source Apache commits:
  - `47c5bb8ee7` Support list/query async jobs by resource (#12983)
- Summary:
  - Add `resourceId` and `resourceType` filters to `listAsyncJobs` and `queryAsyncJobResult`
  - Make `ApiCommandResourceType.fromString(...)` case-insensitive for resource-driven async job lookups
  - Add `ResourceIdSupport` to centralize resource UUID parsing, resource-type validation, and access checks for async job resource filters
  - Extend `AsyncJobDao` and `AsyncJobDaoImpl` so async jobs can be resolved by either job id or `(resource type, resource id)`
- Functional impact:
  - Allows operators to locate async jobs even when they only know the backing resource UUID and type, not the async job UUID
  - Makes resource-type matching more tolerant of casing differences in API clients
  - Preserves the earlier local `Record 003` behavior that allows `listAsyncJobs` to filter by `resourceType` alone without requiring `resourceId`
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `QueryManagerImpl` because this branch already carries the `Record 003` extension that permits `resourceType`-only async job listing
  - `ApiResponseHelper` merged cleanly after reconciling import drift and retaining the local `Site2SiteVpnManager` injection block
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `QueryManagerImpl` conflicted on `main` because Apache now expects `resourceType` and `resourceId` together for list filtering, while this branch intentionally already supports `resourceType` without `resourceId`
  - `ApiResponseHelper` conflicted on `main` in the import/injection region due unrelated local drift near the same hunk
- Resolution notes:
  - Kept the Apache `queryAsyncJobResult`, DAO lookup, case-insensitive resource-type parsing, and shared resource helper changes
  - Preserved the local `listAsyncJobs` type-only filter so this sync does not silently regress `Record 003`

### Record 034 - remove unused console proxy command port config

- Local branch: `main`
- Local commit: `f0f0218dcb`
- Source Apache commits:
  - `feb6076930` Remove unused config consoleproxy.cmd.port (#12807)
- Summary:
  - Remove the unused `ConsoleProxyCmdPort` config key from `ConsoleProxyManager`
  - Stop exposing `consoleproxy.cmd.port` through `ConsoleProxyManagerImpl.getConfigKeys()`
  - Delete stale `consoleproxy.cmd.port` rows during the `4.22.0.0 -> 4.22.1.0` schema upgrade
- Functional impact:
  - Removes an unused console proxy setting from the surfaced configuration set
  - Cleans obsolete configuration data during upgrade without changing active console proxy behavior
- Validation:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already re-ordered nearby upgrade statements for `backup_interval_type` removal and `vm_template.type` backfill
  - The resolved schema keeps the local statement order and adds only the `consoleproxy.cmd.port` cleanup delete
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because Apache inserts the config cleanup next to statements this branch already moved and de-duplicated earlier
- Resolution notes:
  - Preserved the local upgrade order, avoided duplicating the existing `backups.backup_interval_type` drop, and inserted only the new configuration cleanup

### Record 035 - update password reset mail template default value

- Local branch: `main`
- Local commit: `21c8b313df`
- Source Apache commits:
  - `5013cf2af6` Fix user password reset mail template value (#12882)
- Summary:
  - Update the `user.password.reset.mail.template` upgrade SQL to the new `{{{resetLink}}}` format
  - Migrate only legacy template values that still use the old `http://{{{resetLink}}}` or `{{{domainUrl}}}{{{resetLink}}}` placeholders
  - Use `CONCAT_WS('\n', ...)` so the stored template matches the multiline string expected by the newer password reset flow
- Functional impact:
  - Aligns upgraded deployments with the current password reset mail rendering logic
  - Avoids leaving stale default template values that generate the wrong reset URL in notification emails
- Validation:
  - Apache cherry-pick required a manual merge on `main` only in `schema-42200to42210.sql` because this branch already carries later upgrade statements in the same tail region
  - The resolved schema keeps the existing `backup_interval_type` removal and `consoleproxy.cmd.port` cleanup, then appends only the password reset template update block
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because Apache still includes an adjacent `backup_interval_type` drop that is already present in this branch
- Resolution notes:
  - Reused the existing schema tail order and inserted only the new template migration SQL to avoid duplicate DDL

### Record 036 - allow service offering lookup across cluster host tags

- Local branch: `main`
- Local commit: `75874d825d`
- Source Apache commits:
  - `b5858029bb` Fix listing service offerings with different host tags (#12919)
- Summary:
  - Add `HostTagsDao.listByClusterId(...)` so service offering search can inspect all host tags defined on hosts inside a VM's current cluster
  - Introduce `allow.different.host.tags.offerings.for.vm.scale` and register it through `UserVmManagerImpl.getConfigKeys()`
  - When the new setting is enabled, extend scale-offering host-tag matching to include any tag found in the VM's current cluster instead of requiring the current offering's exact tag set
  - Surface `hosttags` and `storagetags` columns in the compute offering wizard when the offering payload includes those fields
- Functional impact:
  - Lets operators list compatible target offerings for VM scale even when the offering uses a different host-tag subset that is still valid within the VM's cluster
  - Keeps the previous strict host-tag behavior by default unless the new advanced setting is enabled
  - Makes host and storage tag differences visible in the UI during compute offering selection
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The change is limited to host-tag DAO/query plumbing, one new config key registration, two focused `QueryManagerImplTest` cases, and compute-offering wizard column rendering
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 037 - exclude group snapshots from account snapshot resource counts

- Local branch: `main`
- Local commit: `97a11d6e18`
- Source Apache commits:
  - `7b467496cb` Do not include snapshots with Group type in snapshots resource count (#12945)
- Summary:
  - Exclude `Snapshot.Type.GROUP` entries from `CountSnapshotsByAccount`
  - Apply the exclusion only to snapshot resource counting, leaving the rest of snapshot DAO behavior untouched
- Functional impact:
  - Prevents group snapshots from inflating per-account snapshot resource counts
  - Aligns snapshot quota/accounting behavior with the expectation that grouped snapshots should not be counted like regular per-volume snapshots
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the `CountSnapshotsByAccount` search builder and `countSnapshotsForAccount(...)`
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 038 - avoid KVM domain lookup for non-running VM checks

- Local branch: `main`
- Local commit: `78a25f2a85`
- Source Apache commits:
  - `273699cf56` kvm: fix wrong CheckVirtualMachineAnswer when vm does not exist (#12928)
- Summary:
  - Only call `domainLookupByName(...)` when the VM power state is `PowerOn`
  - Preserve the paused-domain special case for powered-on VMs, but avoid libvirt domain inspection for powered-off or missing VMs
  - Add focused wrapper tests that cover running, paused, powered-off, unknown-state, null-VNC, and libvirt-exception paths
- Functional impact:
  - Prevents `CheckVirtualMachineCommand` from returning the wrong answer path when the VM no longer exists in libvirt
  - Reduces false failures for non-running KVM VMs by skipping unnecessary domain lookup
  - Improves regression coverage for the wrapper's error handling and paused-VM behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the KVM check wrapper and its new unit test class
- Europa cherry-pick status:
  - `Pending cherry-pick on ablestack-europa`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 039 - rollback disk snapshots on VM snapshot failure

- Local branch: `main`
- Local commit: `d1ebe5062b`
- Source Apache commits:
  - `d75acb6efc` Fix rollback disk snapshots on instance snapshot failure (#12949)
- Summary:
  - Add each created disk snapshot to the rollback list before invoking the snapshot strategy
  - Guard rollback cleanup against `null` `SnapshotInfo` and missing `SnapshotVO` rows
  - Keep the VM snapshot unit test aligned with the renamed rollback list parameter
- Functional impact:
  - Prevents partially created per-volume snapshots from being left behind when a later VM snapshot disk step fails
  - Makes rollback cleanup tolerant of partially persisted or already-removed snapshot metadata during failure handling
  - Reduces orphaned snapshot state in KVM VM snapshot error paths
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `51d1aa5bb6`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 040 - include hidden image-store refs when resolving Xen snapshot chains

- Local branch: `main`
- Local commit: `bb635f652f`
- Source Apache commits:
  - `2a60305792` Fix snapshot chaining on Xen (#12597)
- Summary:
  - Add a DAO method that lists snapshot-store refs by snapshot id, role, and a set of states
  - Update `DefaultSnapshotStrategy.getSnapshotImageStoreRef(...)` to consider both `Ready` and `Hidden` image-store refs
  - Align the unit test with the new DAO method and remove the redundant null-path stubbing
- Functional impact:
  - Preserves Xen incremental snapshot chain lookup even when a parent snapshot is hidden on secondary storage
  - Reduces the chance of losing the expected parent chain and falling back to an incorrect full backup path
  - Keeps snapshot image-store lookup limited to the target zone while widening the acceptable persisted states
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SnapshotDataStoreDaoImpl` because this branch already carried a local `idStateNeqSearch` builder for non-destroyed snapshot lookups
  - The resolved DAO keeps the local `idStateNeqSearch` behavior and adds Apache's `idEqRoleEqStateInSearch` path for `Ready`/`Hidden` image-store lookup
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `bae63ac800`
- Conflict notes:
  - `SnapshotDataStoreDaoImpl` conflicted on `main` because Apache adds a new state-in search builder in the same initialization block where this branch already introduced `idStateNeqSearch`
- Resolution notes:
  - Preserved the local `idStateNeqSearch` initialization and added the Apache `idEqRoleEqStateInSearch` builder without changing the branch-local non-destroyed lookup behavior

### Record 041 - pass snapshot CPG on Primera online copy

- Local branch: `main`
- Local commit: `e5e4e63261`
- Source Apache commits:
  - `8f3c6fad7a` set snapcpg config on copy (#12955)
- Summary:
  - Set `snapCpg` on Primera online copy parameters alongside the destination CPG
  - Leave the existing online copy behavior unchanged apart from propagating the configured snapshot CPG
- Functional impact:
  - Ensures Primera online copy operations inherit the configured snapshot CPG instead of relying only on the destination CPG
  - Reduces the risk of copy-time snapshot placement drifting from the storage policy expected by the Primera backend
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `parms.setSnapCPG(snapCpg)` line in `PrimeraAdapter`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `0a94c732c0`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 042 - scope persistent network lookup by zone

- Local branch: `main`
- Local commit: `2b5d1dc0d4`
- Source Apache commits:
  - `b805766f4b` Fix Host setup when persistent networks exist (#12751)
- Summary:
  - Add the data center filter to `PersistentNetworkSearch` in `NetworkDaoImpl`
  - Align the search builder with the existing `getAllPersistentNetworksFromZone(...)` parameter binding
- Functional impact:
  - Prevents persistent network discovery for host setup from matching networks that share attributes across different zones
  - Reduces the risk of reusing or counting persistent networks outside the requested data center when broadcast URI values overlap
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a single `PersistentNetworkSearch.and(\"dc\", ...)` addition in `NetworkDaoImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `64c919e65c`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 043 - compare NSX NAT delete service by name

- Local branch: `main`
- Local commit: `b078081e41`
- Source Apache commits:
  - `30dd234b00` fix: NsxResource.executeRequest DeleteNsxNatRuleCommand comparison bug (#12833)
- Summary:
  - Add `getNetworkServiceName()` to `DeleteNsxNatRuleCommand`
  - Compare NSX NAT delete service selection by service name instead of object identity
  - Add a focused test that verifies the Port Forwarding delete path reaches the expected NSX API call
- Functional impact:
  - Prevents NSX NAT rule deletion from missing the correct branch when the command carries an equivalent service object rather than the same enum instance
  - Improves reliability of Port Forwarding and Static NAT rule cleanup in NSX-backed networks
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the NAT delete command, `NsxResource`, and one focused `NsxResourceTest`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 7ad9fbc1f0`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 044 - allow established and related traffic in routed VR forward chain

- Local branch: `main`
- Local commit: `166bb4304f`
- Source Apache commits:
  - `1fc4cb90bf` Routed VR: accept packets from related and established connections (#12986)
- Summary:
  - Add an nftables `ct state established,related accept` rule when creating `forward` chains in `CsNetfilter`
  - Leave the existing input/output ICMP allowance behavior unchanged
- Functional impact:
  - Prevents routed VR forward chains from dropping reply traffic that belongs to already established or related connections
  - Improves flow continuity for routed guest traffic without widening new-connection exposure
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to a 2-line `CsNetfilter.py` change in the `forward` hook path
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 5681e66c42`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 045 - validate zone local-storage enablement before creating file-based pools

- Local branch: `main`
- Local commit: `3c639d46fd`
- Source Apache commits:
  - `d38c1f8d12` Fix error message while creating local storage pool (#12767)
- Summary:
  - Reuse `isLocalStorageEnabledForZone(...)` when creating local storage
  - Reject file-scheme storage pool creation early when local storage is disabled for the zone
  - Replace duplicated zone-level local-storage checks with the shared helper
- Functional impact:
  - Returns a clearer validation failure when a local/file-backed primary storage pool is requested in a zone where local storage is disabled
  - Prevents the create-pool flow from reaching a later, less accurate error path
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `StorageManagerImpl` because this branch already imports `ArrayUtils` in the same block where Apache adds `BooleanUtils`
  - The resolved file keeps the branch-local imports and preserves Apache's `isLocalStorageEnabledForZone(...)` guard in the create-pool path
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 6fadfd9913`
- Conflict notes:
  - `StorageManagerImpl` conflicted on `main` only in the import section because the branch already had nearby local import additions
- Resolution notes:
  - Kept the existing `ArrayUtils` import and added Apache's `BooleanUtils` import so the shared helper and the local code both compile cleanly

### Record 046 - enable default SystemVM template registration on 4.20.2 -> 4.20.3 upgrade

- Local branch: `main`
- Local commit: `3422f7d5da`
- Source Apache commits:
  - `e2497cfc4d` backport: default system vm template update implementation (#12935)
- Summary:
  - Remove the empty `updateSystemVmTemplates(...)` override from `Upgrade42020to42030`
  - Let the upgrade path inherit the shared default implementation from `DbUpgradeSystemVmTemplate`
- Functional impact:
  - Restores automatic SystemVM template lookup/registration during the 4.20.2.0 -> 4.20.3.0 upgrade path
  - Prevents the upgrade from silently skipping the default SystemVM template update logic for that release jump
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The branch already carried the interface-side default implementation, so the net change is the removal of the stale no-op override in `Upgrade42020to42030`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a51d5eb639`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 047 - register new SystemVM templates for non-KVM hypervisors with amd64 arch

- Local branch: `main`
- Local commit: `76f1194d8c`
- Source Apache commits:
  - `6f1aa96b4c` engine/schema: fix new systemvm template is not registered during upgrade if hypervisor is not KVM (#12952)
- Summary:
  - Assign `CPU.CPUArch.amd64` to non-KVM hypervisors in `SystemVmTemplateRegistration.hypervisorList`
  - Update the registration test so VMware template metadata is expected with `amd64` instead of a null/default arch assumption
- Functional impact:
  - Prevents upgrade-time SystemVM template registration from skipping VMware, XenServer, Hyper-V, LXC, and OVM3 entries because their architecture was previously unspecified
  - Makes the registration map deterministic for non-KVM hypervisors
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `SystemVmTemplateRegistrationTest` because this branch already refactored the test to call `getMetadataTemplateDetails(...)` directly
  - The resolved test keeps the branch-local helper call and adopts Apache's explicit `amd64` expectation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f0af890272 before backfilling local/main`
- Conflict notes:
  - `SystemVmTemplateRegistrationTest` conflicted on `main` because the local branch had already changed the VMware metadata lookup helper call while Apache updates the expected arch in the same assertion block
- Resolution notes:
  - Preserved the branch-local helper-based test structure and updated the asserted VMware arch to `CPU.CPUArch.amd64`

### Record 048 - add CloudStack user-agent headers to template download requests

- Local branch: `main`
- Local commit: `f051bdc876`
- Source Apache commits:
  - `4ebe3349b7` add user-agent header to template downloader request (#12791)
- Summary:
  - Introduce `HttpClientCloudStackUserAgent` as a shared CloudStack-branded user-agent string provider
  - Set the shared user-agent on HTTP and HEAD requests issued by template downloaders, direct downloads, URL validation helpers, and QCOW2 size probing
  - Reuse a common `UriUtils.USER_AGENT` constant for `HttpURLConnection`-based helper paths
- Functional impact:
  - Makes outbound template-download and remote-image probe traffic identify itself consistently to upstream HTTP servers
  - Reduces the chance of providers applying different behavior to anonymous Java HTTP clients during template validation, download, or virtual-size inspection flows
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to downloader/helper call sites plus the new shared user-agent utility class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 1f8ff5cbad after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 049 - apply nexthop static routes to PBR tables and interface ACL chains

- Local branch: `main`
- Local commit: `994506d3ec`
- Source Apache commits:
  - `83f705ddc5` Static Routes with nexthop non-functional for private gateways (#12859)
- Summary:
  - Add `CsHelper.find_device_for_gateway(...)` to map a gateway IP to the matching router interface subnet
  - Update `CsStaticRoutes` so route add/delete operations touch both the main routing table and the matching interface-specific PBR table when a nexthop belongs to a private gateway subnet
  - Extend `CsAddress` firewall generation to emit the same inbound/outbound ACL chains for nexthop-based static routes as for legacy `ip_address`-based routes
- Functional impact:
  - Fixes VPC router traffic drops where static routes configured with a gateway/nexthop were installed only in the main routing table while policy-based routing uses interface-specific tables
  - Restores ACL and PREROUTING/FORWARD rule generation for nexthop-based static routes, so traffic can traverse private gateway paths consistently
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `CsAddress.py`, `CsHelper.py`, and `CsStaticRoutes.py`
  - Runtime/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as eb48668e0a after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 050 - skip redundant NSX LB patch operations that trigger 404s

- Local branch: `main`
- Local commit: `aaa8d95329`
- Source Apache commits:
  - `05c59630e0` fix: LB Creation avoid 404 API errors due to non-needed patches (#12835)
- Summary:
  - Check for existing NSX LB pools before patching and skip the update when the pool members are unchanged
  - Resolve monitor profile paths by direct lookup and create the monitor profile only when it is actually missing
  - Avoid patching an NSX virtual server when it already exists, and add focused regression tests for the skip/patch decision points
- Functional impact:
  - Prevents LB create/update flows from sending redundant NSX patch requests that can fail with `404 Not Found` when the target object is already in the desired state
  - Makes NSX LB provisioning more idempotent by distinguishing between genuinely missing objects and objects that already exist with the expected membership or monitor profile
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient.java` and `NsxApiClientTest.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as af6a324516 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 051 - fetch all NSX paged list results instead of truncating at the first page

- Local branch: `main`
- Local commit: `10751b2efb`
- Source Apache commits:
  - `e0fe953791` fix: NSX SDK list operations are pageable: the API returns a non-null and non-empty (#12834)
- Summary:
  - Introduce a reusable `PagedFetcher` helper that follows NSX cursor-based pagination and merges items across pages
  - Update `NsxApiClient` list retrieval paths to use complete paged results for sites, enforcement points, locale services, and policy-group members
  - Add focused unit tests that cover single-page, empty-cursor, multi-page, and null-first-page-item flows
- Functional impact:
  - Prevents NSX-backed operations from acting on incomplete inventories when the NSX API returns more than one page of results
  - Makes NSX group, locale-service, and enforcement-point lookups deterministic in environments with larger object counts
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `NsxApiClient`, the new `PagedFetcher`, and its dedicated test class
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 3604e72e77 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 052 - add only missing PowerFlex MDMs when preparing the KVM SDC client

- Local branch: `main`
- Local commit: `c3d54c8797`
- Source Apache commits:
  - `71bd26ff7c` PowerFlex/ScaleIO storage - the MDMs validation improvements (#12893)
- Summary:
  - Filter the storage-pool MDM list down to only the addresses that are not already present in the SDC configuration
  - Return a success path when all requested MDMs are already configured, instead of forcing a redundant add flow
  - Report the exact missing MDM addresses when registration still fails after an add attempt
- Functional impact:
  - Prevents KVM ScaleIO/PowerFlex pool preparation from misclassifying a partially preconfigured MDM set as fully ready or fully failed based on the first address alone
  - Makes repeated SDC preparation idempotent and easier to troubleshoot when only a subset of MDM endpoints is missing
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `ScaleIOStorageAdaptor.java`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a0e88938be after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 053 - skip already-covered upgrade hops when the source version is not explicit in the graph

- Local branch: `main`
- Local commit: `2ce5188d0c`
- Source Apache commits:
  - `4b7370a601` upgrade: skip the upgrade paths which are not needed (#12881)
- Summary:
  - Filter `DatabaseVersionHierarchy.getPath(...)` results so only upgrade steps with a version strictly newer than the source database version are returned
  - Make the `Usage Server` configuration group insert idempotent in `schema-42000to42010.sql`
  - Add a regression test that verifies `4.20.1.0 -> 4.20.3.0` resolves directly to the `4.20.2.0 -> 4.20.3.0` upgrader
- Functional impact:
  - Prevents upgrade planning from replaying obsolete path segments when the exact source version is not a direct node in the version hierarchy
  - Makes the early 4.20 schema path safer on reruns by avoiding duplicate configuration-group insert failures
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to the upgrade path resolver, one schema SQL line, and one targeted regression test
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 8e73f1f762 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 054 - restore management server id from cookies after SAML login

- Local branch: `main`
- Local commit: `affd5335ca`
- Source Apache commits:
  - `d6c39772b2` Set management server id from cookies after saml login (#12858)
- Summary:
  - Add the `managementserverid` cookie to the SAML login response when the login payload carries a management server id
  - Restore that cookie into the UI store during the SAML re-entry path in `permission.js`
- Functional impact:
  - Prevents post-SAML UI/API flows from losing the management server affinity that normal login paths already preserve
  - Reduces the chance of follow-up authenticated requests missing the server-id hint immediately after SAML authentication
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to `SAMLUtils.java` and `ui/src/permission.js`
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 1aabbb1777 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 055 - avoid NAS backup provider crashes when no running KVM host is available

- Local branch: `main`
- Local commit: `e2bbf6a31f`
- Source Apache commits:
  - `6ca6aa1c3f` Fix NPE in NASBackupProvider when no running KVM host is available (#12805)
- Summary:
  - Guard `deleteBackup(...)` so it fails with a descriptive runtime exception when no running KVM host can be found in the target zone
  - Short-circuit `syncBackupStorageStats(...)` when there are no repositories or no eligible running KVM host
  - Keep the branch-local `commons-collections4` import while adopting Apache's host-null protections
- Functional impact:
  - Prevents the NAS backup sync background task from crashing with a null dereference during host outages or agent reconnect windows
  - Makes forced backup deletion failures explicit when there is no execution host available instead of failing later with an opaque NPE
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in the `CollectionUtils` import because this branch already uses `org.apache.commons.collections4.CollectionUtils`
  - The resolved file preserves Apache's null-host handling and repository-empty early return while keeping the local collections4 import
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as e85e854cd1 after history-doc conflict resolution`
- Conflict notes:
  - `NASBackupProvider` conflicted on `main` only in the import block due to the branch's existing `commons-collections4` migration
- Resolution notes:
  - Kept the local `org.apache.commons.collections4.CollectionUtils` import and applied the Apache runtime guards unchanged

### Record 056 - keep Public networks out of multi-CIDR cleanup side effects

- Local branch: `main`
- Local commit: `e8362ab92d`
- Source Apache commits:
  - `ae455ee193` VPC restart cleanup for Public networks with multi-CIDR data (#12622)
- Summary:
  - Skip `addCidrAndGatewayForIpv4/Ipv6(...)` and matching remove flows for `TrafficType.Public` networks in `ConfigurationManagerImpl`
  - Sanitize legacy Public-network addressing fields in `schema-42200to42210.sql` by nulling network-level CIDR/gateway columns
- Functional impact:
  - Prevents Public networks from accumulating comma-separated CIDR and gateway state that later breaks VPC restart cleanup with malformed CIDR parsing
  - Leaves Public network addressing sourced from VLAN/IP-range state rather than duplicating it into network-level fields
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `schema-42200to42210.sql` because this branch already drops `backup_interval_type` in the same migration tail
  - The resolved migration keeps both the local backup cleanup and the Apache Public-network sanitization SQL
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f93d0dde9b after history-doc conflict resolution`
- Conflict notes:
  - `schema-42200to42210.sql` conflicted on `main` because prior local migration work already rewrote the same tail section
- Resolution notes:
  - Preserved the existing `backup_interval_type` drop and inserted only the Apache Public-network cleanup statements beside it

### Record 057 - propagate forced delete flags across management servers

- Local branch: `main`
- Local commit: `a4baa35318`
- Source Apache commits:
  - `160876c6d7` Fix: API Thread held forever during force deleting across MS (#12968)
- Summary:
  - Extend `PropagateResourceEventCommand` with `forced` and `forceDeleteStorage` flags
  - Pass those flags through `ResourceManagerImpl.deleteHost(...)`, cross-MS propagation, and peer-side `executeUserRequest(...)`
  - Return peer-side runtime failure details as an explicit failed answer instead of letting the caller wait indefinitely
  - Add focused tests that verify delete-host overloads preserve the force flags
- Functional impact:
  - Prevents force-delete host operations routed through another management server from silently losing the force semantics
  - Turns peer-side propagated failures into deterministic API errors instead of threads hanging while waiting for a result that never arrives
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to resource event propagation classes plus focused resource-manager tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as d1abaedd51 after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 058 - retry KVM incremental snapshot rebase after transient image locks

- Local branch: `main`
- Local commit: `eac20a6180`
- Source Apache commits:
  - `7c7b2ae75d` Fix KVM incremental volume snapshot creation (#12666)
- Summary:
  - Add `incremental.snapshot.retry.rebase.wait` to agent properties with a default 60-second backoff
  - Retry the QCOW2 rebase once when the initial rebase fails specifically because another process still holds the image lock
  - Preserve immediate failure behavior for non-lock-related rebase errors
- Functional impact:
  - Reduces transient KVM incremental snapshot failures caused by libvirt/qemu still holding the image lock immediately after snapshot operations
  - Keeps other rebase failures explicit instead of masking them behind generic retry behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Cached diff is limited to agent property definitions and `KVMStorageProcessor`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as ec5184fc2d after history-doc conflict resolution`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 059 - expose richer VM start failure details under an explicit config gate

- Local branch: `main`
- Local commit: `d5c5ff9455`
- Source Apache commits:
  - `68030df10b` VM start error handling improvements and config to expose error to users (#12894)
- Summary:
  - Add global config `expose.errors.to.user` and use it when deciding whether non-admin users may see detailed VM start errors
  - Track the last known start failure reason through deployment retries so final errors can surface a meaningful cause
  - Preserve the Europa-specific VirtualRouter network-unavailable message, but route its detail exposure through the same config gate
- Functional impact:
  - Gives operators and optionally end users clearer VM start failure messages instead of a generic “see management server log” response
  - Makes repeated deployment retries easier to diagnose by surfacing the last concrete failure reason when capacity or resource allocation ultimately fails
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` only in `VirtualMachineManagerImpl.start(...)` because this branch already had a custom VirtualRouter-specific error message
  - The resolved code keeps the local VirtualRouter wording but gates detailed exposure through Apache's new `canExposeError(...)` logic and config key
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as bcc7a07225 after history-doc conflict resolution`
- Conflict notes:
  - `VirtualMachineManagerImpl` conflicted on `main` where the branch already customized the VirtualRouter resource-unavailable error path
- Resolution notes:
  - Kept the local `The Network for VM ... is unavailable` message and switched its detail exposure to use Apache's `canExposeError(...)` policy

### Record 060 - expose and validate HAProxy idle timeout through load balancer orchestration

- Local branch: `main`
- Local commit: `32d8f85186`
- Source Apache commits:
  - `6e810989b6` HAProxy Configuration: network.loadbalancer.haproxy.idle.timeout (#12586)
- Summary:
  - Add `network.loadbalancer.haproxy.idle.timeout` as a configurable orchestration setting and propagate it through load balancer command construction
  - Teach `LoadBalancerConfigCommand`/`HAProxyConfigurator` to render `timeout client` and `timeout server` when the idle timeout is positive, and to blank them when explicitly set to `0`
  - Extend HAProxy health checks and tests so the new idle-timeout behavior is validated end to end
- Functional impact:
  - Gives operators a supported knob for HAProxy idle timeout without patching systemvm templates or generated configs by hand
  - Keeps runtime and health-check expectations aligned so VR load balancer checks do not falsely fail when the timeout is customized
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Europa cherry-pick required manual conflict resolution in `LoadBalancerConfigCommand` and `HAProxyConfigurator` because this branch already carries `lbConnectTimeout`, `lbClientTimeout`, and `lbServerTimeout` command fields
  - Cached diff spans HAProxy command/config generation, orchestration config plumbing, tests, and the systemvm health check script
  - Maven/UI/systemvm test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as a8c29e8606 after history-doc conflict resolution`
- Conflict notes:
  - `LoadBalancerConfigCommand` and `HAProxyConfigurator` conflicted on `ablestack-europa` where local HAProxy timeout customization already occupied the same command/config surfaces
- Resolution notes:
  - Preserved the local `lbConnectTimeout` / `lbClientTimeout` / `lbServerTimeout` fields and layered Apache's `idleTimeout` as a client/server override when present

### Record 061 - honor backup command timeout for NAS create and restore flows

- Local branch: `main`
- Local commit: `5803119a11`
- Source Apache commits:
  - `68bd056306` Support timeout configuration for Create and Restore NAS backup (#12964)
- Summary:
  - Use `command.wait` when provided, otherwise fall back to `commands.timeout`, for NAS backup create/restore KVM wrappers
  - Apply the resolved timeout to `rsync`, `qemu-img`, and piped backup commands instead of relying on mixed default process timeouts
  - Preserve the branch-local RBD restore helper flow while aligning its timeout handling with Apache's millisecond-based execution model
- Functional impact:
  - Prevents long-running NAS backup create/restore operations from timing out too early or ignoring operator-supplied wait values
  - Makes backup command execution more predictable across create and restore paths by using the same timeout source consistently
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `LibvirtRestoreBackupCommandWrapper` because this branch already refactored the RBD restore helper structure
  - The resolved code keeps the local RBD helper layout and applies Apache's timeout fallback logic to both restore and take-backup paths
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as c37ef5e0f1 after history-doc conflict resolution`
- Conflict notes:
  - `LibvirtRestoreBackupCommandWrapper` conflicted on `main` where the branch already carried a different restore-helper shape around the same timeout-sensitive logic
- Resolution notes:
  - Preserved the local RBD restore helper path and merged Apache's `command.wait -> commands.timeout` fallback plus timeout-aware script execution

### Record 062 - avoid forcing custom service offering changes on VM snapshot revert

- Local branch: `main`
- Local commit: `c778a082d4`
- Source Apache commits:
  - `b22dbbe2d7` Fix Revert Instance to Snapshot with custom service offering (#12885)
- Summary:
  - Split VM snapshot revert service-offering handling into a boolean "needs change" decision and only perform an upgrade when the snapshot actually differs from the current VM configuration
  - Compare dynamic compute offering CPU, memory, and speed against values stored in VM snapshot details so revert paths do not trigger unnecessary custom offering changes
  - Use snapshot detail values, not live VM detail values, when a service offering change is required during revert
- Functional impact:
  - Prevents revert-to-snapshot from forcing an unnecessary service offering change when the current VM already matches the snapshot's custom offering
  - Keeps dynamic custom offering reverts aligned with the snapshot's captured CPU and memory settings instead of whatever the VM happens to expose at revert time
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` because this branch still carried the older inline upgrade flow
  - The resolved code keeps the Apache boolean gate, adds snapshot-detail map extraction, and only upgrades the VM offering inside the revert transaction when the snapshot truly requires it
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as bf875bec57 after history-doc conflict resolution`
- Conflict notes:
  - `VMSnapshotManagerImpl` and `VMSnapshotManagerTest` conflicted on `main` where the pre-existing logic upgraded the VM offering directly instead of deciding first whether a change was needed
- Resolution notes:
  - Replaced the older direct-upgrade path with Apache's conditional change flow and preserved the branch-local DAO and test wiring already present in these classes

### Record 063 - support SharedMountPoint volume checks during importVm preflight

- Local branch: `main`
- Local commit: `dae6777b00`
- Source Apache commits:
  - `b0b3dc91f5` fix: support SharedMountPoint volume checks for importVm (#12946)
- Summary:
  - Extend the KVM `CheckVolumeCommand` wrapper so SharedMountPoint pools are treated as supported when validating volumes for import flows
  - Keep the existing filesystem and NFS handling unchanged while broadening the accepted pool-type list
- Functional impact:
  - Prevents import-VM preflight checks from rejecting SharedMountPoint-backed volumes even though the hypervisor path can handle them
  - Reduces false negatives when validating KVM import candidates stored on shared mount primary storage
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the supported pool-type list in `LibvirtCheckVolumeCommandWrapper`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 0f43c06318 after code and history-doc conflict resolution`
- Conflict notes:
  - `LibvirtCheckVolumeCommandWrapper` conflicted on `ablestack-europa` because the branch already carried `RBD` in the same supported pool-type list
- Resolution notes:
  - Preserved Europa's existing `RBD` support and added Apache's `SharedMountPoint` support alongside it

### Record 064 - support SharedMountPoint storage discovery for KVM import and unmanage

- Local branch: `main`
- Local commit: `920d6aa0ff`
- Source Apache commits:
  - `b1bc5380a2` fix: support SharedMountPoint for KVM volume import and unmanage (#12956)
- Summary:
  - Add SharedMountPoint to the supported KVM storage-pool types exposed by the import/unmanage API contract
  - Align the KVM volume listing wrapper's qemu-img-compatible storage-pool list with the same SharedMountPoint support
- Functional impact:
  - Lets KVM import and unmanage flows enumerate volumes on SharedMountPoint pools instead of excluding them as unsupported
  - Keeps API-level validation and hypervisor-side storage discovery consistent for the same storage backend
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff updates one API interface constant and one KVM wrapper constant
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as fee04fc506 after code and history-doc conflict resolution`
- Conflict notes:
  - `LibvirtGetVolumesOnStorageCommandWrapper` conflicted on `ablestack-europa` because the branch already carried the same storage-pool types in a different local list layout
- Resolution notes:
  - Kept Europa's existing `RBD` and `SharedMountPoint` support while accepting the Apache alignment for the API and hypervisor-side constants

### Record 065 - replace GROUP_CONCAT backup volume serialization with JSON aggregation

- Local branch: `main`
- Local commit: `4d2401d7e7`
- Source Apache commits:
  - `4ba4bd33c3` replace GROUP_CONCAT with JSON_ARRAYAGG to avoid errors like Row 19 was cut by GROUP_CONCAT (#12777)
- Summary:
  - Rewrite upgrade SQL that backfills backup volume metadata so it uses `JSON_ARRAYAGG(JSON_OBJECT(...))` instead of string-built `GROUP_CONCAT(...)`
  - Keep the same backup volume payload fields while avoiding truncation-prone string concatenation in large-volume cases
- Functional impact:
  - Prevents upgrade-time backup metadata generation from silently truncating long volume lists under MySQL `GROUP_CONCAT` limits
  - Produces structurally valid JSON arrays for `backups.backed_volumes` and `vm_instance.backup_volumes` even when many disks are present
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff only updates the SQL migration logic in `schema-42010to42100.sql`
  - Database migration execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 6f1bd5d2f8 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 066 - improve KVM GPU domain parsing and support Display controller class

- Local branch: `main`
- Local commit: `a2baf8a85b`
- Source Apache commits:
  - `416679fae1` Fix domain parsing for GPU & add Display controller in the supported PCI class (#12981)
- Summary:
  - Tighten KVM GPU domain parsing in `LibvirtGpuDef` so discovery handles vendor output more reliably
  - Extend `gpudiscovery.sh` to treat Display controller PCI class entries as GPU-capable devices in addition to the previously supported classes
  - Add focused unit coverage for the updated GPU definition parsing behavior
- Functional impact:
  - Improves GPU discovery accuracy on hosts where PCI domain values or `lspci` output formatting previously caused parser failures
  - Enables detection of accelerator cards exposed through the Display controller class, including newer AMD Instinct-style hardware
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff spans the GPU parser, the KVM discovery script, and new `LibvirtGpuDefTest` coverage
  - Maven-based Java test execution and script-level runtime validation have not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as f77bea5384 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 067 - avoid custom offering NPEs during unmanaged and external VM import

- Local branch: `main`
- Local commit: `6683ae7a27`
- Source Apache commits:
  - `2416db2a44` Fix NPE on external/unmanaged instance import using custom offerings (#12884)
- Summary:
  - Move unmanaged/external KVM import CPU and memory reservation checks into dedicated helper methods that can read values either from the offering or from runtime/import details when the offering is dynamic
  - Add volume reservation helper coverage for external KVM import and wire the reservation lifecycle so conversions/imports close all temporary reservations safely
  - Extend `UnmanagedVMsManagerImplTest` with focused checks for dynamic offering detail parsing and invalid integer detail handling
- Functional impact:
  - Prevents unmanaged or external VM import from dereferencing null CPU/memory values when custom offerings rely on runtime/detail-provided sizing
  - Fails earlier and more cleanly when required import detail values are missing or malformed, instead of surfacing a later null-pointer failure
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` because this branch already carries VMware import task tracking, extra-param validation, and adjacent import test coverage
  - The resolved code keeps the branch-local VMware import task flow and extra-param tests while layering Apache's reservation helpers and new custom-offering regression tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as fdfa6bdde5 after history-doc auto-merge`
- Conflict notes:
  - `UnmanagedVMsManagerImpl` and `UnmanagedVMsManagerImplTest` conflicted on `main` where local import extensions and nearby tests occupied the same import-resource management sections
- Resolution notes:
  - Preserved local VMware import task bookkeeping and merged Apache's null-safe reservation helpers plus detail-parsing tests into the existing import flow

### Record 068 - fix PowerFlex 4.x VM snapshot take/revert handling

- Local branch: `main`
- Local commit: `fa0bb99c76`
- Source Apache commits:
  - `131ea9f7ac` Fix PowerFlex 4.x issues with take & revert instance snapshots (#12880)
- Summary:
  - Adjust ScaleIO/PowerFlex VM snapshot strategy handling so multi-volume snapshot state updates and revert flows follow the newer PowerFlex 4.x expectations
  - Update the ScaleIO gateway client logic to vary overwrite behavior based on PowerFlex version-specific API semantics
- Functional impact:
  - Fixes take/revert VM snapshot behavior for PowerFlex 4.x environments that would otherwise mis-handle multi-volume state updates or use the wrong overwrite semantics
  - Improves compatibility across older and newer PowerFlex API variants without changing unrelated snapshot behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff touches `ScaleIOVMSnapshotStrategy` and `ScaleIOGatewayClientImpl` only
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 3962871697 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 069 - allow creating a volume directly on a selected storage pool

- Local branch: `main`
- Local commit: `87af436ce2`
- Source Apache commits:
  - `df7ff97271` Create volume on a specified storage pool (#12966)
- Summary:
  - Extend `CreateVolumeCmd` and `VolumeApiServiceImpl` so callers can optionally target a specific storage pool when creating a volume
  - Surface the new pool-selection control in the UI create-volume flow and add the matching user-facing text
- Functional impact:
  - Lets operators place a newly created volume on an explicitly chosen storage pool instead of relying entirely on normal planner selection
  - Improves operational control for storage troubleshooting, migration prep, or targeted placement workflows from both API and UI
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - Staged diff spans the user API command, backend create-volume service path, and `CreateVolume.vue` plus locale text
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as 7562c347d1 after history-doc auto-merge`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 070 - align GitHub Actions checkout step on v6

- Local branch: `main`
- Local commit: `01d5026710`
- Source Apache commits:
  - `6bcbb008b4` Bump `actions/checkout` to `v6` (#12164)
- Summary:
  - Update GitHub Actions workflows so checkout steps use `actions/checkout@v6`
  - Normalize the same checkout version across the workflow set already carried by this branch
- Functional impact:
  - Keeps CI workflow dependencies aligned with the newer checkout action release without changing product runtime behavior
  - Reduces maintenance drift between upstream workflow baselines and this fork's broader workflow matrix
- Validation:
  - Apache cherry-pick on `main` broadened beyond the single upstream workflow because this branch already carries additional workflow files using the same checkout action pin
  - The staged diff only touches `.github/workflows/*.yml`
  - GitHub Actions execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `Applied on ablestack-europa as ec243bba23 after resolving a branch-local workflow/path collision during cherry-pick`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - Applied the same checkout version normalization across the branch-local workflow set rather than limiting the change to upstream's single-file footprint
  - On `ablestack-europa`, preserved the branch-local desktop-service `works.yml` content when a mis-targeted cherry-pick conflict surfaced, and only kept the intended workflow checkout-version updates

### Record 071 - avoid unnecessary service-offering changes during VM snapshot revert

- Local branch: `main`
- Local commit: `8b9a3455a9`
- Source Apache commits:
  - `b22dbbe2d7` Fix Revert Instance to Snapshot with custom service offering (#12885)
- Summary:
  - Split VM-snapshot revert validation so running instances reject disk-only snapshot revert and stopped instances reject disk-and-memory revert with clearer state-specific messages
  - Refactor service-offering revert logic into `userVmServiceOfferingNeedsChange(...)` so dynamic offerings only trigger a revert-time offering change when CPU, memory, or speed actually differ from the snapshot payload
  - Extend `VMSnapshotManagerTest` with explicit coverage for matching and non-matching dynamic offering details
- Functional impact:
  - Avoids unnecessary service-offering update attempts during snapshot revert when a dynamic offering still resolves to the same sizing that the instance already uses
  - Produces more accurate revert validation for state/type combinations, reducing confusing revert failures around custom offerings and memory snapshots
- Validation:
  - Attempting the Apache cherry-pick on `main` showed no remaining code delta after the earlier snapshot revert/custom-offering work already in this branch
  - This local commit therefore records the satisfied upstream state in the history document and backfills the previous record's final SHA
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8d9e5cc095`
- Conflict notes:
  - `N/A`
- Resolution notes:
  - No additional code merge was required because the current branch state already satisfied the upstream revert/offering behavior

### Record 072 - support Linstor primary storage in NAS backup restore flows

- Local branch: `main`
- Local commit: `1fbd528784`
- Source Apache commits:
  - `03de62bf38` Support Linstor Primary Storage for NAS BnR (#12796)
- Summary:
  - Extend NAS backup restore path building so Linstor-backed volumes use the expected `/dev/drbd/by-res/cs-<uuid>/0` style device path while existing pool types keep their current path conventions
  - Carry restore volume sizes through `RestoreBackupCommand` and use them in the KVM restore wrapper when a Linstor target volume must be created before `qemu-img convert`
  - Update the KVM restore wrapper, script, and focused tests so block-device restore works for both RBD and Linstor while filesystem-backed restores still honor the branch-local timeout handling
- Functional impact:
  - Allows NAS backup restore to work against Linstor primary storage instead of assuming only filesystem or RBD-backed volume layouts
  - Preserves existing timeout-controlled restore behavior for non-block storage while adding the extra size/connect steps Linstor requires
- Validation:
  - Apache cherry-pick required a manual conflict resolution on `main` in `LibvirtRestoreBackupCommandWrapper` because this branch already carried NAS timeout handling and earlier restore-path adjustments in the same helper methods
  - The resolved code keeps the branch-local millisecond timeout flow and merges Apache's Linstor block-device handling, restore-size propagation, and updated wrapper tests
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8c47f676a8`
- Conflict notes:
  - `LibvirtRestoreBackupCommandWrapper` conflicted where Apache's Linstor support overlapped the branch-local NAS timeout and restore helper changes
- Resolution notes:
  - Kept the branch-local timeout-aware `rsync` and `QemuImg` invocation path, then layered Apache's Linstor-specific device-path, connect, create-target, and raw-attach handling on top

### Record 073 - allow import and unmanage of backing-file volumes behind a config gate

- Local branch: `main`
- Local commit: `321effd42c`
- Source Apache commits:
  - `e93ae1a4f4` New config key "allow.import.volume.with.backing.file" to skip volume backing (#12809)
- Summary:
  - Make `VolumeImportUnmanageService` configurable and add the global advanced setting `allow.import.volume.with.backing.file`
  - Gate backing-file rejection in both volume import/unmanage and unmanaged VM import paths behind the new setting instead of rejecting such volumes unconditionally
  - Expose the new config key from `VolumeImportUnmanageManagerImpl` so the behavior can be toggled without code changes
- Functional impact:
  - Gives operators a controlled way to import or unmanage QCOW2 volumes that still reference a backing file when their environment explicitly allows that workflow
  - Preserves the safer default behavior by keeping the check enabled unless the new global setting is turned on
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the service/config surface and the two backing-file validation call sites
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `4227ac97a0`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 074 - harden KVM direct-download URL handling

- Local branch: `main`
- Local commit: `1a0561f603`
- Source Apache commits:
  - `0edd577f4b` Fix: KVM Direct Download URL injection
- Summary:
  - Tighten direct-download path handling so generated URL/location strings no longer rely on unsafe concatenation patterns that could be abused by crafted input
  - Keep the fix scoped to the KVM direct-download implementations for standard, metalink, and NFS-backed flows
- Functional impact:
  - Reduces the risk of malformed or attacker-controlled download location input influencing direct-download execution paths on KVM
  - Leaves normal template/image direct-download behavior unchanged for valid inputs
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the three direct-download implementation classes
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `939a1f5f1f`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 075 - preserve camelCase `domainId` handling in login/auth flows

- Local branch: `main`
- Local commit: `7ed9e5f573`
- Source Apache commits:
  - `56dc11980f` test_accounts.py failure fix - keep the camelCase parameter "domainId" (#12689)
- Summary:
  - Add `ApiServerService.getDomainId(...)` and implement the fallback in `ApiServer` so login/auth flows can read either `domainid` or camelCase `domainId`
  - Update both default login and OAuth login authenticators to use the shared helper instead of reading only the legacy parameter key directly
  - Carry the matching OAuth command test update with the API/auth changes
- Functional impact:
  - Prevents login failures for clients or tests that still submit the camelCase `domainId` parameter name
  - Centralizes the parameter fallback so auth entrypoints behave consistently instead of each flow drifting independently
- Validation:
  - Apache cherry-pick required a manual conflict resolution on `main` in `ApiServerService` because this branch already exposes an additional service method on the same interface
  - The resolved code keeps the branch-local interface method and layers Apache's shared `getDomainId(...)` helper plus auth-call-site updates alongside it
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `7d62faab7e`
- Conflict notes:
  - `ApiServerService` conflicted where Apache added `getDomainId(...)` and the local branch had already added `isPostRequestsAndTimestampsEnforced()`
- Resolution notes:
  - Kept both interface methods and applied Apache's camelCase domain-id fallback everywhere else unchanged

### Record 076 - add the GitHub Actions ecosystem to Dependabot

- Local branch: `main`
- Local commit: `177ee3b036`
- Source Apache commits:
  - `cf9bda2050` [CI] Add github-actions ecosystem to Dependabot (#12823)
- Summary:
  - Extend `.github/dependabot.yml` so Dependabot also tracks workflow action versions in the GitHub Actions ecosystem
  - Keep the existing dependency-update structure and intervals intact while adding the extra ecosystem block
- Functional impact:
  - Improves CI maintenance coverage by letting Dependabot flag outdated GitHub Action references alongside the existing dependency sources
  - Does not affect product runtime behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.github/dependabot.yml` only
  - GitHub Actions execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `819521ce4e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 077 - refresh codespell configuration and hook versions

- Local branch: `main`
- Local commit: `cec3def757`
- Source Apache commits:
  - `5d61ba3538` [CI] Create `.codespellrc`; upgrade codespell hook; fix typos (#12824)
- Summary:
  - Add a repository-level `.codespellrc` and update the codespell hook configuration in `.pre-commit-config.yaml`
  - Carry the small typo fixes that align the codebase and docs with the refreshed spelling checks
- Functional impact:
  - Improves repository linting hygiene and reduces repeated false positives or manual local overrides for spelling checks
  - Does not change product runtime behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff spans `.codespellrc`, `.pre-commit-config.yaml`, and a handful of typo-only source/doc updates
  - Pre-commit execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f38d48b590`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 078 - remove the broken ViserJS attribution link from the UI README

- Local branch: `main`
- Local commit: `c424b9ad2c`
- Source Apache commits:
  - `9cc6c09b9e` Remove broken ViserJS attribution link from UI README (#12724)
- Summary:
  - Remove the stale/broken ViserJS attribution link from `ui/README.md`
- Functional impact:
  - Improves repository documentation accuracy without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `ui/README.md` only
  - Documentation link verification has not been run separately in this environment
- Europa cherry-pick status:
  - `40255d1472`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 079 - add code owners for the NSX network elements plugin

- Local branch: `main`
- Local commit: `95024da846`
- Source Apache commits:
  - `b744824f65` Add code owners for nsx network elements plugin (#12838)
- Summary:
  - Add the NSX network elements plugin paths to `.github/CODEOWNERS`
- Functional impact:
  - Improves repository ownership and review routing metadata without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.github/CODEOWNERS` only
  - CODEOWNERS validation has not been run separately in this environment
- Europa cherry-pick status:
  - `f703098925`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 080 - clarify isolation-method descriptions for physical network creation

- Local branch: `main`
- Local commit: `315c86c260`
- Source Apache commits:
  - `faaf7669c5` Update isolation methods description for physical network (#12759)
- Summary:
  - Refresh the isolation-method description text in `CreatePhysicalNetworkCmd` so the API help better reflects the supported physical-network modes
- Functional impact:
  - Improves admin/API documentation clarity without changing runtime behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `CreatePhysicalNetworkCmd` only
  - API doc generation has not been run in this environment by request
- Europa cherry-pick status:
  - `c41a3f6d59`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 081 - avoid duplicate resource count increments during KVM VM import from disk

- Local branch: `main`
- Local commit: `a8996af5f3`
- Source Apache commits:
  - `497266270b` Cleanup imported VM from disk on failure due to volume allocation + prevent duplicate volume and primary storage increment on import
- Summary:
  - Extend `allocateRawVolume(...)` with an `incrementResourceCount` switch so import flows can allocate temporary/imported volumes without double-counting volume or primary-storage usage
  - Update KVM VM import-from-disk and unmanaged external import paths to pass `false` for those pre-created volumes while keeping normal VM allocation paths on `true`
  - Preserve the local branch's naming, device-id, and `EXTERNAL` image-format handling while adding cleanup on allocation-time `ResourceAllocationException`
- Functional impact:
  - Prevents imported VMs from over-incrementing volume or primary storage resource counts during staged disk allocation
  - Improves failure cleanup when a resource-allocation check trips mid-import, reducing leaked partially imported VM state
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `VirtualMachineManagerImpl` and `UnmanagedVMsManagerImpl` because this branch already carries import-flow extensions, custom device naming, and additional template-format handling
  - The resolved code keeps the local import behavior and applies Apache's resource-count guard only to the affected import allocation paths
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `c8f413e09e`
- Conflict notes:
  - `VirtualMachineManagerImpl` conflicted where Apache added the new `incrementResourceCount` flag and the local branch had already customized data disk naming/device-id handling and `EXTERNAL` format root-volume skipping
  - `UnmanagedVMsManagerImpl` conflicted where Apache's import cleanup adjustments overlapped the branch-local unmanaged/external KVM import extensions
- Resolution notes:
  - Kept the branch-local import flow structure and device naming, added the new boolean flag with `true` for normal allocations and `false` for import-only allocations, and preserved cleanup on allocation failures

### Record 082 - refresh MinIO canned policy membership when buckets are removed

- Local branch: `main`
- Local commit: `3e13406450`
- Source Apache commits:
  - `7703fdacab` [minio] Handle user's canned policy when a bucket is deleted
- Summary:
  - Add `accountId` to `BucketTO` so bucket deletion has enough context to rebuild the owning account's canned policy
  - Refactor MinIO policy generation into a shared helper and reuse it on both create and delete so bucket membership stays accurate
  - Update the MinIO driver test to cover the new delete-time policy refresh path
- Functional impact:
  - Prevents deleted buckets from lingering in the account's MinIO canned policy and avoids stale access rules after bucket removal
  - Keeps create and delete policy management behavior aligned instead of drifting between separate code paths
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to `BucketTO`, the MinIO object-store driver, and its focused test
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `ecfa5d8ea9`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 083 - transition expunging VMs to error when expunge fails

- Local branch: `main`
- Local commit: `d27f93ae8c`
- Source Apache commits:
  - `bce55945ec` Mark VMs in error state when expunge fails during destroy operation (#12749)
- Summary:
  - Add the `Expunging -> Error` VM state transition for `OperationFailedToError` and use it when expunge fails during destroy
  - Capture external-volume lookup support in `VolumeDao` via `findByExternalUuid(...)`
  - Extend `UserVmManagerImplTest` with focused coverage for the new `transitionExpungingToError(...)` helper behavior
- Functional impact:
  - Prevents a failed expunge from leaving user VMs stuck in `Expunging` without a recoverable/error signal
  - Gives external-plugin flows a direct DAO lookup by external UUID without changing existing volume lookups
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `UserVmManagerImplTest` because the local branch already had a large block of adjacent VM configuration and limit-validation tests at the same file tail
  - The resolved test file keeps the local coverage and appends Apache's expunge-to-error tests without dropping either set
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `a5a586e631`
- Conflict notes:
  - `UserVmManagerImplTest` conflicted where Apache appended new expunge-failure tests and the local branch had already grown an unrelated block of trailing tests
- Resolution notes:
  - Preserved the local test block and added Apache's new transition tests after it, while leaving the service/DAO changes themselves unchanged

### Record 084 - align snapshot datastore CI fixes with current hidden-ref search builders

- Local branch: `main`
- Local commit: `5c10b983e7`
- Source Apache commits:
  - `3b42fbf3b2` Fixing CI failures (#12789)
- Summary:
  - Switch the snapshot datastore search builder used by non-destroyed snapshot lookups from a single-state exclusion to a `NOTIN` builder that also fits the hidden-state filtering path
  - Carry the secondary-storage smoke test polling fix so SSVM readiness waits are more tolerant and less timing-sensitive
- Functional impact:
  - Keeps snapshot-store lookups consistent with the newer hidden-ref handling already present in this branch
  - Reduces flaky SSVM readiness failures in the smoke test flow without changing product runtime behavior
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `SnapshotDataStoreDaoImpl` because this branch already carries the hidden-ref `state IN` search builder used by Xen snapshot chaining fixes
  - The resolved code keeps the local `idEqRoleEqStateInSearch` builder and replaces the old single-state exclusion search with Apache's `NOTIN` variant
  - Marvin/integration tests have not been run yet in this environment by request
- Europa cherry-pick status:
  - `2e2600d5fc`
- Conflict notes:
  - `SnapshotDataStoreDaoImpl` conflicted where Apache renamed the non-destroyed-state search builder and the local branch had already added a second builder for hidden snapshot refs
- Resolution notes:
  - Retained both search use-cases by keeping the local hidden-ref builder and adopting Apache's `NOTIN` builder for the generic non-destroyed lookup helpers

### Record 085 - refresh unmanaged import test expectations after import cleanup changes

- Local branch: `main`
- Local commit: `6e54ade3f9`
- Source Apache commits:
  - `c6b20b8cc7` Fix failing tests
- Summary:
  - Update `UnmanagedVMsManagerImplTest` mocks to match the expanded `allocateRawVolume(...)` signature used by the import cleanup work
- Functional impact:
  - Keeps the unmanaged-import unit tests aligned with the newer import volume-allocation contract without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `UnmanagedVMsManagerImplTest` only
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `c116e07b1e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 086 - remove redundant stubbings from maintenance manager tests

- Local branch: `main`
- Local commit: `35ee3d570a`
- Source Apache commits:
  - `7f7d0b02e1` Remove unnecessary stubbings in ManagementServerMaintenanceManagerImplTest (#11914) (#12623)
- Summary:
  - Remove redundant Mockito stubbings from `ManagementServerMaintenanceManagerImplTest`
- Functional impact:
  - Reduces unit-test noise and brittle stubbing without affecting runtime behavior
- Validation:
  - Attempting the Apache cherry-pick on `main` showed no remaining code delta because the current branch test already matches the simplified stubbing pattern
  - This local commit therefore records the satisfied upstream state in the history document
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `5b9a74fe08`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 087 - apply whitespace cleanup for pre-commit-managed files

- Local branch: `main`
- Local commit: `082d4f4373`
- Source Apache commits:
  - `5d95bdd0eb` pre-commit trailing whitespace auto clean up (#12841)
- Summary:
  - Apply trailing-whitespace cleanup across the small set of config, README, template, and asset files touched by pre-commit
- Functional impact:
  - Reduces lint churn and keeps formatting consistent without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to whitespace-only cleanup and the corresponding `.pre-commit-config.yaml` normalization
  - Pre-commit execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `07fd1ad56c`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 088 - sync `.asf.yaml` collaborator list updates

- Local branch: `main`
- Local commit: `d8e944983c`
- Source Apache commits:
  - `608345d165` Update collaborators list in `.asf.yaml`
- Summary:
  - Align the repository's `.asf.yaml` collaborator list with the upstream metadata cleanup
- Functional impact:
  - Keeps ASF/GitHub repository metadata in sync without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick status:
  - `705777c3f9`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 089 - add upstream contributor metadata to `.asf.yaml`

- Local branch: `main`
- Local commit: `d203c9647a`
- Source Apache commits:
  - `9bbd32a8ef` Add DaanHoogland to the list of contributors
- Summary:
  - Add the upstream contributor metadata update to `.asf.yaml`
- Functional impact:
  - Keeps repository metadata aligned without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick status:
  - `43c0263bd1`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 090 - apply the latest upstream `.asf.yaml` metadata adjustment

- Local branch: `main`
- Local commit: `47ff0094d3`
- Source Apache commits:
  - `d8f748ad0e` Update `.asf.yaml`
- Summary:
  - Apply the remaining upstream `.asf.yaml` metadata adjustment on top of the collaborator/contributor sync
- Functional impact:
  - Keeps repository metadata aligned without changing product behavior
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff touches `.asf.yaml` only
  - Metadata effects are external to this local environment and were not separately validated here
- Europa cherry-pick status:
  - `8550a818d4`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 091 - record the already-satisfied direct-download temporary filename hardening backport

- Local branch: `main`
- Local commit: `c296a31b33`
- Source Apache commits:
  - `46a6bbad27` `Fix: KVM Direct Download URL injection (#60)`
- Summary:
  - Record that the current branch already uses UUID-based temporary filenames for direct template downloads instead of reusing the source URL basename
  - Confirm that direct, metalink, and NFS download paths all route through the same temporary filename hardening
- Functional impact:
  - Avoids path/filename reuse issues during direct download staging without introducing a duplicate code change
  - Keeps the branch aligned with the older backport while preserving the newer direct-download handling already merged here
- Validation:
  - Comparing the upstream backport against the current branch showed no remaining code delta in `DirectTemplateDownloaderImpl`, `MetalinkDirectTemplateDownloader`, or `NfsDirectTemplateDownloader`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `18234309b0`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 092 - record the already-satisfied MinIO canned-policy delete refresh backport

- Local branch: `main`
- Local commit: `7dc5086883`
- Source Apache commits:
  - `3b987f21af` `[20.3] handle user's canned policy when a bucket is deleted`
- Summary:
  - Record that the current branch already refreshes MinIO canned policies after bucket deletion using the bucket owner's account context
  - Confirm that the shared canned-policy regeneration helper is already used on both bucket create and delete paths
- Functional impact:
  - Prevents a duplicate backport from re-touching the MinIO object-store driver while preserving the correct post-delete access policy behavior
  - Keeps the history aligned with the upstream maintenance branch that carried the same fix earlier than `apache/main`
- Validation:
  - Comparing the upstream backport against the current branch showed no remaining code delta in `BucketTO`, `MinIOObjectStoreDriverImpl`, or `MinIOObjectStoreDriverImplTest`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `598c6c63a5`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 093 - record the intentionally reverted account netstats lateral-join change

- Local branch: `main`
- Local commit: `6121050871`
- Source Apache commits:
  - `58916eb608` `Use lateral join (introduced in MySQL 8.0.14) with subquery on user_statistics table in account_view for netstats (#12631)`
- Summary:
  - Record that the current branch intentionally keeps the separate `cloud.account_netstats_view` model rather than reintroducing the lateral-join variant
  - Note that this branch already matches the reverted end state later adopted upstream, so the original lateral-join change should stay unapplied here
- Functional impact:
  - Preserves compatibility with the branch's existing account network statistics view structure and avoids reintroducing a change that upstream subsequently backed out
  - Keeps schema/view behavior stable while still tracking the upstream commit history explicitly
- Validation:
  - Comparing the original lateral-join change against the current branch confirmed that `cloud.account_view` still joins `cloud.account_netstats_view` and the standalone `cloud.account_netstats_view.sql` remains present
  - This local commit therefore records the intentional already-reverted state in the history document instead of reapplying the lateral-join change
  - Schema migration execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `37ba105d50`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 094 - avoid wiping volume size metadata on failed download states

- Local branch: `main`
- Local commit: `4a2853d2b4`
- Source Apache commits:
  - `d0f6730157` `volume download fix`
- Summary:
  - Update the volume-download completion handler to persist size and physical-size metadata only when the download finishes in a non-error state
  - Reuse `VMTemplateStorageResourceAssoc.ERROR_DOWNLOAD_STATES` for the error-path check instead of repeating the individual status comparisons
- Functional impact:
  - Prevents failed or abandoned volume downloads from overwriting stored size metadata with invalid values
  - Keeps the error-path handling for download callbacks aligned with the shared error-state list
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to `BaseImageStoreDriverImpl`
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f0b2d71f4e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 095 - record the already-satisfied unmanaged import test follow-up

- Local branch: `main`
- Local commit: `0df74163d9`
- Source Apache commits:
  - `e8f8aca694` `Fix failing tests`
- Summary:
  - Record that the current branch already carries the `allocateRawVolume(..., anyBoolean())` matcher updates needed by the expanded unmanaged-import allocation signature
  - Note that the follow-up test-only change is already covered by the earlier unmanaged import cleanup work in this branch
- Functional impact:
  - Avoids duplicating a no-op test-only backport while keeping the source commit explicitly tracked in the sync history
  - Confirms that unmanaged import tests remain aligned with the boolean-extended allocation method signature
- Validation:
  - Attempting the Apache cherry-pick on `main` produced no remaining staged code delta because `UnmanagedVMsManagerImplTest` already uses the updated matcher signature
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the test change
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `78a9ab466d`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 096 - record the already-satisfied KVM incremental snapshot rebase retry improvement

- Local branch: `main`
- Local commit: `83fef55597`
- Source Apache commits:
  - `7c7b2ae75d` `Fix KVM incremental volume snapshot creation (#12666)`
- Summary:
  - Record that the current branch already exposes the incremental snapshot rebase retry wait property and retries rebase operations when libvirt still holds the image lock
  - Confirm that the KVM storage processor already contains the follow-up retry helper and matching agent property wiring
- Functional impact:
  - Avoids duplicating an already-integrated KVM snapshot resiliency improvement while still tracking the upstream source commit explicitly
  - Preserves the current retry-on-lock behavior for incremental snapshot rebases without introducing extra divergence
- Validation:
  - Comparing the upstream change against the current branch showed no remaining code delta in `agent.properties`, `AgentProperties`, or `KVMStorageProcessor`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `69efbc0036`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 097 - fix revert-to-VM-snapshot service offering changes for custom offerings

- Local branch: `main`
- Local commit: `5a5d654f58`
- Source Apache commits:
  - `b22dbbe2d7` `Fix Revert Instance to Snapshot with custom service offering (#12885)`
- Summary:
  - Record that the current branch already splits the revert validation messages for running/stopped instance snapshot combinations and already gates service offering changes through `userVmServiceOfferingNeedsChange(...)`
  - Note that the related `VMSnapshotManagerTest` coverage for custom and dynamic offerings is already present in this branch
- Functional impact:
  - Avoids duplicating an already-integrated revert-to-snapshot fix while still tracking the upstream source commit explicitly
  - Confirms that custom/dynamic service offering revert handling remains aligned with the upstream behavior already absorbed by this branch
- Validation:
  - Attempting the Apache cherry-pick on `main` surfaced only a comment-context conflict in `VMSnapshotManagerImpl` because the current branch already contained the functional logic and test coverage introduced by the upstream commit
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f3222d5ddf`
- Conflict notes:
  - `VMSnapshotManagerImpl` conflicted only in the helper documentation block where Apache and the local branch edited the same nearby comment context
- Resolution notes:
  - Kept the local parameter descriptions in the helper comment and recorded the source commit as already satisfied because no functional code delta remained

### Record 098 - add Headlamp as the preferred Kubernetes dashboard while keeping legacy access guidance

- Local branch: `main`
- Local commit: `10056a6683`
- Source Apache commits:
  - `18075ae4a9` `Add support for Headlamp dashboard for kubernetes; deprecate legacy kubernetes dashboard (#12776)`
- Summary:
  - Update Kubernetes cluster readiness checks to accept either Headlamp in `kube-system` or the legacy Kubernetes Dashboard namespace
  - Switch the Kubernetes binaries ISO helper and control-node bootstrap flow to install Headlamp by default while preserving a fallback path for legacy dashboard manifests shipped in the ISO
  - Refresh the UI guidance so operators get Headlamp-first access, token creation, and legacy dashboard compatibility instructions side by side
- Functional impact:
  - Makes new Kubernetes clusters prefer Headlamp without breaking older clusters that still ship the legacy dashboard manifest
  - Improves operator guidance for dashboard access and token creation across both dashboard generations
- Validation:
  - Apache cherry-pick applied cleanly on `main` with no manual conflict resolution
  - The staged diff is limited to the Kubernetes service util, bootstrap YAML, ISO helper script, and dashboard help UI
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `16fdb49f92`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 099 - record the already-satisfied create-volume-on-storage-pool flow

- Local branch: `main`
- Local commit: `b198b1c4c9`
- Source Apache commits:
  - `df7ff97271` `Create volume on a specified storage pool (#12966)`
- Summary:
  - Record that the current branch already exposes the `storageid` API parameter, server-side `createVolumeOnStoragePool(...)` path, and the admin UI flow for creating a volume on a selected primary storage pool
  - Confirm that the localized UI strings and create-on-storage toggle are already present in this branch
- Functional impact:
  - Avoids duplicating an already-integrated volume-placement enhancement while still tracking the upstream source commit explicitly
  - Confirms that admins can already place newly created volumes on a chosen primary storage pool in this branch
- Validation:
  - Attempting the Apache cherry-pick on `main` produced no remaining staged code delta because `CreateVolumeCmd`, `VolumeApiServiceImpl`, `CreateVolume.vue`, and the related UI labels already contain the upstream behavior
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven/UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `1621924b65`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 100 - tighten public IP limit validation for dedicated ranges and reservation-backed allocation

- Local branch: `main`
- Local commit: `746ac3e1de`
- Source Apache commits:
  - `9db630932e` `Address public IP limit validations`
- Summary:
  - Allow account/domain VLAN map lookups to accept nullable owners so public IP validation can correctly fall back from account ownership to domain/system-account evaluation
  - Expose the system account through `ApiDBUtils` and teach `CheckedReservation` to use it when a domain-scoped reservation has no concrete account owner
  - Wrap public IP allocation and VLAN range creation in reservation-aware limit checks so dedicated and non-dedicated public IP flows apply the right resource accounting path
- Functional impact:
  - Prevents public IP limit validation from miscounting or skipping checks when the allocation path uses domain-scoped ownership or reservation-backed VLAN creation
  - Keeps dedicated public IP reservations from incrementing account public IP counts incorrectly while still enforcing limits for normal allocations
- Validation:
  - Apache cherry-pick required manual conflict resolution on `main` in `ConfigurationManagerImpl` because the local branch already carried additional imports around the VLAN creation path (`DomainHelper`, SystemVM-related managers) in the same import block
  - The resolved code keeps the local imports and applies Apache's nullable VLAN owner lookup plus reservation-backed public IP validation logic
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `00835167b6`
- Conflict notes:
  - `ConfigurationManagerImpl` conflicted in the import block where Apache introduced reservation-related dependencies and the local branch already had extra local imports nearby
- Resolution notes:
  - Kept the local import set intact and added Apache's reservation-related imports and logic without changing the local VLAN creation flow structure

### Record 101 - record the already-satisfied global create-network menu guard source change

- Local branch: `main`
- Local commit: `e054627bab`
- Source Apache commits:
  - `db83622956` `ui: fix create network from global create menu (#12677)`
- Summary:
  - Record that the current branch already guards the global create-network entry path so missing zone/resource context does not break the UI flow
  - Confirm that the earlier UI fix on this branch already covers the same null-safe access pattern intended by the Apache source commit
- Functional impact:
  - Avoids duplicating a source commit whose functional outcome is already present in the current UI behavior
  - Keeps the sync history explicit about the upstream source commit that was absorbed by the earlier local UI fix
- Validation:
  - Reverse-applying the Apache patch on the current branch showed the protective logic is already present in `CreateNetwork.vue`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `69055c5eec`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 102 - record the already-satisfied template-zone delete redirect source change

- Local branch: `main`
- Local commit: `cddd3442c4`
- Source Apache commits:
  - `7aa0558c5b` `ui: avoid 404 after deleting template zones (#12681)`
- Summary:
  - Record that the current branch already routes template-zone deletion back to `/template` instead of relying on browser history when the last row disappears
  - Note that the local UI result matches the Apache source intent even though the surrounding formatting and table markup differ
- Functional impact:
  - Prevents duplicate reapplication of a UI navigation fix that is already present in the current branch
  - Makes the sync history explicit about the upstream source commit behind the already-absorbed behavior
- Validation:
  - Manual inspection of `TemplateZones.vue` confirmed `handleCancel()` already pushes to `/template` when no rows remain after deletion
  - The remaining diff against the Apache source commit is limited to formatting/context differences, so this record is tracked as already satisfied
  - UI build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `33cbfa1532`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 103 - record the already-satisfied physical-network isolation description source change

- Local branch: `main`
- Local commit: `fc2c7c112f`
- Source Apache commits:
  - `faaf7669c5` `Update isolation methods description for physical network (#12759)`
- Summary:
  - Record that the current branch already contains the upstream wording cleanup for physical network isolation method descriptions
  - Confirm that the local API help text matches the clarified upstream description for the supported isolation methods
- Functional impact:
  - Avoids duplicating a documentation-only API text change that is already present in the current branch
  - Preserves explicit upstream traceability for the wording change in the sync history
- Validation:
  - Reverse-applying the Apache patch on the current branch showed the updated isolation-method description is already present in `CreatePhysicalNetworkCmd`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the change
  - API doc generation has not been run yet in this environment by request
- Europa cherry-pick status:
  - `a60c247e47`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 104 - record the already-satisfied non-KVM SystemVM template arch registration source change

- Local branch: `main`
- Local commit: `c873a7a6e1`
- Source Apache commits:
  - `6f1aa96b4c` `engine/schema: fix new systemvm template is not registered during upgrade if hypervisor is not KVM (#12952)`
- Summary:
  - Record that the current branch already treats non-KVM hypervisors as `amd64` when iterating system VM template registrations during upgrade
  - Confirm that the matching unit-test expectation for VMware/system VM metadata architecture is already present as well
- Functional impact:
  - Avoids reapplying a source-level SystemVM upgrade fix whose behavior is already absorbed by the current branch
  - Keeps upstream traceability for the specific source commit that led to the already-present registration behavior
- Validation:
  - Inspection of `SystemVmTemplateRegistration.hypervisorList` and `SystemVmTemplateRegistrationTest` confirmed the non-KVM hypervisors already use `CPU.CPUArch.amd64`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `e245f9e940`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 105 - record the already-satisfied EL10 packaging compatibility source change

- Local branch: `main`
- Local commit: `0a6b159a4c`
- Source Apache commits:
  - `80ee7f183f` `Fix six package incompatiblity with EL10 (#12799)`
- Summary:
  - Record that the current branch already carries the EL10 packaging follow-up by depending on `python3-six` and `python3-protobuf` and by selecting the compatible mysql connector wheel at install time
  - Note that the local branch keeps its `centos7` packaging path and `urllib3` handling while still preserving the upstream EL10 compatibility intent
- Functional impact:
  - Avoids duplicating a packaging/source change whose functional result is already present in the branch-specific RPM spec flow
  - Keeps the upstream source commit explicitly tracked despite the local packaging path differing from upstream `el8`
- Validation:
  - Inspection of `packaging/centos7/cloud.spec` confirmed the required Python package dependencies and Python-version-aware mysql connector install logic are already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - RPM build execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f04471f841`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 106 - record the already-satisfied backup schedule cleanup source change

- Local branch: `main`
- Local commit: `193095aede`
- Source Apache commits:
  - `27e4d979f1` `Clean up backup references to their schedules when the schedules are deleted (#12401)`
- Summary:
  - Record that the current branch already clears backup-to-schedule references during schedule cleanup, moves schedule response assembly into `ApiResponseHelper`, and drops the unused `backup_interval_type` column
  - Confirm that the local branch already preserves the same user-visible API result while keeping the schedule cleanup path consistent
- Functional impact:
  - Avoids duplicating a backup schedule cleanup source change that is already present in the current branch
  - Keeps explicit upstream traceability for the source commit behind the existing cleanup behavior
- Validation:
  - Inspection of `schema-42200to42210.sql` and `ApiResponseHelper.createBackupScheduleResponse(...)` confirmed the column removal and response-building logic are already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `27fd9a023e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 107 - record the already-satisfied NSX delete-NAT comparison source change

- Local branch: `main`
- Local commit: `fd40b07fe3`
- Source Apache commits:
  - `30dd234b00` `fix: NsxResource.executeRequest DeleteNsxNatRuleCommand comparison bug (#12833)`
- Summary:
  - Record that the current branch already avoids serialized `Network.Service` identity mismatches during NSX NAT deletion by comparing the service name rather than relying on object identity
  - Confirm that the existing NSX delete path already distinguishes `StaticNat` and `PortForwarding` using the stable service-name value
- Functional impact:
  - Avoids reapplying a source-level NSX fix whose runtime behavior is already present in the current branch
  - Preserves explicit upstream traceability for the serialized-command comparison bugfix
- Validation:
  - Inspection of `NsxResource.executeRequest(DeleteNsxNatRuleCommand)` confirmed the current branch already compares against `Network.Service.*.getName()`
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f5b7edfdc6`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 108 - record the already-satisfied SharedMountPoint import volume-check source change

- Local branch: `main`
- Local commit: `f105fcf043`
- Source Apache commits:
  - `b0b3dc91f5` `fix: support SharedMountPoint volume checks for importVm (#12946)`
- Summary:
  - Record that the current branch already allows `CheckVolumeCommand` on `SharedMountPoint` pools in the KVM wrapper
  - Note that the local branch has since widened the supported set further, so the upstream source change is fully subsumed by the current implementation
- Functional impact:
  - Avoids duplicating a KVM import compatibility fix that is already present in the current branch
  - Keeps the upstream source commit explicitly tracked even though the local implementation now supports a superset of storage pool types
- Validation:
  - Inspection of `LibvirtCheckVolumeCommandWrapper.STORAGE_POOL_TYPES_SUPPORTED` confirmed `SharedMountPoint` is already included
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `e99c249814`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 109 - record the already-satisfied SharedMountPoint import/unmanage source change

- Local branch: `main`
- Local commit: `2e40988604`
- Source Apache commits:
  - `b1bc5380a2` `fix: support SharedMountPoint for KVM volume import and unmanage (#12956)`
- Summary:
  - Record that the current branch already supports `SharedMountPoint` in the KVM volume import and unmanage path
  - Confirm that the local implementation already includes the Apache source change and no extra delta remains to apply
- Functional impact:
  - Avoids reapplying a KVM volume import/unmanage fix whose behavior is already present in the branch
  - Preserves source-level traceability for the upstream SharedMountPoint enhancement
- Validation:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the SharedMountPoint import/unmanage support is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `9ca8c24dc3`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 110 - record the already-satisfied GPU domain parsing source change

- Local branch: `main`
- Local commit: `fa3aad483d`
- Source Apache commits:
  - `416679fae1` `Fix domain parsing for GPU & add Display controller in the supported PCI class (#12981)`
- Summary:
  - Record that the current branch already carries the improved GPU domain parsing and the added Display controller support in KVM GPU discovery
  - Confirm that the earlier GPU compatibility work in this branch already absorbed the upstream source change and its behavioral outcome
- Functional impact:
  - Avoids duplicating a GPU discovery source change whose functionality is already present in the current branch
  - Keeps explicit upstream traceability for the source commit behind the existing GPU parsing behavior
- Validation:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the GPU parsing/discovery update is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `27c1f93f7e`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 111 - record the already-satisfied password reset mail template source change

- Local branch: `main`
- Local commit: `e521155877`
- Source Apache commits:
  - `5013cf2af6` `Fix user password reset mail template value (#12882)`
- Summary:
  - Record that the current branch already contains the finalized SQL update for `user.password.reset.mail.template`
  - Confirm that the schema upgrade now rewrites legacy reset-link placeholder formats to the current `{{{resetLink}}}` form
- Functional impact:
  - Avoids duplicating a schema-only source change whose final behavior is already present in the current branch
  - Keeps explicit upstream traceability for the password-reset mail template correction
- Validation:
  - Inspection of `schema-42200to42210.sql` confirmed the multiline `CONCAT_WS` update statement for `user.password.reset.mail.template` is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the SQL change
  - Schema migration execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8033c4f682`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 112 - record the already-satisfied force-delete cross-management-server propagation source change

- Local branch: `main`
- Local commit: `f2222c9a3e`
- Source Apache commits:
  - `160876c6d7` `Fix: API Thread held forever during force deleting across MS (#12968)`
- Summary:
  - Record that the current branch already propagates `forced` and `forceDeleteStorage` flags through `PropagateResourceEventCommand` for cross-management-server host event handling
  - Confirm that the local `ResourceManagerImpl` path already uses the extended command constructor and peer propagation flow expected by the upstream fix
- Functional impact:
  - Avoids duplicating a clustered resource-management fix whose behavior is already present in the current branch
  - Preserves upstream traceability for the force-delete propagation change that prevents stuck API threads across management servers
- Validation:
  - Inspection of `PropagateResourceEventCommand` and `ResourceManagerImpl.propagateResourceEvent(...)` confirmed the current branch already includes the extra force-delete flags and the corresponding propagation path
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `f7fd36e378`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 113 - record the already-satisfied backup and bucket review follow-up source change

- Local branch: `main`
- Local commit: `552317a397`
- Source Apache commits:
  - `13842a626d` `Address reviews`
- Summary:
  - Record that the current branch already contains the review follow-up changes for the backup and bucket reservation work
  - Confirm that the branch already preserves the refined delete-bucket exception contract and the simplified backup/bucket reservation handling introduced after upstream review
- Functional impact:
  - Avoids duplicating a source follow-up whose behavioral outcome was already absorbed during the earlier backup and bucket reservation batch
  - Keeps the upstream review-adjustment source commit explicitly tracked in the sync history
- Validation:
  - Current branch state in `BackupManagerImpl`, `BucketApiServiceImpl`, and `DeleteBucketCmd` already reflects the post-review API and reservation behavior documented in the earlier local bucket/backup records
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `cadcb40b42`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 114 - record the already-satisfied updateBucket limit-validation source change

- Local branch: `main`
- Local commit: `876f207ba4`
- Source Apache commits:
  - `2511fdffaa` `Implement limit validations on updateBucket`
- Summary:
  - Record that the current branch already validates object-storage quota changes during `updateBucket(...)`
  - Confirm that the update flow now uses reservation-backed quota adjustment instead of changing bucket quota without limit checks
- Functional impact:
  - Avoids duplicating an upstream source change whose updateBucket resource-limit behavior is already present in the branch
  - Preserves explicit traceability for the source commit behind the existing updateBucket quota validation path
- Validation:
  - Reverse-applying the Apache patch on the current branch succeeded, showing the updateBucket limit-validation change is already present
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8f61fdd046`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 115 - record the already-satisfied Routed VR related/established source change

- Local branch: `main`
- Local commit: `9bc48de2e4`
- Source Apache commits:
  - `1fc4cb90bf` `Routed VR: accept packets from related and established connections (#12986)`
- Summary:
  - Record that the current branch already adds the `RELATED,ESTABLISHED` acceptance path to the routed VR forwarding rules
  - Confirm that the current SystemVM network filter flow already reflects the upstream source fix for routed VR traffic handling
- Functional impact:
  - Avoids duplicating a SystemVM source change whose routed VR packet-handling behavior is already present in the branch
  - Preserves explicit upstream traceability for the related/established rule addition
- Validation:
  - Inspection of `systemvm/debian/opt/cloud/bin/cs/CsNetfilter.py` confirmed the routed VR state-match rule already uses the `RELATED,ESTABLISHED` handling introduced by the source fix
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM runtime verification has not been run yet in this environment by request
- Europa cherry-pick status:
  - `3afeeef158`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 116 - record the already-satisfied NSX load-balancer patch-suppression source change

- Local branch: `main`
- Local commit: `97c80615e7`
- Source Apache commits:
  - `05c59630e0` `fix: LB Creation avoid 404 API errors due to non-needed patches (#12835)`
- Summary:
  - Record that the current branch already skips unnecessary NSX LB patch calls when the effective pool/service state is unchanged
  - Confirm that the current NSX client already contains the patch-suppression logic and the related test coverage that avoid 404s on non-needed updates
- Functional impact:
  - Avoids duplicating an NSX source change whose runtime behavior is already present in the branch
  - Preserves explicit upstream traceability for the LB patch-suppression fix
- Validation:
  - Inspection of `NsxApiClient` and `NsxApiClientTest` confirmed the current branch already contains the skip-patch logic and its associated tests
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `0550ec4fa1`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 117 - record the already-satisfied HAProxy idle-timeout source change

- Local branch: `main`
- Local commit: `8c3f389807`
- Source Apache commits:
  - `6e810989b6` `HAProxy Configuration: network.loadbalancer.haproxy.idle.timeout (#12586)`
- Summary:
  - Record that the current branch already supports `network.loadbalancer.haproxy.idle.timeout` in the HAProxy configuration flow
  - Confirm that the local implementation already carries the idle-timeout handling and its related health-check/config wiring
- Functional impact:
  - Avoids duplicating an HAProxy source change whose behavior is already present in the current branch
  - Preserves explicit upstream traceability for the idle-timeout feature source commit
- Validation:
  - Inspection of `HAProxyConfigurator` confirmed the current branch already processes the idle-timeout value and retains the related handling around HAProxy configuration
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM/runtime verification has not been run yet in this environment by request
- Europa cherry-pick status:
  - `c4225d4ef2`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 118 - record the already-satisfied nexthop static-route source change

- Local branch: `main`
- Local commit: `ad2b713279`
- Source Apache commits:
  - `83f705ddc5` `Static Routes with nexthop non-functional for private gateways (#12859)`
- Summary:
  - Record that the current branch already applies nexthop static routes to the required PBR and ACL paths in routed/VPC router handling
  - Confirm that the local SystemVM scripts already carry the shared gateway-device lookup and the related FORWARD rule generation for nexthop routes
- Functional impact:
  - Avoids duplicating a routed-network source fix whose behavior is already present in the branch
  - Preserves explicit upstream traceability for the nexthop static-route repair
- Validation:
  - Inspection of `CsAddress.py` and `CsStaticRoutes.py` confirmed the current branch already contains the nexthop route ACL/PBR handling introduced by the source commit
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - SystemVM runtime verification has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8614ecc20c`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 119 - record the already-satisfied backup pending-job test merge-forward source change

- Local branch: `main`
- Local commit: `ea197ef934`
- Source Apache commits:
  - `f5e75771bc` `merge forwards fix`
- Summary:
  - Record that the current branch already carries the pending-job backup-delete protection covered by the upstream merge-forward test tweak
  - Note that the exact test method signature differs locally, but the guarded delete-backup behavior and surrounding test coverage are already present
- Functional impact:
  - Avoids duplicating a test-only merge-forward adjustment while preserving the fact that the underlying guarded behavior is already covered in the current branch
  - Keeps the upstream follow-up commit explicitly visible in the sync history
- Validation:
  - The current `BackupManagerTest` still covers deletion blocked by pending jobs, and the underlying backup-delete guard is already present from earlier backup reservation work
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the test-only signature tweak
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `8c110c3012`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`

### Record 120 - record the already-satisfied NSX pagination source change

- Local branch: `main`
- Local commit: `0478e72d09`
- Source Apache commits:
  - `e0fe953791` `fix: NSX SDK list operations are pageable: the API returns a non-null and non-empty (#12834)`
- Summary:
  - Record that the current branch already follows NSX cursor chains and merges paged results through `PagedFetcher`
  - Confirm that the current NSX client already uses the pagination helper for list operations that return `cursor`-based result pages
- Functional impact:
  - Avoids duplicating an NSX list-pagination source change whose behavior is already present in the current branch
  - Preserves explicit upstream traceability for the pagination fix that ensures complete NSX datasets are fetched
- Validation:
  - Inspection of `NsxApiClient`, `PagedFetcher`, and `PagedFetcherTest` confirmed the current branch already contains the cursor-following pagination helper and its test coverage
  - This local commit therefore records the satisfied upstream state in the history document instead of duplicating the implementation
  - Maven-based Java test execution has not been run yet in this environment by request
- Europa cherry-pick status:
  - `b22afa163a`
- Conflict notes:
  - `None observed on main`
- Resolution notes:
  - `N/A`


### Record 121 - ONTAP primary storage pool lifecycle operations

- Local branch: `main`
- Local commit: `02cfc88817`
- Source Apache commits:
  - `02cfc88817` Create, Delete, Enable, Disable, Enter, Cancel maintenance of Primary StoragePool with ONTAP storage (#12563)
- Summary:
  - Add the ONTAP primary-storage lifecycle operations needed to enable, disable, enter maintenance, cancel maintenance, delete, and create pool flows consistently
  - Align ONTAP pool handling with the newer managed-primary-storage operation model used by the rest of the stack
- Functional impact:
  - Expands ONTAP storage administration coverage without changing unrelated storage providers
  - Reduces operator-side workflow gaps when maintaining ONTAP-backed primary storage pools
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `eb4136e17c`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 122 - clone existing offerings and update the clone

- Local branch: `main`
- Local commit: `3ac814b3af`
- Source Apache commits:
  - `3ac814b3af` Add support to clone existing offerings and update them (#12357)
- Summary:
  - Add support to clone existing offerings instead of recreating equivalent definitions by hand
  - Preserve follow-up update flows so the cloned offering can be adjusted before use
- Functional impact:
  - Simplifies offering administration and reduces manual configuration drift
  - Keeps offering copy operations explicit and traceable in API/UI flows
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `36750771df`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 123 - enable SharedMountPoint HA heartbeat for KVM

- Local branch: `main`
- Local commit: `2c0995de98`
- Source Apache commits:
  - `2c0995de98` KVM: Enable HA heartbeat on ShareMountPoint (#12773)
- Summary:
  - Enable the KVM HA heartbeat path for `SharedMountPoint` primary storage
  - Treat SharedMountPoint more consistently with the HA heartbeat expectations already used by other shared-storage types
- Functional impact:
  - Improves host-HA health detection when SharedMountPoint-backed KVM pools are in use
  - Reduces the chance of SharedMountPoint pools being skipped by HA heartbeat handling
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `579a22856f`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 124 - refactor quota summary API assembly

- Local branch: `main`
- Local commit: `c3e41d9bd7`
- Source Apache commits:
  - `c3e41d9bd7` Refactor Quota Summary API (#10505)
- Summary:
  - Refactor quota summary construction so response assembly is cleaner and easier to extend
  - Keep the observable quota summary behavior while reducing duplication in the response-building path
- Functional impact:
  - No intended behavioral change for callers of the Quota Summary API
  - Lowers maintenance cost around future quota-summary enhancements and fixes
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `d983c56298`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 125 - add KVM NIC enable and disable API support

- Local branch: `main`
- Local commit: `fe17d4d04d`
- Source Apache commits:
  - `fe17d4d04d` Add API to enable/disable NICs for KVM (#12819)
- Summary:
  - Add the `enabled` state for VM NICs and expose the KVM-specific update flow in API, DB views, and UI
  - Keep the older `linkstate` handling intact while layering the new administrative NIC enable/disable behavior on top
- Functional impact:
  - Allows operators to enable or disable supported KVM NICs without removing the NIC
  - Preserves existing Europa link-state and IP/MAC editing behavior while extending the NIC management surface
- Validation:
  - `main` and `europa` both carry the merged API, DB view, hypervisor, and UI changes
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `da920f25bb`
- Conflict notes:
  - `Nic`, `NicResponse`, `NicVO`, DB views, router join objects, `HypervisorGuruBase`, locale strings, and `NicsTab.vue` all overlapped with Europa-specific link-state work
- Resolution notes:
  - Keep Europa `linkstate`, IP/MAC edit, and UI actions intact while adding the Apache `enabled` field end-to-end
  - Preserve both link-state and enabled-state handling in API responses and KVM NIC update UI flows

### Record 126 - block CKS-member VM unmanage or reinstall operations

- Local branch: `main`
- Local commit: `db08332010`
- Source Apache commits:
  - `db08332010` [4.22] Prevent unmanaging or reinstalling a VM if it is part of a CKS cluster (#12800)
- Summary:
  - Prevent VM unmanage and reinstall operations when the VM is still part of a CKS cluster
  - Add the cluster-membership helper needed to guard those flows consistently from the server layer
- Functional impact:
  - Protects CKS clusters from destructive lifecycle actions that would leave cluster state inconsistent
  - Keeps operator intent explicit by failing earlier on unsafe VM lifecycle requests
- Validation:
  - `main` and `europa` both carry the guard logic and matching test updates
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `0df62891cd`
- Conflict notes:
  - `UserVmManagerImpl` and `UserVmManagerImplTest` overlapped with Europa `vBMC` assignment helpers
- Resolution notes:
  - Preserve Europa `allocateVbmcToVM` and `removeVbmcToVM` handling while adding Apache `isVMPartOfAnyCKSCluster(...)`
  - Extend the local test imports instead of dropping existing Europa coverage helpers

### Record 127 - deduplicate dummy templates and refresh import guest OS mapping

- Local branch: `main`
- Local commit: `2869448c1e`
- Source Apache commits:
  - `2869448c1e` Fix duplicate dummy templates, and update guest os for dummy template (#12780)
- Summary:
  - Prevent duplicate dummy template creation during KVM import flows
  - Refresh the guest OS used by the default KVM import template so later import matching behaves correctly
- Functional impact:
  - Avoids accumulating duplicate dummy templates in import-heavy environments
  - Improves downstream unmanaged-instance import matching for the default KVM template path
- Validation:
  - `main` and `europa` both carry the SQL, storage motion, and unmanaged-import adjustments
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `7d1012e114`
- Conflict notes:
  - `schema-42200to42210.sql`, `StorageSystemDataMotionStrategy`, and `UnmanagedVMsManagerImpl` overlapped with existing Europa import customizations
- Resolution notes:
  - Keep the local SQL tail and import-template helpers, then layer in the Apache dummy-template deduplication and guest-OS refresh logic
  - Preserve the existing unmanaged import template naming/constants while aligning the guest OS defaults

### Record 128 - derive VMware-to-KVM import guest OS from source mappings

- Local branch: `main`
- Local commit: `350d2c3ba2`
- Source Apache commits:
  - `350d2c3ba2` [VMware to KVM] Add guest OS for importing VM based on the source VM OS (#12802)
- Summary:
  - Carry the source guest OS mapping into the VMware-to-KVM import path so the selected guest OS better matches the imported VM
  - Auto-select the mapped guest OS in the import UI when mappings are available
- Functional impact:
  - Improves imported-VM accuracy by avoiding an incorrect or generic guest OS selection
  - Reduces manual operator correction during VMware-to-KVM imports
- Validation:
  - `main` and `europa` both carry the guest-OS mapping updates in server and UI flows
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `b0b8f54ea8`
- Conflict notes:
  - `ImportUnmanagedInstance.vue` and `ManageInstances.vue` overlapped with Europa watcher logic and import-task state customizations
- Resolution notes:
  - Keep Europa resource watchers, task filters, and auto-refresh handling while adding the Apache guest-OS mapping selection logic
  - Preserve local import-task UX state and only layer in the new guest-OS mapping behavior

### Record 129 - handle ALL-port firewall rules during CKS scale and delete

- Local branch: `main`
- Local commit: `1a40fc72de`
- Source Apache commits:
  - `1a40fc72de` Fix K8s scaling and deletion issue if firewall rule is for ALL ports (#12806)
- Summary:
  - Fix CKS scaling and deletion flows so firewall rules defined for all ports do not break cleanup and update logic
  - Normalize the affected rule handling in the CKS orchestration path
- Functional impact:
  - Prevents CKS cluster operations from failing when broad firewall rules are already attached
  - Reduces stuck scale/delete workflows caused by rule-shape assumptions
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `38ed1cbef4`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 130 - reserve secondary storage resources for upload operations

- Local branch: `main`
- Local commit: `5dac21b5cb`
- Source Apache commits:
  - `5dac21b5cb` [22.0] secondary storage resource limit for upload
- Summary:
  - Add resource reservation checks for secondary-storage uploads before the transfer starts
  - Align upload flows with the broader secondary-storage quota and reservation model
- Functional impact:
  - Prevents overcommitting secondary storage during upload operations
  - Makes quota failures happen earlier and more predictably for upload workflows
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `fa99e94ad7`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 131 - follow up upload monitor reservation merge cleanup

- Local branch: `main`
- Local commit: `b3614473ca`
- Source Apache commits:
  - `b3614473ca` storage: fix upload monitor limit merge cleanup
- Summary:
  - Clean up the upload-monitor follow-up after the reservation-aware secondary-storage changes
  - Keep the upload monitor flow internally consistent after the earlier limit-enforcement backport
- Functional impact:
  - Reduces merge-forward drift in the upload monitor path without broadening user-visible behavior
  - Keeps the reservation-aware upload code path coherent for later maintenance
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `d9bdb38905`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 132 - reserve secondary storage resources for download operations

- Local branch: `main`
- Local commit: `79387430f4`
- Source Apache commits:
  - `79387430f4` [22.0] secondary storage resource limit for download
- Summary:
  - Add resource reservation checks for secondary-storage downloads before data movement begins
  - Pair the download path with the earlier upload-side reservation handling
- Functional impact:
  - Prevents download workflows from silently exceeding secondary-storage allocation limits
  - Makes operator-visible failures happen at request time instead of later during transfer
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `461d51a498`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 133 - treat infinite secondary-storage limits correctly during upload checks

- Local branch: `main`
- Local commit: `d600fdd363`
- Source Apache commits:
  - `d600fdd363` Consider infinite resources when calculating secondary storage limit for upload operations
- Summary:
  - Honor effectively-infinite secondary-storage limits instead of treating them as bounded upload capacity
  - Keep upload reservation logic consistent with the semantics of unlimited quotas
- Functional impact:
  - Prevents false-positive quota failures for accounts or domains with unlimited secondary-storage settings
  - Reduces noisy operator intervention on otherwise valid upload requests
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `1edc102946`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 134 - fix ImageStoreUploadMonitor follow-up merge issue

- Local branch: `main`
- Local commit: `bc6ac3ef25`
- Source Apache commits:
  - `bc6ac3ef25` Fixed a merge issue in ImageStoreUploadMonitorImpl
- Summary:
  - Resolve the lingering merge issue in `ImageStoreUploadMonitorImpl` after the upload reservation work
  - Keep the image-store upload monitor aligned with the intended reservation-aware logic
- Functional impact:
  - Low-risk internal cleanup that prevents the upload monitor from drifting away from the corrected limit path
  - Helps keep later secondary-storage fixes easier to reason about
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `42d4ca4307`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 135 - support conserve mode on VPC offerings

- Local branch: `main`
- Local commit: `8550d45ae7`
- Source Apache commits:
  - `8550d45ae7` Add conserve mode for VPC offerings (#12487)
- Summary:
  - Add conserve-mode handling for VPC offerings instead of limiting the behavior to other network-offering classes
  - Expose the related offering behavior consistently in the VPC flow
- Functional impact:
  - Lets operators define VPC offerings that conserve resources until services are explicitly required
  - Brings VPC offering behavior closer to the already-supported non-VPC offering model
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `d8646c2c1a`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 136 - fix VMware-to-KVM migration instance listing failures

- Local branch: `main`
- Local commit: `59cb77b6f4`
- Source Apache commits:
  - `59cb77b6f4` [Fix] VMware to KVM migration instances listing failure (#12766)
- Summary:
  - Fix the instance-listing path used by VMware-to-KVM migration discovery so manageable candidates are returned reliably
  - Remove failure cases caused by assumptions in the source-instance listing logic
- Functional impact:
  - Prevents VMware-to-KVM migration workflows from failing before import starts
  - Improves operator confidence in source-VM discovery and selection
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `2795390f8c`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 137 - allow affinity group selection during CKS cluster creation

- Local branch: `main`
- Local commit: `2629d5f5ba`
- Source Apache commits:
  - `2629d5f5ba` CKS: Allow affinity group selection during cluster creation (#12386)
- Summary:
  - Extend CKS cluster creation so affinity groups can be selected during the initial request
  - Carry the chosen affinity-group settings through the relevant API and UI creation path
- Functional impact:
  - Improves placement control for new CKS clusters
  - Reduces the need for follow-up manual adjustments after cluster creation
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `0399cdfe22`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 138 - clear system VM public NIC addresses for PublicNetworkGuru

- Local branch: `main`
- Local commit: `ed3d3f22e4`
- Source Apache commits:
  - `ed3d3f22e4` Clear System VM IP from NICs for PublicNetworkGuru (#11992)
- Summary:
  - Clear stale System VM public-NIC IP information during `PublicNetworkGuru` NIC handling
  - Keep public-network NIC state aligned with the intended system-VM allocation flow
- Functional impact:
  - Prevents stale or misleading System VM NIC state from leaking into later network operations
  - Lowers the chance of incorrect PublicNetworkGuru assumptions during NIC orchestration
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `3faa5129f9`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 139 - move API key usage to the latest stored key pair

- Local branch: `main`
- Local commit: `f1104735d2`
- Source Apache commits:
  - `f1104735d2` API key pair restructure (#9504)
- Summary:
  - Refactor API-key handling to look up the latest stored key pair instead of reading keys directly from the user record in affected flows
  - Update request-signing and autoscale integration paths to use the restructured key lookup model
- Functional impact:
  - Aligns runtime behavior with the newer multi-key-pair model
  - Reduces risk of using stale or structurally outdated API/secret key data in server workflows
- Validation:
  - `main` and `europa` both carry the latest-key-pair lookup changes across DAO, server, autoscale, and account paths
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `1a367f0dd5`
- Conflict notes:
  - `UserAccountDao`, `UserAccountDaoImpl`, `QueryManagerImpl`, `AutoScaleManagerImpl`, `ManagementServerImpl`, `AccountManagerImpl`, `UserVmManagerImpl`, and `AccountManagerImplTest` overlapped with existing Europa extensions
- Resolution notes:
  - Preserve Europa-only helper methods and imports while switching runtime key usage to `ApiDBUtils.searchForLatestUserKeyPair(...)`
  - Keep local Keycloak/Glue/Wall flows and add the Apache key-removal helper where required

### Record 140 - remove unused VMware-to-KVM convert environment variables

- Local branch: `main`
- Local commit: `f19bcc499e`
- Source Apache commits:
  - `f19bcc499e` [VMware to KVM Migration] Fix unused convert env vars (#11947)
- Summary:
  - Remove unused conversion-environment plumbing from the VMware-to-KVM migration flow
  - Keep the import path focused on the variables and options that are actually consumed
- Functional impact:
  - Low-risk internal cleanup that reduces confusion in the VMware-to-KVM conversion path
  - Makes later conversion-path debugging easier by removing dead configuration branches
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `767aeab043`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 141 - clean up imported VM artifacts on allocation failure

- Local branch: `main`
- Local commit: `c9f0d6e39f`
- Source Apache commits:
  - `c9f0d6e39f` Cleanup imported VM from disk on failure due to volume allocation + prevent duplicate volume and primary storage increment on import
- Summary:
  - Clean up imported VM artifacts from disk when the workflow fails during volume allocation
  - Prevent duplicate volume and primary-storage resource increments during import failure handling
- Functional impact:
  - Reduces leaked imported artifacts and resource-count skew after failed imports
  - Makes VMware-to-KVM and unmanaged import recovery more predictable for operators
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `da94b79294`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 142 - add VDDK-backed VMware-to-KVM migration support

- Local branch: `main`
- Local commit: `a8a4d7a362`
- Source Apache commits:
  - `a8a4d7a362` Added VDDK support in VMware to KVM migrations (#12970)
- Summary:
  - Add VDDK-backed direct VMware-to-KVM conversion support alongside the existing OVF-based flow
  - Extend the API, agent, KVM wrapper, server orchestration, and UI so operators can select VDDK-backed imports when the host supports it
- Functional impact:
  - Improves VMware-to-KVM migration flexibility and can reduce intermediate export handling in supported environments
  - Keeps Europa `Ablestack V2K` custom flow intact while exposing Apache VDDK behavior for the standard import path
- Validation:
  - `main` and `europa` both carry the merged VDDK server, KVM wrapper, and UI changes
  - `git diff --check` was clean after conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `9713ecd26f`
- Conflict notes:
  - `LibvirtConvertInstanceCommandWrapper`, `UnmanagedVMsManagerImpl`, and `ImportUnmanagedInstance.vue` all overlapped with Europa VMware-to-KVM and Ablestack V2K customizations
- Resolution notes:
  - Preserve Europa `Ablestack V2K`, SharedMountPoint/RBD handling, and existing UI import options while adding upstream VDDK controls and server-side support
  - Restore `convertinstancehostid` and `convertinstancepoolid` handling for the Europa V2K path while keeping VDDK-specific behavior confined to the standard VMware-to-KVM flow

### Record 143 - expose redundant-network restart control in the UI

- Local branch: `main`
- Local commit: `3306995626`
- Source Apache commits:
  - `3306995626` Enable defining a network as redundant during restart through the UI (#7405)
- Summary:
  - Expose the redundant-network toggle through the UI restart network workflow
  - Bring the restart flow closer to the already-supported API capability
- Functional impact:
  - Lets operators request redundant-network behavior during restart without dropping to the API
  - Improves parity between API and UI for network restart operations
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `fd8b981fa5`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 144 - improve PowerFlex and ScaleIO client initialization handling

- Local branch: `main`
- Local commit: `5bac2c8310`
- Source Apache commits:
  - `5bac2c8310` PowerFlex/ScaleIO client initialization, authentication and command execution improvements (#12391)
- Summary:
  - Improve PowerFlex/ScaleIO client initialization, authentication, and command execution handling
  - Tighten the provider-side error handling around ScaleIO/PowerFlex operations
- Functional impact:
  - Reduces provider-side failures caused by brittle initialization or authentication sequencing
  - Makes storage-provider troubleshooting easier when ScaleIO/PowerFlex commands fail
- Validation:
  - Matching commits are present on `main` and `ablestack-europa`
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `c5ebe3d17d`
- Conflict notes:
  - `None recorded in current sync notes`
- Resolution notes:
  - `N/A`

### Record 145 - reserve resources before creating volumes

- Local branch: `main`
- Local commit: `091fa8c75c`
- Source Apache commits:
  - `091fa8c75c` [22.0] resource reservation on volume creation
- Summary:
  - Reserve volume and primary-storage resources before committing volume creation
  - Fail earlier when quota or storage reservations cannot be satisfied instead of allocating partially and rolling back later
- Functional impact:
  - Improves quota correctness during volume creation under concurrency
  - Reduces the chance of resource-count drift around failed or racing volume-create requests
- Validation:
  - `main` and `europa` both carry the reservation-aware volume-create flow
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `962821ecbe`
- Conflict notes:
  - `VolumeApiServiceImpl` overlapped with the Europa `kvdoEnable` volume-create extension
- Resolution notes:
  - Keep the Apache `CheckedReservation` try-with-resources structure and its exception handling
  - Preserve the Europa `kvdoEnable` argument when calling the local `commitVolume(...)` overload

### Record 146 - fix snapshot copy reservation concurrency handling

- Local branch: `main`
- Local commit: `ca9227dcc7`
- Source Apache commits:
  - `ca9227dcc7` Fix snapshot copy resource limit concurrency
- Summary:
  - Correct the snapshot-copy resource-reservation path so concurrent snapshot copy flows do not double-count or race their limit handling
  - Remove the stale duplicate post-processing increments left behind by the older copy path
- Functional impact:
  - Improves snapshot copy quota correctness under concurrent zone-copy activity
  - Prevents resource-count skew after copy operations that already reserve and account for snapshot resources earlier in the flow
- Validation:
  - `main` and `europa` both carry the updated snapshot copy reservation flow and test adjustments
  - `git diff --check` was clean after the conflict resolution
  - Runtime build/test execution remains deferred by request in this workspace
- Europa cherry-pick status:
  - `fe03ece06d`
- Conflict notes:
  - `SnapshotManagerImpl` retained an older duplicate resource-count increment block after the Apache reservation refactor
- Resolution notes:
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

## Refined Resource-Limit Batches

### R01 - Backup / Bucket Reservation Core

- Apache commits:
  - `19b4ef1069`, `13842a626d`, `2511fdffaa`
- Scope:
  - Reservation-aware limit checks for backup create/delete and bucket alloc/delete/update flows
- Why grouped:
  - These three commits evolve the same operational path from initial reservation support to review fixes and `updateBucket` completion

### R02 - Secondary Storage Transfer Limits

- Apache commits:
  - `03dfe4d1f3`, `81a8ac8e1f`
- Scope:
  - Download/upload resource counting and reservation behavior on secondary storage transfer paths
- Why grouped:
  - Both commits govern transient secondary storage consumption during template/image movement and are likely to share supporting context

### R03 - Snapshot Copy Reservation Concurrency

- Apache commits:
  - `8608b4edd0`
- Scope:
  - Snapshot copy concurrency handling for resource limit reservations
- Why separate:
  - Touches snapshot management only and can be validated independently from backup/object storage flows

### R04 - VM Start Reservation Validation

- Apache commits:
  - `4bcd509193`
- Scope:
  - Reservation and limit validation during `StartVirtualMachine`
- Why separate:
  - High runtime sensitivity and likely overlap with Europa VM lifecycle customizations

### R05 - Reserved Resource Details Exposure

- Apache commits:
  - `95816b44e9`
- Scope:
  - API/UI exposure of reserved resource details for extensions and VM views
- Why separate:
  - This is an API/UI visibility change rather than a backend reservation enforcement change

## Initial Candidate Notes

### B00 - Metadata / CI / docs housekeeping

- Candidate Apache commits:
  - `608345d165` Update collaborators list in `.asf.yaml`
  - `9cc6c09b9e` Remove broken ViserJS attribution link from UI README
  - `9bbd32a8ef` Add contributor metadata
  - `d8f748ad0e` Update `.asf.yaml`
  - `b744824f65` Add code owners for NSX plugin
  - `6bcbb008b4` Bump `actions/checkout` to `v6`
  - `cf9bda2050` Add github-actions ecosystem to Dependabot
  - `5d95bdd0eb` pre-commit trailing whitespace auto clean up
  - `5d61ba3538` codespell and hook update
- Notes:
  - Safe starter batch for local/main commit workflow
  - Not all commits may need Europa cherry-pick if they do not affect runtime behavior

### B01 - Resource limits / quota / reservation

- Candidate Apache commits:
  - `37e3657770`, `003c840817`, `8d269cf5be`, `831ef82ff9`, `1f849caa0b`
  - `3d678e726a`, `d11d182c71`, `4855d40e6e`, `d722415105`, `07c3dc86b2`
  - `89df318164`, `4dd91feb27`, `1593944553`, `7faa1b650b`, `b025e85fc5`
  - `e0ef3a6947`, `06ee2fea76`, `4bcd509193`, `03dfe4d1f3`, `81a8ac8e1f`
  - `360b64ce1e`, `0a4b4c6af0`, `dc7068a135`, `9c0c8da706`, `e8d57d1b0d`
  - `4f93ba888c`, `19b4ef1069`, `2511fdffaa`
- Notes:
  - Highest functional risk area
  - Expect conflicts in `api`, `server`, `engine/schema`, `plugins/database/quota`
  - Duplicate release-line backports must be collapsed into one local change set

### B02 - Backup / volume / snapshot / import flows

- Candidate Apache commits:
  - `5d5ee7b689`, `f7f0e75122`, `88a12a801f`, `8ce1c9876e`, `24fd440ee7`
  - `86c9f7bd94`, `8608b4edd0`, `c19630f0a4`, `84676afd5c`, `b22dbbe2d7`
  - `2416db2a44`, `131ea9f7ac`, `6ca6aa1c3f`, `4ebe3349b7`, `e2497cfc4d`
  - `b0b3dc91f5`, `b1bc5380a2`, `03de62bf38`, `7ba5240b31`, `1ff9eec997`
  - `68bd056306`, `7b467496cb`, `2a60305792`, `8f3c6fad7a`, `df7ff97271`
  - `d75acb6efc`, `0c86899cc1`
- Notes:
  - Strong overlap with Europa customizations is likely
  - Storage provider-specific behavior must be reviewed before direct cherry-pick

### B03 - Network / VPC / LB / NSX / VR

- Candidate Apache commits:
  - `7ad68aafa5`, `2359061f66`, `27bce46a8e`, `09ee0927e9`, `93239e09f1`
  - `30dd234b00`, `abdf926219`, `ae455ee193`, `1fc4cb90bf`, `05c59630e0`
  - `e0fe953791`, `6e810989b6`, `83f705ddc5`
- Notes:
  - High probability of semantic conflicts on Europa networking behavior
  - Resolve based on current Europa service assumptions and API compatibility

### B04 - Hypervisor / KVM / VMware / CKS

- Candidate Apache commits:
  - `6419e1c825`, `9e386a3128`, `8c579538f9`, `7048944883`, `b497f58022`
  - `7107d28db8`, `7c3637a2f5`, `7cdcf571fa`, `c1af36f8fc`, `71bd26ff7c`
  - `18075ae4a9`, `7eea9ed448`, `e297644ce1`, `273699cf56`
- Notes:
  - Medium conflict risk, but runtime validation on KVM and CKS paths is required

### B05 - UI / UX / config defaults

- Candidate Apache commits:
  - `120a43648b`, `db83622956`, `7aa0558c5b`, `71daf84c9e`, `59b6c32b60`
  - `9f57a4dd19`, `ed575cc0a1`
- Notes:
  - Good early cherry-pick candidates after metadata batch
  - UI build and localization regression checks are required

### B06 - Async jobs / account / user / API ergonomics

- Candidate Apache commits:
  - `74af9b9875`, `470812100e`, `b5858029bb`, `416679fae1`, `b196e97cc3`
  - `47c5bb8ee7`, `38abe2df0b`, `5013cf2af6`, `160876c6d7`, `13842a626d`
- Notes:
  - Review API response compatibility before Europa propagation
