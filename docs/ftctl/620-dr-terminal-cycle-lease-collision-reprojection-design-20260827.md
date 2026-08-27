# DR Terminal Cycle Lease Collision Reprojection Design

## 1. Purpose

This design closes a Cloud projection gap observed after an FTCTL scheduler
restart or package replacement. The data plane can complete a durable cycle
while Cloud keeps the accepted `SYNC` run in `RUNNING` because the engine
sequence was previously used by an older scheduler lease.

The change must preserve all validated VMware to ABLESTACK and ABLESTACK RBD
paths. It changes only Cloud cycle identity and strict terminal recovery. It
does not change copy, CBT, RBD, target materialization, failover, or failback
behavior.

## 2. Incident Contract

The affected state has all of the following evidence:

- FTCTL worker state is `TERMINAL_PUBLISHED` with exit code `0`.
- `control_request_run_uuid` equals the Cloud run UUID.
- Engine state is `READY` and step is `full-resync-completed`.
- The manifest and checkpoint paths contain the same run UUID.
- The target durable timestamp is not older than the Cloud run start.
- A historical `dr_sync_cycle` already owns the engine sequence under a
  different scheduler lease.

Cloud must not require a manual DB update in this state. It must create or
resolve the current lease's canonical cycle, bind it to the accepted run, and
allow the normal terminal projection path to converge the run and plan.

## 3. Identity Model

Engine sequence alone is not a durable Cloud identity across scheduler lease
changes. Cloud resolves a cycle in this order:

1. `(plan_id, scheduler_session_uuid, scheduler_lease_epoch, cycle_token)`
2. Legacy `(plan_id, sequence)` only when the stored row has no lease identity
3. A new canonical Cloud sequence when the engine sequence collides with a row
   from another lease

The canonical sequence is at least the engine sequence, greater than the
latest Cloud cycle, and not lower than FTCTL `authority_sequence`. The FTCTL
cycle token remains attached so accepted-run ownership is not inferred from a
newer scheduler producer UUID.

## 4. Strict Late Terminal Recovery

`FtctlDrRuntimeProjectionAdapter` may reconstruct a missing completed Full Seed
cycle only when every incident-contract predicate in section 2 is true. The
reconstructed row is persisted as `FULL_SEED / READY / LOCAL_DURABLE`, linked
to the Cloud run, and then used by the existing accepted-cycle terminal gate.

Missing, stale, mismatched, failed, or non-owned evidence remains non-terminal.
The recovery path never marks a copy successful from progress percentage alone.

## 5. Transaction And Retry Behavior

- Cycle resolution and terminal binding run under the existing plan lock and
  projection transaction.
- Repeated polling resolves the same scheduler-cycle identity and is
  idempotent.
- A later FTCTL cycle with the global sequence floor naturally supersedes the
  one-time compatibility cycle.
- Cloud background projection or the UI Update action is sufficient to recover
  an accepted run. Direct SQL repair is prohibited.

## 6. AS-IS / TO-BE

| Area | AS-IS | TO-BE |
|---|---|---|
| Cycle identity | `(plan_id, sequence)` | Lease-aware scheduler cycle identity |
| Sequence reuse | Historical row is reused or current row is skipped | Allocate a canonical Cloud sequence |
| Full Seed terminal | Requires a pre-existing non-colliding Cycle | Strict terminal evidence can reconstruct the missing durable Cycle |
| Run result | `RUNNING` can remain after FTCTL terminal | Accepted Run converges to `SUCCEEDED` |
| Plan/UI | `SYNCING` and action gating remain stale | Plan converges to `READY`; actions become usable |
| Recovery | Manual DB repair | Automatic idempotent reprojection |

## 7. Verification Gate

1. Unit test a reused engine sequence under a new scheduler lease.
2. Run the disaster-recovery plugin test suite and changed-module Maven build.
3. Deploy the changed Cloud classes and the FTCTL package containing the global
   sequence floor to the same test cluster.
4. Verify package versions and installed script hashes on every compute host.
5. Without modifying DB rows, refresh projection and verify:
   - accepted `SYNC` run is `SUCCEEDED`;
   - current plan is `READY / ENABLED`;
   - latest durable Cycle belongs to the current scheduler lease;
   - UI actions are available;
   - no active worker, lock, or NBD endpoint remains.

