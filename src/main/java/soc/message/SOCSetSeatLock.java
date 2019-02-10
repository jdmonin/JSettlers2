/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2013-2014,2017-2018 Jeremy D Monin <jeremy@nand.net>
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

import java.util.InputMismatchException;
import java.util.StringTokenizer;

import soc.game.SOCGame.SeatLockState;
import soc.proto.Message;


/**
 * This message sets the lock state of a seat.
 * See {@link SeatLockState} for state details.
 *<P>
 * In version 2.0.00 and newer ({@link #VERSION_FOR_ALL_SEATS}),
 * this message can send to client the lock states of all seats at once
 * (4 or 6: game's max player count).
 *<P>
 * For player consistency, seat locks can't be
 * changed while {@link soc.game.SOCGame#getResetVoteActive()}
 * in server version 1.1.19 and higher.
 *<P>
 * Although this is a game-specific message, it's handled by {@code SOCServer} instead of a {@code GameHandler}.
 *
 * @author Robert S. Thomas
 */
public class SOCSetSeatLock extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Minimum client version (2.0.00) where this message can send to client
     * the lock states of all seats at once (4 or 6: game's max player count).
     * @since 2.0.00
     */
    public static final int VERSION_FOR_ALL_SEATS = 2000;

    /**
     * Name of game
     */
    private final String game;

    /**
     * Change lock state of this seat number.
     * Not used (-1) if multiple seats ({@link #states} != null).
     */
    private final int playerNumber;

    /**
     * The state of the lock. Before v2.0.00, this was boolean
     * (LOCKED, UNLOCKED, did not have {@link SeatLockState#CLEAR_ON_RESET}).
     * Not used if multiple seats ({@link #states} != null).
     */
    private SeatLockState state;

    /**
     * When message contains all seats, the lock state of each seat.
     * Length is always 4 or 6: game's max player count.
     * @see #state
     * @since 2.0.00
     */
    private final SeatLockState[] states;

    /**
     * Create a SetSeatLock message for one seat.
     *
     * @param ga  the name of the game
     * @param pn  the number of the changing player
     * @param st  the state of the lock; remember that versions before v2.0.00 won't recognize
     *    {@link SeatLockState#CLEAR_ON_RESET}.
     * @see #SOCSetSeatLock(String, SeatLockState[])
     */
    public SOCSetSeatLock(String ga, int pn, SeatLockState st)
    {
        messageType = SETSEATLOCK;
        game = ga;
        playerNumber = pn;
        state = st;
        states = null;
    }

    /**
     * Create a SetSeatLock message for all seats.
     * This message form is recognized only by client v2.0.00 and newer ({@link #VERSION_FOR_ALL_SEATS}).
     *
     * @param ga  the name of the game
     * @param st  the state of each seat's lock, indexed by player number
     * @throws IllegalArgumentException if {@code st.length} is not 4 or 6
     * @see #SOCSetSeatLock(String, int, SeatLockState)
     * @since 2.0.00
     */
    public SOCSetSeatLock(final String ga, final SeatLockState[] st)
        throws IllegalArgumentException
    {
        if ((st.length != 4) && (st.length != 6))
            throw new IllegalArgumentException("length");

        messageType = SETSEATLOCK;
        game = ga;
        playerNumber = -1;
        states = st;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the number of changing player, or -1 if message contains all seats
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * Get the single seat's lock state. Not used if message contains all seats
     * ({@link #getPlayerNumber()} == -1); if so, use {@link #getLockStates()} instead.
     * @return the state of the lock
     */
    public SeatLockState getLockState()
    {
        return state;
    }

    /**
     * Get all seats' lock state. Not used if message contains just one player's seat
     * ({@link #getPlayerNumber()} != -1); if so, use {@link #getLockState()} instead.
     * @return the state of each seat's lock, indexed by player number,
     *     or {@code null} if sent for one player's seat
     */
    public SeatLockState[] getLockStates()
    {
        return states;
    }

    /**
     * SETSEATLOCK sep game sep2 playerNumber sep2 state
     *<BR>
     * or
     *<BR>
     * SETSEATLOCK sep game sep2 state sep2 state sep2 state sep2 state [sep2 state sep2 state]
     *
     * @return the command string
     */
    public String toCmd()
    {
        if (states == null)
        {
            return toCmd(game, playerNumber, state);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(SETSEATLOCK + sep + game);
            for (int pn = 0; pn < states.length; ++pn)
            {
                sb.append(sep2_char);
                final SeatLockState st = states[pn];
                sb.append
                    ((st == SeatLockState.LOCKED) ? "true"
                     : (st == SeatLockState.UNLOCKED) ? "false"
                       : "clear");   // st == SeatLockState.CLEAR_ON_RESET

            }

            return sb.toString();
        }
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
    public static String toCmd(final String ga, final int pn, final SeatLockState st)
    {
        return SETSEATLOCK + sep + ga + sep2 + pn + sep2 +
            ((st == SeatLockState.LOCKED) ? "true"
             : (st == SeatLockState.UNLOCKED) ? "false"
               : "clear");   // st == SeatLockState.CLEAR_ON_RESET
    }

    /**
     * Parse the command String into a SetSeatLock message
     * for one or all seats in the game.
     *
     * @param s   the String to parse
     * @return    a SetSeatLock message, or null if the data is garbled
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
            final String tok = st.nextToken();
            if (Character.isDigit(tok.charAt(0)))
            {
                pn = Integer.parseInt(tok);
                ls = parseLockState(st.nextToken());

                return new SOCSetSeatLock(ga, pn, ls);
            } else {
                final int np = 1 + st.countTokens();
                if ((np != 4) && (np != 6))
                    return null;
                final SeatLockState[] sls = new SeatLockState[np];
                sls[0] = parseLockState(tok);
                for (pn = 1; pn < np; ++pn)
                    sls[pn] = parseLockState(st.nextToken());

                return new SOCSetSeatLock(ga, sls);
            }
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Parse and return a seat lock state, which should be one of three strings
     * {@code "true"}, {@code "false"}, or {@code "clear"}; these are parsed to
     * {@link SeatLockState#LOCKED}, {@link SeatLockState#UNLOCKED}, or {@link SeatLockState#CLEAR_ON_RESET}
     * respectively.
     * @param lockst  Lock state string to parse
     * @return the parsed SeatLockState
     * @throws InputMismatchException if the next token isn't {@code "true"}, {@code "false"}, or {@code "clear"}
     * @since 2.0.00
     */
    private static SeatLockState parseLockState(final String lockst)
        throws InputMismatchException
    {
        final SeatLockState ls;

        if (lockst.equals("true"))
            ls = SeatLockState.LOCKED;
        else if (lockst.equals("false"))
            ls = SeatLockState.UNLOCKED;
        else if (lockst.equals("clear"))
            ls = SeatLockState.CLEAR_ON_RESET;
        else
            throw new InputMismatchException("lockstate: " + lockst);

        return ls;
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        Message.SetSeatLock.Builder b = Message.SetSeatLock.newBuilder();
        b.setGaName(game).setSeatNumber(playerNumber)
            .setState(ProtoMessageBuildHelper.toMsgSeatLockState(state));
        return Message.FromServer.newBuilder().setSetSeatLock(b).build();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCSetSeatLock:game=" + game);
        if (states == null)
        {
            sb.append("|playerNumber=");
            sb.append(playerNumber);
            sb.append("|state=");
            sb.append(state);
        } else {
            sb.append("|states=");
            for (int pn = 0; pn < states.length; ++pn)
            {
                if (pn > 0)
                    sb.append(',');
                sb.append(states[pn]);
            }
        }

        return sb.toString();
    }

}
