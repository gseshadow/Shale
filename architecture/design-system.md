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
