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
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Set;

import soc.game.SOCBoard;
import soc.game.SOCDevCardConstants;
import soc.game.SOCGame;
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayer;
import soc.game.SOCPlayingPiece;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.SOCScenario;
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
 * @see TestTemplatesAbstracts
 * @since 2.4.50
 */
public class TestToCmdToStringParse
{
    private static SOCGameOptionSet knownOpts = SOCGameOptionSet.getAllKnownOptions();

    /**
     * Round-trip parsing tests on messages listed in {@link #TOCMD_TOSTRING_COMPARES}.
     * Message forms which need more detailed tests are in {@link #testMiscMessageForms()} instead.
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
            boolean skipParseToString = false;  // if true, not round-trip
            Set<String> ignoreObjFields = null;
            // look for test options/params
            for (int i = 3; i < compareCase.length; ++i)
            {
                if (compareCase[i] == OPT_PARSE_ONLY)
                {
                    parseOnly = true;
                }
                else if (compareCase[i] == OPT_SKIP_PARSE)
                {
                    skipParseToString = true;
                }
                else if (compareCase[i] == OPT_IGNORE_OBJ_FIELDS)
                {
                    ++i;
                    @SuppressWarnings("unchecked")
                    Set<String> ignores = (Set<String>) compareCase[i];
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
                }
                else if (! skipParseToString)
                {
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

    /** Test data; copy before passing to any message constructor. */
    private static final List<String> SCENS_KEY_LIST;
    static
    {
        SCENS_KEY_LIST = new ArrayList<>();
        SCENS_KEY_LIST.add("KEY1");
        SCENS_KEY_LIST.add("KEY2");
    }

    /**
     * Marker to say next item is a <tt>{@link Set}&lt;String&gt;</tt> of fields to ignore when comparing object
     * fields in {@link #compareMsgObjFields(Class, SOCMessage, SOCMessage, StringBuilder, Set)}.
     * @see #OPT_PARSE_ONLY
     * @see #OPT_SKIP_PARSE
     */
    private static final Object OPT_IGNORE_OBJ_FIELDS = new Object();

    /**
     * Marker to say message should only be parsed from its toCmd/toString delimited strings into a SOCMessage,
     * not round-trip rendered to toString and recompared.
     * Still round-trips from/to toCmd unless that element is {@code null}.
     * Useful for v1.x message backwards-compatibility tests.
     * @see #OPT_IGNORE_OBJ_FIELDS
     * @see #OPT_SKIP_PARSE
     */
    private static final Object OPT_PARSE_ONLY = new Object();

    /**
     * Marker to say message should not be parsed from its toString delimited string into a SOCMessage,
     * only rendered from message to toString. Still round-trips from/to toCmd unless that element is {@code null}.
     * Useful during initial/basic tests of a new message.
     * @see #OPT_PARSE_ONLY
     */
    private static final Object OPT_SKIP_PARSE = new Object();

