# FORTRESS — a defensive Battlecode 2026 bot

Clean-sweep rebuild from `genesis`. This plan supersedes the old one entirely.
Package stays `wongcallum`; **FORTRESS** is the strategy identity.

This document is grounded in `docs/specs.md` (rules), the three BC26 postmortems
(`team_food_*`, `lorem_ipsum_*`, `outercloud.dev-*`), prior-year pathfinding
notes (`c0nrad.io-pathfinding.md`), and a read of the three aggressive reference
bots we benchmark against (`src/SPAARK`, `src/Version41`, `src/finalsbot`).

---

## 1. Thesis: in this game, defense is the favoured strategy

A dead king = **instant game loss**. Living kings are the single heaviest
scoring term in the phase that almost every game ends in:

| Term | Cooperation weight | Backstab weight |
|------|:--:|:--:|
| % damage to cats | 0.5 | 0.3 |
| **% living rat kings** | 0.3 | **0.5** |
| % cheese transferred | 0.2 | 0.2 |

Every postmortem agrees **most games end in backstab**, and `food` (top-12)
states plainly that *survival is the most important element of the macro* since
most games end before tiebreaker. So the dominant lever is: **keep our king
alive to round 2000.** A bot that guarantees survival wins the 0.5 king term
outright against opponents who lose kings to overextension.

The decisive insight is `food`'s combat theory: **the passive/reactive side
beats the aggressive side in rat combat.** The aggressor walks into invisible
rat traps (50 dmg + 3-turn stun, hidden from the enemy) and into your attack
range; the defender punishes it. Two passive teams stalemate, but against an
*aggressive* opponent the passive team wins the trades. All three of our local
benchmarks (SPAARK, Version41, finalsbot) are aggressive. **Defense is not a
handicap here — it is the counter.**

### Our identity in one paragraph
We never initiate a backstab. We forage efficiently, bank **cat damage with
cheap cat traps** (the most under-exploited points + tiebreaker lever — 10
cheese for 100 damage, placeable freely in cooperation), keep a large cheese
reserve, and hold our king safe and central. If the enemy backstabs us, our
trap-heavy reactive defense impales their aggression while we out-survive them.
If they never backstab, we win on cat damage + cheese. Either branch routes
through the same three pillars: **survive · economy · cat damage.**

---

## 2. Why each pillar, in numbers

- **Survive.** King: 500→max 600 HP, 3×3, consumes **2 cheese/round**, loses
  **10 HP/round** when global cheese hits 0, move cooldown 40, 360° vision r=25.
  Starvation is the #1 self-inflicted death — *never* let global cheese reach 0.
  Hold a fat reserve so we can also spawn-burst against a rush.
- **Economy.** 2500 starting cheese. Mines spawn 20 cheese in a 9×9 area with
  rising probability the longer they go unspawned. Raw cheese applies a
  `0.01 × amount` cooldown penalty — so deliver loads of ~40–100, not 500.
  Cheese funds traps, spawns, dirt, and upkeep; more cheese collected → more
  rats → more cheese (the snowball every postmortem names).
- **Cat damage.** Cat traps: 10 cheese, 100 damage, +stun, max 10 active,
  placeable in cooperation (and ≤100 rounds after *being* backstabbed). Cats
  have 4000 HP. `food` explicitly regretted not racking more cat damage — it is
  0.5 of the score in coop, 0.3 in backstab, **and** a tiebreaker input, and it
  doubles as king protection. We make it a first-class behaviour, not an
  afterthought.

---

## 3. How FORTRESS beats the three aggressive benchmarks

