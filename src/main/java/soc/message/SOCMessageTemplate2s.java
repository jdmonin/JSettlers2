/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008,2010-2012 Jeremy D Monin <jeremy@nand.net>
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

// import java.util.StringTokenizer;


/**
 * Template for per-game message types with 2 string parameters.
 * The second parameter can be optional.
 * Your class javadoc should explain the meaning of param1 and param2,
 * so that you won't need to write getters for those.
 *<P>
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code><pre>
 *   // format of s: REJECTCARDNAME sep game sep2 cardid sep2 cardname
 *   public static SOCRejectCardName parseDataStr(final String s)
 *   {
 *       String ga; // the game name
 *       String cid; // the card id
 *       String cname; // the card name, or null for unknown
 *
 *       StringTokenizer st = new StringTokenizer(s, sep2);
 *
 *       try
 *       {
 *           ga = st.nextToken();
 *           cid = st.nextToken();
 *           cname = st.nextToken();
 *       }
 *       catch (Exception e)
 *       {
 *           return null;
 *       }
 *
 *        return new SOCRejectCardName(ga, cid, cname);
 *   }
 *</pre></code>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public abstract class SOCMessageTemplate2s extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game.
     */
    protected String game;

    /**
     * First string parameter.
     */
    protected String p1;

    /**
     * Second, optional string parameter; null if missing.
     */
    protected String p2;

    /**
     * Create a new message. The second parameter is optional here;
     * your subclass may decide to make it mandatory.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     * @param p1   First parameter
     * @param p2   Second parameter, or null
     */
    protected SOCMessageTemplate2s(int id, String ga, String p1, String p2)
    {
        messageType = id;
        game = ga;
        this.p1 = p1;
        this.p2 = p2;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the first parameter
     */
    public String getParam1()
    {
        return p1;
    }

    /**
     * @return the second parameter, or null
     */
    public String getParam2()
    {
        return p2;
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, p1, p2);
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @param param1 The first parameter
     * @param param2 The second parameter, or null
     * @return    the command string
     */
    protected static String toCmd(final int messageType, String ga, String param1, String param2)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + param1
        + sep2 + (param2 != null ? param2 : "");
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    a RejectCardName message, or null if parsing errors
    public static SOCRejectCardName parseDataStr(final String s)
    {
        String ga; // the game name
        String cid; // the card id
        String cname; // the card name, or null for unknown

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cid = st.nextToken();
            cname = st.nextToken();
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCRejectCardName(ga, cid, cname);
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game
            + "|param1=" + p1
            + "|param2=" + (p2 != null ? p2 : "");
    }
}
