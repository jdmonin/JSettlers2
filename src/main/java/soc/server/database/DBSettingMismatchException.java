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
package soc.server.database;

/**
 * This exception indicates {@link SOCDBHelper#initialize(String, String, Properties)} was called
 * with one or more properties which are also in the {@code settings} table but with different values.
 *<P>
 * Since {@code SOCDBHelper} prints a message when settings mismatch or are corrected,
 * this Exception doesn't contain all info. Its {@link #getMessage()} is the first
 * mismatched setting name which was found.
 *
 * @see SOCDBHelper#PROP_JSETTLERS_DB_SETTINGS
 * @since 1.2.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class DBSettingMismatchException extends RuntimeException
{
    private static final long serialVersionUID = 1200L;

    /**
     * Create a {@link DBSettingMismatchException}; {@code settingKey} is the name of a mismatched setting.
     * It will be placed into {@link #getMessage()}.
     * @param settingKey  Key name of a mismatched setting, such as {@link SOCDBHelper#SETTING_BCRYPT_WORK__FACTOR}.
     *     If multiple settings are mismatched, provide only one of them.
     */
    public DBSettingMismatchException(final String settingKey)
    {
        super(settingKey);
    }

}
