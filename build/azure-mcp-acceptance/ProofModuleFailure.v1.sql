/* Acceptance-only catch-path diagnostics. @C4aModules is the original expected registry.
   Read metadata before rollback; return only fixed public names and failure bits. */
IF ERROR_NUMBER()=59400 AND ERROR_MESSAGE()=N'CIMD proof module source/owner/context drift.'
BEGIN
 BEGIN TRY
  SELECT CAST(CONCAT('PROOF_MODULE_FAIL:',e.Name,':',
    CASE WHEN o.object_id IS NULL THEN 1 ELSE 0 END,
    CASE WHEN m.definition IS NULL THEN 1 ELSE 0 END,
    CASE WHEN o.object_id IS NULL OR COALESCE(o.principal_id,s.principal_id)<>1 THEN 1 ELSE 0 END,
    CASE WHEN ISNULL(m.uses_ansi_nulls,0)<>1 THEN 1 ELSE 0 END,
    CASE WHEN ISNULL(m.uses_quoted_identifier,0)<>1 THEN 1 ELSE 0 END,
    CASE WHEN (e.OwnerLookup=1 AND m.execute_as_principal_id=-2)
           OR (e.OwnerLookup=0 AND m.execute_as_principal_id IS NULL AND m.object_id IS NOT NULL)
         THEN 0 ELSE 1 END,
    CASE WHEN HASHBYTES('SHA2_256',m.definition)=e.Hash THEN 0 ELSE 1 END) AS varchar(160)) AS ProofModuleDiagnostic
  FROM @C4aModules e
  LEFT JOIN sys.schemas s ON s.name=N'dbo'
  LEFT JOIN sys.objects o ON o.schema_id=s.schema_id AND o.name=e.Name
  LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
  WHERE NOT EXISTS(SELECT 1 FROM sys.objects x JOIN sys.schemas y ON y.schema_id=x.schema_id
    JOIN sys.sql_modules z ON z.object_id=x.object_id WHERE y.name=N'dbo' AND x.name=e.Name
    AND COALESCE(x.principal_id,y.principal_id)=1 AND z.uses_ansi_nulls=1 AND z.uses_quoted_identifier=1
    AND ((e.OwnerLookup=1 AND z.execute_as_principal_id=-2) OR (e.OwnerLookup=0 AND z.execute_as_principal_id IS NULL))
    AND HASHBYTES('SHA2_256',z.definition)=e.Hash)
  ORDER BY e.Name;
 END TRY
 BEGIN CATCH
  /* Diagnostics must never prevent the original rollback and rethrow. */
  DECLARE @AcceptanceDiagnosticUnavailable bit=1;
 END CATCH;
END;
