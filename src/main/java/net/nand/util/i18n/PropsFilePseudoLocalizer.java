/*
 * nand.net i18n utilities for Java: Property file pseudo-localizer.
 * This file Copyright (C) 2013 Jeremy D Monin <jeremy@nand.net>
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
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.fedorahosted.tennera.antgettext.StringUtil;

/**
 * Given a simple .properties file, pseudo-localize its values.
 * English filename.properties becomes filename_en_AA.properties;
 * other languages' filename_la.properties becomes filename_la_AA.properties.
 *<P>
 * Runs each property value through {@link StringUtil#pseudolocalise(String)} from the JBoss Ant-Gettext utilities.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class PropsFilePseudoLocalizer
{
    /**
     * Make a filename for a pseudo locale properties file.
     *
     * @param srcPropFilename  Source filename to localize from, ending with ".properties".
     *          English filename.properties becomes filename_en_AA.properties;
     *          other languages' filename_la.properties becomes filename_la_AA.properties.
     *          Source filename format filename_la_COUNTRY.properties is not supported.
     * @throws IllegalArgumentException  if {@code srcPropFilename} contains more than 1 underscore ('_')
     * @return  The pseudolocalized property file filename from {@code srcPropFilename}, see above for details
     */
    public static final String makePseudoPropFilename(final String srcPropFilename)
        throws IllegalArgumentException
    {
        String destPropFilename = StringUtil.removeFileExtension(srcPropFilename, ".properties");
        final int under = destPropFilename.lastIndexOf('_');
        if (under != -1)
        {
            if (-1 != destPropFilename.lastIndexOf('_', under - 1))
                throw new IllegalArgumentException("Too many underscores");

            destPropFilename = destPropFilename + "_AA.properties";
        } else {
            destPropFilename = destPropFilename + "_en_AA.properties";
        }

        return destPropFilename;
    }

    /**
     * Pseudolocalize a source file to a destination.  If the destination exists, it will be overwritten.
     * @param srcPropFilename  Source file to localize from, filename ending with ".properties"
     * @param destPropFilename  Pseudo filename to localize to, from {@link #makePseudoPropFilename(String)}
     * @throws IOException  if an error occurs reading or writing the files
     */
    public static void pseudoLocalizeFile(final File srcPropFile, final String destPropFilename)
        throws IOException
    {
        List<PropsFileParser.KeyPairLine> pairs = PropsFileParser.parseOneFile(srcPropFile);

        for (PropsFileParser.KeyPairLine pair : pairs)
            pair.value = StringUtil.pseudolocalise(pair.value);

        PropsFileWriter pfw = new PropsFileWriter(new File(destPropFilename));
        pfw.write(pairs, "This is a generated file: Pseudolocalized from " + srcPropFile.getName() + " on " + new Date());
        pfw.close();
    }

    /**
     * With 1 argument, pseudolocalize that filename's values.  With 0 or more than 1, print help message.
     *<P>
     * Return codes:
     *<UL>
     * <LI> 0 = success; the file was written, its filename was printed to {@link System#err}
     * <LI> 1 = help message was printed and exited, no file was written
     * <LI> 2 = problem with the source filename (too many underscores), no file was written
     * <LI> 4 = IO error was printed
     *</UL>
     * @param args  Single filename to pseudolocalize, with same rules as {@link #pseudoLocalizeFile(String)}
     */
    public static void main(String[] args)
    {
        if (args.length != 1)
        {
            System.err.println("Usage: PropsFilePseudoLocalizer sourcefilename.properties");
            System.err.println("    The source file will be pseudo-localized to a new file, depending on its current name:");
            System.err.println("    english source.properties -> source_en_AA.properties");
            System.err.println("    other   source_lang.properties -> source_lang_AA.properties");
            System.err.println("    other   source_lang_COUNTRY.properties -> not supported");
            System.exit(1);
        }

        try
        {
            final String pseudoPropFilename = makePseudoPropFilename(args[0]);
            pseudoLocalizeFile(new File(args[0]), pseudoPropFilename);
            System.err.println("Wrote " + pseudoPropFilename);
        }
        catch (IllegalArgumentException e)
        {
            System.err.println("Source filename too complex, use fewer underscores: " + args[0]);
            System.exit(2);
        }
        catch (IOException e)
        {
            System.err.println("I/O error occurred: " + e);
            System.exit(4);
        }
    }

}
