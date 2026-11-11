#!/usr/bin/env python3
"""Separately versioned signed boxed catalog. Reads, never rewrites, all predecessors."""
from pathlib import Path
import argparse,json,hashlib
ROOT=Path(__file__).resolve().parents[2];BUILD=Path(__file__).resolve().parent;RES=ROOT/'shale-data/src/main/resources/ai';SQL=ROOT/'docs/sql/ai-integration'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--check',action='store_true');args=p.parse_args()
def emit(path,s):
 if args.check:
  if not path.exists() or path.read_text()!=s:raise SystemExit('Stale artifact: '+str(path.relative_to(ROOT)))
 else:path.write_text(s)
def lit(s):
 if s is None:return 'NULL'
 if isinstance(s,bool):return str(int(s))
 if isinstance(s,int):return str(s)
 return "N'"+s.replace("'","''")+"'"
def inserts(table,rows):
 return ''.join('INSERT '+table+' VALUES '+','.join('('+','.join(lit(v) for v in row)+')' for row in rows[i:i+500])+';\n' for i in range(0,len(rows),500))
baseline=json.loads((BUILD/'cimd-catalog-v2-system-baseline.json').read_text())
expected='DECLARE @SystemProductVersion varchar(64)='+lit(baseline['ProductVersion'])+';\nDECLARE @ExpectedSystemPermissions table(DatabaseName sysname,Class int,MajorId int,MinorId int,Grantee sysname,Grantor sysname,PermissionName sysname,State char(1));\n'
expected+=inserts('@ExpectedSystemPermissions',[[r[k] for k in ['DatabaseName','class','major_id','minor_id','Grantee','Grantor','permission_name','state']] for r in baseline['permissions']])
expected+='DECLARE @ExpectedSystemObjects table(DatabaseName sysname,ObjectId int,SchemaName sysname,Name sysname,Type char(2),OwnerId int,DefinitionHash varchar(64),ExecuteAs int,Ansi bit,Quoted bit);\n'
expected+=inserts('@ExpectedSystemObjects',[[r[k] for k in ['DatabaseName','object_id','SchemaName','name','type','OwnerId','DefinitionHash','execute_as_principal_id','uses_ansi_nulls','uses_quoted_identifier']] for r in baseline['objects']])
expected+='DECLARE @ExpectedSystemAuthority table(DatabaseName sysname,Kind nvarchar(32),Name nvarchar(517),Related nvarchar(517),Type nvarchar(128),Value nvarchar(max),Flags nvarchar(128));\n'
expected+=inserts('@ExpectedSystemAuthority',[[r[k] for k in ['DatabaseName','Kind','Name','Related','Type','Value','Flags']] for r in baseline['authority']])
guard=(BUILD/'cimd-catalog-v2-guard.sql').read_text()
authority=(BUILD/'cimd-catalog-v2-authority-query.sql').read_text().strip().rstrip(';')
system=(BUILD/'cimd-catalog-v2-system.sql').read_text().replace('-- SYSTEM_BASELINE',expected).replace('-- AUTHORITY_COLLECTION', ('INSERT #CimdSystemAuthority '+authority+';').replace("'","''"))
old=(RES/'cimd-runtime-v1-catalog.sql').read_text()
body=old.replace('CREATE PROCEDURE dbo.AiCimdAttestCatalogV1','CREATE PROCEDURE dbo.AiCimdAttestCatalogV2',1)
body=body.replace("IF @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0", "IF (@@TRANCOUNT<>0 AND ISNULL(IS_SRVROLEMEMBER('sysadmin',ORIGINAL_LOGIN()),0)<>1) OR (@@OPTIONS & 2)<>0",1)
index=body.index(" IF (SELECT COUNT(*) FROM sys.extended_properties")
body=body[:index]+guard+'\n'+body[index:]
index=body.index(' IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment')
body=body[:index]+system+'\n'+body[index:]
body=body.replace('SELECT Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment WHERE Id=1;', "SELECT Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone,CONVERT(varchar(64),HASHBYTES('SHA2_256',CERTENCODED(CERT_ID('AiCimdCatalogCertificateV2'))),2) AS CatalogCertificateSha256,CONVERT(varchar(170),@OwnerSid,2) AS CatalogOwnerSid FROM dbo.AiCaseDataDeployment WHERE Id=1;")
# Deployment supplies a disabled, non-admin owner BEFORE this one-shot OFF installation.
# No owner login/password/ownership change is provisioned here; original receipts stay sealed.
install="""/* PR4D candidate: governed boxed-only catalog signing. No production execution authorization. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF USER_NAME()<>'dbo' OR ISNULL(IS_SRVROLEMEMBER('sysadmin'),0)<>1 OR @@TRANCOUNT<>0 OR (@@OPTIONS & 2)<>0
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 59620,'Fresh unscoped installer required.',1;
DECLARE @DeploymentOwner varbinary(85)=(SELECT owner_sid FROM sys.databases WHERE database_id=DB_ID());
IF NOT EXISTS(SELECT 1 FROM sys.server_principals WHERE sid=@DeploymentOwner AND type='S' AND is_disabled=1)
 OR EXISTS(SELECT 1 FROM sys.server_role_members r JOIN sys.server_principals p ON p.principal_id=r.member_principal_id WHERE p.sid=@DeploymentOwner)
 THROW 59620,'Provision the dedicated disabled non-admin owner separately.',1;
IF OBJECT_ID('dbo.AiCimdAttestCatalogV2') IS NOT NULL OR DATABASE_PRINCIPAL_ID('AiCimdCatalogV2') IS NOT NULL
 OR CERT_ID('AiCimdCatalogCertificateV2') IS NOT NULL OR EXISTS(SELECT 1 FROM master.sys.certificates WHERE name='AiCimdCatalogCertificateV2')
 OR SUSER_ID('AiCimdCatalogSignerV2') IS NOT NULL THROW 59620,'Catalog V2 already exists; never repair or reseal.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @lock int; EXEC @lock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WHERE Id=1 AND Enabled=0) THROW 59620,'OFF foundation required.',1;
"""
install+='EXEC sys.sp_executesql '+lit(body)+';\n'
install+="""CREATE ROLE AiCimdCatalogV2 AUTHORIZATION dbo;
GRANT VIEW DEFINITION TO AiCimdCatalogV2;
GRANT EXECUTE ON dbo.AiCimdAttestCatalogV2 TO AiCimdCatalogV2;
DECLARE @KeyPassword nvarchar(80)=CONVERT(nvarchar(36),NEWID())+N'aB9!';
DECLARE @Ddl nvarchar(max)=N'CREATE CERTIFICATE AiCimdCatalogCertificateV2 ENCRYPTION BY PASSWORD='+QUOTENAME(@KeyPassword,'''')+N' WITH SUBJECT=''Shale fixed catalog V2 metadata'',EXPIRY_DATE=''2030-01-01'';';
EXEC sys.sp_executesql @Ddl;
DECLARE @PublicCertificate varchar(max)=CONVERT(varchar(max),CERTENCODED(CERT_ID('AiCimdCatalogCertificateV2')),2);
SET @Ddl=N'USE master; CREATE CERTIFICATE AiCimdCatalogCertificateV2 FROM BINARY=0x'+CONVERT(nvarchar(max),@PublicCertificate)+N'; CREATE LOGIN AiCimdCatalogSignerV2 FROM CERTIFICATE AiCimdCatalogCertificateV2;
 REVOKE CONNECT SQL FROM AiCimdCatalogSignerV2;
 GRANT VIEW ANY DEFINITION,VIEW ANY DATABASE,AUTHENTICATE SERVER,CONNECT ANY DATABASE TO AiCimdCatalogSignerV2;';
EXEC sys.sp_executesql @Ddl;
SET @Ddl=N'ADD SIGNATURE TO dbo.AiCimdAttestCatalogV2 BY CERTIFICATE AiCimdCatalogCertificateV2 WITH PASSWORD='+QUOTENAME(@KeyPassword,'''')+N';';
EXEC sys.sp_executesql @Ddl;
ALTER CERTIFICATE AiCimdCatalogCertificateV2 REMOVE PRIVATE KEY;
SET @KeyPassword=NULL; SET @Ddl=NULL;
EXEC dbo.AiCimdAttestCatalogV2;
COMMIT TRANSACTION;
SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CERTENCODED(CERT_ID('AiCimdCatalogCertificateV2'))),2) AS CatalogCertificateSha256,
 CONVERT(varchar(170),(SELECT owner_sid FROM sys.databases WHERE database_id=DB_ID()),2) AS CatalogOwnerSid;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
"""
emit(RES/'cimd-runtime-v2-catalog.sql',body)
emit(RES/'cimd-runtime-v2-catalog.sha256',hashlib.sha256(body.encode('utf-16le')).hexdigest()+'\n')
emit(SQL/'universal-pr4d-catalog.sql',install)
