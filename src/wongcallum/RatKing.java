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
            warAct(rc, cur, enemies);
            return;
        }

        // peace: ring cats with cat traps, otherwise run the economy spawn
        RobotInfo cat = Combat.nearestCat(cur, rc.senseNearbyRobots(GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED * 2));
        if (cat != null && Combat.kingHandleCat(rc, cur, cat)) return;

        if (rc.getGlobalCheese() <= SPAWN_FLOOR) return;
        // don't burn the reserve spawning into a pileup: when many rats already crowd
        // the king (a cheese-scarce or boxed-in map where foragers can't disperse to
        // find cheese), extra bodies bring no income and just drain us toward
        // starvation — conserve the reserve and survive on it instead.
        if (rc.senseNearbyRobots(Const.PEACE_CROWD_RADIUS_SQUARED, rc.getTeam()).length >= Const.PEACE_POP_CAP) return;
        for (Direction d : Const.DIRECTIONS) {
            MapLocation loc = cur.translate(2 * d.dx, 2 * d.dy);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                return;
            }
        }
    }

    private static void warAct(RobotController rc, MapLocation cur, RobotInfo[] enemies) throws GameActionException {
        // standing minefield first — lay it early while the perimeter is still free.
        // the ring alone keeps the king safe (enemies die stepping into bite range),
        // so we spend NO cheese on bodies: our army stays out foraging for income.
        if (Combat.kingLayRatRing(rc, cur)) return;
        if (enemies.length > 0) {
            Combat.biteBest(rc, cur, enemies, GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED);
        }
    }

    // store our location in the shared array so baby rats know where to deliver
    private static void broadcastLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        rc.writeSharedArray(Const.KING_X_SLOT, me.x + 1);
        rc.writeSharedArray(Const.KING_Y_SLOT, me.y + 1);
    }
}
