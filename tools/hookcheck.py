#!/usr/bin/env python3
"""Checks the module's hooks against a build of Nothing OS, without a phone.

  hookcheck.py index OUT.json APK_OR_JAR...      every class, method, field and resource name
  hookcheck.py refs OUT.json SRC_DIR             every name the module's source uses
  hookcheck.py check REFS.json OLD.json NEW.json what NEW lacks that OLD has (and what both lack)

How it works, for the Kotlin course: an Xposed hook names the app's code by strings (a class
name, a method name, a field name). Nothing's apps are shrunk by R8, which renames whatever it
may, so a new build can lose any name. `index` lists the names a build really has (dexdump,
from the Android SDK), `refs` lists the names the module asks for (read from the source:
findAndHookMethod, hookAllMethods, findClass, callMethod, getObjectField, getIdentifier...), and
`check` compares the two. A build known to work (OLD) is checked too, so a name the tool
misreads shows up there, not as a false break in NEW.

What it cannot see: a name built at run time (string concatenation), a method found by its
shape (DexKit, Symbols.kt) and behaviour changes with the same names. Those need the phone.
"""
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile


def sdk_tool(name):
    home = os.environ.get("ANDROID_HOME", "/opt/android-sdk")
    found = sorted(glob.glob(f"{home}/build-tools/*/{name}"))
    if not found:
        sys.exit(f"{name} not found under {home}/build-tools")
    return found[-1]


# ---------------------------------------------------------------- index

def index_dex(path, classes):
    out = subprocess.run([sdk_tool("dexdump"), path], capture_output=True, text=True,
                         errors="replace").stdout
    cls = None
    section = None
    for line in out.splitlines():
        m = re.match(r"\s*Class descriptor\s*: 'L(.*);'", line)
        if m:
            name = m.group(1).replace("/", ".")
            cls = classes.setdefault(name, {"super": None, "interfaces": [], "methods": {},
                                            "fields": {}})
            section = None
            continue
        if cls is None:
            continue
        m = re.match(r"\s*Superclass\s*: 'L(.*);'", line)
        if m:
            cls["super"] = m.group(1).replace("/", ".")
            continue
        s = line.strip()
        if s.startswith("Interfaces"):
            section = "interfaces"
            continue
        if s.startswith(("Static fields", "Instance fields")):
            section = "fields"
            continue
        if s.startswith(("Direct methods", "Virtual methods")):
            section = "methods"
            continue
        if section == "interfaces":
            m = re.match(r"#\d+\s*: 'L(.*);'", s)
            if m:
                cls["interfaces"].append(m.group(1).replace("/", "."))
            continue
        m = re.match(r"name\s*: '(.*)'", s)
        if m:
            pending = m.group(1)
            cls["_pending"] = pending
            continue
        m = re.match(r"type\s*: '(.*)'", s)
        if m and "_pending" in cls:
            name = cls.pop("_pending")
            if section == "methods":
                cls["methods"].setdefault(name, []).append(m.group(1))
            elif section == "fields":
                cls["fields"][name] = m.group(1)
    for c in classes.values():
        c.pop("_pending", None)


def resources(apk):
    out = subprocess.run([sdk_tool("aapt2"), "dump", "resources", apk], capture_output=True,
                         text=True, errors="replace").stdout
    return sorted(set(re.findall(r"resource 0x[0-9a-f]+ (\S+)", out)))


def cmd_index(out, paths):
    classes = {}
    res = []
    with tempfile.TemporaryDirectory() as tmp:
        for path in paths:
            with zipfile.ZipFile(path) as z:
                for entry in z.namelist():
                    if re.fullmatch(r"classes\d*\.dex", entry):
                        target = os.path.join(tmp, "x.dex")
                        with open(target, "wb") as f:
                            f.write(z.read(entry))
                        index_dex(target, classes)
            if path.endswith(".apk"):
                res += resources(path)
    json.dump({"classes": classes, "resources": sorted(set(res))}, open(out, "w"))
    print(f"{out}: {len(classes)} classes, {len(set(res))} resources")


