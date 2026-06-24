# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A competition bot for **Battlecode 2026: "Uneasy Alliances"** (an MIT AI programming contest). You write Java that controls a swarm of robots — each robot runs an independent copy of `RobotPlayer.run()` in its own JVM, gets one turn per round, and is capped by a per-turn **bytecode** budget. Our team's bot is the package **`wongcallum`** (`src/wongcallum/`). We compete as team `wongcallum`.

The full game rules are in `docs/SPECS.md` — read it before making strategic changes. The short version:

- A match is **best-of-3 games**; each game is **2000 rounds**. Maps are 20×20–60×60 and always symmetric.
- Units: **Baby Rats** (workers, 17500 bytecode/turn) spawned by **Rat Kings** (20000 bytecode/turn). **Cats** are neutral NPCs that kill rats; you don't control them.
- **A team auto-loses a game the instant all its rat kings die.** King survival is the overriding priority.
- Games start in **cooperation** (both teams + alliance fight the cats) and flip to **backstabbing** the moment one team damages/traps/ratnaps the other. Scoring weights shift toward king-survival after a backstab. `rc.isCooperation()` tells you the current phase.
- **Cheese** is the resource: baby rats collect it and carry it home; transferring to a king makes it global. Kings consume 2 cheese/round or take 10 HP damage — **a starving king dies.**

The engine source code can be found in `../battlecode26/engine/src/main/battlecode`, there is no need to extract the JAR.

## Commands

Development happens inside a **Nix dev shell** (`flake.nix`) which provides JDK 21, Python, and LSPs. With `direnv` it loads automatically (`.envrc`); otherwise run `nix develop`. JDK 21 is mandatory — the build hard-fails on older JDKs.

```bash
./gradlew build              # compile all players (also downloads the GUI client)
./gradlew run                # run one headless match using gradle.properties settings
./gradlew compileJava -q     # fast compile-only (what you'll run most while iterating)
./gradlew test               # run JUnit tests (test/)
./gradlew zipForSubmit       # produce submission.zip for the contest site
./gradlew update             # bump engine/client to latest contest version (run often)
./gradlew listMaps           # list available maps; listPlayers lists bot packages
nix run .#client             # launch the GUI client to watch .bc26 replays in matches/
```

**Run a specific match** by overriding the `gradle.properties` defaults on the CLI:

```bash
./gradlew run -PteamA=wongcallum -PteamB=examplefuncsplayer -Pmaps=DefaultMedium
```

Useful run flags (all `-P...`): `maps` (comma-separated), `teamA`/`teamB`, `languageA`/`languageB` (`java`/`python`), `debug`, `showIndicators`, `outputVerbose`. The winner line in stdout looks like `[server] wongcallum (A) wins (round 1234)`. Replays are written to `matches/<a>-vs-<b>-on-<map>.bc26`.

**Regression benchmarking** — the key tool for verifying a change actually made the bot stronger:

```bash
nu scripts/bench.nu                              # play current vs all past wongcallum checkpoints
nu scripts/bench.nu --commits [<sha> <sha>]      # specific commits
nu scripts/bench.nu --opponent Version41 --detail
```

Battlecode is **deterministic** for a given map+seed (re-running a match is bit-identical), so `bench.nu` measures strength by sweeping a **suite of maps** and playing **both sides** to cancel first-player advantage. It stashes/checks-out/rebuilds each commit, so run it from a clean-ish tree.

Improvements should not only be measured using this script, however. Verify with the user by providing them with the path of matches in `matches` so they can view it in the client to observe, so the user can confirm that they work.

## Architecture

### Per-robot execution model (the constraints that shape everything)
- `RobotPlayer.run()` is the entrypoint for every unit. It loops forever: `turnCount++`, dispatch on `rc.getType()` to `BabyRat.act()` / `RatKing.act()`, all wrapped in try/catch, and **always `Clock.yield()` in `finally`**. An unhandled exception or a missing yield can paralyze/kill the robot.
- **Robots do not share memory.** Each runs a separate JVM, so `static` fields are per-robot, not global. The only cross-robot channel is the **64-int shared array** (0–1023 each) and short-range **squeaks**.
- **Bytecode is the budget.** Loops must be bounded; prefer caching over recomputation. Idiom used throughout: **sense once per turn** (`rc.senseNearbyRobots()` / `senseNearbyMapInfos()`) and pass the arrays into helpers, rather than re-sensing per query.
- **Always guard actions with `can*()`** (`canMove`, `canAttack`, `canBuildRat`, `canPlaceRatTrap`, …) before calling the action.

### Strategic stance

See `docs/PLAN.md`.

## Conventions & gotchas

- **A bot package = a directory under `src/` containing `RobotPlayer.java`**, and the package name must match the directory. To start a new bot version, copy an existing package to a new directory and rename the `package` line.
- **Tracked packages:** `wongcallum` (ours), `examplefuncsplayer` (Java sample), `examplefuncsplayer_py` (Python sample). The other packages are **gitignored** — they're large external reference/opponent bots kept locally only for benchmarking; don't edit them or assume they're committed.
- **Keep `main` always-compiling and always-beating the previous version.** Commit small and often; verify each change with a headless run on the map suite (and `bench.nu` for anything strategic) before moving on.
- `gradle.properties` holds the default match config (currently `wongcallum` vs `examplefuncsplayer` on `DefaultSmall`).
- Allowed Java is restricted (see spec's "Java language usage"); `java.util`/`java.math`/`scala` are bytecode-counted as your own code. Don't open files or use reflection — robots will explode at runtime even if it compiles.
- `engine_version.txt` / `client_version.txt` pin the contest version; `build.gradle` reads them. Bump via `./gradlew update`.

### Build log

Keep a build log in docs/NOTES.md that describes the reasons behind design decisions, problems you overcame, and anything else that would be helpful to put in the report.

### Style

- Do not use javadoc comments or /// (triple slash) markdown comments. Only use // comments.
- If it is described in code, do NOT describe it as a comment. Comments should be very short and only written when necessary.
- Your commit messages should be short, concise and imperative. Design decisions go in the build log (`docs/NOTES.md`).
