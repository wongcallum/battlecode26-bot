package wongcallum;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public final class Comms {
    private Comms() {}

    static final int HEARTBEAT = 0;
    static final int KING_X = 1;
    static final int KING_Y = 2;
    static final int MINE_BASE = 3; // each map needs 2 slots, max 60x60 is more than 10 bits
    static final int MAX_MINES = 8; // 3 + 8*2 = 19

    static int knownMines = 0;

    static void reportKingState(RobotController rc) throws GameActionException {
        rc.writeSharedArray(HEARTBEAT, rc.getRoundNum() & 1023);
        MapLocation me = rc.getLocation();
        rc.writeSharedArray(KING_X, me.x + 1);
        rc.writeSharedArray(KING_Y, me.y + 1);
        publishMines(rc);
    }

    private static void publishMines(RobotController rc) throws GameActionException {
        if (knownMines >= MAX_MINES) return;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (knownMines >= MAX_MINES) break;
            if (!info.hasCheeseMine()) continue;
            MapLocation loc = info.getMapLocation();
            if (alreadyPublished(rc, loc)) continue;
            int slot = MINE_BASE + 2 * knownMines;
            rc.writeSharedArray(slot, loc.x + 1);
            rc.writeSharedArray(slot + 1, loc.y + 1);
            knownMines += 1;
        }
    }

    private static boolean alreadyPublished(RobotController rc, MapLocation loc) throws GameActionException {
        for (int i = 0; i < knownMines; i++) {
            int slot = MINE_BASE + 2 * i;
            if (rc.readSharedArray(slot) == loc.x + 1 && rc.readSharedArray(slot + 1) == loc.y + 1) return true;
        }
        return false;
    }

    static MapLocation readKingLocation(RobotController rc) throws GameActionException {
        int x = rc.readSharedArray(KING_X);
        int y = rc.readSharedArray(KING_Y);
        if (x == 0 || y == 0) return null;
        return new MapLocation(x - 1, y - 1);
    }

    static MapLocation readNearestKnownMine(RobotController rc, MapLocation from) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < MAX_MINES; i++) {
            int slot = MINE_BASE + 2 * i;
            int x = rc.readSharedArray(slot);
            int y = rc.readSharedArray(slot + 1);
            if (x == 0 || y == 0) continue;
            MapLocation m = new MapLocation(x - 1, y - 1);
            int d = from.distanceSquaredTo(m);
            if (d < bestDist) { bestDist = d; best = m; }
        }
        return best;
    }

    static boolean squeakSafe(RobotController rc, int content) throws GameActionException {
        for (RobotInfo r : rc.senseNearbyRobots(GameConstants.SQUEAK_RADIUS_SQUARED)) {
            if (r.getType() == UnitType.CAT) return false;
        }
        return rc.squeak(content);
    }
}
