/*
  Cases page-zero execution-plan comparison (read only).

  In SSMS, connect through the same approved identity/database and establish the same
  ShaleClientId/PrincipalEmail SESSION_CONTEXT used by the application. Do not bypass RLS.
  Enable Query > Include Actual Execution Plan (Ctrl+M) before executing this file.
  Do not share the Results grid: CaseId values are returned only to keep paging/order observable.

  @IncludeClosedDenied documents the restored UI baseline. The restored CaseDao SQL does not
  turn that value into an additional predicate; current-status resolution is retained below
  exactly for otherwise-equivalent plan comparison.
*/
SET NOCOUNT ON;
SET STATISTICS IO ON;
SET STATISTICS TIME ON;

DECLARE @ShaleClientId int = 7;
DECLARE @ResponsibleAttorneyRoleId int = 4; -- RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY.
DECLARE @IncludeClosedDenied bit = 0;
DECLARE @RestrictToUserId int = NULL;        -- NULL is the restored Cases screen path.
DECLARE @Offset int = 0;
DECLARE @PageSize int = 100;

SELECT @@VERSION AS SqlServerVersion;
SELECT name AS DatabaseName, compatibility_level AS CompatibilityLevel
FROM sys.databases
WHERE database_id = DB_ID();

/* 1. RESTORED: correlated authoritative four-key OUTER APPLY used by INTAKE_NEWEST. */
SELECT c.Id AS CaseId, migrated.IntakeDate AS SortDate
FROM dbo.Cases c
LEFT JOIN dbo.PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
             cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id AND cu.RoleId = @ResponsibleAttorneyRoleId AND cu.IsPrimary = 1
    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
) ra
LEFT JOIN dbo.Users u ON u.id = ra.UserId
OUTER APPLY (
    SELECT MAX(CASE WHEN type_key.SystemKey = 'intake' THEN cd.StartsAt END) AS IntakeDate,
           MAX(CASE WHEN type_key.SystemKey = 'date_of_injury' THEN cd.StartsAt END) AS IncidentDate,
           MAX(CASE WHEN type_key.SystemKey = 'statute_of_limitations' THEN cd.StartsAt END) AS StatuteDate,
           MAX(CASE WHEN type_key.SystemKey = 'tort_notice_deadline' THEN cd.StartsAt END) AS TortDate
    FROM dbo.CaseDates cd
    INNER JOIN dbo.CaseDateTypes type_key ON type_key.Id = cd.CaseDateTypeId
      AND (type_key.ShaleClientId = c.ShaleClientId OR type_key.ShaleClientId IS NULL)
    WHERE cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId AND cd.IsDeleted = 0
      AND type_key.SystemKey IN ('intake','date_of_injury','statute_of_limitations','tort_notice_deadline')
) migrated
WHERE ISNULL(c.IsDeleted, 0) = 0
  AND c.ShaleClientId = @ShaleClientId
  AND (@RestrictToUserId IS NULL OR EXISTS (
      SELECT 1 FROM dbo.CaseUsers cu_scope
      WHERE cu_scope.CaseId = c.Id AND cu_scope.UserId = @RestrictToUserId
  ))
ORDER BY migrated.IntakeDate DESC, c.Id DESC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

/* 2. REVERTED a882af2e SHAPE: grouped one-key authoritative sorting boundary. */
SELECT c.Id AS CaseId, migrated_sort.SortDate
FROM dbo.Cases c
LEFT JOIN dbo.PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
             cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id AND cu.RoleId = @ResponsibleAttorneyRoleId AND cu.IsPrimary = 1
    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
) ra
LEFT JOIN dbo.Users u ON u.id = ra.UserId
LEFT JOIN (
    SELECT cd.ShaleClientId, cd.CaseId, MAX(cd.StartsAt) AS SortDate
    FROM dbo.CaseDates cd
    INNER JOIN dbo.CaseDateTypes stored_type ON stored_type.Id = cd.CaseDateTypeId
      AND (stored_type.ShaleClientId = cd.ShaleClientId OR stored_type.ShaleClientId IS NULL)
    WHERE cd.IsDeleted = 0 AND stored_type.SystemKey = 'intake'
    GROUP BY cd.ShaleClientId, cd.CaseId
) migrated_sort ON migrated_sort.CaseId = c.Id AND migrated_sort.ShaleClientId = c.ShaleClientId
WHERE ISNULL(c.IsDeleted, 0) = 0
  AND c.ShaleClientId = @ShaleClientId
  AND (@RestrictToUserId IS NULL OR EXISTS (
      SELECT 1 FROM dbo.CaseUsers cu_scope
      WHERE cu_scope.CaseId = c.Id AND cu_scope.UserId = @RestrictToUserId
  ))
ORDER BY migrated_sort.SortDate DESC, c.Id DESC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

/* 3. CONTROL: restored membership/current-status/assignment shape, ordinary case-name sort. */
SELECT c.Id AS CaseId
FROM dbo.Cases c
LEFT JOIN dbo.PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
             cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id AND cu.RoleId = @ResponsibleAttorneyRoleId AND cu.IsPrimary = 1
    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
) ra
LEFT JOIN dbo.Users u ON u.id = ra.UserId
WHERE ISNULL(c.IsDeleted, 0) = 0
  AND c.ShaleClientId = @ShaleClientId
  AND (@RestrictToUserId IS NULL OR EXISTS (
      SELECT 1 FROM dbo.CaseUsers cu_scope
      WHERE cu_scope.CaseId = c.Id AND cu_scope.UserId = @RestrictToUserId
  ))
ORDER BY c.Name ASC, c.Id ASC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

SET STATISTICS TIME OFF;
SET STATISTICS IO OFF;
