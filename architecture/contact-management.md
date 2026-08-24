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
2. **Phase 1B — read/domain contracts:** add UI-free DTOs and effective overlay reads; specify name
   formatting and historical-reference behavior. Continue all legacy reads.
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

Deployment order is: (1) deploy this application/documentation commit, which remains compatible with
both schemas; (2) back up and run the migration once using an approved all-tenant migration principal
with an unset tenant session context; (3) run the read-only verification script with the same
all-tenant visibility; (4) investigate every nonzero “expect 0” result and confirm all six RLS rows;
(5) deploy future runtime phases only after verification. The SQL in this repository has **not** been
executed against a database.

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
