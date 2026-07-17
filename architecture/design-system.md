# Shale Design System Architecture

## Purpose

The Shale Design System defines the long-term visual language for Shale. It is the canonical visual reference for the JavaFX desktop application and the future React web application.

This document describes design intent, composition rules, and extension principles. It is not a CSS reference, a JavaFX implementation guide, or a React component API. The JavaFX desktop application remains the reference implementation, and future clients should mirror the same visual language rather than invent parallel styling systems.

---

## Design Philosophy

Shale has an established visual identity. Future UI work should preserve and extend that identity.

Shale is not a Material Design application. Do not introduce Material conventions, palettes, elevation rules, component shapes, or interaction patterns just because they are familiar.

Shale is not intended to imitate Windows controls. Native-looking controls may be used where technically necessary, but Shale screens should not drift toward generic desktop defaults.

The design language is defined by Shale's existing visual system:

* a dark teal-and-blue application shell,
* translucent layered surfaces,
* light content planes,
* rounded cards,
* restrained gradients,
* semantic status indicators,
* dense but readable legal-workflow layouts,
* reusable entity-centered cards and controls.

The goal of future design work is continuity. New features should feel like they have always belonged in Shale.

---

## Core Principles

### Consistency over novelty

Prefer recognizable Shale patterns over new visual ideas. A screen should not have a unique design language merely because it is new.

### Composition over duplication

Build larger UI from shared primitives. If a screen needs a card, filter panel, toolbar, status pill, search field, table, or dialog, start from the existing primitive before creating a specialized version.

### Semantic design tokens

Use semantic tokens for color, spacing, radius, elevation, borders, gradients, indicators, and buttons. Tokens should describe design meaning, not arbitrary values.

Examples:

* application background,
* navigation surface,
* content surface,
* card surface,
* primary action,
* destructive action,
* status pill,
* muted text.

Avoid adding raw visual values when an existing semantic value already communicates the intended role.

### Reusable primitives

Shared primitives are the vocabulary of Shale UI. Entity cards, filter panels, section regions, toolbars, buttons, indicators, tables, and dialogs should remain reusable across entities and screens.

### Desktop as reference implementation

The JavaFX desktop application is the visual source of truth. React should mirror the same design language, surface hierarchy, density model, and component composition patterns.

React does not need to copy JavaFX implementation details, but it should preserve the same visual intent.

### Preserve existing visual identity

Do not redesign Shale incidentally while adding features. Treat existing card shapes, surface layering, gradients, colors, typography weight, and indicator patterns as intentional unless an architecture decision explicitly changes them.

### Avoid unnecessary redesign

A feature request is not permission to redesign a screen. Change only what the task requires, and keep surrounding visual language stable.

---

## Visual Language

### Application background

The application background is the deepest visual layer. It is a dark blue and teal shell that gives Shale its recognizable atmosphere. It should frame the application and remain visible behind shell, navigation, and content surfaces.

### Shell surfaces

Shell surfaces organize the outer application layout. They should feel transparent or lightly layered over the application background rather than opaque and isolated.

### Navigation surfaces

Navigation surfaces sit within the shell and identify global movement through the product. They use translucent glass-like surfaces, subtle borders, and light-on-dark text treatment where appropriate.

Navigation should feel stable across desktop and web. Do not create unrelated navigation treatments per screen.

### Content surfaces

Content surfaces are the main working planes. They usually appear as light translucent panels layered over the application shell. They provide focus and readability without abandoning the Shale background.

### Section surfaces

Section surfaces group related content inside a content surface. They are softer than cards and should be used for screen sections, filter areas, form groups, dashboard regions, and similar blocks.

### Cards

Cards are the primary unit for entity summaries and workflow items. They use light card surfaces, rounded corners, subtle borders, and restrained elevation.

Cards should communicate structure without excessive decoration.

### Elevated cards

Elevated cards are used when a card needs prominence, hover affordance, selection focus, or separation from a dense surrounding area. Elevation should remain subtle and consistent with existing Shale shadows.

### Dialogs

Dialogs are focused task surfaces. They should use Shale dialog surfaces, existing button hierarchy, clear sectioning, and familiar form controls. Dialogs should not introduce a different visual system from the main application.

### Overlays

Overlays are temporary layers above the current workflow, such as notification centers, popovers, or transient panels. They should preserve the glass-layered Shale feel while maintaining readability.

---

## Design Tokens

Shale uses semantic tokens as the bridge between visual intent and implementation. Future UI should reuse existing tokens rather than introducing new values.

