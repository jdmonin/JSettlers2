#!/usr/bin/env python

# JSettlers functional testing: Server startup params
# Covers command-line params and jsserver.properties files
# See bottom of file for copyright and license information (GPLv3+).

# File/directory assumptions at runtime (mostly tested in env_ok()):
# - This script lives in test/bin/  [not tested]
# - Properties files can be created and deleted in test/tmp/  [tests dir existence only]
# - Server JAR has been built already, to ../../target/JSettlersServer.jar
# - java command is on the PATH
# Since this is a testing script, most error conditions will throw an exception
# instead of being caught (for example, os.chdir failure).

# Basic functions used per test: _run_and_get_outputs, print_result


from __future__ import print_function  # Python 2.6+ required

import os, re, subprocess, sys
from threading import Thread

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
        print_err("Must build server JAR first: missing " + REL_PATH_JS_SERVER_JAR)

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
        print_err(str(e))  # "OSError: [Errno 2] No such file or directory"

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
        timeout (int): Maximum time in seconds for the program to run, or 0 for no limit.
            Note: Because java normally behaves well but also takes a bit of time to halt,
            this timeout sends SIGTERM to allow cleanup, not SIGKILL for a hard kill.
            It's thus possible that a process won't actually stop.

    Returns:
        list of [int,str,str]: Exit code, stdout, stderr.
        stdout and stderr contents will have universal newlines.
        If the timeout is reached, returns None for exit code.
        If a signal terminated the process, exit code is negative signal number.

    Raises:
        OSError: If cmd cannot be found or executed.
    """
    class _Res_Obj(object):
        def __init__(self):
            self.stdout = ""
            self.stderr = ""
            self.exit_code = None
            self.proc = None

    def _thread_subproc(args, results_obj):
        """Subprocess thread to capture output with timeout before py2.7;
        refactored from http://www.ostricher.com/2015/01/python-subprocess-with-timeout/
        """
        proc = subprocess.Popen(
            args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            bufsize=-1, universal_newlines=True)
        results_obj.proc = proc
        results_obj.stdout, results_obj.stderr = proc.communicate()
            # communicate() waits for end-of-file and proc termination
            # unless the calling thread kills us
        results_obj.exit_code = proc.returncode

    # prep args[]
    if args and len(args):
        args.insert(0, cmd)
    else:
        args = [cmd, ]

    res_obj = _Res_Obj()
    if timeout:
        proc_thread = Thread(target=_thread_subproc, args=(args, res_obj))
        proc_thread.start()
        proc_thread.join(timeout)
        if proc_thread.is_alive():
            # Process still running - kill it (timeout)
            try:
                res_obj.proc.terminate()  # SIGTERM, not SIGKILL (-9)
                    # TODO consider .kill() if still running several seconds after .terminate()
            except OSError:
                # The process finished between the `is_alive()` and `kill()`
                pass
            # OK, the process was definitely killed
    else:
        # no timeout required: run it in our own thread
        _thread_subproc(args, res_obj)

    return(res_obj.exit_code, res_obj.stdout, res_obj.stderr)

def print_result(desc, res):
    """Helper method: Print test description, prefixed by 'ok' or 'FAIL' depending on bool res."""
    if (res):
        pfx = "ok: "
    else:
        pfx = "FAIL: "

    print(pfx + str(desc))

def test_run_and_get_outputs():
    """Basic tests for _run_and_get_outputs; expects unix-style env (runs sleep, false, etc)"""
    # basic test is covered in env_ok(): "java -version"

    (ec, __, __) = _run_and_get_outputs("sleep", ["1"])  # no timeout
    print_result("Test 1: sleep 1: ec == " + str(ec), (ec == 0))

    (ec, __, __) = _run_and_get_outputs("sleep", ["5"], timeout=3)
    print_result("Test 2: sleep 5 with timeout 3: ec == " + str(ec), (ec is None))

    (ec, __, __) = _run_and_get_outputs("false")
    print_result("Test 3: false: ec == " + str(ec), (ec != 0))

    got_err = False
    try:
        (ec, __, __) = _run_and_get_outputs("/prog_does_not_Exist")
    except OSError:
        got_err = True
    print_result("Test 4: Program does not exist", got_err)

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
    if (os.name == 'posix'):
        test_run_and_get_outputs()  # only if on unix: runs sleep, false commands
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
