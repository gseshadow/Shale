# Organization Management Architecture and Roadmap

## Purpose

Modernize Organizations to the standard established by Contacts while preserving Shale's existing
tenant, service, navigation, card, and case-party architecture. This document records the verified
repository baseline, the intended product model, and the phased implementation boundary.

## Verified repository baseline

### Database and model

`dbo.Organizations` is a strict tenant-owned entity with `Id`, `ShaleClientId`, one nullable
`OrganizationTypeId`, `Name`, one Phone/Fax/Email/Website, one structured postal address, Notes,
soft deletion, timestamps, and `RowVer`. The Java `Organization` model represents those legacy
columns directly.

`dbo.OrganizationTypes` has now been modernized by the Phase 1A migration contract. Current runtime
code still reads only `OrganizationTypeId` and `Name`; the new overlay fields and RLS are database
foundation rather than a runtime read cutover. `OrganizationDaoOrganizationTypeQueryTest` therefore
continues to protect the unchanged legacy query shape.

No structured Organization phone, email, address, or website child tables exist in the repository.
The equivalent Contact tables are `ContactPhoneNumbers`, `ContactEmailAddresses`, and
`ContactAddresses`. They provide strict tenant ownership, one active primary value per category,
ordering, soft-delete history, actor metadata, timestamps, and `RowVer`.

### Persistence and services

`OrganizationDao` owns directory paging, full detail loading, create, update, soft delete, type reads,
and several legacy case-link operations. Its active list/detail queries use explicit Organization
tenant predicates and soft-delete filters.

Current create and update persistence is materially behind the Contact aggregate pattern:

* `NewOrganizationController` calls `OrganizationDao.create` directly.
* `OrganizationController` performs direct DAO field updates.
* `OrganizationServicePort` exists, but JavaFX does not use it as the mutation boundary.
* `actorUserId` exists on service commands but is not used by the adapter/DAO mutation.
* Update does not bind `Organizations.RowVer` and therefore has no optimistic-concurrency guard.
* Organization create/update does not append transaction-bound entity-action audit events.
* The adapter reloads the current Organization and copies it into a whole-row legacy update.
* `NewOrganizationController` performs type loading and create on the JavaFX thread.

`OrganizationServiceAdapter#createOrganization` selects the first configured Organization Type when
used by server/API flows. The desktop create flow instead requires an explicitly selected type.
This disagreement must be removed by the aggregate service design.

### UI surfaces

The main directory is `organizations.fxml` plus `OrganizationsController`. It uses debounced,
generation-guarded, 100-row paged loading and `OrganizationCardFactory.FULL`. The directory query is
lightweight, but supports only name filtering and returns one legacy type and one set of legacy
contact values.

The reusable `OrganizationCardFactory` and `OrganizationCard` already support FULL, COMPACT, and MINI
variants and are used by the main directory, Global Search, Case surfaces, intake drafts, requested-from
workflows, and material workflows. The existing factory must be extended rather than replaced.

`organization.fxml` plus `OrganizationController` renders a two-column detail screen with one field
per row, pencil-button single-field dialogs, and Related Cases. It has no Organization classification
chip group, no related-contact section, and no clickable phone, email, website, or address actions.
It maintains static detail and type-option caches and supports live-update invalidation.

`new-organization.fxml` plus `NewOrganizationController` is a separate create form, so Add and Edit do
not share one staged aggregate editor. It uses the enhanced narrative control for Notes but otherwise
uses legacy scalar controls.

Contact clickable actions already exist in `ContactExternalActions` and `ExternalBrowserHelper`.
Contact classification presentation already exists in `ContactClassificationChipGroup`. These should
be generalized or reused through entity-neutral inputs rather than copied into Organization-specific
utilities.

### Relationships

Organization detail related-case reads already flow through
`CaseSummaryDao.listActiveRelatedToOrganization`, backed by authoritative `CaseParties`, Party Roles,
side, primary state, and notes. This must remain the only relationship authority.

`OrganizationDao` still contains legacy `CaseOrganizations` link/unlink and linkable-case methods.
They are compatibility debt and must not be used by the redesign. Soft deletion currently removes
matching `CaseParties` rows in the Organization DAO transaction; this destructive relationship
behavior needs explicit review in a later lifecycle phase and is outside the classification foundation.

