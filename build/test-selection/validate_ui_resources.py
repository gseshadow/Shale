#!/usr/bin/env python3
"""Static validation for JavaFX FXML and CSS resources (no toolkit or rendered layout)."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "shale-ui/src/main/resources"


def main() -> int:
    errors: list[str] = []
    for fxml in sorted((RESOURCES / "fxml").glob("*.fxml")):
        try:
            root = ET.parse(fxml).getroot()
        except ET.ParseError as error:
            errors.append(f"{fxml.relative_to(ROOT)}: malformed XML: {error}")
            continue
        ids: set[str] = set()
        for element in root.iter():
            fx_id = element.attrib.get("{http://javafx.com/fxml/1}id")
            if fx_id and fx_id in ids:
                errors.append(f"{fxml.relative_to(ROOT)}: duplicate fx:id {fx_id!r}")
            if fx_id:
                ids.add(fx_id)

    for css in sorted((RESOURCES / "css").rglob("*.css")):
        text = css.read_text(encoding="utf-8")
        without_comments = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        if without_comments.count("{") != without_comments.count("}"):
            errors.append(f"{css.relative_to(ROOT)}: unbalanced CSS rule braces")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("Validated JavaFX FXML parsing/IDs and CSS rule structure.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
