/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2012-2014,2016-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.util.SOCFeatureSet;  // for javadocs only


/**
 * This is a text message that shows in a status box on the client.
 * Used for "welcome" message at initial connect to game (follows
 * {@link SOCJoinChannelAuth JOINCHANNELAUTH} or {@link SOCJoinGameAuth JOINGAMEAUTH}),
 * or rejection if client can't join that game (or channel).
 * Also used in {@link soc.client.SOCAccountClient SOCAccountClient}
 * to tell the user if their change was made successfully.
 *<P>
 * Sent in response to any message type used by clients to request authentication
 * and create or connect to a game or channel: {@link SOCJoinGame}, {@link SOCJoinChannel},
 * {@link SOCImARobot}, {@link SOCAuthRequest}, {@link SOCNewGameWithOptionsRequest}.
 *
 * <H3>Status values:</H3>
 * The Status Value parameter (nonnegative integer) was added in version 1.1.06.
 * For backwards compatibility, the status value (integer {@link #getStatusValue()} ) is not sent
 * as a parameter if it is 0.  (In JSettlers older than 1.1.06, status value
 * is always 0.)  Earlier versions simply printed the entire message as text,
 * without trying to parse anything.
 *
 * <H5>Status value back-compatibility:</H5>
 * The server-called method {@link #toCmd(int, int, String)} checks client version compatibility
 * to avoid sending newly defined status codes/values to clients too old to understand them;
 * older "fallback" status values are sent instead. See individual status values' javadocs.
 *
 * <H3>"Debug Is On" notification:</H3>
 * In version 1.1.17 and newer, a server with debug commands enabled will send
 * a STATUSMESSAGE right after sending its {@link SOCVersion VERSION}, which will include text
 * such as "debug is on" or "debugging on".  It won't send a nonzero status value, because
 * older client versions might treat it as generic failure and disconnect.
 *<P>
 * In version 2.0.00 and newer, the server's "debug is on" status is {@link #SV_OK_DEBUG_MODE_ON}.
 * Older clients are sent {@link #SV_OK}, and the status text to older clients must include the word "debug".
 *
 * @author Robert S. Thomas
 */
