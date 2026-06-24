package wongcallum;

import battlecode.common.*;

public class BabyRat {
    static MapLocation exploreTarget = null;
    static int targetTurns = 0;

    static void act(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();

        // repick when arrived, or after long enough that the target is likely unreachable
        if (exploreTarget == null || cur.distanceSquaredTo(exploreTarget) <= 8 || targetTurns > 60) {
            exploreTarget = randomLocation(rc);
            targetTurns = 0;
        }
        targetTurns++;

        // repick at once if Bug2 reports the target unreachable
        if (!Pathfinder.moveTo(rc, exploreTarget)) {
            exploreTarget = randomLocation(rc);
            targetTurns = 0;
        }
        rc.setIndicatorString("explore -> " + exploreTarget);
    }

    private static MapLocation randomLocation(RobotController rc) {
        int x = Const.rng.nextInt(rc.getMapWidth());
        int y = Const.rng.nextInt(rc.getMapHeight());
        return new MapLocation(x, y);
    }
}
