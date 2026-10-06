# Settings Phase 8B management inventory

This inventory records the production surfaces reached from the Settings directory. It is a presentation
migration inventory, not a new service or mutation contract. All definition managers remain lazy: their pane is
constructed only after its Settings action passes the existing authorization/context guard.

## Shared window contract

Eleven managers use `DefinitionManagementSession` and `DefinitionManagementWindow`. They remain JavaFX `Dialog`
windows owned by the launching window with `WINDOW_MODAL` modality. `AppDialogs.applySecondaryDialogShell`
owns the one canonical title and registers the `DialogPane` weakly with `ThemeManager`; the content therefore
inherits the authenticated theme immediately and follows later Light/Dark changes. The shared shell is 900 by
700 preferred, 680 by 520 minimum, constrained to the owner's current screen, and keeps a stable Done footer.
Close requests are rejected during a mutation, disposal invalidates pending generations, and completion reports
whether a committed change occurred. Managers keep their existing external executor and load on construction.

The shared A.2 vocabulary lives in `foundation/management.css`: window root, help/header, scroll host, footer,
cards, selected collection rows, subdued historical rows, feedback states, and Audit Log toolbar/filter surfaces.
Feature panes retain their own authoritative state, editors, commands, confirmation handlers, and database colors.

## Production manager inventory

| Settings entry | Launcher and implementation | Collection / filters / editor / feedback | Actions and authorization | Compliance result |
| --- | --- | --- | --- | --- |
| Custom Dictionary | `SettingsController.onManageCustomDictionary` → `CustomDictionaryManagementLauncher` → `CustomDictionaryManagementPane` | `ListView`; word search; inline add field; local status label | Add, Remove, Refresh; authenticated personal scope | Existing modal/lazy lifecycle and normalization retained; shared shell and state styling applied. |
| Case Statuses | `onManageCaseStatuses` → `CaseStatusManagementLauncher` → `CaseStatusManagementPane` | card `VBox`; no search; modal editor; pane status | Add, Customize/Edit, reorder, Remove, Restore, Refresh; administrator gate | Overlay/global read-only behavior, colors, historical rows, and confirmations unchanged. |
| Practice Areas | `onManagePracticeAreas` → `PracticeAreaManagementLauncher` → `PracticeAreaManagementPane` | card `VBox`; no search; modal editor; pane status | Add, Edit, Deactivate, Refresh; administrator gate | Existing definition ownership, sort, colors, and historical compatibility unchanged. |
| Link Types | `onManageLinkTypes` → `LinkTypeManagementLauncher` → `LinkTypeManagementPane` | effective-definition cards; no search; modal editor; inline status | Add, Edit/Customize, Activate, Deactivate, Remove Custom, Reset Override, Refresh; administrator gate | Existing system-key overlay resolution, RowVer commands, audit/service transaction, and live refresh retained. |
| Case Team Roles | `onManageCaseTeamRoles` → `CaseTeamRoleManagementLauncher` → `CaseTeamRoleAdminPane` | definition cards; no search; modal editor; inline status | Add, Edit, Activate, Deactivate, Remove, Restore, Reset Override, Refresh; administrator gate | Existing ordering, color, overlay, RowVer, historical labels, and mutation handlers retained. |
| Case Dates | `onManageCaseDateTypes` → `CaseDateTypeManagementLauncher` → `CaseDateTypeManagementPane` | definition cards plus independent ordered Case Card and default Case Overview presentation editors; inline status | Add, Edit, Activate, Deactivate, Remove, Reset Override, reorder/save/reload presentation lists; administrator gate | SystemKey/category/color/SupportsTime, overlay, RowVer, validation, historical presentation selections, explicit-empty defaults, and audited service-port mutations retained. Overview defaults apply only without a per-case override. Protected mappings remain inline. |
| Request Fields | `onManageRequestFields` → `RequestDefinitionManagementLauncher` → `RequestDefinitionAdminPane` | category tabs/cards; category-local editor dialogs and status | category switch, Add, Edit/Save, Activate, Deactivate, Remove Custom, Reset Override, Refresh; administrator gate | All three families, category validation, ordering/colors, RowVer, audits, and request behavior unchanged. |
| Contact Classifications | `onManageContactClassifications` → `ContactClassificationManagementLauncher` → `ContactClassificationAdminPane` | family tabs/cards; optional historical toggle; modal editor; inline state feedback | Add, Edit/Save, Activate, Deactivate, Remove, Restore/Restore Override, Reset to Global, Refresh; administrator gate | All three families, abbreviation/order, overlays, assignment compatibility, RowVer, and database colors retained. |
| Organization Types | `onManageOrganizationTypes` → `OrganizationTypeManagementLauncher` → `OrganizationTypeAdminPane` | active/inactive/removed card regions; modal editor; inline status | Add, Edit/Save, Activate, Deactivate, Remove, Restore, Refresh; administrator gate | SystemKey, description, color, order, overlay ownership, RowVer, and legacy ID compatibility retained. |
| User Management | `onManageUsers` → `UserManagementLauncher` → `UserManagementPane` | independently scrolling `TableView`; search and inactive filter; add/edit/reset dialogs; footer status | Add User, Edit/Save Changes, Deactivate/Reactivate, Remove from Tenant, Reset Password, Cancel, Refresh; administrator/context gate | Fixed viewport mode, tenant isolation, password contract, RowVer updates, and confirmations unchanged. |
| Firm-wide Roles | `onManageFirmWideRoles` → `FirmWideRoleManagementLauncher` → `FirmWideRoleAdminPane` | definition cards; active/inactive/deleted status; modal name editor; inline state feedback | Add, Rename, Activate, Deactivate, Delete, Refresh; administrator/context gate | Protected Administrator/Attorney membership remains flag-owned; tenant-defined lifecycle uses UserServicePort, RowVer, tenant/actor authorization, and existing transactional entity-action audits. Assignment history remains intact. |
| Audit Log | `onViewAuditLog` → `SceneManager.showAuditLogViewer` → `audit-log-viewer.fxml` / `AuditLogViewerController` | routed read-only `TableView`; mode and six existing filters; status near toolbar | Apply, Clear Filters; administrator gate | Intentionally remains an embedded routed pane rather than a modal. Query, tenant scope, newest-first ordering, 500-row limit, and read-only behavior remain unchanged; duplicate content title removed because the route shell owns it. |
| My Sessions | `SettingsController` personal section → `UserSessionManagementClient` | authenticated user's compact session cards; factual Active/Expired/Revoked state; server current marker; loading/empty/error states | Refresh and confirmed self-revocation; every authenticated role | User-reported Windows/Eclipse acceptance confirms ordinary-user and administrator access, self-only results, the current marker, and current-session revocation locking then returning to sign-in. Active means unexpired and unrevoked, not process presence. |
| Sessions | `onManageAdminSessions` → `AdminSessionsLauncher` → `AdminSessionsPane` | compact session cards; user/client/active/since server filters; Previous/Next bounded paging; loading/empty/error/authorization states | Refresh, Clear filters, Revoke session; administrator/context gate plus authoritative server gate | Phase 9 card/status/time language is reused; current session points to ordinary logout; reads/revokes use only Phase 8A server APIs and their existing read/mutation audits. |

