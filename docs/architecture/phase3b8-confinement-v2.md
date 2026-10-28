# Case confinement V2

Base: a06a05cf7a9c0b758fc7a0478c5541c9dfdb1489. Private evidence is never committed.

## Diagnosis and identity

The exact production catalog before Phase1R hashes to EB39DDBFA59DF338DCAA969E440C7D254B1CB27ACE1211524375D786B2006AB1.
Adding only its three filters projects D4AA4B9D029044A69BA0FE10A171DD6B5447374EC3124CBFB6EA6E743E49F0CC,
not historical V1's 2FD4544BC7454FF67E8411449F983C29A0D2CF13F92982EBCCC8D67BBBF97C1F.
34 Cases physical column IDs have gaps; 158 active columns and their relative order agree.
Three existing cascade triggers differ only by LF/CRLF. Three existing functions differ only
by reviewed declaration delimiters and boundary whitespace. No production object rewrite is justified.

V2 uses active column position (ROW_NUMBER ordered by physical ID per object), preserving exact
name/type/length/precision/scale/nullability/identity/collation/computed-definition checks and order.
Module text is NOT normalized. A checked-in hash-only allowlist maps each explicitly reviewed exact
UTF-16LE definition to one module identity. Unknown text, including changed literals, fails closed.
No parsing, whitespace stripping, identifier replacement or normalization inside literals occurs.
Ownership, execution context, settings, signatures and dependency identities are checked independently.
All eight catalog categories and exact Phase1R coverage remain required.

## Versioning and implementation map

V1 generation: build/sql-confinement/generate.py uses case-catalog.sql/case-catalog.sha256.
It embeds that prerequisite in phase3b7-confinement.sql and phase3b7-confinement-verify.sql,
packages confinement-catalog.sql, and writes confinement-modules.properties. The installer persists
Shale.AiCaseDataV1 and Shale.AiCaseDataV1.Source on AiCaseDataInvocation. AiCaseDataAttestation
executes the packaged verifier and independently checks the source and all 13 module hashes.
verify.sql dispatches the installed confinement verifier before its earlier predecessor checks.

V2 has its own generator, prerequisite, installer, standalone/package verifier, manifest and
Shale.AiCaseDataV2 / Shale.AiCaseDataV2.Source receipts. It creates the same objects and six public
procedures; procedure V1 suffixes continue to denote the unchanged RPC/data contract.
Existing V1 is not upgraded or relabelled. Uninstalled/failed V1 can install V2 after prerequisites;
an existing V1 installation must be handled by a separate explicitly reviewed upgrade, never adopted.
Historical installer, verifier, expected digest and module manifest remain byte-identical.
Production explicitly selects V2; V1 compatibility remains explicit for historical local tests.
No V2 failure falls back to V1. Runtime permission/identity/Azure/route checks remain common.

## Audit and scope

No new PHI read, business mutation, grant provisioning or event vocabulary is introduced.
The installed procedures retain existing atomic PHI/integration audits, proof binding, quotas,
expiry and final handoff. Catalog reads need no application audit row. Migration receipts are
metadata, not application audit history. All capability defaults remain OFF.

## Pre-edit test-impact inventory

- V1 generation/receipts: AiCaseDataMigrationContractTest and AiCaseDataLocalSqlServerTest remain V1.
- V2 serialization/generation: new Python projection/variant and Java migration/local SQL tests.
- AiCaseDataAttestation constructors/resources: existing attestation, Azure and distinct-login tests.
- Production composition: McpCaseCompositionTest, McpProductionReadinessLocalSqlServerTest,
  McpCaseCompositionTest and serving authority/expiry/client acceptance.
- Phase1R prerequisite: existing Phase1R tests and authoritative Case/RLS fixture.
- Catalog mutations: actual SQL column/type/order/module/settings/owner/dependency/RLS attacks;
  unknown literal contents must never become an accepted representation variant.

Native Azure contained-principal and disposable Azure mutation/fault acceptance remain separate
deployment gates. Local SQL and routing tests cannot close them. No production access is authorized.


## V2 receipt identities and review limits

The versioned Case/RLS prerequisite SHA-256 is
`6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a`.
Its generated source receipt is recorded separately in the packaged V2 manifest. The installed
Shale.AiCaseDataV2 catalog receipt additionally pins destination object/principal IDs, grants,
all cumulative module bodies, dependencies and Unicode reference data. It is destination-specific;
never copy a disposable catalog receipt into production. Same reviewed representations can install
on different databases; subsequent unexpected destination catalog changes still remove readiness.
Module-name/owner/execution-context/settings/dependency checks are not waived by a definition variant.
The RLS tuple additionally includes policy schema, name and effective owner, so relocating a filter
to a different enabled policy cannot impersonate sec.TenantFilter.

Production deployment uses explicit shale.ai.data.contract-version=V2. Historical/local V1 can be
selected explicitly; neither Java nor the cumulative verifier catches V2 failure to try V1.
The live Azure read-only test requires SHALE_AZURE_CONTRACT_VERSION=V2 in addition to the existing
independent identity settings. No native Azure execution or disposable Azure mutation/fault result
is inferred from local EngineEdition routing or SQL Server fixtures.
