# Case Materials Architecture

*Phase 0 status: architecture discovery and design only. Do not create production migrations or application code from this document without a later implementation phase.*

## Established architecture discovered

Case Materials must extend Shale's existing audit architecture rather than invent a second framework.

* PHI field auditing is field-oriented. `PhiFieldRegistry` is the source of truth for PHI-bearing table/field pairs, and `PhiAuditService` writes CREATE/UPDATE/DELETE field changes for registered fields into `dbo.AuditLog` through `AuditLogDao`.
* PHI read/view auditing already exists at screen/section intent level through `PhiReadAuditService`; it writes `action=READ;screen=...` rows to `dbo.AuditLog` with a short dedupe window. It is not field-render-level telemetry.
* Non-PHI entity-action auditing is append-only in `dbo.EntityActionAuditLog`. `EntityActionAuditDao.append(Connection, EntityActionAuditEvent)` inserts tenant, actor, entity, action, parent context, source, correlation id, and strictly allowlisted metadata on the caller's SQL connection.
* Entity-action audit entities/actions/metadata are compile-time allowlists in `EntityActionAuditEvent`. Current allowed entity types are `LINK_TYPE`, `CASE_LINK`, and `CASE_LINK_SHARE`; current actions include create/update/delete, activation/deactivation, primary/reorder, add/remove, and override/reset semantics.
* Entity-action metadata may contain only stable IDs and non-sensitive state markers. Existing guards reject or ignore URL, description, note, name, email, phone, credential/password/token, RowVer, SQL, exception, command, and DTO-like content.
* The audit table is strict tenant-owned: non-null `ShaleClientId`, `ActorUserId`, indexes by tenant/time, tenant/entity, tenant/actor, tenant/parent, and RLS through `sec.fn_FilterByTenant(ShaleClientId)`. It is not overlay/global data.
* Current Case Link and Link Type mutations audit through the DAO-owned transaction seam in `CaseDao`: the business row change and entity-action audit insert use the same `Connection` before commit. If the audit insert fails, the mutation must roll back.
* Audit viewer reads are tenant-scoped, admin-only, read-only, bounded, and keep PHI Audit rows separate from Entity Activity rows rather than projecting entity actions into fake field changes.
* Settings Link Type administration is audited through entity-action events for tenant custom/override creation, updates, activation/deactivation, override reset, and removal. Global defaults are not directly mutated by tenant Settings.
* Case Timeline and Task Timeline entries are operational chronology records. They are not substitutes for security/compliance audit entries.

### Audit gaps and ambiguities for later phases

* `EntityActionAuditEvent.EntityType`, SQL check constraints, migration contract tests, viewer metadata rendering, and live-update audit invalidations must all be extended before Case Materials entity-action writes can ship.
* `PhiAuditService.OBJECT_TYPE_IDS` and `PhiReadAuditService.OBJECT_TYPE_IDS` do not yet contain Material Request, Material Follow-up, Material Item, Material Type, or future custody event object types.
* Existing PHI write audit stores field values in `dbo.AuditLog.StringValue`. For Case Materials, later phases must decide which fields may be audited with raw values, which require redacted markers only, and whether any new PHI fields should avoid old/new raw value storage.
* Sensitive read coverage exists for PHI views, but entity-action audit currently focuses on mutations. Later phases need an explicit seam for sensitive opening/downloading of electronic materials that preserves actor, tenant, case, item, source, and non-sensitive metadata without recording URL, filename content if sensitive, tokens, or document text.
* Case Timeline activity creation and `Cases.UpdatedAt` touching are domain concerns and must be transactionally coordinated with Material mutations, but they must not be used to satisfy audit requirements.

## Domain boundaries

Case Materials is a case-owned operational area for tracking efforts to obtain materials and items the firm receives or holds. It is not a file-storage subsystem, not an external credential vault, not a replacement for Contacts/Organizations, not a replacement for Tasks/Calendar reminders, and not the immutable security/compliance audit log.

Primary concepts:

1. **Material Request**: an effort to obtain outside material for a case.
2. **Material Item**: material actually received or held by the firm; may be linked to a request or unsolicited.

Supporting concepts:

