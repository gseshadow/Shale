# Authoritative Case summary projection and compatibility inventory

This note records the discovery performed before introducing `CaseSummaryProjection`. The first phase adds the shared read boundary without removing or rerouting legacy consumers.

## Authoritative boundary

`CaseSummaryDao.list(tenant, deletedState, order)` is a single-statement, PHI-minimized read. It first compares the requested tenant with `SESSION_CONTEXT(N'ShaleClientId')`, then also predicates `Cases.ShaleClientId`. A missing or conflicting context fails. Deleted state and ordering are required enums, never implicit defaults.

Each optional one-to-many relationship is selected by `OUTER APPLY TOP (1)`, so a Case remains present and malformed legacy primaries cannot multiply rows. Current status candidates must have `CaseStatuses.EndDate IS NULL`; selection is primary first, then effective date, update/create timestamps, and row ID descending. Status definitions must be global or owned by the Case tenant. Responsible attorney (role 4) and primary legal assistant (role 11) are selected by `CaseUsers.RoleId`, preferring `IsPrimary`, then update/create timestamps and row ID descending; this preserves an assignment when malformed legacy data has no primary flag. User display hydration additionally requires the Case tenant, while the selected authoritative user ID is retained even if display hydration is unavailable.

The projection contains: Case ID, tenant ID, Case number/name; status ID, `SystemKey`, `LifecycleKey`, name/color; Practice Area ID/name; responsible-attorney ID/name/color; primary-legal-assistant ID/name/color; created/updated timestamps; and deleted state. It intentionally excludes descriptions, summaries, updates, notes, parties/contact details, medical data, deadlines, and `RowVer`. Intake and other Case Dates remain outside this projection until the shared query can consume the authoritative `CaseDates` semantic-role boundary rather than legacy `Cases` convenience dates.

## Existing consumer inventory

| Consumer | Existing method/query | Consumed fields | Filters/order | Projection fit / intentionally separate data |
|---|---|---|---|---|
| Cases list/grid | `CaseDao.findCasesViewPage` / `findPageInternal` | Case name; current status; Practice Area color; responsible attorney; migrated Case Dates; client/opponents; latest update; description; non-engagement flag | active by default; optional closed/denied, text and status filters; all `CaseSort` orders; paging | Identity/status/assignment fit. Migrated dates, party names, update, description, non-engagement and paging/filter policy stay consumer-specific. Deferred to avoid behavior change. |
| Case board | `CaseDao.listAssignedCasesForBoard` | `CaseRow` card data including status, attorney, dates, parties, update and description | active only; assigned-user membership; status/date order | Core fields fit; membership, card enrichment and lane behavior remain separate. Deferred. |
| Cases export | `CaseDao.listCasesViewForExport`, then `CaseExportService` batch Case Date projection | grid fields plus authoritative migrated dates | same grid criteria/order; 500-row pages | Core fields fit; export shape and dates remain separate. Deferred. |
| Search | `CaseDao.searchCasesByName` | rich `CaseRow` card data | active; name `LIKE`; updated descending, limited | Core fields fit; ranking/search filter and rich card enrichment remain separate. Deferred. |
| Deleted Cases | `CaseDao.searchDeletedCasesByName` | rich `CaseRow` card data | deleted only; name `LIKE`; updated descending, limited | Explicit `DELETED` mode fits lifecycle; restore UI/card enrichment remains separate. Deferred. |
| MyShale paging | `CaseDao.findMyCasesPage` | `CaseRow` | active by default; any `CaseUsers` membership; legacy date compatibility; caller-selected sort | Core fields fit; membership and compatibility date behavior remain separate. Deferred. |
| MyShale/user detail assigned cases | `CaseDao.listActiveCasesForUserTeamMember`, `UserDetailService.loadAssignedCases` | rich card data | active; any `CaseUsers` membership; intake/id descending; limit | Core fields fit; membership, limits and card enrichment remain separate. Deferred. |
| Contact related Cases | `ContactDao.findRelatedCases` | local `RelatedCaseRow`: ID/name, party role/side, status, responsible attorney | contact and tenant; active contact/case relationships; name order | Case core fits; relationship role/side remains in Contact query. Deferred. |
| Organization related Cases | `OrganizationDao.findRelatedCases` | local `RelatedCaseRow`: ID/name, party role/side, status, responsible attorney | organization; active relationships; name order | Case core fits; organization relationship metadata remains outside. Deferred. |
| Reports | `CaseDao.listCaseStatusReport`, `listCaseStatusReportCases` | status identity/display/counts and report detail rows | active; current effective status; status/date filters; status sort order | Status core fits; aggregation, report dates and detailed report columns remain separate. Deferred. |

## Conflicts found

Status selection was not uniform: several card queries prefer `IsPrimary` and recency even when a row has ended, while report and newer queries require `EndDate IS NULL` and use `EffectiveDate`. Assignment queries consistently use IDs and role 4, but some require `IsPrimary = 1`, some admit any team membership for filtering, and older calendar SQL still contains role 1. Date authority also differs: the Cases grid/export use migrated `CaseDates`, while MyShale deliberately retains legacy `Cases` date compatibility. Rich `CaseRow` queries repeatedly join Practice Areas and independently apply status, assignment, party, update, contact, and organization enrichment. Those joins also caused each query to carry its own deletion and tenant details.

## Main active Cases grid cutover

