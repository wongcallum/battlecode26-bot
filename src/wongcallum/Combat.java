package wongcallum;

import battlecode.common.*;

// Cat-damage pillar: cats roam the map and hunt rats. A cat trap is 10 cheese for
// 100 damage + a 20-turn stun, placeable freely in cooperation, capped at 10
// active and consumed on trigger. Cat damage is half the cooperation score, so we
// bait cats across freshly-placed traps: it banks score and saves the rat.
public class Combat {
    // nearest cat in the sensed set (callers pass an already range-limited sense)
    static RobotInfo nearestCat(MapLocation cur, RobotInfo[] robots) {
        RobotInfo best = null;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo r : robots) {
            if (r.getType() == UnitType.CAT) {
                int d = cur.distanceSquaredTo(r.getLocation());
                if (d < bestD) {
                    bestD = d;
                    best = r;
                }
            }
        }
        return best;
    }

    // a carrying rat pays from its own raw stash (cheese it would have delivered);
    // otherwise it dips into global, which we only allow above the upkeep buffer
    static boolean canAffordTrap(RobotController rc) {
        if (rc.getNumberCatTraps() >= TrapType.CAT_TRAP.maxCount) return false;
        return rc.getRawCheese() >= TrapType.CAT_TRAP.buildCost
                || rc.getGlobalCheese() >= Const.CAT_TRAP_RESERVE;
    }

    // baby rat: face the cat (so the trap tile sits in our 90 cone), drop a trap in
    // its path, then peel away. baby rats move at cooldown 10 vs the cat's 20, so a
    // retreat gains ground while the trap does the work. returns true (turn handled).
    static boolean ratHandleCat(RobotController rc, MapLocation cur, RobotInfo cat) throws GameActionException {
        MapLocation catLoc = cat.getLocation();
        Direction toCat = cur.directionTo(catLoc);
        if (rc.getDirection() != toCat && rc.isTurningReady() && rc.canTurn(toCat)) {
            rc.turn(toCat);
        }
        if (canAffordTrap(rc)) {
            MapLocation trapTile = cur.add(toCat);
            if (rc.canPlaceCatTrap(trapTile)) {
                rc.placeCatTrap(trapTile);
            }
        }
        fleeFrom(rc, cur, catLoc);
        rc.setIndicatorString("CAT flee " + catLoc);
        return true;
    }

    // greedy step that maximises distance from the threat over passable directions
    static void fleeFrom(RobotController rc, MapLocation cur, MapLocation threat) throws GameActionException {
        if (!rc.isMovementReady()) return;
        Direction best = null;
        int bestD = cur.distanceSquaredTo(threat);
        for (Direction d : Const.DIRECTIONS) {
            if (!rc.canMove(d)) continue;
            int dd = cur.add(d).distanceSquaredTo(threat);
            if (dd > bestD) {
                bestD = dd;
                best = d;
            }
        }
        if (best == null) return;
        if (rc.getDirection() == best) {
            if (rc.canMoveForward()) rc.moveForward();
        } else {
            rc.move(best); // strafe: we are facing the cat, not our escape route
        }
    }

    // king: 360 vision means no facing constraint, so ring tiles toward the cat
    // (within build range 8) with traps. returns true if it placed one this turn.
    static boolean kingHandleCat(RobotController rc, MapLocation cur, RobotInfo cat) throws GameActionException {
        if (!rc.isActionReady() || !canAffordTrap(rc)) return false;
        Direction toCat = cur.directionTo(cat.getLocation());
        MapLocation[] cands = {
            cur.add(toCat).add(toCat),
            cur.add(toCat),
            cur.add(toCat).add(toCat.rotateLeft()),
            cur.add(toCat).add(toCat.rotateRight()),
        };
        for (MapLocation t : cands) {
            if (rc.canPlaceCatTrap(t)) {
                rc.placeCatTrap(t);
                rc.setIndicatorString("CAT trap " + t);
                return true;
            }
        }
        return false;
    }
}
