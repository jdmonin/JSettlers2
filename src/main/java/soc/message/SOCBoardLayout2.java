/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2009-2014,2016-2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;  // for javadocs
import soc.game.SOCScenario;    // for javadocs
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;
import soc.util.DataUtils;


/**
 * This message contains the board's encoding version and layout information
 * in a flexible format to allow for expansions and 6-player extensions.
 * The layout is represented as a series of named "Layout Parts":
 * Integer arrays and integer parameters.  These contain the hex layout, the
 * number layout, where the robber is, and possibly the port layout.
 * This message does not contain information about where the
 * player's pieces are on the board.
 *<P>
 * <H4>Typical parts of the board layout:</H4>
 *
 * Not all layouts or {@link #getBoardEncodingFormat()}s include all these parts.
 * See the list at {@link SOCBoardLarge#getAddedLayoutPart(String)} for each part's details.
 *
 *<UL>
 *<LI> HL: The land hex layout (classic v1 or v2 board encoding; not sent if <tt>LH</tt> is sent).
 *         See note below on value mapping.
 *<LI> NL: The dice numbers
 *<LI> RH: The robber hex, if &gt; 0
 *<LI> PL: The ports layout
 *<LI> PH: The pirate hex, if &gt; 0
 *<LI> LH: The land hexes (v3 board encoding).<br>
 *         If grouped into "land areas", those areas are sent to the client via {@link SOCPotentialSettlements}.
 *<LI> PX: Players excluded from settling these land areas (usually none)
 *<LI> RX: Robber excluded from these land areas (usually none)
 *</UL>
 *
 *<H4>Other Layout Parts:</H4>
 * A few game scenarios in jsettlers v2.0.00 and newer may add other parts; call {@link #getAddedParts()} for them
 * at the client. See {@link SOCBoardLarge#getAddedLayoutPart(String)} for the list of <B>all Layout Parts</B>.
 *<P>
 * <H4>Board Layout Parts by board encoding version:</H4>
 *<UL>
 *<LI> v1: HL, NL, RH
 *<LI> v2: HL, NL, RH, maybe PL
 *<LI> v3: LH, maybe PL, maybe RH, maybe PH, maybe VS, never HL or NL. <BR>
 *         Sometimes (for game scenarios) one or more of: PX, RX, CE, CV, LS, PP, VE, AL, N1, N2, N3. <BR>
 *         LH is null before makeNewBoard is called.
 *</UL>
 * Unlike {@link SOCBoardLayout}, dice numbers here equal the actual rolled numbers.
 * <tt>SOCBoardLayout</tt> required a mapping/unmapping step.
 *<P>
 * For backwards compatibility, the <tt>HL</tt> values for {@link SOCBoard#WATER_HEX} and
 * {@link SOCBoard#DESERT_HEX} are changed to their pre-v2.0 values in the
 * constructor before sending over the network, and changed back at the
 * client via {@link #getIntArrayPart(String) getIntArrayPart("HL")}.
 * Value mapping is not needed for <tt>LH</tt> introduced in v2.0.00
 * for {@link SOCBoard#BOARD_ENCODING_LARGE} ("v3").
 *
 *<H4>Optimization:</H4>
 * For v2.0.00 and newer servers and clients ({@link SOCBoardLayout#VERSION_FOR_OMIT_IF_EMPTY_NEW_GAME}):
 *<P>
 * For original 4- and 6-player board encodings (v1 and v2, not v3 sea board), and if
 * the game is still forming (state {@link soc.game.SOCGame#NEW}),
 * client already has data for the empty board. If so, no board layout message
 * is sent to the client.
 *<P>
 * If Sea Board, the layout must be sent, to set the "VS" part and other data useful for initial rendering.
 * The empty-board message for Sea Board is compact, omitting the sea hexes.
 *
 * @author Jeremy D Monin <jeremy@nand.net>
 * @see SOCBoardLayout
 * @since 1.1.08
 */
