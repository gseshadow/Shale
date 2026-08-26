/* Contacts Phase 2C-A: structured contact-point foundation and lossless legacy backfill.
   Forward-only, additive, and rerunnable. DO NOT run without the documented all-tenant preflight. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL
 THROW 56400,'Phase 2C-A requires a NULL ShaleClientId SESSION_CONTEXT.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR
   (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1)
 THROW 56401,'Use an approved all-tenant administrative principal; application principals are forbidden.',1;
IF @OperatorVerifiedAllTenantVisibility<>1
 THROW 56402,'Independently verify all-tenant visibility, then explicitly acknowledge it.',1;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.Contacts',N'U') IS NULL OR OBJECT_ID(N'dbo.Users',N'U') IS NULL OR OBJECT_ID(N'dbo.ShaleClients',N'U') IS NULL
 THROW 56403,'Contacts, Users, and ShaleClients are required.',1;
IF OBJECT_ID(N'sec.fn_FilterByTenant',N'IF') IS NULL THROW 56404,'The strict tenant predicate function is required.',1;
DECLARE @PolicyId int,@PolicyQualified nvarchar(517);
IF (SELECT COUNT(*) FROM sys.security_policies WHERE name=N'TenantFilter')<>1 THROW 56405,'Exactly one TenantFilter policy is required.',1;
SELECT @PolicyId=object_id,@PolicyQualified=QUOTENAME(SCHEMA_NAME(schema_id))+N'.'+QUOTENAME(name)
 FROM sys.security_policies WHERE name=N'TenantFilter' AND is_enabled=1;
IF @PolicyId IS NULL THROW 56406,'TenantFilter must be enabled.',1;

/* Discovery contract: refuse a different live Contacts shape rather than guessing types. */
DECLARE @Legacy TABLE(ColumnName sysname);
INSERT @Legacy VALUES(N'PhoneCell'),(N'PhoneHome'),(N'PhoneWork'),(N'EmailPersonal'),(N'EmailWork'),(N'EmailOther'),(N'AddressHome'),(N'AddressWork'),(N'AddressOther');
IF EXISTS(SELECT 1 FROM @Legacy l LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.Contacts') AND c.name=l.ColumnName
 WHERE c.column_id IS NULL OR TYPE_NAME(c.system_type_id)<>N'nvarchar' OR c.is_nullable<>1)
 THROW 56407,'Legacy Contact point columns must exist as nullable nvarchar; review the live schema.',1;
IF NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'Id' AND system_type_id=56 AND is_nullable=0)
 OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'ShaleClientId' AND system_type_id=56 AND is_nullable=0)
 THROW 56408,'Contacts Id and ShaleClientId must be int NOT NULL.',1;
IF EXISTS(SELECT 1 FROM dbo.Contacts GROUP BY ShaleClientId,Id HAVING COUNT_BIG(*)>1) THROW 56409,'Duplicate tenant Contact keys prevent the composite FK.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Contacts') AND name=N'UX_Contacts_ShaleClientId_Id')
 CREATE UNIQUE INDEX UX_Contacts_ShaleClientId_Id ON dbo.Contacts(ShaleClientId,Id);

