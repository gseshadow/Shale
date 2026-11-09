:on error exit
:r docs/sql/ai-integration/deployment-binding/session.sql
:r docs/sql/ai-integration/deployment-binding/inputs.sql
:r docs/sql/ai-integration/phase3b8-confinement-verify.sql
:r docs/sql/ai-integration/deployment-binding/validate.sql
SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.AiCaseDataDeployment)
 THEN 'EXACT_EXISTING_BINDING_NO_CHANGE' ELSE 'EMPTY_READY_FOR_SEPARATE_INSTALL_AUTHORIZATION' END AS BindingStatus;
SELECT Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment;
