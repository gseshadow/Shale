# JavaFX rendered-test review

This review replaces the former blocking `ui-rendered` area. CSS and presentation-only FXML changes
select `ui-presentation` (with `ui-fxml-structure` for FXML); Java/controller changes select
`ui-behavior` and their relevant feature;
rendered geometry is explicit and advisory through `mvn -Pui-visual test`.

## Retained as blocking behavioral or structural coverage

- `CalendarEventCardVisualPolishTest` — semantic ownership/accent classes and factory behavior.
- `CalendarVisualPolishSourceTest` — calendar pseudo-class states, controls, and filtering behavior.
- `CaseLinkSharesPhase535VisualIntegrationTest` — selectable-card and display-only interaction behavior.
- `CaseLinkSharesPhase536VisualDefectsTest` — reusable cell state and accessibility reset behavior.
- `ControlStylesRuntimeContractTest` — semantic class replacement and disabled/default identity.
- `SettingsFxmlLoadTest` — FXMLLoader, controller injection, required IDs, and handler wiring.
- `SettingsUserManagementSemanticRuntimeTest` — semantic action classifications after FXML injection.
- `UserAssignedTasksRenderingTest` — shared card path, filtering, navigation, and semantic controls.

These tests do not enter the critical manifest automatically; they block only when their directly
mapped behavior or structural area is selected.

## Moved to advisory visual status

- `NewTaskDialogFooterCssRuntimeTest` — snapshots and exact rendered corner radii.
- `TaskDetailDialogLayoutRegressionTest` — padding/radius-derived dialog presentation.
- `MaterialRequestCardFactoryRenderingTest` — rendered widths, bounds, clips, and layout geometry.
- `CasesToolbarCssRuntimeTest` — JavaFX CSS rendering probe.
- `MyShaleControllerBoardLayoutTest` — viewport widths and layout constants mixed with dashboard checks.
- `SettingsUserManagementVisualTest` — stage layout, skin cells, fixed size, and snapshot output.
- `UserResponsiveLayoutTest` — viewport and scrollbar/layout breakpoint structure.
- `SemanticControlCssRuntimeTest` — rendered padding, preferred width, and CSS skin conversion probe.

## Deleted as obsolete or brittle

- `TaskCardHoverTooltipLayoutRegressionTest` — popup/scene timing and post-layout clipping arithmetic.
- `AppDialogsResponsiveActionLayoutTest` — exact width, wrapping rows, and child-bound containment.
- `CaseLinkSharesPhase534DialogLayoutTest` — superseded source-fragment assertions for scrollbar and modal layout helpers.
- `CaseLinkSharesPhase537ContentSizingTest` — reproduced preferred-height, viewport, and screen-bound calculations.
- `RequestedFromWorkflowDialogLayoutTest` — post-layout scene-bound ordering and containment arithmetic.

Deleted geometry tests must not be recreated with wider tolerances. Visual correctness for those
surfaces is assessed by targeted manual inspection when their presentation changes.
