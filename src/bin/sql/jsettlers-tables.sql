-- DB tables/indexes create script for jsettlers.
-- Run jsettlers-create-mysql.sql or jsettlers-create-postgres.sql before this script.
-- Make sure socdata is the database you are connected to when running this script:
--      MySQL:    $ mysql -u root -D socdata -p -e "SOURCE jsettlers-tables.sql"
--      Postgres: $ psql --file jsettlers-tables.sql socdata
-- See bottom of file for copyright and license information (GPLv3+).
-- Always use lowercase for table names and field names.  0-9 and underscore (_) are also safe.
-- Don't create "mytable_name" if that name without underscores ("mytablename") is already a table.
-- Remember that the sql must be valid for mysql, postgresql, sqlite, and oracle.
-- For indexes, use the table name + __ + one lowercase letter.
-- For multi-line SQLs, indent so that SOCDBHelper.runSetupScript can combine them.

-- Schema upgrades: See SOCDBHelper.upgradeSchema()
--  2017-05-25 v1.2.00: Add users.nickname_lc

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

CREATE TABLE games (
	gamename VARCHAR(20) not null,
	player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20),
	score1 SMALLINT, score2 SMALLINT, score3 SMALLINT, score4 SMALLINT,
	starttime TIMESTAMP not null
	);

CREATE INDEX games__n ON games(gamename);

-- tradeFlag is always 1 or 0; using SMALLINT to be db-neutral.
CREATE TABLE robotparams (
	robotname VARCHAR(20) not null,
	maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT,
	strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag SMALLINT,
	PRIMARY KEY (robotname)
	);



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
