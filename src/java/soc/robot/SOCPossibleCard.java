/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
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
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import soc.game.SOCPlayer;

import java.util.Vector;


/**
 * This is a possible card that we can buy
 *
 * @author Robert S Thomas
 *
 */
public class SOCPossibleCard extends SOCPossiblePiece
{
    /**
     * constructor
     *
     * @param pl   the owner
     * @param et  the eta
     */
    public SOCPossibleCard(SOCPlayer pl, int et)
    {
        pieceType = SOCPossiblePiece.CARD;
        player = pl;
        coord = 0;
        eta = et;
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
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
        pieceType = SOCPossiblePiece.CARD;
        player = pc.getPlayer();
        coord = 0;
        eta = pc.getETA();
        threats = new Vector<SOCPossiblePiece>();
        biggestThreats = new Vector<SOCPossiblePiece>();
        threatUpdatedFlag = false;
        hasBeenExpanded = false;
    }
}
