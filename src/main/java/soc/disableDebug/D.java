/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2007-2009,2014,2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.disableDebug;

/**
 * Debug output; the disabled class is always off.
 * {@link soc.debug.D} and soc.disableDebug.D have the same interface, to easily switch
 * debug on and off per class.
 *<P>
 * Extended with 4 levels of importance: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL};
 * Depending on the level, call one of the debug methods to print out.
 */
public class D
{
    /**
     * Print out everything
     * @since 2.4.50
     */
    public static final int INFO = 0;

    /**
     * Print out warnings or above
     * @since 2.4.50
     */
    public static final int WARNING = 1;

    /**
     * Print out errors or fatals
     * @since 2.4.50
     */
    public static final int ERROR = 2;

    /**
     * Print out fatals only. NOTE: despite the name, fatals are exceptions that may or may not cause the application to crash
     * @since 2.4.50
     */
    public static final int FATAL = 3;

    static public final boolean ebugOn = false;
    // static private boolean enabled = false;

    /**
     * The debug level, one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}
     * Default set to WARNING.
     * Doesn't affect anything as debug is off
     * @since 2.4.50
     */
    static private int level = WARNING;

    /**
     * Changes the debug level to one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL}
     * The default is WARNING.
     * @throws IllegalArgumentException if level not in range {@code INFO} - {@code FATAL}
     * @since 2.4.50
     */
    public static void setLevel(int l)
        throws IllegalArgumentException
    {
        if ((l < INFO) || (l > FATAL))
            throw new IllegalArgumentException("level");

        level = l;
    }

    /**
     * Get the current debug level (one of: {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #FATAL})
     * @return the current debug level
     * @since 2.4.50
     */
    public static int ebug_level()
    {
        return level;
    }

    /**
     * Does nothing, since this is the disabled version.
     */
    public static final void ebug_enable() {}

    /**
     * Always disabled; does nothing, since this is the disabled version.
     */
    public static final void ebug_disable() {}

    /**
     * Is debug currently enabled?
     * Always returns false as debug is off
     * @since 1.1.00
     */
    public static final boolean ebugIsEnabled()
    {
        return false;
    }

   /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     * @deprecated Use {@link #ebugPrintlnINFO(String)} added in v2.4.50
     */
    public static final void ebugPrintln(String text) {}

    /**
     * DOCUMENT ME!
     * @deprecated Use {@link #ebugPrintlnINFO()} added in v2.4.50
     */
    public static final void ebugPrintln() {}

   /**
     * Does nothing as debug is off
     *
     * @param text DOCUMENT ME!
     */
    public static final void ebugPrintlnINFO(String text) {}

    /**
     * Does nothing as debug is off
     *
     * @param text DOCUMENT ME!
     * @since 2.4.50
     */
    public static final void ebugPrintlnINFO(String prefix, String text) {}

    /**
     * Does nothing as debug is off
     * @since 2.4.50
     */
    public static final void ebugPrintlnINFO() {}

    /**
     * If debug is enabled, print the stack trace of this exception
     * @param ex Exception or other Throwable
     * @param prefixMsg Message for {@link #ebugPrintln(String)} above the exception,
     *                  or null
     * @since 1.1.00
     */
    public static final void ebugPrintStackTrace(Throwable ex, String prefixMsg) {}

    /**
     * Does nothing as debug is off
     * @param ex Exception or other Throwable
     * @param prefixMsg Message for {@link #ebugPrintlnINFO(String)} above the exception,
     *                  or null
     * @since 2.4.50
     */
    public static final void ebugFATAL(Throwable ex, String prefixMsg) {}

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     * @deprecated Use {@link #ebugPrintINFO(String)} added in v2.4.50
     */
    public static final void ebugPrint(String text) {}

    /**
     * DOCUMENT ME!
     *
     * @param text DOCUMENT ME!
     * @since 2.4.50
     */
    public static final void ebugPrintINFO(String text) {}

    /**
     * Debug-println this text; for compatibility with log4j.
     * Calls {@link #ebugPrintln(String)}.
     * @param text Text to debug-print
     */
    public final void debug(String text) { ebugPrintln(text); }

    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     * @since 2.4.50
     */
    public static final void ebugWARNING(String text) { }

    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     * @since 2.4.50
     */
    public static final void ebugWARNING(String prefix, String text) { }

    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     * @since 2.4.50
     */
    public static final void ebugERROR(String text) { }

    /**
     * Does nothing as debug is off
     * @param text Text to debug-print
     * @since 2.4.50
     */
    public static final void ebugERROR(String prefix, String text) { }

}
