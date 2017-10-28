/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2009,2012 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.proto.Data;

public class MonopolyStrategy
{

    /** Our game */
    private final SOCGame game;

    /** Our {@link SOCRobotBrain}'s player */
    private final SOCPlayer ourPlayerData;

    /**
     * The resource we want to monopolize,
     * chosen by {@link #decidePlayMonopoly()},
     * such as {@link Data.ResourceType#CLAY}
     * or {@link Data.ResourceType#SHEEP}
     */
    protected int monopolyChoice;

    /**
     * Create a MonopolyStrategy for a {@link SOCRobotBrain}'s player.
     * @param ga  Our game
     * @param pl  Our player data in <tt>ga</tt>
     */
    MonopolyStrategy(SOCGame ga, SOCPlayer pl)
    {
        if (pl == null)
            throw new IllegalArgumentException();
        game = ga;
        ourPlayerData = pl;
        monopolyChoice = Data.ResourceType.SHEEP_VALUE;
    }

    /**
     * Get our monopoly choice; valid only after
     * {@link #decidePlayMonopoly()} returns true.
     * @return  Resource type to monopolize,
     *    such as {@link Data.ResourceType#CLAY}
     *    or {@link Data.ResourceType#SHEEP}
     */
    public int getMonopolyChoice()
    {
        return monopolyChoice;
    }

    /**
     * Decide whether we should play a monopoly card,
     * and set {@link #getMonopolyChoice()} if so.
     * @return True if we should play the card
     */
    public boolean decidePlayMonopoly()
    {
        int bestResourceCount = 0;
        int bestResource = 0;

        for (int resource = Data.ResourceType.CLAY_VALUE;
             resource <= Data.ResourceType.WOOD_VALUE; resource++)
        {
            //log.debug("$$ resource="+resource);
            int freeResourceCount = 0;
            boolean twoForOne = false;
            boolean threeForOne = false;

            if (ourPlayerData.getPortFlag(resource))
            {
                twoForOne = true;
            }
            else if (ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
            {
                threeForOne = true;
            }

            int resourceTotal = 0;

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (ourPlayerData.getPlayerNumber() == pn)
                    continue;  // skip our resources

                resourceTotal += game.getPlayer(pn).getResources().getAmount(resource);

                //log.debug("$$ resourceTotal="+resourceTotal);
            }

            if (twoForOne)
            {
                freeResourceCount = resourceTotal / 2;
            }
            else if (threeForOne)
            {
                freeResourceCount = resourceTotal / 3;
            }
            else
            {
                freeResourceCount = resourceTotal / 4;
            }

            //log.debug("freeResourceCount="+freeResourceCount);
            if (freeResourceCount > bestResourceCount)
            {
                bestResourceCount = freeResourceCount;
                bestResource = resource;
            }
        }

        if (bestResourceCount > 2)
        {
            monopolyChoice = bestResource;

            return true;
        }
        else
        {
            return false;
        }

    }

}
