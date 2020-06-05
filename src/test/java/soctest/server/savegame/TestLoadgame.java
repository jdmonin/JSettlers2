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

package soctest.server.savegame;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.server.SOCGameListAtServer;
import soc.server.genericServer.StringConnection;
import soc.server.savegame.GameLoaderJSON;
import soc.server.savegame.SavedGameModel;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameLoaderJSON} and {@link SavedGameModel}.
 *
 * @since 2.4.00
 */
public class TestLoadgame
{
    /** dummy server setup, to avoid IllegalStateException from {@link GameLoaderJSON} */
    @BeforeClass
    public static void setup()
    {
        SavedGameModel.glas = new SOCGameListAtServer(null);
    }

    /**
     * Attempt to load a savegame test artifact by calling {@link GameLoaderJSON#loadGame(File)}.
     * Doesn't postprocess or call {@link SavedGameModel#resumePlay(boolean)}.
     * If not found, will fail an {@code assertNotNull}.
     * @param testResFilename  Base name of test artifact, like {@code "classic-botturn.game.json"},
     *     to be loaded from {@code /src/test/resources/resources/savegame/}
     * @throws IOException if file can't be loaded
     */
    private static SavedGameModel load(final String testRsrcFilename)
        throws IOException
    {
        final String rsrcPath = "/resources/savegame/" + testRsrcFilename;
        final URL u = TestLoadgame.class.getResource(rsrcPath);
        assertNotNull("Couldn't find " + rsrcPath, u);
        final File f;
        try
        {
            f = new File(u.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("unlikely internal error", e);
        }

        return GameLoaderJSON.loadGame(f);
    }

    @Test
    public void testBasicLoading()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-botturn.game.json");
        final SOCGame ga = sgm.getGame();

        assertNotNull(ga.savedGameModel);
        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("game name", sgm.gameName, ga.getName());
        assertEquals(1, ga.getCurrentPlayerNumber());
        assertEquals("gamestate", SOCGame.PLAY1, sgm.gameState);
        assertEquals("gamestate", SOCGame.LOADING, ga.getGameState());

        assertEquals(SOCBoard.BOARD_ENCODING_ORIGINAL, ga.getBoard().getBoardEncodingFormat());
        assertEquals("robberHex", 155, ga.getBoard().getRobberHex());

        assertEquals(4, sgm.playerSeats.length);
        assertEquals(4, ga.maxPlayers);
        final String[] NAMES = {null, "robot 4", "robot 2", "debug"};
        final int[] TOTAL_VP = {0, 3, 2, 2};
        final int[][] RESOURCES = {null, {0, 1, 0, 2, 0}, {2, 2, 0, 0, 0}, {1, 3, 1, 0, 1}};
        final int[][] PIECE_COUNTS = {{15, 5, 4, 0}, {13, 4, 3, 0}, {13, 3, 4, 0}, {12, 3, 4, 0}};

        for (int pn = 0; pn < 4; ++pn)
        {
            final SavedGameModel.PlayerInfo pi = sgm.playerSeats[pn];
            final SOCPlayer pl = ga.getPlayer(pn);
            assertEquals(NAMES[pn], pi.name);
            assertEquals(NAMES[pn], pl.getName());
            assertEquals(TOTAL_VP[pn], pi.totalVP);
            assertEquals(TOTAL_VP[pn], pl.getTotalVP());

            final SOCResourceSet rs = (RESOURCES[pn] != null)
                ? new SOCResourceSet(RESOURCES[pn])
                : new SOCResourceSet();
            assertTrue(rs.equals(pl.getResources()));

            for (int ptype = SOCPlayingPiece.ROAD; ptype <= SOCPlayingPiece.CITY; ++ptype)
                assertEquals(PIECE_COUNTS[pn][ptype], pl.getNumPieces(ptype));
                // ROADS, SETTLEMENTS, CITIES == 0, 1, 2: tested in TestPlayingPiece
            assertEquals(PIECE_COUNTS[pn][3], pl.getNumKnights());

            // TODO piece locations, etc

            final boolean isVacant = (NAMES[pn] == null);
            assertEquals(isVacant, pi.isSeatVacant);
            assertEquals(isVacant, ga.isSeatVacant(pn));
            if (isVacant)
                continue;

            final boolean isBot = ! ("debug".equals(NAMES[pn]));
                // not a sufficient condition in all savegame artifacts, but works here
            assertEquals(isBot, pi.isRobot);
            assertEquals(isBot, pl.isRobot());
        }
    }

    /**
     * If any non-vacant player hasn't joined the game before {@link SavedGameModel#resumePlay(boolean)},
     * should throw an exception
     */
    @Test(expected=java.util.MissingResourceException.class)
    public void testNeedPlayers()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-botturn.game.json");
        sgm.resumePlay(true);
    }

    /** Test successful {@link SavedGameModel#resumePlay(boolean)} after all player connections have joined. */
    @Test
    public void testResumePlay()
        throws IOException
    {
        final String GAME_NAME = "classic";
        final SavedGameModel sgm = load("classic-botturn.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", GAME_NAME, sgm.gameName);
        assertTrue(ga.isSeatVacant(0));

        assertNotNull(sgm.findSeatsNeedingBots());
        SavedGameModel.glas.addGame(GAME_NAME, null, false);
            // this isn't the server-side call, but that one's much more work to set up for

        for (int pn = 1; pn < 4; ++pn)
        {
            final String pName = sgm.playerSeats[pn].name;

            assertFalse(ga.isSeatVacant(pn));

            // GameList membership required by findSeatsNeedingBots
            assertFalse(SavedGameModel.glas.isMember(pName, GAME_NAME));
            StringConnection sc = new StringConnection();
            sc.setData(pName);
            SavedGameModel.glas.addMember(sc, GAME_NAME);
            assertTrue(SavedGameModel.glas.isMember(pName, GAME_NAME));
        }

        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.PLAY1, ga.getGameState());
    }

    /** When loading a game that's over, should call {@link SavedGameModel#resumePlay(boolean)} successfully */
    @Test
    public void testLoadGameOverResume()
        throws IOException
    {
        final SavedGameModel sgm = load("classic-over.game.json");
        final SOCGame ga = sgm.getGame();

        assertEquals("game name", "classic", sgm.gameName);
        assertEquals("gamestate", SOCGame.OVER, sgm.gameState);

        sgm.resumePlay(true);
        assertEquals("gamestate", SOCGame.OVER, ga.getGameState());
        assertEquals(2, ga.getPlayerWithWin().getPlayerNumber());
        assertNull(ga.savedGameModel);
    }

}
