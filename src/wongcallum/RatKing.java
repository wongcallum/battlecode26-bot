package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;

// defensive behaviour, maintain cheese, spawn baby rats, defend
public final class RatKing {
    private RatKing() {
    }

    // Work force is scaled to income, not fixed: baby rats cost cheese to spawn but
    // nothing to keep, so on cheese-poor maps every extra rat just shortens the
    // king's life (it brings no income the few mines don't already cover), while on
    // rich maps more foragers pay for themselves. Target ~RATS_PER_MINE per mine the
    // king can see, within [RATS_MIN, RATS_MAX].
    private static final int RATS_MIN = 4;
    private static final int RATS_MAX = 14;
    private static final int RATS_PER_MINE = 2;
    private static final int CHEESE_BUFFER = 800;
    private static final int SPAWN_INTERVAL = 8;
    private static final int CAT_TRAP_RANGE_DSQ = 16; // only trap a cat closing on us
    private static final int WAR_CHEESE_FLOOR = 60;   // keep a little cheese once at war

    static int lastSpawnRound = -SPAWN_INTERVAL;
    static int spawnedCount = 0;                     // persistent total spawned

    static void act(RobotController rc) throws GameActionException {
        int cheese = rc.getAllCheese();
        int round = rc.getRoundNum();
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + cheese
                + " spawned=" + spawnedCount + " ratTraps=" + rc.getNumberRatTraps()
                + (rc.isCooperation() ? " COOP" : " WAR"));

        // Publish position + sensed mines so rats can navigate from anywhere.
        Comms.reportKingState(rc);

        // The king has one action per turn; spend it on defence before growth.
        // Sense once and derive both threats from it (one native call, not two).
        RobotInfo[] robots = rc.senseNearbyRobots();
        RobotInfo enemy = Utils.nearestEnemy(robots, rc.getLocation(), rc.getTeam().opponent());
        RobotInfo cat = Utils.nearestCat(robots, rc.getLocation());

        // "Uneasy Alliances": the game starts in cooperation (both rat teams fight
        // the cats) and only flips to war once a backstab is initiated — i.e. once
        // someone damages an enemy rat. We never initiate, so isCooperation() is a
        // clean "are we under attack?" flag: vs a passive opponent it stays true
        // forever and the trap wall below never fires (preserving our economy), but
        // the instant a real bot backstabs us we start walling the king in.
        boolean war = !rc.isCooperation();

        if (enemy != null && rc.canAttack(enemy.getLocation())) {
            // 1) bite the nearest enemy in reach (king reach is radius^2 8)
            rc.attack(enemy.getLocation());
        } else if (war && buildRatTrapWall(rc, enemy, cheese)) {
            // 2) at war, ring the king with rat traps (placed above): each does 50
            // damage + a 30-round stun to an enemy stepping near, blunting the
            // bite-swarm (up to 8 adjacent biters = 80 dmg/round) that otherwise
            // melts our 600-hp king in ~8 rounds.
        } else if (cat != null
                && rc.getLocation().isWithinDistanceSquared(cat.getLocation(), CAT_TRAP_RANGE_DSQ)
                && rc.getNumberCatTraps() < TrapType.CAT_TRAP.maxCount
                && cheese >= CHEESE_BUFFER + TrapType.CAT_TRAP.buildCost
                && placeCatTrapToward(rc, cat.getLocation())) {
            // 2) stun an approaching cat with a doorstep trap (placed above)
        } else if (spawnedCount < Math.min(RATS_MAX, RATS_MIN + RATS_PER_MINE * Comms.knownMines)
                && round - lastSpawnRound >= SPAWN_INTERVAL
                && cheese >= rc.getCurrentRatCost() + CHEESE_BUFFER) {
            // grow: scale to mines the king can see (persistent count; visible rats
            // undercount foragers on open maps and would over-spawn the whole pool)
            for (Direction d : Utils.directions) {
                MapLocation loc = rc.getLocation().translate(2 * d.getDeltaX(), 2 * d.getDeltaY());
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    spawnedCount += 1;
                    lastSpawnRound = round;
                    break;
                }
            }
        }
    }

    /**
     * Wall the king in with rat traps once at war. The 16 tiles at Chebyshev
     * distance 2 from the king's centre are exactly the squares an enemy must
     * stand on to bite our 3x3 body, so trapping them denies melee access. A rat
     * trap costs 20 cheese and does 50 damage + a 30-round stun (it triggers on
     * approach within radius^2 2, and is consumed), and we may hold up to 25 — so
     * the king re-fills the wall as traps are spent. We fill nearest-to-threat
     * first so a one-action-per-turn king walls the approach side before the
     * swarm arrives. Returns true if a trap was placed (the king's action spent).
     */
    private static boolean buildRatTrapWall(RobotController rc, RobotInfo enemy, int cheese)
            throws GameActionException {
        if (rc.getNumberRatTraps() >= TrapType.RAT_TRAP.maxCount) return false;
        if (cheese < TrapType.RAT_TRAP.buildCost + WAR_CHEESE_FLOOR) return false;

        MapLocation me = rc.getLocation();
        MapLocation threat = (enemy != null) ? enemy.getLocation() : null;
        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (Math.max(Math.abs(dx), Math.abs(dy)) != 2) continue; // ring tiles only
                MapLocation loc = me.translate(dx, dy);
                if (!rc.canPlaceRatTrap(loc)) continue; // off-map/wall/mine/occupied/already trapped
                int score = (threat != null) ? loc.distanceSquaredTo(threat) : 0;
                if (score < bestScore) {
                    bestScore = score;
                    best = loc;
                }
            }
        }
        if (best == null) return false;
        rc.placeRatTrap(best);
        return true;
    }

    /** Place a cat trap on the king's perimeter, preferring the cat's side. */
    private static boolean placeCatTrapToward(RobotController rc, MapLocation cat) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction toCat = me.directionTo(cat);
        MapLocation pref = me.add(toCat).add(toCat); // two steps toward the cat
        if (rc.canPlaceCatTrap(pref)) {
            rc.placeCatTrap(pref);
            return true;
        }
        for (Direction d : Utils.directions) {
            MapLocation loc = me.translate(2 * d.getDeltaX(), 2 * d.getDeltaY());
            if (rc.canPlaceCatTrap(loc)) {
                rc.placeCatTrap(loc);
                return true;
            }
        }
        return false;
    }
}
