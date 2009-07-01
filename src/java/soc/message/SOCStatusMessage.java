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


/**
 * This is a text message that shows in a status box on the client.
 * Used for "welcome" message at initial connect to game (follows
 * {@link SOCJoinAuth JOINAUTH} or {@link SOCJoinGameAuth JOINGAMEAUTH}),
 * or rejection if client can't join that game (or channel).
 * Also used in {@link soc.client.SOCAccountClient SOCAccountClient}
 * to tell the user if their change was made successfully.
 *<P>
 * <b>Added in Version 1.1.06:</b>
 * Status value parameter (nonnegative integer).
 * For backwards compatibility, the status value (integer {@link #getStatusValue()} ) is not sent
 * as a parameter, if it is 0.  (In JSettlers older than 1.1.06, it
 * is always 0.)  Earlier versions simply printed the entire message as text,
 * without trying to parse anything.
 *
 * @author Robert S. Thomas
 */
public class SOCStatusMessage extends SOCMessage
{
    /**
     * Status value constants. SV_OK = 0 : Welcome, OK to connect.
     * SV_NOT_OK_GENERIC = 1 : Generic "not OK" status value.
     * Other specific status value constants are given here.
     * If any are added, do not change or remove the numeric values of earlier ones.
     * @since 1.1.06
     */
    public static final int SV_OK = 0;

    /**
     * SV_NOT_OK_GENERIC = 1 : Generic "not OK" status value.
     * This is given to the client if a more specific value does not apply,
     * or if the client's version is older than the version where the more specific
     * value was introduced.
     * @since 1.1.06
     */
    public static final int SV_NOT_OK_GENERIC = 1;

    /**
     * Name not found in server's accounts = 2
     * @since 1.1.06
     */
    public static final int SV_NAME_NOT_FOUND = 2;

    /**
     * Incorrect password = 3
     * @since 1.1.06
     */
    public static final int SV_PW_WRONG = 3;

    /**
     * This name is already logged in = 4
     * @since 1.1.06
     */
    public static final int SV_NAME_IN_USE = 4;

    /**
     * This game version is too new for your client's version to join = 5
     * @since 1.1.06
     */
    public static final int SV_CANT_JOIN_GAME_VERSION = 5;

    /**
     * Cannot log in due to a database problem = 6
     * @since 1.1.06
     */
    public static final int SV_PROBLEM_WITH_DB = 6;

    /**
     * For account creation, new account was created successfully = 7
     * @since 1.1.06
     */
    public static final int SV_ACCT_CREATED_OK = 7;

    /**
     * For account creation, an error prevented the account from
     * being created = 8
     * @since 1.1.06
     */
    public static final int SV_ACCT_NOT_CREATED_ERR = 8;

    /**
     * New game requested with game options, but some are not
     * recognized by the server = 9
     * @see soc.server.SOCServer#handleNEWGAMEWITHOPTIONS
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_OPTION_UNKNOWN = 9;

    /**
     * New game requested with game options, but this game
     * already exists = 10
     * @see soc.server.SOCServer#handleNEWGAMEWITHOPTIONS
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_ALREADY_EXISTS = 10;

    /**
     * Text for server or client to present: New game requested,
     * but this game already exists
     * @since 1.1.07
     */
    public static final String MSG_SV_NEWGAME_ALREADY_EXISTS
        = "A game with this name already exists, please choose a different name.";

    /**
     * Status message
     */
    private String status;

    /**
     * Optional status value; defaults to 0 ({@link #SV_OK})
     * @since 1.1.06
     */
    private int svalue;

    /**
     * Create a StatusMessage message, with status value 0 ({@link #SV_OK}).
     *
     * @param st  the status message text
     */
    public SOCStatusMessage(String st)
    {
        this (0, st);
    }

    /**
     * Create a StatusMessage message, with a nonzero value.
     *
     * @param sv  status value (from constants defined here, such as {@link #SV_OK})
     * @param st  the status message text
     * @since 1.1.06
     */
    public SOCStatusMessage(int sv, String st)
    {
        messageType = STATUSMESSAGE;
        status = st;
        svalue = sv;
    }

    /**
     * @return the status message text
     */
    public String getStatus()
    {
        return status;
    }

    /**
     * @return the status value, as in {@link #SV_OK}
     */
    public int getStatusValue()
    {
        return svalue;
    }

    /**
     * STATUSMESSAGE sep [svalue sep2] status
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(svalue, status);
    }

    /**
     * STATUSMESSAGE sep [svalue sep2] status
     *
     * @param sv  the status value; if 0 or less, is not output.
     *            Should be a constant such as {@link #SV_OK}.
     * @param st  the status
     * @return the command string
     */
    public static String toCmd(int sv, String st)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(STATUSMESSAGE);
        sb.append(sep);
        if (sv > 0)
        {
            sb.append(sv);
            sb.append(sep2);
        }
        sb.append(st);
        return sb.toString();
    }

    /**
     * Parse the command String into a StatusMessage message
     *
     * @param s   the String to parse
     * @return    a StatusMessage message, or null of the data is garbled
     */
    public static SOCStatusMessage parseDataStr(String s)
    {
        int sv = 0;
        int i = s.indexOf(sep2);
        if (i != -1)
        {
            if (i > 0)
            {
                try
                {
                    sv = Integer.parseInt(s.substring(0, i));
                    if (sv < 0)
                        sv = 0;
                }
                catch (NumberFormatException e)
                {
                    // continue with sv=0, don't strip the string
                    i = -1;
                }
            } else {
                return null;   // Garbled: Started with sep2
            }
            s = s.substring(i + 1);
        }
        return new SOCStatusMessage(sv, s);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCStatusMessage:");
        if (svalue > 0)
        {
            sb.append("sv=");
            sb.append(svalue);
            sb.append(sep2);
        }
        sb.append("status=");
        sb.append(status);
        return sb.toString();
    }

}
