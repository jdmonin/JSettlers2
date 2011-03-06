/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.message;

import java.util.StringTokenizer;


/**
 * This message from server to client says whose turn it is.
 * Only the game's current player number should change; no other
 * game state is updated.
 *
 * @author Robert S. Thomas
 * @see SOCTurn
 */
public class SOCSetTurn extends SOCMessageTemplate1i
{
    /**
     * Create a SetTurn message.
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     */
    public SOCSetTurn(String ga, int pn)
    {
        super(SETTURN, ga, pn);
    }

    /**
     * @return the seat number
     */
    public int getPlayerNumber()
    {
        return p1;
    }

    /**
     * SETTURN sep game sep2 playerNumber
     *
     * @param ga  the name of the game
     * @param pn  the seat number
     * @return the command string
     */
    public static String toCmd(String ga, int pn)
    {
        return SETTURN + sep + ga + sep2 + pn;
    }

    /**
     * Parse the command String into a SetTurn message
     *
     * @param s   the String to parse: SETTURN sep game sep2 playerNumber
     * @return    a StartGame message, or null if the data is garbled
     */
    public static SOCSetTurn parseDataStr(String s)
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

        return new SOCSetTurn(ga, pn);
    }

}
