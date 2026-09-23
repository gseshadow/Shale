# Firm-wide roles

## Phase 1A foundation (2026-09-23)

Firm-wide roles are tenant-wide eligibility identities. They are deliberately independent of
`CaseUsers`, `CaseTeamRoleDefinitions`, and `CaseTeamMemberRoles`: membership in a Case Team is
neither required nor consulted by a firm-wide eligibility decision.

`FirmWideRoleDefinitions` owns tenant-scoped, stable role identities and lifecycle state.
`UserFirmWideRoleAssignments` supports multiple active custom role memberships per user. Both
relationships carry `ShaleClientId`, use composite same-tenant foreign keys, and are protected by
the strict `TenantFilter`/`sec.fn_FilterByTenant` predicate. Assignment creation/removal actors are
same-tenant users through composite foreign keys; future mutation services must additionally
validate the session tenant and an active, nonremoved administrator before writing.

### ADMIN and ATTORNEY compatibility

Every tenant receives `ADMIN` and `ATTORNEY` definitions. These definitions provide stable IDs for
future policy references, but they do **not** replace or duplicate authorization authority:

* `ADMIN` eligibility reads `Users.is_admin`.
* `ATTORNEY` eligibility reads `Users.is_attorney`.
* The assignment table is only authoritative for tenant-defined roles. A database trigger rejects
  assignment rows for either built-in role.

Consequently existing login payloads, User Management writes, last-active-administrator protection,
and all existing authorization checks retain their current meaning. There is no flag backfill and no
second built-in role state to drift. A future role-management phase may add audited custom-definition
and assignment mutations, but must not move built-in authority without a separately reviewed cutover.

### Authoritative eligibility operation

`UserServicePort.currentActorHasFirmWideRole(tenant, definitionId)` is the single backend operation.
The DAO verifies SQL session tenant context, obtains `PrincipalUserId` from session context, requires
the actor to be active and not removed, requires an active/nondeleted same-tenant definition, and
then checks the appropriate legacy flag or active custom assignment. It reads no authentication DTO,
UI session role list, token claim, or Case Team table. Inactive, removed, foreign-tenant, missing, or
removed-role inputs return false.

### Lifecycle and audit review

Definitions and assignments use soft-deletion metadata and `RowVer`; active assignment uniqueness is
enforced by a filtered index. Phase 1A exposes no definition or assignment mutation API, so it adds no
entity-action audit vocabulary and emits no runtime audit event. The migration's deterministic system
seeds have no human actor. A later management phase must transact actor validation, mutation, and an
allowlisted entity-action audit append on one connection before commit. Eligibility is a nonsensitive
authorization predicate rather than a PHI/value view and is intentionally not audited.

### Deployment and verification

1. In a fresh query under the approved `sysadmin`/`db_owner` migration principal—not
   `shale_app` or `shale_runtime`—leave both `ShaleClientId` and `PrincipalUserId` session context
   unset. Run verification in `ALL_TENANT` mode with its acknowledgement left `0`. Record the visible
   tenant inventory/count and inspect the enabled `TenantFilter` predicates plus deployed predicate
   functions independently.
2. Reconcile that output with an independently approved tenant inventory. Only then set the exact
   database name, independently obtained expected tenant count, and operator acknowledgement on a
   reviewed execution copy. A mismatch or hidden tenant blocks execution before the transaction.
3. Manually apply `docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql` in that same kind of
   fresh all-tenant administrative session. The checked-in defaults cannot mutate production.
4. Rerun `docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a_verify.sql` in authoritative
   `ALL_TENANT` mode with the approved values. The visible and expected tenant counts must match;
   missing tables, missing/inactive/deleted built-ins, built-in assignment violations, cross-tenant
   violations, duplicate active assignments, and missing/inexact RLS predicate counts must all be
   zero. Optional `TENANT` mode is explicitly non-authoritative and reports only the matching session
   tenant; it cannot establish deployment completion.
5. Deploy the application. Do not deploy application code first because its eligibility query expects
   both new tables.

The migration is forward-only, transactional, guarded, and rerunnable. On rerun it inventories the
exact required columns, trusted constraints and their relationships, default constraints, index keys
and filters, enabled built-in guard trigger behavior, and exact strict RLS predicate. Familiar names
with incompatible definitions fail closed. The binary-collated `SystemKey` check enforces actual
uppercase ASCII keys regardless of the database's default collation. The migration must not be
executed by the application or as part of automated tests.

## Next-phase constraints

A later field-confirmation design must preserve a confirmation as historical fact: changing a form
policy must not silently turn an already confirmed value into pending. Policy identity/versioning or
an explicit prospective-effective rule must make that behavior visible.

The design should prefer stable `(ShaleClientId, FormKey, FieldKey)` policy references. Do not rewrite
`FormConfigurationDao.replace` merely to stabilize generated row IDs unless analysis demonstrates
that those stable tenant/form/field keys cannot safely express policy identity, history, concurrency,
and replacement behavior.

## Phase 1B administration (2026-09-23)

Tenant administrators can create, rename, activate/deactivate, and soft-delete tenant-defined firm-wide role definitions and can add, remove, and explicitly restore user assignments. Every operation verifies the requested tenant and actor against SQL session context, requires an active nonremoved administrator, applies row-version concurrency to existing rows, and appends an entity-action audit event on the mutation transaction before commit. Definition deletion never deletes or rewrites assignment history; inactive or deleted definitions simply cease to grant eligibility.

User Management reads built-in membership from `Users.is_admin` and `Users.is_attorney` and reads tenant-defined membership from active `UserFirmWideRoleAssignments`, allowing several coherent roles per user. The assignment APIs reject built-in definitions, reinforced by the Phase 1A trigger. No operation reads or writes `CaseUsers`, `CaseTeamRoleDefinitions`, or `CaseTeamMemberRoles`; Case Team administration remains independent. Eligibility remains a nonsensitive authorization predicate and is intentionally not read-audited. Administrative reads expose role identity/lifecycle and assignment identity only and are not PHI reads; all definition and assignment mutations are entity-action audited without names, row versions, or DTO payloads.

Exact deployment order:

1. Apply and verify `docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql` as documented above (if not already deployed).
2. Apply `docs/sql/2026-09-23_firm_wide_roles_audit_allowlist_phase1b.sql` manually with an approved database migration principal.
3. Run the read-only `docs/sql/2026-09-23_firm_wide_roles_phase1b_verify.sql`; every `FindingCount` must be zero.
4. Deploy the Phase 1B application binaries. Application-first deployment is unsupported because audit inserts require the new allowlist values.

Phase 1B does not implement field-confirmation policy, Case Date confirmation, case-attention badges, notifications, or any Case Team role/assignment mutation.
