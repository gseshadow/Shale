-- Empty catalog XML scalar results are SQL NULL; canonicalize only those empty serializations.
-- No effective permission UNKNOWN is canonicalized.
-- Include after context; hashes are database/engine-specific change-control identities, not approval.
DELETE #LegacyFingerprint;
INSERT #LegacyFingerprint(OtherHash)
SELECT HASHBYTES('SHA2_256',CONVERT(nvarchar(max),DB_NAME()) + N'|' +
 CONVERT(nvarchar(128),DATABASEPROPERTYEX(DB_NAME(),'Collation')) +
 COALESCE(CONVERT(nvarchar(max),(SELECT principal_id,name,type,sid,owning_principal_id,is_fixed_role FROM sys.database_principals ORDER BY principal_id FOR XML RAW('Row'), ROOT('Principals'), BINARY BASE64)),N'<Principals/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.database_role_members ORDER BY role_principal_id,member_principal_id FOR XML RAW('Row'), ROOT('Members'), BINARY BASE64)),N'<Members/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.schemas ORDER BY schema_id FOR XML RAW('Row'), ROOT('Schemas'), BINARY BASE64)),N'<Schemas/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT object_id,schema_id,name,type,principal_id,parent_object_id FROM sys.objects WHERE is_ms_shipped=0 ORDER BY object_id FOR XML RAW('Row'), ROOT('Objects'), BINARY BASE64)),N'<Objects/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT object_id,CONVERT(varchar(66),HASHBYTES('SHA2_256',definition),1) AS DefinitionHash,uses_ansi_nulls,uses_quoted_identifier,is_schema_bound,uses_database_collation,is_recompiled,null_on_null_input,execute_as_principal_id FROM sys.sql_modules ORDER BY object_id FOR XML RAW('Row'), ROOT('Modules'), BINARY BASE64)),N'<Modules/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT c.object_id,c.column_id,c.name,c.system_type_id,c.user_type_id,c.max_length,c.precision,c.scale,c.is_nullable,c.is_computed,c.collation_name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id WHERE o.is_ms_shipped=0 ORDER BY c.object_id,c.column_id FOR XML RAW('Row'), ROOT('Columns'), BINARY BASE64)),N'<Columns/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT user_type_id,schema_id,name,system_type_id,max_length,precision,scale,is_nullable,is_user_defined,is_table_type FROM sys.types ORDER BY user_type_id FOR XML RAW('Row'), ROOT('Types'), BINARY BASE64)),N'<Types/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.parameters WHERE object_id>0 ORDER BY object_id,parameter_id FOR XML RAW('Row'), ROOT('Parameters'), BINARY BASE64)),N'<Parameters/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.crypt_properties ORDER BY class,major_id,thumbprint FOR XML RAW('Row'), ROOT('Signatures'), BINARY BASE64)),N'<Signatures/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.sql_expression_dependencies ORDER BY referencing_id,referencing_minor_id,referenced_class,referenced_id,referenced_minor_id,referenced_server_name,referenced_database_name,referenced_schema_name,referenced_entity_name FOR XML RAW('Row'), ROOT('Dependencies'), BINARY BASE64)),N'<Dependencies/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.synonyms ORDER BY object_id FOR XML RAW('Row'), ROOT('Synonyms'), BINARY BASE64)),N'<Synonyms/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.triggers ORDER BY object_id FOR XML RAW('Row'), ROOT('Triggers'), BINARY BASE64)),N'<Triggers/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.security_policies ORDER BY object_id FOR XML RAW('Row'), ROOT('Policies'), BINARY BASE64)),N'<Policies/>') +
 COALESCE(CONVERT(nvarchar(max),(SELECT * FROM sys.security_predicates ORDER BY object_id,security_predicate_id FOR XML RAW('Row'), ROOT('Predicates'), BINARY BASE64)),N'<Predicates/>'));
UPDATE #LegacyFingerprint SET StateHash=HASHBYTES('SHA2_256',
 CONVERT(varchar(66),OtherHash,1)+CONVERT(nvarchar(max),(SELECT * FROM sys.database_permissions
 ORDER BY class,major_id,minor_id,grantee_principal_id,grantor_principal_id,type FOR XML RAW('Row'), ROOT('ACL'), BINARY BASE64)));
IF EXISTS(SELECT 1 FROM #LegacyFingerprint WHERE StateHash IS NULL OR OtherHash IS NULL)
 OR (SELECT COUNT(*) FROM #LegacyFingerprint)<>1
 THROW 58349,N'Incomplete catalog fingerprint; no NULL digest is acceptable.',1;
GO
