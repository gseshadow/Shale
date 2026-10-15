:ON ERROR EXIT
/* Separately reviewed read-only fallback, NOT a complete invocation monitor.
   Existing cached counters only. No event session, text/plan lookup or application rows.
   ExpectedDatabase is a trusted fixed operator literal. See owning runbook. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF DB_NAME()<>N'$(ExpectedDatabase)' OR USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0
 THROW 58350,N'Expected database, dbo metadata visibility and no ambient transaction required.',1;
-- Azure SQL Database follows UTC. Local SQL validation is supported only on a UTC engine.
IF DATEPART(TZOFFSET,SYSDATETIMEOFFSET())<>0
 THROW 58350,N'UTC engine required; do not relabel local cache timestamps as UTC.',1;
DECLARE @Targets TABLE(ModuleName sysname NOT NULL, ObjectId int NULL);
INSERT @Targets VALUES
 (N'dbo.sp_login_get_hash',OBJECT_ID(N'dbo.sp_login_get_hash',N'P')),
 (N'dbo.sp_login_mark_failure',OBJECT_ID(N'dbo.sp_login_mark_failure',N'P')),
 (N'dbo.sp_login_mark_success',OBJECT_ID(N'dbo.sp_login_mark_success',N'P'));
IF (SELECT COUNT(DISTINCT ObjectId) FROM @Targets)<>3
 THROW 58351,N'All three distinct reviewed procedure identities must be visible; missing is unknown.',1;
-- Snapshot only minimum cache identity/timing/count metadata; never materialize handles or text.
DECLARE @Snapshot TABLE(ModuleName sysname, ObjectId int, CachedPlanCount bigint,
 RetainedExecutionCount bigint, EarliestCachedUtc datetime, LatestRetainedExecutionUtc datetime);
INSERT @Snapshot
SELECT t.ModuleName,t.ObjectId,COUNT_BIG(s.object_id),SUM(s.execution_count),
 MIN(s.cached_time),MAX(s.last_execution_time)
FROM @Targets t LEFT JOIN sys.dm_exec_procedure_stats s
 ON s.database_id=DB_ID() AND s.object_id=t.ObjectId AND s.type=N'P'
GROUP BY t.ModuleName,t.ObjectId;
DECLARE @SampleUtc datetime2(7)=SYSUTCDATETIME();
SELECT CONVERT(varchar(27),@SampleUtc,126)+N'Z' AS SampleUtc,
 ModuleName,ObjectId,CachedPlanCount,RetainedExecutionCount,
 CONVERT(varchar(23),EarliestCachedUtc,126)+N'Z' AS EarliestCachedUtc,
 CONVERT(varchar(23),LatestRetainedExecutionUtc,126)+N'Z' AS LatestRetainedExecutionUtc,
 N'INCOMPLETE_CACHE_OBSERVATION' AS Coverage
FROM @Snapshot ORDER BY ModuleName;
-- NULL retained counts/times mean unavailable cache evidence, never zero invocations.
GO
