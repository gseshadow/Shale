# Case Dates Architecture

*Last updated: 2026-08-04*

Case dates are authoritative legal and factual dates attached to cases. Phases 1A through 1C are the foundation: `CaseDateTypes` and `CaseDates` schema/RLS/constraints/seeds, effective selector and historical read models, and actor-aware occurrence mutations. Phases 2A and 2B complete date-type administration before Phase 2C Case View occurrence management is treated as complete. Phase 3 has not begun.

## Ownership model

* `CaseDateTypes` defines customizable authoritative case-date meanings using Shale's global/tenant overlay lookup pattern.
* `CaseDates` stores case-owned occurrences for those meanings and allows multiple occurrences of the same type on one case.
* `CalendarEvents` remains the store for manually created user/calendar events.
* The unified calendar is a projection hub, not the owner of dates.
* Other domains must not copy their dates into `CalendarEvents`; Tasks own task due dates, Material Requests own request/follow-up dates, workflow fields on Cases own lifecycle dates, and CalendarEvents owns manual events.
* Existing fixed legal/factual `Cases` columns remain authoritative temporarily until a later explicitly verified migration moves them into `CaseDates`.
* Workflow/lifecycle dates such as accepted, denied, closed, fee-agreement signed, and non-engagement letter sent remain separate unless deliberately reclassified in a later phase.

## Tenant, overlay, and calendar security

`CaseDateTypes` uses tenant-or-global RLS so a tenant can see global definitions and its own overrides/custom rows. `CaseDates` uses strict tenant RLS. The database enforces that each `CaseDates` row references a case with the same tenant through a composite case foreign key.

Because a case-date type can be global or tenant-owned, SQL Server cannot fully express the allowed type relationship as a simple composite foreign key. Backend services transactionally validate that a referenced type is either global or belongs to the same tenant as the case date. They also validate actor tenant membership and `SupportsTime`/`AllDay` consistency.

Tenant administration never mutates global seed rows directly. Editing or activating/deactivating a global date type creates or updates a current-tenant override with the same stable `SystemKey`. A deleted tenant override is a reset marker: the global default becomes effective again. Tenant custom rows without a global-matching key are tenant-owned values and removal soft-deletes only the tenant row. Cross-tenant rows are not available for mutation, and mutation is never authorized by date-type id alone.

The current `CalendarEvents` and `CalendarEventTypes` foundation has no RLS predicates. That is an unrelated security finding and should be handled by a separate focused migration, not folded into the case-date foundation.

## Phase 1B Java read domain

Phase 1B adds the Java read side only. `EffectiveCaseDateTypeDto` represents selector-ready effective type rows after the modern global/tenant overlay is resolved. `CaseDateDto` represents active case-owned occurrences with stored type identity, effective presentation, SQL Server `datetime2` values mapped directly to `LocalDateTime`, actor display labels, and defensive `RowVer` copies.

The read DAO follows the customizable lookup standard: candidate type rows are limited to global or current-tenant rows; tenant rows win over global rows with the same non-null `SystemKey`; deleted tenant overrides are reset markers and do not suppress the global row; inactive or deleted winners are excluded from selector reads; active tenant-created rows are included; and ordering is deterministic by sort order, name, and id. The occurrence read path does not create, update, synchronize, backfill, or dual-read any `CalendarEvents` rows or fixed `dbo.Cases` legal/factual date columns.

Historical occurrence display is intentionally separate from selector eligibility. Active occurrence reads keep the stored `CaseDateTypeId` and never drop an occurrence because its type is inactive or deleted. If the stored type has a stable key, the current active tenant-effective presentation is applied when available; otherwise the stored referenced type row is used as a safe fallback so old dates remain understandable, including dates that reference inactive or soft-deleted tenant-created types.

## Phase 1C backend mutation domain

Phase 1C adds transactional backend mutations for `CaseDates` occurrences only: create, full-field update/reschedule, soft-delete, and restore. It does not add date-type administration, UI, intake changes, unified-calendar feed changes, notification/reminder behavior, fixed-column migration, backfill, dual reads, or any `CalendarEvents` writes. `CaseDateTypes` remains read-only to this phase except for tenant-safe validation lookups.

