/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2008-2010,2012-2013 Jeremy D Monin <jeremy@nand.net>
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
 * Occasionally, game text is sent with additional information
 * via {@link SOCSVPTextMessage}, instead of using this message type.
 * Some simple actions are sent by the server with {@link SOCSimpleAction}
 * or {@link SOCSimpleRequest} instead of text.
 *<P>
 * Dice roll result text messages are sent to older clients only;
 * see {@link #VERSION_FOR_DICE_RESULT_INSTEAD}.
 *<P>
 * This class was introduced in version 2.0.00; earlier versions of the server
 * and client use {@link SOCGameTextMsg} for server announcements and messages.
 *
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
     * Version number (2000) where the server no longer sends dice roll results as a game text message.
     *<P>
     * Clients older than v2.0.00 expect the server to announce dice roll
     * results via text messages such as "j rolled a 2 and a 2."
     * The client would then replace that on-screen with "Rolled a 4."
     * to reduce visual clutter.
     *<P>
     * Starting with v2.0.00, the client prints roll results from
     * the {@link SOCDiceResult} message instead. So, the server doesn't send
     * the roll result game text message to v2.0.00 or newer clients.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_DICE_RESULT_INSTEAD = 2000;

    /**
     * Our token separator; not the normal {@link SOCMessage#sep2}.
     * Used in {@link #parseDataStr(String)} to get all of the text,
     * by choosing an unlikely separator character.
     * {@link SOCGameTextMsg} overrides {@code sep2} instead.
     */
    private static final String unlikely_char1 = Character.toString( (char) 1 );

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
        return GAMESERVERTEXT + sep + ga + unlikely_char1 + tm;
    }

    /**
     * Parse the command String into a GameServerText message
     *
     * @param s   the String to parse
     * @return    a GameServerText message, or null of the data is garbled
     */
    public static SOCGameServerText parseDataStr(final String s)
    {
        final String ga, tm;

        StringTokenizer st = new StringTokenizer(s, unlikely_char1);

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
    public final int getMinimumVersion()
    {
        return VERSION_FOR_GAMESERVERTEXT;
    }

}
