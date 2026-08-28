/* Contacts Phase 3A sqlcmd same-session wrapper.
   Invoke only through sqlcmd with every variable supplied on the command line.
   This wrapper changes SESSION_CONTEXT only; it is read-only against user database objects. */
:On Error exit

SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=TRY_CONVERT(int,N'$(TenantId)'), @read_only=1;
EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=TRY_CONVERT(int,N'$(AdministratorUserId)'), @read_only=1;
EXEC sys.sp_set_session_context @key=N'Phase3AExpectedDatabase', @value=N'$(ExpectedDatabaseName)', @read_only=1;
EXEC sys.sp_set_session_context @key=N'Phase3AExpectedTenantId', @value=TRY_CONVERT(int,N'$(TenantId)'), @read_only=1;
EXEC sys.sp_set_session_context @key=N'Phase3AOperatorAcknowledgement', @value=TRY_CONVERT(bit,N'$(OperatorAcknowledgement)'), @read_only=1;
EXEC sys.sp_set_session_context @key=N'Phase3AApplicationBoundaryAcknowledgement', @value=TRY_CONVERT(bit,N'$(ApplicationBoundaryAcknowledgement)'), @read_only=1;
EXEC sys.sp_set_session_context @key=N'Phase3AMismatchIdCap', @value=TRY_CONVERT(int,N'$(MismatchIdCap)'), @read_only=1;

/* Metadata and identifiers only; no Contact data is projected. */
SELECT DB_NAME() AS DatabaseName,
       USER_NAME() AS DatabasePrincipal,
       TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId')) AS ShaleClientId,
       TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')) AS PrincipalUserId;
GO

/* :r is processed by sqlcmd on this connection. GO separates batches, not sessions. */
:r docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql
