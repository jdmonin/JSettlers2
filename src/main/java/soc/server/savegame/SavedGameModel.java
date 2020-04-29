/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.server.savegame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.game.*;
import soc.message.SOCBoardLayout;
import soc.message.SOCBoardLayout2;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCMessage;
import soc.message.SOCPlayerElement;
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCPotentialSettlements;
import soc.server.SOCGameHandler;
import soc.server.SOCGameListAtServer;
import soc.server.genericServer.Connection;
import soc.util.Version;

/**
 * Data model for a game saved to/loaded from a file.
 *<P>
 * To save, use the {@link #SavedGameModel(SOCGame)} constructor and {@link GameSaverJSON}.
 * To load, use {@link #SavedGameModel()} and {@link GameLoaderJSON}.
 * See those constructors' javadocs for usage details.
 *<P>
 * This standalone model is cleaner than trying to serialize/deserialize {@link SOCGame}, SOCBoard, etc.
 *<P>
 * Like the optional database, this data model has a {@link #modelVersion} which may be older than the
 * current JSettlers version. See {@link #MODEL_VERSION} for lifecycle details.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.3.00
 */
public class SavedGameModel
{
    /**
     * Current model schema version: 2300 for v2.3.00.
     *<P>
     * Like the JSettlers database schema, this version may be older than the current JSettlers version.
     * The model version should change only when its fields require changes which would prevent
     * older JSettlers versions from understanding and loading a file using the new model.
     *<P>
     * Within the same schema version:
     *<UL>
     * <LI> New fields can be added, as long as they're optional and the game data is complete without them
     * <LI> When a newer JSettlers version loads an older file, such added fields will be their type's default value
     *      (0, null, etc)
     * <LI> When an older JSettlers version loads a file with added fields
     *      which aren't in its copy of the model, the GSON parser ignores them
     *</UL>
     * {@link #createLoadedGame()} will reject a loaded game if its {@link #modelVersion}
     * is newer than {@code MODEL_VERSION}, or if the game has features/options that it can't save.
     * If you need to make a saved-game file for use by multiple JSettlers versions, save it from the oldest version.
     *<P>
     * When {@code MODEL_VERSION} is changed, that will be documented here and in {@code /doc/Versions.md}.
     * The earliest version number is 2300.
     */
    public static int MODEL_VERSION = 2300;

    /** Server's game list, for checking name and creating game */
    public static SOCGameListAtServer glas;

    private transient SOCGame game = null;

    /* DATA FIELDS to be saved into file */

    /**
     * Model schema version when saved, in same format as {@link #MODEL_VERSION}.
     * See that field's javadoc for lifecycle details.
     */
    int modelVersion;

    /**
     * Version of JSettlers which saved this game file, from {@link Version#versionNumber()}.
     * This field is only for reference, not important when loading a saved game.
     * @see #modelVersion
     * @see #gameMinVersion
     */
    int savedByVersion;

    /** Game minimum version, from {@link SOCGame#getClientVersionMinRequired()} */
    int gameMinVersion;

    public String gameName;

    /** Game options (or null), from {@link SOCGameOption#packOptionsToString(Map, boolean)}. */
    String gameOptions;

    /** Game duration, from {@link SOCGame#getStartTime()} */
    int gameDurationSeconds;

    /**
     * Current gameState, from {@link SOCGame#getGameState()}
     * @see #oldGameState
     */
    public int gameState;

    /**
     * Old gameState, from {@link SOCGame#getOldGameState()}
     * @see #gameState
     */
    int oldGameState;

    /** Current dice roll results, from {@link SOCGame#getCurrentDice()} */
    int currentDice;

    /** First player number, current player, round count, etc. */
    HashMap<GEType, Integer> elements = new HashMap<>();

    /** Remaining unplayed dev cards, from {@link SOCGame#getDevCardDeck()} */
    int[] devCardDeck;

    /** Flag fields, from {@link SOCGame#getFlagFieldsForSave()} */
    boolean placingRobberForKnightCard, robberyWithPirateNotRobber,
        askedSpecialBuildPhase, movedShipThisTurn;

    /** Ships placed this turn if {@link SOCGame#hasSeaBoard}, from {@link SOCGame#getShipsPlacedThisTurn()}, or null */
    List<Integer> shipsPlacedThisTurn;

    /** Board layout and contents */
    BoardInfo boardInfo;

    /** Player info and empty seats. Size is {@link SOCGame#maxPlayers}. */
    public PlayerInfo[] playerSeats;

    /* End of DATA FIELDS */

    /**
     * Can this game be saved to a {@link SavedGameModel}, or does it have options or features
     * which haven't yet been implemented here?
     *<P>
     * Currently unsupported:
     *<UL>
     * <LI> Any game scenario: {@link SOCGameOption} {@code "SC"} != null
     *</UL>
     *
     * @param ga  Game to check; not null
     * @throws UnsupportedOperationException  if game has an option or feature not yet supported
     *     by {@link SavedGameModel}; {@link Throwable#getMessage()} will name the unsupported option/feature.
     * @see #checkCanLoad()
     */
    public static void checkCanSave(final SOCGame ga)
        throws UnsupportedOperationException
    {
        if (null != ga.getGameOptionStringValue("SC"))
            throw new UnsupportedOperationException("a scenario");

        // all current non-scenario game opts are supported
    }

    /**
     * Create an empty SavedGameModel to load a game file into.
     * Once data is loaded and {@link #createLoadedGame()} is called,
     * state will temporarily be {@link SOCGame#LOADING}
     * and {@link SOCGame#savedGameModel} will be this SGM.
     * Call {@link #resumePlay(boolean)} to continue play.
     */
    public SavedGameModel()
    {
    }

    /**
     * Create a SavedGameModel to save as a game file.
     * Game state must be {@link SOCGame#ROLL_OR_CARD} or higher.
     * @param ga  Game data to save; not null
     * @throws UnsupportedOperationException  if game has an option or feature not yet supported
     *     by {@link SavedGameModel}; see {@link #checkCanSave(SOCGame)} for details.
     * @throws IllegalStateException if game state &lt; {@link SOCGame#ROLL_OR_CARD}
     * @throws IllegalArgumentException if {@link SOCGameHandler#getBoardLayoutMessage(SOCGame)}
     *     returns an unexpected layout message type
     */
    public SavedGameModel(final SOCGame ga)
        throws UnsupportedOperationException, IllegalStateException, IllegalArgumentException
    {
        this();

        checkCanSave(ga);

        if (ga.getGameState() < SOCGame.ROLL_OR_CARD)
            throw new IllegalStateException("gameState");

        modelVersion = MODEL_VERSION;
        savedByVersion = Version.versionNumber();
        game = ga;

        // save data fields:
        gameName = ga.getName();
        final Map<String, SOCGameOption> opts = ga.getGameOptions();
        if (opts != null)
            gameOptions = SOCGameOption.packOptionsToString(opts, false);
        gameDurationSeconds = (int) (((System.currentTimeMillis() - ga.getStartTime().getTime()) + 500) / 1000L);
            // same rounding calc as SSMH.processDebugCommand_gameStats
        gameState = ga.getGameState();
        oldGameState = ga.getOldGameState();
        currentDice = ga.getCurrentDice();
        gameMinVersion = ga.getClientVersionMinRequired();
        devCardDeck = ga.getDevCardDeck();

        final boolean[] flags = ga.getFlagFieldsForSave();
        placingRobberForKnightCard = flags[0];
        robberyWithPirateNotRobber = flags[1];
        askedSpecialBuildPhase = flags[2];
        movedShipThisTurn = flags[3];

        shipsPlacedThisTurn = ga.getShipsPlacedThisTurn();

        {
            final SOCPlayer lrPlayer = ga.getPlayerWithLongestRoad(),
                            laPlayer = ga.getPlayerWithLargestArmy();

            elements.put(GEType.ROUND_COUNT, ga.getRoundCount());
            elements.put(GEType.FIRST_PLAYER, ga.getFirstPlayer());
            elements.put(GEType.CURRENT_PLAYER, game.getCurrentPlayerNumber());
            elements.put(GEType.LONGEST_ROAD_PLAYER, (lrPlayer != null) ? lrPlayer.getPlayerNumber() : -1);
            elements.put(GEType.LARGEST_ARMY_PLAYER, (laPlayer != null) ? laPlayer.getPlayerNumber() : -1);
            if (gameState == SOCGame.SPECIAL_BUILDING)
                elements.put(GEType.SPECIAL_BUILDING_AFTER_PLAYER, ga.getSpecialBuildingPlayerNumberAfter());
        }

        boardInfo = new BoardInfo(ga);

        playerSeats = new PlayerInfo[ga.maxPlayers];
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
            playerSeats[pn] = new PlayerInfo(ga.getPlayer(pn), ga.isSeatVacant(pn));
    }

    /**
     * Get the completely loaded game, or the game which was "saved" into this model.
     * @return Game, or {@code null} if not loaded successfully
     */
    public SOCGame getGame()
    {
        return game;
    }

    /**
     * Resume play of a loaded game: Check any constraints, update gameState.
     * @param ignoreConstraints  If true, don't check any {@link Constraint}s in the model
     * @return game ready to play, with {@link SOCGame#getGameState()} same as when it was saved
     * @throws UnsupportedOperationException if gameState != {@link SOCGame#LOADING}
     * @throws IllegalStateException if a constraint is not met
     */
    public SOCGame resumePlay(final boolean ignoreConstraints)
        throws UnsupportedOperationException, IllegalStateException
    {
        if (game.getGameState() != SOCGame.LOADING)
            throw new UnsupportedOperationException("gameState");

        // TODO maybe check constraints

        game.lastActionTime = System.currentTimeMillis();
        game.setGameState(gameState);

        return game;
    }

    /**
     * Can the game data loaded into this {@link SavedGameModel} become a {@link SOCGame}
     * in {@link #createLoadedGame()}, or does it have options or features which haven't yet been implemented here?
     *<P>
     * See {@link #checkCanSave(SOCGame)} for list of unsupported features checked here.
     *<P>
     * This is needed because within the same {@link #MODEL_VERSION}, a new JSettlers version could
     * add fields or logic to support saving/loading more features; for example, certain scenarios but not all.
     * The older version isn't able to load that saved game.
     *
     * @throws NoSuchElementException if loaded data's model schema version ({@link #modelVersion} field)
     *     is newer than the current {@link SavedGameModel#MODEL_VERSION}
     *     and important fields might not be in our version of the model.
     *     Exception's {@link Throwable#getMessage()} will be of the form:
     *     "model version 9170 newer than our version 2300"
     * @throws UnsupportedOperationException if game has an option or feature not yet supported
     *     by {@link #createLoadedGame()}. {@link Throwable#getMessage()} will name the unsupported option/feature
     *     or the problematic game opt from {@link SOCGameOption#parseOptionsToMap(String)}.
     *     In that case, {@link Throwable#getMessage()} will contain that method's IllegalArgumentException message
     *     and {@link Throwable#getCause()} will not be null.
     */
    public void checkCanLoad()
        throws NoSuchElementException, UnsupportedOperationException
    {
        if (modelVersion > MODEL_VERSION)
            throw new NoSuchElementException
                ("model version " + modelVersion + " newer than our version " + MODEL_VERSION);

        if ((gameOptions == null) || gameOptions.isEmpty())
            return;

        Map<String, SOCGameOption> opts;
        try
        {
            opts = SOCGameOption.parseOptionsToMap(gameOptions);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("Problem opt in gameOptions: " + e.getMessage(), e);
        }
        if (opts == null)
            return;

        final SOCGameOption oSC = opts.get("SC");
        if ((oSC != null) && (null != oSC.getStringValue()))
            throw new UnsupportedOperationException("a scenario");

        // all current non-scenario game opts are supported
    }

