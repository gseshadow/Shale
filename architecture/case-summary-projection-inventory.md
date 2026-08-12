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
| Cases export | `CasesController.exportCases` → `CaseExportService.exportCases` → `CaseSummaryDao.listActiveGridForExport` | shared summary plus export-scoped dates, party/client, latest-update, and description enrichment | immutable current grid search/status/sort snapshot; 500-row SQL pages | Converted; Reports and every deferred consumer retain their legacy boundaries. |
| Search | `SearchService.searchAll` → `CaseSummaryDao.searchActiveByName` | shared summary plus compact-card dates/color/flag | active; literal case-insensitive name substring; score/name/ID order; unpaged | Converted for active desktop Search only; Deleted Cases and API/web search retain legacy paths. |
| Deleted Cases | `SearchService.searchAll` → `CaseSummaryDao.searchDeletedByName` | shared summary, compact-card dates/color/flag, and restore `RowVer` | deleted only; literal case-insensitive name substring; score/name/ID order; unpaged | Converted for the admin-only desktop Deleted Cases group. Legacy DAO retained for compatibility. |
| MyShale paging | `CaseDao.findMyCasesPage` | `CaseRow` | active by default; any `CaseUsers` membership; legacy date compatibility; caller-selected sort | Core fields fit; membership and compatibility date behavior remain separate. Deferred. |
| MyShale/user detail assigned cases | `CaseDao.listActiveCasesForUserTeamMember`, `UserDetailService.loadAssignedCases` | rich card data | active; any `CaseUsers` membership; intake/id descending; limit | Core fields fit; membership, limits and card enrichment remain separate. Deferred. |
| Contact related Cases | `ContactDao.findRelatedCases` | local `RelatedCaseRow`: ID/name, party role/side, status, responsible attorney | contact and tenant; active contact/case relationships; name order | Case core fits; relationship role/side remains in Contact query. Deferred. |
| Organization related Cases | Desktop and server/web `OrganizationServiceAdapter.getOrganizationDetail` → `CaseSummaryDao.listActiveRelatedToOrganization` | shared `RelatedCaseRow`: authoritative summary/dates plus relationship role/side/primary metadata | tenant-active organization/cases; one row per relationship; primary/name/Case/relationship order | Converted for desktop and server/web Organization detail; legacy `OrganizationDao.findRelatedCases` removed. |
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

## Desktop Search Case-results cutover

The old active-Case path was `SearchController` → `SearchService.searchAll` →
`CaseDao.searchCasesByName` → rich legacy `CaseRow`. The active-Case group now uses
`CaseSummaryDao.searchActiveByName` and its search-scoped `SearchCaseRow`; the admin-only Deleted
Cases group deliberately continues through `CaseDao.searchDeletedCasesByName`. Contacts,
organizations, users, tasks, and calendar events retain their existing providers and cards.

The preserved Case contract trims surrounding whitespace, treats blank as idle without querying,
and performs a case-insensitive literal substring match against `Cases.Name` only. Percent,
underscore, and opening-bracket characters are escaped as literals. There is no minimum length
beyond nonblank, no tokenization or Case-number normalization, and no Case number, description,
update, party, contact, client, date, status, Practice Area, or assignee matching. Only active
(`ISNULL(IsDeleted,0)=0`) Cases in the trusted session tenant are returned. The established search
has no limit or paging boundary. In-memory precedence remains exact, prefix, word-boundary prefix,
then substring, followed by case-insensitive name and authoritative Case ID; the DAO also ends its
deterministic base order with Case ID.

The compact card reuses projection Case/tenant identity, Case name/number, authoritative status,
Practice Area, responsible-attorney and legal-assistant IDs/display, timestamps, and deletion state.
Search-only enrichment is limited to the fields already displayed by that card: authoritative
semantic intake/statute/tort dates, Practice Area color, and the non-engagement flag. Scalar applies
and date aggregation retain matching Cases with missing optionals and prevent multiplication; there
is one statement and no result hydration. No narrative, update, contact, party, or client value is
selected or searched, and no deprecated `Cases` convenience date is read.

Each controller load captures immutable query, generation, tenant, and user values. Clearing,
replacement, or live refresh increments the generation; both success and failure callbacks validate
generation and current identity context before any FX-thread render, count/summary, selection, empty,
loading, or error mutation. Provider failures retain the established explicit partial-results model
and full exception logging without logging returned PHI. Existing `CaseCardFactory` compact rendering
and Case-ID navigation are unchanged. The legacy active search method remains because the server/API
service adapter still calls it; Deleted Cases, related views, My Shale, Reports, Documents, Calendar,
server/API/web, active grid, board, and export are intentionally unchanged.