IF OBJECT_ID(N'dbo.ContactPhoneNumbers',N'U') IS NULL CREATE TABLE dbo.ContactPhoneNumbers(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactPhoneNumbers PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL,
 Kind nvarchar(16) NOT NULL, DisplayNumber nvarchar(255) NOT NULL, NormalizedNumber nvarchar(32) NULL, Extension nvarchar(20) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_ContactPhoneNumbers_IsPrimary DEFAULT(0), SortOrder int NOT NULL CONSTRAINT DF_ContactPhoneNumbers_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactPhoneNumbers_IsDeleted DEFAULT(0), CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ContactPhoneNumbers_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, DeletedAt datetime2(7) NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactPhoneNumbers_Kind CHECK(Kind IN(N'MOBILE',N'HOME',N'WORK',N'FAX',N'OTHER')),
 CONSTRAINT CK_ContactPhoneNumbers_SortOrder CHECK(SortOrder>=0), CONSTRAINT CK_ContactPhoneNumbers_Display CHECK(LEN(DisplayNumber)>0),
 CONSTRAINT CK_ContactPhoneNumbers_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

IF OBJECT_ID(N'dbo.ContactEmailAddresses',N'U') IS NULL CREATE TABLE dbo.ContactEmailAddresses(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactEmailAddresses PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL,
 Kind nvarchar(16) NOT NULL, EmailAddress nvarchar(320) NOT NULL, NormalizedEmail nvarchar(320) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_ContactEmailAddresses_IsPrimary DEFAULT(0), SortOrder int NOT NULL CONSTRAINT DF_ContactEmailAddresses_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactEmailAddresses_IsDeleted DEFAULT(0), CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ContactEmailAddresses_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, DeletedAt datetime2(7) NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactEmailAddresses_Kind CHECK(Kind IN(N'PERSONAL',N'WORK',N'OTHER')),
 CONSTRAINT CK_ContactEmailAddresses_SortOrder CHECK(SortOrder>=0), CONSTRAINT CK_ContactEmailAddresses_Value CHECK(LEN(EmailAddress)>0),
 CONSTRAINT CK_ContactEmailAddresses_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

IF OBJECT_ID(N'dbo.ContactAddresses',N'U') IS NULL CREATE TABLE dbo.ContactAddresses(
 Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_ContactAddresses PRIMARY KEY, ShaleClientId int NOT NULL, ContactId int NOT NULL,
 Kind nvarchar(16) NOT NULL, AddressLine1 nvarchar(255) NULL, AddressLine2 nvarchar(255) NULL, City nvarchar(100) NULL,
 StateOrProvince nvarchar(100) NULL, PostalCode nvarchar(32) NULL, CountryCode char(2) NULL, LegacyAddressText nvarchar(max) NULL,
 IsPrimary bit NOT NULL CONSTRAINT DF_ContactAddresses_IsPrimary DEFAULT(0), SortOrder int NOT NULL CONSTRAINT DF_ContactAddresses_SortOrder DEFAULT(0),
 IsDeleted bit NOT NULL CONSTRAINT DF_ContactAddresses_IsDeleted DEFAULT(0), CreatedAt datetime2(7) NOT NULL CONSTRAINT DF_ContactAddresses_CreatedAt DEFAULT(SYSUTCDATETIME()), CreatedByUserId int NULL,
 UpdatedAt datetime2(7) NULL, UpdatedByUserId int NULL, DeletedAt datetime2(7) NULL, DeletedByUserId int NULL, RowVer rowversion NOT NULL,
 CONSTRAINT CK_ContactAddresses_Kind CHECK(Kind IN(N'HOME',N'WORK',N'OTHER')), CONSTRAINT CK_ContactAddresses_SortOrder CHECK(SortOrder>=0),
 CONSTRAINT CK_ContactAddresses_Content CHECK(AddressLine1 IS NOT NULL OR AddressLine2 IS NOT NULL OR City IS NOT NULL OR StateOrProvince IS NOT NULL OR PostalCode IS NOT NULL OR CountryCode IS NOT NULL OR LegacyAddressText IS NOT NULL),
 CONSTRAINT CK_ContactAddresses_DeleteFields CHECK((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR (IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL)));

/* Required columns are validated; unrelated future additive extensions are allowed. */
DECLARE @Columns TABLE(TableName sysname,ColumnName sysname,TypeName sysname,MaxLength int,Nullable bit,IdentityColumn bit,RowVersion bit);
INSERT @Columns VALUES
(N'ContactPhoneNumbers',N'Id',N'bigint',8,0,1,0),(N'ContactPhoneNumbers',N'ShaleClientId',N'int',4,0,0,0),(N'ContactPhoneNumbers',N'ContactId',N'int',4,0,0,0),(N'ContactPhoneNumbers',N'Kind',N'nvarchar',32,0,0,0),(N'ContactPhoneNumbers',N'DisplayNumber',N'nvarchar',510,0,0,0),(N'ContactPhoneNumbers',N'NormalizedNumber',N'nvarchar',64,1,0,0),(N'ContactPhoneNumbers',N'Extension',N'nvarchar',40,1,0,0),
(N'ContactEmailAddresses',N'Id',N'bigint',8,0,1,0),(N'ContactEmailAddresses',N'ShaleClientId',N'int',4,0,0,0),(N'ContactEmailAddresses',N'ContactId',N'int',4,0,0,0),(N'ContactEmailAddresses',N'Kind',N'nvarchar',32,0,0,0),(N'ContactEmailAddresses',N'EmailAddress',N'nvarchar',640,0,0,0),(N'ContactEmailAddresses',N'NormalizedEmail',N'nvarchar',640,1,0,0),
(N'ContactAddresses',N'Id',N'bigint',8,0,1,0),(N'ContactAddresses',N'ShaleClientId',N'int',4,0,0,0),(N'ContactAddresses',N'ContactId',N'int',4,0,0,0),(N'ContactAddresses',N'Kind',N'nvarchar',32,0,0,0),(N'ContactAddresses',N'AddressLine1',N'nvarchar',510,1,0,0),(N'ContactAddresses',N'AddressLine2',N'nvarchar',510,1,0,0),(N'ContactAddresses',N'City',N'nvarchar',200,1,0,0),(N'ContactAddresses',N'StateOrProvince',N'nvarchar',200,1,0,0),(N'ContactAddresses',N'PostalCode',N'nvarchar',64,1,0,0),(N'ContactAddresses',N'CountryCode',N'char',2,1,0,0),(N'ContactAddresses',N'LegacyAddressText',N'nvarchar',-1,1,0,0);
INSERT @Columns SELECT t,c,ty,l,n,0,r FROM (SELECT t,v.* FROM (VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))q(t) CROSS APPLY(VALUES
(N'IsPrimary',N'bit',1,0,0),(N'SortOrder',N'int',4,0,0),(N'IsDeleted',N'bit',1,0,0),(N'CreatedAt',N'datetime2',8,0,0),(N'CreatedByUserId',N'int',4,1,0),(N'UpdatedAt',N'datetime2',8,1,0),(N'UpdatedByUserId',N'int',4,1,0),(N'DeletedAt',N'datetime2',8,1,0),(N'DeletedByUserId',N'int',4,1,0),(N'RowVer',N'timestamp',8,0,1))v(c,ty,l,n,r))x;
IF EXISTS(SELECT 1 FROM @Columns e LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+e.TableName) AND c.name=e.ColumnName LEFT JOIN sys.types y ON y.user_type_id=c.user_type_id
 WHERE c.column_id IS NULL OR y.name<>e.TypeName OR c.max_length<>e.MaxLength OR c.is_nullable<>e.Nullable OR c.is_identity<>e.IdentityColumn OR IIF(c.system_type_id=189,1,0)<>e.RowVersion)
 THROW 56410,'A Phase 2C-A required column is missing or incompatible.',1;
IF EXISTS(SELECT 1 FROM sys.columns c WHERE c.object_id IN(OBJECT_ID(N'dbo.ContactPhoneNumbers'),OBJECT_ID(N'dbo.ContactEmailAddresses'),OBJECT_ID(N'dbo.ContactAddresses')) AND c.name IN(N'CreatedAt',N'UpdatedAt',N'DeletedAt') AND (c.precision<>27 OR c.scale<>7))
 THROW 56411,'Lifecycle timestamps must be datetime2(7).',1;

IF EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)
 OR EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)
 OR EXISTS(SELECT 1 FROM dbo.ContactAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)
 THROW 56412,'Duplicate active primaries require manual repair.',1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactPhoneNumbers') AND name=N'UX_ContactPhoneNumbers_ActivePrimary') CREATE UNIQUE INDEX UX_ContactPhoneNumbers_ActivePrimary ON dbo.ContactPhoneNumbers(ShaleClientId,ContactId) WHERE IsDeleted=0 AND IsPrimary=1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactEmailAddresses') AND name=N'UX_ContactEmailAddresses_ActivePrimary') CREATE UNIQUE INDEX UX_ContactEmailAddresses_ActivePrimary ON dbo.ContactEmailAddresses(ShaleClientId,ContactId) WHERE IsDeleted=0 AND IsPrimary=1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactAddresses') AND name=N'UX_ContactAddresses_ActivePrimary') CREATE UNIQUE INDEX UX_ContactAddresses_ActivePrimary ON dbo.ContactAddresses(ShaleClientId,ContactId) WHERE IsDeleted=0 AND IsPrimary=1;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactPhoneNumbers') AND name=N'IX_ContactPhoneNumbers_Display') CREATE INDEX IX_ContactPhoneNumbers_Display ON dbo.ContactPhoneNumbers(ShaleClientId,ContactId,IsDeleted,SortOrder,Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactEmailAddresses') AND name=N'IX_ContactEmailAddresses_Display') CREATE INDEX IX_ContactEmailAddresses_Display ON dbo.ContactEmailAddresses(ShaleClientId,ContactId,IsDeleted,SortOrder,Id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.ContactAddresses') AND name=N'IX_ContactAddresses_Display') CREATE INDEX IX_ContactAddresses_Display ON dbo.ContactAddresses(ShaleClientId,ContactId,IsDeleted,SortOrder,Id);

