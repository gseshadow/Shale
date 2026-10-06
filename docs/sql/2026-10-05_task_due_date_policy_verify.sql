IF OBJECT_ID(N'dbo.TaskPolicyConfigurations', N'U') IS NULL
BEGIN
    THROW 57051, 'TaskPolicyConfigurations does not exist.', 1;
END;
IF EXISTS (SELECT 1 FROM dbo.TaskPolicyConfigurations WHERE DueDatePolicy NOT IN ('OPTIONAL','WARN','REQUIRED'))
BEGIN
    THROW 57052, 'Invalid task due-date policy exists.', 1;
END;
IF EXISTS (SELECT ShaleClientId FROM dbo.TaskPolicyConfigurations GROUP BY ShaleClientId HAVING COUNT(*) > 1)
BEGIN
    THROW 57053, 'Duplicate tenant task policy exists.', 1;
END;
SELECT p.ShaleClientId,p.DueDatePolicy FROM dbo.TaskPolicyConfigurations p ORDER BY p.ShaleClientId;
SELECT sp.is_enabled AS RlsEnabled, COUNT_BIG(*) AS PredicateCount
FROM sys.security_policies sp JOIN sys.security_predicates pr ON pr.object_id=sp.object_id
WHERE pr.target_object_id=OBJECT_ID(N'dbo.TaskPolicyConfigurations') GROUP BY sp.is_enabled;
-- Under two separately stamped non-bypass sessions, update one visible tenant to OPTIONAL,
-- verify the other tenant remains invisible/unchanged, then restore WARN. RLS functions are not invoked as scalars.
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID(N'dbo.EntityActionAuditLog') AND definition LIKE '%TASK_POLICY_CONFIGURATION%')
BEGIN
    THROW 57056, 'Task policy entity-action audit mapping is missing.', 1;
END;
SELECT TOP (20) ShaleClientId,ActorUserId,EntityId,Action,OccurredAt,Metadata
FROM dbo.EntityActionAuditLog WHERE EntityType='TASK_POLICY_CONFIGURATION' ORDER BY Id DESC;
-- After an administrator mutation, verify exactly one UPDATED row contains only PREVIOUS_POLICY and RESULTING_POLICY,
-- and verify its tenant, actor, entity id, and database timestamp match the committed configuration change.
