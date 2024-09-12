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
import soc.extra.server.GameEventLog.EventEntry;
import soc.game.SOCGame;
import soc.message.SOCBuildRequest;
import soc.message.SOCDiceResult;
import soc.message.SOCGameTextMsg;
import soc.message.SOCMessage;
import soc.message.SOCMessageForGame;
import soc.message.SOCPutPiece;
import soc.message.SOCRobberyResult;
import soc.message.SOCVersion;
import soc.server.SOCServer;
import soc.util.Version;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link GameEventLog} and its {@link GameEventLog.EventEntry},
 * including {@code .soclog} file parsing.
 *
 * @see TestRecorder#testNewGameFirstLogEntries()
 * @since 2.5.00
 */
public class TestGameEventLog
{
    /**
     * Attempt to load a {@code .soclog} game event test artifact
     * by calling {@link GameEventLog#load(File, boolean, int)}.
     * If not found, will fail an {@code assertNotNull}. Doesn't try to catch
     * {@code load(..)}'s declared runtime exceptions, but does declare them here
     * as {@code throws} for caller to optionally catch.
     *
     * @param testResFilename  Base name of test artifact, like {@code "classic-botturn.game.json"},
     *     to be loaded from {@code /src/test/resources/resources/gameevent/}
     * @param ignoreComments  Parameter to pass to {@code GameEventLog.load(..)}
     * @param filterAtClientPN  Parameter to pass to {@code GameEventLog.load(..)}
     * @return loaded and parsed log file
     * @throws IOException if file can't be loaded
     * @throws ParseException if thrown by {@code GameEventLog.load(..)}
     * @throws NoSuchElementException if thrown by {@code GameEventLog.load(..)}
     * @see soctest.server.savegame.TestLoadgame#load(String, SOCServer)
     */
    public static GameEventLog load
        (final String testRsrcFilename, final boolean ignoreComments, final int filterAtClientPN)
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

