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

## Step 4B authentication foundation

`POST /api/auth/login` now validates credentials through the existing `AuthServicePort` / `AuthServiceImpl` path and issues a signed bearer token. The token contains only server-derived identity claims: `userId`, `shaleClientId`, optional `email`, issued-at time, and expiry. Tenant ids are not accepted from login request bodies.

### Required auth environment variables

- `SHALE_AUTH_TOKEN_SECRET` is required in `dev`, `local`, `prod`, and `azure` profiles. Use a high-entropy value of at least 32 characters. Do not commit it to source control.
- `SHALE_AUTH_TOKEN_TTL_SECONDS` is optional and defaults to 8 hours.

### Login request

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.test","password":"correct horse battery staple"}'
```

Successful responses include a bearer token and a safe user payload:

```json
{
  "authenticated": true,
  "tokenType": "Bearer",
  "accessToken": "<signed-token>",
  "expiresInSeconds": 28800,
  "user": {
    "authenticated": true,
    "userId": 123,
    "shaleClientId": 456,
    "email": "ada@example.test",
    "displayName": "Ada Lovelace",
    "nameFirst": "Ada",
    "nameLast": "Lovelace"
  }
}
```

Invalid passwords and unknown users return `401` with the same safe `invalid_credentials` response. Passwords, password hashes, and database-specific failure details are never returned.

### Current user

```bash
TOKEN='<signed-token-from-login>'
curl -i http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Missing, malformed, invalid, or expired tokens return `401`.

### Calling protected endpoints

Use the bearer token on DB-backed read endpoints:

```bash
curl -i 'http://localhost:8080/api/cases/search?query=smith' \
  -H "Authorization: Bearer $TOKEN"
```

The server resolves `userId` and `shaleClientId` from the signed token and uses that principal for runtime DB session context. Clients must not send tenant ids in request bodies or query strings to select a tenant.

### Dev/local compatibility

`dev` and `local` profiles still accept `X-Shale-UserId` / `X-Shale-TenantId` for the temporary local workflow, and they also accept bearer tokens when `SHALE_AUTH_TOKEN_SECRET` is configured.

### Azure/prod behavior

`prod` and `azure` profiles accept bearer tokens only. They do not trust `X-Shale-UserId` or `X-Shale-TenantId`.

## Step 4C logout and revocation

Access tokens now include a unique `jti` token id claim. `POST /api/auth/logout` verifies the presented bearer token and stores only the revoked token id until the token expiry time; raw tokens are not stored. Expired revocation entries are ignored and removed opportunistically.

```bash
curl -i -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Logout is safe to call with a missing or invalid token. The client should clear any locally held bearer token regardless of the response.

`GET /api/auth/me` returns a safe user profile shape when a valid, non-revoked token is presented:

```json
{
  "authenticated": true,
  "userId": 123,
  "shaleClientId": 456,
  "email": "ada@example.test",
  "displayName": "Ada Lovelace",
  "nameFirst": "Ada",
  "nameLast": "Lovelace",
  "isAdmin": false,
  "isAttorney": true,
  "initials": "AL",
  "color": "#123456"
}
```

`POST /api/auth/refresh` is available as a conservative foundation: it accepts a valid, non-revoked bearer token, revokes that token id, and returns a new access token. Long-lived refresh tokens are deferred.

Recommended web-client behavior:

- Store the bearer token only for as long as needed to keep the user signed in.
- Clear the bearer token on logout.
- Treat any `401` from protected API calls as a signal to return to login.
- Do not store tenant ids separately as trusted client state; use the signed token/server response only for display and let the server enforce tenant context.
