/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2012,2014 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
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

import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * This message contains a list of legal edges (ships or roads).
 * Used when joining a game in progress with the large sea board.
 * @since 2.0.00
 */
public class SOCLegalEdges extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * Name of game
     */
    public final String game;

    /**
     * Player number, or -1 for all players
     */
    public final int playerNumber;

    /**
     * True for ships, false for roads
     */
    public final boolean edgesAreShips;

    /**
     * List of legal edges
     */
    public final HashSet<Integer> leList;

    /**
     * Create a SOCLegalEdges message (roads or ships).
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for all players
     * @param areShips  True for legal ships, false for legal roads
     * @param le  the list of legal edges
     */
    public SOCLegalEdges(final String ga, final int pn, final boolean areShips, final HashSet<Integer> le)
    {
        messageType = LEGALEDGES;
        game = ga;
        playerNumber = pn;
        leList = le;
        edgesAreShips = areShips;
    }

    /**
     * @return the game name
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the formatted command String
     */
    @Override
    public String toCmd()
    {
        return toCmd(game, playerNumber, edgesAreShips, leList);
    }

    /**
     * <tt>toCmd</tt> for a SOCLegalEdges message.
     *<P><tt>
     * LEGALEDGES sep game sep2 playerNumber sep2 areShips sep2 leList*
     *</tt><BR>
     * * leList elements are hexadecimal.
     *
     * @param ga  the game name
     * @param pn  the player number, or -1 for all players
     * @param areShips  True for legal ships, false for legal roads
     * @param le  the list of legal edges
     * @return    the command string
     */
    public static String toCmd(final String ga, final int pn, final boolean areShips, final HashSet<Integer> le)
    {
        StringBuilder cmd = new StringBuilder();
        cmd.append(LEGALEDGES);
        cmd.append(sep);
        cmd.append(ga);
        cmd.append(sep2);
        cmd.append(pn);
        cmd.append(sep2);
        cmd.append(areShips ? 't' : 'f');

        for (int edge : le)
        {
            cmd.append(sep2);
            cmd.append(Integer.toHexString(edge));
        }

        return cmd.toString();
    }

    /**
     * Parse the command String into a SOCLegalEdges message
     *
     * @param s   the String to parse
     * @return    a LEGALEDGES message, or null of the data is garbled
     */
    public static SOCLegalEdges parseDataStr(String s)
    {
        String ga;
        int pn;
        boolean areShips;
        HashSet<Integer> le;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            areShips = (st.nextToken().equals("t"));
            le = new HashSet<Integer>();

            while (st.hasMoreTokens())
                le.add(Integer.valueOf(st.nextToken(), 16));
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLegalEdges(ga, pn, areShips, le);
    }

    /**
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder
            ("SOCLegalEdges:game=" + game + "|playerNum=" + playerNumber + "|areShips=" + edgesAreShips + "|list=");
        for (int edge : leList)
        {
            sb.append(Integer.toHexString(edge));
            sb.append(' ');
        }

        return sb.toString();
    }
}
