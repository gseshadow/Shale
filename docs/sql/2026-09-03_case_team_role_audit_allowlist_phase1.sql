/* Forward-only successor: add CASE_TEAM_ROLE to the canonical EntityActionAuditLog allowlist. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U'); IF @id IS NULL THROW 56510,'Audit table is missing.',1;
 DECLARE @name sysname=N'CK_EntityActionAuditLog_EntityType';
 DECLARE @definition nvarchar(max)=(
  SELECT definition
  FROM sys.check_constraints
  WHERE parent_object_id=@id AND name=@name
 );
 IF @definition IS NULL OR CHARINDEX(N'''CONTACT_CREDENTIAL''',@definition)=0 THROW 56511,'Canonical EntityType allowlist was not found or does not contain the expected predecessor vocabulary.',1;

 DECLARE @actionDefinition nvarchar(max)=(
  SELECT definition
  FROM sys.check_constraints
  WHERE parent_object_id=@id AND name=N'CK_EntityActionAuditLog_Action'
 );
 IF @actionDefinition IS NULL THROW 56512,'Canonical Action allowlist was not found.',1;
 IF EXISTS(
  SELECT 1
  FROM (VALUES(N'CREATED'),(N'OVERRIDE_CREATED'),(N'UPDATED'),(N'ACTIVATED'),(N'DEACTIVATED'),(N'REMOVED'),(N'RESTORED'),(N'OVERRIDE_RESET')) required(ActionName)
  WHERE CHARINDEX(N''''+required.ActionName+N'''',@actionDefinition)=0
 ) THROW 56513,'The Action allowlist is missing an action required by CASE_TEAM_ROLE auditing.',1;

 IF CHARINDEX(N'''CASE_TEAM_ROLE''',@definition)=0
 BEGIN
  DECLARE @replacement nvarchar(max)=N'('+@definition+N' OR [EntityType]=''CASE_TEAM_ROLE'')';
  DECLARE @dropSql nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@name)+N';';
  DECLARE @addSql nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' CHECK '+@replacement+N';';
  EXEC sys.sp_executesql @dropSql;
  EXEC sys.sp_executesql @addSql;
 END;
 IF NOT EXISTS(
  SELECT 1
  FROM sys.check_constraints
  WHERE parent_object_id=@id
   AND name=@name
   AND CHARINDEX(N'''CASE_TEAM_ROLE''',definition)>0
   AND is_disabled=0
   AND is_not_trusted=0
 ) THROW 56514,'CASE_TEAM_ROLE was not installed as a trusted EntityType constraint value.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
