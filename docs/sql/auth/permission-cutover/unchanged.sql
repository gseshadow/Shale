-- Every original row must remain byte-for-byte/catalog-value identical, including grantor and state.
IF EXISTS(SELECT * FROM #PriorModules EXCEPT SELECT * FROM sys.sql_modules)
 OR EXISTS(SELECT * FROM #PriorOwners EXCEPT SELECT object_id,principal_id,schema_id FROM sys.objects)
 OR EXISTS(SELECT * FROM #PriorPermissions EXCEPT SELECT * FROM sys.database_permissions)
 OR EXISTS(SELECT * FROM #PriorMemberships EXCEPT SELECT * FROM sys.database_role_members)
 OR EXISTS(SELECT * FROM #PriorPredicates EXCEPT SELECT object_id,security_predicate_id,target_object_id,
 predicate_definition,predicate_type,operation FROM sys.security_predicates)
 OR EXISTS(SELECT object_id,security_predicate_id,target_object_id,predicate_definition,predicate_type,operation
 FROM sys.security_predicates EXCEPT SELECT * FROM #PriorPredicates)
 THROW 58309,N'Original permissions, grantors, states, memberships or predicates changed; rollback.',1;
GO

IF (SELECT COUNT(*) FROM (SELECT * FROM sys.database_permissions EXCEPT SELECT * FROM #PriorPermissions) added)
 <> (SELECT PermissionCount FROM #CutoverDelta)
 OR (SELECT COUNT(*) FROM (SELECT * FROM sys.database_role_members EXCEPT SELECT * FROM #PriorMemberships) added)
 <> (SELECT MembershipCount FROM #CutoverDelta)
 THROW 58327,N'Unexpected permission or membership delta; rollback.',1;
GO
