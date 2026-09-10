/* Organizations Phase 3A: structured contact-method foundation and conservative legacy backfill.
   Forward-only, additive, rerunnable, and intentionally not a runtime-authority cutover.
   Set both deployment variables only after verifying the target and all-tenant visibility. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
DECLARE @ExpectedDatabase sysname = N'Shale';
DECLARE @OperatorVerifiedAllTenantVisibility bit = 1;

IF @ExpectedDatabase = N'REPLACE_WITH_APPROVED_DATABASE'
   OR DB_NAME() <> @ExpectedDatabase
 THROW 57200,'Set @ExpectedDatabase to the approved database and reconnect to that database.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
 THROW 57201,'Phase 3A requires NULL ShaleClientId and PrincipalUserId session context.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR
 (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 57202,'Use an approved all-tenant administrative principal, never an application principal.',1;
IF @OperatorVerifiedAllTenantVisibility<>1
 THROW 57203,'Independently verify all-tenant visibility, then set the acknowledgement to 1.',1;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Organizations',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL
 THROW 57204,'Organizations, Users, and ShaleClients are required.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 57205,'sec.fn_FilterByTenant is required.',1;
DECLARE @PolicyId int,@Policy nvarchar(517),@sql nvarchar(max);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')<>1 THROW 57206,'Exactly one TenantFilter policy is required.',1;
SELECT @PolicyId=object_id,@Policy=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
 FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 57207,'TenantFilter must be enabled.',1;

DECLARE @Legacy table(ColumnName sysname,TypeName sysname,MaxLength int,Nullable bit);
INSERT @Legacy VALUES
    (N'Phone',      N'nvarchar', 60,  1),
    (N'Fax',        N'nvarchar', 60,  1),
    (N'Email',      N'nvarchar', 508, 1),
    (N'Website',    N'nvarchar', 600, 1),
    (N'Address1',   N'nvarchar', 400, 1),
    (N'Address2',   N'nvarchar', 400, 1),
    (N'City',       N'nvarchar', 200, 1),
    (N'State',      N'nvarchar', 100, 1),
    (N'PostalCode', N'nvarchar', 40,  1),
    (N'Country',    N'nvarchar', 200, 1);
IF EXISTS(SELECT 1 FROM @Legacy e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.Organizations') AND c.name=e.ColumnName
 LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id
 WHERE c.column_id IS NULL OR t.name<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.Nullable)
 THROW 57208,'Legacy Organization contact columns are missing or incompatible with the verified schema.',1;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'Id' AND system_type_id=56 AND is_nullable=0)
 OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'ShaleClientId' AND system_type_id=56 AND is_nullable=0)
 THROW 57209,'Organizations.Id and ShaleClientId must be int NOT NULL.',1;
IF EXISTS(SELECT 1 FROM dbo.Organizations GROUP BY ShaleClientId,Id HAVING COUNT_BIG(*)>1) THROW 57210,'Duplicate tenant Organization keys prevent composite ownership.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Organizations') AND name=N'UX_Organizations_ShaleClientId_Id')
 CREATE UNIQUE INDEX UX_Organizations_ShaleClientId_Id ON dbo.Organizations(ShaleClientId,Id);

IF OBJECT_ID(N'dbo.OrganizationPhoneNumbers',N'U') IS NULL CREATE TABLE dbo.OrganizationPhoneNumbers(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_OrganizationPhoneNumbers PRIMARY KEY,ShaleClientId int NOT NULL,OrganizationId int NOT NULL,
 Kind nvarchar(16) NOT NULL,DisplayNumber nvarchar(255) NOT NULL,NormalizedNumber nvarchar(32) NULL,Extension nvarchar(20) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_OrganizationPhoneNumbers_IsPrimary DEFAULT(0),SortOrder int NOT NULL CONSTRAINT DF_OrganizationPhoneNumbers_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_OrganizationPhoneNumbers_IsDeleted DEFAULT(0),CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_OrganizationPhoneNumbers_CreatedAt DEFAULT(SYSUTCDATETIME()),CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL,UpdatedByUserId int NULL,DeletedAt datetime2(7) NULL,DeletedByUserId int NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_OrganizationPhoneNumbers_Kind CHECK(Kind IN(N'MOBILE',N'HOME',N'WORK',N'FAX',N'OTHER')),
 CONSTRAINT CK_OrganizationPhoneNumbers_Order CHECK(SortOrder>=0),CONSTRAINT CK_OrganizationPhoneNumbers_Value CHECK(NULLIF(LTRIM(RTRIM(DisplayNumber)),N'') IS NOT NULL),
 CONSTRAINT CK_OrganizationPhoneNumbers_Delete CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));
IF OBJECT_ID(N'dbo.OrganizationEmailAddresses',N'U') IS NULL CREATE TABLE dbo.OrganizationEmailAddresses(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_OrganizationEmailAddresses PRIMARY KEY,ShaleClientId int NOT NULL,OrganizationId int NOT NULL,
 Kind nvarchar(16) NOT NULL,EmailAddress nvarchar(320) NOT NULL,NormalizedEmail nvarchar(320) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_OrganizationEmailAddresses_IsPrimary DEFAULT(0),SortOrder int NOT NULL CONSTRAINT DF_OrganizationEmailAddresses_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_OrganizationEmailAddresses_IsDeleted DEFAULT(0),CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_OrganizationEmailAddresses_CreatedAt DEFAULT(SYSUTCDATETIME()),CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL,UpdatedByUserId int NULL,DeletedAt datetime2(7) NULL,DeletedByUserId int NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_OrganizationEmailAddresses_Kind CHECK(Kind IN(N'PERSONAL',N'WORK',N'OTHER')),
 CONSTRAINT CK_OrganizationEmailAddresses_Order CHECK(SortOrder>=0),CONSTRAINT CK_OrganizationEmailAddresses_Value CHECK(NULLIF(LTRIM(RTRIM(EmailAddress)),N'') IS NOT NULL),
 CONSTRAINT CK_OrganizationEmailAddresses_Delete CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));
IF OBJECT_ID(N'dbo.OrganizationAddresses',N'U') IS NULL CREATE TABLE dbo.OrganizationAddresses(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_OrganizationAddresses PRIMARY KEY,ShaleClientId int NOT NULL,OrganizationId int NOT NULL,Kind nvarchar(16) NOT NULL,
 AddressLine1 nvarchar(400) NULL,AddressLine2 nvarchar(400) NULL,City nvarchar(200) NULL,StateOrProvince nvarchar(100) NULL,PostalCode nvarchar(40) NULL,Country nvarchar(200) NULL,LegacyAddressText nvarchar(max) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_OrganizationAddresses_IsPrimary DEFAULT(0),SortOrder int NOT NULL CONSTRAINT DF_OrganizationAddresses_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_OrganizationAddresses_IsDeleted DEFAULT(0),CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_OrganizationAddresses_CreatedAt DEFAULT(SYSUTCDATETIME()),CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL,UpdatedByUserId int NULL,DeletedAt datetime2(7) NULL,DeletedByUserId int NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_OrganizationAddresses_Kind CHECK(Kind IN(N'HOME',N'WORK',N'OTHER')),CONSTRAINT CK_OrganizationAddresses_Order CHECK(SortOrder>=0),
 CONSTRAINT CK_OrganizationAddresses_Content CHECK(COALESCE(NULLIF(LTRIM(RTRIM(AddressLine1)),N''),NULLIF(LTRIM(RTRIM(AddressLine2)),N''),NULLIF(LTRIM(RTRIM(City)),N''),NULLIF(LTRIM(RTRIM(StateOrProvince)),N''),NULLIF(LTRIM(RTRIM(PostalCode)),N''),NULLIF(LTRIM(RTRIM(Country)),N''),NULLIF(LTRIM(RTRIM(LegacyAddressText)),N'')) IS NOT NULL),
 CONSTRAINT CK_OrganizationAddresses_Delete CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));
IF OBJECT_ID(N'dbo.OrganizationWebsites',N'U') IS NULL CREATE TABLE dbo.OrganizationWebsites(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_OrganizationWebsites PRIMARY KEY,ShaleClientId int NOT NULL,OrganizationId int NOT NULL,
 Kind nvarchar(16) NOT NULL,Website nvarchar(600) NOT NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_OrganizationWebsites_IsPrimary DEFAULT(0),SortOrder int NOT NULL CONSTRAINT DF_OrganizationWebsites_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_OrganizationWebsites_IsDeleted DEFAULT(0),CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_OrganizationWebsites_CreatedAt DEFAULT(SYSUTCDATETIME()),CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL,UpdatedByUserId int NULL,DeletedAt datetime2(7) NULL,DeletedByUserId int NULL,RowVer rowversion NOT NULL,
 CONSTRAINT CK_OrganizationWebsites_Kind CHECK(Kind IN(N'MAIN',N'WORK',N'OTHER')),CONSTRAINT CK_OrganizationWebsites_Order CHECK(SortOrder>=0),
 CONSTRAINT CK_OrganizationWebsites_Value CHECK(NULLIF(LTRIM(RTRIM(Website)),N'') IS NOT NULL),
 CONSTRAINT CK_OrganizationWebsites_Delete CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

DECLARE @Tables table(TableName sysname); INSERT @Tables VALUES(N'OrganizationPhoneNumbers'),(N'OrganizationEmailAddresses'),(N'OrganizationAddresses'),(N'OrganizationWebsites');
IF EXISTS(SELECT 1 FROM @Tables t CROSS APPLY(VALUES(N'Id'),(N'ShaleClientId'),(N'OrganizationId'),(N'Kind'),(N'IsPrimary'),(N'SortOrder'),(N'IsDeleted'),(N'CreatedAt'),(N'CreatedByUserId'),(N'UpdatedAt'),(N'UpdatedByUserId'),(N'DeletedAt'),(N'DeletedByUserId'),(N'RowVer'))c(ColumnName)
 WHERE COL_LENGTH(N'dbo.'+t.TableName,c.ColumnName) IS NULL) THROW 57211,'A required structured Organization column is missing.',1;
IF COL_LENGTH(N'dbo.OrganizationPhoneNumbers',N'DisplayNumber') IS NULL OR COL_LENGTH(N'dbo.OrganizationPhoneNumbers',N'Extension') IS NULL
 OR COL_LENGTH(N'dbo.OrganizationEmailAddresses',N'EmailAddress') IS NULL OR COL_LENGTH(N'dbo.OrganizationAddresses',N'AddressLine1') IS NULL
 OR COL_LENGTH(N'dbo.OrganizationAddresses',N'Country') IS NULL OR COL_LENGTH(N'dbo.OrganizationWebsites',N'Website') IS NULL
 THROW 57212,'A structured value column is missing.',1;

DECLARE @t sysname,@name sysname,@actor sysname;
DECLARE tc CURSOR LOCAL FAST_FORWARD FOR SELECT TableName FROM @Tables; OPEN tc; FETCH NEXT FROM tc INTO @t;
WHILE @@FETCH_STATUS=0 BEGIN
 SET @name=N'FK_'+@t+N'_Organization_Tenant'; IF OBJECT_ID(N'dbo.'+@name,N'F') IS NULL BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@t)+N' WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' FOREIGN KEY(ShaleClientId,OrganizationId) REFERENCES dbo.Organizations(ShaleClientId,Id);'; EXEC sys.sp_executesql @sql; END;
 SET @name=N'FK_'+@t+N'_ShaleClient'; IF OBJECT_ID(N'dbo.'+@name,N'F') IS NULL BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@t)+N' WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' FOREIGN KEY(ShaleClientId) REFERENCES dbo.ShaleClients(Id);'; EXEC sys.sp_executesql @sql; END;
 DECLARE ac CURSOR LOCAL FAST_FORWARD FOR SELECT v FROM(VALUES(N'CreatedByUserId'),(N'UpdatedByUserId'),(N'DeletedByUserId'))x(v); OPEN ac; FETCH NEXT FROM ac INTO @actor;
 WHILE @@FETCH_STATUS=0 BEGIN SET @name=N'FK_'+@t+N'_'+@actor; IF OBJECT_ID(N'dbo.'+@name,N'F') IS NULL BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@t)+N' WITH CHECK ADD CONSTRAINT '+QUOTENAME(@name)+N' FOREIGN KEY('+QUOTENAME(@actor)+N') REFERENCES dbo.Users(id);'; EXEC sys.sp_executesql @sql; END; FETCH NEXT FROM ac INTO @actor; END CLOSE ac; DEALLOCATE ac;
 SET @name=N'UX_'+@t+N'_ActivePrimary'; IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.'+@t) AND name=@name) BEGIN SET @sql=N'CREATE UNIQUE INDEX '+QUOTENAME(@name)+N' ON dbo.'+QUOTENAME(@t)+N'(ShaleClientId,OrganizationId) WHERE IsDeleted=0 AND IsPrimary=1;'; EXEC sys.sp_executesql @sql; END;
 SET @name=N'IX_'+@t+N'_Display'; IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.'+@t) AND name=@name) BEGIN SET @sql=N'CREATE INDEX '+QUOTENAME(@name)+N' ON dbo.'+QUOTENAME(@t)+N'(ShaleClientId,OrganizationId,IsDeleted,SortOrder,Id);'; EXEC sys.sp_executesql @sql; END;
 FETCH NEXT FROM tc INTO @t; END CLOSE tc; DEALLOCATE tc;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationPhoneNumbers') AND name=N'UX_OrganizationPhoneNumbers_ActiveValue') CREATE UNIQUE INDEX UX_OrganizationPhoneNumbers_ActiveValue ON dbo.OrganizationPhoneNumbers(ShaleClientId,OrganizationId,Kind,DisplayNumber) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationEmailAddresses') AND name=N'UX_OrganizationEmailAddresses_ActiveValue') CREATE UNIQUE INDEX UX_OrganizationEmailAddresses_ActiveValue ON dbo.OrganizationEmailAddresses(ShaleClientId,OrganizationId,EmailAddress) WHERE IsDeleted=0;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.OrganizationWebsites') AND name=N'UX_OrganizationWebsites_ActiveValue') CREATE UNIQUE INDEX UX_OrganizationWebsites_ActiveValue ON dbo.OrganizationWebsites(ShaleClientId,OrganizationId,Website) WHERE IsDeleted=0;

/* A NULL actor identifies mechanical migration provenance. Historical matches, including deleted rows,
   suppress reinsertion. Existing structured rows are never updated or promoted. */
