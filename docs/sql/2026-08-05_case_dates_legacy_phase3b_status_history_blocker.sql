/*
  SESSION-SCOPED, NON-AUTHORITATIVE BLOCKER REPORT: no
  AcceptedDate/DeniedDate/ClosedDate status-history insert is generated.
  @TenantId = NULL does not prove all-tenant visibility; RLS and the connection's
  SESSION_CONTEXT still determine visible rows. Do not use this standalone report
  as evidence of authoritative all-tenant completion.
  dbo.CaseStatuses records current/past status rows with CaseId, StatusId, EffectiveDate,
  EndDate, Notes, CreatedAt, UpdatedAt, and IsPrimary. It does not store previous status,
  actor, historical SystemKey/display label, tenant id, or explicit migration provenance.
  Legacy date columns are SQL date-only fields and current writers populate the first null
  lifecycle date with CAST(SYSDATETIME() AS date). Direct SQL would fabricate missing
  transition metadata and could not preserve historical labels or actors.
*/
SET NOCOUNT ON;
DECLARE @TenantId int = NULL;
IF OBJECT_ID(N'dbo.CaseStatuses', N'U') IS NULL THROW 56300, 'Missing dbo.CaseStatuses.', 1;
SELECT N'SESSION_SCOPED_NON_AUTHORITATIVE' VisibilityScope,CONVERT(nvarchar(128),SESSION_CONTEXT(N'ShaleClientId')) SessionContextShaleClientId,v.FieldName,c.ShaleClientId,COUNT_BIG(*) LegacyRows,SUM(CASE WHEN h.MatchCount=0 THEN 1 ELSE 0 END) MissingSameDateEvidence,SUM(CASE WHEN h.MatchCount>1 THEN 1 ELSE 0 END) MultipleSameDateEvidence
FROM dbo.Cases c CROSS APPLY (VALUES ('AcceptedDate','accepted',c.AcceptedDate),('DeniedDate','denied',c.DeniedDate),('ClosedDate','closed',c.ClosedDate)) v(FieldName,LifecycleKey,LegacyDate)
CROSS APPLY (SELECT COUNT_BIG(*) MatchCount
             FROM dbo.CaseStatuses cs
             JOIN dbo.Statuses s ON s.Id=cs.StatusId
                                AND (s.ShaleClientId=c.ShaleClientId OR s.ShaleClientId IS NULL)
             WHERE cs.CaseId=c.Id
               AND CAST(cs.EffectiveDate AS date)=v.LegacyDate
               AND (s.LifecycleKey=v.LifecycleKey OR s.SystemKey=v.LifecycleKey)) h
WHERE v.LegacyDate IS NOT NULL AND (@TenantId IS NULL OR c.ShaleClientId=@TenantId) GROUP BY v.FieldName,c.ShaleClientId;
