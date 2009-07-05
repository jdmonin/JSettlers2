/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * This file copyright (C) 2008-2009 Jeremy D Monin <jeremy@nand.net>
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

import java.util.TimerTask;

import soc.server.genericServer.StringConnection;
import soc.util.SOCGameList;

/**
 * The server's place to track client-specific information across games.
 * The win-loss count is kept here.
 * Not tied to any database; information here is only for the current
 * session, not persistent across disconnects/reconnects by clients.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @since 1.1.04
 */
public class SOCClientData
{
    /** Number of games won and lost since client connected */
    private int wins, losses;

    /** Synchronization for win-loss count */
    private Object winLossSync;

    /**
     * Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    private boolean sentGameList;

    /**
     * TimerTask for connect-time client-version timer
     * @since 1.1.06
     */
    private SOCCDCliVersionTask cliVersionTask;

    public SOCClientData()
    {
        winLossSync = new Object();
        wins = 0;
        losses = 0;
        sentGameList = false;
        cliVersionTask = null;
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

    /**
     * Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    public boolean hasSentGameList()
    {
        return sentGameList;
    }

    /**
     * Set flag: Has the server's game list been sent to the client yet?
     * Please synchronize on {@link SOCGameList#takeMonitor()} / releaseMonitor.
     * @since 1.1.06
     */
    public void setSentGameList()
    {
        sentGameList = true;
    }

    /**
     * Set up the version timer.
     * It will fire after {@link SOCServer#CLI_VERSION_TIMER_FIRE_MS} milliseconds.
     * @param sr  Our SOCServer
     * @param con Connection for this timer / this clientdata
     * @since 1.1.06
     */
    public void setVersionTimer(SOCServer sr, StringConnection con)
    {
        cliVersionTask = new SOCCDCliVersionTask (sr, this, con);
        sr.utilTimer.schedule(cliVersionTask, SOCServer.CLI_VERSION_TIMER_FIRE_MS);
    }

    /**
     * Cancel the version timer, don't fire it.
     * @since 1.1.06
     */
    public void clearVersionTimer()
    {
        if (cliVersionTask != null)
        {
            cliVersionTask.cancel();
            cliVersionTask = null;
        }
    }


    /**
     * TimerTask at client connect, to guess the client version
     * if it isn't sent soon enough. (assume it's too old to tell us) 
     *<P>
     * When timer fires, assume client's version will not be sent.
     * Set it to {@link SOCServer#CLI_VERSION_ASSUMED_GUESS}.
     * (Don't set the version if cliConn.isVersionKnown() at that point.)
     * Ask server to send the list of games.
     * The version can be corrected later if necessary.
     * @since 1.1.06
     */
    private static class SOCCDCliVersionTask extends TimerTask
    {
        private SOCServer srv;
        private SOCClientData cliData;
        private StringConnection cliConn;

        public SOCCDCliVersionTask (SOCServer sr, SOCClientData cd, StringConnection con)
        {
            srv = sr;
            cliData = cd;
            cliConn = con;
        }

        /**
         * Called when timer fires. See class description for action taken.
         */
        public void run()
        {
            cliData.cliVersionTask = null;  // Clear reference to this soon-to-expire obj
            if (! cliConn.isVersionKnown())
            {
                srv.setClientVersSendGamesOrReject(cliConn, SOCServer.CLI_VERSION_ASSUMED_GUESS, false);
		// will also send game list.
		// if cli vers already known, it's already sent the list.
            }
        }

    }  // SOCCDCliVersionTask

}  // SOCClientData
