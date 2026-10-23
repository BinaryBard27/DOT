# Sway Stack → Next Game: Design Blueprint

Everything in this document is based on actually decompiling your `app-debug.apk` (package `com.swaystack.app`, "Sway Stack", version 1.0.1 build 2) and on live web research done today, not on guesses about what a game like this "probably" has. Where I say something is confirmed, I read it directly in your code. Where I'm giving an opinion, I say so.

## 0. Bottom line

**Your current mechanic (swinging-crane, tap-to-drop, tower stacking) is a well-built implementation of a 20-year-old genre that is not differentiated and carries real IP risk.** The code quality is genuinely better than the concept — you have working camera-follow, frame-rate-independent physics, tiered haptics, and hand-tuned squash-and-stretch animation. None of that is the problem. The problem is that what you built, mechanically and visually, **is Tower Bloxx (2005) with Stack's (2016) scoring system layered on**, down to the 3-lives structure, the sway-increases-with-height-and-error formula, and a drawn crane on screen. A competitor called "Blox: Stacking Game" is live right now doing the same swing-and-stack hybrid.

**Recommendation: keep the engineering, change the mechanic.** Don't polish this loop — replace what the player looks at and taps on, while reusing the physics/animation/camera/haptics systems you already debugged. Full reasoning in Sections 1 and 4. The winning concept, designed in full from Section 7 onward, is **Taut** — a one-finger, string-tension timing game where precision keeps a glowing thread taut (and in tune — good hits play a rising musical note) and error adds slack that compounds against you, structurally the same risk/reward as your current game, visually and legally nothing like it.

---

## 1. What's actually in your current build

Confirmed by decompiling `classes.dex` (jadx, unobfuscated debug build) and reading the manifest, not by playing a guess.

### 1.1 Technical facts

| Fact | Detail |
|---|---|
| Package / name | `com.swaystack.app`, "Sway Stack", v1.0.1 (build 2 — this is at least a second iteration) |
| Architecture | Single Activity, one custom `SurfaceView` (`TowerGameView`), one game-logic class (`GameEngine`), one renderer (`GameRenderer`). ~16 source files total. No navigation library, no menu screens, no settings screen. |
| Rendering | 100% procedural — every visual is a `Canvas` draw call (rects, circles, lines, one gradient). **Zero bitmap or vector image assets exist anywhere in the APK.** No `assets/` folder at all. |
| Game loop | A raw `Thread` calling `update()` then `drawFrame()`, then `Thread.sleep(16L)`. This is not synced to the display's actual refresh (`Choreographer`/vsync) — it's a fixed sleep regardless of how long the frame took. The simulation itself is frame-rate-independent (delta-time is computed and clamped correctly), but visible frame pacing isn't vsync-locked. This is a real but minor technical debt item, not a redesign issue. |
| Input | One `MotionEvent.ACTION_DOWN` handler. Genuinely one-tap — this part already matches what you're asking for. |
| Persistence | One `SharedPreferences` file, one key (`high_score`, an int). That's the entire save system. |
| Unused weight | The manifest declares Room, Retrofit, OkHttp, Moshi, and full Kotlin reflection on the classpath. **None of them are used by your code.** No `@Database`, no network call anywhere, and — confirm this is real, not an oversight — **the app has no `INTERNET` permission at all.** These are dead dependencies bloating a 10.9MB main dex for a game that draws rectangles. Worth stripping regardless of what you build next. |
| Monetization / ads / analytics | None. Zero ad SDK, zero analytics SDK, zero IAP/billing code. This is a completely clean, unmonetized prototype. |
| Audio | `android.media.ToneGenerator` — the **telephone dial-tone generator API** — is the entire sound system. PERFECT/GOOD/MISS each play a different DTMF-style supervisory tone. There are no sound files, no music, nothing resembling ASMR audio. This is the single biggest gap versus what you're asking me to design in Sections 9–10: right now the "audio system" is closer to an old phone keypad than to any ASMR game on the market. |

### 1.2 The mechanic, exactly as coded

1. A block hangs from a drawn crane and swings on a sine wave above the tower (`getSwayAngle()` — amplitude grows with tower height and with accumulated error).
2. One tap → a short fixed delay (`PRE_DROP`, not a hold/charge) → the block drops under gravity.
3. On landing, X-alignment against the top block is checked **in the tower's own rotated coordinate space** (a genuinely nice piece of engineering — the whole tower can be visually swaying and the collision math still works):
   - **PERFECT** (within 12% of block width): `multiplier × 10` points, multiplier and combo both increment, camera kicks up, 20-particle spark burst in a rotating hue, tiered haptic waveform, DTMF tone.
   - **GOOD** (within 75% of block width): flat 10 points, multiplier and combo reset to 0, **and this GOOD hit permanently adds to a "sway penalty" that makes the tower wobble more for the rest of the run** — imprecision compounds against you even when you technically didn't fail. This exact idea — precision keeps things stable, error compounds against future precision — is the one mechanical idea from this build worth carrying forward. It's also literally Tower Bloxx's design (see 1.3).
   - **MISS** (beyond 75%): lose one of 3 lives, block falls off-screen, multiplier/combo reset.