Mutation commands carry the authenticated tenant, active actor, authoritative case id, and the occurrence id plus expected SQL Server `RowVer` for update/delete/restore. Create and type replacement require the submitted `CaseDateTypeId` to be the exact active effective selector winner for the actor tenant. Historical type retention is deliberate when an existing occurrence keeps its stored type id.

Every mutation establishes and verifies tenant session context, validates the active actor in the tenant, authorizes through the supplied active case id, and loads the target occurrence by tenant + case id + occurrence id. Occurrence id alone is never sufficient to mutate a row. A stale or missing row version is rejected instead of silently overwriting newer changes.

## Phase 2A backend date-type administration

Phase 2A adds actor-aware Settings administration APIs for `CaseDateTypes` on the existing `CaseServicePort`/`CaseServiceAdapter`/`CaseDateDao` path:

* `listCaseDateTypesForAdministration(int shaleClientId, int actorUserId)` returns global and current-tenant rows with origin, active/deleted state, editable metadata, and `RowVer` for Settings overlay presentation.
* `createCaseDateType(CaseDateTypeCommand)` creates tenant-owned custom rows.
* `updateCaseDateType(CaseDateTypeCommand)` edits tenant-owned rows or creates/updates a tenant override for an eligible global row.
* `setCaseDateTypeActive(SetCaseDateTypeActiveCommand)` activates/deactivates tenant-owned rows or creates/updates a tenant override for an eligible global row.
* `resetCaseDateTypeOverride(ResetCaseDateTypeOverrideCommand)` soft-deletes the tenant override/custom row according to the established reset/remove semantics.

Editable metadata follows the Phase 1A schema: display name, optional stable `SystemKey` for tenant-custom creation, description, `CalendarCategory`, color, `SupportsTime`, sort order, active state, soft deletion, and row-version concurrency. Validation enforces required names, allowed categories, `#RRGGBB` colors, stable-key format, row-version presence for edits/toggles, tenant ownership, admin actor context, and cross-tenant exclusion. Successful mutations return or reload authoritative state. Existing occurrences are not rewritten when a type is deactivated, removed, reset, or shadowed.

Effective active selector reads remain separate from administration reads. New-occurrence selectors use only `listEffectiveCaseDateTypes`; inactive, deleted, reset-marker, and shadowed rows must not leak into ordinary selectors.

## Phase 2B Settings date-type administration UI

Phase 2B adds **Settings > Case Date Types** in the established Settings manager surface. It uses the Phase 2A `CaseServicePort` administration APIs and does not call DAOs directly or calculate persistence semantics outside the service.

The Settings manager supports loading/error/empty states, Add, Edit/Customize, Activate/Deactivate, Reset to Default, Remove, and refresh-after-mutation. Cards distinguish Global/default, Tenant override, Tenant custom, Active/Inactive, category, color, and `SupportsTime` in the same unobtrusive metadata style as other Settings managers.

The UI uses shared Settings cards, shared semantic buttons (`ActionButtonFactory.semantic`/`ControlStyles`), Shale dialog shell/form styling, inline validation through the existing dialog flow, shared color conversion/rendering, and existing confirmation dialogs. Category values are persisted through the backend contract and color values use the same `ColorPicker`/DB color conversion used by comparable Settings lookup managers. Mutations preserve the submitted authoritative id and expected row version; concurrency failures are surfaced to the user and require reload rather than silent retry.

## Phase 2C Case View occurrence UI

Phase 2C adds authoritative occurrence management to the desktop Case View as a dedicated **Dates** section in the established shared section-tab system. Although it was initially implemented before Phases 2A/2B were complete, it has now been verified against the completed Phase 2A/2B foundation.

The Dates section reads active occurrences through the Phase 1B `CaseServicePort.listCaseDatesForCase` boundary and renders deterministic cards. Add/Edit uses `listEffectiveCaseDateTypes`, not the Settings administration list. New occurrences can select only effective active types. Editing an occurrence selects the current effective type when still selectable; otherwise the dialog displays the current historical type as the retained value without adding it to ordinary selectable options.

The dialog respects authoritative `SupportsTime`: non-timed types force all-day values and timed types permit either all-day or timed input. Remove uses Phase 1C soft-delete with row version. Show Removed/Hide Removed uses the dedicated tenant-, actor-, and case-scoped `listDeletedCaseDatesForCase`; Restore uses Phase 1C restore and retains the stored historical type.

