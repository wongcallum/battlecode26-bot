package wongcallum;

import battlecode.common.*;

public class RobotPlayer {
    static int turnCount = 0;

    @SuppressWarnings({"unused", "CallToPrintStackTrace"})
    public static void run(RobotController rc) throws GameActionException {
        //noinspection InfiniteLoopStatement
        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case BABY_RAT -> BabyRat.act(rc);
                    case RAT_KING -> RatKing.act(rc);
                    default -> {}
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
