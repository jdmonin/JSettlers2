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

import java.util.StringTokenizer;


/**
 * This message contains the board layout information.
 * That is, the hex layout, the number layout, and
 * where the robber is.  This does not contain information
 * about where the player's pieces are on the board.
 *
 * @author Robert S. Thomas
 */
public class SOCBoardLayout extends SOCMessage
{
    /**
     * Name of game
     */
    private String game;

    /**
     * The hex layout
     */
    private int[] hexLayout;

    /**
     * The number layout
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
        messageType = BOARDLAYOUT;
        game = ga;
        hexLayout = hl;
        numberLayout = nl;
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
        return numberLayout;
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

        return new SOCBoardLayout(ga, hl, nl, rh);
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCBoardLayout:game=" + game + "|hexLayout=" + hexLayout + "|numberLayout=" + numberLayout + "|robberHex=" + robberHex;

        return s;
    }
}
