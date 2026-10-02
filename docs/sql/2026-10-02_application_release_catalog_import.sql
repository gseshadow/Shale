/* Additive global release-import metadata and append-only audit storage. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID(N'dbo.ApplicationReleases',N'U') IS NULL OR OBJECT_ID(N'dbo.ApplicationReleaseItems',N'U') IS NULL
  THROW 57600,'Application release catalog foundation is required.',1;
 IF COL_LENGTH(N'dbo.ApplicationReleases',N'Title') IS NULL
  ALTER TABLE dbo.ApplicationReleases ADD Title nvarchar(200) NULL;
 IF COL_LENGTH(N'dbo.ApplicationReleases',N'ReleaseDate') IS NULL
  ALTER TABLE dbo.ApplicationReleases ADD ReleaseDate date NULL;
 IF OBJECT_ID(N'dbo.GlobalControlPlaneAuditLog',N'U') IS NULL
 BEGIN
  CREATE TABLE dbo.GlobalControlPlaneAuditLog(
   Id bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_GlobalControlPlaneAuditLog PRIMARY KEY,
   OperatorId nvarchar(128) NOT NULL,
   EntityType varchar(64) NOT NULL,
   EntityId bigint NOT NULL,
   Action varchar(64) NOT NULL,
   OccurredAt datetime2(7) NOT NULL CONSTRAINT DF_GlobalControlPlaneAuditLog_OccurredAt DEFAULT(SYSUTCDATETIME()),
   Metadata nvarchar(1000) NULL
  );
 END;
 IF EXISTS(SELECT 1 FROM sys.security_predicates WHERE target_object_id IN(OBJECT_ID(N'dbo.ApplicationReleases'),OBJECT_ID(N'dbo.ApplicationReleaseItems'),OBJECT_ID(N'dbo.GlobalControlPlaneAuditLog')))
  THROW 57601,'Global release-control-plane tables must not have tenant RLS.',1;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
