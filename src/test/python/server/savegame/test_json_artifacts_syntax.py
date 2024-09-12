#!/usr/bin/env python

# Read savegame .game.json files to make sure they're syntactically correct
# (no trailing commas, etc).
# Tested in python 2.7.5 and 3.9.12
# This file Copyright (C) 2022 Jeremy D Monin <jeremy@nand.net>
# License: GPLv3

import json, os, sys, unittest

def try_parse_json(json_str, description):
  """ Try to parse a json string, see if can do so without errors.
  The standard json module treats trailing commas in lists as an error.
  Takes a short description to use if not OK.
  Returns None if OK, otherwise returns description and the error details.
   """

  try:
    j = json.loads(json_str)
  except ValueError as e:
    return ("Error parsing %s: %s" % (description, str(e)))

  if type(j) is dict:
    return None
  else:
    return ("Parse didn't return a dict: %s" % description)


def try_load_savegame(fname, fname_rel_path):
  """ Try to load a json file, see if can do so without errors.
  The standard json module treats trailing commas in lists as an error.
  Returns None if OK, otherwise returns fname and the error.
   """
  with open(fname_rel_path, "r") as f:
    return try_parse_json(f.read(), fname)


class TestJsonArtifactsSyntax(unittest.TestCase):

  def test_basic(self):
    # should succeed:
    tests_names_data = {
      'json': '{"modelVersion": 2400, "savedByVersion": 2400, "other_property": true}',
      'json list': '{"hexLayout": [50, 6, 65, 6] }',
      'json dict': '{"boardInfo": { "modelVersion": 2400, "savedByVersion": 2400 } }'
    }
    for t_name, t_data in tests_names_data.items():
      desc = "basic self test parsing " + t_name
      result = try_parse_json(t_data, desc)
      if result:
        sys.stderr.write(result + "\n")
        self.fail(desc)

    # should fail:
    tests_names_data = {
      'list trailing comma': '{"hexLayout": [50, 6, 65, 6, ] }',
      'dict trailing comma': '{"boardInfo": { "modelVersion": 2400, "savedByVersion": 2400, } }'
    }
    for t_name, t_data in tests_names_data.items():
      desc = "basic self test parsing " + t_name
      result = try_parse_json(t_data, desc)
      if result is None:
        self.fail(desc)

  def test_parse_all_savegame_files(self):
    """Gather all savegame files, parse each one, print results if not as expected.
    Current directory should be src/test/python  (running under unittest discover)
    and *.game.json should be under src/test/resources/resources/**/
    """
    self.assertTrue(os.path.isdir("../../test/resources/resources/savegame"),
      msg="Can't find ../../test/resources/resources/savegame ; current directory is " + os.getcwd())

    all_savegame_filenames = {}  # key = short, value = full or relative path
    for root, dirs, files in os.walk("../../test/resources/resources/savegame"):
      for fname in files:
        if fname.lower().endswith(".game.json"):
          all_savegame_filenames[fname] = os.path.join(root, fname)
    self.assertTrue(len(all_savegame_filenames),
      msg="Can't find *.game.json under ../../test/resources/resources/savegame ; current directory is " + os.getcwd())
    all_errors = {}
    for (fname, fn_path) in all_savegame_filenames.items():
      file_errors = try_load_savegame(fname, fn_path)
      if file_errors:
        all_errors[fname] = file_errors
    if all_errors:
      self.fail("\n\njson parsing problems in src/test/resources/resources/savegame files:\n" + repr(all_errors) + "\n\n")

if __name__ == '__main__':
    unittest.main()
