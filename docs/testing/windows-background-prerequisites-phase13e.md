# Phase 13E Windows background-update prerequisite closure

> Phase 13F now implements the deferred HKLM authority through the existing WiX 3.14 MSI. Installed acceptance is
> still NOT RUN; use `docs/testing/windows-installed-registration-phase13f.md`. Phase 13E policy, signing,
> version-metadata, and `UNSUPPORTED` logged-out contracts are unchanged.

## Public-policy security decision: SUPPORTED

The approved endpoint is `GET /api/public/application-policy?channel=PRODUCTION` (all canonical `ReleaseChannel`
values are accepted; unknown/case-variant values are rejected). It calls the same `ApplicationReleaseReadServicePort`
as the authenticated Phase 11 endpoint and adds no table, policy file, manifest derivation, mutation, tenant context,
or authentication weakening. The authenticated endpoint remains unchanged.

The response contains only channel, revision, recommended version, minimum allowed version, deadline, and database
server time. This is public operational release metadata: no tenant, Shale/Windows user, session/JTI, machine/device,
authorization, audit, row version, release database id, or admin-only field is present. Exact policy targets and
minimum versions reveal rollout/security-support posture slightly earlier than client observation, but provide no
package or account access; the public manifest already reveals package version, channel, publication time, mandatory
marker, URLs, and hash. Enumeration is bounded to three channels and one current row. The read is indexed/cheap,
deterministic for the authoritative row/database-time result, publicly cacheable for 60 seconds, and expected only at
startup/evaluation cadence. No bespoke rate limiter is justified; normal edge/server limits and monitoring remain
deployment responsibilities. Absence returns 204.

## Signing architecture

`build/scripts/sign-windows-artifact.ps1` is the single fail-closed boundary. External CI supplies signtool,
certificate path or store thumbprint, optional file password, RFC3161 timestamp URL, expected publisher, and the
signing-required flag. It signs/verifies both executables and MSI. Verification rejects missing/invalid signatures,
absent timestamps, and publisher mismatch. Signing precedes ZIP creation, MSI publication, and manifest/hash work.
Local unsigned mode remains usable and explicitly non-production.

## Registration decision and owner paths

The minimum schema is `schemaVersion=1`, opaque random `installationId`, immutable Windows `ownerSid`, canonical
absolute `installRoot`, and canonical absolute owner `supportRoot`. It contains no Shale account, tenant, email,
session/JTI, machine fingerprint, or case data. `WindowsInstallationRegistration` validates schema, UUID/SID form,
absolute non-traversing Windows paths, and Shale layout. `OwnerUpdatePaths` derives the fixed updater,
`app/shale-installed-version.properties`, existing Phase 12 `update-attempts`, existing Phase 13B
`updates/update-execution.lock`, and owner update-log paths without `%LOCALAPPDATA%`, impersonation, or credentials.

The current per-user non-elevated MSI cannot safely own the desired machine-readable authority: HKLM and explicitly
ACL-hardened ProgramData require an elevated installer boundary, while HKCU is not reliably visible logged out and
an application-written record lets a normal user nominate arbitrary paths. Phase 13E therefore does **not** create a
misleading registry/file writer. A future installer-owned location should be HKLM with one key per UUID (or
ACL-hardened ProgramData): Administrators/SYSTEM create/update/delete, owner reads, unrelated users cannot write.
Upgrade preserves the UUID and updates in place; repair restores the exact record; uninstall removes only that UUID.
Multiple owners remain distinct. Missing roots are stale and ignored/quarantined, never executed. Duplicate UUIDs,
owner/path mismatch, reparse points/junctions, noncanonical roots, unexpected executable layout, or failed signature
validation fail closed. This requires a separately approved elevated/MSI ownership design.

## Version authority and threat model

The build writes `app/shale-installed-version.properties`; successful ZIP application atomically publishes it only
after payload replacement. Running Shale remains version authority and emits sanitized mismatch/malformed evidence
without rewriting it. The file ignores stale MSI `DisplayVersion`, but its per-user location is writable and is not
privilege-grade trust. A future reader requires validated protected registration and expected-publisher signatures.

Addressed now: narrow policy, release signing enforcement/order, semantic metadata parsing/atomicity, privacy-minimal
registration value validation, explicit owner paths, multiple-install distinction, and safe path rejection. Remaining
blockers: safe execution principal; installer-owned registration ACL and repair/upgrade/uninstall implementation;
reparse-safe installed validation; installed signature/ACL/lifecycle proof; and later full reevaluation. Forged
registration, SID/path edits, executable injection, stale records, junctions, malicious users, replaced binaries,
and compromised ProgramData require ACL + canonical/reparse validation + signature validation—not one control alone.

There is no tenant mutation/sensitive tenant read, so no tenant audit or SQL migration is appropriate. The only API
change is the public read. No scheduler, task API, service, SYSTEM executor, impersonation, credential storage,
logged-out updater, or rollout exists. `LoggedOutAutomaticUpdateSupport = UNSUPPORTED` remains unchanged.
