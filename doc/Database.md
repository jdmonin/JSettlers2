# Java Settlers - Optional Database
Setup and info about using the optional database for JSettlers user accounts and game results


## Introduction

The JSettlers server can optionally use a database to store player account
information and game stats.  A client java app to create user accounts is
also provided.

## Contents

-  Database Setup (Installing a JSettlers DB)
-  Security and Admin Users
-  Upgrading from an earlier version


## Database Setup (Installing a JSettlers DB)

If you want to maintain user accounts or save scores of all completed games,
you will need to set up a MySQL, SQLite, or PostgreSQL database. This will
eliminate the "No user database available" console message seen when starting
the server.

This section first describes setting up the database and the JSettlers server's
connection to it, and then how to turn on optional features for Game Scores
or User Accounts.

For these instructions we'll assume you already installed the PostgreSQL or
MySQL software, or will download a SQLite JAR to avoid database server setup.
JSettlers is tested with sqlite 3.15.1, mysql 5.5, and postgresql 8.4 and 9.5.

You will need a JDBC driver JAR file in your classpath or the same directory as
the JSettlers JAR, see below for details. Besides PostgreSQL, MySQL, or SQLite
any JDBC database can be used, including Oracle or MS SQL Server; however only
those three db types are tested in depth with JSettlers.

The default type and name for the database is MySQL and "socdata". To use
another db type or another name, you'll need to specify it as a JDBC URL on
the command line, such as:

	-Djsettlers.db.url=jdbc:mysql://localhost/socdata

or

	-Djsettlers.db.url=jdbc:postgresql://localhost/socdata

or

	-Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite

If needed you can also specify a database username and password as:

	-Djsettlers.db.user=socuser -Djsettlers.db.pass=socpass

or place them on the command line after the port number and max connections:

	$ java -jar JSettlersServer.jar -Djsettlers.db.url=jdbc:mysql://localhost/socdata -Djsettlers.db.jar=mysql-connector-java-5.1.34-bin.jar 8880 20 socuser socpass

### Finding a JDBC driver JAR:

The default JDBC driver is com.mysql.jdbc.Driver.  PostgreSQL and SQLite are also
recognized.  To use PostgreSQL, use a postgresql URL like the one shown above,
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

- MySQL:   http://www.mysql.com/products/connector/
- PostgreSQL:  http://jdbc.postgresql.org/download.html
- SQLite:  https://bitbucket.org/xerial/sqlite-jdbc/downloads/

In some cases, adding to the classpath won't work because of JVM restrictions
about JAR files.  If you find that's the case, place the JDBC jar in the same
location as JSettlersServer.jar, and specify on the jsettlers command line:

	-Djsettlers.db.jar=sqlite-jdbc-3.xx.y.jar

(sqlite jar filename may vary, update the parameter to match it).


### Database Creation

To create the jsettlers database and its db user `('socuser')` and security,
execute the SQL db scripts located in `src/main/bin/sql/`
(included in `jsettlers-2.x.xx-full.tar.gz`): Change to that directory
and follow the instructions here for your database type. Afterwards,
see above for instructions on starting the JSettlers server and connecting
to the database.

If you downloaded the JAR and not the full tar.gz, you can get the SQL scripts
from https://github.com/jdmonin/JSettlers2/tree/master/src/main/bin/sql .
To get each script needed for your DB type: Click the SQL file to view it;
click Raw; save to the folder containing your JSettlers JAR.

#### For mysql:

Run these commands, which will ask for the mysql root password:

    $ mysql -u root -p -e "SOURCE jsettlers-create-mysql.sql"
    $ mysql -u root -D socdata -p -e "SOURCE jsettlers-tables-mysql.sql"

If the scripts run without any errors, they will produce no output.
To validate, you can list tables with this command:

    $ mysql -u root -D socdata -p -e "show tables"
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

If mysql gives the error: `Unknown character set: 'utf8mb4'`
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

You may also need to specify a `jsettlers.db.jar` value as noted in the
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
using the same `-Djsettlers.db.url` and `-Djsettlers.db.jar` values.

### Storing Game Scores in the DB (optional)

Game scores can optionally be saved for reports or community-building. To
automatically save all completed game results in the database, use this option
when starting the JSettlers server:

	-Djsettlers.db.save.games=Y

