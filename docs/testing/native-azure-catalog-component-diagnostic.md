# Read-only Azure catalog component diagnostic

## Harness investigation — 2026-11-04

**BLOCKED pending Windows validation of the revised reader. No further Azure run is authorized.**
The owner reports that the fresh component attempt failed before a complete comparison and
`AZURE_NATIVE_CLEANUP CONFIRMED`. The fixture was destroyed; this is diagnostic evidence only,
not a component difference, receipt drift, acceptance, or permission to retry/adopt.

Investigation baseline: PR #1929 head `56709de2584b9ebcd2cc43e67e80914b4085beef`.
The original reader exits immediately on `execute=false` / update count `-1`; the original valid
mock always returns `true`. The regression now models four leading no-result states plus one
before each SELECT. It reproduces the gap under the proposed sequence, but does **not** establish
the Azure cause: JDBC normally defines false/-1 as exhaustion, and inspection of the pinned
12.6.1.jre11 driver's `SQLServerStatement$1NextResult.onDone` shows that NOCOUNT DML completions
and intermediate non-DML completions are skipped internally. The old failure has no stage evidence.

The reader tolerates 23 no-result advances per gap (four preamble statements plus 19 SETs in the
unchanged resource), resetting after each valid result. It executes SQL once and always advances
with CLOSE_CURRENT_RESULT, with at most 479 advances. Exhaustion without all 19 results still
fails finitely. The allowance also applies after result 19 to detect trailing extra results or
update counts. Zero/positive update counts, missing/extra/duplicated/reordered results, invalid
names, NULL lengths and malformed lengths/digests still fail; result/statement closure is retained.

Other paths shared the same generic failure: datasource construction/open, target validation,
component reads, connection cleanup and report I/O. The failure now adds only one fixed `stage`:
`OWNER_TARGET_PREFLIGHT`, `VERIFIER_TARGET_PREFLIGHT`, `OWNER_COMPONENT_READ`,
`VERIFIER_COMPONENT_READ`, or `REPORT_WRITE`. Connection cleanup belongs to its identity's
target/preflight category; primary read failures retain their category even if cleanup also fails.
Thrown failures contain no driver messages, SQL text, targets, credentials, SIDs, tokens, causes
or suppressed exceptions. Failed comparisons attempt to remove partial/stale TSV output.

Pre-edit test impact: `AzureCatalogComponentsTest` covers traversal, exact expression identity,
comparison and sanitization; the existing five Python launcher guards protect creation, cleanup,
credential bytes and ordering. Existing focused safety/datasource/driver/V2-install/attestation/
migration/Phase1R tests protect the surrounding contracts. The selector maps this Java delta to
cases/security-data (core/data/server). No production source, SQL/resource, receipt, hash, grant,
authority boundary, datasource setting, ordinary 20-second limit or 600-second V2 timeout changes.
Catalog/reference metadata reads introduce no PHI/entity audit event or audit migration.

The validation/live pins below remain historical and unchanged. They intentionally refuse this
executable delta. Do not bypass fences, manufacture/reuse a validation marker or launch Azure.
The owner's delta-only validation instruction supersedes the earlier broad Windows checklist:
run only the focused diagnostic class and five launcher guards described below. No Azure setup,
V2 installation, Phase1R rehearsal or unchanged timeout/datasource suite needs repeating for this
delta. Configured Maven and native Windows results remain required; cached-JUnit checks alone
cannot authorize a live run. Updating/reviewing a launcher baseline is separate work.

Current local verification (Linux; no Azure connection):

- JDK 21 `javac` plus cached JUnit 5.10.3 launcher, using the nine focused classes listed in
  the historical Windows command: 48 executed/passed, zero failures/errors/skips. After adding
  the combined primary-read/close failure assertion, the changed `AzureCatalogComponentsTest`
  class was recompiled and rerun: 10/10 passed. This is supplemental, not Maven/Surefire validation.
