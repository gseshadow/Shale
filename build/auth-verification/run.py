#!/usr/bin/env python3
"""Quiet opt-in launcher for synthetic, disposable loopback authentication SQL coverage."""
import argparse
import getpass
import os
from pathlib import Path
import re
import subprocess
import tempfile

OPT_IN = "LOCAL_DISPOSABLE_AUTH"
PRODUCTION = re.compile(r"^(SHALE_(APP|RT|AUTH(?!_VERIFY)|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*)$", re.I)

def validate(env, port, opt_in):
    if opt_in != OPT_IN or not 1024 <= port <= 65535:
        raise ValueError("Explicit disposable local auth opt-in and valid port required")
    if any(PRODUCTION.match(k) and v.strip() for k, v in env.items()):
        raise ValueError("Application connection variables are forbidden")
    if any(env.get(k, "").strip() for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_ARGS", "MAVEN_OPTS")):
        raise ValueError("Implicit Java/Maven arguments are forbidden")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opt-in", required=True, choices=[OPT_IN])
    parser.add_argument("--port", required=True, type=int)
    args = parser.parse_args()
    env = dict(os.environ)
    validate(env, args.port, args.opt_in)
    # Never accept arbitrary JDBC URLs, database names, SQL files or production credentials.
    env.update(SHALE_AUTH_VERIFY_OPT_IN=OPT_IN, SHALE_AUTH_VERIFY_PORT=str(args.port),
               SHALE_AUTH_VERIFY_ADMIN_USER=input("Dedicated LOCAL SQL admin user: "),
               SHALE_AUTH_VERIFY_ADMIN_PASSWORD=getpass.getpass("Dedicated LOCAL SQL admin password: "))
    root = Path(__file__).resolve().parents[2]
    with tempfile.TemporaryDirectory(prefix="shale-auth-verify-") as work:
        quiet = Path(work) / "quiet.xml"
        quiet.write_text('<configuration><root level="OFF"/></configuration>', encoding="utf-8")
        command = ["mvn", "-pl", "shale-server", "-am", "-Dtest=PreTenantAuthenticationLocalSqlServerTest",
                   "-Dsurefire.failIfNoSpecifiedTests=false", f"-Dlogback.configurationFile={quiet}", "test"]
        result = subprocess.run(command, cwd=root, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, errors="replace")
        lines = [line for line in result.stdout.splitlines() if re.fullmatch(r"AUTH_VERIFY (PASS|FAIL) [a-z_]+", line)]
        for line in lines:
            print(line)
        if result.returncode or len([x for x in lines if x.startswith("AUTH_VERIFY PASS")]) != 3:
            print("AUTH_VERIFY FAIL build_or_prerequisite")
            return 1
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, OSError, KeyboardInterrupt):
        print("AUTH_VERIFY FAIL launcher_prerequisite")
        raise SystemExit(1)
