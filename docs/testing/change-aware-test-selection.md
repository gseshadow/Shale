# Change-aware test selection

Shale intentionally does not run every historical test for every routine change. The repository owns
the selection policy in `build/test-selection/test-areas.json`; CI YAML only supplies the base/head
diff and executes the emitted commands.

## The four modes

### 1. Critical default

```bash
mvn test
```

The parent POM supplies `build/test-selection/critical-tests.txt` to Maven Surefire 3.2.5. This suite
contains only release-blocking security, tenant/session, audit transaction, essential wiring,
updater-integrity, and high-value persistence contracts. Ordinary `clean package`, including the
Windows release reactor, inherits this default. Rendered JavaFX geometry is excluded.

### 2. Changed-code selection

```bash
python build/test-selection/select_tests.py --base origin/codex/latest --head HEAD --format markdown
```

The selector reports changed paths, affected Maven modules, test patterns, exact commands, selection
reasons, skipped areas, directly modified test classes, and full-suite escalation. It fails safely to
the full suite for a parent/module POM, test infrastructure, workflow, Codex selection rules, or an
unknown production path. Documentation-only changes have no affected-area Maven command; CI still
runs the independently required critical gate.

Use `--run` to execute the displayed Python contracts, selected affected-area/full suite, and critical
suite. CI deliberately executes the critical command first, followed by the affected command.

### 3. Explicit feature selection

Cross-platform commands (Python 3):

```bash
python build/test-selection/select_tests.py --area calendar --run
python build/test-selection/select_tests.py --area contacts --run
python build/test-selection/select_tests.py --area tasks --run
python build/test-selection/select_tests.py --area cases --run
python build/test-selection/select_tests.py --area ui-rendered --run
```

On Windows, `py` may replace `python`. The emitted Maven command selects the owning modules with
`-pl ... -am`, a repository-maintained group of class patterns, and
`-Dsurefire.failIfNoSpecifiedTests=false`, so upstream modules without matching tests do not fail.
Pass `--area` more than once for an intentional combined selection. Use `--format markdown` to obtain
a review-friendly explanation without running anything.

### 4. Explicit full suite

```bash
mvn -Pall-tests test
```

The `all-tests` profile replaces the critical include file with Surefire's historical discovery
patterns. Every existing test remains reachable. The separate **Informational Full Maven Suite**
workflow supports manual dispatch and a weekly schedule; its job is informational and does not form
the ordinary Relevant Test Gate.

## Path-to-area map

| Changed path or contract | Selected area(s) |
| --- | --- |
| Calendar Java/FXML/local CSS | `calendar` |
| Contact Java/FXML/data/migrations | `contacts` |
| Tasks or My Shale task code/FXML | `tasks` |
| Case view/domain/intake/FXML | `cases` |
| Organizations | `organizations` |
| Reports/export | `reports` |
| Settings/team/user administration | `settings-team` |
| Updater and desktop updater launch | `updater` |
| Server/API/web consumer | `server` |
| Build, packaging, release scripts | `build-scripts` Python contracts |
| Global UI CSS/shared components | `ui-rendered` |
| Shared core DTO/service port | Direct consumers: Cases, Contacts, Tasks, Organizations, Reports, Server |
| Security/RLS/audit/session infrastructure | `security-data` plus the always-run critical suite |
| Notification/live-update code | `notifications` |
| A module-local test source | Exact fully qualified modified test class, plus any named feature mapping |
| Parent/module POM, workflow, selector/rules | Full-suite escalation |
| Documentation only | No affected-area Maven command unless a documentation contract maps it |
| Unknown production path | Full-suite escalation |

The JSON manifest is authoritative and more precise than this overview.

## CI behavior and reports

The required **Relevant Test Gate** checks out full history, compares PR base/head or push before/after,
runs the critical suite, runs selected build contracts and feature tests, and uses `-Pall-tests` only
on escalation. Its job summary includes the selector explanation and module/reactor Surefire counts.
Surefire/Failsafe XML and the selection summary are archived even after failures. Concurrency
cancellation remains enabled.

The scheduled full suite uses `continue-on-error` so unrelated releases are not blocked. A scheduled
failure creates a visible workflow warning and issue-candidate summary, with reports retained for 30
days.

## Maintaining the inventories

When adding or renaming a test, update feature patterns or the critical manifest only when the
contract warrants it. Regenerate and check the review inventory:

```bash
python build/test-selection/inventory.py --write
python build/test-selection/inventory.py --check
python -m unittest build/test-selection/test_select_tests.py
```

`docs/testing/full-suite-only-inventory.md` shows critical ownership, feature ownership, unreachable
risk, and full-suite-only candidates. Its category is triage—not permission to delete a test.
