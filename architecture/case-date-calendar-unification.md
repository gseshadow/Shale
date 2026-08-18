# Case Date and Calendar unification

## Phase 1 — runtime synchronization retired (2026-08-17)

All runtime Calendar Event ↔ Case Date synchronization is retired. Authoritative Case Date create,
update, soft-delete, and restore transactions mutate and audit only `CaseDates`; authoritative
Calendar Event create, update/cancel, and hard-delete transactions mutate and audit only
`CalendarEvents`. Calendar assignment notifications and Case Date post-commit LiveBus invalidation
remain at their existing service/controller boundaries. The Calendar feed continues to project active
Case Dates directly with stable `CASE_DATE:<CaseDates.Id>` source identity and existing case-Dates
click routing; persisted Calendar Events remain an independent feed source.

Production inventory at retirement contained four active, unlinked tenant-7 Calendar Events, no
non-null `CalendarEvents.CaseDateId`, mapping, or synchronization-audit rows, and no invalid or
duplicate relationships. No cleanup or backfill was performed. Existing linked rows, if present in
another environment or restored history, remain readable and are not unlinked or synchronized.

The linkage and mapping schema documented below remains temporarily deployed for a later,
forward-only removal phase: `CalendarEvents.CaseDateId`, `CalendarCaseDateTypeMappings`, link
constraints/indexes, trigger, RLS, and synchronization audit vocabulary are unchanged. Runtime does
not consult mappings or pair records by title, date, label, type name, or any other heuristic.
`CalendarEvents` still lacks its own RLS predicate; this separate known gap is not corrected here.
Application mutations continue to require authenticated SQL session tenant/actor identity and apply
explicit `ShaleClientId` predicates.

Audit compatibility uses the existing schema without migration. Each successful authoritative
mutation retains exactly its own `CALENDAR_EVENT` or `CASE_DATE` entity-action record in the owning
transaction; synchronization audit records are no longer emitted.

## Phase 2 — unified Calendar New Event workflow (updated 2026-08-18)

Calendar **New Event** immediately opens one form, with no choice page or Back/Next workflow. In
order it contains Title, Assign to Case, Type, Start Date, End Date, Start Time, Duration, All Day,
Notes, and Save/Cancel. Title, Type, and Start Date are required; Notes and Case are initially
optional. The supplied Calendar date is the Start Date default. Case selection reuses the searchable
Case selector loading pattern and `CaseCardFactory` MINI presentation; a selected card has Change
and Remove actions. Open-state, tenant, and request-generation checks reject stale asynchronous
results.

Type authority follows Case assignment. Without a Case, the searchable selector exposes only active
effective `CalendarEventTypes`; with a Case, only active tenant-effective `CaseDateTypes`. Changing
authority clears an incompatible selection without clearing shared form values. A type is identified
by source-kind plus authoritative numeric ID, never display text, so duplicate names stay distinct.
A non-time-supporting Case Date Type forces All Day. Timed values use Start Time plus Duration on
the selected Start/End dates; all-day values retain the existing local-date-at-start-of-day and
optional multi-day-end convention, without timezone conversion.

No Case creates exactly one unlinked `CalendarEvent` through `CalendarService`; Title maps to
`CalendarEvents.Title`, Notes maps to its description, dates map to `StartsAt`/`EndsAt`, and this form
does not expose assignment. A selected Case requires a Case Event type and creates exactly one
`CaseDate` through `CaseServicePort.CreateCaseDateCommand`; Title maps to `CaseDates.Title`, the
selected Case/type IDs map directly, Notes maps to `CaseDates.Notes`, and the controller publishes
the established PHI-free Case Dates LiveBus invalidation only after success. Both titles are trimmed
and limited to the authoritative 255 characters. Neither route reads mappings, writes
`CalendarEvents.CaseDateId`, creates a counterpart, or invokes the other route. Submission
revalidates captured tenant and actor, is single-flight, preserves form state after failure, and
permits retry.

The linkage/mapping schema, synchronization audit vocabulary, obsolete Settings guard, known
`CalendarEvents` RLS gap, feed projection identities, and existing edit/click routing remain
unchanged pending the separately gated schema-retirement release.

### Shared local time and duration entry (updated 2026-08-18)

Calendar New Event and the Case Date occurrence editor use the same JavaFX `TimeDurationInput`.
Start Time is an editable selector whose 48 standard choices cover every half hour from 12:00 AM
through 11:30 PM. Committed input is parsed centrally and displayed as `h:mm AM/PM`; accepted input
includes hour-only and hour/minute 24-hour values and case-insensitive 12-hour values with AM/PM
(for example `9`, `9:15`, `9 AM`, `9:15 AM`, and `14:15`). Invalid mixed-meridiem, out-of-range,
blank, or otherwise unparseable text is rejected visibly. Display text is never authoritative or
persisted: the forms produce `LocalTime` and then local `LocalDateTime` values without a timezone
conversion.

