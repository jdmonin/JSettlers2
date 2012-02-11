-- DB tables/indexes create script for jsettlers.  Run jsettlers-create.sql before this script.
-- Always use lowercase for table names and field names.  0-9 and underscore (_) are also safe.
-- Remember that the sql must be valid for mysql, postgresql, sqlite, and oracle.
-- For indexes, use the table name + __ + one lowercase letter.
-- See bottom of file for copyright and license information (GPLv3).

USE socdata;

CREATE TABLE users (
	nickname VARCHAR(20), host VARCHAR(50), password VARCHAR(20), email VARCHAR(50), lastlogin DATE,
	PRIMARY KEY (nickname)
);

CREATE TABLE logins (
	nickname VARCHAR(20), host VARCHAR(50), lastlogin DATE,
	PRIMARY KEY (nickname)
);

CREATE TABLE games (
	gamename VARCHAR(20),
	player1 VARCHAR(20), player2 VARCHAR(20), player3 VARCHAR(20), player4 VARCHAR(20),
	score1 SMALLINT, score2 SMALLINT, score3 SMALLINT, score4 SMALLINT,
	starttime TIMESTAMP
);

CREATE INDEX "games__n" ON games(gamename);

CREATE TABLE robotparams (
	robotname VARCHAR(20),
	maxgamelength INT, maxeta INT, etabonusfactor FLOAT, adversarialfactor FLOAT, leaderadversarialfactor FLOAT, devcardmultiplier FLOAT, threatmultiplier FLOAT,
	strategytype INT, starttime TIMESTAMP, endtime TIMESTAMP, gameswon INT, gameslost INT, tradeFlag BOOL,
	PRIMARY KEY (robotname)
);



-- This file is part of the JSettlers project.
-- 
--  This file Copyright (C) 2012 Jeremy D Monin (jdmonin@nand.net)
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
