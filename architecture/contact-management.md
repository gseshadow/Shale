# Contact Management Architecture and Roadmap

## Decision and Phase 1A boundary

Contacts remain strict tenant-owned records. Phase 1A is an additive database foundation: it adds
structured-name storage, three independent customizable-definition domains, and three historical
assignment domains. It does **not** add Java models, DAO/service methods, Settings, Contact View,
display-name changes, or structured phone/email/address behavior. Existing `Name`, `FirstName`,
`LastName`, `WorkName`, `IsExpert`, and every current query continue unchanged.

## Model

`ContactTypes`, `Specialties`, and `CredentialDefinitions` follow the global/tenant overlay standard.
Each has a stable lowercase `SystemKey`, presentation fields, selection order, active/deleted state,
timestamps and actors, deletion metadata, `RowVer`, and nullable tenant ownership. A deleted definition
must be inactive and have both deletion timestamp and actor; a nondeleted definition has neither. The effective list
is global plus current tenant, with a same-key tenant definition winning. Inactive definitions cannot
be newly selected; soft-deleted definitions and assignments remain available for history.

The concepts are deliberately orthogonal:

* **Contact Type** is contact-wide classification (Expert, Attorney, Provider, Vendor, Witness).
* **Specialty** is an independent area of practice or expertise. A Contact need not be an Expert to
  have one, and Specialty is not a Contact/Contact Type column.
* **Credential** is a repeatable professional designation. A definition separates its full `Name`
  (for example, Doctor of Medicine) from its display `Abbreviation` (MD), while `SystemKey`
  (`doctor_of_medicine`) remains stable identity. Credentials are not name suffixes.
  `ContactCredentials.DisplayOrder` retains explicit presentation order. Duplicate active instances
  of one credential are unsupported; a future professional-license/jurisdiction model must be
  separate. `Contacts.Prefix` is an honorific, while `Contacts.Suffix` is limited to actual suffixes
  such as Jr., Sr., II, III, and IV.

Only the global `ContactTypes.SystemKey='expert'` definition is seeded. Additional global Contact
Types, Specialties, and Credentials remain intentionally unseeded pending product/domain review.
Credential definitions are not seeded in Phase 1A.

## Assignment lifecycle and tenancy

`ContactContactTypes`, `ContactSpecialties`, and `ContactCredentials` are explicit, strict
tenant-owned many-to-many tables. Each carries `ShaleClientId`, creation/update/deletion metadata,
soft deletion, and `RowVer`. Filtered unique indexes prohibit duplicate **active** relationships while
allowing removed relationships to remain. Whether restoration reactivates that row or inserts a new
historical row is deferred to the audited mutation-service phase; this schema supports either.

The composite `(ShaleClientId, ContactId)` foreign keys make a cross-tenant Contact assignment
impossible. Definition foreign keys preserve history. Because a definition may be global or tenant
owned, SQL Server cannot express “definition tenant is NULL or equals assignment tenant” as a foreign
key. All creation, update, and deletion actor columns reference authoritative `dbo.Users(id)`; nullable
creation/update actors support migration provenance, and deletion invariants require a deletion actor.
Because `Users.ShaleClientId` is nullable, these FKs preserve identity/history but do not authorize.
Every future assignment mutation service must transactionally validate the global-or-same-tenant
definition, effective active/nondeleted state, actor tenant authorization, and Contact ownership on the
same tenant-context connection and transaction before insert/restore. The
verification script detects inconsistent pre-existing rows. Definitions use
`sec.fn_FilterByTenantOrGlobal`; assignments use strict `sec.fn_FilterByTenant`, all attached to the
established enabled `TenantFilter` policy. The migration fails rather than inventing an RLS policy.

## Structured names and compatibility

Nullable `Prefix`, `MiddleName`, `PreferredName`, and `Suffix` columns are additive. No values are
derived or normalized in Phase 1A. Current name rendering and editing remain authoritative so this
deployment cannot alter visible Contact or Case behavior. A later cutover must define formatting,
fallback, parsing, credential punctuation, and organization-contact rules before using these fields.

## Legacy Expert bridge

The observed live baseline has 2,314 tenant-7 and 10 tenant-8 Contacts, no `IsExpert=1` rows,
so the initial backfill validly inserts zero assignments; these counts are observations, not schema
logic. The migration creates/fetches the authoritative global `expert` definition, then inserts one
active assignment for every `Contacts.IsExpert=1` row, including soft-deleted Contacts. Its `NOT EXISTS`
guard and active filtered unique index make reruns safe. It never changes `IsExpert`, never restores a
removed assignment, and does not infer the legacy flag from assignments. Before any read cutover, a
later release must dual-write the legacy flag and authoritative assignment transactionally, reconcile
drift, deploy assignment reads with compatibility fallback, monitor, and only then separately retire
`IsExpert` after all consumers are proven migrated.

