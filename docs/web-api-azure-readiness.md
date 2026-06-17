# Shale Server Azure App Service Readiness

This document covers Step 4A deployment/configuration readiness for `shale-server`.

## Profiles

Use an explicit Spring profile when starting the server:

- `dev` or `local` for local development. These profiles enable the temporary development identity headers `X-Shale-UserId` and `X-Shale-TenantId`.
- `prod` or `azure` for deployed environments. These profiles reject development identity headers and DB-backed API routes fail closed until real authentication is implemented.

Do not run Azure App Service with `dev` or `local` enabled.

## Required database environment variables

The server accepts the Azure-ready names below and preserves the existing desktop/local aliases.

| Purpose | Preferred variable | Existing alias |
| --- | --- | --- |
| App/auth JDBC URL | `SHALE_APP_DB_URL` | `SHALE_APP_JDBC_URL` |
| App/auth SQL user | `SHALE_APP_DB_USER` | `SHALE_APP_USER` |
| App/auth SQL password | `SHALE_APP_DB_PASSWORD` | `SHALE_APP_PASS` |
| Runtime JDBC URL | `SHALE_RT_DB_URL` | `SHALE_RT_JDBC_URL` |
| Runtime SQL user | `SHALE_RT_DB_USER` | `SHALE_RT_USER` |
| Runtime SQL password | `SHALE_RT_DB_PASSWORD` | `SHALE_RT_PASS` |

Optional database tuning variables:

- `DB_MAX_POOL_SIZE` defaults to `10`.
- `DB_CONNECTION_TIMEOUT_MS` defaults to `10000`, while the connection pool enforces a minimum 30 second timeout.

Startup failures identify missing variables by name, for example `Missing required config: SHALE_APP_DB_URL or SHALE_APP_JDBC_URL`.

## CORS configuration

CORS is disabled unless an allowed-origin list is configured. Set one of:

- `SHALE_ALLOWED_CORS_ORIGINS`
- `SHALE_SERVER_ALLOWED_CORS_ORIGINS`

Use a comma-separated list, for example:

```bash
SHALE_ALLOWED_CORS_ORIGINS=https://app.example.com,https://staging.example.com
```

## Port binding

`shale-server` respects Azure's `PORT` environment variable. If `PORT` is not set, it defaults to `8080`.

## Local dev startup with environment variables

```bash
export SPRING_PROFILES_ACTIVE=dev
export SHALE_APP_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=ShaleApp;encrypt=true;trustServerCertificate=true'
export SHALE_APP_DB_USER='shale_app'
export SHALE_APP_DB_PASSWORD='your_app_password'
export SHALE_RT_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=ShaleRuntime;encrypt=true;trustServerCertificate=true'
export SHALE_RT_DB_USER='shale_runtime'
export SHALE_RT_DB_PASSWORD='your_runtime_password'
mvn -pl shale-server -am spring-boot:run
```

The existing `SHALE_APP_JDBC_URL`, `SHALE_APP_USER`, `SHALE_APP_PASS`, `SHALE_RT_JDBC_URL`, `SHALE_RT_USER`, and `SHALE_RT_PASS` names also continue to work.

## Azure App Service configuration notes

Configure App Service application settings with:

- `SPRING_PROFILES_ACTIVE=azure`
- `SHALE_APP_DB_URL`
- `SHALE_APP_DB_USER`
- `SHALE_APP_DB_PASSWORD`
- `SHALE_RT_DB_URL`
- `SHALE_RT_DB_USER`
- `SHALE_RT_DB_PASSWORD`
- `SHALE_ALLOWED_CORS_ORIGINS` when a browser client origin needs API access

Do not configure `X-Shale-UserId`, `X-Shale-TenantId`, real user ids, tenant ids, or secrets in source code. Passwords should live only in App Service settings or a future managed secret store.

## Health endpoints

- `GET /api/health` is a process liveness check and does not require database access.
- `GET /api/health/db` checks database connectivity and returns only safe status messages. It does not expose JDBC URLs, users, passwords, hosts, or tenant identifiers.

## Safe smoke-test checklist

### Dev/local profile

```bash
curl -i http://localhost:8080/api/health
curl -i http://localhost:8080/api/health/db
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' http://localhost:8080/api/dev/whoami
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' 'http://localhost:8080/api/cases/search?query=test'
curl -i 'http://localhost:8080/api/cases/search?query=test'
```

Expected behavior:

- `/api/health` returns `200` with `status=ok`.
- `/api/health/db` returns `200` when DB connectivity is valid or `503` with a safe diagnostic otherwise.
- `/api/dev/whoami` works only with valid development headers.
- DB-backed endpoints work with valid development headers and fail closed without them.

### Prod/azure profile

```bash
curl -i http://localhost:8080/api/health
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' http://localhost:8080/api/dev/whoami
curl -i -H 'X-Shale-UserId: 123' -H 'X-Shale-TenantId: 456' 'http://localhost:8080/api/cases/search?query=test'
```

Expected behavior:

- `/api/health` returns `200` with `status=ok`.
- `/api/dev/whoami` is not available.
- DB-backed endpoints reject development headers and fail closed until real auth is implemented.
