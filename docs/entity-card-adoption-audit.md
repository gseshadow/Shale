# Entity Card Adoption Audit

Date: 2026-06-26

## Scope

This audit reviewed JavaFX entity rendering in `shale-ui` for manually constructed case, task, contact, organization, and user cards. The goal was to expand use of existing entity-specific factories rather than create a generic entity card abstraction.

## Existing entity card factories

- `CaseCardFactory` supports `FULL`, `COMPACT`, `MINI`, and `TASK_PREVIEW` variants.
- `TaskCardFactory` supports `FULL`, `MY_TASKS`, `COMPACT`, `COMPACT_FLUID`, and `MINI` variants.
- `ContactCardFactory` supports `FULL`, `COMPACT`, and `MINI` variants.
- `OrganizationCardFactory` supports `FULL`, `COMPACT`, and `MINI` variants.
- `UserCardFactory` supports `FULL`, `COMPACT`, and `MINI` variants.
- Additional card factories present: `NotificationCardFactory`, `CalendarEventCardFactory`, `PracticeAreaCardFactory`, and `StatusCardFactory`.

## Findings by entity type

### Case cards

| Location | Classification | Notes |
| --- | --- | --- |
| `CasesController` | Already uses factory | Main case list uses `CaseCardFactory`. |
| `SearchController` | Already uses factory | Global case search and deleted-case search use `CaseCardFactory`. |
| `ContactViewController` | Already uses factory | Related cases use `CaseCardFactory`, with a relationship metadata wrapper. |
| `OrganizationController` | Already uses factory | Related cases use `CaseCardFactory`, with a relationship metadata wrapper. |
| `MyShaleController` | Already uses factory | My Cases and task-adjacent case previews use `CaseCardFactory`. |
| `UserController` | Already uses factory | User-related cases use `CaseCardFactory`. |
| `CalendarController` | Already uses factory | Related case nodes use `CaseCardFactory.MINI`. |
| `TaskDetailDialog` | Already uses factory | Related case preview uses `CaseCardFactory.MINI`. |
| `NewCalendarEventDialog` | Already uses factory | Selected case preview uses `CaseCardFactory.MINI`. |
| `NotificationCardFactory` | Already uses factory | Case context preview uses `CaseCardFactory.MINI`, wrapped for notification layout sizing. |
| `CasePickerDialog` | Intentionally different | This is a picker list row, currently plain text rather than an entity card; migrating would alter picker density and selection behavior. |

### Task cards

| Location | Classification | Notes |
| --- | --- | --- |
| `MyShaleController` | Already uses factory | My Tasks grid/list and lane previews use `TaskCardFactory`. |
| `CaseController` | Already uses factory | Case task tab uses `TaskCardFactory.COMPACT`. |
| `UserController` | Already uses factory | Assigned task list uses `TaskCardFactory.COMPACT_FLUID`. |
| `SearchController` | Already uses factory | Global task search uses `TaskCardFactory.FULL`. |
| `CalendarController` | Already uses factory | Related task nodes use `TaskCardFactory.MINI`. |
| `CaseController#createCaseTaskActivityCard` | Intentionally different | This is an activity event row with task hyperlink metadata, not a task entity card. |

### Contact cards

| Location | Classification | Notes |
| --- | --- | --- |
| `ContactsController` | Already uses factory | Main contacts list uses `ContactCardFactory`. |
| `CaseController` | Already uses factory | Case party/contact summaries use `ContactCardFactory` inside party-specific wrappers. |
| `SearchController` | Already uses factory | Global contact search uses `ContactCardFactory`. |
| `NewIntakeController#renderPendingParties` | Slight variation | Pending intake party draft card can represent a new or existing contact and includes unsaved role/side/notes/removal controls. No existing `ContactCardFactory` variant preserves this draft workflow appearance. |
| Generic `ContactPickerDialog` rows | Intentionally different | The generic picker renders labels for arbitrary item types and is not contact-specific. |

### Organization cards