4. 3 lives, then game over. Tap to restart resets everything to zero — there's no meta-progression, no unlocks, no second mode.
5. Landing triggers a genuinely well-authored 3-phase squash-and-stretch animation (compress → overshoot/rebound → settle), with PERFECT hits getting full-strength animation and GOOD hits damped to 40%. This is hand-tuned curve work, not boilerplate — credit where it's due.

### 1.3 Don't assume it's good because it exists — here's the honest split

| | Item | Why |
|---|---|---|
| **KEEP** | Frame-rate-independent update loop (`dt` normalization, clamped at 50ms) | Correct engineering, reuse as-is |
| **KEEP** | Camera-follow lerp (Y tracks tower top, X tracks latest block, both ease at different rates) | Feels good, genre-agnostic, reuse the pattern |
| **KEEP** | Tiered haptics by outcome quality (waveform for PERFECT, short pulse for GOOD, strength-scaled pulse for MISS) | Already correctly matches "reward should feel different from failure" — just needs a real sound layer to match it |
| **KEEP** | 3-phase squash-and-stretch curve, damped by outcome quality | Real game-feel authoring skill, directly reusable on any object that "lands" |
| **KEEP** | The core *tension*: precision keeps you stable, error compounds against future attempts | This is the load-bearing psychological hook — see Section 3. Keep the principle, not the tower. |
| **KEEP** | The color language (sky gradient, terracotta roofs, amber window glow, green/red accent) | Genuinely pleasant "toy" palette for a 100%-vector art style — reusable even in a different mechanic |
| **MODIFY** | Procedural, zero-bitmap rendering approach | This isn't a limitation — it's your biggest scalability asset. It means new levels/reskins cost code changes, not commissioned art. Keep the *approach*, redesign what it draws (Section 11). |
| **REMOVE** | Room, Retrofit, OkHttp, Moshi, full Kotlin reflection from the classpath | Unused, just bloats the APK and build time |
| **REMOVE** | `ToneGenerator` as the audio system | Replace entirely — see Section 10 |
| **REBUILD** | The swinging-crane-drop-onto-a-tower mechanic and its visuals | Too close to two specific, well-known existing games — Section 4 |
| **REBUILD** | Game loop timing (`Thread.sleep(16)` → proper `Choreographer` or Compose frame callback) | Minor but should happen alongside a rebuild |
| **REBUILD** | The entire meta layer (menu, settings, save data beyond one int, any progression) | There isn't one to modify — it needs to be designed new, see Sections 12–14 |

---

## 2. What's actually working in this market right now

I researched this live rather than relying on memory (mobile game charts move fast). Real findings, with sources, organized by the pattern that matters rather than as a flat list of 20 — several of the genres you named turned out to collapse into the same few underlying patterns.

### 2.1 The macro shift you should know about

Hyper-casual (extremely simple, ad-only, 5-minute sessions) is being actively displaced by **hybrid-casual** — simple core mechanics with light meta-progression (unlocks, themes, missions) and *both* ad and IAP monetization. Industry analysts describe hybrid-casual as "the format actively replacing classic hyper-casual," because pure hyper-casual retention collapses by day 30 <cite index="6-1">even as pure hyper-casual downloads remain enormous — the category accounted for roughly 22 billion downloads in 2025</cite>. <cite index="66-1">Industry-wide, 2026 benchmarks put a realistic "good" retention profile at Day-1/Day-7/Day-30 of roughly 35%/15%/5%, with top-quartile games clearing 40%/20%/10%</cite> — but genre matters enormously: <cite index="68-1">hyper-casual specifically tends to land around 33% on day 1 but only about 4% by day 30, "because the loop was never built to hold anyone for a month."</cite> Some of the widely-circulated genre tables are stale 2022 data being recirculated, so treat any specific percentage as a directional target, not a guarantee — but the *shape* (hyper-casual gets people in fast and loses almost everyone by day 30) is consistent across every source I checked.

**What this means for you:** don't design a pure infinite-mode-only game. Build the reflex core hyper-casual players want, but wrap it in the light meta-progression hybrid-casual players stay for. This is already baked into the concept design below.

### 2.2 Stacking / swinging-crane games (directly relevant — this is your current genre)

| Game | Core mechanic | Why it worked / works | What to learn | What NOT to copy |
|---|---|---|---|---|
| **Stack** (Ketchapp, 2016) | Block slides horizontally, tap stops it; overhang is **physically cut off**, shrinking the block; a streak of perfect hits **regrows** block width | <cite index="18-1">Perfect streaks make blocks larger, giving more margin — but speed increases roughly every 15–20 points</cite>, so the reward (more room) and the punishment (less time) run against each other | The "reward = more margin, punishment = less time" tension is elegant and worth stealing as a *principle* | <cite index="21-1">The single most repeated complaint across real user reviews is ad frequency — "so many ads," ads triggering accidentally from mis-taps near the bottom of the screen, ads on every failure</cite> |
| **Tower Bloxx** (Digital Chocolate, 2005) | Crane swings a floor left-right, one-button drop, <cite index="24-1">poor alignment makes the tower sway more and become harder to build</cite> | <cite index="26-1">Combo meter fills on perfect drops; three missed drops ends a Quick Game</cite> | The compounding-instability idea (this is literally where your current sway-penalty formula comes from) | The crane, the sway formula, and the 3-lives structure are *specifically* what your current build reproduces — this is the direct IP-risk comparison, see Section 4 |
| **Blox: Stacking Game** (current App Store listing) | <cite index="32-1">"Realistic physics swing," combo system for chained perfect drops, blocks regrow on high combos</cite> | Confirms the swinging-crane-stack niche is being actively built **right now**, not just a 2005 relic | If you ship the current mechanic, you're not just competing with a 20-year memory, you're competing with a live 2026 release using near-identical language | — |

