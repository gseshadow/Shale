/* Read-only, PHI-safe Phase 2C-A verification. Results contain metadata, identifiers for tenants,
   fixed Kind/lifecycle labels, and aggregate counts only. Never select a Contact value or name. */
SET NOCOUNT ON;
BEGIN TRY
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL THROW 56500,'Verification requires NULL tenant context.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1) THROW 56501,'Approved administrative principal required.',1;
IF @OperatorVerifiedAllTenantVisibility<>1 THROW 56502,'Independently verify and acknowledge all-tenant visibility.',1;

SELECT t.name TableName,c.column_id,c.name ColumnName,TYPE_NAME(c.user_type_id) TypeName,c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,c.is_computed
 FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id WHERE SCHEMA_NAME(t.schema_id)=N'dbo' AND t.name IN(N'ContactPhoneNumbers',N'ContactEmailAddresses',N'ContactAddresses') ORDER BY t.name,c.column_id;
SELECT OBJECT_NAME(o.parent_object_id) TableName,o.name ConstraintName,o.type_desc,OBJECT_DEFINITION(o.object_id) Definition,o.is_disabled,o.is_not_trusted
 FROM sys.objects o WHERE o.parent_object_id IN(OBJECT_ID(N'dbo.ContactPhoneNumbers'),OBJECT_ID(N'dbo.ContactEmailAddresses'),OBJECT_ID(N'dbo.ContactAddresses')) AND o.type IN(N'C',N'F',N'D') ORDER BY TableName,o.type,o.name;
SELECT OBJECT_NAME(i.object_id) TableName,i.name,i.is_unique,i.is_disabled,i.filter_definition,STRING_AGG(c.name,N',') WITHIN GROUP(ORDER BY ic.index_column_id) Columns
 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE i.object_id IN(OBJECT_ID(N'dbo.ContactPhoneNumbers'),OBJECT_ID(N'dbo.ContactEmailAddresses'),OBJECT_ID(N'dbo.ContactAddresses')) GROUP BY i.object_id,i.name,i.is_unique,i.is_disabled,i.filter_definition ORDER BY TableName,i.name;
SELECT OBJECT_NAME(sp.target_object_id) TableName,SCHEMA_NAME(p.schema_id)+N'.'+p.name PolicyName,p.is_enabled,sp.predicate_type_desc,sp.predicate_definition
 FROM sys.security_predicates sp JOIN sys.security_policies p ON p.object_id=sp.security_policy_id WHERE sp.target_object_id IN(OBJECT_ID(N'dbo.ContactPhoneNumbers'),OBJECT_ID(N'dbo.ContactEmailAddresses'),OBJECT_ID(N'dbo.ContactAddresses'),OBJECT_ID(N'dbo.Contacts')) ORDER BY TableName;

