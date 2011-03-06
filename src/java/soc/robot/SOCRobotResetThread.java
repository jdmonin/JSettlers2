/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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


/**
 * When this thread wakes up it calls the disconnectReconnect
 * method of the SOCRobotClient that spawned it
 *
 * @author Robert S Thomas
 */
public class SOCRobotResetThread extends Thread
{
    SOCRobotClient client;
    boolean sleeping;
    boolean alive;

    /**
     * constructor
     *
     * @param cl   the robot client
     */
    public SOCRobotResetThread(SOCRobotClient cl)
    {
        client = cl;
        alive = true;
        sleeping = true;
    }

    /**
     * DOCUMENT ME!
     */
    public void run()
    {
        while (alive)
        {
            //
            //  start by sleeping so another
            //  thread has a chance to put it to sleep
            //
            while (sleeping)
            {
                sleeping = false;

                try
                {
                    sleep(300000);
                }
                catch (InterruptedException exc)
                {
                    ;
                }
            }

            client.disconnectReconnect();
            alive = false;
        }
    }

    /**
     * DOCUMENT ME!
     */
    public void sleepMore()
    {
        sleeping = true;
    }

    /**
     * DOCUMENT ME!
     */
    public void stopRobotResetThread()
    {
        alive = false;
    }
}
