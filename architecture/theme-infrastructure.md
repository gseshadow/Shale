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

## Phase 1B semantic paint vocabulary

Phase 1B adds a matching canonical vocabulary to both theme resources. New shared component work uses these
roles; `foundation/colors.css` and older names remain compatibility fallbacks for unmigrated screens:

* surfaces: application canvas/chrome, navigation/default/hover/selected, content plane, section, card,
  card hover, elevated, input, muted, and overlay/dialog;
* text: primary, secondary, muted, disabled, on-dark, on-primary, link/hover, danger, success, and warning;
* boundaries: subtle/strong border, divider, input/hover, focus, danger, and selected;
* actions: Primary gradient endpoints and states, Secondary states/text, Danger wash/states/text, icon hover,
  and selected-control background;
* semantic presentation: neutral chip, success/warning/danger/info washes/borders/text, neutral avatar,
  inactive/complete/current stage, and update-card roles; and
* interaction state: focus ring, selection, validation error, disabled opacity, and the one card-shadow color.

The canonical names begin with `-shale-color-` (plus `-shale-opacity-disabled`). Theme files contain paint
only; all geometry, typography, state selectors, and component composition live in the stable foundation
stylesheets loaded by `app.css`. Existing names such as `-shale-color-content-surface`,
`-shale-color-dialog-surface`, `-shale-button-*`, `-shale-control-*`, and `-shale-indicator-*` remain active
compatibility aliases/fallbacks. They are not the vocabulary for new component paint, and heavily used
legacy tokens have not been globally repointed.

Solid token pairs are automatically contrast-checked for normal text and essential focus/action boundaries.
Primary and current-stage gradients, plus authoritative database colors, require visual review because their
paint is dynamic; readable text/labels remain mandatory so hue never carries meaning alone. Dark theme has
full token parity but remains technical support rather than a claim that every legacy page is migrated.

## Phase 2A shared application shell

The application frame around routed desktop pages is the first consumer of the A.2 vocabulary. The shared
`main.fxml` shell owns the pale application canvas, navy-to-blue top chrome, deep-navy navigation rail,
near-white route plane, selected-navigation state, signed-in footer, and the global search, intake,
notification, profile, and logout controls. Its stable geometry and state selectors live in
`foundation/shell.css`; paint continues to come exclusively from the light and dark canonical tokens.

Ordinary shell actions, including the profile navigation action, use `ControlStyles`, while global navigation,
the back affordance, and notification bell retain narrowly scoped shell classes for their specialized interaction. Routed page
content remains inside the shared content plane but continues to own its internal scrolling. Case section
navigation remains owned by `AppSectionTabs` and its horizontal scroll boundary, so this phase does not move
or restyle Case Overview content, the case header, stage history, detail areas, Primary Link, or Updates rail.

## Phase 7 authenticated Appearance preference

Light remains the unauthenticated and failure-safe default. The existing `dbo.UserPreferences` store owns the
typed `appearance.theme` value (`LIGHT` or `DARK`) for the current tenant/user; no theme-specific table or
machine-local preference is used. Authentication resolves the value on its worker thread and applies it through
`ThemeManager` on the JavaFX thread before constructing or revealing the main shell. Identity checks discard a
completion belonging to a prior session. Logout/login presentation resets the manager to Light.

Settings > Personal exposes only Light and Dark. Selection previews immediately and persistence is asynchronous.
Failure restores the last confirmed theme and reports a sanitized inline error. This personal presentation
preference is intentionally not written to the entity-action audit log: it changes no domain, administrative,
authorization, or sensitive data, while `UpdatedAt`/`UpdatedByUserId` retain store-level provenance.
