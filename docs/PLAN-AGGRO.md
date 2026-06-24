# AGGRO — contested-economy Battlecode 2026 bot

Branch `aggro` (own worktree), based on the V6 defensive checkpoint `259c413c`.
This plan **layers on top of** `docs/PLAN.md` (FORTRESS) — it does not replace the
defensive pillars; it changes the *strategic stance* built on them. FORTRESS's
scoring math, engine facts, and opponent reads still hold and are assumed here.

---

## 1. Why we are pivoting (the replay that changed the read)

We watched `Version41-vs-wongcallum-on-DefaultMedium`. The decisive observation:
**after killing our foragers, the enemy does not even bother assaulting our king.**
It camps the cheese mines, owns the economy uncontested, and forms a **second rat
king**. We sit behind an impenetrable ring and slowly starve.

Two things are simultaneously true:

1. **Our defense is excellent.** The trap ring is so strong the enemy declines to
   attack it — across the V5/V6 traces the king sits at ~400–530 HP with `adj=0`
   (no attacker ever reaches a body tile). King-assault is effectively *off the
   table*.
2. **Defense alone cannot win.** A perfect turtle loses for two independent reasons:
   - **Starvation.** Upkeep alone is 2 cheese/round, so 2500 starting cheese is gone
     by ~r1250 with zero income — we cannot reach r2000 no matter how good the ring is.
   - **Score.** Even *if* we reached r2000, backstab scoring is
     `0.5·livingKings + 0.3·catDamage + 0.2·cheese`. An economy-less turtle loses all
     three: they camp every mine (cheese), form extra kings (the 0.5 term), and bank
     cat damage. **There is no pure-defense line that wins against an economic opponent.**

So the binding problem was never king survival — it's that **we concede the entire
economy the moment the war starts.**

## 2. The new thesis: the unkillable king is a *platform*, not a bunker

Because the ring takes king-death off the table, **rat losses no longer lose us the
game.** That inverts the cost of aggression: we can spend rats freely to contest the
economy, since the only real loss condition is already defended. The hoarded 2500
war-chest is *worthless hoarded* — it only buys upkeep until we starve. Converted into
rats that fight for mines, it buys income, board presence, and score.

**FORTRESS turtles behind the ring. AGGRO uses the ring as cover to fight for the
economy.** Same foundation, opposite economic stance.

### Confidence (honest)
- **High** that pure defense is a dead end (proven: starvation + score).
- **Moderate** that contesting the economy makes us competitive. The enemy snowballs
  first and forager survival under field control is genuinely hard. This is a real
  fight, not a guaranteed win — but the expected value clearly favors the pivot, and
  every phase below is independently measurable so we fail fast if a lever doesn't pay.

## 3. Invariants (never break these — they are why the pivot is safe)

1. **Keep the ring.** The king always lays/maintains its hidden rat-trap ring first,
   and we always hold a **ring-reserve** of cheese to re-lay it through an assault.
   Aggression spends only the *surplus* above that reserve.
2. **Never risk the king.** No behavior may put a king in avoidable danger; king
   survival is still the 0.5 term and the instant-loss condition.
3. **Peace is provably unchanged.** All new behavior stays gated on
   `!rc.isCooperation()`. Against a passive opponent we never go to war, so the peace
   economy + cat-trap pillar are byte-identical. (Verify each phase with a no-backstab
   opponent / mirror, as in V5.)
4. **Small, measured commits.** Each phase compiles, runs clean, and is benchmarked vs
   SPAARK / Version41 / finalsbot (both sides) before the next.

## 4. What "contesting the economy" requires (the levers)

The snowball loop is `cheese → rats → mines → cheese`. We were plugged out of it.
To plug in:

