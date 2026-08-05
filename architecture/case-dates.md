# Case Dates Architecture

*Last updated: 2026-08-04*

Case dates are authoritative legal and factual dates attached to cases. Phases 1A through 1C are the foundation: `CaseDateTypes` and `CaseDates` schema/RLS/constraints/seeds, effective selector and historical read models, and actor-aware occurrence mutations. Phases 2A and 2B complete date-type administration before Phase 2C Case View occurrence management is treated as complete. Phase 3A is complete; no later Phase 3 migration or cutover phase has begun.

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

## Scope exclusions before Phase 3A

Case Dates through Phase 2C intentionally excluded unified-calendar projection work, `CalendarFeedDao` changes for authoritative Case Dates, `CalendarEvents` creation/synchronization/mutation, legacy fixed-date migration, backfill, dual reads/writes, heuristic deduplication, notifications, reminders, calendar routing for projected Case Dates, and calendar drag/reschedule. Phase 3A is now complete. Later Phase 3 migration/cutover work, including legacy fixed-date migration, backfill, dual reads/writes, heuristic deduplication, notifications, reminders, calendar drag/reschedule, and Case Date mutation changes, has not begun.

## Phase 3A unified Calendar projection

Phase 3A projects active authoritative `dbo.CaseDates` occurrences into the existing unified Calendar feed as read-only items. `CaseDates` remains the sole authoritative occurrence store; the projection never inserts, updates, deletes, mirrors, synchronizes, backfills, or caches rows in `dbo.CalendarEvents`.

The unified feed loads `CaseDates` in one set-based range branch alongside manual `CalendarEvents`, projected Tasks, and legacy fixed `dbo.Cases` date fields. The branch is tenant-scoped by `CaseDates.ShaleClientId`, joins the same-tenant active case, excludes soft-deleted occurrences, and uses the existing responsible-attorney case ownership predicate for user schedule filtering. Case-filtered consumers use the existing case id predicate. Occurrence creators, removal actors, and type administrators are not calendar owners.

Range matching uses the unified feed's local `LocalDateTime` boundary contract: `StartsAt < requestedEndExclusive` and `COALESCE(EndsAt, StartsAt) >= requestedStartInclusive`. This returns single dates, `NULL`-end occurrences, ranges that begin before the window, ranges that end after the window, and ranges that span the whole window. The projection preserves stored `StartsAt`, optional `EndsAt`, and authoritative `AllDay` exactly; it does not expand all-day values to end-of-day, add/subtract days, infer all-day from midnight, or convert through UTC/time zones.

Historical type presentation follows the Phase 1B read rule. The stored type relationship is preserved. If the stored type has a stable key and a current active tenant-effective presentation exists, that presentation is used; otherwise the stored type's own name/category/color are used so inactive, deleted, shadowed, or replaced historical types remain understandable without becoming selector choices.

`CaseDateTypes.CalendarCategory` is the layer-classification input. `DEADLINE` maps to the existing `CASE_DEADLINES` layer. `TRIAL`, `HEARING`, `MEDIATION`, `DEPOSITION`, `NOTICE`, `APPOINTMENT`, `MILESTONE`, and `OTHER` map to the existing `OTHER_CASE_DATES` layer. Unknown categories are excluded by the projection rather than misclassified as manual events. No fifth user-facing layer is introduced, and existing layer defaults remain unchanged.

Projected authoritative occurrences use `CASE_DATE:<CaseDates.Id>` as the stable feed key/source identity and `CASE_DATE` as the source discriminator. The occurrence id remains the source record id, so multiple occurrences on one case or of one type remain independent and cannot collide with `EVENT:<CalendarEventsId>`, `TASK:<TaskId>`, or legacy fixed-date keys such as `CASE_SOL:<CaseId>`.

Calendar presentation uses the shared feed model and rendering pipeline: title is the date-type name plus existing case context, details are empty, type color flows through the existing calendar color field/fallback behavior, and notes are never exposed in Calendar titles, labels, tooltips, logs, or exception text. Month, week, day, agenda, and Case View Calendar agenda consumers receive these items through the same unified feed load and local layer filtering. Activation is read-only and routes to the authoritative case's Dates section where supported by the current case view; it must not open the manual Calendar Event editor, enable drag/reschedule, inline delete, or mutate `CaseDates`.

Refresh behavior remains the existing async Calendar feed lifecycle. New, updated/rescheduled, soft-deleted, restored, or presentation-changed case dates appear on the next normal feed load/refresh. No polling loop, background sync worker, duplicate cache, notification scheduler, reminder system, date-type administration behavior, occurrence mutation behavior, legacy fixed-date migration, backfill, dual write, or heuristic deduplication is part of Phase 3A. Legacy fixed `dbo.Cases` date projections coexist independently until a later migration phase defines and verifies a cutover.


## Legacy fixed-date audit before Phase 3B

