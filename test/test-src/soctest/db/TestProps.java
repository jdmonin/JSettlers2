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
package soctest.db;

import java.sql.SQLException;
import java.util.Properties;

import org.junit.Test;
import static org.junit.Assert.*;

import soc.server.database.SOCDBHelper;

/**
 * A few tests for DB properties. Running these tests doesn't require a database or jdbc driver JAR.
 *
 * @since 2.0.00
 * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
 */
public class TestProps
{
    /** Missing driver should throw SQLException, not ClassNotFoundException */
    @Test(expected=SQLException.class)
    public final void testMissingDriverClass()
        throws Exception
    {
        Properties props = new Properties();
        props.put(SOCDBHelper.PROP_JSETTLERS_DB_URL, "jdbc:othertype:...");
        props.put(SOCDBHelper.PROP_JSETTLERS_DB_DRIVER, "com.example.notexist");
        SOCDBHelper.initialize("u", "p", props);
    }

    /** Test for inconsistency: unknown URL/schema without driver */
    @Test(expected=IllegalArgumentException.class)
    public final void testUrlNoDriver()
        throws Exception
    {
        Properties props = new Properties();
        props.put(SOCDBHelper.PROP_JSETTLERS_DB_URL, "jdbc:othertype:...");
        SOCDBHelper.initialize("u", "p", props);  // should throw IllegalArgumentException
    }

    /** Test for inconsistency: unknown driver without URL */
    @Test(expected=IllegalArgumentException.class)
    public final void testDriverNoUrl()
        throws Exception
    {
        Properties props = new Properties();
        props.put(SOCDBHelper.PROP_JSETTLERS_DB_DRIVER, "com.example.othertype");
        SOCDBHelper.initialize("u", "p", props);  // should throw IllegalArgumentException
    }

    public static void main(String[] args)
    {
        org.junit.runner.JUnitCore.main("soctest.db.TestProps");
    }

}
