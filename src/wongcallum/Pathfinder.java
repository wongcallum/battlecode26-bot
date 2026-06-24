package wongcallum;

import battlecode.common.*;

// Bug2 navigation from the [pathfinding lecture[(https://www.youtube.com/watch?v=Hkk33bEfrzg)
//
// Head straight at the goal along the line, when an obstacle
// blocks that, trace its boundary until we rejoin the line
// at a point strictly closer to the goal than where we hit, then resume.
public class Pathfinder {
    static MapLocation lastTarget = null;
    static MapLocation lineStart = null;
    static boolean followingWall = false;
    static int hitDistSq = 0;
    static MapLocation hitLoc = null; // where we started tracing this obstacle
    static int followSteps = 0;
    static Direction bugDir = Direction.NORTH; // last direction we traced along

    // wall-follow this many steps without escaping before we treat the obstacle as
    // a barrier worth digging through. small dirt patches get rounded cheaply in
    // fewer steps, so this keeps digging (5 cheese + 25 cooldown) a last resort.
    static final int DIG_AFTER = 10;

    static boolean moveTo(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return true;
        MapLocation cur = rc.getLocation();
        if (cur.equals(target)) return true;

        if (!target.equals(lastTarget)) {
            lastTarget = target;
            lineStart = cur;
            followingWall = false;
        }

        if (!followingWall) {
            Direction d = cur.directionTo(target);
            if (rc.canMove(d)) {
                stepFacing(rc, d);
                return true;
            }
            startFollowing(cur, target, d); // blocked: begin wall following here
        }

        // circumnavigated the obstacle back to the hit point, unreachable
        if (followSteps > 0 && cur.equals(hitLoc)) {
            followingWall = false;
            return false;
        }

        // leave wall following once back on the line and closer than the hit point
        if (onLine(lineStart, target, cur) && cur.distanceSquaredTo(target) < hitDistSq) {
            Direction d = cur.directionTo(target);
            if (rc.canMove(d)) {
                followingWall = false;
                stepFacing(rc, d);
                return true;
            }
            startFollowing(cur, target, d); // blocked again on the line: fresh hit
        }

        // stuck tracing a long barrier (e.g. cheesefarm's map-spanning dirt walls):
        // wall-following can't get around it, so tunnel straight through the dirt.
        if (followSteps > DIG_AFTER && tryDig(rc, cur, target)) {
            followingWall = false;
            followSteps = 0;
            return true;
        }

        // hug the wall to the right
        Direction candidate = bugDir.rotateRight();
        for (int i = 0; i < 8; i++) {
            if (rc.canMove(candidate)) {
                bugDir = candidate;
                followSteps++;
                stepFacing(rc, candidate);
                return true;
            }
            candidate = candidate.rotateLeft();
        }
        return true;
    }

    // dig the dirt tile one step toward the target. acting needs the tile in our
    // cone, so face it first (safe here: only called once we're confirmed stuck,
    // not every wall-follow step). returns true if we dug this turn.
    private static boolean tryDig(RobotController rc, MapLocation cur, MapLocation target) throws GameActionException {
        Direction d = cur.directionTo(target);
        if (rc.canMove(d)) return false; // not actually blocked toward the target
        if (rc.getDirection() != d) {
            if (rc.isTurningReady() && rc.canTurn(d)) rc.turn(d);
            else return false;
        }
        MapLocation ahead = cur.translate(d.dx, d.dy);
        if (!rc.canRemoveDirt(ahead)) return false; // not dirt, out of range, or no cheese
        rc.removeDirt(ahead);
        return true;
    }

    private static void startFollowing(MapLocation cur, MapLocation target, Direction d) {
        followingWall = true;
        hitDistSq = cur.distanceSquaredTo(target);
        hitLoc = cur;
        followSteps = 0;
        bugDir = d;
    }

    private static void stepFacing(RobotController rc, Direction d) throws GameActionException {
        if (rc.getDirection() != d && rc.isTurningReady() && rc.canTurn(d)) {
            rc.turn(d);
        }
        if (rc.getDirection() == d) {
            if (rc.canMoveForward()) rc.moveForward();
        } else if (rc.canMove(d)) {
            rc.move(d);
        }
    }

    // is p within 1 tile of the line?
    private static boolean onLine(MapLocation s, MapLocation g, MapLocation p) {
        long cross = (long) (g.x - s.x) * (p.y - s.y) - (long) (g.y - s.y) * (p.x - s.x);
        long lineLenSq = (long) (g.x - s.x) * (g.x - s.x) + (long) (g.y - s.y) * (g.y - s.y);
        return cross * cross <= lineLenSq;
    }
}
