# Codex Implementation Plan — Taut

Companion to `01-game-design-blueprint.md`. This covers Sections 16–19: architecture, build roadmap, analytics, and MVP scope. Written so you can hand this file straight to Codex alongside the current repo.

## 16. Technical architecture

The current codebase is small (16 files) and clean enough to extend rather than throw away. Below, systems marked **(existing, evolve)** already exist in some form in `GameEngine.kt`/`GameState.kt`/`GameRenderer.kt` and should be adapted, not rewritten from zero. Systems marked **(new)** don't exist yet — the current build has no menu, no settings, no save system beyond one int, no monetization, no analytics.

| System | Purpose | Inputs | Outputs | Depends on | Key edge cases |
|---|---|---|---|---|---|
| **Game Loop** *(existing, evolve)* | Drive update+render at consistent pacing | System clock, `Choreographer` callback | `dt` to Game Engine, locked canvas to Renderer | none | App background/foreground (thread must pause/resume cleanly — partially handled already); first-frame `dt` spike (already clamped to 50ms — keep this) |
| **Input System** *(existing, evolve)* | Capture single tap, timestamp it, route to a "pin attempt" | `MotionEvent` | Tap event → Game Engine | Game Loop | Multi-touch (explicitly ignore all pointers but the first — not currently filtered); taps during menu/transition states must not leak into gameplay |
| **Peg/Level State** *(new)* | Hold current peg sequence, positions, oscillation params for the active run | Level Generator output | Peg data to Renderer and to Game Engine's grading check | Level Generator | Seam between generated chunks in Free Play must not visibly pop; branch-point resolution (level 50+) needs one deterministic tie-break rule |
| **Level Generator** *(new)* | Produce peg patterns from parameters — Constellation Path levels and the Free Play infinite stream | Level index / seed, Difficulty System output | Peg pattern list (position, live/dead, oscillation speed) | Difficulty System | Must run a "perfect-bot" solvability simulation at generation time, before anything ships to a player; Daily Challenge needs a fixed seed so every player gets the same layout |
| **Difficulty System** *(new, small)* | Map level index / Free Play score to concrete parameters (window size, speed, dead-peg density) | Current level/score | Parameter set | none | Must be a pure function — same input always gives the same output, or Daily Challenge fairness breaks |
| **Game Engine** *(existing, evolve heavily)* | Own tap grading, slack accumulation, combo/multiplier, fray count | Tap events, `dt`, peg data | `PinResult` events (PERFECT/GOOD/MISS) consumed by Audio/Haptics/VFX/Progression | Peg/Level State, Input | Tap landing exactly on a phase boundary needs one authoritative rounding rule; slack must stay clamped (current code already clamps sway amplitude — reuse the pattern) |
| **Progression/Save System** *(new)* | Persist coins, unlocked cosmetics, best runs, level completion, daily streak | Run-end events | Persisted state | none | Current build only saves one int — this is genuinely new. Use **Jetpack DataStore**, not Room: Room is already an unused dependency sitting in your classpath, but this save data doesn't need real queries yet, and DataStore is simpler and coroutine-native. Reconsider Room only if the Constellation Gallery grows into something that needs actual querying. |
| **Audio Manager** *(replaces SoundManager entirely)* | Play pitched note samples, ambient bed, layered combo harmonics | `PinResult` events, combo state | Sound playback | Game Engine events | Use `SoundPool`, not `MediaPlayer`, for the pluck notes — `MediaPlayer` start latency is too high for tight audio/visual sync. Runtime pitch-shifting a single sample isn't reliable on stock Android APIs — plan on a small set of pre-rendered note variants (Section 10 of the design doc) rather than shifting one sample live. |
| **VFX Manager** *(existing, evolve)* | Spawn/update particles, glow, screen-shake | `PinResult` events | Particle list to Renderer | Game Engine events | Add a device-tier particle budget (low/mid/high) — current code has no awareness of device capability and always spawns the same 20 particles |
| **UI System** *(new)* | Menu, settings, cosmetic shop, Constellation Path level-select | User taps | Screen/navigation state | Progression/Save | The current app is a single Activity with a single custom View and zero navigation — there is nothing to extend here. Recommend building these screens in **Jetpack Compose** layered over the existing `SurfaceView` game canvas: Compose is dramatically less boilerplate for simple menu/shop screens than hand-built Views, and it's a clean addition rather than a rewrite of anything that currently works. Leave the actual gameplay rendering on `SurfaceView`/`Canvas` — rewriting proven, working render code for no functional gain is wasted effort. |
| **Analytics** *(new)* | Emit events for Section 18's metrics | Gameplay events from every other system | Logged events | Every gameplay system | Firebase Analytics is the standard free option, but it requires re-adding the `INTERNET` permission (currently absent) and triggers Play Store's Data Safety disclosure form — a real, deliberate tradeoff, not a free add |
| **Monetization (Ads/IAP)** *(new)* | Rewarded continue, interstitials, remove-ads + cosmetic purchases | Run-end/level-transition events | Ad display calls, purchase flow | Progression/Save | Needs Google Play Billing Library (this is a dependency worth adding — unlike the currently-unused Retrofit/OkHttp, which should be removed, not repurposed) and an ad mediation SDK (AdMob is the default choice). Both require `INTERNET` permission — a deliberate, justified addition this time. |
| **Settings** *(new, small)* | Sound/haptic/music toggles, persisted | User toggle | Persisted flag read by Audio/VFX | Progression/Save | The current `SoundManager.enabled` flag exists in code with **no UI to ever change it** — cheap, concrete first thing to actually finish |
| **Performance** *(existing, fix)* | Keep frame time bounded on low-end devices | — | — | — | The two bugs at the top of this file, plus a particle budget by device tier |
| **Android Integration** *(existing, extend)* | Manifest permissions, Play Store requirements, back-button handling | — | — | — | Current build has no special back-button handling; adding a real menu means the system back button needs defined behavior (pause, not exit-to-home, during a run) |