### 2.3 One-tap precision-timing games (adjacent to your current combo/tolerance system)

I found at least six near-identical live titles in a single search: <cite index="33-1">CrazyTaps ("tap when the moving target reaches the zone... margin for error gets smaller")</cite>, <cite index="34-1">Perfect Tap ("Perfect and Good hit zones," streaks, rewarded-ad continues)</cite>, <cite index="35-1">a second, unrelated "Perfect Tap" ("watch the white circle move and tap when it hits the center")</cite>, <cite index="36-1">1-Second Champion ("tap at exactly one second," daily challenges, ghost mode)</cite>, and <cite index="42-1">Tap On Time ("tap the screen exactly when your avatar aligns with the target")</cite>. **This genre is at least as saturated as stacking.** Simply moving from "tower" to "generic timing bar" doesn't solve your differentiation problem — it trades one crowded lane for another. This directly shaped which concept I recommend below: it needs a distinct sensory identity, not just a different shape of precision window.

### 2.4 "ASMR" mini-game collections (the label you're targeting — read this carefully)

Searching "ASMR mobile game" on Google Play right now surfaces almost entirely one sub-genre: <cite index="8-1">"tidy up / sort / clean / organize" collections — cleaning, sorting, skincare, pet grooming, all bundled into one app with "real ASMR sounds"</cite>. This is worth knowing before you brand anything "ASMR": <cite index="11-1">real user reviews of this exact sub-genre repeatedly call out ad load as the dealbreaker — "the random ads interrupting gets pretty annoying," "so many adds, do not download"</cite>, and the trend holds even for major-publisher output: CrazyLabs' **ASMR Slicing** is professionally produced (3D kinetic-sand cutting, haptics, real sound design) and still draws criticism — <cite index="49-1">one detailed review found roughly half its levels have "total lack of structure... no goal. no purpose," calling it "a wildly stilted experience" despite good production values</cite>.

**Read on this: "ASMR game" currently signals "cheap ad-farm mini-game collection" to a chunk of the install base, not "premium sensory product."** There's a smaller, higher-quality lane — <cite index="15-1">reviewers researching "most satisfying" games point instead to titles like PowerWash Simulator (spray ASMR) and Unpacking (organizing + narrative) as the premium end of the same instinct</cite> — fewer, better-crafted interactions, not a mini-game buffet. Aim for that lane, not the sort-collection swarm.

### 2.5 Sorting / pouring games (water sort)

<cite index="53-1">Water-sort games ("tap any cup to pour colored liquid into another... savor the tranquil pouring water sound effects") are a huge, heavily cloned category</cite> — I found five near-identical titles in one search. It's also, for the third time in this research, dogged by the same specific complaint: <cite index="55-1">"the number of ads is ridiculous and $20 to get rid of forced ads," "you will spend more time watching ads than playing the game"</cite>. Three unrelated genres, three independent sets of real user reviews, one consistent complaint. That's not a coincidence — it's a warning worth taking seriously for Section 15 (monetization).

