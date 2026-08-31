# EnhancedTextArea

`EnhancedTextArea` is shale-ui's reusable editor for multiline **plain text**. Its
`textProperty()`, prompt, editable state, and preferred-row APIs deliberately
mirror `TextArea`, so existing bidirectional bindings and persistence remain
unchanged. `expandable` and `spellCheckEnabled` are JavaFX properties and both
default to `true`.

The expanded dialog edits an isolated `ExpandedTextEdit` draft. Apply copies the
draft to the control; Cancel, the close affordance, and Escape discard it.
Ctrl+Enter applies. The dialog uses `AppDialogs` ownership and secondary-window
styling.

Spell checking is offline and dictionary based. The bundled baseline dictionary
is loaded from `spellcheck/en_US.txt`; callers can add session/user dictionary
terms through `addToCustomDictionary`. A future settings-backed implementation
can persist those terms without changing the component's text contract.

JavaFX `TextArea` does not expose styled character ranges or supported APIs for
painting spelling underlines. Rather than depending on private skins or replacing
native text editing, this implementation presents a non-blocking misspelling
summary and selection-based suggestions/Ignore in the context menu. This retains
native selection, clipboard, keyboard navigation, and undo/redo behavior. A true
inline squiggle would require adopting a maintained styled-text control and
should be evaluated separately.
