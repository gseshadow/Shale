"""Check changed documentation fences and repository-relative link targets."""
from pathlib import Path
import re
import subprocess

base = "71e39b863f5929022dba5b6dae9642be88dbb28a"
paths = subprocess.check_output(["git", "diff", "--name-only", base, "HEAD"], text=True).splitlines()
for name in paths:
    path = Path(name)
    if path.suffix != ".md" or not path.exists():
        continue
    text = path.read_text()
    assert sum(line.startswith("```") for line in text.splitlines()) % 2 == 0, name
    for target in re.findall(r"\]\(([^)]+)\)", text):
        if re.match(r"[a-z]+:", target) or target.startswith("#"):
            continue
        relative = target.split("#", 1)[0]
        assert (path.parent / relative).exists(), (name, target)
    assert all(line == line.rstrip() for line in text.splitlines()), name
print("Changed Markdown fences, local link targets and whitespace: PASS")
