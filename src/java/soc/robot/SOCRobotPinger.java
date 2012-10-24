/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2011 Jeremy D Monin <jeremy@nand.net>
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.message.SOCMessage;
import soc.message.SOCTimingPing;

import soc.util.CappedQueue;
import soc.util.CutoffExceededException;


/**
 * Pings the robots so that they can have a sense of time
 *
 * @author Robert S Thomas
 */
public class SOCRobotPinger extends Thread
{
    CappedQueue<SOCMessage> messageQueue;
    SOCTimingPing ping;
    boolean alive;
    String robotNickname;

    /**
     * Create a robot pinger
     *
     * @param q  the robot brain's message queue
     * @param nickname the robot's nickname, for debug thread naming
     */
    public SOCRobotPinger(CappedQueue<SOCMessage> q, String gameName, String nickname)
    {
        messageQueue = q;
        ping = new SOCTimingPing(gameName);
        alive = true;
        robotNickname = nickname;
    }

    /**
     * DOCUMENT ME!
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
        ping = null;
    }

    /**
     * DOCUMENT ME!
     */
    public void stopPinger()
    {
        alive = false;
    }
}
