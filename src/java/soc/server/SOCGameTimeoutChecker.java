/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010,2015-2016 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package soc.server;


/**
 * Wakes up every few seconds to check for turns that have expired
 * by calling {@link SOCServer#checkForExpiredTurns(long)},
 * and every 5 minutes to check for games that have expired
 * with {@link SOCServer#checkForExpiredGames(long)}.
 *
 * @author Robert S Thomas
 */
public class SOCGameTimeoutChecker extends Thread
{
    private SOCServer server;
    private boolean alive;

    /**
     * Create a game timeout checker
     *
     * @param srv  the game server
     */
    public SOCGameTimeoutChecker(SOCServer srv)
    {
        server = srv;
        alive = true;
        setName ("timeoutChecker");  // Thread name for debug
        try { setDaemon(true); } catch (Exception e) {}  // Don't wait on us to exit program
    }

    /**
     * Wakes up every few seconds to check for turns that have expired,
     * and every 5 minutes to check for games that have expired.
     * See {@link SOCGameTimeoutChecker class javadoc}.
     */
    public void run()
    {
        // check every few seconds; should be about half of ROBOT_FORCE_ENDTURN_SECONDS
        final int sleepMillis = SOCServer.ROBOT_FORCE_ENDTURN_SECONDS * 600;

        // Holds time of next check for game expiry, not just turn expiry
        long gameExpireCheckTime = 0L;

        while (alive)
        {
            long now = System.currentTimeMillis();
            if (gameExpireCheckTime == 0L)
                gameExpireCheckTime = now;

            if (now >= gameExpireCheckTime)
            {
                server.checkForExpiredGames(now);

                // check every 5 minutes
                gameExpireCheckTime = now + (SOCServer.GAME_TIME_EXPIRE_CHECK_MINUTES * 60 * 1000);
                yield();
            }

            server.checkForExpiredTurns(now);
            yield();

            try
            {
                sleep(sleepMillis);
            }
            catch (InterruptedException exc) {}
        }

        server = null;
    }

    /**
     * DOCUMENT ME!
     */
    public void stopChecking()
    {
        alive = false;
    }
}
