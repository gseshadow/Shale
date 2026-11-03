# Read-only Azure catalog component diagnostic

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
