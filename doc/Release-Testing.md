# Release Testing

When preparing to release a new version, testing should include:

## Quick tests and setup

- Before building the JARs to be tested, `git status` should have no untracked or uncommitted changes
    - Run `gradle distCheckSrcDirty` to check that and list any files with such changes
- `gradle clean test` runs without failures, under gradle 6.9.x and 7.5.x
- These should print the expected version and build number:
    - `java -jar build/libs/JSettlers-2.*.jar --version`
    - `java -jar build/libs/JSettlersServer-2.*.jar --version`
- Message Traffic debug prints during all tests, to help debugging if needed:  
  Run server and clients with JVM property `-Djsettlers.debug.traffic=Y`

## Basic functional tests

- Game setup, join, and reset:
    - Create and start playing a practice game with 1 locked space & 2 bots, past initial placement
      into normal play (roll dice, etc) with default options
        - During initial placement, cancel and re-place a settlement
            - That settlement's visual highlight for "latest placement" should disappear along with the settlement
            - If second settlement is canceled, game should return gained resources to the bank
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
    - Server shouldn't recap the reset game's chat to current players (they already have that chat text in their windows)
    - Have a client rejoin and take over for a bot
    - Joining client should see chat recap
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

### Setup

- Start JSettlersServer at a shell or command prompt
    - If you have a linux or windows server, use that instead of your laptop/desktop;
      on linux, end the command line with ` &` to keep running in background
    - Should stay up for several days including activity (bot games)
    - Run several bot games (`-Djsettlers.bots.botgames.total=5 -Djsettlers.bots.botgames.gametypes=3`)
      - Join one as observer; pause should be shorter than normal games
      - View Game Info of each; should be a mix of 4- and 6-player, classic and sea board

### New features in previous 2 versions

Re-test new features of the most recent two releases listed in [Versions.md](Versions.md)

### Each available Game Option

- For house rule game opt "6-player board: Can Special Build only if 5 or 6 players in game",  
  also test latest server version against client v2.2.00 or older:
    - Client can create a game with this option, 4 players, on 6-player board
    - When client clicks Special Building button, server sends text explaining the house rule is active

### Basic rules and game play

- Start a game with default options + "Allow undo piece builds and moves"
- Can build pieces by right-clicking board or with the Build Panel
    - Most recently placed piece is highlighted until end of turn or another build
- Can trade with ports by right-clicking board or using Trade Offer Bank/Port button
    - Trade to have resources to build city; should update Build Panel buttons
    - Undo trade; should update Build Panel buttons
- Trade offer, rejection, counter-offer accept/rejection
    - Build 2 roads, trade to have resources to build settlement; should update Build Panel buttons
- Can play dev card before dice roll
- Sea board: Can move a ship, but not a ship placed this turn
    - After move, ship gets highlighted since it's most recently placed piece
- Year of Plenty dev card
    - Give that card to Debug player:  
      `dev: 2 debug`
    - Play card, hit Cancel in dialog
    - Card should return to inventory, be able to play same turn
    - Play it again
    - Choose 2 resources, should appear in hand
    - Other players should see your + 2 resources
- Monopoly dev card
    - Give that card to Debug player:  
      `dev: 3 debug`
    - Play card, hit Cancel in dialog
    - Card should return to inventory, be able to play same turn
    - Play it again
    - Choose a resource type, should take from all players who have it
- Can win only on your own turn
    - This can be tested using the 6-player board's Special Building Phase
- Move robber/steal resources
    - For these tests, can use the `debug` player and debug command `*FREEPLACE* 1`
      to quickly build players' pieces, and `dev: 9 debug` to get each Soldier card
      to play to move the robber
    - Play Soldier card, but then Cancel the move with button in hand panel
      - Before roll dice, and after, on same turn
      - Card should return to inventory and be able to play again same turn
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
    - When your army size is 2, play Soldier card to take Largest Army
      - Cancel moving the soldier; should revoke Largest Army so no one has it
    - Make sure another player has Largest Army, then play enough Soldier cards to take it from them
      - When you play the card, Largest Army should change for all player clients & observers
      - Cancel playing/moving: Largest Army player should change back
      - Play again, move the robber instead of canceling
    - Move robber next to your settlement/city
      - Give your player 4 Soldier cards
      - Build a settlement at a desert hex
      - Roll 7 or use Soldier to move robber to that desert
      - Shouldn't be asked "Are you sure you want to move the robber to your own hex?"
      - Roll 7 or use Soldier to move robber next to another of your settlements
      - Should be asked "Are you sure"
      - Upgrade another settlement to a city (not at desert)
      - Roll 7 or use Soldier to move robber next to that city
      - Should be asked "Are you sure"
      - Roll 7 or use Soldier to move robber next to another player's settlement/city
      - Shouldn't be asked "Are you sure"
