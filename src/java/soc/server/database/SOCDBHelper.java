/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas <thomas@infolab.northwestern.edu>
 * Portions of this file Copyright (C) 2009-2010,2012 Jeremy D Monin <jeremy@nand.net>
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
import soc.util.SOCRobotParameters;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Calendar;
import java.util.Properties;


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
 *<BR> (See src/bin/sql/jsettlers-tables.sql) <BR>
 *<code>
 * CREATE DATABASE socdata;
 * USE socdata;
 * CREATE TABLE users (nickname VARCHAR(20), host VARCHAR(50), password VARCHAR(20), email VARCHAR(50), lastlogin DATE);
 * CREATE TABLE logins (nickname VARCHAR(20), host VARCHAR(50), lastlogin DATE);
 * CREATE TABLE games (gamename VARCHAR(20), player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20), score1 TINYINT, score2 TINYINT, score3 TINYINT, score4 TINYINT, starttime TIMESTAMP);
 * CREATE TABLE robotparams (robotname VARCHAR(20), maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT, strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag BOOL);
 *</code>
 *
 * @author Robert S. Thomas
 */
public class SOCDBHelper
{
    // If a new property is added, please add a PROP_JSETTLERS_DB_ constant
    // and also add it to SOCServer.PROPS_LIST.

    /** Property <tt>jsettlers.db.user</tt> to specify the server's SQL database username.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_USER = "jsettlers.db.user";

    /** Property <tt>jsettlers.db.pass</tt> to specify the server's SQL database password.
     * @since 1.1.09
     */
    public static final String PROP_JSETTLERS_DB_PASS = "jsettlers.db.pass";

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

    /** Property <tt>jsettlers.db.save.games</tt> to ask to save
     * all game results in the database.
     * Set this to 1 or Y to activate this feature.
     * @since 2.0.00
     */
    public static final String PROP_JSETTLERS_DB_SAVE_GAMES = "jsettlers.db.save.games";

    /**
     * The db driver used, or null if none.
     * Set in {@link #initialize(String, String, Properties)}.
     * @since 1.1.14
     */
    private static String driverclass = null;

    /**
     * db connection, or <tt>null</tt> if never initialized or if cleaned up for shutdown.
     * If this is non-null but closed, most queries will try to recreate it via {@link #checkConnection()}.
     * Set in {@link #connect(String, String)}, based on the {@link #dbURL}
     * from {@link #initialize(String, String, Properties)}.
     * Cleared in {@link #cleanup(boolean) cleanup(true)}.
     */
    private static Connection connection = null;

    /**
     * Retain the URL (default, or passed via props to {@link #initialize(String, String, Properties)}).
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

    /** Cached username used when reconnecting on error */
    private static String userName;

    /** Cached password used when reconnecting on error */
    private static String password;

    private static String CREATE_ACCOUNT_COMMAND = "INSERT INTO users VALUES (?,?,?,?,?);";
    private static String RECORD_LOGIN_COMMAND = "INSERT INTO logins VALUES (?,?,?);";
    private static String USER_PASSWORD_QUERY = "SELECT password FROM users WHERE ( users.nickname = ? );";
    private static String HOST_QUERY = "SELECT nickname FROM users WHERE ( users.host = ? );";
    private static String LASTLOGIN_UPDATE = "UPDATE users SET lastlogin = ?  WHERE nickname = ? ;";
    private static String SAVE_GAME_COMMAND = "INSERT INTO games VALUES (?,?,?,?,?,?,?,?,?,?);";
    private static String ROBOT_PARAMS_QUERY = "SELECT * FROM robotparams WHERE robotname = ?;";

    private static PreparedStatement createAccountCommand = null;
    private static PreparedStatement recordLoginCommand = null;
    private static PreparedStatement userPasswordQuery = null;
    private static PreparedStatement hostQuery = null;
    private static PreparedStatement lastloginUpdate = null;
    private static PreparedStatement saveGameCommand = null;
    private static PreparedStatement robotParamsQuery = null;