This section is an evidence-gathering audit only. Phase 3B has not been implemented, and this audit does not authorize schema changes, new `CaseDateTypes`, backfill, cutover, dual reads, dual writes, calendar deduplication, notification/reminder changes, `CalendarEvents` changes, synchronization logic, UI changes, or any runtime behavior change.

### Repository baseline and protections verified

* Current branch at audit time: `work`; recent history includes Phase 3A read-only projection commits ending at `c56e768 Merge pull request #1302 from gseshadow/codex/implement-read-only-case-dates-projection`.
* Phase 3A coexistence is implemented by the unified Calendar feed loading active `dbo.CaseDates` alongside manual events, tasks, and legacy fixed `dbo.Cases` date fields. Authoritative occurrences use source key `CASE_DATE:<CaseDates.Id>` and source type `CASE_DATE`; legacy fixed dates continue to use `CASE_*:<CaseId>` keys.
* Tenant isolation remains a required migration constraint: `CaseDates` is tenant-scoped by `ShaleClientId` and its composite case foreign key requires `(ShaleClientId, CaseId)` to match `dbo.Cases`. Future audits and data profiling must not reason about a case/date/type id without tenant context.
* Overlay semantics remain unchanged: `CaseDateTypes` supports global built-ins plus tenant-owned rows/overrides selected by stable `SystemKey`; Phase 3B must not destabilize historical type presentation.
* No safe database connection was available from repository-local configuration during this audit. Production data counts below are therefore database-dependent and must be gathered with the read-only script in this section.

### Candidate fixed fields discovered on `dbo.Cases`

The audit found the following `date` columns stored directly on `dbo.Cases` and represented by the authoritative Java case detail model: `CallerDate`, `AcceptedDate`, `ClosedDate`, `DeniedDate`, `DateOfMedicalNegligence`, `DateMedicalNegligenceWasDiscovered`, `DateOfInjury`, `StatuteOfLimitations`, `TortNoticeDeadline`, `DiscoveryDeadline`, `DateFeeAgreementSigned`, and `DateNonEngagementLetterSent`. Related non-date workflow flags `FeeAgreementSigned` and `NonEngagementLetterSent` are included only to classify their paired dates. `CallerTime` is a `time` column on `dbo.Cases`; it is paired with `CallerDate` for intake/caller workflow meaning but is not projected by the unified Calendar and is classified out of scope for CaseDates migration until product defines timed intake semantics.

All discovered fixed-date columns are documented as `date` in the schema inventory and are nullable in the Java DTO/update paths because the DAO consistently reads with nullable date conversion and writes with nullable date setters. The schema inventory does not document check constraints on these legacy fields. Current migrations in this repository add `CaseDates`/`CaseDateTypes` but do not migrate, remove, or dual-write any of these fixed columns.

### Writer/reader/classification matrix

