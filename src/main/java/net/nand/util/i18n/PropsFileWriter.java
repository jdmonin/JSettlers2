/*
 * nand.net i18n utilities for Java: Property file writer.
 * This file Copyright (C) 2013,2016 Jeremy D Monin <jeremy@nand.net>
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
 * The maintainer of this program can be reached at jeremy@nand.net
 */
package net.nand.util.i18n;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Write a properties file out to disk, in the same format used by the jsettlers project:
 * Valid ISO-8859-1 high-bit characters will be in the file as such, and not as <code>&#92;uXXXX</code> code escapes,
 * unlike Java's built-in {@link java.util.Properties#store(java.io.OutputStream, String)} method.
 *<P>
 * Usage:
 *<UL>
 * <LI> writer = new {@link #PropsFileWriter(File)} or {@link #PropsFileWriter(PrintWriter)};
 * <LI> writer.{@link #write(List, String) .write(pairs, filecomment)};
 * <LI> // Or: <BR>
 *      {@link #writeOne(PrintWriter, String, String, List, boolean) writeOne(writer, key, value, paircomment, spacedEquals)};<BR>
 *      writeOne(...); ...
 * <LI> writer.close();
 *</UL>
 *
 * @see PropsFileParser
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class PropsFileWriter
{
    /** An open PrintWriter for the output file; must have encoding {@code ISO-8859-1} */
    private PrintWriter pw;

    /**
     * For java properties file encoding, is "high-bit" character {@code c} valid in both ISO-8859-1 and ISO-8859-15?
     * If not, it should be escaped (<code>&#92;uXXXX</code>) in the output file.
     *<P>
     * If a character returns {@code true} here, its unicode {@code char} can be output as its ISO-8859-1 byte:
     * "ISO-8859-1 was incorporated as the first 256 code points ... of Unicode" -- Wikipedia ISO-8859-1 article
     *<P>
     * This function tests only "high-bit" characters (0x80 and above): Any character below {@code 00A0} returns false,
     * including ascii characters which are valid in ISO-8859-1.  Just use an if-statement to test the range
     * 0x20 to 0x7E (' ' to '~') instead.
     *<P>
     * In case of editing on a computer using the newer ISO-8859-15, the few ISO-8859-1 characters dropped from that
     * encoding return false.
     *
     * @param c  A non-ascii "high-bit" character, with unicode code point {@code 0080} or higher
     * @see <A href="http://en.wikipedia.org/wiki/Iso-8859-1">Wikipedia: ISO/IEC 8859-1</A>
     * @see <A href="http://en.wikipedia.org/wiki/ISO/IEC_8859-15">Wikipedia: ISO/IEC 8859-15</A>
     */
    public static final boolean isValidHighISO8859_1(final char c)
    {
        if ((c > 0xBE) && (c <= 0xFF))
            return true;
        if (c < 0xA0)
            return false;

        switch (c)
        {
        // per wiki, ISO-8859-15 changes from ISO-8859-1: replaces 0xA4 A6 A8 B4 B8 BC BD BE
        case 0xA4:
        case 0xA6:
        case 0xA8:
        case 0xB4:
        case 0xB8:
        case 0xBC:
        case 0xBD:
        case 0xBE:
            return false;
        default:
            return true;
        }
    }

    /**
     * Escape and append 1 special character to a StringBuilder.
     * @param sb  StringBuilder to append {@code c} onto
     * @param c  A character to escape; &#92;t &#92;r &#92;n and ' ' are appended as their 2-character text escapes,
     *           all others will be appended as &#92;uXXXX (4 hex digits).
     */
    public static final void appendEsc(StringBuilder sb, final char c)
    {
        sb.append('\\');
        if (c <= 0xFF)
        {
            if (c == ' ')
                sb.append(' ');
            else if (c == '\t')
                sb.append('t');
            else if (c == '\r')
                sb.append('r');
            else if (c == '\n')
                sb.append('n');
            else if (c <= 0x0F) {
                sb.append("u000");  // u000X
                sb.append(Integer.toHexString(c));
            } else {
                sb.append("u00");  // u00XX
                sb.append(Integer.toHexString(c));
            }
        } else {
            sb.append('u');
            if (c <= 0x0FFF)
                sb.append('0');  // u0XXX
            // else uXXXX
            sb.append(Integer.toHexString(c));
        }
    }

    /**
     * Examine a property value string, and escape any non-ISO-8859-1 unicode characters and leading whitespace.
     * @param val  Property value to unicode-escape
     * @return  Escaped {@code val} if escapes were needed, otherwise {@code val}
     */
    public static final String escValue(final String val)
    {
        if (val == null)
            return "";
        if (val.length() == 0)
            return val;

        final int L = val.length();
        StringBuilder esc = null;  // don't build it unless needed
        int i = 0;                 // index of chars examined so far
        char c;

        // Look for leading whitespace
        c = val.charAt(0);
        if (Character.isWhitespace(c))
        {
            esc = new StringBuilder();
            appendEsc(esc, c);

            for (i = 1, c = val.charAt(1);
                 Character.isWhitespace(c) && (i < L);
                 ++i, c = ((i < L) ? val.charAt(i) : ' '))
            {
                appendEsc(esc, c);
            }
        }
        // Postcondition: charAt(i) is not whitespace, or i is past end of val

        // Now examine the rest of the string
        for (; i < L; ++i)
        {
            c = val.charAt(i);
            final boolean normalChar = ((c >= ' ') && (c < 0x7F))
                || ((c >= 0xA0) && (c <= 0xFF) && isValidHighISO8859_1(c));

            if (normalChar)
            {
                if (esc != null)
                    esc.append(c);  // continue to build esc if we've started it
            } else {
                if (esc == null)
                {
                    // set up what we have so far; no escapes needed until now
                    esc = new StringBuilder(val.subSequence(0, i));  // exclude c, charAt(i)
                }
                appendEsc(esc, c);
            }
        }

        // Return the final string
        if (esc != null)
            return esc.toString();
        else
            return val;
    }

    /**
     * Write one key-value pair line to the properties file, making sure unicode escapes are re-escaped to <code>&#92;uXXXX</code>
     * where needed, otherwise keeping {@code val} unchanged and keeping high-bit ISO-8859-1 characters for easier viewing.
     *
     * @param pw  An open PrintWriter for the output file; must have encoding {@code ISO-8859-1}
     * @param key  Key to write; assumes no special characters to be escaped
     * @param val  Value to write; leading whitespace and special characters will be escaped;
     *             backslashes will not be doubled, assumes they are part of an escape sequence in {@code val}
     * @param comments  Comment lines to write above key=value, or {@code null}; each one must contain a leading {@code "# "}
     * @param spacedEquals  If true, If true, the key and value are separated by " = " instead of "="
     */
    public static final void writeOne
        (PrintWriter pw, final String key, final String val, final List<String> comments, final boolean spacedEquals)
    {
        if (comments != null)
            for (String c : comments)
                pw.println(c);

        if (key != null)
        {
            pw.println(key + ((spacedEquals) ? " = " : '=') + escValue(val));
        }
    }

    /**
     * Create a new PropsFileWriter using this open PrintWriter,
     * which must use the {@code "ISO-8859-1"} file encoding.
     */
    public PropsFileWriter(PrintWriter pw)
    {
        this.pw = pw;
    }

    /**
     * Create and open a new PropsFileWriter to this file.
     * @param pFile  Properties file to write
     * @throws FileNotFoundException  if the file can't be created, opened, or written, or is a unix special file
     * @throws SecurityException  if write access is denied
     * @throws UnsupportedEncodingException  if the {@code "ISO-8859-1"} file encoding is somehow not supported;
     *           this is the encoding used by Java properties files, so it should be available;
     *           this error is not expected to occur.
     */
    public PropsFileWriter(final File pFile)
        throws FileNotFoundException, SecurityException, UnsupportedEncodingException
    {
        pw = new PrintWriter(pFile, "ISO-8859-1");
            // may throw FileNotFoundException, SecurityException or UnsupportedEncodingException as per this constructor's javadoc
    }

    public void flush()
    {
        pw.flush();
    }

    public void close()
    {
        pw.flush();
        pw.close();
    }

    /**
     * Write these key-value pairs through the open writer.
     * @param pairs Key-value pairs to write, same format as {@link PropsFileParser#parseOneFile(File)}
     * @param fileComment  Optional single-line comment to place above output keys, or {@code null}; will prepend "# "
     */
    public void write(List<PropsFileParser.KeyPairLine> pairs, final String fileComment)
    {
        if (fileComment != null)
            pw.println("# " + fileComment);

        for (PropsFileParser.KeyPairLine pair : pairs)
            writeOne(pw, pair.key, pair.value, pair.comment, pair.spacedEquals);
    }

}