| Opponent | Their threat (from code read) | Our defensive counter |
|----------|-------------------------------|-----------------------|
| **Version41** (most dangerous) | Embedded defection; rallies 10+ rats to a distressed king via shared-array SOS; symmetry-guided attack vectors; forms extra kings for multi-front pressure; clears cats late. | King **SOS spawn-burst + standing trap ring**: a 10-rat rally charges into hidden traps (25 max, 50 dmg each) and a fresh spawn wave. King is **mobile + walls itself with dirt**, so it is never the sitting target their swarm needs. We run our **own** symmetry detection to know which side they come from and keep the king biased away from their spawn, toward center + our mines. |
| **finalsbot** | 9-direction heuristic micro; **ratnap chains that disable enemy spawning**; aggressive cheese stripping; trap pressure. | Keep a cheese reserve so spawn cost stays affordable even if rats are ratnapped — we are **not dependent on a baby-rat shield**. Defensive facing (no blind spots) + cheese-boosted HP-equalising so they can't ratnap us by position or by HP. **Counter-ratnap** their carriers to free allies. Fan out across *multiple* mines + king-mines so we aren't out-economied. |
| **SPAARK** | Adaptive; attacks when it has numbers; reactive traps; less coordinated. | Aggression-scaled micro: we only push when local numbers favour us, otherwise hold and let them step into trap range. Their less-coordinated harassment loses the trade war to a turtle that punishes advances. |

General anti-rush (proven sufficient by `lorem_ipsum` against *all* rush bots):
**spawn more rats when the king is threatened.** Simple and decisive.

---

## 4. Architecture (files under `src/wongcallum/`)

Keep it small, modular, and bytecode-aware. Sense once per turn; pass arrays into
helpers (never re-sense per query). Guard every action with `can*()`.

- `RobotPlayer.java` — entrypoint. `turnCount++`, dispatch on `rc.getType()`,
  global try/catch, **always `Clock.yield()` in `finally`**.
- `Const.java` — tunable constants + `Direction[]` table + seeded RNG.
- `Pathfinder.java` — turn-aware bugnav (see §6 V1).
- `Comms.java` — shared-array read/write (king is sole writer) + squeak helpers
  with cat-suppression; symmetry detection.
- `Sensing.java` — once-per-turn sense + small typed helpers (nearest king /
  cheese / mine / enemy / cat).
- `BabyRat.java` — the forager/defender FSM.
- `RatKing.java` — survival, spawning, upkeep, king defense.
- `Micro.java` — defensive combat scoring (used by both unit types).

**One rat job only: miner.** `lorem_ipsum`'s biggest survivability win was
deleting "scout"/"soldier" specializations — specialists got caught in 1-v-3s
and died. All rats forage; combat and defense are *behaviours* layered on the
miner, triggered by sensing, not separate unit classes.

---

## 5. Shared-array layout (64 ints, 0–1023 each; only kings write)

Adapted from `food`'s proven layout. Kings write; rats read once/turn.

| Slot | Purpose |
|------|---------|
| 0 | Map symmetry (unknown / H / V / rotational, once determined) |
| 1 | Starting king position (lossless if small map, else lossy) |
| 2–6 | Current positions of up to 5 kings |
| 7 | King **heartbeat** (each king toggles its bit; a missed toggle ⇒ that king is dead ⇒ clear its slot + SOS) |
| 8 | King **SOS** (one bit per king; set when it senses a threat) |
| 9 | Formation location for a new rat king |
| 10 | Number of discovered mines |
| 11–63 | Discovered mine locations |

Squeaks (radius 4, last 5 rounds, **also heard by cats who pounce the source**):
mine reports to the king, enemy locations during combat, formation rallies.
**Suppress squeaks when a cat is sensed.** Coordination (king formation, combat
recruiting) depends on squeaking — `lorem_ipsum` broke half its bot by toning
squeaking down, so keep it on except near cats.

---

## 6. Phased build (each phase = a committable checkpoint that beats the last)

Build tonight in this order. The two fundamentals that broke the previous
attempt were **movement** and **economy** — nail V1 and V2 before anything else.

**V0 — Skeleton & safe loop.** Copy `examplefuncsplayer` → `wongcallum`. Dispatch
+ try/catch/`finally`+`yield`. Empty `Const/Pathfinder/Comms/Sensing/Micro`.
*DoD:* runs a full game vs `examplefuncsplayer`, zero exceptions, no crash.

