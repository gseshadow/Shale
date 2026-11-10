/* Generated read-only combined verification. No fallback and no receipt writes. */
SET NOCOUNT ON;
IF NOT EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 JOIN sys.extended_properties p ON p.class=1 AND p.major_id=o.object_id AND p.minor_id=0 AND p.name=N'Shale.RegistryConfinementV1.Source'
 WHERE s.name=N'dbo' AND o.name=N'AiRegistryConfinementV1' AND o.type='P' AND COALESCE(o.principal_id,s.principal_id)=1 AND m.execute_as_principal_id IS NULL AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1
 AND HASHBYTES('SHA2_256',m.definition)=0x3636deda3744ada73314b233532834bd13d02a102132f16ea4cf00ab91cde5c1 AND CONVERT(varbinary(32),p.value)=0x3636deda3744ada73314b233532834bd13d02a102132f16ea4cf00ab91cde5c1
 AND EXISTS(SELECT 1 FROM sys.extended_properties r WHERE r.class=1 AND r.major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND r.name=N'Shale.RegistryConfinementV1.Required' AND CONVERT(varbinary(32),r.value)=0x3636deda3744ada73314b233532834bd13d02a102132f16ea4cf00ab91cde5c1))
 THROW 59310,'Reviewed registry confinement prerequisite missing or changed.',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND NOT(grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND minor_id=0 AND permission_name='EXECUTE' AND state='D' AND grantor_principal_id=1))
 OR NOT EXISTS(SELECT 1 FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND permission_name='EXECUTE' AND state='D')
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1'))
 THROW 59310,'Private compatibility module permission/signature drift.',1;
IF (SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name IN(N'Shale.AiCaseDataV1.Source',N'Shale.AiCaseDataV2.Source'))<>1
 THROW 59310,'Exactly one installed confinement version required.',1;
