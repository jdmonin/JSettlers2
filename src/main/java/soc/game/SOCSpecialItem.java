/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2014-2017,2019 Jeremy D Monin <jeremy@nand.net>
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

import java.util.ArrayList;
import java.util.List;

import soc.message.SOCMessage;  // strictly for isSingleLineAndSafe


/**
 * A special item in a game that uses Settlers scenarios or expansions.
 * During game play, players may be allowed to {@code PICK} (choose), {@code SET}, or {@code CLEAR} special items;
 * the meaning of these actions is scenario-specific.  See {@code typeKey} list below for usage and meaning.
 * See {@link #playerPickItem(String, SOCGame, SOCPlayer, int, int)} and
 * {@link #playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)} for API details.
 *<P>
 * Example use: The Wonders chosen by players in the {@link SOCGameOption#K_SC_WOND _SC_WOND} scenario.
 *<P>
 * Special Items are per-game and/or per-player.  In {@link SOCGame} and {@link SOCPlayer}
 * they're accessed by an item type key.  For compatibility among scenarios and expansions,
 * this key should be a {@link SOCGameOption} keyname; if an option has more than one
 * special item type, {@code typeKey} should be optionName + "/" + a short alphanumeric key of your choosing.
 * Please document the Special Item type(s) in the SOCGameOption's javadoc, including
 * whether each is per-game, per-player, or both (for more convenient access).
 *<P>
 * In some scenarios, Special Items may have requirements for players to build or use them.
 * See {@link SOCSpecialItem.Requirement} javadoc for more details.  To check requirements,
 * call {@link SOCSpecialItem#checkRequirements(SOCPlayer, boolean)}.
 *<P>
 * <H5>Optional Fields:</H5>
 * Some {@code typeKey}s may use the {@link #getLevel()} and {@link #getStringValue()} fields;
 * their meaning is type-specific.
 *<P>
 * <H5>Non-Networked Fields:</H5>
 * During game setup, {@link #makeKnownItem(String, int)} can be called for convenience at both the
 * server and client from {@link SOCGame#updateAtBoardLayout()}:
 *<P>
 * The cost and requirement fields are initialized at the server and at the client, not sent over the network.
 * Because of their limited and known use, it's easier to set them up in a factory method here than to create,
 * send, and parse messages with all details of the game's Special Items.  If a new Special Item type is created
 * for a new scenario or expansion, the client would most likely need new code to handle that scenario or
 * expansion, so the new item type's field initialization can be added to the factory at that time.
 * See {@link #makeKnownItem(String, int)}.
 *<P>
 * <B>Locks:</B> Field values are not synchronized here. If a specific item type or access pattern
 * requires synchronization, do so outside this class and document the details.  Some methods here
 * require locking as described in their javadocs.
 *<P>
 * Special items must be {@link Cloneable} for use in copy constructors; see {@link #clone()} for details.
 *
 *<H5>Current scenarios and {@code typeKey}s:</H5>
 *
 *<H6>{@link SOCGameOption#K_SC_WOND _SC_WOND}</H6>
 *  In this scenario, the game has a list of unique "Wonders", indexed 1 to {@link SOCGame#maxPlayers} + 1.
 *  (The 6-player game includes another copy of the first two wonders.)
 *  To win the game, a player must take ownership of exactly one of these, and build 4 levels of it.
 *<P>
 *  A reference to the player's {@code SOCSpecialItem} is kept in the game's Special Item list, and also placed
 *  at index 0 of the player's Special Item list.
 *<P>
 *  This scenario uses the {@link #getStringValue()} field to identify the wonder object with a localized name:
 *  "w1" is the Theater, "w5" is the Cathedral, etc. The 6-player game includes another copy of
 *  the first two wonders: 2 special items will have "w1", 2 will have "w2".
 *
 *<UL>
 * <LI> On their own turn, a player can {@code PICK} a wonder from the game's list.  Each player can pick at most 1;
 *    no other player can pick the same one.  If they are able to pick that wonder, doing so builds its first level.
 *    When sending a PICK request, the wonder's game item index and player item index must meet the requirements
 *    of {@link SOCScenario#K_SC_WOND}.
 * <LI> Game state must be {@link SOCGame#PLAY1 PLAY1}
 * <LI> There are requirements ({@link #req}) to pick each wonder, different wonders have different requirements
 * <LI> There is a resource cost to build each level, different wonders have different costs
 * <LI> When the player first picks a Wonder to build its first level, player loses 1 ship.
 *    (In the physical game the ship becomes a marker on the Wonder card.)
 * <LI> Can build several levels per turn; each level is built with a {@code PICK} request
 * <LI> When level 4 is built, player wins the game, even if they have less than 10 VP
 * <LI> When a player reaches 10 VP, <b>and</b> has a higher level of wonder than any other player (no ties), they win
 *    on their turn.
 *</UL>
 *  For more details see {@link SOCScenario#K_SC_WOND}.
 *
 * @since 2.0.00
 */