/* Compatible pre-existing objects must satisfy the owned defaults, checks, and exact index shapes. */
IF EXISTS(SELECT 1 FROM(SELECT t,c,d FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))t(t) CROSS APPLY(VALUES(N'IsPrimary',N'((0))'),(N'SortOrder',N'((0))'),(N'IsDeleted',N'((0))'),(N'CreatedAt',N'(sysutcdatetime())'))v(c,d))e
 LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.'+e.t) AND c.name=e.c LEFT JOIN sys.default_constraints d ON d.object_id=c.default_object_id
 WHERE d.object_id IS NULL OR LOWER(REPLACE(d.definition,N' ',N''))<>e.d) THROW 56417,'A required default is missing or incompatible.',1;
DECLARE @Checks TABLE(T sysname,N sysname,Token1 nvarchar(30),Token2 nvarchar(30));
INSERT @Checks VALUES
(N'ContactPhoneNumbers',N'CK_ContactPhoneNumbers_Kind',N'MOBILE',N'FAX'),(N'ContactPhoneNumbers',N'CK_ContactPhoneNumbers_SortOrder',N'SortOrder',N'>='),(N'ContactPhoneNumbers',N'CK_ContactPhoneNumbers_Display',N'DisplayNumber',N'LEN'),(N'ContactPhoneNumbers',N'CK_ContactPhoneNumbers_DeleteFields',N'DeletedAt',N'IsPrimary'),
(N'ContactEmailAddresses',N'CK_ContactEmailAddresses_Kind',N'PERSONAL',N'OTHER'),(N'ContactEmailAddresses',N'CK_ContactEmailAddresses_SortOrder',N'SortOrder',N'>='),(N'ContactEmailAddresses',N'CK_ContactEmailAddresses_Value',N'EmailAddress',N'LEN'),(N'ContactEmailAddresses',N'CK_ContactEmailAddresses_DeleteFields',N'DeletedAt',N'IsPrimary'),
(N'ContactAddresses',N'CK_ContactAddresses_Kind',N'HOME',N'OTHER'),(N'ContactAddresses',N'CK_ContactAddresses_SortOrder',N'SortOrder',N'>='),(N'ContactAddresses',N'CK_ContactAddresses_Content',N'LegacyAddressText',N'AddressLine1'),(N'ContactAddresses',N'CK_ContactAddresses_DeleteFields',N'DeletedAt',N'IsPrimary');
IF EXISTS(SELECT 1 FROM @Checks e LEFT JOIN sys.check_constraints c ON c.parent_object_id=OBJECT_ID(N'dbo.'+e.T) AND c.name=e.N WHERE c.object_id IS NULL OR c.is_disabled=1 OR c.is_not_trusted=1 OR c.definition NOT LIKE N'%'+e.Token1+N'%' OR c.definition NOT LIKE N'%'+e.Token2+N'%')
 THROW 56418,'A required CHECK is missing, disabled, untrusted, or incompatible.',1;
