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
information and game stats (details below).  A client applet to
create user accounts is also provided.

If you're upgrading from an earlier version of JSettlers, check
VERSIONS.txt for new features, bug fixes, and config changes.

JSettlers is an open-source project licensed under the GPL. The
project is hosted at http://sourceforge.net/projects/jsettlers2
and at http://nand.net/jsettlers/devel/ .  Questions, bugs, and
patches can be posted at the sourceforge page; patches can also
be sent to https://github.com/jdmonin/JSettlers2 .

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
the client or server, setup and other issues. Over time, more docs
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
directory which contains both JSettlers.jar, JsettlersServer.jar and the
"lib" directory.  (If you have downloaded jsettlers-1.1.xx-full.tar.gz,
look in the src/target directory for these files.)

If you have downloaded jsettlers-1.1.xx-full.jar or jsettlers-1.1.x-server.jar
instead of the full tar.gz, use that filename on the command lines shown below.

SERVER STARTUP:

Start the server with the following command
(server requires Java 1.4 or higher):

  java -jar JSettlersServer.jar -Djsettlers.startrobots=7 8880 20 socuser socpass

Those parameters are : number of bots; TCP port number; max clients; db user; db password.
You can also run with no parameters; the server will run on the default port of 8880 with no bots.

If MySQL or another database is not installed and running (See "Database Setup"),
you will see a warning with the appropriate explanation:

  Warning: failed to initialize database: ....

The database is not required: Without it, the server will function normally except
that user accounts cannot be maintained.  If you do use the database, you can give
users a nickname and password to use when they log in and play.
People without accounts can still connect, by leaving the password field blank,
as long as they aren't using a nickname which has a password in the database.

If you would like robots to automatically start when your server starts,
add the "startrobots" property to your jsettlers java command line, BEFORE the
port number:

  java -jar JSettlersServer.jar -Djsettlers.startrobots=6 8880 15 socuser socpass

This will start 6 robots on the server.

The started robots count against your max connections (15 in this example).
If the robots leave less than 6 player connections available, or if they take
more than half the max connections, a warning message is printed at startup.

To have all games' results stored in the database, use this option:
  -Djsettlers.db.save.games=Y

To see a list of all jsettlers options (use them with -D), run:
  java -jar JSettlersServer.jar --help
This will print all server options, and all Game Option default values.

To change a Game Option from its default, for example to activate the house rule
"Robber can't return to the desert", use the "-o" switch with the game option's name and value:
   -o RD=t
All command-line switches and options go before the port number if specified.


CLIENT CONNECT:

Now, from another command line window, start the player client with
the following command:

  java -jar JSettlers.jar localhost 8880

You can instead double-click the JAR file to launch the client, and then
click "connect to server".  Use the command line if you want to see the
message traffic and debug output.

In the player client window, enter any name in the Nickname field and
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

Now click on the "Sit Here" button and press "Start Game".  The robot
players should automatically join the game and start playing.

To play again with the same game options and players, click "Quit", then "Reset Board".
If other people are in the game, they will be asked to vote on the reset; any player can reject it.
If bots are in your game, and you want to reset with fewer or no bots, click the bot's Lock button
before clicking Quit/Reset and it won't rejoin the reset game.

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
disconnected.  (Only debug users can shut down the server.
See README.developer if you want that.)


Hosting a JSettlers server
--------------------------
  - Start MySQL or PostgreSQL server
    (the database is optional; if you want a db, file-based sqlite also works)
  - Start JSettlers Server
  - Start http server (optional)
  - Copy JSettlers.jar jar and "web/*.html" server directory (optional)
  - Copy "docs/users" to the server directory (optional)

To host a JSettlers server, start the server as described in "Setting up
and Testing". To maintain user accounts, be sure to start the database
first. (If you use a database, you can give users an account; everyone else
can still log in and play, by leaving the password field blank.)

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
${docroot}. This will allow users to use the web browser plug-in.

You may also copy the "doc/users" directory (recursively) to the same
directory as the sample .html pages to provide user documentation:
    $ jar -xf JSettlers.jar doc/users

