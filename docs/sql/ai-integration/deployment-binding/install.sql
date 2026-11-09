:on error exit
/* PROPOSAL ONLY. Never run during development. Separately authorized single INSERT only. */
:r docs/sql/ai-integration/deployment-binding/session.sql
:r docs/sql/ai-integration/deployment-binding/inputs.sql
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
IF ISNULL(@BindApproved,0)<>1 THROW 59303,'Separate exact binding installation authorization required.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 /* Table lock serializes concurrent installers including a noncooperating INSERT. */
 DECLARE @BindExistingCount bigint;
 SELECT @BindExistingCount=COUNT_BIG(*) FROM dbo.AiCaseDataDeployment WITH(TABLOCKX,HOLDLOCK);
:r docs/sql/ai-integration/phase3b8-confinement-verify.sql
:r docs/sql/ai-integration/deployment-binding/validate.sql
 IF @BindExistingCount=0
 BEGIN
  INSERT dbo.AiCaseDataDeployment(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone)
   VALUES(1,@BindIssuer,@BindResource,'case-read-v1',CONVERT(varchar(100),@BindJavaZone),CONVERT(sysname,@BindSqlZone));
  IF @@ROWCOUNT<>1 THROW 59303,'Exactly one initial row required.',1;
 END;
:r docs/sql/ai-integration/deployment-binding/validate.sql
 IF (SELECT COUNT_BIG(*) FROM dbo.AiCaseDataDeployment)<>1
  THROW 59303,'Binding verification failed before commit.',1;
 COMMIT TRANSACTION;
 SELECT CASE WHEN @BindExistingCount=0 THEN 'INSTALLED_ONE_BINDING'
 ELSE 'EXACT_EXISTING_BINDING_NO_CHANGE' END AS BindingStatus;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
