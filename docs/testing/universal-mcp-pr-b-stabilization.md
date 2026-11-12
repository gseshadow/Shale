# Bounded PR B stabilization review

Review baseline: `f7707887f06ab87cd55ed8eff21ee3b4a2128796` on the existing PR B branch.
Scope is deployment egress, one small Case concurrency workload, and combined PR A/B
security regressions. No production deployment, activation, SQL migration, pool manager,
DCR or confinement redesign is authorized or performed.

## Security contract and test impact before the correction

`AiCimdCaseData.ready` runs full predecessor and additive verification at startup and
for each CIMD admission. Each request owns its deadline and proof; SQL admission/read/
handoff recheck tenant, revision, approval, scopes and revocation. A failed verification
permanently withdraws the composition until reconstruction. These contracts remain.
The instance monitor currently covers the entire JDBC verification and also protects
`reader` and `close` state. It cannot serialize external SQL changes and supplies no SQL
transaction/isolation boundary; the documented trusted-operator drain rule remains.

The bounded correction narrows that monitor to closed-state reads/writes and a final
closed-state check after verification. Every request still runs its own unchanged full
checks in the same admission location; no result sharing, cache, retry, timeout increase,
new pool or SQL permission/module change is introduced. A concurrent failure/close must
prevent another verifier from succeeding. Reader state checks keep their short monitor.

Pre-edit impact search: all references to `AiCimdCaseData`, its constructor, `ready`,
`reader` and `close`; existing `AiCimdAuthorizationLocalSqlServerTest` drift/withdrawal
and post-read revocation cases; `McpCaseCompositionTest`, `McpTransportTest`, default-OFF
Boot tests. Add focused real-SQL concurrency/state regressions through the existing
trusted predecessor factory seam; reuse the existing broader security evidence.

## Baseline measurement

One existing authoritative, hash-gated nonce SQL fixture; verified JDBC TLS; unchanged
Boot/MCP composition on loopback. Issuance uses the existing SQL lifecycle fixture.
No browser/metadata simulation or concurrent DDL workload is involved in this measurement.
One manual and one CIMD Overview baseline, one batch of four simultaneous CIMD Overviews
(below the existing five-request ceiling), then one probe. Same original deadlines.

Baseline: manual 2,270 ms; CIMD 4,938 ms. All four concurrent calls timed out at about
10,010 ms; the later probe returned 401 because verification had withdrawn the instance.
JVM monitor sampling observed 970 blocked samples at 20 ms (about 19.4 cumulative
thread-seconds) on the CIMD instance. Historical catalog batches were 0.53–1.46 seconds;
additive catalog batches 0.81–0.86 seconds. The long instance lock serializes these
checks and can block an already-verified request's reader behind another verification.
This explains a timeout under a small request workload without invoking hypothetical
network/SQL infrastructure failures. Exact timings are observations, not an SLA.

## Deployment egress boundary

The attached managed environment has no Azure outbound identity, Azure deployment
configuration or runtime network access. Its enforced network routes ordinary HTTPS
through a governed proxy; the production fetcher intentionally uses direct pinned TCP.
One unchanged-default fetch attempt failed closed at about 5.2 seconds including JVM/TLS
initialization. This is not evidence that the Azure route fails or succeeds. Existing
DNS/TLS/SSRF/deadline tests and prior live vendor metadata evidence are reused. Do not
promote the test-only DNS/CONNECT seam to a production fallback.

Azure acceptance remains pending. The precise operator check is recorded below after
review of the existing deployment runbooks; no additional simulation framework is needed.

## Precise Azure egress acceptance check (not executed here)

An authorized operator must run this in the intended Azure nonproduction slot/worker,
with its actual DNS servers, VNet integration, UDR/NSG/firewall/NAT route, Java runtime
and trust store. Record app/slot, artifact SHA and network configuration revision. A
separate VM, this Cloud workspace, curl success, or the PR B test connector does not
establish acceptance of that route. Do not deploy a new site, change routing, touch SQL,
start the Shale application with CIMD enabled, or enable production CIMD for this check.

1. Confirm the intended route supports **direct TCP 443 to a validated public numeric
   destination**, with TLS SNI/hostname verification against the original public metadata
   hostname. `CimdHttps` deliberately ignores ambient HTTP proxy settings. If the route
   requires a proxy-only connection, this implementation has a deployment compatibility
   blocker; do not silently activate the test connector, proxy-resolved DNS, private-IP
   exception, trust-all TLS, or alternate metadata document.
2. Use the exact PR B application runtime classpath, standard `CimdMetadataService`
   constructor, and the same JVM trust configuration as the slot. A small standalone
   diagnostic process is sufficient; it creates no OAuth credentials or SQL connections.
   Request the stock client's exact metadata URL
   `https://nousresearch.github.io/hermes-agent/docs/oauth/client-metadata.json` from a
   fresh service/JVM so no cached positive result masks the route. The issuer is only
   the configured exact identity binding; it is not fetched. No Shale production URL
   needs to be contacted. For a chosen confidential client, also exercise its published
   JWKS through the same default service and combined five-second budget.
