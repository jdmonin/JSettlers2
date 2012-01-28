/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2010-2012 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;


/**
 * This message contains a list of potential settlements.
 *<P>
 * In version 2.0.00 and newer:
 *<UL>
 *<LI> <tt>playerNumber</tt> can be -1
 *   to indicate this applies to all players.  For the
 *   SOCBoardLarge encoding only, this will also indicate
 *   the legal settlements should be set and the
 *   legal roads recalculated.
 *<LI> More than one "land area" (group of islands, or subset of islands)
 *   can be designated; can also require the player to start
 *   the game in a certain land area.
 *</UL>
 *
 * @author Robert S Thomas
 */
public class SOCPotentialSettlements extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * In version 2.0.00 and above, playerNumber can be -1
     * to indicate all players have these potential settlements.
     * @since 2.0.00
     */
    public static final int VERSION_FOR_PLAYERNUM_ALL = 2000;

    /**
     * Name of game
     */
    private String game;

    /**
     * Player number, or -1 for all players (version 2.0.00 or newer)
     */
    private int playerNumber;

    /**
     * List of potential settlements
     */
    private Vector psList;

    /**
     * How many land areas are on this board?
     * Always 1 before version 2.0.00.
     * @since 2.0.00
     */
    public final int areaCount;

    /**
     * Which land area number is {@link #psList} within {@link #landAreasLegalNodes}?
     * 0 if none, because the game has started.
     *<P>
     * Not used if {@link #areaCount} == 1.
     * @since 2.0.00
     */
    public final int startingLandArea;

    /**
     * Each land area's legal node coordinates.
     * Index 0 is unused.
     *<P>
     * Null if {@link #areaCount} == 1.
     * @since 2.0.00
     * @see #startingLandArea
     */
    public final HashSet[] landAreasLegalNodes;

    /**
     * Create a SOCPotentialSettlements message.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for all players in v2.0.00
     *   or newer (see <tt>ps</tt> for implications)
     * @param ps  the list of potential settlement nodes; if <tt>pn == -1</tt>
     *   and the client and server are at least
     *   version 2.0.00 ({@link #VERSION_FOR_PLAYERNUM_ALL}),
     *   <tt>ps</tt> also is the list of legal settlements.
     */
    public SOCPotentialSettlements(String ga, int pn, Vector ps)
    {
        messageType = POTENTIALSETTLEMENTS;
        game = ga;
        playerNumber = pn;
        psList = ps;
        areaCount = 1;
        landAreasLegalNodes = null;
        startingLandArea = 1;
    }

    /**
     * Create a SOCPotentialSettlements message with multiple land areas,
     * each of which have a set of legal settlements, but only one of which
     * has potential settlements at this time.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for all players
     * @param pan  Potential settlements' land area number, or 0 if the
     *             game has started, so none of the land areas equals the
     *             list of potential settlements.  In that case use <tt>ln[0]</tt>
     *             to hold the potential settlements node list.
     * @param lan  Each land area's legal node lists.
     *             List number <tt>pan</tt> will be sent as the list of
     *             potential settlements.
     *            If none of the land areas equals the list of potential
     *            settlements, because the game has started,
     *            use index 0 for that potentials list.
     *            Otherwise index 0 is unused (<tt>null</tt>).
     * @throws IllegalArgumentException  if <tt>ln[pan] == null</tt>,
     *            or if <tt>ln[<i>i</i>]</tt> == <tt>null</tt> for any <i>i</i> &gt; 0
     */
    public SOCPotentialSettlements(String ga, int pn, final int pan, HashSet[] lan)
        throws IllegalArgumentException
    {
        messageType = POTENTIALSETTLEMENTS;
        game = ga;
        playerNumber = pn;
        psList = new Vector(lan[pan]);
        areaCount = lan.length - 1;
        landAreasLegalNodes = lan;
        startingLandArea = pan;

        // consistency-check land areas
        for (int i = 1; i < lan.length; ++i)
            if (lan[i] == null)
                throw new IllegalArgumentException();
        if (psList == null)
            throw new IllegalArgumentException();
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
     * POTENTIALSETTLEMENTS formatted command, for a message with 1 or multiple land areas.
     * Format will be either {@link #toCmd(String, int, Vector)}
     * or {@link #toCmd(String, int, int, HashSet[])}.
     *
     * @return the command String
     */
    public String toCmd()
    {
        if (landAreasLegalNodes == null)
            return toCmd(game, playerNumber, psList);
        else
            return toCmd(game, playerNumber, startingLandArea, landAreasLegalNodes);
    }

    /**
     * <tt>toCmd</tt> for a SOCPotentialSettlements message with 1 land area.
     *<P><tt>
     * POTENTIALSETTLEMENTS sep game sep2 playerNumber sep2 psList
     *</tt>
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
     * <tt>toCmd</tt> for a SOCPotentialSettlements message with multiple land areas,
     * each of which have a set of legal settlements, but only one of which
     * has potential settlements at this time.
     *<P><tt>
     * POTENTIALSETTLEMENTS sep game sep2 playerNumber sep2 psList
     *    sep2 NA sep2 <i>(number of areas)</i> sep2 PAN sep2 <i>(pan)</i>
     *    { sep2 LA<i>#</i> sep2 legalNodesList }+
     *</tt>
     * LA# is the land area number "LA1" or "LA2".
     * None of the LA#s will be PAN's <i>(pan)</i> number.
     *
     * @param ga  name of the game
     * @param pn  the player number, or -1 for all players
     * @param pan  Potential settlements' land area number, or 0 if the
     *             game has started, so none of the land areas equals the
     *             list of potential settlements.  In that case use <tt>lan[0]</tt>
     *             to hold the potential settlements node list.
     * @param lan  Each land area's legal node lists.
     *             List number <tt>pan</tt> will be sent as the list of
     *             potential settlements.
     *            If none of the land areas equals the list of potential
     *            settlements, because the game has started,
     *            use index 0 for that potentials list.
     *            Otherwise index 0 is unused (<tt>null</tt>).
     * @return   the command string
     */
    public static String toCmd(String ga, int pn, final int pan, final HashSet[] lan)
    {
        StringBuffer cmd = new StringBuffer(POTENTIALSETTLEMENTS + sep + ga + sep2 + pn);

        Iterator siter = lan[pan].iterator();
        while (siter.hasNext())
        {
            int number = ((Integer) siter.next()).intValue();
            cmd.append(sep2);
            cmd.append(number);
        }

        cmd.append(sep2);
        cmd.append("NA");
        cmd.append(sep2);
        cmd.append(lan.length - 1);

        cmd.append(sep2);
        cmd.append("PAN");
        cmd.append(sep2);
        cmd.append(pan);

        for (int i = 1; i < lan.length; ++i)
        {
            if (i == pan)
                continue;  // don't re-send the potentials list

            cmd.append(sep2);
            cmd.append("LA");
            cmd.append(i);

            Iterator pnIter = lan[i].iterator();
            while (pnIter.hasNext())
            {
                cmd.append(sep2);
                int number = ((Integer) pnIter.next()).intValue();
                cmd.append(number);
            }
        }

        return cmd.toString();

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
        HashSet[] las = null;
        int pan = 0;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            boolean hadNA = false;

            while (st.hasMoreTokens())
            {
                String tok = st.nextToken();
                if (tok.equals("NA"))
                {
                    hadNA = true;
                    break;
                }
                ps.addElement(new Integer(Integer.parseInt(tok)));
            }

            if (hadNA)
            {
                // If more than 1 land area, the potentialSettlements
                // numbers will be followed by:
                // NA, 3, PAN, 1, LA2, ..., LA3, ...
                // None of the LA#s will be PAN's number.

                final int numArea = Integer.parseInt(st.nextToken());
                las = new HashSet[numArea + 1];

                String tok = st.nextToken();
                if (! tok.equals("PAN"))
                    return null;
                pan = Integer.parseInt(st.nextToken());
                if (pan < 0)
                    return null;

                if (st.hasMoreTokens())
                {
                    tok = st.nextToken();  // "LA2", "LA3", ...
                } else {
                    // should not occur, but allow if 1 area
                    tok = null;
                    if ((numArea > 1) || (pan != 1))
                        return null;
                }

                // Loop for numAreas, starting with tok == "LA#"
                while (st.hasMoreTokens())
                {
                    if (! tok.startsWith("LA"))
                        return null;
                    final int areaNum = Integer.parseInt(tok.substring(2));
                    HashSet ls = new HashSet();

                    // Loop for node numbers, until next "LA#"
                    while (st.hasMoreTokens())
                    {
                        tok = st.nextToken();
                        if (tok.startsWith("LA"))
                            break;
                        ls.add(new Integer(Integer.parseInt(tok)));
                    }
                    las[areaNum] = ls;
                }

                if (las[pan] == null)
                    las[pan] = new HashSet(ps);
                else
                    return null;  // not a well-formed message

                // Make sure all LAs are defined
                for (int i = 1; i <= numArea; ++i)
                    if (las[i] == null)
                        return null;
            }
        }
        catch (Exception e)
        {
            return null;
        }

        if (las == null)
            return new SOCPotentialSettlements(ga, pn, ps);
        else
            return new SOCPotentialSettlements(ga, pn, pan, las);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer s = new StringBuffer
            ("SOCPotentialSettlements:game=" + game + "|playerNum=" + playerNumber + "|list=");
        Enumeration senum = psList.elements();

        while (senum.hasMoreElements())
        {
            Integer number = (Integer) senum.nextElement();
            s.append(Integer.toHexString(number.intValue()));
            s.append(' ');
        }

        if (landAreasLegalNodes != null)
        {
            s.append("|pan=");
            s.append(startingLandArea);
            for (int i = 1; i < landAreasLegalNodes.length; ++i)
            {
                s.append("|la");
                s.append(i);
                s.append('=');
                if (i == startingLandArea)
                {
                    s.append("(psList)");
                    continue;
                }
    
                Iterator laIter = landAreasLegalNodes[i].iterator();
                while (laIter.hasNext())
                {
                    int number = ((Integer) laIter.next()).intValue();
                    s.append(Integer.toHexString(number));
                    s.append(' ');
                }
            }
        }

        return s.toString();
    }
}