DECLARE @Indexes TABLE(T sysname,N sysname,U bit,F nvarchar(100),K nvarchar(200));
INSERT @Indexes VALUES
(N'ContactPhoneNumbers',N'PK_ContactPhoneNumbers',1,NULL,N'Id'),(N'ContactEmailAddresses',N'PK_ContactEmailAddresses',1,NULL,N'Id'),(N'ContactAddresses',N'PK_ContactAddresses',1,NULL,N'Id'),
(N'ContactPhoneNumbers',N'UX_ContactPhoneNumbers_ActivePrimary',1,N'([IsDeleted]=(0)AND[IsPrimary]=(1))',N'ShaleClientId,ContactId'),(N'ContactEmailAddresses',N'UX_ContactEmailAddresses_ActivePrimary',1,N'([IsDeleted]=(0)AND[IsPrimary]=(1))',N'ShaleClientId,ContactId'),(N'ContactAddresses',N'UX_ContactAddresses_ActivePrimary',1,N'([IsDeleted]=(0)AND[IsPrimary]=(1))',N'ShaleClientId,ContactId'),
(N'ContactPhoneNumbers',N'IX_ContactPhoneNumbers_Display',0,NULL,N'ShaleClientId,ContactId,IsDeleted,SortOrder,Id'),(N'ContactEmailAddresses',N'IX_ContactEmailAddresses_Display',0,NULL,N'ShaleClientId,ContactId,IsDeleted,SortOrder,Id'),(N'ContactAddresses',N'IX_ContactAddresses_Display',0,NULL,N'ShaleClientId,ContactId,IsDeleted,SortOrder,Id');
IF EXISTS(SELECT 1 FROM @Indexes e OUTER APPLY(SELECT i.object_id,i.index_id,i.is_unique,i.is_disabled,i.filter_definition FROM sys.indexes i WHERE i.object_id=OBJECT_ID(N'dbo.'+e.T) AND i.name=e.N)i OUTER APPLY(SELECT STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.key_ordinal) K FROM sys.index_columns ic JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id WHERE ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.is_included_column=0)x WHERE i.index_id IS NULL OR i.is_unique<>e.U OR i.is_disabled=1 OR ISNULL(REPLACE(i.filter_definition,N' ',N''),N'')<>ISNULL(e.F,N'') OR x.K<>e.K)
 THROW 56419,'A required index has incompatible keys, uniqueness, filter, or state.',1;