3. Record pass/fail, cold retrieval elapsed time (excluding JVM startup), TLS hostname
   verification and the accepted metadata identity/method/counts, never credentials,
   browser state/code URLs, full environment or request bodies. The actual resolver must
   return its complete A/AAAA answer set; unchanged all-answer SSRF classification must
   pass before the selected numeric address is dialed. Retrieval, any allowed JWKS fetch
   and parsing must finish within the existing five-second budget; bytes/decompression,
   redirect rejection and cache-expiry rules remain unchanged.
4. Reuse `CimdRetrievalTest`/`CimdMetadataTest` for deterministic negative coverage (mixed
   public/private DNS, rebinding, TLS hostname/trust failure, redirects, slow-drip and
   body limits). Do not probe Azure metadata/private hosts merely to rerun those checks.
   Record actual network rejection or TLS error locally through approved diagnostics;
   keep sensitive diagnostics out of the PR. A blocked route remains a deployment
   acceptance blocker rather than justification to weaken an enforcement check.

No Azure check result or merge/deployment approval is inferred from this local review.

## Same workload after the narrow correction

| Observation | Before | After |
| --- | --- | --- |
| Manual-client Overview | 200 / 2,270 ms | 200 / 2,645 ms |
| Single CIMD Overview | 200 / 4,938 ms | 200 / 5,105 ms |
| Four simultaneous CIMD Overviews | four 504 / about 10,010 ms | four 200 / 7,587–8,557 ms |
| Following CIMD probe | 401 / instance withdrawn | 200 / 4,450 ms |
| Samples blocked on CIMD instance monitor | 970 | 0 |
| Withdrawn before shutdown | true | false |

Each successful response returned the expected synthetic authorized Case 100. Afterward,
26 predecessor catalog batches and six additive batches were observed: the manual call
still performs its original verification; each of six CIMD calls still performs both
existing predecessor passes and its own complete additive check. Nothing is coalesced
or skipped. Per-batch ranges remained 0.53–1.47 seconds historical and 0.81–1.13 seconds
additive. The correction removes JVM head-of-line blocking rather than changing SQL
algorithms, security checks, deadlines or resources.

The existing HTTP/admission deadline is **ten seconds for the whole request**, shared
with the tool invocation; it is not restarted after verification. SQL statement budgets
remain capped at five seconds and the caller-supplied eight-second library test deadlines
remain unchanged. Two concurrent HTTP observations exceed eight seconds end to end;
they fit the actual ten-second HTTP contract, not an eight-second latency claim. The
four-request result leaves limited headroom. Larger-load/Azure capacity acceptance is
not established and was not pursued as part of this bounded review.

Sanitized before/after HTTP outcomes and timing aggregates are in
[`evidence/universal-mcp-pr-b/stabilization`](evidence/universal-mcp-pr-b/stabilization).
Measurement uses the existing PR B fixture, with test-only JDBC timing wrappers and JVM
monitor sampling; it records role/method duration, not SQL text, proofs, parameters or
PHI. No concurrent DDL, refresh/revocation campaign or added pools are part of the batch.

## Combined authorization/HTTP regression review

| Boundary reviewed | Conclusion and reused evidence |
| --- | --- |
| Tenant ownership and RLS | Browser owner derives from a freshly validated original WEB session; token owner from exact credential/client SQL resolution. No caller-selected tenant is added. Existing two-tenant stock/HTTP evidence rejected foreign Cases and foreign administrator approvals. SQL namespace and procedures are unchanged. |
| Manual OAuth clients | Default validator overloads and manual providers remain separate; URL selection is gated on enabled CIMD. The before/after manual HTTP Case calls both succeeded. Existing manual registration/consent tests and the prior lifecycle/legacy-exclusion SQL test are reused. |
| Application/approval revisions and revocation | SQL still enforces current publication/approval versions at consent, exchange, refresh, admission, read and handoff. Existing actual HTTP rotation/revocation and six SQL security regressions are reused; the post-read revocation/other-tenant test is selected again for this correction. |
| HTTP/login/consent | Namespace servlet registration includes REQUEST/ASYNC; configured external issuer builds consent URI; browser origin, WEB bearer, S256 and explicit consent stay in place. No HTTP authorization/provider changes are made in stabilization. Existing stock/browser/HTTP evidence and targeted policy/Boot tests cover these seams. |
| Default-OFF / production | Conditional CIMD composition still refuses production/azure activation and requires complete explicit development controls/adapters. No flags, deployment bindings, runtime configuration or SQL candidates are changed. Existing default-OFF Boot/configuration tests are reused and checked in the relevant reactor. |
| Concurrent failure | Each readiness call checks withdrawal at entry and after its own full verification. Failure/close writes remain synchronized and permanent; the reader keeps a short synchronized state check. New deterministic real-SQL tests exercise independent entry and concurrent failure propagation. |