- The new leading/interleaved-state method run against the original reader fails with the
  sanitized incomplete-metadata refusal; against the revised reader it passes.
- `python -m unittest discover -s build/azure-native -p test_catalog_components.py`: 5 passed,
  including a rerun after the runbook prose changes.
- `python -m unittest discover -s build/sql-confinement -p test_checkout.py`: 11 passed.
- `python build/sql-confinement/checkout_bytes.py`: 25 pinned files/index and both receipts PASS.
- `python build/test-selection/select_tests.py --base 56709de2584b9ebcd2cc43e67e80914b4085beef --head HEAD`
  checks the unchanged HEAD; explicit `--path` selection of the three uncommitted Java files
  selects cases/security-data and core/data/server. Its affected Maven batch and critical
  `mvn test` were attempted with the configured cache in offline mode; both fail before test
  execution because Spring Boot BOM 4.1.1 is uncached. The focused configured Maven command also
  fails resolving that BOM through the unavailable dependency network path.
- `git diff --check`: PASS. Native Windows/PowerShell and required configured Maven suites remain
  unverified. No Azure resource, fixture reuse, production access or acceptance is claimed.

### Delta-only Windows validation and marker protocol

Final delta review added separate leading-only/interleaved-only cases and explicit private-evidence
canaries. JDK 21 compilation and the cached JUnit rerun pass all 12 diagnostic tests with zero
failures/errors/skips. Both separate traversal regressions fail against the original reader.
The five Python guards and 25 pinned-byte/index/receipt checks pass again. Configured Windows
Maven remains pending; these supplemental checks do not repeat the passed setup/V2/Phase1R work.

Use a clean checkout of the new reader commit reported in the handoff, with JDK 21/Maven in a
fresh Windows shell containing no `SHALE_AZURE_*` variables. The only Maven test selector needed
for this delta is:

```powershell
mvn -B -ntp -pl shale-data -am '-Dtest=AzureCatalogComponentsTest' '-Dsurefire.failIfNoSpecifiedTests=false' clean test
```

Require a fresh `shale-data/target/surefire-reports/TEST-com.shale.data.security.ai.AzureCatalogComponentsTest.xml`:
12 executed, zero failures/errors/skips. The leading-only and interleaved-only tests are separate;
the combined-state test covers missing/extra/reordered results and initial/trailing zero/positive
update counts. The category test checks all five exact allowlisted messages, synthetic SQL,
JDBC target, username/password, SID, token and driver-message canaries, null cause and no suppressed
exceptions, including a primary read failure followed by a failing close. The existing exact SQL
expression comparison test remains part of this same class.

Also run `python -m unittest discover -s build/azure-native -p test_catalog_components.py`:
five passes, protecting the unchanged runbook command blocks, credentials, cleanup and ordering.
Checkout code, resource bytes, SQL expressions, installer, receipts, grants, production source,
V2/Phase1R helpers and timeouts have no diff, so their already-passed Windows/SQL rehearsals carry
forward. No Azure opt-in or private candidate is needed for these two offline commands.

After both pass, record test-only evidence at
`work/azure-native/catalog-reader-windows-validation.json`, with `codeHead` and `sourceHead`
both equal to the full new reader commit SHA, `hostName` equal to the validating Windows host,
`result=PASS`, `scope=CATALOG_READER_DELTA_ONLY`, `tests=12`, `failures=0`, `errors=0`, `skipped=0`,
`launcherGuards=5` and `azureAuthorized=false`. Do not write the live-consumed
`catalog-diagnostic-validation.json` from this delta-only test sequence.

For a subsequent separately reviewed packet, its executable baseline (`$ExecutableHead`,
`$ValidatedHead` and eventual live marker `codeHead`) must be this new reader commit SHA,
never the historical `5867337...` or `56709de...` baseline. Its live marker `sourceHead` must be
the exact final reviewed packet commit, on the same `hostName`, with passing same-source packet
checks. Do not relabel the test-only marker as a live marker. Updating pins, consuming a marker
or launching remains separate, unapproved work. Passing Windows tests alone authorizes no Azure run.

