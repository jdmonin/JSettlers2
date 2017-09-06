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

package socmisc.io;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soc.message.SOCMessage;

/**
 * Parse the soc.message specification format of a java source file.
 * Gracefully handle a source file which doesn't contain the {@code jsettlers_message} token.
 * Throw {@link ParseException} if token found but other parse errors occur.
 *<P>
 * Per-message specification and test cases use a protobuf-like syntax
 * given in a comment in the message type's java source which begins with
 * the token {@code jsettlers_message}.
 *<P>
 * For test cases see {@link soctest.net.TestMessageEncodeSpec}.
 *
 *<H2>Spec Format example</H2>
 * <pre><tt>
 * /*
 *  * jsettlers_message: AUTHREQUEST {
 *  *   string role;
 *  *   string nickname;
 *  *   int authScheme;
 *  *   string host [empty = '\t'];
 *  *   string password [rest_of_message];  // pw may contain delimiter characters
 *  * }
 *  * constructor_fields(role, nickname, password, authScheme, host);
 *  * testcase ("UA", "myname", "mypw", 1, null)
 *  *   = "UA,myname,1,\t,mypw";
 *  * testcase ("P", "myname", "", 1, "myhost")
 *  *   = "P,myname,1,myhost,";
 *  *&sol;
 * </tt></pre>
 * <UL>
 *   <LI> The entire spec is inside a standard multi-line java comment
 *   <LI> The {@code jsettlers_message:} token is followed by the message's type constant,
 *        {@link Message#AUTHREQUEST} here
 *   <LI> The field list is given within curly braces, one per line
 *   <LI> Each field declaration line ends with {@code ;} and an optional single-line {@code //} comment
 *   <LI> Current field types: string, int
 *   <LI> Field names correspond to the message class's getters or final public fields
 *   <LI> Can optionally specify the field's placeholder value sent when {@code empty} or null
 *   <LI> A few messages have final fields which can contain delimiter characters,
 *        noted here with {@code [rest_of_message]}
 *   <LI> Field's {@code //} comment can't contain <tt>}</tt>, to simplify the parser
 *   <LI> Outside of the field list, single-line {@code //} comments aren't allowed
 *   <LI> If message constructor takes fields in a different order than the spec's field list,
 *        give the constructor order using {@code constructor_fields(...);}.
 *        Otherwise omit {@code constructor_fields}.
 *   <LI> One or more test cases are required
 *   <LI> Test case format: List of arguments in constructor order, expected encoded output
 *        from {@code SOCMessage#toCmd()}. Arguments are ints, double-quoted strings, or null.
 *        Strings won't contain commas because comma is the encoded field delimiter.
 *   <LI> The expected output is a double-quoted string literal. The required prefix will be
 *        automatically added: message type constant's integer value + {@code |} delimiter.
 *        String can contain tabs as {@code \t}, newlines as {@code \n}.
 * </UL>
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public class ParsedMessageSpec
{
    private static final Pattern PAT_JSETTLERS_MESSAGE_SPEC
        = Pattern.compile
            ("\\s/\\*[*\\s]+jsettlers_message:\\s*(\\w+)\\s*\\{\\s*([^}]+)\\s*\\}[*\\s]+"
             + "(constructor_fields\\s*\\(\\s*(\\w[^)]+)\\s*\\)\\s*;)?[*\\s]+"
             + "(testcase\\s*\\(\\s*([^)\\n]+)\\s*\\)[*\\s]*=[*\\s]*\"([^\"\\n]+)\";[*\\s]*)+"
             + "[*\\s]*\\*/",
             Pattern.MULTILINE);

    private static final Pattern PAT_MESSAGE_FIELD
        = Pattern.compile
            ("^[*\\s]*(\\w+)\\s+(\\w+)(\\s+\\[\\s*(\\w+)(\\s*=\\s*(\\S+))?\\s*\\]\\s*)?;\\s*(//.*)?$",
             Pattern.MULTILINE);

    private static final Pattern PAT_TESTCASE
        = Pattern.compile
            ("[*\\s]*(testcase\\s*\\(\\s*([^)\\n]+)\\s*\\)[*\\s]*=[*\\s]*\"([^\"\\n]+)\";[*\\s]*)",
             Pattern.MULTILINE);

    /** Message type's class name within {@code soc.message} package, from constructor */
    public final String className;

    /**
     * Message type's constant name within {@link SOCMessage},
     * like {@link SOCMessage#AUTHREQUEST "AUTHREQUEST"}.
     * For value see {@link #msgtypeConstValue}.
     * {@code null} if no spec token found in file.
     */
    public String msgtypeConstName;

    /** Value of {@link #msgtypeConstName} field within {@link SOCMessage}, if any */
    public int msgtypeConstValue;

    /**
     * Each {@link MsgField} and its options (empty = ?, rest_of_message),
     * or {@code null} if no spec token found in file
     */
    public List<MsgField> fields;

    /** Constructor field-order list, if differs from list of fields, or {@code null} */
    public final String[] construcFields;

    /** Map of field names to details */
    public final Map<String, MsgField> fieldMap;

    /**
     * 1 or more {@link ParsedMessageSpec.TestCase} specs (constructor arg values, expected output string),
     * or {@code null} if no spec token found in file
     */
    public List<TestCase> testCases;

    /**
     * Parse a message specification from this message class java source.
     * If source doesn't contain the {@code jsettlers_message:} spec token,
     * all fields except {@code className} will be {@code null}.
     *<P>
     * See {@code ParsedMessageSpec class javadoc} for spec format details.
     * @param className  Message class name within {@code soc.message}
     * @param src  Message java source to parse
     * @throws ParseException if token found but other parse errors occur.
     */
    public ParsedMessageSpec(String className, String src)
        throws ParseException
    {
        this.className = className;

        Matcher m = PAT_JSETTLERS_MESSAGE_SPEC.matcher(src);
        if (! m.find())
        {
            int i = src.indexOf("jsettlers_message:");
            if (i >= 0)
                throw new ParseException
                    ("Contains 'jsettlers_message:' but could not parse message spec: "
                     + className, i);

            construcFields = null;
            fieldMap = null;
            return;
        }

        final int specBeginPos = m.start();

        msgtypeConstName = m.group(1);  // "AUTHREQUEST"

        // msgtypeConstValue from soc.message.SOCMessage reflection
        try
        {
            msgtypeConstValue
                = soc.message.SOCMessage.class.getDeclaredField(msgtypeConstName).getInt(null);
        }
        catch (NoSuchFieldException e)
        {
            throw new ParseException
                ("Const named " + msgtypeConstName + " not found in soc.message.SOCMessage for: "
                 + className, specBeginPos);
        }
        catch (Exception e)
        {
            ParseException pe = new ParseException
                ("Unexpected exception while reading constant field soc.message.SOCMessage."
                 + msgtypeConstName + " for: " + className, specBeginPos);
            pe.initCause(e);
            throw pe;
        }

        final String rawMatch = m.group(0);
        final String rawFieldList = m.group(2);
            // "*   string role; *  ... *  string password rest_of_message];  // pw may ..."
        final String rawConstrucFields = m.group(4);
            // null or "role, nickname, password, authScheme, host"

        construcFields = (rawConstrucFields != null)
            ? rawConstrucFields.split("\\s*,\\s*")
            : null;
        fields = parseMsgFields(rawFieldList, className, specBeginPos);
        testCases = parseTestCases(rawMatch, className, msgtypeConstValue, specBeginPos);

        fieldMap = new HashMap<String, MsgField>();
        final int fieldCount = fields.size();
        for (int i = 0; i < fieldCount; ++i)
        {
            final MsgField f = fields.get(i);
            fieldMap.put(f.fname, f);
        }

        if (construcFields != null)
        {
            for (int i = 0; i < construcFields.length; ++i)
                if (! fieldMap.containsKey(construcFields[i]))
                    throw new ParseException
                        ("constructor_fields has undeclared field " + construcFields[i], specBeginPos);
            if (construcFields.length != fieldCount)
                throw new ParseException
                    ("constructor_fields must include all declared fields", specBeginPos);
        }
    }

    private static List<MsgField> parseMsgFields
        (final String rawFieldList, final String className, final int specBeginPos)
        throws ParseException
    {
        Matcher m = PAT_MESSAGE_FIELD.matcher(rawFieldList);
        if (! m.find())
            throw new ParseException("Could not parse field list: " + className, specBeginPos);

        final ArrayList<MsgField> fields = new ArrayList<MsgField>();
        do
        {
            final String ft = m.group(1), fname = m.group(2);
            final Class ftype;
            if (ft.equals("int"))
                ftype = Integer.TYPE;
            else if (ft.equals("string"))
                ftype = String.class;
            else
                throw new ParseException
                    ("Unknown field type " + ft + " for " + fname + ": " + className,
                     specBeginPos + m.start(1));

            char emptyChar = (char) 0;
            boolean isRestOfMessage = false;
            if (m.group(3) != null)
            {
                String optName = m.group(4), optVal = m.group(6);
                if (optName.equals("rest_of_message"))
                {
                    isRestOfMessage = true;
                    if (optVal != null)
                        throw new ParseException
                            ("Option rest_of_message takes no value for " + fname + ": " + className,
                             specBeginPos + m.start(6));
                }
                else if (optName.equals("empty"))
                {
                    if (optVal == null)
                        throw new ParseException
                            ("Option empty requires value for " + fname + ": " + className,
                             specBeginPos + m.start(4));
                    try
                    {
                        emptyChar = parseChar(optVal);
                    } catch (ParseException e) {
                        throw new ParseException
                            ("Option empty: bad value: " + e.getMessage()
                             + " for " + fname + ": " + className,
                             specBeginPos + m.start(6));
                    }
                } else {
                    throw new ParseException
                        ("Unknown option " + optName + " for " + fname + ": " + className,
                         specBeginPos + m.start(4));
                }
            }

            fields.add(new MsgField(fname, ftype, emptyChar, isRestOfMessage));
        } while (m.find());

        return fields;
    }

    private static List<TestCase> parseTestCases
        (final String rawCasesList, final String className, final int msgtype, final int specBeginPos)
        throws ParseException
    {
        Matcher m = PAT_TESTCASE.matcher(rawCasesList);
        if (! m.find())
            throw new ParseException("Could not parse testcases: " + className, specBeginPos);

        final List<TestCase> testCases = new ArrayList<TestCase>();
        final String pfx = Integer.toString(msgtype) + '|';
        do
        {
            // args may be quoted strings; won't contain commas because comma is the encoded field delimiter
            String[] argsRaw = m.group(2).split("\\s*,\\s*");
            Object[] args = new Object[argsRaw.length];
            for (int i = 0; i < argsRaw.length; ++i)
            {
                String arg = argsRaw[i];
                if (arg.equals("null"))
                {
                    args[i] = null;
                }
                else if (arg.charAt(0) == '"')
                {
                    int L = arg.length();
                    if ((L < 2) || (arg.charAt(L - 1) != '"'))
                        throw new ParseException
                            ("Could not parse testcase quoted-string argument: " + arg + ": "
                             + className, specBeginPos + m.start(2));
                    args[i] = arg.substring(1, L - 1);
                } else {
                    try
                    {
                        args[i] = Integer.valueOf(Integer.parseInt(arg));
                    } catch (NumberFormatException e) {
                        throw new ParseException
                            ("Could not parse testcase int argument: " + arg + ": " + className,
                             specBeginPos + m.start(2));
                    }
                }
            }

            String outp = m.group(3).replaceAll("\\\\t", "\t").replaceAll("\\\\n", "\n");
            // TODO do we need to handle \\\ ?
            int i = outp.indexOf('\\');
            if (i != -1)
            {
                ++i;
                throw new ParseException
                    ("testcase contained unknown escape \\" + outp.charAt(i), i);
            }
            testCases.add(new TestCase(args, pfx + outp));

        } while (m.find());

        return testCases;
    }

    public static final char parseChar(String lit)
        throws ParseException
    {
        // see also JLS 3.10.4 Character Literals

        int L = lit.length();
        if ((L == 0) || (lit.charAt(0) != '\'') || (lit.charAt(L - 1) != '\''))
            throw new ParseException("Char literal requires single quotes ('')", 0);

        char c = lit.charAt(1);
        if (L == 3)  // "'x'"
        {
            if ((c == '\'') || (c == '\\'))
                throw new ParseException("Badly formed char literal", 0);
            return c;
        }
        if (c != '\\')
            throw new ParseException("Char literal is too long", 0);

        c = lit.charAt(2);  // after \

        if ((c >= '0') && (c <= '7'))
        {
            lit = lit.substring(2, L - 1);
            try
            {
                int oc = Integer.parseInt(lit, 8);
                if (oc <= 0377)
                    return (char) oc;
                throw new ParseException("Bad octal value " + lit + ", max is 0377", 0);
            } catch (NumberFormatException e) {
                throw new ParseException("Can't parse octal value " + lit, 0);
            }
        }

        if (c == 'u')
        {
            if (L < 5)
                throw new ParseException("Missing value after \\u", 0);
            lit = lit.substring(3, L - 1);
            try
            {
                int hex = Integer.parseInt(lit, 16);
                if (hex <= 0xffff)
                    return (char) hex;
                throw new ParseException("Bad unicode value " + lit + ", max is ffff", 0);
            } catch (NumberFormatException e) {
                throw new ParseException("Can't parse unicode hex value " + lit, 0);
            }
        }

        if (L == 4)
        {
            switch (c)
            {
            case 'b':  c = '\b';  break;
            case 'f':  c = '\f';  break;
            case 'n':  c = '\n';  break;
            case 'r':  c = '\r';  break;
            case 't':  c = '\t';  break;
            case '"':   break;   // already "
            case '\'':  break;   // already '
            case '\\':  break;   // already \
            default:
                throw new ParseException("Unknown char escape \\" + c, 0);
            }

            return c;
        }

        throw new ParseException("Char literal is too long", 0);
    }

    /**
     * Get the message class's field parameter types ({@link String}.class, {@link Integer#TYPE}).
     * If {@link #construcFields} is used, field types are returned in that order,
     * otherwise in the order {@link #fields} were declared in the spec.
     * Generic <tt>&lt;?&gt;</tt> is to match java reflection constructor.getParameterTypes().
     */
    public Class<?>[] getConstructorFieldTypes()
    {
        final int n = fields.size();
        Class<?>[] typ = new Class<?>[n];

        if (construcFields == null)
            for (int i = 0; i < n; ++i)
                typ[i] = fields.get(i).ftype;
        else
            for (int i = 0; i < construcFields.length; ++i)
                typ[i] = fieldMap.get(construcFields[i]).ftype;

        return typ;
    }

    public static class MsgField
    {
        /** field name, for getter introspection */
        public final String fname;

        /** field type: {@link String}.class, {@link Integer#TYPE} */
        public final Class ftype;

        /** if not 0, placeholder character (like {@code '\t'})
         *  sent over the network when field is empty or null
         */
        public final char emptyChar;

        /** If true, the rest of the message is this field's contents, including any delimiter chars */
        public final boolean isRestOfMessage;

        public MsgField
            (final String fname, final Class ftype, final char emptyChar, final boolean isRestOfMessage)
        {
            this.fname = fname;
            this.ftype = ftype;
            this.emptyChar = emptyChar;
            this.isRestOfMessage = isRestOfMessage;
        }

        public boolean equals(final Object o)
        {
            if ((o == null) || ! (o instanceof MsgField))
                return super.equals(o);

            final MsgField f = (MsgField) o;
            return fname.equals(f.fname) && ftype.equals(f.ftype)
                && (emptyChar == f.emptyChar) && (isRestOfMessage == f.isRestOfMessage);
        }
    }

    public static class TestCase
    {
        /** Each String or Integer argument to constructor */
        public final Object[] args;

        /**
         * Expected result of calling MessageType.toCmd(),
         * including the required prefix not given in spec text:
         * message type constant's integer value + {@code |} delimiter
         */
        public final String cmdStr;

        public TestCase(final Object[] args, final String cmdStr)
        {
            this.args = args;
            this.cmdStr = cmdStr;
        }

        public boolean equals(final Object o)
        {
            if (o == null)
                return false;
            return
                (o instanceof TestCase)
                ? cmdStr.equals(((TestCase) o).cmdStr) && Arrays.equals(args, ((TestCase) o).args)
                : super.equals(o);
        }
    }

}