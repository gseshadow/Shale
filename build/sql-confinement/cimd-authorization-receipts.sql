CREATE PROCEDURE dbo.AiCimdReadAuthorizationReceiptsV1
WITH EXECUTE AS OWNER
AS BEGIN
 SET NOCOUNT ON;
 /* INSERT EXEC may own a statement transaction. This accessor only reads immutable metadata. */
 IF (@@OPTIONS & 2)<>0
  OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL
  THROW 59520,'Neutral metadata lease required.',1;
 IF (SELECT COUNT(*) FROM dbo.AiCaseDataDeployment)<>1
  OR (SELECT COUNT(*) FROM dbo.AiCimdProofReceiptV1)<>1
  OR (SELECT COUNT(*) FROM dbo.AiCimdOAuthReceiptV1)<>1
  THROW 59520,'Complete immutable metadata required.',1;
 SELECT
  (SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()]
   FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)') AS BindingState,
  (SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()]
   FROM dbo.AiCimdProofReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)') AS ProofState,
  p.SourceHash AS ProofSource,p.BeforeHash AS ProofBefore,p.CatalogHash AS ProofCatalog,
  o.SourceHash AS OAuthSource,o.BeforeHash AS OAuthBefore,o.CatalogHash AS OAuthCatalog
 FROM dbo.AiCimdProofReceiptV1 p CROSS JOIN dbo.AiCimdOAuthReceiptV1 o
 WHERE p.Id=1 AND o.Id=1;
END;
