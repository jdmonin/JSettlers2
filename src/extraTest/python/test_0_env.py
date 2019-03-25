#!/usr/bin/env python

# Example test script to be called from python unittest; warn if python3, check env has a CLASSPATH
# This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

from __future__ import print_function

import os, sys, unittest

class test_0_env(unittest.TestCase):

    def test_env(self):
        print("test_0_env.py running; pwd is " + os.getcwd())

        if sys.version_info[0] > 2:
            print("Warning: python3 not supported; some tests might not be tested under 3 or unicode-ready",
                file=sys.stderr)

        if "CLASSPATH" in os.environ:
            print("CLASSPATH is " + os.environ["CLASSPATH"])
        else:
            print("** no CLASSPATH exported")
        self.assertTrue("CLASSPATH" in os.environ)

if __name__ == '__main__':
    unittest.main()

