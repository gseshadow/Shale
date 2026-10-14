#!/usr/bin/env python3
"""Quiet opt-in launcher for synthetic, disposable loopback authentication SQL coverage."""
import argparse
import getpass
import ntpath
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

WINDOWS = os.name == "nt"


class LauncherFailure(ValueError):
    """Only closed failure categories may reach operator output."""


def resolve_maven(candidate="mvn"):
    candidates = ("mvn.cmd", "mvn") if WINDOWS and candidate == "mvn" else (candidate,)
    for name in candidates:
        resolved = shutil.which(name)
        if resolved:
            return ntpath.abspath(resolved) if WINDOWS else os.path.abspath(resolved)
    raise LauncherFailure("maven_missing")


def run_maven(maven, arguments, root, env):
    """Invoke installed Maven; batch wrappers require explicit, bounded cmd parsing."""
    command = [maven, *arguments]
    executable = None
    if WINDOWS and ntpath.splitext(maven)[1].lower() in (".cmd", ".bat"):
        # list2cmdline is C-runtime quoting, not cmd.exe quoting. Reject expansion,
        # redirection and quote characters; quote EVERY token, including spaces.
        if any(not token or re.search(r'["%!^&|<>\r\n\x00]', token) for token in command):
            raise LauncherFailure("maven_invocation")
        system_root = env.get("SYSTEMROOT", env.get("SystemRoot", ""))
        if not ntpath.isabs(system_root) or re.search(r'["%!^&|<>\r\n\x00]', system_root):
            raise LauncherFailure("maven_invocation")
        executable = ntpath.join(system_root, "System32", "cmd.exe")
        # /d disables AutoRun; /v:off disables delayed environment expansion;
        # /s strips only the outer pair around the fully quoted batch command.
        batch = " ".join('"' + token + '"' for token in command)
        command = '"' + executable + '" /d /s /v:off /c "' + batch + '"'
    try:
        return subprocess.run(command, executable=executable, shell=False, cwd=root, env=dict(env),
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, errors="replace")
    except OSError:
        raise LauncherFailure("maven_invocation") from None


def check_maven(maven, root, env):
    # Quiet tooling/JDK check before collecting dedicated SQL credentials.
    if run_maven(maven, ["-version"], root, env).returncode:
        raise LauncherFailure("maven_invocation")


OPT_IN = "LOCAL_DISPOSABLE_AUTH"
PRODUCTION = re.compile(r"^(SHALE_(APP|RT|AUTH(?!_VERIFY)|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*)$", re.I)

def validate(env, port, opt_in):
    if opt_in != OPT_IN or not 1024 <= port <= 65535:
        raise ValueError("Explicit disposable local auth opt-in and valid port required")
    if any(PRODUCTION.match(k) and v.strip() for k, v in env.items()):
        raise LauncherFailure("prohibited_environment")
    if any(env.get(k, "").strip() for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_ARGS", "MAVEN_OPTS")):
        raise LauncherFailure("prohibited_environment")

def launch():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opt-in", required=True, choices=[OPT_IN])
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--maven", default="mvn", help="Installed Maven executable, never command arguments")
    args = parser.parse_args()
    env = dict(os.environ)
    validate(env, args.port, args.opt_in)
    root = Path(__file__).resolve().parents[2]
    maven = resolve_maven(args.maven)
    check_maven(maven, root, env)
    # Never accept arbitrary JDBC URLs, database names, SQL files or production credentials.
    env.update(SHALE_AUTH_VERIFY_OPT_IN=OPT_IN, SHALE_AUTH_VERIFY_PORT=str(args.port),
               SHALE_AUTH_VERIFY_ADMIN_USER=input("Dedicated LOCAL SQL admin user: "),
               SHALE_AUTH_VERIFY_ADMIN_PASSWORD=getpass.getpass("Dedicated LOCAL SQL admin password: "))
    with tempfile.TemporaryDirectory(prefix="shale-auth-verify-") as work:
        quiet = Path(work) / "quiet.xml"
        quiet.write_text('<configuration><root level="OFF"/></configuration>', encoding="utf-8")
        command = ["-pl", "shale-server", "-am", "-Dtest=PreTenantAuthenticationLocalSqlServerTest",
                   "-Dsurefire.failIfNoSpecifiedTests=false", f"-Dlogback.configurationFile={quiet}", "test"]
        result = run_maven(maven, command, root, env)
        lines = [line for line in result.stdout.splitlines() if re.fullmatch(r"AUTH_VERIFY (PASS|FAIL) [a-z_]+", line)]
        for line in lines:
            print(line)
        if result.returncode or len([x for x in lines if x.startswith("AUTH_VERIFY PASS")]) != 3:
            print("AUTH_VERIFY FAIL build_or_prerequisite")
            return 1
    return 0

def main():
    try:
        return launch()
    except LauncherFailure as failure:
        print("AUTH_VERIFY FAIL " + str(failure))
    except (ValueError, OSError, KeyboardInterrupt, EOFError):
        print("AUTH_VERIFY FAIL launcher_prerequisite")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