    /**
     * Message parsing round-trip test cases for {@link #testRoundTripParsing()}.
     * Each element array's format is:
     *<UL>
     * <LI> Message object with expected field values
     * <LI> {@link SOCMessage#toCmd()} expected output, for {@link SOCMessage#toMsg(String)} parsing,
     *      or {@code null} to not validate {@code toCmd()} contents
     * <LI> {@link SOCMessage#toString()} expected output, for {@link SOCMessage#parseMsgStr(String)} parsing,
     *      or {@code null} to not validate {@code toString()} contents
     *      (see also {@link #OPT_SKIP_PARSE})
     *</UL>
     * The element can end there, or have markers like {@link #OPT_PARSE_ONLY} or {@link #OPT_IGNORE_OBJ_FIELDS}
     * and any associated parameters.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })  // for SOCPotentialSettlements constructor calls
    private static final Object[][] TOCMD_TOSTRING_COMPARES =
    {
        {new SOCAcceptOffer("ga", 2, 3), "1039|ga,2,3", "SOCAcceptOffer:game=ga|accepting=2|offering=3"},
        {new SOCAcceptOffer("ga", -2, 3), "1039|ga,-2,3", "SOCAcceptOffer:game=ga|accepting=-2|offering=3"},
        // TODO? SOCAdminPing
        {new SOCAdminReset(), "1065", "SOCAdminReset:"},
        // TODO SOCAuthRequest
        {
            new SOCBankTrade("ga", new SOCResourceSet(0, 0, 2, 0, 0, 0), new SOCResourceSet(1, 0, 0, 0, 0, 0), 3),
            "1040|ga,0,0,2,0,0,1,0,0,0,0,3",
            "SOCBankTrade:game=ga|give=clay=0|ore=0|sheep=2|wheat=0|wood=0|unknown=0|get=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|pn=3"
        },
        {
            new SOCBankTrade("ga", new SOCResourceSet(), SOCResourceSet.EMPTY_SET, SOCBankTrade.PN_REPLY_NOT_YOUR_TURN),
                // tests server's disallow reply using both of the likely SOCResourceSet param forms
            "1040|ga,0,0,0,0,0,0,0,0,0,0,-3",
            "SOCBankTrade:game=ga|give=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=0|pn=-3"
        },
        {new SOCBCastTextMsg("msg text"), "1062|msg text", "SOCBCastTextMsg:text=msg text"},
        {
            new SOCBoardLayout
                ("ga",
                 new int[]{50, 6, 65, 6, 6, 5, 3, 4, 10, 8, 2, 3, 1, 0, 6, 6, 1, 1, 4, 3, 4, 11, 8, 2, 5, 5, 2, 6, 6, 5, 3, 4, 100, 19, 6, 101, 6},
                 new int[]{-1, -1, -1, -1, -1, 1, 4, 0, -1, -1, 5, 2, 6, -1, -1, -1, 7, 3, 8, 7, 3, -1, -1, 6, 4, 1, 5, -1, -1, 9, 8, 2, -1, -1, -1, -1, -1 },
                 0x9b, true),
            "1014|ga,50,6,65,6,6,5,3,4,10,8,2,3,1,0,6,6,1,1,4,3,4,11,8,2,5,5,2,6,6,5,3,4,100,19,6,101,6,-1,-1,-1,-1,-1,1,4,0,-1,-1,5,2,6,-1,-1,-1,7,3,8,7,3,-1,-1,6,4,1,5,-1,-1,9,8,2,-1,-1,-1,-1,-1,155",
            "SOCBoardLayout:game=ga|hexLayout={ 50 6 65 6 6 5 3 4 10 8 2 3 1 0 6 6 1 1 4 3 4 11 8 2 5 5 2 6 6 5 3 4 100 19 6 101 6 }|numberLayout={ -1 -1 -1 -1 -1 1 4 0 -1 -1 5 2 6 -1 -1 -1 7 3 8 7 3 -1 -1 6 4 1 5 -1 -1 9 8 2 -1 -1 -1 -1 -1 }|robberHex=0x9b"
        },
        // TODO SOCBoardLayout2
        {
            new SOCBotJoinGameRequest("ga", 3, (SOCGameOptionSet) null),
            "1023|ga,3,-",
            "SOCBotJoinGameRequest:game=ga|playerNumber=3|opts=-",
            OPT_IGNORE_OBJ_FIELDS, new HashSet<String>(Arrays.asList("opts"))
        },
        {
            new SOCBotJoinGameRequest("ga", 3, "PL=2,RD=t"),
            "1023|ga,3,PL=2,RD=t",
            "SOCBotJoinGameRequest:game=ga|playerNumber=3|opts=PL=2,RD=t",
            OPT_IGNORE_OBJ_FIELDS, new HashSet<String>(Arrays.asList("opts"))
        },
        {
            new SOCBotJoinGameRequest("ga", 2, SOCGameOption.parseOptionsToSet("PL=5", knownOpts)),
            "1023|ga,2,PL=5",
            "SOCBotJoinGameRequest:game=ga|playerNumber=2|opts=PL=5",
            OPT_IGNORE_OBJ_FIELDS, new HashSet<String>(Arrays.asList("opts"))
        },
            // v1.x was SOCJoinGameRequest:
        {
            new SOCBotJoinGameRequest("ga", 1, SOCGameOption.parseOptionsToSet("PL=6", knownOpts)),
            "1023|ga,1,PL=6",
            "SOCJoinGameRequest:game=ga|playerNumber=1|opts=PL=6",
            OPT_PARSE_ONLY,
            OPT_IGNORE_OBJ_FIELDS, new HashSet<String>(Arrays.asList("opts"))
        },
        {new SOCBuildRequest("ga", SOCPlayingPiece.CITY), "1043|ga,2", "SOCBuildRequest:game=ga|pieceType=2"},
        {new SOCBuyDevCardRequest("ga"), "1045|ga", "SOCBuyDevCardRequest:game=ga"},
            // v1.x was SOCBuyCardRequest:
        {new SOCBuyDevCardRequest("ga"), "1045|ga", "SOCBuyCardRequest:game=ga", OPT_PARSE_ONLY},
        {new SOCCancelBuildRequest("ga", SOCPlayingPiece.CITY), "1044|ga,2", "SOCCancelBuildRequest:game=ga|pieceType=2"},
        {new SOCChangeFace("ga", 3, 7), "1058|ga,3,7", "SOCChangeFace:game=ga|playerNumber=3|faceId=7"},
        {new SOCChannelMembers("cha", Arrays.asList("player0", "droid 1", "robot 2", "debug")), "1002|cha,player0,droid 1,robot 2,debug", "SOCChannelMembers:channel=cha|members=[player0, droid 1, robot 2, debug]"},
        {new SOCChannelMembers("cha", Arrays.asList("m")), "1002|cha,m", "SOCChannelMembers:channel=cha|members=[m]"},  // shortest list
            // v1.x was SOCMembers, slightly different list format:
        {new SOCChannelMembers("cha", Arrays.asList("player0", "droid 1", "debug")), "1002|cha,player0,droid 1,debug", "SOCMembers:channel=cha|members=player0,droid 1,debug", OPT_PARSE_ONLY},
        // TODO SOCChannels
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
        // TODO? SOCCreateAccount
        {new SOCDebugFreePlace("ga", 3, true), "1087|ga,3,0,1", "SOCDebugFreePlace:game=ga|playerNumber=3|pieceType=0|coord=0x1"},
        {new SOCDebugFreePlace("ga", 3, SOCPlayingPiece.SETTLEMENT, 0x405), "1087|ga,3,1,1029", "SOCDebugFreePlace:game=ga|playerNumber=3|pieceType=1|coord=0x405"},
        {new SOCDeleteChannel("ch name"), "1007|ch name", "SOCDeleteChannel:channel=ch name"},
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
        // TODO SOCGameOptionGetDefaults
        {new SOCGameOptionGetInfos(null, false, false), "1081|-", "SOCGameOptionGetInfos:options=-"},
        {new SOCGameOptionGetInfos(null, true, false), "1081|-,?I18N", "SOCGameOptionGetInfos:options=-,?I18N"},
        {new SOCGameOptionGetInfos(null, true, true), "1081|?I18N", "SOCGameOptionGetInfos:options=?I18N"},
        {new SOCGameOptionGetInfos(Arrays.asList("SC", "PLP"), false, false), "1081|SC,PLP", "SOCGameOptionGetInfos:options=SC,PLP"},
        {new SOCGameOptionGetInfos(Arrays.asList("SC", "PLP"), true, false), "1081|SC,PLP,?I18N", "SOCGameOptionGetInfos:options=SC,PLP,?I18N"},
        {
            new SOCGameOptionGetInfos(Arrays.asList(new SOCGameOption("N7"), new SOCGameOption("PL")), false),
            "1081|N7,PL",
            "SOCGameOptionGetInfos:options=N7,PL"
        },
        {
            new SOCGameOptionGetInfos(Arrays.asList(new SOCGameOption("N7"), new SOCGameOption("PL")), true),
            "1081|N7,PL,?I18N",
            "SOCGameOptionGetInfos:options=N7,PL,?I18N"
        },
        // For SOCGameOptionGetInfos forms with OPTKEY_GET_ANY_CHANGES, see testMiscMessageForms()
        // TODO SOCGameOptionInfo
        // TODO? SOCGames
        {
            new SOCGameServerText("ga", "You stole a wheat from robot 2."),
            "1091|ga" + SOCGameServerText.UNLIKELY_CHAR1 + "You stole a wheat from robot 2.",
            "SOCGameServerText:game=ga|text=You stole a wheat from robot 2."
        },
        {new SOCGameState("ga", 20), "1025|ga,20", "SOCGameState:game=ga|state=20"},
        {new SOCGameStats("ga", new int[]{10,  4, 3, 2}, new boolean[]{false, true, true, true}), "1061|ga,10,4,3,2,false,true,true,true", "SOCGameStats:game=ga|10|4|3|2|false|true|true|true"},
        // TODO? SOCGamesWithOptions
        {
            new SOCGameTextMsg("ga", SOCGameTextMsg.SERVERNAME, "testp3 built a road, text,may=contain,delimiters"),
            "1010|ga\000Server\000testp3 built a road, text,may=contain,delimiters",
            "SOCGameTextMsg:game=ga|nickname=Server|text=testp3 built a road, text,may=contain,delimiters"
        },
        {new SOCImARobot("robot 7", "**", "soc.robot.SomeExample"), "1022|robot 7,**,soc.robot.SomeExample", "SOCImARobot:nickname=robot 7|cookie=**|rbclass=soc.robot.SomeExample"},
        {
            new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.PLAY, 3),
            "1098|ga,3,4,3",
            "SOCInventoryItemAction:game=ga|playerNum=3|action=PLAY|itemType=3|rc=0",
            OPT_SKIP_PARSE
            // TODO +stripAttribNames
        },
        {
            new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.CANNOT_PLAY, 3, 1),
            "1098|ga,3,5,3,1",
            "SOCInventoryItemAction:game=ga|playerNum=3|action=CANNOT_PLAY|itemType=3|rc=1",
            OPT_SKIP_PARSE
        },
        {
            new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.ADD_OTHER, 5, true, false, true),
            "1098|ga,3,3,5,5",
            "SOCInventoryItemAction:game=ga|playerNum=3|action=ADD_OTHER|itemType=5|kept=true|isVP=false|canCancel=true",
            OPT_SKIP_PARSE
        },
        {
            new SOCInventoryItemAction("ga", 3, SOCInventoryItemAction.ADD_PLAYABLE, 2, false, false, false),
            "1098|ga,3,2,2",
            "SOCInventoryItemAction:game=ga|playerNum=3|action=ADD_PLAYABLE|itemType=2|kept=false|isVP=false|canCancel=false",
            OPT_SKIP_PARSE
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
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_FOG",
            OPT_SKIP_PARSE
            // TODO SOCLocalizedStrings +stripAttribNames
        },
        {
            new SOCLocalizedStrings(SOCLocalizedStrings.TYPE_SCENARIO, SOCLocalizedStrings.FLAG_REQ_ALL, (List<String>) null),
            "1100|S|2",
            "SOCLocalizedStrings:type=S|flags=0x2|(strs empty)",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "SC_WOND")),
            "1100|S|0|SC_FOG|SC_WOND",
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_FOG|SC_WOND",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "name text", "desc text")),
            "1100|S|0|SC_FOG|name text|desc text",
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_FOG|name text|desc text",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", "name text", null)),
            "1100|S|0|SC_FOG|name text|\t",
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_FOG|name text|(null)",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0, Arrays.asList("SC_FOG", SOCLocalizedStrings.MARKER_KEY_UNKNOWN)),
            "1100|S|0|SC_FOG|\026K",
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_FOG|K",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_SCENARIO, 0,
                 Arrays.asList("SC_WOND", SOCLocalizedStrings.MARKER_KEY_UNKNOWN, "SC_FOG", "name text", "desc text")),
            "1100|S|0|SC_WOND|\026K|SC_FOG|name text|desc text",
            "SOCLocalizedStrings:type=S|flags=0x0|strs=SC_WOND|K|SC_FOG|name text|desc text",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL, (List<String>) null),
            "1100|O|4",
            "SOCLocalizedStrings:type=O|flags=0x4|(strs empty)",
            OPT_SKIP_PARSE
        },
        {
            new SOCLocalizedStrings
                (SOCLocalizedStrings.TYPE_GAMEOPT, SOCLocalizedStrings.FLAG_SENT_ALL,
                 Arrays.asList("SC", "scenario")),
             "1100|O|4|SC|scenario",
             "SOCLocalizedStrings:type=O|flags=0x4|strs=SC|scenario",
             OPT_SKIP_PARSE
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
            new SOCMakeOffer("ga", new SOCTradeOffer
                ("ga", -2, new boolean[]{false,  false, false, false},
                 SOCResourceSet.EMPTY_SET, SOCResourceSet.EMPTY_SET)),
            "1041|ga,-2,false,false,false,false,0,0,0,0,0,0,0,0,0,0",
            "SOCMakeOffer:game=ga|offer=game=ga|from=-2|to=false,false,false,false|give=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=0"
        },
        {
            new SOCMovePiece("ga", 1, SOCPlayingPiece.SHIP, 3078, 3846),
            "1093|ga,1,3,3078,3846",
            "SOCMovePiece:game=ga|pn=1|pieceType=3|fromCoord=3078|toCoord=3846"
        },
        {new SOCMoveRobber("ga", 3, 0x305), "1034|ga,3,773", "SOCMoveRobber:game=ga|playerNumber=3|coord=305"},
        {new SOCNewChannel("ch name"), "1001|ch name", "SOCNewChannel:channel=ch name"},
        {new SOCNewGame("ga"), "1016|ga", "SOCNewGame:game=ga"},
        {
            new SOCNewGameWithOptions("ga", SOCGameOption.parseOptionsToSet("BC=t4,RD=f", knownOpts), -1, 0),
            "1079|ga,-1,BC=t4,RD=f",
            "SOCNewGameWithOptions:game=ga|param1=-1|param2=BC=t4,RD=f"
        },
        {
            new SOCNewGameWithOptionsRequest("uname", "", "-", "newgame", "N7=t7,PL=4"),
            "1078|uname,\t,-,newgame,N7=t7,PL=4",
            "SOCNewGameWithOptionsRequest:nickname=uname|password empty|host=-|game=newgame|opts=N7=t7,PL=4",
            OPT_SKIP_PARSE,
            OPT_IGNORE_OBJ_FIELDS, new HashSet<String>(Arrays.asList("optsStr"))
            // TODO +stripAttribNames
        },
        {new SOCPickResources("ga", new SOCResourceSet(0, 1, 0, 0, 1, 0)), "1052|ga,0,1,0,0,1", "SOCPickResources:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0"},
        {new SOCPickResources("ga", 0, 1, 0, 0, 1), "1052|ga,0,1,0,0,1", "SOCPickResources:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0"},
            // v1.x was SOCDiscoveryPick:
        {new SOCPickResources("ga", 0, 1, 0, 0, 1), "1052|ga,0,1,0,0,1", "SOCDiscoveryPick:game=ga|resources=clay=0|ore=1|sheep=0|wheat=0|wood=1|unknown=0", OPT_PARSE_ONLY},
        {new SOCPickResourceType("ga", SOCResourceConstants.SHEEP), "1053|ga,3", "SOCPickResourceType:game=ga|resType=3"},
            // v1.x was SOCMonopolyPick:
        {new SOCPickResourceType("ga", SOCResourceConstants.WHEAT), "1053|ga,4", "SOCMonopolyPick:game=ga|resource=4", OPT_PARSE_ONLY},
        {new SOCPieceValue("ga", SOCPlayingPiece.VILLAGE, 0xa06, 4, 0), "1095|ga,5,2566,4,0", "SOCPieceValue:game=ga|pieceType=5|coord=2566|pv1=4|pv2=0"},
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
        // same as above, but no trailing space in toString's list:
        {new SOCPotentialSettlements("ga", 3, new ArrayList<Integer>(Arrays.asList(0xc04, 0xe05, 0x60a))), "1057|ga,3,3076,3589,1546", "SOCPotentialSettlements:game=ga|playerNum=3|list=c04 e05 60a", OPT_PARSE_ONLY},
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
        {new SOCRejectConnection("reason msg"), "1059|reason msg", "SOCRejectConnection:reason msg"},
        {new SOCRejectOffer("ga", 2), "1037|ga,2", "SOCRejectOffer:game=ga|playerNumber=2"},
        {new SOCRemovePiece("ga", 2, SOCPlayingPiece.SHIP, 0xe04), "1094|ga,2,3,3588", "SOCRemovePiece:game=ga|pn=2|pieceType=3|coord=3588"},
        {
            new SOCReportRobbery("ga", 2, 3, SOCResourceConstants.UNKNOWN, true, 1, 0, 0),
            "1102|ga,2,3,R,6,1,T",
            "SOCReportRobbery:game=ga|perp=2|victim=3|resType=6|amount=1|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", 2, 3, SOCResourceConstants.WHEAT, true, 1, 0, 0),
            "1102|ga,2,3,R,4,1,T",
            "SOCReportRobbery:game=ga|perp=2|victim=3|resType=4|amount=1|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", -1, -1, SOCResourceConstants.WHEAT, true, 1, 0, 0),
                // pn -1 is only for future scenario/expansion use
            "1102|ga,-1,-1,R,4,1,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=-1|resType=4|amount=1|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", -1, -1, SOCResourceConstants.WHEAT, true, 1, 0, 4),
                // pn -1 is only for future scenario/expansion use
            "1102|ga,-1,-1,R,4,1,T,0,4",
            "SOCReportRobbery:game=ga|perp=-1|victim=-1|resType=4|amount=1|isGainLose=true|extraValue=4"
        },
        {
            new SOCReportRobbery("ga", 2, 3, SOCResourceConstants.WHEAT, false, 5, 7, 0),
            "1102|ga,2,3,R,4,5,F,7",
            "SOCReportRobbery:game=ga|perp=2|victim=3|resType=4|amount=5|isGainLose=false|victimAmount=7"
        },
        {
            new SOCReportRobbery("ga", 2, 3, SOCResourceConstants.WHEAT, false, 5, 7, 4),
            "1102|ga,2,3,R,4,5,F,7,4",
            "SOCReportRobbery:game=ga|perp=2|victim=3|resType=4|amount=5|isGainLose=false|victimAmount=7|extraValue=4"
        },
        {
            // scenario SC_PIRI: to announce player won vs pirate attack, "rob" 0 unknown resources
            new SOCReportRobbery("ga", -1, 3, SOCResourceConstants.UNKNOWN, true, 0, 0, 0),
            "1102|ga,-1,3,R,6,0,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resType=6|amount=0|isGainLose=true"
        },
        {
            // scenario SC_PIRI: to announce pirate attack result is tied, "rob" 0 resources of type 0
            new SOCReportRobbery("ga", -1, 3, 0, true, 0, 0, 0),
            "1102|ga,-1,3,R,0,0,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resType=0|amount=0|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", -1, 3, new SOCResourceSet(7, 0, 0, 6, 0, 0), 0),  // clay != 0 to test that part of parsing
            "1102|ga,-1,3,S,1,7,4,6,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resSet=clay=7|ore=0|sheep=0|wheat=6|wood=0|unknown=0|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", -1, 3, new SOCResourceSet(7, 0, 0, 6, 0, 0), 4),  // extraValue field
            "1102|ga,-1,3,S,1,7,4,6,T,0,4",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resSet=clay=7|ore=0|sheep=0|wheat=6|wood=0|unknown=0|isGainLose=true|extraValue=4"
        },
        {
            new SOCReportRobbery("ga", -1, 3, new SOCResourceSet(0, 8, 0, 6, 7, 0), 0),  // 3 resource types
            "1102|ga,-1,3,S,2,8,4,6,5,7,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resSet=clay=0|ore=8|sheep=0|wheat=6|wood=7|unknown=0|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", -1, 3, new SOCResourceSet(0, 7, 0, 0, 0, 0), 0),  // 1 resource type
            "1102|ga,-1,3,S,2,7,T",
            "SOCReportRobbery:game=ga|perp=-1|victim=3|resSet=clay=0|ore=7|sheep=0|wheat=0|wood=0|unknown=0|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", 3, 2, PEType.SCENARIO_CLOTH_COUNT, true, 1, 0, 0),
            "1102|ga,3,2,E,106,1,T",
            "SOCReportRobbery:game=ga|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=1|isGainLose=true"
        },
        {
            new SOCReportRobbery("ga", 3, 2, PEType.SCENARIO_CLOTH_COUNT, true, 1, 0, 4),
            "1102|ga,3,2,E,106,1,T,0,4",
            "SOCReportRobbery:game=ga|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=1|isGainLose=true|extraValue=4"
        },
        {
            new SOCReportRobbery("ga", 3, 2, PEType.SCENARIO_CLOTH_COUNT, false, 5, 7, 0),
            "1102|ga,3,2,E,106,5,F,7",
            "SOCReportRobbery:game=ga|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=5|isGainLose=false|victimAmount=7"
        },
        {
            new SOCReportRobbery("ga", 3, 2, PEType.SCENARIO_CLOTH_COUNT, false, 5, 7, 4),
            "1102|ga,3,2,E,106,5,F,7,4",
            "SOCReportRobbery:game=ga|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=5|isGainLose=false|victimAmount=7|extraValue=4"
        },
        {new SOCResetBoardAuth("ga", 3, 2), "1074|ga,3,2", "SOCResetBoardAuth:game=ga|rejoinPN=3|requestingPN=2"},
            // parse from old field names, which are in some STACSettlers soclog files:
        {new SOCResetBoardAuth("ga", 3, 2), "1074|ga,3,2", "SOCResetBoardAuth:game=ga|param1=3|param2=2", OPT_PARSE_ONLY},
        {new SOCResetBoardReject("ga"), "1077|ga", "SOCResetBoardReject:game=ga"},
        {new SOCResetBoardRequest("ga"), "1073|ga", "SOCResetBoardRequest:game=ga"},
        {new SOCResetBoardVote("ga", 3, true), "1076|ga,3,1", "SOCResetBoardVote:game=ga|pn=3|vote=1"},
            // parse from old field names, which are in some STACSettlers soclog files:
        {new SOCResetBoardVote("ga", 3, true), "1076|ga,3,1", "SOCResetBoardVote:game=ga|param1=3|param2=1", OPT_PARSE_ONLY},
        {new SOCResetBoardVoteRequest("ga", 3), "1075|ga,3", "SOCResetBoardVoteRequest:game=ga|param=3"},
        {new SOCResourceCount("ga", 3, 11), "1063|ga,3,11", "SOCResourceCount:game=ga|playerNumber=3|count=11"},
        {new SOCRevealFogHex("ga", 3340, SOCBoard.WOOD_HEX, 12), "10001|ga,3340,5,12", "SOCRevealFogHex:game=ga|hexCoord=3340|hexType=5|diceNum=12"},
        {new SOCRobotDismiss("ga"), "1056|ga", "SOCRobotDismiss:game=ga"},
        {new SOCRollDice("ga"), "1031|ga", "SOCRollDice:game=ga"},
        {new SOCRollDicePrompt("ga", 3), "1072|ga,3", "SOCRollDicePrompt:game=ga|playerNumber=3"},
        // can ignore unused SOCRollDiceRequest
        {new SOCScenarioInfo(new ArrayList<>(SCENS_KEY_LIST), false), "1101|[|KEY1|KEY2", "SOCScenarioInfo:p=[|p=KEY1|p=KEY2", OPT_SKIP_PARSE},
        {new SOCScenarioInfo(new ArrayList<>(SCENS_KEY_LIST), true), "1101|[|KEY1|KEY2|?", "SOCScenarioInfo:p=[|p=KEY1|p=KEY2|p=?", OPT_SKIP_PARSE},
        {new SOCScenarioInfo("KEY3", true), "1101|KEY3|0|-2", "SOCScenarioInfo:key=KEY3|minVers=0|lastModVers=MARKER_KEY_UNKNOWN", OPT_SKIP_PARSE},
        {
            new SOCScenarioInfo("KEY4", false),
            "1101|[|KEY4",
            "SOCScenarioInfo:p=[|p=KEY4", OPT_SKIP_PARSE,
            OPT_IGNORE_OBJ_FIELDS,
            new HashSet<String>(Arrays.asList("scKey", "noMoreScens"))
        },
        {
            new SOCScenarioInfo(SOCScenario.getScenario(SOCScenario.K_SC_NSHO), "new shores", null),  // has no long desc
            "1101|SC_NSHO|2000|2000|_SC_SEAC=t,SBL=t,VP=t13|new shores",
            "SOCScenarioInfo:key=SC_NSHO|minVers=2000|lastModVers=2000|opts=_SC_SEAC=t,SBL=t,VP=t13|title=new shores",
            OPT_SKIP_PARSE,
            OPT_IGNORE_OBJ_FIELDS,
            new HashSet<String>(Arrays.asList("scKey", "noMoreScens"))
        },
        {
            new SOCScenarioInfo(SOCScenario.getScenario(SOCScenario.K_SC_4ISL), "4 islands", "long desc, 4 islands"),
            "1101|SC_4ISL|2000|2000|_SC_SEAC=t,SBL=t,VP=t12|4 islands|long desc, 4 islands",
            "SOCScenarioInfo:key=SC_4ISL|minVers=2000|lastModVers=2000|opts=_SC_SEAC=t,SBL=t,VP=t12|title=4 islands|desc=long desc, 4 islands",
            OPT_SKIP_PARSE,
            OPT_IGNORE_OBJ_FIELDS,
            new HashSet<String>(Arrays.asList("scKey", "noMoreScens"))
        },
        {new SOCServerPing(42), "9999|42", "SOCServerPing:sleepTime=42"},
        {new SOCSetPlayedDevCard("ga", 2, false), "1048|ga,2,false", "SOCSetPlayedDevCard:game=ga|playerNumber=2|playedDevCard=false"},
        {new SOCSetSeatLock("ga", 2, SeatLockState.LOCKED), "1068|ga,2,true", "SOCSetSeatLock:game=ga|playerNumber=2|state=LOCKED"},
        {
            new SOCSetSeatLock("ga", new SeatLockState[]{SeatLockState.UNLOCKED, SeatLockState.CLEAR_ON_RESET, SeatLockState.LOCKED, SeatLockState.UNLOCKED}),
            "1068|ga,false,clear,true,false",
            "SOCSetSeatLock:game=ga|states=UNLOCKED,CLEAR_ON_RESET,LOCKED,UNLOCKED"
        },
        {
            new SOCSetSpecialItem("ga", SOCSetSpecialItem.OP_CLEAR_PICK, "_SC_WOND", 2, 0, 3),
            "1099|ga,6,_SC_WOND,2,0,3,-1,0,\t",
            "SOCSetSpecialItem:game=ga|op=CLEAR_PICK|typeKey=_SC_WOND|gi=2|pi=0|pn=3|co=-1|lv=0|sv null"
        },
        {
            new SOCSetSpecialItem("ga", SOCSetSpecialItem.OP_SET, "_SC_WOND", 2, 0, 3, -1, 2, "w2"),
            "1099|ga,1,_SC_WOND,2,0,3,-1,2,w2",
            "SOCSetSpecialItem:game=ga|op=SET|typeKey=_SC_WOND|gi=2|pi=0|pn=3|co=-1|lv=2|sv=w2"
        },
        {new SOCSetTurn("ga", 2), "1055|ga,2", "SOCSetTurn:game=ga|param=2"},
        {new SOCSimpleAction("ga", 3, 1, 22), "1090|ga,3,1,22,0", "SOCSimpleAction:game=ga|pn=3|actType=1|v1=22|v2=0"},
        {new SOCSimpleRequest("ga", 2, 1001, 2562), "1089|ga,2,1001,2562,0", "SOCSimpleRequest:game=ga|pn=2|reqType=1001|v1=2562|v2=0"},
        {new SOCSimpleRequest("ga", 2, 1001, 2562, 7), "1089|ga,2,1001,2562,7", "SOCSimpleRequest:game=ga|pn=2|reqType=1001|v1=2562|v2=7"},
        {new SOCSitDown("ga", "testp2", 2, false), "1012|ga,testp2,2,false", "SOCSitDown:game=ga|nickname=testp2|playerNumber=2|robotFlag=false"},
        {new SOCStartGame("ga", SOCGame.START1A), "1018|ga,5", "SOCStartGame:game=ga|gameState=5"},
        {new SOCStatusMessage("simple ok status"), "1069|simple ok status", "SOCStatusMessage:status=simple ok status"},
        {new SOCStatusMessage(11, "nonzero status text"), "1069|11,nonzero status text", "SOCStatusMessage:sv=11|status=nonzero status text"},
        {new SOCSVPTextMessage("ga", 3, 2, "settling a new island", true), "1097|ga,3,2,settling a new island", "SOCSVPTextMessage:game=ga|pn=3|svp=2|desc=settling a new island"},
        {new SOCTimingPing("ga"), "1088|ga", "SOCTimingPing:game=ga"},
        {new SOCTurn("ga", 3, 0), "1026|ga,3", "SOCTurn:game=ga|playerNumber=3"},
        {new SOCTurn("ga", 3, SOCGame.ROLL_OR_CARD), "1026|ga,3,15", "SOCTurn:game=ga|playerNumber=3|gameState=15"},
        {
            new SOCUpdateRobotParams(new soc.util.SOCRobotParameters(120, 35, 0.13f, 1.0f, 1.0f, 3.0f, 1.0f, 0, 1)),
            "1071|120,35,0.13,1.0,1.0,3.0,1.0,0,1",
            "SOCUpdateRobotParams:mgl=120|me=35|ebf=0.13|af=1.0|laf=1.0|dcm=3.0|tm=1.0|st=0|tf=1"
        },
        {
            new SOCVersion(2450, "2.4.50", "JM20200801", ";6pl;sb;", "en_US"),
            "9998|2450,2.4.50,JM20200801,;6pl;sb;,en_US",
            "SOCVersion:2450|str=2.4.50|verBuild=JM20200801|feats=;6pl;sb;|cliLocale=en_US"
        },
        {
            new SOCVersion(1118, "1.1.18", "OV20130402", null, null),
            "9998|1118,1.1.18,OV20130402,\t",
            "SOCVersion:1118|str=1.1.18|verBuild=OV20130402|feats=(null)|cliLocale=(null)"
        },
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

    /**
     * Test various forms of messages which need more detailed checks than
     * {@link #testRoundTripParsing()} runs on {@link #SCENS_KEY_LIST}:
     *<UL>
     * <LI> {@link SOCGameOptionGetInfos} with {@link SOCGameOptionGetInfos#OPTKEY_GET_ANY_CHANGES OPTKEY_GET_ANY_CHANGES}
     *      token / {@link SOCGameOptionGetInfos#hasTokenGetAnyChanges hasTokenGetAnyChanges} flag
     *</UL>
     */
    @Test
    public void testMiscMessageForms()
        throws InputMismatchException, ParseException
    {
        // SOCGameOptionGetInfos with OPTKEY_GET_ANY_CHANGES token
        {
            // without i18n flag:
            String EXPECTED_CMD = "1081|SC,PLP,?CHANGES",
                EXPECTED_STR = "SOCGameOptionGetInfos:options=SC,PLP,?CHANGES";
            SOCGameOptionGetInfos msg = new SOCGameOptionGetInfos(Arrays.asList
                ("SC", "PLP", SOCGameOptionGetInfos.OPTKEY_GET_ANY_CHANGES), false, false);
            assertEquals(EXPECTED_CMD, msg.toCmd());
            assertEquals(EXPECTED_STR, msg.toString());

            SOCMessage fromCmd = SOCMessage.toMsg(EXPECTED_CMD);
            assertTrue(fromCmd instanceof SOCGameOptionGetInfos);
            assertTrue(((SOCGameOptionGetInfos) fromCmd).hasTokenGetAnyChanges);
            assertFalse(((SOCGameOptionGetInfos) fromCmd).hasTokenGetI18nDescs);
            assertFalse(((SOCGameOptionGetInfos) fromCmd).hasOnlyTokenI18n);
            assertEquals(Arrays.asList("SC", "PLP"), ((SOCGameOptionGetInfos) fromCmd).optionKeys);

            SOCMessage fromStr = SOCMessage.parseMsgStr(EXPECTED_STR);
            assertTrue(fromStr instanceof SOCGameOptionGetInfos);
            // fromStr's field contents should be identical to fromCmd
            StringBuilder ret = new StringBuilder();
            compareMsgObjFields(SOCGameOptionGetInfos.class, fromCmd, fromStr, ret, null);
            if (ret.length() > 0)
                fail("SOCGameOptionGetInfos: fromStr field mismatch vs fromCmd: " + ret);

            // same, with i18n flag:

            EXPECTED_CMD = "1081|SC,PLP,?CHANGES,?I18N";
            EXPECTED_STR = "SOCGameOptionGetInfos:options=SC,PLP,?CHANGES,?I18N";
            msg = new SOCGameOptionGetInfos
                (Arrays.asList("SC", "PLP", SOCGameOptionGetInfos.OPTKEY_GET_ANY_CHANGES), true, false);
            assertEquals(EXPECTED_CMD, msg.toCmd());
            assertEquals(EXPECTED_STR, msg.toString());

            fromCmd = SOCMessage.toMsg(EXPECTED_CMD);
            assertTrue(fromCmd instanceof SOCGameOptionGetInfos);
            assertTrue(((SOCGameOptionGetInfos) fromCmd).hasTokenGetAnyChanges);
            assertTrue(((SOCGameOptionGetInfos) fromCmd).hasTokenGetI18nDescs);
            assertFalse(((SOCGameOptionGetInfos) fromCmd).hasOnlyTokenI18n);
            assertEquals(Arrays.asList("SC", "PLP"), ((SOCGameOptionGetInfos) fromCmd).optionKeys);

            fromStr = SOCMessage.parseMsgStr(EXPECTED_STR);
            assertTrue(fromStr instanceof SOCGameOptionGetInfos);
            // fromStr's field contents should be identical to fromCmd
            compareMsgObjFields(SOCGameOptionGetInfos.class, fromCmd, fromStr, ret, null);
            if (ret.length() > 0)
                fail("SOCGameOptionGetInfos: fromStr field mismatch vs fromCmd: " + ret);

            // other forms shouldn't set hasTokenGetAnyChanges:

            fromCmd = SOCMessage.toMsg("1081|SC,PLP,?I18N");
            assertTrue(fromCmd instanceof SOCGameOptionGetInfos);
            assertFalse(((SOCGameOptionGetInfos) fromCmd).hasTokenGetAnyChanges);

            fromStr = SOCMessage.parseMsgStr("SOCGameOptionGetInfos:options=SC,PLP,?I18N");
            assertTrue(fromStr instanceof SOCGameOptionGetInfos);
            assertFalse(((SOCGameOptionGetInfos) fromStr).hasTokenGetAnyChanges);
        }
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
