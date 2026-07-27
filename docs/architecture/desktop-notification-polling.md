# Desktop notification polling and privacy boundary

## Lifecycle and backlog policy

`NotificationPollingService` is owned by `SceneManager`. It starts only from the authenticated main-scene path, stops before the login scene is shown, and closes during JavaFX application shutdown. Each start creates a tenant/user generation. Scheduled and UI callbacks verify that generation, so logout or session replacement invalidates cursors, presentation deduplication, and pending results. A named single-thread daemon scheduler prevents overlapping retrievals and cannot keep the JVM alive.

Existing unread hydration remains the authoritative startup population of the in-app notification center. Polling first asks the authenticated service for the current maximum durable notification ID and turns that ID into the Phase 0 opaque cursor. That constant-time, tenant/user-scoped high-water operation avoids paging through unbounded history and does not rely on `CreatedAt`. Rows above the baseline are session-locally eligible for desktop presentation. Application restart deliberately establishes a new baseline; durable device delivery/presentation acknowledgement is outside this increment.

The cursor belongs only to the active tenant/user generation. Pages advance in ascending durable ID order. Empty pages do not clear the center. Invalid, regressing, or malformed cursor pages and authorization failures stop the lifecycle fail-closed. Other runtime failures receive exponential retry delay starting at five seconds, capped at five minutes, with an injectable 20-percent jitter seam. Success resets failure count and restores the conservative 60-second polling interval.

## Reconciliation and privacy

Cursor retrieval is read-only: it does not update read, dismissed, delivered, or presented state and creates no audit event. `NotificationCenterService` remains the sole in-app collection and already deduplicates durable IDs and event keys without replacing existing objects, thereby preserving newer local read state and listeners. Category suppression is applied by the existing preferences mapper before reconciliation or presentation.

`NotificationPrivacyProjector` is the only conversion path to `DesktopNotificationPresenter`. The presenter accepts `NativeNotificationPresentation`, not `AppNotification` or a service/DAO DTO. Its allowlisted fields are durable notification ID, the generic `Shale` heading, generic projected message, and an allowlisted category code. It cannot carry durable title/body, entity title, case/client/contact/organization names, task/request titles, descriptions, notes, or serialized internal objects.

Production uses `NoOpDesktopNotificationPresenter`, which deterministically returns `UNSUPPORTED`, shows nothing, writes no notification state, and emits no per-notification log. Operational polling logs are restricted to tenant/user IDs, counts/retry timing, notification IDs, allowlisted categories, and stable result codes; exception messages and notification content are excluded. Polling, reconciliation, projection, and presentation are read-only infrastructure and intentionally create no legal entity-action audit rows.

## Deferred platform work

Native Windows/macOS presentation, click activation, second-instance routing, tray/menu-bar behavior, packaging/signing, device registration, presentation acknowledgements, quiet hours, and detailed-content preferences remain out of scope. The established updater launcher still identifies Windows as the production-complete desktop path while the macOS packaging guide describes a first-pass path with launcher work remaining. The next adapter should therefore be a bounded Windows notification implementation consuming only `NativeNotificationPresentation`.
