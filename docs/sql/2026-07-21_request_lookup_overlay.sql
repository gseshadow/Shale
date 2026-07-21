/*
  Request lookup overlay compatibility migration.
  Idempotent/rerunnable. Run after 2026-07-21_case_materials_foundation_phase1.sql and before updated desktop builds.
  Keeps legacy MaterialRequests.RequestMethod and Status text columns for compatibility while introducing
  tenant/global administration tables and semantic SystemKeys for future FK-backed phases.
*/
IF OBJECT_ID(N'dbo.RequestMethods', N'U') IS NULL
CREATE TABLE dbo.RequestMethods (
    Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_RequestMethods PRIMARY KEY,
    ShaleClientId int NULL,
    SystemKey varchar(64) NULL,
    Name nvarchar(120) NOT NULL,
    SortOrder int NOT NULL CONSTRAINT DF_RequestMethods_SortOrder DEFAULT (0),
    IsActive bit NOT NULL CONSTRAINT DF_RequestMethods_IsActive DEFAULT (1),
    IsDeleted bit NOT NULL CONSTRAINT DF_RequestMethods_IsDeleted DEFAULT (0),
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_RequestMethods_CreatedAt DEFAULT (SYSUTCDATETIME()),
    UpdatedAt datetime2 NULL,
    RowVer rowversion NOT NULL
);
IF OBJECT_ID(N'dbo.RequestStatuses', N'U') IS NULL
CREATE TABLE dbo.RequestStatuses (
    Id int IDENTITY(1,1) NOT NULL CONSTRAINT PK_RequestStatuses PRIMARY KEY,
    ShaleClientId int NULL,
    SystemKey varchar(64) NULL,
    Name nvarchar(120) NOT NULL,
    Color varchar(32) NULL,
    SortOrder int NOT NULL CONSTRAINT DF_RequestStatuses_SortOrder DEFAULT (0),
    IsActive bit NOT NULL CONSTRAINT DF_RequestStatuses_IsActive DEFAULT (1),
    IsDeleted bit NOT NULL CONSTRAINT DF_RequestStatuses_IsDeleted DEFAULT (0),
    CreatedAt datetime2 NOT NULL CONSTRAINT DF_RequestStatuses_CreatedAt DEFAULT (SYSUTCDATETIME()),
    UpdatedAt datetime2 NULL,
    RowVer rowversion NOT NULL
);
DECLARE @methods TABLE(SystemKey varchar(64),Name nvarchar(120),SortOrder int);
INSERT @methods VALUES ('email','Email',10),('phone','Phone',20),('fax','Fax',30),('mail','Mail',40),('portal','Portal',50),('in_person','In Person',60),('other','Other',70);
MERGE dbo.RequestMethods AS t USING @methods AS s ON t.ShaleClientId IS NULL AND t.SystemKey=s.SystemKey
WHEN MATCHED THEN UPDATE SET Name=s.Name, SortOrder=s.SortOrder, IsActive=1, IsDeleted=0
WHEN NOT MATCHED THEN INSERT(ShaleClientId,SystemKey,Name,SortOrder) VALUES(NULL,s.SystemKey,s.Name,s.SortOrder);
DECLARE @statuses TABLE(SystemKey varchar(64),Name nvarchar(120),Color varchar(32),SortOrder int);
INSERT @statuses VALUES ('requested','Requested','#2563eb',10),('follow_up_due','Follow Up Due','#f97316',20),('partially_received','Partially Received','#8b5cf6',30),('fully_received','Fully Received','#16a34a',40),('closed','Closed','#64748b',50),('cancelled','Cancelled','#dc2626',60);
MERGE dbo.RequestStatuses AS t USING @statuses AS s ON t.ShaleClientId IS NULL AND t.SystemKey=s.SystemKey
WHEN MATCHED THEN UPDATE SET Name=s.Name, Color=s.Color, SortOrder=s.SortOrder, IsActive=1, IsDeleted=0
WHEN NOT MATCHED THEN INSERT(ShaleClientId,SystemKey,Name,Color,SortOrder) VALUES(NULL,s.SystemKey,s.Name,s.Color,s.SortOrder);
