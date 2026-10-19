/* Read-only evidence for the existing Windows sandbox. Retain output privately.
   Connect only to 127.0.0.1:14333 with the existing local provisioner. No Azure. */
SET NOCOUNT ON;
IF DB_NAME()<>N'ShaleRetirementExercise_3b484ef49823414f942e872efe21cf6c'
 THROW 58200,'Wrong local verification database; stop.',1;
IF SERVERPROPERTY('EngineEdition')=5
 THROW 58201,'Azure is outside this verification.',1;
SELECT SYSUTCDATETIME() AS DatabaseUtcNow,DB_NAME() AS InspectionDatabase;
SELECT c.name AS ConstraintName,c.definition AS ConstraintDefinition,
       c.is_disabled AS IsDisabled,c.is_not_trusted AS IsNotTrusted
FROM sys.check_constraints c
WHERE c.parent_object_id=OBJECT_ID(N'dbo.UserSessions');
SELECT col.name AS ColumnName,def.definition AS DefaultDefinition
FROM sys.columns col
LEFT JOIN sys.default_constraints def ON def.object_id=col.default_object_id
WHERE col.object_id IN(OBJECT_ID(N'dbo.UserSessions'),OBJECT_ID(N'dbo.DesktopRememberCredentials'))
  AND col.name IN(N'IssuedAt',N'CreatedAt',N'UpdatedAt',N'LastRefreshedAt',N'RevokedAt');
SELECT TOP(10) s.SessionId,s.IssuedAt,s.LastRefreshedAt,s.UpdatedAt,s.ExpiresAt,s.RevokedAt,s.RevocationReason,
       r.AbsoluteExpiresAt,r.RotatedAt,r.ConsumedAt
FROM dbo.UserSessions s
JOIN dbo.DesktopRememberCredentials r ON r.SessionId=s.SessionId AND r.ShaleClientId=s.ShaleClientId AND r.UserId=s.UserId
ORDER BY s.Id DESC;
SELECT p.name AS PolicyName,p.is_enabled AS IsEnabled,sp.predicate_type_desc AS PredicateType,
       sp.operation_desc AS Operation,sp.predicate_definition AS PredicateDefinition
FROM sys.security_predicates sp
JOIN sys.security_policies p ON p.object_id=sp.object_id
WHERE sp.target_object_id=OBJECT_ID(N'dbo.UserSessions');
