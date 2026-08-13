# Cross-Hypervisor DR Dual-Cluster Test Release Deployment

## Purpose

This document records the test-release build and deployment baseline used to
make the 22 and 32 ABLESTACK clusters available for bidirectional DR tests.
It is an operational handoff document, not a place to store credentials.

## Source Baseline

| Component | Repository | Branch | Build source commit | GitHub Actions run |
|---|---|---|---|---|
| Cloud | `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `6d4cd27c92cf74e19219523d0cb5eeeb90c767ac` | `31669628452` |
| FTCTL | `dhslove/ablestack-qemu-exec-tools` | `feature/ftctl-cloud-integration` | `0c8cb99debb18994bedb0f50b989491c721689a3` | `31667802879` |

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

### Build compatibility correction

- The first Cloud branch release run (`31667802778`) generated the System VM artifact but failed while generating the RPM API documentation.
- Cause: the new `Dr*` commands generated files such as `listDrSites.xml`, but `tools/apidoc/gen_toc.py` did not map that command family to an API documentation category.
- Resolution: map `Dr*` API documents to the existing `Disaster Recovery` category and repeat the branch test release build before deployment.
- Deployment preflight then found that the 4.22-to-4.23 and Europa after-upgrade paths re-added failback lifecycle columns and their reconciliation index with a non-idempotent `ALTER TABLE`. This is unsafe for the 32 cluster, whose DR schema had already been applied while it was on 4.22.
- Both schema paths now use the project-standard `IDEMPOTENT_ADD_COLUMN` and `IDEMPOTENT_ADD_KEY` procedures. This applies to the run dispatch fields, view-cache diagnostics, event index, cutover-disk fields, and failback lifecycle fields instead of only the last failback block.
- The complete corrected set was executed twice against six isolated tables on the 32 management DB. The second pass retained the expected schema without duplicate-object errors: run 17 columns/2 indexes, run-step 2 columns/1 index, view-cache 4 columns, event 3 columns/1 index, cutover-disk 5 columns/1 index, and failback-session 23 columns/1 index. The procedure and all six test tables were removed afterward.
- The superseded run `31669628452` was cancelled before deployment and replaced with a build from the corrected schema commit.

### QEMU, FTCTL, and V2K build and deployment

| Artifact | GitHub Actions evidence | Package | SHA256 |
|---|---|---|---|
| FTCTL branch RPM | run `31667802879`, artifact `ftctl-branch-rpm-31667802879` | `ablestack_vm_ftctl-0.9.5-1.noarch.rpm` | `53e4947dd05e0afbb1c7ac700845a74e1a376ac8f4fc5a951317bd8b6a454211` |
| QEMU exec tools | run `31668261623`, Rocky 9.7 artifact | `ablestack-qemu-exec-tools-0.9.5-1.el9.el9.noarch.rpm` | `0c3647b555fcbd412a53c95a851bb2c1cb3b72c336c1329b9c70591658ba725a` |
| V2K | run `31668261623`, Rocky 9.7 V2K artifact | `ablestack_v2k-0.9.5-1.el9.el9.noarch.rpm` | `39f47cbc8df56ed0bc5c88f5bd56d26dbb4ef84150762c82244073ef6545cd88` |

The three packages were installed on `10.10.22.1` through `.3` and
`10.10.32.1` through `.3`. All six hosts reported the same package versions,
an active `mold-agent`, an active `ablestack-vm-ftctl.timer`, and the installed
`dr_runtime.sh` SHA256
`1985cf5a4cabf99df3ef44042a03c4426a40dcaee7ec462a899affaf04f3134e`.
The persistent DR scheduler on `10.10.32.2` was preserved across deployment.

The FTCTL build commit precedes documentation-only commit `44446c1`. No QEMU,
FTCTL, or V2K runtime source changed in that documentation commit, so the
deployed executable artifacts remain current.

### GitHub environment registration

- Environment `dr-test` exists in both repositories.
- All secret names in the GitHub Secret Contract are present in both environments.
- Authenticated SSH was verified for all eight ABLESTACK endpoints and an authenticated vCenter session was verified over HTTPS.

This section is updated after the GitHub Actions builds, dual-cluster
deployment, and post-deployment verification complete.
