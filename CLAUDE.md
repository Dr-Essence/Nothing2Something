# Working rules for this module

- **Never assume.** Inspect and research before every change: read the decompiled code, the
  user's logs (LSPosed log, full logcat), videos and dumps, and find the cause in that
  evidence. A change that rests on an untested idea of how Android or the vendor stack behaves
  is a guess: get the evidence first (ask for the exact dump or log that settles it) instead
  of shipping a build to find out.
- **Watch everything the user sends.** Every video, screenshot and report recording is looked
  at, frame by frame where it matters, before anything is said about what it shows. Layout
  numbers from dumps never stand in for looking: judge how a screen looks from the pictures.
- **Test before each build.** Compile and check every change against the code it touches
  before giving a build; say plainly what was and was not verified, and never present a guess
  as a fix.
- **Never fight Nothing's or Android's features.** A feature works with Nothing Launcher /
  SystemUI / widgets / Android, not against them. If one of their mechanisms gets in the way
  (a check, a relayout, a scale, a portrait lock), find it in the decompiled code and either
  switch that one piece off where it is not needed or fix the mechanism itself, rather than
  letting both run and undoing its result afterwards (no re-measuring, re-padding or
  re-positioning what Nothing just laid out, no racing its own passes).
- **Clean dead code.** Remove what a change makes unused (methods, fields, hooks, settings,
  diagnostics that served their purpose) in the same change.
- **Keep the README's Status section current with every change.** Each commit that changes
  behaviour also updates it: the current version and state, what is pending, which features
  have not been tested on a phone yet, which tests are pending (and what evidence they need),
  and the bugs fixed. A change is not finished while the Status section still describes the
  build before it.
- **The README is written professionally:** state results as facts ("confirmed on the
  phone", "reported: …", "not yet recorded after a reboot"), never as quotes of chat messages
  ("seems fixed", "I think it works") or in the first person.
- **Keep the README's Testing section current:** whenever a change affects what works on a
  phone or Nothing OS version other than the CMF Phone 1 (a fix for another version, a new hook,
  a rerun of the hook check on every build), update its table and its "As of" version in the same
  commit.
- **Versions while in alpha:** every build raises only the last number (0.70.1, 0.70.2, ...),
  with versionCode + 1. The middle number changes only when Dr.Essence calls a milestone, and
  1.0.0 is never reached while the module is an alpha (until 0.70.0 each feature raised the
  middle number, which would have reached 1.0 within the alpha).
- The module must never crash or stall what it hooks: guard everything handed to the host,
  never block the main thread, and never make the launcher delete or lose the user's items.
- The module must never cause problems for other modules: where another module changes the
  same thing (a colour, a transparency, a background), keep what it did and change only our
  part, and check other modules' logs and sources before saying a problem is not ours.

# Project direction

- **Emoji covers are a beta.** They will be replaced by **Nothing Dot Matrix Display**, an
  engine built privately in its own repository (Dr-Essence/Ndot-Matrix-Display-Engine, whose
  CLAUDE.md holds the plan). In public (README, Roadmap, beta notes) it is described only as
  "a new engine for the emoji covers. Planned, built privately"; no details.
- **A Kotlin course from this project.** Dr.Essence is learning to code and plans to maintain
  this module. New features are written in Kotlin (the Java code is converted only when a file
  is reworked anyway), and the project will become a course for learning Kotlin from it: how
  each feature was found in Nothing's code and implemented, and the mistakes made and how they
  were fixed (the commit history and the README's Status are its record). Write new code and
  its comments so it can be taught from: clear names, the reason for each step, and what was
  checked. Focus: unlocking features and bypassing limits in Nothing OS and Android, not
  ordinary apps.

# Working plan (agreed 2026-11-14)

These six rules come from an honest review of the project. They hold until Dr.Essence lifts them.

1. **Feature freeze.** No new features. Three pillars get finished to a quality worth showing:
   the app drawer folders with their dot screen, the dot-matrix app icons, and glass (chosen
   2026-11-15). No other feature is removed for now: the goal is every feature working and
   stable on every Nothing phone. Gaming Space is a demo whose look will change completely
   (a small chance it is removed). When a request is a new feature, say so
   and ask whether it is worth breaking the freeze.
2. **Design in pictures first.** Any visible change (Gaming Space, the final look, settings
   layout, banners) is shown as a mock-up and agreed before code is written for it. Code is
   built once, from the agreed picture.
3. **One tester release a week.** Builds in between are for Dr.Essence's own phone only.
   The README's Builds table and the testers get the weekly build, not every commit.
4. **Dr.Essence owns the code.** Every change is explained so it can be read and understood:
   what each part does and why, in plain words, with the file and line. Changes stay small
   enough to read in one sitting. This is the Kotlin course.
5. **Split big files and add tests.** Large files (SettingsActivity, the landscape grid) are
   split when they are worked on anyway. Pure logic (dot rendering, colour maths, settings
   parsing, the hook check) gets unit tests that run in CI.
6. **The README stays short.** It says what the module is, how to install it, what is safe,
   and the Testing table. The history of builds lives in CHANGELOG.md.

**Current phase: stability (from 2026-11-14).** The only work is testing every existing feature
with docs/TEST_PLAN.md and fixing what fails. Guide Dr.Essence through the plan part by part, and
when a new feature is asked for, say that it breaks the freeze, suggest noting it in the
Roadmap for later, and go back to the plan. Fixes, tests and finishing existing work are allowed.

**Order after the stability phase (the README Roadmap, agreed 2026-11-15):** finish the three
pillars; then the Nothing OS 5 port, but only once Nothing OS 5 testers are found (without
them, the engine comes first); then Nothing Dot Matrix Display; then the Gaming Space redesign.
No dates.

**Ideas kept for later, not on the Roadmap:** status bar colours (icons and text in a colour the
user picks; a user's suggestion, idea from Iconify).
