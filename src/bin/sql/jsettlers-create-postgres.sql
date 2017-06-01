-- DB Create script for jsettlers in postgresql.
-- Run as 'postgres' or another admin user.
-- If you're using the pgAdmin GUI, you can create the socdata db with the
--   Create Database dialog, then bring up the SQL window (Query Tool) and
--   paste the rest of this file into there.
-- Run jsettlers-tables.sql as socuser right after this script.
-- See bottom of file for copyright and license information (GPLv3).

CREATE DATABASE socdata
  WITH ENCODING='UTF8'
       CONNECTION LIMIT=-1;

CREATE ROLE socuser LOGIN PASSWORD 'socpass'
   VALID UNTIL 'infinity';

-- To make our tables owned by the new 'socuser' user when they are created by
-- the jsettlers-tables.sql script, that script should be run as socuser.
-- To connect as socuser, you may also need to add lines like this to pg_hba.conf in your postgres data directory:
-- # TYPE  DATABASE    USER        CIDR-ADDRESS          METHOD
-- host    all         all         127.0.0.1/32          md5

-- Tested with postgres 8.4 and 9.5 on centos 6


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2014,2016-2017 Jeremy D Monin (jeremy@nand.net)
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
