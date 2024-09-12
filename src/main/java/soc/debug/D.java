/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2012,2014,2020-2023 Jeremy D Monin <jeremy@nand.net>
 * Portions of this file Copyright (C) 2017-2018 Strategic Conversation (STAC Project) https://www.irit.fr/STAC/
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
 *<P>
 * Extended with 4 levels of importance: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL};
 * Depending on the level, call one of the debug methods to print out.
 */
public class D
{
    /**
     * Print out everything
     * @since 2.5.00
     */
    public static final int INFO = 0;

    /**
     * Print out warnings or above
     * @since 2.5.00
     */
    public static final int WARNING = 1;

    /**
     * Print out errors or fatals
     * @since 2.5.00
     */
    public static final int ERROR = 2;

    /**
     * Print out fatals only. NOTE: despite the name, fatals are exceptions that may or may not cause the application to crash.
     * This logger treats a {@code FATAL} like any other level, doesn't end the process or perform any extra actions.
     * @since 2.5.00
     */
    public static final int FATAL = 3;

    /**
     * The debug level, one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}.
     * Default set to INFO.
     * @since 2.5.00
     */
    static private int level = INFO;

    static public final boolean ebugOn = true;
    static private boolean enabled = true;

    /**
     * Set the debug level to one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}.
     * The default is {@code INFO}.
     * @throws IllegalArgumentException if level not in range {@link #INFO} - {@link #FATAL}
     * @since 2.5.00
     */
    public static void setLevel(int l)
        throws IllegalArgumentException
    {
        if ((l < INFO) || (l > FATAL))
            throw new IllegalArgumentException("level");

        level = l;
    }

    /**
     * Get the current debug level (one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}).
     * The default is {@code INFO}.
     * @return the current debug level
     * @since 2.5.00
     */
    public static int ebug_level()
    {
        return level;
    }

    /**
     * Enable debugging - start producing output.
     */
    public static final void ebug_enable()
    {
        enabled = true;
    }

    /**
     * Disable debugging - stop producing output.
     */
    public static final void ebug_disable()
    {
        enabled = false;
    }

    /**
     * Is debug currently enabled?
     * @return  true if debugging is currently enabled
     * @since 1.1.00
     */
    public static final boolean ebugIsEnabled()
    {
        return enabled;
    }

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     * @deprecated Use {@link #ebugPrintlnINFO(String)} added in v2.5.00
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
     * @deprecated Use {@link #ebugPrintlnINFO()} added in v2.5.00
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
     * @deprecated Use {@link #ebugPrintINFO(String)} added in v2.5.00
     */
    public static final void ebugPrint(String text)
    {
        if (enabled)
        {
            System.out.print(text);
        }
    }

    /**
     * Debug-println this info text;
     *
     * @param text DOCUMENT ME!
     * @since 2.5.00
     */
    public static final void ebugPrintlnINFO(String text)
    {
        if (enabled && level == INFO)
        {
            System.out.println(text);
        }
    }

    /**
     * Debug-println this info text;
     *
     * @param text DOCUMENT ME!
     * @since 2.5.00
     */
    public static final void ebugPrintlnINFO(String prefix, String text)
    {
        if (enabled && level == INFO)
        {
            System.out.println(prefix + ":" + text);
        }
    }

    /**
     * Debug-print this info text.
     * @since 2.5.00
     */
    public static final void ebugPrintlnINFO()
    {
        if (enabled && level == INFO)
        {
            System.out.println();
        }
    }

    /**
     * Debug-print this info text;
     *
     * @param text DOCUMENT ME!
     * @since 2.5.00
     */
    public static final void ebugPrintINFO(String text)
    {
        if (enabled && level == INFO)
        {
            System.out.print(text);
        }
    }

    /**
     * Debug-println this text; for compatibility with log4j.
     * Calls {@link #ebugPrintln(String)}.
     * @param text Text to debug-print
     */
    public final void debug(String text) { ebugPrintln(text); }

    /**
     * If debug is enabled, print the stack trace of this exception
     * @param ex Exception or other Throwable.  If null, will create an exception
     *           in order to force a stack trace.
     * @param prefixMsg Message for {@link #ebugPrintln(String)} above the exception,
     *                  or null; will print as:
     *                  prefixMsg + " - " + ex.toString
     * @since 1.1.00
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

    /**
     * Debug-println this "fatal" error, an exception that may or may not cause the application to crash soon.
     * If debug is enabled, print the stack trace of this exception.
     * This logger treats a {@code FATAL} like any other level, doesn't end the process or perform any extra actions.
     * @param ex Exception or other Throwable.  If null, will create an exception
     *           in order to force a stack trace.
     * @param prefixMsg Message for {@link #ebugPrintlnINFO(String)} above the exception,
     *                  or null; will print as:
     *                  prefixMsg + " - " + ex.toString
     * @since 2.5.00
     */
    public static final void ebugFATAL(Throwable ex, String prefixMsg)
    {
        ebugPrintStackTrace(ex, prefixMsg);
    }

    /**
     * Debug-println this warning text;
     * @param text Text to debug-print
     * @since 2.5.00
     */
    public static final void ebugWARNING(String text)
    {
        if (enabled && level <= WARNING)
        {
            System.out.println("WARN: " + text);
        }
    }

    /**
     * Debug-println this warning text;
     * @param text Text to debug-print
     * @since 2.5.00
     */
    public static final void ebugWARNING(String prefix, String text)
    {
        if (enabled && level <= WARNING)
        {
            System.out.println("WARN: " + prefix + " " + text);
        }
    }

    /**
     * Debug-println this error text;
     * @param text Text to debug-print
     * @since 2.5.00
     */
    public static final void ebugERROR(String text)
    {
        if (enabled && level <= ERROR)
        {
            System.out.println("ERR: " + text);
        }
    }

    /**
     * Debug-println this error text;
     * @param text Text to debug-print
     * @since 2.5.00
     */
    public static final void ebugERROR(String prefix,String text)
    {
        if (enabled && level <= ERROR)
        {
            System.out.println("ERR: " + prefix + " " + text);
        }
    }

}
