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

## V5 — king defense / anti-rush (war-mode turtle)

- First test vs the real reference bots (SPAARK, Version41, finalsbot) was a wake-
  up call: **0/18, dying at r78–352** — far short of the 2000-round economy game.
  A debug trace of the king showed the cause: enemies bite the king to death
  ("destroyed all of the enemy team's rat kings"); the aggressive bots **backstab
  almost immediately** (`isCooperation()` is already false by ~r34), so we are at
  war the whole game with no answer.
- Engine facts (from source): **biting an enemy rat/king initiates the backstab**
  (`InternalRobot.backstab(this.team)`), as does ratnapping — biting a **cat** does
  not. So all retaliation must be gated on `!isCooperation()` or we become the
  aggressor (which also disables our own cat traps). Rat trap: **20 cheese → 50
  damage + a 30-turn stun, hidden from the enemy**, max 25 active, triggered when an
  enemy *moves into* dist² ≤ 2. Placing dirt costs **0 cheese**. King attacks within
  dist² 8 (360°, no facing); baby rats within dist² 2 (must face the target).
- What failed: **spawning body-blockers.** The trace showed the king dumping its
  whole 2500-cheese war-chest into 100-HP rats at a rising cost (10→90) that the
  enemy one-shot with cheese-boosted bites — they died as fast as we made them, and
  once cheese ran out the king was defenceless. 40-cheese blockers are the wrong
  tool.
- What worked: a **standing ring of hidden rat traps** (`Combat.kingLayRatRing` —
  the 16 perimeter tiles within build range, laid *proactively* while still free,
  not reactively in occupied melee). The trap ring held the king at **548 HP from
  ~r70 to ~r253** against wave after wave — survival jumped to **r250–650, a 3–5×
  improvement** across every bot and map. The traps are invisible to the enemy, so
  the rush walks straight into them.
- Design is a **coop-gated war mode** (`RatKing.warAct`): once `!isCooperation()`,
  turtle behind the trap ring + a `WAR_RESERVE = 1200` war-chest, bite attackers in
  melee, and raise an SOS (`SOS_SLOT`) so nearby rats rally to wall the king. Gating
  on `!coop` is essential: a passive opponent never backstabs, so war mode never
  fires and the peace economy + cat-trap pillar are **provably unchanged** — V5 vs
  V3 is a 5–5 split on the economy suite (every map a 1-1 first-mover tie), i.e. no
  regression.
- Still **0 wins** vs the reference bots: the new failure mode is pure **cheese
  starvation** — under siege we have no income, so we eventually can't re-lay
  triggered traps and the ring decays. Next levers (cheap/free): **wall the king
  with dirt** (free) to block/slow the rush and cut trap-drain, and a **light war
  economy** so the ring sustains to r2000 (a living king wins the 0.5 backstab
  term). Tracked for V5.1.

### V5.1 — tried war income + dirt walls (REVERTED, kept as a finding)

- Hypothesis: the turtle dies only because it runs out of cheese, so give it
  **income** — drop `WAR_RESERVE` 1200→300 so the king spawns foragers at war, stop
  the rats *rallying* to the king (they just died there) and let them **forage**
  instead, plus an opportunistic free **dirt wall** (`getDirt()` stockpile) on the
  threat side. Expectation: income sustains the ring to r2000 → win on king survival.
- Result: **regressed across the board** — survival fell from V5's r250–650 back to
  **r136–450** vs all three bots, both sides. Reverted entirely to V5.
- Why it loses: **under siege the enemy owns the field, so spawned foragers die
  before they can deliver.** The cheese spent on them is cheese stolen from the trap
  ring — the *only* thing keeping the king alive — so we starve the defence faster.
  With no income, **conserving for the ring (the V5 turtle) is strictly better.**
- The decisive arithmetic (the real lesson): the turtle bleeds ~3.6 cheese/round
  (2 upkeep + ~1.6 trap re-lay) with **zero income**, and surviving r34→2000 at that
  rate needs **~7000 cheese while we start with 2500**. So a pure turtle *cannot*
  reach r2000 — it can only delay death (measured ceilings r600–1300). **Winning
  requires income, income requires foragers that survive, so beating these rush bots
  is a combat problem (V6), not a defence-tuning problem.**
- Dirt walls are not the free backbone they first looked: `placeDirt` needs a
  **dug-dirt stockpile** *and* costs **25 action cooldown**, so on open maps (nothing
  to dig) there is nothing to wall with. It is a bonus on dirt-rich maps, not a
  general anti-rush tool.
- Decision: keep V5's conserving turtle. The path to actual wins is **V6 — defensive
  combat micro**: protect/escort foragers so income survives, and make the ring + a
  biting king lethal enough to break the rush and earn breathing room.

## V6 — diagnose the death, then stop the suicide-rally

- Began V6 by **instrumenting the king** (a temporary per-turn `System.out` trace of
  hp / global cheese / trap count / sensed enemies / sensed allies) and reading a full
  match vs Version41 on DefaultSmall. The trace settled what V5 was actually dying of,
  and it was **not** what the V5 notes assumed:
  - r60–92: the opening rush lands; the king drops 600→406 and **our whole army dies in
    the SOS rally** (sensed allies 7→0).
  - r92–256: the king sits at ~400 HP with **`threat=0, adj=0` for ~160 rounds** behind a
    full 16-trap ring. The **ring alone neutralises the rush** — attackers die stepping
    into bite range, so none ever reach the king's body tiles. Defence is *solved*.
  - r256: global cheese reaches 0; the king then bleeds the **10 HP/round starvation
    penalty** and dies r277. It dies **broke, never overwhelmed.**
