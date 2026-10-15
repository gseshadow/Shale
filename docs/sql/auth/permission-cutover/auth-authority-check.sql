-- Shared fail-closed decision for preflight, installer and standalone diagnosis.
IF EXISTS(SELECT 1 FROM #AuthAuthorityChecks WHERE EffectiveValue=1 OR EffectiveValue IS NULL)
 THROW 58307,N'Auth identity has unexpected broad/escalation authority or UNKNOWN evidence; inspect diagnostics.',1;
GO