## Historical preparation and validation

Prepared 2026-11-02 against reviewed packet `7a70ea76db26351632b647863a4d814182bc472b`.
Diagnostic code baseline: `5867337464e2caf0eabdf5c8694061ab43d3c8a7`.
Windows/Maven validation and the next fresh Azure attempt remain **PENDING**.
No Azure resource was created and no production connection was opened for this change.

The owner reports successful execution of the unchanged V2 installer under its 600-second
ceiling. Static comparison established all 97 owned names and all 19 component constructions
agree. `CATALOG_RECEIPT (SQL 0)` localizes failure to execution of the packaged catalog stage;
it does not prove that the stored receipt differs from the recomputed catalog hash.

## Implementation and result

- `shale-data/src/test/resources/ai/confinement-v2-catalog-components.sql`: the same owned-name
  declaration and 19 expressions in their original order. Each component is assigned to a
  local `nvarchar(max)` variable and only its ordinal/name, `DATALENGTH` and SHA-256 are selected.
  Local table-variable initialization writes no persistent database object or application row.
- `shale-data/src/test/java/com/shale/data/security/ai/AzureCatalogComponents.java`: bounded JDBC
  result draining, strict 19-result validation and length/hash comparison. NULL, missing, extra,
  duplicated or reordered evidence fails closed. Empty components remain valid hashed empty strings.
- `shale-data/src/test/java/com/shale/data/security/ai/AiCaseDataAzureSqlCatalogComponentTest.java`:
  separate `SHALE_AZURE_CATALOG_COMPONENT_OPT_IN=AZURE_SQL_DATABASE_CATALOG_COMPONENTS` gate.
  Reuses `AiCaseDataAzureSqlDisposableSetupTest.ownerSource` / `ownerConnect` and
  `AiCaseDataAzureSqlDatabaseReadOnlyTest.source("VERIFIER")`, without alternate credentials or
  connection configuration. Requires the existing disposable/independent-credential checks,
  matching live Azure logical-server/database targets, dbo owner, configured verifier, complete
  VIEW DEFINITION and absence of the mutating setup opt-in. SQL/close/configuration failures
  are sanitized without cause/suppressed exception chains.
- `shale-data/src/test/java/com/shale/data/security/ai/AzureCatalogComponentsTest.java`:
  seven offline expression, metadata, difference, JDBC-closure and error-sanitization tests.
- `build/azure-native/test_catalog_components.py`: five offline launcher safeguard tests.
- [Operator runbook](native-azure-readonly-operator-runbook.md): existing fresh-resource creation,
  owner probe and setup, followed by the diagnostic before owner-secret cleanup. The historical
  full native attestation section is not selected. Setup Java, SQL and timeouts remain untouched.

The only diagnostic artifact is private local `work/azure-native/catalog-components.tsv`.
It has a header and exactly 19 rows: ordinal, fixed name, owner/verifier byte lengths,
owner/verifier SHA-256 and equality. No raw catalog/module text, schema SQL, target names,
credentials, SIDs, tokens or application/production rows are emitted. No receipt comparison is
performed. A difference preserves this metadata and fails with the exact fixed ordinal/name list;
stop and clean up. Nineteen equal rows record a complete context match; clean up and investigate
`confinement-v2-catalog.sql` execution behavior in separate work. Equality does not close either
native gate, prove receipt equality, or authorize another attempt or production deployment.

## Minimum Windows validation and exact subsequent launcher

Use `C:\Eclipse\Workspace\shale-parent`, checkout this PR head cleanly, and execute the **first
PowerShell fence in runbook section 1** in a fresh private Windows session. It pins the code
baseline above, permits only the listed evidence documents to differ, verifies all 25 existing
pinned files/index and V1/V2 receipts, runs 11 checkout regressions and five new launcher guards,
parses all PowerShell fences and tests the exact existing credential block on the same host,
then runs the focused 45-test selector with JDK 21/Maven and the repository selector's affected
suite plus critical reactor. The private candidate is verified locally against its existing
digest; it is never attached or committed. Only a passing sequence writes the local same-host,
exact-source validation marker. No mutating V2/Phase1R local rehearsal needs repeating because
those inputs and paths are unchanged; prior validated evidence carries forward for that scope.

