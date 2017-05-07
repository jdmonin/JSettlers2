/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;

import java.util.StringTokenizer;


/**
 * This message from client says which resources the player picked for a
 * Discovery/Year of Plenty card, or (v2.0.00+) free resources from the Gold Hex
 * or other events in Sea Board scenarios.
 *<P>
 * For <B>Discovery/Year of Plenty,</B> this is the client response to server's
 * {@link SOCGameState GAMESTATE}({@link SOCGame#WAITING_FOR_DISCOVERY WAITING_FOR_DISCOVERY}).
 *<P>
 * For <B>Gold Hex</B> picks or other Sea Board scenarios, this is the Client response to server's
 * {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES}).
 * (Same prompt/response pattern as {@link SOCDiscardRequest} / {@link SOCDiscard}.)
 *<BR>
 * If the resource count is wrong, the server will resend {@code SOCSimpleRequest(PROMPT_PICK_RESOURCES)}
 * with the required resource count.
 *<BR>
 * Otherwise: <BR>
 * The server will report the picked resources to the other players via {@link SOCPlayerElement} and text, but
 * will not send a {@code SOCPickResources} message to other players. The server will also send all players a
 * {@link SOCPlayerElement}({@link SOCPlayerElement#NUM_PICK_GOLD_HEX_RESOURCES NUM_PICK_GOLD_HEX_RESOURCES}, 0).
 *<P>
 * Also used in scenario SC_PIRI when player wins a free resource for defeating the
 * pirate fleet attack at a dice roll.
 *<P>
 * Before v2.0.00, this message class was called {@code SOCDiscoveryPick}.
 *
 * @author Robert S. Thomas
 */
public class SOCDiscoveryPick extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

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
     * @since 2.0.00
     */
    public SOCDiscoveryPick(String ga, int cl, int or, int sh, int wh, int wo)
    {
        this(ga, new SOCResourceSet(cl, or, sh, wh, wo, 0));
    }

    /**
     * Create a DiscoveryPick message.
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked
     */
    public SOCDiscoveryPick(String ga, SOCResourceSet rs)
    {
        messageType = DISCOVERYPICK;
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
     * @return the set of picked resources
     */
    public SOCResourceSet getResources()
    {
        return resources;
    }

    /**
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2 wheat sep2 wood
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, resources);
    }

    /**
     * Build a command string for this message.
     *
     * @param ga  the name of the game
     * @param rs  the resources being picked
     * @return the command string
     */
    public static String toCmd(String ga, SOCResourceSet rs)
    {
        String cmd = DISCOVERYPICK + sep + ga;

        for (int i = SOCResourceConstants.CLAY; i <= SOCResourceConstants.WOOD;
                i++)
        {
            cmd += (sep2 + rs.getAmount(i));
        }

        return cmd;
    }

    /**
     * PICKRESOURCES sep game sep2 clay sep2 ore sep2 sheep sep2 wheat sep2 wood
     *
     * @param ga  the name of the game
     * @param cl  the amount of clay being picked
     * @param or  the amount of ore being picked
     * @param sh  the amount of sheep being picked
     * @param wh  the amount of wheat being picked
     * @param wo  the amount of wood being picked
     * @return the command string
     * @since 2.0.00
     */
    public static String toCmd(String ga, int cl, int or, int sh, int wh, int wo)
    {
        return DISCOVERYPICK + sep + ga + sep2 + cl + sep2 + or + sep2 + sh + sep2 + wh + sep2 + wo;
    }

    /**
     * Parse the command String into a DiscoveryPick message
     *
     * @param s   the String to parse
     * @return    a DiscoveryPick message, or null if the data is garbled
     */
    public static SOCDiscoveryPick parseDataStr(String s)
    {
        final String ga; // the game name
        final int cl, // the amount of clay being picked
                  or, // the amount of ore being picked
                  sh, // the amount of sheep being picked
                  wh, // the amount of wheat being picked
                  wo; // the amount of wood being picked

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            cl = Integer.parseInt(st.nextToken());
            or = Integer.parseInt(st.nextToken());
            sh = Integer.parseInt(st.nextToken());
            wh = Integer.parseInt(st.nextToken());
            wo = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDiscoveryPick(ga, cl, or, sh, wh, wo);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        return "SOCDiscoveryPick:game=" + game + "|resources=" + resources;
    }

}
