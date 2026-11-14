/* Counts/catalog metadata only. Never reads password hashes, credentials or PHI.
   This proves an unchanged observed checkpoint, not successful completion of unknown DDL. */
IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN
 (N'mcpacc_auth',N'mcpacc_runtime',N'mcpacc_predecessor',N'mcpacc_verifier',N'mcpacc_bootstrap')
 AND type='S' AND authentication_type=2)<>5
 THROW 59810,'Foundation identities missing; no automatic repair.',1;
IF EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Enabled<>0)
 THROW 59810,'Controls must stay OFF.',1;
DECLARE @Snapshot nvarchar(max)=(SELECT o.object_id,o.name,o.type,o.modify_date,m.definition
 FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE o.is_ms_shipped=0 ORDER BY o.object_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue
 FROM sys.extended_properties ORDER BY class,major_id,minor_id,name FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT principal_id,name,type,authentication_type,CONVERT(varchar(256),sid,2) AS PrincipalSid
 FROM sys.database_principals ORDER BY principal_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.database_role_members ORDER BY role_principal_id,member_principal_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.database_permissions ORDER BY class,major_id,minor_id,grantee_principal_id,permission_name FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.security_predicates ORDER BY object_id,security_predicate_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.security_policies ORDER BY object_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.indexes WHERE object_id IN(SELECT object_id FROM sys.tables WHERE is_ms_shipped=0) ORDER BY object_id,index_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT * FROM sys.columns WHERE object_id IN(SELECT object_id FROM sys.tables WHERE is_ms_shipped=0) ORDER BY object_id,column_id FOR JSON PATH);
SET @Snapshot=@Snapshot+(SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment ORDER BY Id FOR JSON PATH);
IF @Snapshot IS NULL THROW 59810,'Reconciliation metadata is incomplete.',1;
DECLARE @AcceptanceSnapshot varchar(160)=CONCAT('ACCEPTANCE_SNAPSHOT:',CONVERT(varchar(64),HASHBYTES('SHA2_256',@Snapshot),2),':',
 (SELECT COUNT_BIG(*) FROM dbo.ShaleClients),':',(SELECT COUNT_BIG(*) FROM dbo.Users),':',
 (SELECT COUNT_BIG(*) FROM dbo.Cases));
PRINT @AcceptanceSnapshot;
