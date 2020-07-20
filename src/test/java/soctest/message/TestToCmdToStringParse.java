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

package soctest.message;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.InputMismatchException;
import java.util.List;

import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.SOCGame.SeatLockState;
import soc.message.SOCAcceptOffer;
import soc.message.SOCBankTrade;
import soc.message.SOCBoardLayout;
import soc.message.SOCBotJoinGameRequest;
import soc.message.SOCBuyDevCardRequest;
import soc.message.SOCChangeFace;
import soc.message.SOCChoosePlayer;
import soc.message.SOCClearOffer;
import soc.message.SOCClearTradeMsg;
import soc.message.SOCDeleteGame;
import soc.message.SOCDevCardAction;
import soc.message.SOCDiceResult;
import soc.message.SOCDiscardRequest;
import soc.message.SOCFirstPlayer;
import soc.message.SOCGameElements;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCGameServerText;
import soc.message.SOCGameState;
import soc.message.SOCGameStats;
import soc.message.SOCGameTextMsg;
import soc.message.SOCJoinGame;
import soc.message.SOCJoinGameAuth;
import soc.message.SOCLargestArmy;
import soc.message.SOCLastSettlement;
import soc.message.SOCLeaveAll;
import soc.message.SOCLeaveGame;
import soc.message.SOCLongestRoad;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCMovePiece;
import soc.message.SOCMoveRobber;
import soc.message.SOCNewGame;
import soc.message.SOCNewGameWithOptions;
import soc.message.SOCPlayDevCardRequest;
import soc.message.SOCPlayerElement;
import soc.message.SOCStartGame;
import soc.message.SOCPlayerElement.PEType;
import soc.message.SOCPlayerElements;
import soc.message.SOCPutPiece;
import soc.message.SOCRejectOffer;
import soc.message.SOCRobotDismiss;
import soc.message.SOCRollDice;
import soc.message.SOCRollDicePrompt;
import soc.message.SOCSVPTextMessage;
import soc.message.SOCSetPlayedDevCard;
import soc.message.SOCSetSeatLock;
import soc.message.SOCSetTurn;
import soc.message.SOCSimpleAction;
import soc.message.SOCSitDown;
import soc.message.SOCTurn;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * A few tests for {@link SOCMessage} formats and parsing:
 *<UL>
 * <LI> {@link SOCMessage#toCmd()} {@code <->} {@link SOCMessage#toMsg(String)
 *      / {@code SOCMessageType.parseDataStr(String)}
 * <LI> {@link SOCMessage#toString()} {@code <->} {@link SOCMessage#parseMsgStr(String)}
 * <LI> {@link SOCMessage#stripAttribNames(String)}
 * <LI> {@link SOCMessage#stripAttribsToList(String)}
 *</UL>
 * @since 2.4.10
 */
public class TestToCmdToStringParse
{
    /** Round-trip parsing tests on messages listed in {@link #TOCMD_TOSTRING_COMPARES}. */
    @Test
    public void testRoundTripParsing()
    {
        StringBuilder results = new StringBuilder();

        StringBuilder res = new StringBuilder();
        for(Object[] compareCase : TOCMD_TOSTRING_COMPARES)
        {
            final SOCMessage msg = (SOCMessage) compareCase[0];
            final Class<? extends SOCMessage> msgClass = msg.getClass();
            final String expectedToCmd = (String) compareCase[1], expectedToString = (String) compareCase[2];

            String s = msg.toCmd();
            if (! s.equals(expectedToCmd))
            {
                res.append(" toCmd: expected \"" + expectedToCmd + "\", got \"" + s + "\"");
            } else {
                // round-trip compare msg -> toCmd() -> toMsg(cmd)
                SOCMessage rev = SOCMessage.toMsg(s);
                if (rev == null)
                {
                    res.append(" toMsg(cmd): got null");
                } else if (! msgClass.isInstance(rev)) {
                    res.append(" toMsg(cmd): got wrong class " + rev.getClass().getSimpleName());
                } else {
                    compareMsgObjFields(msgClass, msg, rev, res);
                }
            }

            s = msg.toString();
            if (! s.equals(expectedToString))
            {
                res.append(" toString: expected \"" + expectedToString + "\", got \"" + s + "\"");
            } else {
                // round-trip compare msg -> toString() -> parseMsgStr(str)
                try
                {
                    SOCMessage rev = SOCMessage.parseMsgStr(s);
                    if (rev == null)
                    {
                        res.append(" parseMsgStr(s): got null");
                    } else if (! msgClass.isInstance(rev)) {
                        res.append(" parseMsgStr(s): got wrong class " + rev.getClass().getSimpleName());
                    } else {
                        compareMsgObjFields(msgClass, msg, rev, res);
                    }
                } catch (InputMismatchException e) {
                    res.append(" parseMsgStr(s) rejected: " + e.getMessage());
                } catch (ParseException e) {
                    res.append(" parseMsgStr(s) failed: " + e);
                }
            }

            if (res.length() > 0)
            {
                results.append(msgClass.getSimpleName()).append(':').append(res).append('\n');
                res.setLength(0);
            }
        }

        if (results.length() > 0)
        {
            System.err.println("testRoundTripParsing: " + results);
            fail(results.toString());
        }
    }

    /**
     * Compares all instance field values using reflection.
     * @param msgClass  SOCMessage class being compared, for convenience and consistency
     * @param mExpected  Constructed message instance with expected field values, to compare against {@code mActual}
     * @param mActual  Message from parsed string, to compare against {@code mExpected}
     * @param res  String where comparison results are reported;
     *     will add text to this only if field compare(s) fail.
     */
    private void compareMsgObjFields
        (final Class<? extends SOCMessage> msgClass,
         final SOCMessage mExpected, final SOCMessage mActual, final StringBuilder res)
    {
        final String className = msgClass.getSimpleName();
        assertTrue("mExpected instanceof " + className, msgClass.isInstance(mExpected));
        assertTrue("mActual instanceof " + className, msgClass.isInstance(mActual));

        for (Field f : msgClass.getDeclaredFields())
        {
            if (Modifier.isStatic(f.getModifiers()))
                continue;

            final String fName = f.getName();
            try
            {

                f.setAccessible(true);
                Object valueExpected = f.get(mExpected),
                    valueActual = f.get(mActual);
                if (valueExpected == null)
                {
                    if (valueActual != null)
                    {
                        res.append(" field " + fName + ": expected null, got " + str(valueActual));
                    }
                } else {
                    if ((valueExpected instanceof int[]) && (valueActual instanceof int[]))
                    {
                        if (! Arrays.equals((int[]) valueExpected, (int[]) valueActual))
                            res.append
                                (" field " + fName + ": expected " + str(valueExpected) + ", got " + str(valueActual));
                    }
                    else if ((valueExpected instanceof boolean[]) && (valueActual instanceof boolean[]))
                    {
                        if (! Arrays.equals((boolean[]) valueExpected, (boolean[]) valueActual))
                            res.append
                                (" field " + fName + ": expected " + str(valueExpected) + ", got " + str(valueActual));
                    }
                    else if ((valueExpected instanceof Object[]) && (valueActual instanceof Object[]))
                    {
                        if (! Arrays.equals((Object[]) valueExpected, (Object[]) valueActual))
                            res.append
                                (" field " + fName + ": expected " + str(valueExpected) + ", got " + str(valueActual));
                    }
                    else if (! valueExpected.equals(valueActual))
                    {
                        res.append(" field " + fName + ": expected " + str(valueExpected) + ", got " + str(valueActual));
                    }
                }
            } catch (Exception e) {
                res.append(" error reading field " + fName+ ": " + e);
            }
        }
    }

    /**
     * Render a value into a more readable form that hints at its type.
     *<UL>
     * <LI> string -> "string"
     * <LI> int[] -> [-2, 1, 3, 0]
     * <LI> boolean[] -> [false, true, true, true]
     * <LI> Object[] -> [each elem.toString]
     * <LI> null -> null
     *<UL>
     * @param val value to render, or {@code null}
     * @return value as string
     */
    private static final String str(final Object val)
    {
        final String ret;

        if (val == null)
            ret = "null";
        else if (val instanceof String)
            ret = '"' + (String) val + '"';
        else if (val instanceof int[])
            ret = Arrays.toString((int[]) val);
        else if (val instanceof boolean[])
            ret = Arrays.toString((boolean[]) val);
        else if (val instanceof Object[])
            ret = Arrays.toString((Object[]) val);
        else
            ret = val.toString();

        return ret;
    }

    /**
     * Message parsing round-trip test cases for {@link #testRoundTripParsing()}.
     */
    private static final Object[][] TOCMD_TOSTRING_COMPARES =
    {
        {new SOCAcceptOffer("ga", 2, 3), "1039|ga,2,3", "SOCAcceptOffer:game=ga|accepting=2|offering=3"},
        {
            new SOCBankTrade("ga", new SOCResourceSet(0, 0, 2, 0, 0, 0), new SOCResourceSet(1, 0, 0, 0, 0, 0), 3),
            "1040|ga,0,0,2,0,0,1,0,0,0,0,3",
            "SOCBankTrade:game=ga|give=clay=0|ore=0|sheep=2|wheat=0|wood=0|unknown=0|get=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|pn=3"
        },
        {new SOCBotJoinGameRequest("ga", 3, null), "1023|ga,3,-", "SOCBotJoinGameRequest:game=ga|playerNumber=3|opts=null"},
        {new SOCBotJoinGameRequest("ga", 3, SOCGameOption.parseOptionsToMap("PL=6")), "1023|ga,3,PL=6", "SOCBotJoinGameRequest:game=ga|playerNumber=3|opts=PL=6"},
        {new SOCBuyDevCardRequest("ga"), "1045|ga", "SOCBuyDevCardRequest:game=ga"},  // TODO SOCBuyCardRequest too (v1.x)?
        {
            new SOCBoardLayout
                ("ga",
                 new int[]{50, 6, 65, 6, 6, 5, 3, 4, 10, 8, 2, 3, 1, 0, 6, 6, 1, 1, 4, 3, 4, 11, 8, 2, 5, 5, 2, 6, 6, 5, 3, 4, 100, 19, 6, 101, 6},
                 new int[]{-1, -1, -1, -1, -1, 1, 4, 0, -1, -1, 5, 2, 6, -1, -1, -1, 7, 3, 8, 7, 3, -1, -1, 6, 4, 1, 5, -1, -1, 9, 8, 2, -1, -1, -1, -1, -1 },
                 0x9b, true),
            "1014|ga,50,6,65,6,6,5,3,4,10,8,2,3,1,0,6,6,1,1,4,3,4,11,8,2,5,5,2,6,6,5,3,4,100,19,6,101,6,-1,-1,-1,-1,-1,1,4,0,-1,-1,5,2,6,-1,-1,-1,7,3,8,7,3,-1,-1,6,4,1,5,-1,-1,9,8,2,-1,-1,-1,-1,-1,155",
            "SOCBoardLayout:game=ga|hexLayout={ 50 6 65 6 6 5 3 4 10 8 2 3 1 0 6 6 1 1 4 3 4 11 8 2 5 5 2 6 6 5 3 4 100 19 6 101 6 }|numberLayout={ -1 -1 -1 -1 -1 1 4 0 -1 -1 5 2 6 -1 -1 -1 7 3 8 7 3 -1 -1 6 4 1 5 -1 -1 9 8 2 -1 -1 -1 -1 -1 }|robberHex=0x9b"
        },
        {new SOCChangeFace("ga", 3, 7), "1058|ga,3,7", "SOCChangeFace:game=ga|playerNumber=3|faceId=7"},
        {new SOCChoosePlayer("ga", 2), "1035|ga,2", "SOCChoosePlayer:game=ga|choice=2"},
        {new SOCClearOffer("ga", 2), "1038|ga,2", "SOCClearOffer:game=ga|playerNumber=2"},
        {new SOCClearTradeMsg("ga", -1), "1042|ga,-1", "SOCClearTradeMsg:game=ga|playerNumber=-1"},
        {new SOCDeleteGame("ga"), "1015|ga", "SOCDeleteGame:game=ga"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_OLD, 6), "1046|ga,3,3,6", "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_OLD|cardType=6"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_NEW, 9), "1046|ga,3,2,9", "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_NEW|cardType=9"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.DRAW, 5), "1046|ga,3,0,5", "SOCDevCardAction:game=ga|playerNum=3|actionType=DRAW|cardType=5"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.PLAY, 9), "1046|ga,3,1,9", "SOCDevCardAction:game=ga|playerNum=3|actionType=PLAY|cardType=9"},
        // TODO SOCDevCard too (v1.x)?
        {
            // v2.x end-of-game form
            new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_OLD, Arrays.asList(new Integer[]{5, 4})),
            "1046|ga,3,3,5,4",
            "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_OLD|cardTypes=[5, 4]"
        },
        {new SOCDiceResult("ga", 9), "1028|ga,9", "SOCDiceResult:game=ga|param=9"},
        /*
SOCDiceResultResources:game=message-seqs|p=2|p=2|p=5|p=1|p=3|p=0|p=3|p=7|p=1|p=4
         */
        {new SOCDiscardRequest("ga", 4), "1029|ga,4", "SOCDiscardRequest:game=ga|numDiscards=4"},
        {new SOCFirstPlayer("ga", 2), "1054|ga,2", "SOCFirstPlayer:game=ga|playerNumber=2"},
        {new SOCGameElements("ga", GEType.CURRENT_PLAYER, 1), "1096|ga|4|1", "SOCGameElements:game=ga|e4=1"},
        {
            new SOCGameElements
                ("ga", new GEType[]{GEType.DEV_CARD_COUNT, GEType.ROUND_COUNT, GEType.FIRST_PLAYER, GEType.LONGEST_ROAD_PLAYER, GEType.LARGEST_ARMY_PLAYER},
                 new int[]{25, 2, 1, -1, -1}),
            "1096|ga|2|25|1|2|3|1|6|-1|5|-1",
            "SOCGameElements:game=ga|e2=25,e1=2,e3=1,e6=-1,e5=-1"
        },
        /*
SOCGameMembers:game=ga|members=[testLoadgame]
SOCGameMembers:game=ga|members=[testTradeDecline_p3, droid 1, robot 2, testTradeDecline_p2]
         */
        {
            new SOCGameServerText("ga", "You stole a wheat from robot 2."),
            "1091|ga" + SOCGameServerText.UNLIKELY_CHAR1 + "You stole a wheat from robot 2.",
            "SOCGameServerText:game=ga|text=You stole a wheat from robot 2."
        },
        {new SOCGameState("ga", 20), "1025|ga,20", "SOCGameState:game=ga|state=20"},
        {new SOCGameStats("ga", new int[]{10,  4, 3, 2}, new boolean[]{false, true, true, true}), "1061|ga,10,4,3,2,false,true,true,true", "SOCGameStats:game=ga|10|4|3|2|false|true|true|true"},
        {new SOCGameTextMsg("ga", SOCGameTextMsg.SERVERNAME, "testp3 built a road."), "1010|ga Server testp3 built a road.", "SOCGameTextMsg:game=ga|nickname=Server|text=testp3 built a road."},
        {new SOCJoinGame("testp2", "", SOCMessage.EMPTYSTR, "ga"), "1013|testp2,\t,\t,ga", "SOCJoinGame:nickname=testp2|password empty|host=\t|game=ga"},
        {new SOCJoinGameAuth("ga"), "1021|ga", "SOCJoinGameAuth:game=ga"},
        {new SOCJoinGameAuth("ga", 20, 21, new int[]{-2, 1, 3, 0}), "1021|ga,20,21,S,-2,1,3,0", "SOCJoinGameAuth:game=ga|bh=20|bw=21|vs=[-2, 1, 3, 0]"},
        {new SOCLargestArmy("ga", 2), "1067|ga,2", "SOCLargestArmy:game=ga|playerNumber=2"},
        {new SOCLastSettlement("ga", 2, 0x405), "1060|ga,2,1029", "SOCLastSettlement:game=ga|playerNumber=2|coord=405"},
        {new SOCLeaveAll(), "1008", "SOCLeaveAll:"},
        {new SOCLeaveGame("testp2", "-", "ga"), "1011|testp2,-,ga", "SOCLeaveGame:nickname=testp2|host=-|game=ga"},
        {new SOCLongestRoad("ga", 2), "1066|ga,2", "SOCLongestRoad:game=ga|playerNumber=2"},
        {
            new SOCMakeOffer("ga", new SOCTradeOffer
                ("ga", 3, new boolean[]{false,  false,  true, false},
                 new SOCResourceSet(0, 1, 0, 1, 0, 0),
                 new SOCResourceSet(0, 0, 1, 0, 0, 0))),
            "1041|ga,3,false,false,true,false,0,1,0,1,0,0,0,1,0,0",
            "SOCMakeOffer:game=ga|offer=game=ga|from=3|to=false,false,true,false|give=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0"
        },
        {
            new SOCMovePiece("ga", 1, SOCPlayingPiece.SHIP, 3078, 3846),
            "1093|ga,1,3,3078,3846",
            "SOCMovePiece:game=ga|param1=1|param2=3|param3=3078|param4=3846"
        },
        {new SOCMoveRobber("ga", 3, 0x305), "1034|ga,3,773", "SOCMoveRobber:game=ga|playerNumber=3|coord=305"},
        {new SOCNewGame("ga"), "1016|ga", "SOCNewGame:game=ga"},
        {
            new SOCNewGameWithOptions("ga", SOCGameOption.parseOptionsToMap("BC=t4,RD=f,N7=t7,PL=4"), -1, 0),
            "1079|ga,-1,BC=t4,RD=f,N7=t7,PL=4",
            "SOCNewGameWithOptions:game=ga|param1=-1|param2=BC=t4,RD=f,N7=t7,PL=4"
        },
        {new SOCPlayDevCardRequest("ga", SOCDevCardConstants.KNIGHT), "1049|ga,9", "SOCPlayDevCardRequest:game=ga|devCard=9"},
        {new SOCPlayerElement("ga", 1, SOCPlayerElement.SET, 105, 1), "1024|ga,1,100,105,1", "SOCPlayerElement:game=ga|playerNum=1|actionType=SET|elementType=105|amount=1"},
        {new SOCPlayerElement("ga", 2, SOCPlayerElement.LOSE, 4, 1, true), "1024|ga,2,102,4,1,Y", "SOCPlayerElement:game=ga|playerNum=2|actionType=LOSE|elementType=4|amount=1|news=Y"},
        {
            new SOCPlayerElements("ga", 2, SOCPlayerElement.GAIN, new SOCResourceSet(1, 0, 2, 3, 4, 0)),
            "1086|ga|2|101|1|1|3|2|4|3|5|4",
            "SOCPlayerElements:game=ga|playerNum=2|actionType=GAIN|e1=1,e3=2,e4=3,e5=4"
        },
        {
            new SOCPlayerElements("ga", 2, SOCPlayerElement.SET, new PEType[]{PEType.LAST_SETTLEMENT_NODE, PEType.NUMKNIGHTS, PEType.ROADS}, new int[]{69, 0, 13}),
            "1086|ga|2|100|18|69|15|0|10|13",
            "SOCPlayerElements:game=ga|playerNum=2|actionType=SET|e18=69,e15=0,e10=13"
        },
        /*
SOCPlayerStats:game=ga|p=1|p=2|p=5|p=0|p=3|p=0
SOCPotentialSettlements:game=classic|playerNum=2|list=(empty)
SOCPotentialSettlements:game=message-seqs|playerNum=3|list=c04 e05 60a
         */
        {new SOCPutPiece("ga", 3, 0, 1034), "1009|ga,3,0,1034", "SOCPutPiece:game=ga|playerNumber=3|pieceType=0|coord=40a"},
        {new SOCRejectOffer("ga", 2), "1037|ga,2", "SOCRejectOffer:game=ga|playerNumber=2"},
        {new SOCRobotDismiss("ga"), "1056|ga", "SOCRobotDismiss:game=ga"},
        {new SOCRollDice("ga"), "1031|ga", "SOCRollDice:game=ga"},
        {new SOCRollDicePrompt("ga", 3), "1072|ga,3", "SOCRollDicePrompt:game=ga|playerNumber=3"},
        {new SOCSetPlayedDevCard("ga", 2, false), "1048|ga,2,false", "SOCSetPlayedDevCard:game=ga|playerNumber=2|playedDevCard=false"},
        {new SOCSetSeatLock("ga", 2, SeatLockState.LOCKED), "1068|ga,2,true", "SOCSetSeatLock:game=ga|playerNumber=2|state=LOCKED"},
        {
            new SOCSetSeatLock("ga", new SeatLockState[]{SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.LOCKED, SeatLockState.UNLOCKED}),
            "1068|ga,false,clear,true,false",
            "SOCSetSeatLock:game=ga|states=UNLOCKED,CLEAR_ON_RESET,LOCKED,UNLOCKED"
        },
        {new SOCSetTurn("ga", 2), "1055|ga,2", "SOCSetTurn:game=ga|param=2"},
        {new SOCSimpleAction("ga", 3, 1, 22), "1090|ga,3,1,22,0", "SOCSimpleAction:game=ga|pn=3|actType=1|v1=22|v2=0"},
        {new SOCSitDown("ga", "testp2", 2, false), "1012|ga,testp2,2,false", "SOCSitDown:game=ga|nickname=testp2|playerNumber=2|robotFlag=false"},
        {new SOCStartGame("ga", SOCGame.START1A), "1018|ga,5", "SOCStartGame:game=ga|gameState=5"},
        {new SOCSVPTextMessage("ga", 3, 2, "settling a new island", true), "1097|ga,3,2,settling a new island", "SOCSVPTextMessage:game=ga|pn=3|svp=2|desc=settling a new island"},
        {new SOCTurn("ga", 3, 0), "1026|ga,3", "SOCTurn:game=ga|playerNumber=3"},
        {new SOCTurn("ga", 3, SOCGame.ROLL_OR_CARD), "1026|ga,3,15", "SOCTurn:game=ga|playerNumber=3|gameState=15"},
    };

    /** Tests for {@link SOCMessage#stripAttribNames(String)}. */
    public void testStripAttribNames()
    {
        assertEquals("", SOCMessage.stripAttribNames(""));
        assertEquals("xyz", SOCMessage.stripAttribNames("xyz"));
        assertEquals("xyz", SOCMessage.stripAttribNames("param=xyz"));
        assertEquals("xyz,abc", SOCMessage.stripAttribNames("xyz|p=abc"));
        assertEquals("xyz,abc", SOCMessage.stripAttribNames("param=xyz|p=abc"));
    }

    /** Tests for {@link SOCMessage#stripAttribsToList(String)}. */
    @Test
    public void testStripAttribsToList()
    {
        List<String> expected = new ArrayList<>();

        // single-element lists
        expected.add("xyz");
        assertTrue(expected.equals(SOCMessage.stripAttribsToList("xyz")));
        assertTrue(expected.equals(SOCMessage.stripAttribsToList("param=xyz")));

        // multi-element
        expected.add("abc");
        assertTrue(expected.equals(SOCMessage.stripAttribsToList("xyz|abc")));
        assertTrue(expected.equals(SOCMessage.stripAttribsToList("xyz|param=abc")));
        assertTrue(expected.equals(SOCMessage.stripAttribsToList("param=xyz|param=abc")));

        // empty list
        List<String> li = SOCMessage.stripAttribsToList("");
        assertNotNull(li);
        assertEquals(1, li.size());
        assertEquals("", li.get(0));
    }

}
