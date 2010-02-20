/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009-2010 Jeremy D Monin <jeremy@nand.net>
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
 * This message contains the board layout information.
 * That is, the hex layout, the number layout, and
 * where the robber is.  This does not contain information
 * about where the player's pieces are on the board.
 *<P>
 * This message sends the standard board layout of the
 * original game, {@link soc.game.SOCBoard#BOARD_ENCODING_ORIGINAL}. 
 * As of version 1.1.08, there is a newer board layout for
 * game expansions.  See the new message type {@link SOCBoardLayout2 BOARDLAYOUT2}.
 *<P>
 * Unlike {@link SOCBoardLayout2}, the dice numbers are mapped before sending
 * over the network, and unmapped when received.  This is because of a change in
 * {@link soc.game.SOCBoard#getNumberLayout()} in 1.1.08, the version which
 * introduced <tt>SOCBoardLayout2</tt>.  Older clients/servers will still need the
 * mapping done, so it's now done here instead of in SOCBoard.
 *
 * @author Robert S. Thomas
 */
public class SOCBoardLayout extends SOCMessage
{
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
     * Name of game
     */
    private String game;

    /**
     * The hex layout
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
     * @param hl   the hex layout
     * @param nl   the number layout
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
     * @param alreadyMappedNL  has the number layout already been mapped?
     * @since 1.1.08
     */
    private SOCBoardLayout(String ga, int[] hl, int[] nl, int rh, boolean alreadyMappedNL)
    {
        messageType = BOARDLAYOUT;
        game = ga;
        hexLayout = hl;
        if (alreadyMappedNL)
        {
            numberLayout = nl;
        } else {
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
     * @return the hex layout
     */
    public int[] getHexLayout()
    {
        return hexLayout;
    }

    /**
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
        arrayIntoSB(hexLayout, sb);
        sb.append("|numberLayout=");
        arrayIntoSB(numberLayout, sb);
        sb.append("|robberHex=0x");
        sb.append(Integer.toHexString(robberHex));

        return sb.toString();
    }
}
