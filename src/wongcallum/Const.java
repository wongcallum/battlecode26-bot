package wongcallum;

import battlecode.common.Direction;

import java.util.Random;

public class Const {
    static final Random rng = new Random(6147);

    // shared-array slots: the king broadcasts its location here for rats to home to
    static final int KING_X_SLOT = 0;
    static final int KING_Y_SLOT = 1;

    // react to a cat once it is within this distance (just past the cat's pounce
    // range of 13, inside a rat's vision of 20) so we can trap-and-peel in time
    static final int CAT_THREAT_RADIUS_SQUARED = 16;
    // don't spend global cheese on a trap below this buffer (protects king upkeep)
    static final int CAT_TRAP_RESERVE = 200;

    // the king senses attackers within this radius to bite them while it turtles
    static final int KING_THREAT_RADIUS_SQUARED = 16;

    // stop spawning once this many allied rats already crowd the king (within
    // PEACE_CROWD_RADIUS): a pileup means foragers aren't dispersing to find cheese,
    // so more bodies just drain the reserve. tuned to bite only on genuine pileups.
    static final int PEACE_CROWD_RADIUS_SQUARED = 16;
    static final int PEACE_POP_CAP = 14;
    // at war the trap ring defends the king alone, so foragers don't rally — they
    // flee an enemy within this radius (then keep foraging) to stay alive and keep
    // delivering income, instead of dying uselessly in the swarm at the king.
    static final int WAR_FLEE_RADIUS_SQUARED = 10;

    static final Direction[] DIRECTIONS = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
}
