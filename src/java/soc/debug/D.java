/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2012 Jeremy D Monin <jeremy@nand.net>
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
package soc.debug;

/**
 * Debug output; can be switched on and off.  All output goes to System.out.
 * soc.debug.D and {@link soc.disableDebug.D} have the same interface, to easily switch
 * debug on and off per class.
 *
 * @author $author$
 */
public class D
{
    static public final boolean ebugOn = true;
    static private boolean enabled = true;

    /**
     * DOCUMENT ME!
     */
    public static final void ebug_enable()
    {
        enabled = true;
    }

    /**
     * DOCUMENT ME!
     */
    public static final void ebug_disable()
    {
        enabled = false;
    }
    
    /**
     * Is debug currently enabled?
     * */
    public static final boolean ebugIsEnabled()
    {
        return enabled;
    }

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrintln(String text)
    {
        if (enabled)
        {
            System.out.println(text);
        }
    }

    /**
     * DOCUMENT ME!
     */
    public static final void ebugPrintln()
    {
        if (enabled)
        {
            System.out.println();
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrint(String text)
    {
        if (enabled)
        {
            System.out.print(text);
        }
    }

    /**
     * Debug-println this text; for compatability with log4j.
     * Calls {@link #ebugPrintln(String)}.
     * @param text Text to debug-print
     */
    public static final void debug(String text) { ebugPrintln(text); }

    /**
     * If debug is enabled, print the stack trace of this exception
     * @param ex Exception or other Throwable.  If null, will create an exception
     *           in order to force a stack trace.
     * @param prefixMsg Message for {@link #ebugPrintln(String)} above the exception,
     *                  or null; will print as:
     *                  prefixMsg + " - " + ex.toString
     */
    public static final void ebugPrintStackTrace(Throwable ex, String prefixMsg)
    {
        if (! enabled)
        {
            return;
        }

        if (ex == null)
        {
            try
            {
                int x = 0;
                int y = x / 0;
                System.out.print(y);
            } catch (Throwable th)
            {
                ex = th;
            }
        }
        if (prefixMsg != null)
        {
            StringBuilder sb = new StringBuilder(prefixMsg);
            sb.append(" - ");
            sb.append(ex.getClass().getName());  // also, stack trace will print ex.toString()
            final String det = ex.getMessage();  // some ex.toString don't include getMessage contents
            if (det != null)
            {
                sb.append(": ");
                sb.append(det);
            }
            ebugPrintln(sb.toString());
        }
        System.out.println("-- Exception stack trace begins -- Thread: " + Thread.currentThread().getName());
        ex.printStackTrace(System.out);

        /**
         * Look for cause(s) of exception
         */
        Throwable prev = ex;
        for ( Throwable cause = prev.getCause();    // NOTE: getCause is 1.4+
              ((cause != null) && (cause != prev));
               prev = cause )
        {
            System.out.println("** --> Nested cause exception: **");
            cause.printStackTrace(System.out);
        }
        System.out.println("-- Exception ends: " + ex.getClass().getName() + " --");
    }

}
