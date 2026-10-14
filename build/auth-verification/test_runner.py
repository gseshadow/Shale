import importlib.util
import contextlib
import io
from pathlib import Path
import subprocess
import unittest

spec = importlib.util.spec_from_file_location("auth_runner", Path(__file__).with_name("run.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

class LocalAuthRunnerSafetyTest(unittest.TestCase):
    def test_opt_in_and_loopback_port_bounds(self):
        runner.validate({}, 14333, runner.OPT_IN)
        for port in (0, 1023, 65536):
            with self.assertRaises(ValueError): runner.validate({}, port, runner.OPT_IN)
        with self.assertRaises(ValueError): runner.validate({}, 14333, "production")

    def test_rejects_all_application_connection_sources_and_implicit_arguments(self):
        for name in ("SHALE_APP_DB_PASSWORD", "SHALE_RT_USER", "SHALE_AUTH_TOKEN_SECRET",
                     "SHALE_SERVER_API_BASE_URL", "SPRING_DATASOURCE_URL", "SQLAZURECONNSTR_MAIN",
                     "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_ARGS", "MAVEN_OPTS"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                runner.validate({name: "unusable-synthetic"}, 14333, runner.OPT_IN)

    def test_verifier_namespace_is_not_an_application_auth_source(self):
        runner.validate({"SHALE_AUTH_VERIFY_OPT_IN": runner.OPT_IN}, 14333, runner.OPT_IN)

    def report(self, output, code=1):
        captured = io.StringIO()
        with contextlib.redirect_stdout(captured):
            result = runner.report_result(subprocess.CompletedProcess([], code, output))
        return result, captured.getvalue()

    def test_independent_sql_and_cleanup_failures_do_not_become_build_failure(self):
        events = ("AUTH_VERIFY FAIL remembered_rotation\n"
                  "AUTH_VERIFY DETAIL remembered_rotation jti_identity assertion\n"
                  "AUTH_VERIFY FAIL cleanup\n"
                  "AUTH_VERIFY DETAIL cleanup marker_identity guard\n")
        self.assertEqual((1, events), self.report("private driver output\n" + events))
        # A child returning zero cannot hide reported scenario/cleanup failures.
        self.assertEqual((1, events), self.report(events, 0))

    def test_closed_vocabulary_drops_injected_values_and_bounds_repeated_events(self):
        events = ("AUTH_VERIFY FAIL cleanup\nAUTH_VERIFY DETAIL cleanup marker_identity guard\n")
        injected = ("AUTH_VERIFY FAIL private_secret\n"
                    "AUTH_VERIFY DETAIL cleanup marker_identity private_secret\n"
                    "AUTH_VERIFY DETAIL cleanup private_secret guard\n"
                    "AUTH_VERIFY DETAIL remembered_rotation marker_identity guard\n"
                    "AUTH_VERIFY FAIL cleanup private_secret\n"
                    "AUTH_VERIFY PASS private_secret\n"
                    "AUTH_VERIFY DETAIL cleanup marker_identity guard private_secret\n")
        self.assertEqual((1, events), self.report(injected + events * 100))

    def test_build_failure_without_known_scenario_and_missing_or_duplicate_passes_fail(self):
        for text in ("private compiler output", "AUTH_VERIFY PASS credential_identity\n" * 3):
            code, output = self.report(text, 0)
            self.assertEqual(1, code)
            self.assertIn("AUTH_VERIFY FAIL build_or_prerequisite\n", output)
            self.assertNotIn("private", output)
        passes = "".join("AUTH_VERIFY PASS " + phase + "\n" for phase in sorted(runner.SCENARIOS))
        self.assertEqual((0, passes), self.report(passes, 0))
        self.assertEqual((1, passes + "AUTH_VERIFY FAIL build_or_prerequisite\n"), self.report(passes, 1))

    def test_cleanup_failure_after_all_scenarios_pass_is_still_failure(self):
        passes = "".join("AUTH_VERIFY PASS " + phase + "\n" for phase in sorted(runner.SCENARIOS))
        events = passes + "AUTH_VERIFY FAIL cleanup\nAUTH_VERIFY DETAIL cleanup database_drop sql_permission\n"
        self.assertEqual((1, events), self.report(events))

if __name__ == "__main__": unittest.main()
