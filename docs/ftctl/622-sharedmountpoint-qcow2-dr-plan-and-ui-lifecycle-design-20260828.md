# SharedMountPoint QCOW2 DR Plan and UI Lifecycle Design

## 1. Authority and Test Object

The 31 ABLESTACK cluster owns DR site, plan, run, replica, and UI state. The 13
cluster is controlled through its registered Mold API and source Agent. The
only test object is the existing plan:

- plan: `rocky9-vm DR Plan`
- UUID: `41886f03-c19e-4382-927d-89bc4d6ce8e9`
- source VM: `48bdce4a-8bba-4984-80f1-46b1c92042cd`
- source/target storage: `/mnt/glue-gfs`, SharedMountPoint, qcow2

Updating the existing plan is allowed. Creating a replacement site or plan is
outside scope.

## 2. Inventory Corrections

Remote Mold inventory must use the same KVM conventions as local inventory:

- KVM VM with a non-empty `UEFI` detail is UEFI; `SECURE` means secure boot.
- KVM VM without a `UEFI` detail is BIOS/LEGACY, not unknown.
- RBD volumes are raw.
- SharedMountPoint file volumes are qcow2 unless the API supplies a more
  specific format.
- Source paths remain absolute canonical paths for the Agent profile.

The guided-spec preview must therefore remove
`SOURCE_HARDWARE_INVENTORY_REQUIRED` for the existing BIOS VM and emit a
file/qcow2 disk mapping.

Implementation is isolated in `DrMoldInventoryClient`: remote KVM inventory
uses the same absent-UEFI BIOS default as local inventory, and volume format is
resolved from an explicit API value, pool type, then path extension. Unit tests
preserve the existing UEFI and RBD/raw contracts.

## 3. Async UI/API Flow

All commands remain asynchronous:

1. UI submits an action and receives an accepted Cloud run.
2. Backend persists intent and dispatches Agent work.
3. Agent invokes FTCTL and returns acceptance/runtime evidence.
4. Cloud polling projects run, cycle, replica, and readiness state.
5. UI shows accepted, transferring, materializing, boot verification, and
   terminal result separately.

The UI never calls FTCTL, QMP, libvirt, or a remote Mold API directly.

## 4. Cloud-owned Materialization

Cloud creates/imports the target qcow2 volume in the selected SharedMountPoint
pool and creates the target VM only after a durable checkpoint. The mapping
keeps the absolute engine path while the Cloud volume row and resource
ownership records remain authoritative for VM lifecycle actions.

Firmware, secure boot, disk controllers, I/O threads, `io.policy=io_uring`, CPU,
memory, network, offering, and volume format are copied from the resolved
contract. Materialization must fail on a mismatch rather than silently create
a different VM.

## 5. UI Completion Matrix

The existing plan is exercised in the 31 UI in this order:

1. plan update/preview;
2. full synchronization;
3. automatic incremental synchronization and RPO display;
4. pause/resume and full resynchronization;
5. test failover and test cleanup;
6. failover;
7. reprotect and failback;
8. release with both target-retain and target-delete dispositions;
9. plan delete after resource disposition verification.

Each action passes only when UI terminal state agrees with API, DB, Agent,
FTCTL, target file, and target VM state. A modal close or accepted job is not a
PASS.

## 6. Dark Mode and Status

Existing DR components and tokens are reused. No raw JSON is shown. File/qcow2
provider, bitmap health, target export, and transfer progress are exposed as
typed labels. Warning and disabled states use the established dark-mode tokens;
hard-coded light backgrounds or black text are prohibited.

## 7. Regression and Deployment

- Changed Maven modules are built from a WSL ext4 clone.
- FTCTL packages are built through GitHub Actions.
- UI static assets are deployed without replacing the active webapp or
  deleting `WEB-INF`/`META-INF`.
- VMware-to-RBD and RBD-to-RBD action-contract suites are mandatory gates.
- Deployment markers and installed host scripts are verified on 13 and 31
  before the existing plan is run.
- The changed disaster-recovery Maven module must pass
  `DrMoldInventoryClientTest`; the FTCTL release must pass both the existing
  remote-RBD smoke and the new SharedMountPoint qcow2 smoke.
