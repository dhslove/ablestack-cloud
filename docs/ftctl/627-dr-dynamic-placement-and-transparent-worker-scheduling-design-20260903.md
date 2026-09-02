# DR Dynamic Placement and Transparent Worker Scheduling Design

## 1. Decision

DR plans describe workload identity, sites, storage and network policy, and
RPO/RTO. They must not freeze transient infrastructure placement. VM host,
coordinator host, and transfer worker are runtime observations or leases, not
Plan authority.

The following are prohibited design patterns:

- persisting a VM's observed `hostid` as an immutable execution route;
- using `mapping_json.source.hardware.sourceHostUuid` or
  `sourceWorkerHostUuid` as a prerequisite for every action;
- treating a stopped VM's missing host as an unavailable replication engine;
- asking a normal UI user to select coordinator/source/target workers;
- reusing inventory snapshots as routing tables;
- rejecting disaster Failover because the source VM, source Agent, or source
  Mold cannot be reached.

These rules apply to VMware-to-RBD, RBD-to-RBD, and SharedMountPoint
qcow2-to-qcow2. Provider-specific data transfer remains isolated, but dynamic
placement is a common orchestration contract.

### 1.1 Scope guard

This design does not declare the existing DR lifecycle or data plane defective
and does not replace it. The already successful Plan, synchronization,
checkpoint, Test Failover, cleanup, Failover, Failback, Reprotect, release,
terminal projection, and provider implementations remain the baseline.

Only these three defects are in scope:

1. a transient VM/worker/coordinator host is persisted or reused as fixed
   execution authority;
2. a normal user is required to choose internal execution hosts;
3. VM power state or current host presence is used as a general condition for
   DR synchronization or recovery availability.

Implementation must remove those conditions without changing disk contents,
VM Details, checkpoint ordering, provider selection, authority transitions, or
the successful action state machine. A change outside this boundary requires a
separate design and regression justification.

## 2. AS-IS Failure

`DrFtctlActionCapabilityServiceImpl` selects the remote source Agent before it
knows which action is being evaluated. `DrRemoteAgentClient.sourceWorkerUuid()`
then resolves the VM's current `hostid` and persists it into `mapping_json`.
When a VM is stopped, migrated, or its source site is unavailable, the lookup
returns no host. One lookup exception is expanded to
`DR_ACTION_CAPABILITY_UNAVAILABLE` for every action.

This couples four independent concepts:

1. the VM's current compute placement;
2. an Agent capable of VM-local QMP control;
3. a worker capable of accessing shared storage and transferring data;
4. a coordinator that owns an operation lease.

The coupling violates virtualization semantics and makes recovery depend on
the infrastructure that DR is expected to survive.

## 3. Authority Boundaries

| Information | Authority | Persistence rule |
| --- | --- | --- |
| VM identity and replicable VM Details | Mold/vCenter inventory | Plan contract |
| Current VM host | Live Mold/vCenter query | Observation only, never execution authority |
| Eligible worker pool | Site inventory, Agent health, storage capability | Refreshable projection |
| Coordinator | Plan-scoped lease | Runtime only |
| Transfer worker | Cycle/disk-scoped lease | Runtime and audit evidence only |
| QMP control Agent | Current VM placement at command time | Command-local only |
| Durable recovery point | FTCTL checkpoint/cycle journal | Durable recovery authority |

`mapping_json` may carry `lastObservedHostUuid` for diagnostics. It must be
timestamped, must never be named `sourceWorkerHostUuid`, and must never enable,
disable, or route an action.

## 4. Runtime Resolvers

Cloud introduces explicit, action-aware services instead of a shared host-ID
shortcut:

```java
interface DrPlacementResolver {
    DrVmPlacement observeVmPlacement(DrPlanVO plan, DrSiteRole role);
    List<DrWorkerCandidate> listEligibleWorkers(DrPlanVO plan,
            DrWorkerRole role, DrAction action);
}

interface DrWorkerScheduler {
    DrWorkerLease acquire(DrPlanVO plan, DrRunVO run,
            DrWorkerRole role, DrWorkerRequirements requirements);
    DrWorkerLease reassign(DrWorkerLease failedLease, String reasonCode);
}
```

