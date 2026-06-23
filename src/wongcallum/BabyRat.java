package wongcallum;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

// role: collect cheese, explore, defend, fight defensively
// state machine states: EXPLORE / COLLECT / RETURN / DEFEND / FLEE_CAT
public final class BabyRat {
    private BabyRat() {
    }

    static MapLocation homeKing = null;       // last known allied king location
    static MapLocation rememberedMine = null; // a cheese source to fall back to
    static MapLocation exploreTarget = null;
    static MapLocation lastLocation = null;
    static int stuckTurns = 0;
    static String state = "EXPLORE";

    private static final int RETURN_THRESHOLD = 40; // raw cheese before heading home
    private static final int REACHED_DIST_SQ = 8;
    private static final int STUCK_LIMIT = 12;      // abandon an unreachable target
    private static final int MINE_LOITER_DSQ = 4;   // "at the mine" if within this
    private static final int LOITER_LIMIT = 20;     // give up a dry mine after this

    static void act(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();

        if (cur.equals(lastLocation)) {
            stuckTurns += 1;
        } else {
            stuckTurns = 0;
        }
        lastLocation = cur;

        RobotInfo king = Utils.nearestAlliedKing(rc);
        if (king != null) homeKing = king.getLocation();
        else if (homeKing == null) homeKing = Comms.readKingLocation(rc);

        MapLocation mineSeen = Utils.nearestMine(rc);
        if (mineSeen != null) rememberedMine = mineSeen;

        int raw = rc.getRawCheese();

        if (raw >= RETURN_THRESHOLD && homeKing != null) {
            state = "RETURN";
            if (!transferAllCheese(rc, raw)) Pathfinder.moveTo(rc, homeKing);
            indicate(rc, raw);
            return;
        }

        MapLocation cheese = Utils.nearestCheese(rc);
        if (cheese != null) {
            state = "COLLECT";
            if (rc.canPickUpCheese(cheese)) rc.pickUpCheese(cheese);
            else Pathfinder.moveTo(rc, cheese);
            indicate(rc, raw);
            return;
        }

        if (rememberedMine != null) {
            boolean atMine = cur.isWithinDistanceSquared(rememberedMine, MINE_LOITER_DSQ);
            int patience = atMine ? LOITER_LIMIT : STUCK_LIMIT;
            if (stuckTurns >= patience) {
                rememberedMine = null;
                stuckTurns = 0;
            } else {
                state = "COLLECT";
                if (!atMine) Pathfinder.moveTo(rc, rememberedMine); // else loiter for spawns
                indicate(rc, raw);
                return;
            }
        }

        state = "EXPLORE";
        MapLocation commsMine = Comms.readNearestKnownMine(rc, cur); // a mine a king has seen
        boolean reached = exploreTarget != null && cur.distanceSquaredTo(exploreTarget) <= REACHED_DIST_SQ;
        if (exploreTarget == null || reached) {
            exploreTarget = (commsMine != null) ? commsMine : Utils.randomLocation(rc);
            stuckTurns = 0;
        } else if (stuckTurns >= STUCK_LIMIT) {
            exploreTarget = Utils.randomLocation(rc); // break out of an unreachable target
            stuckTurns = 0;
        }
        Pathfinder.moveTo(rc, exploreTarget);
        indicate(rc, raw);
    }

    private static boolean transferAllCheese(RobotController rc, int amount) throws GameActionException {
        if (homeKing == null || amount <= 0) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation t = homeKing.translate(dx, dy);
                if (rc.canTransferCheese(t, amount)) {
                    rc.transferCheese(t, amount);
                    return true;
                }
            }
        }
        return false;
    }

    private static void indicate(RobotController rc, int raw) {
        rc.setIndicatorString("BABY_RAT | " + state + " | raw=" + raw + " | king=" + homeKing);
        // TODO: sense cats/king-threat and branch into DEFEND/FLEE_CAT.
    }
}
