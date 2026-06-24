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
# Usage:
#   nu scripts/bench.nu                              # default suite + checkpoints
#   nu scripts/bench.nu --commits [17cae9b9 e03deb7f]
#   nu scripts/bench.nu --maps [DefaultSmall tiny] --detail
#   nu scripts/bench.nu --opponent examplefuncsplayer --no-swap

# Play one headless match (a vs b on map); return {winner, round, ok}.
def run-match [map: string, a: string, b: string, timeout: int] {
  let g = ["run" $"-Pmaps=($map)" $"-PteamA=($a)" $"-PteamB=($b)"
           "-PlanguageA=java" "-PlanguageB=java"
           "-PoutputVerbose=false" "-PshowIndicators=false" "-q"]
  let r = (do { ^timeout $timeout ./gradlew ...$g } | complete)
  let line = ($"($r.stdout)\n($r.stderr)" | lines
              | where {|l| $l =~ 'wins \(round' } | get -o 0 | default "")
  let p = ($line
           | parse --regex '\[server\]\s+(?<team>\S+)\s+\((?<side>[AB])\)\s+wins\s+\(round\s+(?<round>\d+)\)'
           | get -o 0)
  if ($p == null) {
    {winner: "ERROR", round: 0, ok: false}
  } else {
    {winner: $p.team, round: ($p.round | into int), ok: true}
  }
}

def main [
  --commits: list<string> = []   # git refs to test (default: all commits touching src/wongcallum)
  --maps: list<string> = [DefaultSmall DefaultMedium DefaultLarge cheesefarm starvation]
  --opponent: string = "examplefuncsplayer"
  --timeout: int = 200           # per-match wall-clock cap (seconds)
  --no-swap                      # only play wongcallum as team A
  --detail                       # also print every individual match
] {
  let commits = if ($commits | is-empty) {
    (^git log --reverse --format="%h" -- src/wongcallum | lines)
  } else { $commits }

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
      $rows = ($rows | append {commit: $commit, subject: ($subject | str substring 0..38), built: "no", n: 0, wins: 0, "win%": "—", avg_round: "—"})
      continue
    }

    mut res = []
    for map in $maps {
      print -n $"  ($map): "
      let mA = (run-match $map "wongcallum" $opponent $timeout)
      $res = ($res | append ($mA | merge {map: $map, side: "A"}))
      print -n $"A=($mA.winner)@($mA.round) "
      if (not $no_swap) {
        let mB = (run-match $map $opponent "wongcallum" $timeout)
        $res = ($res | append ($mB | merge {map: $map, side: "B"}))
        print -n $"B=($mB.winner)@($mB.round)"
      }
      print ""
    }

    let valid = ($res | where ok | insert won {|r| $r.winner == "wongcallum"})
    if $detail { $valid | select map side winner round won | print }
    let n = ($valid | length)
    let wins = ($valid | where won | length)
    let wr = if $n > 0 { ($wins / $n * 100 | math round) } else { 0 }
    let ar = if $n > 0 { ($valid | get round | math avg | math round) } else { 0 }
    $rows = ($rows | append {commit: $commit, subject: ($subject | str substring 0..38), built: "yes", n: $n, wins: $wins, "win%": $wr, avg_round: $ar})
  }

  print $"(ansi green)• restoring ($orig)(ansi reset)"
  ^git checkout -q $orig
  if $dirty { ^git stash pop -q }

  let sides = (if (not $no_swap) { " x2 sides" } else { "" })
  print ""
  print $"(ansi attr_bold)SUMMARY — wongcallum vs ($opponent), ($maps | length) maps($sides)(ansi reset)"
  print ($rows | table --width 160)
}
