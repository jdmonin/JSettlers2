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

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;
import soc.game.SOCResourceSet;
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;

/**
 * All known resources gained by players from a dice roll, and their new total resource counts.
 * Server calls {@link #buildForGame(SOCGame)}. Announced to all game members after a
 * {@link SOCDiceResult} where players gain resources: For overall sequence, see that message's javadoc.
 *<P>
 * Information sent here is gathered from each player's {@link SOCPlayer#getRolledResources()};
 * only players whose rolled resources have nonzero {@link SOCResourceSet#getKnownTotal()} are sent.
 * Those players' new resource totals, however, are from {@link SOCPlayer#getResources()}
 * {@link SOCResourceSet#getTotal() .getTotal()} which includes any unknown resources.
 *<P>
 * Before v2.0.00 these were sent as {@link SOCPlayerElement SOCPlayerElement(GAIN)}
 * and {@link SOCGameTextMsg}, followed by {@link SOCResourceCount}.
 * This single message is more efficient and also easier for i18n/localization.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class SOCDiceResultResources extends SOCMessageTemplateMi
{
    /**
     * Minimum version number (2.0.00) where the server sends dice roll result resources
     * and players' resource totals as a single {@link SOCDiceResultResources} message,
     * not several other message types.
     */
    public static final int VERSION_FOR_DICERESULTRESOURCES = 2000;

    private static final long serialVersionUID = 2000L;

    /**
     * {@code playerNum(i)} is the player number gaining the resources in {@link #playerRsrc playerRsrc(i)}.
     */
    public List<Integer> playerNum;

    /**
     * {@code playerRsrc(i)} is the resource set gained by player {@link #playerNum playerNum(i)}.
     * @see #playerResTotal
     */
    public List<SOCResourceSet> playerRsrc;

    /**
     * {@code playerResTotal(i)} is the new resource total count held by player {@link #playerNum playerNum(i)}.
     * @see #playerRsrc
     */
    public List<Integer> playerResTotal;

    /**
     * Builder for server to tell clients about players' gained resources and new total counts.
     * Only players with nonzero {@link SOCPlayer#getRolledResources()}
     * {@link SOCResourceSet#getKnownTotal() .getKnownTotal()} are included.
     * If no player gained any known resources, returns {@code null}.
     * @param ga  Game to check for rolled resources
     * @return  Message for this game's rolled resources, or {@code null} if no players gained known resources
     */
    public static final SOCDiceResultResources buildForGame(final SOCGame ga)
    {
        ArrayList<Integer> pnum = null;
        ArrayList<SOCResourceSet> rsrc = null;

        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            if (ga.isSeatVacant(pn))
                continue;

            final SOCPlayer pp = ga.getPlayer(pn);
            final SOCResourceSet rs = pp.getRolledResources();
            if (rs.getKnownTotal() == 0)
                continue;

            if (pnum == null)
            {
                pnum = new ArrayList<Integer>();
                rsrc = new ArrayList<SOCResourceSet>();
            }
            pnum.add(Integer.valueOf(pn));
            rsrc.add(rs);
        }

        if (pnum == null)
            return null;

        ArrayList<Integer> rTotal = new ArrayList<Integer>();
        for (int pn : pnum)
            rTotal.add(Integer.valueOf(ga.getPlayer(pn).getResources().getTotal()));

        return new SOCDiceResultResources(ga.getName(), pnum, rTotal, rsrc);
    }

    /**
     * Constructor for server to tell clients about players' gained resources and new total counts.
     * The int array will be built from {@code pn} and {@code rsrc}. The {@link #playerNum},
     * {@link #playerRsrc}, and {@link #playerResTotal} list fields will be left blank,
     * we don't need them to send the ints to clients.
     *
     * @param gaName  Game name
     * @param pn  Player numbers, same format as {@link #playerNum}
     * @param rTotal  New total resource count for each {@code pn}
     * @param rsrc Resources gained by each {@code pn}, same format as {@link #playerRsrc}
     * @throws IllegalArgumentException if {@code pn}.size() != {@code rsrc}.size() or {@code rTotal}.size(),
     *     or if any of them is empty
     * @throws NullPointerException if any parameter is null
     */
    private SOCDiceResultResources
        (final String gaName, final List<Integer> pn, final List<Integer> rTotal, final List<SOCResourceSet> rsrc)
        throws IllegalArgumentException, NullPointerException
    {
        super(DICERESULTRESOURCES, gaName, buildIntList(pn, rTotal, rsrc));
            // buildIntList checks pn, rTotal, rsrc for null and lengths

        playerNum = pn;
        playerResTotal = rTotal;
        playerRsrc = rsrc;
    }

    /**
     * Constructor for client to parse message from server via
     * {@link #parseDataStr(List) parseDataStr(List&lt;String>)}.
     * Decodes the integers in {@code pa[]} into {@link #playerNum}, {@link #playerRsrc},
     * and {@link #playerResTotal} lists.
     *
     * @param gameName Game name
     * @param pa  Sequence of integer parameters with this format: <pre>
     * pa[0] = count of players gaining resource(s)
     * Per-player sequences consisting of:
     *   pa[i] = player number gaining resource(s)
     *   pa[i+1] = new total resource count for that player
     *   Pair of:
     *   pa[i+2] = resource amount gained; not 0
     *   pa[i+3] = resource type gained
     *   More pairs, if any, for each other resource type gained by the player
     *   If there are more players after this one:
     *   pa[i+n] = 0, marking the end of the player's sequence</pre>
     * The player count parameter helps delimit in case of future expansion of this message's fields.
     * @throws IllegalArgumentException if {@code pa[]} doesn't fit that format, or ends in the middle of parsing
     */
    private SOCDiceResultResources(final String gameName, final int[] pa)
        throws IllegalArgumentException
    {
        super(DICERESULTRESOURCES, gameName, pa);

        final int plCount = pa[0];

        playerNum = new ArrayList<Integer>(plCount);
        playerRsrc = new ArrayList<SOCResourceSet>(plCount);
        playerResTotal = new ArrayList<Integer>(plCount);

        try
        {
            final int L = pa.length;
            int p = 0, i = 1;

            while (i < L)
            {
                playerNum.add(Integer.valueOf(pa[i]));
                ++p;
                ++i;

                playerResTotal.add(Integer.valueOf(pa[i]));
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

            if (p != plCount)
                throw new IllegalArgumentException("player count mismatch");
        }
        catch (ArrayIndexOutOfBoundsException e) {
            IllegalArgumentException iae = new IllegalArgumentException("too short");
            iae.initCause(e);
            throw iae;
        }
    }

    /**
     * Used by server constructor to build an outbound array of ints from these players and these resources.
     * See {@link #SOCDiceResultResources(String, int[])} for expected format.
     * @param pnum Player numbers, same format as {@link #playerNum}
     * @param rTotal  New total resource count for each {@code pn}
     * @param rsrc Resources gained by each {@code pn}, same format as {@link #playerRsrc}
     * @throws IllegalArgumentException if {@code pn}.size() != {@code rsrc}.size() or {@code rTotal}.size(),
     *     or if any of them is empty
     * @throws NullPointerException if any parameter is null
     */
    private static final int[] buildIntList
        (final List<Integer> pnum, final List<Integer> rTotal, final List<SOCResourceSet> rsrc)
    {
        final int n = pnum.size();
        if ((n == 0) || (n != rsrc.size()) || (n != rTotal.size()))
            throw new IllegalArgumentException();

        int len = 3 * n;  // player count elem, then for each player: player number, total rsrc count,
            // and the 0 separating their rsrc set from the next player unless last
            // (so, +1 for player count elem, -1 for no 0 after last player)
        for (SOCResourceSet rs : rsrc)
            len += 2 * (rs.getResourceTypeCount());  // for each rtype, amount and type number

        int[] pa = new int[len];
        pa[0] = n;
        int i = 1;  // to write next value to pa[i]
        for (int p = 0; p < n; ++p)  // player index for reading from pnum, rTotal, and rsrc
        {
            pa[i] = pnum.get(p);  ++i;
            pa[i] = rTotal.get(p);  ++i;

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
            prb.setResTotal(playerResTotal.get(i));

            b.addPlayerResources(prb);
        }

        GameMessage.GameMessageFromServer.Builder gb
            = GameMessage.GameMessageFromServer.newBuilder();
        gb.setGameName(game).setDiceResultResources(b);
        return Message.FromServer.newBuilder().setGameMessage(gb).build();
    }

}