# ---------------------------------------------------------------- refs

STRING = r'"((?:[^"\\]|\\.)*)"'
CONST = re.compile(r'(?:static\s+final\s+String|const\s+val|val)\s+([A-Z][A-Z0-9_]*)\s*(?::\s*String\s*)?=\s*' + STRING)
CALL = re.compile(r'(XposedHelpers|XposedBridge|Hooks|Symbols)\.(\w+)\s*\(')
# The module's own little lookup helpers (DotIcons/LiveIcons.field, FolderSheetLandscape
# .fieldType): read too, they name fields the same way (missed until 0.70.33).
HELPER = re.compile(r'(?<![\w.])(field|fieldType)\s*\(\s*\w+\s*,\s*' + STRING)
CLASS_NAME = re.compile(r'^(com\.android\.|com\.nothing\.|android\.|com\.google\.android\.)[\w.$]+$')


def split_args(text, start):
    """The arguments of the call whose '(' is at text[start - 1], as source strings."""
    depth, args, cur, i, quote = 1, [], "", start, None
    while i < len(text) and depth:
        ch = text[i]
        if quote:
            cur += ch
            if ch == "\\":
                cur += text[i + 1]
                i += 1
            elif ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
            cur += ch
        elif ch in "([{":
            depth += 1
            cur += ch
        elif ch in ")]}":
            depth -= 1
            if depth:
                cur += ch
        elif ch == "," and depth == 1:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
        i += 1
    if cur.strip():
        args.append(cur.strip())
    return args


PRIMITIVES = {"int": "I", "long": "J", "boolean": "Z", "float": "F", "double": "D",
              "byte": "B", "char": "C", "short": "S", "Int": "I", "Long": "J", "Boolean": "Z",
              "Float": "F", "Double": "D"}


def param_type(arg, value):
    """A parameter type argument as a dex descriptor, or None when it is not written out."""
    arg = arg.strip()
    m = re.fullmatch(r"(\w+)(?:\.class|::class\.javaPrimitiveType(?:!!)?|\.TYPE)", arg)
    if m and m.group(1) in PRIMITIVES:
        return PRIMITIVES[m.group(1)]
    m = re.fullmatch(r"([\w.]+)(?:\.class|::class\.java)", arg)
    if m:
        name = m.group(1)
        known = {"String": "java.lang.String", "Object": "java.lang.Object",
                 "Context": "android.content.Context", "Canvas": "android.graphics.Canvas",
                 "View": "android.view.View", "Intent": "android.content.Intent",
                 "Bundle": "android.os.Bundle", "MotionEvent": "android.view.MotionEvent",
                 "Rect": "android.graphics.Rect", "Configuration": "android.content.res.Configuration"}
        name = known.get(name, name)
        return "L" + name.replace(".", "/") + ";" if "." in name else None
    v = value(arg)
    if v and "." in v:
        return "L" + v.replace(".", "/") + ";"
    return None


def dex_params(signature):
    """'(ILandroid/view/View;[I)V' -> ['I', 'Landroid/view/View;', '[I']."""
    inner = signature[1:signature.index(")")]
    out, i = [], 0
    while i < len(inner):
        j = i
        while inner[j] == "[":
            j += 1
        j = inner.index(";", j) + 1 if inner[j] == "L" else j + 1
        out.append(inner[i:j])
        i = j
    return out


