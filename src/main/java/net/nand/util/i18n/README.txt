README.txt - Notes and current status for the nand.net i18n translator's editor


This internationalization editor is used by the JSettlers project, so its "home" is here:
	download:    http://nand.net/jsettlers/devel/i18n/PTE-0.9.0.jar
	webpage:     http://nand.net/jsettlers/devel/i18n/
	bug-tracker: https://github.com/jdmonin/JSettlers2/issues
	source:      https://github.com/jdmonin/JSettlers2/tree/master/src/main/java/net/nand/util/i18n
in the net.nand.util.i18n and net.nand.util.i18n.gui packages.


Purpose and File Format
-----------------------

Java i18n localization strings are typically kept in properties files named with the
language and country/region, falling back to a base language file if the user's language,
country, or region aren't found:
	toClient_es_AR.properties
	toClient_es.properties
	toClient.properties
Each file contains key = value lines: The keys used in java code, and each key's value in the language.
Comment lines are encouraged for context and explanation.

This editor makes translation maintenance a bit easier, including side-by-side comparison.

There are other properties editors out there, I wanted to see what writing one would be like.
Thank you for trying our i18n editor.

- Jeremy D Monin <jeremy@nand.net>


Using this Editor
-----------------
Use the menu buttons to open a file as a translation "destination", and it will find that file's
parent as "source" by looking for a file with the same base name but fewer "_" suffixes.
Or, you can choose any two files as "source" and "destination".

The editor shows the "source" and "destination" files side by side by key for comparison and
translation.  "Source" is the file with a less specific locale, "destination" is more specific.

Start net.nand.util.i18n.gui.PTEMain, then browse to the files you want to edit, or choose one
"destination" file to edit, with its "source" file automatically picked based on the filename.

To create a new destination translation, click the "New Destination" menu button and browse to
the source (typically without any "_" suffix).  Enter the language code for the new translation
and click Create. The editor will open, and you can begin entering keys' translations.

This editor has unicode support and color hilighting, and will save files in the required
ISO-8859-1 encoding (with unicode escapes) automatically.

Keys can be added by right-clicking a blank line.
Ctrl-F finds text in the source and destination files.
Ctrl-S saves any unsaved changes to the source and destination.
Click the main window's Help button for more info.

When starting the editor this message is harmless, because preferences are stored per-user:
	Dec 6, 2013 3:59:16 PM java.util.prefs.WindowsPreferences <init>
	WARNING: Could not open/create prefs root node Software\JavaSoft\Prefs at root 0x80000002. Windows RegCreateKeyEx(...) returned error code 5.


Developers
----------
The project code lives at https://github.com/jdmonin/JSettlers2 .

To build PTE.jar, use: gradle i18neditorJar

See the PropertiesTranslatorEditor javadoc for current limitations and TODO items.

Patches can be sent by email or by pull request; if emailing, use diff -u format.
Please make sure your patch follows the project coding style (see /doc/Readme.developer.md).


Version history
---------------

1.1.0 (not yet released)
- Current beta version being developed.
- Use Ctrl-S/Cmd-S to save any changes to both source and destination
- Remove icon clutter in key string add/edit dialog

1.0.0 (2016-04-21 532e652)
- Can create new translation destination files
- When searching, show current matching cell using different text color
- Adds Find button
- MacOSX can use Cmd-F for Find
- Use case-insensitive comparison for filenames (Windows)
- While loading source and destination, look for duplicate keys

0.9.0 (2014-03-31 b9efb1a)
- Initial release of side-by-side Properties Translator's Editor
- Can edit files but not create them
- Use Ctrl-F for Find (not yet discoverable in GUI)
