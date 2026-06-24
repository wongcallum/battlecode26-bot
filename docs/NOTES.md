# Build Notes

Running dot-point log of each commit, written for use in the report.
Newest sections appended at the bottom.

## V1 — Bug2 explore navigation (baby rats)

- Split the example boilerplate into `RobotPlayer` (dispatch loop) + `Const`
  (RNG, directions) + `BabyRat` / `RatKing` stubs.
- `Pathfinder.moveTo` implements **Bug2**: head along the m-line (from where the
  target was acquired to the target); on hitting an obstacle, record the hit
  distance and trace the boundary (right-hand rule) until back on the m-line and
  strictly closer than the hit point, then resume the straight line.
- Turn-aware stepping: face the chosen direction first (sweeps the 90° vision
  cone, makes it a cheap cooldown-10 forward move) then move; strafe only as a
  fallback when turning isn't ready.
- Obstacle test is `canMove(dir)`, gated behind `isMovementReady()` so a `false`
  means a genuine wall/occupant, not a cooldown.
- "On the m-line" = perpendicular distance < 1 tile, computed with an integer
  cross product (no sqrt, bytecode-cheap).
- Baby rats currently only **explore**: pick a random map cell, Bug2 to it,
  repick on arrival (dist² ≤ 8) or after 60 turns (likely unreachable target).

### V1.1 — fix wall-trace looping

- First wall-follow draft made rats orbit walls in squares. Cause: the boundary
  scan started 90° right of the heading and rotated left, which peeled the rat
  off the wall instead of hugging it (EAST-into-wall → S → W → N → E → loop).
- Fixed to a true right-hand rule: scan from **45° right** of the heading,
  rotate left to the first opening — keeps the wall pinned on one side so the
  trace goes monotonically around the obstacle.
- Added **circumnavigation detection**: remember the hit point; if the trace
  returns to it, the goal is unreachable (e.g. a random target inside a wall) →
  `moveTo` returns false and the rat repicks immediately instead of orbiting.
- Re-hit refresh: if we rejoin the m-line closer but are blocked again, record a
  fresh hit point/distance rather than reusing the old one.

## V2 — economy (explore · collect · return)

- Baby rats now run a 3-state loop on top of Bug2: **collect** (go to the nearest
  visible cheese, else explore a random cell), and **return** (carry raw cheese
  back to a king and transfer it into the global pool).
- Return trigger: carrying `>= 40` raw cheese, or carrying any cheese with none
  left in sight. 40 is chosen against the carry penalty — cooldown scales by
  `(1 + 0.01*raw)`, so 40 cheese is only a ~1.4x slowdown on the trip home.
- Cheese facts that shaped this (from the engine): pickup is **free** (no action
  cooldown, no `isActionReady`) but the tile must be in the 90° vision cone and
  within dist² 2; transfer needs `isActionReady`, costs cooldown 10, and the king
  must be in-cone within dist² 9. `stepFacing` already turns us toward our move
  direction, so navigating to cheese/king naturally satisfies the cone check.
- **King location via shared array**: only kings can write it, any rat can read.
  King writes `(x+1, y+1)` to slots 0/1 each turn (0 = unset). Rats prefer a
  directly-sensed king (handles king movement / multiple kings) and fall back to
  the broadcast. Needed because a freshly-built rat inherits the *king's* facing,
  so it can't be relied on to see the king at spawn.
- King also opportunistically grabs cheese within its own pickup radius (360°
  vision) — king-collected cheese converts straight to global.
- Spawning: replaced the 2000 "survive-on-reserve" hack with a `SPAWN_FLOOR` of
  400 global cheese. Now that rats deliver income, the floor protects ~200 rounds
  of upkeep while letting the king build a workforce when cheese is healthy.
- Result on the default maps: vs examplefuncsplayer we now **win** (its king
  starves ~1310) where V1 lost to our own starvation at 1060; a wongcallum mirror
  runs the full 2000 rounds with both kings alive, confirming the economy is
  net-sustainable rather than just outlasting a weak opponent.

### V2.1 — tried cheese-mine memory (REVERTED, kept as a finding)

- Rats visibly take long random paths and "forget" mines they've cleared. Tried
  to fix it two ways: (a) remember mine centers (`MapInfo.hasCheeseMine()`) and
  return to the nearest known mine; (b) remember the tile where the rat *last*
  found cheese and shuttle back to it ("territory" memory).
- Benchmarked each as a head-to-head vs the no-memory V2, both map orientations,
  over the suite {Small, Medium, Large, cheesefarm, starvation}. Both memory
  variants **lost decisively** on Medium, Large and cheesefarm; only won on Small.
  Territory memory even **starved our own king** on cheesefarm (game lost ~r390).
- Why memory loses: cheese respawns slowly at a mine (~1 spawn / ~70 rounds), so a
  *just-harvested* spot is depleted and low expected-value to revisit, while a
  random wanderer keeps stumbling onto cheese that has *accumulated* at mines
  nobody is working. Any "go back to where I found cheese" rule also feeds back
  toward easily-found (near-king) mines and starves coverage of distant ones —
  the exact accumulation we set out to fix. Random's ignorance ≈ good coverage.
- Decision: keep pure-random exploration. The real lever for more cheese is
  *better coverage / anti-clustering* (spread rats across distinct mines), not
  memory — but that needs coordination (king-aggregated mine list, or squeaks)
  and is left for a future version.
