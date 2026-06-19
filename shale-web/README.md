# Shale Web

Standalone React + TypeScript + Vite frontend for the browser login milestone. This app is intentionally separate from the JavaFX/Maven application and is not a Maven module.

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

## API target

The first milestone is browser login against the deployed Azure API only. By default the app posts credentials to:

```text
https://shale-api.azurewebsites.net/api/auth/login
```

If the Azure App Service name changes, copy `.env.example` to `.env.local` and set `VITE_SHALE_API_BASE_URL` to the deployed API origin. The backend must allow `http://localhost:5173` in `SHALE_ALLOWED_CORS_ORIGINS` for local browser login.