After validation succeeds, execute the **first PowerShell fence under
[Reviewed live launcher — mandatory](native-azure-readonly-operator-runbook.md#reviewed-live-launcher--mandatory)**
from the same private session and repository root. This is the updated exact launcher; do not
paste sections individually. It checks the same-source/same-host validation marker, clean tracked
state, actual pinned bytes/receipts, private input hash and the live-section digest, then consumes
the marker before creating one nonce/resource group/server/database. It refuses an existing scope
record. Existing runtime secret generation, validated TLS and exact IPv4 firewall remain in place.
Probe/setup/diagnostic failures all enter exact-scope deletion and confirmed-absence checks.
Any interrupted or failed attempt forbids fixture reuse; cleanup failure remains BLOCKED until
reconciled and deletion confirmed. No automatic retry and no production access/deployment.

Live sections 2, 3 and 5 SHA-256:
`fdb9e31752dec1f1da350c07f0c61c4fc85d8e3661e905c5f02a7c0c964b138a`.
Resource-creation and cleanup blocks, and the credential-generation block, remain exactly those
from the reviewed packet; the five launcher tests protect those bytes and diagnostic ordering.

## Review and verification evidence

Reviewed `architecture/codex-prompt-rules.md`, the tenancy/RLS contract, relevant AI schema/audit
sections of `architecture/database-schema.md`, `docs/architecture/phase3b8-confinement-v2.md`,
`docs/architecture/azure-sql-case-attestation.md`, the setup/DataSource/owner helper implementations,
the packaged verifier, installer receipt builders and operator runbook before editing.

Pre-edit test impact: existing Azure acceptance safety and DataSource/driver-timeout tests protect
target, independent identities, TLS, owner timeouts and sanitized failures. `AzureOwnerV2InstallTest`
protects the unchanged 600-second ceiling and setup allowance. Existing attestation, V1/V2 migration
and Phase1R contracts protect the resource/receipt/authority boundaries. Added tests address only the
new component collector and its launcher placement. No production symbol or resource was edited.

Local Linux verification:

- JDK 21 compilation of the new helper/live/offline tests and existing focused classes succeeded.
- Cached JUnit 5.10.3 runner: 45 executed, 45 passed, zero failures/errors/skips. This is supplemental
  verification, not the configured Maven/Surefire or Windows gate. After a final assertion-only
  sanitization adjustment, all seven changed diagnostic tests were recompiled and passed again.
- `python -m unittest discover -s build/sql-confinement -p test_checkout.py`: 11 passed.
- `python -m unittest discover -s build/azure-native -p test_catalog_components.py`: five passed.
- `python build/sql-confinement/checkout_bytes.py`: all 25 files/index and both source receipts PASS.
- `git diff --check`: PASS. Protected installer/catalog/manifest/timeout/setup Java inputs match
  the reviewed remote blobs exactly.
- Repository selector identifies cases/security-data (core/data/server). Focused Maven, affected
  suite and critical reactor cannot execute here: uncached Spring Boot dependency BOM 4.1.1 cannot
  be retrieved through this environment's unavailable network path. Windows PowerShell execution
  is also unavailable. Required Windows/Maven/PowerShell verification remains unexecuted; do not
  claim this packet validated or launch before section 1 passes on the owner host.

Audit compatibility: metadata and the three existing Unicode reference tables are the only reads.
There is no tenant/PHI read, business/admin mutation, new authority or audit schema. No application
audit row is appropriate for this diagnostic. Installer, catalog construction, module/source/catalog
receipts, grants, roles, hashes, credentials and authority boundaries are unchanged.
