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

    static RobotInfo nearestEnemy(MapLocation cur, RobotInfo[] enemies) {
        RobotInfo best = null;
        int bestD = Integer.MAX_VALUE;
        for (RobotInfo r : enemies) {
            int d = cur.distanceSquaredTo(r.getLocation());
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }

    // bite the lowest-health enemy in reach (finishing removes a biter fastest).
    // baby rats must face the target first (90 cone); the king is 360 so it cannot.
    // only call when !isCooperation so we never initiate the backstab ourselves.
    static boolean biteBest(RobotController rc, MapLocation cur, RobotInfo[] enemies, int range) throws GameActionException {
        if (!rc.isActionReady()) return false;
        RobotInfo target = null;
        int bestHp = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (cur.distanceSquaredTo(e.getLocation()) > range) continue;
            if (e.getHealth() < bestHp) {
                bestHp = e.getHealth();
                target = e;
            }
        }
        if (target == null) return false;
        MapLocation tloc = target.getLocation();
        if (rc.canAttack(tloc)) {
            rc.attack(tloc);
            return true;
        }
        Direction d = cur.directionTo(tloc);
        if (rc.isTurningReady() && rc.canTurn(d)) rc.turn(d);
        if (rc.canAttack(tloc)) {
            rc.attack(tloc);
            return true;
        }
        return false;
    }

    // the 16 tiles around the king's 3x3 body within build range (dist^2 <= 8),
    // i.e. the standing-minefield ring
    static final int[][] RING = {
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2},
        {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2},
    };

    // king fills a standing ring of hidden rat traps around itself (50 dmg + 30-turn
    // stun on trigger, invisible to the enemy). laid proactively while the tiles are
    // still free — the rush then walks into it. the core anti-rush tool, far better
    // value than 100-HP blockers that just die. returns true if it placed one.
    static boolean kingLayRatRing(RobotController rc, MapLocation cur) throws GameActionException {
        if (!rc.isActionReady() || rc.getNumberRatTraps() >= TrapType.RAT_TRAP.maxCount) return false;
        if (rc.getAllCheese() < TrapType.RAT_TRAP.buildCost) return false;
        for (int[] o : RING) {
            MapLocation t = cur.translate(o[0], o[1]);
            if (rc.canPlaceRatTrap(t)) {
                rc.placeRatTrap(t);
                return true;
            }
        }
        return false;
    }

    // baby rat answering the king's SOS: bite an adjacent enemy if the backstab is
    // already on, otherwise close on the king to wall its body tiles. always takes
    // the turn (return true) so the caller skips foraging.
    static boolean ratDefend(RobotController rc, MapLocation cur, MapLocation kingLoc, RobotInfo[] enemies, boolean coop) throws GameActionException {
        if (!coop && biteBest(rc, cur, enemies, GameConstants.ATTACK_DISTANCE_SQUARED)) {
            rc.setIndicatorString("DEFEND bite");
            return true;
        }
        Pathfinder.moveTo(rc, kingLoc);
        rc.setIndicatorString("DEFEND rally " + kingLoc);
        return true;
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