## Desktop Deleted Cases cutover

The Deleted Cases consumer is the admin-only group within desktop global Search. Its old read path
was `SearchController` → `SearchService.searchAll` → `CaseDao.searchDeletedCasesByName` → rich legacy
`CaseRow`; its new read path ends at `CaseSummaryDao.searchDeletedByName` and the scoped
`DeletedCaseRow`. Search initialization, its single background executor, query generation, tenant/user
identity guard, FX-thread application, provider-level partial-error state, LiveBus reload subscription,
empty/loading presentation, compact `CaseCardFactory`, confirmation dialog, and navigation remain the
existing Search lifecycle. A restore now invalidates the current generation before work begins, and
both restore success and failure callbacks verify that generation and identity. Success publishes the
existing PHI-free deleted-state event and reloads; conflict/failure keeps the card and reloads to obtain
authoritative state. Thus a pre-restore load cannot reintroduce a restored Case.

The exact population is every `Cases.IsDeleted = 1` Case in the trusted session tenant whose Case name
contains the nonblank trimmed query, case-insensitively and with SQL LIKE metacharacters treated
literally. Blank search remains idle and issues no Deleted Cases query. There are no status or Practice
Area filters, paging, or result limit. SQL orders by Case name then Case ID; established in-memory
ranking remains exact, prefix, word-boundary prefix, substring, case-insensitive name, then Case ID.
Current status is resolved by the shared open-status deterministic rule even if its definition is
inactive; absent status/assignments remain absent, and selected assignment IDs survive unavailable
display hydration. No deletion timestamp or actor is displayed.

The row reuses summary Case/tenant identity and number/name, authoritative current-status identity and
display, Practice Area identity/name, responsible-attorney and legal-assistant IDs/display,
created/updated timestamps, and deleted state. Deleted-only composition adds only the compact card's
authoritative semantic Intake, Statute, and Tort dates, Practice Area color, non-engagement flag, and
the `Cases.RowVer` restore token. Scalar applies and date aggregation keep one row per Case and retain
missing optional relationships without per-row hydration. It adds no parties, contacts, updates,
narratives, or description and reads no deprecated `Cases` date column.

Restore continues through `SearchController` → `CaseDetailService` → the established transactional
`CaseDao` lifecycle operation. The accepted row's authoritative Case ID and defensively copied
`RowVer` are now supplied to the existing tenant/session/actor-verified update; audit vocabulary,
timeline insertion, rollback, and optimistic-concurrency semantics are otherwise unchanged. The
legacy two-argument restore method and `CaseDao.searchDeletedCasesByName` are retained because deleting
and other deferred compatibility paths must not be migrated in this slice. Active grid, board, export,
active Search, remaining My Shale, related views, Reports, Documents, Calendar, server, API, and web
are unchanged.

## Desktop Contact/Organization Related Cases cutover

The desktop Related Cases consumers are the read-only Contact and Organization detail sections, not
a Case-to-Case link table. Their authoritative relationship is one directional `CaseParties` row
from a Case (`CaseId`) to either a Contact (`ContactId`) or Organization (`OrganizationId`), carrying
the relationship ID, `PartyRoleId`, side text, primary flag, and notes. One card is retained per
`CaseParties.Id`; multiple roles between the same entities therefore remain distinct. The old paths
were `ContactViewController` → `ContactDetailService` → `ContactDao.findRelatedCases` and
`OrganizationController` → `OrganizationDao.findRelatedCases`. Both desktop paths now compose
`CaseSummaryDao.RelatedCaseRow` around `CaseSummaryProjection`. The legacy DAO methods and DTOs remain
for the server adapter and deferred compatibility consumers.

Both queries require trusted session tenant equality, constrain the related Contact/Organization and
Case to that tenant, and retain only active entities and active Cases. There is no paging or result
limit. SQL orders primary relationships first, then Case name, Case ID, and relationship ID. Contact
View retains its Case-name ordering; Organization View retains its in-memory Case-name/intake/statute
sorting and Case-name/responsible-attorney filtering. Neither section has an add selector or direct
relationship mutation control: creation, editing, and removal remain in the established Case Parties
transactional boundary, so no mutation, audit, concurrency, confirmation, or selector behavior was
changed.

Cards continue through `CaseCardFactory.FULL` and navigate by the projection's authoritative Case ID.
They reuse Case identity/name/number, authoritative current status identity/display, Practice Area
identity/name, both authoritative assignment identities/display, timestamps, and deleted state.
Related-only composition contains `CaseParties`/Party Role identity and display metadata plus the
already-rendered Practice Area color, non-engagement flag, and authoritative semantic Intake,
Statute, and Tort dates. A single set query uses scalar applies and aggregation; missing optional
status, Practice Area, or assignments cannot remove or multiply a relationship.