| Field | Schema type | Writers | Readers | UI editor | Calendar projection | Workflow side effects | Likely classification | Evidence |
| ----- | ----------- | ------- | ------- | --------- | ------------------- | --------------------- | --------------------- | -------- |
| `StatuteOfLimitations` | `dbo.Cases.StatuteOfLimitations date NULL` | User-entered create/update through `CaseDao.createCase(...)`, `CaseDao.updateCase(...)`, and details/overview update flows; test fixtures only. | Case overview/detail queries, My Cases sort/filter/radar, web create/edit/detail, unified Calendar. | Desktop new intake, Case Overview nullable edit, Case Details `DatePicker`; web new/edit case form. | Legacy source key `CASE_SOL:<CaseId>`, source type `STATUTE_OF_LIMITATIONS`, title `SOL`, layer `CASE_DEADLINES`; independent of `CASE_DATE:<Id>`. | No repository workflow transition was found to derive or own it. | `MIGRATION_CANDIDATE` | Deadline name, direct user editing, deadline layer projection, no workflow flag dependency. Proposed system key: existing built-in if present, otherwise `statute_of_limitations`; category `DEADLINE`; `SupportsTime=false`; all-day single date; legacy value remains authoritative until explicit backfill/cutover. |
| `TortNoticeDeadline` | `dbo.Cases.TortNoticeDeadline date NULL` | User-entered create/update through `CaseDao.createCase(...)`, `CaseDao.updateCase(...)`, details/overview update flows; test fixtures only. | Case overview/detail queries, My Cases tort notice radar/sort, web create/edit/detail, unified Calendar. | Desktop new intake, Case Overview nullable edit, Case Details `DatePicker`; web new/edit case form. | Legacy source key `CASE_TORT:<CaseId>`, source type `TORT_NOTICE_DEADLINE`, title `Tort Notice`, layer `CASE_DEADLINES`. | No repository workflow transition was found to derive or own it. | `MIGRATION_CANDIDATE` | Deadline semantics and independent user editing. Proposed system key: `tort_notice_deadline`; category `DEADLINE` or product-approved `NOTICE` only if deadline filtering changes; `SupportsTime=false`; all-day single date; legacy authority during transition. |
| `DiscoveryDeadline` | `dbo.Cases.DiscoveryDeadline date NULL` | User-entered update through `CaseDao.updateCase(...)` and details `DatePicker`; test fixtures only. | Case detail queries and unified Calendar classification. | Desktop Case Details `DatePicker`; not found in web editor. | Legacy source key `CASE_DISCOVERY:<CaseId>`, source type `DISCOVERY_DEADLINE`, title `Discovery`, layer `CASE_DEADLINES`. | No repository workflow transition was found to derive or own it. | `MIGRATION_CANDIDATE` | Deadline semantics and direct date editing. Proposed system key: `discovery_deadline`; category `DEADLINE`; `SupportsTime=false`; all-day single date; web/client parity risk before cutover. |
| `DateOfInjury` | `dbo.Cases.DateOfInjury date NULL` | User-entered create/update through intake, desktop detail/overview, web new/edit; PHI audit runs when updated in core DAO update paths. | Case overview/detail, search/export-style case lists, web detail, unified Calendar, PHI registry. | Desktop new intake, Case Overview, Case Details; web new/edit. | Legacy source key `CASE_INJURY:<CaseId>`, source type `CASE_DATE`, title `Date of Injury`, layer `OTHER_CASE_DATES`. | No transition owns it, but it is PHI/factual case data and may drive limitations externally. | `MIGRATION_CANDIDATE` | Factual occurrence with direct editing; PHI/audit obligations must be preserved. Proposed system key: `date_of_injury`; category `OTHER` unless product creates `FACTUAL`; `SupportsTime=false`; all-day single date; cutover must preserve PHI audit behavior. |
| `DateOfMedicalNegligence` | `dbo.Cases.DateOfMedicalNegligence date NULL` | User-entered create/update through intake and desktop details/overview; PHI audit in DAO update. | Case detail, overview, unified Calendar, PHI registry; limited/non-web usage found. | Desktop new intake, Case Overview, Case Details. | Legacy source key `CASE_MED_NEG:<CaseId>`, source type `CASE_DATE`, title `Medical Negligence`, layer `OTHER_CASE_DATES`. | No workflow transition found; PHI/factual medical timing. | `MIGRATION_CANDIDATE` | Factual occurrence, directly edited; migration must preserve PHI audit/no-PHI calendar presentation. Proposed key `medical_negligence`; category `OTHER`; `SupportsTime=false`; all-day. |
| `DateMedicalNegligenceWasDiscovered` | `dbo.Cases.DateMedicalNegligenceWasDiscovered date NULL` | User-entered create/update through intake and desktop details; PHI audit in DAO update. | Case detail and unified Calendar, PHI registry. | Desktop new intake and Case Details. | Legacy source key `CASE_MED_NEG_DISCOVERED:<CaseId>`, source type `CASE_DATE`, title `Medical Negligence Discovered`, layer `OTHER_CASE_DATES`. | No workflow transition found; PHI/factual discovery date. | `MIGRATION_CANDIDATE` | Factual occurrence, directly edited. Proposed key `medical_negligence_discovered`; category `OTHER`; `SupportsTime=false`; all-day; product should confirm label. |
| `CallerDate` | `dbo.Cases.CallerDate date NULL` | User-entered create/update through intake/detail and web new case; import/organization/contact readers preserve value; list filtering uses it. | Broad case lists, intake/date filters, organization/contact lookups, web, unified Calendar. | Desktop new intake and Case Details; web new case intake date. | Legacy source key `CASE_CALLER:<CaseId>`, source type `CASE_DATE`, title `Intake`, layer `OTHER_CASE_DATES`. | Owns intake workflow state together with `CallerTime` and `IntakeTakenByUserId`; case list ordering/filtering depends on it. | `AMBIGUOUS_REQUIRES_DECISION` | Although calendar-projected, it appears to be an intake workflow timestamp rather than a legal occurrence. Replacing with a freely editable Case Date could break intake reporting/filtering and caller-time semantics. Product must decide whether calendar should remain read-only projection of intake or if an occurrence type is desired. |
| `CallerTime` | `dbo.Cases.CallerTime time NULL` | User-entered intake/detail update. | Case detail/intake workflows; not projected by Calendar. | Desktop intake/details. | None. | Paired with caller/intake workflow. | `OUT_OF_SCOPE` | Time-only companion to `CallerDate`; no existing `CaseDates` all-day/timed mapping can be deterministic without product rules. |
| `AcceptedDate` | `dbo.Cases.AcceptedDate date NULL` | Workflow transition writer `CaseDao.updatePrimaryStatus(...)` sets it to current date when status system key becomes `accepted` and it is null; desktop details can also edit directly. | Case detail/list/export-style summaries and unified Calendar. | Desktop Case Details `DatePicker`. | Legacy source key `CASE_ACCEPTED:<CaseId>`, source type `CASE_DATE`, title `Accepted`, layer `OTHER_CASE_DATES`. | Status workflow milestone; transition auto-defaults and preserves existing value. | `WORKFLOW_TIMESTAMP` | Owned by accepted status lifecycle. Freely editable Case Date would decouple the milestone from status transition semantics and lifecycle audit. Calendar projection should remain a read-only view unless product separates milestone from status. |
| `DeniedDate` | `dbo.Cases.DeniedDate date NULL` | Workflow transition writer `CaseDao.updatePrimaryStatus(...)` sets it to current date when status system key becomes `denied` and it is null; desktop details can also edit directly. | Case detail/list/export-style summaries and unified Calendar. | Desktop Case Details `DatePicker`. | Legacy source key `CASE_DENIED:<CaseId>`, source type `CASE_DATE`, title `Denied`, layer `OTHER_CASE_DATES`. | Status workflow milestone. | `WORKFLOW_TIMESTAMP` | Owned by denied status lifecycle; direct editing is repair/override-like rather than evidence of a calendar occurrence. Keep read-only Calendar projection unless product changes lifecycle design. |
| `ClosedDate` | `dbo.Cases.ClosedDate date NULL` | Workflow transition writer `CaseDao.updatePrimaryStatus(...)` sets it to current date when status system key becomes `closed` and it is null; desktop details can also edit directly. | Case detail/list/export-style summaries and unified Calendar. | Desktop Case Details `DatePicker`. | Legacy source key `CASE_CLOSED:<CaseId>`, source type `CASE_DATE`, title `Closed`, layer `OTHER_CASE_DATES`. | Status workflow milestone; closed/open state is also represented by status lifecycle. | `WORKFLOW_TIMESTAMP` | Owned by closed status lifecycle. Migration to user-managed occurrence could break status reporting and closed-case behavior. |
| `DateFeeAgreementSigned` with `FeeAgreementSigned` | `dbo.Cases.DateFeeAgreementSigned date NULL`; flag `FeeAgreementSigned bit` | User-entered details update; UI listener defaults date to today when the flag is checked and date is blank. | Case detail and unified Calendar. | Desktop Case Details checkbox plus `DatePicker`. | Legacy source key `CASE_FEE_AGREEMENT:<CaseId>`, source type `CASE_DATE`, title `Fee Agreement Signed`, layer `OTHER_CASE_DATES`. | Paired with fee agreement workflow flag; date defaulting is driven by flag selection. | `WORKFLOW_TIMESTAMP` | The owning fact is the signed flag/milestone. A standalone editable Case Date could disagree with `FeeAgreementSigned`. Keep legacy authority unless product defines a client-agreement milestone migration preserving flag consistency. |
| `DateNonEngagementLetterSent` with `NonEngagementLetterSent` | `dbo.Cases.DateNonEngagementLetterSent date NULL`; flag `NonEngagementLetterSent bit` | User-entered details update; UI listener defaults date to today when flag is checked and date is blank. | Case detail, task/notification case cards use the flag, and unified Calendar uses the date. | Desktop Case Details checkbox plus `DatePicker`. | Legacy source key `CASE_NON_ENGAGEMENT:<CaseId>`, source type `CASE_DATE`, title `Non-Engagement Letter Sent`, layer `OTHER_CASE_DATES`. | Paired with non-engagement workflow flag used by task cards and notifications. | `WORKFLOW_TIMESTAMP` | Date is owned by the letter-sent workflow. A freely editable occurrence could diverge from task/notification displays keyed to the flag. Keep read-only projection unless product defines flag/date authority rules. |

