/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2015 Jeremy D Monin <jeremy@nand.net>
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
package soc.robot;

import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.message.SOCSetSpecialItem;  // strictly for javadocs

import java.util.Vector;


/**
 * This is a possible Special Item Pick that we can request from the server,
 * with a {@link SOCSetSpecialItem#OP_PICK} message.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCPossiblePickSpecialItem extends SOCPossiblePiece
{
    /** The special item's {@code typeKey}, for {@link SOCSetSpecialItem#typeKey} */
    public final String typeKey;

    /** The pick request's game index */
    public final int gi;

    /** The pick request's player index */
    public final int pi;

    /** The resource costs if any, or {@code null} */
    public final SOCResourceSet cost;

    /**
     * Constructor.
     *
     * @param pl   the owner
     * @param type the special item's {@code typeKey}, for {@link SOCSetSpecialItem#typeKey}
     * @param gi   the pick request's game index
     * @param pi   the pick request's player index
     * @param eta  the ETA
     * @param cost the resource costs if any, or {@code null}
     */
    public SOCPossiblePickSpecialItem
        (final SOCPlayer pl, final String type, final int gi, final int pi, final int eta, final SOCResourceSet cost)
    {
        super(SOCPossiblePiece.PICK_SPECIAL, pl, 0);  // no coordinate

        this.typeKey = type;
        this.gi = gi;
        this.pi = pi;
        this.cost = cost;
        this.eta = eta;
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
    }

}
