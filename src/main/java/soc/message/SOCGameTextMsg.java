/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2008-2010,2012-2014,2016-2017,2019 Jeremy D Monin <jeremy@nand.net>
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
 * This message contains a text message for a SoC game.
 * Seen by {@link soc.server.SOCServer server} or by
 * human players on-screen, occasionally parsed by robots
 * if they're expecting a debug message.
 *<P>
 * Text announcements from the server are instead sent as
 * {@link SOCGameServerText} to client versions 2.0.00 and newer.
 * See that class javadoc for what game information is sent as text
 * and what's sent as other message types.
 *<P>
 * Text messages from clients in chat channels (not in games)
 * use {@link SOCChannelTextMsg} instead.
 *
 * @author Robert S Thomas
 */
public class SOCGameTextMsg extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * {@code "Server"}, the reserved nickname used when sending game text messages
     * from the server itself, not chat messages from players.
     *<P>
     * This is used only for pre-2.0.00 clients, because starting with that version,
     * the server's announcement texts are sent as {@link SOCGameServerText} instead.
     *<P>
     * Moved here in v2.0.00; previously declared in SOCServer and a string literal at clients.
     * @since 2.0.00
     */
    public static final String SERVERNAME = "Server";

    /**
     * Reserved nickname {@code ":"} for server messages which should appear in the chat area.
     * Can be used with any client version.
     *<P>
     * When this "nickname" is used, the message text should end with " ::" because the original client would
     * begin the text line with ":: " from {@code nickname + ": "}.
     *<P>
     * Also used in {@link SOCChannelTextMsg}.
     * @since 2.0.00
     */
    public static final String SERVER_FOR_CHAT = ":";

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
     * Our token separator; to avoid collision with any possible text from user, not the normal {@link SOCMessage#sep2}
     */
    private static String sep2 = "" + (char) 0;

    /**
     * Name of game
     */
    private String game;

    /**
     * Nickname of sender, or {@link #SERVERNAME}.
     * In v2.0.00 and newer, can also be {@code ":"} ({@link #SERVER_FOR_CHAT}) for server messages
     * which should appear in the chat area (recent-chat recap, etc).
     */
    private String nickname;

    /**
     * Text message.
     * For expected format when {@link #nickname} is {@link #SERVER_FOR_CHAT}, see that nickname constant's javadoc.
     */
    private String text;

    /**
     * Create a GameTextMsg message.
     *
     * @param ga  name of game
     * @param nn  nickname of sender; announcements from the server (not from a player) use {@link #SERVERNAME}
     *     or {@link #SERVER_FOR_CHAT}
     * @param tm  text message. For expected format when {@code nn} is {@link #SERVER_FOR_CHAT},
     *     see that constant's javadoc.
     */
    public SOCGameTextMsg(String ga, String nn, String tm)
    {
        messageType = GAMETEXTMSG;
        game = ga;
        nickname = nn;
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
     * Get the player's nickname for a chat message, or the server for game announcement text
     * @return the player's nickname, or {@link #SERVERNAME},
     *     or {@link #SERVER_FOR_CHAT} ({@code ":"}) for server messages which should appear in
     *     the chat area (recap, etc)
     */
    public String getNickname()
    {
        return nickname;
    }

    /**
     * @return the text message.
     *    For expected format when {@link #getNickname()} is {@link #SERVER_FOR_CHAT},
     *    see that constant's javadoc.
     */
    public String getText()
    {
        return text;
    }

    /**
     * GAMETEXTMSG sep game sep2 nickname sep2 text
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, nickname, text);
    }

    /**
     * GAMETEXTMSG sep game sep2 nickname sep2 text
     *
     * @param ga  the game name
     * @param nn  the nickname
     * @param tm  the text message
     * @return    the command string
     */
    public static String toCmd(String ga, String nn, String tm)
    {
        return GAMETEXTMSG + sep + ga + sep2 + nn + sep2 + tm;
    }

    /**
     * Parse the command String into a GameTextMsg message
     *
     * @param s   the String to parse
     * @return    a GameTextMsg message, or null if the data is garbled
     */
    public static SOCGameTextMsg parseDataStr(String s)
    {
        String ga;
        String nn;
        String tm;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            nn = st.nextToken();
            tm = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCGameTextMsg(ga, nn, tm);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCGameTextMsg:game=" + game + "|nickname=" + nickname + "|text=" + text;

        return s;
    }

}