No authorization, tenant, revocation, manual-client or default-OFF regression was found
in this bounded source/evidence review. This is not a new security model or a formal
proof. Existing transactional audit owners, immutable receipts/bindings, trusted-operator
drain requirements and PHI handling remain unchanged. No new mutation or audit schema
is introduced; measurement reads do not create application audit events.

## Verification and disposition

Current focused checks passed: 60 Case access/read/attestation tests; 61 metadata retrieval,
TLS/SSRF, OAuth request/revocation/browser policy and transport/default-OFF Boot tests;
three explicitly selected real SQL tests, zero skips. The SQL selection was only:

- `AiCimdAuthorizationLocalSqlServerTest#concurrentAdmissionsRunIndependentVerificationWithoutHoldingReaderStateMonitor`
- `AiCimdAuthorizationLocalSqlServerTest#concurrentVerificationFailureWithdrawsAnAlreadyRunningVerifier`
- `AiCimdAuthorizationLocalSqlServerTest#revocationAfterReadWithholdsDisclosureAndPreservesOtherTenant`

The prior PR B stock-client/two-tenant OAuth/audit evidence, six SQL security scenarios,
265 affected checks, 225 critical checks and browser consent results are reused for the
unchanged surfaces. No full historical SQL campaign was repeated. Current selector and
critical reactor results follow in the final verification note.

Both measurement fixtures and the three SQL tests cleaned up their exact nonce databases
and logins with receipt/binding assertions intact. An owner-only final count found zero
remaining fixture databases/logins. The task-owned SQL container, TLS key/trust store and
credential files were removed; the authoritative private fixture hash is unchanged.
No production setting, deployment, SQL module/receipt, pool or existing PR state changed.

The monitor correction is warranted by a reproduced failure and a passing identical
small workload. Actual Azure egress acceptance remains **pending/blocking for deployment**;
no merge/deployment/production-activation approval is inferred. Larger-load capacity
remains unmeasured; the observed four-request result is not an SLA or a reason to extend
deadlines. Stop this review at these findings rather than expanding the architecture.

The Azure operator can use JShell on the exact release runtime classpath for step 2
(no Spring startup, SQL or feature activation):

```java
import com.shale.server.oauth.CimdClientIdentifier;
import com.shale.server.oauth.cimd.CimdMetadataService;
try (var service = new CimdMetadataService("https://api.myshale.com/oauth/mcp")) {
    long started = System.nanoTime();
    var snapshot = service.retrieve(new CimdClientIdentifier(
        "https://nousresearch.github.io/hermes-agent/docs/oauth/client-metadata.json"));
    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
    if (elapsedMs > 5000) throw new IllegalStateException("Metadata budget exceeded");
    System.out.println("PASS metadataMs=" + elapsedMs
        + " redirects=" + snapshot.redirects().size() + " methods=" + snapshot.methods());
}
```

Use the slot's unchanged trust configuration; do not set trust-all flags or substitute
resolver/connector implementations. An exception is a failed acceptance check. The
issuer string above is only a cache/identity binding; this code fetches the vendor
metadata URL, not the Shale production endpoint.

Final local critical core/data/server reactor: **225 passed**, zero skips. The selector
was based on `f7707887`; its affected suite ran without SQL opt-in, so it did not repeat
historical database scenarios. The three selected real-SQL methods above are the only
SQL regression executions in this review, in addition to the two small measurement
fixtures. The metadata and authorization sources are otherwise unchanged.

The current selector affected suite passed **155 tests** (core 2, data 137, server 16),
zero failures/errors/skips. Commands used were the existing task-local Maven/JDK 21
prefix with:

```text
mvn -pl shale-data -am -Dtest=AiCaseDataAccessTest,IntegrationCaseReadTest,AiCaseDataAttestationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl shale-server -am -Dtest=CimdRetrievalTest,CimdMetadataTest,CimdConnectionConfigurationTest,OAuthCodeExchangePolicyTest,OAuthRevocationPolicyTest,OAuthBrowserFilterTest,McpTransportTest,McpProductionOffBootTest -Dsurefire.failIfNoSpecifiedTests=false test
python build/test-selection/select_tests.py --base f7707887f06ab87cd55ed8eff21ee3b4a2128796 --head HEAD --format json
# Execute the emitted selected_maven_args without SQL opt-in.
mvn -pl shale-server -am test
git diff --check
```

Real SQL ran with the existing disposable opt-in, verified fixture hash and trusted TLS,
using `-Dtest=AiCimdAuthorizationLocalSqlServerTest#` followed by exactly the three method
names above joined with `+`. No full-suite profile, deployment or production action ran.
