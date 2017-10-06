package syntax;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import soc.syntax.JSettlersLexer;
import soc.syntax.JSettlersParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Syntax {
    /**
     * Simplify conversion from a string into a parsed node of JSettlers syntax
     *
     * This class hides some complexity in parsing text. Exceptions, the
     * lexer & parser itself are hidden away from the API user.
     *
     * Due to the type erasure of the JVM, we must specify both a generic T and
     * a class variable. The T is used to cast the obtained node type, the clazz
     * is used for reflection.
     *
     * Example use:
     * ResourceContext rs = Syntax.parse(ResourceContext.class, "wheat");
     *
     * @param clazz class of node type to parse. E.g. ResourceSetContext
     * @param text string in the form of the syntax node type e.g. [wheat]
     * @param <T>  class of node type to parse. E.g. ResourceSetContext
     * @return node instance of type T. Returns null when parsing fails.
     */
    public static <T extends ParserRuleContext> T parse(Class clazz, String text){
        // To make this method nice, we must resort to reflection
        // unfortunately...
        // TODO: check if clazz derives from ParserRuleContext and bail if not
        String name = clazz.getSimpleName(); // e.g. ResourceSetContext
        String withoutContext = name.replaceAll("Context", "");
        String firstCharLowercase = withoutContext.substring(0, 1).toLowerCase();
        String methodName = firstCharLowercase + withoutContext.substring(1);

        CharStream cs = CharStreams.fromString(text);
        JSettlersLexer lexer = new JSettlersLexer(cs);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        JSettlersParser parser = new JSettlersParser(tokenStream);
        Method method;
        try {
            method = parser.getClass().getMethod(methodName);
            Object parsedNode = method.invoke(parser);
            return (T) parsedNode;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}
