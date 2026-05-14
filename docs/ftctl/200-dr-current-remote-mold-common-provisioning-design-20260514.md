# DR Current And Remote Mold Common Provisioning Design

Date: 2026-05-14

## 1. Purpose

DR protection must be designed for both current-Mold and remote-Mold execution paths from the beginning.

The remote Mold option is not an exception path. It is one of the two supported DR Cloud-managed provisioning paths:

- current Mold: the source Mold also owns the replica VM and volumes.
- remote Mold: the source Mold asks the remote Mold to own the replica VM and volumes.

Both paths must converge into the same canonical replica resource model before Cloud persists FTCTL state or asks qemu FTCTL to start replication.

## 2. Incident Summary

During DR-WIN registration for `dr-w22-01`, the remote Mold created `dr-w22-01-standby` and its root/data volumes on the 10.10.32 cluster, but the source VM FTCTL tab still showed "not protected".

Observed source-side state:

- the source `RegisterFtctlProtectionCmd` async job failed.
- `ftctl_protection` had no active row for source VM `381`.
- source VM details had only failure state:
  - `ftctl.provisioning.backend=cloud-managed`
  - `ftctl.provisioning.state=ProvisioningFailed`
  - `ftctl.last.error=Remote Mold returned incomplete FTCTL DR replica resources`

Observed remote-side state:

- remote replica VM existed.
- remote root/data volumes existed and were Cloud-managed.

The failure happened after remote resource creation and before source-side protection persistence. The source Mold parsed the remote API response too narrowly and treated a valid CloudStack wrapped response as incomplete.

## 3. Root Cause

The source Mold parser expected this flat response shape:

```json
{
  "prepareftctldrreplicaresourcesresponse": {
    "remotevirtualmachineid": "...",
    "remotevirtualmachineinstancename": "...",
    "diskmap": "sda=rbd/replica-root;sdb=rbd/replica-data",
    "volume": []
  }
}
```

CloudStack response serialization can also return the response object under its object name:

```json
{
  "prepareftctldrreplicaresourcesresponse": {
    "ftctldrreplicaresources": {
      "remotevirtualmachineid": "...",
      "remotevirtualmachineinstancename": "...",
      "diskmap": "sda=rbd/replica-root;sdb=rbd/replica-data",
      "volume": []
    }
  }
}
```

When the parser reads only the flat shape, `remotevirtualmachineid` and `diskmap` appear blank even though the remote Mold successfully created the resources.

## 4. Common DR Provisioning Contract

Current-Mold and remote-Mold DR provisioning must both return a canonical `DrReplicaResources` model:

- replica VM UUID
- replica VM display name
- replica VM hypervisor instance name
- replica VM Cloud state snapshot
- replica VM target host ID/name snapshot when available in that Mold context
- target host UUID/address
- target storage pool UUID/name/type/path
- source volume ID/UUID
- source disk target, such as `sda` or `sdb`
- replica volume UUID/name/path
- replica volume Cloud state snapshot
- disk label
- canonical disk map, such as `sda=<replica-path>;sdb=<replica-path>`

The rest of registration must consume only this canonical model.

## 5. Execution Paths

### Current Mold

The current Mold path creates or finds the replica VM and volumes through local Cloud DAO/API services, then returns the canonical model directly.

It must:

- create or reuse a stopped replica VM.
- create or reuse matching root/data volumes.
- attach volumes with matching device IDs.
- return Cloud-created disk paths only.
- persist local Cloud numeric IDs where available.

### Remote Mold

The remote Mold path calls the remote API command `prepareFtctlDrReplicaResources`.

The remote Mold command must:

- create or reuse a stopped replica VM.
- create or reuse matching root/data volumes.
- attach volumes with matching device IDs.
- return remote external UUIDs and disk paths.
- never return transient credentials.

The source Mold must normalize the remote response into the same canonical model used by the current-Mold path.

## 6. Parser Requirements

The remote response parser must accept both response shapes:

- flat payload under `prepareftctldrreplicaresourcesresponse`
- nested payload under `prepareftctldrreplicaresourcesresponse.ftctldrreplicaresources`

The parser must also be defensive about volume entries:

- accept direct volume objects.
- accept volume entries wrapped as `ftctldrreplicavolume`.
- require `sourcevolumeid` and `path` for each volume.
- require `sourcedisktarget` when `diskmap` is absent.
- rebuild `diskmap` from `sourcedisktarget=path` pairs when the remote response omits `diskmap`.

## 7. Idempotency Requirements

Both current-Mold and remote-Mold resource preparation must be idempotent:

- same source VM UUID and standby VM name reuse the same replica VM.
- existing matching volumes are reused.
- only missing volumes are created.
- duplicate replica VMs or duplicate volumes are not created.
- if remote resource creation succeeded but source registration failed, the next registration reuses remote resources and completes source protection state.

This incident is exactly that partial-success case.

## 8. Persistence Requirements

After canonical resources are available, source Mold persists:

- active `ftctl_protection`
- active `ftctl_protection_volume` rows
- source VM `ftctl.*` details for remote/current replica metadata
- complete disk map for qemu profile sync

If canonical resource normalization fails, Cloud records `ProvisioningFailed` and `ftctl.last.error`, but it must not silently mark protection active.

## 9. qemu FTCTL Boundary

qemu FTCTL receives the explicit disk map and performs replication/data-plane actions only.

It must not:

- create Cloud-managed VMs.
- create Cloud-managed volumes.
- infer missing Cloud-managed target paths.
- format or re-create Cloud-managed targets.

This preserves the HA principle: Cloud owns VM/volume lifecycle and asynchronous state lookup; Mold Agent forwards FTCTL commands and status; qemu FTCTL performs the DR action.

## 10. Verification

Code verification:

- flat remote response parses successfully.
- nested remote response parses successfully.
- missing `diskmap` is reconstructed from volume entries.
- missing volume path or source volume ID fails clearly.

Runtime verification:

- retry after this incident reuses the existing remote `dr-w22-01-standby`.
- source Mold creates active protection state.
- source FTCTL tab no longer shows "not protected".
- source FTCTL tab projects the remote replica VM and volume state through the same fields used by the current-Mold path.
- qemu profile contains Cloud-created target paths.
- qemu events show replication action, not VM/volume creation.
