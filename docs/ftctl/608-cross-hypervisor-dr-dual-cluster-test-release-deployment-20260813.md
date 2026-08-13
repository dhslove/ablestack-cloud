# Cross-Hypervisor DR Dual-Cluster Test Release Deployment

## Purpose

This document records the test-release build and deployment baseline used to
make the 22 and 32 ABLESTACK clusters available for bidirectional DR tests.
It is an operational handoff document, not a place to store credentials.

## Source Baseline

| Component | Repository | Branch | Build source commit | GitHub Actions run |
|---|---|---|---|---|
| Cloud | `dhslove/ablestack-cloud` | `feature/ftctl-cloud-integration` | `f1a521829ec8e0704b69462168b1d5e94b9b0829` | `31667802778` |
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

This section is updated after the GitHub Actions builds, dual-cluster
deployment, and post-deployment verification complete.
