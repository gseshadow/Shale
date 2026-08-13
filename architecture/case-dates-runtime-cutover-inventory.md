# Case Dates post-migration runtime cutover inventory

## Decision record status

This is the Phase 3D inventory and cutover plan. Phase 3B backfill and Phase 3C post-validation are complete: 2,233 eligible source rows reconcile exactly, `BlockerCount = 0`, and 610 flag-set/date-missing rows are intentional historical anomalies. The inventory assumes those deployed results and must not rerun, repair, or reinterpret them.

### Existing-case server and React cutover (2026-08-12)

The existing `GET /api/cases/{id}` and `PATCH /api/cases/{id}/core-details` round trip now carries the established complete nine-slot snapshot. Each slot has its enum key and canonical SystemKey, stored type id, occurrence id/RowVer and value when present, or an explicit absent flag and witnessed `Cases.RowVer`. The server derives tenant and actor from the authenticated runtime session; request identity is never authoritative. The React detail and currently exposed core editor use stable SystemKeys, preserve absent slots without creating them, preserve unedited timed intake values, and display a reload-required conflict without retrying.

The API detail query no longer selects any of the nine migrated scalar columns. Accepted, denied, and closed dates remain workflow-owned `Cases` fields. The aggregate PATCH mutates occurrences and non-date Case fields on one connection under `CaseAggregateTransaction`; its Case participant advances `UpdatedAt`/`RowVer`, and occurrence PHI/entity audits and Case PHI audits use that connection so any mutation or audit failure rolls back the unit. The response is reloaded after commit from `Cases` plus authoritative mapped occurrences. The old `CaseDao.updateCase` compatibility method remains because unconverted callers still exist, but this server path cannot call it. There is no dual read or dual write.

Desktop fixed editors, configurable intake, summary projections, Calendar feed, protected mapping administration, and Case Date live update invalidation were completed in their earlier documented slices. The earlier existing-case slice did not alter those boundaries; the following new-case slice completes the server/web creation cutover without changing them.

### Server and React new-case cutover (2026-08-12)

Web **new-case creation is now cut over**. `ApiReadController.createCase` accepts `caseDates` identified by canonical `systemKey` and optional authoritative `caseDateTypeId`, preserving `startsAt`, optional `endsAt`, `allDay`, timed values, and omission. `CaseServiceAdapter.createCase` delegates to `CaseDateDao.createCaseAggregate`; its `CaseAggregateTransaction` owns commit and rollback while the connection-bound `CaseDao.insertBasicCaseAggregate` inserts the Case, status, and responsible attorney without any migrated `Cases` date column. Occurrences and PHI/entity-action audits share that transaction, and SQL session tenant/actor, active membership, effective type/mapping identity, timing, ownership, and singleton duplication are validated before commit. Detail is reloaded through the authoritative nine-slot projection after commit. No legacy scalar create alias is retained and there is no dual-write.

The next evidenced Phase 3D boundary is the legacy organization/contact related-case projection and remaining report/export compatibility reads inventoried below. No migration, reconciliation, backfill, column removal, or live SQL Server verification was performed for this slice.

### First runtime-cutover slice (2026-08-06)

The fixed contract and read-foundation gate are implemented: `MigratedCaseDateKey` is the single immutable mapping, rejects the discarded alias, and records that only `intake` supports time. `CaseDateDao.listMigratedSingletonsForCase` uses the existing tenant-scoped occurrence query and effective tenant/global presentation overlay, retains stored historical type fallback, ignores soft-deleted occurrences, and fails explicitly for duplicate active singleton occurrences or a discarded-alias occurrence.

No compatibility reader or writer has been switched in this slice. The exact blocker is the inventory's unresolved concurrency shape: existing case editors submit only `Cases.RowVer` and cannot supply per-occurrence identity/`CaseDates.RowVer` or an explicit expected-absence token. In addition, case creation owns its transaction inside `CaseDao`; adding occurrences afterward would be non-atomic. Per the intake atomicity gate, this slice stops rather than introducing last-write-wins or a partially created case. Consequently all production dependencies classified **CONVERT** below remain, the legacy calendar projection remains until its readers are authoritative, and there is no dual-write. The next gate is an aggregate command/DTO contract carrying the nine occurrence identities and row versions and transaction ownership spanning case creation/update plus occurrence mutations; only after that contract is approved may compatibility hydration and writes cut over together.

### Aggregate/concurrency foundation gate (2026-08-06)

### Existing-case JavaFX fixed-editor cutover (2026-08-07)

### Case View local synchronization (2026-08-07)

#### Split-persistence correction

Runtime tracing found that the earlier slice had converted a helper but not every production handler. The real Overview buttons called `onEditIncidentDateField`, `onEditDateOfMedicalNegligenceField`, `onEditSolDateField`, and `onEditTortNoticeDeadlineField`; those delegated to `saveCoreOverviewField`/`saveDetailDateOverviewField`, which ultimately called the legacy `CaseDao.updateCase` or `updateCaseDetails` SQL writing `dbo.Cases.DateOfInjury`, `StatuteOfLimitations`, `TortNoticeDeadline`, or `DateOfMedicalNegligence`. In the read direction, `CaseOverviewRenderer.renderOverviewDates`, `CaseOverviewRenderer.applyDetail`, and `CaseDetailsEditor.renderView`/`renderEditors` populated the same controls from legacy-shaped `CaseOverviewDto`/`CaseDetailDto` getters. Because the authoritative snapshot load and the broad detail/overview load ran concurrently, CaseDates could paint first and the later legacy callback would then restore the divergent `dbo.Cases` value. Local synchronization masked this until a full reload.