`Contacts.OrganizationId` is an existing legacy Organization reference. The redesign must inventory
its active consumers before deciding whether the Organization View should expose related Contacts or
whether a richer employment/affiliation model is warranted. It must not create a second relationship
authority accidentally.

`CaseLinkShares` records shares with Contacts only. The Organization redesign does not add an
Organization Shared Links section unless a separate product decision expands that domain.

## Product decisions

### Organization Types

Organizations may have multiple color-coded, searchable Organization Types. Organization Type is an
organization-wide classification, not a case role. Examples may include hospital, medical practice,
law firm, insurer, government agency, court, expert firm, records vendor, and pharmacy, but global
seeds must be chosen from reviewed live data rather than assumptions.

Modernize the existing `OrganizationTypes` table to the global/tenant overlay standard if the live
inventory proves that migration compatible. Do not create a parallel `OrganizationTypeDefinitions`
table merely to obtain a cleaner name.

Add a strict tenant-owned historical assignment table named `OrganizationOrganizationTypes`, subject
to live identifier-length and naming verification. Backfill each Organization's legacy
`OrganizationTypeId` as an active assignment. Preserve `Organizations.OrganizationTypeId` as a
compatibility primary-type bridge until every reader and writer is proven migrated. Assignment
mutations must transactionally maintain that compatibility value during the bridge period.

Unknown custom types are presentation-only and receive no special workflow behavior.

### Contact points

Organizations support multiple ordered phones, emails, websites, and addresses, with no more than one
active primary value in each category. Fax is a phone kind rather than an unrelated scalar domain.

Recommended organization-specific child tables are:

* `OrganizationPhoneNumbers`
* `OrganizationEmailAddresses`
* `OrganizationWebsites`
* `OrganizationAddresses`

Each is strict tenant-owned and historical, with Organization ownership enforced by a composite
tenant foreign key, normalized/search values where appropriate, kind/purpose, primary state,
ordering, lifecycle actor metadata, timestamps, and `RowVer`.

Do not use a polymorphic entity/contact-point foreign key. Reuse the Contact contracts and UI
composition patterns while retaining enforceable Organization foreign keys.

Suggested kinds are organization-specific and must be reviewed before constraints are finalized:

* phones: MAIN, RECORDS, BILLING, FAX, OTHER
* emails: MAIN, RECORDS, BILLING, OTHER
* websites: MAIN, PORTAL, RECORDS, OTHER
* addresses: MAIN, RECORDS, BILLING, SERVICE, REGISTERED_AGENT, OTHER

Legacy scalar values remain compatibility/history inputs during migration. Ambiguous or invalid data
is preserved rather than silently discarded or guessed.

### UI

Use one shared staged aggregate Organization editor for both Add and Edit. Cancel or window close must
perform no mutation. Save uses one service command and one database transaction.

Extend the existing Organization card factory to accept a list of type presentations plus primary
contact-point summaries. Use the existing classification chip visual language with accessible color
contrast. Child actions must consume their events so calling, emailing, opening a website, or opening
an address does not also navigate the parent card.

The Organization View header shows the Organization name and all assigned type chips. The main body
groups contact information into readable cards with Call, Email, Open Website, and Open in Maps
actions. Related Cases remains a separate sibling section using the existing `CaseCardFactory` and
relationship metadata wrapper.

### Phase 2B.1 — dedicated Organization editor (implemented)

The Organization detail view is permanently read-only. Its authorized **Edit Organization** action
opens a fresh, window-modal editor owned by the application window; it never swaps detail labels for
hidden or inline form controls. The bounded, scrollable modal follows the Edit Contact dialog language,
keeps semantic Save and Cancel actions in its stable dialog footer, and contains the existing reusable
`OrganizationTypeAssignmentPane` alongside all legacy scalar Organization fields.

Each opening loads authoritative Organization details, effective type definitions, the complete assignment
profile, the Organization `RowVer`, and assignment `RowVer` values away from the JavaFX application thread.
Compatibility consistency is validated while constructing the dialog-local
`OrganizationTypeAssignmentStage`. Scalar and assignment edits remain staged locally until one Save delegates
to the Phase 2B atomic aggregate update, which preserves compatibility-primary synchronization and writes its
existing transaction-bound audit records. A successful result closes the modal, invalidates the detail cache,
refreshes the read-only view once, and publishes the established post-commit live update. A genuine concurrency
conflict keeps the modal open and reloads authoritative state before Save is re-enabled; other validation or
persistence failures retain the staged editor without exposing database error text.

