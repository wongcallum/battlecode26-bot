package wongcallum;

import battlecode.common.Direction;
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
    private static final int CAT_FLEE_DSQ = 18;     // run once a cat is this close (pounce is 13)
    private static final int DEFEND_DSQ = 36;       // defend the king from enemies this close to it

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

        // FLEE_CAT: cats are unkillable (4000 hp) and hit for 20 — never trade, run.
        RobotInfo cat = Utils.nearestCat(rc);
        if (cat != null && cur.isWithinDistanceSquared(cat.getLocation(), CAT_FLEE_DSQ)) {
            state = "FLEE_CAT";
            fleeFrom(rc, cat.getLocation());
            indicate(rc, raw);
            return;
        }

        // DEFEND: an enemy threatening our king — rally to it and fight defensively.
        RobotInfo enemy = Utils.nearestEnemy(rc);
        if (enemy != null && homeKing != null
                && enemy.getLocation().isWithinDistanceSquared(homeKing, DEFEND_DSQ)) {
            state = "DEFEND";
            // Micro picks a favourable bite or a retreat toward the king; if it
            // finds nothing worth doing yet, close the gap to the threat.
            if (!Micro.fight(rc, rc.senseNearbyRobots(), homeKing)) {
                Pathfinder.moveTo(rc, enemy.getLocation());
            }
            indicate(rc, raw);
            return;
        }

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

    /** Step directly away from a threat, trying nearby headings if blocked. */
    private static void fleeFrom(RobotController rc, MapLocation threat) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction away = threat.directionTo(rc.getLocation());
        if (away == Direction.CENTER) away = Direction.NORTH;
        Direction[] tries = {away, away.rotateLeft(), away.rotateRight(),
                away.rotateLeft().rotateLeft(), away.rotateRight().rotateRight()};
        for (Direction d : tries) {
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
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
