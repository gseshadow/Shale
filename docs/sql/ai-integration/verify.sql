/* Read-only catalog/state inspection. No authorization to connect to production.
   Use reviewed owner/setup authority; runtime enforcement requires the separate synthetic test. */
SET NOCOUNT ON;
DECLARE @TenantTables table(Name sysname PRIMARY KEY);
INSERT @TenantTables VALUES(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),
 (N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),
 (N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiAssertionReplay');
SELECT t.Name,
 SUM(CASE WHEN p.predicate_type_desc=N'FILTER' AND p.predicate_definition LIKE N'%fn_FilterByTenant%ShaleClientId%' THEN 1 ELSE 0 END) FilterCount,
 SUM(CASE WHEN p.predicate_type_desc=N'BLOCK' AND p.operation_desc=N'AFTER INSERT' THEN 1 ELSE 0 END) InsertBlockCount,
 SUM(CASE WHEN p.predicate_type_desc=N'BLOCK' AND p.operation_desc=N'AFTER UPDATE' THEN 1 ELSE 0 END) UpdateBlockCount,
 SUM(CASE WHEN sp.is_enabled=0 THEN 1 ELSE 0 END) DisabledFindingCount
FROM @TenantTables t LEFT JOIN sys.security_predicates p ON p.target_object_id=OBJECT_ID(N'dbo.'+t.Name)
LEFT JOIN sys.security_policies sp ON sp.object_id=p.object_id GROUP BY t.Name ORDER BY t.Name;
SELECT COUNT(*) UntrustedConstraintFindingCount FROM sys.foreign_keys
WHERE OBJECT_NAME(parent_object_id) IN(SELECT Name FROM @TenantTables) AND (is_disabled=1 OR is_not_trusted=1);
SELECT Enabled GlobalEnabled,Revision FROM dbo.AiProductCapability WHERE Id=1;
SELECT COUNT(*) EnabledTenantPolicyCount FROM dbo.TenantAiIntegrationPolicy WHERE Enabled=1;
SELECT COUNT(*) RegistrationCount FROM dbo.AiIntegrationRegistration;
SELECT name ReceiptName,CONVERT(varchar(64),CONVERT(varbinary(32),value),2) ReceiptHash
FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiProductCapability')
AND name IN(N'Shale.AiFoundationV1',N'Shale.AiFoundationV1.Source');
/* Expected fresh state: all eleven rows have 1/1/1 predicates and zero disabled findings;
   zero untrusted findings, GlobalEnabled=0, EnabledTenantPolicyCount=0, RegistrationCount=0.
   Catalog presence is static evidence, never proof of ordinary non-bypass enforcement. */

/* Cumulative lifecycle receipt: read-only, fail-fast drift check. */
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiProductCapability'),(N'TenantAiIntegrationPolicy'),(N'AiIntegrationRegistration'),(N'AiIntegrationRedirect'),(N'AiIntegrationClientKey'),(N'AiDelegationGrant'),(N'AiConsentIntent'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiQuotaReservation'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1'),(N'AiSetProductCapabilityV1'),(N'AiGetProductCapabilityV1'),(N'AiGetTenantPolicyV1'),(N'AiInspectKeysV1'),(N'AiSetTenantPolicyV1'),(N'AiRegisterIntegrationV1'),(N'AiChangeIntegrationV1'),(N'AiRotateClientKeyV1'),(N'AiRevokeClientKeyV1'),(N'AiBeginConsentV1'),(N'AiAcceptConsentV1'),(N'AiRevokeGrantV1'),(N'AiRevokeSessionV1'),(N'AiInspectRegistrationsV1'),(N'AiVerifySessionV1'),(N'AiReserveQuotaV1'),(N'AiReleaseQuotaV1'),(N'ResolveAiClientV1'),(N'ResolveAiCredentialV1'),(N'AiImmutable_TenantAiIntegrationPolicy'),(N'AiImmutable_AiIntegrationRegistration'),(N'AiImmutable_AiIntegrationRedirect'),(N'AiImmutable_AiIntegrationClientKey'),(N'AiImmutable_AiDelegationGrant'),(N'AiImmutable_AiConsentIntent'),(N'AiImmutable_AiDelegatedSession'),(N'AiImmutable_AiOAuthCredential'),(N'AiImmutable_AiQuotaReservation'),(N'AiAuthorizationTransaction'),(N'AiImmutable_AiAuthorizationTransaction'),(N'AiLoadOAuthClientV1'),(N'AiBeginOAuthV1'),(N'AiFindOAuthIntentV1'),(N'AiApproveOAuthV1'),(N'AiCompleteOAuthV1'),(N'AiDenyOAuthV1'),(N'AiConsumeOAuthCodeV1'),(N'AiAssertionReplay'),(N'ResolveAiOAuthCodeV1'),(N'AiExchangeOAuthCodeV1'),(N'ResolveAiRefreshV1'),(N'AiIssueCredentialsV1'),(N'AiRefreshCredentialsV1'),(N'AiLifecycle_Session'),(N'AiLifecycle_Credential'),(N'AiCascade_Grant'),(N'AiCascade_Key'),(N'AiCascade_Registration'),(N'AiCascade_Web'),(N'AiCascade_User'),(N'AiCascade_Tenant');
DECLARE @catalog nvarchar(max)=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');
DECLARE @fingerprint varbinary(32)=HASHBYTES('SHA2_256',@catalog);



IF @fingerprint<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1'),0x)
 THROW 58700,'Cumulative AI credential catalog drift or migration missing.',1;
SELECT CAST(0 AS int) AS CredentialCatalogDriftFindingCount;

IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiCredentialsV1.Source'),0x)<>0x4463497b7476f047263a3e6dfb3abe28c9c251633ecec19a87eb2bcf255f3ddc
 THROW 58700,'Reviewed AI credential migration source receipt missing or changed.',1;
SELECT CAST(0 AS int) AS CredentialSourceReceiptFindingCount;

/* Optional RFC 7009 extension: verify its cumulative receipt when installed. */
IF OBJECT_ID(N'dbo.AiRevokeOAuthCredentialV1') IS NOT NULL BEGIN
 INSERT @Owned VALUES (N'ResolveAiRevocationV1'),(N'AiLoadRevocationClientV1'),(N'AiRevokeOAuthCredentialV1');
 SET @catalog=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');

 IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1'),0x)
 THROW 58800,'Cumulative AI revocation catalog drift or receipt missing.',1;
 IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiRevocationV1.Source'),0x)<>0x153fce8576ba106e182d2006b694a001b2cfd3ef017beccb725f83185d660821
 THROW 58800,'Reviewed AI revocation source receipt missing or changed.',1;
 SELECT CAST(0 AS int) AS RevocationCatalogDriftFindingCount;
END;

/* Optional internal ACCESS extension: no data surface. */
IF OBJECT_ID(N'dbo.AiValidateAccessV1') IS NOT NULL OR OBJECT_ID(N'dbo.ResolveAiAccessV1') IS NOT NULL
 OR EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1') BEGIN
INSERT @Owned VALUES (N'ResolveAiAccessV1'),(N'AiValidateAccessV1');
SET @catalog=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiIntegrationRuntime',N'AiIntegrationBootstrap',N'AiCapabilityOperator') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');

 IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1'),0x)
 THROW 58900,'Cumulative ACCESS catalog drift or receipt missing.',1;
 IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiOAuthCredential') AND minor_id=0 AND name=N'Shale.AiAccessV1.Source'),0x)<>0x85d215258b9e296d42a6544bcba606bb7455bd8827c876eba79b95159af129cd
 THROW 58900,'Reviewed ACCESS source receipt missing or changed.',1;
 SELECT CAST(0 AS int) AS AccessCatalogDriftFindingCount;
END;