The production Overview button handlers and all nine Details inline handlers now initialize from `AuthoritativeCaseDateEditor` and save exclusively through `mutateMigratedCompatibilityDates`; intake time uses the same aggregate occurrence. General DTO hydration no longer writes any fixed-date label or editor. Unrelated existing-case Overview and Details saves call `CaseDao.updateCaseNonDate` and `updateCaseDetailsNonMigrated`, whose SQL omits all nine migrated columns and `CallerTime`; after those writes advance `Cases.RowVer`, the compatibility snapshot is invalidated and coherently reloaded. There is no dual-write. Deferred legacy callers remain new-case intake, server/API/web compatibility contracts, legacy DAO methods retained for those callers and rollback, migrations/validation, and historical projections documented below. LiveBus proactive cross-instance refresh remains deferred.

`CaseController.synchronizeCaseDatesAfterLocalMutation` is the single case-scoped local invalidation path shared by the fixed Overview/Details controls and the generic Dates section. A successful fixed aggregate mutation installs its returned coherent aggregate result directly in `AuthoritativeCaseDateEditor` (including the new `Cases.RowVer`, all occurrence ids and `CaseDates.RowVer` values, and every expected-absence witness), repaints every fixed location, and marks/reloads the ordinary Dates list. It neither performs a second mutation nor performs an unnecessary compatibility reload.

Generic Add and Edit resolve both the selected type and, for a type change, the prior occurrence through `MigratedCaseDateKey`; Remove and Restore resolve the occurrence's stored SystemKey. After the normal Dates-list refresh, a mutation involving any canonical mapping reloads the complete nine-slot compatibility aggregate and repaints Overview/Details. A non-migrated type only refreshes the generic list. Visible scalar controls and legacy DTO values are never used to manufacture concurrency metadata.

All nine canonical mappings participate: timed `intake` and the eight all-day mappings enumerated by `MigratedCaseDateKey`. Loads and mutations run away from the JavaFX Application Thread; FX application is guarded by case id and monotonically increasing generation, so case switches and out-of-order completions cannot install stale snapshots. Invalidation does not recursively trigger another mutation or refresh loop. Workflow flags remain independent and cannot fabricate persisted dates. Proactive cross-instance refresh through LiveBus remains deferred; an external conflict may continue to require Reload.

`CaseController.reloadCurrentCaseForViewMode` now starts an off-FX-thread authoritative nine-slot load through `CaseServicePort.loadMigratedCompatibilityDateSnapshot`. `AuthoritativeCaseDateEditor` owns the indivisible `Cases.RowVer` plus nine occurrence states; case switches invalidate it and generation checks discard old-case responses. The fixed Overview and Details controls are overlaid only from that snapshot, including timed `intake`; workflow flags remain independent and no longer invent today's date.

Inline fixed-date saves use `CompatibilityCaseDateEditor` to produce all nine intents and invoke only `mutateMigratedCompatibilityDates`. A no-op does not call the service. Success replaces the complete snapshot with the aggregate result, re-renders the controls, and permits a second edit with refreshed case and occurrence tokens. A conflict is never retried: the rejected snapshot is invalidated, a clear stale/inconsistent message is shown, and an explicit authoritative reload occurs before another user-initiated save. Work runs on a background thread; result application is generation guarded on the FX thread and the shared busy state prevents duplicate saves.

This converted existing-case desktop path neither reads the nine legacy DTO date values into fixed controls nor calls a legacy compatibility writer and performs no dual-write. Deferred compatibility remains in new-case intake, `CaseServiceAdapter.updateCaseCoreDetails`, `ApiReadController`, `shale-web/src/api.ts`, `shale-web/src/App.tsx`, retained legacy DAO boundaries used by those callers, and the documented projections in `CaseDao`, `OrganizationDao`, `ContactDao`, and `CalendarFeedDao`. Migration/validation/rollback SQL and their exact contract tests remain the non-runtime allowlist. Intake/API/web atomic conversion remains a later gate and must not be partially converted.

The desktop-facing service contract now exposes the authoritative nine-slot read and mutation boundary. `CompatibilityCaseDateEditor` retains occurrence ids, occurrence row versions, and expected-absence witnesses without presenting them, and constructs exactly nine keyed intents. `CaseServiceAdapter.DaoCaseGateway` delegates this boundary directly to `CaseDateDao`, never to the legacy-shaped `CaseDao.updateCase` or `updateCaseDetails` compatibility writers.

`CaseAggregateTransaction` is the sole commit/rollback owner. `CaseDateDao.mutateMigratedCompatibilityDates(Connection, CaseDateAggregateCommand)` is its connection-accepting participant: it locks and validates `Cases.RowVer`, locks changed canonical singleton states, resolves effective tenant/global types for creates, applies concurrency-checked updates or historical soft deletion, performs PHI/entity audits, and touches `Cases.UpdatedAt` once for a real change. No-op commands write and audit nothing. The result refreshes `Cases.RowVer` and all nine states; stale case or occurrence errors require reload and are never retried silently.

This aggregate boundary names none of the legacy date columns, performs no legacy write, and is not a dual-write. New-case intake remains deferred. Remaining compatibility callers are desktop `CaseController` broad detail/overview saves and intake, service `CaseServiceAdapter.updateCaseCoreDetails`, API `ApiReadController`, web `App.tsx`/`api.ts`, and data projections in `CaseDao`, `OrganizationDao`, `ContactDao`, and `CalendarFeedDao`. The next client gate is wiring the JavaFX controller editors to this completed service boundary and removing their broad legacy-writer calls.

`CaseDateAggregateCommand` is now the complete nine-slot mutation envelope. Every canonical `MigratedCaseDateKey` is present exactly once and has one explicit `CompatibilityCaseDateMutation`: `Unchanged`, `Create(ExpectedAbsent, Value)`, `Update(occurrenceId, CaseDates.RowVer, Value)`, or `Clear(occurrenceId, CaseDates.RowVer)`. A null occurrence id is not an absence assertion. `ExpectedAbsent` carries the `Cases.RowVer` witnessed by the authoritative read; a future aggregate writer must compare it while locking the case row and the active singleton key range. Since every Case Date mutation touches `Cases.UpdatedAt` and therefore `Cases.RowVer`, two editors cannot both successfully use the same absence witness. Creation must additionally fail if its locked active-key query sees one or more occurrences; duplicates are always an error, never a selection rule. Removed rows remain history and are neither restored nor repurposed.

