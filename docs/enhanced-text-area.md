# EnhancedTextArea

`EnhancedTextArea` is shale-ui's reusable narrative editor. Its persisted value is
a plain `String` containing either legacy plain text or Shale's limited Markdown.
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

The compact control remains a native `TextArea`. Plain values are directly editable.
When supported formatting is present it becomes a read-only, syntax-free preview;
the tooltip directs editing to the expand button, preventing a compact edit from
silently damaging Markdown. `textProperty()` always remains the persisted value.

The expanded surface alone uses RichTextFX 0.11.6 (BSD-2-Clause), selected for its
styled ranges, undo manager, and inline decoration support. Supported persistence is
`**bold**`, `*italic*`, the narrowly recognized `<u>underline</u>` extension, `- `
bullets, and numbered Markdown lists. Other HTML is ordinary inert text: there is no
HTML renderer, script execution, remote content, or network spellchecking.

Spelling is debounced and fully local. Misspelled ranges receive a red dashed
underline which is recomputed from the local dictionary and omitted by the Markdown
codec. The caret word's context menu offers replacements, Ignore, and Add to
dictionary. Apply serializes the isolated draft; Cancel/Escape discards it.
