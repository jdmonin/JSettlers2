/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010,2014-2015,2017 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.List;

import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * This message from the server sends information on some parts of a player's status,
 * such as resource type counts.  Same structure as {@link SOCPlayerElement} but with
 * less overhead to send multiple similar element changes.
 *<P>
 * For a given player number and action type, contains multiple
 * pairs of (element type, amount).
 *<P>
 * Defined in v1.1.09 but unused before v2.0.00, so {@link #getMinimumVersion()} returns 2000.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.09
 * @see SOCGameElements
 */
public class SOCPlayerElements extends SOCMessageTemplateMi
{
    private static final long serialVersionUID = 2000L;  // last structural change v2.0.00

    /** Minimum client version required: v2.0.00 */
    public static final int MIN_VERSION = 2000;

    /**
     * Player number; some elements allow -1 to apply to all players
     */
    private int playerNumber;

    /**
     * Action type: {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     */
    private int actionType;

    /**
     * Element types from {@link SOCPlayerElement}, such as {@link SOCPlayerElement#CLAY},
     * each matching up with the same-index item of parallel array {@link #amounts}.
     */
    private int[] elementTypes;

    /**
     * Element amounts to set or change, matching up with each same-index item of {@link #elementTypes}.
     *<P>
     * Before v2.0.00 this field was {@code values}.
     */
    private int[] amounts;

    /**
     * Constructor for server to tell client about player elements.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ac  the type of action: {@link SOCPlayerElement#SET},
     *             {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     * @param et  array of the types of element, such as {@link SOCPlayerElement#SETTLEMENTS}
     * @param amt array of the amounts to set or change each element, corresponding to <tt>et[]</tt>
     * @throws NullPointerException if {@code et} null or {@code amt} null
     */
    public SOCPlayerElements(String ga, int pn, int ac, final int[] et, final int[] amt)
        throws NullPointerException
    {
        super(PLAYERELEMENTS, ga, new int[2 + (2 * et.length)]);
        if (amt == null)
            throw new NullPointerException();

        playerNumber = pn;
        actionType = ac;
        elementTypes = et;
        amounts = amt;
        pa[0] = pn;
        pa[1] = ac;
        for (int pai = 2, eti = 0; eti < et.length; ++eti)
        {
            pa[pai] = et[eti];   ++pai;
            pa[pai] = amt[eti];  ++pai;
        }
    }

    /**
     * Minimum version where this message type is used ({@link #MIN_VERSION}).
     * PLAYERELEMENTS was introduced in v1.1.09 for the game-options feature
     * but unused until 2.0.00.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    @Override
    public int getMinimumVersion() { return MIN_VERSION; }

    /**
     * @return the player number; some elements allow -1 to apply to all players
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the action type: {@link SOCPlayerElement#SET},
     *     {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * @return the element types from {@link SOCPlayerElement}, such as {@link SOCPlayerElement#CLAY},
     *     each matching up with the same-index item of parallel array {@link #getAmounts()}.
     */
    public int[] getElementTypes()
    {
        return elementTypes;
    }

    /**
     * @return the element amounts to set or change, matching up with
     *     each same-index item of {@link #getElementTypes()}.
     *<P>
     * Before v2.0.00 this method was {@code getValues()}.
     */
    public int[] getAmounts()
    {
        return amounts;
    }

    /**
     * Parse the command String list into a SOCPlayerElements message.
     *
     * @param pa   the parameters; length 5 or more required.
     *     Built by constructor at server. Length must be odd. <pre>
     * pa[0] = gameName
     * pa[1] = playerNum
     * pa[2] = actionType
     * pa[3] = elementType[0]
     * pa[4] = amount[0]
     * pa[5] = elementType[1]
     * pa[6] = amount[1]
     * ...</pre>
     * @return    a SOCPlayerElements message, or null if parsing errors
     */
    public static SOCPlayerElements parseDataStr(List<String> pa)
    {
        if (pa == null)
            return null;
        final int L = pa.size();
        if ((L < 5) || ((L % 2) == 0))
            return null;

        try
        {
            final String gaName = pa.get(0);
            final int playerNumber = Integer.parseInt(pa.get(1));
            final int actionType = Integer.parseInt(pa.get(2));

            final int n = (L - 3) / 2;
            int[] elementTypes = new int[n];
            int[] amounts = new int[n];
            for (int i = 0, pai = 3; i < n; ++i)
            {
                elementTypes[i] = Integer.parseInt(pa.get(pai));  ++pai;
                amounts[i]      = Integer.parseInt(pa.get(pai));  ++pai;
            }

            return new SOCPlayerElements(gaName, playerNumber, actionType, elementTypes, amounts);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.PlayerElements.Builder b
            = GameMessage.PlayerElements.newBuilder();
        b.setPlayerNumber(playerNumber).setActionValue(actionType);

        final int n = (pa.length - 1) / 2;
        ArrayList<Integer> t = new ArrayList<>(n), a = new ArrayList<>(n);
        for (int i = 2; i < pa.length; )
        {
            t.add(pa[i]);  ++i;
            a.add(pa[i]);  ++i;
        }
        b.addAllElementTypesValue(t);
        b.addAllAmounts(a);

        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setPlayerElements(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

    /**
     * @return a human readable form of the message
     * @since 2.0.00
     */
    public String toString()
    {
        final String act;
        switch (actionType)
        {
        case SOCPlayerElement.SET:  act = "SET";  break;
        case SOCPlayerElement.GAIN: act = "GAIN"; break;
        case SOCPlayerElement.LOSE: act = "LOSE"; break;
        default: act = Integer.toString(actionType);
        }

        StringBuilder sb = new StringBuilder
            ("SOCPlayerElements:game=" + game + "|playerNum=" + playerNumber + "|actionType=" + act + '|');
        for (int i = 2; i < pa.length; )
        {
            if (i > 2)
                sb.append(',');
            sb.append('e');
            sb.append(pa[i]);  ++i;
            sb.append('=');
            sb.append(pa[i]);  ++i;
        }

        return sb.toString();
    }

}
