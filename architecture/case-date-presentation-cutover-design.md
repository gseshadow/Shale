# Case Date presentation cutover design (Phase 1 decision record)

## Scope and fixed product decisions

The Phase 1 configuration foundation described here is implemented; card and Overview runtime cutover
remains deferred. Intake remains the protected
`INTAKE` workflow identity and New Intake continues to require it, but presentation configurations may
omit it. Existing New Intake form selections, My Shale SOL/TCN warnings and legacy report/export columns
remain unchanged during this phase. Active SOL and TCN semantic-role mappings must not be retired yet.

The firm owns two ordered defaults: a case-card selection and an uncustomized Case Overview selection.
A per-case Overview configuration remains an override, including a configuration with zero selection
rows (an explicit empty display). Absence of its parent `CaseOverviewConfigurations` row means “inherit
the firm Overview default”; an existing parent with no children means “show no dates.”

The foundation schema names are `dbo.CaseDatePresentationConfigurations` (one tenant-owned parent per
`CASE_CARD` or `CASE_OVERVIEW`) and `dbo.CaseDatePresentationSelections` (ordered stable identities).
Administrative replacement is audited as `CASE_DATE_PRESENTATION_CONFIGURATION`.

## Selection identity and occurrence matching

Persist a selection as an immutable **type identity**, not as a semantic role, label, or current numeric
type id:

* `SYSTEM:<normalized SystemKey>` for a type with a nonblank `SystemKey`; normalization is trim plus
  lower-case using the database-compatible invariant convention.
* `TYPE:<CaseDateTypes.Id>` for a tenant-created type with no `SystemKey`.

At write time a new selection must resolve to the active, non-deleted tenant-effective type. At read
time, `SYSTEM:k` matches an occurrence when the occurrence's stored `CaseDateTypeId` joins to any
tenant-visible global or same-tenant `CaseDateTypes` row whose normalized `SystemKey = k`. It therefore
finds occurrences still pointing to the original global SOL/TCN row after a tenant overlay is selected.
`TYPE:id` matches only that stored id and requires the type to belong to the tenant. Matching never uses
name, semantic role, or the current overlay winner.

The selected identity controls matching; the current effective winner controls the displayed type name,
color, and other presentation. If no active effective winner exists, a matching occurrence remains
readable and uses its stored historical type row for presentation. Historical/inactive identities already
stored in a per-case override remain in their saved order and render this way; they cannot be newly added
to a firm default or a case override. A deleted stored type is historical presentation, not a reset marker
for occurrence identity. An occurrence with `IsDeleted=1` never matches for display.

The core query shape is:

```sql
-- @TenantId, @CaseId and @SelectionIdentity are parameters under tenant SESSION_CONTEXT.
SELECT cd.Id, cd.StartsAt, cd.EndsAt, cd.AllDay, cd.CaseDateTypeId,
       stored.Name AS StoredTypeName, stored.Color AS StoredTypeColor
FROM dbo.CaseDates cd
JOIN dbo.CaseDateTypes stored ON stored.Id = cd.CaseDateTypeId
WHERE cd.ShaleClientId = @TenantId AND cd.CaseId = @CaseId AND cd.IsDeleted = 0
  AND ((@SelectionIdentity LIKE 'SYSTEM:%'
        AND LOWER(LTRIM(RTRIM(stored.SystemKey))) = SUBSTRING(@SelectionIdentity,8,160)
        AND (stored.ShaleClientId = @TenantId OR stored.ShaleClientId IS NULL))
    OR (@SelectionIdentity LIKE 'TYPE:%'
        AND stored.Id = TRY_CONVERT(int,SUBSTRING(@SelectionIdentity,6,20))
        AND stored.ShaleClientId = @TenantId));
```

## Initial firm defaults

Seed defaults from semantics, not from global ids. The card default is ordered Intake, SOL, then TCN, matching
the actual current card display. The Overview
default is the current uncustomized candidate order from `CaseOverviewConfigurationDao`: date of injury,
medical negligence, Intake, SOL, TCN. Its existing `defaults(...)` implementation filters that ordered
candidate list through the tenant's runtime-effective types, so unavailable optional types are omitted
and the remaining order is compacted. For each tenant, resolve the effective semantic mapping using the same
precedence as runtime (active, non-deleted tenant mapping first, otherwise the active global mapping),
join its mapped type, and store that type's stable identity. Thus a tenant semantic override seeds the
tenant meaning, while `SYSTEM:` matching still includes occurrences stored against the original global
type. Missing or ambiguous required Intake resolution and ambiguous/corrupt type state are migration
blockers. An optional candidate absent from the runtime-effective list is omitted, exactly as it is today.

