## Shale relevant test selection

### Changed paths
- `docs/architecture/shale-web-v2-architecture-roadmap.md`
- `shale-web/docs/phase-3e-evidence/audit-production.json`
- `shale-web/docs/phase-3e-evidence/audit.json`
- `shale-web/docs/phase-3e-evidence/login/browser-observations.json`
- `shale-web/docs/phase-3e-evidence/login/browser-review.cjs`
- `shale-web/docs/phase-3e-evidence/login/cases-dark.png`
- `shale-web/docs/phase-3e-evidence/login/cases-light.png`
- `shale-web/docs/phase-3e-evidence/login/contacts-dark.png`
- `shale-web/docs/phase-3e-evidence/login/contacts-light.png`
- `shale-web/docs/phase-3e-evidence/login/tasks-dark.png`
- `shale-web/docs/phase-3e-evidence/login/tasks-light.png`
- `shale-web/docs/phase-3e-evidence/logout/browser-observations.json`
- `shale-web/docs/phase-3e-evidence/logout/browser-review.cjs`
- `shale-web/docs/phase-3e-evidence/logout/http-1280.png`
- `shale-web/docs/phase-3e-evidence/logout/http-320.png`
- `shale-web/docs/phase-3e-evidence/logout/pending-1280.png`
- `shale-web/docs/phase-3e-evidence/logout/pending-320.png`
- `shale-web/docs/phase-3e-evidence/logout/success-1280.png`
- `shale-web/docs/phase-3e-evidence/logout/success-320.png`
- `shale-web/docs/phase-3e-evidence/logout/timeout-1280.png`
- `shale-web/docs/phase-3e-evidence/logout/timeout-320.png`
- `shale-web/docs/phase-3e-evidence/startup/browser-observations.json`
- `shale-web/docs/phase-3e-evidence/startup/browser-review.cjs`
- `shale-web/docs/phase-3e-evidence/startup/pending-retry-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/pending-retry-320.png`
- `shale-web/docs/phase-3e-evidence/startup/pending-return-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/pending-return-320.png`
- `shale-web/docs/phase-3e-evidence/startup/restored-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/restored-320.png`
- `shale-web/docs/phase-3e-evidence/startup/sign-in-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/sign-in-320.png`
- `shale-web/docs/phase-3e-evidence/startup/timeout-retry-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/timeout-retry-320.png`
- `shale-web/docs/phase-3e-evidence/startup/timeout-return-1280.png`
- `shale-web/docs/phase-3e-evidence/startup/timeout-return-320.png`
- `shale-web/docs/phase-3e-evidence/test-selection.md`
- `shale-web/docs/phase-3e-review.md`
- `shale-web/src/App.test.tsx`
- `shale-web/src/App.tsx`
- `shale-web/src/app/routeRegistry.test.ts`
- `shale-web/src/app/routeRegistry.ts`
- `shale-web/src/preview/FoundationPreview.test.tsx`
- `shale-web/src/preview/FoundationPreview.tsx`
- `shale-web/src/preview/navigation.ts`
- `shale-web/src/returnPath.test.ts`
- `shale-web/src/returnPath.ts`
- `shale-web/src/shell/ResponsiveShell.tsx`
- `shale-web/src/shell/navigation.ts`

### Selected areas and reasons
- **server**
  - shale-web/docs/phase-3e-evidence/audit-production.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/audit.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/cases-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/cases-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/contacts-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/contacts-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/tasks-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/login/tasks-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/http-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/http-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/pending-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/pending-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/success-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/success-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/timeout-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/logout/timeout-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/pending-retry-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/pending-retry-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/pending-return-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/pending-return-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/restored-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/restored-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/sign-in-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/sign-in-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/timeout-retry-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/timeout-retry-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/timeout-return-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/startup/timeout-return-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-evidence/test-selection.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3e-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/app/routeRegistry.test.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/app/routeRegistry.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/preview/FoundationPreview.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/preview/FoundationPreview.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/preview/navigation.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/returnPath.test.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/returnPath.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/shell/ResponsiveShell.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/shell/navigation.ts: Server, API, authentication endpoint, or web consumer changed.

**Full-suite escalation:** no
**Selected modules:** shale-server
**Modified test classes:** none
**Maximum generated command length:** 123 / 7000
**Complete owned classes (manual/advisory):** 32

### Selected test reasons
- `com.shale.server.controller.AuthControllerTest`
  - Classification: blocking_smoke, critical
  - Mapping: area:server
  - Changed path: `shale-web/docs/phase-3e-evidence/audit-production.json`
  - Changed path: `shale-web/docs/phase-3e-evidence/audit.json`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/cases-dark.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/cases-light.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/contacts-dark.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/contacts-light.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/tasks-dark.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/login/tasks-light.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/http-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/http-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/pending-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/pending-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/success-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/success-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/timeout-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/logout/timeout-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/pending-retry-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/pending-retry-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/pending-return-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/pending-return-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/restored-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/restored-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/sign-in-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/sign-in-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/timeout-retry-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/timeout-retry-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/timeout-return-1280.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/startup/timeout-return-320.png`
  - Changed path: `shale-web/docs/phase-3e-evidence/test-selection.md`
  - Changed path: `shale-web/docs/phase-3e-review.md`
  - Changed path: `shale-web/src/App.test.tsx`
  - Changed path: `shale-web/src/App.tsx`
  - Changed path: `shale-web/src/app/routeRegistry.test.ts`
  - Changed path: `shale-web/src/app/routeRegistry.ts`
  - Changed path: `shale-web/src/preview/FoundationPreview.test.tsx`
  - Changed path: `shale-web/src/preview/FoundationPreview.tsx`
  - Changed path: `shale-web/src/preview/navigation.ts`
  - Changed path: `shale-web/src/returnPath.test.ts`
  - Changed path: `shale-web/src/returnPath.ts`
  - Changed path: `shale-web/src/shell/ResponsiveShell.tsx`
  - Changed path: `shale-web/src/shell/navigation.ts`

### Commands
- `mvn -pl shale-server -am -Dtest=com.shale.server.controller.AuthControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn test`

### Broader ownership coverage

- `python build/test-selection/select_tests.py --area server --run`
- Complete area ownership is optional local/advisory coverage.

### Skipped areas
- **build-scripts:** No changed path mapped to this area.
- **calendar:** No changed path mapped to this area.
- **cases:** No changed path mapped to this area.
- **contacts:** No changed path mapped to this area.
- **notifications:** No changed path mapped to this area.
- **organizations:** No changed path mapped to this area.
- **reports:** No changed path mapped to this area.
- **security-data:** No changed path mapped to this area.
- **settings-team:** No changed path mapped to this area.
- **tasks:** No changed path mapped to this area.
- **ui-behavior:** No changed path mapped to this area.
- **ui-fxml-structure:** No changed path mapped to this area.
- **ui-presentation:** No changed path mapped to this area.
- **ui-visual-advisory:** No changed path mapped to this area.
- **updater:** No changed path mapped to this area.
