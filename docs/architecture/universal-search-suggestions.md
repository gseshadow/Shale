# Universal search suggestions and local query history

The desktop shell owns one session-scoped `UniversalSearchPopup`. It anchors beneath the existing search
field, uses Shale's transient popup theme registration and semantic Navigation/Ghost controls, and never
changes the full-results layout. Its specialized `universal-search-choice` class expresses a compact
two-line navigation row with an explicit keyboard selection state; it is not a general button vocabulary.

`SearchService.suggest` and selected-ID revalidation use the dedicated `SuggestionDao`, wired with the same
runtime `DbSessionProvider` as full search. A suggestion request borrows one tenant-initialized connection,
checks the requested tenant against session context (and reads optional Users visibility-column presence in
one metadata query), then executes one parameterized SQL Server batch. The batch also checks tenant context
before reading any business table. It returns only category, ID, display name, identifying detail, and rank.
Each category ranks exact, prefix, then literal substring matches before its SQL bound; category, name and
numeric ID provide stable ordering. Cases, Contacts, Organizations, Users, Tasks, Calendar Events, and permitted
Deleted Cases retain that priority, three rows per category and eighteen overall. The batch skips later categories
once the overall bound is filled. Five seconds bounds the entire batch, rather than every category separately.

Suggestion matching is limited to Cases.Name/CaseNumber/OfficePrinterCode/numeric Id; Contact display, stored,
first and last names; Organization name; User first/last/full name; Task title; and Calendar Event title.
CaseNumber and OfficePrinterCode are verified existing Case identifiers. The latter is an office code, not a
new integration identifier. Deleted Cases use the same fields with explicit deletion and current-principal
admin checks, plus the existing shell permission check. Optional User removed/active/deleted predicates retain
UserDao visibility semantics. Tasks retain the established same-tenant Case existence rule, including its
existing parent-deletion behavior; cancelled events remain excluded.

No Case status/team/date projections, Contact credentials/classifications/contact-point lookups, Organization
entities, Task assignments, narrative fields, or per-result reads are loaded for suggestions. Identifying detail
is a Case code (where present) and stable numeric ID; other categories show the stable ID. Selecting a suggestion
rechecks only that category and ID through the same field and visibility predicates before existing navigation.
Suggestion inputs over 2,048 normalized characters or SQL Server’s 8,000-byte escaped LIKE-pattern limit
yield no suggestions; Enter/View all retain full search.
The existing broader full-search queries, card hydration and matching remain available through Enter and View all.
Calendar navigation only reuses the retained controller when Calendar is the active route. Async Task
detail navigation also checks the captured session revision/tenant/user before showing detail or errors.

Typing is debounced for 250 ms. A single daemon worker retains one running operation and one replaceable
pending operation. Generation checks suppress both successful and failed late callbacks. Invalidation additionally requests JDBC
statement cancellation on a separate daemon worker with one replaceable pending cancellation. No cancel or
database operation runs on JavaFX. A cancellation-registration race still cancels only the captured obsolete
statement. Acquisition itself is not cancellable; a driver that does not promptly honor cancellation can still
delay the latest pending request, bounded by the existing connection configuration and batch timeout. The AppState
session revision invalidates even an away-and-back identity change; tenant/user and query checks provide
additional guards. Focus departure, outside mouse input, Escape, navigation, logout, and shell shutdown
dismiss/invalidate the popup. Popup mouse presses protect row activation during TextField focus transfer.
Enter with no explicit arrow selection retains the full-search route; a fixed-footer View all results action is always
available for a nonempty query, including loading/no-match/failure states.

Shell initialization gives focus once to the existing My Shale navigation button after installing the initial
route. Manual password sign-in and remembered sign-in share this path. Automatic/programmatic search focus
does not open suggestions; JavaFX keyboard focus visibility opens them on intentional traversal. Clicking
the field opens history even when already focused (including after Escape), and typing continues to refresh
live suggestions. Search remains focus-traversable; no delay or recurring focus correction is used.

## Local history and privacy

`RecentSearchHistory` uses the existing `AppPaths.appSupportDir("Shale")` location and the established
bounded versioned-file/atomic-replacement pattern, rather than introducing a database preference or migration.
Files under `recent-searches/tenant-<id>-user-<id>.json` separate authenticated users and tenants within the
OS user's support directory. This is local convenience data, not an encrypted secret store or a cross-device
preference. Each file contains only schema version and up to ten query strings. It stores no result details.
Queries are trimmed, deduplicated case-insensitively with Locale.ROOT, and ordered by completed use.
Queries longer than 2,048 characters are searched normally but omitted from local history; loads are capped at
100 KB. Missing, unreadable, malformed, or unsupported files yield empty history. Completion/removal/clear
I/O runs off the JavaFX thread. Successful completion writes use captured scope and drain on shell disposal.