- Road Building dev card
    - In a 2-player game, give debug player 4 Road Building cards and a Year of Plenty card:  
      `dev: 1 debug` (4 times)  
      `dev: 2 debug`
    - Test these situations, 1 per turn:
    - Build 2 free roads
    - Build 1 free road, right-click board, choose Cancel, continue to end of turn; should see "skipped placing the second road" in game text area
    - Build 1 free road, end turn; should see "skipped placing the second road" again
    - Play card but instead of placing a free road, in Build panel click Cancel
        - Should see "cancelled the Road Building card" in game text area, dev card returned to player's inventory in hand panel
        - Should be able to play Year of Plenty card on same turn
    - Play card, end turn; should see "cancelled the Road Building card" and card returned to inventory
    - In a new 2-player game on 6-player board, give debug player 2 Road Building cards:  
      `dev: 1 debug` (2 times)
    - In another client, join same game as other player
    - Other player: Request Special Build
    - Debug player: Play card, build 1 free road, end turn; other player's Special Build should start as usual
    - Other player: Finish that Special Build and usual turn
    - Other player: During debug's turn, request Special Build
    - Debug player: Play card, end turn instead of building; card should be returned to debug's inventory, other player's Special Build should start as usual
- Gain Longest Road/Route
    - To save time with these tests, run the test server with Savegame feature enabled:
      - Pick any directory/folder where you want your server to look for savegames
      - Launch server with property `-Djsettlers.savegame.dir=` set to that directory
      - Copy src/test/resources/resources/savegame/reletest-longest-3p.game.json and reletest-longest-3p-sea.game.json into that savegame directory
    - For these tests, can use the `debug` player and debug command `*FREEPLACE* 1`
      to quickly build players' pieces and VP totals, then `rsrcs: 3 0 3 1 3 debug` to give
      resources to build the last few connecting roads/ships/last settlement the usual way
    - Situations to test:
      - Be first player to have Longest Route
      - Build roads/ships to take Longest Route from another player
      - Build settlement to split another player's Longest Route, giving a 3rd player the new Longest Route.
        (Skip this situation if testing for "move a ship".)
        If this ends the game, 3rd player should win only when their turn begins.
          - To set up the board, run `*LOADGAME* reletest-longest-3p` or `*LOADGAME* reletest-longest-3p-sea` debug command in any other game window
    - Piece types to test each situation with:
      - Build roads only
      - Build a route that has roads and ships (through a coastal settlement)
      - Move a ship to gain Longest Route
- Take Longest Route by building a coastal settlement to connect roads to ships, then undo that
    - Copy src/test/resources/resources/savegame/reletest-longest-joinships.game.json to your server's configured savegame directory
    - Run `*LOADGAME* reletest-longest-joinships` debug command in any other game window
    - Optional: Use client 2.4.00 or older as players or observers
        - Those versions don't recalculate longest route in this situation, but server 2.5.00 and newer should tell them it's changed
    - Build a coastal settlement
    - Should take Longest Route from other player
    - Right-click that settlement, Undo build
    - Other player should regain Longest Route
- Can win by gaining Longest Road/Route
    - To set up for each test, can use debug command `*FREEPLACE* 1` to quickly build pieces until you have 8 VP;
      be careful to not gain longest route before the test begins
    - With 8 VP, test each item in "Gain Longest Road/Route" list above
- Can win by gaining Largest Army
    - To set up for each test, can use debug command `*FREEPLACE* 1` to quickly build pieces until you have 8 VP,
      `dev: 9 debug` to get 2 soldier cards, play them
    - With 8 VP and playing 3rd Soldier card, test each item in "Move robber/steal resources" list above,
      except "Move robber next to your settlement/city"
        - When card is played, game might immediately award Largest Army and Hand Panel might show 10 VP
        - Card should fully play out (choose player, etc) before server announces game is over

### Undo Build/Move Pieces

- Setup
    - As Debug user, start a new game on server with options:
        - Game scenario: New Shores
        - Allow undo piece builds and moves
        - Limit undos to 7 per player (is default)
    - Initial placement: Build 2 coastal settlements near two small islands, each with an initial ship (not a road)
    - Have another client join as observer
- Build Road and Undo
    - Add resources: `rsrcs: 4 0 0 0 4 debug`
    - Build 3 roads towards the middle of the main island
        - In player client and observer, should show 2 VP
    - Note player's current resource counts
    - Build another road to gain Longest Route
        - Player should show 4 VP and Longest Route
    - Undo build that road
        - Player should show 2 VP and no Longest Route
        - Player's resource counts should be same as before building that latest road
        - Player's Undos remaining should be 6
