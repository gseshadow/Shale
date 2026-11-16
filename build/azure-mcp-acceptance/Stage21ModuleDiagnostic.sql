/* Read-only current-state diagnostic for the Stage 21 module assertion.
   Run only under separately authorized acceptance metadata collection.
   No module execution, DDL, receipt refresh, transaction or application-row reads.
   A rolled-back install leaves no module metadata: MISSING is not the historical
   failure cause and does not authorize replay. Never run an installer to populate this query. */
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

IF USER_NAME()<>N'dbo'
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),0)<>1
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59813,'Existing full owner metadata visibility required.',1;
;WITH Expected(ModuleName,DefinitionHash,OwnerLookup) AS (
 SELECT * FROM (VALUES
 (N'AiCimdDataLocateAccessOwnerV1',0x6c358f5c58362a3ca79303ae4bc4296f4772349216743862be3fa17a36f3b67f,1),
 (N'AiCimdDataLoadAuthorityV1',0x8e3907c529e7a9c9241c1cc5a47b0812b004856e87ee3bef71f1869852b82bc7,0),
 (N'AiCimdDataAuthenticateV1',0x5da2096e5f92f54033fc058b2fd710e7fe72f2c8b883f7b6e0ed64096c5fbe36,0),
 (N'AiCimdDataAppendAccessV1',0x8cbdbb3e875d2757571f3c2db4d64289cc0805742cf51b09296e20b88b265333,0),
 (N'AiCimdDataAdmitV1',0x3a8f1f250bbfaf5981ab7373dbeef07fe8162f58cb65162737ab88fc0434872e,0),
 (N'AiCimdDataSearchCasesV1',0x142505fa5fd4c3fb9574afa670af2303e8192b3d0dac9f3657b2eb4935661cf3,0),
 (N'AiCimdDataGetCaseOverviewV1',0xb99848d7a8e2b8ae75635cea0b02067ba42cbae7def13916fc9813c0bdf1013f,0),
 (N'AiCimdDataFinishCaseQuotaV1',0xb40ceade881d455d4b399d3a254737ced92379adf25f7ef0024621b7e9c4b868,0),
 (N'AiCimdDataAuthorizeCaseHandoffV1',0xe31f7b3de78a951d6115057543b764ab2f881dbd8747165f0bc4432b355afd6e,0),
 (N'AiCimdDataRecordCaseFailureV1',0xc36fe5be62a5f47dbdd707200b9c8ff19ceaf5ea006952a347be59b052fabef8,0),
 (N'AiCimdDataImmutableInvocation',0xf8be9680b7baae5b780d1d9af85bddc215596bca774531e441bc41aa0640f52b,0),
 (N'AiCimdImmutableFamilyV1',0x6f57d0cd6f0942cd7d577b212e27f6e4e2008dae87f0e816c9dfe04b8181d852,0),
 (N'AiCimdImmutableCredentialV1',0x21443ac7c353dd83327a690260162835b770d232c11cfbacd0ada397012ac808,0),
 (N'AiCimdLifecycle_CredentialV1',0x10c80b55f931b54600a032593ebe674ad3e6758062a75b2e991b0abff62eeedd,0),
 (N'AiCimdRevokeFamilyV1',0xb2c491da48cbf8f7099aa1e488aa89d3275369610817a4e6f6d4aabc179f2b27,0),
 (N'AiCimdImmutableReceiptV1',0xa0bf04f6e6687307d15c46292d3b9102dedb264b37c986a797430ec1a25119ff,0)
 ) v(ModuleName,DefinitionHash,OwnerLookup)
), Observed AS (
 SELECT e.ModuleName,e.DefinitionHash,e.OwnerLookup,o.object_id,
        COALESCE(o.principal_id,s.principal_id) AS EffectiveOwnerId,
        m.definition,m.uses_ansi_nulls,m.uses_quoted_identifier,m.execute_as_principal_id
 FROM Expected e
 LEFT JOIN sys.schemas s ON s.name=N'dbo'
 LEFT JOIN sys.objects o ON o.schema_id=s.schema_id AND o.name=e.ModuleName
 LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
), Checks AS (
 SELECT ModuleName,DefinitionHash,OwnerLookup,object_id,
   CASE WHEN object_id IS NOT NULL THEN 1 ELSE 0 END AS ObjectPresent,
   CASE WHEN definition IS NOT NULL THEN 1 ELSE 0 END AS DefinitionVisible,
   CASE WHEN object_id IS NOT NULL AND EffectiveOwnerId=1 THEN 1 ELSE 0 END AS OwnerMatches,
   CASE WHEN uses_ansi_nulls=1 THEN 1 ELSE 0 END AS AnsiNullsMatches,
   CASE WHEN uses_quoted_identifier=1 THEN 1 ELSE 0 END AS QuotedIdentifierMatches,
   CASE WHEN (OwnerLookup=1 AND execute_as_principal_id=-2)
          OR (OwnerLookup=0 AND execute_as_principal_id IS NULL AND definition IS NOT NULL)
        THEN 1 ELSE 0 END AS ExecuteAsMatches,
   CASE WHEN HASHBYTES('SHA2_256',definition)=DefinitionHash THEN 1 ELSE 0 END AS DefinitionHashMatches,
   CONVERT(varchar(64),HASHBYTES('SHA2_256',definition),2) AS ObservedDefinitionHash
 FROM Observed
)
SELECT ModuleName,ObjectPresent,DefinitionVisible,
       OwnerMatches,AnsiNullsMatches,QuotedIdentifierMatches,ExecuteAsMatches,DefinitionHashMatches,
       CONVERT(varchar(64),DefinitionHash,2) AS ExpectedDefinitionHash,ObservedDefinitionHash,
       CASE WHEN OwnerLookup=1 THEN 'OWNER' ELSE 'CALLER' END AS ExpectedExecutionContext,
       CASE WHEN ObjectPresent=0 THEN 'ABSENT_NOW_NO_HISTORICAL_METADATA'
            WHEN DefinitionVisible=0 THEN 'DEFINITION_UNAVAILABLE'
            WHEN OwnerMatches+AnsiNullsMatches+QuotedIdentifierMatches+ExecuteAsMatches+DefinitionHashMatches=5
            THEN 'MATCHES_ASSERTION_NOW' ELSE 'MISMATCH_NOW' END AS Observation,
       CASE WHEN ObjectPresent=1 AND DefinitionVisible=1
                 AND OwnerMatches+AnsiNullsMatches+QuotedIdentifierMatches+ExecuteAsMatches+DefinitionHashMatches=5
            THEN 0 ELSE 1 END AS CurrentAssertionFindingCount
FROM Checks ORDER BY ModuleName;
