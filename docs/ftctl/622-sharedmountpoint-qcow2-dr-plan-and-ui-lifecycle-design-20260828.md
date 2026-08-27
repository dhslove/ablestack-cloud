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
