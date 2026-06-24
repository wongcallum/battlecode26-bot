package wongcallum;

import battlecode.common.*;

public class RatKing {
    // a stationary king starves; a king that roams gets ganked. so: while peace
    // holds the king forages cheese it can see WITHIN a short leash of its spawn
    // (self-feeding without exposing the instant-loss unit), and spawns an army. the
    // instant a war ever starts it locks into the proven V6 turtle — a standing
    // hidden rat-trap ring the enemy dies walking into — and never moves again.
    static final int SPAWN_FLOOR = 400;
    static final int FORAGE_LEASH_SQUARED = 2500; // effectively the whole map (war-lock keeps it safe)

    static final int CAMP_RADIUS_SQUARED = 16; // settle within ~4 tiles of the target mine

    static MapLocation spawnLoc = null;
    static MapLocation campMine = null; // the mine we've committed to camping
    static boolean warStarted = false;
    static boolean seeded = false;

    static void act(RobotController rc) throws GameActionException {
        if (!seeded) {
            Const.rng.setSeed(rc.getID() * 0x9E3779B9L);
            seeded = true;
        }
        MapLocation cur = rc.getLocation();
        if (spawnLoc == null) spawnLoc = cur;
        broadcastLocation(rc, cur);

        MapInfo[] infos = rc.senseNearbyMapInfos();
        RobotInfo[] all = rc.senseNearbyRobots();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (!rc.isCooperation()) warStarted = true;

        // a mobile king feeds itself: grab every cheese tile in reach into global
        for (MapInfo mi : infos) {
            if (mi.getCheeseAmount() > 0 && rc.canPickUpCheese(mi.getMapLocation())) {
                rc.pickUpCheese(mi.getMapLocation());
            }
        }

        // publish mines: those in our own vision, plus everything nearby rats squeak
        for (MapInfo mi : infos) {
            if (mi.hasCheeseMine()) Comms.publishMine(rc, mi.getMapLocation());
        }
        Message[] squeaks = rc.readSqueaks(-1);
        for (int i = 0; i < squeaks.length && i < 16; i++) {
            MapLocation m = Comms.decodeMine(squeaks[i].getBytes());
            if (rc.onTheMap(m)) Comms.publishMine(rc, m);
        }

        // TEMP: trace king position + nearest known mine (remove before commit)
        if (rc.getRoundNum() % 50 == 0) {
            System.out.println("TRACE r=" + rc.getRoundNum() + " gc=" + rc.getGlobalCheese()
                + " coop=" + rc.isCooperation() + " @" + cur
                + " nearestMine=" + Comms.nearestKnownMine(rc, cur, 0));
        }

        RobotInfo cat = Combat.nearestCat(cur, all);
        boolean catNear = cat != null && cur.distanceSquaredTo(cat.getLocation()) <= Const.KING_CAT_RADIUS_SQUARED;

        // ACTION (one per turn): cat traps > turtle (ring then bite, once at war) >
        // grow the army (peace only — spawning into a rush just over-drains the chest).
        boolean acted = false;
        if (catNear) {
            acted = Combat.kingHandleCat(rc, cur, cat);
        }
        if (!acted && warStarted) {
            acted = Combat.kingLayRatRing(rc, cur);
            if (!acted && enemies.length > 0) {
                acted = Combat.biteBest(rc, cur, enemies, GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED);
            }
        }
        if (!acted && !warStarted && rc.getGlobalCheese() > SPAWN_FLOOR) {
            for (Direction d : Const.DIRECTIONS) {
                MapLocation loc = cur.translate(2 * d.dx, 2 * d.dy);
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    break;
                }
            }
        }

        // MOVEMENT (king moves every 4 rounds): once war ever starts, hold the ring
        // forever. in peace, hop onto nearby cheese but stay on the leash near spawn.
        if (warStarted || !rc.isMovementReady()) {
            rc.setIndicatorString(warStarted ? "KING turtle" : "KING (no move)");
            return;
        }
        // commit to the nearest known mine and camp it (so it doesn't oscillate
        // between equidistant mines): walk there, then sit in the cluster of cheese
        // and rat traffic. once camped, stay put.
        if (campMine == null) {
            MapLocation m = Comms.nearestKnownMine(rc, cur, 0);
            if (m != null && spawnLoc.distanceSquaredTo(m) <= FORAGE_LEASH_SQUARED) campMine = m;
        }
        if (campMine != null && cur.distanceSquaredTo(campMine) > CAMP_RADIUS_SQUARED) {
            Pathfinder.moveTo(rc, campMine);
            rc.setIndicatorString("KING -> mine " + campMine);
            return;
        }
        rc.setIndicatorString("KING camp " + campMine);
    }

    private static void broadcastLocation(RobotController rc, MapLocation me) throws GameActionException {
        rc.writeSharedArray(Const.KING_X_SLOT, me.x + 1);
        rc.writeSharedArray(Const.KING_Y_SLOT, me.y + 1);
    }
}
