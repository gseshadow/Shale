# A.2 Phase 8L final visual consistency and regression audit

Date: 2026-09-22. Authority: production at `c1537d35` plus the Phase 8L corrections in this change.

## Method and classification

The audit reviewed the 8A–8K commits and inventories, every production FXML/CSS resource, and source hits for window/dialog/popup construction, display, inline styling, stylesheet/theme registration, close/hidden handlers, animation timers, scroll ownership, growth constraints, and percentage constraints. **C1** means compliant and unchanged, **C2** a demonstrated narrow correction, **C3** an intentional exception, **C4** platform-owned/excluded, and **C5** a deferred non-A.2 issue.

The inventory fields below use these defaults unless a row overrides them: a main route is launched by `SceneManager`, owned by the primary Stage, modeless, titled by the shell, styled by `app-shell`/`shell-route-page` and `app.css`, registered through the shell Scene, live-themed, disposed on route/session replacement, and owns its state message and route scroll region. A secondary Stage/Dialog is owner-window modal, titled by its launcher/shared shell, `WindowSizingUtil`/the dialog pane owns preferred and minimum size and screen clamping, its content owns vertical scrolling while its shell owns the footer, and `ThemeManager` registration is removed on hiding. All session-aware async surfaces use their controller generation/closed guard and are closed or invalidated by authenticated-session cleanup. “Semantic” accessibility means a meaningful title, textual action labels, and accessible text on icon-only actions.

## Complete production surface inventory

### Main routes

| Surface | Owner / FXML; launcher | Root / style owner | Size, scroll, footer | State and accessibility | Result |
|---|---|---|---|---|---|
| My Shale | `MyShaleController`; `my-shale.fxml`; navigation sidebar | `app-shell shell-route-page my-shale-page`; shell/foundation CSS | primary canvas; bounded board scrolls; no footer | progressive loading/empty/error; named view/filter actions | C2: comma separation restored for runtime classes used by child scroll/card nodes; otherwise unchanged |
| Tasks | My Shale task section and Task navigation entry | My Shale semantic card/board roots | route-owned bounded scrolling | loading/empty plus named board/grid controls | C1 |
| Cases | `CasesController`; `cases.fxml` | `app-shell shell-route-page cases-page`; tables/cards | route-owned content plane, table/card modes | load/empty/error and accessible toolbar | C2: `cases-content-surface` and `shale-table` now resolve as separate runtime classes |
| Case View: Overview, Details, Parties, Dates, Tasks, Requests/Materials, Updates, Links, Calendar | `CaseController`; `case.fxml`; case navigation | `app-shell shell-route-page`; section roots own feature CSS | section navigation; section scroll owners; no route footer | per-section loading/empty/error; semantic headings/actions | C2: updates scroll classes separated; remaining workflow-specific structure preserved |
| Contacts | `ContactsController`; `contacts.fxml` | `app-shell shell-route-page contacts-page` | route table/cards and toolbar | loading/empty/error, search identity | C1 |
| Contact View | `ContactViewController`; `contact.fxml` | `app-shell shell-route-page`; profile cards | route scroll; editor dialogs own actions | unavailable/error states, named edit actions | C1 |
| Organizations | `OrganizationsController`; `organizations.fxml` | `app-shell shell-route-page organizations-page` | route table/cards | loading/empty/error and search identity | C1 |
| Organization View | `OrganizationController`; `organization.fxml` | `app-shell shell-route-page`; profile cards | route scroll | unavailable/error and named edit actions | C1 |
| Team | `TeamController`; `team.fxml` | shell route plus content/data surfaces | route scroll/content plane | loading/empty/error and member actions | C2: three intended runtime classes separated |
| User View / assigned Tasks | `UserController`; `user.fxml` | shell route and user sections | section ScrollPanes, no footer | accessible named controls and inline error | C2: form classes separated and hard-coded error paint replaced by theme semantic error class |
| User Schedule | `UserController`; `user.fxml`; user navigation | user schedule section/shared agenda | section-owned scrolling | empty/error and named calendar actions | C1 |
| Calendar | `CalendarController`; `calendar.fxml` | `content-root shell-transparent calendar-page-root`; calendar CSS | calendar board owns resizing; toolbar wraps | explicit loading/error labels and named layer/view actions | C2: all 13 multi-class attributes now create distinct runtime classes |
| Settings | `SettingsController`; `settings.fxml` | settings scroll/groups; settings CSS | one vertical scroll owner, no horizontal bar | role-aware rows; each Manage/Open action identifies target | C2: scroll, group, and title classes separated; runtime load contract verifies them |
| Reports | `ReportsController`; `reports.fxml` | `app-shell reports-page-root`; reports CSS | result tables/cards own growth | status/error/empty and report action names | C1 |
| Search | `SearchController`; `search.fxml` | route content and `surface-scroll transparent-scroll` | one page ScrollPane, H-bar disabled | result/empty state and query identity | C2: scroll classes separated |
| Login | `LoginController`; `login.fxml`; application startup/logout | login root; login-specific central CSS | primary Stage; form content | validation/error and labeled controls | C3: pre-authentication surface intentionally does not participate in authenticated route/session disposal |
| Main shell/navigation/notification header | `MainController`; `main.fxml`; `SceneManager` | `app-shell`; shell CSS | primary Stage/header/sidebar | named navigation; notification control has accessible identity | C2: bell feature and shell classes separated so both style owners apply |