* **Material Type**: global-plus-tenant overlay lookup such as Medical records, Billing records, Police report, Photographs, Other.
* **Material Format**: separate format lookup or constrained value such as electronic file, paper, CD/DVD, email, portal access, physical object. Format must not be collapsed into Material Type.
* **Material Request Follow-up**: append-only operational history of follow-up attempts.
* **Future Material Custody Event**: append-only history of physical custody/location transfers, release, return, destruction, and chain-of-custody notes.

## Proposed schema-level design (no Phase 0 migration)

### dbo.MaterialTypes

Global-plus-tenant overlay lookup. Use `ShaleClientId NULL` for global defaults and tenant id for overrides/custom rows.

Recommended columns: `Id`, nullable `ShaleClientId`, `SystemKey`, `Name`, `Description`, `Color`, `SortOrder`, `IsActive`, `IsDeleted`, `CreatedAt`, `CreatedByUserId`, `UpdatedAt`, `UpdatedByUserId`, `RowVer`.

Indexes/policies: filtered unique `SystemKey` per scope, active list index by `(ShaleClientId, IsDeleted, IsActive, SortOrder, Name)`, and overlay RLS through the established global-or-current-tenant predicate if present. Effective service reads must prefer current-tenant rows over global rows with the same non-null `SystemKey` and include tenant custom rows.

### dbo.MaterialFormats

Deferred. Phase 1 may use a constrained application enum or create an overlay lookup only if Settings administration is in scope. Keep as a separate FK/value from Material Type.

### dbo.MaterialRequests

Strict tenant-owned aggregate root under Case.

Recommended columns: `Id`, `ShaleClientId`, `CaseId`, `MaterialTypeId`, optional `Title`, `Description`, `RequestedByUserId`, `AssignedToUserId`, `RequestedFromContactId`, `RequestedFromOrganizationId`, `RequestedFromText`, `RequestMethod`, `RequestedAt`, `RelevantStartDate`, `RelevantEndDate`, `Status`, `ExpectedResponseDate`, `NextFollowUpAt`, `FirstReceivedAt`, `FullyReceivedAt`, `ClosedAt`, `ClosedByUserId`, `ClosureReason`, `Notes`, `IsDeleted`, `DeletedAt`, `DeletedByUserId`, `CreatedAt`, `CreatedByUserId`, `UpdatedAt`, `UpdatedByUserId`, `RowVer`.

Rules: tenant must match Case and actor users. Requested-from should prefer Contacts/Organizations with same tenant and allow controlled free-text fallback only when neither entity exists. `NextFollowUpAt` is a current scheduling convenience; previous follow-ups live in append-only `MaterialRequestFollowUps`.

### dbo.MaterialRequestFollowUps

Append-only operational history under Material Request.

Recommended columns: `Id`, `ShaleClientId`, `MaterialRequestId`, `CaseId`, `AttemptedAt`, `AttemptedByUserId`, `Method`, `Outcome`, `NextFollowUpAt`, `Notes`, `CreatedAt`, `CreatedByUserId`, `RowVer`.

Rules: no ordinary update/delete path. Corrections should be additive. A follow-up may update the parent request's `NextFollowUpAt` and touch the Case in the same transaction.

### dbo.MaterialItems

Strict tenant-owned aggregate root under Case, optionally linked to a Material Request.

Recommended columns: `Id`, `ShaleClientId`, `CaseId`, nullable `MaterialRequestId`, `MaterialTypeId`, `Format`, `Name`, `Description`, `SourceContactId`, `SourceOrganizationId`, `SourceText`, `ReceivedByUserId`, `ReceivedAt`, `RelevantStartDate`, `RelevantEndDate`, `Completeness`, `QuantityCount`, `PageCount`, `FileCount`, `StorageLocation`, nullable `ExternalLinkId`, `PhysicalCondition`, `CustodyStatus`, `ReturnedOrReleasedAt`, `ReturnedOrReleasedToContactId`, `ReturnedOrReleasedToOrganizationId`, `ReturnedOrReleasedToText`, `ReturnReleaseMethod`, `ReturnReleaseNotes`, `IsDeleted`, `DeletedAt`, `DeletedByUserId`, `CreatedAt`, `CreatedByUserId`, `UpdatedAt`, `UpdatedByUserId`, `RowVer`.

Rules: if linked to a request, the request and item must share tenant and case. External electronic storage should reuse `dbo.ExternalLinks`/`dbo.CaseLinks` for URLs or provider links; do not duplicate URL storage or store credentials/tokens. Storage location is a descriptive internal locator, not a file blob or credential.

