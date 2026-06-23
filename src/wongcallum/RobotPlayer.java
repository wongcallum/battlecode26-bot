package wongcallum;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import java.util.Random;

@SuppressWarnings("unused")
public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public static void run(RobotController rc) {
        System.out.println("I don't want a lot for Christmas");

        while (true) {
            turnCount += 1;

            try {
                switch (rc.getType()) {
                    case BABY_RAT -> BabyRat.act(rc);
                    case RAT_KING -> RatKing.act(rc);
                    default       -> { }
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException @ round " + rc.getRoundNum());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception @ round " + rc.getRoundNum());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