- Build Settlement and Undo
    - Add resources: `rsrcs: 0 0 2 1 1 debug`
    - Build a ship to reach a small island
        - Don't use the same line of ships/roads just built for testing Undo Build Road/Longest Route
        - In player client and observer, should show 2 VP and no SVP
    - Note player's current resource counts
    - Build a settlement on the small island
        - Player should show 5 VP and 2 SVP
    - Undo build that settlement
        - Player should show 2 VP and no SVP
        - Player's resource counts should be same as before building that settlement
        - Player's Undos remaining should be 5
- Build City and Undo
    - Add resources: `rsrcs: 0 3 0 1 0 debug`
    - Note player's current resource counts
    - Upgrade any settlement to city
        - Player should show 3 VP
    - Undo that city upgrade
        - Player should show 2 VP
        - Player's resource counts should be same as before that city
        - Player's Undos remaining should be 4

### Scenarios and Victory Points to Win

- New Game dialog: VP to Win vs scenarios
    - Start server which doesn't specify opts VP or \_VP\_ALL
    - In client's main window, click "New Game"
    - Note default "Victory points to Win" is 10
    - Click "Create Game" to create; in created game, note VP is default (10);
      is 10 if not shown in Building Panel
    - Briefly join game with another client; should also see VP is default
    - Quit that game
    - Click "New Game" again
    - Note VP still at default (10)
    - In the Game Scenario dropdown, pick Fog Islands; VP to Win should change to 12
    - Pick scenario Through the Desert; VP should remain 12
    - Pick scenario Cloth Trade; VP should change to 14
    - Pick scenario Wonders; VP should change back to default
    - Pick scenario "(none)"; VP should remain default
    - Change VP to Win to 11; if it wasn't already, VP checkbox should automatically be checked by doing so
    - Now pick each of those scenarios again; VP should not change from 11
    - Keep VP at 11, pick scenario Fog Islands
    - Click "Create Game" to create; note VP is 11
    - Briefly join game with another client; should also see VP is 11
    - Quit that game
    - Click "New Game" again
    - Set VP to 11, pick scenario Through the Desert; VP should remain 11
    - Un-check the VP checkbox
    - Click "Create game"; in created game, note VP was set to 12 by server based on scenario
    - Briefly join game with another client; should also see VP is 12
    - Quit that game
    - Shut down server
- When server has larger default VP
    - Start a server; add at end of usual command line: `-o VP=t13`
    - Repeat the above test. Should get same results, except default VP is 13 not 10, so Cloth Trade will be 14 VP and all others will be 13, except when directly changing VP to 11 in dialog
- When server has a default scenario (standard VP)
    - Start a server; add at end of usual command line: `-o SC=SC_WOND`
    - Repeat the above test. Should get same results, except dialog's default scenario is Wonders when first shown
- When server has a default scenario and larger default VP
    - Start a server; add at end of usual command line: `-o SC=SC_WOND -o VP=t13`
    - Repeat the above test. Should get same results, except for changes described for VP=t13 and SC=SC_WOND
- Optional: When server has a default scenario, VP, and uses default VP for all scenarios
    - Start a server; add at end of usual command line: `-o SC=SC_CLVI -o VP=t13 -o _VP_ALL=t`
    - Repeat the above test. Should get same results, except for changes described for VP=t13, and default scenario should be Cloth Trade, but with 13 VP not its usual 14
- If the version being tested has changed things about the VP-Scenario interaction, also test the above with a recent previous version
    - New client with previous server
    - New server; new client creates game, previous client joins it
    - New server; previous client creates game, new client joins it

### Unprivileged info commands  

As a non-admin non-debug user, start playing a game. These should all work:

- `*WHO*` lists all players and observers in game
- `*STATS*` shows game's duration ("started x minutes ago") and number of rounds, server's count of started and completed games
- Finish the game (win or lose)
- `*STATS*` now shows game's duration as "took x minutes", server's count of completed games has increased, player's win/loss count has increased
- Start and finish another game
- `*STATS*` shows server's count of started and completed games have increased, player's win/loss count has increased

### Game info sent to observer

- Start and begin playing a game as `debug` player
- Give another player enough Victory Point dev cards to win: `dev: 5 playername` etc
- Start another client, join game as observer
- Observer should see accurate stats for public parts of game (not cards in hand or true VP totals, etc)
- Let the game end
- Observer should see same end-of-game announcements and total VP as players, including VP totals in hand panels

