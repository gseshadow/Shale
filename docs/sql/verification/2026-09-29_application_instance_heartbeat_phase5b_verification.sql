/* Every successful check returns FindingCount = 0. Run independently after Phase 5B migration. */
SELECT N'ApplicationInstancesExists' CheckName, COUNT(*) FindingCount WHERE OBJECT_ID(N'dbo.ApplicationInstances',N'U') IS NULL;
SELECT N'HeartbeatColumns' CheckName, COUNT(*) FindingCount FROM (VALUES(N'LastHeartbeatAt'),(N'LastHumanActivityAt')) e(Name) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND c.name=e.Name LEFT JOIN sys.types t ON t.user_type_id=c.user_type_id WHERE c.column_id IS NULL OR t.name<>N'datetime2' OR c.scale<>7 OR c.is_nullable<>1;
SELECT N'OriginalPhase4BColumns' CheckName, COUNT(*) FindingCount FROM (VALUES(N'Id'),(N'ShaleClientId'),(N'UserId'),(N'MachineId'),(N'ClientType'),(N'MajorVersion'),(N'MinorVersion'),(N'BuildVersion'),(N'StartedAt'),(N'EndedAt'),(N'CreatedAt'),(N'UpdatedAt'),(N'RowVer')) e(Name) LEFT JOIN sys.columns c ON c.object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND c.name=e.Name WHERE c.column_id IS NULL;
SELECT N'OriginalPrimaryKey' CheckName, COUNT(*) FindingCount WHERE NOT EXISTS(SELECT 1 FROM sys.key_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name=N'PK_ApplicationInstances' AND type=N'PK');
SELECT N'OriginalForeignKeys' CheckName, 2-COUNT(*) FindingCount FROM sys.foreign_keys WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name IN(N'FK_ApplicationInstances_Client',N'FK_ApplicationInstances_UserTenant') AND is_disabled=0 AND is_not_trusted=0;
SELECT N'OriginalChecks' CheckName, 4-COUNT(*) FindingCount FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name IN(N'CK_ApplicationInstances_ClientType',N'CK_ApplicationInstances_Machine',N'CK_ApplicationInstances_Version',N'CK_ApplicationInstances_Lifecycle') AND is_disabled=0 AND is_not_trusted=0;
SELECT N'StrictRlsPredicateCount' CheckName, ABS(3-COUNT(*)) FindingCount FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationInstances');
SELECT N'FilterPredicate' CheckName, COUNT(*) FindingCount WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'FILTER' AND predicate_definition COLLATE DATABASE_DEFAULT LIKE N'%fn_FilterByTenant%');
SELECT N'InsertBlockPredicate' CheckName, COUNT(*) FindingCount WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER INSERT');
SELECT N'UpdateBlockPredicate' CheckName, COUNT(*) FindingCount WHERE NOT EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_type_desc=N'BLOCK' AND operation_desc=N'AFTER UPDATE');
SELECT N'NoOverlayPredicate' CheckName, COUNT(*) FindingCount FROM sys.security_predicates WHERE target_object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND predicate_definition COLLATE DATABASE_DEFAULT LIKE N'%OrGlobal%';
SELECT N'NoForcedBackfillOrDefault' CheckName, COUNT(*) FindingCount FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.ApplicationInstances') AND name IN(N'LastHeartbeatAt',N'LastHumanActivityAt') AND default_object_id<>0;
SELECT N'PriorPhaseTables' CheckName, COUNT(*) FindingCount FROM (VALUES(N'ApplicationReleases'),(N'ApplicationReleaseItems'),(N'ApplicationPolicy'),(N'UserReleaseState')) e(Name) WHERE OBJECT_ID(N'dbo.'+e.Name,N'U') IS NULL;
/* Backward-compatible Phase 4B insert shape: execute in a rollback-only transaction under a valid tenant/user SESSION_CONTEXT. */
BEGIN TRANSACTION;
DECLARE @Tenant int=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')),@User int=TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId'));
IF @Tenant IS NOT NULL AND @User IS NOT NULL
BEGIN
 INSERT dbo.ApplicationInstances(ShaleClientId,UserId,MachineId,ClientType,MajorVersion,MinorVersion,BuildVersion) VALUES(@Tenant,@User,NEWID(),'DESKTOP',1,0,0);
 SELECT N'OldClientInsertLeavesHeartbeatNull' CheckName, COUNT(*) FindingCount FROM dbo.ApplicationInstances WHERE Id=SCOPE_IDENTITY() AND (LastHeartbeatAt IS NOT NULL OR LastHumanActivityAt IS NOT NULL);
END;
ROLLBACK TRANSACTION;
