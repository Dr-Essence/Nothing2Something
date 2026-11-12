# Reverse engineering and update readiness

Notes from the phone's own code (CMF Phone 1, Nothing OS 4.1, Android 16), used to check every
hook of the module and to prepare for updates of Nothing OS and its apps.

| File | What it is |
| --- | --- |
| `system_server_hooks.md` | Every system_server hook checked against the phone's decompiled services.jar: thread, lock, signature. |
| `launcher_dependencies.md` | About 300 dependencies on Nothing Launcher 4.0.20, each with a stable way to find it again if renamed. |
| `systemui_settings_dependencies.md` | The same for System UI, Settings, Settings search, HeliBoard and Android's hidden APIs. |
| `dot_matrix_engine.md` | Research for the Nothing Dot Matrix Display engine (roadmap). |

## Supporting another Nothing OS build

`tools/nos5-files.sh` collects what is needed from a rooted phone on that build (Nothing's
system apps the module hooks, Android's framework and system_server, the emoji font, build
info without any identifier; no logs): run `su -c "sh /sdcard/Download/nos5-files.sh"` and send
`Download/nos5-files.tar.gz`. Diagnostics → Hook health on that phone shows which features load.

Without a phone: the **Nothing OS firmware files** workflow (`.github/workflows/firmware.yml`)
takes the newest build of each Nothing OS version of every Nothing and CMF phone from
[spike0en/nothing_archive](https://github.com/spike0en/nothing_archive) (Nothing's official
updates rebuilt into partition images), unpacks system, system_ext and product on GitHub's
runner, and keeps the same files as the script, found by package name. Each build is an
artifact kept 7 days (Nothing's files are never committed here), with an index of every app's
package, path and version kept 30 days.

## How the module meets an update (0.70.19 to 0.70.23)

1. **It shows what broke.** Each hooked app reports which features installed and which did
   not, with why: Diagnostics → Hook health (`xposed/HookReport.kt`, `ui/HookHealth.kt`).
2. **A feature installs whole or not at all**, where a half-installed one could harm: the
   landscape grid only after its safety parts (they keep the user's items), the lock screen
   rotation only with its fingerprint parts, the dual status bar turned on only after all its
   hooks, the OLED saver hiding only with all that brings the bar back, the Game Bar's buttons
   only with their handler, the loader's idle wait resolved at install with Nothing's own as
   fallback.
3. **Obfuscated names are checked by type before use** (Settings' task manager, the widget
   picker's profile `za.a0`): a renamed or re-typed field makes the feature skip and say so,
   instead of reading or writing the wrong field.
4. **Android's newer System UI designs** (scene container, new status bar, Compose bouncer,
   new quick settings) are flags read at start; while one is on, the features built for the
   older design are left out.

## Still open (needs a decision or the phone)

- **More names for the resolver.** Since 0.70.26 `xposed/Symbols.kt` finds three launcher
  methods again with DexKit after an update (`launcher/LauncherSymbols.kt`: d7.h.i, h7.d.a,
  p7.e.e, each query tested against 4.0.20). Still checked only, not found again: `za.a0`'s
  fields (same-type fields cannot be told apart by a query), `smart.z`'s getters, Settings'
  `A1`/`K1`/`M1` (Settings, another process; the same Symbols can serve it).
- Same-type swaps (two int fields exchanging names) are not caught by type checks.
- The suspected redraw loop of Nothing's drawer tile (evidence: the debug report's
  `gfxinfo_*.txt`).
