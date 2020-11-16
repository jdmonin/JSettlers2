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
import soc.game.SOCResourceSet;
import soc.message.SOCPlayerElement.PEType;


/**
 * This message from the server gives info about a robbery's perpetrator, victim, and what was stolen.
 * Its audience can be the perpetrator or victim (specifics about what was stolen),
 * or the rest of the game (unknown resource), or announced to the entire game (cloth in Cloth Trade scenario).
 *<P>
 * Clients older than v2.4.50 ({@link #MIN_VERSION}) are instead sent a sequence of
 * {@link SOCPlayerElement} and {@link SOCGameServerText} messages.
 *
 *<H4>Scenarios and Expansions:</H4>
 *
 * Along with robbery in the standard rules, this message is used for:
 *
 *<UL>
 * <LI> Cloth Trade scenario ({@code SC_CLVI}): <BR>
 *   Players may rob cloth from opponents.
 *   Announced with {@link #peType} = {@link PEType#SCENARIO_CLOTH_COUNT}
 * <LI> Pirate Islands scenario ({@code SC_PIRI}): <BR>
 *   Announces the results of a pirate fleet attack on a player.
 *   <UL>
 *     <LI> {@link #perpPN} = -1
 *     <LI> {@link #extraValue} = strength of pirate fleet
 *     <LI> If player won: {@link #amount} = 0, {@link #resType} = {@link SOCResourceConstants#UNKNOWN};
 *          server will also announce {@link SOCPlayerElement}({@link PEType#NUM_PICK_GOLD_HEX_RESOURCES})
 *          and send player a
 *          {@link SOCSimpleRequest}({@link SOCSimpleRequest#PROMPT_PICK_RESOURCES PROMPT_PICK_RESOURCES})
 *     <LI> If tied: {@link #amount} = 0, {@link #resType} = 0.<BR>
 *          This is optional; so far no released server version reports ties, since nothing is gained or lost.
 *     <LI> If player lost: {@link #amount} = number of resources lost,
 *          {@link #resType} = {@link SOCResourceConstants#UNKNOWN};
 *          server will also send to player with {@link #resSet} or {@link #resType} = specific resources lost
 *   </UL>
 *   See also {@link SOCSimpleAction}({@link SOCSimpleAction#SC_PIRI_FORT_ATTACK_RESULT SC_PIRI_FORT_ATTACK_RESULT})
 *   used in this scenario to announce fortress attack attempt results.
 *</UL>
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

    /**
     * Perpetrator player number, or -1 if none (used by {@code SC_PIRI} scenario's pirate fleet attack results,
     * future use by other scenarios/expansions)
     */
    public final int perpPN;

    /**
     * Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     * or {@link SOCResourceConstants#UNKNOWN}.
     * Ignored if <tt>{@link #resSet} != null</tt> or <tt>{@link #peType} != null</tt>.
     */
    public final int resType;

    /**
     * Resource set being stolen, or {@code null} if what's being stolen is
     * a single resource type like sheep or unknown (use {@link #resType} instead) or a {@link #peType}.
     * Should not be empty or contain {@link SOCResourceConstants#UNKNOWN} resources.
     */
    public final SOCResourceSet resSet;

    /**
     * PlayerElement type such as {@link PEType#SCENARIO_CLOTH_COUNT},
     * or {@code null} if what's being stolen is a {@link #resSet}
     * or a single resource type like sheep (use {@link #resType} instead).
     */
    public final PEType peType;

    /**
     * True if {@link #amount} is gained by {@link #perpPN} and lost by {@link #victimPN},
     * false if {@link #amount} and {@link #victimAmount} are their new total amounts to set.
     *<P>
     * Always true if <tt>{@link #resSet} != null</tt>,
     * which doesn't use {@code amount} and {@code victimAmount} fields.
     */
    public final boolean isGainLose;

    /**
     * Amount stolen if {@link #isGainLose}, or {@link #perpPN}'s new total amount
     * (see {@link #victimAmount}). Ignored if <tt>{@link #resSet} != null</tt>.
     */
    public final int amount;

    /**
     * {@link #victimPN}'s new total amount if not {@link #isGainLose} (see {@link #amount});
     * unsent (0) if {@link #isGainLose}. Ignored if <tt>{@link #resSet} != null</tt>.
     */
    public final int victimAmount;

    /**
     * Optional information related to the robbery, or 0; for use by scenarios/expansions.
     * Used in scenario {@code SC_PIRI} to send the pirate fleet strength when they attack
     * (see class javadoc).
     */
    public final int extraValue;

    /**
     * Create a {@link SOCReportRobbery} message about one resource type
     * (not a resource set, or cloth or some other {@link PEType}).
     *
     * @param gaName  name of the game
     * @param perpPN  Perpetrator's player number, or -1 if none (for future use by scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param resType  Resource type being stolen, like {@link SOCResourceConstants#SHEEP}
     *     or {@link SOCResourceConstants#UNKNOWN}
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @param extraValue  Optional information related to the robbery, or 0; see {@link #extraValue}
     * @throws IllegalArgumentException if {@code amount}, {@code victimAmount}, or {@code resType} &lt; 0,
     *     or {@code isGainLose} but {@code victimAmount} != 0
     * @see #SOCReportRobbery(String, int, int, SOCResourceSet, int)
     * @see #SOCReportRobbery(String, int, int, PEType, boolean, int, int, int)
     */
    public SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final int resType,
         final boolean isGainLose, final int amount, final int victimAmount, final int extraValue)
        throws IllegalArgumentException
    {
        this(gaName, perpPN, victimPN, resType, null, null, isGainLose, amount, victimAmount, extraValue);
    }

    /**
     * Create a {@link SOCReportRobbery} message about a resource set
     * (not one resource type, or cloth or some other {@link PEType}).
     * Will set {@link #isGainLose} == true.
     *
     * @param gaName  name of the game
     * @param perpPN  Perpetrator's player number, or -1 if none (for future use by scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param resSet  Resource set being stolen; not null or empty.
     *     Should not contain {@link SOCResourceConstants#UNKNOWN}, those will be ignored.
     * @param extraValue  Optional information related to the robbery, or 0; see {@link #extraValue}
     * @throws IllegalArgumentException if {@link SOCResourceSet#isEmpty()}
     * @see #SOCReportRobbery(String, int, int, int, boolean, int, int, int)
     * @see #SOCReportRobbery(String, int, int, PEType, boolean, int, int, int)
     */
    public SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final SOCResourceSet resSet, final int extraValue)
        throws IllegalArgumentException
    {
        this(gaName, perpPN, victimPN, -1, resSet, null, true, 0, 0, extraValue);
    }

    /**
     * Create a {@link SOCReportRobbery} message about cloth or some other player element
     * (not a resource or resource set).
     *
     * @param gaName  name of the game
     * @param perpPN  Perpetrator's player number, or -1 if none (for future use by scenarios/expansions)
     * @param victimPN  Victim's player number, or -1 if none (for future use by scenarios/expansions)
     * @param peType  the type of element, like {@link PEType#SCENARIO_CLOTH_COUNT}
     * @param isGainLose  If true, the amount here is a delta Gained/Lost by players, not a total to Set
     * @param amount  Amount being stolen if {@code isGainLose}, otherwise {@code perpPN}'s new total amount
     * @param victimAmount  {@code victimPN}'s new total amount if not {@code isGainLose}, 0 otherwise
     * @param extraValue  Optional information related to the robbery, or 0; see {@link #extraValue}
     * @throws IllegalArgumentException if {@code peType} null, or {@code amount} or {@code victimAmount} &lt; 0,
     *     or {@code isGainLose} but {@code victimAmount} != 0
     * @see #SOCReportRobbery(String, int, int, int, boolean, int, int, int)
     * @see #SOCReportRobbery(String, int, int, SOCResourceSet, int)
     */
    public SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount, final int extraValue)
        throws IllegalArgumentException
    {
        this(gaName, perpPN, victimPN, -1, null, peType, isGainLose, amount, victimAmount, extraValue);
    }

    private SOCReportRobbery
        (final String gaName, final int perpPN, final int victimPN,
         final int resType, final SOCResourceSet resSet, final PEType peType,
         final boolean isGainLose, final int amount, final int victimAmount, final int extraValue)
        throws IllegalArgumentException
    {
        if ((peType == null) && (resSet == null) && (resType < 0))
            throw new IllegalArgumentException("peType/resSet/resType");
        if ((resSet == null) && ((amount < 0) || (victimAmount < 0)))
            throw new IllegalArgumentException("amounts");
        if (isGainLose && (victimAmount != 0))
            throw new IllegalArgumentException("victimAmount but isGainLose");
        if ((resSet != null) && resSet.isEmpty())
            throw new IllegalArgumentException("resSet empty");

        messageType = REPORTROBBERY;
        this.gaName = gaName;
        this.perpPN = perpPN;
        this.victimPN = victimPN;
        this.resType = ((resSet == null) && (peType == null)) ? resType : 0;
        this.resSet = resSet;
        this.peType = peType;
        this.isGainLose = isGainLose;
        this.amount = amount;
        this.victimAmount = victimAmount;
        this.extraValue = extraValue;
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
     * (('R' sep2 resType sep2 amount) or ('S' sep2 resType sep2 amount sep2 resType sep2 amount ...)
     * or ('E' sep2 {@link PEType#getValue() peType.getValue()} sep2 amount))
     * sep2 (isGainLose: 'T' or 'F') [sep2 victimAmount [sep2 extraValue]]
     *
     * @return the command string
     */
    public String toCmd()
    {
        StringBuilder sb = new StringBuilder
            (REPORTROBBERY + sep + gaName + sep2 + perpPN + sep2 + victimPN + sep2);
        if (resSet != null)
        {
            sb.append('S');
            for (int rt = SOCResourceConstants.MIN; rt <= SOCResourceConstants.WOOD; ++rt)
            {
                int am = resSet.getAmount(rt);
                if (am != 0)
                    sb.append(sep2).append(rt).append(sep2).append(am);
            }
        }
        else if (peType != null)
            sb.append('E').append(sep2).append(peType.getValue()).append(sep2).append(amount);
        else
            sb.append('R').append(sep2).append(resType).append(sep2).append(amount);
        sb.append(sep2).append(isGainLose ? 'T' : 'F');
        if ((victimAmount != 0) || (extraValue != 0))
        {
            sb.append(sep2).append(victimAmount);
            if (extraValue != 0)
                sb.append(sep2).append(extraValue);
        }

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
            final int ppn, vpn;
            SOCResourceSet rset = null;
            final PEType petype;
            final boolean gainLose;
            final int rtype, amt;
            int victimAmt, extraVal;

            ga = st.nextToken();
            ppn = Integer.parseInt(st.nextToken());
            vpn = Integer.parseInt(st.nextToken());
            String s = st.nextToken();
            if (s.length() != 1)
                return null;  // expected type char ('R' etc)
            char ch = s.charAt(0);

            int typeval = Integer.parseInt(st.nextToken());
            if (ch == 'S')
            {
                rtype = -1;
                petype = null;
                amt = 0;

                rset = new SOCResourceSet();
                rset.add(Integer.parseInt(st.nextToken()), typeval);

                // expect type & amount pair(s), then next field's T or F
                do
                {
                    s = st.nextToken();
                    if (s.length() == 0)
                        return null;
                    if (! Character.isDigit(s.charAt(0)))
                        break;  // is most likely next field's T or F

                    int resTypeval = Integer.parseInt(s);
                    int resAmt = Integer.parseInt(st.nextToken());
                    rset.add(resAmt, resTypeval);
                } while (true);
            }
            else if (ch == 'R')
            {
                rtype = typeval;
                petype = null;
                amt = Integer.parseInt(st.nextToken());

                s = st.nextToken();
            }
            else if (ch == 'E')
            {
                rtype = -1;
                petype = PEType.valueOf(typeval);
                if (petype == null)
                    return null;
                amt = Integer.parseInt(st.nextToken());

                s = st.nextToken();
            }
            else
                return null;  // expected 'R' or 'E'

            // at this point, s should be 'T' or 'F'
            if (s.length() != 1)
                return null;  // expected boolean char
            ch = s.charAt(0);
            if (ch == 'T')
                gainLose = true;
            else if (ch == 'F')
                gainLose = false;
            else
                return null;

            victimAmt = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 0;
            extraVal = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 0;

            return new SOCReportRobbery(ga, ppn, vpn, rtype, rset, petype, gainLose, amt, victimAmt, extraVal);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Strip out the parameter/attribute names from {@link #toString()}'s format,
     * returning message parameters as a list for {@link SOCMessage#parseMsgStr(String)}.
     * Handles resSet= resource type amounts, undoes mapping of action constant integers -> strings
     * ({@code "SCENARIO_CLOTH_COUNT"} etc).
     *
     * @param messageStrParams Params part of a message string formatted by {@link #toString()}; not {@code null}
     * @return Message parameters to finish parsing into a SOCMessage, or {@code null} if malformed
     */
    public static String stripAttribNames(final String messageStrParams)
    {
        final boolean isPENotRes = messageStrParams.contains("|peType="),
            isResSet = (! isPENotRes) && messageStrParams.contains("|resSet="),
            hasExtraValSkipsVictimAmt = messageStrParams.contains("|extraValue=")
                && ! messageStrParams.contains("|victimAmount=");
        List<String> pieces = SOCMessage.stripAttribsToList(messageStrParams);

        if (pieces.size() < 6)
            return null;
        if (! (pieces instanceof ArrayList<?>))
            pieces = new ArrayList<>(pieces);  // must support add method

        pieces.add(3, isResSet ? "S" : (isPENotRes ? "E" : "R"));
        int boolParamIdx = 6;
        if (isPENotRes)
        {
            PEType pe = PEType.valueOf(pieces.get(4));
            int v = (pe != null) ? pe.getValue() : 0;
            pieces.set(4, Integer.toString(v));
        }
        else if (isResSet)
        {
            // At this point, from SOCResourceSet.toString(),
            // pieces[4..9] have the form: "clay=3", "1", 0", "2", "0", "0"
            // Must convert to pairs of rtype, amount.
            // Ignores unknown res type/quantity in pieces[9].
            if (pieces.size() < 11)
                return null;
            List<String> resPairs = new ArrayList<>();
            for (int i = 4, rtype = SOCResourceConstants.MIN; i <= 8; ++i, ++rtype)
            {
                try
                {
                    String s = pieces.get(i);
                    if ((i == 4) && s.startsWith("clay="))
                        s = s.substring(5);
                    int amt = Integer.parseInt(s);
                    if (amt != 0)
                    {
                        resPairs.add(Integer.toString(rtype));
                        resPairs.add(Integer.toString(amt));
                    }
                }
                catch (NumberFormatException e) {}
            }

            // Replace the 6 resource amounts in pieces[4..9] with resPairs
            for (int i = 1; i <= 6; ++i)
                pieces.remove(4);
            pieces.addAll(4, resPairs);
            boolParamIdx = 4 + resPairs.size();
        }

        pieces.set(boolParamIdx, (pieces.get(boolParamIdx).equals("true") ? "T" : "F"));

        if (hasExtraValSkipsVictimAmt)
            pieces.add(pieces.size() - 1, "0");  // needed because toString skipped victimAmount=0 before extraValue=...

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
        if (resSet != null)
            sb.append("|resSet=").append(resSet);
        else if (peType != null)
            sb.append("|peType=").append(peType).append("|amount=").append(amount);
        else
            sb.append("|resType=").append(resType).append("|amount=").append(amount);
        sb.append("|isGainLose=").append(isGainLose);
        if ((victimAmount != 0) || ! isGainLose)
            sb.append("|victimAmount=").append(victimAmount);
        if (extraValue != 0)
            sb.append("|extraValue=").append(extraValue);

        return sb.toString();
    }

}