`observeVmPlacement()` always performs a current Mold/vCenter lookup. Its
result includes an observation generation or timestamp and is invalidated by
host migration. It is called only for an operation that actually needs
VM-local control.

`listEligibleWorkers()` selects from connected Agents in the relevant site and
checks FTCTL capability, maintenance state, storage reachability, transport
network reachability, resource slots, and recent failures. SharedMountPoint
file operations are not restricted to the VM's compute host.

This is a routing correction around the existing action implementation. The
resolver supplies the host that receives the same validated command; it does
not rebuild profiles, change provider logic, or reinterpret VM/disk metadata.

## 5. Scheduling Rules

### 5.1 Coordinator

The controller acquires a Plan/Run coordinator lease from the eligible pool.
The lease has an epoch, heartbeat, and expiry. A failed coordinator is replaced
without changing the Plan or checkpoint identity.

### 5.2 Transfer worker

A transfer worker is selected per Cycle and, when useful, per disk. Selection
uses storage access, provider capability, load, bandwidth and slot admission,
failure-domain spread, and backoff. The selected host is recorded as evidence,
not reused as future authority.

### 5.3 VM-local control

QMP, live snapshot, or guest-quiesce commands resolve the current VM host
immediately before dispatch. The command includes the observed VM placement
generation. `HOST_CHANGED` or Agent disconnect triggers a fresh lookup and a
bounded idempotent retry. A migration must not invalidate the transfer worker
lease after a durable checkpoint has been produced.

### 5.4 Stopped VM

A stopped VM is a valid source for file and block replication. For
SharedMountPoint qcow2, readiness is based on file accessibility, disk-set
mapping, checkpoint integrity, and writer/lease state. Missing `hostid` is not
an error. The stopped state selects the offline checkpoint path and does not
disable synchronization.

### 5.5 VMware source power and placement

A VMware source VM may be `POWERED_ON` or `POWERED_OFF` when protection is
created, Full Sync is requested, or periodic synchronization is evaluated.
Power state selects a consistency path; it is not global action eligibility:

- `POWERED_ON`: use the existing snapshot/CBT path and the configured
  crash-consistent or quiesced policy;
- `POWERED_OFF`: read the stable virtual-disk chain through VDDK and permit
  Full Seed; when a valid CBT baseline/change ID exists, permit incremental or
  `NO_CHANGE` completion without starting the VM;
- CBT configured but not yet activated: publish typed
  `CBT_PENDING_ACTIVATION` for incremental readiness. Do not start or power
  cycle the source automatically, do not invalidate an existing durable
  checkpoint, and do not block checkpoint-based Test Failover or disaster
  Failover;
- vCenter/source unavailable: periodic source replication may wait or degrade,
  while disaster Failover remains governed by target capability, fencing
  acknowledgement, and the last durable recovery point.

The ESXi host reported by vCenter is an observation only. vMotion/DRS may move
the VM at any time, so vCenter inventory and disk locators are refreshed for
each snapshot/CBT operation. No ESXi identity is persisted as Plan routing
authority.

The VDDK data-plane worker is a KVM-side worker, not the VMware VM's host. It
is automatically selected per Run/Cycle from connected workers that can load
VDDK and reach the required transport and target storage. A normal user never
selects it, and `targetWorkerHostId` is not a durable prerequisite.

## 6. Action-Aware Capability Matrix

| Action | Source Agent required | Target Agent required | Current VM host required |
| --- | --- | --- | --- |
| VMware Full Sync, source on or off | vCenter/VDDK source access | yes | no pinned ESXi host |
| VMware incremental, source off with valid CBT baseline | vCenter/VDDK source access | yes | no pinned ESXi host |
| Sync, running VM | only for checkpoint/QMP producer | yes | command-time only |
| Sync, stopped shared-file VM | no | yes | no |
| Test Failover | no after durable checkpoint | yes | no |
| Disaster Failover | no | yes | no |
| Planned Failover | yes for final checkpoint and source stop | yes | command-time only |
| Test Cleanup | no | yes | no |
| Release, retain target | no | target/controller only | no |
| Release, delete target | no | target/controller only | no |
| Failback/Reprotect | selected from current authority and destination roles | selected per direction | only for an actual VM lifecycle/QMP command |

