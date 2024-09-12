# Developing JSettlers

## Contents

- Overall structure of the code and project
- Tips for debugging
- Setup instructions for JSettlers as an Eclipse project
- Build Setup and Results
- Recommended debug/run configurations for testing
- To configure a sqlite database for testing
- Current partially-done work
- To do: The current TODO list
- Saving and loading games at server
- Game rules, Game Options
- Developing with a database (JDBC)
- Internationalization (I18N)
- Robots (AI)
- Network Communication and interop with other versions or languages
- Coding Style
- Release Testing
- JSettlers on Github
- Related Projects



## Overall structure of the code and project

### Project layout

This project uses gradle 6.x or 7.x (or IDEs) to build, and
follows the directory structure/layout of a maven/gradle project.

Also see the "Build Setup and Results" section.

### Packages and notable classes

The most important major classes have several paragraphs of class javadocs
describing their structure and interactions. If something is unclear after
reading those docs and this README section, please file an issue at github
or email `jsettlers@nand.net` to clarify things.

The main server class is [soc.server.SOCServer](../src/main/java/soc/server/SOCServer.java);
 clients' requests and actions are dispatched into
[SOCServerMessageHandler](../src/main/java/soc/server/SOCServerMessageHandler.java),
[SOCGameMessageHandler](../src/main/java/soc/server/SOCGameMessageHandler.java),
and [SOCGameHandler](../src/main/java/soc/server/SOCGameHandler.java).
The client communication and game-list window is in
[soc.client.SOCPlayerClient](../src/main/java/soc/client/SOCPlayerClient.java),
and in-game interface is in
[soc.client.SOCPlayerInterface](../src/main/java/soc/client/SOCPlayerInterface.java).
Game state is held at the server in
[soc.game.SOCGame](../src/main/java/soc/game/SOCGame.java) and its fields;
only partial game state is known at clients.
The game's "business logic" is mostly in SOCGame,
[SOCPlayer](../src/main/java/soc/game/SOCPlayer.java),
and [SOCBoard](../src/main/java/soc/game/SOCBoard.java).

The sea board and scenarios use [SOCBoardLarge](../src/main/java/soc/game/SOCBoardLarge.java).
Game options and scenario rules
are controlled through [SOCGameOption](../src/main/java/soc/game/SOCGameOption.java):
See section "Game rules, Game Options"
for details.

Package `soc.extra` is for useful or reusable code like [GameEventLog](../src/main/java/soc/extra/GameEventLog.java)
which is developed with the main code but shouldn't be part of the built jars
or test packages.

Communication is described in the "Network Communication" section
and [soc.message.SOCMessage](../src/main/java/soc/message/SOCMessage.java) javadocs. Robots talk with the
server like normal human clients. Most robot messages are per-game; instead
of being handled in SOCRobotClient, these are handled in a loop in
[SOCRobotBrain](../src/main/java/soc/robot/SOCRobotBrain.java)`.run()`.

For more information about the AI, please see the "Robots (AI)" section
and Robert S Thomas' dissertation.

### Board layouts and coordinates

Pieces are placed at edges, nodes, or hexes.

To show piece coordinates in the board's tooltips, in the game window chat box type: `=*= showcoords`  
To no longer show those coordinates, type: `=*= hidecoords`

For more information about the board coordinates, see javadocs in [soc.game.SOCBoard](../src/main/java/soc/game/SOCBoard.java)
and [SOCBoardLarge](../src/main/java/soc/game/SOCBoardLarge.java)
(or dissertation appendix A), and these diagrams:

**Sea boards:**  
Rectilinear grid of rows and columns. A vertical edge's coordinate is its center.
A diagonal edge's coordinate value is taken from its left-end node.  
![hexcoord-sea.png](/doc/hexcoord-sea.png)

**4-player classic:**  
Diagonal axes for rows and columns.  
![hexcoord.gif](/doc/hexcoord.gif)

**6-player classic:**  
Same coordinates as 4-player classic. Trading ports' hexes are off the edge of the grid.  
![hexcoord-6player.gif](/doc/hexcoord-6player.gif)

### Development

Coding is done in Java 8 for client compatibility, but should compile cleanly
in newer JDKs. The build system is gradle 6.x or 7.x;
the newest tested versions are gradle 6.9.2 and 7.5.1. Use any IDE you want, including vi.
Use spaces, not tabs.  Please try to keep the other conventions of the
current code (see "Coding Style" below for more details.).

