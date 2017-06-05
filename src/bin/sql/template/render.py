#!/usr/bin/env python

# render.py - Simple template renderer for SQL DML/DDL to specific DBMS types.
#   usage: render.py [-i infile] [-o outfile] -d mysql|postgres|sqlite
#   returns: 0 on success, 1 if error reading infile or writing outfile, 2 if problems with command line
#   assumes utf-8 encoding for infile, outfile
#
# This file is part of the JSettlers project.
#
# This file Copyright (C) 2017 Jeremy D Monin <jeremy@nand.net>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see http://www.gnu.org/licenses/ .

import codecs, getopt, re, sys

dbtype = None  # mysql|postgres|sqlite; see parse_cmdline()
infile = None
outfile = None

DB_TOKENS = {
    'mysql': {
        'now': 'now()',
        'TIMESTAMP': 'TIMESTAMP',  # stored in table data as unix epoch seconds
        'set_session_tz_utc': "SET TIME_ZONE='+0:00';  -- UTC not always set up in mysql as a TZ name"
        },
    'postgres': {
        'now': 'now()',
        'TIMESTAMP': 'TIMESTAMP WITHOUT TIME ZONE',  # stored in table data as UTC
        'set_session_tz_utc': "SET TIME ZONE 'UTC';"
        },
    'sqlite': {
        'now': "strftime('%s', 'now')",
        'TIMESTAMP': 'TIMESTAMP',
        'set_session_tz_utc': "-- reminder: sqlite has no session timezone setting, only the server process's TZ"
        }
}

def print_usage():
    sys.stderr.write("usage: render.py [-i infile] [-o outfile] -d mysql|postgres|sqlite\n");
    sys.stderr.write("default infile and outfile are the program's input and output.\n");
    sys.stderr.write("returns: 0 on success, 1 if error reading infile or writing outfile, 2 if problems with command line\n");
    sys.stderr.write("token format: {{now}}, {{TIMESTAMP}}, etc\n");

def parse_cmdline():
    """Parse command line. If any problems, calls sys.exit(2)."""
    global dbtype, infile, outfile

    try:
        opts, args = getopt.getopt(sys.argv[1:], "?hd:i:o:", ["help", "dbtype=", "input=", "output="])
        if (len(args)):
            raise getopt.GetoptError("Unrecognized item on command line: Use only -i, -o, -d")
    except getopt.GetoptError as ex:
        sys.stderr.write(str(ex) + "\n");
        print_usage()
        sys.exit(2)
    for opt, oVal in opts:
        if opt in ('-i', '--input'):
            infile = oVal
        elif opt in ('-o', '--output'):
            outfile = oVal
        elif opt in ('-d', '--dbtype'):
            if oVal in ('mysql', 'postgres', 'sqlite'):
                dbtype = oVal
            else:
                sys.stderr.write('dbtype ' + oVal + ' not recognized, only: mysql postgres sqlite\n');
                sys.exit(2);
        elif opt in ('-h', '-?', '--help'):
            print_usage()
            sys.exit(0)
    if dbtype is None:
        print_usage()
        sys.exit(2)

def render(in_str):
    """Given an input string which may contain template tokens, return a rendered output string. If an unknown {{token}} is found, raises KeyError."""
    tokens = DB_TOKENS[dbtype]
    for tok in tokens:
        in_str = in_str.replace("{{" + tok + "}}", tokens[tok])
    if "{{" in in_str:
        s = re.search("{{.+?}}", in_str, re.DOTALL)
        if s:
            raise KeyError("unknown template token " + s.group(0))
    return in_str

parse_cmdline();  # exits if problems found
try:
    if infile is None or infile == '-':
        file_in = sys.stdin
    else:
        file_in = codecs.open(infile, 'r', encoding='utf8')
    with file_in:
        in_str = file_in.read()

    out_str = render(in_str)

    if outfile is None or outfile == '-':
        file_out = sys.stdout
    else:
        file_out = codecs.open(outfile, 'w', encoding='utf8')
    with file_out:
        file_out.write(out_str)
except BaseException as e:
    if infile is None:
        infile = '(stdin)'
    if outfile is None:
        outfile = '(stdout)'
    sys.stderr.write("Error processing " + infile + " to " + outfile + ": " + str(e) + "\n")
    sys.exit(1)

sys.exit(0)

