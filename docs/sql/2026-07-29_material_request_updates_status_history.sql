SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.MaterialRequestUpdates', N'StatusSystemKey') IS NULL
    ALTER TABLE dbo.MaterialRequestUpdates ADD StatusSystemKey nvarchar(120) NULL;

IF COL_LENGTH(N'dbo.MaterialRequestUpdates', N'StatusDisplayValue') IS NULL
    ALTER TABLE dbo.MaterialRequestUpdates ADD StatusDisplayValue nvarchar(120) NULL;

/*
 Existing update rows do not reliably preserve stable status identity. Seed exactly
 one conservative current occurrence per existing request and do not invent prior
 transitions. New creates and real transitions populate these columns transactionally.
*/
INSERT dbo.MaterialRequestUpdates
    (ShaleClientId, CaseId, MaterialRequestId, UpdateType, FieldKey, Body,
     ActorUserId, CreatedAt, StatusSystemKey, StatusDisplayValue)
SELECT mr.ShaleClientId, mr.CaseId, mr.Id, N'SYSTEM_EVENT', N'STATUS_INITIAL',
       N'Current status recorded when status history was introduced.',
       mr.CreatedByUserId, COALESCE(mr.UpdatedAt, mr.CreatedAt), rs.SystemKey, COALESCE(rs.Name, mr.Status)
FROM dbo.MaterialRequests mr
OUTER APPLY (
    SELECT TOP (1) r.SystemKey, r.Name
    FROM dbo.RequestStatuses r
    WHERE (r.ShaleClientId = mr.ShaleClientId OR r.ShaleClientId IS NULL)
      AND (LOWER(LTRIM(RTRIM(r.SystemKey))) = LOWER(LTRIM(RTRIM(mr.Status)))
        OR LOWER(LTRIM(RTRIM(r.Name))) = LOWER(LTRIM(RTRIM(mr.Status))))
    ORDER BY CASE WHEN r.ShaleClientId = mr.ShaleClientId THEN 0 ELSE 1 END, r.Id
) rs
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.MaterialRequestUpdates mu
    WHERE mu.ShaleClientId = mr.ShaleClientId
      AND mu.MaterialRequestId = mr.Id
      AND mu.StatusDisplayValue IS NOT NULL
);

COMMIT TRANSACTION;
