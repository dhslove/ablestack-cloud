# FTCTL DR Remote Mold Standby Read-Only Projection Design

## Background

DR cloud-managed provisioning now creates the standby VM and volumes through the target Mold service when the replica site uses a remote Mold. The source Mold correctly owns the FTCTL protection row, qemu command path, runtime events, and lifecycle state. The remote Mold only owns the Cloud resources that were created in that remote site.

The current remote standby VM detail page still falls back to "not protected" because `getFtctlProtection` only looks for a local active `ftctl_protection` row by primary or secondary VM id. In remote Mold DR, the remote standby VM has no local protection row by design. It only has VM detail markers such as `ftctl.remote.replica.vm=true`, `ftctl.standby.vm=true`, and `ftctl.remote.source.vm.*`.

## Design Principles

1. Preserve the HA ownership model.
   - Cloud creates and manages VMs and volumes through Cloud APIs.
   - Mold Agent relays FTCTL commands and returns qemu-side status or events.
   - qemu FTCTL performs DR replication and failure actions.
   - Cloud reads qemu events/status asynchronously and projects them to the UI.

2. Do not duplicate protection ownership across Mold services.
   - The source Mold keeps the authoritative `ftctl_protection` row.
   - The remote Mold must not create a synthetic local `ftctl_protection` row for the standby VM.
   - The remote Mold must not issue qemu FTCTL actions from the standby VM detail page.

3. Apply the model to both current-Mold and remote-Mold DR.
   - Current-Mold DR continues to use the local protection row because both source and standby VM ids are in one Cloud database.
   - Remote-Mold DR uses source-side detail snapshots for source UI and target-side VM detail markers for target UI.
   - Any new DR display logic must explicitly account for both paths.

4. Keep the remote standby page read-only.
   - The target Mold page should show that the VM is an FTCTL standby/replica resource.
   - It should display local Cloud state for the standby VM and attached volumes.
   - It should not expose protection registration or lifecycle action buttons for the standby VM.
   - Runtime qemu check/event data is not authoritative on the target Mold unless a source-owned runtime profile is available, so target-side runtime sections should return `not_available` instead of triggering misleading qemu reads.

## Current Failure

Observed after DR-WIN-03 retest:

- Source Mold VM page:
  - Primary VM state is shown.
  - Remote standby VM state, host, and remote volumes are shown.
  - Protection state is shown as protected/mirroring/primary/clear.

- Remote Mold standby VM page:
  - The standby VM is stopped, as expected for cloud-managed standby.
  - The FTCTL tab displays "protection is not configured".
  - The configure button is disabled because the VM is stopped.

The second screen is wrong. A stopped cloud-managed standby VM is expected, and the screen should be a read-only FTCTL standby view, not a registration candidate.

## Backend Changes

`FtctlServiceImpl.buildProtectionResponse()` should add a fallback path:

1. Look up active local protection as today.
2. If no local protection exists, detect a remote Mold standby resource from VM details:
   - `ftctl.remote.replica.vm=true`
   - `ftctl.standby.vm=true`
   - source metadata under `ftctl.remote.source.vm.*`
3. Return a read-only `FtctlProtectionResponse`:
   - `protectionrole=standby`
   - `enabled=true`
   - `mode=dr`
   - `backendmode=remote-nbd`
   - `provisioningbackend=cloud-managed`
   - `provisioningstate=Ready`
   - `protectionstate=protected`
   - `transportstate=not_available`
   - `activeside=primary`
   - `adminstate=read-only`
   - primary VM name/UUID from `ftctl.remote.source.vm.*`
   - secondary VM id/UUID/name/state/host from the requested local standby VM
   - secondary volume list from `volumeDao.findByInstance(standbyVmId)`

`getFtctlCheck`, `getFtctlHealth`, and `getFtctlEvents` should also detect the same remote standby marker and return read-only `not_available` responses without invoking the agent. This prevents a remote Mold standby page from querying qemu runtime data that belongs to the source Mold protection profile.

## UI Changes

The FTCTL tab already hides action buttons when `protectionrole=standby`. It also suppresses protection registration in standby view. One adjustment is required:

- If `primaryvirtualmachineid` is absent but `primaryvirtualmachinename` or `primaryvirtualmachineuuid` is present, display it as plain text instead of `-`.

This is needed because a remote Mold cannot route to the source Mold VM detail page using a local numeric VM id.

## Expected Result

After the change:

- Source Mold page keeps showing the source-owned protection state and remote replica snapshot.
- Remote Mold standby page shows a read-only FTCTL standby view.
- The remote standby VM being `Stopped` no longer causes the FTCTL tab to show the "not protected" empty state.
- No HA behavior changes.
- No current-Mold DR behavior changes.
- No qemu FTCTL action is issued from the remote standby page.
