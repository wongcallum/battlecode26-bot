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
