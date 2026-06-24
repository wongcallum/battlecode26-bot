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

        if (rc.getGlobalCheese() <= SPAWN_FLOOR) return;

        MapLocation center = rc.getLocation();
        for (Direction d : Const.DIRECTIONS) {
            MapLocation loc = center.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                return;
            }
        }
    }

    // store our location in the shared array so baby rats know where to deliver
    private static void broadcastLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        rc.writeSharedArray(Const.KING_X_SLOT, me.x + 1);
        rc.writeSharedArray(Const.KING_Y_SLOT, me.y + 1);
    }
}
