package wongcallum;

import battlecode.common.*;

public class RatKing {
    // keep this much global cheese as a buffer for upkeep (2/round) before spawning.
    // rats now deliver cheese, so the floor protects king survival while still
    // letting us build a workforce when income is healthy.
    static final int SPAWN_FLOOR = 400;

    static void act(RobotController rc) throws GameActionException {
        broadcastLocation(rc);

        // free global cheese: grab anything within reach (king has 360 vision)
        for (MapInfo mi : rc.senseNearbyMapInfos(GameConstants.CHEESE_PICK_UP_RADIUS_SQUARED)) {
            if (mi.getCheeseAmount() > 0 && rc.canPickUpCheese(mi.getMapLocation())) {
                rc.pickUpCheese(mi.getMapLocation());
            }
        }

        MapLocation cur = rc.getLocation();

        // war mode (the backstab has happened): turtle behind a hidden trap ring and
        // a war-chest. gated on !cooperation so against a passive opponent (no
        // backstab) the peace economy and cat-trap pillar are completely untouched —
        // crucially, no SOS fires for harmless cooperating enemy foragers.
        if (!rc.isCooperation()) {
            RobotInfo[] enemies = rc.senseNearbyRobots(Const.KING_THREAT_RADIUS_SQUARED, rc.getTeam().opponent());
            rc.writeSharedArray(Const.SOS_SLOT, enemies.length > 0 ? 1 : 0);
            warAct(rc, cur, enemies);
            return;
        }
        rc.writeSharedArray(Const.SOS_SLOT, 0);

        // peace: ring cats with cat traps, otherwise run the economy spawn
        RobotInfo cat = Combat.nearestCat(cur, rc.senseNearbyRobots(GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED * 2));
        if (cat != null && Combat.kingHandleCat(rc, cur, cat)) return;

        if (rc.getGlobalCheese() <= SPAWN_FLOOR) return;
        // identical to the pre-V5 economy spawn so peace play is provably unchanged
        for (Direction d : Const.DIRECTIONS) {
            MapLocation loc = cur.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                return;
            }
        }
    }

    private static void warAct(RobotController rc, MapLocation cur, RobotInfo[] enemies) throws GameActionException {
        // standing minefield first — lay it early while the perimeter is still free
        if (Combat.kingLayRatRing(rc, cur)) return;
        // attackers in melee: bite the weakest to thin them
        if (enemies.length > 0
                && Combat.biteBest(rc, cur, enemies, GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED)) {
            return;
        }
        // keep a war-chest; only spend the surplus on bodies (toward the threat)
        if (rc.getGlobalCheese() > Const.WAR_RESERVE) {
            Direction toward = enemies.length > 0
                    ? cur.directionTo(Combat.nearestEnemy(cur, enemies).getLocation())
                    : Direction.NORTH;
            spawnToward(rc, cur, toward);
        }
    }

    // build a rat near the king, preferring the threat side then fanning out
    private static boolean spawnToward(RobotController rc, MapLocation center, Direction toward) throws GameActionException {
        Direction[] pref = { toward, toward.rotateLeft(), toward.rotateRight() };
        for (Direction d : pref) {
            MapLocation loc = center.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                return true;
            }
        }
        for (Direction d : Const.DIRECTIONS) {
            MapLocation loc = center.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                return true;
            }
        }
        return false;
    }

    // store our location in the shared array so baby rats know where to deliver
    private static void broadcastLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        rc.writeSharedArray(Const.KING_X_SLOT, me.x + 1);
        rc.writeSharedArray(Const.KING_Y_SLOT, me.y + 1);
    }
}
