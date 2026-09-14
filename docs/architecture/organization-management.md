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

Phase 3A added `OrganizationPhoneNumbers`, `OrganizationEmailAddresses`, `OrganizationAddresses`,
and `OrganizationWebsites`. Like the equivalent Contact tables, they provide strict tenant
ownership, one active primary value per category, ordering, soft-delete history, actor metadata,
timestamps, and `RowVer`.

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

## Phase 3B structured contact-method read boundary

Phase 3B adds the immutable, Organization-owned `OrganizationPhoneNumber`,
`OrganizationEmailAddress`, `OrganizationAddress`, and `OrganizationWebsite` read models and the
aggregate `OrganizationStructuredContactProfile`. `OrganizationServicePort.findStructuredContactProfile`
returns `Optional.empty()` when the active Organization is absent in the caller's tenant; an
Organization in another tenant is deliberately indistinguishable from a missing one.

`OrganizationDao` owns this read. It verifies the tenant-stamped session, loads the active parent and
its legacy scalar values, then issues one explicit-column query for each structured table on the same
connection. Every statement filters both `ShaleClientId` and `OrganizationId`; this bounded five-query
shape avoids both N+1 access and a four-way contact-method cross product.

The profile contains complete active and soft-deleted history. Each collection is immutable and sorts
active rows first by `SortOrder`, then stable `Id`, followed by deleted rows in the same order. Safe
active projections are provided. Primary phone, fax, email, address, and website projections consider
only active rows, and duplicate active primaries produce `INVALID_PRIMARY` compatibility state rather
than an arbitrary selection. Unknown future `Kind` values map to `UNKNOWN` while preserving their raw
database value; other corrupt invariants produce a table-scoped data-integrity failure.

Compatibility is reported independently for phone, fax, email, address, and website as both absent,
matching, legacy-only, structured-only, different, or invalid-primary. Comparison trims outer
whitespace, compares email case-insensitively, compares address components independently, and does not
rewrite values or erase phone punctuation differences. An overall convenience result is true only
when every concept is matching or absent on both sides.

Legacy scalar Organization fields remain application-authoritative. This additive read does not alter
Organization detail, cards, search, controllers, server APIs, caches, live updates, writes, schema, or
audit behavior. The read itself intentionally emits no audit event because Phase 3B introduces no new
consumer or sensitive-value display. Phase 3C remains responsible for atomic structured mutations and
legacy compatibility synchronization; Organization UI remains unchanged until a later presentation
phase.

## Phase 3C atomic structured contact mutations

`OrganizationTypeMutationDao` remains the single aggregate transaction owner. Organization scalar fields,
the exact Organization Type assignment profile, all owned structured-contact collections, compatibility
scalars, and PHI-minimized entity-action audits share its tenant-stamped JDBC connection and rollback
boundary. Child helpers neither open connections nor commit. The successful Organization update consumes
the caller's opening `Organizations.RowVer` exactly once in one consolidated parent update and returns
`OUTPUT inserted.RowVer`; every submitted persisted child in an owned exact set must carry its opening
`RowVer`, including unchanged rows, so omission cannot hide a concurrent edit.

Contact participation is explicit. `LegacyContactMutation` preserves the existing desktop, Case/Intake,
Material Request, direct DAO, and server contracts and changes only a deterministically selected
compatibility-owned row. It preserves additional active and historical rows and refuses an ambiguous
ownership decision. `StructuredContactMutation` contains four independently explicit
`OwnedContactCollection` values: `owned=false` means unchanged, while `owned=true` is an exact active and
explicit-history set. Exact saves reject duplicate/foreign IDs, missing or stale tokens, unsupported kinds,
blank values, multiple primaries, and noncontiguous active ordering; omission from an owned collection
soft-removes an active row, explicit deleted state preserves/restores stable identity, and no hard delete is
used.

