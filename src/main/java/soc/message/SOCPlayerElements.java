/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010,2014-2015,2017-2020 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.message.SOCPlayerElement.PEType;

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

    /**
     * Minimum client version required: v2.0.00.
     *<P>
     * Same version as {@link SOCPlayerElement#VERSION_FOR_CARD_ELEMENTS}.
     */
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
     * Element types from {@link SOCPlayerElement} such as {@link PEType#CLAY} as ints,
     * each matching up with the same-index item of parallel array {@link #amounts}.
     * See {@link #getElementTypes()} for details.
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
     * @param et  array of the types of element, such as {@link PEType#SETTLEMENTS} or {@link PEType#WHEAT}.
     *             For playing pieces in general, see {@link SOCPlayerElement#elementTypeForPieceType(int)}.
     * @param amt array of the amounts to set or change each element, corresponding to <tt>et[]</tt>
     * @throws NullPointerException if {@code et} null or {@code amt} null, or {@code et} contains null values
     * @since 2.3.00
     */
    public SOCPlayerElements(String ga, int pn, int ac, final PEType[] et, final int[] amt)
        throws NullPointerException
    {
        this(ga, pn, ac, PEType.getValues(et), amt);
    }

    /**
     * Constructor for server to tell client about player elements.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ac  the type of action: {@link SOCPlayerElement#SET},
     *             {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     * @param et  array of the types of element, such as {@link PEType#SETTLEMENTS}
     *             or {@link PEType#WHEAT}, from {@link PEType#getValues(PEType[])}.
     *             For playing pieces in general, see {@link SOCPlayerElement#elementTypeForPieceType(int)}.
     * @param amt array of the amounts to set or change each element, corresponding to <tt>et[]</tt>
     * @throws NullPointerException if {@code et} null or {@code amt} null
     * @see #SOCPlayerElements(String, int, int, PEType[], int[])
     */
    private SOCPlayerElements(String ga, int pn, int ac, final int[] et, final int[] amt)
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
     * Constructor for server to tell client(s) about known player resources:
     * {@link SOCResourceConstants#CLAY} through {@link SOCResourceConstants#WOOD}.
     * Resource types with {@link SOCResourceSet#getAmount(int) rs.getAmount(type)} == 0 aren't sent.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ac  the type of action: {@link SOCPlayerElement#SET},
     *             {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     * @param rs  resource set, to send known resource types; {@link SOCResourceConstants#UNKNOWN} are ignored
     * @throws NullPointerException if {@code rs} null
     * @since 2.0.00
     */
    public SOCPlayerElements(String ga, int pn, int ac, final SOCResourceSet rs)
        throws NullPointerException
    {
        super(PLAYERELEMENTS, ga, null);

        playerNumber = pn;
        actionType = ac;

        final int typeCount = rs.getResourceTypeCount();
        pa = new int[2 + 2 * typeCount];
        pa[0] = pn;
        pa[1] = ac;
        if (typeCount > 0)
        {
            elementTypes = new int[typeCount];
            amounts = new int[typeCount];
        }

        for (int pai = 2, eti = 0, r = SOCResourceConstants.CLAY; r <= SOCResourceConstants.WOOD; ++r)
        {
            int amt = rs.getAmount(r);
            if (amt > 0)
            {
                pa[pai] = r;    ++pai;
                pa[pai] = amt;  ++pai;

                elementTypes[eti] = r;
                amounts[eti] = amt;
                ++eti;
            }
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
     * Get the player info element types.
     * These are ints to preserve values from unknown types from different versions.
     * Converted to int at sending side with {@link PEType#getValue()}.
     * To convert at receiving side to {@link PEType}s, use {@link PEType#valueOf(int)}.
     * @return the element types from {@link SOCPlayerElement} such as {@link PEType#CLAY} as ints,
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

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list for {@link #parseDataStr(List)}.
     * Handles elemNum=value pairs, undoes mapping of action constant integers -> strings ({@code "GAIN"} etc).
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters to finish parsing into a SOCMessage, or {@code null} if malformed
     * @since 2.4.50
     */
    public static List<String> stripAttribsToList(String messageStrParams)
    {
        // don't call SOCMessage.stripAttribsToList, we need the e# names in elemNum=value pairs

        String[] pieces = messageStrParams.split(sepRE);
        // [0] game=...
        // [1] playerNum=...
        // [2] actionType=...
        // [3] e#=#,e#=#,...

        if (pieces.length != 4)
            return null;

        List<String> ret = new ArrayList<>();

        if (pieces[0].startsWith("game="))
            ret.add(pieces[0].substring(5));
        else
            return null;

        if (pieces[1].startsWith("playerNum="))
            ret.add(pieces[1].substring(10));
        else
            return null;

        String act = pieces[2];
        if (! act.startsWith("actionType="))
            return null;
        act = act.substring(11);
        if (! Character.isDigit(act.charAt(0)))
        {
            for (int ac = 0; ac < SOCPlayerElement.ACTION_STRINGS.length; ++ac)
            {
                if (SOCPlayerElement.ACTION_STRINGS[ac].equals(act))
                {
                    act = Integer.toString(ac + 100);
                    break;
                }
            }
        }
        ret.add(act);

        // "e5=9,e7=12" -> "5", "9, "7", "12"
        pieces = pieces[3].split(",");
        for (int i = 0; i < pieces.length; ++i)
        {
            String piece = pieces[i];  // "e5=9"
            if (piece.charAt(0) != 'e')
                return null;

            int j = piece.indexOf('=');
            if (j < 2)
                return null;

            ret.add(piece.substring(1, j));
            ret.add(piece.substring(j + 1));
        }

        return ret;
    }

    /**
     * @return a human readable form of the message
     * @since 2.0.00
     */
    public String toString()
    {
        final String act;
        if ((actionType >= 100) && ((actionType - 100) < SOCPlayerElement.ACTION_STRINGS.length))
            act = SOCPlayerElement.ACTION_STRINGS[actionType - 100];
        else
            act = Integer.toString(actionType);

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
