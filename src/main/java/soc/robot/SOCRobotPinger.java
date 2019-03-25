/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2011,2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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
 *
 * The maintainer of this program can be reached at jsettlers@nand.net
 **/
package soc.robot;

import soc.message.SOCMessage;
import soc.message.SOCTimingPing;

import soc.util.CappedQueue;
import soc.util.CutoffExceededException;


/**
 * Pings a {@link SOCRobotBrain} to give a sense of time while its game is in progress.
 * Once per second, adds a {@link SOCTimingPing} into the brain's {@link CappedQueue}.
 *
 * @author Robert S Thomas
 */
/*package*/ class SOCRobotPinger extends Thread
{
    private CappedQueue<SOCMessage> messageQueue;
    private final SOCTimingPing ping;
    private volatile boolean alive;
    private final String robotNickname;

    /**
     * Create a robot pinger
     *
     * @param q  the robot brain's message queue
     * @param nickname the robot's nickname, for debug thread naming
     */
    public SOCRobotPinger(CappedQueue<SOCMessage> q, String gameName, String nickname)
    {
        setDaemon(true);

        messageQueue = q;
        ping = new SOCTimingPing(gameName);
        alive = true;
        robotNickname = nickname;
    }

    /**
     * Once per second queue a {@link SOCTimingPing}, until {@link #stopPinger()} is called.
     */
    @Override
    public void run()
    {
        // Thread name for debug
        try
        {
            Thread.currentThread().setName("robotPinger-" + robotNickname);
        }
        catch (Throwable th) {}

        while (alive)
        {
            try
            {
                messageQueue.put(ping);
            }
            catch (CutoffExceededException exc)
            {
                alive = false;
            }

            yield();

            try
            {
                sleep(1000);
            }
            catch (InterruptedException exc) {}
        }

        messageQueue = null;
    }

    /**
     * Stop the pinger thread's {@link #run()} loop by clearing its "alive" flag.
     */
    public void stopPinger()
    {
        alive = false;
    }

}
