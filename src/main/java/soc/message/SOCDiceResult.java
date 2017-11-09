/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2013,2017 Jeremy D Monin <jeremy@nand.net>
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
 * This message reports total of what was rolled on the dice.
 * The two individual dice amounts can be reported in a text message.
 *<P>
 * This is in response to a client player's {@link SOCRollDice} request.
 * Will always be followed by {@link SOCGameState} (7 might lead to discards
 * or moving the robber, etc.), and sometimes with further messages after that,
 * depending on the roll results and scenario/rules in effect.
 *<P>
 * When players gain resources on the roll, game members will be sent
 * {@link SOCDiceResultResources} for v2.0.00+ clients; older clients will
 * be sent {@link SOCPlayerElement SOCPlayerElement(GAIN, resType, amount)}
 * and a text message such as "Joe gets 3 sheep. Mike gets 1 clay."
 *<P>
 * Players who gain resources on the roll will be sent
 * {@link SOCPlayerElement SOCPlayerElement(SET, resType, amount)} messages
 * for all their new resource counts.  Before v2.0.00, those were sent to each
 * player in the game after a roll, not just those who gained resources.
 * Afterwards the current player (any client version) is sent their currently
 * held amounts for each resource as a group of <tt>SOCPlayerElement(pn, {@link #SET}, ...)</tt>
 * messages. Then, for each player who gained resources, their total {@link SOCResourceCount}
 * is sent to the game.
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
     * @param dr  the dice result
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
     * @param dr  the dice result
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
