# ![logo](doc/graf/Logo32.png?raw=true) Java Settlers

A web-based client-server version of Settlers of Catan


## Introduction

JSettlers is a web-based version of the board game Settlers of Catan
written in Java. This client-server system supports multiple
simultaneous games between people and computer-controlled
opponents. Initially created as an AI research project.

The client may be run as a Java application, or as an applet when
accessed from a web site which also hosts a JSettlers server.

The server can optionally use a database to store player account
information and game stats (details below).  A client java app to
create user accounts is also provided.

If you're upgrading from an earlier version of JSettlers, check
[doc/Versions.md](doc/Versions.md) for new features, bug fixes, and config changes
and see **Upgrading from an earlier version** section below.

JSettlers is an open-source project licensed under the GPL. The
project is hosted at https://github.com/jdmonin/JSettlers2/ and
at http://nand.net/jsettlers/devel/ .  Questions, bugs, patches,
and pull requests can be posted at the github page.

\- The JSettlers Development Team


## Contents

-  Documentation
-  Requirements
-  Client Command Line
-  Server Setup and Testing
-  Shutting down the server
-  Installing a JSettlers Server
-  Upgrading from an earlier version
-  Security and Admin Users
-  Development and Building JSettlers


## Documentation

User documentation for game play is available as .html pages located
in the `src/site/users` directory. These can be put on a JSettlers server for
its users using the applet.

Currently, this Readme and the `doc` directory are the only technical
documentation for running the client or server, setup and other issues.
Over time, more docs will be written. If you are interested in helping
write documentation please contact the development team from our github page.

If you downloaded a JSettlers JAR file without attached documentation,
the official location of this Readme and the docs is online at
https://github.com/jdmonin/JSettlers2/blob/master/Readme.md .


## Requirements

To play JSettlers by connecting to a remote server you will need the
Java Runtime Version 6 or above. To connect as an applet, use any
browser which is Java enabled (using the browser plug-in) or just
download the JAR from http://nand.net/jsettlers/ and run it.

To Play JSettlers locally you need the Java Runtime 6 or above.
`JSettlers-full.jar` can connect directly to any server over the Internet.

To host a JSettlers server that provides a web applet for clients, you will
need an http server such as Apache's httpd, available from http://httpd.apache.org.

The JSettlers-full.jar file can also run locally as a server, without
needing a web server.  The applet is considered more convenient,
because you know everyone will have the same version.

To build JSettlers from source, you will need Java JDK 6 or newer and
gradle 4 or 5, or an IDE such as Eclipse which understands gradle's format.
See [doc/Readme.developer.md](doc/Readme.developer.md) for details.


## Client Command Line

Running the client with no parameters is the same as double-clicking it:  
`java -jar JSettlers-full.jar` will bring up a window with options to
connect to a server, practice against bots (no network needed), or start
a server for others to connect to.

To connect directly to a server, give its host and port number:  
`java -jar JSettlers-full.jar myserver.example.com 8880`

If your screen is High-DPI, JSettlers should automatically detect that
instead of running in a very small window. If detection fails for some
reason, ask for High-DPI support this way:  
`java -Djsettlers.uiScale=2 -jar JSettlers-full.jar`

Also available: `--help`, `--version`, and various debugging flags
listed in [doc/Readme.developer.md](doc/Readme.developer.md).


## Server Setup and Testing

From the command line, make sure you are in the JSettlers distribution
directory which contains both `JSettlers.jar`, `JsettlersServer.jar` and the
`lib` directory.  (If you have downloaded `jsettlers-2.x.xx-full.tar.gz`,
look in the /target directory for these files.)

If you have downloaded `jsettlers-2.x.xx-full.jar` or `jsettlers-2.x.xx-server.jar`
instead of the full tar.gz, use that filename on the command lines shown below.

### Server Startup

Start the server with the following command
(server requires Java 6 JRE or higher, or JDK 6 or higher):

    java -jar JSettlersServer.jar

This will start the server on the default port of `8880` with `7` robots.
It will try to connect to an optional mysql database named `socdata`; startup
will continue even if there is no DB or the DB connect doesn't work.

You can change those values and specify game option defaults; see details below.

If MySQL or another database is not installed and running (See "Database Setup"
in [doc/Database.md](doc/Database.md)), you will see a warning with the
appropriate explanation:

    Warning: No user database available: ....
    Users will not be authenticated.

The database is not required: Without it, the server will function normally
except that user accounts cannot be maintained.

If you do use the database, you can give users a nickname and password to use
when they log in and play.  People without accounts can still connect, by
leaving the password field blank, as long as they aren't using a nickname
which has a password in the database.  Optionally game results and stats can
also be saved in the database, see next section; those aren't saved by default.

### Parameters and game option defaults:

