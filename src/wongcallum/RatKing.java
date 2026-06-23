package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

// defensive behaviour, maintain cheese, spawn baby rats, defend
public final class RatKing {
    private RatKing() {
    }

    private static final int TARGET_RATS = 10;
    private static final int CHEESE_BUFFER = 400;
    private static final int SPAWN_INTERVAL = 8;

    static int lastSpawnRound = -SPAWN_INTERVAL;

    static void act(RobotController rc) throws GameActionException {
        int cheese = rc.getAllCheese();
        int round = rc.getRoundNum();
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + cheese);

        // try to sustainably spawn by having a rate limit and a minimum buffer
        int liveRats = Utils.visibleAlliedRats(rc);
        if (round - lastSpawnRound >= SPAWN_INTERVAL
                && liveRats < TARGET_RATS
                && cheese >= rc.getCurrentRatCost() + CHEESE_BUFFER) {
            // King is 3x3
            for (Direction d : Utils.directions) {
                MapLocation loc = rc.getLocation().translate(2 * d.getDeltaX(), 2 * d.getDeltaY());
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    lastSpawnRound = round;
                    break;
                }
            }
        }

        // TODO(next): anti-rush defence, global-array SOS, defensive micro.
    }
}
