/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2010,2014 Jeremy D Monin <jeremy@nand.net>
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

/**
 * This message from the server holds information on some parts of the player's status,
 * such as resource type counts.  Same structure as {@link SOCPlayerElement} but with
 * less redundancy when sending multiple messages.
 * For a given player number and action type, contains multiple
 * pairs of (element type, value).
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 1.1.09
 */
public class SOCPlayerElements extends SOCMessageTemplateMi
{
    private static final long serialVersionUID = 1109L;  // last structural change v1.1.09

    /** Introduced in version 1.1.09 */
    public static final int VERSION = 1109;

    /**
     * The original 5 named resources, for convenience:
     * {@link SOCPlayerElement#CLAY}, ORE, SHEEP, WHEAT, {@link SOCPlayerElement#WOOD}.
     */
    public static final int[] NAMED_RESOURCES = {SOCPlayerElement.CLAY, SOCPlayerElement.ORE, SOCPlayerElement.SHEEP, SOCPlayerElement.WHEAT, SOCPlayerElement.WOOD};

    /**
     * Player number
     */
    private int playerNumber;

    /**
     * Action type: {@link SOCPlayerElement#SET}, {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     */
    private int actionType;

    /**
     * Element types, such as {@link SOCPlayerElement#CLAY}, matching
     * up with each item of {@link #values}
     */
    private int[] elementTypes;

    /**
     * Element values, matching up with each item of {@link #elementTypes}
     */
    private int[] values;

    /**
     * Constructor for server to tell client about player elements.
     *
     * @param ga  name of the game
     * @param pn  the player number
     * @param ac  the type of action: {@link SOCPlayerElement#SET},
     *             {@link SOCPlayerElement#GAIN}, or {@link SOCPlayerElement#LOSE}
     * @param et  array of the types of element, such as {@link SOCPlayerElement#SETTLEMENTS}
     * @param va  array of the values of each element, corresponding to <tt>et[]</tt>
     * @throws NullPointerException if et null or va null
     */
    public SOCPlayerElements(String ga, int pn, int ac, final int[] et, final int[] va)
        throws NullPointerException
    {
        super(PLAYERELEMENTS, ga, new int[2 + (2 * et.length)]);
        if (va == null)
            throw new NullPointerException();

        playerNumber = pn;
        actionType = ac;
        elementTypes = et;
        values = va;
        pa[0] = pn;
        pa[1] = ac;
        for (int pai = 2, eti = 0; eti < et.length; ++eti)
        {
            pa[pai] = et[eti];  ++pai;
            pa[pai] = va[eti];  ++pai;
        }
    }

    /**
     * Minimum version where this message type is used.
     * PLAYERELEMENTS introduced in 1.1.09 for game-options feature.
     * @return Version number, 1109 for JSettlers 1.1.09.
     */
    public int getMinimumVersion() { return 1109; }

    /**
     * @return the player number
     */
    public int getPlayerNumber()
    {
        return playerNumber;
    }

    /**
     * @return the action type
     */
    public int getAction()
    {
        return actionType;
    }

    /**
     * @return the element type array, such as {@link SOCPlayerElement#CLAY}, matching
     * up with each item of {@link #getValues()}
     */
    public int[] getElementTypes()
    {
        return elementTypes;
    }

    /**
     * @return the element value array, matching up with each item of {@link #getElementTypes()}
     */
    public int[] getValues()
    {
        return values;
    }

    /**
     * Parse the command String array into a SOCPlayerElements message.
     *
     * @param pa   the parameters; length 5 or more required. Length must be odd.<pre>
     * pa[0] = playerNum
     * pa[1] = actionType
     * pa[2] = elementType[0]
     * pa[3] = value[0]
     * pa[4] = elementType[1]
     * pa[5] = value[1]</pre>
     * (etc.)
     * @return    a SOCPlayerElements message, or null if parsing errors
     */
    public static SOCPlayerElements parseDataStr(String[] pa)
    {
        if ((pa == null) || (pa.length < 5) || ((pa.length % 2) == 0))
            return null;
        try
        {
            String ga = pa[0];
            int[] ipa = new int[pa.length - 1];
            for (int i = 0; i < ipa.length; ++i)
                ipa[i] = Integer.parseInt(pa[i+1]);
            int playerNumber = ipa[0];
            int actionType = ipa[1];
            final int n = ipa.length / 2 - 1;
            int[] elementTypes = new int[n];
            int[] values = new int[n];
            for (int i = 0, pai = 2; i < n; ++i)
            {
                elementTypes[i] = ipa[pai];  ++pai;
                values[i] = ipa[pai];  ++pai;
            }
            return new SOCPlayerElements(ga, playerNumber, actionType, elementTypes, values);
        } catch (Throwable e)
        {
            return null;
        }
    }

}