JSettlers options, parameters, and game option defaults can be specified on the
command line, or in a `jsserver.properties` file in the current directory when
you start the server.

Command line example:

    java -jar JSettlersServer.jar -Djsettlers.startrobots=9 8880 50

In this example the parameters are: Start 9 bots; TCP port number 8880;
max clients 50.

The started robots count against your max simultaneous connections (50 in this
example).  If the robots leave less than 6 player connections available, or if
they take more than half the max connections, a warning message is printed at
startup. To start a server with no robots (human players only), use

    -Djsettlers.startrobots=0

Any command-line switches and options go before the port number if specified
on the command line.  If the command includes -jar, switches and options go
after the jar filename.

To change a Game Option from its default, for example to activate the house rule
"Robber can't return to the desert", use the `-o` switch with the game option's
name and value, or equivalently "-Djsettlers.gameopt." + the name and value:

    -o RD=t
    -Djsettlers.gameopt.RD=t

You could also set a default game scenario this way; for example if your server
was running a tournament of Fog Islands games:

    -o SC=SC_FOG

If the scenario's game options conflict with any other game options given (VP,
etc), a warning will be printed during startup.  In general, servers shouldn't
set a default scenario; users can choose a scenario on their own if they want.

To have all completed games' results saved in the database, use this option:

    -Djsettlers.db.save.games=Y

To see a list of all jsettlers options (use them with -D), run:

    java -jar JSettlersServer.jar --help

This will print all server options, and all Game Option default values. Note the
format of those default values: Some options need both a true/false flag and a
numeric value. To change the default winning victory points to 12 for example:

    -o VP=t12

### jsserver.properties:

Instead of a long command line, any option can be added to `jsserver.properties`
which is read at startup if it exists in the current directory.  Any option
given on the command line overrides the same option in the properties file.
Comment lines start with # .  See `src/main/bin/jsserver.properties.sample` for full
descriptions of all available properties.

This example command line

  java -jar JSettlersServer.jar -Djsettlers.startrobots=9 -o RD=t 8880 50 socuser socpass

is the same as jsserver.properties with these contents:

    jsettlers.startrobots=9
    jsettlers.gameopt.RD=t
    jsettlers.port=8880
    jsettlers.connections=50
    # db user and pass are optional
    jsettlers.db.user=socuser
    jsettlers.db.pass=socpass

To determine if the server is reading the properties file, look for this text
near the start of the console output:

    Reading startup properties from jsserver.properties

To check the syntax and values of a `jsserver.properties file`, use the `-t` or
`--test-config` command line parameter for Config Validation Mode:

    java -jar JSettlersServer.jar --test-config

This will test and print all configured values and then exit with return code 0
if no problems are found, nonzero otherwise. Output will include a summary line
such as:

    * Config Validation Mode: No problems found.

### Connect a client

Now, double-click JSettlers.jar to launch the client.  If you'd
prefer to start the player client from another command line window,
use the following command:

    java -jar JSettlers.jar

Optionally you can also provide the server's name and port, to skip
the Connect To Server dialog:

    java -jar JSettlers.jar localhost 8880

When the client launches, click Connect To Server. Leave the Server
name field blank to connect to your own computer (localhost) and use
the JSettlers server you started up in the previous section.

Once you've connected, enter any name in the Nickname field and
create a new game.

Type `*STATS*` into the chat part of the game window.  You should see
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
If other people are in the game, they will be asked to vote on the reset; any player can
reject it. If bots are in your game, and you want to reset with fewer or no bots, click
the bot's Lock button before clicking Quit/Reset and it won't rejoin the reset game.

If you want other people to access your server, tell them your server address
and port number (the default is 8880).  They can run the JSettlers.jar file by
itself, and it will bring up a window to enter your server address (DNS name
or IP) and port number.  Or, they can enter the following command:

    java -jar JSettlers.jar <server_address> <port_number>

If you would like to maintain accounts for your JSettlers server, start the
database prior to starting the JSettlers Server. See the "Database Setup"
section of [doc/Database.md](doc/Database.md) for directions.

### Server shutdown

To shut down the server hit `Ctrl-C` in its console window, or connect as the
optional debug user and enter `*STOP*` in the chat area of a game window.
This will stop the server and all connected clients will be disconnected.
(See [doc/Readme.developer.md](doc/Readme.developer.md) if you want to set up
a debug user.)

### Installing a JSettlers server

#### Checklist:

- If using the optional database, start MySQL or PostgreSQL server  
  (file-based sqlite is another lightweight DB option)
- Copy and edit `jsserver.properties` (optional)
- Start JSettlers Server
- Start http server (optional)
- Copy `JSettlers.jar` client JAR and `src/site/*.html` to an http-served directory (optional)

#### Details:

