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
