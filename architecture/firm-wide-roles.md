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

1. Back up and review prerequisites, then manually apply
   `docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql`.
2. Run the separate read-only
   `docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a_verify.sql`; every finding/violation count
   must be zero.
3. Deploy the application. Do not deploy application code first because its eligibility query expects
   both new tables.

The migration is forward-only, transactional, guarded, and rerunnable. It must not be executed by the
application or as part of automated tests.

## Next-phase constraints

A later field-confirmation design must preserve a confirmation as historical fact: changing a form
policy must not silently turn an already confirmed value into pending. Policy identity/versioning or
an explicit prospective-effective rule must make that behavior visible.

The design should prefer stable `(ShaleClientId, FormKey, FieldKey)` policy references. Do not rewrite
`FormConfigurationDao.replace` merely to stabilize generated row IDs unless analysis demonstrates
that those stable tenant/form/field keys cannot safely express policy identity, history, concurrency,
and replacement behavior.
