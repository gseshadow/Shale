# Phase 8J shared utility-dialog inventory

## Scope and findings

The production search covered `AppDialogs`, `EnhancedTextArea.openEditor`, direct `Alert`, `Dialog`,
`ChoiceDialog`, and `TextInputDialog` construction, dirty-close helpers, Intake recovery, theme registration,
and authenticated-session teardown. This phase changes only the common presentation/result shell. Workflow
editors completed in 8A–8I, native context menus, hover popups, pickers, and file choosers remain untouched.

| Family | Owner/type/modality | Buttons and result boundary | Theme/session state before 8J | 8J disposition |
|---|---|---|---|---|
| `AppDialogs.showInfo/showError` | Caller `Window`; blocking window-modal `Stage` | Acknowledge only; no domain result | Scene registered and live-themed; weak registration, but no explicit unregister | Shared semantic information/error roots and explicit scene cleanup |
| `showConfirmation` | Caller `Window`; blocking window-modal `Stage` | `boolean`; Cancel is cancel button; caller mutates only after `true` | Live-themed; generic visual state | Retained compatibility API; ordinary/danger semantic root chosen from action kind |
| `showChoice` | Caller `Window`; blocking window-modal `Stage` | Typed `Optional<T>`; caller-owned values and labels | Live-themed; generic visual state | Retained typed boundary; choice semantic root |
| dirty close choices | Owning workflow window; blocking nested modal | `Keep Editing`/`Discard`; false/true; no persistence in helper | Several callers assembled typed actions; some older callers use confirmation wording | Added narrowly typed safe-default helper; caller migration is deliberately not mechanical |
| destructive confirmation | Owning workflow window; blocking nested modal | Caller action runs only after accepted result | Many callers pass `DANGER`, historically making it default | Added a cancel-default typed helper; existing compatibility calls are unchanged where behavior is established |
| `EnhancedTextArea.openEditor` | Invoking window; `Dialog<String>`, window-modal by JavaFX owner policy | Apply returns current Markdown draft; Cancel/X/Escape return empty; Ctrl+Enter is explicit Apply | DialogPane registered/live-themed; draft disposed on hide | Dedicated enhanced root, accessible purpose, explicit theme unregister, and one-shot result guard |
| direct workflow `Dialog<T>` | Workflow owner; modal; specialized typed converters/validation | Varies (`String`, DTO, `Void`, staged aggregate) | Most use `applySecondaryDialogShell` | Remain separate because validation and result contracts differ |
| Intake failed-save recovery | Intake window; existing choice/result flow | Try Again / Save Local Backup / Copy Intake Text / Keep Editing | Workflow-owned state and persistence boundary | Unchanged; it is specialized recovery, not a generic confirmation |
| update progress/recovery alerts | Application owner; mutable non-domain progress and recovery flow | Hide/cancel/restart/update actions | Raw `Alert`, but themed via `applySecondaryWindowChrome` | Intentionally specialized and unchanged |
| Team editor confirmations | Team editor owner; raw `Alert` with workflow-specific async/close behavior | Existing confirmation results | Raw but explicitly themed | Intentionally specialized and unchanged |
| choice/text-input dialogs | No production `ChoiceDialog` or `TextInputDialog` construction found | Not applicable | Not applicable | No category invented |
| shared busy/progress/validation dialog | No general-purpose production helper found | Not applicable | Busy/validation is workflow-local | No category invented |

## Direct-construction answers

1. Raw JavaFX `Alert` construction remains in `TeamEditorDialog` and `UpdateFlowCoordinator`; each has
   intentional specialized state or recovery behavior and already opts into `AppDialogs` theme chrome.
2. Raw `Dialog<T>` instances in definition administration, Case/Contact/Organization/User editors, and
   password/color flows retain typed converters, inline validation, or staged aggregate semantics.
3. Information, warning, sanitized error, ordinary confirmation, destructive confirmation, dirty discard,
   and typed action-choice presentation can safely share the scoped utility vocabulary.
4. Enhanced editing, Intake recovery, progress/update, password input, color selection, and typed workflow
   editors remain separate because their result and validation contracts differ.
5. Ownerless calls exist where legacy Settings/User administration passes `null`; no owner is synthesized or
   retained by this presentation phase. All `EnhancedTextArea` production launchers resolve their node window.
6. Dialogs routed through `AppDialogs` receive live Light/Dark updates. No unthemed raw utility constructor was
   identified in the scoped family; native/excluded popups are outside 8J.
7. Blocking utility stages cannot coexist with an owner-driven logout interaction. Registrations are now
   explicitly released on close in addition to `ThemeManager` weak tracking; workflow session teardown remains
   authoritative for nonblocking workflow windows.
8. Legacy exception concatenation still exists outside the shared helper, notably Task loading/mutation paths,
   Settings audit opening, and some older Case operations. It is not centralized into `showError`; 8J adds no
   raw-exception conversion. Follow-up sanitization must be scoped to each business error policy.
9. Ordinary confirmations and acknowledgement dialogs rely on default-button Enter. Destructive and discard
   typed helpers deliberately default to the safe cancellation/Keep Editing choice.
10. `EnhancedTextArea` is the shared multiline-Enter surface; plain Enter stays inside RichTextFX and only the
    documented Ctrl+Enter accelerator applies.
11. Intake, Material Request, Case Overview, Task, Contact, Organization, User, Party, and Calendar dirty-close
    workflows require their established discard wording; compatible callers may adopt the typed helper without
    reducing the result to generic Yes/No.
12. All completed workflow-specific windows, pickers, Intake recovery logic, native context menus, Task hover
    popups, and file/directory choosers remain structurally untouched.

## Enhanced-text caller matrix

The three production static `openEditor` call sites are in `CaseController`: two explicit description/narrative
edit launchers and the explicit Case Update editor. Embedded `EnhancedTextArea` controls cover New Intake client
condition/description/summary, the Case Update composer, Contact condition/notes, Organization notes,
Calendar/Event description, and new/detail Task description. Material Request continues to use its existing plain
wrapped `TextArea`; no enhanced-editor path exists there and none was invented. The explicit expand control or
explicit Case action remains the only launch path: Save, Submit,
Create, and ordinary Enter handlers were not rewired. Current text is passed unchanged to the isolated draft;
Apply alone returns it to the caller, while Cancel, Escape, and title-bar close do not cross the callback boundary.

## Hierarchy and retained boundaries

Previously, common message/choice stages used `app-dialog-root`, while expanded text used only the broad
`secondary-window-shell`. The final hierarchy adds `utility-dialog-root` plus one semantic state class; expanded
text additionally uses `enhanced-text-dialog-root`. The stylesheet is entirely root-scoped and imports after the
completed workflow-window sheets, so it cannot select Intake, Task, Calendar, Material Request, Party, Contact,
Organization, Case Team, User, or Settings roots unless a shared utility root is explicitly present.

No service, DAO, SQL, authorization, tenant/session identity, optimistic-concurrency, transaction, audit, or
persistence code changes in Phase 8J. Accordingly, no schema migration or new audit event is required.
