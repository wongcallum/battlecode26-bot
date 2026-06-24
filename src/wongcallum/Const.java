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

    // MOBILE KING (the winning bots all walk their king onto cheese to self-feed):
    // react to a cat within this radius by laying cat traps in its path.
    static final int KING_CAT_RADIUS_SQUARED = 16;
    // in war, if at least this many enemy rats crowd the king within
    // KING_THREAT_RADIUS_SQUARED, drift away from them while biting (it can't outrun
    // rats, but it shouldn't walk into a swarm either).
    static final int KING_FLEE_ENEMY_COUNT = 4;
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
