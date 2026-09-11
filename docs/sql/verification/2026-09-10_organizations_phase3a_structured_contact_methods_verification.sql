/* Read-only Organizations Phase 3A verification. Returns only metadata, IDs, and aggregate counts. */
SET NOCOUNT ON;
BEGIN TRY
DECLARE @ExpectedDatabase sysname=N'REPLACE_WITH_APPROVED_DATABASE';
DECLARE @OperatorVerifiedAllTenantVisibility bit=0;
IF @ExpectedDatabase=N'REPLACE_WITH_APPROVED_DATABASE' OR DB_NAME()<>@ExpectedDatabase THROW 57300,'Set @ExpectedDatabase to the approved database.',1;
IF SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 57301,'Verification requires NULL tenant and principal session context.',1;
IF USER_NAME() IN(N'shale_app',N'shale_runtime') OR (ISNULL(IS_SRVROLEMEMBER(N'sysadmin'),0)<>1 AND ISNULL(IS_MEMBER(N'db_owner'),0)<>1) THROW 57302,'Use an approved all-tenant administrative principal.',1;
IF @OperatorVerifiedAllTenantVisibility<>1 THROW 57303,'Acknowledge independently verified all-tenant visibility.',1;

DECLARE @Tables table(TableName sysname); INSERT @Tables VALUES(N'OrganizationPhoneNumbers'),(N'OrganizationEmailAddresses'),(N'OrganizationAddresses'),(N'OrganizationWebsites');
SELECT t.name TableName,c.column_id ColumnOrder,c.name ColumnName,ty.name DataType,c.max_length MaxLengthBytes,c.is_nullable IsNullable,c.is_identity IsIdentity
 FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id JOIN @Tables e ON e.TableName=t.name WHERE SCHEMA_NAME(t.schema_id)=N'dbo' ORDER BY t.name,c.column_id;
SELECT OBJECT_NAME(o.parent_object_id) TableName,o.type_desc ObjectType,o.name ObjectName
 FROM sys.objects o JOIN @Tables e ON e.TableName=OBJECT_NAME(o.parent_object_id) WHERE o.type IN(N'C',N'F',N'D') ORDER BY TableName,ObjectType,ObjectName;
SELECT OBJECT_NAME(i.object_id) TableName,i.name IndexName,i.is_unique IsUnique,i.has_filter HasFilter,i.filter_definition FilterDefinition,
 STRING_AGG(CONVERT(nvarchar(max),c.name),N',') WITHIN GROUP(ORDER BY ic.key_ordinal) KeyColumns
 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id AND ic.key_ordinal>0 JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 JOIN @Tables e ON e.TableName=OBJECT_NAME(i.object_id) GROUP BY i.object_id,i.name,i.is_unique,i.has_filter,i.filter_definition ORDER BY TableName,IndexName;
SELECT OBJECT_NAME(f.parent_object_id) ChildTable,f.name ForeignKeyName,OBJECT_NAME(f.referenced_object_id) ParentTable,f.delete_referential_action_desc DeleteAction,f.is_disabled IsDisabled,f.is_not_trusted IsNotTrusted
 FROM sys.foreign_keys f JOIN @Tables e ON e.TableName=OBJECT_NAME(f.parent_object_id) ORDER BY ChildTable,ForeignKeyName;
SELECT OBJECT_NAME(spr.target_object_id) TableName,sp.name PolicyName,sp.is_enabled PolicyEnabled,spr.predicate_type_desc PredicateType,spr.operation_desc Operation,spr.predicate_definition PredicateDefinition
 FROM sys.security_predicates spr JOIN sys.security_policies sp ON spr.object_id=sp.object_id JOIN @Tables e ON e.TableName=OBJECT_NAME(spr.target_object_id) ORDER BY TableName;

