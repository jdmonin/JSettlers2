package soc.robot;

import java.text.DecimalFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import org.apache.log4j.Logger;

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
 *
 * @author Robert S. Thomas
 */

public class SOCRobotDM {

  protected static final DecimalFormat df1 = new DecimalFormat("###0.00");

  protected int maxGameLength = 300;
	
  protected int maxETA = 99;

  protected float etaBonusFactor = (float)0.8;
	
  protected float adversarialFactor = (float)1.5;

  protected float leaderAdversarialFactor = (float)3.0;

  protected float devCardMultiplier = (float)2.0;

  protected float threatMultiplier = (float)1.1;

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
  protected HashMap playerTrackers;
  protected SOCPlayerTracker ourPlayerTracker;
  protected SOCPlayer ourPlayerData;
  protected Stack buildingPlan;
  protected SOCGame game;
  protected Vector threatenedRoads;
  protected Vector goodRoads;
  protected SOCPossibleRoad favoriteRoad;
  protected Vector threatenedSettlements;
  protected Vector goodSettlements;
  protected SOCPossibleSettlement favoriteSettlement;    
  protected SOCPossibleCity favoriteCity;
  protected SOCPossibleCard possibleCard;

  /** debug logging */
  private transient Logger log = Logger.getLogger(this.getClass().getName());

