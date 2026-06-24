package wongcallum;

import battlecode.common.*;

// Mine-sharing. Only KINGS can write the shared array, but foraging rats (not the
// near-blind king) are what actually find the mines — so rats SQUEAK the mines they
// remember, and any king within squeak range (dist^2 16) ingests them and publishes
// the team mine list to the shared array, which every unit reads. This is what makes
// the king mobile (it walks to a known mine to self-feed) and the rats efficient
// (they head to known mines instead of wandering at random).
public class Comms {
    static final int MINE_START = 2;   // shared-array slots 2 .. 2+2*MAX_MINES-1
    static final int MAX_MINES = 10;

    // a mine location packs into 12 bits (x,y <= 63 on a <=60-wide map)
    static int encodeMine(MapLocation m) {
        return ((m.x & 63) << 6) | (m.y & 63);
    }

    static MapLocation decodeMine(int bytes) {
        return new MapLocation((bytes >> 6) & 63, bytes & 63);
    }

    // king only: add a mine to the shared list if not already present (and there's room)
    static void publishMine(RobotController rc, MapLocation m) throws GameActionException {
        int firstEmpty = -1;
        for (int i = 0; i < MAX_MINES; i++) {
            int x = rc.readSharedArray(MINE_START + 2 * i);
            int y = rc.readSharedArray(MINE_START + 2 * i + 1);
            if (x == 0 && y == 0) {
                if (firstEmpty < 0) firstEmpty = i;
                continue;
            }
            if (new MapLocation(x - 1, y - 1).distanceSquaredTo(m) <= 8) return;
        }
        if (firstEmpty >= 0) {
            rc.writeSharedArray(MINE_START + 2 * firstEmpty, m.x + 1);
            rc.writeSharedArray(MINE_START + 2 * firstEmpty + 1, m.y + 1);
        }
    }

    // nearest known mine to cur with distance^2 strictly greater than minDistSq
    // (pass 0 for "any"; pass e.g. 16 to skip the depleted mine we're standing on)
    static MapLocation nearestKnownMine(RobotController rc, MapLocation cur, int minDistSq) throws GameActionException {
        MapLocation best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < MAX_MINES; i++) {
            int x = rc.readSharedArray(MINE_START + 2 * i);
            int y = rc.readSharedArray(MINE_START + 2 * i + 1);
            if (x == 0 && y == 0) continue;
            MapLocation m = new MapLocation(x - 1, y - 1);
            int d = cur.distanceSquaredTo(m);
            if (d > minDistSq && d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }
}