Duration is represented by separately labeled, accessible Hours (0–23) and Minutes (0–59)
selectors. New timed records default to 1 Hour and 0 Minutes, while 0 Hours and 0 Minutes is invalid.
The complete minute ranges deliberately preserve nonstandard persisted durations. All Day disables
all three timed controls without clearing their values; a Case Date Type that does not support time
forces that state. Returning to a time-supporting timed state therefore restores the last sensible
values.

Start Date remains required and End Date optional, with End Date prohibited before Start Date. A
timed start is Start Date plus parsed Start Time. Its end combines the selected End Date (or Start
Date when absent), the same local clock basis, and duration; day wrap is resolved against the
selected end calendar date so same-day, cross-midnight, and multi-day persisted timestamps reopen
and save unchanged. All-day records retain local start-of-day values and the optional multi-day End
Date convention. This shared calculation is used for both General Event and Case Date requests and
does not change source identity, Case/type authority, persistence routing, RowVer handling, Case
touches, auditing, notifications, or LiveBus publication.

**Audit compatibility review.** This is validation and local-value composition at the existing UI
boundary only. Meaningful mutations continue through the established Calendar Event or Case Date
transaction and existing entity/PHI audit paths, with tenant, actor, entity, parent/Case, and RowVer
context unchanged. No new sensitive read or mutation is introduced, no audit payload is assembled
in UI code, and the existing audit schema is sufficient without migration.

## Phase 3 — source-specific Calendar activation and editing (updated 2026-08-18)

Calendar activation is now an explicit typed contract. A `CASE_DATE:<CaseDates.Id>` target retains
both the occurrence ID and owning Case ID. On the Calendar, week/day cards, Month items and
drill-down, and schedule agendas invoke a shared UI-level `CaseDateOccurrenceEditorLauncher` rather
than Case navigation. The launcher owns `CaseDateOccurrenceDialog` as a modal of the current Calendar
window, reloads the exact occurrence through `CaseServicePort.getCaseDate`, loads the effective types,
and checks captured tenant, actor, Case, occurrence, open-window, and request-generation state before
display or save. Missing, removed, foreign-tenant, inaccessible, wrong-Case, duplicate, or stale
results fail closed and do not open another editor.

The launcher is also the authoritative occurrence-open path used by Case → Dates; normal Case
navigation and its add/remove/restore behavior remain unchanged. Updates retain Title and every
occurrence field, submit the loaded RowVer through `CaseServicePort.updateCaseDate`, and therefore
retain the established transaction, audit, Case-touch, and concurrency behavior. Each owning
controller performs only its existing post-success UI effects: the Case Dates LiveBus invalidation is
published, while Calendar also reloads its current range without changing selected view, date,
filters, layers, or overlays. Cancel and rejected saves leave the owning surface and dialog state
unchanged; no Calendar Event counterpart, linkage, persistence route, or synchronization is added.

A `CALENDAR_EVENT:<CalendarEventId>` target (with the deployed `EVENT:` spelling accepted only as a
read-compatibility alias) invokes only the existing Calendar Event editor. It reloads by event ID
under the captured tenant and continues through `CalendarService`, preserving assignment effects,
audit and concurrency boundaries, and optional Case association without treating that association
as Case Date identity. Neither route consults titles, labels, type names, or dates, and neither may
fall through to the other source's editor or the New Event wizard.

Mouse and Enter/Space activation share the same resolved target. Cards require a primary, stationary
click, consume successful activation, remain keyboard focusable, and isolate embedded controls.
Single-flight occurrence/event guards prevent duplicate editors, while open-state, tenant, Case, and
generation checks reject asynchronous results after navigation or session changes. Feed projection,
ordering, filtering, LiveBus refresh, the unified New Event wizard, and all database/linkage schema
remain unchanged.

## Step 1 — persisted linkage and mutation foundation (complete)

### Inventory and authority

`CaseDates` (`Id` bigint, tenant and Case/type IDs, `StartsAt`/`EndsAt`, `AllDay`, notes,
soft-delete metadata, actor timestamps, and `RowVer`) owns case-date identity, Case ownership,
type, semantic value, and lifecycle. `CaseDateTypes` is a global/tenant overlay keyed by stable
lowercase `SystemKey`; protected semantic-role mappings continue to resolve SOL and TCN without
numeric IDs. Existing occurrence writes enter through `CaseServicePort` → `CaseServiceAdapter` →
`CaseDateDao`; each validates session tenant, actor, Case/type ownership and optimistic concurrency,
and writes PHI/entity audit in its DAO-owned transaction.

