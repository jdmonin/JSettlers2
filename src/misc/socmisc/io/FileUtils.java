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

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Miscellaneous file-related methods.
 *
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @since 2.0.00
 */
public abstract class FileUtils
{
    /**
     * Find a nearby directory which must have certain parent directories.
     * For example, the {@code message} directory with parents {@code src/main/java/soc/message}.
     * The search starts in the current working directory.
     * @param pathSpec  Directory and its parents, formatted like {@code "src/main/java/soc/message"}
     *    without a leading or trailing slash
     * @param topDirAlsoContains  One or more other files/directories which must be located in the
     *    top-level directory alongside {@code pathSpec}'s first level ({@code "src"} in the example
     *    above), to help prevent false positive matches, or {@code null}
     */
    public static File findNearbyDirFromParents
        (final String pathSpec, final String[] topDirAlsoContains)
        throws IllegalArgumentException, IOException, SecurityException
    {
        final String[] pathDirs = pathSpec.split("/");
        if (pathDirs.length < 2)
            throw new IllegalArgumentException
                ("pathSpec requires multiple components, separated by /");

        final Set<String> topDirAlso;
        if (topDirAlsoContains != null)
        {
            topDirAlso = new HashSet<String>();
            Collections.addAll(topDirAlso, topDirAlsoContains);
        } else {
            topDirAlso = null;
        }

        // start in current directory. Until dir and also files are seen, move upward
        final String topDirName = pathDirs[0];
        File dir = new File(".").getCanonicalFile();
        do
        {
            File topDir = null;  // set if seen in dir
            Set<String> thisDirAlsoRemaining = null;

            for (File f : dir.listFiles())
            {
                String fn = f.getName();
                if (fn.equals(topDirName) && f.isDirectory())
                    topDir = f;
                else if ((topDirAlso != null) && topDirAlso.contains(fn))
                {
                    if (thisDirAlsoRemaining == null)
                        thisDirAlsoRemaining = new HashSet<String>(topDirAlso);
                    thisDirAlsoRemaining.remove(fn);
                }
            }
            if ((topDir != null) && ((topDirAlso == null) || thisDirAlsoRemaining.isEmpty()))
            {
                File subd = findNearbyDir_subdir(topDir, pathDirs, 1);
                if (subd != null)
                    return subd;   // <--- recursion found dir at end of full pathSpec ---
            }

            // move to parent
            File par = dir.getParentFile();
            if ((par == null) || par.equals(dir))
                dir = null;
            else
                dir = par;
        } while (dir != null);

        return null;
    }

    private static File findNearbyDir_subdir
        (final File dir, final String[] pathDirs, final int pathOffset)
        throws IOException, SecurityException
    {
        final String dirName = pathDirs[pathOffset];
        File[] dmatch = dir.listFiles(new FileFilter()
        {
            public boolean accept(File f)
            { return f.getName().equals(dirName) && f.isDirectory(); }
        });

        if ((dmatch == null) || (dmatch.length == 0))
            return null;

        if (pathOffset == pathDirs.length - 1)
            return dmatch[0];
        else
            return findNearbyDir_subdir(dmatch[0], pathDirs, pathOffset + 1);
    }

    /**
     * Read a UTF-8 text file into a String.
     * Assumes the file is sized in kilobytes not gigabytes and will fit into memory.
     * @param f  File to read; will call {@link File#length()} method to get size.
     *    If {@code f} doesn't exist, {@code length()} returns 0 and this method returns {@code ""}
     *    instead of throwing an exception.
     * @throws IOException if an error occurs opening or reading the file
     * @throws SecurityException if access is denied reading the file
     */
    public static String readUTF8File(final File f)
        throws IOException, SecurityException
    {
        final int L = (int) f.length();
        if (L <= 0)
            return "";

        byte[] buf = new byte[L];
        FileInputStream fis = null;
        try
        {
            fis = new FileInputStream(f);
            int offset = 0, n = 0;
            int block = (L > 8192) ? 8192 : L;  // avoids IndexOutOfBoundsException near end of file
            while (-1 != (n = fis.read(buf, offset, block)))
            {
                offset += n;
                int remain = L - offset;
                if (remain == 0)
                    break;
                else if (remain < block)
                    block = remain;
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        return new String(buf, "UTF-8");
    }

}
