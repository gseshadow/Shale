/*
  Exact Cases page-zero execution-plan comparison (strictly read only).

  WARNING: these are the exact production SELECT lists and therefore the Results grids contain
  PHI-bearing case/display fields. Run only in an approved SSMS session. Before execution, enable
  Tools > Options > Query Results > SQL Server > Results to Grid > Discard results after execution,
  then open a new query window. Do not save, export, screenshot, copy, or return Results. Return
  only .sqlplan files and Messages statistics.

  Before running, enable Query > Include Actual Execution Plan (Ctrl+M). Connect with the same
  approved application identity and ensure SESSION_CONTEXT(N'ShaleClientId') and
  SESSION_CONTEXT(N'PrincipalUserId') are already established exactly as they are for the affected
  user. This script validates them but never sets, clears, weakens, or bypasses RLS context.

  Include Closed/Denied is intentionally not declared: the restored findPageInternal/countAll
  code accepts that Java argument but does not bind it or add an include/exclude predicate to this
  page-row SQL. Search text, selected statuses, and user restriction are absent because the observed
  initial default request has none; adding optional-parameter predicates would not be the exact plan.
*/
SET NOCOUNT ON;
SET STATISTICS IO ON;
SET STATISTICS TIME ON;

DECLARE @ShaleClientId int = 7;
DECLARE @PrincipalUserId int = NULL; -- REQUIRED: replace with the affected authenticated user id.
DECLARE @ResponsibleAttorneyRoleId int = 4;
DECLARE @Offset int = 0;
DECLARE @PageSize int = 100;

IF TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId')) IS NULL
   OR TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId')) <> @ShaleClientId
    THROW 51000, 'Existing ShaleClientId SESSION_CONTEXT does not match @ShaleClientId.', 1;
IF @PrincipalUserId IS NULL OR TRY_CONVERT(int, SESSION_CONTEXT(N'PrincipalUserId')) <> @PrincipalUserId
    THROW 51001, 'Set @PrincipalUserId to the already-established PrincipalUserId SESSION_CONTEXT value.', 1;

SELECT @@VERSION AS SqlServerVersion;
SELECT name AS DatabaseName, compatibility_level AS CompatibilityLevel
FROM sys.databases WHERE database_id = DB_ID();

/* 1. EXACT RESTORED PRODUCTION INTAKE_NEWEST PAGE-ROW QUERY. */
SELECT
  c.Id,
  c.Name,
  migrated.IntakeDate AS CallerDate, migrated.StatuteDate AS StatuteOfLimitations, migrated.IncidentDate AS DateOfIncident, migrated.TortDate AS TortNoticeDeadline,
  latestUpdate.LatestCaseUpdate,
  c.Description AS Description,
  current_status.PrimaryStatusId,
  current_status.CurrentStatusName,
  current_status.PrimaryStatusColor,
  pa.Color AS PracticeAreaColor,
  clientContact.ClientName,
  oppContact.OpposingPartiesName,
  ra.UserId AS ResponsibleAttorneyId,
  u.color AS ResponsibleAttorneyColor,
 c.NonEngagementLetterSent AS NonEngagementLetterSent,
  LTRIM(RTRIM(
    COALESCE(u.name_first, '') +
    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
    COALESCE(u.name_last, '')
  )) AS ResponsibleAttorneyName
FROM dbo.Cases c
LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY
      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
      cs.UpdatedAt DESC,
      cs.CreatedAt DESC,
      cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id
      AND cu.RoleId = @ResponsibleAttorneyRoleId
      AND cu.IsPrimary = 1
    ORDER BY
      cu.UpdatedAt DESC,
      cu.CreatedAt DESC,
      cu.Id DESC
) ra
LEFT JOIN dbo.Users u
  ON u.id = ra.UserId
