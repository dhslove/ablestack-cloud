# RPM database upgrade lifecycle design

## Objective

An update of an installed ABLESTACK management RPM must preserve the previous
service state and, when the service was running before the update, start the
new management server during the RPM transaction. Management server startup is
the authoritative database migration entry point. A branch development release
must also contain one KVM System VM image built from the same source commit.

## AS-IS / TO-BE

| Area | AS-IS | TO-BE |
|---|---|---|
| RPM update detection | The old package pre-uninstall script stops and disables the management service for both update and removal. | The new package pre-install script records whether the service was active and enabled before an update. |
| Service removal | Update and removal use the same destructive scriptlet. | Only a final erase stops and disables the service. An update only stops it. |
| Service recovery | Post-transaction enables the service but does not start it. | Post-transaction restores the enabled state and starts a service that was active before the update. |
| Database update | Packaged SQL is not executed until an operator starts the management server later. | The RPM transaction starts the new management server, which executes `DatabaseUpgradeChecker` before serving requests. A failed start fails the scriptlet and prints service status. |
| Same-version schema convergence | `CREATE TABLE IF NOT EXISTS` does not update columns in an existing table. | Diplo after-upgrade SQL reapplies the complete `created` column definition for all six Storage Service tables. |
| Fresh install | The package enables the service but does not start it before DB configuration exists. | Existing behavior is retained. Automatic start applies only to an update of a previously active service. |
| System VM release | The release job downloads a System VM artifact, but an empty or missing image can reach asset assembly. | Release assembly requires exactly one non-empty KVM `qcow2.bz2` image from the same source commit. VMware OVA is not built. |

## RPM transaction sequence

1. `%pre management` removes stale state files and, for `$1 == 2`, records
   `was-active` and `was-enabled` under `/run`.
2. The old package `%preun` may still stop and disable the service during the
   first update to this fixed version. The state files survive that scriptlet.
3. The new `%post` enables the service only on a fresh install. It does not
   overwrite an operator's update-time enablement choice.
4. `%posttrans management` reloads systemd, restores enablement, starts a
   previously active service, waits for a stable active state, and only then
   removes the state files.
5. Management startup runs all Bronto, Cerato, and Diplo before/after scripts.

The CentOS 8 package uses `mold.service`. The CentOS 7 package uses
`cloudstack-management.service`; both implement the same state machine.

## Database convergence

The following existing tables are repaired by `schema-Diplo-After.sql`:

- `storage_service_instance`
- `storage_service_protocol`
- `storage_file_share`
- `storage_block_target`
- `storage_access_rule`
- `storage_identity_domain`

For each table, `created` converges to:

```sql
datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
```

The calls use the existing `cloud.IDEMPOTENT_CHANGE_COLUMN` procedure and are
safe when the desired definition is already present.

## Build and deployment gates

The origin branch development release must use:

- `pack=noredist`
- `base_branch=ablestack-diplo`
- Rocky 9.7 RPM build
- KVM System VM build only
- one non-empty `qcow2.bz2` System VM image
- a whitespace-free System VM image name with a regenerated and verified release checksum
- RPM and System VM assets built from the same resolved commit SHA

If System VM source files changed, deployment is not complete until the newly
built KVM template is registered in the target environment and selected for
new System VMs. When no System VM source changed, registration is unnecessary,
but the release image gate still proves that the branch remains buildable.

## Test-environment acceptance criteria

1. Record installed RPMs, `mold.service` state, DB column definitions, and free
   disk space before deployment.
2. Back up management configuration and database schema before updating.
3. Update with the branch release RPMs without removing existing configuration.
4. Confirm the RPM transaction restarts a previously active `mold.service`.
5. Confirm the login page and its hashed assets return HTTP 200.
6. Confirm all six Storage Service `created` columns have
   `DEFAULT CURRENT_TIMESTAMP`.
7. Confirm a second same-version update is idempotent and leaves the management
   service and login page healthy.
8. Confirm the branch release retains the KVM System VM image and checksums.
