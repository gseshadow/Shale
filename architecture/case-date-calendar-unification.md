# Case Date and Calendar unification

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
