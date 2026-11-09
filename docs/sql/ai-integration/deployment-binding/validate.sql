/* Include only, read-only assertions. V2 catalog verifier must precede this include. */
IF @BindServer IS NULL OR @BindDatabase IS NULL OR @BindOrigin IS NULL
 OR @BindIssuer IS NULL OR @BindResource IS NULL OR @BindJavaZone IS NULL OR @BindSqlZone IS NULL
 OR ISNULL(@BindOffConfirmed,0)<>1 OR ISNULL(@BindEvidenceConfirmed,0)<>1
 THROW 59301,'Unconfirmed destination, configuration, time zones or OFF evidence.',1;
IF CONVERT(varbinary(max),@BindServer)<>CONVERT(varbinary(max),CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')))
 OR CONVERT(varbinary(max),@BindDatabase)<>CONVERT(varbinary(max),DB_NAME())
 THROW 59301,'Wrong logical server or database.',1;
/* Full URI validation uses the existing McpOAuthProperties offline probe, not SQL URL guessing. */
IF DATALENGTH(@BindOrigin)=0 OR DATALENGTH(@BindOrigin)>480
 OR DATALENGTH(@BindIssuer)>512 OR DATALENGTH(@BindResource)>512
 OR CONVERT(varbinary(max),@BindIssuer)<>CONVERT(varbinary(max),@BindOrigin+N'/oauth/mcp')
 OR CONVERT(varbinary(max),@BindResource)<>CONVERT(varbinary(max),@BindOrigin+N'/mcp')
 OR DATALENGTH(@BindJavaZone)=0 OR DATALENGTH(@BindJavaZone)>200
 OR CONVERT(varbinary(max),@BindJavaZone)<>CONVERT(varbinary(max),CONVERT(nvarchar(max),CONVERT(varchar(max),@BindJavaZone)))
 OR DATALENGTH(CONVERT(varchar(max),@BindJavaZone))>100
 OR DATALENGTH(@BindSqlZone)=0 OR DATALENGTH(@BindSqlZone)>256
 THROW 59301,'Invalid lengths/encoding or issuer/resource derivation.',1;
IF NOT EXISTS(SELECT 1 FROM sys.time_zone_info
 WHERE CONVERT(varbinary(max),name)=CONVERT(varbinary(max),@BindSqlZone))
 THROW 59301,'SQL time-zone name is unavailable.',1;
IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties
 WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND minor_id=0
 AND name=N'Shale.AiCaseDataV2.Source'),0x)<>0x60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891
 THROW 59301,'Reviewed V2 source receipt required; never install or repair migrations here.',1;
IF (SELECT COUNT(*) FROM dbo.AiProductCapability)<>1
 OR EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id<>1 OR Enabled<>0)
 OR EXISTS(SELECT 1 FROM dbo.TenantAiIntegrationPolicy WHERE Enabled<>0)
 THROW 59301,'Global and tenant SQL capabilities must remain OFF.',1;
IF (SELECT COUNT(*) FROM @BindWallTimes)<>28
 OR EXISTS(SELECT 1 FROM @BindWallTimes WHERE YEAR(UtcSample) NOT BETWEEN 2025 AND 2028
 OR MONTH(UtcSample) NOT IN(1,3,4,7,10,11,12) OR DAY(UtcSample)<>15
 OR CONVERT(time(7),UtcSample)<>CONVERT(time(7),'00:00:00'))
 THROW 59301,'Require exactly the attestation 28 Java wall-time samples.',1;
IF EXISTS(SELECT 1 FROM @BindWallTimes
 WHERE CONVERT(datetime2(7),(UtcSample AT TIME ZONE 'UTC') AT TIME ZONE @BindSqlZone)<>JavaWallTime)
 THROW 59301,'Java/SQL wall-time mapping differs; do not change the running zone to make it pass.',1;
/* Binary comparisons include lengths/trailing spaces and override database case-insensitive collation. */
IF (SELECT COUNT_BIG(*) FROM dbo.AiCaseDataDeployment)>1
 OR EXISTS(SELECT 1 FROM dbo.AiCaseDataDeployment WHERE Id<>1
 OR CONVERT(varbinary(max),Issuer)<>CONVERT(varbinary(max),@BindIssuer)
 OR CONVERT(varbinary(max),Resource)<>CONVERT(varbinary(max),@BindResource)
 OR CONVERT(varbinary(max),Contract)<>CONVERT(varbinary(max),CONVERT(varchar(12),'case-read-v1'))
 OR CONVERT(varbinary(max),CONVERT(nvarchar(max),JavaTimeZone))<>CONVERT(varbinary(max),@BindJavaZone)
 OR CONVERT(varbinary(max),SqlTimeZone)<>CONVERT(varbinary(max),@BindSqlZone))
 THROW 59302,'Existing binding conflicts. STOP; never replace, change or remove it.',1;
