package wongcallum;

import battlecode.common.Direction;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
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

    static RobotInfo nearestAlliedKing(RobotInfo[] robots, MapLocation me, Team team) {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getType() == UnitType.RAT_KING && r.getTeam() == team) {
                int d = me.distanceSquaredTo(r.getLocation());
                if (d < bestDist) { bestDist = d; best = r; }
            }
        }
        return best;
    }

    static RobotInfo nearestCat(RobotInfo[] robots, MapLocation me) {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getType() != UnitType.CAT) continue;
            int d = me.distanceSquaredTo(r.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    static RobotInfo nearestEnemy(RobotInfo[] robots, MapLocation me, Team opponent) {
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getTeam() != opponent) continue;
            int d = me.distanceSquaredTo(r.getLocation());
            if (d < bestDist) { bestDist = d; best = r; }
        }
        return best;
    }

    static MapLocation nearestCheese(MapInfo[] infos, MapLocation me) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : infos) {
            if (info.getCheeseAmount() > 0) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bestDist) { bestDist = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }

    static MapLocation nearestMine(MapInfo[] infos, MapLocation me) {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : infos) {
            if (info.hasCheeseMine()) {
                int d = me.distanceSquaredTo(info.getMapLocation());
                if (d < bestDist) { bestDist = d; best = info.getMapLocation(); }
            }
        }
        return best;
    }
}
