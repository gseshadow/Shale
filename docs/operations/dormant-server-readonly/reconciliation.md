# Offline reconciliation of the localized production result

Reviewed packet: `codex/dormant-server-readonly-packet`,
`160a05c3a369a73cbeec24c7c41c186893989541`.
Candidate runtime source remains `600723b92fe34731e1d6d25e598fc9e3cbfc2770`,
with runtime code unchanged from `1f12091aef3275721c1e35d6ba00a2ecb80c5173`.
Only repository and documented semantics were reviewed. No production SQL, Azure inventory,
SQL mutation, grant change, migration, application execution or deployment was performed.
The reported production results are user-supplied observations; they were not reproduced.

## SESSION_RLS: confirmed verifier defect, conditional definition diagnosis

Microsoft documents `operation` as NULL for all applicable operations and lists NULL among
`operation_desc` values. FILTER is the predicate type, never an operation description:
[sys.security_predicates](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-security-predicates-transact-sql).
The corrected FILTER rule is exactly:

```sql
predicate_type_desc = N'FILTER'
AND operation IS NULL
AND operation_desc IS NULL
```

BLOCK still requires `predicate_type_desc = N'BLOCK'` and the exact `AFTER INSERT` or
`AFTER UPDATE` description for its slot. Aggregate, localized TYPE_OPERATION,
FILTER_OPERATION_NULL and UNEXPECTED_PREDICATES now agree. All six PRESENT results
were PASS: the aggregate six was six unmatched expected binding tuples, not six missing rows.

Microsoft describes `predicate_definition` as a normalized function invocation including
arguments, not the predicate function's body, and does not promise byte-identical formatting.
Balanced outer parentheses and redundant parentheses around a column argument are legitimate
SQL expression representations. Repository precedent explicitly accepts an outer wrapper in
`docs/sql/2026-09-23_field_confirmation_foundation_phase2a.sql` and
`docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql`; the Contact/Organization
verifiers also remove parentheses, spaces, tabs, CR and LF.

The corrected binding rule lowercases and removes identifier brackets, ASCII spaces, tabs,
CR and LF, then compares the entire expression to exactly this finite grammar:

```text
"(" * outer + "sec.fn_filterbytenant(" + "(" * argument
+ "shaleclientid" + ")" * argument + ")" + ")" * outer
outer, argument independently range from 0 through 8 inclusive.
```

This is 81 exact balanced representations of the same call. Both SQL checks generate the
same set. No substring match, function-body normalization, hash adoption, other schema,
overlay function, column, extra argument or expression is accepted. Parentheses are not
indiscriminately erased: that would also accept a different function named
`sec.fn_FilterByTenantShaleClientId()`. A representation beyond the documented eight-wrapper
bound fails closed and requires an offline contract review, not a production rewrite.
An unavailable binding expression is UNKNOWN. Policy identity/owner, enabled/schema-bound
state, exact six-count and duplicate checks remain independent.

The FILTER failure is conclusively a packet defect. The six FUNCTION_DEFINITION failures are
compatible with a common parenthesized representation, but the supplied booleans/counts
cannot establish their exact cause. They are not relabelled PASS. No raw production binding
or function definition was retrieved or published. If their sole difference is one of these
representations, the corrected packet accepts them without any production change.
The binding check does not resolve STRICT_PREDICATE_REVIEW or prove function-body semantics.

## RUNTIME_NONBYPASS: repository-derived ordinary allowlist is empty

The existing dynamic scope includes every non-system SQL module with non-NULL execution
context, unavailable definition, or cryptographic properties, excluding the two Stage A
procedures. Membership in that scope is not itself evidence that a module is unsafe.
No reviewed migration adds a signed-module exception for ordinary runtime.

The following are repository identities, not discovered production object names:

| Owner-executed module | Source migration | Reviewed direct EXECUTE contract |
| --- | --- | --- |
| dbo.LookupAuthenticationCredentialV1 | 2026-10-14_pretenant_authentication_modules.sql; auth/permission-cutover/install.sql | shale_app GRANT; shale_runtime and shale_runtime_role DENY; excluded from this category and checked by Stage A fences |
| dbo.ResolveDesktopRememberCredentialV2 | Same Stage A sources | Same Stage A contract |
| dbo.ResolveAiClientV1 | ai-integration/phase3b1.sql | AiIntegrationBootstrap only |
| dbo.ResolveAiCredentialV1 | ai-integration/phase3b1.sql | AiIntegrationBootstrap only |
| dbo.ResolveAiOAuthCodeV1 | ai-integration/phase3b2c1.sql | AiIntegrationBootstrap only |
| dbo.ResolveAiRefreshV1 | ai-integration/phase3b2c2.sql | AiIntegrationBootstrap only |
| dbo.ResolveAiAccessV1 | ai-integration/phase3b2d.sql | AiIntegrationBootstrap only |
| dbo.ResolveAiRevocationV1 | ai-integration/revocation.sql | AiIntegrationBootstrap only |
| dbo.AiDataLocateAccessOwnerV1 | ai-integration/phase3b7-confinement.sql; retained by V2 | Private static ownership-chain helper; no application/public direct grant |

