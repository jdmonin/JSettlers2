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

package soctest.robot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.robot.SOCBuildingSpeedEstimateFactory;
import soc.robot.SOCPossibleCard;
import soc.robot.SOCPossibleCity;
import soc.robot.SOCPossiblePickSpecialItem;
import soc.robot.SOCPossiblePiece;
import soc.robot.SOCPossibleRoad;
import soc.robot.SOCPossibleSettlement;
import soc.robot.SOCPossibleShip;

import org.junit.Assert;  // for javadocs
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCPossiblePiece} and its subclasses
 * like {@link SOCPossibleSettlement}, {@link SOCPossibleCard}.
 * @since 2.4.50
 */
public class TestPossiblePiece
{
    /**
     * For use with {@link #testSerializeToFile()}:
     * This folder and all contents are created at start of each test method, deleted at end of it
     */
    @Rule
    public TemporaryFolder testTmpFolder = new TemporaryFolder();
        // TODO later: if more unit tests added, making/removing an unused temp folder for each test will be inefficient

    final SOCGame ga = new SOCGame("game-TestPossiblePiece");
    final SOCPlayer pl2 = new SOCPlayer(2, ga), plOther = new SOCPlayer(3, ga);
    final SOCBuildingSpeedEstimateFactory bsef = new SOCBuildingSpeedEstimateFactory(null);

    /**
     * Test serialization and deserialization of all PossiblePiece types.
     */
    @Test
    public void testSerializeToFile()
        throws ClassNotFoundException, IOException
    {
        final SOCPossibleRoad nr1 = new SOCPossibleRoad(pl2, 0x14, null);
        final SOCPossibleRoad nr2 = new SOCPossibleRoad(pl2, 0x13, new ArrayList<>(Arrays.asList(nr1)));

        SOCPossibleRoad pr = new SOCPossibleRoad(pl2, 0x12, new ArrayList<>(Arrays.asList(nr1, nr2)));
        pr.addNewPossibility(new SOCPossibleRoad(pl2, 0x11, new ArrayList<>(Arrays.asList(pr, nr1, nr2))));
        writeReadCheckPiece(pr);

        SOCPossibleSettlement ps = new SOCPossibleSettlement
            (pl2, 0x13, new ArrayList<>(Arrays.asList(nr1, nr2)), bsef);
        ps.addConflict(new SOCPossibleSettlement(plOther, 0x15, null, bsef));
        writeReadCheckPiece(ps);

        writeReadCheckPiece
            (new SOCPossibleCity(pl2, 0x33, bsef));

        writeReadCheckPiece
            (new SOCPossibleShip(pl2, 0x44, true, new ArrayList<>(Arrays.asList(nr1, nr2))));

        writeReadCheckPiece
            (new SOCPossibleCard(pl2, 7));

        writeReadCheckPiece
            (new SOCPossiblePickSpecialItem(pl2, "TestSIType", 11, 22, 33, new SOCResourceSet(1, 0, 2, 1, 3, 0)));
    }

    /**
     * For {@link #testSerializeToFile()}, set some common fields,
     * write and read {@code p} from a new file, then compare non-transient field contents.
     * Will fail asserts if fields mismatch (calls
     * {@link #comparePieceFields(SOCPossiblePiece, SOCPossiblePiece, String[], boolean)})
     * or otherwise fail if {@link #writeAndReadPiece(SOCPossiblePiece)} has a problem
     * with serialization or writing/reading.
     * @param p  Piece to write to file
     */
    private void writeReadCheckPiece(final SOCPossiblePiece p)
        throws IOException, ClassNotFoundException
    {
        p.setETA(3);
        p.addToScore(3.3f);
        SOCPossibleRoad otherR1 = new SOCPossibleRoad(plOther, 0x42, null),
            otherR2 = new SOCPossibleRoad(plOther, 0x44, null);
        p.addThreat(otherR1);
        p.addThreat(otherR2);
        p.addBiggestThreat(otherR2);

        final SOCPossiblePiece readP = writeAndReadPiece(p);
        assertNotNull(readP);
        comparePieceFields(p, readP, false);
    }