**V1 — Turn-aware navigation.** Rats have a facing direction; **forward** move =
cooldown 10, **strafe** (off-facing) = 18, **turn** = 10 (separate). The 90°
vision cone points along the facing. So every step: **turn to face, then
`moveForward()`** (cheap + sweeps the cone along travel); strafe only as a
fallback when turn cooldown isn't ready. Direction selection = bugnav: greedy at
the goal; when blocked, trace the obstacle with a consistent rotation until
closer-than-hit-point and the goal reopens. Lessons baked in:
  - **Don't reset bug state when the target moves a little** (a moving king is
    not a new target) — `lorem_ipsum`'s maze bug.
  - **Look left/right**: alternate turn-then-move / move-then-turn to widen
    vision for free (`outercloud`/`food`).
  - Treat **dirt as passable** and dig through it; wait a turn if still blocked
    after digging (`food`'s dig-then-stall fix).
  *DoD:* a rat crosses DefaultMedium corner-to-corner without orbiting or jamming.

**V2 — Economy (miner FSM: EXPLORE → COLLECT → RETURN).** Pick up adjacent
cheese; else path to nearest sensed cheese; else nearest **known** mine; else
explore. **Fan out** — each rat targets its own mine / random remote point and
re-rolls after delivery, so the swarm doesn't all pile on one mine (a previous
bug). Return at ≥40 raw; collect **up to ~100**, scaling the cap with proximity
to the king (`food` and `lorem_ipsum` both got big wins moving the cap 40→100).
Transfer to **all 9 king body tiles** (cheap; handles walls/range). King also
collects cheese it stands on / senses.
  *DoD:* on a replay, global cheese climbs steadily and the king sits at full HP.

**V3 — Spawning & upkeep (survival-gated).** Maintain a target live-rat count via
the cost curve (`cost = 10 + 10·floor(liveRats/4)`; estimate population from
cost). **Scale the target by map size** (smaller maps trade rats fast → fewer
rats; `food`'s late regret) **and by known mines**. Always hold a **cheese
reserve** below which we never spend — this both prevents starvation and funds
anti-rush spawn-bursts and late-game tiebreaker dumps (~round 1950).
  *DoD:* king never starves on any of the three default maps, both sides.

**V4 — Communication & symmetry.** Implement §5: heartbeat, SOS, king positions,
mines, symmetry. Symmetry detection by elimination (a sensed tile that violates a
symmetry rules it out; once two are eliminated, broadcast it). Rats squeak mine
finds to the king; king writes them to the array.
  *DoD:* rats converge on known mines from the array; symmetry resolves mid-game.

**V5 — King defense / anti-rush (the heart of the bot).** On threat (sensed enemy
or cat, or SOS): set SOS bit; **spawn toward the threat**; **retreat away** while
keeping central; **place dirt to wall a chasing cat** (`outercloud`: extremely
effective); **dig to escape** if boxed in; bite / drop a **trap** when adjacent.
Rats near a king hearing SOS switch to **DEFEND**. Cat escape = move
**perpendicular** to the cat's path while increasing distance (`food`: the king
is slower than the cat, so a straight retreat loses).
  *DoD:* king survives a scripted rush (bench vs a rush bot / Version41).