Contact loading retains its generation and Case-independent snapshot lifecycle, with stale failures
now rejected as well as stale successes. Organization related loads now capture relationship-load
generation, Organization ID, and tenant ID; both success and failure callbacks validate all captured
context on the JavaFX thread before changing cards or empty state. Navigation or refresh therefore
cannot accept an older related-Case result. Active grid, board, export, Search, Deleted Cases,
remaining My Shale, Reports, Documents, Calendar, server/API, and web paths are unchanged.

## Remaining desktop MyShale Case-consumer cutover

The final MyShale audit found one current Case surface and one unreachable compatibility implementation
inside the same controller. The visible Overview and **My Cases** lanes already consumed
`CaseSummaryDao.listActiveAssignedBoard`; the controller still retained an older, non-FXML paging
implementation (`CaseDao.findMyCasesPage`), its `CaseRow` mapper, and a LiveBus single-Case hydration
through `CaseDao.getMyCaseRow`. No selected-user selector exists on MyShale: membership is for the
authenticated `AppState.userId`, captured with `AppState.shaleClientId`. My Tasks case selectors and
embedded Case metadata remain task-service data and are not independent Case-list consumers.
`UserDetailService.listActiveCasesForUserTeamMember` is a separate user-detail surface and remains
explicitly deferred.

MyShale now has one Case read/list entry point:
`SceneManager.createMyShaleView` → `MyShaleController.refreshMyCasesBoard` →
`CaseSummaryDao.listActiveAssignedBoard`. The requested tenant must match SQL Server session context;
the authenticated user ID must identify a nondeleted `Users` row owned by that trusted tenant. Case
membership remains **any** `CaseUsers` row for that user, regardless of assignment role or
`IsPrimary`; multiple assignments are collapsed by `EXISTS`. Cases are explicitly active/nondeleted.
The query does not exclude closed Cases; configured status lanes and their ID-based filter preserve
the established presentation, including an ID-keyed lane for an unconfigured status and a No Status
lane. Empty lanes remain omitted. SQL returns a complete snapshot ordered by status ID, Intake date
descending, then Case ID descending. In-memory lane sorts preserve Name, Intake, Statute, Tort,
Updated oldest, and Updated newest behavior and use Case ID as the final tie-breaker.

The card composition reuses Case/tenant ID, number/name, authoritative current-status ID/stable
keys/name/color, Practice Area ID/name, responsible-attorney and primary-legal-assistant IDs/display,
created/updated timestamps, and deletion state. MyShale-only enrichment remains the three configured
semantic dates, Practice Area color, and non-engagement flag. Dates come only from `CaseDates`,
`CaseDateTypes`, and active semantic-role mappings. Status and assignments reuse scalar shared
projection resolvers with `RoleSemantics`; there is no label identity, numeric role literal, row
multiplication, per-Case hydration, narrative expansion, or deprecated `Cases` date read.

Initial loading remains gated on status-option completion. Every snapshot captures tenant, user, and
generation; success and failure apply on the FX thread only while all three remain current. LiveBus
now invalidates and replaces the authoritative snapshot instead of issuing the legacy per-Case query,
so a membership removal, tenant/user change, or superseded refresh cannot leave a stale card or
counter. Loading, accepted empty, and failure remain distinct. The visible section's scene lifecycle
owns LiveBus subscription and rejects refreshes after replacement. This changes a live event from one
legacy hydration query (plus fallback full load on failure) to one set-based snapshot query, while
normal loads remain one validation query plus one set query and have no N+1 work.

The unreachable paging fields/methods and their `CaseRow` mapping were removed from
`MyShaleController`. `CaseDao.findMyCasesPage`, `getMyCaseRow`, `listAssignedCasesForBoard`, and
`listActiveCasesForUserTeamMember` remain available because compatibility/service-adapter and deferred
desktop consumers still reference those DAO boundaries. The verified global Cases grid, global Case
board behavior, export, Search, Deleted Cases, Contact/Organization related views, Reports, Documents,
Calendar, server/API, and web paths are unchanged.

## Desktop Reports Case-query cutover

The desktop **Case Status Report** has two distinct grains. Its chart/table query is a status-grain
aggregate (one configured selected status row, including zero-count statuses); its drill-down and XLSX
export are true Case-summary consumers (one row per eligible active Case). Both now enter through
`CaseSummaryDao`: `listActiveStatusReport` and `listActiveStatusReportCases`. The old
`CaseDao.listCaseStatusReport` and `CaseDao.listCaseStatusReportCases` remain intact for compatibility;
no deferred server, API, web, Documents, Calendar, mutation, or saved-report path was changed.

