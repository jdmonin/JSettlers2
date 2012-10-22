/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2012 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas
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
 **/
package soc.message;

import java.util.Hashtable;
import java.util.StringTokenizer;

import soc.game.SOCBoard;


/**
 * This message contains the board's layout information and its encoding version.
 * The layout is represented as a series of named integer arrays and
 * named integer parameters.  These contain the hex layout, the
 * number layout, where the robber is, and possibly the port layout.
 * This message does not contain information about where the
 * player's pieces are on the board.
 *<P>
 * Names of typical parts of the board layout:
 *<UL>
 *<LI> HL: The hexes, from {@link SOCBoard#getHexLayout()}.
 *         For backwards compatibility, the values for {@link SOCBoard#WATER_HEX} and
 *         {@link SOCBoard#DESERT_HEX} are changed to their pre-v2.0.00 values in the
 *         constructor before sending over the network, and changed back in
 *         {@link #getIntArrayPart(String) getIntArrayPart("HL")}.
 *<LI> NL: The dice numbers, from {@link SOCBoard#getNumberLayout()}
 *<LI> RH: The robber hex, from {@link SOCBoard#getRobberHex()}, if &gt; 0
 *<LI> PL: The ports, from {@link SOCBoard#getPortsLayout()}
 *<LI> LH: The land hexes (v3 board encoding), from {@link soc.game.SOCBoardLarge#getLandHexLayout()}
 *<LI> PH: The pirate hex, from {@link soc.game.SOCBoardLarge#getPirateHex()}, if &gt; 0
 *</UL>
 * Board layout parts by board encoding version:
 *<UL>
 *<LI> v1: HL, NL, RH
 *<LI> v2: HL, NL, RH, maybe PL
 *<LI> v3: LH, maybe PL, maybe RH, maybe PH, never HL or NL; LH is null before makeNewBoard is called.
 *         The v3 board's land hexes may be logically grouped into several
 *         "land areas" (groups of islands, or subsets of islands).  Those
 *         areas are sent to the client via {@link SOCPotentialSettlements}.
 *</UL>
 * Unlike {@link SOCBoardLayout}, dice numbers here equal the actual rolled numbers.
 * <tt>SOCBoardLayout</tt> required a mapping/unmapping step.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @see SOCBoardLayout
 * @since 1.1.08
 */