| Location | Classification | Notes |
| --- | --- | --- |
| `OrganizationsController` | Already uses factory | Main organization list uses `OrganizationCardFactory`. |
| `CaseController` | Already uses factory | Case party/organization summaries use `OrganizationCardFactory` inside party-specific wrappers. |
| `SearchController` | Already uses factory | Global organization search uses `OrganizationCardFactory`. |
| `NewIntakeController#renderPendingParties` | Slight variation | Pending intake party draft card can represent a new or existing organization and includes unsaved role/side/notes/removal controls. No existing `OrganizationCardFactory` variant preserves this draft workflow appearance. |

### User cards

| Location | Classification | Notes |
| --- | --- | --- |
| `TeamController` | Already uses factory | Main team list uses `UserCardFactory`. |
| `CaseController` | Already uses factory | Case team/assignment user cards use `UserCardFactory`. |
| `SearchController` | Already uses factory | Global user search uses `UserCardFactory`. |
| `TaskCard` | Already uses factory | Assignee previews use `UserCardFactory.MINI`. |
| `TaskDetailDialog` | Already uses factory | Assigned team section uses `UserCardFactory.MINI`. |
| `NewTaskDialog` | Already uses factory | Assigned user draft list uses `UserCardFactory.MINI`. |
| `NewCalendarEventDialog` | Already uses factory | Selected user preview uses `UserCardFactory.MINI`. |
| `AssignedUserPickerDialog` | Already uses factory | Picker candidates use `UserCardFactory.MINI` inside buttons to preserve selection behavior. |

## Migration candidates ranked from lowest to highest risk

1. Notification case preview sizing wrapper: low risk if a future `CaseCardFactory` variant owns notification sizing, but current code already uses `CaseCardFactory.MINI`; only layout wrapper code would move.
2. Related case metadata wrappers in `ContactViewController` and `OrganizationController`: low-to-medium risk; the entity card itself already uses `CaseCardFactory`, and only relationship metadata wrapping is duplicated.
3. Pending intake contact/organization party drafts in `NewIntakeController`: medium risk; these are draft workflow cards with mixed entity types, notes, role/side metadata, and remove controls.
4. Picker list rows (`CasePickerDialog`, generic `ContactPickerDialog`): medium-to-high risk; changing text rows to cards would change density, keyboard/mouse selection expectations, and dialog sizing.
5. Task activity feed cards in `CaseController`: high risk; activity rows are not task cards and preserve event-specific text, links, metadata, and chronology.

## Migration decision

No code migration was performed in this pass. The preferred areas were reviewed:

1. Global Search: already uses the entity-specific factories for case, task, contact, organization, and user results.
2. Related Cases: already use `CaseCardFactory`; duplicated wrapper code also carries relationship metadata and sizing outside the entity card.
3. Related Organizations: already use `OrganizationCardFactory` where organization cards are rendered.
4. Task preview cards: already use `TaskCardFactory` and `CaseCardFactory` for related case previews.
5. Notification cards: already use `NotificationCardFactory`, and embedded case context already uses `CaseCardFactory.MINI`.

Because every low-risk duplicate already delegates entity rendering to an existing factory, and the remaining manual surfaces are intentionally different workflow, picker, or activity rows, there was no safe migration that would preserve appearance, click behavior, sizing, spacing, navigation, animations, controller ownership, and business logic without redesigning a screen or adding a broader variant prematurely.

## Screens updated

None.

## Screens intentionally untouched

- Global Search, because entity results already use existing factories.
- Related Cases, because entity cards already use `CaseCardFactory` and wrapper metadata is screen-specific.
- Related Organizations, because organization cards already use `OrganizationCardFactory`.
- Task preview cards, because task and related case previews already use factories.
- Notification cards, because notification rows already use `NotificationCardFactory` and embedded case mini cards already use `CaseCardFactory`.
- Intake pending-party cards, picker rows, and task activity rows, because they are intentionally different workflows rather than duplicate entity cards.

## Risks and follow-up

- Moving relationship metadata wrappers into `CaseCardFactory` would risk mixing relationship-specific context into generic case rendering.
- Adding draft-party variants to contact or organization factories would require careful design because one pending card can represent unsaved data, saved entity references, and workflow actions.
- Picker dialogs should only be migrated after an explicit design decision about card-based picker density and keyboard selection behavior.
