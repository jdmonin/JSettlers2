/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013-2015,2017-2018 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017 Ruud Poutsma <rtimon@gmail.com>
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
 **/
package soc.message;

import java.util.ArrayList;
import java.util.List;

import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * All resources gained by players from a dice roll.
 * Sent to all game members after a {@link SOCDiceResult}.
 *<P>
 * Before v2.0.00, these were sent as {@link SOCPlayerElement SOCPlayerElement(GAIN)} and {@link SOCGameTextMsg}.
 * This single message is more efficient and also easier for i18n/localization.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCDiceResultResources extends SOCMessageTemplateMi
{
    /**
     * Version number (2.0.00) where the server no longer sends dice roll result resources as
     * a game text message + several {@link SOCPlayerElement SOCPlayerElement(GAIN)} messages,
     * and instead sends a single {@link SOCDiceResultResources} message.
     */
    public static final int VERSION_FOR_DICERESULTRESOURCES = 2000;

    private static final long serialVersionUID = 2000L;

    /**
     * {@code playerNum(i)} is the player number gaining the resources in {@link #playerRsrc playerRsrc(i)}.
     */
    public List<Integer> playerNum;

    /**
     * {@code playerRsrc(i)} is the resource set gained by player {@link #playerNum playerNum(i)}.
     */
    public List<SOCResourceSet> playerRsrc;

    /**
     * Constructor for server to tell clients about players' gained resources.
     * The int array will be built from {@code pn} and {@code rsrc}; the {@code playerNum} and
     * {@code playerRsrc} fields will be left blank, we don't need them to send the ints to clients.
     *
     * @param gaName  Game name
     * @param pn  Player numbers, same format as {@link #playerNum}
     * @param rsrc Resources gained by each {@code pn}, same format as {@link #playerRsrc}
     * @throws IllegalArgumentException if {@code pn}.size() != {@code rsrc}.size(), or if either is empty
     * @throws NullPointerException if any parameter is null
     */
    public SOCDiceResultResources(final String gaName, List<Integer> pn, List<SOCResourceSet> rsrc)
        throws IllegalArgumentException, NullPointerException
    {
        super(DICERESULTRESOURCES, gaName, buildIntList(pn, rsrc));
            // buildIntList checks pn, rsrc for null and lengths

        playerNum = pn;
        playerRsrc = rsrc;
    }

    /**
     * Constructor for client to parse message from server via
     * {@link #parseDataStr(List) parseDataStr(List&lt;String>)}.
     * Decodes the integers in {@code pa[]} into {@link #playerNum} and {@link #playerRsrc}.
     *
     * @param gameName Game name
     * @param pa Parameters, each of which is a sequence of integers with this format: <pre>
     * pa[i] = player number gaining resource(s)
     *    Pair of:
     * pa[i+1] = resource amount gained
     * pa[i+2] = resource type
     *    More pairs, if any, for each other resource type gained by the player
     * If there are more players after this one:
     * pa[i+n] = 0, marking the end of the pairs</pre>
     * @throws IllegalArgumentException if {@code pa[]} doesn't fit that format, or ends in the middle of parsing
     */
    protected SOCDiceResultResources(final String gameName, final int[] pa)
        throws IllegalArgumentException
    {
        super(DICERESULTRESOURCES, gameName, pa);

        playerNum = new ArrayList<Integer>();
        playerRsrc = new ArrayList<SOCResourceSet>();

        final int L = pa.length;
        int i = 0;
        try
        {
            while (i < L)
            {
                playerNum.add(Integer.valueOf(pa[i]));
                ++i;

                // Parse pairs of res amount + res type, until we get a 0
                SOCResourceSet rsrc = new SOCResourceSet();
                int amount = pa[i];  ++i;
                while ((amount != 0) && (i < L))
                {
                    rsrc.add(amount, pa[i]);  ++i;
                    if (i < L)
                    {
                        amount = pa[i];  ++i;
                    } else {
                        amount = 0;  // last player, end of array
                    }
                }

                playerRsrc.add(rsrc);
            }
        }
        catch (ArrayIndexOutOfBoundsException e) {
            IllegalArgumentException iae = new IllegalArgumentException("too short");
            iae.initCause(e);
            throw iae;
        }
    }

    /**
     * Used by server constructor to build an outbound array of ints from these players and these resources.
     * @param pnum Player numbers, same format as {@link #playerNum}
     * @param rsrc Resources gained by each {@code pn}, same format as {@link #playerRsrc}
     * @throws IllegalArgumentException if {@code pn}.size() != {@code rsrc}.size(), or if either is empty
     * @throws NullPointerException if any parameter is null
     */
    private static final int[] buildIntList
        (final List<Integer> pnum, final List<SOCResourceSet> rsrc)
    {
        final int n = pnum.size();
        if ((n == 0) || (n != rsrc.size()))
            throw new IllegalArgumentException();

        int len = (2 * n) - 1;  // for each player, player number and the 0 separating their rset from the next player
        for (SOCResourceSet rs : rsrc)
            len += 2 * (rs.getResourceTypeCount());  // for each rtype, amount and type number

        int[] pa = new int[len];
        int i = 0;  // write next value to pa[i]
        for (int p = 0; p < n; ++p)  // current index reading from pnum and rsrc
        {
            pa[i] = pnum.get(p);  ++i;

            final SOCResourceSet rs = rsrc.get(p);
            for (int rtype = SOCResourceConstants.MIN; rtype <= Data.ResourceType.WOOD_VALUE; ++rtype)
            {
                int amt = rs.getAmount(rtype);
                if (amt != 0)
                {
                    pa[i] = amt;    ++i;
                    pa[i] = rtype;  ++i;
                }
            }

            if (p != (n-1))
            {
                pa[i] = 0;  ++i;  // separate from next player
            }
        }

        return pa;
    }

    /**
     * Minimum version where this message type is used ({@link #VERSION_FOR_DICERESULTRESOURCES}).
     * DICERESULTRESOURCES introduced in 2.0.00 for dice roll result resources.
     * @return Version number, 2000 for JSettlers 2.0.00.
     */
    public int getMinimumVersion() { return VERSION_FOR_DICERESULTRESOURCES; /* == 2000 */ }

    /**
     * Parse the command String list into a SOCDiceResultResources message.
     * Calls {@link #SOCDiceResultResources(String, int[])} constructor,
     * see its javadoc for parameter details.
     *
     * @param pa   the parameters; length 2 or more required.
     * @return    a parsed message, or null if parsing errors
     */
    public static SOCDiceResultResources parseDataStr(List<String> pa)
    {
        if ((pa == null) || (pa.size() < 2))
            return null;

        try
        {
            final String gaName = pa.get(0);
            int[] ipa = new int[pa.size() - 1];
            for (int i = 0; i < ipa.length; ++i)
                ipa[i] = Integer.parseInt(pa.get(i + 1));

            return new SOCDiceResultResources(gaName, ipa);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected Message.FromServer toProtoFromServer()
    {
        GameMessage.DiceResultResources.Builder b
            = GameMessage.DiceResultResources.newBuilder();

        final int n = playerNum.size();
        for (int i = 0; i < n; ++i)
        {
            GameMessage.DiceResultResources.PlayerResources.Builder prb
                = GameMessage.DiceResultResources.PlayerResources.newBuilder();

            prb.setPlayerNumber(playerNum.get(i));
            final SOCResourceSet rs = playerRsrc.get(i);
            prb.setResGained(ProtoMessageBuildHelper.toResourceSet(rs));
            prb.setResTotal(rs.getTotal());  // TODO should be player's entire total, not gained total

            b.addPlayerResources(prb);
        }

        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGaName(game).setDiceResultResources(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

}
