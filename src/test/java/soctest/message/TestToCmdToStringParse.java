/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2020,2022 Jeremy D Monin <jeremy@nand.net>
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
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Set;

import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCTradeOffer;
import soc.game.SOCGame.SeatLockState;
import soc.message.*;
import soc.message.SOCGameElements.GEType;
import soc.message.SOCPlayerElement.PEType;

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
    /**
     * Round-trip parsing tests on messages listed in {@link #TOCMD_TOSTRING_COMPARES}.
     * @see #testCoverageMessageRenameMap()
     */
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
            boolean parseOnly = false;  // if true, not round-trip
            Set<String> ignoreObjFields = null;
            if (compareCase.length > 3)
            {
                if (compareCase[3] == OPT_PARSE_ONLY)
                {
                    parseOnly = true;
                }
                else if (compareCase[3] == OPT_IGNORE_OBJ_FIELDS)
                {
                    @SuppressWarnings("unchecked")
                    Set<String> ignores = (Set<String>) compareCase[4];
                    ignoreObjFields = ignores;
                }
            }

            String s = msg.toCmd();  // call even if not checking contents, to make sure no exception is thrown
            if (expectedToCmd == null)
            {
                // that's fine, skip toCmd
            } else if (! s.equals(expectedToCmd)) {
                res.append(" toCmd: expected \"" + expectedToCmd + "\", got \"" + s + "\"");
            } else {
                // finish round-trip compare msg -> toCmd() -> toMsg(cmd)
                SOCMessage rev = SOCMessage.toMsg(s);
                if (rev == null)
                {
                    res.append(" toMsg(cmd): got null");
                } else if (! msgClass.isInstance(rev)) {
                    res.append(" toMsg(cmd): got wrong class " + rev.getClass().getSimpleName());
                } else {
                    compareMsgObjFields(msgClass, msg, rev, res, ignoreObjFields);
                }
            }

            s = (parseOnly)
                ? expectedToString
                : msg.toString();  // call even if not checking contents, to make sure no exception is thrown
            if (expectedToString != null)
            {
                if (! (parseOnly || s.equals(expectedToString)))
                {
                    res.append(" toString: expected \"" + expectedToString + "\", got \"" + s + "\"");
                } else {
                    // finish round-trip compare msg -> toString() -> parseMsgStr(str)
                    try
                    {
                        SOCMessage rev = SOCMessage.parseMsgStr(s);
                        if (rev == null)
                        {
                            res.append(" parseMsgStr(s): got null");
                        } else if (! msgClass.isInstance(rev)) {
                            res.append(" parseMsgStr(s): got wrong class " + rev.getClass().getSimpleName());
                        } else {
                            compareMsgObjFields(msgClass, msg, rev, res, ignoreObjFields);
                        }
                    } catch (InputMismatchException e) {
                        res.append(" parseMsgStr(s) rejected: " + e.getMessage());
                    } catch (ParseException e) {
                        res.append(" parseMsgStr(s) failed: " + e);
                    }
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
     * @param ignoreObjFields  Instance field names to ignore during comparison, or {@code null}
     */
    private void compareMsgObjFields
        (final Class<? extends SOCMessage> msgClass,
         final SOCMessage mExpected, final SOCMessage mActual, final StringBuilder res,
         final Set<String> ignoreObjFields)
    {
        final String className = msgClass.getSimpleName();
        assertTrue("mExpected instanceof " + className, msgClass.isInstance(mExpected));
        assertTrue("mActual instanceof " + className, msgClass.isInstance(mActual));

        for (Field f : msgClass.getDeclaredFields())
        {
            if (Modifier.isStatic(f.getModifiers()))
                continue;

            final String fName = f.getName();
            if ((ignoreObjFields != null) && ignoreObjFields.contains(fName))
                continue;

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
                    else if ((valueExpected instanceof int[][]) && (valueActual instanceof int[][]))
                    {
                        if (! Arrays.deepEquals((int[][]) valueExpected, (int[][]) valueActual))
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
     * <LI> int[][] -> [[1, 2, 3], [3079, -3083, 3335]]
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
        else if (val instanceof int[][])
            ret = Arrays.deepToString((int[][]) val);
        else if (val instanceof boolean[])
            ret = Arrays.toString((boolean[]) val);
        else if (val instanceof Object[])
            ret = Arrays.toString((Object[]) val);
        else
            ret = val.toString();

        return ret;
    }

    /**
     * {@link SOCDiceResultResources} has no server constructor, only {@code buildForGame(SOCGame)}, and
     * {@link SOCPlayerStats#SOCPlayerStats(SOCPlayer, int) SOCPlayerStats constructor} also needs a SOCGame object.
     * Player 1's {@link SOCPlayer#getRolledResources()} and {@link SOCPlayer#getResourceRollStats()}:
     * 4 brick, 2 wood. Player 3: 2 ore, 5 wheat.
     */
    private static final SOCGame GAME_WITH_PLAYER_RESOURCES;
    static
    {
        final SOCGame ga = new SOCGame("ga");
        ga.addPlayer("p1", 1);
        ga.addPlayer("p3", 3);
        ga.getPlayer(1).addRolledResources(new SOCResourceSet(4, 0, 0, 0, 2, 0));
        ga.getPlayer(3).addRolledResources(new SOCResourceSet(0, 2, 0, 5, 0, 0));

        GAME_WITH_PLAYER_RESOURCES = ga;
    }

    /**
     * Marker to say next item is a <tt>{@link Set}&lt;String&gt;</tt> of fields to ignore when comparing object
     * fields in {@link #compareMsgObjFields(Class, SOCMessage, SOCMessage, StringBuilder, Set)}.
     * @see #OPT_PARSE_ONLY
     */
    private static final Object OPT_IGNORE_OBJ_FIELDS = new Object();

    /**
     * Marker to say message should only be parsed from its toCmd/toString delimited strings into a SOCMessage,
     * not round-trip rendered to toString and recompared. Still round-trips to toCmd unless that element is {@code null}.
     * Useful for v1.x message backwards-compatibility tests.
     * @see #OPT_IGNORE_OBJ_FIELDS
     */
    private static final Object OPT_PARSE_ONLY = new Object();

    /**
     * Message parsing round-trip test cases for {@link #testRoundTripParsing()}.
     * Each element array's format is:
     *<UL>
     * <LI> Message object with expected field values
     * <LI> {@link SOCMessage#toCmd()} expected output, for {@link SOCMessage#toMsg(String)} parsing,
     *      or {@code null} to not validate {@code toCmd()} contents
     * <LI> {@link SOCMessage#toString()} expected output, for {@link SOCMessage#parseMsgStr(String)} parsing,
     *      or {@code null} to not validate {@code toString()} contents
     *</UL>
     * The element can end there, or have markers like {@link #OPT_PARSE_ONLY} or {@link #OPT_IGNORE_OBJ_FIELDS}
     * and any associated parameters.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })  // for SOCPotentialSettlements constructor calls
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
            // v1.x was SOCJoinGameRequest:
        {new SOCBotJoinGameRequest("ga", 3, SOCGameOption.parseOptionsToMap("PL=6")), "1023|ga,3,PL=6", "SOCJoinGameRequest:game=ga|playerNumber=3|opts=PL=6", OPT_PARSE_ONLY},
        {new SOCBuildRequest("ga", SOCPlayingPiece.CITY), "1043|ga,2", "SOCBuildRequest:game=ga|pieceType=2"},
        {new SOCBuyDevCardRequest("ga"), "1045|ga", "SOCBuyDevCardRequest:game=ga"},
            // v1.x was SOCBuyCardRequest:
        {new SOCBuyDevCardRequest("ga"), "1045|ga", "SOCBuyCardRequest:game=ga", OPT_PARSE_ONLY},
        {
            new SOCBoardLayout
                ("ga",
                 new int[]{50, 6, 65, 6, 6, 5, 3, 4, 10, 8, 2, 3, 1, 0, 6, 6, 1, 1, 4, 3, 4, 11, 8, 2, 5, 5, 2, 6, 6, 5, 3, 4, 100, 19, 6, 101, 6},
                 new int[]{-1, -1, -1, -1, -1, 1, 4, 0, -1, -1, 5, 2, 6, -1, -1, -1, 7, 3, 8, 7, 3, -1, -1, 6, 4, 1, 5, -1, -1, 9, 8, 2, -1, -1, -1, -1, -1 },
                 0x9b, true),
            "1014|ga,50,6,65,6,6,5,3,4,10,8,2,3,1,0,6,6,1,1,4,3,4,11,8,2,5,5,2,6,6,5,3,4,100,19,6,101,6,-1,-1,-1,-1,-1,1,4,0,-1,-1,5,2,6,-1,-1,-1,7,3,8,7,3,-1,-1,6,4,1,5,-1,-1,9,8,2,-1,-1,-1,-1,-1,155",
            "SOCBoardLayout:game=ga|hexLayout={ 50 6 65 6 6 5 3 4 10 8 2 3 1 0 6 6 1 1 4 3 4 11 8 2 5 5 2 6 6 5 3 4 100 19 6 101 6 }|numberLayout={ -1 -1 -1 -1 -1 1 4 0 -1 -1 5 2 6 -1 -1 -1 7 3 8 7 3 -1 -1 6 4 1 5 -1 -1 9 8 2 -1 -1 -1 -1 -1 }|robberHex=0x9b"
        },
        {new SOCCancelBuildRequest("ga", SOCPlayingPiece.CITY), "1044|ga,2", "SOCCancelBuildRequest:game=ga|pieceType=2"},
        {new SOCChangeFace("ga", 3, 7), "1058|ga,3,7", "SOCChangeFace:game=ga|playerNumber=3|faceId=7"},
        {new SOCChannelMembers("cha", Arrays.asList("player0", "droid 1", "robot 2", "debug")), "1002|cha,player0,droid 1,robot 2,debug", "SOCChannelMembers:channel=cha|members=[player0, droid 1, robot 2, debug]"},
        {new SOCChannelMembers("cha", Arrays.asList("m")), "1002|cha,m", "SOCChannelMembers:channel=cha|members=[m]"},  // shortest list
            // v1.x was SOCMembers, slightly different list format:
        {new SOCChannelMembers("cha", Arrays.asList("player0", "droid 1", "debug")), "1002|cha,player0,droid 1,debug", "SOCMembers:channel=cha|members=player0,droid 1,debug", OPT_PARSE_ONLY},
        // {"SOCTextMsg", "SOCChannelTextMsg"}
        {
            new SOCChannelTextMsg("cha", "member name", "msg which may,have,delimiters"),
            "1005|cha\000member name\000msg which may,have,delimiters",
            "SOCChannelTextMsg:channel=cha|nickname=member name|text=msg which may,have,delimiters"
        },
            // v1.x was SOCTextMsg:
        {
            new SOCChannelTextMsg("cha", "member name", "txt contents"),
            "1005|cha\000member name\000txt contents",
            "SOCTextMsg:channel=cha|nickname=member name|text=txt contents",
            OPT_PARSE_ONLY
        },
        {new SOCChoosePlayer("ga", 2), "1035|ga,2", "SOCChoosePlayer:game=ga|choice=2"},
        {
            new SOCChoosePlayerRequest("ga", new boolean[]{true, false, false, true}, true),
            "1036|ga,NONE,true,false,false,true",
            "SOCChoosePlayerRequest:game=ga|canChooseNone=true|choices=[true, false, false, true]"
        },
        {
            new SOCChoosePlayerRequest("ga", new boolean[]{true, false, false, true}, false),
            "1036|ga,true,false,false,true",
            "SOCChoosePlayerRequest:game=ga|choices=[true, false, false, true]"
        },
        {new SOCClearOffer("ga", 2), "1038|ga,2", "SOCClearOffer:game=ga|playerNumber=2"},
        {new SOCClearTradeMsg("ga", -1), "1042|ga,-1", "SOCClearTradeMsg:game=ga|playerNumber=-1"},
        {new SOCDebugFreePlace("ga", 3, true), "1087|ga,3,0,1", "SOCDebugFreePlace:game=ga|playerNumber=3|pieceType=0|coord=0x1"},
        {new SOCDebugFreePlace("ga", 3, SOCPlayingPiece.SETTLEMENT, 0x405), "1087|ga,3,1,1029", "SOCDebugFreePlace:game=ga|playerNumber=3|pieceType=1|coord=0x405"},
        {new SOCDeleteGame("ga"), "1015|ga", "SOCDeleteGame:game=ga"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_OLD, 6), "1046|ga,3,3,6", "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_OLD|cardType=6"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_NEW, 9), "1046|ga,3,2,9", "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_NEW|cardType=9"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.DRAW, 5), "1046|ga,3,0,5", "SOCDevCardAction:game=ga|playerNum=3|actionType=DRAW|cardType=5"},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.PLAY, 9), "1046|ga,3,1,9", "SOCDevCardAction:game=ga|playerNum=3|actionType=PLAY|cardType=9"},
            // v1.x was SOCDevCard:
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.DRAW, 2), "1046|ga,3,0,2", "SOCDevCard:game=ga|playerNum=3|actionType=0|cardType=2", OPT_PARSE_ONLY},
        {new SOCDevCardAction("ga", 3, SOCDevCardAction.DRAW, SOCDevCardConstants.KNIGHT), null, "SOCDevCard:game=ga|playerNum=3|actionType=0|cardType=0", OPT_PARSE_ONLY},
        {
            // v2.x end-of-game form
            new SOCDevCardAction("ga", 3, SOCDevCardAction.ADD_OLD, Arrays.asList(new Integer[]{5, 4})),
            "1046|ga,3,3,5,4",
            "SOCDevCardAction:game=ga|playerNum=3|actionType=ADD_OLD|cardTypes=[5, 4]"
        },
        {new SOCDevCardCount("ga", 22), "1047|ga,22", "SOCDevCardCount:game=ga|numDevCards=22"},
        {new SOCDiceResult("ga", 9), "1028|ga,9", "SOCDiceResult:game=ga|param=9"},
        {
            SOCDiceResultResources.buildForGame(GAME_WITH_PLAYER_RESOURCES),
            "1092|ga|2|1|6|4|1|2|5|0|3|7|2|2|5|4",
            "SOCDiceResultResources:game=ga|p=2|p=1|p=6|p=4|p=1|p=2|p=5|p=0|p=3|p=7|p=2|p=2|p=5|p=4",
            OPT_IGNORE_OBJ_FIELDS,
            new HashSet<String>(Arrays.asList("playerNum", "playerRsrc", "playerResTotal"))
        },
        {
            new SOCDiscard("ga", 3, new SOCResourceSet(2, 1, 3, 1, 2, 0)),
            "1033|ga,2,1,3,1,2,0",
            "SOCDiscard:game=ga|resources=clay=2|ore=1|sheep=3|wheat=1|wood=2|unknown=0"
        },
        {
            new SOCDiscard("ga", 2, 1, 3, 1, 2, 0),
            "1033|ga,2,1,3,1,2,0",
            "SOCDiscard:game=ga|resources=clay=2|ore=1|sheep=3|wheat=1|wood=2|unknown=0"
        },
        {new SOCDiscardRequest("ga", 4), "1029|ga,4", "SOCDiscardRequest:game=ga|numDiscards=4"},
        {new SOCEndTurn("ga"), "1032|ga", "SOCEndTurn:game=ga"},
        {new SOCFirstPlayer("ga", 2), "1054|ga,2", "SOCFirstPlayer:game=ga|playerNumber=2"},
        {new SOCGameElements("ga", GEType.CURRENT_PLAYER, 1), "1096|ga|4|1", "SOCGameElements:game=ga|e4=1"},
        {
            new SOCGameElements
                ("ga", new GEType[]{GEType.DEV_CARD_COUNT, GEType.ROUND_COUNT, GEType.FIRST_PLAYER, GEType.LONGEST_ROAD_PLAYER, GEType.LARGEST_ARMY_PLAYER},
                 new int[]{25, 2, 1, -1, -1}),
            "1096|ga|2|25|1|2|3|1|6|-1|5|-1",
            "SOCGameElements:game=ga|e2=25,e1=2,e3=1,e6=-1,e5=-1"
        },
        {new SOCGameMembers("ga", Arrays.asList("player0", "droid 1", "robot 2", "debug")), "1017|ga,player0,droid 1,robot 2,debug", "SOCGameMembers:game=ga|members=[player0, droid 1, robot 2, debug]"},
        {new SOCGameMembers("ga", Arrays.asList("p")), "1017|ga,p", "SOCGameMembers:game=ga|members=[p]"},  // shortest list
            // v1.x: slightly different list format, same message type name
        {new SOCGameMembers("ga", Arrays.asList("player0", "droid 1", "robot 2", "debug")), "1017|ga,player0,droid 1,robot 2,debug", "SOCGameMembers:game=ga|members=player0,droid 1,robot 2,debug", OPT_PARSE_ONLY},
        {
            new SOCGameServerText("ga", "You stole a wheat from robot 2."),
            "1091|ga" + SOCGameServerText.UNLIKELY_CHAR1 + "You stole a wheat from robot 2.",
            "SOCGameServerText:game=ga|text=You stole a wheat from robot 2."
        },
        {new SOCGameState("ga", 20), "1025|ga,20", "SOCGameState:game=ga|state=20"},
        {new SOCGameStats("ga", new int[]{10,  4, 3, 2}, new boolean[]{false, true, true, true}), "1061|ga,10,4,3,2,false,true,true,true", "SOCGameStats:game=ga|10|4|3|2|false|true|true|true"},
        {
            new SOCGameTextMsg("ga", SOCGameTextMsg.SERVERNAME, "testp3 built a road, text,may=contain,delimiters"),
            "1010|ga\000Server\000testp3 built a road, text,may=contain,delimiters",
            "SOCGameTextMsg:game=ga|nickname=Server|text=testp3 built a road, text,may=contain,delimiters"
        },
        {new SOCJoinChannel("m name", "", "-", "ch name"), "1004|m name,\t,-,ch name", "SOCJoinChannel:nickname=m name|password empty|host=-|channel=ch name"},
        {new SOCJoinChannel("m name", "***", "-", "ch name"), "1004|m name,***,-,ch name", "SOCJoinChannel:nickname=m name|password=***|host=-|channel=ch name"},
            // v1.x was SOCJoin:
        {new SOCJoinChannel("m name", "", "-", "ch name"), "1004|m name,\t,-,ch name", "SOCJoin:nickname=m name|password empty|host=-|channel=ch name", OPT_PARSE_ONLY},
        {new SOCJoinChannelAuth("m name", "ch name"), "1020|m name,ch name", "SOCJoinChannelAuth:nickname=m name|channel=ch name"},
            // v1.x was SOCJoinAuth:
        {new SOCJoinChannelAuth("m name", "ch name"), "1020|m name,ch name", "SOCJoinAuth:nickname=m name|channel=ch name", OPT_PARSE_ONLY},
        {new SOCJoinGame("testp2", "", SOCMessage.EMPTYSTR, "ga"), "1013|testp2,\t,\t,ga", "SOCJoinGame:nickname=testp2|password empty|host=\t|game=ga"},
        {new SOCJoinGameAuth("ga"), "1021|ga", "SOCJoinGameAuth:game=ga"},
        {new SOCJoinGameAuth("ga", 20, 21, new int[]{-2, 1, 3, 0}), "1021|ga,20,21,S,-2,1,3,0", "SOCJoinGameAuth:game=ga|bh=20|bw=21|vs=[-2, 1, 3, 0]"},
        {new SOCLargestArmy("ga", 2), "1067|ga,2", "SOCLargestArmy:game=ga|playerNumber=2"},
        {new SOCLastSettlement("ga", 2, 0x405), "1060|ga,2,1029", "SOCLastSettlement:game=ga|playerNumber=2|coord=405"},
        {new SOCLeaveAll(), "1008", "SOCLeaveAll:"},
        {new SOCLeaveChannel("m name", "-", "ch name"), "1006|m name,-,ch name", "SOCLeaveChannel:nickname=m name|host=-|channel=ch name"},
            // v1.x was SOCLeave:
        {new SOCLeaveChannel("m name", "-", "ch name"), "1006|m name,-,ch name", "SOCLeave:nickname=m name|host=-|channel=ch name", OPT_PARSE_ONLY},
        {new SOCLeaveGame("testp2", "-", "ga"), "1011|testp2,-,ga", "SOCLeaveGame:nickname=testp2|host=-|game=ga"},
        {
            new SOCLocalizedStrings(SOCLocalizedStrings.TYPE_SCENARIO, 0, "SC_FOG"),
            "1100|S|0|SC_FOG",
            null  // TODO SOCLocalizedStrings +stripAttribNames
        },
        {
            new SOCLocalizedStrings(SOCLocalizedStrings.TYPE_SCENARIO, SOCLocalizedStrings.FLAG_REQ_ALL, (List<String>) null),
            "1100|S|2",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "SC_WOND")),
            "1100|S|0|SC_FOG|SC_WOND",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "name text", "desc text")),
            "1100|S|0|SC_FOG|name text|desc text",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "name text", null)),
            "1100|S|0|SC_FOG|name text|\t",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", SOCLocalizedStrings.MARKER_KEY_UNKNOWN)),
            "1100|S|0|SC_FOG|\026K",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0,
                 Arrays.asList("SC_WOND", SOCLocalizedStrings.MARKER_KEY_UNKNOWN, "SC_FOG", "name text", "desc text")),
            "1100|S|0|SC_WOND|\026K|SC_FOG|name text|desc text",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL, (List<String>) null),
            "1100|O|4",
            null
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL,
                 Arrays.asList("SC", "scenario")),
             "1100|O|4|SC|scenario",
             null
        },
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
            new SOCNewGameWithOptions("ga", SOCGameOption.parseOptionsToMap("BC=t4,RD=f,N7=t7,PL=4,SBL=t"), -1, 0),
            "1079|ga,-1,BC=t4,RD=f,N7=t7,PL=4,SBL=t",
            "SOCNewGameWithOptions:game=ga|param1=-1|param2=BC=t4,RD=f,N7=t7,PL=4,SBL=t"
        },
        {new SOCPickResources("ga", new SOCResourceSet(0, 1, 0, 0, 1, 0)), "1052|ga,0,1,0,0,1", "SOCPickResources:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0"},
        {new SOCPickResources("ga", 0, 1, 0, 0, 1), "1052|ga,0,1,0,0,1", "SOCPickResources:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0"},
            // v1.x was SOCDiscoveryPick:
        {new SOCPickResources("ga", 0, 1, 0, 0, 1), "1052|ga,0,1,0,0,1", "SOCDiscoveryPick:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0", OPT_PARSE_ONLY},
        {new SOCPickResourceType("ga", SOCResourceConstants.SHEEP), "1053|ga,3", "SOCPickResourceType:game=ga|resType=3"},
            // v1.x was SOCMonopolyPick:
        {new SOCPickResourceType("ga", SOCResourceConstants.WHEAT), "1053|ga,4", "SOCMonopolyPick:game=ga|resource=4", OPT_PARSE_ONLY},
        {new SOCPieceValue("ga", SOCPlayingPiece.VILLAGE, 0xa06, 4, 0), "1095|ga,5,2566,4,0", "SOCPieceValue:game=ga|param1=5|param2=2566|param3=4|param4=0"},
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
        {
            new SOCPlayerStats(GAME_WITH_PLAYER_RESOURCES.getPlayer(3), SOCPlayerStats.STYPE_RES_ROLL),
            "1085|ga|1|0|2|0|5|0",
            "SOCPlayerStats:game=ga|p=1|p=0|p=2|p=0|p=5|p=0"
        },
        {new SOCPotentialSettlements("ga", 2, new ArrayList<Integer>()), "1057|ga,2", "SOCPotentialSettlements:game=ga|playerNum=2|list=(empty)"},
        {new SOCPotentialSettlements("ga", 3, new ArrayList<Integer>(Arrays.asList(0xc04, 0xe05, 0x60a))), "1057|ga,3,3076,3589,1546", "SOCPotentialSettlements:game=ga|playerNum=3|list=c04 e05 60a "},
        // v2.x forms: For easier comparison, these tests all have 1 node in each land area set instead of many
        {
            // 1 player's info, non-null psNodes, no LSE
            new SOCPotentialSettlements
                ("ga", 0, new ArrayList<Integer>(Arrays.asList(0xa04, 0xa08)), 0,
                 new HashSet[]{null, new HashSet(Arrays.asList(0xc02)), new HashSet(Arrays.asList(0x408)), new HashSet(Arrays.asList(0xa0f)), new HashSet(Arrays.asList(0x100c))},
                 null ),
            "1057|ga,0,2564,2568,NA,4,PAN,0,LA1,3074,LA2,1032,LA3,2575,LA4,4108",
            "SOCPotentialSettlements:game=ga|playerNum=0|list=a04 a08 |pan=0|la1=c02 |la2=408 |la3=a0f |la4=100c "
        },
        {
            // all players' info, "(null)" psNodes, no LSE
            new SOCPotentialSettlements
                ("ga", -1, null, 1,
                 new HashSet[]{null, new HashSet(Arrays.asList(0x802)), new HashSet(Arrays.asList(0xc02))},
                 null ),
            "1057|ga,-1,NA,2,PAN,1,LA1,2050,LA2,3074",
            "SOCPotentialSettlements:game=ga|playerNum=-1|list=(null)|pan=1|la1=802 |la2=c02 "
        },
        {
            // 1 player's LSE, "(null)" psNodes
            new SOCPotentialSettlements
                ("ga", 3, null, 1,
                 new HashSet[]{null, new HashSet(Arrays.asList(0xa0f)), new HashSet(Arrays.asList(0x60a))},
                 new int[][]{{0xc07, -0xc0b, 0xd07, -0xd0b, 0xe04, -0xe0a, 0xa03}} ),
            "1057|ga,3,NA,2,PAN,1,LA1,2575,LA2,1546,SE,c07,-c0b,d07,-d0b,e04,-e0a,a03",
            "SOCPotentialSettlements:game=ga|playerNum=3|list=(null)|pan=1|la1=a0f |la2=60a |lse={{c07-c0b,d07-d0b,e04-e0a,a03}}"
        },
        {
            // 1 player's LSE, "(fromAllLANodes)" psNodes
            new SOCPotentialSettlements
                ("ga", 3, null, 0,
                 new HashSet[]{null, new HashSet(Arrays.asList(0xa0f)), new HashSet(Arrays.asList(0x60a))},
                 new int[][]{{0xc07, -0xc0b, 0xd07, -0xd0b, 0xe04, -0xe0a, 0xa03}} ),
            "1057|ga,3,NA,2,PAN,0,LA1,2575,LA2,1546,SE,c07,-c0b,d07,-d0b,e04,-e0a,a03",
            "SOCPotentialSettlements:game=ga|playerNum=3|list=(fromAllLANodes)|pan=0|la1=a0f |la2=60a |lse={{c07-c0b,d07-d0b,e04-e0a,a03}}"
        },
        {
            // 1 player's psList and LSE only, no LAs
            new SOCPotentialSettlements
                ("ga", 3,
                 new ArrayList<Integer>(Arrays.asList(0xa04, 0xa08)),
                 new int[][]{{0xc07, -0xc0b, 0xd07, -0xd0b, 0xe04, -0xe0a, 0xa03}}),
            "1057|ga,3,2564,2568,NA,0,PAN,0,SE,c07,-c0b,d07,-d0b,e04,-e0a,a03",
            "SOCPotentialSettlements:game=ga|playerNum=3|list=a04 a08 |lse={{c07-c0b,d07-d0b,e04-e0a,a03}}"
        },
        {
            // all players' LSE, "(empty)" psNodes; final part of lse is {} to test: toCmd sends as if was {0}
            new SOCPotentialSettlements
                ("ga", 3, new ArrayList<Integer>(), 0,
                 new HashSet[]{null, new HashSet(Arrays.asList(0xa0f)), new HashSet(Arrays.asList(0x60a))},
                 new int[][]{{0xc07, -0xc0b, 0xe04, -0xe0a}, {}, {0xd07, -0xd0b, 0xa03}, {}} ),
            "1057|ga,3,0,NA,2,PAN,0,LA1,2575,LA2,1546,SE,c07,-c0b,e04,-e0a,SE,SE,d07,-d0b,a03,SE,0",
            "SOCPotentialSettlements:game=ga|playerNum=3|list=(empty)|pan=0|la1=a0f |la2=60a |lse={{c07-c0b,e04-e0a},{},{d07-d0b,a03},{}}"
        },
        {new SOCPutPiece("ga", 3, 0, 1034), "1009|ga,3,0,1034", "SOCPutPiece:game=ga|playerNumber=3|pieceType=0|coord=40a"},
        {new SOCRejectOffer("ga", 2), "1037|ga,2", "SOCRejectOffer:game=ga|playerNumber=2"},
        {new SOCRemovePiece("ga", 2, SOCPlayingPiece.SHIP, 0xe04), "1094|ga,2,3,3588", "SOCRemovePiece:game=ga|param1=2|param2=3|param3=3588"},
        {new SOCResourceCount("ga", 3, 11), "1063|ga,3,11", "SOCResourceCount:game=ga|playerNumber=3|count=11"},
        {new SOCRevealFogHex("ga", 3340, SOCBoard.WOOD_HEX, 12), "10001|ga,3340,5,12", "SOCRevealFogHex:game=ga|param1=3340|param2=5|param3=12"},
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
        /*
SOCSetSpecialItem:game=w|op=SET|typeKey=_SC_WOND|gi=2|pi=0|pn=0|co=-1|lv=2|sv=w2
         */
        {new SOCSetTurn("ga", 2), "1055|ga,2", "SOCSetTurn:game=ga|param=2"},
        {new SOCSimpleAction("ga", 3, 1, 22), "1090|ga,3,1,22,0", "SOCSimpleAction:game=ga|pn=3|actType=1|v1=22|v2=0"},
        {new SOCSimpleRequest("ga", 2, 1001, 2562), "1089|ga,2,1001,2562,0", "SOCSimpleRequest:game=ga|pn=2|reqType=1001|v1=2562|v2=0"},
        {new SOCSimpleRequest("ga", 2, 1001, 2562, 7), "1089|ga,2,1001,2562,7", "SOCSimpleRequest:game=ga|pn=2|reqType=1001|v1=2562|v2=7"},
        {new SOCSitDown("ga", "testp2", 2, false), "1012|ga,testp2,2,false", "SOCSitDown:game=ga|nickname=testp2|playerNumber=2|robotFlag=false"},
        {new SOCStartGame("ga", SOCGame.START1A), "1018|ga,5", "SOCStartGame:game=ga|gameState=5"},
        {new SOCSVPTextMessage("ga", 3, 2, "settling a new island", true), "1097|ga,3,2,settling a new island", "SOCSVPTextMessage:game=ga|pn=3|svp=2|desc=settling a new island"},
        {new SOCTurn("ga", 3, 0), "1026|ga,3", "SOCTurn:game=ga|playerNumber=3"},
        {new SOCTurn("ga", 3, SOCGame.ROLL_OR_CARD), "1026|ga,3,15", "SOCTurn:game=ga|playerNumber=3|gameState=15"},
        // TODO SOCVersion
    };

    /**
     * Make sure {@link #TOCMD_TOSTRING_COMPARES} includes all
     * old and new message type names from {@link SOCMessage#MESSAGE_RENAME_MAP},
     * with same old->new mappings.
     * @see #testRoundTripParsing()
     */
    @Test
    public void testCoverageMessageRenameMap()
    {
        Set<String> unseenOldNames = new HashSet<>(SOCMessage.MESSAGE_RENAME_MAP.keySet()),
            unseenNewNames = new HashSet<>(SOCMessage.MESSAGE_RENAME_MAP.values());

        for (Object[] compareCase : TOCMD_TOSTRING_COMPARES)
        {
            final String newTypeName = compareCase[0].getClass().getSimpleName();
            unseenNewNames.remove(newTypeName);  // ok if wasn't in there

            String oldMsgToString = (String) compareCase[2];
            if (oldMsgToString == null)
                continue;
            int i = oldMsgToString.indexOf(':');
            assertTrue("can parsetype msg out of " + oldMsgToString, i > 0);

            final String oldTypeName = oldMsgToString.substring(0, i);
            if (unseenOldNames.contains(oldTypeName))
            {
                unseenOldNames.remove(oldTypeName);
                // consistency check
                assertEquals("new value SOCMessage.MESSAGE_RENAME_MAP(\"" + oldTypeName + "\")",
                    SOCMessage.MESSAGE_RENAME_MAP.get(oldTypeName), newTypeName);
            }
        }

        if (! unseenOldNames.isEmpty())
            fail("TOCMD_TOSTRING_COMPARES doesn't test these MESSAGE_RENAME_MAP old types: " + unseenOldNames);
        if (! unseenNewNames.isEmpty())
            fail("TOCMD_TOSTRING_COMPARES doesn't test these MESSAGE_RENAME_MAP new types: " + unseenNewNames);
    }

    /** Tests for {@link SOCMessage#stripAttribNames(String)}. */
    @Test
    public void testStripAttribNames()
    {
        final String[][] expected_actual =
            {
                {"", ""},
                {"xyz", "xyz"},
                {"xyz", "param=xyz"},
                {"xyz,abc", "xyz|p=abc"},
                {"xyz,abc", "param=xyz|p=abc"},
                // game name contains '='
                {"fancy=game==name,abc,xyz", "game=fancy=game==name|p1=abc|p2=xyz"},
                // other param contains '='
                {"ga,usual_name,==fancy=name", "game=ga|pn1=usual_name|pn2===fancy=name"},
                {"ga_special=name,[player==0, droid 1, robot 2, debug]",
                    "game=ga_special=name|members=[player==0, droid 1, robot 2, debug]"},
            };
        for (String[] exp_act : expected_actual)
            assertEquals(exp_act[0], SOCMessage.stripAttribNames(exp_act[1]));
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
