package wongcallum;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/// 1. take a bite if it is favourable, always for a finishing blow, otherwise
///    only when we are not being swarmed
/// 2. otherwise fall back toward home/the king.
///
/// no engaging with cats, and no initiating conflict
public final class Micro {
    private Micro() {}

    private static final int BITE_RANGE_DSQ = GameConstants.ATTACK_DISTANCE_SQUARED; // 2
    private static final int CROWD_DSQ = 8;    // enemies this close count as "on us"
    private static final int OUTNUMBERED = 2;  // this many on us = don't trade (unless a kill)

    static boolean fight(RobotController rc, RobotInfo[] nearby, MapLocation retreatTo)
            throws GameActionException {
        MapLocation me = rc.getLocation();

        int crowd = 0;
        RobotInfo target = null; // lowest-hp enemy currently in bite range
        for (RobotInfo r : nearby) {
            if (r.getTeam() == rc.getTeam() || r.getType() == UnitType.CAT) continue;
            int d = me.distanceSquaredTo(r.getLocation());
            if (d <= CROWD_DSQ) crowd += 1;
            if (d <= BITE_RANGE_DSQ && rc.canAttack(r.getLocation())
                    && (target == null || r.getHealth() < target.getHealth())) {
                target = r;
            }
        }

        if (target == null && crowd == 0) return false; // no enemy rats nearby

        if (target != null) {
            boolean finisher = target.getHealth() <= GameConstants.RAT_BITE_DAMAGE;
            if (finisher || crowd < OUTNUMBERED) {
                rc.attack(target.getLocation());
                return true;
            }
        }

        if (retreatTo != null) {
            Pathfinder.moveTo(rc, retreatTo);
            return true;
        }
        return false;
    }
}
