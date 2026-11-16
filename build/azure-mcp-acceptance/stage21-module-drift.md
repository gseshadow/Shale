# Stage 21 PROOF_MODULE_DRIFT: read-only investigation

Reported after PR #1972: checkpoint24 was verified, then one separate-connection
Stage 21 attempt failed with 59400 / `PROOF_MODULE_DRIFT`, no session failures,
and no completion marker. Preserve that attempt's report, packet, all journals,
client/utility hashes and the completed 00–20 evidence. This investigation executes
no SQL, changes no objects and makes no journal recovery decision.

## What is established

The public assertion is **“CIMD proof module source/owner/context drift.”** It is
an existential check over **16 modules**, not a module-specific error. Each must
have a dbo-schema object and a `sys.sql_modules` row satisfying all of:

| Property | Exact requirement |
| --- | --- |
| Definition digest | SHA-256 of the complete `nvarchar` definition, compared with its expected `binary(32)` hash |
| Effective owner | `COALESCE(sys.objects.principal_id, sys.schemas.principal_id) = 1` (dbo) |
| ANSI_NULLS | `uses_ansi_nulls = 1` |
| QUOTED_IDENTIFIER | `uses_quoted_identifier = 1` |
| Execution context | `execute_as_principal_id = -2` for `AiCimdDataLocateAccessOwnerV1`; NULL (CALLER) for the other 15 |

The assertion occurs directly in the installer, in its embedded seal verification,
and in the second Stage 21 source (the standalone verifier). The assertion code
alone does not identify which occurrence or which module/property failed. Absence
of a stage completion marker also does not distinguish these occurrences. Check
retained `CompletedStages`/source markers if available; do not infer them.

The first two occurrences execute **inside the installer's transaction**. Its CATCH
rolls back before rethrowing. A later independent catalog query cannot recover
rolled-back module metadata. If the query below returns 16 absent rows, it cannot
identify the historical failing module. If the standalone verifier failed after a
successful installer commit, the modules may still exist and can be inspected.
No current observation is a substitute for evidence from the failed transaction.

## Offline findings against the reviewed public code

Base: merge of PR #1972 (`bd458064`). Review covered the repository prompt rules,
database schema and tenancy/RLS architecture, CIMD generator, installer, verifier,
acceptance renderer/transport and related generation/packet tests.

- All **16** `@C4aModule0` through `@C4aModule15` hex payloads decode to the expected
  names. SHA-256 of their exact UTF-16LE bytes matches every expected module digest
  in both installer and standalone verifier. No hash was refreshed or replaced.
- Exactly `AiCimdDataLocateAccessOwnerV1` declares `WITH EXECUTE AS OWNER`; the other
  15 declare no execution-context override, matching the expected CALLER metadata.
- The installer sets ANSI_NULLS and QUOTED_IDENTIFIER ON before module creation.
  It creates modules with separate `sys.sp_executesql` calls from hex-decoded
  `nvarchar(max)`. Module bodies therefore do not acquire the rendering file's
  Windows CRLF line endings. The hex already fixes their internal line endings.
- The acceptance renderer supplies the seven XML SET options before each Stage 21
  source, followed by GO. In particular, ANSI_NULLS and QUOTED_IDENTIFIER are ON.
  ODBC SQLCMD's default QUOTED_IDENTIFIER setting is therefore insufficient to
  explain a failure by itself. Source and generated-packet regression tests check
  the exact payloads, expected registries and both SET preambles.
- The renderer preserves the installer and verifier source bytes (apart from file
  newline representation). No accepted include, target fence or session preamble
  rewrites the hex payloads. One connection per stage preserves the two sources'
  order; both sources establish the relevant options independently.
- Ownership and persisted engine flags cannot be established by hashing text.
  No live catalog metadata or exact retained retry packet was available to this
  offline review. A fresh session with no reported failures establishes neither
  a particular module's owner nor its persisted SET/execution-context flags.

**Conclusion:** the public implementation and expected hashes are internally
consistent, and the generated packet passes the byte/preamble checks. There is no
established implementation/hash/runner defect to correct yet. The exact historical
module/property remains unknown. Do not weaken the assertion, change ownership,
rehash definitions or treat a text audit as observed SQL Server metadata.

## Audit the exact retry packet without SQL

