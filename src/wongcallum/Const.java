package wongcallum;

import battlecode.common.Direction;

import java.util.Random;

public class Const {
    static final Random rng = new Random(6147);

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
