-- DB Create script for jsettlers.  Run jsettlers-tables.sql right after this script.
-- See bottom of file for copyright and license information (GPLv3).

CREATE DATABASE socdata
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- note: utf8mb4 was added in mysql 5.5.3 (released 2010-03-24) to support
-- unicode characters outside the BMP. If your mysql is older and you can't upgrade,
-- change utf8mb4 to utf8 and utf8mb4_unicode_ci to utf8_unicode_ci and rerun the script.


GRANT ALL PRIVILEGES
  ON socdata.*
  TO 'socuser'@'localhost'
  IDENTIFIED BY 'socpass';

-- Tested with mysql 5.1 and 5.5 on centos 6, 5.5 on MacOSX 10.9


-- This file is part of the JSettlers project.
--
--  This file Copyright (C) 2012,2017 Jeremy D Monin (jeremy@nand.net)
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
