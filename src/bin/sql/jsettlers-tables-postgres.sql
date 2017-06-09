-- DB tables/indexes create script for jsettlers.
-- Run jsettlers-create-mysql.sql or jsettlers-create-postgres.sql before this script.
-- Make sure socdata is the database you are connected to when running this script:
--      MySQL:    $ mysql -u root -D socdata -p -e "SOURCE jsettlers-tables.sql"
--      Postgres: $ psql --file jsettlers-tables.sql socdata
-- See bottom of file for copyright and license information (GPLv3+).

-- When changing the schema, update template/jsettlers-tables-tmpl.sql and not db-specific files.
-- Always use lowercase for table names and field names.  0-9 and underscore (_) are also safe.
-- Don't create "mytable_name" if that name without underscores ("mytablename") is already a table.
-- Remember that the sql must be valid for mysql, postgresql, sqlite, and oracle.
-- For indexes, use the table name + __ + one lowercase letter.
-- For multi-line SQLs, indent so that SOCDBHelper.runSetupScript can combine them.
-- Comments must begin with a space: "-- ".

-- Schema upgrades:
--   See SOCDBHelper.upgradeSchema(). DDL here must be kept in sync with what's found there.
--   2017-06-08 v1.2.00: Add db_version table; users.nickname_lc; TIMESTAMP column type now dbtype-specific;
--       games + player5, player6, score5, score6, duration_sec, winner, gameopts

-- DB Schema Version / upgrade history: Added in v1.2.00 (schema version 1200).
-- At startup, SOCDBHelper checks max(to_vers) here for this db's schema version.
-- If this table is empty, as a fallback the version will be detected based on new fields and
-- a warning message is printed. If fallback detection is incorrect, prepared statements should fail
-- and startup will be halted; the server admin will need to intervene and add that row.

CREATE TABLE db_version (
	from_vers INT not null,  -- or 0 for new install
	to_vers   INT not null,
	ddl_done  TIMESTAMP WITHOUT TIME ZONE,      -- null if upgrade's DDL changes are incomplete
	bg_tasks_done TIMESTAMP WITHOUT TIME ZONE,  -- null if upgrade is incomplete;
	           -- if upgrade requires no background tasks/conversions, same value as ddl_done
	PRIMARY KEY (to_vers)
	);
-- At DB creation, a row is added to this table to indicate current version: See bottom of this script.

-- Users:
-- When the password encoding or max length changes,
-- be sure to update SOCDBHelper.createAccount and updateUserPassword.
CREATE TABLE users (
	nickname VARCHAR(20) not null, host VARCHAR(50) not null, password VARCHAR(20) not null, email VARCHAR(50), lastlogin DATE,
	nickname_lc VARCHAR(20),
	PRIMARY KEY (nickname)
	);

CREATE UNIQUE INDEX users__l ON users(nickname_lc);

CREATE TABLE logins (
	nickname VARCHAR(20) not null, host VARCHAR(50), lastlogin DATE,
	PRIMARY KEY (nickname)
	);

-- Players and scores for completed games.
-- If database schema was upgraded from an earlier version,
-- duration_sec and winner will be null for old data rows.
CREATE TABLE games (
	gamename VARCHAR(20) not null,
	player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20),
	player4 VARCHAR(20), player5 VARCHAR(20), player6 VARCHAR(20),
	score1 SMALLINT, score2 SMALLINT, score3 SMALLINT,
	score4 SMALLINT, score5 SMALLINT, score6 SMALLINT,
	starttime TIMESTAMP WITHOUT TIME ZONE not null, duration_sec INT not null,
	winner VARCHAR(20) not null, gameopts VARCHAR(500)
	);

CREATE INDEX games__n ON games(gamename);

-- tradeFlag is always 1 or 0; using SMALLINT to be db-neutral.
CREATE TABLE robotparams (
	robotname VARCHAR(20) not null,
	maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT,
	strategytype INT, starttime TIMESTAMP WITHOUT TIME ZONE, endtime TIMESTAMP WITHOUT TIME ZONE, gameswon INT, gameslost INT, tradeFlag SMALLINT,
	PRIMARY KEY (robotname)
	);

-- Mark this newly created db's schema version:
SET TIME ZONE 'UTC';
INSERT INTO db_version(from_vers, to_vers, ddl_done, bg_tasks_done)
	VALUES(0, 1200, now(), now());


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2012,2014-2017 Jeremy D Monin (jeremy@nand.net)
--  Portions of this file Copyright (C) 2004-2005 Chadwick A McHenry (mchenryc@acm.org)
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU General Public License as published by
--  the Free Software Foundation, either version 3 of the License, or
--  (at your option) any later version.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU General Public License for more details.
--
--  You should have received a copy of the GNU General Public License
--  along with this program.  If not, see http://www.gnu.org/licenses/ .