The main JavaFX surface is `SceneManager.createCasesView` → `CasesController` →
`CaseSummaryDao.findActiveGridPage`. Its controller retains 100-row SQL paging (cards request the
next page on scroll; grid mode continues requesting pages), SQL-backed debounced case-name search,
the status multi-filter, cached total counts, generation-based stale-load rejection, and the existing
`CaseCardFactory`, table row, Enter-key, and double-click navigation paths. The supported orders are
intake newest/oldest, statute soonest/latest, case name ascending/descending, responsible attorney
ascending/descending, and current status ascending/descending. Each is a `GridOrder` allow-list value
and every SQL expression ends in a Case ID direction-matched tie-breaker.

The grid reuses every matching `CaseSummaryProjection` field: Case/tenant identity, number/name,
authoritative current-status identity and display, Practice Area identity/name, responsible-attorney
and legal-assistant identities/display, timestamps, and deletion state. A scoped `CaseGridRow` keeps
the existing consumer-only Practice Area color, non-engagement flag, client/opponent labels, latest
update, description, and four displayed dates out of the shared PHI-minimized record. Enrichment uses
`OUTER APPLY`, aggregation, and a bounded page query, so missing optionals remain and malformed
one-to-many data cannot multiply Cases. Intake, statute, and tort dates resolve through
`CaseDateTypeSemanticRoleMappings`; injury continues through the authoritative stored
`CaseDateTypes.SystemKey`. No deprecated `Cases` date convenience column is read and no per-row DAO
hydration remains.

The prior `CaseDao.findCasesViewPage` entry point is intentionally retained because export and other
deferred compatibility paths still compile against the rich `CaseRow`. `listCasesViewForExport`, the
board, Search, Deleted Cases, MyShale, user/contact/organization related Cases, reports, documents,
API, and web paths are unchanged. In particular, this phase neither copies nor changes the older
calendar role-1 SQL. This is a sensitive read-only cutover: it continues through the established
Cases view/read boundaries and introduces no mutation requiring an entity-action audit transaction.

### Active-grid status-mode correction

The UI status menu is a selection model, not directly a SQL `IN` contract. Selecting every available
status means **unrestricted**, selecting a subset means the established selected-ID filter (while
retaining Cases without a current status), and clearing every status means **no current status**.
`CasesController` now translates those three states to `GridStatusMode`; count and page queries share
the same predicate builder and bind status IDs only in `SELECTED` mode. This corrects the initial
cutover regression where the default 11-of-11 selection was incorrectly emitted as a restrictive
`IN` predicate, allowing a successful count of zero to be rendered as a legitimate empty table.

The follow-up lifecycle correction makes status-option initialization the sole release point for the
first page load. Previously, the FXML startup `runLater` queried while the asynchronous status options
were still empty, which translated to `NO_STATUS`. The options callback later selected 11-of-11 and
the independent results-count refresh correctly reported the unrestricted 1,172 total, but it never
invoked the supplied `loadFirstPage` callback; consequently the table retained the earlier successful
zero-row page. Both status-option completion paths now invoke that callback, and the unconditional
pre-option page request has been removed.

## Assigned Case board cutover

The Case board remains the existing My Shale **My Cases** surface and card factory. Its old entry point
was `MyShaleController` → `CaseDao.listAssignedCasesForBoard`; the new entry point is
`MyShaleController` → `CaseSummaryDao.listActiveAssignedBoard`. Other My Shale queries and every
deferred consumer remain on their compatibility paths. The board reuses projection Case/tenant
identity, number/name, current-status ID/stable keys/display, Practice Area identity/name,
responsible-attorney and legal-assistant authoritative IDs/display, timestamps, and deletion state.
Its scoped enrichment contains only the three displayed semantic Case Dates, Practice Area color,
and non-engagement display flag. It does not add parties, contacts, updates, narrative, or description
to the shared projection.

The single set query explicitly requires active, nondeleted Cases owned by the trusted session tenant
and membership for the signed-in user. Status and both assignment roles use the shared deterministic
projection rules; scalar `OUTER APPLY` and date aggregation preserve missing optionals and one row per
Case. Lane identity and filtering use status ID, never the label. Configured statuses retain their DAO
order; an assigned Case whose status is not in the current options receives its own ID-keyed lane,
and a missing status uses the deterministic **No Status** lane. Duplicate/renamed labels therefore do
not merge lanes. The established UI omits empty lanes and has no Case-board lane visibility/order
preference (the preference DAO applies only to My Tasks lanes), so that behavior is retained.

The established board contract requires all Cases assigned to the current user simultaneously for
lane counts and the Overview widgets. This is user-membership bounded rather than an unbounded
tenant-wide load. Search/status filtering and lane sorting remain in-memory over one accepted board
snapshot; all sort modes end with Case ID. Refresh replaces the full snapshot, so cards cannot be
duplicated or dropped by incremental pagination. Initialization now gates that query until status
options finish, and both success and failure callbacks verify load generation plus user/tenant before
changing loading state, cards, counts, or error state. Existing JavaFX executor/FX-thread application,
LiveBus refresh, loading/empty/error states, selection/navigation, scroll surface, headers/counts, and
`CaseCardFactory` rendering are unchanged. The legacy board DAO remains temporarily because the
service adapter compatibility contract still exposes it; export, Search, Deleted Cases, other My
Shale data paths, related views, reports, documents, API, web, and calendar are unchanged.
