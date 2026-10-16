:ON ERROR EXIT
:r docs/sql/auth/permission-cutover/legacy-retirement-context.sql
:r docs/sql/auth/permission-cutover/controlled-legacy-approval.sql
-- Preserve the reviewed trial packet and explicit rollback approval. Never expire recovery at window end.
:r docs/sql/auth/permission-cutover/legacy-retirement-rollback-body.sql
