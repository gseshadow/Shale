# Azure SQL Database attestation acceptance

Date: 2026-10-27. Base: `efd8be8a350523344bf69b72bbc715f9e888589d` (`codex/latest`, #1924).
Branch: `codex/azure-sql-attestation`.

**BLOCKED.** Implementation and structural acceptance do not yet prove all required Azure SQL
security properties safely. The private authoritative source attachment is absent from this
workspace, and no Azure acceptance credentials/fixture are configured. No historical #1922/#1924
SQL/client result is presented as a fresh pass. Do not deploy or activate this candidate.

The [reviewed design candidate](../architecture/azure-sql-case-attestation.md) enumerates every
current database/server check, reason, Azure replacement, preserved property, residual risk and
required evidence. This change does not contact production or change SQL objects, production
identities, Azure configuration, deployed JARs or capabilities. **No SQL migration required.**

## Test-impact review before implementation

Read the repository bootstrap/rules, confinement architecture, implementation and local closeout,
production composition evidence, preflight, SQL migration/verifier, current attestation,
`McpAiCaseData`, `McpCaseReadiness`, datasource/config and the related database/tenancy contracts.
Repository-wide searches covered attestation callers, constructor shapes, server catalog fragments,
tokens, receipts, public grants, datasource properties and readiness assumptions before edits.

| Changed contract | Inspected/maintained test owners |
| --- | --- |
| Positive engine routing, contained model, visibility/unknown/error refusal | New `AiCaseDataAttestationTest` (JDBC doubles, explicitly not Azure semantics) |
| Public authority, role/direct grant/ownership and external catalog defense | `AiCaseDataLocalSqlServerTest`, expanded actual SQL fault cases; authoritative input still mandatory |
| Immutable SQL/module/catalog/source receipts | `AiCaseDataMigrationContractTest`, unchanged expectations |
| Production `not-ready` and every protected request withholding on public drift | `McpProductionReadinessLocalSqlServerTest`, `McpCatalogVerifierServingChecks`, expanded fault matrix |
| Per-request attestation, pool separation, proof/handlers/OAuth | Existing datasource/access/proof/composition/search/Overview/filter/OAuth tests retained |
| Live target-database Azure semantics | New explicit `AiCaseDataAzureSqlDatabaseReadOnlyTest`; never runs without opt-in |
| Stage 0 dispatch/no persistent mutation/no secret surfaces | New focused script contract assertion, exact Azure batch embedded in dispatcher |
| Affected-area selection | Add focused attestation class to security-data blocking inventory; selector Python retained |

## Evidence and limits

Local SQL Server cannot emulate Azure server metadata availability, contained-user authentication,
virtual-master visibility or logical-server role semantics by changing EngineEdition in a mock.
The focused routing test proves that unsupported server catalogs are never called in the Azure
branch and that incomplete metadata fails; it intentionally stops at a refused catalog receipt.
It is not a full successful attestation or actual Azure permission proof.

The shared structural helper test uses the separately hash-gated SQL Server fixture to reject an
external BLOB source and an unresolved cross-database synonym without contacting either target.
That test is present but **NOT RUN** here. It cannot establish Azure external-table visibility.

Required actual-login attacks remain direct Case/join/credential/control reads; mutable/read-only
SESSION_CONTEXT poison; direct PHI INSERT; all 42 legacy/private EXECUTEs; direct/schema/database/
column/view/function/public grants and grant option; verifier public visibility and direct grants;
ownership; impersonation; DDL; GRANT/CONTROL; and external/cross-database routes. New disposable tests
add nine public application permission shapes, grant option, direct escalation grants for both
identities, external-source and synonym faults. The production readiness/serving helpers inject
eight public faults and require fresh `not-ready`/401 with no structured data, then recovery.
These are **unexecuted attack expectations**, not results. Existing final-handoff code is unchanged.

No production permission injection is authorized or needed for read-only platform acceptance.
Azure privilege-fault tests require a separately owned disposable Azure database, synthetic data,
independent users and the exact authoritative fixture. Never enable local owner/fault harnesses on
production. This candidate does not claim such an Azure mutation fixture was provisioned.

The fresh commands/results are recorded below and in the [verification JSON](azure-sql-case-attestation-verification.json). Counts overlap and are not additive. The tested source is `09f2593f42958c130e21cb224d7ef97a708271bc`; subsequent receipt-only edits do not change that implementation. Maven uses task-owned Maven 3.9.11/JDK 21, an
explicit workspace-local repository/proxy settings file and the system CA truststore. TLS remains
validated. The normal command prefix below is `mvn -B`; actual launcher/setup paths are private
scratch, not deployment dependencies. Skips/zero-test SQL classes are never counted as acceptance.

| Command / group | Fresh result |
| --- | --- |
| `mvn -B -pl shale-server -am -DskipTests package` | PASS; includes Boot executable JAR compilation/repackage, no deployment |
| `mvn -B -pl shale-data -am -Dtest=AiCaseDataAttestationTest,AiCaseDataMigrationContractTest,AiCaseDataAccessTest,ProofDigestTest,DataSourcesAiCaseDataTest -Dsurefire.failIfNoSpecifiedTests=false test` | 16 PASS, 0 failures/errors/skips |
| Focused server command: same data tests plus `McpCaseCompositionTest,McpSearchTest,McpOverviewTest,McpFilterIsolationTest,OAuthAuthorizationFlowTest,OAuthConfigurationTest` with `-pl shale-server -am` | 43 PASS, 0 failures/errors/skips across data/server; opt-in OAuth SQL methods produce no live proof |
| Selector affected command | 137 PASS, 2 explicit live-gate skips, 0 failures/errors (139 run) |
| `mvn -B test` critical reactor | 226 PASS, 0 failures/errors/skips; all seven modules SUCCESS |
| `python3 -m unittest discover -s build/test-selection -p 'test_*.py'` | 27 PASS after final selector inventory/source changes |
| Authoritative Case/security, expanded live inherited faults, production Boot/SQL, final-handoff expiry, TypeScript 66/66, Python 66/66, TLS/proxy, durable SQL OAuth lifecycle | **NOT RUN**; missing original private schema input; no replacement/synthetic Case schema invented |
| Azure SQL live read-only platform gate / production Stage 0 | **NOT RUN**; no configured authorized target connections |

Resolved setup/failed attempts are not passing evidence: initial shell fetch needed the permitted
network execution path; Maven needed workspace-local cache/proxy configuration, a full JDK rather
than the installed JRE, and the system CA store. The first static Stage 0 privacy assertion also
incorrectly matched a comment saying no locations are emitted; the complete test was reviewed and
changed to inspect executable SQL. The corrected test passes in the final focused/affected runs.

The selector recommends informational `mvn -Pall-tests test` solely because its inventory changed.
That optional historical suite was not run; it cannot replace the focused/affected/critical gates.
Changed-file-to-test review and a second search confirmed server surfaces remain behind the explicit
SQL Server/MI branch, all private identity constructor callers compile, and source/catalog/module
receipts and final-handoff code remain unchanged. Git diff checks pass.

## Exact read-only Stage 0 recheck, before provisioning

From the repository root, using an **existing separately authorized operator identity** with
CONTROL DATABASE visibility and approved secure password handling:

```bash
sqlcmd -S "$SQL_SERVER" -d "$SQL_DATABASE" -U "$SQL_INVENTORY_USER" -N -b -l 5 -t 15 -i docs/sql/ai-integration/stage0-platform-verify.sql
```

The dispatcher executes the Azure verifier when EngineEdition=5, the reviewed server-catalog
branch for 8/2/3/4, and STOP for everything else. The Azure standalone equivalent is the same
command with `-i docs/sql/ai-integration/azure-sql-stage0-verify.sql`. No `-C`, PHI query, secret dump,
DDL, provisioning or grant is included. Missing visibility must STOP rather than trigger a grant.

On Azure require EngineEdition 5, Platform AZURE_SQL_DATABASE, seven zero FindingCounts and
`PLATFORM_COMPATIBLE_PENDING_IDENTITY_AND_CATALOG_ATTESTATION`. It returns one bounded row, followed
by a failing SQL error for dangerous state. The existing general Stage 0 inventory/receipts and
all independent later migration/provisioning/configuration gates still apply. This platform result
alone does not prove any not-yet-provisioned principal or authorize readiness.

## Later live read-only Azure gate, after governed provisioning

No production changes are part of this task. Before future production readiness, first execute this
gate on a disposable **actual Azure SQL Database** with the authoritative reviewed catalog and
separately provisioned contained identities, then on the separately authorized installed deployment.
Do not run the mutation harnesses against production.

Set `SHALE_AZURE_ATTESTATION_OPT_IN=AZURE_SQL_DATABASE_READ_ONLY`,
`SHALE_AZURE_JDBC_URL` (SQL authentication, explicit databaseName, no TLS overrides),
`SHALE_AZURE_DATABASE`, `SHALE_AZURE_ISSUER`, `SHALE_AZURE_RESOURCE`, and independent
`SHALE_AZURE_{AI,VERIFIER,AUTH,RUNTIME,BOOTSTRAP}_{USERNAME,PASSWORD}` through approved secret
handling. Use the actual JVM PHI time zone, matching immutable SQL deployment binding. Run:

```bash
mvn -B -pl shale-data -am -Dtest=AiCaseDataAzureSqlDatabaseReadOnlyTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Require one executed test, zero skips/failures/errors. It positively detects edition 5 then performs
the complete live production attestation, including fresh source/catalog/module/RLS/binding/identity
checks, without protected procedure execution or application data reads. Its absence is not a pass.
Run Stage 0 again after any governed credential/catalog change; operator credential/secret-consumer
proof remains distinct from runtime database permission closure. Retain only bounded outcomes and
reviewed source/head identity, never JDBC binds, credential identities or catalog dumps.

Acceptance must also measure remote attestation within the unchanged ten-second protected request
budget. If Azure visibility, catalog receipt compatibility or timing differs, STOP and review;
do not bypass a check, widen a role or extend proof/deadline behavior to obtain readiness.

## Remaining completion gates

Recover original `shale-schema-source.sql` (728862 bytes,
SHA-256 `4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`) and use the existing
`build/sql-confinement/prepare-authoritative-fixture.py` to reproduce the exact reviewed candidate.
Then rerun all mandatory authoritative security/production/client/expiry/TLS/OAuth groups using
owned local SQL, synthetic rows and independently validated TLS as #1924 documents. Run the
structural external-route/public faults; reproduce applicable attacks in an owned disposable Azure
fixture before claiming Azure authoritative security acceptance. Complete the read-only live Azure
gate and operator evidence. None may be fabricated from source assertions or historical receipts.

No production change was made. The candidate remains BLOCKED, not READY FOR AZURE SQL DATABASE
STAGE 0 RECHECK, while mandatory local and Azure security evidence is absent.
