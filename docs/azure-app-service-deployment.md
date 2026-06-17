# Shale Server Azure App Service Deployment Guide

This guide is the repeatable deployment checklist for the first `shale-server` Azure App Service deployment. It assumes the Step 4A-4D API migration work is present: Azure/local profiles, bearer auth, health endpoints, Swagger/OpenAPI, standardized errors, and paged read endpoints.

## Deployment readiness review

- `shale-server` listens on `server.port=${PORT:8080}`, so Azure App Service can provide the runtime port without source changes.
- Use `SPRING_PROFILES_ACTIVE=azure` for Azure App Service. The `azure` profile uses bearer-token auth only and does not register the temporary development header resolver.
- `dev` and `local` are the only profiles that accept `X-Shale-UserId` and `X-Shale-TenantId`.
- Database URLs, database users, database passwords, CORS origins, token secrets, and token TTLs are read from environment variables or app settings.
- Tenant/user identity for protected routes comes from the signed bearer token and server-side runtime session setup, not from request bodies or query parameters.
- Do not deploy source-controlled secrets. Keep all credentials in Azure App Service application settings or Key Vault-backed settings.

## Build the deployable jar

From the repository root:

```bash
mvn -pl shale-server -am clean package
```

The Spring Boot executable jar is produced under `shale-server/target/`. Copy it to the deployment package as `shale-server.jar` so the startup command can stay stable across Maven version changes:

```bash
cp shale-server/target/shale-server-*.jar shale-server.jar
```

If you deploy the helper script, place `deployment/azure/startup.sh` beside the jar or keep the repository-relative path in the deployed package.

## Create Azure App Service

Recommended first deployment settings:

1. Create an Azure App Service plan on Linux.
2. Create a Web App using the Java 21 runtime stack.
3. Use the region closest to the Azure SQL database where practical.
4. Configure HTTPS-only.
5. Configure deployment from a zip package, Azure CLI, GitHub Actions, or the Azure Portal deployment center.
6. Deploy the jar as `/home/site/wwwroot/shale-server.jar`.

Example Azure CLI skeleton:

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
  --name shale-server-prod \
  --runtime "JAVA:21-java21"
```

Adjust names, SKU, region, and runtime stack to match the target Azure subscription standards.

## Required App Service application settings

Set these values in Azure Portal **Configuration > Application settings** or with `az webapp config appsettings set`.

| Setting | Required | Notes |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | Set to `azure`. |
| `SHALE_APP_DB_URL` | Yes | Auth/app Azure SQL JDBC URL. Legacy alias `SHALE_APP_JDBC_URL` is still supported. |
| `SHALE_APP_DB_USER` | Yes | Auth/app database username. Legacy alias `SHALE_APP_USER` is still supported. |
| `SHALE_APP_DB_PASSWORD` | Yes | Auth/app database password. Legacy alias `SHALE_APP_PASS` is still supported. |
| `SHALE_RT_DB_URL` | Yes | Runtime Azure SQL JDBC URL. Legacy alias `SHALE_RT_JDBC_URL` is still supported. |
| `SHALE_RT_DB_USER` | Yes | Runtime database username. Legacy alias `SHALE_RT_USER` is still supported. |
| `SHALE_RT_DB_PASSWORD` | Yes | Runtime database password. Legacy alias `SHALE_RT_PASS` is still supported. |
| `SHALE_AUTH_TOKEN_SECRET` | Yes | At least 32 characters. Use a high-entropy secret stored only in Azure settings/Key Vault. |
| `SHALE_AUTH_TOKEN_TTL_SECONDS` | No | Defaults to 8 hours. Set shorter values if required by policy. |
| `SHALE_ALLOWED_CORS_ORIGINS` | For browser clients | Comma-separated allowed origins, for example `https://app.example.com`. |
| `SHALE_SERVER_ALLOWED_CORS_ORIGINS` | Optional alias | Alternative CORS env var name. |
| `DB_MAX_POOL_SIZE` | No | Existing pool-size tuning knob. |
| `DB_CONNECTION_TIMEOUT_MS` | No | Existing connection-timeout tuning knob. |
| `JAVA_OPTS` | No | JVM tuning, for example memory options. |

Do not configure `X-Shale-UserId`, `X-Shale-TenantId`, hardcoded tenant ids, hardcoded user ids, JDBC URLs, usernames, passwords, or token secrets in source files.

## Startup command

Preferred Linux App Service startup command if the jar is deployed to `/home/site/wwwroot/shale-server.jar`:

```bash
java $JAVA_OPTS -jar /home/site/wwwroot/shale-server.jar --spring.profiles.active=azure
```

Alternative helper script startup command:

