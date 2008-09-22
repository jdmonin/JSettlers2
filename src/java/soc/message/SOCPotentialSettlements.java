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

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message contains a list of potential settlements
 *
 * @author Robert S Thomas
 */
@SuppressWarnings("serial")
public class SOCPotentialSettlements extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * Player number
     */
    private int playerNumber;

    /**
     * List of potential settlements
     */
    private Vector<Integer> psList;

    /**
     * Create a SOCPotentialSettlements message.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ps  the list of potential settlements
     */
    public SOCPotentialSettlements(String ga, int pn, Vector<Integer> ps)
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
    public Vector<Integer> getPotentialSettlements()
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
    public static String toCmd(String ga, int pn, Vector<Integer> ps)
    {
        String cmd = POTENTIALSETTLEMENTS + sep + ga + sep2 + pn;
        Enumeration<Integer> senum = ps.elements();

        while (senum.hasMoreElements())
        {
            Integer number = senum.nextElement();
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
        Vector<Integer> ps = new Vector<Integer>();

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
        Enumeration<Integer> senum = psList.elements();

        while (senum.hasMoreElements())
        {
            Integer number = senum.nextElement();
            s += (Integer.toHexString(number.intValue()) + " ");
        }

        return s;
    }
}
