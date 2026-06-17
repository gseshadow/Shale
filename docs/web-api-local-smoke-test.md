# Web/API Local Smoke Test for `shale-server`

This document lists the local commands for running `shale-server` in the temporary `dev` profile and manually verifying the read-only development API surface added during Step 3F.

These commands are for local development only. They do **not** add or exercise JWTs, browser cookies, Azure authentication, durable sessions, or write endpoints.

## Scope

The smoke test covers:

- `GET /api/health`
- `GET /api/dev/whoami`
- `GET /api/notifications/unread`
- `GET /api/cases/search?query=test`
- `GET /api/contacts/search?query=brian`
- `GET /api/cases/{caseId}`
- `GET /api/cases/{caseId}/tasks`

Only `/api/health` is expected to work without a resolved user/tenant context. Every DB-backed example below includes temporary development identity headers.

## Run from `shale-server`

Open Windows CMD and change to the server module directory:

```cmd
cd /d C:\Eclipse\Workspace\shale-parent\shale-server
```

Maven must be run from `shale-server` for the command below, or the reactor modules must already be installed in your local Maven repository.

## Required environment variables

Set all six database environment variables before starting Spring Boot. Use quoted Windows CMD `set` syntax so passwords and special characters are preserved correctly:

```cmd
set "SHALE_APP_JDBC_URL=jdbc:sqlserver://localhost:1433;databaseName=ShaleApp;encrypt=true;trustServerCertificate=true"
set "SHALE_APP_USER=shale_app"
set "SHALE_APP_PASS=your_app_password"
set "SHALE_RT_JDBC_URL=jdbc:sqlserver://localhost:1433;databaseName=ShaleRuntime;encrypt=true;trustServerCertificate=true"
set "SHALE_RT_USER=shale_runtime"
set "SHALE_RT_PASS=your_runtime_password"
```

Adjust the JDBC URLs, database names, users, and passwords for your local SQL Server environment.

## Start the server in the `dev` profile

From `C:\Eclipse\Workspace\shale-parent\shale-server`, run:

```cmd
mvn org.springframework.boot:spring-boot-maven-plugin:3.3.4:run -Dspring-boot.run.profiles=dev
```

Leave this process running while you execute the curl commands from another terminal.

## Temporary development identity headers

The DB-backed endpoints require these headers in the `dev` profile:

```http
X-Shale-UserId: 123
X-Shale-TenantId: 456
```

Replace `123` and `456` with a valid local user id and tenant/client id from your development database.

These headers are a temporary dev-only identity simulation. They are not authentication, are unsafe for production, and must not be used as a substitute for JWTs, cookies, Azure auth, or any real browser/mobile session mechanism.

In the default/non-`dev` Spring profile, DB-backed endpoints should remain blocked because the temporary header resolver is not active.

## Curl smoke-test commands

### Health check

`/api/health` does not require development identity headers:

```cmd
curl -i http://localhost:8080/api/health
```

Expected result: HTTP 200 with a JSON body similar to `{ "status": "ok" }`.

### Development principal check

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  http://localhost:8080/api/dev/whoami
```

Expected result: HTTP 200 with the resolved `userId` and `shaleClientId` when the `dev` profile is active and both header values are valid positive integers.

### Unread notifications

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  http://localhost:8080/api/notifications/unread
```

Expected result: HTTP 200 with a JSON array. An empty `[]` can be valid when the selected user has no unread notifications.

### Case search

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  "http://localhost:8080/api/cases/search?query=test"
```

Expected result: HTTP 200 with matching cases visible to the selected tenant/user context.

### Contact search

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  "http://localhost:8080/api/contacts/search?query=brian"
```

Expected result: HTTP 200 with matching contacts visible to the selected tenant/user context.

### Case detail

Replace `{caseId}` with a real case id visible to the selected tenant/user context:

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  http://localhost:8080/api/cases/{caseId}
```

Expected result: HTTP 200 with the case detail, or a not-found response if that case id is not visible in the selected context.

### Case tasks

Replace `{caseId}` with a real case id visible to the selected tenant/user context:

```cmd
curl -i ^
  -H "X-Shale-UserId: 123" ^
  -H "X-Shale-TenantId: 456" ^
  http://localhost:8080/api/cases/{caseId}/tasks
```

Expected result: HTTP 200 with a JSON array of tasks for that case. An empty array can be valid if the case has no tasks visible to the selected context.

## Default profile fail-closed check

If you start the server without `-Dspring-boot.run.profiles=dev`, DB-backed endpoints should remain blocked even if you send `X-Shale-UserId` and `X-Shale-TenantId`. The temporary header resolver is only active in the `dev` profile.

## Troubleshooting

### Maven cannot resolve Shale modules

Run Maven from:

```cmd
C:\Eclipse\Workspace\shale-parent\shale-server
```

If you run from a different directory, install the reactor modules first from the parent project, then retry the server command.

### Missing `SHALE_*` configuration errors

Confirm all required environment variables are set in the same CMD session that starts Spring Boot:

- `SHALE_APP_JDBC_URL`
- `SHALE_APP_USER`
- `SHALE_APP_PASS`
- `SHALE_RT_JDBC_URL`
- `SHALE_RT_USER`
- `SHALE_RT_PASS`

### Passwords with special characters fail in CMD

Use quoted CMD syntax exactly:

```cmd
set "KEY=value"
```

This prevents characters such as `&`, `^`, `|`, `<`, `>`, and spaces from being interpreted by CMD.

### `Login failed for user 'shale_runtime'`

`SHALE_RT_PASS` is wrong or stale for the `shale_runtime` SQL login. Reset or retrieve the current runtime password and re-run:

```cmd
set "SHALE_RT_PASS=your_runtime_password"
```

Then restart Spring Boot.

### Unread notifications returns `[]`

An empty unread-notifications array can be valid. It means the selected development user has no unread notifications visible under the selected tenant/client context.