public class SOCBoardLayout2 extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;

    /**
     * Known layout part keys.  To be ignored by {@link #getAddedParts()} because the client calls
     * specific {@link SOCBoardLarge} methods for each of these, instead of generically calling
     * {@link SOCBoardLarge#setAddedLayoutParts(HashMap)}.  See {@code getAddedParts()} javadoc
     * for details.
     * @since 2.0.00
     */
    private final String[] KNOWN_KEYS = { "HL", "NL", "RH", "PL", "LH", "PH", "PX", "RX", "CV" };

    /**
     * Minimum version (1.1.08) of client/server which recognize
     * and send VERSION_FOR_BOARDLAYOUT2.
     */
    public static final int VERSION_FOR_BOARDLAYOUT2 = 1108;

    /**
     * These hex land type values are remapped when sent over the network in layout part <tt>HL</tt>.
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
     * Some are optional depending on game options and scenario;
     * see class javadoc, {@link #getAddedParts()}, {@link #KNOWN_KEYS},
     * {@link SOCBoardLarge#getAddedLayoutParts()},
     * and {@link soc.server.SOCBoardAtServer#setAddedLayoutPart(String, int[])}.
     */
    private Map<String, Object> layoutParts;

    /**
     * Create a SOCBoardLayout2 message; see class javadoc for parts' meanings.
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param parts  the parts of the layout: int[] arrays or Strings or Integers.
     *               contents are not validated here, but contents not matching their keys' documented type
     *               may cause a ClassCastException later.
     */
    public SOCBoardLayout2(String ga, int bef, Map<String, Object> parts)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = parts;
    }

    /**
     * Create a SOCBoardLayout2 message for encoding format v1 or v2; see class javadoc for parameters' meanings.
     * ({@link SOCBoard#BOARD_ENCODING_ORIGINAL} or {@link SOCBoard#BOARD_ENCODING_6PLAYER}.)
     *
     * @param ga   the name of the game
     * @param bef  the board encoding format number, from {@link SOCBoard#getBoardEncodingFormat()}
     * @param hl   the hex layout; not mapped yet, so the constructor will map it from the
     *               {@link SOCBoard#getHexLayout()} value range
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
        layoutParts = new HashMap<String, Object>();

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
        layoutParts.put("RH", Integer.valueOf(rh));
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
     * @param px   the player exclusion land areas, or null, from {@link SOCBoardLarge#getPlayerExcludedLandAreas()}
     * @param rx   the robber exclusion land areas, or null, from {@link SOCBoardLarge#getRobberExcludedLandAreas()}
     * @param other  any other layout parts to add, or null; see {@link #getAddedParts()}.
     *             Please make sure that new keys don't conflict with ones already listed in the class javadoc.
     */
    public SOCBoardLayout2
        (final String ga, final int bef,
         final int[] lh, final int[] pl, final int rh, final int ph, final int[] px, final int[] rx,
         final Map<String, int[]> other)
    {
        messageType = BOARDLAYOUT2;
        game = ga;
        boardEncodingFormat = bef;
        layoutParts = new HashMap<String, Object>();
        if (lh != null)
            layoutParts.put("LH", lh);
        if (pl != null)
            layoutParts.put("PL", pl);
        if (rh > 0)
            layoutParts.put("RH", Integer.valueOf(rh));
        if (ph > 0)
            layoutParts.put("PH", Integer.valueOf(ph));
        if (px != null)
            layoutParts.put("PX", px);
        if (rx != null)
            layoutParts.put("RX", rx);

        if (other != null)
            layoutParts.putAll(other);
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
     * Get all the generic Added Layout Parts, which won't each need special method calls to
     * add them to a {@link SOCBoardLarge}.
     *<P>
     * Some game scenarios may add other layout part keys and int[] values.
     * For example, scenario {@link SOCScenario#K_SC_PIRI SC_PIRI} adds
     * <tt>"PP" = { 0x..., 0x... }</tt> for the fixed Pirate Path.
     * This is a generic mechanism for adding to layout information
     * without continually changing {@code SOCBoardLayout2} or {@link SOCBoardLarge}.
     *<P>
     * The returned keys of those parts will be none of the ones in the "typical parts of the board layout"
     * listed in this class javadoc, unlike {@link SOCBoardLarge#getAddedLayoutParts()} which includes any key
     * added by the options or scenario.  The difference is that the client calls
     * {@link SOCBoardLarge#setAddedLayoutParts(HashMap)} once for all the parts returned here.  For the other keys
     * listed in the class javadoc, such as {@code "CV"}, the client instead calls part-specific methods such as
     * {@link SOCBoardLarge#setVillageAndClothLayout(int[])} to set up the board when the server sends its layout.
     * @return  Other added parts' keys and values, or null if none
     * @since 2.0.00
     */
    public HashMap<String, int[]> getAddedParts()
    {
        HashMap<String, int[]> added = null;

        for (String key : layoutParts.keySet())
        {
            boolean known = false;
            for (String knk : KNOWN_KEYS)
            {
                if (key.equals(knk))
                {
                    known = true;
                    break;
                }
            }

            if (! known)
            {
                if (added == null)
                    added = new HashMap<String, int[]>();
                added.put(key, (int[]) layoutParts.get(key));
            }
        }

        return added;
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
    public static String toCmd(final String ga, final int bef, final Map<String, Object> parts)
    {
        StringBuffer cmd = new StringBuffer();
        cmd.append(BOARDLAYOUT2);
        cmd.append(sep);
        cmd.append(ga);
        cmd.append(sep2);
        cmd.append(bef);
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
        HashMap<String, Object> parts = new HashMap<String, Object>();
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
     * Contents of this message in Protobuf format, as a OneOf field of {@link Message.FromServer}.
     * This proto message and its List<Integer> map values are expensive to build, but
     * {@link #makeProto(boolean)} will cache it after the first call.
     * @since 3.0.00
     */
    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.BoardLayout.Builder b
            = GameMessage.BoardLayout.newBuilder();
        b.setLayoutEncodingValue(boardEncodingFormat);

        GameMessage.BoardLayout._BoardLayoutPart.Builder pb
            = GameMessage.BoardLayout._BoardLayoutPart.newBuilder();
        for (String okey : layoutParts.keySet())
        {
            Object ov = layoutParts.get(okey);
            if (ov instanceof Integer)
                pb.setIVal(((Integer) ov).intValue());
            else if (ov instanceof int[])
            {
                Data._IntArray.Builder ib = Data._IntArray.newBuilder();
                ib.addAllArr(DataUtils.toList((int[]) ov));
                pb.setIArr(ib);
            }
            else if (ov instanceof String)
                pb.setSVal((String) ov);
            else
                pb.setSVal(ov.toString());

            b.putParts(okey, pb.build());
        }

        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setBoardLayout(b);

        return Message.FromServer.newBuilder().setGameMessage(gb).build();
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
                DataUtils.arrayIntoStringBuf
                    ((int[]) kv, sb, ! (okey.equals("HL") || okey.equals("NL")));
            } else {
                sb.append(kv.toString());
            }
        }
        return sb.toString();
    }

}