Compatibility selection is deterministic. Phone uses the active primary non-FAX voice row, falling back to
the first active voice row by `SortOrder, Id`; Fax always uses the first active FAX row by that order and does
not require `IsPrimary`. When voice exists, the schema's one overall phone primary belongs to voice; Fax may
be primary only when voice is absent. Email, address, and website use their active primary and otherwise the
first active ordered row. Missing structured values clear the corresponding scalar, and compatibility-length
validation occurs before the parent mutation. Thus a callable voice number and Fax legitimately coexist
without a schema change.

The Phase 3C audit vocabulary is `ORGANIZATION_PHONE`, `ORGANIZATION_EMAIL`, `ORGANIZATION_ADDRESS`, and
`ORGANIZATION_WEBSITE` (plus the aggregate `ORGANIZATION` parent vocabulary). Create, update, remove,
restore, and ordering/primary-affecting updates are appended on the business connection. Metadata is limited
to IDs, kind, ordering/primary state; contact values and RowVers are prohibited. Deploy
`2026-09-10_organizations_phase3c_audit_allowlist.sql` only after its refusal-default operator guards are
completed, then run the separate read-only verification script.

The genuine remaining bridge boundary is that public server requests and the current Organization UI remain
legacy-scalar callers; the server does not yet expose structured exact-set JSON. Omitted structured
collections therefore never clear data. Service/controller code continues to perform the established single
post-commit authoritative refresh and Organization live-update publication; DAO code publishes nothing.
Phase 3D remains responsible for connecting a staged Contact-style editor. Clickable presentation, cards,
and search remain Phase 3D/3E presentation work.

## Phase 3D structured Edit Organization modal

The dedicated Edit Organization modal now follows the Contact editor's bounded, scrollable dialog and
contact-point card language. Its stable Save/Cancel footer remains outside the scrolling surface. The
scrolling content is divided into Organization Details, Contact Information (Phone Numbers, Email
Addresses, Addresses, and Websites), Organization Types, and Notes. The legacy single Phone, Fax,
Email, Website, and six-part address controls no longer exist in this editor; the read-only Organization
view may continue to render their synchronized compatibility projections.

Every opening loads the Organization identity and `RowVer`, effective type definitions and the complete
assignment profile, and the complete structured contact profile away from the JavaFX thread. Active and
historical rows, stable IDs, and opening child `RowVer` values seed dialog-local stages only after all
loads succeed. An inconsistent Phase 3B compatibility profile is shown as a safe conflict and disables
Save rather than overwriting either representation. Closing invalidates pending callbacks, and reopening
creates a new authoritative stage.

Phone, email, address, and website changes are exact staged sets. Cards support add, edit, soft removal,
clearly labeled historical restoration, contiguous ordering, and primary selection. Phone and Fax share
the phone collection: when voice rows exist, a non-Fax row owns the primary flag, while compatibility Fax
continues to select the first ordered active Fax without requiring it to be primary. Email duplicates are
compared case-insensitively while display casing is retained. Partial addresses are valid, blank addresses
are not, and a noneditable `LegacyAddressText` is carried through unchanged. Website values are retained
as entered and do not require a URL scheme. Unknown future kinds remain visible but must be deliberately
changed to a supported kind before an exact save. As in Edit Contact, removed rows are hidden by default
behind **Show Removed** and restoration keeps their stable identity and opening concurrency token.

Dirty state compares identity, Notes, Organization Type assignment state, and the exact four structured
stages with their opening snapshots. Cancel and discard issue no mutation, audit, refresh, or live event;
reverting all edits clears dirty state. Save validates the complete stage, then invokes exactly one
`updateOrganizationAggregate` command containing the exact assignment state and one
`StructuredContactMutation` whose four collections are explicitly `owned=true`. The modal never submits
`LegacyContactMutation`, updates compatibility scalars separately, or commits per-row mutations. The
Phase 3C transaction remains authoritative for tenant/actor authorization, all parent/assignment/child
RowVers, compatibility synchronization, atomic rollback, and transaction-bound PHI-safe audit events;
no schema or audit migration is added by this UI phase.

A genuine concurrency failure keeps the modal open and reloads Organization identity, types, assignments,
and all structured rows for explicit user review; it is never silently retried. A successful aggregate
result closes the dialog and uses the established Organization controller boundary for one authoritative
view refresh and one post-commit live invalidation. Validation and persistence failures keep the modal
open and publish nothing.

