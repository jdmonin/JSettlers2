#!/usr/bin/env python

# render.py - Simple template renderer for SQL DML/DDL to specific DBMS types.
#   Usage: render.py [-i infile] [-o outfile] [-c comparefile] -d mysql|postgres|sqlite
#   Returns: 0 on success, 1 if error reading/writing or failed comparison, 2 if problems with command line
#   Assumes utf-8 encoding for infile, outfile, comparefile
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
compfile = None  # for comparison mode, against rendered template

DB_TOKENS = {
    'mysql': {
        'now': 'now()',
        'TIMESTAMP': 'TIMESTAMP',  # stored in table data as unix epoch seconds
        'TIMESTAMP_NULL': 'TIMESTAMP NULL DEFAULT null',
            # 'NULL default null' needed to deactivate mysql's default settings for timestamp columns
        'set_session_tz_utc': "SET TIME_ZONE='+0:00';  -- UTC not always set up in mysql as a TZ name"
        },
    'postgres': {
        'now': 'now()',
        'TIMESTAMP': 'TIMESTAMP WITHOUT TIME ZONE',  # stored in table data as UTC
        'TIMESTAMP_NULL': 'TIMESTAMP WITHOUT TIME ZONE',
        'set_session_tz_utc': "SET TIME ZONE 'UTC';"
        },
    'sqlite': {
        'now': "strftime('%s000', 'now')",  # +000 for millis, not epoch seconds
        'TIMESTAMP': 'TIMESTAMP',  # zentus-sqlite stores java.sql.Timestamp in table data as epoch milliseconds
        'TIMESTAMP_NULL': 'TIMESTAMP',
        'set_session_tz_utc': "-- reminder: sqlite has no session timezone setting, only the server process's TZ"
        }
}

def print_usage():
    sys.stderr.write("Usage: render.py [-i infile] [-o outfile] [-c comparefile] -d mysql|postgres|sqlite\n")
    sys.stderr.write("Output Mode renders infile's template to outfile for a given dbtype.\n")
    sys.stderr.write("Comparison Mode checks if a previously rendered file is up to date with the current template.\n")
    sys.stderr.write("Default infile and outfile are the program's input and output streams.\n")
    sys.stderr.write("Returns: 0 on success, 1 if error reading infile or writing outfile or if comparefile differs, 2 if problems with command line\n")
    sys.stderr.write("Token format: {{now}}, {{TIMESTAMP}}, etc\n")

def parse_cmdline():
    """Parse command line. If any problems, calls sys.exit(2)."""
    global dbtype, infile, outfile, compfile

    try:
        opts, args = getopt.getopt(sys.argv[1:], "?hd:i:o:c:", ["help", "dbtype=", "input=", "output=", "compare="])
        if (len(args)):
            raise getopt.GetoptError("Unrecognized item on command line: Use only -d, -i, -o, -c")
    except getopt.GetoptError as ex:
        sys.stderr.write(str(ex) + "\n")
        print_usage()
        sys.exit(2)
    all_ok = True
    for opt, oVal in opts:
        if opt in ('-i', '--input'):
            infile = oVal
        elif opt in ('-o', '--output'):
            outfile = oVal
        elif opt in ('-c', '--compare'):
            compfile = oVal
            if oVal == '-':
                sys.stderr.write('cannot use -c with stdin ("-")\n')
                all_ok = False
        elif opt in ('-d', '--dbtype'):
            if oVal in ('mysql', 'postgres', 'sqlite'):
                dbtype = oVal
            else:
                sys.stderr.write('dbtype ' + oVal + ' not recognized, only: mysql postgres sqlite\n')
                all_ok = False
        elif opt in ('-h', '-?', '--help'):
            print_usage()
            sys.exit(0)
    if dbtype is None:
        all_ok = False
    if (compfile is not None) and (outfile is not None):
        all_ok = False
        sys.stderr.write('Cannot use both -o and -c\n')
    if not all_ok:
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

# main:

parse_cmdline()  # exits if problems found

sys_exit = 0  # to set sys.exit's value within try-except
try:
    if infile is None or infile == '-':
        file_in = sys.stdin
        infile = '(stdin)'  # for possible syserr.write below
    else:
        file_in = codecs.open(infile, 'r', encoding='utf8')
    with file_in:
        in_str = file_in.read()

    out_str = render(in_str)

    if compfile is not None:
        # comparison mode
        with codecs.open(compfile, 'r', encoding='utf8') as file_comp:
            comp_str = file_comp.read()
        if out_str != comp_str:
            sys.stderr.write(compfile + " contents differ from " + infile + " for dbtype " + dbtype + "\n")
            sys_exit = 1
    else:
        # output mode
        if outfile is None or outfile == '-':
            file_out = sys.stdout
        else:
            file_out = codecs.open(outfile, 'w', encoding='utf8')
        with file_out:
            file_out.write(out_str)
except BaseException as e:
    if compfile is None:
        if outfile is None or outfile == '-':
            outfile = '(stdout)'
        sys.stderr.write("Error rendering " + infile + " to " + outfile + ": " + str(e) + "\n")
    else:
        sys.stderr.write("Error comparing " + infile + " to " + compfile + ": " + str(e) + "\n")
    sys_exit = 1

sys.exit(sys_exit)

