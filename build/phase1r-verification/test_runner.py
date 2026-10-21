"""Closed evidence protocol tests; no SQL or credentials."""
import contextlib
import importlib.util
import io
from pathlib import Path
from types import SimpleNamespace
import unittest

spec = importlib.util.spec_from_file_location("phase1r_runner", Path(__file__).with_name("run.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class EvidenceProtocolTest(unittest.TestCase):
    def report(self, lines, code=0):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            status = runner.report(SimpleNamespace(stdout="\n".join(lines), returncode=code))
        return status, output.getvalue()

    def test_all_scenarios_and_zero_exit_required(self):
        self.assertEqual(0, self.report(runner.PASSES)[0])
        for missing in runner.PASSES:
            self.assertEqual(1, self.report(runner.PASSES - {missing})[0])
        self.assertEqual(1, self.report(runner.PASSES, 1)[0])

    def test_cleanup_failure_after_pass_is_not_success(self):
        self.assertEqual(1, self.report([*runner.PASSES, "AUTH_VERIFY FAIL cleanup"])[0])

    def test_arbitrary_child_output_is_discarded(self):
        status, output = self.report([*runner.PASSES, "password=synthetic", "PHASE1R PASS arbitrary", "AUTH_VERIFY FAIL arbitrary"])
        self.assertEqual(0, status)
        self.assertNotIn("synthetic", output)
        self.assertNotIn("arbitrary", output)


if __name__ == "__main__":
    unittest.main()
