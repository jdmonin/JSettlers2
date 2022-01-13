# Java Settlers - Optional Database
Setup and info about using the optional database for JSettlers user accounts and game results


## Introduction

The JSettlers server can optionally use a database to store player account
information and/or game stats. These features can be individually turned on
in the server config. A client java app to create user accounts is
also provided.

## Contents

-  Database Setup (Installing a JSettlers DB)
-  JSettlers Features which use the Database
   - Game Stats and Scores
   - Player Accounts
-  Security, Admin Users, Admin Commands
-  Upgrading from an earlier version of JSettlers
-  Settings Table and Checking Info about the DB


## Database Setup (Installing a JSettlers DB)

If you want to maintain user accounts or save scores of all completed games,
you will need to set up a MySQL/MariaDB, SQLite, or PostgreSQL database. This will
eliminate the "No user database available" console message seen when starting
the server.

This section first describes setting up the database and the JSettlers server's
connection to it, and then how to turn on optional features for Game Scores
or User Accounts.

For these instructions we'll assume you already installed the PostgreSQL, MariaDB, or
MySQL software, or will download a SQLite JAR to avoid database server setup.
JSettlers is tested with sqlite 3.27.2.1, mariadb 10.4, mysql 5.5, and
postgresql 8.4, 9.5, 11.6, 12.1.

You will need a JDBC driver JAR file in your classpath or the same directory as
the JSettlers JAR; see below for details. Besides PostgreSQL, MySQL, MariaDB,
or SQLite, any JDBC database can be used, including Oracle or MS SQL Server;
however only that list of db types are tested in depth with JSettlers.

The default type and name for the database is MySQL and "socdata". To use
another db type or another name, you'll need to specify it as a JDBC URL on
the command line, such as:  
`-Djsettlers.db.url=jdbc:mariadb://localhost/socdata`  
or  
`-Djsettlers.db.url=jdbc:mysql://localhost/socdata`  
or  
`-Djsettlers.db.url=jdbc:postgresql://localhost/socdata`  
or  
`-Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite`

If needed you can also specify a database username and password as:  
`-Djsettlers.db.user=socuser -Djsettlers.db.pass=socpass`  

or place them on the command line after the port number and max connections:

	$ java -jar JSettlersServer.jar -Djsettlers.db.url=jdbc:mysql://localhost/socdata -Djsettlers.db.jar=mysql-connector-java-5.1.34-bin.jar 8880 20 socuser socpass

All of these options can instead be placed in a `jsserver.properties` settings file.
For more details see the main Readme file's **jsserver.properties** section.


### Finding a JDBC driver JAR:

The default JDBC driver is com.mysql.jdbc.Driver. MariaDB, PostgreSQL, and SQLite are also
recognized. To use MariaDB or PostgreSQL, use a db URL like the ones shown above,
or specify the driver on the SOCServer command line:

	-Djsettlers.db.driver=org.postgresql.Driver

To use SQLite, use a sqlite url like the one shown above, or specify a
sqlite driver such as:

	-Djsettlers.db.driver=org.sqlite.JDBC

If a database URL is given but SOCServer can't connect to the database,
it won't continue startup.

Depending on your computer's setup, you may need to point JSettlers at the
appropriate JDBC drivers, by placing them in your java classpath.
Your database system's JDBC drivers can be downloaded at these locations:

- MariaDB: https://mariadb.org/download/ -> Connector/J
- MySQL:   https://www.mysql.com/products/connector/ -> Connector/J
- PostgreSQL:  https://jdbc.postgresql.org/download.html
- SQLite:  https://github.com/xerial/sqlite-jdbc/releases -> assets -> sqlite-jdbc-3.xx.y.jar

In some cases, adding to the classpath won't work because of JVM restrictions
about JAR files.  If you find that's the case, place the JDBC jar in the same
location as JSettlersServer.jar, and specify on the jsettlers command line:

	-Djsettlers.db.jar=sqlite-jdbc-3.xx.y.jar

