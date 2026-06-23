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

    static int lastSpawnRound = -SPAWN_INTERVAL;
    static int spawnedCount = 0;                     // persistent total spawned

    static void act(RobotController rc) throws GameActionException {
        int cheese = rc.getAllCheese();
        int round = rc.getRoundNum();
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + cheese + " spawned=" + spawnedCount);

        // Publish position + sensed mines so rats can navigate from anywhere.
        Comms.reportKingState(rc);

        // The king has one action per turn; spend it on defence before growth.
        // Sense once and derive both threats from it (one native call, not two).
        RobotInfo[] robots = rc.senseNearbyRobots();
        RobotInfo enemy = Utils.nearestEnemy(robots, rc.getLocation(), rc.getTeam().opponent());
        RobotInfo cat = Utils.nearestCat(robots, rc.getLocation());

        if (enemy != null && rc.canAttack(enemy.getLocation())) {
            // 1) bite the nearest enemy in reach (king reach is radius^2 8)
            rc.attack(enemy.getLocation());
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
