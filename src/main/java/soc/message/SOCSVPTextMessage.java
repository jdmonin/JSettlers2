/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012-2014 Jeremy D Monin <jeremy@nand.net>
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
 * Message from server to game's clients, with the number of Special Victory
 * Points (SVP) and reason the player was awarded them.
 * The server will also send a {@link SOCPlayerElement} with the SVP total.
 * So, robot players can ignore this textual message.
 * Also sent for each player's SVPs when client is joining a game in progress,
 * before client sits down at a seat.
 *
 * @see SOCGameServerText
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCSVPTextMessage extends SOCMessage
    implements SOCKeyedMessage, SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of the game.
     */
    public final String game;

    /**
     * Player number.
     */
    public final int pn;

    /**
     * Number of Special Victory Points (SVP) awarded.
     */
    public final int svp;

    /**
     * Description of the player's action that led to the SVP.
     * At the server this is an I18N string key, at the client it's localized text sent from the server.
     * This allows new SVP actions and descriptions without client changes.
     * Constructor checks this against {@link SOCMessage#isSingleLineAndSafe(String, boolean)}.
     */
    public final String desc;

    /**
     * Create a new SVPTEXTMSG message.
     *
     * @param ga  the game name
     * @param pn  Player number
     * @param svp  Number of Special Victory Points (SVP) awarded
     * @param desc  Description of the player's action that led to the SVP.
     *     At the server this is an I18N string key which the server must localize before sending,
     *     at the client it's localized text sent from the server. This allows new SVP actions
     *     and descriptions without client changes.
     * @throws IllegalArgumentException if <tt>desc</tt> is null or
     *     fails {@link SOCMessage#isSingleLineAndSafe(String, boolean) SOCMessage.isSingleLineAndSafe(desc, true)}
     */
    public SOCSVPTextMessage(final String ga, final int pn, final int svp, final String desc)
        throws IllegalArgumentException
    {
        if ((desc == null) || ! isSingleLineAndSafe(desc, true))
            throw new IllegalArgumentException("desc");

        messageType = SVPTEXTMSG;
        game = ga;
        this.pn = pn;
        this.svp = svp;
        this.desc = desc;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * SVPTEXTMSG sep game sep2 pn sep2 svp sep2 desc
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(messageType, game, pn, svp, desc);
    }

    /**
     * SVPTEXTMSG sep game sep2 pn sep2 svp sep2 desc
     *
     * @param ga  the game name
     * @param pn  Player number
     * @param svp  Number of Special Victory Points (SVP) awarded
     * @param desc  Description of the player's action that led to the SVP
     * @return    the command string
     */
    protected static String toCmd
        (final int messageType, final String ga, final int pn, final int svp, final String desc)
    {
        return Integer.toString(messageType) + sep + ga + sep2 + pn + sep2 + svp + sep2 + desc;
    }

    /**
     * {@inheritDoc}
     *<P>
     * This message type's key field is {@link #desc}.
     */
    public String getKey()
    {
        return desc;
    }

    public String toCmd(final String localizedText)
    {
        return toCmd(messageType, game, pn, svp, localizedText);
    }

    /**
     * Parse the command string into a SOCSVPTextMessage message.
     *
     * @param s   the String to parse; format: game sep2 pn sep2 svp sep2 desc
     * @return    a SOCSVPTextMessage message, or null if parsing errors
     */
    public static SOCSVPTextMessage parseDataStr(final String s)
    {
        String ga; // the game name
        int pn, svp;
        String desc;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            svp = Integer.parseInt(st.nextToken());
            // get all of the line for description,
            //  by choosing a separator character
            //  that can't appear in desc
            desc = st.nextToken(Character.toString( (char) 1 )).trim();
            if (desc.startsWith(SOCMessage.sep2))
                desc = desc.substring(1);
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCSVPTextMessage(ga, pn, svp, desc);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return getClass().getSimpleName() + ":game=" + game
            + "|pn=" + pn + "|svp=" + svp + "|desc=" + desc;
    }

}
