# QCOW2 DR 13 Test Environment Deployment

## Purpose And Scope

This document records the test-release deployment prepared for local-qcow2 DR
validation involving the 13 cluster. The initial approved scope covered the
management server `10.10.13.10` and compute host `10.10.13.1`. On 2026-08-27,
the same release was additionally deployed to `10.10.13.2`. Host
`10.10.13.3` was not a deployment target.

The deployment establishes the Cloud, Agent, V2K/N2K, qemu-exec-tools, FTCTL,
and HangCTL software baseline. It does not create a DR site or plan and does
not by itself change the cluster storage topology.

## Source And Build Baseline

| Component | Repository | Branch | Source commit | GitHub Actions run | Result |
|---|---|---|---|---:|---|
| Cloud | `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `7fbf4d061cbf54ea11d4b1cb6d632ea87b527f46` | `32973565265` | PASS |
| QEMU exec tools, V2K, N2K | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `2ca46fb454322f088718caea805aa5cd304a8296` | `32973569975` | PASS |
| FTCTL | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `2ca46fb454322f088718caea805aa5cd304a8296` | `32973574881` | PASS |

The deployed Cloud package version is
`4.23.0.0-Mold.Europa.202608261320.1`. The qemu/DR component package version
is `0.9.5-1`.

## Artifact Integrity

The local staging files and the copies on both 13-cluster nodes were compared
before installation. All SHA256 values matched.

| Artifact | SHA256 |
|---|---|
| Cloud release ZIP | `f24c64256e65cd553d8b717278e5b7af43f2c4c015ad6c7ead232bd0135e1df4` |
| Cloud common RPM | `a4cb274cd6f7852b334a7aab833d57e56a3b589f94cba8e0a57f194eb329aaa5` |
| Cloud management RPM | `358070feac267c698dc95a308d3f08b821bba65fdcf113951a7242d7254d2b63` |
| Cloud UI RPM | `32740da644381e8e881345b7361c2b1620e4b306a74370ca92e25e314b725d99` |
| Cloud usage RPM | `a9305ccb8108e277e1e3eefdee5dc97c7e4f6acad4596b6c983e8c6590b350dc` |
| Cloud Agent RPM | `7d075aad99bac51ff2e50edbdf6cc4752e883619f1802b42b0c2bf54c3b9f975` |
| QEMU exec tools RPM | `7cec0f03478c0927c05c1e120257ec2942d80925572e85e7952b717c60890a9b` |
| V2K RPM | `2fb50dc9e959a702de9140302236e7090097927011afbc6adf1187b6072df2b9` |
| N2K RPM | `82ca0a1076be457c6caeec35a3a2d63934732bb6b30369aba0994846a7faad76` |
| FTCTL RPM | `3d4deb43c75ba0c2abe795ac3d8f3d6e8ac7db860c2e4c5c36ad373cb09bdfdb` |
| HangCTL RPM | `11ae2b93e94225583fbca28a94fa3b565a0d1d7b63566a65e2bf1c976f8d0d0d` |

## Authentication Profiles

| Role | Endpoint | SSH port | Successful profile |
|---|---|---:|---|
| Management | `10.10.13.10` | 22 | `13-management-root`, `13-admin` |
| Compute | `10.10.13.1` | 22 | `13-compute-root` |
| Compute | `10.10.13.2` | 22 | `13-compute-root` |

Credential values are not stored in this document or repository. The
following GitHub `dr-test` Environment secret names were added to both
`dhslove/ablestack-cloud` and `dhslove/ablestack-qemu-exec-tools`:

- `ABLESTACK_TEST_13_MANAGEMENT_HOST`
- `ABLESTACK_TEST_13_COMPUTE_HOSTS`
- `ABLESTACK_TEST_13_SSH_PORT`
- `ABLESTACK_TEST_13_SSH_USER`
- `ABLESTACK_TEST_13_MANAGEMENT_SSH_PASSWORD`
- `ABLESTACK_TEST_13_COMPUTE_SSH_PASSWORD`
- `ABLESTACK_TEST_13_ADMIN_USER`
- `ABLESTACK_TEST_13_ADMIN_PASSWORD`

## Backup And Deployment

The management backup is stored at
`/root/qcow2-dr-release-backup-20260826` on `10.10.13.10`. It contains the
Cloud and Usage database dumps, installed package inventory, Cloud
configuration, management libraries, active webapp, and checksums.

The compute backup is stored at `/root/qcow2-dr-host-backup-20260826` on
`10.10.13.1`. The additional `10.10.13.2` deployment backup is stored at
`/root/qcow2-dr-host-backup-20260827`. Each backup contains the package
inventory and the Cloud/ABLESTACK/qemu tool configuration and installation
trees.

All packages were installed with `aspkg`. The active Cloud webapp was kept at
`/usr/share/cloudstack-management/webapp`; `WEB-INF` was verified before and
after installation.

The management package cleanup script incorrectly moved three package-owned
JARs to `legacy-lib`, causing a temporary `ServerDaemon` class-load failure.
Only the three files confirmed by `aspkg -ql cloudstack-management` were
restored from the deployment quarantine. After restoration, management
started normally and package verification reported no missing package JARs.
This is the same package-cleanup protection required by the dual-cluster test
deployment procedure.

On `10.10.13.1` and `10.10.13.2`, NBD was confirmed idle before the module was
loaded or reloaded. The persistent configuration is `nbds_max=32` and
`max_part=16`; devices `/dev/nbd16` through `/dev/nbd31` were verified on both
hosts.

## Verification Results

| Check | Result |
|---|---|
| `mold`, `mold-usage` | active |
| `/client/` from management and external network | HTTP 200 |
| Active webapp `WEB-INF` | present |
| FTCTL UI bundle markers | present |
| Cloud DB schema | `4.23.0.0` |
| `cloud.dr.service.enabled` | `true` |
| DR site/plan API under administrator session | HTTP 200 |
| Active DR sites/plans/runs | 0 / 0 / 0 |
| `mold-agent` on `10.10.13.1` and `.2` | active; both hosts `Up/Enabled` |
| FTCTL and HangCTL timers on `.1` and `.2` | active |
| Runtime `nbds_max` on `.1` and `.2` | 32 |
| `dr_runtime.sh` SHA256 | `5c7d1537637df00a25a0a10a0691d95db0a2b3f67bed75ea81ebc6125375ecaa` |
| `dr_scheduler.sh` SHA256 | `004790aa6f5f35722d395a6cd6e7e9c11e3ae5ad3651e5c72460ab023180c1bf` |
| Connectivity from the 13-cluster deployment to 12/31 management SSH and UI | PASS |

## QCOW2 Test Precondition

The only active primary storage found on the 13 cluster at deployment time is
`primary-gfs`, a cluster-scoped `SharedMountPoint` at `/mnt/glue-gfs`. Package
deployment is complete, but a local-qcow2 to local-qcow2 functional test must
select a VM whose source and target disks are actually file-backed local
storage. The DR site/plan should not be created until that inventory is
confirmed in the UI and Cloud DB.

## Final State

The approved deployment scope (`10.10.13.10`, `10.10.13.1`, and
`10.10.13.2`) is PASS. No package or configuration change was made on
`10.10.13.3`.
