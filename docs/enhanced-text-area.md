# EnhancedTextArea

`EnhancedTextArea` is shale-ui's reusable editor for multiline **plain text**. Its
`textProperty()`, prompt, editable state, and preferred-row APIs deliberately
mirror `TextArea`, so existing bidirectional bindings and persistence remain
unchanged. `expandable` and `spellCheckEnabled` are JavaFX properties and both
default to `true`. `editorTitle` supplies the field name used by the expanded
dialog (for example, `Description` produces `Edit Description`).

The expanded dialog edits an isolated `ExpandedTextEdit` draft. Apply copies the
draft to the control; Cancel, the close affordance, and Escape discard it.
Ctrl+Enter applies. The dialog uses `AppDialogs` ownership and secondary-window
styling.

Spell checking is offline and dictionary based. The bundled baseline dictionary
is loaded from `spellcheck/en_US.txt`; callers can add session/user dictionary
terms through `addToCustomDictionary`. A future settings-backed implementation
can persist those terms without changing the component's text contract.

JavaFX `TextArea` does not expose styled character ranges or supported APIs for
painting spelling underlines. RichTextFX was re-evaluated for this refinement,
but adopting it would replace the native accessibility, prompt, selection,
clipboard, undo manager, and `StringProperty` behavior merely to paint spelling
ranges. No dependency was added. Instead, the native editor remains visually
quiet and offers caret/selection-aware suggestions, Ignore, and Add to dictionary
in its context menu. A true inline squiggle remains unavailable without accepting
that larger control and compatibility migration.
