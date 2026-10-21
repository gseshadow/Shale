-- CANDIDATE ONLY. Run from repository root with sqlcmd -b, reviewed variables, one connection.
:on error exit
DECLARE @Phase1RMode varchar(10)='APPLY';
:r docs/sql/phase1r/engine.sql
