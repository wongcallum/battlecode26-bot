package wongcallum;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

// role: collect cheese, explore, defend, fight defensively
// state machine states: EXPLORE / COLLECT / RETURN / DEFEND / FLEE_CAT
public final class BabyRat {
    private BabyRat() {}

    static MapLocation exploreTarget = null;
    static MapLocation lastLocation = null;
    static int stuckTurns = 0;

    private static final int REACHED_DIST_SQ = 8;
    private static final int STUCK_LIMIT = 12;

    static void act(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();

        if (cur.equals(lastLocation)) {
            stuckTurns += 1;
        } else {
            stuckTurns = 0;
        }
        lastLocation = cur;

        // explore
        boolean reached = exploreTarget != null && cur.distanceSquaredTo(exploreTarget) <= REACHED_DIST_SQ;
        if (exploreTarget == null || reached || stuckTurns >= STUCK_LIMIT) {
            exploreTarget = Utils.randomLocation(rc);
            stuckTurns = 0;
        }

        Pathfinder.moveTo(rc, exploreTarget);

        rc.setIndicatorString("BABY_RAT | EXPLORE -> " + exploreTarget + " | stuck=" + stuckTurns);
        // TODO: sense cheese/cats/king and branch into COLLECT/DEFEND/FLEE_CAT.
    }
}
