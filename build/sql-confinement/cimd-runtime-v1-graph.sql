/* Supplemental closed execution graph. Every reached user module must have reviewed
   bytes; unknown edges fail rather than extending the historical catalog's scope. */
DECLARE @RuntimeModules table(Name sysname,Hash binary(32));
-- EXPECTED_MODULES
DECLARE @Reach table(Id int PRIMARY KEY);
INSERT @Reach SELECT o.object_id FROM sys.objects o WHERE o.schema_id=SCHEMA_ID('dbo') AND o.name IN
('AiCimdDataAuthenticateV1','AiCimdDataSearchCasesV1','AiCimdDataGetCaseOverviewV1','AiCimdDataRecordCaseFailureV1','AiCimdDataFinishCaseQuotaV1','AiCimdDataAuthorizeCaseHandoffV1',
 'AiCimdRevokeFamilyV1','AiCimdLoadClientV1','AiCimdBeginOAuthV1','AiCimdFindOAuthIntentV1','AiCimdApproveOAuthV1','AiCimdCompleteOAuthV1','AiCimdDenyOAuthV1',
 'AiCimdLoadClientProofV1','AiCimdRedeemV1','AiCimdRefreshV1','AiCimdRevokeCredentialV1','AiCimdResolveCodeV1','AiCimdResolveRefreshV1','AiCimdResolveRevocationV1',
 'AiCimdResolvePublicationV1','AiCimdPublishMetadataV1','AiCimdRevokeUnknownV1','AiPublishApplicationV1','AiRequestApplicationV1','AiDecideApplicationV1');
IF (SELECT COUNT(*) FROM @Reach)<>26 THROW 59600,'Runtime root missing.',1;
DECLARE @added int=1;
WHILE @added<>0 BEGIN
 INSERT @Reach SELECT DISTINCT e.Id FROM (
 SELECT d.referenced_id AS Id FROM sys.sql_expression_dependencies d JOIN @Reach r ON r.Id=d.referencing_id WHERE d.referenced_id IS NOT NULL
 UNION SELECT t.object_id FROM sys.triggers t JOIN @Reach r ON r.Id=t.parent_id
 UNION SELECT o.object_id FROM sys.objects o JOIN @Reach r ON r.Id=o.parent_object_id WHERE o.type IN('C','D')
 UNION SELECT OBJECT_ID(REPLACE(LEFT(p.predicate_definition,CHARINDEX('(',p.predicate_definition)-1),' ','')) FROM sys.security_predicates p JOIN @Reach r ON r.Id=p.target_object_id
 UNION SELECT f.parent_object_id FROM sys.foreign_keys f JOIN @Reach r ON r.Id=f.referenced_object_id WHERE f.delete_referential_action<>0 OR f.update_referential_action<>0
 ) e JOIN sys.objects o ON o.object_id=e.Id WHERE o.is_ms_shipped=0 AND NOT EXISTS(SELECT 1 FROM @Reach old WHERE old.Id=e.Id);
 SET @added=@@ROWCOUNT;
END;
IF EXISTS(SELECT 1 FROM sys.sql_expression_dependencies d JOIN @Reach r ON r.Id=d.referencing_id
 WHERE d.referenced_server_name IS NOT NULL OR d.referenced_database_name IS NOT NULL OR d.is_caller_dependent=1 OR d.is_ambiguous=1
 OR d.referenced_id IS NULL AND NOT(ISNULL(d.referenced_schema_name,'')='sys' AND d.referenced_entity_name IN('sp_set_session_context','sp_getapplock')
 OR d.referenced_schema_name IS NULL AND d.referenced_entity_name IN('inserted','deleted')))
 THROW 59600,'Unresolved or cross-database runtime edge.',1;
IF EXISTS(SELECT 1 FROM @Reach r JOIN sys.objects o ON o.object_id=r.Id LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE COALESCE(o.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=o.schema_id))<>1
 OR o.type IN('SN','FS','FT','PC','X','AF')
 OR m.object_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM @RuntimeModules a WHERE a.Name=o.name AND a.Hash=HASHBYTES('SHA2_256',m.definition)))
 OR EXISTS(SELECT 1 FROM sys.crypt_properties c JOIN @Reach r ON r.Id=c.major_id)
 OR EXISTS(SELECT 1 FROM sys.triggers t JOIN @Reach r ON r.Id=t.object_id WHERE t.is_disabled=1 OR t.is_instead_of_trigger=1)
 THROW 59600,'Unreviewed runtime module, ownership or trigger.',1;
IF EXISTS(SELECT 1 FROM sys.synonyms) OR EXISTS(SELECT 1 FROM sys.external_data_sources) OR EXISTS(SELECT 1 FROM sys.external_tables)
 OR EXISTS(SELECT 1 FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on=1 OR is_db_chaining_on=1))
 THROW 59600,'Runtime database isolation drift.',1;
