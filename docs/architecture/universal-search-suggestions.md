# Universal search suggestions and local query history

The desktop shell owns one session-scoped `UniversalSearchPopup`. It anchors beneath the existing search
field, uses Shale's transient popup theme registration and semantic Navigation/Ghost controls, and never
changes the full-results layout. Its specialized `universal-search-choice` class expresses a compact
two-line navigation row with an explicit keyboard selection state; it is not a general button vocabulary.

`SearchService` reuses the existing Case Summary, Contact, Organization, User, Task, and Calendar Event
DAO searches through bounded overloads. Existing full-search entry points retain their matching, limits,
and ordering. `SuggestionBounds` wraps the existing filtered SELECT and ranks exact matches, prefixes,
then other existing matches before applying parameterized SQL Server paging. Names and numeric IDs provide
stable ties. Cases, Contacts, Organizations, Users, Tasks, Calendar Events, and permitted Deleted Cases
are shown in that order, with three rows per category and eighteen overall. No card-date batch or full-results
page is loaded for suggestions. Five-second statement timeouts bound provider work. The selected record is
rechecked by ID through the same search/tenant/lifecycle predicates before calling the existing navigation
handler; a missing record or changed match produces a sanitized message.
Calendar navigation only reuses the retained controller when Calendar is the active route. Async Task
detail navigation also checks the captured session revision/tenant/user before showing detail or errors.

Typing is debounced for 250 ms. A single daemon worker retains one running operation and one replaceable
pending operation. Generation checks suppress both successful and failed late callbacks. The AppState
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

Suggestions expose the existing universal-search identifying summaries (names, case numbers, directory
email/phone, Task case/status, and event case/time), with no narrative preview. Search and selection preserve
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
