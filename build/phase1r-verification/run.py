#!/usr/bin/env python3
"""Synthetic Phase 1R acceptance in a NEW database on dedicated loopback SQL only."""
import argparse
import getpass
import importlib.util
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("phase1r_auth_launcher", ROOT / "build/auth-verification/run.py")
auth = importlib.util.module_from_spec(spec)
spec.loader.exec_module(auth)
OPT_IN = "LOCAL_DISPOSABLE_PHASE1R"
PASSES = {"PHASE1R PASS migration_rollback", "PHASE1R PASS api_lifecycle",
          "PHASE1R PASS injected_setup_failure", "AUTH_VERIFY PASS credential_identity",
          "AUTH_VERIFY PASS remembered_rotation", "AUTH_VERIFY PASS runtime_isolation"}


def report(result):
    seen = set()
    failed = False
    for line in result.stdout.splitlines():
        if line in PASSES:
            if line not in seen:
                print(line)
                seen.add(line)
        elif re.fullmatch(r"AUTH_VERIFY FAIL (?:" + "|".join(auth.STEPS) + ")", line):
            failed = True
            print(line)
    if result.returncode or failed or seen != PASSES:
        print("PHASE1R FAIL acceptance")
        return 1
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opt-in", required=True, choices=[OPT_IN])
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--maven", default="mvn")
    args = parser.parse_args()
    try:
        env = dict(os.environ)
        auth.validate(env, args.port, auth.OPT_IN)
        maven = auth.resolve_maven(args.maven)
        auth.check_maven(maven, ROOT, env)
        env.update(SHALE_AUTH_VERIFY_OPT_IN=auth.OPT_IN, SHALE_AUTH_VERIFY_PORT=str(args.port),
                   SHALE_AUTH_VERIFY_ADMIN_USER=input("Dedicated LOCAL SQL admin user: "),
                   SHALE_AUTH_VERIFY_ADMIN_PASSWORD=getpass.getpass("Dedicated LOCAL SQL admin password: "))
        result = auth.run_maven(maven, ["-pl", "shale-server", "-am", "-Dtest=Phase1RLocalSqlServerTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "-Dlogback.configurationFile=" + str(ROOT / "build/case-read-verification/quiet-logback.xml"), "test"], ROOT, env)
        return report(result)
    except (ValueError, OSError, KeyboardInterrupt, EOFError):
        print("PHASE1R FAIL prerequisite")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
