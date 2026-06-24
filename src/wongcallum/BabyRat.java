package wongcallum;

import battlecode.common.*;

public class BabyRat {
    // raw cheese carried before heading home. carrying cheese scales cooldown by
    // (1 + 0.01*raw), so ~40 keeps us under a 1.4x slowdown on the trip back.
    static final int RETURN_THRESHOLD = 40;

    static MapLocation home = null;        // last known allied rat king location
    static MapLocation exploreTarget = null;
    static int targetTurns = 0;

    static void act(RobotController rc) throws GameActionException {
        MapLocation cur = rc.getLocation();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapInfo[] infos = rc.senseNearbyMapInfos();

        updateHome(rc, allies, cur);
        pickUpAdjacent(rc, infos);

        int raw = rc.getRawCheese();
        MapLocation nearestCheese = nearestCheese(cur, infos);

        // head home when full, or when carrying cheese with nothing more in sight
        boolean returning = home != null && (raw >= RETURN_THRESHOLD || (raw > 0 && nearestCheese == null));
        if (returning) {
            returnHome(rc, cur, raw);
            rc.setIndicatorString("return " + raw + " -> " + home);
            return;
        }

        // collecting: go to the nearest visible cheese, otherwise explore
        MapLocation target = nearestCheese;
        if (target == null) {
            if (exploreTarget == null || cur.distanceSquaredTo(exploreTarget) <= 8 || targetTurns > 60) {
                exploreTarget = randomLocation(rc);
                targetTurns = 0;
            }
            targetTurns++;
            target = exploreTarget;
        }
        if (!Pathfinder.moveTo(rc, target) && target == exploreTarget) {
            exploreTarget = randomLocation(rc);
            targetTurns = 0;
        }
        rc.setIndicatorString("collect " + raw + " -> " + target);
    }

    // close to the king: face it and dump our cheese; otherwise navigate toward it
    private static void returnHome(RobotController rc, MapLocation cur, int raw) throws GameActionException {
        if (cur.distanceSquaredTo(home) <= GameConstants.CHEESE_TRANSFER_RADIUS_SQUARED) {
            Direction toKing = cur.directionTo(home);
            if (rc.getDirection() != toKing && rc.isTurningReady() && rc.canTurn(toKing)) {
                rc.turn(toKing);
            }
            if (rc.canTransferCheese(home, raw)) {
                rc.transferCheese(home, raw);
                return;
            }
        }
        Pathfinder.moveTo(rc, home);
    }

    // prefer a directly-sensed king (handles movement / multiple kings); else fall
    // back to the location the king broadcasts in the shared array
    private static void updateHome(RobotController rc, RobotInfo[] allies, MapLocation cur) throws GameActionException {
        MapLocation best = null;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo r : allies) {
            if (r.getType() == UnitType.RAT_KING) {
                int d = cur.distanceSquaredTo(r.getLocation());
                if (d < bestD) {
                    bestD = d;
                    best = r.getLocation();
                }
            }
        }
        if (best != null) {
            home = best;
            return;
        }
        int x = rc.readSharedArray(Const.KING_X_SLOT);
        int y = rc.readSharedArray(Const.KING_Y_SLOT);
        if (x > 0 && y > 0) {
            home = new MapLocation(x - 1, y - 1);
        }
    }

    // pick up every cheese tile in reach (free: no cooldown, just needs line of sight)
    private static void pickUpAdjacent(RobotController rc, MapInfo[] infos) throws GameActionException {
        for (MapInfo mi : infos) {
            if (mi.getCheeseAmount() > 0 && rc.canPickUpCheese(mi.getMapLocation())) {
                rc.pickUpCheese(mi.getMapLocation());
            }
        }
    }

    private static MapLocation nearestCheese(MapLocation cur, MapInfo[] infos) {
        MapLocation best = null;
        int bestD = Integer.MAX_VALUE;
        for (MapInfo mi : infos) {
            if (mi.getCheeseAmount() > 0) {
                int d = cur.distanceSquaredTo(mi.getMapLocation());
                if (d < bestD) {
                    bestD = d;
                    best = mi.getMapLocation();
                }
            }
        }
        return best;
    }

    private static MapLocation randomLocation(RobotController rc) {
        int x = Const.rng.nextInt(rc.getMapWidth());
        int y = Const.rng.nextInt(rc.getMapHeight());
        return new MapLocation(x, y);
    }
}