Both new methods require the requested tenant to match SQL Server `SESSION_CONTEXT`, reject status IDs
outside the tenant/global status overlay, bind IDs and inclusive date limits, reuse the shared current
status apply, and explicitly require active Cases. Status identity is by ID; null-status Cases remain
excluded because the existing report contains only selected configured statuses. Detail ordering remains
intake descending and Case ID descending. The selected statuses, blank date defaults, inclusive start/end,
zero-count rows, table/chart fields, drill-down columns, filenames, XLSX ordering, typed cells, and audit
boundary are unchanged.

The report's Intake, Date of Injury, Statute of Limitations, and Tort Notice values now come set-wise from
`CaseDates`/`CaseDateTypes` and the effective semantic-role mapping (Date of Injury retains its deployed
stable system key). Denied and Closed remain their workflow-owned `Cases` timestamps as classified in
`case-dates.md`; they are not silently reinterpreted as user-managed semantic dates. Responsible attorney
and primary legal assistant use `CaseUsers.RoleId` with `RoleSemantics`, primary/recent deterministic
selection, and tenant-owned Users. Optional status, Practice Area, and assignments use scalar applies or
left joins, so they cannot multiply a Case. Report-only description and dates stay in `ReportCaseRow`
rather than expanding `CaseSummaryProjection`.

The aggregate is one statement and each selected-status export currently remains one statement, matching
the prior export query count while removing legacy SQL and per-row hydration from the desktop Reports
consumer. Report/status loads capture a monotonically increasing generation and tenant; stale successes,
failures, and loading-state callbacks are discarded, and empty status selection also advances the
generation. Controller database and workbook work remain on the existing daemon executor, JavaFX changes
remain dispatched with `Platform.runLater`, and export continues to use immutable `ReportCriteria` and
status-name snapshots with the existing success/failure cleanup.

## Desktop Documents consumer cutover

The desktop Documents surface is the Case-profile summary export rooted at
`CaseController.generateAndOpenSummary`. It has no Case search, list, selector, filter, paging, recent-Case,
or display-name identity path: navigation supplies the authoritative Case ID, and both supported formats
(HTML and PDF) generate the same `CASE_SUMMARY` document. The old composition called
`CaseDao.getOverview`, `CaseDao.getDetail`, up to two tenant-scoped `ContactDao.findById` lookups, and
`CaseDao.listCaseUpdates`; the overview redundantly supplied Case name, current status, and Practice Area.

This cutover classifies that workflow as a document-specific composition (one immutable document model per
active Case), wrapping the true one-Case summary consumer `CaseSummaryDao.findActiveForDocuments`. The new
lookup requires the requested tenant to match SQL Server session context, binds the tenant-scoped Case ID,
requires an active/nondeleted Case, and returns zero or one row. It reuses the shared projection SQL and its
authoritative current-status, Practice Area, responsible-attorney, and primary-legal-assistant resolution;
scalar applies and the left Practice Area join preserve missing optional values without multiplying Cases.
The document consumes only `caseName`, `primaryStatusName`, and `practiceAreaName`. Status color and both
assignment identities remain correctly mapped in the projection but are not document template fields.

The full-detail generation queries remain specialized and unchanged: overview still supplies primary caller
and client identity plus incident/statute semantic dates and description; detail supplies workflow accepted,
denied, and closed dates plus narrative summary and retains its existing related-contact grain; contact
lookups supply the established phone/address/email values; updates retain their deterministic created-time/ID
ordering. Templates, placeholders, normalization, filenames, formats, temporary storage, rendering, opening,
and PHI audit timing are unchanged. Legacy PDF debug logging no longer emits generated XHTML snippets or
temporary paths, and controller failures no longer echo exception/path details that can contain Case names.
Calendar, server/API/web, template management, persistence/mutations,
and every previously converted desktop consumer are deferred and unchanged.

Before dispatch, `CaseController` now captures tenant ID, authenticated user ID, Case ID, type, and format in
an immutable `CaseDocumentGenerationRequest`. Incomplete context cannot generate. The background task uses
only that snapshot, while a monotonic generation plus tenant/user/Case comparison rejects stale successes and
failures after a selection or controller-context change. Database reads and rendering remain on the existing
daemon worker and accepted UI callbacks remain on the JavaFX thread. The authoritative validation adds one
bounded query per generation; the existing document-only overview/detail/contact/update hydration is retained
because it supplies genuine generation content rather than Case-summary data.

