/* SYNTHETIC shape for exercising existing minimized worker. Not authoritative Case SQL acceptance. */
ALTER TABLE dbo.Users ADD name_first nvarchar(100) NULL,name_last nvarchar(100) NULL,color varchar(7) NULL;
CREATE TABLE dbo.Cases(Id int PRIMARY KEY,ShaleClientId int NOT NULL,CaseNumber nvarchar(200),Name nvarchar(255),PracticeAreaId int NULL,IsDeleted bit NOT NULL,UpdatedAt datetime2 NULL);
CREATE TABLE dbo.PracticeAreas(Id int PRIMARY KEY,ShaleClientId int NULL,Name nvarchar(255));
CREATE TABLE dbo.Statuses(Id int PRIMARY KEY,ShaleClientId int NULL,SystemKey varchar(50),LifecycleKey varchar(50),Name nvarchar(255),Color varchar(7),SortOrder int);
CREATE TABLE dbo.CaseStatuses(Id int PRIMARY KEY,ShaleClientId int NOT NULL,CaseId int,StatusId int,EndDate datetime2 NULL,IsPrimary bit,EffectiveDate datetime2,UpdatedAt datetime2,CreatedAt datetime2);
CREATE TABLE dbo.CaseUsers(Id int PRIMARY KEY,ShaleClientId int NOT NULL,CaseId int,UserId int,RoleId int,IsPrimary bit,UpdatedAt datetime2,CreatedAt datetime2);
CREATE TABLE dbo.AuditLog(Id bigint IDENTITY PRIMARY KEY,ShaleClientId int NOT NULL,UserId int,ObjectTypeId int,ObjectId bigint,FieldName varchar(64),FieldCode int,StringValue nvarchar(500),DateValue date,BooleanValue bit,IntValue int,EntryDate datetime2);
GO
ALTER SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Cases,
ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseStatuses,
ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseUsers,
ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.AuditLog;
GO
GRANT SELECT ON dbo.Cases TO AiIntegrationRuntime;
GRANT SELECT ON dbo.CaseStatuses TO AiIntegrationRuntime;
GRANT SELECT ON dbo.CaseUsers TO AiIntegrationRuntime;
GRANT SELECT ON dbo.Statuses TO AiIntegrationRuntime;
GRANT SELECT ON dbo.PracticeAreas TO AiIntegrationRuntime;
GRANT SELECT ON dbo.Users TO AiIntegrationRuntime;
GRANT INSERT ON dbo.AuditLog TO AiIntegrationRuntime;
INSERT dbo.Cases VALUES(100,7,N'SECRET_NUMBER',N'SECRET_CASE patient client',NULL,0,NULL),(200,8,N'FOREIGN',N'SECRET_CASE patient foreign',NULL,0,NULL),(101,7,N'DELETED',N'SECRET_CASE patient deleted',NULL,1,NULL);
