/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2009,2011,2012,2018,2020-2021 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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

import java.util.Random;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;

/**
 * Discard strategy for a {@link SOCRobotBrain} in a game.
 * For details see {@link #getBestRobberHex()} and {@link #chooseRobberVictim(boolean[], boolean)}.
 *<P>
 * Before version 2.2.00 these methods were static and could not easily be extended.
 */
public class RobberStrategy
{

    /** Our game */
    protected final SOCGame game;

    /** Our {@link #brain}'s player in {@link #game} */
    protected final SOCPlayer ourPlayerData;

    /** Our brain for {@link #ourPlayerData} */
    protected final SOCRobotBrain brain;

    /** Random number generator from {@link #brain} */
    protected final Random rand;

    /** debug logging */
    protected static transient D log = new D();

    /**
     * Create a RobberStrategy for a {@link SOCRobotBrain}'s player.
     * @param ga  Our game
     * @param pl  Our player data in {@code ga}
     * @param br  Robot brain for {@code pl}
     * @param rand  Random number generator from {@code br}
     * @since 2.2.00
     */
    public RobberStrategy(SOCGame ga, SOCPlayer pl, SOCRobotBrain br, Random rand)
    {
        if ((pl == null) || (br == null))
            throw new IllegalArgumentException();

        game = ga;
        ourPlayerData = pl;
        brain = br;
        this.rand = rand;
    }

   /**
    * Determine the best hex to move the robber, based on
    * board and current opponent info in {@link SOCPlayerTracker}s.
    * Calls {@link #selectPlayerToThwart(int)}, {@link #selectRobberHex(int, int)}.
    *<P>
    * Currently the robot always chooses to move the robber, never the pirate,
    * so this method calculates for the robber only.
    *
    * @return Hex coordinate to move robber to. Should be a member of {@link SOCBoard#getLandHexCoords()}.
    */
   public int getBestRobberHex()
   {
       log.debug("%%% MOVEROBBER");

       final int prevRobberHex = game.getBoard().getRobberHex();
       int victimNum = selectPlayerToThwart(prevRobberHex);
       return selectRobberHex(prevRobberHex, victimNum);
   }

   /**
    * Choose a robbery victim, from players with pieces adjacent to the robber (or pirate).
    *
    * @param isVictim  Boolean array indicating which players are possible victims;
    *     1 element per player number (0 to {@code game.maxPlayers} - 1).
    * @param canChooseNone   In some game scenarios (such as <tt>SC_PIRI</tt>),
    *     the robot may have the option to choose to not rob anyone. This strategy
    *     ignores that and always chooses a victim to rob.
    * @return  Player number to rob, or -1 if none could be decided
    * @see #selectPlayerToThwart(int)
    */
   public int chooseRobberVictim
       (final boolean[] isVictim, final boolean canChooseNone)
   {
       final SOCPlayerTracker[] playerTrackers = brain.playerTrackers;

       int choice = -1;

       /**
        * choose the player with the smallest WGETA
        */
       for (int i = 0; i < game.maxPlayers; i++)
       {
           if (game.isSeatVacant(i) || ! isVictim[i])
               continue;

           if (choice == -1)
           {
               choice = i;
           }
           else
           {
               SOCPlayerTracker tracker1 = playerTrackers[i];
               SOCPlayerTracker tracker2 = playerTrackers[choice];

               if ((tracker1 != null) && (tracker2 != null) && (tracker1.getWinGameETA() < tracker2.getWinGameETA()))
               {
                   //log.debug("Picking a robber victim: pnum="+i+" VP="+game.getPlayer(i).getPublicVP());
                   choice = i;
               }
           }
       }

       /**
        * choose victim at random
        *
          do {
          choice = Math.abs(rand.nextInt() % SOCGame.MAXPLAYERS);
          } while (!choices[choice]);
        */

       return choice;
   }