- Live preflight evidence must include a full seed, an actual guest write,
  bitmap-observed changed bytes, incremental application, and equal source and
  target logical hashes before the existing plan is submitted from the UI.

## 8. AS-IS / TO-BE

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| Inventory | Remote BIOS and qcow2 unresolved | Local/remote KVM semantics identical |
| API | Guided spec contains hardware blocker/raw format | BIOS/LEGACY and file/qcow2 contract |
| Backend | Target transport assumes RBD wording/behavior | Provider-capability dispatch |
| Agent | Target file export not accepted | Validated SharedMountPoint qcow2 export |
| FTCTL | RBD-only incremental | QMP bitmap push backup for qcow2 |
| DB | Existing plan stores stale preview evidence | Same plan updated, no duplicate plan |
| UI | Cannot complete the existing plan | Full menu lifecycle with terminal evidence |

## 9. Source format contract and failed-seed recovery

The first UI-driven full synchronization of the existing plan proved that the
source volume path does not carry a filename extension. The file is qcow2, but
the guided-spec sanitizer discarded the inventory `format` field. FTCTL then
treated the empty format as raw, the full seed failed before transfer, and the
next scheduler attempt incorrectly selected incremental mode solely because
the sequence number had advanced.

The corrected contract is intentionally narrow:

- the UI preserves the source inventory `type` and `format` in both the flat
  disk mapping and nested `source` object;
- Cloud guided-spec sanitization preserves those fields;
- FTCTL may inspect a missing format with `qemu-img info --force-share` only
  for an ABLESTACK-to-ABLESTACK local file source;
- VMware/VDDK and RBD sources are never inferred or rewritten;
- an ABLESTACK-to-ABLESTACK plan without a durable completed checkpoint always
  retries a full seed, regardless of the failed sequence number;
- provider-specific failures identify the ABLESTACK mover instead of the
  VMware mover.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| UI | Source type/format are dropped when disk rows are rebuilt | Inventory type/format survive create and edit serialization |
| Cloud | Guided spec keeps target format only | Source and target type/format are preserved |
| FTCTL | Extensionless qcow2 becomes an empty format and falls through as raw | ABLESTACK file source is inspected and canonicalized before dispatch |
| Scheduler | Failed sequence 1 is followed by incremental sequence 2 | No durable baseline means full seed retry only |
| Observability | Native replication failure is attributed to `vmware-mover` | Failure component follows the selected provider |

## 10. Remote KVM source authority

The controller-local `dr_plan.source_worker_host_id` remains a foreign key for
hosts owned by the controller Mold only. It must not contain a host ID from a
remote source Mold. For a remote ABLESTACK source, the signed Mold inventory is
authoritative and the guided mapping stores `sourceHostUuid` and
`sourceHostName` under `source.hardware`.

KVM virtual machines without an explicit UEFI detail use the existing Cloud
contract: no UEFI detail means BIOS/legacy firmware and Secure Boot is false.
The absence of the optional UEFI detail must not discard otherwise valid source
host authority. `DrPlanResponse` therefore exposes the remote source worker
UUID and name separately, and the UI displays the local host ID or the remote
name/UUID as appropriate.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| Source inventory | Missing optional UEFI data replaces valid host data with an inventory error | Remote KVM defaults to BIOS and preserves host UUID/name |
| Plan persistence | Remote host cannot be represented by the local host foreign key | Remote host authority remains in `mapping_json.source.hardware` |
| API | Only controller-local `sourceworkerhostid` is exposed | Remote `sourceworkerhostuuid` and `sourceworkerhostname` are also exposed |
| UI | Remote source worker is shown as `-` | Remote source worker name and UUID are shown consistently |
| Existing-plan edit | An unchanged form omits the guided fields, so persisted source inventory errors survive a successful edit | A KVM source mapping with an inventory error or missing remote host UUID resubmits the full guided payload and refreshes source authority |
| Direction vocabulary | Refresh gating recognizes only the internal `KVM_TO_KVM` value | Refresh gating accepts the API/UI `ABLESTACK_TO_ABLESTACK` value and the mapping's internal `KVM_TO_KVM` value while excluding VMware sources |
| Detail authority display | The source worker row depends on API convenience fields and can show `-` even after mapping repair | The detail view falls back to `mappingjson.source.hardware.sourceHostName/sourceHostUuid` so the remote authority remains visible |

