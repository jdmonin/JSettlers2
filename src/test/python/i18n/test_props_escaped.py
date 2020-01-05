#!/usr/bin/env python

# Read properties files to make sure props with MessageFormat.format placeholder args escape certain characters.
# Tested in python 2.7.5 and 3.5.2
# This file Copyright (C) 2019 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

import codecs, os, re, unittest

def check_missing_escape(localized_str):
  """ Test localized_str contents against java MessageFormat.format format, looking for missing escape chars.
  If has any positional args ({0}, {1}, ...), then single-quotes and open curly braces ("'", "{")
  which aren't a positional arg should be preceded by a single-quote to escape them.
  Returns -1 or the position of the first quote or curly brace which looks to be missing its escape char.
   """

  # look first for {\d positional args
  if not re.search('{\d', localized_str):
    return -1

  # look for ' but not '' ; for { but not either '{ or {\d
  m = re.search("(^'[^'{])|([^']'[^'{])", localized_str)
  if m:
    pos = m.start()
    if localized_str[pos] != "'":
      pos += 1  # since start of match is before the "'"
    return pos
  m = re.search("([^']\{\D)|([^']\{)$", localized_str)
  if m:
    return m.start()

  return -1

def check_props_list_missing_escape(props_list):
  """ Test props_list's name=value elements against check_missing_escape.
  If no failures, returns empty list.
  If any failures, returns list of (prop_index+1, prop_char_pos, prop_name) tuples.
  Assumes each props_list element follows same format as lines from a java .properties file
  with no end-of-line \ continuations:
  https://docs.oracle.com/javase/6/docs/api/java/util/Properties.html#load(java.io.Reader)

  This unit test parses the lines "manually" instead of using the ConfigParser module,
  to retain line numbers and prevent any interpretation of "#" chars in values as comments.
  """
  fails = []
  re_empty_line = re.compile("^\s*$")
  re_comment_line = re.compile("^\s*#")
  re_data_line = re.compile("^\s*([^=]+?)\s*=\s*(.*?)$")

  for linenum in range(len(props_list)):
    propline = props_list[linenum]
    if re_empty_line.search(propline) or re_comment_line.search(propline):
      continue
    m = re_data_line.search(propline)
    if not m:
      fails.append( (linenum + 1, 0, "(Cannot parse this line)") )
      continue
    k, v = m.group(1), m.group(2)
    idx = check_missing_escape(v)
    if idx != -1:
      fails.append( (linenum + 1, idx, k) )

  return fails