- **L1 — Foragers that survive to deliver.** The current blocker. Even fleeing, our
  rats die because they wander into enemy-held ground with no notion of *where* safety
  is. Fix: forage the **safe side** (away from the enemy king's symmetric mirror).
- **L2 — Spawn through the war.** The king must keep building rats in war (down to the
  ring-reserve), converting the war-chest + income into a real workforce. (V6.0 tried
  this *uncapped* and over-drained — so it must be paired with L1 and a reserve floor.)
- **L3 — Win mine trades.** When our foragers meet enemy foragers at a mine, win the
  exchange (cheap hidden rat trap on the approach, bite when favorable, flee when
  outnumbered). This both protects our income and **denies theirs**.
- **L4 — More kings.** Form a 2nd king at a safe/far mine: doubles spawn points, adds
  to the 0.5 king term, and is the cheese-transfer shortcut postmortems say often
  decided games.

## 5. Phased build (each = a committable, benchmarked checkpoint)

Ordering is deliberate: **make foragers survive before scaling their number**, because
spawning rats that just die is the V5.1/V6.0 failure.

**A1 — Threat-aware safe-side foraging (survival first).**
King broadcasts a danger signal; foragers bias exploration and fleeing toward our own
half (the side of map-center nearer our king) and away from the enemy. Cheapest robust
heuristic needs only our king position (already broadcast) + map dimensions — no full
symmetry resolution required. Optionally sharpen later with real symmetry (A5).
*DoD:* on the rush maps, `allies` stays > 0 deep into the game and global cheese stops
flatlining at 0 — i.e. **nonzero sustained income** (instrument a cheese-over-time trace).

**A2 — War spawning (scale the economy).**
With foragers surviving, the king keeps building rats during war down to a
`RING_RESERVE` floor (enough to re-lay the full ring through an assault), throttled by
the natural build-cost curve. Spend the war-chest *surplus* on workforce, don't hoard it.
*DoD:* global cheese trends flat-or-up instead of monotonically down; survival pushes
toward r2000 on at least the medium/large maps; no peace regression.

**A3 — Mine trades / contest (deny their snowball).**
Foraging rats fight for contested mines: drop a hidden rat trap on an approaching enemy
forager's path, bite when locally favorable, flee when outnumbered (never a lone rat
into 1-v-many). Goal: hold mines and **reduce enemy income**, not assault their king.
*DoD:* we hold/contest mines on replays; enemy cheese growth visibly slowed; survival
and/or score up vs A2.

**A4 — Second king (more of the 0.5 term + spawn points).**
Form a 2nd king at a safe mine once cheese + assembled rats allow, respecting the engine
king-count/cutoff rules (verify exact formation mechanic + `RAT_KING_UPGRADE_CHEESE_COST`
+ `RAT_KING_CUTOFF_ROUND` in engine before building). Each king lays its own ring.
*DoD:* 2 living kings at r2000 on maps that reach it; measurable score/win improvement;
losing one king never loses the game.

**A5 — Symmetry, comms, tuning.**
Proper symmetry detection (king broadcasts H/V/rotational once resolved) to sharpen safe
foraging and locate enemy mines; shared-array mine list for coordinated coverage;
bytecode + regression pass; const tuning. Code freeze.
*DoD:* foragers converge on safe mines via the array; clean bytecode headroom; best
suite result.

## 6. Testing protocol (the metric shifts)

FORTRESS measured **survival round**. AGGRO's leading indicators, in order:
1. **Income** — does `allies` stay > 0 and does global cheese stop hitting 0?
   (Instrument a per-king cheese/allies/round trace, as we did to diagnose V5.)
2. **Reach r2000** — survival round climbing toward 2000 means the economy sustains.
3. **Score / wins** — the real goal: reach r2000 with competitive cheese + 1–2 living
   kings, or win outright by outlasting an over-extended enemy.

Tooling: the direct-JVM `diag.nu` runner (wongcallum vs SPAARK/Version41/finalsbot,
both sides, Default Small/Med/Large), plus replay review in the client per change.
Watch a replay after every phase — the V5→V6 lesson was that the *behavioral* story
(who's at the mines, who's starving) matters more than the win%.

## 7. Open questions to resolve as we go

- **Does provoking them re-trigger a king assault?** If we contest their mines hard,
  they may re-commit to attacking our king. The ring + ring-reserve must hold — verify
  on replays; this is exactly what the reserve floor protects.
- **Is "our half" enough on small maps?** 20×20 maps may have no safe mines; safe-side
  foraging helps less there. Symmetry/known-mine coordination (A5) may be needed.
- **Net income sign.** Confirm empirically that surviving foragers deliver more cheese
  than they cost to spawn + replace — the whole pivot rests on this being positive once
  L1 (survival) is fixed.
- **2nd-king mechanic.** Confirm how a king is actually formed and the precise
  max-kings / cutoff-round constraints before A4.