  /**
   * constructor
   *
   * @param br  the robot brain
   */
  public SOCRobotDM(SOCRobotBrain br) {
    brain = br;
    playerTrackers = brain.getPlayerTrackers();
    ourPlayerTracker = brain.getOurPlayerTracker();
    ourPlayerData = brain.getOurPlayerData();
    buildingPlan = brain.getBuildingPlan();
    game = brain.getGame();

    threatenedRoads = new Vector();
    goodRoads = new Vector();
    threatenedSettlements = new Vector();
    goodSettlements = new Vector();
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
   * constructor
   * 
   * this is if you don't want to use a brain
   *
   * @param params  the robot parameters
   * @param pt   the player trackers
   * @param opt  our player tracker
   * @param opd  our player data
   * @param bp   our building plan
   */
  public SOCRobotDM(SOCRobotParameters params,
		    HashMap pt,
		    SOCPlayerTracker opt,
		    SOCPlayer opd,
		    Stack bp) {
    brain = null;
    playerTrackers = pt;
    ourPlayerTracker = opt;
    ourPlayerData = opd;
    buildingPlan = bp;
    game = ourPlayerData.getGame();

    maxGameLength = params.getMaxGameLength();
    maxETA = params.getMaxETA();
    etaBonusFactor = params.getETABonusFactor();
    adversarialFactor = params.getAdversarialFactor();
    leaderAdversarialFactor = params.getLeaderAdversarialFactor();
    devCardMultiplier = params.getDevCardMultiplier();
    threatMultiplier = params.getThreatMultiplier();

    threatenedRoads = new Vector();
    goodRoads = new Vector();
    threatenedSettlements = new Vector();
    goodSettlements = new Vector();
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
   * @return favorite road
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
   * make some building plans
   *
   * @param strategy  an integer that determines which strategy is used (SMART_STRATEGY | FAST_STRATEGY)
   */
  public void planStuff(int strategy) {
      //long startTime = System.currentTimeMillis();
    log.debug("PLANSTUFF");
	  
    SOCBuildingSpeedEstimate currentBSE = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
    int currentBuildingETAs[] = currentBSE.getEstimatesFromNowFast(ourPlayerData.getResources(), ourPlayerData.getPortFlags());

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
    if ((brain != null) && (brain.getDRecorder().isOn())) {
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
    Iterator trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext()) {
      SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
      int wgeta = tracker.getWinGameETA();
      if (wgeta < leadersCurrentWGETA) {
	leadersCurrentWGETA = wgeta;
      }
    }

    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);

    ///
    /// reset scores and biggest threats for everything
    ///
    Iterator posPiecesIter;
    SOCPossiblePiece posPiece;
    posPiecesIter = ourPlayerTracker.getPossibleCities().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = (SOCPossiblePiece)posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = (SOCPossiblePiece)posPiecesIter.next();
      posPiece.resetScore();
      posPiece.clearBiggestThreats();
    }
    posPiecesIter = ourPlayerTracker.getPossibleRoads().values().iterator();
    while (posPiecesIter.hasNext()) {
      posPiece = (SOCPossiblePiece)posPiecesIter.next();
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
	!ourPlayerData.hasPlayedDevCard() &&
	ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
	ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
      SOCPossibleRoad secondFavoriteRoad = null;
      Enumeration threatenedRoadEnum;
      Enumeration goodRoadEnum;
      log.debug("*** making a plan for road building");

      ///
      /// we need to pick two roads
      ///
      if (favoriteRoad != null) {
	//
	//  pretend to put the favorite road down, 
	//  and then score the new pos roads
	//
	SOCRoad tmpRoad = new SOCRoad(ourPlayerData, favoriteRoad.getCoordinates());
	
	HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);
				
	SOCPlayerTracker ourPlayerTrackerCopy = (SOCPlayerTracker)trackersCopy.get(new Integer(ourPlayerData.getPlayerNumber()));

	int ourCurrentWGETACopy = ourPlayerTrackerCopy.getWinGameETA();
	log.debug("ourCurrentWGETACopy = "+ourCurrentWGETACopy);
				
	int leadersCurrentWGETACopy = ourCurrentWGETACopy;
	Iterator trackersCopyIter = trackersCopy.values().iterator();
	while (trackersCopyIter.hasNext()) {
	  SOCPlayerTracker tracker = (SOCPlayerTracker)trackersCopyIter.next();
	  int wgeta = tracker.getWinGameETA();
	  if (wgeta < leadersCurrentWGETACopy) {
	    leadersCurrentWGETACopy = wgeta;
	  }
	}

	Enumeration newPosEnum = favoriteRoad.getNewPossibilities().elements();
	while (newPosEnum.hasMoreElements()) {
	  SOCPossiblePiece newPos = (SOCPossiblePiece)newPosEnum.nextElement();
	  if (newPos.getType() == SOCPossiblePiece.ROAD) {
	    newPos.resetScore();
	    // float wgetaScore = getWinGameETABonusForRoad((SOCPossibleRoad)newPos, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETACopy, trackersCopy);


	    log.debug("$$$ new pos road at "+Integer.toHexString(newPos.getCoordinates())+" has a score of "+newPos.getScore());

	    if (favoriteRoad.getCoordinates() != newPos.getCoordinates()) {
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
	while (threatenedRoadEnum.hasMoreElements()) {
	  SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
	  log.debug("$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));
					
	  //
	  // see how building this piece impacts our winETA
	  //
	  threatenedRoad.resetScore();
	  // float wgetaScore = getWinGameETABonusForRoad(threatenedRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

	  log.debug("$$$  final score = "+threatenedRoad.getScore());
					
	  if (favoriteRoad.getCoordinates() != threatenedRoad.getCoordinates()) {
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
	while (goodRoadEnum.hasMoreElements()) {
	  SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
	  log.debug("$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));
	  //
	  // see how building this piece impacts our winETA
	  //
	  goodRoad.resetScore();
	  // float wgetaScore = getWinGameETABonusForRoad(goodRoad, currentBuildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);

	  log.debug("$$$  final score = "+goodRoad.getScore());

	  if (favoriteRoad.getCoordinates() != goodRoad.getCoordinates()) {
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

	if (!buildingPlan.empty()) {
	  SOCPossiblePiece planPeek = (SOCPossiblePiece)buildingPlan.peek();
	  if ((planPeek == null) ||
	      (planPeek.getType() != SOCPlayingPiece.ROAD)) {
	    if (secondFavoriteRoad != null) {
	      log.debug("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
	      log.debug("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
	      log.debug("$ PUSHING "+secondFavoriteRoad);
	      buildingPlan.push(secondFavoriteRoad);
	      log.debug("$ PUSHING "+favoriteRoad);
	      buildingPlan.push(favoriteRoad);
	    }
	  } else if (secondFavoriteRoad != null) {
	    SOCPossiblePiece tmp = (SOCPossiblePiece)buildingPlan.pop();
	    log.debug("$ POPPED OFF");
	    log.debug("### SECOND FAVORITE ROAD IS AT "+Integer.toHexString(secondFavoriteRoad.getCoordinates()));
	    log.debug("###   WITH A SCORE OF "+secondFavoriteRoad.getScore());
	    log.debug("$ PUSHING "+secondFavoriteRoad);
	    buildingPlan.push(secondFavoriteRoad);
	    log.debug("$ PUSHING "+tmp);
	    buildingPlan.push(tmp);
	  }
	}     
      } 
    } 
    //long endTime = System.currentTimeMillis();
    //System.out.println("plan time: "+(endTime-startTime));
  }
  
  /**
   * dumbFastGameStrategy
   * uses rules to determine what to build next
   *
   * @param buildingETAs  the etas for building something
   */
  protected void dumbFastGameStrategy(int[] buildingETAs) {
    log.debug("***** dumbFastGameStrategy *****");
    int bestETA = 500;
    SOCBuildingSpeedEstimate ourBSE = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());

    if (ourPlayerData.getTotalVP() < 5) {
      //
      // less than 5 points, don't consider LR or LA
      //

      //
      // score possible cities
      //
      if (ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0) {
	Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
	while (posCitiesIter.hasNext()) {
	  SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
	  log.debug("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
	  log.debug("Speedup = "+posCity.getSpeedupTotal());
	  log.debug("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	  if ((brain != null) && (brain.getDRecorder().isOn())) {
	    brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
	    brain.getDRecorder().record("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
	    brain.getDRecorder().record("Speedup = "+posCity.getSpeedupTotal());
	    brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	    brain.getDRecorder().stopRecording();
	  }
	  if ((favoriteCity == null) ||
	      (posCity.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
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
      Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
      while (posSetsIter.hasNext()) {
	SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
	  brain.getDRecorder().record("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
	  brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
	  brain.getDRecorder().record("ETA = "+posSet.getETA());
	  Stack roadPath = posSet.getRoadPath();
	  if (roadPath!= null) {
	    brain.getDRecorder().record("Path:");
	    Iterator rpIter = roadPath.iterator();
	    while (rpIter.hasNext()) {
	      SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
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
		(posSet.getSpeedupTotal() > favoriteCity.getSpeedupTotal())) {
	      favoriteSettlement = posSet;
	    }
	  } else {
	    if (posSet.getSpeedupTotal() > favoriteSettlement.getSpeedupTotal()) {
	      favoriteSettlement = posSet;
	    }
	  }
	}
      }
      
      if (favoriteSettlement != null) {
	//
	// we want to build a settlement
	//
	log.debug("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
	buildingPlan.push(favoriteSettlement);
	if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
	  //
	  // we need to build roads first
	  //	  
	  Stack roadPath = favoriteSettlement.getRoadPath();
	  while (!roadPath.empty()) {
	    buildingPlan.push(roadPath.pop());
	  }
	}
      } else if (favoriteCity != null) {
	//
	// we want to build a city
	//
	log.debug("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
	buildingPlan.push(favoriteCity);
      } else {
	//
	// we can't build a settlement or city
	//
	if (game.getNumDevCards() > 0) {
	  //
	  // buy a card if there are any left
	  //
	  log.debug("Buy a card");
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
      log.debug("Calculating Largest Army ETA");
      int laETA = 500;
      int laSize = 0;
      SOCPlayer laPlayer = game.getPlayerWithLargestArmy();
      if (laPlayer == null) {
	///
	/// no one has largest army
	///
	laSize = 3;
      } else if (laPlayer.getPlayerNumber() == ourPlayerData.getPlayerNumber()) {
	///
	/// we have largest army
	///
	log.debug("We have largest army");
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
	  < laSize) {
	knightsToBuy = laSize - (ourPlayerData.getNumKnights() +
				 ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.KNIGHT));
      }
      log.debug("knightsToBuy = "+knightsToBuy);
      if (ourPlayerData.getGame().getNumDevCards() >= knightsToBuy) {      
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
      if (laETA < bestETA) {
	bestETA = laETA;
	choice = LA_CHOICE;
      }
      log.debug("laETA = "+laETA);
      
      //
      // consider Longest Road
      //
      log.debug("Calculating Longest Road ETA");
      int lrETA = 500;
      Stack bestLRPath = null;
      int lrLength;
      SOCPlayer lrPlayer = game.getPlayerWithLongestRoad();
      if ((lrPlayer != null) && 
	  (lrPlayer.getPlayerNumber() == ourPlayerData.getPlayerNumber())) {
	///
	/// we have longest road
	///
	log.debug("We have longest road");
      } else {
	if (lrPlayer == null) {
	  ///
	  /// no one has longest road
	  ///
	  lrLength = Math.max(4, ourPlayerData.getLongestRoadLength());
	} else {
	  lrLength = lrPlayer.getLongestRoadLength();
	}
	Iterator lrPathsIter = ourPlayerData.getLRPaths().iterator();
	int depth;
	while (lrPathsIter.hasNext()) {
	  Stack path;
	  SOCLRPathData pathData = (SOCLRPathData)lrPathsIter.next();
	  depth = Math.min(((lrLength + 1) - pathData.getLength()), ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD));
	  path = recalcLongestRoadETAAux(pathData.getBeginning(), pathData.getLength(), lrLength, depth);
	  if ((path != null) &&
	      ((bestLRPath == null) ||
	       (path.size() < bestLRPath.size()))) {
	    bestLRPath = path;
	  }
	  path = recalcLongestRoadETAAux(pathData.getEnd(), pathData.getLength(), lrLength, depth);
	  if ((path != null) &&
	      ((bestLRPath == null) ||
	       (path.size() < bestLRPath.size()))) {
	    bestLRPath = path;
	  }
	}
	if (bestLRPath != null) {
	  //
	  // calculate LR eta
	  //
	  log.debug("Number of roads: "+bestLRPath.size());
	  SOCResourceSet targetResources = new SOCResourceSet();
	  for (int i = 0; i < bestLRPath.size(); i++) {
	    targetResources.add(SOCGame.ROAD_SET);
	  }
	  try {
	    SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
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
      log.debug("lrETA = "+lrETA);
      
      //
      // consider possible cities
      //
      if ((ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0) &&
	  (buildingETAs[SOCBuildingSpeedEstimate.CITY] <= bestETA)) {
	Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
	while (posCitiesIter.hasNext()) {
	  SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
	  if ((brain != null) && (brain.getDRecorder().isOn())) {
	    brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
	    brain.getDRecorder().record("Estimate speedup of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
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
      if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
	scoreSettlementsForDumb(buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT], ourBSE);
	Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
	while (posSetsIter.hasNext()) {
	  SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
	  if ((brain != null) && (brain.getDRecorder().isOn())) {
	    brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
	    brain.getDRecorder().record("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
	    brain.getDRecorder().record("Speedup = "+posSet.getSpeedupTotal());
	    brain.getDRecorder().record("ETA = "+posSet.getETA());
	    Stack roadPath = posSet.getRoadPath();
	    if (roadPath!= null) {
	      brain.getDRecorder().record("Path:");
	      Iterator rpIter = roadPath.iterator();
	      while (rpIter.hasNext()) {
		SOCPossibleRoad posRoad = (SOCPossibleRoad)rpIter.next();
		brain.getDRecorder().record("Road at "+game.getBoard().edgeCoordToString(posRoad.getCoordinates()));
	      }
	    }
	    brain.getDRecorder().stopRecording();
	  }
	  if ((posSet.getRoadPath() == null) ||
	      (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= posSet.getRoadPath().size())) {
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
      switch (choice) {
      case LA_CHOICE:
	log.debug("Picked LA");
	for (int i = 0; i < knightsToBuy; i++) {
	  SOCPossibleCard posCard = new SOCPossibleCard(ourPlayerData, 1);
	  buildingPlan.push(posCard);
	}
	break;
	
      case LR_CHOICE:
	log.debug("Picked LR");
	while (!bestLRPath.empty()) {
	  SOCPossibleRoad pr = (SOCPossibleRoad)bestLRPath.pop();
	  log.debug("LR road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
	  buildingPlan.push(pr);
	}
	break;

      case CITY_CHOICE:
	log.debug("Picked favorite city at "+game.getBoard().nodeCoordToString(favoriteCity.getCoordinates()));
	buildingPlan.push(favoriteCity);
	break;
	
      case SETTLEMENT_CHOICE:
	log.debug("Picked favorite settlement at "+game.getBoard().nodeCoordToString(favoriteSettlement.getCoordinates()));
	buildingPlan.push(favoriteSettlement);
	if (!favoriteSettlement.getNecessaryRoads().isEmpty()) {
	  //
	  // we need to build roads first
	  //	  
	  Stack roadPath = favoriteSettlement.getRoadPath();
	  while (!roadPath.empty()) {
	    SOCPossibleRoad pr = (SOCPossibleRoad)roadPath.pop();
	    log.debug("Nec road at "+game.getBoard().edgeCoordToString(pr.getCoordinates()));
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
  protected void scoreSettlementsForDumb(int settlementETA, SOCBuildingSpeedEstimate ourBSE) {
    log.debug("-- scoreSettlementsForDumb --");
    Queue queue = new Queue();
    Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext()) {
      SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
      log.debug("Estimate speedup of stlmt at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
      log.debug("***    speedup total = "+posSet.getSpeedupTotal());
	
      ///
      /// find the shortest path to this settlement
      ///
      Vector necRoadVec = posSet.getNecessaryRoads();
      if (!necRoadVec.isEmpty()) {
	queue.clear();
	Iterator necRoadsIter = necRoadVec.iterator();
	while (necRoadsIter.hasNext()) {
	  SOCPossibleRoad necRoad = (SOCPossibleRoad)necRoadsIter.next();
	  log.debug("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad.getCoordinates()));
	  queue.put(new Pair(necRoad, null));
	}
	//
	// Do a BFS of the necessary road paths looking for the shortest one.
	//
	while (!queue.empty()) {
	  Pair dataPair = (Pair)queue.get();
	  SOCPossibleRoad curRoad = (SOCPossibleRoad)dataPair.getA();
	  log.debug("-- current road at "+game.getBoard().edgeCoordToString(curRoad.getCoordinates()));
	  Vector necRoads = curRoad.getNecessaryRoads();
	  if (necRoads.isEmpty()) {
	    //
	    // we have a path 
	    //
	    log.debug("Found a path!");
	    Stack path = new Stack();
	    path.push(curRoad);
	    Pair curPair = (Pair)dataPair.getB();
	    log.debug("curPair = "+curPair);
	    while (curPair != null) {
	      path.push(curPair.getA());
	      curPair = (Pair)curPair.getB();
	    }
	    posSet.setRoadPath(path);
	    queue.clear();
	    log.debug("Done setting path.");
	  } else {
	    necRoadsIter = necRoads.iterator();
	    while (necRoadsIter.hasNext()) {
	      SOCPossibleRoad necRoad2 = (SOCPossibleRoad)necRoadsIter.next();
	      log.debug("-- queuing necessary road at "+game.getBoard().edgeCoordToString(necRoad2.getCoordinates()));
	      queue.put(new Pair(necRoad2, dataPair));
	    }
	  }
	}
	log.debug("Done searching for path.");

	//
	// calculate ETA
	//
	SOCResourceSet targetResources = new SOCResourceSet();
	targetResources.add(SOCGame.SETTLEMENT_SET);
	int pathLength = 0;
	Stack path = posSet.getRoadPath();
	if (path != null) {
	  pathLength = path.size();
	}
	for (int i = 0; i < pathLength; i++) {
	  targetResources.add(SOCGame.ROAD_SET);
	}
	try {
	  SOCResSetBuildTimePair timePair = ourBSE.calculateRollsFast(ourPlayerData.getResources(), targetResources, 100, ourPlayerData.getPortFlags());
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
      log.debug("Settlement ETA = "+posSet.getETA());
    }
  }

  /**
   * Does a depth first search from the end point of the longest
   * path in a graph of nodes and returns how many roads would 
   * need to be built to take longest road.
   *
   * @param startNode     the path endpoint
   * @param pathLength    the length of that path
   * @param lrLength      length of longest road in the game
   * @param searchDepth   how many roads out to search
   *
   * @return a stack containing the path of roads with the last one on top, or null if it can't be done
   */
  private Stack recalcLongestRoadETAAux(int startNode, int pathLength, int lrLength, int searchDepth) {
    log.debug("=== recalcLongestRoadETAAux("+Integer.toHexString(startNode)+","+pathLength+","+lrLength+","+searchDepth+")");
    
    //
    // we're doing a depth first search of all possible road paths 
    //
    int longest = 0;
    int numRoads = 500;
    Pair bestPathNode = null;
    Stack pending = new Stack();
    pending.push(new Pair(new NodeLenVis(startNode, pathLength, new Vector()), null));

    while (!pending.empty()) {
      Pair dataPair = (Pair)pending.pop();
      NodeLenVis curNode = (NodeLenVis)dataPair.getA();
      log.debug("curNode = "+curNode);
      int coord = curNode.node;
      int len = curNode.len;
      Vector visited = curNode.vis;
      boolean pathEnd = false;
      
      //
      // check for road blocks 
      //
      Enumeration pEnum = game.getBoard().getPieces().elements();
      while (pEnum.hasMoreElements()) {
	SOCPlayingPiece p = (SOCPlayingPiece)pEnum.nextElement();
	if ((len > 0) &&
	    (p.getPlayer().getPlayerNumber() != ourPlayerData.getPlayerNumber()) &&
	    ((p.getType() == SOCPlayingPiece.SETTLEMENT) ||
	     (p.getType() == SOCPlayingPiece.CITY)) &&
	    (p.getCoordinates() == coord)) {
	  pathEnd = true;
	  log.debug("^^^ path end at "+Integer.toHexString(coord));
	  break;
	}
      }

      if (!pathEnd) {
	// 
	// check if we've connected to another road graph
	//
	Iterator lrPathsIter = ourPlayerData.getLRPaths().iterator();
	while (lrPathsIter.hasNext()) {
	  SOCLRPathData pathData = (SOCLRPathData)lrPathsIter.next();
	  if ((startNode != pathData.getBeginning() &&
	       startNode != pathData.getEnd()) &&
	      (coord == pathData.getBeginning() ||
	       coord == pathData.getEnd())) {
	    pathEnd = true;
	    len += pathData.getLength();
	    log.debug("connecting to another path: "+pathData);
	    log.debug("len = "+len);
	    break;
	  }      
	}
      }
      
      if (!pathEnd) {
	//
	// (len - pathLength) = how many new roads we've built
	//
	if ((len - pathLength) >= searchDepth) {
	  pathEnd = true;
	}
	log.debug("Reached search depth");
      }

      if (!pathEnd) {
	pathEnd = true;

	int j;		
	Integer edge;
	boolean match;

	j = coord - 0x11;
	edge = new Integer(j);
	match = false;
	if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) &&
	    (ourPlayerData.isLegalRoad(j))) {
	  for (Enumeration ev = visited.elements();
	       ev.hasMoreElements(); ) {
	    Integer vis = (Integer)ev.nextElement();
	    if (vis.equals(edge)) {
	      match = true;
	      break;
	    }
	  }	
	  if (!match) {
	    Vector newVis = (Vector)visited.clone();
	    newVis.addElement(edge);
	    // node coord and edge coord are the same
	    pending.push(new Pair(new NodeLenVis(j, len+1, newVis), dataPair));
	    pathEnd = false;
	  }
	}
	j = coord;
	edge = new Integer(j);
	match = false;
	if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) &&
	    (ourPlayerData.isLegalRoad(j))) {
	  for (Enumeration ev = visited.elements();
	       ev.hasMoreElements(); ) {
	    Integer vis = (Integer)ev.nextElement();
	    if (vis.equals(edge)) {
	      match = true;
	      break;
	    }
	  }	
	  if (!match) {
	    Vector newVis = (Vector)visited.clone();
	    newVis.addElement(edge);
	    // coord for node = edge + 0x11
	    j += 0x11;
	    pending.push(new Pair(new NodeLenVis(j, len+1, newVis), dataPair));
	    pathEnd = false;
	  }
	}
	j = coord - 0x01;
	edge = new Integer(j);
	match = false;
	if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) &&
	    (ourPlayerData.isLegalRoad(j))) {
	  for (Enumeration ev = visited.elements();
	       ev.hasMoreElements(); ) {
	    Integer vis = (Integer)ev.nextElement();
	    if (vis.equals(edge)) {
	      match = true;
	      break;
	    }
	  }	
	  if (!match) {
	    Vector newVis = (Vector)visited.clone();
	    newVis.addElement(edge);
	    // node coord = edge coord + 0x10
	    j += 0x10;
	    pending.push(new Pair(new NodeLenVis(j, len+1, newVis), dataPair));
	    pathEnd = false;
	  }
	}	 
	j = coord - 0x10;
	edge = new Integer(j);
	match = false;
	if ((j >= SOCBoard.MINEDGE) && (j <= SOCBoard.MAXEDGE) &&
	    (ourPlayerData.isLegalRoad(j))) {
	  for (Enumeration ev = visited.elements();
	       ev.hasMoreElements(); ) {
	    Integer vis = (Integer)ev.nextElement();
	    if (vis.equals(edge)) {
	      match = true;
	      break;
	    }
	  }	
	  if (!match) {
	    Vector newVis = (Vector)visited.clone();
	    newVis.addElement(edge);
	    // node coord = edge coord + 0x01
	    j += 0x01;
	    pending.push(new Pair(new NodeLenVis(j, len+1, newVis), dataPair));
	    pathEnd = false;
	  }
	}
      }		
      
      if (pathEnd) {
	if (len > longest) {
	  longest = len;
	  numRoads = curNode.len - pathLength;
	  bestPathNode = dataPair;
	} else if ((len == longest) &&
		   (curNode.len < numRoads)) {
	  numRoads = curNode.len - pathLength;
	  bestPathNode = dataPair;
	}
      }
    }
    if ((longest > lrLength) &&
	(bestPathNode != null)) {
      log.debug("Converting nodes to road coords.");
      //
      // return the path in a stack with the last road on top
      //
      //
      // convert pairs of node coords to road coords
      //
      Stack temp = new Stack();
      SOCPossibleRoad posRoad;
      int coordA, coordB, test;
      Pair cur, parent;
      cur = bestPathNode;
      parent = (Pair)bestPathNode.getB();
      while (parent != null) {
	coordA = ((NodeLenVis)cur.getA()).node;
	coordB = ((NodeLenVis)parent.getA()).node;
	test = coordA - coordB;
	if (test == 0x11) {
	  // it is a '\' road
	  log.debug(game.getBoard().nodeCoordToString(coordB));
	  posRoad = new SOCPossibleRoad(ourPlayerData, coordB, new Vector());
	} else if (test == -0x11) {
	  // it is a '/' road
	  log.debug(game.getBoard().nodeCoordToString(coordA));
	  posRoad = new SOCPossibleRoad(ourPlayerData, coordA, new Vector());
	} else if (test == 0x0F) {
	  // it is a '|' road for an A node
	  log.debug(game.getBoard().nodeCoordToString((coordA - 0x10)));
	  posRoad = new SOCPossibleRoad(ourPlayerData, (coordA - 0x10), new Vector());
	} else {
	  // it is a '|' road for a Y node
	  log.debug(game.getBoard().nodeCoordToString((coordA - 0x01)));
	  posRoad = new SOCPossibleRoad(ourPlayerData, (coordA - 0x01), new Vector());
	}
	temp.push(posRoad);
	cur = parent;
	parent = (Pair)parent.getB();
      }
      //
      // reverse the order of the roads so that the last one is on top
      //
      Stack path = new Stack();
      while (!temp.empty()) {
	path.push(temp.pop());
      }
      return path;
    } else {
      return null;
    }
  }

  /**
   * smart game strategy
   * use WGETA to determine best move
   *
   * @param buildingETAs  the etas for building something
   */
  protected void smartGameStrategy(int[] buildingETAs) {
    log.debug("***** smartGameStrategy *****");

    //
    // save the lr paths list to restore later
    //
    Vector savedLRPaths[] = new Vector[SOCGame.MAXPLAYERS];
    for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
      savedLRPaths[pn] = (Vector)game.getPlayer(pn).getLRPaths().clone();
    }
    
    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    log.debug("ourCurrentWGETA = "+ourCurrentWGETA);

    int leadersCurrentWGETA = ourCurrentWGETA;
    Iterator trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext()) {
      SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
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
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0) {
      Iterator posRoadsIter = ourPlayerTracker.getPossibleRoads().values().iterator();
      while (posRoadsIter.hasNext()) {
	SOCPossibleRoad posRoad = (SOCPossibleRoad)posRoadsIter.next();
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
      log.debug("*** threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());
      if (threatenedSet.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialSettlement(threatenedSet.getCoordinates())) {
	log.debug("POTENTIAL SETTLEMENT ERROR");
	//System.exit(0);
      } 
    }
    Enumeration goodSetEnum = goodSettlements.elements();
    while (goodSetEnum.hasMoreElements()) {
      SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetEnum.nextElement();
      log.debug("*** good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());
      if (goodSet.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialSettlement(goodSet.getCoordinates())) {
	log.debug("POTENTIAL SETTLEMENT ERROR");
	//System.exit(0);
      } 
    }    
    Enumeration threatenedRoadEnum = threatenedRoads.elements();
    while (threatenedRoadEnum.hasMoreElements()) {
      SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadEnum.nextElement();
      log.debug("*** threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates())+" has a score of "+threatenedRoad.getScore());      	
      if (threatenedRoad.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialRoad(threatenedRoad.getCoordinates())) {
	log.debug("POTENTIAL ROAD ERROR");
	//System.exit(0);
      }
    }
    Enumeration goodRoadEnum = goodRoads.elements();
    while (goodRoadEnum.hasMoreElements()) {
      SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadEnum.nextElement();
      log.debug("*** good road at "+Integer.toHexString(goodRoad.getCoordinates())+" has a score of "+goodRoad.getScore());
      if (goodRoad.getNecessaryRoads().isEmpty() &&
	  !ourPlayerData.isPotentialRoad(goodRoad.getCoordinates())) {
	log.debug("POTENTIAL ROAD ERROR");
	//System.exit(0);
      }
    }  
    */

    log.debug("PICKING WHAT TO BUILD");

    ///
    /// pick what we want to build
    ///
		
    ///
    /// pick a settlement that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0) {
      Iterator threatenedSetIter = threatenedSettlements.iterator();
      while (threatenedSetIter.hasNext()) {
	SOCPossibleSettlement threatenedSet = (SOCPossibleSettlement)threatenedSetIter.next();
	if (threatenedSet.getNecessaryRoads().isEmpty()) {
	  log.debug("$$$$$ threatened settlement at "+Integer.toHexString(threatenedSet.getCoordinates())+" has a score of "+threatenedSet.getScore());

	  if ((favoriteSettlement == null) ||
	      (threatenedSet.getScore() > favoriteSettlement.getScore())) {
	    favoriteSettlement = threatenedSet;
	  }
	}
      } 

      Iterator goodSetIter = goodSettlements.iterator();
      while (goodSetIter.hasNext()) {
	SOCPossibleSettlement goodSet = (SOCPossibleSettlement)goodSetIter.next();
	if (goodSet.getNecessaryRoads().isEmpty()) {
	  log.debug("$$$$$ good settlement at "+Integer.toHexString(goodSet.getCoordinates())+" has a score of "+goodSet.getScore());

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
    log.debug("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    } 
    
    ///
    /// pick a road that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0) {
      Iterator threatenedRoadIter = threatenedRoads.iterator();
      while (threatenedRoadIter.hasNext()) {
	SOCPossibleRoad threatenedRoad = (SOCPossibleRoad)threatenedRoadIter.next();
	log.debug("$$$$$ threatened road at "+Integer.toHexString(threatenedRoad.getCoordinates()));

	if ((brain != null) && (brain.getDRecorder().isOn())) {	  
	  brain.getDRecorder().startRecording("ROAD"+threatenedRoad.getCoordinates());
	  brain.getDRecorder().record("Estimate value of road at "+game.getBoard().edgeCoordToString(threatenedRoad.getCoordinates()));
	} 
	
	//
	// see how building this piece impacts our winETA
	//
	threatenedRoad.resetScore();
	float wgetaScore = getWinGameETABonusForRoad(threatenedRoad, buildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);
	if ((brain != null) && (brain.getDRecorder().isOn())) {	  
	  brain.getDRecorder().stopRecording();
	} 
		
	log.debug("wgetaScore = "+wgetaScore);

	if (favoriteRoad == null) {
	  favoriteRoad = threatenedRoad;
	} else {
	  if (threatenedRoad.getScore() > favoriteRoad.getScore()) {
	    favoriteRoad = threatenedRoad;
	  }
	}
      }
      Iterator goodRoadIter = goodRoads.iterator();
      while (goodRoadIter.hasNext()) {
	SOCPossibleRoad goodRoad = (SOCPossibleRoad)goodRoadIter.next();
	log.debug("$$$$$ good road at "+Integer.toHexString(goodRoad.getCoordinates()));

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("ROAD"+goodRoad.getCoordinates());
	  brain.getDRecorder().record("Estimate value of road at "+game.getBoard().edgeCoordToString(goodRoad.getCoordinates()));
	} 

	//
	// see how building this piece impacts our winETA
	//
	goodRoad.resetScore();
	float wgetaScore = getWinGameETABonusForRoad(goodRoad, buildingETAs[SOCBuildingSpeedEstimate.ROAD], leadersCurrentWGETA, playerTrackers);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().stopRecording();
	} 
	
	log.debug("wgetaScore = "+wgetaScore);					

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
    log.debug("%%% RESTORING LRPATH LIST %%%");
    for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
      game.getPlayer(pn).setLRPaths(savedLRPaths[pn]);
    }  
    
    ///
    /// pick a city that can be built now
    ///
    if (ourPlayerData.getNumPieces(SOCPlayingPiece.CITY) > 0) {
      HashMap trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
      SOCPlayerTracker ourTrackerCopy = (SOCPlayerTracker)trackersCopy.get(new Integer(ourPlayerData.getPlayerNumber()));
      int originalWGETAs[] = new int[SOCGame.MAXPLAYERS];	 
      int WGETAdiffs[] = new int[SOCGame.MAXPLAYERS];	 
      Vector leaders = new Vector();
      int bestWGETA = 1000;
      // int bonus = 0;
				
      Iterator posCitiesIter = ourPlayerTracker.getPossibleCities().values().iterator();
      while (posCitiesIter.hasNext()) {
	SOCPossibleCity posCity = (SOCPossibleCity)posCitiesIter.next();
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("CITY"+posCity.getCoordinates());
	  brain.getDRecorder().record("Estimate value of city at "+game.getBoard().nodeCoordToString(posCity.getCoordinates()));
	} 
	
	//
	// see how building this piece impacts our winETA
	//
	leaders.clear();
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().suspend();
	}
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);
	Iterator trackersBeforeIter = trackersCopy.values().iterator();
	while (trackersBeforeIter.hasNext()) {
	  SOCPlayerTracker trackerBefore = (SOCPlayerTracker)trackersBeforeIter.next();
	  log.debug("$$$ win game ETA for player "+trackerBefore.getPlayer().getPlayerNumber()+" = "+trackerBefore.getWinGameETA());
	  originalWGETAs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
	  WGETAdiffs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
	  if (trackerBefore.getWinGameETA() < bestWGETA) {
	    bestWGETA = trackerBefore.getWinGameETA();
	    leaders.removeAllElements();
	    leaders.addElement(trackerBefore);
	  } else if (trackerBefore.getWinGameETA() == bestWGETA) {
	    leaders.addElement(trackerBefore);
	  }
	}		
	log.debug("^^^^ bestWGETA = "+bestWGETA);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().resume();
	}
	//
	// place the city
	//
	SOCCity tmpCity = new SOCCity(ourPlayerData, posCity.getCoordinates());
	game.putTempPiece(tmpCity);

	ourTrackerCopy.addOurNewCity(tmpCity);
				
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);

	float wgetaScore = calcWGETABonusAux(originalWGETAs, trackersCopy, leaders);

	//
	// remove the city
	//
	ourTrackerCopy.undoAddOurNewCity(posCity);
	game.undoPutTempPiece(tmpCity);

	log.debug("*** ETA for city = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("ETA = "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
	} 	

	float etaBonus = getETABonus(buildingETAs[SOCBuildingSpeedEstimate.CITY], leadersCurrentWGETA, wgetaScore);
	log.debug("etaBonus = "+etaBonus);
	
	posCity.addToScore(etaBonus);
	//posCity.addToScore(wgetaScore);

	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("WGETA score = "+df1.format(wgetaScore));
	  brain.getDRecorder().record("Total city score = "+df1.format(etaBonus));
	  brain.getDRecorder().stopRecording();
	} 

	log.debug("$$$  final score = "+posCity.getScore());

	log.debug("$$$$$ possible city at "+Integer.toHexString(posCity.getCoordinates())+" has a score of "+posCity.getScore());

	if ((favoriteCity == null) ||
	    (posCity.getScore() > favoriteCity.getScore())) {
	  favoriteCity = posCity;
	}
      }
    }
         
    if (favoriteSettlement != null) {
      log.debug("### FAVORITE SETTLEMENT IS AT "+Integer.toHexString(favoriteSettlement.getCoordinates()));
      log.debug("###   WITH A SCORE OF "+favoriteSettlement.getScore());
      log.debug("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.SETTLEMENT]);
      log.debug("###   WITH A TOTAL SPEEDUP OF "+favoriteSettlement.getSpeedupTotal());
    }

    if (favoriteCity != null) {
      log.debug("### FAVORITE CITY IS AT "+Integer.toHexString(favoriteCity.getCoordinates()));
      log.debug("###   WITH A SCORE OF "+favoriteCity.getScore());
      log.debug("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.CITY]);
      log.debug("###   WITH A TOTAL SPEEDUP OF "+favoriteCity.getSpeedupTotal());
    }

    if (favoriteRoad != null) {
      log.debug("### FAVORITE ROAD IS AT "+Integer.toHexString(favoriteRoad.getCoordinates()));
      log.debug("###   WITH AN ETA OF "+buildingETAs[SOCBuildingSpeedEstimate.ROAD]);
      log.debug("###   WITH A SCORE OF "+favoriteRoad.getScore());
    }
    int pick = -1;
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
	 (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) == 0) ||
	 (favoriteCity.getScore() > favoriteRoad.getScore()) ||
	 ((favoriteCity.getScore() == favoriteRoad.getScore()) &&
	  (buildingETAs[SOCBuildingSpeedEstimate.CITY] < buildingETAs[SOCBuildingSpeedEstimate.ROAD])))) {
      log.debug("### PICKED FAVORITE CITY");
      pick = SOCPlayingPiece.CITY;
      log.debug("$ PUSHING "+favoriteCity);
      buildingPlan.push(favoriteCity);
    } 
    ///
    /// if there is a road with a better score than
    /// our favorite settlement, then build the road, 
    /// else build the settlement
    ///
    else if ((favoriteRoad != null) &&
	     (ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) > 0) &&
	     (favoriteRoad.getScore() > 0) &&
	     ((favoriteSettlement == null) ||
	      (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) == 0) ||
	      (favoriteSettlement.getScore() < favoriteRoad.getScore()))) {
      log.debug("### PICKED FAVORITE ROAD");
      pick = SOCPlayingPiece.ROAD;
      log.debug("$ PUSHING "+favoriteRoad);
      buildingPlan.push(favoriteRoad);
    } else if ((favoriteSettlement != null) &&
	       (ourPlayerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0)) {
      log.debug("### PICKED FAVORITE SETTLEMENT");
      pick = SOCPlayingPiece.SETTLEMENT;
      log.debug("$ PUSHING "+favoriteSettlement);
      buildingPlan.push(favoriteSettlement);
    }
    ///
    /// if buying a card is better than building...
    ///
			
    //
    // see how buying a card improves our win game ETA
    //
    if (game.getNumDevCards() > 0) {
      if ((brain != null) && (brain.getDRecorder().isOn())) {
	brain.getDRecorder().startRecording("DEVCARD");
	brain.getDRecorder().record("Estimate value of a dev card");
      } 
      
      possibleCard = getDevCardScore(buildingETAs[SOCBuildingSpeedEstimate.CARD], leadersCurrentWGETA);
      float devCardScore = possibleCard.getScore();
      log.debug("### DEV CARD SCORE: "+devCardScore);
      if ((brain != null) && (brain.getDRecorder().isOn())) {
	brain.getDRecorder().stopRecording();
      } 
      
      if ((pick == -1) ||
	  ((pick == SOCPlayingPiece.CITY) &&
	   (devCardScore > favoriteCity.getScore())) ||
	  ((pick == SOCPlayingPiece.ROAD) &&
	   (devCardScore > favoriteRoad.getScore())) ||
	  ((pick == SOCPlayingPiece.SETTLEMENT) &&
	   (devCardScore > favoriteSettlement.getScore()))) {
	log.debug("### BUY DEV CARD");
				
	if (pick != -1) {
	  buildingPlan.pop();
	  log.debug("$ POPPED OFF SOMETHING");
	}
		 
	log.debug("$ PUSHING "+possibleCard);
	buildingPlan.push(possibleCard);
      }
    }
  }


  /**
   * score possible settlements for smartStrategy
   */
  protected void scorePossibleSettlements(int settlementETA, int leadersCurrentWGETA) {
    log.debug("****** scorePossibleSettlements");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();

    /*
    boolean goingToPlayRB = false;
    if (!ourPlayerData.hasPlayedDevCard() &&
	ourPlayerData.getNumPieces(SOCPlayingPiece.ROAD) >= 2 &&
	ourPlayerData.getDevCards().getAmount(SOCDevCardSet.OLD, SOCDevCardConstants.ROADS) > 0) {
      goingToPlayRB = true;
    }
    */

    Iterator posSetsIter = ourPlayerTracker.getPossibleSettlements().values().iterator();
    while (posSetsIter.hasNext()) {
      SOCPossibleSettlement posSet = (SOCPossibleSettlement)posSetsIter.next();
      log.debug("*** scoring possible settlement at "+Integer.toHexString(posSet.getCoordinates()));
      if (!threatenedSettlements.contains(posSet)) {
	threatenedSettlements.addElement(posSet);
      } else if (!goodSettlements.contains(posSet)) {
	goodSettlements.addElement(posSet);
      }
      //
      // only consider settlements we can build now
      //
      Vector necRoadVec = posSet.getNecessaryRoads();
      if (necRoadVec.isEmpty()) {
	log.debug("*** no roads needed");
	//
	//  no roads needed
	//
	//
	//  get wgeta score
	//
	SOCSettlement tmpSet = new SOCSettlement(ourPlayerData, posSet.getCoordinates());
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().startRecording("SETTLEMENT"+posSet.getCoordinates());
	  brain.getDRecorder().record("Estimate value of settlement at "+game.getBoard().nodeCoordToString(posSet.getCoordinates()));
	} 
	
	HashMap trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
	SOCPlayerTracker.updateWinGameETAs(trackersCopy);
	float wgetaScore = calcWGETABonus(playerTrackers, trackersCopy);
	log.debug("***  wgetaScore = "+wgetaScore);

	log.debug("*** ETA for settlement = "+settlementETA);
	if ((brain != null) && (brain.getDRecorder().isOn())) {
	  brain.getDRecorder().record("ETA = "+settlementETA);
	} 
	
	float etaBonus = getETABonus(settlementETA, leadersCurrentWGETA, wgetaScore);
	log.debug("etaBonus = "+etaBonus);
	
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
  protected float getWinGameETABonus(SOCPossiblePiece posPiece) {
    HashMap trackersCopy = null;
    SOCSettlement tmpSet = null;
    SOCCity tmpCity = null;
    SOCRoad tmpRoad = null;
    float bonus = 0;
		

    log.debug("--- before [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    log.debug("our player numbers = "+ourPlayerData.getNumbers());
    log.debug("--- before [end] ---");
    switch (posPiece.getType()) {
    case SOCPossiblePiece.SETTLEMENT:
      tmpSet = new SOCSettlement(ourPlayerData, 
				 posPiece.getCoordinates());
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpSet, game, playerTrackers);
      break;

    case SOCPossiblePiece.CITY:
      trackersCopy = SOCPlayerTracker.copyPlayerTrackers(playerTrackers);
      tmpCity = new SOCCity(ourPlayerData, 
			    posPiece.getCoordinates());
      game.putTempPiece(tmpCity);
      SOCPlayerTracker trackerCopy = (SOCPlayerTracker)trackersCopy.get(new Integer(ourPlayerData.getPlayerNumber()));
      if (trackerCopy != null) {
	trackerCopy.addOurNewCity(tmpCity);
      }
      break;
			
    case SOCPossiblePiece.ROAD:
      tmpRoad = new SOCRoad(ourPlayerData, 
			    posPiece.getCoordinates());
      trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad, game, playerTrackers);
      break;
    }

    //trackersCopyIter = trackersCopy.iterator();
    //while (trackersCopyIter.hasNext()) {
    //	SOCPlayerTracker trackerCopy = (SOCPlayerTracker)trackersCopyIter.next();
    //	trackerCopy.updateThreats(trackersCopy);
    //}

    log.debug("--- after [start] ---");
    //SOCPlayerTracker.playerTrackersDebug(trackersCopy);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);

    float WGETABonus = calcWGETABonus(playerTrackers, trackersCopy);
    log.debug("$$$ win game ETA bonus : +"+WGETABonus);
    bonus = WGETABonus;
		
    log.debug("our player numbers = "+ourPlayerData.getNumbers());
    log.debug("--- after [end] ---");
    switch (posPiece.getType()) {
    case SOCPossiblePiece.SETTLEMENT:			
      SOCPlayerTracker.undoTryPutPiece(tmpSet, game);
      break;

    case SOCPossiblePiece.CITY:
      game.undoPutTempPiece(tmpCity);
      break;

    case SOCPossiblePiece.ROAD:
      SOCPlayerTracker.undoTryPutPiece(tmpRoad, game);
      break;
    }

    log.debug("our player numbers = "+ourPlayerData.getNumbers());
    log.debug("--- cleanup done ---");
		
    return bonus;
  }


  /**
   * add a bonus to the road score based on the change in 
   * win game ETA for this one road
   *
   * @param posRoad  the possible piece that we're scoring
   * @param roadETA  the eta for the road
   * @param leadersCurrentWGETA  the leaders current WGETA
   * @param playerTrackers  the player trackers (passed in as an argument for figuring out road building plan)
   */
  protected float getWinGameETABonusForRoad(SOCPossibleRoad posRoad, int roadETA, int leadersCurrentWGETA, HashMap playerTrackers) {
    log.debug("--- addWinGameETABonusForRoad");
    int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    log.debug("ourCurrentWGETA = "+ourCurrentWGETA);


    HashMap trackersCopy = null;
    SOCRoad tmpRoad1 = null;

    log.debug("--- before [start] ---");
    SOCResourceSet originalResources = ourPlayerData.getResources().copy();
    SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
    //SOCPlayerTracker.playerTrackersDebug(playerTrackers);
    log.debug("--- before [end] ---");
    try {
      SOCResSetBuildTimePair btp = estimate.calculateRollsFast(ourPlayerData.getResources(), SOCGame.ROAD_SET, 50, ourPlayerData.getPortFlags());
      btp.getResources().subtract(SOCGame.ROAD_SET);
      ourPlayerData.getResources().setAmounts(btp.getResources());
    } catch (CutoffExceededException e) {
      log.debug("crap in getWinGameETABonusForRoad - "+e);
    }
    tmpRoad1 = new SOCRoad(ourPlayerData, posRoad.getCoordinates());
    trackersCopy = SOCPlayerTracker.tryPutPiece(tmpRoad1, game, playerTrackers);
    SOCPlayerTracker.updateWinGameETAs(trackersCopy);
    float score = calcWGETABonus(playerTrackers, trackersCopy);

    if (!posRoad.getThreats().isEmpty()) {
      score *= threatMultiplier;
      log.debug("***  (THREAT MULTIPLIER) score * "+threatMultiplier+" = "+score);
    }
    log.debug("*** ETA for road = "+roadETA);
    float etaBonus = getETABonus(roadETA, leadersCurrentWGETA, score);
    log.debug("$$$ score = "+score);
    log.debug("etaBonus = "+etaBonus);
    posRoad.addToScore(etaBonus);

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("ETA = "+roadETA);
      brain.getDRecorder().record("WGETA Score = "+df1.format(score));
      brain.getDRecorder().record("Total road score = "+df1.format(etaBonus));
    } 
    
    log.debug("--- after [end] ---");
    SOCPlayerTracker.undoTryPutPiece(tmpRoad1, game);
    ourPlayerData.getResources().clear();
    ourPlayerData.getResources().add(originalResources);
    log.debug("--- cleanup done ---");
		
    return etaBonus;
  }

  /**
   * calc the win game eta bonus
   *
   * @param  trackersBefore   list of player trackers before move
   * @param  trackersAfter    list of player trackers after move
   */
  protected float calcWGETABonus(HashMap trackersBefore, HashMap trackersAfter) {
    log.debug("^^^^^ calcWGETABonus");
    int originalWGETAs[] = new int[SOCGame.MAXPLAYERS];	 
    int WGETAdiffs[] = new int[SOCGame.MAXPLAYERS];	 
    Vector leaders = new Vector();
    int bestWGETA = 1000;
    float bonus = 0;

    Iterator trackersBeforeIter = trackersBefore.values().iterator();
    while (trackersBeforeIter.hasNext()) {
      SOCPlayerTracker trackerBefore = (SOCPlayerTracker)trackersBeforeIter.next();
      log.debug("$$$ win game ETA for player "+trackerBefore.getPlayer().getPlayerNumber()+" = "+trackerBefore.getWinGameETA());
      originalWGETAs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();
      WGETAdiffs[trackerBefore.getPlayer().getPlayerNumber()] = trackerBefore.getWinGameETA();

      if (trackerBefore.getWinGameETA() < bestWGETA) {
	bestWGETA = trackerBefore.getWinGameETA();
	leaders.removeAllElements();
	leaders.addElement(trackerBefore);
      } else if (trackerBefore.getWinGameETA() == bestWGETA) {
	leaders.addElement(trackerBefore);
      }
    }
		
    log.debug("^^^^ bestWGETA = "+bestWGETA);

    bonus = calcWGETABonusAux(originalWGETAs, trackersAfter, leaders);

    log.debug("^^^^ final bonus = "+bonus);

    return bonus;
  }

  /**
   * calcWGETABonusAux
   *
   * @param originalWGETAs   the original WGETAs
   * @param trackersAfter    the playerTrackers after the change
   * @param leaders          a list of leaders
   */
  public float calcWGETABonusAux(int[] originalWGETAs, HashMap trackersAfter, 
				 Vector leaders) {
    int WGETAdiffs[] = new int[SOCGame.MAXPLAYERS];	
    int bestWGETA = 1000;
    float bonus = 0;
		
    for (int i = 0; i < SOCGame.MAXPLAYERS; i++) {
      WGETAdiffs[i] = originalWGETAs[i];
      if (originalWGETAs[i] < bestWGETA) {
	bestWGETA = originalWGETAs[i];
      }
    }
		
    Iterator trackersAfterIter = trackersAfter.values().iterator();
    while (trackersAfterIter.hasNext()) {
      SOCPlayerTracker trackerAfter = (SOCPlayerTracker)trackersAfterIter.next();
      WGETAdiffs[trackerAfter.getPlayer().getPlayerNumber()] -= trackerAfter.getWinGameETA();
      log.debug("$$$ win game ETA diff for player "+trackerAfter.getPlayer().getPlayerNumber()+" = "+WGETAdiffs[trackerAfter.getPlayer().getPlayerNumber()]);
      if (trackerAfter.getPlayer().getPlayerNumber() == ourPlayerData.getPlayerNumber()) {
	if (trackerAfter.getWinGameETA() == 0) {
	  log.debug("$$$$ adding win game bonus : +"+(100 / SOCGame.MAXPLAYERS));
	  bonus += (100.0f / (float)SOCGame.MAXPLAYERS);
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
    // and increaseing the leaders' WGETA
    //
    if ((originalWGETAs[ourPlayerData.getPlayerNumber()] > 0) &&
	(bonus == 0)) {
      bonus += ((100.0f / (float)SOCGame.MAXPLAYERS) * ((float)WGETAdiffs[ourPlayerData.getPlayerNumber()] / (float)originalWGETAs[ourPlayerData.getPlayerNumber()]));
    }			
		
    log.debug("^^^^ our current bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("WGETA bonus for only myself = "+df1.format(bonus));
    } 
		
    //
    //  try adding takedown bonus for all other players
    //  other than the leaders
    //
    for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++) {
      Enumeration leadersEnum = leaders.elements();
      while (leadersEnum.hasMoreElements()) {
	SOCPlayerTracker leader = (SOCPlayerTracker)leadersEnum.nextElement();
	if ((pn != ourPlayerData.getPlayerNumber()) &&
	    (pn != leader.getPlayer().getPlayerNumber())) {
	  if (originalWGETAs[pn] > 0) {
	    float takedownBonus = -1.0f * (100.0f / (float)SOCGame.MAXPLAYERS) * adversarialFactor * ((float)WGETAdiffs[pn] / (float)originalWGETAs[pn]) * ((float)bestWGETA / (float)originalWGETAs[pn]);
	    bonus += takedownBonus;
	    log.debug("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
	    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	      brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
	    } 
	  } else if (WGETAdiffs[pn] < 0) {
	    float takedownBonus = (100.0f / (float)SOCGame.MAXPLAYERS) * adversarialFactor;
	    bonus += takedownBonus;
	    log.debug("^^^^ added takedown bonus for player "+pn+" : "+takedownBonus);
	    if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	      brain.getDRecorder().record("Bonus for AI with "+pn+" : "+df1.format(takedownBonus));
	    } 
	  }
	}
      }
    }
		
    //
    //  take down bonus for leaders
    //
    Enumeration leadersEnum = leaders.elements();
    while (leadersEnum.hasMoreElements()) {
      SOCPlayerTracker leader = (SOCPlayerTracker)leadersEnum.nextElement();
      if (leader.getPlayer().getPlayerNumber() != ourPlayerData.getPlayerNumber()) {
	if (originalWGETAs[leader.getPlayer().getPlayerNumber()] > 0) {
	  float takedownBonus = -1.0f * (100.0f / (float)SOCGame.MAXPLAYERS) * leaderAdversarialFactor * ((float)WGETAdiffs[leader.getPlayer().getPlayerNumber()] / (float)originalWGETAs[leader.getPlayer().getPlayerNumber()]);
	  bonus += takedownBonus;
	  log.debug("^^^^ added takedown bonus for leader "+leader.getPlayer().getPlayerNumber()+" : +"+takedownBonus);
	  if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)){
	    brain.getDRecorder().record("Bonus for LI with "+leader.getPlayer().getName()+" : +"+df1.format(takedownBonus));
	  } 
	  
	} else if (WGETAdiffs[leader.getPlayer().getPlayerNumber()] < 0) {
	  float takedownBonus = (100.0f / (float)SOCGame.MAXPLAYERS) * leaderAdversarialFactor;
	  bonus += takedownBonus;
	  log.debug("^^^^ added takedown bonus for leader "+leader.getPlayer().getPlayerNumber()+" : +"+takedownBonus);
	  if (((brain != null) && (brain.getDRecorder().isOn())) && (takedownBonus != 0)) {
	    brain.getDRecorder().record("Bonus for LI with "+leader.getPlayer().getName()+" : +"+df1.format(takedownBonus));
	  } 
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
  public SOCPossibleCard getDevCardScore(int cardETA, int leadersCurrentWGETA) {
    float devCardScore = 0;
    log.debug("$$$ devCardScore = +"+devCardScore);
    log.debug("--- before [start] ---");
    // int ourCurrentWGETA = ourPlayerTracker.getWinGameETA();
    int WGETAdiffs[] = new int[SOCGame.MAXPLAYERS];
    int originalWGETAs[] = new int[SOCGame.MAXPLAYERS];	 
    int bestWGETA = 1000;
    Vector leaders = new Vector();
    Iterator trackersIter = playerTrackers.values().iterator();
    while (trackersIter.hasNext()) {
      SOCPlayerTracker tracker = (SOCPlayerTracker)trackersIter.next();
      originalWGETAs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
      WGETAdiffs[tracker.getPlayer().getPlayerNumber()] = tracker.getWinGameETA();
      log.debug("$$$$ win game ETA for player "+tracker.getPlayer().getPlayerNumber()+" = "+tracker.getWinGameETA());

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
    log.debug("--- before [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()+1);
    ourPlayerData.getGame().updateLargestArmy();
    log.debug("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    float bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);
	 
    //
    //  adjust for knight card distribution
    //
    log.debug("^^^^ raw bonus = "+bonus);
					
    bonus *= 0.58f;
    log.debug("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.58 = "+df1.format(bonus));
    } 

    log.debug("^^^^ bonus for +1 knight = "+bonus);
    devCardScore += bonus;
	 
    log.debug("--- after [end] ---");
    ourPlayerData.setNumKnights(ourPlayerData.getNumKnights()-1);
    ourPlayerData.getGame().restoreLargestArmyState();
    log.debug("--- cleanup done ---");

    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Estimating vp card value ...");
    } 
    
    //
    // see what a vp card does to our win game eta
    //
    log.debug("--- before [start] ---");
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().suspend();
    }
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().resume();
    }
    log.debug("--- before [end] ---");
    ourPlayerData.getDevCards().add(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
    log.debug("--- after [start] ---");
    SOCPlayerTracker.updateWinGameETAs(playerTrackers);

    bonus = calcWGETABonusAux(originalWGETAs, playerTrackers, leaders);
		
    log.debug("^^^^ our current bonus = "+bonus);

    //
    //  adjust for +1 vp card distribution
    //
    bonus *= 0.21f;
    log.debug("^^^^ adjusted bonus = "+bonus);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Bonus * 0.21 = "+df1.format(bonus));
    } 
    
    log.debug("$$$ win game ETA bonus for +1 vp: "+bonus);
    devCardScore += bonus;
		
    log.debug("--- after [end] ---");
    ourPlayerData.getDevCards().subtract(1, SOCDevCardSet.NEW, SOCDevCardConstants.CAP);
    log.debug("--- cleanup done ---");

    //
    // add misc bonus
    //
    devCardScore += devCardMultiplier;
    log.debug("^^^^ misc bonus = "+devCardMultiplier);
    if ((brain != null) && (brain.getDRecorder().isOn())) {
      brain.getDRecorder().record("Misc bonus = "+df1.format(devCardMultiplier));
    } 
			
    float score = getETABonus(cardETA, leadersCurrentWGETA, devCardScore);
		
    log.debug("$$$$$ devCardScore = "+devCardScore);
    log.debug("$$$$$ devCardETA = "+cardETA);
    log.debug("$$$$$ final score = "+score);

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
  public float getETABonus(int eta, int leadWGETA, float bonus) {
    log.debug("**** getETABonus ****");
    //return Math.round(etaBonusFactor * ((100f * ((float)(maxGameLength - leadWGETA - eta) / (float)maxGameLength)) * (1.0f - ((float)leadWGETA / (float)maxGameLength))));

    if (log.isDebugEnabled()) {
      log.debug("etaBonusFactor = "+etaBonusFactor);
      log.debug("etaBonusFactor * 100.0 = "+(etaBonusFactor * 100.0f));
      log.debug("eta = "+eta);
      log.debug("maxETA = "+maxETA);
      log.debug("eta / maxETA = "+((float)eta / (float)maxETA));
      log.debug("1.0 - ((float)eta / (float)maxETA) = "+(1.0f - ((float)eta / (float)maxETA)));
      log.debug("leadWGETA = "+leadWGETA);
      log.debug("maxGameLength = "+maxGameLength);
      log.debug("1.0 - ((float)leadWGETA / (float)maxGameLength) = "+(1.0f - ((float)leadWGETA / (float)maxGameLength)));
    }
		

    //return etaBonusFactor * 100.0f * ((1.0f - ((float)eta / (float)maxETA)) * (1.0f - ((float)leadWGETA / (float)maxGameLength)));

    return (bonus / (float)Math.pow((1+etaBonusFactor), eta));

    //return (bonus * (float)Math.pow(etaBonusFactor, ((float)(eta*eta*eta)/(float)1000.0)));
  }
 


}		







