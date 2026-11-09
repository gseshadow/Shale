# Deployment-binding SQL LF attributes repair — November 9, 2026

Base: `05788239ea95b7b3d724198de7a11559affded7f` on `codex/latest`.
Scope: offline checkout and packaging only. No production SQL, Azure, deployment, MCP/OAuth
activation, credential/grant change or immutable deployment-binding operation.

## Root cause and minimal fix

The unchanged checkout guard obtains its inventory using Git's
`git ls-files -- docs/sql/ai-integration/*.sql` pathspec, which includes nested paths. Git
attributes patterns have different semantics: `docs/sql/ai-integration/*.sql` does **not**
apply to subdirectories. All seven committed `deployment-binding/*.sql` files therefore had
`eol: unspecified`, and the guard correctly refused them with `Current PR LF attributes required`.

The permanent repository fix is one additional rule:

```gitattributes
docs/sql/ai-integration/deployment-binding/*.sql text eol=lf
```

It explicitly covers discover, inputs, install, postcheck, preflight, session and validate SQL,
without changing unrelated checkout policy. All seven already have LF committed blobs; no
SQL edit, regeneration, renormalization or new receipt is necessary. The rule applies when Git
materializes files on Windows, macOS or Linux, independently of `core.autocrlf` conversion.

The previous test fixtures used `Path.glob('*.sql')`, which excluded nested files and tested only
25 of the guard's actual 32 pinned files. Both fresh and existing-worktree fixtures now use the
same Git pathspec inventory as the guard. The CLI assertion uses the actual fixture count.
Fresh-prefix coverage exercises `core.autocrlf=true`, `input` and `false`; an unrelated text
file proves conversion is active when expected. The existing cached-CRLF refresh, fresh clone,
staged/genuine-edit refusal, unchanged index/flags and unrelated-file preservation checks now
include the seven nested SQL files too. No production guard or security verification logic changed.

## Evidence and verification

Before the attributes fix, the expanded test fails under `core.autocrlf=true` because
`deployment-binding/discover.sql` is materialized as CRLF. After the fix, all checkout tests pass.
These are real Git conversion tests on Linux, not a claim of new native Windows/macOS execution.

All 32 pinned files have unchanged SHA-256 values against the base commit, including the seven
binding files, historical migrations, verifiers, packaged SQL/module manifests and catalog inputs.
The raw-byte source receipts remain:

| Contract | Reviewed source receipt |
| --- | --- |
| V1 | `b949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3` |
| V2 | `60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891` |

| Command/check | Result |
| --- | --- |
| `python -B -m unittest discover -s build/sql-confinement -p test_checkout.py -v` | 11 PASS, no failures/errors/skips; all 32 pinned files in the fixtures. |
| `python -B build/sql-confinement/checkout_bytes.py --refresh` followed by `python -B build/sql-confinement/checkout_bytes.py` | Both PASS: 32 exact files, clean pinned index, unchanged V1/V2 receipts. |
| `git check-attr eol -- docs/sql/ai-integration/deployment-binding/*.sql` | All seven `eol: lf`. |
| `python -B -m unittest discover -s build/deployment-binding -p test_packet.py -v` | 5 PASS; SQL inspected as text only. |
| `python -B -m unittest discover -s build/sql-confinement -p test_v2.py -v` | 3 PASS, 1 private-input SKIP; skip is not live/private-artifact acceptance. |
| Focused Maven: `mvn -pl shale-data -am -Dtest=AiCaseDataMigrationContractTest,AiCaseDataV2MigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test` | 2 PASS; unchanged raw migration source receipts. |
| Selector affected security-data Maven suite | 142 PASS, zero failures/errors/skips in executed cases; SQL opt-in absent, no database fixture ran. |
| `mvn test` | 229 PASS, zero failures/errors/skips; BUILD SUCCESS. Counts overlap the affected suite. |
| `mvn -pl shale-data -am -DskipTests package` | BUILD SUCCESS; all four packaged `ai/` SQL/manifest resource entries match source bytes/SHA-256 exactly. Local data JAR only; no deployment artifact published. |
| Baseline SHA-256 comparison of all guard inputs and Git diff checks | 32 unchanged pinned files; no SQL, resource, production Java or guard changes. |

Maven used workspace Maven 3.9.11/JDK 21.0.13, the existing session proxy/trust settings and local
dependency cache. The child environment removed `SHALE_*`, `SPRING_*`, `SQLCMD*`, inherited JVM
injection/Maven options and all Shale SQL opt-ins; normal proxy variables and validated CA trust
remained. No test process received a production destination or credential. The selector maps the
checkout test/README to security-data, covering receipt/RLS/audit contracts that consume the pinned
SQL. Its full-suite recommendation is false; no unrelated full suite was run.

Changed-file review: `.gitattributes` is verified by the actual guard and Git conversion/refresh
regressions; `test_checkout.py` expands the existing contract's inventory; the README and this record
document the repair and local operator preflight. No new application read, mutation, audit event or
schema is introduced; audit compatibility is unchanged.

## Local action after merge

Fresh checkouts receive LF automatically. Existing cached CRLF copies may remain unchanged even
when Git reports clean status. After updating a local checkout, run the existing guarded refresh
and verification from the repository root, stopping on either failure:

```bash
python build/sql-confinement/checkout_bytes.py --refresh &&
python build/sql-confinement/checkout_bytes.py
```

In PowerShell, check `$LASTEXITCODE` immediately after **each** command and stop on nonzero.
The helper refuses genuine/staged edits; preserve those rather than forcing a checkout or staging
renormalized SQL. Do not change global Git settings. There is no production operator action, SQL
execution, binding reinstall, migration rerun, deployment or activation required by this merge.
Native Azure and live MCP acceptance gates retain their previous status.