`CalendarEvents` owns `CalendarEventId`, event type, optional Case/Task, title/description,
`StartsAt`/`EndsAt`, `AllDay`, source identity, assignment, completion/cancellation, and timestamps.
`CalendarEventDao` is reached by desktop `CalendarService`; create/update/delete currently own
separate DAO connections, touch an associated Case, and assignment notification remains a
post-write service concern. The inspected deployed Calendar foundation contains no recurrence,
attendee, reminder, feed-membership, restoration/soft-delete, timezone-id, or external-sync columns
or tables. The feed reads persisted events plus projections. This step does not invent those
contracts or change them. Calendar deletion remains hard deletion; cancellation remains the
existing calendar lifecycle marker. Calendar had no optimistic token, so this foundation adds
`CalendarEvents.RowVer` without changing existing UI behavior.

### Link and cardinality

The nullable `CalendarEvents.CaseDateId` is the sole authoritative link. A composite foreign key
to `(CaseDates.ShaleClientId, CaseDates.Id)` enforces tenant equality and a filtered unique index
permits at most one Calendar event for a Case Date. Both sides may remain unlinked. There is no
circular or cascading FK and no historical rows are paired. A missing or hard-deleted Calendar
event leaves the Case Date intact; a Case Date cannot be hard-deleted while referenced. Soft delete
does not implicitly unlink, delete, cancel, or restore its event. Later flows must explicitly unlink
before an intentionally independent lifecycle operation. Restoring a Case Date preserves its link.

Recurring identity cannot be inferred safely. The current persisted Calendar schema has no
recurrence representation; any future recurrence master or occurrence model must be reviewed
before linking. A recurring series/occurrence must not use this boundary until it can prove exactly
one semantic Case Date identity. Reminders and attendees never participate in the link.

### Stable type mappings

`CalendarCaseDateTypeMappings` is strict tenant-owned configuration using event/date type IDs, never
labels. Two filtered unique indexes establish a one-to-one active mapping per tenant; inactive rows
retain history. `CaseDateToCalendar` and `CalendarToCaseDate` independently authorize later
directions, and at least one must be enabled. Global and tenant-configured effective types can be
referenced, but the mutation boundary verifies tenant visibility. Unmapped types remain unmapped.
No mappings are seeded: the existing stable keys prove type identity but do not prove that tenants
want bidirectional synchronization. SOL, TCN, meetings, calls, reminders, and every other type
therefore remain unchanged until explicit administration is added.

The migration extends the single established enabled `TenantFilter` policy only after rejecting a
missing, disabled, or ambiguously named policy. The mapping table has the strict tenant predicate as
one `FILTER` plus `BLOCK AFTER INSERT`, `BLOCK BEFORE UPDATE`, and `BLOCK AFTER UPDATE` predicates.
Consequently a foreign-tenant write is rejected rather than inserted and then hidden, and neither
the old nor new tenant key of an update can evade the trusted session tenant. The overlay-aware
mapping trigger additionally requires both audit user IDs to resolve to `Users.id` in the mapping
tenant. It intentionally does not reject deleted historical actors because the established audit
column contract preserves authoritative actor IDs independent of current user lifecycle.

Reruns validate definitions rather than names alone: prerequisite column types/nullability,
composite-key and filtered-index key order/filters, FK endpoints and no-action behavior, mapping
columns/defaults/check/primary key, active uniqueness, trigger validation, and every RLS operation.
A compatible partial table receives only safely missing defaults, constraints, indexes, FKs,
trigger, and predicates. Incompatible partial shapes or same-named incompatible objects abort the
transaction with an actionable error; the migration never drops the table or repairs it by
discarding/reinterpreting rows.

### Shared transaction and ownership contract

`CaseCalendarMutationService` is the UI-neutral orchestration seam. It derives tenant from the
connection's trusted `SESSION_CONTEXT`, derives actor from an injected authenticated context, and
then checks actor membership, both tenant-scoped records, their Case IDs, active mapping, deletion/
cancellation state, existing linkage, and both `RowVer` tokens while holding update locks. It
updates only the link and appends one PHI-safe `CASE_DATE/LINKED` entity-action audit on the same
connection before commit; any validation, stale write, uniqueness, or audit failure rolls back.
Lower-level Calendar and Case Date public mutation services are not called, preventing nested
transactions and duplicate audit/timeline events.