**V6 — Defensive combat micro.** Score the 9 moves (8 neighbours + stay).
Penalize: impassable/dirt, near cats, near higher-HP enemies, flying-rat paths,
strafing, **cardinal-to-an-enemy** (they can ratnap you next turn). Reward:
ratnap/finish/attack tiles, near the **enemy** king, closer to destination.
Action priority: **throw > ratnap > bite > trap**. Doctrine = **reactive**: hold
unless local numbers favour pushing; invert penalties only when defending our
king under SOS (charge the attacker to draw it off the king — `food`'s trick).
**Defensive facing** at end of turn so no enemy sits in our blind spot.
Cheese-boost bites to **finish** or to keep HP ≥ the enemy (denies their
ratnap-by-HP). Ratnap the **highest-HP** threat and throw it into walls/cats;
counter-ratnap enemy carriers. **Micro-exhaust timer**: if stuck "fighting" a
target through a wall for N turns, disengage (`outercloud`). **Never walk a lone
rat into a 1-v-many** (`lorem_ipsum`'s recurring death).
  *DoD:* win combat-map exchanges vs SPAARK/finalsbot more often than the prior tag.

**V7 — Cat damage & 2nd king.** Actively place **cat traps** near cats in
cooperation to bank damage (points + tiebreaker + protection). Form a **2nd
king** at a discovered mine when cheese + 7 assembled rats allow (respect: no new
kings after round 1200 with ≥2 kings) — a survival hedge and the cheese-transfer
shortcut (`food`: many games were won by who formed a king first).
  *DoD:* measurable cat-damage on the scoreboard; survives single-king sniping.

**V8 — Tuning, bytecode, regression.** `bench.nu` vs **SPAARK / Version41 /
finalsbot** (not just example), both sides, the three default maps. Tune
`Const`. Bound every loop, sense once, keep turns < 17500 (rats) / 20000 (kings)
via `Clock.getBytecodeNum()`. Code freeze.

---

## 7. Constants to tune (`Const.java`)

`RETURN_THRESHOLD` (≈40) · `MAX_CARRY` (≈100, scaled by king proximity) ·
`KING_CHEESE_RESERVE` (large; anti-starve + anti-rush) · `TARGET_RATS`
(scaled by map size & known mines) · `LATE_DUMP_ROUND` (≈1950) ·
`TRAP_RESERVE` (keep cheese for defensive traps) · `SOS_RADIUS` ·
`MICRO_EXHAUST_TURNS` · `EXPLORE_RETARGET_DIST`. Seed RNG (6147) for determinism.

---

## 8. Testing / QA protocol

Battlecode is deterministic per map+seed, so strength is measured across a **map
suite × both sides**, not reruns. Use `scripts/bench.nu`:

```
nu -c "source scripts/bench.nu; main --opponent SPAARK --detail"
nu -c "source scripts/bench.nu; main --opponent Version41"
nu -c "source scripts/bench.nu; main --opponent finalsbot"
```

Default suite stays the three default maps while the core loop is built. Process
(from every postmortem): make a *change* → watch a *replay* to see the units do
the new thing → only then trust the win-rate. Watching replays beats staring at
win%. Make a few **targeted** maps later (a combat map, a maze, a cat-heavy map)
rather than averaging over dozens. Beware overfitting to these specific bots.

---

## 9. Guardrails & gotchas (read once, apply always)

- Every turn wrapped in try/catch; **always `Clock.yield()` in `finally`**. An
  unhandled exception costs 500 bytecode and can paralyze the robot.
- `can*()` before **every** action. Robots don't share memory (separate JVMs);
  `static` is per-robot. Only cross-robot channels are the shared array + squeaks.
- **Sense once per turn**, cache, pass arrays to helpers. Bound every loop.
- No file I/O, no reflection — compiles fine but explodes at runtime.
- Determinism: same map+seed ⇒ identical replay. Use it for debugging.
- Carrying raw cheese slows you (`0.01 × amount`) — don't over-hoard.
- Squeaks attract cats — suppress near cats, but **keep squeaking otherwise**
  (coordination depends on it).
- Keep `main` always-compiling and always-beating the previous checkpoint.
  Commit small and often.

---

## 10. If behind — cut in this order (protect survival + economy)

1. Drop 2nd-king formation.  2. Drop cat traps (just flee cats).  3. Simplify
micro to "bite-if-favourable, else retreat + trap".  4. Drop offensive combat
entirely. A pure **survive + economy + king-defense + cat-traps** bot already
scores the 0.5 king term and bankable cat damage, and demos cleanly. **Never**
cut: safe turn loop, turn-aware navigation, economy, king upkeep.

## 11. Definition of "done" per increment

Compiles · runs a full game with zero exceptions · beats the previous tag on the
suite (both sides) · committed.

## 12. Report components (school deliverable)

Keep `docs/report-notes.md` updated with dot points per increment (standing
instruction). Capture for the report/presentation: the defensive thesis + the
scoring math that justifies it, the architecture/class diagram, replay clips
(economy snowball + a king-defense save + an aggressor dying to our trap),
the `bench.nu` regression evidence vs the aggressive bots, and one
bytecode-optimization technique (sense-once / bounded loops).