### Calendar coexistence and duplicate-display risks

Legacy fixed fields still projected after Phase 3A are exactly the `CalendarFeedDao.CASE_DATE_PROJECTIONS` rows: SOL, tort notice, discovery, caller/intake, accepted, denied, closed, date of injury, fee agreement signed, non-engagement letter sent, medical negligence, and medical negligence discovered. Their source identities are `CASE_SOL:<CaseId>`, `CASE_TORT:<CaseId>`, `CASE_DISCOVERY:<CaseId>`, `CASE_CALLER:<CaseId>`, `CASE_ACCEPTED:<CaseId>`, `CASE_DENIED:<CaseId>`, `CASE_CLOSED:<CaseId>`, `CASE_INJURY:<CaseId>`, `CASE_FEE_AGREEMENT:<CaseId>`, `CASE_NON_ENGAGEMENT:<CaseId>`, `CASE_MED_NEG:<CaseId>`, and `CASE_MED_NEG_DISCOVERED:<CaseId>`.

Stable identity collision with authoritative `CaseDates` is not expected because authoritative occurrences use `CASE_DATE:<CaseDates.Id>` while fixed projections use `CASE_*:<CaseId>` keys. Visual and semantic duplicates are already possible: a user can create an authoritative Case Date of the same meaning and date as a fixed legacy value, and Phase 3A intentionally performs no deduplication or creation warning. Filtering/routing also differs: authoritative dates classify from `CaseDateTypes.CalendarCategory` and route to the case Dates section; legacy fixed dates classify by hard-coded source type/key and route as ordinary case-related feed items rather than authoritative occurrence editors. Responsible-attorney ownership filtering applies through the same active case ownership predicate, but source owner semantics differ because occurrence creators/type administrators are not calendar owners.

