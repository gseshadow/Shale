import importlib.util
from pathlib import Path
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

if __name__ == "__main__": unittest.main()
