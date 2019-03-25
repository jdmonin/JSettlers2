#!/usr/bin/env python

# render.py - Simple template renderer for SQL DML/DDL to specific DBMS types.
#   Usage: render.py [-i infile] [-o outfile] [-c comparefile] -d dbtype[,dbtype,...]
#     -d recognized types are: mysql,postgres,sqlite
#     -c or -o filename can contain %s placeholder for dbtype string
#   Returns: 0 on success, 1 if error reading/writing or failed comparison, 2 if problems with command line
#   Assumes utf-8 encoding for infile, outfile, comparefile
#
# Typical usage if you see the message "Must regenerate SQL script(s) from templates using render.py":
#   cd src/main/bin/sql/template
#   ./render.py -i jsettlers-tables-tmpl.sql -d mysql,sqlite,postgres -o ../jsettlers-tables-%s.sql
#   git status
#
# This file is part of the JSettlers project.
#
# This file Copyright (C) 2017,2019 Jeremy D Monin <jeremy@nand.net>
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

known_dbtypes = ('mysql', 'postgres', 'sqlite')
# from parse_cmdline():
dbtypes = None  # a list, from known_dbtypes
infile = None
outfile = None   # may contain %s for dbtype placeholder
compfile = None  # for comparison mode, against rendered template; may contain %s for dbtype placeholder

TOKENS = {}  # updated in setup_tokens() to include DB_TOKENS[dbtype] and tokens based on command-line params

sys_exit = 0  # for sys.exit's value if any render_one call fails

# contains all dbtypes in known_dbtypes; each type must have same token keynames
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
    usage = [
        "Usage: render.py [-i infile] [-o outfile] [-c comparefile] -d dbtype[,dbtype,...]",
        "  -d recognized types are: mysql,postgres,sqlite",
        "  -c or -o filename can contain %s placeholder for dbtype string",
        "Output Mode renders infile's template to outfile for a given dbtype.",
        "Comparison Mode checks if a previously rendered file is up to date with the current template.",
        "Default infile and outfile are the program's input and output streams.",
        "Returns: 0 on success, 1 if error reading infile or writing outfile or if comparefile differs, 2 if problems with command line",
        "Token format: {{now}}, {{TIMESTAMP}}, etc"
    ]
    sys.stderr.write("\n".join(usage) + "\n")

def parse_cmdline():
    """Parse command line. If any problems, calls sys.exit(2)."""
    global dbtypes, infile, outfile, compfile

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
            if '%' in oVal:
                s = re.search(r'%[^s]', oVal)
                if s:
                    sys.stderr.write("output filename: unknown token " + s.group(0) + '\n')
                    all_ok = False
        elif opt in ('-c', '--compare'):
            compfile = oVal
            if oVal == '-':
                sys.stderr.write('cannot use -c with stdin ("-")\n')
                all_ok = False
            elif '%' in oVal:
                s = re.search(r'%[^s]', oVal)
                if s:
                    sys.stderr.write("comparison filename: unknown token " + s.group(0) + '\n')
                    all_ok = False
        elif opt in ('-d', '--dbtype'):
            ovList = oVal.split(',')
            for d in ovList:
                if len(d):
                    if not (d in known_dbtypes):
                        sys.stderr.write('dbtype ' + d + ' not recognized, only: ' + ' '.join(known_dbtypes) + '\n')
                        all_ok = False
                else:
                    sys.stderr.write('missing dbtype or extra comma\n')
                    all_ok = False
            if all_ok:
                dbtypes = ovList
        elif opt in ('-h', '-?', '--help'):
            print_usage()
            sys.exit(0)
    if dbtypes is None:
        all_ok = False
    if (compfile is not None) and (outfile is not None):
        all_ok = False
        sys.stderr.write('Cannot use both -o and -c\n')
    if not all_ok:
        sys.stderr.write('\n')
        print_usage()
        sys.exit(2)

def setup_tokens(dbtype, infile_name):
    """Set up TOKENS from dynamic tokens and DB_TOKENS[dbtype]. Can call multiple times."""
    if infile_name == '-':
        infile_name = '(standard input)'
    TOKENS['render_src'] = infile_name
    TOKENS.update(DB_TOKENS[dbtype])

def render(in_str):
    """Given an input string which may contain template TOKENS, return a rendered output string. If an unknown {{token}} is found, raises KeyError."""
    for tok in TOKENS:
        in_str = in_str.replace("{{" + tok + "}}", TOKENS[tok])
    if "{{" in in_str:
        s = re.search("{{.+?}}", in_str, re.DOTALL)
        if s:
            raise KeyError("unknown template token " + s.group(0))
    return in_str

def render_one(dbtype, infile, outfile, compfile):
    """Render or compare files; pass in either outfile or compfile, which can include token %s to be replaced by dbtype"""
    global sys_exit

    if outfile and ('%s' in outfile):
        outfile = outfile.replace('%s', dbtype)
    if compfile and ('%s' in compfile):
        compfile = compfile.replace('%s', dbtype)

    setup_tokens(dbtype, infile)

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


# main:

parse_cmdline()  # exits if problems found
for d in dbtypes:
    render_one(d, infile, outfile, compfile)  # sets sys_exit if problems found
if sys_exit == 1:
    sys.stderr.write("Must regenerate SQL script(s) from templates using render.py\n")
sys.exit(sys_exit)

