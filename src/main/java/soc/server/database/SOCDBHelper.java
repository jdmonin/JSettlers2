/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2012,2014-2017,2019-2021 Jeremy D Monin <jeremy@nand.net>
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
import soc.game.SOCGameOption;
import soc.game.SOCGameOptionSet;
import soc.game.SOCPlayer;
import soc.server.SOCServer;  // solely for javadocs, ROBOT_PARAMS_*, and getSettingsFormatted callback
import soc.util.IntPair;
import soc.util.SOCRobotParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * This class contains methods for connecting to a database
 * and for manipulating the data stored there.
 * See {@code /Readme.md} for more info on how JSettlers uses this optional database,
 * and {@code /doc/Readme.developer.md} for developing and testing with it.
 *<P>
 * Originally based on jdbc code found at www.javaworld.com
 *<P>
 * This code assumes that you're using mySQL as your database,
 * but allows you to use other database types.
 * The default URL is "jdbc:mysql://localhost/socdata".
 * The default driver is "com.mysql.jdbc.Driver".
 * These can be changed by supplying properties to {@link #initialize(String, String, Properties)}
 * for {@link #PROP_JSETTLERS_DB_URL} and {@link #PROP_JSETTLERS_DB_DRIVER}.
 *<P>
 * For database schema, see {@code src/main/bin/sql/template/jsettlers-tables-tmpl.sql}.
 *
 *<H3>Schema Upgrades:</H3>
 * Sometimes a new JSettlers version adds to the DB schema. When starting the JSettlers server, call
 * {@link #isSchemaLatestVersion()} to check, and if needed {@link #upgradeSchema(Set)}.
 * To improve flexibility, currently we let the server's admin defer upgrades and continue running
 * with the old schema until they have time to upgrade it.
 *<P>
 * After an upgrade, further background tasks such as data conversions may be needed.
 * These can run while the server is operating normally (running games, bots, and channels).
 * See {@link #doesSchemaUpgradeNeedBGTasks()}.
 *
 *<H3>Testing:</H3>
 * For unit tests, call {@link #testDBHelper()} on a temporary copy of a database.
 *
 *<H3>Threads:</H3>
 * Remember that some JDBC drivers may not cleanly handle multi-threaded access
 * (some versions of the SQLite driver, for example). Use {@link SOCServer}'s
 * Treater thread when accessing the DB, especially for updates or inserts.
 *
 *<H3>Settings:</H3>
 * When using {@link #SCHEMA_VERSION_1200} and above, the DB has a {@code settings} table to
 * store things like {@link #SETTING_BCRYPT_WORK__FACTOR}. See {@link #checkSettings(boolean, boolean)},
 * {@link #getSettingsFormatted(SOCServer)}, and {@link #PROP_JSETTLERS_DB_SETTINGS}.
 *
 *<H3>Password Hashing:</H3>
 * When using {@link #SCHEMA_VERSION_1200} and above, user account passwords are hashed
 * with {@link BCrypt}. See {@link #PW_SCHEME_BCRYPT} for more details.
 * Password hashing details are encapsulated here: SOCServer itself only needs to optionally
 * determine a Work Factor once, pass it to {@link #initialize(String, String, Properties)}
 * using {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR}, and save it to the settings table
 * using {@link #checkSettings(boolean, boolean) checkSettings(true, true)}.
 *
 *<H3>Transactions:</H3>
 * Some database types and drivers may use an "auto-commit" mode by default, others might not, for individual
 * SQL commands. To account for this when a longer transaction is needed, and abstract it a bit,
 * call methods {@link #enterTransactionMode()} and {@link #exitTransactionMode(boolean)} around your
 * transaction's SQL commands. See those methods' javadocs for details.
 *
 *<H3>Class history:</H3>
 * Before v2.5.00, {@code SOCDBHelper} methods were static. That version changed to non-static
 * so any third-party developers could extend or change DB functionality.
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
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_DRIVER = "jsettlers.db.driver";

    /** Property <tt>jsettlers.db.url</tt> to specify the server's URL.
     * The default URL is "jdbc:mysql://localhost/socdata".
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_URL = "jsettlers.db.url";

    /**
     * Utility property <tt>jsettlers.db.settings</tt> to write the values of settings properties
     * like {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR} to the {@code settings} table.
     * Run SOCServer with this property to update the table after startup fails with
     * a {@link DBSettingMismatchException}.
     *<P>
     * This property's only valid value is {@code "write"} (value does not include the double quote marks).
     * Other values will be rejected during {@link #initialize(String, String, Properties)}.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DB_SETTINGS = "jsettlers.db.settings";

    /**
     * Integer property <tt>jsettlers.db.bcrypt.work_factor</tt> to set or test the {@link BCrypt} work factor
     * (password hashing round count's power of 2, see {@link BCrypt#gensalt(int)} for details).
     * Used with {@link #PW_SCHEME_BCRYPT}.
     *<P>
     * This property overrides {@link #SETTING_BCRYPT_WORK__FACTOR}'s value.
     *<P>
     * If this prop's value is {@code "test"} instead of an integer, server calls {@link #testBCryptSpeed()}.
     *<P>
     * During initial DB setup or schema upgrade from {@link #SCHEMA_VERSION_ORIGINAL}, the work factor
     * is saved to the {@code settings} table.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR = "jsettlers.db.bcrypt.work_factor";

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
     * Boolean property {@code jsettlers.db.upgrade_schema} to run {@link #upgradeSchema(Set)}
     * at server startup, then exit. To activate this mode, set this to true.
     *<P>
     * Same SOCServer semantics/exceptions as {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP},
     * see that property's javadoc for details.
     * @since 1.2.00
     */
    public static final String PROP_JSETTLERS_DB_UPGRADE__SCHEMA = "jsettlers.db.upgrade_schema";

    /**
     * Property <tt>jsettlers.db.save.games</tt> to ask to save
     * all game results and scores in the database.
     * Set this to 1 or Y to activate this feature.
     *<P>
     * If not set, but DB schema is new enough to save users' win-loss counts
     * ({@link #SCHEMA_VERSION_2000}), those per-user counts will still be updated.
     *
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
     * For convenience/reference, the JVM property name that xerial sqlite-jdbc uses
     * when extracting its native library to a non-default temp directory.
     *<P>
     * JSettlers server v2.2.00 and newer will set the JVM property if it's not already set
     * but is in {@code jsserver.properties} file or the server command line.
     * @since 2.2.00
     */
    public static final String PROP_SQLITE_TMPDIR = "org.sqlite.tmpdir";

    /**
     * Original JSettlers schema version (1.0.00), before any new extra tables/fields.
     * {@code games} table has columns for only 4 players' names and scores, not 6.
     *<P>
     * Next version (but not latest) is {@link #SCHEMA_VERSION_1200}.
     *
     * @see #SCHEMA_VERSION_LATEST
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_ORIGINAL = 1000;

    /**
     * First updated JSettlers schema version (1.2.00) which adds any new extra tables/fields.
     *<UL>
     * <LI> {@code db_version} table with upgrade history
     * <LI> {@code settings} table
     * <LI> Added fields to {@code games} and {@code users}; see {@code Versions.md} for details
     *</UL>
     * Next version is {@link #SCHEMA_VERSION_2000}.
     *
     * @see #SCHEMA_VERSION_ORIGINAL
     * @see #SCHEMA_VERSION_LATEST
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_1200 = 1200;

    /**
     * JSettlers schema version 2.0.00, with extra tables/fields added since {@link #SCHEMA_VERSION_1200}.
     *<UL>
     * <LI> {@code users}: Added games_won, games_lost fields
     * <LI> {@code games}: Obsoleted by {@code games2}. Upgrade won't delete it, but new games won't be added to it
     * <LI> {@code games2}: Normalized {@code games} table with per-player sub-table, also added scenario field
     * <LI> {@code games2_players}: Sub-table: Score for 1 player in a game
     *</UL>
     *
     * @see #SCHEMA_VERSION_ORIGINAL
     * @see #SCHEMA_VERSION_LATEST
     * @since 2.0.00
     */
    public static final int SCHEMA_VERSION_2000 = 2000;

    /**
     * Latest version of the JSettlers schema, currently 2.0.00 ({@link #SCHEMA_VERSION_2000}).
     * @see #isSchemaLatestVersion()
     * @since 1.2.00
     */
    public static final int SCHEMA_VERSION_LATEST = SCHEMA_VERSION_2000;
        // Value should match the version hardcoded into
        // jsettlers-tables-tmpl.sql "INSERT INTO db_version" command

    // Password encoding schemes, as seen in schema v1200's users.pw_scheme field
    // If a scheme is added, do where-used on these constants to find places that might need an update

    /**
     * No password encoding scheme: plain text.
     * This scheme is {@code null} in {@code users.pw_scheme} database field,
     * password is stored in {@code users.password}.
     * Maximum length is {@link #PW_MAX_LEN_SCHEME_NONE}; clients before v1.2.00
     * truncated longer passwords before sending them to the server.
     *<P>
     * Used in versions before {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     * @see #PW_SCHEME_BCRYPT
     */
    public static final int PW_SCHEME_NONE = 0;

    /**
     * Password encoding scheme #1: {@link BCrypt}.
     * Scheme is stored in {@code users.pw_scheme} database field,
     * encoded password stored in {@code users.pw_store}.
     * Work Factor can be specified with {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR}
     * or <tt>settings({@link #SETTING_BCRYPT_WORK__FACTOR})</tt>,
     * and tested with {@link #testBCryptSpeed()}.
     * Maximum password length is {@link #PW_MAX_LEN_SCHEME_BCRYPT}.
     *<P>
     * The old field {@code users.password} is unused, ignored, and contains '!'
     * because the older schema specified NOT NULL and sqlite can't alter fields.
     *<P>
     * Used with {@link #SCHEMA_VERSION_1200} and higher.
     * @since 1.2.00
     * @see #PW_SCHEME_NONE
     */
    public static final int PW_SCHEME_BCRYPT = 1;

    /**
     * Minimum Work Factor (9) allowed for {@link #PW_SCHEME_BCRYPT} encoding in JSettlers:
     * see {@link #BCRYPT_DEFAULT_WORK_FACTOR} for details. Anything below 9 is too fast.
     * @since 1.2.00
     */
    public static final int BCRYPT_MIN_WORK_FACTOR = 9;

    /**
     * Default Work Factor (12) for {@link #PW_SCHEME_BCRYPT} encoding in JSettlers:
     * Password hashing round count's power of 2, see {@link BCrypt#gensalt(int)} for details.
     * @see BCrypt#GENSALT_MAX_LOG2_ROUNDS
     * @see #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR
     * @see #SETTING_BCRYPT_WORK__FACTOR
     * @see #testBCryptSpeed()
     * @since 1.2.00
     */
    public static final int BCRYPT_DEFAULT_WORK_FACTOR = 12;

    // Password max lengths for various pw_schemes

    /**
     * Original max length for a JSettlers account password when using {@link #PW_SCHEME_NONE}.
     * @see #isPasswordLengthOK(String)
     * @since 1.2.00
     */
    public static final int PW_MAX_LEN_SCHEME_NONE = 20;

    /**
     * Max length for a JSettlers account password when using {@link #PW_SCHEME_BCRYPT}. {@link BCrypt} encrypts
     * the password's bytes as encoded in UTF-8. Check against that length, not {@link String#length()};
     * {@link #isPasswordLengthOK(String)} does so.
     *<P>
     * Higher lengths (72) were tested successfully in {@link soctest.db.TestBCryptMisc} with ASCII characters,
     * this max value is lower for compatibility with other BCrypt implementations.
     * @since 1.2.00
     */
    public static final int PW_MAX_LEN_SCHEME_BCRYPT = 50;

    // Keys for settings table (schema v1200+)

    /**
     * {@code Settings} table key for the {@link #PW_SCHEME_BCRYPT} Work Factor.
     * If present, {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR} overrides this setting's value;
     * see its javadoc for details on the Work Factor.
     * @see #BCRYPT_DEFAULT_WORK_FACTOR
     * @since 1.2.00
     */
    public static final String SETTING_BCRYPT_WORK__FACTOR = "BCRYPT.WORK_FACTOR";

    // Known DB types: These constants aren't used outside the class or stored anywhere,
    // so they can change between versions if needed. All @since 1.2.00 unless noted.

    /**
     * Known DB type mariadb for {@link #dbType}.
     * Treated as functionally same as {@link #DBTYPE_MYSQL}.
     * @since 2.0.00
     */
    private static final char DBTYPE_MARIADB = 'A';

    /**
     * Known DB type mysql for {@link #dbType}.
     * @see #DBTYPE_MARIADB
     */
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
     * dbtype-specific DDL SQL syntax tokens for upgradeSchema and testDBHelper.
     * Set in {@link #initialize(String, String, Properties) initialize(..)},
     * once {@link #dbType} is known: To add or change a token, see comments in that method.
     * Any token declared here must use the same name and value as in render.py DB_TOKENS.
     * @since 2.0.0
     */
        // TOKEN DECLARATION LIST -- test_token_consistency.py
    private static String INT_AUTO_PK, TIMESTAMP_NULL, TIMESTAMP;

    /**
     * During {@link #upgradeSchema(Set)}, if a data conversion batch gets this many rows, execute and start a new batch.
     * @since 1.2.00
     */
    private static final int UPG_BATCH_MAX = 100;

    /**
     * The db driver type if detected, or null char if never connected. Used when certain DB types
     * need special consideration. If DB has been initialized, value will be {@link #DBTYPE_MYSQL},
     * {@link #DBTYPE_SQLITE}, {@link #DBTYPE_POSTGRESQL}, etc, or {@link #DBTYPE_UNKNOWN}.
     *<P>
     * Set in {@link #initialize(String, String, Properties)} based on db URL and jdbc driver name.
     * @see #driverclass
     * @since 1.2.00
     */
    private char dbType;

    /**
     * The db driver used, or null if none.
     * If {@link #driverinstance} != null, use that to connect instead of driverclass;
     * we still need to remember driverclass to detect various db-specific behaviors.
     * Set in {@link #initialize(String, String, Properties)}.
     * @see #dbType
     * @since 1.1.14
     */
    private String driverclass = null;

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
    private Driver driverinstance = null;

    /**
     * db connection, or <tt>null</tt> if never initialized or if cleaned up for shutdown.
     * If this is non-null but closed, most queries will try to recreate it via {@link #checkConnection()}.
     * Set in {@link #connect(String, String, String)}, based on the {@link #dbURL}
     * from {@link #initialize(String, String, Properties)}.
     * Cleared in {@link #cleanup(boolean) cleanup(true)}.
     */
    private Connection connection = null;

    /**
     * Retain the URL (default, or passed via props to {@link #initialize(String, String, Properties)}).
     * Used in {@link #connect(String, String, String)}.
     *<P>
     * If {@link #driverinstance} != null, go through it to connect to dbURL.
     * @since 1.1.09
     */
    private String dbURL = null;

    /**
     * This flag indicates that the connection should be valid, yet the last
     * operation failed. Methods will attempt to reconnect prior to their
     * operation if this is set.
     */
    private boolean errorCondition = false;

    /**
     * True if we successfully completed {@link #initialize(String, String, Properties)}
     * without throwing an exception.
     * Set false in {@link #cleanup(boolean)}.
     * @see #isInitialized()
     */
    private boolean initialized = false;

    /**
     * The detected schema version of the currently connected database.
     * See {@link #getSchemaVersion()} javadocs for details.
     * Is set in {@link #connect(String, String, String)}.
     * @see #schemaUpgBGTasks_fromVersion
     * @since 1.2.00
     */
    private int schemaVersion;

    /**
     * Work Factor for encrypting user passwords with {@link #PW_SCHEME_BCRYPT}.
     * Default value is {@link #BCRYPT_DEFAULT_WORK_FACTOR}; see that constant's javadoc
     * for details and related methods/fields.
     *<P>
     * Set from {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR} if present, or from
     * {@link #SETTING_BCRYPT_WORK__FACTOR}, in {@link #initialize(String, String, Properties)}.
     * @since 1.2.00
     */
    private int bcryptWorkFactor = BCRYPT_DEFAULT_WORK_FACTOR;

    /**
     * If not 0, the version from which a recent schema upgrade must perform background tasks to complete
     * (data conversions, etc). See {@link #doesSchemaUpgradeNeedBGTasks()} for details.
     *<P>
     * If there are multiple schema versions between {@code from_vers} and {@code to_vers},
     * the background tasks thread can set this field to an intermediate version after
     * finishing all tasks of an upgrade from {@code from_vers} to that version, then
     * continue tasks towards {@code to_vers}.
     * @see #schemaUpgBGTasksThread
     * @see #schemaVersion
     * @since 1.2.00
     */
    private volatile int schemaUpgBGTasks_fromVersion;

    /**
     * Schema upgrade background tasks thread, if any; see {@link #doesSchemaUpgradeNeedBGTasks()} for details.
     * Started by {@link #startSchemaUpgradeBGTasks()}.
     *<P>
     * <B>Locks:</B> Writes to this field are synchronized on {@link #connection}.
     * @see #schemaUpgBGTasks_fromVersion
     * @since 1.2.00
     */
    private volatile UpgradeBGTasksThread schemaUpgBGTasksThread;

    /**
     * Thread executor to queue and call {@link BCrypt} without tying up the main thread
     * in {@link #authenticateUserPassword(String, String, AuthPasswordRunnable)}.
     * @see AuthPasswordRunnable
     * @since 1.2.00
     */
    private final static ExecutorService bcryptQueueThreader = Executors.newSingleThreadExecutor();

    /**
     * Cached DB connection username, used when reconnecting on error.
     * Before v1.2.00 this field was {@code userName}.
     */
    private String dbcUserName;

    /**
     * Cached DB connection password, used when reconnecting on error.
     * Before v1.2.00 this field was {@code password}.
     */
    private String dbcPassword;

    /**
     * Properties containing {@link #PROP_JSETTLERS_DB_DRIVER}, {@link #PROP_JSETTLERS_DB_URL},
     * and any other desired properties given to {@link #initialize(String, String, Properties)},
     * or {@code null}.
     * @since 1.2.00
     */
    private Properties props;

    /**
     * {@link #createAccountCommand} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code CREATE_ACCOUNT_COMMAND}.
     */
    private static final String CREATE_ACCOUNT_COMMAND_1000 =
        "INSERT INTO users(nickname,host,password,email,lastlogin) VALUES (?,?,?,?,?);";

    /**
     * {@link #createAccountCommand} for schema {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String CREATE_ACCOUNT_COMMAND_1200 =
        "INSERT INTO users(nickname,host,password,email,lastlogin,nickname_lc,pw_scheme,pw_store) VALUES (?,?,'!',?,?,?,?,?);";

    /**
     * {@link #createAccountCommand} for schema &gt;= {@link #SCHEMA_VERSION_2000}.
     * @since 2.0.00
     */
    private static final String CREATE_ACCOUNT_COMMAND_2000 =
        "INSERT INTO users(nickname,host,password,email,lastlogin,nickname_lc,pw_scheme,pw_store,games_won,games_lost) VALUES (?,?,'!',?,?,?,?,?,0,0);";

    private static final String RECORD_LOGIN_COMMAND = "INSERT INTO logins VALUES (?,?,?);";

    /**
     * {@link #userPasswordQuery} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code USER_PASSWORD_QUERY}.
     */
    private static final String USER_PASSWORD_QUERY_1000 =
        "SELECT nickname,password FROM users WHERE nickname = ? ;";

    /**
     * {@link #userPasswordQuery} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String USER_PASSWORD_QUERY_1200 =
        "SELECT nickname,password,pw_scheme,pw_store FROM users WHERE nickname_lc = ? ;";

    private static final String HOST_QUERY = "SELECT nickname FROM users WHERE ( users.host = ? );";
    private static final String LASTLOGIN_UPDATE = "UPDATE users SET lastlogin = ?  WHERE nickname = ? ;";

    /**
     * {@link #passwordUpdateCommand} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code PASSWORD_UPDATE}.
     * @since 1.1.20
     */
    private static final String PASSWORD_UPDATE_COMMAND_1000 =
        "UPDATE users SET password = ? WHERE nickname = ? ;";

    /**
     * {@link #passwordUpdateCommand} for schema &gt;= {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String PASSWORD_UPDATE_COMMAND_1200 =
        "UPDATE users SET password = '!', pw_scheme = ?, pw_store = ? WHERE nickname_lc = ? ;";

    /**
     * {@link #saveGameCommand} for schema older than {@link #SCHEMA_VERSION_1200}.
     * Before v1.2.00 this field was {@code SAVE_GAME_COMMAND}.
     */
    private static final String SAVE_GAME_COMMAND_1000 =
        "INSERT INTO games(gamename,player1,player2,player3,player4,score1,score2,score3,score4,starttime)"
        + " VALUES (?,?,?,?,?,?,?,?,?,?);";

    /**
     * {@link #saveGameCommand} for schema {@link #SCHEMA_VERSION_1200}.
     * @since 1.2.00
     */
    private static final String SAVE_GAME_COMMAND_1200 =
        "INSERT INTO games(gamename,player1,player2,player3,player4,player5,player6,score1,score2,score3,score4,score5,score6,"
        + "starttime,duration_sec,winner,gameopts) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";

    /**
     * {@link #saveGameCommand} for schema &gt;= {@link #SCHEMA_VERSION_2000}.
     * Primary key is in {@link #SAVE_GAME_COMMAND_2000_GEN_KEY}.
     * @see #SAVE_GAME_PLAYER_COMMAND
     * @since 2.0.00
     */
    private static final String SAVE_GAME_COMMAND_2000 =
        "INSERT INTO games2(gamename,starttime,duration_sec,winner,gameopts,scenario) VALUES (?,?,?,?,?,?);";

    /**
     * Primary-key name array ({@code ["gameid"]}) for {@link #saveGameCommand}'s
     * {@link Connection#prepareStatement(String, String[])} call.
     * @since 2.0.00
     */
    private static final String[] SAVE_GAME_COMMAND_2000_GEN_KEY = {"gameid"};

    /**
     * Per-player {@link #saveGamePlayerCommand}, for use with {@link #SAVE_GAME_COMMAND_2000}.
     * @since 2.0.00
     */
    private static final String SAVE_GAME_PLAYER_COMMAND =
        "INSERT INTO games2_players(gameid,player,score) VALUES (?,?,?);";

    private static final String ROBOT_PARAMS_QUERY = "SELECT * FROM robotparams WHERE robotname = ?;";
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

    /**
     * {@link #userIncrWonCommand} for schema &gt;= {@link #SCHEMA_VERSION_2000}.
     * @see #USER_INCREMENT_LOST_COMMAND
     * @since 2.0.00
     */
    private static final String USER_INCREMENT_WON_COMMAND =
        "UPDATE users SET games_won = 1 + coalesce(games_won, 0) WHERE nickname = ?;";

    /**
     * {@link #userIncrLostCommand} for schema &gt;= {@link #SCHEMA_VERSION_2000}.
     * @see #USER_INCREMENT_WON_COMMAND
     * @since 2.0.00
     */
    private static final String USER_INCREMENT_LOST_COMMAND =
        "UPDATE users SET games_lost = 1 + coalesce(games_lost, 0) WHERE nickname = ?;";

    /** Create a new account in {@code users}: {@link #CREATE_ACCOUNT_COMMAND_2000} */
    private PreparedStatement createAccountCommand = null;

    private PreparedStatement recordLoginCommand = null;

    /** Query whether a user nickname exists in {@code users}: {@link #USER_EXISTS_QUERY_1200} */
    private PreparedStatement userExistsQuery = null;

    /**
     * If statement not {@code null}, add 1 to user's {@code games_won}: {@link #USER_INCREMENT_WON_COMMAND}.
     * Is {@code null} if schema &lt; {@link #SCHEMA_VERSION_2000}.
     * @see #userIncrLostCommand
     */
    private PreparedStatement userIncrWonCommand = null;

    /**
     * If statement not {@code null}, add 1 to user's {@code games_lost}: {@link #USER_INCREMENT_LOST_COMMAND}.
     * Is {@code null} if schema &lt; {@link #SCHEMA_VERSION_2000}.
     * @see #userIncrWonCommand
     */
    private PreparedStatement userIncrLostCommand = null;

    /** Query for a user's password and original-cased nickname in {@code users}: {@link #USER_PASSWORD_QUERY_1200} */
    private PreparedStatement userPasswordQuery = null;

    private PreparedStatement hostQuery = null;
    private PreparedStatement lastloginUpdate = null;

    /**
     * User password update in {@code users}: {@link #PASSWORD_UPDATE_COMMAND_1200}.
     * Before v1.2.00 this field was {@code passwordUpdate}.
     * @since 1.1.20
     */
    private PreparedStatement passwordUpdateCommand = null;

    /**
     * Completed-game info insert into {@code games2} or old-schema {@code games}: {@link #SAVE_GAME_COMMAND_2000}
     * or {@link #SAVE_GAME_COMMAND_1200}.
     * @see #saveGamePlayerCommand
     */
    private PreparedStatement saveGameCommand = null;

    /**
     * Completed-game per-player info insert into {@code games2_players}: {@link #SAVE_GAME_PLAYER_COMMAND}.
     * {@code null} if schema is older than {@link #SCHEMA_VERSION_2000}.
     * @see #saveGameCommand
     * @since 2.0.00
     */
    private PreparedStatement saveGamePlayerCommand = null;

    /** Query all robot parameters for a bot name; {@link #ROBOT_PARAMS_QUERY}.
     *  Used in {@link #retrieveRobotParams(String)}.
     */
    private PreparedStatement robotParamsQuery = null;

    /** Query how many users, if any, exist in the {@code users} table: {@link #USER_COUNT_QUERY}.
     *  @since 1.1.19
     */
    private PreparedStatement userCountQuery = null;

    /****************************************
     * Connect and initialize, related methods and getters
     ****************************************/

    /**
     * This makes a connection to the database
     * and initializes the prepared statements.
     * (If <tt>props</tt> includes {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP},
     * runs that script before the prepared statements.)
     * Sets {@link #isInitialized()} and {@link #getSchemaVersion()}. Calls
     * {@link #checkSettings(boolean, boolean) checkSettings(false, setupScript != null)}
     * to look for inconsistent or missing {@code settings} entries and (if running a setup
     * script) save those settings.
     *<P>
     * The default URL is "jdbc:mysql://localhost/socdata".
     * The default driver is "com.mysql.jdbc.Driver".
     * These can be changed by supplying <code>props</code>.
     *
     * @param user  the user name for accessing the database, or {@code null} to not attempt connection
     * @param pswd  the password for the user, or ""
     * @param dbProps  null, or properties containing {@link #PROP_JSETTLERS_DB_DRIVER},
     *       {@link #PROP_JSETTLERS_DB_URL}, and any other desired properties.
     *       Ignores {@link #PROP_JSETTLERS_DB_USER} and {@link #PROP_JSETTLERS_DB_PASS} if present,
     *       uses the {@code user} and {@code pswd} parameters instead.
     *       <P>
     *       If {@code props} contains {@link #PROP_JSETTLERS_DB_SETTINGS} == "write"</tt>,
     *       the {@code settings} table will be updated from props or default values as needed.
     * @throws IllegalArgumentException if there are problems with {@code props} contents:
     *         <UL>
     *           <LI> {@link #PROP_JSETTLERS_DB_URL} property doesn't use a recognized scheme
     *               ({@code jdbc:mariadb}, {@code jdbc:mysql}, {@code :postgresql}, or {@code :sqlite})
     *               but {@link #PROP_JSETTLERS_DB_DRIVER} isn't provided
     *           <LI> {@link #PROP_JSETTLERS_DB_DRIVER} isn't recognized as mariadb, mysql, postgres, or sqlite,
     *               but {@link #PROP_JSETTLERS_DB_URL} isn't provided
     *           <LI> {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR} is out of range
     *               (9 to {@link BCrypt#GENSALT_MAX_LOG2_ROUNDS}) or can't be parsed as an integer
     *           <LI> {@link #PROP_JSETTLERS_DB_SETTINGS} is provided but isn't {@code "write"}
     *         </UL>
     * @throws DBSettingMismatchException if {@code props} contains one or more properties which are
     *         also in the {@code settings} table but with different values; this method's call to
     *         {@link #checkSettings(boolean, boolean)} will print details to {@link System#err} before
     *         throwing the exception. See {@link #PROP_JSETTLERS_DB_SETTINGS} to re-run and recover from
     *         this exception.
     * @throws SQLException if an SQL command fails, or the DB couldn't be initialized;
     *         or if the DB schema version couldn't be detected (if so, exception's
     *         {@link Exception#getCause() .getCause()} will be an {@link IllegalStateException})
     * @throws IOException  if <tt>props</tt> includes {@link #PROP_JSETTLERS_DB_SCRIPT_SETUP} but
     *         the SQL file wasn't found, or if any other IO error occurs reading the script
     */
    public void initialize(final String user, final String pswd, Properties dbProps)
        throws IllegalArgumentException, DBSettingMismatchException, SQLException, IOException
    {
        initialized = false;

        // Driver types and URLs recognized here should
        // be the same as those listed in Readme.md and Database.md.

        driverclass = "com.mysql.jdbc.Driver";
        dbType = DBTYPE_MYSQL;
        dbURL = "jdbc:mysql://localhost/socdata";
        props = dbProps;

        if (user == null)
            return;

        if (dbProps != null)
        {
            String prop_dbURL = dbProps.getProperty(PROP_JSETTLERS_DB_URL);
            String prop_driverclass = dbProps.getProperty(PROP_JSETTLERS_DB_DRIVER);

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
                    else if (driverclass.contains("mariadb"))
                        dbType = DBTYPE_MARIADB;
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
                else if (prop_dbURL.startsWith("jdbc:mariadb"))
                {
                    driverclass = "org.mariadb.jdbc.Driver";
                    dbType = DBTYPE_MARIADB;
                }
                else if (! prop_dbURL.startsWith("jdbc:mysql"))
                {
                    throw new IllegalArgumentException
                        ("JDBC: URL property is set, but driver property is not ("
                         + PROP_JSETTLERS_DB_URL + ", " + PROP_JSETTLERS_DB_DRIVER + ")");
                }
            } else {
                if (prop_driverclass != null)
                    driverclass = prop_driverclass;

                // if it's mysql, use the mysql default url above.
                // if it's mariadb or postgres or sqlite, use appropriate url.
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
                else if (driverclass.contains("mariadb"))
                {
                    dbURL = "jdbc:mariadb://localhost/socdata";
                    dbType = DBTYPE_MARIADB;
                }
                else if (! driverclass.contains("mysql"))
                {
                    throw new IllegalArgumentException
                        ("JDBC: Driver property is set, but URL property is not ("
                         + PROP_JSETTLERS_DB_DRIVER + ", " + PROP_JSETTLERS_DB_URL + ")");
                }
            }

            String prop_bcryptWF = dbProps.getProperty(PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR);
            if (prop_bcryptWF != null)
            {
                String errMsg = null;

                try
                {
                    int wf = Integer.parseInt(prop_bcryptWF);
                    if ((wf >= BCRYPT_MIN_WORK_FACTOR) && (wf <= BCrypt.GENSALT_MAX_LOG2_ROUNDS))
                        bcryptWorkFactor = wf;
                    else
                        errMsg = "Out of range (" + BCRYPT_MIN_WORK_FACTOR + '-' + BCrypt.GENSALT_MAX_LOG2_ROUNDS + ")";
                } catch (NumberFormatException e) {
                    errMsg = "Bad format, integer is required";
                }

                if (errMsg != null)
                    throw new IllegalArgumentException
                        ("DB: BCrypt work factor param: " + errMsg + " ("
                         + PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR + ")");
            }

            String pval = dbProps.getProperty(PROP_JSETTLERS_DB_SETTINGS);
            if ((pval != null) && ! pval.equals("write"))
                throw new IllegalArgumentException
                    ("DB: Utility property " + PROP_JSETTLERS_DB_SETTINGS + "'s value must be \"write\"");
        }

        if (dbType == DBTYPE_UNKNOWN)
        {
            // try to detect unsupported/semi-known types from driver

            if (driverclass.toLowerCase().contains("oracle"))
                dbType = DBTYPE_ORA;
        }

        // Now that we have dbType, initialize DDL syntax tokens:
        // Any token initialized here must use the same name and value as in render.py DB_TOKENS,
        // and be declared at marker comment "TOKEN DECLARATION LIST".
        // test_token_consistency.py (in same dir as render.py) tests for that.
        // To simplify the test script, this block uses a specific style.
        // Comment-only lines are ignored except for "// fallthrough".

        // BEGIN COMPARISON AREA -- test_token_consistency.py

        switch(dbType)
        {
        case DBTYPE_MARIADB:
            // fallthrough
        case DBTYPE_MYSQL:
            INT_AUTO_PK = "INT NOT NULL AUTO_INCREMENT PRIMARY KEY";
            TIMESTAMP = "TIMESTAMP";
            TIMESTAMP_NULL = "TIMESTAMP NULL DEFAULT null";
            break;

        case DBTYPE_POSTGRESQL:
            INT_AUTO_PK = "SERIAL PRIMARY KEY";
            TIMESTAMP = "TIMESTAMP WITHOUT TIME ZONE";
            TIMESTAMP_NULL = "TIMESTAMP WITHOUT TIME ZONE";
            break;

        case DBTYPE_SQLITE:
            INT_AUTO_PK = "INTEGER PRIMARY KEY";
            TIMESTAMP = "TIMESTAMP";
            TIMESTAMP_NULL = "TIMESTAMP";
            break;

        default:
            INT_AUTO_PK = "INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
                // ANSI SQL:2003 syntax; looks supported by DB2, ora 12c, postgres 10 and newer, etc
                // https://dba.stackexchange.com/questions/164862/ansi-sql-auto-numbered-column answer 2017-02-20
            TIMESTAMP = "TIMESTAMP";
            TIMESTAMP_NULL = "TIMESTAMP";
            break;
        }

        // END COMPARISON AREA -- test_token_consistency.py

        driverinstance = null;
        boolean driverNewInstanceFailed = false;
        try
        {
            // Load the JDBC driver
            try
            {
                String prop_jarname = (dbProps != null) ? dbProps.getProperty(PROP_JSETTLERS_DB_JAR) : null;
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
                    driverinstance = (Driver) dclass.getDeclaredConstructor().newInstance();
                } else {
                    // JDBC driver class must already be loaded.
                    driverinstance = (Driver) (Class.forName(driverclass).getDeclaredConstructor().newInstance());
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
            String prop_dbSetupScript = (dbProps != null) ? dbProps.getProperty(PROP_JSETTLERS_DB_SCRIPT_SETUP) : null;
            if ((prop_dbSetupScript != null) && (prop_dbSetupScript.length() == 0))
                prop_dbSetupScript = null;

            // Connect, detect schemaVersion, and prepare table queries;
            // runs setup script, if any, first
            connect(user, pswd, prop_dbSetupScript);

            // Check settings table vs props; if any value mismatches found,
            // prints differences and throws DBSettingMismatchException
            checkSettings(false, (prop_dbSetupScript != null));
        }
        catch (DBSettingMismatchException dx)
        {
            throw dx;
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
     * @return  True if database available
     * @since 1.1.14
     */
    public boolean isInitialized()
    {
        return initialized && (connection != null);
    }

    /**
     * Get the detected schema version of the currently connected database.
     * To upgrade an older schema to the latest available, see {@link #upgradeSchema(Set)}.
     * @return Schema version, such as {@link #SCHEMA_VERSION_ORIGINAL} or {@link #SCHEMA_VERSION_1200}
     * @see #SCHEMA_VERSION_LATEST
     * @see #isSchemaLatestVersion()
     * @see #isPasswordLengthOK(String)
     */
    public int getSchemaVersion()
    {
        return schemaVersion;
    }

    /**
     * Does the currently connected DB have the latest schema, with all optional fields?
     * ({@link #SCHEMA_VERSION_LATEST})
     * @return True if DB schema is the most up-to-date version
     * @throws IllegalStateException  if not connected to DB (! {@link #isInitialized()})
     * @see #upgradeSchema(Set)
     * @see #getSchemaVersion()
     * @see #doesSchemaUpgradeNeedBGTasks()
     * @since 1.2.00
     */
    public boolean isSchemaLatestVersion()
        throws IllegalStateException
    {
        if (! isInitialized())
            throw new IllegalStateException();

        return (schemaVersion == SCHEMA_VERSION_LATEST);
    }

    /**
     * For the currently connected DB, are any background tasks needed to complete a recent {@link #upgradeSchema(Set)}?
     * If so, start those tasks by calling {@link #startSchemaUpgradeBGTasks()}.
     *<P>
     * Background tasks would typically be data conversions to populate new fields, which will gradually activate
     * new functionality such as per-user password encoding. The tasks are self-checkpointing and will cleanly resume
     * if interrupted by server shutdown.
     *<P>
     * This flag is determined at {@link #initialize(String, String, Properties)}, by reading the
     * {@code db_version} table record having {@code max(to_vers)}, checking that record for
     * a null {@code bg_tasks_done} field.
     *
     * @return True if DB schema upgrade needs background tasks
     * @throws IllegalStateException  if not connected to DB (! {@link #isInitialized()})
     * @see #isSchemaLatestVersion()
     * @since 1.2.00
     */
    public boolean doesSchemaUpgradeNeedBGTasks()
        throws IllegalStateException
    {
        if (! isInitialized())
            throw new IllegalStateException();

        return (0 != schemaUpgBGTasks_fromVersion);
    }

    /**
     * If not already running, start a thread to do any background tasks needed to complete a
     * recent {@link #upgradeSchema(Set)}. Thread will start with a 5-second sleep to let other
     * SOCServer startup tasks complete.
     * @return true if a thread was started, false if it was already running or ! {@link #isInitialized()}
     * @see #doesSchemaUpgradeNeedBGTasks()
     * @since 1.2.00
     */
    public boolean startSchemaUpgradeBGTasks()
    {
        if (! isInitialized())
            return false;

        synchronized(connection)
        {
            UpgradeBGTasksThread t = schemaUpgBGTasksThread;
            if ((t != null) && t.isAlive())
                return false;

            t = new UpgradeBGTasksThread();
            schemaUpgBGTasksThread = t;
            t.start();

            return true;
        }
    }

    /**
     * Checks if connection is supposed to be up and available, and attempts to reconnect
     * if there was previously an error.  Reconnecting closes the current
     * {@link #connection}, opens a new one, and re-initializes the prepared statements.
     *
     * @return true if the connection is established, false if DB connection was never initialized
     * @see #isInitialized()
     */
    private boolean checkConnection() throws SQLException
    {
        if (connection != null)
        {
            try
            {
                return (! errorCondition) || connect(dbcUserName, dbcPassword, null);
            } catch (IOException ioe) {
                // will not occur, connect script is null
                return false;
            }
        }

        return false;
    }

    /**
     * Opens a new connection, detects its {@link #schemaVersion}, and initializes the prepared statements.
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
     * @throws SQLException if any connect error, missing table, or SQL error occurs
     * @throws IllegalStateException if schema version can't be determined,
     *     or DB upgrade was started but is incomplete ({@code db_version.ddl_done} field is null)
     * @throws IOException  if <tt>setupScriptPath</tt> wasn't found, or if any other IO error occurs reading the script
     * @return  true on success; will never return false, instead will throw a sqlexception
     */
    private boolean connect(final String user, final String pswd, final String setupScriptPath)
        throws SQLException, IllegalStateException, IOException
    {
        if (driverinstance == null) {
            connection = DriverManager.getConnection(dbURL, user, pswd);
        } else {
            Properties dbProps = new Properties();
            dbProps.put("user", user);
            dbProps.put("password", pswd);
            connection = driverinstance.connect(dbURL, dbProps);
        }

        errorCondition = false;
        dbcUserName = user;
        dbcPassword = pswd;

        if (setupScriptPath != null)
            runSetupScript(setupScriptPath);  // may throw IOException, SQLException

        detectSchemaVersion();
        prepareStatements();

        return true;
    }

    /**
     * Detect connected DB's {@link #schemaVersion} and check its upgrade status.
     * @throws SQLException if any unexpected problem occurs
     * @throws IllegalStateException if schema version can't be determined,
     *     or DB upgrade was started but is incomplete ({@code db_version.ddl_done} field is null)
     * @since 1.2.00
     */
    private void detectSchemaVersion()
        throws SQLException, IllegalStateException
    {
        schemaVersion = -1;

        /* primary schema-version detection: db_version table */
        if (doesTableExist("db_version"))
        {
            ResultSet rs = connection.createStatement().executeQuery
                ("SELECT max(to_vers) FROM db_version;");
            if (rs.next())
            {
                schemaVersion = rs.getInt(1);
                if (rs.wasNull())
                    schemaVersion = -1;
            }
            rs.close();
        }
        if (schemaVersion > 0)
        {
            int from_vers = 0;
            boolean upg_ddl_unfinished = false, upg_bg_unfinished = false;

            ResultSet rs = connection.createStatement().executeQuery
                ("SELECT from_vers, ddl_done, bg_tasks_done FROM db_version WHERE to_vers=" + schemaVersion + ";");
            if (rs.next())
            {
                from_vers = rs.getInt(1);
                rs.getTimestamp(2);
                if (rs.wasNull())
                {
                    upg_ddl_unfinished = true;
                } else {
                    rs.getTimestamp(3);
                    if (rs.wasNull())
                        upg_bg_unfinished = true;
                }
            }
            rs.close();

            if (upg_ddl_unfinished)
                throw new IllegalStateException
                    ("Incomplete DB schema upgrade from version " + from_vers + " to " + schemaVersion
                     + ": db_version.ddl_done field is null");

            if (upg_bg_unfinished)
            {
                // Caller will need to check doesSchemaUpgradeNeedBGTasks()
                // and restart the upgrade-bg-tasks thread if not running.

                schemaUpgBGTasks_fromVersion = from_vers;
                System.err.println("* Warning: DB schema upgrade BG tasks are incomplete per db_version table");
            }
        } else {
            /* fallback schema-version detection: look for added fields */
            if (doesTableColumnExist("users", "nickname_lc"))
                schemaVersion = SCHEMA_VERSION_1200;
            else
                schemaVersion = SCHEMA_VERSION_ORIGINAL;

            if (schemaVersion > SCHEMA_VERSION_ORIGINAL)
                System.err.println
                    ("* Warning: DB schema version appears to be " + schemaVersion + ", but missing from db_version table");
        }
    }

    /**
     * Prepare statements like {@link #createAccountCommand} based on {@link #schemaVersion}.
     * @throws SQLFeatureNotSupportedException  if JDBC driver doesn't support {@link Statement#RETURN_GENERATED_KEYS}
     *     needed for schema v2.0.00. This is a subclass of {@link SQLException}.
     * @throws SQLException if any unexpected problem occurs during {@link Connection#prepareStatement(String)} calls
     * @since 1.2.00
     */
    private void prepareStatements()
        throws SQLFeatureNotSupportedException, SQLException
    {
        createAccountCommand = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_2000)
             ? CREATE_ACCOUNT_COMMAND_2000
             : ((schemaVersion == SCHEMA_VERSION_1200) ? CREATE_ACCOUNT_COMMAND_1200 : CREATE_ACCOUNT_COMMAND_1000));
        recordLoginCommand = connection.prepareStatement(RECORD_LOGIN_COMMAND);
        userExistsQuery = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? USER_EXISTS_QUERY_1200 : USER_EXISTS_QUERY_1000);
        if (schemaVersion >= SCHEMA_VERSION_2000)
        {
            userIncrWonCommand = connection.prepareStatement(USER_INCREMENT_WON_COMMAND);
            userIncrLostCommand = connection.prepareStatement(USER_INCREMENT_LOST_COMMAND);
        }
        userPasswordQuery = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? USER_PASSWORD_QUERY_1200 : USER_PASSWORD_QUERY_1000);
        hostQuery = connection.prepareStatement(HOST_QUERY);
        lastloginUpdate = connection.prepareStatement(LASTLOGIN_UPDATE);
        passwordUpdateCommand = connection.prepareStatement
            ((schemaVersion >= SCHEMA_VERSION_1200) ? PASSWORD_UPDATE_COMMAND_1200 : PASSWORD_UPDATE_COMMAND_1000);
        if (schemaVersion < SCHEMA_VERSION_2000)
        {
            saveGameCommand = connection.prepareStatement
                ((schemaVersion == SCHEMA_VERSION_1200) ? SAVE_GAME_COMMAND_1200 : SAVE_GAME_COMMAND_1000);
        } else {
            // use prepareStatement variant with primary-key field name array,
            // not Statement.RETURN_GENERATED_KEYS, because postgres prefers it
            // (per their developer comments) and other DBs are OK with it.
            saveGameCommand = connection.prepareStatement
                (SAVE_GAME_COMMAND_2000, SAVE_GAME_COMMAND_2000_GEN_KEY);
            saveGamePlayerCommand = connection.prepareStatement(SAVE_GAME_PLAYER_COMMAND);
        }
        robotParamsQuery = connection.prepareStatement(ROBOT_PARAMS_QUERY);
        userCountQuery = connection.prepareStatement(USER_COUNT_QUERY);
    }

    /**
     * Check the {@code settings} table for optional db-related properties and their fields:
     *<UL>
     * <LI> Set {@link #bcryptWorkFactor} from {@code settings}({@link #SETTING_BCRYPT_WORK__FACTOR})
     *      unless {@link #props} contains {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR}.
     *</UL>
     * If <tt>{@link #PROP_JSETTLERS_DB_SETTINGS} == "write"</tt> or {@code writeIfNeeded}, and any
     * settings are missing or have different values in {@link #props}, print differences and update
     * the table from properties or default values as needed.
     *<P>
     * Otherwise if any settings are in the table and also in {@link #props} with different values,
     * print differences to {@link System#err} and throw {@link DBSettingMismatchException}.
     *<P>
     * Called from {@link #initialize(String, String, Properties)}, after {@link #schemaVersion} is determined
     * and any settings fields like {@link #bcryptWorkFactor} have been initialized from {@link #props}.
     * @param checkAll  If true check all DB settings against their fields, not only those in {@code props},
     *     and do not write any updates to the {@code settings} table unless {@code writeIfNeeded}.
     * @param writeIfNeeded  If true check all DB settings like {@code checkAll} and save updates to
     *     the {@code settings} table, as if <tt>{@link #PROP_JSETTLERS_DB_SETTINGS} == "write"</tt>.
     *     Set this when running the initial DB setup script.
     * @throws SQLException  if any unexpected error occurs
     * @throws DBSettingMismatchException  if any value mismatches found in settings table versus props
     * @see #getSettingsFormatted(SOCServer)
     * @since 1.2.00
     */
    public final void checkSettings(boolean checkAll, final boolean writeIfNeeded)
        throws SQLException, DBSettingMismatchException
    {
        final boolean withWrite =
            writeIfNeeded
            || ((checkAll || (props == null)) ? false : props.containsKey(PROP_JSETTLERS_DB_SETTINGS));
        if (writeIfNeeded)
            checkAll = true;

        final ArrayList<String> mm = new ArrayList<String>();  // keyname, db value, props value, keyname, db value, ...
        boolean anyMissing = false;  // is table missing any expected params like SETTING_BCRYPT_WORK__FACTOR?

        // bcryptWorkFactor
        if (schemaVersion >= SCHEMA_VERSION_1200)
        {
            int bc = getIntSetting(SETTING_BCRYPT_WORK__FACTOR, 0);

            if (bc != 0)
            {
                if ((bc >= BCRYPT_MIN_WORK_FACTOR) && (bc <= BCrypt.GENSALT_MAX_LOG2_ROUNDS))
                {
                    if (checkAll || ((props != null) && props.containsKey(PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR)))
                    {
                        if (bc != bcryptWorkFactor)
                        {
                            if (withWrite)
                                updateSetting(SETTING_BCRYPT_WORK__FACTOR, bcryptWorkFactor, false);

                            mm.add(SETTING_BCRYPT_WORK__FACTOR);
                            mm.add(Integer.toString(bc));
                            mm.add(Integer.toString(bcryptWorkFactor));
                        }
                    } else {
                        bcryptWorkFactor = bc;
                    }
                } else {
                    System.err.println
                        ("* Warning: Ignoring DB setting for " + SETTING_BCRYPT_WORK__FACTOR + ": Out of range");
                }
            } else {
                if (withWrite)
                {
                    updateSetting(SETTING_BCRYPT_WORK__FACTOR, bcryptWorkFactor, true);

                    mm.add(SETTING_BCRYPT_WORK__FACTOR);
                    mm.add("-");
                    mm.add(Integer.toString(bcryptWorkFactor));
                } else {
                    anyMissing = true;
                    System.err.println
                        ("* Warning: Missing DB setting for " + SETTING_BCRYPT_WORK__FACTOR
                         + ", using " + bcryptWorkFactor);
                }
            }
        }

        if (! mm.isEmpty())
        {
            System.err.println
                ((writeIfNeeded)
                 ? "\n* These DB settings were added or updated:"
                   + "\nSettings key\t\tDB\tCurrent value"
                 : (checkAll)
                   ? "\n* These DB settings have changed from their current values set during startup:"
                     + "\nSettings key\t\tDB\tCurrent value"
                   : "\n* These DB settings differ from values specified in properties:"
                     + "\nSettings key\t\tDB\tProperty");
            final int L = mm.size();
            for (int i = 0; i < L; ++i)
            {
                System.err.print(mm.get(i));
                System.err.print(((i % 3) == 2) ? '\n' : '\t');
            }
            System.err.println();

            if (withWrite)
            {
                System.err.println("Saving to settings table from properties values.\n");
            } else {
                System.err.println
                    ((checkAll)
                     ? "The next startup will use the changed DB values instead of current values."
                     : ("To save to the settings table, run once with utility property -D"
                        + PROP_JSETTLERS_DB_SETTINGS + "=write"));

                throw new DBSettingMismatchException(mm.get(0));
            }
        } else if (withWrite && ! writeIfNeeded) {
            System.err.println("Warning: Found no settings table updates from properties values.");
        } else if (anyMissing) {
            System.err.println
                ("To save to the settings table, run once with utility property -D"
                 + PROP_JSETTLERS_DB_SETTINGS + "=write");
        }

    }

    /****************************************
     * SOCDBHelper API methods
     ****************************************/

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
     * @see #authenticateUserPassword(String, String, AuthPasswordRunnable)
     */
    public String getUser(String userName)
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
     * @param sPassword  Password being tried, or "" if none.
     *     Different password schemes have different maximum password lengths;
     *     see {@link #isPasswordLengthOK(String)}.
     *     Passwords longer than 256 characters are always rejected here before checking {@code PW_SCHEME}.
     *     If this user has {@link #PW_SCHEME_NONE}, for backwards compatibility {@code sPassword} is
     *     truncated to 20 characters ({@link #PW_MAX_LEN_SCHEME_NONE}).
     * @param authCallback  Optional callback to make after authentication lookups and hashing succeed or fail.
     *     This is useful because {@link BCrypt} password hashing is slow by design, and so is done in another
     *     thread.  If not {@code null}, {@code authCallback} is called at the end of this method,
     *     either in the caller's thread or in a thread dedicated to {@code BCrypt} calls.
     * @return user's nickname if password is correct;
     *     {@code sUserName} if password is "" but user doesn't exist in db
     *     or if database is not currently connected;
     *     {@code null} if account exists in db and password is wrong.
     * @throws SQLException if any unexpected database problem.
     *     <P>
     *     Only the {@code BCrypt} call will be done in a separate thread; all DB activity happens in this method
     *     in the caller's thread, so SQLExceptions will be thrown to the caller and not lost or ignored.
     * @see #updateUserPassword(String, String)
     * @see #getUser(String)
     * @since 1.2.00
     */
    public String authenticateUserPassword
        (final String sUserName, String sPassword, final AuthPasswordRunnable authCallback)
        throws SQLException
    {
        final int L = sPassword.length();
        if (L > 256)
            return null;

        String dbUserName = sUserName;
        String dbPassword = null;  // encoded value, unless user has PW_SCHEME_NONE
        int pwScheme = PW_SCHEME_NONE;
        boolean dbUserFound = false;
        boolean ranBCryptTask = false;  // true if used a task on bcryptQueueThreader,
            // which will call authCallback when done

        if (checkConnection())
        {
            try
            {
                dbUserName = (schemaVersion < SCHEMA_VERSION_1200) ? sUserName : sUserName.toLowerCase(Locale.US);
                userPasswordQuery.setString(1, dbUserName);

                ResultSet resultSet = userPasswordQuery.executeQuery();

                // if no results, nickname isn't in the users table
                if (resultSet.next())
                {
                    dbUserFound = true;
                    dbUserName = resultSet.getString(1);  // get nickname with its original case; searched on nickname_lc
                    dbPassword = resultSet.getString(2);
                    if (schemaVersion >= SCHEMA_VERSION_1200)
                    {
                        pwScheme = resultSet.getInt(3);  // returns 0 for NULL, which is PW_SCHEME_NONE
                        if (pwScheme != PW_SCHEME_NONE)
                            dbPassword = resultSet.getString(4);
                    }
                } else {
                    dbUserName = sUserName;  // not in db: ret original case
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

        boolean ok;

        if (dbUserFound && (dbPassword != null))
        {
            ok = false;

            try
            {
                switch (pwScheme)
                {
                case PW_SCHEME_NONE:
                    if (L > PW_MAX_LEN_SCHEME_NONE)
                        sPassword = sPassword.substring(0, PW_MAX_LEN_SCHEME_NONE);
                    ok = dbPassword.equals(sPassword);
                    break;

                case PW_SCHEME_BCRYPT:
                    try
                    {
                        if ((L <= PW_MAX_LEN_SCHEME_BCRYPT)
                            && (sPassword.getBytes("utf-8").length <= PW_MAX_LEN_SCHEME_BCRYPT))
                        {
                            if (authCallback == null)
                            {
                                ok = BCrypt.checkpw(sPassword, dbPassword);  // may throw IllegalArgumentException
                            } else {
                                ranBCryptTask = true;

                                final String sPass = sPassword, dbUser = dbUserName, dbPass = dbPassword;
                                bcryptQueueThreader.execute(new Runnable()
                                {
                                    public void run()
                                    {
                                        try
                                        {
                                            boolean pwOK = BCrypt.checkpw(sPass, dbPass);
                                                // may throw IllegalArgumentException
                                            authCallback.authResult((pwOK) ? dbUser: null, true);  // <--- Callback ---
                                        } catch (RuntimeException e) {}
                                    }
                                });
                            }
                        }
                    }
                    catch (UnsupportedEncodingException e) {}
                    break;

                default:
                    // pw_scheme not recognized.  TODO print or log something?
                }
            } catch (RuntimeException e) {}
        } else {
            ok = "".equals(sPassword);
        }

        final String ret = (ok) ? dbUserName: null;
        if ((authCallback != null) && ! ranBCryptTask)
            authCallback.authResult(ret, false);  // <--- Callback ---

        return ret;
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
    public String getUserFromHost(String host) throws SQLException
    {
        String nickname = null;

        if (checkConnection())
        {
            try
            {
                hostQuery.setString(1, host);

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
     * If schema &gt;= {@link #SCHEMA_VERSION_1200}, the password will be encoded with {@link #PW_SCHEME_BCRYPT}.
     *<P>
     * <B>Before calling, validate the user doesn't already exist</B>
     * by calling {@link #getUser(String) getUser(userName)}.
     * This method doesn't verify that the user is a unique new user before trying to create the record.
     * The DB will throw an exception instead, especially at {@link #SCHEMA_VERSION_1200} or higher
     * which adds a column and unique index for case-insensitive nickname.
     *
     * @param userName  New user name (nickname) to create
     * @param host  Client hostname or IP requesting new account
     * @param password  New user's initial password. Can't be null or "", and must pass
     *     {@link #isPasswordLengthOK(String)} which depends on {@link #getSchemaVersion()}.
     * @param email  Optional email address to contact this user
     * @param time  Created-at time, same format as {@link System#currentTimeMillis()}
     *            and {@link java.sql.Date#Date(long)}
     *
     * @return true if the DB connection is open and the account was created,
     *     false if no database is currently connected
     *
     * @throws IllegalArgumentException  If password is null or too short or too long
     * @throws SQLException if any unexpected database problem occurs
     */
    public boolean createAccount
        (String userName, String host, String password, String email, long time)
        throws IllegalArgumentException, SQLException
    {
        // When the password encoding or max length changes in jsettlers-tables-tmpl.sql,
        // be sure to update this method and updateUserPassword.

        if (! isPasswordLengthOK(password))
            throw new IllegalArgumentException("password");

        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                createAccountCommand.setString(1, userName);
                createAccountCommand.setString(2, host);
                if (schemaVersion < SCHEMA_VERSION_1200)
                {
                    createAccountCommand.setString(3, password);
                    createAccountCommand.setString(4, email);
                    createAccountCommand.setDate(5, sqlDate, cal);
                } else {
                    // password field is unused, value hardcoded in query sql
                    createAccountCommand.setString(3, email);
                    createAccountCommand.setDate(4, sqlDate, cal);
                    createAccountCommand.setString(5, userName.toLowerCase(Locale.US));
                    createAccountCommand.setInt(6, PW_SCHEME_BCRYPT);
                    try
                    {
                        String pw_store = BCrypt.hashpw(password, BCrypt.gensalt(bcryptWorkFactor));
                            // hashpw may throw IllegalArgumentException
                        createAccountCommand.setString(7, pw_store);
                    } catch (RuntimeException e) {
                        SQLException sqlE = new SQLException("BCrypt exception");
                        sqlE.initCause(e);
                        throw sqlE;  // caught, printed, re-thrown below
                    }
                    // SCHEMA_VERSION_2000 adds fields, but its sql has same number of params (the new fields get 0)
                }

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
    public boolean recordLogin(String userName, String host, long time) throws SQLException
    {
        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                recordLoginCommand.setString(1, userName);
                recordLoginCommand.setString(2, host);
                recordLoginCommand.setDate(3, sqlDate, cal);

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
    public boolean updateLastlogin(String userName, long time) throws SQLException
    {
        if (checkConnection())
        {
            try
            {
                java.sql.Date sqlDate = new java.sql.Date(time);
                Calendar cal = Calendar.getInstance();

                lastloginUpdate.setDate(1, sqlDate, cal);
                lastloginUpdate.setString(2, userName);

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
     * If schema &gt;= {@link #SCHEMA_VERSION_1200}, the password will be encoded with {@link #PW_SCHEME_BCRYPT}.
     * @param userName  Username to update.  Does not validate this user exists: Call {@link #getUser(String)}
     *     first to do so.  If schema &gt;= {@link #SCHEMA_VERSION_1200}, {@code userName} is case-insensitive.
     * @param newPassword  New password. Can't be null or "", and must pass
     *     {@link #isPasswordLengthOK(String)} which depends on {@link #getSchemaVersion()}.
     * @return  True if the update command succeeded, false if can't connect to db.
     *     <BR><B>Note:</B> If there is no user with {@code userName}, will nonetheless return true.
     * @throws IllegalArgumentException  If user or password are null, or password is too short or too long
     * @throws SQLException if an error occurs
     * @see #authenticateUserPassword(String, String, AuthPasswordRunnable)
     * @since 1.1.20
     */
    public boolean updateUserPassword(String userName, final String newPassword)
        throws IllegalArgumentException, SQLException
    {
        if (userName == null)
            throw new IllegalArgumentException("userName");
        if (! isPasswordLengthOK(newPassword))
            throw new IllegalArgumentException("newPassword");

        // When the password encoding or max length changes in jsettlers-tables-tmpl.sql,
        // be sure to update this method and createAccount.

        if (! checkConnection())
            return false;

        if (schemaVersion >= SCHEMA_VERSION_1200)
            userName = userName.toLowerCase(Locale.US);
        try
        {
            if (schemaVersion < SCHEMA_VERSION_1200)
            {
                passwordUpdateCommand.setString(1, newPassword);
                passwordUpdateCommand.setString(2, userName);
            } else {
                passwordUpdateCommand.setInt(1, PW_SCHEME_BCRYPT);
                try
                {
                    String pw_store = BCrypt.hashpw(newPassword, BCrypt.gensalt(bcryptWorkFactor));
                        // hashpw may throw IllegalArgumentException
                    passwordUpdateCommand.setString(2, pw_store);
                } catch (RuntimeException e) {
                    SQLException sqlE = new SQLException("BCrypt exception");
                    sqlE.initCause(e);
                    throw sqlE;  // caught, printed, re-thrown below
                }
                passwordUpdateCommand.setString(3, userName);
            }
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
     * Get the maximum password length, given the current schema version's encoding scheme
     * ({@link #PW_SCHEME_BCRYPT} or {@link #PW_SCHEME_NONE}).
     * To check a specific password's length, call {@link #isPasswordLengthOK(String)} instead.
     * @return  Maximum allowed password length for current password scheme
     *     ({@link #PW_MAX_LEN_SCHEME_BCRYPT} or {@link #PW_MAX_LEN_SCHEME_NONE})
     * @since 1.2.00
     */
    public final int getMaxPasswordLength()
    {
        return (schemaVersion >= SCHEMA_VERSION_1200)
            ? PW_MAX_LEN_SCHEME_BCRYPT
            : PW_MAX_LEN_SCHEME_NONE;
    }

    /**
     * Is this password's nonzero length less than or equal to the current maximum, given the current schema version's
     * password encoding scheme ({@link #PW_SCHEME_BCRYPT} or {@link #PW_SCHEME_NONE})?
     * For {@link #PW_SCHEME_BCRYPT}, checks length of its {@code UTF-8} bytes.
     * @param password Password being tried
     * @return True if password isn't null or "", and isn't longer than the current maximum
     * @see #getMaxPasswordLength()
     * @since 1.2.00
     */
    public final boolean isPasswordLengthOK(final String password)
    {
        if (password == null)
            return false;

        int L = password.length();
        if (L == 0)
            return false;

        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            return (L <= PW_MAX_LEN_SCHEME_NONE);
        } else {
            try
            {
                // check L first to skip UTF-8 encoding if overly long;
                // utf-8 length won't be less than java string length
                if ((L <= PW_MAX_LEN_SCHEME_BCRYPT)
                    && (password.getBytes("utf-8").length <= PW_MAX_LEN_SCHEME_BCRYPT))
                    return true;
            }
            catch (UnsupportedEncodingException e) {}

            return false;
        }
    }

    /**
     * Record this completed game's time, players, scores, and game options in the database.
     * For players whose users exist in the database, update their win-loss counts.
     *<P>
     * User win-loss records require schema version &gt;= {@link SOCDBHelper#SCHEMA_VERSION_2000}.
     *
     * @param ga  Game that's just completed
     * @param gameLengthSeconds  Duration of game, from {@link SOCGame#getDurationSeconds()}
     * @param winLossOnly  If true don't store game details, only update users' win-loss counts.
     *     Caller should negate value of {@link #PROP_JSETTLERS_DB_SAVE_GAMES} to set this parameter.
     *
     * @return true if the save succeeded
     * @throws IllegalArgumentException if {@link SOCGame#getPlayerWithWin() ga.getPlayerWithWin()} is null
     * @throws SQLException if an error occurs
     */
    public boolean saveGameScores
        (final SOCGame ga, final int gameLengthSeconds, final boolean winLossOnly)
        throws IllegalArgumentException, SQLException
    {
        final SOCPlayer winner = ga.getPlayerWithWin();
        if (winner == null)
            throw new IllegalArgumentException("no winner");

        if ((winLossOnly && (userIncrWonCommand == null))
            || ! checkConnection())
        {
            return false;  // <--- Early return: nothing to save, or conn was never initialized ---
        }

        String[] names = new String[SOCGame.MAXPLAYERS];  // DB max 6; ga.maxPlayers max 4 or 6
        short[] scores = new short[SOCGame.MAXPLAYERS];
        for (int pn = 0; pn < ga.maxPlayers; ++pn)
        {
            SOCPlayer pl = ga.getPlayer(pn);
            names[pn] = pl.getName();
            scores[pn] = (short) pl.getTotalVP();
        }

        try
        {
            int newGameID = -1;  // PK from insertGames2Row, unless winLossOnly

            if (! winLossOnly)
            {

                final int db_max_players = (schemaVersion < SCHEMA_VERSION_1200) ? 4 : 6;
                if ((ga.maxPlayers > db_max_players)
                    && ! (ga.isSeatVacant(4) && ga.isSeatVacant(5)))
                {
                    // Need to try and fit player 5 and/or player 6
                    // into the 4 db slots (backwards-compatibility)
                    saveGameScores_fit6pInto4(ga, names, scores);
                }

                final String gaName = ga.getName();
                final long startTimeMillis = ga.getStartTime().getTime();
                final SOCGameOptionSet opts = ga.getGameOptions();
                final String optsStr = (opts == null)
                    ? null
                    : SOCGameOption.packOptionsToString(opts.getAll(), false, true);

                if (schemaVersion >= SCHEMA_VERSION_2000)
                {
                    SOCGameOption scOpt = opts.get("SC");
                    final String scen = (scOpt != null) ? scOpt.getStringValue() : null;

                    newGameID = insertGames2Row
                        (gaName, winner.getName(), startTimeMillis, gameLengthSeconds, optsStr, scen);

                    // Per-player scores will be stored below, in a transaction

                } else {
                    // schemaVersion < SCHEMA_VERSION_2000: no games2 table
                    saveGameCommand.setString(1, gaName);
                    int i = 2;

                    for (int pn = 0; pn < db_max_players; ++i, ++pn)
                        saveGameCommand.setString(i, names[pn]);
                    for (int pn = 0; pn < db_max_players; ++i, ++pn)
                        if ((scores[pn] != 0) || (names[pn] != null))
                            saveGameCommand.setShort(i, scores[pn]);
                        else
                            saveGameCommand.setNull(i, Types.SMALLINT);

                    saveGameCommand.setTimestamp(i, new Timestamp(startTimeMillis));  ++i;

                    if (schemaVersion >= SCHEMA_VERSION_1200)
                    {
                        saveGameCommand.setInt(i, gameLengthSeconds);  ++i;
                        saveGameCommand.setString(i, winner.getName());  ++i;
                        saveGameCommand.setString(i, optsStr);  ++i;
                    }

                    newGameID = 0;

                    saveGameCommand.executeUpdate();
                }
            }

            if (userIncrWonCommand != null)
            {
                // In a transaction:
                // - Save per-player scores.
                // - Update per-user win/loss records for any players who exist in DB
                // Applies to schemaVersion >= SCHEMA_VERSION_2000

                String winnerName = winner.getName();
                if ((winnerName == null) || winnerName.isEmpty())
                    winnerName = "?";  // could happen if disconnected before save

                // begin transaction
                final boolean wasConnAutocommit = enterTransactionMode();

                try
                {
                    if (! winLossOnly)
                    {
                        // Per-player scores:

                        boolean hadAnyPlayers = false;
                        saveGamePlayerCommand.clearBatch();
                        for (int pn = 0; pn < ga.maxPlayers; ++pn)
                        {
                            if (ga.isSeatVacant(pn))
                                continue;
                            final String plName = names[pn];
                            final int plScore = scores[pn];
                            if ((plScore == 0) || (plName == null) || plName.isEmpty())
                                continue;  // initial settlements give starting score of 2: no one would have 0 at game end

                            hadAnyPlayers = true;
                            saveGamePlayerCommand.setInt(1, newGameID);
                            saveGamePlayerCommand.setString(2, plName);
                            saveGamePlayerCommand.setInt(3, plScore);
                            saveGamePlayerCommand.addBatch();
                        }
                        if (hadAnyPlayers)
                            saveGamePlayerCommand.executeBatch();
                    }

                    // Per-user win/loss records:

                    if ((winnerName != null) && (winnerName.length() > 0))
                    {
                        userIncrWonCommand.setString(1, winnerName);
                        userIncrWonCommand.executeUpdate();
                    }

                    int nLost = 0;
                    final int winnerPN = winner.getPlayerNumber();
                    for (int pn = 0; pn < ga.maxPlayers; ++pn)
                    {
                        if ((pn == winnerPN) || ga.isSeatVacant(pn))
                            continue;
                        String pname = names[pn];
                        if ((pname == null) || pname.isEmpty())
                            continue;

                        userIncrLostCommand.setString(1, pname);
                        userIncrLostCommand.addBatch();
                        ++nLost;
                    }
                    if (nLost > 0)
                        userIncrLostCommand.executeBatch();

                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    exitTransactionMode(wasConnAutocommit);
                }
            }

        }
        catch (SQLException sqlE)
        {
            errorCondition = true;
            sqlE.printStackTrace();
            throw sqlE;
        }

        return true;
    }

    /**
     * Try and fit names and scores of player 5 and/or player 6
     * into the 4 db slots, for backwards-compatibility with
     * {@link #SCHEMA_VERSION_ORIGINAL}.
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
     * If you need default params if bot not found or no database,
     * call {@link SOCServer#getRobotParameters(String)} instead.
     *
     * @param robotName Name of robot for db lookup
     * @return null if robotName not in database, or if db is empty and robotparams table doesn't exist
     * @throws SQLException if unexpected problem retrieving the params
     */
    public final SOCRobotParameters retrieveRobotParams(final String robotName)
        throws SQLException
    {
        SOCRobotParameters robotParams = null;

        if (checkConnection())
        {
            if (robotParamsQuery == null)
                return null;  // <--- Early return: Table not found in db, is probably empty ---

            try
            {
                robotParamsQuery.setString(1, robotName);

                ResultSet resultSet = robotParamsQuery.executeQuery();

                if (resultSet.next())
                {
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
    public int countUsers()
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
     * Build a list of DB settings and related info like {@link #getSchemaVersion()} and the BCrypt work factor,
     * formatted for printing for an admin user: friendly names and values, not technical name keys.
     * Includes all known settings, such as {@link #SETTING_BCRYPT_WORK__FACTOR}.
     * Also includes JDBC version, {@link DatabaseMetaData#supportsGetGeneratedKeys()}, etc.
     *
     * @param srv  Server, solely to call {@link SOCServer#getConfigBoolProperty(String, boolean)}
     * @return Formatted list of DB settings. Always an even number of items, a name and then a value
     *     for each setting. Some values might be {@code null}.
     * @throws IllegalStateException  if not connected to DB (! {@link #isInitialized()})
     * @see #checkSettings(boolean, boolean)
     * @since 1.2.00
     */
    public List<String> getSettingsFormatted(final SOCServer srv)
    {
        if (! isInitialized())
            throw new IllegalStateException();

        List<String> li = new ArrayList<String>();

        li.add("Schema version");
        li.add
            (Integer.toString(schemaVersion)
             + ((schemaVersion == SCHEMA_VERSION_LATEST) ? " (is latest version)" : " (upgrade recommended)"));
        li.add("Password encoding scheme");
        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            li.add("None (plain text)");
        } else {
            li.add("BCrypt");

            String dbStat = "";
            try
            {
                int dbWF = getIntSetting(SETTING_BCRYPT_WORK__FACTOR, 0);
                if (dbWF == 0)
                    dbStat = " (Missing from DB settings table)";
                else if (dbWF != bcryptWorkFactor)
                    dbStat = " (Mismatch: DB settings table has " + dbWF + ")";
            } catch (SQLException e) {
                dbStat = " (Error retrieving from DB: " + e.getMessage() + ")";
            }
            li.add("BCrypt work factor");
            li.add(bcryptWorkFactor + dbStat);
        }

        try
        {
            final DatabaseMetaData meta = connection.getMetaData();
            li.add("DB server version");
            li.add(meta.getDatabaseProductVersion());
            li.add("JDBC driver");
            li.add(driverclass
                + " v" + driverinstance.getMajorVersion() + '.' + driverinstance.getMinorVersion()
                + " (jdbc v" + meta.getJDBCMajorVersion() + '.' + meta.getJDBCMinorVersion()
                + ")");
            li.add("Driver supports insert getGeneratedKeys?");
            li.add(Boolean.toString(meta.supportsGetGeneratedKeys()));
        } catch (SQLException e) {
            li.add("Error retrieving DB version info");
            li.add(e.getMessage());  // might be null
        }

        li.add("Game results saved in DB?");
        li.add(Boolean.toString
            (srv.getConfigBoolProperty(SOCDBHelper.PROP_JSETTLERS_DB_SAVE_GAMES, false)));

        return li;
    }

    /****************************************
     * Public utility methods
     ****************************************/

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
    public Map<String,List<String>> queryUsersDuplicateLCase(final Set<String> out_allNames)
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
     * Run timing tests for {@link BCrypt} at various work factors, and print results to {@link System#err}.
     * Look for an acceptable speed of about 270-620 milliseconds per BCrypt.
     * Starting range is {@link #BCRYPT_DEFAULT_WORK_FACTOR} +/- 3. If all those work factors are
     * too fast, the range is gradually increased up to {@link BCrypt#GENSALT_MAX_LOG2_ROUNDS}
     * until an acceptable WF is found.
     *<P>
     * Called from {@code SOCServer} startup (Utility Mode) when
     * {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR} is {@code "test"}.
     * @return  The fastest acceptable work factor (270-620 milliseconds per BCrypt),
     *     or -1 if all tested WFs were too slow, or -2 if all WFs were too fast
     * @since 1.2.00
     */
    public int testBCryptSpeed()
    {
        System.err.println
            ((((props != null) && props.containsKey(PROP_JSETTLERS_DB_UPGRADE__SCHEMA)) ? "" : "* Utility Mode: ")
             + "Testing BCrypt speeds for work factors:");

        int max = BCRYPT_DEFAULT_WORK_FACTOR + 3;
        float[] wfSpeedMSec = new float[max + 1];
        float[][] wfSpeedsRef = { wfSpeedMSec };

        // test from high to low WF, so progress gets faster not slower:
        int recc_wf = testBCryptSpeed_range
            (wfSpeedsRef, max, BCRYPT_DEFAULT_WORK_FACTOR - 3);

        while (recc_wf == -2)
        {
            // None are slow enough: call testBCryptSpeed_range with larger WFs

            if (max >= BCrypt.GENSALT_MAX_LOG2_ROUNDS)
            {
                System.err.println("\n\n*** Maximum BCrypt work factor is still too fast");
                break;
            }
            int mNew = max + 3;
            if (mNew > BCrypt.GENSALT_MAX_LOG2_ROUNDS)
                mNew = BCrypt.GENSALT_MAX_LOG2_ROUNDS;

            recc_wf = testBCryptSpeed_range(wfSpeedsRef, max + 1, mNew);
            wfSpeedMSec = wfSpeedsRef[0];
            max = wfSpeedMSec.length - 1;
        }

        System.err.println();
        System.err.println("WF:  BCrypt time (ms) per password:");
        for (int wf = BCRYPT_DEFAULT_WORK_FACTOR - 3; wf <= max; ++wf)
        {
            if (wf < 10)
                System.err.print(' ');
            System.err.print(wf);
            System.err.print("   ");
            if (wf == recc_wf)
                System.err.println(wfSpeedMSec[wf] + "  <--- Recommended Work Factor ---");
            else if (wfSpeedMSec[wf] > 0)
                System.err.println(wfSpeedMSec[wf]);
            else
                System.err.println("> 1200.0");
        }
        System.err.println();

        return recc_wf;
    }

    /**
     * Test speed of a range of Work Factors for {@link #testBCryptSpeed()}.
     * Look for an acceptable speed of about 270-620 milliseconds per BCrypt.
     *<P>
     * {@code wfFrom} can be either higher or lower than {@code wfTo}.
     * If a work factor's BCrypts take longer than 1200 millisec each,
     * its loop will be stopped early and -1f will be stored instead of its speed.
     * @param wfSpeedsRef  Reference to array of work factor speeds (millisec).
     *     Work factor {@code wf}'s speed will be stored in {@code wfSpeedsRef[0][wf]}.
     *     Passed by reference so the array can be grown if needed.
     * @param wfFrom  Work Factor to start at; max is {@link BCrypt#GENSALT_MAX_LOG2_ROUNDS}
     * @param wfTo    Work Factor to finish at; max is {@link BCrypt#GENSALT_MAX_LOG2_ROUNDS}
     * @return  The fastest acceptable work factor (270-620 milliseconds per BCrypt),
     *     or -1 if all WFs were too slow, or -2 if all WFs were too fast
     * @since 1.2.00
     */
    private int testBCryptSpeed_range(float[][] wfSpeedsRef, int wfFrom, int wfTo)
    {
        if (wfFrom > BCrypt.GENSALT_MAX_LOG2_ROUNDS)
            wfFrom = BCrypt.GENSALT_MAX_LOG2_ROUNDS;
        if (wfTo > BCrypt.GENSALT_MAX_LOG2_ROUNDS)
            wfTo = BCrypt.GENSALT_MAX_LOG2_ROUNDS;

        float[] wfSpeedMSec = wfSpeedsRef[0];
        final int inc, max;
        if (wfFrom < wfTo)
        {
            inc = 1;   max = wfTo;
        } else {
            inc = -1;  max = wfFrom;
        }
        // grow if needed
        if (wfSpeedMSec.length <= max)
        {
            float[] wfs = new float[max + 1];
            System.arraycopy(wfSpeedMSec, 0, wfs, 0, wfSpeedMSec.length);
            wfSpeedMSec = wfs;
            wfSpeedsRef[0] = wfs;
        }

        final int TOO_SLOW_MSEC = 1200;
        final SecureRandom sr = new SecureRandom();
        boolean all_too_fast = true;
        int fastest_wf = -1;
        float fastest_msec = 9999;

        for (int wf = wfFrom; ; wf += inc)
        {
            System.err.print(wf);
            System.err.print(' ');
            System.err.flush();

            // We're testing the time to hash or check passwords, not time to generate salt,
            // so don't include that as part of our timing measurement
            final String salt = BCrypt.gensalt(wf, sr);

            final long start_ms = System.currentTimeMillis();
            boolean tooSlow = false;
            for (int i = 0; i < 7; ++i)
            {
                BCrypt.hashpw("testDBHelper", salt);
                if ((i == 1) && (((System.currentTimeMillis() - start_ms) / 2) > TOO_SLOW_MSEC))
                {
                    tooSlow = true;
                    break;
                }
            }
            final long end_ms = System.currentTimeMillis();

            if (tooSlow)
            {
                wfSpeedMSec[wf] = -1f;
                all_too_fast = false;
            } else {
                float speed = (end_ms - start_ms) / 7.0f;
                wfSpeedMSec[wf] = speed;
                if (speed >= 270)
                {
                    all_too_fast = false;
                    if ((speed <= 620)
                        && ((fastest_wf == -1) || (speed < fastest_msec)))
                    {
                        fastest_wf = wf;
                        fastest_msec = speed;
                    }
                }
            }

            if (wf == wfTo)
                break;
        }

        return (all_too_fast) ? -2 : fastest_wf;
    }

    /**
     * Build and run a SELECT query, with a LIMIT clause appropriate to the DB type if possible.
     * Currently possible for MariaDB, MySQL, Postgres, SQLite, and semi-supported ORA.
     * Otherwise this will be a standard SELECT without any LIMIT clause.
     * @param selectStmt  SQL statement, beginning with SELECT, omitting trailing {@code ';'}.
     *     <BR>
     *     Example: {@code "SELECT * FROM games WHERE duration_sec >= 3600"}
     * @param limit  Number of rows for LIMIT clause
     * @return  This limited SELECT statement's ResultSet, from {@link Statement#executeQuery(String)}
     * @throws SQLException if any unexpected database problem
     * @since 1.2.00
     */
    public ResultSet selectWithLimit(final String selectStmt, final int limit)
        throws SQLException
    {
        StringBuilder sql = new StringBuilder(selectStmt);
        int L = sql.length();
        if (sql.charAt(L - 1) == ';')
            sql.setLength(L - 1);

        switch (dbType)
        {
        case DBTYPE_MARIADB:
        case DBTYPE_MYSQL:
        case DBTYPE_POSTGRESQL:
        case DBTYPE_SQLITE:
            sql.append(" LIMIT ");
            sql.append(limit);
            break;

        case DBTYPE_ORA:
            sql.insert(0, "SELECT * FROM (");
            sql.append(") t WHERE ROWNUM <= ");
            sql.append(limit);
            break;

        default:
            // no limit clause
        }

        sql.append(';');

        return connection.createStatement().executeQuery(sql.toString());
    }

    /**
     * Insert a new game-info row into the {@code games2} table and return its generated ID.
     * Used by {@link #saveGameScores(SOCGame, int, boolean)}.
     *
     * @param startTimeMillis  Game start time, from {@link SOCGame#getStartTime()}{@link java.util.Date#getTime() .getTime()}
     * @param gameLengthSeconds  Game length, from {@link SOCGame#getDurationSeconds()}
     * @param optsStr  Null or game options, from {@link SOCGame#getGameOptions()}
     *     passed to {@link SOCGameOption#packOptionsToString(Map, boolean, boolean)}
     * @param scen  Scenario name key from game option {@code "SC"}, or {@code null} if none
     * @return  Newly inserted row's primary key ID
     * @throws IllegalStateException  If not connected and if {@link #checkConnection()} fails
     * @throws UnsupportedOperationException if schema @lt; {@link #SCHEMA_VERSION_2000}
     * @throws SQLException if any unexpected database problem
     * @since 2.0.00
     */
    private int insertGames2Row
        (final String gaName, final String winnerName, final long startTimeMillis, final int gameLengthSeconds,
         final String optsStr, final String scen)
        throws IllegalStateException, UnsupportedOperationException, SQLException
    {
        try
        {
            if (! checkConnection())
                throw new IllegalStateException();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        if (schemaVersion < SCHEMA_VERSION_2000)
            throw new UnsupportedOperationException();

        // No try-catch here for SQLException: handled within caller saveGameScores

        int i = 1;
        saveGameCommand.setString(1, gaName);  ++i;
        saveGameCommand.setTimestamp(i, new Timestamp(startTimeMillis));  ++i;
        saveGameCommand.setInt(i, gameLengthSeconds);  ++i;
        saveGameCommand.setString(i, winnerName);  ++i;
        saveGameCommand.setString(i, optsStr);  ++i;  // null is OK
        saveGameCommand.setString(i, scen);  ++i;  // null is OK

        saveGameCommand.executeUpdate();

        int id = 0;
        ResultSet rs = null;
        try
        {
            rs = saveGameCommand.getGeneratedKeys();
            if (rs.next()) {
                id = rs.getInt(1);
            }
        } finally {
            if (rs != null)
                try { rs.close(); }
                catch (SQLException ignore) {}
        }

        return id;
    }

    /**
     * Query to see if a table exists in the database.
     * Any exception is caught here and returns false.
     * @param tabname  Table name to check for; case-sensitive in some db types.
     *    The jsettlers standard is to always use lowercase names when creating tables and columns.
     * @return  true if table exists in the current connection's database
     * @throws IllegalStateException  If not connected and if {@link #checkConnection()} fails
     * @see #doesTableColumnExist(String, String)
     * @since 1.2.00
     */
    public boolean doesTableExist(final String tabname)
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
                try
                {
                    rs.close();
                }
                catch (SQLException se) {}
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
    public boolean doesTableColumnExist
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
                try
                {
                    rs.close();
                }
                catch (SQLException e) {}

            return false;
        }

        return true;
    }

    /****************************************
     * Other utility methods
     ****************************************/

    /**
     * Puts {@link #connection} into a mode where it always expects explicit begin transaction
     * and commit/rollback commands, and calls {@code BEGIN TRANSACTION} now.
     *<UL>
     * <LI> The call to this function should be in its own {@code try/catch}({@link SQLException}) block,
     *      not the same one as your transaction's SQL statements/commands
     * <LI> After calling this function, you should run SQL statements/commands as usual,
     *      within a {@code try/catch}({@link SQLException}) block
     * <LI> Then, call {@link {@link Connection#commit()} or {@link Connection#rollback()} as needed
     *      within the same {@code try} block. (Maybe call {@link Connection#rollback()} in the
     *      {@code catch}({@link SQLException}) block of that {@code try/catch}.)
     * <LI> Once done with transaction(s), call {@link #exitTransactionMode(boolean)}
     *      in the {@code finally} block of that {@code try/catch}
     *</UL>
     *
     * @return  Status value from {@link Connection#getAutoCommit()} about
     *     {@link #connection}'s behavior before entering transaction mode.
     *     This value must be saved (use a {@code final} local) and, after your
     *     transactions, passed to {@link #exitTransactionMode(boolean)}.
     * @throws SQLException  if an unexpected DB error occurs or the connection is closed
     * @since 2.0.00
     */
    private boolean enterTransactionMode()
        throws SQLException
    {
        // Note: if this method's code changes, also update testDBHelper() to match.

        final boolean wasConnAutocommit = connection.getAutoCommit();

        // begin transaction
        if (wasConnAutocommit)
            connection.setAutoCommit(false);
        else
            try {
                connection.commit();  // end previous transaction, if any
            } catch (SQLException e) {}

        return wasConnAutocommit;
    }

    /**
     * After you've completed your transaction(s) and called {@link Connection#commit()}
     * or {@link Connection#rollback()} for the last time, call this method to take
     * SOCDBHelper out of "transaction mode" and return it to single-statement mode.
     *<P>
     * The call to this method is probably best placed in the {@code finally} block of the
     * {@code try/catch} that contains the transaction's SQL commands and commit(s).
     *
     * @param wasConnAutocommit  The value returned from {@link #enterTransactionMode()},
     *     to restore {@link #connection}'s behavior to how it was before transaction mode.
     * @throws SQLException  if an unexpected DB error occurs or the connection is closed,
     *     or (unlikely) a distributed transaction was in progress when
     *     {@link Connection#setAutoCommit(boolean)} was called
     * @since 2.0.00
     */
    private void exitTransactionMode(final boolean wasConnAutocommit)
        throws SQLException
    {
        // Note: if this method's code changes, also update testDBHelper() to match.

        if (wasConnAutocommit)
            connection.setAutoCommit(true);
        // else,
        //   nothing to do since caller has called commit() or rollback()
    }

    /****************************************
     * DB install, schema upgrade
     ****************************************/

    /**
     * Load and run a SQL script.
     * Typically DDL commands to create or alter tables, indexes, etc.
     * @param setupScriptPath  Full path or relative path to the SQL script filename
     * @throws FileNotFoundException  if file not found
     * @throws IOException  if any other IO error occurs
     * @throws SQLException if any unexpected database problem
     * @since 1.1.15
     */
    private void runSetupScript(final String setupScriptPath)
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
     * After this method returns, call {@link #doesSchemaUpgradeNeedBGTasks()} to determine if the upgrade needs
     * further tasks or conversions in a background thread. See that method's javadoc for how to start the thread.
     *<P>
     * Pre-checks include {@link #queryUsersDuplicateLCase(Set)}.
     * If upgrading from {@link #SCHEMA_VERSION_ORIGINAL} and {@link #PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR}
     * is not set, pre-check calls {@link #testBCryptSpeed()} to set the Work Factor.
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
     * @param userAdmins List of users who are user admins, for special treatment during upgrade if needed,
     *     or {@code null}. These user nicknames do not need to actually exist in the database.
     * @throws IllegalStateException  if already latest version ({@link #isSchemaLatestVersion()}),
     *     or if not connected to DB (! {@link #isInitialized()})
     * @throws MissingResourceException  if pre-checks indicate a problem in the data (such as wrong current DB user,
     *     or nicknames which collide with each other when lowercase) which must be manually resolved by this
     *     server's administrator before upgrade: {@link Throwable#getMessage()} will be a multi-line string with
     *     problem details to show to the server admin.
     *    <P>
     *     Upgrade to schema 2.0.00 for {@code DBTYPE_ORA} isn't yet implemented: Upgrade attempts
     *     on that DB type will throw {@code MissingResourceException}.
     * @throws SQLException  if any unexpected database problem during the upgrade
     * @see {@link #isSchemaLatestVersion()}
     * @since 1.2.00
     */
    public void upgradeSchema(final Set<String> userAdmins)
        throws IllegalStateException, SQLException, MissingResourceException
    {
        if (isSchemaLatestVersion())  // throws IllegalStateException if ! isInitialized()
            throw new IllegalStateException("already at latest schema");

        /* final pre-checks */

        if (dbType == DBTYPE_POSTGRESQL)
        {
            // Check table ownership since table create scripts may have ran as postgres user, not socuser
            String otherOwner = upg_postgres_checkIsTableOwner();
            if (otherOwner != null)
                throw new MissingResourceException
                    ("Must change table owner to " + dbcUserName + " from " + otherOwner, "unused", "unused");
        }
        else if (dbType == DBTYPE_ORA)
        {
            throw new MissingResourceException
                ("Upgrade on oracle to schema 2.0.00 not yet implemented", "unused", "unused");
        }

        final Set<String> upg_1200_allUsers = new HashSet<String>();  // built during pre-check, used during upgrade
        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            /* pre-checks */

            final Map<String, List<String>> dupes = queryUsersDuplicateLCase(upg_1200_allUsers);
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
                     + "  BEGIN;\n"
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

            if ((props != null) && ! props.containsKey(PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR))
            {
                int wf = testBCryptSpeed();
                if (wf < BCRYPT_MIN_WORK_FACTOR)
                    throw new MissingResourceException
                        ("Must re-run with " + PROP_JSETTLERS_DB_BCRYPT_WORK__FACTOR + " property", "unused", "unused");

                bcryptWorkFactor = wf;
            }
        }

        final int from_vers = schemaVersion;

        /* 1.2.00: First, create db_version table */
        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            // no rollback needed if fails, so don't try/catch here

            final String sql = "CREATE TABLE db_version ("
                + "from_vers INT not null, to_vers INT not null, ddl_done "
                + TIMESTAMP_NULL +", bg_tasks_done " + TIMESTAMP_NULL
                + ", PRIMARY KEY (to_vers) );";
            runDDL(sql);
        }

        /* add upgrade in progress to db_version history table */
        try
        {
            PreparedStatement ps = connection.prepareStatement
                ("INSERT into db_version(from_vers, to_vers, ddl_done, bg_tasks_done) VALUES(?,?,null,null);");
            ps.setInt(1, from_vers);
            ps.setInt(2, SCHEMA_VERSION_LATEST);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            // no rollback needed if fails, unless schemaVersion < SCHEMA_VERSION_1200

            if (schemaVersion < SCHEMA_VERSION_1200)
            {
                try {
                    runDDL("DROP TABLE db_version;");
                }
                catch (SQLException se) {
                    if (se.getCause() == null)
                        se.initCause(e);
                    throw se;
                }
            }
            throw e;
        }

        // NOTES for future schema changes:
        // - Keep your DDL SQL syntax consistent with the commands tested in testDBHelper().
        // - Be prepared to rollback to a known-good state if a problem occurs.
        //   Each unrelated part of an upgrade must completely succeed or fail.
        //   That requirement is for postgresql and mysql/mariadb: sqlite can't drop any added columns;
        //   the server's admin must back up their sqlite db before running the upgrade.

        /**
         * 1.2.00: settings table;
         *     games + player5, player6, score5, score6, duration_sec, winner, gameopts;
         *     users + nickname_lc, pw_scheme, pw_store, pw_change, index users__l
         */
        if (schemaVersion < SCHEMA_VERSION_1200)
        {
            /* add games fields; add users field, fill it, add unique index */
            boolean added_tab_settings = false, added_game_fields = false, added_user_fields = false;
            try
            {
                runDDL
                    ("CREATE TABLE settings ( s_name varchar(32) not null, s_value varchar(500), i_value int, "
                     + "s_changed " + TIMESTAMP + " not null, PRIMARY KEY (s_name) );");
                added_tab_settings = true;

                // sqlite can't add multiple fields at once
                runDDL("ALTER TABLE games ADD COLUMN player5 VARCHAR(20);");
                added_game_fields = true;
                runDDL("ALTER TABLE games ADD COLUMN player6 VARCHAR(20);");
                runDDL("ALTER TABLE games ADD COLUMN score5 SMALLINT;");
                runDDL("ALTER TABLE games ADD COLUMN score6 SMALLINT;");
                runDDL("ALTER TABLE games ADD COLUMN duration_sec INT;");
                runDDL("ALTER TABLE games ADD COLUMN winner VARCHAR(20);");
                runDDL("ALTER TABLE games ADD COLUMN gameopts VARCHAR(500);");

                runDDL("ALTER TABLE users ADD COLUMN nickname_lc VARCHAR(20);");
                added_user_fields = true;
                runDDL("ALTER TABLE users ADD COLUMN pw_scheme INT;");
                runDDL("ALTER TABLE users ADD COLUMN pw_store VARCHAR(255);");
                runDDL("ALTER TABLE users ADD COLUMN pw_change " + TIMESTAMP_NULL + ";");

                // fill nickname_lc field; use String.toLowerCase(..), not SQL lower(..) which is ascii-only on sqlite.
                // This is much quicker to calculate and update than pw_store, so we won't do that field yet.
                if (! upg_1200_allUsers.isEmpty())
                {
                    PreparedStatement ps = connection.prepareStatement
                        ("UPDATE users SET nickname_lc=? WHERE nickname=?");

                    // begin transaction
                    final boolean wasConnAutocommit = enterTransactionMode();

                    try
                    {
                        int n = 0;
                        for (final String nm : upg_1200_allUsers)
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
                        exitTransactionMode(wasConnAutocommit);
                    }

                }

                // create unique index
                runDDL("CREATE UNIQUE INDEX users__l ON users(nickname_lc);");

                // save bcryptWorkFactor to settings
                try
                {
                    updateSetting(SETTING_BCRYPT_WORK__FACTOR, bcryptWorkFactor, true);
                } catch (SQLException e) {
                    // shouldn't happen without other earlier or later problems
                    System.err.println
                        ("* Could not set " + SETTING_BCRYPT_WORK__FACTOR + " in settings table: " + e);
                }

                if (userAdmins != null)
                    upgradeSchema_1200_encodeUserPasswords
                        (userAdmins, null,
                         "Encoding passwords for user account admins...",
                         "* Warning: No user account admins found to encode",
                         "User admin password encoding completed");

            } catch (SQLException e) {
                System.err.println
                    ("*** Problem occurred during schema upgrade to v1200:\n"
                     + e + "\n\n* Will attempt to roll back to schema v1000.");

                boolean couldRollback = true;

                if (added_tab_settings && ! runDDL_rollback("DROP TABLE settings;"))
                    couldRollback = false;

                if (couldRollback && added_user_fields)
                {
                    final String[] cols = {"pw_scheme", "pw_store", "pw_change"};
                    if ((dbType == DBTYPE_SQLITE)
                              // roll back first field added, if exception was thrown for that
                        || ! (runDDL_rollback("ALTER TABLE users DROP nickname_lc;")
                              && runDDL_dropCols("users", cols)))
                        couldRollback = false;
                }

                if (couldRollback && added_game_fields)
                {
                    final String[] cols = {"player6", "score5", "score6", "duration_sec", "winner", "gameopts"};
                    if ((dbType == DBTYPE_SQLITE)
                        || ! (runDDL_rollback("ALTER TABLE games DROP player5;")
                              && runDDL_dropCols("games", cols)))
                        couldRollback = false;
                }

                // nothing successfully upgraded, so remove in-progress db_version table entry
                upgradeSchema_setDBVersionTable(false, from_vers, 0, false);

                if (! couldRollback)
                    System.err.println
                        ("*** Could not completely roll back failed upgrade: Must restore DB from backup!");
                else
                    System.err.println("\n* All rollbacks were successful.\n");

                throw e;
            }
        }

        /**
         * 2.0.00:
         * - add new tables games2, games2players, upg_tmp_games
         * - add users fields
         * - copy data from games into upg_tmp_games & games2
         */
        if (schemaVersion < SCHEMA_VERSION_2000)
        {
            boolean added_tab_games2 = false, added_tab_games2_pl = false,
                added_tab_upg_tmp = false, added_user_fields = false;

            try
            {
                // games2
                String sql= "CREATE TABLE games2 ("
                    + "gameid " + INT_AUTO_PK + ", gamename VARCHAR(20) not null,"
                    + "starttime " + TIMESTAMP + " not null,"
                    + "duration_sec INT,"  // allow null, unlike new-install sql
                    + "winner VARCHAR(20) not null,"
                    + "gameopts VARCHAR(500), scenario VARCHAR(16) ); ";
                runDDL(sql);
                added_tab_games2 = true;

                runDDL("CREATE INDEX games2__s ON games2(starttime);");

                // games2_players
                sql = "CREATE TABLE games2_players ("
                    + "gameid INT not null, player VARCHAR(20) not null, score SMALLINT not null,"
                    + "PRIMARY KEY(gameid, player) ); ";
                runDDL(sql);
                added_tab_games2_pl = true;

                // upg_tmp_games: temporary for upgrade, until BG tasks done
                sql = "CREATE TABLE upg_tmp_games ("
                    + "gameid " + INT_AUTO_PK + ", gamename VARCHAR(20) not null,"
                    + "player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20), player5 VARCHAR(20), player6 VARCHAR(20),"
                    + "score1 SMALLINT, score2 SMALLINT, score3 SMALLINT, score4 SMALLINT, score5 SMALLINT, score6 SMALLINT,"
                    + "starttime " + TIMESTAMP + " not null, duration_sec INT, winner VARCHAR(20) not null, gameopts VARCHAR(500), mig_done SMALLINT );";
                runDDL(sql);
                added_tab_upg_tmp = true;

                runDDL("CREATE INDEX upg_tmp_games__m ON upg_tmp_games(mig_done);");

                // Copy data from games into upg_tmp_games & games2:
                // has no "added" var; during rollback these tables will be deleted

                // begin transaction:
                final boolean wasConnAutocommit = enterTransactionMode();

                Statement st = null;
                try
                {
                    st = connection.createStatement();
                    st.executeUpdate
                        ("INSERT INTO upg_tmp_games(gamename,player1,player2,player3,player4,player5,player6,"
                         + "score1,score2,score3,score4,score5,score6,starttime,duration_sec,winner,gameopts)"
                         + " SELECT gamename,player1,player2,player3,player4,player5,player6,score1,score2,score3,score4,score5,score6,"
                         + "starttime,duration_sec,coalesce(winner,'?'),gameopts FROM games ORDER BY starttime;");
                    connection.commit();
                    st.close();
                    st = null;

                    // 2nd transaction
                    st = connection.createStatement();
                    st.executeUpdate
                        ("INSERT INTO games2(gameid,gamename,starttime,duration_sec,winner,gameopts)"
                         + " SELECT gameid,gamename,starttime,duration_sec,winner,gameopts FROM upg_tmp_games ORDER BY gameid;");
                    connection.commit();
                    st.close();
                    st = null;

                    // If postgres, must update games2's PK sequence after inserting rows which specify gameid.
                    // The sequence update doesn't require a commit, and can't be rolled back:
                    // https://www.postgresql.org/docs/11/functions-sequence.html
                    if (dbType == DBTYPE_POSTGRESQL)
                    {
                        String seqname = dbtypePostgresGetSerialSequence("games2", "gameid");  // 'public.games2_gameid_seq' (etc)
                        if (seqname != null)
                        {
                            PreparedStatement ps = connection.prepareStatement
                                ("SELECT setval(?, (SELECT coalesce(max(gameid),1) FROM games2), true);");
                                // uses 1 not 0 if table is empty, to avoid this error:
                                // ERROR:  setval: value 0 is out of bounds for sequence "games2_gameid_seq" (1..9223372036854775807)
                            ps.setString(1, seqname);
                            ps.executeQuery();  // setval returns a resultset we ignore,
                                // but executeUpdate would throw an exception because resultset is returned
                            ps.close();  // also closes the ignored resultset
                        } else {
                            // Null shouldn't be possible:
                            // INT_AUTO_PK DDL creates a sequence; sequence query method is tested in testDBHelper(..)
                            // Try to help anyway:
                            System.err.println
                                ("* DB upgrade warning: Can't find sequence for primary key field games2.gameid\n"
                                 + "  The upgrade will continue, but you can't save new games to the database until you correct the warning:\n"
                                 + "  - Connect to the DB with psql\n"
                                 + "  - Run the command \\ds and note the sequence name for games2\n"
                                 + "  - Run this command, replacing name_of_seq with the name from \\ds:\n"
                                 + "  - SELECT setval('name_of_seq', (SELECT coalesce(max(gameid),1) FROM games2), true);\n");
                        }
                    }

                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    try {
                        if (st != null)
                            st.close();
                    } catch (SQLException e) {}

                    exitTransactionMode(wasConnAutocommit);
                }

                // users
                // sqlite can't add multiple fields at once
                runDDL("ALTER TABLE users ADD COLUMN games_won INT;");
                added_user_fields = true;
                runDDL("ALTER TABLE users ADD COLUMN games_lost INT;");

            } catch (SQLException e) {
                System.err.println
                    ("*** Problem occurred during schema upgrade to v2000:\n"
                     + e + "\n\n* Will attempt to roll back to schema v1200.\n");

                boolean couldRollback = true;

                if (couldRollback && added_user_fields)
                {
                    final String[] cols = {"games_won", "games_lost"};
                    if ((dbType == DBTYPE_SQLITE)
                        || ! runDDL_dropCols("users", cols))
                        couldRollback = false;
                }

                if (couldRollback && added_tab_upg_tmp && ! runDDL_rollback("DROP TABLE upg_tmp_games;"))
                    couldRollback = false;

                if (couldRollback && added_tab_games2_pl && ! runDDL_rollback("DROP TABLE games2_players;"))
                    couldRollback = false;

                if (couldRollback && added_tab_games2 && ! runDDL_rollback("DROP TABLE games2;"))
                    couldRollback = false;

                if (! couldRollback)
                    System.err.println
                        ("*** Could not completely roll back failed upgrade: Must restore DB from backup!");
                else
                    System.err.println("\n* All rollbacks were successful.\n");

                // clean up in-progress db_version table entry
                if (from_vers < SCHEMA_VERSION_1200)
                    // if orig schemaVersion was v1000, update to 1200 not 2000
                    upgradeSchema_setDBVersionTable(false, from_vers, SCHEMA_VERSION_1200, true);
                else
                    // orig was 1200 -> nothing successfully done, so delete entry
                    upgradeSchema_setDBVersionTable(false, from_vers, 0, false);

                throw e;
            }
        }

        final boolean has_bg_tasks = (schemaVersion < SCHEMA_VERSION_2000);

        /* mark upgrade as completed in db_version table */
        try
        {
            upgradeSchema_setDBVersionTable(true, from_vers, SCHEMA_VERSION_LATEST, has_bg_tasks);
        } catch (SQLException e) {
            System.err.println
                ("* Upgrade was successful except for final db_version table update; please manually update db_version as described above.");
        }

        if (has_bg_tasks)
            schemaUpgBGTasks_fromVersion = schemaVersion;

        prepareStatements();

        /* upgrade is completed. */
        System.err.println("* DB schema upgrade completed.\n\n");
    }

    /**
     * Update the {@code db_version} table at the end of an upgrade (or rollback) based on the versions involved.
     * The table then accurately indicates whether the upgrade was complately successful; partially successful
     * up to a certain schema version but rolled back from latest version; or complately failed and rolled back.
     *<P>
     * This is a followup to the start of {@link #upgradeSchema(Set)}, which adds a row to {@code db_version}
     * with {@code from_vers=}<em>fromVers</em> before trying any DDL statements.
     *<P>
     * Called from {@link #upgradeSchema(Set)} after its DDL statements; not called from BG tasks thread.
     *
     * @param throwExcepIfError  If any problem comes up while updating the table, the Exception is always printed here
     *     along with the updated statement being attempted. If true, also throws it for caller to catch.
     * @param fromVers  Version being upgraded from; schema version of the database
     *     before {@link #upgradeSchema(Set)} was called
     * @param successfulToVers  Version up to which all upgrade changes were successful, or 0 if rollback was needed
     *     before any intermediate version was reached. If not 0, the {@code ddl_done} field
     *     will get the current timestamp. If 0, the {@code db_version} row for this upgrade with
     *     {@code from_vers=}<em>fromVers</em> is deleted.
     * @param hasBGTasks  If true, the upgrade DDL was successful but some background tasks remain;
     *     the {@code bg_tasks_done} field should remain {@code null}. If false, that field should get
     *     the current timestamp. Ignored if {@code successfulToVers} is 0.
     * @throws SQLException if an error occurred and {@code throwExcepIfError} is true
     * @since 2.0.00
     */
    private void upgradeSchema_setDBVersionTable
        (final boolean throwExcepIfError, final int fromVers, final int successfulToVers, final boolean hasBGTasks)
        throws SQLException
    {
        try
        {
            final PreparedStatement ps;
            if (successfulToVers == 0)
            {
                ps = connection.prepareStatement
                    ("DELETE FROM db_version WHERE from_vers=?;");
                ps.setInt(1, fromVers);
            } else {
                ps = connection.prepareStatement
                    ("UPDATE db_version SET to_vers=?, ddl_done=?, bg_tasks_done=? WHERE from_vers=?;");
                final Timestamp now = new Timestamp(System.currentTimeMillis());
                ps.setInt(1, successfulToVers);
                ps.setTimestamp(2, now);
                if (hasBGTasks)
                    ps.setNull(3, Types.TIMESTAMP);
                else
                    ps.setTimestamp(3, now);
                ps.setInt(4, fromVers);
            }

            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.err.println("*** SQLException while updating db_version table: " + e);
            if (successfulToVers == 0)
                System.err.println
                    ("    Cleanup needed: DELETE FROM db_version WHERE from_vers=" + fromVers + ';');
            else
                System.err.println
                    ("    Cleanup needed: Restore from backup, or UPDATE db_version SET to_vers=" + successfulToVers
                     + ", ddl_done=(timestamp), bg_tasks_done=" + ((hasBGTasks) ? "null": "(timestamp)")
                     + " WHERE from_vers=" + fromVers + ';');

            if (throwExcepIfError)
                throw e;
        }
    }

    /****************************************
     * Connection cleanup
     ****************************************/

    /**
     * Close out and shut down the database connection.
     * Any {@link SQLException}s while doing so are caught here.
     * @param isForShutdown  If true, set <tt>connection = null</tt>
     *          so we won't try to reconnect later.
     */
    public void cleanup(final boolean isForShutdown)
    {
        try
        {
            if (! checkConnection())
                return;
        }
        catch (SQLException e) {}

        try
        {
            createAccountCommand.close();
            userPasswordQuery.close();
            hostQuery.close();
            lastloginUpdate.close();
            saveGameCommand.close();
            if (saveGamePlayerCommand != null)
                saveGamePlayerCommand.close();
            robotParamsQuery.close();
            userCountQuery.close();
            userExistsQuery.close();
            if (userIncrWonCommand != null)
                userIncrWonCommand.close();
            if (userIncrLostCommand != null)
                userIncrLostCommand.close();
        }
        catch (Throwable thr)
        {
            ; /* ignore failures in query closes */
        }

        if (isForShutdown && (schemaUpgBGTasksThread != null) && schemaUpgBGTasksThread.isAlive())
            schemaUpgBGTasksThread.doShutdown = true;

        initialized = false;
        try
        {
            connection.close();
            if (isForShutdown)
                connection = null;
        }
        catch (SQLException sqlE)
        {
            errorCondition = true;
            if (isForShutdown)
                connection = null;

            sqlE.printStackTrace();
        }
    }

    /****************************************
     * Helpers for upgrade, etc
     ****************************************/

    /**
     * As part of schema upgrade to 1200, encode passwords for a set of users.
     * Assumes their {@code pw_store} column is currently {@code null}.
     * @param users  Usernames to encode passwords. These are used here with {@link #userPasswordQuery}, so
     *     if {@link #schemaVersion} &lt; {@link #SCHEMA_VERSION_1200} they must be case-sensitive for
     *     {@code users.nickname}, otherwise must be lowercase for {@code users.nickname_lc}.
     * @param sr  SecureRandom to use, or {@code null} for a new one
     * @param beginText  Null or text to print at start of conversion
     * @param warnEmptyText  Null or text to print if no matching users found in db to convert
     * @param doneText  Null or text to print at end of conversion
     * @return true if any of these {@code users} were found in the database and encoded, false if none found
     * @throws SQLException  if any unexpected database problem
     */
    private boolean upgradeSchema_1200_encodeUserPasswords
        (final Set<String> users, SecureRandom sr,
         final String beginText, final String warnEmptyText, final String doneText)
        throws SQLException
    {
        if (sr == null)
            sr = new SecureRandom();

        if (beginText != null)
            System.err.println(beginText);

        Map<String, String> userConvPW = new HashMap<String, String>();
        for (String uname : users)
        {
            userPasswordQuery.setString(1, uname);

            String dbUserName = null, dbPassword = null;
            ResultSet resultSet = userPasswordQuery.executeQuery();
            if (resultSet.next())
            {
                dbUserName = resultSet.getString(1);
                dbPassword = resultSet.getString(2);
            }
            resultSet.close();

            if (dbPassword != null)
                try
                {
                    String pwStore = BCrypt.hashpw(dbPassword, BCrypt.gensalt(bcryptWorkFactor));
                        // hashpw may throw IllegalArgumentException
                    userConvPW.put(dbUserName, pwStore);
                } catch (RuntimeException e) {
                    SQLException sqlE = new SQLException("BCrypt exception");
                    sqlE.initCause(e);
                    throw sqlE;
                }
        }

        if (userConvPW.isEmpty())
        {
            if (warnEmptyText != null)
                System.err.println(warnEmptyText);

            return false;  // <--- Early return: Nothing to do ---
        }

        PreparedStatement ps = connection.prepareStatement
            ("UPDATE users SET password='!', pw_scheme=" + PW_SCHEME_BCRYPT + ", pw_store=? WHERE nickname=?");

        // begin transaction
        final boolean wasConnAutocommit = enterTransactionMode();

        try
        {
            int n = 0;
            for (Map.Entry<String, String> e : userConvPW.entrySet())
            {
                ps.setString(1, e.getValue());
                ps.setString(2, e.getKey());
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
            exitTransactionMode(wasConnAutocommit);
        }

        if (doneText != null)
            System.err.println(doneText);

        return true;
    }

    /**
     * For {@link #upgradeSchema()} with {@link #DBTYPE_POSTGRESQL}, check that we're
     * currently connected as the owner of jsettlers tables such as {@code 'users'}.
     * If not, DDL will probably fail.
     * @return {@code null} if OK, or table owner name if <B>not</B> currently connected as table owner.
     * @throws SQLException  if any unexpected database problem querying current user or table owner
     * @since 1.2.00
     */
    private String upg_postgres_checkIsTableOwner()
        throws SQLException
    {
        String curr = null, owner = null, error = null;

        String sql = "select current_user;";
        ResultSet rs = connection.createStatement().executeQuery(sql);
        if (rs.next())
            curr = rs.getString(1);
        else
            error = "Empty result: " + sql;
        rs.close();

        if (error == null)
        {
            sql = "select tableowner from pg_tables where tablename='users';";
            rs = connection.createStatement().executeQuery(sql);
            if (rs.next())
            {
                owner = rs.getString(1);
                if (owner == null)
                    error = "Null owner for users table from: " + sql;
            } else{
                error = "Empty result: " + sql;
            }
            rs.close();
        }

        if (error != null)
            throw new SQLException(error);

        // assert: owner != null

        return (owner.equals(curr)) ? null : owner;
    }

    // dbtype-specific methods

    /**
     * On postgres, get a field's serial sequence name:
     * {@code pg_get_serial_sequence(..)}.
     *<P>
     * Supported by PostgreSQL v8.0 and above (maybe also earlier).
     *
     * @param tabName  Name of table which has {@code fieldName}; assumes no special characters (A-Za-z0-9_ only)
     * @param fieldName  Field name within table; assumes no special characters (A-Za-z0-9_ only)
     * @return Sequence name like {@code "public.games2_gameid_seq"}, or null if field has no sequence
     * @throws IllegalStateException if {@link #dbType} != {@link #DBTYPE_POSTGRESQL}
     * @throws SQLException if {@code tabName} or {@code fieldName} doesn't exist,
     *     or unexpected problem with executeQuery
     * @since 2.0.00
     */
    private String dbtypePostgresGetSerialSequence
        (final String tabName, final String fieldName)
        throws IllegalStateException, SQLException
    {
        if (dbType != DBTYPE_POSTGRESQL)
            throw new IllegalStateException("dbType: " + dbType);

        String seqname = null;

        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery
            ("SELECT pg_get_serial_sequence('" + tabName + "', '" + fieldName + "');");
        if (rs.next())
            seqname = rs.getString(1);
        st.close();  // also closes rs

        return seqname;
    }

    // DDL methods

    /**
     * Run DDL to drop columns, useful during rollback. Syntax varies by DB type.
     * @param tabName Table name
     * @param colNames Columns to drop
     * @return True if drops succeeded, false if an Exception occurred.
     *    The Exception will also be printed to {@link System#err}.
     * @throws IllegalStateException if {@link #dbType} is {@link #DBTYPE_SQLITE}, which cannot drop columns
     * @since 1.2.00
     */
    private boolean runDDL_dropCols(final String tabName, final String[] colNames)
        throws IllegalStateException
    {
        if (dbType == DBTYPE_SQLITE)
            throw new IllegalStateException("sqlite cannot drop columns");

        try {
            if ((dbType == DBTYPE_MARIADB) || (dbType == DBTYPE_MYSQL)
                || (dbType == DBTYPE_POSTGRESQL) || (dbType == DBTYPE_ORA))
            {
                // These dbTypes can drop multiple columns as a single statement; see 2013-09-01 item
                // https://stackoverflow.com/questions/6346120/how-do-i-drop-multiple-columns-with-a-single-alter-table-statement
                // mysql, postgres: ALTER TABLE users DROP pw_scheme, DROP pw_store, DROP pw_change;
                // ora:             ALTER TABLE users DROP (pw_scheme, pw_store, pw_change);
                StringBuilder sb = new StringBuilder("ALTER TABLE ");
                sb.append(tabName);
                for (int i = 0; i < colNames.length; ++i)
                {
                    if (i > 0)
                        sb.append(',');
                    if (dbType != DBTYPE_ORA)
                        sb.append(" DROP ");
                    else if (i == 0)
                        sb.append(" DROP (");
                    sb.append(colNames[i]);
                }
                if (dbType == DBTYPE_ORA)
                    sb.append(')');
                sb.append(';');
                runDDL(sb.toString());
            } else {
                for (int i = 0; i < colNames.length; ++i)
                    runDDL("ALTER TABLE " + tabName + " DROP " + colNames[i] + ';');
            }

            return true;
        }
        catch (Exception e) {
            System.err.println("* Problem during drop columns for " + tabName + ": " + e);

            return false;
        }
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
    private boolean runDDL_rollback(final String sql)
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
     * Run a DDL command to create or remove a database structure.
     * @param sql  SQL to run. Some DB types, including zentus sqlite JDBC, might ignore any SQL after the first ";".
     * @throws IllegalStateException if not connected and if {@link #checkConnection()} fails
     * @throws SQLException if an error occurs while running {@code sql}
     * @since 1.2.00
     * @see #runDDL_rollback(String)
     */
    private void runDDL(final String sql)
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
     * Get a DB setting from the {@code settings} table.
     * @param settingKey  Setting's key name, such as {@link SOCDBHelper#SETTING_BCRYPT_WORK__FACTOR}.
     * @param defaultVal  Value to return if not found
     * @return  Setting's value, or {@code defaultVal} if not found
     * @throws SQLException if an unexpected error occurs
     * @see #updateSetting(String, int, boolean)
     * @since 1.2.00
     */
    private int getIntSetting(final String settingKey, final int defaultVal)
        throws SQLException
    {
        int v = defaultVal;

        Statement s = connection.createStatement();
        ResultSet rs = s.executeQuery
            ("SELECT i_value FROM settings WHERE s_name='" + settingKey + "';");
        if (rs.next())
            v = rs.getInt(1);
        s.close();  // also closes rs

        return v;
    }

    /**
     * Update or add a DB setting in the {@code settings} table.
     * @param settingKey  Setting's key name, such as {@link SOCDBHelper#SETTING_BCRYPT_WORK__FACTOR}.
     * @param val  New value to set
     * @param isAdd  True if adding (inserting), not updating, this setting
     * @throws SQLException if {@code isAdd} but {@code settingKey} is already in the table,
     *     or if an unexpected error occurs
     * @see #getIntSetting(String, int)
     * @see #checkSettings(boolean, boolean)
     * @since 1.2.00
     */
    private void updateSetting(final String settingKey, final int val, final boolean isAdd)
        throws SQLException
    {
        PreparedStatement ps = connection.prepareStatement
            (isAdd
             ? "INSERT INTO settings(s_name, i_value, s_changed) values(?, ?, ?);"
             : "UPDATE settings SET i_value=?, s_changed=? WHERE s_name=?;");
        final Timestamp tsNow = new Timestamp(System.currentTimeMillis());
        if (isAdd)
        {
            ps.setString(1, settingKey);
            ps.setInt(2, val);
            ps.setTimestamp(3, tsNow);
        } else {
            ps.setInt(1, val);
            ps.setTimestamp(2, tsNow);
            ps.setString(3, settingKey);
        }

        ps.executeUpdate();
        ps.close();
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

        int numCols = rsmd.getColumnCount();

        // Display column headings
        for (i = 1; i <= numCols; i++)
        {
            if (i > 1)
                System.out.print(",");

            System.out.print(rsmd.getColumnLabel(i));
        }

        System.out.println("");

        boolean more = rs.next();
        while (more)
        {
            for (i = 1; i <= numCols; i++)
            {
                if (i > 1)
                    System.out.print(",");

                System.out.print(rs.getString(i));
            }

            System.out.println("");

            more = rs.next();
        }
    }

    /****************************************
     * testDBHelper() unit tests
     ****************************************/

    /**
     * Unit testing: Call {@link #doesTableExist(String)} and print results.
     * @param tabname  Table name to check
     * @param wantSuccess  True if expecting it to be found
     * @param isRequired  True if test is required, not optional (affects only the output text, not return value)
     * @return true if call result == {@code wantSuccess}
     * @throws IllegalStateException if not connected; see {@link #doesTableExist(String)} javadoc
     * @since 2.0.00
     */
    private boolean testOne_doesTableExist
        (final String tabname, final boolean wantSuccess, final boolean isRequired)
        throws IllegalStateException
    {
        final boolean exists = doesTableExist(tabname),
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
    private boolean testOne_doesTableColumnExist
        (final String tabname, final String colname, final boolean wantSuccess, final boolean isRequired)
        throws IllegalStateException
    {
        final boolean exists = doesTableColumnExist(tabname, colname),
            pass = (exists == wantSuccess);
        System.err.println
            ( ((pass) ? "test ok" : ((isRequired) ? "test FAIL" : "test failed but optional: ok"))
              + ": doesTableColumnExist(" + tabname + ", " + colname + "): " + exists);

        return (pass);
    }

    /**
     * For {@link #testDBHelper()}, test ability to insert a few rows into {@code games2}
     * and return their primary key IDs: {@link #insertGameRow(String, String, long, int, String, String)}.
     * The new rows are SELECTed and DELETEd as part of the test.
     *<P>
     * Assumes connection is already initialized.
     * If DB schema is older than {@link #SCHEMA_VERSION_2000}, does nothing.
     *
     * @param prepareWithArrayParam  If true, call {@link Connection#prepareStatement(String, String[])}
     *     instead of {@link Connection#prepareStatement(String, int)} before the test-insert calls
     * @param isRequired  True if test is required, not optional (affects only the output text, not return value)
     * @return true if succeeded (or if schema < {@link #SCHEMA_VERSION_2000})
     * @since 2.0.00
     */
    private boolean testOne_insertGameRow(final boolean prepareWithArrayParam, final boolean isRequired)
    {
        final String testDesc = "testOne_insertGameRow(" + prepareWithArrayParam + ')',
                     testFailed = (isRequired) ? "test FAIL" : "test failed but optional: ok";

        if (schemaVersion < SCHEMA_VERSION_2000)
        {
            System.err.println("test skipped (db has old schema): " + testDesc);
            return true;
        }

        final PreparedStatement prevPS = saveGameCommand;

        try
        {
            final PreparedStatement ps = (prepareWithArrayParam)
                ? connection.prepareStatement
                    (SAVE_GAME_COMMAND_2000, new String[]{ "gameid" })
                : connection.prepareStatement
                    (SAVE_GAME_COMMAND_2000, Statement.RETURN_GENERATED_KEYS);
            saveGameCommand = ps;
        } catch(SQLFeatureNotSupportedException sfe) {
            System.err.println(testFailed + " (SQLFeatureNotSupportedException): " + testDesc + ": " + sfe);
            return false;
        } catch(SQLException e) {
            System.err.println(testFailed + " (SQLException): " + testDesc + ": " + e);
            return false;
        }

        final long startTimeMillis = System.currentTimeMillis();
        boolean allOK = true;
        int[] newIDs = new int[5];
        try
        {
            final PreparedStatement psSel = connection.prepareStatement
                ("SELECT gamename,duration_sec FROM games2 WHERE gameid=?");

            // test insertGameRow a few times
            for (int i = 0; i < newIDs.length; ++i)
                newIDs[i] = insertGames2Row
                    ("db_testOne_ins" + i, "winner", startTimeMillis, i+1, null, null);

            // check their IDs (reasonable gameid, can SELECT expected contents)
            for (int i = 0; i < newIDs.length; ++i)
            {
                final int id = newIDs[i];
                if ((id <= 0) || (id > 9999999))
                {
                    System.err.println("Unreasonable gameid=" + id + " for inserted games2 row");
                    allOK = false;
                    continue;
                }

                psSel.setInt(1, id);
                ResultSet rs = psSel.executeQuery();
                if (! rs.next())
                {
                    System.err.println("Can't select from games2 where gameid=" + id);
                    allOK = false;
                } else {
                    String gaName = rs.getString(1);
                    int gaSec = rs.getInt(2);
                    if ((gaSec != (i+1)) || ! ("db_testOne_ins" + i).equals(gaName))
                    {
                        System.err.println("Wrong test data: gameid=" + id);
                        allOK = false;
                    }
                }
                rs.close();
            }
            psSel.close();

            // for cleanup/removal, see "finally" clause below
        } catch (SQLException e) {
            System.err.println(testFailed + " (SQLException): " + testDesc + ": " + e);
            return false;
        } finally {
            for (final int id : newIDs)
                if (id != 0)
                    try
                    {
                        connection.createStatement().executeUpdate
                            ("DELETE FROM games2 WHERE gameid=" + id);
                    } catch (SQLException e) {
                        System.err.println
                            ("* Cleanup failed: couldn't delete temporary games2 where gameid=" + id + ": " + e);
                    }

            saveGameCommand = prevPS;
        }

        System.err.println
            (((allOK) ? "test ok: " : (testFailed + ": ")) + testDesc + ": gameIDs " + Arrays.toString(newIDs));

        return allOK;
    }

    /**
     * For {@link #testDBHelper()}, run a DDL command to create or remove a test fixture.
     * See {@link #runDDL(String)} for details and exception descriptions.
     * @param desc Description to print as part of testing log; will be preceded by "For testing: "
     * @param sql  SQL to run
     * @since 2.0.00
     */
    private void testDBHelper_runDDL(final String desc, final String sql)
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
     * The current DB user must have been granted ability to create and drop new tables,
     * but does not need to be the owner of the currently existing tables. That is, tests
     * can run as the {@code socuser} user and not {@code postgres} or mysql {@code root} user.
     *<P>
     * Called from {@link SOCServer#initSocServer(String, String)}
     * if {@link SOCServer#PROP_JSETTLERS_TEST_DB} flag is set.
     *<P>
     * <B>Security note:</B> To run some tests, the DB connect username must be granted authorization to
     * run DDL commands, add or alter tables, etc.
     *
     * @throws IllegalStateException if {@link #initialize(String, String, Properties)} hasn't yet been called,
     *     or if DB has no tables. To avoid throwing this, don't call this method if ! {@link #isInitialized()}.
     * @throws SQLException  if connection fails or any required tests failed
     * @since 2.0.00
     */
    public final void testDBHelper()
        throws IllegalStateException, SQLException
    {
        if (! initialized)
            throw new IllegalStateException();

        final boolean wasConnAutocommit = connection.getAutoCommit();
            // autocommit mode/transaction tests here use the
            // same idiom as enterTransactionMode() / exitTransactionMode(..)

        boolean anyFailed = false;

        System.err.println();
        {
            final DatabaseMetaData meta = connection.getMetaData();
            System.err.println
                ("DB testing note: dbType " + dbType + ", driver class: " + driverclass
                 + " v" + driverinstance.getMajorVersion() + '.' + driverinstance.getMinorVersion()
                 + " (jdbc v" + meta.getJDBCMajorVersion() + '.' + meta.getJDBCMinorVersion()
                 + "), db version: " + meta.getDatabaseProductVersion()
                 + ", autoCommit: " + wasConnAutocommit
                 + ", supportsGetGeneratedKeys: " + meta.supportsGetGeneratedKeys());
            // Note that ORA's getJDBCMajorVersion() reports the DB version (10, 11, etc) not JDBC's version
        }

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
            System.err.println();

            // Insert and return rowID during save-game (schema v2000 and newer)
            testOne_insertGameRow(false, false);  // prepared with Statement.RETURN_GENERATED_KEYS: support is optional
            anyFailed |= ! testOne_insertGameRow(true, true);   // prepared with {PK_ARRAY}: support is required
            System.err.println();

            // Any dbType-specific tests that don't need a temp table
            if (dbType == DBTYPE_POSTGRESQL)
            {
                // Test that we can get this info without errors; test doesn't need current DB user to be tables' owner
                try
                {
                    upg_postgres_checkIsTableOwner();
                    System.err.println("Test ok: upg_postgres_checkIsTableOwner()");
                } catch (SQLException e) {
                    System.err.println("Test failed: upg_postgres_checkIsTableOwner(): " + e);
                    anyFailed = true;
                }

                System.err.println();
            }

            // Temporarily add a table and field, then test existence, then batch-insert rows.
            // Assumes current DB user has been granted ability to create and drop tables.
            if (! anyFailed)
            {
                boolean hasFixtureTabXYZ = false, hasFixtureFieldXYZW = false,
                    hasFixtureFieldD3 = false, didBulkIns = false;
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
                    // fixtures for runDDL_dropCols:
                    {
                        final String[] cols = {"d1", "d2", "d3"};
                        for (String c : cols)
                        {
                            testDBHelper_runDDL
                                ("fixture: table gamesxyz2 add field " + c,
                                 "ALTER TABLE gamesxyz2 ADD COLUMN " + c + " int;");
                            anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", c, true, true);
                        }
                        hasFixtureFieldD3 = true;
                    }
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

                    // Any dbType-specific tests that need a temp table
                    if (dbType == DBTYPE_POSTGRESQL)
                    {
                        boolean hasFixtureTabPg = false;
                        try {
                            testDBHelper_runDDL
                                ("fixture: create table gamestest_pg",
                                 "CREATE TABLE gamestest_pg ( testid " + INT_AUTO_PK + ", ifield int not null );");
                            hasFixtureTabPg = true;

                            PreparedStatement ps = connection.prepareStatement
                                    ("INSERT INTO gamestest_pg(ifield) VALUES(?)");
                            for (int n = 0; n < 3; ++n)
                            {
                                ps.setInt(1, n);
                                ps.executeUpdate();
                            }
                            ps.close();

                            String seqname = dbtypePostgresGetSerialSequence("gamestest_pg", "ifield");
                            if (seqname != null)
                            {
                                System.err.println("Test failed: PostgreSQL: pg_get_serial_sequence(.., 'ifield') should be null");
                                anyFailed = true;
                            }

                            seqname = dbtypePostgresGetSerialSequence("gamestest_pg", "testid");
                                // 'public.gamestest_testid_seq' (etc)
                            if (seqname != null)
                            {
                                if (! seqname.toLowerCase(Locale.US).contains("testid"))
                                {
                                    System.err.println
                                        ("Test failed: PostgreSQL: pg_get_serial_sequence(.., 'testid') returned \"" + seqname
                                         + "\", doesn't contain \"testid\" as expected");
                                    anyFailed = true;
                                }
                            } else {
                                System.err.println("Test failed: PostgreSQL: pg_get_serial_sequence returned null");
                                anyFailed = true;
                            }

                            if (! anyFailed)
                                System.err.println("Test ok: PostgreSQL: pg_get_serial_sequence(\"gamestest_pg\", ...)");
                        } catch (SQLException e) {
                            System.err.println("Test failed: PostgreSQL: pg_get_serial_sequence: " + e);
                            anyFailed = true;
                        }

                        // cleanup
                        if (hasFixtureTabPg)
                        {
                            try
                            {
                                testDBHelper_runDDL
                                    ("fixture cleanup: drop table gamestest_pg", "DROP TABLE gamestest_pg;");
                            } catch (SQLException e) {
                                System.err.println("Cleanup failed: Drop table gamestest_pg: " + e);
                                anyFailed = true;
                            }
                        }
                    }

                    // batch insert/convert, as seen in upgradeSchema():
                    // ensure jdbc drivers support executeBatch (optional in javadoc) and transactions

                    try
                    {
                        PreparedStatement ps = connection.prepareStatement
                            ("INSERT INTO gamesxyz2(name,xyzw) VALUES(?,?)");

                        // begin transaction
                        if (wasConnAutocommit)
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

                        didBulkIns = true;

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

                    if (didBulkIns)
                    {
                        try
                        {
                            ResultSet rs = selectWithLimit("SELECT * FROM gamesxyz2 WHERE xyzw <= 9", 5);
                            int i = 0;
                            while (rs.next())
                                ++i;
                            rs.close();
                            if (i == 5)
                            {
                                System.err.println("Test ok: selectWithLimit");
                            } else {
                                System.err.println("Test failed: selectWithLimit: Expected 5 rows, got " + i);
                                if (dbType != DBTYPE_UNKNOWN)
                                    anyFailed = true;
                                else
                                    System.err.println("  (failure OK here: dbType is unknown)");
                            }
                        } catch (SQLException e) {
                            System.err.println("Test failed: selectWithLimit: " + e);
                            anyFailed = true;
                        }
                    }

                    if (hasFixtureTabXYZ)
                    {
                        // test index-drop syntax:
                        try
                        {
                            String sql = ((dbType != DBTYPE_MYSQL) && (dbType != DBTYPE_MARIADB))
                                ? "DROP INDEX gamesxyz2__w;"
                                : "DROP INDEX gamesxyz2__w ON gamesxyz2;";
                            testDBHelper_runDDL("fixture cleanup: drop index gamesxyz2__w", sql);
                        } catch (SQLException e) {
                            System.err.println("Cleanup failed: Drop index gamesxyz2__w: " + e);
                            anyFailed = true;
                        }

                        // test column-drop syntax, if not sqlite:
                        if (hasFixtureFieldXYZW && (dbType != DBTYPE_SQLITE))
                        {
                            testDBHelper_runDDL("drop table column gamesxyz2.xyzw",
                                "ALTER TABLE gamesxyz2 DROP xyzw;");
                            anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "xyzw", false, true);

                            // test drop multiple columns
                            if (hasFixtureFieldD3)
                            {
                                final String[] cols = {"d1", "d2", "d3"};
                                if (runDDL_dropCols("gamesxyz2", cols))
                                {
                                    System.err.println("Test ok: runDDL_dropCols gamesxyz2");
                                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "d1", false, true);
                                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "d2", false, true);
                                    anyFailed |= ! testOne_doesTableColumnExist("gamesxyz2", "d3", false, true);
                                } else {
                                    anyFailed = true;
                                    System.err.println("4 Tests failed: runDDL_dropCols gamesxyz2");
                                }
                            }
                        } else {
                            System.err.println
                                ("5 tests skipped for sqlite: drop table column gamesxyz2.xyzw, runDDL_dropCols");
                        }
                        testDBHelper_runDDL("fixture cleanup: drop table gamesxyz2", "DROP TABLE gamesxyz2;");
                        anyFailed |= ! testOne_doesTableExist("gamesxyz2", false, true);
                    }
                }
            } else {
                System.err.println("16 tests skipped because not creating fixture after previous failures.");
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
            System.err.println("*** Some required DB tests failed.");
            throw new SQLException("Required test(s) failed");
        } else {
            System.err.println("* All required DB tests passed.");
        }
    }

    /**
     * Interface for callbacks from {@link SOCDBHelper#authenticateUserPassword(String, String, AuthPasswordRunnable)}.
     * See {@link #authResult(String, boolean)} for callback details.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.2.00
     */
    public static interface AuthPasswordRunnable
    {
        /**
         * Called after user and password are authenticated or rejected, which may be a slow process which runs in
         * its own Thread. So, this callback will occur in the caller's Thread or in a Thread dedicated to
         * {@link BCrypt} calls.
         * @param dbUserName  Username if auth was successful, or {@code null}; same meaning as the String
         *     returned from {@link SOCDBHelper#authenticateUserPassword(String, String, AuthPasswordRunnable)}.
         * @param hadDelay  If true, this callback has been delayed by {@code BCrypt} calculations;
         *     otherwise it's an immediate callback (user not found, password didn't use BCrypt hashing)
         *     and for consistency you might want to delay replying to the client.
         */
        public void authResult(final String dbUserName, final boolean hadDelay);
    }

    /**
     * Thread to run any background tasks needed to complete a schema upgrade,
     * such as data conversions. See {@link SOCDBHelper#doesSchemaUpgradeNeedBGTasks()}
     * for details.
     *<P>
     * Thread will start with a 5-second sleep to let other SOCServer startup tasks complete.
     *<P>
     * To track progress towards {@link SOCDBHelper#SCHEMA_VERSION_LATEST SCHEMA_VERSION_LATEST},
     * this thread reads and updates {@link SOCDBHelper#schemaUpgBGTasks_fromVersion}.
     * When started, that field must be a known {@code SCHEMA_VERSION_*} constant, or 0 to do nothing.
     *
     * @author Jeremy D Monin &lt;jeremy@nand.net&gt;
     * @since 1.2.00
     */
    private class UpgradeBGTasksThread extends Thread
    {
        /** Flag to shut down the thread if set true */
        public volatile boolean doShutdown = false;

        public void run()
        {
            try
            {
                setName("UpgradeBGTasksThread");
            }
            catch (Exception e) {}

            try
            {
                Thread.sleep(5000);
            }
            catch (InterruptedException e) {}

            System.err.println("\n* Schema upgrade: Beginning background tasks\n");

            try
            {
                while ((schemaUpgBGTasks_fromVersion < schemaVersion) && ! doShutdown)
                {
                    final int fromVers = schemaUpgBGTasks_fromVersion;
                    if (fromVers == 0)
                        break;

                    switch (fromVers)
                    {
                    case SCHEMA_VERSION_ORIGINAL:
                        upgradeBGTasks_1000_1200();  // SCHEMA_VERSION_ORIGINAL -> SCHEMA_VERSION_1200
                        break;

                    case SCHEMA_VERSION_1200:
                        upgradeBGTasks_1200_2000();
                        break;

                    default:
                        System.err.println("*** UpgradeBGTasksThread: Unknown fromVersion: " + fromVers);

                        return;  // <--- Early return: Unknown version ----
                    }
                }
            } catch (SQLException e) {
                if (! doShutdown)
                {
                    System.err.println
                        ("*** Schema upgrade: SQL error during background tasks: " + e);
                    e.printStackTrace();
                } else {
                    System.err.println
                        ("*** Schema upgrade: SQL error during shutdown: " + e);
                }

                return;  // <--- Early return: Unexpected problem ---
            }

            schemaUpgBGTasks_fromVersion = 0;

            try
            {
                Timestamp sqlNow = new Timestamp(System.currentTimeMillis());

                PreparedStatement ps = connection.prepareStatement
                    ("UPDATE db_version SET bg_tasks_done = ? WHERE bg_tasks_done IS NULL AND to_vers = ?;");
                ps.setTimestamp(1, sqlNow);
                ps.setInt(2, schemaVersion);
                ps.executeUpdate();
            } catch (SQLException e) {
                System.err.println
                    ("*** Schema upgrade BG tasks completed, but SQL error setting db_version.bg_tasks_done: " + e);
            }

            if (! doShutdown)
                System.err.println("\n* Schema upgrade: Completed background tasks\n");
            else
                // This may not print, depending on shutdown method
                System.err.println("\n* Schema upgrade: Shutting shutdown background tasks, will complete later\n");
        }

        /**
         * Upgrade from {@link SOCDBHelper#SCHEMA_VERSION_ORIGINAL SCHEMA_VERSION_ORIGINAL}
         * to {@link SOCDBHelper#SCHEMA_VERSION_1200 SCHEMA_VERSION_1200}:
         * Encode all {@code users.password} fields into {@code users.pw_store} using {@link BCrypt}.
         */
        private void upgradeBGTasks_1000_1200()
            throws SQLException
        {
            int UPG_BATCH = 10;  // smaller batch, because BCrypt takes a while to run each record
            if (UPG_BATCH > UPG_BATCH_MAX)
                UPG_BATCH = UPG_BATCH_MAX;

            System.err.println("Schema upgrade: Encoding passwords for users");

            SecureRandom sr = new SecureRandom();
            HashSet<String> users = new HashSet<String>();
            do
            {
                users.clear();

                ResultSet rs = selectWithLimit("SELECT nickname_lc FROM users WHERE pw_store IS NULL", UPG_BATCH);
                for (int i = 0; (i < UPG_BATCH) && rs.next(); ++i)
                    users.add(rs.getString(1));
                rs.close();

                if (! users.isEmpty())
                    if (! upgradeSchema_1200_encodeUserPasswords(users, sr, null, null, null))
                        throw new SQLException("L3087 Internal error: Could not select any users.nickname to encode");
            } while (! (doShutdown || users.isEmpty()));

            if (! doShutdown)
                System.err.println("Schema upgrade: User password encoding: Completed");

            schemaUpgBGTasks_fromVersion = SCHEMA_VERSION_1200;
        }

        /**
         * Upgrade from {@link SOCDBHelper#SCHEMA_VERSION_1200 SCHEMA_VERSION_1200}
         * to {@link SOCDBHelper#SCHEMA_VERSION_2000 SCHEMA_VERSION_2000}:
         *<UL>
         * <LI> Normalize {@code games} into {@code games2} and {@code games2_players}, from {@code upg_tmp_games} copy
         * <LI> Update DB {@code users}' win/loss records while doing so
         *</UL>
         * @since 2.0.00
         */
        private void upgradeBGTasks_1200_2000()
            throws SQLException
        {
            final int UPG_BATCH = UPG_BATCH_MAX / 3 + 1;
                // less than max, because loop body includes per-game updates for several tables

            System.err.println("Schema upgrade: Normalizing games into games2");

            // key = nickname_lc, value = nickname
            final HashMap<String, String> allDBUsers = new HashMap<String, String>();
            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT nickname_lc, nickname FROM users");
            while (rs.next())
                allDBUsers.put(rs.getString(1), rs.getString(2));
            st.close();  // also closes rs

            PreparedStatement psInsPlayer = connection.prepareStatement
                ("INSERT INTO games2_players(gameid,player,score) VALUES(?,?,?);");
            PreparedStatement psSetWinner = connection.prepareStatement
                ("UPDATE games2 SET winner=? WHERE gameid=?;");
            PreparedStatement psAddUserWins = connection.prepareStatement
                ("UPDATE users SET games_won = coalesce(games_won,0) + ? WHERE nickname=?;");
            PreparedStatement psAddUserLosses = connection.prepareStatement
                ("UPDATE users SET games_lost = coalesce(games_lost,0) + ? WHERE nickname=?;");
            PreparedStatement psAddUserWinsLosses = connection.prepareStatement
                ("UPDATE users SET games_won = coalesce(games_won,0) + ?, games_lost = coalesce(games_lost,0) + ? WHERE nickname=?;");

            boolean hasGames;  // if so, some games were converted: should call psInsPlayer.executeBatch()
            boolean hasSetWinners;  // if so, some game winners were determined: psSetWinner.executeBatch()
            HashMap<String, IntPair> winLossDBUsers = new HashMap<String, IntPair>();  // users' win,loss adds in this batch

            // begin transaction of first loop iteration
            final boolean wasConnAutocommit = enterTransactionMode();

            try
            {

                do
                {
                    // Iterate through copied games (UPG_BATCH at a time):
                    // - Normalize per-player info into games2_players table
                    // - If players are users in DB, their names get normalized, including games2 winner field

                    hasGames = false;
                    hasSetWinners = false;

                    StringBuilder sbMarkUpg = new StringBuilder
                        ("UPDATE upg_tmp_games SET mig_done=1 WHERE gameid IN (");

                    rs = selectWithLimit
                        ("SELECT gameid,winner,player1,player2,player3,player4,player5,player6,score1,score2,score3,score4,score5,score6"
                         + " FROM upg_tmp_games WHERE mig_done IS NULL", UPG_BATCH);
                    for (int i = 0; (i < UPG_BATCH) && rs.next(); ++i)
                    {
                        final int gameid = rs.getInt(1);
                        String winner = rs.getString(2);
                        if ((winner != null) && winner.equals("?"))
                            winner = null;
                        final String[] plNames = new String[6];
                        final int[] plScores = new int[6];
                        for (int pn = 0; pn < 6; ++pn)
                            plNames[pn] = rs.getString(pn + 3);
                        for (int pn = 0; pn < 6; ++pn)
                            plScores[pn] = rs.getInt(pn + 3 + 6);

                        /** if true, update this field: currently either '?' or non-normalized name of a DB user */
                        boolean setWinnerInGames2 = false;

                        String winner_LC = null;  // lowercase, for normalized-username lookups in DB
                        final boolean winnerWasNull = (winner == null);
                        if (winnerWasNull)
                        {
                            // try to determine winnner from scores; if tied, don't pick one

                            int highscore = 0, winPN = -1;
                            boolean hadTie = false;
                            for (int pn = 0; pn < 6; ++pn)
                            {
                                if (plNames[pn] == null)
                                    continue;
                                final int score = plScores[pn];
                                if (score > highscore)
                                {
                                    highscore = score;
                                    hadTie = false;
                                    winPN = pn;
                                } else if (score == highscore) {
                                    hadTie = true;
                                }
                            }

                            if ((winPN != -1) && ! hadTie)
                            {
                                winner = plNames[winPN];
                                winner_LC = winner.toLowerCase(Locale.US);
                                setWinnerInGames2 = true;

                                // normalize nickname if in DB
                                final String dbName = allDBUsers.get(winner_LC);
                                if (dbName != null)
                                    winner = dbName;
                            }
                        } else {
                            winner_LC = winner.toLowerCase(Locale.US);
                        }

                        // Set per-player scores:

                        for (int pn = 0; pn < 6; ++pn)
                        {
                            String name = plNames[pn];
                            if (name == null)
                                continue;

                            final String name_LC = name.toLowerCase(Locale.US);
                            final boolean playerWon = name_LC.equals(winner_LC);

                            // If player is user in DB, see if need to normalize username. If so:
                            //   Normalize for storage in games2_players
                            //   If player is winner:
                            //     If ! winnerWasNull, see if need to normalize winner name
                            //       If so, normalize winner var & set setWinnerInGames2 flag

                            final String dbName = allDBUsers.get(name_LC);
                            if (dbName != null)
                            {
                                name = dbName;

                                IntPair userWinLoss = winLossDBUsers.get(dbName);
                                if (userWinLoss == null)
                                {
                                    userWinLoss = new IntPair(0, 0);
                                    winLossDBUsers.put(dbName, userWinLoss);
                                }

                                if (playerWon)
                                {
                                    userWinLoss.a++;
                                    if (! (winnerWasNull || winner.equals(dbName)))
                                    {
                                        winner = dbName;
                                        setWinnerInGames2 = true;
                                    }
                                } else {
                                    userWinLoss.b++;
                                }
                            }

                            psInsPlayer.setInt(1, gameid);
                            psInsPlayer.setString(2, name);
                            psInsPlayer.setInt(3, plScores[pn]);
                            psInsPlayer.addBatch();
                        }

                        if (i > 0)
                            sbMarkUpg.append(',');
                        else
                            hasGames = true;
                        sbMarkUpg.append(gameid);

                        if (setWinnerInGames2)
                        {
                            psSetWinner.setString(1, winner);
                            psSetWinner.setInt(2, gameid);
                            psSetWinner.addBatch();
                            hasSetWinners = true;
                        }
                    }
                    rs.close();

                    if (hasGames)
                    {
                        // "begin transaction" happens just above do-loop.
                        // Transaction is committed at bottom of loop body, which begins a new one.

                        psInsPlayer.executeBatch();

                        if (! winLossDBUsers.isEmpty())
                        {
                            for (final String dbUser : winLossDBUsers.keySet())
                            {
                                final IntPair WL = winLossDBUsers.get(dbUser);
                                final int wins = WL.a, losses = WL.b;
                                if (wins != 0)
                                {
                                    if (losses != 0)
                                    {
                                        psAddUserWinsLosses.setInt(1, wins);
                                        psAddUserWinsLosses.setInt(2, losses);
                                        psAddUserWinsLosses.setString(3, dbUser);
                                        psAddUserWinsLosses.executeUpdate();
                                    } else {
                                        psAddUserWins.setInt(1, wins);
                                        psAddUserWins.setString(2, dbUser);
                                        psAddUserWins.executeUpdate();
                                    }
                                } else {
                                    psAddUserLosses.setInt(1, losses);
                                    psAddUserLosses.setString(2, dbUser);
                                    psAddUserLosses.executeUpdate();
                                }
                            }

                            winLossDBUsers.clear();
                        }

                        if (hasSetWinners)
                            psSetWinner.executeBatch();

                        sbMarkUpg.append(");");
                        st = connection.createStatement();
                        st.executeUpdate(sbMarkUpg.toString());  // UPDATE upg_tmp_games SET mig_done=1 WHERE gameid IN (...)
                        st.close();

                        connection.commit();  // also begins transaction for next iteration
                    }

                } while (hasGames && ! doShutdown);

                if (! doShutdown)
                {
                    runDDL("DROP TABLE upg_tmp_games;");

                    System.err.println("Schema upgrade: Normalizing games into games2: Completed");
                }

                schemaUpgBGTasks_fromVersion = SCHEMA_VERSION_2000;

            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                exitTransactionMode(wasConnAutocommit);
            }
        }

    }

}
