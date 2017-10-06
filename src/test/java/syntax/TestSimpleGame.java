package syntax;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.Test;
import soc.syntax.JSettlersLexer;
import soc.syntax.JSettlersParser;
import soc.game.*;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertTrue;

public class TestSimpleGame {
    @Test
    public void test() {
        try {
            InputStream s = this.getClass().getClassLoader().getResourceAsStream("SimpleGame.jsettlers");
            CharStream cs = CharStreams.fromStream(s);
            JSettlersLexer lexer = new JSettlersLexer(cs);
            CommonTokenStream cts = new CommonTokenStream(lexer);
            JSettlersParser p = new JSettlersParser(cts);
            StdOutTreeWalker walker = new StdOutTreeWalker();
            p.addParseListener(walker);
            JSettlersParser.ScriptContext script = p.script();
            System.out.print("--end--");
//            ScriptVisitor scriptVisitor = new ScriptVisitor();
//            Script parsedScript = scriptVisitor.visitScript(script);
//            assertTrue(parsedScript.game() != null);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    public class BaseVisitor<T> extends JSettlersBaseVisitor<T> {
//        protected int parseInt(TerminalNode digit) {
//            int value = Integer.parseInt(digit.toString());
//            return value;
//        }
//    }
//
//    class Script {
//        private SOCGame game;
//
//        public Script(SOCGame game) {
//            this.game = game;
//        }
//
//        public SOCGame game() {
//            return this.game;
//        }
//    }
//
//    public class ScriptVisitor extends JSettlersBaseVisitor<Script> {
//        @Override
//        public Script visitScript(JSettlersParser.ScriptContext ctx) {
//            GameVisitor gv = new GameVisitor();
//            SOCGame game = gv.visitGame(ctx.game());
//            TurnsVisitor turns = new TurnsVisitor(game);
//
//            // kick off the script here
//            turns.visitTurns(ctx.turns());
//            return new Script(game);
//        }
//    }
//    class GameVisitor extends JSettlersBaseVisitor<SOCGame> {
//        @Override
//        public SOCGame visitGame(JSettlersParser.GameContext ctx) {
//            SOCGame game = new SOCGame("agame");
//            if (ctx.players() != null) {
////                for (JSettlersParser.SetupPlayerContext spc : ctx.setupPlayer()) {
////                    SetupPlayerVisitor sp = new SetupPlayerVisitor(game);
////                    SOCPlayer player = sp.visitSetupPlayer(spc);
////                     TODO: add player to game?
////                }
//            }
//            return game;
//        }
//    }
//
//    class SetupPlayerVisitor extends BaseVisitor<SOCPlayer> {
//        private SOCGame game;
//
//        public SetupPlayerVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public SOCPlayer visitPlayers(JSettlersParser.PlayersContext ctx) {
//            JSettlersParser.PlayerContext p = ctx.player();
//            if (p != null) {
//                int pn = parseInt(p.NUMBER());
//                SOCPlayer socp = new SOCPlayer(pn, game);
//                return socp;
//            }
//            return null;
//        }
//    }
//
//    class TurnsVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCGame game;
//
//        public TurnsVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public Object visitTurns(JSettlersParser.TurnsContext ctx) {
//            for (JSettlersParser.TurnContext turn : ctx.turn()) {
//                TurnVisitor tv = new TurnVisitor(game);
//                tv.visitTurn(turn);
//            }
//            return null;
//        }
//    }
//
//    class TurnVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCGame game;
//
//        public TurnVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public Object visitTurn(JSettlersParser.TurnContext ctx) {
//            for (JSettlersParser.ActionContext action : ctx.action()) {
//                ActionVisitor av = new ActionVisitor(game);
//                av.visitAction(action);
//                for (JSettlersParser.CheckContext cc : ctx.check()) {
//                    CheckVisitor cv = new CheckVisitor(game);
//                    boolean check = cv.visitCheck(cc);
//                    if (!check){
//                        // TODO: assert.fail with message
//                    }
//                }
//            }
//            return null;
//        }
//    }
//
//    class CheckVisitor extends BaseVisitor<Boolean> {
//        private SOCGame game;
//
//        public CheckVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public Boolean visitCheck(JSettlersParser.CheckContext ctx) {
//            if (ctx.isOnTurn() != null){
//                JSettlersParser.IsOnTurnContext iot = ctx.isOnTurn();
//                int pn = parseInt(iot.player().NUMBER());
//                return game.getCurrentPlayerNumber() == pn;
//            }
//            return true;
////            String cz = ctx.CHECK().toString();
////            return super.visitCheck(ctx);
//        }
//    }
//
//    class ActionVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCGame game;
//
//        public ActionVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public Object visitAction(JSettlersParser.ActionContext ctx) {
////            if (ctx.rollDice() != null) {
////                SOCRollDice rd = new SOCRollDice(game.getName());
////                // fire action
////            }
//            if (ctx.buildCity() != null) {
//                // TODO: implement
//            }
//            return null;
//        }
//    }
//
////    class RollDiceVisitor extends JSettlersBaseVisitor<SOCRollDice> {
////        private SOCGame game;
////
////        public RollDiceVisitor(SOCGame game) {
////            this.game = game;
////        }
////
////        @Override
////        public SOCRollDice visitRollDice(JSettlersParser.RollDiceContext ctx) {
////            SOCRollDice rd = new SOCRollDice(game.getName());
////            return rd;
////        }
////    }
//
//    class PlayerVisitor extends JSettlersBaseVisitor<SOCPlayer> {
//        private SOCGame game;
//
//        public PlayerVisitor(SOCGame game) {
//            this.game = game;
//        }
//
//        @Override
//        public SOCPlayer visitPlayer(JSettlersParser.PlayerContext ctx) {
//            int pn = Integer.parseInt(ctx.NUMBER().toString());
//            return game.getPlayer(pn);
//        }
//    }
//
//    class DevCardsVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCPlayer player;
//
//        public DevCardsVisitor(SOCPlayer player) {
//            this.player = player;
//        }
//
//        @Override
//        public Object visitDevCards(JSettlersParser.DevCardsContext ctx) {
//
//            for (JSettlersParser.DevCardContext dc : ctx.devCard()) {
//                int type;
//                if (dc.monopoly() != null) type = SOCDevCardConstants.MONO;
//                if (dc.yearOfPlenty() != null) type = SOCDevCardConstants.DISC;
//                if (dc.soldier() != null) type = SOCDevCardConstants.KNIGHT;
//                if (dc.roadBuilding() != null) type = SOCDevCardConstants.ROADS;
//                if (dc.victoryPoint() != null) type = SOCDevCardConstants.UNIV;
////                SOCInventoryItem newItem = new SOCInventoryItem(
////                        type, false, true, );
//
//            }
//            return null;
//        }
//    }
//
//    class StockVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCPlayer player;
//
//        public StockVisitor(SOCPlayer player) {
//            this.player = player;
//        }
//
//        @Override
//        public Object visitStock(JSettlersParser.StockContext ctx) {
//            // TODO implement
//            return null;
//        }
//    }
//
//    // <Object> here means: I dont need or care about T
//    class HandVisitor extends JSettlersBaseVisitor<Object> {
//        private SOCResourceSet playerHand;
//
//        public HandVisitor(SOCResourceSet playerHand) {
//            this.playerHand = playerHand;
//        }
//
//        @Override
//        public Integer visitHand(JSettlersParser.HandContext ctx) {
//            ResourceSet toAdd = new ResourceSetVisitor().visitResourceSet(ctx.resourceSet());
//            playerHand.add(toAdd);
//            return 0;
//        }
//    }
//
//    class ResourceSetVisitor extends JSettlersBaseVisitor<ResourceSet> {
//        @Override
//        public ResourceSet visitResourceSet(JSettlersParser.ResourceSetContext ctx) {
//            SOCResourceSet rs = new SOCResourceSet();
//            for (JSettlersParser.ResourceContext c : ctx.resource()) {
//                if (c.ore() != null) rs.add(1, SOCResourceConstants.ORE);
//                if (c.brick() != null) rs.add(1, SOCResourceConstants.CLAY);
//                if (c.wheat() != null) rs.add(1, SOCResourceConstants.WHEAT);
//                if (c.timber() != null) rs.add(1, SOCResourceConstants.WOOD);
//                if (c.sheep() != null) rs.add(1, SOCResourceConstants.SHEEP);
//            }
//            return rs;
//        }
//    }

}