Safe future migration therefore requires an explicit strategy rather than heuristic matching. Do not add deduplication. During any migration phase, preserve legacy source identities until the precise cutover moment, and expect duplicate display if backfilled `CaseDates` are projected before the legacy projection is removed or suppressed.

### Database-dependent profiling script

No safe database connection was available from checked-in configuration. The following read-only SQL script gathers tenant-aware aggregate counts without PHI. It intentionally returns only tenant ids, counts, min/max dates, sentinel counts, date/time component summaries, and exact-match duplicate counts against existing `CaseDates` using proposed system-key criteria. Run in a read-only session with the intended tenant context/RLS settings.

```sql
SET NOCOUNT ON;
DECLARE @TenantId int = NULL; -- Optional: set for one tenant; leave NULL only when permitted to aggregate all visible tenants.

WITH Legacy AS (
    SELECT c.ShaleClientId, c.Id AS CaseId, v.FieldName, v.ProposedSystemKey, v.LegacyDate
    FROM dbo.Cases c
    CROSS APPLY (VALUES
        ('StatuteOfLimitations', 'statute_of_limitations', c.StatuteOfLimitations),
        ('TortNoticeDeadline', 'tort_notice_deadline', c.TortNoticeDeadline),
        ('DiscoveryDeadline', 'discovery_deadline', c.DiscoveryDeadline),
        ('DateOfInjury', 'date_of_injury', c.DateOfInjury),
        ('DateOfMedicalNegligence', 'medical_negligence', c.DateOfMedicalNegligence),
        ('DateMedicalNegligenceWasDiscovered', 'medical_negligence_discovered', c.DateMedicalNegligenceWasDiscovered),
        ('CallerDate', 'intake', c.CallerDate),
        ('AcceptedDate', 'accepted', c.AcceptedDate),
        ('DeniedDate', 'denied', c.DeniedDate),
        ('ClosedDate', 'closed', c.ClosedDate),
        ('DateFeeAgreementSigned', 'fee_agreement_signed', c.DateFeeAgreementSigned),
        ('DateNonEngagementLetterSent', 'non_engagement_letter_sent', c.DateNonEngagementLetterSent)
    ) v(FieldName, ProposedSystemKey, LegacyDate)
    WHERE (@TenantId IS NULL OR c.ShaleClientId = @TenantId)
      AND ISNULL(c.IsDeleted, 0) = 0
), ExactCaseDateDuplicates AS (
    SELECT l.ShaleClientId, l.CaseId, l.FieldName, COUNT_BIG(*) AS DuplicateCount
    FROM Legacy l
    JOIN dbo.CaseDateTypes cdt
      ON (cdt.ShaleClientId IS NULL OR cdt.ShaleClientId = l.ShaleClientId)
     AND cdt.SystemKey = l.ProposedSystemKey
     AND ISNULL(cdt.IsDeleted, 0) = 0
    JOIN dbo.CaseDates cd
      ON cd.ShaleClientId = l.ShaleClientId
     AND cd.CaseId = l.CaseId
     AND cd.CaseDateTypeId = cdt.Id
     AND CAST(cd.StartAt AS date) = l.LegacyDate
     AND ISNULL(cd.IsDeleted, 0) = 0
    WHERE l.LegacyDate IS NOT NULL
    GROUP BY l.ShaleClientId, l.CaseId, l.FieldName
)
SELECT l.FieldName,
       l.ShaleClientId,
       COUNT_BIG(*) AS VisibleCases,
       SUM(CASE WHEN l.LegacyDate IS NOT NULL THEN 1 ELSE 0 END) AS NonNullCount,
       MIN(l.LegacyDate) AS MinDate,
       MAX(l.LegacyDate) AS MaxDate,
       SUM(CASE WHEN l.LegacyDate IN ('19000101','19010101','99991231') THEN 1 ELSE 0 END) AS SentinelCount,
       SUM(CASE WHEN d.DuplicateCount IS NOT NULL THEN 1 ELSE 0 END) AS CasesWithExactCaseDateDuplicate,
       SUM(ISNULL(d.DuplicateCount, 0)) AS ExactCaseDateDuplicateRows
FROM Legacy l
LEFT JOIN ExactCaseDateDuplicates d
  ON d.ShaleClientId = l.ShaleClientId AND d.CaseId = l.CaseId AND d.FieldName = l.FieldName
GROUP BY l.FieldName, l.ShaleClientId
ORDER BY l.FieldName, l.ShaleClientId;

SELECT c.ShaleClientId,
       COUNT_BIG(*) AS CasesWithMultipleLegalDeadlineFields
FROM dbo.Cases c
WHERE (@TenantId IS NULL OR c.ShaleClientId = @TenantId)
  AND ISNULL(c.IsDeleted, 0) = 0
  AND ((CASE WHEN c.StatuteOfLimitations IS NULL THEN 0 ELSE 1 END) +
       (CASE WHEN c.TortNoticeDeadline IS NULL THEN 0 ELSE 1 END) +
       (CASE WHEN c.DiscoveryDeadline IS NULL THEN 0 ELSE 1 END)) >= 2
GROUP BY c.ShaleClientId
ORDER BY c.ShaleClientId;

SELECT c.ShaleClientId,
       COUNT_BIG(*) AS CasesWithMultipleFactualDateFields
FROM dbo.Cases c
WHERE (@TenantId IS NULL OR c.ShaleClientId = @TenantId)
  AND ISNULL(c.IsDeleted, 0) = 0
  AND ((CASE WHEN c.DateOfInjury IS NULL THEN 0 ELSE 1 END) +
       (CASE WHEN c.DateOfMedicalNegligence IS NULL THEN 0 ELSE 1 END) +
       (CASE WHEN c.DateMedicalNegligenceWasDiscovered IS NULL THEN 0 ELSE 1 END)) >= 2
GROUP BY c.ShaleClientId
ORDER BY c.ShaleClientId;

SELECT c.ShaleClientId,
       SUM(CASE WHEN c.CallerDate IS NOT NULL AND c.CallerTime IS NOT NULL THEN 1 ELSE 0 END) AS CallerDateWithTimeCount,
       SUM(CASE WHEN c.CallerDate IS NOT NULL AND c.CallerTime IS NULL THEN 1 ELSE 0 END) AS CallerDateWithoutTimeCount
FROM dbo.Cases c
WHERE (@TenantId IS NULL OR c.ShaleClientId = @TenantId)
  AND ISNULL(c.IsDeleted, 0) = 0
GROUP BY c.ShaleClientId
ORDER BY c.ShaleClientId;
```