class TestPropsEscaped(unittest.TestCase):

  def test_latin1_decoding(self):
    # basic assumptions
    ascii_list= [97, 98, 99, 100, 101]
    self.assertEqual(ascii_list, [ord(ch) for ch in 'abcde'])
    byte_arr = bytearray(ascii_list)
    self.assertEqual(ascii_list, [int(int_item) for int_item in byte_arr])

    # decode iso-8859-1 with high-bit character: example from https://en.wikipedia.org/wiki/Mojibake
    iso_8859_1_list = [110, 0xE5]  # 'n', small letter A with ring above
    utf8_list = [110, 195, 165]    # same string encoded in utf-8
    try:
      self.assertEqual(utf8_list, [int(i) for i in bytearray(bytearray([110, 0xE5]).decode('iso-8859-1').encode('utf8'))], )
    except Exception as e:
      fail('Cannot convert from iso-8859-1 to utf8, but they should be built-in codecs: ' + str(e))

  def test_check_missing_escape(self):
     self.assertEqual(-1, check_missing_escape(""))
     self.assertEqual(-1, check_missing_escape("without-specials"))
     self.assertEqual(-1, check_missing_escape("aren't any positional args"))
     self.assertEqual(-1, check_missing_escape("'"))
     self.assertEqual(-1, check_missing_escape("''"))
     self.assertEqual(-1, check_missing_escape("' "))
     self.assertEqual(-1, check_missing_escape("'x"))
     self.assertEqual(-1, check_missing_escape("'' {0}"))
     self.assertEqual(-1, check_missing_escape(" '"))
     self.assertEqual(-1, check_missing_escape("x'"))
     self.assertEqual(-1, check_missing_escape("{0} ''"))
     self.assertEqual(-1, check_missing_escape("{"))
     self.assertEqual(-1, check_missing_escape("{ "))
     self.assertEqual(-1, check_missing_escape("{a"))
     self.assertEqual(-1, check_missing_escape("{0}"))
     self.assertEqual(-1, check_missing_escape("{0,number}"))
     self.assertEqual(-1, check_missing_escape("abc {0}"))
     self.assertEqual(-1, check_missing_escape("{0}'{"))  # test vs end-of-line
     self.assertEqual(-1, check_missing_escape("not an argument: {something}"))
     self.assertEqual(-1, check_missing_escape("aren't any args: {something}"))
     self.assertEqual(-1, check_missing_escape("argument{0} but without-specials"))
     self.assertEqual(-1, check_missing_escape("argument{0} it''s escaped"))
     self.assertEqual(3, check_missing_escape("isn't escaped but has {0}"))
     self.assertEqual(-1, check_missing_escape("argument{0} has escaped '{ brace"))
     self.assertEqual(8, check_missing_escape("{1} this { is missing escape"))
     self.assertEqual(8, check_missing_escape("{2} this {also} is missing escape"))

  def test_check_props_list_missing_escape(self):
    self.assertFalse(check_props_list_missing_escape([]))  # no errors in empty file

    comment_lines = ["# this is a comment", "  # also a comment"]
    blank_lines = ["", " ", "   ", "\t"]

    li = []
    li.extend(comment_lines)
    li.extend(blank_lines)
    self.assertFalse(check_props_list_missing_escape(li))  # no errors in empty file

    ok_lines = ["k=v", " k = v", " k=v", "k = v", "k =v", "k= v"]
    li.extend(ok_lines)
    self.assertFalse(check_props_list_missing_escape(li))

    res = check_props_list_missing_escape([" missing_equals and not a comment line"])
    self.assertEqual(1, len(res))
    self.assertEqual(res[0], (1, 0, "(Cannot parse this line)"))

    badline_1 = "some.prop = isn't escaped but has {0}"  # column index 3
    badline_2 = "x.prop={1} this { is missing escape"     # column index 8
    res = check_props_list_missing_escape([badline_1, badline_2])
    self.assertEqual(2, len(res))
    self.assertEqual(res[0], (1, 3, "some.prop"))
    self.assertEqual(res[1], (2, 8, "x.prop"))

  def test_parse_all_props_files(self):
    """Gather all properties files, parse each one, print results if not as expected.
    Current directory should be src/test/python  (running under unittest discover)
    and *.properties should be under src/main/resources/**/
    """
    self.assertTrue(os.path.isdir("../../main/resources"),
      msg="Can't find ../../main/resources ; current directory is " + os.getcwd())

    all_prop_filenames = []
    for root, dirs, files in os.walk("../../main/resources"):
      for fname in files:
        if fname.lower().endswith(".properties"):
          fullpath = str(root) + os.sep + fname
          all_prop_filenames.append(fullpath)
    self.assertTrue(len(all_prop_filenames),
      msg="Can't find *.properties under ../../main/resources ; current directory is " + os.getcwd())
    all_errors = {}
    for fname in all_prop_filenames:
      with codecs.open(fname, 'r', 'iso-8859-1') as f:
        file_errors = check_props_list_missing_escape(f.readlines())
        if file_errors:
          all_errors[fname] = file_errors
    if all_errors:
      self.fail("\n\nCharacter escape problems in properties files (line, char, key):\n" + repr(all_errors) + "\n\n")

if __name__ == '__main__':
    unittest.main()