## 11. Existing-plan-only validation and completed transfer telemetry

Validation reuses the operator-created plan
`41886f03-c19e-4382-927d-89bc4d6ce8e9`. The implementation and test flow must
not create a replacement site or DR plan.

Each SharedMountPoint full seed records per-disk transferred bytes in the
durable checkpoint. The qcow2 bitmap push path uses the provider result
`changedBytes`; other ABLESTACK full-seed paths use the resolved source virtual
size as the conservative transfer value. The scheduler then publishes those
checkpoint values as `latest_completed_*`, Cloud projects the canonical
completed Cycle into `dr_sync_cycle`, and the protection UI reads that Cycle.

`READY` with a completed non-empty full seed displayed as `0 B` is not a UI
PASS when the FTCTL progress journal proves a non-zero transfer. UI PASS
requires the completed Cycle sequence, mode, byte counts, and durability
timestamps to agree across FTCTL, Cloud DB, and the protection tab.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL full seed | Progress journal has bytes but the durable checkpoint omits them | Aggregate per-disk bytes and persist changed/read/written/payload metrics |
| Cloud Cycle | Completed Cycle receives `NULL` metrics | Canonical Cycle receives checkpoint metrics during normal projection |
| UI | Missing metrics are rendered as `0 B` | The existing plan shows the completed full-seed transfer amount |
| Regression scope | A telemetry fix could alter another provider path | Change is limited to ABLESTACK full-seed checkpoint creation; VMware and RBD mechanics remain unchanged |

## 12. Zero-change incremental cycle contract

The existing plan's source scheduler completed automatic SharedMountPoint
qcow2 cycles, but Cloud retained Cycle 5 while FTCTL advanced through later
cycles. The periodic projection scheduler was running; its Agent status
validation rejected each completed cycle as
`DR_STATUS_CYCLE_EVIDENCE_CONFLICT` because the checkpoint reported zero
changed and written bytes with `effectiveMode=CBT_INCREMENTAL`.

The shared DR status contract already defines a zero-byte durable cycle as
`NO_CHANGE`. The contract validator must remain strict because weakening it
would also change the validated VMware-to-RBD and RBD-to-RBD paths. The
ABLESTACK driver therefore normalizes only its completed-cycle evidence:

- `requestedMode` remains `CBT_INCREMENTAL` because the scheduler requested an
  incremental cycle;
- `effectiveMode` is `NO_CHANGE` when the aggregate changed byte count is zero;
- `effectiveMode` remains `CBT_INCREMENTAL` when at least one byte changed;
- durability, bitmap advancement, Cycle token, and NBD teardown evidence are
  still required and are not inferred by Cloud;
- the rule applies uniformly to ABLESTACK remote RBD, SharedMountPoint qcow2
  bitmap push, and site-agent NBD implementations.

After deployment, the existing Plan must converge without DB repair: the next
automatic cycle is projected, stale active Cycle aliases are superseded, the
Plan runtime reaches `READY/IDLE`, and the cached protection view follows the
latest completed sequence. No replacement Site or Plan may be created.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL | Zero-byte incremental completion is labeled `CBT_INCREMENTAL` | Requested mode stays incremental; effective mode is `NO_CHANGE` |
| Agent | Correctly rejects zero-byte `CBT_INCREMENTAL` evidence | Existing strict validation accepts the corrected `NO_CHANGE` evidence |
| Cloud | Projection cache remains on a stale active Cycle | Periodic projection consumes the next valid completed Cycle |
| DB | Plan runtime sequence trails the source scheduler | Runtime and canonical Cycle advance atomically without manual repair |
| UI | Existing Plan appears indefinitely syncing | Existing Plan converges to `READY/IDLE` and shows the latest durable Cycle |

## 13. SharedMountPoint full-reseed byte authority

