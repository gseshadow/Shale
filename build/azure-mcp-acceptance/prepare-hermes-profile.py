"""Copy only local model selection into a new, private acceptance-only Hermes home."""
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlsplit
import yaml

def prepare(source, destination):
    config = yaml.safe_load(source.read_text(encoding="utf-8")) or {}
    model = config.get("model", {})
    if not isinstance(model, dict):
        raise RuntimeError("LOCAL_MODEL_CONFIGURATION_REQUIRED")
    endpoint = urlsplit(model.get("base_url", ""))
    if endpoint.scheme not in ("http", "https") or endpoint.hostname not in ("localhost", "127.0.0.1", "::1") or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
        raise RuntimeError("LOOPBACK_MODEL_REQUIRED")
    selected = {k: model[k] for k in ("default", "provider", "base_url", "api_mode") if k in model}
    if not selected.get("default"):
        raise RuntimeError("MODEL_REQUIRED")
    destination.mkdir(exist_ok=False)
    (destination / "config.yaml").write_text(yaml.safe_dump({"model": selected, "toolsets": ["mcp"], "telemetry": {"enabled": False}}), encoding="utf-8")
    print(json.dumps({"Result": "LOCAL_PROFILE_PREPARED", "Model": selected["default"]}))

if __name__ == "__main__":
    source = Path(os.environ.get("HERMES_HOME") or Path(os.environ["LOCALAPPDATA"]) / "hermes")
    if (source / "active_profile").exists():
        raise RuntimeError("EXPLICIT_PROFILE_SELECTION_REQUIRED")
    prepare(source / "config.yaml", Path(sys.argv[1]))
