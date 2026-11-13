/* Database-local read-only fence. SQLCMD must also pin -S and -d before connecting. */
SET NOCOUNT ON;
IF CONVERT(varbinary(max),DB_NAME())<>CONVERT(varbinary(max),N'ShaleMcpAcceptance')
 THROW 59800,'STOP: only ShaleMcpAcceptance is permitted.',1;
IF ISNULL(CONVERT(int,SERVERPROPERTY('EngineEdition')),0)<>5
 THROW 59800,'STOP: native Azure SQL Database required.',1;
IF CONVERT(varbinary(max),ISNULL(CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')),N''))
 NOT IN(CONVERT(varbinary(max),N'shale'),CONVERT(varbinary(max),N'shale.database.windows.net'))
 THROW 59800,'STOP: unexpected logical server.',1;
IF CONVERT(varbinary(max),ISNULL(CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')),N''))
 <>CONVERT(varbinary(max),N'SQL_Latin1_General_CP1_CI_AS')
 THROW 59800,'STOP: unexpected collation.',1;
IF @@TRANCOUNT<>0 OR (2 & @@OPTIONS)=2
 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 OR SESSION_CONTEXT(N'PrincipalEmail') IS NOT NULL
 THROW 59800,'STOP: fresh neutral autocommit connection required.',1;
