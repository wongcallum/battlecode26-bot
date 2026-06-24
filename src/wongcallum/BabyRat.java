package wongcallum;

import battlecode.common.*;

public class BabyRat {
    // raw cheese carried before heading home. carrying cheese scales cooldown by
    // (1 + 0.01*raw), so ~40 keeps us under a 1.4x slowdown on the trip back.
    static final int RETURN_THRESHOLD = 40;

    static MapLocation home = null;        // last known allied rat king location
    static MapLocation exploreTarget = null;
    static int targetTurns = 0;
    static boolean seeded = false;

    static void act(RobotController rc) throws GameActionException {
        // every rat's Const.rng is seeded with the same constant, so without this
        // they all draw identical explore targets and march in lockstep. reseed
        // per-id so the swarm decorrelates and spreads across the map.
        if (!seeded) {
            Const.rng.setSeed(rc.getID() * 0x9E3779B9L);
            seeded = true;
        }

        MapLocation cur = rc.getLocation();

        // at war, don't rally to the king: the trap ring defends it alone, while a
        // rat that piles onto the besieged king just dies in the swarm (V5: our whole
        // army died at the king, leaving no income). instead survive — flee a near
        // enemy — and keep foraging, so the army lives to deliver cheese.
        if (!rc.isCooperation()) {
            RobotInfo foe = Combat.nearestEnemy(cur, rc.senseNearbyRobots(Const.WAR_FLEE_RADIUS_SQUARED, rc.getTeam().opponent()));
            if (foe != null) {
                Combat.fleeFrom(rc, cur, foe.getLocation());
                rc.setIndicatorString("WAR flee " + foe.getLocation());
                return;
            }
        }

        // cat defense comes next: trap-and-peel overrides foraging
        RobotInfo cat = Combat.nearestCat(cur, rc.senseNearbyRobots(Const.CAT_THREAT_RADIUS_SQUARED));
        if (cat != null) {
            Combat.ratHandleCat(rc, cur, cat);
            return;
        }

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
                exploreTarget = pickExploreTarget(rc, allies);
                targetTurns = 0;
            }
            targetTurns++;
            target = exploreTarget;
        }
        if (!Pathfinder.moveTo(rc, target) && target == exploreTarget) {
            exploreTarget = pickExploreTarget(rc, allies);
            targetTurns = 0;
        }
        rc.setIndicatorString("collect " + raw + " -> " + target);
    }

    // anti-clustering: sample a few random cells and keep the one that sits
    // farthest from our nearest visible ally, so rats push off each other and
    // cover more of the map (and more mines) instead of trailing one another
    private static MapLocation pickExploreTarget(RobotController rc, RobotInfo[] allies) {
        MapLocation best = randomLocation(rc);
        if (allies.length == 0) return best;
        int bestScore = minDistToAlly(best, allies);
        for (int i = 0; i < 3; i++) {
            MapLocation cand = randomLocation(rc);
            int s = minDistToAlly(cand, allies);
            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }
        return best;
    }

    private static int minDistToAlly(MapLocation loc, RobotInfo[] allies) {
        int best = Integer.MAX_VALUE;
        for (RobotInfo r : allies) {
            int d = loc.distanceSquaredTo(r.getLocation());
            if (d < best) best = d;
        }
        return best;
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