When adding new methods or fields, describe them in javadoc, including the
`@since` marker and the one-sentence summary (even though some old methods
don't have that summary).

When submitting patches, please send pull requests or use unified diff (`-ur`) format.

The client's structure was refactored in 2.0 by Paul Bilnoski.  Paul's description of this work:

> Added two major new pieces of API: PlayerClientListener and GameDisplay. These are spliced between the `SOCPlayerClient` and AWT UI code, primarily found in `SOCPlayerInterface`. The APIs are used by the player client abstract network communication away from the UI - to communicate with the UI about network events received and receive events to send to the server. Some of it is done using inner classes and interfaces which should be split out into an improved package structure.


## Tips for Debugging

You can use debug commands in any practice game, by typing into the game chat
textfield.  Type `*help*` for a list of commands.  Debug commands can give resources
or dev cards to players, freely place pieces on the board, remove a bot from the
game, etc.  There are also a few robot debugging commands, not listed in `*help*`;
see the "Robots (AI)" section.

Since the game window remembers recently sent chat text, you can use the Up/Down arrow keys
to browse and re-send debug commands.

To help with security and prevent cheats, by default debug commands are disabled
except for practice games.  If you need to use debug commands on a multi-player
server, start that server with `-Djsettlers.allow.debug=Y` on its command line,
then connect with username `debug`. Except for practice games, no other username can
use debug commands.

For security, if you must use the "debug" user outside of your own laptop or workstation,
please use sqlite or another database and make a "debug" account with a password
(see "Database Setup" section of [Database.md](Database.md)).

`D.ebugPrintlnINFO` is turned on or off for each java class by the import at the top of the file.
For example if you wanted to see D.ebugPrintlnINFO output for soc.game.SOCPlayer,
in SOCPlayer.java you would change the line  
`import soc.disableDebug.D;`  
to  
`import soc.debug.D;`

To show hidden/private player details, activate and use the "Fully Observable"
or "VP Observable" inactive game option:

- `PLAY_VPO`: Show all players' VP/dev card info
- `PLAY_FO`: Show all player info as fully observable: Resources, VP/dev cards

(For activation process, search this file for "Inactive Options".)

To print the contents of messages sent between the server and client, start the
client with vm argument `-Djsettlers.debug.traffic=Y` (this goes before `-jar` if using
the command line). This works for the player client and the robot client, including
bots started as part of the SOCServer. For each message, robot clients will print its
direction `IN -` (from server) or `OUT -` (from bot) + their name + ` - ` + message data.
When a bot client appears idle and isn't receiving messages for any particular active game,
it won't print SOCServerPings.

One way to send configuration or debug settings from the server to a third-party
robot, client, or the game objects running there, is to use the `_EXT_BOT`,
`_EXT_CLI`, or `_EXT_GAM` SOCGameOptions. Those are defined but not used by
core JSettlers, for use by third-party code. They can be set at the server's
command line or properties file and read by the client when it connects to a game.

To force the board to contain fog hexes, start the server with vm argument
`-Djsettlers.debug.board.fog=Y` which will hide 20% of land hexes behind fog
when using the sea board. To force half the fog hexes to be gold for testing,
use `-Djsettlers.debug.board.fog_gold=Y`.

If you want to inspect the game object state at the server or robot:

- Set a breakpoint at `SOCServer.processDebugCommand` or `SOCServerMessageHandler.handleGAMETEXTMSG`
- send a text message from a client's chat textfield, and inspect the game obj
  at that point
- To inspect game state at robot, breakpoint `SOCRobotClient.treat` and send a
  text message like `*BOTLIST*` or `robot2:current-plans`
- To trace robot decisions and actions for incoming messages, set a breakpoint
  in `SOCRobotBrain.run` at `turnEventsCurrent.addElement(mes);`
- On Linux/MacOSX/Unix JVMs, you can print a stack trace / thread dump at the server by
  sending SIGQUIT (`kill -QUIT ` _pidnumber_) . In deadlocks the thread dump won't
  show what has an object locked, but may show what's waiting on the object.
- If you've set breakpoints in any robot code, temporarily increase
  `SOCServer.ROBOT_FORCE_ENDTURN_SECONDS` so the bot's turns won't be ended early
  for inactivity while you're debugging.

Some game options are meant to be set by the server during game creation,
not requested by the client.  Their option keynames all start with '_'.
(These are used for game scenario rules.)  The New Game Options frame normally
hides them during game setup.  If you want to show them:

- Launch the client
- Start a Server, or Join a Server
- In the Nickname field, type: `debug`
- Click Practice or New Game

The values you set may still be overridden by the server
in SOCGameOptionSet.adjustOptionsToKnown.

If you're testing and need the client to create a game no one else can join,
find and uncomment the `DEBUGNOJOIN` option in `soc.game.SOCGameOption`
before starting the server.

To use the "Free Placement" debug mode, type this debug command on your turn:

	*FREEPLACE* 1

You can then build and place pieces without consuming resources, by clicking
or right-clicking on the board.  (Don't use the "Buy" buttons for Free
Placement.) The placements must follow game rules, except that they cost
no resources. To place for another player, click their name or face icon.
You cannot remove a piece once it is placed.

During initial placement, you can use Free Placement for any player/bot to
set up some or all of the initial settlements and roads/ships. The number of
settlements must equal the number of roads/ships before the game can continue.

The placed pieces accumulate victory points for their players as normal, and
Longest Road is tracked.  If a player gets 10 VP total, the game will be over
when their turn arrives; your own player would win the game immediately.

To exit Free Placement mode, either click "Done" to end your turn
or type this debug command:

	*FREEPLACE* 0

When exiting Free Placement mode during initial placement, all players must have
either 1 settlement and road, or at least 2 settlements and roads.  This allows
the game setup routines to work properly during this debug mode.  The current
player will be set to the first player (if 2+ settlements/roads placed) or the
last player (1 settlement placed).

In Eclipse, increase the console buffer size before long test runs, so you can
scroll up to see the entire game's debug output.  Go to Eclipse Preferences ->
`Run/Debug` -> `Console`, and either un-check "Limit console output", or set it
to a size larger than the default 80000 characters.  Robots and network messages
can create a lot of output, so can javadoc generation.

These debug commands are used at the client to help show the board's status:

- `=*= showcoords` or `=*= hidecoords`: show or hide coordinates of the playing
  piece, hex, etc currently at the mouse pointer's location.

- `=*= show: all` or `=*= hide: all`: show or hide potential and legal piece
  placements which are currently valid for player 0 (sits in the upper-left
  corner); available only for the sea board layout, not the classic 4- or
  6-player boards. Yellow shapes show legal coordinates, green shows potential,
  red shows "on land". To show only some piece types' legals and potentials, 
  instead of 'all' use an index number from the
  `SOCBoardPanel.debugShowPotentials` javadoc.
  The board boundary is shown for index 2, which also prints the board panel
  and SOCPlayerInterface frame sizes and margins to System.err.

If you want the robots to play a few games without any humans, set this
server property on the command line (after `-jar ...`) when starting the server:
`-Djsettlers.bots.botgames.total=5`. As each game finishes, a new bot-only
game will be started until 5 have been played. You can optionally use the client
to observe a bot-only game. Also if `jsettlers.bots.botgames.total` != 0, at any
time the client can create a new game and start it as bots-only using the debug
command `*STARTBOTGAME* [maxBots]`. See the "Robots (AI)" section for more
details on bot testing.

### JSettlers client properties for debugging and testing

To use any of these, specify them in the IDE or java command line as JVM
parameters (before, not after, `-jar` or the SOCPlayerClient class name):

- `-Djsettlers.locale=es_MX` - Use a different locale
- `-Djsettlers.debug.traffic=Y` - Print network traffic; see above for details
- `-Djsettlers.allow.debug=Y` - If client starts a server, allow debug mode user to connect
- `-Djsettlers.debug.clear_prefs=PI_width,PI_height` - Remove these persistent
  preferences at startup. See SOCPlayerClient PREF_* fields for all name keys.
- `-Djsettlers.debug.client.features=;6pl;sb;` - Pretend to not support some
  of the optional client features from `SOCFeatureSet`. (To see all the
  standard features, omit this property but use `jsettlers.debug.traffic`, then
  look for semicolons within the Version message sent to the server.)


## Setup instructions for JSettlers as an Eclipse project

Written for Eclipse 4.23 and Buildship 3.1, should be applicable to other versions
with minor changes. These instructions can be adapted to import JSettlers and
its `build.gradle` into other IDEs.

- If your Eclipse's File -> Import dialog doesn't have a "Gradle" option:
    - Help -> Eclipse Marketplace
        - If Help doesn't have an Eclipse Marketplace option:
            - Help -> Install new software
            - Add
                - Name: buildship
                - Location: `https://download.eclipse.org/buildship/updates/latest/`
                - OK
            - (Or see https://projects.eclipse.org/projects/tools.buildship for install instructions)
            - Select "buildship" from "Work with" dropdown
    - Search -> Find "buildship"
      ("Buildship: Eclipse Plug-ins for Gradle")
        - Buildship 3.x runs on JDK 8 or newer, eclipse 4.3 or newer
    - Install
    - If prompted to restart Eclipse, do so
- Eclipse preferences -> Gradle -> Gradle distribution: Specific gradle version: 6.x or 7.x (6.9.2, 7.5.1 are tested)
- Choose File -> Import -> Gradle -> Existing Gradle Project
- Browse to the jsettlers git checkout's top-level directory (containing `build.gradle`)
- Hit Finish
- Eclipse should import the project and do an initial build
    - If you see the error `Unsupported class file major version 61`,
      your default JDK is too new for that gradle version ([details here](https://docs.gradle.org/current/userguide/compatibility.html)).
        - Install JDK 8 (JSettlers' current preferred version)
        - In Eclipse preferences -> Gradle, point it to that JDK's java home
        - Remove the imported project in Eclipse and import again
- Project -> Properties
    - Resource:
        - Text file encoding: UTF-8
    - Java Compiler:
        - Enable project specific settings
        - JDK compliance
            - Compliance level: 1.8
        - Errors/Warnings:
            - Enable project specific settings
            - Defaults are generally OK, but be sure to change these from "Ignore" to "Warning":
                - Potential programming problems -> switch case fall-through
                - Name shadowing and conflicts -> All
    - OK
    	- If eclipse asks "Build the project now?", hit Yes
- Gradle downloads the project's required and optional library JARs, and the import wizard
  adds them to the project's Dependencies list.
- Run the `assemble` or `build` gradle task now to copy resources from `src/main/resources/`.  
  To do so: Gradle tasks tab -> jsettlers -> build -> assemble

Continue reading to see how to set up the builds and the run configs in Eclipse.
A later section walks through the coding style expected for pull requests or
patch submissions; to set up Eclipse now to use that style, see section
"Eclipse coding style setup and tips".


## Build Setup and Results

Before building, make sure you have the Java Development Kit (JDK) version 8 or higher.
If you only want to run the client or server, you can use either JDK 8 or higher,
or version 8 of the smaller Java Runtime (JRE).

Extra tests in the build want python 2.7 or 3 for unittest discovery.
Java unit tests and extraTests use JUnit 4, which is downloaded by `build.gradle`.
Other scripts like `bin/sql/template/render.py` use python 3, or 2.6 or later.

If you wish to maintain a user database for your server, you need MySQL
or PostgreSQL installed and configured, or the sqlite jdbc driver for a
file-based local database: See "Database Setup" in [Database.md](Database.md).

This project is designed to build with gradle 6.x or 7.x, or from within an IDE
like eclipse. Gradle builds output to `build/libs/`.

To quickly run the server and client: In the Package Explorer pane:

- Expand package soc.server, find `SOCServer`, right-click, run as Java Application
- Expand soc.client, find `SOCPlayerClient`, right-click, run as Java Application
- Click "Connect to a Server", take defaults, click Connect
- Enter a nickname, click New Game, enter a game name, create the game
- Should be able to sit down and start the game

If not using an IDE like eclipse, check the `build.gradle` file. There may be
build variables you may want to change locally. These can be changed by
creating a `build.properties` file, or from the gradle command line by passing
a `-Dname=value` parameter.

The basic build command/task is:  
`gradle build`  
If the build fails with this error:
`A problem occurred starting process 'command 'python''`  
then make sure you can run the `python` command.
If your computer has `python3` instead, update the ext.python_command
declaration in `build.gradle`.

There are several gradle build tasks. Here are the main ones:

- `build`: create project jar files; also runs unit tests
- `assemble`: create jars but don't run unit tests
- `test`: run unit tests
- `extraTest`: run unit tests and a few lengthy extra tests
    - `extraTestPython`: only the python extra tests
    - `extraTest --exclude-task extraTestPython`: only the java extra tests
- `dist`: `build` and create tarballs of the source + built JARs  
  (jsettlers-2.x.xx-src.tar.gz, jsettlers-2.x.xx-full.tar.gz, jsettlers-2.x.xx-full.zip)
  in "build/distributions/"
- `javadoc`: create JavaDoc files in "build/docs/javadoc"
- `i18neditorJar`: create `PTE.jar` for maintaining i18n translations (not built by default)
- `clean`: clean the project of all generated files

**Note**: Even if you're in an IDE running SOCServer or SOCPlayerClient as Java apps,
you should first run the `build` or `assemble` gradle task to copy resources
to their built location from `src/main/resources`; otherwise startup will
fail with this error:

    Packaging error: Cannot determine JSettlers version

### Including JSettlers as a subproject build

JSettlers can be used as a subproject within a gradle build project.
If the JSettlers repo is at (for example) `lib/jsettlers2/` within your
project directory structure, and in your gradle project as  
`include ':lib:jsettlers2'`  
then in your build.gradle you can add the JSettlers jar to your code's compile classpath with:
```
dependencies {
    implementation project(':lib:jsettlers2')
}

compileJava {
    classpath = tasks.getByPath(':lib:jsettlers2:serverJar').outputs.files
}
```


## Recommended debug/run configurations for testing

In my IDE's JSettlers project, I've created these debug/run configurations:

    Java applications:
        cli-noargs: soc.client.SOCPlayerClient
            vm arguments: -Djsettlers.debug.traffic=Y -Djsettlers.allow.debug=Y

        socserver: soc.server.SOCServer
            program arguments: -o N7=t7 -Djsettlers.startrobots=7 -Djsettlers.allow.debug=Y

        socserver-sqlite (optional): soc.server.SOCServer
            program arguments: -o N7=t7 -o RD=y -Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite
                -Djsettlers.startrobots=7 -Djsettlers.allow.debug=Y
                -Djsettlers.accounts.admins=adm 8880 20 dbuser dbpass

The server will start 7 bots with the above configurations.  If you need to
stop and start your own bots, then add `-Djsettlers.bots.cookie=cook` to the
server configuration arguments, and create these Java application configs:

    robot1: soc.robot.SOCRobotClient
        arguments: localhost 8880 robot1 r1 cook
    robot2: soc.robot.SOCRobotClient
        arguments: localhost 8880 robot2 r2 cook

For automated functional testing, the project also includes the script
`src/extraTest/python/server/test_startup_params.py`; run and update that script if
you are developing anything related to game options or jsettlers properties.


## To configure a sqlite database for testing (optional)

This is optional. See also the "Developing with a database (JDBC)" section
of this readme.

These instructions are written for Eclipse 4.6. JSettlers+sqlite works with
standard Eclipse; the j2ee Eclipse adds a convenient data browser. Note that
[Readme.md](../Readme.md) mentions a command-line option
`-Djsettlers.db.jar=driverfile.jar`; that's needed only while running the
jsettlers JAR from the command line, not running inside the IDE.

- See the `socserver-sqlite` IDE Run Configuration in the previous section;
  this config includes the sqlite database you're about to configure.
- Download the driver from https://github.com/xerial/sqlite-jdbc/releases -> assets.
  The downloaded JAR might have a name like `sqlite-jdbc-3.27.2.1.jar`.
  These instructions use a generic name `sqlite-jdbc-3.xx.y`.
- Project properties -> Java build path -> Libraries -> Add External JARs... ->
     Browse to `sqlite-jdbc-3.xx.y.jar`
- If using eclipse j2ee instead of basic Eclipse:
  - Eclipse menu -> prefs -> data mgmt -> connectivity -> driver definitions -> Add
  - Select `SQLite JDBC Driver`
  - Jar list -> edit (or add, if empty) -> navigate to `sqlite-jdbc-3.xx.y.jar` -> OK

- Create and initialize the db file:
  - in Run Configurations dialog:
    - duplicate `socserver-sqlite` -> `socserver-sqlite-setup`
        - program arguments: add at beginning of args: `-Djsettlers.db.script.setup=src/main/bin/sql/jsettlers-tables-sqlite.sql`
  - Run the `socserver-sqlite-setup` configuration
  - After running, this line should appear in the console:  
    `Setup script was successful. Exiting now.`
- If using eclipse j2ee, add the database so you can add tables and query it:
  - `Window -> show view -> other... -> data mgmt -> data source explorer`
  - Right-click Database Connections, choose New
    - Type: SQLite ; give a description and click Next
    - Browse to `jsettlers.sqlite` (it's most likely in your workspace, or the
      server's working directory)
  - Click "Test Connection"
  - Click Finish
- Run the `socserver-sqlite` configuration
  - In console, this line should appear near the top of the output:  
    `User database initialized.`
- The database is now ready for use, development, and debugging.
- To create player users, see [Readme.md](../Readme.md) and use `SOCAccountClient`.
  The first account you create should be `adm` (named in property
  `-Djsettlers.accounts.admins=adm`) which can then create others.


## Current partially-done work

- Refactor SOCMessage classes to use templates
- Some SOCMessage classes (SOCGames, SOCJoinGameRequest) accept objects like
  SOCGame and parse/create them; over-the-network communication will always be
  strings only.
- Search the source for `/*18N*/` and externalize those strings; see "I18N"
  section below


## To do: The current TODO list

Work on these possible roadmap items has not yet begun. This list is ranked
from easier to more difficult. You can also search the source for TODO for
ideas.

- Visual reminder to player when they've made a trade offer
- Refactor: `new Date().getTime()` -> `System.currentTimeMillis()`
- Occasionally the board does not re-scale at game reset
- Docs: State diagram for `SOCGame` states, or important message sequences
  (log into server, create/join game, roll dice, etc)
- Docs: `PlayerClientListener` interface has some methods without javadocs: Add by checking `SOCPlayerInterface.ClientBridge` implementation
- Java 7+ cleanup: Use diamond operator where possible
  - Example: change  
    `Map<String, SOCGameOption> newOpts = new HashMap<String, SOCGameOption>()`  
    to  
    `Map<String, SOCGameOption> newOpts = new HashMap<>()`
  - This regexp search will find some candidates: `= new \w+\s*<[^>]`
  - For clarity, decide case-by-case whether to use diamond with deeply nested types like  
    `new Stack<Pair<NodeLenVis<Integer>, List<Integer>>>()`
- Add more scenarios' unit tests to `soctest.game.TestScenarioRules`
- Kick and replace robots if inactive but current player in game, assume they're buggy (see forceEndTurn, SOCPlayer.isStubbornRobot())
- Control the speed of robots, in practice games and with other humans
  - Adjust `SOCRobotBrain.pause`, `ROBOT_FORCE_ENDTURN_TRADEOFFER_SECONDS`, etc
- For bot test runs with `-Djsettlers.bots.botgames.shutdown=Y` (`SOCServer.PROP_JSETTLERS_BOTS_BOTGAMES_SHUTDOWN`):
  - Print a summary at the end in a machine-readable format like YAML: Number of games, average length, etc
  - Capture any exceptions thrown by bots during those games
  - If any exceptions thrown, System.exit(1)
- Add more sound effects
- Client: Call frame.setIconImage instead of using default java icon
  - Thanks to tiehfood for this suggestion (github issue #84)
- Add more functional and unit tests, in `src/extraTest/` and `src/test/` directories
  - Medium-level example: Add a board-geometry unit test to `soctest.game.TestBoardLayouts`
    to check all scenarios' layouts against the "Layout placement rules for special situations"
    mentioned in `SOCBoardAtServer` class javadocs
  - To set up specific test situations, could load a saved game from JSON
    by calling SOCServer.createAndJoinReloadedGame or a method like TestRecorder.connectLoadJoinResumeGame
- Possibly: Auto-add robots when needed as server runs, with server active-game count
    - Only do so if `jsettlers.startrobots` property is set
- Client i18n: Add language selector to main window (suggested in issue #99 by kotc)
- Refactor: TestRecorder: Add method for common setup code from testBasics_Loadgame, testBasics_SendToClientWithLocale, testRecordClientMessage
- Refactor: `SOCResourceSet`: Use something like AtomicIntegerArray for thread-safe writes/reads
- Refactor: Combine ShadowedBox, SpeechBalloon: They look the same except for that balloon point
- Refactor: Rework ShadowedBox, SpeechBalloon to use a custom-drawn Swing Border
- Refactor: New methods to shortcut `ga.getPlayer(ga.getCurrentPlayer())` or `getClient().getClientManager()`
- Refactor: `SOCGame` buy methods (`couldBuyDevCard`, `buyRoad`, etc): Call SOCResourceSet.gte(ResourceSet),
  subtract(ResourceSet) with playing piece `COST` constants
- Refactor `SOCGameOptionSet`:
  Create SOCGameOptionSetAtServer for methods used only there, like optionsNotSupported and optionsTrimmedForSupport
- Refactor: name of dev-cards consolidate
- Refactor: resource-type constants consolidate somewhere (Clay, Wheat, etc)
    - Currently in 2 places: `SOCResourceConstants.CLAY` vs `SOCPlayerElement.CLAY`
    - Maybe standardize resource type names and other terms to those in 5th Edition:
        - Resources: brick, lumber, grain, ore, wool (no ambiguous "W")
        - Tile types: hills, forest, fields, mountains, pasture, desert
        - Dev cards: knight, progress (road building, year of plenty, monopoly), VP (market, library, chapel, university, great hall)
- Customize bot names (txt file or startup property) in SOCServer.setupLocalRobots
- Refactor `SOCRobotClient`: Move simple handle-methods which don't put the
  message into brainQ, but only update game fields/methods, into
  SOCDisplaylessPlayerClient if possible.
- Refactor `SOCDisplaylessPlayerClient` like SOCPlayerClient: Move handler methods into
  a class like MessageHandler, and sender methods into a class like GameMessageSender.
  Watch for method calls from the `soc.robot` and `soc.client` packages.
- Client: Save and reload practice games
    - Use same json format as `*SAVEGAME*`, `*LOADGAME*` debug commands mentioned in "Saving and loading games at server" section
    - File dialog for save/load
    - May require a change to how client jar is built/packaged, to include gson jar; also check licensing for redistribution
- House rules and game options
    - Client: Remember last game's options from previous launch (github issue #28)
        - Or, "game template"
        - Maybe: Save Settings/Load Settings button
        - Client already remembers some settings persistently, like Sound and Player Icon: See soc.client.UserPreferences
    - Client: New game: Random options and scenario for variety (issue #29)
    - Optional max time limit for player turns (issue #68)
    - Thanks to kotc and dannythunder for these suggestions
- Track a limited supply of resource cards at the bank
    - Currently unlimited
    - Official game rules have a supply limit. Paraphrasing 5th edition rules:
        - During resource production (dice roll), check the remaining supply
          for each resource type (brick, ore, ...) versus needed production (settlements and cities)
        - If supply shortage affects 1 player, that player gets all the remaining supply
        - If affects more than 1 player, no player receives that resource type
        - Other resource types are supplied as usual if they aren't short
    - Original 4-player game supplies 19 resources of each type;
      6-player adds 5 more of each; Seafarers does not add more supply
    - If a shortage occurs or supply is very low after production,
      announce that to the game with a `SOCGameServerText`
    - Show remaining supply in Statistics popup and `*STATS*` debug command
    - Game window is probably too cluttered already to always show remaining supply;
      any way to cleanly do so would be a bonus
    - Be backwards-compatible: Add a house rule to `SOCGameOptionSet` for limited or unlimited resources
    - Thanks to Ruud Poutsma and balping (github issue #85) for requesting this feature
- Refactor: combine the `cli/displayless/robot` endturn-like methods. For example,
  search for `ga.setCurrentDice(0)`, or `newToOld`, or `ga.resetVoteClear`
- Bots on sea boards: Help bots decide when it makes sense to move a ship (versus build another)
  and have the bot do so
    - Example: Revealing fog hexes in the middle island
    - React properly if server rejects move-ship request
- Docker (dockerfile in git) or other containerization, including sqlite jdbc and
  a `jsserver.properties` using sqlite
    - Related bootstrapping issue: admin doc reminder to create admin account
      right away, name that admin in jsserver.properties
- User documentation is out of date; but unsure if any user ever reads it anyway
- At board reset, game observers not currently handled properly
- Property for read-only database use without errors
- Game "owner" with extra powers (kick out player, etc)
    - What happens if owner loses connection?
- "Hot seat" mode (multiple human players sharing a screen on 1 client)
- Monitoring: Command line utility or html-based dashboard: Uptime, health of
  bots, currently active/total games from `*STATS*` cmd, client versions, any
  errors, etc
- Per-game thread/message queue at server (use SOCMessageForGame.getGame)
- New game/modding/scenarios: Optional simple map file format for board layout and game options (suggested in github issue #46 by kotc)
    - Maybe have a converter to this format from `*SAVEGAME*` json format
- HTML5 client (see v3 branch for protobuf/JSON over websockets and preliminary
  observer-only start on that work)
- Cities & Knights support
    - UI mock-ups
    - state change / network message plans
    - robot support
- Support for multiple game types
    - At server: Other game types would extend GameHandler (like SOCGameHandler)
    - When client connects: Game list, scenario list, and game options would
      need to deal with multiple game types
    - SOCJoinGame message: Add optional gametype field at end (blank for
      current type SOC; older clients wouldn't send the field)


## Saving and loading games at server

To help with testing, the server can save a game and board's state to a file
and load it later, using admin/debug commands.

This feature is still being developed, so the notes here are very basic.
The file format/structure is stable, future versions will stay compatible with it.

Most games with a scenario can't yet be saved, because of their special pieces
or game/player data fields. Basic scenarios like "Four Islands" which don't have
special rules or pieces can be saved and loaded.

### Usage/UI

- Set value of server property `jsettlers.savegame.dir` to point to the game-saves directory
- Log in as `debug` or an admin user
- Start a game, place pieces as needed, begin game play
- Admin command to save a snapshot: `*SAVEGAME* savename`
  - savename can contain letters and digits (Character.isLetterOrDigit), dashes (`-`), underscores (`_`)
  - If snapshot already exists, use flag `-f` to force overwriting it with the new save
- Admin command to load a snapshot: `*LOADGAME* savename`
  - Server parses the snapshot and create a game with its contents
  - Debug/admin user joins, bots are asked to join
  - A later version might optionally support requiring certain types of bots
  - Temporarily sets gamestate to new hold/pause state `LOADING`, so current player won't take action until everyone has joined
- If other human players will be playing, have them join and sit down now
  - Note: If joining human has same name as any player in loaded game, their client will automatically sit down and can't be an observer
- Admin command to resume play of loaded game: `*RESUMEGAME*`
  - If game was saved with human players who haven't rejoined, bots will join now for those players
  - If no human players have sat down, game will play as robots-only even if server isn't set to allow bot-only games
  - Game play now resumes, at the current player and state it was saved with

This feature requires a GSON jar which must be on the classpath, or named `gson.jar` (no version number)
in same directory as JSettlersServer.jar. Download GSON 2.8.6 or higher from
https://search.maven.org/artifact/com.google.code.gson/gson/2.8.6/jar or
https://mvnrepository.com/artifact/com.google.code.gson/gson/2.8.6 .  
If using Eclipse, also add GSON to the project's build path -> Libraries -> Add External JAR

If you're not using this feature, JSettlers doesn't require or use the GSON jar.

If you want to write code which loads saved games, see `SOCServer.createAndJoinReloadedGame`
and/or `TestRecorder.connectLoadJoinResumeGame`.

### Saving game message logs / game event logs

For test or debugging purposes, if you want to play some games and save their network message logs
in a standardized format, you can do so with `soc.extra.server.RecordingSOCServer`. That specialized server
records all messages relevant to gameplay captured by server-side calls to
`messageToPlayer / messageToGame(.., isEvent=true)` or `recordGameEvent(..)`,
along with messages from client players and observers.
A list of basic game actions and their message sequences is in
[Message-Sequences-for-Game-Actions.md](Message-Sequences-for-Game-Actions.md).

Launch RecordingSOCServer and log in as `debug` with the standard client.
In any game you're playing or observing, logs can be saved at any time with the debug command  
`*savelog* filename`  
which will save to `filename.soclog` in the server's current directory.

Optional flags for `*SAVELOG*` command:

- `-c`: Save only messages to clients from the server, not also from all clients to server
- `-u`: Untimed; don't write the elapsed-time field if present in log entries
- `-f`: Force overwrite an existing log

Resetting the board will clear the log.

For log file format, see `soc.extra.server.GameEventLog` javadocs.

RecordingSOCServer also enables the `*SAVEGAME*` command; games are saved to the current directory.

RecordingSOCServer isn't built into the JSettlers jars. So if you need to run it from the command line,
you'll need more than the usual classpath. You can launch it with a bash/zsh command like:  
`jar=(build/libs/JSettlersServer-*.jar); java -classpath ${CLASSPATH}:${jar}:build/classes/java/main:build/classes/java/test soc.extra.server.RecordingSOCServer`


## Game rules, Game Options

Game rules and actions are controlled through Game Options (class
`SOCGameOption`; see SOCGameOptionSet.getAllKnownOptions javadoc for a list).
Options have types (bool, enum, etc) and flags for their properties
("hidden internal option", "drop if not set", etc). All scenario-related
game option keynames start with `_SC_` and provide special rules/behaviors
for the scenario.

If you're developing a change to the game rules or behavior, see
SOCGameOptionSet.getAllKnownOptions javadoc for how to add a Game Option.

For quick tests or prototyping, including third-party bots/AI development,
there are a few predefined but unused game options available:
`_EXT_BOT`, `_EXT_CLI`, and `_EXT_GAM`. For more info, search this file for
those names or see getAllKnownOptions.

### Inactive/activated Game Options:

Some game options might be useful only for developers or in other special
situations, and would only be clutter if they always appeared in every client's
New Game Options window. So those are declared as Inactive Options, which are
unused and hidden unless activated during server startup by setting a
config property:  
`jsettlers.gameopts.activate=PLAY_VPO,OTHEROPT`  
Activated Options are then handled like any regular game option.
For more details, see the SOCGameOption.activate javadoc.

### Third-Party Game Options:

"Third-party" options can be defined by any 3rd-party client, bot, or server JSettlers fork,
as a way to add features or flags but remain backwards-compatible with standard JSettlers.
For more details, see the SOCGameOption.FLAG_3RD_PARTY javadoc.


## Developing with a database (JDBC)

JSettlers can optionally use a database to store users and passwords, game
score history, and robot parameters.  If databases interest you, the project
welcomes contributions. Please keep these things in mind:

- The DB is an optional part of jsettlers, other functions can't rely on it.
- DB code should be vendor-neutral and run on mysql, postgres, sqlite, oracle, etc.
  Please test against sqlite and at least one other db type before sending a pull request.
- See [Database.md](Database.md) for JDBC driver download sites, URL syntax,
  and server command-line arguments.
- For test runs inside Eclipse, add the JDBC driver to the project's
  build path -> Libraries -> Add External JAR, or add it to the classpath tab of
  SOCServer's eclipse Run Configuration; that option is
  useful when testing against multiple database types.
- Any DB upgrade should be done in `soc.server.database.SOCDBHelper.upgradeSchema()`
  and (for new installs) `jsettlers-tables-tmpl.sql`
- Any changes to the schema setup scripts should be done in
  `src/main/bin/sql/template/jsettlers-tables-tmpl.sql` and then regenerating
  scripts from that template:

      cd src/main/bin/sql/template
      ./render.py -i jsettlers-tables-tmpl.sql -d mysql,sqlite,postgres -o ../jsettlers-tables-%s.sql
      git status

- See also the "To configure a sqlite database for testing" section of this readme.


## Internationalization (I18N)

An internationalization framework was put into place for v2.0.00.
Temporary work in progress is surrounded by marker comments:
`/*I*/"{0} has won the game with {1} points."/*18N*/`

When building strings that the user will see, don't use + to build the strings;
use strings.get or strings.getSpecial with parameter placeholders such as {0}.
(At the server, use `messageToPlayerKeyed`, `messageToPlayerKeyedSpecial`,
`messageToGameKeyed`, etc.). The client strings live in
`soc/client/strings/data*.properties`.  An example commit is 68c3972.
The server strings live in `soc/server/strings/*.properties`.  An example
commit is 3e062b7. See the comments at the top of
strings/server/toClient.properties for format details.

If an i18n string lookup's english text isn't obvious from the key, add it as a
comment to make searching the source for strings easier:

	setTooltipText(strings.get("hpan.points.total.yours"));  // "Your victory point total"

Note that all java properties files use `ISO-8859-1`, not `UTF-8`, as their
encoding. Make sure your editor or IDE honors this encoding format.
Unfortunately you must use `\uXXXX` for characters outside that 8-bit encoding's
range, directly or with the `native2ascii` utility:

	native2ascii -encoding utf8 examplefile.utf8 examplefile.properties

You can use the included editor `net.nand.util.i18n.gui.PTEMain` to edit
localized strings for two locales side by side; this editor has unicode
support and color hilighting, and will convert to `ISO-8859-1` (with unicode
escapes) automatically when saving. See `src/main/java/net/nand/util/i18n/README.txt`
for more details.

Before running PTEMain for the first time, you might need to run the gradle task
`i18neditorJar` (using gradle on the command line or from your IDE) so that the
PTEMain editor's own externalized strings will be available.

While starting the editor this message is harmless, because preferences are stored per-user:

    Dec 6, 2013 3:59:16 PM java.util.prefs.WindowsPreferences <init>
    WARNING: Could not open/create prefs root node Software\JavaSoft\Prefs at root 0x80000002. Windows RegCreateKeyEx(...) returned error code 5.

If you need to override system locales for testing, launch the client with
vm argument `-Djsettlers.locale=es` (this goes before -jar if using the
command line).

Pseudolocalization for testing (en_AA locale) uses StringUtil from the JBoss
Ant-Gettext utilities. See command-line utility
`net.nand.util.i18n.PropsFilePseudoLocalizer`.

Thanks to Luis A. Ramirez for building the initial client i18n framework and
advocating i18n-friendly programming practices throughout the code base with
helpful specific suggestions.


## Robots (AI)

If you're interested in experimental changes to the jsettlers robots, or writing
a "third-party" robot to connect to jsettlers, read this section.

The project was originally started as Robert S Thomas' PhD dissertation about AI
agents, so there's some instrumentation for the bots but it's not entirely
documented.  For a technical overview of how the bots plan their actions, start
at the SOCRobotBrain class javadoc.

See the "Related Projects" section near the end of this document for
some third-party robot AI examples.

### Testing/Debugging

When testing with the robots, you may need to send them resources or commands
on their turn. The easiest way to do that is to type the debug command but don't
hit enter, then wait for the bot to offer a trade. The trade offer will give a
few seconds to send the command. On the other hand if you want the bots to move
quickly, use the No Trading house rule and play on the 6-player board, where the
bots have shorter delays since there might be more players (but you can also use
this board with 2-4 players).

You can also build pieces for the bots using "Free Placement" debug mode (see
above) to help set up debugging or testing situations, including the game's
initial placement.

Games with bots can be set up, pieces built or dev cards given, then saved and
reloaded by the debug user with the optional "savegame" feature: See section
"Saving and loading games at server".

There are a few bot debugging commands, such as print-vars and stats. To send
them, type `botname:command` into the chat textbox while playing or observing a
game with bots: `robot 7:stats`. See `SOCRobotClient.handleGAMETEXTMSG_debug`
for more details. For some commands, you must first send a `:debug-on` command
to start recording stats.

Some of the bot debugging commands can ask about those stats for an empty
location where the bot's considering to build (`:consider-move`) or building
to counter another player's builds (`:consider-target`). You can ask the client
to let you click on these locations to send the right coordinate to the bot.
These client-helper debug commands are recognized in the chat textbox. Type one,
then click the location you're asking the bot about:

| Command | Bot debug command sent after clicking location |
| --- | --- |
| `\clm-road ` _botname_ | _botname_`:consider-move road ` _coord_ |
| `\clm-set ` _botname_  |  _botname_`:consider-move settlement ` _coord_ |
| `\clm-city ` _botname_ | _botname_`:consider-move city ` _coord_ |
| `\clt-road ` _botname_ | _botname_`:consider-target road ` _coord_ |
| `\clt-set ` _botname_  |  _botname_`:consider-target settlement ` _coord_ |
| `\clt-city ` _botname_ | _botname_`:consider-target city ` _coord_ |

### Development

If you're looking to make minor changes, it's probably best to fork or extend
the `soc.robot` package and go from the classes and javadocs there.
For trivial example subclasses extending `SOCRobotClient` and
`SOCRobotBrain`, see `soc.robot.sample3p.Sample3PClient` and `Sample3PBrain`.
The `Sample3PClient` class javadoc mentions useful server properties such as
`jsettlers.bots.percent3p`, `jsettlers.bots.botgames.wait_sec`, and
`jsettlers.bots.timeout.turn`. `Sample3PBrain` demonstrates using a custom
strategy subclass, custom field, and using the `_EXT_BOT` game option to
pass data to the bot when joining a game.

For a larger change, some parts of soc.robot might still be useful for
talking with the server and tracking the game's other players.
Robert S Thomas' dissertation also has some discussion on those structures.

The built-in bots run within the server JVM for security. To have it
also start your third-party bot, start the server with something like
`-Djsettlers.bots.start3p=3,com.example.yourbot.BotClient` .
For details see the SOCServer.PROP_JSETTLERS_BOTS_START3P javadoc.

If your bot runs separately from the server and connects:
You can start a bot through `SOCRobotClient`'s constructor or main method.
The server generates a security cookie that bots must send at connect. You can
view the cookie by starting the server with `-Djsettlers.bots.showcookie=Y` or
override it with `-Djsettlers.bots.cookie=foo`.

When they join a game, third-party bots can be sent configuration or debug
settings using the `_EXT_BOT` game option. This option's string value can
be set at the server command line with  
`java -jar JSettlersServer.jar -o _EXT_BOT=abcde`  
and then read in the bot's brain class. For an example see
`Sample3PBrain.setOurPlayerData()`.

If human players will be creating games, but your bots don't support the sea
board/scenarios or 6-player games, you can disallow those game types at the
server with one or both of these flag properties:

- `-Djsettlers.game.disallow.6player=Y`
- `-Djsettlers.game.disallow.sea_board=Y`

### Running robot-only games

For bot testing and statistics, you can have the server run some robot-only
games (no human players) with the `jsettlers.bots.botgames.total` server property.
To run 7 robot-only games in a row, with each game randomly choosing from 10
robot players, you could start the server with:
`-Djsettlers.startrobots=10 -Djsettlers.bots.botgames.total=7`.
To start more of them at once, increase parallel property (default 4):
`-Djsettlers.bots.botgames.parallel=7`

The robot-only games run at a quick pace, about 2 minutes for a 4-player game.
You can use the jsettlers client to observe a bot game as it plays.

To speed up or slow down robot-only games, start the server with this tuning
option to set the length of SOCRobotBrain pauses during bot-only games: For
example `-Djsettlers.bots.fast_pause_percent=10` will pause for only 10% as long
as in normal games.

To start robot-only games with an equal mix of different sizes and boards,
set optional property `jsettlers.bots.botgames.gametypes`:

| Value | Game sizes and board types |
| --- | --- |
| 1 (default) | Classic 4-player only |
| 2 | Classic 4- and 6-player |
| 3 | Classic and sea board (no scenarios), both 4- and 6-player |

For testing purposes, if you want the server to exit after running all its
robot-only games, start the server with `-Djsettlers.bots.botgames.shutdown=Y` .

If `jsettlers.bots.botgames.total` != 0 (including < 0), at any time the client
can create a new game, join but not sit down at a seat, and start that game as
bots-only using the debug command `*STARTBOTGAME* [maxBots]` to test the bots
with any given combination of game options and scenarios. (Only the `debug` user
can run debug commands on standalone servers. To enable the debug user, start
the server with `-Djsettlers.allow.debug=Y` .)

To test that your bot is properly tracking resources in the game data,
start the server with `-Djsettlers.debug.bots.datacheck.rsrc=Y`
to send `SOCBotGameDataCheck(TYPE_RESOURCE_AMOUNTS)` at the end of
each turn. See that message's javadoc for details.

For robustness testing, the `SOCRobotClient.debugRandomPause` flag can be enabled
by editing its declaration to inject random delays into handling messages and
commands from the server.


## Network Communication and interop with other versions or languages

Players' clients talk to the JSettlers server using a simple string-based
message format. See "Overall Structure" for an overview on network message
handling. Communication format and more details are described in
`soc.message.SOCMessage`. To see all message traffic from a client, set
`jsettlers.debug.traffic=Y` (see "Tips for Debugging" section).

A list of basic game actions and their message sequences is in
[Message-Sequences-for-Game-Actions.md](Message-Sequences-for-Game-Actions.md).
Sample code to recognize and extract game actions from them
(`soc.extra.robot.GameActionExtractor`)
is described in [GameActionExtractor.md](extra/GameActionExtractor.md).

Keeping the network protocol simple helps with interoperability between different
versions and implementations. At the TCP level, JSettlers messages are unicode
Strings sent using `DataOutputStream.writeUTF(String)` and
`DataInputStream.readUTF()`.

AI bots, server monitors, or other client programs can also be written in
non-Java languages. Such clients could communicate with JSettlers by
implementing `writeUTF` and `readUTF` (searching online finds sample code
for popular languages), looking for the message types they need to work with
(listed in `SOCMessage`), and decoding/encoding those message types. See
"Overall Structure" above and class javadocs of `SOCMessage`,
`SOCDisplaylessPlayerClient`, `SOCRobotClient`,
`soc.server.genericServer.Server`, and `SOCServer`.

Unit tests and extraTests check consistency of core game action message
sequences, and informally document examples of those expected sequences.
See `src/test/java/soctest/server/TestRecorder.java` and
`src/extraTest/java/soctest/server/TestActionsMessages.java`.
Those classes also show how you can start up a SOCServer and non-GUI clients
and connect and control them as needed, including loading saved game artifacts.

The experimental `v3` branch replaces the homegrown SOCMessage protocol with
Protobuf, optionally encapsulated in JSON over HTTP. Proof-of-concept bots
are included. To write bots or clients using those well-known protocols,
check out that branch and read its `Readme.developer`.

If you're writing a third-party client or robot, some features of the standard
client are optional. When each client connects, it sends the server a
`SOCVersion` message which includes its version, locale, and features from
`SOCFeatureSet`. If your client hasn't implemented 6-player games, Seafarers
boards, or scenarios, don't include those client features in the `SOCVersion`
message you send.


## Coding Style

Scroll down a bit if you just want to see how to set up the style in Eclipse.

This is the project's coding style, based on the original authors' style.
This section has more detail than you need to know, but it's here if you're
interested. Although we may not agree with every detail of it, for consistency's
sake please keep the conventions of the code that's already there.

Use spaces, not tabs.  The basic indent is 4.  Place braces on their own line.
Lines should be less than about 120 characters long; if you have to indent too
much, consider refactoring into a new method.

Use 1 blank line between methods.  If you have nested classes, use 3 blank lines
between them, and comment each one's closing brace with the class name. If you
have a long method whose work can be divided into "sections", preface each
section with a `/** javadoc-style */` multi-line comment.

Import individual classes, not entire packages like `soc.game.*`. Some classes
deliberately don't import some others for better separation, especially across
packages, or will import a few specific classes for javadocs only.

If a declaration line is getting too long (about 120 characters), break it and
indent it slightly from the first line, not aligned with the method name. `throws`
declarations are also always indented on the next line:

``` java
    public SOCGameOption(String key, int minVers, int lastModVers,
        boolean defaultValue, boolean dropIfUnused, String desc)
        throws IllegalArgumentException
```

If related methods have the same name but different sets of arguments, indent
all their declarations the same way for easier comparison.

Some methods return in the middle of their body, depending on conditions.
That kind of early return should be marked with a prominent comment such as:

    return;   // <--- Early return: Temporary piece ---

If an i18n string lookup's english text isn't obvious from the key, add it as a
comment to make searching the source for strings easier:

``` java
    setTooltipText(strings.get("hpan.points.total.yours"));  // "Your victory point total"
```

Use parentheses around all boolean expressions and their non-unary subexpression parts,
and a space after negation `! `, to make them easier to see as such:

``` java
    flagvalue = (state == xyz);
    somevar = (testflag && ! otherFlag) ? a : b;
    somecondition = ((state == xyz) || (players < 4));
```

Some methods end with this style of code:

``` java
    if (x)
        return y;
    else
        return z;
```

In those cases, the return is part of the logical flow of the if-statement:
Both y and z are normal and valid, and deserve equal "visual weight" and indenting.
That section of code should not be reformatted to something like:

``` java
    if (x)
        return y;
    return z;
```

because conventionally in jsettlers, that would mean z is the usual case
and y is less common or is an edge condition.

In emacs, you can place this in your .emacs file to use spaces instead of tabs:

``` emacs
(setq-default c-basic-offset 4)
(setq indent-tabs-mode nil)
```

(courtesy https://www.jwz.org/doc/tabs-vs-spaces.html, which also mentions vi)

You will also want this to have this, which disables auto-reindenting:
`(setq-default c-electric-flag nil)`

### Eclipse coding style setup and tips:

    preferences -> general -> editors -> text editors:
    displayed tab width: 8
    [x] insert spaces for tabs
    [x] show print margin
    print margin column: 120
    [x] show whitespace characters
        configure visibility -> trailing space, trailing ideographic space, leading tab, trailing tab

    prefs -> java -> code style -> formatter
        Click "Enable Project Specific Settings", then New
        {
            Profile name: 'jsettlers'
            Initialize with profile: Eclipse (built-in)
            [X] Open the edit dialog now

            (Indentation)
            Tab policy: Spaces only
            Indentation: 4
            Tab size: 8
            confirm is unchecked: Indent: [ ] Empty lines

            (Braces)
            All 'next line' except:
            Blocks in case stmt: Next line indented
            Array init: Next line indented
            [X] Keep empty array initializer on one line

            (New Lines) -> In Control Statements
            [X] Before else in if
            [X] Before catch in try
            [X] Before finally in try
            [ ] New line before while in do
            [X] Keep 'else if' on one line
            [ ] (all other options)

            (Line Wrapping)
            Maximum line width: 120
            [x] Never join already-wrapped lines

            (All other sections)
            Take defaults
        }

        Hit OK
        Make sure the formatter "active profile" is jsettlers
        restart eclipse

        go to prefs -> java -> code style -> formatter
        click Configure Project Specific Settings
        if it's not active: set active profile to jsettlers & restart eclipse

When you hit enter twice to skip a line in Eclipse, watch for unwanted whitespace because
Eclipse will add whitespace to each blank line to match the current indent.

If desired, in Eclipse preferences, you can bind a key combination to Remove Trailing Whitespace.
This will trim it from the entire file when the key is pressed.

To manually clean up trailing whitespace:

- Eclipse preferences -> general -> editors -> text editors -> `[x] Show whitespace characters`
- Find/Replace: Regular expressions: `Find [\t ]+$`

### Style notes for graphics

The rotated 3:1 port hexes' font is Optima Bold, 21 pt.


## Release Testing

When preparing to release a new version, run through the list of tests
in [Release-Testing.md](Release-Testing.md).


## JSettlers on Github

The project code lives at https://github.com/jdmonin/JSettlers2 .
Patches can be sent by email or by pull request.
Please make sure your patch follows the project coding style.

The main branch receives new features and enhancements for the next 'minor'
release. As soon as a bug is fixed or a feature's design is fairly stable,
it should be committed to main.

v3 is the experimental branch with major architectural changes.
Protobuf replaces the homegrown SOCMessage protocol.

Releases are tagged as format "release-2.4.00". Each release's last commit
updates [Versions.md](Versions.md) with the final build number,
with a commit message like: "Version 2.4.00 is build JM20200704"  
Then: `git tag -a release-2.4.00 -m 'Version 2.4.00 is build JM20200704'`

### Historical info

While v2.0.00 was being developed, several 1.x.xx releases came out.
2.0 work began (and stable-1.x.xx branch split from main)
right after releasing version 1.1.13. Most work on 1.x.xx was backported
from 2.0 to the stable-1.x.xx git branch; changeset comments often mention
a hash from a main commit. The main branch was renamed from master for v2.5.

The github repo includes the JSettlers2 v1.1.xx CVS history formerly hosted at
https://sourceforge.net/projects/jsettlers2/ , converted to git on 2012-09-28
with cvs2git (cvs2svn 2.4.0).

The old 1.0.x source history (2004-2005) from Robert S Thomas and Chad McHenry
can be found at https://github.com/jdmonin/JSettlers1
or https://sourceforge.net/projects/jsettlers/ .
That JSettlers1 repo also includes jsettlers-1-1-branch which has
Jeremy Monin's first JSettlers releases 1.1.00 through 1.1.06.


## Related Projects

JSettlers was originally started to explore AI agents, and these projects
have used its code as a base for similar work.

### STAC

The Strategic Conversation Project (STAC) is an extensive multi-year effort
to study negotiations, game theory, strategic decision making, and agents,
including human-AI interactions in games. As part of their work, they ran
tournaments of humans and bots using modified JSettlers and recorded that
gameplay for study.

STAC forked JSettlers and added several bot types, UI for partial trades
and "fully observable" open-hand games, a way to record a game's actions to
logs or a database for playback later, some bot API refactoring, and other
miscellaneous work. Some of STAC's features and APIs have been
adapted upstream as part of JSettlers v2.5.00. Jeremy occasionally contributes
PRs to the STAC fork at https://github.com/ruflab/StacSettlers .

Website: https://www.irit.fr/STAC/about.html  
Github: https://github.com/ruflab/StacSettlers  
Previous github: https://github.com/sorinMD/StacSettlers

### Settlers of Botan

Instead of changing any JSettlers code, this undergraduate project's
third-party bot uses JSettlers as a library and extends/overrides the
robot classes with some new implementations and algorithms.

https://github.com/sambattalio/settlers_of_botan
