/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2021 Jeremy D Monin <jeremy@nand.net>
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

package soctest.server;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.NoSuchElementException;

import soc.extra.server.GameEventLog;
import soc.extra.server.GameEventLog.QueueEntry;
import soc.message.SOCBuildRequest;
import soc.message.SOCDiceResult;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCPutPiece;
import soc.message.SOCVersion;
import soc.server.SOCServer;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameEventLog} and its {@link GameEventLog.QueueEntry},
 * including {@code .soclog} file parsing.
 *
 * @see TestRecorder#testNewGameFirstLogEntries()
 * @since 2.5.00
 */
public class TestGameEventLog
{
    /**
     * Attempt to load a {@code .soclog} game event test artifact
     * by calling {@link GameEventLog#load(File, boolean, boolean)}.
     * If not found, will fail an {@code assertNotNull}. Doesn't try to catch
     * {@code load(..)}'s declared runtime exceptions, but does declare them here
     * as {@code throws} for caller to optionally catch.
     *
     * @param testResFilename  Base name of test artifact, like {@code "classic-botturn.game.json"},
     *     to be loaded from {@code /src/test/resources/resources/gameevent/}
     * @param ignoreComments  Parameter to pass to {@code GameEventLog.load(..)}
     * @param serverOnly  Parameter to pass to {@code GameEventLog.load(..)}
     * @return loaded and parsed log file
     * @throws IOException if file can't be loaded
     * @throws ParseException if thrown by {@code GameEventLog.load(..)}
     * @throws NoSuchElementException if thrown by {@code GameEventLog.load(..)}
     * @see soctest.server.savegame.TestLoadgame#load(String, SOCServer)
     */
    public static GameEventLog load
        (final String testRsrcFilename, final boolean ignoreComments, final boolean serverOnly)
        throws IOException, ParseException, NoSuchElementException
    {
        final String rsrcPath = "/resources/gameevent/" + testRsrcFilename;
        final URL u = TestGameEventLog.class.getResource(rsrcPath);
        assertNotNull("Couldn't find " + rsrcPath, u);
        final File f;
        try
        {
            f = new File(u.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("unlikely internal error", e);
        }

        return GameEventLog.load(f, ignoreComments, serverOnly);
    }

    /**
     * Comprehensive tests for {@link GameEventLog.QueueEntry}: Constructors, toString,
     * corresponding {@link GameEventLog.QueueEntry#parse(String)}.
     *<P>
     * More tests for {@link QueueEntry#parse(String)} are in {@link #testQueueEntryParse()}.
     *
     * @throws ParseException if parsing fails unexpectedly
     */
    @Test
    public void testQueueEntry()
        throws ParseException
    {
        final SOCBuildRequest event = new SOCBuildRequest("testgame", 2);

        QueueEntry qe = new QueueEntry(event, -1, false);
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertNull(qe.comment);
        assertEquals("all:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, new int[]{3});
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{3}, qe.excludedPN);
        assertNull(qe.comment);
        assertEquals("!p3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("!p3:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, new int[]{2,3,4});
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertArrayEquals(new int[]{2,3,4}, qe.excludedPN);
        assertNull(qe.comment);
        assertEquals("!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, SOCServer.PN_OBSERVER, false);
        assertFalse(qe.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("ob:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("ob:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, SOCServer.PN_REPLY_TO_UNDETERMINED, false);
        assertFalse(qe.isFromClient);
        assertEquals(SOCServer.PN_REPLY_TO_UNDETERMINED, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("un:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("un:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, 3, true);
        assertTrue(qe.isFromClient);
        assertEquals(3, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertNull(qe.comment);
        assertEquals("f3:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("f3:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(event, SOCServer.PN_OBSERVER, true);
        assertTrue(qe.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("fo:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("fo:SOCBuildRequest:game=testgame|pieceType=2"));

        // Should use PN_OBSERVER not -1 for observer client, so make sure -1 doesn't result in "fo:" or "all:"
        qe = new QueueEntry(event, -1, true);
        assertTrue(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertEquals(event, qe.event);
        assertNull(qe.excludedPN);
        assertEquals("f-1:SOCBuildRequest:game=testgame|pieceType=2", qe.toString());
        assertEquals(qe, QueueEntry.parse("f-1:SOCBuildRequest:game=testgame|pieceType=2"));

        qe = new QueueEntry(null, -1, false);
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("all:null", qe.toString());
        assertEquals(qe, QueueEntry.parse("all:null"));

        qe = new QueueEntry(null, 3, false);
        assertFalse(qe.isFromClient);
        assertEquals(3, qe.pn);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("p3:null", qe.toString());
        assertEquals(qe, QueueEntry.parse("p3:null"));

        qe = new QueueEntry("abcde");
        assertFalse(qe.isFromClient);
        assertEquals(-1, qe.pn);
        assertNull(qe.event);
        assertNull(qe.excludedPN);
        assertEquals("abcde", qe.comment);
        assertEquals("#abcde", qe.toString());
        assertEquals(qe, QueueEntry.parse("#abcde"));

        qe = new QueueEntry(" abcde ");
        assertNull(qe.event);
        assertEquals("# abcde ", qe.toString());
        assertEquals(qe, QueueEntry.parse("# abcde "));

        try
        {
            qe = new QueueEntry(null, 3, true);
            fail("Should throw IllegalArgumentException if isFromClient and event=null");
        } catch (IllegalArgumentException e) {}

        try
        {
            final SOCVersion vmsg = new SOCVersion(1100, "1.1.00", "", null, null);
            assertFalse(vmsg instanceof SOCMessageForGame);
            qe = new QueueEntry(vmsg, 3, true);
            fail("Should throw IllegalArgumentException if isFromClient and event not SOCMessageForGame");
        } catch (IllegalArgumentException e) {}

        try
        {
            qe = new QueueEntry(null);
            fail("Should throw IllegalArgumentException for comment constructor if comment=null");
        } catch (IllegalArgumentException e) {}
    }

    /**
     * Various negative and positive tests for {@link QueueEntry#parse(String)}.
     * More positive tests are included in {@link #testQueueEntry()}.
     * @throws ParseException if parsing fails unexpectedly
     */
    @Test
    public void testQueueEntryParse()
        throws ParseException
    {
        QueueEntry qe;

        assertNull(QueueEntry.parse(null));
        assertNull(QueueEntry.parse(""));

        qe = QueueEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=2");
        assertNotNull(qe);
            // Detailed parsing of that is done in testQueueEntry;
            // here we just want to show what successfully parses,
            // to narrow down why these next parses should fail

        try
        {
            qe = QueueEntry.parse(":SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException when nothing before ':'");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("all:");
            fail("Should throw ParseException when nothing after ':'");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException when missing ':'");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("all:xyz");
            fail("Should throw ParseException when not a SOCMessage after ':'");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=X");
            fail("Should throw ParseException when can't parse expected message fields after ':'");
        } catch (ParseException e) {}

        for(String userSpec : new String[]{ "p", "p_", "f", "f_", "fz", "al", "xy", "_", "!p", "!pz", "!p_, !p]" })
            try
            {
                qe = QueueEntry.parse(userSpec + ":SOCBuildRequest:game=testgame|pieceType=2");
                fail("Should throw ParseException for invalid user spec " + userSpec + ':');
            } catch (ParseException e) {}

        // Various problems after !p :

        try
        {
            qe = QueueEntry.parse("!p[]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for empty !p[]");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("!p[:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for missing ']'");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("!p[X]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for non-digit in [] list");
        } catch (ParseException e) {}

        try
        {
            qe = QueueEntry.parse("!p[5, X]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for non-digit in [] list");
        } catch (ParseException e) {}
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, boolean)}
     * on the known-good {@code all-basic-actions.soclog} artifact.
     */
    @Test
    public void testLoadKnownGood()
        throws NoSuchElementException, IOException, ParseException
    {
        final GameEventLog log = load("all-basic-actions.soclog", false, false);
        final int EXPECTED_FILE_LINE_COUNT = 778;  // length from wc -l

        assertNotNull(log);
        assertEquals("test", log.gameName);
        assertEquals(2500, log.version);
        assertEquals("BC=t4,N7=f7,RD=f,SBL=t,PL=4", log.optsStr);
        assertFalse(log.entries.isEmpty());
        assertEquals(EXPECTED_FILE_LINE_COUNT, log.numLines);
        assertEquals(log.numLines, 1 + log.entries.size());  // true if no blank lines

        // comment-line parsing
        assertEquals(" Game created at: 2021-10-10 22:48:46 -0400", log.entries.get(0).comment);

        // spot-check a couple of parsed messages:

        SOCMessage msg = log.entries.get(17).event;
        assertTrue("Line 19 expected SOCDiceResult, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCDiceResult);
        assertEquals("test", ((SOCDiceResult) msg).getGame());
        assertEquals(-1, ((SOCDiceResult) msg).getResult());

        msg = log.entries.get(EXPECTED_FILE_LINE_COUNT - 16 - 2).event;
        assertTrue
            ("Line " + (EXPECTED_FILE_LINE_COUNT - 16) + " expected SOCPutPiece, got " +
                 ((msg != null) ? msg.getClass().getSimpleName() : "null"),
             msg instanceof SOCPutPiece);
        assertEquals("test", ((SOCPutPiece) msg).getGame());
        assertEquals(2, ((SOCPutPiece) msg).getPieceType());
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, boolean)}
     * on soclog artifacts missing various required items:
     *<UL>
     * <LI> {@code test-missing-header.soclog}: missing required header line
     * <LI> {@code test-missing-header-version.soclog}: header is missing version=
     * <LI> {@code test-missing-header-version-numparse.soclog}: header's version= isn't a parseable number
     * <LI> {@code test-missing-header-gamename.soclog}: header is missing game_name=
     * <LI> {@code test-missing-header-gamename-empty.soclog}: header's game_name= has no value
     * <LI> {@code test-missing-header-gamename-invalid.soclog}: header's game_name= fails
     *      {@link soc.message.SOCMessage#isSingleLineAndSafe(String)}
     * <LI> {@code test-version-too-old.soclog}: version too old (nonexistent 2.4.99)
     * <LI> {@code test-missing-socversion.soclog}: Missing required SOCVersion message
     * <LI> {@code test-missing-socnewgame.soclog}: Missing required SOCNewGame / SOCNewGameWithOptions message
     * <LI> {@code test-version-mismatch.soclog}: version inconsistent in header vs SOCVersion
     * <LI> {@code test-gamename-mismatch-newgame.soclog}, {@code test-gamename-mismatch-newgamewithopts.soclog}:
     *      game name inconsistent in header vs SOCNewGame / SOCNewGameWithOptions
     *</UL>
     */
    @Test
    public void testLoadIncompletes()
        throws NoSuchElementException, IOException, ParseException
    {
        try
        {
            load("test-missing-header.soclog", false, false);
            fail("test-missing-header.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("header line"));
        }

        try
        {
            load("test-missing-header-version.soclog", false, false);
            fail("test-missing-header-version.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("header line"));
        }

        try
        {
            load("test-missing-header-version-numparse.soclog", false, false);
            fail("test-missing-header-version-numparse.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().equals("Couldn't parse version number in header"));
        }

        try
        {
            load("test-missing-header-gamename.soclog", false, false);
            fail("test-missing-header.gamename: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("missing required game_name"));
        }

        try
        {
            load("test-missing-header-gamename-empty.soclog", false, false);
            fail("test-missing-header.gamename-empty: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Empty game_name"));
        }

        try
        {
            load("test-missing-header-gamename-invalid.soclog", false, false);
            fail("test-missing-header.gamename-invalid: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Invalid game_name"));
        }

        try
        {
            load("test-version-too-old.soclog", false, false);
            fail("test-version-too-old.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().startsWith("Minimum version for format is "));
        }

        try
        {
            load("test-missing-socversion.soclog", false, false);
            fail("test-missing-socversion.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().equals("First event message must be SOCVersion"));
        }

        try
        {
            load("test-missing-socnewgame.soclog", false, false);
            fail("test-missing-socnewgame.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().equals("Second event message must be SOCNewGame or SOCNewGameWithOptions"));
        }

        try
        {
            load("test-version-mismatch.soclog", false, false);
            fail("test-version-mismatch.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("in SOCVersion differs from header"));
        }

        try
        {
            load("test-gamename-mismatch-newgame.soclog", false, false);
            fail("test-gamename-mismatch-newgame.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Game name differs from header"));
        }

        try
        {
            load("test-gamename-mismatch-newgamewithopts.soclog", false, false);
            fail("test-gamename-mismatch-newgamewithopts.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Game name differs from header"));
        }

    }

    /**
     * Tests {@link GameEventLog#load(File, boolean, boolean)} behavior
     * when given a logfile in STAC v1 format {@code test-stac-legacy-human-league1-sample.soclog}:
     * Should refuse to parse, but throw {@link NoSuchElementException} instead of generic {@link ParseException}.
     */
    @Test
    public void testLoadStacV1()
        throws IOException, ParseException
    {
        try
        {
            load("test-stac-legacy-human-league1-sample.soclog", false, false);
            fail("test-stac-legacy-human-league1-sample.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("must start with \"SOC game event log\" header"));
        }
    }

}