Capability evaluation returns per-site and per-action results. Failure of one
site probe must not mark unrelated actions unavailable. Disaster recovery
actions use target capability and durable checkpoint evidence even when the
source site is absent.

## 7. UI Contract

The normal Plan wizard removes coordinator, source-worker, and target-worker
selectors. The user selects business policy and durable resources only:

- source and target sites;
- workload;
- target storage, network, and compute policy;
- RPO/RTO and consistency policy.

Preview reports `Automatic placement: READY`, eligible worker counts, storage
access, and blocking policy reasons. Detail and Run views show the actual
coordinator/worker as read-only runtime evidence, including lease epoch and
reassignment history.

An administrator-only advanced policy may exclude hosts, express preference,
or cap parallelism. It must not pin an execution host and must not be required
for Plan creation.

The UI consumes backend `actionavailability`. It must not allow submission when
the same snapshot reports a blocking capability reason, and it must never show
the Plan as generally `READY` while all actions are globally unavailable.

VM power state is not a protection, synchronization, reprotection, or
failback-preflight prerequisite. Those paths are authorized by the committed
authority generation and a durable checkpoint set. Power-off and power-on
checks remain only inside an explicit cutover transaction when they are the
requested transition outcome, and destination boot validation remains a
post-materialization success criterion.

## 8. Existing Plan Migration

No DB repair or Plan recreation is required.

1. Ignore `source_worker_host_id`, `target_worker_host_id`,
   `coordinator_worker_host_id`, `sourceWorkerHostUuid`, and
   `source.hardware.sourceHostUuid` for routing.
2. Preserve old values only as legacy audit data until a schema migration
   retires them.
3. Build the eligible pool from current site inventory on the next readiness
   refresh.
4. Reproject action availability per action and site.
5. Do not mutate VM Details, disk mappings, checkpoint identity, or validated
   provider behavior during migration.

Existing plans retain their successful runtime state and recovery points. The
next availability refresh changes only host selection and capability
projection; it must not force reseed, recreate a replica, or rewrite a target
VM.

## 9. Regression Gates

The following tests are mandatory before deployment:

1. running VM migrates between hosts before and during a Cycle;
2. stopped SharedMountPoint VM synchronizes with `host_id=NULL`;
3. source site unreachable while disaster Failover remains available;
4. coordinator failure causes lease-based reassignment;
5. transfer worker failure retries on another storage-capable worker;
6. one capability endpoint failure blocks only actions that need that site;
7. UI wizard contains no required worker selector;
8. UI action menu and backend submission use the same availability snapshot;
9. VMware-to-RBD, RBD-to-RBD, and qcow2-to-qcow2 baseline action suites pass;
10. VMware source `POWERED_OFF` permits Full Seed without an automatic power
    operation;
11. VMware source `POWERED_OFF` with a valid CBT baseline completes incremental
    transfer or `NO_CHANGE` without an automatic power operation;
12. VMware source migration between inventory read and VDDK open causes a
    bounded locator refresh, not Plan mutation or global capability failure;
13. no test persists observed placement as Plan routing authority.

Static regression checks must reject new code that reads a persisted host UUID
to decide action capability or dispatch without an explicit live resolver or
runtime lease.

## 10. AS-IS / TO-BE

| Layer | AS-IS | TO-BE |
| --- | --- | --- |
| UI | User selects internal workers | Platform schedules automatically; runtime placement is read-only |
| API | Plan accepts required worker IDs | Worker constraints optional and administrative only |
| Backend | One remote source lookup gates every action | Action-aware site capability and dynamic placement resolvers |
| Agent | Command route assumes a persisted worker UUID | Receives lease-bound commands for the selected runtime role |
| FTCTL | Worker identity can become Plan authority | Checkpoint identity survives worker replacement |
| DB | Transient host IDs stored with Plan configuration | Durable policy in Plan; placement and leases in runtime evidence |
| VMware source power | Incident observations can be mistaken for a `POWERED_ON` prerequisite | `POWERED_ON` and `POWERED_OFF` are valid capture states; no automatic power-on for readiness |
| VMware placement | Selected target worker and observed placement can become a fixed VDDK route | Refresh vCenter locator per attempt and lease an eligible VDDK worker automatically |

