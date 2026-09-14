/* Apply after the Phase 2 assignment schema and before application deployment. Canonical forward-only OR-chain extension. */
SET XACT_ABORT ON;
BEGIN TRY BEGIN TRANSACTION;
 DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U'),@name sysname=N'CK_EntityActionAuditLog_EntityType';
 IF @id IS NULL THROW 56620,'EntityActionAuditLog is missing.',1;
 DECLARE @definition nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name);
 IF @definition IS NULL OR CHARINDEX(N'''CASE_TEAM_ROLE''',@definition)=0 THROW 56621,'Canonical Phase 1 Case Team audit allowlist predecessor is missing.',1;
 IF CHARINDEX(N'''CASE_TEAM_MEMBER''',@definition)=0 OR CHARINDEX(N'''CASE_TEAM_MEMBER_ROLE''',@definition)=0 BEGIN
  DECLARE @replacement nvarchar(max)=N'('+@definition;
  IF CHARINDEX(N'''CASE_TEAM_MEMBER''',@definition)=0 SET @replacement+=N' OR [EntityType]=''CASE_TEAM_MEMBER''';
  IF CHARINDEX(N'''CASE_TEAM_MEMBER_ROLE''',@definition)=0 SET @replacement+=N' OR [EntityType]=''CASE_TEAM_MEMBER_ROLE''';
  SET @replacement+=N')';
  DECLARE @dropSql nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@name)+N';';
  DECLARE @addSql nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' CHECK '+@replacement+N';';
  EXEC sys.sp_executesql @dropSql;
  EXEC sys.sp_executesql @addSql;
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name AND is_disabled=0 AND is_not_trusted=0 AND CHARINDEX(N'''CASE_TEAM_MEMBER''',definition)>0 AND CHARINDEX(N'''CASE_TEAM_MEMBER_ROLE''',definition)>0) THROW 56622,'Phase 2 Case Team audit values were not installed as trusted values.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
