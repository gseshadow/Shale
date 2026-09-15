# JavaFX theme infrastructure (Phase 1A)

## Scope and ownership

`ThemeManager.application()` is the single production theme service. It owns session-only `LIGHT` / `DARK`
state and is shared by the application scene, `AppDialogs`, secondary-window constructors, and Shale-owned
popup roots. The public constructor exists for isolated tests; production code must use the application-owned
instance. Theme state is deliberately not persisted or exposed through Settings in Phase 1A.

`/css/app.css` remains the stable author-stylesheet entry point and retains the foundation imports. Every
registered target is installed in this order:

1. `/css/app.css`
2. the active `/css/theme/<theme>.css`

The manager removes only URLs it resolved as Shale base/theme resources, preserves unrelated stylesheets in
their existing relative order, and appends the two Shale sheets. Re-registration and theme changes are
therefore idempotent. Missing resources fail during manager construction with the missing classpath path.

## Lifecycle

Scenes and independently styled parents are weakly registered, so the manager does not keep closed windows,
dialog panes, or popup roots alive. Owners may explicitly unregister; `AppDialogs` unregisters a replaced
stage scene and `ShaleFilterMenu` unregisters its content when hidden. All registration and theme mutations
must occur on the JavaFX Application Thread.

The main scene remains registered while navigation replaces its root, preserving the active theme across
login and application routes. `AppDialogs` installs the complete theme for both `DialogPane` alerts/dialogs
and scenes attached to secondary stages. Existing direct `app.css` installers now route through the manager.

## Compatibility and deferred work

The light token sheet repeats the current `foundation/colors.css` production values; the foundation remains a
fallback for compatibility. The dark sheet is an internal technical palette and is not a claim of completed
dark-mode visual migration. Database-defined entity/status/type colors remain authoritative.

Phase 1A specifically supports the custom `ShaleFilterMenu` popup root. Native context menus, rich-text
context menus, and Task hover-popup visual migration remain deferred. Phase 1B should migrate remaining raw
color selectors to looked-up colors and visually verify each screen in both themes without changing the
central lifecycle contract.