IF EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseDataV1.Source' AND CONVERT(varbinary(32),value)=0xb949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3)
 EXEC(N'SET NOCOUNT ON;
DECLARE @caseObjects table(Name sysname PRIMARY KEY);
INSERT @caseObjects VALUES(N''Cases''),(N''Users''),(N''CaseUsers''),(N''CaseStatuses''),(N''Statuses''),(N''PracticeAreas''),(N''auditLog''),(N''UserSessions''),(N''ShaleClients'');
DECLARE @caseCatalog nvarchar(max)=N'''';
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N'':'',o.name,N'':'',o.type,N'':'',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=N''dbo'' AND o.name IN(SELECT Name FROM @caseObjects) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N'':'',c.name,N'':'',c.column_id,N'':'',TYPE_NAME(c.user_type_id),N'':'',c.max_length,N'':'',c.precision,N'':'',c.scale,N'':'',c.is_nullable,N'':'',c.is_identity,N'':'',c.collation_name,N'':'',cc.definition,N'';'') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(c.object_id),c.column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N'':'',o.name,N'':'',o.type,N'':'',ch.definition,N'':'',ch.is_disabled,N'':'',ch.is_not_trusted,N'':'',dc.definition,N'':'',OBJECT_SCHEMA_NAME(fk.referenced_object_id),N''.'',OBJECT_NAME(fk.referenced_object_id),N'':'',fk.delete_referential_action,N'':'',fk.update_referential_action,N'':'',fk.is_disabled,N'':'',fk.is_not_trusted,N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @caseObjects) AND o.type IN(''C'',''D'',''F'',''PK'',''UQ'') ORDER BY OBJECT_NAME(o.parent_object_id),o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(fkc.parent_object_id),N'':'',OBJECT_NAME(fkc.constraint_object_id),N'':'',fkc.constraint_column_id,N'':'',COL_NAME(fkc.parent_object_id,fkc.parent_column_id),N'':'',COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id),N'';'') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(fkc.parent_object_id),OBJECT_NAME(fkc.constraint_object_id),fkc.constraint_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N'':'',i.name,N'':'',i.type,N'':'',i.is_unique,N'':'',i.is_disabled,N'':'',i.filter_definition,N'':'',COL_NAME(ic.object_id,ic.column_id),N'':'',ic.key_ordinal,N'':'',ic.is_included_column,N'':'',ic.is_descending_key,N'';'') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(i.object_id),i.name,ic.index_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(p.target_object_id),N'':'',p.predicate_definition,N'':'',p.predicate_type,N'':'',p.operation,N'':'',sp.is_enabled,N'':'',sp.is_schema_bound,N'';'') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(p.target_object_id),p.predicate_type,p.operation FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(t.parent_id),N'':'',t.name,N'':'',t.is_disabled,N'':'',m.definition,N'';'') AS [text()] FROM sys.triggers t JOIN sys.sql_modules m ON m.object_id=t.object_id WHERE OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(t.parent_id),t.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N'':'',o.name,N'':'',m.definition,N'':'',m.execute_as_principal_id,N'':'',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE s.name=''sec'' AND o.type IN(''FN'',''IF'',''TF'') OR s.name=''dbo'' AND o.name=''ToBitNV'' ORDER BY s.name,o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
IF HASHBYTES(''SHA2_256'',@caseCatalog)<>0x2fd4544bc7454ff67e8411449f983c29a0d2cf13f92982ebccc8d67bbbf97c1f THROW 59100,''Authoritative Case/RLS catalog prerequisite drift.'',1;
SET NOCOUNT ON;
/* Read-only live confinement catalog attestation. Requires complete VIEW DEFINITION. */
IF HAS_PERMS_BY_NAME(DB_NAME(),''DATABASE'',''VIEW DEFINITION'')<>1 THROW 59100,''Complete catalog visibility required.'',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N''AiProductCapability''),(N''TenantAiIntegrationPolicy''),(N''AiIntegrationRegistration''),(N''AiIntegrationRedirect''),(N''AiIntegrationClientKey''),(N''AiDelegationGrant''),(N''AiConsentIntent''),(N''AiDelegatedSession''),(N''AiOAuthCredential''),(N''AiQuotaReservation''),(N''AiLockV1''),(N''AiActorV1''),(N''AiEnabledV1''),(N''AiSetProductCapabilityV1''),(N''AiGetProductCapabilityV1''),(N''AiGetTenantPolicyV1''),(N''AiInspectKeysV1''),(N''AiSetTenantPolicyV1''),(N''AiRegisterIntegrationV1''),(N''AiChangeIntegrationV1''),(N''AiRotateClientKeyV1''),(N''AiRevokeClientKeyV1''),(N''AiBeginConsentV1''),(N''AiAcceptConsentV1''),(N''AiRevokeGrantV1''),(N''AiRevokeSessionV1''),(N''AiInspectRegistrationsV1''),(N''AiVerifySessionV1''),(N''AiReserveQuotaV1''),(N''AiReleaseQuotaV1''),(N''ResolveAiClientV1''),(N''ResolveAiCredentialV1''),(N''AiImmutable_TenantAiIntegrationPolicy''),(N''AiImmutable_AiIntegrationRegistration''),(N''AiImmutable_AiIntegrationRedirect''),(N''AiImmutable_AiIntegrationClientKey''),(N''AiImmutable_AiDelegationGrant''),(N''AiImmutable_AiConsentIntent''),(N''AiImmutable_AiDelegatedSession''),(N''AiImmutable_AiOAuthCredential''),(N''AiImmutable_AiQuotaReservation''),(N''AiAuthorizationTransaction''),(N''AiImmutable_AiAuthorizationTransaction''),(N''AiLoadOAuthClientV1''),(N''AiBeginOAuthV1''),(N''AiFindOAuthIntentV1''),(N''AiApproveOAuthV1''),(N''AiCompleteOAuthV1''),(N''AiDenyOAuthV1''),(N''AiConsumeOAuthCodeV1''),(N''AiAssertionReplay''),(N''ResolveAiOAuthCodeV1''),(N''AiExchangeOAuthCodeV1''),(N''ResolveAiRefreshV1''),(N''AiIssueCredentialsV1''),(N''AiRefreshCredentialsV1''),(N''AiLifecycle_Session''),(N''AiLifecycle_Credential''),(N''AiCascade_Grant''),(N''AiCascade_Key''),(N''AiCascade_Registration''),(N''AiCascade_Web''),(N''AiCascade_User''),(N''AiCascade_Tenant''); INSERT @Owned VALUES (N''ResolveAiRevocationV1''),(N''AiLoadRevocationClientV1''),(N''AiRevokeOAuthCredentialV1'');
INSERT @Owned VALUES (N''ResolveAiAccessV1''),(N''AiValidateAccessV1'');
INSERT @Owned VALUES (N''IntegrationAccessAuditLog''),(N''IntegrationAccessAuditResource''),(N''AiAppendCaseAccessV1''),(N''AiReviewCaseAccessV1''),(N''AiGateCaseReadV1''),(N''AiFinishCaseQuotaV1''),(N''AiAppendOnly_Access''),(N''AiAppendOnly_Resource''),(N''AiOverviewPhiCountV1''),(N''AiAccountCaseAttemptV1'');
INSERT @Owned VALUES (N''AiDataLocateAccessOwnerV1''),(N''AiDataLoadAuthorityV1''),(N''AiDataAuthenticateV1''),(N''AiDataAppendAccessV1''),(N''AiDataAdmitV1''),(N''AiDataSearchCasesV1''),(N''AiDataGetCaseOverviewV1''),(N''AiDataFinishCaseQuotaV1''),(N''AiDataAuthorizeCaseHandoffV1''),(N''AiDataRecordCaseFailureV1''),(N''AiCaseDataInvocation''),(N''AiCaseDataDeployment''),(N''AiDataUnicodeRange''),(N''AiDataUnicodeLower''),(N''AiDataWordTransition''),(N''AiDataNormalizeV1''),(N''AiDataImmutableInvocation''),(N''AiDataImmutableDeployment'');
DECLARE @catalog nvarchar(max)=N'''';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N'':'',o.type,N'':'',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''''),N'':'',COALESCE(m.definition,N''''),N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N''dbo'') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N'':'',c.name,N'':'',c.column_id,N'':'',c.system_type_id,N'':'',c.max_length,N'':'',c.precision,N'':'',c.scale,N'':'',c.is_nullable,N'':'',c.is_identity,N'':'',c.collation_name,N'':'',cc.definition,N'';'') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N'':'',o.name,N'':'',o.type,N'':'',ch.definition,N'':'',ch.is_disabled,N'':'',ch.is_not_trusted,N'':'',dc.definition,N'':'',fk.referenced_object_id,N'':'',fk.is_disabled,N'':'',fk.is_not_trusted,N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N'':'',fkc.parent_column_id,N'':'',fkc.referenced_column_id,N'';'') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N'':'',i.name,N'':'',i.is_unique,N'':'',i.is_disabled,N'':'',i.filter_definition,N'':'',ic.column_id,N'':'',ic.key_ordinal,N'':'',ic.is_included_column,N'':'',ic.is_descending_key,N'';'') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N'':'',p.predicate_definition,N'':'',p.predicate_type,N'':'',p.operation,N'':'',sp.is_enabled,N'';'') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N'':'',p.grantee_principal_id,N'':'',p.permission_name,N'':'',p.state,N'';'') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N'':'',t.is_disabled,N'';'') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id,N'';'') AS [text()] FROM sys.database_principals WHERE name IN(N''AiIntegrationRuntime'',N''AiIntegrationBootstrap'',N''AiCapabilityOperator'') ORDER BY name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)''),N'''');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N''sec.fn_FilterByTenant'')),N''MISSING'');

SET @catalog=@catalog+(SELECT CONCAT(p.class,N'':'',p.major_id,N'':'',p.minor_id,N'':'',p.grantee_principal_id,N'':'',p.grantor_principal_id,N'':'',p.permission_name,N'':'',p.state,N'';'') AS [text()] FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiCaseDataRuntime''),DATABASE_PRINCIPAL_ID(N''AiCaseDataCatalogVerifier'')) OR p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(o.name,N'':'',m.execute_as_principal_id,N'':'',s.principal_id,N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(referencing_id),N'':'',referenced_server_name,N'':'',referenced_database_name,N'':'',referenced_schema_name,N'':'',referenced_entity_name,N'';'') AS [text()] FROM sys.sql_expression_dependencies WHERE OBJECT_NAME(referencing_id) IN(SELECT Name FROM @Owned) ORDER BY referencing_id,referenced_entity_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id) FROM sys.database_principals WHERE name=N''AiCaseDataRuntime''),N''MISSING'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id) FROM sys.database_principals WHERE name=N''AiCaseDataCatalogVerifier''),N''MISSING'');
SET @catalog=@catalog+(SELECT CONCAT(is_trustworthy_on,N'':'',is_db_chaining_on) FROM sys.databases WHERE database_id=DB_ID());
SET @catalog=@catalog+(SELECT CONCAT(StartCodepoint,N'':'',EndCodepoint,N'':'',Category,N'':'',Whitespace,N'':'',LetterDigit,N'':'',Cased,N'';'') AS [text()] FROM dbo.AiDataUnicodeRange ORDER BY StartCodepoint FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(Codepoint,N'':'',CONVERT(varchar(16),CONVERT(varbinary(8),LowerText),2),N'';'') AS [text()] FROM dbo.AiDataUnicodeLower ORDER BY Codepoint FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(StateId,N'':'',Category,N'':'',NextState,N'':'',IsEnd,N'':'',Lookahead,N'';'') AS [text()] FROM dbo.AiDataWordTransition ORDER BY StateId,Category FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');

IF HASHBYTES(''SHA2_256'',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N''dbo.AiCaseDataInvocation'') AND minor_id=0 AND name=N''Shale.AiCaseDataV1''),0x)
 THROW 59100,''Confinement catalog drift or missing receipt.'',1;
SELECT CAST(0 AS int) ConfinementCatalogDriftFindingCount;

IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N''dbo.AiCaseDataInvocation'') AND name=N''Shale.AiCaseDataV1.Source''),0x)<>0xb949079a9d1e639db206f535502441b59aada5cc6af74da5fda1549f566db7a3 THROW 59100,''Reviewed confinement source receipt missing or changed.'',1;
');
ELSE IF EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseDataV2.Source' AND CONVERT(varbinary(32),value)=0x60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891)
 EXEC(N'SET NOCOUNT ON;
SET NOCOUNT ON;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),''DATABASE'',''VIEW DEFINITION''),0)<>1
 THROW 59200,''Complete V2 catalog visibility required.'',1;
IF (SELECT COUNT(*) FROM sys.schemas WHERE name COLLATE Latin1_General_100_BIN2 IN(N''dbo'',N''sec'') AND principal_id=DATABASE_PRINCIPAL_ID(N''dbo''))<>2
 THROW 59200,''V2 dbo/sec schema ownership required.'',1;
DECLARE @ReviewedCaseModules table(SchemaName sysname,ModuleName sysname,DefinitionHash binary(32),CanonicalHash varchar(64),PRIMARY KEY(SchemaName,ModuleName,DefinitionHash));
INSERT @ReviewedCaseModules VALUES
(N''dbo'',N''AiCascade_Tenant'',0x013f90b908c49e35d9f7510abbde4cb3c88e9f9d988df214894ab5f5307a55b4,''013f90b908c49e35d9f7510abbde4cb3c88e9f9d988df214894ab5f5307a55b4''),
(N''dbo'',N''AiCascade_Tenant'',0x8a5471c35ac4c51d258d4403e6ed9fba20792d025636e4eebf69e36cdd525d51,''013f90b908c49e35d9f7510abbde4cb3c88e9f9d988df214894ab5f5307a55b4''),
(N''dbo'',N''AiCascade_User'',0x8fb242847e2970c3040aa844b1c3126f276a23709cda6ecd99aaee16bbd90f0e,''8fb242847e2970c3040aa844b1c3126f276a23709cda6ecd99aaee16bbd90f0e''),
(N''dbo'',N''AiCascade_User'',0xc89d506ff1254529b31530e745c28ce5c249ed2e68f7e0b7ea77b458e3c2889c,''8fb242847e2970c3040aa844b1c3126f276a23709cda6ecd99aaee16bbd90f0e''),
(N''dbo'',N''AiCascade_Web'',0x1b5862631966933d9b5cda68b7705c035ddb5ff42953f128a5ee03c55e66f39f,''1b5862631966933d9b5cda68b7705c035ddb5ff42953f128a5ee03c55e66f39f''),
(N''dbo'',N''AiCascade_Web'',0xb2bafb8024687539b2c7b70f35b5e0175c6d5c3242de115da14f3123a40871f4,''1b5862631966933d9b5cda68b7705c035ddb5ff42953f128a5ee03c55e66f39f''),
(N''dbo'',N''ToBitNV'',0xd203661a9f42799d7e9f60cdbdad11c2f86b14aea4a82f44f64f55c89445deeb,''d203661a9f42799d7e9f60cdbdad11c2f86b14aea4a82f44f64f55c89445deeb''),
(N''dbo'',N''ToBitNV'',0xeff52f3dc23fba3d88d8d788fe06bfb88f61e3ebcbdc5c5bc3640f083d2c2ecc,''d203661a9f42799d7e9f60cdbdad11c2f86b14aea4a82f44f64f55c89445deeb''),
(N''sec'',N''fn_FilterByTenant'',0x787ada49e37e9af1e368d6f61b1a5393de3fc4ced2bc018e8f4c22afc9c07041,''dca29ac712cf8be8badba8d62a324857eb73931c6b98367c656c6fd094738502''),
(N''sec'',N''fn_FilterByTenant'',0xdca29ac712cf8be8badba8d62a324857eb73931c6b98367c656c6fd094738502,''dca29ac712cf8be8badba8d62a324857eb73931c6b98367c656c6fd094738502''),
(N''sec'',N''fn_FilterByTenantOrGlobal'',0x8a33cf8c28f999a1118d6b225e38930861f3774d55e568453716616f79fb0ac3,''8a33cf8c28f999a1118d6b225e38930861f3774d55e568453716616f79fb0ac3''),
(N''sec'',N''fn_FilterByTenantOrGlobal'',0xbe4978581488a070d4ad30fb6d424ffa32888face044483ba36cc461acfe944c,''8a33cf8c28f999a1118d6b225e38930861f3774d55e568453716616f79fb0ac3'');
IF (SELECT COUNT(*) FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name))<>6
 THROW 59200,''V2 reviewed Case modules missing.'',1;
IF EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name)
 AND NOT EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name AND r.DefinitionHash=HASHBYTES(''SHA2_256'',m.definition)))
 THROW 59200,''V2 Case module definition is not an approved representation.'',1;
DECLARE @caseObjects table(Name sysname PRIMARY KEY);
INSERT @caseObjects VALUES(N''Cases''),(N''Users''),(N''CaseUsers''),(N''CaseStatuses''),(N''Statuses''),(N''PracticeAreas''),(N''auditLog''),(N''UserSessions''),(N''ShaleClients'');
DECLARE @caseCatalog nvarchar(max)=N'''';
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N'':'',o.name,N'':'',o.type,N'':'',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=N''dbo'' AND o.name IN(SELECT Name FROM @caseObjects) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N'':'',c.name,N'':'',ROW_NUMBER() OVER(PARTITION BY c.object_id ORDER BY c.column_id),N'':'',TYPE_NAME(c.user_type_id),N'':'',c.max_length,N'':'',c.precision,N'':'',c.scale,N'':'',c.is_nullable,N'':'',c.is_identity,N'':'',c.collation_name,N'':'',cc.definition,N'';'') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(c.object_id),c.column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N'':'',o.name,N'':'',o.type,N'':'',ch.definition,N'':'',ch.is_disabled,N'':'',ch.is_not_trusted,N'':'',dc.definition,N'':'',OBJECT_SCHEMA_NAME(fk.referenced_object_id),N''.'',OBJECT_NAME(fk.referenced_object_id),N'':'',fk.delete_referential_action,N'':'',fk.update_referential_action,N'':'',fk.is_disabled,N'':'',fk.is_not_trusted,N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @caseObjects) AND o.type IN(''C'',''D'',''F'',''PK'',''UQ'') ORDER BY OBJECT_NAME(o.parent_object_id),o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(fkc.parent_object_id),N'':'',OBJECT_NAME(fkc.constraint_object_id),N'':'',fkc.constraint_column_id,N'':'',COL_NAME(fkc.parent_object_id,fkc.parent_column_id),N'':'',COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id),N'';'') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(fkc.parent_object_id),OBJECT_NAME(fkc.constraint_object_id),fkc.constraint_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N'':'',i.name,N'':'',i.type,N'':'',i.is_unique,N'':'',i.is_disabled,N'':'',i.filter_definition,N'':'',COL_NAME(ic.object_id,ic.column_id),N'':'',ic.key_ordinal,N'':'',ic.is_included_column,N'':'',ic.is_descending_key,N'';'') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(i.object_id),i.name,ic.index_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(p.target_object_id),N'':'',p.predicate_definition,N'':'',p.predicate_type,N'':'',p.operation,N'':'',OBJECT_SCHEMA_NAME(p.object_id),N'':'',sp.name,N'':'',USER_NAME(COALESCE(sp.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=sp.schema_id))),N'':'',sp.is_enabled,N'':'',sp.is_schema_bound,N'';'') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(p.target_object_id),p.predicate_type,p.operation FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_NAME(t.parent_id),N'':'',t.name,N'':'',t.is_disabled,N'':'',(SELECT TOP(1) r.CanonicalHash FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(t.object_id) AND r.ModuleName=t.name AND r.DefinitionHash=HASHBYTES(''SHA2_256'',m.definition)),N'';'') AS [text()] FROM sys.triggers t JOIN sys.sql_modules m ON m.object_id=t.object_id WHERE OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @caseObjects) ORDER BY OBJECT_NAME(t.parent_id),t.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N'':'',o.name,N'':'',(SELECT TOP(1) r.CanonicalHash FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name AND r.DefinitionHash=HASHBYTES(''SHA2_256'',m.definition)),N'':'',m.execute_as_principal_id,N'':'',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE s.name=''sec'' AND o.type IN(''FN'',''IF'',''TF'') OR s.name=''dbo'' AND o.name=''ToBitNV'' ORDER BY s.name,o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');

SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N'':'',o.name,N'':'',o.type,N'':'',m.uses_ansi_nulls,N'':'',m.uses_quoted_identifier,N'':'',m.is_schema_bound,N'':'',m.is_recompiled,N'':'',m.null_on_null_input,N'':'',m.uses_native_compilation,N'';'') AS [text()]
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name)
 ORDER BY s.name,o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_SCHEMA_NAME(p.object_id),N'':'',OBJECT_NAME(p.object_id),N'':'',p.parameter_id,N'':'',p.name,N'':'',(SELECT SCHEMA_NAME(ty.schema_id) FROM sys.types ty WHERE ty.user_type_id=p.user_type_id),N'':'',TYPE_NAME(p.user_type_id),N'':'',p.system_type_id,N'':'',p.max_length,N'':'',p.precision,N'':'',p.scale,N'':'',p.is_output,N'':'',p.is_readonly,N'';'') AS [text()]
 FROM sys.parameters p WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(p.object_id) AND r.ModuleName=OBJECT_NAME(p.object_id))
 ORDER BY OBJECT_SCHEMA_NAME(p.object_id),OBJECT_NAME(p.object_id),p.parameter_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_SCHEMA_NAME(d.referencing_id),N'':'',OBJECT_NAME(d.referencing_id),N'':'',d.referenced_server_name,N'':'',d.referenced_database_name,N'':'',d.referenced_schema_name,N'':'',d.referenced_entity_name,N'':'',COL_NAME(d.referenced_id,d.referenced_minor_id),N'':'',d.is_schema_bound_reference,N'':'',d.is_caller_dependent,N'':'',d.is_ambiguous,N'';'') AS [text()]
 FROM sys.sql_expression_dependencies d WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(d.referencing_id) AND r.ModuleName=OBJECT_NAME(d.referencing_id))
 ORDER BY OBJECT_SCHEMA_NAME(d.referencing_id),OBJECT_NAME(d.referencing_id),d.referenced_server_name,d.referenced_database_name,d.referenced_schema_name,d.referenced_entity_name,COL_NAME(d.referenced_id,d.referenced_minor_id),d.is_schema_bound_reference,d.is_caller_dependent,d.is_ambiguous
 FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