Cancel and an accepted window-close discard both scalar and assignment staging without persistence, audit, or
live-update effects. Closing an unchanged editor is immediate; closing a dirty editor uses the Edit Contact
discard-confirmation convention. Phase 2C remains responsible for clickable phone/fax, email, website and map
presentation, Organization Type chips, cards, and search filters; none are introduced by Phase 2B.1.

## Phased implementation roadmap

### Phase 0 — live catalog and data inventory

Create a read-only SQL inventory for `Organizations`, `OrganizationTypes`, `Contacts.OrganizationId`,
their keys/indexes/defaults/checks/RLS predicates, row counts, type usage, orphan/cross-tenant risks,
legacy contact-point population, duplicates, and representative data shapes. Run it against
`Shale_Copy` or another approved administrative connection before writing Phase 1 SQL. Do not change
schema or data.

Repository inventory and the approved live Phase 0 inventory are complete. The verified baseline was
176 tenant-7 Organizations (171 active and 5 deleted) and exactly seven tenant-7 Organization Types.

### Phase 1A — Organization Type foundation

Implemented by `2026-09-08_organization_types_foundation_phase1a.sql` and its separate read-only
verification contract. The migration adds the compatible overlay fields, constraints, indexes, actor
metadata, lifecycle fields, color, ordering, and `RowVer` to the existing `OrganizationTypes` table.
Attach the established tenant-or-global RLS predicate only after verifying the live policy contract.
Add `OrganizationOrganizationTypes` with strict tenant RLS and composite Organization ownership.
Backfill existing single-type assignments without changing visible behavior.

The seven existing IDs, names, and tenant-7 ownership values remain unchanged and receive explicit
stable keys, deterministic zero-based ordering, and the reviewed Shale-compatible palette. No global
type is seeded. Every Organization, including the five soft-deleted rows, receives one active primary
assignment matching its legacy `OrganizationTypeId`. Reruns insert only when no matching historical
assignment exists, so a later soft removal is never restored, replaced, or duplicated. Reruns also
preserve later definition color, ordering, lifecycle, timestamp, actor, and RowVer changes.
The assignment's authoritative type-ID FK cannot alone enforce that a definition is global or belongs
to the assignment tenant, so Phase 1C must validate that rule transactionally.

`OrganizationTypes` uses `sec.fn_FilterByTenantOrGlobal`; `OrganizationOrganizationTypes` uses strict
`sec.fn_FilterByTenant`. Both predicates attach to the single enabled existing `TenantFilter` policy.
No runtime Java, audit allowlist, visible behavior, or legacy Organization column changes in Phase 1A.
`Organizations.OrganizationTypeId` remains the compatibility primary-type authority until the later
dual-write and read cutover.

Deliver one guarded, rerunnable migration and a separate read-only verification script. Do not change
runtime Java reads/writes or UI in Phase 1A.

### Phase 1B — read/domain contracts (implemented)

`OrganizationServicePort` now exposes immutable Organization Type definition, assigned-type, and profile
records. The data adapter provides one bounded effective tenant/global definition query and one bounded
single-Organization assignment-profile query. Tenant overrides win by stable `SystemKey`; inactive winners
mask globals for new selection; deleted overrides reset to active globals; and historical assignments retain
the exact definition selected by their stored `OrganizationTypeId`, including inactive/deleted lifecycle data.
Directory/search pages deliberately do not hydrate profiles in this phase.

`Organizations.OrganizationTypeId` and current visible behavior remain the compatibility primary-type
authority. Profiles report disagreement with the active primary assignment without repairing either source.
Phase 1A remains the deployed database foundation. Phase 1C mutations, Settings administration, and all UI
presentation remain deferred; no runtime read or write cutover has occurred.

### Phase 1C — transactional type administration and assignments

Implemented. The service boundary supports administrator-authorized tenant definition creation,
global override creation, update, activation/deactivation, soft removal, and restoration. `SystemKey`
is immutable lowercase snake-case overlay identity; a deleted historical override is restored rather
than duplicated. Definition ordering remains the `SortOrder` accepted by create/update because the
Contact Phase 1C pattern has no separate definition-ordering transaction.