The New Organization screen intentionally remains a legacy scalar UI caller reconciled by the Phase 3C
compatibility bridge. Converting Add Organization to the shared structured experience is the remaining UI
boundary. Phase 3E remains next for read-only structured presentation, clickable phone/email/map/website
actions, Organization Type chips, card changes, and search/filter changes; none are part of Phase 3D.

## Phase 3E.1 structured read-only presentation

Organization View now loads the active Organization, its authoritative Organization Type assignment profile,
and its complete structured contact profile off the JavaFX thread under one stale-generation guard. The header
shows active definitions as wrapping, database-colored chips in primary, assignment-order, stable-ID order; the
compatibility scalar type is not rendered separately. Historical/deleted assignments and contact methods remain
available to the editor/read aggregate but are outside the normal read-only presentation.

The former scalar Phone, Fax, Email, Website, Address1, Address2, City, State, Postal Code, and Country rows are
replaced by sparse Phone Numbers, Email Addresses, Addresses, and Websites card groups. Values remain unchanged
for display, unknown kinds receive a readable fallback, Fax is visibly a Fax and is not callable, and partial
addresses are composed only from populated components. Phone, email, map, and website actions reuse the shared
validated external-action URI builder. Websites permit only HTTP(S), adding HTTPS to the launch target (not the
stored value) when needed; unsupported host actions are localized and nonfatal.

Read-only values are ordinary wrapped display text, not hyperlinks. Contact and Organization profiles share the
same `ContactMethodDisplayCard`: kind and optional Primary badges occupy the upper-left, while a real semantic
small secondary `Call`, `Email`, `Open in Maps`, or `Open Website` button occupies the upper-right. Fax uses the
same card without an empty action slot or misleading Call button. The shared component consumes nested button
mouse actions so an actionable method embedded in an Organization card cannot also activate card navigation.

The existing `OrganizationCard` is extended rather than replaced. Directory pages fetch card presentation data
for at most 100 current-page Organization IDs using five tenant-scoped, active-only queries (types, preferred
voice phone, preferred email, primary address, and preferred website). Independent queries avoid a contact-point
cross product and keep query count fixed as card count grows. Page results and their projections share the
existing search/page generation guard, so stale hydration cannot attach to a newer generation. Nested external
actions consume their mouse event while the card retains mouse and keyboard profile navigation.

This read-only feature adds no meaningful mutation and therefore no new entity-action audit event or migration;
existing structured reads retain their established audit treatment. Phase 3E.2 remains responsible for
Organization Type and structured contact search/filtering. New Organization structured creation also remains a
later compatibility cleanup.

## Phase 3E.2 structured directory search and Organization Type filtering

The desktop directory searches active Organizations by name and active structured phone display/normalized
values, email address, address lines 1 and 2, city, state/province, postal code, country, website, and active
assigned Organization Type name. Deleted Organizations, contact methods, and assignments and inactive/deleted
type definitions are excluded; Notes and historical removed values are not searchable. Phone query digits reuse
Contact directory normalization without changing stored display values; punctuation-only input cannot activate
the normalized-number branch.

The compact Contact-style popup lists tenant-effective active Organization Types with configured colors and
supports multiple selections. Selections use OR semantics across every active assignment, including non-primary
assignments; search and the selected-type group combine with AND. Clear Filters, sort, and filter changes start a
new first-page generation.

An immutable criteria snapshot carries tenant, normalized text, defensively copied type IDs, deterministic name
sort/direction, offset, and bounded page size. Page and count use one shared, parameterized predicate. Explicit
Organization/child `ShaleClientId` correlations remain alongside RLS; independent `EXISTS` predicates avoid child
cross-products and duplicate results, and Organization ID is the final sort tie-breaker. The legacy server service
overload remains name-search compatible, with no new public API parameter.

