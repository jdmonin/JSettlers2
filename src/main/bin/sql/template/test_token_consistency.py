#!/usr/bin/env python

# test_token_consistency.py: Check that DDL SQL token names are consistent among DB types,
#   token values are consistent between render.py and java SOCDBHelper.upgradeSchema.
# Lives in same place as render.py, for easier import.
# Called from build.gradle with 1 arg: SOCDBHelper.java with full path
#
# This file is part of the JSettlers project.
#
# This file Copyright (C) 2019-2020 Jeremy D Monin <jeremy@nand.net>
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

import os, re, sys
import render  # current dir is checked for render.py before module path

progname = sys.argv[0]

def exit_error(msg):
  print(progname + ": " + msg)
  sys.exit(1)

# Methods used by tests:

def check_py_token_dbs_same_names():
  """Compare render.py DB_TOKENS key name sets among db types"""
  all_same = True
  token_name_set = None
  token_name_set_src = ''
  for dbtype in render.DB_TOKENS.keys():
    if dbtype in render.DBTYPES_FALLTHROUGH_ONLY:
      tokens = render.DB_TOKENS[dbtype].keys()
      if (len(tokens) != 1) or ('fallthrough' not in tokens):
        all_same = False
        print(progname + ": render.py DB_TOKEN expected only 'fallthrough' for dbtype " + dbtype)
    elif token_name_set is None:
      token_name_set = frozenset(render.DB_TOKENS[dbtype].keys())
      token_name_set_src = dbtype
    else:
      diffr = (token_name_set ^ (frozenset(render.DB_TOKENS[dbtype].keys())))
      if len(diffr):
        all_same = False
        print(progname + ": render.py DB_TOKEN sets differ: " + token_name_set_src
          + " vs dbtype " + dbtype + ": " + ", ".join(diffr))

  return all_same


# Tests:

java_all_ok = True  # for use by check_java_token_values_vs_py and its subfunction print_err

