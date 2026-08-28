# Contacts Phase 3A readiness audit runbook

Phase 3A is a read-only deployment gate. It performs **no repair**. A failure never authorizes an automatic data
mutation. Keep `dbo.Contacts.DisplayName`; it is not one of the ten retirement targets.

## Copy rehearsal

1. From a clean checkout of the candidate application, run `mvn test` (or the three module commands in the release
   checklist) and retain the build log.
2. Restore or refresh a production database backup into an access-controlled, non-production SQL Server copy.
3. Point the normal application/JDBC environment variables at that copy (database host, database name, credentials,
   and tenant configuration). Confirm the database name before starting the application.
4. Launch the application normally and smoke-test Contact directory, detail, edit, classification, credential, and
   Case-party flows against the copy. Do not use the audit as a substitute for this smoke test.
5. Open one SQL client connection to the copy. If that connection does not already carry an immutable context, run:

   ```sql
   EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=7, @read_only=1;
   EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=<approved active admin user id>, @read_only=1;
   SELECT DB_NAME(), SESSION_CONTEXT(N'ShaleClientId'), SESSION_CONTEXT(N'PrincipalUserId'), USER_NAME();
   ```

   Never clear or replace an existing context. Instead, open a new connection. In
   `docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql`, set the expected database,
   expected tenant (7 by default), acknowledgement to `1`, and desired mismatch-ID cap.
6. In that **same SQL session**, run the unchanged audit file with SQLCMD mode, for example:

   ```bash
   sqlcmd -S "$SHALE_COPY_DB_HOST" -d "$SHALE_COPY_DB_NAME" -G -b \
     -i docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql \
     -o contacts-phase3a-copy-phi-safe-results.txt
   ```

7. Preserve only the seven PHI-safe result sets: tenant, Contact IDs, category/count metadata, database dependency
   metadata, and final status. Treat even Contact IDs as controlled operational output.
8. If the status is `FAIL_NOT_READY`, diagnose it and propose any repair as a separate, reviewed change with its own
   backup, validation, and rollback plan. A failed audit does not authorize mutation.
9. Refresh the copy if appropriate and repeat the smoke test and unchanged audit until it reports
   `PASS_READY_FOR_PHASE_3B`.
10. During the production approval window, establish the corresponding tenant context and run the exact same
    committed audit against production. Preserve its PHI-safe summary with the release evidence.

A passing copy does **not** authorize a future retirement script. Before production retirement, Phase 3B must ship a
no-legacy-write application version and every user/process must deploy and adopt it. Only after an adoption window,
production Phase 3A PASS, dependency review, backups, and explicit approval may a separately reviewed final migration
drop the nine scalar contact-point columns and `IsExpert`. That final migration must again preserve `DisplayName`.