`CaseDateDao.listMigratedCompatibilityStateForCase` supplies all nine canonical keys, current start/end and all-day state, occurrence id and `CaseDates.RowVer` when present, or the explicit expected-absence witness when absent. It is built only from the authoritative singleton occurrence reader plus the tenant-scoped case concurrency row; no legacy `dbo.Cases` date is consulted.

`CaseAggregateTransaction` is the designated single JDBC transaction owner. Aggregate case insert/update, mapped occurrence work, the `Cases.UpdatedAt`/row-version touch, PHI audit, entity-action audit, and workflow side effects must all receive its one connection. Participants must not commit, open a follow-up transaction, or own nested transactions. The boundary rolls the entire unit back when any participant fails.

This gate deliberately converts **no production writer**: desktop (through `shale-ui`), API (`ApiReadController`), web (`api.ts`/`App.tsx`), and the legacy-shaped `CaseServiceAdapter`/`CaseDao` service paths cannot yet provide the complete nine-slot state. Their complete read-edit-write round trips remain deferred rather than weakening concurrency. Existing ordinary legacy-backed paths therefore remain compatibility debt, not dual-write: none also writes `CaseDates`. The next gate must wire connection-accepting CaseDao and CaseDateDao mutation participants into `CaseAggregateTransaction`, then convert one entire client round trip (read metadata through aggregate write) and remove that path's legacy-column writes. Intake must retain timed/all-day input; the other eight keys remain all-day; workflow flags remain independent and must never fabricate occurrences.

`dbo.CaseDates` will become the runtime authority for the nine migrated meanings below. The corresponding `dbo.Cases` columns remain unchanged for rollback/history and must receive no normal application writes after cutover. `FeeAgreementSigned` and `NonEngagementLetterSent` remain workflow authorities as flags; their missing paired dates must never cause an occurrence to be fabricated.

## Fixed mapping contract

| Legacy source | Stable `CaseDateTypes.SystemKey` | Runtime shape |
| --- | --- | --- |
| `CallerDate` plus optional `CallerTime` | `intake` | timed when the migrated occurrence is timed; otherwise all-day |
| `DateOfInjury` | `date_of_injury` | all-day |
| `DateOfMedicalNegligence` | `date_of_medical_negligence` | all-day |
| `DateMedicalNegligenceWasDiscovered` | `date_medical_negligence_discovered` | all-day |
| `StatuteOfLimitations` | `statute_of_limitations` | all-day |
| `TortNoticeDeadline` | `tort_notice_deadline` | all-day |
| `DiscoveryDeadline` | `discovery_deadline` | all-day |
| `DateFeeAgreementSigned` | `fee_agreement_signed` | all-day; never inferred from its flag |
| `DateNonEngagementLetterSent` | `non_engagement_letter_sent` | all-day; never inferred from its flag |

`medical_negligence_discovered` is a discarded draft alias. Runtime selector resolution, commands, API input, tests, and future compatibility tooling must reject it rather than resolve, normalize, or merge it. The deployed Phase 1A names, categories, colors, time support, sort order, and activation state are not part of this cutover and must remain unchanged.

## Classification vocabulary

* **CONVERT** — a production runtime dependency that must use `CaseDates` before cutover.
* **KEEP-COMPAT** — migration, validation, schema-history, or rollback documentation that intentionally names the legacy columns and must remain non-runtime.
* **REMOVE** — obsolete production behavior, especially duplicate legacy calendar projection or normal writes to migrated columns.
* **REVIEW** — a dependency whose product or concurrency semantics need an explicit decision before implementation.

## Production inventory

### `shale-data`: persistence, queries, calendar, reports/exports

| Exact path | Occurrence and risk | Class / required disposition |
| --- | --- | --- |
| `shale-data/src/main/java/com/shale/data/dao/CaseDao.java` | Case create/update SQL writes all nine migrated columns; detail, overview/grid/search and selection queries read subsets; PHI change auditing names injury and both medical dates. Existing broad updates also touch `Cases.UpdatedAt` and use the case row version. | **CONVERT (highest risk).** Remove migrated columns from ordinary `INSERT`/`UPDATE` ownership and hydrate legacy-shaped consumers from one tenant-scoped, effective-SystemKey occurrence projection. Mutations must resolve the exact tenant-effective type, update/create the single mapped occurrence transactionally with expected `CaseDates.RowVer`, retain PHI/entity audit, and touch `Cases.UpdatedAt`. Do not dual-write legacy columns. |
| `shale-data/src/main/java/com/shale/data/dao/CalendarFeedDao.java` | **COMPLETED:** the former `CASE_DATE_PROJECTIONS` loop selected nine migrated `dbo.Cases` columns and emitted `CASE_SOL`, `CASE_TORT`, `CASE_DISC`, `CASE_CALLER`, `CASE_INJURY`, `CASE_FEE_AGREEMENT`, `CASE_NON_ENGAGEMENT`, `CASE_MED_NEG`, and `CASE_MED_NEG_DISCOVERED` entries beside authoritative occurrences. | Calendar now reads authoritative `CASE_DATE:<CaseDates.Id>` occurrences only for migrated meanings. It retains genuine `CalendarEvents`, task due dates, and the explicitly named `AcceptedDate`, `DeniedDate`, and `ClosedDate` lifecycle projections. There is no migrated legacy fallback and no Calendar CaseDates writer. |
| `shale-data/src/main/java/com/shale/data/dao/OrganizationDao.java` and `ContactDao.java` | **PARTIALLY COMPLETED:** desktop Contact/Organization and server/web Organization related Cases use `CaseSummaryDao`; `ContactDao.findRelatedCases` remains for deferred server/contact compatibility. The removed `OrganizationDao.findRelatedCases` formerly selected legacy intake, SOL, and tort columns. | **SERVER ORGANIZATION AUTHORITATIVE.** `GET /api/organizations/{organizationId}` now reuses the set-based, tenant-safe `CaseSummaryDao.RelatedCaseRow` projection. Server Case search and assigned Cases were the next recommended cutover at that checkpoint and are now complete. |
| `shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java` | Web/core create and core-detail commands pass caller/injury/SOL/tort values into `CaseDao`; desktop generic case updates transit here as well. Existing occurrence APIs accept type ids rather than stable migrated meanings. | **CONVERT / REVIEW.** Add an actor-aware stable-key mutation boundary for mapped singleton meanings and coordinate case plus occurrence writes in one transaction. Decide expected occurrence row-version representation for legacy-shaped editors before coding; never bypass current type/case/actor validation. |
| Other `shale-data` production DAOs (`TaskDao`, `NotificationDao`) | Matches for `NonEngagementLetterSent` are the workflow flag used to decorate task/notification case cards, not the migrated date. | **KEEP (not a date dependency).** Preserve the workflow flag and do not synthesize `non_engagement_letter_sent` when it is true and its date occurrence is absent. |