If you want to maintain user accounts or save scores of all completed games,
you will need to set up a MySQL, SQLite, or PostgreSQL database. This database
is optional. If you will use a MySQL or PostgreSQL database, be sure to start
the database server software before installing JSettlers. For DB setup details
see the "Database Setup" section of [doc/Database.md](doc/Database.md)
(available online at https://github.com/jdmonin/JSettlers2/blob/master/doc/Database.md).

To install a JSettlers server, start the server as described in "Server Setup
and Testing". Remember that you can set server parameters and game option
default values with a `jsserver.properties` file: Copy the sample file
`src/main/bin/jsserver.properties.sample` to the same directory as `JSettlersServer.jar`,
rename it to `jsserver.properties`, and edit properties as needed.

Remote users can simply start their clients as described there,
and connect to your server's DNS name or IP address.

To provide a web page from which users can run the applet, you will
need to set up an http server such as Apache.  We assume you have
installed it already, and will refer to `${docroot}` as a directory
to place files to be served by your web server.

Copy `index.html` from `src/site/` to `${docroot}`.  If you're going to use an
accounts database and anyone can register their own account (this is not
the default setting), also copy `accounts.html`.

Edit the html to make sure the PORT parameter in `index.html` and `account.html`
applet tags match the port of your JSettlers server, and the text starting
"this applet connects to" has the right server name and port for users who can't
run the applet in their browser.  If you're using `account.html`, also
un-comment `index.html`'s link to `account.html`.

Next copy the `JSettlers.jar` client file to `${docroot}`. This will allow users
to use the web browser plug-in or download it to connect from their computer.
If you've downloaded it as `JSettlers-{version}-full.jar`, rename it to `JSettlers.jar`.

Your web server directory structure should now contain:

    ${docroot}/index.html
    ${docroot}/account.html (optional)
    ${docroot}/JSettlers.jar

Users should now be able to visit your web site to run the JSettlers client.

### Upgrading from an earlier version

If you're doing a new installation, not upgrading a server that's
already been running JSettlers, skip this section.

It's a simple process to upgrade to the latest version of JSettlers:

- Read [doc/Versions.md](doc/Versions.md) for new features, bug fixes, and
  config changes made from your version to the latest version.  Occasionally
  defaults change and you'll need to add a server config option to keep the
  same behavior, so read carefully.
- If you're using the optional database, backup the database and see
  the "Upgrading from an earlier version" section of [doc/Database.md](doc/Database.md)
  for parameter changes and other actions to take.
- Save a backup copy of your current JSettlers.jar and JSettlersServer.jar,
  in case you want to run the old version for any reason.
- Stop the old server
- Copy the new JSettlers.jar and JSettlersServer.jar into place
- Start the new server, including any new options you wanted from [doc/Versions.md](doc/Versions.md)
- If the new server's startup messages include a line about database schema
  upgrade, see the "Upgrading" section of [doc/Database.md](doc/Database.md).
- Test that you can connect and start games as usual, with and without bots.
  When you connect make sure the version number shown in the left side of
  the client window is the new JSettlers version.


## Development and Building JSettlers

JSettlers is an open-source project licensed under the GPL. The project
source code is hosted at https://github.com/jdmonin/JSettlers2/ and
the project website is http://nand.net/jsettlers/devel/ .  Questions,
bugs, patches, and pull requests can be posted at the github page.

For more information on building or developing JSettlers, see
[doc/Readme.developer.md](doc/Readme.developer.md). That readme also has
information about translating jsettlers to other languages; see the
"I18N" section.

JSettlers is licensed under the GNU General Public License.  Each source file
lists contributors by year.  A copyright year range (for example, 2007-2011)
means the file was contributed to by that person in each year of that range.
See individual source files for the GPL version and other details.

BCrypt.java is licensed under the "new BSD" license, and is copyright
(C) 2006 Damien Miller; see BCrypt.java for details.  jBCrypt-0.4.tar.gz
retrieved 2017-05-27 from http://www.mindrot.org/projects/jBCrypt/
and some constants, javadocs, throws declarations added by Jeremy D Monin.

org.fedorahosted.tennera.antgettext.StringUtil is licensed under the
"Lesser GPL" (LGPL) license, and is from the JBoss Ant-Gettext utilities.

The hex and port images were created by Jeremy Monin, and are licensed
Creative Commons Attribution Share Alike (cc-by-sa 3.0 US) or Creative
Commons Attribution (CC-BY 3.0 US); see each image's gif comments for details.
goldHex.gif is based on a 2010-12-21 CC-BY 2.0 image by Xuan Che, available at
http://www.flickr.com/photos/rosemania/5431942688/ , of ancient Greek coins.
doc/graf/Logo.svg is (C) 2017 Ruud Poutsma.