DECLARE @Fks TABLE(Name sysname,Child sysname,ChildColumns nvarchar(100),Parent sysname,ParentColumns nvarchar(100));
INSERT @Fks SELECT N'FK_'+t+N'_Contact_Tenant',t,N'ShaleClientId,ContactId',N'Contacts',N'ShaleClientId,Id' FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))x(t)
UNION ALL SELECT N'FK_'+t+N'_ShaleClient',t,N'ShaleClientId',N'ShaleClients',N'Id' FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))x(t);
DECLARE @Name sysname,@Child sysname,@ChildColumns nvarchar(100),@Parent sysname,@ParentColumns nvarchar(100),@sql nvarchar(max),@Actor sysname;
DECLARE f CURSOR LOCAL FAST_FORWARD FOR SELECT * FROM @Fks; OPEN f; FETCH NEXT FROM f INTO @Name,@Child,@ChildColumns,@Parent,@ParentColumns;
WHILE @@FETCH_STATUS=0 BEGIN IF OBJECT_ID(N'dbo.'+@Name,N'F') IS NULL BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@Child)+N' ADD CONSTRAINT '+QUOTENAME(@Name)+N' FOREIGN KEY('+@ChildColumns+N') REFERENCES dbo.'+QUOTENAME(@Parent)+N'('+@ParentColumns+N');'; EXEC(@sql); END FETCH NEXT FROM f INTO @Name,@Child,@ChildColumns,@Parent,@ParentColumns; END CLOSE f; DEALLOCATE f;
DECLARE a CURSOR LOCAL FAST_FORWARD FOR SELECT t,a FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))t(t) CROSS JOIN(VALUES(N'CreatedByUserId'),(N'UpdatedByUserId'),(N'DeletedByUserId'))a(a);
OPEN a; FETCH NEXT FROM a INTO @Child,@Actor; WHILE @@FETCH_STATUS=0 BEGIN SET @Name=N'FK_'+@Child+N'_'+@Actor; IF OBJECT_ID(N'dbo.'+@Name,N'F') IS NULL BEGIN SET @sql=N'ALTER TABLE dbo.'+QUOTENAME(@Child)+N' ADD CONSTRAINT '+QUOTENAME(@Name)+N' FOREIGN KEY('+QUOTENAME(@Actor)+N') REFERENCES dbo.Users(id);'; EXEC(@sql); END FETCH NEXT FROM a INTO @Child,@Actor; END CLOSE a; DEALLOCATE a;
/* Validate all required FKs semantically and prohibit cascade deletion. */
IF EXISTS(SELECT 1 FROM @Fks e LEFT JOIN sys.foreign_keys f ON f.parent_object_id=OBJECT_ID(N'dbo.'+e.Child) AND f.name=e.Name OUTER APPLY(SELECT STRING_AGG(pc.name,N',') WITHIN GROUP(ORDER BY fc.constraint_column_id) cc,STRING_AGG(rc.name,N',') WITHIN GROUP(ORDER BY fc.constraint_column_id) pc FROM sys.foreign_key_columns fc JOIN sys.columns pc ON pc.object_id=fc.parent_object_id AND pc.column_id=fc.parent_column_id JOIN sys.columns rc ON rc.object_id=fc.referenced_object_id AND rc.column_id=fc.referenced_column_id WHERE fc.constraint_object_id=f.object_id)x WHERE f.object_id IS NULL OR OBJECT_NAME(f.referenced_object_id)<>e.Parent OR x.cc<>e.ChildColumns OR x.pc<>e.ParentColumns OR f.delete_referential_action<>0)
 THROW 56413,'Required tenant-safe FK is missing, incompatible, or cascading.',1;