```bash
bash /home/site/wwwroot/deployment/azure/startup.sh
```

The helper script defaults `SPRING_PROFILES_ACTIVE` to `azure`, defaults the jar path to `/home/site/wwwroot/shale-server.jar`, validates that the jar exists, and then executes `java -jar`.

For Windows App Service, use `deployment/azure/startup.cmd` as a reference and prefer the portal startup command equivalent for the deployed jar path.

## Health-check configuration

Configure the App Service health check path to:

```text
/api/health
```

`/api/health` is DB-free and is suitable for liveness checks. Use `/api/health/db` for readiness/smoke validation because it checks the configured auth/app database pool with a safe query and returns only safe status messages.

Operational endpoints:

| Endpoint | Auth | Purpose |
| --- | --- | --- |
| `GET /api/health` | None | DB-free liveness probe. |
| `GET /api/health/db` | None | Safe app/auth DB readiness check. |
| `GET /v3/api-docs` | None | OpenAPI JSON. |
| `GET /swagger-ui/index.html` | None | Swagger UI. |

If public Swagger UI is not desired after the first deployment phase, restrict access at the network/App Service layer or add a future server-side docs profile/security policy.

## Deploy the jar

Example zip deployment flow:

```bash
mvn -pl shale-server -am clean package
rm -rf build/azure-appservice
mkdir -p build/azure-appservice/deployment/azure
cp shale-server/target/shale-server-*.jar build/azure-appservice/shale-server.jar
cp deployment/azure/startup.sh build/azure-appservice/deployment/azure/startup.sh
(
  cd build/azure-appservice
  zip -r ../shale-server-appservice.zip .
)
az webapp deployment source config-zip \
  --resource-group rg-shale-web \
  --name shale-server-prod \
  --src build/shale-server-appservice.zip
```

After deployment, restart the Web App so the startup command and app settings are applied.

## Log streaming and diagnostics

Use Azure Portal **Log stream**, or Azure CLI:

```bash
az webapp log config \
  --resource-group rg-shale-web \
  --name shale-server-prod \
  --application-logging filesystem \
  --level information
az webapp log tail \
  --resource-group rg-shale-web \
  --name shale-server-prod
```

Startup failures should clearly identify missing required configuration such as database settings or `SHALE_AUTH_TOKEN_SECRET`. Do not paste secrets into tickets or logs.

## Restart procedure

```bash
az webapp restart \
  --resource-group rg-shale-web \
  --name shale-server-prod
```

Use restart after changing app settings, startup command, or deployment package. Re-run the smoke-test checklist after each restart.

## Rollback procedure

Preferred rollback options:

1. Redeploy the last known-good zip/jar package.
2. If deployment slots are configured, swap back to the previous known-good slot.
3. Restore previous App Service application settings if a configuration change caused the failure.
4. Restart the app and rerun smoke tests.

Keep every release artifact identifiable by commit SHA and build timestamp so rollback can use an exact jar.

## Post-deployment smoke-test checklist

Set these shell variables for smoke testing:

```bash
BASE_URL="https://shale-server-prod.azurewebsites.net"
EMAIL="ada@example.test"
PASSWORD="replace-with-test-password"
```

Run:

```bash
curl -i "$BASE_URL/api/health"
curl -i "$BASE_URL/api/health/db"
curl -i "$BASE_URL/v3/api-docs"
curl -i "$BASE_URL/swagger-ui/index.html"
LOGIN_RESPONSE=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(printf '%s' "$LOGIN_RESPONSE" | python -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
curl -i "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $TOKEN"
curl -i "$BASE_URL/api/cases/search?query=smith" \
  -H "Authorization: Bearer $TOKEN"
curl -i "$BASE_URL/api/cases/search-page?query=smith&page=0&size=25" \
  -H "Authorization: Bearer $TOKEN"
curl -i "$BASE_URL/api/cases/search?query=smith"
curl -i -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"","password":""}'
curl -i "$BASE_URL/api/cases/search?query=smith" \
  -H 'X-Shale-UserId: 1' \
  -H 'X-Shale-TenantId: 1'
```

Expected results:

- `/api/health` returns `200` and `{"status":"ok"}`.
- `/api/health/db` returns `200` and `{"status":"ok"}` when Azure SQL is reachable and configured.
- OpenAPI and Swagger endpoints load.
- Login returns an access token and safe user payload.
- `/api/auth/me` returns the authenticated safe user profile.
- Authenticated case search returns `200`.
- Case search without bearer auth returns standardized `401`.
- Empty login payload returns standardized `400`.
- Dev headers in the `azure` profile do not authenticate the request; without a bearer token, the request returns standardized `401`.
