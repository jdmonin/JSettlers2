/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2011 Jeremy D Monin <jeremy@nand.net>
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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;


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
 *<LI> HL: The hexes, from {@link SOCBoard#getHexLayout()}
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
 *<LI> v3: LH, maybe PL, maybe RH, maybe PH; LH is null before makeNewBoard is called
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
    private Hashtable layoutParts;

    /**
     * Create a SOCBoardLayout2 message
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param parts  the parts of the layout: int[] arrays or Strings or Integers.
     *               contents are not validated here, but improper contents
     *               may cause a ClassCastException later.
     */
    public SOCBoardLayout2(String ga, int bef, Hashtable parts)
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
     * @param hl   the hex layout
     * @param nl   the number layout
     * @param pl   the port layout, or null
     * @param rh   the robber hex
     */
    public SOCBoardLayout2(String ga, final int bef, int[] hl, int[] nl, int[] pl, int rh)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = new Hashtable();
        layoutParts.put("HL", hl);
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
        layoutParts = new Hashtable();
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
     * Get a layout part of type int[]
     * @param pkey the part's key name
     * @return the component, or null if no part named <tt>pkey</tt>.
     */
    public int[] getIntArrayPart(String pkey)
    {
        return (int[]) layoutParts.get(pkey);
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
    public static String toCmd(String ga, int bev, Hashtable parts)
    {
        StringBuffer cmd = new StringBuffer();
        cmd.append(BOARDLAYOUT2);
        cmd.append(sep);
        cmd.append(ga);
        cmd.append(sep2);
        cmd.append(bev);
        for (Enumeration e = parts.keys(); e.hasMoreElements(); )
        {
            String okey = (String) e.nextElement();
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
        Hashtable parts = new Hashtable();
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
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCBoardLayout2:game=");
        sb.append(game);
        sb.append("|bef=");
        sb.append(boardEncodingFormat);
        for (Enumeration e = layoutParts.keys(); e.hasMoreElements(); )
        {
            String okey = (String) e.nextElement();
            sb.append("|");
            sb.append(okey);
            sb.append("=");
            Object kv = layoutParts.get(okey);
            if (kv instanceof int[])
            {
                arrayIntoStringBuf((int[]) kv, sb);
            } else {
                sb.append(kv.toString());
            }
        }
        return sb.toString();
    }

}
