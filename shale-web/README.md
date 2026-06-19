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

## Required Azure CORS setting

Local browser login requires the Azure App Service to allow the Vite development origin:

```text
SHALE_ALLOWED_CORS_ORIGINS=http://localhost:5173
```

Restart the Azure App Service after changing CORS or other App Service application settings so the running API process picks up the new values.