IF HASHBYTES(''SHA2_256'',@caseCatalog)<>0x6e9182b6e9b75200a16d51dbd537ca8926b358d603220d287a9710ad6ca8063a THROW 59200,''V2 authoritative Case/RLS prerequisite drift.'',1;
SET NOCOUNT ON;
/* Read-only live confinement catalog attestation. Requires complete VIEW DEFINITION. */
IF HAS_PERMS_BY_NAME(DB_NAME(),''DATABASE'',''VIEW DEFINITION'')<>1 THROW 59100,''Complete catalog visibility required.'',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N''AiProductCapability''),(N''TenantAiIntegrationPolicy''),(N''AiIntegrationRegistration''),(N''AiIntegrationRedirect''),(N''AiIntegrationClientKey''),(N''AiDelegationGrant''),(N''AiConsentIntent''),(N''AiDelegatedSession''),(N''AiOAuthCredential''),(N''AiQuotaReservation''),(N''AiLockV1''),(N''AiActorV1''),(N''AiEnabledV1''),(N''AiSetProductCapabilityV1''),(N''AiGetProductCapabilityV1''),(N''AiGetTenantPolicyV1''),(N''AiInspectKeysV1''),(N''AiSetTenantPolicyV1''),(N''AiRegisterIntegrationV1''),(N''AiChangeIntegrationV1''),(N''AiRotateClientKeyV1''),(N''AiRevokeClientKeyV1''),(N''AiBeginConsentV1''),(N''AiAcceptConsentV1''),(N''AiRevokeGrantV1''),(N''AiRevokeSessionV1''),(N''AiInspectRegistrationsV1''),(N''AiVerifySessionV1''),(N''AiReserveQuotaV1''),(N''AiReleaseQuotaV1''),(N''ResolveAiClientV1''),(N''ResolveAiCredentialV1''),(N''AiImmutable_TenantAiIntegrationPolicy''),(N''AiImmutable_AiIntegrationRegistration''),(N''AiImmutable_AiIntegrationRedirect''),(N''AiImmutable_AiIntegrationClientKey''),(N''AiImmutable_AiDelegationGrant''),(N''AiImmutable_AiConsentIntent''),(N''AiImmutable_AiDelegatedSession''),(N''AiImmutable_AiOAuthCredential''),(N''AiImmutable_AiQuotaReservation''),(N''AiAuthorizationTransaction''),(N''AiImmutable_AiAuthorizationTransaction''),(N''AiLoadOAuthClientV1''),(N''AiBeginOAuthV1''),(N''AiFindOAuthIntentV1''),(N''AiApproveOAuthV1''),(N''AiCompleteOAuthV1''),(N''AiDenyOAuthV1''),(N''AiConsumeOAuthCodeV1''),(N''AiAssertionReplay''),(N''ResolveAiOAuthCodeV1''),(N''AiExchangeOAuthCodeV1''),(N''ResolveAiRefreshV1''),(N''AiIssueCredentialsV1''),(N''AiRefreshCredentialsV1''),(N''AiLifecycle_Session''),(N''AiLifecycle_Credential''),(N''AiCascade_Grant''),(N''AiCascade_Key''),(N''AiCascade_Registration''),(N''AiCascade_Web''),(N''AiCascade_User''),(N''AiCascade_Tenant''); INSERT @Owned VALUES (N''ResolveAiRevocationV1''),(N''AiLoadRevocationClientV1''),(N''AiRevokeOAuthCredentialV1'');
INSERT @Owned VALUES (N''ResolveAiAccessV1''),(N''AiValidateAccessV1'');
INSERT @Owned VALUES (N''IntegrationAccessAuditLog''),(N''IntegrationAccessAuditResource''),(N''AiAppendCaseAccessV1''),(N''AiReviewCaseAccessV1''),(N''AiGateCaseReadV1''),(N''AiFinishCaseQuotaV1''),(N''AiAppendOnly_Access''),(N''AiAppendOnly_Resource''),(N''AiOverviewPhiCountV1''),(N''AiAccountCaseAttemptV1'');
INSERT @Owned VALUES (N''AiDataLocateAccessOwnerV1''),(N''AiDataLoadAuthorityV1''),(N''AiDataAuthenticateV1''),(N''AiDataAppendAccessV1''),(N''AiDataAdmitV1''),(N''AiDataSearchCasesV1''),(N''AiDataGetCaseOverviewV1''),(N''AiDataFinishCaseQuotaV1''),(N''AiDataAuthorizeCaseHandoffV1''),(N''AiDataRecordCaseFailureV1''),(N''AiCaseDataInvocation''),(N''AiCaseDataDeployment''),(N''AiDataUnicodeRange''),(N''AiDataUnicodeLower''),(N''AiDataWordTransition''),(N''AiDataNormalizeV1''),(N''AiDataImmutableInvocation''),(N''AiDataImmutableDeployment'');
DECLARE @catalog nvarchar(max)=N'''';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N'':'',o.type,N'':'',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''''),N'':'',COALESCE(m.definition,N''''),N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N''dbo'') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N'':'',c.name,N'':'',c.column_id,N'':'',c.system_type_id,N'':'',c.max_length,N'':'',c.precision,N'':'',c.scale,N'':'',c.is_nullable,N'':'',c.is_identity,N'':'',c.collation_name,N'':'',cc.definition,N'';'') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N'':'',o.name,N'':'',o.type,N'':'',ch.definition,N'':'',ch.is_disabled,N'':'',ch.is_not_trusted,N'':'',dc.definition,N'':'',fk.referenced_object_id,N'':'',fk.is_disabled,N'':'',fk.is_not_trusted,N'';'') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N'':'',fkc.parent_column_id,N'':'',fkc.referenced_column_id,N'';'') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N'':'',i.name,N'':'',i.is_unique,N'':'',i.is_disabled,N'':'',i.filter_definition,N'':'',ic.column_id,N'':'',ic.key_ordinal,N'':'',ic.is_included_column,N'':'',ic.is_descending_key,N'';'') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N'':'',p.predicate_definition,N'':'',p.predicate_type,N'':'',p.operation,N'':'',sp.is_enabled,N'';'') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N'':'',p.grantee_principal_id,N'':'',p.permission_name,N'':'',p.state,N'';'') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N'':'',t.is_disabled,N'';'') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id,N'';'') AS [text()] FROM sys.database_principals WHERE name IN(N''AiIntegrationRuntime'',N''AiIntegrationBootstrap'',N''AiCapabilityOperator'') ORDER BY name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)''),N'''');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N''sec.fn_FilterByTenant'')),N''MISSING'');