OUTER APPLY (
    SELECT TOP (1)
      CASE
        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
        ELSE COALESCE(ct.Name, '')
      END AS ClientName
    FROM dbo.CaseParties cp
    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
    WHERE cp.CaseId = c.Id
      AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'party'
      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
) clientContact
OUTER APPLY (
    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
    FROM (
      SELECT
        LTRIM(RTRIM(
          CASE
            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
            ELSE COALESCE(ct.Name, o.Name, '')
          END
        )) AS DisplayName,
        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
        cp.UpdatedAt,
        cp.CreatedAt,
        cp.Id
      FROM dbo.CaseParties cp
      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
      WHERE cp.CaseId = c.Id
        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
    ) opp
    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
) oppContact
OUTER APPLY (
    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
    FROM dbo.CaseUpdates cu
    WHERE cu.CaseId = c.Id
      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
    ORDER BY cu.CreatedAt DESC, cu.Id DESC
) latestUpdate
OUTER APPLY (
  SELECT
    MAX(CASE WHEN type_key.SystemKey = 'intake' THEN cd.StartsAt END) AS IntakeDate,
    MAX(CASE WHEN type_key.SystemKey = 'date_of_injury' THEN cd.StartsAt END) AS IncidentDate,
    MAX(CASE WHEN type_key.SystemKey = 'statute_of_limitations' THEN cd.StartsAt END) AS StatuteDate,
    MAX(CASE WHEN type_key.SystemKey = 'tort_notice_deadline' THEN cd.StartsAt END) AS TortDate
  FROM dbo.CaseDates cd
  INNER JOIN dbo.CaseDateTypes type_key ON type_key.Id = cd.CaseDateTypeId
    AND (type_key.ShaleClientId = c.ShaleClientId OR type_key.ShaleClientId IS NULL)
  WHERE cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId AND cd.IsDeleted = 0
    AND type_key.SystemKey IN ('intake','date_of_injury','statute_of_limitations','tort_notice_deadline')
) migrated
WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
  AND c.ShaleClientId = @ShaleClientId

ORDER BY
  migrated.IntakeDate DESC, c.Id DESC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

/* 2. EXACT a882af2e GROUPED INTAKE_NEWEST PAGE-ROW QUERY SHAPE. */
SELECT
  c.Id,
  c.Name,
  CAST(NULL AS date) AS CallerDate, CAST(NULL AS date) AS StatuteOfLimitations, CAST(NULL AS date) AS DateOfIncident, CAST(NULL AS date) AS TortNoticeDeadline,
  latestUpdate.LatestCaseUpdate,
  c.Description AS Description,
  current_status.PrimaryStatusId,
  current_status.CurrentStatusName,
  current_status.PrimaryStatusColor,
  pa.Color AS PracticeAreaColor,
  clientContact.ClientName,
  oppContact.OpposingPartiesName,
  ra.UserId AS ResponsibleAttorneyId,
  u.color AS ResponsibleAttorneyColor,
 c.NonEngagementLetterSent AS NonEngagementLetterSent,
  LTRIM(RTRIM(
    COALESCE(u.name_first, '') +
    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
    COALESCE(u.name_last, '')
  )) AS ResponsibleAttorneyName
FROM dbo.Cases c
LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY
      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
      cs.UpdatedAt DESC,
      cs.CreatedAt DESC,
      cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id
      AND cu.RoleId = @ResponsibleAttorneyRoleId
      AND cu.IsPrimary = 1
    ORDER BY
      cu.UpdatedAt DESC,
      cu.CreatedAt DESC,
      cu.Id DESC
) ra
LEFT JOIN dbo.Users u
  ON u.id = ra.UserId
