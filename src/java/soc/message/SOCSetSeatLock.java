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

import soc.game.SOCGame.SeatLockState;


/**
 * This message sets the lock state of a seat.
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
    private SeatLockState state;

    /**
     * Create a SetSeatLock message.
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param st  the state of the lock; remember that versions before v2.0.00 won't recognize
     *    {@link SeatLockState#CLEAR_ON_RESET}.
     */
    public SOCSetSeatLock(String ga, int pn, SeatLockState st)
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
    public SeatLockState getLockState()
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
     * @return the command string.  For backwards compatibility,
     *     seatLockState will be "true" for LOCKED, "false" for UNLOCKED, or "clear" for CLEAR_ON_RESET.
     *     Versions before v2.0.00 won't recognize {@code "clear"}.
     */
    public static String toCmd(String ga, int pn, SeatLockState st)
    {
        return SETSEATLOCK + sep + ga + sep2 + pn + sep2 +
            ((st == SeatLockState.LOCKED) ? "true"
             : (st == SeatLockState.UNLOCKED) ? "false"
             : "clear");   // st == SeatLockState.CLEAR_ON_RESET
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
        final SeatLockState ls; // the state of the lock

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            final String lockst = st.nextToken();
            if (lockst.equals("true"))
                ls = SeatLockState.LOCKED;
            else if (lockst.equals("false"))
                ls = SeatLockState.UNLOCKED;
            else if (lockst.equals("clear"))
                ls = SeatLockState.CLEAR_ON_RESET;
            else
                return null;
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
