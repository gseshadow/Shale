# Confined SQL build inputs

`generate.py` is run from any directory with JDK 21 `java`/`javac` on PATH. It reads the unchanged
historical migrations, normalization template and JDK Unicode reference, then writes the additive
migration, standalone/cumulative verifier and packaged module/source manifest deterministically.
It does not connect to SQL, provision identities or enable handlers.

`case-catalog.sql` describes portable authoritative Case/RLS catalog facts; its expected SHA-256 is
from the hash-verified external schema plus the reviewed candidate predicates. Do not update the
expected digest to accommodate unexplained catalog drift. The external schema and derived exports
are never build inputs to commit. See the SQL runbook for provenance and verification requirements.

The generated SQL implements Java strip/UTF-16 letter-digit/Locale.ROOT normalization, including
contextual Greek sigma using the JDK word-break DFA. The supported JDK reference and resulting
SQL semantics are covered by executable normalization and ordinary-DAO/protected-SQL parity tests.

To reproduce the authoritative local fixture without changing or committing the supplied export:

```bash
python3 build/sql-confinement/prepare-authoritative-fixture.py /path/to/shale-schema-source.sql work/sql-fixture
```

The script verifies the required source SHA-256 before decoding, retains all 96 authoritative table
shapes and reviewed DDL, and checks both derived hashes. Point `SHALE_CASE_VERIFY_SCHEMA` at the
private `authoritative-candidate.sql` and `SHALE_CASE_VERIFY_SCHEMA_SHA256` at its receipt value.
Use only the explicit disposable loopback opt-in and synthetic credentials described in the existing
verification harness. Neither the preparation script nor generator executes SQL.

The complete authoritative acceptance now includes a real external-table fault, not only a BLOB
source and synonym. Its owned SQL Server 2022 fixture requires the matching PolyBase component,
`polybase enabled=1`, and a restart after configuration. Absence is a failing prerequisite, not a
skipped mandatory test. The external SQL source points back to the same owned instance's internal
`localhost:1433`, with a nonce runtime credential and an empty synthetic marker table. Its nested
SQLPAL ODBC connection uses a local-only certificate trust override; it is not TLS acceptance.
Production-composition JDBC and the separate Caddy proxy tests still require validated TLS.
The shared catalog helper tests do not establish native Azure contained-user or catalog semantics.
See the [current acceptance receipt](../../docs/testing/azure-sql-case-attestation.md) for exact
engine/component hashes, commands, results and the remaining disposable Azure gate.


## V2 successor

`python3 build/sql-confinement/generate-v2.py` reads frozen V1 outputs but writes only new V2
installer/prerequisite/standalone/package files, a new manifest, and the V2 cumulative dispatcher.
It never regenerates historical V1. Reviewed Case module inputs are hash-only explicit variants;
private source/candidate/production metadata must never be committed. The V2 generator encodes all
13 unchanged confined module bodies as UTF-16LE hex so checkout line endings do not change installation.
See [V2 design](../../docs/architecture/phase3b8-confinement-v2.md) and the V2 acceptance record.

Run `python3 -m unittest discover -s build/sql-confinement -p 'test_v2.py'` for static contracts.
The private projection case requires SHALE_CASE_VERIFY_SCHEMA and SHALE_V2_PRODUCTION_CATALOG;
its skip without inputs is never acceptance. Actual SQL tests additionally require the existing
LOCAL_DISPOSABLE_AI opt-in, loopback port/password and candidate SHA-256. V1 tests remain V1;
AiCaseDataV2LocalSqlServerTest runs the same authority assertions on production-style metadata,
and AiCaseDataV2AuthoritativeLocalSqlServerTest explicitly selects the authoritative variant.
