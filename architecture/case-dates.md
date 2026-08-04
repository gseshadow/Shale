# Case Dates Architecture

*Last updated: 2026-08-04*

Case dates are authoritative legal and factual dates attached to cases. Phase 1A adds the database foundation only; it does not add Java models, DAOs, services, UI, intake behavior, calendar-feed behavior, or migration of existing fixed `dbo.Cases` date columns.

## Ownership model

* `CaseDateTypes` defines customizable authoritative case-date meanings using Shale's global/tenant overlay lookup pattern.
* `CaseDates` stores case-owned occurrences for those meanings and allows multiple occurrences of the same type on one case.
* `CalendarEvents` remains the store for manually created user/calendar events.
* The unified calendar is a projection hub, not the owner of dates.
* Other domains must not copy their dates into `CalendarEvents`; Tasks own task due dates, Material Requests own request/follow-up dates, workflow fields on Cases own lifecycle dates, and CalendarEvents owns manual events.
* Existing fixed legal/factual `Cases` columns remain authoritative temporarily until a later explicitly verified migration moves them into `CaseDates`.
* Workflow/lifecycle dates such as accepted, denied, closed, fee-agreement signed, and non-engagement letter sent remain separate unless deliberately reclassified in a later phase.

## Tenant and calendar security

`CaseDateTypes` uses tenant-or-global RLS so a tenant can see global definitions and its own overrides/custom rows. `CaseDates` uses strict tenant RLS. The database enforces that each `CaseDates` row references a case with the same tenant through a composite case foreign key.

Because a case-date type can be global or tenant-owned, SQL Server cannot fully express the allowed type relationship as a simple composite foreign key. Phase 1C services must transactionally validate that a referenced type is either global or belongs to the same tenant as the case date. Phase 1C must also validate actor tenant membership and `SupportsTime`/`AllDay` consistency.

The current `CalendarEvents` and `CalendarEventTypes` foundation has no RLS predicates. That is an unrelated security finding and should be handled by a separate focused migration, not folded into the case-date foundation.

## Phase 1B Java read domain

Phase 1B adds the Java read side only. `EffectiveCaseDateTypeDto` represents selector-ready effective type rows after the modern global/tenant overlay is resolved. `CaseDateDto` represents active case-owned occurrences with stored type identity, effective presentation, SQL Server `datetime2` values mapped directly to `LocalDateTime`, actor display labels, and defensive `RowVer` copies.

The read DAO follows the customizable lookup standard: candidate type rows are limited to global or current-tenant rows; tenant rows win over global rows with the same non-null `SystemKey`; deleted tenant overrides are reset markers and do not suppress the global row; inactive or deleted winners are excluded from selector reads; active tenant-created rows are included; and ordering is deterministic by sort order, name, and id. The occurrence read path does not create, update, synchronize, backfill, or dual-read any `CalendarEvents` rows or fixed `dbo.Cases` legal/factual date columns.

Historical occurrence display is intentionally separate from selector eligibility. Active occurrence reads keep the stored `CaseDateTypeId` and never drop an occurrence because its type is inactive or deleted. If the stored type has a stable key, the current active tenant-effective presentation is applied when available; otherwise the stored referenced type row is used as a safe fallback so old dates remain understandable, including dates that reference inactive or soft-deleted tenant-created types.

## Phase 1C backend mutation domain

Phase 1C adds transactional backend mutations for `CaseDates` occurrences only: create, full-field update/reschedule, soft-delete, and restore. It does not add date-type administration, UI, intake changes, unified-calendar feed changes, notification/reminder behavior, fixed-column migration, backfill, dual reads, or any `CalendarEvents` writes. `CaseDateTypes` remains read-only to this phase except for tenant-safe validation lookups.

Mutation commands carry the authenticated tenant, active actor, authoritative case id, and the occurrence id plus expected SQL Server `RowVer` for update/delete/restore. The service boundary validates positive ids, required `StartsAt`, required row versions, and `EndsAt >= StartsAt`; it trims optional notes without adding a repository-specific length cap because the database column is `nvarchar(max)`. Stored `datetime2` values are treated as application date/time values and mapped directly to `LocalDateTime`; Phase 1C performs no UTC/local conversion, `ZoneId` conversion, offset shifting, start-of-day shifting, or end-of-day expansion.

Create and type replacement require the submitted `CaseDateTypeId` to be the exact active effective selector winner for the actor tenant. A tenant override shadows its global row, so submitting the shadowed global id is rejected. Inactive rows, deleted rows, reset-marker override rows, and rows owned by another tenant are also rejected. `SupportsTime = 0` requires `AllDay = true`; `SupportsTime = 1` allows either all-day or timed values. Phase 1C never infers `AllDay` from midnight values.

