Java Settlers - A web-based client-server version of Settlers of Catan

Introduction
------------

JSettlers is a web-based version of the board game Settlers of Catan
written in Java. This client-server system supports multiple
simultaneous games between people and computer-controlled
opponents. Initially created as an AI research project.

The client may be run as a Java application, or as an applet when
accessed from a web site which also hosts a JSettlers server.

The server may be configured to use a database to store account
information (details below).  A client applet to create user accounts
is also provided.

JSettlers is an open-source project licensed under the GPL. The
software is hosted at http://sourceforge.net/projects/jsettlers2
and at http://nand.net/jsettlers/devel/ .  Questions, bugs, and
patches can be posted at the sourceforge page.

                          -- The JSettlers Development Team


Contents
--------

  Documentation
  Requirements
  Setting up and testing
  Shutting down the server
  Hosting a JSettlers Server
  Database Setup
  Development and Compiling


Documentation
-------------

User documentation for game play is available as .html pages located
in "docs/users" directory. These can be put on a JSettlers server for
its users using the applet.

Currently, this README is the only technical documentation for running
the client or server, setup and other issues. Over time other more
will be written. If you are interested in helping write documentation
please contact the development team from the SourceForge site.


Requirements
------------

To play JSettlers by connecting to a remote server you will need the
Java Runtime Version 1.4 or above (1.5 or later recommended). To connect as an
applet, use any browser which is Java enabled (using the browser plug-in).

To Play JSettlers locally you need the Java Runtime 1.4 or above.
JSettlers-full.jar can connect directly to any server over TCP/IP

To host a JSettlers server that provides a web applet for clients, you will
need an http server such as Apache's httpd, available from http://httpd.apache.org.
The JSettlers-full.jar file can also run locally as a server, without
needing a web server.  The applet is considered more convenient,
because you know everyone will have the same version.

To build JSettlers from source, you will need Apache Ant, available from
http://ant.apache.org, or an IDE such as Eclipse which understands Ant's format.


Setting up and testing
----------------------

From the command line, make sure you are in the JSettlers distribution
directory which contains both JSettlers.jar, settlers-server.jar and the
"lib" directory.  (If you have downloaded jsettlers-1.1.xx-full.tar.gz,
look in the src/target directory for these files.)

Start the server with the following command
(server requires Java 1.4 or higher):

  java -jar JSettlersServer.jar 8880 10 dbUser dbPass

If MySQL or another database is not installed and running (See "Database Setup"),
you will see a warning with the appropriate explanation:

  Warning: failed to initialize database: ....

The database is not required: Without it, the server will function normally except
that user accounts cannot be maintained.  If you do use the database, you'll need
to give users an account before they are allowed to log in and play.

Now, from another command line window, start the player client with
the following command:

  java -jar JSettlers.jar localhost 8880

In the player client window, enter "debug" in the Nickname field and
create a new game.

Type *STATS* into the chat part of the game window.  You should see
something like the following in the chat display:

  * > Uptime: 0:0:26
  * > Total connections: 1
  * > Current connections: 1
  * > Total Users: 1
  * > Games started: 0
  * > Games finished: 0
  * > Total Memory: 2031616
  * > Free Memory: 1524112
  * > Version: 1100 (1.1.00) build JM20080808

If you do not, you might not have entered your nickname correctly.  It
must be "debug" in order to use the administrative commands.

Now you can add some robot players.  Enter the following commands in
separate command line windows:

  java -cp JSettlersServer.jar soc.robot.SOCRobotClient localhost 8880 robot1 passwd

  java -cp JSettlersServer.jar soc.robot.SOCRobotClient localhost 8880 robot2 passwd

  java -cp JSettlersServer.jar soc.robot.SOCRobotClient localhost 8880 robot3 passwd

Now click on the "Sit Here" button and press "Start Game".  The robot
players should automatically join the game and start playing.

If you want other people to access your server, tell them your server
IP address and port number (in this case 8880).  They can run the
JSettlers.jar file by itself, and it will bring up a window to enter your IP
and port number.  Or, they can enter the following command:

  java -jar JSettlers.jar <host> <port_number>

Where host is the IP address and port_number is the port number.

If you would like to maintain accounts for your JSettlers server,
start the database prior to starting the JSettlers Server. See the
directions in "Database Setup".


Shutting down the server
------------------------

To shut down the server enter *STOP* in the chat area of a game
window.  This will stop the server and all connected clients will be
disconnected.


Hosting a JSettlers server
--------------------------
  - Start MySQL or PostgreSQL server (optional)
  - Start JSettlers Server
  - Start http server (optional)
  - Copy JSettlers.jar jar and "web/*.html" server directory (optional)
  - Copy "docs/users" to the server directory (optional)

