/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009,2011-2013,2015,2017-2018,2020-2022 Jeremy D Monin <jeremy@nand.net>
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

import soc.disableDebug.D;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;

import java.util.Iterator;
import java.util.Vector;


/**
 * Make and consider resource trade offers ({@link SOCTradeOffer}) with other players.
 *<P>
 * Chooses a response:
 *<UL>
 * <LI> {@link #IGNORE_OFFER}
 * <LI> {@link #REJECT_OFFER}
 * <LI> {@link #ACCEPT_OFFER}
 * <LI> {@link #COUNTER_OFFER}
 *</UL>
 *<P>
 * Moved the routines that make and
 * consider offers out of the robot
 * brain.
 *
 * @author Robert S. Thomas
 */
public class SOCRobotNegotiator
{
    protected static final int WIN_GAME_CUTOFF = 25;

    /**
     * Response: Ignore an offer. Should be used only if we aren't
     * among the offer's recipients from {@link SOCTradeOffer#getTo()}.
     * If the offer is meant for us, the offering player is waiting for
     * our response and ignoring it will delay the game.
     * @since 2.0.00
     */
    public static final int IGNORE_OFFER = -1;

    /** Response: Reject an offer. */
    public static final int REJECT_OFFER = 0;

    /** Response: Accept an offer. */
    public static final int ACCEPT_OFFER = 1;

    /** Response: Plan and make a counter-offer if possible, otherwise reject. */
    public static final int COUNTER_OFFER = 2;

    protected SOCRobotBrain brain;
    protected int strategyType;
    protected SOCGame game;

    /**
     * {@link #ourPlayerData}'s building plan.
     *<P>
     * Before v2.5.00 this was an unencapsulated Stack of {@link SOCPossiblePiece}.
     */
    protected SOCBuildPlanStack buildingPlan;

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

    protected SOCRobotDM decisionMaker;
    protected boolean[][] isSellingResource;
    protected boolean[][] wantsAnotherOffer;
    protected Vector<SOCTradeOffer> offersMade;
    protected SOCPossiblePiece[] targetPieces;

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
        ourPlayerNumber = ourPlayerData.getPlayerNumber();
        buildingPlan = brain.getBuildingPlan();
        decisionMaker = brain.getDecisionMaker();
        game = brain.getGame();

        isSellingResource = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetIsSelling();

        wantsAnotherOffer = new boolean[game.maxPlayers][SOCResourceConstants.MAXPLUSONE];
        resetWantsAnotherOffer();

        offersMade = new Vector<SOCTradeOffer>();

