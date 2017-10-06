package syntax;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;
import soc.game.SOCResourceSet;
import soc.syntax.JSettlersLexer;
import soc.syntax.JSettlersParser;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static soc.game.SOCResourceConstants.WHEAT;

public class ShowCheatSheetTree {
    @Test
    public void test() {
        try {
            InputStream s = this.getClass().getClassLoader().getResourceAsStream("CheatSheet.jsettlers");
            CharStream cs = CharStreams.fromStream(s);
            soc.syntax.JSettlersLexer lexer = new JSettlersLexer(cs);
            CommonTokenStream cts = new CommonTokenStream(lexer);
            JSettlersParser p = new JSettlersParser(cts);
            StdOutTreeWalker walker = new StdOutTreeWalker();
            p.addParseListener(walker);
            JSettlersParser.ScriptContext script = p.script();
            System.out.print("--end--");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void testResourceParsing() {
        String resourcesText = "[wheat sheep]";
        CharStream cs = CharStreams.fromString(resourcesText);
        JSettlersLexer lexer = new JSettlersLexer(cs);
        CommonTokenStream cts = new CommonTokenStream(lexer);
        JSettlersParser p = new JSettlersParser(cts);
        StdOutTreeWalker walker = new StdOutTreeWalker();
        p.addParseListener(walker);
        JSettlersParser.ResourceSetContext resouces = p.resourceSet();
        System.out.print("--end--");
    }
    @Test
    public void testSyntaxHelper() {
        String text = "[wheat]";
        JSettlersParser.ResourceSetContext rs = Syntax.parse(JSettlersParser.ResourceSetContext.class, text);
        assertTrue(true);
        SOCResourceSet r = new SOCResourceSet(rs);
        assertEquals(1, r.getAmount(WHEAT));
    }
}
