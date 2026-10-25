# Phase 1R targeted read candidate

**Local candidate; production activation BLOCKED.** The owning evidence and deployment gates are in the
[Case-read runbook](../../testing/case-read-local-security-verification.md#phase-1r-candidate--2026-10-21).
This is not the June broad proposal. No functions, grants, data, table definitions or indexes change.

The only RLS additions to existing enabled, schema-bound `sec.TenantFilter` are:

| Table | FILTER function and argument |
| --- | --- |
| `dbo.Users` | `sec.fn_FilterByTenant(ShaleClientId)` |
| `dbo.auditLog` | `sec.fn_FilterByTenant(ShaleClientId)` |
| `dbo.Statuses` | `sec.fn_FilterByTenantOrGlobal(ShaleClientId)` |

`preflight.sql`, `apply.sql`, `postcheck.sql` and `rollback.sql` include the same guarded single-batch
[engine](engine.sql). Run SQLCMD from the repository root with `-b -V 16`, one connection per entry point.
Includes contain no GO; errors roll back the entire owned transaction. Ambient transactions are refused.
An exclusive transaction application lock serializes this candidate's entry points. Coordinate all other
policy/schema maintenance; that lock does not fence unrelated administrative DDL.

Required review inputs (no defaults, credentials or destination auto-adoption):

| SQLCMD variable | Required independently reviewed value |
| --- | --- |
| `ExpectedDatabase` | Exact disposable database name for local review; eventual production identity needs separate authorization. |
| `ReviewedCollation` | Database identifier collation; preserve schema/column collations separately in offline artifacts. |
| `ReviewedStrictHash` | SHA-256 of exact reviewed `sys.sql_modules.definition` for strict function, UTF-16LE bytes, hex without `0x`. |
| `ReviewedOverlayHash` | Same for tenant-or-global function. |
| `ReviewedOtherPredicatesHash` | SHA-256, UTF-16LE, of the ordered JSON inventory below from the reviewed destination baseline. |

Review complete function bodies, schema/object ownership and dependencies, not names/hashes alone.
The missing original artifact prevents supplying authoritative definition hashes in this PR. Synthetic
fixture bodies/hashes must never become production review inputs. Undefined variables must stop execution.
Keep any values supplied to SQLCMD confined to reviewed identifier/collation/64-digit hex strings; never
interpolate untrusted text. Authentication uses an existing approved local mechanism, never `-P`.

Read-only inventory expression for review (not a command to collect production state in this task):

```sql
SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(
 SELECT p.* FROM sys.security_predicates p
 WHERE p.target_object_id NOT IN(OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
 ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH)),2) AS OtherPredicatesHash;
```

Use the reviewed values for all four entry points, including rollback. The unrelated inventory hash is
bound to actual object/predicate IDs in that database, not portable across fresh imports. Re-review the
local imported inventory against the offline dependency manifest before generating its local fingerprint.
Do not refresh a hash to make a failed drift check pass. The engine also checks exact target names using
binary comparisons, effective dbo ownership, int tenant columns, inline schema-bound caller functions,
and the exact FILTER catalog expression. Any target BLOCK, other policy or different function/column
fails closed. Disabled/unknown policy/function state is rejected. Already matching coverage is preserved.
Conditional ADD statements use fixed dynamic batches because SQL Server binds security DDL before
branch execution; no runtime input is inserted into their SQL.

## Exact rollback

Successful additions atomically write `sec` schema extended property **`Shale.Phase1R.v1`**. This small
receipt records database GUID, policy ID/creation time, reviewed function/inventory hashes, complete
before/after predicate hashes and only the added target/predicate IDs. It is migration metadata, not an
application table, user audit event or grant. Retain it with the database until rollback is no longer
needed. Protected metadata authority is required; it cannot defend against a malicious administrator
rewriting both the policy and receipt. Preserve a private original change-control copy for later review.

Using the same reviewed SQLCMD variables/environment and the existing approved **local** authentication
mechanism, the entry-point invocations from repository root are:

```text
sqlcmd -b -V 16 -i docs/sql/phase1r/preflight.sql
sqlcmd -b -V 16 -i docs/sql/phase1r/apply.sql
sqlcmd -b -V 16 -i docs/sql/phase1r/postcheck.sql
sqlcmd -b -V 16 -i docs/sql/phase1r/rollback.sql
```

Supply the five required review variables through SQLCMD `-v` or its reviewed environment; the examples
omit connection/variable values deliberately. Undefined inputs fail. No production execution is authorized.

Run `rollback.sql` with the original review inputs. It requires the exact recorded after state, drops
only recorded additions, verifies the original full predicate hash, then removes only its own receipt.
It never disables/recreates a policy, changes functions/grants or touches rows. Repeat rollback without
a receipt is a no-op after normal prerequisite checks. Missing receipt means **no ownership claim**:
never infer which matching predicates to remove. Partial pre-existing matches survive; all-pre-existing
coverage creates no receipt and both apply/rollback are true no-ops. Catalog/receipt drift stops rollback
for separate review; do not delete or edit the receipt to bypass it. Any partial rollback failure restores
all predicates and receipt atomically.

## Offline schema evidence

**Recovered-input execution, 2026-10-24:** source hash gate and hardened authoritative Case/RLS
scenarios now pass. See the [resumed acceptance ledger](../../testing/phase3b7-adversarial-acceptance.md#resumed-authoritative-acceptance--recovered-input-2026-10-24)
for independent source/derived/candidate hashes, full dependency/owner qualifications, runtime permission
failures and the unresolved serving expiry drill. 3B.7 is FAILED, with no real enablement recommendation.
The importer has bounded SSMS/ToBitNV compatibility support and derived schemas explicitly assign dbo
schema ownership; runtime must not retain schema CONTROL/policy ALTER after temporary db_owner removal.
Source bytes and both derived SQL files remain outside Git/PR artifacts. Only the three existing FILTER
changes are installed in the local candidate. The unavailable-input statements below are historical.


Original source SHA-256 recorded by the previous assessment:
`4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda`.
The original `shale-schema-source.sql` (UTF-16LE, 728,862 bytes) is unavailable in this task's workspace.
It was not modified. **Derived artifact hash: unavailable. Candidate schema artifact hash: unavailable.**
The migration source is reviewable separately; its Git blob identity is not a candidate-schema hash.

Dependency inventory remains the [runbook's selected dependency table](../../testing/case-read-local-security-verification.md#required-authoritative-schema-input-not-supplied-by-this-repository):
ShaleClients, Users, Cases, CaseStatuses, Statuses, PracticeAreas, CaseUsers, Roles, CaseDates, CaseDateTypes,
CaseDateSemanticRoles, CaseDateTypeSemanticRoleMappings, auditLog/ObjectTypes, both sec functions and all
transitive FK/type/computed/default dependencies (including `dbo.ToBitNV` where retained), schema owners,
constraints, indexes, column/database collations and existing selected predicates. No dependency was
invented or loosened. This required inventory is not a newly verified transitive inventory: the absent
source prevents completing that proof. No derived export was generated.

When the approved source is available, preserve source bytes/hash, record a separate reviewed UTF-8 derived
hash and dependency manifest, apply only this candidate to an owned local clone, and export a separately
labelled final-state candidate with its own hash. Verify the exact three-FILTER difference. Any necessary
scalar-function/importer support remains a separately bounded, tested change; current importer is unchanged.
Run the Case launcher with the candidate schema hash/provenance. Until then authoritative Case SQL is BLOCKED.

## Synthetic acceptance

From the repository root, on a **new dedicated loopback SQL Server**:

```bash
python3 build/phase1r-verification/run.py --opt-in LOCAL_DISPOSABLE_PHASE1R --port 14333
```

Enter only the dedicated local administrator credentials privately. The fixture creates random marked
resources and two new unprivileged SQL logins; cleanup requires the exact nonce and original login SIDs.
No arbitrary URL, existing database name, production app environment or operator packet is accepted.
The quiet runner requires all six fixed PASS lines, successful cleanup and exit zero. It reuses the
existing auth launcher's tested environment/Maven boundaries. No retirement/recovery scripts run.
Auth tables are explicitly synthetic; real session/remembered foundation migrations and current production
auth/controllers/JWT/session DAOs are reused. This evidence never represents authoritative Case acceptance.
