# ABLESTACK RBD Cross-Site DR Plan Authority And UI Lifecycle Design

Date: 2026-08-24

## 1. Scope

This design completes the single-VM `KVM_TO_KVM` path between the 22 and 32
ABLESTACK clusters. Both source and target storage are RBD. Ceph `rbd-mirror`
is explicitly out of scope.

The already validated `VMWARE_TO_KVM` path is a regression-protected contract.
Provider-specific changes in this design must not alter VMware inventory, VDDK,
CBT, target materialization, failover, or failback behavior.

### 1.1 Plan-owner data-plane correction

The Cloud instance that stores the DR Site and DR Plan is the sole control
authority. For the 22-to-32 validation, the 32 management server owns the Plan
even though the protected VM runs on the 22 site. The source Agent and FTCTL
are data-plane workers; they do not SSH to, provision, or administrate the
target site.

The first remote KVM implementation violated this boundary by placing a
`qemu+ssh` URI and a target-host SSH key in the source FTCTL profile. It also
allowed the source worker to synthesize `/dev/rbd/<pool>/<image>` before the
target Cloud had created its VM and volume records. That path is removed for
remote `KVM_TO_KVM` plans.

Corrected ordering:

1. The Plan Owner resolves target placement and creates the stopped target VM
   and Cloud-owned RBD volume records before dispatching the first Sync.
2. The Plan Owner sends `TARGET_EXPORT_START` to the selected target Agent.
   Target FTCTL validates or creates the Cloud-selected RBD image and exposes
   it with librbd-backed `qemu-nbd` on the reserved DR port range.
3. The Plan Owner receives the typed export manifest and adds only NBD endpoint
   metadata to the source command profile.
4. The Plan Owner sends `SYNC` or `RECOVER_SYNC` to the source Site through the
   registered Mold API and Agent broker.
5. Source FTCTL reads source RBD through librbd. Full Seed writes to the target
   NBD export. Incremental cycles retain only source RBD snapshot baselines,
   calculate changed extents with `rbd diff`, and apply those extents to the
   target NBD export. No target SSH command or target RBD snapshot is used.
6. After a durable checkpoint, the existing materialization completion and
   `TARGET_MATERIALIZED` notification contract marks the replica Ready.
7. Before production target VM power-on, and on Release or Delete, the export
   is stopped and checked before Cloud starts the VM through its normal krbd
   runtime path.

`profile.transport.mode` is `site-agent-nbd`. It contains no SSH user, key, or
libvirt URI. `profile.transport.exports[]` contains `device`, `host`, `port`,
`name`, `uri`, and the canonical target locator. The required FTCTL capability
is `dr-site-agent-rbd-transport-v1`.

This branch is selected only for remote-source `KVM_TO_KVM`. VMware-to-KVM
continues to use the validated VDDK/librbd mover, while local KVM-to-KVM and
existing HA/FT `remote-nbd` profiles retain their contracts.

| Area | AS-IS | TO-BE |
|---|---|---|
| Control authority | Source FTCTL administers target by SSH | Plan Owner controls both Sites through API and Agent |
| Target resources | Synthetic path before Cloud ownership | Stopped target VM and Cloud volume created first |
| Full Seed | Source starts target qemu-nbd through SSH | Target Agent starts export; source connects to NBD |
| Incremental | `rbd export-diff` piped to target SSH | source changed extents applied over target NBD |
| Credentials | target root SSH material reaches source | no target SSH credential in source runtime |
| VM runtime | export can overlap target VM power-on | export stopped before VM starts with krbd |

## 2. Authority Model

The Cloud that owns `dr_site`, `dr_plan`, `dr_run`, and the operator UI is the
**Plan Owner Cloud**. It remains authoritative whether it is located at the
source site, target site, or a third management site.

The Plan Owner Cloud:

- validates the complete plan and records every asynchronous Run;
- owns lifecycle decisions, action eligibility, status projection, and UI;
- asks the Cloud that owns a VM or volume to perform its local lifecycle action;
- never addresses a remote Agent or libvirt daemon directly.

Plan ownership is independent from replication direction. In the concrete
`22 -> 32` qualification, cluster 32 owns the sites, plan, Runs, and operator
UI, so cluster 32 is the Plan Owner while cluster 22 is the remote source. If
the same plan is created on cluster 22, cluster 22 remains the Plan Owner and
must invoke cluster 32 through the same signed site contract. A data-plane
worker never acquires lifecycle authority merely because it executes on the
source or target site.

Each registered ABLESTACK site exposes a signed Mold API execution broker. The
broker resolves local UUIDs, dispatches commands to its local Agent/FTCTL, and
returns an accepted operation reference. Status and cancellation use the Plan
UUID, Run UUID, and operation reference. API secrets are decrypted only for the
outbound request and are never written to an FTCTL profile or event.

