# Universal onboarding PR 1 verification

Baseline: `c4800cb515e35edd79e6b55f1f61536bd17b3ebf` (`codex/latest`, merged #1944).
Design: [universal MCP tenant onboarding](../architecture/universal-mcp-tenant-onboarding.md).

## Test impact established before implementation

Reviewed `AGENTS.md`, `architecture/codex-prompt-rules.md`, system overview, tenancy/RLS,
AI/MCP identity/delegation/OAuth/quota/transport architecture and the current Phase 4 runbook and
verification record. Production readiness/preflight/binding records were inspected for preserved
constraints. No UI or SQL implementation is changed.

Pre-edit searches and source inspection covered the full client binding: `TenantBoundOAuthClientRepository`,
`DurableOAuthAuthorization`, `OAuthAuthorizationRequestPolicy`, `OAuthHumanFilter`, `OAuthBrowserFilter`,
`OAuthMetadataContract`, `OAuthCodeExchange`, `OAuthRevocation`, `AiOAuthTransactionStore`,
`AiOAuthExchangeStore`, `AiOAuthCredentialStore`, `AiIntegrationSecurityService` and the installed-shape
registration/procedure definitions in the versioned SQL sources. Related tests inspected:
`OAuthClientMappingTest`, `OAuthMetadataTest`, `OAuthCodeExchangePolicyTest`,
`OAuthRevocationPolicyTest`, browser/filter/configuration and durable OAuth lifecycle tests/records.

New production symbol: `CimdClientIdentifier` only. New test: `CimdClientIdentifierTest`.
No existing constructor, field, request contract, client lookup, SQL, UI or active validator changes.
Existing tests have no superseded expectations; manual-client and disabled-registration assertions
must remain. The new test checks exact identity, bounds, unsupported/unsafe syntax, redacted errors,
and explicitly distinguishes syntax from future network destination safety.

| Changed file | Verification |
| --- | --- |
| `CimdClientIdentifier.java` | Four focused new tests; compile; reference search proving no runtime caller. |
| `CimdClientIdentifierTest.java` | Run directly with existing manual/metadata/exchange/revocation/browser policies. |
| Design and two owning-document pointers | Relative-link/fence checks, source/standards review and diff review. |
| This record | Exact result reporting; no production or end-to-end claims. |

## Commands and results

The workspace Maven launcher selects JDK 21/Maven 3.9.11 and the managed CA/proxy/cache.
No disposable SQL opt-in or production credential is configured. Opt-in lifecycle fixtures therefore
provide no SQL acceptance in these runs, even if their containing class reports no JUnit skips.

```bash
/workspace/.tools/bin/mvn -B -pl shale-server -am \
  -Dtest=CimdClientIdentifierTest,OAuthClientMappingTest,OAuthMetadataTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,OAuthBrowserFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base c4800cb5 --head HEAD --output work/onboarding-selection.json
PATH=/workspace/.tools/bin:$PATH python build/test-selection/run_selection.py work/onboarding-selection.json --command affected
/workspace/.tools/bin/mvn -B test
git diff --check c4800cb5 HEAD
```

Focused result: **23 PASS**, zero failures/errors/skips, including four new identifier tests.
Affected result: **56 PASS**, zero failures/errors/skips. The selector includes the new test and
existing OAuth/client/assertion/browser/OFF-composition and ordinary login regressions; server is
the only affected area, with no full-suite escalation. Opt-in SQL lifecycle scenarios did not execute.
Critical `mvn -B test`: **242 PASS**, zero failures/errors/skips across all seven reactor modules.
Counts overlap and are not additive unique-test totals. Maven 3.9.11 / Temurin Java 21.0.12.1.

Static checks: changed-document code fences and new-document relative links PASS; `git diff --check`
PASS; changed-file/test review and reference search PASS. `CimdClientIdentifier` is referenced only
by its own test; the active manual client-ID checks and registration metadata assertions are unchanged.
Independent changed-path checks confirm the complete `docs/sql` and `shale-data/src/main/resources/ai`
trees are unchanged, including immutable binding inputs. No workflow, dependency or selector edits.
The historical all-tests profile, web/browser campaigns and SQL opt-ins were not required for this
isolated non-UI/non-SQL foundation and were not run.

No SQL, registry, CIMD fetch or DCR behavior was exercised or implemented. No actual Hermes/Claude/
ChatGPT session was run. The plan's combined browser/client/SQL URL-only scenario is specified,
not executed. Audit integration uses no new schema/event and changes no existing audit transaction.
No production configuration/SQL/credential, deployment binding, activation, deployment or merge.
