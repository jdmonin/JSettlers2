/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2003-2004  Robert S. Thomas
 * Portions of this file copyright (C) 2009-2012 Jeremy D Monin <jeremy@nand.net>
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

import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCCity;
import soc.game.SOCDevCardConstants;
import soc.game.SOCDevCardSet;
import soc.game.SOCGame;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

/**
 * Moved the routines that pick what to build
 * next out of SOCRobotBrain.  Didn't want
 * to call this SOCRobotPlanner because it
 * doesn't really plan, but you could think
 * of it that way.  DM = Decision Maker
 *<P>
 * Uses the info in the {@link SOCPlayerTrackers}s.
 * One important method here is {@link #planStuff(int)}.
 *
 * @author Robert S. Thomas
 */
public class SOCRobotDM
{

  protected static final DecimalFormat df1 = new DecimalFormat("###0.00");

  protected int maxGameLength = 300;

  protected int maxETA = 99;

  protected float etaBonusFactor = (float) 0.8;

  protected float adversarialFactor = (float) 1.5;

  protected float leaderAdversarialFactor = (float) 3.0;

  protected float devCardMultiplier = (float) 2.0;

  protected float threatMultiplier = (float) 1.1;

  protected static final int LA_CHOICE = 0;
  protected static final int LR_CHOICE = 1;
  protected static final int CITY_CHOICE = 2;
  protected static final int SETTLEMENT_CHOICE = 3;

  /**
   * used in planStuff
   */
  protected static final int TWO_SETTLEMENTS = 11;
  protected static final int TWO_CITIES = 12;
  protected static final int ONE_OF_EACH = 13;
  protected static final int WIN_LA = 14;
  protected static final int WIN_LR = 15;

  /**
   * used for describing strategies
   */
  public static final int SMART_STRATEGY = 0;
  public static final int FAST_STRATEGY = 1;

  protected SOCRobotBrain brain;
  protected HashMap<Integer,SOCPlayerTracker> playerTrackers;
  protected SOCPlayerTracker ourPlayerTracker;
  protected final SOCPlayer ourPlayerData;

  /**
   * {@link #ourPlayerData}'s player number.
   * @since 2.0.00
   */
  private final int ourPlayerNumber;

  /**
   * {@link #ourPlayerData}'s building plan.
   * A stack of {@link SOCPossiblePiece}.
   * May include {@link SOCPossibleCard} to be bought.
   */
  protected Stack<SOCPossiblePiece> buildingPlan;

  protected SOCGame game;
  protected Vector<SOCPossibleRoad> threatenedRoads;

  /** {@link SOCPossibleRoad}s and/or subclass {@link SOCPossibleShip}s */
  protected Vector<SOCPossibleRoad> goodRoads;
  protected SOCPossibleRoad favoriteRoad;

  /** Threatened settlements, as calculated by {@link #scorePossibleSettlements(int, int)} */
  protected Vector<SOCPossibleSettlement> threatenedSettlements;

  /** Good settlements, as calculated by {@link #scorePossibleSettlements(int, int)} */
  protected Vector<SOCPossibleSettlement> goodSettlements;

  protected SOCPossibleSettlement favoriteSettlement;
  protected SOCPossibleCity favoriteCity;
  protected SOCPossibleCard possibleCard;


  /**
   * constructor
   *
   * @param br  the robot brain
   */
  public SOCRobotDM(SOCRobotBrain br)
  {
    brain = br;
    playerTrackers = brain.getPlayerTrackers();
    ourPlayerTracker = brain.getOurPlayerTracker();
    ourPlayerData = brain.getOurPlayerData();
    ourPlayerNumber = ourPlayerData.getPlayerNumber();
    buildingPlan = brain.getBuildingPlan();
    game = brain.getGame();

    threatenedRoads = new Vector<SOCPossibleRoad>();
    goodRoads = new Vector<SOCPossibleRoad>();
    threatenedSettlements = new Vector<SOCPossibleSettlement>();
    goodSettlements = new Vector<SOCPossibleSettlement>();
    SOCRobotParameters params = brain.getRobotParameters();
    maxGameLength = params.getMaxGameLength();
    maxETA = params.getMaxETA();
    etaBonusFactor = params.getETABonusFactor();
    adversarialFactor = params.getAdversarialFactor();
    leaderAdversarialFactor = params.getLeaderAdversarialFactor();
    devCardMultiplier = params.getDevCardMultiplier();
    threatMultiplier = params.getThreatMultiplier();
  }


  /**
   * Constructor to use if you don't want to use a brain.
   *
   * @param params  the robot parameters
   * @param pt   the player trackers
   * @param opt  our player tracker
   * @param opd  our player data
   * @param bp   our building plan
   */
  public SOCRobotDM(SOCRobotParameters params,
		    HashMap<Integer,SOCPlayerTracker> pt,
		    SOCPlayerTracker opt,
		    SOCPlayer opd,
		    Stack<SOCPossiblePiece> bp)
  {
    brain = null;
    playerTrackers = pt;
    ourPlayerTracker = opt;
    ourPlayerData = opd;
    ourPlayerNumber = opd.getPlayerNumber();
    buildingPlan = bp;
    game = ourPlayerData.getGame();

    maxGameLength = params.getMaxGameLength();
    maxETA = params.getMaxETA();
    etaBonusFactor = params.getETABonusFactor();
    adversarialFactor = params.getAdversarialFactor();
    leaderAdversarialFactor = params.getLeaderAdversarialFactor();
    devCardMultiplier = params.getDevCardMultiplier();
    threatMultiplier = params.getThreatMultiplier();

    threatenedRoads = new Vector<SOCPossibleRoad>();
    goodRoads = new Vector<SOCPossibleRoad>();
    threatenedSettlements = new Vector<SOCPossibleSettlement>();
    goodSettlements = new Vector<SOCPossibleSettlement>();
  }


  /**
   * @return favorite settlement
   */
  public SOCPossibleSettlement getFavoriteSettlement() {
    return favoriteSettlement;
  }

  /**
   * @return favorite city
   */
  public SOCPossibleCity getFavoriteCity() {
    return favoriteCity;
  }

  /**
   * @return favorite road or ship
   */
  public SOCPossibleRoad getFavoriteRoad() {
    return favoriteRoad;
  }

  /**
   * @return possible card
   */
  public SOCPossibleCard getPossibleCard() {
    return possibleCard;
  }

  /**
   * make some building plans.
   * Calls either {@link #smartGameStrategy(int[])} or {@link #dumbFastGameStrategy(int[])}.
   * Both of these will check whether this is our normal turn, or if
   * it's the 6-player board's {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
   *
   * @param strategy  an integer that determines which strategy is used (SMART_STRATEGY | FAST_STRATEGY)
   */
  public void planStuff(final int strategy)
  {
      //long startTime = System.currentTimeMillis();
    D.ebugPrintln("PLANSTUFF");

    SOCBuildingSpeedEstimate currentBSE = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
    int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast
        (ourPlayerData.getResources(), ourPlayerData.getPortFlags());

    threatenedSettlements.removeAllElements();
    goodSettlements.removeAllElements();
    threatenedRoads.removeAllElements();
    goodRoads.removeAllElements();

    favoriteRoad = null;
    favoriteSettlement = null;
    favoriteCity = null;

    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

    ///
    /// update ETAs for LR, LA, and WIN
    ///
    if ((brain != null) && brain.getDRecorder().isOn())
    {
      // clear the table
      brain.getDRecorder().eraseAllRecords();
      // record our current resources
      brain.getDRecorder().startRecording(SOCRobotClient.CURRENT_RESOURCES);
      brain.getDRecorder().record(ourPlayerData.getResources().toShortString());
      brain.getDRecorder().stopRecording();
      // start recording the current players' plans
      brain.getDRecorder().startRecording(SOCRobotClient.CURRENT_PLANS);
    }

    if (strategy == SMART_STRATEGY) {
      SOCPlayerTracker.updateWinGameETAs(playerTrackers);
    }

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      // stop recording
      brain.getDRecorder().stopRecording();
    }