Active tenant actors may add (or restore) selectable assignments, soft-remove and restore non-primary
assignments, choose a primary, atomically replace-and-remove a primary, and reorder the exact active
assignment set. Assignment mutations lock the Organization and active primary, reject existing
compatibility disagreement, retain one active primary, and update `Organizations.OrganizationTypeId`
in the same transaction as primary flags. Definition lifecycle changes never rewrite historical
assignments, which continue to resolve by their concrete stored type ID.

Every operation verifies tenant/actor IDs, request-session tenant context, actor tenant membership,
administrator status for definition administration, ownership, and required `RowVer` values. Mutation
and PHI-safe entity-action audit append share one connection and transaction. Deploy the guarded
`2026-09-09_organizations_phase1c_audit_allowlist.sql` successor to `Shale_Copy` with an approved
all-tenant administrative principal, then run its independent read-only verification script.

Settings administration, Organization editor controls, presentation, and runtime read cutover remain
future phases. Existing legacy Organization create/update paths remain compatibility-only and do not
yet dual-write `OrganizationOrganizationTypes`; Phase 2B must route those aggregate writes through the
transactional service before the bridge can be retired.

### Phase 2A — structured contact-point foundation and migration

Add the four Organization contact-point tables using the proven Contact table invariants. Provide a
guarded migration and separate verification. Conservatively backfill legacy Phone, Fax, Email,
Website, and address fields. Do not delete or blank legacy columns.

Implemented as Organizations Phase 3A by
`2026-09-10_organizations_phase3a_structured_contact_methods.sql`. The Organization-owned tables are
`OrganizationPhoneNumbers`, `OrganizationEmailAddresses`, `OrganizationAddresses`, and
`OrganizationWebsites`; Contacts have no safe website/link child, so the last is the smallest
Organization-specific equivalent of the phone/email lifecycle. All four are strict tenant-owned,
RLS-protected historical children with composite Organization ownership, stable identity, closed
kinds, one active primary per concept, deterministic nonnegative order, actor/timestamp provenance,
soft deletion, and `RowVer`. No parent delete cascades.

Legacy mapping is exact and conservative: Phone becomes `WORK` order 0 and primary; Fax becomes `FAX`
order 1 and is primary only when there is no populated Phone or other active primary; Email becomes
`WORK`; the six address components become one `WORK` address whenever any component is meaningful;
Website becomes `MAIN`. Outer whitespace is trimmed during controlled copying, but phone punctuation,
extensions embedded in the legacy value, email internal characters, incomplete addresses, country
text, and websites without schemes are preserved. Historical matching includes deleted rows, so a
rerun never resurrects a removed backfill. Existing structured rows are not updated or promoted.

This is database foundation only. Existing scalar columns remain the current read/write authority,
and no trigger or runtime dual write exists; scalar edits after deployment therefore need not update
the structured copy. Phase 3B will add a read-only adapter. Phase 3C will own atomic compatibility
synchronization, authorization, concurrency, restoration, and mutation audits. The audit allowlist is
unchanged now because mechanical backfill emits no user mutation events. The Organization UI, cards,
search, APIs, and runtime mutation paths are explicitly unchanged.

### Phase 2B — Organization aggregate read/write boundary (implemented 2026-09-09)

Add aggregate detail/create/update commands and results to `OrganizationServicePort`. The mutation
starts its transaction before authorization, validates `Organizations.RowVer`, validates every child
RowVer and complete intended active set, applies types and contact points, maintains compatibility
fields, appends audits, and returns the final authoritative profile. Stale input is not silently merged
or retried.

Move JavaFX create and edit mutations off the FX thread and through the service port. Remove the
desktop's direct mutation dependency on `OrganizationDao` while retaining DAO-backed reads only where
the current architecture still explicitly permits them.

Phase 2B uses one reusable, dialog-local Organization Type assignment stage for Add and Edit. It
starts from an immutable profile snapshot, exposes effective active definitions by stored definition
ID, preserves inactive/removed historical assignments, and keeps add/remove/primary/order changes
local until parent Save. Exactly one eligible active assignment is submitted as primary. The DAO-owned
aggregate transaction validates tenant/actor, locks current state, checks Organization and assignment
`RowVer` values, reconciles the exact ordered set (including restoration), synchronizes
`Organizations.OrganizationTypeId`, and writes the existing assignment audit events before commit.
Desktop live invalidation is published only after successful return.