    /**
     * This makes a connection to the database
     * and initializes the prepared statements.
     * Sets {@link #isInitialized()}.
     *<P>
     * The default URL is "jdbc:mysql://localhost/socdata".
     * The default driver is "com.mysql.jdbc.Driver".
     * These can be changed by supplying <code>props</code>.
     *
     * @param user  the user name for accessing the database
     * @param pswd  the password for the user
     * @param props  null, or properties containing {@link #PROP_JSETTLERS_DB_USER},
     *       {@link #PROP_JSETTLERS_DB_URL}, and any other desired properties.
     * @throws SQLException if an SQL command fails, or the db couldn't be
     *         initialized;
     *         or if the {@link #PROP_JSETTLERS_DB_DRIVER} property is not mysql, not sqlite, not postgres,
     *         but the {@link #PROP_JSETTLERS_DB_URL} property is not provided.
     */
    public static void initialize(String user, String pswd, Properties props) throws SQLException
    {
        initialized = false;

        // Driver types and URLs recognized here should
        // be the same as those listed in README.txt.

        driverclass = "com.mysql.jdbc.Driver";
    	dbURL = "jdbc:mysql://localhost/socdata";
    	if (props != null)
    	{
    	    String prop_dbURL = props.getProperty(PROP_JSETTLERS_DB_URL);
    	    String prop_driverclass = props.getProperty(PROP_JSETTLERS_DB_DRIVER);
    	    if (prop_dbURL != null)
    	    {
    	        dbURL = prop_dbURL;
    	        if (prop_driverclass != null)
    	            driverclass = prop_driverclass;
    	        else if (prop_dbURL.startsWith("jdbc:postgresql"))
    	            driverclass = "org.postgresql.Driver";
    	        else if (prop_dbURL.startsWith("jdbc:sqlite:"))
    	            driverclass = "org.sqlite.JDBC";
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
                }
                else if (driverclass.contains("sqlite"))
                {
                    dbURL = "jdbc:sqlite:socdata.sqlite";
                }
                else if (! driverclass.contains("mysql"))
    	        {
    	            throw new SQLException("JDBC: Driver property is set, but URL property is not: ("
    	                + PROP_JSETTLERS_DB_DRIVER + ", " + PROP_JSETTLERS_DB_URL + ")");
    	        }
    	    }
    	}

    	try
        {
            // Load the JDBC driver. Revisit exceptions when /any/ JDBC allowed.
            Class.forName(driverclass).newInstance();

            connect(user, pswd);
        }
        catch (ClassNotFoundException x)
        {
            SQLException sx =
                new SQLException("JDBC driver is unavailable: " + driverclass);
            sx.initCause(x);
            throw sx;
        }
        catch (Throwable x) // everything else
        {
            // InstantiationException & IllegalAccessException
            // should not be possible  for org.gjt.mm.mysql.Driver
            // ClassNotFound
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
            return (! errorCondition) || connect(userName, password);
        }