SET @catalog=@catalog+(SELECT CONCAT(p.class,N'':'',p.major_id,N'':'',p.minor_id,N'':'',p.grantee_principal_id,N'':'',p.grantor_principal_id,N'':'',p.permission_name,N'':'',p.state,N'';'') AS [text()] FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiCaseDataRuntime''),DATABASE_PRINCIPAL_ID(N''AiCaseDataCatalogVerifier'')) OR p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(o.name,N'':'',m.execute_as_principal_id,N'':'',s.principal_id,N'';'') AS [text()] FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(referencing_id),N'':'',referenced_server_name,N'':'',referenced_database_name,N'':'',referenced_schema_name,N'':'',referenced_entity_name,N'';'') AS [text()] FROM sys.sql_expression_dependencies WHERE OBJECT_NAME(referencing_id) IN(SELECT Name FROM @Owned) ORDER BY referencing_id,referenced_entity_name FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id) FROM sys.database_principals WHERE name=N''AiCaseDataRuntime''),N''MISSING'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N'':'',owning_principal_id) FROM sys.database_principals WHERE name=N''AiCaseDataCatalogVerifier''),N''MISSING'');
SET @catalog=@catalog+(SELECT CONCAT(is_trustworthy_on,N'':'',is_db_chaining_on) FROM sys.databases WHERE database_id=DB_ID());
SET @catalog=@catalog+(SELECT CONCAT(StartCodepoint,N'':'',EndCodepoint,N'':'',Category,N'':'',Whitespace,N'':'',LetterDigit,N'':'',Cased,N'';'') AS [text()] FROM dbo.AiDataUnicodeRange ORDER BY StartCodepoint FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(Codepoint,N'':'',CONVERT(varchar(16),CONVERT(varbinary(8),LowerText),2),N'';'') AS [text()] FROM dbo.AiDataUnicodeLower ORDER BY Codepoint FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');
SET @catalog=@catalog+(SELECT CONCAT(StateId,N'':'',Category,N'':'',NextState,N'':'',IsEnd,N'':'',Lookahead,N'';'') AS [text()] FROM dbo.AiDataWordTransition ORDER BY StateId,Category FOR XML PATH(''''),TYPE).value(N''.'',N''nvarchar(max)'');

IF HASHBYTES(''SHA2_256'',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N''dbo.AiCaseDataInvocation'') AND minor_id=0 AND name=N''Shale.AiCaseDataV2''),0x)
 THROW 59100,''Confinement catalog drift or missing receipt.'',1;
SELECT CAST(0 AS int) ConfinementCatalogDriftFindingCount;

IF ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N''dbo.AiCaseDataInvocation'') AND name=N''Shale.AiCaseDataV2.Source''),0x)<>0x60f7b39727a95ca65afd26ba3f8206a2700b64ca4df512da89df070a9f92e891 THROW 59100,''Reviewed confinement source receipt missing or changed.'',1;
');
ELSE THROW 59310,'Unknown confinement source; no fallback permitted.',1;
DECLARE @RegistryBefore bit=0,@RegistrySealing bit=0;
IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment)<>1 THROW 59310,'An existing immutable binding is required; never provision it here.',1;
DECLARE @RegistryLegacyState nvarchar(max)=(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue
 FROM sys.extended_properties WHERE name LIKE N'Shale.%' AND name NOT LIKE N'Shale.RegistryConfinementV1.%' AND name NOT LIKE N'Shale.UniversalMcpPR3%'
 ORDER BY class,major_id,minor_id,name FOR JSON PATH);
SET @RegistryLegacyState=@RegistryLegacyState+(SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment ORDER BY Id FOR JSON PATH);
DECLARE @RegistryLegacyHash binary(32)=HASHBYTES('SHA2_256',@RegistryLegacyState);
IF @RegistryLegacyHash<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND name=N'Shale.RegistryConfinementV1.Before'),0x)
 THROW 59310,'Historical receipt or immutable binding changed.',1;
DECLARE @RegistryDigest binary(32);
/* Read-only closed graph for the registry extension. Inputs are declared by the generated wrapper.
   No user/Case/credential data is returned. DDL belongs only to the separate dbo installer. */
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59310,'Complete registry catalog visibility required.',1;
IF (SELECT COUNT(*) FROM sys.schemas WHERE name IN(N'dbo',N'sec') AND principal_id=1)<>2
 OR EXISTS(SELECT 1 FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on=1 OR is_db_chaining_on=1))
 THROW 59310,'Registry schema ownership or database isolation drift.',1;
/* public is an implicit parent of every new identity; application grants here are bridges. */
IF EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id=DATABASE_PRINCIPAL_ID(N'public'))
 THROW 59310,'Registry public ownership/inheritance bridge.',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions p LEFT JOIN sys.all_objects o ON p.class=1 AND o.object_id=p.major_id
 WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'public') AND
 (p.state='D' AND p.permission_name LIKE '%DEFINITION%' OR p.state IN('G','W') AND
 (p.state='W' OR NOT(p.class=0 AND p.permission_name IN('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')
 OR p.class=1 AND p.permission_name IN('SELECT','EXECUTE','VIEW DEFINITION') AND OBJECT_SCHEMA_NAME(p.major_id) IN('sys','INFORMATION_SCHEMA') AND (COALESCE(o.is_ms_shipped,0)=1 OR p.major_id<0 AND p.permission_name='SELECT')
 OR p.class IN(1,3) AND p.permission_name='VIEW DEFINITION'))))
 THROW 59310,'Registry public application authority bridge.',1;
IF EXISTS(SELECT 1 FROM sys.triggers WHERE parent_class=0) THROW 59310,'Unreviewed database DDL trigger.',1;
DECLARE @RegistryDmlTables table(Name sysname PRIMARY KEY);
INSERT @RegistryDmlTables VALUES(N'AiExternalApplication'),(N'AiExternalApplicationRevision'),(N'AiTenantApplicationApproval'),
 (N'AiIntegrationRegistration'),(N'AiDelegationGrant'),(N'AiDelegatedSession'),(N'AiOAuthCredential'),(N'AiConsentIntent'),(N'EntityActionAuditLog'),(N'GlobalControlPlaneAuditLog');
/* Constraints/defaults/computed expressions and policy modules are also ownership-chain edges.
   Application checks may use built-ins and their own columns, never an unreviewed user module. */
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d JOIN sys.objects o ON o.object_id=d.referencing_id
 WHERE (o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @RegistryDmlTables)
 OR o.type IN('C','D') AND OBJECT_SCHEMA_NAME(o.parent_object_id)=N'dbo' AND OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @RegistryDmlTables))
 AND (d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR d.is_caller_dependent=1 OR d.is_ambiguous=1
 OR EXISTS(SELECT 1 FROM sys.objects f WHERE f.object_id=d.referenced_id AND f.is_ms_shipped=0 AND f.type IN('FN','IF','TF','FS','FT','P','PC','SN'))))
 THROW 59310,'Unreviewed registry table-expression dependency.',1;
IF EXISTS(SELECT 1 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE OBJECT_SCHEMA_NAME(p.target_object_id)=N'dbo' AND OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @RegistryDmlTables)
 AND (OBJECT_NAME(p.target_object_id) IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'GlobalControlPlaneAuditLog')
 OR s.object_id<>OBJECT_ID(N'sec.TenantFilter') OR s.is_enabled<>1 OR s.is_schema_bound<>1
 OR COALESCE(s.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=s.schema_id))<>1
 OR p.predicate_definition COLLATE Latin1_General_100_BIN2<>N'([sec].[fn_FilterByTenant]([ShaleClientId]))'))
 THROW 59310,'Unreviewed registry reachable policy dependency.',1;
DECLARE @RegistryTriggers table(ParentName sysname,Name sysname PRIMARY KEY);
INSERT @RegistryTriggers VALUES
 (N'AiIntegrationRegistration',N'AiImmutable_AiIntegrationRegistration'),(N'AiIntegrationRegistration',N'AiCascade_Registration'),
 (N'AiDelegationGrant',N'AiImmutable_AiDelegationGrant'),(N'AiDelegationGrant',N'AiCascade_Grant'),
 (N'AiDelegatedSession',N'AiImmutable_AiDelegatedSession'),(N'AiDelegatedSession',N'AiLifecycle_Session'),
 (N'AiOAuthCredential',N'AiImmutable_AiOAuthCredential'),(N'AiOAuthCredential',N'AiLifecycle_Credential'),
 (N'AiConsentIntent',N'AiImmutable_AiConsentIntent');
IF @RegistryBefore=0 INSERT @RegistryTriggers VALUES
 (N'AiExternalApplication',N'AiImmutable_ExternalApplication'),(N'AiExternalApplicationRevision',N'AiImmutable_ApplicationRevision'),(N'AiTenantApplicationApproval',N'AiImmutable_ApplicationApproval');
IF EXISTS(SELECT 1 FROM sys.triggers t WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables)
 AND NOT EXISTS(SELECT 1 FROM @RegistryTriggers a WHERE a.Name=t.name AND a.ParentName=OBJECT_NAME(t.parent_id)))
 OR EXISTS(SELECT 1 FROM @RegistryTriggers a WHERE NOT EXISTS(SELECT 1 FROM sys.triggers t WHERE t.name=a.Name AND t.parent_id=OBJECT_ID(N'dbo.'+a.ParentName) AND t.is_disabled=0 AND t.is_instead_of_trigger=0))
 THROW 59310,'Registry DML/terminal-audit trigger graph drift.',1;
IF EXISTS(SELECT 1 FROM sys.trigger_events e JOIN sys.triggers t ON t.object_id=e.object_id WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables) AND (e.is_first=1 OR e.is_last=1))
 THROW 59310,'Unreviewed registry trigger order.',1;
/* The generated source inserts exact approved module hashes, including two reviewed LF/CRLF forms. */
DECLARE @RegistryModules table(Name sysname PRIMARY KEY,HashLF binary(32),HashCRLF binary(32),IsNew bit);
INSERT @RegistryModules VALUES
(N'AiLockV1',0xa8f496e1f4b6603c060ba3c2d078e079b539f66efb531067c5d41c24dd393b18,0x11690cc9fe34318fb169104107ac9b4e578b7c513b8d58edf284ff7daf013934,0),
(N'AiActorV1',0x57a45b449a5446799ba7cf7e2964732b1bf7fcbee352331512b685ebe5b268d5,0x734eff41b16af46602287b6716fae52e7ccfcbf81bd6e23adcd66b3398b524aa,0),
(N'AiEnabledV1',0xdc249a14ec96452574628744c2827ef70a0e4cf71dd1d422626278052f8ee05d,0x29f2d406623e20ca043c83fda91a2d1dfbb28f5b5ebba55f2f9b093c85b2b861,0),
(N'AiImmutable_AiIntegrationRegistration',0xeec5f03324ce7d486c48073c86a016819da42261ca28c01d4069c3fd0b3ec364,0xeec5f03324ce7d486c48073c86a016819da42261ca28c01d4069c3fd0b3ec364,0),
(N'AiCascade_Registration',0x6ad9df3617747aaceebb385b25ffff3ffbd119c77a464a7d5a452e4b0b0d0f15,0xb0097b15d5771255bc3fffe9c48aac027d0ca4156c0b00c18c4623e1de7f9237,0),
(N'AiImmutable_AiDelegationGrant',0x1a6db0a9936d3bee2d3e837776d2a832b8b34f264074484988b3b40b1d852687,0x1a6db0a9936d3bee2d3e837776d2a832b8b34f264074484988b3b40b1d852687,0),
(N'AiCascade_Grant',0x54e0809c1ef0d41c1094d06a09cbbde977e49333cd2f048a6a050adbd9bd5b26,0x23f1df05694975687533a871bfa7674e4470ce164d912280d7460b47b7309ff1,0),
(N'AiImmutable_AiDelegatedSession',0xa8214790ba693b561b599620f9a32639897ec52bd9536da3aba2c01bc2870ee0,0xa8214790ba693b561b599620f9a32639897ec52bd9536da3aba2c01bc2870ee0,0),
(N'AiLifecycle_Session',0x5f37d2194961a07171b6bde9377179a1f21d8b3c9146ac024a32df2c354016c8,0x5aea1d7215b1a130d91e63dfe4401ff8dfe1179ac6cbdfc1ecced8d610515478,0),
(N'AiImmutable_AiOAuthCredential',0x8b4d9ec4efe813195e4fe418e334c9a6a8a72fd427044718a4de6d042cf6986a,0x8b4d9ec4efe813195e4fe418e334c9a6a8a72fd427044718a4de6d042cf6986a,0),
(N'AiLifecycle_Credential',0x5375f3e1440baf9c2651d0d693a76ac86813f1ba118416266666de9e6ff8a327,0xa045aea5d1592d9b99180b0b90753165834bf8ab4bc6bb32732a33e4fa291257,0),
(N'AiImmutable_AiConsentIntent',0x151ddefb6bc36706598c017528ea652eee58ea966a2804fdcf81998595de4552,0x151ddefb6bc36706598c017528ea652eee58ea966a2804fdcf81998595de4552,0),
(N'AiPublishApplicationV1',0x92712ff1439eb22f6ceb8b98c5c580f7d7d776aa93a5b3a3ba00ce23b8bb8d25,0xa28fccc241ae7349be250038aed48d9167f97cd962ee8c48f52bf6ac6191181b,1),
(N'AiRequestApplicationV1',0x0932fbb13de771682ca315f277c969348459431b965c3932e6500f3eecef47b5,0x901e9d597797fa8c67c0ce2b207ce51c5af4e0c2f0ab5af249836cb22fd250ea,1),
(N'AiDecideApplicationV1',0xa791c4e9a0d63d99608e132b3f44f0418fdaf610bd5c003431ca46c34a38f7ed,0xffdec5fddbacb7139623b457e7d7fa52381903a22d4edbd0039bef527d61feda,1),
(N'AiImmutable_ExternalApplication',0xf4c36352c10cba9dbf295928970362e2699ffd96d3478efed04428ea9eaf60c5,0x6d41bae0d0e25e639bf3b002905ee2496bf0116cf5b2c847507c92a8be0bbc71,1),
(N'AiImmutable_ApplicationRevision',0x77053d2860e902487aaf54d159e3498b26481ae4de9aea005aa973cf2593c486,0x77053d2860e902487aaf54d159e3498b26481ae4de9aea005aa973cf2593c486,1),
(N'AiImmutable_ApplicationApproval',0x68c41de426f2f8c77d20e698de9a946d42e830e4918c5a0a8555c273be4be515,0x54b20ce9ecd95b130958ba50d24131ad832624ca1ae7e7cab572e0f821602c0e,1);
IF EXISTS(SELECT 1 FROM @RegistryModules a WHERE (@RegistryBefore=0 OR a.IsNew=0) AND NOT EXISTS(
 SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE s.name=N'dbo' AND o.name=a.Name AND COALESCE(o.principal_id,s.principal_id)=1 AND m.execute_as_principal_id IS NULL
 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND HASHBYTES('SHA2_256',m.definition) IN(a.HashLF,a.HashCRLF)))
 OR EXISTS(SELECT 1 FROM sys.crypt_properties c WHERE c.major_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules WHERE @RegistryBefore=0 OR IsNew=0))
 THROW 59310,'Registry module source/settings/owner/signature drift.',1;
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d WHERE d.referencing_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules WHERE @RegistryBefore=0 OR IsNew=0)
 AND (d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR d.is_caller_dependent=1 OR d.is_ambiguous=1
 OR d.referenced_id IS NULL AND NOT(ISNULL(d.referenced_schema_name,N'')=N'sys' AND d.referenced_entity_name IN(N'sp_getapplock',N'sp_set_session_context')
 OR d.referenced_schema_name IS NULL AND d.referenced_entity_name IN(N'inserted',N'deleted'))))
 THROW 59310,'Registry unresolved/cross-database dependency.',1;
IF @RegistryBefore=1 BEGIN
 IF EXISTS(SELECT 1 FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'AiTenantApplicationApproval',N'AiPublishApplicationV1',N'AiRequestApplicationV1',N'AiDecideApplicationV1'))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant'))
 THROW 59310,'Registry preparation requires an uninstalled extension.',1;
END ELSE BEGIN
 IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant') AND type='R' AND owning_principal_id=1)<>2
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')))
 THROW 59310,'Registry role ownership/inheritance drift.',1;
 DECLARE @RegistryMembers table(Id int PRIMARY KEY,RoleId int,Sid varbinary(85),AuthenticationType int);
 INSERT @RegistryMembers SELECT p.principal_id,r.role_principal_id,p.sid,p.authentication_type
 FROM sys.database_role_members r JOIN sys.database_principals p ON p.principal_id=r.member_principal_id
 WHERE r.role_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'));
 IF EXISTS(SELECT 1 FROM @RegistryMembers a JOIN sys.database_principals p ON p.principal_id=a.Id WHERE p.type<>'S' OR p.principal_id<=4)
 OR EXISTS(SELECT 1 FROM sys.database_role_members r JOIN @RegistryMembers a ON a.Id=r.member_principal_id WHERE r.role_principal_id<>a.RoleId)
 OR EXISTS(SELECT 1 FROM sys.database_permissions p JOIN @RegistryMembers a ON p.grantee_principal_id=a.Id WHERE NOT(p.class=0 AND p.permission_name='CONNECT' AND p.state='G'))
 THROW 59310,'Registry member token/direct authority drift.',1;
 DECLARE @RegistryTokens table(Id int PRIMARY KEY);
 INSERT @RegistryTokens SELECT Id FROM @RegistryMembers;
 INSERT @RegistryTokens VALUES(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher')),(DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'));
 IF EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.types WHERE is_user_defined=1 AND principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.certificates WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 OR EXISTS(SELECT 1 FROM sys.assemblies WHERE principal_id IN(SELECT Id FROM @RegistryTokens))
 THROW 59310,'Registry securable ownership bridge.',1;
 /* Exact permissions: one/two EXECUTEs and six denials on each of three tables, no grant option. */
 IF EXISTS(SELECT 1 FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')) AND NOT(
 p.class=1 AND p.minor_id=0 AND p.grantor_principal_id=1 AND
 (p.state='G' AND p.permission_name='EXECUTE' AND OBJECT_SCHEMA_NAME(p.major_id)=N'dbo' AND
 (p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher') AND OBJECT_NAME(p.major_id)=N'AiPublishApplicationV1'
 OR p.grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationTenant') AND OBJECT_NAME(p.major_id) IN(N'AiRequestApplicationV1',N'AiDecideApplicationV1'))
 OR p.state='D' AND p.permission_name IN('SELECT','INSERT','UPDATE','DELETE','ALTER','CONTROL') AND OBJECT_SCHEMA_NAME(p.major_id)=N'dbo'
 AND OBJECT_NAME(p.major_id) IN(N'AiExternalApplication',N'AiExternalApplicationRevision',N'AiTenantApplicationApproval'))))
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'))<>19
 OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'))<>20
 THROW 59310,'Registry EXECUTE/table/column/schema permission drift.',1;
 IF (SELECT COUNT(*) FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id
 WHERE p.target_object_id=OBJECT_ID(N'dbo.AiTenantApplicationApproval') AND s.object_id=OBJECT_ID(N'sec.TenantFilter') AND s.is_enabled=1 AND s.is_schema_bound=1
 AND COALESCE(s.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=s.schema_id))=1
 AND p.predicate_definition COLLATE Latin1_General_100_BIN2=N'([sec].[fn_FilterByTenant]([ShaleClientId]))'
 AND (p.predicate_type_desc='FILTER' OR p.predicate_type_desc='BLOCK' AND p.operation_desc IN('AFTER INSERT','AFTER UPDATE')))<>3
 OR (SELECT COUNT(*) FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.AiTenantApplicationApproval'))<>3
 THROW 59310,'Registry strict RLS policy identity drift.',1;
 DECLARE @RegistryEdition int=CONVERT(int,SERVERPROPERTY('EngineEdition'));
 IF @RegistryEdition NOT IN(2,3,4,5,8) OR @RegistryEdition IS NULL THROW 59310,'Unknown registry platform.',1;
 IF @RegistryEdition=5 BEGIN
  IF EXISTS(SELECT 1 FROM @RegistryMembers WHERE AuthenticationType<>2) THROW 59310,'Contained registry SQL users required.',1;
 END ELSE BEGIN
  IF EXISTS(SELECT 1 FROM @RegistryMembers WHERE AuthenticationType<>1) THROW 59310,'Mapped registry SQL users required.',1;
  IF ISNULL(HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION'),0)<>1 OR ISNULL(HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DATABASE'),0)<>1
  THROW 59310,'Registry server catalog visibility required.',1;
  /* This branch is never issued on Azure SQL Database. */
  DECLARE @RegistryServerCheck nvarchar(max)=N'
  IF EXISTS(SELECT 1 FROM sys.database_role_members r JOIN sys.database_principals u ON u.principal_id=r.member_principal_id LEFT JOIN sys.server_principals s ON s.sid=u.sid
   WHERE r.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')) AND (s.type<>''S'' OR s.is_disabled=1 OR s.principal_id IS NULL))
  OR EXISTS(SELECT 1 FROM sys.server_role_members r JOIN sys.server_principals s ON s.principal_id=r.member_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id
   WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')))
  OR EXISTS(SELECT 1 FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id JOIN sys.database_principals u ON u.sid=s.sid JOIN sys.database_role_members d ON d.member_principal_id=u.principal_id
   WHERE d.role_principal_id IN(DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')) AND p.state IN(''G'',''W'') AND NOT(p.class=100 AND p.state=''G'' AND p.permission_name IN(''CONNECT SQL'',''VIEW ANY DATABASE'')))
  THROW 59310,''Registry SQL login server authority drift.'',1;';
  EXEC(@RegistryServerCheck);
 END;
END;

IF @RegistryBefore=0 BEGIN
SET NOCOUNT ON; IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1 THROW 59300,'Full catalog visibility required.',1;
DECLARE @Owned table(Name sysname PRIMARY KEY); INSERT @Owned VALUES (N'AiExternalApplication'),(N'AiExternalApplicationRevision'),(N'AiTenantApplicationApproval'),(N'AiPublishApplicationV1'),(N'AiRequestApplicationV1'),(N'AiDecideApplicationV1'),(N'AiImmutable_ExternalApplication'),(N'AiImmutable_ApplicationRevision'),(N'AiImmutable_ApplicationApproval'),(N'AiLockV1'),(N'AiActorV1'),(N'AiEnabledV1');
DECLARE @catalog nvarchar(max)=N'';
SET @catalog=@catalog+(SELECT CONCAT(o.name,N':',o.type,N':',COALESCE(CONVERT(nvarchar(20),o.principal_id),N''),N':',COALESCE(m.definition,N''),N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @Owned) ORDER BY o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()] FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @Owned) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.delete_referential_action,N':',fk.update_referential_action,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(fkc.constraint_object_id,N':',fkc.parent_column_id,N':',fkc.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns fkc WHERE OBJECT_NAME(fkc.parent_object_id) IN(SELECT Name FROM @Owned) ORDER BY fkc.constraint_object_id,fkc.constraint_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(OBJECT_NAME(i.object_id),N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',ic.column_id,N':',ic.key_ordinal,N':',ic.is_included_column,N':',ic.is_descending_key,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @Owned) ORDER BY i.object_id,i.index_id,ic.index_column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.target_object_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N':',sp.is_enabled,N':',sp.is_schema_bound,N';') AS [text()] FROM sys.security_predicates p JOIN sys.security_policies sp ON sp.object_id=p.object_id WHERE OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @Owned) ORDER BY p.target_object_id,p.security_predicate_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(p.major_id,N':',p.minor_id,N':',p.grantor_principal_id,N':',p.grantee_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @Owned) ORDER BY p.major_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+(SELECT CONCAT(t.object_id,N':',t.is_disabled,N';') AS [text()] FROM sys.triggers t WHERE t.name IN(SELECT Name FROM @Owned) ORDER BY t.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',owning_principal_id,N';') AS [text()] FROM sys.database_principals WHERE name IN(N'AiApplicationPublisher',N'AiApplicationTenant') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE(OBJECT_DEFINITION(OBJECT_ID(N'sec.fn_FilterByTenant')),N'MISSING');

SET @catalog=@catalog+COALESCE((SELECT CONCAT(p.class,N':',p.major_id,N':',p.minor_id,N':',USER_NAME(p.grantee_principal_id),N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.grantee_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');
SET @catalog=@catalog+COALESCE((SELECT CONCAT(name,N':',definition,N':',is_disabled,N':',is_not_trusted,N';') AS [text()] FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') ORDER BY name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)'),N'');

IF HASHBYTES('SHA2_256',@catalog)<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalApplication') AND name=N'Shale.UniversalMcpPR3'),0x) THROW 59300,'PR 3 catalog drift; never refresh receipts.',1;

IF (SELECT COUNT(*) FROM sys.schemas WHERE name IN(N'dbo',N'sec') AND principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))<>2
 OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')))
 OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')))
 THROW 59300,'PR 3 schema ownership or inherited role authority drift.',1;

DECLARE @RegistryGraph nvarchar(max)=@catalog+CONVERT(nvarchar(64),@RegistryLegacyHash,2);
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(t.parent_id),N':',t.name,N':',t.is_disabled,N':',t.is_instead_of_trigger,N':',e.type,N':',e.is_first,N':',e.is_last,N':',m.execute_as_principal_id,N':',m.uses_ansi_nulls,N':',m.uses_quoted_identifier,N':',USER_NAME(COALESCE(o.principal_id,s.principal_id)),N':',m.definition,N';') AS [text()]
 FROM sys.triggers t JOIN sys.objects o ON o.object_id=t.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=t.object_id LEFT JOIN sys.trigger_events e ON e.object_id=t.object_id
 WHERE OBJECT_SCHEMA_NAME(t.parent_id)=N'dbo' AND OBJECT_NAME(t.parent_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY t.parent_id,t.name,e.type FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(p.target_object_id),N':',OBJECT_SCHEMA_NAME(p.object_id),N':',OBJECT_NAME(p.object_id),N':',s.principal_id,N':',p.predicate_definition,N':',p.predicate_type,N':',p.operation,N';') AS [text()]
 FROM sys.security_predicates p JOIN sys.security_policies s ON s.object_id=p.object_id WHERE OBJECT_SCHEMA_NAME(p.target_object_id)=N'dbo' AND OBJECT_NAME(p.target_object_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY p.target_object_id,p.predicate_type,p.operation FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(d.referencing_id),N':',d.referenced_class,N':',d.referenced_schema_name,N':',d.referenced_entity_name,N':',d.referenced_minor_id,N':',d.is_caller_dependent,N':',d.is_ambiguous,N';') AS [text()]
 FROM sys.sql_expression_dependencies d WHERE d.referencing_id IN(SELECT OBJECT_ID(N'dbo.'+Name) FROM @RegistryModules)
 ORDER BY d.referencing_id,d.referenced_class,d.referenced_id,d.referenced_minor_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.name,N':',c.column_id,N':',c.system_type_id,N':',c.user_type_id,N':',c.max_length,N':',c.precision,N':',c.scale,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N':',cc.definition,N';') AS [text()]
 FROM sys.columns c LEFT JOIN sys.computed_columns cc ON cc.object_id=c.object_id AND cc.column_id=c.column_id
 WHERE OBJECT_SCHEMA_NAME(c.object_id)=N'dbo' AND OBJECT_NAME(c.object_id) IN(SELECT Name FROM @RegistryDmlTables) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryGraph=@RegistryGraph+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.delete_referential_action,N':',fk.update_referential_action,N':',fk.is_disabled,N':',fk.is_not_trusted,N';') AS [text()]
 FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id
 WHERE OBJECT_SCHEMA_NAME(o.parent_object_id)=N'dbo' AND OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @RegistryDmlTables) AND o.type IN('C','D','F') ORDER BY o.object_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @RegistryDigest=HASHBYTES('SHA2_256',@RegistryGraph);
IF @RegistrySealing=0 AND @RegistryDigest<>ISNULL((SELECT CONVERT(varbinary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiRegistryConfinementV1') AND name=N'Shale.RegistryConfinementV1.Catalog'),0x)
 THROW 59310,'Registry confinement catalog drift; never refresh receipts.',1;

END;
SELECT 0 AS RegistryConfinementFindingCount;
