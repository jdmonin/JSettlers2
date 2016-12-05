/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2013-2014,2016 Jeremy D Monin <jeremy@nand.net>
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
 * From a client, this message is a request to join any existing game
 * (having game options or not) or to create a game without any
 * {@link soc.game.SOCGameOption game options}.
 *<P>
 * Server responds to client's request with {@link SOCJoinGameAuth JOINGAMEAUTH}
 * and sends JOINGAME to all members of the game (players and observers)
 * including the requesting client, then further messages to the requesting client
 * (see below). The newly joined client may be an observer; if they are a player
 * {@link SOCSitDown SITDOWN} will be sent too.
 *<P>
 * To request a new game with game options, send
 * {@link SOCNewGameWithOptionsRequest NEWGAMEWITHOPTIONSREQUEST} instead.
 *<P>
 * If the join request is successful, requesting client is sent a specific sequence
 * of messages with details about the game; see {@link SOCGameMembers}.
 * In order for robot clients to be certain they have all details about a game
 * (board layout, player scores, piece counts, etc), they should take no action
 * before receiving {@link SOCGameMembers} about that game.
 *<P>
 * Once a client has successfully joined or created any game or channel, the
 * password field can be left blank in later join/create requests.  All server
 * versions ignore the password field after a successful request.
 *<P>
 * Although this is a game-specific message, it's about the game lifecycle
 * so it's handled by {@code SOCServer} instead of a {@code GameHandler}.
 *
 * @author Robert S Thomas
 * @see SOCJoinChannel
 */
public class SOCJoinGame extends SOCMessageTemplateJoinGame
{
    private static final long serialVersionUID = 1107L;  // last structural change v1.1.07

    /**
     * Create a Join Game message.
     *
     * @param nn  nickname
     * @param pw  optional password, or "" if none
     * @param hn  server host name to which client is connecting
     * @param ga  name of the game
     */
    public SOCJoinGame(String nn, String pw, String hn, String ga)
    {
        super(nn, pw, hn, ga);  // will set messagetype=JOINGAME
    }

    /**
     * JOINGAME sep nickname sep2 password sep2 host sep2 game
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(nickname, password, host, game);
    }

    /**
     * JOINGAME sep nickname sep2 password sep2 host sep2 game
     *
     * @param nn  the nickname
     * @param pw  the optional password, or "" if none
     * @param hn  the server host name to which client is connecting
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String nn, String pw, String hn, String ga)
    {
        String temppw = new String(pw);

        if (temppw.equals(""))
        {
            temppw = NULLPASS;
        }

        return JOINGAME + sep + nn + sep2 + temppw + sep2 + hn + sep2 + ga;
    }

    /**
     * Parse the command String into a JoinGame message
     *
     * @param s   the String to parse
     * @return    a JoinGame message, or null of the data is garbled
     */
    public static SOCJoinGame parseDataStr(String s)
    {
        String nn;
        String pw;
        String hn;
        String ga;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            nn = st.nextToken();
            pw = st.nextToken();
            hn = st.nextToken();
            ga = st.nextToken();

            if (pw.equals(NULLPASS))
            {
                pw = "";
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCJoinGame(nn, pw, hn, ga);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return super.toString("SOCJoinGame", null);
    }

}