There is no separate report DAO in the repository. Current report/export-like case lists and document data originate in `CaseDao` and `CaseDocumentService`; therefore those named paths are the report/export cutover boundary rather than an undiscovered reporting subsystem.

### `shale-core`: DTO and service contracts

| Exact path | Occurrence and risk | Class / required disposition |
| --- | --- | --- |
| `shale-core/src/main/java/com/shale/core/dto/CaseDetailDto.java` | Exposes every migrated legacy date and both workflow flags. Many desktop and server consumers therefore read columns indirectly. | **CONVERT / REVIEW.** Prefer an explicit stable-key occurrence view (including occurrence id, type id, `RowVer`, `StartsAt`, `AllDay`) so edits have concurrency identity. A temporary compatibility accessor may derive the nine old properties from `CaseDates`, but it must never query `Cases` or write legacy fields and must be marked for removal. |
| `shale-core/src/main/java/com/shale/core/dto/CaseOverviewDto.java` | Carries tort notice for overview/radar presentation. | **CONVERT.** Derive from the authoritative mapped occurrence. |
| `shale-core/src/main/java/com/shale/core/service/CaseServicePort.java` | `CreateCaseCommand` and `UpdateCaseCoreDetailsCommand` carry migrated dates; existing Case Date mutation commands identify a type by id and already carry occurrence row versions for update/delete/restore. | **CONVERT.** Define stable-key mapped-date commands or an aggregate case-edit command with per-occurrence concurrency tokens. Preserve actor/tenant parameters and existing generic occurrence behavior. |
| `shale-core/src/main/java/com/shale/core/privacy/PhiFieldRegistry.java` | Registers legacy injury/medical fields and `CaseDates.StartsAt`, `EndsAt`, and `Notes`. | **CONVERT.** Retain legacy entries for historical audit interpretation, while new changes audit the CaseDates PHI fields and semantic SystemKey without exposing values. |
| `shale-core/src/main/java/com/shale/core/model/CalendarFeedCategory.java` | Contains source/category definitions for legacy fixed projections as well as authoritative dates. | **REVIEW/REMOVE.** Remove only constants proven exclusive to the nine retired projections; preserve deployed layers/defaults and status-date behavior. |

### `shale-ui` and `shale-desktop`

| Exact path | Occurrence and risk | Class / required disposition |
| --- | --- | --- |
| `shale-ui/src/main/java/com/shale/ui/controller/CaseController.java` | New intake, Overview, and Details display/edit all or subsets of the nine dates through `CaseDetailDto`; flag listeners currently invent today's paired date when a box is checked. The separate Dates section already uses authoritative occurrence APIs. | **CONVERT (highest UI risk).** Bind fixed labels/editors to mapped occurrences and submit occurrence row versions. Checking either workflow flag must no longer default a date; the 610 anomalies must continue to render as flag set with no date. Retain `CallerTime` through the timed `intake` occurrence. Refresh both case detail and Dates section after mutation. |
| `shale-ui/src/main/resources/fxml/case.fxml` | Declares fixed labels/editors for every migrated field and the generic Dates section. | **CONVERT / REVIEW.** Either rebind fixed workflow locations to the mapped occurrence model or remove duplicate editors after product review; do not leave two independent editors. The Dates section remains. |
| `shale-ui/src/main/java/com/shale/ui/component/CaseCard.java` and `component/factory/CaseCardFactory.java` | Tort notice is displayed/sorted in case cards. | **CONVERT.** Feed it from the mapped occurrence without changing card behavior. |
| `shale-ui/src/main/java/com/shale/ui/document/CaseDocumentService.java` | Uses accepted/denied/closed status dates only. | **KEEP (not migrated).** No migrated Phase 3B field is read here; report/export regression should prove this remains true. |
| `shale-desktop/src/**` | No direct migrated-field or CaseDates production references were found; it composes the core/data/UI modules. | **NO DIRECT CHANGE.** Desktop behavior cuts over through the modules above; packaging/startup smoke coverage is still required. |

### `shale-server` APIs and `shale-web`

