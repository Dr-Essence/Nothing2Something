---
name: n2s-release
description: Ship a new build of the Nothing2Something (Nothing Launcher Plus) LSPosed module, from a finished change in the working tree to a pushed commit. Use it whenever a change is ready to hand to Dr.Essence or testers ("ship it", "make a build", "push it", "bump the version", "release 0.70.x"). Covers the version bump and README Status entry (with a helper script), the build, the commit and the push.
---

# Releasing a Nothing2Something build

CLAUDE.md holds the rules; this is the order to apply them in, so every release looks the
same. Stop and tell the user instead of pushing if a step fails.

1. **Check the change.** `git status` / `git diff`: only this change's files. Re-read the
   diff for anything that could crash or stall the host app, and remove what it made unused.
2. **Version and Status.** Write the Status entry to a scratch file, starting
   `- **{VERSION}, <title>.**`, wrapped like the entries around it: what changed and why, the
   evidence (and "Mistake recorded, 0.70.x: …" when fixing an earlier build), **Checked:**
   what was and was not verified, **To test:** the phone steps and what to send back (never
   a public LSPosed log). Then, from the repo root:
   ```
   python3 .claude/skills/n2s-release/scripts/release_prep.py --entry <file>
   ```
   It raises only the last number and versionCode + 1 (`--milestone` only on Dr.Essence's
   word; never 1.0), updates both version strings in Status and puts the entry under
   "**Not tested on a phone yet**". New or ended features: also edit the Current build list
   and the Roadmap by hand.
   A commit that changes no behaviour (a tool, a doc, this skill) skips this step and names
   the file in its subject instead of a version.
3. **Build.** `export ANDROID_HOME=/opt/android-sdk; ./gradlew -q :app:assembleRelease`
   (add `--offline` and the local Xposed stub `-I <scratchpad>/xposed-init.gradle` when Maven
   is limited). Must end with exit 0.
4. **Commit.** Staged diff holds no model names, keys or emails. Message
   `0.70.N: <summary>`, a few lines on what and why, then the session's trailers verbatim.
5. **Push.** `git push -u origin <session branch>`, retrying network errors 4 times
   (2, 4, 8, 16 s). Never force, no PR unless asked.
6. **Tell the user** in plain words: the version, what it brings, **Verified** / **Not
   verified**, numbered **To test** steps. The build to install is **the CI build** (Actions,
   "Build module", the run of this commit, once it is green): it is built with the real
   Xposed API and signed with the release key. Never hand out the local build: it is
   debug-signed (it cannot update an installed release, so installing it means uninstalling,
   which deletes the settings), and a local stub of the Xposed API can differ from the real
   one. Mistake recorded, 0.70.46 to 0.70.48: local builds were sent; their stub declared
   findAndHookMethod and hookMethod as returning Object, not XC_MethodHook.Unhook, so on the
   phone every hook failed with NoSuchMethodError and the launcher ran without the module.
   A local build is only for checking the change compiles; if one is checked on its own,
   dexdump it and confirm the Xposed calls end in `XC_MethodHook$Unhook;`.