public class SOCStatusMessage extends SOCMessage
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Status value constants. SV_OK = 0 : Welcome, OK to give a username and optional password.
     * @see #SV_NOT_OK_GENERIC
     * @see #SV_OK_DEBUG_MODE_ON
     * @since 1.1.06
     */
    public static final int SV_OK = 0;

    /**
     * SV_NOT_OK_GENERIC = 1 : Generic "not OK" status value.
     * This is given to the client if a more specific value does not apply,
     * or if the client's version is older than the version where the more specific
     * value was introduced.
     * @see #SV_OK
     * @since 1.1.06
     */
    public static final int SV_NOT_OK_GENERIC = 1;

    // Other specific status value constants are given here.
    // When adding new ones, see "IF YOU ADD A STATUS VALUE" comment below.

    /**
     * Name not found in server's accounts = 2.
     * Server version 1.1.19 and 1.1.20 never replies with this to any authentication
     * request message type; {@link #SV_PW_WRONG} is sent even if the name doesn't exist.
     * Server v1.2.00 and higher will send this reply to any older clients which
     * can't recognize {@link #SV_OK_SET_NICKNAME}.
     * @since 1.1.06
     */
    public static final int SV_NAME_NOT_FOUND = 2;

    /**
     * Incorrect password = 3.
     * Also used in v1.1.19 and higher for authentication replies when the
     * account name is not found, instead of {@link #SV_NAME_NOT_FOUND}.
     *<P>
     * If no password was given but the server requires passwords (a config option in
     * server v1.1.19 and higher), it will reply with {@link #SV_PW_REQUIRED} if the
     * client is v1.1.19 or higher, {@link #SV_PW_WRONG} if lower.
     * @since 1.1.06
     */
    public static final int SV_PW_WRONG = 3;

    /**
     * This name is already logged in = 4.
     * In version 1.1.08 and higher, a "take over" option is used for
     * reconnect when a client loses connection, and server doesn't realize it.
     * A new connection can "take over" the name after a minute's timeout.
     * For actual timeouts, see SOCServer.checkNickname.
     * @see #SV_NAME_NOT_ALLOWED
     * @since 1.1.06
     */
    public static final int SV_NAME_IN_USE = 4;

    /**
     * This game version is too new for your client's version to join = 5
     *<P>
     * Server v1.1.20 and newer also send this value to {@code SOCAccountClient}
     * if client is too old to create accounts at the server's version
     * because of a required logon auth or other message added since that client's version.
     *<P>
     * Server v2.0.00 and newer also send this value if client wants to join or create a game
     * but is missing a Client Feature (from {@link SOCFeatureSet}) required by the game.
     * This situation doesn't need its own Status Value because the server announces such games
     * to the client with the "Cannot Join" flag prefix, and the client shouldn't have UI options
     * to create a game with features it doesn't have.
     *
     * @since 1.1.06
     */
    public static final int SV_CANT_JOIN_GAME_VERSION = 5;

    /**
     * Cannot log in or create account due to a temporary database problem = 6
     * @since 1.1.06
     */
    public static final int SV_PROBLEM_WITH_DB = 6;

    /**
     * For account creation, new account was created successfully = 7
     * @see #SV_ACCT_CREATED_OK_FIRST_ONE
     * @since 1.1.06
     */
    public static final int SV_ACCT_CREATED_OK = 7;

    /**
     * For account creation, an error prevented the account from
     * being created, or server doesn't use accounts, = 8.
     *<P>
     * To see whether a server v1.1.19 or newer uses accounts and passwords, check
     * whether {@link SOCFeatureSet#SERVER_ACCOUNTS} is sent when the client connects.
     * @since 1.1.06
     * @see #SV_ACCT_NOT_CREATED_DENIED
     */
    public static final int SV_ACCT_NOT_CREATED_ERR = 8;

    /**
     * New game requested with game options, but some are not
     * recognized by the server = 9
     * @see soc.server.SOCServerMessageHandler#handleNEWGAMEWITHOPTIONSREQUEST
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_OPTION_UNKNOWN = 9;

    /**
     * New game requested with game options, but this option or value
     * is too new for the client to handle = 10
     *<P>
     * Format of this status text is: <BR>
     * Status string with error message
     *   {@link SOCMessage#sep2 SEP2} game name
     *   {@link SOCMessage#sep2 SEP2} option keyname with problem
     *   {@link SOCMessage#sep2 SEP2} option keyname with problem (if more than one)
     *   ...
     * @see soc.server.SOCServerMessageHandler#handleNEWGAMEWITHOPTIONSREQUEST
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_OPTION_VALUE_TOONEW = 10;

    /**
     * New game requested, but this game already exists = 11
     * @see soc.server.SOCServerMessageHandler#handleNEWGAMEWITHOPTIONSREQUEST
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_ALREADY_EXISTS = 11;

    /**
     * New game requested, but name of game or player does not meet standards = 12
     * @see soc.server.SOCServer#createOrJoinGameIfUserOK
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_NAME_REJECTED = 12;

    /**
     * New game requested, but name of game or player is too long = 13.
     * The text returned with this status shall indicate the max permitted length.
     * @see soc.server.SOCServer#createOrJoinGameIfUserOK
     * @since 1.1.07
     */
    public static final int SV_NEWGAME_NAME_TOO_LONG = 13;

    /**
     * New game requested, but client already has created too many active games.
     * The text returned with this status shall indicate the max number.
     * @see soc.server.SOCServer#createOrJoinGameIfUserOK
     * @since 1.1.10
     */
    public static final int SV_NEWGAME_TOO_MANY_CREATED = 14;

    /**
     * New chat channel requested, but client already has created too many active channels.
     * The text returned with this status shall indicate the max number.
     * @since 1.1.10
     */
    public static final int SV_NEWCHANNEL_TOO_MANY_CREATED = 15;

    /**
     * Password required but missing = 16.
     * Used if server config settings require all players to have user accounts and passwords.
     *<P>
     * Clients older than v1.1.19 won't recognize this status value; if possible they
     * should be sent {@link #SV_PW_WRONG} instead.
     * @since 1.1.19
     */
    public static final int SV_PW_REQUIRED = 16;

    /**
     * For account creation, the requesting user's account is not authorized to create accounts = 17.
     * @since 1.1.19
     * @see #SV_ACCT_NOT_CREATED_ERR
     */
    public static final int SV_ACCT_NOT_CREATED_DENIED = 17;

    /**
     * For account creation, new account was created successfully and was the server's first account = 18.
     * Normally (when not the first account) the status code returned is {@link #SV_ACCT_CREATED_OK}.
     * This separate code is provided to let the client know they
     * must authenticate before creating any other accounts.
     *<P>
     * This status is not sent if the server is in Open Registration mode ({@link SOCFeatureSet#SERVER_OPEN_REG}),
     * because in that mode there's nothing special about the first account and no need to authenticate
     * before creating others.
     *<P>
     * Clients older than v1.1.20 won't recognize this status value;
     * they should be sent {@link #SV_ACCT_CREATED_OK} instead.
     * @since 1.1.20
     */
    public static final int SV_ACCT_CREATED_OK_FIRST_ONE = 18;

    /**
     * Client's authentication failed because requested nickname is not allowed or is reserved:
     * {@code "Server"}, bot names like {@code "robot 7"}, or {@code "debug"} when not in debug mode.
     *<P>
     * This code would also be sent if the nickname fails {@link SOCMessage#isSingleLineAndSafe(String)},
     * but a client's {@link SOCMessage} with that failing nickname would be malformed and not parsed at
     * the server, so it wouldn't see a message to reply to.
     *<P>
     * Server versions earlier than v1.2.00 would instead respond with {@link #SV_NAME_IN_USE};
     * this status is more specific. Clients older than v1.2.00 won't recognize this status value;
     * they will be sent {@link #SV_NOT_OK_GENERIC} instead.
     * @since 1.2.00
     */
    public static final int SV_NAME_NOT_ALLOWED = 19;

    /**
     * Client has authenticated successfully, but their case-insensitive username differs from
     * their exact case-sensitive name in the database. To join and create games and channels,
     * the client must update its internal nickname field.
     *<P>
     * Status text format: exactUsername + {@link SOCMessage#sep2_char} + text to display
     *<P>
     * When joining a game or chat channel, the message with this status is sent before {@link SOCJoinGameAuth}
     * or (for v1.2.01 and newer) {@link SOCJoinChannelAuth}. In reply to client's {@link SOCAuthRequest} this
     * status is sent instead of a message with status {@link #SV_OK}.
     *<P>
     * Clients older than v1.2.00 won't recognize this status value, and won't know to update their nickname
     * once authenticated; they will instead be sent {@link #SV_NAME_NOT_FOUND} with status text indicating
     * the exact case-sensitive nickname to use.
     *<P>
     * <B>Bots:</B> Server and client code assume that only human player clients, not bots or
     * {@code SOCDisplaylessPlayerClient}, will need to handle this status value.
     * @since 1.2.00
     */
    public static final int SV_OK_SET_NICKNAME = 20;

    /**
     * Client has connected successfully ({@link #SV_OK}) and the server's Debug Mode is on.
     * Versions older than 2.0.00 get {@link #SV_OK} instead;
     * see {@link #toCmd(int, int, String)}.
     * @since 2.0.00
     */
    public static final int SV_OK_DEBUG_MODE_ON = 21;

    // IF YOU ADD A STATUS VALUE:
    // Do not change or remove the numeric values of earlier ones.
    // Be sure to update statusValidAtVersion().

    /**
     * Text for server or client to present: New game requested,
     * but this game already exists
     * @since 1.1.07
     */
    public static final String MSG_SV_NEWGAME_ALREADY_EXISTS
        = "A game with this name already exists, please choose a different name.";

    /**
     * Text for server or client to present: New game or auth requested,
     * but game name or player name does not meet standards
     * @see #MSG_SV_NEWGAME_NAME_REJECTED_DIGITS
     * @since 1.1.07
     */
    public static final String MSG_SV_NEWGAME_NAME_REJECTED
        = "This name is not permitted, please choose a different name.";

    /**
     * Text for server or client to present: New game or auth requested,
     * but game name or player name is only digits and does not meet standards
     * @see #MSG_SV_NEWGAME_NAME_REJECTED
     * @since 2.0.00
     */
    public static final String MSG_SV_NEWGAME_NAME_REJECTED_DIGITS
        = "A name with only digits is not permitted, please add a letter.";

    /**
     * Text for server or client to present: New game or auth requested,
     * but game name or player name is too long.  Maximum permitted length
     * is appended to this message after the trailing ":".
     * @since 1.1.07
     */
    public static final String MSG_SV_NEWGAME_NAME_TOO_LONG
        = "Please choose a shorter name; maximum length: ";

    /**
     * Text for {@link #SV_NEWGAME_TOO_MANY_CREATED}.
     * Maximum game count is appended to this after the trailing ":".
     * @since 1.1.10
     */
    public static final String MSG_SV_NEWGAME_TOO_MANY_CREATED
        = "Too many of your games still active; maximum: ";

    /**
     * Text for {@link #SV_NEWCHANNEL_TOO_MANY_CREATED}.
     * Maximum channel count is appended to this after the trailing ":".
     * @since 1.1.10
     */
    public static final String MSG_SV_NEWCHANNEL_TOO_MANY_CREATED
        = "Too many of your chat channels still active; maximum: ";

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
     * @param st  the status message text.
     *            For this constructor, since status value is 0,
     *            may not contain {@link SOCMessage#sep2} characters.
     *            This will cause parsing to fail on the remote end.
     */
    public SOCStatusMessage(String st)
    {
        this (0, st);
    }

    /**
     * Create a StatusMessage message, with a nonzero status value.
     * Does not check that {@code sv} is compatible with the client it's sent to;
     * for that use {@link #toCmd(int, int, String)} instead.
     *
     * @param sv  status value (from constants defined here, such as {@link #SV_OK})
     * @param st  the status message text.
     *            If sv is nonzero, you may embed {@link SOCMessage#sep2} characters
     *            in your string, and they will be passed on for the receiver to parse.
     * @since 1.1.06
     */
    public SOCStatusMessage(int sv, String st)
    {
        messageType = STATUSMESSAGE;
        status = st;
        svalue = sv;
    }

    /**
     * @return the status message text. Is allowed to contain {@link SOCMessage#sep2} characters.
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
     * STATUSMESSAGE sep [svalue sep2] status -- does not include backwards compatibility.
     * This method is best for sending status values {@link #SV_OK} or {@link #SV_NOT_OK_GENERIC}.
     * for other newer status values, call {@link #toCmd(int, int, String)} instead.
     *
     * @param sv  the status value; if 0 or less, is not output.
     *            Should be a constant such as {@link #SV_OK}.
     *            Remember that not all client versions recognize every status;
     *            see {@link #toCmd(int, int, String)}.
     * @param st  the status message text.
     *            If sv is nonzero, you may embed {@link SOCMessage#sep2} characters
     *            in your string, and they will be passed on for the receiver to parse.
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
     * STATUSMESSAGE sep [svalue sep2] status -- includes backwards compatibility.
     * Calls {@link #statusValidAtVersion(int, int)}. if {@code sv} isn't recognized in
     * that client version, will send {@link #SV_NOT_OK_GENERIC} or another "fallback"
     * value defined in the client. See individual status values' javadocs for details.
     *<UL>
     * <LI> {@link #SV_OK_DEBUG_MODE_ON} falls back to {@link #SV_OK}
     * <LI> {@link #SV_PW_REQUIRED} falls back to {@link #SV_PW_WRONG}
     * <LI> {@link #SV_ACCT_CREATED_OK_FIRST_ONE} falls back to {@link #SV_ACCT_CREATED_OK}
     * <LI> {@link #SV_OK_SET_NICKNAME} has no successful fallback, the client must be
     *      sent {@link #SV_NAME_NOT_FOUND} and must reauthenticate; throws {@link IllegalArgumentException}
     * <LI> All others fall back to {@link #SV_NOT_OK_GENERIC}
     * <LI> In case the fallback value is also not recognized at the client,
     *      {@code toCmd(..)} will fall back again to something more generic
     * <LI> Clients before v1.1.06 will be sent the status text {@code st} only,
     *      without the {@code sv} parameter which was added in 1.1.06
     *</UL>
     *
     * @param sv  the status value; if 0 or less, is not output.
     *            Should be a constant such as {@link #SV_OK}.
     * @param cliVers Client's version, same format as {@link soc.util.Version#versionNumber()}
     * @param st  the status message text.
     *            If sv is nonzero, you may embed {@link SOCMessage#sep2} characters
     *            in your string, and they will be passed on for the receiver to parse.
     * @return the command string
     * @throws IllegalArgumentException If a {@code sv} has no successful fallback at {@code cliVers},
     *     such as with {@link #SV_OK_SET_NICKNAME}, and the client must reauthenticate instead;
     *     the exception is thrown to prevent continued server processing as if the fallback was successful.
     * @since 1.1.07
     * @see #statusFallbackForVersion(int, int)
     */
    public static String toCmd(int sv, final int cliVers, final String st)
        throws IllegalArgumentException
    {
        int fallSV = sv = statusFallbackForVersion(sv, cliVers);
        if (fallSV != sv)
            return toCmd(sv, cliVers, st);  // ensure fallback value is valid at client's version
        else
            return toCmd(sv, st);
    }

    /**
     * Is this status value defined in this version?  If not, {@link #SV_NOT_OK_GENERIC} should be sent instead.
     * A different fallback value can be sent instead if the client is new enough to understand it; for
     * example instead of {@link #SV_ACCT_CREATED_OK_FIRST_ONE}, send {@link #SV_ACCT_CREATED_OK}.
     *<P>
     * See {@link #statusFallbackForVersion(int, int)} to check client version and find a compatible status value,
     * as the server may need to do with older clients.
     *
     * @param statusValue  status value (from constants defined here, such as {@link #SV_OK})
     * @param cliVersion Client's version, same format as {@link soc.util.Version#versionNumber()};
     *                   below 1.1.06, only 0 ({@link #SV_OK}) is allowed.
     *                   If cliVersion > ourVersion, will act as if cliVersion == ourVersion.
     * @since 1.1.07
     */
    public static boolean statusValidAtVersion(int statusValue, int cliVersion)
    {
        switch (cliVersion)
        {
        case 1106:
            return (statusValue <= SV_ACCT_NOT_CREATED_ERR);
        case 1107:
        case 1108:
        case 1109:
            return (statusValue <= SV_NEWGAME_NAME_TOO_LONG);
        case 1110:
            return (statusValue <= SV_NEWCHANNEL_TOO_MANY_CREATED);
        case 1119:
            return (statusValue <= SV_ACCT_NOT_CREATED_DENIED);
        case 1120:
            return (statusValue <= SV_ACCT_CREATED_OK_FIRST_ONE);
        case 1200:
            return (statusValue <= SV_OK_SET_NICKNAME);
        default:
            {
            if (cliVersion < 1106)       // for 1000 - 1105 inclusive
                return (statusValue == 0);
            else if (cliVersion < 1119)  // for 1111 - 1118 inclusive
                return (statusValue < SV_PW_REQUIRED);
            else if (cliVersion < 2000)
                return (statusValue < SV_OK_DEBUG_MODE_ON);
            else
                // our version or newer; check vs highest constant that we know
                return (statusValue <= SV_OK_DEBUG_MODE_ON);
            }
        }
    }

    /**
     * Is this status value defined at this version? Check client version and if not, find a compatible status value.
     * @param sv  the status value; should be a constant such as {@link #SV_OK}.
     * @param cliVersion Client's version, same format as {@link soc.util.Version#versionNumber()}
     * @return {@code sv} if valid at {@code cliVersion}, or the most applicable status for that version.
     * @throws IllegalArgumentException If a {@code sv} has no successful fallback at {@code cliVersion},
     *     such as with {@link #SV_OK_SET_NICKNAME}, and the client must reauthenticate instead;
     *     the exception is thrown to prevent continued server processing as if the fallback was successful.
     * @see #statusValidAtVersion(int, int)
     * @since 2.0.00
     */
    public static int statusFallbackForVersion(int sv, int cliVersion)
    {
        if (! statusValidAtVersion(sv, cliVersion))
        {
            boolean reject = false;

            switch(sv)
            {
            case SV_OK_DEBUG_MODE_ON:
                sv = SV_OK;
                break;
            case SV_PW_REQUIRED:
                sv = SV_PW_WRONG;
                break;
            case SV_ACCT_CREATED_OK_FIRST_ONE:
                sv = SV_ACCT_CREATED_OK;
                break;
            case SV_OK_SET_NICKNAME:
                reject = true;
                break;
            default:
                if (cliVersion >= 1106)
                    sv = SV_NOT_OK_GENERIC;
                else
                    sv = SV_OK;
            }

            if (reject)
                throw new IllegalArgumentException("No fallback for sv " + sv + " at client v" + cliVersion);
        }

        return sv;
    }

    /**
     * Parse the command String into a StatusMessage message.
     * If status is nonzero, you may embed {@link SOCMessage#sep2} characters
     * in your string, and they will be passed on to the receiver.
     *
     * @param s   the String to parse
     * @return    a StatusMessage message, or null if the data is garbled
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
