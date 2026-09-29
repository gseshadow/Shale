/* Run as a disposable non-dbo principal after substituting valid tenant/user ids. Expected cross-tenant errors: 33504. */
DECLARE @SameTenant int=7,@SameTenantActor int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=7 ORDER BY id),@OtherTenant int=8,@OtherTenantActor int=(SELECT TOP(1) id FROM dbo.Users WHERE ShaleClientId=8 ORDER BY id);
EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=@SameTenant,@read_only=1;
INSERT dbo.AdministrativeReadAuditLog(ShaleClientId,ActorUserId,ReadType,ResultCount,Metadata) VALUES(@SameTenant,@SameTenantActor,'APPLICATION_INSTANCE_RECENT_LIST',0,'{"page":0,"pageSize":1}');
DECLARE @AuditId bigint=SCOPE_IDENTITY();
SELECT 'SameTenantVisible' CheckName,CASE WHEN EXISTS(SELECT 1 FROM dbo.AdministrativeReadAuditLog WHERE ShaleClientId=@SameTenant AND ActorUserId=@SameTenantActor) THEN 0 ELSE 1 END FindingCount;
SELECT 'CrossTenantFiltered' CheckName,COUNT(*) FindingCount FROM dbo.AdministrativeReadAuditLog WHERE ShaleClientId=@OtherTenant;
BEGIN TRY
 INSERT dbo.AdministrativeReadAuditLog(ShaleClientId,ActorUserId,ReadType,ResultCount) VALUES(@OtherTenant,@OtherTenantActor,'APPLICATION_INSTANCE_RECENT_LIST',0);
 THROW 57610, 'Cross-tenant insert unexpectedly succeeded.', 1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>33504 THROW; END CATCH;
BEGIN TRY
 UPDATE dbo.AdministrativeReadAuditLog SET ShaleClientId=@OtherTenant,ActorUserId=@OtherTenantActor WHERE Id=@AuditId;
 THROW 57611, 'Cross-tenant update unexpectedly succeeded.', 1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>33504 THROW; END CATCH;
SELECT 'CrossTenantInsertAndUpdateBlocked33504' CheckName,0 FindingCount;
/* In a fresh dbo window, delete Id=@AuditId. The read-only session context cannot be cleared here. */
