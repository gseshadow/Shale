# Shale Web

Standalone React + TypeScript + Vite frontend for the Step 5 browser-login milestone. This app is intentionally separate from the JavaFX/Maven application and is not a Maven module.

## Local developer workflow

```bash
cd shale-web
npm install
npm run dev
```

Open <http://localhost:5173>.

## Validation workflow

```bash
npm run typecheck
npm run build
```

## Current Azure API target

The browser-login milestone targets the deployed Azure API only. The current deployed API origin is:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

By default, `shale-web` posts credentials to:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net/api/auth/login
```

After login succeeds, the app stores the returned access token in `sessionStorage` and calls:

```text
https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net/api/auth/me
```

with `Authorization: Bearer <token>`. The authenticated user information displayed in the UI comes from `/api/auth/me`.

## `.env.local` override

If the Azure App Service URL changes, copy `.env.example` to `.env.local` and update the origin:

```bash
cp .env.example .env.local
```

```text
VITE_SHALE_API_BASE_URL=https://shale-api-hsd6hrcya0g4amhv.southcentralus-01.azurewebsites.net
```

Do not include `/api/auth/login` or any other path in `VITE_SHALE_API_BASE_URL`; use only the API origin.


## Deployment plan

The 5P-4 read-only beta build/deploy runbook is documented in [docs/shale-web-deployment.md](../docs/shale-web-deployment.md). It recommends Azure Static Web Apps for the first beta, documents the required `VITE_SHALE_API_BASE_URL` build setting, and lists the Azure API CORS update needed for the deployed frontend origin.

## Required Azure CORS setting

Local browser login requires the Azure App Service to allow the Vite development origin:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173
```

Restart the Azure App Service after changing CORS or other App Service application settings so the running API process picks up the new values.

## Web V2 Phase 2A synthetic preview

Run `npm run dev` and open <http://localhost:5173/foundation.html> for the isolated responsive shell,
shared components and Light/Dark examples. It makes no API calls and needs no credentials. `/` and the
existing business routes now use the authenticated ResponsiveShell around their retained beta screen content. [Phase 2A review and evidence](docs/phase-2a-review.md)
documents ownership, local build review, checks, limitations and temporary-entry removal.


## Web V2 Phase 2B authenticated shell

The existing authenticated URLs use one responsive shell and navigation registry. Light/Dark shell
selection is local to the mounted session; route screens retain their Light beta skin. Authentication,
services and mutation handlers are unchanged. [Phase 2B review](docs/phase-2b-review.md) records the
synthetic review script, screenshots, validation, known login return-to issue, acceptance gaps and
frontend-only rollback. Phase 2 remains in progress; no deployment is part of this milestone.


## Web V2 Phase 2C My Shale presentation

My Shale now adopts shared presentation in both shell themes, including assigned cards and existing
Complete actions. Other routes retain their beta Light skin. [Phase 2C review](docs/phase-2c-review.md)
records exact scope, synthetic screenshots, native browser zoom, checks, gaps and frontend rollback.
Phase 2 remains in progress; foundation.html and the isolated preview remain available.