The existing Plan's UI-triggered full reseed completed in QEMU, but the
durable checkpoint used `changedBytes=0` from bitmap initialization even
though the same provider result contained non-zero `bytesProcessed`,
`sourceReadBytes`, and `targetWrittenBytes`. The strict Agent contract then
correctly rejected a zero-byte `FULL_SEED` completion as conflicting evidence,
leaving Cloud Cycle 15 in `TRANSFERRING` after the request Run had succeeded.

The common Agent and Cloud validators remain unchanged. For an ABLESTACK
SharedMountPoint full seed, FTCTL selects the first positive value from the
provider's target-written, source-read, processed, payload, and changed-byte
fields. A non-qcow2 provider still uses the resolved virtual-size fallback.
Incremental byte accounting and the previously validated VMware and RBD paths
are not changed.

After deployment, validation reuses Plan
`41886f03-c19e-4382-927d-89bc4d6ce8e9` only. A new UI full reseed must publish
non-zero full-seed metrics, allow the Agent to accept the terminal Cycle, mark
the previous incomplete alias `SUPERSEDED`, and converge the protection view
to `READY / IDLE` without direct DB repair.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL qcow2 full seed | Persists bitmap `changedBytes=0` despite a completed full copy | Persists the actual processed/read/written byte count |
| Agent | Rejects zero-byte `FULL_SEED` as an evidence conflict | Accepts unchanged strict contract after provider evidence is corrected |
| Cloud | Request Run succeeds while its Cycle remains `TRANSFERRING` | Canonical Cycle is completed and stale alias state is superseded |
| UI | Shows `RESEEDING / DEGRADED` after the copy finishes | Shows non-zero transfer metrics and returns to `READY / IDLE` |

## 14. SharedMountPoint replica locator and test artifact authority

The existing Plan `41886f03-c19e-4382-927d-89bc4d6ce8e9` exposed a path
authority defect after its full seed and automatic incremental cycle completed.
The target pool was `/mnt/glue-gfs` with type `SharedMountPoint`, while the
Cloud volume path and disk mapping intentionally stored the pool-relative path
`rocky9-vm-dr-disk-0`. The target Agent passed that relative path directly to
`qemu-nbd`, whose working directory was `/`; the resulting replica was opened
as `/rocky9-vm-dr-disk-0` instead of the Cloud-managed
`/mnt/glue-gfs/rocky9-vm-dr-disk-0`. Test failover then correctly rejected the
relative artifact locator before creating a test VM.

The corrected contract keeps the successful provider paths isolated:

- for `SharedMountPoint` file disks only, a pool-relative target path is
  resolved beneath the absolute pool root before any file creation or export;
- an already absolute file path is preserved;
- an empty pool root, an absolute-path escape, `..` traversal, or a result
  outside the configured pool is rejected before `qemu-nbd` starts;
- RBD locator construction and the validated VMware-to-RBD and RBD-to-RBD
  paths are unchanged;
- the Cloud test artifact manifest applies the same normalization, so the
  test clone reads the same durable file that the target export writes and the
  Cloud target volume resolves;
- the target export status returns the canonical absolute `targetPath` and the
  UI reports test failover success only after the test VM is created and its
  configured boot validation succeeds.

Deployment validation must stop the stale target export that holds the root
filesystem file, install the patched FTCTL and Cloud classes, and reuse the
same Plan for a UI full reseed. PASS requires the new export process and
checkpoint to reference `/mnt/glue-gfs/rocky9-vm-dr-disk-0`, followed by UI
test failover, test cleanup, failover, and failback. No replacement Site or DR
Plan may be created. The obsolete root-level file may be removed only after
the corrected GFS replica is durable and no process has it open.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL target export | Opens the relative target path from `/` | Opens a validated absolute path below `/mnt/glue-gfs` |
| Cloud artifact spec | Rejects the relative mapping without resolving the pool root | Emits the same canonical SharedMountPoint file locator as FTCTL |
| Target volume | Logical Cloud path and physical export diverge | Cloud pool root plus volume path equals the physical replica |
| UI test failover | Request Run fails before a test VM exists | Progress reflects artifact preparation, VM creation, boot validation, and terminal completion |
| Regression scope | A generic file-path relaxation could affect other providers | Normalization is limited to SharedMountPoint; RBD and VMware contracts remain unchanged |

