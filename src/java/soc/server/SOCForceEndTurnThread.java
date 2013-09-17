/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>. Contents were
 * formerly part of SOCServer.java; portions of this file Copyright (C) 2010-2013 Jeremy D Monin.
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
package soc.server;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.robot.SOCRobotClient;
import soc.server.genericServer.StringConnection;

/**
 * Force-end this robot's turn.
 * Done in a separate thread in case of deadlocks.
 * Created from {@link SOCServer#checkForExpiredTurns(long)}
 * when that's called from {@link SOCGameTimeoutChecker#run()}.
 *<P>
 * Before 2.0.00, this class was SOCServer.SOCForceEndTurnThread;
 * split out in 2.0.00 to its own top-level class.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.11
 */
class SOCForceEndTurnThread extends Thread
{
    private final SOCServer srv;
    private final SOCGame ga;
    private final SOCPlayer pl;

    public SOCForceEndTurnThread(final SOCServer srv, final SOCGame g, final SOCPlayer p)
    {
        setDaemon(true);
        this.srv = srv;
        ga = g;
        pl = p;
    }

    /** If our targeted robot player is still the current player, force-end their turn. */
    @Override
    public void run()
    {
        final String rname = pl.getName();
        final int plNum = pl.getPlayerNumber();
        if (ga.getCurrentPlayerNumber() != plNum)
            return;

        StringConnection rconn = srv.getConnection(rname);
        System.err.println("For robot " + rname + ": force end turn in game " + ga.getName() + " cpn=" + plNum + " state " + ga.getGameState());
        if (ga.getGameState() == SOCGame.WAITING_FOR_DISCARDS)
            System.err.println("  srv card count = " + pl.getResources().getTotal());
        if (rconn == null)
        {
            System.err.println("L9120: internal error: can't find connection for bot " + rname);
            return;  // shouldn't happen
        }

        // if it's the built-in type, print brain variables
        SOCClientData scd = (SOCClientData) rconn.getAppData();
        if (scd.isBuiltInRobot)
        {
            SOCRobotClient rcli = SOCLocalRobotClient.robotClients.get(rname);
            if (rcli != null)
                rcli.debugPrintBrainStatus(ga.getName());
            else
                System.err.println("L9397: internal error: can't find robotClient for " + rname);
        } else {
            System.err.println("  Can't print brain status; robot type is " + scd.robot3rdPartyBrainClass);
        }

        srv.endGameTurnOrForce(ga, plNum, rname, rconn, false);
    }

}  // class SOCForceEndTurnThread