| Exact path | Occurrence and risk | Class / required disposition |
| --- | --- | --- |
| `shale-server/src/main/java/com/shale/server/controller/ApiReadController.java` | Create accepts caller/injury/SOL/tort; core update accepts injury/SOL/tort; responses serialize legacy-shaped `CaseDetailDto`. | **CONVERT.** Preserve tenant and authenticated actor extraction, but route mapped inputs through authoritative stable-key occurrence mutations and return occurrence concurrency metadata. Explicitly reject the discarded alias if a key-capable request is introduced. |
| `shale-web/src/api.ts` | `CaseDetail`, create payload, and core-update payload expose caller/injury/SOL/tort as scalar dates. | **CONVERT.** Introduce mapped occurrence types/row versions; a transitional scalar may be derived only from returned CaseDates data. |
| `shale-web/src/App.tsx` | New/edit forms write caller/injury/SOL/tort; Case View displays injury/SOL/tort from the scalar detail. | **CONVERT.** Display and edit mapped occurrences, preserving nullable behavior and timed intake semantics. Avoid a create-time two-transaction partial case/date state by using an aggregate server operation. |

### Tests and fixtures

| Exact paths | Classification and cutover work |
| --- | --- |
| `shale-data/src/test/java/com/shale/data/dao/CaseDatesLegacyPhase3bMigrationContractTest.java` plus `docs/sql/2026-08-05_case_dates_legacy_phase3b_*.sql` and `docs/sql/2026-08-06_case_dates_legacy_phase3b_*.sql` | **KEEP-COMPAT.** Immutable evidence/contract for the completed migration. Do not execute or edit the data migration. Alias rejection remains asserted. |
| `CalendarFeedDaoTest`, `CalendarFeedSourceFilterTest`, `CalendarOverlaySelectionTest` | **CONVERT.** Remove expectations for the nine `CASE_*` projections; assert only `CASE_DATE:<id>`, existing layers, tenant predicates, soft-delete exclusion, historical presentation, and all-day/timed mapping. Leave non-migrated status projection expectations intact. |
| `CaseDaoCaseDetailQueryTest`, `CaseDaoCasesGridQueryTest`, `CaseDaoSelectionQueryTest`, `CaseDateDaoReadContractTest` | **CONVERT/EXPAND.** Prove set-based stable-key hydration, overlay winner/fallback behavior, no legacy reads/writes, concurrency, audit, `UpdatedAt`, tenant relationship integrity, and no alias acceptance. |
| `CaseServiceAdapterTest` | **CONVERT/EXPAND.** Prove aggregate transaction and stable-key resolution, including rollback on occurrence conflict and no compatibility write. |
| `shale-server/src/test/java/com/shale/server/controller/ApiReadControllerTest.java` and web tests/build | **CONVERT/EXPAND.** Prove API fields originate in CaseDates, edits carry concurrency, cross-tenant ids fail, and alias input is rejected. |
| `CaseControllerNullableDateDialogTest`, `CaseCardFactoryTortNoticeTest`, UI Case Dates tests | **CONVERT/EXPAND.** Prove fixed views use occurrences and flag-only rows remain blank without creating anything. |
| Other fixtures containing the legacy names | **REVIEW individually.** Retain schema/migration fixtures; replace runtime query/write expectations. A post-cutover static scan should allow legacy names only in an explicit compatibility allowlist. |

## Proposed implementation sequence and gates

## Calendar cutover completion (2026-08-10)

The duplicate path was `CalendarController` → `CalendarService` → `CalendarFeedDao.listCalendarFeed*` → the generated `CASE_DATE_PROJECTIONS` `UNION ALL` branches → the nine migrated columns on `dbo.Cases`. Those rows used case-level identities and coexisted with the occurrence-level `dbo.CaseDates` branch. The fixed branches, their aliases/helpers, and their legacy source-field classification have been removed.

The retained Calendar sources are: persisted, non-cancelled `CalendarEvents`; incomplete task `DueAt` projections; active, non-deleted `CaseDates` occurrences with stored occurrence/type identity and effective tenant/global presentation; and `AcceptedDate`, `DeniedDate`, and `ClosedDate` lifecycle projections. Those three lifecycle fields are intentionally retained because they are outside the nine-field migration and repository evidence does not establish a replacement authority. Calendar has no fallback for an absent migrated occurrence, does not create/edit Case Dates, and never copies an occurrence into `CalendarEvents`.

Case Dates invalidations remain PHI-free. The main Calendar feed now observes same-tenant cross-instance `CaseDates` entity events, ignores initiating-instance and duplicate event ids, coalesces queued refreshes, and relies on its load-generation guard to discard stale responses. Case-specific Calendar/Case Dates refresh retains its case-id and tenant guards. Local mutations continue through the established post-commit reload/publish paths; Calendar event mutations retain their independent reload behavior.

Remaining production legacy readers outside Calendar are deliberately deferred: `CaseDao` list/grid/search/report/export compatibility queries and legacy-shaped detail boundaries; `OrganizationDao` and `ContactDao` related-case summaries; shared `CaseDetailDto`/`CaseOverviewDto` compatibility properties; case cards; server API responses; and web models/views. Remaining normal migrated-field writers outside Calendar are the deferred legacy-shaped case update/API/web compatibility paths identified above; configurable Intake and configured New Intake are already cut over and do not write them. Migration/validation SQL and historical PHI registry names remain compatibility evidence rather than runtime Calendar dependencies. The next gate is one shared authoritative date projection for lists, boards, search, MyShale, reports, and exports, followed by conversion of their complete concurrency-aware write round trips.

## Shared list projection foundation (2026-08-10)

The approved boundary for later list-style conversions is
`CaseServicePort.projectMigratedCaseDates(Collection<Long>, tenant, actor)`. Its immutable core DTO is
`MigratedCaseDateProjectionDto`, the data adapter delegates through `CaseServiceAdapter`, and the sole
persistence implementation is `CaseDateDao.projectMigratedCaseDates`. Consumers therefore need no DAO,
JDBC, JavaFX, report, export, spreadsheet, API, or web-serialization dependency. This extends the existing
Case Dates service/adapter/DAO structure and reuses `MigratedCaseDateKey`; it is not a competing date domain
or a replacement for the generic occurrence APIs used by Dates, Calendar, and configurable Intake.

