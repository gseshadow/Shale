/* Status Settings soft deletion. No Statuses or CaseStatuses rows are deleted or rewritten. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.Statuses',N'U') IS NULL THROW 54900,'Required table dbo.Statuses is missing.',1;
 IF COL_LENGTH(N'dbo.Statuses',N'IsActive') IS NULL
  ALTER TABLE dbo.Statuses ADD IsActive bit NOT NULL CONSTRAINT DF_Statuses_IsActive DEFAULT(1) WITH VALUES;
 IF COL_LENGTH(N'dbo.Statuses',N'IsDeleted') IS NULL
  ALTER TABLE dbo.Statuses ADD IsDeleted bit NOT NULL CONSTRAINT DF_Statuses_IsDeleted DEFAULT(0) WITH VALUES;
 IF EXISTS(SELECT 1 FROM dbo.Statuses WHERE IsDeleted=1 AND IsActive=1)
  THROW 54901,'Statuses contains an invalid active/deleted row.',1;
 IF OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL THROW 54902,'Required audit table is missing.',1;
 DECLARE @constraint sysname;
 SELECT @constraint=cc.name FROM sys.check_constraints cc
 WHERE cc.parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND cc.definition LIKE '%EntityType%IN%';
 IF @constraint IS NULL THROW 54903,'Entity action EntityType allowlist is missing.',1;
 DECLARE @sql nvarchar(max)=N'ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT '+QUOTENAME(@constraint);
 EXEC sys.sp_executesql @sql;
 ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT CK_EntityActionAuditLog_EntityType
 CHECK(EntityType IN ('CASE','CASE_STATUS','LINK_TYPE','CASE_LINK','CASE_LINK_SHARE','CASE_DATE','CASE_DATE_TYPE','CALENDAR_EVENT','CASE_DATE_ROLE_MAPPING','CALENDAR_CASE_DATE_TYPE_MAPPING','FORM_CONFIGURATION','MATERIAL_TYPE','MATERIAL_REQUEST','MATERIAL_REQUEST_FOLLOW_UP','MATERIAL_ITEM','USER'));
 ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