- Note on scoring: in cooperation, points = `0.5·%catDamage + 0.3·%livingKings +
  0.2·%cheese`. Cheese is only 20%; since neither bot fights cats or makes extra
  kings, head-to-head wins here are effectively a cheese-throughput proxy.

### V2.2 — anti-clustering (coverage via spreading)

- Two changes, both aimed at covering more mines at once instead of trailing
  each other:
  1. **Per-id RNG seed.** `Const.rng` was seeded with one constant for every
     rat. Statics are per-robot, but identical seeds mean every rat draws the
     *same* explore-target sequence and moves in lockstep. Reseed with
     `getID() * 0x9E3779B9` on the first turn so the swarm decorrelates.
  2. **Repulsion on explore targets.** When (re)picking an explore cell, sample 4
     random cells and keep the one farthest from the nearest *visible* ally, so
     rats push off local clumps and fan out.
- Head-to-head vs committed V2 (no memory), both orientations, suite {Small,
  Medium, Large, cheesefarm, starvation}: **V2.2 wins 2–0 on Small, Medium and
  Large**; cheesefarm and starvation tie 1–1. This is the inverse of the failed
  memory experiment, which *lost* Medium/Large — coverage, not memory, is the
  lever, exactly as predicted in V2.1.
- Open issue: on `cheesefarm` a king dies ~round 380 for both versions
  regardless — likely cat pressure on that map, not an economy problem. Wants the
  future combat/cat-trap work, not more economy tuning.

### V2.3 — dig through dirt barriers

- The cheesefarm "king dies ~r380" issue was *not* cats: that map is carved up by
  diagonal **dirt barriers**. Dirt is impassable (`isPassable` = no wall and no
  dirt), and our Bug2 treated it like a permanent wall — but the barriers run to
  the map edge, so wall-following never gets around them and the whole swarm piled
  up against them next to the king and slowly starved.
- Fix: rats `removeDirt()` (cost 5 cheese, action cooldown 25, dist² 2, in-cone) a
  dirt tile to tunnel straight through. The hard part was *when* to dig:
  - First cut dug any dirt blocking the straight line. **Regressed Small/Medium**
    — two bugs: (1) `tryDig` force-turned to face the blocked tile every turn,
    which wrecked wall-following (it faces *along* the wall, not at the target);
    (2) digging small dirt patches wastes cheese/cooldown vs just stepping around.
  - Final: dig only as a **last resort** — after wall-following the same obstacle
    for `DIG_AFTER = 10` steps without escaping (i.e. a real barrier, not a nub).
    Then face the target and dig one tile through, reset, resume the straight line.
- Head-to-head vs V2.2, both orientations, suite: **V2.3 wins 2–0 on Medium,
  Large, cheesefarm and starvation**, ties Small 1–1, regresses nothing. On
  cheesefarm the dirt-blind V2.2 king starves out by ~r380 while V2.3 breaks out
  and wins.

## V3 — cat traps + cat avoidance (the cat-damage pillar)

- First combat work (PLAN's V7 cat-traps + a V5 cat-defense seed). Cat damage is
  **half the cooperation score** (`0.5·%catDamage`) and we banked **zero** before
  this. Cat traps are the lever: **10 cheese → 100 damage + a 20-turn stun**,
  placeable freely in cooperation, **max 10 active**, single-use (removed when a
  cat triggers it). The trap only fires when a **cat moves into** trigger range
  (dist² ≤ 2), so traps must sit in the cat's *path*, not merely near it.
- Engine facts that shaped the design (read from source, not guessed): cats are
  `Team.NEUTRAL`, roam waypoints, chase the nearest rat, and get **distracted by
  squeaks** (so squeaking near cats is self-harming). Baby rat moves at cooldown
  10 vs the cat's **20**, so a rat out-runs a cat in a straight retreat (the "flee
  perpendicular" rule is only for the cooldown-40 king). Trap placement needs the
  tile **in the 90° vision cone** for a rat (must face it) but the **king is 360°**
  and has no facing constraint. A rat carrying ≥10 raw cheese pays for the trap
  from **its own stash**, not global — trapping rarely touches the king's buffer.
- Behaviour (`Combat.java`):
  - **Baby rat — trap-and-peel.** Within dist² 16 of a cat: face it (puts the trap
    tile in-cone), drop a cat trap on the tile *between* us and the cat, then
    greedily step to the passable neighbour that maximises distance. The chasing
    cat walks across the fresh trap; the rat (twice as fast) gets away. Overrides
    foraging only while a cat is near — the no-cat path is byte-for-byte unchanged.
  - **King — defensive ring.** A cat within sight makes the king spend that turn's
    action laying a cat trap toward it (360° = place anywhere in build range 8)
    instead of spawning. Survival outranks the spawn; it resumes building when
    clear.
  - **Reserve gate.** Never dip *global* cheese below `CAT_TRAP_RESERVE = 200` for
    a trap (protects king upkeep); a carrying rat just spends its own raw cheese.
    The 10-active cap self-limits the swarm so we never over-spend.
- Head-to-head vs V2.3, both orientations, suite {Small, Medium, Large,
  cheesefarm, starvation}: **V3 wins 10/10, zero coin-flip ties.** Medium and
  Large run the full 2000 rounds with both kings alive, so the win is decided
  purely on score — V3 banks cat damage that V2.3 can't, and takes the 0.5 term.
  On cheesefarm side-A V3 wins by ~r589 (the trap-less king dies to cats while
  ours neutralises them).
