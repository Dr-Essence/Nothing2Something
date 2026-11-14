#!/bin/bash
# Run by .github/workflows/firmware-inspect.yml: unpacks one build and prints what is asked of
# it to the job's log (the development session cannot reach the artifact storage).
# Now (0.70.60): the widget picker's profile class in the Nothing OS 4.0 launcher, found by
# its shape (a final class holding a static copy of itself, with Resources, WindowMetrics and
# Insets fields, and an inner $a whose c(Context) returns it: za.a0 in 4.1), with its fields
# and methods, and how NtWidgetsFullSheet reaches it.
set -e
T=$1
bash tools/firmware-collect.sh "$T" fw
DEX=$(ls "$ANDROID_HOME"/build-tools/*/dexdump | sort -V | tail -1)
mkdir -p dex
for apk in $(find fw -path '*com.nothing.launcher*' -name '*.apk'); do
  unzip -oq "$apk" 'classes*.dex' -d dex
done
for f in dex/classes*.dex; do "$DEX" -d "$f"; done > launcher.txt
python3 - <<'PY'
import re
text = open("launcher.txt").read()
blocks = re.split(r"(?=Class #\d+\s*-\s*\n)", text)
want = {"Landroid/content/res/Resources;", "Landroid/view/WindowMetrics;", "Landroid/graphics/Insets;"}
found = []
for b in blocks:
    m = re.search(r"Class descriptor\s*: '(L[^']+;)'", b)
    if not m:
        continue
    name = m.group(1)
    types = set(re.findall(r"type\s*: '([^']+)'", b))
    if want <= types and name in types:
        found.append((name, b))
print("== profile candidates:", [n for n, _ in found])
for name, b in found:
    print("=" * 30, name)
    for line in b.split("\n"):
        s = line.strip()
        if s.startswith(("Class descriptor", "Access flags", "Superclass", "name ", "type ",
                         "Static fields", "Instance fields", "Direct methods", "Virtual methods")):
            print(s)
    inner = name[:-1] + "$a;"
    for b2 in blocks:
        if f"Class descriptor  : '{inner}'" in b2:
            print("=" * 30, inner)
            for line in b2.split("\n"):
                s = line.strip()
                if s.startswith(("name ", "type ", "Direct methods", "Virtual methods")):
                    print(s)
    # Who calls it, and the profile's own method bodies that set its fields.
    for line in text.split("\n"):
        if (name[:-1] + "$a;.") in line and "invoke" in line:
            print("call:", line.strip()[-160:])
sheet = [b for b in blocks if "Lcom/nothing/launcher/widgets/view/NtWidgetsFullSheet;'" in b[:400]]
print("== NtWidgetsFullSheet present:", bool(sheet))
PY
