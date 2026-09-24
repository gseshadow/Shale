# Case Overview Phase 2 visual QA

## Status

**Blocked — runtime inspection has not been completed.**

This record intentionally does not approve the assembled Case Overview or infer visual defects from
FXML/CSS source inspection. The required JavaFX runtime review must be repeated in an environment that
can resolve the Maven build and provide a graphical desktop.

## Attempted environment

- Date: 2026-09-17
- Runtime: Linux container, Java 21 repository checkout
- Display: no `DISPLAY` or `WAYLAND_DISPLAY`; Xvfb is not installed
- Build: no prebuilt module artifacts were present
- Development data: no local application configuration was present, so no credentials were read or
  displayed and no shared data was mutated

The normal Maven entry point could not be prepared because Maven Central returned HTTP 403 while
resolving `org.springframework.boot:spring-boot-dependencies:3.3.4`. Offline compilation was also
unavailable because required Maven plugins were not cached.

## Pre-edit defect inventory

No visual defects were recorded. Runtime observation was unavailable, and speculative CSS/FXML
changes are prohibited for this pass.

| Category | Finding |
| --- | --- |
| Critical usability defect | Not inspected at runtime |
| Responsive/layout defect | Not inspected at runtime |
| Theme defect | Not inspected at runtime |
| Consistency defect | Not inspected at runtime |
| Minor polish issue | Not inspected at runtime |
| Intentional difference from the mockup | Not assessed; no reference image was available in the checkout |

## Screenshot matrix

The intended output directory is `target/visual-qa/case-overview/`. No screenshots were generated,
and no screenshot placeholders are committed.

| Theme | Client size | Scale | Result |
| --- | --- | --- | --- |
| Light | 1920 × 1080 | 100% | Unavailable: no graphical display/runtime build |
| Light | 1600 × 900 | 100% | Unavailable: no graphical display/runtime build |
| Light | 1366 × 768 | 100% | Unavailable: no graphical display/runtime build |
| Light | Documented minimum/narrowest supported width | 100% | Unavailable: no graphical display/runtime build |
| Dark | 1920 × 1080 | 100% | Unavailable: no graphical display/runtime build |
| Dark | 1600 × 900 | 100% | Unavailable: no graphical display/runtime build |
| Dark | 1366 × 768 | 100% | Unavailable: no graphical display/runtime build |
| Dark | Documented minimum/narrowest supported width | 100% | Unavailable: no graphical display/runtime build |
| Light and dark | Representative sizes | 125% | Unavailable: no Windows display-scaling or rendered runtime |
| Light and dark | Representative sizes | 150% | Unavailable: no Windows display-scaling or rendered runtime |

## Checks completed

- Reviewed the Phase 1A–2D implementation history, Case Overview FXML, controller/component ownership,
  shared CSS owners, and existing Phase 2 contract tests.
- `python build/test-selection/validate_ui_resources.py` passed.
- `python -m unittest build/test-selection/test_select_tests.py` passed (24 tests).
- The clean-baseline `mvn test` command was attempted and blocked during Maven model resolution by the
  external HTTP 403 response described above.
- No production, business, persistence, authorization, schema, query, or test code was changed.

## Required follow-up

Run the normal JavaFX desktop application with authorized development configuration on a graphical
workstation. Inspect a representative case without mutating shared data, capture the complete matrix
under `target/visual-qa/case-overview/`, write the observed categorized defect list before editing,
apply only evidence-backed corrections, and then execute the focused Phase 2 contracts, advisory
rendered profile, full `mvn test`, resource validator, selector checks, and `git diff --check`.