Existing per-case `CaseOverviewConfigurations` parents and ordered selections are not rewritten. This
preserves customized layouts, retained inactive selections, and explicit-empty configurations. Only cases
without a parent inherit the new firm default. Before seeding, compare the proposed resolved identities
and their row-level rendered occurrence ids with current card/Overview output for every tenant.

## Multiple-occurrence display rule

For an ordinary selected identity with multiple active occurrences, display the occurrence with the
earliest `StartsAt`; break ties by lowest `CaseDates.Id`. Null is not possible for `StartsAt`. This rule is
deterministic, aligns deadline cards with the most urgent date, and avoids the current nondeterministic or
latest-value effects of `MAX`. It is a presentation rule only: no occurrence is deleted or designated
primary. Apply the identical `ROW_NUMBER() OVER (PARTITION BY CaseId, SelectionIdentity ORDER BY StartsAt,
Id)` rule in the shared projection used by cards, Overview, list sorting, My Shale warning/radar evaluation,
and any future report/export consumer that opts into display selection. The generic Dates list and Calendar
continue to show every occurrence.

## Required reader, sort, and writer cutovers before SOL/TCN mapping retirement

1. Add one tenant-scoped shared projection that resolves ordered firm defaults/per-case override identities,
   matches stored global and tenant-overlay occurrences, applies historical presentation fallback, and
   chooses the deterministic occurrence.
2. Convert `CaseSummaryDao` card/list projections and all `CaseDao` boundary-date sort/filter queries away
   from `CaseDateTypeSemanticRoleMappings`. Convert `CaseCardModel`, `CaseCardFactory`, and consumers in
   Cases, My Shale, User Detail, Contact, and Organization projections to the selected projection.
3. Convert `CaseOverviewConfigurationDao` defaults and saved numeric `CaseDateTypeId` selections to stable
   identity while retaining parent-row explicit-empty semantics. Convert `CaseController` Overview reads;
   its occurrence editors must edit the selected occurrence id/RowVer, not resolve SOL/TCN by role.
4. Preserve My Shale warning labels and behavior, but source its SOL/TCN dates and sorts from the firm card
   selections until a separately approved warning policy exists. This compatibility is required before
   mappings can retire.
5. Keep `MigratedCaseDateKey`, server/React fixed SOL/TCN detail fields, New Intake, Calendar, and generic
   Case Dates on stable type/SystemKey identity. Confirmation-policy identity follows the resulting
   tenant-effective Case Date Type (`SYSTEM:` overlay family or tenant-owned `TYPE:`), never the SOL/TCN
   semantic-role mapping. Remove any remaining
   SOL/TCN mutation that resolves through a semantic role before retirement.
6. Keep legacy report/export DTO columns and headings, but feed them from the selected deterministic card
   projection. Inventory external SQL/report consumers separately; an unknown direct mapping reader blocks
   retirement.
7. Only after all reads, sorts, warnings, and writers above are deployed and verified may a later migration
   deactivate SOL/TCN mappings. Intake mapping protection and mapping resolution remain.

## Forward migration and row-level verification

1. **Inventory only:** materialize tenant-effective mapping/type identities and proposed defaults into temp
   tables. Fail on missing/duplicate winners and invalid `TYPE:` ownership.
2. **Foundation implemented:** add `dbo.CaseDatePresentationConfigurations` and
   `dbo.CaseDatePresentationSelections` for tenant firm card/default-Overview parents and ordered selections with strict tenant
   FKs/RLS, filtered uniqueness, RowVer, and audit support. Do not touch semantic mappings.
3. **Seed:** insert one row per tenant and ordered identity in one transaction. Do not create per-case rows.
4. **Compatibility release:** dual-read configuration only (new firm defaults plus existing per-case override),
   while the underlying occurrences remain single-authority `CaseDates`. Convert the shared readers/sorts.
5. **Verification/soak:** compare every case row, including missing values and selected occurrence ids. Resolve
   every mismatch. Then convert the remaining writers and external consumers.
6. **Later approval:** retire active SOL/TCN mappings only after zero row-level findings and rollback rehearsal.

Before/after verification must retain concrete identities rather than use only checksums:

