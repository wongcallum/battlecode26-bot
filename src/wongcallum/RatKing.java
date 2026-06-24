package wongcallum;

import battlecode.common.*;

public class RatKing {
    static final int CHEESE_RESERVE = 2000;

    static void act(RobotController rc) throws GameActionException {
        if (rc.getGlobalCheese() <= CHEESE_RESERVE) return;

        MapLocation center = rc.getLocation();
        for (Direction d : Const.DIRECTIONS) {
            MapLocation loc = center.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                // spawning so baby rats exist to test navigation
                rc.buildRat(loc);
                return;
            }
        }
    }
}
