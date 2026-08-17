# Cross-Hypervisor DR Dual-Cluster Test Release Deployment

## Purpose

This document records the test-release build and deployment baseline used to
make the 22 and 32 ABLESTACK clusters available for bidirectional DR tests.
It is an operational handoff document, not a place to store credentials.

## Source Baseline

| Component | Repository | Branch | Build source commit | GitHub Actions run |
|---|---|---|---|---|
| Cloud | `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `8020879482572b7248111efaf710ef19014676b4` | `31673957028` |
| FTCTL | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `84468e78cb8878384902e195ee164ba9d92596f6` | `31671559609` |
| QEMU exec tools and V2K | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `84468e78cb8878384902e195ee164ba9d92596f6` | `31671626742` |

The Cloud workflow is `ABLESTACK Branch Development Release`. The FTCTL
workflow is `FTCTL Branch Development Release`. Both releases use the label
`ftctl-cloud-integration-dual-dr`.

## Test Environment

| Environment | Role | Endpoints | SSH port | Credential storage |
|---|---|---|---:|---|
| 22 cluster | Management | `10.10.22.10` | 22 | GitHub `dr-test` environment secrets |
| 22 cluster | Compute | `10.10.22.1`, `.2`, `.3` | 22 | GitHub `dr-test` environment secrets |
| 32 cluster | Management | `10.10.32.10` | 22 | GitHub `dr-test` environment secrets |
| 32 cluster | Compute | `10.10.32.1`, `.2`, `.3` | 22 | GitHub `dr-test` environment secrets |
| VMware | vCenter | `10.10.21.10` | 443 | GitHub `dr-test` environment secrets |

The 22 cluster historically used SSH port `10022`. Live verification on
2026-08-13 showed that port `10022` is refused on all four nodes and port `22`
is the active authenticated SSH path. Always perform a TCP and SSH preflight
before deployment instead of relying on the historical port.

## GitHub Secret Contract

Create the `dr-test` GitHub Environment in both repositories and keep these
values as environment secrets. Secret values must never be printed, checked
into Git, or copied into this document.

| Secret name | Purpose |
|---|---|
| `ABLESTACK_TEST_22_MANAGEMENT_HOST` | 22 cluster management endpoint |
| `ABLESTACK_TEST_22_COMPUTE_HOSTS` | Comma-separated 22 cluster compute endpoints |
| `ABLESTACK_TEST_22_SSH_PORT` | Active 22 cluster SSH port |
| `ABLESTACK_TEST_22_SSH_USER` | 22 cluster SSH account |
| `ABLESTACK_TEST_22_SSH_PASSWORD` | 22 cluster SSH credential |
| `ABLESTACK_TEST_32_MANAGEMENT_HOST` | 32 cluster management endpoint |
| `ABLESTACK_TEST_32_COMPUTE_HOSTS` | Comma-separated 32 cluster compute endpoints |
| `ABLESTACK_TEST_32_SSH_PORT` | Active 32 cluster SSH port |
| `ABLESTACK_TEST_32_SSH_USER` | 32 cluster SSH account |
| `ABLESTACK_TEST_32_SSH_PASSWORD` | 32 cluster SSH credential |
| `ABLESTACK_TEST_VMWARE_VCENTER_HOST` | VMware vCenter endpoint |
| `ABLESTACK_TEST_VMWARE_VCENTER_USER` | VMware vCenter account |
| `ABLESTACK_TEST_VMWARE_VCENTER_PASSWORD` | VMware vCenter credential |
| `ABLESTACK_TEST_DB_USER` | Local management DB account |
| `ABLESTACK_TEST_DB_PASSWORD` | Local management DB credential |
| `ABLESTACK_TEST_DB_NAME` | Cloud DB name |

GitHub secrets are write-only. Future workflows and Codex sessions can use
their names through GitHub Actions, but cannot retrieve or display their values.

## Deployment Safety Contract

1. Build Cloud and FTCTL artifacts only with GitHub Actions.
2. Verify the artifact source SHA and checksum before deployment.
3. Back up management JARs and active web static assets before changing them.
4. Preserve `/usr/share/cloudstack-management/webapp/WEB-INF` and `META-INF`.
5. Never replace the webapp root and never use `rsync --delete` against it.
6. Install FTCTL on all three compute hosts in each cluster.
7. Verify `mold-agent`, `ablestack-vm-ftctl.timer`, installed scripts, and the
   expected source markers after package deployment.
8. Verify `/client/` returns HTTP 200, the management process is healthy, and
   the active UI bundle contains the current DR markers on both clusters.

## Deployment Result

### Cloud build and dual-cluster deployment

GitHub Actions run `31673957028` completed successfully from commit
`8020879482572b7248111efaf710ef19014676b4`. The deployed Cloud package version
on both management servers and all six compute hosts is
`4.23.0.0-Mold.Europa.202608130629.1`.

| Package | SHA256 |
|---|---|
| `cloudstack-common` | `d697fbb62f16977c78c821a757bd9bc56dd1e720a536ad99c0c3ab82af0772cf` |
| `cloudstack-management` | `018674e64b0458138d7e7f311476487f948d8636cbb997f6f40eee0b2e8d0a9a` |
| `cloudstack-ui` | `13a46ed339be8b0b032042d571590607f658e4bcab569ca7930a43ef2a9a3b19` |
| `cloudstack-usage` | `5213577aaa66e9cd5972bed483a1d36c7c97f977697d040e02b6d5f77db23a97` |
| `cloudstack-agent` | `eabb5861ca5355dcf691b7b0bebe7ccee6719c6cf38c6e822d741da64896329e` |

The 22 cluster used native `rpm -Uvh --replacepkgs`. The 32 cluster has direct
RPM usage intentionally masked and used `aspkg -Uvh --replacepkgs`. Do not
interpret the direct `rpm usage is blocked` message on the 32 cluster as a
host or package failure.

Management backups were created before deployment:

- 22 cluster: `/root/cloud-dual-dr-backup-20260813-142848`
- 32 cluster: `/root/cloud-dual-dr-backup-20260813-142901`

Compute-host agent backups were created under
`/root/cloud-agent-dual-dr-backup-20260813-142933` or the matching per-host
timestamped directory before package replacement.

The 32 management package post-install cleanup incorrectly classified three
new package-owned JARs as unmanaged and moved them to
`/var/lib/cloudstack/management/legacy-lib/20260813T071239Z`. This caused
`ClassNotFoundException: org.apache.cloudstack.ServerDaemon`. The exact
package files `cloudstack-4.23.0.0-Mold.Europa-202608130629.jar`,
`cloud-plugin-storage-volume-linstor-4.23.0.0-Mold.Europa-202608130629.jar`,
and `cloud-plugin-storage-volume-storpool-4.23.0.0-Mold.Europa-202608130629.jar`
were restored to `/usr/share/cloudstack-management/lib` before restarting
`mold`. For future upgrades, check `aspkg -V cloudstack-management` and
`management-server.err` immediately if the package succeeds but
`org.apache.cloudstack.ServerDaemon` is missing. Restore only package-owned
files of the installed build; never copy a JAR from a different release.

Post-deployment verification passed on both management servers:

- `mold` and `mold-usage` are active.
- `/client/` returns HTTP 200.
- `/usr/share/cloudstack-management/webapp/WEB-INF` remains present.
- Active UI bundles contain `blockingLoadingState`, `fetchSyncProgress`, and
  `extractJobId`.
- Both databases contain 21 DR tables and report all three KVM routing hosts
  connected.

All six compute hosts report the deployed Cloud agent version, active
`mold-agent`, and active `ablestack-vm-ftctl.timer`.

### Build compatibility correction

- The first Cloud branch release run (`31667802778`) generated the System VM artifact but failed while generating the RPM API documentation.
- Cause: the new `Dr*` commands generated files such as `listDrSites.xml`, but `tools/apidoc/gen_toc.py` did not map that command family to an API documentation category.
- Resolution: map `Dr*` API documents to the existing `Disaster Recovery` category and repeat the branch test release build before deployment.
- Deployment preflight then found that the 4.22-to-4.23 and Europa after-upgrade paths re-added failback lifecycle columns and their reconciliation index with a non-idempotent `ALTER TABLE`. This is unsafe for the 32 cluster, whose DR schema had already been applied while it was on 4.22.
- Both schema paths now use the project-standard `IDEMPOTENT_ADD_COLUMN` and `IDEMPOTENT_ADD_KEY` procedures. This applies to the run dispatch fields, view-cache diagnostics, event index, cutover-disk fields, and failback lifecycle fields instead of only the last failback block.
- The complete corrected set was executed twice against six isolated tables on the 32 management DB. The second pass retained the expected schema without duplicate-object errors: run 17 columns/2 indexes, run-step 2 columns/1 index, view-cache 4 columns, event 3 columns/1 index, cutover-disk 5 columns/1 index, and failback-session 23 columns/1 index. The procedure and all six test tables were removed afterward.
- A fresh-schema preflight then found that the restore-point backfill queried
  `dr_run` before that table was created. Both the 4.22-to-4.23 and Europa
  after-upgrade scripts now create and extend `dr_run` and `dr_run_step` before
  executing that backfill. The corrected Europa script was applied twice to
  the 22 management DB, which had no DR schema. Both passes succeeded and
  produced 21 DR tables with the expected 32-column `dr_run` contract.
- The superseded run `31669628452` was cancelled before deployment and replaced with a build from the corrected schema commit.

### 32 cluster login regression and API key repair

After the 4.23 deployment, the `10.10.32.10:8080` login request succeeded but
the UI could not finish initialization. The authenticated `listUsers` request
returned HTTP 530, and the UI subsequently raised a permission initialization
error because no user object was available.

The management log identified the actual failure as an `api_keypair.secret_key`
decryption error. The 4.22.1-to-4.23 schema script had copied the legacy
`cloud.user.secret_key` plaintext directly into a field mapped with `@Encrypt`.
Its idempotence check also compared that plaintext value with an existing
encrypted value, allowing a duplicate active key pair to be inserted.

The live 32-cluster recovery preserved the registered API and Secret values:

- A root-only SQL backup was saved as
  `/root/api_keypair-pre-repair-20260813-210237.sql` with mode `0600`.
- A plaintext duplicate whose decrypted value matched the existing encrypted
  pair was soft-deleted.
- A standalone plaintext Secret was encrypted in place with the management
  server's current CloudStack V2 database encryptor.
- The resulting active set contains three key pairs with three distinct API
  keys and no plaintext Secret candidates.
- Session login, `listUsers`, and `listUserKeys` were rechecked and all returned
  HTTP 200. The administrator still has exactly one active key pair.

The source migration now follows a failure-safe sequence:

1. The prepare SQL creates the key-pair schema but does not copy or drop legacy
   key columns.
2. `Upgrade42210to42300.performDataMigration()` reads legacy key pairs, encrypts
   each Secret with `DBEncryptionUtil`, updates the oldest matching pair, and
   soft-deletes any active duplicates. If no matching pair exists, it inserts
   a new encrypted pair.
3. The cleanup SQL drops `cloud.user.api_key` and `cloud.user.secret_key` only
   after the Java migration completes successfully.

Focused schema-module tests cover encrypted insertion, deterministic duplicate
reconciliation, and retry behavior when the legacy columns are already absent.
The WSL ext4 Maven reactor command
`mvn -pl engine/schema -am -Dtest=Upgrade42210to42300Test -Dsurefire.failIfNoSpecifiedTests=false test`
completed with three tests passed and no failures or errors.

### QEMU, FTCTL, and V2K build and deployment

| Artifact | GitHub Actions evidence | Package | SHA256 |
|---|---|---|---|
| FTCTL branch RPM | run `31671559609`, artifact `ftctl-branch-rpm-31671559609` | `ablestack_vm_ftctl-0.9.5-1.noarch.rpm` | `7b4c4bc293078e66d3344ad95e59947bb27ad876db1c44d15c7fe44f3dc22931` |
| FTCTL Rocky 9.7 RPM | run `31671626742`, artifact `ftctl-rpm-package-rocky9.7` | `ablestack_vm_ftctl-0.9.5-1.noarch.rpm` | `8aad3cda252d598df52edf4fecbd64a3882be8c54e7ef05f6be755a2eae16e08` |
| QEMU exec tools | run `31671626742`, artifact `rpm-package-9.7` | `ablestack-qemu-exec-tools-0.9.5-1.el9.el9.noarch.rpm` | `89fe8e79d8b38dd9088f5280e2d0e8cf55fb1189596dffda06cc06beba246784` |
| V2K | run `31671626742`, artifact `v2k-rpm-package-rocky9.7` | `ablestack_v2k-0.9.5-1.el9.el9.noarch.rpm` | `46c3f7cffdb044d97a4e612f48a76065f0506fe44ae1235f077260edba9aed0e` |

The Rocky 9.7 packages were installed on `10.10.22.1` through `.3` and
`10.10.32.1` through `.3`. All six hosts reported an active `mold-agent`, an
active `ablestack-vm-ftctl.timer`, the V2K `govc` runtime, and the VMware mover
`--device-key` marker from commit `84468e7`.

The package restart preflight used the existing 32-cluster plan
`ef73f5f3-9740-4bbd-8c9a-74a972e5f19f`. The prior package rejected its legacy
numeric `cbtDiskId=2000`. The corrected package resolved the persisted
`sourceDiskKey=2000`, completed checkpoint 763 as `CBT_INCREMENTAL` with
206,241,792 changed and written bytes, and completed checkpoint 765 after the
V2K update with 6,946,816 changed bytes. The scheduler remained `RUNNING` and
`HEALTHY`, protection returned to `READY`, and no FTCTL error remained.

After the final Cloud agent deployment and scheduler restart, checkpoint 776
also completed as `CBT_INCREMENTAL` with 9,240,576 changed and written bytes.
Cloud DB state was `READY`/`ENABLED` without an error, while `dr-status`
reported scheduler `RUNNING`, a live scheduler PID, durable target data,
materialized target resources, and one valid target disk. This is the final
runtime regression baseline for the 32 cluster.

### GitHub environment registration

- Environment `dr-test` exists in both repositories.
- All secret names in the GitHub Secret Contract are present in both environments.
- Authenticated SSH was verified for all eight ABLESTACK endpoints and an authenticated vCenter session was verified over HTTPS.

## Retest Handoff

Both clusters are ready for DR test-plan creation and execution. The 32
cluster also retains the running regression plan above. Before starting a new
test, verify the selected plan has no active run or stale lock and confirm the
target VM and storage names do not collide with the retained plan.

The next operator action is to create or select a DR plan in the desired
cluster UI and start the test from that UI. Build, package, service, schema,
credential-registration, vCenter-connectivity, and one live incremental-cycle
verification are complete; no additional server-side preparation is required.

## 2026-08-16 Upstream-Aligned Dual-Cluster Test Release

### Source alignment and release builds

The feature branches were first synchronized with their current base branches,
committed, and pushed before any package was built.

| Repository | Feature source used by build | Synchronized base | Merge commit |
|---|---|---|---|
| `dhslove/ablestack-cloud` | `c85cf95d242bcbe7cfbea2e5338f41bb26076e1e` | `upstream/ablestack-europa` at `6a617e5ce3039d0636b5d519af1dff11497885df` | `c85cf95d242bcbe7cfbea2e5338f41bb26076e1e` |
| `dhslove/ablestack-qemu-exec-tools` | `a30584e3b287b28e596b6362df4b4977cf1c4156` | `upstream/main` and `origin/main` at `72854d47a5355752ffb98d37682e9aeecb177795` | `a30584e3b287b28e596b6362df4b4977cf1c4156` |

GitHub Actions completed successfully from those exact feature commits:

- Cloud full branch development release: run
  [`31917005031`](https://github.com/dhslove/ablestack-cloud/actions/runs/31917005031)
- QEMU/V2K/N2K/HangCTL full package build: run
  [`31917006777`](https://github.com/dhslove/ablestack-qemu-exec-tools/actions/runs/31917006777)
- FTCTL branch package build: run
  [`31917008164`](https://github.com/dhslove/ablestack-qemu-exec-tools/actions/runs/31917008164)

The Cloud test package version is
`4.23.0.0-Mold.Europa.202608160021.1`; the release metadata display version is
`v4.10.0-Europa-20260816-ALPHA1`.

Focused pre-release verification also passed from clean WSL ext4 clones:

- 44 DR Maven tests covering admission, protection-group execution, and FTCTL
  runtime projection.
- Four schema-upgrade tests covering the 4.22.1-to-4.23.0 contract.
- Production UI build with the asynchronous DR progress and protection-group
  action markers.
- FTCTL 10/30/100-plan fleet admission smoke, CBT pagination smoke, V2K CPU
  offering smoke, and controller CBT smoke.

### Artifact identity

| Package | SHA256 |
|---|---|
| `cloudstack-agent` | `42c8bea8e4b1b1b13f2c15f3ed365c8a59ed958241448d3747648256c5833ed1` |
| `cloudstack-common` | `a35643c781194f55beba7eb8ba3b9e2eb080850582410f8ec7e19cc3b99678ed` |
| `cloudstack-management` | `668dd2ac59756671ac9c2ec36a145a0597753f3caa0cb360faacfa246c6273a0` |
| `cloudstack-ui` | `319bdafc3c7674c042bb8fa8e479462a35f817f2dbc6ee2a5c94ed8821be27a5` |
| `cloudstack-usage` | `3ce45e805069ce926bdeb5cc1aa3f08da6d4cf5b5eb14d3414810c9def4cf190` |
| `ablestack_n2k-0.9.5-1.el9.el9` | `3fc8475ecf5970efb1e7303cebcc5b578071220dabaa5ac279841f12f84f4748` |
| `ablestack-qemu-exec-tools-0.9.5-1.el9.el9` | `0da113e90757e7f216ece3d349b53fc3b2412ec429b2d2ad36813469fad4153d` |
| `ablestack_v2k-0.9.5-1.el9.el9` | `c02399fb632d572a23ba6096fbfea1760ab791ec3829a63d552323663e391f82` |
| `ablestack_vm_ftctl-0.9.5-1` | `5fe01c40435422ad18b9715d24a9809b9e6b0aa7c296b3cab1989d7a5d89b9d9` |
| `ablestack_vm_hangctl-0.9.5-1` | `64f3aa4a5ab24ad91bc7e0d1c7a4f01a08b341e68e1147f6708005ac5448317a` |

### Deployment matrix and rollback evidence

The same Cloud package set was installed on management servers
`10.10.22.10` and `10.10.32.10`. The same Cloud agent and five first-party
QEMU/FTCTL packages were installed on all six compute hosts. Native `rpm` was
used on the 22 cluster and the administrator `aspkg` wrapper was used on the
32 cluster.

Pre-deployment backups exist at
`/root/dual-dr-release-backup-20260816-092705` on both management servers and
all six compute hosts. Management backups include the active webapp, Cloud
JARs, configuration, installed package list, and DB schema. Host backups
include package lists, Agent state, and installed FTCTL/QEMU/V2K trees.

The 22-cluster HangCTL timers were already intentionally masked and remained
masked. FTCTL timers are active on all six hosts; HangCTL remains active and
enabled on the 32 cluster.

### 32-cluster package cleanup recovery

During the 32 management upgrade, the package cleanup script again moved three
package-owned JARs from the new build into
`/var/lib/cloudstack/management/legacy-lib/20260816T010712Z`:

- `cloudstack-4.23.0.0-Mold.Europa-202608160021.jar`
- `cloud-plugin-storage-volume-linstor-4.23.0.0-Mold.Europa-202608160021.jar`
- `cloud-plugin-storage-volume-storpool-4.23.0.0-Mold.Europa-202608160021.jar`

The exact same-build files were restored before service startup. Subsequent
`aspkg -V cloudstack-management` reported no missing package-owned JAR, the
remaining timestamp-only differences matched those restored files, and
`ClassNotFoundException: org.apache.cloudstack.ServerDaemon` did not occur.
`WEB-INF` remained present throughout deployment.

After the Agent rollout, hosts 2 and 3 on the 32 cluster initially remained in
`Connecting`. The management log showed that the two-second DR projection
scheduler was dispatching to the reconnecting hosts while their host-join
locks were being acquired. Deployment recovery temporarily set
`dr.projection.scheduler.enabled=false`, restarted management so all three
Agents could attach, and restored the setting to `true`. All three hosts then
converged to `Up/Enabled`, and no new host-lock or FTCTL status-answer warning
was observed in the post-recovery interval.

### Final verification result

Both clusters passed the same final checks:

- `mold` and `mold-usage` are active; `/client/` returns HTTP 200.
- The active webapp retains `WEB-INF` and contains `blockingLoadingState`,
  `fetchSyncProgress`, `extractJobId`, and `startDrProtectionGroupAction`.
- Administrator session login and `startDrProtectionGroupAction` API discovery
  succeed on both management servers.
- Each DB has 23 `dr_%` tables, including `dr_run`, `dr_resource_lease`, and
  `dr_group_run`; accepted-cycle and protection-group columns are present.
- Active DR Run, resource lease, and protection-group Run counts are all zero.
- All six routing hosts are `Up/Enabled` and report Agent version
  `4.23.0.0-Mold.Europa-202608160021`.
- `mold-agent` and `ablestack-vm-ftctl.timer` are active on all six hosts.
- NBD is loaded with `nbds_max=32` and the persistent module configuration is
  present on all six hosts.
- Installed FTCTL scripts contain separate Full Seed and Incremental slots,
  retryable `WAITING_RESOURCE`, and VMware `--device-key` handling. Installed
  V2K scripts also contain `--device-key` handling.

The paired 22/32 environments are therefore aligned to the same source-built
test release and are ready for UI-driven single-plan and protection-group DR
retesting. No additional server-side deployment step is required before the
operator starts the next test.

## 2026-08-18 Requested-Cycle Terminal Race Patch

### Scope and source identity

This deployment closes the race in which a completed Full Seed was followed by
the next incremental scheduler cycle before Cloud had durably terminated the
accepted protection-group child Run. The patch preserves the existing
VMware-to-ABLESTACK RBD data path and changes only terminal evidence,
canonical-cycle ownership, lease convergence, and the operator-facing
consistency state.

| Repository | Branch | Deployed commit |
|---|---|---|
| `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `73147967a5f394386ef43a80833d506f6626fd14` |
| `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `3847172799` |

The FTCTL package was produced by GitHub Actions run
[`32079201628`](https://github.com/dhslove/ablestack-qemu-exec-tools/actions/runs/32079201628).
The resulting `ablestack_vm_ftctl-0.9.5-1.noarch.rpm` SHA256 is
`dc1c429651cb7f222192e02589ea8f18365210696d922b16ffa6b3c33e1579ca`.

Cloud was built as the changed disaster-recovery Maven module from a clean WSL
ext4 clone. The 14 changed classes were injected into the installed aggregate
Cloud JAR, following the changed-class deployment rule. The deployed aggregate
JAR SHA256 on both management servers is
`244c513b6ee484eebe66c75f600a1b8f833d3d7742ea89232fd0b30f66c9cecb`.
The UI overlay archive SHA256 is
`8ac3226fa3e80d0e1f761f226c5b673a1112a706871029039f5fe37a8bf4a453`.

### Build and smoke verification

- The requested-cycle terminal repair passed the one-disk, two-disk, and
  Windows test matrix.
- `DrProtectionGroupServiceImplTest` and
  `FtctlDrRuntimeProjectionAdapterTest` passed 44 tests with no failures or
  errors.
- The production UI build passed and contains the light/dark result-finalizing
  state and localized transfer-complete/result-verification labels.
- The full FTCTL self-test runner reached a pre-existing reconcile/fencing
  harness wait; the two terminal-race cases were therefore rerun directly and
  passed, and the GitHub Actions package build completed successfully.

### Dual-cluster deployment result

The FTCTL RPM was installed on `10.10.22.1`, `.2`, `.3` with native `rpm` and
on `10.10.32.1`, `.2`, `.3` with `aspkg`. All six hosts report
`ablestack_vm_ftctl-0.9.5-1.noarch`, an active FTCTL timer, and the
`dr-requested-cycle-terminal-v1` capability. Installed script SHA256 values are
identical across the six hosts:

- `dr_runtime.sh`:
  `085c35c2dfdc3adc7f8b418cdd66ee2a3e839f31ea28c04dd66fbbb09a077fd8`
- `dr_scheduler.sh`:
  `bbba6519137ff905c5baca4b25d2ebae0926d20f90e800687c434e4070566822`

Cloud changed classes and the static UI overlay were deployed to both
management servers while preserving `WEB-INF`. Rollback backups are stored at
`/root/ftctl-terminal-race-20260818-081700` on `10.10.32.10` and
`/root/ftctl-terminal-race-20260818-081809` on `10.10.22.10`.

Both management servers passed the post-deployment checks: `mold` is active,
`/client/` returns HTTP 200, `WEB-INF` exists, the aggregate JAR and changed
class hashes match, and no new startup linkage failure was observed. Installed
terminal-race self-tests also passed on one compute host in each cluster.

### 32-cluster retest cleanup

Three child Runs from protection-group Run 5 predated this patch and had no
live parent monitor. Their cancel requests were accepted by the public API but
could not naturally leave `CANCEL_REQUESTED`. The cleanup was therefore bounded
to Run IDs 189, 190, and 191 and their exact UUIDs; only their open
`runtime-projection` steps were changed to `CANCELED`. All resource leases are
`RELEASED`, and no current Run is active for the Windows, Rocky, or Ubuntu
plans. Historical group Run 5 remains `FAILED` as an audit record.

Windows and Ubuntu are `READY`. Rocky is `DEGRADED` only because its current
RPO is stale; this is the expected starting condition for a Full Seed recovery
test and does not represent an active Run or resource conflict. The operator
can now select the three plans and run **Protection Group Action > Full
Synchronization** to verify concurrent terminal convergence and immediate
next-incremental scheduling.
