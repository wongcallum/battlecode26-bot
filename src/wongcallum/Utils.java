package wongcallum;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

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
}
