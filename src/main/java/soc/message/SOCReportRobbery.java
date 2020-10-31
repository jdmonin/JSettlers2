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
package soc.message;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import soc.game.SOCResourceConstants;  // for javadocs only
import soc.message.SOCPlayerElement.PEType;


/**
 * This message from the server gives info about a robbery's perpetrator, victim, and what was stolen.
 * Its audience can be the perpetrator or victim (specifics about what was stolen),
 * or the rest of the game (unknown resource), or announced to the entire game (cloth in Cloth Trade scenario).
 *<P>
 * Clients older than v2.4.50 ({@link #MIN_VERSION}) are instead sent a sequence of
 * {@link SOCPlayerElement} and {@link SOCGameServerText} messages.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.4.50
 */
public class SOCReportRobbery extends SOCMessage
    implements SOCMessageForGame
{
    private static final long serialVersionUID = 2450L;  // last structural change v2.4.50

    /**
     * Version number (2.4.50) where this message type was introduced.
     */
    public static final int MIN_VERSION = 2450;

    /**
     * Name of the game
     */
    public final String gaName;

    /** Victim player number, or -1 if none (for future use by scenarios/expansions) */
    public final int victimPN;

    /** Perpetrator player number, or -1 if none (for future use by scenarios/expansions) */
    public final int perpPN;

    /**
     * Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     * or {@link SOCResourceConstants#UNKNOWN}.
     * Ignored if <tt>{@link #peType} != null</tt>.
     */
    public final int resType;

    /**
     * PlayerElement type such as {@link PEType#SCENARIO_CLOTH_COUNT},
     * or {@code null} if a resource like sheep is being stolen (use {@link #resType} instead).
     */
    public final PEType peType;

    /**
     * True if {@link #amount} is gained by {@link #perpPN} and lost by {@link #victimPN},
     * false if {@link #amount} and {@link #victimAmount} are their new total amounts to set.
     */
    public final boolean isGainLose;

    /**
     * Amount stolen if {@link #isGainLose}, or {@link #perpPN}'s new total amount
     * (see {@link #victimAmount}).
     */
    public final int amount;

    /**
     * {@link #victimPN}'s new total amount if not {@link #isGainLose} (see {@link #amount});
     * unsent (0) if {@link #isGainLose}.
     */
    public final int victimAmount;

    /**
     * Create a {@link SOCReportRobbery} message about a resource (not cloth or some other {@link PEType}).
     *
     * @param gaName  name of the game
     * @param perpPN  Perpetrator's player number, or -1 if none (for future use by scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param resType  Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     *     or {@link SOCResourceConstants#UNKNOWN}
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @throws IllegalArgumentException if {@code amount}, {@code victimAmount}, or {@code resType} &lt; 0,
     *     or {@code isGainLose} but {@code victimAmount} != 0
     * @see #SOCReportRobbery(String, int, int, PEType, boolean, int, int)
     */
    public SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final int resType,
         final boolean isGainLose, final int amount, final int victimAmount)
        throws IllegalArgumentException
    {
        this(gaName, perpPN, victimPN, resType, null, isGainLose, amount, victimAmount);
    }

    /**
     * Create a {@link SOCReportRobbery} message about cloth or some other player element (not a resource).
     *
     * @param gaName  name of the game
     * @param perpPN  Perpetrator's player number, or -1 if none (for future use by scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param peType  the type of element, like {@link PEType#SCENARIO_CLOTH_COUNT}
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @throws IllegalArgumentException if {@code peType} null, or {@code amount} or {@code victimAmount} &lt; 0,
     *     or {@code isGainLose} but {@code victimAmount} != 0
     * @see #SOCReportRobbery(String, int, int, int, boolean, int, int)
     */
    public SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount)
        throws IllegalArgumentException
    {
        this(gaName, perpPN, victimPN, -1, peType, isGainLose, amount, victimAmount);
    }

    private SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final int resType, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount)
        throws IllegalArgumentException
    {
        if ((peType == null) && (resType < 0))
            throw new IllegalArgumentException("peType/resType");
        if ((amount < 0) || (victimAmount < 0))
            throw new IllegalArgumentException("amounts");
        if (isGainLose && (victimAmount != 0))
            throw new IllegalArgumentException("victimAmount but isGainLose");

        messageType = REPORTROBBERY;
        this.gaName = gaName;
        this.perpPN = perpPN;
        this.victimPN = victimPN;
        this.resType = resType;
        this.peType = peType;
        this.isGainLose = isGainLose;
        this.amount = amount;
        this.victimAmount = victimAmount;
    }

    /**
     * @return the name of the game
     */
    public String getGame()
    {
        return gaName;
    }

    /**
     * Minimum version where this message type is used.
     * Introduced in v2.4.50 ({@link #MIN_VERSION}).
     * @return Version number, 2450 for JSettlers 2.4.50
     */
    @Override
    public final int getMinimumVersion() { return MIN_VERSION; }

    /**
     * {@link #REPORTROBBERY} sep gameName sep2 perpPN sep2 victimPN sep2
     * (('R' sep2 resType) or ('E' sep2 {@link PEType#getValue() peType.getValue()}))
     * sep2 (isGainLose: 'T' or 'F') sep2 amount [sep2 victimAmount]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder sb = new StringBuilder
            (REPORTROBBERY + sep + gaName + sep2 + perpPN + sep2 + victimPN + sep2);
        if (peType != null)
            sb.append('E').append(sep2).append(peType.getValue());
        else
            sb.append('R').append(sep2).append(resType);
        sb.append(sep2).append(isGainLose ? 'T' : 'F')
          .append(sep2).append(amount);
        if (victimAmount != 0)
            sb.append(sep2).append(victimAmount);

        return sb.toString();
    }

    /**
     * Parse the command String into a {@link SOCReportRobbery} message.
     *
     * @param cmd   the String to parse, from {@link #toCmd()}
     * @return    a SOCReportRobbery message, or {@code null} if parsing errors
     */
    public static SOCReportRobbery parseDataStr(final String cmd)
    {
        StringTokenizer st = new StringTokenizer(cmd, sep2);

        try
        {
            final String ga;
            final int ppn, vpn, rtype;
            final PEType petype;
            final boolean gainLose;
            final int amt, victimAmt;

            ga = st.nextToken();
            ppn = Integer.parseInt(st.nextToken());
            vpn = Integer.parseInt(st.nextToken());
            String s = st.nextToken();
            if (s.length() != 1)
                return null;  // expected type char ('R' etc)
            char ch = s.charAt(0);
            int typeval = Integer.parseInt(st.nextToken());
            if (ch == 'R')
            {
                rtype = typeval;
                petype = null;
            }
            else if (ch == 'E')
            {
                rtype = -1;
                petype = PEType.valueOf(typeval);
                if (petype == null)
                    return null;
            }
            else
                return null;  // expected 'R' or 'E'

            s = st.nextToken();
            if (s.length() != 1)
                return null;  // expected boolean char
            ch = s.charAt(0);
            if (ch == 'T')
                gainLose = true;
            else if (ch == 'F')
                gainLose = false;
            else
                return null;

            amt = Integer.parseInt(st.nextToken());
            victimAmt = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 0;

            return new SOCReportRobbery(ga, ppn, vpn, rtype, petype, gainLose, amt, victimAmt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list for {@link SOCMessage#parseMsgStr(String)}.
     * Handles elemNum=value pairs, undoes mapping of action constant integers -> strings ({@code "GAIN"} etc).
     *
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters to finish parsing into a SOCMessage, or {@code null} if malformed
     */
    public static String stripAttribNames(final String messageStrParams)
    {
        final boolean isResNotPE = messageStrParams.contains("|resType=");
        List<String> pieces = SOCMessage.stripAttribsToList(messageStrParams);

        if (pieces.size() < 6)
            return null;
        if (! (pieces instanceof ArrayList<?>))
            pieces = new ArrayList<>(pieces);  // must support add method

        pieces.add(3, isResNotPE ? "R" : "E");
        if (! isResNotPE)
        {
            PEType pe = PEType.valueOf(pieces.get(4));
            int v = (pe != null) ? pe.getValue() : 0;
            pieces.set(4, Integer.toString(v));
        }
        pieces.set(5, (pieces.get(5).equals("true") ? "T" : "F"));

        StringBuilder ret = new StringBuilder(pieces.get(0));
        final int L = pieces.size();
        for (int i = 1; i < L; ++i)
            ret.append(sep2_char).append(pieces.get(i));

        return ret.toString();
    }

    /**
     * @return a human readable form of the message
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SOCReportRobbery:game=" + gaName);
        sb.append("|perp=").append(perpPN)
          .append("|victim=").append(victimPN);
        if (peType != null)
            sb.append("|peType=").append(peType);
        else
            sb.append("|resType=").append(resType);
        sb.append("|isGainLose=").append(isGainLose)
          .append("|amount=").append(amount);
        if ((victimAmount != 0) || ! isGainLose)
            sb.append("|victimAmount=").append(victimAmount);

        return sb.toString();
    }

}
