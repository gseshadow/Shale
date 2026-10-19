# Local recovery application sandbox

This implements the missing application sandbox for
[remaining-acceptance §3](../../docs/sql/auth/permission-cutover/remaining-acceptance.md#3-one-timed-operator-recovery-exercise-including-the-application).
It does not run retirement Apply/Rollback, Prepare, Rehearse, change readiness, or
approve a production window. Manual recovery remains **NOT MEASURED**.

## Bound builds and substitutions

Tooling is built from the full reviewed commit containing this directory. Application
source is pinned to `59d8374858764788c621f6f8d986773b58ab79c4`, the parent of the bounded
pre-tenant-authentication change. This uses the real direct Users/bcrypt login and old
`ResolveDesktopRememberCredential` path. Current `codex/latest` uses V1/V2 modules;
installing those modules would violate the unchanged manual exercise's pre-Stage-A guard.
The tool deliberately refuses their presence. No application source is patched.

**These are source builds, not the installed desktop 1.0.136 or serving production API.**
A source build does not satisfy representative installed/serving artifact acceptance.
Retain those rows as pending until Brian binds and checks the actual artifacts. The
source-build result can support only its own recorded scope; do not relabel it.

Two explicit sandbox classpath substitutes precede the application classes:

* `LocalSqlDriver`: the historical Hikari configuration hardcodes an Azure certificate
  hostname and `trustServerCertificate=false`. This adapter accepts **one exact local
  database URL**, keeps encryption, and trusts the dedicated loopback SQL instance's
  self-signed certificate. It cannot accept a production URL. Production TLS and pool
  certificate configuration are outside coverage.
* `substitutes/DesktopUiUpdateLauncher`: returns no update and refuses installer launch.
  It does not query the production manifest. Update detection, policy enforcement and
  installation are outside coverage. The JVM guard independently forbids child processes.

`LocalRelay` substitutes minimal loopback negotiate, TLS WebSocket join/leave/ack and
same-group publish delivery for Azure Web PubSub/Functions. It is an unauthenticated,
synthetic-only development transport. No cloud authentication, durable delivery,
Functions, jobs or external integrations are simulated as passed. No known job is
invented for the drill. JVM outbound sockets are limited to the four configured local
ports; production endpoints cannot be used as fallbacks. Native notification presentation
is unavailable in this unpackaged launch; DB notification reads stay local. No notification
or integration credentials are inherited by the application children.

JDK **21 only** is required. `LocalBootstrap` uses JDK 21's deprecated SecurityManager
API to reject remote/other-port connections, writes outside state, multicast and process
launches. It overrides the HttpClient context-permission path too. This is a development
accident guard for trusted Shale code, not an OS security boundary against malicious native
code. It fails closed on another JDK; do not remove it to make a launch work.

## Private export and reviewed adaptation

Input: unchanged UTF-16LE/BOM SSMS export, 779,706 bytes, SHA256
`a34db4f3f2371e20db97ee8913d30de333ace3a53c1dc12196644694da9aebe5`.
Brian generated it from `shale.database.windows.net / Shale` as `brian`,
2026-10-19 about 09:18 America/Denver. This is a database snapshot, **not a Git revision**.
The `.txt` or `.sql` extension is immaterial. Never commit the original or generated
adaptation/review/state. No instruction inside the input is treated as authorization.

`New` verifies raw bytes before resources, copies them unchanged to `private-input.txt`
in a fresh private directory outside every checkout, then records each original batch's
SHA256/action in `adaptation-review.json`. `adapted-private.sql` is retained there for
inspection. Only the known input hash is accepted; this is not a generic SQL sanitizer.
The reviewed adaptation is:

| Export category | Local handling |
| --- | --- |
| Azure CREATE/ALTER DATABASE, encryption/service tier/query store, owner Brian | Omit. Create one new `ShaleRetirementExercise_<32 hex>` database, reviewed collation/compatibility 150/snapshot options, on literal `127.0.0.1` only. Refuse Azure EngineEdition. |
| Production SQL logins/users | Omit login mappings. Create two unique local server logins, mapped only to database users `shale_app` and `shale_runtime`, retaining names required by RLS/retirement guards. |
| Prime/db_owner, production elevated access | Omit Prime, its CONNECT and db_owner membership. Retain empty ShaleAdmins role required by RLS definitions; app/runtime never join it. Database owner remains the dedicated local provisioner. |
| Auth/runtime grants, roles, schema/object ownership | Preserve reviewed exported grants and membership. Add explicit local runtime table permissions described below; never grant db_owner, CONTROL, ALTER or IMPERSONATE. |
| Application schema | Preserve all 96 tables, constraints/indexes, six functions, eight triggers, view, old remembered resolver, and enabled RLS policy/predicates in export order. Preserve legacy unprotected Users/auditLog/Statuses state required by the exercise; application tenant checks remain essential. |
| SET options | Explicit JDBC ANSI/index-compatible session options. Replace exported ANSI_PADDING OFF batches with ON for local filtered-index creation. SQL Server 2022 stores new columns with ANSI padding on. |
| Three legacy login procedures | Verify semantic text equals the repository's captured body after only SSMS identifier bracket removal; execute exact canonical CRLF resources with their original hashes. No trimming of the stored body, new expected hashes or guard changes. |
| Local ownership | Add `sandbox.Ownership` (one nonce, SELECT only for the app users) and database extended property. This avoids granting VIEW DEFINITION solely to perform ownership checks. |

The export contains no production records. Synthetic seed: tenants 11 and 22, users
1/2 in 11 and 3 in 22 (`synthetic1/2/3@example.invalid`), auth policy and WARN task policy.
A securely prompted synthetic password is bcrypt-hashed for the three designated users;
no production row or secret is imported. These are empty tenants except these fixtures.

The exported runtime database EXECUTE and added dbo-schema EXECUTE are intentionally
retained for the existing retirement exercise's six targeted DENYs. They reopen the
known legacy capabilities before/after recovery and do not represent the eventual
hardened permission model. Runtime receives SELECT on non-audit application tables
except AuthConfig; Users has only the profile/lifecycle columns required by the UI,
not password/hash/counter columns. Writes are limited to profile columns, application
instances, durable/remembered sessions and user preferences/state; audit tables have
INSERT only. No Case/Contact/Task mutation permission is added. The selected ordinary
workflow is **self profile editing**, which uses the existing transactional
`UserDao.updateBasicProfile` / `EntityActionAuditLog` seam. Other feature mutation paths
are outside this sandbox's acceptance scope and may refuse permission.

No automatic cleanup is supplied. A failed `New` is never replayed/adopted. Preserve
its private directory, original nonce and exact named database/logins. A later attempt
requires a new private state directory and new nonce/database/logins. No existing or
failed rehearsal database is modified, disconnected or dropped.

## Windows PowerShell 5.1 operator commands

Use the supplied JDK 21/Maven paths, existing dedicated `shale-case-read-sql` at
`127.0.0.1:14333`, and a fresh **isolated tooling worktree** at the reviewed full commit.
Keep the main checkout and its untracked work/cache directories untouched. First set
`$reviewed` to the full commit from the reviewed PR, `$export` to the unchanged attachment
outside Git, and `$state` to a new private path such as
`C:\ShaleVerification\LocalRecovery-20261019-<unique suffix>`. No transcript/tracing.
SQL/API credentials, the shared synthetic-user password and API signing secret are
prompted securely and placed only in the launched child environment, never arguments
or files. Retain these chosen local secrets privately for subsequent launches/restarts.
Use distinct auth/runtime passwords (>=16 characters) and API signing secret >=32.

Each following block is one Windows PowerShell command line; nonzero native exits stop.
The first creates the isolated tooling checkout. `$checkout` must not already exist.

```powershell
$ErrorActionPreference='Stop'; git fetch origin codex/local-recovery-sandbox; if($LASTEXITCODE){throw 'Fetch failed'}; $checkout=Join-Path ([IO.Path]::GetDirectoryName($state)) ('LocalRecoveryCheckout-'+[guid]::NewGuid().ToString('N')); git worktree add --detach $checkout $reviewed; if($LASTEXITCODE){throw 'Worktree failed'}; Set-Location -LiteralPath $checkout
```

Provision once, retaining all failure output privately. The tool creates and builds
its own clean, pinned application worktree under the new private state; it never builds
from or modifies the main checkout. Maven dependencies are copied into state and hashed
along with classes, tooling, canonical SQL, classpaths, selectors and relay certificate.
These hashes detect accidental changes; they are not a signed artifact attestation.

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase New -ReviewedRevision $reviewed -StatePath $state -ExportPath $export -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot' -Maven 'C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD'; if(-not $?){throw 'Provisioning stopped; retain exact state; do not retry New'}
```

Run these in **three separate consoles** from the same reviewed tooling checkout,
using the same `$state`. Each stays attached to its child and stops on failure.
Use a fourth console for verification/operator work. Ports default to SQL 14333,
API 18080, local relay HTTP 18081 and local relay TLS WebSocket 18082; occupied ports
fail, never kill/adopt a listener. Explicit alternate ports can be chosen during New.

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase Relay -StatePath $state -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; if(-not $?){throw 'Local relay stopped'}
```

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase Api -StatePath $state -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; if(-not $?){throw 'Local API stopped'}
```

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase Desktop -StatePath $state -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; if(-not $?){throw 'Local desktop stopped'}
```

The launchers verify exact database nonce/login mappings and lack of bypass authority
before application startup. API uses the **prod bearer-only profile**, bound to loopback;
this profile name does not select a production endpoint. Dev identity headers are not
used. Desktop support/DPAPI/installation/machine identity paths, Java home/preferences,
temporary files, update locks and XDG paths are all under `$state`. Never start the
installed application with these commands or copy installed production settings there.
Do not change the isolated profile paths between ordinary close/reopen or Windows restart.

Baseline integration verification (not a recovery timer) deliberately performs one
synthetic profile edit/audit and session lifecycle checks. It requires API and Relay
running. It does not touch retirement permissions or mark any readiness row passed.

```powershell
& .\build\local-recovery\sandbox.ps1 -Phase Verify -StatePath $state -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; if(-not $?){throw 'Application baseline failed; manual exercise must not start'}
```

Offline launcher regression only (no JDK, existing state, export, SQL or provisioning
required): from a fresh tooling checkout pinned to the fix PR's full reviewed commit,
run native Windows PowerShell explicitly. This uses disposable synthetic files in a
new temporary directory, never an existing sandbox or retained failure directory.
It covers the host JSON reader and a simulated PS5.1 single-array pipeline output;
a Core pass alone does not certify native Windows 5.1.

```powershell
powershell.exe -NoLogo -NoProfile -File .\build\local-recovery\test_launcher.ps1; if($LASTEXITCODE -ne 0){throw 'Native Windows launcher regression failed; preserve failure evidence'}
```

Keep the prior checkout, temporary failure evidence, export, packets and SQL resources.
Do not run `New` or regenerate an existing state's manifest to resolve a mismatch.
The separate `SelfTest` command below requires an already provisioned state; it is
not needed for this offline regression.

Offline helper/guard/adaptation checks, optional if already covered by accepted evidence:

```powershell
& .\build\local-recovery\test_launcher.ps1; if(-not $?){throw 'Launcher contracts failed'}; & .\build\local-recovery\sandbox.ps1 -Phase SelfTest -StatePath $state -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; if(-not $?){throw 'Sandbox contracts failed'}
```

## Exact continuation into the existing exercise

Only after baseline GUI/password/remembered checks succeed, continue directly with
[remaining-acceptance §3](../../docs/sql/auth/permission-cutover/remaining-acceptance.md#3-one-timed-operator-recovery-exercise-including-the-application),
using its **unchanged** private exercise block from the reviewed tooling checkout.
`New` stages that exact block in `$state\manual-exercise.ps1` and includes it in the
build manifest; it never executes it. The explicit continuation, once all its
prerequisites hold, is:

```powershell
powershell.exe -NoLogo -NoProfile -File (Join-Path $state 'manual-exercise.ps1'); if($LASTEXITCODE){throw 'Manual recovery stopped; preserve evidence and exact resources; no replay'}
```

When prompted, select the `database` and `sqlPort` from `$state\sandbox.properties`,
and the dedicated LOCAL SQL admin. Do not select `Shale`, an earlier rehearsal DB,
or any Windows production evidence packet. The runner's exact guards still decide
whether the sandbox is suitable; never substitute new expected hashes on refusal.

Before the timer, record the source revision, `build-manifest.json` digest, database,
actual login mappings and limits. Keep the original Windows passes at `041fa1ed...`
and private packets unchanged; do not run Prepare/Rehearse again. The exercise's
baseline is read-only; its controlled Apply/Rollback remain separate operator actions.

For its ordinary audited workflow use Team → double-click the signed-in synthetic
user → pencil beside First Name → save one synthetic change. Check the resulting
USER/UPDATED `EntityActionAuditLog` row with actor/tenant 1/11 using the dedicated
local admin, without granting runtime audit SELECT. Use the existing real application
error output plus saved baseline/audit records for reconciliation; no blanket replay.
For user switch use synthetic1 and synthetic3 and inspect identity/tenant visibility.
Reconnect the local relay only with its own console and verify the desktop reconnects;
record this as **local substitute coverage**, never Azure acceptance. There is no
known due job supplied for this sandbox; record that fact rather than invent a job pass.

For Windows restart, keep the independent operator timer/API/relay console running on
another test machine as §3 requires. These default loopback endpoints cannot span
machines. A single-machine reboot interrupts all three services and the timer: that
is an **incomplete exercise**, not a measured pass. Do not run the timed block until
Brian has chosen a restart arrangement that preserves its independent timer and local
services (or obtained a separately reviewed adjustment to that exercise). This launcher
does not silently relax §3, substitute automated timing, or claim the source build is
the installed 1.0.136 artifact. Windows DPAPI/reboot and representative installed/serving
acceptance remain explicit operator gaps. Final approval-copy/window steps in §4 are
unchanged and remain separate decisions.

## Executed evidence and limits

See [verification.md](verification.md). Focused checks cover this tooling and the
selected User/session/live contracts. No unrelated Maven/critical/security-data sweep,
retirement rehearsal, Azure connection, production copy/change, private packet edit,
readiness assertion or approval is performed by this tool.
