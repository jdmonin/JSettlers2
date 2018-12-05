/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2012,2014,2016-2018 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoard;
import soc.game.SOCGame;  // for javadocs
import soc.util.DataUtils;


/**
 * This message contains the board layout information: The hex layout, the
 * dice number layout, and the robber location.  Does not contain information
 * about any player's pieces on the board (see {@link SOCPutPiece PUTPIECE}).
 *<P>
 * This message sends the classic board layout for the original
 * 4-player game, {@link #BOARD_ENCODING_ORIGINAL}.
 * As of version 1.1.08 there are newer board layouts for game expansions
 * and 6-player extensions: See {@link SOCBoardLayout2 BOARDLAYOUT2}.
 *<P>
 * Unlike {@link SOCBoardLayout2}, the dice numbers are mapped before sending
 * over the network, and unmapped when received.  This is because of a change in
 * {@link soc.game.SOCBoard#getNumberLayout()} in 1.1.08, the version which
 * introduced <tt>SOCBoardLayout2</tt>.  Older clients/servers will still need the
 * mapping done, so it's now done here instead of in SOCBoard.
 *<P>
 * In v2.0.00 and newer, the hex layout values for WATER_HEX and DESERT_HEX
 * were changed to allow new types of land hex.  Just like the dice numbers,
 * this is mapped in the constructor, sent over the network, and unmapped in
 * {@link #getHexLayout()}, for backwards compatibility with older clients.
 *
 *<H4>Optimization:</H4>
 * For v2.0.00 and newer servers and clients ({@link #VERSION_FOR_OMIT_IF_EMPTY_NEW_GAME}):
 *<P>
 * If the game is still forming (state {@link SOCGame#NEW}),
 * client already has data for the empty board. If so, no board layout message
 * is sent to the client.
 *
 * @author Robert S. Thomas
 */
