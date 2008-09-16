/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.robot;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

import org.apache.log4j.Logger;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.util.CutoffExceededException;


/**
 * Moved the routines that make and
 * consider offers out of the robot
 * brain.
 *
 * @author Robert S. Thomas
 */
public class SOCRobotNegotiator
{
    protected static final int WIN_GAME_CUTOFF = 25;
    public static final int REJECT_OFFER = 0;
    public static final int ACCEPT_OFFER = 1;
    public static final int COUNTER_OFFER = 2;
    protected SOCRobotBrain brain;
    protected int strategyType;
    protected SOCGame game;
    protected Stack buildingPlan;
    protected HashMap playerTrackers;
    protected SOCPlayerTracker ourPlayerTracker;
    protected SOCPlayer ourPlayerData;
    protected SOCRobotDM decisionMaker;
    protected boolean[][] isSellingResource;
    protected boolean[][] wantsAnotherOffer;
    protected Vector offersMade;
    protected SOCPossiblePiece[] targetPieces;
    /** debug logging */
    private transient Logger log = Logger.getLogger(this.getClass().getName());

    /**
     * constructor
     *
     * @param br  the robot brain
     */
    public SOCRobotNegotiator(SOCRobotBrain br)
    {
        brain = br;
        strategyType = br.getRobotParameters().getStrategyType();
        playerTrackers = brain.getPlayerTrackers();
        ourPlayerTracker = brain.getOurPlayerTracker();
        ourPlayerData = brain.getOurPlayerData();
        buildingPlan = brain.getBuildingPlan();
        decisionMaker = brain.getDecisionMaker();
        game = brain.getGame();

        isSellingResource = new boolean[SOCGame.MAXPLAYERS][SOCResourceConstants.MAXPLUSONE];
        resetIsSelling();

        wantsAnotherOffer = new boolean[SOCGame.MAXPLAYERS][SOCResourceConstants.MAXPLUSONE];
        resetWantsAnotherOffer();

        offersMade = new Vector();

        targetPieces = new SOCPossiblePiece[SOCGame.MAXPLAYERS];
        resetTargetPieces();
    }

    /**
     * reset target pieces for all players
     */
    public void resetTargetPieces()
    {
        log.debug("*** resetTargetPieces ***");

        for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
        {
            targetPieces[pn] = null;
        }
    }

    /**
     * set a target piece for a player
     *
     * @param pn  the player number
     * @param piece  the piece that they want to build next
     */
    public void setTargetPiece(int pn, SOCPossiblePiece piece)
    {
        targetPieces[pn] = piece;
    }

    /**
     * reset offers made
     */
    public void resetOffersMade()
    {
        offersMade.clear();
    }

    /**
     * add an offer to the offers made list
     *
     * @param offer  the offer
     */
    public void addToOffersMade(SOCTradeOffer offer)
    {
        if (offer != null)
        {
            offersMade.add(offer);
        }
    }

    /**
     * reset the isSellingResource array so that
     * if the player has the resource, then he is selling it
     */
    public void resetIsSelling()
    {
        log.debug("*** resetIsSelling (true for every resource the player has) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
            {
                if (( ! game.isSeatVacant(pn)) &&
                    (game.getPlayer(pn).getResources().getAmount(rsrcType) > 0))
                {
                    isSellingResource[pn][rsrcType] = true;
                }
            }
        }
    }

    /**
     * reset the wantsAnotherOffer array to all false
     */
    public void resetWantsAnotherOffer()
    {
        log.debug("*** resetWantsAnotherOffer (all false) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
            {
                wantsAnotherOffer[pn][rsrcType] = false;
            }
        }
    }

    /**
     * mark a player as not selling a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsNotSelling(int pn, int rsrcType)
    {
        log.debug("*** markAsNotSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = false;
    }

    /**
     * mark a player as willing to sell a resource
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsSelling(int pn, int rsrcType)
    {
        log.debug("*** markAsSelling pn=" + pn + " rsrcType=" + rsrcType);
        isSellingResource[pn][rsrcType] = true;
    }

    /**
     * mark a player as not wanting another offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsNotWantingAnotherOffer(int pn, int rsrcType)
    {
        log.debug("*** markAsNotWantingAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = false;
    }

    /**
     * mark a player as wanting another offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public void markAsWantsAnotherOffer(int pn, int rsrcType)
    {
        log.debug("*** markAsWantsAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
        wantsAnotherOffer[pn][rsrcType] = true;
    }

    /**
     * @return true if the player is marked as wanting a better offer
     *
     * @param pn         the number of the player
     * @param rsrcType   the type of resource
     */
    public boolean wantsAnotherOffer(int pn, int rsrcType)
    {
        return wantsAnotherOffer[pn][rsrcType];
    }

    /***
     * make an offer to another player
     *
     * @param targetPiece  the piece that we want to build
     * @return the offer we want to make, or null for no offer
     */
    public SOCTradeOffer makeOffer(SOCPossiblePiece targetPiece)
    {
        log.debug("***** MAKE OFFER *****");

        if (targetPiece == null)
        {
            return null;
        }

        SOCTradeOffer offer = null;

        SOCResourceSet targetResources = null;

        switch (targetPiece.getType())
        {
        case SOCPossiblePiece.CARD:
            targetResources = SOCGame.CARD_SET;

            break;

        case SOCPossiblePiece.ROAD:
            targetResources = SOCGame.ROAD_SET;

            break;

        case SOCPossiblePiece.SETTLEMENT:
            targetResources = SOCGame.SETTLEMENT_SET;

            break;

        case SOCPossiblePiece.CITY:
            targetResources = SOCGame.CITY_SET;

            break;
        }

        SOCResourceSet ourResources = ourPlayerData.getResources();

        log.debug("*** targetResources = " + targetResources);
        log.debug("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return offer;
        }

        if (ourResources.getAmount(SOCResourceConstants.UNKNOWN) > 0)
        {
            log.debug("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return offer;
        }

        SOCTradeOffer batna = getOfferToBank(targetResources);
        log.debug("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        log.debug("*** batnaBuildingTime = " + batnaBuildingTime);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        log.debug("*** batnaBuildingTime = " + batnaBuildingTime);

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        ///
        int[] rollsPerResource = estimate.getRollsPerResource();
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //log.debug("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (log.isDebugEnabled())
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                log.debug("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //log.debug("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (log.isDebugEnabled())
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                log.debug("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
            }
        }

        ///
        /// make a list of what other players are selling
        ///
        boolean[] someoneIsSellingResource = new boolean[SOCResourceConstants.MAXPLUSONE];

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            someoneIsSellingResource[rsrcType] = false;

            for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
            {
                if ((pn != ourPlayerData.getPlayerNumber()) && (isSellingResource[pn][rsrcType]))
                {
                    someoneIsSellingResource[rsrcType] = true;
                    log.debug("*** player " + pn + " is selling " + rsrcType);

                    break;
                }
            }
        }

        ///
        /// figure out which resources we don't have enough of
        /// that someone is selling
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while ((getRsrcIdx >= 0) && ((ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx])) || (!someoneIsSellingResource[neededRsrc[getRsrcIdx]])))
        {
            getRsrcIdx--;
        }