---

## 17. Codex build roadmap

Sequenced so the core loop is proven fun before any content or monetization investment — matching your own instruction in Section 19 of the brief.

| Milestone | Goal | Build this | Don't build yet | Acceptance check |
|---|---|---|---|---|
| **0 — Project audit** | Clean, correct baseline before adding anything | Strip Room/Retrofit/OkHttp/Moshi/full Kotlin-reflect from Gradle; fix the per-frame `Paint` allocation; fix frame pacing | Nothing new | Current Sway Stack game still plays identically; APK shrinks; no new features |
| **1 — Core prototype** | Prove the Taut loop is fun before investing further | Peg + pulse + tap-to-pin, PERFECT/GOOD/MISS grading, on one hardcoded straight line of pegs; frays/lives; restart. Placeholder audio (reusing the existing tone-based `SoundManager` is fine here — don't build real audio yet) | Level generator, art, real audio, progression, monetization | You can play an endless straight-line run, grading feels right, frays and restart work |
| **2 — Game feel** | Match the Section 9 sensory spec | Port squash/stretch curve to the "pin" reaction; particle bursts (evolve existing `Particle`); tiered haptics (near-verbatim port of existing haptic code); camera follow (reuse existing lerp); glow rendering | Level generator, monetization | A PERFECT pin feels at least as good as the current build's PERFECT stack, side by side |
| **3 — Level generation** | Real procedural system, not hardcoded pegs | Parametric generator (Section 12), perfect-bot solvability validator, Free Play infinite stream + Constellation Path discrete levels, first 30 levels tuned | Progression, monetization, final art | Can play levels 1–30 with zero hand-placed peg data; validator passes on 1,000+ generated seeds with no unsolvable result |
| **4 — Progression** | Meta layer that gives players a reason to return | DataStore-based save; coins; cosmetic unlock data model (placeholder color-swaps are fine, not final art); Constellation Gallery data capture; daily-challenge seeding | Final art/audio, monetization | Coins persist across app restarts; at least one cosmetic unlockable end-to-end |
| **5 — Art/audio** | Full sensory spec, real assets | Real note-sample audio system (Section 10); glow/gradient render polish (Section 11); real cosmetic material visuals; store assets/icon | — | Matches Sections 9–11 of the design doc in a direct playtest |
| **6 — Monetization** | Revenue without breaking the "never mid-run" rule | Rewarded-ad continue; interstitials only between Constellation Path levels; remove-ads purchase; cosmetic IAP; Play Billing integration | — | Full flow works in Billing's sandbox/test mode; **explicitly verify no ad can ever fire mid-run** — this is a testable pass/fail, not a guideline |
| **7 — Analytics** | Instrumentation before soft launch, not after | Every event in Section 18 wired to Firebase (or your chosen tool); a debug view to confirm events actually fire | Formal A/B test infrastructure | Every Section 18 event fires at least once in one manual playthrough and is visible in the tool's debug console |
| **8 — Optimization** | Confirm it runs well on real hardware, not just your dev phone | Device-tier particle budgets; confirm real vsync-aligned frame pacing on at least one low-end physical device; cold-start time check; final APK size check | — | Stable frame rate on a representative low-end device; cold start well under a few seconds |
| **9 — Soft launch** | Real data before you scale spend or effort | Staged rollout via Play Console (small % of traffic); crash monitoring; first read of real D1 against Section 2.1's benchmark ranges | Paid UA, big content pushes | Crash-free sessions comfortably above 99%; D1 measured and compared honestly to benchmark, not assumed |
| **10 — Iteration** | Improve on evidence, not guesses | First A/B tests from Section 18 run against real cohort data; first content update shipped from soft-launch learnings | — | At least one test reaches statistical significance and changes a real parameter |

---

## 18. Analytics and experimentation

**Minimum event set** — enough to answer "is this actually good," nothing tracked for its own sake:

| Event | Why it matters |
|---|---|
| `tutorial_complete` | First successful PERFECT — did onboarding land at all |
| `run_start` / `run_end` (Free Play) | Session shape, average run length |
| `level_start` / `level_complete` / `level_fail` (Constellation Path) | Where in the level list players actually get stuck |
| `pin_result` (PERFECT/GOOD/MISS, sampled if volume is too high to log every tap) | The raw skill-curve data everything else is built from |
| `session_length`, `levels_per_session` | Session shape over time, not just on day one |
| `retry_immediate` (restart within a few seconds of a run ending) | The clearest "one more try" signal you have — if this number is high, the core loop is working regardless of anything else |
| D1 / D7 / D30 retention | Compare honestly against the ranges in Section 2.1 — don't compare against the inflated "40/20/10" folklore number without checking which source it came from |
| `ad_impression`, `ad_reward_claimed` | Ad engagement and rewarded-ad completion rate |
| `iap_purchase` | Conversion, plus which cosmetic actually sells |
| Churn flag (no session in 3/7/14 days) | Feeds re-engagement messaging |
| `difficulty_spike` (auto-flagged where fail rate on a level crosses a threshold) | Finds a broken level *because the data says so*, not because someone happened to notice |

**A/B tests worth running, in rough priority order:**

1. **PERFECT window width** — the single number most likely to make or break the core feel
2. **When dead pegs/branches are introduced** (Section 13's staging is a starting hypothesis, not a fixed answer)
3. **Whether the rising-melody audio layer actually affects retention**, or is a nice-to-have that doesn't move the number — worth knowing honestly rather than assuming Section 10's centerpiece idea works just because it sounds good on paper
4. **Coins-per-PERFECT** (reward frequency)
5. **Constellation Path level length** (shorter, more numerous levels vs. fewer, longer ones)
6. **Interstitial frequency** (every level vs. every second level) — weigh this one particularly carefully against the ad-fatigue pattern documented in Section 2 of the design doc
7. **Dark-glow theme vs. an alternate lighter theme** (visual style)

---

## 19. MVP vs. later

| Bucket | Items |
|---|---|
| **MUST HAVE for MVP** | Core Taut loop (tap-to-pin, 3-tier grading, fray/lives); Free Play endless mode; basic difficulty scaling (even without the full Constellation Path generator yet); real pluck/fray audio (the melody layer can be simplified for v1); ported haptics/particles/camera; one persisted high score; app icon |
| **SHOULD HAVE** | Constellation Path levels 1–30; coins + 2–3 real cosmetics; daily challenge; rewarded-ad continue |
| **NICE TO HAVE** | Full cosmetic shop breadth; Constellation Gallery; branch-point levels (100+ tier content); leaderboards/social features |
| **DO NOT BUILD YET** | A Room-based save schema (DataStore is enough until something concrete proves otherwise); multiplayer/social of any kind; a level editor; a formal experimentation platform (basic event logging is the MVP bar — a full A/B testing harness is a later-milestone problem); any subscription monetization model |

The brief's own instinct here is correct and worth restating plainly: **the first playable build should exist to answer one question — is tapping a pulse onto a peg, alone, with nothing else built yet, actually fun to do fifty times in a row?** Everything from Milestone 2 onward is only worth building if the answer is yes.
