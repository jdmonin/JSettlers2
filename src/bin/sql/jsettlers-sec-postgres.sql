-- DB Security grants script for jsettlers in postgresql.
-- First, run jsettlers-tables.sql.
-- Run this script as 'postgres' or another admin user.
-- See bottom of file for copyright and license information (GPLv3).

GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE users, logins, games, robotparams
  TO socuser;

-- no sequences defined yet
-- GRANT USAGE, SELECT
--  ON SEQUENCE .., ..
--  TO socuser;



-- misc notes:
-- Tested with postgres 8.4 on centos 6
-- Reminder: psql shell can list existing privs with \z [objectname]


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2016 Jeremy D Monin (jeremy@nand.net)
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
