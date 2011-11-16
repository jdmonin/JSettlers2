/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010-2011 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message contains a list of potential settlements
 *
 * @author Robert S Thomas
 */
public class SOCPotentialSettlements extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * In version 1.2.00 and above, playerNumber can be -1
     * to indicate all players have these potential settlements.
     * @since 1.2.00
     */
    public static final int VERSION_FOR_PLAYERNUM_ALL = 1200;

    /**
     * Name of game
     */
    private String game;

    /**
     * Player number, or -1 for all players (version 1.2.00 or newer)
     */
    private int playerNumber;

    /**
     * List of potential settlements
     */
    private Vector psList;

    /**
     * Create a SOCPotentialSettlements message.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ps  the list of potential settlements
     */
    public SOCPotentialSettlements(String ga, int pn, Vector ps)
    {
        messageType = POTENTIALSETTLEMENTS;
        game = ga;
        playerNumber = pn;
        psList = ps;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the list of potential settlements
     */
    public Vector getPotentialSettlements()
    {
        return psList;
    }

    /**
     * POTENTIALSETTLEMENTS sep game sep2 playerNumber sep2 psList
     *
     * @return the command String
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, psList);
    }

    /**
     * POTENTIALSETTLEMENTS sep game sep2 playerNumber sep2 psList
     *
     * @param ga  the game name
     * @param pn  the player number
     * @param ps  the list of potential settlements
     * @return    the command string
     */
    public static String toCmd(String ga, int pn, Vector ps)
    {
        String cmd = POTENTIALSETTLEMENTS + sep + ga + sep2 + pn;
        Enumeration senum = ps.elements();

        while (senum.hasMoreElements())
        {
            Integer number = (Integer) senum.nextElement();
            cmd += (sep2 + number);
        }

        return cmd;
    }

    /**
     * Parse the command String into a PotentialSettlements message
     *
     * @param s   the String to parse
     * @return    a PotentialSettlements message, or null of the data is garbled
     */
    public static SOCPotentialSettlements parseDataStr(String s)
    {
        String ga;
        int pn;
        Vector ps = new Vector();

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());

            while (st.hasMoreTokens())
            {
                ps.addElement(new Integer(Integer.parseInt(st.nextToken())));
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCPotentialSettlements(ga, pn, ps);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCPotentialSettlements:game=" + game + "|playerNum=" + playerNumber + "|list=";
        Enumeration senum = psList.elements();

        while (senum.hasMoreElements())
        {
            Integer number = (Integer) senum.nextElement();
            s += (Integer.toHexString(number.intValue()) + " ");
        }

        return s;
    }
}
