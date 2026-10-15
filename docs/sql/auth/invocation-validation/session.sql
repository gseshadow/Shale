-- Diagnostic synthetic session only. Never adapt by substituting live module IDs.
-- Source fields, including intrinsic statement, must be reviewed in RAW payloads.
CREATE EVENT SESSION [{{session}}] ON DATABASE
ADD EVENT sqlserver.module_start(SET collect_statement=(0)
 WHERE ([source_database_id]=({{dbid}}) AND
 ([object_id]=({{a}}) OR [object_id]=({{b}}) OR [object_id]=({{c}})))),
ADD EVENT sqlserver.module_end(SET collect_statement=(0)
 WHERE ([source_database_id]=({{dbid}}) AND
 ([object_id]=({{a}}) OR [object_id]=({{b}}) OR [object_id]=({{c}}))))
ADD TARGET package0.ring_buffer(SET max_memory=(1024),max_events_limit=({{limit}}))
WITH (MAX_MEMORY={{buffer}} KB,MAX_DISPATCH_LATENCY=1 SECONDS,
 EVENT_RETENTION_MODE=ALLOW_SINGLE_EVENT_LOSS,TRACK_CAUSALITY=OFF,STARTUP_STATE=OFF);