Mechanically, water-sort is a **multi-step planning puzzle** (you can get stuck, need undo), not a reflex game — useful as a contrast: "pouring" as a sensory material is proven appealing (the phrase "tranquil pouring water sound" is literally in that app's own marketing), but it doesn't have to be built as a slow logic puzzle. That distinction fed directly into concept design below.

### 2.6 Cutting / slicing ASMR

Covered above (2.4) via ASMR Slicing. Real, validated sensory category (knife-through-material sound and visual is consistently called "satisfying" across reviews), but the market leader's own weakness — levels with no goal, "plays on autopilot" — is a specific, avoidable mistake: **every level needs a legible target, even in a sensory-first game.**

---

## 3. The psychological loop, stripped of any one game's skin

Across every genre above, the games that hold up long-term share the same four-part loop, not just "make it satisfying":

**Input → Anticipation → Feedback → Consequence-that-compounds.**

- **Input**: one deliberate action (tap, or tap-and-release), never more.
- **Anticipation**: something is moving or building toward a decision point *before* you act — this is what makes a static "tap the button" boring and a swinging/flowing/growing object compelling. Your current sway math is a correct anticipation engine; keep the *idea*.
- **Feedback**: sensory confirmation graded in at least 3 tiers (not pass/fail) — sound, color, particle, haptic, and score should all agree with each other on how well you did.
- **Consequence that compounds**: the single most important, most consistently reused idea across Stack *and* Tower Bloxx *and* your current build — **imprecision doesn't just cost you points, it makes the next attempt harder.** This is what turns "tap the thing" into "protect what you've built." It's the one mechanical DNA strand worth carrying into whatever you build next, independent of genre skin.

Everything below is designed against this loop, not against "stacking" or "ASMR" as fixed categories.

---

## 4. Should you keep the stacking mechanic? No — here's exactly why, not vague legal caution

You asked me not to give vague IP advice, so here's the specific comparison instead of a lawyer-referral non-answer.

| Element in your build | Same element in Tower Bloxx | Same element in Stack |
|---|---|---|
| Block swings on a visible crane before drop | <cite index="24-1">"Each floor is carried by a crane and swings from side to side before the player drops it"</cite> — identical | — |
| One-tap release, timing-based | <cite index="27-1">"you do this by dropping them from a crane... it's crucial to get the timing right"</cite> | <cite index="22-1">"Tap the screen to stop the moving block at the right moment"</cite> |
| Tower sways more as it gets taller / as errors accumulate | <cite index="24-1">"poorly placed floors make the tower sway more and become harder to build"</cite> — your `totalSwayPenalty` formula is this exact idea, coded | — |
| Exactly 3 lives, miss = lose one | <cite index="25-1">"If you drop a block and miss the tower completely, then you lose one of your three lives"</cite> — identical count | — |
| Perfect alignment → multiplier / combo meter | <cite index="26-1">"a combo meter, which fills up whenever you make a perfect drop"</cite> | <cite index="18-1">Perfect streaks widen the block, functionally the same reward-for-precision idea</cite> |

That's five separate, specific mechanical and visual choices, not one loose similarity. This isn't "the genre is crowded" — it's that a specific existing game's specific systems were reconstructed closely enough that a reviewer or a competitor's lawyer could point to each row above. Add to that a **live 2026 competitor** doing the same hybrid (**Blox: Stacking Game** — Section 2.2) and continuing to build this exact loop is both a legal exposure question and a "why would anyone pick yours" question.

**What to do about it, concretely:**
- **Remove**: the drawn crane, the vertical tower silhouette, the exact "3 lives / sway penalty / multiplier" combination as a bundle.
- **Keep**: the *principle* of compounding instability from imprecision (Section 3) — principles aren't protectable the way specific execution is, and it's a good principle.
- **Change the axis**: your tower grows *up*. Move the growth to a horizontal or diagonal axis and the whole silhouette stops reading as "stacking game" at a glance, which matters as much for App Store screenshots as it does for legal distance.
- **Change what's swinging and why**: a pendulum-on-a-crane reads as Tower Bloxx specifically. The same underlying sine-wave timing skill (which you've already built and debugged) can drive a completely different visual metaphor — Section 7 does exactly this.

---

## 5. Exploring the 1D space properly

You asked for genuine exploration here, not a token paragraph before jumping to concepts. The mechanics you listed (lines, arrows, timing, chains, flow, cutting, pulling, sliding, connecting) split cleanly into three families:

| Family | What the player is actually doing | Sensory home | Existing precedent |
|---|---|---|---|
| **Timing-under-tension** | Catch a moving/oscillating thing at the right instant | Snap, pluck, chime | Stack, Tower Bloxx, the whole "Perfect Tap" cluster (2.3) |
| **Continuous-guided-motion** | Track or drag something smoothly along a path, precision measured continuously not at one instant | Pour, peel, cut, slide | Water sort, ASMR Slicing, fruit-slicing games |
| **Assembly-under-constraint** | Fit a piece into a shrinking/specific space | Click, seat, lock | Stack (block-shrinking variant), jigsaw/nesting games |

Your current build sits entirely in family 1. The strongest ASMR opportunity is actually family 2 (continuous motion maps naturally onto continuous sound — a pour, a cut, a peel all *sound like something happening*, not just a beep on success). The concept selected below deliberately blends families 1 and 2: a single timed "pin" action (family 1, so it stays true one-tap) whose *material* and *audio result* behave like family 2.

---

## 6. Six original concepts

Ten were requested; I'm giving you six with real depth instead of ten with four of them being filler, per your own "do not pad" instruction. Each is genuinely distinct in silhouette, material, and failure state — not the same loop with a new color palette.

### 6.1 Taut *(recommended — full design from Section 7 on)*
**Pitch:** Pin a pulse of light to a taut string, in time, before it drifts loose — perfect pins keep it taut and add a note to a rising melody; missed pins add slack that makes the whole string wobble.
**Core mechanic:** Oscillating "pulse" travels toward the next anchor peg on a sine-wave path (direct reuse of your existing sway math); one tap pins it. **Input:** single tap. **5-second read:** a glowing bead swings toward a dot; tap when it's over the dot. **ASMR:** plucked-string/chime audio, pitch rises with a good run — the loop trains you to want to hear it stay in tune. **IP risk:** low — no tower, no crane, no vertical stack; a string/constellation growing sideways reads nothing like Tower Bloxx or Stack on a screenshot.

### 6.2 Pour Line
**Pitch:** Seal a pour of glowing liquid into a chain of vessels at exactly the right fill line — overfill sloshes into the next vessel and destabilizes it.
**Core mechanic:** Liquid streams continuously from above; tap to cut the pour; fill line accuracy graded PERFECT/GOOD/MISS. **ASMR:** pouring/splash audio — a validated appealing sound (multiple water-sort listings market this exact phrase). **Downside vs. Taut:** overlaps mechanically with water-sort's "pour" language (2.5) — needs strong visual differentiation to avoid reading as a water-sort clone.

