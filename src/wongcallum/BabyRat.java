package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

// role: collect cheese, explore, defend, fight defensively
// state machine states: EXPLORE / COLLECT / RETURN / DEFEND / FLEE_CAT
public final class BabyRat {
    private BabyRat() {
    }

    static MapLocation homeKing = null;       // last known allied king location
    static MapLocation rememberedMine = null; // a cheese source to fall back to
    static MapLocation exploreTarget = null;
    static MapLocation lastLocation = null;
    static int stuckTurns = 0;
    static String state = "EXPLORE";

    private static final int RETURN_THRESHOLD = 40; // raw cheese before heading home
    private static final int REACHED_DIST_SQ = 8;
    private static final int STUCK_LIMIT = 12;      // abandon an unreachable target
    private static final int MINE_LOITER_DSQ = 4;   // "at the mine" if within this
    private static final int LOITER_LIMIT = 20;     // give up a dry mine after this
    private static final int CAT_FLEE_DSQ = 18;     // run once a cat is this close (pounce is 13)
    private static final int DEFEND_DSQ = 36;       // defend the king from enemies this close to it
    private static final int SECOND_KING_BEFORE_ROUND = 1200; // kings made later get culled (cutoff rule)
    private static final int SECOND_KING_MIN_CHEESE = 1500;   // only hedge with a clear surplus
    private static final int RAT_TRAP_COST = 20;              // TrapType.RAT_TRAP.buildCost
    private static final int MAX_RAT_TRAPS = 25;              // TrapType.RAT_TRAP.maxCount (team-wide)
    private static final int WALL_CHEESE_FLOOR = 100;         // rats keep a bigger reserve than the king

    static void act(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();

        if (cur.equals(lastLocation)) {
            stuckTurns += 1;
        } else {
            stuckTurns = 0;
        }
        lastLocation = cur;

        // Sense once per turn and reuse for every query (bytecode budget is 17500).
        RobotInfo[] robots = rc.senseNearbyRobots();
        MapInfo[] mapInfos = rc.senseNearbyMapInfos();
        Team team = rc.getTeam();

        RobotInfo king = Utils.nearestAlliedKing(robots, cur, team);
        if (king != null) homeKing = king.getLocation();
        else if (homeKing == null) homeKing = Comms.readKingLocation(rc);

        MapLocation mineSeen = Utils.nearestMine(mapInfos, cur);
        if (mineSeen != null) rememberedMine = mineSeen;

        int raw = rc.getRawCheese();

        // FLEE_CAT: cats are unkillable (4000 hp) and hit for 20 — never trade, run.
        RobotInfo cat = Utils.nearestCat(robots, cur);
        if (cat != null && cur.isWithinDistanceSquared(cat.getLocation(), CAT_FLEE_DSQ)) {
            state = "FLEE_CAT";
            fleeCats(rc, robots);
            indicate(rc, raw);
            return;
        }

        // SECOND KING (survival hedge): the game ends the moment a team loses its
        // last king, so a backup king means the enemy must starve out two of them.
        // But a second king doubles upkeep to 4/round, so it only pays off when the
        // economy can clearly sustain it. canBecomeRatKing needs 7 allied rats in
        // our 3x3 + 50 cheese, so this only fires when foragers are already bunched
        // (e.g. loitering at a rich mine) and the pool is still high — never during
        // a starvation race, where doubling upkeep would kill both kings sooner.
        if (rc.getRoundNum() < SECOND_KING_BEFORE_ROUND
                && rc.getAllCheese() >= SECOND_KING_MIN_CHEESE
                && rc.canBecomeRatKing()) {
            rc.becomeRatKing();
            return;
        }

        // DEFEND: an enemy threatening our king — rally to it and fight defensively.
        RobotInfo enemy = Utils.nearestEnemy(robots, cur, team.opponent());
        if (enemy != null && homeKing != null
                && enemy.getLocation().isWithinDistanceSquared(homeKing, DEFEND_DSQ)) {
            state = "DEFEND";
            // Once at war, a defender on the king's doorstep has three escalating
            // home-defence options, all of which remove a biter from the ~80
            // dmg/round swarm before resorting to a plain trade:
            //   1. if we are already carrying an enemy, finish hurling it away;
            //   2. else lay a rat trap on the king's ring (50 dmg + 30 stun) — the
            //      king alone lays only 1/round, so defenders thicken it far faster;
            //   3. else ratnap the biggest enemy biter we can (it is then stunned
            //      and immune, neutralised for as long as we hold it).
            // All war-gated, so none of this fires (nor spends cheese) versus a
            // passive opponent we never go to war with.
            boolean war = !rc.isCooperation();
            boolean acted = false;
            if (war) {
                if (rc.getCarrying() != null) {
                    acted = Micro.ratnapDefend(rc, robots, homeKing);
                } else if (layKingWallTrap(rc, cur)) {
                    acted = true;
                } else {
                    acted = Micro.ratnapDefend(rc, robots, homeKing);
                }
            }
            if (!acted && !Micro.fight(rc, robots, homeKing)) {
                Pathfinder.moveTo(rc, enemy.getLocation());
            }
            indicate(rc, raw);
            return;
        }

        if (raw >= RETURN_THRESHOLD && homeKing != null) {
            state = "RETURN";
            if (!transferAllCheese(rc, raw)) Pathfinder.moveTo(rc, homeKing);
            indicate(rc, raw);
            return;
        }

        MapLocation cheese = Utils.nearestCheese(mapInfos, cur);
        if (cheese != null) {
            state = "COLLECT";
            if (rc.canPickUpCheese(cheese)) rc.pickUpCheese(cheese);
            else Pathfinder.moveTo(rc, cheese);
            indicate(rc, raw);
            return;
        }

        if (rememberedMine != null) {
            boolean atMine = cur.isWithinDistanceSquared(rememberedMine, MINE_LOITER_DSQ);
            int patience = atMine ? LOITER_LIMIT : STUCK_LIMIT;
            if (stuckTurns >= patience) {
                rememberedMine = null;
                stuckTurns = 0;
            } else {
                state = "COLLECT";
                if (!atMine) Pathfinder.moveTo(rc, rememberedMine); // else loiter for spawns
                indicate(rc, raw);
                return;
            }
        }

        state = "EXPLORE";
        MapLocation commsMine = Comms.readNearestKnownMine(rc, cur); // a mine a king has seen
        boolean reached = exploreTarget != null && cur.distanceSquaredTo(exploreTarget) <= REACHED_DIST_SQ;
        if (exploreTarget == null || reached) {
            exploreTarget = (commsMine != null) ? commsMine : Utils.randomLocation(rc);
            stuckTurns = 0;
        } else if (stuckTurns >= STUCK_LIMIT) {
            exploreTarget = Utils.randomLocation(rc); // break out of an unreachable target
            stuckTurns = 0;
        }
        Pathfinder.moveTo(rc, exploreTarget);
        indicate(rc, raw);
    }

