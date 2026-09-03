# Change-aware test selection

Shale intentionally does not run every historical test for every routine change. The repository owns
the selection policy in `build/test-selection/test-areas.json`; CI YAML only supplies the base/head
diff and executes the emitted commands.

## Validation modes

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

The selector reports changed paths, affected Maven modules, blocking test classes, exact commands,
selection rationale, skipped areas, directly modified test classes, complete ownership counts, and
informational full-suite escalation.
It recommends the full suite for information when a parent/module POM, test infrastructure, workflow,
Codex selection rule, or unknown production path changes. That historical result is never part of the
required gate. Documentation-only changes have no affected-area Maven command; CI still
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
python build/test-selection/select_tests.py --area ui-presentation --run
python build/test-selection/select_tests.py --area ui-behavior --run
```

On Windows, `py` may replace `python`. The emitted Maven command selects the owning modules with
`-pl ... -am`, repository-maintained explicit or narrowly bounded test classes, and
`-Dsurefire.failIfNoSpecifiedTests=false`, so upstream modules without matching tests do not fail.
Pass `--area` more than once for an intentional combined selection. Use `--format markdown` to obtain
a review-friendly explanation without running anything.

The selector retains a readable command string for logs and job summaries, but execution always uses
structured process arguments. In particular, the complete comma-separated `-Dtest=...` selection is
one Maven argument; CI and `--run` never pass the display string through `Invoke-Expression`, `eval`,
`cmd /c`, or a shell interpreter.

Area ownership and blocking execution are separate contracts. Automatic PR selection uses, in order,
directly modified tests, explicit narrow file-to-test mappings, and each affected area's small
`blocking_smoke_patterns` manifest. It never expands the area's complete `ownership_patterns` list.
The separately maintained critical suite still runs through `mvn test`. Complete feature ownership is
available through explicit `--area` execution for manual/advisory or periodic coverage; developers do
not add selector exceptions or focused change-set declarations for ordinary feature work.

If complete ownership is larger than the former 50-class automatic limit, the selector reports that
count and the manual area command without failing the PR or scheduling the broad list. Structured Maven
commands remain below a conservative 7,000-character Windows limit and explicitly requested legitimate
inventories are split deterministically into module-local batches without truncation. The
critical safety manifest is never serialized onto the command line: it remains the separate `mvn test`
invocation supplied through the parent POM.

The selector writes an authoritative JSON execution plan for CI. The repository-owned
`run_selection.py` runner validates that plan, resolves `mvn.cmd` explicitly on Windows, logs the
display-only command and every indexed argument, and launches Maven with
`subprocess.run(argv, shell=False, check=True)`. PowerShell never reconstructs or transports dynamic
Maven argument arrays.

### 4. Explicit full suite

```bash
mvn -Pall-tests test
```

The `all-tests` profile replaces the critical include file with Surefire's historical discovery
patterns. Every existing test remains reachable. The separate **Informational Full Maven Suite**
workflow supports manual dispatch and a weekly schedule; its job is informational and does not form
the ordinary Relevant Test Gate.

### 5. Advisory JavaFX visual suite

```bash
mvn -Pui-visual test
```

This manually and periodically runnable profile owns tests coupled to rendered dimensions, JavaFX
skins/internal nodes, viewports and scrollbars, popup/window timing or containment, platform text
measurement, clipping, and padding-derived geometry. Its separately named workflow uses non-blocking
semantics and archives reports. Changed paths never select `ui-visual-advisory`; request it explicitly.

## Path-to-area map

| Changed path or contract | Selected area(s) |
| --- | --- |
| Calendar Java | `calendar`, and `ui-behavior` for UI controllers/components |
| Contact Java/FXML/data/migrations | `contacts` |
| Tasks or My Shale task code/FXML | `tasks` |
| Case view/domain/intake/FXML | `cases` |
| Organizations | `organizations` |
| Reports/export | `reports` |
| Settings/team/user administration | `settings-team` |
| Updater and desktop updater launch | `updater` |
| Server/API/web consumer | `server` |
| Build, packaging, release scripts | `build-scripts` Python contracts |
| Any JavaFX CSS | `ui-presentation` (static contracts only) |
| Any JavaFX FXML | `ui-presentation` plus `ui-fxml-structure` (loading/ID/node/handler structure only) |
| JavaFX controller/shared-component Java | relevant feature area plus `ui-behavior` |
| Rendered geometry/skin checks | `ui-visual-advisory`, explicit/manual only |
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
runs package/compilation validation, selector contracts, the critical suite, and selected feature
tests. Full-suite escalation is reported as an informational command and is not executed by this
required job. Its summary includes the selector explanation and module/reactor Surefire counts.
Surefire/Failsafe XML and the selection summary are archived even after failures. Concurrency
cancellation remains enabled.

The scheduled full suite uses `continue-on-error` so unrelated releases are not blocked. A scheduled
failure creates a visible workflow warning and issue-candidate summary, with reports retained for 30
days.

## Maintaining the inventories

When adding or renaming a test, update explicit feature ownership or the fully qualified critical
class manifest only when the
contract warrants it. Regenerate and check the review inventory:

```bash
python build/test-selection/inventory.py --write
python build/test-selection/inventory.py --check
python -m unittest build/test-selection/test_select_tests.py
```

`docs/testing/full-suite-only-inventory.md` shows critical ownership, feature ownership, unreachable
risk, and full-suite-only candidates. Its category is triage—not permission to delete a test.