## Audit compatibility review

Suggestions expose the existing universal-search identifying summaries (names, case codes, and stable IDs), with no narrative preview. Search and selection preserve
the existing runtime connection, tenant equality/RLS, lifecycle filtering, and admin-only Deleted Case rule.
Existing full search is unaudited at the keystroke/summary level; suggestions and selected-ID availability
checks intentionally retain that treatment. They introduce no domain or administrative mutation.

Direct navigation reuses SceneManager and the established detail-screen/section PHI-read audit paths,
including Task.Detail.Read and Task.Activity.Read. These existing events retain their actor/entity context;
the popup never constructs audit rows. History save/remove/clear changes only local personal convenience
data and intentionally emits no entity-action audit. Query text, result details, SQL, DTOs, row versions,
and exception payloads are not added to logs or audit metadata. No audit schema/allowlist migration is needed.
Focused coverage verifies provider visibility/binding invariants, existing navigation wiring, query-only
history, scope separation, and stale-callback suppression; ordinary read/mutation audit paths are unchanged.

Windows acceptance steps are in [the manual checklist](../testing/universal-search-windows.md).

## Performance diagnosis and measurement

Verified avoidable work in the previous suggestion path: categories were serial with separate runtime connection
borrows and repeated tenant setup/checks. Case bounded overloads still projected semantic dates, status and team;
Contacts performed schema probes, contact-point/credential expressions and classification hydration; Organizations
mapped full entities; Task/Event queries searched narrative fields and returned full card fields. Bounding the
outer result did not remove these costs. The existing latest-only runner already prevented an executor backlog,
but did not cancel an obsolete active statement. This change addresses those code-path costs without a cache,
a longer debounce, parallel database connections, or a tenant-context bypass.

The supplied unrelated TaskDao connection/setup timing (583 ms, SQL 52 ms) is a hypothesis only. It does not
measure suggestions and cannot establish the Windows/Azure bottleneck. No production SQL connection or Windows
GUI is available in this environment; the approximately 500 ms after-debounce goal remains unverified.

With `-DSHALE_PERF_LOGGING=true -DSHALE_LOG_LEVEL=DEBUG`, `PERF search` logs include only fixed phases/providers,
request generations, elapsed milliseconds, and row counts. Under 1,000 ms is DEBUG, 1,000–1,999 ms INFO, and
2,000+ ms WARN using the existing PerformanceLogging thresholds. Failures log ERROR with class or numeric SQL
error code only: exception messages can contain sensitive values and are intentionally omitted. Runtime
connection/setup logs apply to all callers; correlate the `search-suggestions` thread with the runner generation.
A scoped thread-local generation correlates runtime, DAO and UI phases without retaining queries or identities;
non-suggestion runtime work uses request 0. The scope is cleared when each loader completes.

Phases: typing_debounce, executor_queue, connection_acquisition, session_context_setup,
connection_and_session_context (combined provider duration, including monitor wait), tenant_verification,
sql_execution_and_first_response, sql_category (server-measured per-category work), result_mapping,
sql_execution_and_transfer, suggestions_total, fx_dispatch, fx_render, and after_debounce.
Combined phases overlap and must not be added to component phases. `fx_render` measures JavaFX row construction,
not the graphics pulse or pixel presentation; the Windows checklist also checks visible appearance.

Existing repository index scripts establish tenant/ID keys on Cases, Contacts, Organizations, Users, a Users
(ShaleClientId,IsRemoved,is_deleted,name_last,name_first,id) index, and Calendar tenant/StartsAt/CaseId indexes.
These narrow candidate access by tenant or identity; they do not make LOWER/trim/leading-wildcard predicates or
rank sorts into seeks. Cases/Contacts names are nvarchar(max), so a naive name-key index is not valid. Full-text
search would also change literal substring semantics. No live index catalog, plan, logical-read count, or cardinality
has been measured. If sql_category remains slow, inspect the actual plan and SET STATISTICS IO/TIME on a
representative test tenant; check tenant access, rows read vs returned, sort spills and RLS estimates before proposing
covering/computed/full-text indexes. Inspect sys.indexes/sys.index_columns for the six tables instead of assuming
every historical script is deployed. A small TOP alone is not evidence of cheap SQL.

Deployment: updated desktop binaries are required. No SQL migration or index script, API deployment, audit schema
change, or version bump is required by this change. Summary suggestions retain existing unaudited search-read
treatment; detail navigation retains its existing PHI-read audit paths. No business mutation is introduced.