Each generation loads its lightweight page/count before the existing five fixed card-projection queries receive
only that page's IDs. No profile or per-card query is introduced. Snapshot and generation checks prevent stale
search/filter results from applying. This read-only work introduces no mutation or audit event. New Organization
remains a legacy-scalar editor reconciled by the aggregate boundary; structured creation is still future work.

## Phase 3F.1 structured New Organization workflow

New and Edit Organization now compose the same `OrganizationAggregateEditor`. The shared editor owns the
Organization Details, structured Contact Information cards, `OrganizationTypeAssignmentPane`, Notes,
validation, primary selection, ordering, and dirty-state rules; the surrounding controllers own only their
create-versus-edit loading, concurrency, save, and close lifecycle. New Organization no longer contains
legacy Phone, Fax, Email, Website, or address scalar controls, including hidden duplicates.

A create stage begins with empty Notes, no type assignments, and four empty structured collections. New
contact rows have neither IDs nor RowVers. The first assigned type, email, address, and website becomes
primary. A Fax may be added first, but it does not displace a voice primary; adding the first later voice
number establishes the preferred voice row. Removing an unsaved row discards it locally rather than creating
history, and untouched add-card drafts never enter the submitted exact sets.

Create submits one `createOrganizationAggregate` command. It contains identity/Notes, the exact type stage,
and one `StructuredContactMutation` with all four collections explicitly owned and contiguously ordered.
`OrganizationTypeMutationDao` remains the sole transaction owner for the Organization, assignments,
structured rows, derived legacy compatibility scalars, and existing PHI-safe entity-action audits. Any child,
compatibility, uniqueness, actor/tenant, or audit failure rolls back the transaction. This phase uses the
existing schema and audit vocabulary and adds no migration.

Cancel and clean close perform no mutation. Dirty close covers Name, Notes, types, and all contact collections
and uses the same discard confirmation as Edit; discard publishes no update and writes no audit. A successful
create closes first, then invokes the established directory callback once, which preserves the directory's
current criteria and performs its existing authoritative refresh/live-update behavior.

Case, Intake, Material Request, direct DAO, and server Organization creation remain deliberate legacy-scalar
callers. Phase 3C continues reconciling those contracts to deterministic structured compatibility rows inside
the aggregate transaction. Phase 3F.2 is the future boundary for removing those compatibility callers only
after each embedded/public contract is explicitly migrated; Phase 3F.1 does not change the public API.

## Phase 3F.2 — structured authority and legacy compatibility closure

The four structured Organization contact tables are the authoritative in-repository presentation and mutation
model. `Organizations.Phone`, `Fax`, `Email`, `Website`, and postal-address columns remain synchronized
compatibility mirrors; they are not removed, and new application code must not treat them as contact storage.
Only `OrganizationTypeMutationDao`, the aggregate transaction owner, may write those mirrors. Legacy inputs are
adapted to structured mutations before that owner commits.

### Complete mutation ownership

* New and Edit submit structured exact sets through the Organization service aggregate commands.
* The public legacy service create method converts populated scalar inputs to explicitly owned structured rows;
  blanks produce no rows. Its update method is a compatibility patch adapter which reconciles only the safely
  identified compatibility-owned row and preserves additional structured values.
* `OrganizationDao#create` and `#update` remain deprecated source-compatible adapters and contain no independent
  Organization mutation SQL. Organization deletion remains its established separate lifecycle operation.
* Case and Intake call the connection-bound single-type aggregate worker with four explicitly owned empty
  contact collections. The worker neither opens nor commits a connection, so the Organization, type assignment,
  structured audit events, parent Case/Intake, and party relationship roll back together.
* Material Request “Requested From” currently collects only Name and Type. Its reachable DAO compatibility call
  delegates to the same aggregate and therefore submits empty structured collections, creates no blank child,
  and returns only after commit; the existing chooser refresh/selection occurs only after that success.

### Public and read-only compatibility boundaries

Server Organization POST/PATCH JSON remains unchanged. POST scalar fields become structured create rows and
mirrors atomically. PATCH omission retains the opening scalar projection; an explicit blank clears the safely
owned compatibility row. Additional structured rows survive, and ambiguous ownership fails with the existing
safe conflict handling rather than falling through. Internal row versions and exact-set JSON are not exposed.

