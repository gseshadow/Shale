# Owner catalog failure investigation — 2026-11-02

PR #1929 stays draft and unmerged. No Azure or production access occurred. Both native
gates remain OPEN. **READY FOR ONE FRESH DISPOSABLE AZURE OWNER ATTEMPT** after
owner-observed Windows validation at `c5d13ea4d93f0b0ca732244506d7b0f5d1b96047`
and review of the mandatory-cleanup live packet. See the
[owner evidence](native-azure-readonly-acceptance.md#windows-owner-validation-and-retry-classification--2026-11-02).
This readiness is not native acceptance. The owner authorized the scoped
Phase1R identity-contract fix after reconciling the stack. Existing valid boxed receipts
and all V1/V2 confinement SQL, module manifests and receipts are preserved.

## Reported evidence and exact source reconciliation

The owner reports head `da7933eacc358080014dfba2558473d31c349a47`, VPN disabled and the
exact non-VPN workstation IPv4 in the firewall. Non-mutating probe: OWNER_CONNECT and
OWNER_PREFLIGHT PASS, approximately 816 ms, loginSeconds=30, socketMillis=120000,
connectedSocketMillis=120000, driver=12.6.1.0; applicationObjectsCreated=false,
nativeAcceptance=false; 1 test / 0 failures / 0 errors / 0 skips. Subsequent setup opened
a new connection and errored with `Invalid object name 'sys.database_recovery_status'`:
1 test / 0 failures / 1 error / 0 skips. This is a catalog incompatibility after successful
login, not the previous connection timeout. The owner subsequently confirmed deletion of the failed resource group: cleanup=CONFIRMED,
productionAccess=false, productionChange=false. No setup retry occurred; never reuse it.

The report attributes the error to OWNER_PREFLIGHT / platform detection. The exact Git
source contradicts that attribution:

- `AiCaseDataAttestation.platform`, lines 205–210, calls only `statement(c)` (creates a
  statement and sets a three-second query timeout) and executes exactly
  `SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))`. It has no recovery-status query,
  catalog helper, receipt helper or platform fallback.
- Setup line **28** invokes OWNER_PREFLIGHT. Its helper at lines 141–153 first calls
  `platform(c)`, then reads DB_NAME/USER_NAME and custom object/principal counts only.
- Setup line **39**, the reported stack location, invokes
  **PHASE1R_AND_V2_PREREQUISITE**, via `phase(...)` and the Phase1R lambda.
- That lambda constructs `AiCaseDataV2AuthoritativeLocalSqlServerTest`, whose inherited
  `AiCaseDataV2LocalSqlServerTest.beforeConfinement` reads `docs/sql/phase1r/engine.sql`,
  substitutes the fixture's reviewed inputs, then executes PREFLIGHT/APPLY/POSTCHECK.

Thus there is **no incompatible query in platform(c)** to replace on this head. The exact
incompatible SQL is in `docs/sql/phase1r/engine.sql`, twice:

```sql
SELECT database_guid
FROM sys.database_recovery_status
WHERE database_id=DB_ID()
```

Line 73 converts this to varchar(36) and compares it with the existing receipt's
`$.database`. Line 123 uses the same conversion as `[database]` when creating the receipt.
The shared Phase1R engine assumes the SQL Server/MI recovery catalog is available in the
Azure Database fixture. Its database-incarnation binding is a receipt integrity check,
not engine discrimination. SQL batch binding/compilation can expose an unsupported object
even when the branch containing it would not execute; receipt absence is no workaround.
[Microsoft documents this catalog for SQL Server and Managed Instance](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-recovery-status-transact-sql),
not Azure SQL Database.

## Why the probe can pass while setup fails

Both tests use exactly the same `ownerSource`, `ownerConnect`, `validateOwner`, connected
guard, `phase` wrapper and `ownerPreflight` helper. They open separate connections; neither
caches or transfers a preflight pass. The probe closes its connection after those two
preflight reads. Setup continues through Stage 0, schema import and prerequisite migrations,
then reaches the incompatible Phase1R batch. The probe never executes Phase1R.

The owner subsequently supplied the full stack through `AiCaseDataLocalSqlServerTest.sql:712`,
`AiCaseDataV2LocalSqlServerTest.beforeConfinement:65`, setup lambda `$4:39`, `phase:160`
and setup `:39`, and corrected the earlier OWNER_PREFLIGHT attribution. The setup lasted
168.1 seconds. Both commands used the same detached head, no tracked/staged edits, and the
probe invocation recompiled the relevant main/test sources. The only untracked changes were
Python caches. There is no evidence of stale Java classes or configuration drift; the
successful probe and failing later Phase1R setup are consistent.

## Correct platform design (already implemented)

Use one documented EngineEdition property read on every connection, with exactly one
non-NULL row. The [documented property](https://learn.microsoft.com/en-us/sql/t-sql/functions/serverproperty-transact-sql)
is sufficient for platform routing; ProductVersion and resource names are not substitutes.

| EngineEdition | Contract |
| --- | --- |
| 5 | Azure SQL Database: own contained SQL authentication, database tokens/authority and reviewed database catalogs |
| 2, 3, 4 | SQL Server: existing login/server/catalog authority contract |
| 8 | Managed Instance: existing server contract |
| NULL, no row, multiple rows, all other editions | Refuse; no fallback or suppressed SQL error |

The owner probe and setup additionally require edition 5. Recognizing edition 8 in production
attestation does not admit MI to this Azure Database fixture. The focused tests now require
exactly this single catalog-free property query for every supported edition, propagation of
SQL failure without fallback, strict two-query owner preflight and rejection of other engines
before owner object reads. V1/V2 Azure runtime routing and Stage 0 explicitly exclude recovery
status as well as existing forbidden server catalogs. No production detector change is needed.

## Full verification-path catalog audit

Reviewed all Java in `com.shale.data.security.ai`, packaged AI SQL, every SQL file under
`docs/sql/ai-integration`, and the reused Phase1R engine. Also searched repository Java/SQL
for recovery status, server catalogs, master/msdb and server Extended Events references.

| Path | Finding |
| --- | --- |
| Owner preflight and platform helper | No unsupported catalog; two owner reads only |
| Azure Stage 0 and embedded dispatcher Azure batch | Database-scoped objects, external sources/tables, scoped credentials, synonyms, expression dependencies, database bits and permissions; no boxed catalog |
| Dispatcher SQL Server/MI branch | Server permissions/principals/endpoints isolated in its own dynamically executed branch; never bound/executed by edition 5 |
| Runtime attestation | login_token, server_permissions/principals/endpoints, server permission checks and HAS_DBACCESS only under SQL_SERVER_OR_MI; Azure identity uses database principals and its own dm_exec_sessions row |
| Native read-only gate | Own-session dm_exec_sessions is documented Azure evidence; master.sys.objects is an intentional zero-row denial probe, whose accepted errors remain narrow; it is not a required successful catalog read |
| AI prerequisite migrations, cumulative verifier, V1/V2 installer/prerequisite/standalone and packaged verifiers | No additional boxed-server catalog references found; new regression scans the AI SQL inventory and packaged catalogs |
| Phase1R reused by setup | Historical two unguarded recovery-status references above; fixed by the engine dispatch below |
| Local SQL test lifecycle/faults and server serving drills | Local-only login/server/XE inventory; Azure setup reuses the SQL executor and beforeConfinement, not the local lifecycle/fault methods |

Static review does not establish live Azure metadata visibility, representation or acceptance.
The catalog scan is scoped coverage, not a general SQL dialect parser or proof of every future
catalog's availability. No missing-object exception is converted into success.

## Required follow-up and preserved boundaries

The production platform detector is already correct and is unchanged. The Phase1R engine
now separately selects the live database identity after the existing dbo/target/collation
checks and under its transaction/lock:

- Editions 2/3/4/8: fixed dynamic query of `database_guid` from recovery status; JSON
  version 1 and the same unprefixed GUID field. Existing valid receipts are read in place,
  not rewritten. This also avoids binding the newer physical-name column on older SQL Server.
- Edition 5: fixed dynamic query of the current database row in `sys.databases`, selected
  by exact DB_NAME, returning `physical_database_name`. JSON version 2 and a
  `AZURE_SQL_DATABASE_PHYSICAL:` prefix distinguish its identity semantics. The schema
  extended-property name stays `Shale.Phase1R.v1`; all ten fields/ownership guards remain.
- No row, multiple rows, NULL/blank identity, unsupported edition or SQL error refuses
  before any RLS addition. Both receipt validation and creation use the same resolved value.
  Receipt version mismatch or changed identity refuses every entry point. No fallback,
  missing-object suppression, target-name special case or database-binding omission exists.

[Microsoft documents physical_database_name](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-databases-transact-sql)
as the unique identifier for the current physical Azure SQL database, and explicitly says
restore or service-level-objective changes change it. It is stable for that physical
incarnation, **not** a promise of identity across scaling/restore/replica lifecycle events.
Those events deliberately invalidate the receipt and require separate review; the engine
never reissues/adopts it. This conservative behavior preserves incarnation binding rather
than weakening it to a logical name, DB_ID or pricing-tier identifier. A common property
was rejected because changing SQL Server's identity source would invalidate valid historical
receipts and physical_database_name is not available on all supported older boxed engines.

The earlier Phase1R runner/README and V2 authoritative fixtures establish local SQL Server
scope only. They do not prove Azure metadata semantics. The new local integration tests
execute real batches on a dedicated loopback SQL Server, with edition injection explicitly
labelled: poisoned unreachable catalog/column names verify branch compilation isolation,
but SQL Server's physical-name value is not Azure identity evidence. Windows native metadata
and stability/representation checks remain owner validation work.

Old code refuses Azure JSON version 2, and this engine rejects cross-platform receipt
substitution. No existing receipt is upgraded or repaired. V1/V2 confinement source/catalog
hashes and SQL remain unchanged because this receipt is Phase1R metadata, not a confinement
module/hash input. A private original change-control receipt remains required.

Windows offline validation of this fix is now owner-confirmed and the next live packet
has been reviewed. The operator packet permits one fresh disposable owner attempt only.
Native read-only and mutation/fault gates remain OPEN. The failed fixture has been deleted;
no retry on that fixture or adoption is permitted.

TLS validation, exact-workstation-only firewall, credential separation, login/socket/query/
cancel/JUnit limits, V2 SQL/receipts and Phase1R ownership/rollback guards, authority boundaries, read-only gate and all
OFF defaults are unchanged. No PHI or business data is read or changed by this follow-up;
existing transactional audit integration/schema is unchanged and no new audit event is needed.

## Verification

All results below are from this follow-up's final code/SQL; no Azure or production
connection occurred. Native Windows/Azure validation is not credited from these runs.

| Check | Result |
| --- | --- |
| Focused Maven (command below) | **33 passed**, 0 failures/errors/skips |
| Real local SQL Maven (command below) | **8 passed**, 0 failures/errors/skips: 6 real batch/receipt tests plus 2 offline contracts |
| Selector-generated affected batch, core/data/server | **160 discovered / 154 passed / 6 opt-in skips**, 0 failures/errors; the six local identity tests passed separately with their opt-in above |
| Critical reactor `mvn -B -ntp test` | **228 passed**, 0 failures/errors/skips, all seven reactor projects SUCCESS |
| `python -m unittest discover -s build/phase1r-verification -p 'test_*.py'` | **3/3 passed**; runner protocol, not native SQL evidence |
| `python -m unittest discover -s build/test-selection -p 'test_*.py'` | **31/31 passed** |
| `python -m unittest discover -s build/sql-confinement -p test_checkout.py` | **11/11 passed** |
| `python build/sql-confinement/checkout_bytes.py` | All **25** inputs, clean index and original V1/V2 source receipts PASS |
| Diff, relative links, exact protected production Java/V1/V2 SQL/resources and credential-generation block comparison | PASS; only the authorized Phase1R engine/README, focused tests and runbook/evidence change |
| Windows owner validation at `c5d13ea4d93f0b0ca732244506d7b0f5d1b96047` | Owner reports checkout/receipts, focused and affected/critical PASS; all 7 modules SUCCESS; Shale Server 65/0/0/0; Phase1R 2+6=8/0/0/0, BUILD SUCCESS; local container removed, `PHASE1R_LOCAL_CLEANUP CONFIRMED`; no Azure/production access |

The local fixture used a new loopback-only SQL Server 2022 Developer container from
`mcr.microsoft.com/mssql/server:2022-latest`, image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
It created only nonce-named synthetic databases. Edition injection exercised the real SQL
compiler and transaction/receipt code, not Azure metadata. All fixture databases and the
owned container were removed; local container cleanup CONFIRMED.

The six SQL tests cover boxed version-1 GUID receipt shape and byte-identical replay;
Azure version-2 physical identity and unreachable recovery-catalog poisoning; foreign,
missing, wrong-version and extra-field receipt refusal across all four entry points;
NULL/blank/missing/ambiguous identity and unknown engine refusal before mutation;
changed physical incarnation and cross-platform receipt substitution; and partial/all
pre-existing predicate ownership with exact rollback preservation. The boxed branch also
passes with an unreachable poisoned Azure column. No failed receipt is rewritten to pass.

```sh
mvn -B -ntp -pl shale-data -am \
  -Dtest=AiCaseDataAttestationTest,AiCaseDataAzureSqlDataSourceTest,AiCaseDataAzureSqlAcceptanceSafetyTest,AiCaseDataAzureSqlDriverTimeoutTest,AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest,Phase1RDatabaseIdentityContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
# Only with the dedicated loopback fixture and local-only opt-in documented in the runbook:
mvn -B -ntp -pl shale-data -am \
  -Dtest=Phase1RDatabaseIdentityContractTest,Phase1RDatabaseIdentityLocalSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dlogback.configurationFile=/workspace/Shale/build/case-read-verification/quiet-logback.xml test
python build/test-selection/select_tests.py \
  --base da7933eacc358080014dfba2558473d31c349a47 --head HEAD \
  --format markdown --output work/platform-selection.md --plan-output work/platform-selection.json
# Execute exactly the generated affected batch (performed before the critical reactor):
python - <<'PYTHON'
import json, subprocess
plan = json.load(open('work/platform-selection.json'))
for args in plan['selected_maven_batches']:
    subprocess.run(['mvn', '-B', '-ntp', *args], check=True)
PYTHON
mvn -B -ntp test
```

Before implementation, reviewed the repository rules, database/tenancy and Azure/confinement
architecture, Phase1R README/runner and existing V2 preparation, owner probe/setup, existing
attestation/datasource/safety/driver/migration tests, selectors and prior owner evidence.
The pre-edit inventory maps engine identity/receipt behavior to the two new Phase1R tests,
platform semantics to existing attestation tests, and shared preflight to datasource tests.
The affected cases/security-data selection covers request/authority/audit regressions across
core/data/server; no mapping exception or optional historical full-suite run was needed.
The final changed-file review found no production Java or confinement SQL/manifest changes.

**Ready for one fresh disposable owner attempt.** Windows validation is complete and the
[exact live packet](native-azure-readonly-operator-runbook.md) is reviewed, including
probe → setup → read-only ordering and mandatory exact-scope cleanup. Both native gates remain OPEN;
no deployment, enablement or production change is authorized or performed.

