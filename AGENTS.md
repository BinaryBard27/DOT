# AGENTS.md

## Project

Android game (Kotlin), being redesigned from a Tower-Bloxx-style stacking
game ("Sway Stack") into a new concept ("Taut") — a one-tap string-tension
timing game.

## Reference docs

- `docs/game-design-blueprint.md` — full game design: mechanic, market
  research, art direction, level design, monetization. Read the relevant
  section before starting work in that area, don't assume.
- `docs/codex-implementation-plan.md` — technical architecture and the
  milestone build order. Follow milestone order — don't build Milestone 3+
  work before earlier milestones are actually done.

## Working agreements

- Reuse existing systems (GameEngine's sine-wave math, camera lerp,
  haptics, squash/stretch animation, particle system) wherever the
  underlying math transfers. Don't rewrite what already works.