Server responses, lightweight legacy service summaries, global-search model hydration, Case party/requested-from
projections, and document/report/export shapes retain the synchronized scalar columns where changing the source
would alter a public or established projection contract. These are read-only compatibility projections, use
bounded queries, preserve tenant and deletion predicates, and do not create join multiplication. Organization
profiles, cards, directory search, and filters continue to use structured projections.

Aggregate mutations append the existing minimized Organization/type/contact audit vocabulary on the transaction
connection; contact values are not metadata and no audit migration is required. A rollback removes all such
events. Cache invalidation and live-update publication remain caller-owned post-commit effects: one successful
outer workflow publishes/refreshes after its complete commit, while failures publish nothing.

Operational drift is checked by the separate, guarded, read-only
`docs/sql/verification/2026-09-10_organizations_phase3f2_compatibility_verification.sql`. It performs no repair,
DDL, DML, trigger changes, or RLS disablement. Genuine remaining compatibility boundaries are the public server
scalar contract and the stable scalar read projections listed above. Deleted Organization restoration remains a
separate future lifecycle phase.

## Phase 3F.3 — deleted Organization discovery and restoration

The desktop Organizations directory remains `ACTIVE_ONLY` by default. Active tenant administrators alone see
the compact **Show removed** control; it switches to a clearly indicated removed-only directory without mixing
lifecycle states, retains text and Organization Type filters, and starts a new first-page generation. Page and
count share the lifecycle predicate, tenant correlation, structured search predicates, deterministic `Name, Id`
sort, and the existing five bounded current-page card projection queries.

Removed results reuse `OrganizationCard`. They carry a Removed badge and restrained presentation, do not open the
active profile, and disable phone, email, map, and website launch behavior. An administrator can restore from the
card after a non-destructive confirmation naming the Organization and explaining that its retained relationships,
types, and contact information become active. A submission guard prevents duplicate requests; failure retains the
removed result and presents a safe review/reload message.

`OrganizationServicePort.RestoreOrganizationCommand` is an explicit lifecycle contract containing tenant, actor,
Organization identity, and the opening parent `RowVer`. The DAO transaction validates positive IDs and the tenant
session, requires an active same-tenant administrator, locks the deleted parent, rejects missing, active, foreign,
or stale state, and preflights retained primary/type, ordering, primary, and child-ownership invariants. Unsafe
history is never repaired during Restore and instead requires administrative data review. The existing deployed
Organizations table has no parent `DeletedAt`, `DeletedByUserId`, or `UpdatedByUserId` columns, so restoration clears
only `IsDeleted`, updates `UpdatedAt`, and captures `OUTPUT inserted.RowVer`.

Restoration updates the existing parent row only. It does not insert an Organization, alter its ID, mutate or
restore type assignments or structured contact rows, duplicate children, rewrite mirrors, run backfill, or touch
Case relationships. Consequently independently removed children remain historical and retained active children,
primary/order state, compatibility projections, Case links, and audit history remain unchanged. This is also the
safe causal boundary because the established delete operation does not mark children as removed-by-parent.
Phase 3F.3 also removes the former compatibility cleanup that physically deleted `CaseParties` during parent
soft deletion; new deletions therefore retain both `CaseOrganizations` and `CaseParties` relationships. Historical
party rows already removed by older releases cannot be causally reconstructed and are not fabricated by Restore.

One PHI-safe `ORGANIZATION / RESTORED` entity-action event is appended on the mutation connection before commit;
audit failure rolls back the parent update. No audit migration is needed because `RESTORED` and `ORGANIZATION` are
already deployed vocabulary. The controller publishes exactly one tenant Organization invalidation after commit,
then refreshes removed mode; stale, unauthorized, inconsistent, and audit-failed attempts publish nothing.
Public server Organization JSON remains the synchronized legacy-scalar compatibility boundary and no restoration
HTTP route is introduced. The read-only operational report is
`docs/sql/verification/2026-09-11_organizations_phase3f3_lifecycle_verification.sql`.
