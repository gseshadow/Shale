# JavaFX multiline editor inventory

Inventory made by searching `shale-ui` for FXML `TextArea`, imported/qualified `TextArea`, constructors, and wrappers.

| Area / occurrence | Class | Disposition | Reason |
|---|---|---|---|
| Shared inline editor | D | Unchanged | `EnhancedTextArea` intentionally wraps the native JavaFX input control. |
| New intake condition, description, summary | A | Already enhanced | Reference implementation. |
| New/edit organization notes | A | Enhanced / shared popup | Narrative organization notes. |
| Organization detail Notes edit action | B | Shared popup | Apply continues through the existing organization update callback. |
| Case overview Description edit action | B | Shared popup | Apply continues through `saveCoreOverviewField`; Cancel has no callback. |
| Case detail narrative edit actions | B | Shared popup | Existing individual field update callbacks and RowVer handling remain authoritative. |
| Case full-form description, summary, accepted/denied detail | A | Unchanged | Coupled legacy whole-record edit mode; retained pending removal of that mode rather than changing concurrency semantics. |
| New/edit task description | A | Enhanced | Ordinary task narrative; normal task Save remains authoritative. |
| Calendar event description | A | Enhanced | Ordinary event narrative; normal event Save remains authoritative. |
| Event wizard / case-date notes | A | Unchanged | Specialized occurrence workflow with test-facing native control contract. |
| Task, case, and material update composers | A | Unchanged | Append-only feed composers; popup formatting would change immutable-feed conventions. |
| Material request/item descriptions and source text | A/D | Unchanged | Dense metadata workflow; source text is controlled provenance data. |
| Case links, shares, external-link notes | D | Unchanged | Structured metadata attached to link/share records. |
| Contact medical condition | A | Unchanged | Specialized classification/medical-profile workflow. |
| Party-add notes | A | Unchanged | Embedded workflow whose native-control validation contract is shared. |
| Contact classification description | D | Unchanged | Administrator-maintained taxonomy metadata. |
| Case placeholder/debug area | C | Unchanged | Machine/status placeholder, not persisted narrative. |
| Test-only TextArea instances | C/D | Unchanged | Fixtures and contract probes, not application editing surfaces. |