### 6.3 Ribbon Cut
**Pitch:** A ribbon of dough/clay/light unspools at a steady pace; tap to slice it into equal segments without a miscut breaking your rhythm.
**Core mechanic:** Continuous scroll, timed tap-cuts, segment-length precision graded. **ASMR:** slice/cut audio, a proven category (2.6). **Downside:** closest of the six to "ASMR Slicing" in spirit, though the ribbon/segment framing (vs. free-form object cutting) is a meaningfully different game, not a reskin.

### 6.4 Peel
**Pitch:** Tap-and-hold to peel a strip of film off a surface at a steady rate; release exactly at the marked edge — peel too fast and it tears, breaking your streak.
**Core mechanic:** Hold-based (not pure tap) continuous-precision peel, released at a target point. **ASMR:** the "satisfying peel" is one of the single most-viewed categories in ASMR/oddly-satisfying video content generally, though it's less proven specifically as a *game* mechanic — genuine novelty, genuine risk. **Downside:** hold-based input is a small deviation from "pure one-tap" — still one-finger, but not a discrete tap, so it changes the muscle-memory feel you said you want.

### 6.5 Splice
**Pitch:** Two ends of a broken circuit/thread drift toward each other; tap at the exact moment they align to fuse them, building an unbroken line across the screen.
**Core mechanic:** Two moving elements, one timed convergence tap. **ASMR:** electric/magnetic "snap-together" sound design, spark visual. **Downside:** convergence-of-two-objects is a harder 5-second read than "one thing swings, tap it" — more cognitively loaded than the brief's <5-second bar really wants.

### 6.6 Bead Chain *(included to show what I rejected, and why)*
**Pitch:** Beads slide in on a track and snap onto a growing necklace when tapped at the right position.
**Why it's here:** it's the most direct "just reskin the current game" option — same swing-and-catch input, same chain-building visual language, lowest engineering cost. **Why it's not the pick:** a chain of beads growing in a line is still, structurally, a stacking game with a different noun. It doesn't solve the differentiation or IP-distance problem from Section 4 — it just changes what the blocks are called.

### Scoring (1–10, before the final pick)

| Concept | Addictive | Simple | ASMR | Retention | Replay | Prod. cost | Original | Monetization | Viral | **Overall** |
|---|---|---|---|---|---|---|---|---|---|---|
| Taut | 8 | 9 | 8 | 8 | 8 | 7 | 8 | 7 | 7 | **8.0** |
| Pour Line | 7 | 8 | 8 | 6 | 6 | 6 | 5 | 6 | 6 | 6.4 |
| Ribbon Cut | 7 | 8 | 7 | 6 | 6 | 6 | 5 | 6 | 6 | 6.3 |
| Peel | 6 | 6 | 8 | 5 | 5 | 5 | 7 | 5 | 6 | 5.9 |
| Splice | 6 | 5 | 6 | 6 | 6 | 6 | 7 | 5 | 5 | 5.8 |
| Bead Chain | 7 | 9 | 5 | 6 | 6 | 8 | 3 | 6 | 5 | 6.1 |

**Top 3: Taut, Pour Line, Ribbon Cut.**

---

## 7. Why Taut wins

Not picked on "fun" alone, per your instruction — scored against all seven factors you listed:

