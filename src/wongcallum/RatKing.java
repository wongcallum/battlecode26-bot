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
    private static final int CHEESE_BUFFER = 800;
    private static final int SPAWN_INTERVAL = 8;
    private static final int CAT_TRAP_RANGE_DSQ = 16; // only trap a cat closing on us
    private static final int WAR_CHEESE_FLOOR = 60;   // keep a little cheese once at war
    private static final int TRAP_GUARD_DSQ = 25;     // top the wall up only while a threat is this close
    private static final int MIN_RING = 10;           // ...but always keep at least this many traps up

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

        // sense once and reuse
        RobotInfo[] robots = rc.senseNearbyRobots();
        RobotInfo enemy = Utils.nearestEnemy(robots, rc.getLocation(), rc.getTeam().opponent());
        RobotInfo cat = Utils.nearestCat(robots, rc.getLocation());

        boolean war = !rc.isCooperation();

        // testing against aggressive bots shows that the trap wall works,
        // but then we starve because repairing the ring uses too much cheese
        // try to spend cheese only to repair when there is an incoming threat
        boolean enemyNearKing = enemy != null
                && rc.getLocation().isWithinDistanceSquared(enemy.getLocation(), TRAP_GUARD_DSQ);
        boolean maintainWall = enemyNearKing || rc.getNumberRatTraps() < MIN_RING;
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + cheese
                + " spawned=" + spawnedCount + " ratTraps=" + rc.getNumberRatTraps()
                + (war ? " WAR" : " COOP"));

        if (enemy != null && rc.canAttack(enemy.getLocation())) {
            rc.attack(enemy.getLocation());
        } else if (war && maintainWall && buildRatTrapWall(rc, enemy, cheese)) {
            // at war, ring the king with rat traps, each doing dmg + stun
            // in an attempt to curb a bite swarm that can kill the king in 8 rounds
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

    // try to stop enemy rats from biting, using traps placed nearest to threat
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
