# Material Request notification integration

## Existing architecture

`dbo.Notifications` is the single persistent store. Its durable row includes tenant and recipient IDs, category, severity (`INFO`, `WARNING`, or `CRITICAL` in the current JavaFX model), title/body, typed `EntityType` plus `EntityId`, `ActionType`, read and dismissed flags/timestamps, creator, and `EventKey`. `NotificationDao.createIfAbsent` scopes lookup by tenant, recipient, and event key. The unique database index is the final concurrency guard. `NotificationServicePort` and `NotificationServiceAdapter` are the cross-module boundary for the existing task/calendar operations; desktop hydration and dismissal currently use `NotificationDao` through `DurableNotificationService`.

The notification window consumes `AppNotification`. Durable hydration preserves the entity ID, event key, severity, case context, read state, and action type. Read and dismissal only update the notification row. The existing due evaluator is `TaskDueDateNotificationGenerator`, a 30-minute scheduled worker started by `SceneManager`; it queries candidate DAOs and persists through `NotificationDao`. It is client-hosted scheduled infrastructure, not a per-entity JavaFX timer.

Task notification clicks use the typed task entity route. Calendar assignments use the typed calendar-event route. Case context routes through `SceneManager.openCaseProfile(caseId, sectionKey)`. Material requests use `EntityType=MaterialRequest`, preserve their request ID in `EntityId`, hydrate their case through the request relationship, and route to the stable `REQUESTS` section key. The current case-route lifecycle has no typed child-entity parameter, so exact detail opening is intentionally deferred rather than encoded as display text.

## Mutation inventory and transaction behavior

All production create/edit UI mutations originate in `CaseMaterialRequestsTabController`, build `CreateMaterialRequestCommand` or `UpdateMaterialRequestCommand`, and call `MaterialRequestServicePort`. `MaterialRequestServiceAdapter` delegates to `MaterialRequestDao`. Commands carry `actorUserId` from `AppState` through every layer. There are only two request mutation implementations: `MaterialRequestDao.create` and `MaterialRequestDao.update`.

Both DAO mutations explicitly begin a JDBC transaction, validate tenant/case/users/lookups, write the request, touch `Cases.UpdatedAt`, append PHI/entity-action audit entries, and commit; failures roll back. Recipient notifications are inserted through a connection-aware `NotificationDao` method on that same transaction. Therefore a failed mutation cannot commit a notification. Existing row-version concurrency and closure normalization remain unchanged.

Create compares both submitted role IDs against no prior selections. Update reads persisted `RequestedByUserId` and `AssignedToUserId` before writing and compares those values to the submitted IDs. Only changed, newly selected, non-actor users are collected in a set. A recipient selected into both roles gets one combined notification. Immediate event keys contain request, mutation occurrence, and recipient, while still using the existing tenant/user/EventKey deduplication representation.

## Due dates

The existing Material Request schema names its user-facing due date `ExpectedResponseDate` (a SQL `date`), rather than `DueAt`; this integration treats that established field as the requested due occurrence and does not introduce a duplicate column. The existing scheduled evaluator now also asks `MaterialRequestDao` for due candidates. The query excludes deleted rows, cleared due dates, and effective statuses whose resolved `RequestStatuses.SystemKey` is `closed` or `cancelled`. It never compares customizable status display text to terminal literals except to resolve the stored value back to a SystemKey.

The current assignee is preferred; requester is the fallback. With no valid recipient the evaluator skips the row. (The current schema requires a requester, but the null-safe rule remains explicit.) The occurrence key is `material-request:{requestId}:due:{dueDate}:{recipientId}`. Repeated runs deduplicate; changing the date produces a new occurrence; clearing it removes eligibility. Dismissal changes only `Notifications` and cannot alter the request due date.

## Next phase: FollowUpIntervalDays

Do not overload the due occurrence for follow-ups. A follow-up notification should remain a `MaterialRequest` entity notification with a distinct action/occurrence type (for example `FOLLOW_UP_DUE`) and an event key containing request ID, exact `NextFollowUpAt`, and recipient. Case ID is recoverable through the typed entity hydration, while request ID, action type, recipient, and event key are already durable.

To advance `NextFollowUpAt` from dismissal time transactionally, add a dismissal command carrying the expected durable notification ID/event key and implement a connection-aware notification-dismiss plus optimistic Material Request update in the data/service boundary. The present asynchronous UI dismissal API is not sufficient for that atomic cross-entity operation. No decisions in this phase prevent this extension; ordinary due dismissal deliberately remains notification-only.

## Future critical presentation

Severity already exists end-to-end and includes `CRITICAL`; these notifications use `INFO`. A future critical popup needs no new notification schema or producer. It should filter the same hydrated notification collection by `CRITICAL` (and define acknowledgement/presentation state only if product requirements differ from read/dismissed), rather than create another notification framework.
