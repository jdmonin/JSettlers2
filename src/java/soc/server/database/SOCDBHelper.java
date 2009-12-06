/**
 * Java Settlers - An online multiplayer version of the game Settlers of Catan
 * Copyright (C) 2003  Robert S. Thomas
 * Some documentation javadocs here are Copyright (C) 2009 Jeremy D Monin <jeremy@nand.net>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * The author of this program can be reached at thomas@infolab.northwestern.edu
 **/
package soc.server.database;

import soc.util.SOCRobotParameters;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Calendar;


/**
 * This class contains methods for connecting to a database
 * and for manipulating the data stored there.
 *
 * Based on jdbc code found at www.javaworld.com
 *
 * @author Robert S. Thomas
 */
/**
 * This code assumes that you're using mySQL as your database.
 * It uses a database created with the following commands:
 * CREATE DATABASE socdata;
 * USE socdata;
 * CREATE TABLE users (nickname VARCHAR(20), host VARCHAR(50), password VARCHAR(20), email VARCHAR(50), lastlogin DATE);
 * CREATE TABLE logins (nickname VARCHAR(20), host VARCHAR(50), lastlogin DATE);
 * CREATE TABLE games (gamename VARCHAR(20), player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20), score1 TINYINT, score2 TINYINT, score3 TINYINT, score4 TINYINT, starttime TIMESTAMP);
 * CREATE TABLE robotparams (robotname VARCHAR(20), maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT, strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag BOOL);
 *
 */
public class SOCDBHelper
{
    private static Connection connection = null;

    /**
     * This flag indicates that the connection should be valid, yet the last
     * operation failed. Methods will attempt to reconnect prior to their
     * operation if this is set.
     */
    private static boolean errorCondition = false;

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
     *
     * @param user  the user name for accessing the database
     * @param pswd  the password for the user
     * @throws SQLException if an SQL command fails, or the db couldn't be
     *         initialized
     */
    public static void initialize(String user, String pswd) throws SQLException
    {
        try
        {
            // Load the mysql driver. Revisit exceptions when /any/ JDBC allowed
            Class.forName("org.gjt.mm.mysql.Driver").newInstance();

            connect(user, pswd);
        }
        catch (ClassNotFoundException x)
        {
            SQLException sx =
                new SQLException("MySQL driver is unavailable");
            sx.initCause(x);
            throw sx;
        }
        catch (Exception x) // everything else
        {
            // InstantiationException & IllegalAccessException
            // should not be possible  for org.gjt.mm.mysql.Driver
            // ClassNotFound
            SQLException sx = new SQLException("Unable to initialize user database");
            sx.initCause(x);
            throw sx;
        }
    }

    /**
     * Checks if connection is supposed to be present and attempts to reconnect
     * if there was previously an error.  Reconnecting closes the current
     * conection, opens a new one, and re-initializes the prepared statements.
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
     * initialize and checkConnection use this to get ready.
     */
    private static boolean connect(String user, String pswd)
        throws SQLException
    {
        String url = "jdbc:mysql://localhost/socdata";

        connection = DriverManager.getConnection(url, user, pswd);

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
     * DOCUMENT ME!
     *
     * @param gameName DOCUMENT ME!
     * @param player1 DOCUMENT ME!
     * @param player2 DOCUMENT ME!
     * @param player3 DOCUMENT ME!
     * @param player4 DOCUMENT ME!
     * @param score1 DOCUMENT ME!
     * @param score2 DOCUMENT ME!
     * @param score3 DOCUMENT ME!
     * @param score4 DOCUMENT ME!
     * @param startTime DOCUMENT ME!
     *
     * @return true if the save succeeded
     *
     * @throws SQLException DOCUMENT ME!
     */
    public static boolean saveGameScores(String gameName, String player1, String player2, String player3, String player4, short score1, short score2, short score3, short score4, java.util.Date startTime) throws SQLException
    {
        // ensure that the JDBC connection is still valid
        if (checkConnection())
        {
            try
            {
                // fill in the data values to the Prepared statement
                saveGameCommand.setString(1, gameName);
                saveGameCommand.setString(2, player1);
                saveGameCommand.setString(3, player2);
                saveGameCommand.setString(4, player3);
                saveGameCommand.setString(5, player4);
                saveGameCommand.setShort(6, score1);
                saveGameCommand.setShort(7, score2);
                saveGameCommand.setShort(8, score3);
                saveGameCommand.setShort(9, score4);
                saveGameCommand.setTimestamp(10, new Timestamp(startTime.getTime()));

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
     * DOCUMENT ME!
     */
    public static void cleanup() throws SQLException
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
                connection.close();
            }
            catch (SQLException sqlE)
            {
                errorCondition = true;
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