### Game reset voting

- To test process for rejecting/accepting the reset, ask for reset and answer No and Yes in each of these:
    - 1 human 2 bots
    - 2 humans 1 bot
    - 2 humans 0 bots

### Fog Hex reveal gives resources, during initial placement and normal game play

- Start server with vm property `-Djsettlers.debug.board.fog=Y`
- Start and begin playing a game with the Use Sea Board option
    - Place an initial settlement at a fog hex; should receive resources from each revealed hex
- Stop server, start it without `-Djsettlers.debug.board.fog=Y`
- Start and begin playing a game with the Fog Islands scenario
    - Place initial coastal settlements next to the fog island, with initial ships to reveal hexes from the fog
    - Keep restarting game (reset the board) until you've revealed a gold hex and picked a free resource

### Scenario-specific behaviors

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
  - Build a ship, immediately convert it to a warship: Should still be highlighted as most recently placed piece
  - As player with pn=0 and as another pn: Leave and rejoin game: After sitting down, should see Legal Sea Edges
    (dotted line from main island to your player's fortress) and be able to place boats along them
  - Defeat all fortresses: Pirate Fleet should disappear
      - Start a 2-player game: Debug player, 1 bot
      - Give both players about 8 Warship cards: `dev: 9 debug`, repeat for bot name
      - Use Free Placement mode to build ships to each player's fortress: `*FREEPLACE* 1`
      - Take turns, upgrade to warships, attack fortress until both are defeated
          - At least once: Build a ship and attack the same turn;
            if battle is lost, the removed ship's visual highlight for "latest placement" should disappear along with the ship
      - Pirate Fleet should disappear
      - Play a few more rounds; should see no exceptions from bot at server console
- Wonders
  - Player with 0 available ships (built all 15 already) can't start to build a Wonder,
    even if they have all other requirements
  - Starting a Wonder deducts 1 ship from player's available count
  - Building more levels of that Wonder doesn't deduct a ship
  - If observer joins after Wonder started, sees accurate ship count

### New Game minimum version warning

Setup:

- View contents of [src/main/resources/resources/version.info](../src/main/resources/resources/version.info)
- If `project.versionnumMaxNoWarn` is `2700` or higher: Temporarily change it to 2000, recompile client

Test:

- Start the client
- Use its "Start a Server" button to do so
- Enter any nickname, click "New Game"
    - Type any game name (continue to do so for each game created during this test)
    - Make sure "Limit undos to 7 per player" checkbox is set, but "Allow undo piece builds and moves" is not
    - Click "Create Game"; should create game without seeing any version popup dialog
    - In the new game's window, click "Options"; the list of options should not include "Limit undos" or "Allow undo"
    - Close that game
- Click "New Game"
    - Set checkboxes for both "Limit undos" and "Allow undo piece builds"
    - Click "Create Game": Should see a "Confirm options minimum version" dialog
        - Should say 2.7.00 or newer is required for these tests
        - Should list both of those options, each preceded by "2.7.00:"
	- Click "Create with these options"
    - In the new game's window, click "Options"; the list of options should include both of those listed ones
    - Close that game
- Click "New Game"
    - Set checkbox for "Allow undo piece builds" but not "Limit undos"
    - Click "Create Game": Should see a "Confirm options minimum version" dialog
        - Should say 2.7.00 or newer is required for these tests
        - Should list "Allow undo", preceded by "2.7.00:"
	- Click "Change options"
    - Un-set checkbox for "Allow undo piece builds"
    - Click "Create Game"; should create game without seeing any version popup dialog
    - In the new game's window, click "Options"; the list of options should not include "Allow undo" or "Limit undos"
- Exit client
- If `project.versionnumMaxNoWarn` was changed, revert it back and recompile client

### Client preferences

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

### Network robustness: Client reconnect when scenario's board layout has "special situations"

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

### Version compatibility testing

Versions to test against:

- **1.1.06** (before Game Options)
- **1.1.11** (has client bugfixes, 6-player board)
- **1.2.01** (newest 1.x, before Scenarios/sea boards); **2.0.00** (many message format changes, i18n)
- **2.5.00** (many message sequence changes)

Test these combinations:

- New client, old server
    - If server is >= 1.1.09 but older than 1.1.19, add property at end of command line: `-Djsettlers.startrobots=5`
    - If server >= 1.1.14, also add at end of command line: `-Djsettlers.allow.debug=Y`
    - If server older than 1.1.15, also add at end of command line: `8880 99 dbu pw`
- New server, old client

Test these specific things for each version:

- Server config:
    - When testing a 2.3 or newer server, start it with prop `jsettlers.admin.welcome=hi,customized`
        - All client versions should see that custom text when they connect
- With an older client connected to a newer server, list of available new-game options
  should adapt to the older client version.  
  With a newer client connected to an older server, list of available new-game options
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
        - Development Card count should update in Build Panel when buying a card
    - When server >= 2.4:
        - Have an observing client join game right after a player offers a trade; should see current trade offer
        - A few turns later, have an observing client join game; shouldn't see trade offer from a previous turn
- When testing 2.3 or newer against older than 2.3:
    - Start a game with robot player at seat number 2
    - Give dev cards to bot player:  
      `dev: 3 #2`  
      `dev: 6 #2`  
      `dev: 9 #2`
    - Have second client join as observer (same version as first client)
    - Should see correct number of dev cards for bot
    - Sit down observer to take over bot's seat
    - Should see correct card types in inventory (Monopoly, University, Knight); shouldn't see any unknown cards
    - Play 1 round, so new dev cards become old
    - Give that player another dev card:  
      `dev: 1 #2`
    - Have former observer exit, re-launch, rejoin and sit at bot player
    - Should see 3 old, 1 new dev cards
    - Optional: Do this test once with PLAY_VPO game option active
        - A 2nd client (same version as 1st; must be v2.5+) should see those card details correctly as observer, without sitting down
- When testing a 2.x client and 1.x server: In any game, test robot seat-lock button
    - Click its lock button multiple times: Should only show Locked or Unlocked, never Marked
    - Lock a bot seat and reset the game: Seat should be empty in new game
- When testing a 1.x client and 2.x server: In any game, test robot seat-lock button
    - Using a 2.x client, begin playing a new game
    - Mark one bot seat as "locked" and another as "marked"
    - Join with 1.x client
        - Should see marked seat as unlocked, locked as locked
        - Should be able to take over bot by sitting at "marked" seat
- When testing new server with client 2.5.00 or newer, and older client in same game:
    - All clients in game (players and observers) should see expected results in player hand panels and game text area for:
        - Bank trade and Undo trade
            - Total resource counts should be accurate before and after
            - Gain/lose resources to build a piece type; should update Build Panel buttons
            - Clients older than v2.5.00 are sent `SOCPlayerElement`s before `SOCBankTrade` message
        - Trade between players
            - Do a trade where a player gives 1, receives 2; total resource counts should be accurate before and after
            - Gain/lose resources to build a piece type; should update Build Panel buttons
            - Clients older than v2.5.00 are sent `SOCPlayerElement`s before `SOCAcceptOffer` message
        - Discard
            - Total resource counts should be accurate before and after
        - Soldier dev card
            - Give Soldier cards to client players:  
              `dev: 9 #2` etc
            - Test robbery, with each client as victim, robber, observer
            - Clients v2.5.00 or newer are sent `SOCRobberyResult` messages; older clients are sent `SOCPlayerElement` and `SOCGameServerText` instead
        - Discovery/Year of Plenty dev card
            - Give Discovery cards to client players:  
              `dev: 2 #2` etc
            - Play Discovery, with each client as player, observer
            - Clients v2.5.00 or newer are sent `SOCPickResources` messages; older clients are sent `SOCPlayerElement` and `SOCGameServerText` instead
        - Gold Hex resource pick
            - Make a new game with New Shores scenario
            - Reset the board until island's gold hex dice number is reasonably frequent
            - During initial placement, put two players near gold hex
            - For two players, build ships and a settlement on that gold hex by using debug command `*FREEPLACE* 1`
            - When gold hex dice number is rolled, pick free resources
            - Test once: Have observers (client 2.0.00 and current) join while waiting for the pick;
              should see "Picking Resources" text in player's hand panel
            - Clients are sent same message sequence as for Discovery/Year of Plenty detailed above
    - Optionally: Test as observer, as current player/affected player, as uninvolved player:
        - Classic board: (client 1.1.18 and 2.0.00)
            - Roll 7, no discards, prompt move robber
            - Roll 7, prompt for discards
            - Not 7, gain resources
        - Sea game scenarios: (client 2.0.00)
            - Roll 7, no discards, ask move robber or pirate
            - New Shores: Roll, gain from gold hex (for self, for others)
            - Cloth Trade: Roll, distribute cloth; game ends at roll because distributed/depleted half of villages' cloth
            - Pirate Islands: Roll, fleet battle lost (discard), won (pick free resource);
              if 7, battle results should be shown at client by the time they're asked to discard or choose a player to rob
- When testing new server with client 2.7.00 or newer, and older client in same game:
    - Play and cancel each dev card type, as described in "Basic rules and game play" section
        - v2.7.00 clients and newer can cancel dev card plays (Road Building is v2.5.00 and newer)
        - Road Building, Monopoly, Year of Plenty, Soldier
        - Clients should see the cancel, see the card return to client player's hand/inventory
        - For Soldier, clients should see changes to Largest Army when card is canceled

### Server robustness: Bot disconnect/reconnect during game start

- Start server with vm properties: `-Djsettlers.bots.test.quit_at_joinreq=30` `-Djsettlers.debug.traffic=Y`
- Connect and start a 6-player game
- Bots should arrive, game should start
- Server console should have lines like:  
  `robot 3 leaving at JoinGameRequest('g', 3): jsettlers.bots.test.quit_at_joinreq`  
  `srv.leaveConnection('robot 3') found waiting ga: 'g' (3)`  
  If not, start another game and try again

### StatusMessage "status value" fallback at older versions

- Start a 2.0.00 or newer server with command-line arg `-Djsettlers.allow.debug=Y`
- Start a 2.0.00 client with vm property `-Djsettlers.debug.traffic=Y`
- That client's initial connection to the server should see at console: `SOCStatusMessage:sv=21`  
  (which is `SV_OK_DEBUG_MODE_ON` added in 2.0.00)
- Start a 1.2.00 client with same vm property `-Djsettlers.debug.traffic=Y`
  - If needed, can download from https://github.com/jdmonin/JSettlers2/releases/tag/release-1.2.00
- That client's initial connection should get sv == 0, should see at console: `SOCStatusMessage:status=Debugging is On`

### Game Option and Scenario info sync/negotiation when server and client are different versions/locales

For these tests, use these JVM parameters when launching clients:  
`-Djsettlers.debug.traffic=Y -Djsettlers.locale=en_US`  
Message traffic will be shown in the terminal/client output.

- Test client newer than server:
    - Build server JAR as usual, make temp copy of it, and start the temp copy (which has the actual current version number)
    - In `SOCScenario.initAllScenarios()`, uncomment `SC_TSTNC` "New: v+1 back-compat", `SC_TSTNA` "New: v+1 another back-compat", and `SC_TSTNO` "New: v+1 only"  
      Update their version parameters to current versionnum and current + 1. Example:  
      `("SC_TSTNC", 2400, 2401, ...)`  
      `("SC_TSTNA", 2400, 2401, ...)`  
      `("SC_TSTNO", 2401, 2401, ...)`
    - In `SOCGameOptionSet.getAllKnownOptions()`, scroll to the end and uncomment `DEBUGBOOL` "Test option bool".
      Update its min-version parameter to current versionnum. Example:  
      `("DEBUGBOOL", 2400, Version.versionNumber(), false, ...)`
    - In `src/main/resources/resources/version.info`, add 1 to versionnum and version. Example: 2400 -> 2401, 2.4.00 -> 2.4.01
    - Build client (at that "new" version) using `gradle assemble` to skip the usual unit tests.
      The built jars' filenames might include current version number (not + .0.01); that's normal.
    - Launch that client (prints the "new" version number at startup), don't connect to server
    - Click "Practice"; dialog's game options should include DEBUGBOOL,
      Scenario dropdown should include those 3 "new" scenarios
    - Quit and re-launch client, connect to server
    - Message traffic should include:
      - Client's `SOCGameOptionGetInfos` for DEBUGBOOL
      - Server response: `SOCGameOptionInfo` for DEBUGBOOL, + 1 more Option Info to end that short list
    - Click "New Game"
    - In message traffic, should see a `SOCScenarioInfo` with `lastModVers=MARKER_KEY_UNKNOWN` for each of the 3 new scenarios, + 1 more to end the list of Infos
    - The "new" items are unknown at server: New Game dialog shouldn't have DEBUGBOOL,
      its Scenario dropdown shouldn't have the 3 test scenarios
    - Quit client and server
- Then, test server newer than client:
    - Temporarily "localize" the test option and scenarios by adding to
      `src/main/resources/resources/strings/server/toClient_es.properties`:  
      `gameopt.DEBUGBOOL = test debugbool localized-es`  
      `gamescen.SC_TSTNC.n = test-localizedname-es`  
    - Build server jar, using `gradle assemble` to skip the usual unit tests
    - Start a server from that jar (prints the "new" version number at startup)
    - Reset `version.info`, `toClient_es.properties`, `SOCGameOptionSet.getAllKnownOptions()`,
      and `SOCScenario.initAllScenarios()` to their actual versions
      (2401 -> 2400, re-comment options/scenarios, etc).
      Afterwards, `git status` shouldn't list those files as modified.
    - Build and launch client (at actual version)
    - Connect to "newer" server
    - Message traffic should include:
      - Client's generic `SOCGameOptionGetInfos` asking if any changes
      - Server response: `SOCGameOptionInfo` for DEBUGBOOL, + 1 more Option Info to end that short list
    - Click "New Game"
    - In message traffic, should see a `SOCScenarioInfo` for each of the 3 new scenarios, + 1 more to end the list of Infos
    - Dialog should show DEBUGBOOL option. Should see `SC_TSTNC` and `SC_TSTNA` but not `SC_TSTNO` in Scenario dropdown
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
    - Connect to server; in message traffic, should see a `SOCGameOptionInfo` for DEBUGBOOL with "localized" name
    - In that client, click "Game Info"
    - In message traffic, should see only 1 `SOCScenarioInfo`, with that game's SC_TSTNC scenario
    - Game Info dialog should show scenario's info and "localized" name
    - Quit and re-launch that client
    - Connect to server, click "New Game"
    - In message traffic, should see:
      - a `SOCScenarioInfo` for each of the 3 new scenarios (SC_TSTNC, SC_TSTNA, SC_TSTNO); SC_TSTNC name should be the localized one
      - `SOCLocalizedStrings:type=S` with all scenario texts except SC_TSTNC, SC_TSTNA, SC_TSTNO
      - 1 more `SOCScenarioInfo` to end the list of Infos
    - Dialog should show "localized" DEBUGBOOL game option. Scenario dropdown should show all scenarios with localized text
    - Cancel out of New Game dialog
    - Quit clients and server

### Third-party Game Option negotiation

- Test when only client knows a 3P game opt:
    - Start a client with JVM parameter `-Djsettlers.debug.client.gameopt3p=xyz`
    - Click Practice button; in Practice Game dialog, should see and set checkbox for game option "Client test 3p option XYZ"
    - Start the practice game
    - In game window, click "Options" button; game opt XYZ should be set
    - Start a server without any `gameopt3p` param
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
    - Double-click again, popup should show message "Cannot join ... This client does not have required feature(s): com.example.js.feat.XYZ"
- Quit all clients and server
- Unit tests handle third-party options if added in a fork
    - In soc.game.SOCGameOptionSet.getAllKnownOptions, temporarily uncomment game opts `"_3P"` and `"_3P2"`
    - Run unit tests or `gradle build`
    - All unit tests should still pass (TestGameOptions.testOptionsNewerThanVersion looks for "known" 3rd-party gameopts)

### i18n/Localization

For these tests, temporarily "un-localize" SC_FOG scenario, SC_TTD description by commenting out 3 lines in `src/main/resources/resources/strings/server/toClient_es.properties`:  
```
# gamescen.SC_FOG.n = ...
# gamescen.SC_FOG.d = ...
...
gamescen.SC_TTD.n = ...
# gamescen.SC_TTD.d = ...
```

- 3 rounds, to test with clients in english (`en_US`), spanish (`es`), and your computer's default locale:  
  Launch each client with specified locale by using a JVM parameter value like: `-Djsettlers.locale=es`
- If client's default locale is `en_US` or `es`, can combine that testing round with "default locale" round
- If other languages/locales are later added, don't need to do more rounds of testing for them;
  the 3 rounds cover english (the fallback locale), non-english, and client's default locale
- Reminder: To show a debug trace of network message traffic in the terminal/client output,
  also use JVM param `-Djsettlers.debug.traffic=Y`

For each round of testing, all these items should appear in the expected language/locale:

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
        (except for english client), or for Fog Islands, `SC_FOG|K` because it's marked as "unknown"
    - Join each of those 3 games
      - In message traffic, shouldn't see another `SOCLocalizedStrings:type=S`, because server tracks already-sent ones
    - Re-launch client, to clear that server-side and client-side tracking
    - Join each of those 3 games
      - Popup when joining, or game Options button: Scenario info should be localized same way Game Info dialog was

### Client Feature handling

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
- When reconnecting disconnected clients:
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

### Saving and loading games at server

Setup: Needs `gson.jar` in same directory as server jar.
For details, search [Readme.developer.md](Readme.developer.md) for `gson.jar`

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

### Command line and jsserver.properties

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
- `*SAVELOG*` debug command:
    - In IDE or command line (see [Readme.developer.md](Readme.developer.md)),
      launch RecordingSOCServer and log in as `debug` with the standard client
    - As debug player, start a game and play at least 1 round past initial placement
    - In chat text field, enter and send: `*SAVELOG* testsave`
    - Should create file `testsave.soclog` in server's current directory, with header and messages to/from server
    - Repeat command: `*SAVELOG* testsave`
    - Should see text like: "Log file already exists: Add -f flag to force, or use a different name"
    - Send command: `*SAVELOG* -f testsave`
    - Should overwrite testsave.soclog; this is same game, so new contents are previous contents + the savelog commands
    - Send command: `*SAVELOG* -f -u testsave`
    - Should overwrite testsave.soclog; new contents should not have the timestamp field
    - Send command: `*SAVELOG* -f -c testsave`
    - Should overwrite testsave.soclog; new contents should have only messages to clients, not from clients
    - Send command: `*SAVELOG* -f -c -u testsave`
    - Should overwrite testsave.soclog; only messages to clients, no timestamps
    - Reset the board and play at least 1 round
    - Send command: `*SAVELOG* -f testsave`
    - Should overwrite testsave.soclog; contents are the new game, nothing from before the reset
    - Make note of game name
    - Leave game, make sure it disappears from client's list of server games
    - Make new game with same name, start initial placement
    - Send command: `*SAVELOG* -f testsave`
    - Should overwrite testsave.soclog; contents are the new game, nothing from the previous one of same name
- Idle games, timeout behaviors:
    - Leave a practice game idle for hours, then finish it; bots should not time out or leave game
    - Leave a non-practice game idle for hours; should warn 10-15 minutes before 2-hour limit,
      should let you add time in 30-minute intervals up to original limit + 30 minutes remaining
    - Client: Click "Start a Server" button, leave it idle for at least 2 hours;
      should stay up and available, shouldn't time out or give a network error
    - If connection to server is lost or times out, Connect or Practice panel should give you all options
        - In src/main/java/soc/server/genericServer/NetConnection.java temporarily change `TIMEOUT_VALUE` to `10 * 1000`
        - Test: Start a Server
            - Launch client and click "Start a Server", then "Start"
            - Should start up and connect to that server as usual
            - Wait 10 seconds
        - Client should return to its Connect or Practice panel
            - Should show the 3 buttons for Connect to a Server, Practice, Start a Server
            - Network trouble message should be shown above the buttons
            - No input textfields should be visible
            - Click "Connect to Server"; all buttons should be enabled
            - Click "Start a Server"; all buttons should be enabled
        - Start up a server on default port
        - Test: Connect to a Server
            - In client, click "Connect to a Server", then "Connect"
            - Should connect to that server as usual
            - Wait 10 seconds
        - Client should return to its Connect or Practice panel
            - Should be same as for "Test: Start a Server": see that for details
        - Exit client, stop server; revert NetConnection.java temporary change
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
      `java -jar JSettlersServer-2.*.jar -Djsettlers.bots.botgames.total=100 -Djsettlers.bots.botgames.parallel=6 -Djsettlers.bots.fast_pause_percent=5 -Djsettlers.bots.botgames.gametypes=3 -Djsettlers.debug.bots.datacheck.rsrc=Y -Djsettlers.allow.debug=Y -Djsettlers.bots.botgames.shutdown=Y 8118 15`
    - To optionally see progress, connect to port 8118 with a client. Game numbers start at 100 and count down.
    - These games should complete in under 10 minutes
    - Once the games complete, that server will exit
    - Scroll through its output looking for exceptions
        - "force end turn" output, and occasional bad placements or bank trades, are expected and OK
        - If any exceptions occur: Debug, triage, document or correct them
- Board layout generator stability:
    - See `TestBoardLayoutsRounds` in "extraTest" section
- Build contents and built artifacts:
    - `gradle dist` runs without errors or unusual warnings, under gradle 6.9.x and 7.5.x
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

On most recent and less-recent OSX and Windows; JRE 8 and a new JDK:  
(Note: Java 8 runs on Win XP and higher; can download installer from https://jdk.java.net/ )

- Dialog keyboard shortcuts, including New Game and Game Reset dialogs' esc/enter keys, FaceChooserFrame arrow keys
- Hotkey shortcuts
    - Generic modifier key: Ctrl on all platforms (Ctrl-R, Ctrl-D, etc)
    - MacOSX or Windows: also test modifier Cmd or Alt
    - Roll and Done buttons: R, D + modifier
    - Ask to Special Build in 6-player game: B + modifier
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
- Game window Close button handler
    - Create a game
    - Click its game window's Close button, then choose Continue Playing; window should not disappear
    - Click Close again and choose Reset Board; window should not disappear
    - Click Close again and choose Quit Game; window should disappear, game should be removed from list of games
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