public class SOCBoardLayout extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /**
     * First version number (2.0.00) where client isn't sent this message
     * when joining a {@link SOCGame#NEW} game, because the board is empty
     * and client already has data for an empty board.
     */
    public static final int VERSION_FOR_OMIT_IF_EMPTY_NEW_GAME = 2000;

    /**
     * Map of dice rolls to values in {@link #numberLayout}. Formerly in SOCBoard.
     * @since 1.1.08
     */
    private static final int[] boardNum2sentNum = { -1, -1, 0, 1, 2, 3, 4, -1, 5, 6, 7, 8, 9 };

    /**
     * Map of values in {@link #numberLayout} to dice rolls:<pre>
     *    -1 : robber
     *     0 : 2
     *     1 : 3
     *     2 : 4
     *     3 : 5
     *     4 : 6
     *     5 : 8 (7 is skipped)
     *     6 : 9
     *     7 : 10
     *     8 : 11
     *     9 : 12 </pre>
     * Formerly in SOCBoard.
     * @since 1.1.08
     */
    private static final int[] sentNum2BoardNum = { 2, 3, 4, 5, 6, 8, 9, 10, 11, 12 };

    /**
     * Hex land type numbers sent over the network.
     * Compare to {@link SOCBoard#WATER_HEX}, {@link SOCBoard#DESERT_HEX}.
     * @since 2.0.00
     */
    private static final int SENTLAND_WATER = 6, SENTLAND_DESERT = 0;

    /**
     * Name of game
     */
    private String game;

    /**
     * The hex layout; a mapping/unmapping step is done in constructor/{@link #getHexLayout()}.
     */
    private int[] hexLayout;

    /**
     * The dice number layout; a mapping/unmapping step is done in constructor/{@link #getNumberLayout()}.
     */
    private int[] numberLayout;

    /**
     * Where the robber is
     */
    private int robberHex;

    /**
     * Create a SOCBoardLayout message
     *
     * @param ga   the name of the game
     * @param hl   the hex layout; not mapped yet from SOCBoard's value range,
     *               so the constructor will map it.
     * @param nl   the dice number layout; not mapped yet, so the constructor
     *               will map it from the {@link SOCBoard#getNumberLayout()} value
     *               range to the BOARDLAYOUT message's value range.
     * @param rh   the robber hex
     */
    public SOCBoardLayout(String ga, int[] hl, int[] nl, int rh)
    {
        this(ga, hl, nl, rh, false);
    }

    /**
     * Create a SOCBoardLayout message
     *
     * @param ga   the name of the game
     * @param hl   the hex layout
     * @param nl   the number layout
     * @param rh   the robber hex
     * @param alreadyMapped  have the hex layout and number layout already been mapped?
     * @since 1.1.08
     */
    private SOCBoardLayout
        (String ga, final int[] hl, final int[] nl, final int rh, final boolean alreadyMapped)
    {
        messageType = BOARDLAYOUT;
        game = ga;
        if (alreadyMapped)
        {
            hexLayout = hl;
            numberLayout = nl;
        } else {
            hexLayout = new int[hl.length];
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

            numberLayout = new int[nl.length];
            for (int i = nl.length - 1; i >= 0; --i)
            {
                int n = nl[i];
                if (n != -1)
                    n = boardNum2sentNum[n];
                numberLayout[i] = n;
            }
        }
        robberHex = rh;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * Get the hex layout, already mapped from the BOARDLAYOUT message value range
     * to the {@link SOCBoard#setHexLayout(int[])} value range.
     * @return the hex layout
     */
    public int[] getHexLayout()
    {
        int[] hl = new int[hexLayout.length];
        for (int i = hl.length - 1; i >= 0; --i)
        {
            int h = hexLayout[i];
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
     * Get the dice number layout, already mapped from the BOARDLAYOUT message value range
     * to the {@link soc.game.SOCBoard#setNumberLayout(int[])} value range.
     * @return the number layout
     */
    public int[] getNumberLayout()
    {
        int[] nl = new int[numberLayout.length];
        for (int i = nl.length - 1; i >= 0; --i)
        {
            int n = numberLayout[i];
            if (n != -1)
                n = sentNum2BoardNum[n];
            else
                n = 0;  // 0, not -1, in 1.1.07 SOCBoard.getNumberOnHexFromNumber
            nl[i] = n;
        }
        return nl;

        // This getter is called only once for the object,
        // so there's no need to remember the new int[].
    }

    /**
     * @return the robber hex
     */
    public int getRobberHex()
    {
        return robberHex;
    }

    /**
     * Formatted string to send this BOARDLAYOUT over the network.
     * BOARDLAYOUT sep game sep2 hexLayout[0] sep2 ... sep2 hexLayout[36]
     * sep2 numberLayout[0] sep2 ... sep2 numberLayout[36] sep2 robberHex
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, hexLayout, numberLayout, robberHex);
    }

    /**
     * Formatted string to send this BOARDLAYOUT over the network.
     * BOARDLAYOUT sep game sep2 hexLayout[0] sep2 ... sep2 hexLayout[36]
     * sep2 numberLayout[0] sep2 ... sep2 numberLayout[36] sep2 robberHex
     *
     * @return the command string
     */
    public static String toCmd(String ga, int[] hl, int[] nl, int rh)
    {
        String cmd = BOARDLAYOUT + sep + ga;

        for (int i = 0; i < 37; i++)
        {
            cmd += (sep2 + hl[i]);
        }

        for (int i = 0; i < 37; i++)
        {
            cmd += (sep2 + nl[i]);
        }

        cmd += (sep2 + rh);

        return cmd;
    }

    /**
     * Parse the command String into a BoardLayout message
     *
     * @param s   the String to parse
     * @return    a BoardLayout message
     */
    public static SOCBoardLayout parseDataStr(String s)
    {
        String ga; // game name
        int[] hl = new int[37]; // hex layout
        int[] nl = new int[37]; // number layout
        int rh; // robber hex
        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            ga = st.nextToken();

            for (int i = 0; i < 37; i++)
            {
                hl[i] = Integer.parseInt(st.nextToken());
            }

            for (int i = 0; i < 37; i++)
            {
                nl[i] = Integer.parseInt(st.nextToken());
            }

            rh = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCBoardLayout(ga, hl, nl, rh, true);
    }

    /**
     * Render the SOCBoardLayout in human-readable form.
     * In version 1.1.09 and later, the hexLayout and numberLayout contents are included,
     *   and for convenience, robberHex is in hexadecimal instead of base-10
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer("SOCBoardLayout:game=");
        sb.append(game);
        sb.append("|hexLayout=");
        DataUtils.arrayIntoStringBuf(hexLayout, sb, false);
        sb.append("|numberLayout=");
        DataUtils.arrayIntoStringBuf(numberLayout, sb, false);
        sb.append("|robberHex=0x");
        sb.append(Integer.toHexString(robberHex));

        return sb.toString();
    }
}