The execution broker is an FTCTL site capability, not a DR Plan API. Therefore
it is registered by the always-on `ftctl-service` plugin whenever
`cloud.ftctl.service.enabled=true`; it must not depend on the remote site's
`cloud.dr.service.enabled` value. A site may execute delegated work for a Plan
Owner without exposing DR Site or DR Plan menus locally. The Plan Owner alone
creates Runs and decides lifecycle state; the remote broker only validates and
executes the allow-listed site-local command.

The broker boundary has two independent contracts:

1. `SiteExecutionBroker` resolves a site-local host UUID and submits or polls
   Agent/FTCTL work.
2. `SiteResourceBroker` inventories and creates, starts, stops, retains, or
   deletes site-local Cloud VMs, volumes, networks, and offerings.

The current `22 -> 32` test invokes the execution broker remotely on cluster
22 and uses the resource broker locally on the Plan Owner/target cluster 32.
A source-side Plan Owner must route target materialization through the remote
resource broker; it must never resolve remote UUIDs with the Plan Owner's local
numeric DAOs.

```mermaid
flowchart LR
  UI[Plan Owner UI] --> API[Plan Owner DR API]
  API --> DB[(Plan Owner DR DB)]
  API --> SB[Source Site Broker API]
  API --> TB[Target Site Broker API]
  SB --> SA[Source Agent]
  TB --> TA[Target Agent]
  SA --> SF[Source FTCTL]
  TA --> TF[Target FTCTL]
  API --> TC[Target Cloud VM and Volume Lifecycle]
  SF <--> NBD[remote NBD data plane]
  NBD <--> TF
  SB --> API
  TB --> API
  API --> DB
  DB --> UI
```

## 3. Storage And Replication Modes

| Mode | UI name | Data path | Durability boundary |
| --- | --- | --- | --- |
| Scheduled | RPO scheduled replication | RBD snapshot/diff or full seed -> remote NBD -> target librbd | target flush, export drain, checkpoint commit |
| Live | Near-real-time replication | running VM krbd -> QEMU mirror -> remote NBD -> target librbd | mirror ready plus periodic durable checkpoint |

VM execution continues to use the Cloud-created krbd attachment. Replication
writes use librbd through the target-side NBD exporter. A target VM is never
started while its writer/export is active.

Live mode is selectable only when all source disks support QEMU live mirror,
both site brokers support the contract version, target NBD capacity is
available, and the network preflight succeeds. Otherwise the UI explains why
only scheduled mode is available. Live mode does not mean synchronous zero-RPO;
the displayed RPO is measured from the latest durable checkpoint.

## 4. Remote Site Broker Contract

### 4.1 Inventory

`listDrSiteWorkloads` returns VM hardware, NICs, and every protected volume:

- VM UUID, instance name, power state, host UUID;
- firmware, secure boot, machine type, CPU, memory, guest OS;
- volume UUID, RBD pool/image, device target, size, type, device ID, offering;
- NIC/network UUID and MAC address.

Target placement inventory is also read from the selected site: zone, KVM host,
RBD pool, compute offering, disk offering, and network. Local numeric IDs are
never sent across sites; UUIDs are used at every broker boundary.

### 4.2 Resource preparation

The target Cloud creates the stopped replica VM and all RBD volumes through the
existing Cloud-managed FTCTL provisioning service. It returns target VM UUID,
host UUID, volume UUIDs, and canonical `rbd:<pool>/<image>` mappings.

### 4.3 Data-plane action

`submitFtctlDrSiteAction` accepts a redacted Plan profile and immutable
`planUuid/runUuid/action/siteRole/sourceVmUuid`. It resolves a local execution
host, submits the Agent command asynchronously, and returns `ACCEPTED` plus an
operation reference. Duplicate requests with the same Plan/Run/Action are
idempotent.

`getFtctlDrSiteActionStatus` and `cancelFtctlDrSiteAction` use the same identity.
The Plan Owner projects source and target evidence into one canonical Run.

The concrete signed API command is `executeFtctlDrSiteAgentCommand`. Its
parameters are limited to `commandtype`, `commandjson`, and `workerhostuuid`.
The local FTCTL service accepts only `ACTION`, `STATUS`, `CAPABILITIES`, and
`REVERSE_PREFLIGHT`, resolves the host by UUID, requires an Up KVM host, and
returns the typed Agent answer. The legacy DR-plugin-owned broker command is not
used for cross-site execution because a worker site is not required to enable
the DR Plan service.

Cloud API serialization may expose the broker fields either directly below
`executeftctldrsiteagentcommandresponse` or below its
`ftctldrsiteagentcommand` object. The Plan Owner client unwraps both forms and
requires `answerclass` plus `answerjson` before accepting the remote execution.
This wire-compatibility rule is covered by regression tests so a valid
site-local Agent answer cannot be misclassified as a remote engine outage.

## 5. UI Lifecycle