For later synchronization, a Case Date value maps to Calendar start/end as the same SQL local
`datetime2` values and `AllDay` flag—no timezone conversion. Calendar owns subsequent scheduling,
so Calendar edits may update the Case Date semantic value only when `CalendarToCaseDate` is enabled
and a later atomic flow supplies both row versions. Default title is the type presentation and
default description may derive from Case Date notes, but neither is identity. Changing either type
must atomically validate the new active mapping or explicitly unlink; it must never resolve by
display text. Linked operations produce one audit for each domain mutation actually performed and
must not manufacture Case Timeline events.

### Deferred work

Case → Calendar creation/update, Calendar → Case Date creation/update, mapping administration UI,
unlink/delete/restore UI integration, recurrence design, and conservative opt-in reconciliation of
existing data are later roadmap steps. Neither bidirectional creation flow is complete. All event
types and Case Date Types currently require later tenant administrative mapping.

## Roadmap

1. **Complete:** persisted one-to-one link, stable mapping schema, and shared atomic link boundary.
2. Deferred: Case Date creates/updates Calendar event.
3. Deferred: Calendar event creates/updates Case Date.
4. Deferred: conservative administrative reconciliation of existing records.

## Step 4 — transactional synchronization (complete)

### Mutation-path inventory and entry points

Before Step 4, authoritative Case Date occurrence writes were `CaseDateDao.createCaseDate`,
`updateCaseDate`, `deleteCaseDate`, and `restoreCaseDate`; they already owned tenant/actor checks,
RowVer validation, the SQL transaction, Case touch, PHI audit, entity-action audit, authoritative
reload, and controller publication of the established case-date live-update. They do not create
timeline or assignment-notification rows. Calendar writes were `CalendarEventDao.create`, `update`,
and `deleteCalendarEvent`, reached through `CalendarService`; they touched the Case, while assignment
notification and live publication were existing post-return service/controller effects. Calendar
has hard delete and `IsCancelled`, but no restore endpoint or durable timeline contract.

Step 4 integrates the connection-bound `CaseCalendarSynchronizer` directly into those DAO mutation
transactions. It is deliberately not public, owns no connection, never commits, and is not called
recursively for counterpart writes. Calendar DAO now obtains both tenant and actor from authenticated
SQL session context, validates active membership, and treats submitted tenant identity only as an
equality assertion. No worker, startup reconciliation, seed, backfill, polling route, or matching
heuristic is introduced.

### Field ownership and projection

Identity is exclusively `CalendarEvents.CaseDateId`; an unlinked record is never paired. Case Date
owns Case identity and `CaseDateTypeId`; Calendar owns event title, description, Task/source details,
assignment, and completion. Only `StartsAt`, `EndsAt`, and `AllDay` are shared projections. An active
mapping projects the destination type ID. Case-Date-created events receive the mapped event type's
presentation name only at creation and a fixed `CASE_DATE` source marker; later synchronization does
not overwrite title, description, Task/source fields, assignment, or completion. Calendar-created
Case Dates leave Notes null. SQL local date-times are copied without timezone conversion.

### Lifecycle, safety, and effects

Eligible creates insert exactly one counterpart and establish `CaseDateId` in the same transaction.
Eligible linked updates update that row using its locked RowVer. A type change re-reads the active,
direction-specific mapping: a valid replacement changes the counterpart type and shared projection;
a missing/inactive direction unlinks but does not modify the former counterpart. Ordinary later edits
of an unlinked row do not create a counterpart; only creation or an explicit source-type transition
may create one. Case Date soft-delete cancels its linked event only when it was not already cancelled,
and restore reactivates only a cancellation whose latest entity audit proves it was caused by Case
Date synchronization. Calendar cancellation soft-deletes its linked Case Date and cancellation reversal
restores only a deletion whose latest entity audit proves Calendar synchronization ownership. Calendar hard-delete soft-deletes its counterpart,
then unlinks before deleting the event, preserving Case Date history.

All source and counterpart rows and mapping key ranges are locked in the tenant transaction; updates
include RowVer predicates and stable domain messages replace stale/missing/duplicate outcomes. The
composite FK and filtered unique link remain final race defenses. Counterpart writes append one
durable entity-action audit on the same connection with IDs and `SYNCHRONIZATION_DIRECTION` only.
They intentionally do not manufacture timeline, notification, or live-update effects: the single
authoritative source path continues to publish through the existing infrastructure, avoiding
recursive and duplicate effects. Audit integration uses the existing entity-action schema and
requires no migration.
