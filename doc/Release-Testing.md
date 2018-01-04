# Release Testing

When preparing to release a new version, testing should include:

- Before building the JARs to be tested, `git status` should have no untracked or uncommitted changes
  (the `dist-src` build target also checks this)
- `gradle test` runs without failures
- Message Traffic debug prints during all tests, to help debugging if needed:  
  Run server and clients with JVM property `-Djsettlers.debug.traffic=Y`
- Basic functional tests
    - Game setup, join, and reset:
        - Create and start playing a practice game with 1 locked space & 2 bots, past initial placement
          into normal play (roll dice, etc) with default options
        - Create and start playing a practice game on the 6-player board (5 bots), with options like Roll No 7s for First 7 Turns
        - JSettlersServer.jar: Start a dedicated server on another ("remote") machine's text-only console
        - Join that remote server & play a full game, then reset board and start another game
            - `*STATS*` command should include the finished game
            - Bots should rejoin and play
        - JSettlers.jar: Start a local server and a game, start another client, join and start playing game
          (will have 2 human clients & 2 bots)
        - Ensure the 2 clients can talk to each other in the game's chat area
        - Client leaves game (not on their turn): bot should join to replace them & then plays their turn (not unresponsive)
        - New client joins and replaces bot; verify all of player info is sent
        - On own turn, leave again, bot takes over
        - Lock 1 bot seat and reset game: that seat should remain empty, no bot
    - Game play: (as debug user or in practice game)
        - Get and play all non-VP dev card types, and give 1 VP card, with debug commands

                dev: 0 playername
                ...
                dev: 4 playername
        - Road Building with 1 road left, after resource debug command to build the others

                rsrcs: 10 0 0 0 10 playername
                dev: 1 playername

          Should see "You may place your 1 remaining road." & be able to do other actions afterwards
        - 6-player board: On server game with a player and observer, request and use Special Building Phase
    - Basic GUI functions
        - Board resizes with window
        - Sound works
        - Bots' face icons match their name (Robots smarter than Droids)
    - 2 clients: While both connected to a server, start and join a chat channel and talk to each other there
- Automated tests in build.xml `test` target
- New features in this version from [Versions.md](Versions.md)
- Regression testing
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
    - Game reset voting, with: 1 human 2 bots, 2 humans 1 bot, 2 humans 0 bots:
      Humans can vote No to reject bots auto-vote Yes; test No and Yes
    - Fog Hex reveal gives resources, during initial placement and normal game play:
         - Start server with vm property: `-Djsettlers.debug.board.fog=Y`
         - Start and test a game with the Use Sea Board option; place an initial settlement at a fog hex
         - Start and test a game with the Fog Islands scenario
    - Version compatibility testing
        - Other versions to use: **1.1.06** before Game Options; **1.1.11** with 6-player board and client bugfixes;
          latest **1.x.xx**; latest **2.0.xx**
        - New client, old server
        - New server, old client
        - Test these specific things for each version:
            - With a 1.x.xx client connected to a 2.0.xx server, available new-game options
              should be the same as a 1.x.xx server (adapts to older client version)
            - Create and start playing a 4-player game, and a 6-player game; allow trading in one of them
            - In the 6-player game, request and use the Special Building Phase
            - Create and start playing a 4-player game with no options (this uses a different message type)
            - In any of those games, lock a bot seat and game reset; make sure that works
              (seatlockstate changes between 1.x.xx and 2.0.xx)
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
    - v2.0.00+: StatusMessage "status value" fallback at older versions
        - Start a 2.0.00 or newer server with `-Djsettlers.allow.debug=Y`
        - Start a 2.0.00 client with vm property `-Djsettlers.debug.traffic=Y`
        - That client's initial connection to the server should see at console: `SOCStatusMessage:sv=21`  
          (which is `SV_OK_DEBUG_MODE_ON` added in 2.0.00)
        - Start a 1.2.00 client with same vm property `-Djsettlers.debug.traffic=Y`
        - That client's initial connection should get sv == 0, should see at console: `SOCStatusMessage:status=Debugging is On`
    - v2.0.00+: SOCScenario info sync/negotiation when server and client are different versions
        - Test client newer than server:
            - Build server JAR and start a server from it  
              (or, turn off code hot-replace within IDE and start server there)
            - In `SOCScenario.initAllScenarios()`, uncomment `SC_TSTNB` and `SC_TSTNO`
            - In `version.info`, add 1 to versionnum and version (example: 2000 -> 2001, 2.0.00 -> 2.0.01)
            - Launch client (at that "new" version)
            - Click "Practice"; Dialog to make a game should see those 2 "new" scenarios
            - Quit and re-launch client
            - Connect to server, click "New Game"; the 2 new ones are unknown at server,
              should not appear in the dialog's Scenario dropdown
            - Quit client and server
        - Then, test server newer than client:
            - Build server JAR and start a server from it (at that "new" version)
            - Reset `version.info` and `SOCScenario.initAllScenarios()` to their actual versions (2001 -> 2000, re-comment, etc)
            - Launch client (at actual version)
            - Connect to server, click "New Game"; should see `SC_TSTNB` but not `SC_TSTNO`
              in the dialog's Scenario dropdown
            - Start a game using the `SC_TSTNB` scenario, begin game play
            - Launch a second client
            - Connect to server, join that game
            - Within that game, second client's "Game Info" dialog should show scenario info
    - Command line and jsserver.properties
        - Server and client: `-h` / `--help` / `-?`, `--version`
        - Server: Unknown args `-x -z` should print both, then not continue startup
        - Start client w/ no args, start client with host & port on command line
        - Game option defaults on command line, in `jsserver.properties`: `-oVP=t11 -oN7=t5 -oRD=y`
        - Server prop for no chat channels (`jsettlers.client.maxcreatechannels=0`):  
          Client main panel should not see channel create/join/list controls
        - Start server with prop `jsettlers.startrobots=0`:  
          Connect client and try to start a game, should see "No robots on this server" in game text area
