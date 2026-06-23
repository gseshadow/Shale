# 5P-4 Shale Web Build / Deploy Plan

This runbook plans the first publication of the read-only `shale-web` beta. It is documentation only: do not deploy the web app or change Azure resources as part of 5P-4.

## Scope

The beta web app is read-only and currently includes:

- Login/auth
- My Shale
- Cases search/detail
- Related contacts
- Case tasks
- Case updates
- Status timeline
- Contacts search/detail
- Organizations search/detail
- Team
- Settings

Current endpoints/origins:

| Component | Origin |
| --- | --- |
| Deployed Azure API | `https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net` |
| Local Vite dev server | `http://localhost:5173` |

## Documents reviewed for this plan

- `architecture/codex-prompt-rules.md`
- `docs/azure-app-service-deployment.md`
- `docs/web-api-azure-readiness.md`
- `docs/web-api-local-smoke-test.md`
- `docs/web-api-migration-step-2.md`
- `docs/web-api-step-3.md`
- `shale-web/README.md`

## Recommended hosting option

Use **Azure Static Web Apps** for the first read-only beta.

### Option evaluation

| Option | Fit for first beta | Notes |
| --- | --- | --- |
| Azure Static Web Apps | **Recommended** | Simplest practical match for a Vite static frontend. It directly hosts the `dist` output, provides HTTPS, supports custom domains later, and has a straightforward GitHub Actions deployment path. No server runtime is needed for the current read-only React app. |
| Azure App Service static hosting | Acceptable but heavier | Could host static files from an App Service, but this adds runtime/app-service management that the frontend does not need. The API already uses App Service; duplicating that model for static files is more operational overhead for beta. |
| Azure Storage static website + CDN | Good later for cost/performance tuning | Works well for static assets and CDN scenarios, but setup is more fragmented: storage account, static website endpoint, optional CDN/Front Door, cache invalidation, and routing/error-document configuration. It is less simple than Static Web Apps for the first beta. |

Decision: create a new Azure Static Web App for `shale-web`, initially using the generated `*.azurestaticapps.net` hostname. Add a custom domain only after the beta origin and CORS settings have been verified.

## Frontend build configuration

### Production API base URL

The production API base URL for the beta is:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

Configure the frontend build with:

```text
VITE_SHALE_API_BASE_URL=https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

Do not include `/api`, `/api/auth/login`, or any other path in `VITE_SHALE_API_BASE_URL`; it must be the API origin only.

### Required local validation commands

From the repository root:

```bash
cd shale-web
npm install
npm run typecheck
npm run build
```

Equivalent root-level validation commands:

```bash
npm run typecheck --prefix shale-web
npm run build --prefix shale-web
```

### Static output folder

The production build output is:

```text
shale-web/dist
```

For Azure Static Web Apps, use:

| Setting | Value |
| --- | --- |
| App location | `shale-web` |
| Build command | `npm run build` |
| Output location | `dist` |
| API location | blank / not configured |

## Required Azure API CORS update

The existing local development browser origin is:

```text
http://localhost:5173
```

When the deployed Static Web Apps origin is known, add it to the Azure API App Service setting `SHALE_ALLOWED_CORS_ORIGINS`.

Example:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173,https://<deployed-web-origin>
```

For the initial Azure Static Web Apps hostname, `<deployed-web-origin>` will look similar to:

```text
https://<generated-name>.azurestaticapps.net
```

If a custom domain is added later, add that exact HTTPS origin too, for example:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173,https://<generated-name>.azurestaticapps.net,https://app.example.com
```

Restart the Azure App Service after changing this app setting so the running API process picks up the new CORS list.

## First manual deployment workflow

Do not perform these steps during 5P-4. Use this checklist during 5P-5 Publish Read-Only Beta.

1. Confirm the API is already deployed and healthy.
   - Open `https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net/api/health`.
   - Expected result: HTTP 200 and a safe health response.
2. Validate the frontend locally.
   ```bash
   cd shale-web
   npm install
   npm run typecheck
   npm run build
   ```
3. Create a new Azure Static Web App.
   - Runtime/build preset: React or Custom Vite-compatible static app.
   - App location: `shale-web`.
   - API location: leave blank.
   - Output location: `dist`.
   - Build command: `npm run build`.
4. Add the Static Web App environment variable:
   ```text
   VITE_SHALE_API_BASE_URL=https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
   ```
5. Deploy the current branch/build to Azure Static Web Apps.
6. Copy the deployed frontend origin, for example `https://<generated-name>.azurestaticapps.net`.
7. Update the API App Service app setting:
   ```text
   SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173,https://<deployed-web-origin>
   ```
8. Restart the Azure API App Service.
9. Run the smoke-test checklist below against the deployed web origin.
10. Record the deployed web origin, source branch/commit, and validation outcome in the release notes or 5P-5 handoff notes.

## Recommended future CI/CD workflow

Use Azure Static Web Apps' generated GitHub Actions workflow after the first beta deployment is proven.

Recommended workflow behavior:

1. Trigger on pull requests and pushes to the beta/release branch.
2. Install dependencies from `shale-web/package-lock.json` with `npm ci`.
3. Run `npm run typecheck --prefix shale-web`.
4. Run `npm run build --prefix shale-web` with `VITE_SHALE_API_BASE_URL` set to the production API origin.
5. Deploy `shale-web/dist` to the Azure Static Web App only after typecheck and build pass.
6. Use environment-specific Static Web Apps secrets/settings rather than committing deployment tokens or secrets.
7. Keep API deployment independent from frontend deployment unless a future release requires coordinated API/frontend contract changes.

## Rollback strategy

For the first beta, rollback should favor fast restoration of the last known-good static build.

1. Identify the last known-good frontend commit and Azure Static Web Apps deployment.
2. Redeploy that commit/build through the Static Web Apps deployment history or CI workflow.
3. If the issue is caused by the deployed frontend origin changing, restore `SHALE_ALLOWED_CORS_ORIGINS` to include the active known-good origin and restart the API App Service.
4. If login or API reads fail after rollback, verify both:
   - The frontend was built with the expected `VITE_SHALE_API_BASE_URL`.
   - The API CORS list includes the exact deployed frontend origin.
5. If a custom domain is involved, keep DNS pointed at the known-good Static Web App until the replacement deployment is verified.

## Deployed beta smoke-test checklist

Run this checklist against `https://<deployed-web-origin>` after CORS is updated and the API App Service is restarted.

- Deployed web app loads over HTTPS.
- Login works with a valid beta user.
- Refresh preserves the authenticated session.
- My Shale loads.
- Case search works.
- Case detail loads.
- Related contacts display on case detail.
- Case tasks display on case detail.
- Case updates display on case detail.
- Status timeline displays on case detail.
- Contacts search works.
- Contact detail loads.
- Organizations search works.
- Organization detail loads.
- Team page works.
- Settings page works.
- Logout works.
- Non-authenticated access redirects to login.

## Remaining steps before 5P-5 Publish Read-Only Beta

- Choose the Azure resource group/name for the new Static Web App.
- Create the Azure Static Web App but do not add editing/write capabilities.
- Confirm where the Static Web App deployment token and environment settings will live.
- Deploy the validated `shale-web/dist` build.
- Add the deployed frontend origin to `SHALE_ALLOWED_CORS_ORIGINS` on the Azure API App Service.
- Restart the API App Service after the CORS app setting change.
- Run and record the deployed smoke-test checklist.
- Decide whether to keep the generated Azure Static Web Apps hostname for beta or add a custom domain after smoke tests pass.
