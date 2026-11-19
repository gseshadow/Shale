"""Copy only local model selection into a new, private acceptance-only Hermes home."""
import argparse
import ipaddress
import re
import json
import os
from pathlib import Path
from urllib.parse import urlsplit
import yaml

def prepare(source, destination, model_origin=None):
    config = yaml.safe_load(source.read_text(encoding="utf-8")) or {}
    model = config.get("model", {})
    if not isinstance(model, dict):
        raise RuntimeError("LOCAL_MODEL_CONFIGURATION_REQUIRED")
    endpoint = urlsplit(model.get("base_url", ""))
    loopback = endpoint.hostname in ("localhost", "127.0.0.1", "::1")
    private_https = False
    if model_origin and endpoint.scheme == "https":
        try:
            address = ipaddress.ip_address(endpoint.hostname)
            private_https = any(address in ipaddress.ip_network(n) for n in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")) and model_origin == f"https://{endpoint.netloc}"
        except ValueError:
            pass
    if endpoint.scheme not in ("http", "https") or not (loopback or private_https) or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
        raise RuntimeError("LOOPBACK_MODEL_REQUIRED")
    selected = {k: model[k] for k in ("default", "provider", "base_url", "api_mode") if k in model}
    if not selected.get("default"):
        raise RuntimeError("MODEL_REQUIRED")
    model_env = None
    if private_https:
        reference = model.get("api_key", "")
        match = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", reference)
        if not match:
            raise RuntimeError("MODEL_KEY_ENVIRONMENT_REFERENCE_REQUIRED")
        name = match.group(1)
        selected["api_key"] = reference
        if not os.environ.get(name):
            env_path = source.parent / ".env"
            lines = env_path.read_text(encoding="utf-8").splitlines() if env_path.exists() else []
            matches = [line for line in lines if re.match(r"^(?:export\s+)?" + re.escape(name) + r"\s*=", line)]
            # Copy only one literal, single-line assignment. Never copy the whole .env,
            # interpolate another secret, or retain a multiline value.
            if len(matches) != 1:
                raise RuntimeError("MODEL_KEY_ENVIRONMENT_REQUIRED")
            value = matches[0].split("=", 1)[1].strip()
            if not value or "$" in value or not re.fullmatch(r"(?:'[^'\r\n]*'|\"[^\"\\\r\n]*\"|[^\s'\"#\\]+)", value):
                raise RuntimeError("MODEL_KEY_LITERAL_ASSIGNMENT_REQUIRED")
            model_env = name + "=" + value + "\n"
    destination.mkdir(exist_ok=False)
    (destination / "config.yaml").write_text(yaml.safe_dump({"model": selected, "telemetry": {"enabled": False}}), encoding="utf-8")
    if model_env is not None:
        (destination / ".env").write_text(model_env, encoding="utf-8")
    print(json.dumps({"Result": "LOCAL_PROFILE_PREPARED", "Model": selected["default"]}))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("destination", type=Path)
    parser.add_argument("--model-origin")
    args = parser.parse_args()
    source = Path(os.environ.get("HERMES_HOME") or Path(os.environ["LOCALAPPDATA"]) / "hermes")
    if (source / "active_profile").exists():
        raise RuntimeError("EXPLICIT_PROFILE_SELECTION_REQUIRED")
    prepare(source / "config.yaml", args.destination, args.model_origin)
