-- DB Create script for jsettlers in postgresql.
-- Run as 'postgres' or another admin user; the admin user will own the
--   jsettlers db and its objects. For security, the new socuser user won't
--   have the ability to create or drop db objects, only to work with data.
-- If you're using the pgAdmin GUI, you can create the socdata db with the
--   Create Database dialog, then bring up the SQL window (Query Tool) and
--   paste the rest of this file into there.
-- Run jsettlers-tables.sql right after this script.
-- Then run jsettlers-sec-postgres.sql.
-- See bottom of file for copyright and license information (GPLv3).

CREATE DATABASE socdata
  WITH ENCODING='UTF8'
       CONNECTION LIMIT=-1;

CREATE ROLE socuser LOGIN PASSWORD 'socpass'
   VALID UNTIL 'infinity';

-- our tables will be in the 'socdata' schema, but they aren't created yet.
-- so to grant access, postgres 8 requires running another script
-- (jsettlers-sec-postgres.sql) after jsettlers-tables.sql creates them.
-- ALTER DEFAULT PRIVILEGES is available in postgres 9+, but CentOS 6 comes with 8.

-- Tested with postgres 8.4 on centos 6


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2014,2016 Jeremy D Monin (jeremy@nand.net)
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