## Case roles

The existing tenant-7 `PartyRoles.SystemKey=expert` row means Expert Witness in a particular Case;
the global `ContactTypes.SystemKey=expert` row classifies a Contact across cases. Both are legitimate,
nonconflicting concepts. Phase 1A modifies neither `PartyRoles`, `CaseParties`, nor `CaseContacts`.
Opposing Counsel, Supporting Counsel, Expert Witness, and Treating Provider are case-specific roles, not Contact Types. Phase 1A does not change runtime case-role behavior. Future work must use authoritative `CaseParties` plus `PartyRoles`
(including side where applicable) and must not introduce new reliance on legacy `CaseContacts`.

## Phased roadmap

1. **Phase 1A (this change):** deploy schema, RLS, conservative Expert seed/backfill, verification,
   and architecture contracts. No runtime reads or writes change.
2. **Phase 1B — read/domain contracts (implemented):** UI-free shared records and tenant-scoped
   effective-definition/classification-profile reads are exposed through `ContactServicePort`.
   Definition lists apply the established SystemKey overlay (tenant wins; a deleted tenant override
   resets to global fallback), and profile reads retain exact historical definition IDs. Current
   display-name and Expert reads remain legacy-authoritative.
3. **Phase 1C — transactional administration and assignment writes:** add admin-authorized Settings
   services and Contact assignment services with tenant/actor/definition validation, optimistic
   concurrency, same-transaction entity-action auditing, and `IsExpert` dual-write.
4. **Phase 2 — Contact experience:** expose classification, specialty, credential, and structured-name
   editing after accessibility and validation design. Migrate credential-like legacy suffix values only
   through reviewed, reversible data classification—not automatic parsing.
5. **Phase 3 — display cutover:** reconcile drift, switch display-name composition and Expert reads,
   retain fallback/telemetry, then remove fallback after compatibility acceptance.
6. **Phase 4 — retirement:** separately approve retirement of `IsExpert` and any obsolete name paths.
   Preserve assignment/audit history and keep Case roles on `CaseParties`/`PartyRoles`.

## RLS and audit boundary

Live `dbo.Contacts` currently has no TenantFilter predicate. Phase 1A deliberately does not attach
one; this existing architectural/security condition requires separate review. The six new tables each
have exactly one FILTER predicate on the enabled established TenantFilter policy. RLS is defense in
depth, never a substitute for transactional authorization. No mutation path or EntityActionAuditLog
allowlist change is included. Future mutation work must approve an entity/action vocabulary; restrict
metadata; record actor and tenant identity; enforce RowVersion concurrency; and audit add, remove,
restore, and update actions in the mutation transaction.

## Deployment, rollback, and operations

Phase 1A was deployed to production on 2026-08-24. The migration also completed twice on
`Shale_Copy`, with complete verification after both runs. Production verification found zero column,
structured-column, critical-default, lifecycle, actor-FK, tenant, duplicate-key, cross-tenant,
predicate, required-index, and composite-key semantic violations. It confirmed exactly one compatible
global Expert definition, zero legacy Expert Contacts and assignments, and exactly one enabled
TenantFilter policy. Contacts intentionally remain without an RLS predicate. No PHI, credentials, or
connection information is recorded here.

Phase 1B adds no SQL and assumes that verified deployed contract. Runtime reads explicitly restrict
Contact and assignment tenant identity on the tenant-context connection; RLS remains defense in depth.

Reruns validate every Phase 1A-owned required column and named index, foreign key, CHECK, default,
and RLS predicate by its contract. A conflicting object with a Phase 1A name or an incompatible
required column fails for manual review. Unrelated columns, indexes, foreign keys, and CHECKs added
by legitimate later additive phases are tolerated, so the foundation remains rerunnable.

The scripts use `USER_NAME()`—the current SQL Server/Azure SQL database principal identity—to reject
`shale_app` and `shale_runtime`; they do not use an application-name connection-string value. They
also require NULL `SESSION_CONTEXT(N'ShaleClientId')`, sysadmin or `db_owner` administrative
membership, and an explicit `@OperatorVerifiedAllTenantVisibility=1` acknowledgement after an
independent all-tenant visibility preflight. Role membership alone is not treated as proof that an RLS
predicate grants all-tenant visibility, and neither script changes permissions or principal state.