## Desktop Calendar Case-query cutover

The desktop Calendar inventory has four distinct grains. `CalendarFeedDao.listCalendarFeed`,
`listCalendarFeedForCase`, and `listCalendarFeedForUserSchedule` remain one feed item per persisted
event, task due projection, authoritative Case Date occurrence, or lifecycle-date projection. Event
ID/occurrence key remains authoritative; the optional Case ID and name are presentation/navigation
composition only. `CalendarEventDao.getById` and global-search rows remain persisted-event grain.
`CalendarFeedDao.listTaskCardRows` remains task grain. Event types, user overlays, task assignments,
Calendar mutations, notifications, reminders, external synchronization, and Case-detail Calendar are
specialized or deferred paths and are unchanged.

The eligible one-Case paths were the event editor's related-Case card and the create/edit Case
selector. Their old paths were `CalendarController` -> `CalendarFeedDao.listCaseCardRows` and
`CalendarController` -> `CaseDao.listCaseSelectionOptions`. Both independently resolved a purported
responsible attorney, including a hard-coded role `1`; the card join could multiply a Case. Their new
paths are `CalendarController` -> `CaseSummaryDao.findActiveForCalendar` and
`listActiveForCalendar`, returning `CalendarCaseRow` around the authoritative
`CaseSummaryProjection`. Both verify the requested tenant against SQL Server session context, require
active Cases, bind Case identity, use shared current-status and semantic assignment resolution, retain
missing optional relationships, and order the selector by case name then Case ID. The selected and
opened identity is always Case ID; duplicate or renamed labels do not participate in resolution.
Deleted or cross-tenant IDs do not resolve, preserving the prior related-card/selector eligibility.
Events with null Case IDs remain unchanged.

Calendar reuses Case/tenant ID, number/name, current-status identity/stable keys/name/color, Practice
Area identity/name, responsible-attorney and primary-legal-assistant IDs/names/colors, timestamps, and
deleted state. `NonEngagementLetterSent` remains narrowly scoped card presentation outside the shared
projection. Event IDs/types, title/description, start/end, all-day, recurrence/source identity,
assigned users, attendees, reminders, task/feed data, colors, and Calendar display calculations remain
Calendar-owned. Feed population, visibility, in-memory Case/event-type/source/user filters, search,
day/week/month rendering, range `[startInclusive,endExclusive)`, Case Date intersection behavior,
local timestamps, all-day handling, recurrence behavior, limits, and `StartsAt, AllDay, KeyValue`
deterministic ordering are unchanged.

The selector remains one off-FX-thread set query with no paging or per-Case hydration. Opening an event
continues its existing bounded background hydration and now performs one authoritative Case-summary
lookup instead of the legacy Calendar Case join. Existing load generations continue rejecting stale
feed successes/failures after date/view/filter refresh; JavaFX application remains on the FX thread.
No mutation transaction, audit timing, RowVer behavior, reminder/notification write, recurrence,
feed, or synchronization boundary changed. The legacy `CaseDao.listCaseSelectionOptions` remains for
unverified compatibility consumers; the Calendar-exclusive `CalendarFeedDao.listCaseCardRows` and its
DTO were removed. Previously verified grid, board, export, Search, Deleted Cases, related views,
MyShale, Reports, and Documents consumers, plus server/API/web, are unchanged.

## Server/web Case search and assigned Cases cutover

The production paths `GET /api/cases/search` and `GET /api/cases/search-page` now run through
`ApiReadController` → `CaseServiceAdapter.searchCases` → `CaseSummaryDao.searchActiveForServer`.
`GET /api/cases/assigned` runs through `CaseServiceAdapter.listAssignedCases` →
`CaseSummaryDao.listActiveAssignedForServer`. Both boundaries are bounded, set-based projections;
the former Case-ID result followed by one `CaseDao.getOverview` hydration per Case is gone.

The server projection preserves the existing `CaseOverviewDto`/React shape and supplies intake,
date of injury, statute of limitations, and tort notice exclusively from active `CaseDates` through
the tenant-effective semantic-role mapping. Absent occurrences remain null, timed intake is reduced
to the contract's existing `LocalDate`, and workflow flags cannot fabricate dates. Tenant and actor
come from the authenticated runtime session, active/nondeleted and assignment predicates remain in
SQL, and name/Case-ID ordering plus bounded paging/limits are deterministic. Compatibility
`CaseDao.getOverview` remains for desktop Case View and document-era consumers; desktop User Detail's
`listActiveCasesForUserTeamMember` is the next smallest Case Dates cutover. No live SQL Server
verification was performed for this documentation update.
