#!/usr/bin/env python3
"""Prepares a Nothing2Something release in the working tree (no commit, no push).

  python3 .claude/skills/n2s-release/scripts/release_prep.py --entry entry.md [--milestone]

- Bumps app/build.gradle: versionName 0.X.Y -> 0.X.(Y+1) and versionCode + 1. With
  --milestone (only when Dr.Essence has called a milestone in their own words) 0.X.Y ->
  0.(X+1).0 instead. Refuses anything that would reach 1.0.0: the module is an alpha.
- README.md: replaces "version <old>, what is pending" and "**Current build: <old>**" with
  the new version, and inserts the entry file's text right after the "**Not tested on a phone
  yet**" line, with every {VERSION} in it replaced by the new version.
- Prints the new version and versionCode. Changes nothing if any step cannot be done.
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]  # .claude/skills/n2s-release/scripts -> repo
GRADLE = ROOT / "app" / "build.gradle"
README = ROOT / "README.md"
MARKER = "**Not tested on a phone yet**\n"


def fail(why):
    print("release_prep: " + why, file=sys.stderr)
    sys.exit(1)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--entry", required=True, help="markdown file with the README Status entry")
    p.add_argument("--milestone", action="store_true",
                   help="raise the middle number (only when Dr.Essence called a milestone)")
    a = p.parse_args()

    gradle = GRADLE.read_text()
    name = re.search(r"versionName '(\d+)\.(\d+)\.(\d+)'", gradle)
    code = re.search(r"versionCode (\d+)", gradle)
    if not name or not code:
        fail("versionName/versionCode not found in app/build.gradle")
    major, minor, patch = (int(x) for x in name.groups())
    old = f"{major}.{minor}.{patch}"
    new = f"{major}.{minor + 1}.0" if a.milestone else f"{major}.{minor}.{patch + 1}"
    if major >= 1 or new.startswith("1."):
        fail(f"{old} -> {new} would reach 1.0: the module is an alpha (CLAUDE.md)")
    new_code = int(code.group(1)) + 1

    entry = Path(a.entry).read_text().replace("{VERSION}", new).rstrip("\n") + "\n"
    if not entry.startswith(f"- **{new}"):
        fail(f'the entry must start with "- **{{VERSION}}, <title>.**" (got: {entry[:40]!r})')

    readme = README.read_text()
    for needle in (f"version {old}, what is pending", f"**Current build: {old}**", MARKER):
        if readme.count(needle) != 1:
            fail(f"README.md: expected exactly one {needle.strip()!r}")
    readme = readme.replace(f"version {old}, what is pending", f"version {new}, what is pending")
    readme = readme.replace(f"**Current build: {old}**", f"**Current build: {new}**")
    readme = readme.replace(MARKER, MARKER + entry, 1)

    gradle = gradle.replace(f"versionCode {code.group(1)}", f"versionCode {new_code}", 1)
    gradle = gradle.replace(f"versionName '{old}'", f"versionName '{new}'", 1)
    GRADLE.write_text(gradle)
    README.write_text(readme)
    print(f"{old} -> {new} (versionCode {new_code})")


if __name__ == "__main__":
    main()
