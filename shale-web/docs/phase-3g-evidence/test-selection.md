## Shale relevant test selection

### Changed paths
- `docs/architecture/shale-web-v2-architecture-roadmap.md`
- `shale-web/docs/phase-3g-evidence/audit-production.json`
- `shale-web/docs/phase-3g-evidence/audit.json`
- `shale-web/docs/phase-3g-evidence/browser-observations.json`
- `shale-web/docs/phase-3g-evidence/browser-review.cjs`
- `shale-web/docs/phase-3g-evidence/dialog-1280.png`
- `shale-web/docs/phase-3g-evidence/dialog-320.png`
- `shale-web/docs/phase-3g-evidence/logout-1280.png`
- `shale-web/docs/phase-3g-evidence/logout-320.png`
- `shale-web/docs/phase-3g-evidence/navigation-1280.png`
- `shale-web/docs/phase-3g-evidence/navigation-320.png`
- `shale-web/docs/phase-3g-evidence/pending-discard-1280.png`
- `shale-web/docs/phase-3g-evidence/pending-discard-320.png`
- `shale-web/docs/phase-3g-evidence/rejection-1280.png`
- `shale-web/docs/phase-3g-evidence/rejection-320.png`
- `shale-web/docs/phase-3g-evidence/test-selection.md`
- `shale-web/docs/phase-3g-review.md`
- `shale-web/src/App.test.tsx`
- `shale-web/src/App.tsx`
- `shale-web/src/ContactDraftProtection.tsx`
- `shale-web/src/ContactValueInput.test.tsx`
- `shale-web/src/ContactValueInput.tsx`
- `shale-web/src/contactDraftApp.test.tsx`
- `shale-web/src/contactDraftProtection.css`
- `shale-web/src/main.tsx`
- `shale-web/src/sessionRejectionApp.test.tsx`

### Selected areas and reasons
- **server**
  - shale-web/docs/phase-3g-evidence/audit-production.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/audit.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/browser-observations.json: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/browser-review.cjs: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/dialog-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/dialog-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/logout-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/logout-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/navigation-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/navigation-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/pending-discard-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/pending-discard-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/rejection-1280.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/rejection-320.png: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-evidence/test-selection.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/docs/phase-3g-review.md: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/App.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/ContactDraftProtection.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/ContactValueInput.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/ContactValueInput.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/contactDraftApp.test.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/contactDraftProtection.css: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/main.tsx: Server, API, authentication endpoint, or web consumer changed.
  - shale-web/src/sessionRejectionApp.test.tsx: Server, API, authentication endpoint, or web consumer changed.

**Full-suite escalation:** no
**Selected modules:** shale-server
**Modified test classes:** none
**Maximum generated command length:** 123 / 7000
**Complete owned classes (manual/advisory):** 32

### Selected test reasons
- `com.shale.server.controller.AuthControllerTest`
  - Classification: blocking_smoke, critical
  - Mapping: area:server
  - Changed path: `shale-web/docs/phase-3g-evidence/audit-production.json`
  - Changed path: `shale-web/docs/phase-3g-evidence/audit.json`
  - Changed path: `shale-web/docs/phase-3g-evidence/browser-observations.json`
  - Changed path: `shale-web/docs/phase-3g-evidence/browser-review.cjs`
  - Changed path: `shale-web/docs/phase-3g-evidence/dialog-1280.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/dialog-320.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/logout-1280.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/logout-320.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/navigation-1280.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/navigation-320.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/pending-discard-1280.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/pending-discard-320.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/rejection-1280.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/rejection-320.png`
  - Changed path: `shale-web/docs/phase-3g-evidence/test-selection.md`
  - Changed path: `shale-web/docs/phase-3g-review.md`
  - Changed path: `shale-web/src/App.test.tsx`
  - Changed path: `shale-web/src/App.tsx`
  - Changed path: `shale-web/src/ContactDraftProtection.tsx`
  - Changed path: `shale-web/src/ContactValueInput.test.tsx`
  - Changed path: `shale-web/src/ContactValueInput.tsx`
  - Changed path: `shale-web/src/contactDraftApp.test.tsx`
  - Changed path: `shale-web/src/contactDraftProtection.css`
  - Changed path: `shale-web/src/main.tsx`
  - Changed path: `shale-web/src/sessionRejectionApp.test.tsx`

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