(sqlite jar filename may vary, update the parameter to match it).

#### If SQLite gives "Operation not permitted" error at startup

Recent sqlite-jdbc versions extract and use a native shared library.
On Linux and possibly other OSes, this can trigger a security feature if the library is
extracted to default directory `/tmp` and that directory's mount point has the `noexec` flag:

    Failed to load native library:sqlite-3.xx.y-...-libsqlitejdbc.so. osinfo: Linux/x86_64
    java.lang.UnsatisfiedLinkError: /tmp/sqlite-3.xx.y-...-libsqlitejdbc.so: /tmp/sqlite-3.xx.y-...-libsqlitejdbc.so: failed to map segment from shared object: Operation not permitted
    Warning: No user database available: Unable to initialize user database
            java.lang.UnsatisfiedLinkError: org.sqlite.core.NativeDB._open_utf8([BI)V

If sqlite gives you that "operation not permitted" error:

- Choose a directory in a filesystem which isn't mounted `noexec`
  - A user's home directory may satisfy this
  - To check mount flags, use the command `mount -v`
- Make a directory within that one, for example:  
  `mkdir -p /home/jsuser/jsettlers/sqlite-tmp`
- Do whichever of these 2 options is easier for you:
  - Add to your `jsserver.properties` file:  
    `org.sqlite.tmpdir=/home/jsuser/jsettlers/sqlite-tmp`
  - When starting the server, give sqlite-jdbc that directory name before the `-jar` parameter:  
    `java -Dorg.sqlite.tmpdir=/home/jsuser/jsettlers/sqlite-tmp -jar JSettlersServer-...`

### If your database server isn't a type listed above

Although only MariaDB, MySQL, PostgreSQL, and SQLite are tested,
JSettlers may work with other database software. Create the database
and its tables and indexes, then test for needed functionality by starting
the JSettlers server once with the `jsettlers.db` parameters described above
plus this at the end of its command line:  
`-Djsettlers.test.db=Y`

You should be OK to use your DB if output ends with:  
`* All required DB tests passed.`

If output does not include `User database initialized`, check your `jsettlers.db`
parameters (driver, jar, username, password, url).

If output does include `User database initialized` but not all tests pass, check the
output for details. You may or may not be able to correct the problem(s). If you have
corrected problems by changing code in `SOCDBHelper.java`, and all tests pass, then
please contact us on github, giving us the full output from `-Djsettlers.test.db=Y`
including the line which starts with `DB testing note:`.


### Database Creation

To create the jsettlers database and its db user (`'socuser'`) and security,
execute the SQL db scripts located in `src/main/bin/sql/`
(included in `jsettlers-2.x.xx-full.tar.gz`): Change to that directory
and follow the instructions here for your database type. Afterwards,
see above for instructions on starting the JSettlers server and connecting
to the database.

If you downloaded the JAR and not the full tar.gz, you can get the SQL scripts
from https://github.com/jdmonin/JSettlers2/tree/main/src/main/bin/sql .
To get each script needed for your DB type: Click the SQL file to view it;
click Raw; save to the folder containing your JSettlers JAR.

#### For mysql or mariadb:

Run these commands, which will ask for the mariadb/mysql root password:

    $ mysql -u root -p -e "SOURCE jsettlers-create-mysql.sql"
    $ mysql -u root -D socdata -p -e "SOURCE jsettlers-tables-mysql.sql"

If the scripts run without any errors, they will produce no output.
To validate, you can list tables with this command:

    $ mysql -u socuser -D socdata -p -e "show tables"
	Enter password: socpass
	+-------------------+
	| Tables_in_socdata |
	+-------------------+
	| db_version        |
	| games             |
	| logins            |
	| robotparams       |
	| settings          |
	| users             |
	+-------------------+

If mariadb/mysql gives the error: `Unknown character set: 'utf8mb4'`
you will need to make a small change to jsettlers-create-mysql.sql
and re-run the commands; see comments at the top of that script.

#### For Postgresql:

Run these commands as the postgres system user:

    $ psql --file jsettlers-create-postgres.sql
    $ psql -d socdata --file jsettlers-sec-postgres.sql
    $ psql -d socdata -U socuser -h 127.0.0.1 --file jsettlers-tables-postgres.sql
    Password for user socuser: socpass

If the scripts run without any errors, they will produce very terse output
such as `CREATE DATABASE`, `CREATE TABLE`, and
`NOTICE: CREATE TABLE / PRIMARY KEY will create implicit index`.
You can validate by listing the newly created tables with this command:

    $ psql -d socdata -c '\dt'
	            List of relations
	 Schema |    Name     | Type  |  Owner
	--------+-------------+-------+----------
	 public | db_version  | table | socuser
	 public | games       | table | socuser
	 public | logins      | table | socuser
	 public | robotparams | table | socuser
	 public | settings    | table | socuser
	 public | users       | table | socuser

When you start your JSettlers server, remember to specify the postgres DB using:

    -Djsettlers.db.url=jdbc:postgresql://localhost/socdata

You may also need to specify a `jsettlers.db.jar` prop value as noted in the
"JDBC driver" section.

#### For sqlite:

Copy `jsettlers-tables-sqlite.sql` to the same directory as `JSettlersServer.jar`
and `sqlite-jdbc.jar` and run this command (sqlite jar filename may
vary, update the jsettlers.db.jar parameter to match it):

    $ java -jar JSettlersServer.jar -Djsettlers.db.jar=sqlite-jdbc.jar  -Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite  -Djsettlers.db.script.setup=jsettlers-tables-sqlite.sql

You should see this message:

	DB setup script was successful. Exiting now.

This will create a `jsettlers.sqlite` file containing the empty tables.

This script will fail if the file and tables already exist.

Later when you start your JSettlers server, remember to specify the sqlite DB
using the same `-Djsettlers.db.url` and `-Djsettlers.db.jar` values. Do not
specify `jsettlers.db.script.setup`, which is used only during setup.

#### JSettlers Server startup after DB Creation:

Every time you start your JSettlers server, remember to use the DB parameters/properties
detailed in the DB Creation section. These can either be given on the command line or
in a `jsserver.properties` file, whichever is easier for you.

When you start JSettlersServer and connect to the new database, you should see:

	User database initialized.

You may also see a warning that some tuning settings are missing:

	* Warning: Missing DB setting for BCRYPT.WORK_FACTOR, using 13
	To save to the settings table, run once with utility property -Djsettlers.db.settings=write

To do so, start the server one time with that property. For more information
see section "Settings Table and Checking Info about the DB" below. For security
you should also read section "Password Encryption (BCrypt)", which includes
timing tests to find the right Work Factor for your server.


## JSettlers Features which use the Database

### Storing Game Stats and Scores in the DB (optional)

Game scores and stats can optionally be saved for reports or community-building.

- For players whose account/nickname is in the database (optional),
  their total wins and losses are counted in the `users` table
- You can save all completed game results and stats in the database:
  Winner name, per-player scores, game options, duration.
  This isn't active by default, and must be turned on in the server config:
  Either use this option when starting the JSettlers server:

        -Djsettlers.db.save.games=Y

  Or, in your server's jsserver.properties file, add the line:

        jsettlers.db.save.games=Y

  Game stats and scores are kept in the `games2` and `games2_players` tables.
  (Or if DB hasn't been upgraded to schema v2000, `games`.)
  Server v2.4.00 and newer will sort game option names alphabetically as a canonical form;
  game results saved by earlier versions have unsorted game options.

### Creating JSettlers Player Accounts in the DB (optional)

Users with accounts must type their password to log into the server to play.
People without accounts can still connect by leaving the password field blank,
as long as they aren't using a nickname which has a password in the database.

To create player accounts, for security you must set the account admins list
property (`jsettlers.accounts.admins`) unless your server is in "open
registration" mode where anyone can create accounts. Set the property in your
`jsserver.properties` file or the server startup command line. For more details
see section "Security, Admin Users, Admin Commands" below.

Once your server is running with the `jsettlers.accounts.admins` property,
run the simple account creation client with the following command:

	java -cp JSettlers.jar soc.client.SOCAccountClient yourserver.example.com 8880

In versions before 1.1.19, anyone could create their own user accounts
(open registration). In 1.1.19 this default was changed to improve security:
An existing user must log in before creating any new account.  If you still
want to allow open registration of user accounts, include this option when
you start your server:

	-Djsettlers.accounts.open=y

When you first set up the database, there won't be any user accounts, so the
server will allow anyone to create the first account.  Please be sure to
create that first user account soon after you set up the database. The first
account created must be on the account admins list.

Player accounts are stored in the `users` table. Their total wins and losses
are also tracked there.

#### Password Encryption (BCrypt)

Player account passwords are encrypted using BCrypt. For tuning, BCrypt includes
a "Work Factor" parameter; the hashing algorithm runs for 2 ^ WorkFactor rounds,
so a larger Work Factor is tougher to brute-force attack but runs slower on
your server.

The default Work Factor for JSettlers is 12. To use a different value set the
`jsettlers.db.bcrypt.work_factor` property on the server command line or in
`jsserver.properties`. Each account's Work Factor is stored with its encrypted
password; changing the Work Factor property affects future passwords but not
already-encrypted ones.

To test the speed of different work factors on your server, run JSettlersServer
once with `-Djsettlers.db.bcrypt.work_factor=test`, which will try a range of
work factors and print the timed results. Then, (using 14 as an example) run
the server once with `-Djsettlers.db.bcrypt.work_factor=14 -Djsettlers.db.settings=write` .

If you're upgrading from a version before **1.2.00**, you will need to upgrade your
database schema in order to use BCrypt. Test bcrypt speed and set that
`work_factor` property before starting the upgrade process.


## Security, Admin Users, Admin Commands

If you want to require that all players have accounts and passwords, include
this option when you start your server:

	-Djsettlers.accounts.required=y

To specify the Account Admin Users who can create new accounts
and run privileged commands, list them when you start your server:

	-Djsettlers.accounts.admins=bob,joe,lily

Admin Users must log in with an account and password stored in the database.
Their accounts are created as if they were a regular player; see section
"Creating JSettlers Player Accounts". Being listed in `jsettlers.accounts.admins`
is what gives them admin privileges.

Note:
The server doesn't require or check at startup that the named accounts all
already exist; the User Account Admins list is only a comma-separated list
of names, to simplify initial setup.

### Admin Commands

Only the Admin Users on that list can create accounts and run privileged commands,
such as listing all users in a game with

	*WHO* gamename
	
or listing all users connected to the server with

	*WHO* *
	
along with commands unrelated to users, like:

	*GC*
	*RESETBOT* botname
	
For a list of all available commands, type

	*HELP*
	
into the chat window of any game while connected as an admin user.


### Password Reset

In case any account's password is lost, there's a rudimentary **password-reset** feature:  
Run JSettlersServer with the usual DB parameters and add: `--pw-reset username`  
You will be prompted for `username`'s new password. This command can be run while
the server is up. It will reset the password and exit, and won't start a second
JSettlersServer.


## Upgrading from an earlier version of JSettlers

Use the docs to plan before starting your upgrade:

- Read this entire section of this file
  - A few steps and commands mention the `socuser` DB username. If you've set up
    the database with a different username, make a note to use that instead
- Read the "Upgrading from an earlier version" section of [Readme.md](../Readme.md)
- Check [Versions.md](Versions.md) for new features, bug fixes, and config changes
  since the old version
  - Most versions won't have any DB schema changes or require a DB upgrade.
    In [Versions.md](Versions.md) look for the word "**schema**" in the list of changes.

### Checklist before starting the upgrade:
- Make a DB backup or export its contents. Technical problems during the
  upgrade are very unlikely; having the backup gives you more flexibility
  if a problem comes up.
- If you're upgrading from JSettlers **1.1.20** or earlier:
  - To create more users, you must have an account admin list configured
    (`jsettlers.accounts.admins` property, in the `jsserver.properties`
    file or command line) unless your server is in "open registration" mode.
  - Test bcrypt speed, to decide on and set the work_factor property,
    before starting the upgrade process. For details search for
    "Password Encryption (BCrypt)" in this file.
- If you're upgrading from JSettlers **1.1.18** or earlier:  
  For security reasons, newer versions default to disable user account
  self-registration. If you still want to use that option, search this
  doc for "open registration".
- If using **Oracle**: Upgrading to the latest DB schema (v2.0.00) isn't yet implemented:
  The upgrade and latest schema need some DB features which haven't been
  written for that dialect of SQL or tested on that unsupported DB type.

### When starting up the server using the new version:

- If the new version's server startup messages include this line:

        * Database schema upgrade is recommended: To upgrade, use -Djsettlers.db.upgrade_schema=Y command line flag.

  Then do so now if convenient. The schema upgrade is not required immediately,
  to give you more flexibility, but may be required for some new features.
  When you run JSettlersServer with that upgrade flag plus your usual parameters
  it will check necessary conditions; upgrade the database; report success or
  any problems found; then exit immediately. You should see:

        DB schema upgrade was successful. Exiting now.

#### Postgresql note:
  If you've been using jsettlers **1.1.20** or older with postgresql,
  the upgrade may tell you to change your tables' owner to `socuser` first:

        * To begin schema upgrade, please fix and rerun:
        Must change table owner to socuser from postgres

  JSettlers comes with the script `jsettlers-upg-prep-postgres-owner.sql`
  to do so, in the same directory as the scripts mentioned in the
  Database Creation section of this doc. As the postgres system user, run:

        psql -d socdata --file jsettlers-upg-prep-postgres-owner.sql -v to=socuser
	
  Then, run the schema upgrade command.

### Completing the upgrade:
- The upgrade is technically complete once you've seen this output:  
  `DB schema upgrade was successful. Exiting now.`
- If you see this output:  
  `some upgrade tasks will complete in the background during normal server operation.`  
  There are some table conversions or other tasks remaining, which the JSettlers server
  will automatically take care of in small batches while it's running as usual
- Make a new DB backup or export its contents
- The upgrade_schema command-line flag is not used during day-to-day operation of the server
- Note: The schema version and upgrade history is kept in the db_version table


## Settings Table and Checking Info about the DB

In schema version **1.1.20** and newer, the database has a `settings` table
of DB tuning setting names and values. Currently the only setting name is
`BCRYPT.WORK_FACTOR`.

If `BCRYPT.WORK_FACTOR` is missing from `settings` at server startup, the BCrypt
work factor will be chosen by testing the speed of a range of factors, and you
will see this warning:

	* Warning: Missing DB setting for BCRYPT.WORK_FACTOR, using 13
	To save to the settings table, run once with utility property -Djsettlers.db.settings=write

To view info about the database, list current settings, and check that they match
what's saved in the `settings` table, Account Admins can type this admin command
into the chat window of any JSettlers game:

	*DBSETTINGS*

This shows details about the database type, server version, schema version,
and all current DB settings:

	Database settings:
	> Schema version: 1200 (is latest version)
	> Password encoding scheme: BCrypt
	> BCrypt work factor: 13
	> DB server version: 3.15.1
	> JDBC driver: org.sqlite.JDBC v3.15 (jdbc v2.1)

If there is a mismatch or missing setting, you will see a message like these:

	> BCrypt work factor: 13 (Missing from DB settings table)
	> BCrypt work factor: 11 (Mismatch: DB settings table has 13)

(A mismatch could occur from old command-line parameters or
`jsserver.properties` contents.)
