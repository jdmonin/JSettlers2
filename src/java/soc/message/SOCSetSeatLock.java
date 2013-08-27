/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013 Jeremy D Monin <jeremy@nand.net>
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
 * This message sets the lock state of a seat.
 *<P>
 * For player consistency, seat locks can't be changed
 * while {@link soc.game.SOCGame#getResetVoteActive()}
 * in server version 1.1.19 and higher.
 *
 * @author Robert S. Thomas
 */
public class SOCSetSeatLock extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The number player that is changing
     */
    private int playerNumber;

    /**
     * The state of the lock
     */
    private boolean state;

    /**
     * Create a SetSeatLock message.
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param st  the state of the lock
     */
    public SOCSetSeatLock(String ga, int pn, boolean st)
    {
        messageType = SETSEATLOCK;
        game = ga;
        playerNumber = pn;
        state = st;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of changing player
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the state of the lock
     */
    public boolean getLockState()
    {
        return state;
    }

    /**
     * SETSEATLOCK sep game sep2 playerNumber sep2 state
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, state);
    }

    /**
     * SETSEATLOCK sep game sep2 playerNumber sep2 state
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param st  the state of the lock
     * @return the command string
     */
    public static String toCmd(String ga, int pn, boolean st)
    {
        return SETSEATLOCK + sep + ga + sep2 + pn + sep2 + st;
    }

    /**
     * Parse the command String into a SetSeatLock message
     *
     * @param s   the String to parse
     * @return    a SetSeatLock message, or null of the data is garbled
     */
    public static SOCSetSeatLock parseDataStr(String s)
    {
        String ga; // the game name
        int pn; // the number of the changing player
        boolean ls; // the state of the lock

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            ls = (Boolean.valueOf(st.nextToken())).booleanValue();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSetSeatLock(ga, pn, ls);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCSetSeatLock:game=" + game + "|playerNumber=" + playerNumber + "|state=" + state;
    }
}
