# Phase 8K transient-popup inventory

## Production inventory and boundaries

The repository-wide Java search for `ContextMenu`, `MenuItem`, `CheckMenuItem`, `RadioMenuItem`,
`setContextMenu`, `setOnContextMenuRequested`, `Popup`, and `Tooltip` found three Shale-owned context
menus and one Task hover popup. No domain context menu exists for Contact, Organization, User, Task,
Material Request, Calendar, TreeView, or ListView, and Phase 8K does not invent one.

| Surface | Launcher / owner | Identity and actions | State / focus | Position, theme, cleanup |
|---|---|---|---|---|
| Cases table row context menu (`CasesController`) | Secondary click, context-menu key, or Shift+F10 handled by `TableRow`; row/window owner | Captures the `CaseCardVm` (including stable numeric id) at show. Open Case, separator, Copy Case Name, Copy Case Number, separator, Create Task, Add Case Update. Execution rejects a recycled row whose current id differs. | JavaFX menu keyboard navigation/focus and existing authorization/callbacks remain authoritative; opening does not alter table selection. | JavaFX positions the `ContextMenu`; `TransientPopupSupport` attaches current theme CSS on show and unregisters it on hide. Target is cleared on hide. |
| Compact `EnhancedTextArea` spell/edit menu | Native `TextArea` context request; editor/window owner | Captures the selected/caret word for ordered local suggestions, Ignore, Add to dictionary, separator; Undo, Redo, separator, Cut, Copy, Paste, Delete, separator, Select All. | Enablement comes from JavaFX undo/redo, selection, editability, text, and clipboard state at show. Actions edit only the in-memory control value and never invoke expanded-dialog Apply. | JavaFX menu positioning/edge correction; shared scoped roots and show/hide theme registration. |
| Expanded RichTextFX spell/edit menu | Mouse context request at exact RichTextFX hit or keyboard request at caret; editor/dialog owner | Captures the exact local misspelling range; ordered suggestions, Ignore, Add to dictionary, separator when applicable; then Undo, Redo, separator, Cut, Copy, Paste, Delete, separator, Select All. | Existing formatting/selection model is retained. Focus leaving the editor or owner focus loss hides the menu. The editor dispose path stops spelling debounce and removes scene/window listeners. | JavaFX menu positioning/edge correction; shared scoped roots and show/hide theme registration. |
| Task hover popup (`TaskCard`, all factory variants) | Delayed hover on card; card/window owner | Informational Task title plus full already-hydrated description only when `TaskCardFactory` receives `allowPhiDescription`; no actions and no navigation replacement. | 400 ms deliberate show delay; 120 ms transfer grace. It remains open while card **or** popup is hovered, has no maximum-duration timer, and never takes focus. | Anchors beside the card, flips and clamps against the visual bounds of the monitor containing the card, and uses a small transfer gap. Theme root is weakly registered only while shown and unregistered on hide. Card removal hides and cancels both pending transitions. |

MenuButton content in Reports (`Select all`, `Clear`, status checks), Cases column selection, Case-list
filters, Notification Center dismissal choices, and Team Editor role choices is application-authored menu
content hosted by JavaFX `MenuButton` skins, not application-created `ContextMenu` instances. Its existing
JavaFX skin ownership, selection semantics, and actions remain unchanged in this focused phase.

## Classification and explicit exclusions

* **Safely styleable/application-owned:** the Cases row menu, compact Enhanced Text Area menu, expanded
  RichTextFX menu, and Task `Popup` listed above.
* **JavaFX skin/platform owned:** default context menus of ordinary `TextField`/`TextArea` controls;
  ComboBox, ChoiceBox, DatePicker, ColorPicker, and MenuButton popup skins; ordinary `Tooltip`s.
* **Operating-system owned:** `FileChooser` and `DirectoryChooser`, including their menus.
* There is no other spellcheck provider or asynchronous suggestion service. Both real spellcheck menus use
  the existing offline `LocalSpellChecker`; no typed text leaves the process.
* There are no CheckMenuItem/RadioMenuItem objects inside an application-created `ContextMenu`; the found
  CheckMenuItems belong to MenuButton dropdowns. No production `RadioMenuItem` construction exists.

## Audit questions and lifecycle result

1. The three context menus in the table are application owned and receive `.shale-context-menu`; both
   narrative menus additionally receive `.rich-text-context-menu`.
2. Ordinary text-control and picker menus come from JavaFX skins; file/directory chooser menus come from
   the OS and are intentionally untouched.
3. Custom text actions are offline spelling replacement, Ignore, and Add to dictionary. Rich-text formatting
   remains toolbar/shortcut behavior and is not added to the context menu.
4. Only Cases row actions depend on domain identity. They now use the captured numeric Case identity rather
   than displayed text or visual row index.
5. Before Phase 8K the Cases menu dereferenced the recycled row at action time. No menu inferred identity
   from displayed text. The stable-id guard closes the recycled-row gap.
6. JavaFX owns ContextMenu edge handling. The previous Task popup clamped after cursor placement but did not
   deliberately flip relative to its card; it now selects the card's monitor, flips, and clamps to visual bounds.
7. The prior Task card installed an owner `setOnHidden` handler and locally retained popup content between
   reuse cycles. The final lifecycle cancels show/hide transitions on hide/removal and unregisters popup theme
   roots. Rich text already removed focus/window listeners and stopped its spelling timer on disposal.
8. `FULL`, `MY_TASKS`, `COMPACT`, `COMPACT_FLUID`, and `MINI` all use the single `TaskCard` hover path.
9. Hydration is caller-defined in the existing `TaskCardModel`; no hover query was added. Authenticated Case,
   My Shale, User, Search, and Calendar callers continue passing their already-hydrated model fields.
10. `TaskCardFactory.create(model, variant)` suppresses PHI description by default. Only callers explicitly
    passing `allowPhiDescription=true` expose it. Passive `COMPACT_FLUID` titles retain the existing PHI rule.
11. Task hover uses one-shot show and transfer-grace transitions, not automatic dismissal. Ordinary Tooltips
    retain platform timing and are outside this phase.
12. `TransientPopupSupport` reuses only stylesheet lifecycle and geometry. Context-menu command population,
    spellcheck range authority, and Task hover union-state remain deliberately separate.

The shared CSS is token-only and scoped under `.shale-context-menu`, `.rich-text-context-menu`, and
`.task-hover-popup`. It covers item states, labels, accelerator/indicator/arrow regions, separators, danger
semantics, focus, popup border/elevation, title, and description. Existing database colors are not changed.
No SQL, service, DAO, persistence, audit mutation, logging, clipboard telemetry, or network read was added.