All mutations are UI initiated and immediately return an accepted Run. The UI
polls cached Plan projection and does not block on Agent or FTCTL work.

| UI action | Expected authority and result |
| --- | --- |
| Create plan | Plan Owner validates both site inventories and stores UUID mappings |
| Full resync | target Cloud resources -> source broker seed -> durable target checkpoint |
| Pause / Resume | Plan Owner sends scheduler control to the currently active source site |
| Test failover | target Cloud creates isolated test VM from the latest durable target artifact |
| Test cleanup | target Cloud removes test-only VM/artifacts and resumes protection |
| Failover | source isolation/final sync when available, writer drain, target Cloud starts replica |
| Failback | reverse broker path replicates target changes into Cloud-owned source RBD, then source Cloud starts VM |
| Reprotect | active-site broker becomes source and the opposite site becomes target |
| Release | stops protection; target retention or deletion follows the explicit operator choice |
| Delete plan | removes Plan metadata only after the selected resource disposition completes |

## 6. State And Failure Rules

- Broker unavailability is `WAITING_SITE`, retryable, and never a false terminal
  failure.
- Target resource or NBD shortage is `WAITING_RESOURCE` with bounded backoff.
- A successful Run requires both data-plane durability and Cloud VM/volume
  lifecycle convergence.
- A late status reply cannot overwrite a newer terminal Run.
- Failover/failback authority transitions and durable checkpoint commit are
  transactional in the Plan Owner DB.
- UI action eligibility is derived from the canonical Plan state, not from the
  latest event text.

## 7. Preflight And Regression Gates

Preflight must verify signed API calls in both directions, source VM inventory,
target placement UUIDs, RBD feature compatibility, NBD device/port capacity,
QEMU mirror capability, Agent-controlled NBD reachability, available capacity,
and stopped target VM state. Source FTCTL never receives target SSH credentials
and never starts a process on the target host directly.

The target Agent allocates an export port from the configured reserved range.
It starts every disk export as one operation and records a Plan-scoped manifest.
If any disk, RBD image, port, or `qemu-nbd` start fails, all exports opened by
that attempt are stopped and the incomplete manifest is removed. A collision on
the deterministic first port searches the remaining reserved range before the
operation becomes `WAITING_RESOURCE`.

### 7.1 Verified 22 -> 32 data-plane preflight

The implementation preflight on 2026-08-24 used a temporary 64 MiB image in
the 32-cluster RBD pool. The 32.2 target Agent host exported it with
`qemu-nbd` on the configured `11809-11872/tcp` range. The 22.1 source host read
the export metadata, wrote a 4 KiB `0x5a` pattern, and read the same pattern
back successfully. The exporter and temporary RBD image were then removed.
This proves the Plan Owner selected data path without touching a protected VM
disk. The deployed configuration has duplicate port declarations; the last
declaration is effective and package deployment must converge the file to one
canonical declaration.

Deployment preflight must also call `listApis` or issue a signed broker probe on
every ABLESTACK site and verify `executeFtctlDrSiteAgentCommand` is present
before a Plan Run is accepted. Missing API registration is a deployment error,
not a credential error and not a terminal Plan failure.

Before deployment, run the FTCTL baseline contract suite for sync,
pause/resume, release/tombstone, test failover/cleanup, failover, failback, and
reprotect. Run the existing VMware-to-ABLESTACK tests unchanged. The release is
blocked if either contract regresses.

## 8. AS-IS / TO-BE

| Area | AS-IS | TO-BE |
| --- | --- | --- |
| Controller | worker binding assumes local source/target hosts | Plan Owner Cloud controls either remote site through broker APIs |
| Authority placement | target-side ownership is implicit in the tested path | whichever Cloud stores the plan remains authoritative; source/target location does not transfer control |
| Remote execution API | broker registration depends on the remote DR Plan plugin being enabled | FTCTL service always exposes the narrow site-local broker while the Plan Owner retains lifecycle authority |
| Broker response contract | nested Cloud API object is mistaken for a missing typed answer | Plan Owner accepts the flat and standard object-wrapped response forms and validates the typed Agent payload |
| KVM inventory | VM summary only; disks and hardware absent | complete VM, RBD disk, NIC, and hardware inventory by UUID |
| Target placement | Plan Owner local DAOs used for every KVM target | selected target site's Mold inventory and lifecycle APIs |
| KVM replication | repeated local `qemu-img convert` full seed | remote-NBD full/incremental plus optional QEMU live mirror |
| Target export failure | a partial multi-disk start can leave an exporter running | Plan-scoped manifest, reserved-range fallback, and all-or-nothing rollback |
| VM lifecycle | local target materializer assumptions | Cloud owning each VM performs create/start/stop/delete |
| UI | KVM_TO_KVM appears selectable before end-to-end readiness | capability-gated mode and complete asynchronous lifecycle |