### Colors

Color tokens describe surface roles, text hierarchy, intent, indicators, and borders. New colors require a semantic need. Do not add colors solely to make one screen look different.

### Typography

Typography should favor clarity, hierarchy, and legal-workflow readability. Existing font sizes, weights, muted labels, strong headings, and compact metadata treatments should be reused before adding new typographic styles.

### Spacing

Spacing should come from the shared density system and component primitives. Avoid one-off margins, padding, and gaps unless a reusable primitive cannot express the layout.

### Density

Density tokens define comfortable, compact, and dense layouts. Choose density based on information complexity and task context, not personal preference.

### Radius

Radius values are part of Shale's identity. Cards, controls, pills, dialogs, and sections should use established rounded forms. Avoid one-off radii.

### Elevation

Elevation should be subtle. Use it to communicate layering, hover, clickability, or modal focus. Do not create screen-specific shadow systems.

### Borders

Borders are used to separate translucent layers, cards, controls, and selected states. They should stay understated and semantic.

### Gradients

Gradients are part of Shale's application shell and action styling. Use existing gradients; avoid screen-specific decorative gradients.

### Indicators

Indicators communicate status, severity, entity type, count, selection, presence, and unread/current state. Prefer existing indicator roles such as status pill, entity pill, badge, and dot indicator.

### Buttons

Buttons follow semantic action hierarchy:

* Primary Button for the main affirmative action.
* Secondary Button for neutral supporting actions.
* Destructive Button for irreversible or dangerous actions.

Do not create custom button colors or shapes when an existing action role applies.

---

## Component Primitives

Larger UI should be assembled from shared primitives. These primitives are the stable vocabulary for both desktop and future web implementations.

### Entity Card

The first-class primitive for entity summaries, relationships, search results, and workflow items.

### Compact Entity Card

A denser Entity Card variant for lists, boards, search results, related objects, and secondary summaries.

### Filter Panel

A section-level grouping for search, filters, date ranges, status filters, and view controls.

### Section Region

A reusable content grouping primitive for forms, dashboard areas, settings groups, reports sections, and detail panels.

### Toolbar

A horizontal action and view-control area. Toolbars should use shared buttons, search fields, filters, and spacing.

### Search Field

A shared rounded search input used consistently across searchable screens.

### Primary Button

The dominant action on a surface or dialog.

### Secondary Button

A neutral action for supporting commands.

### Destructive Button

A danger action for deletion, removal, cancellation with destructive impact, or irreversible workflows.

### Status Pill

A semantic lifecycle/status label. Status colors may be system-defined or database-driven, but the pill form should remain consistent.

### Case Status Indicators

Case status has three official visual representations. Use the smallest representation that communicates the status without adding unnecessary visual weight, and always preserve the database-driven status color.

#### Status Badge

A compact metadata indicator made of a small status-colored dot and the status name. Use Status Badge beside the case name, in Case Details fields, tables/lists, compact metadata rows, and other areas where a filled capsule would compete with nearby content. The badge is display-only and should not imply form input behavior.

#### Status Pill

The primary display treatment for case status. A Status Pill is a rounded filled capsule using the status color from the database and readable foreground text selected for contrast. Use Status Pill for the Case View current status display, status timeline segments, full and compact case cards, Settings Case Status previews, My Shale cards, and statistics/drill-down cards where a prominent status marker is appropriate.

#### Status Selector

The form/input representation for choosing or filtering status. Use Status Selector for Edit Case Status dialogs, status filters, combo boxes, choice boxes, and dropdowns. Preserve native control behavior and accessibility. Dropdown items may include a small color dot when safe, but they do not need to mirror display pills exactly. If the current selected status is shown outside the control, use a Status Badge or Status Pill according to the surrounding visual density.

#### Practice Area Indicators

Practice area has three official visual representations. Always preserve the database-driven practice-area color and choose the smallest representation that fits the surrounding density. Practice area indicators are display primitives only; tenant/global ownership, loading, edit, deactivate/remove behavior, and persistence remain in the existing practice-area management flow.

##### Practice Area Badge

A compact metadata indicator made of a small practice-area-colored dot and the practice-area name. Use Practice Area Badge in small metadata areas, tables/lists, and dense places where a filled capsule would add too much visual weight. The badge is display-only and should not imply selector or edit behavior.

##### Practice Area Pill

