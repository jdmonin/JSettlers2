/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2012 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.StringTokenizer;


/**
 * This message means that someone is leaving a game.
 * Sent from leaving client to server (if leaving cleanly),
 * then sent from server out to all clients in game.
 *
 * @author Robert S Thomas
 */
public class SOCLeaveGame extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Nickname of the leaving member
     */
    private String nickname;

    /**
     * Name of game
     */
    private String game;

    /**
     * Host name of server hosting game, when sent from client.
     * Unused ("-") when sent from server.
     */
    private String host;

    /**
     * Create a LeaveGame message.
     *
     * @param nn  nickname
     * @param hn  host name, or "-" if sending from server to all players.
     *            (Length 0 would fail {@link #parseDataStr(String)} at the receiver)
     * @param ga  name of game
     */
    public SOCLeaveGame(String nn, String hn, String ga)
    {
        messageType = LEAVEGAME;
        nickname = nn;
        game = ga;
        host = hn;
    }

    /**
     * @return the nickname
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * Get the host name of the server hosting game, when sent from client.
     * Unused ("-") when sent from server.
     * @return the host name, or "-"
     */
    public String getHost()
    {
        return host;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * LEAVEGAME sep nickname sep2 host sep2 game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, host, game);
    }

    /**
     * LEAVEGAME sep nickname sep2 host sep2 game
     *
     * @param nn  the neckname
     * @param hn  the host name
     * @param ga  the name of the game
     * @return    the command string
     */
    public static String toCmd(String nn, String hn, String ga)
    {
        return LEAVEGAME + sep + nn + sep2 + hn + sep2 + ga;
    }

    /**
     * Parse the command String into a Leave message
     *
     * @param s   the String to parse
     * @return    a LeaveGame message, or null of the data is garbled
     */
    public static SOCLeaveGame parseDataStr(String s)
    {
        String nn; // nickname
        String hn; // host name
        String ga; // game name

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            hn = st.nextToken();
            ga = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLeaveGame(nn, hn, ga);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCLeaveGame:nickname=" + nickname + "|host=" + host + "|game=" + game;

        return s;
    }
}
