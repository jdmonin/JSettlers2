/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2025 Jeremy D Monin <jeremy@nand.net>
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
package soc.game;

import soc.server.SOCBoardAtServer;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Handles dice-rolling and resource-distribution logic previously embedded
 * in the {@link SOCGame} God Class.
 *<P>
 * All game-state reads and writes go through the {@code SOCGame} reference
 * passed at construction time.  Public methods on {@code SOCGame} that relate
 * to dice ({@link SOCGame#rollDice()}, {@link SOCGame#canRollDice(int)},
 * {@link SOCGame#getResourcesGainedFromRoll(SOCPlayer, int)}) now delegate here.
 *
 * @since 2.8.00
 */
class SOCGameDiceHandler
{
    private final SOCGame game;

    /**
     * @param game  the game whose dice logic this handler manages; not null
     */
    SOCGameDiceHandler(final SOCGame game)
    {
        this.game = game;
    }

    /**
     * @return true if it's ok for this player to roll the dice
     * @param pn  player number of the player who wants to roll
     */
    boolean canRollDice(final int pn)
    {
        if (game.getCurrentPlayerNumber() != pn)
        {
            return false;
        }
        else if (game.getGameState() != SOCGame.ROLL_OR_CARD)
        {
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * Roll the dice.  Distribute resources, or (for 7) set game state to
     * move robber or to wait for players to discard.
     * See {@link SOCGame#rollDice()} for full documentation.
     *<P>
     * Called at server only.
     *
     * @return The roll results
     */
    SOCGame.RollResult rollDice()
    {
        // N7C: Roll no 7s until a city is built.
        // N7: Roll no 7s during first # rounds.
        //     Use > not >= because roundCount includes current round
        final boolean okToRoll7
            = ((game.isGameOptionSet("N7C")) ? game.hasBuiltCity() : true)
              && (( ! game.isGameOptionSet("N7")) || (game.getRoundCount() > game.getGameOptionIntValue("N7")));

        final Random rand = game.getRandForDice();
        int die1, die2;
        int diceTotal;
        do
        {
            die1 = Math.abs(rand.nextInt() % 6) + 1;
            die2 = Math.abs(rand.nextInt() % 6) + 1;

            diceTotal = die1 + die2;
        } while ((diceTotal == 7) && ! okToRoll7);

        game.setCurrentDiceValue(diceTotal);

        final SOCGame.RollResult currentRoll = game.getCurrentRollResult();
        currentRoll.update(die1, die2);  // also clears currentRoll.cloth (SC_CLVI)

        boolean sc_piri_plGainsGold = false;  // Has a player won against pirate fleet attack? (SC_PIRI)
        if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
        {
            /**
             * Move the pirate fleet along their path.
             * Copy pirate fleet attack results to currentRoll.
             * If the pirate fleet is already defeated, do nothing.
             */
            final int numSteps = (die1 < die2) ? die1 : die2;
            final int newPirateHex = ((SOCBoardLarge) game.getBoard()).movePirateHexAlongPath(numSteps);
            game.setOldGameStateDirect(game.getGameState());
            if (newPirateHex != 0)
                game.movePirate(game.getCurrentPlayerNumber(), newPirateHex, numSteps);
            else
                game.getRobberyResult().victims = null;

            final List<SOCPlayer> victims = game.getRobberyResult().victims;
            if ((victims != null) && (victims.size() == 1))
            {
                currentRoll.sc_piri_fleetAttackVictim = victims.get(0);

                currentRoll.sc_piri_fleetAttackRsrcs = game.getRobberyResult().sc_piri_loot;
                if (currentRoll.sc_piri_fleetAttackRsrcs.contains(SOCResourceConstants.GOLD_LOCAL))
                {
                    final SOCPlayer plGold = currentRoll.sc_piri_fleetAttackVictim;  // won't be null
                    plGold.setNeedToPickGoldHexResources(1 + plGold.getNeedToPickGoldHexResources());

                    if (diceTotal == 7)
                    {
                        game.setHasRolledSeven(true);

                        // Need to set this state only on 7, to pick _before_ discards.  On any other
                        // dice roll, the free pick here will be combined with the usual roll-result gold picks.
                        game.setOldGameStateDirect(SOCGame.ROLL_OR_CARD);
                        game.setGameStateDirect(SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE);

                        return currentRoll;  // <--- Early return: Wait to pick, then come back & discard ---

                    } else {
                        sc_piri_plGainsGold = true;
                    }
                }
            } else {
                currentRoll.sc_piri_fleetAttackVictim = null;
                currentRoll.sc_piri_fleetAttackRsrcs = null;
            }
        }

        /**
         * handle the seven case
         */
        if (diceTotal == 7)
        {
            rollDice_update7gameState();
        }
        else
        {
            boolean anyGoldHex = false;

            /**
             * distribute resources
             */
            final SOCPlayer[] players = game.getPlayers();
            for (int i = 0; i < game.maxPlayers; i++)
            {
                if (! game.isSeatVacant(i))
                {
                    SOCPlayer pl = players[i];
                    pl.addRolledResources(getResourcesGainedFromRoll(pl, diceTotal));
                    if (game.hasSeaBoard && pl.getNeedToPickGoldHexResources() > 0)
                        anyGoldHex = true;
                }
            }

            if (sc_piri_plGainsGold)
            {
                anyGoldHex = true;
                // this 1 gold was already added to that player's getNeedToPickGoldHexResources
            }

            /**
             * distribute cloth from villages
             */
            if (game.hasSeaBoard && game.isGameOptionSet(SOCGameOptionSet.K_SC_CLVI))
            {
                // distribute will usually return false; most rolls don't hit dice#s which distribute cloth
                if (((SOCBoardAtServer) game.getBoard()).distributeClothFromRoll(game, currentRoll, diceTotal))
                    game.checkForWinner();
            }

            /**
             * done, next game state
             */
            if (game.getGameState() != SOCGame.OVER)
            {
                if (! anyGoldHex)
                {
                    game.setGameStateDirect(SOCGame.PLAY1);
                } else {
                    game.setOldGameStateDirect(SOCGame.PLAY1);
                    game.setGameStateDirect(SOCGame.WAITING_FOR_PICK_GOLD_RESOURCE);
                }
            }
        }

        return currentRoll;
    }

    /**
     * When a 7 is rolled, update the game state:
     * Always {@link SOCGame#WAITING_FOR_DISCARDS} if any player's resource total &gt; 7,
     * otherwise {@link SOCGame#PLACING_ROBBER}, {@link SOCGame#WAITING_FOR_ROBBER_OR_PIRATE},
     * or scenario-specific states.
     *<P>
     * Also called from {@link SOCGame#pickGoldHexResources(int, SOCResourceSet)}
     * for the {@code _SC_PIRI} scenario.
     */
    void rollDice_update7gameState()
    {
        game.setHasRolledSeven(true);

        /**
         * if there are players with too many cards, wait for
         * them to discard
         */
        final SOCPlayer[] players = game.getPlayers();
        for (int i = 0; i < game.maxPlayers; i++)
        {
            if (players[i].getResources().getTotal() > 7)
            {
                players[i].setNeedToDiscard(true);
                game.setGameStateDirect(SOCGame.WAITING_FOR_DISCARDS);
            }
        }

        /**
         * if no one needs to discard, then wait for
         * the robber to move
         */
        if (game.getGameState() != SOCGame.WAITING_FOR_DISCARDS)
        {
            // next-state logic is similar to playKnight and discard;
            // if you update this method, check those ones

            game.setPlacingRobberForKnightCard(false);
            game.setOldGameStateDirect(SOCGame.PLAY1);
            if (game.isGameOptionSet(SOCGameOptionSet.K_SC_PIRI))
            {
                game.setRobberyWithPirateNotRobber(false);
                final SOCGame.RollResult currentRoll = game.getCurrentRollResult();
                currentRoll.sc_robPossibleVictims = game.getPossibleVictims();
                if (currentRoll.sc_robPossibleVictims.isEmpty())
                    game.setGameStateDirect(SOCGame.PLAY1);  // no victims
                else
                    game.setGameStateDirect(SOCGame.WAITING_FOR_ROB_CHOOSE_PLAYER);  // 1 or more victims; could choose to not steal anything
            }
            else if (game.canChooseMovePirate())
            {
                game.setGameStateDirect(SOCGame.WAITING_FOR_ROBBER_OR_PIRATE);
            } else {
                game.setRobberyWithPirateNotRobber(false);
                game.setGameStateDirect(SOCGame.PLACING_ROBBER);
            }
        }
    }

    /**
     * For {@link #rollDice()}, figure out what resources a player gets on a given roll,
     * based on the hexes adjacent to the player's settlements and cities
     * and based on the robber's position.
     *<P>
     * If {@link SOCGame#hasSeaBoard}, and the player's adjacent to a
     * {@link SOCBoardLarge#GOLD_HEX}, the gold-hex resources they must pick
     * are returned as {@link SOCResourceConstants#GOLD_LOCAL}.
     *
     * @param player   the player
     * @param roll     the total number rolled on the dice
     *
     * @return the resource set
     */
    SOCResourceSet getResourcesGainedFromRoll(SOCPlayer player, final int roll)
    {
        SOCResourceSet resources = new SOCResourceSet();
        final int robberHex = game.getBoard().getRobberHex();

        getResourcesGainedFromRollPieces(roll, resources, robberHex, player.getSettlements(), 1);
        getResourcesGainedFromRollPieces(roll, resources, robberHex, player.getCities(), 2);

        return resources;
    }

    /**
     * Figure out what resources these piece positions would get on a given roll,
     * based on the hexes adjacent to the pieces' node coordinates.
     *
     * @param roll     the total number rolled on the dice
     * @param resources  Add new resources to this set
     * @param robberHex  Robber's position, from {@link SOCBoard#getRobberHex()}
     * @param pieces  Collection of a type of the player's {@link SOCPlayingPiece}s at nodes on the board
     * @param incr   Add this many resources (1 or 2) per playing piece
     */
    private void getResourcesGainedFromRollPieces
        (final int roll, SOCResourceSet resources,
         final int robberHex, Collection<? extends SOCPlayingPiece> pieces, final int incr)
    {
        final SOCBoard board = game.getBoard();
        for (final SOCPlayingPiece p : pieces)
        {
            for (final int hexCoord : board.getAdjacentHexesToNode(p.getCoordinates()))
            {
                if ((hexCoord == robberHex) || (board.getNumberOnHexFromCoord(hexCoord) != roll))
                    continue;

                switch (board.getHexTypeFromCoord(hexCoord))
                {
                case SOCBoard.CLAY_HEX:
                    resources.add(incr, SOCResourceConstants.CLAY);
                    break;

                case SOCBoard.ORE_HEX:
                    resources.add(incr, SOCResourceConstants.ORE);
                    break;

                case SOCBoard.SHEEP_HEX:
                    resources.add(incr, SOCResourceConstants.SHEEP);
                    break;

                case SOCBoard.WHEAT_HEX:
                    resources.add(incr, SOCResourceConstants.WHEAT);
                    break;

                case SOCBoard.WOOD_HEX:
                    resources.add(incr, SOCResourceConstants.WOOD);
                    break;

                case SOCBoardLarge.GOLD_HEX:
                    if (game.hasSeaBoard)
                        resources.add(incr, SOCResourceConstants.GOLD_LOCAL);
                        // if not hasSeaBoard, GOLD_HEX == SOCBoard.MISC_PORT_HEX
                    break;
                }
            }
        }
    }
}