    /// Flee cats by maximising the minimum distance to any nearby cat. Scanning
    /// every legal move (rather than a fixed away-heading) escapes corners and
    /// naturally goes perpendicular when straight-away is walled, while keeping us
    /// clear of a second cat trying to cut us off. Cats pounce to dist^2 13 and
    /// move at half a rat's speed, so steadily widening the gap is the whole game.
    private static void fleeCats(RobotController rc, RobotInfo[] nearby) throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation me = rc.getLocation();
        int bestScore = -1;
        Direction bestDir = null;
        for (Direction d : Utils.directions) {
            if (!rc.canMove(d)) continue;
            MapLocation to = me.add(d);
            int minDist = Integer.MAX_VALUE;
            for (RobotInfo r : nearby) {
                if (r.getType() != UnitType.CAT) continue;
                int dist = to.distanceSquaredTo(r.getLocation());
                if (dist < minDist) minDist = dist;
            }
            if (minDist > bestScore) {
                bestScore = minDist;
                bestDir = d;
            }
        }
        if (bestDir != null) rc.move(bestDir);
    }

    /**
     * Help wall the king: place a rat trap on a king-perimeter tile (Chebyshev
     * distance 2 from the king's centre — an enemy biting square) that is within
     * this rat's own build range (distance^2 2). Cheap and team-immune, with a
     * generous cheese floor so foragers don't bankrupt the king, and self-limited
     * by the 25-trap team cap (once the ring is full, placement fails and the rat
     * falls through to fighting). Returns true if a trap was placed.
     */
    private static boolean layKingWallTrap(RobotController rc, MapLocation cur) throws GameActionException {
        if (homeKing == null) return false;
        if (rc.getNumberRatTraps() >= MAX_RAT_TRAPS) return false;
        if (rc.getAllCheese() < RAT_TRAP_COST + WALL_CHEESE_FLOOR) return false;
        MapLocation best = null;
        int bestDsq = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != 2) continue; // ring tiles only
                MapLocation loc = homeKing.translate(dx, dy);
                int d = cur.distanceSquaredTo(loc);
                if (d > 2) continue;                 // must be within this rat's build range
                if (!rc.canPlaceRatTrap(loc)) continue;
                if (d < bestDsq) {
                    bestDsq = d;
                    best = loc;
                }
            }
        }
        if (best == null) return false;
        rc.placeRatTrap(best);
        return true;
    }

    private static boolean transferAllCheese(RobotController rc, int amount) throws GameActionException {
        if (homeKing == null || amount <= 0) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation t = homeKing.translate(dx, dy);
                if (rc.canTransferCheese(t, amount)) {
                    rc.transferCheese(t, amount);
                    return true;
                }
            }
        }
        return false;
    }

    private static void indicate(RobotController rc, int raw) {
        rc.setIndicatorString("BABY_RAT | " + state + " | raw=" + raw + " | king=" + homeKing);
    }
}
