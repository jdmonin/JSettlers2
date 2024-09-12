/*
 * nand.net i18n utilities for Java: Property file editor for translators (side-by-side source and destination languages).
 * This file Copyright (C) 2013,2016,2019,2021 Jeremy D Monin <jeremy@nand.net>
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
 **/
package net.nand.util.i18n;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a parsed properties file.
 *<P>
 * Usage:
 *<UL>
 * <LI> Call {@link #parseOneFile(File)} to read and parse the file.
 * <LI> Read or edit contents as needed.
 * <LI> To save changes, use {@link PropsFileWriter}.
 *</UL>
 * See {@link ParsedPropsFilePair} to work with two related property files with similar sets of keys.
 *<P>
 * Remember that {@code .properties} bundle files are encoded not in {@code UTF-8} but in {@code ISO-8859-1}:
 *<UL>
 * <LI> <A href="https://docs.oracle.com/javase/6/docs/api/java/util/Properties.html"
 *       >java.util.Properties</A> (Java 6)
 * <LI> <A href="http://stackoverflow.com/questions/4659929/how-to-use-utf-8-in-resource-properties-with-resourcebundle"
 *       >Stack Overflow: How to use UTF-8 in resource properties with ResourceBundle</A> (asked on 2011-01-11)
 *</UL>
 * Characters outside that encoding must use <code>&#92;uXXXX</code> code escapes.
 *
 * @see PropsFileWriter
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class PropsFileParser
{
    /**
     * If a key contains this marker {@code "_nolocaliz"}, it should be present only in the
     * source language (something.properties), and probably shouldn't be localized (something_lang.properties).
     *<P>
     * Before v1.2.1 this was {@code KEY_PREFIX_NO_LOCALIZE}, and editor checked for it only at
     * start of key names.
     */
    public static final String KEY_MARKER_NO_LOCALIZE = "_nolocaliz";

    /**
     * Parse one properties file.  May include a header comment (separated by blank line(s) from the first key line),
     * may include an ending comment after the last key line.
     * @param pFile  File to parse
     * @return  the file entries as a List.
     *       If the file starts with a header comment, the first list entry will have a comment and null key and value.
     *       If the file ends in a comment, the last list entry will have a comment and null key and value.
     * @throws IOException  If file not found, cannot be read, etc.
     * @throws SecurityException  if read access is denied
     * @throws UnsupportedEncodingException  if the {@code "ISO-8859-1"} file encoding is somehow not supported;
     *           this is the encoding used by Java properties files, so it should be available;
     *           this error is not expected to occur.
     */
    public static List<KeyPairLine> parseOneFile(final File pFile)
        throws IOException, SecurityException, UnsupportedEncodingException
    {
        return parseOneFile(pFile, null);
    }

    /**
     * Parse one properties file.  May include a header comment (separated by blank line(s) from the first key line),
     * may include an ending comment after the last key line.
     * @param pFile  File to parse
     * @param dupeKeys  Output param for noting any duplicate key names and their values, or {@code null}.
     *     Key = each key seen more than once while parsing, value = values for that key.
     *    <P>
     *     For structure details see {@link #findDuplicateKeys(List, Map)},
     *     including special case of 'duplicates' with same value.
     * @return  the file entries as a List.
     *       If the file starts with a header comment, the first list entry will have a comment and null key and value.
     *       If the file ends in a comment, the last list entry will have a comment and null key and value.
     * @throws IOException  If file not found, cannot be read, etc.
     * @throws SecurityException  if read access is denied
     * @throws UnsupportedEncodingException  if the {@code "ISO-8859-1"} file encoding is somehow not supported;
     *           this is the encoding used by Java properties files, so it should be available;
     *           this error is not expected to occur.
     */
    public static List<KeyPairLine> parseOneFile(final File pFile, final Map<String, String> dupeKeys)
        throws IOException, SecurityException, UnsupportedEncodingException
    {
        List<KeyPairLine> ret = new ArrayList<KeyPairLine>();

        BufferedReader fr = new BufferedReader
            (new InputStreamReader(new FileInputStream(pFile), "ISO-8859-1"));  // bundle encoding is not UTF-8
        List<String> headerComment = null;
        List<String> comment = null;
        boolean firstKeySeen = false;  // true when we reach a line in the file that has a key

        do
        {
            final String L;
            try
            {
                L = fr.readLine();  // excludes trailing \n or \r
            } catch (IOException ioe) {
                try { fr.close(); }  catch (IOException ioeclose) {}
                throw ioe;
            }
            if (L == null)
                break;  // end of file

            if ((! firstKeySeen) && (L.trim().length() == 0))
            {
                // At top of file, if we have a blank line, preceding comment lines become part of headerComment.

                if (headerComment == null)
                    headerComment = new ArrayList<String>();

                if ((comment != null) && ! comment.isEmpty())
                {
                    headerComment.addAll(comment);
                    comment.clear();
                }
                headerComment.add("");  // blank line
            }

            else if ((L.length() == 0) || (L.charAt(0) == '#'))
            {
                if (comment == null)
                    comment = new ArrayList<String>();
                comment.add(L);
            } else {
                final int ieq = L.indexOf('=');  // assumes keyname won't have an escaped = in it
                if (ieq != -1)
                {
                    final String key = L.substring(0, ieq).trim();
                    if (key.length() > 0)
                    {
                        CharSequence val = L.subSequence(ieq + 1, L.length());
                        final boolean spacedEquals = Character.isWhitespace(L.charAt(ieq - 1));

                        // trim leading spaces from val, but not trailing spaces
                        if ((val.length() > 0) && Character.isWhitespace(val.charAt(0)))
                        {
                            final int vlen = val.length();
                            int i;
                            for (i = 1; (i < vlen) && Character.isWhitespace(val.charAt(i)); ++i)
                                ;  // iterate

                            if (i < vlen)
                                val = val.subSequence(i, vlen);
                            else
                                val = "";
                        }

                        // look for escaped leading spaces
                        if ((val.length() > 0) && (val.charAt(0) == '\\') && (val.charAt(1) == ' '))
                        {
                            // What about other backslash escapes?
                            // Should note somewhere, we want to have \r \n \t as 2 characters in editor, not as newlines or tabs
                            final int vlen = val.length();
                            StringBuilder spc = new StringBuilder(" ");
                            int i;
                            for (i = 2; (i+1 < vlen) && (val.charAt(i) == '\\') && (val.charAt(i+1) == ' '); i += 2)
                                spc.append(' ');

                            if (i < vlen)
                            {
                                spc.append(val.subSequence(i, vlen));
                                val = spc;
                            } else {
                                val = "";
                            }
                        }

                        String valStr = val.toString();
                        if (valStr.contains("\\u"))
                            valStr = unescapeUnicodes(valStr);
                        if (valStr.trim().length() == 0)  // null for whitespace-only entries
                            valStr = null;

                        if (! firstKeySeen)
                        {
                            firstKeySeen = true;
                            if ((headerComment != null) && ! headerComment.isEmpty())
                                ret.add(new KeyPairLine(headerComment));
                        }

                        ret.add(new KeyPairLine(key, valStr, comment, spacedEquals));
                        comment = null;  // Don't share comment reference with next KeyPairLine
                    } else {
                        // TODO malformed: 0-length key
                    }
                } else {
                    // TODO malformed
                }
            }
        }
        while (true);  // loop body will break out at EOF
        fr.close();

        if ((comment != null) && ! comment.isEmpty())
            ret.add(new KeyPairLine(comment));

        if (dupeKeys != null)
            findDuplicateKeys(ret, dupeKeys);

        return ret;
    }

    /**
     * Search a list of key-pair lines for any duplicates, filling the {@code dupeKeys} map.
     * @param kpLines  Key-pair lines to search for duplicates, as returned by {@link #parseOneFile(File)}.
     * @param dupeKeys  Output param for noting any duplicate key names and their values.
     *     This should be empty when called.
     *    <BR>
     *     Key = each key seen more than once while parsing, value = values for that key.
     *    <BR>
     *     Because this is only for display/warning purposes, to simplify structure
     *     the duplicate values are kept as a string separated by ",".
     *    <P>
     *     <B>Special case:</B> If a key appears more than once with exactly the
     *     same value, it won't be considered a dupe and won't be placed in this map.
     */
    public static void findDuplicateKeys(final List<KeyPairLine> kpLines, final Map<String, String> dupeKeys)
    {
        /** Each key's first-seen value, to place both values into dupeKeys */
        final Map<String, String> kSeen = new HashMap<String, String>();

        for (KeyPairLine kp : kpLines)
        {
            final String key = kp.key;
            if (key == null)
                continue;

            final String val = kp.value;
            final String val0 = kSeen.get(key);
            if (val0 == null)
            {
                kSeen.put(key, val);
            }
            else if (! val.equals(val0))
            {
                // found duplicate key with different value

                String dvals = dupeKeys.get(key);
                if (dvals == null)
                    dvals = val0 + ", " + val;
                else
                    dvals = dvals + ", " + val;

                dupeKeys.put(key, dvals);
            }
        }
    }

    /**
     * Un-escape <code>&#92;uXXXX</code> sequences into unicode characters.
     * @param valStr  String containing unicode escape sequences
     * @return  Un-escaped string
     */
    private static String unescapeUnicodes(String valStr)
    {
        StringBuilder ret = new StringBuilder();
        int iCopied = 0;  // have copied up to, but not including, this index into ret

        int iEsc = valStr.indexOf("\\u");
        while (iEsc != -1)
        {
            // before unescape, copy valStr up to, but not including, iEsc into ret
            ret.append(valStr.substring(iCopied, iEsc));
            iCopied = iEsc + 6;  // skip the esc sequence

            ret.append((char) (Integer.parseInt(valStr.substring(iEsc + 2, iEsc + 6), 16)));  // ? appendCodePoint

            // look for the next unescape
            iEsc = valStr.indexOf("\\u", iEsc + 6);
        }

        if (iCopied < valStr.length())
        {
            ret.append(valStr.substring(iCopied, valStr.length()));
        }

        return ret.toString();
    }

    //
    // Nested Classes
    //

    /** Parsed key-pair line from one properties file; includes its preceding {@link #comment} lines, if any. */
    public static final class KeyPairLine
    {
        /** key, or {@code null} for any comment at the end of the file */
        public String key;

        /** value, if {@code key != null}. If the value is empty or is only whitespace, will be {@code null}. */
        public String value;

        /** comment lines, if any, or {@code null}; each one contains leading {@code "# "} */
        public List<String> comment;

        /** If true, the key and value are separated by " = " instead of "=".
         *  This is tracked to minimize whitespace changes when editing a properties file.
         */
        public boolean spacedEquals;

        /**
         * Line with a key and value, and optionally a comment.
         * @param key  Key, or {@code null} for any comment at the end of the file.
         *            If {@code null}, the {@link #PropsFileParser(List)} constructor might be more convenient.
         * @param value  Value, if {@code key != null}
         * @param comment  Comment line(s), or {@code null} for a blank line.
         *            For efficiency the contents aren't copied, an object reference is stored in the KeyPairLine;
         *            be sure to not change the {@code comment} list contents after calling this.
         * @param spacedEquals  {@link #spacedEquals}: If true, the key and value are separated by " = " instead of "=".
         */
        public KeyPairLine(final String key, final String value, final List<String> comment, final boolean spacedEquals)
        {
            this.key = key;
            this.value = value;
            this.comment = ((comment == null) || comment.isEmpty()) ? null : comment;
            this.spacedEquals = spacedEquals;
        }

        /**
         * Line with a comment, or blank line; key and value are {@code null}.
         * @param comment  Comment line(s), or {@code null} for a blank line.
         *            For efficiency the contents aren't copied, an object reference is stored in the KeyPairLine;
         *            be sure to not change the {@code comment} list contents after calling this.
         */
        public KeyPairLine(final List<String> comment)
        {
            this.comment = ((comment == null) || comment.isEmpty()) ? null : comment;
        }

        public final String toString()
        {
            return "{key=" + key + ", value=#" + value + "#, comment=" + comment + "}";
        }
    }

}
