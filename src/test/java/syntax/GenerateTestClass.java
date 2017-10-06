package syntax;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.Test;
import org.stringtemplate.v4.*;
import org.stringtemplate.v4.misc.ObjectModelAdaptor;
import org.stringtemplate.v4.misc.STNoSuchPropertyException;
import soc.syntax.JSettlersLexer;
import soc.syntax.JSettlersParser;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generates a test class using a script

This class takes a script and generates a java test class file. This test class
follows all commands in the script. It sets up a game, starts the game and then
executes all messages sequentially. After each message, assertions are executed
like any unit test.

Currently this class is not yet done. When writing generators for messages and
for assertions, it became apparent that this is yet too complicated. First, we
need a set of unit tests testing the individual components. When that is ready,
we have example idiomatic code to execute messages and checks.

For now this class will act as a dot on the horizon where we eventually want to
go to.

TODO: Stringtemplate syntax is fugly. We should probably ditch it in favor
for something better or create our own system.
 */
public class GenerateTestClass {

    private class ScriptModel {
        private String name;

        public String getName() {
            return name;
        }

        public ScriptModel(String name) {
            this.name = name;
        }
    }

    @Test
    public void test() {
        String fileName = "CheatSheet.jsettlers";
        String[] split = fileName.split("[.]");
        String scriptName = split[0];
        JSettlersParser.ScriptContext script = getScriptContext(fileName);

        STGroup group = new STGroupFile("ToUnitTestClass.jsettlers.stg");
        group.registerModelAdaptor(Object.class, new MyModelAdaptor());
        group.registerRenderer(ParserRuleContext.class, new ContextNameRenderer());
        group.registerRenderer(JSettlersParser.PlayerContext.class, new PlayerRenderer());
        ScriptModel scriptModel = new ScriptModel(scriptName);
        ST st = group.getInstanceOf("script");
        st.add("scriptModel", scriptModel);
        st.add("script", script);
        try {
            // TODO: typesafe paths
            String relativePath = "src/test/java/generated/";
            File file = new File(relativePath + "SimpleGameTest.java");
            FileOutputStream fos = new FileOutputStream(file);
            if (!file.exists()) {
                file.createNewFile();
            }
            OutputStreamWriter osWriter = new OutputStreamWriter(fos);
            STWriter stWriter = new AutoIndentWriter(osWriter);
            st.write(stWriter);
            osWriter.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.print(st.render());
        System.out.print("--end--");
    }

    /**
     * By default, StringTemplate does not support myObject.property(). ANTLR
     * produces trees using these properties, so this class is needed to support
     * out use-case.
     */
    private class MyModelAdaptor extends ObjectModelAdaptor {
        @Override
        public synchronized Object getProperty(
                Interpreter interpreter, ST self, Object o, Object property, String propertyName)
                throws STNoSuchPropertyException
        {
            if (o == null) {
                throw new NullPointerException("o");
            }

            Class<?> c = o.getClass();

            if ( property==null ) {
                return throwNoSuchProperty(c, propertyName, null);
            }

            Member member = findMember2(c, propertyName);
            if ( member!=null ) {
                try {
                    if (member instanceof Method) {
                        return ((Method)member).invoke(o);
                    }
                    else if (member instanceof Field) {
                        return ((Field)member).get(o);
                    }
                }
                catch (Exception e) {
                    throwNoSuchProperty(c, propertyName, e);
                }
            }

            return throwNoSuchProperty(c, propertyName, null);
        }

        private Member findMember2(Class<?> clazz, String memberName) {
            if (clazz == null) {
                throw new NullPointerException("clazz");
            }
            if (memberName == null) {
                throw new NullPointerException("memberName");
            }

            synchronized (membersCache) {
                Map<String, Member> members = membersCache.get(clazz);
                Member member;
                if (members != null) {
                    member = members.get(memberName);
                    if (member != null) {
                        return member != INVALID_MEMBER ? member : null;
                    }
                } else {
                    members = new HashMap<String, Member>();
                    membersCache.put(clazz, members);
                }

                // try getXXX and isXXX properties, look up using reflection
                String methodSuffix = Character.toUpperCase(memberName.charAt(0)) +
                        memberName.substring(1, memberName.length());

                member = tryGetMethod(clazz, memberName);
                if (member == null) {
                    member = tryGetMethod(clazz, "get" + methodSuffix);
                    if (member == null) {
                        member = tryGetMethod(clazz, "is" + methodSuffix);
                        if (member == null) {
                            member = tryGetMethod(clazz, "has" + methodSuffix);
                        }
                    }
                }

                if (member == null) {
                    // try for a visible field
                    member = tryGetField(clazz, memberName);
                }

                members.put(memberName, member != null ? member : INVALID_MEMBER);
                return member;
            }
        }
    }

    /**
     * Renders the name of the class in StringTemplate
     */
    private class ContextNameRenderer extends StringRenderer{
        @Override
        public String toString(Object o, String formatString, Locale locale) {
            if (!(o instanceof ParserRuleContext)) {
                return "";
            }
            Class c = o.getClass();
            String name = c.getSimpleName().replace("Context", "");
            return name;
        }
    }

    /**
     * Renders the name of a player with support for client, server and non-
     * contextual player names.
     */
    private class PlayerRenderer extends StringRenderer{
        @Override
        public String toString(Object o, String formatString, Locale locale) {
            if (!(o instanceof JSettlersParser.PlayerContext)) {
                return "";
            }
            JSettlersParser.PlayerContext player = (JSettlersParser.PlayerContext)o;
            if (player.server() != null){
                return "serverPlayer" + player.NUMBER();
            }
            if (player.client() != null){
                return "clientPlayer" + player.NUMBER();
            }
            return "player" + player.NUMBER();
        }
    }

    private String getStringTemplate() {
        String fileName = "ToUnitTestClass.jsettlers.st";
        InputStream is = this.getClass().getClassLoader()
                .getResourceAsStream(fileName);
        Charset utf8 = Charset.forName(StandardCharsets.UTF_8.name());
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        String content = s.next();
        return content;
    }

    private JSettlersParser.ScriptContext getScriptContext(String fileName) {
        InputStream scriptStream = this.getClass().getClassLoader().getResourceAsStream(fileName);
        CharStream cs = null;
        try {
            cs = CharStreams.fromStream(scriptStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSettlersLexer lexer = new JSettlersLexer(cs);
        CommonTokenStream cts = new CommonTokenStream(lexer);
        JSettlersParser p = new JSettlersParser(cts);
        StdOutTreeWalker walker = new StdOutTreeWalker();
        p.addParseListener(walker);

        return p.script();
    }
}
