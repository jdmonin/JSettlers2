/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2008 Christopher McNeil <http://sourceforge.net/users/cmcneil>
 * Portions of this file copyright (C) 2003-2004 Robert S. Thomas
 * Portions of this file copyright (C) 2009,2011 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGame;
import soc.game.SOCPlayer;

public class RobberStrategy {

	/** debug logging */
    // private transient Logger log = Logger.getLogger(this.getClass().getName());
    private transient D log = new D();

   /**
    * move the robber
    */
   public int getBestRobberHex(SOCGame game, SOCPlayer ourPlayerData, HashMap playerTrackers)
   {
       Random rand = new Random();
       log.debug("%%% MOVEROBBER");

       final int[] hexes = game.getBoard().getLandHexCoords();

       int robberHex = game.getBoard().getRobberHex();

       /**
        * decide which player we want to thwart
        */
       int[] winGameETAs = new int[game.maxPlayers];
       for (int i = game.maxPlayers - 1; i >= 0; --i)
           winGameETAs[i] = 100;
       Iterator trackersIter = playerTrackers.values().iterator();

       while (trackersIter.hasNext())
       {
           SOCPlayerTracker tracker = (SOCPlayerTracker) trackersIter.next();
           log.debug("%%%%%%%%% TRACKER FOR PLAYER " + tracker.getPlayer().getPlayerNumber());

           try
           {
               tracker.recalcWinGameETA();
               winGameETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
               log.debug("winGameETA = " + tracker.getWinGameETA());
           }
           catch (NullPointerException e)
           {
               log.debug("Null Pointer Exception calculating winGameETA");
               winGameETAs[tracker.getPlayer().getPlayerNumber()] = 500;
           }
       }

       int victimNum = -1;

       for (int pnum = 0; pnum < game.maxPlayers; pnum++)
       {
           if (! game.isSeatVacant(pnum))
           {
               if ((victimNum < 0) && (pnum != ourPlayerData.getPlayerNumber()))
               {
                   // The first pick
                   log.debug("Picking a robber victim: pnum=" + pnum);
                   victimNum = pnum;
               }
               else if ((pnum != ourPlayerData.getPlayerNumber()) && (winGameETAs[pnum] < winGameETAs[victimNum]))
               {
                   // A better pick
                   log.debug("Picking a robber victim: pnum=" + pnum);
                   victimNum = pnum;
               }
           }
       }
       // Postcondition: victimNum != -1 due to "First pick" in loop.

       /**
        * figure out the best way to thwart that player
        */
       SOCPlayer victim = game.getPlayer(victimNum);
       SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate();
       int bestHex = robberHex;
       int worstSpeed = 0;

       for (int i = 0; i < 19; i++)
       {
           /**
            * only check hexes that we're not touching,
            * and not the robber hex
            */
           if ((hexes[i] != robberHex) && ourPlayerData.getNumbers().hasNoResourcesForHex(hexes[i]))
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
        * pick a spot at random if we can't decide
        */
       while ((bestHex == robberHex) && ourPlayerData.getNumbers().hasNoResourcesForHex(hexes[bestHex]))
       {
           bestHex = hexes[Math.abs(rand.nextInt() % hexes.length)];
           log.debug("%%% random pick = " + Integer.toHexString(bestHex));
       }
       
       return bestHex;
   }
   
   /**
    * choose a robber victim
    *
    * @param choices a boolean array representing which players are possible victims
    */
   public int chooseRobberVictim(boolean[] choices, SOCGame game, HashMap playerTrackers)
   {
       int choice = -1;

       /**
        * choose the player with the smallest WGETA
        */
       for (int i = 0; i < game.maxPlayers; i++)
       {
           if (! game.isSeatVacant (i))
           {
               if (choices[i])
               {
                   if (choice == -1)
                   {
                       choice = i;
                   }
                   else
                   {
                       SOCPlayerTracker tracker1 = (SOCPlayerTracker) playerTrackers.get(new Integer(i));
                       SOCPlayerTracker tracker2 = (SOCPlayerTracker) playerTrackers.get(new Integer(choice));
   
                       if ((tracker1 != null) && (tracker2 != null) && (tracker1.getWinGameETA() < tracker2.getWinGameETA()))
                       {
                           //log.debug("Picking a robber victim: pnum="+i+" VP="+game.getPlayer(i).getPublicVP());
                           choice = i;
                       }
                   }
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
