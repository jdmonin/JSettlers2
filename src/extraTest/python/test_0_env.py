#!/usr/bin/env python

# Example test script to be called from python unittest; check env has a CLASSPATH
# This file Copyright (C) 2019-2020 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

from __future__ import print_function

import os, sys, unittest

class test_0_env(unittest.TestCase):

    def test_env(self):
        print("test_0_env.py running; pwd is " + os.getcwd())

        if "CLASSPATH" in os.environ:
            print("CLASSPATH is " + os.environ["CLASSPATH"])
        else:
            print("** no CLASSPATH exported")
        self.assertTrue("CLASSPATH" in os.environ)

if __name__ == '__main__':
    unittest.main()

