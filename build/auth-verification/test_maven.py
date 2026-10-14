"""Protect both local launchers' process/credential boundary without SQL access.

Windows command construction is simulated unless the explicitly skipped native
Windows test can run on a Windows host. A fake batch wrapper never launches Java.
"""
import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


def load(folder):
    path = Path(__file__).resolve().parents[1] / folder / "run.py"
    spec = importlib.util.spec_from_file_location(folder, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


RUNNERS = (load("auth-verification"), load("case-read-verification"))
MAVEN = r"C:\Program Files\Apache\Maven\apache-maven-3.9.11\bin\mvn.CMD"
CMD = r"C:\Windows\System32\cmd.exe"
DIRECT_MAVEN = str(Path("installed-maven").resolve())


class MavenInvocationTest(unittest.TestCase):
    def test_windows_resolution_prefers_cmd_and_honors_explicit_space_path(self):
        for runner in RUNNERS:
            with self.subTest(runner=runner.__name__), mock.patch.object(runner, "WINDOWS", True):
                with mock.patch.object(runner.shutil, "which", return_value=MAVEN) as which:
                    self.assertEqual(MAVEN, runner.resolve_maven())
                    which.assert_called_once_with("mvn.cmd")
                with mock.patch.object(runner.shutil, "which", side_effect=[None, MAVEN]) as which:
                    self.assertEqual(MAVEN, runner.resolve_maven())
                    self.assertEqual([mock.call("mvn.cmd"), mock.call("mvn")], which.call_args_list)
                with mock.patch.object(runner.shutil, "which", return_value=MAVEN) as which:
                    self.assertEqual(MAVEN, runner.resolve_maven(MAVEN))
                    which.assert_called_once_with(MAVEN)

    def test_missing_maven_has_fixed_category_on_both_platforms(self):
        for runner in RUNNERS:
            for windows in (False, True):
                with self.subTest(runner=runner.__name__, windows=windows), mock.patch.object(
                        runner, "WINDOWS", windows), mock.patch.object(runner.shutil, "which", return_value=None):
                    with self.assertRaisesRegex(runner.LauncherFailure, "^maven_missing$"):
                        runner.resolve_maven()

    def test_windows_batch_quotes_space_paths_and_never_interpolates_credentials(self):
        for runner in RUNNERS:
            with self.subTest(runner=runner.__name__), mock.patch.object(runner, "WINDOWS", True), mock.patch.object(
                    runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "")) as run:
                env = {"SYSTEMROOT": r"C:\Windows", "PRIVATE_PASSWORD": 'private %!&|<>^" value'}
                arguments = ["-pl", "shale-server", r"-Dlogback.configurationFile=C:\Temp Space (local)\quiet.xml", "test"]
                runner.run_maven(MAVEN, arguments, Path("checkout with spaces"), env)
                command = run.call_args.args[0]
                self.assertEqual('"' + CMD + '" /d /s /v:off /c ""' + MAVEN +
                                 '" "-pl" "shale-server" "' + arguments[2] + '" "test""', command)
                self.assertNotIn(env["PRIVATE_PASSWORD"], command)
                self.assertEqual(env, run.call_args.kwargs["env"])
                self.assertFalse(run.call_args.kwargs["shell"])
                self.assertEqual(CMD, run.call_args.kwargs["executable"])
                self.assertEqual(subprocess.PIPE, run.call_args.kwargs["stdout"])
                self.assertEqual(subprocess.STDOUT, run.call_args.kwargs["stderr"])

    def test_batch_command_expansion_and_injection_fail_closed(self):
        for runner in RUNNERS:
            for token in ('%SECRET%', '!SECRET!', '"', '&whoami', '|whoami', '>file', '<file', '^', '\n', '\r', '\0'):
                for position in ("maven", "argument", "system_root"):
                    with self.subTest(runner=runner.__name__, token=repr(token), position=position), mock.patch.object(
                            runner, "WINDOWS", True), mock.patch.object(runner.subprocess, "run") as run:
                        maven = MAVEN + token if position == "maven" else MAVEN
                        # Keep the .cmd suffix when testing hostile executable paths.
                        if position == "maven":
                            maven = MAVEN[:-4] + token + ".cmd"
                        argument = "test" + token if position == "argument" else "test"
                        env = {"SystemRoot": "C:\\Windows" + (token if position == "system_root" else "")}
                        with self.assertRaisesRegex(runner.LauncherFailure, "^maven_invocation$"):
                            runner.run_maven(maven, [argument], Path("."), env)
                        run.assert_not_called()

    def test_linux_and_windows_exe_retain_direct_argv_invocation(self):
        for runner in RUNNERS:
            for windows, maven in ((False, "/opt/Maven Space/bin/mvn"), (True, r"C:\Tools Space\mvn.exe")):
                with self.subTest(runner=runner.__name__, windows=windows), mock.patch.object(
                        runner, "WINDOWS", windows), mock.patch.object(runner.subprocess, "run") as run:
                    runner.run_maven(maven, ["-version"], Path("."), {})
                    self.assertEqual([maven, "-version"], run.call_args.args[0])
                    self.assertFalse(run.call_args.kwargs["shell"])
                    self.assertIsNone(run.call_args.kwargs["executable"])

    @unittest.skipUnless(os.name != "nt", "direct POSIX invocation requires a POSIX host")
    def test_native_posix_process_receives_literal_arguments_and_private_environment(self):
        for runner in RUNNERS:
            with self.subTest(runner=runner.__name__):
                result = runner.run_maven(sys.executable,
                    ["-c", "import os,sys; print(sys.argv[1]); print(os.environ['PRIVATE_VALUE'])",
                     "literal space %!& argument"], Path("."), dict(os.environ, PRIVATE_VALUE="synthetic-private"))
                self.assertEqual(0, result.returncode)
                self.assertEqual(["literal space %!& argument", "synthetic-private"], result.stdout.splitlines())

    @unittest.skipUnless(os.name == "nt", "actual Windows cmd.exe execution requires a Windows host")
    def test_native_windows_batch_receives_space_argument_and_private_environment(self):
        with tempfile.TemporaryDirectory(prefix="shale space ") as work:
            maven = Path(work) / "Maven Space" / "mvn.CMD"
            maven.parent.mkdir()
            maven.write_text('@echo off\necho ARG=%~1\necho PRIVATE=%PRIVATE_VALUE%\nexit /b 0\n', encoding="utf-8")
            env = dict(os.environ, PRIVATE_VALUE="synthetic-private")
            for runner in RUNNERS:
                with self.subTest(runner=runner.__name__):
                    result = runner.run_maven(str(maven), ["space argument (local)"], Path(work), env)
                    self.assertEqual(0, result.returncode)
                    self.assertIn("ARG=space argument (local)", result.stdout)
                    self.assertIn("PRIVATE=synthetic-private", result.stdout)


class LauncherBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="shale test space ")
        self.addCleanup(self.temp.cleanup)
        self.schema = Path(self.temp.name) / "schema.sql"
        self.schema.write_text("CREATE SCHEMA sec;", encoding="utf-8")

    def args(self, runner):
        args = ["run.py", "--opt-in", runner.OPT_IN, "--port", "14333"]
        if runner is RUNNERS[1]:
            args += ["--schema", str(self.schema), "--schema-sha256",
                     hashlib.sha256(self.schema.read_bytes()).hexdigest(), "--schema-source", "synthetic test only"]
        return args

    def invoke(self, runner, env, results, maven=DIRECT_MAVEN):
        output = io.StringIO()
        with mock.patch.dict(os.environ, env, clear=True), mock.patch.object(sys, "argv", self.args(runner)), \
                mock.patch.object(runner, "WINDOWS", False), mock.patch.object(runner.shutil, "which", return_value=maven), \
                mock.patch.object(runner.subprocess, "run", side_effect=results) as run, \
                mock.patch("builtins.input", return_value="synthetic-user") as user, \
                mock.patch.object(runner.getpass, "getpass", return_value="private %!& value") as password, \
                contextlib.redirect_stdout(output):
            code = runner.main()
        return code, output.getvalue(), run, user, password

    def test_missing_maven_and_invocation_failure_precede_all_prompts(self):
        for runner in RUNNERS:
            for maven, results, category in ((None, [], "maven_missing"), ("/installed/mvn", [FileNotFoundError("private error")], "maven_invocation"),
                                             ("/installed/mvn", [subprocess.CompletedProcess([], 1, "private output")], "maven_invocation")):
                with self.subTest(runner=runner.__name__, category=category):
                    code, output, run, user, password = self.invoke(runner, {}, results, maven)
                    self.assertNotEqual(0, code)
                    prefix = "AUTH_VERIFY" if runner is RUNNERS[0] else "CASE_READ_VERIFY"
                    self.assertEqual(prefix + " FAIL " + category + "\n", output)
                    user.assert_not_called()
                    password.assert_not_called()
                    if maven is None:
                        run.assert_not_called()

    def test_prohibited_environment_is_sanitized_before_tooling_or_prompts(self):
        for runner in RUNNERS:
            for name in ("SHALE_AUTH_TOKEN_SECRET", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS"):
                with self.subTest(runner=runner.__name__, name=name):
                    code, output, run, user, password = self.invoke(runner, {name: "private value"}, [])
                    prefix = "AUTH_VERIFY" if runner is RUNNERS[0] else "CASE_READ_VERIFY"
                    self.assertEqual(prefix + " FAIL prohibited_environment\n", output)
                    self.assertNotEqual(0, code)
                    run.assert_not_called()
                    user.assert_not_called()
                    password.assert_not_called()

    def test_quiet_success_supplies_credentials_only_to_private_child_environment(self):
        for runner in RUNNERS:
            prefix = "AUTH_VERIFY" if runner is RUNNERS[0] else "CASE_READ_VERIFY"
            events = ("AUTH_VERIFY PASS credential_identity\nAUTH_VERIFY PASS remembered_rotation\nAUTH_VERIFY PASS runtime_isolation\n"
                      if runner is RUNNERS[0] else "CASE_READ_VERIFY PASS cleanup\n")
            with self.subTest(runner=runner.__name__):
                code, output, run, user, password = self.invoke(runner, {}, [subprocess.CompletedProcess([], 0, "private version"),
                                                       subprocess.CompletedProcess([], 0, "private diagnostic\n" + events)])
                self.assertEqual(0, code)
                self.assertIn(events, output)
                self.assertNotIn("private", output)
                self.assertEqual(2, run.call_count)
                probe, harness = run.call_args_list
                self.assertEqual([DIRECT_MAVEN, "-version"], probe.args[0])
                self.assertFalse(any(key.endswith("ADMIN_PASSWORD") for key in probe.kwargs["env"]))
                namespace = "SHALE_AUTH_VERIFY" if runner is RUNNERS[0] else "SHALE_CASE_VERIFY"
                self.assertEqual("private %!& value", harness.kwargs["env"][namespace + "_ADMIN_PASSWORD"])
                self.assertEqual("14333", harness.kwargs["env"][namespace + "_PORT"])
                self.assertEqual(runner.OPT_IN, harness.kwargs["env"][namespace + "_OPT_IN"])
                self.assertNotIn("private %!& value", str(harness.args[0]))
                self.assertNotIn(namespace + "_ADMIN_PASSWORD", os.environ)
                if runner is RUNNERS[0]:
                    logback = next(arg.split("=", 1)[1] for arg in harness.args[0] if arg.startswith("-Dlogback"))
                    self.assertFalse(Path(logback).exists(), "temporary quiet config must be cleaned up")

    def test_child_start_failure_after_prompts_remains_sanitized(self):
        for runner in RUNNERS:
            with self.subTest(runner=runner.__name__):
                code, output, run, user, password = self.invoke(runner, {}, [subprocess.CompletedProcess([], 0, ""), OSError("private child failure")])
                prefix = "AUTH_VERIFY" if runner is RUNNERS[0] else "CASE_READ_VERIFY"
                self.assertEqual(prefix + " FAIL maven_invocation\n", output)
                self.assertNotEqual(0, code)


if __name__ == "__main__":
    unittest.main()