def cmd_refs(out, src):
    files = [f for f in glob.glob(f"{src}/**/*", recursive=True) if f.endswith((".java", ".kt"))]
    consts = {}
    texts = {}
    for f in files:
        texts[f] = open(f, errors="replace").read()
        for name, value in CONST.findall(texts[f]):
            consts.setdefault(name, value.replace("\\$", "$"))

    def value(arg):
        arg = arg.strip()
        m = re.fullmatch(STRING, arg)
        if m:
            return m.group(1).replace("\\$", "$")  # Kotlin's "\$" is a "$"
        name = arg.split(".")[-1]
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", name) and name in consts:
            return consts[name]
        m = re.fullmatch(r"(?:XposedHelpers\.)?findClass(?:IfExists)?\((.*)\)", arg)
        if m:
            return value(split_args(m.group(1) + ")", 0)[0])
        return None

    refs = []
    for f, text in texts.items():
        rel = os.path.relpath(f, src)
        for m in CALL.finditer(text):
            line = text.count("\n", 0, m.start()) + 1
            args = split_args(text, m.end())
            api, call = m.group(1), m.group(2)
            ref = {"file": rel, "line": line, "call": f"{api}.{call}"}
            if call in ("findClass", "findClassIfExists") and args:
                ref.update(kind="class", cls=value(args[0]))
            elif call in ("findAndHookMethod", "findMethodExact", "findMethodExactIfExists",
                          "findMethodBestMatch") and len(args) >= 2:
                cls = value(args[0])
                name_i = 2 if len(args) > 2 and value(args[1]) is None and not re.fullmatch(STRING, args[1]) else 1
                ref.update(kind="method", cls=cls, name=value(args[name_i]) if name_i < len(args) else None)
                if call == "findAndHookMethod":
                    # The parameter types between the name and the callback (the last one).
                    ref["params"] = [param_type(a, value) for a in args[name_i + 1:-1]]
            elif call in ("hookAllMethods", "deoptimizeAll") and len(args) >= 2:
                ref.update(kind="method", cls=value(args[0]), name=value(args[1]))
            elif call in ("hookAllConstructors", "findAndHookConstructor") and args:
                ref.update(kind="class", cls=value(args[0]))
            elif call in ("callMethod", "callStaticMethod") and len(args) >= 2:
                ref.update(kind="member", cls=value(args[0]) if call == "callStaticMethod" else None,
                           name=value(args[1]))
            elif re.fullmatch(r"(get|set)(Static)?\w*Field|findField(IfExists)?", call) and len(args) >= 2:
                ref.update(kind="field", cls=value(args[0]) if "Static" in call or call.startswith("findField") else None,
                           name=value(args[1]))
            else:
                continue
            refs.append(ref)
        for m in HELPER.finditer(text):
            refs.append({"file": rel, "line": text.count("\n", 0, m.start()) + 1,
                         "call": m.group(1), "kind": "field", "cls": None, "name": m.group(2)})
        # Every class-like string literal, wherever it is used (constants included).
        for raw in re.findall(STRING, text):
            lit = raw.replace("\\$", "$")
            if CLASS_NAME.match(lit) and lit.count(".") >= 2 and not lit.endswith("."):
                line = text.count("\n", 0, text.index('"' + raw + '"')) + 1
                refs.append({"file": rel, "line": line, "call": "literal", "kind": "class", "cls": lit})
        for name, typ in re.findall(r'getIdentifier\(\s*' + STRING + r'\s*,\s*' + STRING, text):
            line = text.count("\n", 0, text.index(name)) + 1
            refs.append({"file": rel, "line": line, "call": "getIdentifier", "kind": "resource",
                         "name": f"{typ}/{name}"})
    json.dump(refs, open(out, "w"), indent=0)
    kinds = {}
    for r in refs:
        kinds[r["kind"]] = kinds.get(r["kind"], 0) + 1
    print(f"{out}: {len(refs)} references {kinds}")


# ---------------------------------------------------------------- check

def has_member(index, cls, name, kind):
    """Whether cls (or a superclass/interface in the index) has a method/field named name."""
    seen = set()
    todo = [cls]
    while todo:
        c = todo.pop()
        if c in seen or c not in index:
            continue
        seen.add(c)
        entry = index[c]
        if (kind in ("method", "member") and name in entry["methods"]) or \
           (kind in ("field", "member") and name in entry["fields"]):
            return True
        todo += [entry["super"]] + entry["interfaces"]
    return False