Legacy callers that provide one `OrganizationTypeId` are mapped deterministically to one assignment:
primary, `SortOrder = 0`, with the same compatibility ID. Embedded Case/Intake creation invokes the
same connection-bound worker inside the already-owned Case transaction. Existing server create/update
routes remain functional and response-compatible; their optional type selects the primary while update
preserves unrelated non-primary assignments. Server update accepts an optional Base64 Organization
`RowVer`; when omitted it reloads the current token immediately before the aggregate transaction, which
protects against a race after that reload but is not an HTTP precondition supplied by the client.
Phase 2C cards, header chips, and type search/filter presentation remain deferred. Restoring a deleted
Organization record remains a separate lifecycle feature.

### Phase 3 — shared Add/Edit Organization experience

Replace the separate new form and field-by-field dialogs with one bounded, scrollable staged editor.
Support identity, multiple types, multiple contact points, primary selection, ordering, removal and
restore, validation, Notes, unsaved-change confirmation, and stable Cancel/Save actions. Use the same
editor entry point from the Organizations list and Organization View.

### Phase 4 — Organization View and card presentation

Restyle the detail header and information surface using Shale's existing component vocabulary. Show
all type chips in the header and cards. Render contact-point cards with clickable actions. Extend, do
not replace, `OrganizationCardFactory` for FULL/COMPACT/MINI consumers, and batch-hydrate directory and
search results to avoid N+1 queries.

### Phase 5 — Settings administration (implemented as Organizations Phase 2A)

Add Organization Types to the existing classification/lookup administration patterns. Global rows
remain immutable; tenant customization creates overrides; custom rows are tenant-owned; lifecycle and
RowVer conflicts follow the established Contact administration behavior.

The Settings section now separates effective active definitions, inactive tenant definitions, and
removed tenant definitions. Its administrator-only controls support tenant creation/editing,
same-SystemKey global customization, activation/deactivation, soft removal, and restoration through
the Phase 1C service boundary. Removing an override reveals its global fallback; deactivating an
override masks that fallback. Lifecycle changes preserve historical Organization assignments, and a
stale `RowVer` reloads authoritative state rather than overwriting another administrator's change.
Manual Refresh or Settings navigation picks up another workstation's changes; no broad live-update
contract was introduced.

This does not complete Organization Types. Multi-type assignment editing and primary selection remain
Phase 2B work; cards, headers, and search filters remain later presentation work. Restoring a deleted
Organization record is a separate lifecycle concern and remains unimplemented.

### Phase 6 — relationship and lifecycle review

Inventory and decide the future of `Contacts.OrganizationId`, related Contacts presentation,
Organization deletion's current `CaseParties` cleanup, and obsolete `CaseOrganizations` DAO methods.
This phase must preserve `CaseParties` as case relationship authority. Do not expand Organization link
sharing without a separate product decision.

### Phase 7 — cutover and retirement

Reconcile legacy and structured data, switch all list/search/detail/card consumers to the aggregate
model, monitor compatibility, and only then remove fallbacks. Dropping legacy columns or tables is a
later separately approved destructive migration.

## Testing and verification

Before every implementation phase, perform the repository-required pre-edit impact search. Relevant
baseline tests include Organization DAO type query, Organization service adapter, Organization card
variants, New Organization semantic controls, Related Case renderer mapping, server API reads, intake
party cards, requested-from workflows, material workflows, and Global Search.

New coverage must include overlay precedence, other-tenant exclusion, lifecycle behavior, historical
rendering, duplicate prevention, backfill reconciliation, primary constraints, RowVer conflicts,
transaction-bound audit behavior, desktop aggregate editor behavior, clickable-action event isolation,
batch hydration, and server serialization where records change.

Use the repository's Organizations affected-area selection and focused tests first, then the local
critical `mvn test`. The optional full suite is informational only when selector escalation or an
explicit request calls for it. Ordinary CSS adjustments require visual/static validation rather than
new blocking pixel-geometry tests.

## Contact redesign lessons carried forward

* Design read and mutation contracts before wiring the editor.
* Populate all required timestamps and actor fields.
* Search every port record/constructor and test implementation when contracts grow.
* Batch-load card classifications and primary contact points.
* Verify the actual card factory used by every surface.
* Distinguish failed loads from legitimate empty results.
* Preserve historical assignments and legacy values through cutover.
* Do not let classifications replace case-specific roles.
* Keep migrations additive, guarded, rerunnable, and independently verifiable.