public class SOCBoardLayout2 extends SOCMessage
    implements SOCMessageForGame
{
    /**
     * Minimum version (1.1.08) of client/server which recognize
     * and send VERSION_FOR_BOARDLAYOUT2.
     */
    public static final int VERSION_FOR_BOARDLAYOUT2 = 1108;

    /**
     * Hex land type numbers sent over the network.
     * Compare to {@link SOCBoard#WATER_HEX}, {@link SOCBoard#DESERT_HEX}.
     * @since 2.0.00
     */
    private static final int SENTLAND_WATER = 6, SENTLAND_DESERT = 0;

    /**
     * Name of game
     */
    private final String game;

    /**
     * Board layout encoding version, from {@link SOCBoard#getBoardEncodingFormat()}.
     */
    private final int boardEncodingFormat;

    /**
     * Contents are int[] or String (which may be int).
     */
    private Hashtable<String, Object> layoutParts;

    /**
     * Create a SOCBoardLayout2 message
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param parts  the parts of the layout: int[] arrays or Strings or Integers.
     *               contents are not validated here, but improper contents
     *               may cause a ClassCastException later.
     */
    public SOCBoardLayout2(String ga, int bef, Hashtable<String, Object> parts)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = parts;
    }

    /**
     * Create a SOCBoardLayout2 message for encoding format v1 or v2.
     * ({@link SOCBoard#BOARD_ENCODING_ORIGINAL} or {@link SOCBoard#BOARD_ENCODING_6PLAYER}.)
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param hl   the hex layout; not mapped yet, so the constructor will map it from the
     *               {@link soc.game.SOCBoard#getHexLayout()} value range
     *               to the BOARDLAYOUT2 message's value range.
     * @param nl   the number layout
     * @param pl   the port layout, or null
     * @param rh   the robber hex
     */
    public SOCBoardLayout2(final String ga, final int bef, final int[] hl, final int[] nl, final int[] pl, final int rh)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = new Hashtable<String, Object>();

        // Map the hex layout
        int[] hexLayout = new int[hl.length];
        for (int i = hl.length - 1; i >= 0; --i)
        {
            int h = hl[i];
            switch (h)
            {
            case SOCBoard.WATER_HEX:
                h = SENTLAND_WATER;   break;
            case SOCBoard.DESERT_HEX:
                h = SENTLAND_DESERT;  break;
            default:
                // leave unchanged
            }
            hexLayout[i] = h;
        }
        layoutParts.put("HL", hexLayout);

        layoutParts.put("NL", nl);
        if (pl != null)
            layoutParts.put("PL", pl);
        layoutParts.put("RH", new Integer(rh));
    }

    /**
     * Create a SOCBoardLayout2 message for encoding format v3.
     * ({@link SOCBoardLarge}, {@link SOCBoard#BOARD_ENCODING_LARGE}.)
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param lh   the land hex layout, or null if all water (before makeNewBoard is called)
     * @param pl   the port layout, or null
     * @param rh   the robber hex, or -1
     * @param ph   the pirate hex, or 0
     */
    public SOCBoardLayout2(String ga, final int bef, int[] lh, int[] pl, int rh, int ph)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = new Hashtable<String, Object>();
        if (lh != null)
            layoutParts.put("LH", lh);
        if (pl != null)
            layoutParts.put("PL", pl);
        if (rh > 0)
            layoutParts.put("RH", new Integer(rh));
        if (ph > 0)
            layoutParts.put("PH", new Integer(ph));
    }

    /**
     * Game name
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Game's board {@link SOCBoard#getBoardEncodingFormat()} version
     * @return the encoding format version
     */
    public int getBoardEncodingFormat()
    {
        return boardEncodingFormat;
    }

    /**
     * Get a layout part of type int[].
     *<P>
     * As a special case, when <tt>pkey</tt> is <tt>"HL"</tt>, desert and water hexes
     * will be mapped from the sent values to the SOCBoard values before returning the array.
     *
     * @param pkey the part's key name
     * @return the component, or null if no part named <tt>pkey</tt>.
     */
    public int[] getIntArrayPart(String pkey)
    {
        final int[] iap = (int[]) layoutParts.get(pkey);
        if (! pkey.equals("HL"))
            return iap;

        // Map "HL" (hex layout) from sent values to SOCBoard values
        int[] hl = new int[iap.length];
        for (int i = hl.length - 1; i >= 0; --i)
        {
            int h = iap[i];
            switch (h)
            {
            case SENTLAND_WATER:
                h = SOCBoard.WATER_HEX;   break;
            case SENTLAND_DESERT:
                h = SOCBoard.DESERT_HEX;  break;
            default:
                // leave unchanged
            }
            hl[i] = h;
        }
        return hl;
    }

    /**
     * Get a layout part of type int
     * @param pkey the part's key name
     * @return the part's value, or 0 if no part named <tt>pkey</tt>, or if it's not integer.
     */
    public int getIntPart(String pkey)
    {
        String sobj = (String) layoutParts.get(pkey);
        if (sobj == null)
            return 0;
        try
        {
            return Integer.parseInt(sobj);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    /**
     * Get a layout part of type String
     * @param pkey the part's key name
     * @return the part's value, or null if no part named <tt>pkey</tt>.
     */
    public String getStringPart(String pkey)
    {
        return (String) layoutParts.get(pkey);
    }

    /**
     * Formatted string to send this BOARDLAYOUT2 over the network.
     * BOARDLAYOUT2 sep game sep2 encoding <em>{item}</em>
     *<P>
     * Each <em>item</em>'s format is either:
     *<UL>
     *<LI>sep2 name sep2 value
     *<LI>sep2 name sep2 '['length sep2 value sep2 value ...
     *</UL>
     *
     * @return the command string
     */
    @Override
    public String toCmd()
    {
        return toCmd(game, boardEncodingFormat, layoutParts);
    }

    /**
     * Formatted string to send this BOARDLAYOUT2 over the network.
     * See {@link #toCmd()} for details.
     *
     * @return the command string
     */
    public static String toCmd(String ga, int bev, Hashtable<String, Object> parts)
    {
        StringBuffer cmd = new StringBuffer();
        cmd.append(BOARDLAYOUT2);
        cmd.append(sep);
        cmd.append(ga);
        cmd.append(sep2);
        cmd.append(bev);
        for (String okey : parts.keySet())
        {
            cmd.append(sep2);
            cmd.append(okey);
            cmd.append(sep2);
            Object ov = parts.get(okey);
            if (ov instanceof Integer)
            {
                cmd.append(Integer.toString(((Integer) ov).intValue()));
            } else if (ov instanceof int[])
            {
                int[] ovi = (int[]) ov;
                cmd.append("[");
                cmd.append(Integer.toString(ovi.length));
                for (int i = 0; i < ovi.length; ++i)
                {
                    cmd.append(sep2);
                    cmd.append(Integer.toString(ovi[i]));
                }
            } else {
                cmd.append(ov.toString());
            }
        }

        return cmd.toString();
    }

    /**
     * Parse the command string into a BoardLayout2 message
     *
     * @param s   the String to parse
     * @return    a BoardLayout2 message
     */
    public static SOCBoardLayout2 parseDataStr(String s)
    {
        String ga; // game name
        final int bef;   // board encoding format
        Hashtable<String, Object> parts = new Hashtable<String, Object>();
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();
            bef = Integer.parseInt(st.nextToken());
            while (st.hasMoreTokens())
            {
                String pname = st.nextToken();
                String pvalue = st.nextToken();
                if (pvalue.startsWith("["))
                {
                    int n = Integer.parseInt(pvalue.substring(1));
                    int[] pv = new int[n];
                    for (int i = 0; i < n; ++i)
                    {
                        pv[i] = Integer.parseInt(st.nextToken());
                    }
                    parts.put(pname, pv);
                } else {
                    parts.put(pname, pvalue);
                }
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBoardLayout2(ga, bef, parts);
    }

    /**
     * Render the SOCBoardLayout2 in human-readable form.
     * In version 2.0.00 and later, the land hexes and port layout (<tt>LH</tt>, <tt>PL</tt>)
     *   are in hexadecimal instead of base-10.
     * @return a human readable form of the message
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCBoardLayout2:game=");
        sb.append(game);
        sb.append("|bef=");
        sb.append(boardEncodingFormat);
        for (String okey : layoutParts.keySet())
        {
            sb.append("|");
            sb.append(okey);
            sb.append("=");
            Object kv = layoutParts.get(okey);
            if (kv instanceof int[])
            {
                arrayIntoStringBuf
                    ((int[]) kv, sb, ! (okey.equals("HL") || okey.equals("NL")));
            } else {
                sb.append(kv.toString());
            }
        }
        return sb.toString();
    }

}