        ///
        /// if getRsrcIdx < 0 then we've asked for everything
        /// we need and nobody has it
        ///
        if (getRsrcIdx >= 0)
        {
            log.debug("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            log.debug("*** offer should be null : offer = " + offer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (offer == null))
            {
                log.debug("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) > 0)
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    log.debug("*** offer = " + offer);

                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);
                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                }

                giveRsrcIdx++;
            }

            log.debug("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                {
                    log.debug("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    log.debug("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

                    if ((ourResources.getAmount(neededRsrc[giveRsrcIdx1]) > targetResources.getAmount(neededRsrc[giveRsrcIdx1])) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        giveResourceSet.clear();
                        giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                        ///
                        /// make sure the offer is better than our BATNA
                        ///
                        int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                        if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                        {
                            offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                            log.debug("*** offer = " + offer);
                            log.debug("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            log.debug("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (offer == null))
                {
                    if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0)
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (offer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (ourResources.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    log.debug("*** offer = " + offer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (offer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        log.debug("*** offer = " + offer);
                                        log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }

                giveRsrcIdx1 = 0;
                giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                {
                    if ((leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (offer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (leftovers.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    log.debug("*** offer = " + offer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (offer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        log.debug("*** offer = " + offer);
                                        log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }
            }
        }

        ///
        /// consider offers where we give one for one unneeded we 
        /// we can use at a bank or port
        ///
        if (offer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (!someoneIsSellingResource[neededRsrc[getRsrcIdx2]]))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (offer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(1, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(1, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (offer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (offer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** offer = " + offer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (offer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    offer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** offer = " + offer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(1, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        return offer;
    }

    /**
     * aux function for make offer
     */
    protected SOCTradeOffer makeOfferAux(SOCResourceSet giveResourceSet, SOCResourceSet getResourceSet, int neededResource)
    {
        log.debug("**** makeOfferAux ****");
        log.debug("giveResourceSet = " + giveResourceSet);
        log.debug("getResourceSet = " + getResourceSet);

        SOCTradeOffer offer = null;

        ///
        /// see if we've made this offer before
        ///
        boolean match = false;
        Iterator offersMadeIter = offersMade.iterator();

        while ((offersMadeIter.hasNext() && !match))
        {
            SOCTradeOffer pastOffer = (SOCTradeOffer) offersMadeIter.next();

            if ((pastOffer != null) && (pastOffer.getGiveSet().equals(giveResourceSet)) && (pastOffer.getGetSet().equals(getResourceSet)))
            {
                match = true;
            }
        }

        ///
        /// see if somone is offering this to us
        ///
        if (!match)
        {
            int opn = ourPlayerData.getPlayerNumber();
            for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
            {
                if (i != opn)
                {
                    SOCTradeOffer outsideOffer = game.getPlayer(i).getCurrentOffer();

                    if ((outsideOffer != null) && (outsideOffer.getGetSet().equals(giveResourceSet)) && (outsideOffer.getGiveSet().equals(getResourceSet)))
                    {
                        match = true;

                        break;
                    }
                }
            }
        }

        log.debug("*** match = " + match);

        if (!match)
        {
            ///
            /// this is a new offer
            ///
            log.debug("* this is a new offer");

            int numOfferedTo = 0;
            boolean[] offeredTo = new boolean[SOCGame.MAXPLAYERS];
            int opn = ourPlayerData.getPlayerNumber();

            ///
            /// if it's our turn
            ///			
            if (game.getCurrentPlayerNumber() == opn)
            {
                ///
                /// only offer to players that are selling what we're asking for
                /// and aren't too close to winning
                ///
                for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                {
                    log.debug("** isSellingResource[" + i + "][" + neededResource + "] = " + isSellingResource[i][neededResource]);

                    if ((i != opn) && isSellingResource[i][neededResource] &&
                        (! game.isSeatVacant(i)) &&
                        (game.getPlayer(i).getResources().getTotal() >= getResourceSet.getTotal()))
                    {
                        SOCPlayerTracker tracker = (SOCPlayerTracker) playerTrackers.get(new Integer(i));

                        if ((tracker != null) && (tracker.getWinGameETA() >= WIN_GAME_CUTOFF))
                        {
                            numOfferedTo++;
                            offeredTo[i] = true;
                        }
                        else
                        {
                            offeredTo[i] = false;
                        }
                    }
                }
            }
            else
            {
                ///
                /// it's not our turn, just offer to the player who's turn it is
                ///
                int curpn = game.getCurrentPlayerNumber();

                if (isSellingResource[curpn][neededResource] && (game.getPlayer(curpn).getResources().getTotal() >= getResourceSet.getTotal()))
                {
                    log.debug("** isSellingResource[" + curpn + "][" + neededResource + "] = " + isSellingResource[curpn][neededResource]);

                    SOCPlayerTracker tracker = (SOCPlayerTracker) playerTrackers.get(new Integer(curpn));

                    if ((tracker != null) && (tracker.getWinGameETA() >= WIN_GAME_CUTOFF))
                    {
                        numOfferedTo++;
                        offeredTo[curpn] = true;
                    }
                }
            }

            log.debug("** numOfferedTo = " + numOfferedTo);

            if (numOfferedTo > 0)
            {
                ///
                ///  the offer
                ///
                offer = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), offeredTo, giveResourceSet, getResourceSet);

                ///
                /// only make the offer if we think somone will take it
                ///
                boolean acceptable = false;

                for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
                {
                    if (offeredTo[pn])
                    {
                        int offerResponse = considerOffer2(offer, pn);
                        log.debug("* considerOffer2(offer, " + pn + ") = " + offerResponse);

                        if (offerResponse == ACCEPT_OFFER)
                        {
                            acceptable = true;

                            break;
                        }
                    }
                }

                if (!acceptable)
                {
                    offer = null;
                }
            }
        }

        return offer;
    }

    /**
     * another aux function
     * this one returns the number of rolls until we reach
     * the target given a possible offer
     *
     * @param player             our player data
     * @param targetResources    the resources we want
     * @param giveSet            the set of resources we're giving
     * @param getSet             the set of resources we're receiving
     * @param estimate           a SOCBuildingSpeedEstimate for our player
     */
    protected int getETAToTargetResources(SOCPlayer player, SOCResourceSet targetResources, SOCResourceSet giveSet, SOCResourceSet getSet, SOCBuildingSpeedEstimate estimate)
    {
        SOCResourceSet ourResourcesCopy = player.getResources().copy();
        log.debug("*** giveSet = " + giveSet);
        log.debug("*** getSet = " + getSet);
        ourResourcesCopy.subtract(giveSet);
        ourResourcesCopy.add(getSet);

        int offerBuildingTime = 1000;

        try
        {
            SOCResSetBuildTimePair offerBuildingTimePair = estimate.calculateRollsFast(ourResourcesCopy, targetResources, 1000, player.getPortFlags());
            offerBuildingTime = offerBuildingTimePair.getRolls();
        }
        catch (CutoffExceededException e)
        {
            ;
        }

        log.debug("*** offerBuildingTime = " + offerBuildingTime);
        log.debug("*** ourResourcesCopy = " + ourResourcesCopy);

        return (offerBuildingTime);
    }

    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @param receiverNum  the player number of the receiver
     *
     * @return if we want to accept, reject, or make a counter offer
     */
    public int considerOffer2(SOCTradeOffer offer, int receiverNum)
    {
        ///
        /// This version should be faster
        ///
        log.debug("***** CONSIDER OFFER 2 *****");

        int response = REJECT_OFFER;

        SOCPlayer receiverPlayerData = game.getPlayer(receiverNum);
        SOCResourceSet receiverResources = receiverPlayerData.getResources();

        SOCResourceSet rsrcsOut = offer.getGetSet();
        SOCResourceSet rsrcsIn = offer.getGiveSet();

        //
        // if the receiver doesn't have what's asked for, they'll reject
        //
        if ((receiverResources.getAmount(SOCResourceConstants.UNKNOWN) == 0) && (!receiverResources.contains(rsrcsOut)))
        {
            return response;
        }

        int senderNum = offer.getFrom();

        log.debug("senderNum = " + senderNum);
        log.debug("receiverNum = " + receiverNum);
        log.debug("rsrcs from receiver = " + rsrcsOut);
        log.debug("rsrcs to receiver = " + rsrcsIn);

        SOCPossiblePiece receiverTargetPiece = targetPieces[receiverNum];

        log.debug("targetPieces[" + receiverNum + "] = " + receiverTargetPiece);

        SOCPlayerTracker receiverPlayerTracker = (SOCPlayerTracker) playerTrackers.get(new Integer(receiverNum));

        if (receiverPlayerTracker == null)
        {
            return response;
        }

        SOCPlayerTracker senderPlayerTracker = (SOCPlayerTracker) playerTrackers.get(new Integer(senderNum));

        if (senderPlayerTracker == null)
        {
            return response;
        }

        SOCRobotDM simulator;

        if (receiverTargetPiece == null)
        {
            Stack receiverBuildingPlan = new Stack();
            simulator = new SOCRobotDM(brain.getRobotParameters(), playerTrackers, receiverPlayerTracker, receiverPlayerData, receiverBuildingPlan);

            if (receiverNum == ourPlayerData.getPlayerNumber())
            {
                simulator.planStuff(strategyType);
            }
            else
            {
                simulator.planStuff(strategyType);
            }

            if (receiverBuildingPlan.empty())
            {
                return response;
            }

            receiverTargetPiece = (SOCPossiblePiece) receiverBuildingPlan.peek();
            targetPieces[receiverNum] = receiverTargetPiece;
        }

        log.debug("receiverTargetPiece = " + receiverTargetPiece);

        SOCPossiblePiece senderTargetPiece = targetPieces[senderNum];

        log.debug("targetPieces[" + senderNum + "] = " + senderTargetPiece);

        SOCPlayer senderPlayerData = game.getPlayer(senderNum);

        if (senderTargetPiece == null)
        {
            Stack senderBuildingPlan = new Stack();
            simulator = new SOCRobotDM(brain.getRobotParameters(), playerTrackers, senderPlayerTracker, senderPlayerData, senderBuildingPlan);

            if (senderNum == ourPlayerData.getPlayerNumber())
            {
                simulator.planStuff(strategyType);
            }
            else
            {
                simulator.planStuff(strategyType);
            }

            if (senderBuildingPlan.empty())
            {
                return response;
            }

            senderTargetPiece = (SOCPossiblePiece) senderBuildingPlan.peek();
            targetPieces[senderNum] = senderTargetPiece;
        }

        log.debug("senderTargetPiece = " + senderTargetPiece);

        int senderWGETA = senderPlayerTracker.getWinGameETA();

        if (senderWGETA > WIN_GAME_CUTOFF)
        {
            //
            //  see if the sender is in a race with the receiver
            //
            boolean inARace = false;

            if ((receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) || (receiverTargetPiece.getType() == SOCPossiblePiece.ROAD))
            {
                Enumeration threatsEnum = receiverTargetPiece.getThreats().elements();

                while (threatsEnum.hasMoreElements())
                {
                    SOCPossiblePiece threat = (SOCPossiblePiece) threatsEnum.nextElement();

                    if ((threat.getType() == senderTargetPiece.getType()) && (threat.getCoordinates() == senderTargetPiece.getCoordinates()))
                    {
                        inARace = true;

                        break;
                    }
                }

                if (inARace)
                {
                    log.debug("inARace == true (threat from sender)");
                }
                else if (receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT)
                {
                    Enumeration conflictsEnum = ((SOCPossibleSettlement) receiverTargetPiece).getConflicts().elements();

                    while (conflictsEnum.hasMoreElements())
                    {
                        SOCPossibleSettlement conflict = (SOCPossibleSettlement) conflictsEnum.nextElement();

                        if ((senderTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) && (conflict.getCoordinates() == senderTargetPiece.getCoordinates()))
                        {
                            inARace = true;

                            break;
                        }
                    }

                    if (inARace)
                    {
                        log.debug("inARace == true (conflict with sender)");
                    }
                }
            }

            if (!inARace)
            {
                ///
                /// see if this is good for the receiver
                ///
                SOCResourceSet targetResources = null;

                switch (receiverTargetPiece.getType())
                {
                case SOCPossiblePiece.CARD:
                    targetResources = SOCGame.CARD_SET;

                    break;

                case SOCPossiblePiece.ROAD:
                    targetResources = SOCGame.ROAD_SET;

                    break;

                case SOCPossiblePiece.SETTLEMENT:
                    targetResources = SOCGame.SETTLEMENT_SET;

                    break;

                case SOCPossiblePiece.CITY:
                    targetResources = SOCGame.CITY_SET;

                    break;
                }

                SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(receiverPlayerData.getNumbers());

                SOCTradeOffer receiverBatna = getOfferToBank(targetResources);
                log.debug("*** receiverBatna = " + receiverBatna);

                int batnaBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET, estimate);

                log.debug("*** batnaBuildingTime = " + batnaBuildingTime);

                int offerBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, rsrcsOut, rsrcsIn, estimate);

                log.debug("*** offerBuildingTime = " + offerBuildingTime);

                /*
                   if ((offerBuildingTime < batnaBuildingTime) ||
                       ((receiverBatna != null) &&
                        (offerBuildingTime == batnaBuildingTime) &&
                        (rsrcsOut.getTotal() < receiverBatna.getGiveSet().getTotal())) ||
                       ((receiverBatna == null) &&
                        (offerBuildingTime == batnaBuildingTime) &&
                        (rsrcsOut.getTotal() < rsrcsIn.getTotal()))) {
                 */

                //
                // only accept offers that are better than BATNA
                //
                if (offerBuildingTime < batnaBuildingTime)
                {
                    response = ACCEPT_OFFER;
                }
                else
                {
                    response = COUNTER_OFFER;
                }
            }
        }

        return response;
    }

    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @return true if we want to accept the offer
     */

    /*
       public int considerOffer(SOCTradeOffer offer) {
       log.debug("***** CONSIDER OFFER *****");
       int response = REJECT_OFFER;
    
       SOCPlayer offeringPlayer = game.getPlayer(offer.getFrom());
       SOCResourceSet rsrcsOut = offer.getGetSet();
       SOCResourceSet rsrcsIn = offer.getGiveSet();
       if (ourPlayerData.getResources().contains(rsrcsOut)) {
       int ourOriginalWGETA = 0;
       SOCPossiblePiece ourOriginalPiece = null;
       int ourOriginalPieceType = 0;
       int ourOriginalPieceCoord = 0;
       int ourOriginalPieceETA = 0;
       int ourOriginalPieceScore = 0;
       SOCPossibleSettlement ourOriginalFavoriteSettlement = null;
       int ourOriginalFavoriteSettlementCoord = 0;
       int ourOriginalFavoriteSettlementETA = 50;
       int ourOriginalFavoriteSettlementScore = 0;
       SOCPossibleCity ourOriginalFavoriteCity = null;
       int ourOriginalFavoriteCityCoord = 0;
       int ourOriginalFavoriteCityETA = 50;
       int ourOriginalFavoriteCityScore = 0;
       SOCPossibleRoad ourOriginalFavoriteRoad = null;
       int ourOriginalFavoriteRoadCoord = 0;
       int ourOriginalFavoriteRoadETA = 50;
       int ourOriginalFavoriteRoadScore = 0;
       SOCPossibleCard ourOriginalPossibleCard = null;
       int ourOriginalPossibleCardETA = 50;
       int ourOriginalPossibleCardScore = 0;
       int ourNewWGETA = 0;
       int ourNewPieceType = 0;
       int ourNewPieceCoord = 0;
       int ourNewPieceETA = 0;
       int ourNewPieceScore = 0;
       SOCPossibleSettlement ourNewFavoriteSettlement = null;
       int ourNewFavoriteSettlementCoord = 0;
       int ourNewFavoriteSettlementETA = 50;
       int ourNewFavoriteSettlementScore = 0;
       SOCPossibleCity ourNewFavoriteCity = null;
       int ourNewFavoriteCityCoord = 0;
       int ourNewFavoriteCityETA = 50;
       int ourNewFavoriteCityScore = 0;
       SOCPossibleRoad ourNewFavoriteRoad = null;
       int ourNewFavoriteRoadCoord = 0;
       int ourNewFavoriteRoadETA = 50;
       int ourNewFavoriteRoadScore = 0;
       SOCPossibleCard ourNewPossibleCard = null;
       int ourNewPossibleCardETA = 50;
       int ourNewPossibleCardScore = 0;
       int theirOriginalWGETA = 0;
       SOCPossiblePiece theirOriginalPiece = null;
       int theirOriginalPieceType = 0;
       int theirOriginalPieceCoord = 0;
       int theirOriginalPieceETA = 0;
       int theirOriginalPieceScore = 0;
       int theirNewWGETA = 0;
       int theirNewPieceType = 0;
       int theirNewPieceCoord = 0;
       int theirNewPieceETA = 0;
       int theirNewPieceScore = 0;
       SOCPlayerTracker.updateWinGameETAs(playerTrackers);
       ourOriginalWGETA = ourPlayerTracker.getWinGameETA();
    
       SOCResourceSet theirResources = offeringPlayer.getResources();
       SOCResourceSet ourResources = ourPlayerData.getResources();
    
       SOCRobotDM simulator;
    
       Stack ourBuildingPlan = buildingPlan;
       if (ourBuildingPlan.empty()) {
       log.debug("**** our building plan is empty ****");
       simulator = new SOCRobotDM(brain.getRobotParameters(),
       playerTrackers,
       ourPlayerTracker,
       ourPlayerData,
       ourBuildingPlan);
       simulator.planStuff();
       }
    
       if (ourBuildingPlan.empty()) {
       return response;
       }
       SOCPossiblePiece targetPiece = (SOCPossiblePiece)ourBuildingPlan.peek();
       ourOriginalFavoriteSettlement = decisionMaker.getFavoriteSettlement();
       ourOriginalFavoriteCity = decisionMaker.getFavoriteCity();
       ourOriginalFavoriteRoad = decisionMaker.getFavoriteRoad();
       ourOriginalPossibleCard = decisionMaker.getPossibleCard();
       SOCPlayerTracker theirPlayerTracker = (SOCPlayerTracker)playerTrackers.get(new Integer(offer.getFrom()));
    
       if (theirPlayerTracker != null) {
       theirOriginalWGETA = theirPlayerTracker.getWinGameETA();
       log.debug("CHECKING OFFER FROM PLAYER "+offer.getFrom());
       log.debug("they give : "+rsrcsIn);
       log.debug("they get : "+rsrcsOut);
    
       log.debug("---------< before >-----------");
       ourOriginalPiece = targetPiece;
       ourOriginalPieceType = targetPiece.getType();
       ourOriginalPieceCoord = targetPiece.getCoordinates();
       ourOriginalPieceETA = targetPiece.getETA();
       ourOriginalPieceScore = targetPiece.getScore();
       log.debug("ourResources : "+ourResources);
       log.debug("ourOriginalWGETA = "+ourOriginalWGETA);
       log.debug("our target piece type : "+targetPiece.getType());
       log.debug("our target piece coord : "+Integer.toHexString(targetPiece.getCoordinates()));
       log.debug("our target piece eta : "+targetPiece.getETA());
       log.debug("our target piece score : "+targetPiece.getScore());
    
       if (ourOriginalFavoriteSettlement != null) {
       ourOriginalFavoriteSettlementCoord = ourOriginalFavoriteSettlement.getCoordinates();
       ourOriginalFavoriteSettlementETA = ourOriginalFavoriteSettlement.getETA();
       ourOriginalFavoriteSettlementScore = ourOriginalFavoriteSettlement.getScore();
       }
       if (ourOriginalFavoriteCity != null) {
       ourOriginalFavoriteCityCoord = ourOriginalFavoriteCity.getCoordinates();
       ourOriginalFavoriteCityETA = ourOriginalFavoriteCity.getETA();
       ourOriginalFavoriteCityScore = ourOriginalFavoriteCity.getScore();
       }
       if (ourOriginalFavoriteRoad != null) {
       ourOriginalFavoriteRoadCoord = ourOriginalFavoriteRoad.getCoordinates();
       ourOriginalFavoriteRoadETA = ourOriginalFavoriteRoad.getETA();
       ourOriginalFavoriteRoadScore = ourOriginalFavoriteRoad.getScore();
       }
       if (ourOriginalPossibleCard != null) {
       ourOriginalPossibleCardETA = ourOriginalPossibleCard.getETA();
       ourOriginalPossibleCardScore = ourOriginalPossibleCard.getScore();
       }
       Stack theirBuildingPlan = new Stack();
       simulator = new SOCRobotDM(brain.getRobotParameters(),
       playerTrackers,
       theirPlayerTracker,
       theirPlayerTracker.getPlayer(),
       theirBuildingPlan);
       simulator.planStuff();
       theirOriginalPiece = (SOCPossiblePiece)theirBuildingPlan.pop();
       theirOriginalPieceType = theirOriginalPiece.getType();
       theirOriginalPieceCoord = theirOriginalPiece.getCoordinates();
       theirOriginalPieceETA = theirOriginalPiece.getETA();
       theirOriginalPieceScore = theirOriginalPiece.getScore();
       log.debug("theirResources : "+theirResources);
       log.debug("theirOriginalWGETA = "+theirOriginalWGETA);
       if (theirOriginalPiece != null) {
       log.debug("their target piece type : "+theirOriginalPiece.getType());
       log.debug("their target piece coord : "+Integer.toHexString(theirOriginalPiece.getCoordinates()));
       log.debug("their target piece eta : "+theirOriginalPiece.getETA());
       log.debug("their target piece score : "+theirOriginalPiece.getScore());
       } else {
       log.debug("their target piece == null");
       }
    
       theirResources.add(rsrcsOut);
       theirResources.subtract(rsrcsIn);
       ourResources.add(rsrcsIn);
       ourResources.subtract(rsrcsOut);
    
       log.debug("---------< after >-----------");
    
       SOCPlayerTracker.updateWinGameETAs(playerTrackers);
       ourNewWGETA = ourPlayerTracker.getWinGameETA();
       theirNewWGETA = theirPlayerTracker.getWinGameETA();
       Stack ourBuildingPlanAfter = new Stack();
       simulator = new SOCRobotDM(brain.getRobotParameters(),
       playerTrackers,
       ourPlayerTracker,
       ourPlayerData,
       ourBuildingPlanAfter);
       simulator.planStuff();
       SOCPossiblePiece ourNewTargetPiece = (SOCPossiblePiece)ourBuildingPlanAfter.pop();
       ourNewFavoriteSettlement = simulator.getFavoriteSettlement();
       ourNewFavoriteCity = simulator.getFavoriteCity();
       ourNewFavoriteRoad = simulator.getFavoriteRoad();
       ourNewPossibleCard = simulator.getPossibleCard();
       ourNewPieceType = ourNewTargetPiece.getType();
       ourNewPieceCoord = ourNewTargetPiece.getCoordinates();
       ourNewPieceETA = ourNewTargetPiece.getETA();
       ourNewPieceScore = ourNewTargetPiece.getScore();
    
       log.debug("ourResources : "+ourResources);
       log.debug("ourNewWGETA = "+ourNewWGETA);
       if (ourNewTargetPiece != null) {
       log.debug("our target piece type : "+ourNewTargetPiece.getType());
       log.debug("our target piece coord : "+Integer.toHexString(ourNewTargetPiece.getCoordinates()));
       log.debug("our target piece eta : "+ourNewTargetPiece.getETA());
       log.debug("our target piece score : "+ourNewTargetPiece.getScore());
       } else {
       log.debug("our target piece == null");
       }
    
       if (ourNewFavoriteSettlement != null) {
       ourNewFavoriteSettlementCoord = ourNewFavoriteSettlement.getCoordinates();
       ourNewFavoriteSettlementETA = ourNewFavoriteSettlement.getETA();
       ourNewFavoriteSettlementScore = ourNewFavoriteSettlement.getScore();
       }
       if (ourNewFavoriteCity != null) {
       ourNewFavoriteCityCoord = ourNewFavoriteCity.getCoordinates();
       ourNewFavoriteCityETA = ourNewFavoriteCity.getETA();
       ourNewFavoriteCityScore = ourNewFavoriteCity.getScore();
       }
       if (ourNewFavoriteRoad != null) {
       ourNewFavoriteRoadCoord = ourNewFavoriteRoad.getCoordinates();
       ourNewFavoriteRoadETA = ourNewFavoriteRoad.getETA();
       ourNewFavoriteRoadScore = ourNewFavoriteRoad.getScore();
       }
       if (ourNewPossibleCard != null) {
       ourNewPossibleCardETA = ourNewPossibleCard.getETA();
       ourNewPossibleCardScore = ourNewPossibleCard.getScore();
       }
       theirBuildingPlan.clear();
       simulator = new SOCRobotDM(brain.getRobotParameters(),
       playerTrackers,
       theirPlayerTracker,
       theirPlayerTracker.getPlayer(),
       theirBuildingPlan);
       simulator.planStuff();
       SOCPossiblePiece theirNewTargetPiece = (SOCPossiblePiece)theirBuildingPlan.pop();
    
       theirNewPieceType = theirNewTargetPiece.getType();
       theirNewPieceCoord = theirNewTargetPiece.getCoordinates();
       theirNewPieceETA = theirNewTargetPiece.getETA();
       theirNewPieceScore = theirNewTargetPiece.getScore();
       log.debug("theirResources : "+theirResources);
       log.debug("theirNewWGETA = "+theirNewWGETA);
       if (theirNewTargetPiece != null) {
       log.debug("their target piece type : "+theirNewTargetPiece.getType());
       log.debug("their target piece coord : "+Integer.toHexString(theirNewTargetPiece.getCoordinates()));
       log.debug("their target piece eta : "+theirNewTargetPiece.getETA());
       log.debug("their target piece score : "+theirNewTargetPiece.getScore());
       } else {
       log.debug("their target piece == null");
       }
    
       log.debug("---------< cleanup >-----------");
    
       theirResources.subtract(rsrcsOut);
       theirResources.add(rsrcsIn);
       ourResources.subtract(rsrcsIn);
       ourResources.add(rsrcsOut);
    
       SOCPlayerTracker.updateWinGameETAs(playerTrackers);
    
       log.debug("ourResources : "+ourResources);
       log.debug("theirResources : "+theirResources);
    
       log.debug("---------< done >-----------");
       }
       //
       //  now that we have the info, decide if taking
       //  the offer is worth it
       //
       if (theirOriginalWGETA < WIN_GAME_CUTOFF) {
       //brain.getClient().sendText(game, "You're too close to winning.");
       } else {
       //
       //  see if we are in a race with them
       //
       boolean inARace = false;
       if ((ourOriginalPieceType == SOCPossiblePiece.SETTLEMENT) ||
       (ourOriginalPieceType == SOCPossiblePiece.ROAD)) {
       Enumeration threatsEnum = ourOriginalPiece.getThreats().elements();
       while (threatsEnum.hasMoreElements()) {
       SOCPossiblePiece threat = (SOCPossiblePiece)threatsEnum.nextElement();
       if ((threat.getCoordinates() == theirOriginalPieceCoord) ||
       (threat.getCoordinates() == theirNewPieceCoord)) {
       inARace = true;
       break;
       }
       }
       if (inARace) {
       log.debug("inARace == true (threat == their new piece)");
       //brain.getClient().sendText(game, "No way!  We're racing for the same spot.");
       } else if (ourOriginalPieceType == SOCPossiblePiece.SETTLEMENT) {
       Enumeration conflictsEnum = ((SOCPossibleSettlement)ourOriginalPiece).getConflicts().elements();
       while (conflictsEnum.hasMoreElements()) {
       SOCPossibleSettlement conflict = (SOCPossibleSettlement)conflictsEnum.nextElement();
       if ((conflict.getCoordinates() == theirOriginalPieceCoord) ||
       (conflict.getCoordinates() == theirNewPieceCoord)) {
       inARace = true;
       break;
       }
       }
       if (inARace) {
       log.debug("inARace == true (conflict == their new piece)");
       //brain.getClient().sendText(game, "If you build your settlement, it'll prevent me from building mine.");
       }
       }
       }
       if (!inARace) {
       log.debug("-- ourOriginalWGETA: "+ourOriginalWGETA);
       log.debug("--      ourNewWGETA: "+ourNewWGETA);
       log.debug("-- theirOriginalWGETA: "+theirOriginalWGETA);
       log.debug("--      theirNewWGETA: "+theirNewWGETA);
       log.debug("--  ourOriginalPieceType: "+ourOriginalPieceType);
       log.debug("--       ourNewPieceType: "+ourNewPieceType);
       log.debug("--   ourOriginalPieceETA: "+ourOriginalPieceETA);
       log.debug("--        ourNewPieceETA: "+ourNewPieceETA);
       log.debug("-- ourOriginalPieceScore: "+ourOriginalPieceScore);
       log.debug("--      ourNewPieceScore: "+ourNewPieceScore);
       log.debug("-- ourOriginalFavoriteSettlementETA: "+ourOriginalFavoriteSettlementETA);
       log.debug("--       ourOriginalFavoriteCityETA: "+ourOriginalFavoriteCityETA);
       log.debug("--       ourOriginalFavoriteRoadETA: "+ourOriginalFavoriteRoadETA);
       log.debug("--       ourOriginalPossibleCardETA: "+ourOriginalPossibleCardETA);
       log.debug("--                            total: "+(ourOriginalFavoriteSettlementETA
       + ourOriginalFavoriteCityETA + ourOriginalFavoriteRoadETA + ourOriginalPossibleCardETA));
       log.debug("-- ourNewFavoriteSettlementETA: "+ourNewFavoriteSettlementETA);
       log.debug("--       ourNewFavoriteCityETA: "+ourNewFavoriteCityETA);
       log.debug("--       ourNewFavoriteRoadETA: "+ourNewFavoriteRoadETA);
       log.debug("--       ourNewPossibleCardETA: "+ourNewPossibleCardETA);
       log.debug("--                            total: "+(ourNewFavoriteSettlementETA
       + ourNewFavoriteCityETA + ourNewFavoriteRoadETA + ourNewPossibleCardETA));
       log.debug("-- ourOriginalFavoriteSettlementScore: "+ourOriginalFavoriteSettlementScore);
       log.debug("--       ourOriginalFavoriteCityScore: "+ourOriginalFavoriteCityScore);
       log.debug("--       ourOriginalFavoriteRoadScore: "+ourOriginalFavoriteRoadScore);
       log.debug("--       ourOriginalPossibleCardScore: "+ourOriginalPossibleCardScore);
       log.debug("--                            total: "+(ourOriginalFavoriteSettlementScore
       + ourOriginalFavoriteCityScore + ourOriginalFavoriteRoadScore + ourOriginalPossibleCardScore));
       log.debug("-- ourNewFavoriteSettlementScore: "+ourNewFavoriteSettlementScore);
       log.debug("--       ourNewFavoriteCityScore: "+ourNewFavoriteCityScore);
       log.debug("--       ourNewFavoriteRoadScore: "+ourNewFavoriteRoadScore);
       log.debug("--       ourNewPossibleCardScore: "+ourNewPossibleCardScore);
       log.debug("--                            total: "+(ourNewFavoriteSettlementScore
       + ourNewFavoriteCityScore + ourNewFavoriteRoadScore + ourNewPossibleCardScore));
    
       //
       // see if we have something to gain from the offer
       //
       if (ourOriginalPieceType == ourNewPieceType) {
       //
       //  things to check if we want to build the
       //  same piece before and after the deal
       //
       if ((ourOriginalPieceETA > ourNewPieceETA) ||
       ((ourOriginalPieceETA == ourNewPieceETA) &&
       (ourOriginalPieceScore < ourNewPieceScore))) {
       response = ACCEPT_OFFER;
       } else {
       response = COUNTER_OFFER;
       //brain.getClient().sendText(game, "That deal's not good for me.");
       }
       } else {
       //
       //  things to check if we changed our
       //  plans based on the deal
       //
       if ((ourOriginalPieceScore < ourNewPieceScore) ||
       ((ourOriginalPieceScore == ourNewPieceScore) &&
       (ourOriginalPieceETA > ourNewPieceETA))) {
       response = ACCEPT_OFFER;
       } else {
       response = COUNTER_OFFER;
       //brain.getClient().sendText(game, "That deal's not good for me.");
       }
       }
     */
    /*
       if (response == ACCEPT_OFFER) {
       //
       //  if the deal allows them to build a piece now,
       //  see if their piece has a higher score than ours
       //
       if ((theirNewPieceETA == 0) &&
       (theirNewPieceScore > ourNewPieceScore)) {
       brain.getClient().sendText(game, "I think you're comming out ahead on that deal.");
       response = REJECT_OFFER;
       }
       }
     */
    /*
       }
       }
       }
       return response;
       }
     */

    /**
     * @return a counter offer or null
     *
     * @param originalOffer  the offer given to us
     */
    public SOCTradeOffer makeCounterOffer(SOCTradeOffer originalOffer)
    {
        log.debug("***** MAKE COUNTER OFFER *****");

        SOCTradeOffer counterOffer = null;

        SOCPossiblePiece targetPiece = targetPieces[ourPlayerData.getPlayerNumber()];

        if (targetPiece == null)
        {
            Stack ourBuildingPlan = buildingPlan;

            if (ourBuildingPlan.empty())
            {
                SOCRobotDM simulator;
                log.debug("**** our building plan is empty ****");
                simulator = new SOCRobotDM(brain.getRobotParameters(), playerTrackers, ourPlayerTracker, ourPlayerData, ourBuildingPlan);
                simulator.planStuff(strategyType);
            }

            if (ourBuildingPlan.empty())
            {
                return counterOffer;
            }

            targetPiece = (SOCPossiblePiece) ourBuildingPlan.peek();
            targetPieces[ourPlayerData.getPlayerNumber()] = targetPiece;
        }

        SOCResourceSet targetResources = null;

        switch (targetPiece.getType())
        {
        case SOCPossiblePiece.CARD:
            targetResources = SOCGame.CARD_SET;

            break;

        case SOCPossiblePiece.ROAD:
            targetResources = SOCGame.ROAD_SET;

            break;

        case SOCPossiblePiece.SETTLEMENT:
            targetResources = SOCGame.SETTLEMENT_SET;

            break;

        case SOCPossiblePiece.CITY:
            targetResources = SOCGame.CITY_SET;

            break;
        }

        SOCResourceSet ourResources = ourPlayerData.getResources();

        log.debug("*** targetResources = " + targetResources);
        log.debug("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return counterOffer;
        }

        if (ourResources.getAmount(SOCResourceConstants.UNKNOWN) > 0)
        {
            log.debug("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return counterOffer;
        }

        SOCTradeOffer batna = getOfferToBank(targetResources);
        log.debug("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        log.debug("*** batnaBuildingTime = " + batnaBuildingTime);

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        ///
        int[] rollsPerResource = estimate.getRollsPerResource();
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //log.debug("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (log.isDebugEnabled())
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                log.debug("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //log.debug("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (log.isDebugEnabled())
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                log.debug("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
            }
        }

        ///
        /// figure out which resources we don't have enough of
        /// that the offering player is selling
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while ((getRsrcIdx >= 0) && ((ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx])) || (originalOffer.getGiveSet().getAmount(neededRsrc[getRsrcIdx]) == 0)))
        {
            getRsrcIdx--;
        }

        ///
        /// if getRsrcIdx < 0 then we've asked for everything
        /// we need and the offering player isn't selling it
        ///
        if (getRsrcIdx >= 0)
        {
            log.debug("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            log.debug("*** counterOffer should be null : counterOffer = " + counterOffer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (counterOffer == null))
            {
                log.debug("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) > 0)
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    log.debug("*** counterOffer = " + counterOffer);
                }

                giveRsrcIdx++;
            }

            log.debug("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                {
                    log.debug("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    log.debug("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

                    if ((ourResources.getAmount(neededRsrc[giveRsrcIdx1]) > targetResources.getAmount(neededRsrc[giveRsrcIdx1])) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        giveResourceSet.clear();
                        giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                        ///
                        /// make sure the offer is better than our BATNA
                        ///
                        int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                        if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                        {
                            counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                            log.debug("*** counterOffer = " + counterOffer);
                            log.debug("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            log.debug("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                {
                    if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0)
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (counterOffer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (ourResources.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (counterOffer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        log.debug("*** counterOffer = " + counterOffer);
                                        log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }

                giveRsrcIdx1 = 0;
                giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                {
                    if ((leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
                    {
                        while ((giveRsrcIdx2 < notNeededRsrcCount) && (counterOffer == null))
                        {
                            giveResourceSet.clear();
                            giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                            giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx2]);

                            if (leftovers.contains(giveResourceSet))
                            {
                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            giveRsrcIdx2++;
                        }

                        giveRsrcIdx2 = 0;

                        while ((giveRsrcIdx2 < neededRsrcCount) && (counterOffer == null))
                        {
                            if (neededRsrc[giveRsrcIdx2] != neededRsrc[getRsrcIdx])
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx2]);

                                if (leftovers.contains(giveResourceSet))
                                {
                                    ///
                                    /// make sure the offer is better than our BATNA
                                    ///
                                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                    if ((offerBuildingTime < batnaBuildingTime) || ((batna != null) && (offerBuildingTime == batnaBuildingTime) && (giveResourceSet.getTotal() < batna.getGiveSet().getTotal())))
                                    {
                                        counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                                        log.debug("*** counterOffer = " + counterOffer);
                                        log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                    }
                                }
                            }

                            giveRsrcIdx2++;
                        }
                    }

                    giveRsrcIdx1++;
                }
            }
        }

        ///
        /// consider offers where we give one for one unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(1, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(1, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(1, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        ///
        /// consider offers where we give one for two unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(2, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(2, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(2, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        ///
        /// consider offers where we give one for three unneeded we 
        /// we can use at a bank or port
        ///
        if (counterOffer == null)
        {
            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            log.debug("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && (originalOffer.getGiveSet().getAmount(notNeededRsrc[getRsrcIdx2]) == 0))
            {
                getRsrcIdx2--;
            }

            while ((getRsrcIdx2 >= 0) && (counterOffer == null))
            {
                getResourceSet.clear();
                getResourceSet.add(3, notNeededRsrc[getRsrcIdx2]);
                leftovers.add(3, notNeededRsrc[getRsrcIdx2]);

                ///
                /// give one unneeded 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                    {
                        if ((leftovers.getAmount(notNeededRsrc[giveRsrcIdx1]) > 0) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
                        {
                            leftovers.subtract(1, notNeededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                    log.debug("*** offerBuildingTime = " + offerBuildingTime);
                                }
                            }

                            leftovers.add(1, notNeededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                ///
                /// give one needed 
                ///
                if (counterOffer == null)
                {
                    int giveRsrcIdx1 = 0;

                    while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                    {
                        if (leftovers.getAmount(neededRsrc[giveRsrcIdx1]) > 0)
                        {
                            leftovers.subtract(1, neededRsrc[giveRsrcIdx1]);

                            if (getOfferToBank(targetResources, leftovers) != null)
                            {
                                giveResourceSet.clear();
                                giveResourceSet.add(1, neededRsrc[giveRsrcIdx1]);

                                ///
                                /// make sure the offer is better than our BATNA
                                ///
                                int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

                                if (offerBuildingTime < batnaBuildingTime)
                                {
                                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, notNeededRsrc[getRsrcIdx2]);
                                    log.debug("*** counterOffer = " + counterOffer);
                                }
                            }

                            leftovers.add(1, neededRsrc[giveRsrcIdx1]);
                        }

                        giveRsrcIdx1++;
                    }
                }

                leftovers.subtract(3, notNeededRsrc[getRsrcIdx2]);
                getRsrcIdx2--;
            }
        }

        return counterOffer;
    }

    /**
     * @return the offer that we'll make to the bank/ports
     *
     * @param targetResources  what resources we want
     * @param ourResources     the resources we have
     */
    public SOCTradeOffer getOfferToBank(SOCResourceSet targetResources, SOCResourceSet ourResources)
    {
        SOCTradeOffer bankTrade = null;

        if (ourResources.contains(targetResources))
        {
            return bankTrade;
        }

        SOCBuildingSpeedEstimate estimate = new SOCBuildingSpeedEstimate(ourPlayerData.getNumbers());
        int[] rollsPerResource = estimate.getRollsPerResource();
        boolean[] ports = ourPlayerData.getPortFlags();

        /**
         * do any possible trading with the bank/ports
         */

        ///
        /// Seperate resource types into needed and not-needed.  Sort
        /// groups by frequency, most to least.  Start with most frequent 
        /// not-needed resources.  Trade for least frequent needed resources.
        /// Loop until freq. of give resource + thresh >= get resource freq.
        /// and there is not enough of that resource to trade after 
        /// subtracting needed ammount.
        ///
        int[] neededRsrc = new int[5];
        int[] notNeededRsrc = new int[5];
        int neededRsrcCount = 0;
        int notNeededRsrcCount = 0;

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            if (targetResources.getAmount(rsrcType) > 0)
            {
                neededRsrc[neededRsrcCount] = rsrcType;
                neededRsrcCount++;
            }
            else
            {
                notNeededRsrc[notNeededRsrcCount] = rsrcType;
                notNeededRsrcCount++;
            }
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //log.debug("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < neededRsrcCount; i++) {
           //log.debug("NEEDED RSRC: "+neededRsrc[i]+" : "+rollsPerResource[neededRsrc[i]]);
           }
         */
        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //log.debug("j="+j+" i="+i);
                //log.debug("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //log.debug("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < notNeededRsrcCount; i++) {
           //log.debug("NOT-NEEDED RSRC: "+notNeededRsrc[i]+" : "+rollsPerResource[notNeededRsrc[i]]);
           }
         */

        ///
        /// figure out which resources we don't have enough of
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while (ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx]))
        {
            getRsrcIdx--;
        }

        int giveRsrcIdx = 0;

        while (giveRsrcIdx < notNeededRsrcCount)
        {
            ///
            /// find the ratio at which we can trade
            ///
            int tradeRatio;

            if (ports[notNeededRsrc[giveRsrcIdx]])
            {
                tradeRatio = 2;
            }
            else if (ports[SOCBoard.MISC_PORT])
            {
                tradeRatio = 3;
            }
            else
            {
                tradeRatio = 4;
            }

            if (ourResources.getAmount(notNeededRsrc[giveRsrcIdx]) >= tradeRatio)
            {
                ///
                /// make the trade
                ///
                SOCResourceSet give = new SOCResourceSet();
                SOCResourceSet get = new SOCResourceSet();
                give.add(tradeRatio, notNeededRsrc[giveRsrcIdx]);
                get.add(1, neededRsrc[getRsrcIdx]);

                //log.debug("our resources: "+ourPlayerData.getResources());
                //log.debug("Making bank trade:");
                //log.debug("give: "+give);
                //log.debug("get: "+get);
                boolean[] to = new boolean[SOCGame.MAXPLAYERS];

                for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                {
                    to[i] = false;
                }

                bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                return bankTrade;
            }
            else
            {
                giveRsrcIdx++;
            }
        }

        ///
        /// Can't trade not-needed resources.
        /// Try trading needed resources.
        ///
        giveRsrcIdx = 0;

        while (giveRsrcIdx < neededRsrcCount)
        {
            ///
            /// find the ratio at which we can trade
            ///
            int tradeRatio;

            if (ports[neededRsrc[giveRsrcIdx]])
            {
                tradeRatio = 2;
            }
            else if (ports[SOCBoard.MISC_PORT])
            {
                tradeRatio = 3;
            }
            else
            {
                tradeRatio = 4;
            }

            if (rollsPerResource[neededRsrc[giveRsrcIdx]] >= rollsPerResource[neededRsrc[getRsrcIdx]])
            {
                ///
                /// Don't want to trade unless we have extra of this resource
                ///
                if ((ourResources.getAmount(neededRsrc[giveRsrcIdx]) - targetResources.getAmount(neededRsrc[giveRsrcIdx])) >= tradeRatio)
                {
                    ///
                    /// make the trade
                    ///
                    SOCResourceSet give = new SOCResourceSet();
                    SOCResourceSet get = new SOCResourceSet();
                    give.add(tradeRatio, neededRsrc[giveRsrcIdx]);
                    get.add(1, neededRsrc[getRsrcIdx]);

                    //log.debug("our resources: "+ourPlayerData.getResources());
                    //log.debug("Making bank trade:");
                    //log.debug("give: "+give);
                    //log.debug("get: "+get);
                    boolean[] to = new boolean[SOCGame.MAXPLAYERS];

                    for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                    return bankTrade;
                }
            }
            else
            {
                ///
                /// We can trade this even though we need it because 
                /// we're betting that we'll get it by our next turn
                ///
                if (ourResources.getAmount(neededRsrc[giveRsrcIdx]) >= tradeRatio)
                {
                    ///
                    /// make the trade
                    ///
                    SOCResourceSet give = new SOCResourceSet();
                    SOCResourceSet get = new SOCResourceSet();
                    give.add(tradeRatio, neededRsrc[giveRsrcIdx]);
                    get.add(1, neededRsrc[getRsrcIdx]);

                    //log.debug("our resources: "+ourPlayerData.getResources());
                    //log.debug("Making bank trade:");
                    //log.debug("give: "+give);
                    //log.debug("get: "+get);
                    boolean[] to = new boolean[SOCGame.MAXPLAYERS];

                    for (int i = 0; i < SOCGame.MAXPLAYERS; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerData.getPlayerNumber(), to, give, get);

                    return bankTrade;
                }
            }

            giveRsrcIdx++;
        }

        return bankTrade;
    }

    /**
     * @return the offer that we'll make to the bank/ports
     *
     * @param targetResources  what resources we want
     */
    public SOCTradeOffer getOfferToBank(SOCResourceSet targetResources)
    {
        return getOfferToBank(targetResources, ourPlayerData.getResources());
    }
}
