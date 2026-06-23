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

    // have a range of rats scaled to income to improve performance on maps with low cheese
    private static final int RATS_MIN = 4;
    private static final int RATS_MAX = 14;
    private static final int RATS_PER_MINE = 2;
    private static final int CHEESE_BUFFER = 400;
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
        RobotInfo enemy = Utils.nearestEnemy(rc);
        RobotInfo cat = Utils.nearestCat(rc);

        if (enemy != null && rc.canAttack(enemy.getLocation())) {
            rc.attack(enemy.getLocation());
        } else if (cat != null
                && rc.getLocation().isWithinDistanceSquared(cat.getLocation(), CAT_TRAP_RANGE_DSQ)
                && rc.getNumberCatTraps() < TrapType.CAT_TRAP.maxCount
                && cheese >= CHEESE_BUFFER + TrapType.CAT_TRAP.buildCost
                && placeCatTrapToward(rc, cat.getLocation())) {
        } else if (spawnedCount < Math.min(RATS_MAX, RATS_MIN + RATS_PER_MINE * Comms.knownMines)
                && round - lastSpawnRound >= SPAWN_INTERVAL
                && cheese >= rc.getCurrentRatCost() + CHEESE_BUFFER) {
            // don't rely on visible rats, the king spawns too many on big maps
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
