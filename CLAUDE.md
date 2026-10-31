# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Single-module Android game (Kotlin, package `com.swaystack.app`). It began as "Sway Stack", a Tower-Bloxx-style stacking game, and is being redesigned into **Taut**: a one-tap string-tension timing game. A pulse oscillates near the next peg, and the player taps to pin the string (PERFECT / GOOD / MISS). Many identifiers still use the old names (`TowerGameView`, `BlockPhase`, `BlockImpactEvent`, `swaystack`). Leave them alone unless a rename is the task.

Also read `AGENTS.md` (same working agreements) and the design docs before working in an area:
- `docs/game-design-blueprint.md`: mechanic, art direction, level design, monetization.
- `docs/codex-implementation-plan.md`: architecture table (§16) and milestone roadmap (§17). Follow milestone order and don't build later-milestone work early.
- `TESTING.md`: manual on-device checklist for the shipped flows, plus a list of what the UI doesn't cover yet.

Working agreement: reuse the existing systems (sine-wave oscillation, camera lerp, haptics, squash/stretch, particles) wherever the math transfers. Don't rewrite them.

## Commands

Use `./gradlew` from Git Bash or `.\gradlew.bat` from PowerShell.

```
./gradlew assembleDebug                 # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # install on connected device/emulator
./gradlew testDebugUnitTest             # all JVM unit tests (incl. Robolectric)
./gradlew testDebugUnitTest --tests "com.swaystack.app.LevelGeneratorTest"
./gradlew testDebugUnitTest --tests "com.swaystack.app.LevelGeneratorTest.someMethod"
./gradlew assembleRelease               # minified; signed only if keystore.properties or KEYSTORE_PATH exists
./gradlew recordRoborazziDebug / verifyRoborazziDebug   # screenshot tests (baselines in app/src/test/screenshots)
```

- Unit tests run on a **JDK 21 toolchain** because Robolectric needs JDK 17+, while the app compiles for Java/Kotlin 11. `gradle.properties` hardcodes JDK paths (`C:\Program Files\Java\jdk-21`, `jdk-23`) through `org.gradle.java.installations.paths`. If tests can't find a toolchain, check that setting first.
- Robolectric tests use `@Config(sdk = [34])`.
- There is no lint or ktlint setup beyond the standard Android `lint` task.
- `tools/generate_difficulty_report.ps1` regenerates `difficulty_report.csv` (per-level speed and perfect-window values). It **duplicates the difficulty formulas** from the Kotlin code, so update it whenever you change the speed or window curves.
- Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or the env vars `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`. Debug builds use the repo-root `debug.keystore`. The secrets Gradle plugin reads `.env`, falling back to `.env.example`.

## Architecture

The UI is View-based. Compose is disabled (`compose = false`), and gameplay is drawn on a `SurfaceView` canvas. Persistence uses `SharedPreferences`. The implementation plan suggests Compose and DataStore, but neither has been adopted yet.

- **`MainActivity`** hosts `TowerGameView` full-screen, adds a Settings button that opens `SettingsActivity`, and starts `AdsManager` (UMP consent, then AdMob with test IDs from `BuildConfig`). Gameplay must keep working if consent, ads, or the network fail.
- **`TowerGameView`** owns the game-loop thread (about 60 fps, with sleep sized to the remaining frame budget), touch routing, and wiring. The home screen and level-complete buttons are **hit-tested by screen-height fractions** in `onTouchEvent` and drawn in `GameRenderer`. If you move a button in the renderer, update the matching hit region here. `SettingsStore` and the `TautPrefs` SharedPreferences are read once, at construction.
- **`game/GameState`** is a single mutable state bag for everything: mode, pegs, segments, particles, camera, landmark progress. `resetRun(mode, level, seed, difficulty)` and `resetDailyChallenge(day)` set up a run. Free Play extends forever through `appendFreePlayChunk()` and `pruneFreePlayMemory()`.
- **`game/GameEngine`** handles per-frame `update(dtMs)` (dt is clamped to 50 ms and normalized to 60 fps units), tap grading, scoring, combo and slack, lives and frays, haptics, and particle bursts. There are two parallel code paths. One is **Free Play / generated levels** (`landmark == null`): horizontal oscillation around `currentPeg()`, with lives. The other is **landmark levels** (`landmark != null`): oscillation along the stroke direction, with per-stroke frays, where running out of frays restarts the stroke. Any change to grading or motion usually has to be made in both paths.
- **`game/GameRenderer`** is a stateless draw of `GameState` onto a `Canvas`. Sharing reuses it to render a bitmap (`ShareHelper`).
- **`game/MotionBounds`** keeps the pulse at least 8 dp inside the screen edges. All oscillation goes through `safeAmplitude` / `clampX` / `clampY`.
- **Constellation Path**: `assets/landmarks.json` defines 13 landmarks, each with normalized peg points and strokes (index lists), loaded by `LandmarkRepository`. `LevelCatalog` maps levels 1–39 to (landmark, `Difficulty`) as `landmarkIndex * 3 + tier`. The `Difficulty` enum (EASY / MEDIUM / HARD) scales speed, perfect window, and frays. `Progression.isUnlocked` chains EASY → MEDIUM → HARD per landmark, using keys like `"id:EASY"`.
- **`game/LevelGenerator`** generates seeded peg paths (`PathShape`s) for Free Play and other generated levels. A validator (`validate` / `simulatePerfectBot`) checks solvability. Generation must stay **deterministic** for a given seed.
- **Daily Challenge** (`DailyChallenge.kt`): the local day index selects one landmark at MEDIUM. `DailyProgress` stores the streak in prefs (`daily_streak`, `daily_last_completed_day`). The engine records a completion when the last stroke finishes.
- **`SoundManager`** generates placeholder tones. The plan says it will eventually be replaced by a SoundPool note-sample system.

Tests in `app/src/test` are mostly pure-JVM checks of the deterministic systems: generator solvability, level catalog mapping, difficulty tiers, motion bounds, landmark peg spacing, daily streak, and settings.
