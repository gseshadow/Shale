/* SYNTHETIC ONLY. Not an authoritative schema export or a production installer. */
CREATE TABLE dbo.ShaleClients(Id int PRIMARY KEY,IsActive bit NOT NULL);
CREATE TABLE dbo.Users(id int PRIMARY KEY,ShaleClientId int NULL,is_admin bit NULL,is_deleted bit NULL,IsRemoved bit NULL);
CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,id);
CREATE TABLE dbo.EntityActionAuditLog(
 Id bigint IDENTITY PRIMARY KEY,ShaleClientId int NOT NULL,ActorUserId int NOT NULL,
 EntityType varchar(64) NOT NULL CONSTRAINT CK_SyntheticAudit_EntityType CHECK(EntityType IN('TASK_POLICY_CONFIGURATION','USER','LINK_TYPE','CASE_LINK','CASE_LINK_SHARE')),
 EntityId bigint NOT NULL,Action varchar(64) NOT NULL CHECK(Action IN('CREATED','UPDATED','DEACTIVATED')),
 OccurredAt datetime2(7) NOT NULL DEFAULT(SYSUTCDATETIME()),ParentEntityType varchar(64) NULL,
 ParentEntityId bigint NULL,CorrelationId uniqueidentifier NULL,Source varchar(64) NULL,Metadata nvarchar(1000) NULL);
CREATE TABLE dbo.GlobalControlPlaneAuditLog(Id bigint IDENTITY PRIMARY KEY,OperatorId nvarchar(128) NOT NULL,
 EntityType varchar(64) NOT NULL,EntityId bigint NOT NULL,Action varchar(64) NOT NULL,
 OccurredAt datetime2(7) NOT NULL DEFAULT(SYSUTCDATETIME()),Metadata nvarchar(1000) NULL);
CREATE ROLE ShaleAdmins;
EXEC(N'CREATE SCHEMA sec AUTHORIZATION dbo');
GO
/* Same synthetic strict predicate used by the existing Phase1RCandidateFixture.
   Its dbo exception is setup/module authority, never runtime-security evidence. */
CREATE FUNCTION sec.fn_FilterByTenant(@Tenant int) RETURNS TABLE WITH SCHEMABINDING AS
RETURN SELECT 1 AS AccessResult WHERE USER_NAME()=N'dbo' OR IS_MEMBER(N'ShaleAdmins')=1
 OR @Tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'));
GO
CREATE SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users WITH(STATE=ON,SCHEMABINDING=ON);
INSERT dbo.ShaleClients VALUES(7,1),(8,1);
INSERT dbo.Users VALUES(71,7,1,0,0),(72,7,0,0,0),(73,7,0,0,0),(74,7,0,0,0),(75,7,0,0,0),(76,7,0,0,0),(77,7,0,0,0),(78,7,0,0,0),(79,7,0,0,0),(81,8,1,0,0),(82,8,0,0,0);
GO
