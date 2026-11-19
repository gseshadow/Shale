"""Build a separate, acceptance-bound browser/server artifact. Never contacts Azure or SQL."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ORIGIN = "https://shale-mcp-acceptance.azurewebsites.net"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output).resolve()
    output.relative_to((ROOT / "work").resolve())
    output.mkdir(exist_ok=False)
    env = {k: v for k, v in os.environ.items() if not k.startswith(("SHALE_", "SPRING_", "VITE_", "SQLCMD", "SQLCONNSTR_", "SQLAZURECONNSTR_"))}
    env["VITE_SHALE_API_BASE_URL"] = ORIGIN
    npm = shutil.which("npm.cmd") or shutil.which("npm")
    mvn = shutil.which("mvn.cmd") or shutil.which("mvn")
    if not npm or not mvn:
        raise RuntimeError("BUILD_TOOLS_REQUIRED")
    for command, cwd in [([npm, "run", "build"], ROOT / "shale-web"),
                         ([mvn, "-o", "-B", "-pl", "shale-server", "-am", "-DskipTests", "package"], ROOT)]:
        subprocess.run(command, cwd=cwd, env=env, check=True)
    dist = ROOT / "shale-web/dist"
    index = dist / "index.html"
    assets = sorted((dist / "assets").glob("*"))
    if not index.is_file() or not assets or not any(ORIGIN in p.read_text(encoding="utf-8") for p in assets if p.suffix == ".js"):
        raise RuntimeError("ACCEPTANCE_BROWSER_BUILD_REQUIRED")
    artifact = output / "shale-server.jar"
    shutil.copyfile(ROOT / "shale-server/target/shale-server.jar", artifact)
    with zipfile.ZipFile(artifact, "a", compression=zipfile.ZIP_DEFLATED) as jar:
        if any(n.startswith("BOOT-INF/classes/acceptance-browser/") for n in jar.namelist()):
            raise RuntimeError("BROWSER_ALREADY_PACKAGED")
        for file in [index, *assets]:
            if not file.is_file() or file.is_symlink():
                raise RuntimeError("REGULAR_BROWSER_ASSETS_REQUIRED")
            jar.write(file, "BOOT-INF/classes/acceptance-browser/" + file.relative_to(dist).as_posix())
    manifest = {"Origin": ORIGIN, "JarSHA256": hashlib.sha256(artifact.read_bytes()).hexdigest().upper(),
                "SourceHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "Scope": "Acceptance browser package; no deployment or activation", "BuildVersion": "1.0.138"}
    (output / "artifact.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest))

if __name__ == "__main__":
    main()
