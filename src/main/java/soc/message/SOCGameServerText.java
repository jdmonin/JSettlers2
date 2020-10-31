/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2014,2016-2017,2019-2020 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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
 * This message contains a text message or announcement from the server
 * for a SoC game.  Sent by {@link soc.server.SOCServer server}.
 * Player chat text is sent as {@link SOCGameTextMsg} instead.
 *<P>
 * Robots ignore this message type so they won't be dependent on brittle
 * text parsing. For the benefit of robots and to help client responsiveness,
 * the server often precedes this message type with data-only messages
 * such as {@link SOCAcceptOffer}.
 *<P>
 * Occasionally, game text is sent with additional information
 * via {@link SOCSVPTextMessage} instead of using this message type.
 * Some simple actions or prompts are sent by the server with {@link SOCSimpleAction}
 * or {@link SOCSimpleRequest} instead of as text.
 *<P>
 * This class was introduced in version 2.0.00; earlier versions of the server
 * and client used {@link SOCGameTextMsg} for server announcements and messages.
 *
 * @see SOCKeyedMessage
 * @author Jeremy D Monin
 * @since 2.0.00
 */
public class SOCGameServerText extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * First version number (2.0.00) that has this message type.
     * Send older clients {@link SOCGameTextMsg} or other appropriate messages instead.
     */
    public static final int VERSION_FOR_GAMESERVERTEXT = 2000;

    /**
     * Our token separator; not the normal {@link SOCMessage#sep2}.
     * Used in {@link #parseDataStr(String)} to get all of the text,
     * as a separator character unlikely to be found in text: {@code (char) 1}.
     *<P>
     * {@link SOCGameTextMsg} overrides {@code sep2} instead.
     *<P>
     * Before v2.4.50, this was private and named {@code unlikely_char1}.
     */
    public static final String UNLIKELY_CHAR1 = Character.toString( (char) 1 );

    /**
     * Name of game
     */
    private final String game;

    /**
     * Text message
     */
    private final String text;

    /**
     * Create a GameServerText message.
     *
     * @param ga  name of game
     * @param tm  text message
     */
    public SOCGameServerText(final String ga, final String tm)
    {
        messageType = GAMESERVERTEXT;
        game = ga;
        text = tm;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the text message
     */
    public String getText()
    {
        return text;
    }

    /**
     * GAMESERVERTEXT sep game char1 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, text);
    }

    /**
     * GAMESERVERTEXT sep game char1 text
     *
     * @param ga  the game name
     * @param tm  the text message
     * @return    the command string
     */
    public static String toCmd(final String ga, final String tm)
    {
        return GAMESERVERTEXT + sep + ga + UNLIKELY_CHAR1 + tm;
    }

    /**
     * Parse the command String into a GameServerText message
     *
     * @param s   the String to parse
     * @return    a GameServerText message, or null if the data is garbled
     */
    public static SOCGameServerText parseDataStr(final String s)
    {
        final String ga, tm;

        StringTokenizer st = new StringTokenizer(s, UNLIKELY_CHAR1);

        try
        {
            ga = st.nextToken();
            tm = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameServerText(ga, tm);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Changes separator after game to the {@link #UNLIKELY_CHAR1} expected by {@link #parseDataStr(String)}.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        if (! messageStrParams.startsWith("game="))
            return messageStrParams;  // probably malformed
        final int i = messageStrParams.indexOf("|text=");
        if (i <= 0)
            return messageStrParams;

        final String ga = messageStrParams.substring(5, i),
            tm = messageStrParams.substring(i + 6);
        return ga + UNLIKELY_CHAR1 + tm;
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCGameServerText:game=" + game + "|text=" + text;
    }

    /**
     * Minimum version where this message type is used.
     * GAMESERVERTEXT introduced in 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public final int getMinimumVersion() { return VERSION_FOR_GAMESERVERTEXT; /* == 2000 */ }

}
