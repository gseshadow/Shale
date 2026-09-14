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
5. Choose one of the two same-session execution methods below. `SESSION_CONTEXT` is connection-scoped: values set in
   SSMS, Azure Data Studio, or one `sqlcmd` process are **not** inherited by another connection or process.

## Method A: interactive same-session execution

1. Open exactly one query connection to the copy database. If it does not already carry an immutable context, run:

   ```sql
   EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=7, @read_only=1;
   EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=<approved active admin user id>, @read_only=1;
   SELECT DB_NAME(), SESSION_CONTEXT(N'ShaleClientId'), SESSION_CONTEXT(N'PrincipalUserId'), USER_NAME();
   ```

   Never clear or replace an existing context. Instead, open a new connection. In
   `docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql`, set the expected database,
   expected tenant (7 by default), both acknowledgements to `1`, and the desired mismatch-ID cap.
2. Verify that `DB_NAME()`, `USER_NAME()`, and both context values are correct using the `SELECT` above.
3. Execute the entire authoritative audit in that **exact query connection**. Do not close the window, reconnect, or
   launch a separate `sqlcmd` process between the preamble and audit. `GO` separators are safe because they retain the
   connection.

## Method B: sqlcmd same-session wrapper

From the repository root, invoke the committed wrapper. One `sqlcmd` process connects once, sets immutable context,
verifies it, and uses `:r` to include—not duplicate—the authoritative audit on the same connection:

   ```bash
   sqlcmd -S "$SHALE_COPY_DB_HOST" -d "$SHALE_COPY_DB_NAME" -G -b \
     -v ExpectedDatabaseName="$SHALE_COPY_DB_NAME" \
        TenantId="7" \
        AdministratorUserId="<approved-active-admin-user-id>" \
        OperatorAcknowledgement="1" \
        ApplicationBoundaryAcknowledgement="1" \
        MismatchIdCap="100" \
     -i docs/sql/verification/2026-08-28_contacts_phase3a_sqlcmd_wrapper.sql \
     -o contacts-phase3a-copy-phi-safe-results.txt
   ```

The placeholder administrator ID must be replaced at execution time; no credential or permanent administrator ID is
stored in the repository. `-b` and the wrapper's `:On Error exit` make `sqlcmd` return nonzero when validation or the
included audit throws. The wrapper sets audit parameters in immutable session context, but the included audit still
independently validates database, tenant, principal, acknowledgements, schema, RLS, dependencies, and findings.

## Evidence and progression

7. Preserve only the seven PHI-safe audit result sets plus the wrapper's database/principal/context verification:
   tenant and principal IDs, Contact IDs, category/count metadata, database dependency
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
