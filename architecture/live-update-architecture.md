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

Allowed `change` values include `CREATED`, `UPDATED`, `DELETED`, `PRIMARY_CHANGED`, `REORDERED`, `ADDED`, `UPDATED`, `REMOVED`, `ACTIVATED`, `DEACTIVATED`, `OVERRIDE_RESET`, and `ACTIVITY_ADDED`. These are invalidation hints only; subscribers must not reconstruct visible records from live payloads.

Live-update payloads must never contain Link URLs, link display titles, descriptions, Case Link notes, share notes, Contact names, Contact emails, Contact phones, RowVer values, raw audit Metadata, old/new DTO snapshots, SQL text, exception messages, credentials, or tokens. The tenant id is derived from the authenticated desktop session context and remains in the established event envelope. The transport routes only to the tenant group; subscribers also reject mismatched tenant ids before triggering any reload. DAO refreshes keep explicit tenant parameters and RLS/session-context protections.

Business screen behavior:

* Case View → Links subscribes to `CaseLink`, `CaseLinkShare`, and `LinkType` invalidations. Case-specific events refresh only when `caseId` matches the open case; hidden/lazy tabs are marked stale and reload on activation. The Overview Primary Link presentation is invalidated with Case Link changes and reloaded authoritatively, including transitions to no primary link.
* Contact View subscribes to `CaseLinkShare` and reloads shared Case Links only when `contactId` matches the open contact. Case Link create/update flows publish one `CaseLinkShare` invalidation per committed add, update, or removal using the staged command change set and committed result/original share identifiers at the mutation boundary. It does not receive Contact PII or share notes in the live event.
* Settings → Link Types subscribes to `LinkType`, remains admin-gated, and reloads the effective global/tenant overlay list through the existing Link Type administration service.
* Audit Log viewer subscribes to `EntityAuditActivity`, remains admin-only, and reloads only in All and Entity Activity modes. PHI Audit mode ignores entity-only invalidations unless a separate PHI-audit notification is introduced by another phase.

Publication occurs only after successful mutation methods return. Validation, authorization, duplicate/conflict, optimistic-lock, and rollback failures do not call the live publisher. A publishing failure after a committed mutation is logged by the existing `DesktopUiRuntimeBridge`/`LiveBus` path and must not make the UI report the committed database mutation as failed.

Domain invalidations and `EntityAuditActivity` invalidations are separate safe events: audited Case Link, Case Link Share, and Link Type mutations publish the domain invalidation needed by business screens and an audit-activity invalidation needed by the unified Audit Log viewer. Sending live notifications does not create audit rows, and Audit Viewer reloads are read-only and must not recursively generate entity-action audit records.

Controllers continue to use existing JavaFX rules: database reloads run on background executors, UI mutations happen via `Platform.runLater`, request generations reject stale results, open edit dialogs are not closed or overwritten by live events, and later manual/live refreshes can recover after a failed reload. Existing connection-loss behavior is preserved: the live client reports connectivity and reconnects by negotiating/joining the tenant group again; there is no durable replay, so currently visible affected views should use one authoritative bounded reload after reconnect rather than assuming missed event delivery.

## Case Dates cross-instance invalidation

Desktop existing-case Case Date mutations use the established tenant LiveBus envelope with
`type=EntityUpdated`, `entityType=CaseDates`, `entityId=CaseId`, a UUID `eventId`,
`shaleClientId`, `updatedByUserId`, `clientInstanceId`, and a patch containing only
`caseId` and `change` (`CREATED`, `UPDATED`, `REMOVED`, or `ADDED`). The contract must
not contain occurrence values, names, type labels, descriptions, notes, row versions, or
other PHI. It is an invalidation, never a data patch.

`CaseController` publishes through its single `publishCaseDatesChanged` boundary only
after a generic create/update/remove/restore or the nine-slot aggregate service call has
returned successfully. Those service calls return after their DAO transaction commits;
validation, stale-row-version, rollback, and other failures never reach publication. An
aggregate command whose nine intents are all `Unchanged` does not call the mutation or
publisher. Publication failure cannot roll back or misreport the already committed save.

The controller owns the subscription for the attached Case View and unsubscribes with its
existing scene lifecycle. It rejects another tenant, another case, its own
`clientInstanceId`, and duplicate/replayed `eventId` values. Bursts are coalesced on the FX
queue. Receipt performs no mutation: background workers reload both the generic Dates
collection and the complete `AuthoritativeCaseDateEditor` snapshot. Only the FX thread
applies results, and existing case-id, request-generation, and attached-scene checks keep
case switches and older responses from replacing newer state. The snapshot replaces the
Cases row version plus all nine occurrence ids, CaseDates row versions, values, and
expected-absence witnesses coherently; live payloads never carry or synthesize them.

A remote invalidation arriving while a Case Date dialog or save is active is deferred. The
open dialog input is not repainted, closed, retried, or converted to last-write-wins. After
the edit/save completes, one authoritative reconciliation runs; if the submitted tokens
were genuinely stale, the established conflict message and explicit authoritative reload
remain in force. The initiating instance applies the mutation result/local synchronization
and ignores its echoed instance id, preventing a refresh or mutation loop.

All generic CaseDates changes invalidate the generic Dates section. Because the PHI-safe
contract intentionally omits the affected SystemKey, every remote event also reloads the
complete nine-slot snapshot; non-migrated occurrences therefore cannot populate a fixed
field. Reconnect duplicates are deduplicated and remain read-only. This adds no polling,
new socket, durable replay, legacy `dbo.Cases` date read/write, or dual-write. The next
cutover gate remains atomic new-case intake; API/web/new-case paths are not partially
converted here.
