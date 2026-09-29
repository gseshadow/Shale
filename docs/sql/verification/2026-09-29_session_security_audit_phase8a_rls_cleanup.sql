/* Run as dbo only if the Phase 8A live RLS verifier stopped before its normal cleanup. */
EXEC sys.sp_set_session_context
    @key = N'ShaleClientId',
    @value = NULL;

DELETE dbo.SessionSecurityAuditLog
WHERE TargetSessionId IN (
        '8A000000-0000-4000-8000-000000000007',
        '8A000000-0000-4000-8000-000000000008')
  AND EventType = 'SELF_REVOKE'
  AND ReasonCode = 'USER_REVOKED';

IF USER_ID(N'Phase8ARlsVerifier') IS NOT NULL
BEGIN
    DROP USER [Phase8ARlsVerifier];
END;
