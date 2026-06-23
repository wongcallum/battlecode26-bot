package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

// defensive behaviour, maintain cheese, spawn baby rats, defend
public final class RatKing {
    private RatKing() {}

    static int spawned = 0;

    private static final int SOFT_CAP = 8; // placeholder
    private static final int CHEESE_RESERVE = 200;

    static void act(RobotController rc) throws GameActionException {
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + rc.getAllCheese() + " rats=" + spawned);

        if (spawned < SOFT_CAP && rc.getAllCheese() >= rc.getCurrentRatCost() + CHEESE_RESERVE) {
            for (Direction d : Utils.directions) {
                MapLocation loc = rc.getLocation().translate(2 * d.getDeltaX(), 2 * d.getDeltaY());
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    spawned += 1;
                    break;
                }
            }
        }

        // TODO: avoid starvation, sustainable spawning, anti-rush defence, shared state.
    }
}
