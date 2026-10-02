#!/usr/bin/env python3
"""Publish a manifest's structured notes through the authoritative control-plane API."""
from __future__ import annotations
import argparse, json, os, sys, urllib.error, urllib.request
from pathlib import Path

def import_catalog(manifest_path: Path, endpoint: str | None, token: str | None) -> bool:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    notes = manifest.get("releaseNotes")
    if notes is None:
        print("Manifest has no structured releaseNotes; catalog import is not required.")
        return False
    if not isinstance(notes, dict) or notes.get("version") != manifest.get("version"):
        raise ValueError("structured releaseNotes version must match the manifest version")
    if not endpoint or not token:
        raise ValueError("SHALE_RELEASE_CONTROL_PLANE_URL and SHALE_RELEASE_CONTROL_PLANE_TOKEN are required when releaseNotes are present")
    url = endpoint.rstrip("/") + "/api/control-plane/application-releases/" + notes["version"] + "/import"
    request = urllib.request.Request(url, data=json.dumps(notes).encode("utf-8"), method="POST",
        headers={"Content-Type":"application/json", "X-Shale-Control-Plane-Token":token})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result=json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail=exc.read().decode("utf-8", errors="replace")[:500]
        raise RuntimeError(f"release catalog import rejected with HTTP {exc.code}: {detail}") from exc
    if result.get("version") != notes["version"] or result.get("outcome") not in {"CREATED","UNCHANGED","UPDATED"}:
        raise RuntimeError("release catalog import returned an invalid acknowledgement")
    print(f"Release catalog import {result['outcome'].lower()} for {result['version']}.")
    return True

def main() -> int:
    parser=argparse.ArgumentParser();parser.add_argument("manifest",type=Path);args=parser.parse_args()
    try:
        import_catalog(args.manifest,os.getenv("SHALE_RELEASE_CONTROL_PLANE_URL"),os.getenv("SHALE_RELEASE_CONTROL_PLANE_TOKEN"));return 0
    except Exception as exc:
        print(f"Release catalog import failed: {exc}",file=sys.stderr);return 1
if __name__=="__main__":raise SystemExit(main())
