/* Forward-only Phase 2D: move current Case Date confirmation policy ownership from New Intake fields to tenant-effective Case Date Types. */
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.FieldConfirmationPolicies',N'U') IS NULL THROW 57100,'Phase 2A field confirmation schema is missing.',1;
 IF COL_LENGTH(N'dbo.FieldConfirmationPolicies',N'CaseDateTypePolicyKey') IS NULL
  ALTER TABLE dbo.FieldConfirmationPolicies ADD CaseDateTypePolicyKey varchar(160) NULL;
 /* Enabled legacy policies require a human rollout decision. They are neither discarded nor reinterpreted. */
 IF EXISTS(SELECT 1 FROM dbo.FieldConfirmationPolicies WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL AND RequiresConfirmation=1)
  THROW 57101,'Enabled New Intake field confirmation policies exist. Review the read-only Phase 2D verification output and explicitly retire or migrate each policy before applying Phase 2D.',1;
 ALTER TABLE dbo.FieldConfirmationPolicies ALTER COLUMN FormKey varchar(64) NULL;
 ALTER TABLE dbo.FieldConfirmationPolicies ALTER COLUMN FieldKey varchar(128) NULL;
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FieldConfirmationPolicies') AND name=N'UX_FieldConfirmationPolicies_Current')
  DROP INDEX UX_FieldConfirmationPolicies_Current ON dbo.FieldConfirmationPolicies;
 CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Current ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey)
  WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NULL;
 CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_CurrentCaseDateType ON dbo.FieldConfirmationPolicies(ShaleClientId,CaseDateTypePolicyKey)
  WHERE SupersededAt IS NULL AND CaseDateTypePolicyKey IS NOT NULL;
 IF EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.FieldConfirmationPolicies') AND name=N'UX_FieldConfirmationPolicies_Field_Revision')
  DROP INDEX UX_FieldConfirmationPolicies_Field_Revision ON dbo.FieldConfirmationPolicies;
 CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_Field_Revision ON dbo.FieldConfirmationPolicies(ShaleClientId,FormKey,FieldKey,PolicyRevision)
  WHERE CaseDateTypePolicyKey IS NULL;
 CREATE UNIQUE INDEX UX_FieldConfirmationPolicies_CaseDateType_Revision ON dbo.FieldConfirmationPolicies(ShaleClientId,CaseDateTypePolicyKey,PolicyRevision)
  WHERE CaseDateTypePolicyKey IS NOT NULL;
 ALTER TABLE dbo.FieldConfirmationPolicies WITH CHECK ADD CONSTRAINT CK_FieldConfirmationPolicies_Identity
  CHECK ((CaseDateTypePolicyKey IS NULL AND FormKey IS NOT NULL AND FieldKey IS NOT NULL) OR
         (CaseDateTypePolicyKey IS NOT NULL AND FormKey IS NULL AND FieldKey IS NULL));
 COMMIT;
END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;
