/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message reports total of what was rolled on the dice.
 * The two individual dice amounts can be reported in a text message.
 *
 * @author Robert S. Thomas
 */
@SuppressWarnings("serial")
public class SOCDiceResult extends SOCMessageTemplate1i
{
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
