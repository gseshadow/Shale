# Shale Server Azure App Service Deployment Guide

This guide is the repeatable deployment checklist for deploying `shale-server` to Azure App Service. It assumes the Step 4A-4D API migration work is present: Azure/local profiles, bearer auth, health endpoints, Swagger/OpenAPI, standardized errors, and paged read endpoints.

## Deployment readiness review

- `shale-server` listens on `server.port=${PORT:8080}`, allowing Azure App Service to provide the runtime port.
- Use `SPRING_PROFILES_ACTIVE=azure` in Azure App Service.
- The `azure` profile uses bearer-token authentication only.
- The `dev` and `local` profiles are the only profiles that support development headers such as `X-Shale-UserId` and `X-Shale-TenantId`.
- Database URLs, usernames, passwords, token secrets, token TTLs, and CORS settings come from Azure App Settings.
- Do not store secrets in source control.
- Tenant/user identity comes exclusively from validated bearer tokens.

---

## Build the deployable jar

From the repository root:

```bash
mvn -pl shale-server -am clean package -DskipTests
```

Locate the executable Spring Boot jar:

```text
shale-server/target/shale-server-<version>.jar
```

Rename or copy it as:

```text
shale-server.jar
```

Example:

```bash
cp shale-server/target/shale-server-*.jar shale-server.jar
```

Important:

Do not deploy:

```text
shale-server-<version>.jar.original
```

The `.original` file is not executable.

---

## Create Azure App Service

Recommended settings:

1. Linux App Service Plan
2. Java 21 Runtime
3. HTTPS Only enabled
4. Region as close to Azure SQL as practical
5. Deploy a Spring Boot executable jar

Example:

```bash
az group create --name rg-shale-web --location eastus

az appservice plan create \
  --resource-group rg-shale-web \
  --name plan-shale-web \
  --is-linux \
  --sku B1

az webapp create \
  --resource-group rg-shale-web \
  --plan plan-shale-web \
  --name shale-api \
  --runtime "JAVA:21-java21"
```

---

## Required App Settings

Configure under:

```text
Azure Portal
→ Web App
→ Settings
→ Environment Variables
```

Required:

| Setting | Notes |
|----------|----------|
| SPRING_PROFILES_ACTIVE | azure |
| SHALE_APP_JDBC_URL | App/Auth database JDBC URL |
| SHALE_APP_USER | App/Auth database user |
| SHALE_APP_PASS | App/Auth database password |
| SHALE_RT_JDBC_URL | Runtime database JDBC URL |
| SHALE_RT_USER | Runtime database user |
| SHALE_RT_PASS | Runtime database password |
| SHALE_AUTH_TOKEN_SECRET | 32+ character random secret |

Optional:

| Setting | Notes |
|----------|----------|
| SHALE_AUTH_TOKEN_TTL_SECONDS | Defaults to 28800 (8 hours) |
| SHALE_ALLOWED_CORS_ORIGINS | Required for browser clients. For local `shale-web` login, set `SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173` and restart the App Service. |
| DB_MAX_POOL_SIZE | Pool tuning |
| DB_CONNECTION_TIMEOUT_MS | Pool tuning |

---

## Startup Command

Configure under:

```text
Azure Portal
→ Web App
→ Settings
→ Configuration
→ Stack Settings
→ Startup Command
```

Use:

```bash
java -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure
```

Do NOT use:

```bash
java $JAVA_OPTS -jar /home/site/wwwroot/shale-server.jar
```

During deployment testing Azure attempted to interpret `$JAVA_OPTS` as a Java class name and startup failed.

---

## Deploy the jar

### Recommended Method (Validated)

Open:

```text
Azure Portal
→ Web App
→ Advanced Tools
→ Go
```

This opens Kudu.

Navigate:

```text
Debug Console
→ CMD
→ site
→ wwwroot
```

Upload:

```text
shale-server.jar
```

directly into:

```text
/home/site/wwwroot
```

Verify the file exists before restarting.

This deployment path was validated successfully.

---

## Restart

After changing startup commands, environment variables, or deployed jars:

```text
Azure Portal
→ Web App
→ Restart
```

---

## Log Streaming

Open:

```text
Azure Portal
→ Monitoring
→ Log Stream
```

Successful startup should show:

```text
Starting ShaleServerApplication
The following 1 profile is active: "azure"
auth-pool - Start completed.
runtime-pool - Start completed.
Tomcat started on port 80
Started ShaleServerApplication
```

---

## Health Check Configuration

Configure:

```text
/api/health
```

Health endpoints:

| Endpoint | Purpose |
|----------|----------|
| /api/health | Liveness |
| /api/health/db | Database readiness |
| /v3/api-docs | OpenAPI JSON |
| /swagger-ui/index.html | Swagger UI |

---

## Swagger Authentication

Open:

```text
https://<site>.azurewebsites.net/swagger-ui/index.html
```

Authenticate:

1. Execute `POST /api/auth/login`
2. Copy the returned access token
3. Click Authorize

Important:

Paste only:

```text
<access token>
```

NOT:

```text
Bearer <access token>
```

Swagger already prepends the Bearer scheme automatically.

Using `Bearer <token>` manually may result in:

```json
{
  "status": 401,
  "message": "Invalid or expired authentication token."
}
```

---

## Current Step 5 Browser Login Endpoint

The current deployed Azure API origin for the Step 5 `shale-web` browser-login milestone is:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

Local browser login from Vite requires this App Service setting followed by an App Service restart:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173
```

---

## Post Deployment Smoke Tests

### Health

```text
GET /api/health
```

Expected:

```json
{
  "status": "ok"
}
```

### Database

```text
GET /api/health/db
```

Expected:

```json
{
  "status": "ok"
}
```

### OpenAPI

```text
GET /v3/api-docs
```

Expected:

```text
200 OK
```

### Swagger

```text
GET /swagger-ui/index.html
```

Expected:

```text
Swagger UI loads
```

### Login

```text
POST /api/auth/login
```

Expected:

```json
{
  "accessToken": "...",
  ...
}
```

### Authenticated User

```text
GET /api/auth/me
```

Expected:

```text
200 OK
```

Returns authenticated user information.

### Protected Data Endpoint

Example:

```text
GET /api/cases/search?query=smith
```

Expected:

```text
200 OK
```

This validates:

- JWT generation
- JWT validation
- Azure profile
- Database connectivity
- Runtime session creation
- Tenant context propagation
- End-to-end API functionality

---

## First Successful Deployment Notes (2026-06-18)

The first successful Azure deployment validated:

- Linux App Service
- Java 21
- Azure profile
- Auth database connectivity
- Runtime database connectivity
- Swagger/OpenAPI
- JWT authentication
- `/api/health`
- `/api/health/db`
- `/api/auth/login`
- `/api/auth/me`

Known deployment lessons:

1. Use Kudu direct upload if Deployment Center placement is unclear.
2. Verify the jar exists in `/home/site/wwwroot`.
3. Use `java -jar ...` without `$JAVA_OPTS`.
4. Paste only the raw token into Swagger authorization.
5. Expect first startup to take approximately 45–60 seconds while Spring Boot initializes and Azure instrumentation attaches.