# Remote KVM Source Worker Identity Resolution

## Problem

An ABLESTACK-to-ABLESTACK Plan may keep the source VM as an external
reference. In that case `source_worker_host_id` is intentionally local-site
only and can be null. A Plan created or refreshed while the source VM is
stopped can also omit `sourceHostUuid` from `mapping_json`.

The runtime status, capability, and action paths previously parsed that static
mapping independently. A missing value therefore changed an otherwise durable
Plan into `DR_ENGINE_UNAVAILABLE`, disabled every UI recovery action, and hid
the fact that the source VM could be started on a valid remote worker.

## Contract

1. Remote KVM worker identity has one resolver owned by
   `DrRemoteAgentClient`.
2. The resolver checks current Mold VM inventory first. A running VM's current
   host is authoritative and permits safe host migration.
3. A resolved UUID is persisted into the Plan root, source, and source hardware
   mapping so a later source shutdown or management restart retains the last
   valid worker identity.
4. If current inventory is unavailable or the VM is stopped, the durable
   mapping is used. Failure is reported only when neither source exists.
5. Runtime projection, action dispatch, and capability calculation use this
   same resolver. They must not implement independent JSON parsing.
6. The resolver is active only for remote `KVM_TO_KVM` Plans. VMware-to-RBD and
   local RBD-to-RBD behavior remains unchanged.

## UI Acceptance

After the source VM is started through its source Mold UI, updating the Plan
must resolve and persist the worker, remove `DR_ENGINE_UNAVAILABLE`, and enable
the state-valid recovery action. Recovery must converge to `READY` without a
manual DB update. Subsequent failover and failback must continue to resolve the
same worker after management restart and source power transitions.