SELECT N'ContactPhoneNumbers' TableName,ShaleClientId,Kind,IsDeleted,IsPrimary,COUNT_BIG(*) RowCount FROM dbo.ContactPhoneNumbers GROUP BY ShaleClientId,Kind,IsDeleted,IsPrimary
UNION ALL SELECT N'ContactEmailAddresses',ShaleClientId,Kind,IsDeleted,IsPrimary,COUNT_BIG(*) FROM dbo.ContactEmailAddresses GROUP BY ShaleClientId,Kind,IsDeleted,IsPrimary
UNION ALL SELECT N'ContactAddresses',ShaleClientId,Kind,IsDeleted,IsPrimary,COUNT_BIG(*) FROM dbo.ContactAddresses GROUP BY ShaleClientId,Kind,IsDeleted,IsPrimary ORDER BY TableName,ShaleClientId,Kind,IsDeleted,IsPrimary;
SELECT N'phone duplicate active primaries' Finding,COUNT_BIG(*) FindingCount FROM(SELECT 1 x FROM dbo.ContactPhoneNumbers WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)x
UNION ALL SELECT N'email duplicate active primaries',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.ContactEmailAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)x
UNION ALL SELECT N'address duplicate active primaries',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.ContactAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,ContactId HAVING COUNT_BIG(*)>1)x;
SELECT N'phone cross-tenant/orphan Contacts' Finding,COUNT_BIG(*) FindingCount FROM dbo.ContactPhoneNumbers p LEFT JOIN dbo.Contacts c ON c.ShaleClientId=p.ShaleClientId AND c.Id=p.ContactId WHERE c.Id IS NULL
UNION ALL SELECT N'email cross-tenant/orphan Contacts',COUNT_BIG(*) FROM dbo.ContactEmailAddresses e LEFT JOIN dbo.Contacts c ON c.ShaleClientId=e.ShaleClientId AND c.Id=e.ContactId WHERE c.Id IS NULL
UNION ALL SELECT N'address cross-tenant/orphan Contacts',COUNT_BIG(*) FROM dbo.ContactAddresses a LEFT JOIN dbo.Contacts c ON c.ShaleClientId=a.ShaleClientId AND c.Id=a.ContactId WHERE c.Id IS NULL;
SELECT N'phone invalid lifecycle' Finding,COUNT_BIG(*) FindingCount FROM dbo.ContactPhoneNumbers WHERE NOT((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
UNION ALL SELECT N'email invalid lifecycle',COUNT_BIG(*) FROM dbo.ContactEmailAddresses WHERE NOT((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL))
UNION ALL SELECT N'address invalid lifecycle',COUNT_BIG(*) FROM dbo.ContactAddresses WHERE NOT((IsDeleted=0 AND DeletedAt IS NULL AND DeletedByUserId IS NULL) OR(IsDeleted=1 AND IsPrimary=0 AND DeletedAt IS NOT NULL AND DeletedByUserId IS NOT NULL));

/* Aggregate source/backfill reconciliation; CASE expressions never project protected values. */
;WITH phone AS(SELECT c.ShaleClientId,c.Id,v.Kind,v.Value FROM dbo.Contacts c CROSS APPLY(VALUES(N'MOBILE',CONVERT(nvarchar(255),c.PhoneCell)),(N'HOME',CONVERT(nvarchar(255),c.PhoneHome)),(N'WORK',CONVERT(nvarchar(255),c.PhoneWork)))v(Kind,Value) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL),
email AS(SELECT c.ShaleClientId,c.Id,v.Kind,v.Value FROM dbo.Contacts c CROSS APPLY(VALUES(N'PERSONAL',CONVERT(nvarchar(320),c.EmailPersonal)),(N'WORK',CONVERT(nvarchar(320),c.EmailWork)),(N'OTHER',CONVERT(nvarchar(320),c.EmailOther)))v(Kind,Value) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL),
addr AS(SELECT c.ShaleClientId,c.Id,v.Kind,v.Value FROM dbo.Contacts c CROSS APPLY(VALUES(N'HOME',CONVERT(nvarchar(max),c.AddressHome)),(N'WORK',CONVERT(nvarchar(max),c.AddressWork)),(N'OTHER',CONVERT(nvarchar(max),c.AddressOther)))v(Kind,Value) WHERE NULLIF(LTRIM(RTRIM(v.Value)),N'') IS NOT NULL)
SELECT N'phone source legacy population' Metric,COUNT_BIG(*) AggregateCount FROM phone UNION ALL SELECT N'phone backfilled matches',COUNT_BIG(*) FROM phone s WHERE EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.ContactId=s.Id AND p.Kind=s.Kind AND p.DisplayNumber=s.Value)
UNION ALL SELECT N'phone missing backfills',COUNT_BIG(*) FROM phone s WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.ContactId=s.Id AND p.Kind=s.Kind AND p.DisplayNumber=s.Value)
UNION ALL SELECT N'phone duplicate backfills',COUNT_BIG(*) FROM phone s CROSS APPLY(SELECT COUNT_BIG(*) n FROM dbo.ContactPhoneNumbers p WHERE p.ShaleClientId=s.ShaleClientId AND p.ContactId=s.Id AND p.Kind=s.Kind AND p.DisplayNumber=s.Value)x WHERE x.n>1
UNION ALL SELECT N'email source legacy population',COUNT_BIG(*) FROM email UNION ALL SELECT N'email backfilled matches',COUNT_BIG(*) FROM email s WHERE EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=s.ShaleClientId AND e.ContactId=s.Id AND e.Kind=s.Kind AND e.EmailAddress=s.Value)
UNION ALL SELECT N'email missing backfills',COUNT_BIG(*) FROM email s WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=s.ShaleClientId AND e.ContactId=s.Id AND e.Kind=s.Kind AND e.EmailAddress=s.Value)
UNION ALL SELECT N'email duplicate backfills',COUNT_BIG(*) FROM email s CROSS APPLY(SELECT COUNT_BIG(*) n FROM dbo.ContactEmailAddresses e WHERE e.ShaleClientId=s.ShaleClientId AND e.ContactId=s.Id AND e.Kind=s.Kind AND e.EmailAddress=s.Value)x WHERE x.n>1
UNION ALL SELECT N'address source legacy population',COUNT_BIG(*) FROM addr UNION ALL SELECT N'address backfilled matches',COUNT_BIG(*) FROM addr s WHERE EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=s.ShaleClientId AND a.ContactId=s.Id AND a.Kind=s.Kind AND a.LegacyAddressText=s.Value)
UNION ALL SELECT N'address missing backfills',COUNT_BIG(*) FROM addr s WHERE NOT EXISTS(SELECT 1 FROM dbo.ContactAddresses a WHERE a.ShaleClientId=s.ShaleClientId AND a.ContactId=s.Id AND a.Kind=s.Kind AND a.LegacyAddressText=s.Value)
UNION ALL SELECT N'address duplicate backfills',COUNT_BIG(*) FROM addr s CROSS APPLY(SELECT COUNT_BIG(*) n FROM dbo.ContactAddresses a WHERE a.ShaleClientId=s.ShaleClientId AND a.ContactId=s.Id AND a.Kind=s.Kind AND a.LegacyAddressText=s.Value)x WHERE x.n>1;
END TRY BEGIN CATCH THROW; END CATCH;