SET @sql=N';WITH s AS(SELECT ShaleClientId,Id OrganizationId,N''WORK'' Kind,LTRIM(RTRIM(Phone)) Value,0 SortOrder FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Phone)),N'''') IS NOT NULL
 UNION ALL SELECT ShaleClientId,Id,N''FAX'',LTRIM(RTRIM(Fax)),1 FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Fax)),N'''') IS NOT NULL)
INSERT dbo.OrganizationPhoneNumbers(ShaleClientId,OrganizationId,Kind,DisplayNumber,IsPrimary,SortOrder)
SELECT s.ShaleClientId,s.OrganizationId,s.Kind,s.Value,CASE WHEN NOT EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.OrganizationId=s.OrganizationId AND p.IsDeleted=0 AND p.IsPrimary=1) AND (s.Kind=N''WORK'' OR NOT EXISTS(SELECT 1 FROM dbo.Organizations o WHERE o.ShaleClientId=s.ShaleClientId AND o.Id=s.OrganizationId AND NULLIF(LTRIM(RTRIM(o.Phone)),N'''') IS NOT NULL)) THEN 1 ELSE 0 END,s.SortOrder FROM s
WHERE NOT EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.OrganizationId=s.OrganizationId AND p.Kind=s.Kind AND p.DisplayNumber=s.Value);
INSERT dbo.OrganizationEmailAddresses(ShaleClientId,OrganizationId,Kind,EmailAddress,NormalizedEmail,IsPrimary,SortOrder)
SELECT o.ShaleClientId,o.Id,N''WORK'',LTRIM(RTRIM(o.Email)),LOWER(LTRIM(RTRIM(o.Email))),CASE WHEN EXISTS(SELECT 1 FROM dbo.OrganizationEmailAddresses e WHERE e.ShaleClientId=o.ShaleClientId AND e.OrganizationId=o.Id AND e.IsDeleted=0 AND e.IsPrimary=1) THEN 0 ELSE 1 END,0 FROM dbo.Organizations o
WHERE NULLIF(LTRIM(RTRIM(o.Email)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationEmailAddresses e WHERE e.ShaleClientId=o.ShaleClientId AND e.OrganizationId=o.Id AND e.EmailAddress=LTRIM(RTRIM(o.Email)));
INSERT dbo.OrganizationAddresses(ShaleClientId,OrganizationId,Kind,AddressLine1,AddressLine2,City,StateOrProvince,PostalCode,Country,IsPrimary,SortOrder)
SELECT o.ShaleClientId,o.Id,N''WORK'',NULLIF(LTRIM(RTRIM(o.Address1)),N''''),NULLIF(LTRIM(RTRIM(o.Address2)),N''''),NULLIF(LTRIM(RTRIM(o.City)),N''''),NULLIF(LTRIM(RTRIM(o.State)),N''''),NULLIF(LTRIM(RTRIM(o.PostalCode)),N''''),NULLIF(LTRIM(RTRIM(o.Country)),N''''),CASE WHEN EXISTS(SELECT 1 FROM dbo.OrganizationAddresses a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND a.IsDeleted=0 AND a.IsPrimary=1) THEN 0 ELSE 1 END,0 FROM dbo.Organizations o
WHERE COALESCE(NULLIF(LTRIM(RTRIM(o.Address1)),N''''),NULLIF(LTRIM(RTRIM(o.Address2)),N''''),NULLIF(LTRIM(RTRIM(o.City)),N''''),NULLIF(LTRIM(RTRIM(o.State)),N''''),NULLIF(LTRIM(RTRIM(o.PostalCode)),N''''),NULLIF(LTRIM(RTRIM(o.Country)),N'''')) IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationAddresses a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND ISNULL(a.AddressLine1,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Address1)),N''''),N'''') AND ISNULL(a.AddressLine2,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Address2)),N''''),N'''') AND ISNULL(a.City,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.City)),N''''),N'''') AND ISNULL(a.StateOrProvince,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.State)),N''''),N'''') AND ISNULL(a.PostalCode,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.PostalCode)),N''''),N'''') AND ISNULL(a.Country,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Country)),N''''),N''''));
INSERT dbo.OrganizationWebsites(ShaleClientId,OrganizationId,Kind,Website,IsPrimary,SortOrder)
SELECT o.ShaleClientId,o.Id,N''MAIN'',LTRIM(RTRIM(o.Website)),CASE WHEN EXISTS(SELECT 1 FROM dbo.OrganizationWebsites w WHERE w.ShaleClientId=o.ShaleClientId AND w.OrganizationId=o.Id AND w.IsDeleted=0 AND w.IsPrimary=1) THEN 0 ELSE 1 END,0 FROM dbo.Organizations o
WHERE NULLIF(LTRIM(RTRIM(o.Website)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationWebsites w WHERE w.ShaleClientId=o.ShaleClientId AND w.OrganizationId=o.Id AND w.Website=LTRIM(RTRIM(o.Website)));';
EXEC sys.sp_executesql @sql;

DECLARE rls CURSOR LOCAL FAST_FORWARD FOR SELECT TableName FROM @Tables; OPEN rls; FETCH NEXT FROM rls INTO @t;
WHILE @@FETCH_STATUS=0 BEGIN
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.'+@t) AND object_id<>@PolicyId) THROW 57213,'A competing RLS predicate exists.',1;
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE object_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.'+@t)) BEGIN SET @sql=N'ALTER SECURITY POLICY '+@Policy+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.'+QUOTENAME(@t)+N';'; EXEC sys.sp_executesql @sql; END;
 FETCH NEXT FROM rls INTO @t; END CLOSE rls; DEALLOCATE rls;
IF EXISTS(SELECT 1 FROM @Tables e OUTER APPLY(SELECT COUNT(*) n,SUM(CASE WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(spr.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''),N'(',N''),N')',N''))=N'sec.fn_filterbytenantshaleclientid' AND spr.predicate_type_desc=N'FILTER' AND spr.operation IS NULL AND spr.operation_desc IS NULL AND sp.is_enabled=1 THEN 0 ELSE 1 END) unexpected FROM sys.security_predicates spr JOIN sys.security_policies sp ON spr.object_id=sp.object_id WHERE spr.target_object_id=OBJECT_ID(N'dbo.'+e.TableName))x WHERE x.n<>1 OR x.unexpected<>0)
 THROW 57214,'Each structured Organization table must have exactly the strict enabled tenant FILTER predicate.',1;
IF EXISTS(SELECT 1 FROM sys.foreign_keys f WHERE f.parent_object_id IN(OBJECT_ID(N'dbo.OrganizationPhoneNumbers'),OBJECT_ID(N'dbo.OrganizationEmailAddresses'),OBJECT_ID(N'dbo.OrganizationAddresses'),OBJECT_ID(N'dbo.OrganizationWebsites')) AND (f.is_disabled=1 OR f.is_not_trusted=1 OR f.delete_referential_action<>0))
 THROW 57215,'Structured Organization foreign keys must be enabled, trusted, and noncascading.',1;

COMMIT TRANSACTION;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO
