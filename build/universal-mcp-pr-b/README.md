# Disposable stock-client acceptance harness

Review-only developer tooling, not a production deployment or automatic activation path.
These are the actual harness sources used for the evidence in
`docs/testing/universal-mcp-pr-b-verification.md`. Workspace paths intentionally describe
that recorded run (`/workspace/Shale`); adjust task-owned paths for another checkout.
No private schema, credentials, certificates, client cache, or compiled artifacts are included.

The Java fixture extends the existing hash-gated authoritative SQL fixture, installs the
unchanged PR A candidates once in a new nonce database, and installs PR B's additive
onboarding projection while the product capability is OFF. It starts with **zero CIMD
applications/approvals/credentials**. Manual clients are seeded by the existing fixture.
Only fixture-owner setup uses `sa`; browser/issuance/Case operations use the actual
restricted adapters. The real password verifier, authentication module, durable WEB
sessions, authorization server, browser page, and Case readers are composed explicitly.

Prerequisites from the recorded environment:

- JDK 21, Maven 3.9.11, Node 24, SQL Server 2022 container, Python Playwright, Firefox and NSS certutil.
- Approved authoritative fixture and hash; never substitute another schema.
- Existing server/data test classes and server test dependency classpath; compile `src/`
  and `SqlEvidence.java` against that classpath plus data test classes. The run snapshots
  compiled classes to avoid changing a running JVM's classpath during Maven compilation.
- `SHALE_AI_SQL_OPT_IN=LOCAL_DISPOSABLE_AI`, loopback port 15433, fixture-owned password,
  `SHALE_AI_SQL_TRUSTSTORE`, `SHALE_CASE_VERIFY_SCHEMA` and its expected SHA-256.
  JVM trust contains the disposable SQL certificate and governed egress CA. Runtime SQL
  verifies the certificate; no `trustServerCertificate=true` is used.
- All mutable artifacts below `work/pr-b-harness`, `work/pr-b-sql`, and `work/pr-b-client`.
  The browser password and SQL password files are mode 0600 and removed after testing.

Run `ConnectionFixture` with the test classpath from the `shale-server` working directory.
It starts a loopback Boot server at port 18080, with explicit TEST identity and default-OFF
flags enabled only in this disposable configuration. `ConnectionServer` overrides only
composition dependencies; the HTTP/security/credential/Case implementation is production
code. `stop` closes the context, asserts historical receipt/binding equality and drops its
exact nonce database/logins. Do not use this composition with an existing database.

Build the existing web app with `VITE_SHALE_API_BASE_URL=https://shale.test`. `front.py`
serves that build and forwards API/protocol requests over the local connector. Issue a
short-lived leaf certificate for `shale.test` from a disposable CA, import that CA into
an isolated Firefox NSS profile, and use the CA for client TLS verification. The scoped
CONNECT fixture routes only `shale.test:443` to loopback; it supplies no OAuth identity.
`dns.py`/`LiveMetadata` document the environment-specific public DNS/proxy seam: fresh
actual all-answer DNS, unchanged classification, numeric pinning and vendor-host TLS.
They are test tooling, not a production proxy implementation. Direct egress remains untested.

Install the **unmodified** Hermes checkout at
`74dde239c8a9a5ae72af1c68b9e74d0b6aa798a5` with its own frozen `uv.lock` and `mcp` extra.
The recorded install used Python 3.14.7 and MCP SDK 2.3.0. Use a new `HERMES_HOME` per tenant.
Set only generic routing/trust/browser-opener environment, then run:

```text
hermes mcp add shale --url https://shale.test/mcp --auth oauth --connect-timeout 300
```

No OAuth `client_id`, metadata URL, redirect, scope, or protocol option is supplied.
The browser-opener writes its argument to a mode-0600 task file; never print that URL.
`browser.py` drives Shale's existing login and separate explicit approval/consent actions.
The CLI itself negotiates and lists tools. `hermes-tool.py` invokes Hermes' unchanged
registration/handler functions with ordinary tool arguments; it does not replace its
transport, OAuth provider, client identity or token storage. No model/API key is needed
for this deterministic tool-dispatch driver. Save only the summarized synthetic results.

`http-regressions.py` supplements the stock-client run with actual refresh/revocation,
foreign tenant/read/approval and ordinary-user checks. It reads actual client identifiers
and credentials from the stock cache and never writes them into a report. Run security
checks sequentially, without concurrent catalog-mutation SQL tests. The existing full
verification is deliberately expensive and any failed CIMD verification withdraws the
instance until reconstruction; no timeout or check is relaxed for this harness.

`SqlEvidence` emits counts only. Review actual matching invocation READ/RETURNED/handoff,
quota release and PHI rows, not merely HTTP success. Close the fixture and remove its
container, certificates, passwords and client/browser credential caches after recording
sanitized evidence. Never log state/code URLs, bearer/refresh tokens or SQL passwords.