The primary practice-area display treatment. A Practice Area Pill is a rounded filled capsule using the practice-area color from the database and readable foreground text selected for contrast. Use Practice Area Pill for the Case View Practice Area field, Settings Practice Area card preview, future practice-area filters/cards, and case cards when a visible practice-area treatment is appropriate. The pill should be visually related to Status Pill through radius, padding, and weight, but its meaning comes from the practice-area name and DB-driven color rather than status lifecycle semantics.

##### Practice Area Selector

The form/input representation for choosing or filtering practice area. Use Practice Area Selector for edit-practice-area dialogs, combo boxes, choice boxes, dropdowns, and filters. Preserve native control behavior and accessibility. Selector items may include a small color dot when safe, but they do not need to look exactly like display pills. If the current selected practice area is shown outside the control, use a Practice Area Badge or Practice Area Pill according to the surrounding visual density.

### Entity Pill

A compact chip for people, assignees, related entities, and metadata.

### Badge

A small label for counts, severity, flags, beta/current states, and auxiliary metadata.

### Dot Indicator

A compact visual marker for unread state, presence, current state, category, or accent color.

### Table

A structured data primitive for dense tabular workflows. Tables should use Shale surfaces, header treatment, row density, selected state, and placeholder styling.

### Dialog

A focused modal primitive using Shale dialog surfaces, shared controls, and semantic action buttons.

---

## Entity Card

Entity Card is a first-class design primitive. It is the common model for summarizing business objects and workflow items.

Entity Cards should support these variants:

### Full

Use for primary entity summaries where the card is the main unit of attention. Full cards may include title, subtitle, status, metadata, related people, counts, and primary secondary details.

### Compact

Use for list, board, and search contexts where many entities must remain visible. Compact cards should preserve the same hierarchy while reducing spacing and secondary detail.

### Inline

Use for small relationship previews inside text-heavy or form-heavy contexts. Inline cards should be recognizable but visually quiet.

### Embedded

Use for nested cards inside another surface, such as related contacts inside a case or linked case summaries inside a task dialog. Embedded cards should avoid competing with the parent card.

### Clickable

Use when the card navigates, opens a detail view, or launches a selection action. Clickability should be communicated through cursor, hover, and subtle elevation or border changes.

### Selectable

Use when cards participate in selection workflows. Selectable cards should preserve card identity while adding clear selected and hover affordances.

Case Cards, Contact Cards, Organization Cards, User Cards, Task Cards, and Search Results all compose from Entity Card. They may differ in content slots and metadata, but they should not invent unrelated surfaces, spacing, borders, or typography.

---

## Density System

Shale supports three density levels.

### Comfortable

Use for primary workflows, full detail pages, full entity cards, forms, and areas where reading and comprehension matter more than maximum information density.

### Compact

Use for lists, boards, search results, related objects, and secondary regions where users scan multiple items.

### Dense

Use for metadata, inline summaries, tables, compact status displays, and areas where many small facts must fit without reducing readability.

Density should be chosen by workflow need. Do not mix densities randomly inside a screen.

---

## Surface Layering

Shale's visual hierarchy is layered from background to temporary overlays:

1. Application Background
2. Shell Surface
3. Navigation Surface
4. Content Surface
5. Section Surface
6. Card Surface
7. Elevated Card Surface
8. Dialog Surface
9. Overlay Surface

Each layer has a role. New UI should be placed into the correct layer instead of creating new surface categories.

---

## Styling Rules

* Avoid introducing new colors without a semantic need.
* Avoid one-off spacing values.
* Avoid one-off border radii.
* Avoid screen-specific gradients.
* Avoid screen-specific shadow/elevation systems.
* Reuse shared controls before creating new ones.
* Prefer extending existing components over creating similar ones.
* Compose screens from primitives rather than styling each screen independently.
* Preserve established card, indicator, dialog, toolbar, and filter patterns.
* Maintain visual consistency across desktop and web.
* Keep the JavaFX desktop implementation as the visual reference until an explicit architecture decision replaces it.
* When React equivalents are added, map Shale design roles to web components and tokens rather than copying JavaFX implementation details.

---

## Future Direction

Future screens should be assembled from shared primitives rather than individually designed.

This applies to:

* Reports,
* Calendar,
* Dashboard,
* Statistics,
* Practice Areas,
* Case Statuses,
* AI features,
* future administrative tools,
* future web application screens.

These features should naturally inherit the Shale design language through shared surfaces, tokens, density, indicators, controls, and Entity Card composition.

A future contributor should be able to add a screen by choosing the correct surface layer, composing shared primitives, selecting appropriate density, and applying semantic tokens. If a new primitive is required, it should be designed as a reusable addition to the Shale system rather than as a one-off screen style.

