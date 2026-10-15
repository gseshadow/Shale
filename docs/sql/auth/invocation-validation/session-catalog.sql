SELECT s.event_session_id AS SessionId,
 CONVERT(varchar(64),HASHBYTES('SHA2_256',x.ConfigXml),2) AS ConfigHash,
 x.ConfigXml
FROM sys.database_event_sessions s
CROSS APPLY (SELECT CONVERT(nvarchar(max),(
 SELECT s.*,
 (SELECT e.* FROM sys.database_event_session_events e WHERE e.event_session_id=s.event_session_id
  ORDER BY e.event_id FOR XML PATH('event'),TYPE),
 (SELECT a.* FROM sys.database_event_session_actions a WHERE a.event_session_id=s.event_session_id
  ORDER BY a.event_id,a.name FOR XML PATH('action'),TYPE),
 (SELECT f.* FROM sys.database_event_session_fields f WHERE f.event_session_id=s.event_session_id
  ORDER BY f.object_id,f.name FOR XML PATH('field'),TYPE),
 (SELECT t.* FROM sys.database_event_session_targets t WHERE t.event_session_id=s.event_session_id
  ORDER BY t.target_id FOR XML PATH('target'),TYPE)
 FOR XML PATH('session'),TYPE
)) AS ConfigXml) x
WHERE s.name=N'{{session}}';
