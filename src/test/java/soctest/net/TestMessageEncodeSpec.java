/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
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

package soctest.net;

import java.lang.reflect.Constructor;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.message.SOCMessage;

import socmisc.io.FileUtils;
import socmisc.io.ParsedMessageSpec;

/**
 * Test whether a message can be encoded to and decoded, using its formal specification.
 * Per-message specification and test cases use a protobuf-like syntax
 * given in a comment in the message type's java source which begins with
 * the token {@code jsettlers_message}.
 *<P>
 * Uses ThatMessageType.toCmd() to encode, and SOCMessage.toMsg(String)
 * to decode, with introspection to validate field contents.
 *<P>
 * See {@link ParsedMessageSpec} class javadoc for more details.
 *<P>
 * When ran, classpath must include {@code soc.message.*} and {@code socmisc.io.*}.
 *<P>
 * For parsing the specification syntax from {@code soc.message} package's
 * *.java, must be run anywhere within the project directory.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class TestMessageEncodeSpec
{
    @Test
    public void testParseNoToken()
        throws ParseException
    {
        ParsedMessageSpec ps = new ParsedMessageSpec
            ("SampleMsg", "public class SampleMsg { ... }\n");
        assertTrue("classname", "SampleMsg".equals(ps.className));
        assertNull(ps.msgtypeConstName);
        assertNull(ps.fields);
        assertNull(ps.testCases);
    }

    @Test(expected=ParseException.class)
    public void testParseBadMatch()
        throws ParseException
    {
        ParsedMessageSpec ps = new ParsedMessageSpec
            ("SampleMsg", '/' + "* \n * jsettlers_message: INCOMPLETE_SPEC_FORMAT_HERE *" + "/\n\n");
    }

    @Test(expected=ParseException.class)
    public void testParseCommentBrace()
        throws ParseException
    {
        final String PARSE_TEST_SPEC_COMMENT_BAD_CHAR =
            "public class SampleMsg extends SOCMessage\n{\n\n/"
            + "* \n"
            + " * jsettlers_message: SAMPLE { \n"
            + " *   string role; \n"
            + " *   string nickname;  // comment contains }, regex fails expecting constructor_fields or testcase afterwards\n"
            + " *   int authScheme; \n"
            + " * } \n"
            + " * testcase (\"P\", \"myname\", \"\", 1, \"myhost\") \n"
            + " *   = \"P,myname,1,myhost,\"; \n"
            + " *" + "/\n\n"
            + "}\n";

        ParsedMessageSpec ps = new ParsedMessageSpec
            ("SampleMsg", PARSE_TEST_SPEC_COMMENT_BAD_CHAR);
    }

    @Test
    public void testParser()
        throws ParseException
    {
        final String PARSE_TEST_SPEC =
            '/' + "* \n * some other comment \n *"
            + "/\n\npublic class SOCAuthRequest extends SOCMessage\n{\n\n/"
            + "* \n"
            + " * jsettlers_message: AUTHREQUEST { \n"
            + " *   string role  [empty='\\001' ]; \n"
            + " *   string nickname; \n"
            + " *   int authScheme; \n"
            + " *   string host [empty = '\\t']; \n"
            + " *   string password [rest_of_message];  // pw may contain delimiter characters \n"
            + " * } \n"
            + " * constructor_fields(role, nickname, password, authScheme, host); \n"
            + " * testcase (\"UA\", \"myname\", \"mypw\", 1, null) \n"
            + " *   = \"UA,myname,1,\\t,mypw\"; \n"
            + " * testcase (\"P\", \"myname\", \"\", 1, \"myhost\") \n"
            + " *   = \"P,myname,1,myhost,\"; \n"
            + " *" + "/\n\n"
            + " private String role;\n\n"
            + "}\n";

        ParsedMessageSpec ps = new ParsedMessageSpec("SOCAuthRequest", PARSE_TEST_SPEC);

        assertTrue("classname", "SOCAuthRequest".equals(ps.className));

        final String[] CONSTRUC = { "role", "nickname", "password", "authScheme", "host" };
        assertTrue("construcFields contents correct", Arrays.equals(CONSTRUC, ps.construcFields));

        // expected test cases; soc.message.SOCMessage.AUTHREQUEST == 999
        final ArrayList<ParsedMessageSpec.TestCase> TC = new ArrayList<ParsedMessageSpec.TestCase>();
        TC.add(new ParsedMessageSpec.TestCase
               (new Object[]{"UA", "myname", "mypw", Integer.valueOf(1), null},
                "999|UA,myname,1,\t,mypw"));
        TC.add(new ParsedMessageSpec.TestCase
               (new Object[]{"P", "myname", "", Integer.valueOf(1), "myhost"},
                "999|P,myname,1,myhost,"));
        assertTrue("testCases contents correct", ps.testCases.equals(TC));

        final ArrayList<ParsedMessageSpec.MsgField> F = new ArrayList<ParsedMessageSpec.MsgField>();
        F.add(new ParsedMessageSpec.MsgField("role", String.class, (char) 1, false));
        F.add(new ParsedMessageSpec.MsgField("nickname", String.class, (char) 0, false));
        F.add(new ParsedMessageSpec.MsgField("authScheme", Integer.TYPE, (char) 0, false));
        F.add(new ParsedMessageSpec.MsgField("host", String.class, '\t', false));
        F.add(new ParsedMessageSpec.MsgField("password", String.class, (char) 0, true));
        assertTrue("field list contents correct", ps.fields.equals(F));
    }

    @Test
    public void testAllMessageSourceSpecs()
    	throws IOException, SecurityException
    {
        // - find the soc.message _source_ package
        File msgdir = FileUtils.findNearbyDirFromParents
            ("src/main/java/soc/message", new String[]{ "Readme.md", "doc" });
        assertNotNull
            ("Could not find src/main/java/soc/message; tests should be ran within project's directory structure",
             msgdir);

        File[] msgs = msgdir.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            { return (! name.equals("SOCMessage.java")) && name.endsWith(".java"); }
        });

        assertTrue("Did not find *.java in " + msgdir.getAbsolutePath(), msgs.length != 0);

        int nSpecs = 0;  // count message classes having specs
        Map<String, String> failMsgs = new HashMap<String, String>();  // <msg class, failure detail>

        StringBuilder sb = new StringBuilder();
        for (File f : msgs)
        {
            try
            {
                final String fn = f.getName();
                int i = fn.lastIndexOf(".java");
                final String clsName = (i > 0) ? fn.substring(0, i) : fn;

                // - try to parse spec
                ParsedMessageSpec ps = new ParsedMessageSpec(clsName, FileUtils.readUTF8File(f));
                if (ps.testCases == null)
                    continue;  // no spec in this file

                ++nSpecs;
                if (sb.length() != 0)
                    sb.delete(0, sb.length());  // clear previous contents

                Class<?> msgC = null;
                try
                {
                    msgC = Class.forName("soc.message." + clsName);
                } catch (ClassNotFoundException e) {
                    failMsgs.put(clsName, "Could not load class");
                    continue;
                }

                // - find constructor
                final Class<?>[] fldTypes = ps.getConstructorFieldTypes();
                Constructor co = null;
                for (Constructor c : msgC.getDeclaredConstructors())
                {
                    if (Arrays.equals(fldTypes, c.getParameterTypes()))
                    {
                        co = c;
                        break;
                    }
                }
                if (co == null)
                {
                    failMsgs.put(clsName, "Could not find constructor for " + Arrays.toString(fldTypes));
                    continue;
                }

                // - TODO granular exception checks while running each test case (encode + decode)
                int ti = 0;
                for (ParsedMessageSpec.TestCase tc : ps.testCases)
                {
                    ++ti;

                    SOCMessage msg = (SOCMessage) co.newInstance(tc.args);
                    final String cmd = msg.toCmd();
                    if (! cmd.equals(tc.cmdStr))
                        sb.append
                            ("testcase " + ti + ": Expected \"" + tc.cmdStr + "\", toCmd() got \"" + cmd + "\"");

                    // TODO decode tc.cmdStr and check field contents through reflection
                }

                if (sb.length() != 0)
                    failMsgs.put(clsName, sb.toString());
            } catch (IOException e) {
                failMsgs.put(f.getName(), "Could not read: " + e);
            } catch (SecurityException e) {
                failMsgs.put(f.getName(), "Could not read: " + e);
            } catch (ParseException e) {
                failMsgs.put(f.getName(), "Parse failed: " + e.getMessage());
            } catch (Exception e) {
                failMsgs.put(f.getName(), "Exception: " + e);
            }
        }

        System.err.println("\nFound and tested " + nSpecs + " message specs\n");

        if (! failMsgs.isEmpty())
        {
            StringBuilder names = new StringBuilder();
            int n = 0;  // after 5 names, append ",..." once
            for (Map.Entry<String, String> ent: failMsgs.entrySet())
            {
                if (n < 5)
                {
                    if (n > 0)
                        names.append(',');
                    names.append(ent.getKey());
                }
                else if (n == 5)
                    names.append(",...");

                System.err.println("Failure for " + ent.getKey() + ": " + ent.getValue());
                ++n;
            }

            fail("Some classes failed: " + names);
        }

    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.net.TestMessageEncodeSpec");
    }

}