    /**
     * Try to create the {@link SOCGame} and its objects based on data loaded into this SGM.
     * Calls {@link #checkCanLoad()}. If successful (no exception thrown), game state will be {@link SOCGame#LOADING}.
     * Doesn't add to game list {@link #glas} or check whether game name is already taken, because
     * {@link soc.server.SOCServer#createOrJoinGame(Connection, int, String, Map, SOCGame, int)}
     * will rename the loaded game to avoid name collisions.
     *
     * @throws IllegalStateException if this method's already been called
     *     or if required static game list field {@link SavedGameModel#glas} is null
     * @throws NoSuchElementException if loaded data's model schema version ({@link #modelVersion} field)
     *     is newer than the current {@link SavedGameModel#MODEL_VERSION};
     *     see {@link #checkCanLoad()} for details
     * @throws UnsupportedOperationException if loaded game model has an option or feature not yet supported
     *     by {@code createLoadedGame()}; see {@link #checkCanLoad()} for details
     */
    /*package*/ void createLoadedGame()
        throws IllegalStateException, NoSuchElementException, UnsupportedOperationException
    {
        if (game != null)
            throw new IllegalStateException("already called createLoadedGame");
        if (glas == null)
            throw new IllegalStateException("SavedGameModel.glas is null");

        checkCanLoad();

        // TODO what if name invalid/some other inconsistency/unable to create? throw an exception?
        //    also gameMinVersion vs server version

        final SOCGame ga = new SOCGame(gameName, SOCGameOption.parseOptionsToMap(gameOptions));
        ga.initAtServer();
        ga.setGameState(SOCGame.LOADING);
        game = ga;
        ga.savedGameModel = this;
        ga.setTimeSinceCreated(gameDurationSeconds);
        ga.setCurrentDice(currentDice);
        if (devCardDeck == null)
            devCardDeck = new int[0];
        ga.setFieldsForLoad
            (devCardDeck, oldGameState, shipsPlacedThisTurn,
             placingRobberForKnightCard, robberyWithPirateNotRobber, askedSpecialBuildPhase, movedShipThisTurn);
        if (elements != null)
            for (GEType elem : elements.keySet())
                SOCDisplaylessPlayerClient.handleGAMEELEMENT(ga, elem, elements.get(elem));

        boardInfo.loadInto(ga);
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            final SOCPlayer pl = ga.getPlayer(pn);
            final PlayerInfo pinfo = playerSeats[pn];
            if (! pinfo.isSeatVacant)
                ga.addPlayer(pinfo.name, pn);
            pinfo.loadInto(pl);
        }
    }

    /**
     * Info on one player position sitting in the game.
     * @see SOCPlayer
     * @see soc.server.SOCClientData
     */
    public static class PlayerInfo
    {
        public String name;
        public boolean isSeatVacant;
        int totalVP;
        public boolean isRobot, isBuiltInRobot;
        int faceID;

        /** Resources in hand */
        KnownResourceSet resources;

        /** Available piece counts, SVP, cloth count, hasPlayedDevCard and other flags, etc. */
        HashMap<PEType, Integer> elements = new HashMap<>();

        // TODO: future: add field for pl.getCurrentOffer()

        /** Resource roll stats, from {@link SOCPlayer#getResourceRollStats()} */
        int[] resRollStats;

        /**
         * Standard dev card types in player's hand,
         * received in current turn (new) or previous turns
         * (playable or kept until end of game).
         * Each item is a card type like {@link SOCDevCardConstants#ROADS},
         * from {@link SOCInventoryItem#itype} field.
         */
        ArrayList<Integer> oldDevCards = new ArrayList<>(),
                           newDevCards = new ArrayList<>();
        // TODO: future: support general SOCInventoryItems/SOCSpecialItems for scenarios

        /**
         * Player's pieces, from {@link SOCPlayer#getPieces()}.
         * @see #fortressPiece
         */
        ArrayList<SOCPlayingPiece> pieces = new ArrayList<>();

        /*
         * Player's fortress, if any, from {@link SOCPlayer#getFortress()}; usually null.
         * Not part of {@link #pieces} list.
         * This field was part of the experimental early SGM; no released version supports scenarios yet.
         * TODO: SGM in a future JSettlers version should support "special pieces" in a more general way.
        SOCFortress fortressPiece;
         */

        PlayerInfo(final SOCPlayer pl, final boolean isVacant)
        {
            final SOCGame ga = pl.getGame();

            name = pl.getName();
            isSeatVacant = isVacant;
            totalVP = pl.getTotalVP();
            isRobot = pl.isRobot();
            isBuiltInRobot = pl.isBuiltInRobot();
            faceID = pl.getFaceId();
            resources = new KnownResourceSet(pl.getResources());

            elements.put(PEType.NUMKNIGHTS, pl.getNumKnights());
            elements.put(PEType.ROADS, pl.getNumPieces(SOCPlayingPiece.ROAD));
            elements.put(PEType.SETTLEMENTS, pl.getNumPieces(SOCPlayingPiece.SETTLEMENT));
            elements.put(PEType.CITIES, pl.getNumPieces(SOCPlayingPiece.CITY));
            if (! isVacant)
            {
                if (pl.getNeedToDiscard())
                    elements.put(PEType.DISCARD_FLAG, 1);
                else
                {
                    int n = pl.getNeedToPickGoldHexResources();
                    if (n != 0)
                        elements.put(PEType.NUM_PICK_GOLD_HEX_RESOURCES, n);
                }
                if (pl.hasPlayedDevCard())
                    elements.put(PEType.PLAYED_DEV_CARD_FLAG, 1);
                if (ga.maxPlayers > 4)
                {
                    if (pl.hasAskedSpecialBuild())
                        elements.put(PEType.ASK_SPECIAL_BUILD, 1);
                    if (ga.getGameState() == SOCGame.SPECIAL_BUILDING)
                        elements.put(PEType.HAS_SPECIAL_BUILT, (pl.hasSpecialBuilt()) ? 1 : 0);
                }
            }
            if (ga.hasSeaBoard)
            {
                elements.put(PEType.SHIPS, pl.getNumPieces(SOCPlayingPiece.SHIP));
                int n = pl.getNumWarships();
                if (n != 0)
                    elements.put(PEType.SCENARIO_WARSHIP_COUNT, n);
            }

            resRollStats = pl.getResourceRollStats();

            final SOCInventory cardsInv = pl.getInventory();
            for (SOCInventoryItem item : cardsInv.getByState(SOCInventory.NEW))
                if (item instanceof SOCDevCard)
                    newDevCards.add(item.itype);
            for (int dcState = SOCInventory.PLAYABLE; dcState <= SOCInventory.KEPT; ++dcState)
                for (SOCInventoryItem item : cardsInv.getByState(dcState))
                    if (item instanceof SOCDevCard)
                        oldDevCards.add(item.itype);
            // TODO other inventory item types: see SGH.sitDown_sendPrivateInfo

            pieces.addAll(pl.getPieces());
            // fortressPiece = pl.getFortress();
        }

        /**
         * Load PlayerInfo fields into this SOCPlayer.
         * If seat isn't vacant, call {@link SOCGame#addPlayer(String, int)} before calling this.
         */
        void loadInto(final SOCPlayer pl)
        {
            pl.setName(name);
            // TODO set totalVP/SVP
            pl.setRobotFlag(isRobot, isBuiltInRobot);
            pl.setFaceId(faceID);
            resources.loadInto(pl.getResources());

            final SOCGame ga = pl.getGame();
            final int pn = pl.getPlayerNumber();

            if ((resRollStats != null) && (resRollStats.length > 0))
                pl.setResourceRollStats(resRollStats);

            {
                final SOCInventory inv = pl.getInventory();
                for (final int ctype : oldDevCards)
                    inv.addDevCard(1, SOCInventory.OLD, ctype);
                for (final int ctype : newDevCards)
                    inv.addDevCard(1, SOCInventory.NEW, ctype);
            }

            final SOCBoard b = ga.getBoard();
            for (SOCPlayingPiece pp : pieces)
            {
                // TODO handle SOCVillage
                pp.setGameInfo(pl, b);
                ga.putPiece(pp);
            }
            /*
            if (fortressPiece != null)
            {
                fortressPiece.setGameInfo(pl, b);
                ga.putPiece(fortressPiece);
            }
             */

            // Set player elements only after putPieces,
            // so remaining-piece counts aren't reduced twice
            for (final PEType et : elements.keySet())
                SOCDisplaylessPlayerClient.handlePLAYERELEMENT
                    (ga, pl, pn, SOCPlayerElement.SET, et, elements.get(et), null);
        }
    }

    /**
     * Set of the 5 known resource types, to use in saved game
     * instead of raw 7-element int array from {@link SOCResourceSet}.
     */
    static class KnownResourceSet
    {
        public int clay, ore, sheep, wheat, wood;

        public KnownResourceSet(SOCResourceSet rs)
        {
            clay  = rs.getAmount(SOCResourceConstants.CLAY);
            ore   = rs.getAmount(SOCResourceConstants.ORE);
            sheep = rs.getAmount(SOCResourceConstants.SHEEP);
            wheat = rs.getAmount(SOCResourceConstants.WHEAT);
            wood  = rs.getAmount(SOCResourceConstants.WOOD);
        }

        /** Load resource-type counts from this KnownResourceSet into {@code rs}. */
        public void loadInto(SOCResourceSet rs)
        {
            rs.setAmounts(new SOCResourceSet(clay, ore, sheep, wheat, wood, 0));
        }
    }

    /**
     * Board layout and contents.
     * Leverages {@link SOCGameHandler#getBoardLayoutMessage(SOCGame)}
     * to gather layout's elements into a generalized format.
     *<P>
     * The board's basic encoding format is stored here as {@code layout2.boardEncodingFormat}.
     * If layout2 is null, it uses layout1 and boardEncodingFormat is {@link SOCBoard#BOARD_ENCODING_ORIGINAL}.
     * Different encodings can use different coordinate systems and layout parts;
     * see {@link SOCBoard#getBoardEncodingFormat()} javadoc for details.
     *<P>
     * Board height and width aren't recorded here; they're constant based on
     * encoding format, unless game option {@code "_BHW"} specifies otherwise.
     *
     * @see SOCBoard
     */
    static class BoardInfo
    {
        /** Board layout elements, or null if using {@link #layout2} */
        SOCBoardLayout layout1;

        /** Board layout elements and encodingFormat, or null if using {@link #layout1} */
        SOCBoardLayout2 layout2;

        /**
         * Players' potential settlements and related values.
         * Will have either 1 per player, or 1 for all players (playerNumber == -1).
         * From {@link SOCGameHandler#gatherBoardPotentials(SOCGame, int)}.
         */
        SOCPotentialSettlements[] playerPotentials;

        /**
         * @throws IllegalArgumentException if {@link SOCGameHandler#getBoardLayoutMessage(SOCGame)}
         *     returns an unexpected layout message type
         */
        BoardInfo(final SOCGame ga)
            throws IllegalArgumentException
        {
            SOCMessage m = SOCGameHandler.getBoardLayoutMessage(ga);
            if (m instanceof SOCBoardLayout)
                layout1 = (SOCBoardLayout) m;
            else if (m instanceof SOCBoardLayout2)
                layout2 = (SOCBoardLayout2) m;
            else
                throw new IllegalArgumentException
                    ("unexpected boardlayout msg type " + m.getType() + " " + m.getClass().getSimpleName());

            playerPotentials = SOCGameHandler.gatherBoardPotentials(ga, Integer.MAX_VALUE);
        }

        void loadInto(final SOCGame ga)
        {
            if (layout2 != null)
                SOCDisplaylessPlayerClient.handleBOARDLAYOUT2(layout2, ga);
            else if (layout1 != null)
                SOCDisplaylessPlayerClient.handleBOARDLAYOUT(layout1, ga);

            for (final SOCPotentialSettlements potenMsg : playerPotentials)
                SOCDisplaylessPlayerClient.handlePOTENTIALSETTLEMENTS(potenMsg, ga);
        }
    }

    /**
     * A constraining condition requested before resuming the game.
     * For example, player 3 must be a "faster" built-in bot, or be a certain third-party bot class.
     * Constraints can be ignored when resuming.
     */
    public static class Constraint
    {
        // TBD
    }

}
