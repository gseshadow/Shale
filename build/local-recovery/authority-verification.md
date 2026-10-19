# Authority correction verification

Base: `46f63323498e04f4afaf49f2c94b59153bcad658` (`origin/codex/latest`).
Executed on Linux, 2026-10-19. **Manual recovery NOT MEASURED; Windows execution
unverified.** Brian's reported failed Windows attempt/original baseline and completed
password/remembered/logout/reboot evidence are retained and are not new executions.
No Azure, production mutation, retirement authorization or broad test suite was run.

## Review and test impact

Reviewed AGENTS/prompt rules, database-schema, tenancy-and-rls, system-overview, the
owning auth-module-permission-cutover runbook, OPERATOR/remaining acceptance and the
local README, verification, single-PC continuation, Sandbox, startup and session
launchers. Inspected all authority snapshot/diagnostic/decision/effective checks,
canonical legacy modules, full Apply/rollback context/fingerprint/bodies/approvals,
ControlledRetirementCompatibilityLocalSqlServerTest and AuthPermissionCutoverFixture,
existing controlled-retirement Python contracts, ContractChecks, StartupChecks,
test_launcher.ps1 and session test-update.ps1 before implementation.

Changed contracts and directly relevant checks:

| Surface | Verification |
| --- | --- |
| Sandbox ownership and launch | AuthorityChecks calls the exact launch-owner and actual login/catalog/non-bypass helpers before/during/after retirement; wrong nonce/catalog and changed nonce refusal; original tenant filter with nonempty fixtures; admin/injection child-environment scrubbing. StartupChecks verifies unchanged network/profile/process isolation. |
| Existing-state SQL correction/inspector | Real SQL baseline, all auth/runtime findings, UNKNOWN/additional auth/additional runtime refusal, full expected ACL minus one row and OtherHash enforcement; unchanged guarded Apply failure/success and exact rollback; application/ownership row digests and extended properties identical. |
| Session launcher additive overlay | Existing test-update.ps1 extended for every authority receipt parent/revision/state binding; original inventory/path/tamper/transport assertions retained. Existing test_launcher.ps1 checks original and startup manifest binding, PS5.1 array shape and child-only secure prompt. |
| Windows continuation | test_authority_continuation.ps1 parses every command block and runs actual payload generation with synthetic local records; generated script parses, binds the recorded corrected baseline and preserves original result bytes. It executes no exercise/SQL. |
| Unchanged retirement controls | Four existing test_controlled_retirement.py contracts pass; Git equality confirms production SQL and all application production sources unchanged. |

Audit compatibility: the correction is a local sandbox security administration action,
recorded in private inspection/digests/attempt marker/commit result and existing operator
change control. It reads only catalog/ownership evidence and changes one local ACL row;
no application PHI/domain/session/credential row, entity-action audit schema/event,
production audit grant or timeline surrogate is introduced. Existing application audit
behavior is unchanged. Synthetic test row snapshots are not a production audit/read.

## Real SQL result

Fresh loopback-only SQL Server 2022 Developer container, image digest
`sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`.
Only new synthetic resources were created. All resources and failed development probes
remain retained; no existing Windows database/evidence was accessed or SQL retried there.

The representative schema contains Users/AuthConfig, old remembered resolver/session
shapes, all six sandbox write-target tables, an audit table, Statuses and a tenant
filter with real other-tenant data. It applies the original auth metadata DENY,
Users SELECT, three exact canonical legacy module grants, old resolver grant, both
original ownership SELECTs and the actual Sandbox.provision runtime permission recipe
(column projection/update, table reads, six table writes, audit INSERT, schema EXECUTE
and exported database EXECUTE). It does not reconstruct the unavailable private
96-table export. Original Windows baseline identity is never substituted in this test;
a new synthetic catalog has its own independent baseline.

Final retained resource: `ShaleRetirementExercise_fc67b1f8bc414f79ab06a950b4fddbcf`.
Full diagnostic evidence: `work/authority/synthetic-fc67b1f8bc414f79ab06a950b4fddbcf.json`.
Result log: `work/authority/sql-checks-data-reviewed.log` (private scratch, not committed).

* Original: 210 complete auth checks, 205 zero and exactly five granted; 44 runtime
  checks all zero. No UNKNOWN. Independent extra-auth, extra-runtime and NULL-evidence
  negative controls refuse; NULL serialization is explicitly retained.
* Actual unchanged controlled Apply: SQL 58307; COMPLETE returned findings are exactly
  Ownership object/Nonce SELECT, not just an excerpt. Connection rollback restores
  the complete original synthetic catalog hash.
