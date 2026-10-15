-- Tokens are replaced only by harness.ps1 with a generated 32-hex task token.
-- No application schema, data, users, grants, policies or legacy definitions.
SET XACT_ABORT ON;
IF EXISTS (SELECT 1 FROM sys.objects WHERE is_ms_shipped=0)
 OR EXISTS (SELECT 1 FROM sys.database_event_sessions)
 OR SCHEMA_ID(N'{{schema}}') IS NOT NULL
 THROW 59010,'Dedicated database must be empty; refuse adoption',1;
BEGIN TRANSACTION;
EXEC(N'CREATE SCHEMA [{{schema}}] AUTHORIZATION dbo');
EXEC sys.sp_addextendedproperty @name=N'InvocationValidationOwner',@value=N'{{token}}',
 @level0type=N'SCHEMA',@level0name=N'{{schema}}';
EXEC(N'CREATE PROCEDURE [{{schema}}].[probe_a] @input nvarchar(4000),@mode int=0 AS
BEGIN SET NOCOUNT ON; IF @mode=1 THROW 59011,''SYNTHETIC_FAILURE'',1;
IF @mode=2 WAITFOR DELAY ''00:00:30'';
IF @mode=3 WAITFOR DELAY ''00:00:01''; END');
EXEC(N'CREATE PROCEDURE [{{schema}}].[probe_b] @input nvarchar(4000),@mode int=0 AS
BEGIN SET NOCOUNT ON; IF @mode=1 THROW 59011,''SYNTHETIC_FAILURE'',1;
IF @mode=2 WAITFOR DELAY ''00:00:30'';
IF @mode=3 WAITFOR DELAY ''00:00:01''; END');
EXEC(N'CREATE PROCEDURE [{{schema}}].[probe_c] @input nvarchar(4000),@mode int=0 AS
BEGIN SET NOCOUNT ON; IF @mode=1 THROW 59011,''SYNTHETIC_FAILURE'',1;
IF @mode=2 WAITFOR DELAY ''00:00:30'';
IF @mode=3 WAITFOR DELAY ''00:00:01''; END');
EXEC(N'CREATE PROCEDURE [{{schema}}].[wrapper] @input nvarchar(4000) AS
BEGIN EXEC [{{schema}}].[probe_a] @input; EXEC [{{schema}}].[probe_b] @input;
EXEC [{{schema}}].[probe_c] @input; END');
EXEC(N'CREATE PROCEDURE [{{schema}}].[unrelated] @input nvarchar(4000) AS RETURN 0');
EXEC(N'CREATE PROCEDURE [{{schema}}].[probe_a_decoy] @input nvarchar(4000) AS RETURN 0');
COMMIT;
