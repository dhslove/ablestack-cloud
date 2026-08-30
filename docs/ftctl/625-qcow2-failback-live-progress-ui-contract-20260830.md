# qcow2 failback live progress UI contract

## Problem

The failback action can finish its synchronous Cloud dispatch steps before the
FTCTL data worker finishes copying. The operation row then exposes a backend
workflow value of 100 percent even though the authoritative transfer journal is
still active. A version 1 qcow2 journal is also rejected by the UI version 2
guard, so users see neither bytes nor ETA.

## TO-BE

- FTCTL SharedMountPoint qcow2 workers publish transfer schema version 2 with
  plan, run, and cycle ownership.
- Cloud continues to expose the operation-owned live sample on the Run.
- For non-terminal `SYNC`, `RECOVER_SYNC`, and `FAILBACK` operations, a valid
  live transfer sample prevents a stale 100-percent workflow value from being
  displayed as completion.
- The UI maps the transfer into the 70-95 percent workflow range. Only a
  terminal Run may display completed 100 percent.
- The transfer panel shows bytes, throughput, ETA, disk position, and transfer
  mode using the same sample.

## Regression gate

- Unit test an active failback with backend workflow 100 and transfer 11.
- Retain the existing monotonic SYNC progress tests.
- Run FTCTL qcow2 SharedMountPoint smoke tests and Cloud UI unit tests.
- Validate the deployed UI from a real failback before marking the menu PASS.
