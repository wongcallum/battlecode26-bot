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