Or, in your server's jsserver.properties file, add the line:

	jsettlers.db.save.games=Y

### Creating JSettlers Player Accounts in the DB (optional)

Users with accounts must type their password to log into the server to play.
People without accounts can still connect by leaving the password field blank,
as long as they aren't using a nickname which has a password in the database.

To create player accounts, for security you must set the account admins list
property (`jsettlers.accounts.admins`) unless your server is in "open
registration" mode where anyone can create accounts. Set the property in your
`jsserver.properties` file or the server startup command line. See below for
more details on listing admin usernames in that property.

To create player accounts, run the simple account creation client with the
following command:

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

### Password Encryption (BCrypt)

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
work factors and print the timed results.

If you're upgrading from a version before **1.2.00**, you will need to upgrade your
database schema in order to use BCrypt. Test bcrypt speed and set that
`work_factor` property before starting the upgrade process.


## Security and Admin Users

If you want to require that all players have accounts and passwords, include
this option when you start your server:

	-Djsettlers.accounts.required=y

To specify the Account Admin Users who can create new accounts, list them when you start your server:

	-Djsettlers.accounts.admins=bob,joe,lily

Only the account admins on that list can create accounts and run user-related commands,
such as listing all users in a game with

	*WHO* gamename
	
or listing all users connected to the server with

	*WHO* *
	
For a list of all available commands, type

	*HELP*
	
into the chat window of any game while connected as an admin user.

Note:
The server doesn't require or check at startup that the named accounts all
already exist; the User Account Admins list is only a comma-separated list
of names, to simplify initial setup.

In case an admin account password is lost, there's a rudimentary **password-reset** feature:  
Run JSettlersServer with the usual DB parameters and add: `--pw-reset username`  
You will be prompted for username's new password. This command can be run while
the server is up. It will reset the password and exit, and won't start a second
JSettlersServer.


## Upgrading from an earlier version

If you're upgrading from an earlier version of JSettlers, check
`doc/Versions.md` for new features, bug fixes, and config changes.
Before starting the upgrade, read this section and also the
"Upgrading from an earlier version" section of `Readme.md`.

### Before starting the upgrade:
- Make a DB backup or export its contents. JSettlers **1.2.00** is the first
  version which has schema changes, which are recommended but optional
  (see below). Technical problems during the upgrade are very unlikely;
  having the backup gives you more flexibility if a problem comes up.
- If you're upgrading from JSettlers **1.1.20** or earlier, to create more
  users you must have an account admin list configured
  (`jsettlers.accounts.admins` property, in the `jsserver.properties`
  file or command line) unless your server is in "open registration" mode.
- If you're upgrading from JSettlers **1.1.18** or earlier, for security
  reasons newer versions by default disallow user account
  self-registration. If you still want to use that option, search this
  doc for "open registration".

### When starting up the server using the new version:

- If the new server's startup messages include this line:

        * Database schema upgrade is recommended: To upgrade, use -Djsettlers.db.upgrade_schema=Y command line flag.

  Then do so now if convenient. The schema upgrade is not required immediately,
  to give you more flexibility, but may be required for some new features.
  When you run JSettlersServer with that upgrade flag plus your usual parameters
  it will check necessary conditions; upgrade the database; report success or
  any problems found; then exit immediately. You should see:

        DB schema upgrade was successful. Exiting now.

  The schema version and upgrade history is kept in the db_version table. The
  upgrade_schema flag is not used during day-to-day operation of the server.

  **Note:** If you've been using jsettlers **1.1.20** or older, test bcrypt speed and
  set the work_factor property before starting the upgrade process. For details
  search for "Password Encryption (BCrypt)" in this file.

  **Note:** If you've been using jsettlers **1.1.20** or older with postgresql,
  the upgrade may tell you to change your tables' owner to socuser first:

        * To begin schema upgrade, please fix and rerun:
        Must change table owner to socuser from postgres

  JSettlers comes with the script `jsettlers-upg-prep-postgres-owner.sql`
  to do so, in the same directory as the scripts mentioned in the
  Database Creation section of this doc. As the postgres system user, run:

        psql -d socdata --file jsettlers-upg-prep-postgres-owner.sql -v to=socuser
	
  Then, run the schema upgrade command.