        return false;
    }

    /**
     * Opens a new connection and initializes the prepared statements.
     * {@link #initialize(String, String, Properties)} and {@link #checkConnection()} use this to get ready.
     */
    private static boolean connect(String user, String pswd)
        throws SQLException
    {
        connection = DriverManager.getConnection(dbURL, user, pswd);

        errorCondition = false;
        userName = user;
        password = pswd;
        
        // prepare PreparedStatements for queries
        createAccountCommand = connection.prepareStatement(CREATE_ACCOUNT_COMMAND);
        recordLoginCommand = connection.prepareStatement(RECORD_LOGIN_COMMAND);
        userPasswordQuery = connection.prepareStatement(USER_PASSWORD_QUERY);
        hostQuery = connection.prepareStatement(HOST_QUERY);
        lastloginUpdate = connection.prepareStatement(LASTLOGIN_UPDATE);
        saveGameCommand = connection.prepareStatement(SAVE_GAME_COMMAND);
        robotParamsQuery = connection.prepareStatement(ROBOT_PARAMS_QUERY);

        return true;
    }
    
    /**
     * Retrieve this user's password from the database.
     *
     * @param sUserName Username who needs password
     *
     * @return null if user account doesn't exist, or if no database is currently connected
     *
     * @throws SQLException if any unexpected database problem
     */
    public static String getUserPassword(String sUserName) throws SQLException
    {
        String password = null;

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                // fill in the data values to the Prepared statement
                userPasswordQuery.setString(1, sUserName);

                // execute the Query
                ResultSet resultSet = userPasswordQuery.executeQuery();

                // if no results, user is not authenticated
                if (resultSet.next())
                {
                    password = resultSet.getString(1);
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

        return password;
    }

    /**
     * DOCUMENT ME!
     *
     * @param host DOCUMENT ME!
     *
     * @return  null if user is not authenticated
     *
     * @throws SQLException DOCUMENT ME!
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
     * DOCUMENT ME!
     *
     * @param userName DOCUMENT ME!
     * @param host DOCUMENT ME!
     * @param password DOCUMENT ME!
     * @param email DOCUMENT ME!
     * @param time DOCUMENT ME!
     *
     * @return true if the account was created
     *
     * @throws SQLException DOCUMENT ME!
     */
    public static boolean createAccount(String userName, String host, String password, String email, long time) throws SQLException
    {
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
     * DOCUMENT ME!
     *
     * @param userName DOCUMENT ME!
     * @param host DOCUMENT ME!
     * @param time DOCUMENT ME!
     *
     * @return true if the login was recorded
     *
     * @throws SQLException DOCUMENT ME!
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
     * @throws SQLException DOCUMENT ME!
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
     * Record this game's time, players, and scores in the database.
     *
     * @param game  Game that's just completed
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
     * for the first 4 player numbers, and copies player 5 and 6's
     * data to those positions in <tt>names[]</tt> and <tt>scores[]</tt>.
     * @param ga  Game that's over
     * @param names  Player names for player number 0-5; contents will be changed
     * @param scores  Player scores for player number 0-5; contents will be changed
     * @since 2.0.00
     */
    private static void saveGameScores_fit6pInto4
        (SOCGame ga, String[] names, short[] scores)
    {
        // Need to try and fit player 5 and/or player 6
        // into the 4 db slots (backwards-compatibility)

        int nVacantLow = 0;
        for (int pn = 0; pn < 4; ++pn)
            if (ga.isSeatVacant(pn))
                ++nVacantLow;

        int nOccupiedHigh = 0;
        int plHighA = -1, plHighB = -1;
        if (! ga.isSeatVacant(4))
        {
            ++nOccupiedHigh;
            plHighA = 4;
        }
        if (! ga.isSeatVacant(5))
        {
            ++nOccupiedHigh;
            if (plHighA != -1)
                plHighB = 5;
            else
                plHighA = 5;
        }

        if (nVacantLow >= nOccupiedHigh)
        {
            // Enough room; total non-vacant players <= 4.

            // First, find a spot for plHighA
            int pn = 0;
            for (; pn < 4; ++pn)
            {
                if (ga.isSeatVacant(pn))
                {
                    // pn gets plHighA's info
                    names[pn] = names[plHighA];
                    scores[pn] = scores[plHighA];
                    break;
                }
            }

            if (plHighB != -1)
            {
                // Find a spot for plHighB
                ++pn;
                for (; pn < 4; ++pn)
                {
                    if (ga.isSeatVacant(pn))
                    {
                        names[pn] = names[plHighB];
                        scores[pn] = scores[plHighB];
                        break;
                    }
                }
            }
        }

        // TODO else not enough room.
        // What if pl5 or pl6 is the winner?
        // Replace lowest-score within first 4?
        // Replace a robot if pl5/pl6 is a human? (What if bot won?)
    }

    /**
     * DOCUMENT ME!
     *
     * @param robotName DOCUMENT ME!
     *
     * @return null if robotName not in database
     *
     * @throws SQLException DOCUMENT ME!
     */
    public static SOCRobotParameters retrieveRobotParams(String robotName) throws SQLException
    {
        SOCRobotParameters robotParams = null;

        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
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
     * Query to see if a column exists in a table.
     * Any exception is caught here and returns false.
     * @param tabname  Table name to check <tt>colname</tt> within; case-sensitive in some db types
     * @param colname  Column name to check; case-sensitive in some db types.
     *    The jsettlers standard is to always use lowercase names when creating tables and columns.
     * @return  true if column exists in the current connection's database
     * @throws IllegalStateException  If not connected and if {@link #checkConnection()} fails
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
            throw new IllegalStateException();
        }

        ResultSet rs = null;
        try
        {
            final boolean checkResultNum;  // Do we need to check query result contents?

            PreparedStatement ps;
            if (! driverclass.toLowerCase().contains("oracle"))
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
            }
            catch (SQLException sqlE)
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
    private static void dispResultSet(ResultSet rs) throws SQLException
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
}
