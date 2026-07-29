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

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.MaterialRequests')
      AND name = N'CK_MaterialRequests_Status'
)
BEGIN
    ALTER TABLE dbo.MaterialRequests
    DROP CONSTRAINT CK_MaterialRequests_Status;
END;

-- JavaFX Color.toString() historically produced 0xRRGGBBAA. Configurable
-- lookup colors use CSS #RRGGBB and do not preserve alpha.
UPDATE dbo.RequestStatuses
SET Color = UPPER(N'#' + SUBSTRING(Color, 3, 6)),
    UpdatedAt = SYSUTCDATETIME()
WHERE LEN(Color) = 10
  AND LEFT(Color, 2) IN (N'0x', N'0X')
  AND SUBSTRING(Color, 3, 8) NOT LIKE '%[^0-9A-Fa-f]%';

-- Closure semantics are resolved from RequestStatuses.SystemKey in application
-- code. This legacy name/value check would misclassify a custom display name.
IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.MaterialRequests')
      AND name = N'CK_MaterialRequests_Closure'
)
BEGIN
    ALTER TABLE dbo.MaterialRequests
    DROP CONSTRAINT CK_MaterialRequests_Closure;
END;

COMMIT TRANSACTION;
