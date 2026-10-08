## Shale relevant test selection

### Changed paths
- `docs/architecture/shale-web-v2-architecture-roadmap.md`
- `shale-web/docs/phase-3b-evidence/browser-observations.json`
- `shale-web/docs/phase-3b-evidence/browser-review.cjs`
- `shale-web/docs/phase-3b-evidence/outage-retry-1280.png`
- `shale-web/docs/phase-3b-evidence/outage-retry-320.png`
- `shale-web/docs/phase-3b-evidence/outage-sign-in-1280.png`
- `shale-web/docs/phase-3b-evidence/outage-sign-in-320.png`
- `shale-web/docs/phase-3b-evidence/restored-1280.png`
- `shale-web/docs/phase-3b-evidence/restored-320.png`
- `shale-web/docs/phase-3b-evidence/sign-in-1280.png`
- `shale-web/docs/phase-3b-evidence/sign-in-320.png`
- `shale-web/docs/phase-3b-review.md`
- `shale-web/src/App.test.tsx`
- `shale-web/src/App.tsx`
- `shale-web/src/api.ts`
- `shale-web/src/useStartupSession.test.tsx`
- `shale-web/src/useStartupSession.ts`

### Selected areas and reasons
- **server**
  - shale-web/docs/phase-3b-evidence/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/outage-retry-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/outage-retry-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/outage-sign-in-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/outage-sign-in-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/restored-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/restored-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/sign-in-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-evidence/sign-in-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3b-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/api.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/useStartupSession.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/useStartupSession.ts: Server, API, authentication endpoint, or web consumer changed.

**Full-suite escalation:** no
**Selected modules:** shale-server
**Modified test classes:** none
**Maximum generated command length:** 123 / 7000
**Complete owned classes (manual/advisory):** 32

### Selected test reasons
- `com.shale.server.controller.AuthControllerTest`
  - Classification: blocking_smoke, critical
  - Mapping: area:server
  - Changed path: `shale-web/docs/phase-3b-evidence/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3b-evidence/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3b-evidence/outage-retry-1280.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/outage-retry-320.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/outage-sign-in-1280.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/outage-sign-in-320.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/restored-1280.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/restored-320.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/sign-in-1280.png`
  - Changed path: `shale-web/docs/phase-3b-evidence/sign-in-320.png`
  - Changed path: `shale-web/docs/phase-3b-review.md`
  - Changed path: `shale-web/src/App.test.tsx`
  - Changed path: `shale-web/src/App.tsx`
  - Changed path: `shale-web/src/api.ts`
  - Changed path: `shale-web/src/useStartupSession.test.tsx`
  - Changed path: `shale-web/src/useStartupSession.ts`

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