   /**
    * Select the player to target with the robber given the current hex the robber is on.
    * Unlike {@link #chooseRobberVictim(boolean[], boolean)}, the robber may be moved
    * because caller might call {@link #selectRobberHex(int, int)} with the result from
    * {@code selectPlayerToThwart}.
    *<P>
    * Default implementation targets the player closest to winning.  This may be overridden, for
    * example, to target the player with the resources we want most.
    *
    * @param robberHex the current location of the robber
    * @return a valid player number
    * @since 2.5.00
    */
   public int selectPlayerToThwart(int robberHex)
   {
       /**
        * decide which player we want to thwart
        */
       int[] winGameETAs = new int[game.maxPlayers];
       for (int i = game.maxPlayers - 1; i >= 0; --i)
           winGameETAs[i] = 100;

       for (final SOCPlayerTracker tracker : brain.playerTrackers)
       {
           if (tracker == null)
               continue;

           final int trackerPN = tracker.getPlayer().getPlayerNumber();
           log.debug("%%%%%%%%% TRACKER FOR PLAYER " + trackerPN);

           try
           {
               tracker.recalcWinGameETA();
               winGameETAs[trackerPN] = tracker.getWinGameETA();
               log.debug("winGameETA = " + tracker.getWinGameETA());
           }
           catch (NullPointerException e)
           {
               log.debug("Null Pointer Exception calculating winGameETA");
               winGameETAs[trackerPN] = 500;
           }
       }

       final int ourPlayerNumber = ourPlayerData.getPlayerNumber();
       int victimNum = -1;

       for (int pnum = 0; pnum < game.maxPlayers; pnum++)
       {
           if (game.isSeatVacant(pnum))
               continue;

           if ((victimNum < 0) && (pnum != ourPlayerNumber))
           {
               // The first pick
               log.debug("Picking a robber victim: pnum=" + pnum);
               victimNum = pnum;
           }
           else if ((pnum != ourPlayerNumber) && (winGameETAs[pnum] < winGameETAs[victimNum]))
           {
               // A better pick
               log.debug("Picking a better robber victim: pnum=" + pnum);
               victimNum = pnum;
           }
       }
       // Postcondition: victimNum != -1 due to "First pick" in loop.

       return victimNum;
   }

   /**
    * Select the hex to rob, based on the fact we are targeting the specified player,
    * and the robber is currently on the specified hex.
    * Used by {@link #getBestRobberHex()}.
    * @param prevRobberHex  the robber's current location
    * @param victimNum the targeted player
    * @return a valid hex coordinate
    * @see #selectPlayerToThwart(int)
    * @since 2.5.00
    */
   public int selectRobberHex(int prevRobberHex, int victimNum)
   {
       final int[] hexes = game.getBoard().getLandHexCoords();

       /**
        * figure out the best way to thwart that player
        */
       SOCPlayer victim = game.getPlayer(victimNum);
       SOCBuildingSpeedEstimate estimate = brain.getEstimator();
       int bestHex = prevRobberHex;
       int worstSpeed = 0;
       final boolean skipDeserts = game.isGameOptionSet("RD");  // can't move robber to desert
       SOCBoard gboard = (skipDeserts ? game.getBoard() : null);

       for (int i = 0; i < hexes.length; i++)
       {
           /**
            * only check hexes that we're not touching,
            * and not the robber hex, and possibly not desert hexes
            */
           if ((hexes[i] != prevRobberHex)
                   && ourPlayerData.getNumbers().hasNoResourcesForHex(hexes[i])
                   && ! (skipDeserts && (gboard.getHexTypeFromCoord(hexes[i]) == SOCBoard.DESERT_HEX )))
           {
               estimate.recalculateEstimates(victim.getNumbers(), hexes[i]);

               int[] speeds = estimate.getEstimatesFromNothingFast(victim.getPortFlags());
               int totalSpeed = 0;

               for (int j = SOCBuildingSpeedEstimate.MIN;
                        j < SOCBuildingSpeedEstimate.MAXPLUSONE; j++)
               {
                   totalSpeed += speeds[j];
               }

               log.debug("total Speed = " + totalSpeed);

               if (totalSpeed > worstSpeed)
               {
                   bestHex = hexes[i];
                   worstSpeed = totalSpeed;
                   log.debug("bestHex = " + Integer.toHexString(bestHex));
                   log.debug("worstSpeed = " + worstSpeed);
               }
           }
       }

       log.debug("%%% bestHex = " + Integer.toHexString(bestHex));

        /**
         * Pick a spot at random if we can't decide.
         * Don't pick deserts if the game option is set.
         * Don't pick one of our hexes if at all possible.
         * It's not likely we'll need to pick one of our hexes
         * (we try 30 times to avoid it), so there isn't code here
         * to pick the 'least bad' one.
         * (TODO) consider that: It would be late in the game
         *       if the board's that crowded with pieces.
         *       Use similar algorithm as picking for opponent,
         *       but apply it worst vs best.
         */
        if (bestHex == prevRobberHex)
        {
            int numRand = 0;
            while ((bestHex == prevRobberHex)
                   || (skipDeserts
                       && (gboard.getHexTypeFromCoord(bestHex) == SOCBoard.DESERT_HEX ))
                   || ((numRand < 30)
                       && ourPlayerData.getNumbers().hasNoResourcesForHex(bestHex)))
            {
                bestHex = hexes[Math.abs(rand.nextInt()) % hexes.length];
                log.debug("%%% random pick = " + Integer.toHexString(bestHex));
                ++numRand;
            }
        }

       return bestHex;
   }

}