IF EXISTS(SELECT 1 FROM(SELECT t,a,N'FK_'+t+N'_'+a n FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))t(t) CROSS JOIN(VALUES(N'CreatedByUserId'),(N'UpdatedByUserId'),(N'DeletedByUserId'))a(a))e LEFT JOIN sys.foreign_keys f ON f.parent_object_id=OBJECT_ID(N'dbo.'+e.t) AND f.name=e.n LEFT JOIN sys.foreign_key_columns fc ON fc.constraint_object_id=f.object_id WHERE f.object_id IS NULL OR f.referenced_object_id<>OBJECT_ID(N'dbo.Users') OR COL_NAME(fc.parent_object_id,fc.parent_column_id)<>e.a OR COL_NAME(fc.referenced_object_id,fc.referenced_column_id)<>N'id')
 THROW 56414,'Actor FK mapping is incompatible.',1;

/* Lossless backfill. Whitespace decides population only; stored legacy text is never trimmed.
   Phone precedence MOBILE, HOME, WORK; email PERSONAL, WORK, OTHER; address HOME, WORK, OTHER. */
;WITH src AS(SELECT c.ShaleClientId,c.Id ContactId,v.Kind,v.Value,v.SortOrder FROM dbo.Contacts c CROSS APPLY(VALUES
 (N'MOBILE',CONVERT(nvarchar(255),c.PhoneCell),0),(N'HOME',CONVERT(nvarchar(255),c.PhoneHome),1),(N'WORK',CONVERT(nvarchar(255),c.PhoneWork),2))v(Kind,Value,SortOrder) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL), ranked AS(SELECT *,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,ContactId ORDER BY SortOrder) rn FROM src)
INSERT dbo.ContactPhoneNumbers(ShaleClientId,ContactId,Kind,DisplayNumber,NormalizedNumber,IsPrimary,SortOrder)
SELECT s.ShaleClientId,s.ContactId,s.Kind,s.Value,CASE WHEN s.Value LIKE N'+%' AND LEN(s.Value) BETWEEN 8 AND 16 AND SUBSTRING(s.Value,2,32) NOT LIKE N'%[^0-9]%' THEN s.Value END,
 IIF(s.rn=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.ContactId=s.ContactId AND p.IsDeleted=0 AND p.IsPrimary=1),1,0),s.SortOrder FROM ranked s
WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.ContactId=s.ContactId AND p.Kind=s.Kind AND p.DisplayNumber=s.Value);
;WITH src AS(SELECT c.ShaleClientId,c.Id ContactId,v.Kind,v.Value,v.SortOrder FROM dbo.Contacts c CROSS APPLY(VALUES
 (N'PERSONAL',CONVERT(nvarchar(320),c.EmailPersonal),0),(N'WORK',CONVERT(nvarchar(320),c.EmailWork),1),(N'OTHER',CONVERT(nvarchar(320),c.EmailOther),2))v(Kind,Value,SortOrder) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL), ranked AS(SELECT *,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,ContactId ORDER BY SortOrder) rn FROM src)
