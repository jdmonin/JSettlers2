/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2015,2018 Jeremy D Monin <jeremy@nand.net>
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


/**
 * This is a possible card that we can buy
 *
 * @author Robert S Thomas
 *
 */
/*package*/ class SOCPossibleCard extends SOCPossiblePiece
{
    /**
     * constructor
     *
     * @param pl   the owner
     * @param et  the eta
     */
    public SOCPossibleCard(SOCPlayer pl, int et)
    {
        super(SOCPossiblePiece.CARD, pl, 0);  // no coordinate

        eta = et;
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
    }

    /**
     * copy constructor
     *
     * @param pc  the possible card to copy
     */
    public SOCPossibleCard(SOCPossibleCard pc)
    {
        //D.ebugPrintln(">>>> Copying possible card: "+pc);
        super(SOCPossiblePiece.CARD, pc.getPlayer(), 0);

        eta = pc.getETA();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
    }

}
