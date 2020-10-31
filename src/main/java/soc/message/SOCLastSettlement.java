/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2010,2014,2017,2020 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This message tells the client where the last settlement was placed.
 * Used for robots during initial placement at the start of a game.
 *<P>
 * In games where all clients are v2.0.00 or newer, send {@link SOCPlayerElement.PEType#LAST_SETTLEMENT_NODE} instead:
 * Check clients' version against {@link SOCPlayerElement#VERSION_FOR_CARD_ELEMENTS}.
 *
 * @author Robert S Thomas
 */
public class SOCLastSettlement extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 1111L;  // last structural change v1.1.11

    /**
     * the name of the game
     */
    private String game;

    /**
     * the number of the player
     */
    private int playerNumber;

    /**
     * the coordinates of the piece
     */
    private int coordinates;

    /**
     * create a LastSettlement message
     *
     * @param na  name of the game
     * @param pn  player number
     * @param co  coordinates
     */
    public SOCLastSettlement(String na, int pn, int co)
    {
        messageType = LASTSETTLEMENT;
        game = na;
        playerNumber = pn;
        coordinates = co;
    }

    /**
     * @return the name of the game
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
     * @return the coordinates
     */
    public int getCoordinates()
    {
        return coordinates;
    }

    /**
     * Command string:
     *
     * LASTSETTLEMENT sep game sep2 playerNumber sep2 coordinates
     *
     * @return the command string
     */
    public String toCmd()
    {
        return toCmd(game, playerNumber, coordinates);
    }

    /**
     * Command string:
     *
     * LASTSETTLEMENT sep game sep2 playerNumber sep2 coordinates
     *
     * @param na  the name of the game
     * @param pn  player number
     * @param co  coordinates
     * @return the command string
     */
    public static String toCmd(String na, int pn, int co)
    {
        return LASTSETTLEMENT + sep + na + sep2 + pn + sep2 + co;
    }

    /**
     * parse the command string into a LASTSETTLEMENT message.
     *
     * @param s   the String to parse
     * @return    a LASTSETTLEMENT message, or null if the data is garbled
     */
    public static SOCLastSettlement parseDataStr(String s)
    {
        String na; // name of the game
        int pn; // player number
        int co; // coordinates

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            na = st.nextToken();
            pn = Integer.parseInt(st.nextToken());
            co = Integer.parseInt(st.nextToken());
        }
        catch (Exception e)
        {
            return null;
        }

        return new SOCLastSettlement(na, pn, co);
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Converts settlement coordinate to decimal from hexadecimal format.
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     * @since 2.4.50
     */
    public static String stripAttribNames(String messageStrParams)
    {
        String s = SOCMessage.stripAttribNames(messageStrParams);
        if (s == null)
            return null;

        String[] pieces = s.split(SOCMessage.sep2);
        if (pieces.length < 3)
            return null;

        pieces[2] = Integer.toString(Integer.parseInt(pieces[2], 16));

        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < pieces.length; ++i)
            ret.append(pieces[i]).append(sep2_char);

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        String s = "SOCLastSettlement:game=" + game + "|playerNumber=" + playerNumber + "|coord=" + Integer.toHexString(coordinates);

        return s;
    }
}
