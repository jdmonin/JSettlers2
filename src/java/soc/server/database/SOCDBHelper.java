/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2012,2014-2017 Jeremy D Monin <jeremy@nand.net>
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

import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.server.SOCServer;  // solely for javadocs and ROBOT_PARAMS_*
import soc.util.SOCRobotParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Set;


/**
 * This class contains methods for connecting to a database
 * and for manipulating the data stored there.
 *<P>
 * Based on jdbc code found at www.javaworld.com
 *<P>
 * This code assumes that you're using mySQL as your database,
 * but allows you to use other database types.
 * The default URL is "jdbc:mysql://localhost/socdata".
 * The default driver is "com.mysql.jdbc.Driver".
 * These can be changed by supplying properties to {@link #initialize(String, String, Properties)}
 * for {@link #PROP_JSETTLERS_DB_URL} and {@link #PROP_JSETTLERS_DB_DRIVER}.
 *<P>
 * It uses a database created with the following commands:
 *<BR> (See {@code src/bin/sql/jsettlers-tables.sql}) <BR>
 *<code><pre>
 * CREATE DATABASE socdata;
 * USE socdata;
 * CREATE TABLE users (nickname VARCHAR(20), host VARCHAR(50), password VARCHAR(20), email VARCHAR(50), lastlogin DATE, nickname_lc VARCHAR(20));
 * CREATE TABLE logins (nickname VARCHAR(20), host VARCHAR(50), lastlogin DATE);
 * CREATE TABLE games (gamename VARCHAR(20), player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20), score1 TINYINT, score2 TINYINT, score3 TINYINT, score4 TINYINT, starttime TIMESTAMP);
 * CREATE TABLE robotparams (robotname VARCHAR(20), maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT, strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag BOOL);
 *</pre></code>
 *
 *<H3>Schema Upgrades:</H3>
 * Sometimes a new JSettlers version adds to the DB schema. When starting the JSettlers server, call
 * {@link #isSchemaLatestVersion()} to check, and if needed {@link #upgradeSchema()}.
 * To improve flexibility, currently we let the server's admin defer upgrades and continue running
 * with the old schema until they have time to upgrade it.
 *
 *<H3>Testing:</H3>
 * For unit tests, call {@link #testDBHelper()} on a temporary copy of a database.
 *
 * @author Robert S. Thomas
 */
public class SOCDBHelper
{
    // If a new property is added, please add a PROP_JSETTLERS_DB_ constant
    // and also add it to SOCServer.PROPS_LIST.

    /** Property <tt>jsettlers.db.user</tt> to specify the server's SQL database username.
     * Default is <tt>"socuser"</tt>.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_USER = "jsettlers.db.user";

    /** Property <tt>jsettlers.db.pass</tt> to specify the server's SQL database password.
     * Default is <tt>"socpass"</tt>.
     * v2.0.00 and higher allow a blank password ("").
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_PASS = "jsettlers.db.pass";

    /** Property <tt>jsettlers.db.jar</tt> to specify the JAR filename for the server's JDBC driver.
     * This is required when running a JAR file, since JVM will ignore CLASSPATH.
     *<P>
     * Default is blank (no driver jar file), since the filename varies when used.
     * @since 1.1.15
     */
    public static final String PROP_JSETTLERS_DB_JAR = "jsettlers.db.jar";

    /** Property <tt>jsettlers.db.driver</tt> to specify the server's JDBC driver class.
     * The default driver is "com.mysql.jdbc.Driver".
     * If the {@link #PROP_JSETTLERS_DB_URL URL} begins with "jdbc:postgresql:",
     * the driver will be "org.postgresql.Driver".
     * If the <tt>URL</tt> begins with "jdbc:sqlite:",
     * the driver will be "org.sqlite.JDBC".
     * @see #dbType
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_DRIVER = "jsettlers.db.driver";

    /** Property <tt>jsettlers.db.url</tt> to specify the server's URL.
     * The default URL is "jdbc:mysql://localhost/socdata".
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_URL = "jsettlers.db.url";

    /** Property <tt>jsettlers.db.script.setup</tt> to run a SQL setup script
     * at server startup, then exit.  Used to create tables when setting up a server.
     * To activate this mode, set this to the SQL script's full path or relative path.
     *<P>
     * To implement this, the SOCServer constructor connects to the db and runs the setup script,
     * then signals success by throwing an {@link java.io.EOFException EOFException} which is
     * caught by {@code main(..)}.  Errors throw {@link SQLException} instead.
     * @since 1.1.15
     */
    public static final String PROP_JSETTLERS_DB_SCRIPT_SETUP = "jsettlers.db.script.setup";

    /**
     * Boolean property {@code jsettlers.db.upgrade_schema} to run {@link #upgradeSchema()}
     * at server startup, then exit. To activate this mode, set this to true.
     *<P>
     * Same SOCServer semantics/exceptions as {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP},
     * see that property's javadoc for details.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DB_UPGRADE__SCHEMA = "jsettlers.db.upgrade_schema";

    /** Property <tt>jsettlers.db.save.games</tt> to ask to save
     * all game results in the database.
     * Set this to 1 or Y to activate this feature.
     * @since 1.1.15
     */
    public static final String PROP_JSETTLERS_DB_SAVE_GAMES = "jsettlers.db.save.games";

    /**
     * Internal property name used to hold the <tt>--pw-reset</tt> command line argument's username.
     * When present at server startup, the server will prompt and reset the password if the user exists,
     * then exit.
     *<P>
     * This is a Utility Mode parameter; not for use in property files, because the server always exits
     * after trying to change the password.
     *<P>
     * As with {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP}, the SOCServer constructor throws either
     * {@link java.io.EOFException EOFException} or {@link SQLException} (for success or failure)
     * which are caught by {@code main(..)}.
     *
     * @since 1.1.20
     */
    public static final String PROP_IMPL_JSETTLERS_PW_RESET = "_jsettlers.user.pw_reset";

    /**
     * Original JSettlers schema version (1.0.00), before any new/extra tables/fields.
     * @see #SCHEMA_VERSION_1200
     * @see #SCHEMA_VERSION_LATEST
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_ORIGINAL = 1000;

    /**
     * First new JSettlers schema version (1.2.00) which adds any new/extra tables/fields.
     *<UL>
     * <LI> Add {@code users.nickname_lc}, unique index {@code users__l}: nickname as lowercase,
     *      to prevent collisions among user nicknames
     *</LI>
     * @see #SCHEMA_VERSION_ORIGINAL
     * @see #SCHEMA_VERSION_LATEST
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_1200 = 1200;

    /**
     * Latest version of the JSettlers schema, currently 1.2.00.
     * @see #isSchemaLatestVersion()
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_LATEST = 1200;

    // Known DB types: These constants aren't used outside the class or stored anywhere,
    // so they can change between versions if needed. All @since 1.2.00.

    /** Known DB type mysql for {@link #dbType}. */
    private static final char DBTYPE_MYSQL = 'M';

    /** Unsupported known DB type ora for {@link #dbType}. */
    private static final char DBTYPE_ORA = 'O';

    /** Known DB type postgresql for {@link #dbType}. */
    private static final char DBTYPE_POSTGRESQL = 'P';

    /** Known DB type sqlite for {@link #dbType}. */
    private static final char DBTYPE_SQLITE = 'S';

    /** Unknown DB type for {@link #dbType}. */
    private static final char DBTYPE_UNKNOWN = '?';

    /**
     * During {@link #upgradeSchema()} if a data conversion batch gets this many rows, execute and start a new batch.
     * @since 1.2.00
     */
    private static final int UPG_BATCH_MAX = 100;

    /**
     * The db driver type if detected, or null char if never connected. Used when certain DB types
     * need special consideration. If DB has been initialized, value will be {@link #DBTYPE_MYSQL},
     * {@link #DBTYPE_SQLITE}, etc, or {@link #DBTYPE_UNKNOWN}.
     *<P>
     * Set in {@link #initialize(String, String, Properties)} based on db URL and jdbc driver name.
     * @see #driverclass
     * @since 1.2.00
     */
    private static char dbType;

    /**
     * The db driver used, or null if none.
     * If {@link #driverinstance} != null, use that to connect instead of driverclass;
     * we still need to remember driverclass to detect various db-specific behaviors.
     * Set in {@link #initialize(String, String, Properties)}.
     * @see #dbType
     * @since 1.1.14
     */
    private static String driverclass = null;

    /**
     * The db driver instance, if we dynamically loaded its JAR.
     * Otherwise null, use {@link #dbURL} to connect instead.
     *<P>
     * Used because {@link DriverManager#registerDriver(Driver)} won't work
     * if the classloader is different, which it will be for dynamic loading.
     *<P>
     * Set in {@link #initialize(String, String, Properties)}.
     * Used in {@link #connect(String, String, String)}.
     * @since 1.1.15
     */
    private static Driver driverinstance = null;