The projection always contains nine slots for each returned authoritative `CaseId`: `intake`,
`date_of_injury`, `date_of_medical_negligence`, `date_medical_negligence_discovered`,
`statute_of_limitations`, `tort_notice_deadline`, `discovery_deadline`, `fee_agreement_signed`, and
`non_engagement_letter_sent`. Identity is the stable stored/effective `SystemKey`, never a display label.
Each slot explicitly distinguishes presence from absence. A present intake retains its complete
`LocalDateTime` and stored `AllDay` value; the other eight accept only their established all-day shape.
Flags do not manufacture occurrences. No slot is read from or falls back to a migrated `dbo.Cases` column.

The DTO intentionally excludes occurrence ids, type ids, row versions, and expected-absence witnesses:
list consumers cannot edit, and those values would unnecessarily duplicate the established
`CompatibilityCaseDateState`/aggregate optimistic-concurrency contract used by Case View. The existing edit
snapshot continues to carry occurrence identity and `CaseDates.RowVer` for present slots and the observed
case row version for expected absence. The shared projection performs no write to `CaseDates`, `Cases`, or
`CalendarEvents` and introduces no write path.

The DAO de-duplicates positive requested ids while preserving first-request order, safely ignores null and
non-positive ids, and returns only cases visible through the authenticated SQL session tenant. An unavailable,
unknown, or cross-tenant id is omitted, so absence/presence cannot reveal another tenant's case. Existing
cases with no active occurrences receive a coherent nine-slot empty projection. Input is partitioned into
500-id chunks, leaving ample room below SQL Server's 2,100-parameter limit; each chunk uses one set-oriented
Case/CaseDates/type query, with one tenant-session validation and one actor validation per boundary call,
never one query per case or hidden type lookup. Duplicate active occurrences for a migrated singleton are an
explicit conflict rather than being collapsed by label or date.

Stored type identity is retained even when that type is inactive or soft-deleted. Active effective
tenant/global overlays supply the canonical `SystemKey` when available, using tenant-first resolution; the
stored historical type key is the fallback when no active overlay exists. Tenant joins cover cases,
occurrences, and stored types, while SQL `SESSION_CONTEXT` remains authoritative and must match the supplied
tenant argument. This matches the completed Case View and Calendar historical/effective-type behavior.

No broad consumer conversion occurred in this slice. `CaseDao` grid/board/search/deleted/MyShale/report/export
queries, related-case queries in `ContactDao` and `OrganizationDao`, case cards, server/API/web models, and
legacy-shaped shared DTO fields are intentionally not converted or classified as converted. The next gate is
Cases grid, board, and export conversion using this boundary; later screens should follow independently.

1. **Freeze the contract.** Add one shared, immutable mapping of the nine canonical SystemKeys. Reject `medical_negligence_discovered` at every stable-key boundary. Do not modify Phase 1A seed/category data.
2. **Build tenant-safe read projection.** In `CaseDateDao`, load mapped occurrences set-wise by `(ShaleClientId, CaseId)` and resolve presentation by stable SystemKey using the established tenant override/global fallback rules. Active occurrences remain the runtime value; historical type fallback remains available for display. Treat multiple active occurrences for a mapped singleton key as an explicit conflict, never “first row wins.”
3. **Define concurrency-aware aggregate DTOs.** Return occurrence id/type id/SystemKey/value/`AllDay`/`RowVer`. Decide and document how a create or case edit submits expected absence versus expected row version. This decision blocks implementation of legacy-shaped editors.
4. **Cut over writes transactionally.** Create/update/delete the mapped occurrence using the existing actor, relationship, time-support, soft-delete, audit, and row-version rules. Touch `Cases.UpdatedAt` in the same transaction. New case plus supplied mapped dates must be atomic. Do not write the legacy columns, flags, or migration rows.
5. **Cut over all readers.** Hydrate Case View, grids/cards, related organization/contact summaries, APIs, web, reports, and exports from the authoritative projection. Compatibility scalar DTO accessors, if temporarily necessary, must be computed from that projection only and documented with a removal issue.
6. **Remove duplicate calendar projection.** Retire only the nine migrated legacy branches after all reads use CaseDates. Preserve manual events, tasks, non-migrated status dates, layer routing/defaults, owner filtering, navigation, and read-only Case Date calendar behavior.
7. **Lock normal divergence.** Add static SQL/query contract tests proving production SQL neither selects nor writes the nine legacy columns. Permit names only in migration/validation/schema history and explicit historical audit metadata. No compatibility dual-write is presently justified.
8. **Regression and release gate.** Run module/unit builds and tests, web build/tests, and static scans. Without connecting to a database, contract-test RLS predicates, composite tenant/case relationship validation, effective overlay resolution, row versions, audits, `UpdatedAt`, soft deletion, historical types, and timing. Database integration validation belongs to a separately approved environment and is not part of this inventory phase.

## Explicit risks and review decisions

* **Singleton ambiguity:** generic Case Dates permits multiple occurrences of a type, while each migrated fixed field represented one value. Mapped fixed editors must reject multiple active same-key occurrences; product must decide whether the generic Dates UI may add a second mapped occurrence. Recommended: prevent a second active occurrence for these nine meanings at the service boundary without altering schema.
* **Concurrency shape:** current case editors know the `Cases` row version but not the mapped occurrence row version. Adding per-occurrence tokens is required to avoid last-write-wins. This is an implementation blocker, not grounds for a legacy dual-write.
* **Create atomicity:** current server/web create commands embed four dates. Case creation and its mapped occurrences must share a transaction and same-tenant active actor; otherwise partial creation is possible.
* **Intake time:** `CallerDate` plus `CallerTime` migrated to one `intake` occurrence. Cutover must derive both presentation pieces from `StartsAt`/`AllDay`; it must not keep reading `CallerTime` after authority changes.
* **Flag/date independence:** flags remain workflow state. A missing occurrence remains missing even when a flag is true. Date edits must not silently flip flags, and flag edits must not create dates.
* **Audit semantics:** changing factual/medical mapped dates remains a PHI and entity-audited action. Audit records should identify case, occurrence, and canonical SystemKey but never log date/notes values.
* **Overlay and history:** stable-key lookup must select the active tenant-effective type for creation while existing occurrences retain their stored type id and historical fallback. A tenant override must not orphan or invisibly replace a stored occurrence.

