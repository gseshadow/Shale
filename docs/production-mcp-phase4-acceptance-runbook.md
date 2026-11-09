# Phase 4 OAuth bootstrap datasource security invariant

This scoped hardening starts from `b3d6f86c` on `codex/latest`, including merged PR #1942.
This runbook and the companion verification record were absent at that baseline. Existing authority
remains in `production-mcp-deployment-preflight.md`, `production-mcp-integration-readiness.md`, and
`testing/production-mcp-case-composition.md`; this change does not supersede their activation gates.

## Required invariant

Every enabled OAuth exchange/bootstrap datasource validates its complete dedicated configuration
before allocating a datasource, opening SQL, resolving a code, or exchanging credentials. Bootstrap,
AI Case, and readiness verifier sources share `AiJdbcUrl.validate`. No ordinary pool or credentials
are fallback candidates. Readiness validates all three URLs before allocating the AI pool.

AI JDBC URLs select only the destination and optional application name. Supported syntax is
`jdbc:sqlserver://host[:port][;databaseName=value][;applicationName=value]`, with driver-style named
instances and bracketed IPv6 also accepted. Ports are 1–65535. Duplicate keys, blank values,
control characters, malformed settings and brace-escaped values are rejected. A trailing semicolon
is accepted. Property names are case insensitive. Only `databaseName` and `applicationName` are
allowed: unknown driver settings fail closed, including future security aliases.

TLS and identity policy belong to datasource properties. Encryption is explicitly `true` and
`trustServerCertificate=false`. No hostname override is permitted: the Microsoft driver validates
the server name from the destination against a trusted server certificate. URL-supplied TLS,
authentication, embedded credentials, trust managers/stores, certificate pins, socket factories,
MARS and timeout settings are rejected even when their supplied value appears safe. This removes
URL/property precedence ambiguity. Separate required username/password settings retain their values.
Bootstrap keeps its existing five-second login and ten-second socket timeouts.

Only `exchange-enabled=true` constructs the OAuth bootstrap provider. Absent/false switches require
no bootstrap configuration and allocate no bootstrap datasource. All eight controls remain OFF by
default. No new startup SQL probe, background task, endpoint or token contract is introduced.
Malformed configuration blocks enabled startup; SQL/TLS failure withholds the exchange and emits
only `OAuth bootstrap unavailable.` without a driver exception chain. SQL log protection is installed
before driver configuration, including when Case composition is OFF.

## Operator review after merge

No immediate deployment, SQL, Azure, credential, migration, immutable binding, or activation action
is required. Before a separately authorized future release enables an affected source, review its
URL offline for this destination-only format. Remove embedded policy/credential/timeout options
through the normal governed configuration process; do not add a validation bypass. Ensure the
existing JVM trust configuration trusts the server certificate and that its hostname matches the
URL. A URL that previously supplied insecure TLS or extra driver properties will now fail startup.
Synthetic OAuth SQL fixtures also need a trusted localhost certificate for bootstrap; owner/setup
fixture connections are separate. No test/development TLS exemption is provided.

## Audit compatibility and retained gaps

This is process configuration validation, not a sensitive data read or domain/admin mutation; it
creates no audit event and needs no audit schema/migration. Existing SQL exchange, revocation and
Case disclosure audits remain authoritative and unchanged. Never audit rejected URLs or credentials.

Offline driver-property tests do not prove a deployed TLS handshake, native Azure catalog
acceptance, production readiness, identity separation, public ingress, monitoring or pilot approval.
Those existing gates remain open under their current records. Browser authorization/consent UX is
outside this PR. No production datasource was contacted and no activation/deployment is authorized.
