/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2009,2012,2018,2020-2023 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCResourceConstants;

/**
 * Monopoly dev card strategy for a {@link SOCRobotBrain} in a game.
 * See {@link #decidePlayMonopoly()} for details.
 */
public class MonopolyStrategy
{

    /** Our game */
    protected final SOCGame game;

    /** Our {@link SOCRobotBrain}'s player in {@link #game} */
    protected final SOCPlayer ourPlayerData;

    /**
     * The resource type we want to monopolize,
     * chosen by {@link #decidePlayMonopoly()},
     * such as {@link SOCResourceConstants#CLAY}
     * or {@link SOCResourceConstants#SHEEP}
     */
    protected int monopolyChoice;

    /**
     * Create a MonopolyStrategy for a {@link SOCRobotBrain}'s player.
     * @param ga  Our game
     * @param pl  Our player data in {@code ga}; not null
     * @param br  Robot brain for {@code pl}
     * @throws IllegalArgumentException if {@code pl} is null
     */
    public MonopolyStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br)
        throws IllegalArgumentException
    {
        if (pl == null)
            throw new IllegalArgumentException();
        game = ga;
        ourPlayerData = pl;
        // br is currently unused here, but a third-party bot's strategy may need it,
        // so this constructor asks for it.

        monopolyChoice = SOCResourceConstants.SHEEP;
    }

    /**
     * Get our monopoly choice. Valid only after
     * {@link #decidePlayMonopoly()} returns true,
     * unless the bot's architecture calls {@link #setMonopolyChoice(int)} directly instead.
     * @return  Resource type to monopolize,
     *    such as {@link SOCResourceConstants#CLAY}
     *    or {@link SOCResourceConstants#SHEEP}
     */
    public int getMonopolyChoice()
    {
        return monopolyChoice;
    }

    /**
     * Directly set our monopoly choice.
     * For example, a third-party bot's architecture might have code which determines
     * the best action is monopoly and the resource type to monopolize,
     * instead of using {@link #decidePlayMonopoly()}.
     *
     * @param rtype  Resource type to monopolize,
     *    such as {@link SOCResourceConstants#CLAY}
     *    or {@link SOCResourceConstants#SHEEP}.
     *    Not checked for validity.
     * @since 2.5.00
     */
    public void setMonopolyChoice(final int rtype)
    {
        monopolyChoice = rtype;
    }

    /**
     * Decide whether we should play a monopoly card,
     * and set {@link #getMonopolyChoice()} if so.
     *<P>
     * Decision and chosen resource type are based on which type our player
     * could trade for the most resources (given our player's ports),
     * not on the resources needed for we currently want to build.
     *<P>
     * See {@link #setMonopolyChoice(int)} for another possible approach
     * third-party bots may use.
     *
     * @return True if we should play the card
     */
    public boolean decidePlayMonopoly()
    {
        int bestResourceCount = 0;
        int bestResourceType = 0;
        final int ourPN = ourPlayerData.getPlayerNumber();
        final boolean threeForOne = ourPlayerData.getPortFlag(SOCBoard.MISC_PORT);

        for (int resource = SOCResourceConstants.CLAY;
                 resource <= SOCResourceConstants.WOOD; resource++)
        {
            //log.debug("$$ resource="+resource);
            int freeResourceCount = 0;
            final boolean twoForOne = ourPlayerData.getPortFlag(resource);

            int resourceTotal = 0;

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (pn == ourPN)
                    continue;  // skip our own resources

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
                bestResourceType = resource;
            }
        }

        if (bestResourceCount > 2)
        {
            monopolyChoice = bestResourceType;

            return true;
        }
        else
        {
            return false;
        }

    }

}