### Link Type indicators

Case Link Type settings use a reusable `LinkTypeIndicatorFactory` modeled on the existing practice-area pill treatment. The indicator accepts database-driven colors from seeded `#RRGGBB` values and existing stored `0xRRGGBBAA` values, preserving the Shale compact pill/card visual language instead of introducing a separate link-type visual system.

### Case Link Cards

Case Links use a reusable JavaFX `CaseLinkCardFactory` with three official variants: Full, Compact, and Mini. The factory is display-focused: controllers provide the complete link DTO and Open, Edit, Set Primary, and Delete callbacks, while service calls, dialogs, tenant context, browser-launch error handling, and optimistic-concurrency behavior remain in the Case controller/service-port flow.

Full Link Cards are used in Case > Links. They show the bold link title, description or a muted no-description state, the Link Type pill, the Primary badge when applicable, optional case-specific notes, Set Primary for non-primary links, Delete, and a small bottom-right Edit action. Full management remains in Case > Links, but the card itself opens the destination; the screen does not expose Open Link, Move Up, or Move Down controls.

Compact Link Cards are used for the Case > Overview Primary Link. They show the bold title, description or muted fallback, Link Type pill, Primary badge, and only a small bottom-right Edit action. The compact Overview card itself opens the destination and does not expose Open Link or Manage Links buttons.

Mini Link Cards are intended for future dense lists. They show only the link title and compact Link Type pill, open the destination when activated, and do not include management actions, notes, descriptions, or primary badges by default.

For every Case Link Card variant, clicking the card or activating it with Enter/Space opens the stored destination through the existing external-browser helper path owned by the controller. Child action controls must isolate their mouse and keyboard events so Edit, Set Primary, and Delete do not also trigger card opening.

The Link Type pill remains visible on every variant. The Link Type database color also drives a restrained type-identity treatment: a low-opacity background wash that fades back to the normal Shale card surface and a narrow left accent rail. The accent rail currently represents Link Type identity only; it does not communicate status, severity, or workflow state. Invalid or missing colors use the neutral Shale fallback supported by the shared color utility.

Phase 5.1.1 reliability clarification: Case Link Card rail widths are fixed CSS sizes per variant (Full 5px, Compact 4px, Mini 3px) while the database-driven Link Type color continues to drive the left rail color/accent and the low-opacity gradient wash. The rail width must not be supplied through a looked-up dynamic CSS property used by `-fx-border-width`.

Case Link dialogs must validate through the JavaFX dialog button action-event filter pattern so invalid input consumes the Save/OK action, keeps inline validation visible, keeps the dialog open, and focuses the first invalid field where practical. Result converters should only return the already-validated result or normal cancel/window-close empty state.

Case Link mutations and DAO-backed dialog prerequisite loads, including Link Type loading, must execute off the JavaFX application thread using the controller's bounded background executor pattern. UI updates, stale-case rejection, success messages, and error dialogs must be applied back on the JavaFX application thread after service-backed reloads complete.

## Case Link “Shared With” presentation and editor (Phase 5.3.1)

Case Link cards remain display-only UI. `CaseLinkCardFactory` receives all share data through `CaseLinkDto` and must not call services, DAOs, application state, or browser helpers.

* FULL cards show a subtle “Shared With” line beneath description/notes when one or more active shares exist.
* COMPACT cards show a concise “Shared” presentation when active shares exist.
* MINI cards intentionally remain minimal: title plus Link Type pill only.
* Empty cards do not show a noisy “Shared With: nobody” row.
* Child controls keep click isolation so Edit/Delete/Set Primary interactions do not launch the URL; the card background remains the primary click target.
* The Case Link Add/Edit dialog owns the editing workflow surface. Add and Edit dialogs include a functional Shared With editor with a tenant-scoped searchable Contact selector, Add Contact action, staged rows, SharedAt editing, share-specific Notes editing, and Unshare/Remove actions.
* New Case Link dialogs may stage Shared With contacts before the Case Link exists. Cancel persists nothing; Save creates the ExternalLink, CaseLink, and all staged shares through one aggregate service/DAO transaction.
* Existing Case Link dialogs initialize from persisted shares, stage additions/edits/unshares locally, and persist them only when the main dialog is saved. Cancel leaves Link fields and shares unchanged.
* Persisted shares referencing later-unavailable Contacts remain visible with an unavailable marker and may be unshared; unavailable Contacts are not offered as new selector options.
* SharedAt means the time the contact was recorded as receiving or being granted access to the Case Link. Unsharing is a soft-delete of the share record and is distinct from deleting a Contact.

