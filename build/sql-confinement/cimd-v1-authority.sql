/* Inserted into the live loader after capability/tenant locks and before legacy authority rows.
   Publication holds its row through commit; decisions serialize on the existing tenant lock.
   Registry publication never waits for a tenant, so this adds no reverse lock edge. */
DECLARE @application uniqueidentifier,@approval uniqueidentifier,@cimdHeld bigint;
SELECT @application=ApplicationId,@approval=ApprovalId FROM dbo.AiCimdFamilyV1
 WHERE ShaleClientId=@t AND SessionId=@SessionId AND UserId=@u;
SELECT @cimdHeld=Revision FROM dbo.AiExternalApplication WITH(HOLDLOCK) WHERE ApplicationId=@application;
SELECT @cimdHeld=Id FROM dbo.AiTenantApplicationApproval WITH(HOLDLOCK) WHERE ShaleClientId=@t AND ApprovalId=@approval;
SELECT @cimdHeld=UserId FROM dbo.AiCimdFamilyV1 WITH(HOLDLOCK) WHERE ShaleClientId=@t AND SessionId=@SessionId;
