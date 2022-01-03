/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * This file Copyright (C) 2013,2015,2017,2020 Jeremy D Monin <jeremy@nand.net>
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
package soc.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Common helper functions for internationalization and localization (I18N).
 *<P>
 * I18N localization was added in v2.0.00; network messages sending localized text should
 * check the remote receiver's version against {@link SOCStringManager#VERSION_FOR_I18N}.
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 * @see SOCStringManager
 */
public abstract class I18n
{
    /**
     * Optional JVM property {@code jsettlers.locale} to specify the locale,
     * overriding the default from {@link java.util.Locale#getDefault()}.
     * @see net.nand.util.i18n.mgr.StringManager#parseLocale(String)
     */
    public static final String PROP_JSETTLERS_LOCALE = "jsettlers.locale";

    /**
     * Build a string with the contents of this list, such as "x, y, and z".
     *<P>
     * This method and its formatting strings ({@code i18n.listitems.*}) may need
     * refinement as more languages are supported.
     * @param items  Each item's {@link Object#toString() toString()} will be placed in the list
     * @param strings  StringManager to retrieve localized formatting between items
     * @return A string nicely listing the items, with a form such as:
     *   <UL>
     *   <LI> nothing
     *   <LI> x
     *   <LI> x and y
     *   <LI> x, y, and z
     *   <LI> x, y, z, and w
     *   </UL>
     * @throws IllegalArgumentException if {@code items} is null, or {@code strings} is null
     */
    public static final String listItems(List<? extends Object> items, SOCStringManager strings)
        throws IllegalArgumentException
    {
        if ((items == null) || (strings == null))
            throw new IllegalArgumentException("null");

        final int L = items.size();
        switch(L)
        {
        case 0:
            return strings.get("i18n.listitems.nothing");  // "nothing"

        case 1:
            return items.get(0).toString();

        case 2:
            return strings.get("i18n.listitems.2", items.get(0), items.get(1));  // "{0} and {1}"

        default:
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < (L-1); ++i)
                sb.append(strings.get("i18n.listitems.item", items.get(i)));  // "{0}, " -- trailing space noted in properties file comment
            sb.append(strings.get("i18n.listitems.finalitem", items.get(L-1)));  // "and {0}"
            return sb.toString();
        }
    }

    /**
     * Precision constant for {@link #numTo3SigFigs(BigDecimal, String)}.
     * @since 2.3.00
     */
    private static final MathContext PRECISION_3_SIG_FIGS = new MathContext(3, RoundingMode.HALF_UP);

    /**
     * For {@link #bytesToHumanUnits(long)}, turn the number and unit into a string.
     * See that method and its unit tests for examples of output format.
     * @param amount  Amount to represent, such as 12.345; assumes non-negative
     * @param unit  Unit of measurement to append: "MB", "GB", etc
     * @return If amount >= 100, returns amount and unit.
     *     Otherwise returns amount to 3 significant figures + unit.
     * @since 2.3.00
     */
    private static String numTo3SigFigs(final BigDecimal amount, final String unit)
    {
        // TODO i18n: require locale/SOCStringManager param, to localize decimal point

        final long wholeAmount = amount.setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
        StringBuilder sb = new StringBuilder();
        if (wholeAmount >= 100)
        {
            sb.append(wholeAmount);
        } else {
            sb.append(amount.round(PRECISION_3_SIG_FIGS).toPlainString());

            if ((wholeAmount < 10) && (sb.lastIndexOf(".") == (sb.length() - 2)))
                // fix "2.0" -> "2.00"
                sb.append('0');
        }

        sb.append(' ');
        sb.append(unit);
        return sb.toString();
    }

    /**
     * 1 MB constant for {@link #bytesToHumanUnits(long)}: 1024 * 1024.
     * @since 2.3.00
     */
    private static final int BYTES_1_MB = 1024 * 1024;

    /**
     * 1 GB constant for {@link #bytesToHumanUnits(long)}: 1024 * {@link #BYTES_1_MB}.
     * Is long to avoid conversion when comparing to method's input.
     * @since 2.3.00
     */
    private static final long BYTES_1_GB = BYTES_1_MB * 1024;

    /**
     * For display, return bytes in "human-readable" units: MB or GB, to 3 significant figures
     * (23.7 MB, 123 MB, 2.10 GB). This method uses binary kilobytes of 1024, not decimal 1000.
     *<P>
     * Because it currently has one specific use, it doesn't work with KB or TB
     * or the user's locale, but always reports as MB or GB. Later versions can expand that if needed.
     *
     * @param bytes  Number of bytes to transform into "human-readable" units; non-negative
     * @return  String with a number of MB or GB, with at least 2 decimal digits
     * @throws IllegalArgumentException if {@code bytes} &lt; 0
     * @since 2.3.00
     */
    public static String bytesToHumanUnits(final long bytes)
        throws IllegalArgumentException
    {
        if (bytes < 0)
            throw new IllegalArgumentException("bytes < 0");

        return
            (bytes >= BYTES_1_GB)
            ? numTo3SigFigs(BigDecimal.valueOf(bytes / (double) BYTES_1_GB), "GB")
            : numTo3SigFigs(BigDecimal.valueOf(bytes / (double) BYTES_1_MB), "MB");

        // TODO i18n: require locale/SOCStringManager param, to localize decimal point
    }

    /**
     * Localize a duration as either hours:minutes:seconds, leading with days if needed.
     * @param millis Duration in milliseconds
     * @param strings  StringManager to retrieve localized formatting
     * @return localized equivalent of duration, like "1:03:52" or "2 days 1:03:52"
     * @throws IllegalArgumentException if {@code millis} &lt; 0
     * @throws NullPointerException if {@code strings} is null
     * @since 2.3.00
     */
    public static String durationToDaysHoursMinutesSeconds(final long millis, final SOCStringManager strings)
        throws IllegalArgumentException, NullPointerException
    {
        if (millis < 0)
            throw new IllegalArgumentException("negative");

        final long hours = millis / (60 * 60 * 1000),
            hoursAsMillis = hours * 60 * 60 * 1000,
            minutes = (millis - hoursAsMillis) / (60 * 1000),
            seconds = (millis - hoursAsMillis - (minutes * 60 * 1000)) / 1000;

        if (hours < 24)
        {
            return strings.get("i18n.duration.hours_min_sec", hours, minutes, seconds);
        } else {
            final int days = (int) (hours / 24),
                      hr   = (int) (hours - (days * 24L));
            return strings.get("i18n.duration.days_hours_min_sec", days, hr, minutes, seconds);
        }

    }

}

