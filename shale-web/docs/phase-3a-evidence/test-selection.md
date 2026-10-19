## Shale relevant test selection

### Changed paths
- `docs/architecture/shale-web-v2-architecture-roadmap.md`
- `shale-web/docs/phase-2d-operator-checklist.md`
- `shale-web/docs/phase-2d-review.md`
- `shale-web/docs/phase-3a-evidence/browser-observations.json`
- `shale-web/docs/phase-3a-evidence/browser-review.cjs`
- `shale-web/docs/phase-3a-evidence/cases-dark.png`
- `shale-web/docs/phase-3a-evidence/cases-light.png`
- `shale-web/docs/phase-3a-evidence/contacts-dark.png`
- `shale-web/docs/phase-3a-evidence/contacts-light.png`
- `shale-web/docs/phase-3a-evidence/tasks-dark.png`
- `shale-web/docs/phase-3a-evidence/tasks-light.png`
- `shale-web/docs/phase-3a-review.md`
- `shale-web/src/App.test.tsx`
- `shale-web/src/App.tsx`
- `shale-web/src/returnPath.test.ts`
- `shale-web/src/returnPath.ts`

### Selected areas and reasons
- **server**
  - shale-web/docs/phase-2d-operator-checklist.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-2d-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/cases-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/cases-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/contacts-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/contacts-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/tasks-dark.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-evidence/tasks-light.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3a-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/returnPath.test.ts: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/returnPath.ts: Server, API, authentication endpoint, or web consumer changed.

**Full-suite escalation:** no
**Selected modules:** shale-server
**Modified test classes:** none
**Maximum generated command length:** 123 / 7000
**Complete owned classes (manual/advisory):** 32

### Selected test reasons
- `com.shale.server.controller.AuthControllerTest`
  - Classification: blocking_smoke, critical
  - Mapping: area:server
  - Changed path: `shale-web/docs/phase-2d-operator-checklist.md`
  - Changed path: `shale-web/docs/phase-2d-review.md`
  - Changed path: `shale-web/docs/phase-3a-evidence/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3a-evidence/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3a-evidence/cases-dark.png`
  - Changed path: `shale-web/docs/phase-3a-evidence/cases-light.png`
  - Changed path: `shale-web/docs/phase-3a-evidence/contacts-dark.png`
  - Changed path: `shale-web/docs/phase-3a-evidence/contacts-light.png`
  - Changed path: `shale-web/docs/phase-3a-evidence/tasks-dark.png`
  - Changed path: `shale-web/docs/phase-3a-evidence/tasks-light.png`
  - Changed path: `shale-web/docs/phase-3a-review.md`
  - Changed path: `shale-web/src/App.test.tsx`
  - Changed path: `shale-web/src/App.tsx`
  - Changed path: `shale-web/src/returnPath.test.ts`
  - Changed path: `shale-web/src/returnPath.ts`

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
