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

# Exact protocol vocabulary, never arbitrary lowercase words from child output.
STEPS = {
    "prerequisites": {"configuration"},
    "setup": {"resources", "pools"},
    "credential_identity": {"credentials"},
    "remembered_rotation": {
        "session_seed", "create", "created_deadline", "wrong_installation", "rotate", "principal", "deadline", "replay",
        "jti_identity", "revoke", "revoked", "disabled_seed", "disabled_reject", "removed_seed",
        "removed_reject", "null_tenant_seed", "null_tenant_reject", "expired_seed", "expired_reject",
        "caller_restoration",
    },
    "runtime_isolation": {"isolation"},
    "cleanup": {"pool_close", "marker_identity", "database_drop", "auth_login_identity",
                "auth_login_drop", "runtime_login_identity", "runtime_login_drop"},
}
CATEGORIES = {"assertion", "guard", "sql_permission", "sql_constraint", "sql_timeout",
              "sql_connection", "sql_other", "unexpected"}
SCENARIOS = {"credential_identity", "remembered_rotation", "runtime_isolation"}


def report_result(result):
    """Emit at most one event per kind/phase, using only closed protocol tokens."""
    seen = set()
    for line in result.stdout.splitlines():
        tokens = line.split(" ")
        if len(tokens) == 3 and tokens[0] == "AUTH_VERIFY":
            _, kind, phase = tokens
            allowed = (kind == "PASS" and phase in SCENARIOS) or (kind == "FAIL" and phase in STEPS)
        elif len(tokens) == 5 and tokens[:2] == ["AUTH_VERIFY", "DETAIL"]:
            _, kind, phase, step, category = tokens
            allowed = step in STEPS.get(phase, set()) and category in CATEGORIES
        else:
            continue
        if allowed and (kind, phase) not in seen:
            seen.add((kind, phase))
            print(line)
    failures = any(kind == "FAIL" for kind, _ in seen)
    success = not result.returncode and not failures and all(("PASS", phase) in seen for phase in SCENARIOS)
    if not success and not failures:
        print("AUTH_VERIFY FAIL build_or_prerequisite")
    return 0 if success else 1

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
        return report_result(result)

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
