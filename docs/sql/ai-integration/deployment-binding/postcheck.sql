:on error exit
:r docs/sql/ai-integration/deployment-binding/preflight.sql
IF (SELECT COUNT_BIG(*) FROM dbo.AiCaseDataDeployment)<>1
 THROW 59304,'Postcheck requires exactly one matching committed binding.',1;
SELECT 'BINDING_INSTALLED_ONLY_ACTIVATION_NOT_AUTHORIZED' AS BindingStatus;
