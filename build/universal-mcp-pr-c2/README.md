# Disposable C2 stock-client acceptance harness

Developer test tooling only; no production deployment/activation. Extends the existing
PR B harness and the hash-gated authoritative C1/C2 SQL fixture. No schema substitute,
private fixture, passwords, tokens, keys, browser profiles or compiled files are included.

Use a task-owned SQL Server container on loopback 15433, JDK 21, Maven, real compiled
core/data/server test classes and server dependency classpath. Required fixture SHA-256:
`4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f`.
Set the established `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, fixture path/hash,
SQL password/port/truststore variables. Use verified JDBC TLS. A JVM truststore must
also contain the disposable SQL CA for the bootstrap datasource; Maven JVM options
alone do not propagate that trust into a forked test JVM. Never disable verification.

Compile `src/` against the real server/data test classpath, then start
`com.shale.data.security.ai.DcrConnectionFixture` from `shale-server/`. Snapshot its
classpath before other Maven compilation. It creates a nonce database/login set,
imports the verified fixture unchanged, installs reviewed predecessors, C1 and C2,
checks an empty DCR registry and explicitly enables only disposable controls.
Auth is real BCrypt/SQL plus durable WEB sessions. Registration/OAuth/Case operations
use actual restricted providers. Only fixture preparation/inspection uses owner SQL.
The existing manual registration is retained for a fresh ordinary consent/code flow.

`DcrConnectionServer` starts loopback 18080. Build the existing web UI with
`VITE_SHALE_API_BASE_URL=https://shale.test`. `front.py` serves it over local HTTPS
and forwards protocol/API calls, recording only sanitized paths/status/metadata.
Issue a short-lived `shale.test` certificate from a disposable CA with critical CA
constraints/keyCertSign usage. Trust it in isolated Firefox NSS and client CA stores;
Python 3.14 requires the CA key-usage extension. The scoped CONNECT fixture routes
only `shale.test:443` to loopback. External vendor metadata uses the unchanged PR B
fresh-DNS/classification/numeric-proxy/vendor-TLS test connector. It does not establish
production direct-egress acceptance.

Use **unmodified Hermes Agent** commit
`74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5`, installed with its frozen lock and `mcp`
extra (Python 3.14.7, MCP SDK 2.3.0). Allocate separate empty `HERMES_HOME` directories.
Only route/trust/browser-opener environment is supplied. Run on an interactive PTY:

```text
hermes mcp add shale --url https://shale.test/mcp --auth oauth --connect-timeout 300
```

No client ID, metadata URL, callback override, scope or protocol option. Headless
Hermes prints its authorization URL; `capture-browser-url.py` transfers it privately
to the browser test. `browser.py` uses the existing login, tenant-approval and separate
consent UI. Firefox must run outside a command sandbox that blocks child-process
startup; this changes neither app behavior nor TLS verification. Enable the discovered
tools at the CLI prompt. `hermes-tool.py` calls unchanged Hermes discovery/tool-handler
functions with normal arguments, as in PR B; it implements no OAuth, transport or SQL.

For the **DCR phase**, set `SHALE_C2_CIMD_ADVERTISEMENT=false` in the test server.
This configures the actual Shale property
`shale.ai.cimd.metadata-advertisement-enabled=false`, causing stock clients to choose
DCR via standard discovery. All required lifecycle capabilities remain enabled and
verified. For the **CIMD regression**, the test fixture file `advertise-cimd` switches
that operator property back to its normal true value; use another fresh client profile.
The response comes from Shale's real metadata code, not a rewriting test proxy.
Neither advertisement change grants tenant/credential authority or modifies SQL.

`http-regressions.py` supplements stock acceptance with real refresh, cross-tenant,
ordinary-user/admin rejection, approval/family revocation and an existing manual-client
Case call. `registration-abuse.py` checks RFC errors, size, confidential registration,
and 429/Retry-After. These custom HTTP checks are not claimed as stock-client flows.
`SqlEvidence.java` emits counts and supports exact disposable control OFF/ON probes
and cleanup inspection. Its nonce/loopback guard must remain.

Additional `mcp-remote` 0.16.6 evidence: URL-only default registers/authenticates but
its legacy protocol fails. The unchanged `--protocol auto` option succeeds through
Case tools; that mode is supplemental, not URL-only acceptance. The ordinary JSON-RPC
stdio host in `stock-driver.py` implements no OAuth provider. Do not patch clients or
relax Shale's protocol/auth/deadline checks to turn an incompatible default into a pass.

Stop via the fixture `stop` file. The fixture checks receipt/binding equality and removes
its exact database/logins. Inspect zero remaining fixture databases/logins, remove the
owned container and all task credentials, trust keys, browser/client caches and raw logs.
Retain only sanitized evidence under `docs/testing/evidence/universal-mcp-pr-c2/`.
