/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2008 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 **/
package soc.server;

/**
 * The server's place to track client-specific information across games.
 * The win-loss count is kept here.
 * Not tied to any database; information here is only for the current
 * session, not persistent across disconnects/reconnects by clients.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 */
public class SOCClientData
{
    /** Number of games won and lost since client connected */
    private int wins, losses;

    /** Synchronization for win-loss count */
    private Object winLossSync;

    public SOCClientData()
    {
        winLossSync = new Object();
        wins = 0;
        losses = 0;
    }

    /**
     * Client has won a game; update win-loss count.
     * Thread-safe; synchronizes on an internal object.
     */
    public void wonGame()
    {
        synchronized (winLossSync)
        {
            ++wins;
        }
    }

    /**
     * Client has lost a game; update win-loss count.
     * Thread-safe; synchronizes on an internal object.
     */
    public void lostGame()
    {
        synchronized (winLossSync)
        {
            ++losses;
        }
    }

    /**
     * @return Number of games won by this client in this session
     */
    public int getWins()
    {
        return wins;
    }

    /**
     * @return Number of games lost by this client in this session
     */
    public int getLosses()
    {
        return losses;
    }

}