INSERT dbo.ContactEmailAddresses(ShaleClientId,ContactId,Kind,EmailAddress,NormalizedEmail,IsPrimary,SortOrder)
SELECT s.ShaleClientId,s.ContactId,s.Kind,s.Value,CASE WHEN s.Value=LTRIM(RTRIM(s.Value)) AND s.Value NOT LIKE N'% %' AND LEN(s.Value)-LEN(REPLACE(s.Value,N'@',N''))=1 AND s.Value LIKE N'%_@_%._%' THEN LOWER(s.Value) END,
 IIF(s.rn=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=s.ShaleClientId AND e.ContactId=s.ContactId AND e.IsDeleted=0 AND e.IsPrimary=1),1,0),s.SortOrder FROM ranked s
WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=s.ShaleClientId AND e.ContactId=s.ContactId AND e.Kind=s.Kind AND e.EmailAddress=s.Value);
;WITH src AS(SELECT c.ShaleClientId,c.Id ContactId,v.Kind,v.Value,v.SortOrder FROM dbo.Contacts c CROSS APPLY(VALUES
 (N'HOME',CONVERT(nvarchar(max),c.AddressHome),0),(N'WORK',CONVERT(nvarchar(max),c.AddressWork),1),(N'OTHER',CONVERT(nvarchar(max),c.AddressOther),2))v(Kind,Value,SortOrder) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL), ranked AS(SELECT *,ROW_NUMBER() OVER(PARTITION BY ShaleClientId,ContactId ORDER BY SortOrder) rn FROM src)
INSERT dbo.ContactAddresses(ShaleClientId,ContactId,Kind,LegacyAddressText,IsPrimary,SortOrder)
SELECT s.ShaleClientId,s.ContactId,s.Kind,s.Value,IIF(s.rn=1 AND NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=s.ShaleClientId AND a.ContactId=s.ContactId AND a.IsDeleted=0 AND a.IsPrimary=1),1,0),s.SortOrder FROM ranked s
WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=s.ShaleClientId AND a.ContactId=s.ContactId AND a.Kind=s.Kind AND a.LegacyAddressText=s.Value);

/* Attach exactly the established strict FILTER predicate; dbo.Contacts remains untouched. */
DECLARE r CURSOR LOCAL FAST_FORWARD FOR SELECT t FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))x(t);
OPEN r; FETCH NEXT FROM r INTO @Child; WHILE @@FETCH_STATUS=0 BEGIN
 IF NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE security_policy_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.'+@Child) AND predicate_type_desc=N'FILTER') BEGIN SET @sql=N'ALTER SECURITY POLICY '+@PolicyQualified+N' ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.'+QUOTENAME(@Child)+N';'; EXEC(@sql); END
 FETCH NEXT FROM r INTO @Child; END CLOSE r; DEALLOCATE r;
IF EXISTS(SELECT 1 FROM(VALUES(N'ContactPhoneNumbers'),(N'ContactEmailAddresses'),(N'ContactAddresses'))e(t) OUTER APPLY(SELECT COUNT(*) n,MIN(LOWER(REPLACE(REPLACE(REPLACE(REPLACE(sp.predicate_definition,N'[',N''),N']',N''),NCHAR(9),N''),N' ',N''))) d FROM sys.security_predicates sp WHERE sp.security_policy_id=@PolicyId AND sp.target_object_id=OBJECT_ID(N'dbo.'+e.t) AND sp.predicate_type_desc=N'FILTER')x WHERE x.n<>1 OR x.d NOT LIKE N'%fn_filterbytenant(shaleclientid)%')
 THROW 56415,'Each child table must have exactly the strict tenant FILTER predicate.',1;
IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE security_policy_id=@PolicyId AND target_object_id=OBJECT_ID(N'dbo.Contacts'))
 THROW 56416,'Phase 2C-A must not add RLS to dbo.Contacts.',1;
COMMIT TRANSACTION;
END TRY BEGIN CATCH IF @@TRANCOUNT>0 ROLLBACK TRANSACTION; THROW; END CATCH;
GO