Your web server directory structure should now contain:
  ${docroot}/index.html
  ${docroot}/*.html
  ${docroot}/JSettlers.jar
  ${docroot}/users/...

Users should now be able to visit your web site to run the client
version of JSettlers.


Database Setup
--------------

If you want to maintain user accounts, you will need to set up a MySQL, SQLite,
or PostgreSQL database. This will eliminate the "Problem connecting to database"
errors from the server. We assume you have installed it correctly.  SQLite is an
easy database choice because it's just a JAR file, not a separate large install,
if you're looking to avoid that.

You will need a JDBC driver JAR file in your classpath or the same directory as
the JSettlers JAR, see below for details.

The default name for the database is "socdata".  To use another name,
you'll need to specify it as a JDBC URL on the command line, such as:
	-Djsettlers.db.url=jdbc:mysql://localhost/socdata
or
	-Djsettlers.db.url=jdbc:postgresql://localhost/socdata
or
	-Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite
If needed you can also specify a database username and password as:
	-Djsettlers.db.user=socuser -Djsettlers.db.pass=socpass
or place them on the command line after the port number and max connections:
	$ java -jar JSettlersServer.jar -Djsettlers.db.url=jdbc:mysql://localhost/socdata -Djsettlers.db.jar=mysql-connector-java-5.1.34-bin.jar 8880 20 socuser socpass


Finding a JDBC driver JAR:

The default JDBC driver is com.mysql.jdbc.Driver.  PostgreSQL and SQLite are also
recognized.  To use PostgreSQL, use a postgresql URL like the one shown above,
or specify the driver on the SOCServer command line:
	-Djsettlers.db.driver=org.postgresql.Driver
To use SQLite, use a sqlite url like the one shown above, or specify a
sqlite driver such as:
	-Djsettlers.db.driver=org.sqlite.JDBC
If a database URL is given but SOCServer can't connect to the database, it won't continue startup.

Depending on your computer's setup, you may need to point JSettlers at the
appropriate JDBC drivers, by placing them in your java classpath.
Your database system's JDBC drivers can be downloaded at these locations:
	MySQL:   http://www.mysql.com/products/connector/
	PostgreSQL:  http://jdbc.postgresql.org/download.html
	SQLite:  https://bitbucket.org/xerial/sqlite-jdbc
	          or http://www.sqlite.org/cvstrac/wiki?p=SqliteWrappers
	  If sqlite crashes jsettlers on launch, or gives java.lang.UnsatisfiedLinkError
	  at launch but doesn't crash, add -Dsqlite.purejava=true before -jar on the
	  java command line and retry.

In some cases, adding to the classpath won't work because of JVM restrictions
about JAR files.  If you find that's the case, place the JDBC jar in the same
location as JSettlersServer.jar, and specify on the jsettlers command line:
	-Djsettlers.db.jar=sqlite-jdbc-3.7.2.jar


Database Creation:

To create the jsettlers database in mysql, execute the SQL db scripts
jsettlers-create.sql and jsettlers-tables.sql located in src/bin/sql:

$ mysql -u root -p -e "SOURCE jsettlers-create-mysql.sql"
This will connect as root, prompt for the root password, create a 'socuser' user with the password
'socpass', and create the 'socdata' database.

To build the empty tables, run:
$ mysql -u root -D socdata -p -e "SOURCE jsettlers-tables.sql"

For Postgres, create the db and tables with:
$ psql --file jsettlers-create-postgres.sql
$ psql --file jsettlers-tables.sql socdata

For sqlite, run this command in the target directory (jar filename may vary):
$ java -jar JSettlersServer.jar -Djsettlers.db.jar=sqlite-jdbc-3.7.2.jar  -Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite  -Djsettlers.db.script.setup=../src/bin/sql/jsettlers-tables.sql

This will create jsettlers.sqlite containing the empty tables.
This script will fail if the tables already exist.


Optional: Storing Game Scores in the DB:

To automatically save all completed game results in database, use this option when
starting the JSettlers server:
	-Djsettlers.db.save.games=Y


Optional: Creating JSettlers Player Accounts in the DB:

To create player accounts, run the simple account creation client with the
following command:

  java -jar JSettlers.jar soc.client.SOCAccountClient localhost 8880

Users with accounts must type their password to log into the server to play.
People without accounts can still connect by leaving the password field blank,
as long as they aren't using a nickname which has a password in the database.

In versions before 1.1.19, anyone could create their own user accounts
(open registration). In 1.1.19 this default was changed to improve security:
An existing user must log in before creating any new account.  If you still
want to allow open registration of user accounts, include this option when
you start your server:
	-Djsettlers.accounts.open=y

When you first set up the database, there won't be any user accounts, so the
server will allow anyone to create the first account.  Please be sure to
create that first user account soon after you set up the database.

If you want to require that all players have accounts and passwords, include
this option when you start your server:
	-Djsettlers.accounts.required=y

To permit only certain users to create new accounts, instead of all users,
list them when you start your server:
	-Djsettlers.accounts.admins=bob,joe,lily
The server doesn't require or check at startup that the named accounts all
already exist, this is just a comma-separated list of names.


Development and Compiling
-------------------------

Source code for JSettlers is available via tarballs and github.
See the project website at http://nand.net/jsettlers/devel/
or http://sourceforge.net/projects/jsettlers2/
for details. Patches against the latest version may be submitted there
or at https://github.com/jdmonin/JSettlers2 .

For more information on compiling or developing JSettlers, see README.developer.

JSettlers is licensed under the GNU General Public License.  Each source file
lists contributors by year.  A copyright year range (for example, 2007-2011)
means the file was contributed to by that person in each year of that range.
See individual source files for the GPL version and other details.

The hex and port images were created by Jeremy Monin, and are licensed
Creative Commons Attribution Share Alike (cc-by-sa 3.0 US) or Creative
Commons Attribution (CC-BY 3.0 US); see each image's gif comments for details.