Case-date loading is asynchronous on a controller-owned worker executor with generation guards, case snapshots, detached-root protections, explicit loading/empty/failure/Retry/Refresh states, mutation guards, and no PHI in logs or exception text.

## Scope exclusions before Phase 3

Case Dates through Phase 2C intentionally exclude unified-calendar projection work, `CalendarFeedDao` changes for authoritative Case Dates, `CalendarEvents` creation/synchronization/mutation, legacy fixed-date migration, backfill, dual reads/writes, heuristic deduplication, notifications, reminders, calendar routing for projected Case Dates, and calendar drag/reschedule. Phase 3A has not begun.

## Phase 3A unified Calendar projection

Phase 3A projects active authoritative `dbo.CaseDates` occurrences into the existing unified Calendar feed as read-only items. `CaseDates` remains the sole authoritative occurrence store; the projection never inserts, updates, deletes, mirrors, synchronizes, backfills, or caches rows in `dbo.CalendarEvents`.

The unified feed loads `CaseDates` in one set-based range branch alongside manual `CalendarEvents`, projected Tasks, and legacy fixed `dbo.Cases` date fields. The branch is tenant-scoped by `CaseDates.ShaleClientId`, joins the same-tenant active case, excludes soft-deleted occurrences, and uses the existing responsible-attorney case ownership predicate for user schedule filtering. Case-filtered consumers use the existing case id predicate. Occurrence creators, removal actors, and type administrators are not calendar owners.

Range matching uses the unified feed's local `LocalDateTime` boundary contract: `StartsAt < requestedEndExclusive` and `COALESCE(EndsAt, StartsAt) >= requestedStartInclusive`. This returns single dates, `NULL`-end occurrences, ranges that begin before the window, ranges that end after the window, and ranges that span the whole window. The projection preserves stored `StartsAt`, optional `EndsAt`, and authoritative `AllDay` exactly; it does not expand all-day values to end-of-day, add/subtract days, infer all-day from midnight, or convert through UTC/time zones.

Historical type presentation follows the Phase 1B read rule. The stored type relationship is preserved. If the stored type has a stable key and a current active tenant-effective presentation exists, that presentation is used; otherwise the stored type's own name/category/color are used so inactive, deleted, shadowed, or replaced historical types remain understandable without becoming selector choices.

`CaseDateTypes.CalendarCategory` is the layer-classification input. `DEADLINE` maps to the existing `CASE_DEADLINES` layer. `TRIAL`, `HEARING`, `MEDIATION`, `DEPOSITION`, `NOTICE`, `APPOINTMENT`, `MILESTONE`, and `OTHER` map to the existing `OTHER_CASE_DATES` layer. Unknown categories are excluded by the projection rather than misclassified as manual events. No fifth user-facing layer is introduced, and existing layer defaults remain unchanged.

Projected authoritative occurrences use `CASE_DATE:<CaseDates.Id>` as the stable feed key/source identity and `CASE_DATE` as the source discriminator. The occurrence id remains the source record id, so multiple occurrences on one case or of one type remain independent and cannot collide with `EVENT:<CalendarEventsId>`, `TASK:<TaskId>`, or legacy fixed-date keys such as `CASE_SOL:<CaseId>`.

Calendar presentation uses the shared feed model and rendering pipeline: title is the date-type name plus existing case context, details are empty, type color flows through the existing calendar color field/fallback behavior, and notes are never exposed in Calendar titles, labels, tooltips, logs, or exception text. Month, week, day, agenda, and Case View Calendar agenda consumers receive these items through the same unified feed load and local layer filtering. Activation is read-only and routes to the authoritative case's Dates section where supported by the current case view; it must not open the manual Calendar Event editor, enable drag/reschedule, inline delete, or mutate `CaseDates`.

Refresh behavior remains the existing async Calendar feed lifecycle. New, updated/rescheduled, soft-deleted, restored, or presentation-changed case dates appear on the next normal feed load/refresh. No polling loop, background sync worker, duplicate cache, notification scheduler, reminder system, date-type administration behavior, occurrence mutation behavior, legacy fixed-date migration, backfill, dual write, or heuristic deduplication is part of Phase 3A. Legacy fixed `dbo.Cases` date projections coexist independently until a later migration phase defines and verifies a cutover.
