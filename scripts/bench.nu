#!/usr/bin/env nu
# scripts/bench.nu — measure wongcallum's strength across git checkpoints.
#
# Battlecode is DETERMINISTIC for a given map + seed: re-running the same match
# yields identical output. So the meaningful way to "average a few runs" is over
# a SUITE OF MAPS, and over BOTH SIDES (we play wongcallum as team A and as team
# B on every map) to cancel any first-/second-player advantage.
#
# For each commit it: stashes uncommitted (tracked) work, checks out the commit,
# rebuilds, plays the suite vs an opponent, parses the winner + end round, then
# restores your original branch and pops the stash.
#
# Matches are played by invoking the engine JVM directly (battlecode.server.Main)
# instead of `./gradlew run`. This skips gradle's config phase + daemon overhead
# per match, and — with the websocket server disabled (bc.server.websocket=false,
# no port-6175 contention) — lets every (map, side) match for a commit run
# concurrently via `par-each`. A match is single-core (robots are cooperatively
# scheduled), so N cores ~ N matches at once.
#
# Usage:
#   nu scripts/bench.nu                              # default suite + checkpoints
#   nu scripts/bench.nu --commits [17cae9b9 e03deb7f]
#   nu scripts/bench.nu --maps [DefaultSmall tiny] --detail
#   nu scripts/bench.nu --opponent examplefuncsplayer --no-swap
#   nu scripts/bench.nu --threads 6                  # cap match parallelism

# Play one headless match (a vs b on map) via direct java; return {winner, round, ok}.
def run-match [map: string, a: string, b: string, timeout: int, cp: string, classes: string] {
  let jargs = [
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"
    "--add-opens=java.base/jdk.internal.math=ALL-UNNAMED"
    "--add-opens=java.base/jdk.internal.util=ALL-UNNAMED"
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED"
    "--add-opens=java.base/sun.security.action=ALL-UNNAMED"
    "-Dbc.server.wait-for-client=false"
    "-Dbc.server.mode=headless"
    "-Dbc.server.map-path=maps"
    "-Dbc.server.robot-player-to-system-out=false"
    "-Dbc.server.debug=false"
    "-Dbc.engine.debug-methods=false"
    "-Dbc.engine.enable-profiler=false"
    "-Dbc.engine.show-indicators=false"
    "-Dbc.server.websocket=false"
    "-Dbc.server.validate-maps=true"
    "-Dbc.server.alternate-order=false"
    $"-Dbc.game.team-a=($a)"
    $"-Dbc.game.team-b=($b)"
    "-Dbc.game.team-a.language=java"
    "-Dbc.game.team-b.language=java"
    $"-Dbc.game.team-a.url=($classes)"
    $"-Dbc.game.team-b.url=($classes)"
    $"-Dbc.game.team-a.package=($a)"
    $"-Dbc.game.team-b.package=($b)"
    $"-Dbc.game.maps=($map)"
    $"-Dbc.server.save-file=matches/($a)-vs-($b)-on-($map).bc26"
    "-cp" $cp
    "battlecode.server.Main"
    "-c=-"
  ]
  let r = (do { ^timeout $timeout java ...$jargs } | complete)
  let out = ($"($r.stdout)\n($r.stderr)" | lines)
  let line = ($out | where {|l| $l =~ 'wins \(round' } | get -o 0 | default "")
  # A perfect tie is broken by Math.random() in the engine (DominationFactor
  # WON_BY_DUBIOUS_REASONS) — non-reproducible, so we score it as a draw.
  let dubious = ($out | any {|l| $l =~ 'won arbitrarily'})
  let p = ($line
           | parse --regex '\[server\]\s+(?<team>\S+)\s+\((?<side>[AB])\)\s+wins\s+\(round\s+(?<round>\d+)\)'
           | get -o 0)
  if ($p == null) {
    {winner: "ERROR", round: 0, ok: false, dubious: false}
  } else {
    {winner: $p.team, round: ($p.round | into int), ok: true, dubious: $dubious}
  }
}

