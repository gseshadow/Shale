/* Phase 1B forward-only audit vocabulary. Apply manually after Phase 1A and before application code. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U');
 IF @id IS NULL THROW 56910,'EntityActionAuditLog is missing.',1;
 DECLARE @name sysname=N'CK_EntityActionAuditLog_EntityType';
 DECLARE @definition nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name AND is_disabled=0 AND is_not_trusted=0);
 IF @definition IS NULL OR CHARINDEX(N'''ORGANIZATION_ORGANIZATION_TYPE''',@definition)=0
  THROW 56911,'The current trusted EntityType allowlist predecessor is missing.',1;
 DECLARE @action nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=N'CK_EntityActionAuditLog_Action' AND is_disabled=0 AND is_not_trusted=0);
 IF @action IS NULL OR EXISTS(SELECT 1 FROM(VALUES(N'CREATED'),(N'UPDATED'),(N'ACTIVATED'),(N'DEACTIVATED'),(N'DELETED'),(N'ADDED'),(N'REMOVED'),(N'RESTORED'))v(Value) WHERE CHARINDEX(N''''+Value+N'''',@action)=0)
  THROW 56912,'The trusted Action allowlist lacks Phase 1B actions.',1;
 IF CHARINDEX(N'''FIRM_WIDE_ROLE''',@definition)=0 OR CHARINDEX(N'''USER_FIRM_WIDE_ROLE''',@definition)=0
 BEGIN
  DECLARE @replacement nvarchar(max)=N'('+@definition;
  IF CHARINDEX(N'''FIRM_WIDE_ROLE''',@definition)=0 SET @replacement+=N' OR [EntityType]=''FIRM_WIDE_ROLE''';
  IF CHARINDEX(N'''USER_FIRM_WIDE_ROLE''',@definition)=0 SET @replacement+=N' OR [EntityType]=''USER_FIRM_WIDE_ROLE''';
  SET @replacement+=N')';
  EXEC(N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@name));
  EXEC(N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' CHECK '+@replacement);
 END;
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name AND is_disabled=0 AND is_not_trusted=0 AND CHARINDEX(N'''FIRM_WIDE_ROLE''',definition)>0 AND CHARINDEX(N'''USER_FIRM_WIDE_ROLE''',definition)>0)
  THROW 56913,'Phase 1B audit vocabulary was not installed as a trusted constraint.',1;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
