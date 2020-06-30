#!/usr/bin/env python

# Quick-and-dirty tool to shift a set of 0xRRCC board coordinates by a small amount.
# Tested in python 2.7.5 and 3.5.2
# This file Copyright (C) 2020 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

from __future__ import print_function
import re, sys

USAGE=""" Quick-and-dirty tool to shift a set of 0xRRCC board coordinates by a small amount.
Run as:
./coords-shift.py row-offset col-offset

example offsets: 2 -1

Paste any text containing coordinates into the terminal, ending with ^D (Linux/MacOS) or ^Z (Windows).
Example text:
        // clockwise from northwest
        0x0104, 0x0106, 0x0108, 0x0309, 0x050A,
        0x0709, 0x0908, 0x0906, 0x0904, 0x0703

The program will then output what you pasted, changing any hex 0xRRCC by the offset amount.
If an offset sends RR or CC below 0 or above 0xFF, a warning comment is printed on the line above that output.
"""

if len(sys.argv) != 3:
  print(USAGE)
  sys.exit(1)

offset_r = int(sys.argv[1])
offset_c = int(sys.argv[2])

line_warnings = []

def match_apply_offset(matchobj):
  """apply (offset_r, offset_c) to this 0xRRCC string; add to line_warnings if < 0 or > 0xFF after offset"""
  match = matchobj.group()
  (r,c) = (int(match[2:4], 16), int(match[4:6], 16))
  r += offset_r
  c += offset_c
  ret = '0x{:02X}{:02X}'.format(r, c)
  if r < 0 or c < 0 or r > 0xFF or c > 0xFF:
    line_warnings.append(match + " -> " + ret)
  return ret

# main:

print("Enter text containing coordinates to offset; end with EOF character:")
all_input = sys.stdin.readlines()

all_out = []

for iline in all_input:
  del line_warnings[:]   # list.clear() not added until python 3.3
  out_line = re.sub(r'0x[0-9a-f]{4}\b', match_apply_offset, iline.rstrip(), flags=re.I)
  if len(line_warnings):
    all_out.append("// *** Out of range after offset: " + ', '.join(line_warnings))
  all_out.append(out_line)

print("")
print("")
for iline in all_out:
  print(iline)
print("")


