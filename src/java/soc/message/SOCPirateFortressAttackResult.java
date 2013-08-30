/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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
 * This message from server announces the results of the current player's pirate fortress
 * attack attempt: Pirates' defense strength, and number of ships lost (win/tie/loss).
 * Sent after client sends a {@link SOCSimpleRequest}({@link SOCSimpleRequest#SC_PIRI_FORT_ATTACK SC_PIRI_FORT_ATTACK}).
 *<P>
 * This message is sent out <b>after</b> any other related messages (see below), so that those
 * can be shown visually before any popup announcing the result.
 *<P>
 * Param 1: The pirates' defense strength (random 1 - 6) <br>
 * Param 2: The number of ships lost by the player: 0 if player wins, 1 if tie, 2 if pirates win
 *<P>
 * Used in the pirate islands scenario (_SC_PIRI).
 *<P>
 * Messages from server which precede this one, in this order:
 *<UL>
 * <LI> {@link SOCRemovePiece} for each removed ship, unless player wins
 * <LI> {@link SOCPlayerElement}({@link SOCPlayerElement#SCENARIO_WARSHIP_COUNT SCENARIO_WARSHIP_COUNT})
 *        if any of the player's warships were removed
 *      <P>&nbsp;<P>
 *      If player wins:
 * <LI> {@link SOCMoveRobber}, if all players' fortresses are recaptured,
 *        which removes the pirate fleet from the board (new pirate coordinate = 0)
 * <LI> {@link SOCPieceValue} for the fortress' reduced strength;
 *        if its new strength is 0, it is recaptured by the player
 * <LI> {@link SOCPutPiece}(SETTLEMENT) if the player wins for the last time,
 *        and they recapture the fortress
 *</UL> 
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPirateFortressAttackResult extends SOCMessageTemplate2i
{
    private static final long serialVersionUID = 2000L;

    /**
     * Create a SOCPirateFortressAttackResult message.
     *
     * @param ga  the name of the game
     * @param pirStrength  Pirates' defense strength
     * @param shipsLost  Number of ships lost by the player (0, 1, 2)
     */
    public SOCPirateFortressAttackResult(final String ga, final int pirStrength, final int shipsLost)
    {
        super(PIRATEFORTRESSATTACKRESULT, ga, pirStrength, shipsLost);
    }

    /**
     * PIRATEFORTRESSATTACKRESULT sep game sep2 pirStrength sep2 shipsLost
     *
     * @param ga  the name of the game
     * @param pirStrength  Pirates' defense strength
     * @param shipsLost  Number of ships lost by the player (0, 1, 2)
     * @return the command string
     */
    public static String toCmd(final String ga, final int pirStrength, final int shipsLost)
    {
        return SOCMessageTemplate2i.toCmd(PIRATEFORTRESSATTACKRESULT, ga, pirStrength, shipsLost);
    }

    /**
     * Parse the command string into a SOCPirateFortressAttackResult message.
     *
     * @param s   the String to parse; format: game sep2 pirStrength sep2 shipsLost
     * @return    a SOCPirateFortressAttackResult message, or null if parsing errors
     */
    public static SOCPirateFortressAttackResult parseDataStr(String s)
    {
        final String ga; // the game name
        final int ps; // pirate strength
        final int sl; // ships lost

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            ps = Integer.parseInt(st.nextToken());
            sl = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPirateFortressAttackResult(ga, ps, sl);
    }

    /**
     * Minimum version where this message type is used.
     * PIRATEFORTRESSATTACKRESULT introduced in 2.0.00 for the pirate islands scenario (_SC_PIRI).
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion()
    {
        return 2000;
    }

}