Run with Python 3 from the reviewed checkout, using the **retained retry rendering**:

```powershell
python build/azure-mcp-acceptance/proof_module_diagnostic.py --input $ExactRetainedStage21Sql
```

The utility reads only that file and the two public reviewed sources. It emits
fixed public names, hashes, byte counts and booleans; it never prints source,
paths, private values or exception text and never launches a process or connection.
Compare `InputSHA256` with the retained manifest/report hash. Review all of:

- `AllEncodedDefinitionsMatch` and `ExpectedRegistriesMatch`.
- `InstallerMatchesReviewedSource` and `VerifierMatchesReviewedSource`.
- `PacketSessionPreamblesMatch`.
- `EngineMetadataObserved` must remain **false**: this is deliberately offline.

Exit 0 checks payload digests/context declarations and expected registries. The
source and packet-preamble booleans must also be reviewed; an old/different packet
may pass its payload checks while failing those provenance checks. Exit 1 reports
payload/registry differences; exit 2 rejects malformed or unsupported input. None
authorizes a retry. Preserve the exact original file, including its SHA-256; do not
regenerate it in place to obtain a passing report.

## Narrow read-only catalog query

The standalone query is
[`Stage21ModuleDiagnostic.sql`](Stage21ModuleDiagnostic.sql). Independently review
its hash and fixed destination before any separately authorized SQL collection.
This task has not executed it. It requires the existing acceptance owner with
full CONTROL/VIEW DEFINITION, neutral autocommit context, the exact Azure logical
server/database/collation, and reads only database identity/session metadata plus
`sys.schemas`, `sys.objects`, and `sys.sql_modules` for the 16 fixed public names.
It executes no module, reads no application rows, and creates no object or receipt.

For each name it returns separate zero/one comparisons, expected/observed definition
digests, expected execution context, bounded observed context (`CALLER`, `OWNER`,
`OTHER_PRINCIPAL`, `UNAVAILABLE`), and `CurrentAssertionFindingCount`:

- `MISMATCH_NOW`: use the zero property columns to identify the current module and
  exact failed predicate. Multiple modules/properties can fail simultaneously.
- `MATCHES_ASSERTION_NOW`: current metadata satisfies this assertion; it does not
  prove what existed during the failed attempt or establish installation completion.
- `ABSENT_NOW_NO_HISTORICAL_METADATA`: an absent module has no inspectable definition,
  owner or persisted flags. Those comparison zeroes mean unavailable, not proof that
  every property was historically wrong. This is the expected limitation after rollback.
- `DEFINITION_UNAVAILABLE`: stop; do not substitute an empty definition or new hash.

The query outputs neither definitions nor credentials, owner account names, arbitrary
SQL messages or application data. `CurrentAssertionFindingCount` is a current-state
failure count, not a completed-stage count. Do not run the installer to populate it.
The output and exact retained packet audit are the next evidence needed to distinguish
a live metadata mismatch from a stale/different packet or unavailable historical state.
If everything rolled back and no transient metadata was retained, exact historical
identification is impossible from this evidence alone; additional instrumentation
would need a separately reviewed plan, not an investigative installer replay.

## Recovery recommendation

Keep all 24 completed stages and all existing evidence. The new failed/pending
Stage 21 attempt on the selected fork remains unresolved. Do not reuse the older
`Stage21Recovery.ps1` to clear this failure: it supports only the original grouped
16–26 failure and refuses a second selected fork. Do not remove the selection file,
modify either journal, mark a stage complete, reset acceptance or replay 00–20.

First collect/review the retained-packet audit and, only when independently authorized,
the read-only current-state query. If modules are absent, separately reconfirm the
existing binding, OFF controls and PR3 receipt/confinement checkpoint without installing
anything. Object absence alone is insufficient. Any existing/partial module, drift or
uncertain transaction means stop and preserve evidence. There is no automatic recovery
or further execution command in this diagnostic change. A future correction must be
based on an identified discrepancy; any subsequent resume requires a separately reviewed
resolution of the pending attempt while preserving the verified prefix.

Audit compatibility: public-source hashing and narrow administrative catalog metadata
only. No sensitive application read, domain/admin database mutation, audit schema change
or alternative audit trail is introduced. Live SQL behavior remains unverified.
