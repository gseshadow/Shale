# Local change-aware test selection

## GitHub automation policy

Automated GitHub testing is disabled by explicit project-owner decision. GitHub Actions does not run or require Maven, Python selector, static FXML/CSS, JavaFX, critical, affected, advisory, or full-suite tests; it does not publish or archive Surefire/Failsafe reports. Pull requests and releases are not blocked by a GitHub test check. Compilation and relevant testing are local developer/Codex responsibilities.

The repository retains `build/test-selection/` as an optional local planning and execution tool. It helps developers choose tests relevant to a change without requiring every historical test. It is not a CI contract and produces no GitHub step outputs.

## Local validation modes

### Critical default

```bash
mvn test
```

The parent POM supplies `build/test-selection/critical-tests.txt` to Maven Surefire. This remains a useful local high-value suite; it is not a GitHub or release gate.

### Changed-code selection

```bash
python build/test-selection/select_tests.py --base <base> --head HEAD --format markdown
```

The selector reports changed paths, affected modules, focused test classes, commands, rationale, and optional full-suite escalation. Add `--run` only when local execution is appropriate. Output and plan files remain available through `--output` and `--plan-output` for local tooling.

### Explicit feature selection

```bash
python build/test-selection/select_tests.py --area calendar --run
python build/test-selection/select_tests.py --area contacts --run
python build/test-selection/select_tests.py --area tasks --run
python build/test-selection/select_tests.py --area cases --run
python build/test-selection/select_tests.py --area ui-presentation --run
python build/test-selection/select_tests.py --area ui-behavior --run
```

The emitted Maven commands remain structured and shell-safe. Complete feature ownership can be requested explicitly; ordinary work should use the smallest relevant local checks.

### Optional full and visual suites

```bash
mvn -Pall-tests test
mvn -Pui-visual test
```

Both profiles remain optional manual local tools. The full profile exposes historically discoverable tests. The visual profile contains rendered JavaFX checks that depend on platform layout behavior. Neither runs on a schedule in GitHub, publishes GitHub artifacts, nor blocks a pull request or release.

## Local ownership guidance

`build/test-selection/test-areas.json` remains the authoritative local path-to-area map. Test classes and inventories remain in the repository; removing GitHub automation is not permission to delete useful tests. When adding or renaming a test, update local ownership only when its contract warrants inclusion. The inventory can still be maintained locally:

```bash
python build/test-selection/inventory.py --write
python build/test-selection/inventory.py --check
python -m unittest build/test-selection/test_select_tests.py
```