public class SOCSpecialItem
    implements Cloneable
{

    /**
     * To win the game in {@link SOCGameOption#K_SC_WOND _SC_WOND}, player can build this many
     * levels (4) of their Wonder.
     */
    public static final int SC_WOND_WIN_LEVEL = 4;

    /**
     * Requirements for the Wonders in the {@link SOCGameOption#K_SC_WOND _SC_WOND} scenario.
     * Index 0 unused.  The 6-player game includes another copy of the first two wonders.
     * Used by {@link #makeKnownItem(String, int)}.
     *<P>
     * Parsing each of these is tested in {@link soctest.game.TestSpecialItem#testRequirementParseGood()};
     * if you change the contents here, also update the test cases in that method.
     */
    private static final String[] REQ_SC_WOND = { null, "2C", "S@N2", "C@P,5L", "S@N1", "C,6V", "2C", "S@N2" };

    /**
     * Costs for the Wonders in the {@link SOCGameOption#K_SC_WOND _SC_WOND} scenario.
     * Index 0 unused.  The 6-player game includes another copy of the first two wonders.
     * Each 5-element array is { clay, ore, sheep, wheat, wood }. Used by {@link #makeKnownItem(String, int)}.
     */
    private static final int[][] COST_SC_WOND =
    {
        null,
        // clay, ore, sheep, wheat, wood
        { 1, 0, 3, 0, 1 },  // theater
        { 0, 0, 1, 1, 3 },  // great bridge
        { 0, 2, 0, 3, 0 },  // monument
        { 3, 0, 0, 1, 1 },  // great wall
        { 1, 3, 0, 1, 0 },  // cathedral
        { 1, 0, 3, 0, 1 },  // theater
        { 0, 0, 1, 1, 3 }   // great bridge
    };

    /**
     * {@link #sv} for the Wonders in the {@link SOCGameOption#K_SC_WOND _SC_WOND} scenario.
     * {@code sv} is used in this scenario to identify the wonder with a localized name.
     * Index 0 unused.  The 6-player game includes another copy of the first two wonders.
     * Used by {@link #makeKnownItem(String, int)}.
     */
    private static final String[] SV_SC_WOND = { null, "w1", "w2", "w3", "w4", "w5", "w1", "w2" };

    /**
     * Item's optional game item index, or -1, as used with {@link SOCGame#getSpecialItem(String, int, int, int)}.
     */
    protected int gameItemIndex;

    /**
     * The player who owns this item, if any. Will be null for certain items
     * which belong to the game and not to players.
     */
    protected SOCPlayer player;

    /** Optional coordinates on the board for this item, or -1. An edge or a node, depending on item type. */
    protected int coord;

    /** Optional level of construction or strength, or 0. */
    protected int level;

    /** Optional string value field, or null; this field's meaning is specific to the item's {@code typeKey}. */
    protected String sv;

    /**
     * Optional cost to buy, use, or build the next level, or {@code null}.
     * Not sent over the network; see {@link SOCSpecialItem class javadoc}.
     */
    protected SOCResourceSet cost;

    /**
     * Optional requirements to buy, use, or build the next level, or {@code null}.
     * Not sent over the network; see {@link SOCSpecialItem class javadoc}.
     */
    public final List<Requirement> req;

    /**
     * Create a scenario/expansion's special item if known. This is a factory method for game setup convenience.
     * The known item's {@link #req requirements} and cost will be filled from static data.
     * Sets {@link #getGameIndex()} to {@code idx}.
     *<P>
     * Currently known {@code typeKey}s:
     *<UL>
     *<LI> {@link SOCGameOption#K_SC_WOND _SC_WOND}: Wonders
     *</UL>
     * If {@code typeKey} is unknown, the item will be created with {@code null} cost and requirements,
     * equivalent to calling {@link #SOCSpecialItem(SOCPlayer, int, SOCResourceSet, String) new SOCSpecialItem}
     * {@code (null, -1, null, null)}.
     *
     * @param typeKey  Special item type.  Typically a {@link SOCGameOption} keyname;
     *    see {@link SOCSpecialItem class javadoc} for details.
     * @param idx  Index within game's Special Item list
     * @return A Special Item at no coordinate (-1) and unowned by any player, with cost/requirements if known,
     *     or {@code null} cost and requirements otherwise.
     */
    public static final SOCSpecialItem makeKnownItem(final String typeKey, final int idx)
    {
        // If you update this method or add a scenario here, update soctest.game.TestSpecialItem method testMakeKnownItem.

        if (! typeKey.equals(SOCGameOption.K_SC_WOND))
        {
            return new SOCSpecialItem(null, -1, null, null);  // <--- Early return: Unknown typeKey ---
        }

        final String[] typeReqs = REQ_SC_WOND;
        final int[][] typeCosts = COST_SC_WOND;
        final String[] typeSV = SV_SC_WOND;

        final SOCResourceSet costRS;
        if ((idx < 0) || (idx >= typeCosts.length))
        {
            costRS = null;
        } else {
            final int[] cost = typeCosts[idx];
            costRS = (cost == null) ? null : new SOCResourceSet(cost);
        }

        final String req = ((idx < 0) || (idx >= typeReqs.length)) ? null : typeReqs[idx];
        final String sv = ((idx < 0) || (idx >= typeSV.length)) ? null : typeSV[idx];

        final SOCSpecialItem si = new SOCSpecialItem(null, -1, 0, sv, costRS, req);
        si.setGameIndex(idx);

        return si;
    }

    /**
     * Process a request from a player to {@code PICK} a known special item.
     * Implements scenario-specific rules and behavior for the item.
     * Called at server, not at client.
     *<P>
     * In some scenarios, calls {@link SOCGame#checkForWinner()}; after calling
     * this method, check {@link SOCGame#getGameState()} &gt;= {@link SOCGame#OVER}.
     *<P>
     * When both {@code gi} and {@code pi} are specified, the item is retrieved
     * by calling {@link SOCGame#getSpecialItem(String, int, int, int)} before
     * making any changes.  That object's {@link #getCost()}, if any, is what was
     * paid if this method returns {@code true}.  If the caller needs to know
     * the cost paid, call that method before this one.
     *<P>
     * Currently only {@link SOCGameOption#K_SC_WOND _SC_WOND} is recognized as a {@code typeKey} here.
     * To see which scenario and option {@code typeKey}s use this method, and scenario-specific usage details,
     * see the {@link SOCSpecialItem} class javadoc.
     *<P>
     * <B>Locks:</B> Call {@link SOCGame#takeMonitor()} before calling this method.
     *
     * @param typeKey  Item's {@code typeKey}, as described in the {@link SOCSpecialItem} class javadoc
     * @param ga  Game containing {@code pl} and special items
     * @param pl  Requesting player; never {@code null}
     * @param gi  Pick this index within game's Special Item list, or -1
     * @param pi  Pick this index within {@code pl}'s Special Item list, or -1
     * @return  true if the item's cost was deducted from {@code pl}'s resources
     * @throws IllegalStateException if {@code pl} cannot set or clear this item right now
     *     (due to cost, requirements, game state, is not their turn, or anything else),
     *     or if {@code typeKey} is unknown here, or if this {@code typeKey} doesn't
     *     use {@code PICK} requests from client players.
     * @see #playerSetItem(String, SOCGame, SOCPlayer, int, int, boolean)
     */
    public static boolean playerPickItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi)
        throws IllegalStateException
    {
        if ((pl.getPlayerNumber() != ga.getCurrentPlayerNumber())
            || ((ga.getGameState() != SOCGame.PLAY1) && (ga.getGameState() != SOCGame.SPECIAL_BUILDING)))
            throw new IllegalStateException();

        if (! SOCGameOption.K_SC_WOND.equals(typeKey))
            throw new IllegalStateException("unknown typeKey: " + typeKey);

        // _SC_WOND

        if ((gi < 1) || (pi != 0))
            throw new IllegalStateException();

        SOCSpecialItem itm = ga.getSpecialItem(typeKey, gi);
            // same logic for _SC_WOND as getSpecialItem(typeKey,gi,pi)
            // because that method checks gi before pi, and for this
            // scenario the item at valid gi will never be null.

        if ((itm == null) || ((itm.player != null) && (itm.player != pl)))
            throw new IllegalStateException();  // another player owns it, or item not found

        SOCSpecialItem plWonder = pl.getSpecialItem(typeKey, 0);
        if ((plWonder != null) && (plWonder != itm))
            throw new IllegalStateException();  // player already has a different wonder

        // check costs at each level
        SOCResourceSet cost = itm.cost;
        if ((cost != null) && ! pl.getResources().contains(cost))
            throw new IllegalStateException("cost");

        // if unowned (first level), check requirements and then acquire item if met
        if (itm.player == null)
        {
            if (! itm.checkRequirements(pl, false))
                throw new IllegalStateException("requirements");

            itm.setPlayer(pl);
            pl.setSpecialItem(typeKey, 0, itm);
        }

        if (cost != null)
            pl.getResources().subtract(cost);

        itm.level++;

        // win condition: check for level 4
        // win condition: check for >= 10 VP and highest build level (no ties)
        if ((itm.level >= SC_WOND_WIN_LEVEL) || (pl.getTotalVP() >= ga.vp_winner))
            ga.checkForWinner();

        return (cost != null);
    }

    /**
     * Process a request from a player to {@code SET} or {@code CLEAR} a known special item.
     * Implements scenario-specific rules and behavior for the item.
     * Called at server, not at client.
     *<P>
     * In some scenarios, calls {@link SOCGame#checkForWinner()}; after calling
     * this method, check {@link SOCGame#getGameState()} &gt;= {@link SOCGame#OVER}.
     *<P>
     * To see which scenario and option {@code typeKey}s use this method, and scenario-specific usage details,
     * see the {@link SOCSpecialItem} class javadoc.
     *<P>
     * <B>Locks:</B> Call {@link SOCGame#takeMonitor()} before calling this method.
     *
     * @param typeKey  Item's {@code typeKey}, as described in the {@link SOCSpecialItem} class javadoc
     * @param ga  Game containing {@code pl} and special items
     * @param pl  Requesting player; never {@code null}
     * @param gi  Set or clear this index within game's Special Item list, or -1
     * @param pi  Set or clear this index within {@code pl}'s Special Item list, or -1
     * @param isSet  True if player wants to set, false if player wants to clear, this item index
     * @return  true if the item's cost was deducted from {@code pl}'s resources
     * @throws IllegalStateException if {@code pl} cannot set or clear this item right now
     *     (due to cost, requirements, game state, or anything else), or if {@code typeKey} is unknown here,
     *     or if this {@code typeKey} doesn't use {@code SET} or {@code CLEAR} requests from client players.
     * @see #playerPickItem(String, SOCGame, SOCPlayer, int, int)
     */
    public static boolean playerSetItem
        (final String typeKey, final SOCGame ga, final SOCPlayer pl, final int gi, final int pi, final boolean isSet)
        throws IllegalStateException
    {
        throw new IllegalStateException("SET/CLEAR requests not used with this typeKey: " + typeKey);
    }

    /**
     * Make a new item, optionally owned by a player.
     * Its optional Level will be 0, string value will be {@code null}.
     *
     * @param pl  player who owns the item, or {@code null}
     * @param co  coordinates, or -1
     * @param cost  cost to buy, use, or build the next level, or null
     * @param req  requirements to buy, use, or build the next level, or null.
     *      If provided, this requirement specification string will be
     *      parsed by {@link SOCSpecialItem.Requirement#parse(String)}.
     * @throws IllegalArgumentException  if {@code req != null} but isn't a syntactically valid specification
     */
    public SOCSpecialItem(SOCPlayer pl, final int co, SOCResourceSet cost, final String req)
        throws IllegalArgumentException
    {
        this(pl, co, 0, null, cost, req);
    }

    /**
     * Make a new item, optionally owned by a player, with an optional level and string value.
     *
     * @param pl  player who owns the item, or {@code null}
     * @param co  coordinates, or -1
     * @param lv  current level of construction or strength, or 0
     * @param sv  current string value (optional), or {@code null}.
     *      Meaning is type-specific, see {@link #getStringValue()}.
     *      If not {@code null}, must pass {@link SOCMessage#isSingleLineAndSafe(String)}.
     * @param cost  cost to buy, use, or build the next level, or null
     * @param req  requirements to buy, use, or build the next level, or null.
     *      If provided, this requirement specification string will be
     *      parsed by {@link SOCSpecialItem.Requirement#parse(String)}.
     * @throws IllegalArgumentException  if {@code req != null} but isn't a syntactically valid specification,
     *      or if {@code sv} fails {@link SOCMessage#isSingleLineAndSafe(String)}
     */
    public SOCSpecialItem
        (SOCPlayer pl, final int co, final int lv, final String sv, SOCResourceSet cost, final String req)
        throws IllegalArgumentException
    {
        player = pl;
        coord = co;
        level = lv;
        this.sv = sv;
        this.cost = cost;
        this.req = (req != null) ? Requirement.parse(req) : null;

        if ((sv != null) && ! SOCMessage.isSingleLineAndSafe(sv))
            throw new IllegalArgumentException("sv");
    }

    /**
     * Get this item's optional game item index, or -1 if none,
     * as used with {@link SOCGame#getSpecialItem(String, int, int, int)}.
     * @see #getPlayer()
     */
    public int getGameIndex()
    {
        return gameItemIndex;
    }

    /** Set this item's {@link #getGameIndex(). */
    public void setGameIndex(final int gi)
    {
        gameItemIndex = gi;
    }

    /**
     * Get the player who owns this item, if any.
     * @return the owner of the item, or {@code null}
     * @see #getGameIndex()
     */
    public SOCPlayer getPlayer()
    {
        return player;
    }

    /**
     * Set or clear the player who owns this item.
     * @param pl  the owner of this item, or {@code null}
     */
    public void setPlayer(SOCPlayer pl)
    {
        player = pl;
    }

    /**
     * @return the node or edge coordinate for this item, or -1 if none
     */
    public int getCoordinates()
    {
        return coord;
    }

    /**
     * @param co the node or edge coordinate for this item, or -1 if none
     */
    public void setCoordinates(final int co)
    {
        coord = co;
    }

    /**
     * Get the current construction level or strength of this item.
     * @return  Current level
     */
    public int getLevel()
    {
        return level;
    }

    /**
     * Set the current level of this special item.
     * @param lv  New level
     */
    public void setLevel(final int lv)
    {
        level = lv;
    }

    /**
     * Get the current string value, if any, of this special item.
     * This is an optional field whose meaning is specific to the item type (typeKey).
     * @return  Current string value, or {@code null}
     */
    public String getStringValue()
    {
        return sv;
    }

    /**
     * Set or clear the current string value of this special item.
     * @param sv  New value, or {@code null} to clear
     */
    public void setStringValue(final String sv)
    {
        this.sv = sv;
    }

    /**
     * Get the optional cost to buy, use, or build the next level.
     * Not sent over the network; see {@link SOCSpecialItem class javadoc}.
     * @return  Cost, or {@code null}
     * @see #checkCost(SOCPlayer)
     */
    public SOCResourceSet getCost()
    {
        return cost;
    }

    /**
     * Set or clear the optional cost to buy, use, or build the next level.
     * Not sent over the network; see {@link SOCSpecialItem class javadoc}.
     * @param co  New cost, or {@code null} to clear
     */
    public void setCost(SOCResourceSet co)
    {
        cost = co;
    }

    /**
     * Does this player have resources for this special item's {@link #getCost()}, if any?
     * @param pl  Player to check
     * @return  True if cost is {@code null} or {@link SOCPlayer#getResources() pl.getResources()} contains the cost.
     *     Always false if {@code pl} is {@code null}.
     * @see #checkRequirements(SOCPlayer, boolean)
     */
    public final boolean checkCost(final SOCPlayer pl)
    {
        return (pl != null) && ((cost == null) || pl.getResources().contains(cost));
    }

    /**
     * Does this player meet this special item's {@link #req} requirements?
     * @param pl  Player to check
     * @param checkCost  If true, also check the cost against player's current resources
     * @return  True if player meets the requirements, false otherwise; true if {@link #req} is null or empty.
     *     If {@code checkCost} and {@link #getCost()} != null, false unless player's resources contain {@code cost}.
     *     Always false if player is {@code null}.
     * @throws IllegalArgumentException if {@link #req} has an unknown requirement type,
     *     or refers to an Added Layout Part {@code "N1"} through {@code "N9"} that isn't defined in the board layout
     * @throws UnsupportedOperationException if requirement type S (Settlement) includes {@code atPort} location;
     *     this is not implemented
     * @see #checkRequirements(SOCPlayer, List)
     * @see #checkCost(SOCPlayer)
     */
    public final boolean checkRequirements(final SOCPlayer pl, final boolean checkCost)
        throws IllegalArgumentException, UnsupportedOperationException
    {
        if (checkCost && ! checkCost(pl))
            return false;

        return checkRequirements(pl, req);
    }

    /**
     * Does this player meet a special item's requirements?
     *
     * @param pl  Player to check
     * @param reqsList  Requirements list; to parse from a string, use {@link SOCSpecialItem.Requirement#parse(String)}
     * @return  True if player meets the requirements, false otherwise; true if {@code reqsList} is null or empty.
     *     Always false if player is null.
     * @throws IllegalArgumentException if {@code reqsList} has an unknown requirement type,
     *     or refers to an Added Layout Part {@code "N1"} through {@code "N9"} that isn't defined in the board layout
     * @throws UnsupportedOperationException if requirement type S (Settlement) includes {@code atPort} location;
     *     this is not implemented
     * @see #checkRequirements(SOCPlayer, boolean)
     */
    public static boolean checkRequirements(final SOCPlayer pl, final List<SOCSpecialItem.Requirement> reqsList)
        throws IllegalArgumentException, UnsupportedOperationException
    {
        if (pl == null)
            return false;  // no player, can't meet any requirements
        if (reqsList == null)
            return true;   // no requirements, nothing to fail

        for (final Requirement req : reqsList)
        {
            final int count = req.count;
            List<? extends SOCPlayingPiece> pieces = null;

            switch (req.reqType)
            {
            case 'S':
                pieces = pl.getSettlements();
                if (pieces.size() < count)
                    return false;
                break;

            case 'C':
                pieces = pl.getCities();
                if (pieces.size() < count)
                    return false;
                break;

            case 'V':
                if (pl.getTotalVP() < count)
                    return false;
                break;

            case 'L':
                if (pl.getLongestRoadLength() < count)
                    return false;
                break;

            default:
                throw new IllegalArgumentException("Unknown requirement type " + req.reqType);
            }

            if (pieces != null)
            {
                // check for location requirement:

                if (req.atPort && (req.reqType != 'C'))
                    throw new UnsupportedOperationException("atPort reqType " + req.reqType + " not implemented");

                if (req.atCoordList != null)
                {
                    boolean foundAtNode = false;
                    final int[] nodes = ((SOCBoardLarge) pl.getGame().getBoard()).getAddedLayoutPart(req.atCoordList);
                    if (nodes == null)
                        throw new IllegalArgumentException
                            ("Requirement uses undefined Added Layout Part " + req.atCoordList);

                    for (SOCPlayingPiece pp : pieces)
                    {
                        final int node = pp.getCoordinates();
                        for (int i = 0; i < nodes.length; ++i)
                        {
                            if (node == nodes[i])
                            {
                                foundAtNode = true;
                                break;
                            }
                        }

                        if (foundAtNode)
                            break;
                    }

                    if (! foundAtNode)
                        return false;  // no matching piece found
                }
                else if (req.atPort)
                {
                    boolean foundAtNode = false;
                    final SOCBoard board = pl.getGame().getBoard();

                    for (SOCPlayingPiece pp : pieces)
                    {
                        if (board.getPortTypeFromNodeCoord(pp.getCoordinates()) != -1)
                        {
                            foundAtNode = true;
                            break;
                        }
                    }

                    if (! foundAtNode)
                        return false;  // no matching piece found
                }
            }
        }

        return true;  // didn't fail any requirement
    }

    /**
     * @return a human readable form of this object
     */
    @Override
    public String toString()
    {
        return "SOCSpecialItem:player=" + player + "|coord=" + Integer.toHexString(coord) + "|level=" + level
            + "|cost=[" + cost + "]|req=" + req;
    }

    /**
     * Compare this SOCSpecialItem to another SOCSpecialItem, or another object.
     * Comparison method:
     * <UL>
     * <LI> If other is null, false.
     * <LI> If other is not a SOCSpecialItem, false.
     * <LI> SOCSpecialItem are equal with the same coordinate, player, and level.
     * </UL>
     *
     * @param other The object to compare with, or null
     */
    @Override
    public boolean equals(Object other)
    {
        if ((other == null) || ! (other instanceof SOCSpecialItem))
            return false;

        return ((coord == ((SOCSpecialItem) other).coord)
            &&  (player == ((SOCSpecialItem) other).player)
            &&  (level == ((SOCSpecialItem) other).level));
    }

    /**
     * For use in set copy constructors, create and return a clone of this {@link SOCSpecialItem}.
     * The {@code SOCSpecialItem} implementation just calls {@code super.clone()}.
     * If subclasses have any lists or structures, be sure to deeply copy them.
     * Requirements aren't deep-copied, because they are final and won't change.
     * @throws CloneNotSupportedException  Declared from super.clone(), should not occur
     *     since SOCSpecialItem implements Cloneable.
     * @return a clone of this item
     */
    public SOCSpecialItem clone()
        throws CloneNotSupportedException
    {
        SOCSpecialItem cl = (SOCSpecialItem) super.clone();
        cl.cost = cost.copy();
        return cl;
    }

    /**
     * Data structure and parser for a special item's requirements.
     *<P>
     * A requirement is a minimum count of items (Settlements, Cities, Victory Points, or Length of player's longest
     * route) with an optional required position (at a Port, or at a list of special nodes) for at least one of the
     * Settlement or City items.
     *<P>
     * At the client, requirements are rendered in {@code SOCSpecialItemDialog.buildRequirementsText};
     * if new fields or requirement types are added, please update that method.
     *
     * @see #parse(String)
     * @see SOCSpecialItem#checkRequirements(SOCPlayer, boolean)
     */
    public static final class Requirement
    {
        /**
         * 'S' for settlement, 'C' for city, 'V' for victory points, 'L' for length of player's longest route.
         * If {@link #atPort} is true, must be 'C'.
         */
        public final char reqType;

        /**
         * Number of pieces, victory points, or length of route required. Default is 1.
         * If {@link #atPort} or {@link #atCoordList} is specified, at least 1 of the pieces (not all)
         * must be at a port or a listed coordinate.
         */
        public final int count;

        /**
         * If true, a {@code reqType} piece must be at a 3:1 or 2:1 port.
         * See notes for {@link #count}. Currently supports only {@code reqType} C (City),
         * not S, because no current scenario justified the extra coding for a combined set of
         * Settlements and Cities.
         */
        public final boolean atPort;

        /**
         * Board layout coordinate list such as "N1", or null.  If non-null, a reqType piece must
         * be at a node coordinate in this named list within the board layout's {@code getAddedLayoutPart}s.
         */
        public final String atCoordList;

        /**
         * Parse a requirement specification string into {@link Requirement} objects.
         *<P>
         * Requirements are a comma-separated list of items, each item having this syntax:<BR>
         * [count] itemType [@ location]
         *<UL>
         * <LI> Count is an optional integer, otherwise 1 is the default
         * <LI> ItemType is a letter for the requirement type:
         *    C for Cities, S for Settlements (those upgraded to cities don't count),
         *    V for total Victory Points, or L for length of player's longest trade route.
         * <LI> Location is an optional location that at least one of the player's {@code ItemType} pieces must be at:
         *  <UL>
         *   <LI> P for any 3:1 or 2:1 trade port; ItemType must be C (cities).
         *   <LI> N1 through N9 for a Node List in the board layout
         *      ({@link SOCBoardLarge#getAddedLayoutPart(String) board.getAddedLayoutPart("N1")} etc).
         *  </UL>
         *</UL>
         *
         * <H5>Examples:</H5>
         *<UL>
         * <LI>{@code 3S} = 3 settlements
         * <LI>{@code 2C,8V} = 2 cities, 8 victory points
         * <LI>{@code 6L} = trade route length 6
         * <LI>{@code C@P} = city at any port
         * <LI>{@code 2C@N2} = 2 cities, at least one of which is in node list 2 ({@code "N2"}) in the board layout
         *</UL>
         *
         * @param req  Requirements string following the syntax given above
         * @return List of {@link Requirement}s, or {@code null} if {@code req} is ""
         * @throws IllegalArgumentException  if {@code req} isn't a syntactically valid specification
         */
        public static List<Requirement> parse(final String req)
            throws IllegalArgumentException
        {
            final int L = req.length();
            if (L == 0)
                return null;

            ArrayList<Requirement> ret = new ArrayList<Requirement>();

            int i = 0;
            char c;  // in parsing loop, c == req.charAt(i)

            // Loop for each comma-separated requirement
            while (i < L)
            {
                c = req.charAt(i);

                // first: optional digit(s), then item-type letter
                int itemCount;
                if (Character.isDigit(c))
                {
                    int j = i + 1;
                    while (j < req.length())
                    {
                        c = req.charAt(j);
                        if (! Character.isDigit(c))
                            break;

                        ++j;
                    }
                    if (j == req.length())
                        throw new IllegalArgumentException("Must follow item count with item type in " + req);
                    // postcondition: j is 1 char past end of digits

                    itemCount = Integer.parseInt(req.substring(i, j));
                    i = j;
                    // c was req.charAt(j) already
                } else {
                    itemCount = 1;
                }

                final char reqType = c;  // 'S', 'C', 'V', 'L'
                if ((c < 'A') || (c > 'Z'))
                    throw new IllegalArgumentException("Expected item-type letter at position " + i + " in " + req);

                ++i;
                if (i >= L)
                {
                    // This req is done, comma separates it from next req
                    ret.add(new Requirement(reqType, itemCount, false, null));

                    break;  // <--- Finished last req ---
                }

                c = req.charAt(i);

                if (c == ',')
                {
                    // This req is done: comma separates it from next req
                    ret.add(new Requirement(reqType, itemCount, false, null));

                    ++i;
                    if (i >= L)
                        throw new IllegalArgumentException("ends with ',': " + req);

                    continue;  // <--- completed this req ---
                }

                if (c != '@')
                    throw new IllegalArgumentException("Expected @ or , at position " + i + " in " + req);

                ++i;
                if (i >= L)
                    throw new IllegalArgumentException("Must follow @ with P or N# in " + req);

                c = req.charAt(i);

                // Currently valid after '@': N#, or P for Port
                switch (c)
                {
                case 'P':
                    ret.add(new Requirement(reqType, itemCount, true, null));
                    ++i;
                    break;

                case 'N':
                    ++i;
                    if (i < L)
                        c = req.charAt(i);
                    if ((i < L) && Character.isDigit(c))
                    {
                        ret.add(new Requirement(reqType, itemCount, false, "N" + c));
                        ++i;
                        break;
                    }
                    // else, will fall through to default and throw exception

                default:
                    throw new IllegalArgumentException("Must follow @ with P or N# in " + req);
                }

                // If we got here, we've parsed the req and i should be at end or comma
                if (i < L)
                {
                    if (req.charAt(i) != ',')
                        throw new IllegalArgumentException
                            ("Extra characters in spec: Expected , or end of string at position " + i + " in " + req);

                    ++i;
                    // top of main loop will parse the requirement that follows the comma
                }
            }

            return ret;
        }

        /**
         * Create a Requirement item with these field values.
         * See each field's javadoc for meaning of parameter named from that field.
         * Except for {@code atPort} interactions, parameter values are not validated here.
         * @param reqType  See {@link #reqType}
         * @param count    See {@link #count}
         * @param atPort   See {@link #atPort}
         * @param atCoordList  See {@link #atCoordList}
         * @throws IllegalArgumentException if {@code atPort} and either {@code reqType != 'C'}
         *     or {@code atCoordList != null}
         */
        public Requirement(final char reqType, final int count, final boolean atPort, final String atCoordList)
            throws IllegalArgumentException
        {
            if (atPort)
            {
                if (reqType != 'C')
                    throw new IllegalArgumentException("atPort not implemented for reqType " + reqType);
                if (atCoordList != null)
                    throw new IllegalArgumentException("can't have atPort and atCoordList");
            }

            this.reqType = reqType;
            this.count = count;
            this.atPort = atPort;
            this.atCoordList = atCoordList;
        }

        /**
         * Equality test, mostly for testing/debugging.
         * @return true if {@code other} is a {@link Requirement} with same field contents
         */
        public final boolean equals(final Object other)
        {
            if (! (other instanceof Requirement))
                return false;
            final Requirement oth = (Requirement) other;
            return (reqType == oth.reqType)
                && (count == oth.count)
                && (atPort == oth.atPort)
                && ((atCoordList == null)
                    ? (oth.atCoordList == null)
                    : atCoordList.equals(oth.atCoordList));
        }

        /** String representation for debugging; same format as {@link #parse(String)}. */
        public final String toString()
        {
            StringBuilder sb = new StringBuilder();
            if (count != 1)
                sb.append(count);
            sb.append(reqType);
            if (atPort || (atCoordList != null))
            {
                sb.append('@');
                if (atPort)
                    sb.append('P');
                else
                    sb.append(atCoordList);
            }

            return sb.toString();
        }
    }

}