- So the binding constraint is **income/starvation, not combat lethality.** And the floor
  is unforgiving: upkeep alone is 2 cheese/round, so 2500 starting cheese is gone by
  ~r1250 *even with a free, perfect defence*. **There is no turtle-only path to r2000 —
  income is mandatory.** (Sharpens the V5.1 arithmetic: the issue isn't trap re-lay cost,
  it's that we have no income at all.)
- **Rejected — war rebuild-spawn (V6.0).** First reflex was to give the king income by
  spawning foragers during the long clear windows (gate: no enemy sensed, cheese above a
  floor). Regressed hard: r277→**r162**. The crew did reach 7 rats, but spawning drained
  the war-chest ~40/round while the foragers (which still have to leave the safe bubble to
  find cheese) delivered too little, too late — the king starved *earlier*. Same shape as
  V5.1: bodies cost more than they bring. Reverted.
- **Kept — stop the suicide-rally (the V6 checkpoint).** The trace's real tell is that the
  rally is *counterproductive*: it feeds our existing income-generating army into the
  meat-grinder at the king (allies 7→0) for **no defensive benefit**, because the trap
  ring already holds the king. So at war the rats **no longer rally** — they flee an enemy
  within `WAR_FLEE_RADIUS_SQUARED` (then keep foraging), staying alive to deliver. The king
  drops the rally/SOS entirely and just lays its ring + bites (`warAct`).
- Result vs the three rush bots (both sides, Default Small/Med/Large): total survival
  **7255→8195 rounds (+13%)**, better-or-equal on 8 of 9 map-pairs (Version41/Medium
  r565→**r925**). The king now holds ~528 HP instead of ~356 — without the rally, the swarm
  is never drawn onto it. Peace play is untouched (all war code is gated on
  `!isCooperation`), and the change *deletes* the SOS slot, `DEFEND_RADIUS_SQUARED`, and
  `Combat.ratDefend` — strictly simpler.
- **Still 0 wins, and the trace says why:** even fleeing, our foragers are dead by ~r96
  because **the enemy controls the open field** — they wander into it and get caught, so
  income stays zero and the king just starves later (r256→r324). Fleeing a single nearest
  enemy locally isn't enough; the rats have no notion of *where* safety is.
- Next lever (V6.1): the king **broadcasts the threat direction**; foragers forage the
  **safe side** (away from the enemy king's symmetric mirror) so income actually survives.
  That is the step that turns "starve at ~r400" into "outlast them to r2000" — the first
  real path to a win.

## V6 finishing pass — harden the defensive build for submission

(Branched here to finalise the defensive bot as a safe fallback submission, separate from
the strategic "contest the economy" pivot.)

- **Full-suite robustness sweep** (all 43 contest maps, both sides, vs the passive
  examplefuncsplayer): **zero crashes / exceptions anywhere** — the bot is submission-safe.
  Score 68/86; every win is by **outlasting** the example bot (it starves ~r1300). The
  losses cluster on one map family — see below.
- **Population crowd-cap (the one behaviour change).** Diagnosed a loss-to-passive on
  `whereisthecheese`: the king was spawning **29 rats that piled up in its own vision the
  whole game** while global cheese fell at exactly 2/round (pure upkeep, **zero income**).
  Cause: the cheese mines are **dirt-locked** and our foragers can't path through the dirt
  to reach them, so they mill around home. We had burned ~2100 cheese on bodies that bring
  nothing, then starved. Fix: stop spawning once `PEACE_POP_CAP` (14) allied rats already
  crowd the king (`PEACE_CROWD_RADIUS_SQUARED`). It only bites on genuine pileups (on normal
  maps foragers disperse, so the count stays low and spawning is unchanged) — confirmed by
  the war suite being **bit-identical** (the cap never fires before the early backstab) and
  the peace sweep improving (`whereisthecheese` 0/2 → 1/2, overall 18→19/20 on the first
  10-map cut). It is also strategically correct: conserving the reserve lets us **survive
  long enough for a cat to dig a channel into the dirt-locked mines**, after which our
  preserved foragers can finally collect.
- **Known weakness, left as-is (low-risk fallback):** dirt-locked-cheese maps, where we
  can't reach the mines ourselves and depend on a cat/opponent to open access. The real fix
  is foragers **digging to mines themselves**, but that is Pathfinder surgery (regressed us
  in V2.3) and edges into the deferred economy pivot. Many of these losses are maps where
  *both* teams starve early (`streetsofnewyork`, `whatsthecatdoin`, `uneruesansfin`), i.e. a
  brutal-map problem, not a unique flaw.
- **Submission packaging fix.** `zipForSubmit` zipped *all* of `src/`, which would have
  bundled the three gitignored external reference bots (SPAARK/Version41/finalsbot) into the
  submission — bloat that could fail the contest build. Scoped it to `wongcallum/**` only;
  `submission.zip` is now the 7 wongcallum source files and nothing else.