Because the legacy columns are SQL `date` fields, the projected values are effectively date-only except for the separate `CallerTime` companion field. Production data is still required to confirm null rates, sentinel values, duplicate rates against existing `CaseDates`, and tenant distribution.

### Migration strategy analysis and smallest safe future sequence

Repository evidence supports treating only the legal/factual fields as immediate migration candidates: `StatuteOfLimitations`, `TortNoticeDeadline`, `DiscoveryDeadline`, `DateOfInjury`, `DateOfMedicalNegligence`, and `DateMedicalNegligenceWasDiscovered`. The safest next phase should not backfill or cut over workflow-owned timestamps. It should first resolve product decisions and collect production aggregates.

Strategy comparison:

* Keep legacy field permanently and continue projecting it: safest for workflow-owned timestamps (`AcceptedDate`, `DeniedDate`, `ClosedDate`, `DateFeeAgreementSigned`, `DateNonEngagementLetterSent`, and likely `CallerDate`), but preserves mixed authoring models and duplicate risk if users create similar Case Dates.
* Backfill a Case Date while retaining legacy field as authority: useful for a data-review phase, but creates duplicate display unless the legacy projection is suppressed at the same deployment boundary; rollback must account for authoritative rows that are not yet authority.
* One-time backfill followed by direct authority cutover: cleanest for true legal/factual candidates after product approval and data profiling; requires deployment ordering, audit/concurrency preservation, RLS-safe migration, historical label/color mapping, and explicit old-field read-only/removal plan.
* Temporary dual-read with one authoritative writer: may ease rollout but risks inconsistent display and user confusion; must choose exactly one writer and deterministic precedence.
* Temporary dual-write: highest consistency and rollback risk; avoid unless a hard compatibility requirement is proven.
* No migration: appropriate for workflow-owned timestamps unless product redefines them as independent occurrences.
* Remove a legacy projection after authoritative migration: likely necessary for each migrated candidate to avoid duplicate display, but should be done only after backfill validation and cutover tests.

Recommended future sequence:

1. **Pre-Phase 3B decision gate:** run the read-only profiling script; decide whether `CallerDate` remains intake workflow-only; confirm whether medical negligence labels/system keys/categories are correct; decide if web parity is required before migrating `DiscoveryDeadline` and medical-negligence dates.
2. **Phase 3B, if approved:** define and seed/activate only the missing legal/factual `CaseDateTypes` needed for migration candidates, with no backfill and no writer/reader cutover. If all required types already exist in production, Phase 3B may instead be a no-op verification phase that locks type mappings.
3. **Phase 3C:** tenant-safe, auditable dry-run/backfill plan and validation queries for the approved migration candidates only; no workflow timestamps.
4. **Phase 3D:** authority cutover for one small class first, preferably a deadline field, removing/suppressing the corresponding legacy Calendar projection at the same boundary to avoid duplicates.
5. **Later phases:** repeat per field group after monitoring; decide whether legacy columns become read-only, hidden, or removed only after rollback windows expire.