def main [
  --commits: list<string> = []   # git refs to test (default: all commits touching src/wongcallum)
  --maps: list<string> = [DefaultSmall DefaultMedium DefaultLarge cheesefarm starvation]
  --opponent: string = "examplefuncsplayer"
  --timeout: int = 200           # per-match wall-clock cap (seconds)
  --threads: int = 0             # cap concurrent matches (0 = one per core)
  --no-swap                      # only play wongcallum as team A
  --detail                       # also print every individual match
] {
  let commits = if ($commits | is-empty) {
    (^git log --reverse --format="%h" -- src/wongcallum | lines)
  } else { $commits }

  # Resolve the runtime classpath + compiled-classes dir once. These paths are
  # constant across commits (only the bytes in build/classes change per build).
  let cp = (^./gradlew -q printRuntimeClasspath | lines | last | str trim)
  let classes = $"(pwd)/build/classes"

  # Remember where we are and park any uncommitted (tracked) work.
  let branch = (^git symbolic-ref --quiet --short HEAD | str trim)
  let orig = if ($branch | is-empty) { (^git rev-parse HEAD | str trim) } else { $branch }
  let dirty = (^git status --porcelain --untracked-files=no | str trim | is-not-empty)
  if $dirty {
    print $"(ansi yellow)• stashing uncommitted changes(ansi reset)"
    ^git stash push -q -m "bench.nu autostash"
  }

  mut rows = []
  for commit in $commits {
    let subject = (^git show -s --format="%s" $commit | str trim)
    print $"(ansi cyan_bold)▶ ($commit)(ansi reset) ($subject)"
    ^git checkout -q $commit

    # A clean build per commit so removed/renamed sources can't linger as stale
    # classes (this is what correctly fails e.g. a dispatch-only checkpoint).
    let build = (do { ^./gradlew clean compileJava -q } | complete)
    if $build.exit_code != 0 {
      print $"  (ansi red)build failed — skipped(ansi reset)"
      $rows = ($rows | append {commit: $commit, subject: ($subject | str substring 0..38), built: "no", n: 0, wins: 0, draws: 0, "win%": "—", avg_round: "—"})
      continue
    }

    # Build the job list (every map, both sides) and play them concurrently.
    let jobs = ($maps | each {|map|
      let base = [{map: $map, side: "A", a: "wongcallum", b: $opponent}]
      if $no_swap { $base } else {
        $base | append {map: $map, side: "B", a: $opponent, b: "wongcallum"}
      }
    } | flatten)

    let work = {|j|
      run-match $j.map $j.a $j.b $timeout $cp $classes | merge {map: $j.map, side: $j.side}
    }
    let res = if $threads > 0 {
      ($jobs | par-each --threads $threads $work)
    } else {
      ($jobs | par-each $work)
    }

    # Per-map A/B summary line (mirrors the old serial output).
    for map in $maps {
      let a = ($res | where map == $map and side == "A" | get -o 0)
      let b = ($res | where map == $map and side == "B" | get -o 0)
      let as = if $a == null { "" } else { $"A=($a.winner)@($a.round) " }
      let bs = if $b == null { "" } else { $"B=($b.winner)@($b.round)" }
      print $"  ($map): ($as)($bs)"
    }

    let valid = ($res | where ok | insert result {|r|
      if $r.dubious { "draw" } else if $r.winner == "wongcallum" { "win" } else { "loss" }
    })
    if $detail { $valid | select map side winner round result | sort-by map side | print }
    let n = ($valid | length)
    let wins = ($valid | where result == "win" | length)
    let draws = ($valid | where result == "draw" | length)
    # Draws (engine coin-flip ties) count as half; they are not reproducible.
    let score = ($wins + ($draws * 0.5))
    let wr = if $n > 0 { ($score / $n * 100 | math round) } else { 0 }
    let ar = if $n > 0 { ($valid | get round | math avg | math round) } else { 0 }
    $rows = ($rows | append {commit: $commit, subject: ($subject | str substring 0..38), built: "yes", n: $n, wins: $wins, draws: $draws, "win%": $wr, avg_round: $ar})
  }

  print $"(ansi green)• restoring ($orig)(ansi reset)"
  ^git checkout -q $orig
  if $dirty { ^git stash pop -q }

  let sides = (if (not $no_swap) { " x2 sides" } else { "" })
  print ""
  print $"(ansi attr_bold)SUMMARY — wongcallum vs ($opponent), ($maps | length) maps($sides)(ansi reset)"
  print ($rows | table --width 160)
}
