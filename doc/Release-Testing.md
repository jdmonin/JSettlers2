# Release Testing

When preparing to release a new version, testing should include:

## Quick tests and setup

- Before building the JARs to be tested, `git status` should have no untracked or uncommitted changes
    - Running `gradle distCheckSrcDirty` also checks that, listing any files with such changes
- `gradle clean test` runs without failures, under gradle 4 and also gradle 5
- These should print the expected version and build number:
    - `java -jar build/libs/JSettlers-2.*.jar --version`
    - `java -jar build/libs/JSettlersServer-2.*.jar --version`
- Message Traffic debug prints during all tests, to help debugging if needed:  
  Run server and clients with JVM property `-Djsettlers.debug.traffic=Y`

## Basic functional tests

- Game setup, join, and reset:
    - Create and start playing a practice game with 1 locked space & 2 bots, past initial placement
      into normal play (roll dice, etc) with default options
    - Create and start playing a practice game on the 6-player board (5 bots), with options like Roll No 7s for First 7 Turns
    - `JSettlersServer.jar`: Start a dedicated server on another ("remote") machine's text-only console
    - Join that remote server & play a full game, then reset board and start another game
        - `*STATS*` command should include the finished game
        - Bots should rejoin and play
    - `JSettlers.jar`: Start a Server (non-default port # like 8080), start a game
    - In the new game's chat, say a few lines ("x", "y", "z" etc)
    - Start another client, join first client's local server and that game
    - Joining client should see "recap" of the game chat ("x", "y", "z")
    - Start the game (will have 2 human clients & 2 bots)
    - Ensure the 2 clients can talk to each other in the game's chat area
    - Client leaves game (not on their turn): A bot should join to replace them & play their next turn (not unresponsive)
    - Have new client join and replace bot; verify all of player info is sent
    - On own turn, leave again, bot takes over
    - Lock 1 bot seat and reset game: that seat should remain empty, no bot
    - Lock the only remaining bot seat and reset game: no bots in new game, it begins immediately
        - Use v2.0.xx lock button's new "Marked" state for this test
- Game play: (as debug user or in practice game)
    - Get and play all non-VP dev card types, and give 1 VP card, with debug commands

            dev: 0 playername
            ...
            dev: 4 playername
    - Road Building with 1 road left, after resource debug command to build the others

            rsrcs: 10 0 0 0 10 playername
            dev: 1 playername

      Should see "You may place your 1 remaining road." & be able to do other actions afterwards
    - 6-player board: On server game with a player and observer, request and use Special Building Phase (SBP)
        - Observer sees request for SBP; then during player's SBP, observer sees yellow turn arrow for your player
- Basic GUI functions:
    - Board resizes with window
    - Sound works
    - Bots' face icons match their name (Robots smarter than Droids)
- Chat channels:
    - While connected to a server, start 2 chat channels
    - In one of those channels, say a few lines ("x", "y", "z" etc)
    - Connect with a second client and join both channels
    - Joining client should see "recap" of the one channel's chat ("x", "y", "z"), no recap in the other chat
    - The 2 clients should each be able to chat, and see each other's text in the correct channel

## New features

- All features added or changed in this version, from [Versions.md](Versions.md)

## Regression testing

- Start a remote server on a console (linux, etc), should stay up for several days including activity (bot games)
    - v2.0.00+: Run several bot games (`jsettlers.bots.botgames.total=5`);
      join one as observer to make sure the pause is shorter than normal games
- New features in previous 2 versions from [Versions.md](Versions.md)
- Each available game option
- Basic rules and game play
    - Can build pieces by right-clicking board or with the Build Panel
    - Can trade with ports by right-clicking board or using Trade Offer Bank/Port button
    - Trade offer, rejection, counter-offer accept/rejection
    - Can play dev card before dice roll
    - Can win only on your own turn
        - This can be tested using the 6-player board's Special Building Phase
- Game reset voting, with: 1 human 2 bots, 2 humans 1 bot, 2 humans 0 bots:
  Humans can vote No to reject bots auto-vote Yes; test No and Yes
- Fog Hex reveal gives resources, during initial placement and normal game play:
     - Start server with vm property: `-Djsettlers.debug.board.fog=Y`
     - Start and test a game with the Use Sea Board option; place an initial settlement at a fog hex
     - Start and test a game with the Fog Islands scenario
- Version compatibility testing
    - Other versions to use: **1.1.06** (before Game Options); **1.1.11** (has 6-player option and client bugfixes);
      latest **1.x.xx**; latest **2.0.xx**
    - New client, old server
    - New server, old client
    - Test these specific things for each version:
        - With an older client connected to a newer server, available new-game options
          should adapt to the older client version.  
          With a newer client connected to an older server, available new-game options
          should adapt to the older server version.  
          This is especially visible when testing 1.x.xx against 2.0.xx.
        - Create and start playing a 4-player game with no options (this uses an older message type)
        - Create and start playing a 4-player game with No Trading option
        - Create and start playing a 6-player game
        - In the 6-player game, request and use the Special Building Phase
        - Connect with a second client (same version as first client) and take over for a robot
            - Should see all info for the player (resources, cards, etc)
            - Play at least 2 rounds; build something, buy a card, or trade
        - When testing a 2.0.xx client and 1.x.xx server: In any game, test robot seat-lock button
            - Click its lock button multiple times: Should only show Locked or Unlocked, never Marked
            - Lock a bot seat and reset the game: Seat should be empty in new game
        - On a 2.0.xx server, have 2.0.xx client create game with a scenario (1.x.xx can't join),
          1.x.xx client should see it in gamelist with "(cannot join)" prefix.
          Start another 1.x.xx client and connect, should see in list with that same prefix
- Server robustness: Bot disconnect/reconnect during game start
    - Start server with vm properties: `-Djsettlers.bots.test.quit_at_joinreq=30` `-Djsettlers.debug.traffic=Y`
    - Connect and start a 6-player game
    - Bots should arrive, game should start
    - Server console should have lines like:  
      `robot 3 leaving at JoinGameRequest('g', 3): jsettlers.bots.test.quit_at_joinreq`  
      `srv.leaveConnection('robot 3') found waiting ga: 'g' (3)`  
      If not, start another game and try again
- StatusMessage "status value" fallback at older versions
    - Start a 2.0.00 or newer server with `-Djsettlers.allow.debug=Y`
    - Start a 2.0.00 client with vm property `-Djsettlers.debug.traffic=Y`
    - That client's initial connection to the server should see at console: `SOCStatusMessage:sv=21`  
      (which is `SV_OK_DEBUG_MODE_ON` added in 2.0.00)
    - Start a 1.2.00 client with same vm property `-Djsettlers.debug.traffic=Y`
    - That client's initial connection should get sv == 0, should see at console: `SOCStatusMessage:status=Debugging is On`
- SOCScenario info sync/negotiation when server and client are different versions
    - Test client newer than server:
        - Build server JAR and start a server from it  
          (or, turn off code hot-replace within IDE and start server there)
        - In `SOCScenario.initAllScenarios()`, uncomment `SC_TSTNB` and `SC_TSTNO`
        - In `version.info`, add 1 to versionnum and version (example: 2000 -> 2001, 2.0.00 -> 2.0.01)
        - Build and launch client (at that "new" version)
        - Click "Practice"; Dialog to make a game should see those 2 "new" scenarios
        - Quit and re-launch client
        - Connect to server, click "New Game"; the 2 new ones are unknown at server,
          should not appear in the dialog's Scenario dropdown
        - Quit client and server
    - Then, test server newer than client:
        - Build server JAR and start a server from it (at that "new" version)
        - Reset `version.info` and `SOCScenario.initAllScenarios()` to their actual versions (2001 -> 2000, re-comment, etc)
        - Build and launch client (at actual version)
        - Connect to server, click "New Game"; should see `SC_TSTNB` but not `SC_TSTNO`
          in the dialog's Scenario dropdown
        - Start a game using the `SC_TSTNB` scenario, begin game play
        - Launch a second client
        - Connect to server, join that game
        - Within that game, second client's "Game Info" dialog should show scenario info
- Client Feature handling
    - For human players:
        - Start a server (dedicated or client-hosted)
    	- Launch a pair of SOCPlayerClients which report limited features, using vm property `-Djsettlers.debug.client.features=;6pl;sb;`
    	  and connect to server. Don't give a Nickname or create any game from these clients.  
          (A pair let us test more than the code which handles the server's first limited client.)
    	- Launch a standard client, connect to server, create a game having any Scenario (New Shores, etc)
    	- Limited client pair's game list should show that game as "(cannot join)"
    	- Launch another pair of SOCPlayerClients which report no features, using vm property `-Djsettlers.debug.client.features=`
          (empty value) and connect to server
        - In each client of that second limited pair, give a Nickname and create any game on the server, in order to authenticate.
	      Leave those new games, to delete them.
    	- In standard client, create a game having 6 players but no scenario
        - First pair of limited clients should connect to that game
        - Second pair of limited clients' game list should show that game as "(cannot join)"
        - In one of the second pair, double-click that game in game list; should show a popup "Client is incompatible with features of this game".  
          Double-click game again; should try to join, then show a popup with server's reply naming the missing required feature: `6pl`
    - For reconnecting disconnected clients:
        - Start a server without any options
        - Start a standard client under your IDE's debugger, connect to server
        - Create & start 3 games (against bots): standard 4-player (no options); on sea board; with any Scenario
        - Start each game, go through initial placement and into normal game play
        - In your IDE, pause the debugged client to simulate network connection loss
        - Start a new client using vm property `-Djsettlers.debug.client.features=;6pl;sb;` and connect as that same username
		- In the new client, double-click the standard or non-scenario sea game to rejoin
        - Should allow connect after appropriate number of seconds, and automatically rejoin the first 2 games but
		  not the game with scenario
        - Game with scenario should disappear from game list, because there were no other human players
	- For robot clients, which are invited to games:
        - Start a server which expects third-party bots, with these command-line parameters:
          `-Djsettlers.bots.cookie=foo  -Djsettlers.bots.percent3p=50`
        - Start the `soc.robot.sample3p.Sample3PClient` "third-party" bot, which does not have the Game Scenarios client feature, with these command-line parameters:
          `localhost 8880 samplebot x foo`
        - Start another Sample3PClient:
          `localhost 8880 samplebot2 x foo`
    	- Launch a standard client, connect to server
    	- Create and start a 4-player game: Some samplebots should join (no features required) along with the built-in bots
    	- Create and start a 6-player game: Some samplebots should join (requires a feature which they have) along with the built-in bots
    	- Create and start a game having any Scenario (New Shores, etc): No samplebots should join, only built-in bots
    	- Quit the standard client and stop the server
        - Start a server which expects third-party bots and has no built-in bots, with these command-line parameters:
          `-Djsettlers.bots.cookie=foo  -Djsettlers.bots.percent3p=50  -Djsettlers.startrobots=0`
        - Start two Sample3PClients, same way as above
    	- Launch a standard client, connect to server
    	- Create and start a game having any Scenario: No samplebots should join, server should tell game-starting client to lock all empty seats
    	- Start a second standard client, connect, join that game and sit down
    	- Start that game (with the two human players)
    	- After initial placement, have one player leave
    	- Server should tell game it can't find a robot
- Command line and jsserver.properties
    - Server and client: `-h` / `--help` / `-?`, `--version`
    - Server: Unknown args `-x -z` should print both, then not continue startup
    - Start client w/ no args, start client with host & port on command line
    - Game option defaults on command line, in `jsserver.properties`: `-oVP=t11 -oN7=t5 -oRD=y`
    - Server prop for no chat channels (`jsettlers.client.maxcreatechannels=0`):  
      Client main panel should not see channel create/join/list controls
    - Start server with prop `jsettlers.startrobots=0`:  
      Connect client and try to start a game, should see "No robots on this server" in game text area

## Database setup and Account Admins list

- SOCAccountClient with a server not using a DB:
    - To launch SOCAccountClient, use: `java -cp JSettlers.jar soc.client.SOCAccountClient yourserver.example.com 8880`
    - At connect, should see a message like "This server does not use accounts"

**Test all of the following** with supported DB types: sqlite first, mysql, postgres.
See [Database.md](Database.md) for versions to test ("JSettlers is tested with...").

- Set up a new DB with instructions from the "Database Creation" section of [Database.md](Database.md),
  including (for any 1 DB type) running `-Djsettlers.db.bcrypt.work_factor=test`
  and then specifying a non-default `jsettlers.db.bcrypt.work_factor` when running the SQL setup script
- (v2.0.00+) After setup, run SOCServer automated DB tests with `-Djsettlers.test.db=y`
- Start up SOCServer with DB parameters and `-Djsettlers.accounts.admins=adm,name2,etc`
- Run SOCAccountClient to create those admin accounts, some non-admin accounts
- Run SOCAccountClient again: Should allow only admin accounts to log in: Try a non-admin, should fail
- Run SOCPlayerClient: Nonexistent usernames with a password specified should have a pause before returning
  status from server, as if they were found but password was wrong
- SOCPlayerClient: Log in with a case-insensitive account nickname (use all-caps or all-lowercase)
- SOCPlayerClient: Log in as non-admin user, create game: `*who*` works (not an admin command),
  `*who* testgame` and `*who* *` shouldn't ; `*help*` shouldn't show any admin commands
- Test SOCServer parameter `--pw-reset username`  
  SOCPlayerClient: Log in afterwards with new password and start a game
- Server prop to require accounts (`jsettlers.accounts.required=Y`):  
  Should not allow login as nonexistent user with no password
- Server prop for games saved in DB (`jsettlers.db.save.games=Y`):  
  Play a complete game, check for results there: `select * from games;`
- Test creating as old schema (before v1.2.00) and upgrading
    - Get the old schema SQL files you'll need from the git repo by using any pre-1.2.00 release tag, for example:

          git show release-1.1.19:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1119.sql

      - Files for mysql: jsettlers-create-mysql.sql, jsettlers-tables.sql
      - For postgres: jsettlers-create-postgres.sql, jsettlers-tables.sql, jsettlers-sec-postgres.sql
      - For sqlite: Only jsettlers-tables.sql
    - Run DB setup scripts with instructions from the "Database Creation" section of [Database.md](Database.md)
      and beginning-of-file comments in jsettlers-create-mysql.sql or -postgres.sql
    - Run SOCServer with the old schema and property `-Djsettlers.accounts.admins=adm`;
      startup should print `Database schema upgrade is recommended`
    - Create an admin user named `adm` using SOCAccountClient
    - Run DB upgrade by running SOCServer with `-Djsettlers.db.upgrade_schema=Y` property
    - Run SOCServer as usual; startup should print `User database initialized`
    - Run JSettlers.jar; log in as `Adm` to test case-insensitive nicknames.  
      Make sure you can create a game, to test password encoding conversion.  
      Run the `*DBSETTINGS*` admin command to verify BCrypt password encoding is being used.

## Other misc testing

- "Replace/Take Over" on lost connection:
    - Start a game at server with player client
    - Start a second client under your IDE's debugger & join that game
    - Start game, go through initial placement and into normal game play
    - In your IDE, pause the debugged client to simulate network connection loss
    - Start a new client and connect as that same username; should allow connect after appropriate number of seconds
- Leave a practice game idle for hours, then finish it; bots should not time out or leave game
- Leave a non-practice game idle for hours; should warn 10-15 minutes before 2-hour limit,
  should let you add time in 30-minute intervals up to original limit + 30 minutes remaining
- Robot stability:
    - This test can be started and run in the background.
    - At a command line, start and run a server with 100 robot-only games:  
      `java -jar JSettlersServer-2.0.00.jar -Djsettlers.bots.botgames.total=100 -Djsettlers.bots.botgames.parallel=20 -Djsettlers.bots.fast_pause_percent=5 -Djsettlers.bots.botgames.shutdown=Y 8118 15`
    - To optionally see progress, connect to port 8118 with a client. Game numbers start at 100 and count down.
    - These games should complete in under 10 minutes
    - Once the games complete, that server will exit
    - Scroll through its output looking for exceptions
        - "force end turn" output, and occasional bad placements or bank trades, are expected and OK
        - If any exceptions occur: Debug, triage, document or correct them
- Board layout generator stability:
    - See `extraTest` section, or run as:  
      `gradle extraTest -D 'test.single=*TestBoardLayouts*' -x :extraTestPython`
- Build contents and built artifacts
    - `gradle dist` runs without errors, under gradle 4 and also gradle 5
    - Diff list of files from `gradle dist` outputs in `build/distributions/`:
        - `unzip -t jsettlers-2.*-full.zip | sort`
        - `tar tzf jsettlers-2.*-full.tar.gz | sort` (same files as above)
        - `tar tzf jsettlers-2.*-src.tar.gz | sort` (same but without *.jar)
    - Diff that list of files against previously released version's `full.tar.gz`
        - Make sure any missing/moved/removed files are deliberate (from refactoring, etc)
    - In a temp dir, do a fresh git checkout and compare contents:  
      Example if using `bash`:

            cd my_project_top_level_dir  # containing src, doc, etc
            MYTOPDIR=$(pwd)
            cd /tmp && mkdir jt && cd jt
            git clone https://github.com/jdmonin/JSettlers2.git
            cd JSettlers2
            X_IGNORES="-x .git -x build -x target -x tmp"
			diff -ur $X_IGNORES . "$MYTOPDIR" | grep ^Only  # check for missing/extra files
            diff -ur $X_IGNORES . "$MYTOPDIR"  # check for uncommitted or unpushed changes
            cd .. && rm -rf JSettlers2
            cd .. && rmdir jt


## Automated extra testing (extraTest)

A few functional tests are scripted to set up, begin, and run in the background
while you're doing other work or other testing.

Open a terminal or command prompt, go to the project's top-level directory
(containing `build.gradle`), and run:  
`gradle extraTest`

These tests will run for several minutes, and end without errors:  
`BUILD SUCCESSFUL`

The current Extra Tests are:

- Game: `TestBoardLayoutsRounds`: Board layout generator stability:
    - The board layout generator is complicated, to flexibly handle the sea scenario layouts.
      This test ensures it won't hang, time out, or crash while making a new board or resetting a board,
      by running a couple thousand rounds of a unit test.
    - When run in this mode, each round of TestBoardLayouts performs extra checks of the layout structure.
      If any layout failures occur, that's a bug to be triaged or corrected before release.
- Server: `test_startup_params.py`: Various argument/property combinations:
    - The test script should run for about two minutes, and end without errors


## Platform-specific

On most recent and less-recent OSX and Windows; oldest JRE (java 6) and a new JRE:

- Keyboard shortcuts including game-reset dialog's esc/enter keys, FaceChooserFrame arrow keys
- Sound, including 2 clients in same game for overlapping piece-place sound
- Start or join networked game
- Graphics, including scaling and antialiasing after window resize
- High-DPI support: Test layout and font appearance
    - Run as usual (auto-detect resolution) on a low-DPI and a high-DPI display if available
    - Override runs, using jvm property `-Djsettlers.uiScale=1` and again using `-Djsettlers.uiScale=2`
- Persistent user prefs (sound, auto-reject bot offer, window size)  
  Then, re-run to check default size with jvm property `-Djsettlers.debug.clear_prefs=PI_width,PI_height`
- Accessibility/High-Contrast mode
    - Test debug jvm property `-Djsettlers.uiContrastMode=light`
    - On Windows, test high-contrast dark and light themes, and high-contrast accessibility mode
    - On Windows, test debug jvm property `-Djsettlers.uiContrastMode=dark` while using a dark theme
- SQLite database setup, from instructions in [Database.md](Database.md)

## Instructions and Setup

- [Readme.md](../Readme.md), `Readme.developer.md`, [Database.md](Database.md):
  Validate all URLs, including JDBC driver downloads
- Follow server setup instructions in [Readme.md](../Readme.md)
- Set up a new DB: Covered above in "Platform-specific"