```sql
-- BEFORE deployment: expected rows from current semantic behavior.
SELECT c.ShaleClientId,c.Id CaseId,m.SemanticRoleKey,cd.Id CaseDateId,
       cd.CaseDateTypeId,cd.StartsAt,cd.AllDay
INTO #BeforePresentation
FROM dbo.Cases c
JOIN dbo.CaseDateTypeSemanticRoleMappings m
  ON (m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL)
 AND m.SemanticRoleKey IN ('STATUTE_OF_LIMITATIONS','TORT_NOTICE_DEADLINE')
 AND m.IsActive=1 AND m.IsDeleted=0
JOIN dbo.CaseDates cd ON cd.ShaleClientId=c.ShaleClientId AND cd.CaseId=c.Id
 AND cd.CaseDateTypeId=m.CaseDateTypeId AND cd.IsDeleted=0
WHERE ISNULL(c.IsDeleted,0)=0;

-- AFTER deployment: persist the shared projection output for the same cases.
SELECT ShaleClientId,CaseId,SelectionIdentity,CaseDateId,CaseDateTypeId,StartsAt,AllDay
INTO #AfterPresentation
FROM dbo.vw_CaseSelectedDatePresentation -- proposed name; final migration must use the approved object
WHERE SelectionPurpose IN ('CARD','OVERVIEW');

-- Exact row findings: both result sets must be empty after translating role to seeded identity.
SELECT b.*,'MISSING_AFTER' Finding FROM #BeforePresentation b
WHERE NOT EXISTS (SELECT 1 FROM #AfterPresentation a WHERE a.ShaleClientId=b.ShaleClientId
 AND a.CaseId=b.CaseId AND a.CaseDateId=b.CaseDateId);
SELECT a.*,'UNEXPECTED_AFTER' Finding FROM #AfterPresentation a
WHERE NOT EXISTS (SELECT 1 FROM #BeforePresentation b WHERE b.ShaleClientId=a.ShaleClientId
 AND b.CaseId=a.CaseId AND b.CaseDateId=a.CaseDateId);

-- Overlay proof: selected SYSTEM identity must find rows stored on every visible same-key type id.
SELECT s.ShaleClientId,s.SelectionIdentity,cd.CaseId,cd.Id CaseDateId,cd.CaseDateTypeId,t.ShaleClientId StoredTypeOwner
FROM dbo.FirmCaseDateSelections s
JOIN dbo.CaseDateTypes t ON (t.ShaleClientId=s.ShaleClientId OR t.ShaleClientId IS NULL)
 AND s.SelectionIdentity=CONCAT('SYSTEM:',LOWER(LTRIM(RTRIM(t.SystemKey))))
JOIN dbo.CaseDates cd ON cd.ShaleClientId=s.ShaleClientId AND cd.CaseDateTypeId=t.Id AND cd.IsDeleted=0
WHERE NOT EXISTS (SELECT 1 FROM #AfterPresentation a WHERE a.ShaleClientId=cd.ShaleClientId
 AND a.CaseId=cd.CaseId AND a.SelectionIdentity=s.SelectionIdentity);

-- Determinism proof: no returned row may differ from earliest StartsAt/Id.
SELECT a.*,'NON_DETERMINISTIC_WINNER' Finding
FROM #AfterPresentation a
WHERE EXISTS (SELECT 1 FROM dbo.CaseDates x JOIN dbo.CaseDateTypes xt ON xt.Id=x.CaseDateTypeId
 WHERE x.ShaleClientId=a.ShaleClientId AND x.CaseId=a.CaseId AND x.IsDeleted=0
 AND a.SelectionIdentity=CONCAT('SYSTEM:',LOWER(LTRIM(RTRIM(xt.SystemKey))))
 AND (x.StartsAt<a.StartsAt OR (x.StartsAt=a.StartsAt AND x.Id<a.CaseDateId)));
```

The final scripts must use the actual approved table/view names, include `SESSION_CONTEXT`/RLS verification,
and emit descriptive `FindingCount` columns plus the row details above. Rollback removes only the new firm
configuration/read path; it must not delete or rewrite occurrences, per-case overrides, or mappings.

## Audit compatibility and remaining decision

Firm-default administration is a meaningful tenant administrative mutation. It uses the
`CASE_DATE_PRESENTATION_CONFIGURATION` entity type. Its DAO must own one
transaction and append the existing entity-action audit event on the same connection, recording only
purpose and ordering count (never dates, labels, RowVer, or serialized selections). Reads and automatic
inheritance are not audited because they reveal no new sensitive value and make no mutation. Existing
per-case Overview mutation auditing remains authoritative. The existing audit schema is sufficient; the
future implementation may need an allowlist migration for the new entity type.

There is no remaining product decision blocking the confirmation fix or this design. Presentation
implementation is blocked only on approval of the concrete schema/object names and audit entity type;
SOL/TCN mapping retirement is additionally blocked until external report consumers are inventoried and
the row-level verification above is clean.

## Desktop SOL/TCN identity cutover (2026-09-25)

