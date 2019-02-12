/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2009,2011,2012,2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

// import org.apache.log4j.Logger;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;

/*package*/ class RobberStrategy
{

    /** debug logging */
    // private transient Logger log = Logger.getLogger(this.getClass().getName());
    private static transient D log = new D();

   /**
    * Determine the best hex to move the robber.
    */
   public static int getBestRobberHex
       (SOCGame game, SOCPlayer ourPlayerData, HashMap<Integer, SOCPlayerTracker> playerTrackers, Random rand)
   {
       log.debug("%%% MOVEROBBER");

       final int[] hexes = game.getBoard().getLandHexCoords();

       final int prevRobberHex = game.getBoard().getRobberHex();

       /**
        * decide which player we want to thwart
        */
       int[] winGameETAs = new int[game.maxPlayers];
       for (int i = game.maxPlayers - 1; i >= 0; --i)
           winGameETAs[i] = 100;

       Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();
       while (trackersIter.hasNext())
       {
           SOCPlayerTracker tracker = trackersIter.next();
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

       /**
        * figure out the best way to thwart that player
        */
       SOCPlayer victim = game.getPlayer(victimNum);
       SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
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

   /**
    * Choose a robber victim.
    *
    * @param choices  a boolean array representing which players are possible victims;
    *                 1 element per player number (0 to <tt>game.maxPlayers</tt> - 1).
    * @param canChooseNone   In some game scenarios (such as <tt>SC_PIRI</tt>),
    *     the robot may have the option to choose to not rob anyone. This strategy
    *     ignores that and always chooses a victim to rob.
    * @return  Player number to rob, or -1 if none could be decided
    */
   public static int chooseRobberVictim
       (final boolean[] choices, final boolean canChooseNone,
        final SOCGame game, final HashMap<Integer, SOCPlayerTracker> playerTrackers)
   {
       int choice = -1;

       /**
        * choose the player with the smallest WGETA
        */
       for (int i = 0; i < game.maxPlayers; i++)
       {
           if (game.isSeatVacant(i) || ! choices[i])
               continue;

           if (choice == -1)
           {
               choice = i;
           }
           else
           {
               SOCPlayerTracker tracker1 = playerTrackers.get(new Integer(i));
               SOCPlayerTracker tracker2 = playerTrackers.get(new Integer(choice));

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

}
