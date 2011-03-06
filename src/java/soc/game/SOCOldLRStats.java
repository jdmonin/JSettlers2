/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Portions of this file Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import soc.disableDebug.D;


/***
 * this keeps track of the old LR stats
 *
 * @author  Robert S. Thomas
 */
public class SOCOldLRStats
{
    int[] lrLengths;
    SOCPlayer playerWithLR;

    /**
     * constructor
     *
     * @param  ga  the game
     */
    public SOCOldLRStats(SOCGame ga)
    {
        D.ebugPrintln("&&&& SOCOldLRStats constructor");
        lrLengths = new int[ga.maxPlayers];

        for (int i = 0; i < ga.maxPlayers; i++)
        {
            lrLengths[i] = ga.getPlayer(i).getLongestRoadLength();
            D.ebugPrintln("&&& lrLengths[" + i + "] = " + lrLengths[i]);
        }

        playerWithLR = ga.getPlayerWithLongestRoad();

        if (playerWithLR == null)
        {
            D.ebugPrintln("&&& playerWithLR = -1");
        }
        else
        {
            D.ebugPrintln("&&& playerWithLR = " + playerWithLR.getPlayerNumber());
        }
    }

    /**
     * restore the old stats
     *
     * @param  ga  the game
     */
    public void restoreOldStats(SOCGame ga)
    {
        D.ebugPrintln("&&&& restoreOldStats");

        for (int i = 0; i < ga.maxPlayers; i++)
        {
            ga.getPlayer(i).setLongestRoadLength(lrLengths[i]);
            D.ebugPrintln("&&& lrLengths[" + i + "] = " + lrLengths[i]);
        }

        ga.setPlayerWithLongestRoad(playerWithLR);

        if (playerWithLR == null)
        {
            D.ebugPrintln("&&& playerWithLR = -1");
        }
        else
        {
            D.ebugPrintln("&&& playerWithLR = " + playerWithLR.getPlayerNumber());
        }
    }
}