`AiIntegrationBootstrap` is a distinct role, not an alias for `shale_runtime_role`.
`AiIntegrationRuntime` grants on other caller-executed AI procedures are also a different
contract. The synthetic `AiSecurityLocalSqlServerTest` provisions distinct bootstrap/runtime
identities and explicitly rejects resolver execution as its integration runtime. This source
was inspected, not executed. The Phase 3B7 architecture forbids direct application/public
EXECUTE on the private helper; V2 retains that contract. Ordinary Java runtime uses the
ordinary datasource; none of these definitions/grants requires elevating that datasource.
`OAuthCodeBootstrapConfiguration` independently configures the resolver datasource from
`shale.ai.oauth.bootstrap.jdbc-url`, `.username` and `.password`; it reuses neither ordinary
pool. `McpOAuthSecurityConfiguration` injects that qualified bootstrap provider separately
from `RuntimeConnectionProvider`. The AI migration README also explicitly leaves identity
membership to separate provisioning and does not install ordinary/auth-pool membership.
The retained older `ResolveDesktopRememberCredential` is caller-executed and is not an
approved elevated exception.

For `shale_runtime` / `shale_runtime_role`, the reviewed elevated EXECUTE allowlist is **[]**,
recorded explicitly in `contract.json`. The existing cutover preflight likewise rejects runtime
EXECUTE across its elevated snapshot. No nonempty ordinary allowlist can be derived from
these sources, so the packet continues to require zero effective elevated EXECUTEs. Any future
exception needs exact reviewed module identity/body/context/owner and expected authority,
required-module existence/visibility checks, and rejection of every additional authority;
neither a count nor signing/owner execution alone is an allowlist.

The six reported effective authorities therefore **cannot be accepted by this reviewed empty
allowlist**. Their count does not identify them as the six AI resolvers. Their names, bodies,
grant origins and separate historical approvals are unknown to this review. This is an
unresolved contract discrepancy, not proof of six exploitable defects and not authorization
to revoke anything. No production identity was guessed or published, and no grant was changed.
If an independent existing approval later establishes an ordinary application contract,
review that exact source/evidence before introducing any exception.

Missing module-definition visibility now adds UNKNOWN to the aggregate/category rather than
allowing a zero-permission result to imply a complete module review. NULL permission results
remain UNKNOWN. Known category violations retain FAIL precedence over missing visibility.
All existing no-db_owner/ShaleAdmins, database/schema/security-policy administration,
impersonation, ownership/fixed-role and broad protected-object ALTER/CONTROL checks remain.
The Stage A execution exclusions remain separately fenced, not permitted runtime grants.

## Offline changes and verification scope

Pre-edit impact inventory: `test_packet.py` contains all packet symbol/fact references,
including the obsolete byte-preservation test and tests intentionally retaining the bad
FILTER expectation. Related Stage A/AI migration contracts and cutover fixture sources were
inspected. No runtime implementation, migration or historical verifier is changed.

Changed packet files: catalog.sql, contract.json, test_packet.py, README.md and this review.
collect.ps1, transport grammar, source SHA, localized fact inventory, Azure/decision modes
and all three review-only UNKNOWNs remain unchanged:
STRICT_PREDICATE_REVIEW, SESSION_CATALOG_REVIEW, ORDINARY_API_REVIEW.

The offline suite executes the actual aggregate/localized relational expressions against
synthetic SQLite in-memory catalog rows, adapting only SQL dialect syntax/functions. It covers
81 balanced representations, whitespace, wrong schema/function/column/arguments/expression,
malformed/unbalanced and over-bound forms, FILTER NULL operations, missing/duplicate bindings,
missing definition visibility, all fixed output names, six synthetic elevated EXECUTEs,
NULL permission results, and transport/output rejection. Unchanged catalog/auth sections
are pinned independently; the obsolete whole-packet byte pin was intentionally superseded.
Source assertions verify the six resolver grants target the bootstrap role and that the
private helper has no direct application grant.

Run only offline packet/source checks for this task. No SQL Server, Azure SQL, PowerShell
collector mode, ODBC, application smoke, migration fixture or deployment validation is claimed.
No Maven integration/local SQL fixture is run. Catalog reads introduce no application audit
event or schema change; existing application audit requirements are unaffected.

Offline verification commands:

```text
python docs/operations/dormant-server-readonly/test_packet.py
  PASS: 21 tests (including all 81 representation combinations in aggregate and local checks).
python build/test-selection/select_tests.py --base 160a05c3a369a73cbeec24c7c41c186893989541 --head HEAD --format json
  No affected Maven area/module or full-suite escalation; packet tests are run explicitly.
git diff --check
  PASS.
```

The selector's generic `mvn test` recommendation is not executed: this task explicitly permits
offline packet/source tests only. Native SQL Server/Azure behavior remains unexecuted and unverified.