def check_java_token_values_vs_py(dbh_java_fullpath):
  """Compare render.py DB_TOKENS values against those in SOCDBHelper.upgradeSchema"""

  global java_all_ok
  java_all_ok = True
  line_num = 0
  state = ""   # parser 'state machine' shorthand for next part of the comparison area

  def print_err(msg):
    global java_all_ok
    print(progname + ".check_java_token_values_vs_py: " + dbh_java_fullpath
      + " line " + str(line_num)
      + ": Parse error within COMPARISON AREA, see py source; state=" + state + ": " + msg)
    java_all_ok = False

  try:
    token_names = None
    token_dbtype_vals = {}   # key = dbtype or 'default', val = dict with tokennames & values
    with open(dbh_java_fullpath) as f:
       # Read lines until we see "TOKEN DECLARATION LIST".
       # At that point parse the next line for token list.
       # Then, read lines until we see "BEGIN COMPARISON AREA".
       # At that point read and "parse"; ignore comment-only lines.
       # When we see "END COMPARISON AREA" (hopefully at expected time), stop reading.
       f_line = ""
       saw_decl_line = False
       saw_begin_line = False
       saw_all_expected = False
       curr_case_dbtype = None  # while parsing switch cases; 'default' can be a value here
       dbtypes_fallthrough_to_next = []  # while parsing switch cases:
         # "// fallthrough" db types to note when reaching next non-fallthrough
         # (not otherwise used yet: if 2 DB types share all tokens, they can share the same generated template)

       while java_all_ok and (f_line is not None):
         f_line = f.readline()
         if f_line is None:
           break
         f_line = f_line.strip()
         line_num += 1
         if not len(f_line):
           continue
         if not saw_begin_line:
           if state == 'decl':
             if f_line.startswith("//"):
               continue
             # assumes 2 or more tokens are declared, all on same line
             if f_line.startswith("private static String "):
               m = re.search(r"String\s+(\w+(,\s*\w+)+)\s*;", f_line)
               if m:
                 token_names = set([tokname.strip() for tokname in m.group(1).split(',')])
                 state = ''  # will read until BEGIN COMPARISON AREA
               else:
                 print_err("failed regex match: private static String ...")
             else:
               print_err("expected: private static String")
           elif f_line == "// TOKEN DECLARATION LIST -- test_token_consistency.py":
             saw_decl_line = True
             state = 'decl'
           elif f_line == "// BEGIN COMPARISON AREA -- test_token_consistency.py":
             saw_begin_line = True
             state = 'switch'
             if not saw_decl_line:
               print(progname + ': Missing "TOKEN DECLARATION LIST" before "BEGIN COMPARISON AREA" (line '
                 + str(line_num) + ') in ' + dbh_java_fullpath)
               java_all_ok = False
         elif f_line == "// END COMPARISON AREA -- test_token_consistency.py":
            if not saw_all_expected:
              print(progname + ': "END COMPARISON AREA" too early (line '
                + str(line_num) + ' state ' + state + ') in ' + dbh_java_fullpath)
              java_all_ok = False
            else:
              break    # <--- Normal read-loop termination ---
         else:
           if f_line.startswith("//") and (f_line != '// fallthrough'):
             continue
           if state == 'switch':
             if re.search(r"^switch\w*\(dbType\)$", f_line):
               state = '{'
             else:
               print_err("failed regex match")
           elif state == '{':
             if f_line == '{':
               state = 'case'   # expects case:, default:, or '}'
           elif state == 'case':
             if f_line == '}':
               state = 'end'
               saw_all_expected = True
             elif f_line == 'default:':
               state = 'caseline'
               curr_case_dbtype = 'default'
               token_dbtype_vals[curr_case_dbtype] = {}
             else:
               m = re.search(r"^case\s+DBTYPE_(\w+)\s*:", f_line)
               if m:
                 state = 'caseline'
                 curr_case_dbtype = m.group(1).lower()
                 if curr_case_dbtype == 'postgresql':
                   curr_case_dbtype = 'postgres'
                 token_dbtype_vals[curr_case_dbtype] = {}
               else:
                 print_err("failed regex match: case DBTYPE_...")
           elif state == 'caseline':
             if f_line == 'break;':  # done parsing this case
               if len(dbtypes_fallthrough_to_next):
                 for fall_dbtype in dbtypes_fallthrough_to_next:
                   token_dbtype_vals[fall_dbtype]['fallthrough'] = curr_case_dbtype
                 dbtypes_fallthrough_to_next = []
               state = 'case'
             elif f_line == '// fallthrough':
               dbtypes_fallthrough_to_next.append(curr_case_dbtype)
               state = 'case'
             else:
               m = re.search(r'^(\w+)\s*=\s*"([^"]*)";\s*$', f_line)
               if m:
                 token_dbtype_vals[curr_case_dbtype][m.group(1)] = m.group(2)
               else:
                 print_err("failed regex match: var assign | break | // fallthrough")
           elif state == 'end':
             print_err("expected: END COMPARISON AREA")
    if not saw_begin_line:
      print(progname + ': Missing "BEGIN COMPARISON AREA" in ' + dbh_java_fullpath)
      java_all_ok = False

    if not java_all_ok:
      return False   # parse error(s)

    # Sanity-check dbtypes found in java
    if 'default' in token_dbtype_vals:
      if len(token_dbtype_vals) < 3:
        print(progname + ': switch(dbType) should have at least 2 dbTypes + default: ' + dbh_java_fullpath
          + " near line " + str(line_num))
        java_all_ok = False
    else:
      print(progname + ': switch(dbType) missing default case: ' + dbh_java_fullpath
        + " near line " + str(line_num))
      java_all_ok = False

    # Check if all dbtypes (including default) have the same set of token_names
    for dbtype in token_dbtype_vals.keys():
      if dbtype in render.DBTYPES_FALLTHROUGH_ONLY:
        tokens = token_dbtype_vals[dbtype].keys()
        if (len(tokens) != 1) or ('fallthrough' not in tokens):
          java_all_ok = False
          print(progname + ": SOCDBHelper.upgradeSchema token sets: Expected fallthrough for dbtype " + dbtype)
        continue
      diffr = (token_names ^ (frozenset(token_dbtype_vals[dbtype].keys())))
      if len(diffr):
        java_all_ok = False
        print(progname + ": SOCDBHelper.upgradeSchema token sets differ: String declaration vs dbtype "
          + dbtype + ": " + ", ".join(diffr))

    # Check that dbtypes here (besides default) are same as render.DB_TOKENS
    dbtypes_set = set(token_dbtype_vals.keys())
    if 'default' in dbtypes_set:  # should be there, prevent error if not; presence is sanity-checked in above code
      dbtypes_set.remove('default')
    diffr = (dbtypes_set ^ (frozenset(render.DB_TOKENS.keys())))
    if len(diffr):
      java_all_ok = False
      print(progname + ": SOCDBHelper.upgradeSchema db types differ vs render.DB_TOKENS: "
        + ", ".join(diffr))

    # For java token names, check token values vs render.DB_TOKENS for non-default dbtypes
    if java_all_ok:
      for dbtype in dbtypes_set:
        if dbtype in render.DBTYPES_FALLTHROUGH_ONLY:
          tok_list = ['fallthrough']
        else:
          tok_list = token_names
        for token_name in tok_list:
          if render.DB_TOKENS[dbtype][token_name] != token_dbtype_vals[dbtype][token_name]:
            if java_all_ok:
              print(progname + ": SOCDBHelper.upgradeSchema token value differs from render.DB_TOKENS:")
              java_all_ok = False
            print("- DBTYPE_" + dbtype.upper() + ": token " + token_name)

    return java_all_ok

  except IOError as e:
    print(progname + ": Error reading " + dbh_java_fullpath + ": " + str(e))
    return False


# main:

# Check conditions:

if len(sys.argv) < 2:
  exit_error("Missing required parameter")

java_src_fname_path = sys.argv[1]
if not java_src_fname_path.lower().endswith('.java'):
  exit_error("Filename must end with .java: " + java_src_fname_path)
  sys.exit(1)

if not os.path.exists(java_src_fname_path):
  exit_error("File not found: " + java_src_fname_path)
  sys.exit(1)

# Run actual tests:

all_ok = check_py_token_dbs_same_names() and check_java_token_values_vs_py(java_src_fname_path)

if all_ok:
  sys.exit(0)
else:
  sys.exit(1)



