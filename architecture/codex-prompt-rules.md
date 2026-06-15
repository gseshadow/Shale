# Codex Prompt Rules

## Purpose

This file is the primary entry point for all Codex work in the Shale project.

Before making any code changes, consult this document first. Use it to determine which additional architecture documents are relevant to the task.

---

## Required Workflow

Before making changes:

1. Read this file.
2. Read any referenced architecture documents relevant to the task.
3. Inspect existing implementations before creating new ones.
4. Reuse existing patterns and components whenever possible.
5. Verify assumptions against the codebase and database schema.
6. Run compile/tests appropriate to the change.
7. Summarize:

   * Files changed
   * Reason for change
   * Risks or follow-up work

---

## Architecture Documents

### Database Work

Read:

* architecture/database-schema.md
* architecture/tenancy-and-rls.md

Required:

* Do not guess table names.
* Do not guess column names.
* Do not guess role ids.
* Verify schema before writing SQL.
* Preserve tenant filtering.
* Preserve soft-delete filtering.
* Preserve existing DAO patterns.

---

### UI Work

Read:

* architecture/development-rules.md

Required:

* Reuse existing card components.
* Reuse existing card factories.
* Reuse existing navigation patterns.
* Reuse existing dialogs and controls when practical.
* Follow existing controller/service patterns.

Do not create duplicate implementations when an existing component already solves the problem.

---

### Live Updates / Notifications

Read:

* architecture/live-update-architecture.md

Required:

* Preserve tenant isolation.
* Preserve PubSub group routing.
* Do not bypass the dispatcher architecture.
* Follow existing notification patterns.

---

### System-Level Changes

Read:

* architecture/system-overview.md

Required:

* Respect module boundaries.
* Preserve service-port architecture.
* Avoid coupling UI directly to persistence.

---

## Database Safety Rules

Before modifying SQL:

1. Verify all referenced columns exist.
2. Verify all referenced tables exist.
3. Verify joins match existing patterns.
4. Verify role values from documented role mappings.
5. Verify status mappings before use.
6. Prefer existing queries and DTO mappings when possible.

If a field cannot be verified:

* Do not invent a column name.
* Do not assume a relationship.
* Verify from the schema or existing code first.

---

## Debugging Rules

When a reported fix does not appear to work:

Do not immediately make additional assumptions.

Instead:

1. Identify the exact code path being executed.
2. Verify the modified component is the component actually displayed.
3. Trace data through:

   * Database
   * DAO
   * DTO
   * ViewModel
   * UI Binding
4. Confirm the final displayed control receives the expected data.
5. Use logging to prove the failure point when necessary.

Examples:

* UI changes that compile but do not appear.
* Grid columns that remain blank.
* Filters that do not affect displayed results.
* Notification updates that do not appear.
* Layout changes that have no visible effect.

---

## Layout Rules

When fixing sizing or layout issues:

Do not inspect only the target control.

Inspect the entire parent hierarchy:

* BorderPane
* VBox
* HBox
* AnchorPane
* ScrollPane
* TableView
* ListView

Verify:

* VGrow/HGrow settings
* Anchor constraints
* Preferred sizes
* Maximum sizes
* Parent container behavior

A layout fix is not complete until the visible UI behaves correctly.

---

## Reuse First

Before creating:

* New card
* New dialog
* New navigation flow
* New service
* New DAO
* New DTO
* New notification type
* New UI component

Search for an existing implementation that can be reused or extended.

Reuse is preferred over replacement.

---

## Shale-Specific Rules

### Cases

Use existing case navigation patterns.

Double-clicking, card clicks, mini-card clicks, and case-opening behavior should reuse existing navigation infrastructure.

### Cards

Shale already has card and mini-card implementations.

When asked to add a card or mini-card:

* Reuse existing card factories when possible.
* Match existing card behavior and styling.
* Do not create parallel card systems.

### Multi-Tenancy

Tenant isolation is mandatory.

Never:

* Bypass RLS intentionally.
* Remove tenant filters.
* Cross tenant boundaries.
* Open runtime connections without proper tenant initialization.

---

## Definition of Done

A task is not complete because it compiles.

A task is complete when:

1. Code compiles.
2. Tests pass (when applicable).
3. The visible UI behaves correctly.
4. The actual user-reported issue is resolved.
5. No existing functionality is broken.
