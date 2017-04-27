/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2013,2017 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003 Robert S. Thomas <thomas@infolab.northwestern.edu>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.util.StringTokenizer;


/**
 * This message from client means that the client player has picked these resources
 * to receive from the gold hex. Client response to server's
 * {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
 * (Same prompt/response pattern as {@link SOCDiscardRequest} / {@link SOCDiscard}.)
 *<P>
 * If the resource count is wrong, the server will resend {@code SOCSimpleRequest(PROMPT_PICK_RESOURCES)}
 * with the required resource count.
 * Otherwise:
 * The server will report the picked resources to the other
 * players via {@link SOCPlayerElement} and text, but will not send
 * a <tt>SOCPickResources</tt> message to other players.
 * The server will also send all players a
 * {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}, 0)
 * message.
 *<P>
 * Also used in scenario SC_PIRI when player wins a free resource for defeating the
 * pirate fleet attack at a dice roll.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPickResources extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Name of game
     */
    private String game;

    /**
     * The set of resources picked to be gained
     */
    private SOCResourceSet resources;

    /**
     * Create a Pick Resources message.
     *
     * @param ga  the name of the game
     * @param cl  the amount of clay being picked
     * @param or  the amount of ore being picked
     * @param sh  the amount of sheep being picked
     * @param wh  the amount of wheat being picked
     * @param wo  the amount of wood being picked
     * @param uk  the amount of unknown resources being picked
     */
    public SOCPickResources(String ga, int cl, int or, int sh, int wh, int wo, int uk)
    {
        messageType = PICKRESOURCES;
        game = ga;
        resources = new SOCResourceSet(cl, or, sh, wh, wo, uk);
    }

    /**
     * Create a Pick Resources message.
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked
     */
    public SOCPickResources(String ga, SOCResourceSet rs)
    {
        messageType = PICKRESOURCES;
        game = ga;
        resources = rs;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the set of resources being picked
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resources);
    }

    /**
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked
     * @return the command string
     */
    public static String toCmd(String ga, SOCResourceSet rs)
    {
        return toCmd(ga, rs.getAmount(SOCResourceConstants.CLAY), rs.getAmount(SOCResourceConstants.ORE), rs.getAmount(SOCResourceConstants.SHEEP), rs.getAmount(SOCResourceConstants.WHEAT), rs.getAmount(SOCResourceConstants.WOOD), rs.getAmount(SOCResourceConstants.UNKNOWN));
    }

    /**
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2
     * wheat sep2 wood sep2 unknown
     *
     * @param ga  the name of the game
     * @param cl  the ammount of clay being picked
     * @param or  the ammount of ore being picked
     * @param sh  the ammount of sheep being picked
     * @param wh  the ammount of wheat being picked
     * @param wo  the ammount of wood being picked
     * @param uk  the ammount of unknown resources being picked
     * @return the command string
     */
    public static String toCmd(String ga, int cl, int or, int sh, int wh, int wo, int uk)
    {
        return PICKRESOURCES + sep + ga + sep2 + cl + sep2 + or + sep2 + sh + sep2 + wh + sep2 + wo + sep2 + uk;
    }

    /**
     * Parse the command String into a Pick Resources message.
     *
     * @param s   the String to parse
     * @return    a Pick Resources message, or null of the data is garbled
     */
    public static SOCPickResources parseDataStr(String s)
    {
        final String ga; // the game name
        final int cl, // the amount of clay being picked
                  or, // the amount of ore being picked
                  sh, // the amount of sheep being picked
                  wh, // the amount of wheat being picked
                  wo, // the amount of wood being picked
                  uk; // the amount of unknown resources being picked

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cl = Integer.parseInt(st.nextToken());
            or = Integer.parseInt(st.nextToken());
            sh = Integer.parseInt(st.nextToken());
            wh = Integer.parseInt(st.nextToken());
            wo = Integer.parseInt(st.nextToken());
            uk = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPickResources(ga, cl, or, sh, wh, wo, uk);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCPickResources:game=" + game + "|resources=" + resources;
    }

}
