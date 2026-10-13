"""Protect the local-only, explicit-input boundary without opening SQL connections."""
import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("case_read_runner", Path(__file__).with_name("run.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class RunnerSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.schema = Path(self.temp.name) / "schema.sql"
        self.schema.write_text("CREATE SCHEMA sec;", encoding="utf-8")
        self.digest = hashlib.sha256(self.schema.read_bytes()).hexdigest()

    def check(self, **overrides):
        args = dict(schema=self.schema, digest=self.digest, source="reviewed offline definition snapshot",
                    port=14333, opt_in=runner.OPT_IN, env={})
        args.update(overrides)
        return runner.validate(**args)

    def test_explicit_input_and_hash_are_required(self):
        self.assertEqual(self.schema.resolve(), self.check())
        for args in ({"opt_in": "yes"}, {"digest": "0" * 64}, {"source": ""}, {"port": 443}, {"port": 65536}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.check(**args)

    def test_application_credentials_and_implicit_arguments_fail_closed(self):
        for key in ("SHALE_APP_DB_URL", "SHALE_RT_PASS", "SHALE_AUTH_TOKEN_SECRET", "SHALE_SUGGESTION_TEST_JDBC_URL",
                    "SPRING_DATASOURCE_URL", "SQLAZURECONNSTR_DB", "JAVA_TOOL_OPTIONS", "MAVEN_ARGS"):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.check(env={key: "not-a-credential"})
        self.check(env={"SHALE_CASE_VERIFY_ADMIN_PASSWORD": "synthetic", "HTTPS_PROXY": "inherited"})

    def test_changed_or_non_sql_inputs_are_rejected(self):
        self.schema.write_text("changed", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.check()
        with self.assertRaises(FileNotFoundError):
            self.check(schema=Path(self.temp.name) / "missing.sql")

    def test_only_closed_scenario_events_can_be_printed(self):
        text = "password=x\nCASE_READ_VERIFY PASS cleanup\nCASE_READ_VERIFY PASS unsafe/value\n"
        self.assertEqual([("PASS", "cleanup")], runner.EVENT.findall(text))


if __name__ == "__main__":
    unittest.main()
