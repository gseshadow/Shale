/* Included by the four entry points; ONE batch, no GO. No live-state auto-adoption.
   Reviewed hashes are SHA2_256 of SQL Server nvarchar bytes (UTF-16LE), hex without 0x.
   ReviewedOtherPredicatesHash pins ALL predicates outside the three targets, across ALL policies.
   Receipt is bounded policy metadata, never an application row or a new grant.
   Do not set review inputs by querying the destination and copying its current values unchecked. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF @@TRANCOUNT<>0 THROW 58500,N'Phase1R requires no ambient transaction.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @LockResult int;
 EXEC @LockResult=sys.sp_getapplock @Resource=N'Shale.Phase1R',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @LockResult<0 THROW 58501,N'Phase1R lock unavailable.',1;
 IF USER_NAME()<>N'dbo' OR DB_NAME() COLLATE Latin1_General_100_BIN2<>N'$(ExpectedDatabase)' COLLATE Latin1_General_100_BIN2
  THROW 58502,N'Phase1R database/metadata authority mismatch.',1;
 IF CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')) COLLATE Latin1_General_100_BIN2
       <>N'$(ReviewedCollation)' COLLATE Latin1_General_100_BIN2
  THROW 58503,N'Phase1R identifier collation requires review.',1;
 DECLARE @Policy int=OBJECT_ID(N'sec.TenantFilter'),@Strict int=OBJECT_ID(N'sec.fn_FilterByTenant',N'IF'),
         @Overlay int=OBJECT_ID(N'sec.fn_FilterByTenantOrGlobal',N'IF');
 IF NOT EXISTS(SELECT 1 FROM sys.security_policies WHERE object_id=@Policy AND name COLLATE Latin1_General_100_BIN2=N'TenantFilter' AND is_enabled=1 AND is_schema_bound=1)
  THROW 58504,N'Phase1R requires the existing enabled schema-bound policy.',1;
 IF (SELECT COUNT(*) FROM sys.schemas WHERE name COLLATE Latin1_General_100_BIN2 IN(N'dbo',N'sec') AND principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))<>2
 OR EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
           WHERE o.object_id IN(@Policy,@Strict,@Overlay,OBJECT_ID(N'dbo.Users'),OBJECT_ID(N'dbo.auditLog'),OBJECT_ID(N'dbo.Statuses'))
           AND COALESCE(o.principal_id,s.principal_id)<>DATABASE_PRINCIPAL_ID(N'dbo'))
  THROW 58505,N'Phase1R unexpected schema/object ownership.',1;
 DECLARE @StrictHash varchar(64),@OverlayHash varchar(64);
 SELECT @StrictHash=CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2) FROM sys.sql_modules
 WHERE object_id=@Strict AND is_schema_bound=1 AND execute_as_principal_id IS NULL AND uses_ansi_nulls=1 AND uses_quoted_identifier=1;
 SELECT @OverlayHash=CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2) FROM sys.sql_modules
 WHERE object_id=@Overlay AND is_schema_bound=1 AND execute_as_principal_id IS NULL AND uses_ansi_nulls=1 AND uses_quoted_identifier=1;
 IF @StrictHash IS NULL OR @OverlayHash IS NULL
 OR @StrictHash<> '$(ReviewedStrictHash)' OR @OverlayHash<> '$(ReviewedOverlayHash)'
 OR LEN('$(ReviewedStrictHash)')<>64 OR LEN('$(ReviewedOverlayHash)')<>64
 OR (SELECT COUNT(*) FROM sys.parameters WHERE object_id IN(@Strict,@Overlay) AND parameter_id=1
     AND system_type_id=56 AND user_type_id=56 AND is_output=0)<>2
 OR (SELECT COUNT(*) FROM sys.parameters WHERE object_id IN(@Strict,@Overlay))<>2
  THROW 58506,N'Phase1R function definition/signature is not reviewed.',1;
 DECLARE @Targets TABLE(Name sysname PRIMARY KEY,Id int,Definition nvarchar(256),Added bit NOT NULL DEFAULT 0);
 INSERT @Targets(Name,Id,Definition) VALUES
 (N'Users',OBJECT_ID(N'dbo.Users',N'U'),N'([sec].[fn_FilterByTenant]([ShaleClientId]))'),
 (N'auditLog',OBJECT_ID(N'dbo.auditLog',N'U'),N'([sec].[fn_FilterByTenant]([ShaleClientId]))'),
 (N'Statuses',OBJECT_ID(N'dbo.Statuses',N'U'),N'([sec].[fn_FilterByTenantOrGlobal]([ShaleClientId]))');
 IF EXISTS(SELECT 1 FROM @Targets t LEFT JOIN sys.objects o ON o.object_id=t.Id
           WHERE t.Id IS NULL OR o.name COLLATE Latin1_General_100_BIN2<>t.Name COLLATE Latin1_General_100_BIN2)
 OR (SELECT COUNT(*) FROM @Targets t JOIN sys.columns c ON c.object_id=t.Id
     WHERE c.name COLLATE Latin1_General_100_BIN2=N'ShaleClientId' AND c.system_type_id=56 AND c.user_type_id=56 AND c.is_computed=0)<>3
  THROW 58507,N'Phase1R exact target identifiers/int tenant columns required.',1;
 -- Reject every unknown target predicate, including BLOCK, alternate policies and alternate columns.
 IF EXISTS(SELECT 1 FROM sys.security_predicates p JOIN @Targets t ON t.Id=p.target_object_id
           WHERE p.object_id<>@Policy OR p.predicate_type_desc<>N'FILTER'
           OR p.predicate_definition COLLATE Latin1_General_100_BIN2<>t.Definition COLLATE Latin1_General_100_BIN2)
 OR EXISTS(SELECT t.Id FROM @Targets t JOIN sys.security_predicates p ON p.target_object_id=t.Id GROUP BY t.Id HAVING COUNT(*)<>1)
  THROW 58508,N'Phase1R unknown/conflicting target predicate.',1;
 DECLARE @Other nvarchar(max)=(SELECT p.* FROM sys.security_predicates p WHERE NOT EXISTS
  (SELECT 1 FROM @Targets t WHERE t.Id=p.target_object_id) ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH);
 IF CONVERT(varchar(64),HASHBYTES('SHA2_256',@Other),2)<>'$(ReviewedOtherPredicatesHash)' OR LEN('$(ReviewedOtherPredicatesHash)')<>64
  THROW 58509,N'Phase1R unrelated predicate inventory differs from review.',1;
 DECLARE @Before nvarchar(max)=(SELECT p.* FROM sys.security_predicates p ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH),
         @Receipt nvarchar(4000),@After nvarchar(max),@Added nvarchar(max),@Value sql_variant;
 SELECT @Receipt=CONVERT(nvarchar(4000),value) FROM sys.extended_properties
 WHERE class=3 AND major_id=SCHEMA_ID(N'sec') AND minor_id=0 AND name=N'Shale.Phase1R.v1';
 IF @Receipt IS NOT NULL
 BEGIN
  IF ISJSON(@Receipt)<>1 THROW 58510,N'Phase1R receipt malformed.',1;
  IF COALESCE(JSON_VALUE(@Receipt,'$.version'),'')<>'1' OR COALESCE(JSON_VALUE(@Receipt,'$.policy'),'')<>CONVERT(varchar(20),@Policy)
   OR COALESCE(JSON_VALUE(@Receipt,'$.created'),'')<>(SELECT CONVERT(varchar(33),create_date,126) FROM sys.objects WHERE object_id=@Policy)
   OR COALESCE(JSON_VALUE(@Receipt,'$.strict'),'')<>@StrictHash OR COALESCE(JSON_VALUE(@Receipt,'$.overlay'),'')<>@OverlayHash
   OR COALESCE(JSON_VALUE(@Receipt,'$.other'),'')<>'$(ReviewedOtherPredicatesHash)'
   OR COALESCE(JSON_VALUE(@Receipt,'$.after'),'')<>CONVERT(varchar(64),HASHBYTES('SHA2_256',@Before),2)
   OR COALESCE(LEN(JSON_VALUE(@Receipt,'$.before')),0)<>64
   OR COALESCE(JSON_VALUE(@Receipt,'$.database'),'')<>CONVERT(varchar(36),(SELECT database_guid FROM sys.database_recovery_status WHERE database_id=DB_ID()))
   OR (SELECT COUNT(*) FROM OPENJSON(@Receipt))<>10
   OR NOT EXISTS(SELECT 1 FROM OPENJSON(@Receipt) WHERE [key]='added' AND type=4)
   OR (SELECT COUNT(*) FROM OPENJSON(@Receipt,'$.added')) NOT BETWEEN 1 AND 3
   OR EXISTS(SELECT 1 FROM OPENJSON(@Receipt,'$.added') j LEFT JOIN @Targets t ON t.Id=TRY_CONVERT(int,JSON_VALUE(j.value,'$.target_object_id'))
             LEFT JOIN sys.security_predicates p ON p.target_object_id=t.Id AND p.object_id=@Policy
              AND p.security_predicate_id=TRY_CONVERT(int,JSON_VALUE(j.value,'$.security_predicate_id'))
             WHERE t.Id IS NULL OR p.security_predicate_id IS NULL OR (SELECT COUNT(*) FROM OPENJSON(j.value))<>2)
   OR EXISTS(SELECT JSON_VALUE(value,'$.target_object_id') FROM OPENJSON(@Receipt,'$.added') GROUP BY JSON_VALUE(value,'$.target_object_id') HAVING COUNT(*)<>1)
   THROW 58511,N'Phase1R receipt/catalog drift; no mutation permitted.',1;
 END;
 IF @Phase1RMode NOT IN('APPLY','ROLLBACK','PREFLIGHT','POSTCHECK') THROW 58512,N'Phase1R invalid entry point.',1;
 IF @Phase1RMode='ROLLBACK' AND @Receipt IS NOT NULL
 BEGIN
  -- Current full catalog equals recorded after state. Only exact recorded predicate IDs may be removed.
  DECLARE @TargetName sysname,@Sql nvarchar(500);
  DECLARE removals CURSOR LOCAL FAST_FORWARD FOR
   SELECT t.Name FROM @Targets t JOIN sys.security_predicates p ON p.target_object_id=t.Id
   JOIN OPENJSON(@Receipt,'$.added') j ON p.target_object_id=TRY_CONVERT(int,JSON_VALUE(j.value,'$.target_object_id'))
    AND p.security_predicate_id=TRY_CONVERT(int,JSON_VALUE(j.value,'$.security_predicate_id')) AND p.object_id=@Policy;
  OPEN removals; FETCH NEXT FROM removals INTO @TargetName;
  WHILE @@FETCH_STATUS=0
  BEGIN
   SET @Sql=N'ALTER SECURITY POLICY sec.TenantFilter DROP FILTER PREDICATE ON dbo.'+QUOTENAME(@TargetName);
   EXEC sys.sp_executesql @Sql;
   FETCH NEXT FROM removals INTO @TargetName;
  END;
  CLOSE removals; DEALLOCATE removals;
  SET @After=(SELECT p.* FROM sys.security_predicates p ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH);
  IF CONVERT(varchar(64),HASHBYTES('SHA2_256',@After),2)<>JSON_VALUE(@Receipt,'$.before')
   THROW 58513,N'Phase1R rollback did not restore exact prior predicates.',1;
  EXEC sys.sp_dropextendedproperty @name=N'Shale.Phase1R.v1',@level0type=N'SCHEMA',@level0name=N'sec';
 END;
 IF @Phase1RMode='APPLY' AND @Receipt IS NULL
 BEGIN
  UPDATE t SET Added=1 FROM @Targets t WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates p WHERE p.target_object_id=t.Id);
  IF EXISTS(SELECT 1 FROM @Targets WHERE Name=N'Users' AND Added=1)
   EXEC sys.sp_executesql N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users;';
  IF EXISTS(SELECT 1 FROM @Targets WHERE Name=N'auditLog' AND Added=1)
   EXEC sys.sp_executesql N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.auditLog;';
  IF EXISTS(SELECT 1 FROM @Targets WHERE Name=N'Statuses' AND Added=1)
   EXEC sys.sp_executesql N'ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenantOrGlobal(ShaleClientId) ON dbo.Statuses;';
  SET @After=(SELECT p.* FROM sys.security_predicates p ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH);
  SET @Added=(SELECT p.target_object_id,p.security_predicate_id FROM sys.security_predicates p JOIN @Targets t ON t.Id=p.target_object_id
              WHERE t.Added=1 AND p.object_id=@Policy ORDER BY p.target_object_id FOR JSON PATH);
  -- All-pre-existing matching coverage is a true no-op: do not claim ownership of it.
  IF EXISTS(SELECT 1 FROM @Targets WHERE Added=1)
  BEGIN
   SET @Receipt=(SELECT 1 AS version,@Policy AS policy,
    (SELECT CONVERT(varchar(33),create_date,126) FROM sys.objects WHERE object_id=@Policy) AS created,
    CONVERT(varchar(36),(SELECT database_guid FROM sys.database_recovery_status WHERE database_id=DB_ID())) AS [database],
    @StrictHash AS strict,@OverlayHash AS overlay,'$(ReviewedOtherPredicatesHash)' AS other,
    CONVERT(varchar(64),HASHBYTES('SHA2_256',@Before),2) AS before,
    CONVERT(varchar(64),HASHBYTES('SHA2_256',@After),2) AS after,JSON_QUERY(@Added) AS added FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
   SET @Value=@Receipt;
   EXEC sys.sp_addextendedproperty @name=N'Shale.Phase1R.v1',@value=@Value,@level0type=N'SCHEMA',@level0name=N'sec';
  END;
 END;
 IF @Phase1RMode IN('APPLY','POSTCHECK') AND
  (SELECT COUNT(*) FROM sys.security_predicates p JOIN @Targets t ON t.Id=p.target_object_id
   WHERE p.object_id=@Policy AND p.predicate_type_desc=N'FILTER'
   AND p.predicate_definition COLLATE Latin1_General_100_BIN2=t.Definition COLLATE Latin1_General_100_BIN2)<>3
  THROW 58514,N'Phase1R three FILTER postcheck failed.',1;
 -- Reject concurrent/unexpected unrelated predicate drift before committing any candidate state.
 SET @Other=(SELECT p.* FROM sys.security_predicates p WHERE NOT EXISTS
  (SELECT 1 FROM @Targets t WHERE t.Id=p.target_object_id) ORDER BY p.object_id,p.security_predicate_id FOR JSON PATH);
 IF CONVERT(varchar(64),HASHBYTES('SHA2_256',@Other),2)<>'$(ReviewedOtherPredicatesHash)'
  THROW 58509,N'Phase1R unrelated predicate inventory changed during execution.',1;
 COMMIT;
 SELECT @Phase1RMode AS Phase1RMode,N'PASS' AS Outcome;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