Historical type retention is deliberate. If an existing occurrence keeps its stored type id during update, the mutation validates that stored type with a tenant-safe historical lookup and applies `SupportsTime` from that authoritative stored row, even when the row is now inactive or deleted. If the caller changes the type id, the replacement must pass the current exact active-effective selector validation. Restore preserves the stored type id and permits tenant-safe inactive/deleted historical types because restore is reactivating an existing occurrence, not selecting a new definition; a missing or cross-tenant stored type remains invalid.

Every mutation establishes and verifies tenant session context, validates the active actor in the tenant, authorizes through the supplied active case id, and loads the target occurrence by tenant + case id + occurrence id. Occurrence id alone is never sufficient to mutate a row, so a date id cannot bypass case authorization or cross-tenant checks. Soft-deleted rows are excluded from ordinary reads and updates/deletes; restore uses a dedicated soft-deleted lookup path.

Update, soft-delete, and restore use SQL Server `RowVer` optimistic concurrency in the write predicate after tenant/case/occurrence existence has been established. A stale or missing row version is rejected instead of silently overwriting newer changes, and returned DTOs and commands defensively copy row-version byte arrays.

Each real mutation is atomic: the occurrence write, parent case `UpdatedAt` touch, PHI write audit, and append-only entity-action audit use the same connection and transaction and commit or roll back together. Verified semantic no-op updates return the current occurrence after authorization and row-version validation without changing `UpdatedAt`, advancing `RowVer`, touching the case, or emitting audit entries.

## Phase 2C Case View occurrence UI

Phase 2C adds authoritative occurrence management to the desktop Case View as a dedicated **Dates** section in the established shared section-tab system. The section is activated through the same exclusive section-root path as Overview, Details, Parties, Tasks, Calendar, Requests, Links, and Timeline, so inactive roots remain invisible, unmanaged, and mouse-transparent. Phase 2C does not create a separate window or navigation stack for case-date management.

The Dates section reads active occurrences through the Phase 1B `CaseServicePort.listCaseDatesForCase` boundary and renders deterministic cards ordered by start, end, type name, and occurrence id when the service result is not already authoritative. Each active card displays the backend-provided effective or historical type presentation, the stored all-day or timed range, optional notes, an unobtrusive historical/inactive type marker when the current stored type is not in the effective selector list, and Edit and Remove actions. Date/time values remain application `LocalDateTime` values; the UI does not apply `ZoneId`, UTC conversion, offset conversion, end-of-day expansion, or date shifting, and it never infers all-day status from midnight.

The same dialog is used for Add Date and Edit Date. It uses the shared semantic button API, the shared form-control shell, and the shared color-coded dropdown selector for database lookup DTOs with authoritative colors. New occurrences can select only effective active types returned by `listEffectiveCaseDateTypes`. Editing an occurrence selects the current effective type when it is still selectable; otherwise the dialog displays the current historical type as the retained value without adding it to ordinary selectable options. If the user changes the type, the replacement id must be one of the effective selector options and the Phase 1C service remains authoritative for exact overlay-winner validation.

The dialog respects `SupportsTime`: non-timed types force all-day values and disable time entry, while timed types permit either all-day or timed input. Start date is required, timed start time is required only for timed entries, end input is optional, partial end input is rejected, and end must not precede start. All-day ends preserve the selected calendar date at date-only precision and are not expanded to `23:59:59` or shifted by a day. Opening and saving unchanged values preserves the original stored values so Phase 1C semantic no-op handling remains available.

Remove uses the existing semantic danger confirmation pattern and calls the Phase 1C soft-delete operation with tenant, actor, authoritative case id, occurrence id, and expected row version. Phase 2C also supports Show Removed/Hide Removed. Removed rows are loaded only through the dedicated tenant- and case-scoped `listDeletedCaseDatesForCase` service read, preserving active-read soft-delete filtering and the historical type presentation fallback. Removed cards are visually distinct, show the stored range and removed state, and expose Restore only. Restore calls the Phase 1C restore operation with the deleted occurrence's expected row version and retains the stored historical type.

Case-date loading is asynchronous on a controller-owned worker executor and marshals updates back to the JavaFX application thread. Each load has a generation guard and case id snapshot; navigating away, changing cases, closing/detaching the view, or superseding a request invalidates late results so they cannot populate another case or an inactive/detached root. Loading, empty, failure, Retry, Refresh, and mutation refresh states are explicit. Mutation failures keep the dialog/state available for correction, and optimistic-concurrency conflicts are presented as changed-elsewhere failures requiring a reload rather than an automatic retry with a newer row version. Notes and other PHI are not written to UI logs or exception text.

`CaseDates` remains the owner of these authoritative occurrences. Phase 2C intentionally excludes unified-calendar projection work, `CalendarEvents` synchronization, legacy fixed-date migration, backfill, dual reads, notifications, reminders, and additional date-type administration.