OUTER APPLY (
    SELECT TOP (1)
      CASE
        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
        ELSE COALESCE(ct.Name, '')
      END AS ClientName
    FROM dbo.CaseParties cp
    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
    WHERE cp.CaseId = c.Id
      AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'party'
      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
) clientContact
OUTER APPLY (
    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
    FROM (
      SELECT
        LTRIM(RTRIM(
          CASE
            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
            ELSE COALESCE(ct.Name, o.Name, '')
          END
        )) AS DisplayName,
        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
        cp.UpdatedAt,
        cp.CreatedAt,
        cp.Id
      FROM dbo.CaseParties cp
      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
      WHERE cp.CaseId = c.Id
        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
    ) opp
    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
) oppContact
OUTER APPLY (
    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
    FROM dbo.CaseUpdates cu
    WHERE cu.CaseId = c.Id
      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
    ORDER BY cu.CreatedAt DESC, cu.Id DESC
) latestUpdate
LEFT JOIN (
  SELECT cd.ShaleClientId, cd.CaseId, MAX(cd.StartsAt) AS SortDate
  FROM dbo.CaseDates cd
  INNER JOIN dbo.CaseDateTypes stored_type ON stored_type.Id = cd.CaseDateTypeId
    AND (stored_type.ShaleClientId = cd.ShaleClientId OR stored_type.ShaleClientId IS NULL)
  WHERE cd.IsDeleted = 0 AND stored_type.SystemKey = N'intake'
  GROUP BY cd.ShaleClientId, cd.CaseId
) migrated_sort ON migrated_sort.CaseId = c.Id AND migrated_sort.ShaleClientId = c.ShaleClientId
WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
  AND c.ShaleClientId = @ShaleClientId

ORDER BY
  migrated_sort.SortDate DESC, c.Id DESC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

/* 3. EXACT RESTORED PRODUCTION SELECT/FROM SHAPE, CASE_NAME_ASC CONTROL. */
SELECT
  c.Id,
  c.Name,
  migrated.IntakeDate AS CallerDate, migrated.StatuteDate AS StatuteOfLimitations, migrated.IncidentDate AS DateOfIncident, migrated.TortDate AS TortNoticeDeadline,
  latestUpdate.LatestCaseUpdate,
  c.Description AS Description,
  current_status.PrimaryStatusId,
  current_status.CurrentStatusName,
  current_status.PrimaryStatusColor,
  pa.Color AS PracticeAreaColor,
  clientContact.ClientName,
  oppContact.OpposingPartiesName,
  ra.UserId AS ResponsibleAttorneyId,
  u.color AS ResponsibleAttorneyColor,
 c.NonEngagementLetterSent AS NonEngagementLetterSent,
  LTRIM(RTRIM(
    COALESCE(u.name_first, '') +
    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
    COALESCE(u.name_last, '')
  )) AS ResponsibleAttorneyName
FROM dbo.Cases c
LEFT JOIN PracticeAreas pa ON pa.Id = c.PracticeAreaId
OUTER APPLY (
    SELECT TOP (1) s.Id AS PrimaryStatusId, s.Name AS CurrentStatusName, s.Color AS PrimaryStatusColor
    FROM dbo.CaseStatuses cs
    INNER JOIN dbo.Statuses s ON s.Id = cs.StatusId
    WHERE cs.CaseId = c.Id
    ORDER BY
      CASE WHEN cs.IsPrimary = 1 THEN 0 ELSE 1 END,
      cs.UpdatedAt DESC,
      cs.CreatedAt DESC,
      cs.Id DESC
) current_status
OUTER APPLY (
    SELECT TOP (1) cu.UserId
    FROM dbo.CaseUsers cu
    WHERE cu.CaseId = c.Id
      AND cu.RoleId = @ResponsibleAttorneyRoleId
      AND cu.IsPrimary = 1
    ORDER BY
      cu.UpdatedAt DESC,
      cu.CreatedAt DESC,
      cu.Id DESC
) ra
LEFT JOIN dbo.Users u
  ON u.id = ra.UserId