        targetPieces = new SOCPossiblePiece[game.maxPlayers];
        resetTargetPieces();
    }

    /**
     * reset target pieces for all players
     */
    public void resetTargetPieces()
    {
        D.ebugPrintlnINFO("*** resetTargetPieces ***");

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            targetPieces[pn] = null;
        }
    }

    /**
     * set a target piece for a player based on info from their build plan.
     * This implementation uses only {@link SOCBuildPlan#getFirstPiece()},
     * but a third-party bot could override to use more pieces if the plan has them.
     *
     * @param pn  the player number
     * @param buildPlan  the build plan; can be empty or null
     * @see #setTargetPiece(int, SOCPossiblePiece)
     * @since 2.6.00
     */
    public void setTargetPiece(int pn, SOCBuildPlan buildPlan)
    {
        setTargetPiece(pn, ((buildPlan != null) && ! buildPlan.isEmpty()) ? buildPlan.getFirstPiece() : null);
    }

    /**
     * set a target piece for a player
     *
     * @param pn  the player number
     * @param piece  the piece that they want to build next, or null if none
     * @see #setTargetPiece(int, SOCBuildPlan)
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
     * @param offer  the offer, or null. Null won't be added to list.
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
        D.ebugPrintlnINFO("*** resetIsSelling (true for every resource the player has) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if (( ! game.isSeatVacant(pn)) &&
                    game.getPlayer(pn).getResources().contains(rsrcType))
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
        D.ebugPrintlnINFO("*** resetWantsAnotherOffer (all false) ***");

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD; rsrcType++)
        {
            for (int pn = 0; pn < game.maxPlayers; pn++)
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
        D.ebugPrintlnINFO("*** markAsNotSelling pn=" + pn + " rsrcType=" + rsrcType);
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
        D.ebugPrintlnINFO("*** markAsSelling pn=" + pn + " rsrcType=" + rsrcType);
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
        D.ebugPrintlnINFO("*** markAsNotWantingAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
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
        D.ebugPrintlnINFO("*** markAsWantsAnotherOffer pn=" + pn + " rsrcType=" + rsrcType);
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
     * Make an trade offer to another player, or decide to make no offer,
     * based on what we want to build and our player's current {@link SOCPlayer#getResources()}.
     * Checks {@link SOCBuildPlan#getFirstPieceResources()}.
     *<P>
     * Before v2.5.00 this method took a {@link SOCPossiblePiece} instead of a {@link SOCBuildPlan}.
     *
     * @param buildPlan  our build plan, or {@code null} or empty
     * @return the offer we want to make, or {@code null} for no offer
     * @see #getOfferToBank(SOCBuildPlan, SOCResourceSet)
     */
    public SOCTradeOffer makeOffer(SOCBuildPlan buildPlan)
    {
        D.ebugPrintlnINFO("***** MAKE OFFER *****");

        if ((buildPlan == null) || buildPlan.isEmpty())
        {
            return null;
        }

        SOCTradeOffer offer = null;

        SOCResourceSet targetResources = buildPlan.getFirstPieceResources();
        if (targetResources.isEmpty())
            return null;

        SOCResourceSet ourResources = ourPlayerData.getResources();

        D.ebugPrintlnINFO("*** targetResources = " + targetResources);
        D.ebugPrintlnINFO("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return offer;
        }

        if (ourResources.contains(SOCResourceConstants.UNKNOWN))
        {
            D.ebugPrintlnINFO("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return offer;
        }

        SOCTradeOffer batna = getOfferToBank(targetResources);
        D.ebugPrintlnINFO("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

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
            if (targetResources.contains(rsrcType))
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
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
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

            for (int pn = 0; pn < game.maxPlayers; pn++)
            {
                if ((pn != ourPlayerNumber) && (isSellingResource[pn][rsrcType]))
                {
                    someoneIsSellingResource[rsrcType] = true;
                    D.ebugPrintlnINFO("*** player " + pn + " is selling " + rsrcType);

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
            D.ebugPrintlnINFO("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            D.ebugPrintlnINFO("*** offer should be null : offer = " + offer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (offer == null))
            {
                D.ebugPrintlnINFO("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.contains(notNeededRsrc[giveRsrcIdx]))
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    offer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    D.ebugPrintlnINFO("*** offer = " + offer);

                    int offerBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);
                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                }

                giveRsrcIdx++;
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (offer == null))
                {
                    D.ebugPrintlnINFO("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    D.ebugPrintlnINFO("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

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
                            D.ebugPrintlnINFO("*** offer = " + offer);
                            D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (offer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (offer == null))
                {
                    if (ourResources.contains(notNeededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                                        D.ebugPrintlnINFO("*** offer = " + offer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                    if (leftovers.contains(neededRsrc[giveRsrcIdx1]) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
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
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                                        D.ebugPrintlnINFO("*** offer = " + offer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

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
                        if (leftovers.contains(notNeededRsrc[giveRsrcIdx1]) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
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
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                        if (leftovers.contains(neededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** offer = " + offer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
        D.ebugPrintlnINFO("**** makeOfferAux ****");
        D.ebugPrintlnINFO("giveResourceSet = " + giveResourceSet);
        D.ebugPrintlnINFO("getResourceSet = " + getResourceSet);

        SOCTradeOffer offer = null;

        ///
        /// see if we've made this offer before
        ///
        boolean match = false;
        Iterator<SOCTradeOffer> offersMadeIter = offersMade.iterator();

        while ((offersMadeIter.hasNext() && !match))
        {
            SOCTradeOffer pastOffer = offersMadeIter.next();

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
            for (int i = 0; i < game.maxPlayers; i++)
            {
                if (i != ourPlayerNumber)
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

        D.ebugPrintlnINFO("*** match = " + match);

        if (!match)
        {
            ///
            /// this is a new offer
            ///
            D.ebugPrintlnINFO("* this is a new offer");

            int numOfferedTo = 0;
            boolean[] offeredTo = new boolean[game.maxPlayers];

            ///
            /// if it's our turn
            ///
            if (game.getCurrentPlayerNumber() == ourPlayerNumber)
            {
                ///
                /// only offer to players that are selling what we're asking for
                /// and aren't too close to winning
                ///
                for (int i = 0; i < game.maxPlayers; i++)
                {
                    D.ebugPrintlnINFO("** isSellingResource[" + i + "][" + neededResource + "] = " + isSellingResource[i][neededResource]);

                    if ((i != ourPlayerNumber)
                        && isSellingResource[i][neededResource] &&
                        (! game.isSeatVacant(i)) &&
                        (game.getPlayer(i).getResources().getTotal() >= getResourceSet.getTotal()))
                    {
                        final SOCPlayerTracker tracker = playerTrackers[i];

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
                final int curpn = game.getCurrentPlayerNumber();

                if (isSellingResource[curpn][neededResource] && (game.getPlayer(curpn).getResources().getTotal() >= getResourceSet.getTotal()))
                {
                    D.ebugPrintlnINFO("** isSellingResource[" + curpn + "][" + neededResource + "] = " + isSellingResource[curpn][neededResource]);

                    final SOCPlayerTracker tracker = playerTrackers[curpn];

                    if ((tracker != null) && (tracker.getWinGameETA() >= WIN_GAME_CUTOFF))
                    {
                        numOfferedTo++;
                        offeredTo[curpn] = true;
                    }
                }
            }

            D.ebugPrintlnINFO("** numOfferedTo = " + numOfferedTo);

            if (numOfferedTo > 0)
            {
                ///
                ///  the offer
                ///
                offer = new SOCTradeOffer(game.getName(), ourPlayerNumber, offeredTo, giveResourceSet, getResourceSet);

                ///
                /// only make the offer if we think someone will take it
                ///
                boolean acceptable = false;

                for (int pn = 0; pn < game.maxPlayers; pn++)
                {
                    if (offeredTo[pn])
                    {
                        int offerResponse = considerOffer2(offer, pn);
                        D.ebugPrintlnINFO("* considerOffer2(offer, " + pn + ") = " + offerResponse);

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
    protected int getETAToTargetResources
        (SOCPlayer player, SOCResourceSet targetResources, SOCResourceSet giveSet, SOCResourceSet getSet,
         SOCBuildingSpeedEstimate estimate)
    {
        SOCResourceSet ourResourcesCopy = player.getResources().copy();
        D.ebugPrintlnINFO("*** giveSet = " + giveSet);
        D.ebugPrintlnINFO("*** getSet = " + getSet);
        ourResourcesCopy.subtract(giveSet);
        ourResourcesCopy.add(getSet);

        final int offerBuildingTime =
            estimate.calculateRollsFast(ourResourcesCopy, targetResources, 1000, player.getPortFlags());

        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
        D.ebugPrintlnINFO("*** ourResourcesCopy = " + ourResourcesCopy);

        return (offerBuildingTime);
    }

    /**
     * consider an offer made by another player
     *
     * @param offer  the offer to consider
     * @param receiverNum  the player number of the receiver
     *
     * @return if we want to accept, reject, or make a counter offer
     *     ( {@link #ACCEPT_OFFER}, {@link #REJECT_OFFER}, or {@link #COUNTER_OFFER} )
     */
    public int considerOffer2(SOCTradeOffer offer, final int receiverNum)
    {
        ///
        /// This version should be faster
        ///
        D.ebugPrintlnINFO("***** CONSIDER OFFER 2 *****");

        int response = REJECT_OFFER;

        SOCPlayer receiverPlayerData = game.getPlayer(receiverNum);
        SOCResourceSet receiverResources = receiverPlayerData.getResources();

        SOCResourceSet rsrcsOut = offer.getGetSet();
        SOCResourceSet rsrcsIn = offer.getGiveSet();

        //
        // if the receiver doesn't have what's asked for, they'll reject
        //
        if (! (receiverResources.contains(SOCResourceConstants.UNKNOWN) || receiverResources.contains(rsrcsOut)) )
        {
            return response;
        }

        final int senderNum = offer.getFrom();

        D.ebugPrintlnINFO("senderNum = " + senderNum);
        D.ebugPrintlnINFO("receiverNum = " + receiverNum);
        D.ebugPrintlnINFO("rsrcs from receiver = " + rsrcsOut);
        D.ebugPrintlnINFO("rsrcs to receiver = " + rsrcsIn);

        SOCPossiblePiece receiverTargetPiece = targetPieces[receiverNum];

        D.ebugPrintlnINFO("targetPieces[" + receiverNum + "] = " + receiverTargetPiece);

        SOCPlayerTracker receiverPlayerTracker = playerTrackers[receiverNum];

        if (receiverPlayerTracker == null)
        {
            return response;
        }

        SOCPlayerTracker senderPlayerTracker = playerTrackers[senderNum];

        if (senderPlayerTracker == null)
        {
            return response;
        }

        SOCRobotDM simulator;

        if (receiverTargetPiece == null)
        {
            SOCBuildPlanStack receiverBuildingPlan = new SOCBuildPlanStack();
            simulator = new SOCRobotDM
                (brain.getRobotParameters(), brain.openingBuildStrategy, brain.getEstimatorFactory(),
                 playerTrackers, receiverPlayerTracker, receiverPlayerData, receiverBuildingPlan);

            if (receiverNum == ourPlayerNumber)
            {
                simulator.planStuff(strategyType);
            }
            else
            {
                simulator.planStuff(strategyType);
            }

            if (receiverBuildingPlan.isEmpty())
            {
                return response;
            }

            receiverTargetPiece = receiverBuildingPlan.getFirstPiece();
            targetPieces[receiverNum] = receiverTargetPiece;
        }

        D.ebugPrintlnINFO("receiverTargetPiece = " + receiverTargetPiece);

        SOCPossiblePiece senderTargetPiece = targetPieces[senderNum];

        D.ebugPrintlnINFO("targetPieces[" + senderNum + "] = " + senderTargetPiece);

        SOCPlayer senderPlayerData = game.getPlayer(senderNum);

        if (senderTargetPiece == null)
        {
            SOCBuildPlanStack senderBuildingPlan = new SOCBuildPlanStack();
            simulator = new SOCRobotDM
                (brain.getRobotParameters(), brain.openingBuildStrategy, brain.getEstimatorFactory(),
                 playerTrackers, senderPlayerTracker, senderPlayerData, senderBuildingPlan);

            if (senderNum == ourPlayerNumber)
            {
                simulator.planStuff(strategyType);
            }
            else
            {
                simulator.planStuff(strategyType);
            }

            if (senderBuildingPlan.isEmpty())
            {
                return response;
            }

            senderTargetPiece = senderBuildingPlan.getFirstPiece();
            targetPieces[senderNum] = senderTargetPiece;
        }

        D.ebugPrintlnINFO("senderTargetPiece = " + senderTargetPiece);

        int senderWGETA = senderPlayerTracker.getWinGameETA();

        if (senderWGETA > WIN_GAME_CUTOFF)
        {
            //
            //  see if the sender is in a race with the receiver
            //
            boolean inARace = false;

            if ((receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT) || (receiverTargetPiece.getType() == SOCPossiblePiece.ROAD))
            {
                for (SOCPossiblePiece threat : receiverTargetPiece.getThreats())
                {
                    if ((threat.getType() == senderTargetPiece.getType()) && (threat.getCoordinates() == senderTargetPiece.getCoordinates()))
                    {
                        inARace = true;

                        break;
                    }
                }

                if (inARace)
                {
                    D.ebugPrintlnINFO("inARace == true (threat from sender)");
                }
                else if (receiverTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT)
                {
                    for (SOCPossibleSettlement conflict : ((SOCPossibleSettlement) receiverTargetPiece).getConflicts())
                    {
                        if ((senderTargetPiece.getType() == SOCPossiblePiece.SETTLEMENT)
                            && (conflict.getCoordinates() == senderTargetPiece.getCoordinates()))
                        {
                            inARace = true;

                            break;
                        }
                    }

                    if (inARace)
                    {
                        D.ebugPrintlnINFO("inARace == true (conflict with sender)");
                    }
                }
            }

            if (! inARace)
            {
                ///
                /// see if this is good for the receiver
                ///
                SOCResourceSet targetResources = receiverTargetPiece.getResourcesToBuild();
                if (targetResources == null)
                    return REJECT_OFFER;

                SOCBuildingSpeedEstimate estimate = brain.getEstimator(receiverPlayerData.getNumbers());

                if (D.ebugIsEnabled())
                {
                    SOCTradeOffer receiverBatna = getOfferToBank(targetResources);
                    D.ebugPrintlnINFO("*** receiverBatna = " + receiverBatna);
                }

                int batnaBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET, estimate);

                D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

                int offerBuildingTime = getETAToTargetResources(receiverPlayerData, targetResources, rsrcsOut, rsrcsIn, estimate);

                D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);

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
       D.ebugPrintlnINFO("***** CONSIDER OFFER *****");
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
       if (ourBuildingPlan.isEmpty()) {
       D.ebugPrintlnINFO("**** our building plan is empty ****");
       simulator = new SOCRobotDM(brain.getRobotParameters(),
       playerTrackers,
       ourPlayerTracker,
       ourPlayerData,
       ourBuildingPlan);
       simulator.planStuff();
       }

       if (ourBuildingPlan.isEmpty()) {
       return response;
       }
       SOCPossiblePiece targetPiece = (SOCPossiblePiece)ourBuildingPlan.peek();
       ourOriginalFavoriteSettlement = decisionMaker.getFavoriteSettlement();
       ourOriginalFavoriteCity = decisionMaker.getFavoriteCity();
       ourOriginalFavoriteRoad = decisionMaker.getFavoriteRoad();
       ourOriginalPossibleCard = decisionMaker.getPossibleCard();
       SOCPlayerTracker theirPlayerTracker = (SOCPlayerTracker)playerTrackers.get(Integer.valueOf(offer.getFrom()));

       if (theirPlayerTracker != null) {
       theirOriginalWGETA = theirPlayerTracker.getWinGameETA();
       D.ebugPrintlnINFO("CHECKING OFFER FROM PLAYER "+offer.getFrom());
       D.ebugPrintlnINFO("they give : "+rsrcsIn);
       D.ebugPrintlnINFO("they get : "+rsrcsOut);

       D.ebugPrintlnINFO("---------< before >-----------");
       ourOriginalPiece = targetPiece;
       ourOriginalPieceType = targetPiece.getType();
       ourOriginalPieceCoord = targetPiece.getCoordinates();
       ourOriginalPieceETA = targetPiece.getETA();
       ourOriginalPieceScore = targetPiece.getScore();
       D.ebugPrintlnINFO("ourResources : "+ourResources);
       D.ebugPrintlnINFO("ourOriginalWGETA = "+ourOriginalWGETA);
       D.ebugPrintlnINFO("our target piece type : "+targetPiece.getType());
       D.ebugPrintlnINFO("our target piece coord : "+Integer.toHexString(targetPiece.getCoordinates()));
       D.ebugPrintlnINFO("our target piece eta : "+targetPiece.getETA());
       D.ebugPrintlnINFO("our target piece score : "+targetPiece.getScore());

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
       D.ebugPrintlnINFO("theirResources : "+theirResources);
       D.ebugPrintlnINFO("theirOriginalWGETA = "+theirOriginalWGETA);
       if (theirOriginalPiece != null) {
       D.ebugPrintlnINFO("their target piece type : "+theirOriginalPiece.getType());
       D.ebugPrintlnINFO("their target piece coord : "+Integer.toHexString(theirOriginalPiece.getCoordinates()));
       D.ebugPrintlnINFO("their target piece eta : "+theirOriginalPiece.getETA());
       D.ebugPrintlnINFO("their target piece score : "+theirOriginalPiece.getScore());
       } else {
       D.ebugPrintlnINFO("their target piece == null");
       }

       theirResources.add(rsrcsOut);
       theirResources.subtract(rsrcsIn);
       ourResources.add(rsrcsIn);
       ourResources.subtract(rsrcsOut);

       D.ebugPrintlnINFO("---------< after >-----------");

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

       D.ebugPrintlnINFO("ourResources : "+ourResources);
       D.ebugPrintlnINFO("ourNewWGETA = "+ourNewWGETA);
       if (ourNewTargetPiece != null) {
       D.ebugPrintlnINFO("our target piece type : "+ourNewTargetPiece.getType());
       D.ebugPrintlnINFO("our target piece coord : "+Integer.toHexString(ourNewTargetPiece.getCoordinates()));
       D.ebugPrintlnINFO("our target piece eta : "+ourNewTargetPiece.getETA());
       D.ebugPrintlnINFO("our target piece score : "+ourNewTargetPiece.getScore());
       } else {
       D.ebugPrintlnINFO("our target piece == null");
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
       D.ebugPrintlnINFO("theirResources : "+theirResources);
       D.ebugPrintlnINFO("theirNewWGETA = "+theirNewWGETA);
       if (theirNewTargetPiece != null) {
       D.ebugPrintlnINFO("their target piece type : "+theirNewTargetPiece.getType());
       D.ebugPrintlnINFO("their target piece coord : "+Integer.toHexString(theirNewTargetPiece.getCoordinates()));
       D.ebugPrintlnINFO("their target piece eta : "+theirNewTargetPiece.getETA());
       D.ebugPrintlnINFO("their target piece score : "+theirNewTargetPiece.getScore());
       } else {
       D.ebugPrintlnINFO("their target piece == null");
       }

       D.ebugPrintlnINFO("---------< cleanup >-----------");

       theirResources.subtract(rsrcsOut);
       theirResources.add(rsrcsIn);
       ourResources.subtract(rsrcsIn);
       ourResources.add(rsrcsOut);

       SOCPlayerTracker.updateWinGameETAs(playerTrackers);

       D.ebugPrintlnINFO("ourResources : "+ourResources);
       D.ebugPrintlnINFO("theirResources : "+theirResources);

       D.ebugPrintlnINFO("---------< done >-----------");
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
       D.ebugPrintlnINFO("inARace == true (threat == their new piece)");
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
       D.ebugPrintlnINFO("inARace == true (conflict == their new piece)");
       //brain.getClient().sendText(game, "If you build your settlement, it'll prevent me from building mine.");
       }
       }
       }
       if (!inARace) {
       D.ebugPrintlnINFO("-- ourOriginalWGETA: "+ourOriginalWGETA);
       D.ebugPrintlnINFO("--      ourNewWGETA: "+ourNewWGETA);
       D.ebugPrintlnINFO("-- theirOriginalWGETA: "+theirOriginalWGETA);
       D.ebugPrintlnINFO("--      theirNewWGETA: "+theirNewWGETA);
       D.ebugPrintlnINFO("--  ourOriginalPieceType: "+ourOriginalPieceType);
       D.ebugPrintlnINFO("--       ourNewPieceType: "+ourNewPieceType);
       D.ebugPrintlnINFO("--   ourOriginalPieceETA: "+ourOriginalPieceETA);
       D.ebugPrintlnINFO("--        ourNewPieceETA: "+ourNewPieceETA);
       D.ebugPrintlnINFO("-- ourOriginalPieceScore: "+ourOriginalPieceScore);
       D.ebugPrintlnINFO("--      ourNewPieceScore: "+ourNewPieceScore);
       D.ebugPrintlnINFO("-- ourOriginalFavoriteSettlementETA: "+ourOriginalFavoriteSettlementETA);
       D.ebugPrintlnINFO("--       ourOriginalFavoriteCityETA: "+ourOriginalFavoriteCityETA);
       D.ebugPrintlnINFO("--       ourOriginalFavoriteRoadETA: "+ourOriginalFavoriteRoadETA);
       D.ebugPrintlnINFO("--       ourOriginalPossibleCardETA: "+ourOriginalPossibleCardETA);
       D.ebugPrintlnINFO("--                            total: "+(ourOriginalFavoriteSettlementETA
       + ourOriginalFavoriteCityETA + ourOriginalFavoriteRoadETA + ourOriginalPossibleCardETA));
       D.ebugPrintlnINFO("-- ourNewFavoriteSettlementETA: "+ourNewFavoriteSettlementETA);
       D.ebugPrintlnINFO("--       ourNewFavoriteCityETA: "+ourNewFavoriteCityETA);
       D.ebugPrintlnINFO("--       ourNewFavoriteRoadETA: "+ourNewFavoriteRoadETA);
       D.ebugPrintlnINFO("--       ourNewPossibleCardETA: "+ourNewPossibleCardETA);
       D.ebugPrintlnINFO("--                            total: "+(ourNewFavoriteSettlementETA
       + ourNewFavoriteCityETA + ourNewFavoriteRoadETA + ourNewPossibleCardETA));
       D.ebugPrintlnINFO("-- ourOriginalFavoriteSettlementScore: "+ourOriginalFavoriteSettlementScore);
       D.ebugPrintlnINFO("--       ourOriginalFavoriteCityScore: "+ourOriginalFavoriteCityScore);
       D.ebugPrintlnINFO("--       ourOriginalFavoriteRoadScore: "+ourOriginalFavoriteRoadScore);
       D.ebugPrintlnINFO("--       ourOriginalPossibleCardScore: "+ourOriginalPossibleCardScore);
       D.ebugPrintlnINFO("--                            total: "+(ourOriginalFavoriteSettlementScore
       + ourOriginalFavoriteCityScore + ourOriginalFavoriteRoadScore + ourOriginalPossibleCardScore));
       D.ebugPrintlnINFO("-- ourNewFavoriteSettlementScore: "+ourNewFavoriteSettlementScore);
       D.ebugPrintlnINFO("--       ourNewFavoriteCityScore: "+ourNewFavoriteCityScore);
       D.ebugPrintlnINFO("--       ourNewFavoriteRoadScore: "+ourNewFavoriteRoadScore);
       D.ebugPrintlnINFO("--       ourNewPossibleCardScore: "+ourNewPossibleCardScore);
       D.ebugPrintlnINFO("--                            total: "+(ourNewFavoriteSettlementScore
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
        D.ebugPrintlnINFO("***** MAKE COUNTER OFFER *****");

        SOCTradeOffer counterOffer = null;

        SOCPossiblePiece targetPiece = targetPieces[ourPlayerNumber];

        if (targetPiece == null)
        {
            SOCBuildPlanStack ourBuildingPlan = buildingPlan;

            if (ourBuildingPlan.isEmpty())
            {
                SOCRobotDM simulator;
                D.ebugPrintlnINFO("**** our building plan is empty ****");
                simulator = new SOCRobotDM
                    (brain.getRobotParameters(), brain.openingBuildStrategy, brain.getEstimatorFactory(),
                     playerTrackers, ourPlayerTracker, ourPlayerData, ourBuildingPlan);
                simulator.planStuff(strategyType);
            }

            if (ourBuildingPlan.isEmpty())
            {
                return counterOffer;
            }

            targetPiece = ourBuildingPlan.getFirstPiece();
            targetPieces[ourPlayerNumber] = targetPiece;
        }

        SOCResourceSet targetResources = targetPiece.getResourcesToBuild();
        if (targetResources == null)
            return null;

        SOCResourceSet ourResources = ourPlayerData.getResources();

        D.ebugPrintlnINFO("*** targetResources = " + targetResources);
        D.ebugPrintlnINFO("*** ourResources = " + ourResources);

        if (ourResources.contains(targetResources))
        {
            return counterOffer;
        }

        if (ourResources.contains(SOCResourceConstants.UNKNOWN))
        {
            D.ebugPrintlnINFO("AGG WE HAVE UNKNOWN RESOURCES !!!! %%%%%%%%%%%%%%%%%%%%%%%%%%%%");

            return counterOffer;
        }

        SOCTradeOffer batna = getOfferToBank(targetResources);
        D.ebugPrintlnINFO("*** BATNA = " + batna);

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());

        SOCResourceSet giveResourceSet = new SOCResourceSet();
        SOCResourceSet getResourceSet = new SOCResourceSet();

        int batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, giveResourceSet, getResourceSet, estimate);

        if (batna != null)
        {
            batnaBuildingTime = getETAToTargetResources(ourPlayerData, targetResources, batna.getGiveSet(), batna.getGetSet(), estimate);
        }

        D.ebugPrintlnINFO("*** batnaBuildingTime = " + batnaBuildingTime);

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
            if (targetResources.contains(rsrcType))
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
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < neededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NEEDED RSRC: " + neededRsrc[i] + " : " + rollsPerResource[neededRsrc[i]]);
            }
        }

        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        if (D.ebugOn)
        {
            for (int i = 0; i < notNeededRsrcCount; i++)
            {
                D.ebugPrintlnINFO("NOT-NEEDED RSRC: " + notNeededRsrc[i] + " : " + rollsPerResource[notNeededRsrc[i]]);
            }
        }

        ///
        /// figure out which resources we don't have enough of
        /// that the offering player is selling
        ///
        int getRsrcIdx = neededRsrcCount - 1;

        while ((getRsrcIdx >= 0) && ((ourResources.getAmount(neededRsrc[getRsrcIdx]) >= targetResources.getAmount(neededRsrc[getRsrcIdx]))
                                     || ! originalOffer.getGiveSet().contains(neededRsrc[getRsrcIdx])))
        {
            getRsrcIdx--;
        }

        ///
        /// if getRsrcIdx < 0 then we've asked for everything
        /// we need and the offering player isn't selling it
        ///
        if (getRsrcIdx >= 0)
        {
            D.ebugPrintlnINFO("*** getRsrc = " + neededRsrc[getRsrcIdx]);

            getResourceSet.add(1, neededRsrc[getRsrcIdx]);

            D.ebugPrintlnINFO("*** counterOffer should be null : counterOffer = " + counterOffer);

            ///
            /// consider offers where we give one unneeded for one needed
            ///
            int giveRsrcIdx = 0;

            while ((giveRsrcIdx < notNeededRsrcCount) && (counterOffer == null))
            {
                D.ebugPrintlnINFO("*** ourResources.getAmount(" + notNeededRsrc[giveRsrcIdx] + ") = " + ourResources.getAmount(notNeededRsrc[giveRsrcIdx]));

                if (ourResources.contains(notNeededRsrc[giveRsrcIdx]))
                {
                    giveResourceSet.clear();
                    giveResourceSet.add(1, notNeededRsrc[giveRsrcIdx]);
                    counterOffer = makeOfferAux(giveResourceSet, getResourceSet, neededRsrc[getRsrcIdx]);
                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                }

                giveRsrcIdx++;
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            ///
            /// consider offers where we give one needed for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;

                while ((giveRsrcIdx1 < neededRsrcCount) && (counterOffer == null))
                {
                    D.ebugPrintlnINFO("*** ourResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + ourResources.getAmount(neededRsrc[giveRsrcIdx1]));
                    D.ebugPrintlnINFO("*** targetResources.getAmount(" + neededRsrc[giveRsrcIdx1] + ") = " + targetResources.getAmount(neededRsrc[giveRsrcIdx1]));

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
                            D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                            D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
                        }
                    }

                    giveRsrcIdx1++;
                }
            }

            D.ebugPrintlnINFO("*** ourResources = " + ourResources);

            SOCResourceSet leftovers = ourResources.copy();
            leftovers.subtract(targetResources);

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            ///
            /// consider offers where we give two for one needed
            ///
            if (counterOffer == null)
            {
                int giveRsrcIdx1 = 0;
                int giveRsrcIdx2 = 0;

                while ((giveRsrcIdx1 < notNeededRsrcCount) && (counterOffer == null))
                {
                    if (ourResources.contains(notNeededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                                        D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                    if (leftovers.contains(neededRsrc[giveRsrcIdx1]) && (neededRsrc[giveRsrcIdx1] != neededRsrc[getRsrcIdx]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                                        D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                        D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && ! originalOffer.getGiveSet().contains(notNeededRsrc[getRsrcIdx2]))
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
                        if (leftovers.contains(notNeededRsrc[giveRsrcIdx1]) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                        if (leftovers.contains(neededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
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

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && ! originalOffer.getGiveSet().contains(notNeededRsrc[getRsrcIdx2]))
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
                        if (leftovers.contains(notNeededRsrc[giveRsrcIdx1]) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                        if (leftovers.contains(neededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
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

            D.ebugPrintlnINFO("*** leftovers = " + leftovers);

            int getRsrcIdx2 = notNeededRsrcCount - 1;

            while ((getRsrcIdx2 >= 0) && ! originalOffer.getGiveSet().contains(notNeededRsrc[getRsrcIdx2]))
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
                        if (leftovers.contains(notNeededRsrc[giveRsrcIdx1]) && (notNeededRsrc[giveRsrcIdx1] != notNeededRsrc[getRsrcIdx2]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
                                    D.ebugPrintlnINFO("*** offerBuildingTime = " + offerBuildingTime);
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
                        if (leftovers.contains(neededRsrc[giveRsrcIdx1]))
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
                                    D.ebugPrintlnINFO("*** counterOffer = " + counterOffer);
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
     * Decide what bank/port trade to request, if any,
     * based on which resources we want and {@code ourResources}.
     *<P>
     * Other forms of {@code getOfferToBank(..)} call this one;
     * this is the one to override if a third-party bot wants to
     * customize {@code getOfferToBank} behavior.
     *
     * @return the offer that we'll make to the bank/ports,
     *     or {@code null} if {@code ourResources} already contains all needed {@code targetResources}
     *     or {@code targetResources} is null or empty
     * @param targetResources  what resources we want; can be null or empty
     * @param ourResources     the resources we have; not null
     * @see #getOfferToBank(SOCBuildPlan, SOCResourceSet)
     */
    public SOCTradeOffer getOfferToBank(SOCResourceSet targetResources, SOCResourceSet ourResources)
    {
        SOCTradeOffer bankTrade = null;

        if (ourResources.contains(targetResources))
        {
            return bankTrade;
        }

        SOCBuildingSpeedEstimate estimate = brain.getEstimator(ourPlayerData.getNumbers());
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
            if (targetResources.contains(rsrcType))
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

        if (neededRsrcCount == 0)
        {
            return bankTrade;  // <--- Early return bankTrade (null): nothing needed ---
        }

        for (int j = neededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("neededRsrc[i]="+neededRsrc[i]+" "+rollsPerResource[neededRsrc[i]]);
                if (rollsPerResource[neededRsrc[i]] > rollsPerResource[neededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+neededRsrc[i+1]+" "+rollsPerResource[neededRsrc[i+1]]);
                    int tmp = neededRsrc[i];
                    neededRsrc[i] = neededRsrc[i + 1];
                    neededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < neededRsrcCount; i++) {
           //D.ebugPrintln("NEEDED RSRC: "+neededRsrc[i]+" : "+rollsPerResource[neededRsrc[i]]);
           }
         */
        for (int j = notNeededRsrcCount - 1; j >= 0; j--)
        {
            for (int i = 0; i < j; i++)
            {
                //D.ebugPrintln("j="+j+" i="+i);
                //D.ebugPrintln("notNeededRsrc[i]="+notNeededRsrc[i]+" "+rollsPerResource[notNeededRsrc[i]]);
                if (rollsPerResource[notNeededRsrc[i]] > rollsPerResource[notNeededRsrc[i + 1]])
                {
                    //D.ebugPrintln("swap with "+notNeededRsrc[i+1]+" "+rollsPerResource[notNeededRsrc[i+1]]);
                    int tmp = notNeededRsrc[i];
                    notNeededRsrc[i] = notNeededRsrc[i + 1];
                    notNeededRsrc[i + 1] = tmp;
                }
            }
        }

        /*
           for (int i = 0; i < notNeededRsrcCount; i++) {
           //D.ebugPrintln("NOT-NEEDED RSRC: "+notNeededRsrc[i]+" : "+rollsPerResource[notNeededRsrc[i]]);
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

                //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                //D.ebugPrintln("Making bank trade:");
                //D.ebugPrintln("give: "+give);
                //D.ebugPrintln("get: "+get);
                boolean[] to = new boolean[game.maxPlayers];

                for (int i = 0; i < game.maxPlayers; i++)
                {
                    to[i] = false;
                }

                bankTrade = new SOCTradeOffer(game.getName(), ourPlayerNumber, to, give, get);

                return bankTrade;
            }

            giveRsrcIdx++;
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

                    //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                    //D.ebugPrintln("Making bank trade:");
                    //D.ebugPrintln("give: "+give);
                    //D.ebugPrintln("get: "+get);
                    boolean[] to = new boolean[game.maxPlayers];

                    for (int i = 0; i < game.maxPlayers; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerNumber, to, give, get);

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

                    //D.ebugPrintln("our resources: "+ourPlayerData.getResources());
                    //D.ebugPrintln("Making bank trade:");
                    //D.ebugPrintln("give: "+give);
                    //D.ebugPrintln("get: "+get);
                    boolean[] to = new boolean[game.maxPlayers];

                    for (int i = 0; i < game.maxPlayers; i++)
                    {
                        to[i] = false;
                    }

                    bankTrade = new SOCTradeOffer(game.getName(), ourPlayerNumber, to, give, get);

                    return bankTrade;
                }
            }

            giveRsrcIdx++;
        }

        return bankTrade;
    }

    /**
     * Decide what bank/port trade to request, if any,
     * based on what we want to build and our player's current {@link SOCPlayer#getResources()}.
     *<P>
     * Calls {@link #getOfferToBank(SOCResourceSet, SOCResourceSet)}.
     *
     * @param buildPlan  what we want to build; may be {@code null} or empty.
     *     Will call {@link SOCBuildPlan#getFirstPieceResources()}
     *     unless a third-party bot overrides this method.
     * @param ourResources   the resources we have, from {@link SOCPlayer#getResources()}; not {@code null}
     * @return the offer that we'll make to the bank/ports, or {@code null} if none needed or {@code buildPlan} is empty
     * @since 2.5.00
     */
    public SOCTradeOffer getOfferToBank(SOCBuildPlan buildPlan, SOCResourceSet ourResources)
    {
        if ((buildPlan == null) || buildPlan.isEmpty())
            return null;

        return getOfferToBank(buildPlan.getFirstPieceResources(), ourResources);
    }

    /**
     * Decide what bank/port trade to request, if any,
     * based on which resources we want and our player's current {@link SOCPlayer#getResources()}.
     *<P>
     * Calls {@link #getOfferToBank(SOCResourceSet, SOCResourceSet)}.
     *
     * @param targetResources  what resources we want; can be {@code null} or empty
     * @return the offer that we'll make to the bank/ports based on the resources we have,
     *     or {@code null} if {@code ourPlayerData.getResources()} already contains all needed {@code targetResources}
     * @see #makeOffer(SOCBuildPlan)
     */
    public SOCTradeOffer getOfferToBank(SOCResourceSet targetResources)
    {
        return getOfferToBank(targetResources, ourPlayerData.getResources());
    }

    /// logic recording isSelling or wantsAnotherOffer based on responses: Accept, Reject or no response ///

    /**
     * Marks what a player wants or is not selling based on the received offer.
     * @param offer the offer we have received
     * @since 2.5.00
     */
    protected void recordResourcesFromOffer(SOCTradeOffer offer)
    {
        ///
        /// record that this player wants to sell me the stuff
        ///
        SOCResourceSet giveSet = offer.getGiveSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (giveSet.contains(rsrcType))
            {
                D.ebugPrintlnINFO("%%% player " + offer.getFrom() + " wants to sell " + rsrcType);
                markAsWantsAnotherOffer(offer.getFrom(), rsrcType);
            }
        }

        ///
        /// record that this player is not selling the resources
        /// he is asking for
        ///
        SOCResourceSet getSet = offer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.contains(rsrcType))
            {
                D.ebugPrintlnINFO("%%% player " + offer.getFrom() + " wants to buy " + rsrcType
                    + " and therefore does not want to sell it");
                markAsNotSelling(offer.getFrom(), rsrcType);
            }
        }

    }

    /**
     * Marks what resources another player is not selling, based on their reject to our offer.
     * Does nothing if our {@link SOCPlayer#getCurrentOffer()} is null.
     *<P>
     * To do so for another player's offer, use {@link #recordResourcesFromRejectAlt(int)}.
     *
     * @param rejector the player number corresponding to the player who has rejected our offer
     * @since 2.5.00
     */
    protected void recordResourcesFromReject(int rejector)
    {
        D.ebugPrintlnINFO("%%%%%%%%% REJECT OFFER %%%%%%%%%%%%%");

        final SOCTradeOffer ourOffer = ourPlayerData.getCurrentOffer();
        if (ourOffer == null)
            return;

        ///
        /// record which player said no
        ///
        final SOCResourceSet getSet = ourOffer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.contains(rsrcType) && ! wantsAnotherOffer(rejector, rsrcType))
                markAsNotSelling(rejector, rsrcType);
        }
    }

    /**
     * Marks what resources a player is not selling based on a reject to other offers
     *<P>
     * To do so for our player's offer, use {@link #recordResourcesFromReject(int)}.
     *
     * @param rejector the player number corresponding to the player who has rejected an offer
     * @since 2.5.00
     */
    protected void recordResourcesFromRejectAlt(int rejector)
    {
        D.ebugPrintlnINFO("%%%% ALT REJECT OFFER %%%%");

        for (int pn = 0; pn < game.maxPlayers; pn++)
        {
            SOCTradeOffer offer = game.getPlayer(pn).getCurrentOffer();

            if (offer != null)
            {
                boolean[] offeredTo = offer.getTo();

                if (offeredTo[rejector])
                {
                    //
                    // I think they were rejecting this offer
                    // mark them as not selling what was asked for
                    //
                    SOCResourceSet getSet = offer.getGetSet();

                    for (int rsrcType = SOCResourceConstants.CLAY;
                            rsrcType <= SOCResourceConstants.WOOD;
                            rsrcType++)
                    {
                        if (getSet.contains(rsrcType) && ! wantsAnotherOffer(rejector, rsrcType))
                            markAsNotSelling(rejector, rsrcType);
                    }
                }
            }
        }

    }

    /**
     * This is called when players haven't responded to our offer,
     * so we assume they are not selling and don't want anything else.
     * Marks the resources we offered as not selling and marks that the player doesn't want a different offer for that resource
     * @param ourCurrentOffer the offer we made and not received an answer to; not null
     * @since 2.5.00
     */
    protected void recordResourcesFromNoResponse(SOCTradeOffer ourCurrentOffer)
    {
        boolean[] offeredTo = ourCurrentOffer.getTo();
        SOCResourceSet getSet = ourCurrentOffer.getGetSet();

        for (int rsrcType = SOCResourceConstants.CLAY;
                rsrcType <= SOCResourceConstants.WOOD;
                rsrcType++)
        {
            if (getSet.contains(rsrcType))
            {
                for (int pn = 0; pn < game.maxPlayers; pn++)
                {
                    if (offeredTo[pn])
                    {
                        markAsNotSelling(pn, rsrcType);
                        markAsNotWantingAnotherOffer(pn, rsrcType);
                    }
                }
            }
        }
    }

}
