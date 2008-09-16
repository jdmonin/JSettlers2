/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * This message sets the lock state of a seat.
 *
 * @author Robert S. Thomas
 */
public class SOCSetSeatLock extends SOCMessage
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
