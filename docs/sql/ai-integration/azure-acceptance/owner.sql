IF USER_NAME()<>N'dbo' OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 THROW 59801,'STOP: existing database-local owner visibility required.',1;
/* Reject ON controls, never turn them OFF as an installer repair. Dynamic SQL permits absent tables. */
DECLARE @OffTable sysname, @OffSql nvarchar(max);
DECLARE OffTables CURSOR LOCAL FAST_FORWARD FOR
 SELECT name FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN
 (N'AiProductCapability',N'AiCimdProofControlV1',N'AiCimdOAuthControlV1',N'AiExternalControlV2',N'AiDcrControlV3',N'TenantAiIntegrationPolicy');
OPEN OffTables; FETCH NEXT FROM OffTables INTO @OffTable;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @OffSql=N'IF EXISTS(SELECT 1 FROM dbo.'+QUOTENAME(@OffTable)+N' WHERE Enabled<>0) THROW 59801,''STOP: SQL controls must remain OFF.'',1;';
 EXEC sys.sp_executesql @OffSql;
 FETCH NEXT FROM OffTables INTO @OffTable;
END;
CLOSE OffTables; DEALLOCATE OffTables;
