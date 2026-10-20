# Retirement runner: one returned log path

`Invoke-PacketSql` must return exactly one string: its retained SQLCMD log path.
PowerShell can surface the completion values from both asynchronous stream-copy
tasks on the success pipeline. An otherwise successful process can therefore return
an array containing `System.Threading.Tasks.VoidTaskResult` values and a path.
Verify and rollback consumers then fail while reading that result as a file, including
provider/path errors and an unavailable `Raw` parameter. SQL may already have committed.
An exception from the runner is not proof that SQL did not execute.

The fix assigns both awaited completion values to `$null`, while still awaiting both
drains before flushing retained output and recording the exit code. It preserves
the one string path returned at the end. No SQL, approval, hash, packet integrity,
deadline, password, rollback or no-replay behavior changes.

## Focused offline regression

Run from a separate checkout of the reviewed fix, in native Windows PowerShell 5.1:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -NonInteractive -File .\build\auth-verification\test_retirement_transport.ps1
if ($LASTEXITCODE -ne 0) { throw 'Offline real-process regression failed; do not attempt a production change' }
```

Expected: `PASS real child Apply/Verify/Rollback/Verify ... on Desktop 5.1...`.
Any phase, commit or approval messages in this test are synthetic fixture output,
labelled OFFLINE SYNTHETIC, not an actual SQL operation or operator decision.
This needs no SQL credentials, Docker, Java, application launch or network connection.
It compiles one local synthetic console executable with PowerShell's existing
`Add-Type`; that child writes fake Unicode result files and large stdout/stderr streams,
but never connects to SQL. Its fake executable and result files use a fresh synthetic
temporary directory, then it removes that exact directory. No operator packet or
retained SQL resource is touched.
Windows Core is not a substitute for this native Desktop check.

The same test runs on POSIX Core with a Python-only fake child. It exercises the
actual phase consumers and transport, checking exactly one string path, complete
131072-byte stdout and stderr captures, saved original commit identity, Verify check
records, rollback restoration marker and restored-state Verify. Metadata/prompt
fixtures are synthetic; this is not actual SQL guard or application acceptance.

The existing process test now also checks the returned path rather than discarding
it, while retaining its nonzero-exit, parser failure, deadline and credential-boundary
checks. Existing offline packet lifecycle tests continue to cover seals, attempts,
explicit decisions, expiry and no replay. No passing application/SQL rehearsal or
unrelated broad suite is required again by this transport-only fix.

## Existing attempts remain immutable

Do not patch a sealed packet's runner, edit its manifest, delete an attempt marker,
fabricate `rolled-back.json`, replay Apply/rollback, or rebuild that attempted packet.
If SQL restoration was independently verified after a runner failure, keep that
read-only baseline result and representative application recovery records separately.
The absence of a runner restoration marker does not override actual SQL evidence.
Do not rerun the phase merely to obtain a marker.

This fix does not authorize any connection, restart a trial, transfer old approvals,
or provide a replacement production packet. Any later attempt needs a separately
reviewed continuation and new exact-window authorization after the offline Windows
check. Keep original packets, hashes, outputs and recovery evidence unchanged.

Audit compatibility: only runner output handling and synthetic process tests change;
no business-data mutation, sensitive-read audit, application event or audit schema changes.

## Implementation verification

PowerShell Core 7.4.6 reproduced the defect: both the strengthened existing process
test and the new phase-consumer test fail against the original runner's leaked
completion values. Both pass after suppressing those two results. This is an
observed regression reproduction, not a source-text assertion alone.

Final checks on Linux, Core 7.4.6, Temurin JDK 21.0.8 and Maven 3.9.11:

- `SHALE_OPERATOR_TEST_PWSH=/tmp/shale-pwsh-7.4.6/pwsh python -m unittest discover -s build/auth-verification -p 'test_*.py'`:
  38 passed, five skipped (native connection-builder, native cmd transport, native
  operator behavior, real ODBC and real Go client checks unavailable).
- Repository selector's affected security-data command: 54 passed, zero skipped.
  Exact test list: `AuthCredentialLookupTest,AuthUserLifecycleSecurityTest,PreTenantAuthenticationModuleContractTest,CaseAggregateTransactionTest,CaseLifecycleAuditContractTest,ContactAggregateMutationContractTest,ContactMutationContractTest,EntityActionAuditEventTest,EntityActionAuditMigrationContractTest,RequestLookupRlsPhase2MigrationTest,RequestScopedDbSessionProviderTest,ServerSessionSkeletonTest`;
  `mvn -pl shale-data,shale-server -am -Dtest=<that-list> -Dsurefire.failIfNoSpecifiedTests=false test`.
- Critical reactor `mvn -B -ntp test`: 116 passed, zero skipped.
- Windows fake-child C# source compiled separately on Core; that does not establish
  native Windows executable emission or execution.
- Documentation fence/link and `git diff --check` checks passed. SQL files are unchanged.

The selector mapped these paths to security-data without full-suite escalation.
Impact review covered the real transport and its Verify/rollback consumers, existing
offline lifecycle/ODBC tests and adjacent refusal/output/deadline/password tests.
No Java production/test, SQL guard or serializer contract changed. Maven used
task-local proxy/trust settings outside the repository after initial network setup
failures; only the final successful runs above are counted. No database rehearsal,
Azure operation or existing-state update was performed by this implementation task.

**Native Windows PowerShell 5.1 execution of the new real-child regression remains
unverified.** Keep this correction unapproved for another production attempt until
that offline result is reviewed; no passing app or SQL rehearsal is repeated here.
