#!/usr/bin/env python3
"""Opt-in launcher. Never accepts a JDBC URL or prints child-process diagnostics."""
import argparse
import getpass
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OPT_IN = "LOCAL_DISPOSABLE_CASE_READS"
PRODUCTION = re.compile(r"^(SHALE_(APP|RT|AUTH|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*)$", re.I)
SCENARIOS = ("prerequisites", "setup", "fixtures", "pooled_context", "rls_enforcement",
             "search_assigned_overview", "foreign_join_protection", "actor_authority", "overview_audit", "audit_insert_denied",
             "failure_before_commit", "commit_lost_ack", "cleanup")
EVENT = re.compile(r"^CASE_READ_VERIFY (PASS|FAIL) (" + "|".join(SCENARIOS) + r")$", re.M)


def validate(schema, digest, source, port, opt_in, env):
    if opt_in != OPT_IN:
        raise ValueError("Explicit local disposable opt-in required")
    if any(PRODUCTION.fullmatch(k) and v.strip() for k, v in env.items()):
        raise ValueError("Use a clean shell without production/application connection or auth variables")
    if any(env.get(k, "").strip() for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "MAVEN_ARGS")):
        raise ValueError("Implicit Java/Maven arguments are not permitted")
    if not 1024 <= port <= 65535:
        raise ValueError("Use a dedicated local TCP port between 1024 and 65535")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        raise ValueError("Reviewed schema SHA-256 required")
    if not source.strip() or len(source) > 200 or "\n" in source:
        raise ValueError("A bounded schema provenance label is required")
    path = Path(schema).resolve(strict=True)
    if path.suffix.lower() != ".sql" or not path.is_file() or path.stat().st_size > 8 * 1024 * 1024:
        raise ValueError("Use a schema-only SQL file of at most 8 MiB")
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest.lower():
        raise ValueError("Schema hash mismatch; review the input before retrying")
    return path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--opt-in", required=True)
    p.add_argument("--schema", required=True)
    p.add_argument("--schema-sha256", required=True)
    p.add_argument("--schema-source", required=True, help="Non-sensitive offline definition provenance")
    p.add_argument("--port", type=int, default=14333)
    p.add_argument("--maven", default="mvn", help="Installed Maven executable, never command arguments")
    args = p.parse_args()
    try:
        schema = validate(args.schema, args.schema_sha256, args.schema_source, args.port, args.opt_in, os.environ)
        maven = shutil.which(args.maven)
        if not maven:
            raise ValueError("Maven executable unavailable")
        user = os.environ.get("SHALE_CASE_VERIFY_ADMIN_USER") or input("Disposable LOCAL SQL admin user: ")
        password = os.environ.get("SHALE_CASE_VERIFY_ADMIN_PASSWORD") or getpass.getpass("Disposable LOCAL SQL admin password: ")
        if not user or not password:
            raise ValueError("Dedicated local credentials required")
        env = dict(os.environ)
        env.update(SHALE_CASE_VERIFY_OPT_IN=OPT_IN, SHALE_CASE_VERIFY_PORT=str(args.port),
                   SHALE_CASE_VERIFY_SCHEMA=str(schema), SHALE_CASE_VERIFY_SCHEMA_SHA256=args.schema_sha256.lower(),
                   SHALE_CASE_VERIFY_SCHEMA_SOURCE=args.schema_source,
                   SHALE_CASE_VERIFY_ADMIN_USER=user, SHALE_CASE_VERIFY_ADMIN_PASSWORD=password)
        command = [maven, "-B", "-pl", "shale-data", "-am",
                   "-Dtest=CaseReadLocalSqlServerTest,CaseReadVerificationSafetyTest",
                   "-Dsurefire.failIfNoSpecifiedTests=false",
                   "-Dlogback.configurationFile=" + str(Path(__file__).with_name("quiet-logback.xml")), "test"]
        # Driver/Maven diagnostics can contain values. Capture in memory; do not save or dump them.
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, errors="replace")
        events = EVENT.findall(result.stdout)
        for match in EVENT.finditer(result.stdout):
            print(match.group(0))
        if result.returncode or ("PASS", "cleanup") not in events:
            print("UNVERIFIED: harness/build failed; only fixed scenario outcomes are shown. See runbook prerequisites.")
            return 1
        print("PASS: local SQL integration only; deployed Azure acceptance remains open.")
        return 0
    except (ValueError, OSError):
        print("REFUSED: invalid prerequisites or unavailable local tooling; no diagnostics or credentials dumped.")
        return 2


if __name__ == "__main__":
    sys.exit(main())
