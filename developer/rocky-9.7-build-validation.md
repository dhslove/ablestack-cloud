<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Rocky 9.7 Build Validation

## Policy Note

This document is now a technical reference only.

- The operational source of truth is [build-release-policy.ko.md](/Users/dhslove/Documents/GitHub/dhslove/ablestack-cloud/developer/build-release-policy.ko.md).
- Official development builds and release builds must use GitHub Actions.
- Local builds must not be used as the standard delivery path.

## Goal

- Validate that this repository can build and package on Rocky Linux 9.7.
- Prefer GitHub-hosted, serverless packaging over local workstation packaging.
- Keep a local reproduction path for macOS maintainers when a container runtime is available.

## Current Local Constraint

- This workstation is macOS.
- At the time this document was prepared, no local container runtime was installed:
  - `docker`
  - `podman`
  - `colima`
  - `lima`

That means Rocky 9.7 runtime validation cannot currently be executed directly on this machine without first adding a container or VM layer.

## Recommended Validation Path

Use GitHub Actions as the source of truth for Rocky 9.7 packaging validation.

- Workflow: `.github/workflows/rocky97-rpm.yml`
- Build helper inside the container: `tools/build/rocky97-rpm-build.sh`
- Packaging entry point: `packaging/package.sh --distribution rocky9`

This path is preferred because:

- it matches the final delivery target: serverless GitHub Actions packaging
- it avoids macOS-specific drift
- it pins the Rocky 9.7 base image source
- it uploads generated RPMs and build logs as workflow artifacts

## Rocky 9.7 Container Source

The workflow downloads the official Rocky Linux 9.7 container archive and verifies its checksum before running the build.

- Archive:
  - `https://download.rockylinux.org/pub/rocky/9.7/images/x86_64/Rocky-9-Container-Base.latest.x86_64.tar.xz`
- SHA256:
  - `2213bb44c0f1bfd0bbef16118ab5a1ec6875362c5ad6c6dafdecf28d14151ad2`

## Packaging Notes

- `packaging/package.sh` now accepts `rocky9` as a distribution alias.
- The alias currently reuses the existing `centos8` spec assets.
- This keeps the Rocky 9.7 packaging path explicit without duplicating the spec tree before a dedicated Rocky 9 spec is needed.

## Node and Java Handling

The Rocky 9.7 helper intentionally pins toolchains inside the container:

- Java:
  - installs both `java-11-openjdk-devel` and `java-17-openjdk-devel`
  - uses Java 17 for the Maven build path
- Node:
  - installs Rocky-provided `nodejs` to satisfy RPM build dependencies
  - prepends a pinned Node.js tarball to `PATH`
  - default pinned version: `14.21.3`

This is deliberate because the repository's existing UI workflow still builds with Node 14, while Rocky 9.7 can expose a newer system Node stream.

## GitHub Actions Usage

### Manual run

Run the `Rocky 9.7 RPM Packaging` workflow with `workflow_dispatch`.

Supported inputs:

- `pack`
  - `oss`
  - `noredist`
- `simulator`
  - `default`
  - `simulator`
- `release`
  - optional RPM release override
- `templates`
  - optional template list such as `kvm,xen,vmware`
- `node_version`
  - optional Node.js override; default is `14.21.3`

### Automatic run

The workflow also runs for pull requests that touch:

- `packaging/**`
- `tools/build/rocky97-rpm-build.sh`
- `ui/**`
- `pom.xml`
- `.github/workflows/rocky97-rpm.yml`

## Artifacts

The workflow uploads:

- `dist/rpmbuild/RPMS/**/*.rpm`
- `dist/rpmbuild/SRPMS/**/*.rpm`
- `dist/rocky97-build/build.log`
- `dist/rocky97-build/environment.txt`
- `dist/rocky97-build/artifacts.txt`

## Historical Local Reproduction on macOS

If local runtime validation is ever needed for debugging only, the least disruptive path is:

1. Install a container runtime on macOS.
2. Download and verify the same Rocky 9.7 container archive used by CI.
3. Load it into Docker-compatible runtime.
4. Run the same helper script inside the container.

Example shape:

```bash
docker run --rm \
  --workdir /workspace \
  -e DISTRO=rocky9 \
  -e PACK=oss \
  -e NODE_VERSION=14.21.3 \
  -v "$PWD":/workspace \
  ablestack/rocky97-build:latest \
  bash -lc './tools/build/rocky97-rpm-build.sh'
```

## Validation Status

- Build path design: prepared
- Rocky 9.7 GitHub Actions workflow: added
- Rocky 9.7 in-container helper: added
- macOS local execution: not run on this workstation because no container runtime is installed yet
- GitHub Actions runtime execution: pending first workflow run