## Later schema-removal prerequisites

Legacy columns may be considered for deletion only in a separate, approved phase after: all production readers and writers pass the static allowlist gate; at least one release/rollback observation window completes; CaseDates-to-completed-migration reconciliation remains exact; the 610 anomalies have an approved retention disposition without fabricated dates; no supported client depends on scalar legacy API fields; exports/reports are verified; RLS, overlay, concurrency, audit, `UpdatedAt`, soft-delete/history, time semantics, and calendar routing tests pass; rollback no longer requires the columns; and a reviewed removal migration has its own backup, validation, and rollback plan.

## Desktop Cases grid, board, and export cutover (2026-08-10)

This gate converts only the desktop Cases surface rooted at `CasesController` and its two existing presentations: the JavaFX `TableView` grid and the `FlowPane` card board produced by the shared `CaseCardFactory`. Production composition is `SceneManager.createCasesView` → `CasesController` → `CaseSummaryDao.findActiveGridPage`. Row/card selection, double-click/Enter navigation, the shared card layout, status/search filters, infinite card paging, full grid loading, load generations, and base ordering remain unchanged.

The grid and board display four fixed meanings: timed `intake` (rendered with the existing date-only list presentation), and all-day `date_of_injury`, `statute_of_limitations`, and `tort_notice_deadline`. The authoritative query composes them by Case ID through `CaseDates`, `CaseDateTypes`, and effective semantic-role mappings; missing occurrences remain null and optional relationships cannot remove the Case. The controller never reads those values from `CaseRow` or deprecated `Cases` convenience columns.

Intake and statute sorting participate in SQL paging, so `CaseSummaryDao.findActiveGridPage` resolves authoritative `CaseDates`, stored `CaseDateTypes`, and semantic-role mappings before `OFFSET/FETCH`; it also supplies authoritative aliases for the bounded base row. Search and status filtering are not date filters. Export calls `listActiveGridForExport`, which reuses that exact count/data boundary in 500-row batches with Case ID as the final ordering tie-breaker. Each page resolves export-only values set-wise, and there is no per-Case hydration. The immutable returned list and the existing in-memory workbook are intentionally retained for compatibility; SQL retrieval is bounded, but total export memory remains proportional to the result size.

The old Cases export boundary was `CasesController.exportCases` → `CaseExportService.exportCases` → `CaseDao.listCasesViewForExport` plus `CaseServicePort.projectMigratedCaseDates`. The new boundary is the same UI command → `CaseExportService` → `CaseSummaryDao.listActiveGridForExport` → consumer-specific `ExportCaseRow` → existing CSV mapping or `CaseXlsxExporter`. It reuses projection Case identity, status identity/display, Practice Area identity/display, and responsible/legal-assistant identity/display. Export-scoped enrichment retains client and opposing-party presentation, deterministic latest nondeleted update (`CreatedAt`, then `Id`), description, and authoritative intake/injury/statute/tort dates without expanding the shared projection. Its eleven headers and order, UTF-8 CSV handling, typed date cells, Unicode-safe Excel truncation, background execution, duplicate-export prevention, PHI read audit, and PHI-safe user error remain intact. Reports continue using their existing report DTO/query and service constructor and are deliberately deferred.

The converted runtime path no longer consumes the legacy `CallerDate`/`CallerTime`, `DateOfInjury`, `StatuteOfLimitations`, or `TortNoticeDeadline` values for grid, board, or Cases export. The other migrated fields were never displayed or exported by these consumers. Shared `CaseRow`, `CaseDetailDto`, `CaseOverviewDto`, and their legacy compatibility properties remain because Search, Deleted Cases, MyShale, user/related-case views, reports, API, and web still call them. Those deferred queries and normal legacy-shaped writers are not falsely marked converted.

Cases now observes the established PHI-safe `CaseDates` entity invalidation. Same-tenant cross-instance events are initiating-instance suppressed, event-id deduplicated, refresh-coalesced, and restart the authoritative page query so date ordering/paging cannot become stale; the existing generation guard discards older loads. The event contains identifiers/change classification only. Export captures a projection snapshot when invoked and does not subscribe.

This gate adds no CaseDates or CalendarEvents mutation, no schema/type change, no label identity, no dual read/comparison, and no legacy fallback. Case View, configurable Intake, Calendar, and the shared projection contract are unchanged. Remaining readers/writers are those explicitly deferred above plus migration/schema/audit compatibility evidence. The next gate is **Search and Deleted Cases**.


## Server/web Organization related Cases cutover (2026-08-12)

The production path `GET /api/organizations/{organizationId}` → `OrganizationServiceAdapter.getOrganizationDetail` now delegates exactly once to `CaseSummaryDao.listActiveRelatedToOrganization`. The existing `OrganizationServicePort` and React response shape remain unchanged. The adapter maps only authoritative semantic Intake and Statute occurrences into `RelatedCaseSummary`; tort remains internal and is not added to the API. Missing occurrences remain null, and workflow flags cannot create occurrences.

