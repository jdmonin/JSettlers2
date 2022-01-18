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

package soctest.proto;

import soc.game.SOCResourceSet;
import soc.message.ProtoMessageBuildHelper;
import soc.message.SOCBankTrade;
import soc.message.SOCDiscard;
import soc.message.SOCMakeOffer;
import soc.message.SOCMessage;
import soc.message.SOCPickResources;
import soc.proto.Data;
import soc.proto.GameMessage;
import soc.proto.Message;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test the few place where protobuf rejects or requires certain message content:
 *
 *<UL>
 * <LI> Messages from client with a {@link Data.ResourceSet} shouldn't contain unknown resources
 *</UL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 3.0.00
 */
public class TestMessageContentValidators
{

    /**
     * Test messages from client containing {@link Data.ResourceSet}s,
     * which should have no unknown resources:
     * {@link GameMessage.TradeWithBank}, {@link GameMessage.TradeMakeOffer},
     * {@link GameMessage.LoseResources}, {@link GameMessage.GainResources}.
     */
    @Test
    public void testUnknownResourceSetFromClient()
    {
        final SOCResourceSet SHEEP_3 = new SOCResourceSet(0, 0, 3, 0, 0, 0),
            WOOD_1 = new SOCResourceSet(0, 0, 0, 0, 1, 0),
            WOOD_1_UNKNOWN_1 = new SOCResourceSet(0, 0, 0, 0, 1, 1);

        // TradeWithBank
        {
            // no unknowns:
            GameMessage.TradeWithBank.Builder b
                = GameMessage.TradeWithBank.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(SHEEP_3))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1));
            GameMessage.GameMessageFromClient.Builder gb
                = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeWithBank(b);
            Message.FromClient msg1 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            SOCBankTrade msgAtSrv = (SOCBankTrade) SOCMessage.toMsg(msg1);
            assertNotNull("ok with no unknowns", msgAtSrv);
            assertEquals(SHEEP_3, msgAtSrv.getGiveSet());
            assertEquals(WOOD_1, msgAtSrv.getGetSet());

            // unknown in "give": server rejects
            b = GameMessage.TradeWithBank.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1));
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeWithBank(b);
            Message.FromClient msg2 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCBankTrade) SOCMessage.toMsg(msg2);
            assertNull("reject unknown rsrc in give", msgAtSrv);

            // unknown in "get": server rejects
            b = GameMessage.TradeWithBank.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(SHEEP_3))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1));
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeWithBank(b);
            Message.FromClient msg3 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCBankTrade) SOCMessage.toMsg(msg3);
            assertNull("reject unknown rsrc in get", msgAtSrv);
        }

        // TradeMakeOffer
        {
            // no unknowns:
            GameMessage.TradeMakeOffer.Builder b
                = GameMessage.TradeMakeOffer.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(SHEEP_3))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1));
            Data._IntArray.Builder iab = Data._IntArray.newBuilder();
            iab.addArr(3);
            b.setToPlayers(iab);

            GameMessage.GameMessageFromClient.Builder gb
                = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeMakeOffer(b);
            Message.FromClient msg1 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            SOCMakeOffer msgAtSrv = (SOCMakeOffer) SOCMessage.toMsg(msg1);
            assertNotNull("ok with no unknowns", msgAtSrv);
            assertEquals(SHEEP_3, msgAtSrv.getOffer().getGiveSet());
            assertEquals(WOOD_1, msgAtSrv.getOffer().getGetSet());

            // unknown in "give": server rejects
            b = GameMessage.TradeMakeOffer.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1));
            iab = Data._IntArray.newBuilder();
            iab.addArr(3);
            b.setToPlayers(iab);
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeMakeOffer(b);
            Message.FromClient msg2 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCMakeOffer) SOCMessage.toMsg(msg2);
            assertNull("reject unknown rsrc in give", msgAtSrv);

            // unknown in "get": server rejects
            b = GameMessage.TradeMakeOffer.newBuilder();
            b.setGive(ProtoMessageBuildHelper.toResourceSet(SHEEP_3))
                .setGet(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1));
            iab = Data._IntArray.newBuilder();
            iab.addArr(3);
            b.setToPlayers(iab);
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setTradeMakeOffer(b);
            Message.FromClient msg3 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCMakeOffer) SOCMessage.toMsg(msg3);
            assertNull("reject unknown rsrc in get", msgAtSrv);
        }

        // LoseResources
        {
            // no unknowns:
            GameMessage.LoseResources.Builder b
                = GameMessage.LoseResources.newBuilder();
            b.setLose(ProtoMessageBuildHelper.toResourceSet(SHEEP_3));
            GameMessage.GameMessageFromClient.Builder gb
                = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setLoseResources(b);
            Message.FromClient msg1 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            SOCDiscard msgAtSrv = (SOCDiscard) SOCMessage.toMsg(msg1);
            assertNotNull("ok with no unknowns", msgAtSrv);
            assertEquals(SHEEP_3, msgAtSrv.getResources());

            // unknown: server rejects
            b = GameMessage.LoseResources.newBuilder();
            b.setLose(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1));
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setLoseResources(b);
            Message.FromClient msg2 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCDiscard) SOCMessage.toMsg(msg2);
            assertNull("reject unknown rsrc", msgAtSrv);
        }

        // GainResources
        {
            // no unknowns:
            GameMessage.GainResources.Builder b
                = GameMessage.GainResources.newBuilder();
            b.setGain(ProtoMessageBuildHelper.toResourceSet(SHEEP_3));
            GameMessage.GameMessageFromClient.Builder gb
                = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setGainResources(b);
            Message.FromClient msg1 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            SOCPickResources msgAtSrv = (SOCPickResources) SOCMessage.toMsg(msg1);
            assertNotNull("ok with no unknowns", msgAtSrv);
            assertEquals(SHEEP_3, msgAtSrv.getResources());

            // unknown: server rejects
            b = GameMessage.GainResources.newBuilder();
            b.setGain(ProtoMessageBuildHelper.toResourceSet(WOOD_1_UNKNOWN_1));
            gb = GameMessage.GameMessageFromClient.newBuilder();
            gb.setGameName("test").setGainResources(b);
            Message.FromClient msg2 = Message.FromClient.newBuilder()
                .setGameMessage(gb).build();

            msgAtSrv = (SOCPickResources) SOCMessage.toMsg(msg2);
            assertNull("reject unknown rsrc", msgAtSrv);
        }
    }

}