The desktop list boundary now treats SOL and TCN as ordinary Case Date Type families identified by
`SYSTEM:statute_of_limitations` and `SYSTEM:tort_notice_deadline`; it does not consult the card or
Overview presentation selections. The cut-over paths are the legacy `CaseDao` Cases-list pagination
boundary and the desktop `CaseSummaryDao` grid, assigned board, User Detail, deleted search, global
search, and Contact/Organization related-case projections. These set-based projections select the
earliest `StartsAt` (with `CaseDates.Id` as the paging-sort tie breaker where an occurrence row is
selected), retain established null ordering and Case-id pagination tie breakers, and require an active
tenant-effective type winner. A non-deleted tenant overlay masks the global definition even when the
overlay is inactive; a deleted overlay falls back to the global definition. Stored historical family
occurrences remain readable while the effective family is available.

Intake continues to resolve through its protected semantic role. SOL/TCN semantic mappings also remain
active because report/detail export projections, the desktop Documents compatibility lookup, server/React
fixed compatibility projections and editors, mapping administration,
and migration/verification tooling still read or manage them. Those paths were deliberately not changed
in this desktop presentation slice. The next safe boundary is a separately verified report/export and
server compatibility cutover, followed by fixed-editor review. Mapping retirement
is not safe until those readers and writers plus external SQL consumers have been inventoried, converted,
and soaked with row-level verification.

### Desktop boundary inventory

The concrete cut-over inventory is intentionally explicit so a new list surface cannot be mistaken for a completed
mapping-retirement boundary:

| Desktop behavior | Runtime boundary | SOL/TCN consumer |
| --- | --- | --- |
| Cases list sort and stable pagination | `CaseDao.findPageInternal` / `authoritativeBoundaryDateApplySql` | SOL soonest/latest and TCN soonest use the earliest family occurrence; the existing Case-id tie breaker and SQL Server null placement remain unchanged. |
| Cases grid sort, filter count, and export page | `CaseSummaryDao.findActiveGridPage`, `countActiveGrid`, `listActiveGridForExport`, and `gridSql` | The count predicate remains date-independent; the shared bounded grid projection supplies ordinary-family SOL/TCN dates without per-case reads. |
| My Shale assigned board warnings and deadline radar | `CaseSummaryDao.listActiveAssignedBoard` | Warning thresholds and labels continue to consume the projected SOL/TCN values; card presentation selections are not consulted. |
| User Detail assigned-case list | `CaseSummaryDao.listActiveAssignedForUserDetail` | The bounded set projection supplies ordinary-family SOL/TCN values. |
| Global and deleted-case search | `CaseSummaryDao.searchActiveByName` and `searchDeletedByName` | Search result cards receive ordinary-family SOL/TCN values, including readable historical occurrences while the effective type is available. |
| Contact and Organization related cases | `CaseSummaryDao.listActiveRelatedToContact`, `listActiveRelatedToOrganization`, and `listActiveRelated` | Both callers share one tenant-scoped ordinary-family projection. |

Every collection above remains one set-based statement under the established tenant session/RLS boundary. For an active
effective family, occurrences stored on either the visible global definition or the same-tenant overlay remain candidates.
An inactive, non-deleted tenant overlay masks the global definition and yields null; a deleted overlay permits global
fallback; an entirely missing optional family also yields null. Multiple values choose the earliest `StartsAt` (and the
boundary query uses lowest `CaseDates.Id` for an exact timestamp tie). Card or Overview selection changes cannot alter any
of these results.

## Desktop report, export, and Documents cutover (2026-09-26)

The desktop Case status report detail projection and its XLSX export now resolve the existing SOL and
TCN fields as the ordinary `SYSTEM:statute_of_limitations` and `SYSTEM:tort_notice_deadline` families.
The bounded Documents Case-summary lookup uses the same ordinary SOL family. Both readers apply the
desktop overlay contract: the non-deleted same-tenant definition wins over the global definition, an
inactive winner masks the global family and returns null, a deleted overlay permits global fallback,
and a missing family returns null. While the effective winner is active, occurrences stored against
any visible global or same-tenant definition in the family participate, with earliest `StartsAt` and
then lowest `CaseDates.Id` selecting the scalar value. Neither reader consults Case Card or Case
Overview selections, and neither mutates occurrences, mappings, confirmation history, or report
configuration.

The Cases XLSX/CSV export was already cut over through
`CaseSummaryDao.listActiveGridForExport` -> `findActiveGridPage` -> `gridSql`; this phase verifies and
retains that path rather than adding another export projection. Existing report DTO fields, XLSX
headings/order/date formats/null behavior, and Documents model/rendering contracts are unchanged.

After this slice, no desktop production report, document-generation, XLSX/CSV export, or report/detail
projection reads SOL or TCN through protected semantic-role mappings. Remaining SOL/TCN mapping
consumers are outside this slice: protected mapping administration and protection, migration and
verification tooling, and the server/React plus fixed desktop compatibility detail/editor paths.
Mappings therefore remain active.
