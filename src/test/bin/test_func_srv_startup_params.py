#!/usr/bin/env python

# JSettlers functional testing: Server startup params
# Covers command-line params and jsserver.properties files
# See bottom of file for copyright and license information (GPLv3+).

# File/directory assumptions at runtime (mostly tested in env_ok()):
# - Python interpreter is python2, not python3  [env_ok() prints warning if 3]
# - This script lives in test/bin/  [not tested]
# - Properties files can be created and deleted in test/tmp/  [tests dir existence only]
# - Server JAR has been built already, to ../../target/JSettlersServer.jar
# - java command is on the PATH
# Since this is a testing script, most error conditions will throw an exception
# instead of being caught (for example, os.chdir failure).

# See bottom of file for main() function.
# Overall test function: all_tests, which calls arg_test or
# gameopt_tests_cmdline_propsfile to run individual tests.
# Basic functions used per test: _run_and_get_outputs, print_result


from __future__ import print_function  # Python 2.6 or higher is required

import os, re, socket, subprocess, sys, time
from threading import Thread

FNAME_JSSERVER_JAR = "JSettlersServer.jar"
REL_PATH_JS_SERVER_JAR = "../../target/" + FNAME_JSSERVER_JAR
REL_PATH_TEMPDIR = "../tmp"
FNAME_JSSERVER_PROPS = "jsserver.properties"
MAX_TIMEOUT_SEC = 20

tests_failed_count = 0
"""int: global counter for test failures; increment in arg_test or
    other unit-test functions.
    """

def print_err(*args, **kwargs):
    """Print the arguments to stderr instead of stdout."""
    print(*args, file=sys.stderr, **kwargs)

