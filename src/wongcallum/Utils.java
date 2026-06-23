package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Utils {
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static MapLocation randomLocation(RobotController rc) {
        int x = RobotPlayer.rng.nextInt(rc.getMapWidth());
        int y = RobotPlayer.rng.nextInt(rc.getMapHeight());
        return new MapLocation(x, y);
    }

    static RobotInfo nearestAlliedKing(RobotController rc) throws GameActionException {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (r.getType() == UnitType.RAT_KING) {
                int d = me.distanceSquaredTo(r.getLocation());
                if (d < bestDist) { bestDist = d; best = r; }
            }
        }
        return best;
    }

    static MapLocation nearestCheese(RobotController rc) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.getCheeseAmount() > 0) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bestDist) { bestDist = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }

    static MapLocation nearestMine(RobotController rc) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.hasCheeseMine()) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bestDist) { bestDist = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }

    static int visibleAlliedRats(RobotController rc) throws GameActionException {
        int n = 0;
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (r.getType() == UnitType.BABY_RAT) n += 1;
        }
        return n;
    }

    static RobotInfo nearestCat(RobotController rc) {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();
        for (RobotInfo r : rc.senseNearbyRobots()) {
            if (r.getType() != UnitType.CAT) continue;
            int d = me.distanceSquaredTo(r.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    static RobotInfo nearestEnemy(RobotController rc) throws GameActionException {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        MapLocation me = rc.getLocation();
        for (RobotInfo r : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            int d = me.distanceSquaredTo(r.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }
}
