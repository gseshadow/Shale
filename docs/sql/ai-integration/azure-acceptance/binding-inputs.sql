/* Destination-specific nonsecret proposal. Copy only into private packet; no authorization.
   The origin is operator-supplied, not evidence of routing, deployment or a running JVM.
   Preserve NULL zones/zero approvals until actual test-worker evidence is reviewed. */
DECLARE @BindServer nvarchar(max)=N'shale', @BindDatabase nvarchar(max)=N'ShaleMcpAcceptance';
DECLARE @BindOrigin nvarchar(max)=N'https://shale-mcp-acceptance.azurewebsites.net';
DECLARE @BindIssuer nvarchar(max)=N'https://shale-mcp-acceptance.azurewebsites.net/oauth/mcp';
DECLARE @BindResource nvarchar(max)=N'https://shale-mcp-acceptance.azurewebsites.net/mcp';
DECLARE @BindJavaZone nvarchar(max)=NULL, @BindSqlZone nvarchar(max)=NULL;
DECLARE @BindApproved bit=0;
DECLARE @BindOffConfirmed bit=0;
DECLARE @BindEvidenceConfirmed bit=0;
DECLARE @BindWallTimes table(UtcSample datetime2(7) PRIMARY KEY, JavaWallTime datetime2(7) NOT NULL);
/* Paste the existing BindingTimeZoneProbe's 28 rows from the approved test worker here.
   Do not infer UTC, copy production samples, change the JVM zone, or fill approval bits now. */