def env_ok():
    """Check environment. Return true if okay, false if problems."""
    all_ok = True

    # python version
    if sys.version_info[0] > 2:
        print_err("Warning: python3 not supported; may give errors writing jsserver.properties (unicode vs string bytes)")

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

    # shouldn't have any server running already on default tcp port
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1)
        s.connect(('127.0.0.1', 8880))
        s.close()
        all_ok = False
        print_err("Test environment cannot already have a server running on tcp port 8880")
    except IOError:
        pass
    except Exception as e:
        all_ok = False
        print_err("Failed to check tcp port 8880")
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
        timeout (int): Maximum time in seconds for the program to run, or 0 for no limit.
            Note: Because java normally behaves well but also takes a bit of time to halt,
            this timeout sends SIGTERM to allow cleanup, not SIGKILL for a hard kill.
            It's thus possible that a process won't actually stop.
            Note: All or some output may be lost (buffering) when timeout kills the process.

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
            rc = None   # time.sleep may overwrite proc.returncode/res_obj.exit_code
            time.sleep(1)  # wait a bit for OS cleanup after kill,
                # such as freeing the TCP port so the next test can bind to it.
        else:
            rc = res_obj.exit_code
    else:
        # no timeout required: run it in our own thread
        _thread_subproc(args, res_obj)
        rc = res_obj.exit_code

    return(rc, res_obj.stdout, res_obj.stderr)

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
    Assumes already running in test/tmp/ and can rewrite or delete jsserver.properties if needed.

    Args:
        should_startup (bool): True if server should start up and run with these params,
            False if these params should cause startup to fail or to return nonzero
        cmdline_params (str): Parameters for command line; defaults to empty string
        propsfile_contents (list of str): Contents to write to jsserver.properties,
            or None to run the test without a jsserver.properties file.
            Each list item is written as a line to the file.
            Use ascii characters only, to avoid problems with java's expected iso-8859-1
            vs utf-8 or other encodings.
        expected_output_incl (str): String to search for in server output, case-sensitive,
            or None to not look in output for any particular string
            Note: All or some output may be lost (buffering) when timeout kills the process.
            So if should_startup==True, it's unreliable to also use expected_output_incl.

    Returns:
    	bool: True if test results matched should_startup (and expected_output_incl if given),
            False otherwise.

    Raises:
        ValueError: If called with should_startup==True and expected_output_incl not None.
            Because of buffering vs subprocess timeouts, there's no guarantee that output is
            entirely captured when the timeout kills the subprocess; searching for expected
            output may fail although the program generated that output.
    """
    global tests_failed_count

    if should_startup and (expected_output_incl is not None):
        raise ValueError("Can't use should_startup with expected_output_incl")

    args = ["-jar", REL_PATH_JS_SERVER_JAR]
    if len(cmdline_params):
        args.extend(cmdline_params.split())

    if propsfile_contents is not None:
        prn_pfile = "; with jsserver.properties"
        with open(FNAME_JSSERVER_PROPS, "w") as pf:
            for line in propsfile_contents:
                pf.write(line)
                pf.write("\n")
    else:
        prn_pfile = "; no jsserver.properties"
        try:
            os.remove(FNAME_JSSERVER_PROPS)
        except OSError:
            pass  # didn't exist

    print("Test: java " + " ".join(args) + prn_pfile)
    exit_code, stdout, stderr = _run_and_get_outputs("java", args, timeout=MAX_TIMEOUT_SEC)

    ret = True
    if exit_code is None:
        did_startup = True
        prn_startup = "(started up)"
    else:
        did_startup = False
        prn_startup = "(exited: " + str(exit_code) + ")"

    if should_startup != did_startup:
        ret = False
    if (expected_output_incl is not None) and not did_startup:
        if expected_output_incl not in (stdout + " " + stderr):
            ret = False
            prn_startup += " -- missing expected output"

    if ret:
        print(prn_startup + " -> ok")
    else:
        tests_failed_count += 1
        print(prn_startup + " -> FAIL")
        if (expected_output_incl is not None) and not did_startup:
            print("EXPECTED: " + expected_output_incl)
        print("STDOUT: " + stdout)
        print("STDERR: " + stderr)
        if propsfile_contents is not None:
            print("jsserver.properties contents:")
            for line in propsfile_contents:
                print(line)
        print("")
    return ret

def gameopt_tests_cmdline_propsfile(should_startup, opt, expected_output_incl=None):
    """Run two tests with this game option: Once on command line, once with properties file.
    Calls arg_test with -o <opt> and again with props file contents jsettlers.<opt> .

    Args:
        should_startup (bool): True if server should start up and run with these params,
            False if these params should cause startup to fail or to return nonzero
        opt (str): Game option and value, in the form "oname=val".
            Will be appended to gameopt-setting prefix in the two tests:
            "-o oname=val"; ["jsettlers.gameopt.oname=val"]
        expected_output_incl (str): String to search for in server output, case-sensitive,
            or None to not look in output for any particular string
            Note: All or some output may be lost (buffering) when timeout kills the process.
            So if should_startup==True, it's unreliable to also use expected_output_incl.

    Returns:
    	bool: True if test results matched should_startup (and expected_output_incl if given),
            False otherwise.

    Raises:
        ValueError: If called with should_startup==True and expected_output_incl not None.
    """
    # use "all" to ensure all tests run (avoid boolean short-circuit)
    return all([
        arg_test(should_startup, "-o " + opt, None, expected_output_incl),
        arg_test(should_startup, "", ["jsettlers.gameopt." + opt], expected_output_incl)
        ])

def all_tests():
    """Call each defined test.

    Returns:
        bool: True if all passed, False otherwise (see tests_failed_count).
    """

    global tests_failed_count

    # no problems, no game opts on cmdline, no props file
    arg_test(True, "", None)

    # twice on cmdline; different uppercase/lowercase
    arg_test(False, "-o NT=t -o NT=y", None, "option cannot appear twice on command line: NT")
    arg_test(False, "-o NT=t -o nt=f", None, "option cannot appear twice on command line: NT")
    arg_test(False, "-Djsettlers.gameopt.NT=t -Djsettlers.gameopt.nt=f", None, "option cannot appear twice on command line: NT")
    arg_test(False, "-o NT=t -Djsettlers.gameopt.nt=f", None, "option cannot appear twice on command line: NT")

    # missing value
    arg_test(False, "-o", None, "Missing required option name/value after -o")

    # props file with no gameopts
    arg_test(True, "", ["jsettlers.allow.debug=y"])

    # props file with gameouts with no problems
    arg_test(True, "", ["jsettlers.gameopt.NT=y", "jsettlers.gameopt.vp=t12"])

    # Run each of these tests for commandline and for properties file:

    # if multiple problems, make sure init_propsCheckGameopts reports them
    arg_test(False, "-oXYZ=t -oZZZ=t", None,
        "Unknown game option: XYZ\nUnknown game option: ZZZ")
    arg_test(False, "", ["jsettlers.gameopt.XYZ=t", "jsettlers.gameopt.ZZZ=t"],
        "Unknown game option: XYZ\nUnknown game option: ZZZ")

    # empty game option name after prefix
    arg_test(False, "-Djsettlers.gameopt.=n", None,
        "Empty game option name in property key: jsettlers.gameopt.")
    arg_test(False, "", ["jsettlers.gameopt.=n"],
        "Empty game option name in property key: jsettlers.gameopt.")

    # unknown opt name
    gameopt_tests_cmdline_propsfile(False, "un_known=y", "Unknown game option: UN_KNOWN")

    # "unknown or malformed" opt (or bad value)
    arg_test(False, "-o RD=g", None, "Unknown or malformed game option: RD")
    arg_test(False, "-o RD=yy", None, "Unknown or malformed game option: RD")
    gameopt_tests_cmdline_propsfile(False, "n7=z", "Unknown or malformed game option: N7")
    gameopt_tests_cmdline_propsfile(False, "vp=z15", "Unknown or malformed game option: VP")
    gameopt_tests_cmdline_propsfile(False, "OPTNAME_TOO_LONG=t", "Key length > 8: OPTNAME_TOO_LONG")

    # missing value for property
    arg_test(False, "-Djsettlers.xyz", None, "Missing value for property jsettlers.xyz")

    # unknown scenario name
    gameopt_tests_cmdline_propsfile(False, "SC=ZZZ", "default scenario ZZZ is unknown")
    gameopt_tests_cmdline_propsfile(False, "sc=ZZZ", "default scenario ZZZ is unknown")  # non-uppercase opt name
    arg_test(False, "-Djsettlers.gameopt.sc=ZZZ", None, "Command line default scenario ZZZ is unknown")

    return (0 == tests_failed_count)


def cleanup():
    """Clean up after all tests: Delete tmp/jsserver.properties"""
    if os.path.exists(FNAME_JSSERVER_PROPS):
        os.remove(FNAME_JSSERVER_PROPS)

def main():
    """Main function: Check environment, set up, run tests, clean up.

    Returns:
        If any tests failed, calls sys.exit(tests_failed_count).
        Otherwise, exit code is 0 (default).
    """

    global tests_failed_count

    if not env_ok():
        print_err("")
        print_err("*** Exiting due to missing required conditions. ***")
        sys.exit(1)  # <--- Early exit ---
    setup()
    if (os.name == 'posix'):
        test_run_and_get_outputs()  # only if on unix: runs sleep, false commands
    all_tests()
    cleanup()

    print("")
    if (tests_failed_count > 0):
        print("Total failure count: " + str(tests_failed_count))
        sys.exit(tests_failed_count)
    else:
        print("All tests passed.")


main()


# This file is part of the JSettlers project.
#
# This file Copyright (C) 2016-2017 Jeremy D Monin <jeremy@nand.net>
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
