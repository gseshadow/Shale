/*
  Proposed tenant RLS coverage migration.

  Status: proposal only. Review live RLS inventory before executing.
  Non-destructive: creates schema/functions/policies only when missing.

  Strict predicate: table row visible only for current SESSION_CONTEXT('ShaleClientId').
  Overlay predicate: global/default row (ShaleClientId IS NULL) or current tenant row visible.
*/

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF SCHEMA_ID(N'rls') IS NULL
BEGIN
    EXEC(N'CREATE SCHEMA rls');
END;
GO

CREATE OR ALTER FUNCTION rls.fn_TenantMatch(@ShaleClientId int)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS fn_accessResult
    WHERE @ShaleClientId = TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId'));
GO

CREATE OR ALTER FUNCTION rls.fn_TenantOrGlobalMatch(@ShaleClientId int)
RETURNS TABLE
WITH SCHEMABINDING
AS
RETURN
    SELECT 1 AS fn_accessResult
    WHERE @ShaleClientId IS NULL
       OR @ShaleClientId = TRY_CONVERT(int, SESSION_CONTEXT(N'ShaleClientId'));
GO

/*
  Example policy creation pattern. SQL Server does not allow a single policy to
  be altered with dynamic table names, so review/expand this block per live
  table after running the audit queries.

  Strict tenant tables to consider:
    dbo.Users, dbo.Cases, dbo.Contacts, dbo.Organizations, dbo.CaseUpdates,
    dbo.Tasks, dbo.TaskUpdates, dbo.Roles, dbo.Categories, dbo.CalendarEvents,
    dbo.UserPreferences, dbo.UserBoardLanePreferences.

  Global overlay lookup/default tables to consider:
    dbo.Statuses, dbo.Priorities, dbo.PracticeAreas, dbo.PartyRoles,
    dbo.PartySides, dbo.CalendarEventTypes.

  AuditLog requires an explicit decision before policy selection because
  ShaleClientId is nullable for rollout/system rows.
*/

IF OBJECT_ID(N'dbo.PracticeAreas', N'U') IS NOT NULL
   AND COL_LENGTH(N'dbo.PracticeAreas', N'ShaleClientId') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.security_predicates p
        WHERE p.object_id = OBJECT_ID(N'dbo.PracticeAreas')
   )
BEGIN
    CREATE SECURITY POLICY rls.Policy_PracticeAreas_TenantOrGlobal
        ADD FILTER PREDICATE rls.fn_TenantOrGlobalMatch(ShaleClientId) ON dbo.PracticeAreas,
        ADD BLOCK PREDICATE rls.fn_TenantOrGlobalMatch(ShaleClientId) ON dbo.PracticeAreas AFTER INSERT,
        ADD BLOCK PREDICATE rls.fn_TenantOrGlobalMatch(ShaleClientId) ON dbo.PracticeAreas AFTER UPDATE
    WITH (STATE = ON);
END;
GO
