/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file copyright (C) 2003-2004  Robert S. Thomas
 * Portions of this file copyright (C) 2009-2022 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2012 Paul Bilnoski <paul@bilnoski.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import soc.disableDebug.D;
import soc.game.SOCBoard;
import soc.game.SOCBoardLarge;
import soc.game.SOCCity;
import soc.game.SOCDevCard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCFortress;
import soc.game.SOCGame;
import soc.game.SOCGameOptionSet;
import soc.game.SOCInventory;
import soc.game.SOCLRPathData;
import soc.game.SOCPlayer;
import soc.game.SOCPlayerNumbers;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCRoad;
import soc.game.SOCRoutePiece;
import soc.game.SOCSettlement;
import soc.game.SOCShip;
import soc.game.SOCSpecialItem;
import soc.util.CutoffExceededException;
import soc.util.NodeLenVis;
import soc.util.Pair;
import soc.util.Queue;
import soc.util.SOCRobotParameters;

/**
 * Moved the routines that pick what to build or buy
 * next out of SOCRobotBrain.  Didn't want
 * to call this SOCRobotPlanner because it
 * doesn't really plan, but you could think
 * of it that way.  DM = Decision Maker
 *<P>
 * Uses the info in the {@link SOCPlayerTracker}s.
 * One important method here is {@link #planStuff(int)},
 * which updates {@link #buildingPlan} and related fields.
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

  /**
   * Player trackers, one per player number; vacant seats are null.
   * Same format as {@link SOCRobotBrain#getPlayerTrackers()}.
   * @see #ourPlayerTracker
   */
  protected SOCPlayerTracker[] playerTrackers;

  /** Player tracker for {@link #ourPlayerData}. */
  protected SOCPlayerTracker ourPlayerTracker;

  protected final SOCPlayer ourPlayerData;

  /**
   * {@link #ourPlayerData}'s player number.
   * @since 2.0.00
   */
  protected final int ourPlayerNumber;

  /**
   * {@link #ourPlayerData}'s building plan.
   * Same Stack as {@link SOCRobotBrain#getBuildingPlan()}.
   * May include {@link SOCPossibleCard} to be bought.
   * Filled each turn by {@link #planStuff(int)}.
   * Emptied by {@link SOCRobotBrain}'s calls to {@link SOCRobotBrain#resetBuildingPlan()}.
   *<P>
   * Before v2.5.00 this was an unencapsulated Stack of {@link SOCPossiblePiece}.
   */
  protected final SOCBuildPlanStack buildingPlan;

  /**
   * Strategy to plan and build initial settlements and roads.
   * Used here for {@link OpeningBuildStrategy#estimateResourceRarity()}.
   * @since 2.5.00
   */
  protected final OpeningBuildStrategy openingBuildStrategy;

  /**
   * Our {@link SOCBuildingSpeedEstimate} factory.
   * Is set during construction, from {@link SOCRobotBrain#createEstimatorFactory()} if available.
   * @see #getEstimatorFactory()
   * @since 2.5.00
   */
  protected SOCBuildingSpeedEstimateFactory bseFactory;

  /** The game we're playing in */
  protected final SOCGame game;

  /**
   * these are the two resources that we want
   * when we play a discovery dev card
   *<P>
   * Before v2.5.00, this field was {@code SOCRobotBrain.resourceChoices}
   *
   * @see #getResourceChoices()
   */
  protected SOCResourceSet resourceChoices;

  /** Roads threatened by other players; currently unused. */
  protected final ArrayList<SOCPossibleRoad> threatenedRoads;

  /**
   * A road or ship ({@link SOCPossibleRoad} and/or subclass {@link SOCPossibleShip})
   * we could build this turn; its {@link SOCPossibleRoad#getNecessaryRoads()} is empty.
   * Built in {@link #smartGameStrategy(int[])}.
   */
  protected final ArrayList<SOCPossibleRoad> goodRoads;

  /**
   * A road or ship we could build this turn, chosen
   * from {@link #threatenedRoads} or {@link #goodRoads}
   * in {@link #smartGameStrategy(int[])}.
   * If we want to build this soon, it will be added to {@link #buildingPlan}.
   */
  protected SOCPossibleRoad favoriteRoad;

  /** Threatened settlements, as calculated by {@link #scorePossibleSettlements(int, int)} */
  protected final ArrayList<SOCPossibleSettlement> threatenedSettlements;

  /** Good settlements, as calculated by {@link #scorePossibleSettlements(int, int)} */
  protected final ArrayList<SOCPossibleSettlement> goodSettlements;

  /**
   * A settlement to build, chosen from {@link #goodSettlements} or {@link #threatenedSettlements}.
   * If we want to build this soon, it will be added to {@link #buildingPlan}.
   */
  protected SOCPossibleSettlement favoriteSettlement;

  protected SOCPossibleCity favoriteCity;
  protected SOCPossibleCard possibleCard;


  /**
   * Constructor for setting DM fields from a robot brain.
   *
   * @param br  the robot brain
   */
  public SOCRobotDM(SOCRobotBrain br)
  {
    this(br.getRobotParameters(), br.getOpeningBuildStrategy(), br.getEstimatorFactory(),
        br.getPlayerTrackers(), br.getOurPlayerTracker(), br.getOurPlayerData(), br.getBuildingPlan());

    brain = br;
  }


  /**
   * Constructor for specifying DM fields instead of using a Brain.
   *
   * @param params  the robot parameters
   * @param obs  a robot brain's current {@link OpeningBuildStrategy}, or {@code null} to create one here,
   *     in case DM needs to call {@link OpeningBuildStrategy#estimateResourceRarity()}
   * @param bsef  the BSE factory to use, or {@code null} to create a
   *     new <tt>{@link SOCBuildingSpeedEstimateFactory}(null)</tt>
   * @param pt   the player trackers, same format as {@link SOCRobotBrain#getPlayerTrackers()}
   * @param opt  our player tracker
   * @param opd  our player data; also calls {@link SOCPlayer#getGame()} here
   * @param bp   our building plan
   */
  public SOCRobotDM
      (SOCRobotParameters params,
       OpeningBuildStrategy obs,
       SOCBuildingSpeedEstimateFactory bsef,
       SOCPlayerTracker[] pt,
       SOCPlayerTracker opt,
       SOCPlayer opd,
       SOCBuildPlanStack bp)
  {
    brain = null;
    playerTrackers = pt;
    ourPlayerTracker = opt;
    ourPlayerData = opd;
    ourPlayerNumber = opd.getPlayerNumber();
    buildingPlan = bp;
    bseFactory = (bsef != null) ? bsef : new SOCBuildingSpeedEstimateFactory(null);
    game = ourPlayerData.getGame();
    openingBuildStrategy = (obs != null) ? obs : new OpeningBuildStrategy(game, opd, null);

    maxGameLength = params.getMaxGameLength();
    maxETA = params.getMaxETA();
    etaBonusFactor = params.getETABonusFactor();
    adversarialFactor = params.getAdversarialFactor();
    leaderAdversarialFactor = params.getLeaderAdversarialFactor();
    devCardMultiplier = params.getDevCardMultiplier();
    threatMultiplier = params.getThreatMultiplier();

    resourceChoices = new SOCResourceSet();
    resourceChoices.add(2, SOCResourceConstants.CLAY);
    threatenedRoads = new ArrayList<SOCPossibleRoad>();
    goodRoads = new ArrayList<SOCPossibleRoad>();
    threatenedSettlements = new ArrayList<SOCPossibleSettlement>();
    goodSettlements = new ArrayList<SOCPossibleSettlement>();
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
   * Called as needed by {@link SOCRobotBrain} and related strategy classes.
   * Adds to {@link #buildingPlan}, sets {@link #favoriteSettlement}, etc.
   * Calls either {@link #smartGameStrategy(int[])} or {@link #dumbFastGameStrategy(int[])}.
   * Both of those will check whether this is our normal turn, or if
   * it's the 6-player board's {@link SOCGame#SPECIAL_BUILDING Special Building Phase}.
   * Both strategies also call
   * {@link #scenarioGameStrategyPlan(float, float, boolean, boolean, SOCBuildingSpeedEstimate, int, boolean) scenarioGameStrategyPlan(..)}
   * if the game has an applicable scenario such as {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.
   *<P>
   * Some details:
   *<UL>
   * <LI> Make a new {@link SOCBuildingSpeedEstimate} based on our current dice numbers
   * <LI> Get building piece type ETAs based on {@link SOCBuildingSpeedEstimate#getEstimatesFromNowFast(SOCResourceSet, boolean[])}
   *        with our current resources and ports
   * <LI> Clear lists threatened and good settlements and roads
   * <LI> Set favoriteRoad, favoriteSettlement, favoriteCity to null
   * <LI> If {@code SMART_STRATEGY}, update all {@link SOCPlayerTracker#updateWinGameETAs(SOCPlayerTracker[])}
   * <LI> Get each player's win ETA from their tracker; find leading player (shortest win ETA)
   * <LI> For each of our possible pieces in our tracker, call its {@link SOCPossiblePiece#resetScore() resetScore()}
   *        and {@link SOCPossiblePiece#clearBiggestThreats() clearBiggestThreats()}
   *    <BR>&nbsp;
   * <LI><B>Call smartGameStrategy or dumbFastGameStrategy</B> using building piece type ETAs
   *    <BR>&nbsp;
   * <LI> If {@code SMART_STRATEGY} and we have a Road Building card, plan and push 2 roads onto {@code buildingPlan}
   *</UL>
   *
   * @param strategy  an integer that determines which strategy is used
   *    ({@link #SMART_STRATEGY} or {@link #FAST_STRATEGY})
   */
  public void planStuff(final int strategy)
  {
      //long startTime = System.currentTimeMillis();
    D.ebugPrintlnINFO("PLANSTUFF");

    SOCBuildingSpeedEstimate currentBSE = getEstimator(ourPlayerData.getNumbers());
    int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast
        (ourPlayerData.getResources(), ourPlayerData.getPortFlags());

    threatenedSettlements.clear();
    goodSettlements.clear();
    threatenedRoads.clear();
    goodRoads.clear();

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
    for (final SOCPlayerTracker tracker : playerTrackers)
    {
      if (tracker == null)
          continue;

      int wgeta = tracker.getWinGameETA();
      if (wgeta < leadersCurrentWGETA)
          leadersCurrentWGETA = wgeta;
    }

    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

    ///
    /// reset scores and biggest threats for everything
    ///
    Iterator<? extends SOCPossiblePiece> posPiecesIter;
    SOCPossiblePiece posPiece;
    posPiecesIter = ourPlayerTracker.getPossibleCities().values().iterator();
    while (posPiecesIter.hasNext())
    {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posPiecesIter.hasNext())
    {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleRoads().values().iterator();
    while (posPiecesIter.hasNext())
    {
      posPiece = posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }

    switch (strategy)
    {
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
    if ((strategy == SMART_STRATEGY)
        && (! ourPlayerData.hasPlayedDevCard())
        && ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2
        && ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS))
    {
        planRoadBuildingTwoRoads();
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
   *<H4>Outline:</H4>
   * Possible cities and settlements are looked at first.
   * Find the city with best {@link SOCPossibleCity#getSpeedupTotal()}, then check each possible
   * settlement's {@link SOCPossiblePiece#getETA()} against the city's ETA to possibly choose one to build.
   * (If one is chosen, its {@link SOCPossibleSettlement#getNecessaryRoads()}
   * will also be chosen here.)  Then, Knights or Dev Cards.
   * Only then would roads or ships be looked at, for Longest Route
   * (and only if we're at 5 VP or more).
   *<P>
   * This method never directly checks
   * {@code ourPlayerTracker}{@link SOCPlayerTracker#getPossibleRoads() .getPossibleRoads()}, instead
   * it adds the roads or ships from {@link SOCPossibleSettlement#getNecessaryRoads()} to {@link #buildingPlan}
   * when a possible settlement is picked to build.
   *<P>
   * Some scenarios require special moves or certain actions to win the game.  If we're playing in
   * such a scenario, after calculating {@link #favoriteSettlement}, {@link #favoriteCity}, etc, calls
   * {@link #scenarioGameStrategyPlan(float, float, boolean, boolean, SOCBuildingSpeedEstimate, int, boolean)}.
   * See that method for the list of scenarios which need such planning.
   *
   * @param buildingETAs  the ETAs for building each piece type
   * @see #smartGameStrategy(int[])
   */
  protected void dumbFastGameStrategy(final int[] buildingETAs)
  {
    D.ebugPrintlnINFO("***** dumbFastGameStrategy *****");

    // If this game is on the 6-player board, check whether we're planning for
    // the Special Building Phase.  Can't buy cards or trade in that phase.
    final boolean forSpecialBuildingPhase =
        game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != ourPlayerNumber);

    int bestETA = 500;
    SOCBuildingSpeedEstimate ourBSE = getEstimator(ourPlayerData.getNumbers());

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
              D.ebugPrintlnINFO("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
              D.ebugPrintlnINFO("Speedup = "+posCity.getSpeedupTotal());
              D.ebugPrintlnINFO("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
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
          D.ebugPrintlnINFO("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
          buildingPlan.push(favoriteSettlement);
          if (! favoriteSettlement.getNecessaryRoads().isEmpty())
          {
              //
              // we need to build roads first
              //
              Stack<SOCPossibleRoad> roadPath = favoriteSettlement.getRoadPath();
              while (! roadPath.empty()) {
                  buildingPlan.push(roadPath.pop());
              }
          }
      } else if (favoriteCity != null) {
          //
          // we want to build a city
          //
          D.ebugPrintlnINFO("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
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
            D.ebugPrintlnINFO("Buy a card");
            SOCPossibleCard posCard = new SOCPossibleCard
                (ourPlayerData, buildingETAs[SOCBuildingSpeedEstimate.CARD]);
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
      D.ebugPrintlnINFO("Calculating Largest Army ETA");
      int laETA = 500;
      int laSize = 0;
      SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
      if (laPlayer == null)
      {
        ///
        /// no one has largest army
        ///
        laSize = 3;
      } else if (laPlayer.getPlayerNumber() == ourPlayerNumber) {
        ///
        /// we have largest army
        ///
        D.ebugPrintlnINFO("We have largest army");
      } else {
        laSize = laPlayer.getNumKnights() + 1;
      }
      ///
      /// figure out how many knights we need to buy
      ///
      int knightsToBuy = 0;
      if ((ourPlayerData.getNumKnights()
          + ourPlayerData.getInventory().getAmount(SOCDevCardConstants.KNIGHT))  // OLD + NEW knights
          < laSize)
      {
          knightsToBuy = laSize -
              (ourPlayerData.getNumKnights()
               + ourPlayerData.getInventory().getAmount(SOCInventory.OLD, SOCDevCardConstants.KNIGHT));
      }
      D.ebugPrintlnINFO("knightsToBuy = "+knightsToBuy);

      if (ourPlayerData.getGame().getNumDevCards() >= knightsToBuy)
      {
        ///
        /// figure out how long it takes to buy this many knights
        ///
        SOCResourceSet targetResources = new SOCResourceSet();
        for (int i = 0; i < knightsToBuy; i++)
          targetResources.add(SOCDevCard.COST);
        laETA = ourBSE.calculateRollsFast
            (ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
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
      D.ebugPrintlnINFO("laETA = "+laETA);

      //
      // consider Longest Road
      //
      D.ebugPrintlnINFO("Calculating Longest Road ETA");
      int lrETA = 500;
      Stack<?> bestLRPath = null;
      int lrLength;
      SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();
      if ((lrPlayer != null)
          && (lrPlayer.getPlayerNumber() == ourPlayerNumber))
      {
          ///
          /// we have longest road
          ///
          D.ebugPrintlnINFO("We have longest road");
      }
      else if (! game.isGameOptionSet(SOCGameOptionSet.K_SC_0RVP))
      {
          if (lrPlayer == null)
          {
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
              depth = Math.min
                  (((lrLength + 1) - pathData.getLength()),
                   ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD));
              path = (Stack<?>) recalcLongestRoadETAAux
                  (ourPlayerData, true, pathData.getBeginning(), pathData.getLength(), lrLength, depth);
              if ((path != null)
                  && ((bestLRPath == null)
                      || (path.size() < bestLRPath.size())))
              {
                  bestLRPath = path;
              }
              path = (Stack<?>) recalcLongestRoadETAAux
                  (ourPlayerData, true, pathData.getEnd(), pathData.getLength(), lrLength, depth);
              if ((path != null)
                  && ((bestLRPath == null)
                      || (path.size() < bestLRPath.size())))
              {
                  bestLRPath = path;
              }
          }
          if (bestLRPath != null)
          {
              //
              // calculate LR eta
              //
              D.ebugPrintlnINFO("Number of roads: "+bestLRPath.size());
              SOCResourceSet targetResources = new SOCResourceSet();
              for (int i = 0; i < bestLRPath.size(); i++)
                  targetResources.add(SOCRoad.COST);
              lrETA = ourBSE.calculateRollsFast
                  (ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
          }
      }
      if (lrETA < bestETA)
      {
          bestETA = lrETA;
          choice = LR_CHOICE;
      }
      D.ebugPrintlnINFO("lrETA = "+lrETA);

      //
      // consider possible cities
      //
      if ((ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0)
          && (buildingETAs[SOCBuildingSpeedEstimate.CITY] <= bestETA))
      {
          Iterator<SOCPossibleCity> posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
          while (posCitiesIter.hasNext())
          {
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

              if ((posSet.getRoadPath() == null)
                  || (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= posSet.getRoadPath().size()))
              {
                  if (posSet.getETA() < bestETA)
                  {
                      bestETA = posSet.getETA();
                      favoriteSettlement = posSet;
                      choice = SETTLEMENT_CHOICE;
                  } else if (posSet.getETA() == bestETA) {
                      if (favoriteSettlement == null)
                      {
                          if ((favoriteCity == null)
                              || (posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal()))
                          {
                              favoriteSettlement = posSet;
                              choice = SETTLEMENT_CHOICE;
                          }
                      } else {
                          if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal())
                          {
                              favoriteSettlement = posSet;
                          }
                      }
                  }
              }
          }
      }

      if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI)
          || game.isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
      {
          if (scenarioGameStrategyPlan
                  (bestETA, -1f, false, (choice == LA_CHOICE), ourBSE, 0, forSpecialBuildingPhase))
              return;  // <--- Early return: Scenario-specific buildingPlan was pushed ---
      }

      //
      // pick something to build
      //
      switch (choice)
      {
      case LA_CHOICE:
        D.ebugPrintlnINFO("Picked LA");
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
        D.ebugPrintlnINFO("Picked LR");
        while (! bestLRPath.empty())
        {
          SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
          D.ebugPrintlnINFO("LR road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
          buildingPlan.push(pr);
        }
        break;

      case CITY_CHOICE:
          D.ebugPrintlnINFO("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
          buildingPlan.push(favoriteCity);
          break;

      case SETTLEMENT_CHOICE:
          D.ebugPrintlnINFO("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
          buildingPlan.push(favoriteSettlement);
          if (! favoriteSettlement.getNecessaryRoads().isEmpty())
          {
              //
              // we need to build roads first
              //
              Stack<SOCPossibleRoad> roadPath = favoriteSettlement.getRoadPath();
              while (! roadPath.empty())
              {
                  SOCPossibleRoad pr = roadPath.pop();
                  D.ebugPrintlnINFO("Nec road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
                  buildingPlan.push(pr);
              }
          }
      }
    }
  }

  /**
   * For each possible settlement in our {@link SOCPlayerTracker},
   * update its {@link SOCPossiblePiece#getETA() getETA()} and
   * its {@link SOCPossibleSettlement#getRoadPath() getRoadPath()}.
   *<P>
   * Each {@link SOCPossibleSettlement#getRoadPath()} is calculated
   * here by finding the shortest path among its {@link SOCPossibleSettlement#getNecessaryRoads()}.
   *<P>
   * Calculates ETA by using our current {@link SOCBuildingSpeedEstimate} on the resources
   * needed to buy the settlement plus roads/ships for its shortest path's length.
   *
   * @param settlementETA  ETA for building a settlement from now if it doesn't require any roads or ships
   * @param ourBSE  Current building speed estimate, from our {@code SOCPlayer#getNumbers()}
   *
   * @see #scorePossibleSettlements(int, int)
   */
  protected void scoreSettlementsForDumb(final int settlementETA, SOCBuildingSpeedEstimate ourBSE)
  {
    D.ebugPrintlnINFO("-- scoreSettlementsForDumb --");

    Queue<Pair<SOCPossibleRoad, List<SOCPossibleRoad>>> queue
        = new Queue<Pair<SOCPossibleRoad, List<SOCPossibleRoad>>>();
    Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext())
    {
      SOCPossibleSettlement posSet = posSetsIter.next();
      if (D.ebugOn)
      {
          D.ebugPrintlnINFO("Estimate speedup of stlmt at " + game.getBoard().nodeCoordToString(posSet.getCoordinates()));
          D.ebugPrintlnINFO("***    speedup total = " + posSet.getSpeedupTotal());
      }

      ///
      /// find the shortest path to this settlement
      ///
      final List<SOCPossibleRoad> necRoadList = posSet.getNecessaryRoads();
      if (! necRoadList.isEmpty())
      {
          queue.clear();  // will use for BFS if needed:
              // Pair members are <SOCPossibleRoad, null or list of SOCPossibleRoad needed to build that road>.
              // Lists have most-distant necessary road at beginning (item 0), and most-immediate at end of list (n-1).

          for (SOCPossibleRoad necRoad : necRoadList)
          {
              if (D.ebugOn)
                  D.ebugPrintlnINFO("-- queuing necessary road at " + game.getBoard().edgeCoordToString(necRoad.getCoordinates()));
              queue.put(new Pair<SOCPossibleRoad, List<SOCPossibleRoad>>(necRoad, null));
          }

          //
          // Do a BFS of the necessary road paths looking for the shortest one.
          //
          boolean pathTooLong = false;
          for (int maxIter = 50; maxIter > 0 && ! queue.empty(); --maxIter)
          {
              Pair<SOCPossibleRoad, List<SOCPossibleRoad>> dataPair = queue.get();
              SOCPossibleRoad curRoad = dataPair.getA();
              final List<SOCPossibleRoad> possRoadsToCur = dataPair.getB();
              if (D.ebugOn)
                  D.ebugPrintlnINFO("-- current road at " + game.getBoard().edgeCoordToString(curRoad.getCoordinates()));

              final List<SOCPossibleRoad> necRoads = curRoad.getNecessaryRoads();
              if (necRoads.isEmpty())
              {
                  //
                  // we have a path
                  //
                  D.ebugPrintlnINFO("Found a path!");
                  Stack<SOCPossibleRoad> path = new Stack<SOCPossibleRoad>();
                  path.push(curRoad);

                  if (D.ebugOn)
                      D.ebugPrintlnINFO("possRoadsToCur = " + possRoadsToCur);
                  if (possRoadsToCur != null)
                      // push to path, iterating from nearest to curRoad until most distant
                      for (int i = possRoadsToCur.size() - 1; i >= 0; --i)
                          path.push(possRoadsToCur.get(i));

                  posSet.setRoadPath(path);
                  queue.clear();
                  D.ebugPrintlnINFO("Done setting path.");
              } else {
                  final List<SOCPossibleRoad> possRoadsAndCur =
                      (possRoadsToCur != null)
                      ? new ArrayList<SOCPossibleRoad>(possRoadsToCur)
                      : new ArrayList<SOCPossibleRoad>();
                  possRoadsAndCur.add(curRoad);

                  if (queue.size() + necRoads.size() > 40)
                  {
                      // Too many necessary, or dupes led to loop. Bug in necessary road construction?
                      System.err.println("rDM.scoreSettlementsForDumb: Necessary Road Path too long for road/ship 0x"
                          + Integer.toHexString(curRoad.getCoordinates()) + " for settle 0x"
                          + Integer.toHexString(posSet.getCoordinates()));
                      pathTooLong = true;
                      queue.clear();
                      break;
                  }

                  for (SOCPossibleRoad necRoad2 : necRoads)
                  {
                      if (D.ebugOn)
                          D.ebugPrintlnINFO("-- queuing necessary road at " + game.getBoard().edgeCoordToString(necRoad2.getCoordinates()));
                      queue.put(new Pair<SOCPossibleRoad, List<SOCPossibleRoad>>(necRoad2, possRoadsAndCur));
                  }
              }
          }
          if (! queue.empty())
          {
              System.err.println("rDM.scoreSettlementsForDumb: Necessary Road Path length unresolved for settle 0x"
                  + Integer.toHexString(posSet.getCoordinates()));
              pathTooLong = true;
          }
          D.ebugPrintlnINFO("Done searching for path.");

          //
          // calculate ETA
          //
          if (pathTooLong) {
              posSet.setETA(500);
          } else {
              SOCResourceSet targetResources = new SOCResourceSet();
              targetResources.add(SOCSettlement.COST);
              Stack<SOCPossibleRoad> path = posSet.getRoadPath();
              if (path != null)
              {
                  final int pathLength = path.size();
                  final SOCPossiblePiece pathFirst = (pathLength > 0) ? path.peek() : null;
                  SOCResourceSet rtype =
                      ((pathFirst != null) && (pathFirst instanceof SOCPossibleShip)
                       && ! ((SOCPossibleShip) pathFirst).isCoastalRoadAndShip)  // TODO better coastal ETA scoring
                      ? SOCShip.COST
                      : SOCRoad.COST;
                  for (int i = 0; i < pathLength; i++)
                      targetResources.add(rtype);
              }
              posSet.setETA(ourBSE.calculateRollsFast
                  (ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags()));
          }
      } else {
          //
          // no roads are necessary
          //
          posSet.setRoadPath(null);
          posSet.setETA(settlementETA);
      }

      D.ebugPrintlnINFO("Settlement ETA = " + posSet.getETA());
    }
  }

  /**
   * For {@link #planStuff(int)}, if we have a road building card, make sure we build two roads first.
   * Pick 2 good potential roads, and push them onto {@link #buildingPlan}.
   *<P>
   * Call only when our {@code SOCPlayer}:
   *<UL>
   * <LI> Has 2 more more road pieces left
   * <LI> Has an old {@link SOCDevCardConstants#ROADS} card to play
   * <LI> ! {@link SOCPlayer#hasPlayedDevCard() hasPlayedDevCard()}
   *</UL>
   * @since 2.0.00
   */
  protected void planRoadBuildingTwoRoads()
  {
      SOCPossibleRoad secondFavoriteRoad = null;
      D.ebugPrintlnINFO("*** making a plan for road building");

      ///
      /// we need to pick two roads
      ///
      if (favoriteRoad != null)
      {
        //
        //  pretend to put the favorite road down,
        //  and then score the new pos roads
        //
        //  TODO for now, coastal roads/ships are always built as roads not ships
        //
        final SOCRoutePiece tmpRS;
        if ((favoriteRoad instanceof SOCPossibleShip)
            && ! ((SOCPossibleShip) favoriteRoad).isCoastalRoadAndShip )
            tmpRS = new SOCShip(ourPlayerData, favoriteRoad.getCoordinates(), null);
        else
            tmpRS = new SOCRoad(ourPlayerData, favoriteRoad.getCoordinates(), null);

        SOCPlayerTracker[] trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRS, game, playerTrackers);
        SOCPlayerTracker.updateWinGameETAs(trackersCopy);

        SOCPlayerTracker ourPlayerTrackerCopy = trackersCopy[ourPlayerNumber];

        final int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
        D.ebugPrintlnINFO("ourCurrentWGETACopy = "+ourCurrentWGETACopy);

        int leadersCurrentWGETACopy = ourCurrentWGETACopy;
        for (final SOCPlayerTracker tracker : trackersCopy)
        {
          if (tracker == null)
            continue;

          int wgeta = tracker.getWinGameETA();
          if (wgeta < leadersCurrentWGETACopy) {
            leadersCurrentWGETACopy = wgeta;
          }
        }

        for (SOCPossiblePiece newPos : favoriteRoad.getNewPossibilities())
        {
          if (newPos instanceof SOCPossibleRoad)
          {
            newPos.resetScore();
            // float wgetaScore = getWinGameETABonusForRoad
            //   ((SOCPossibleRoad)newPos, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETACopy, trackersCopy);


            D.ebugPrintlnINFO("$$$ new pos road at "+Integer.toHexString(newPos.getCoordinates()));  // +" has a score of "+newPos.getScore());

            if (favoriteRoad.getCoordinates() != newPos.getCoordinates())
            {
              if (secondFavoriteRoad == null) {
                secondFavoriteRoad = (SOCPossibleRoad) newPos;
              } else {
                if (newPos.getScore() > secondFavoriteRoad.getScore()) {
                  secondFavoriteRoad = (SOCPossibleRoad) newPos;
                }
              }
            }
          }
        }

        for (SOCPossibleRoad threatenedRoad : threatenedRoads)
        {
          D.ebugPrintlnINFO("$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

          //
          // see how building this piece impacts our winETA
          //
          threatenedRoad.resetScore();
          // float wgetaScore = getWinGameETABonusForRoad
          //   (threatenedRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

          D.ebugPrintlnINFO("$$$  final score = 0");  // +threatenedRoad.getScore());

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

        for (SOCPossibleRoad goodRoad : goodRoads)
        {
          D.ebugPrintlnINFO("$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));
          //
          // see how building this piece impacts our winETA
          //
          goodRoad.resetScore();
          // float wgetaScore = getWinGameETABonusForRoad
          //   (goodRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

          D.ebugPrintlnINFO("$$$  final score = 0");  // +goodRoad.getScore());

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

        SOCPlayerTracker.undoTryPutPiece(tmpRS, game);

        if (! buildingPlan.isEmpty())
        {
          SOCPossiblePiece planPeek = buildingPlan.peek();
          if ((planPeek == null) ||
              (! (planPeek instanceof SOCPossibleRoad)))
          {
            if (secondFavoriteRoad != null)
            {
              D.ebugPrintlnINFO("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
              D.ebugPrintlnINFO("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
              D.ebugPrintlnINFO("$ PUSHING "+secondFavoriteRoad);
              buildingPlan.push(secondFavoriteRoad);
              D.ebugPrintlnINFO("$ PUSHING "+favoriteRoad);
              buildingPlan.push(favoriteRoad);
            }
          }
          else if (secondFavoriteRoad != null)
          {
            SOCPossiblePiece tmp = buildingPlan.pop();
            D.ebugPrintlnINFO("$ POPPED OFF");
            D.ebugPrintlnINFO("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
            D.ebugPrintlnINFO("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
            D.ebugPrintlnINFO("$ PUSHING "+secondFavoriteRoad);
            buildingPlan.push(secondFavoriteRoad);
            D.ebugPrintlnINFO("$ PUSHING "+tmp);
            buildingPlan.push(tmp);
          }
        }
      }
  }

  /**
   * Does a depth first search of legal possible road edges from the end point of the longest
   * path connecting a graph of nodes, and returns which roads or how many roads
   * would need to be built to take longest road.
   *<P>
   * Do not call if {@link SOCGameOptionSet#K_SC_0RVP} is set, because
   * this method needs {@link SOCPlayer#getLRPaths()} which will be empty.
   *<P>
   * Combined implementation for use by SOCRobotDM and {@link SOCPlayerTracker}.
   *
   * @param pl            Calculate this player's longest road;
   *             typically SOCRobotDM.ourPlayerData or SOCPlayerTracker.player
   * @param wantsStack    If true, return the Stack; otherwise, return numRoads.
   * @param startNode     the path endpoint, such as from
   *             {@link SOCPlayer#getLRPaths()}.(i){@link SOCLRPathData#getBeginning() .getBeginning()}
   *             or {@link SOCLRPathData#getEnd() .getEnd()}
   * @param pathLength    the length of that path
   * @param lrLength      length of longest road in the game
   * @param searchDepth   how many roads out to search
   *
   * @return if <tt>wantsStack</tt>: a {@link Stack} containing the path of roads with the last one
   *         (farthest from <tt>startNode</tt>) on top, or <tt>null</tt> if it can't be done.
   *         If ! <tt>wantsStack</tt>: Integer: the number of roads needed, or 500 if it can't be done
   */
  protected static Object recalcLongestRoadETAAux
      (SOCPlayer pl, final boolean wantsStack, final int startNode,
       final int pathLength, final int lrLength, final int searchDepth)
  {
    // D.ebugPrintln("=== recalcLongestRoadETAAux("+Integer.toHexString(startNode)+","+pathLength+","+lrLength+","+searchDepth+")");

    //
    // We're doing a depth first search of all possible road paths.
    // For similar code, see SOCPlayer.calcLongestRoad2
    // Both methods rely on a stack holding NodeLenVis (pop to curNode in loop);
    // they differ in actual element type within the stack because they are
    // gathering slightly different results (length or a stack of edges).
    //
    int longest = 0;
    int numRoads = 500;
    Pair<NodeLenVis<Integer>, List<Integer>> bestPathNode = null;

    final SOCBoard board = pl.getGame().getBoard();
    Stack<Pair<NodeLenVis<Integer>, List<Integer>>> pending = new Stack<Pair<NodeLenVis<Integer>, List<Integer>>>();
        // Holds as-yet unvisited nodes:
        // Pair members are <NodeLenVis, null or node-coordinate list of all parents (from DFS traversal order)>.
        // Lists have most-distant node at beginning (item 0), and most-immediate at end of list (n-1).
        // That list is used at the end to build the returned Stack which is the road path needed.
    pending.push(new Pair<NodeLenVis<Integer>, List<Integer>>
        (new NodeLenVis<Integer>(startNode, pathLength, new Vector<Integer>()), null));

    while (! pending.empty())
    {
      final Pair<NodeLenVis<Integer>, List<Integer>> dataPair = pending.pop();
      final NodeLenVis<Integer> curNode = dataPair.getA();
      //D.ebugPrintln("curNode = "+curNode);

      final int coord = curNode.node;
      int len = curNode.len;
      final Vector<Integer> visited = curNode.vis;
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

      if (! pathEnd)
      {
          //
          // check if we've connected to another road graph of this player
          //
          Iterator<SOCLRPathData> lrPathsIter = pl.getLRPaths().iterator();
          while (lrPathsIter.hasNext())
          {
              SOCLRPathData pathData = lrPathsIter.next();
              if ((startNode != pathData.getBeginning())
                      && (startNode != pathData.getEnd())
                      && ((coord == pathData.getBeginning()) || (coord == pathData.getEnd())))
              {
                  pathEnd = true;
                  len += pathData.getLength();
                  //D.ebugPrintln("connecting to another path: " + pathData);
                  //D.ebugPrintln("len = " + len);

                  break;
              }
          }
      }

      if (! pathEnd)
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

      if (! pathEnd)
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
                final Integer edge = Integer.valueOf(j);
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

                    List<Integer> nodeParentList = dataPair.getB();
                    if (nodeParentList == null)
                        nodeParentList = new ArrayList<Integer>();
                    else
                        nodeParentList = new ArrayList<Integer>(nodeParentList);  // clone before we add to it
                    nodeParentList.add(coord);  // curNode's coord will be parent to new pending element

                    j = board.getAdjacentNodeToNode(coord, dir);  // edge's other node
                    pending.push(new Pair<NodeLenVis<Integer>, List<Integer>>
                        (new NodeLenVis<Integer>(j, len + 1, newVis), nodeParentList));

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

        return Integer.valueOf(rv);  // <-- Early return: ! wantsStack ---
    }

    if ((longest > lrLength) && (bestPathNode != null))
    {
      //D.ebugPrintln("Converting nodes to road coords.");
      //
      // Return the path in a stack, with the last road (the one from bestPathNode) on top.
      // Convert pairs of node coords to edge coords for roads.
      // List is ordered from farthest parent at 0 to bestPathNode's parent at (n-1),
      // so iterate same way to build the stack.
      //
      Stack<SOCPossibleRoad> path = new Stack<SOCPossibleRoad>();
      int coordC, coordP;
      List<Integer> nodeList = bestPathNode.getB();
      if ((nodeList == null) || nodeList.isEmpty())
          return null;  // <--- early return, no node list: should not happen ---
      nodeList.add(Integer.valueOf(bestPathNode.getA().node));  // append bestPathNode

      final int L = nodeList.size();
      coordP = nodeList.get(0);  // root ancestor
      for (int i = 1; i < L; ++i)
      {
          coordC = nodeList.get(i);
          path.push(new SOCPossibleRoad(pl, board.getEdgeBetweenAdjacentNodes(coordC, coordP), null));

          coordP = coordC;
      }

      return path;
    }

    return null;
  }

  /**
   * Plan building for the smart game strategy ({@link #SMART_STRATEGY}).
   * use player trackers' Win Game ETAs (WGETA) to determine best move
   * and update {@link #buildingPlan}.
   *<P>
   * For example, if {@link #favoriteSettlement} is chosen,
   * it's chosen from {@link #goodSettlements} or {@link #threatenedSettlements}.
   *<P>
   * Some scenarios require special moves or certain actions to win the game.  If we're playing in
   * such a scenario, after calculating {@link #favoriteSettlement}, {@link #favoriteCity}, etc, calls
   * {@link #scenarioGameStrategyPlan(float, float, boolean, boolean, SOCBuildingSpeedEstimate, int, boolean)}.
   * See that method for the list of scenarios which need such planning.
   *
   *<H4>Outline:</H4>
   *<UL>
   * <LI> Determine our Win Game ETA, leading player's WGETA
   * <LI> {@link #scorePossibleSettlements(int, int) scorePossibleSettlements(BuildETAs, leaderWGETA)}:
   *      For each settlement we can build now (no roads/ships needed), add its ETA bonus to its score
   * <LI> Build {@link #goodRoads} from possibleRoads' roads & ships we can build now
   * <LI> Pick a {@link #favoriteSettlement} from threatened/good settlements, with the highest
   *      {@link SOCPossiblePiece#getScore() getScore()}  (ETA bonus)
   * <LI> Pick a {@link #favoriteRoad} from threatened/good, with highest getWinGameETABonusForRoad
   * <LI> Pick a {@link #favoriteCity} from our possibleCities, with highest score (ETA bonus)
   * <LI> If {@code favoriteCity} has the best score (best ETA if tied), choose to build the city
   * <LI> Otherwise choose {@code favoriteRoad} or {@code favoriteSettlement} based on their scores
   * <LI> If buying a dev card scores higher than the chosen piece, choose to buy one instead of building
   * <LI> Check for and calc any scenario-specific {@code buildingPlan}
   *</UL>
   *
   * @param buildingETAs  the ETAs for building each piece type
   * @see #dumbFastGameStrategy(int[])
   */
  protected void smartGameStrategy(final int[] buildingETAs)
  {
    D.ebugPrintlnINFO("***** smartGameStrategy *****");

    // If this game is on the 6-player board, check whether we're planning for
    // the Special Building Phase.  Can't buy cards or trade in that phase.
    final boolean forSpecialBuildingPhase =
        game.isSpecialBuilding() || (game.getCurrentPlayerNumber() != ourPlayerNumber);

    //
    // save the lr paths list to restore later
    //
    @SuppressWarnings("unchecked")
    List<SOCLRPathData>[] savedLRPaths = new List[game.maxPlayers];
    for (int pn = 0; pn < game.maxPlayers; pn++)
    {
      savedLRPaths[pn] = new ArrayList<SOCLRPathData>();
      savedLRPaths[pn].addAll(game.getPlayer(pn).getLRPaths());
    }

    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    D.ebugPrintlnINFO("ourCurrentWGETA = "+ourCurrentWGETA);

    int leadersCurrentWGETA = ourCurrentWGETA;
    for (final SOCPlayerTracker tracker : playerTrackers)
    {
      if (tracker == null)
        continue;

      int wgeta = tracker.getWinGameETA();
      if (wgeta < leadersCurrentWGETA) {
        leadersCurrentWGETA = wgeta;
      }
    }

    /*
    boolean goingToPlayRB = false;
    if (! ourPlayerData.hasPlayedDevCard() &&
        ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
        ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS)) {
      goingToPlayRB = true;
    }
    */

    ///
    /// score the possible settlements into threatenedSettlements and goodSettlements
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
    {
      scorePossibleSettlements(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], leadersCurrentWGETA);
    }

    ///
    /// collect roads that we can build now into goodRoads
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0)
    {
      Iterator<SOCPossibleRoad> posRoadsIter = ourPlayerTracker.getPossibleRoads().values().iterator();
      while (posRoadsIter.hasNext())
      {
        SOCPossibleRoad posRoad = posRoadsIter.next();
        if (! posRoad.isRoadNotShip())
            continue;  // ignore ships in this loop, ships have other conditions to check

        if ((posRoad.getNecessaryRoads().isEmpty())
            && (! threatenedRoads.contains(posRoad))
            && (! goodRoads.contains(posRoad)))
        {
          goodRoads.add(posRoad);
        }
      }
    }

    ///
    /// and collect ships we can build now
    /// (if the pirate is adjacent, can't build there right now)
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SHIP) > 0)
    {
        final SOCBoard board = game.getBoard();
        final int pirateHex =
            (board instanceof SOCBoardLarge)
            ? ((SOCBoardLarge) board).getPirateHex()
            : 0;
        final int[] pirateEdges =
            (pirateHex != 0)
            ? ((SOCBoardLarge) board).getAdjacentEdgesToHex_arr(pirateHex)
            : null;

        Iterator<SOCPossibleRoad> posRoadsIter = ourPlayerTracker.getPossibleRoads().values().iterator();
        while (posRoadsIter.hasNext())
        {
            final SOCPossibleRoad posRoad = posRoadsIter.next();
            if (posRoad.isRoadNotShip())
                continue;  // ignore roads in this loop, we want ships

            if (posRoad.getNecessaryRoads().isEmpty()
                && (! threatenedRoads.contains(posRoad))
                && (! goodRoads.contains(posRoad)))
            {
                boolean edgeOK = true;
                if (pirateEdges != null)
                {
                    final int edge = posRoad.getCoordinates();
                    for (int i = 0; i < pirateEdges.length; ++i)
                    {
                        if (edge == pirateEdges[i])
                        {
                            edgeOK = false;
                            break;
                        }
                    }
                }

                if (edgeOK)
                    goodRoads.add(posRoad);
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
      D.ebugPrintlnINFO("*** threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());
      if (threatenedSet.getNecessaryRoads().isEmpty() &&
          ! ourPlayerData.isPotentialSettlement(threatenedSet.getCoordinates())) {
        D.ebugPrintlnINFO("POTENTIAL SETTLEMENT ERROR");
        //System.exit(0);
      }
    }
    Enumeration goodSetEnum = goodSettlements.elements();
    while (goodSetEnum.hasMoreElements()) {
      SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetEnum.nextElement();
      D.ebugPrintlnINFO("*** good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());
      if (goodSet.getNecessaryRoads().isEmpty() &&
          ! ourPlayerData.isPotentialSettlement(goodSet.getCoordinates())) {
        D.ebugPrintlnINFO("POTENTIAL SETTLEMENT ERROR");
        //System.exit(0);
      }
    }
    Enumeration threatenedRoadEnum = threatenedRoads.elements();
    while (threatenedRoadEnum.hasMoreElements()) {
      SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
      D.ebugPrintlnINFO("*** threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates())+" has a score of "+threatenedRoad.getScore());
      if (threatenedRoad.getNecessaryRoads().isEmpty() &&
          ! ourPlayerData.isPotentialRoad(threatenedRoad.getCoordinates())) {
        D.ebugPrintlnINFO("POTENTIAL ROAD ERROR");
        //System.exit(0);
      }
    }
    Enumeration goodRoadEnum = goodRoads.elements();
    while (goodRoadEnum.hasMoreElements()) {
      SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
      D.ebugPrintlnINFO("*** good road at "+Integer.toHexString(goodRoad.getCoordinates())+" has a score of "+goodRoad.getScore());
      if (goodRoad.getNecessaryRoads().isEmpty() &&
          ! ourPlayerData.isPotentialRoad(goodRoad.getCoordinates())) {
        D.ebugPrintlnINFO("POTENTIAL ROAD ERROR");
        //System.exit(0);
      }
    }
    */

    D.ebugPrintlnINFO("PICKING WHAT TO BUILD");

    ///
    /// pick what we want to build
    ///

    ///
    /// pick favoriteSettlement that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)
    {
      for (SOCPossibleSettlement threatenedSet : threatenedSettlements)
      {
        if (threatenedSet.getNecessaryRoads().isEmpty())
        {
          D.ebugPrintlnINFO("$$$$$ threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());

          if ((favoriteSettlement == null)
              || (threatenedSet.getScore() > favoriteSettlement.getScore()))
          {
            favoriteSettlement = threatenedSet;
          }
        }
      }

      for (SOCPossibleSettlement goodSet : goodSettlements)
      {
        if (goodSet.getNecessaryRoads().isEmpty())
        {
          D.ebugPrintlnINFO("$$$$$ good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());

          if ((favoriteSettlement == null)
              || (goodSet.getScore() > favoriteSettlement.getScore()))
          {
            favoriteSettlement = goodSet;
          }
        }
      }
    }

    //
    // restore the LRPath list
    //
    D.ebugPrintlnINFO("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < game.maxPlayers; pn++)
    {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    }

    ///
    /// pick a road that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0)
    {
      for (SOCPossibleRoad threatenedRoad : threatenedRoads)
      {
        D.ebugPrintlnINFO("$$$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

        if ((brain != null) && (brain.getDRecorder().isOn()))
        {
          brain.getDRecorder().startRecording
              ((threatenedRoad.isRoadNotShip() ? "ROAD" : "SHIP") + threatenedRoad.getCoordinates());
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

        D.ebugPrintlnINFO("wgetaScore = "+wgetaScore);

        if (favoriteRoad == null)
        {
          favoriteRoad = threatenedRoad;
        } else {
          if (threatenedRoad.getScore() > favoriteRoad.getScore())
            favoriteRoad = threatenedRoad;
        }
      }

      for (SOCPossibleRoad goodRoad : goodRoads)
      {
        D.ebugPrintlnINFO("$$$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));

        if ((brain != null) && (brain.getDRecorder().isOn()))
        {
          brain.getDRecorder().startRecording
              ( (goodRoad.isRoadNotShip() ? "ROAD" : "SHIP") + goodRoad.getCoordinates());
          brain.getDRecorder().record("Estimate value of road at "
              + game.getBoard().edgeCoordToString(goodRoad.getCoordinates()));
        }

        //
        // see how building this piece impacts our winETA
        //
        // TODO better ETA scoring for coastal ships/roads
        //
        goodRoad.resetScore();
        final int etype =
            ((goodRoad instanceof SOCPossibleShip) && ! ((SOCPossibleShip) goodRoad).isCoastalRoadAndShip)
            ? SOCBuildingSpeedEstimate.ROAD
            : SOCBuildingSpeedEstimate.SHIP;
        float wgetaScore = getWinGameETABonusForRoad(goodRoad, buildingETAs[etype], leadersCurrentWGETA, playerTrackers);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
          brain.getDRecorder().stopRecording();
        }

        D.ebugPrintlnINFO("wgetaScore = "+wgetaScore);

        if (favoriteRoad == null)
        {
          favoriteRoad = goodRoad;
        } else {
          if (goodRoad.getScore() > favoriteRoad.getScore())
            favoriteRoad = goodRoad;
        }
      }
    }

    //
    // restore the LRPath list
    //
    D.ebugPrintlnINFO("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < game.maxPlayers; pn++) {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    }

    ///
    /// pick a city that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0)
    {
      SOCPlayerTracker[] trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
      SOCPlayerTracker ourTrackerCopy = trackersCopy[ourPlayerNumber];
      int originalWGETAs[] = new int[game.maxPlayers];
      int WGETAdiffs[] = new int[game.maxPlayers];
      Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();
      int bestWGETA = 1000;
      // int bonus = 0;

      Iterator<SOCPossibleCity> posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
      while (posCitiesIter.hasNext())
      {
        SOCPossibleCity posCity = posCitiesIter.next();
        if ((brain != null) && (brain.getDRecorder().isOn()))
        {
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

        for (final SOCPlayerTracker trackerBefore : trackersCopy)
        {
          if (trackerBefore == null)
            continue;

          final int pn = trackerBefore.getPlayer().getPlayerNumber();
          D.ebugPrintlnINFO("$$$ win game ETA for player " + pn + " = " + trackerBefore.getWinGameETA());
          originalWGETAs[pn] = trackerBefore.getWinGameETA();
          WGETAdiffs[pn] = trackerBefore.getWinGameETA();
          if (trackerBefore.getWinGameETA() < bestWGETA)
          {
            bestWGETA = trackerBefore.getWinGameETA();
            leaders.removeAllElements();
            leaders.addElement(trackerBefore);
          } else if (trackerBefore.getWinGameETA() == bestWGETA) {
            leaders.addElement(trackerBefore);
          }
        }
        D.ebugPrintlnINFO("^^^^ bestWGETA = "+bestWGETA);
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

        D.ebugPrintlnINFO("*** ETA for city = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
          brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
        }

        float etaBonus = getETABonus(buildingETAs[SOCBuildingSpeedEstimate.CITY], leadersCurrentWGETA, wgetaScore);
        D.ebugPrintlnINFO("etaBonus = "+etaBonus);

        posCity.addToScore(etaBonus);
        //posCity.addToScore(wgetaScore);

        if ((brain != null) && (brain.getDRecorder().isOn()))
        {
          brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
          brain.getDRecorder().record("Total city score = "+df1.format(etaBonus));
          brain.getDRecorder().stopRecording();
        }

        D.ebugPrintlnINFO("$$$  final score = "+posCity.getScore());

        D.ebugPrintlnINFO("$$$$$ possible city at "+Integer.toHexString(posCity.getCoordinates())+" has a score of "+posCity.getScore());

        if ((favoriteCity == null)
            || (posCity.getScore() > favoriteCity.getScore()))
        {
          favoriteCity = posCity;
        }
      }
    }

    if (favoriteSettlement != null) {
      D.ebugPrintlnINFO("### FAVORITE SETTLEMENT IS AT "+Integer.toHexString(favoriteSettlement.getCoordinates()));
      D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteSettlement.getScore());
      D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]);
      D.ebugPrintlnINFO("###   WITH A TOTAL SPEEDUP OF "+favoriteSettlement.getSpeedupTotal());
    }

    if (favoriteCity != null) {
      D.ebugPrintlnINFO("### FAVORITE CITY IS AT "+Integer.toHexString(favoriteCity.getCoordinates()));
      D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteCity.getScore());
      D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
      D.ebugPrintlnINFO("###   WITH A TOTAL SPEEDUP OF "+favoriteCity.getSpeedupTotal());
    }

    final int road_eta_type =
        ((favoriteRoad != null) && (favoriteRoad instanceof SOCPossibleShip)
         && ! ((SOCPossibleShip) favoriteRoad).isCoastalRoadAndShip)  // TODO better ETA calc for coastal roads/ships
        ? SOCBuildingSpeedEstimate.SHIP
        : SOCBuildingSpeedEstimate.ROAD;

    if (favoriteRoad != null) {
      D.ebugPrintlnINFO("### FAVORITE ROAD IS AT "+Integer.toHexString(favoriteRoad.getCoordinates()));
      D.ebugPrintlnINFO("###   WITH AN ETA OF "+buildingETAs[road_eta_type]);
      D.ebugPrintlnINFO("###   WITH A SCORE OF "+favoriteRoad.getScore());
    }

    int pick = -1;  // piece type, if any, to be pushed onto buildingPlan;
         // use ROAD for road or ship, use MAXPLUSONE for dev card

    float pickScore = 0f;  // getScore() of picked piece

    ///
    /// if the favorite settlement and road can wait, and
    /// favoriteCity has the best score and ETA, then build the city
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
          (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[road_eta_type]))))
    {
      D.ebugPrintlnINFO("### PICKED FAVORITE CITY");
      pick = SOCPlayingPiece.CITY;
      pickScore = favoriteCity.getScore();
    }

    ///
    /// if there is a road with a better score than
    /// our favorite settlement, then build the road,
    /// else build the settlement
    ///
    else if ((favoriteRoad != null)
             && (ourPlayerData.getNumPieces(favoriteRoad.getType()) > 0)
             && (favoriteRoad.getScore() > 0)
             && ((favoriteSettlement == null)
                 || (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0)
                 || (favoriteSettlement.getScore() < favoriteRoad.getScore())))
    {
      D.ebugPrintlnINFO("### PICKED FAVORITE ROAD");
      pick = SOCPlayingPiece.ROAD;  // also represents SHIP here
      pickScore = favoriteRoad.getScore();
    }
    else if ((favoriteSettlement != null)
             && (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0))
    {
      D.ebugPrintlnINFO("### PICKED FAVORITE SETTLEMENT");
      pick = SOCPlayingPiece.SETTLEMENT;
      pickScore = favoriteSettlement.getScore();
    }

    ///
    /// if buying a card is better than building...
    ///

    //
    // see how buying a card improves our win game ETA
    //
    float devCardScore = 0;
    if ((game.getNumDevCards() > 0) && ! forSpecialBuildingPhase)
    {
      if ((brain != null) && (brain.getDRecorder().isOn())) {
        brain.getDRecorder().startRecording("DEVCARD");
        brain.getDRecorder().record("Estimate value of a dev card");
      }

      possibleCard = getDevCardScore(buildingETAs[SOCBuildingSpeedEstimate.CARD], leadersCurrentWGETA);
      devCardScore = possibleCard.getScore();
      D.ebugPrintlnINFO("### DEV CARD SCORE: "+devCardScore);
      if ((brain != null) && (brain.getDRecorder().isOn())) {
        brain.getDRecorder().stopRecording();
      }

      if ((pick == -1)
          || (devCardScore > pickScore))
      {
        D.ebugPrintlnINFO("### BUY DEV CARD");
        pick = SOCPlayingPiece.MAXPLUSONE;
        pickScore = devCardScore;
      }
    }

    if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI)
        || game.isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
    {
        if (scenarioGameStrategyPlan
            (pickScore, devCardScore, true, (pick == SOCPlayingPiece.MAXPLUSONE),
             getEstimator(ourPlayerData.getNumbers()),
             leadersCurrentWGETA, forSpecialBuildingPhase))
          return;  // <--- Early return: Scenario-specific buildingPlan was pushed ---
    }

    //
    // push our picked piece onto buildingPlan
    //
    switch (pick)
    {
    case SOCPlayingPiece.ROAD:
        D.ebugPrintlnINFO("$ PUSHING " + favoriteRoad);
        buildingPlan.push(favoriteRoad);
        break;

    case SOCPlayingPiece.SETTLEMENT:
        D.ebugPrintlnINFO("$ PUSHING " + favoriteSettlement);
        buildingPlan.push(favoriteSettlement);
        break;

    case SOCPlayingPiece.CITY:
        D.ebugPrintlnINFO("$ PUSHING " + favoriteCity);
        buildingPlan.push(favoriteCity);
        break;

    case SOCPlayingPiece.MAXPLUSONE:
        D.ebugPrintlnINFO("$ PUSHING " + possibleCard);
        buildingPlan.push(possibleCard);
        break;
    }

  }

  /**
   * For some game scenarios (currently {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI} and
   * {@link SOCGameOptionSet#K_SC_WOND _SC_WOND}), evaluate and plan any special move.
   * If the scenario-specific move would score higher than the currently picked building plan
   * from {@link #smartGameStrategy(int[])} or {@link #dumbFastGameStrategy(int[])}, push those scenario-specific
   * moves onto {@link #buildingPlan}.
   *<P>
   * Example of such a move: For {@code _SC_PIRI}, each player must build ships out west to their
   * pirate fortress, upgrading them to warships to defend against the pirate fleet and build strength
   * to attack and defeat the fortress.
   *
   * @param bestScoreOrETA  Current plan's score for {@code SMART_STRATEGY} or ETA for {@code FAST_STRATEGY}.
   * @param cardScoreOrETA  Score or ETA to buy a card if known, or -1.
   *          {@code smartGameStrategy} should always calculate this if the move makes sense,
   *          {@code dumbFastGameStrategy} skips it and calculates the ETA for several cards.
   * @param isScoreNotETA  True if {@code bestScoreOrETA} is a score and not a building ETA;
   *          higher scores are better, lower ETAs are better
   * @param bestPlanIsDevCard  True if the current best plan is to buy a dev card
   * @param ourBSE  Our player's current {@link SOCBuildingSpeedEstimate} with our {@link SOCPlayer#getNumbers()}
   * @param leadersCurrentWGETA  For {@code SMART_STRATEGY}, the game leader's Win Game ETA from
   *          {@link SOCPlayerTracker#getWinGameETA()}.  Unused here (0) for {@code FAST_STRATEGY}.
   * @param forSpecialBuildingPhase  True if we're in the {@link SOCGame#SPECIAL_BUILDING} Phase, not our full turn
   * @return  True if a Scenario-specific buildingPlan was pushed
   * @throws IllegalArgumentException if {@code smartGameStrategy} didn't calculate {@code cardScoreOrETA} and it's -1
   * @since 2.0.00
   */
  protected boolean scenarioGameStrategyPlan
      (final float bestScoreOrETA, float cardScoreOrETA, final boolean isScoreNotETA,
       final boolean bestPlanIsDevCard, final SOCBuildingSpeedEstimate ourBSE, final int leadersCurrentWGETA,
       final boolean forSpecialBuildingPhase)
      throws IllegalArgumentException
  {
      if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
          return scenarioGameStrategyPlan_SC_PIRI
              (bestScoreOrETA, cardScoreOrETA, isScoreNotETA, bestPlanIsDevCard, ourBSE,
               leadersCurrentWGETA, forSpecialBuildingPhase);
      else if (game.isGameOptionSet(SOCGameOptionSet.K_SC_WOND))
          return scenarioGameStrategyPlan_SC_WOND
              (bestScoreOrETA, cardScoreOrETA, isScoreNotETA, bestPlanIsDevCard, ourBSE,
               leadersCurrentWGETA, forSpecialBuildingPhase);
      else
          return false;
  }

  /**
   * {@link #scenarioGameStrategyPlan(float, float, boolean, boolean, SOCBuildingSpeedEstimate, int, boolean) scenarioGameStrategyPlan(..)}
   * for {@link SOCGameOptionSet#K_SC_PIRI _SC_PIRI}.  See that method for parameter meanings and other info.
   * @since 2.0.00
   */
  private final boolean scenarioGameStrategyPlan_SC_PIRI
      (final float bestScoreOrETA, float cardScoreOrETA, final boolean isScoreNotETA,
       final boolean bestPlanIsDevCard, final SOCBuildingSpeedEstimate ourBSE, final int leadersCurrentWGETA,
       final boolean forSpecialBuildingPhase)
      throws IllegalArgumentException
  {
    final int ourVP = ourPlayerData.getTotalVP();
    if (ourVP < 4)
    {
      return false;  // <--- Early return: We don't have 4 VP, don't use resources to build out to sea yet ---
    }

    final int ourNumWarships = ourPlayerData.getNumWarships();

    // evaluate game status (current VP, etc); calc scenario-specific options and scores
    //    If bestPlanIsDevCard, don't recalc cardScoreOrETA for buying a warship card

    float shipScoreOrETA;
    int shipETA;
    int shipsBuilt = SOCPlayer.SHIP_COUNT - ourPlayerData.getNumPieces(SOCPlayingPiece.SHIP);

    boolean mightBuildShip = false;
    if ((shipsBuilt >= 6)
        && ((ourVP < 6) || (ourNumWarships < 2) || (ourPlayerData.getFortress() == null)))
    {
        // During early game: Enough ships already built to upgrade for defense (since max dice is 6).
        // Later in game (6+ VP, 2+ warships): need more ships to build out to pirate fortress;
        // getFortress() null if already defeated it.
        // TODO if no other plans, build another ship here if we can

        shipETA = 100;
        shipScoreOrETA = 0f;
    } else {
        // Calculate ETA to buy and build another ship
        mightBuildShip = true;
        shipETA = ourBSE.calculateRollsFast
          (ourPlayerData.getResources(), SOCShip.COST, 100, ourPlayerData.getPortFlags());
        if (! isScoreNotETA)
        {
            shipScoreOrETA = shipETA;
        } else {
            shipScoreOrETA = (100.0f / game.maxPlayers);
            shipScoreOrETA += getETABonus(shipETA, leadersCurrentWGETA, shipScoreOrETA);  // TODO double-check params here
        }
    }

    boolean mightBuyWarshipCard = false;
    final int warshipCardsInHand =
        ourPlayerData.getInventory().getAmount(SOCDevCardConstants.KNIGHT);

    if (warshipCardsInHand > 0)
    {
        // Enough already bought for now, don't bother calculating ETA
        //    TODO if no other plans, consider buying another anyway
        cardScoreOrETA = 100;
    }
    else if (cardScoreOrETA < 0)
    {
        // ETA not provided by caller: calculate it

        if (isScoreNotETA)
            throw new IllegalArgumentException("cardScoreOrETA");  // should already be calculated

        if (game.getNumDevCards() > 0)
        {
            cardScoreOrETA = ourBSE.calculateRollsFast
              (ourPlayerData.getResources(), SOCDevCard.COST, 100, ourPlayerData.getPortFlags());
        } else {
            cardScoreOrETA = 100;
        }
    }

    if ((ourNumWarships < 6) && (game.getNumDevCards() > 0))
    {
        if (warshipCardsInHand == 0)
            mightBuyWarshipCard = true;

            // TODO if no other plans, consider buying another
    }

    // System.err.println("L1848 bot " + ourPlayerData.getName() + (isScoreNotETA ? ": best score " : ": best ETA ")
    //     + bestScoreOrETA + "; card " + cardScoreOrETA + ", ship " + shipScoreOrETA + "; shipsBuilt " + shipsBuilt);

    if (! (mightBuildShip || mightBuyWarshipCard))
    {
        return false;  // <--- Early return: No special action at this time ---
    }

    // TODO Weight it for VP or time; ideally we at least have 5 or 6 warships to defend against pirate attacks

    // If it scores highly: Pick a scenario building plan, push it, return true

    boolean betterScoreIsBuildShip = false;  // this var is used only if build, buy are both possible options
    final float scenPlanScoreOrETA;
    if (! mightBuyWarshipCard)
        scenPlanScoreOrETA = shipScoreOrETA;
    else if (! mightBuildShip)
        scenPlanScoreOrETA = cardScoreOrETA;
    else
    {
        if (isScoreNotETA)
            betterScoreIsBuildShip = (shipScoreOrETA > cardScoreOrETA);
        else
            betterScoreIsBuildShip = (shipScoreOrETA < cardScoreOrETA);

        scenPlanScoreOrETA = (betterScoreIsBuildShip) ? shipScoreOrETA : cardScoreOrETA;
    }

    // compare to non-scenario bestScoreOrETA
    if (isScoreNotETA)
    {
        if (bestScoreOrETA > scenPlanScoreOrETA)
            return false;
    } else {
        if (bestScoreOrETA < scenPlanScoreOrETA)
            return false;
    }

    // OK, at least 1 of the 2 scenario actions scores higher than the non-scenario planned action.

    if (mightBuildShip)
    {
        if (mightBuyWarshipCard && ! betterScoreIsBuildShip)
        {
            buildingPlan.push(new SOCPossibleCard(ourPlayerData, 1));
            return true;
        } else {
            // plan to build it if possible, else fall through.
            if (scenarioGameStrategyPlan_SC_PIRI_buildNextShip())
                return true;
        }
    }

    if (mightBuyWarshipCard)
    {
        buildingPlan.push(new SOCPossibleCard(ourPlayerData, 1));
        return true;
    }

    return false;
  }

  /**
   * If possible, calculate where our next ship would be placed, and add it to {@link #buildingPlan}.
   * Assumes our player's {@link SOCPlayer#getFortress()} is west of all boats we've already placed.
   * If our line of ships has reached the fortress per {@link SOCPlayer#getMostRecentShip()},
   * nothing to do: That goal is complete.
   * @return True if next ship is possible and was added to {@link #buildingPlan}
   * @since 2.0.00
   */
  private final boolean scenarioGameStrategyPlan_SC_PIRI_buildNextShip()
  {
    SOCShip prevShip = ourPlayerData.getMostRecentShip();
    if (prevShip == null)
        return false;  // player starts with 1 ship, so should never be null

    final int fortressNode;
    {
        final SOCFortress fo = ourPlayerData.getFortress();
        if (fo == null)
            return false;  // already defeated it

        fortressNode = fo.getCoordinates();
    }

    final int prevShipNode;
    {
        final int[] nodes = prevShip.getAdjacentNodes();
        final int c0 = nodes[0] & 0xFF,
                  c1 = nodes[1] & 0xFF;

        if (c0 < c1)
            prevShipNode = nodes[0];
        else if (c1 < c0)
            prevShipNode = nodes[1];
        else
        {
            // prevShip goes north-south; check its node rows vs fortress row
            final int r0 = nodes[0] >> 8,
                      r1 = nodes[1] >> 8,
                      rFort = fortressNode >> 8;

           if (Math.abs(rFort - r0) < Math.abs(rFort - r1))
               prevShipNode = nodes[0];
           else
               prevShipNode = nodes[1];
        }
    }

    if (prevShipNode == fortressNode)
    {
        // our line of ships has reached the fortress

        return false;
    }

    // Get the player's ship path towards fortressNode from prevShip.
    // We need to head west, possibly north or south.
    final HashSet<Integer> lse = ourPlayerData.getRestrictedLegalShips();
    if (lse == null)
        return false;  // null lse should not occur in _SC_PIRI

    // Need 1 or 2 edges that are in lse and aren't prevShipEdge,
    //    and the edge's far node is either further west than prevShipNode,
    //    or is vertical and takes us closer north or south to the fortress.
    int edge1 = -9, edge2 = -9;
    final SOCBoard board = game.getBoard();
    final int prevShipEdge = prevShip.getCoordinates();
    int[] nextPossiEdges = board.getAdjacentEdgesToNode_arr(prevShipNode);
    for (int i = 0; i < nextPossiEdges.length; ++i)
    {
        final int edge = nextPossiEdges[i];
        if ((edge == -9) || (edge == prevShipEdge) || ! lse.contains(Integer.valueOf(edge)))
            continue;

        // be sure this edge takes us towards fortressNode
        final int farNode = board.getAdjacentNodeFarEndOfEdge(edge, prevShipNode);
        final int cShip = prevShipNode & 0xFF,
                  cEdge = farNode & 0xFF;
        if (cEdge > cShip)
        {
            continue;  // farNode is east, not west
        } else if (cEdge == cShip) {
            final int rShip = prevShipNode >> 8,
                      rEdge = farNode >> 8,
                      rFort = fortressNode >> 8;
           if (Math.abs(rFort - rEdge) > Math.abs(rFort - rShip))
               continue;  // farNode isn't closer to fortress
        }

        // OK
        if (edge1 == -9)
            edge1 = edge;
        else
            edge2 = edge;
    }

    if (edge1 == -9)
        return false;  // happens if we've built ships out to fortressNode already

    final int newEdge;
    if ((edge2 == -9) || (Math.random() < 0.5))
        newEdge = edge1;
    else
        newEdge = edge2;

    buildingPlan.add(new SOCPossibleShip(ourPlayerData, newEdge, false, null));
    // System.err.println("L2112 ** " + ourPlayerData.getName()
    //     + ": Planned possible ship at 0x" + Integer.toHexString(newEdge) + " towards fortress");

    return true;
  }

  /**
   * {@link #scenarioGameStrategyPlan(float, float, boolean, boolean, SOCBuildingSpeedEstimate, int, boolean) scenarioGameStrategyPlan(..)}
   * for {@link SOCGameOptionSet#K_SC_WOND _SC_WOND}.  See that method for parameter meanings and other info.
   * @since 2.0.00
   */
  private final boolean scenarioGameStrategyPlan_SC_WOND
      (final float bestScoreOrETA, float cardScoreOrETA, final boolean isScoreNotETA,
       final boolean bestPlanIsDevCard, final SOCBuildingSpeedEstimate ourBSE, final int leadersCurrentWGETA,
       final boolean forSpecialBuildingPhase)
      throws IllegalArgumentException
  {
    final int ourVP = ourPlayerData.getTotalVP();
    if (ourVP < 4)
    {
      return false;  // <--- Early return: We don't have 4 VP, don't use resources to build wonders yet ---
    }

    // evaluate game status (current VP, etc); calc scenario-specific options and scores
    // are we already building a wonder?
    // if not, should we pick one now?
    // To win, some work on a wonder is required, but we don't have to finish it,
    // only be farther along than any other player.

    // Look for what we could build based on wonder requirements;
    // calc scores/BSEs for them (if any); pick one.
    // Once building it, calc score/BSE to add a level when possible if another player's wonder level is close,
    // until we have 2 more levels than any other player.

    SOCSpecialItem bestWond = ourPlayerData.getSpecialItem(SOCGameOptionSet.K_SC_WOND, 0);
    int bestETA;
    float bestWondScoreOrETA;
    int gi = -1;  // wonder's "game index" in Special Item interface

    // TODO check level vs other players' level; if 2+ ahead of all others, no need to build more.
    //    final int pLevel = (bestWond != null) ? bestWond.getLevel() : 0;
    // No need to check against max levels: if we've already reached max level, game has ended

    if (bestWond != null)
    {
        gi = bestWond.getGameIndex();

        // Calc score or ETA to continue building pWond

        bestETA = ourBSE.calculateRollsFast
            (ourPlayerData.getResources(), bestWond.getCost(), 100, ourPlayerData.getPortFlags());
        if (isScoreNotETA)
        {
            bestWondScoreOrETA = (100.0f / game.maxPlayers);
            bestWondScoreOrETA += getETABonus(bestETA, leadersCurrentWGETA, bestWondScoreOrETA);
        } else {
            bestWondScoreOrETA = bestETA;
        }

    } else {
        // No wonder has been chosen yet; look at all available ones.

        // these will be given their actual values when bestWond is first assigned
        int wETA = 0;
        float wScoreOrETA = 0f;

        final int numWonders = 1 + game.maxPlayers;
        for (int i = 0; i < numWonders; ++i)
        {
            SOCSpecialItem wond = game.getSpecialItem(SOCGameOptionSet.K_SC_WOND, i+1);

            if (wond.getPlayer() != null)
                continue;  // already claimed
            if (! wond.checkRequirements(ourPlayerData, false))
                continue;  // TODO potentially could plan how to reach requirements (build a settlement or city, etc)

            int eta = ourBSE.calculateRollsFast
                (ourPlayerData.getResources(), wond.getCost(), 100, ourPlayerData.getPortFlags());
            float scoreOrETA;
            if (isScoreNotETA)
            {
                scoreOrETA = (100.0f / game.maxPlayers);
                scoreOrETA += getETABonus(eta, leadersCurrentWGETA, scoreOrETA);
            } else {
                scoreOrETA = eta;
            }

            boolean isBetter;
            if (bestWond == null)
                isBetter = true;
            else if (isScoreNotETA)
                isBetter = (scoreOrETA > wScoreOrETA);
            else   // is ETA
                isBetter = (scoreOrETA < wScoreOrETA);

            if (isBetter)
            {
                bestWond = wond;
                wETA = eta;
                wScoreOrETA = scoreOrETA;
                gi = i + 1;
            }
        }

        if (bestWond == null)
            return false;  // couldn't meet any unclaimed wonder's requirements

        bestETA = wETA;
        bestWondScoreOrETA = wScoreOrETA;
    }

    // Compare bestWond's score or ETA to our current plans

    // System.err.println("L2296 bot " + ourPlayerData.getName() + (isScoreNotETA ? ": best score " : ": best ETA ")
    //     + bestScoreOrETA + "; card " + cardScoreOrETA + ", wondScoreOrETA " + bestWondScoreOrETA);

    // If it scores highly: Push the scenario building plan, push it, return true
    if (isScoreNotETA)
    {
        if (bestScoreOrETA > bestWondScoreOrETA)
            return false;
    } else {
        if (bestScoreOrETA < bestWondScoreOrETA)
            return false;
    }

    // System.err.println("L2297 -> add to buildingPlan: gi=" + gi);
    buildingPlan.add(new SOCPossiblePickSpecialItem
        (ourPlayerData, SOCGameOptionSet.K_SC_WOND, gi, 0, bestETA, bestWond.getCost()));

    return true;
  }

  /**
   * Score possible settlements for for the smart game strategy ({@link #SMART_STRATEGY}),
   * from {@link #ourPlayerTracker}{@link SOCPlayerTracker#getPossibleSettlements() .getPossibleSettlements()}
   * into {@link #threatenedSettlements} and {@link #goodSettlements};
   * calculate those settlements' {@link SOCPossiblePiece#getScore()}s.
   * Ignores possible settlements that require roads or ships.
   *
   * @param settlementETA  the estimated time to build a settlement
   * @param leadersCurrentWGETA  the leading player's estimated time to win the game
   * @see #scoreSettlementsForDumb(int, SOCBuildingSpeedEstimate)
   */
  protected void scorePossibleSettlements(final int settlementETA, final int leadersCurrentWGETA)
  {
    D.ebugPrintlnINFO("****** scorePossibleSettlements");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

    /*
    boolean goingToPlayRB = false;
    if (! ourPlayerData.hasPlayedDevCard() &&
        ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
        ourPlayerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS)) {
      goingToPlayRB = true;
    }
    */

    Iterator<SOCPossibleSettlement> posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext())
    {
      SOCPossibleSettlement posSet = posSetsIter.next();
      D.ebugPrintlnINFO("*** scoring possible settlement at "+Integer.toHexString(posSet.getCoordinates()));
      if (! threatenedSettlements.contains(posSet))
      {
          threatenedSettlements.add(posSet);
      } else if (! goodSettlements.contains(posSet)) {
          goodSettlements.add(posSet);
      }

      //
      // only consider settlements we can build now
      //
      if (posSet.getNecessaryRoads().isEmpty())
      {
        D.ebugPrintlnINFO("*** no roads needed");
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

        SOCPlayerTracker[] trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
        SOCPlayerTracker.updateWinGameETAs(trackersCopy);
        float wgetaScore = calcWGETABonus(playerTrackers, trackersCopy);
        D.ebugPrintlnINFO("***  wgetaScore = "+wgetaScore);

        D.ebugPrintlnINFO("*** ETA for settlement = "+settlementETA);
        if ((brain != null) && (brain.getDRecorder().isOn())) {
          brain.getDRecorder().record("ETA = "+settlementETA);
        }

        float etaBonus = getETABonus(settlementETA, leadersCurrentWGETA, wgetaScore);
        D.ebugPrintlnINFO("etaBonus = "+etaBonus);

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
    SOCPlayerTracker[] trackersCopy = null;
    SOCSettlement tmpSet = null;
    SOCCity tmpCity = null;
    SOCRoutePiece tmpRS = null;  // road or ship
    float bonus = 0;

    D.ebugPrintlnINFO("--- before [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    D.ebugPrintlnINFO("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintlnINFO("--- before [end] ---");

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
      SOCPlayerTracker trackerCopy = trackersCopy[ourPlayerNumber];
      if (trackerCopy != null) {
        trackerCopy.addOurNewCity(tmpCity);
      }
      break;

    case SOCPossiblePiece.ROAD:
      tmpRS = new SOCRoad(ourPlayerData, posPiece.getCoordinates(), null);
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRS, game, playerTrackers);
      break;

    case SOCPossiblePiece.SHIP:
      tmpRS = new SOCShip(ourPlayerData, posPiece.getCoordinates(), null);
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRS, game, playerTrackers);
      break;
    }

    //trackersCopyIter = trackersCopy.iterator();
    //while (trackersCopyIter.hasNext()) {
    //        SOCPlayerTracker trackerCopy = (SOCPlayerTracker)trackersCopyIter.next();
    //        trackerCopy.updateThreats(trackersCopy);
    //}

    D.ebugPrintlnINFO("--- after [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(trackersCopy);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);

    float WGETABonus = calcWGETABonus(playerTrackers, trackersCopy);
    D.ebugPrintlnINFO("$$$ win game ETA bonus : +"+WGETABonus);
    bonus = WGETABonus;

    D.ebugPrintlnINFO("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintlnINFO("--- after [end] ---");

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
      SOCPlayerTracker.undoTryPutPiece(tmpRS, game);
      break;
    }

    D.ebugPrintlnINFO("our player numbers = "+ourPlayerData.getNumbers());
    D.ebugPrintlnINFO("--- cleanup done ---");

    return bonus;
  }


  /**
   * For {@link #SMART_STRATEGY}, add a bonus to the road or ship score
   * based on the change in win game ETA for this one road or ship
   * (possible settlements are 1 road closer, longest road bonus, etc).
   *<UL>
   * <LI> Calls {@link SOCPlayerTracker#tryPutPiece(SOCPlayingPiece, SOCGame, SOCPlayerTracker[])}
   *      which makes a copy of the player trackers and puts the piece there.
   *      This also updates our player's VP total, including any special VP from placement.
   * <LI> Calls {@link SOCPlayerTracker#updateWinGameETAs(SOCPlayerTracker[])} on that copy
   * <LI> Calls {@link #calcWGETABonus(SOCPlayerTracker[], SOCPlayerTracker[])} to compare WGETA before and after placement
   * <LI> Calls {@link #getETABonus(int, int, float)} to weigh that bonus
   * <LI> Adds that to {@code posRoad}'s {@link SOCPossiblePiece#getScore()}
   * <LI> Cleans up with {@link SOCPlayerTracker#undoTryPutPiece(SOCPlayingPiece, SOCGame)}
   *</UL>
   *
   * @param posRoad  the possible piece that we're scoring
   * @param roadETA  the ETA for a road or ship, from building speed estimates
   * @param leadersCurrentWGETA  the leaders current WGETA
   * @param plTrackers  the player trackers (for figuring out road building plan and bonus/ETA)
   */
  protected float getWinGameETABonusForRoad
      (final SOCPossibleRoad posRoad, final int roadETA, final int leadersCurrentWGETA,
       final SOCPlayerTracker[] plTrackers)
  {
    D.ebugPrintlnINFO("--- addWinGameETABonusForRoad");
    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    D.ebugPrintlnINFO("ourCurrentWGETA = "+ourCurrentWGETA);

    SOCPlayerTracker[] trackersCopy = null;
    SOCRoutePiece tmpRS = null;
    // Building road or ship?  TODO Better ETA calc for coastal road/ship
    final boolean isShip = (posRoad instanceof SOCPossibleShip)
        && ! ((SOCPossibleShip) posRoad).isCoastalRoadAndShip;
    final SOCResourceSet rsrcs = (isShip ? SOCShip.COST : SOCRoad.COST);

    D.ebugPrintlnINFO("--- before [start] ---");
    SOCResourceSet originalResources = ourPlayerData.getResources().copy();
    SOCBuildingSpeedEstimate estimate = getEstimator(ourPlayerData.getNumbers());
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    D.ebugPrintlnINFO("--- before [end] ---");
    try
    {
      SOCResSetBuildTimePair btp = estimate.calculateRollsAndRsrcFast
          (ourPlayerData.getResources(), rsrcs, 50, ourPlayerData.getPortFlags());
      btp.getResources().subtract(rsrcs);
      ourPlayerData.getResources().setAmounts(btp.getResources());
    } catch (CutoffExceededException e) {
      D.ebugPrintlnINFO("crap in getWinGameETABonusForRoad - "+e);
    }
    tmpRS = (isShip)
        ? new SOCShip(ourPlayerData, posRoad.getCoordinates(), null)
        : new SOCRoad(ourPlayerData, posRoad.getCoordinates(), null);

    trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRS, game, plTrackers);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);
    float score = calcWGETABonus(plTrackers, trackersCopy);

    if (! posRoad.getThreats().isEmpty())
    {
      score *= threatMultiplier;
      D.ebugPrintlnINFO("***  (THREAT MULTIPLIER) score * "+threatMultiplier+" = "+score);
    }
    D.ebugPrintlnINFO("*** ETA for road = "+roadETA);
    float etaBonus = getETABonus(roadETA, leadersCurrentWGETA, score);
    D.ebugPrintlnINFO("$$$ score = "+score);
    D.ebugPrintlnINFO("etaBonus = "+etaBonus);
    posRoad.addToScore(etaBonus);

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("ETA = "+roadETA);
      brain.getDRecorder().record("WGETA Score = "+df1.format(score));
      brain.getDRecorder().record("Total road score = "+df1.format(etaBonus));
    }

    D.ebugPrintlnINFO("--- after [end] ---");
    SOCPlayerTracker.undoTryPutPiece(tmpRS, game);
    ourPlayerData.getResources().clear();
    ourPlayerData.getResources().add(originalResources);
    D.ebugPrintlnINFO("--- cleanup done ---");

    return etaBonus;
  }

  /**
   * Calc the win game ETA bonus for a move, based on {@link SOCPlayerTracker#getWinGameETA()}.
   * The bonus is based on lowering your bot's WGETA and increasing the leaders' WGETA.
   *
   * @param  trackersBefore   list of player trackers before move
   * @param  trackersAfter    list of player trackers after move; call
   *           {@link SOCPlayerTracker#updateWinGameETAs(SOCPlayerTracker[]) SOCPlayerTracker.updateWinGameETAs(trackersAfter)}
   *           before calling this method
   */
  protected float calcWGETABonus
      (final SOCPlayerTracker[] trackersBefore, final SOCPlayerTracker[] trackersAfter)
  {
    D.ebugPrintlnINFO("^^^^^ calcWGETABonus");
    int originalWGETAs[] = new int[game.maxPlayers];
    int WGETAdiffs[] = new int[game.maxPlayers];
    Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();  // Players winning soonest, based on ETA
    int bestWGETA = 1000;  // Lower is better
    float bonus = 0;

    for (final SOCPlayerTracker trackerBefore : trackersBefore)
    {
      if (trackerBefore == null)
        continue;

      final int pn = trackerBefore.getPlayer().getPlayerNumber();
      D.ebugPrintlnINFO("$$$ win game ETA for player " + pn + " = " + trackerBefore.getWinGameETA());
      originalWGETAs[pn] = trackerBefore.getWinGameETA();
      WGETAdiffs[pn] = trackerBefore.getWinGameETA();

      if (trackerBefore.getWinGameETA() < bestWGETA)
      {
        bestWGETA = trackerBefore.getWinGameETA();
        leaders.removeAllElements();
        leaders.addElement(trackerBefore);
      } else if (trackerBefore.getWinGameETA() == bestWGETA) {
        leaders.addElement(trackerBefore);
      }
    }

    D.ebugPrintlnINFO("^^^^ bestWGETA = "+bestWGETA);

    bonus = calcWGETABonusAux(originalWGETAs, trackersAfter, leaders);

    D.ebugPrintlnINFO("^^^^ final bonus = "+bonus);

    return bonus;
  }

  /**
   * Helps calculate WGETA bonus for making a move or other change in the game.
   * The bonus is based on lowering your bot's WGETA and increasing the leaders' WGETA.
   *
   * @param originalWGETAs   the original WGETAs; each player's {@link SOCPlayerTracker#getWinGameETA()} before the change
   * @param trackersAfter    the playerTrackers after the change; call
   *          {@link SOCPlayerTracker#updateWinGameETAs(SOCPlayerTracker[]) SOCPlayerTracker.updateWinGameETAs(trackersAfter)}
   *          before calling this method
   * @param leaders          a list of leaders (players winning soonest);
   *          the player(s) with lowest {@link SOCPlayerTracker#getWinGameETA()}.
   *          Contains only one element, unless there is an ETA tie.
   */
  protected float calcWGETABonusAux
      (final int[] originalWGETAs, final SOCPlayerTracker[] trackersAfter, final Vector<SOCPlayerTracker> leaders)
  {
    int WGETAdiffs[] = new int[game.maxPlayers];
    int bestWGETA = 1000;
    float bonus = 0;

    for (int i = 0; i < game.maxPlayers; i++)
    {
      WGETAdiffs[i] = originalWGETAs[i];
      if (originalWGETAs[i] < bestWGETA)
        bestWGETA = originalWGETAs[i];
    }

    for (final SOCPlayerTracker trackerAfter : trackersAfter)
    {
      if (trackerAfter == null)
        continue;

      final int pn = trackerAfter.getPlayer().getPlayerNumber();
      WGETAdiffs[pn] -= trackerAfter.getWinGameETA();
      D.ebugPrintlnINFO("$$$ win game ETA diff for player " + pn + " = " + WGETAdiffs[pn]);
      if (pn == ourPlayerNumber)
      {
        if (trackerAfter.getWinGameETA() == 0)
        {
          D.ebugPrintlnINFO("$$$$ adding win game bonus : +"+(100 / game.maxPlayers));
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
    if ((originalWGETAs[ourPlayerNumber] > 0)
        && (bonus == 0))
    {
      bonus += ((100.0f / game.maxPlayers) * ((float)WGETAdiffs[ourPlayerNumber] / (float)originalWGETAs[ourPlayerNumber]));
    }

    D.ebugPrintlnINFO("^^^^ our current bonus = "+bonus);
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
            D.ebugPrintlnINFO("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
            if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0))
              brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
          } else if (WGETAdiffs[pn] < 0) {
            float takedownBonus = (100.0f / game.maxPlayers) * adversarialFactor;
            bonus += takedownBonus;
            D.ebugPrintlnINFO("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
            if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0))
              brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
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
          D.ebugPrintlnINFO("^^^^ added takedown bonus for leader " + leaderPN + " : +" + takedownBonus);
          if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0))
            brain.getDRecorder().record("Bonus for LI with " + leader.getName() + " : +"+df1.format(takedownBonus));

        } else if (WGETAdiffs[leaderPN] < 0) {
          final float takedownBonus = (100.0f / game.maxPlayers) * leaderAdversarialFactor;
          bonus += takedownBonus;
          D.ebugPrintlnINFO("^^^^ added takedown bonus for leader " + leaderPN + " : +" + takedownBonus);
          if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0))
            brain.getDRecorder().record("Bonus for LI with " + leader.getName() + " : +"+df1.format(takedownBonus));
        }
    }
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("WGETA bonus = "+df1.format(bonus));
    }

    return bonus;
  }


  /**
   * Calc dev card score bonus for {@link #SMART_STRATEGY} based on improvements to Win Game ETA (WGETA)
   * from buying knights or +1 VP cards, weighted by their distribution, tunable {@link #devCardMultiplier},
   * and effects on the leading opponent players' WGETAs.
   *<P>
   * Assumes {@link SOCPlayerTracker#getWinGameETA()} is accurate at time of call.
   * Calls {@link SOCPlayerTracker#updateWinGameETAs(SOCPlayerTracker[])} after temporarily adding
   * a knight or +1VP card, but doesn't call it after cleaning up from the temporary add,
   * so {@link SOCPlayerTracker#getWinGameETA()} will be inaccurate afterwards.
   *
   * @param cardETA  estimated time to buy a card
   * @param leadersCurrentWGETA  the leading player's estimated time to win the game
   */
  public SOCPossibleCard getDevCardScore(final int cardETA, final int leadersCurrentWGETA)
  {
    float devCardScore = 0;
    D.ebugPrintlnINFO("$$$ devCardScore = +"+devCardScore);
    D.ebugPrintlnINFO("--- before [start] ---");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

    // TODO refactor? This section is like a copy of calcWGETABonus, with something added in the middle

    int WGETAdiffs[] = new int[game.maxPlayers];
    int originalWGETAs[] = new int[game.maxPlayers];
    int bestWGETA = 1000;
    Vector<SOCPlayerTracker> leaders = new Vector<SOCPlayerTracker>();
    for (final SOCPlayerTracker tracker : playerTrackers)
    {
      if (tracker == null)
        continue;

      final int pn = tracker.getPlayer().getPlayerNumber();
      originalWGETAs[pn] = tracker.getWinGameETA();
      WGETAdiffs[pn] = tracker.getWinGameETA();
      D.ebugPrintlnINFO("$$$$ win game ETA for player " + pn + " = " + tracker.getWinGameETA());

      if (tracker.getWinGameETA() < bestWGETA)
      {
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
    D.ebugPrintlnINFO("--- before [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()+1);
    ourPlayerData.getGame().updateLargestArmy();
    D.ebugPrintlnINFO("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    float bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

    //
    //  adjust for knight card distribution
    //
    D.ebugPrintlnINFO("^^^^ raw bonus = "+bonus);

    bonus *= 0.58f;
    D.ebugPrintlnINFO("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.58 = "+df1.format(bonus));
    }

    D.ebugPrintlnINFO("^^^^ bonus for +1 knight = "+bonus);
    devCardScore += bonus;

    D.ebugPrintlnINFO("--- after [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()-1);
    ourPlayerData.getGame().restoreLargestArmyState();
    D.ebugPrintlnINFO("--- cleanup done ---");

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Estimating vp card value ...");
    }

    //
    // see what a vp card does to our win game eta
    //
    D.ebugPrintlnINFO("--- before [start] ---");
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().suspend();
    }
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().resume();
    }
    D.ebugPrintlnINFO("--- before [end] ---");
    ourPlayerData.getInventory().addDevCard(1, SOCInventory.NEW, SOCDevCardConstants.CAP);  // any +1VP dev card
    D.ebugPrintlnINFO("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);

    D.ebugPrintlnINFO("^^^^ our current bonus = "+bonus);

    //
    //  adjust for +1 vp card distribution
    //
    bonus *= 0.21f;
    D.ebugPrintlnINFO("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.21 = "+df1.format(bonus));
    }

    D.ebugPrintlnINFO("$$$ win game ETA bonus for +1 vp: "+bonus);
    devCardScore += bonus;

    D.ebugPrintlnINFO("--- after [end] ---");
    ourPlayerData.getInventory().removeDevCard(SOCInventory.NEW, SOCDevCardConstants.CAP);
    D.ebugPrintlnINFO("--- cleanup done ---");

    //
    // add misc bonus
    //
    devCardScore += devCardMultiplier;
    D.ebugPrintlnINFO("^^^^ misc bonus = "+devCardMultiplier);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Misc bonus = "+df1.format(devCardMultiplier));
    }

    float score = getETABonus(cardETA, leadersCurrentWGETA, devCardScore);

    D.ebugPrintlnINFO("$$$$$ devCardScore = "+devCardScore);
    D.ebugPrintlnINFO("$$$$$ devCardETA = "+cardETA);
    D.ebugPrintlnINFO("$$$$$ final score = "+score);

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
   * Calc the weighted ETA bonus for a move, adjusting {@code bonus} for {@code eta} and our {@code etaBonusFactor}.
   *
   * @param eta  the current building ETA for a building type.
   *          From {@link SOCBuildingSpeedEstimate#getEstimatesFromNowFast(SOCResourceSet, boolean[])}.
   * @param leadWGETA  the WGETA (Win Game ETA) of the leader
   * @param bonus  Base WGETA bonus, before weight adjustment.
   *          From {@link #calcWGETABonus(SOCPlayerTracker[], SOCPlayerTracker[])},
   *          {@link #calcWGETABonusAux(int[], SOCPlayerTracker[], Vector)}, etc.
   * @return the weighted ETA bonus
   */
  float getETABonus(final int eta, final int leadWGETA, final float bonus)
  {
    D.ebugPrintlnINFO("**** getETABonus ****");
    //return Math.round(etaBonusFactor * ((100f * ((float)(maxGameLength - leadWGETA - eta) / (float)maxGameLength)) * (1.0f - ((float)leadWGETA / (float)maxGameLength))));

    if (D.ebugOn) {
      D.ebugPrintlnINFO("etaBonusFactor = "+etaBonusFactor);
      D.ebugPrintlnINFO("etaBonusFactor * 100.0 = "+(etaBonusFactor * 100.0f));
      D.ebugPrintlnINFO("eta = "+eta);
      D.ebugPrintlnINFO("maxETA = "+maxETA);
      D.ebugPrintlnINFO("eta / maxETA = "+((float)eta / (float)maxETA));
      D.ebugPrintlnINFO("1.0 - ((float)eta / (float)maxETA) = "+(1.0f - ((float)eta / (float)maxETA)));
      D.ebugPrintlnINFO("leadWGETA = "+leadWGETA);
      D.ebugPrintlnINFO("maxGameLength = "+maxGameLength);
      D.ebugPrintlnINFO("1.0 - ((float)leadWGETA / (float)maxGameLength) = "+(1.0f - ((float)leadWGETA / (float)maxGameLength)));
    }


    //return etaBonusFactor * 100.0f * ((1.0f - ((float)eta / (float)maxETA)) * (1.0f - ((float)leadWGETA / (float)maxGameLength)));

    return (bonus / (float)Math.pow((1+etaBonusFactor), eta));

    //return (bonus * (float)Math.pow(etaBonusFactor, ((float)(eta*eta*eta)/(float)1000.0)));
  }

  /**
   * Should the player play a knight for the purpose of working towards largest army?
   * If we already have largest army, should we now defend it if another player is close to taking it from us?
   * Called during game state {@link SOCGame#PLAY1} when we have at least 1 {@link SOCDevCardConstants#KNIGHT}
   * available to play, and haven't already played a dev card this turn.
   * @return  true if knight should be played now, not kept for when it's needed later
   * @since 2.5.00
   */
  public boolean shouldPlayKnightForLA()
  {
      final SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
      final boolean canGrowArmy;

      if ((laPlayer == null) || (laPlayer.getPlayerNumber() != ourPlayerNumber))
      {
          final int larmySize;

          if (laPlayer == null)
              larmySize = 3;
          else
              larmySize = laPlayer.getNumKnights() + 1;

          canGrowArmy =
              ((ourPlayerData.getNumKnights()
                + ourPlayerData.getInventory().getAmount(SOCDevCardConstants.KNIGHT))
                >= larmySize);

      } else {
          canGrowArmy = false;  // we already have largest army

          // TODO Should we defend it if another player is close to taking it from us?
      }

      return canGrowArmy;
  }

  /**
   * Get the resources we want to request when we play a discovery dev card.
   * Third-party bots might sometimes want to change the contents of this set.
   *
   * @return This DM's resource set to request for a Discovery dev card;
   *     never null, but may be empty
   * @see #chooseFreeResources(SOCResourceSet, int, boolean)
   * @see #pickFreeResources(int)
   * @since 2.5.00
   */
  public SOCResourceSet getResourceChoices()
  {
      return resourceChoices;
  }

  /**
   * Respond to server's request to pick resources to gain from the Gold Hex.
   * Use {@link #buildingPlan} or, if that's empty (like during initial placement),
   * pick what's rare from {@link OpeningBuildStrategy#estimateResourceRarity()}.
   *<P>
   * Caller may want to check if {@link #buildingPlan} is empty, and
   * call {@link SOCRobotBrain#planBuilding()} if so, before calling this method.
   *<P>
   * Before v2.5.00, this method was in {@code SOCRobotBrain}.
   *
   * @param numChoose  Number of resources to pick
   * @return  the chosen resource picks; also sets {@link #getResourceChoices()} to the returned set
   * @since 2.0.00
   */
  protected SOCResourceSet pickFreeResources(int numChoose)
  {
      SOCResourceSet targetResources;

      if (! buildingPlan.isEmpty())
      {
          final SOCPossiblePiece targetPiece = buildingPlan.peek();
          targetResources = targetPiece.getResourcesToBuild();  // may be null
          chooseFreeResourcesIfNeeded(targetResources, numChoose, true);
      } else {
          // Pick based on board dice-roll rarities.
          // TODO: After initial placement, consider based on our
          // number probabilities based on settlements/cities placed.
          //  (BSE.getRollsForResourcesSorted)

          resourceChoices.clear();
          final int[] resourceEstimates = openingBuildStrategy.estimateResourceRarity();
          int numEach = 0;  // in case we pick 5, keep going for 6-10
          while (numChoose > 0)
          {
              int res = -1, pct = Integer.MAX_VALUE;
              for (int i = SOCBoard.CLAY_HEX; i <= SOCBoard.WOOD_HEX; ++i)
              {
                  if ((resourceEstimates[i] < pct) && (resourceChoices.getAmount(i) < numEach))
                  {
                      res = i;
                      pct = resourceEstimates[i];
                  }
              }
              if (res != -1)
              {
                  resourceChoices.add(1, res);
                  --numChoose;
              } else {
                  ++numEach;  // has chosen all 5 by now
              }
          }
      }

      return resourceChoices;
  }

  /**
   * Choose the resources we need most, for playing a Discovery development card
   * or when a Gold Hex number is rolled.
   * Find the most needed resource by looking at
   * which of the resources we still need takes the
   * longest to acquire, then add to {@link #resourceChoices}.
   * Looks at our player's current resources.
   *<P>
   * Before v2.5.00, this method was in {@code SOCRobotBrain}.
   *
   * @param targetResources  Resources needed to build our next planned piece,
   *             from {@link SOCPossiblePiece#getResourcesToBuild()}
   *             for {@link #buildingPlan}.peek()
   * @param numChoose  Number of resources to choose
   * @param clearResChoices  If true, clear {@link #resourceChoices} before choosing what to add to it;
   *             set false if calling several times to iteratively build up a big choice.
   * @return  True if we could choose <tt>numChoose</tt> resources towards <tt>targetResources</tt>,
   *             false if we could fully satisfy <tt>targetResources</tt>
   *             from our current resources + less than <tt>numChoose</tt> more.
   *             Examine {@link #resourceChoices}{@link SOCResourceSet#getTotal() .getTotal()}
   *             to see how many were chosen.
   * @see #chooseFreeResourcesIfNeeded(SOCResourceSet, int, boolean)
   * @see #getResourceChoices()
   */
  protected boolean chooseFreeResources
      (final SOCResourceSet targetResources, final int numChoose, final boolean clearResChoices)
  {
      /**
       * clear our resource choices
       */
      if (clearResChoices)
          resourceChoices.clear();

      /**
       * find the most needed resource by looking at
       * which of the resources we still need takes the
       * longest to acquire
       */
      SOCResourceSet rsCopy = ourPlayerData.getResources().copy();
      SOCBuildingSpeedEstimate estimate = getEstimator(ourPlayerData.getNumbers());
      int[] rollsPerResource = estimate.getRollsPerResource();

      for (int resourceCount = 0; resourceCount < numChoose; resourceCount++)
      {
          int mostNeededResource = -1;

          for (int resource = SOCResourceConstants.CLAY;
                  resource <= SOCResourceConstants.WOOD; resource++)
          {
              if (rsCopy.getAmount(resource) < targetResources.getAmount(resource))
              {
                  if (mostNeededResource < 0)
                  {
                      mostNeededResource = resource;
                  }
                  else
                  {
                      if (rollsPerResource[resource] > rollsPerResource[mostNeededResource])
                      {
                          mostNeededResource = resource;
                      }
                  }
              }
          }

          if (mostNeededResource == -1)
              return false;  // <--- Early return: couldn't choose enough ---

          resourceChoices.add(1, mostNeededResource);
          rsCopy.add(1, mostNeededResource);
      }

      return true;
  }

  /**
   * Do we need to acquire at least <tt>numChoose</tt> resources to build our next piece?
   * Choose the resources we need most; used when we want to play a discovery development card
   * or when a Gold Hex number is rolled.
   * If returns true, has called {@link #chooseFreeResources(SOCResourceSet, int, boolean)}
   * and has set {@link #resourceChoices}.
   *<P>
   * Before v2.5.00, this method was in {@code SOCRobotBrain}.
   *
   * @param targetResources  Resources needed to build our next planned piece,
   *             from {@link SOCPossiblePiece#getResourcesToBuild()}
   *             for {@link #buildingPlan}.
   *             If {@code null}, returns false (no more resources required).
   * @param numChoose  Number of resources to choose
   * @param chooseIfNotNeeded  Even if we find we don't need them, choose anyway;
   *             set true for Gold Hex choice, false for Discovery card pick.
   * @return  true if we need <tt>numChoose</tt> resources
   * @since 2.0.00
   */
  protected boolean chooseFreeResourcesIfNeeded
      (SOCResourceSet targetResources, final int numChoose, final boolean chooseIfNotNeeded)
  {
      if (targetResources == null)
          return false;

      if (chooseIfNotNeeded)
          resourceChoices.clear();

      final SOCResourceSet ourResources = ourPlayerData.getResources();
      int numMore = numChoose;

      // Used only if chooseIfNotNeeded:
      int buildingItem = 0;  // for ourBuildingPlan.peek
      boolean stackTopIs0 = false;

      /**
       * If ! chooseIfNotNeeded, this loop
       * body will only execute once.
       */
      do
      {
          int numNeededResources = 0;
          if (targetResources == null)  // can be null from SOCPossiblePickSpecialItem.cost
              break;

          for (int resource = SOCResourceConstants.CLAY;
                  resource <= SOCResourceConstants.WOOD;
                  resource++)
          {
              final int diff = targetResources.getAmount(resource) - ourResources.getAmount(resource);
              if (diff > 0)
                  numNeededResources += diff;
          }

          if ((numNeededResources == numMore)  // TODO >= numMore ? (could change details of current bot behavior)
              || (chooseIfNotNeeded && (numNeededResources > numMore)))
          {
              chooseFreeResources(targetResources, numMore, ! chooseIfNotNeeded);
              return true;
          }

          if (! chooseIfNotNeeded)
              return false;

          // Assert: numNeededResources < numMore.
          // Pick the first numNeeded, then loop to pick additional ones.
          chooseFreeResources(targetResources, numMore, false);
          numMore = numChoose - resourceChoices.getTotal();

          if (numMore > 0)
          {
              // Pick a new target from building plan, if we can.
              // Otherwise, choose our least-frequently-rolled resources.

              ++buildingItem;
              final int bpSize = buildingPlan.size();
              if (bpSize > buildingItem)
              {
                  if (buildingItem == 1)
                  {
                      // validate direction of stack growth for buildingPlan
                      stackTopIs0 = (0 == buildingPlan.indexOf(buildingPlan.getFirstPiece()));
                  }

                  int i = (stackTopIs0) ? buildingItem : (bpSize - buildingItem) - 1;

                  SOCPossiblePiece targetPiece = buildingPlan.elementAt(i);
                  targetResources = targetPiece.getResourcesToBuild();  // may be null

                  // Will continue at top of loop to add
                  // targetResources to resourceChoices.

              } else {

                  // This will be the last iteration.
                  // Choose based on our least-frequent dice rolls.

                  final int[] resourceOrder =
                      bseFactory.getRollsForResourcesSorted(ourPlayerData);

                  int curRsrc = 0;
                  while (numMore > 0)
                  {
                      resourceChoices.add(1, resourceOrder[curRsrc]);
                      --numMore;
                      ++curRsrc;
                      if (curRsrc == resourceOrder.length)
                          curRsrc = 0;
                  }

                  // now, numMore == 0, so do-while loop will exit at bottom.
              }
          }

      } while (numMore > 0);

      return true;
  }

  /**
   * Estimator factory method for when a player's dice numbers are known.
   * Calls {@link SOCRobotBrain#getEstimator(SOCPlayerNumbers)} if DM has non-null {@link #brain} field.
   * If brain not available (simulation situations, etc), constructs a new Estimate with
   * {@link #getEstimatorFactory()}.{@link SOCBuildingSpeedEstimateFactory#getEstimator(SOCPlayerNumbers) getEstimator(numbers)}.
   *<P>
   * This factory may be overridden by third-party bot DMs. However, it's
   * also not unreasonable to expect that simulation of opponent planning
   * would involve a less exact estimation than considering our own plans.
   *
   * @param numbers Player's dice numbers to start from,
   *     in same format passed into {@link SOCBuildingSpeedEstimate#SOCBuildingSpeedEstimate(SOCPlayerNumbers)}
   * @return  Estimator based on {@code numbers}
   * @see #getEstimator()
   * @since 2.5.00
   */
  protected SOCBuildingSpeedEstimate getEstimator(SOCPlayerNumbers numbers)
  {
      if (brain != null)
          return brain.getEstimator(numbers);
      else
          return bseFactory.getEstimator(numbers);
  }

  /**
   * Estimator factory method for when a player's dice numbers are unknown or don't matter yet.
   * Calls {@link SOCRobotBrain#getEstimator()} if DM has non-null {@link #brain} field.
   * If brain not available (simulation situations, etc), constructs a new Estimate with
   * {@link #getEstimatorFactory()}.{@link SOCBuildingSpeedEstimateFactory#getEstimator() getEstimator()}.
   *<P>
   * This factory may be overridden by third-party bot DMs. However, it's
   * also not unreasonable to expect that simulation of opponent planning
   * would involve a less exact estimation than considering our own plans.
   *
   * @return  Estimator which doesn't consider player's dice numbers yet;
   *     see {@link SOCBuildingSpeedEstimate#SOCBuildingSpeedEstimate()} javadoc
   * @see #getEstimator(SOCPlayerNumbers)
   * @since 2.5.00
   */
  protected SOCBuildingSpeedEstimate getEstimator()
  {
      if (brain != null)
          return brain.getEstimator();
      else
          return bseFactory.getEstimator();
  }

  /**
   * Get this decision maker's {@link SOCBuildingSpeedEstimate} factory.
   * Is set in constructor, from our brain or otherwise.
   *
   * @return This decision maker's factory
   * @see #getEstimator(SOCPlayerNumbers)
   * @since 2.5.00
   */
  public SOCBuildingSpeedEstimateFactory getEstimatorFactory()
  {
      return bseFactory;
  }

}


