/* Contacts Phase 1C audit vocabulary. Forward-only, idempotent, schema-only. DO NOT run through an application principal. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL THROW 56200,'Phase 1C audit migration requires NULL tenant SESSION_CONTEXT.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1) THROW 56201,'Use the approved administrative principal.',1;
IF @OperatorVerifiedAllTenantVisibility<>1 THROW 56202,'Operator-verified all-tenant visibility is required.',1;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.EntityActionAuditLog',N'U') IS NULL THROW 56203,'EntityActionAuditLog is missing.',1;
IF COL_LENGTH(N'dbo.EntityActionAuditLog',N'EntityType') IS NULL OR COL_LENGTH(N'dbo.EntityActionAuditLog',N'Action') IS NULL THROW 56204,'Audit vocabulary columns are missing.',1;
IF EXISTS(SELECT 1 FROM dbo.EntityActionAuditLog WHERE EntityType NOT IN('CASE','CASE_STATUS','LINK_TYPE','CASE_LINK','CASE_LINK_SHARE','CASE_DATE','CASE_DATE_TYPE','CALENDAR_EVENT','CASE_DATE_ROLE_MAPPING','CALENDAR_CASE_DATE_TYPE_MAPPING','FORM_CONFIGURATION','MATERIAL_TYPE','MATERIAL_REQUEST','MATERIAL_REQUEST_FOLLOW_UP','MATERIAL_ITEM','USER','CONTACT_TYPE','SPECIALTY','CREDENTIAL_DEFINITION','CONTACT_CONTACT_TYPE','CONTACT_SPECIALTY','CONTACT_CREDENTIAL')) THROW 56205,'Existing audit entity vocabulary is incompatible.',1;
DECLARE @Current sysname=(SELECT name FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND name=N'CK_EntityActionAuditLog_EntityType');
IF @Current IS NOT NULL ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT CK_EntityActionAuditLog_EntityType;
ALTER TABLE dbo.EntityActionAuditLog WITH CHECK ADD CONSTRAINT CK_EntityActionAuditLog_EntityType CHECK(EntityType IN('CASE','CASE_STATUS','LINK_TYPE','CASE_LINK','CASE_LINK_SHARE','CASE_DATE','CASE_DATE_TYPE','CALENDAR_EVENT','CASE_DATE_ROLE_MAPPING','CALENDAR_CASE_DATE_TYPE_MAPPING','FORM_CONFIGURATION','MATERIAL_TYPE','MATERIAL_REQUEST','MATERIAL_REQUEST_FOLLOW_UP','MATERIAL_ITEM','USER','CONTACT_TYPE','SPECIALTY','CREDENTIAL_DEFINITION','CONTACT_CONTACT_TYPE','CONTACT_SPECIALTY','CONTACT_CREDENTIAL'));
ALTER TABLE dbo.EntityActionAuditLog CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