Route classification includes every production FXML route. Filters, sort, selection, and scroll restoration remain controller-owned; Phase 8L changes only collection syntax/semantic paint and do not change navigation or refresh code.

### Major secondary windows

| Surface | Owner/launcher; root/style/title | Modality and geometry | Theme, lifecycle, state/accessibility | Result |
|---|---|---|---|---|
| New Intake | `NewIntakeController`, `new-intake.fxml`; `SceneManager`; shared secondary shell / `form-surface new-intake-root` | owned `WINDOW_MODAL`; 1180×760, min 680×620, screen-clamped; content scroll + fixed shell footer | Scene registered live; close policy/session cleanup; validation/error and named actions | C1 |
| Settings definition managers (statuses, practice areas, links, team roles, case dates, request fields, contact/organization classifications, dictionary) | launchers → `DefinitionManagementSession/Window`; `secondary-window-shell management-window-root` | owned modal Dialog; 880×680, min 640×480; body list scroll + fixed actions | DialogPane registered; mutation/close guard, disposal callback; loading/empty/error | C1 |
| Task Detail | `TaskDetailDialog`; task/case/user routes; `task-detail-window` | owned modal Stage; screen-aware preferred/min; internal scroll and fixed footer | Scene live-themed/unregistered; stale generation and result contract; semantic actions | C1 |
| New Task | `NewTaskDialog`; route actions; `new-task-window` | owned modal Stage; 720×680, min 560×480; fixed footer | Scene live-themed; closes and releases on result; validation and accessible selectors | C1 |
| New Event wizard | `NewEventWizard`; Calendar | owned modal Stage; 720×680, min 560×480; content scroll/footer | registered Scene; handle supports close/disposal; typed accessible controls | C1 |
| General event create/edit | `NewCalendarEventDialog`; wizard/calendar | owned modal Stage; screen-aware dimensions and internal scroll | registered live theme; async/close guard; validation/error | C1 |
| Case Date occurrence | `CaseDateOccurrenceDialog`; Case/Calendar launchers | owned modal Stage; 720×680, min 560×480; fixed actions | registered Scene; result/cancel cleanup; named title/actions | C1 |
| Material Request create/edit | `CaseMaterialsTabController`; Case Materials | owned modal Stage; request shell preferred/min dimensions, clamped; internal scroll/fixed shell footer | Scene registered; captured tenant/actor/case/generation rejects stale work | C1 |
| Party Add/Edit | `PartyAddWorkflowDialog` / Case party editor; Case | owned modal Dialog; party shell/content scroll/footer | shared Dialog styling/live theme; dirty/result semantics | C1 |
| Contact create/edit | `CreateContactDialog` / `ContactViewController`; route/party workflows | owned modal Stage/Dialog; entity editor shell, min/pref and no H-scroll | registered/shared dialog theme; async close guards and validation | C1 |
| Organization create/edit | `new-organization.fxml`, `OrganizationAggregateEditor`, `EditOrganizationDialog` | owned modal Stage/Dialog; entity editor root, internal scroll/footer | live theme through Scene/shared DialogPane; load/error/stale guards | C1 |
| Case Team editor | `TeamEditorDialog`; Case | owned modal Stage; team window root, screen-aware size/scroll/footer | Scene registered/unregistered on hidden; mutation/dirty guards | C1 |
| User Management | `UserManagementLauncher` → definition-management session | owned modal Dialog; management/user root | theme/disposal/session behavior inherited; loading/empty/error | C1 |
| Add/Edit User; Reset Password | `UserManagementPane`; User Management | owned modal Dialog; user editor/password roots; fixed actions | shared dialog theme; duplicate-submit/close guard; accessible validation | C1 |
| Notification Center | `NotificationCenterDialog`; shell bell | owned modal Stage; notification surface with scroll/actions | Scene registered, session-owned and cleaned; loading/empty/error | C1 |
| Case Overview editor / entity and assigned-user pickers | corresponding dialog classes; Case/workflow launchers | owned modal Stage/Dialog; screen-aware internal scroll | live theme, cancel/result boundaries and stale guards | C1 |

