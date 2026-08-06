# Case Dates post-migration runtime cutover inventory

## Decision record status

This is the Phase 3D inventory and cutover plan. Phase 3B backfill and Phase 3C post-validation are complete: 2,233 eligible source rows reconcile exactly, `BlockerCount = 0`, and 610 flag-set/date-missing rows are intentional historical anomalies. The inventory assumes those deployed results and must not rerun, repair, or reinterpret them.

### First runtime-cutover slice (2026-08-06)

The fixed contract and read-foundation gate are implemented: `MigratedCaseDateKey` is the single immutable mapping, rejects the discarded alias, and records that only `intake` supports time. `CaseDateDao.listMigratedSingletonsForCase` uses the existing tenant-scoped occurrence query and effective tenant/global presentation overlay, retains stored historical type fallback, ignores soft-deleted occurrences, and fails explicitly for duplicate active singleton occurrences or a discarded-alias occurrence.

No compatibility reader or writer has been switched in this slice. The exact blocker is the inventory's unresolved concurrency shape: existing case editors submit only `Cases.RowVer` and cannot supply per-occurrence identity/`CaseDates.RowVer` or an explicit expected-absence token. In addition, case creation owns its transaction inside `CaseDao`; adding occurrences afterward would be non-atomic. Per the intake atomicity gate, this slice stops rather than introducing last-write-wins or a partially created case. Consequently all production dependencies classified **CONVERT** below remain, the legacy calendar projection remains until its readers are authoritative, and there is no dual-write. The next gate is an aggregate command/DTO contract carrying the nine occurrence identities and row versions and transaction ownership spanning case creation/update plus occurrence mutations; only after that contract is approved may compatibility hydration and writes cut over together.

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
| `shale-data/src/main/java/com/shale/data/dao/CalendarFeedDao.java` | `CASE_DATE_PROJECTIONS` and its SQL still project all nine legacy fields with `CASE_*:<CaseId>` identities while the authoritative `CaseDates` branch also projects occurrences. This is now a duplicate calendar source. | **REMOVE.** Delete only the nine migrated fixed-column projection rows/SQL. Keep the existing `CASE_DATE:<CaseDates.Id>` branch, category-to-layer routing, tenant/case/owner predicates, soft-delete filtering, timing semantics, and historical type fallback. Status dates (`AcceptedDate`, `DeniedDate`, `ClosedDate`) are not in this migration and remain unchanged. |
| `shale-data/src/main/java/com/shale/data/dao/OrganizationDao.java` and `ContactDao.java` | Organization/contact related-case queries select `CallerDate`, injury, SOL, and tort dates for case summaries. | **CONVERT.** Use set-based tenant-safe mapped occurrence projection; avoid per-row DAO calls. Preserve existing relationship and active-case predicates. |
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