EXEC sys.sp_executesql N'SELECT N''OrganizationPhoneNumbers'' TableName,COUNT_BIG(*) TotalCount,SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END) ActiveCount,SUM(CASE WHEN IsDeleted=1 THEN 1 ELSE 0 END) DeletedCount FROM dbo.OrganizationPhoneNumbers
UNION ALL SELECT N''OrganizationEmailAddresses'',COUNT_BIG(*),SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END),SUM(CASE WHEN IsDeleted=1 THEN 1 ELSE 0 END) FROM dbo.OrganizationEmailAddresses
UNION ALL SELECT N''OrganizationAddresses'',COUNT_BIG(*),SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END),SUM(CASE WHEN IsDeleted=1 THEN 1 ELSE 0 END) FROM dbo.OrganizationAddresses
UNION ALL SELECT N''OrganizationWebsites'',COUNT_BIG(*),SUM(CASE WHEN IsDeleted=0 THEN 1 ELSE 0 END),SUM(CASE WHEN IsDeleted=1 THEN 1 ELSE 0 END) FROM dbo.OrganizationWebsites;
SELECT N''OrganizationPhoneNumbers'' TableName,ShaleClientId,COUNT_BIG(*) StructuredCount FROM dbo.OrganizationPhoneNumbers GROUP BY ShaleClientId
UNION ALL SELECT N''OrganizationEmailAddresses'',ShaleClientId,COUNT_BIG(*) FROM dbo.OrganizationEmailAddresses GROUP BY ShaleClientId
UNION ALL SELECT N''OrganizationAddresses'',ShaleClientId,COUNT_BIG(*) FROM dbo.OrganizationAddresses GROUP BY ShaleClientId
UNION ALL SELECT N''OrganizationWebsites'',ShaleClientId,COUNT_BIG(*) FROM dbo.OrganizationWebsites GROUP BY ShaleClientId ORDER BY TableName,ShaleClientId;';