OUTER APPLY (
    SELECT TOP (1)
      CASE
        WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
        THEN LTRIM(RTRIM(COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')))
        ELSE COALESCE(ct.Name, '')
      END AS ClientName
    FROM dbo.CaseParties cp
    INNER JOIN dbo.PartyRoles pr ON pr.Id = cp.PartyRoleId
    INNER JOIN Contacts ct ON ct.Id = cp.ContactId
    WHERE cp.CaseId = c.Id
      AND LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'party'
      AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'represented'
      AND (ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
    ORDER BY CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END, cp.UpdatedAt DESC, cp.CreatedAt DESC, cp.Id DESC
) clientContact
OUTER APPLY (
    SELECT STRING_AGG(opp.DisplayName, ', ') WITHIN GROUP (ORDER BY opp.SortPrimary, opp.UpdatedAt DESC, opp.CreatedAt DESC, opp.Id DESC) AS OpposingPartiesName
    FROM (
      SELECT
        LTRIM(RTRIM(
          CASE
            WHEN NULLIF(LTRIM(RTRIM(COALESCE(ct.FirstName, ''))), '') IS NOT NULL
              OR NULLIF(LTRIM(RTRIM(COALESCE(ct.LastName, ''))), '') IS NOT NULL
            THEN COALESCE(ct.FirstName, '') + CASE WHEN COALESCE(ct.FirstName, '') = '' OR COALESCE(ct.LastName, '') = '' THEN '' ELSE ' ' END + COALESCE(ct.LastName, '')
            ELSE COALESCE(ct.Name, o.Name, '')
          END
        )) AS DisplayName,
        CASE WHEN COALESCE(cp.IsPrimary, 0) = 1 THEN 0 ELSE 1 END AS SortPrimary,
        cp.UpdatedAt,
        cp.CreatedAt,
        cp.Id
      FROM dbo.CaseParties cp
      LEFT JOIN Contacts ct ON ct.Id = cp.ContactId
      LEFT JOIN dbo.Organizations o ON o.Id = cp.OrganizationId
      WHERE cp.CaseId = c.Id
        AND LOWER(LTRIM(RTRIM(COALESCE(cp.Side, '')))) = 'opposing'
        AND (cp.ContactId IS NOT NULL OR cp.OrganizationId IS NOT NULL)
        AND (ct.Id IS NULL OR ct.IsDeleted = 0 OR ct.IsDeleted IS NULL)
        AND (o.Id IS NULL OR o.IsDeleted = 0 OR o.IsDeleted IS NULL)
    ) opp
    WHERE NULLIF(opp.DisplayName, '') IS NOT NULL
) oppContact
OUTER APPLY (
    SELECT TOP (1) NULLIF(LTRIM(RTRIM(cu.NoteText)), '') AS LatestCaseUpdate
    FROM dbo.CaseUpdates cu
    WHERE cu.CaseId = c.Id
      AND (cu.IsDeleted = 0 OR cu.IsDeleted IS NULL)
      AND NULLIF(LTRIM(RTRIM(cu.NoteText)), '') IS NOT NULL
    ORDER BY cu.CreatedAt DESC, cu.Id DESC
) latestUpdate
OUTER APPLY (
  SELECT
    MAX(CASE WHEN type_key.SystemKey = 'intake' THEN cd.StartsAt END) AS IntakeDate,
    MAX(CASE WHEN type_key.SystemKey = 'date_of_injury' THEN cd.StartsAt END) AS IncidentDate,
    MAX(CASE WHEN type_key.SystemKey = 'statute_of_limitations' THEN cd.StartsAt END) AS StatuteDate,
    MAX(CASE WHEN type_key.SystemKey = 'tort_notice_deadline' THEN cd.StartsAt END) AS TortDate
  FROM dbo.CaseDates cd
  INNER JOIN dbo.CaseDateTypes type_key ON type_key.Id = cd.CaseDateTypeId
    AND (type_key.ShaleClientId = c.ShaleClientId OR type_key.ShaleClientId IS NULL)
  WHERE cd.CaseId = c.Id AND cd.ShaleClientId = c.ShaleClientId AND cd.IsDeleted = 0
    AND type_key.SystemKey IN ('intake','date_of_injury','statute_of_limitations','tort_notice_deadline')
) migrated
WHERE (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
  AND c.ShaleClientId = @ShaleClientId

ORDER BY
  c.Name ASC, c.Id ASC
OFFSET @Offset ROWS FETCH NEXT @PageSize ROWS ONLY;

SET STATISTICS TIME OFF;
SET STATISTICS IO OFF;