There is intentionally no destructive down migration. Transactional DDL rolls back on execution
failure; after a successful deployment, operational rollback is to leave the additive unused objects
in place and roll back application consumers. Dropping objects would discard assignment history and
is not an approved rollback. Before production, operators must confirm no unexpected partial tables,
the existing RLS predicate semantics, and sufficient log/lock capacity for the all-contact backfill.

## Open decisions (deferred, not blockers for Phase 1A)

* The reviewed set and presentation of additional global Contact Types and any credential defaults.
* Exact structured display-name/credential punctuation (the Phase 1A contract prohibits duplicate
  active instances of the same credential).
* Entity-action audit vocabulary/metadata for definition and assignment mutations.
* Whether assignment restore reactivates a historical row or inserts a new historical row; services
  must choose one consistent approach while preserving removal history.

## Phase 1C transactional mutation boundary (implemented)

Phase 1C reuses, rather than replaces, the established mutation conventions: `DbSessionProvider` supplies one tenant-context connection; `SESSION_CONTEXT(N'ShaleClientId')` is compared explicitly; `dbo.Users.id`, `ShaleClientId`, `is_deleted`, `IsRemoved`, and `is_admin` are the actor authorities; JDBC `autoCommit=false` encloses validation, mutation, `EntityActionAuditDao.append`, commit, and rollback; and `UPDATE ... WHERE RowVer=?` is the optimistic-concurrency guard. Immutable shared-port commands defensively copy RowVer bytes. SQL-generated authoritative IDs and the post-mutation RowVer are returned. These are the same tenant/actor, transaction, audit, and RowVer patterns used by Case Date Type and User administration.

Definition creation, update, activation/deactivation, removal, and restoration require a positive tenant and actor, matching tenant session, and an active, nonremoved, nondeleted same-tenant administrator. Global rows are never mutable. A custom lowercase snake_case key is rejected if it would shadow a global key; an override must name a global authoritative ID and reuse that row's exact key. Ordinary update cannot submit or change SystemKey. Removed overrides fall back to global reads; inactive overrides continue masking globals. Removal is soft and never cascades assignments. Update, lifecycle, and restore require ExpectedRowVer. Restore updates the same row and preserves its ID.

Assignment mutations require the same tenant-session validation and an active, nonremoved, nondeleted same-tenant actor, but do not require administrator status. The Contact must be active and tenant-owned. New and restored assignments accept only the authoritative ID of an active, nondeleted definition that is global or same-tenant and currently effective for its SystemKey; a shadowed global ID is rejected. Remove and restore bind assignment ID, tenant, Contact, and ExpectedRowVer. Restore reactivates the same row and rejects a competing active assignment. Removed rows remain history.

Credential creation appends after the maximum active DisplayOrder unless a nonnegative order is supplied. Bulk reorder requires exactly the complete active assignment-ID set, with no duplicates and one ExpectedRowVer per row. The transaction rejects missing, removed, foreign, or stale rows and writes contiguous zero-based order. One `CONTACT_CREDENTIAL/REORDERED` audit event records only Contact ID and ordering count.

The audit vocabulary is `CONTACT_TYPE`, `SPECIALTY`, and `CREDENTIAL_DEFINITION` with `CREATED`, `OVERRIDE_CREATED`, `UPDATED`, `ACTIVATED`, `DEACTIVATED`, `REMOVED`, and `RESTORED`; `CONTACT_CONTACT_TYPE` and `CONTACT_SPECIALTY` with `ADDED`, `REMOVED`, and `RESTORED`; and `CONTACT_CREDENTIAL` with those assignment actions plus `REORDERED`. Metadata is restricted to authoritative Contact/definition IDs, active state, and ordering count. Names, abbreviations, descriptions, contact data, RowVer, and all PHI are prohibited. The allowlist migration must deploy before the application release and its read-only verification must run afterward.

`Contacts.IsExpert` remains the runtime read authority. Adding or restoring an assignment whose authoritative definition key is `expert` sets it true in the assignment transaction. Removing such an assignment recomputes it from every remaining nondeleted assignment joined by authoritative SystemKey, including assignments whose definition later became inactive or removed. Definition lifecycle alone never rewrites the flag or removes assignments. Repository inventory found no existing runtime Java path that writes `IsExpert`; only the Phase 1A migration backfill reads it, so Phase 1C adds no unused reverse legacy mutation API.

Phase 1C contains no Contact View or Settings UI and changes no JavaFX, FXML, or CSS. It does not mutate `CaseParties`, `PartyRoles`, or `CaseContacts`. Phase 2 still must decide interaction design, accessibility, validation presentation, structured-name editing, and credential punctuation; directory search, case-role UI, structured phones/emails/addresses, display cutover, and legacy retirement remain deferred.
