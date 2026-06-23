package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

// defensive behaviour, maintain cheese, spawn baby rats, defend
public final class RatKing {
    private RatKing() {
    }

    private static final int TARGET_RATS = 12;
    private static final int CHEESE_BUFFER = 400;
    private static final int SPAWN_INTERVAL = 8;

    static int lastSpawnRound = -SPAWN_INTERVAL;
    static int spawnedCount = 0;                     // persistent total spawned

    static void act(RobotController rc) throws GameActionException {
        int cheese = rc.getAllCheese();
        int round = rc.getRoundNum();
        rc.setIndicatorString("RAT_KING | hp=" + rc.getHealth() + " cheese=" + cheese + " spawned=" + spawnedCount);

        // on big maps, if we only count the visible maps then the king will overspawn
        // the rate limit and buffer make sure we dont spawn all at once
        if (spawnedCount < TARGET_RATS
                && round - lastSpawnRound >= SPAWN_INTERVAL
                && cheese >= rc.getCurrentRatCost() + CHEESE_BUFFER) {
            // King is 3x3
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

        // TODO(next): anti-rush defence, global-array SOS, defensive micro.
    }
}