    /**
     * db connection, or <tt>null</tt> if never initialized or if cleaned up for shutdown.
     * If this is non-null but closed, most queries will try to recreate it via {@link #checkConnection()}.
     * Set in {@link #connect(String, String, String)}, based on the {@link #dbURL}
     * from {@link #initialize(String, String, Properties)}.
     * Cleared in {@link #cleanup(boolean) cleanup(true)}.
     */
    private static Connection connection = null;

    /**
     * Retain the URL (default, or passed via props to {@link #initialize(String, String, Properties)}).
     * Used in {@link #connect(String, String, String)}.
     *<P>
     * If {@link #driverinstance} != null, go through it to connect to dbURL.
     * @since 1.1.09
     */
    private static String dbURL = null;

    /**
     * This flag indicates that the connection should be valid, yet the last
     * operation failed. Methods will attempt to reconnect prior to their
     * operation if this is set.
     */
    private static boolean errorCondition = false;

    /**
     * True if we successfully completed {@link #initialize(String, String, Properties)}
     * without throwing an exception.
     * Set false in {@link #cleanup(boolean)}.
     */
    private static boolean initialized = false;

    /**
     * The detected schema version of the currently connected database.
     * See {@link #getSchemaVersion()} javadocs for details.
     * Is set in {@link #connect(String, String, String)}.
     * @since 1.2.00
     */
    private static int schemaVersion;

    /** Cached username used when reconnecting on error */
    private static String userName;

    /** Cached password used when reconnecting on error */
    private static String password;

    /**
     * {@link #createAccountCommand} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code CREATE_ACCOUNT_COMMAND}.
     */
    private static final String CREATE_ACCOUNT_COMMAND_1000
        = "INSERT INTO users(nickname,host,password,email,lastlogin) VALUES (?,?,?,?,?);";

    /**
     * {@link #createAccountCommand} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String CREATE_ACCOUNT_COMMAND_1200
        = "INSERT INTO users(nickname,host,password,email,lastlogin,nickname_lc) VALUES (?,?,?,?,?,?);";

    private static String RECORD_LOGIN_COMMAND = "INSERT INTO logins VALUES (?,?,?);";

    /**
     * {@link #userPasswordQuery} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code USER_PASSWORD_QUERY}.
     */
    private static final String USER_PASSWORD_QUERY_1000 = "SELECT nickname,password FROM users WHERE nickname = ? ;";

    /**
     * {@link #userPasswordQuery} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String USER_PASSWORD_QUERY_1200 = "SELECT nickname,password FROM users WHERE nickname_lc = ? ;";

    private static String HOST_QUERY = "SELECT nickname FROM users WHERE ( users.host = ? );";
    private static String LASTLOGIN_UPDATE = "UPDATE users SET lastlogin = ?  WHERE nickname = ? ;";

    /**
     * {@link #passwordUpdateCommand} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code PASSWORD_UPDATE}.
     * @since 1.1.20
     */
    private static final String PASSWORD_UPDATE_COMMAND_1000 = "UPDATE users SET password = ? WHERE nickname = ? ;";

    /**
     * {@link #passwordUpdateCommand} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String PASSWORD_UPDATE_COMMAND_1200 = "UPDATE users SET password = ? WHERE nickname_lc = ? ;";

    private static String SAVE_GAME_COMMAND = "INSERT INTO games VALUES (?,?,?,?,?,?,?,?,?,?);";
    private static String ROBOT_PARAMS_QUERY = "SELECT * FROM robotparams WHERE robotname = ?;";
    private static final String USER_COUNT_QUERY = "SELECT count(*) FROM users;";

    /**
     * {@link #userExistsQuery} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code USER_EXISTS_QUERY}.
     * @since 1.1.20
     */
    private static final String USER_EXISTS_QUERY_1000 = "SELECT nickname FROM users WHERE nickname = ?;";

    /**
     * {@link #userExistsQuery} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String USER_EXISTS_QUERY_1200 = "SELECT nickname FROM users WHERE nickname_lc = ?;";

    /** Create a new account in {@code users}: {@link #CREATE_ACCOUNT_COMMAND_1200} */
    private static PreparedStatement createAccountCommand = null;

    private static PreparedStatement recordLoginCommand = null;

    /** Query whether a user nickname exists in {@code users}: {@link #USER_EXISTS_QUERY_1200} */
    private static PreparedStatement userExistsQuery = null;

    /** Query for a user's password and original-cased nickname in {@code users}: {@link #USER_PASSWORD_QUERY_1200} */
    private static PreparedStatement userPasswordQuery = null;

    private static PreparedStatement hostQuery = null;
    private static PreparedStatement lastloginUpdate = null;

    /**
     * User password update in {@code users}: {@link #PASSWORD_UPDATE_COMMAND_1200}.
     * Before v1.2.00 this field was {@code passwordUpdate}.
     * @since 1.1.20
     */
    private static PreparedStatement passwordUpdateCommand = null;

    /** Completed-game info insert into {@code games}: {@link #SAVE_GAME_COMMAND} */
    private static PreparedStatement saveGameCommand = null;

    /** Query all robot parameters for a bot name; {@link #ROBOT_PARAMS_QUERY}.
     *  Used in {@link #retrieveRobotParams(String)}.
     */
    private static PreparedStatement robotParamsQuery = null;

    /** Query how many users, if any, exist in the {@code users} table: {@link #USER_COUNT_QUERY}.
     *  @since 1.1.19
     */
    private static PreparedStatement userCountQuery = null;

    /**
     * This makes a connection to the database
     * and initializes the prepared statements.
     * (If <tt>props</tt> includes {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP},
     * runs that script before the prepared statements.)
     * Sets {@link #isInitialized()}.
     *<P>
     * The default URL is "jdbc:mysql://localhost/socdata".
     * The default driver is "com.mysql.jdbc.Driver".
     * These can be changed by supplying <code>props</code>.
     *
     * @param user  the user name for accessing the database
     * @param pswd  the password for the user, or ""
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_DB_DRIVER},
     *       {@link #PROP_JSETTLERS_DB_URL}, and any other desired properties.
     *       Ignores {@link #PROP_JSETTLERS_DB_USER} and {@link #PROP_JSETTLERS_DB_PASS} if present,
     *       uses the {@code user} and {@code pswd} parameters instead.
     * @throws SQLException if an SQL command fails, or the db couldn't be
     *         initialized;
     *         or if the {@link #PROP_JSETTLERS_DB_DRIVER} property is not mysql, not sqlite, not postgres,
     *         but the {@link #PROP_JSETTLERS_DB_URL} property is not provided.
     * @throws IOException  if <tt>props</tt> includes {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP} but
     *         the SQL file wasn't found, or if any other IO error occurs reading the script
     */
    public static void initialize(final String user, final String pswd, Properties props)
        throws SQLException, IOException
    {
        initialized = false;

        // Driver types and URLs recognized here should
        // be the same as those listed in README.txt.

        driverclass = "com.mysql.jdbc.Driver";
        dbType = DBTYPE_MYSQL;
    	dbURL = "jdbc:mysql://localhost/socdata";

    	if (props != null)
    	{
    	    String prop_dbURL = props.getProperty(PROP_JSETTLERS_DB_URL);
    	    String prop_driverclass = props.getProperty(PROP_JSETTLERS_DB_DRIVER);

    	    if (prop_dbURL != null)
    	    {
    	        dbURL = prop_dbURL;

    	        if (prop_driverclass != null)
    	        {
    	            driverclass = prop_driverclass;

    	            // dbType detection from driver string:
    	            if (driverclass.contains("postgresql"))
    	                dbType = DBTYPE_POSTGRESQL;
                    else if (driverclass.contains("sqlite"))
                        dbType = DBTYPE_SQLITE;
                    else if (! driverclass.contains("mysql"))
                        dbType = DBTYPE_UNKNOWN;
    	        }
    	        else if (prop_dbURL.startsWith("jdbc:postgresql"))
    	        {
    	            driverclass = "org.postgresql.Driver";
    	            dbType = DBTYPE_POSTGRESQL;
    	        }
    	        else if (prop_dbURL.startsWith("jdbc:sqlite:"))
    	        {
    	            driverclass = "org.sqlite.JDBC";
    	            dbType = DBTYPE_SQLITE;
    	        }
    	        else if (! prop_dbURL.startsWith("jdbc:mysql"))
    	        {
    	            throw new SQLException("JDBC: URL property is set, but driver property is not: ("
    	                + PROP_JSETTLERS_DB_URL + ", " + PROP_JSETTLERS_DB_DRIVER + ")");
    	        }
    	    } else {
    	        if (prop_driverclass != null)
    	            driverclass = prop_driverclass;

                // if it's mysql, use the mysql default url above.
                // if it's postgres or sqlite, use that.
                // otherwise, not sure what they have.

                if (driverclass.contains("postgresql"))
                {
                    dbURL = "jdbc:postgresql://localhost/socdata";
                    dbType = DBTYPE_POSTGRESQL;
                }
                else if (driverclass.contains("sqlite"))
                {
                    dbURL = "jdbc:sqlite:socdata.sqlite";
                    dbType = DBTYPE_SQLITE;
                }
                else if (! driverclass.contains("mysql"))
    	        {
    	            throw new SQLException("JDBC: Driver property is set, but URL property is not: ("
    	                + PROP_JSETTLERS_DB_DRIVER + ", " + PROP_JSETTLERS_DB_URL + ")");
    	        }
    	    }
    	}

    	if (dbType == DBTYPE_UNKNOWN)
    	{
    	    // try to detect unsupported/semi-known types from driver

    	    if (driverclass.toLowerCase().contains("oracle"))
                dbType = DBTYPE_ORA;
    	}

    	driverinstance = null;
    	boolean driverNewInstanceFailed = false;
    	try
        {
            // Load the JDBC driver
    	    try
    	    {
                String prop_jarname = props.getProperty(PROP_JSETTLERS_DB_JAR);
                if ((prop_jarname != null) && (prop_jarname.length() == 0))
                    prop_jarname = null;

                if (prop_jarname != null)
                {
                    // Dynamically load the JDBC driver's JAR file.
                    // Required since JVM ignores CLASSPATH when running a JAR file.
                    File jf = new File(prop_jarname);
                    if (! jf.exists())
                    {
                        System.err.println("Could not find " + prop_jarname + " for JDBC driver class " + driverclass);
                        throw new FileNotFoundException(prop_jarname);
                    }
                    final URL[] urls = { jf.toURI().toURL() };
                    URLClassLoader child = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                    final Class<?> dclass = Class.forName(driverclass, true, child);
                    driverinstance = (Driver) dclass.newInstance();
                } else {
                    // JDBC driver class must already be loaded.
                    Class.forName(driverclass).newInstance();
                }
    	    }
    	    catch (Throwable x)
    	    {
                // InstantiationException, IllegalAccessException, ClassNotFoundException
                // (seen for org.gjt.mm.mysql.Driver)
    	        driverNewInstanceFailed = true;
    	        SQLException sx =
    	            new SQLException("JDBC driver is unavailable: " + driverclass + ": " + x);
    	        sx.initCause(x);
    	        throw sx;
    	    }

    	    // Do we have a setup script to run?
    	    String prop_dbSetupScript = props.getProperty(PROP_JSETTLERS_DB_SCRIPT_SETUP);
    	    if ((prop_dbSetupScript != null) && (prop_dbSetupScript.length() == 0))
    	        prop_dbSetupScript = null;

            // Connect and prepare table queries; run the setup script, if any, first
            connect(user, pswd, prop_dbSetupScript);
        }
    	catch (IOException iox)
    	{
    	    throw iox;  // Let the caller deal with DB setup script IO errors
    	}
        catch (Throwable x) // everything else
        {
            if (driverNewInstanceFailed && (x instanceof SQLException))
            {
                // don't re-wrap driverclass exception thrown above
                throw (SQLException) x;
            }

            SQLException sx = new SQLException("Unable to initialize user database");
            sx.initCause(x);
            throw sx;
        }

        initialized = true;
    }

