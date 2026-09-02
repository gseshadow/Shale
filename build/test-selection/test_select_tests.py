import importlib.util
from pathlib import Path
import unittest
import fnmatch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).with_name("select_tests.py")
SPEC = importlib.util.spec_from_file_location("select_tests", SCRIPT)
SELECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SELECTOR)


class ChangeSelectorTest(unittest.TestCase):
    def selected(self, path):
        return SELECTOR.select([path])

    def test_contact_change_selects_contacts_but_not_calendar(self):
        result = self.selected("shale-ui/src/main/java/com/shale/ui/controller/ContactViewController.java")
        self.assertIn("contacts", result["selected_areas"])
        self.assertNotIn("calendar", result["selected_areas"])
        self.assertNotIn("*Calendar*Test", result["test_patterns"])
        self.assertFalse(result["full_suite"])

    def test_calendar_fxml_change_selects_structural_presentation_only(self):
        result = self.selected("shale-ui/src/main/resources/fxml/calendar.fxml")
        self.assertEqual(["ui-fxml-structure", "ui-presentation"], result["selected_areas"])
        self.assertIn("*FxmlLoadTest", result["test_patterns"])
        self.assertNotIn("*Calendar*Test", result["test_patterns"])

    def test_task_and_my_shale_change_selects_tasks(self):
        for path in ("shale-data/src/main/java/com/shale/data/dao/TaskDao.java",
                     "shale-ui/src/main/java/com/shale/ui/controller/MyShaleController.java"):
            with self.subTest(path=path):
                self.assertIn("tasks", self.selected(path)["selected_areas"])

    def test_global_css_selects_static_presentation_not_rendered_ui(self):
        result = self.selected("shale-ui/src/main/resources/css/app.css")
        self.assertEqual(["ui-presentation"], result["selected_areas"])
        self.assertIn("*CssContractTest", result["test_patterns"])
        self.assertNotIn("*Runtime*Test", result["test_patterns"])
        for unrelated in ("*Case*Test", "*Calendar*Test", "*Task*Test", "*Layout*Test"):
            self.assertNotIn(unrelated, result["test_patterns"])

    def test_tab_color_css_does_not_select_feature_or_popup_geometry(self):
        result = self.selected("shale-ui/src/main/resources/css/foundation/tabs.css")
        self.assertEqual(["ui-presentation"], result["selected_areas"])
        self.assertFalse(any(token in result["selected_command"] for token in
                             ("Case", "Calendar", "Task", "Popup", "Layout", "Rendering")))

    def test_controller_change_selects_feature_and_ui_behavior(self):
        result = self.selected("shale-ui/src/main/java/com/shale/ui/controller/CaseController.java")
        self.assertIn("cases", result["selected_areas"])
        self.assertIn("ui-behavior", result["selected_areas"])

    def test_visual_advisory_is_explicit_only_and_manually_runnable(self):
        implicit = self.selected("shale-ui/src/main/resources/css/app.css")
        self.assertNotIn("ui-visual-advisory", implicit["selected_areas"])
        explicit = SELECTOR.select([], ["ui-visual-advisory"])
        self.assertEqual("mvn -Pui-visual test", explicit["selected_command"])

    def test_security_and_rls_select_critical_affected_data_coverage(self):
        result = self.selected("shale-data/src/main/java/com/shale/data/auth/AuthUserLifecycle.java")
        self.assertIn("security-data", result["selected_areas"])
        self.assertEqual("mvn test", result["critical_command"])

    def test_modified_test_class_is_selected_exactly(self):
        path = "shale-ui/src/test/java/com/shale/ui/controller/ReportsControllerLifecycleTest.java"
        result = self.selected(path)
        self.assertEqual(["com.shale.ui.controller.ReportsControllerLifecycleTest"], result["modified_test_classes"])
        self.assertIn("com.shale.ui.controller.ReportsControllerLifecycleTest", result["test_patterns"])
        self.assertIn("-Dtest=com.shale.ui.controller.ReportsControllerLifecycleTest", result["focused_command"])
        self.assertLess(result["commands"].index(result["focused_command"]), result["commands"].index(result["selected_command"]))

    def test_documentation_only_change_has_no_affected_suite(self):
        result = self.selected("docs/web-api-local-smoke-test.md")
        self.assertFalse(result["selected_areas"])
        self.assertFalse(result["full_suite"])
        self.assertEqual("", result["selected_command"])

    def test_parent_pom_and_selector_changes_escalate(self):
        for path in ("pom.xml", "shale-ui/pom.xml", "build/test-selection/test-areas.json"):
            with self.subTest(path=path):
                result = self.selected(path)
                self.assertTrue(result["full_suite"])
                self.assertEqual("", result["selected_command"])
                self.assertEqual("mvn -Pall-tests test", result["informational_command"])

    def test_unknown_production_path_escalates(self):
        result = self.selected("shale-ui/src/main/java/com/shale/ui/mystery/NewSubsystem.java")
        self.assertTrue(result["full_suite"])
        self.assertEqual("", result["selected_command"])
        self.assertEqual("mvn -Pall-tests test", result["informational_command"])
        self.assertIn("unknown production path", result["escalation_reasons"][0])

    def test_explicit_feature_selection_uses_safe_reactor_option(self):
        result = SELECTOR.select([], ["calendar"])
        self.assertIn("-Dsurefire.failIfNoSpecifiedTests=false", result["selected_command"])
        self.assertIn("-pl shale-core,shale-data,shale-ui -am", result["selected_command"])

    def test_every_test_is_reachable_by_historical_full_suite_pattern(self):
        root = SCRIPT.parents[2]
        patterns = [line.strip() for line in SCRIPT.with_name("all-tests.txt").read_text(encoding="utf-8").splitlines()
                    if line.strip() and not line.startswith("#")]
        tests = list(root.glob("shale-*/src/test/java/**/*.java"))
        unreachable = [str(path.relative_to(root)) for path in tests
                       if "@Test" in path.read_text(encoding="utf-8") and not any(fnmatch.fnmatchcase(path.name, Path(pattern).name) for pattern in patterns)]
        self.assertEqual([], unreachable)

    def test_critical_manifest_is_small_and_references_existing_tests(self):
        root = SCRIPT.parents[2]
        critical = [Path(line.strip()).stem for line in SCRIPT.with_name("critical-tests.txt").read_text(encoding="utf-8").splitlines()
                    if line.strip() and not line.startswith("#")]
        existing = {path.stem for path in root.glob("shale-*/src/test/java/**/*Test.java")}
        self.assertTrue(set(critical) <= existing)
        self.assertLessEqual(len(critical) / len(existing), 0.25)

    def test_parent_pom_uses_critical_default_and_full_recovery_profile(self):
        root = SCRIPT.parents[2]
        pom = ET.parse(root / "pom.xml").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        properties = pom.find("m:properties", ns)
        self.assertTrue(properties.find("m:shale.test.includesFile", ns).text.endswith("critical-tests.txt"))
        profiles = {item.find("m:id", ns).text: item for item in pom.findall("m:profiles/m:profile", ns)}
        full_value = profiles["all-tests"].find("m:properties/m:shale.test.includesFile", ns).text
        self.assertTrue(full_value.endswith("all-tests.txt"))
        visual_value = profiles["ui-visual"].find("m:properties/m:shale.test.includesFile", ns).text
        self.assertTrue(visual_value.endswith("ui-visual-advisory-tests.txt"))
        surefire = [plugin for plugin in pom.findall("m:build/m:plugins/m:plugin", ns)
                    if plugin.find("m:artifactId", ns).text == "maven-surefire-plugin"]
        self.assertEqual(1, len(surefire))
        self.assertEqual("${shale.test.includesFile}", surefire[0].find("m:configuration/m:includesFile", ns).text)
        self.assertEqual("${shale.test.excludesFile}", surefire[0].find("m:configuration/m:excludesFile", ns).text)

    def test_ci_has_relevant_gate_and_separate_informational_full_suite(self):
        root = SCRIPT.parents[2]
        gate = (root / ".github/workflows/maven-test-gate.yml").read_text(encoding="utf-8")
        full = (root / ".github/workflows/maven-full-suite.yml").read_text(encoding="utf-8")
        visual = (root / ".github/workflows/maven-ui-visual.yml").read_text(encoding="utf-8")
        self.assertIn("name: Relevant Test Gate", gate)
        self.assertIn("python build/test-selection/select_tests.py --base", gate)
        self.assertIn("run: mvn test", gate)
        self.assertIn("actions/upload-artifact@v4", gate)
        self.assertIn("workflow_dispatch:", full)
        self.assertIn("schedule:", full)
        self.assertIn("mvn -Pall-tests test", full)
        self.assertIn("continue-on-error: true", full)
        self.assertIn("name: Advisory JavaFX Visual Suite", visual)
        self.assertIn("mvn -Pui-visual test", visual)
        self.assertIn("continue-on-error: true", visual)
        self.assertNotIn("informational_command", gate)


if __name__ == "__main__":
    unittest.main()
