package wongcallum;

import battlecode.common.Direction;

import java.util.Random;

public class Const {
    static final Random rng = new Random(6147);

    // shared-array slots: the king broadcasts its location here for rats to home to
    static final int KING_X_SLOT = 0;
    static final int KING_Y_SLOT = 1;
    // king raises this to 1 when an enemy is near it, so nearby rats rally to defend
    static final int SOS_SLOT = 2;

    // react to a cat once it is within this distance (just past the cat's pounce
    // range of 13, inside a rat's vision of 20) so we can trap-and-peel in time
    static final int CAT_THREAT_RADIUS_SQUARED = 16;
    // don't spend global cheese on a trap below this buffer (protects king upkeep)
    static final int CAT_TRAP_RESERVE = 200;

    // anti-rush: the king flags a threat when an enemy is this close; rats within
    // DEFEND_RADIUS of the king's broadcast location answer the SOS and body-block
    static final int KING_THREAT_RADIUS_SQUARED = 16;
    static final int DEFEND_RADIUS_SQUARED = 64;
    // at war (coop has ended), hold this big a war-chest: spawn only above it, so we
    // keep cheese for the standing trap ring + re-laying triggered traps + upkeep
    static final int WAR_RESERVE = 1200;

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
