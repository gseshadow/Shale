# Shale A.2 Phase 6 supporting-components inventory

This inventory records the production state reviewed before the Phase 6 unification changes. Theme paint for every
listed A.2 component comes from `css/theme/light.css` and `css/theme/dark.css`, registered by the application-owned
`ThemeManager`; geometry and component states remain in the narrow foundation owner noted below.

| # | Surface | Production and style owner | Existing vocabulary and theme owner | Interaction and accessibility | Finding before Phase 6 |
|---|---|---|---|---|---|
| 1 | Primary Link container | `case.fxml` (`ovPrimaryLinkSection`); `content-components.css` | `shale-section-card`, `primary-link-section`; semantic theme surfaces/borders | Display region announced through its visible heading | Compliant; already fills the Overview column and uses the Phase 2D section surface. |
| 2 | Primary Link identity/content | `CaseController.renderOverviewPrimaryLinkState`; `CaseLinkCardFactory`; `cards.css` | Shared compact entity card, link-type pill, primary status pill | Whole card opens the safe URL with mouse, Enter, or Space and has an accessible name | Compliant; authoritative type color and shared linked-entity identity remain owned by the factory. |
| 3 | Primary Link actions | `CaseLinkCardFactory.buildCompactCard` | Shared ghost small action | Explicit Edit button; keyboard-native button behavior | Compliant; action remains isolated from card activation and mutation behavior is unchanged. |
| 4 | Updates section header | `case.fxml`; `content-components.css` | `shale-section-title`, `shale-update-heading` | Visible text heading | Compliant. |
| 5 | Add Case Update composer/action | `case.fxml`; `EnhancedTextArea`; `content-components.css` | Shared narrative editor in `shale-update-composer` | Explicit expand workflow; isolated draft Apply/Cancel contract | Compliant presentation and workflow; submission-state feedback needed to disable the draft while saving. |
| 6 | Submit action | `CaseController.onSubmitCaseUpdateInternal` / `saveNewCaseUpdate`; `buttons.css` | `ControlStyles` primary standard plus `shale-composer-action` | Native button activation | Immediate authoritative persistence is compliant; an explicit in-flight guard and deterministic focus restoration were missing. |
| 7 | Updates search | `case.fxml`; `CaseController.initialize`; `forms.css` and a duplicate local selector in `content-components.css` | `ControlStyles.formControl`; intended shared `shale-search-field` | Text field has accessible text and live local filtering | Gap: local one-off search styling, no established search icon, and no keyboard-accessible clear action. |
| 8 | Update collection host | `case.fxml` (`caseUpdatesScrollPane`, `caseUpdatesFeedBox`); `content-components.css` | Transparent bounded scroll/feed | Feed is reached through ordinary focus traversal | Compliant; one bounded vertical scroll owner with fit-to-width and no horizontal bar. |
| 9 | Update cards | `CaseController.createCaseUpdateCardInternal`; `content-components.css` | `shale-update-card` and shared narrative plain-text projection | Only explicit Edit control is actionable | Gap: a hover border was applied to the otherwise non-actionable card region. |
| 10 | Update author avatars | `CaseController.createCaseUpdateCardInternal`; `content-components.css` | Shared `shale-avatar shale-avatar-compact` neutral fallback | Accessible author-avatar description | Compliant for the available DTO contract; Case Updates carry no authoritative user color, so the stable theme fallback is correct and persistence/query contracts remain untouched. |
| 11 | Update metadata | `CaseController.buildCaseUpdateMetadata`; `content-components.css` | `shale-update-timestamp` with semantic muted text | Created/edited meaning is available as label text | Compliant; compact created timestamp and meaningful edited timestamp are preserved. |
| 12 | Edit action | `CaseController.createCaseUpdateCardInternal` / `startEditingCaseUpdate` | Shared ghost small semantic button and explicit edit class | Creator-only; accessible name, tooltip, native keyboard activation and focus | Compliant. |
| 13 | Overflow/secondary actions | Primary Link factory and update-card builder | Existing explicit Open/Edit actions; no overflow menu exists | Only available authorized actions are exposed | Compliant; no new overflow menu is warranted. |
| 14 | Loading state | Primary Link render methods and Updates loader; `content-components.css` | Shared loading-message vocabulary | Concise visible loading text | Presentation is compliant; Phase 6 aligns Updates to the shared state class without changing lifecycle. |
| 15 | Empty state | Primary Link and Updates render methods; `content-components.css` | Shared empty-message vocabulary | Distinct explanatory text | Primary Link is compliant; Updates used a local class and needed alignment to the shared owner. |
| 16 | Filtered-empty state | `CaseController.applyCaseUpdateFilterInternal` | Previously reused the local Updates empty class | Accessible text distinguished it from true empty | Gap: visible copy was too generic and there was no direct clear-search action. |
| 17 | Error state | Primary Link and Updates failure render methods; `content-components.css` | Shared semantic error paint | Sanitized user-facing text; internal exception is logged | Compliant sanitization; Phase 6 aligns Updates to the shared state class while preserving the safe refresh lifecycle (no unsafe ad-hoc retry). |

## Scope boundary

The inventory found no reason to change Primary Link semantics, Case Link card construction, database/service/DAO
contracts, authorization, live-update subscriptions, update ordering, timestamps, medical-record safeguards, or the
Case Overview/page scrolling model. Phase 6 therefore limits production changes to the demonstrated search,
submission-state/focus, shared-state-class, and non-actionable-card-hover gaps.
