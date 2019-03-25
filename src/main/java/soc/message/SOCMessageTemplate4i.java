/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2011-2014 Jeremy D Monin <jeremy@nand.net>
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
 * Template for per-game message types with 4 integer parameters.
 * Your class javadoc should explain the meaning of param1, param2, param3, and param4,
 * so that you won't need to write getters for those.
 *<P>
 * You will have to write parseDataStr, because of its return
 * type and because it's static.
 *<P>
 * Sample implementation:
 *<code><pre>
 *   // format of s: MOVEPIECE sep game sep2 playerNumber sep2 pieceType sep2 coordFrom sep2 coordTo
 *   public static SOCMovePiece parseDataStr(final String s)
 *   {
 *       String ga; // the game name
 *       int pn; // the player number
 *       int pt; // piece type
 *       int cf; // coordinates from
 *       int ct; // coordinates to
 *
 *       StringTokenizer st = new StringTokenizer(s, sep2);
 *
 *       try
 *       {
 *           ga = st.nextToken();
 *           pn = Integer.parseInt(st.nextToken());
 *           pt = Integer.parseInt(st.nextToken());
 *           cf = Integer.parseInt(st.nextToken());
 *           ct = Integer.parseInt(st.nextToken());
 *       }
 *       catch (Exception e)
 *       {
 *           return null;
 *       }
 *
 *        return new SOCMovePiece(ga, pn, pt, cf, ct);
 *   }
 *</pre></code>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.18
 */
public abstract class SOCMessageTemplate4i extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1118L;

    /**
     * Name of the game.
     */
    protected String game;

    /**
     * First integer parameter.
     */
    protected int p1;

    /**
     * Second integer parameter.
     */
    protected int p2;

    /**
     * Third integer parameter.
     */
    protected int p3;

    /**
     * Fourth integer parameter.
     */
    protected int p4;

    /**
     * Create a new message.
     *
     * @param messageType  Message type ID
     * @param ga  Name of game this message is for
     * @param p1  Parameter 1
     * @param p2  Parameter 2
     * @param p3  Parameter 3
     * @param p4  Parameter 4
     */
    protected SOCMessageTemplate4i
        (final int messageType, final String ga, final int p1, final int p2, final int p3, final int p4)
    {
        this.messageType = messageType;
        game = ga;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.p4 = p4;
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
    public int getParam1()
    {
        return p1;
    }

    /**
     * @return the second parameter
     */
    public int getParam2()
    {
        return p2;
    }

    /**
     * @return the third parameter
     */
    public int getParam3()
    {
        return p3;
    }

    /**
     * @return the fourth parameter
     */
    public int getParam4()
    {
        return p4;
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2 sep2 param3 sep2 param4
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, p1, p2, p3, p4);
    }

    /**
     * MESSAGETYPE sep game sep2 param1 sep2 param2 sep2 param3 sep2 param4
     *
     * @param messageType The message type id
     * @param ga  the new game name
     * @param p1  The first parameter
     * @param p2  The second parameter
     * @param p3  the third parameter
     * @param p4  the fourth parameter
     * @return    the command string
     */
    protected static String toCmd(final int messageType, final String ga, final int p1, final int p2, final int p3, final int p4)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + p1 + sep2 + p2 + sep2 + p3 + sep2 + p4;
    }

    /**
     * Parse the command String into a MessageType message
     *
     * @param s   the String to parse.
     *            Format of s: MOVEPIECE sep game sep2 playerNumber sep2 pType sep2 coordFrom sep2 coordTo
     * @return    a MovePiece message, or null if parsing errors
     *
    public static SOCMovePiece parseDataStr(final String s)
    {
        String ga; // the game name
        int pn; // the player number
        int pt; // piece type
        int cf; // coordinates from
        int ct; // coordinates to

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            pt = Integer.parseInt(st.nextToken());
            cf = Integer.parseInt(st.nextToken());
            ct = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

         return new SOCMovePiece(ga, pn, pt, cf, ct);
     }
     */

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game
            + "|param1=" + p1 + "|param2=" + p2
            + "|param3=" + p3 + "|param4=" + p4;
    }

}