* Local correction: only auth's original dbo SELECT row removed. Full ACL EXCEPT and
  unchanged OtherHash pass. Original nonces/properties and application row digests
  remain exact. No metadata permission, helper module or application credential granted.
* Corrected: 210 auth checks, 207 zero and only the three legacy EXECUTEs; 44 runtime
  checks all zero. Actual auth/runtime SQL-login connections pass the same ownership/
  identity preconditions used at launch; auth SELECT truly denies with SQL 229.
* Full unchanged guarded controlled Apply commits; real-login/nonce/tenant checks pass
  while retired. Full unchanged guarded controlled rollback commits and read-only
  baseline equality restores the exact **corrected** catalog; the same checks pass
  afterward. All original data/property digests remain equal.

Synthetic hashes (evidence only, never Windows expected values):

| State | SHA-256 catalog identity |
| --- | --- |
| Original | `0x209E60B87DFABB9D8A643F42CF62EAB3FBF226B3F6AA550AFB0B5A2884F88A76` |
| Corrected | `0x99B0FD540E12585B03DC985A3CE1A114115C83C18702ACD9F581E95B74586D1F` |
| Retired | `0x01216ED1412869C1207D5B231D23752A953ACC30C9784E76F9B706A10B9A2DC0` |

## Commands/results and boundaries

Pinned application compilation, not application test execution:

```text
git worktree add --detach work/authority/pinned 59d8374858764788c621f6f8d986773b58ab79c4
/workspace/.tools/bin/mvn -B -f work/authority/pinned/pom.xml -pl shale-desktop,shale-server -am -DskipTests install
```

PASS. This supplies the actual pinned class signatures/dependencies, not today's
application build. Initial compilation against the stale preexisting target classes
exposed incompatible method signatures; those targets were not used as the pin.

Focused tools (JDK 21.0.12.1, Core 7.4.6):

```text
javac --release 21 -proc:none -implicit:none -cp <pinned-classes-and-dependencies> -d work/authority/classes build/local-recovery/*.java build/local-recovery/substitutes/*.java
javac --release 21 -proc:none -implicit:none -sourcepath build/local-recovery -cp <tool-classes-and-pinned-classpath> -d work/authority/staged-classes build/local-recovery/Sandbox.java build/local-recovery/AuthorityCorrection.java
java -cp <staged-classes-first:tool-classes:pinned-classpath> AuthorityChecks /workspace/Shale
java -Djava.security.manager=allow -cp <same-classpath> StartupChecks
pwsh -NoLogo -NoProfile -File build/session-utc-update/test-update.ps1
pwsh -NoLogo -NoProfile -File build/local-recovery/test_launcher.ps1
pwsh -NoLogo -NoProfile -File build/local-recovery/test_authority_continuation.ps1
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest build/auth-verification/test_controlled_retirement.py -v
git diff --check
git diff --exit-code origin/codex/latest -- docs/sql/auth/permission-cutover shale-core/src/main shale-data/src/main shale-desktop/src/main shale-ui/src/main shale-server/src/main
```

All PASS. AuthorityChecks requires explicit `SHALE_AUTH_VERIFY_OPT_IN=LOCAL_DISPOSABLE_AUTH`,
loopback port, dedicated synthetic admin and generated test login credentials through
private child environment. Compilation/SQL orchestration and logs are retained under
work/authority. Core commands use task-local XDG_CACHE_HOME/XDG_CONFIG_HOME/XDG_DATA_HOME.
Windows/native PowerShell 5.1/ODBC, original private export/state and full GUI/service
launch are unavailable here. Tests verify the actual launch preconditions and child
secret filtering, not a new GUI/session/password/reboot acceptance. The finite
[Windows continuation](authority-recovery.md) is the remaining native validation.

The original Windows hash remains permanently associated with the failed attempt.
The new correction result records its observed local successor baseline, never a
replacement for prior evidence or canonical module hashes. Only a subsequent separately
reviewed new exercise may measure manual recovery; no pass/timer was fabricated.

Final changed-file/selector review:

```text
python3 build/test-selection/select_tests.py --base 46f63323498e04f4afaf49f2c94b59153bcad658 --head HEAD --format markdown --plan-output work/authority/selection.json
git diff --check 46f63323498e04f4afaf49f2c94b59153bcad658 HEAD
```

PASS. Selector maps these local-tool paths to no Maven area; it emits generic critical/
full-suite suggestions because the paths are unknown. Those unrelated broad suites
were not executed under the user's explicit scope. Focused real SQL, isolation,
launcher/receipt/transport and payload-generation checks cover every changed contract.
No broad Maven-suite or Windows/GUI acceptance pass is inferred. Final source search
confirms ownership SELECT for auth exists only in the negative-control fixture and
reviewed removal; production retirement SQL remains byte-for-byte unchanged.