To host a JSettlers server, start the server as described in "Setting up
and Testing". To maintain user accounts, be sure to start the database
first. (If you use a database, you'll need to give users an account
before they are allowed to log in and play.)

Remote users can simply start their clients as described there,
and specify your server as host.

To provide a web page from which users can run the applet, you will
need to set up an html server, such as Apache.  We assume you have
installed it correctly, and will refer to "${docroot}" as a directory
your web server is configured to provide.

Copy the sample .html pages from "web" to ${docroot}. Edit them, to
make sure the PORT parameter in "index.html" and "account.html" applet
tags match the port of your JSettlers server.

Next copy the client files to the server. Copy JSettlers.jar to
${docroot}. This will allow users with Java version 1.2 or later
installed to use the browser plug-in. Using the .jar like allows for
faster downloads, and startup times, but does not allow browsers with
Java version 1.1 to start the client.

To allow browsers with old versions of Java (1.1) to use the applet,
unpack JSettlers.jar and copy (recursively) the extracted "soc"
and "resources" directories to ${docroot}. To unpack, use:

    $ jar -xf JSettlers.jar

You may also copy the "doc/users" directory (recursively) to the same
directory as the sample .html pages to provide user documentation.

Your web server directory structure should now contain:
  ${docroot}/index.html
  ${docroot}/*.html
  ${docroot}/JSettlers.jar
  ${docroot}/resources/...
  ${docroot}/soc/...
  ${docroot}/users/...

Users should now be able to visit your web site to run the client
version of JSettlers.


Database Setup
--------------

If you want to maintain user accounts, you will need to set up a MySQL
or PostgreSQL database. This will eliminate the "Problem connecting to database"
errors from the server. We assume you have installed it correctly. 

The default name for the database is "socdata".  To use another name,
you'll need to specify it as a JDBC URL on the command line, such as:
	-Djsettlers.db.url=jdbc:mysql://localhost/socdata
or
	-Djsettlers.db.url=jdbc:postgresql://localhost/socdata
or
	-Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite

The default JDBC driver is com.mysql.jdbc.Driver.  PostgreSQL is also
recognized.  To use PostgreSQL, use a postgresql url like the one shown above,
or specify the driver on the SOCServer command line:
	-Djsettlers.db.driver=org.postgresql.Driver

Depending on your computer's setup, you may need to point JSettlers at the
appropriate JDBC drivers, by placing them in your java classpath.
Your database system's JDBC drivers can be downloaded at these locations:
	MySQL:   http://www.mysql.com/products/connector/
	PostgreSQL:  http://jdbc.postgresql.org/download.html
	SQLite:  http://www.zentus.com/sqlitejdbc/

Run the following commands to create the database and configure its
tables.

CREATE DATABASE socdata;

USE socdata;

CREATE TABLE users (nickname VARCHAR(20), host VARCHAR(50), password VARCHAR(20), email VARCHAR(50), lastlogin DATE);

CREATE TABLE logins (nickname VARCHAR(20), host VARCHAR(50), lastlogin DATE);

CREATE TABLE games (gamename VARCHAR(20), player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20), score1 SMALLINT, score2 SMALLINT, score3 SMALLINT, score4 SMALLINT, starttime TIMESTAMP);

CREATE TABLE robotparams (robotname VARCHAR(20), maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT, strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag BOOL);


To create accounts, run the simple account creation client with the
following command:

  java -jar JSettlers.jar soc.client.SOCAccountClient localhost 8880

Until you create accounts, no one will be able to log into the server to play games.


Development and Compiling
-------------------------

Source code for JSettlers is available via tarballs.
See the project website at http://nand.net/jsettlers/devel/
for details. Patches against the latest version may be submitted there.

Before building, make sure you have at least version 1.4 of the Java
development kit installed.  If you simply want to run the client and
server, you only need the Java Runtime (JRE). If you wish to maintain a user
database for your server, you need MySQL installed, and configured.

This package was designed to use the ANT tool available from
http://ant.apache.org tools.  We assume you have installed it
correctly.

Check the "build.properties" file. There may be build variables you
may want to change locally. These can also be changed from the command
line when calling ant, by passing a "-Dname=value" parameter to ant.

Now you are ready to invoke ant. There are several targets, here are
the most useful ones:

 build      Create project jar files. (default)
 clean      Cleans the project of all generated files
 compile    Compile class files into "target/classes"
 dist       Build distribution tarballs and zips.
 javadoc    Creates JavaDoc files in "target/docs/api"
 src        Create a tarball of the source tree

All files created by building are in the "target" directory, including
Java .class files, and JavaDoc files. Distribution tarballs, zip
files, and installation files are placed in "dist".
