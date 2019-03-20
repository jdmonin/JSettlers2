#!/usr/bin/env python

# simple test-case to make sure it's being discovered
# This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

import unittest

class TestGame0(unittest.TestCase):

  def test_trivial(self):
    self.assertTrue(1 == 1)

if __name__ == '__main__':
    unittest.main()