    int leadersCurrentWGETA = ourPlayerTracker.getWinGameETA();
    Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext()) {
      SOCPlayerTracker tracker = trackersIter.next();
      int wgeta = tracker.getWinGameETA();
      if (wgeta < leadersCurrentWGETA) {
          leadersCurrentWGETA = wgeta;
      }
    }

    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

    ///
    /// reset scores and biggest threats for everything
    ///
    Iterator<? extends SOCPossiblePiece> posPiecesIter;
    SOCPossiblePiece posPiece;
    posPiecesIter = ourPlayerTracker.getPossibleCities().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleRoads().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }

    switch (strategy) {
    case SMART_STRATEGY:
      smartGameStrategy(currentBuildingETAs);
      break;

    case FAST_STRATEGY:
      dumbFastGameStrategy(currentBuildingETAs);
      break;
    }


    ///
    /// if we have a road building card, make sure
    /// we build two roads first
    ///
    if ((strategy == SMART_STRATEGY) &&
	(! ourPlayerData.hasPlayedDevCard()) &&
	ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
	ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0)
    {
      SOCPossibleRoad secondFavoriteRoad = null;
      Enumeration<SOCPossibleRoad> threatenedRoadEnum;
      Enumeration<SOCPossibleRoad> goodRoadEnum;
      D.ebugPrintln("*** making a plan for road building");

      ///
      /// we need to pick two roads
      ///
      if (favoriteRoad != null)
      {
	//
	//  pretend to put the favorite road down,
	//  and then score the new pos roads
	//
	SOCRoad tmpRoad;
	if (favoriteRoad instanceof SOCPossibleShip)
	    tmpRoad = new SOCShip(ourPlayerData, favoriteRoad.getCoordinates(), null);
	else
	    tmpRoad = new SOCRoad(ourPlayerData, favoriteRoad.getCoordinates(), null);

	HashMap<Integer, SOCPlayerTracker> trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);

	SOCPlayerTracker ourPlayerTrackerCopy = trackersCopy.get(new Integer(ourPlayerNumber));

	int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
	D.ebugPrintln("ourCurrentWGETACopy = "+ourCurrentWGETACopy);

	int leadersCurrentWGETACopy = ourCurrentWGETACopy;
	Iterator<SOCPlayerTracker> trackersCopyIter = trackersCopy.values().iterator();
	while (trackersCopyIter.hasNext())
	{
	  SOCPlayerTracker tracker = trackersCopyIter.next();
	  int wgeta = tracker.getWinGameETA();
	  if (wgeta < leadersCurrentWGETACopy) {
	    leadersCurrentWGETACopy = wgeta;
	  }
	}

	Enumeration<SOCPossiblePiece> newPosEnum = favoriteRoad.getNewPossibilities().elements();
	while (newPosEnum.hasMoreElements())
	{
	  SOCPossiblePiece newPos = newPosEnum.nextElement();
	  if (newPos instanceof SOCPossibleRoad)
	  {
	    newPos.resetScore();
	    // float wgetaScore = getWinGameETABonusForRoad
	    //   ((SOCPossibleRoad)newPos, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETACopy, trackersCopy);


	    D.ebugPrintln("$$$ new pos road at "+Integer.toHexString(newPos.getCoordinates())+" has a score of "+newPos.getScore());

	    if (favoriteRoad.getCoordinates() != newPos.getCoordinates())
	    {
	      if (secondFavoriteRoad == null) {
		secondFavoriteRoad = (SOCPossibleRoad)newPos;
	      } else {
		if (newPos.getScore() > secondFavoriteRoad.getScore()) {
		  secondFavoriteRoad = (SOCPossibleRoad)newPos;
		}
	      }
	    }
	  }
	}

	threatenedRoadEnum = threatenedRoads.elements();
	while (threatenedRoadEnum.hasMoreElements())
	{
	  SOCPossibleRoad threatenedRoad = threatenedRoadEnum.nextElement();
	  D.ebugPrintln("$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

	  //
	  // see how building this piece impacts our winETA
	  //
	  threatenedRoad.resetScore();
	  // float wgetaScore = getWinGameETABonusForRoad
	  //   (threatenedRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

	  D.ebugPrintln("$$$  final score = "+threatenedRoad.getScore());

	  if (favoriteRoad.getCoordinates() != threatenedRoad.getCoordinates())
	  {
	    if (secondFavoriteRoad == null) {
	      secondFavoriteRoad = threatenedRoad;
	    } else {
	      if (threatenedRoad.getScore() > secondFavoriteRoad.getScore()) {
	      secondFavoriteRoad = threatenedRoad;
	      }
	    }
	  }
	}

	goodRoadEnum = goodRoads.elements();
	while (goodRoadEnum.hasMoreElements())
	{
	  SOCPossibleRoad goodRoad = goodRoadEnum.nextElement();
	  D.ebugPrintln("$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));
	  //
	  // see how building this piece impacts our winETA
	  //
	  goodRoad.resetScore();
	  // float wgetaScore = getWinGameETABonusForRoad
	  //   (goodRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

	  D.ebugPrintln("$$$  final score = "+goodRoad.getScore());

	  if (favoriteRoad.getCoordinates() != goodRoad.getCoordinates())
	  {
	    if (secondFavoriteRoad == null) {
	      secondFavoriteRoad = goodRoad;
	    } else {
	      if (goodRoad.getScore() > secondFavoriteRoad.getScore()) {
		secondFavoriteRoad = goodRoad;
	      }
	    }
	  }
	}

	SOCPlayerTracker.undoTryPutPiece(tmpRoad, game);

	if (! buildingPlan.empty())
	{
	  SOCPossiblePiece planPeek = buildingPlan.peek();
	  if ((planPeek == null) ||
	      (! (planPeek instanceof SOCPossibleRoad)))
	  {
	    if (secondFavoriteRoad != null)
	    {
	      D.ebugPrintln("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
	      D.ebugPrintln("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
	      D.ebugPrintln("$ PUSHING "+secondFavoriteRoad);
	      buildingPlan.push(secondFavoriteRoad);
	      D.ebugPrintln("$ PUSHING "+favoriteRoad);
	      buildingPlan.push(favoriteRoad);
	    }
	  }
	  else if (secondFavoriteRoad != null)
	  {
	    SOCPossiblePiece tmp = buildingPlan.pop();
	    D.ebugPrintln("$ POPPED OFF");
	    D.ebugPrintln("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
	    D.ebugPrintln("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
	    D.ebugPrintln("$ PUSHING "+secondFavoriteRoad);
	    buildingPlan.push(secondFavoriteRoad);
	    D.ebugPrintln("$ PUSHING "+tmp);
	    buildingPlan.push(tmp);
	  }
	}
      }
    }

    //long endTime = System.currentTimeMillis();
    //System.out.println("plan time: "+(endTime-startTime));
  }

  /**
   * Plan building for the dumbFastGameStrategy ({@link #FAST_STRATEGY}).
   * uses rules to determine what to build next
   * and update {@link #buildingPlan}.
   *<P>
   * For example, if {@link #favoriteSettlement} is chosen,
   * it's chosen from {@link #ourPlayerTracker}{@link SOCPlayerTracker#getPossibleSettlements() .getPossibleSettlements()}.
   *
   * @param buildingETAs  the etas for building something
   */
  protected void dumbFastGameStrategy(final int[] buildingETAs)
  {
    D.ebugPrintln("***** dumbFastGameStrategy *****");

    // If this game is on the 6-player board, check whether we're planning for
    // the Special Building Phase.  Can't buy cards or trade in that phase.
    final boolean forSpecialBuildingPhase =
        game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != ourPlayerNumber);

    int bestETA = 500;
    SOCBuildingSpeedEstimate ourBSE = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());

    if (ourPlayerData.getTotalVP() < 5)
    {
      //
      // less than 5 points, don't consider LR or LA
      //

      //
      // score possible cities
      //
      if (ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0)
      {
          Iterator<SOCPossibleCity> posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
          while (posCitiesIter.hasNext())
          {
              SOCPossibleCity posCity = posCitiesIter.next();
              D.ebugPrintln("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
              D.ebugPrintln("Speedup = "+posCity.getSpeedupTotal());
              D.ebugPrintln("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
              if ((brain != null) && brain.getDRecorder().isOn())
              {
                  brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                  brain.getDRecorder().record("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
                  brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                  brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                  brain.getDRecorder().stopRecording();
              }
              if ((favoriteCity == null) ||
                  (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal()))
              {
                  favoriteCity = posCity;
                  bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
              }
          }
      }

      //
      // score the possible settlements
      //
      scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);

      //
      // pick something to build
      //
      Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
      while (posSetsIter.hasNext())
      {
          SOCPossibleSettlement posSet = posSetsIter.next();
          if ((brain != null) && brain.getDRecorder().isOn())
          {
              brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
              brain.getDRecorder().record("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
              brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
              brain.getDRecorder().record("ETA = "+posSet.getETA());
              Stack<SOCPossibleRoad> roadPath = posSet.getRoadPath();
              if (roadPath != null)
              {
                  brain.getDRecorder().record("Path:");
                  Iterator<SOCPossibleRoad> rpIter = roadPath.iterator();
                  while (rpIter.hasNext()) {
                      SOCPossibleRoad posRoad = rpIter.next();
                      brain.getDRecorder().record("Road at "+game.getBoard().edgeCoordToString(posRoad.getCoordinates()));
                  }
              }
              brain.getDRecorder().stopRecording();
          }
          if (posSet.getETA() < bestETA) {
              bestETA = posSet.getETA();
              favoriteSettlement = posSet;
          } else if (posSet.getETA() == bestETA) {
              if (favoriteSettlement == null) {
                  if ((favoriteCity == null) ||
                      (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal()))
                  {
                      favoriteSettlement = posSet;
                  }
              } else {
                  if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal()) {
                      favoriteSettlement = posSet;
                  }
              }
          }
      }

      if (favoriteSettlement != null)
      {
          //
          // we want to build a settlement
          //
          D.ebugPrintln("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
          buildingPlan.push(favoriteSettlement);
          if (! favoriteSettlement.getNecessaryRoads().isEmpty())
          {
              //
              // we need to build roads first
              //
              Stack<SOCPossibleRoad> roadPath = favoriteSettlement.getRoadPath();
              while (!roadPath.empty()) {
                  buildingPlan.push(roadPath.pop());
              }
          }
      } else if (favoriteCity != null) {
          //
          // we want to build a city
          //
          D.ebugPrintln("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
          buildingPlan.push(favoriteCity);
      } else {
          //
          // we can't build a settlement or city
          //
        if ((game.getNumDevCards() > 0) && ! forSpecialBuildingPhase)
        {
            //
            // buy a card if there are any left
            //
            D.ebugPrintln("Buy a card");
            SOCPossibleCard posCard = new SOCPossibleCard(ourPlayerData, buildingETAs[SOCBuildingSpeedEstimate.CARD]);
            buildingPlan.push(posCard);
        }
      }
    } else {
      //
      // we have more than 4 points
      //
      int choice = -1;
      //
      // consider Largest Army
      //
      D.ebugPrintln("Calculating Largest Army ETA");
      int laETA = 500;
      int laSize = 0;
      SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
      if (laPlayer == null) {
	///
	/// no one has largest army
	///
	laSize = 3;
      } else if (laPlayer.getPlayerNumber() == ourPlayerNumber) {
	///
	/// we have largest army
	///
	D.ebugPrintln("We have largest army");
      } else {
	laSize = laPlayer.getNumKnights() + 1;
      }
      ///
      /// figure out how many knights we need to buy
      ///
      int knightsToBuy = 0;
      if ((ourPlayerData.getNumKnights() +
	   ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT) +
	   ourPlayerData.getDevCards().getAmount(SOCDevCardSet.NEW, SOCDevCardConstants.KNIGHT))
	  < laSize)
      {
	knightsToBuy = laSize - (ourPlayerData.getNumKnights() +
				 ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
      }
      D.ebugPrintln("knightsToBuy = "+knightsToBuy);

      if (ourPlayerData.getGame().getNumDevCards() >= knightsToBuy)
      {
	///
	/// figure out how long it takes to buy this many knights
	///
	SOCResourceSet targetResources = new SOCResourceSet();
	for (int i = 0; i < knightsToBuy; i++) {
	  targetResources.add(SOCGame.CARD_SET);
	}
	try {
	  SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
	  laETA = timePair.getRolls();
	} catch (CutoffExceededException ex) {
	  laETA = 100;
	}
      } else {
	///
	/// not enough dev cards left
	///
      }
      if ((laETA < bestETA) && ! forSpecialBuildingPhase)
      {
	bestETA = laETA;
	choice = LA_CHOICE;
      }
      D.ebugPrintln("laETA = "+laETA);

      //
      // consider Longest Road
      //
      D.ebugPrintln("Calculating Longest Road ETA");
      int lrETA = 500;
      Stack<?> bestLRPath = null;
      int lrLength;
      SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();
      if ((lrPlayer != null) &&
          (lrPlayer.getPlayerNumber() == ourPlayerNumber))
      {
          ///
          /// we have longest road
          ///
          D.ebugPrintln("We have longest road");
      } else {
          if (lrPlayer == null) {
              ///
              /// no one has longest road
              ///
              lrLength = Math.max(4, ourPlayerData.getLongestRoadLength());
          } else {
              lrLength = lrPlayer.getLongestRoadLength();
          }
          Iterator<SOCLRPathData> lrPathsIter = ourPlayerData.getLRPaths().iterator();
          int depth;
          while (lrPathsIter.hasNext())
          {
              Stack<?> path;
              SOCLRPathData pathData = lrPathsIter.next();
              depth = Math.min(((lrLength + 1) - pathData.getLength()), ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD));
              path = (Stack<?>) recalcLongestRoadETAAux
                  (ourPlayerData, true, pathData.getBeginning(), pathData.getLength(), lrLength, depth);
              if ((path != null) &&
                      ((bestLRPath == null) ||
                              (path.size() < bestLRPath.size())))
              {
                  bestLRPath = path;
              }
              path = (Stack<?>) recalcLongestRoadETAAux
                  (ourPlayerData, true, pathData.getEnd(), pathData.getLength(), lrLength, depth);
              if ((path != null) &&
                      ((bestLRPath == null) ||
                              (path.size() < bestLRPath.size())))
              {
                  bestLRPath = path;
              }
          }
          if (bestLRPath != null)
          {
              //
              // calculate LR eta
              //
              D.ebugPrintln("Number of roads: "+bestLRPath.size());
              SOCResourceSet targetResources = new SOCResourceSet();
              for (int i = 0; i < bestLRPath.size(); i++) {
                  targetResources.add(SOCGame.ROAD_SET);
              }
              try {
                  SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast
                      (ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
                  lrETA = timePair.getRolls();
              } catch (CutoffExceededException ex) {
                  lrETA = 100;
              }
          }
      }
      if (lrETA < bestETA) {
          bestETA = lrETA;
          choice = LR_CHOICE;
      }
      D.ebugPrintln("lrETA = "+lrETA);

      //
      // consider possible cities
      //
      if ((ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0) &&
          (buildingETAs[SOCBuildingSpeedEstimate.CITY] <= bestETA))
      {
          Iterator<SOCPossibleCity> posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
          while (posCitiesIter.hasNext()) {
              SOCPossibleCity posCity = posCitiesIter.next();
              if ((brain != null) && brain.getDRecorder().isOn())
              {
                  brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
                  brain.getDRecorder().record("Estimate speedup of city at "
                      + game.getBoard().nodeCoordToString(posCity.getCoordinates()));
                  brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
                  brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
                  brain.getDRecorder().stopRecording();
              }
              if ((favoriteCity == null) ||
                      (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                  favoriteCity = posCity;
                  bestETA = buildingETAs[SOCBuildingSpeedEstimate.CITY];
                  choice = CITY_CHOICE;
              }
          }
      }

      //
      // consider possible settlements
      //
      if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
      {
          scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);
          Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
          while (posSetsIter.hasNext())
          {
              SOCPossibleSettlement posSet = posSetsIter.next();
              if ((brain != null) && brain.getDRecorder().isOn())
              {
                  brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
                  brain.getDRecorder().record("Estimate speedup of stlmt at "
                      + game.getBoard().nodeCoordToString(posSet.getCoordinates()));
                  brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
                  brain.getDRecorder().record("ETA = "+posSet.getETA());
                  Stack<SOCPossibleRoad> roadPath = posSet.getRoadPath();
                  if (roadPath!= null) {
                      brain.getDRecorder().record("Path:");
                      Iterator<SOCPossibleRoad> rpIter = roadPath.iterator();
                      while (rpIter.hasNext()) {
                          SOCPossibleRoad posRoad = rpIter.next();
                          brain.getDRecorder().record("Road at "+game.getBoard().edgeCoordToString(posRoad.getCoordinates()));
                      }
                  }
                  brain.getDRecorder().stopRecording();
              }

              if ((posSet.getRoadPath() == null) ||
                      (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= posSet.getRoadPath().size()))
              {
                  if (posSet.getETA() < bestETA) {
                      bestETA = posSet.getETA();
                      favoriteSettlement = posSet;
                      choice = SETTLEMENT_CHOICE;
                  } else if (posSet.getETA() == bestETA) {
                      if (favoriteSettlement == null) {
                          if ((favoriteCity == null) ||
                                  (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
                              favoriteSettlement = posSet;
                              choice = SETTLEMENT_CHOICE;
                          }
                      } else {
                          if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal()) {
                              favoriteSettlement = posSet;
                          }
                      }
                  }
              }
          }
      }

      //
      // pick something to build
      //
      switch (choice)
      {
      case LA_CHOICE:
        D.ebugPrintln("Picked LA");
        if (! forSpecialBuildingPhase)
        {
            for (int i = 0; i < knightsToBuy; i++)
            {
                SOCPossibleCard posCard = new SOCPossibleCard(ourPlayerData, 1);
                buildingPlan.push(posCard);
            }
        }
        break;

      case LR_CHOICE:
        D.ebugPrintln("Picked LR");
        while (!bestLRPath.empty()) {
          SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
          D.ebugPrintln("LR road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
          buildingPlan.push(pr);
        }
        break;

      case CITY_CHOICE:
          D.ebugPrintln("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
          buildingPlan.push(favoriteCity);
          break;

      case SETTLEMENT_CHOICE:
          D.ebugPrintln("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
          buildingPlan.push(favoriteSettlement);
          if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
              //
              // we need to build roads first
              //
              Stack<SOCPossibleRoad> roadPath = favoriteSettlement.getRoadPath();
              while (!roadPath.empty()) {
                  SOCPossibleRoad pr = roadPath.pop();
                  D.ebugPrintln("Nec road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
                  buildingPlan.push(pr);
              }
          }
      }
    }
  }

  /**
   * score all possible settlements by getting their speedup total
   * calculate ETA by finding shortest path and then using a
   * SOCBuildingSpeedEstimate to find the ETA
   *
   * @param settlementETA  eta for building a settlement from now
   */
  protected void scoreSettlementsForDumb(final int settlementETA, SOCBuildingSpeedEstimate ourBSE)
  {
    D.ebugPrintln("-- scoreSettlementsForDumb --");
    Queue<Pair<SOCPossibleRoad,?>> queue = new Queue<Pair<SOCPossibleRoad,?>>();
    Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext())
    {
      SOCPossibleSettlement posSet = posSetsIter.next();
      D.ebugPrintln("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
      D.ebugPrintln("***    speedup total = "+posSet.getSpeedupTotal());

      ///
      /// find the shortest path to this settlement
      ///
      Vector<SOCPossibleRoad> necRoadVec = posSet.getNecessaryRoads();
      if (! necRoadVec.isEmpty())
      {
          queue.clear();
          Iterator<SOCPossibleRoad> necRoadsIter = necRoadVec.iterator();
          while (necRoadsIter.hasNext()) {
              SOCPossibleRoad necRoad = necRoadsIter.next();
              D.ebugPrintln("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad.getCoordinates()));
              queue.put(new Pair<SOCPossibleRoad,Object>(necRoad, null));
          }
          //
          // Do a BFS of the necessary road paths looking for the shortest one.
          //
          while (! queue.empty())
          {
              Pair<SOCPossibleRoad,?> dataPair = queue.get();
              SOCPossibleRoad curRoad = dataPair.getA();
              D.ebugPrintln("-- current road at "+game.getBoard().edgeCoordToString(curRoad.getCoordinates()));
              Vector<SOCPossibleRoad> necRoads = curRoad.getNecessaryRoads();
              if (necRoads.isEmpty())
              {
                  //
                  // we have a path
                  //
                  D.ebugPrintln("Found a path!");
                  Stack<SOCPossibleRoad> path = new Stack<SOCPossibleRoad>();
                  path.push(curRoad);
                  Pair<SOCPossibleRoad,?> curPair = (Pair)dataPair.getB();
                  D.ebugPrintln("curPair = "+curPair);
                  while (curPair != null) {
                      path.push(curPair.getA());
                      curPair = (Pair)curPair.getB();
                  }
                  posSet.setRoadPath(path);
                  queue.clear();
                  D.ebugPrintln("Done setting path.");
              } else {
                  necRoadsIter = necRoads.iterator();
                  while (necRoadsIter.hasNext()) {
                      SOCPossibleRoad necRoad2 = necRoadsIter.next();
                      D.ebugPrintln("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad2.getCoordinates()));
                      queue.put(new Pair<SOCPossibleRoad,Pair<SOCPossibleRoad,?>>(necRoad2, dataPair));
                  }
              }
          }
          D.ebugPrintln("Done searching for path.");

          //
          // calculate ETA
          //
          SOCResourceSet targetResources = new SOCResourceSet();
          targetResources.add(SOCGame.SETTLEMENT_SET);
          int pathLength = 0;
          Stack<SOCPossibleRoad> path = posSet.getRoadPath();
          if (path != null) {
              pathLength = path.size();
          }
          for (int i = 0; i < pathLength; i++) {
              targetResources.add(SOCGame.ROAD_SET);
          }
          try {
              SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast
                  (ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
              posSet.setETA(timePair.getRolls());
          } catch (CutoffExceededException ex) {
              posSet.setETA(100);
          }
      } else {
          //
          // no roads are necessary
          //
          posSet.setRoadPath(null);
          posSet.setETA(settlementETA);
      }
      D.ebugPrintln("Settlement ETA = "+posSet.getETA());
    }
  }

  /**
   * Does a depth first search from the end point of the longest
   * path in a graph of nodes and returns how many roads would
   * need to be built to take longest road.
   *<P>
   * Combined implementation for use by SOCRobotDM and {@link SOCPlayerTracker}.
   *
   * @param pl            Calculate this player's longest road;
   *             typically SOCRobotDM.ourPlayerData or SOCPlayerTracker.player
   * @param wantsStack    If true, return the Stack; otherwise, return numRoads.
   * @param startNode     the path endpoint
   * @param pathLength    the length of that path
   * @param lrLength      length of longest road in the game
   * @param searchDepth   how many roads out to search
   *
   * @return if <tt>wantsStack</tt>: a {@link Stack} containing the path of roads with the last one on top, or null if it can't be done.
   *         If ! <tt>wantsStack</tt>: Integer: the number of roads needed, or 500 if it can't be done
   */
  static Object recalcLongestRoadETAAux
      (SOCPlayer pl, final boolean wantsStack, final int startNode,
       final int pathLength, final int lrLength, final int searchDepth)
  {
    // D.ebugPrintln("=== recalcLongestRoadETAAux("+Integer.toHexString(startNode)+","+pathLength+","+lrLength+","+searchDepth+")");

    //
    // We're doing a depth first search of all possible road paths.
    // For similar code, see SOCPlayer.calcLongestRoad2
    //
    int longest = 0;
    int numRoads = 500;
    Pair<NodeLenVis<Integer>, ?> bestPathNode = null;
    final SOCBoard board = pl.getGame().getBoard();
    Stack<Pair<NodeLenVis<Integer>, ?>> pending = new Stack<Pair<NodeLenVis<Integer>, ?>>();  // as-yet unvisited
    pending.push(new Pair<NodeLenVis<Integer>, Object>
        (new NodeLenVis<Integer>(startNode, pathLength, new Vector<Integer>()), null));

    while (! pending.empty())
    {
      Pair<NodeLenVis<Integer>, ?> dataPair = pending.pop();
      NodeLenVis<Integer> curNode = dataPair.getA();
      //D.ebugPrintln("curNode = "+curNode);

      final int coord = curNode.node;
      int len = curNode.len;
      Vector<Integer> visited = curNode.vis;
      boolean pathEnd = false;

      //
      // check for road blocks
      //
      if (len > 0)
      {
          final int pn = pl.getPlayerNumber();
          SOCPlayingPiece p = board.settlementAtNode(coord);
          if ((p != null)
              && (p.getPlayerNumber() != pn))
          {
              pathEnd = true;
              //D.ebugPrintln("^^^ path end at "+Integer.toHexString(coord));
          }
      }

      if (!pathEnd)
      {
          //
          // check if we've connected to another road graph
          //
          Iterator<SOCLRPathData> lrPathsIter = pl.getLRPaths().iterator();
          while (lrPathsIter.hasNext())
          {
              SOCLRPathData pathData = lrPathsIter.next();
              if ((startNode != pathData.getBeginning())
                      && (startNode != pathData.getEnd())
                      && ((coord == pathData.getBeginning())
                              || (coord == pathData.getEnd())))
              {
                  pathEnd = true;
                  len += pathData.getLength();
                  //D.ebugPrintln("connecting to another path: " + pathData);
                  //D.ebugPrintln("len = " + len);

                  break;
              }
          }
      }

      if (!pathEnd)
      {
          //
          // (len - pathLength) = how many new roads we've built
          //
          if ((len - pathLength) >= searchDepth)
          {
              pathEnd = true;
          }
          //D.ebugPrintln("Reached search depth");
      }

      if (!pathEnd)
      {
        /**
         * For each of the 3 adjacent edges of coord's node,
         * check for unvisited legal road possibilities.
         * When they are found, push that edge's far-end node
         * onto the pending stack.
         */
        pathEnd = true;

        for (int dir = 0; dir < 3; ++dir)
        {
            int j = board.getAdjacentEdgeToNode(coord, dir);
            if (pl.isLegalRoad(j))
            {
                Integer edge = new Integer(j);
                boolean match = false;

                for (Enumeration<Integer> ev = visited.elements(); ev.hasMoreElements(); )
                {
                    Integer vis = ev.nextElement();
                    if (vis.equals(edge))
                    {
                        match = true;
                        break;
                    }
                }

                if (! match)
                {
                    Vector<Integer> newVis = new Vector<Integer>(visited);
                    newVis.addElement(edge);

                    j = board.getAdjacentNodeToNode(coord, dir);  // edge's other node
                    pending.push(new Pair<NodeLenVis<Integer>, Pair<NodeLenVis<Integer>, ?>>
                        (new NodeLenVis<Integer>(j, len+1, newVis), dataPair));
                    pathEnd = false;
                }
            }
        }
      }

      if (pathEnd)
      {
	if (len > longest)
	{
	  longest = len;
	  numRoads = curNode.len - pathLength;
	  bestPathNode = dataPair;
	}
	else if ((len == longest) && (curNode.len < numRoads))
	{
	  numRoads = curNode.len - pathLength;
	  bestPathNode = dataPair;
	}
      }
    }

    if (! wantsStack)
    {
        // As used by SOCPlayerTracker.
        int rv;
        if (longest > lrLength)
            rv = numRoads;
        else
            rv = 500;
        return new Integer(rv);  // <-- Early return: ! wantsStack ---
    }

    if ((longest > lrLength) && (bestPathNode != null))
    {
      //D.ebugPrintln("Converting nodes to road coords.");
      //
      // return the path in a stack with the last road on top
      //
      //
      // first, convert pairs of node coords to road coords (edge coords):
      //
      Stack<SOCPossibleRoad> temp = new Stack<SOCPossibleRoad>();
      SOCPossibleRoad posRoad;
      int coordA, coordB;
      Pair<NodeLenVis<Integer>, ?> cur, parent;
      cur = bestPathNode;
      parent = (Pair)bestPathNode.getB();
      while (parent != null)
      {
          coordA = ((NodeLenVis<?>)cur.getA()).node;
          coordB = ((NodeLenVis<?>)parent.getA()).node;
          posRoad = new SOCPossibleRoad(pl, board.getEdgeBetweenAdjacentNodes(coordA, coordB), new Vector<SOCPossibleRoad>());
          temp.push(posRoad);
          cur = parent;
          parent = (Pair) parent.getB();
      }
      //
      // reverse the order of the roads so that the last one is on top
      //
      Stack<SOCPossibleRoad> path = new Stack<SOCPossibleRoad>();
      while (!temp.empty())
          path.push(temp.pop());

      return path;
    }

    return null;
  }

  /**
   * Plan building for the smart game strategy ({@link #SMART_STRATEGY}).
   * use WGETA to determine best move
   * and update {@link #buildingPlan}.
   *<P>
   * For example, if {@link #favoriteSettlement} is chosen,
   * it's chosen from {@link #goodSettlements} or {{@link #threatenedSettlements}.
   *
   * @param buildingETAs  the etas for building something
   */
  protected void smartGameStrategy(final int[] buildingETAs)
  {
    D.ebugPrintln("***** smartGameStrategy *****");

    // If this game is on the 6-player board, check whether we're planning for
    // the Special Building Phase.  Can't buy cards or trade in that phase.
    final boolean forSpecialBuildingPhase =
        game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != ourPlayerNumber);

    //
    // save the lr paths list to restore later
    //
    Vector<SOCLRPathData>[] savedLRPaths = new Vector[game.maxPlayers];
    for (int pn = 0; pn < game.maxPlayers; pn++) {
      savedLRPaths[pn] = new Vector<SOCLRPathData>(game.getPlayer(pn).getLRPaths());
    }

    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    D.ebugPrintln("ourCurrentWGETA = "+ourCurrentWGETA);

    int leadersCurrentWGETA = ourCurrentWGETA;
    Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext()) {
      SOCPlayerTracker tracker = trackersIter.next();
      int wgeta = tracker.getWinGameETA();
      if (wgeta < leadersCurrentWGETA) {
	leadersCurrentWGETA = wgeta;
      }
    }

    /*
    boolean goingToPlayRB = false;
    if (!ourPlayerData.hasPlayedDevCard() &&
	ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
	ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
      goingToPlayRB = true;
    }
    */

    ///
    /// score the possible settlements
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
      scorePossibleSettlements(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], leadersCurrentWGETA);
    }

    ///
    /// collect roads that we can build now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0)
    {
      Iterator<SOCPossibleRoad> posRoadsIter = ourPlayerTracker.getPossibleRoads().values().iterator();
      while (posRoadsIter.hasNext()) {
	SOCPossibleRoad posRoad = posRoadsIter.next();
	if ((posRoad.getNecessaryRoads().isEmpty()) &&
	    (!threatenedRoads.contains(posRoad)) &&
	    (!goodRoads.contains(posRoad))) {
	  goodRoads.addElement(posRoad);
	}
      }
    }

    /*
    ///
    /// check everything
    ///
    Enumeration threatenedSetEnum = threatenedSettlements.elements();
    while (threatenedSetEnum.hasMoreElements()) {
      SOCPossibleSettlement threatenedSet = (SOCPossibleSettlement)threatenedSetEnum.nextElement();
      D.ebugPrintln("*** threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());
      if (threatenedSet.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialSettlement(threatenedSet.getCoordinates())) {
	D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
	//System.exit(0);
      }
    }
    Enumeration goodSetEnum = goodSettlements.elements();
    while (goodSetEnum.hasMoreElements()) {
      SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetEnum.nextElement();
      D.ebugPrintln("*** good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());
      if (goodSet.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialSettlement(goodSet.getCoordinates())) {
	D.ebugPrintln("POTENTIAL SETTLEMENT ERROR");
	//System.exit(0);
      }
    }
    Enumeration threatenedRoadEnum = threatenedRoads.elements();
    while (threatenedRoadEnum.hasMoreElements()) {
      SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
      D.ebugPrintln("*** threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates())+" has a score of "+threatenedRoad.getScore());
      if (threatenedRoad.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialRoad(threatenedRoad.getCoordinates())) {
	D.ebugPrintln("POTENTIAL ROAD ERROR");
	//System.exit(0);
      }
    }
    Enumeration goodRoadEnum = goodRoads.elements();
    while (goodRoadEnum.hasMoreElements()) {
      SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
      D.ebugPrintln("*** good road at "+Integer.toHexString(goodRoad.getCoordinates())+" has a score of "+goodRoad.getScore());
      if (goodRoad.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialRoad(goodRoad.getCoordinates())) {
	D.ebugPrintln("POTENTIAL ROAD ERROR");
	//System.exit(0);
      }
    }
    */

    D.ebugPrintln("PICKING WHAT TO BUILD");

    ///
    /// pick what we want to build
    ///

    ///
    /// pick a settlement that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
    {
      Iterator<SOCPossibleSettlement> threatenedSetIter = threatenedSettlements.iterator();
      while (threatenedSetIter.hasNext())
      {
	SOCPossibleSettlement threatenedSet = threatenedSetIter.next();
	if (threatenedSet.getNecessaryRoads().isEmpty()) {
	  D.ebugPrintln("$$$$$ threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());

	  if ((favoriteSettlement == null) ||
	      (threatenedSet.getScore() > favoriteSettlement.getScore())) {
	    favoriteSettlement = threatenedSet;
	  }
	}
      }

      Iterator<SOCPossibleSettlement> goodSetIter = goodSettlements.iterator();
      while (goodSetIter.hasNext())
      {
	SOCPossibleSettlement goodSet = goodSetIter.next();
	if (goodSet.getNecessaryRoads().isEmpty()) {
	  D.ebugPrintln("$$$$$ good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());

	  if ((favoriteSettlement == null) ||
	      (goodSet.getScore() > favoriteSettlement.getScore())) {
	    favoriteSettlement = goodSet;
	  }
	}
      }
    }

    //
    // restore the LRPath list
    //
    D.ebugPrintln("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < game.maxPlayers; pn++) {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    }

    ///
    /// pick a road that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0)
    {
      Iterator<SOCPossibleRoad> threatenedRoadIter = threatenedRoads.iterator();
      while (threatenedRoadIter.hasNext()) {
	SOCPossibleRoad threatenedRoad = threatenedRoadIter.next();
	D.ebugPrintln("$$$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("ROAD"+threatenedRoad.getCoordinates());
	  brain.getDRecorder().record("Estimate value of road at "
	      + game.getBoard().edgeCoordToString(threatenedRoad.getCoordinates()));
	}

	//
	// see how building this piece impacts our winETA
	//
	threatenedRoad.resetScore();
	float wgetaScore = getWinGameETABonusForRoad
	    (threatenedRoad, buildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().stopRecording();
	}

	D.ebugPrintln("wgetaScore = "+wgetaScore);

	if (favoriteRoad == null) {
	  favoriteRoad = threatenedRoad;
	} else {
	  if (threatenedRoad.getScore() > favoriteRoad.getScore()) {
	    favoriteRoad = threatenedRoad;
	  }
	}
      }
      Iterator<SOCPossibleRoad> goodRoadIter = goodRoads.iterator();
      while (goodRoadIter.hasNext())
      {
	SOCPossibleRoad goodRoad = goodRoadIter.next();
	D.ebugPrintln("$$$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("ROAD"+goodRoad.getCoordinates());
	  brain.getDRecorder().record("Estimate value of road at "
	      + game.getBoard().edgeCoordToString(goodRoad.getCoordinates()));
	}

	//
	// see how building this piece impacts our winETA
	//
	goodRoad.resetScore();
	final int etype =
	    (goodRoad instanceof SOCPossibleShip)
	    ? SOCBuildingSpeedEstimate.ROAD
	    : SOCBuildingSpeedEstimate.SHIP;
	float wgetaScore = getWinGameETABonusForRoad(goodRoad, buildingETAs[etype], leadersCurrentWGETA, playerTrackers);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().stopRecording();
	}

	D.ebugPrintln("wgetaScore = "+wgetaScore);

	if (favoriteRoad == null) {
	  favoriteRoad = goodRoad;
	} else {
	  if (goodRoad.getScore() > favoriteRoad.getScore()) {
	    favoriteRoad = goodRoad;
	  }
	}
      }
    }

    //
    // restore the LRPath list
    //
    D.ebugPrintln("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < game.maxPlayers; pn++) {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    }

    ///
    /// pick a city that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0)
    {
      HashMap<Integer, SOCPlayerTracker> trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
      SOCPlayerTracker ourTrackerCopy = trackersCopy.get(new Integer(ourPlayerNumber));
      int originalWGETAs[] = new int[game.maxPlayers];
      int WGETAdiffs[] = new int[game.maxPlayers];
      Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();
      int bestWGETA = 1000;
      // int bonus = 0;

      Iterator<SOCPossibleCity> posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
      while (posCitiesIter.hasNext())
      {
	SOCPossibleCity posCity = posCitiesIter.next();
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
	  brain.getDRecorder().record("Estimate value of city at "
	      + game.getBoard().nodeCoordToString(posCity.getCoordinates()));
	}

	//
	// see how building this piece impacts our winETA
	//
	leaders.clear();
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().suspend();
	}
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);

	// TODO refactor? This section is like a copy of calcWGETABonus, with something added in the middle

	Iterator<SOCPlayerTracker> trackersBeforeIter = trackersCopy.values().iterator();
	while (trackersBeforeIter.hasNext())
	{
	  SOCPlayerTracker trackerBefore = trackersBeforeIter.next();
	  final int pn = trackerBefore.getPlayer().getPlayerNumber();
	  D.ebugPrintln("$$$ win game ETA for player " + pn + " = " + trackerBefore.getWinGameETA());
	  originalWGETAs[pn] = trackerBefore.getWinGameETA();
	  WGETAdiffs[pn] = trackerBefore.getWinGameETA();
	  if (trackerBefore.getWinGameETA() < bestWGETA) {
	    bestWGETA = trackerBefore.getWinGameETA();
	    leaders.removeAllElements();
	    leaders.addElement(trackerBefore);
	  } else if (trackerBefore.getWinGameETA() == bestWGETA) {
	    leaders.addElement(trackerBefore);
	  }
	}
	D.ebugPrintln("^^^^ bestWGETA = "+bestWGETA);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().resume();
	}
	//
	// place the city
	//
	SOCCity tmpCity = new SOCCity(ourPlayerData, posCity.getCoordinates(), null);
	game.putTempPiece(tmpCity);

	ourTrackerCopy.addOurNewCity(tmpCity);

	SOCPlayerTracker.updateWinGameETAs(trackersCopy);

	float wgetaScore = calcWGETABonusAux(originalWGETAs, trackersCopy, leaders);

	//
	// remove the city
	//
	ourTrackerCopy.undoAddOurNewCity(posCity);
	game.undoPutTempPiece(tmpCity);

	D.ebugPrintln("*** ETA for city = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	}

	float etaBonus = getETABonus(buildingETAs[SOCBuildingSpeedEstimate.CITY], leadersCurrentWGETA, wgetaScore);
	D.ebugPrintln("etaBonus = "+etaBonus);

	posCity.addToScore(etaBonus);
	//posCity.addToScore(wgetaScore);

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
	  brain.getDRecorder().record("Total city score = "+df1.format(etaBonus));
	  brain.getDRecorder().stopRecording();
	}

	D.ebugPrintln("$$$  final score = "+posCity.getScore());

	D.ebugPrintln("$$$$$ possible city at "+Integer.toHexString(posCity.getCoordinates())+" has a score of "+posCity.getScore());

	if ((favoriteCity == null) ||
	    (posCity.getScore() > favoriteCity.getScore())) {
	  favoriteCity = posCity;
	}
      }
    }

    if (favoriteSettlement != null) {
      D.ebugPrintln("### FAVORITE SETTLEMENT IS AT "+Integer.toHexString(favoriteSettlement.getCoordinates()));
      D.ebugPrintln("###   WITH A SCORE OF "+favoriteSettlement.getScore());
      D.ebugPrintln("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]);
      D.ebugPrintln("###   WITH A TOTAL SPEEDUP OF "+favoriteSettlement.getSpeedupTotal());
    }

    if (favoriteCity != null) {
      D.ebugPrintln("### FAVORITE CITY IS AT "+Integer.toHexString(favoriteCity.getCoordinates()));
      D.ebugPrintln("###   WITH A SCORE OF "+favoriteCity.getScore());
      D.ebugPrintln("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
      D.ebugPrintln("###   WITH A TOTAL SPEEDUP OF "+favoriteCity.getSpeedupTotal());
    }

    final int roadetatype = ((favoriteRoad != null) && (favoriteRoad instanceof SOCPossibleShip))
        ? SOCBuildingSpeedEstimate.SHIP
        : SOCBuildingSpeedEstimate.ROAD;

    if (favoriteRoad != null) {
      D.ebugPrintln("### FAVORITE ROAD IS AT "+Integer.toHexString(favoriteRoad.getCoordinates()));
      D.ebugPrintln("###   WITH AN ETA OF "+buildingETAs[roadetatype]);
      D.ebugPrintln("###   WITH A SCORE OF "+favoriteRoad.getScore());
    }

    int pick = -1;  // piece type, if any, pushed onto buildingPlan

    ///
    /// if the best settlement can wait, and the best road can wait,
    /// and the city is the best speedup and eta, then build the city
    ///
    if ((favoriteCity != null) &&
	(ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0) &&
	(favoriteCity.getScore() > 0) &&
	((favoriteSettlement == null) ||
	 (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) ||
	 (favoriteCity.getScore() > favoriteSettlement.getScore()) ||
	 ((favoriteCity.getScore() == favoriteSettlement.getScore()) &&
	  (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]))) &&
	((favoriteRoad == null) ||
	 (ourPlayerData.getNumPieces(favoriteRoad.getType()) == 0) ||
	 (favoriteCity.getScore() > favoriteRoad.getScore()) ||
	 ((favoriteCity.getScore() == favoriteRoad.getScore()) &&
	  (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[roadetatype]))))
    {
      D.ebugPrintln("### PICKED FAVORITE CITY");
      pick = SOCPlayingPiece.CITY;
      D.ebugPrintln("$ PUSHING "+favoriteCity);
      buildingPlan.push(favoriteCity);
    }

    ///
    /// if there is a road with a better score than
    /// our favorite settlement, then build the road,
    /// else build the settlement
    ///
    else if ((favoriteRoad != null) &&
	     (ourPlayerData.getNumPieces(favoriteRoad.getType()) > 0) &&
	     (favoriteRoad.getScore() > 0) &&
	     ((favoriteSettlement == null) ||
	      (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) ||
	      (favoriteSettlement.getScore() < favoriteRoad.getScore())))
    {
      D.ebugPrintln("### PICKED FAVORITE ROAD");
      pick = SOCPlayingPiece.ROAD;
      D.ebugPrintln("$ PUSHING "+favoriteRoad);
      buildingPlan.push(favoriteRoad);
    }
    else if ((favoriteSettlement != null)
             && (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0))
    {
      D.ebugPrintln("### PICKED FAVORITE SETTLEMENT");
      pick = SOCPlayingPiece.SETTLEMENT;
      D.ebugPrintln("$ PUSHING "+favoriteSettlement);
      buildingPlan.push(favoriteSettlement);
    }
    ///
    /// if buying a card is better than building...
    ///

    //
    // see how buying a card improves our win game ETA
    //
    if ((game.getNumDevCards() > 0) && ! forSpecialBuildingPhase)
    {
      if ((brain != null) && (brain.getDRecorder().isOn())) {
	brain.getDRecorder().startRecording("DEVCARD");
	brain.getDRecorder().record("Estimate value of a dev card");
      }

      possibleCard = getDevCardScore(buildingETAs[SOCBuildingSpeedEstimate.CARD], leadersCurrentWGETA);
      float devCardScore = possibleCard.getScore();
      D.ebugPrintln("### DEV CARD SCORE: "+devCardScore);
      if ((brain != null) && (brain.getDRecorder().isOn())) {
	brain.getDRecorder().stopRecording();
      }

      if ((pick == -1) ||
	  ((pick == SOCPlayingPiece.CITY) &&
	   (devCardScore > favoriteCity.getScore())) ||
	  (((pick == SOCPlayingPiece.ROAD) || (pick == SOCPlayingPiece.SHIP)) &&  // TODO may not be accurate to combine ships,roads here
	   (devCardScore > favoriteRoad.getScore())) ||
	  ((pick == SOCPlayingPiece.SETTLEMENT) &&
	   (devCardScore > favoriteSettlement.getScore()))) {
	D.ebugPrintln("### BUY DEV CARD");

	if (pick != -1) {
	  buildingPlan.pop();
	  D.ebugPrintln("$ POPPED OFF SOMETHING");
	}

	D.ebugPrintln("$ PUSHING "+possibleCard);
	buildingPlan.push(possibleCard);
      }
    }
  }


  /**
   * Score possible settlements for smartStrategy,
   * from {@link #ourPlayerTracker}{@link SOCPlayerTracker#getPossibleSettlements() .getPossibleSettlements()}
   * into {@link #threatenedSettlements} and {@link #goodSettlements};
   * calculate those settlements' {@link SOCPossiblePiece#getScore()}s
   */
  protected void scorePossibleSettlements(final int settlementETA, final int leadersCurrentWGETA)
  {
    D.ebugPrintln("****** scorePossibleSettlements");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

    /*
    boolean goingToPlayRB = false;
    if (!ourPlayerData.hasPlayedDevCard() &&
	ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
	ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
      goingToPlayRB = true;
    }
    */

    Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext())
    {
      SOCPossibleSettlement posSet = posSetsIter.next();
      D.ebugPrintln("*** scoring possible settlement at "+Integer.toHexString(posSet.getCoordinates()));
      if (!threatenedSettlements.contains(posSet)) {
          threatenedSettlements.addElement(posSet);
      } else if (!goodSettlements.contains(posSet)) {
          goodSettlements.addElement(posSet);
      }

      //
      // only consider settlements we can build now
      //
      Vector<SOCPossibleRoad> necRoadVec = posSet.getNecessaryRoads();
      if (necRoadVec.isEmpty())
      {
	D.ebugPrintln("*** no roads needed");
	//
	//  no roads needed
	//
	//
	//  get wgeta score
	//
        SOCBoard board = game.getBoard();
	SOCSettlement tmpSet = new SOCSettlement(ourPlayerData, posSet.getCoordinates(), board);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
	  brain.getDRecorder().record("Estimate value of settlement at "+board.nodeCoordToString(posSet.getCoordinates()));
	}

	HashMap<Integer, SOCPlayerTracker> trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);
	float wgetaScore = calcWGETABonus(playerTrackers, trackersCopy);
	D.ebugPrintln("***  wgetaScore = "+wgetaScore);

	D.ebugPrintln("*** ETA for settlement = "+settlementETA);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("ETA = "+settlementETA);
	}

	float etaBonus = getETABonus(settlementETA, leadersCurrentWGETA, wgetaScore);
	D.ebugPrintln("etaBonus = "+etaBonus);

	//posSet.addToScore(wgetaScore);
	posSet.addToScore(etaBonus);

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
	  brain.getDRecorder().record("Total settlement score = "+df1.format(etaBonus));
	  brain.getDRecorder().stopRecording();
	}

	SOCPlayerTracker.undoTryPutPiece(tmpSet, game);
      }
    }
  }

  /**
   * add a bonus to the possible piece score based
   * on the change in win game ETA
   *
   * @param posPiece  the possible piece that we're scoring
   */
  protected float getWinGameETABonus(final SOCPossiblePiece posPiece)
  {
    HashMap<Integer, SOCPlayerTracker> trackersCopy = null;
    SOCSettlement tmpSet = null;
    SOCCity tmpCity = null;
    SOCRoad tmpRoad = null;  // road or ship
    float bonus = 0;

    D.ebugPrintln("--- before [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    D.ebugPrintln("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintln("--- before [end] ---");

    switch (posPiece.getType())
    {
    case SOCPossiblePiece.SETTLEMENT:
      tmpSet = new SOCSettlement(ourPlayerData, posPiece.getCoordinates(), null);
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
      break;

    case SOCPossiblePiece.CITY:
      trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
      tmpCity = new SOCCity(ourPlayerData, posPiece.getCoordinates(), null);
      game.putTempPiece(tmpCity);
      SOCPlayerTracker trackerCopy = trackersCopy.get(new Integer(ourPlayerNumber));
      if (trackerCopy != null) {
	trackerCopy.addOurNewCity(tmpCity);
      }
      break;

    case SOCPossiblePiece.ROAD:
      tmpRoad = new SOCRoad(ourPlayerData, posPiece.getCoordinates(), null);
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
      break;

    case SOCPossiblePiece.SHIP:
      tmpRoad = new SOCShip(ourPlayerData, posPiece.getCoordinates(), null);
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
      break;
    }

    //trackersCopyIter = trackersCopy.iterator();
    //while (trackersCopyIter.hasNext()) {
    //	SOCPlayerTracker trackerCopy = (SOCPlayerTracker)trackersCopyIter.next();
    //	trackerCopy.updateThreats(trackersCopy);
    //}

    D.ebugPrintln("--- after [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(trackersCopy);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);

    float WGETABonus = calcWGETABonus(playerTrackers, trackersCopy);
    D.ebugPrintln("$$$ win game ETA bonus : +"+WGETABonus);
    bonus = WGETABonus;

    D.ebugPrintln("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintln("--- after [end] ---");

    switch (posPiece.getType())
    {
    case SOCPossiblePiece.SETTLEMENT:
      SOCPlayerTracker.undoTryPutPiece(tmpSet, game);
      break;

    case SOCPossiblePiece.CITY:
      game.undoPutTempPiece(tmpCity);
      break;

    case SOCPossiblePiece.SHIP:  // fall through to ROAD
    case SOCPossiblePiece.ROAD:
      SOCPlayerTracker.undoTryPutPiece(tmpRoad, game);
      break;
    }

    D.ebugPrintln("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintln("--- cleanup done ---");

    return bonus;
  }


  /**
   * add a bonus to the road score based on the change in
   * win game ETA for this one road
   *
   * @param posRoad  the possible piece that we're scoring
   * @param roadETA  the eta for the road
   * @param leadersCurrentWGETA  the leaders current WGETA
   * @param playerTrackers  the player trackers (for figuring out road building plan and bonus/ETA)
   */
  protected float getWinGameETABonusForRoad
      (final SOCPossibleRoad posRoad, final int roadETA, final int leadersCurrentWGETA,
       HashMap<Integer, SOCPlayerTracker> playerTrackers)
  {
    D.ebugPrintln("--- addWinGameETABonusForRoad");
    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    D.ebugPrintln("ourCurrentWGETA = "+ourCurrentWGETA);


    HashMap<Integer, SOCPlayerTracker> trackersCopy = null;
    SOCRoad tmpRoad1 = null;

    D.ebugPrintln("--- before [start] ---");
    SOCResourceSet originalResources = ourPlayerData.getResources().copy();
    SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    D.ebugPrintln("--- before [end] ---");
    try {
      SOCResSetBuildTimePair btp = estimate.calculateRollsFast
          (ourPlayerData.getResources(), SOCGame.ROAD_SET, 50, ourPlayerData.getPortFlags());
      btp.getResources().subtract(SOCGame.ROAD_SET);
      ourPlayerData.getResources().setAmounts(btp.getResources());
    } catch (CutoffExceededException e) {
      D.ebugPrintln("crap in getWinGameETABonusForRoad - "+e);
    }
    tmpRoad1 = new SOCRoad(ourPlayerData, posRoad.getCoordinates(), null);
    trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad1, game, playerTrackers);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);
    float score = calcWGETABonus(playerTrackers, trackersCopy);

    if (!posRoad.getThreats().isEmpty()) {
      score *= threatMultiplier;
      D.ebugPrintln("***  (THREAT MULTIPLIER) score * "+threatMultiplier+" = "+score);
    }
    D.ebugPrintln("*** ETA for road = "+roadETA);
    float etaBonus = getETABonus(roadETA, leadersCurrentWGETA, score);
    D.ebugPrintln("$$$ score = "+score);
    D.ebugPrintln("etaBonus = "+etaBonus);
    posRoad.addToScore(etaBonus);

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("ETA = "+roadETA);
      brain.getDRecorder().record("WGETA Score = "+df1.format(score));
      brain.getDRecorder().record("Total road score = "+df1.format(etaBonus));
    }

    D.ebugPrintln("--- after [end] ---");
    SOCPlayerTracker.undoTryPutPiece(tmpRoad1, game);
    ourPlayerData.getResources().clear();
    ourPlayerData.getResources().add(originalResources);
    D.ebugPrintln("--- cleanup done ---");

    return etaBonus;
  }

  /**
   * Calc the win game ETA bonus for a move, based on {@link SOCPlayerTracker#getWinGameETA()}.
   * The bonus is based on lowering your bot's WGETA and increasing the leaders' WGETA.
   *
   * @param  trackersBefore   list of player trackers before move
   * @param  trackersAfter    list of player trackers after move;
   *           call {@link SOCPlayerTracker#updateWinGameETAs(HashMap) SOCPlayerTracker.updateWinGameETAs(trackersAfter)}
   *           before calling this method
   */
  protected float calcWGETABonus
      (HashMap<Integer, SOCPlayerTracker> trackersBefore, HashMap<Integer, SOCPlayerTracker> trackersAfter)
  {
    D.ebugPrintln("^^^^^ calcWGETABonus");
    int originalWGETAs[] = new int[game.maxPlayers];
    int WGETAdiffs[] = new int[game.maxPlayers];
    Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();  // Players winning soonest, based on ETA
    int bestWGETA = 1000;  // Lower is better
    float bonus = 0;

    Iterator<SOCPlayerTracker> trackersBeforeIter = trackersBefore.values().iterator();
    while (trackersBeforeIter.hasNext())
    {
      SOCPlayerTracker trackerBefore = trackersBeforeIter.next();
      final int pn = trackerBefore.getPlayer().getPlayerNumber();
      D.ebugPrintln("$$$ win game ETA for player " + pn + " = " + trackerBefore.getWinGameETA());
      originalWGETAs[pn] = trackerBefore.getWinGameETA();
      WGETAdiffs[pn] = trackerBefore.getWinGameETA();

      if (trackerBefore.getWinGameETA() < bestWGETA) {
	bestWGETA = trackerBefore.getWinGameETA();
	leaders.removeAllElements();
	leaders.addElement(trackerBefore);
      } else if (trackerBefore.getWinGameETA() == bestWGETA) {
	leaders.addElement(trackerBefore);
      }
    }

    D.ebugPrintln("^^^^ bestWGETA = "+bestWGETA);

    bonus = calcWGETABonusAux(originalWGETAs, trackersAfter, leaders);

    D.ebugPrintln("^^^^ final bonus = "+bonus);

    return bonus;
  }

  /**
   * Helps calculate WGETA bonus for making a move or other change in the game.
   * The bonus is based on lowering your bot's WGETA and increasing the leaders' WGETA.
   *
   * @param originalWGETAs   the original WGETAs; each player's {@link SOCPlayerTracker#getWinGameETA()} before the change
   * @param trackersAfter    the playerTrackers after the change;
   *          call {@link SOCPlayerTracker#updateWinGameETAs(HashMap) SOCPlayerTracker.updateWinGameETAs(trackersAfter)}
   *          before calling this method
   * @param leaders          a list of leaders (players winning soonest);
   *          the player(s) with lowest {@link SOCPlayerTracker#getWinGameETA()}.
   *          Contains only one element, unless there is an ETA tie.
   */
  private float calcWGETABonusAux
      (final int[] originalWGETAs, HashMap<Integer, SOCPlayerTracker> trackersAfter, Vector<SOCPlayerTracker> leaders)
  {
    int WGETAdiffs[] = new int[game.maxPlayers];
    int bestWGETA = 1000;
    float bonus = 0;

    for (int i = 0; i < game.maxPlayers; i++) {
      WGETAdiffs[i] = originalWGETAs[i];
      if (originalWGETAs[i] < bestWGETA) {
	bestWGETA = originalWGETAs[i];
      }
    }

    Iterator<SOCPlayerTracker> trackersAfterIter = trackersAfter.values().iterator();
    while (trackersAfterIter.hasNext())
    {
      SOCPlayerTracker trackerAfter = trackersAfterIter.next();
      final int pn = trackerAfter.getPlayer().getPlayerNumber();
      WGETAdiffs[pn] -= trackerAfter.getWinGameETA();
      D.ebugPrintln("$$$ win game ETA diff for player " + pn + " = " + WGETAdiffs[pn]);
      if (pn == ourPlayerNumber) {
	if (trackerAfter.getWinGameETA() == 0) {
	  D.ebugPrintln("$$$$ adding win game bonus : +"+(100 / game.maxPlayers));
	  bonus += (100.0f / game.maxPlayers);
	  if ((brain != null) && (brain.getDRecorder().isOn())) {
	    brain.getDRecorder().record("Adding Win Game bonus :"+df1.format(bonus));
	  }
	}
      }
    }

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("WGETA Diffs: "+WGETAdiffs[0]+" "+WGETAdiffs[1]+" "+WGETAdiffs[2]+" "+WGETAdiffs[3]);
    }

    //
    // bonus is based on lowering your WGETA
    // and increasing the leaders' WGETA
    //
    if ((originalWGETAs[ourPlayerNumber] > 0) &&
	(bonus == 0)) {
      bonus += ((100.0f / game.maxPlayers) * ((float)WGETAdiffs[ourPlayerNumber] / (float)originalWGETAs[ourPlayerNumber]));
    }

    D.ebugPrintln("^^^^ our current bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("WGETA bonus for only myself = "+df1.format(bonus));
    }

    //
    //  try adding takedown bonus for all other players
    //  other than the leaders
    //
    for (int pn = 0; pn < game.maxPlayers; pn++)
    {
      Enumeration<SOCPlayerTracker> leadersEnum = leaders.elements();
      while (leadersEnum.hasMoreElements())
      {
          final int leaderPN = leadersEnum.nextElement().getPlayer().getPlayerNumber();
	  if ((pn == ourPlayerNumber) || (pn == leaderPN))
	      continue;

	  if (originalWGETAs[pn] > 0)
	  {
	    final float takedownBonus = -1.0f
	        * (100.0f / game.maxPlayers)
	        * adversarialFactor
	        * ((float) WGETAdiffs[pn] / (float) originalWGETAs[pn])
	        * ((float) bestWGETA / (float) originalWGETAs[pn]);
	    bonus += takedownBonus;
	    D.ebugPrintln("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
	    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	      brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
	    }
	  } else if (WGETAdiffs[pn] < 0) {
	    float takedownBonus = (100.0f / game.maxPlayers) * adversarialFactor;
	    bonus += takedownBonus;
	    D.ebugPrintln("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
	    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	      brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
	    }
	  }
	}
    }

    //
    //  take down bonus for leaders
    //
    Enumeration<SOCPlayerTracker> leadersEnum = leaders.elements();
    while (leadersEnum.hasMoreElements())
    {
        final SOCPlayer leader = leadersEnum.nextElement().getPlayer();
        final int leaderPN = leader.getPlayerNumber();
        if (leaderPN == ourPlayerNumber)
            continue;

        if (originalWGETAs[leaderPN] > 0)
        {
	  final float takedownBonus = -1.0f
	      * (100.0f / game.maxPlayers)
	      * leaderAdversarialFactor
	      * ((float) WGETAdiffs[leaderPN] / (float) originalWGETAs[leaderPN]);
	  bonus += takedownBonus;
	  D.ebugPrintln("^^^^ added takedown bonus for leader " + leaderPN + " : +" + takedownBonus);
	  if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)){
	    brain.getDRecorder().record("Bonus for LI with " + leader.getName() + " : +"+df1.format(takedownBonus));
	  }

	} else if (WGETAdiffs[leaderPN] < 0) {
	  final float takedownBonus = (100.0f / game.maxPlayers) * leaderAdversarialFactor;
	  bonus += takedownBonus;
	  D.ebugPrintln("^^^^ added takedown bonus for leader " + leaderPN + " : +" + takedownBonus);
	  if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	    brain.getDRecorder().record("Bonus for LI with " + leader.getName() + " : +"+df1.format(takedownBonus));
	  }
	}
    }
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("WGETA bonus = "+df1.format(bonus));
    }

    return bonus;
  }


  /**
   * calc dev card score
   */
  public SOCPossibleCard getDevCardScore(final int cardETA, final int leadersCurrentWGETA)
  {
    float devCardScore = 0;
    D.ebugPrintln("$$$ devCardScore = +"+devCardScore);
    D.ebugPrintln("--- before [start] ---");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

    // TODO refactor? This section is like a copy of calcWGETABonus, with something added in the middle

    int WGETAdiffs[] = new int[game.maxPlayers];
    int originalWGETAs[] = new int[game.maxPlayers];
    int bestWGETA = 1000;
    Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();
    Iterator<SOCPlayerTracker> trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext())
    {
      SOCPlayerTracker tracker = trackersIter.next();
      final int pn = tracker.getPlayer().getPlayerNumber();
      originalWGETAs[pn] = tracker.getWinGameETA();
      WGETAdiffs[pn] = tracker.getWinGameETA();
      D.ebugPrintln("$$$$ win game ETA for player " + pn + " = " + tracker.getWinGameETA());

      if (tracker.getWinGameETA() < bestWGETA) {
	bestWGETA = tracker.getWinGameETA();
	leaders.removeAllElements();
	leaders.addElement(tracker);
      } else if (tracker.getWinGameETA() == bestWGETA) {
	leaders.addElement(tracker);
      }
    }

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Estimating Knight card value ...");
    }

    ourPlayerData.getGame().saveLargestArmyState();
    D.ebugPrintln("--- before [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()+1);
    ourPlayerData.getGame().updateLargestArmy();
    D.ebugPrintln("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    float bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

    //
    //  adjust for knight card distribution
    //
    D.ebugPrintln("^^^^ raw bonus = "+bonus);

    bonus *= 0.58f;
    D.ebugPrintln("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.58 = "+df1.format(bonus));
    }

    D.ebugPrintln("^^^^ bonus for +1 knight = "+bonus);
    devCardScore += bonus;

    D.ebugPrintln("--- after [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()-1);
    ourPlayerData.getGame().restoreLargestArmyState();
    D.ebugPrintln("--- cleanup done ---");

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Estimating vp card value ...");
    }

    //
    // see what a vp card does to our win game eta
    //
    D.ebugPrintln("--- before [start] ---");
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().suspend();
    }
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().resume();
    }
    D.ebugPrintln("--- before [end] ---");
    ourPlayerData.getDevCards().add(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
    D.ebugPrintln("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

    D.ebugPrintln("^^^^ our current bonus = "+bonus);

    //
    //  adjust for +1 vp card distribution
    //
    bonus *= 0.21f;
    D.ebugPrintln("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.21 = "+df1.format(bonus));
    }

    D.ebugPrintln("$$$ win game ETA bonus for +1 vp: "+bonus);
    devCardScore += bonus;

    D.ebugPrintln("--- after [end] ---");
    ourPlayerData.getDevCards().subtract(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
    D.ebugPrintln("--- cleanup done ---");

    //
    // add misc bonus
    //
    devCardScore += devCardMultiplier;
    D.ebugPrintln("^^^^ misc bonus = "+devCardMultiplier);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Misc bonus = "+df1.format(devCardMultiplier));
    }

    float score = getETABonus(cardETA, leadersCurrentWGETA, devCardScore);

    D.ebugPrintln("$$$$$ devCardScore = "+devCardScore);
    D.ebugPrintln("$$$$$ devCardETA = "+cardETA);
    D.ebugPrintln("$$$$$ final score = "+score);

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("ETA = "+cardETA);
      brain.getDRecorder().record("dev card score = "+df1.format(devCardScore));
      brain.getDRecorder().record("Total dev card score = "+df1.format(score));
    }

    SOCPossibleCard posCard = new SOCPossibleCard(ourPlayerData, cardETA);
    posCard.addToScore(score);

    return posCard;
  }


  /**
   * calc eta bonus
   *
   * @param leadWGETA  the wgeta of the leader
   * @param eta  the building eta
   * @return the eta bonus
   */
  public float getETABonus(final int eta, final int leadWGETA, final float bonus)
  {
    D.ebugPrintln("**** getETABonus ****");
    //return Math.round(etaBonusFactor * ((100f * ((float)(maxGameLength - leadWGETA - eta) / (float)maxGameLength)) * (1.0f - ((float)leadWGETA / (float)maxGameLength))));

    if (D.ebugOn) {
      D.ebugPrintln("etaBonusFactor = "+etaBonusFactor);
      D.ebugPrintln("etaBonusFactor * 100.0 = "+(etaBonusFactor * 100.0f));
      D.ebugPrintln("eta = "+eta);
      D.ebugPrintln("maxETA = "+maxETA);
      D.ebugPrintln("eta / maxETA = "+((float)eta / (float)maxETA));
      D.ebugPrintln("1.0 - ((float)eta / (float)maxETA) = "+(1.0f - ((float)eta / (float)maxETA)));
      D.ebugPrintln("leadWGETA = "+leadWGETA);
      D.ebugPrintln("maxGameLength = "+maxGameLength);
      D.ebugPrintln("1.0 - ((float)leadWGETA / (float)maxGameLength) = "+(1.0f - ((float)leadWGETA / (float)maxGameLength)));
    }


    //return etaBonusFactor * 100.0f * ((1.0f - ((float)eta / (float)maxETA)) * (1.0f - ((float)leadWGETA / (float)maxGameLength)));

    return (bonus / (float)Math.pow((1+etaBonusFactor), eta));

    //return (bonus * (float)Math.pow(etaBonusFactor, ((float)(eta*eta*eta)/(float)1000.0)));
  }

}