The shared query preserves trusted session/request tenant equality, active nondeleted Organization and Case predicates, one result per `CaseParties.Id`, Party Role identity/presentation, primary state, responsible-attorney metadata, and primary-first Case-name/Case-ID/relationship-ID ordering. It resolves dates set-wise from stored `CaseDateTypeId` identities and active tenant-effective semantic-role mappings, retaining historical type presentation behavior and performing no per-Case hydration. `OrganizationDao.findRelatedCases` was removed after call-site verification found no remaining production consumer; `ContactDao.findRelatedCases` is intentionally retained. Existing Organization detail PHI-read behavior is unchanged because the controller/session and response boundary did not change and this slice adds no mutation or audit event.

The active Organization path no longer reads or falls back to `Cases.CallerDate`, `Cases.StatuteOfLimitations`, or `Cases.TortNoticeDeadline`. Server Case search, server assigned Cases, and desktop User Detail assigned Cases are now complete; Documents, `CaseOverviewDto`, and mutations remain deferred.

## Server/web search and My Cases runtime cutover

Server/web Case search (list and page endpoints) and assigned/My Cases now use the bounded
`CaseSummaryDao` server projection with authoritative tenant-effective semantic Case Dates. The
legacy ID lookup plus per-result `CaseDao.getOverview` hydration is removed. Existing response fields,
matching, paging/limits, assignment scope, responsible-attorney metadata, null behavior, and ISO
`LocalDate` serialization are retained. `CaseDao.getOverview` remains for explicitly deferred desktop
Case View/document compatibility. Desktop User Detail assigned Cases now uses `CaseSummaryDao.listActiveAssignedForUserDetail` with a bounded, set-based authoritative CaseDates projection and the existing card contract. The legacy `CaseDao.listActiveCasesForUserTeamMember` method was removed after no-caller verification. Desktop document generation and `CaseDao.getOverview` cleanup are the next remaining cutover. Live SQL Server verification was not performed.


## Desktop User Detail assigned Cases runtime cutover

`SceneManager.createUserView` → `UserController.refreshAssignedCasesAsync` →
`UserDetailService.loadAssignedCases` now delegates to the consumer-specific
`CaseSummaryDao.listActiveAssignedForUserDetail` projection and maps it to the unchanged `CaseRow` /
shared Case-card contract. One bounded statement applies authenticated tenant scope, selected-user
`CaseUsers` membership, active/nondeleted Case eligibility, the existing result limit, deterministic
authoritative-intake-descending then Case-ID-descending ordering, and set-based optional card metadata.
Tenant-effective protected mappings resolve intake, statute, and tort; `date_of_injury` remains the
authoritative injury type identity. Missing occurrences stay null, and neither workflow flags nor other
dates fabricate them. The controller's executor, generation, selected-user, and cache-tenant staleness
guards are unchanged.

The legacy `CaseDao.listActiveCasesForUserTeamMember` query and its obsolete SQL test were removed after
production no-caller proof; focused authoritative and no-caller coverage replaces it. No live SQL Server
verification was performed. Desktop document generation and `CaseDao.getOverview` cleanup are next.

## Final runtime compatibility cleanup and desktop intake hardening (2026-08-13)

Production call-site reconfirmation found no runtime consumer of the scalar
`CaseDao.createBasicCase`/`insertBasicCase`, `CaseDao.updateCase`,
`CaseDao.updateCaseDetails`, `ContactDao.findRelatedCases`,
`CaseDao.findMyCasesPage`, `CaseDao.listAssignedCasesForBoard`,
`CaseDao.searchCasesByName`, `CaseDao.searchDeletedCasesByName`,
`CaseDao.getCaseRow`, `CaseDao.getMyCaseRow`, or the two legacy Case Status report
methods. The production replacements were already `CaseDateDao.createCaseAggregate`,
non-migrated and authoritative aggregate update boundaries, `CaseSummaryDao` related,
search/deleted, assigned/MyShale, report, export, and single-row projections. Those
dead methods and the unused `CaseGateway.updateCase` declaration/delegation were
removed. The active web creation gateway was renamed `createCaseAggregate`; it still
delegates to the single `CaseDateDao` aggregate transaction and is not a second create
path. Broad `CaseDao` paging composition no longer has a legacy-date mode: when date
sorting is requested it always resolves `CaseDates`, and its compatibility-shaped date
columns are null rather than selected from `Cases`.

Desktop New Intake now requires the current authoritative `NEW_INTAKE`
`FormConfigurations` row and matching row version. A missing configuration raises the
existing safe reload/configuration exception during validation, before party-role
seeding, Contact, Case, CaseDate, status, party, audit, timeline, publication, or any
other persistence. A changed configuration retains the same fail-closed reload
message. The only reachable Case insert is the configured insert, which omits all nine
migrated date meanings (and `CallerTime`); configured values create `CaseDates` in the
same transaction. Existing commit, rollback, child/contact/party/status construction,
intake attribution, audit ownership, and post-commit behavior are unchanged.

Intentionally retained compatibility is narrow and consumer-proven:
`CaseDao.getOverview` remains used by desktop Case View non-date hydration and document
composition; its dates are authoritatively overlaid before consumption.
`CaseOverviewDto` date getters remain part of the server JSON/desktop DTO response and
are populated from authoritative projections. Deprecated `CaseDetailDto` date aliases
remain server compatibility properties and derive from `mappedCaseDates`. The
`MigratedCaseDateKey` identifiers and historical PHI/audit field vocabulary remain
unchanged. Accepted, denied, and closed workflow dates, plus
`FeeAgreementSigned` and `NonEngagementLetterSent`, remain on their independent
workflow paths.

No active production create or update path writes any of the nine migrated
`dbo.Cases` date columns, and no active production read uses them. Their names remain
only where required for historical migration, reconciliation, rollback, schema and
validation evidence, PHI/audit vocabulary, frozen compatibility identifiers, and
architecture documentation. The database columns themselves are not changed.

Final remaining Case Dates work is SQL Server integration/manual QA and the separately
scoped schema support for Case Date Type entity-action auditing. This phase did not
perform live SQL Server verification and does not claim it.