DECLARE @Findings table(Finding nvarchar(160) NOT NULL,FindingCount bigint NOT NULL);
INSERT @Findings EXEC sys.sp_executesql N'
SELECT N''legacy Phone without historical structured voice match'',COUNT_BIG(*) FROM dbo.Organizations o WHERE NULLIF(LTRIM(RTRIM(o.Phone)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers p WHERE p.ShaleClientId=o.ShaleClientId AND p.OrganizationId=o.Id AND p.Kind IN(N''MOBILE'',N''HOME'',N''WORK'',N''OTHER'') AND p.DisplayNumber=LTRIM(RTRIM(o.Phone)))
UNION ALL SELECT N''legacy Fax without historical fax match'',COUNT_BIG(*) FROM dbo.Organizations o WHERE NULLIF(LTRIM(RTRIM(o.Fax)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationPhoneNumbers p WHERE p.ShaleClientId=o.ShaleClientId AND p.OrganizationId=o.Id AND p.Kind=N''FAX'' AND p.DisplayNumber=LTRIM(RTRIM(o.Fax)))
UNION ALL SELECT N''legacy Email without historical structured match'',COUNT_BIG(*) FROM dbo.Organizations o WHERE NULLIF(LTRIM(RTRIM(o.Email)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationEmailAddresses e WHERE e.ShaleClientId=o.ShaleClientId AND e.OrganizationId=o.Id AND e.EmailAddress=LTRIM(RTRIM(o.Email)))
UNION ALL SELECT N''populated partial address without historical structured match'',COUNT_BIG(*) FROM dbo.Organizations o WHERE COALESCE(NULLIF(LTRIM(RTRIM(o.Address1)),N''''),NULLIF(LTRIM(RTRIM(o.Address2)),N''''),NULLIF(LTRIM(RTRIM(o.City)),N''''),NULLIF(LTRIM(RTRIM(o.State)),N''''),NULLIF(LTRIM(RTRIM(o.PostalCode)),N''''),NULLIF(LTRIM(RTRIM(o.Country)),N'''')) IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationAddresses a WHERE a.ShaleClientId=o.ShaleClientId AND a.OrganizationId=o.Id AND ISNULL(a.AddressLine1,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Address1)),N''''),N'''') AND ISNULL(a.AddressLine2,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Address2)),N''''),N'''') AND ISNULL(a.City,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.City)),N''''),N'''') AND ISNULL(a.StateOrProvince,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.State)),N''''),N'''') AND ISNULL(a.PostalCode,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.PostalCode)),N''''),N'''') AND ISNULL(a.Country,N'''')=ISNULL(NULLIF(LTRIM(RTRIM(o.Country)),N''''),N''''))
UNION ALL SELECT N''legacy Website without historical structured match'',COUNT_BIG(*) FROM dbo.Organizations o WHERE NULLIF(LTRIM(RTRIM(o.Website)),N'''') IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dbo.OrganizationWebsites w WHERE w.ShaleClientId=o.ShaleClientId AND w.OrganizationId=o.Id AND w.Website=LTRIM(RTRIM(o.Website)))
UNION ALL SELECT N''phone active duplicates'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,Kind,DisplayNumber HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''email active duplicates'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationEmailAddresses WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,EmailAddress HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''website active duplicates'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationWebsites WHERE IsDeleted=0 GROUP BY ShaleClientId,OrganizationId,Website HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''more than one active phone primary'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationPhoneNumbers WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''more than one active email primary'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationEmailAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''more than one active address primary'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationAddresses WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''more than one active website primary'',COUNT_BIG(*) FROM(SELECT 1 x FROM dbo.OrganizationWebsites WHERE IsDeleted=0 AND IsPrimary=1 GROUP BY ShaleClientId,OrganizationId HAVING COUNT_BIG(*)>1)d
UNION ALL SELECT N''cross-tenant or orphan phone rows'',COUNT_BIG(*) FROM dbo.OrganizationPhoneNumbers p LEFT JOIN dbo.Organizations o ON o.Id=p.OrganizationId WHERE o.Id IS NULL OR o.ShaleClientId<>p.ShaleClientId
UNION ALL SELECT N''cross-tenant or orphan email rows'',COUNT_BIG(*) FROM dbo.OrganizationEmailAddresses e LEFT JOIN dbo.Organizations o ON o.Id=e.OrganizationId WHERE o.Id IS NULL OR o.ShaleClientId<>e.ShaleClientId
UNION ALL SELECT N''cross-tenant or orphan address rows'',COUNT_BIG(*) FROM dbo.OrganizationAddresses a LEFT JOIN dbo.Organizations o ON o.Id=a.OrganizationId WHERE o.Id IS NULL OR o.ShaleClientId<>a.ShaleClientId
UNION ALL SELECT N''cross-tenant or orphan website rows'',COUNT_BIG(*) FROM dbo.OrganizationWebsites w LEFT JOIN dbo.Organizations o ON o.Id=w.OrganizationId WHERE o.Id IS NULL OR o.ShaleClientId<>w.ShaleClientId
UNION ALL SELECT N''invalid sort orders'',COUNT_BIG(*) FROM(SELECT SortOrder FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT SortOrder FROM dbo.OrganizationEmailAddresses UNION ALL SELECT SortOrder FROM dbo.OrganizationAddresses UNION ALL SELECT SortOrder FROM dbo.OrganizationWebsites)x WHERE SortOrder<0
UNION ALL SELECT N''blank active scalar values'',COUNT_BIG(*) FROM(SELECT DisplayNumber Value,IsDeleted FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT EmailAddress,IsDeleted FROM dbo.OrganizationEmailAddresses UNION ALL SELECT Website,IsDeleted FROM dbo.OrganizationWebsites)x WHERE IsDeleted=0 AND NULLIF(LTRIM(RTRIM(Value)),N'''') IS NULL
UNION ALL SELECT N''empty active addresses'',COUNT_BIG(*) FROM dbo.OrganizationAddresses WHERE IsDeleted=0 AND COALESCE(NULLIF(LTRIM(RTRIM(AddressLine1)),N''''),NULLIF(LTRIM(RTRIM(AddressLine2)),N''''),NULLIF(LTRIM(RTRIM(City)),N''''),NULLIF(LTRIM(RTRIM(StateOrProvince)),N''''),NULLIF(LTRIM(RTRIM(PostalCode)),N''''),NULLIF(LTRIM(RTRIM(Country)),N''''),NULLIF(LTRIM(RTRIM(LegacyAddressText)),N'''')) IS NULL
UNION ALL SELECT N''deleted rows still primary'',COUNT_BIG(*) FROM(SELECT IsDeleted,IsPrimary FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationEmailAddresses UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationAddresses UNION ALL SELECT IsDeleted,IsPrimary FROM dbo.OrganizationWebsites)x WHERE IsDeleted=1 AND IsPrimary=1;';
INSERT @Findings
SELECT N'missing/wrong/unexpected RLS predicates',COUNT_BIG(*) FROM @Tables e OUTER APPLY(SELECT COUNT(*) n,SUM(CASE WHEN LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(spr.predicate_definition,N'[',N''),N']',N''),N' ',N''),NCHAR(9),N''),NCHAR(10),N''),NCHAR(13),N''),N'(',N''),N')',N''))=N'sec.fn_filterbytenantshaleclientid' AND spr.predicate_type_desc=N'FILTER' AND spr.operation IS NULL AND spr.operation_desc IS NULL AND sp.is_enabled=1 THEN 0 ELSE 1 END) unexpected FROM sys.security_predicates spr JOIN sys.security_policies sp ON spr.object_id=sp.object_id WHERE spr.target_object_id=OBJECT_ID(N'dbo.'+e.TableName))x WHERE x.n<>1 OR x.unexpected<>0;
INSERT @Findings SELECT N'untrusted, disabled, or cascading foreign keys',COUNT_BIG(*) FROM sys.foreign_keys f JOIN @Tables e ON e.TableName=OBJECT_NAME(f.parent_object_id) WHERE f.is_disabled=1 OR f.is_not_trusted=1 OR f.delete_referential_action<>0;
SELECT Finding,FindingCount FROM @Findings ORDER BY Finding;

