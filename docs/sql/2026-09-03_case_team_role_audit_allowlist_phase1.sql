/* Forward-only successor: add CASE_TEAM_ROLE to the canonical EntityActionAuditLog allowlist. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @id int=OBJECT_ID(N'dbo.EntityActionAuditLog',N'U'); IF @id IS NULL THROW 56510,'Audit table is missing.',1;
 DECLARE @name sysname;
 SELECT @name=cc.name FROM sys.check_constraints cc JOIN sys.sql_expression_dependencies d ON d.referencing_id=cc.object_id
 JOIN sys.columns c ON c.object_id=d.referenced_id AND c.column_id=d.referenced_minor_id
 WHERE cc.parent_object_id=@id AND c.name=N'EntityType' AND cc.definition LIKE N'%CONTACT_CREDENTIAL%';
 IF @name IS NULL THROW 56511,'Canonical EntityType allowlist was not found.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name AND definition LIKE N'%CASE_TEAM_ROLE%')
 BEGIN
  DECLARE @definition nvarchar(max)=(SELECT definition FROM sys.check_constraints WHERE parent_object_id=@id AND name=@name);
  SET @definition=STUFF(@definition,LEN(@definition)-CHARINDEX(N')',REVERSE(@definition))+1,0,N',''CASE_TEAM_ROLE''');
  EXEC(N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@name));
  EXEC(N'ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' CHECK '+@definition);
 END;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
