package syntax;

import org.antlr.v4.runtime.ParserRuleContext;
import soc.syntax.JSettlersLexer;
import soc.syntax.JSettlersParser;


public class StdOutTreeWalker extends soc.syntax.JSettlersBaseListener {
    private int level = 0;


    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        super.enterEveryRule(ctx);
        StringBuffer outputBuffer = new StringBuffer(level);
        for (int i = 0; i < level; i++) {
            outputBuffer.append("  ");
        }
        System.out.print(outputBuffer.toString());
        Class c = ctx.getClass();
        String name = c.getSimpleName().replace("Context", "");
        System.out.print(name);
        System.out.print("\r\n");
        level++;
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        super.exitEveryRule(ctx);
        level--;
    }
}
