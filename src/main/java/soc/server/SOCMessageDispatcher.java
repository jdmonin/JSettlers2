/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2016-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.debug.D;
import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCSitDown;
import soc.server.genericServer.Connection;
import soc.server.genericServer.Server;

/**
 * Server class to dispatch all inbound messages.
 * Sole exception: The first message from a client is dispatched by
 * {@link SOCServer#processFirstCommand(SOCMessage, Connection)} instead.
 *<P>
 * Once server is initialized, call {@link #setServer(SOCServer, SOCGameListAtServer)}
 * before calling {@link #dispatch(SOCMessage, Connection)}.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCMessageDispatcher
    implements Server.InboundMessageDispatcher
{
    /**
     * Our SOCServer. {@code srv}, {@link #srvHandler}, and {@link #gameList} are all
     * set non-null by {@link #setServer(SOCServer, SOCServerMessageHandler, SOCGameListAtServer)}.
     */
    private SOCServer srv;

    /**
     * Our SOCServer's inbound message handler.
     * {@link srv}, {@link #gameList}, and {@code #srvHandler} are all set non-null by
     * {@link #setServer(SOCServer, SOCServerMessageHandler, SOCGameListAtServer)}.
     */
    private SOCServerMessageHandler srvHandler;

    /**
     * Our game list, with game type handler info.
     * {@code gameList}, {@link #srv}, and {@link #srvHandler} are all set non-null by
     * {@link #setServer(SOCServer, SOCServerMessageHandler, SOCGameListAtServer)}.
     */
    private SOCGameListAtServer gameList;

    /**
     * Create a new SOCMessageDispatcher. Takes no parameters because the
     * server and dispatcher constructors can't both call each other.
     * Be sure to call {@link #setServer(SOCServer, SOCGameListAtServer)}
     * before dispatching.
     */
    public SOCMessageDispatcher()
    {
    }

    /**
     * Set our SOCServer and game list references, necessary before
     * {@link #dispatch(SOCMessage, Connection)} can be called.
     *
     * @param srv  This dispatcher's server
     * @param srvHandler  Server message handler for {@code srv}
     * @param gameList  Game list for {@code srv}
     * @throws IllegalArgumentException  If {@code srv}, {@code srvHandler}, or {@link gameList} are null
     * @throws IllegalStateException  If {@code setServer(..)} has already been called
     */
    public void setServer
        (final SOCServer srv, final SOCServerMessageHandler srvHandler, final SOCGameListAtServer gameList)
        throws IllegalArgumentException, IllegalStateException
    {
        if ((srv == null) || (srvHandler == null) || (gameList == null))
            throw new IllegalArgumentException("null");
        if (this.srv != null)
            throw new IllegalStateException();

        this.srv = srv;
        this.srvHandler = srvHandler;
        this.gameList = gameList;
    }

    /**
     * {@inheritDoc}
     * @throws IllegalStateException if not ready to dispatch because
     *    {@link #setServer(SOCServer, SOCGameListAtServer)} hasn't been called.
     */
    public void dispatch(final SOCMessage mes, final Connection con)
        throws IllegalStateException
    {
        if (srv == null)
            throw new IllegalStateException("Not ready to dispatch: call setServer first");
        if (mes == null)
            return;

        try
        {
            // D.ebugPrintln(c.getData()+" - "+mes);

            if (mes instanceof SOCMessageForGame)
            {
                // Try to process message through its game type's handler
                // before falling through to server-wide handler

                final String gaName = ((SOCMessageForGame) mes).getGame();
                if (gaName == null)
                    return;  // <--- Early return: malformed ---

                if (! gaName.equals(SOCMessage.GAME_NONE))
                {
                    SOCGame ga = gameList.getGameData(gaName);
                    if ((ga == null) || (con == null))
                    {
                        if (! (mes instanceof SOCSitDown))
                            return;  // <--- Early return: ignore unknown games or unlikely missing con ---

                        // For SOCSitDown, SOCServerMessageHandler will reply to con
                    } else {
                        final GameMessageHandler hand = gameList.getGameTypeMessageHandler(gaName);
                        if (hand != null)  // all consistent games will have a handler
                        {
                            if (hand.dispatch(ga, (SOCMessageForGame) mes, con))
                                return;  // <--- Was handled by GameMessageHandler ---

                            // else: Message type unknown or ignored by handler. Server handles it below.
                        }
                    }
                }
            }

            srvHandler.dispatch(mes, con);
        }
        catch (Throwable e)
        {
            D.ebugPrintStackTrace(e, "ERROR -> dispatch");
        }
    }
}
