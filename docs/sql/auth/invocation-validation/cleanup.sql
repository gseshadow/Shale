-- Called only after exact schema catalog/owner and stopped-session verification.
-- Transaction rolls back on extra dependencies or any unexpected resource.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DROP PROCEDURE [{{schema}}].[wrapper];
DROP PROCEDURE [{{schema}}].[probe_a_decoy];
DROP PROCEDURE [{{schema}}].[unrelated];
DROP PROCEDURE [{{schema}}].[probe_a];
DROP PROCEDURE [{{schema}}].[probe_b];
DROP PROCEDURE [{{schema}}].[probe_c];
EXEC sys.sp_dropextendedproperty @name=N'InvocationValidationOwner',
 @level0type=N'SCHEMA',@level0name=N'{{schema}}';
DROP SCHEMA [{{schema}}];
COMMIT;
