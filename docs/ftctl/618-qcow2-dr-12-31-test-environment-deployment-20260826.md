# QCOW2 DR 12/31 Test Environment Deployment

## Purpose

This document records the test-release build and deployment baseline prepared
for the ABLESTACK local-qcow2 to local-qcow2 DR path between the 12 and 31
clusters. It records authentication profiles and verification results without
storing credential values.

## Source And Build Baseline

| Component | Repository | Branch | Source commit | GitHub Actions run | Result |
|---|---|---|---|---:|---|
| Cloud | `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `7fbf4d061cbf54ea11d4b1cb6d632ea87b527f46` | `32973565265` | PASS |
| QEMU exec tools, V2K, N2K | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `2ca46fb454322f088718caea805aa5cd304a8296` | `32973569975` | PASS |
| FTCTL | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `2ca46fb454322f088718caea805aa5cd304a8296` | `32973574881` | PASS |

The deployed Cloud package version is
`4.23.0.0-Mold.Europa.202608261320.1`. The deployed FTCTL/qemu tool package
version is `0.9.5-1`.

## Artifact Integrity

| Artifact | SHA256 |
|---|---|
| Cloud release ZIP | `f24c64256e65cd553d8b717278e5b7af43f2c4c015ad6c7ead232bd0135e1df4` |
| Cloud management bundle | `c745a916bf209a7d3974e6cac3867251870e960d820a620f30f9d73756f6cc1b` |
| Cloud host common/agent bundle | `d0220ff32e7f27c62334938aa31f59561a2bb9bc8143707923601947864181ab` |
| FTCTL RPM | `3d4deb43c75ba0c2abe795ac3d8f3d6e8ac7db860c2e4c5c36ad373cb09bdfdb` |
| QEMU exec tools RPM | `d92fed1546c6958b8535d6f96158efcccda12c38d52d7ef1513256a57e0c34ab` |
| V2K RPM | `8575d421671df387d862f64a575a0e31f67cbc886d561b964ba48dcb40505000` |
| N2K RPM | `332b7ec040e501d24c741abd3212bb1e942039f4ef9c95e33502e8a2ede6a2c4` |
| Hang control RPM | `1e4fda11ed616012797256c85fe2d15aedef605ff897439a9e080b37954267da` |

## Environment And Authentication Profiles

| Cluster | Role | Endpoints | SSH port | Successful profile |
|---|---|---|---:|---|
| 12 | Management | `10.10.12.10` | 22 | `12-management-root`, `12-admin` |
| 12 | Compute | `10.10.12.1`, `.2`, `.3` | 22 | `12-compute-root` |
| 31 | Management | `10.10.31.10` | 22 | `31-management-root`, `31-admin` |
| 31 | Compute | `10.10.31.1`, `.2`, `.3` | 22 | `31-compute-root` |

All eight SSH endpoints and both Cloud administrator logins were tested
successfully. Credential values are stored as GitHub `dr-test` Environment
secrets in both `dhslove` repositories. The secret contract uses separate
management and compute SSH credentials because the 31 cluster does not use one
common password for both roles.

The following secret name families were added without exposing their values:

- `ABLESTACK_TEST_12_*`: management host, compute hosts, SSH port/user,
  management/compute SSH password, administrator user/password.
- `ABLESTACK_TEST_31_*`: management host, compute hosts, SSH port/user,
  management/compute SSH password, administrator user/password.

## Deployment Procedure And Backups

Management-server backups were created under
`/root/qcow2-dr-release-backup-20260826` on both management servers. They
include the installed package list, `cloud` and `cloud_usage` database dumps,
Cloud configuration, management libraries, the active webapp, and checksums.

Compute-host backups were created under
`/root/qcow2-dr-host-backup-20260826` on all six compute hosts.

Cloud packages were installed with the administrator package wrapper `aspkg`.
The active UI was updated without replacing the webapp root, and
`WEB-INF` was preserved before and after deployment.

The 31 compute hosts did not initially contain the `nbd` package required by
the qemu tool release. The exact `nbd-3.25-1.el9.x86_64` dependency from the
validated build dependency set was installed before the DR packages.

The 12.3 Agent had a pre-existing missing cluster license path. The identical
license file already active on 12.1 and 12.2 was installed at the expected
cluster license path with directory mode `0700` and file mode `0600`. The
license API then reported the same non-expiring cluster license and the Agent
connected normally.

## Management Verification

| Check | 12 cluster | 31 cluster |
|---|---|---|
| `mold` | active | active |
| `mold-usage` | active | active |
| `/client/` | HTTP 200 | HTTP 200 |
| Active `WEB-INF` | present | present |
| DR schema | version `4.23.0.0`, 23 `dr_%` tables | version `4.23.0.0`, 23 `dr_%` tables |
| `cloud.dr.service.enabled` | `true` | `true` |
| `listDrSites` | authenticated empty response | authenticated empty response |
| `listDrPlans` | authenticated empty response | authenticated empty response |

The initial API preflight returned `Unknown API command: listDrSites` even
though the DR module and classes were loaded. The installed configuration had
`cloud.dr.service.enabled=false`, which intentionally makes the DR pluggable
service publish an empty command list. The setting was changed to `true` on
both clusters and both management services were restarted. Authenticated
`listDrSites` and `listDrPlans` then returned valid empty responses.

The active UI bundles contain `blockingLoadingState`, `fetchSyncProgress`, and
`extractJobId`. Package verification found no missing package-owned Cloud JAR,
and the current startup logs contain no `ClassNotFoundException`,
`NoClassDefFoundError`, or schema upgrade failure.

## Compute Host Verification

All six hosts report the following common state:

- `cloudstack-agent-4.23.0.0-Mold.Europa.202608261320.1`
- `ablestack-qemu-exec-tools-0.9.5-1`
- `ablestack_v2k-0.9.5-1`
- `ablestack_n2k-0.9.5-1`
- `ablestack_vm_ftctl-0.9.5-1`
- `ablestack_vm_hangctl-0.9.5-1`
- `nbd-3.25-1.el9`
- active `mold-agent`
- active `ablestack-vm-ftctl.timer`
- loaded NBD module with `nbds_max=32`
- available `qemu-img`, `qemu-nbd`, `nbdkit`, and `virsh`

Installed runtime hashes are identical across the six hosts:

| Runtime file | SHA256 |
|---|---|
| `dr_runtime.sh` | `5c7d1537637df00a25a0a10a0691d95db0a2b3f67bed75ea81ebc6125375ecaa` |
| `dr_scheduler.sh` | `004790aa6f5f35722d395a6cd6e7e9c11e3ae5ad3651e5c72460ab023180c1bf` |

Cloud DB reports all three routing hosts in each cluster as `Up` and
`Enabled`, with the deployed Europa Agent version.

## Network And Clean-State Verification

- 12 compute to 31 management: SSH 22 and Cloud API 8080 are reachable.
- 31 compute to 12 management: SSH 22 and Cloud API 8080 are reachable.
- Both clusters have zero active DR sites and plans.
- Both clusters have zero DR run, resource lease, and sync-cycle rows.

## Readiness Decision

The package, service, API, Agent, FTCTL, NBD, and cross-cluster management
connectivity deployment gate is **PASS** on both clusters.

The 31 cluster currently has an active shared primary-storage record. The 12
cluster currently has no active `storage_pool` or user VM record. Therefore the
software deployment task is complete, but the first local-qcow2 DR functional
test must begin by configuring or discovering 12-cluster local primary storage
and creating/selecting the source qcow2 VM. That infrastructure setup is not a
package deployment failure and must be verified before creating the first DR
plan.

## Next Test Handoff

1. Configure or verify local file-backed primary storage on the 12 cluster.
2. Create or select the 12-cluster source VM whose disk is local qcow2.
3. Verify a local file-backed target storage option on the 31 cluster; the
   currently registered pool is a shared mount point and is not by itself a
   local-qcow2 target.
4. Register the 12 and 31 DR sites in the controller cluster UI.
5. Run inventory discovery and create a single qcow2-to-qcow2 DR plan before
   expanding to multiple VMs.
