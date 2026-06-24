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
