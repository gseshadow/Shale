# Confinement V2 acceptance — 2026-10-28

**BLOCKED.** The successor is implemented for review; deployment preparation readiness is not established.
Required production-profile ordinary-authentication smoke times out on POST /api/auth/login with AI
composition OFF, before enabling V2 readiness. McpProductionReadinessLocalSqlServerTest fails in
ProductionOrdinarySmoke.check/call. No timeout, authentication check or assertion was weakened.

## Version and security scope

Base a06a05cf7a9c0b758fc7a0478c5541c9dfdb1489; implementation 1eee0106.
[Design and implementation map](../architecture/phase3b8-confinement-v2.md).
AiCaseDataV2 has independent catalog/source receipts, installer, standalone and packaged verifier.
Production selects shale.ai.data.contract-version=V2 explicitly. No fallback follows V2 failure.
V1 installers, verifier, manifest and historical hashes remain byte-identical. Its original test
assertions run with default V1; V2 inherits those assertions with explicit version/fixture hooks.

The six RPCs and all 13 confined module bodies remain identical to V1. V2 installs their exact
UTF-16LE bytes from hex, preserving module identities even with CRLF installer text. Case columns
use active logical positions; six prerequisite modules accept only two individually reviewed exact
hash variants each. Metadata, module settings, parameters, dependency and policy identity/ownership
checks remain independent. Literals and module text are never broadly normalized.

## Evidence and hashes

Private candidate SHA-256 4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f
and receipt are verified. Eight exact production Base64 components verify their hashes/counts.
Production-style SQL reconstruction reproduces EB39DDBFA59DF338DCAA969E440C7D254B1CB27ACE1211524375D786B2006AB1
before Phase1R and D4AA4B9D029044A69BA0FE10A171DD6B5447374EC3124CBFB6EA6E743E49F0CC after
exactly its three filters. V1 still refuses that representation. Actual historical Phase1R
preflight/apply/postcheck writes its receipt, then V2 prerequisite/install/standalone/cumulative passes.

- V2 Case/RLS catalog: 6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a.
- V2 source receipt: 60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891.
- Installer file SHA-256: 7f8c10d6bb41660fe59505a3386b74216eaa83c2d63726a657bb7ffeb1336ad8.
- Packaged server JAR: 4159a660406e0d841ae6f37a90319ecb9b0df2166823ff54b526ce86e339c14f.

Installed V2 catalog receipts additionally include destination IDs and grants: they are local
fingerprints, not portable production review values. No attachment or complete private schema/module
body is committed. Hash-only reviewed variants and synthetic fixture transformations are committed.

## Verification

Full per-group counts, failed attempts and final results are in
[the verification JSON](phase3b8-confinement-v2-verification.json). Counts overlap and are not additive.
The local runs use the hash-gated private candidate, loopback SQL Server 2022, nonce-owned databases
and distinct SQL logins. The real external-table fault uses separately owned matching PolyBase.
The initial PolyBase GENERIC-source refusal was resolved by restarting its local server after enabling
PolyBase; both V1 and V2 actual external-table refusal tests then pass.

Focused V2/V1/Phase1R/composition: 28 executed, zero failures/errors/skips. Authoritative Case/RLS:
1 executed, zero failures/errors/skips. Ordinary V1 and V2 data suites: 27 each, zero skips/failures.
Real external table: 1 each, zero skips/failures. Python selector: 27; Phase1R: 3; Case launcher: 4;
V2 contracts/private projection: 4. Package build passes. V2 HTTP authority-fault matrix, TLS/proxy, response boundaries and both independent clients pass.
The online critical reactor passes 228 tests with zero skips/failures/errors. Full per-group results are
reported individually in JSON; no failed or unexecuted check establishes readiness.

## Remaining gates and deployment impact

The ordinary HTTP authentication timeout must be diagnosed and all mandatory acceptance must pass
before this PR can receive the requested readiness classification. An uncached critical-reactor
artifact caused an initial offline dependency failure; its online retry is reported separately.
Native Azure contained identities/read-only live attestation and an authorized disposable Azure
mutation/fault suite remain OPEN. No native Azure fixture or acceptance credentials were provisioned.
Local SQL Server, real local TLS and routing tests cannot close those native gates.

The future sequence is documented in the [deployment preflight](../production-mcp-deployment-preflight.md).
The seven production prerequisites are already installed and MUST NOT be rerun. Phase1R and V2 are
future separately governed changes. No production connection/change, identity provisioning, server
JAR deployment, AI/MCP/OAuth activation or real integration/grant/session occurred. Do not merge or deploy
this blocked candidate. Audit transactions and vocabulary remain unchanged.