- Database setup and Account Admins list
    - SOCAccountClient with a server not using a DB: At connect, should see a message like "This server does not use accounts"
        - To launch SOCAccountClient, use: `java -cp JSettlers.jar soc.client.SOCAccountClient yourserver.example.com 8880`
    - **Test all of the following** with supported DB types: sqlite first, mysql, postgres
        - See [Database.md](Database.md) for versions to test ("JSettlers is tested with...")
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
    - SOCPlayerClient: Log in as non-admin user, create game: `*who*` works (not an admin command) works,
      `*who* testgame` and `*who* *` shouldn't ; `*help*` shouldn't show any admin commands
    - Test SOCServer parameter `--pw-reset username`  
      SOCPlayerClient: Log in afterwards with new password and start a game
    - Server prop to require accounts (`jsettlers.accounts.required=Y`):  
      Should not allow login as nonexistent user with no password
    - Server prop for games saved in DB (`jsettlers.db.save.games=Y`):  
      Play a complete game, check for results there: `select * from games;`
    - Test creating as old schema (before v1.2.00) and upgrading
        - Get the old schema SQL files you'll need from the git repo by using any pre-1.2.00 release tag, for example:

              git show release-1.1.20:src/bin/sql/jsettlers-tables.sql > ../tmp/jsettlers-tables-1120.sql

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
- Other misc testing
    - "Replace/Take Over" on lost connection:
        - Start a game at server with player client
        - Start a second client under your IDE's debugger & join that game
        - Start game, go through initial placement and into normal game play
        - In your IDE, pause the debugged client to simulate network connection loss
        - Start a new client and connect as that same username; should allow after appropriate number of seconds
    - Leave a practice game idle for hours, then finish it; bots should not time out or leave game
    - Leave a non-practice game idle for hours; should warn 10-15 minutes before 2-hour limit,
      should let you add time in 30-minute intervals up to original limit + 30 minutes
- Platform-specific: Recent and less-recent OSX and Windows; oldest JRE (1.5) and new JRE
    - Keyboard shortcuts including game-reset dialog's esc/enter keys, FaceChooserFrame arrow keys
    - Sound, including 2 clients in same game for overlapping piece-place sound
    - Start or join networked game
    - Graphics, including scaling and antialiasing after window resize
    - Persistent user prefs (sound, auto-reject bot offer, window size)  
      Then, re-run to check default size with `-Djsettlers.debug.clear_prefs=PI_width,PI_height`
    - SQLite database setup, from instructions in [Database.md](Database.md)
- Instructions and Setup
    - [Readme.md](../Readme.md), `Readme.developer.md`, [Database.md](Database.md):
      Validate all URLs, including JDBC driver downloads
    - Follow server setup instructions in [Readme.md](../Readme.md)
    - Set up a new DB: Covered above in "Platform-specific"