- **Fun**: highest addictiveness/simplicity combination of the six, and it's the only one that keeps pure single-discrete-tap input (Peel and Splice both compromise this).
- **Retention**: the musical-feedback layer (pitch rises through a scale on a clean run, resets/dulls on error) gives a second axis of mastery beyond score — you're not just chasing a number, you're chasing a *sound*, which is a stronger habit hook than score alone.
- **Production feasibility**: this is the deciding factor most people skip. Taut can reuse, almost line-for-line, your existing sine-wave oscillation math, your camera-lerp system, your squash-and-stretch curve, and your tiered-haptics dispatch — all of which are already written and debugged in `GameEngine.kt`. Pour Line and Ribbon Cut need new continuous-motion collision math from scratch. Given your available time, this matters more than the score table suggests.
- **Differentiation**: no tower, no crane, no vertical growth axis — the string/constellation silhouette doesn't read as any of the games in Section 2 on a store listing screenshot.
- **Monetization**: natural cosmetic surface (string materials, note/instrument packs, background themes) without needing to touch the core loop — see Section 15.
- **Visual identity**: "a glowing thread of light tuning itself into a constellation" is an ownable, screenshot-friendly identity in a way "another set of taut circles" isn't.
- **ASMR satisfaction**: plucked-string/chime audio is an established satisfying-sound category (reviewers specifically called out "the soothing chime of a perfect fit" as the best part of Stack, with no music system at all behind it — Taut is that instinct taken seriously and built as the actual point of the game, not an afterthought.
- **Scalability**: procedurally generating peg patterns is cheap (position + spacing + oscillation-speed data, no art), matching your existing zero-bitmap art pipeline exactly.

---

## 8. Taut — the complete gameplay loop

**Core mechanic, precisely:** A glowing pulse travels along a sine-wave arc toward the next anchor peg. One tap "pins" it. Distance from the peg's center at tap-time determines PERFECT / GOOD / MISS (same three-tier structure as your current build, same tolerance-as-percentage-of-object-size approach — this part of your tuning transfers almost directly). PERFECT: string segment locks taut and bright, a note plays (pitch = current position in a rising scale), multiplier and combo increment. GOOD: segment locks but dim, plays a flat/muted note, multiplier resets, adds to an accumulating "slack" value. MISS: string frays at that point (spark/snap visual), costs one of 3 "frays" (same forgiveness structure as lives), and briefly increases the pulse's oscillation speed for the next attempt. Accumulated slack increases global oscillation amplitude and speed — precision debt compounds, same principle as your current sway penalty, new visual.

| Loop | Definition for Taut |
|---|---|
| **Core Loop** | Watch pulse swing → tap at the peg → get graded → string extends |
| **Reward Loop** | PERFECT → note + light + haptic + multiplier, all agreeing → "that felt right" |
| **Progression Loop** | String extends across the screen (camera pans, reusing your existing lerp) → new peg patterns introduce new rules every ~10 levels (Section 13) |
| **Meta Loop** | Runs end (3 frays) → coins earned from notes hit → spend on string materials / note packs / background themes (Section 14) → next run looks and sounds different |
| **Retention Loop** | Daily "tuning" challenge (fixed peg pattern, same for everyone that day) + a "longest clean run" personal-best chase |

| Moment | What happens |
|---|---|
| **Second 0–5** | Title fades, one glowing peg and one pulse visible, pulse starts swinging immediately — no button to press to "start," the swing itself is the invitation. First tap is the tutorial. |
| **First level** | 5 pegs in a straight horizontal line, generous PERFECT window (~20% of peg spacing), pulse moves slowly. Designed to be unmissable — first-session confidence matters more than challenge here. |
| **First 5 minutes** | Player learns: tap timing, that PERFECT sounds/looks different from GOOD, that a MISS frays but doesn't instantly end the run, and that 3 frays end it. |
| **First 30 minutes** | Unlocks the second peg pattern (gentle vertical offset — Section 13), earns enough coins for the first cosmetic string material, sees the daily challenge for the first time. |
| **Day 1** | Push notification (opt-in) built around loss aversion done honestly: "your longest run today: 14 pegs" rather than a fake-urgency dark pattern. |
| **Day 7** | Enough coins/mastery to have unlocked 3–4 string materials and the first "instrument" note-pack; daily-challenge streak counter now visible. |
| **Long-term** | A gallery of completed "constellations" (screenshots/replays of best runs) becomes the collection layer — mastery is visible and shareable, not just a number. |

---

## 9. Making it "stupidly satisfying" — the sensory spec

For every player action, Input → Anticipation → Impact → Release → Reward:

| Action | Animation | Timing | Screen movement | Particles | Color | Sound | Haptic |
|---|---|---|---|---|---|---|---|
| Pulse approaching peg | Gentle pulse-glow breathing (scale 0.95↔1.05, 2s cycle) | Continuous | None | Faint trailing sparkle | Cool white-blue | Low rising hum, pitch tracks proximity to peg | None |
| **PERFECT pin** | Peg flashes bright, string segment snaps taut with a slight overshoot-then-settle (reuse your existing 3-phase curve, full strength) | Instant on tap | Camera micro-kick (reuse existing Y-offset bump) | 20-particle burst along the string, matches your existing spark system | Segment locks to a bright, saturated hue keyed to position (reuse `HSVToColor` rotation you already have) | Clean plucked-string note, pitch = Nth note in scale | Sharp waveform (reuse your existing PERFECT haptic pattern) |
| **GOOD pin** | Segment locks but with visible slack (slight sag curve), damped animation (40% strength — reuse existing damping) | Instant on tap | None | Small dim sparkle, no burst | Desaturated version of the hue | Muted/detuned note | Single short pulse (reuse existing GOOD haptic) |
| **MISS / fray** | String visibly frays — a small tear/spark animation at that segment | Instant | Brief screen-shake (new, small) | Spark shower, red-tinted | Segment flashes red then fades to grey | Dissonant pluck / string-snap sound | Strength-scaled pulse (reuse existing MISS haptic, strength already tied to impact velocity in your code) |
| Run ends (3rd fray) | String stops glowing, fades to a dim outline | 400ms fade | Camera settles | None | Desaturate whole scene | Final note resolves down (not up) — signals "over" without being punishing | None |

Keep it clean: **no more than one particle burst and one screen-reactive effect per action.** Your current build already avoids clutter (flat colors, no overlapping VFX) — preserve that discipline, it's a real strength, not a limitation to fix.

---

## 10. ASMR audio system

Replacing `ToneGenerator` entirely. Specification, not a vague "make it sound nice":

| Sound | Trigger | Design |
|---|---|---|
| Ambient bed | Constant during play | Very low, slow filtered pad/drone — sits under everything, never competes |
| Approach hum | Pulse nearing peg | Pitch rises smoothly as pulse approaches the peg's tap window, gives an *audio* cue for timing, not just visual — important for accessibility and for "eyes-off-screen" play |
| PERFECT pluck | PERFECT pin | Clean, resonant plucked-string note (think kalimba/harp, not guitar — cleaner attack, longer satisfying decay). Pitch = next note in the current scale (starts at a root note, climbs one scale degree per consecutive PERFECT, wraps/resolves at the octave) |
| GOOD pluck | GOOD pin | Same instrument, deliberately slightly flat/muted — the *ear* should notice something's off before the player consciously registers "that was only GOOD" |
| Fray / MISS | MISS | Short dissonant string-snap, low and textured, not harsh — this should feel like a consequence, not a punishment (avoid the "jarring buzzer" failure a lot of hyper-casual games lean on) |
| Combo milestone | Every 5th consecutive PERFECT | A short chime layered on top of the pluck — audibly marks streaks without needing to look at a UI number |
| Run end | 3rd fray | Descending resolve phrase, 1–2 seconds, signals closure |
| UI taps | Menu/button interaction | Soft, short, consistent click — should feel like part of the same instrument family as gameplay sounds, not a generic system beep |

**Dynamic layering rules:**
- **Speed**: as pulse oscillation speeds up (from accumulated slack or level design), the approach hum's pitch-rise happens faster — audio speed tracks visual speed automatically.
- **Combo**: every consecutive PERFECT adds a very quiet harmonic layer under the ambient bed, builds up to ~5 layers then holds — a purely audio sense of "building something."
- **Accuracy over a whole run**: at run-end, the final chord played is built from the notes hit during that run — a clean run literally produces a nicer-sounding final chord than a sloppy one. This is the single most "designed for ASMR" idea in this spec: the *quality of your play* directly generates the *quality of the music*, not just a score number.

**Practically:** you said you'll generate assets with AI tools. For audio, a small set of source notes recorded/generated once (a clean plucked-string sample across one octave, one detuned variant, one snap/fray sound, one chime, one drone loop) covers the entire system via pitch-shifting in code — this is cheap to produce and matches your low-content-cost requirement directly.

---

## 11. Art direction

**Style:** bright, glossy, candy-colored vector art on a light pastel background. This replaces the dark-neon direction earlier in this project's process — an early reference image pointed that way, but the confirmed direction is fully bright and colorful throughout, matching the actual visual language of successful ASMR games rather than a moody premium-dark look.

| Element | Spec |
|---|---|
| Background | Soft pastel gradient (cream fading to pale sky blue, or a similar light 2-stop combination) — no dark areas anywhere on screen. Brightness itself is the point here, the opposite load-bearing choice from the earlier dark direction |
| Pegs | Round, glossy bead shapes (not thin rings), with a soft highlight suggesting light catching a glossy surface. Fully saturated candy colors — hot pink, turquoise, sunshine yellow, coral, violet — color keyed to position using the existing hue-rotation math |
| String/thread | A thick, glossy ribbon rather than a thin glowing line — reuse the existing path-drawing approach, but with a solid saturated fill plus a highlight stroke along one edge to suggest a rounded, glossy cross-section, instead of a soft outer blur |
| Pulse (moving object) | A glossy bead with a warm highlight; a small confetti-style sparkle burst on a good hit, rather than fine neon sparks |
| Typography | One clean geometric sans, rounded weights (matches the soft, tactile brief) — Poppins or Quicksand, free and license-free |
| Particles | Reuse the existing `Particle.java` class, switch the palette to bright confetti colors, and consider slightly larger/chunkier particles to match the toy-like rendering rather than fine neon sparks |
| UI | Rounded-rect panels, light frosted-white background (not dark glass) — same rounded-rect HUD technique already built, new light color values |

**Scaling to hundreds of levels without hand-made art:** every visual element above is parametric (position, hue, glow intensity, count) rather than a fixed sprite. A new "level" or cosmetic theme is a data change (new hue range, new peg-spacing pattern, new particle color), not a new asset commission — this is the direct payoff of keeping your existing procedural-rendering approach instead of switching to sprite-based art.

**Asset specs for AI generation** (you mentioned Nano Banana / image tools — these apply to the small amount of *reference/marketing* art you'll still want, since gameplay itself stays procedural):

| Asset | Purpose | Dimensions | Style notes |
|---|---|---|---|
| App icon | Store listing | 512×512, adaptive icon safe zone | Single glowing thread across a dark circle, minimal, must read at 48px |
| Feature graphic | Play Store listing | 1024×500 | Constellation of a completed run, dark background, no text baked in (Play Store overlays text) |
| Screenshot frames (x5) | Store listing | Device-frame, portrait | Show progressively more complex constellations, not just the same early state |

---

## 12. Level generation system

**Structure:** two modes, both drawing from the same procedural generator.

- **Free Play** (default, matches hyper-casual expectations for instant play): endless, escalating via the difficulty curve (Section 13), ends at 3 frays, this is where most sessions happen.
- **Constellation Path** (the actual "level" structure requested): discrete, numbered levels with a specific peg pattern and a target (reach peg N, or finish with slack under a threshold). This is what satisfies "level generation" as a real system rather than just difficulty-over-time, and is what unlocks the meta-progression hybrid-casual needs (Section 2.1).

**Generation parameters** (everything below is data, not hand-placed art):

| Parameter | Range / behavior |
|---|---|
| Peg count | 5–40 per Constellation Path level |
| Peg spacing | Base spacing × per-level multiplier, slightly randomized (±10%) per peg so no two levels feel like a ruler |
| Vertical offset pattern | Flat → gentle sine offset → alternating zigzag → one-bow arc and monotonic staircase → independent scatter (introduced progressively, Section 13) |
| Oscillation speed | Base speed × level multiplier; the per-level ramp is 50% steeper than the prototype curve |
| PERFECT window | Shrinks from 20% to 8% of peg spacing across levels 1–30, then to a 6% floor by level 60 and holds |
| Dead pegs (skip zones) | 0 below level 15; 1 per 10 pegs from level 15; must be introduced with a clear visual tell (dim/red-tinted) — never a "gotcha" |
| Branch points | 0 below level 35; from level 35, occasional forks where tap-timing (early/late within the PERFECT window) determines route |

**Validation/solvability:** since this is a skill-timing game (not a logic puzzle with dead-end states), "solvability" means **every generated level must be completable by a player hitting every PERFECT window exactly** — validate this in code by simulating a "perfect bot" run against the generated peg/timing data before a level ships (a short script, not manual QA per level).

**Difficulty scoring:** a simple weighted formula — `difficulty = (1/PERFECT_window_size) + (oscillation_speed) + (dead_peg_density × 2) + (branch_presence × 3)` — used to sort generated levels into Beginner/Intermediate/Advanced/Expert buckets automatically rather than hand-tagging each one.

**First 30 levels, concretely:**

| Levels | Peg pattern | New rule introduced |
|---|---|---|
| 1–10 | Straight, sine-wave, and zigzag rotation | Basic tap-timing and spatial tracking |
| 11–34 | Arc and monotonic staircase join the rotation | Read broad geometry versus repeating movement |
| 15–30 | Dead pegs join the staged shapes | Discrimination — don't tap everything that swings past |
| 31–34 | PERFECT window reaches 8% | First real precision checkpoint |
| 35–40 | Independent scatter and branch points join | Combination pressure and route awareness |

---

## 13. Difficulty curve

Matches your instruction to avoid "difficulty = just faster":

| Levels | New element |
|---|---|
| 1–5 | Pure timing, wide windows, flat pattern |
| 6–10 | Vertical offset (spatial tracking added) |
| 11–14 | Arc/staircase shapes enter the rotation |
| 15–30 | Dead pegs (discrimination), window narrows to 8% |
| 31–34 | Window tightens from 8% toward its floor |
| 35–60 | Scatter and branch points enter; window tightens to 6% |
| 61+ | All six shapes, 6% PERFECT floor, and continued speed pressure |
| 100+ | Reverse-direction pulses, procedurally remixed combinations of every prior rule — this is the mastery/endurance tier, not a new mechanic |

---

## 14. Retention and progression

Only systems that support the core loop — nothing added for the sake of looking complete:

| System | What it does |
|---|---|
| Coins | Earned per note hit (PERFECT > GOOD), spent on cosmetics only — never pay-for-power |
| String materials (cosmetic) | Visual/audio skins for the thread itself — changes glow color and the instrument timbre (e.g., a "bell" material sounds different from a "string" material) |
| Note packs (cosmetic) | Changes the instrument used for the pluck sound — same mechanic, different ASMR flavor, cheap to produce (Section 10) |
| Daily Tuning Challenge | One fixed, same-for-everyone peg pattern per day — direct comparison with friends, not just a personal best |
| Streaks | Daily-challenge participation streak, visible but not punishing if broken (no "lose your progress" mechanic — those are a well-documented retention *and* goodwill risk) |
| Constellation Gallery | Auto-saved screenshot/replay of your best runs — this is the "collection" layer, and it's free to build (you're already rendering the scene; save the state, not a new asset pipeline) |
| Endless mode personal best | Longest clean run (fewest frays used), separate leaderboard from Constellation Path progress |

---

## 15. Monetization

| Rule | Detail |
|---|---|
| **Rewarded ads** | Offered *only* after a run ends (3 frays) — "watch an ad to continue this run" (one extra fray, capped at once per run). Never interrupt an in-progress run. |
| **Interstitial ads** | Capped, and only between Constellation Path levels — never mid-swing, never immediately after a MISS (this is the exact complaint pattern from Section 2.2/2.4/2.5 — "an ad every time I fail" is the single most repeated user complaint across three unrelated genres I researched today; do not repeat it) |
| **Remove-ads purchase** | One-time, priced normally (~$2–4) — simple, no subscription needed for a game this size |
| **Cosmetic purchases** | String materials and note packs beyond what's earnable by play — this is the main IAP surface, and it doesn't touch difficulty or fairness |
| **Starter pack** | One optional bundle (a few premium cosmetics + ad removal) shown once, after a strong first session (e.g., first 10-PERFECT streak) — not on install |
| **Never** | Ads inside an active run; pay-to-win timing windows or speed reduction; forcing an ad to progress past a level |