Stages remain appropriate for resizable workflow workspaces, Dialogs for typed/result-modal interactions, Alerts for small standard messages, and Popup/PopupControl for transient anchored content. No type was converted merely for uniformity.

### Shared modal and transient surfaces

| Surface | Owner/launcher/root | Ownership/theme/lifecycle/accessibility | Result |
|---|---|---|---|
| Enhanced Text Area expanded editor | `EnhancedTextArea` → `Dialog<String>`; utility-dialog/rich-text roots | invoking Window owner; DialogPane registered live; result/cancel contract; multiline Enter retained | C1 |
| Information/warning/error, ordinary/destructive/discard/recovery confirmations | `AppDialogs`; `shale-utility-dialog` plus semantic variant | caller owner; window-modal; shared title/action owner; theme unregister on hidden; Escape and explicit semantic actions | C1 |
| Typed choice/input dialogs | `AppDialogs` and narrow domain callers | caller owner; typed result boundary; platform control body inside Shale dialog chrome | C3: native JavaFX typed controls intentionally retained; Shale owns dialog chrome/actions |
| Cases context menu | `CasesController`; `shale-context-menu cases-context-menu` | stable selected case captured; hidden clears target; transient support theme registration | C1 |
| Filter popup | `ShaleFilterMenu`; semantic popup root | stable anchor, screen coordinates, hidden theme unregister | C1 |
| Rich text/spellcheck menu | `EnhancedTextArea` / `RichTextExpandedEditor`; rich-text context root | editor target captured; keyboard/context invocation; hidden clears target and unregisters | C1 |
| Task hover popup (all `TaskCard` variants) | `TaskCard`; `task-hover-popup` | stable task snapshot; card/popup hover bridge, edge clamping, no max-dismiss timer/card resize; hidden removes listeners and target | C1 |

### Platform-owned exclusions (C4)

OS file/directory choosers and native menus; JavaFX ComboBox, ChoiceBox, DatePicker and ColorPicker popup skins; native window decorations; and other OS/JavaFX popup internals not rooted or skinned by Shale are excluded. Shale still owns the launching action, accessible label, and surrounding form/dialog theme. Global Tooltip redesign and platform popup replacement remain explicitly outside A.2.

## CSS import, scope, and semantic-token audit

`css/app.css` is the single production entry point. Its deterministic order is colors/surfaces/indicators and shared controls first, feature foundations next, transient/dialog rules after their primitives, and shell last. All 23 imports resolve; there are no nested imports, cycles, or duplicate import paths. Theme stylesheets are attached by `ThemeManager`, not imported by features. Central foundation files intentionally own global JavaFX selectors; feature window, management, calendar, reports, settings, dialog, and transient rules are rooted under semantic feature classes. No obsolete production stylesheet or duplicate attachment was demonstrated.

