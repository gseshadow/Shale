# Live Update Architecture

Purpose:
Real-time updates across all Shale clients.

Technology:
Azure Web PubSub

Hub:
shale

Connection flow:

Desktop
-> Azure Function negotiate endpoint
-> Receive websocket URL
-> Connect to Azure Web PubSub
-> Join tenant:{ShaleClientId}

Examples:

tenant:7

Client components:

- NegotiateClient
- LiveBusClient
- LiveEventDispatcher

Rules:

- Updates are tenant scoped.
- Events should be broadcast to tenant groups.
- Never broadcast data across tenant boundaries.
- UI should refresh through dispatcher events rather than direct controller coupling.
## Phase 6.3 Case Links, Link Types, and Entity Activity invalidations

Phase 6.3 extends the existing Azure Web PubSub live-update path; it does not add polling, a second bus, durable event storage, event replay, REST audit routes, or web/React live updates. The established path remains: a successful desktop/service mutation returns after the database work has completed, the `UiRuntimeBridge` publishes an `EntityUpdated` invalidation through `LiveBus` to the tenant group `client-{ShaleClientId}`, `LiveEventDispatcher` parses the event into `EntityUpdatedEvent`, and interested controllers reload through their normal tenant-scoped service/DAO paths.

New domain invalidation entity types:

| Entity type | Purpose | Safe patch keys |
| --- | --- | --- |
| `CaseLink` | Case Link create/update/delete/primary/reorder invalidation | `caseId`, `caseLinkId`, `externalLinkId`, `linkTypeId`, `change` |
| `CaseLinkShare` | Contact share add/update/remove invalidation | `caseId`, `caseLinkId`, `caseLinkShareId`, `contactId`, `change` |
| `LinkType` | Link Type tenant administration/global-overlay invalidation | `linkTypeId`, `change` |
| `EntityAuditActivity` | Entity Action Audit viewer invalidation | `entityActionAuditLogId`, `change` |

Allowed `change` values include `CREATED`, `UPDATED`, `DELETED`, `PRIMARY_CHANGED`, `REORDERED`, `SHARED`, `SHARE_UPDATED`, `UNSHARED`, `ACTIVATED`, `DEACTIVATED`, `OVERRIDE_RESET`, and `ACTIVITY_ADDED`. These are invalidation hints only; subscribers must not reconstruct visible records from live payloads.

Live-update payloads must never contain Link URLs, link display titles, descriptions, Case Link notes, share notes, Contact names, Contact emails, Contact phones, RowVer values, raw audit Metadata, old/new DTO snapshots, SQL text, exception messages, credentials, or tokens. The tenant id is derived from the authenticated desktop session context and remains in the established event envelope. The transport routes only to the tenant group; subscribers also reject mismatched tenant ids before triggering any reload. DAO refreshes keep explicit tenant parameters and RLS/session-context protections.

Business screen behavior:

* Case View → Links subscribes to `CaseLink`, `CaseLinkShare`, and `LinkType` invalidations. Case-specific events refresh only when `caseId` matches the open case; hidden/lazy tabs are marked stale and reload on activation. The Overview Primary Link presentation is invalidated with Case Link changes and reloaded authoritatively, including transitions to no primary link.
* Contact View subscribes to `CaseLinkShare` and reloads shared Case Links only when `contactId` matches the open contact. It does not receive Contact PII or share notes in the live event.
* Settings → Link Types subscribes to `LinkType`, remains admin-gated, and reloads the effective global/tenant overlay list through the existing Link Type administration service.
* Audit Log viewer subscribes to `EntityAuditActivity`, remains admin-only, and reloads only in All and Entity Activity modes. PHI Audit mode ignores entity-only invalidations unless a separate PHI-audit notification is introduced by another phase.

Publication occurs only after successful mutation methods return. Validation, authorization, duplicate/conflict, optimistic-lock, and rollback failures do not call the live publisher. A publishing failure after a committed mutation is logged by the existing `DesktopUiRuntimeBridge`/`LiveBus` path and must not make the UI report the committed database mutation as failed.

Domain invalidations and `EntityAuditActivity` invalidations are separate safe events: audited Case Link, Case Link Share, and Link Type mutations publish the domain invalidation needed by business screens and an audit-activity invalidation needed by the unified Audit Log viewer. Sending live notifications does not create audit rows, and Audit Viewer reloads are read-only and must not recursively generate entity-action audit records.

Controllers continue to use existing JavaFX rules: database reloads run on background executors, UI mutations happen via `Platform.runLater`, request generations reject stale results, open edit dialogs are not closed or overwritten by live events, and later manual/live refreshes can recover after a failed reload. Existing connection-loss behavior is preserved: the live client reports connectivity and reconnects by negotiating/joining the tenant group again; there is no durable replay, so currently visible affected views should use one authoritative bounded reload after reconnect rather than assuming missed event delivery.