## Case Link Shared With Modal Pattern

Case Link Add/Edit dialogs keep Link fields, the primary checkbox, and the save footer as the primary visual hierarchy. Contact selection is not embedded directly in the Link form. When no active shares are staged, the form shows a Shale secondary action labeled `Share Link` near the bottom of the dialog. When one or more staged or persisted shares are active, the form shows a `Shared With` summary containing compact, display-only Contact Cards and an `Edit Shared With` action.

The `Share Link` / `Edit Shared With` workflow uses a secondary, resizable modal owned by the parent Link dialog. The modal contains three logical sections: `Selected Contacts`, `Case Contacts`, and `All Contacts`. The selected section is the source of truth for the modal session and shows the selected count, compact cards/rows, `Remove` for unsaved selections, `Unshare` for persisted shares, and a `Details` action for share-specific metadata.

Case Contacts are convenience suggestions loaded from Contact-backed entries in the current `dbo.CaseParties` model, joined through `dbo.Cases` and `dbo.Contacts` for tenant validation. They do not read legacy `dbo.CaseContacts`, do not include organization-only Case Parties, and do not represent every tenant Contact. They are deduplicated by ContactId and exclude deleted/unavailable Contacts from new selection. All Contacts uses a searchable, keyboard-operable, virtualized list of active tenant Contacts so blank search still permits browsing without rendering an unbounded card stack.

All three sections share one modal-scoped selection model keyed by ContactId. Applying the modal replaces the parent Link dialog's staged selection; canceling the modal discards the modal copy and leaves the parent staging unchanged. The modal never persists. The parent Add/Edit Link OK button remains the only persistence point and continues to save Link fields plus share additions, updates, and removals as one aggregate transaction.

Share `Details` edits only the share-specific `SharedAt` and optional Notes values. `SharedAt` is required, share notes follow the 500-character service/schema contract, and invalid details keep the dialog open with inline validation. Persisted unavailable/deleted Contacts remain visible in the selected and summary views with an unavailable marker, retain stored identity, can be edited/unshared when service rules allow, and are excluded from new selectable Case/All Contact lists.

Accessibility expectations: all share buttons expose clear accessible text, search focus remains in the child modal, Enter/Space toggles Contact selection inside the list rather than saving the parent Link dialog, selected state is communicated by a checkmark and accessible text rather than color alone, and Escape/Cancel closes only the active modal.

## Case Link sharing visual integration (Phase 5.3.5)

Case Link sharing composes the existing Contact Card and Shale dialog primitives rather than generic selector buttons or raw text rows.

* Share Link / Edit Shared With uses selectable MINI Contact Cards for Case Contacts. These cards wrap in a responsive FlowPane, toggle the modal-scoped selection on click or keyboard activation, show a checkmark and selected style, and never navigate to Contact View from inside the modal.
* All Contacts remains a virtualized ListView. Cells render a lightweight MINI Contact Card graphic that shares the Contact Card styling, clears graphic/text/style/accessibility state on reuse, and toggles selection without leaving the modal.
* Selected Contacts continues to show Contact identity first and Details / Remove / Unshare as secondary row actions. Missing email/phone placeholders are suppressed; unavailable persisted Contacts retain an explicit unavailable marker.
* Add/Edit Link dialogs use the secondary dialog shell plus a styled form surface, padded interior, styled ScrollPane viewport, Shale section surfaces, consistent labels, and a distinct Shared With section so the dialog does not appear as an unstyled white document.
* Empty Shared With state renders only the `Share Link` action and reserves no blank card viewport. One or a few Contacts size to content height without unnecessary scrollbars. Many Contacts wrap and grow up to a modest bounded height before the embedded contact area scrolls, while Edit Shared With and the dialog OK/Cancel footer remain reachable.
* FULL and COMPACT Case Link Cards render active shares as embedded, display-only MINI Contact Cards in a wrapping FlowPane beneath a subtle Shared With label. MINI Link Cards remain unchanged and do not render share summaries.
* Embedded Contact Cards on Link Cards are mouse-transparent/display-only by design: clicking them passes through to the parent Link Card and opens the external URL; they do not navigate to Contact View. Edit, Delete, and Set Primary remain isolated child actions.
* Share display names continue to use the existing Case Link Contact option mapping. Legacy Contact records that store a phone number in the Name field are treated as valid Contact display names and are not filtered, rewritten, or flagged as invalid by this presentation layer.