        return GameEventLog.load(f, ignoreComments, filterAtClientPN);
    }

    /**
     * Comprehensive tests for {@link GameEventLog.EventEntry}: Constructors, toString,
     * corresponding {@link GameEventLog.EventEntry#parse(String)}.
     *<P>
     * More tests for {@link EventEntry#parse(String)} are in {@link #testEventEntryParse()}.
     *
     * @throws ParseException if parsing fails unexpectedly
     */
    @Test
    public void testEventEntry()
        throws ParseException
    {
        final SOCBuildRequest event = new SOCBuildRequest("testgame", 2);

        EventEntry ee = new EventEntry(event, -1, false, -1);
        assertFalse(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertNull(ee.comment);
        assertEquals("all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, new int[]{3}, -1);
        assertFalse(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertEquals(event, ee.event);
        assertArrayEquals(new int[]{3}, ee.excludedPN);
        assertEquals(-1, ee.timeElapsedMS);
        assertNull(ee.comment);
        assertEquals("!p3:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("!p3:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, new int[]{2,3,4}, 77);
        assertFalse(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertEquals(event, ee.event);
        assertArrayEquals(new int[]{2,3,4}, ee.excludedPN);
        assertEquals(77, ee.timeElapsedMS);
        assertNull(ee.comment);
        assertEquals("0:00.077:!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("0:00.077:!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, SOCServer.PN_OBSERVER, false, 61077);
        assertFalse(ee.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertEquals("1:01.077:ob:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("1:01.077:ob:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, SOCServer.PN_REPLY_TO_UNDETERMINED, false, -1);
        assertFalse(ee.isFromClient);
        assertEquals(SOCServer.PN_REPLY_TO_UNDETERMINED, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertEquals("un:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("un:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, 3, true, -1);
        assertTrue(ee.isFromClient);
        assertEquals(3, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertNull(ee.comment);
        assertEquals("f3:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("f3:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(event, SOCServer.PN_OBSERVER, true, -1);
        assertTrue(ee.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertEquals("fo:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("fo:SOCBuildRequest:game=testgame|pieceType=2"));

        // Should use PN_OBSERVER not -1 for observer client, so make sure -1 doesn't result in "fo:" or "all:"
        ee = new EventEntry(event, -1, true, -1);
        assertTrue(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertEquals(event, ee.event);
        assertNull(ee.excludedPN);
        assertEquals("f-1:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        assertEquals(ee, EventEntry.parse("f-1:SOCBuildRequest:game=testgame|pieceType=2"));

        ee = new EventEntry(null, -1, false, -1);
        assertFalse(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertNull(ee.event);
        assertNull(ee.excludedPN);
        assertEquals("all:null", ee.toString());
        assertEquals(ee, EventEntry.parse("all:null"));

        ee = new EventEntry(null, 3, false, -1);
        assertFalse(ee.isFromClient);
        assertEquals(3, ee.pn);
        assertNull(ee.event);
        assertNull(ee.excludedPN);
        assertEquals("p3:null", ee.toString());
        assertEquals(ee, EventEntry.parse("p3:null"));

        ee = new EventEntry("abcde");
        assertFalse(ee.isFromClient);
        assertEquals(-1, ee.pn);
        assertNull(ee.event);
        assertNull(ee.excludedPN);
        assertEquals("abcde", ee.comment);
        assertEquals("#abcde", ee.toString());
        assertEquals(ee, EventEntry.parse("#abcde"));

        ee = new EventEntry(" abcde ");
        assertNull(ee.event);
        assertEquals("# abcde ", ee.toString());
        assertEquals(ee, EventEntry.parse("# abcde "));

        // timestamp formatting and parsing (leading 0s, etc)

        ee = new EventEntry(event, -1, false, 0);
        assertEquals("0:00.000:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        EventEntry pe = EventEntry.parse("0:00.000:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(0, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 1);
        assertEquals("0:00.001:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("0:00.001:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(1, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 31);
        assertEquals("0:00.031:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("0:00.031:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(31, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 770);
        assertEquals("0:00.770:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("0:00.770:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(770, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 1770);
        assertEquals("0:01.770:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("0:01.770:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(1770, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 61770);
        assertEquals("1:01.770:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("1:01.770:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(61770, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 90001);
        assertEquals("1:30.001:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("1:30.001:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(90001, pe.timeElapsedMS);
        assertEquals(ee, pe);

        ee = new EventEntry(event, -1, false, 600001);
        assertEquals("10:00.001:all:SOCBuildRequest:game=testgame|pieceType=2", ee.toString());
        pe = EventEntry.parse("10:00.001:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(600001, pe.timeElapsedMS);
        assertEquals(ee, pe);

        pe = EventEntry.parse("35791:23.646:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(Integer.MAX_VALUE - 1, pe.timeElapsedMS);

        pe = EventEntry.parse("35791:23.647:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(Integer.MAX_VALUE, pe.timeElapsedMS);

        // clip to MAX_VALUE instead of overflow/going negative
        pe = EventEntry.parse("35791:23.648:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertEquals(Integer.MAX_VALUE, pe.timeElapsedMS);

        // exceptions

        try
        {
            ee = new EventEntry(null, 3, true, -1);
            fail("Should throw IllegalArgumentException if isFromClient and event=null");
        } catch (IllegalArgumentException e) {}

        try
        {
            final SOCVersion vmsg = new SOCVersion(1100, "1.1.00", "", null, null);
            assertFalse(vmsg instanceof SOCMessageForGame);
            ee = new EventEntry(vmsg, 3, true, -1);
            fail("Should throw IllegalArgumentException if isFromClient and event not SOCMessageForGame");
        } catch (IllegalArgumentException e) {}

        try
        {
            ee = new EventEntry(null);
            fail("Should throw IllegalArgumentException for comment constructor if comment=null");
        } catch (IllegalArgumentException e) {}
    }

    /**
     * Various negative and positive tests for {@link EventEntry#parse(String)}.
     * More positive tests are included in {@link #testEventEntry()}.
     * @throws ParseException if parsing fails unexpectedly
     */
    @Test
    public void testEventEntryParse()
        throws ParseException
    {
        EventEntry ee;

        assertNull(EventEntry.parse(null));
        assertNull(EventEntry.parse(""));

        ee = EventEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=2");
        assertNotNull(ee);
            // Detailed parsing of that is done in testEventEntry;
            // here we just want to show what successfully parses,
            // to narrow down why these next parses should fail

        // Optional timestamp field:

        ee = EventEntry.parse("7:31.123:all:SOCBuildRequest:game=testgame|pieceType=2");
        assertNotNull(ee);
        assertEquals(7 * 60000 + 31123, ee.timeElapsedMS);

        for (String badTS : new String[]{
            "7", "7XYZ", "7:XYZ",
            "7:31:all:SOCBuildRequest:game=testgame|pieceType=2",
            "7:31.1:all:SOCBuildRequest:game=testgame|pieceType=2",
            "7:31.12:all:SOCBuildRequest:game=testgame|pieceType=2",
            "7:31.-12:all:SOCBuildRequest:game=testgame|pieceType=2",
            "7:-5.123:all:SSOCBuildRequest:game=testgame|pieceType=2"
            } )
            try
            {
                ee = EventEntry.parse(badTS);
                fail("Should throw ParseException for bad timestamp: " + badTS);
            } catch (ParseException e) {}


        // Rest of the fields:

        try
        {
            ee = EventEntry.parse(":SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException when nothing before ':'");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("all:");
            fail("Should throw ParseException when nothing after ':'");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException when missing ':'");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("all:xyz");
            fail("Should throw ParseException when not a SOCMessage after ':'");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=X");
            fail("Should throw ParseException when can't parse expected message fields after ':'");
        } catch (ParseException e) {}

        for(String userSpec : new String[]{ "p", "p_", "f", "f_", "fz", "al", "xy", "_", "!p", "!pz", "!p_, !p]" })
            try
            {
                ee = EventEntry.parse(userSpec + ":SOCBuildRequest:game=testgame|pieceType=2");
                fail("Should throw ParseException for invalid user spec " + userSpec + ':');
            } catch (ParseException e) {}

        // Various problems after !p :

        try
        {
            ee = EventEntry.parse("!p[]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for empty !p[]");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("!p[:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for missing ']'");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("!p[X]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for non-digit in [] list");
        } catch (ParseException e) {}

        try
        {
            ee = EventEntry.parse("!p[5, X]:SOCBuildRequest:game=testgame|pieceType=2");
            fail("Should throw ParseException for non-digit in [] list");
        } catch (ParseException e) {}
    }

    /**
     * Test {@link GameEventLog#GameEventLog(GameEventLog, int)} copy constructor
     * with and without {@code filterAtClientPN}.
     * @see #testLoadWithFilterAtClientPN()
     */
    @Test
    public void testFilteringCopyConstructor()
        throws ParseException
    {
        final String GAME_NAME = "testFiltering";
        final SOCGame game = new SOCGame(GAME_NAME);
        final GameEventLog source = new GameEventLog(game, false);
        assertTrue(game == source.game);
        assertNull(source.gameName);
        source.gameName = GAME_NAME;  // to test copying; an actual log would have either game or gameName, not both
        assertFalse(source.isAtClient);
        assertEquals(-1, source.atClientPN);
        assertEquals(0, source.version);
        source.version = Version.versionNumber();  // to test copying
        assertFalse(source.hasTimestamps);
        assertNull(source.optsStr);
        source.optsStr = "PL=4";  // to test copying
        assertEquals(0, source.numLines);

        // Fill contents:
        source.entries.add(EventEntry.parse("all:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("p1:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("p3:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("!p3:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("!p[2, 3, 4]:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("!p[2, 4]:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("un:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("f3:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("fo:SOCBuildRequest:game=testgame|pieceType=2"));
        source.entries.add(EventEntry.parse("all:null"));
        source.entries.add(EventEntry.parse("p3:null"));
        source.entries.add(EventEntry.parse("#abcde"));
        source.numLines = 1 + source.entries.size();

        // Exact copy constructor without filtering:
        GameEventLog copied = new GameEventLog(source, -1);
        assertTrue(game == copied.game);
        assertEquals(source.gameName, copied.gameName);
        assertEquals(source.isAtClient, copied.isAtClient);
        assertEquals(source.atClientPN, copied.atClientPN);
        assertEquals(source.version, copied.version);
        assertFalse(copied.hasTimestamps);
        assertEquals(source.optsStr, copied.optsStr);
        assertEquals(source.numLines, copied.numLines);
        int S = source.entries.size();
        assertEquals(S, copied.entries.size());
        for (int i = 0; i < S; ++i)
            assertTrue(source.entries.get(i) == copied.entries.get(i));

        // Now call the filter copy constructor, check results:
        final int TO_PN = 3;
        final GameEventLog filtered = new GameEventLog(source, TO_PN);
        assertTrue(game == filtered.game);
        assertEquals(source.gameName, filtered.gameName);
        assertTrue(filtered.isAtClient);
        assertEquals(TO_PN, filtered.atClientPN);
        assertEquals(source.version, filtered.version);
        assertFalse(filtered.hasTimestamps);
        assertEquals(source.optsStr, filtered.optsStr);
        assertEquals(source.numLines, filtered.numLines);
        // check filtered contents of entries
        assertEquals(12, source.entries.size());
        assertEquals(6, filtered.entries.size());
        assertTrue(filtered.entries.get(0) == source.entries.get(0));
        assertTrue(filtered.entries.get(1) == source.entries.get(2));
        assertTrue(filtered.entries.get(2) == source.entries.get(5));
        assertTrue(filtered.entries.get(3) == source.entries.get(9));
        assertTrue(filtered.entries.get(4) == source.entries.get(10));
        assertTrue(filtered.entries.get(5) == source.entries.get(11));

        // Exact copy doesn't reject when already filtered:
        copied = new GameEventLog(filtered, -1);
        assertTrue(game == copied.game);
        assertEquals(filtered.gameName, copied.gameName);
        assertEquals(filtered.isAtClient, copied.isAtClient);
        assertEquals(filtered.atClientPN, copied.atClientPN);
        assertEquals(filtered.version, copied.version);
        assertEquals(filtered.optsStr, copied.optsStr);
        assertEquals(filtered.numLines, copied.numLines);
        S = filtered.entries.size();
        assertEquals(S, copied.entries.size());
        for (int i = 0; i < S; ++i)
            assertTrue(filtered.entries.get(i) == copied.entries.get(i));

        // Test bad parameters:
        try
        {
            assertEquals(TO_PN, filtered.atClientPN);
            GameEventLog log = new GameEventLog(filtered, TO_PN + 1);
            fail("Should throw IllegalArgumentException when already filtered: " + log.atClientPN);
        } catch (IllegalArgumentException e) {}
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, int)}
     * on the known-good {@code all-basic-actions.soclog} artifact.
     * @see #testLoadWithTimestamps()
     * @see #testLoadWithAtClient()
     */
    @Test
    public void testLoadKnownGood()
        throws NoSuchElementException, IOException, ParseException
    {
        final GameEventLog log = load("all-basic-actions.soclog", false, -1);
        final int EXPECTED_FILE_LINE_COUNT = 773;  // length from wc -l

        assertNotNull(log);
        assertEquals("test", log.gameName);
        assertFalse(log.isAtClient);
        assertEquals(-1, log.atClientPN);
        assertEquals(2500, log.version);
        assertEquals("BC=t4,N7=f7,RD=f,SBL=t,PL=4", log.optsStr);
        assertFalse(log.entries.isEmpty());
        assertEquals(EXPECTED_FILE_LINE_COUNT, log.numLines);
        assertEquals(log.numLines, 1 + log.entries.size());  // true if no blank lines

        // comment-line parsing
        EventEntry entry = log.entries.get(0);
        assertEquals(" Game created at: 2021-10-10 22:48:46 -0400", entry.comment);
        assertNull(entry.event);
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertNull(entry.excludedPN);

        // spot-check a few parsed messages:

        // ob:SOCDiceResult:game=test|param=-1
        entry = log.entries.get(19);
        SOCMessage msg = entry.event;
        assertFalse(entry.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue("Line 21 expected SOCDiceResult, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCDiceResult);
        assertEquals("test", ((SOCDiceResult) msg).getGame());
        assertEquals(-1, ((SOCDiceResult) msg).getResult());

        // !p[3, 2]:SOCRobberyResult:game=test|perp=3|victim=2|resType=6|amount=1|isGainLose=true
        entry = log.entries.get(307);
        msg = entry.event;
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertArrayEquals(new int[]{3, 2}, entry.excludedPN);
        assertTrue("Line 309 expected SOCRobberyResult, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCRobberyResult);
        assertEquals(3, ((SOCRobberyResult) msg).perpPN);
        assertEquals(2, ((SOCRobberyResult) msg).victimPN);

        // all:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=603
        entry = log.entries.get(EXPECTED_FILE_LINE_COUNT - 16 - 2);
        msg = entry.event;
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue
            ("Line " + (EXPECTED_FILE_LINE_COUNT - 16) + " expected SOCPutPiece, got " +
                 ((msg != null) ? msg.getClass().getSimpleName() : "null"),
             msg instanceof SOCPutPiece);
        assertEquals("test", ((SOCPutPiece) msg).getGame());
        assertEquals(2, ((SOCPutPiece) msg).getPieceType());
    }

    /** Artifact {@code has-timestamps.soclog}'s length from {@code wc -l} */
    private static final int HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT = 223;

    /**
     * Test {@link GameEventLog#load(File, boolean, int)}
     * on the known-good {@code has-timestamps.soclog} artifact.
     * @see #testLoadKnownGood()
     * @see #testLoadWithAtClient()
     * @see #testLoadWithFilterAtClientPN()
     */
    @Test
    public void testLoadWithTimestamps()
        throws NoSuchElementException, IOException, ParseException
    {
        final GameEventLog log = load("has-timestamps.soclog", false, -1);

        assertNotNull(log);
        assertEquals("g", log.gameName);
        assertFalse(log.isAtClient);
        assertEquals(-1, log.atClientPN);
        assertEquals(2500, log.version);
        assertEquals("BC=t4,N7=f7,RD=f,PL=4", log.optsStr);
        assertFalse(log.entries.isEmpty());
        assertEquals(HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT, log.numLines);
        assertEquals(log.numLines, 1 + log.entries.size());  // true if no blank lines

        // comment-line parsing
        assertEquals(" Game created at: 2021-11-12 08:47:58 -0500", log.entries.get(0).comment);

        // Spot-check timestamps in a few parsed messages:

        // 0:05.211:f3:SOCPutPiece:game=g|playerNumber=3|pieceType=1|coord=43
        EventEntry entry = log.entries.get(100);
        SOCMessage msg = entry.event;
        assertEquals(5211, entry.timeElapsedMS);
        assertTrue(entry.isFromClient);
        assertEquals(3, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue
            ("Line 102 expected SOCPutPiece, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
             msg instanceof SOCPutPiece);
        assertEquals("g", ((SOCPutPiece) msg).getGame());
        assertEquals(1, ((SOCPutPiece) msg).getPieceType());
        assertEquals(0x43, ((SOCPutPiece) msg).getCoordinates());

        // 0:31.165:all:SOCDiceResult:game=g|param=5
        entry = log.entries.get(170);
        msg = entry.event;
        assertEquals(31165, entry.timeElapsedMS);
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue("Line 172 expected SOCDiceResult, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCDiceResult);
        assertEquals("g", ((SOCDiceResult) msg).getGame());
        assertEquals(5, ((SOCDiceResult) msg).getResult());

        // 1:04.429:fo:SOCGameTextMsg:game=g|nickname=-|text=entry with timestamp >= 1 minute
        entry = log.entries.get(HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT - 5 - 2);
        msg = entry.event;
        assertEquals(64429, entry.timeElapsedMS);
        assertTrue(entry.isFromClient);
        assertEquals(SOCServer.PN_OBSERVER, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue("Line " + (HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT - 5)
            + " expected SOCGameTextMsg, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCGameTextMsg);
        assertEquals("g", ((SOCGameTextMsg) msg).getGame());
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, int)} with {@code filterAtClientPN}
     * on the known-good {@code has-timestamps.soclog} artifact, against separately-tested
     * {@link GameEventLog#GameEventLog(soc.game.SOCGame, boolean)} filtering copy constructor.
     * @see #testFilteringCopyConstructor()
     * @see #testLoadWithTimestamps()
     * @see #testLoadWithAtClient()
     */
    @Test
    public void testLoadWithFilterAtClientPN()
        throws NoSuchElementException, IOException, ParseException
    {
        final int TO_PN = 3,
            EXPECTED_ORIG_NUM_ENTRIES = HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT - 1;  // true if no blank lines

        // First, load the whole log, filter it with independently tested GameEventLog(log, TO_PN)
        GameEventLog log = load("has-timestamps.soclog", false, -1);
        assertNotNull(log);
        assertEquals("g", log.gameName);
        assertFalse(log.isAtClient);
        assertEquals(-1, log.atClientPN);
        assertEquals(2500, log.version);
        assertFalse(log.entries.isEmpty());
        assertEquals(HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT, log.numLines);
        assertEquals(EXPECTED_ORIG_NUM_ENTRIES, log.entries.size());

        // Filter: none from clients, none sent only to other players/observer
        final int EXPECTED_FROM_CLIENTS = 34,  // from grep -c '[0-9]:f[o0-5]:' has-timestamps.soclog
            EXPECTED_FROM_OBSERVER = 54,    // grep -c '[0-9]:ob:'
            EXPECTED_TO_OTHER_PLAYERS = 7,  // grep -c '[0-9]:p[0-2]:'
            EXPECTED_EXCLUDE_PLAYERS = 0;   // grep -c '[0-9]:!p'
        final GameEventLog filteredHereLog = new GameEventLog(log, TO_PN);
        assertEquals(EXPECTED_ORIG_NUM_ENTRIES -
            (EXPECTED_FROM_CLIENTS + EXPECTED_FROM_OBSERVER + EXPECTED_TO_OTHER_PLAYERS + EXPECTED_EXCLUDE_PLAYERS),
            filteredHereLog.entries.size());

        // Now reload with filtering
        log = load("has-timestamps.soclog", false, TO_PN);
        assertNotNull(log);
        assertEquals("g", log.gameName);
        assertTrue(log.isAtClient);
        assertEquals(TO_PN, log.atClientPN);
        assertEquals(2500, log.version);
        assertFalse(log.entries.isEmpty());
        assertEquals(HAS_TIMESTAMPS_EXPECTED_FILE_LINE_COUNT, log.numLines);
        final int S = filteredHereLog.entries.size();
        assertEquals(S, log.entries.size());
        synchronized(log.entries)  // avoid some locking overhead
        {
            for (int i = 0; i < S; ++i)
                assertEquals(filteredHereLog.entries.get(i), log.entries.get(i));
        }
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, int)}
     * on the known-good {@code is-at-client.soclog} artifact.
     * @see #testLoadKnownGood()
     * @see #testLoadWithTimestamps()
     * @see #testLoadWithFilterAtClientPN()
     * @see soctest.robot.TestGameActionExtractor#testLoadAndExtractInitialPlacement()
     */
    @Test
    public void testLoadWithAtClient()
        throws NoSuchElementException, IOException, ParseException
    {
        final GameEventLog log = load("is-at-client.soclog", false, -1);
        final int EXPECTED_FILE_LINE_COUNT = 198;  // length from wc -l

        assertNotNull(log);
        assertEquals("test", log.gameName);
        assertTrue(log.isAtClient);
        assertEquals(-1, log.atClientPN);
        assertEquals(2500, log.version);
        assertEquals("BC=t4,N7=f7,RD=f,PL=4", log.optsStr);
        assertFalse(log.entries.isEmpty());
        assertEquals(EXPECTED_FILE_LINE_COUNT, log.numLines);
        assertEquals(log.numLines, 1 + log.entries.size());  // true if no blank lines

        // Spot-check a few parsed messages:

        // 0:48.650:all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=a6
        EventEntry entry = log.entries.get(102);
        SOCMessage msg = entry.event;
        assertEquals(48650, entry.timeElapsedMS);
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertNull(entry.excludedPN);
        assertTrue
            ("Line 104 expected SOCPutPiece, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
             msg instanceof SOCPutPiece);
        assertEquals("test", ((SOCPutPiece) msg).getGame());
        assertEquals(0, ((SOCPutPiece) msg).getPieceType());
        assertEquals(0xa6, ((SOCPutPiece) msg).getCoordinates());

        // 2:04.365:!p[2, 1]:SOCRobberyResult:game=test|perp=2|victim=1|resType=6|amount=1|isGainLose=true
        entry = log.entries.get(182);
        msg = entry.event;
        assertEquals(124365, entry.timeElapsedMS);
        assertFalse(entry.isFromClient);
        assertEquals(-1, entry.pn);
        assertArrayEquals(new int[]{2, 1}, entry.excludedPN);
        assertTrue("Line 184 expected SOCRobberyResult, got " + ((msg != null) ? msg.getClass().getSimpleName() : "null"),
            msg instanceof SOCRobberyResult);
        assertEquals(2, ((SOCRobberyResult) msg).perpPN);
        assertEquals(1, ((SOCRobberyResult) msg).victimPN);
    }

    /**
     * Test {@link GameEventLog#load(File, boolean, int)}
     * on soclog artifacts missing various required items:
     *<UL>
     * <LI> {@code test-missing-header.soclog}: missing required header line
     * <LI> {@code test-header-version-missing.soclog}: header is missing version=
     * <LI> {@code test-header-version-numparse.soclog}: header's version= isn't a parseable number
     * <LI> {@code test-header-gamename-missing.soclog}: header is missing game_name=
     * <LI> {@code test-header-gamename-empty.soclog}: header's game_name= has no value
     * <LI> {@code test-header-gamename-invalid.soclog}: header's game_name= fails
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
            load("test-missing-header.soclog", false, -1);
            fail("test-missing-header.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("header line"));
        }

        try
        {
            load("test-header-type-missing.soclog", false, -1);
            fail("test-header-type-missing.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("must start with \"SOC game event log\" header"));
        }

        try
        {
            load("test-header-type-invalid.soclog", false, -1);
            fail("test-header-type-invalid.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("unknown log type"));
        }

        try
        {
            load("test-header-type-length.soclog", false, -1);
            fail("test-header-type-length.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("unknown log type, must be 1 character"));
        }

        try
        {
            load("test-header-version-missing.soclog", false, -1);
            fail("test-header-version-missing.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("Header missing required version"));
        }

        try
        {
            load("test-header-version-numparse.soclog", false, -1);
            fail("test-header-version-numparse.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().equals("Couldn't parse version number in header"));
        }

        try
        {
            load("test-header-gamename-missing.soclog", false, -1);
            fail("test-header-gamename-missing.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("missing required game_name"));
        }

        try
        {
            load("test-header-gamename-empty.soclog", false, -1);
            fail("test-header-gamename-empty.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Empty game_name"));
        }

        try
        {
            load("test-header-gamename-invalid.soclog", false, -1);
            fail("test-header-gamename-invalid.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Invalid game_name"));
        }

        try
        {
            load("test-version-too-old.soclog", false, -1);
            fail("test-version-too-old.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().startsWith("Minimum version for format is "));
        }

        try
        {
            load("test-missing-socversion.soclog", false, -1);
            fail("test-missing-socversion.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().equals("First event message must be SOCVersion"));
        }

        try
        {
            load("test-missing-socnewgame.soclog", false, -1);
            fail("test-missing-socnewgame.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().equals("Second event message must be SOCNewGame or SOCNewGameWithOptions"));
        }

        try
        {
            load("test-version-mismatch.soclog", false, -1);
            fail("test-version-mismatch.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("in SOCVersion differs from header"));
        }

        try
        {
            load("test-gamename-mismatch-newgame.soclog", false, -1);
            fail("test-gamename-mismatch-newgame.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Game name differs from header"));
        }

        try
        {
            load("test-gamename-mismatch-newgamewithopts.soclog", false, -1);
            fail("test-gamename-mismatch-newgamewithopts.soclog: Expected ParseException");
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("Game name differs from header"));
        }

    }

    /**
     * Tests {@link GameEventLog#load(File, boolean, int)} behavior
     * when given a logfile in STAC v1 format {@code test-stac-legacy-human-league1-sample.soclog}:
     * Should refuse to parse, but throw {@link NoSuchElementException} instead of generic {@link ParseException}.
     */
    @Test
    public void testLoadStacV1()
        throws IOException, ParseException
    {
        try
        {
            load("test-stac-legacy-human-league1-sample.soclog", false, -1);
            fail("test-stac-legacy-human-league1-sample.soclog: Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(e.getMessage().contains("must start with \"SOC game event log\" header"));
        }
    }

}
