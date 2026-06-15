# Tenant Architecture

All business data is tenant scoped.

Primary tenant key:
- ShaleClientId

Database access:
- Runtime connections use shale_runtime.
- SESSION_CONTEXT('ShaleClientId') must be set before querying tenant protected tables.

Pattern:

EXEC sys.sp_set_session_context
    @key=N'ShaleClientId',
    @value=@TenantId,
    @read_only=1

RLS protected tables:
- Cases
- Contacts
- Organizations
- Users
- Tasks
- Roles
- Statuses
- Categories
- Priorities

Rules:

- Never bypass tenant filtering.
- Never remove RLS predicates.
- Never query tenant data without an initialized RuntimeSessionService.
- All server requests must resolve a tenant before opening DB connections.