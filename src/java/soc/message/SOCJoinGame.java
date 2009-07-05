/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message means that someone is joining a game, or asking to create a game
 * (with no {@link soc.game.SOCGameOption game options}).
 * Server responds to client's request with {@link SOCJoinGameAuth JOINGAMEAUTH}
 * and sends JOINGAME to all players/observers of the game (including client).
 *<P>
 * To request a new game with game options, send {@link SOCNewGameWithOptionsRequest NEWGAMEWITHOPTIONSREQUEST} instead.
 *
 * @author Robert S Thomas
 */
public class SOCJoinGame extends SOCMessageTemplateJoinGame
{
    /**
     * Create a Join message.
     *
     * @param nn  nickname
     * @param pw  password
     * @param hn  host name
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
     * @param pw  the password
     * @param hn  the host name
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
        return super.toString("SOCJoinGame");
    }
}
