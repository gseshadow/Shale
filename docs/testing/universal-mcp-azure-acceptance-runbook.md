# Isolated Azure Universal MCP acceptance

**PREPARATION ONLY — execution NOT authorized; production OFF / NO-GO.**
Baseline: `55deba6c6188c134a053af11c659b60230a1e948`, `codex/latest` including merged
[#1961](https://github.com/gseshadow/Shale/pull/1961). Read with the
[readiness plan](../universal-mcp-azure-readiness.md) and
[runtime configuration contract](../architecture/universal-mcp-azure-runtime.md).
No Azure/SQL operation, deployment, setting change or activation was performed for this runbook.
Every live step below requires separate authorization for the exact nonproduction scope.

**November 13 preparation update:** the operator confirms a new synthetic `ShaleMcpAcceptance`
database, Standard S0/Online with `SQL_Latin1_General_CP1_CI_AS`, on `shale.database.windows.net`.
The [destination-specific installation packet](../sql/ai-integration/azure-acceptance/README.md)
prepares PowerShell 7/validated-TLS SQLCMD stages with controls OFF. No live verification or
installation has occurred. The shared production logical server does not meet the dedicated-server
requirement below; execution needs explicit shared-server risk acceptance. Never delete the shared
server or `Shale` resource group as fixture cleanup. Production `Shale` remains excluded.

## 1. Smallest useful target and execution prerequisites

Use **one Linux Java 21 App Service worker, one fresh synthetic Azure SQL Database
(EngineEdition 5) on a dedicated nonproduction logical server, and one nonproduction
Static Web Apps frontend**. Reuse existing *nonproduction* plan/app/server/frontend only after
isolation and ownership are evidenced; create missing resources only under later authorization.
Do not use a slot of the production API, a production SQL server/database, a production web deployment,
a database copy/backup containing real rows, production credentials or production DNS changes.

A generated API `*.azurewebsites.net` HTTPS origin and a separate frontend HTTPS origin suffice;
custom DNS, Front Door, another database framework and a new deployment system are unnecessary.
Use existing nonproduction plan capacity when available. The archived Azure fixture used SQL S0;
that is an evidenced starting candidate, **not** proof it can meet current verification/concurrency
budgets. An App Service SKU and capacity have not been selected. Record/approve them before execution;
if the chosen capacity fails, STOP rather than increasing request timeouts or skipping checks.
One worker establishes single-worker acceptance only; multi-replica rollout needs a separate test.

**Nothing currently identifies a suitable live target.** The
[Nov 4 closeout](native-azure-diagnostic-closeout.md) found only production SQL and stopped its
investigation. Do not resume its archived launcher/diagnostic campaign under this preparation task.

Complete a nonsecret execution manifest before any connection or mutation:

| Required input / permission | Current disposition |
| --- | --- |
| Subscription/tenant, nonproduction resource group, full resource IDs and ownership | Missing |
| App Service/plan/SKU/worker count, actual ingress and outbound route revision | Missing |
| Dedicated nonproduction SQL logical-server FQDN, fresh database name/resource ID, tier/collation | Missing; require reviewed `SQL_Latin1_General_CP1_CI_AS` fixture compatibility |
| Test API and frontend origins, TLS, browser workstation/callback reachability | Missing |
| Resource read/deploy/configure/stop/cleanup rights limited to those nonproduction resources; existing Kudu/SSH access for worker probe | Not established; do not request subscription-wide privileges |
| SQL fixture owner capable of reviewed DDL/contained-user provisioning in the fresh database; independent restricted identities | Not established; never give owner credentials to the app |
| SQL network/firewall path for worker and operator; DNS/public TCP 443/trust for metadata on that worker | Not established; no broad “allow Azure services” or production firewall change implied |
| Private fixture location, approved packet/source/artifact hashes, synthetic passwords, cleanup owner/deadline/cost approval | Fixture is not attached in this checkout; other inputs missing |

Record exact IDs in a private cleanup manifest *before* creating anything, with separate lists of
new versus reused resources. Retain approved nonproduction names as variables, never infer them
from production examples. With execution authorization, these read-only metadata commands can
confirm the selected targets; they are **not** commands to run now:

```bash
az account show --query '{subscription:id,tenant:tenantId}' -o json
az webapp show --resource-group "$ACCEPTANCE_RG" --name "$ACCEPTANCE_APP" \
  --query '{id:id,state:state,httpsOnly:httpsOnly,host:defaultHostName,plan:serverFarmId}' -o json
az webapp list-instances --resource-group "$ACCEPTANCE_RG" --name "$ACCEPTANCE_APP" -o json
az sql db show --resource-group "$ACCEPTANCE_RG" --server "$ACCEPTANCE_SQL_SERVER" \
  --name "$ACCEPTANCE_DB" --query '{id:id,status:status,sku:sku,collation:collation}' -o json
```

STOP on a mismatched ID, shared production dependency, unknown ownership/permissions or failed read.
Do not dump App Settings, process environments, publishing credentials or secrets to establish parity.

## 2. Source, fixture and SQL preparation

Offline commands from the repository root, in a fresh build shell without SQL opt-ins or deployment
credentials (only these checks/builds require no Azure execution):

```bash
git merge-base --is-ancestor 55deba6c6188c134a053af11c659b60230a1e948 HEAD
git rev-parse HEAD
sha256sum "$AUTHORITATIVE_FIXTURE"
mvn -B -pl shale-server -am clean package
sha256sum shale-server/target/shale-server-*.jar
VITE_SHALE_API_BASE_URL="$ACCEPTANCE_ORIGIN" npm run build --prefix shale-web
```

Require fixture SHA-256 **`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`**
before import/any fixture connection. Use the unchanged `authoritative-candidate.sql` and existing
`CaseReadVerificationSafety` DDL parser. Never substitute a synthetic schema, reconstruct the private
fixture or import a production backup. The source-export derivation utility accepts a different
hash (`4ac397…`); it is not a replacement input for this run. Retain hashes of the one executable
server JAR (never `.jar.original`) and web build, source SHA, JDK/driver/client versions.

### Ordered owner packet for the fresh database

Paths below are relative to `docs/sql/ai-integration/`. All product/proof/OAuth/external/DCR controls
remain OFF during installation. Fresh neutral dbo/autocommit connections, required context keys NULL,
exact schema/source prerequisites and fail-fast exit/result checks are mandatory. **Pause before
binding (step 5) to perform section 3's dormant deployment/worker-zone/egress steps, then return here.**
Ordinary identities/schema must be ready for that dormant app. Owner DDL and
restricted runtime request budgets remain separate. Use existing TLS-validated SQLCMD policy
`-N -b -V 16`, interactive password, no `-C`/`-P` if SQLCMD is the approved packet runner.

| Order | Required operation / existing source |
| --- | --- |
| 1 | Empty-target/EngineEdition 5 check; `azure-sql-stage0-verify.sql`; hash-gated authoritative DDL import. |
| 2 | `phase3b1.sql` → `phase3b2b.sql` → `phase3b2c1.sql` → `phase3b2c2.sql` → `revocation.sql` → `phase3b2d.sql` → `phase3b3.sql`. |
| 3 | Reviewed clean-room Phase 1R normalization/PREFLIGHT/APPLY/POSTCHECK and `phase3b8-confinement-prerequisite.sql`, as used by `AiCaseDataV2AuthoritativeLocalSqlServerTest.beforeConfinement` and the native setup helper. The candidate already contains three filters: do **not** blindly replay production auth/Phase 1R preflights over it. |
| 4 | `phase3b8-confinement.sql`; `verify.sql` / `phase3b8-confinement-verify.sql`; contained ordinary/predecessor identities. Never substitute historical V1 `phase3b7-confinement.sql`. |
| 5 | One initial `AiCaseDataDeployment` binding, Id=1, `case-read-v1`, exact **test** origin + `/oauth/mcp` and `/mcp`, actual worker JVM zone and approved SQL time-zone mapping. Use `deployment-binding/` discovery/preflight/install/postcheck and its 28 Java/SQL samples; inputs must be reviewed for this fixture. Never bind `shale.test` then update the immutable row to an Azure hostname. |
| 6 | `universal-pr3-confinement-prepare.sql` → `universal-pr3.sql`; `universal-pr3-authority-inventory.sql`, `universal-pr3-verify.sql`, `universal-pr3-confinement-verify.sql` at that version. |
| 7 | `universal-pr4a-proof.sql` → `universal-pr4a-proof-verify.sql`; `universal-pr4b-oauth.sql` → `universal-pr4b-oauth-verify.sql`. |
| 8 | `universal-pr-a-reviewer.sql` → `universal-pr-b-onboarding.sql` → `universal-pr-c1-external-v2.sql` → `universal-pr-c2-dcr-v3.sql`. |
| 9 | Provision the 12 role-specific capability users after their roles exist; run the packet's version-selected read-only Case V2/DCR_V3 catalog checks with controls OFF. Full runtime startup/admission follows only in section 3's separately authorized enabled test state. Preserve historical receipts/binding; never refresh V1/C1 receipts to accept a successor. |

Ordinary browser login/session dependencies must also be verified against the imported DDL:
`UserSessions`, `ApplicationInstances`, `SessionSecurityAuditLog`, `DesktopRememberCredentials`,
`Users`, `ShaleClients` and audit dependencies. The C2 browser fixture installs
`docs/sql/2026-10-14_pretenant_authentication_modules.sql`, grants bounded
`LookupAuthenticationCredentialV1` EXECUTE to its separate auth identity and the required ordinary
session/runtime table permissions. Reuse that fixture seam with a reviewed ordinary caller ACL;
do not blindly replay production auth-cutover/legacy-retirement scripts or grant whole-schema access.
Install older ordinary foundation scripts only if the imported-schema inventory proves a missing
prerequisite and the packet explicitly includes it. Unknown is not permission to rerun an installer.

### Contained identities and synthetic data

Use **17 independent app-consumed identities** for the full DCR run: ordinary auth/runtime (2),
predecessor Case/verifier/bootstrap (3), and the following capability providers (12). Owner/operator
is separate and is absent from application settings. Create Azure **contained SQL users**, explicit
database, independent passwords/SIDs; no master/server logins, server metadata grants or dbo fallback.

| Provider | Exact sole role / permission contract |
| --- | --- |
| Predecessor Case | `AiCaseDataRuntime`: six `AiData*V1` EXECUTEs, existing direct/legacy denials |
| Predecessor verifier | `AiCaseDataCatalogVerifier`: existing VIEW DEFINITION/dependency/reference-table permissions |
| Bootstrap | `AiIntegrationBootstrap`: existing provisional resolvers only |
| `case`, `reviewer` | `AiExternalCaseRuntimeV2`; `AiCimdAuthorizationCatalogV1` respectively (reviewer has exactly VIEW DEFINITION, dependency-view SELECT, receipt-accessor EXECUTE) |
| `publisher`, `approval`, `onboarding` | `AiApplicationPublisher`; `AiApplicationTenant`; `AiExternalOnboardingRuntimeV2` |
| `browser`, `issuer`, `metadata`, `resolver`, `neutral` | `AiExternalOAuthBrowserV2`; `AiExternalOAuthIssuerV2`; `AiExternalMetadataPublisherV2`; `AiExternalOAuthResolverV2`; `AiExternalOAuthNeutralV2` |
| `dcr-endpoint`, `dcr-metadata` | `AiDcrEndpointV3` (admission/register/status only); `AiDcrMetadataReaderV2` (stored metadata only) |

New provider settings are exactly `shale.ai.cimd.runtime.<key>.{jdbc-url,username,password}`;
existing ordinary/predecessor settings and their exact ACLs are in the linked runtime/readiness docs
and installers. Preserve their explicit direct-table denials and exact inherited/public/column checks.
Do not combine onboarding with approval, use `AiDcrRegistrarV2` for HTTP registration, or provision
unused legacy CIMD/C1 roles merely because they exist. Separately authorize any administrative DCR
revision/revocation test through `AiDcrAdministratorV2`; do not give it to the web process.

Reuse the established synthetic identities: tenants 7/8; admins 71/81, ordinary users 72/82;
Case 100 belongs to 7, Case 200 to 8, Case 300 is deleted in 7. Use the fixture's existing seed
methods/rows, fresh BCrypt test passwords and ordinary WEB session issuance. These numbers are
**fixture-only**, never production targets. Start with no external approvals/grants/credentials;
registration must not create any. Keep one independently provisioned synthetic manual client for continuity.

**Missing execution packet:** there is no current turnkey Azure V3 seed/provisioning launcher.
`AiCaseDataAzureSqlDisposableSetupTest` fixes a `ShaleAzureReadOnly_<nonce>` name, five `azro_*`
users and a `shale.test` binding; its read-only test selects the predecessor contract, not DCR_V3.
The C2 launcher creates loopback server logins and enables fixture controls. Neither may be pointed
at the App Service database or have its guards disabled. Before live execution, review a narrowly
scoped destination-bound owner packet that reuses the above imports/installers/seed methods, supplies
the 17 contained identities and the correct initial Azure binding. No runtime/SQL-security redesign
is needed. Failure/uncertain commit means discard the exact owned fixture, not adoption or resealing.

## 3. Dormant app, worker egress and supported startup

Later, with explicit nonproduction deployment/configuration authorization, use the existing Kudu
JAR upload path `/home/site/wwwroot/shale-server.jar`, startup
`java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure`, and existing Static
Web Apps facilities for the matching web build. Do not trigger a production/main web publication.
Keep all ten app controls from the readiness plan false and all five installed SQL controls zero.
PASS: ordinary `/api/health` 200, AI readiness disabled, no external providers/AI pool, MCP/OAuth
POST routes closed (expected 404), no registration advertisement or issuance. Obtain the test worker's
`SHALE_JVM_STARTUP` zone evidence before binding; do not infer the running JVM zone from OS UTC.

Run the metadata probe **on that same worker**, using its unchanged Java/trust/network route,
before feature activation and before the initial immutable binding. An operator can extract the exact approved Boot JAR in a private scratch
directory and use the existing JShell pattern; no Spring startup, SQL or OAuth credential is involved:

```bash
command -v jshell
mkdir -p work/azure-acceptance-egress
cd work/azure-acceptance-egress
jar xf "$ACCEPTANCE_JAR" BOOT-INF/classes BOOT-INF/lib
EGRESS_CP=BOOT-INF/classes
for dependency_jar in BOOT-INF/lib/*.jar; do EGRESS_CP="$EGRESS_CP:$dependency_jar"; done
jshell --class-path "$EGRESS_CP" egress.jsh
```

`ACCEPTANCE_JAR` is an absolute verified artifact path; `egress.jsh` contains:

```java
import com.shale.server.oauth.CimdClientIdentifier;
import com.shale.server.oauth.cimd.CimdMetadataService;
try (var service = new CimdMetadataService(System.getenv("ACCEPTANCE_ORIGIN") + "/oauth/mcp")) {
    long started = System.nanoTime();
    var snapshot = service.retrieve(new CimdClientIdentifier(
        "https://nousresearch.github.io/hermes-agent/docs/oauth/client-metadata.json"));
    long ms = (System.nanoTime() - started) / 1_000_000;
    if (ms > 5000) throw new IllegalStateException("Metadata budget exceeded");
    System.out.println("PASS metadataMs=" + ms + " redirects=" + snapshot.redirects().size()
        + " methods=" + snapshot.methods());
}
/exit
```

Export only the approved nonsecret `ACCEPTANCE_ORIGIN` for this process. Run three fresh-JVM cold
probes; each must finish retrieval within 5 s with original-host TLS and unchanged all-answer DNS/
SSRF classification, numeric address pinning and redirect/body limits. For an approved confidential
client, additionally retrieve its real metadata/JWKS in the same combined budget. Default fetcher
ignores ambient HTTP proxies: proxy-only egress fails this gate. No `LiveMetadata`, test DNS/CONNECT
adapter, trust-all flag, private-address exception or substitute metadata. Curl success or another
VM is insufficient. If worker JDK/JShell/scratch execution access is absent, record that missing
prerequisite and approve a same-runtime diagnostic method separately; do not silently install tools.

For later enabled nonproduction startup, require `azure` only, identity `PRODUCTION` (the runtime
identity grammar, **not a production destination**), exact test public/browser origins, Case `V2`,
external `DCR_V3`, independently configured capability sources, exact Origin/CORS policy, synthetic
JWT secret and approved session cutover. Use separate authorized setting snapshots:

- Dormant: ten controls false; SQL product/proof/OAuth/external/DCR zero; no tenant enablement.
- CIMD phase: complete verified AI/MCP/Case/OAuth/CIMD composition and both tools enabled;
  DCR false, CIMD advertisement true, corresponding synthetic SQL controls enabled only by authorization.
- DCR phase: same V3 composition, DCR capabilities/control enabled, optional CIMD advertisement false
  so a fresh stock client chooses DCR automatically. Keep all production configuration untouched.

PASS requires actual contained-principal checks, `AI_CASE_SQL_READINESS ready`, complete V2/DCR_V3
catalog/binding verification and successful restricted requests. The readiness marker alone is not
full external acceptance. DCR startup deliberately refuses an OFF SQL DCR switch; do not expect
a complete DCR-enabled Boot while every SQL control is zero. In isolated sequential negative runs, missing provider configuration,
wrong Case/external contract or substituted capability identity must refuse startup before serving.
Restore reviewed settings and reconstruct; no catch/fallback. On catalog failure/withdrawal STOP;
record the fixed stage/code/capability, never broaden grants/timeouts or retry a historical verifier.

## 4. HTTPS, browser and stock-client acceptance

With authorized enabled test state, these public requests require no credentials:

```bash
curl --fail --silent --show-error "$ACCEPTANCE_ORIGIN/.well-known/oauth-protected-resource/mcp"
curl --fail --silent --show-error "$ACCEPTANCE_ORIGIN/.well-known/oauth-authorization-server/oauth/mcp"
curl --silent --show-error --dump-header - --output /dev/null \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data '{}' "$ACCEPTANCE_ORIGIN/mcp"
```

Require exact test resource/issuer, both supported scopes, S256/code/refresh/approved client methods,
truthful mechanism advertisement, and anonymous MCP 401 with the exact protected-resource challenge.
Verify actual HTTPS routing for `/oauth/mcp/{authorize,consent,token,revoke,register,onboarding,approval}`
and `/mcp`, REQUEST/ASYNC security dispatch, no Host/Forwarded-derived identity or internal connector URL.
The frontend `/oauth/mcp/authorize` deep link must load through the existing SPA fallback; browser
redirect goes to its fragment form, original Shale login returns correctly, CORS/preflight uses the
exact frontend origin and callbacks never receive the WEB bearer.

Use unmodified Hermes commit `74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5`, frozen lock/`mcp` extra;
record actual Python/MCP SDK versions (recorded baseline 3.14.7 / 2.3.0). In an operator terminal:

```bash
export HERMES_HOME="$ACCEPTANCE_PRIVATE_DIR/hermes-cimd-a"
hermes mcp add shale --url "$ACCEPTANCE_ORIGIN/mcp" --auth oauth --connect-timeout 300
```

Repeat with empty, separate profile directories for CIMD tenant B and DCR tenants A/B after the
approved advertisement change/restart. The client gets only the URL and standard `--auth oauth`;
no pasted ID, metadata/callback/scope/protocol override or client patch. Use the real browser manually
for the smallest run: synthetic normal password login, tenant admin approval, **separate** user
consent, callback, tool discovery and both tools. Also test an ordinary user cannot approve a tenant
application, consent denial/expiry, replaced/logged-out WEB session, and manual-client continuity.
A stale/unbound/DESKTOP or foreign WEB session must fail. No alternate password page/dev headers.

Require Case 100 only for tenant A, Case 200 only for B; deleted/foreign/absent Case content is withheld.
Refresh must rotate and reject replay; family/tenant approval/application revocation blocks the next
access/refresh, while the other tenant remains valid. A shared CIMD application needs independent
approvals/consent in both tenants. For DCR shared-application coverage, reuse an automatically
registered ID in a supplemental normal authorization flow for the second tenant; label that flow
supplemental, not a fresh URL-only stock registration. Record only safe status/count/latency facts.

Reuse `build/universal-mcp-pr-c2/{README.md,browser.py,http-regressions.py,hermes-tool.py}` as the
scenario/API reference. Their checked-in endpoints, proxy/trust stores, private file paths, synthetic
seed setup and full-result outputs are loopback-specific. Do not run them unchanged against Azure.
Manual browser/stock CLI needs no replacement harness. Any automated driver adaptation must change
only destination/private paths/standard TLS and sanitize output, with explicit nonproduction fences;
retain stock library calls, protocol assertions, original authority and all product deadlines.

## 5. Confinement, admission, concurrency and DCR limits

| Test | Acceptance / STOP condition |
| --- | --- |
| Actual SQL identities and denials | Exact own-user/public/role tokens, contained type/auth/session SID proof, distinct provider/predecessor/ordinary SIDs and exact server/database. Under restricted Case/reviewer/resolver identities, reviewed `SELECT TOP (0)` protected-table probes must be permission-denied, not syntax/network failures; no DML/DDL/impersonation or legacy/proof-namespace escape authority. Use current pinned catalogs and the native read-only test's denial methodology, not its incompatible launcher. |
| Startup and each admission | Real production-profile Boot and protected requests must execute the existing complete V2 plus DCR_V3 checks. Capture bounded timings/stage outcomes with existing test seams; HTTP 200 alone is insufficient. Bad configuration/principal fails startup; revoked credential denies the next request without bypass or new budget. |
| Durable disclosure/audit | Each positive synthetic disclosure has committed matching READ/ALLOWED_READ, RETURNED resource, consumed handoff and released quota; Overview adds required PHI read audit. Registration has one matching global creation audit; approval/consent/lifecycle changes retain transaction-bound tenant audits. No success after audit failure. Preserve exact receipt/binding values before/after. |
| Focused parallel calls | Stable schema: one batch each at 1, 2, then 4 simultaneous Overviews, plus one mixed Search/Overview batch. Use four independently consented users (71/72 in A, 81/82 in B), one call/user, at most two per shared tenant integration. Current quota permits one live reservation/user, two/integration, five/tenant and 10 attempts/user/minute: reconcile existing usage and use fresh windows. Reuse the maintained Hermes handler or exact `http-regressions.py` MCP envelope/header equality through HTTPS. Synchronize launch; record end-to-end statuses/latencies under the unchanged 10 s server budget. All in-budget authorized calls pass within 10 s, no 5xx/504/withdrawal/missing audits/unreleased reservations; following probes pass. Test intentional quota exhaustion separately and expect refusal with no disclosure. Record per-call/max time, not an SLA; no concurrent revocation/catalog campaign. |
| Peer/NAT budget | Fresh SQL UTC window: 11 bounded invalid registrations from one socket peer; requests 1–10 are validation errors, 11th is 429 with positive Retry-After. Budget includes prior stock registration attempts: wait for a fresh window or reconcile counts. Two external clients behind one NAT/ingress may share that 10/min bucket; measure the actual worker peer attribution without logging raw addresses. Spoofed X-Forwarded-For must not reset the bucket. Unacceptable shared-client starvation is STOP pending explicit ingress/capacity decision. |
| Independent durable limits | Fixed limits: 100 global, 10/source, 10/canonical-metadata fingerprint per UTC minute; accounting bounded to 1,024 rows. A single ingress peer cannot isolate fingerprint/global limits because peer throttling occurs first. Require reviewed native admission-store scenarios with distinct synthetic source digests (from existing C2 SQL tests), or real trusted peer diversity. Do not forge headers, disable peer limiting or claim all three limits passed from one 429. Cross-replica HTTP enforcement is untested with one worker. |
| Retention/capacity | 5,000 cumulative identities, not a renewable pending capacity; after 24 h only never-approved public payload/cache is reclaimable. Identity/audit history and approved metadata survive. Reuse the C2 deterministic SQL scenarios for boundary/expiry/approval races rather than creating 5,000 HTTP registrations or waiting a day on the live worker. Native execution needs a destination-fenced adapter; otherwise these gates remain OPEN. |
| Audit fault / drift / final handoff | A separately reviewed synthetic-only negative substage must exercise exact audit-fault rollback and post-read revocation/final-handoff denial from the existing C2/PR B tests. Isolate it from serving/capacity runs, drain traffic, snapshot history, inject only its reviewed fixture fault, assert the intended error and no disclosure, then discard/rebuild the fixture. No concurrent catalog mutation campaign or re-sealing. Verifier failure withdraws the instance; restore only by reviewed reconstruction. |

Native admission/fault/retention adapters and safe counts-only evidence collection are part of the
missing bounded execution packet. `SqlEvidence.java` contains useful joins/count assertions but is
owner/loopback/nonce-bound; do not disable its guards. Old
`AiCaseDataAzureSqlDatabaseReadOnlyTest` does not select the DCR_V3 successor. A skipped, incompatible
or unexecuted native test is **OPEN**, not PASS. This runbook defines these tests; it does not invent
a runnable Azure test command where no supported adapter exists.

## 6. Decision, containment and cleanup

Maintain one result sheet: manifest/source/JAR/web/client versions; fixture/packet hashes; complete
SQL/identity/binding disposition; cold metadata timings; four stock flows and manual continuity;
SQL/tenant/revocation/audit outcomes; 1/2/4 concurrency results; peer/fingerprint/global/retention
coverage; startup/admission failures; cleanup confirmation. Distinguish reused local evidence from
new native PASS, FAIL and OPEN. Never retain passwords, proofs, tokens, raw HAR/bodies, callback/
state/code URLs, full SQL/exception dumps, IP addresses or browser/client caches in shared evidence.

STOP immediately on a production reference, wrong resource/hash/schema/identity/binding, missing
permission/visibility, unexpected control state, TLS/SSRF failure, internal callback origin, tenant
leak, successful unaudited operation, catalog withdrawal, deadline failure or incomplete cleanup.
No broader permission, timeout increase, proxy test seam or older contract is an automatic repair.

Contain the authorized test scope: stop tool/transport/authorization/registration/issuance, revoke
synthetic integrations/grants/families via existing governed operations, retain a revocation path
until complete, turn test SQL/app controls OFF, drain/stop the test app. Roll back only to its
retained compatible dormant JAR/configuration and verify closed endpoints. Do not uninstall SQL,
erase audits/receipts, overwrite the binding or revive an older unrevocation-aware server.

After retaining sanitized evidence, delete the **exact newly owned** test database/app/frontend/
server or test resource group named in the cleanup manifest, only if that group contains solely
this run's new resources. Reused nonproduction plans/servers are retained; remove only approved
run-owned children/settings/network rules. Use resource-type-specific deletion/verification from
the reviewed manifest; identifiers are missing, so no deletion command is prefilled here. Confirm
absence by successful inventories, not a failed read. Reconcile partial creation/deletion before exit.
Destroy test passwords/JWT keys, private fixture derivatives, browser/Hermes token directories,
worker scratch JAR/classpath/probe files and raw logs; revoke remaining test credentials.

**Exit:** complete native PASS plus confirmed cleanup permits a separate pilot-review decision only.
Any FAIL/OPEN keeps Azure activation NO-GO. Production remains OFF regardless of test outcome.
Next approvals are limited to the target manifest/cost/permissions, bounded fixture/driver packet,
and explicit isolated execution window; deployment/production SQL/pilot remain separate decisions.