### Future dbo.MaterialCustodyEvents

Append-only strict tenant-owned events under Material Item. They should record custody/location status transitions, transfer actor, transfer recipient, event time, safe notes, and parent Case/Item IDs. Ordinary item deletion must not delete custody history.

## Relationships and ownership

* Case owns Material Requests and Material Items.
* One Material Request may produce many Material Items.
* Material Items may be unsolicited (`MaterialRequestId NULL`).
* Follow-ups belong to a Material Request and are append-only.
* Future custody events belong to a Material Item and are append-only.
* Contacts and Organizations are reused for requested-from, source, and release/return recipients. Free-text fallback is controlled and sanitized.
* External Links/Case Links are reused for electronic storage references when a provider URL or externally hosted material is involved.

## Lifecycle and statuses

Request statuses should be a constrained set in Phase 1/2 until administration requirements are clear: Draft, Requested, Follow-up Due, Partially Received, Fully Received, Closed, Cancelled. Status transitions that materially change the request must be audited and may create Case Timeline activity.

Item statuses/completeness should distinguish possession from completeness: Complete, Partial, Unknown, Duplicate, Unusable, Superseded. Custody status should be separate: In firm custody, With reviewer, Returned, Released, Destroyed, Unknown.

Soft deletion means removed from ordinary operational views, not destruction of audit, follow-up, or custody history. Audit and custody history must never be destroyed by ordinary feature deletion.

## Tenant behavior, RLS, and overlay

* `MaterialRequests`, `MaterialRequestFollowUps`, `MaterialItems`, and future custody events are strict tenant-owned and use `sec.fn_FilterByTenant(ShaleClientId)` plus explicit tenant predicates in DAO queries.
* `MaterialTypes` follows the established overlay model: current tenant sees global defaults and current-tenant rows, never other tenants' rows; same `SystemKey` tenant row overrides the global default.
* Runtime connections must have `SESSION_CONTEXT(N'ShaleClientId')` initialized before tenant-protected reads/writes.
* Services must validate tenant compatibility for Case, Contact, Organization, Users, ExternalLink/CaseLink, Material Request, Material Item, and Material Type references before mutation.

## PHI and sensitive-data treatment

Likely PHI/sensitive fields include request title/description/notes, requested-from free text, relevant date range, follow-up notes/outcomes, item name/description/source text, storage location, physical condition, release notes, and future custody notes. Phase 1/2 must register appropriate fields in `PhiFieldRegistry`, extend object type mappings, and add focused tests. Audit metadata must use IDs, state codes, counts, and booleans only; it must not contain raw PHI, notes, URLs, credentials, RowVer bytes, file contents, raw DTOs, SQL, or exception text.

Sensitive views/reads include opening Case Materials tab, viewing request/item detail, viewing follow-up history, viewing custody history, opening/downloading electronic material, opening an associated External Link from an item, viewing storage location details, and viewing release/return details. These should use the established PHI read audit model or a documented scoped extension when entity-action read semantics are required.

## Audit and Case Timeline action matrix

| Action | Audit | Case Timeline | Notes |
| --- | --- | --- | --- |
| View Case Materials list/detail | PHI read audit | No | Sensitive access, not domain chronology. |
| Create Material Request | Entity-action audit and PHI field audit for registered fields | Yes | Operational event visible on Case. |
| Update material request fields | Entity-action audit and PHI field audit as applicable | Usually yes for meaningful changes | Minor typo edits may be audit-only if no timeline value. |
| Change request status/close/reopen/cancel | Entity-action audit | Yes | Include case parent context, no raw notes. |
| Add follow-up attempt | Entity-action audit and PHI field audit for notes/outcome if registered | Yes | Append-only operational history. |
| Change next follow-up date without attempt | Entity-action audit | Optional | Calendar/task reminder changes may create task activity instead. |
| Create Material Item | Entity-action audit and PHI field audit for registered fields | Yes | Link to request if applicable. |
| View/open/download electronic item | Sensitive read/open audit | No | Do not log URLs/tokens/file contents. |
| Link/unlink request and item | Entity-action audit | Yes when meaningful | Preserve request/item/case IDs. |
| Update item location/condition/completeness/custody status | Entity-action audit and PHI field audit as applicable | Yes for material custody/completeness changes | Future custody event is operational history, not audit substitute. |
| Release/return/destroy item | Entity-action audit and PHI field audit as applicable | Yes | Future custody event also required. |
| Soft-delete request/item | Entity-action audit | Yes | Do not delete follow-up/custody/audit rows. |
| Material Type create/customize/activate/deactivate/reset/remove | Entity-action audit | No | Settings/admin action, no Case Timeline. |
| View Material Type Settings | No by default | No | Admin lookup read only; audit only if future policy marks it sensitive. |