def signature_fits(index, cls, name, params):
    """Whether cls (or a parent) has `name` with exactly these parameters (None: any type)."""
    seen, todo = set(), [cls]
    while todo:
        c = todo.pop()
        if c in seen or c not in index:
            continue
        seen.add(c)
        for sig in index[c]["methods"].get(name, []):
            have = dex_params(sig)
            if len(have) == len(params) and all(p is None or p == h for p, h in zip(params, have)):
                return True
        todo += [index[c]["super"]] + index[c]["interfaces"]
    return False


def anywhere(index, name, kind):
    for entry in index.values():
        if kind in ("method", "member") and name in entry["methods"]:
            return True
        if kind in ("field", "member") and name in entry["fields"]:
            return True
    return False


def resolve(r, idx):
    """'ok', 'missing' or 'unknown' (no class in this index to say)."""
    classes, res = idx["classes"], set(idx["resources"])
    kind, cls, name = r["kind"], r.get("cls"), r.get("name")
    if kind == "resource":
        return "ok" if name in res else "missing"
    if kind == "class":
        if not cls:
            return "unknown"
        return "ok" if cls in classes else ("missing" if owns(idx, cls) else "unknown")
    if not name:
        return "unknown"
    if cls:
        if cls not in classes:
            return "missing" if owns(idx, cls) else "unknown"
        if not has_member(classes, cls, name, kind):
            return "missing"
        params = r.get("params")
        if params is not None and kind == "method":
            return "ok" if signature_fits(classes, cls, name, params) else "missing"
        return "ok"
    if not anywhere(classes, name, kind):
        return "missing"
    params = r.get("params")
    if params is not None and kind == "method" and any(p is not None for p in params):
        # A hook on a class held in a variable: some class must have this method with these
        # parameters (missed until 0.70.33: ModelWriter.updateItemInfoProps).
        fits = any(signature_fits(classes, c, name, params)
                   for c, e in classes.items() if name in e["methods"])
        return "ok" if fits else "missing"
    return "ok"


def owns(idx, cls):
    """Whether this index is the app the class belongs to (its package is in it)."""
    prefix = cls.rsplit(".", 1)[0] + "."
    return any(c.startswith(prefix) for c in idx["classes"])


def cmd_check(refs_path, old_path, new_path):
    refs = json.load(open(refs_path))
    old, new = json.load(open(old_path)), json.load(open(new_path))
    rows = {"breaks": [], "both": []}
    for r in refs:
        a, b = resolve(r, old), resolve(r, new)
        if a == "ok" and b == "missing":
            rows["breaks"].append(r)
        elif a == "missing" and b == "missing" and r["kind"] != "member":
            rows["both"].append(r)
    def show(r):
        what = r.get("cls") or ""
        if r.get("name"):
            what = (what + "." if what else "*.") + r["name"]
        return f'{r["file"]}:{r["line"]}  {r["kind"]:8s} {what}  ({r["call"]})'
    print(f"== Present in {old_path}, missing in {new_path}: {len(rows['breaks'])}")
    for r in sorted(rows["breaks"], key=lambda r: (r["file"], r["line"])):
        print("  " + show(r))
    print(f"== Missing in both (tool misread, or not in these apps): {len(rows['both'])}")
    for r in sorted(rows["both"], key=lambda r: (r["file"], r["line"]))[:80]:
        print("  " + show(r))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    cmd, rest = sys.argv[1], sys.argv[2:]
    if cmd == "index":
        cmd_index(rest[0], rest[1:])
    elif cmd == "refs":
        cmd_refs(rest[0], rest[1])
    elif cmd == "check":
        cmd_check(*rest[:3])
    else:
        sys.exit(__doc__)
