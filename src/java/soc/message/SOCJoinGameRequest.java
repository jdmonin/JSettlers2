/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D. Monin <jeremy@nand.net>
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

import java.util.Hashtable;
import java.util.StringTokenizer;

import soc.game.SOCGameOption;


/**
 * This message means that the server is asking
 * this robot client to join a game.
 *<P>
 * In 1.1.07, added optional parameter: game options.
 * Because this is sent only to robots, and robots' versions always
 * match the server version, we don't need to worry about backwards
 * compatability.
 *
 * @author Robert S Thomas
 * @see SOCJoinGameAuth
 */
public class SOCJoinGameRequest extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Where the robot should sit
     */
    private int playerNumber;

    /**
     * Game options, or null
     * @since 1.1.07
     */
    private Hashtable opts = null;

    /**
     * Create a JoinGameRequest message.
     *
     * @param ga  name of game
     * @param pn  the seat number
     * @param opts {@link SOCGameOption game options}, or null
     */
    public SOCJoinGameRequest(String ga, int pn, Hashtable opts)
    {
        messageType = JOINGAMEREQUEST;
        game = ga;
        playerNumber = pn;
	this.opts = opts;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player seat number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return game options, or null
     * @since 1.1.07
     */
    public Hashtable getOptions()
    {
	return opts;
    }

    /**
     * JOINGAMEREQUEST sep game sep2 playerNumber
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, opts);
    }

    /**
     * JOINGAMEREQUEST sep game sep2 playerNumber sep2 optionstring
     *
     * @param ga  the game name
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, Hashtable opts)
    {
        return JOINGAMEREQUEST + sep + ga + sep2 + pn + sep2 
            + SOCGameOption.packOptionsToString(opts);
    }

    /**
     * Parse the command String into a JoinGameRequest message
     *
     * @param s   the String to parse
     * @return    a JoinGameRequest message, or null of the data is garbled
     */
    public static SOCJoinGameRequest parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the seat number
	String optstr;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
	    optstr = st.nextToken(sep);  // NOT sep2: options may contain ","
        }
        catch (Exception e)
        {
            return null;
        }

	Hashtable opts = SOCGameOption.parseOptionsToHash(optstr);
        return new SOCJoinGameRequest(ga, pn, opts);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCJoinGameRequest:game=" + game + "|playerNumber=" + playerNumber;

        return s;
    }
}
