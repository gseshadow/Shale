/*
  Material Request customizable-status compatibility.
  Forward-only and safe to re-run. MaterialRequests.Status remains nvarchar(32)
  NOT NULL; effective RequestStatuses validation is application-enforced.
*/
SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- DRAFT was part of the original MaterialRequests contract but was omitted
-- from the visible RequestStatuses seed. Keep it as an ordinary visible
-- global built-in rather than a hidden application-only value.
IF NOT EXISTS (
    SELECT 1 FROM dbo.RequestStatuses
    WHERE ShaleClientId IS NULL AND LOWER(LTRIM(RTRIM(SystemKey))) = 'draft'
)
BEGIN
    INSERT dbo.RequestStatuses
        (ShaleClientId, SystemKey, Name, Color, SortOrder, IsActive, IsDeleted,
         CreatedAt, UpdatedAt)
    VALUES
        (NULL, 'draft', N'Draft', '#64748B', 0, 1, 0,
         SYSUTCDATETIME(), SYSUTCDATETIME());
END;

-- Do not rely on constraint names here. Restores and older deployments can
-- retain a generated/renamed constraint name, so recognize only the two
-- obsolete checks by their definitions. In particular, leave unrelated
-- MaterialRequests checks (method, source, dates, and so on) alone.
DECLARE @LegacyConstraintName sysname;
DECLARE legacy_material_request_checks CURSOR LOCAL FAST_FORWARD FOR
SELECT cc.name
FROM sys.check_constraints cc
WHERE cc.parent_object_id = OBJECT_ID(N'dbo.MaterialRequests', N'U')
  AND (
      -- Original fixed status allowlist.
      (cc.definition LIKE N'%[[]Status[]]%'
       AND cc.definition LIKE N'%DRAFT%'
       AND cc.definition LIKE N'%REQUESTED%'
       AND cc.definition LIKE N'%FOLLOW_UP_DUE%'
       AND cc.definition LIKE N'%PARTIALLY_RECEIVED%'
       AND cc.definition LIKE N'%FULLY_RECEIVED%'
       AND cc.definition LIKE N'%CLOSED%'
       AND cc.definition LIKE N'%CANCELLED%')
      OR
      -- Original status-dependent closure check.
      (cc.definition LIKE N'%[[]Status[]]%'
       AND cc.definition LIKE N'%[[]ClosedAt[]]%'
       AND cc.definition LIKE N'%[[]ClosedByUserId[]]%'
       AND cc.definition LIKE N'%[[]ClosureReason[]]%'
       AND cc.definition LIKE N'%CLOSED%'
       AND cc.definition LIKE N'%CANCELLED%')
  );

OPEN legacy_material_request_checks;
FETCH NEXT FROM legacy_material_request_checks INTO @LegacyConstraintName;
WHILE @@FETCH_STATUS = 0
BEGIN
    EXEC sys.sp_executesql
        N'ALTER TABLE dbo.MaterialRequests DROP CONSTRAINT '
        + QUOTENAME(@LegacyConstraintName) + N';';
    FETCH NEXT FROM legacy_material_request_checks INTO @LegacyConstraintName;
END;
CLOSE legacy_material_request_checks;
DEALLOCATE legacy_material_request_checks;

-- JavaFX Color.toString() historically produced 0xRRGGBBAA. Configurable
-- lookup colors use CSS #RRGGBB and do not preserve alpha.
UPDATE dbo.RequestStatuses
SET Color = UPPER(N'#' + SUBSTRING(Color, 3, 6)),
    UpdatedAt = SYSUTCDATETIME()
WHERE LEN(Color) = 10
  AND LEFT(Color, 2) IN (N'0x', N'0X')
  AND SUBSTRING(Color, 3, 8) NOT LIKE '%[^0-9A-Fa-f]%';

-- Closure semantics are resolved from RequestStatuses.SystemKey in application
-- code. The legacy name/value check was removed above because it would
-- misclassify a custom display name.

COMMIT TRANSACTION;