    /**
     * Were we able to {@link #initialize(String, String, Properties)}
     * and connect to the database?
     * True if db is connected and available; false if never initialized,
     * or if {@link #cleanup(boolean)} was called.
     *
     * @return  True if available
     * @since 1.1.14
     */
    public static boolean isInitialized()
    {
        return initialized && (connection != null);
    }

    /**
     * Get the detected schema version of the currently connected database.
     * To upgrade an older schema to the latest available, see {@link #upgradeSchema()}.
     * @return Schema version, such as {@link #SCHEMA_VERSION_ORIGINAL} or {@link #SCHEMA_VERSION_1200}
     * @see #SCHEMA_VERSION_LATEST
     * @see #isSchemaLatestVersion()
     */
    public static int getSchemaVersion()
    {
        return schemaVersion;
    }

    /**
     * Does the currently connected DB have the latest schema, with all optional fields?
     * ({@link #SCHEMA_VERSION_LATEST})
     * @return True if DB schema is the most up-to-date version
     * @throws IllegalStateException  if not connected to DB (! {@link #isInitialized()})
     * @see #upgradeSchema()
     * @see #getSchemaVersion()
     * @since 1.2.00
     */
    public static boolean isSchemaLatestVersion()
        throws IllegalStateException
    {
        if (! isInitialized())
            throw new IllegalStateException();

        return (schemaVersion == SCHEMA_VERSION_LATEST);
    }

    /**
     * Checks if connection is supposed to be present and attempts to reconnect
     * if there was previously an error.  Reconnecting closes the current
     * {@link #connection}, opens a new one, and re-initializes the prepared statements.
     *
     * @return true if the connection is established upon return
     */
    private static boolean checkConnection() throws SQLException
    {
        if (connection != null)
        {
            try
            {
                return (! errorCondition) || connect(userName, password, null);
            } catch (IOException ioe) {
                // will not occur, connect script is null
                return false;
            }
        }

        return false;
    }

