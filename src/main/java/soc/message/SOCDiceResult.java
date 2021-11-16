/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2013,2017-2018,2021 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCResourceConstants;  // for javadocs only


/**
 * Server's report of the total amount rolled on the dice this turn.
 * The two individual dice amounts can be reported in a text message.
 *<P>
 * This is in response to a client player's {@link SOCRollDice} request.
 * Will sometimes be followed with various messages to entire game and/or
 * to some players, depending on the roll results and scenario/rules in effect.
 * The last data message of sequence sent to entire game is always {@link SOCGameState}
 * (rolling a 7 might lead to discards or moving the robber, etc.)
 *<P>
 * The guideline is that the "public" sequence ends with the new game state message,
 * then any new state text for human clients, then any prompt for action
 * sent to individual player clients in the new state.
 * Any special resource (cloth) distributed as roll results, or any report of action happening
 * (fleet battle lost/won), is sent before the state message.
 *
 *<H4>Sequence details</H4>
 *
 * When players gain resources on the roll, game members will be sent
 * {@link SOCDiceResultResources} if v2.0.00 or newer; older clients will
 * be sent {@link SOCPlayerElement SOCPlayerElement(GAIN, resType, amount)}
 * and a text message such as "Joe gets 3 sheep. Mike gets 1 clay."
 *<P>
 * Each player who gained resources on the roll (any client version) is sent their currently
 * held amounts for each resource as <tt>{@link SOCPlayerElements}(pn, {@link SOCPlayerElement#SET SET}, ...)</tt>
 * or a group of <tt>{@link SOCPlayerElement}(pn, SET, ...)</tt> messages.
 * When client receives such a 5-element {@code SOCPlayerElements} in state {@link SOCGame#ROLL_OR_CARD},
 * they may want to clear their {@link SOCResourceConstants#UNKNOWN} amount to 0 in case it has drifted.
 *<P>
 * Before v2.0.00 those were sent as a {@link SOCPlayerElement} group
 * to each player in the game after a roll, not just those who gained resources, followed by
 * sending the game a {@link SOCResourceCount} with their new total resource count.
 *<P>
 * When 7 is rolled and players must discard, then instead, {@code SOCDiceResult}
 * is followed by a {@link SOCGameState}({@link SOCGame#WAITING_FOR_DISCARDS}) announcement,
 * then a {@link SOCDiscardRequest} prompt to each affected player.
 * See {@link SOCDiscard} for player response and the next part of that sequence.
 *
 *<H4>End of sequence</H4>
 *
 * As noted above, the message sequence to the entire game always ends with {@code SOCGameState}.
 * That might be followed with {@link SOCGameServerText} for human players to read,
 * and/or messages sent privately to a player such as {@link SOCDiscardRequest}
 * (which doesn't need to be "public" because entire game knew the number of cards held
 * when the 7 was rolled).
 *
 * @author Robert S. Thomas
 */
public class SOCDiceResult extends SOCMessageTemplate1i
{
    /** Class converted for v1.1.00 to use SOCMessageTemplate1i.
     *  Over the network, fields are unchanged since v1.0.0 or earlier, per git and old cvs history. -JM
     */
    private static final long serialVersionUID = 1100L;

    /**
     * Create a DiceResult message.
     *
     * @param ga  the name of the game
     * @param dr  the dice result, from {@link soc.game.SOCGame#getCurrentDice()}
     */
    public SOCDiceResult(String ga, int dr)
    {
        super (DICERESULT, ga, dr);
    }

    /**
     * @return the dice result
     */
    public int getResult()
    {
        return p1;
    }

    /**
     * DICERESULT sep game sep2 result
     *
     * @param ga  the name of the game
     * @param dr  the dice result, from {@link soc.game.SOCGame#getCurrentDice()}
     * @return the command string
     */
    public static String toCmd(String ga, int dr)
    {
        return DICERESULT + sep + ga + sep2 + dr;
    }

    /**
     * Parse the command String into a DiceResult message
     *
     * @param s   the String to parse: DICERESULT sep game sep2 result
     * @return    a DiceResult message, or null if the data is garbled
     */
    public static SOCDiceResult parseDataStr(String s)
    {
        String ga; // the game name
        int dr; // the dice result

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            dr = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCDiceResult(ga, dr);
    }

}
