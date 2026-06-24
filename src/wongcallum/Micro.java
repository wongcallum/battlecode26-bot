package wongcallum;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/// Defensive combat micro for baby rats. Conservative rules:
///   1. take a bite if it is favourable — always for a finishing blow, otherwise
///      only when we are not being swarmed (no 1-vs-many trades),
///   2. as a strictly home-defensive last resort once we are at war, "ratnap" an
///      enemy biter off the king and hurl it away (see ratnapDefend), and
///   3. otherwise fall back toward home/the king.
/// Cats are never engaged here (4000 hp, unkillable) — fleeing handles them.
/// The only offence here is reactive: ratnapping fires solely when an enemy is
/// already attacking our king (war state) — we still never open the backstab.
public final class Micro {
    private Micro() {}

    private static final int BITE_RANGE_DSQ = GameConstants.ATTACK_DISTANCE_SQUARED; // 2
    private static final int CROWD_DSQ = 8;    // enemies this close count as "on us"
    private static final int OUTNUMBERED = 2;  // this many on us = don't trade (unless a kill)

    /// Try a defensive combat action against the already-sensed nearby robots.
    /// Returns true if it acted (attacked or moved), false if there was nothing
    /// worth fighting and the caller should fall through to its own behaviour.
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

        // Can't bite favourably (out of reach, or swarmed) — pull back to home.
        if (retreatTo != null) {
            Pathfinder.moveTo(rc, retreatTo);
            return true;
        }
        return false;
    }

    /// Reactive, home-only "ratnap" defence. A carried rat is stunned and immune
    /// to attacks, so grabbing an enemy biter removes it from the swarm (up to 8
    /// adjacent biters = 80 dmg/round on the king) for as long as we hold it, and
    /// thrown it lands ~8 tiles away, dazed and far from the fight. We grab the
    /// highest-hp enemy we are allowed to carry (canCarryRat enforces the rules:
    /// lower hp than us, or facing away), then turn to face AWAY from our king and
    /// throw — never toward the king, since a thrown body damages whatever it hits,
    /// our own king included. Caller gates this on the war state, so it only ever
    /// fires once an enemy is already on our king (we never open the backstab).
    /// Returns true if the turn was spent carrying / turning / throwing / hauling.
    static boolean ratnapDefend(RobotController rc, RobotInfo[] nearby, MapLocation king)
            throws GameActionException {
        RobotInfo carried = rc.getCarrying();
        if (carried != null) {
            MapLocation me = rc.getLocation();
            if (carried.getTeam() == rc.getTeam()) { // never haul allies around; set down
                for (Direction d : Utils.directions) {
                    if (rc.canDropRat(d)) { rc.dropRat(d); return true; }
                }
                return true;
            }
            Direction away = (king != null) ? king.directionTo(me) : rc.getDirection();
            if (away == Direction.CENTER) away = rc.getDirection();
            if (rc.getDirection() == away && rc.canThrowRat()) { rc.throwRat(); return true; }
            if (rc.canTurn(away)) { rc.turn(away); return true; }
            if (rc.canThrowRat()) { rc.throwRat(); return true; } // any throw beats holding by the king
            if (rc.isMovementReady() && away != Direction.CENTER && rc.canMove(away)) {
                rc.move(away); // can't throw yet — carry the captive away meanwhile
            }
            return true; // carried enemy is neutralised regardless; don't fall through to a bite
        }

        RobotInfo best = null; // grab the biggest threat we are allowed to carry
        for (RobotInfo r : nearby) {
            if (r.getTeam() == rc.getTeam() || r.getType() != UnitType.BABY_RAT) continue;
            if (!rc.canCarryRat(r.getLocation())) continue;
            if (best == null || r.getHealth() > best.getHealth()) best = r;
        }
        if (best != null) {
            rc.carryRat(best.getLocation());
            return true;
        }
        return false;
    }
}