Light and Dark expose the same `-shale-color-*` contract (enforced by `ThemeResourceContractTest`). Referenced semantic theme paint resolves in both themes. Hex/RGB findings fall into: foundation/theme token definitions; intentional application gradient/foundation fallback paint; database/computed entity colors (status, priority, practice area, case date/contact/organization/team-role/user classifications); Java color serialization; and tests. One unjustified page-level value was found: `user.fxml` owned `#b42318`; it now uses `shale-error-message` / `-shale-color-text-danger`. No database-authoritative color was changed.

## FXML/runtime-class audit

All production FXML parses, contains no duplicate `fx:id`, and now uses comma-separated JavaFX collection syntax. The audit found 28 space-delimited attributes across shell, Team, Search, Calendar, Settings, Cases, Case View, and User View; JavaFX interpreted each as one combined class, preventing intended selectors from matching. They were narrowly corrected without changing class names or hierarchy. The resource validator now fails future whitespace-only multi-class attributes. Existing runtime FXML tests plus the Settings runtime load assertion cover representative separated class lists; phase presentation contracts cover the remaining critical resources.

No detached content, retired-container additions, or duplicate IDs were demonstrated. Selector-to-node review found no release-blocking orphan or ownerless production class; generic foundation classes intentionally serve runtime-created nodes and therefore need not appear in FXML.

## Accessibility, Light/Dark, geometry, lifecycle, and behavior results

Static and automated contracts confirm semantic button purposes, named management actions, one canonical title owner for migrated windows, H-bar suppression on page/form scroll owners, visible shared focus tokens, semantic error states, and live ThemeManager propagation. Corrected runtime classes restore Calendar filters/header hierarchy, Settings cards/headings, route scroll treatments, table surfaces, the shell bell, and User form/error styling in both themes.

Source inspection confirmed migrated windows retain owner/modality, preferred/minimum sizing, screen-aware clamping, stable footer ownership, registration/unregistration, close/hidden cleanup, stale generation checks, duplicate-submit guards, and logout/session invalidation. Popup support retains stable targets and dismissal cleanup. No changes were made to SQL, services, transactions, RowVer, tenant/RLS authority, audit, notifications, timeline publication, navigation, partial refresh, or mutation/result boundaries.

## Automated regression matrix

| Area | Contracts |
|---|---|
| Main routes | shell/style contracts; route Phase presentation tests; navigation/section selection; loading and partial-refresh regression tests; FXML resource validator |
| Workflow windows | Phase 8A–8I presentation contracts; title/owner/sizing/footer tests; Theme lifecycle integration; close/session tests; persistence-boundary tests |
| Shared dialogs/popups | Phase 8J/8K contracts; result/owner/theme tests; target identity/hover/cleanup tests; Enhanced Text Area tests |
| Runtime CSS | Theme resource equality/resolution; shell and toolbar runtime tests; New Task footer runtime; resource parser/import validator; runtime FXML load tests |

## Manual walkthrough and deferred backlog

A database-enabled graphical walkthrough could not be executed in the headless audit environment: no display server or configured production tenant/database session is available. Consequently, real-window Light/Dark switching, narrow/short resize, long data, rapid activation, navigation during actual database work, logout with open native windows, TaskCard hover variants, and renderer/console observation remain runtime-only verification. Automated JavaFX tests that require a display self-skip for this reason. This limitation is not evidence of a defect, but the manual completion criterion remains unverified here.

Deferred non-A.2 findings (C5): none demonstrated. Legacy compact source formatting and domain functionality encountered by searches were not changed. Platform-owned popup skin replacement, Tooltip redesign, and native chooser replacement remain exclusions rather than backlog defects.

## Corrections and assessment

Exact corrections: separate 28 malformed FXML class lists; replace one User View hard-coded error paint with the shared semantic error class; and make the UI resource validator enforce collection syntax. No production Java, persistence, schema, or SQL changed. Automated audit and full-suite results are recorded in the delivery/commit. Subject to the database-enabled manual walkthrough above, the source, CSS, FXML, lifecycle, accessibility, and regression audit finds A.2 structurally ready for closeout; it must not be represented as fully runtime-verified until that walkthrough is performed.
