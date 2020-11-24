# Release Testing

When preparing to release a new version, testing should include:

## Quick tests and setup

- Before building the JARs to be tested, `git status` should have no untracked or uncommitted changes
    - Run `gradle distCheckSrcDirty` to check that and list any files with such changes
- `gradle clean test` runs without failures, under gradle 5.6 and also gradle 6.4
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
    - Start the game (will have 2 human clients & 2 bots), finish initial placement
    - Ensure the 2 clients can talk to each other in the game's chat area
    - Client leaves game (not on their turn): A bot should join, replace them, and play their entire next turn (not unresponsive)
    - Have new client join and replace bot; verify all of player info is sent
    - On own turn, leave again, bot takes over
    - Lock 1 bot seat and reset game: that seat should remain empty, no bot
    - Have a client rejoin and take over for a bot
    - Lock the only remaining bot seat (use lock button's "Marked" state, or "Locked" if client is v1.x)
      and reset game: no bots in new game, it begins immediately
- Game play: (as debug user or in practice game)
    - Get and play all non-VP dev card types, and give 1 VP card: Use debug commands

            dev: 1 playername
            dev: 2 playername
            dev: 3 playername
            dev: 5 playername
            dev: 9 playername
    - Road Building with 1 road left, after resource debug command to build the others:

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
      on linux, end the command line with ` &` to keep running in background
    - Should stay up for several days including activity (bot games)
    - Run several bot games (`-Djsettlers.bots.botgames.total=5 -Djsettlers.bots.botgames.gametypes=3`)
      - Join one as observer; pause should be shorter than normal games
      - View Game Info of each; should be a mix of 4- and 6-player, classic and sea board
- New features in previous 2 versions from [Versions.md](Versions.md)
- Each available Game Option
    - For house rule game opt "6-player board: Can Special Build only if 5 or 6 players in game",  
      also test latest server version against client v2.2.00 or older:
        - Client can create a game with this option, 4 players, on 6-player board
        - When client clicks Special Building button, server sends text explaining the house rule is active
- Basic rules and game play
    - Can build pieces by right-clicking board or with the Build Panel
    - Can trade with ports by right-clicking board or using Trade Offer Bank/Port button
    - Trade offer, rejection, counter-offer accept/rejection
    - Can play dev card before dice roll
    - Can win only on your own turn
        - This can be tested using the 6-player board's Special Building Phase
    - Move robber/steal resources
        - For these tests, can use the `debug` player and debug command `*FREEPLACE* 1`
          to quickly build players' pieces, and `dev: 9 debug` to get each Soldier card
        - Move robber to an unoccupied hex
        - Move to steal from 1 player
        - Move to a hex with 2 players' settlements, choose a player to steal from
        - Sea board:
          - Move pirate next to another player's ship to steal
          - Move next to 2 players' ships, choose a player to steal from
        - Cloth Trade scenario:
          - Move pirate, steal cloth instead of resources
          - Move pirate to a hex with 2 players' ships, choose 1, steal cloth or resources
          - Move pirate to a hex with 2 players' ships, choose 1 who has only resources not cloth;
            shouldn't be asked whether to steal cloth or resources
        - Make sure another player has Largest Army, then play enough Soldier cards to take it from them
    - Gain Longest Road/Route
        - For these tests, can use the `debug` player and debug command `*FREEPLACE* 1`
          to quickly build players' pieces and VP totals, then `rsrcs: 3 0 3 1 3 debug` to give
          resources to build the last few connecting roads/ships/last settlement the usual way
        - Situations to test:
          - Be first player to have Longest Route
          - Build roads/ships to take Longest Route from another player
          - Build settlement to split another player's Longest Route, giving a 3rd player the new Longest Route.
            (Skip this situation if testing for "move a ship".)
            If this ends the game, 3rd player should win only when their turn begins.  
            To save time, you can test with server Savegame feature enabled:  
            Copy src/test/resources/resources/savegame/reletest-longest-3p.game.json and reletest-longest-3p-sea.game.json
            to your server's configured savegame directory, then run `*LOADGAME* reletest-longest-3p`
            or `*LOADGAME* reletest-longest-3p-sea`
        - Piece types to test each situation with:
          - Build roads only
          - Build a route that has roads and ships (through a coastal settlement)
          - Move a ship to gain Longest Route
    - Can win by gaining Longest Road/Route
        - To set up for each test, can use debug command `*FREEPLACE* 1` to quickly build pieces until you have 8 VP;
          be careful to not gain longest route before the test begins
        - With 8 VP, test each item in "Gain Longest Road/Route" list above
    - Can win by gaining Largest Army
        - To set up for each test, can use debug command `*FREEPLACE* 1` to quickly build pieces until you have 8 VP,
          `dev: 9 debug` to get 2 soldier cards, play them
        - With 8 VP and playing 3rd Soldier card, test each item in "Move robber/steal resources" list above.
          When card is played, game might immediately award Largest Army and Hand Panel might show 10 VP.
          Card should fully play out (choose player, etc) before server announces game is over.
- Unprivileged info commands  
    As a non-admin non-debug user, start playing a game. These should all work:
    - `*WHO*` lists all players and observers in game
    - `*STATS*` shows game's duration ("started x minutes ago") and number of rounds, server's count of started and completed games
    - Finish the game (win or lose)
    - `*STATS*` now shows game's duration as "took x minutes", server's count of completed games has increased, player's win/loss count has increased
    - Start and finish another game
    - `*STATS*` shows server's count of started and completed games have increased, player's win/loss count has increased
- Game info sent to observer
    - Start and begin playing a game as `debug` player
    - Give another player enough Victory Point dev cards to win: `dev: 5 playername` etc
    - Start another client, join game as observer
    - Observer should see accurate stats for public parts of game (not cards in hand or true VP totals, etc)
    - Let the game end
    - Observer should see same end-of-game announcements and total VP as players, including VP totals in hand panels
- Game reset voting
    - To test process for rejecting/accepting the reset, ask for reset and answer No and Yes in each of these:
        - 1 human 2 bots
        - 2 humans 1 bot
        - 2 humans 0 bots
- Fog Hex reveal gives resources, during initial placement and normal game play:
     - Start server with vm property `-Djsettlers.debug.board.fog=Y`
     - Start and begin playing a game with the Use Sea Board option
       - Place an initial settlement at a fog hex; should receive resources from each revealed hex
     - Stop server, start it without `-Djsettlers.debug.board.fog=Y`
     - Start and begin playing a game with the Fog Islands scenario
       - Place initial coastal settlements next to the fog island, with initial ships to reveal hexes from the fog
       - Keep restarting game (reset the board) until you've revealed a gold hex and picked a free resource
- Scenario-specific behaviors:
     - Cloth Trade
       - Start a game on the 6-player board
       - Hover over village: Should show remaining cloth amount
       - Build ships to multiple villages, including 2 villages with the same dice number
           - Player receives 1 cloth when building ship to a village (establishing trade)
           - Each 2 cloth is +1 total VP
       - Move ships built elsewhere to 2 villages (will take 2 turns)
           - Cloth, VP, and "establishing trade" should work same way as building a ship next to a village
       - Make sure Robber can't be moved to the small islands
       - As game is played, make sure distribution of cloth to players is correct,
         including from 2 villages with the same dice number
       - Check for depletion of villages (should turn gray when cloth is depleted)
       - Check that when a village has 1 remaining cloth, but 2 established players,
         the board's general supply gives cloth to the 2nd player
       - Join as observer: Check general supply count, hover over villages; cloth counts should be accurate
       - Note your player's cloth total, then leave and rejoin game: Hover over villages; cloth counts should be accurate
       - Move the pirate to rob cloth from another player;
         cloth count and VP total should update accurately  
         (Before you can move the pirate, you must establish a shipping route with any village)
       - Give your player an odd number of cloth, a Soldier card, and enough VP dev cards
         to be 1 point from winning. Move the pirate to rob cloth from another player; should win game
       - Test the scenario's special win condition: Fewer than 4 villages have cloth remaining
           - Start a game which requires 20 VP to win
           - Use several human players, so no player gets to 20 VP before villages are depleted
           - This test may take a while to complete, unless you can temporarily change the source code:
               - Lower `SOCVillage.STARTING_CLOTH`
               - Increase `SOCScenario.SC_CLVI_VILLAGES_CLOTH_REMAINING_MIN`
           - Player with most VP, or most cloth if tied, should win
     - Forgotten Tribe
       - Trading ports as Special Items in player inventory
           - Add a trading port into your player's inventory
               - Either: Use debug command `*SCEN* giveport 4 0 debug`
               - Or: Build ships to a "gift" trade port (must place right away),
                 then past that to a second one (goes into inventory)
           - Leave and rejoin the game
           - Trading port should still be in inventory
           - Build another coastal settlement
           - Should now be able to play that trading port out of inventory
           - Should be able to trade that port's resources at expected ratio (not 4:1)
       - Trading ports during Special Building phase
           - Start a 6-player game
           - Sit at seat number 5 (middle left); lock seat 0 (top left)
           - Start the game; during initial placement, build a costal settlement and a ship north towards the Tribe's ports
           - Start a second client, sit at seat 3 (bottom right) to help observe and confirm turn order
           - In first client, end your turn; ask for Special Building during bot player 2's turn
           - During Special Building, build ships to one of the Tribe's ports; pick up the port and place it
           - End Special Building; next player should be number 3, not number 1
           - During all that, second client should observe same sequence of current players
       - Claiming gift Dev Cards and Ports with ship moves
           - Build ships towards a gift Dev Card (yellow diamond) and gift Trading port
           - Move a ship from elsewhere to claim the Dev Card: Should work as expected
           - On next turn, move a ship from elsewhere to claim the port: Should work as expected
           - Have observer briefly join game: Should see correct dev card count, port in new location
       - Claiming Special Victory Points (SVPs) and ship moves
           - Without using Free Placement debug mode:
           - Build ships to claim 2 SVPs (green diamonds)
           - Have observer briefly join game: Player and observer should see 2 SVPs, correct total VP amount
           - On next turn, move furthest SVP ship to an empty edge
           - Have observer briefly join game: Player and observer should still see 2 SVPs, correct total VP
           - On next turn, move other SVP ship to claim a different SVP
           - Have observer briefly join game: Player and observer should see 3 SVPs, correct total VP
           - Build another ship on an empty edge
           - On next turn, move that new ship to claim an SVP
           - Have observer briefly join game: Player and observer should see 4 SVPs, correct total VP
       - Move the Robber, then make sure Robber can't be moved back to the small islands
     - Pirate Islands and Fortresses
       - Test visibility of Legal Sea Edges (dotted lines to fortress) for all 6 players
         using both hex graphic themes (pastel & classic)
       - As player with pn=0 and as another pn: Leave and rejoin game: After sitting down, should see Legal Sea Edges
         (dotted line from main island to your player's fortress) and be able to place boats along them
       - Defeat all fortresses: Pirate Fleet should disappear
           - Start a 2-player game: Debug player, 1 bot
           - Give both players about 8 Warship cards: `dev: 9 debug`, repeat for bot name
           - Use Free Placement mode to build ships to each player's fortress: `*FREEPLACE* 1`
           - Take turns, upgrade to warships, attack fortress until both are defeated
           - Pirate Fleet should disappear
           - Play a few more rounds; should see no exceptions from bot at server console
     - Wonders
       - Player with 0 available ships (built all 15 already) can't start to build a Wonder,
         even if they have all other requirements
       - Starting a Wonder deducts 1 ship from player's available count
       - Building more levels of that Wonder doesn't deduct a ship
       - If observer joins after Wonder started, sees accurate ship count
- Client preferences
    - Auto-reject bot trade offers:
        - 6-player Practice game: Test UI's trade behavior with and without preference
        - Re-launch client, new practice game, check setting is remembered
    - Sound: See section "Platform-specific"
    - Remember face icon:
        - For clean first run: Launch client with jvm property `-Djsettlers.debug.clear_prefs=faceIcon`, exit immediately
        - Launch new client as usual, without `-Djsettlers.debug.clear_prefs=faceIcon`
          - Connect to server, click New Game
          - In New Game dialog, "Remember face icon" checkbox should be set
          - Start game, change player's icon from default, exit during initial placement
          - Start another game, sit down; player's icon should be the one just chosen
          - Exit game and client
        - Launch new client as usual
          - Connect to server, click New Game
          - In New Game dialog, "Remember face icon" checkbox should be set
          - Sit down at game; player's face icon should be non-default from previous run
          - Exit game and client
        - Launch new client
          - Connect to server, click New Game
          - In New Game dialog, clear "Remember face icon" checkbox
          - Sit down at game; player's face icon should be back to default
          - Exit game and client
        - Launch new client
          - Connect to server, click New Game
          - In New Game dialog, "Remember face icon" checkbox should be cleared; set it
          - Sit down at game; player's face icon should be previously saved non-default
          - Exit game
          - Click New Game
          - In New Game dialog, clear "Remember face icon" checkbox
          - Sit down; icon should be default
          - Exit game and client
        - Launch new client
          - Connect to server, click New Game
          - In New Game dialog, "Remember face icon" checkbox should be cleared
          - Sit down at game; player's face icon should be default
          - Start game
          - Change player's icon to a different non-default, leave game running
          - Click New Game
          - In New Game dialog, set "Remember face icon" checkbox
          - Sit down at game; player's face icon should be the one from still-running game
          - Exit games and client
        - Launch new client
          - Connect to server, click New Game
          - In New Game dialog, "Remember face icon" checkbox should be set
          - Sit down at game; player's face icon should be the different non-default one from previous run
          - Exit game and client
    - Hex Graphics Sets: Test switching between "Classic" and the default "Pastel":
        - All games mentioned here are Practice games, no server needed. "Start a game" here means to
          create a game, sit down, and start the game so a board will be generated.
        - For clean first run: Launch client with jvm property `-Djsettlers.debug.clear_prefs=hexGraphicsSet`
        - Start a practice game, default options; board graphics should appear as pastel
        - Options button: [X] Hex graphics: Use Classic theme  
          Close window instead of hit OK; board should not change
        - Options button: [X] Hex graphics: Use Classic theme  
          Hit OK; board should change to Classic
        - Leave that game running, start another game
            - In New Game options: Fog scenario, 6 players, and un-check Use Classic theme
            - Create Game, start as usual
            - Both games' boards should now be pastel
        - Options: Change theme to Classic; OK (Both games should change)
        - Leave running, Start another: Un-check Scenario, 6-player board, un-check Sea board (Should also be Classic)
        - Options: Un-check Use Classic; OK (All 3 games should change to pastel)
        - Options: Use Classic; OK (All 3 should change)
        - Close client main window: Quit all games
        - Re-launch client, without any jvm properties
        - Start practice game: 4 players, no scenario (should remember preference & be classic)
        - Start another: 6 players (should also be classic)
        - Options: Un-check Use Classic; OK (Both should change)
        - Close client main window: Quit all games
        - Re-launch client
        - Start a practice game (should remember preference & be pastel)
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
        - Place total of 1 settlement and 1 road/ship (not per player)
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
    - Versions to test against: **1.1.06** (before Game Options); **1.1.11** (has client bugfixes, 6-player board);
      **1.2.01** (newest 1.x, before Scenarios/sea boards); **2.0.00** (many message format changes, i18n)
    - New client, old server
        - If server is >= 1.1.09 but older than 1.1.19, add property at end of command line: `-Djsettlers.startrobots=5`
        - If server >= 1.1.14, also add at end of command line: `-Djsettlers.allow.debug=Y`
        - If server older than 1.1.15, also add at end of command line: `8880 99 dbu pw`
    - New server, old client
    - Test these specific things for each version:
        - Server config:
            - When testing a 2.3 or newer server, start it with prop `jsettlers.admin.welcome=hi,customized`;  
              all client versions should see that custom text when they connect
        - With an older client connected to a newer server, available new-game options
          should adapt to the older client version.  
          With a newer client connected to an older server, available new-game options
          should adapt to the older server version.  
            - This is especially visible when testing 1.x against 2.x.
            - Also test this with new client against server 1.1.07 (first version with options)
        - Create and start playing a 4-player game with No Trading option
            - Click Options button: Game options should be those chosen in this game's New Game dialog
            - Give a robot player some new dev cards: VP, playable  
              `dev: 4 botname`  
              `dev: 9 botname`
            - Have another client join (same version as first client) and take over that bot; should see dev card details correctly
        - Create and start playing a 6-player game
        - In the 6-player game, request and use the Special Building Phase
        - On a 2.x server, have 2.x client create game with a scenario (1.x can't join);
          1.x client should see it in gamelist with "(cannot join)" prefix.
        - Connect with another client (same version as first client)
            - Should see 2nd game in list with that "(cannot join)" prefix
            - Join 1st game, take over for a robot
            - Should see all info for the player (resources, cards, etc)
            - Play at least 2 rounds; trade, build something, buy and use a soldier card
            - When server >= 2.4:
                - Have an observing client join game right after a player offers a trade; should see current trade offer
                - A few turns later, have an observing client join game; shouldn't see trade offer from a previous turn
        - When testing 2.3 or newer against older than 2.3:
            - Start a game with robot player at seat number 2
            - Give dev cards to bot player:  
              `dev: 3 #2`  
              `dev: 6 #2`
            - Have second client join as observer (same version as first client)
            - Should see correct number of dev cards for bot
            - Sit down observer to take over bot's seat
            - Should see correct card types in inventory (Monopoly, University); shouldn't see any unknown cards
        - When testing a 2.x client and 1.x server: In any game, test robot seat-lock button
            - Click its lock button multiple times: Should only show Locked or Unlocked, never Marked
            - Lock a bot seat and reset the game: Seat should be empty in new game
        - When testing a 1.x client and 2.x server: In any game, test robot seat-lock button
            - Using a 2.x client, begin playing a new game
            - Mark one bot seat as "locked" and another as "marked"
            - Join with 1.x client
                - Should see marked seat as unlocked, locked as locked
                - Should be able to take over bot by sitting at "marked" seat
        - When testing new server with client 2.4.50 or newer, and older client:
            - Give Soldier dev cards to client players:  
              `dev: 9 #2` etc
            - Test robbery, with each client as victim, robber, observer
            - All clients in game should see expected results in player hand panels and game text area
            - Clients v2.4.50 or newer are sent `SOCReportRobbery` messages; older clients are sent `SOCPlayerElement` and `SOCGameServerText` instead
- Server robustness: Bot disconnect/reconnect during game start
    - Start server with vm properties: `-Djsettlers.bots.test.quit_at_joinreq=30` `-Djsettlers.debug.traffic=Y`
    - Connect and start a 6-player game
    - Bots should arrive, game should start
    - Server console should have lines like:  
      `robot 3 leaving at JoinGameRequest('g', 3): jsettlers.bots.test.quit_at_joinreq`  
      `srv.leaveConnection('robot 3') found waiting ga: 'g' (3)`  
      If not, start another game and try again
- StatusMessage "status value" fallback at older versions
    - Start a 2.0.00 or newer server with command-line arg `-Djsettlers.allow.debug=Y`
    - Start a 2.0.00 client with vm property `-Djsettlers.debug.traffic=Y`
    - That client's initial connection to the server should see at console: `SOCStatusMessage:sv=21`  
      (which is `SV_OK_DEBUG_MODE_ON` added in 2.0.00)
    - Start a 1.2.00 client with same vm property `-Djsettlers.debug.traffic=Y`
      - If needed, can download from https://github.com/jdmonin/JSettlers2/releases/tag/release-1.2.00
    - That client's initial connection should get sv == 0, should see at console: `SOCStatusMessage:status=Debugging is On`
- Game Option and Scenario info sync/negotiation when server and client are different versions/locales
    - For these tests, use these JVM parameters when launching clients:  
      `-Djsettlers.debug.traffic=Y -Djsettlers.locale=en_US`  
      Message traffic will be shown in the terminal/client output.
    - Test client newer than server:
        - Build server JAR as usual, make temp copy of it, and start the temp copy (which has the actual current version number)
        - In `SOCScenario.initAllScenarios()`, uncomment `SC_TSTNC` "New: v+1 back-compat" and `SC_TSTNO` "New: v+1 only"  
          Update their version parameters to current versionnum and current + 1. Example:  
          `("SC_TSTNC", 2000, 2001, ...)`  
          `("SC_TSTNO", 2001, 2001, ...)`
        - In `SOCGameOptionSet.getAllKnownOptions()`, scroll to the end and uncomment `DEBUGBOOL` "Test option bool".
          Update its version parameters to current versionnum and current + 1. Example:  
          `("DEBUGBOOL", 2000, 2001, false, ...)`
        - In `src/main/resources/resources/version.info`, add 1 to versionnum and version. Example: 2000 -> 2001, 2.0.00 -> 2.0.01
        - Build and launch client (at that "new" version), don't connect to server
        - Click "Practice"; dialog's game options should include DEBUGBOOL,
          Scenario dropdown should include those 2 "new" scenarios
        - Quit and re-launch client, connect to server
        - Message traffic should include:
          - Client's `SOCGameOptionGetInfos` for DEBUGBOOL
          - Server response: `SOCGameOptionInfo` for DEBUGBOOL, + 1 more Option Info to end that short list
        - Click "New Game"
        - In message traffic, should see a `SOCScenarioInfo` with `lastModVers=MARKER_KEY_UNKNOWN` for each of the 2 new scenarios, + 1 more to end the list of Infos
        - The "new" items are unknown at server: New Game dialog shouldn't have DEBUGBOOL,
          its Scenario dropdown shouldn't have the 2 test scenarios
        - Quit client and server
    - Then, test server newer than client:
        - Temporarily "localize" the test option and scenarios by adding to
          `src/main/resources/resources/strings/server/toClient_es.properties`:  
          `gameopt.DEBUGBOOL = test debugbool localized-es`  
          `gamescen.SC_TSTNC.n = test-localizedname-es`  
        - Build server JAR and start a server from it (has the "new" version number);  
          use `gradle assemble` here to skip the usual unit tests
        - Reset `version.info`, `toClient_es.properties`, `SOCGameOptionSet.getAllKnownOptions()`,
          and `SOCScenario.initAllScenarios()` to their actual versions (2001 -> 2000, re-comment, etc)
        - Build and launch client (at actual version)
        - Connect to "newer" server
        - Message traffic should include:
          - Client's generic `SOCGameOptionGetInfos` asking if any changes
          - Server response: `SOCGameOptionInfo` for DEBUGBOOL, + 1 more Option Info to end that short list
        - Click "New Game"
        - In message traffic, should see a `SOCScenarioInfo` for each of the 2 new scenarios, + 1 more to end the list of Infos
        - Dialog should show DEBUGBOOL option. Should see `SC_TSTNC` but not `SC_TSTNO` in Scenario dropdown
        - Start a game using `SC_TSTNC` scenario, begin game play
        - Launch a 2nd client, connect to server
        - Click "Game Info"
        - In message traffic, should see only 1 `SOCScenarioInfo`, with that game's scenario
        - Game Info dialog should show scenario's name and info
        - Quit & re-launch 2nd client, connect to server
        - Join that game
        - In message traffic, should see only 1 `SOCScenarioInfo`, with that game's scenario
        - Within game, second client's "Options" dialog should show scenario info
        - Quit 2nd client. Keep server and 1st client running
    - Test i18n (server still newer than client):
        - Launch another client, with a locale: `-Djsettlers.debug.traffic=Y -Djsettlers.locale=es`
        - In message traffic, should see a `SOCGameOptionInfo` for DEBUGBOOL with "localized" name
        - In that client, click "Game Info"
        - In message traffic, should see only 1 `SOCScenarioInfo`, with that game's SC_TSTNC scenario
        - Game Info dialog should show scenario's info and "localized" name
        - Quit and re-launch that client
        - Connect to server, click "New Game"
        - In message traffic, should see:
          - a `SOCScenarioInfo` for each of the 2 new scenarios (SC_TSTNC, SC_TSTNO); SC_TSTNC name should be the localized one
          - `SOCLocalizedStrings:type=S` with all scenario texts except SC_TSTNC, SC_TSTNO
          - 1 more `SOCScenarioInfo` to end the list of Infos
        - Dialog should show "localized" DEBUGBOOL game option. Scenario dropdown should show all scenarios with localized text
        - Cancel out of New Game dialog
        - Quit clients and server
- Third-party Game Option negotiation
    - Test when only client knows a 3P game opt:
        - Start a client with JVM parameter `-Djsettlers.debug.client.gameopt3p=xyz`
        - Click Practice button; in Practice Game dialog, should see and set checkbox for game option "Client test 3p option XYZ"
        - Start the practice game
        - In game window, click "Options" button; game opt XYZ should be set
        - Start a server without that `gameopt3p` param
        - Connect from that client
        - Click New Game button; New Game dialog shouldn't have game opt "Client test 3p option XYZ"
        - Quit that client and server
    - Test when client and server both know it:
        - Start a server with JVM param `-Djsettlers.debug.server.gameopt3p=xyz`
        - Start a client with JVM param `-Djsettlers.debug.client.gameopt3p=xyz`
        - Connect from that client
        - Click New Game; in dialog, should see and set checkbox for "Server test 3p option XYZ"
        - Start game
        - In game window, click "Options" button; game opt XYZ should be set
        - Under your IDE's debugger, start another client with same JVM param `-Djsettlers.debug.client.gameopt3p=xyz`
        - Connect
        - Double-click and join game, take over for a robot
        - In game window, click "Options" button; game opt XYZ should be set
        - In your IDE, pause the debugged client to simulate network connection loss
        - Start a new client using same JVM param as paused one, and connect as that same username
        - Double-click game name: Should allow rejoin after appropriate number of seconds
        - To clean up, terminate the paused debugged client
    - Test when only server knows it:
        - Start another client without that `gameopt3p` param
        - Connect
        - In list of games, the game with option XYZ should show "(cannot join)"
        - Click "Game Info"; popup should show "This game does not use options"
        - Double-click game, popup should show message "Cannot join"
        - Double-click again, popup should show message "Cannot join ... This client does not have required feature(s): com.example.js.XYZ"
    - Quit all clients and server
- i18n/Localization
    - For these tests, temporarily "un-localize" SC_FOG scenario, SC_TTD description by commenting out 3 lines in `src/main/resources/resources/strings/server/toClient_es.properties`:  

          # gamescen.SC_FOG.n = ...
          # gamescen.SC_FOG.d = ...
          ...
          gamescen.SC_TTD.n = ...
          # gamescen.SC_TTD.d = ...

    - 3 rounds, to test with clients in english (`en_US`), spanish (`es`), and your computer's default locale:  
      Launch each client with specified locale by using a JVM parameter value like: `-Djsettlers.locale=es`
    - If client's default locale is `en_US` or `es`, can combine that testing round with "default locale" round
    - If other languages/locales are later added, don't need to do more rounds of testing for them;
      the 3 rounds cover english (the fallback locale), non-english, and client's default locale
    - Reminder: To show a debug trace of network message traffic in the terminal/client output,
      also use JVM param `-Djsettlers.debug.traffic=Y`

    For each of those rounds of testing, all these items should appear in the expected language/locale:

    - Client user interface
      - Initial connect window (welcome text, buttons, version and build-number label)
      - Main window
      - Game window (labels, buttons, resource-name labels, tooltips on item-count squares)
      - Dialogs (discard, year of plenty, VP card, etc)
        - Debug commands to get Year of Plenty, Monopoly, Soldier, and a VP card:  
          `dev: 2 debug`  `dev: 3 debug`  `dev: 9 debug`  `dev: 4 debug`
    - Text from server (in top center pane of game window)
      - Start a game with at least 1 bot. Near top of server text, should see localization of: "Fetching a robot player..."
    - Game options
      - Launch client with the round's locale, connect to server
        - In message traffic, should see `SOCLocalizedStrings:type=O` with text for every game option (except english client)
        - Click New Game button
        - New Game dialog: All game options and client prefs should be localized
        - Create the game
      - Launch other-locale client
        - In that client, click Game Info button
        - Game Info dialog: All game options and client prefs should be localized
    - Game scenarios
      - Launch client with the round's locale, connect to server
        - Click New Game button
        - In message traffic, should see `SOCLocalizedStrings:type=S` with text for every scenario (except english client)
          - Should not see SC_FOG at all, or SC_TTD description, because of the temporary "un-localization"
        - New Game dialog:
          - All scenarios in dropdown should be localized except SC_FOG (Fog Islands)
          - Select localized name of Through The Desert (SC_TTD)
          - Click Scenario Info; description should be unlocalized
        - Start a new game for each of these 3 scenarios:
          - Fog Islands (unlocalized)
          - Through The Desert (localized title only)
          - Wonders (is always localized)
      - Launch other-locale client
        - In that client, click Game Info button for each of those 3 games
          - Game Info dialog: Click Scenario Info button: Except Fog Islands,
            game's scenario info should be localized as expected
          - In message traffic, should see `SOCLocalizedStrings:type=S` with text for only that game's scenario
            (except for english client), or for Fog Islands, `SC_FOG|K` as visible part of the marker for "unknown"
        - Join each of those 3 games
          - In message traffic, shouldn't see another `SOCLocalizedStrings:type=S`, because server tracks already-sent ones
        - Re-launch client, to clear that server-side and client-side tracking
        - Join each of those 3 games
          - Popup when joining, or game Options button: Scenario info should be localized same way Game Info dialog was
- Client Feature handling
    - For human players:
        - Start a server (dedicated or client-hosted)
    	- Launch a pair of SOCPlayerClients which report limited features, using vm property `-Djsettlers.debug.client.features=;6pl;sb;`
    	  and connect to server. Don't give a Nickname or create any game from these clients.  
    	  (Using 2 such clients lets us test more than the code which handles the server's first limited client.)
    	- Launch a standard client, connect to server, create a game having any Scenario (New Shores, etc)
    	- Limited client pair's game list should show that game as "(cannot join)"
    	- Launch another pair of SOCPlayerClients which report no features, using vm property `-Djsettlers.debug.client.features=`
          (empty value) and connect to server
        - In each client of that second limited pair, to authenticate, give a Nickname and create a classic 4-player game on the server.
	      Don't need to sit down. Leave those new games (close their windows) to delete them.
    	- In standard client, create a game having 6 players but no scenario
        - First pair of limited clients should connect to that game
        - Second pair of limited clients' game list should show that game as "(cannot join)"
        - In one of the second pair, double-click that game in game list; should show a popup "Client is incompatible with features of this game".  
          Double-click game again; should try to join, then show a popup with server's reply naming the missing required feature: `6pl`
    - For reconnecting disconnected clients:
        - Start a server without any options
        - Start a standard client under your IDE's debugger, connect to server
        - Create & start 3 games (against bots):
        	- standard 4-player (no options)
        	- on sea board
        	- with any Scenario
        - In each game, finish initial placement and begin normal game play
        - In your IDE, pause the debugged client to simulate network connection loss
        - Start a new client using vm property `-Djsettlers.debug.client.features=;6pl;sb;` and connect as that same username
		- In the new client, double-click the standard or non-scenario sea game to rejoin
        - Should allow connect after appropriate number of seconds, and automatically rejoin the first 2 games but
		  not the game with scenario
        - Game with scenario should disappear from game list, because there were no other human players
    - For standalone/third-party robot clients, which server invites to games:
        - Start a server which expects third-party bots, with these command-line parameters:  
          `-Djsettlers.bots.cookie=foo  -Djsettlers.bots.percent3p=50`
        - Start the `soc.robot.sample3p.Sample3PClient` "third-party" bot, which is limited to not use the Game Scenarios client feature, with these command-line parameters:  
          `localhost 8880 samplebot1 x foo`
        - Start another Sample3PClient:  
          `localhost 8880 samplebot2 x foo`
        - Launch a standard client, connect to server
        - Create and start a 4-player game: Some samplebots should join (no features required) along with the built-in bots
        - Create and start a 6-player game: Some samplebots should join (requires a feature which they have) along with the built-in bots
        - Create and start a game having any Scenario (New Shores, etc): No samplebots should join, only built-in bots
        - Quit the standard client and stop the server
        - Start a server with third-party bots and no built-in bots, with these command-line parameters:  
          `-Djsettlers.bots.percent3p=50  -Djsettlers.startrobots=0  -Djsettlers.bots.start3p=2,soc.robot.sample3p.Sample3PClient`
        - Server should automatically start sample bots, joining as "extrabot 1" and "extrabot 2"
        - Launch a standard client, connect to server
        - Create and start a game having any Scenario: No extrabots should join, server should tell game-starting client to lock all empty seats
        - Start a second standard client, connect, join that game and sit down
        - Start that game (with the two human players)
        - After initial placement, have one player leave
        - Server should tell game it can't find a robot
    - Game Option negotiation when connecting client has limited features:
        - Start a standard server using its jar (not the IDE)
        - Start a client, limited by using vm property `-Djsettlers.debug.client.features=;6pl;` and connect
	  - Click New Game; dialog shouldn't show scenarios or sea board game options,
	    should show options related to 6-player board; max players dropdown should have range 2 - 6
          - Client's console (traffic debug trace) should show GameOptionInfo messages for the unsupported game options
          - Start a 6-player game, bots should join and play as usual
          - During initial placement, exit the client
        - Start a client, limited by using vm property `-Djsettlers.debug.client.features=` and connect
	  - Click New Game; dialog shouldn't show scenarios or sea board game options
	    or 6-player board options; max players dropdown should have range 2 - 4
          - Client's console (traffic debug trace) should show GameOptionInfo messages for the unsupported game options
          - Start a 4-player game, bots should join and play as usual
          - During initial placement, exit the client
        - In `src/main/resources/resources/version.info`, add 1 to versionnum and version. Example: 2400 -> 2401, 2.4.00 -> 2.4.01
        - Repeat those 2 client tests with client at that "new" version; should behave the same as above
        - Reset `version.info` to the actual versions (2401 -> 2400, etc)
        - Repeat those 2 client tests with previous release's client jar (2.3.00); should behave the same as above
        - Exit server
    - Game Option negotiation when server is limiting game types:
        - Test once with each combination of the 2 server properties:
            - `-Djsettlers.game.disallow.6player=Y`
            - `-Djsettlers.game.disallow.sea_board=Y`
            - `-Djsettlers.game.disallow.6player=Y -Djsettlers.game.disallow.sea_board=Y`
        - Start server with property being tested
        - Connect a client (any version)
        - Client's console (traffic debug trace) should show GameOptionInfo messages for game options related to the disallowed game type
        - In New Game dialog, shouldn't see game options related to the disallowed game type
            - `6player`: Use 6-player Board; Max Players 5 or 6
            - `sea_board`: Use sea Board; Scenario
        - Create and start playing a game, past initial placement
        - Connect another client (any version)
        - Game info for the game in progress should show correct options
        - Should be able to join and take over for a bot
        - Play at least 1 full turn
        - Optional: Connect with a client which has limited features
            - Start and connect with a client using a vm property value like `-Djsettlers.debug.client.features=;6pl;` (6-player but not sea board) or `=;sb;` (sea board but not 6-player)
            - In New Game dialog, shouldn't see game options related to the disallowed game type or missing client feature(s)
        - Exit clients and server
- Saving and loading games at server
    - Basics
        - Start server with debug user enabled, but not savegame feature: command-line arg `-Djsettlers.allow.debug=Y`
          - Log in as debug user, start a game
          - Try command `*SAVEGAME* tmp`
          - Should fail with a message like: "SAVEGAME is disabled: Must set jsettlers.savegame.dir property"
          - Shut down the server
        - Saving basics
          - Start server with debug user and savegame: Use command-line property to set path to save-dir, like  
            `-Djsettlers.savegame.dir=/tmp/jsgame`
          - Log in as debug user, start a game, play past initial placement
          - Try command `*SAVEGAME* tmp`
          - If that directory doesn't exist, should fail with a message like: "savegame.dir not found: /tmp/jsgame"
          - Make the directory
          - Again try command `*SAVEGAME* tmp`
          - Should succeed with a message like: "Saved game to tmp.game.json"
          - Again try command `*SAVEGAME* tmp`
          - Should fail with a message like: "Game file already exists: Add -f flag to force, or use a different name"
          - Again try command, adding that flag: `*SAVEGAME* -f tmp`
          - Should succeed
          - Run `*STATS*` command, note the game duration ("game started 4 minutes ago")
          - Make and start a new game
          - Try to save during initial placement: Should fail with a message like: "Must finish initial placement before saving"
        - Loading basics
          - As debug user in any game, run command `*LOADGAME* tmp`
          - Game and its window should get created
            - Debug user should be sitting at a player position
            - Bots should be sitting at bot player positions
            - If loaded game would duplicate name of a game already on server, should have a numeric suffix like "-2"
          - In loaded game, run `*STATS*`; duration should match what's noted in previous test
          - In loaded game, run `*LOADGAME* tmp` again
          - Should create another game with a numeric suffix like "-2" or "-3"
          - Run `*RESUMEGAME*` to resume game play; should do so
          - Play 2 rounds; bots and human player actions should function normally
          - Close all loaded games' windows
    - Loading and resuming games
        - Second human client should be able to sit, taking over a robot position, and have debug user resume game as usual
        - Save and then load a game containing a human player who's connected to server but not part of the resumed game.  
          When resuming that game, server shouldn't send that client any messages, but instead should get a bot to sit at their seat
        - Load a game and have a second human player also sit down. Resume game. Have debug player leave; play should continue for human player still in game
        - Save a 6-player game where debug isn't current player, has Asked to Special Build.  
          Load; when joining, debug player's game window should indicate wants to special build
        - Can load and start game which doesn't include debug player
          - Edit a saved game file to change player name from "debug"
          - Load that game
          - Should see "Take Over" buttons for every occupied player seat (bot or human)
          - Sit down as current player, make sure resumes properly
          - Re-test, sitting as non-current player
          - Re-test, resume without sitting: Should resume as robots-only game
          - Exit robots-only game: Should continue running, not be deleted; should be able to rejoin as observer
    - Saved bot properties
        - Save a game having no bots, mix of smart/fast bots, one with at least 1 Sample3PClient
        - For each of those, examine the players in the save file for correct values for: isRobot, isBuiltInRobot, isRobotWithSmartStrategy, and (for Sample3PClient) `"robot3rdPartyBrainClass": "soc.robot.sample3p.Sample3PClient"`
    - Loading sample savegames from unit tests
        - Copy \*.game.json (except bad-\*) from `src/test/resources/resources/savegame/` to your test server's savegame dir
        - Each one should load without error, and resume without error (except "classic-over")
        - "classic-botturn" should have bots playing in the upper-right and lower-right seats, even though those seats are marked/locked
    - Server config options/properties
        - Start server with savegame, but not debug user or Admin Users list:  
            `-Djsettlers.savegame.dir=/tmp/jsgame`
          - Startup should fail with a message like: "Config: jsettlers.savegame.dir requires debug user or jsettlers.accounts.admins"
        - Start server with savegame, database, and Admin Users list, but not debug user:  
            `-Djsettlers.savegame.dir=/tmp/jsgame -Djsettlers.accounts.admins=adm,name2`
          - Startup should succeed
          - Log in as admin user `adm` or `name2`
          - Should be able to save, load, and resume games
- Command line and jsserver.properties
    - Server and client: `-h` / `--help` / `-?`, `--version`
    - Server: Unknown args `-x -z` should print both, then not continue startup
    - Start client w/ no args, start client with host & port on command line
    - Game option default values
        - On command line: `-oVP=t11 -oN7=t5 -oRD=y`
        - In `jsserver.properties`:

              jsettlers.gameopt.VP=t11
              jsettlers.gameopt.N7=t5
              jsettlers.gameopt.RD=y

    - Server prop for no chat channels (`jsettlers.client.maxcreatechannels=0`):  
      Client main panel should not see channel create/join/list controls
    - Start server with prop `jsettlers.startrobots=0`:  
      Connect client, create and try to start a game;  
      should see "No robots on this server" in game text area
    - Start server with prop `jsettlers.startrobots=1`:  
      Connect client, create and try to start a game;  
      should see "Not enough robots to fill all the seats. Lock some seats. Only 1 robot is available"
    - Start server with prop `jsettlers.stats.file.name=/tmp/stats.txt`:  
      After 60 minutes, server should write `*STATS*` output to that file

## Database setup and Account Admins list

### Tests with no DB

- SOCAccountClient with a server not using a DB:
    - To launch SOCAccountClient, use: `java -cp JSettlers.jar soc.client.SOCAccountClient yourserver.example.com 8880`
    - At connect, should see a message like "This server does not use accounts"

### Tests for each DB type

Test all of these with each supported DB type: sqlite first, mariadb, mysql, postgres.
See [Database.md](Database.md) for versions to test ("JSettlers is tested with...").

- Set up a new DB with instructions from the "Database Creation" section of [Database.md](Database.md),
  including (for any 1 DB type) running `-Djsettlers.db.bcrypt.work_factor=test`
  and then specifying a non-default `jsettlers.db.bcrypt.work_factor` when running the SQL setup script
- After setup, run SOCServer automated DB tests with `-Djsettlers.test.db=y`
- Start up SOCServer with DB parameters and `-Djsettlers.accounts.admins=adm,name2,etc`
- Run SOCAccountClient to create those admin accounts, some non-admin accounts
- Run SOCAccountClient again: Should allow only admin accounts to log in: Try a non-admin, should fail
- Run SOCPlayerClient: Nonexistent usernames with a password specified should have a pause before returning
  status from server, as if they were found but password was wrong
- SOCPlayerClient: Log in with a case-insensitive account nickname (use all-caps or all-lowercase)
- Test SOCServer parameter `--pw-reset username`  
  SOCPlayerClient: Log in afterwards with new password and start a game
- Server prop to require accounts (`jsettlers.accounts.required=Y`):  
  Should not allow login as nonexistent user with no password
- Server prop for games saved in DB (`jsettlers.db.save.games=Y`):  
  Play a complete game, check for results there: `select * from games2;`
- Test creating as old schema (before v2.0.00 or 1.2.00) and upgrading
    - Get the old schema SQL files you'll need from the git repo by using an earlier release tag
      - Files to test upgrade from schema v1.2.00:
        - mariadb/mysql:

              git show release-1.2.00:src/bin/sql/jsettlers-create-mysql.sql > ../tmp/jsettlers-create-mysql-1200.sql
              git show release-1.2.00:src/bin/sql/jsettlers-tables-mysql.sql > ../tmp/jsettlers-tables-mysql-1200.sql

        - postgres:

              git show release-1.2.00:src/bin/sql/jsettlers-create-postgres.sql > ../tmp/jsettlers-create-postgres-1200.sql
              git show release-1.2.00:src/bin/sql/jsettlers-tables-postgres.sql > ../tmp/jsettlers-tables-postgres-1200.sql
              git show release-1.2.00:src/bin/sql/jsettlers-sec-postgres.sql > ../tmp/jsettlers-sec-postgres-1200.sql

        - sqlite:

              git show release-1.2.00:src/bin/sql/jsettlers-tables-sqlite.sql > ../tmp/jsettlers-tables-sqlite-1200.sql

      - Files to test upgrade from original schema:
        - mariadb/mysql:

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
      (which might have slightly different instructions for the old schema version)
    - Run SOCServer with the old schema and property `-Djsettlers.accounts.admins=adm`;
      startup should print `Database schema upgrade is recommended`
    - Create an admin user named `adm` using SOCAccountClient
    - Run DB upgrade by running SOCServer with `-Djsettlers.db.upgrade_schema=Y` property
      - postgres: Test at least once with an empty games table, at least once with some games saved there
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
- Admin commands
    - Be sure server is started with savegame feature: Use command-line property to set path to save-dir, like  
      `-Djsettlers.savegame.dir=/tmp/jsgame`
    - SOCPlayerClient: Log in as non-admin user, create game
        - `*who*` works (not an admin command)
        - `*who* testgame` and `*who* *` shouldn't work
        - `*help*` shouldn't show any admin commands
        - In a different game where user is observing (ongoing, past initial placement),
          shouldn't be able to use commands or chat
    - SOCPlayerClient: Log in as admin user, join an ongoing game but don't sit down
        - `*who* testgame` and `*who* *` should work
        - `*help*` should show admin commands
        - Should be able to chat in game
    - As an admin user, save and load games
        - Start a game, play past initial placement
        - Try command `*SAVEGAME* tmp`
        - Should succeed with a message like: "Saved game to tmp.game.json"
        - As admin user in any other game, run command `*LOADGAME* tmp`
        - Should succeed and load game into a new window
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

- Client/player nickname and game/channel names:
    - When entering your nickname or starting a game and a channel, check for enforcement and a helpful error message:
        - Name can't be entirely digits (is OK for channel)
        - Name can't contain `,` or `|`
        - Game name can't start with `?`
- "Replace/Take Over" on lost connection:
    - Start a game at server with player client
    - Start a second client under your IDE's debugger & join that game
    - Start game, go through initial placement and into normal game play
    - Buy at least 1 dev card, don't use it
    - Note your player's "private info": Resource counts, dev card inventory, etc
    - In your IDE, pause the debugged client to simulate network connection loss
    - Start a new client and connect as that same username
      - Should allow connect after appropriate number of seconds
      - Player's private info should be correct
- Leave a practice game idle for hours, then finish it; bots should not time out or leave game
- Leave a non-practice game idle for hours; should warn 10-15 minutes before 2-hour limit,
  should let you add time in 30-minute intervals up to original limit + 30 minutes remaining
- Practice Games vs Server connection:
    - Launch the player client and start a practice game (past end of initial placement)
    - Connect to a server, change client's nickname from "Player", start or join a game there
    - In practice game, should still be able to accept/reject trade offers from bots
    - Should be able to create and start playing a new practice game
    - In non-practice game, note the current player
    - Stop the server
    - Non-practice game should announce disconnect but still show current player
    - Practice games should continue
- Robot stability:
    - This test can be started and run in the background.
    - At a command line, start and run a server with 100 robot-only games:  
      `java -jar JSettlersServer-2.*.jar -Djsettlers.bots.botgames.total=100 -Djsettlers.bots.botgames.parallel=20 -Djsettlers.bots.fast_pause_percent=5 -Djsettlers.bots.botgames.gametypes=3 -Djsettlers.bots.botgames.shutdown=Y 8118 15`
    - To optionally see progress, connect to port 8118 with a client. Game numbers start at 100 and count down.
    - These games should complete in under 10 minutes
    - Once the games complete, that server will exit
    - Scroll through its output looking for exceptions
        - "force end turn" output, and occasional bad placements or bank trades, are expected and OK
        - If any exceptions occur: Debug, triage, document or correct them
- Board layout generator stability:
    - See `TestBoardLayoutsRounds` in "extraTest" section
- Build contents and built artifacts:
    - `gradle dist` runs without errors or unusual warnings, under gradle 5.6 and also gradle 6.4
    - Full jar and server jar manifests should include correct JSettlers version and git commit id:
        - `unzip -q -c build/libs/JSettlers-*.jar META-INF/MANIFEST.MF | grep 'Build-Revision\|Implementation-Version'`
        - `unzip -q -c build/libs/JSettlersServer-*.jar META-INF/MANIFEST.MF | grep 'Build-Revision\|Implementation-Version'`
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

These tests will run for several minutes, and should end without errors:  
`BUILD SUCCESSFUL`

The current Extra Tests are:

- Game: `TestBoardLayoutsRounds`: Board layout generator stability:
    - The board layout generator is complicated, to flexibly handle the sea scenario layouts.
      This test ensures it won't hang, time out, or crash while making a new board or resetting a board,
      by running a couple thousand rounds of a unit test.
    - When run in this mode, each round of TestBoardLayouts performs extra checks of the layout structure.
      If any layout failures occur, that's a bug to be triaged or corrected before release.
    - Command to run this test by itself:  
      `gradle extraTest -D 'test.single=*TestBoardLayouts*' -x :extraTestPython`
- Server: `test_startup_params.py`: Various argument/property combinations:
    - The test script should run for about two minutes, and end without errors


## Platform-specific

On most recent and less-recent OSX and Windows; oldest JRE (java 7) and a new JRE/JDK:  
(Note: Java 7 runs on Win XP and higher; can download installer from https://jdk.java.net/ )

- Dialog keyboard shortcuts, including New Game and Game Reset dialogs' esc/enter keys, FaceChooserFrame arrow keys
- Hotkey shortcuts
    - Generic modifier key: Ctrl on all platforms (Ctrl-R, Ctrl-D, etc)
    - MacOSX or Windows: also test modifier Cmd or Alt
    - Roll and Done buttons: R, D + modifier
    - Accept, Reject, Counter trade offer when just one is visible: A, J, C + modifier
    - Click in chat text input field
      - Try Ctrl-A, Ctrl-C, Ctrl-V (or Cmd on MacOSX); should be Select All, Copy, Paste as usual
      - With another client, join the game and make a trade offer
      - In first client, enter text into chat but don't select it
      - Ctrl-A (or Cmd) should Select All
      - Ctrl-A again should Accept the sole visible trade offer, since chat input's text is all selected
      - Clear out the chat text input, or hit Enter to send it
      - With that other client, make a trade offer
      - Ctrl-A should Accept the sole visible trade offer, since chat input contains no text
      - Exit out of other client
    - Send a few lines of chat text
    - Right-click in chat text output area, server text area (or Ctrl-click on MacOSX);
      should be able to Select All, Copy to clipboard
- Sound, including 2 clients in same game for overlapping piece-place sound
- Start, join networked games
- Graphics, including scaling and antialiasing after window resize
- High-DPI support
    - Test runs:
      - Run as usual (auto-detect resolution) on a low-DPI and a high-DPI display if available
      - Override default using jvm property `-Djsettlers.uiScale=2`
      - Override default using jvm property `-Djsettlers.uiScale=1`
    - Things to check on each run:
      - Font appearance and size
      - Dialog layouts
        - Main panel after connect
        - Game window, especially player SOCHandPanels
        - Discard dialog: Per-resource color squares: Square size, font size in square
      - Resize game window sightly, to save preference for next run
    - Quick re-test with "Force UI scale" user pref unset, set to 1, set to 2;
      make sure this pref's checkbox can be cleared
- Persistent user prefs (sound, auto-reject bot offer, window size, hex graphics set, face icon)  
  Then, re-run to check default size, with jvm property `-Djsettlers.debug.clear_prefs=PI_width,PI_height`
- Accessibility/High-Contrast mode
    - Test debug jvm property `-Djsettlers.uiContrastMode=light`
    - On Windows, test high-contrast dark and light themes, and high-contrast accessibility mode
    - On Windows, test debug jvm property `-Djsettlers.uiContrastMode=dark` while using a dark theme
    - Those test-runs should include:
      - Move robber to hex with 2 opponents, choose a player to steal from
      - Offer, counter-offer, and reject trade offers, and auto-reject a bot's trade,  
        check visibility of Trade Panel and Message Panel text
      - Gain Largest Army or Longest Road, check visibility of those labels
- DB testing: SQLite
    - SQLite database setup, from instructions in [Database.md](Database.md)
    - Start dedicated server using that SQLite database, including command-line param `-Djsettlers.db.save.games=Y`
    - Create a user, using SOCAccountClient directions in `Database.md`
    - Connect and play a complete game
    - Shut down server
    - Check game stats with a SQLite browser

          select * from games2;
          select * from games2_players;
          select games_won, games_lost from users;

## Instructions and Setup

- [Readme.md](../Readme.md), [Readme.developer.md](Readme.developer.md), [Database.md](Database.md), and this file:  
  Validate all URLs, including JDBC driver downloads
- Follow server setup instructions in [Readme.md](../Readme.md)
- Set up a new DB: Covered above in "Platform-specific"