Phase 3B cannot responsibly include backfill, cutover, dual-read/write, or workflow timestamp migration based on current repository evidence.

### Product decisions required before implementation

* Should `CallerDate`/`CallerTime` remain an intake workflow timestamp, or become/edit through a Case Date occurrence type? If migrated, how should `CallerTime` map to `SupportsTime` and all-day behavior?
* Confirm stable `CaseDateType.SystemKey` names for the six migration candidates and whether existing production built-ins already cover them.
* Confirm `CalendarCategory` for factual/medical dates, because today they render as `OTHER_CASE_DATES` via legacy `CASE_DATE` classification.
* Decide whether fee agreement and non-engagement letter dates are permanently workflow-owned milestones tied to their flags.
* Decide whether accepted/denied/closed dates remain status lifecycle timestamps even when manually repairable in Case Details.
* Decide whether and when web/API editors must move migrated fields to the Dates section or become read-only.
* Define duplicate-display policy during any backfill validation window; the repository currently has no warning or prevention for semantically duplicate Case Dates.

## Phase 3B SQL preparation package (2026-08-05)

Phase 3B prepares reviewable SQL only. The package does not execute SQL, does not change runtime readers or writers, does not dual-write, does not touch `CalendarEvents`, and does not clear or drop any legacy `dbo.Cases` columns. Until explicit writer cutover is implemented, legacy `dbo.Cases` fields remain authoritative.

### Verified status-history and Timeline behavior

Repository code uses `dbo.CaseStatuses` as the case status history table. A history row contains `CaseId`, `StatusId`, `EffectiveDate`, `EndDate`, `Notes`, `CreatedAt`, `UpdatedAt`, and `IsPrimary`; runtime reads join to current `dbo.Statuses` rows for name, color, `IsClosed`, `LifecycleKey`, and `SystemKey`. The row does not store previous status, actor id, historical status key, or historical display label. Case status changes end open status rows and insert a new primary row with `SYSDATETIME()`, then touch `dbo.Cases.UpdatedAt`. Lifecycle date population is separate: `AcceptedDate`, `DeniedDate`, and `ClosedDate` are set to `CAST(SYSDATETIME() AS date)` only when the target lifecycle key is reached and the corresponding legacy field is null. The desktop Timeline loads `caseDao.listCaseStatusHistory(activeCaseId)` and renders `dbo.CaseStatuses` rows joined to current `dbo.Statuses`; it does not derive those status timeline entries from fixed `dbo.Cases.AcceptedDate`, `DeniedDate`, or `ClosedDate`.

Because `dbo.CaseStatuses` lacks actor, previous status, historical label/key, tenant-scoped identity, and migration provenance, the preparation package intentionally does not generate status-history inserts for legacy `AcceptedDate`, `DeniedDate`, or `ClosedDate`. Direct SQL would have to invent missing transition context and could not prove first-versus-latest semantics for repaired or manually edited legacy dates. The smallest prerequisite phase is a status-history data-model upgrade that records tenant id, previous and new status ids, historical lifecycle/system keys and display labels, nullable/explicit actor or system actor convention, source/provenance, and immutable occurred-at timestamps. After that prerequisite, a new read-only discrepancy run can decide whether legacy date-only milestones can be represented without fabricating transitions.

### Approved CaseDateType destinations

| Legacy source | Destination `CaseDateTypes.SystemKey` | Category | Time semantics | Future writer owner |
| --- | --- | --- | --- | --- |
| `CallerDate` + `CallerTime` | `intake` | `OTHER` | Timed when `CallerTime` exists; all-day when only `CallerDate` exists | Intake workflow |
| `DateOfInjury` | `date_of_injury` | `OTHER` | All-day | Case Dates/details workflow after cutover |
| `DateOfMedicalNegligence` | `date_of_medical_negligence` | `OTHER` | All-day | Case Dates/details workflow after cutover |
| `DateMedicalNegligenceWasDiscovered` | `medical_negligence_discovered` | `OTHER` | All-day | Case Dates/details workflow after cutover |
| `StatuteOfLimitations` | `statute_of_limitations` | `DEADLINE` | All-day | Deadline/Case Dates workflow after cutover |
| `TortNoticeDeadline` | `tort_notice_deadline` | `DEADLINE` | All-day | Deadline/Case Dates workflow after cutover |
| `DiscoveryDeadline` | `discovery_deadline` | `DEADLINE` | All-day | Deadline/Case Dates workflow after cutover |
| `DateFeeAgreementSigned` | `fee_agreement_signed` | `MILESTONE` | All-day | Fee-agreement workflow |
| `DateNonEngagementLetterSent` | `non_engagement_letter_sent` | `MILESTONE` | All-day | Non-engagement workflow |
| `AcceptedDate` | status-transition history | n/a | Legacy date-only milestone until prerequisite history upgrade | Status transition workflow |
| `DeniedDate` | status-transition history | n/a | Legacy date-only milestone until prerequisite history upgrade | Status transition workflow |
| `ClosedDate` | status-transition history | n/a | Legacy date-only milestone until prerequisite history upgrade | Status transition workflow |

