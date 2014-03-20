README.txt - Notes and current status for the nand.net i18n translator's editor


This internationalization editor is used by the JSettlers project, so its "home" is here:
	webpage:     http://nand.net/jsettlers/devel/i18n/
	bug-tracker: http://sourceforge.net/projects/jsettlers2/
	source:      https://github.com/jdmonin/JSettlers2
in the net.nand.util.i18n and net.nand.util.i18n.gui packages.

Java i18n localization strings are typically kept in properties files named with the
language and country/region, falling back to a base language file if the user's language,
country, or region aren't found:
	toClient_es_AR.properties
	toClient_es.properties
	toClient.properties
Each file contains key = value lines: The keys used in java code, and each key's value in the language.
Comment lines are encouraged for context and explanation.

The editor shows any two such "source" and "destination" files side by side by key for comparison and
translation.  "Source" is the file with a less specific locale, "destination" is more specific.

Start net.nand.util.i18n.gui.PTEMain, then browse to the files you want to edit, or choose one
"destination" file to edit, with its "source" file automatically picked based on the filename.
This editor has unicode support and color hilighting, and will save files in the required
ISO-8859-1 encoding (with unicode escapes) automatically.

When starting the editor this message is harmless, because preferences are stored per-user:
	Dec 6, 2013 3:59:16 PM java.util.prefs.WindowsPreferences <init>
	WARNING: Could not open/create prefs root node Software\JavaSoft\Prefs at root 0x80000002. Windows RegCreateKeyEx(...) returned error code 5.

The editor is work in progress.  Right now it can't create or copy new .properties files; you'll
need to do that on your own and then use the editor on the new file.

There are other properties editors out there, I wanted to see what writing one would be like.
Thank you for trying our i18n editor.

- Jeremy D Monin <jeremy@nand.net>


Developers
----------
The project code lives at https://github.com/jdmonin/JSettlers2 .
See the PropertiesTranslatorEditor javadoc for current limitations and TODO items.
Patches can be sent by email or by pull request; for email use diff -u format.
Please make sure your patch follows the project coding style (see /README.developer).


Version history
---------------

0.9.0
- Being developed now.  Can edit files but not create them.
