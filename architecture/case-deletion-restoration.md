# Case deletion and restoration audit contract

`Cases.IsDeleted` is a reversible lifecycle state, not a cascade operation. The desktop delete and
restore UI paths terminate at `CaseDetailService` and the authoritative `CaseDao` transaction. The
DAO obtains the actor exclusively from the authenticated `PrincipalUserId` database session context
and verifies both actor and case against the session tenant.

Every successful state transition atomically updates the case using its `RowVer`, appends one
`EntityActionAuditLog` row, and appends one `CaseTimelineEvents` row. The stable vocabulary is
`CASE / DELETED` with timeline type `CASE_DELETED`, and `CASE / RESTORED` with timeline type
`CASE_RESTORED`. Both records use the same UTC occurrence time and authoritative case, tenant, and
actor identifiers. Audit metadata is restricted to `CASE_ID`; names, descriptions, notes, and other
PHI are prohibited.

Timeline history is append-only during these operations. Reads join timeline rows to case identity
and tenant, not active/deleted state, so lifecycle events remain queryable while deleted and all
preserved events are visible when restored. A missing, cross-tenant, already-transitioned, or stale
case changes no rows and writes no audit or timeline record. Any audit/timeline failure rolls back the
case transition. No historical lifecycle records are synthesized.
