# Case Updates desktop workspace

## Inspection and authority

The dedicated Case View section and the quick-access right rail use the existing
`dbo.CaseUpdates` store. `NoteText` is legacy plain text or Shale's limited Markdown;
there is no duplicate history, projection store, API endpoint, or schema migration.
The verified schema is documented in [database-schema.md](database-schema.md#dbocaseupdates).
The existing soft-delete migration is `docs/sql/2026-03-27_caseupdates_soft_delete_phase1.sql`.

Before this change, `CaseController` loaded the entire active history and applied a
local search. `CaseDao.addCaseNote` created notes, touched `Cases.UpdatedAt`, and used
PHI auditing. `updateCaseNote` restricted writes by creator, case, tenant, and active
state, but its legacy overload did not consume the existing `RowVersion` column.
Soft deletion records `IsDeleted`, `DeletedAt`, and `DeletedByUserId`; this feature
adds no delete/restore action and leaves those existing semantics unchanged.

`CaseServicePort` -> `CaseServiceAdapter` -> production `DaoCaseGateway` -> `CaseDao`
is the read/write boundary for both desktop entry points. New port/gateway defaults
throw actionable unsupported-operation exceptions. Tests exercise the production
adapter/gateway using JDBC doubles, including the actual SQL and parameter binding.
Legacy unpaged readers remain for existing document/API consumers; the Case View
no longer invokes them. DTO legacy construction and bean serialization remain
compatible: desktop concurrency/lifecycle accessors are deliberately non-bean methods.

## Navigation and presentation

`AppSectionTabs` owns the new **Case Updates** section and `CASE_UPDATES` restoration
key. It uses the existing exclusive center-root lifecycle. The rail is hidden only
while the full-width workspace is selected; sibling sections retain the same rail,
quick composer, Submit, and Edit. **View All Updates** selects the current case's tab.

`CaseUpdatesWorkspaceController` composes shared content/section surfaces, form
controls, semantic actions, `UserSelectionField`/`UserSelector`, wrapping filter rows,
and the existing update cards. Cards show author, creation/edit time, a syntax-free
preview, full View, and creator-only Edit. Deleted records are labelled and read-only.
Shared semantic paint supplies Light/Dark appearance; no feature-specific colors,
gradients, or duplicate card factory are introduced.

The workspace defaults to newest active updates. Search is debounced 350ms and matches
literal stored update text case-insensitively according to database comparison rules.
Dates filter **creation** time, with an inclusive through date expressed as next
midnight. The query carries the desktop display zone; local day boundaries are converted
to UTC before binding, including daylight-saving transitions. Author filtering uses immutable user identity, including historical authors
with unavailable display names. A distinct metadata query supplies the picker without
loading note contents. Clear resets text, dates, author, lifecycle, sort, and page.
Loading, successful empty, filtered empty, validation, and recoverable load failure
states are distinct. Previous/Next controls never append unbounded card history.

## Paging, permissions, and concurrency

`CaseUpdateQuery` closes sort/lifecycle inputs, validates identities, date order,
search length, and page size (1–100). Workspace pages request 50 rows; the rail requests
25. SQL fetches one extra row solely to discover Next. Seek cursors use sort timestamp
and unique update ID in the same direction; recently edited uses `UpdatedAt` with
`CreatedAt` fallback. Missing timestamps use SQL datetime2 year 1. Literal `%`, `_`,
and `[` are escaped as SQL Server bracket expressions, never interpolated SQL.

Every new read verifies runtime `SESSION_CONTEXT` tenant equality, current same-tenant
actor availability, and case ownership. Queries explicitly qualify case and tenant,
and author joins qualify tenant. RLS/session initialization remain authoritative.
Active history is available to existing case readers. Deleted/all history is a new,
conservative administrative read: it reuses the existing active `Users.is_admin`
check at the DAO boundary and is absent from ordinary-user controls. It grants no
mutation capability. Authors known only from deleted notes are likewise admin-only.
No authentication, role-retirement, or permission-catalog redesign is included.

Both editing entry points use `UpdateCaseNoteCommand` with a defensive copy of the
existing 8-byte `RowVersion`. SQL compares update ID, case, tenant, creator, active
state, and version; an update lock keeps the audit baseline coherent. A zero-row
match returns a conflict/unavailable outcome and never retries or overwrites. The
legacy overload remains for compatibility; desktop Case Updates uses the versioned
path. Successful edits touch the parent case's existing last-updated timestamp.

Keyset paging gives stable tie ordering and avoids offsets shifting under inserts.
It is not a historical snapshot: an edit can change an update's sort position.
Local/remote invalidation resets the workspace to page one while preserving filters.
Existing indexes narrow by case; substring search on `nvarchar(max)` and computed
recent-edit ordering still require database work within that case. No unmeasured
full-text/index migration is required by this slice.

## Shared editor and synchronization

`EnhancedTextArea` retains its shared RichTextFX implementation. Expanded dialogs
now target 900 × 650, are resizable through the existing secondary-window resize
helper, and use `WindowSizingUtil` to fit the screen. The editor grows inside the
content region while the affirmative/Cancel footer stays outside it. Word wrapping,
formatting, offline spelling, and multiline Enter remain; Ctrl+Enter explicitly saves.

`openSavingEditor` adds asynchronous Save for Case Update create/edit. Blank text
stays in the dialog. In-flight Save rejects duplicate activation and close; failure
or conflict retains the draft. Cancel/X/Escape asks Keep Editing/Discard for changed
drafts and performs no mutation. The existing expansion API still Applies only to
its isolated inline form draft. Its larger dimensions and dirty-close protection
also apply to other existing shared-editor callers; persistence callbacks are unchanged.

`CaseController.persistCaseUpdate` is the shared create/edit mutation coordinator.
Quick Submit clears its draft only after successful persistence. The medical-record
keyword safeguard still runs only after successful creation. Database work runs on
the existing style of two-worker daemon executor; FX callbacks check case/session and
request generations. A stale page, case switch, detached view, or changed identity
cannot apply an old response. Remote invalidations do not repaint open editor drafts.

Successful mutations publish the existing PHI-free `caseUpdateAdded` invalidation
through `UiRuntimeBridge`, then invalidate/reload local rail/workspace state and
author metadata. Existing tenant/case filtering and subscription lifecycle handle
remote invalidations. Reconnect reloads authoritatively. Publication or refresh cannot
undo a committed save, and refresh errors remain explicitly recoverable.

## Audit compatibility

Opening the dedicated tab uses the established section-level
`PhiReadAuditService` with `Case.Updates.Read`, screen `Case.Updates`, and the parent
Case identity. It runs off the FX thread and retains existing tenant/actor/session
and short-window deduplication behavior. Searching, paging, choosing filters, preview
rendering, and live invalidation add no per-row audit events: they stay within the
same audited section intent. Existing Case Overview read auditing covers the rail.

Create/edit use the existing registered PHI field `CaseUpdates.NoteText`, object type
3, actor and update identity. Their audit append now uses the same connection as the
business mutation before commit and fails closed; audit failure rolls back the note
and parent timestamp. The established PHI audit format remains authoritative. No
note text, search term, author label, RowVersion, or exception is added to live
messages, entity-action metadata, or user-visible error copy. Conflicts/cancel produce
no successful mutation audit. This uses the existing audit schema; no entity-action
Case Update type or ad-hoc timeline event is invented. Legacy soft-delete audit
modernization is outside this task because no delete mutation is introduced.

See [desktop acceptance and deployment](../docs/testing/case-updates-workspace.md).
