-- Entire target serialization, never .nodes() projection or post-capture redaction.
SELECT CONVERT(nvarchar(33),SYSUTCDATETIME(),126) AS CollectedUtc,
 s.name AS SessionName, s.create_time AS StartedAt,
 s.dropped_event_count AS DroppedEvents, s.dropped_buffer_count AS DroppedBuffers,
 s.largest_event_dropped_size AS LargestDroppedEvent,
 s.buffer_policy_desc AS BufferPolicy, s.total_regular_buffers AS RegularBuffers,
 s.regular_buffer_size AS BufferSize, s.total_large_buffers AS LargeBuffers,
 s.large_buffer_size AS LargeBufferSize,
 t.target_name AS TargetName, t.execution_count AS TargetExecutions,
 t.execution_duration_ms AS TargetDurationMs, t.failed_buffer_count AS FailedTargetBuffers,
 s.pending_buffers AS PendingBuffers, s.buffer_full_count AS FullBuffers,
 s.buffer_processed_count AS ProcessedBuffers, s.total_target_memory AS TargetMemory,
 CONVERT(nvarchar(max),t.target_data) AS RawTarget
FROM sys.dm_xe_database_sessions AS s
JOIN sys.dm_xe_database_session_targets AS t ON t.event_session_address=s.address
WHERE s.name=N'{{session}}';