The Phase 1A global seeds already include `statute_of_limitations`, `tort_notice_deadline`, `discovery_deadline`, `date_of_injury`, and `date_of_medical_negligence`. Phase 3B seed verification reuses those keys and adds only missing approved globals. It treats conflicting category, support-time, active, deleted, or duplicate definitions as blockers instead of silently changing deployed meaning. The authoritative CaseDates removal column is `IsDeleted`, as defined by the Phase 1A migration and used by `CaseDateDao`/`CalendarFeedDao`; the package verifies `dbo.CaseDates.IsDeleted` before reading or writing and does not introduce an `IsRemoved` schema assumption. The existing Phase 1A key `date_medical_negligence_discovered` differs from the approved destination key `medical_negligence_discovered`; the seed script therefore verifies/creates the approved key and preflight exposes effective mappings for review before execution.

### SQL package and execution order

1. Review and run `docs/sql/2026-08-05_case_dates_legacy_phase3b_preflight.sql` in a read-only session to collect aggregate counts, exact matches, conflicts, orphan `CallerTime`, workflow flag/date mismatches, existing type definitions, status-date evidence, reopened/repeated transitions, and unresolved deterministic blockers.
2. Resolve every preflight blocker outside the migration script. Do not execute backfill while orphan `CallerTime`, duplicate type definitions, inactive/deleted/ambiguous effective type definitions, active same-type different-value `CaseDates`, multiple exact matches, removed exact matches, status-history uncertainty, or workflow flag/date mismatches remain unresolved.
3. Run `docs/sql/2026-08-05_case_dates_legacy_phase3b_seed_types.sql` to verify and idempotently seed approved global destination types.
4. Run `docs/sql/2026-08-05_case_dates_legacy_phase3b_backfill_case_dates.sql` only after seed verification and preflight success and after populating the script-local `@MigrationActors` table with one explicit same-tenant migration/system actor per participating tenant. The script inserts only missing exact destination rows, uses tenant/global overlay effective type resolution so tenant overrides win over global rows, preserves all-day versus timed intake semantics with the same `DATETIME2FROMPARTS` rule used by preflight and validation, materializes resolved rows into `#ResolvedBackfill` before mutation, leaves `EndsAt` null, leaves legacy columns intact, and does not update `dbo.Cases.UpdatedAt`.
5. Run `docs/sql/2026-08-05_case_dates_legacy_phase3b_post_validation.sql`. The migration is not successful while unresolved/orphan/conflict counts remain non-zero or an eligible legacy value lacks exactly one active destination representation.
6. Keep `docs/sql/2026-08-05_case_dates_legacy_phase3b_status_history_blocker.sql` as the read-only discrepancy query and blocker report for status dates; do not insert speculative status history in this phase.

Existing-occurrence conflict policy: the current `CaseDates` model allows multiple occurrences per case, but each migrated legacy fixed field represents one legacy value per case/type. Therefore this migration requires exactly zero or one active exact destination before insert; multiple exact active matches, any active same-effective-type different date/time, and any removed exact match are blockers for manual resolution. The script never overwrites, restores, removes, or silently adopts conflicting user-created rows.

Rollback boundary: the CaseDateTypes and CaseDates scripts are idempotent, but the current schema has no reversible migration-owned source key/ledger on `dbo.CaseDates`. Exact matching can avoid duplicate inserts, but it cannot distinguish preexisting user-created exact matches from migration-created rows after commit. Treat database backup/transaction rollback before commit as the reliable rollback boundary unless a future provenance schema is added.

### Future phased cutover roadmap

1. Destination type verification.
2. Preflight and conflict resolution.
3. Controlled backfill.
4. Post-backfill validation.
5. Runtime writer cutover: Intake writes the `intake` CaseDate; fee-agreement workflow writes `fee_agreement_signed`; non-engagement workflow writes `non_engagement_letter_sent`; status transition workflow writes upgraded status-history rows for accepted/denied/closed.
6. Runtime reader cutover from legacy case columns to authoritative `CaseDates` and upgraded status history.
7. Case Timeline cutover to the upgraded status-history model where needed for historical presentation.
8. Unified Calendar legacy-projection removal after readers no longer need fixed `dbo.Cases` date projections.
9. Export/report/web/API cutover.
10. Release soak period with reconciliation comparing legacy fields to destinations.
11. Final dependency scan across database SQL and Java/web/UI references.
12. Legacy column removal only when no dependencies remain and the final verification phase passes.
