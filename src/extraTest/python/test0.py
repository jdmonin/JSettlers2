#!/usr/bin/env python

# Example test script to be called from gradle, does nothing yet
# This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

import os

print("python running; pwd is " + os.getcwd())

if "CLASSPATH" in os.environ:
    print("CLASSPATH is " + os.environ["CLASSPATH"])
else:
    print("** no CLASSPATH exported")