SELECT N'phone legacy populated' Metric,COUNT_BIG(*) MetricCount FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Phone)),N'') IS NOT NULL
UNION ALL SELECT N'fax legacy populated',COUNT_BIG(*) FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Fax)),N'') IS NOT NULL
UNION ALL SELECT N'email legacy populated',COUNT_BIG(*) FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Email)),N'') IS NOT NULL
UNION ALL SELECT N'address legacy populated',COUNT_BIG(*) FROM dbo.Organizations WHERE COALESCE(NULLIF(LTRIM(RTRIM(Address1)),N''),NULLIF(LTRIM(RTRIM(Address2)),N''),NULLIF(LTRIM(RTRIM(City)),N''),NULLIF(LTRIM(RTRIM(State)),N''),NULLIF(LTRIM(RTRIM(PostalCode)),N''),NULLIF(LTRIM(RTRIM(Country)),N'')) IS NOT NULL
UNION ALL SELECT N'website legacy populated',COUNT_BIG(*) FROM dbo.Organizations WHERE NULLIF(LTRIM(RTRIM(Website)),N'') IS NOT NULL;

/* Isolation evidence when impersonation/session-context permissions allow: predicates must be strict,
   and these metadata counts show whether tenant 7/8 test rows exist. Execute tenant visibility probes
   in separate ordinary runtime sessions; this administrative script never changes SESSION_CONTEXT. */
EXEC sys.sp_executesql N'SELECT ShaleClientId,COUNT_BIG(*) StructuredCount FROM(SELECT ShaleClientId FROM dbo.OrganizationPhoneNumbers UNION ALL SELECT ShaleClientId FROM dbo.OrganizationEmailAddresses UNION ALL SELECT ShaleClientId FROM dbo.OrganizationAddresses UNION ALL SELECT ShaleClientId FROM dbo.OrganizationWebsites)x WHERE ShaleClientId IN(7,8) GROUP BY ShaleClientId ORDER BY ShaleClientId;';
SELECT N'Tenant 7/8 isolation requires separate normal-session probes when permission allows; missing context must return zero rows.' IsolationInstruction;
IF EXISTS(SELECT 1 FROM @Findings WHERE FindingCount<>0) THROW 57399,'Organizations Phase 3A verification failed; inspect the zero-healthy findings.',1;
END TRY BEGIN CATCH THROW; END CATCH;
