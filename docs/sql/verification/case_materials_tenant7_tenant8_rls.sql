/* Case Materials tenant 7 / tenant 8 RLS verification. */
PRINT 'Run on a connection where SESSION_CONTEXT(N''ShaleClientId'') was not set read_only.';
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 7;
SELECT N'Tenant 7 should see zero tenant 8 requests' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequests WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 follow-ups' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequestFollowUps WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see zero tenant 8 items' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialItems WHERE ShaleClientId = 8;
SELECT N'Tenant 7 should see global MaterialTypes' AS CheckName, COUNT(*) AS GlobalRows FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL;
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = 8;
SELECT N'Tenant 8 should see zero tenant 7 requests' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequests WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 follow-ups' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialRequestFollowUps WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see zero tenant 7 items' AS CheckName, COUNT(*) AS ExpectedZero FROM dbo.MaterialItems WHERE ShaleClientId = 7;
SELECT N'Tenant 8 should see global MaterialTypes' AS CheckName, COUNT(*) AS GlobalRows FROM dbo.MaterialTypes WHERE ShaleClientId IS NULL;
EXEC sys.sp_set_session_context @key = N'ShaleClientId', @value = NULL;