## Service boundaries and integrations

Case Materials should follow the existing service-port pattern: UI/API -> service port/adapter -> DAO/service boundary -> SQL. DAO-owned aggregate save methods should own tenant validation, row-version predicates, `Cases.UpdatedAt` touches, Case Timeline creation, PHI audit calls, entity-action audit calls, and commit/rollback.

Task/calendar integration should reuse Shale's task and calendar infrastructure. Follow-up reminders should create or relate Tasks and appear in Calendar through task due-date projection rather than introducing a separate reminder engine or duplicating due dates into `CalendarEvents`.

Case Timeline entries should be concise operational summaries with links/IDs and no unnecessary raw PHI. They are user-facing chronology and not immutable compliance evidence.

## Concurrency and deletion

Material Request and Material Item updates should use `RowVer` optimistic concurrency. Soft-delete rows with `IsDeleted`, `DeletedAt`, and `DeletedByUserId`; exclude deleted rows from ordinary lists. Follow-up and future custody rows are append-only and should not expose ordinary edit/delete paths. Meaningful mutations should touch `Cases.UpdatedAt` in the same transaction, following existing Case/task/link conventions.

## Phase 1 recommended boundary

Phase 1 should implement only database foundation and audit registration work needed to safely support later application code:

1. Add proposed SQL migration for `dbo.MaterialTypes` as global-plus-tenant overlay, with `SystemKey`, active/deleted fields, actor fields, `UpdatedAt`, `RowVer`, filtered unique indexes per tenant/global scope, effective-list indexes, and existing overlay RLS/policy integration.
2. Add proposed SQL migration for strict tenant-owned `dbo.MaterialRequests`, `dbo.MaterialRequestFollowUps`, and `dbo.MaterialItems` with keys, tenant columns, Case FKs, actor FKs, soft-delete fields where mutable, `UpdatedAt`, `RowVer`, and strict `sec.fn_FilterByTenant` RLS.
3. Add declarative FKs where verified: Cases, Users, MaterialTypes, MaterialRequests, ExternalLinks, Contacts, Organizations. Where global MaterialTypes prevent composite tenant FKs, add service-layer tenant/global validation and document it in migration comments.
4. Add indexes: request list by `(ShaleClientId, CaseId, IsDeleted, Status, NextFollowUpAt)`, request assignment by `(ShaleClientId, AssignedToUserId, Status, NextFollowUpAt)`, follow-up history by `(ShaleClientId, MaterialRequestId, AttemptedAt DESC, Id DESC)`, item list by `(ShaleClientId, CaseId, IsDeleted, ReceivedAt DESC)`, item request lookup by `(ShaleClientId, MaterialRequestId, IsDeleted)`, item external-link lookup by `(ShaleClientId, ExternalLinkId)`, and material-type effective list indexes.
5. Extend `EntityActionAuditEvent` allowlists, SQL check constraints, viewer metadata allowlist, and tests for `MATERIAL_TYPE`, `MATERIAL_REQUEST`, `MATERIAL_REQUEST_FOLLOW_UP`, and `MATERIAL_ITEM` actions/metadata before enabling writes.
6. Register PHI fields and object type mappings for Case Materials and add focused PHI write/read audit tests.
7. Add RLS verification SQL for tenant 7/tenant 8 visibility and global Material Type overlay visibility.

Deferred decisions: file/blob storage, external provider credentials, Material Format administration, detailed custody event schema, OCR/document indexing, web/API endpoints, live updates, timeline wording, task reminder UX, bulk import, retention/destruction legal holds, and whether some PHI fields require redacted-only audit values instead of raw old/new values.

Blocking conflict before Phase 1: Case Materials cannot ship audited entity-action writes until the current entity-action audit allowlists and SQL check constraints are expanded. Sensitive electronic open/download audit also needs either a documented use of PHI read audit or a scoped entity read/open audit extension.
