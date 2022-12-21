/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2022 Jeremy D Monin <jeremy@nand.net>
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
package soc.message;

import java.util.StringTokenizer;

import soc.game.GameAction;  // for javadocs only
import soc.game.ResourceSet;
import soc.game.SOCGame;  // for javadocs only
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.game.GameAction.ActionType;  // for javadocs only

/**
 * {@link GameAction} game data message sent from server to client joining a game,
 * towards the end of the sequence documented at {@link SOCGameMembers}.
 * Info on the most recent action is needed to properly handle some types of undo.
 * If this message isn't sent, joining client should assume {@link SOCGame#getLastAction()} is null.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.7.00
 */
public class SOCSetLastAction extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2700L;  // no structural change yet; created v2.7.00

    /** Minimum version (2.7.00) of server/client which send and recognize {@link SOCSetLastAction}. */
    public static final int VERSION_FOR_SETLASTACTION = 2700;

    /** Name of the game */
    private final String game;

    /** Type of game action, from {@link ActionType#value}. */
    private final int actTypeValue;

    /** {@link ActionType}-specific int parameter value, or 0. */
    private final int param1, param2, param3;

    /** {@link ActionType}-specific resource set parameter value, or null. */
    private final ResourceSet rset1, rset2;

    /**
     * Create a {@link SOCSetLastAction} message with optional int parameters.
     *
     * @param gn  name of the game
     * @param aType {@link ActionType#value} of this action, or 0 if null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @see #SOCSetLastAction(String, int, int, int, int, ResourceSet, ResourceSet)
     */
    public SOCSetLastAction(String gn, int aType, final int p1, final int p2, final int p3)
    {
        this(gn, aType, p1, p2, p3, null, null);
    }

    /**
     * Create a {@link SOCSetLastAction} message with optional int and resource parameters.
     *
     * @param gn  name of the game
     * @param aType {@link ActionType#value} of this action, or 0 if null
     * @param p1  First action-specific parameter, or 0
     * @param p2  Second action-specific parameter, or 0
     * @param p3  Third action-specific parameter, or 0
     * @param rs1  Resource set 1, or null
     * @param rs2  Resource set 2, or null
     * @see #SOCSetLastAction(String, int, int, int, int)
     */
    public SOCSetLastAction
        (String gn, int aType, final int p1, final int p2, final int p3, final ResourceSet rs1, final ResourceSet rs2)
    {
        messageType = SETLASTACTION;
        game = gn;
        actTypeValue = aType;
        param1 = p1;
        param2 = p2;
        param3 = p3;
        rset1 = rs1;
        rset2 = rs2;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return game;
    }

    /**
     * @return the {@link ActionType#value} of this action, or 0 if null
     */
    public int getActionTypeValue()
    {
        return actTypeValue;
    }

    /**
     * @return the first optional int parameter, or 0
     */
    public int getParam1()
    {
        return param1;
    }

    /**
     * @return the second optional int parameter, or 0
     */
    public int getParam2()
    {
        return param2;
    }

    /**
     * @return the third optional int parameter, or 0
     */
    public int getParam3()
    {
        return param3;
    }

    /**
     * Get the optional first resource set parameter.
     * @return  Resource set, or null
     */
    public ResourceSet getRS1()
    {
        return rset1;
    }

    /**
     * Get the optional second resource set parameter.
     * @return  Resource set, or null
     */
    public ResourceSet getRS2()
    {
        return rset2;
    }

    /**
     * Command string:
     *<BR>
     * SETLASTACTION sep game sep2 actTypeValue sep2 param1 sep2 param2 sep2 param3
     * [ "R1" clay ore sheep wheat wood ][ "R2" clay ore sheep wheat wood ]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder ret = new StringBuilder
            (SETLASTACTION + sep + game + sep2 + actTypeValue + sep2 + param1 + sep2 + param2 + sep2 + param3);
        if (rset1 != null)
        {
            ret.append(sep2_char).append("R1");
            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
                ret.append(sep2_char).append(rset1.getAmount(rtype));
        }
        if (rset2 != null)
        {
            ret.append(sep2_char).append("R2");
            for (int rtype = SOCResourceConstants.CLAY; rtype <= SOCResourceConstants.WOOD; ++rtype)
                ret.append(sep2_char).append(rset2.getAmount(rtype));
        }

        return ret.toString();
    }

    /**
     * Parse the command string into a {@code SOCSetLastAction} message.
     * Ignores unknown params and tokens at end, for possible future expansion.
     *
     * @param s   the String to parse
     * @return    a SETLASTACTION message, or null if the data is garbled
     */
    public static SOCSetLastAction parseDataStr(String s)
    {
        String gaName;
        int aType, p1, p2, p3;
        ResourceSet rs1 = null, rs2 = null;

        StringTokenizer st = new StringTokenizer(s, sep2);

        try
        {
            gaName = st.nextToken();
            aType = Integer.parseInt(st.nextToken());
            p1 = Integer.parseInt(st.nextToken());
            p2 = Integer.parseInt(st.nextToken());
            p3 = Integer.parseInt(st.nextToken());
            while (st.hasMoreTokens())
            {
                String tok = st.nextToken();
                if (tok.equals("R1"))
                {
                    int cl, or, sh, wh, wo;
                    cl = Integer.parseInt(st.nextToken());
                    or = Integer.parseInt(st.nextToken());
                    sh = Integer.parseInt(st.nextToken());
                    wh = Integer.parseInt(st.nextToken());
                    wo = Integer.parseInt(st.nextToken());
                    rs1 = new SOCResourceSet(cl, or, sh, wh, wo, 0);
                }
                else if (tok.equals("R2"))
                {
                    int cl, or, sh, wh, wo;
                    cl = Integer.parseInt(st.nextToken());
                    or = Integer.parseInt(st.nextToken());
                    sh = Integer.parseInt(st.nextToken());
                    wh = Integer.parseInt(st.nextToken());
                    wo = Integer.parseInt(st.nextToken());
                    rs2 = new SOCResourceSet(cl, or, sh, wh, wo, 0);
                }
            }

            return new SOCSetLastAction(gaName, aType, p1, p2, p3, rs1, rs2);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Minimum version where this message type is used.
     * @return Version number, 2700 for JSettlers 2.7.00.
     * @see #VERSION_FOR_SETLASTACTION
     */
    @Override
    public int getMinimumVersion() { return VERSION_FOR_SETLASTACTION; /* == 2700 */ }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a comma-delimited list for {@link SOCMessage#parseMsgStr(String)}.
     * Regularizes rs1 and rs2. Converts {@link ActionType} identifier to its int value.
     * @param messageStrParams  Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters without attribute names, or {@code null} if params are malformed
     */
    public static String stripAttribNames(String messageStrParams)
    {
        // change rs1= rs2= etc before strip attrib names
        messageStrParams = messageStrParams.replace("|rs1=[", "|R1|").replace("|rs2=[", "|R2|")
            .replace("|unknown=0]", "");

        // look up and change actType=BUILD_PIECE to 40;
        // string manip is slightly less annoying than regex find + lookup + replace
        int startIdx = messageStrParams.indexOf("|actType=") + 9;
        if (startIdx <= 9)
            return null;
        int endIdx = messageStrParams.indexOf('|', startIdx);
        if ((endIdx == -1) || (endIdx - startIdx <= 1))
            return null;
        {
            String actTypeIdent = messageStrParams.substring(startIdx, endIdx);  // "BUILD_PIECE"
            if (! Character.isDigit(actTypeIdent.charAt(0)))  // already OK if seeing "actType=40"
            {
                // "...|actType=BUILD_PIECE|..." -> "...|40|..."
                ActionType act = ActionType.valueOf(actTypeIdent);
                if (act == null)
                    act = ActionType.UNKNOWN;
                int atv = act.value;
                messageStrParams = messageStrParams.substring(0, startIdx - 8) + atv + messageStrParams.substring(endIdx);
            }
        }

        return SOCMessage.stripAttribNames(messageStrParams);
    }

    /**
     * Build and return a human-readable form of the message.
     * Converts {@code actType} to its enum identifier using {@link ActionType#valueOf(int)} if possible.
     *<P>
     * Examples:
     *<UL>
     * <LI> {@code SOCSetLastAction:game=ga|actType=BUILD_PIECE|p1=1|p2=3337|p3=3}
     * <LI> {@code SOCSetLastAction:game=ga|actType=MOVE_PIECE|p1=1|p2=3337|p3=2059|rs1=[clay=1|ore=0|sheep=0|wheat=0|wood=4|unknown=0]}
     * <LI> {@code SOCSetLastAction:game=ga|actType=BUILD_PIECE|p1=1|p2=3337|p3=3|rs1=[clay=1|ore=0|sheep=0|wheat=0|wood=4|unknown=0]|rs2=[clay=0|ore=2|sheep=0|wheat=2|wood=0|unknown=0]}
     * <LI> {@code SOCSetLastAction:game=ga|actType=999|p1=1|p2=0|p3=0}
     *</UL>
     * Note: {@link SOCResourceSet#toString()} includes the set's unknown amount, which is always 0 here
     *     since that field isn't sent over the network.
     * @return a human-readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder
            ("SOCSetLastAction:game=" + game + "|actType=");
        ActionType at = ActionType.valueOf(actTypeValue);
        if (at != null)
            sb.append(at);
        else
            sb.append(actTypeValue);
        if ((param1 != 0) || (param2 != 0) || (param3 != 0))
        {
            sb.append("|p1=").append(param1);
            if ((param2 != 0) || (param3 != 0))
            {
                sb.append("|p2=").append(param2);
                if (param3 != 0)
                    sb.append("|p3=").append(param3);
            }
        }

        // We're able to put [brackets] around rsets to help delimit them
        // because there's no backwards-compat to worry about yet,
        // unlike older rset-using types like SOCTradeOffer which always had "give=clay=..."
        if (rset1 != null)
            sb.append("|rs1=[").append(rset1).append(']');
        if (rset2 != null)
            sb.append("|rs2=[").append(rset2).append(']');

        return sb.toString();
    }

}
