#!/usr/bin/env python3
"""Update staging image tags; keep legacy values compatible during migration."""
import re
import sys
from pathlib import Path


def update(root: Path, tag: str) -> None:
    if not re.fullmatch(r"stag-[0-9a-f]{7}-[0-9]{8}", tag):
        raise ValueError("Expected stag-<7-character SHA>-<YYYYMMDD>")
    changes = []
    charts = ("auth", "guidebook", "scheduler")
    paths = [root / "helm" / chart / "values.yaml" for chart in charts]
    environment = root / "environments" / "staging"
    if environment.exists():
        # Require the complete environment before changing any files.
        paths += [environment / f"{chart}.yaml" for chart in charts]
    for path in paths:
        text = path.read_text()
        images = list(re.finditer(r"^image:\s*\n((?:[ \t]+[^\n]*\n|\n)*)", text, re.M))
        if len(images) != 1:
            raise ValueError(f"{path}: expected one top-level image mapping")
        image = images[0]
        body = image.group(1)
        if not re.search(r'^  repository: [\'\"]?ghcr\.io/100-hours-a-week/ktb4-10-be[\'\"]?\s*$', body, re.M):
            raise ValueError(f"{path}: unexpected image repository")
        body, count = re.subn(r"^(  tag:)[^\n]*$", lambda m: f"{m[1]} {tag}", body, flags=re.M)
        if count != 1:
            raise ValueError(f"{path}: expected one image.tag")
        changes.append((path, text[:image.start(1)] + body + text[image.end(1):]))
    # Validate every target before writing any file.
    for path, text in changes:
        path.write_text(text)
        print(f"Updated {path.relative_to(root)}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Usage: update-staging-image.py <gitops-root> <image-tag>")
    update(Path(sys.argv[1]), sys.argv[2])
