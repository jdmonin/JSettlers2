-- DB Security grants script for jsettlers in postgresql.
-- Run this script in socdata as 'postgres' or another admin user.
-- See bottom of file for copyright and license information (GPLv3).

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO socuser;

-- Tested with postgres 8.4 and 9.5 on centos 6


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2016-2017 Jeremy D Monin (jeremy@nand.net)
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
