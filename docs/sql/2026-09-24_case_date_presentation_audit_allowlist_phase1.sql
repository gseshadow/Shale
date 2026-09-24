/* Case Date Presentation Phase 1 forward-only audit vocabulary. Apply after field confirmation Phase 2B and before application code. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U');
 IF @id IS NULL THROW 57060,'EntityActionAuditLog is missing.',1;
 DECLARE @entityName sysname=N'CK_EntityActionAuditLog_EntityType',@actionName sysname=N'CK_EntityActionAuditLog_Action';
 DECLARE @entity nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=@entityName AND is_disabled=0 AND is_not_trusted=0);
 DECLARE @action nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=@actionName AND is_disabled=0 AND is_not_trusted=0);
 IF @entity IS NULL OR CHARINDEX(N'''FIRM_WIDE_ROLE''',@entity)=0 THROW 57061,'Trusted Phase 1B EntityType predecessor is missing.',1;
 IF @action IS NULL OR CHARINDEX(N'''RESTORED''',@action)=0 THROW 57062,'Trusted Action predecessor is missing.',1;
 IF CHARINDEX(N'''CASE_DATE_PRESENTATION_CONFIGURATION''',@entity)=0 OR CHARINDEX(N'''SAVED_VALUE_CONFIRMATION''',@entity)=0 BEGIN
  DECLARE @entityReplacement nvarchar(max)=N'('+@entity;
  IF CHARINDEX(N'''CASE_DATE_PRESENTATION_CONFIGURATION''',@entity)=0 SET @entityReplacement+=N' OR [EntityType]=''CASE_DATE_PRESENTATION_CONFIGURATION''';
  IF CHARINDEX(N'''SAVED_VALUE_CONFIRMATION''',@entity)=0 SET @entityReplacement+=N' OR [EntityType]=''SAVED_VALUE_CONFIRMATION''';
  SET @entityReplacement+=N')';
  DECLARE @dropEntity nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@entityName);
  DECLARE @addEntity nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@entityName)+N' CHECK '+@entityReplacement;
  EXEC sys.sp_executesql @dropEntity; EXEC sys.sp_executesql @addEntity;
 END;
 IF CHARINDEX(N'''CONFIRMED''',@action)=0 BEGIN
  DECLARE @actionReplacement nvarchar(max)=N'('+@action+N' OR [Action]=''CONFIRMED'')';
  DECLARE @dropAction nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@actionName);
  DECLARE @addAction nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@actionName)+N' CHECK '+@actionReplacement;
  EXEC sys.sp_executesql @dropAction; EXEC sys.sp_executesql @addAction;
 END;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
