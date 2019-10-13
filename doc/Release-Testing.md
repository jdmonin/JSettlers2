# Release Testing

When preparing to release a new version, testing should include:

## Quick tests and setup

- Before building the JARs to be tested, `git status` should have no untracked or uncommitted changes
    - Run `gradle distCheckSrcDirty` to check that and list any files with such changes
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

- Start JSettlersServer at a shell or command prompt
    - If you have a linux or windows server, use that instead of your laptop/desktop;
      on linux end the command line with ` &` to keep running in background
    - Should stay up for several days including activity (bot games)
    - v2.0.00+: Run several bot games (`-Djsettlers.bots.botgames.total=5`);
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
     - Start and begin playing a game with the Use Sea Board option
       - Place an initial settlement at a fog hex
     - Start and begin playing a game with the Fog Islands scenario
       - Place initial coastal settlements next to the fog island, with initial ships to reveal hexes from the fog
       - Keep restarting (reset the board) until you've revealed a gold hex and picked a free resource
- Client preferences
    - Auto-reject bot trade offers:
        - Practice game: Test UI's trade behavior with and without preference
        - Re-launch client, new practice game, check setting is remembered
    - Sound: See section "Platform-specific"
    - Hex Graphics Sets: Test switching between "Classic" and new-for-2.0 "Pastel":
        - All games mentioned here are Practice games, no server needed. "Start a game" here means to
          create a game, sit down, and start the game so a board will be generated.
        - For clean first run: Launch client with jvm property `-Djsettlers.debug.clear_prefs=hexGraphicsSet`
        - Start a practice game, default options (board graphics should appear as pastel)
        - Options button: [X] Hex graphics: Use Classic theme; close window instead of hit OK (board should not change)
        - Options button: [X] Hex graphics: Use Classic theme; hit OK (board should change to Classic)
        - Leave that game running, start another game
            - In New Game options: Fog scenario, 6 players, and un-check Use Classic theme
            - Create Game, start as usual
            - (Both games' boards should now be pastel)
        - Options: Change theme to Classic (Both games should change)
        - Leave running, Start another: Un-check Scenario, 6-player board, un-check Sea board (Should also be Classic)
        - Options: Un-check Use Classic (All 3 games should change to pastel)
        - Options: Use Classic (All 3 should change)
        - Close client main window: Quit all games
        - Re-launch client, without any jvm properties
        - Start game: 4 players, no scenario (should remember preference & be classic)
        - Start another: 6 players (should also be classic)
        - Options: Un-check Use Classic (Both should change)
        - Close client main window: Quit all games
        - Re-launch client
        - Start a game (should remember preference & be pastel)
- Network robustness: Client reconnect when scenario's board layout has "special situations"  
    Tests that the board layout, including potential and legal nodes and edges, is reconstructed when
    client leaves/rejoins the game. (For more info see "Layout placement rules for special situations"
    in `soc.server.SOCBoardAtServer` class javadoc.)
    - Scope:
        - Test the **Cloth Trade**, **Fog Islands**, **Through the Desert**, and **Wonders** scenarios
        - Use defaults for game options, number of players, etc
    - Test process for each scenario:
        - Start a server
        - Start 2 clients and have them join the same game, so each can leave/rejoin the game
          during the other's turn
        - Sit down 1 client at player position 0 (upper-left), and the other client at any other position.  
          (This tests more thoroughly because some board data is sent along with player 0's potentials.)
        - Lock some or all empty seats, to avoid waiting for bots
        - Before starting the game, at each client, show that player's legal and potential nodes and edges
          by entering this command in the chat text field:  
          `=*= show: all`  
          At first, only a yellow bounding box will be visible
        - Start the game (server sends board layout, begins Initial Placement)
        - Place 1 settlement and road/ship
            - For Fog Islands: Should be a coastal settlement and ship to reveal a fog hex
            - For Wonders: Have one player place next to and towards the off-limits Strait's
              colored diamonds, other player place a coastal settlement and ship towards a small island
        - At each client player:
            - Note the layout's legal and potential nodes and edges, possibly by taking a screenshot.
              (Legals are yellow, potentials are green, land hexes/nodes are red.
              For this test you don't need to know details of what each symbol means,
              as long as you can compare their patterns now and after leaving/rejoining the game.)
            - During the other player's turn to place, exit that game by closing the window
            - Rejoin the game; sit at same position
            - Show the legal and potential nodes again with `=*= show: all`
            - Compare the revealed nodes and edges to your previous screenshot or notes; should be identical
         - Finish Initial Placement and 1 or 2 rounds of normal game play
         - Again have each client player note the current legals/potentials, leave and reconnect
           during the other's turn, and compare legals/potentials using the above process
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

### Tests for each DB type

Test all of these with each supported DB type: sqlite first, mysql, postgres.
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
- Test creating as old schema (before v2.0.00 or 1.2.00) and upgrading
    - Get the old schema SQL files you'll need from the git repo by using an earlier release tag
    - Files to test upgrade from schema v1.2.00:
      - mysql:

            git show release-1.2.00:src/bin/sql/jsettlers-create-mysql.sql > ../tmp/jsettlers-create-mysql-1200.sql
            git show release-1.2.00:src/bin/sql/jsettlers-tables-mysql.sql > ../tmp/jsettlers-tables-mysql-1200.sql

      - postgres:

            git show release-1.2.00:src/bin/sql/jsettlers-create-postgres.sql > ../tmp/jsettlers-create-postgres-1200.sql
            git show release-1.2.00:src/bin/sql/jsettlers-tables-postgres.sql > ../tmp/jsettlers-tables-postgres-1200.sql
            git show release-1.2.00:src/bin/sql/jsettlers-sec-postgres.sql > ../tmp/jsettlers-sec-postgres-1200.sql

      - sqlite:

            git show release-1.2.00:src/bin/sql/jsettlers-tables-sqlite.sql > ../tmp/jsettlers-tables-sqlite-1200.sql

    - Files to test upgrade from original schema:
      - mysql:

            git show release-1.1.20:src/bin/sql/jsettlers-create-mysql.sql > ../tmp/jsettlers-create-mysql-1120.sql
            git show release-1.1.20:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1120.sql

      - postgres:

            git show release-1.1.20:src/bin/sql/jsettlers-create-postgres.sql > ../tmp/jsettlers-create-postgres-1120.sql
            git show release-1.1.20:src/bin/sql/jsettlers-sec-postgres.sql > ../tmp/jsettlers-sec-postgres-1120.sql
            git show release-1.1.20:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1120.sql

      - sqlite:

            git show release-1.1.20:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1120.sql

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

### Other Database Tests

Test these with any one DB type; sqlite may be the quickest to set up.
Start with a recently-created database with latest schema/setup scripts.

- Game results and player-account win/loss counts:
    - Start a server with db-connect properties and:
      `-Djsettlers.startrobots=0 -Djsettlers.accounts.open=Y -Djsettlers.allow.debug=Y -Djsettlers.db.save.games=Y`
    - Create 3 test-user player accounts: TU1 TU2 TU3
        - `java -cp JSettlers-2.*.jar soc.client.SOCAccountClient localhost 8880`
    - Play each game listed below, checking the DB afterwards for results
        - Players will be: The `debug` user; sometimes players with accounts in the DB
          (`TU1` etc); sometimes players without accounts (`non1` etc)
        - Hint to speed up games: The `debug` user can give a player the resources
          to win (build and upgrade to 4 cities, connected by Longest Road) with:  
          `rsrcs: 8 12 2 10 8 #0`  
          Change `#0` to their player number as needed.
        - sqlite command to check DB results after each game: Shows test-user win-loss records,
          and latest game's details if jsettlers.db.save.games=Y:  
          `sqlite3 t.sqlite3 "select nickname,games_won,games_lost from users where nickname_lc like 'tu%' order by nickname; select * from games2_players where gameid=(select max(gameid) from games2);"`
    - Games to play and win, with defaults for game options:
        - No players in db: debug, non2, non3 (let any player win)
        - Winner TU2 in db, other players aren't: debug, non2
        - Loser TU3 in db, others aren't: debug
        - Winner TU1 and 1 loser TU2 in db, other isn't: debug
        - 6-player game with winner TU1 (sits in position # 4 or 5), 2 losers in db: TU2, TU3, other isn't: debug
    - Win-loss counts in DB after those games (see SQL above) should be:  
      TU1: W 2 L 0  
      TU2: W 1 L 2  
      TU3: W 0 L 2
    - Stop and restart server, but with `-Djsettlers.db.save.games=N`
    - Re-run each of those games; server should update win-loss counts in DB, but not add any new games
    - Win-loss counts in DB after those games (see SQL above) should be:  
      TU1: W 4 L 0  
      TU2: W 2 L 4  
      TU3: W 0 L 4
- DB schema upgrade rollback/recovery
    - Get a copy of the original schema SQL file (not latest version):
        - `git show release-1.1.20:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1120.sql`
        - If not using sqlite, you'll need more sql files; search this doc for "Files to test upgrade from original schema"
    - Create a DB with that schema
        - Rough example: `java -jar JSettlersServer.jar -Djsettlers.db.jar=sqlite-jdbc.jar -Djsettlers.db.url=jdbc:sqlite:uptest.sqlite3 -Djsettlers.db.script.setup=../tmp/jsettlers-tables-1120.sql`
        - That example creates `uptest.sqlite3`
        - See [Database.md](Database.md) for instructions if needed
    - Start a server including these parameters:  
      `-Djsettlers.accounts.open=Y -Djsettlers.allow.debug=Y -Djsettlers.db.save.games=Y`
    - Create 3 users: UPTEST1 UpTest2 uptest3
        - `java -cp JSettlers-2.*.jar soc.client.SOCAccountClient localhost 8880`
    - Shut down the server
    - To temporarily prevent an upgrade to the latest schema, make a table that will conflict with the upgrade's new tables
        - SQL: `CREATE TABLE upg_tmp_games (upg_stop_field varchar(20));`
    - Run the server in DB upgrade mode
        - Use these parameters: `-Djsettlers.db.upgrade_schema=Y -Djsettlers.db.bcrypt.work_factor=9`
        - You should see output:

                User database initialized.
                *** Problem occurred during schema upgrade to v2000:
                org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (table upg_tmp_games already exists)
                
                * Will attempt to roll back to schema v1200.
                
                * All rollbacks were successful.
                
                org.sqlite.SQLiteException: (repeat of above error message)
                
                * DB schema upgrade failed. Exiting now.

    - Verify that the 3 users have been upgraded to schema v1200, gaining nickname_lc:
        - SQL: `SELECT nickname,nickname_lc FROM users WHERE nickname_lc LIKE 'uptest%' ORDER BY nickname_lc;`
    - Run the server normally, including parameters:  
	  `-Djsettlers.allow.debug=Y -Djsettlers.db.save.games=Y`
        - You should see output:

                * Schema upgrade: Beginning background tasks
                
                Schema upgrade: Encoding passwords for users
                Schema upgrade: User password encoding: Completed
                
                * Schema upgrade: Completed background tasks

    - Play and win a game named `uptestgame`
        - Log in as one of the uptest users
        - To speed up gameplay, have the `debug` user join the game and give resources to a player
        - Game results will be saved to the DB
    - Shut down the server
    - Clean up the deliberate schema-breakage so a normal schema upgrade to v2000 can succeed
        - SQL: `DROP TABLE upg_tmp_games;`
    - Run the server in DB upgrade mode
        - Use this parameter: `-Djsettlers.db.upgrade_schema=Y`
        - You should see output:  
          `DB schema upgrade was successful; some upgrade tasks will complete in the background during normal server operation. Exiting now.`
    - Start the server normally
        - No special parameters are needed
        - Shut it down after you see this output:

                * Schema upgrade: Beginning background tasks
                
                Schema upgrade: Normalizing games into games2
                Schema upgrade: Normalizing games into games2: Completed
                
                * Schema upgrade: Completed background tasks

    - Verify that `uptestgame` has been upgraded to schema v2000
        - SQL:  
          `SELECT gameid,gamename,duration_sec,winner FROM games2 WHERE gamename='uptestgame';`  
          `SELECT * from games2_players WHERE gameid=(SELECT gameid FROM games2 WHERE gamename='uptestgame');`
        - If each SQL statement shows 1 or more rows, upgrade was successful

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
- Build contents and built artifacts:
    - `gradle dist` runs without errors, under gradle 4 and also gradle 5
    - Full jar and server jar should include correct git commit id:
        - `unzip -q -c build/libs/JSettlers-*.jar META-INF/MANIFEST.MF | grep Build-Revision`
        - `unzip -q -c build/libs/JSettlersServer-*.jar META-INF/MANIFEST.MF | grep Build-Revision`
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
    - Those test-runs should include offering, counter-offering, and rejecting a trade offer,
      and auto-rejecting a bot's trade, to check visibility of Trade Panel and Message Panel text
- SQLite database setup, from instructions in [Database.md](Database.md)

## Instructions and Setup

- [Readme.md](../Readme.md), `Readme.developer.md`, [Database.md](Database.md):
  Validate all URLs, including JDBC driver downloads
- Follow server setup instructions in [Readme.md](../Readme.md)
- Set up a new DB: Covered above in "Platform-specific"


