/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * This file Copyright (C) 2008,2010-2012,2014 Jeremy D Monin <jeremy@nand.net>
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
 * Template for per-game message types with 1 integer parameter.
 * Your class javadoc should explain the meaning of param1,
 * so that you won't need to write a getter for it.
 *<P>
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code><pre>
 *   public static SOCLongestRoad parseDataStr(final String s)
 *   {
 *       String ga; // the game name
 *       int pn; // the seat number
 *
 *       StringTokenizer st = new StringTokenizer(s, sep2);
 *
 *       try
 *       {
 *           ga = st.nextToken();
 *           pn = Integer.parseInt(st.nextToken());
 *       }
 *       catch (Exception e)
 *       {
 *           return null;
 *       }
 *
 *        return new SOCLongestRoad(ga, pn);
 *   }
 *</pre></code>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.00
 */
public abstract class SOCMessageTemplate1i extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game.
     */
    protected String game;

    /**
     * Single integer parameter.
     */
    protected int p1;

    /**
     * Create a new message.
     *
     * @param id  Message type ID
     * @param ga  Name of game this message is for
     * @param p   Parameter
     */
    protected SOCMessageTemplate1i(int id, String ga, int p)
    {
        messageType = id;
        game = ga;
        p1 = p;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the single parameter
     */
    public int getParam()
    {
        return p1;
    }

    /**
     * MESSAGETYPE sep game sep2 param
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, p1);
    }

    /**
     * MESSAGETYPE sep game sep2 param
     *
     * @param messageType The message type id
     * @param ga  the game name
     * @param param The parameter
     * @return    the command string
     */
    protected static String toCmd(final int messageType, String ga, int param)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + param;
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse
     * @return    a LongestRoad message, or null if parsing errors
    public static SOCLongestRoad parseDataStr(final String s)
    {
        String ga; // the game name
        int pn; // the seat number

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLongestRoad(ga, pn);
    }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game + "|param=" + p1;
    }
}