    /**
     * Compare all common non-transient fields of these pieces, and optionally
     * specified other fields which may be specific to a given piece type.
     * Will fail asserts if comparisons fail.
     *
     * @param expected Piece with expected field values, to compare to {@code expected}; not null.
     * @param actual   Piece with actual field values, to compare to {@code actual}; not null.
     *     The first field checked is {@code pieceType}; from that point on, assumes they are both the same subclass.
     * @param skipThreatLists  If true, won't recursively check each field of each piece within
     *     the {@code biggestThreats} and {@code threats} fields; will only compare if they are null or not, and length.
     */
    private void comparePieceFields
        (final SOCPossiblePiece expected, final SOCPossiblePiece actual, final boolean skipThreatLists)
    {
        final String prefix = "comparePieceFields(" + expected.getClass().getSimpleName() + "): ";

        assertEquals(prefix + "pieceType", expected.getType(), actual.getType());
        assertEquals(prefix + "coord",     expected.getCoordinates(), actual.getCoordinates());
        assertEquals(prefix + "eta",       expected.getETA(), actual.getETA());
        assertEquals(prefix + "update",    expected.isETAUpdated(), actual.isETAUpdated());
        assertEquals(prefix + "score",     expected.getScore(), actual.getScore(), 0.00001);
        assertEquals(prefix + "threatUpdatedFlag", expected.isThreatUpdated(), actual.isThreatUpdated());
        assertEquals(prefix + "hasBeenExpanded", expected.hasBeenExpanded(), actual.hasBeenExpanded());

        // biggestThreats:
        List<SOCPossiblePiece> exList = expected.getBiggestThreats(),
            acList = actual.getBiggestThreats();
        if (exList == null)
        {
            assertNull(prefix + "biggestThreats", acList);
        } else {
            assertNotNull(prefix + "biggestThreats", acList);
            assertEquals(prefix + "biggestThreats.size", exList.size(), acList.size());

            if (! skipThreatLists)
                for (int i = exList.size() - 1; i >= 0; --i)
                    comparePieceFields(exList.get(i), acList.get(i), true);
        }

        // threats:
        exList = expected.getThreats();
        acList = actual.getThreats();
        if (exList == null)
        {
            assertNull(prefix + "threats", acList);
        } else {
            assertNotNull(prefix + "threats", acList);
            assertEquals(prefix + "threats.size", exList.size(), acList.size());

            if (! skipThreatLists)
                for (int i = exList.size() - 1; i >= 0; --i)
                    comparePieceFields(exList.get(i), acList.get(i), true);
        }
    }

    /**
     * For {@link #testSerializeToFile()}, create a file, write serialized {@code p} to it,
     * close and reopen, try to read and deserialize a piece back, return that piece.
     * Also calls {@link Assert#fail(String)} if somehow a non-{@link SOCPossiblePiece} is read back.
     * @param p  Piece to write to file
     * @return  Piece read from file, if no problems occur
     */
    private SOCPossiblePiece writeAndReadPiece(final SOCPossiblePiece p)
        throws IOException, ClassNotFoundException
    {
        assertNotNull(p);

        File tmpf = testTmpFolder.newFile();

        try
            (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(tmpf)))
        {
            oos.writeObject(p);
        }

        try
            (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(tmpf)))
        {
            Object readO = ois.readObject();
            assertNotNull(readO);
            if (readO instanceof SOCPossiblePiece)
            {
                return (SOCPossiblePiece) readO;
            } else {
                fail
                    ("writeAndReadPiece(" + p.getClass().getSimpleName()
                     + "): expected to read SOCPossiblePiece but got "
                     + readO.getClass().getName());

                return null;  // won't reach this code
            }
        }
    }

}
