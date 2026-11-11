/* Signing is metadata-only. An sa/sysadmin owner is never an accepted deployment. */
IF ISNULL(CONVERT(int,SERVERPROPERTY('EngineEdition')),0) NOT IN(2,3,4)
 OR ISNULL(IS_SRVROLEMEMBER('sysadmin'),1)<>0
 OR HAS_PERMS_BY_NAME(NULL,NULL,'CONTROL SERVER')=1
 THROW 59620,'Restricted boxed catalog execution context required.',1;
DECLARE @OwnerSid varbinary(85)=(SELECT owner_sid FROM sys.databases WHERE database_id=DB_ID());
DECLARE @OwnerId int=(SELECT principal_id FROM sys.server_principals WHERE sid=@OwnerSid AND type='S' AND is_disabled=1);
IF @OwnerId IS NULL OR SUSER_SID()<>@OwnerSid
 OR (SELECT COUNT(*) FROM sys.databases WHERE owner_sid=@OwnerSid)<>1
 OR EXISTS(SELECT 1 FROM sys.server_role_members WHERE member_principal_id=@OwnerId)
 OR EXISTS(SELECT 1 FROM sys.server_permissions WHERE grantee_principal_id=@OwnerId AND NOT(class=100 AND state='G' AND permission_name='CONNECT SQL'))
 OR EXISTS(SELECT 1 FROM sys.endpoints WHERE principal_id=@OwnerId)
 OR EXISTS(SELECT 1 FROM sys.server_principals WHERE owning_principal_id=@OwnerId)
 THROW 59620,'Dedicated disabled non-administrative database owner required.',1;
IF EXISTS(SELECT permission_name FROM sys.fn_my_permissions(NULL,'SERVER') EXCEPT SELECT PermissionName FROM (VALUES
 ('CONNECT SQL'),('VIEW ANY DATABASE'),('VIEW ANY DEFINITION'),('VIEW ANY SECURITY DEFINITION'),('VIEW ANY PERFORMANCE DEFINITION'),('AUTHENTICATE SERVER'),('CONNECT ANY DATABASE')) p(PermissionName))
 THROW 59620,'Excess signed server authority.',1;
DECLARE @SignerId int=(SELECT principal_id FROM sys.server_principals WHERE name='AiCimdCatalogSignerV2' AND type='C' AND is_disabled=0);
IF @SignerId IS NULL OR EXISTS(SELECT 1 FROM sys.server_role_members WHERE member_principal_id=@SignerId)
 OR EXISTS(SELECT 1 FROM sys.endpoints WHERE principal_id=@SignerId)
 OR EXISTS(SELECT 1 FROM sys.server_principals WHERE owning_principal_id=@SignerId)
 OR EXISTS(SELECT 1 FROM sys.server_permissions WHERE grantee_principal_id=@SignerId AND NOT(class=100 AND state='G' AND grantor_principal_id=1 AND permission_name IN('VIEW ANY DEFINITION','VIEW ANY DATABASE','AUTHENTICATE SERVER','CONNECT ANY DATABASE')))
 OR (SELECT COUNT(*) FROM sys.server_permissions WHERE grantee_principal_id=@SignerId)<>4
 THROW 59620,'Signing identity authority drift.',1;
IF (SELECT COUNT(*) FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2' AND principal_id=1 AND pvt_key_encryption_type='NA' AND expiry_date>SYSUTCDATETIME())<>1
 OR (SELECT COUNT(*) FROM master.sys.certificates WHERE name='AiCimdCatalogCertificateV2' AND principal_id=1 AND pvt_key_encryption_type='NA' AND expiry_date>SYSUTCDATETIME())<>1
 OR (SELECT thumbprint FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2')<>(SELECT thumbprint FROM master.sys.certificates WHERE name='AiCimdCatalogCertificateV2')
 OR (SELECT COUNT(*) FROM sys.crypt_properties WHERE major_id=OBJECT_ID('dbo.AiCimdAttestCatalogV2') AND thumbprint=(SELECT thumbprint FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2'))<>1
 OR (SELECT COUNT(*) FROM sys.crypt_properties WHERE thumbprint=(SELECT thumbprint FROM sys.certificates WHERE name='AiCimdCatalogCertificateV2'))<>1
 THROW 59620,'Signing certificate or signature drift.',1;

IF EXISTS(SELECT 1 FROM sys.server_triggers) THROW 59620,'Unreviewed server trigger.',1;
