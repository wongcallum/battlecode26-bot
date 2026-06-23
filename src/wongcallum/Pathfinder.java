package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/// concept taken from the [pathfinding lecture](https://www.youtube.com/watch?v=Hkk33bEfrzg)
/// tries to follow a straight line towards the goal
/// when blocked, follow the obstacle boundary until we reach the line again and resume
public final class Pathfinder {
    private Pathfinder() {}

    static MapLocation goal = null;
    static MapLocation start = null;
    static boolean following = false;
    static int hitDistSq = 0;
    static Direction currentDir = null;

    static void moveTo(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return;

        if (!target.equals(goal)) {
            goal = target;
            start = rc.getLocation();
            following = false;
            currentDir = null;
        }

        if (!rc.isMovementReady()) return;

        MapLocation cur = rc.getLocation();
        if (cur.equals(goal)) return;

        if (!following) {
            // try to go straight towards the goal
            Direction dir = cur.directionTo(goal);
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
            // start following the boundary
            following = true;
            hitDistSq = cur.distanceSquaredTo(goal);
            currentDir = dir;
            wallFollowStep(rc, cur);
            return;
        }

        if (onLine(cur) && cur.distanceSquaredTo(goal) < hitDistSq) {
            Direction dir = cur.directionTo(goal);
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                following = false;
                currentDir = null;
                rc.move(dir);
                return;
            }
            hitDistSq = cur.distanceSquaredTo(goal);
        }
        wallFollowStep(rc, cur);
    }

    static boolean arrived(RobotController rc, MapLocation target) {
        return target != null && rc.getLocation().equals(target);
    }

    static void reset() {
        goal = null;
        start = null;
        following = false;
        currentDir = null;
    }

    private static void wallFollowStep(RobotController rc, MapLocation cur) throws GameActionException {
        if (currentDir == null || currentDir == Direction.CENTER) {
            currentDir = cur.directionTo(goal);
            if (currentDir == Direction.CENTER) currentDir = Direction.NORTH;
        }
        Direction check = currentDir.rotateRight().rotateRight();
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(check)) {
                rc.move(check);
                currentDir = check;
                return;
            }
            check = check.rotateLeft();
        }
    }

    // within 1 cell of the line? using cross product
    private static boolean onLine(MapLocation pos) {
        int dx = goal.x - start.x;
        int dy = goal.y - start.y;
        if (dx == 0 && dy == 0) return true;
        long cross = (long) (pos.x - start.x) * dy - (long) (pos.y - start.y) * dx;
        long lenSq = (long) dx * dx + (long) dy * dy;
        return cross * cross <= lenSq;
    }
}
