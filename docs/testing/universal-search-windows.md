# Universal search — manual Windows acceptance

Status: **NOT RUN** in this Linux environment. Run against an authenticated Windows desktop with seeded
duplicate names, multiple supported entity types, and two users/tenants where available.

Startup focus checklist (not run here):

- Cold launch: reach the initial My Shale page; search is unfocused, dropdown closed, and My Shale navigation
  has initial focus. Background initialization must not reopen suggestions or steal later user focus.
- Manual login: sign out and sign in with credentials; verify the same initial focus and closed dropdown.
- Remembered login: enable Stay logged in, exit, and relaunch; after restoration verify the same state.
- Clicking search: an empty field shows recent searches. Press Escape, then click the still-focused field;
  recent searches reopen. Clicking from another control also works.
- Tabbing into search: Tab/Shift+Tab remain usable; traversal into empty search opens history. Verify any
  configured search shortcut, if present, retains its intentional search behavior.
- Typing: type a known query and verify live suggestions. Check Up/Down + Enter direct navigation, unselected
  Enter full search, history recording, Escape, and ordinary/back navigation.

- Mouse: type a known name; confirm grouped Cases, Contacts, Organizations, then other supported types.
  Check exact-before-prefix ordering, distinguishing metadata/IDs, scrolling, row clicks, View all results,
  and dismissal on outside click and ordinary/back navigation. Click a row as field focus transfers.
- Keyboard: Down/Up explicitly select rows; Enter opens the selected record; Escape dismisses.
  Enter before selecting anything opens the existing full-results page. Check Tab access to history
  removal/Clear history, and View all results.
- History: submit full searches and open suggestions; typing alone must add nothing. Focus an empty bar,
  select a recent query, remove one entry, clear the list, and restart Shale. Confirm the latest ten distinct
  queries survive restart, with trimmed readable text and case-insensitive deduplication.
- Appearance: repeat in Light and Dark; verify readable names/metadata, distinct hover and selected states,
  bounded popup scrolling, long-name ellipsis, small-window sizing, and a monitor-edge placement.
- Rapid input: type, replace, erase, and refocus quickly while queries are slow. Only the current query may
  appear; typing stays responsive. Navigate or press Escape before completion and ensure no late reopening.
- Failure/fallback: try a no-match query and unavailable database; verify sanitized status and working Enter/
  View all results fallback. Remove or rename a suggested record before clicking it; confirm a friendly
  unavailable/no-longer-matching message rather than a stale detail view.
- Session isolation: while history or suggestions are open/in flight, log out, sign in as another user,
  and switch tenant. Old dropdown/query state must disappear; each scope shows only its own history.
  Return to the first scope and confirm its history remains. Check ordinary vs admin Deleted Case visibility.

Automated Maven and resource checks are reported separately in the PR; they do not constitute Windows
acceptance or live SQL Server validation.

## Windows/Azure timing checklist

Status: **NOT RUN** here. Use the updated desktop against production Azure SQL with the ordinary authenticated
runtime principal and existing permissions. Enable `-DSHALE_PERF_LOGGING=true -DSHALE_LOG_LEVEL=DEBUG`; keep
Hikari DEBUG off unless connection_acquisition indicates a pool problem. Do not record query text or identifying
row values in a shared measurement report.

1. First search after cold startup/sign-in: choose an existing name and note the executor_queue generation. Record
   typing_debounce, after_debounce, connection_acquisition, session_context_setup, tenant_verification, per-category
   sql_category times/counts, mapping, FX dispatch and row construction. Check visible dropdown appearance too.
   Record cold-start separately from warm results; do not infer suggestion latency from unrelated DAO startup logs.
2. Subsequent searches: repeat at least 20 representative exact names, prefixes, CaseNumber/OfficePrinterCode/Id
   queries, broad common names and no-match queries. Include Contacts, Organizations and other categories, and
   ordinary/admin sessions. Report warm median/p95/max after_debounce and row counts; compare with roughly 500 ms
   after the unchanged 250 ms debounce. Preserve exact-before-prefix ordering and three/eighteen bounds. Check
   narrative/email/phone-only matches still work in the broader full results where previously supported.
3. Rapid typing: type/edit every 50–100 ms, then pause; also replace text while a database request is already active.
   The worker must run only the newest pending query, cancel the old statement, and show only the latest response.
   No old success/error may reopen the popup after Escape/navigation/logout or an away-and-back identity switch.
   Check typing stays responsive and compare newest after_debounce/queue wait to ordinary warm searches.
4. Diagnose remaining delay: high connection_acquisition suggests pool/login/network cost; high session_context_setup
   suggests context round trips; high tenant_verification suggests metadata/network cost; high sql_category identifies
   the category needing actual-plan/IO inspection. High client SQL transfer with low server times suggests transport
   or driver buffering; high executor_queue suggests slow cancellation/acquisition; high fx_dispatch suggests a busy
   JavaFX thread. fx_render is row construction, so compare it with actual visible appearance to catch pulse delays.
5. Disable diagnostic logging after collection. Report Windows, desktop/JDK/JDBC versions and coarse network conditions,
   first/warm/rapid measurements, sanitized phase logs and any cancellation failures. Never include search text,
   credentials, SQL parameter values or record details. Confirm Enter and View all results still open full search.

## SQL declaration regression (#1829)

SQL Server error 2717 was caused by `@prefix nvarchar(4096)` and `@contains nvarchar(4096)` in the client batch.
Bounded `nvarchar(n)` permits at most 4,000 UTF-16 code units; the declarations fail for short input too.
Both are now `nvarchar(4000)`. The existing guard allows at most 3,998 escaped code units, leaving room for
both contains wildcards and respecting LIKE's 8,000-byte limit. Exact text remains `nvarchar(2048)`;
display table columns use legal `nvarchar(max)` and identifier conversion uses `nvarchar(20)`.
All three string parameters use `setNString`, including when `sendStringParametersAsUnicode=false`.
No search text or exception message is added to diagnostic logs.

For read-only runtime verification, explicitly configure an authorized SQL Server test database with the Shale
search tables and runtime principal. Set `SHALE_SUGGESTION_TEST_JDBC_URL`, optional
`SHALE_SUGGESTION_TEST_USERNAME` / `SHALE_SUGGESTION_TEST_PASSWORD`, and positive
`SHALE_SUGGESTION_TEST_TENANT_ID` / `SHALE_SUGGESTION_TEST_USER_ID` through your secure local environment.
Keep credentials out of command lines, URLs and reports. Use the ordinary runtime account to test RLS as well.

```bash
mvn -pl shale-data -am -Dtest=SuggestionDaoTest,SuggestionDaoSqlServerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`SuggestionDaoSqlServerTest` uses the actual Microsoft driver, reproduces 2717 with short input for each old
declaration, and executes the full production DAO batch with Unicode/metacharacters and the maximum escaped
pattern. Without a configured connection it skips; that is not runtime SQL validation. It creates no schema
or records. Synthetic local SQL Server execution verifies syntax and driver compatibility, not production
Azure plans, RLS configuration or Windows latency. Repeat the timing checklist above on the updated desktop.

Local verification on disposable SQL Server 2022 with Microsoft JDBC `12.6.1.jre11` reproduced 2717 for both
old declarations and successfully executed the corrected full batch for both input cases. Production Azure SQL
and Windows performance measurements remain outstanding.

Deployment requires updated desktop binaries only. No SQL migration/index script or API/server deployment is required.