    /**
     * Opens a new connection and initializes the prepared statements.
     * {@link #initialize(String, String, Properties)} and {@link #checkConnection()} use this to get ready.
     * Uses {@link #dbURL} and {@link #driverinstance}.
     *<P>
     * If <tt>setupScriptPath</tt> != null, it will be ran before preparing statements.
     * That way, it can create tables used by the statements.
     *
     * @param user  DB username
     * @param pswd  DB user password, or ""
     * @param setupScriptPath  Full path or relative path to SQL script to run at connect, or null;
     *     typically from {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP}
     * @throws IOException  if <tt>setupScriptPath</tt> wasn't found, or if any other IO error occurs reading the script
     * @throws SQLException if any connect error, missing table, or SQL error occurs
     * @return  true on success; will never return false, instead will throw a sqlexception
     */
    private static boolean connect(final String user, final String pswd, final String setupScriptPath)
        throws SQLException, IOException
    {
        if (driverinstance == null) {
            connection = DriverManager.getConnection(dbURL, user, pswd);
        } else {
            Properties props = new Properties();
            props.put("user", user);
            props.put("password", pswd);
            connection = driverinstance.connect(dbURL, props);
        }

        errorCondition = false;
        userName = user;
        password = pswd;

        if (setupScriptPath != null)
            runSetupScript(setupScriptPath);  // may throw IOException, SQLException

        // check schema upgrade status
        if (doesTableColumnExist("users", "nickname_lc"))
            schemaVersion = SCHEMA_VERSION_1200;
        else
            schemaVersion = SCHEMA_VERSION_ORIGINAL;

        // prepare PreparedStatements for queries
        createAccountCommand = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? CREATE_ACCOUNT_COMMAND_1200 : CREATE_ACCOUNT_COMMAND_1000);
        recordLoginCommand = connection.prepareStatement(RECORD_LOGIN_COMMAND);
        userExistsQuery = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? USER_EXISTS_QUERY_1200 : USER_EXISTS_QUERY_1000);
        userPasswordQuery = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? USER_PASSWORD_QUERY_1200 : USER_PASSWORD_QUERY_1000);
        hostQuery = connection.prepareStatement(HOST_QUERY);
        lastloginUpdate = connection.prepareStatement(LASTLOGIN_UPDATE);
        passwordUpdateCommand = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? PASSWORD_UPDATE_COMMAND_1200 : PASSWORD_UPDATE_COMMAND_1000);
        saveGameCommand = connection.prepareStatement(SAVE_GAME_COMMAND);
        robotParamsQuery = connection.prepareStatement(ROBOT_PARAMS_QUERY);
        userCountQuery = connection.prepareStatement(USER_COUNT_QUERY);

        return true;
    }

    /**
     * Load and run a SQL script.
     * Typically DDL commands to create or alter tables, indexes, etc.
     * @param setupScriptPath  Full path or relative path to the SQL script filename
     * @throws FileNotFoundException  if file not found
     * @throws IOException  if any other IO error occurs
     * @throws SQLException if any unexpected database problem
     * @since 1.1.15
     */
    private static void runSetupScript(final String setupScriptPath)
        throws FileNotFoundException, IOException, SQLException
    {
        if (! checkConnection())
            return;  // also may throw SQLException

        FileReader fr = new FileReader(setupScriptPath);
        BufferedReader br = new BufferedReader(fr);
        List<String> sqls = new ArrayList<String>();

        // Read 1 line at a time, with continuations; build a list
        try
        {
            StringBuilder sb = new StringBuilder();

            for (String nextLine = br.readLine(); nextLine != null; nextLine = br.readLine())
            {
                // Reminder: java String.trim removes ascii whitespace (including tabs) but not unicode whitespace.
                // Character.isWhitespace is true for both ascii and unicode whitespace, except non-breaking spaces.

                if ((nextLine.length() == 0) || (nextLine.trim().length() == 0))
                    continue;  // <-- skip empty lines --

                if (nextLine.startsWith("--"))
                    continue;  // <-- skip comment lines with no leading whitespace --

                if ((dbType == DBTYPE_SQLITE) && nextLine.toLowerCase().startsWith("use "))
                    continue;  // <-- sqlite doesn't support "USE"

                // If starts with whitespace, append it to sb (continue previous line).
                // Otherwise, add previous sb to the sqls list, and start a new sb containing nextLine.
                if (Character.isWhitespace(nextLine.codePointAt(0)))
                {
                    if (sb.length() > 0)
                        sb.append("\n");  // previous line's readLine doesn't include the trailing \n
                } else {
                    sqls.add(sb.toString());
                    sb.delete(0, sb.length());
                }
                sb.append(nextLine);
            }

            // don't forget the last command
            sqls.add(sb.toString());

            // done reading the file
            try { br.close(); }
            catch (IOException eclose) {}
            try { fr.close(); }
            catch (IOException eclose) {}
        }
        catch (IOException e)
        {
            try { br.close(); }
            catch (IOException eclose) {}
            try { fr.close(); }
            catch (IOException eclose) {}

            throw e;
        }

        // No errors: Run the built list of SQLs
        for (String sql : sqls)
        {
            if (sql.trim().length() == 0)
                continue;
            Statement cmd = connection.createStatement();
            cmd.executeUpdate(sql);
            cmd.close();
        }
    }

    /**
     * Perform pre-checks and upgrade the currently connected DB to the latest schema, with all optional fields.
     *<P>
     * Pre-checks include {@link #queryUsersDuplicateLCase(Set)}.
     *
     *<H3>Security note:</H3>
     * To upgrade the schema, the DB connect username must have authorization grants to
     * run DDL commands, add or alter tables, etc.
     *
     *<H3>Rollback of failed upgrade:</H3>
     * If the schema upgrade fails, errors will be printed to {@link System#err} and thrown as a SQLException.
     * If failures also occur during rollback, those are also printed to {@link System#err}.
     * If the DB is SQLite, any added table fields can't be dropped, and the DB file must be restored from a backup
     * because rollback is incomplete.
     *
     * @throws IllegalStateException  if already latest version ({@link #isSchemaLatestVersion()}),
     *     or if not connected to DB (! {@link #isInitialized()})
     * @throws MissingResourceException  if pre-checks indicate a problem in the data (such as nicknames which collide
     *     with each other when lowercase) which must be manually resolved by this server's administrator before upgrade:
     *     {@link Throwable#getMessage()} will be a multi-line String with problem details to show to the server admin.
     * @throws SQLException  if any unexpected database problem during the upgrade
     * @see {@link #isSchemaLatestVersion()}
     * @since 1.2.00
     */
    public static void upgradeSchema()
        throws IllegalStateException, SQLException, MissingResourceException
    {
        if (isSchemaLatestVersion())  // throws IllegalStateException if ! isInitialized()
            throw new IllegalStateException("already at latest schema");

        // NOTES for future schema changes:
        // - Keep your DDL SQL syntax consistent with the DDL commands in testDBHelper().
        // - Be prepared to rollback to a known-good state if a problem occurs.
        //   Each unrelated part of an upgrade must completely succeed or fail.
        //   That requirement is for postgresql and mysql: sqlite can't drop any added columns;
        //   the server's admin must back up their sqlite db before running the upgrade.

        /**
         * 1.2.00: users.nickname_lc, index users__l
         */
        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            /* pre-checks */
            final Set<String> allUsers = new HashSet<String>();
            final Map<String, List<String>> dupes = queryUsersDuplicateLCase(allUsers);
            if (dupes != null)
            {
                StringBuilder sb = new StringBuilder
                    ("These groups of users' nicknames collide with each other when lowercase:\n");
                for (String k : dupes.keySet())
                {
                    sb.append(dupes.get(k));  // "[jtest2, JTest2, JTesT2]"
                    sb.append('\n');
                }
                sb.append
                    ("\nTo upgrade, the nicknames must be changed to be unique when lowercase.\n"
                     + "Contact each user and determine new nicknames, then for each user run this SQL:\n"
                     + "  BEGIN TRANSACTION;\n"
                     + "  UPDATE users SET nickname='newnick' WHERE nickname='oldnick';\n"
                     + "  UPDATE logins SET nickname='newnick' WHERE nickname='oldnick';\n"
                     + "  UPDATE games SET player1='newnick' WHERE player1='oldnick';\n"
                     + "  UPDATE games SET player2='newnick' WHERE player2='oldnick';\n"
                     + "  UPDATE games SET player3='newnick' WHERE player3='oldnick';\n"
                     + "  UPDATE games SET player4='newnick' WHERE player4='oldnick';\n"
                     + "  COMMIT;\n"
                     + "Then, retry the DB schema upgrade.\n"
                    );

                throw new MissingResourceException(sb.toString(), "unused", "unused");
            }

            /* add field, fill it, add unique index */
            boolean added = false;
            try
            {
                runDDL("ALTER TABLE users ADD COLUMN nickname_lc VARCHAR(20);");
                added = true;

                // fill the new field; use String.toLowerCase(..), not SQL lower(..) which is ascii-only on sqlite.
                if (! allUsers.isEmpty())
                {
                    final boolean was_conn_autocommit = connection.getAutoCommit();

                    PreparedStatement ps = connection.prepareStatement
                        ("UPDATE users SET nickname_lc=? WHERE nickname=?");

                    // begin transaction
                    if (was_conn_autocommit)
                        connection.setAutoCommit(false);
                    else
                        try {
                            connection.commit();  // end previous transaction, if any
                        } catch (SQLException e) {}

                    try
                    {
                        int n = 0;
                        for (final String nm : allUsers)
                        {
                            ps.setString(1, nm.toLowerCase(Locale.US));
                            ps.setString(2, nm);
                            ps.addBatch();
                            ++n;
                            if (n >= UPG_BATCH_MAX)
                            {
                                ps.executeBatch();
                                ps.clearBatch();
                                n = 0;
                            }
                        }
                        ps.executeBatch();
                        connection.commit();
                    } catch (SQLException e) {
                        connection.rollback();
                        throw e;
                    } finally {
                        if (was_conn_autocommit)
                            connection.setAutoCommit(true);
                    }

                }

                // create unique index
                runDDL("CREATE UNIQUE INDEX users__l ON users(nickname_lc);");

            } catch (SQLException e) {
                System.err.println
                    ("*** Problem occurred during schema upgrade: Will attempt to roll back.\n" + e + "\n");

                boolean couldRollback = true;

                if (added)
                {
                    if ((dbType == DBTYPE_SQLITE) || ! runDDL_rollback("ALTER TABLE users DROP COLUMN nickname_lc;"))
                        couldRollback = false;
                }

                if (! couldRollback)
                    System.err.println
                        ("*** Could not completely roll back failed upgrade: Must restore DB from backup!");

                throw e;
            }
        }

        /* upgrade is completed. */
        System.err.println("* DB schema upgrade completed.\n\n");
    }

    /**
     * Run a DDL command to roll back part of a database upgrade.
     * Assumes this is run within a {@code catch} block, and thus
     * any {@link SQLException}s should be caught here. If an Exception
     * occurs, it will be printed to {@link System#err}.
     *
     * @param sql  SQL to run
     * @return True if command succeeded, false if an Exception was thrown
     * @since 1.2.00
     */
    private static boolean runDDL_rollback(final String sql)
    {
        try {
            runDDL(sql);
            return true;
        }
        catch (Exception rollE) {
            System.err.println("* Problem during rollback: " + rollE);
            return false;
        }
    }

    /**
     * Search for and return this user (nickname) if it exists in the database.
     * If schema &gt;= {@link #SCHEMA_VERSION_1200}, this check is case-insensitive.
     * Returns their nickname as stored in the database.
     *<P>
     * This method replaces {@code doesUserExist(..)} used in v1.1.20.
     *
     * @param userName  User nickname to check
     * @return  Nickname if found in users table, {@code null} otherwise or if no database is currently connected
     * @throws IllegalArgumentException if {@code userName} is {@code null}
     * @throws SQLException if any unexpected database problem
     * @since 1.2.00
     * @see #authenticateUserPassword(String, String)
     */
    public static String getUser(String userName)
        throws IllegalArgumentException, SQLException
    {
        if (userName == null)
            throw new IllegalArgumentException();

        if (! checkConnection())
            return null;

        if (schemaVersion >= SCHEMA_VERSION_1200)
            userName = userName.toLowerCase(Locale.US);
        userExistsQuery.setString(1, userName);

        ResultSet rs = userExistsQuery.executeQuery();
        if (rs.next())
            userName = rs.getString(1);
        else
            userName = null;

        rs.close();
        return userName;
    }

    /**
     * Check if this user exists, if so validate their password from the database.
     * If schema &gt;= {@link #SCHEMA_VERSION_1200}, username check is case-insensitive.
     * For use of the originally-cased name from that search, if successful this method
     * returns their nickname as stored in the database.
     *<P>
     * For running without the optional database, or when user accounts are optional:
     * If never connected to a database or user's nickname isn't in the users table,
     * and {@code sPassword} is "", returns {@code sUserName}.
     *<P>
     * This method replaces {@code getUserPassword(..)} used before v1.2.00.
     *
     * @param sUserName Username needing password authentication
     * @param sPassword  Password being tried, or "" if none
     *
     * @return user's nickname if password is correct;
     *     {@code sUserName} if password is "" but user doesn't exist in db
     *     or if database is not currently connected;
     *     {@code null} if account exists in db and password is wrong.
     *
     * @throws SQLException if any unexpected database problem
     * @see #updateUserPassword(String, String)
     * @see #getUser(String)
     * @since 1.2.00
     */
    public static String authenticateUserPassword(String sUserName, final String sPassword)
        throws SQLException
    {
        String dbPassword = null;

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                // fill in the data values to the Prepared statement
                if (schemaVersion >= SCHEMA_VERSION_1200)
                    sUserName = sUserName.toLowerCase(Locale.US);
                userPasswordQuery.setString(1, sUserName);

                // execute the Query
                ResultSet resultSet = userPasswordQuery.executeQuery();

                // if no results, nickname isn't in the users table
                if (resultSet.next())
                {
                    sUserName = resultSet.getString(1);  // get nickname with its original case; searched on nickname_lc
                    dbPassword = resultSet.getString(2);
                }

                resultSet.close();
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        final boolean ok = (dbPassword == null)
            ? "".equals(sPassword)
            : dbPassword.equals(sPassword);
        return (ok) ? sUserName: null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param host DOCUMENT ME!
     *
     * @return  null if user is not authenticated
     *
     * @throws SQLException DOCUMENT ME!
     * @see #getUser(String)
     */
    public static String getUserFromHost(String host) throws SQLException
    {
        String nickname = null;

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                // fill in the data values to the Prepared statement
                hostQuery.setString(1, host);

                // execute the Query
                ResultSet resultSet = hostQuery.executeQuery();

                // if no results, user is not authenticated
                if (resultSet.next())
                {
                    nickname = resultSet.getString(1);
                }

                resultSet.close();
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return nickname;
    }

    /**
     * Attempt to create a new account with a unique {@code userName} (nickname) in the {@code users} table.
     *<P>
     * <B>Before calling, validate the user doesn't already exist</B>
     * by calling {@link #getUser(String) getUser(userName)}.
     * This method doesn't verify that the user is a unique new user before trying to create the record.
     * The DB will throw an exception instead, especially at {@link #SCHEMA_VERSION_1200} or higher
     * which adds a column and unique index for case-insensitive nickname.
     *
     * @param userName  New user name (nickname) to create
     * @param host  Client hostname or IP requesting new account
     * @param password  New user's initial password
     * @param email  Optional email address to contact this user
     * @param time  Created-at time, same format as {@link System#currentTimeMillis()}
     *            and {@link java.sql.Date#Date(long)}
     *
     * @return true if the DB connection is open and the account was created,
     *     false if no database is currently connected
     *
     * @throws SQLException if any unexpected database problem occurs
     */
    public static boolean createAccount
        (String userName, String host, String password, String email, long time)
        throws SQLException
    {
        // When the password encoding or max length changes in jsettlers-tables.sql,
        // be sure to update this method and updateUserPassword.

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                // fill in the data values to the Prepared statement
                createAccountCommand.setString(1, userName);
                createAccountCommand.setString(2, host);
                createAccountCommand.setString(3, password);
                createAccountCommand.setString(4, email);
                createAccountCommand.setDate(5, sqlDate, cal);
                if (schemaVersion >= SCHEMA_VERSION_1200)
                    createAccountCommand.setString(6, userName.toLowerCase(Locale.US));

                // execute the Command
                createAccountCommand.executeUpdate();

                return true;
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return false;
    }

    /**
     * Record this user's login host and time.
     *
     * @param userName  User name (nickname)
     * @param host  Login is from this client hostname or IP
     * @param time  Login time, same format as {@link System#currentTimeMillis()}
     *
     * @return true if the DB connection is open and the login was recorded, false if connection is closed
     *
     * @throws SQLException if any unexpected database problem
     */
    public static boolean recordLogin(String userName, String host, long time) throws SQLException
    {
        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                // fill in the data values to the Prepared statement
                recordLoginCommand.setString(1, userName);
                recordLoginCommand.setString(2, host);
                recordLoginCommand.setDate(3, sqlDate, cal);

                // execute the Command
                recordLoginCommand.executeUpdate();

                return true;
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return false;
    }

    /**
     * DOCUMENT ME!
     *
     * @param userName DOCUMENT ME!
     * @param time DOCUMENT ME!
     *
     * @return true if the save succeeded
     *
     * @throws SQLException if any unexpected database problem
     */
    public static boolean updateLastlogin(String userName, long time) throws SQLException
    {
        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                // fill in the data values to the Prepared statement
                lastloginUpdate.setDate(1, sqlDate, cal);
                lastloginUpdate.setString(2, userName);

                // execute the Command
                lastloginUpdate.executeUpdate();

                return true;
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return false;
    }

    /**
     * Update a user's password if the user is in the database.
     * @param userName  Username to update.  Does not validate this user exists: Call {@link #getUser(String)}
     *     first to do so.  If schema &gt;= {@link #SCHEMA_VERSION_1200}, {@code userName} is case-insensitive.
     * @param newPassword  New password (length can be 1 to 20)
     * @return  True if the update command succeeded, false if can't connect to db.
     *     <BR><B>Note:</B> If there is no user with {@code userName}, will nonetheless return true.
     * @throws IllegalArgumentException  If user or password are null, or password is too short or too long
     * @throws SQLException if an error occurs
     * @see #authenticateUserPassword(String, String)
     * @since 1.1.20
     */
    public static boolean updateUserPassword(String userName, final String newPassword)
        throws IllegalArgumentException, SQLException
    {
        if (userName == null)
            throw new IllegalArgumentException("userName");
        if ((newPassword == null) || (newPassword.length() == 0) || (newPassword.length() > 20))
            throw new IllegalArgumentException("newPassword");

        // When the password encoding or max length changes in jsettlers-tables.sql,
        // be sure to update this method and createAccount.

        if (! checkConnection())
            return false;

        if (schemaVersion >= SCHEMA_VERSION_1200)
            userName = userName.toLowerCase(Locale.US);
        try
        {
            passwordUpdateCommand.setString(1, newPassword);
            passwordUpdateCommand.setString(2, userName);
            passwordUpdateCommand.executeUpdate();

            return true;
        }
        catch (SQLException sqlE)
        {
            errorCondition = true;
            sqlE.printStackTrace();

            throw sqlE;
        }
    }

    /**
     * Record this game's time, players, and scores in the database.
     *
     * @param ga  Game that's just completed
     * @param gameLengthSeconds  Duration of game
     *
     * @return true if the save succeeded
     *
     * @throws SQLException DOCUMENT ME!
     */
    public static boolean saveGameScores
        (SOCGame ga, final long gameLengthSeconds)
        throws SQLException
    {
        // TODO 6-player: save their scores too, if
        // those fields are in the database.
        // Check ga.maxPlayers.

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            String[] names = new String[ga.maxPlayers];
            short[] scores = new short[ga.maxPlayers];
            for (int pn = 0; pn < ga.maxPlayers; ++pn)
            {
                SOCPlayer pl = ga.getPlayer(pn);
                names[pn] = pl.getName();
                scores[pn] = (short) pl.getTotalVP();
            }

            if ((ga.maxPlayers > 4)
                && ! (ga.isSeatVacant(4) && ga.isSeatVacant(5)))
            {
                // Need to try and fit player 5 and/or player 6
                // into the 4 db slots (backwards-compatibility)
                saveGameScores_fit6pInto4(ga, names, scores);
            }

            try
            {
                // fill in the data values to the Prepared statement
                saveGameCommand.setString(1, ga.getName());
                saveGameCommand.setString(2, names[0]);
                saveGameCommand.setString(3, names[1]);
                saveGameCommand.setString(4, names[2]);
                saveGameCommand.setString(5, names[3]);
                saveGameCommand.setShort(6, scores[0]);
                saveGameCommand.setShort(7, scores[1]);
                saveGameCommand.setShort(8, scores[2]);
                saveGameCommand.setShort(9, scores[3]);
                saveGameCommand.setTimestamp(10, new Timestamp(ga.getStartTime().getTime()));

                // execute the Command
                saveGameCommand.executeUpdate();

                return true;
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return false;
    }

    /**
     * Try and fit names and scores of player 5 and/or player 6
     * into the 4 db slots, for backwards-compatibility.
     * Checks {@link SOCGame#isSeatVacant(int) ga.isSeatVacant(pn)}
     * and {@link SOCPlayer#isRobot() ga.getPlayer(pn).isRobot()}
     * for the first 4 player numbers, and copies player 5 and 6's
     * data to those positions in <tt>names[]</tt> and <tt>scores[]</tt>.
     *<P>
     * v1.1.15: Copy to vacant slots among first 4 players.
     *<P>
     * v1.1.19: Copy to vacant slots or robot slots among first 4; if human player
     * 5 or 6 won, overwrite the lowest-scoring non-winner slot if necessary.
     *
     * @param ga  Game that's over
     * @param names  Player names for player number 0-5; contents of 0-3 may be changed
     * @param scores  Player scores for player number 0-5; contents of 0-3 may be changed
     * @since 1.1.15
     */
    private static void saveGameScores_fit6pInto4
        (SOCGame ga, String[] names, short[] scores)
    {
        // Need to try and fit player 5 and/or player 6
        // into the 4 db slots (backwards-compatibility)

        int winnerPN;
        {
            SOCPlayer pl = ga.getPlayerWithWin();
            winnerPN = (pl != null) ? pl.getPlayerNumber() : -1;
        }

        int nVacantLow = 0, nBotLow = 0;
        final boolean[] isBot = new boolean[4], // track isBot locally, since we're rearranging pn 0-3 from game obj
                        isVacant = new boolean[4];  // same with isVacant
        for (int pn = 0; pn < 4; ++pn)
        {
            if (ga.isSeatVacant(pn))
            {
                isVacant[pn] = true;
                ++nVacantLow;
            }
            else if (ga.getPlayer(pn).isRobot())
            {
                isBot[pn] = true;
                if (pn != winnerPN)
                    ++nBotLow;
            }
        }

        int[] pnHigh = { -1, -1 };  // Occupied high pn: Will try to find a place for first and then for second element

        if (! ga.isSeatVacant(4))
            pnHigh[0] = 4;

        if (! ga.isSeatVacant(5))
        {
            if (pnHigh[0] == -1)
            {
                pnHigh[0] = 5;
            } else {
                // record score for humans before robots if 4 and 5 are both occupied.
                // pnHigh[0] takes priority: claim it if pl 5 is human and is winner, or pl 4 is a bot that didn't win
                if ( (! ga.getPlayer(5).isRobot())
                      && ( (winnerPN == 5) || (ga.getPlayer(4).isRobot() && (winnerPN != 4)) ) )
                {
                    pnHigh[0] = 5;
                    pnHigh[1] = 4;
                } else {
                    pnHigh[1] = 5;
                    // pnHigh[0] unchanged == 4
                }
            }
        }

        if ((winnerPN >= 4) && (! ga.getPlayer(winnerPN).isRobot()) && (nVacantLow == 0) && (nBotLow == 0))
        {
            // No room to replace a bot or take a vacant spot:
            // Make sure at least the human winner is recorded instead of the lowest non-winner score.
            // (If nVacantLow > 0 or nBotLow > 0, the main loop would take care of recording the winner.)
            // Find the lowest-score spot among pn 0 - 3, replace with winner.
            // TODO Maybe extend this to just sort non-bot scores & names highest to lowest?

            int pnLow = 0, scoreLow = scores[0];
            for (int pn = 1; pn < 4; ++pn)
            {
                if (scores[pn] < scoreLow)
                {
                    pnLow = pn;
                    scoreLow = scores[pn];
                }
            }

            names[pnLow] = names[winnerPN];
            scores[pnLow] = scores[winnerPN];

            return;  // <---- Early return ----
        }

        // Run through loop twice: pnHigh[0], then pnHigh[1]
        // Record score for humans before robots:
        // - if vacant spot, take that
        // - otherwise take lowest-score bot that didn't win, if any
        // - otherwise if is a robot that didn't win, don't worry about claiming a spot

        for (int i = 0; i < 2; ++i)
        {
            final int pnH = pnHigh[i];
            if (pnH == -1)
                break;

            if (nVacantLow > 0)
            {
                for (int pn = 0; pn < 4; ++pn)
                {
                    if (isVacant[pn])
                    {
                        // pn gets pnH's info
                        names[pn] = names[pnH];
                        scores[pn] = scores[pnH];
                        isBot[pn] = ga.getPlayer(pnH).isRobot();
                        isVacant[pn] = false;
                        if (winnerPN == pnH)
                            winnerPN = pn;

                        --nVacantLow;
                        break;
                    }
                }
            }
            else if (nBotLow > 0)
            {
                // find lowest-scoring bot pn
                int pnLowBot = -1, scoreLowBot = Integer.MAX_VALUE;
                for (int pn = 0; pn < 4; ++pn)
                {
                    if ((pn == winnerPN) || ! isBot[pn])
                        continue;

                    if ((pnLowBot == -1) || (scores[pn] < scoreLowBot))
                    {
                        pnLowBot = pn;
                        scoreLowBot = scores[pn];
                    }
                }

                final boolean pnHIsRobot = ga.getPlayer(pnH).isRobot();
                if ((pnLowBot != -1) && ((! pnHIsRobot) || (winnerPN == pnH) || (scores[pnH] > scores[pnLowBot])))
                {
                    // pnLowBot gets pnH's info,
                    // unless they're both bots and pnH didn't win and pnH's score isn't higher
                    names[pnLowBot] = names[pnH];
                    scores[pnLowBot] = scores[pnH];
                    isBot[pnLowBot] = pnHIsRobot;
                    if (winnerPN == pnH)
                        winnerPN = pnLowBot;

                    --nBotLow;
                }
            }
            // else, no spot is open; this player won't be recorded
        }
    }

    /**
     * Get this robot's specialized parameters from the database, if it has an entry there.
     * Optionally, return defaults if not found or if no database: Default bot params are
     * {@link SOCServer#ROBOT_PARAMS_SMARTER} if the robot name starts with "robot "
     * or {@link SOCServer#ROBOT_PARAMS_DEFAULT} otherwise (starts with "droid ").
     * This matches the bot names generated in {@link SOCServer#setupLocalRobots(int, int)}.
     *
     * @param robotName Name of robot for db lookup
     * @param useDefaults  If true, return the server's default parameters if {@code robotName} not in the table
     *    or if a database isn't in use.
     * @return null if robotName not in database, or if db is empty and robotparams table doesn't exist;
     *    if {@code useDefaults}, will return the server default parameters instead of {@code null}.
     * @throws SQLException if unexpected problem retrieving the params
     */
    public static final SOCRobotParameters retrieveRobotParams(final String robotName, final boolean useDefaults)
        throws SQLException
    {
        SOCRobotParameters params = retrieveRobotParams(robotName);

        if ((params == null) && useDefaults)
            if (robotName.startsWith("robot "))
                params = SOCServer.ROBOT_PARAMS_SMARTER;  // uses SOCRobotDM.SMART_STRATEGY
            else  // startsWith("droid ")
                params = SOCServer.ROBOT_PARAMS_DEFAULT;  // uses SOCRobotDM.FAST_STRATEGY

        return params;
    }

    /** DB query portion (no defaults) of {@link #retrieveRobotParams(String, boolean)}. */
    private static SOCRobotParameters retrieveRobotParams(final String robotName)
        throws SQLException
    {
        SOCRobotParameters robotParams = null;

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            if (robotParamsQuery == null)
                return null;  // <--- Early return: Table not found in db, is probably empty ---

            try
            {
                // fill in the data values to the Prepared statement
                robotParamsQuery.setString(1, robotName);

                // execute the Query
                ResultSet resultSet = robotParamsQuery.executeQuery();

                // if no results, user is not authenticated
                if (resultSet.next())
                {
                    // retrieve the resultset
                    int mgl = resultSet.getInt(2);
                    int me = resultSet.getInt(3);
                    float ebf = resultSet.getFloat(4);
                    float af = resultSet.getFloat(5);
                    float laf = resultSet.getFloat(6);
                    float dcm = resultSet.getFloat(7);
                    float tm = resultSet.getFloat(8);
                    int st = resultSet.getInt(9);
                    int tf = resultSet.getInt(14);
                    robotParams = new SOCRobotParameters(mgl, me, ebf, af, laf, dcm, tm, st, tf);
                }

                resultSet.close();
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }

        return robotParams;
    }

    /**
     * Count the number of users, if any, currently in the users table.
     * @return User count, or -1 if not connected.
     * @throws SQLException if unexpected problem counting the users
     * @since 1.1.19
     */
    public static int countUsers()
        throws SQLException
    {
        if (! checkConnection())
            return -1;

        if (userCountQuery == null)
            return -1;  // <--- Early return: Table not found in db, is probably empty ---

        try
        {
            ResultSet resultSet = userCountQuery.executeQuery();

            int count = -1;
            if (resultSet.next())
                count = resultSet.getInt(1);

            resultSet.close();
            return count;
        }
        catch (SQLException sqlE)
        {
            errorCondition = true;
            sqlE.printStackTrace();
            throw sqlE;
        }
    }

    /**
     * Query to see if a table exists in the database.
     * Any exception is caught here and returns false.
     * @param tabname  Table name to check for; case-sensitive in some db types.
     *    The jsettlers standard is to always use lowercase names when creating tables and columns.
     * @return  true if table exists in the current connection's database
     * @throws IllegalStateException  If not connected and if {@link #checkConnection()} fails
     * @see #doesTableColumnExist(String, String)
     * @since 2.0.00
     */
    public static boolean doesTableExist(final String tabname)
        throws IllegalStateException
    {
        try
        {
            if (! checkConnection())
                throw new IllegalStateException();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        ResultSet rs = null;
        boolean found = false;

        try
        {
            rs = connection.getMetaData().getTables(null, null, tabname, null);
            while (rs.next())
            {
                // Check name, in case of multiple rows (wildcard from '_' in name).
                // Use equalsIgnoreCase for case-insensitive db catalogs; assumes
                // this db follows jsettlers table naming rules so wouldn't have two
                // tables with same names differing only by upper/lowercase.

                final String na = rs.getString("TABLE_NAME");
                if ((na != null) && na.equalsIgnoreCase(tabname))
                {
                    found = true;
                    break;
                }
            }
            rs.close();
        }
        catch (Exception e)
        {
            if (rs != null)
            {
                try
                {
                    rs.close();
                }
                catch (SQLException se) {}
            }
        }

        return found;
    }

    /**
     * Query to see if a column exists in a table.
     * Any exception is caught here and returns false.
     * @param tabname  Table name to check <tt>colname</tt> within; case-sensitive in some db types
     * @param colname  Column name to check; case-sensitive in some db types.
     *    The jsettlers standard is to always use lowercase names when creating tables and columns.
     * @return  true if column exists in the current connection's database
     * @throws IllegalStateException  If not connected and if {@link #checkConnection()} fails
     * @see #doesTableExist(String)
     * @since 1.1.14
     */
    public static boolean doesTableColumnExist
        (final String tabname, final String colname)
        throws IllegalStateException
    {
        try
        {
            if (! checkConnection())
                throw new IllegalStateException();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        ResultSet rs = null;
        try
        {
            final boolean checkResultNum;  // Do we need to check query result contents?

            PreparedStatement ps;
            if (dbType != DBTYPE_ORA)
            {
                ps = connection.prepareStatement
                    ("select " + colname + " from " + tabname + " LIMIT 1;");
                checkResultNum = false;
            } else {
                ps = connection.prepareStatement
                    ("select count(*) FROM user_tab_columns WHERE table_name='"
                     + tabname + "' AND column_name='"
                     + colname + "';");
                checkResultNum = true;
            }

            rs = ps.executeQuery();
            if (checkResultNum)
            {
                if (! rs.next())
                {
                    rs.close();
                    return false;
                }
                int count = rs.getInt(1);
                if (count == 0)
                {
                    rs.close();
                    return false;
                }
            }
            rs.close();

        } catch (Throwable th) {

            if (rs != null)
            {
                try
                {
                    rs.close();
                }
                catch (SQLException e) {}
            }

            return false;
        }

        return true;
    }

    /**
     * Query all users to find any 'duplicate' user names, according to
     * {@link String#toLowerCase(java.util.Locale) String.toLowercase}
     * ({@link java.util.Locale#US Locale.US}).
     * Return any if found.
     * @param out_allNames if not {@code null}, will place all usernames in the database into this set
     * @return {@code null} if no dupes, or a Map of any lowercased names
     *     to all the non-lowercased names which all map to that lowercased name
     * @since 1.2.00
     */
    public static Map<String,List<String>> queryUsersDuplicateLCase(final Set<String> out_allNames)
        throws IllegalStateException, SQLException
    {
        try
        {
            if (! checkConnection())
                throw new IllegalStateException();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        HashMap<String,String> namesFromLC = new HashMap<String,String>();  // lowercase -> non-lowercase name
        Map<String,List<String>> dupeMap = new HashMap<String,List<String>>();  // duplicates from namesFromLC

        Statement s = connection.createStatement();
        ResultSet rs = null;
        try
        {
            rs = s.executeQuery("SELECT nickname FROM users");
            while (rs.next())
            {
                String nm = rs.getString(1);
                String nmLC = nm.toLowerCase(Locale.US);
                if (namesFromLC.containsKey(nmLC))
                {
                    List<String> li = dupeMap.get(nmLC);
                    if (li == null)
                    {
                        li = new ArrayList<String>();
                        li.add(namesFromLC.get(nmLC));  // previously-found name with this lc
                        dupeMap.put(nmLC, li);
                    }
                    li.add(nm);
                } else {
                    namesFromLC.put(nmLC, nm);
                }

                if (out_allNames != null)
                    out_allNames.add(nm);
            }

        } finally {
            try {
                if (rs != null)
                    rs.close();
            } catch (SQLException e) {}
            try {
                s.close();
            } catch (SQLException e) {}
        }

        namesFromLC.clear();
        return (dupeMap.isEmpty()) ? null : dupeMap;
    }

    /**
     * Run a DDL command to create or remove a database structure.
     * @param sql  SQL to run
     * @throws IllegalStateException if not connected and if {@link #checkConnection()} fails
     * @throws SQLException if an error occurs while running {@code sql}
     * @since 1.2.00
     * @see #runDDL_rollback(String)
     */
    private static void runDDL(final String sql)
        throws IllegalStateException, SQLException
    {
        try
        {
            if (! checkConnection())
                throw new IllegalStateException();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        Statement s = connection.createStatement();
        try
        {
            s.execute(sql);
        } finally {
            try {
                s.close();
            } catch (SQLException e) {}
        }
    }

    /**
     * Close out and shut down the database connection.
     * @param isForShutdown  If true, set <tt>connection = null</tt>
     *          so we won't try to reconnect later.
     */
    public static void cleanup(final boolean isForShutdown) throws SQLException
    {
        if (checkConnection())
        {
            try
            {
                createAccountCommand.close();
                userPasswordQuery.close();
                hostQuery.close();
                lastloginUpdate.close();
                saveGameCommand.close();
                robotParamsQuery.close();
                userCountQuery.close();
            }
            catch (Throwable thr)
            {
                ; /* ignore failures in query closes */
            }

            try
            {
                connection.close();
                initialized = false;
                if (isForShutdown)
                    connection = null;
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
                initialized = false;
                if (isForShutdown)
                    connection = null;
                sqlE.printStackTrace();
                throw sqlE;
            }
        }
    }

    //-------------------------------------------------------------------
    // dispResultSet
    // Displays all columns and rows in the given result set
    //-------------------------------------------------------------------
    static void dispResultSet(ResultSet rs) throws SQLException
    {
        System.out.println("dispResultSet()");

        int i;

        // used for the column headings
        ResultSetMetaData rsmd = rs.getMetaData();

        // Get the number of columns in the result set
        int numCols = rsmd.getColumnCount();

        // Display column headings
        for (i = 1; i <= numCols; i++)
        {
            if (i > 1)
            {
                System.out.print(",");
            }

            System.out.print(rsmd.getColumnLabel(i));
        }

        System.out.println("");

        // Display data, fetching until end of the result set

        boolean more = rs.next();

        while (more)
        {
            // Loop through each column, getting the
            // column data and displaying
            for (i = 1; i <= numCols; i++)
            {
                if (i > 1)
                {
                    System.out.print(",");
                }

                System.out.print(rs.getString(i));
            }

            System.out.println("");

            // Fetch the next result set row
            more = rs.next();
        }
    }

    /**
     * Unit testing: Call {@link #doesTableExist(String)} and print results.
     * @param tabname  Table name to check
     * @param wantSuccess  True if expecting it to be found
     * @param isRequired  True if test is required, not optional (affects only the output text, not return value)
     * @return true if call result == {@code wantSuccess}
     * @throws IllegalStateException if not connected; see {@link #doesTableExist(String)} javadoc
     * @since 2.0.00
     */
    private static boolean testOne_doesTableExist
        (final String tabname, final boolean wantSuccess, final boolean isRequired)
        throws IllegalStateException
    {
        final boolean exists = SOCDBHelper.doesTableExist(tabname),
            pass = (exists == wantSuccess);
        System.err.println
            ( ((pass) ? "test ok" : ((isRequired) ? "test FAIL" : "test failed but optional: ok"))
              + ": doesTableExist(" + tabname + "): " + exists);
        return (pass);
    }

    /**
     * Unit testing: Call {@link #doesTableColumnExist(String, String)} and print results.
     * @param tabname  Table name to check
     * @param colname  Column name to check
     * @param wantSuccess  True if expecting it to be found
     * @param isRequired  True if test is required, not optional (affects only the output text, not return value)
     * @return true if call result == {@code wantSuccess}
     * @throws IllegalStateException if not connected; see {@link #doesTableColumnExist(String, String)} javadoc
     * @since 2.0.00
     */
    private static boolean testOne_doesTableColumnExist
        (final String tabname, final String colname, final boolean wantSuccess, final boolean isRequired)
        throws IllegalStateException
    {
        final boolean exists = SOCDBHelper.doesTableColumnExist(tabname, colname),
            pass = (exists == wantSuccess);
        System.err.println
            ( ((pass) ? "test ok" : ((isRequired) ? "test FAIL" : "test failed but optional: ok"))
              + ": doesTableColumnExist(" + tabname + ", " + colname + "): " + exists);
        return (pass);
    }

    /**
     * For {@link #testDBHelper()}, run a DDL command to create or remove a test fixture.
     * See {@link #runDDL(String)} for exception descriptions.
     * @param desc Description to print as part of testing log; will be preceded by "For testing: "
     * @param sql  SQL to run
     * @since 2.0.00
     */
    private static void testDBHelper_runDDL(final String desc, final String sql)
        throws IllegalStateException, SQLException
    {
        System.err.println("For testing: " + desc);
        runDDL(sql);
    }

    /**
     * Tests for a working database connection, unit tests for {@link SOCDBHelper} methods.
     * Prints success or failure to {@link System#err}.
     *<P>
     * To run these tests, DB connection info must be initialized and the DB schema should
     * contain what's in {@code jsettlers-tables.sql}: {@code games}, etc.
     *<P>
     * Called from {@link SOCServer#initSocServer(String, String, Properties)}
     * if {@link SOCServer#PROP_JSETTLERS_TEST_DB} flag is set.
     *<P>
     * <B>Security note:</B> To run some tests, the DB connect username must have authorization grants to
     * run DDL commands, add or alter tables, etc.
     *
     * @throws SQLException  if connection fails or any required tests failed
     * @since 2.0.00
     */
    public static final void testDBHelper()
        throws SQLException
    {
        final boolean was_conn_autocommit = connection.getAutoCommit();
        boolean anyFailed = false;

        System.err.println();
        System.err.println
            ("DB testing note: dbType " + dbType + ", driver class: " + driverclass + ", autoCommit: " + was_conn_autocommit);

        // Unit tests: all in one try block because the only expected exception would
        // occur only if the DB connection fails, instead of a per-test condition
        try
        {
            System.err.println();
            anyFailed |= ! testOne_doesTableExist("games", true, true);
            anyFailed |= ! testOne_doesTableExist("gamesxyz", false, true);
            anyFailed |= ! testOne_doesTableExist("gam_es", false, true);  // wildcard

            // Optional tests, OK if these fail: Case-insensitive table name search
            testOne_doesTableExist("GAMES", true, false);
            testOne_doesTableExist("Games", true, false);

            System.err.println();
            anyFailed |= ! testOne_doesTableColumnExist("games", "gamename", true, true);
            anyFailed |= ! testOne_doesTableColumnExist("games", "gamenamexyz", false, true);
            anyFailed |= ! testOne_doesTableColumnExist("gamesxyz", "xyz", false, true);

            // Optional tests, OK if these fail: Case-insensitive column name search
            testOne_doesTableColumnExist("GAMES", "GAMENAME", true, false);
            testOne_doesTableColumnExist("Games", "gameName", true, false);

            // Temporarily add a table and field, then test existence, then batch-insert rows.
            // Assumes current DB user has been granted ability to create and drop tables.
            if (! anyFailed)
            {
                System.err.println();
                boolean hasFixtureTabXYZ = false, hasFixtureFieldXYZW = false;
                boolean switchedAutoCommitOff = false;

                try
                {
                    testDBHelper_runDDL
                        ("fixture: create table gamesxyz2", "CREATE TABLE gamesxyz2 ( name VARCHAR(20) not null );");
                    hasFixtureTabXYZ = true;
                    anyFailed |= ! testOne_doesTableExist("gamesxyz2", true, true);
                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "name", true, true);
                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "xyz", false, true);
                    // test field-add syntax:
                    testDBHelper_runDDL
                        ("fixture: table gamesxyz2 add field xyz", "ALTER TABLE gamesxyz2 ADD COLUMN xyz VARCHAR(20);");
                    testDBHelper_runDDL
                        ("fixture: table gamesxyz2 add field xyzw", "ALTER TABLE gamesxyz2 ADD COLUMN xyzw int;");
                    hasFixtureFieldXYZW = true;
                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "xyz", true, true);
                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "xyzw", true, true);

                    System.err.println();

                    // use try-catch for CREATE UNIQUE INDEX, because we don't have a doesTableIndexExist method
                    try
                    {
                        runDDL("CREATE UNIQUE INDEX gamesxyz2__w ON gamesxyz2(xyzw);");
                        System.err.println("Test ok: Create unique index gamesxyz2__w");
                    } catch (SQLException e) {
                        System.err.println("Test failed: Create unique index gamesxyz2__w: " + e);
                        anyFailed = true;
                    }

                    // batch insert/convert, as seen in upgradeSchema():
                    // ensure jdbc drivers support executeBatch (optional in javadoc) and transactions

                    try
                    {
                        PreparedStatement ps = connection.prepareStatement
                            ("INSERT INTO gamesxyz2(name,xyzw) VALUES(?,?)");

                        // begin transaction
                        if (was_conn_autocommit)
                        {
                            connection.setAutoCommit(false);
                            switchedAutoCommitOff = true;
                        } else {
                            try {
                                connection.commit();  // end previous transaction, if any
                            } catch (SQLException e) {
                                System.err.println("Unexpected error at pre-transaction commit: " + e);
                                e.printStackTrace();
                                throw e;
                            }
                        }

                        for (int i = 0; i < UPG_BATCH_MAX; ++i)
                        {
                            ps.setString(1, "test" + i);
                            ps.setInt(2, i);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        ps.clearBatch();

                        for (int i = 1; i <= UPG_BATCH_MAX; ++i)
                        {
                            ps.setString(1, "test2_" + i);
                            ps.setInt(2, -i);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                        connection.commit();

                        ResultSet rs = connection.createStatement().executeQuery("SELECT count(*) FROM gamesxyz2");
                        rs.next();
                        int n = rs.getInt(1);
                        rs.close();
                        if (n == 2 * UPG_BATCH_MAX)
                            System.err.println("Test ok: executeBatch");
                        else
                            System.err.println
                                ("Test failed: executeBatch: count(*) " + n + " expected " + (2 * UPG_BATCH_MAX));
                    } catch (SQLException e) {
                        System.err.println("Test failed: executeBatch: " + e);
                        anyFailed = true;
                    }

                    // see if 2 commit()s in a row are OK
                    try {
                        connection.commit();
                        connection.commit();
                        System.err.println("Test ok: empty commits");
                    } catch (SQLException e) {
                        System.err.println("Test failed: empty commits: " + e);
                        anyFailed = true;
                    }

                } finally {
                    System.err.println();

                    // end of transaction tests: restore previous mode
                    if (switchedAutoCommitOff)
                    {
                        try
                        {
                            connection.setAutoCommit(true);
                            System.err.println("Cleanup ok: Restore autoCommit mode");
                        } catch (SQLException e) {
                            System.err.println("Cleanup failed: Restore autoCommit mode: " + e);
                            anyFailed = true;
                        }
                    }

                    if (hasFixtureTabXYZ)
                    {
                        // test index-drop syntax:
                        try
                        {
                            testDBHelper_runDDL("fixture cleanup: drop index gamesxyz2__w",
                                "DROP INDEX gamesxyz2__w;");
                        } catch (SQLException e) {
                            System.err.println("Cleanup failed: Drop index gamesxyz2__w: " + e);
                            anyFailed = true;
                        }

                        if (hasFixtureFieldXYZW && (dbType != DBTYPE_SQLITE))
                        {
                            // test field-drop syntax:
                            testDBHelper_runDDL("drop table field gamesxyz2.xyzw",
                                "ALTER TABLE gamesxyz2 DROP COLUMN xyzw;");
                            anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "xyzw", false, true);
                        } else {
                            System.err.println("test skipped for sqlite: drop table field gamesxyz2.xyzw");
                        }
                        testDBHelper_runDDL("fixture cleanup: drop table gamesxyz2", "DROP TABLE gamesxyz2;");
                        anyFailed |= ! testOne_doesTableExist("gamesxyz2", false, true);
                    }
                }
            } else {
                System.err.println("6 tests skipped because not creating fixture after previous failures.");
            }

        } catch (Exception e) {
            soc.debug.D.ebugPrintStackTrace(e, "test caught exception: testDBHelper");
            if (e instanceof SQLException)
            {
                throw (SQLException) e;
            } else {
                SQLException sx = new SQLException("Error during testDBHelper()");
                sx.initCause(e);
                throw sx;
            }
        }

        System.err.println();
        if (anyFailed)
        {
            System.err.println("* Some required DB tests failed.");
            throw new SQLException("Required test(s) failed");
        } else {
            System.err.println("* All required DB tests passed.");
        }
    }

}