## 11. Implementation Map

The implementation uses one placement authority, `DrWorkerPlacementService`.
Callers must not implement their own `firstNonNull(plan.*WorkerHostId)` routing.

```java
Long resolveWorkerHostId(DrPlanVO plan, DrWorkerRole role);
Long resolveWorkerHostId(DrPlanVO plan, DrRunVO run, DrWorkerRole role);
```

The resolver applies these rules in order:

1. reuse the active Run lease when it names an eligible host;
2. determine the role's current site and zone;
3. enumerate `Up` KVM Agents in that zone;
4. filter VDDK workers by detected VDDK support when the provider requires it;
5. select deterministically from the currently eligible set so repeated phases
   of one Run remain stable without writing the host into the Plan;
6. on Agent loss, expire the lease and resolve again from refreshed inventory.

Admission stores `HOST:<id>:<operation-class>` only in `dr_resource_lease`.
The Plan's legacy worker columns and mapping JSON host keys are never consulted
for new routing and are never refreshed from VM placement. Existing values are
retained as historical data only.

The site-local `executeDrSiteAgentCommand` endpoint accepts no required worker
parameter. Its broker deserializes the allow-listed command first and chooses
an `Up` KVM Agent. For VM-local control it resolves the VM's current host at
dispatch time; for status/cancel it probes eligible hosts until the matching
Plan/Run evidence is found; for shared-storage and capability work it chooses
from all eligible workers. The response records the host actually used.

## 12. UI Implementation

The Plan create/edit dialog removes all three worker selectors and never sends
`sourceworkerhostid`, `targetworkerhostid`, or `coordinatorworkerhostid`.
Target compute sizing may use offering defaults and source VM properties but
must not depend on a selected host CPU speed. Preview and detail views show
`Automatic placement` and eligible/unavailable state. Actual execution hosts
belong only in read-only Run evidence.

No fixed-host validation message may block submit. Dark-mode colors use the
existing semantic text, border, success, warning, and error tokens; this change
does not introduce literal light backgrounds.

## 13. Mandatory Full Lifecycle Smoke Gate

Deployment is prohibited until all provider routes pass the same action
contract suite. A test may use mocks for provider I/O but must execute the real
Cloud action availability, command construction, FTCTL contract parsing, and
UI state helpers.

| Phase | Required assertions |
| --- | --- |
| Plan/readiness | stopped source accepted; no worker field required; automatic candidate present |
| Reprotect/failback preflight | stopped or unassigned serving VM accepted when target authority and durable checkpoint are valid |
| Full sync | accepted, durable terminal, Plan READY |
| Incremental sync | accepted or NO_CHANGE, durable terminal, RPO projection consistent |
| Pause/resume | terminal state and scheduler intent agree |
| Test Failover | durable checkpoint only; test artifact and boot gate reflected |
| Test Cleanup | source not required; test VM/artifacts and session terminal agree |
| Disaster Failover | source/site/VM power unavailable does not block target recovery |
| Planned Failover | live source host is observed only for the final local control step |
| Failback | committed authority, reverse baseline and terminal session agree |
| Reprotect | current authority becomes the new source without Plan host pinning |
| Release/delete | retain/delete disposition and resource cleanup remain distinct |

The matrix runs for VMware-to-RBD, RBD-to-RBD, and SharedMountPoint
qcow2-to-qcow2. Release tombstone, multi-disk checkpoint, terminal/Cycle
projection, group run, and action-capability regression suites are included.
Only after this gate, changed-module build, UI production build, and package
artifact checks pass may the same artifact set be deployed to the test sites.
