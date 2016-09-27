#!/usr/bin/env python

# JSettlers functional testing: Server startup params
# Covers command-line params and jsserver.properties files
# See bottom of file for copyright and license information (GPLv3+).

# File/directory assumptions at runtime:
# - This script lives in test/bin/
# - Properties files can be created and deleted in test/tmp/
# - Server JAR has been built already, to ../../target/JSettlersServer.jar
# Since this is a testing script, most error conditions will throw an exception
# instead of being caught (for example, os.chdir failure).


from __future__ import print_function  # Python 2.6+ required

import os, re, subprocess, sys

REL_PATH_JS_SERVER_JAR = "../../target/JSettlersServer.jar"
REL_PATH_TEMPDIR = "../tmp"
FNAME_JSSERVER_PROPS = "jsserver.properties"

def print_err(*args, **kwargs):
    """Print the arguments to stderr instead of stdout."""
    print(*args, file=sys.stderr, **kwargs)

def env_ok():
    """Check environment. Return true if okay, false if problems."""
    all_ok = True

    # paths and files
    if not os.path.isdir(REL_PATH_TEMPDIR):
        all_ok = False
        print_err("Missing required directory " + REL_PATH_TEMPDIR)
    rel_path_jsserver_props = os.path.join(REL_PATH_TEMPDIR, FNAME_JSSERVER_PROPS)
    if os.path.exists(rel_path_jsserver_props) and not os.path.isfile(rel_path_jsserver_props):
        all_ok = False
        print_err(rel_path_jsserver_props + " exists but is not a normal file: Remove it")
    if not os.path.isfile(REL_PATH_JS_SERVER_JAR):
        all_ok = False
        print_err("Must build server JAR first; missing " + REL_PATH_JS_SERVER_JAR)

    # test java binary execution: java -version
    # (no need to parse version# in this test script)
    try:
        (ec, stdout, stderr) = _run_and_get_outputs("java", ["-version"])
        if ec != 0 or not re.search("java version", str(stdout)+" "+str(stderr), re.I):
            all_ok = False
            if ec != 0:
                print_err("Failed to run: java -version")
            else:
                print_err("java -version returned 0, but output didn't include the string: java version")
                print_err("  Output was: " + repr(stdout))
                print_err("  Stderr was: " + repr(stderr))
    except OSError as e:
        all_ok = False
        print_err("Failed to run: java -version")
        print_err(str(e))

    return all_ok

def setup():
    """Set up for testing: Change to temp dir, delete jsserver.properties if exists there."""
    os.chdir(REL_PATH_TEMPDIR)
    if os.path.exists(FNAME_JSSERVER_PROPS):
        os.remove(FNAME_JSSERVER_PROPS)

def _run_and_get_outputs(cmd, args=[], timeout=0):
    """Run a command, capture its stdout and stderr, and return exit code and those.

    Args:
        cmd (str): Binary or script to run
        args (list of str): Command-line arguments, if any
        timeout (int): Maximum time for the program to run, or 0 for no limit

    Returns:
        list of [int,str,str]: Exit code, stdout, stderr.
        stdout and stderr contents will have universal newlines.
        If the timeout is reached, returns None for exit code.
        If a signal terminated the process, exit code is negative signal number.

    Raises:
        OSError: If cmd cannot be found or executed.
    """
    if args and len(args):
        args.insert(0, cmd)
    else:
        args = [cmd, ]

    proc = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        bufsize=-1, universal_newlines=True)
    stdout, stderr = proc.communicate()  # waits for end-of-file and proc termination
        # TODO timeout
    return(proc.returncode, stdout, stderr)

def arg_test(should_startup, cmdline_params="", propsfile_contents=None, expected_output_incl=None):
    """Run a single test of JSettlersServer command-line/properties-file arguments.

    Args:
        should_startup (bool): True if server should start up and run with these params,
            False if these params should cause startup to fail and to return nonzero
        cmdline_params (str): Parameters for command line; defaults to empty string
        propsfile_contents (str): Contents to write to jsserver.properties,
            or None to run the test without a jsserver.properties file
        expected_output_incl (str): String to look for in server output,
            or None to not look in output for any particular string

    Returns:
    	bool: True if test results matched should_startup (and expected_output_incl if given),
            False otherwise.
    """
    pass
    # TODO write or delete props file, launch JSettlersServer, check results, return

def all_tests():
    """Call each defined test."""
    pass
    # TODO calls to arg_test

def cleanup():
    """Clean up after all tests: Delete tmp/jsserver.properties"""
    if os.path.exists(FNAME_JSSERVER_PROPS):
        os.remove(FNAME_JSSERVER_PROPS)

def main():
    """Main function: Check environment, set up, run tests, clean up."""
    if not env_ok():
        print_err("")
        print_err("*** Exiting due to missing required conditions. ***")
        sys.exit(1)  # <--- Early exit ---
    setup()
    all_tests()
    cleanup()

main()


# This file is part of the JSettlers project.
#
# This file Copyright (C) 2016 Jeremy D Monin <jeremy@nand.net>
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
