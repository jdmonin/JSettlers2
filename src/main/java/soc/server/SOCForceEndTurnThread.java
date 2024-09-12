/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015,2019-2022 Jeremy D Monin <jeremy@nand.net>
 * This class was created in 2010 within SOCServer; reading SOCServer.java's
 * commit history led to this notice when the class was split out in 2013 to its own file:
 * Portions of this file Copyright (C) 2010-2013 Jeremy D Monin.
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
import soc.server.genericServer.Connection;

/**
 * Force this robot's turn to end, or force a resource pick, by calling
 * {@link GameHandler#endGameTurnOrForce(SOCGame, int, String, Connection, boolean)}.
 * Done in a separate thread in case of deadlocks; see {@link #run()} for more details.
 * Created from {@link SOCGameHandler#endTurnIfInactive(SOCGame, long)}
 * when that's called from {@link SOCGameTimeoutChecker#run()}.
 *<P>
 * Also calls {@link SOCPlayer#addForcedEndTurn()} to track "stubborn" slow/buggy robots.
 *<P>
 * Before 2.0.00, this class was SOCServer.SOCForceEndTurnThread;
 * split out in 2.0.00 to its own top-level class.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.11
 */
/*package*/ class SOCForceEndTurnThread extends Thread
{
    private final SOCServer srv;
    private final GameHandler hand;
    private final SOCGame ga;
    private final SOCPlayer pl;

    /**
     * Create a new {@link SOCForceEndTurnThread}; caller must start it.
     *
     * @param srv  Our server
     * @param hand  Our game handler
     * @param g  {@code p}'s game
     * @param p  Robot player in {@code g} to force: If current player, force-end their turn.
     *     If not current player but game is waiting for them to discard or pick free resources,
     *     choose randomly so the game can continue.
     */
    public SOCForceEndTurnThread(final SOCServer srv, final GameHandler hand, final SOCGame g, final SOCPlayer p)
    {
        super("forceEndTurn-" + g.getName());
        setDaemon(true);
        this.srv = srv;
        this.hand = hand;
        ga = g;
        pl = p;
    }

    /**
     * If our targeted robot player is still the current player, force-end their turn.
     * If not current player but game is waiting for them to discard or pick free resources,
     * choose randomly so the game can continue.
     * Calls {@link GameHandler#endGameTurnOrForce(SOCGame, int, String, Connection, boolean)}.
     */
    @Override
    public void run()
    {
        final String rname = pl.getName();
        final int plNum = pl.getPlayerNumber();
        final int gs = ga.getGameState();
        final boolean notCurrentPlayer = (ga.getCurrentPlayerNumber() != plNum);

        // Ignore if not current player, unless game is
        // waiting for the bot to discard or gain resources.
        if (notCurrentPlayer
             && (gs != SOCGame.WAITING_FOR_DISCARDS)
             && (gs != SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
             && (gs != SOCGame.STARTS_WAITING_FOR_PICK_GOLD_RESOURCE))
        {
            return;
        }

        Connection rconn = srv.getConnection(rname);
        final boolean isStubborn = pl.isStubbornRobot();
        System.err.println
            ("For robot " + rname
             + ((notCurrentPlayer) ? ": force discard/pick" : ": force end turn")
             + " in game " + ga.getName() + " pn=" + plNum + " state " + gs
             + (isStubborn ? " (stubborn)" : ""));
        if (gs == SOCGame.WAITING_FOR_DISCARDS)
            System.err.println("  srv resource count = " + pl.getResources().getTotal());
        else if (gs == SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE)
            System.err.println("  pl's gold pick count = " + pl.getNeedToPickGoldHexResources());

        pl.addForcedEndTurn();

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
                rcli.debugPrintBrainStatus(ga.getName(), ! isStubborn, false);
            else
                System.err.println("L9397: internal error: can't find robotClient for " + rname);
        } else {
            System.err.println("  Can't print brain status; robot type is " + scd.robot3rdPartyBrainClass);
        }

        hand.endGameTurnOrForce(ga, plNum, rname, rconn, false);
    }

}  // class SOCForceEndTurnThread
