## Shale relevant test selection

### Changed paths
- `docs/architecture/shale-web-v2-architecture-roadmap.md`
- `shale-web/docs/phase-3i-evidence/audit-production.json`
- `shale-web/docs/phase-3i-evidence/audit.json`
- `shale-web/docs/phase-3i-evidence/browser-observations.json`
- `shale-web/docs/phase-3i-evidence/browser-review.cjs`
- `shale-web/docs/phase-3i-evidence/conflict-1280.png`
- `shale-web/docs/phase-3i-evidence/conflict-320.png`
- `shale-web/docs/phase-3i-evidence/dialog-1280.png`
- `shale-web/docs/phase-3i-evidence/dialog-320.png`
- `shale-web/docs/phase-3i-evidence/failed-clear logout-1280.png`
- `shale-web/docs/phase-3i-evidence/failed-clear logout-320.png`
- `shale-web/docs/phase-3i-evidence/failed-clear rejection-1280.png`
- `shale-web/docs/phase-3i-evidence/failed-clear rejection-320.png`
- `shale-web/docs/phase-3i-evidence/logout-1280.png`
- `shale-web/docs/phase-3i-evidence/logout-320.png`
- `shale-web/docs/phase-3i-evidence/navigation-1280.png`
- `shale-web/docs/phase-3i-evidence/navigation-320.png`
- `shale-web/docs/phase-3i-evidence/network-1280.png`
- `shale-web/docs/phase-3i-evidence/network-320.png`
- `shale-web/docs/phase-3i-evidence/pending-discard-1280.png`
- `shale-web/docs/phase-3i-evidence/pending-discard-320.png`
- `shale-web/docs/phase-3i-evidence/pending-failure-1280.png`
- `shale-web/docs/phase-3i-evidence/pending-failure-320.png`
- `shale-web/docs/phase-3i-evidence/rejection-1280.png`
- `shale-web/docs/phase-3i-evidence/rejection-320.png`
- `shale-web/docs/phase-3i-evidence/success-1280.png`
- `shale-web/docs/phase-3i-evidence/success-320.png`
- `shale-web/docs/phase-3i-evidence/test-selection.md`
- `shale-web/docs/phase-3i-evidence/unusable-1280.png`
- `shale-web/docs/phase-3i-evidence/unusable-320.png`
- `shale-web/docs/phase-3i-evidence/validation-1280.png`
- `shale-web/docs/phase-3i-evidence/validation-320.png`
- `shale-web/docs/phase-3i-review.md`
- `shale-web/src/App.tsx`
- `shale-web/src/DetailDraftProtection.tsx`
- `shale-web/src/detailDraftProtection.css`
- `shale-web/src/organizationDraftApp.test.tsx`

### Selected areas and reasons
- **server**
  - shale-web/docs/phase-3i-evidence/audit-production.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/audit.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/conflict-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/conflict-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/dialog-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/dialog-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/failed-clear logout-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/failed-clear logout-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/failed-clear rejection-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/failed-clear rejection-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/logout-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/logout-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/navigation-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/navigation-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/network-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/network-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/pending-discard-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/pending-discard-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/pending-failure-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/pending-failure-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/rejection-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/rejection-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/success-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/success-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/test-selection.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/unusable-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/unusable-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/validation-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-evidence/validation-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3i-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/DetailDraftProtection.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/detailDraftProtection.css: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/organizationDraftApp.test.tsx: Server, API, authentication endpoint, or web consumer changed.

**Full-suite escalation:** no
**Selected modules:** shale-server
**Modified test classes:** none
**Maximum generated command length:** 123 / 7000
**Complete owned classes (manual/advisory):** 32

### Selected test reasons
- `com.shale.server.controller.AuthControllerTest`
  - Classification: blocking_smoke, critical
  - Mapping: area:server
  - Changed path: `shale-web/docs/phase-3i-evidence/audit-production.json`
  - Changed path: `shale-web/docs/phase-3i-evidence/audit.json`
  - Changed path: `shale-web/docs/phase-3i-evidence/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3i-evidence/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3i-evidence/conflict-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/conflict-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/dialog-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/dialog-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/failed-clear logout-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/failed-clear logout-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/failed-clear rejection-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/failed-clear rejection-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/logout-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/logout-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/navigation-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/navigation-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/network-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/network-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/pending-discard-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/pending-discard-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/pending-failure-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/pending-failure-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/rejection-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/rejection-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/success-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/success-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/test-selection.md`
  - Changed path: `shale-web/docs/phase-3i-evidence/unusable-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/unusable-320.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/validation-1280.png`
  - Changed path: `shale-web/docs/phase-3i-evidence/validation-320.png`
  - Changed path: `shale-web/docs/phase-3i-review.md`
  - Changed path: `shale-web/src/App.tsx`
  - Changed path: `shale-web/src/DetailDraftProtection.tsx`
  - Changed path: `shale-web/src/detailDraftProtection.css`
  - Changed path: `shale-web/src/organizationDraftApp.test.tsx`

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