## 15. Artifact-free failed test session terminalization

The existing Plan must remain the only validation object. A failed test
failover Run can stop before FTCTL creates an artifact, Cloud volume, or test
VM. In that case the `dr_test_session` row previously remained `REQUESTED`, so
the UI incorrectly replaced **Test Failover** with **Test Cleanup** even though
there was nothing to delete.

Cloud now treats a test session as artifact-free terminal history only when all
of the following facts agree: its owning Run is `FAILED` or `CANCELED`, the
session is still before materialization, `artifact_manifest` is empty,
`target_vm_id` is null, and `cleanup_required=false`. The Run executor closes
the session with the Run failure in the same failure flow. Action evaluation
ignores a legacy row that satisfies the same strict proof, and submitting a new
UI test on the existing Plan soft-closes that legacy row transactionally before
creating the replacement session.

Any artifact manifest, target VM, cleanup obligation, or cleanup failure keeps
the existing **Test Cleanup** requirement. No DB repair, new Site, or new Plan
is part of the recovery path.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| Cloud Run | Adapter failure terminates only `dr_run` | Artifact-free `dr_test_session` is terminated with the failed Run |
| Action availability | Stale `REQUESTED` session is treated as an active test | Terminal artifact-free history does not block **Test Failover** |
| Orchestrator | New test request is rejected by the stale row | The stale row is safely soft-closed in the existing-Plan transaction |
| Resource safety | Operator is offered cleanup with no resources | Cleanup remains mandatory whenever any artifact or target VM exists |
| UI validation | Existing Plan shows **Test Cleanup** after a pre-artifact failure | Existing Plan shows **Test Failover** and verifies real VM creation before success |

## 16. SharedMountPoint test failover disk isolation

The target qcow2 can remain open by the paused replication writer. A test
artifact must therefore not use that mutable file as a backing image. For a
`FILE` artifact, FTCTL produces and validates an independent `qcow2-copy` from
the latest durable checkpoint using force-share read access. Cloud materializes
the test volume from the published copy path and preserves the existing RBD
snapshot/clone contract for RBD plans.

UI success remains terminal: request acceptance is not success. The test
failover is complete only after the independent disk is prepared, the Cloud
test VM exists, and the configured boot validation succeeds. Test Cleanup
removes the test VM and the independent copy but never the durable replica.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL FILE artifact | Mutable durable target is used as a backing file | A validated independent sparse qcow2 copy is published |
| Replication safety | Later target writes can affect a running test | Scheduler pause plus independent copy freezes the tested checkpoint |
| Cloud materialization | No VM is created when backing-file locking fails | Test volume and VM are created from the independent copy |
| Cleanup | Overlay cleanup is assumed | Only the generated copy and test VM are removed |
| Existing providers | Shared implementation can alter RBD behavior | RBD snapshot/clone and VMware paths remain unchanged |

## 17. Test Failover guest preparation preflight projection

The existing SharedMountPoint Plan failed before independent qcow2 copy
creation with the generic `DR_GUEST_PREP_RUNTIME_UNAVAILABLE` code. Cloud and
the UI must preserve the exact FTCTL prerequisite failure instead of replacing
it with a combined runtime/ISO message.

FTCTL publishes `guest_preflight_state`, `guest_preflight_error_code`, and
`guest_preflight_error_message`. Cloud promotes the specific code and message
to the owning Run, Run step, and test session. The UI translates known codes
and falls back to the safe backend message for future codes.

Linux plans do not require the Windows WinPE or virtio ISO. Target-host
preflight therefore passes for Rocky Linux when the test session, manifest
tool, and v2k runtime are present. A failed prerequisite remains terminal and
offers Test Cleanup only when the session proves cleanup is required.

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| FTCTL | exit 47 with one generic code | exact session/tool/v2k/profile/ISO code and message |
| Cloud | generic runtime/ISO projection | preserves specific FTCTL evidence |
| UI | opaque failed execution | localized actionable cause on Run and step |
| Validation | target package drift is discovered after acceptance | host prerequisites and hashes are checked before UI retest |