Appearance, Notification Preferences, and Protected Case Date Mappings remain inline Settings content and are
not constructed or moved by this phase. No DAO, service, schema, audit-write, transaction, authorization, overlay
winner, mutation payload, or assignment workflow is changed.


## 2026-10-02 session acceptance record

The latest Windows/Eclipse results are user-reported acceptance, not checks executed by this documentation run.
They confirm My Sessions works as expected for ordinary users and administrators, shows only the authenticated
user's sessions, marks the current session, and makes current-session self-revocation lock the app and return to
sign-in. Administrators retain the separate tenant-wide Sessions surface. Earlier local Maven runs passed as
reported by the user; “everything working as expected” is not recorded as a new test result.

No manual cross-tenant or crafted-request security exercise is claimed. A clean installed production launch without
an API override is **NOT RUN**, and intermittent enrollment `REQUEST_TIMEOUT` remains open.

## 2026-10-05 User Management performance and dark-theme correction

Investigation traced the initial popup flow from `SettingsController` through `UserManagementLauncher` and
`UserManagementPane` to `UserServiceAdapter`/`UserDao`. Construction itself did not refresh: the launcher shows
the window and invokes `pane.open()` exactly once, and observable table changes were already marshalled with
`Platform.runLater`. The delay was backend N+1 enrichment: after the user and definition reads, the pane invoked
the single-user role-assignment service once per row. Each invocation acquired another connection and repeated
session/admin and tenant-user checks before its assignment query. For 20 users, this produced the two list reads
plus 20 assignment service/connection sequences. The grid did not load session data or user-detail DTOs.

The grid now uses one bulk tenant-assignment service/DAO boundary. Initial hydration is three bounded reads—role
definitions, all assignment history, and the existing management user projection—regardless of displayed user
count, and remains entirely on the Settings background executor until the final JavaFX mutation. Focused contract
coverage prevents the pane from reverting to the per-user method and verifies the bulk SQL remains administrator
authorized, tenant-qualified, nonremoved-membership constrained, and a single assignment query. Existing Add,
Edit, lifecycle, password, remove, refresh, inactive toggle, local search, selection-button, RowVer, role-history,
and audit paths are unchanged; the existing presentation/action tests continue to cover selection behavior.

The dark-theme contrast defect was shared rather than User Management-specific: `shale-table` painted hard-coded
light header/body/alternate/hover surfaces while its text correctly inherited dark-theme foreground tokens. The
shared table foundation now pairs semantic card, section, embedded, hover, selection, divider, and selected-border
tokens with semantic text tokens, preserving coherent light and dark palettes for every shared table. User name
chips also now derive a readable foreground from their authoritative database color through the shared `UserCard`
component. No global text token was changed because those tokens were correct on intentionally dark surfaces.
Static theme-contract coverage protects the shared token pairing; rendered visual inspection remains part of the
desktop acceptance pass.
