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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import soc.message.SOCMessage;

import socmisc.io.ParsedMessageSpec;

/**
 * Test whether a message can be encoded to and decoded from its specification.
 * Per-message specification and test cases use a protobuf-like syntax
 * given in a comment in the message type's java source which begins with
 * the token {@code jsettlers_message}.
 *<P>
 * Uses ThatMessageType.toCmd() to encode, and SOCMessage.toMsg(String)
 * to decode, with introspection to validate field contents.
 *<P>
 * For parsing the specification syntax, assumes {@code soc.message} package's
 * *.java are located in a certain relative directory when ran (TODO).
 *<P>
 * See {@link ParsedMessageSpec} class javadoc for more details.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class TestMessageEncodeSpec
{
    @BeforeClass
    public static void setup()
    {
        // ... TODO ...
        // - find the soc.message _source_ package
        // - try to parse spec in *.java there, besides SOCMessage itself
        //      Relative to this test src, it's ../../../../main/java/soc/message/
        // - run the test cases (encode + decode)
    }

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
               (Arrays.asList(new Object[]{"UA", "myname", "mypw", Integer.valueOf(1), null}),
                "999|UA,myname,1,\t,mypw"));
        TC.add(new ParsedMessageSpec.TestCase
               (Arrays.asList(new Object[]{"P", "myname", "", Integer.valueOf(1), "myhost"}),
                "999|P,myname,1,myhost,"));
        assertTrue("testCases contents correct", ps.testCases.equals(TC));

        final ArrayList<ParsedMessageSpec.MsgField> F = new ArrayList<ParsedMessageSpec.MsgField>();
        F.add(new ParsedMessageSpec.MsgField("role", String.class, (char) 1, false));
        F.add(new ParsedMessageSpec.MsgField("nickname", String.class, (char) 0, false));
        F.add(new ParsedMessageSpec.MsgField("authScheme", Integer.class, (char) 0, false));
        F.add(new ParsedMessageSpec.MsgField("host", String.class, '\t', false));
        F.add(new ParsedMessageSpec.MsgField("password", String.class, (char) 0, true));
        assertTrue("field list contents correct", ps.fields.equals(F));
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.net.TestMessageEncodeSpec");
    }

}