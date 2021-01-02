# Released Versions of JSettlers

Project home and source history are at [https://github.com/jdmonin/JSettlers2](https://github.com/jdmonin/JSettlers2).
Source history for version `1.1.06` and earlier is at [https://github.com/jdmonin/JSettlers1](https://github.com/jdmonin/JSettlers1).

JARs for recent JSettlers versions can be downloaded from
[https://github.com/jdmonin/JSettlers2/releases](https://github.com/jdmonin/JSettlers2/releases) .


## `3.0.00` (build JX202xxxxx)
- Experimental branch `v3`, not currently the main line of development
- Experimental features: html5 client, jetty/tomcat servlet; protobuf option for bots
- Major refactoring: Game data types, etc, thanks to Ruud Poutsma


## `2.4.50` (build JM20201xxx)
- Currently being developed
- Gameplay:
	- When a trade is offered to bots and humans, bots wait longer before responding.
	  Was 3 seconds, is now 8, changeable with server property `jsettlers.bot.human.pause`
	  (thank you Lee Passey)
- I18N:
	- Added French translation (thank you Lee Passey)
- Network/Message traffic:
	- For efficiency and third-party bots' understanding, server sends data messages instead of text when clients are this version or newer:
		- Report robbery with `SOCReportRobbery`
		- Announce Discovery card/gold hex free resource picks with `SOCPickResources`
		- Reject disallowed trade requests with `SOCBankTrade` or `SOCAcceptOffer` reason codes
	- When Monopoly card played:
		- Server announces amount gained instead of player's total amount of that resource
		- Now sends resource gain/loss messages before, not after, SOCSimpleAction(RSRC_TYPE_MONOPOLIZED)
		  so client's game data's is updated by the time it sees that action message
	- If client sends discard with incorrect total, server re-sends SOCDiscardRequest which includes required amount
	- For third-party clients/bots, client can ask server to announce SOCSimpleAction(DICE_RESULTS_FULLY_SENT)
	  after each dice roll's result messages
- For developers:
	- Upstreamed and reintegrated from STAC Project fork https://github.com/ruflab/StacSettlers :
	    - Various player and game statistic fields/methods and misc code
	    - Misc bot API refactoring; encapsulate robot's build stack as SOCBuildPlan
	    - soc.message methods to parse human-readable toString logging format: parseMsgStr, stripAttribNames
	    - Extend soc.debug / disableDebug logging: 4 debug levels INFO, WARNING, ERROR, FATAL
	    - SOCDBHelper is no longer a static/unextendable singleton
	    - Many thanks to Morgan Giraud for collaboration on this work
	- Enhanced server's recordGameEvent framework for more detailed game recording
	- More accessible robot-related methods and data classes
	- For third-party bots, added more granular override points like
	  `endTurnActions`, `handleTradeResponse`, `planAndDoActionForPLAY1`, `SOCBuildingSpeedEstimateFactory`,
	  `OpeningBuildStrategy.cancelWrongPiecePlacement`
	- Made some data classes Serializable
	- Save/load games:
	    - SavedGameModel:
	        - PlayerInfo: add fields for number of Discovery, Monopoly, Road Building cards played,
	          list of dev cards played; TradeOffer add timestamp
	        - MODEL_VERSION still 2400; earlier server versions will ignore these added fields while loading a savegame
	        - GLAS field made non-static so unit tests can safely run in parallel for quicker builds
	- Unit tests and extraTests against running server for core game actions and message sequences
	- Server consistently uses Properties if passed into constructors
	- Server waits for {@code serverUp()} to return before starting to accept connections
	- extraTest TestBoardLayoutsRounds: Exit early if needed to avoid failure from 30-second timeout
	- For tests using robot-only games, added server behavior flag SOCGameHandler.DESTROY_BOT_ONLY_GAMES_WHEN_OVER
	- Refactored message classes:
	    - Server now mostly calls constructors, not static toCmd methods
	    - Add toString to several message types to clarify fields
	    - SOCLocalizedStrings, SOCStatusMessage, SOCVersion: toString: Use standard delimiter `|`, not `,`
	- Game Options:
	    - "Inactive Options" concept: Developer-only or specialty gameopts
	        - To avoid clutter, normally hidden and not sent to connecting clients
	        - Activate in server config if needed: `jsettlers.gameopts.activate=PLAY_VPO,OTHEROPT`
	        - For details see [Readme.developer.md](Readme.developer.md) section "Game rules, Game Options"
	    - New inactive options, to show or help debug gameplay details, from STAC concepts:
	        - `PLAY_VPO`: Show all players' VP/dev card info
	        - `PLAY_FO`: Show all player info as fully observable: Resources, VP/dev cards
	    - "Third-Party Options" concept: Gameopts defined by a 3rd-party client, bot, or server JSettlers fork,
	      as a way to add features or flags but remain backwards-compatible with standard JSettlers.
	        - When connecting, client must ask server if it knows about all such gameopts, regardless of version
	        - Associated with a given client feature; server looks for feature when a client connects
	    - Refactored option maps to SOCGameOptionSet
	    - Robot clients no longer ignore game option info sync messages
	    - SGH.calcGameClientFeaturesRequired checks each gameopt for features
- Server:
	- When game has been loaded but not yet resumed, humans can sit down at any player's seat (human or robot)
	- If human takes over a player in a formerly bots-only game and stays until the end, don't delete that game immediately
	- Fix cosmetic StringConnection IllegalStateException seen for bots during server shutdown
- Client:
	- New Game dialog:
	    - Sort game option descriptions case-insensitively, in case of acronyms
	    - Options with keynames longer than 3 chars aren't grouped under a 2-character "parent" option
	      (`"PLAY_"` isn't under coincidental `"PL"`), use `_` instead to look for possible parent option
	- Game window:
	    - Hand Panel: Shrink unused space above trading squares
	    - Forgotten Tribe scenario: Much less flicker while placing gift ports
	    - Chat panel: If text to be sent contains `|`, show a popup to say that can't be sent
	- If server announces it's shutting down with StatusMessage(SV_SERVER_SHUTDOWN), show Connect or Practice panel
	- Net debug: If `jsettlers.debug.traffic=Y` is set and message from server can't be parsed, print it to console
- Code internals:
	- Fixed lint warnings for switch fallthrough, variable shadowing, renamed a few obscure fields
	- Renames for consistency:
	    - SOCDevCardConstants.TEMP -> TEMPLE
	    - SOCPlayerInterface.clientIsCurrentPlayer -> isClientCurrentPlayer


## `2.4.00` (build JM20200704)
- Gameplay:
	- Pirate can be placed next to any coastline, even those near edge of board
	- For consistency, calculate Longest Route and Largest Army only at server, not also at client
- Client:
	- Add client preference: Remember face icon when changed
	- Game window:
	  - Game stats: If player leaves at end of game, keep showing their statistics
	  - If connection to server is lost during game, don't hide Current Player arrow
	  - Clarify in tooltip that "right-click to trade" is for bank trades
	  - Bugfix: If face icon chooser window was still open when closing the game window, chooser would stay open
- Server:
	- If connecting client has limited features, send all unsupported game options as unknowns
- Network/Message traffic:
	- When client joins a game:
	  - Server sends players' current trade offers
	  - Server sends seat locks before player info SitDown messages, not after them
	- When client is sitting down at new game or to take over a bot's seat, send their preferred face icon
	- During game play:
	  - When longest route or largest army player changes, announce from server instead of having client calculate it
	- Bugfix: When client is v1.x, send seat lock state CLEAR_ON_RESET as UNLOCKED not LOCKED
	  so they can take over a "marked" bot seat
- Database:
	- If saving all completed game results in database, each game's options are sorted alphabetically
- For developers:
	- Save/load games:
	  - SavedGameModel: Players' piece types and dev cards are written to file as
	    user-friendly type name strings like `"SETTLEMENT"` or `"UNIV"`, not ints.
	    Can still read them from ints if needed.
	  - Simple scenario support: Can save and load scenarios which have only a board layout and
	    optionally game option `_SC_SANY` or `_SC_SEAC`, no other scenario game opts
	    (option names starting with "\_SC\_"). Sets PlayerElements SCENARIO_SVP,
	    SCENARIO_SVP_LANDAREAS_BITMASK. Adds PlayerInfo.specialVPInfo.
	  - Load:
	    - Rename any bot players with same names as those logged into the server
	      to avoid problems during random bot assignment while joining the game
	    - If game is already over, don't change robot player names by asking bots to join and sit
	    - Fix incorrect SOCPlayer.potentialSettlements additions
	    - If can't parse gameOptions, don't load game
	    - Bugfix: Players' Longest Route lengths were incorrect after load
	  - Adds PlayerInfo.earlyElements list to set before piece placement
	  - SavedGameModel: gameOptions now sorted, adds playerSeatLocks, PlayerInfo adds currentTradeOffer
	  - Omit writing pieces' specialVP field when it's 0, ships' isClosed when false
	  - MODEL_VERSION increased to 2400
	  - Unit tests using saved-game artifacts
	- Game window: If observing a robot-only game and it's deleted when over,
	  keep showing winner with Current Player arrow
	- For unit tests, SOCGameListAtServer.addMember now succeeds even if game was added using client-side addGame method


## `2.3.00` (build JM20200525)
- Gameplay:
	- New optional house rule: On 6-player board, allow Special Building phase only if game actually has 5 or 6 players
	- Bugfixes at server:
	  - Forgotten Tribe scenario: If a ship claimed a Special Victory Point and was later moved,
	    player silently lost that SVP
	    (thanks Michi-3 for reporting github issue #71)
	  - Forgotten Tribe scenario: While Special Building a ship, if a port was picked up and placed,
	    turn order afterwards was incorrectly resumed at next clockwise player (also issue #71)
	  - Through The Desert scenario: Could not build roads on some edges of desert strip (also #71)
- Client:
	- Game window:
	  - Hotkeys:
	    - Roll dice with Ctrl-R/Alt-R/Cmd-R
	    - End turn (Done) with Ctrl-D/Alt-D/Cmd-D
	    - Accept/ReJect/Counter trade offers when just one is visible, with Ctrl or Alt or Cmd + A/J/C
	      - Since chat input field already has a hotkey for Ctrl/Alt/Cmd-A when focused:  
	        When cursor is in chat, hit Ctrl/Alt/Cmd-A once to select all text, again to Accept the trade offer
	  - Draw ships with slimmer sails, for better spacing next to other pieces
	  - Bugfix: If player had SVP, square showing SVP amount overlapped trade offer display  
	    and, at end of game, their revealed VP cards
	  - Bugfix: Wonders scenario: Player's Wonder shown in hand panel overlapped trade offer display
	  - Chat textarea, server textarea: Add context menu with Select All and Copy, since new hotkeys claim Ctrl-A and Ctrl-C
	- Bugfix: If started a practice game, then connected to a server game:
	  - Practice game trades stopped working
	  - Might've joined server game as "Player" or "null"
	- Bugfix: Sometimes if client was observing a game and sat to take over a robot player
	  right after it bought a dev card, inventory would include unknown card(s) alongside the correct ones
	- If nickname or channel/game name contains disallowed character ',' or '|', explanation text mentions that character
- Bots/AI:
	- When offering a trade to human players, shorten max wait to 30 seconds (was 100)
	- Fix occasional SOCCity NPE in SOCPlayerTracker.recalcWinGameETA
- Server:
	- Game `*STATS*`: When game over, keep its duration constant instead of still increasing
	- Administration:
	  - Customized client welcome message, with optional server property `jsettlers.admin.welcome`  
	    (see `jsserver.properties.sample` comments for details)
	  - Admin users can chat and run commands while observing a game
	  - Can now run these commands from any admin account, not only as Debug:  
	    `*BCAST*, *BOTLIST*, *BOTLIST*, *RESETBOT*, *KILLBOT*, *GC*`
	  - Daily Stats file: New optional server property `jsettlers.stats.file.name`: Appends output of `*STATS*` command daily
	    (see `jsserver.properties.sample` comments for details)
	  - `*STATS*` also counts number of finished games which had bots, number of bots in those games
	  - `*STATS*` uptime shows "days" not "d", uses leading zeros for minutes:seconds
	  - `*STATS*`, `*GC*` also show memory as MB or GB, % free of total
	  - `*BCAST*` broadcast text now starts with admin username who sent it
	  - `*BOTLIST*` no longer invites all bots to join admin user's game
	  - `*DBSETTINGS*` also shows whether game results are saved in DB
- For developers:
	- Can save/load games using local files and debug commands
	  - This is work in progress: Currently works with classic and sea board games, but no scenarios
	  - Server config must designate a directory
	  - Optional GSON jar must be on classpath or same dir as server
	  - For details see [Readme.developer.md](Readme.developer.md): Search text for `*SAVEGAME*`
	- Bugfix: Free Placement debug mode with Forgotten Tribe scenario:
	  - Handle Gift Port pickup and placement for current player, decline it for other players
	  - Use new ship's player, not current player, for SVP and dev card gifts
- For AI/Robot development:
	- SOCRobotBrain debug stack trace: Print bot name
	- When server starts robot-only games (jsettlers.bots.botgames.total > 0),
	  can specify a mix of game sizes and board types with new startup option:  
	  `-Djsettlers.bots.botgames.gametypes=3`  
	  For details, search for that property in src/main/bin/jsserver.properties.sample
	- Increased `jsettlers.bots.botgames.parallel` default to 4 from 2
- Network/Message traffic:
	- Game/board data sent by server to client joining a game:
	  - For 6-player game, send players' ASK_SPECIAL_BUILD flag if set
	  - For scenario Cloth Villages, send updated General Supply count if game has started
	- Game data sent to client sitting down to play:
	  - If client is this version or newer, omit messages meant to clear player's inventory contents:  
	    Client player now clears inventory when SitDown message received
	  - When client is joining and sitting down to take over a frozen connection, omit unneeded messages
	    which add and then clear unknown cards in that player's inventory
	- Client: Omit unneeded player name/number in some message types


## `2.2.00` (build JM20200229)
- Client:
	- Game window:
	  - Remember recently sent chat text; browse history with Up/Down arrow keys in input field (based on Chad McHenry idea)
	  - At end of game, reveal VP cards in players' hand panels
	  - If user clicks board to dismiss the right-click build menu, don't show "to build pieces, right-click" reminder dialog
- Server:
	- `*STATS*` command: Send client how long they've been connected and, if at least 1 game won or lost, their win-loss count for this session
	- If client joins a game that's already over, send final scores and revealed VP cards
	- Let debug user also chat and run commands while observing a game
	- SQLite database:
	  - For convenience, if JVM properties don't contain `org.sqlite.tmpdir` but that property is set in
	    `jsserver.properties` file or command line, copy it into JVM properties
- For AI/Robot development:
	- Server can automatically start some third-party bots with new startup option:  
	  `-Djsettlers.bots.start3p=3,com.example.BotXClient,5,net.example.BotZClient`  
	  For details, search for that property in src/main/bin/jsserver.properties.sample
	- Strategy classes made easier to change or subclass for 3rd-party bots; added SampleDiscardStrategy
	- Human players can't use the robot nickname prefix "extrabot "


## `2.1.00` (build JM20200119)
- Client:
	- New Game dialog: If "force UI scale" option is changed, use new value for new game's window
	- While joining game, try to prevent game-list window from getting in front of that game's new window
	- Game window:
	  - Game Options dialog: If Enter pressed while in text field, save prefs instead of saying "Game with this name already exists"
	  - Properly re-center top text ("Last round for no 7s") if window made larger/smaller while top text displayed
- Server:
	- Bugfix when client sits down, taking over a bot's seat, during first initial-settlement round:
	  Some players' first placement was skipped because "first player" changed during that bot replacement
	- For quick restart, bind TCP socket with setReuseAddress (SO_REUSEADDR) flag
	- At shutdown, server broadcasts StatusMessage(SV_SERVER_SHUTDOWN) for clean client shutdown
- Bots/AI:
	- Don't try to reconnect after server sends StatusMessage(SV_SERVER_SHUTDOWN)
- Network/Message traffic:
	- Game names can't start with reserved character '?'


## `2.0.00` (build JM20200102)
- Game play:
	- Large board (sea board) support
	- Game Scenario and special-rules support
	- Game option "VP" (Victory Points to win): Maximum increased to 20 from 15
- Client:
	- High-DPI support based on screen resolution, user preference, or running with JVM parameter `-Djsettlers.uiScale=2`
	- Added second set of hex graphics; in new-game options, can choose Classic or the new set (contributed by qubodup)
	- Discovery/Year of Plenty card: Dialog box includes current resource counts (like Discard dialog)
	- Trade counter-offer: For legibility use light background color, not player color
	- Bank trades: If server declines trade, don't enable Undo Trade button or clear Give/Get resources to 0
	- When joining a game or chat channel, server sends a "recap" of recent player chat
	- Game windows:
	    - Show board as large as possible
	    - Player name labels sans-serif for cleaner look
	    - Board graphics can use higher-resolution hex images
	    - Game Options dialog stays in front of its game window
	    - Debug Free Placement mode: Can also click player name to change placing player, not just face icon
	- Popups (AskDialog, etc) layout fine-tuned, can wrap multi-line text
	- When deleting a game, remove from game list using exact match, not startsWith
	- Use Swing framework to help performance and accessibility
	- On Windows, detects High-Contrast mode/theme and uses appropriate colors
	- Applet class is now `soc.client.SOCApplet`
- New I18N framework:
	- Languages: English, Spanish
	- Started by Luis A. Ramirez; thank you Luis
	- Jeremy wrote more I18N utilities (package net.nand.util.i18n)
- Bots/AI:
	- If a bot is slow and its turn has been ended several times, shorten its timeout so other players won't have to wait so long (KotCzarny idea)
	- Shorten bot trade timeout after human players have declined (KotCzarny idea)
- Network/Message traffic:
	- When joining game in progress, server sends current round to update client's "*n* rounds left for No 7s" display
	- More efficient game-setup messages over network
		- If new game request has VP option but with a false boolean part, remove that VP option
		- When forming a new game with a classic non-sea board, don't send the empty board layout:
		  Client already has data for an empty board
	- After a player discards, but others still must pick their discards: Don't send redundant SOCGameState,
	  only the text prompt. This also prevents client from redisplaying "Discarding..." for players who've
	  discarded but still have more than 7 resources
	- Game names and user nicknames can't be a number or punctuation: Must contain a non-digit, non-punctuation character
	- SOCBuildRequest now optional before client's SOCPutPiece request
- Server:
	- At server startup, if robots take up most of maxConnections, warn and use a higher value so humans can connect
	- Game expiration: (in case of sleeping laptop as server)
	  - First warning: If less than 6 minutes remains, auto-add a few minutes to expiration time
	  - `*ADDTIME*` command: Make sure new expiration time gives at least 30 minutes remaining
	- Server closes connections to rejected clients or bots
	- Server Config Validation mode: Test the current config and exit, with new startup option:  
	  `-t` or `--test-config`
- Misc bugfixes:
	- If a new game is created but no one has sat down, then another client joins and leaves it, don't delete that game
- Database:
     - Upgraded Schema `v2.0.00` adds:
         - users table: Count of games won, lost
         - games table: Obsoleted by games2. Upgrade won't delete it, but new games won't be added to it
         - games2: Normalized "games" table with per-player sub-table; also added scenario field
         - games2_players: Sub-table: Per-player scores for each player in a game
     - Users' win/loss counts are updated when game won or lost, even if flag property
       `jsettlers.db.save.games` isn't set
     - Server `--pw-reset` now hides the password text
     - Warn at startup if property `jsettlers.accounts.admins` isn't set, unless using Open Registration mode
     - Can use MariaDB
     - Docs: Give workaround for sqlite-jdbc shared library "operation not permitted" startup error
     - If using Oracle (unsupported): Upgrading to new v2.0.00 schema not yet implemented
- Game internals:
	- Game option key names can now be longer (8 characters)
	- Some game options are meant to be set by the server during game creation, not requested by the client.
	  Their option keynames all start with '_' and are hidden in the New Game options window.
	- Player's inventory can hold more than just development cards
- For AI/Robot development:
	- The server can run bot-only games with new startup options:  
	  `-Djsettlers.bots.botgames.total=7`  
	  `-Djsettlers.bots.botgames.parallel=3`
	- Those bot-only games begin at server startup, or can be delayed with startup option:  
	  `-Djsettlers.bots.botgames.wait_sec=30`
	  (this example uses 30 seconds) to give bot clients more time to connect first.
	- Server can use third-party bots as a certain percentage of the bots in each game
	  with new startup option: (this example uses 50%)  
	  `-Djsettlers.bots.percent3p=50`
	- Third-party bots can have more time to plan their turn with new server startup option:
	  (this example uses 18 seconds)  
	  `-Djsettlers.bots.timeout.turn=18`
	- Third-party bots can be sent config or debug settings with new game option `_EXT_BOT`
	  from server command line:  
	  `java -jar JSettlersServer.jar -o _EXT_BOT=abcde`  
	- Tuning for length of SOCRobotBrain pauses during bot-only games:
	  To pause only 10% as long as in normal games, use  
	  `-Djsettlers.bots.fast_pause_percent=10`
	- New debug command `*STARTBOTGAME* [maxBots]` to begin current game as bots-only
	- If the last human player leaves a game with bots and observers, server continues that game as bots-only
	  if server property `jsettlers.bots.botgames.total` != 0
	- Standalone bot clients shut down properly if they can't reconnect to server after 3 retries
	- Example `soc.robot.sample3p.Sample3PBrain extending SOCRobotBrain`, `Sample3PClient extending SOCRobotClient`
	- Some private SOCRobotClient fields made protected for use by bot developer 3rd-party subclasses
	- If bot disconnects after server asks it to join a game that's starting,
	  server looks for another bot so the game won't hang
- Code internals:
	- Java 5+ features, including generics/parameterized types (thank you Paul Bilnoski)
	- SOCBoard layout refactoring to SOCBoard4p, SOCBoard6p thanks to Ruud Poutsma
	- Major client refactoring (separate UI from network interface) thanks to Paul Bilnoski;
	    Paul's UI split preserves the spirit and flow of the code, with a more logical layered structure.
	- Server inbound message handling refactored in collaboration with Alessandro D'Ottavio,
	    and SOCMessage parsing moved from single-threaded Treater to per-client Connection thread
	- Robot client's inbound-message treat method calls super.treat in the default case,
	    so `SOCDisplaylessClient.treat()` handles all messages which don't need robot-specific handling.
	- For clarity rename genericServer classes: StringConnection -> Connection, NetStringConnection -> NetConnection,
	    LocalStringConnection -> StringConnection, etc
	- Game state renamed for clarity: SOCGame.PLAY -> ROLL_OR_CARD; PLAY1 not renamed; SOCRobotBrain.expectPLAY -> expectROLL_OR_CARD
	- Tightened class scope for clarity: Removed `public` from classes meant for internal use (thank you Colin Werner)
	- Minor refactoring
	- Framework of unit tests for build, and extra automated tests for release, in java and python
	- Built JARs include git commit hash if available, as Build-Revision in MANIFEST.MF
	- Project dir structure converted to maven/gradle layout
	- Project builds with gradle, not ant
	- To simplify build process, moved version and copyright info from `build.xml` to `version.info`
- READMEs and VERSIONS.txt converted to Markdown (thank you Ruud Poutsma), merged old-updates-rsthomas.html into Versions.md


## `1.2.01` (build OV20180526)
- Game reset no longer hangs when game had bot(s) and someone locked all bots' seats
- Game expiration:
    - Initial game length increased: Now 2 hours, was 90 minutes
    - Warns 5 or 6 minutes earlier
    - Ensure at least 1 warning before ending game:
      Local-server games won't immediately expire when a sleeping laptop wakes
      (Practice games haven't expired since v1.1.09)
- Client:
    - Game window bugfix: Join Game hangs on Windows Java 9 (SnippingTextArea peer NoSuchMethodError)
    - Sound prompt when client player is offered a trade
    - Game windows: Render board with antialiasing
- Players can end their turn during Free Road placement if dice were rolled before playing the card.
  Even if no free roads were placed, the Road Building card is not returned to their hand.
- When force-ending a turn (or connection lost) after playing Road Building but before placing
  the first free road, the Road Building card is returned to player's hand
- Server game cleanup: If the last human player leaves a game with bots and observers,
  don't continue that game as bots-only
- Server console: During startup, don't print connect messages for built-in robots
- Server closes connections to rejected clients or bots
- When member leaves a channel, don't send hostname to all members
- Standalone robot client: Print message at successful auth, instead of no output
- If new game options require a certain version, don't warn unless the required version
  is newer than `1.1.20` (released October 2016).

## `1.2.00` (build OV20171005)
- Simple sound effects for game events: Start of client player's turn, resource stolen by robber, etc
- Game windows have same size as previous game if resized, not small default size
- Re-word trade offer announcements to clarify who would give which resources
- Monopoly announces total number of resources stolen
- To ensure everyone has initial settlements, don't allow new clients to sit after 1st settlements are all placed
- To avoid disruptions by game observers, only players can chat after initial placement
- Client:
     - Persistent and per-game preferences for settings like sound effects and game window size
     - Per-game preference to auto-reject bot trades after a multi-second countdown
     - Re-worded other players' trade offer displays to: Gives You / They Get
     - More natural window positioning (follow OS standard, was previously always in upper-left corner)
     - Initial Connect dialog: If username given, ensure New Game button is enabled
     - New Game options: Popup if old versions can't play: Default to Create, not Change Options
- Users can't use the robot nickname prefixes "droid " or "robot ", or "debug" except in debug mode
- Network:
     - Send keepalive messages to idle games to keep clients connected
     - Text messages to channels can be sent only by members
- Database:
     - To create users, an Account Admins list is required (`jsettlers.accounts.admins` property)
       unless using Open Registration mode
     - Optional Schema Upgrade process with `-Djsettlers.db.upgrade_schema=Y` startup option
     - Upgraded Schema `v1.2.00` adds:
         - games table: winner, options, duration, player 5 and 6 names and scores
         - users table: case-insensitive unique usernames/nicknames; password encodings (BCrypt)
         - db_version table, with upgrade history if any
         - settings table
     - New admin command `*DBSETTINGS*`: Show schema version, DB server version, settings entries
     - If using mysql: Newly created DBs now have unicode text encoding (UTF-8).
       (The postgresql and sqlite DB scripts have always created the DB as unicode.)
     - If using postgresql: Tables are created by socuser, not postgres system user
- Game window during debug: Reset "current player" indicator when exiting `*FREEPLACE*` debug mode
- Client debug, bot debug: Print network message contents if system property `jsettlers.debug.traffic=Y` is set
- Startup: Show error if can't read own JSettlers version info


## `1.1.20` (build OV20161024)
- Board hex graphics updated for smoother look and scaling
- Game window board panel resize:
     - Much better performance and reliability by using good-quality synchronous Graphics2D.drawImage
     - Use smooth vectors, not scaled-up images, for ports
- Game window:
     - Guidance for new users: After initial placement, if user tries left-clicking the board to build,
       pop up a hint message to use right-click (or control-click on OSX) instead
     - Trade offers from other players: Show/hide Accept button whenever resources gained/lost/traded
     - Print message in chat area when player leaves, to balance message when a player joins
     - For visibility use black text for Longest Road, Largest Army labels
- New Game options dialog:
     - When "Use 6-player board" option becomes set, increase max players to 6 unless already changed by user
     - For int/intbool options use 0 if blank, intbool don't set int value if checkbox is unchecked
- `*ADDTIME*` command: Don't add time if more than 90 minutes still remaining
- `*HELP*` command recognized from all players, not only debug user
- Server startup options:
     - New optional jsserver.properties file, read before command line
     - Command line: db user and password now optional when specifying port and max connections
     - Unknown options no longer ignored: Prints each one and a short help message, will not continue startup
     - Default max connections increased from 30 to 40
- Game option defaults can be properties in that file or command line: `jsettlers.gameopt.RD=y`
- Game option boolean default values more strictly parsed
- Robots:
     - When a 7 is rolled during other players' turns and bot must discard:
       If bot is inactive after several seconds, force random resource discard
     - To keep idle practice games alive, don't leave an inactive game during other players' turns
- Server stats (`*STATS*` command): Increase "games finished" when game is won, not later when it's destroyed
- `*WHO*` command: User admins or debug user can list any game's members, or `*` or `ALL` to list all connected users
- Game or channel name `"*"` no longer permitted, to avoid conflicts with admin command enhancements
- Server console traces:
     - "joined the game"/"left the game" include current time
     - Remove redundant joined/left debug prints
- Debug commands dev: and rsrcs: also accept player number instead of name, for long or complex names
- Client startup: Always print version on console, even to print usage and exit
- Deactivate client debug prints of network message contents
- Optional user accounts:
     - Warn if database is empty when config requires accounts or names the account admins
     - DB setup script: Correct grant commands for postgresql 8 (for CentOS 6/RHEL 6)
- User account admin:
     - New server parameter `--pw-reset` username can be used if an account password is lost
     - `*WHO*` user-admin command available when only certain users can create accounts (`jsettlers.accounts.admins=...`)
- User account admin client:
     - After creating new user, clear password fields in form
     - Auto-authenticate when creating first admin account in new db
     - Minimum server version `1.1.19`; for older servers, please download the older version's Full JAR and use its account client
     - Server requires minimum client version `1.1.19`, to authenticate before creating users
- User account DB schema: For new installs, require user passwords (existing DBs don't need to make this change).
  Passwords were already required in earlier versions; this only formalizes it in the database.
- For bots in server jar, move `SOCDisplaylessPlayerClient` out of `soc.client` package
- First version to include an automated functional test script


## `1.1.19` (build OV20141127)
- New game option "N7C" for house rule: Roll no 7s until a city is built
- Bugfix when new client sits during first initial-settlement placement round
- Bugfix: potential roads now allowed next to opponent's newly placed settlement,
  if player already has a road touching the potential road
- Trading port/harbor graphics updated for directional clarity and scaling
- Reset board during initial placement: Pick randomly-selected robots, instead of keeping same robots
- Reset board: If only 1 human player, don't reset if all bot seats are locked and would be empty after reset
- On server startup, start some bots by default (previous versions required `-Djsettlers.startrobots`).
  To run a server without built-in bots, use `-Djsettlers.startrobots=0` when starting the server.
- Player chat text: At server, don't ignore messages which start with `'*'`
- For player consistency, don't allow seat lock changes during board reset vote
- New Game Options window: If server is too old for game options, show the game name field and no options here
- Client Connect to Server, Start Server screens: If port number field is empty, use default 8880
- Check client password when "New game" is clicked, not later after filling out game options
- Security: Reply with "incorrect password" when username doesn't exist in server's database
- Account Creation/Security when using the optional user account database:
     - By default, open registration is now disabled: Only existing users can create new accounts.
     - To permit open registration of accounts, use `-Djsettlers.accounts.open=y` when starting the server.
     - To require that all players have accounts and passwords, start the server with: `-Djsettlers.accounts.required=y`
     - To permit only certain users to create accounts, use `-Djsettlers.accounts.admins=bob,joe,lily`
       (comma-separated username list) when starting the server.
     - Once the client has successfully joined or created a game or channel, it won't send a password again.
- Database: Add instructions and db-create scripts for postgresql, sqlite
- When saving 6-player completed game results to a database table with only 4 player fields,
     rearrange positions to ensure human player in seat# 5 and/or 6 are recorded, especially if they won
- When client connects, server sends list of its active optional features.
     - If server does not use chat channels, the channel list is hidden from the main panel.
- Bugfix at game start: To fix occasional forced end turn for robot first player, update lastActionTime
- Bugfix in version tracking when player joins and replaces only bot in a game in progress
- Bots connecting require a security cookie, randomly generated at server startup.
     - The built-in bots started automatically by SOCServer will know the cookie value.
     - To set the cookie to a given value, set the jsettlers.bots.cookie parameter.
     - To print the cookie value in order to connect other bots, use `-Djsettlers.bots.showcookie=Y` when starting the server.
- Server stats include client versions seen since startup
- Server command line game option settings: Also allow y or Y for boolean options
- Server command line: Exit if any `-D` or `-o` parameter appears more than once
- On server startup, exit if a database URL is given but SOCServer can't connect to the database.
- Account creation: Check that the optional user-accounts server feature is active when connecting from account client
- Account creation: Check that requested username contains no reserved characters at server and client
- If new game options require a certain version, don't warn unless the required version
  is newer than `1.1.17` (released November 2012).


## `1.1.18` (build OV20130402)
- Reset board: Keep player chat text; Confirm before restarting after end of a practice game
- Chat text field: Word-wrap long lines
- Don't limit the number of simultaneous practice games
- 6-player board: Focus cursor on input field when the chat window expands; scroll chat/game text to bottom when it shrinks
- If saving completed games to db, save if any human players, even if some have left/rejoined
- Bugfix: Client creating a game on a server newer than itself might show a second New Game Options window
- In-game "Game Options" button: If an info window's already visible, show it instead of making another one
- Server `--help` message: Sort game option keynames alphabetically
- If new game options require a certain version, don't warn unless the required version
  is newer than `1.1.13` (released November 2011).


## `1.1.17` (build OV20121212)
- Road Building: Player may skip (cancel) placing second free road, if they want to use just one road piece
- Road Building: While placing first free road, don't enable Cancel in popup menu
- If jar client can't connect to server, returns to first panel, with buttons to connect or practice
- If try to start server in JSettlers.jar, but port already in use, show message instead of exiting immediately
- If server's debug commands are on, warn at connect
- Get Practice Game options from practice server, not from most recently started game
- If join a server after a practice game, re-enable name and password fields
- Chat text field: If a long line is truncated, keep the rest of it in the textfield
- Debug commands: dev cards: Send card type numbers with help message
- If server rejects bot's dev card play, bot sees that and tries a different move
- When player leaves game, don't send hostname to all players
- Server DB setup script: Ignore net errors when running script and exiting
- If server DB is empty, use default parameters for all bots
- Server constructors: throw exceptions instead of System.exit


## `1.1.16` (build OV20121027)
- Bugfix: `1.1.15` can't start practice games
- If jar client loses server connection, returns to first panel, with buttons
  to connect to a server or practice


## `1.1.15` (build OV20121021)
- Bugfix: Occasional hangs creating new game, when old game isn't yet cleared
- Bugfix: Hangs on mac osx 10.7, 10.8 after a few minutes (SnippingTextArea) - thanks olivierdeckers
- Server command line simplified: port number, max conns, db info now optional
- Can save all completed game results in database, with new option:
	`-Djsettlers.db.save.games=Y`
- Server db property for jdbc driver jar file: `-Djsettlers.db.jar=sqlite-jdbc-3.7.2.jar`
- Server db easy setup script options:
	`-Djsettlers.db.url=jdbc:sqlite:jsettlers.sqlite`  
	`-Djsettlers.db.script.setup=../src/bin/sql/jsettlers-tables.sql`
- Server db sqlite driver URLs updated in readme


## `1.1.14` (build OV20120930)
- Game can require more than 10 Victory Points to win (new game option "VP")
- Don't force-end bot turn if waiting for human discard
- Discard dialog has "Clear" button (sourceforge bug# 3443414)
- Show 'Server is ready' message at end of initialization
- At server shutdown, try to disconnect from database (helpful for sqlite)
- Debug commands are off by default, except practice games; you can enable them with:  
	`-Djsettlers.allow.debug=Y`
- Split out sql from README, add indexes (Chad McHenry mchenryc in 2005 cvs)


## `1.1.13` (build JM20111101)
- Game name maximum length is 30, was 20 previously
- Allow player to undo their last bank trade, if the undo is the very next thing they do,
     by trading the same resources back. For example, give back 1 brick to get back 3 sheep.
- Dice number layout is now clockwise or counterclockwise from 1 of
     several corners; previously always the same corner same direction.
- Show your player's total resource count (Rowan idea)
- 6-player board: New game option "PLB" allows using this board with 2-4 players
- 6-player board: Chat ease of use: Focus on text input field when any text area is clicked;
     to put the cursor in the text areas instead of the input field, click there again.
- Remember the player checkboxes chosen in previous trade offer
- If new game options require a certain version, don't warn unless the required version
     is newer than `1.1.08` (released January 2010).
- Warn on server startup if robots take up most of maxConnections
- On server startup, show each property's description
- If the graphical PlayerClient starts a server, the "server is running" text on-screen
     now also says "Click for info" (D Sawyer idea)
- New debug command for robots:  `botname:print-vars`
- If server forces a robot to end its turn, will print the bot's brain status
      variables and last two turns' received messages before it forces the end.
- If game is in progress, joining/leaving people also announced in chat area (less clutter there)
- Don't echo info commands like `*STATS*` to all players
- Add current total connection count to `*STATS*` (including connections not yet named)
- When clients arrive or depart, show both the named & total current connection count
      on console "(7,9)"; previously showed only named connections "(7)"
- Bugfix: If observer closes the game window, shouldn't ask them if want to keep playing
- Bugfix: Show "(cannot join)" in client's game list for unjoinable games sent when client connects
- Bugfix: Truncating player name, don't drop the first character
- New game option backwards-compatibility framework: allows use of some new game options (like PLB)
      with older clients, by changing related option values (like PL) sent to those old clients.
- Rename `SOCGame.isLocal` field to `.isPractice`
- License upgrade to GPL v3


## `1.1.12` (build JM20110122)
- Don't show hovering road/settlement/city if player has no more pieces
- Feedback if 'Road Building' clicked but 0 roads left
- Lock/unlock button for robot seats: add tooltip, fix label when first shown
- Bugfix: Robot 'No thanks' displays after bank trade
- When 6-player board's window loses focus, un-expand the chat area
- Clearer indication of when client is running a TCP server;
     can click the new "Server is Running" label for a popup with details.
- If game has observers, list them when client joins
- For debugging, new "Free Placement" mode; see README.developer
- Further encapsulate board coordinate encoding
- Javadocs and other explanations of board coordinate encoding


## `1.1.11` (build JM20101231)
- Popup to confirm before you move the robber onto your own hex
- Show robber's previous position on the board
- Robots: Force robot turns to end after several seconds of inactivity
- Bugfix: "Restart" button wasn't enabled if game ends after special build
- Bugfix: Couldn't place initial road on 6-player board's northernmost edge
- Fix infinite loop when robot leaves during game setup
- Game last-action time tracked, to detect idle games
- Debug commands now case-insensitive
- Per-game messages indicated by new interface SOCMessageForGame


## `1.1.10` (build JM20100613)
- Game owner tracked at server
- Security: Limit the maximum simultaneous games/chat channels created per client:
	- Once a game/channel is removed (all members leave), they can create another.
	- Defaults are 5 games, 2 channels.  Use these properties to change the default:    
	`jsettlers.client.maxcreategames`  
	`jsettlers.client.maxcreatechannels`


## `1.1.09` (build JM20100417)
- 4-player board: crisper graphics (images from 6-player board)
- Practice games don't expire (Rowan H idea)
- Show rounds remaining for "roll no 7s during first n turns" (Rowan H idea)
- When moving robber and choosing a victim, popup shows their # VPs
- 6-player board: Always allow to request special build, even if no resources.
     Also allowed at start of own turn, only if not rolled or played card yet,
     and not when you are the first player taking your first turn.
- 6-player: During Special Building Phase, a player can ask to Special Build after
     the phase has begun, even if this means we temporarily go
     backwards in turn order.  (Normal turn order resumes at the
     end of the SBP.)  The board game does not allow this out-of-order building.
- 6-player robots: Slow down a little: Pause 75% of 4-player's pause duration, not 50%
- At end of game, hilight winner with yellow arrow
- At end of game, show number of rounds, along with time elapsed and your resources rolled
- Game options: Change of wording in minimum-version warning: ("friendly" format)  
	from: Client version 1107 or higher is required for these game options.  
	to :  Client version 1.1.07 or newer is required for these game options.
- Double-clicking your face icon, or many rapid clicks, brings up the Face Chooser
- Allow 3rd-party Robot AIs, via new rbclass param in IMAROBOT message, SOCClientData.isBuiltInRobot
	Print robot type on connect (built-in, or rbclass name)
- Fix: Ask 2nd practice game options, when 1st is over but its window still showing
- Fix: robots: Handle CANCELBUILDREQUEST cleanly during states PLAY1 or SPECIAL_BUILDING
- Fix: For game's 1st client, set game.clientVersionLowest (was always 0 before now)
- 6-player window: Before expanding chat area when mouse enters it,
	wait 200 ms (not 100 ms) in case mouse is just passing through.
- Database: Hints on setup and usage of other db types in README.txt
- Database: default jdbc driver changed to com.mysql.jdbc.Driver,
	allow other db types via java properties (see README.txt)
- Database: troubleshooting: print error message details when the driver is
	available, but the database couldn't be accessed or loaded.
- When running local server: Main panel: Show version, buildnum in tooltip
- Command line: Error if dashed arguments appear after port/maxconns/db params
- Command line: Allow -Djsettlers.option=value syntax (mchenryc)
- Command line: Auto-start robots when the server starts, with this parameter:  
	`-Djsettlers.startrobots=7`
- Debug assist: SOCBoardLayout2 prints array contents
- Debug assist: Connection, LocalStringConnection +toString()
- README.developer: Coding Style section


## `1.1.08` (build JM20100112)
- 6-player board, with Special Building Phase rule
- Can now sometimes reconnect after connection to server is lost,
     when message "A player with that nickname is already logged in" appears.
- Smaller, cleaner building panel
- Rotated-board mode, to make it easier to fit a larger board
- Re-word counter offer text to: Give Them / You Get
- Cleaner scaled graphics: Draw hex dice-number circles on hex, instead of GIFs.
- Chat text prompt ("type here to chat") cleared when clicked (D Campbell idea)
- Fix button redraw for Discard, Year of Plenty popups on OSX
- Fix new-game options bg color on OSX Firefox 3.5+
- BoardPanel faster redraw: cache image of board without pieces
- BoardPanel javadocs explain nodeMap and initNodeMapAux
- SOCRobotBrain refactor some message-handlers out of run() (C McNeil idea)
- Old version history (pre-sourceforge): Added file src/docs/old-updates-rsthomas.html found on web at http://jrh-xp.byu.edu/settlers/updates.htm


## `1.1.07` (build JM20091031)
- Per-game options framework, including these options:
	- PL  Maximum # players (2-4)
	- RD  Robber can't return to the desert
	- N7  Roll no 7s during first # rounds
	- BC  Break up clumps of # or more same-type ports/hexes
	- NT  No trading allowed
- Re-word counter offer text
- Hide trade offer after rejecting counteroffer (John F idea)
- Allow debug commands in practice games
- New applet parameter "nickname" for use with dynamic html (Rick Jones idea)
- Framework for parsing "-" / "--" options at server commandline
- Refactor per-turn resets from many places to new game.updateAtTurn()
- GameList kept at server/client
- Bugfix: Could sit down at 2 positions due to network lag
- Rescaled board hex graphics now fall back to polygons if problem occurs
- Removed unused hex graphics from soc/client/images (clay0-5.gif, misc0-5.gif, ore0-5, sheep0-5, wheat0-5, wood0-5)
- Fewer disconnect-reconnect debug messages from robots during idle hours
- Don't cover board with 'choose player' popup (Rowan H idea)
- AskDialog supports multiple lines with "\n"


## `1.1.06` (build JM20090601)
- Based on 1.1.04's code
- Monopoly reports (privately) number of resources stolen to each victim
- Reset practice game, at end of game: New randomly-selected robots, instead of same robots each time
- STATUSMESSAGE can now carry an integer status value
- Track and understand client version starting from connect time, not just from joingame time.
- Can deny entry to individual games based on client's version (ex. client too old to understand a recent game feature, like 6 players)
- Fewer debug messages from robots during idle hours
- Many javadocs added
- Bugfix: Hangs on mac osx 10.5 after a few minutes (SnippingTextArea)
- Bugfix: After disconnect/rejoin, trade offer panel overlays your controls
- Bugfix: "Start a local server" ignored port-number textfield, was always default port
- Bugfix: harmless NullPointerException in SOCBoardPanel.setHoverText for getFontMetrics


## `1.1.05` (reverted before `1.1.06`)
JSettlers 1.1.05 had been under development (build 2008-09-13) but its direction is being re-considered.
Further development is based on 1.1.04.
- Use Log4j 1.2, vs previous homegrown soc.debug/disableDebug


## `1.1.04` (build JM20080906)
- Bugfix: Cancelling 2nd initial settlement, other players lost resources (SOCPlayer)
- Bugfix: Don't disable "play card" button after buying or playing a card (SOCHandPanel)
- Bugfix: Sometimes, "hovering" road or settlement wouldn't show during initial placement (SOCBoardPanel)
- Give player's win/loss count at end of game, unless first game (new class SOCClientData)
- Add StringConnection.appData, to support SOCClientData
- Javadoc adds/updates


## `1.1.03` (build 2008-08-26)
- Reset board: Bugfix: Practice games server version-check
- Don't show hovering road/settlement/city unless player has the resources
- "Play card" button: Disable after playing a card; Enable only at start of turn, not after buying a card
- Bugfix: At end of game, client sometimes incorrectly showed player 0 (Blue) as winner
- Javadocs clarify SOCPlayerClient local TCP vs practice server
- Add minor items to TODO in README.developer


## `1.1.02` (build 2008-08-17)  http://nand.net/jsettlers/devel/
- Reset board: If human leaves game before reset, lock their seat against robots
- Bugfix: Robot disconnect/reconnect version reporting
- Add minor items to TODO in README.developer


## `1.1.01` (build 2008-08-12)  http://nand.net/jsettlers/devel/
- Bugfix: If player loses connection while voting for board reset, the vote never completes
- Bugfix: Reset vote message format (from recent refactoring)
- Version number dynamic from properties file, not hardcoded in soc.util.Version
- Utility method SOCMessage.getClassNameShort for cleaner debug-output in template classes' toString


## `1.1.00`(build 2008-08-09)  http://nand.net/jsettlers/devel/
- Development at new site, sourceforge project appeared abandoned in 2005
- Much more visually responsive to game state
- User-friendly
	- Can right-click on board to build, right-click ports or resource squares to trade  [sf patch 1905791]
	- Can right-click face to choose a new face [sf patch 1860920]
	- Popup dialog buttons wrap if window too narrow
	- Robber doesn't disappear when must be moved, it just "ghosts" [sf patch 1812912]
	- Other minor improvements
- Local "practice-game" mode, if network connection or server is unavailable
- Play with 2-4 players, no longer requires 4
- Larger graphics on board, resizeable for higher-resolution screens [sf patch 1929452, based on images and code of rbrooks9's sf patch 1398331]
- Ability to reset board, during or after game  [sf feature req. 1110481]
- Can cancel and re-place initial settlement, if you haven't yet placed the road  [sf patch 1824441]
- More robust handling if client's connection to server is lost, even if current player
- Automatic dice roll after 5 seconds, if you have no playable card  [sf patch 1812254]
- At end of game, show hidden VP cards for all players  [sf patch 1812497]
- At end of game, give game duration and total connection time
- Announce when longest road/largest army is stolen
- Road-building allowed with 1 road [sf patch 1905080]
- Can win only on your own turn; if not your turn, must wait
- Less clutter in scrolling message area
- Confirm quit before closing window
- Show pieces when rejoining after lost connection
- Attempt to end turn, if current player leaves the game
- Client,server versioning; also add BUILDNUM property
- Can double-click jar for local server hosting (or run w. no arguments); player GUI will ask for IP and port#
- Robot bugfix, now will re-try if makes a bad piece placement
- More advance warning when game will soon expire
- Hilight who won when game is over
- Reminder to place 2 roads with road-building card
- Reminder to only play 1 card per turn
- Reminder when VP cards are played
- Trade offer's checkboxes track current player
- New graphics: images/robot1.gif; Removed obsolete: images/arrowL.gif, arrowR.gif
- Other sourceforge patches applied:
	- 1816668 jdmonin AWT debug help
	- 1816605 jdmonin Patch for #997263 cannot place road during game start
	- 1816581 jdmonin Fix server treater startup race
	- 1812257 jdmonin Debug help, minor comments
	- N/A     sfhonza (John Vicherek) "Swinging" number of resources, http://john.vicherek.com/jsettlers-1.0.6.swing_resources.patch
	- 1088775 drichardson (Douglas Ryan Richardson) [1039250] Auto-rejecting impossible offers; Make accept button invisible when user cannot accept offer


## Older versions:

These older releases were in a separate cvs repo at sourceforge
maintained by Robert S Thomas and Chad McHenry.

For historical reference, Jeremy Monin has converted that repo to git:
https://github.com/jdmonin/JSettlers1


## `1.0.6` (build 2004-11-17)  http://sourceforge.net/projects/jsettlers
- Fixed the same PORT property error in the Account client
- Fixed bug which could allow modified clients to invoke admin
  commands (`*STOP*`, `*KILLCHANNEL*`, etc) (Lasse Vartiainen)
- Fixed 920375, 1022157: mysql-connector-3.x fails: version 2.x works
  (Mezryn)
- Fixed 1060651: Bots crash if database backend is used (Jack Twilley)
- Moved more SQL error handling and reconnecting from SOCServer to
  SOCDBHelper correcting potential errors like 1060651


## `1.0.5` (build 2004-06-12)  http://sourceforge.net/projects/jsettlers
- Fixed an error introduced into the applet initialization which kept
  the PORT property from being read properly


## `1.0.4` (build 2004-06-10)  http://sourceforge.net/projects/jsettlers
- build.xml file added for Ant builds
- soc.util.Version class added so both build files and source code get
  version and copyright info from build.xml. Clients and server updated
- Build process creates two jar files: one for client, one for server
- README updated for jar file invocation, with additional sections for
  intro, requirements, hosting a server, and development
- Fix for inconsistent game state when players leave a game.
- Divider in chat window cannot be moved off-screen
- Text of game chat now correctly scrolls to bottom of text.
- Rewrite of much of the display code to address continuing display
  issues. Methods which directly manipulate GUI components can cause
  race conditions, and are now never called from main networking
  thread.
- Removed calls to deprecated methods
- Images can now be loaded from files (on server or not) or from
  within jar.


## `1.0.3` (build 2004-03-29)
- Continuing to fix the display bug in the SOCPlayerClient


## `1.0.2` (build 2004-03-26)
- Fixed display bug (again) in the SOCPlayerClient when run as a stand
  alone.


## `1.0` (build 2004-03-14)
- First release. See the README file for how to setup a server and
  robot clients.


## Older versions

This chapter contains the contents of the old `old-updates-rsthomas.html` file
written by Robert S. Thomas. It's kept here for historical reference. The
contents are preserved as much as possible, the formatting has been adapted
to Markdown format where possible.

### 2004-03-15

I have recently created a SourceForge project called
[jsettlers](http://sourceforge.net/projects/jsettlers/) to
maintain the Java Settlers code base. There you can access a copy of my
dissertation which describes how the system as well as the bots work.
Also, you can download the Java Settlers class files as well as brief
instructions on how to run your own server. In addition, I have made the
source code available under the [Gnu Public License](http://www.gnu.org/copyleft/gpl.html). You
can access it using CVS at cvs.sf.net/cvsroot/jsettlers. If you are
interested in being part of a development team to continue improving Java
Settlers, please let me know.

### 2002-11-21

In order to fight the problem of the server
getting clogged with dead games, I've implemented a time limit system for
all games. When a game is created it has a lifetime of 90 minutes. Anyone
in the game can check how much time is remaining by typing `*CHECKTIME*`.
When the game only has 5 minutes left a warning will be issued to the
people in that game. To extend a game, simply type `*ADDTIME*` to add
another 30 minutes. This can be done at any time. Hopefully this will
result in a more stable server with less lag.

### 2002-10-17

I've had a number of requests recently for a
solution to the problem of how to deal with obnoxious players. As a quick
fix, I've added a way to ignore the chat messages from other players. Here's how it works:

- To ignore another player, type `\ignore <nickname>`
Where `<nickname>` is the name of the person you want to ignore.
This will add their name to the list of people you're ignoring.
- To stop ignoring a player, type `\unignore <nickname>`
This will remove their name from your list.

These commands work in both game and channel windows and the list will be maintained as long as you are connected to the server. If the commands aren't working, it's probably because you're not using the most recent version of the client. To get the most recent version, simply close your web browser and then run it again to load the Java Settlers page. The latest version of the client should automatically be downloaded to your computer.

If you're having trouble loading the client (you only see a grey box when you load the page), you need to update your java plug-in by going here. After doing that, restart your computer and you should be able to access the site again.

### 2002-04-13

I'm trying out some variations of the robot decision making algorithms. The 'bots have strange suffixes
added to their names so I know which algorithms each one is running.

### 2002-02-15 

I figured out a way to make scroll bars work correctly on both Mac and PC, so now the interface has
them. I also modified how the face button works. If you click on the right
side of the face, it will advance to the next one. If you click on the
left side, it will go back. And I added some new faces, so check 'em out!

### 2001-08-30

I've added an account system to the site. *It is completely optional.* If you go to the
[account creation page](http://settlers.cs.northwestern.edu/account.html)
and make a new account, you will need to enter you
password when you use the system. The benefit of creating an account is
that no one will be able to use your nickname without the password.

### 2001-08-03

I've set up some scripts to restart the server every day at 4:00am CST. This is a temporary fix for
any bugs that take more than a day to manifest.

### 2001-07-18

Fixed a couple bugs in the 'bot strategy code. 

### 2001-07-16

Now the server will list any victory point cards that the winner has when he or she wins. 

### 2001-07-02

I made the seat lock button smaller so that it doesn't cover the counter offer buttons. 

### 2001-07-01

Fixed some more bugs and I've added a new feature. Now when you sit at a game you will see a button in
the other players' panels labled "Lock This Seat" or "Unlock This Seat".
This button will only show for players that are 'bots. If you lock a seat,
that prevents other people from booting the 'bot and sitting down. I added
this because people were requesting a function that would make games
private, and also for a way to boot people. I don't really like the idea
of booting people, so this is a compromise. Let me know what you think.

### 2001-06-28

Still working on some bugs in the networking code, but I think it's getting better. I also added a cancel button on the counter offer box. 

### 2001-06-27

Joseph Landry (jal) read my FAQ about the randomness of the numbers and sent me an improved formula to get a better
distribution. I thought I would put his email here just in case anyone else has made the same mistake I did:

Your FAQ page says that your random number generator is

``` java
int die1 = (int)(Math.round(Math.random() * 5.0) + 1);
int die2 = (int)(Math.round(Math.random() * 5.0) + 1);
int currentDice = die1 + die2;
```

`Math.random()` produces a number between `0` and `0.999` correct?
(I'm only printing to 3 decimal places for all this)
 
Then the resultant numbers passed to Math.round() that range from `0 to 4.999`

    0.000 - 0.499 -> 0 + 1 = 1
    0.500 - 1.499 -> 1 + 1 = 2
    1.500 - 2.499 -> 2 + 1 = 3
    2.500 - 3.499 -> 3 + 1 = 4
    3.500 - 4.499 -> 4 + 1 = 5
    4.500 - 4.999 -> 5 + 1 = 6
 
As you can see from this chart, the 1 and 6 are only half as likely to
get rolled as 2,3,4,and 5.
 
Shouldn't your formulas be...

``` java
int die1 = (int)(Math.round(Math.random() * 6.0 + 0.5) ;
int die2 = (int)(Math.round(Math.random() * 6.0 + 0.5) ;
int currentDice = die1 + die2;
```

This produces numbers passed to `Math.round()` that range from `0.5` to `6.499`
 
    0.500 - 1.499 -> 1
    1.500 - 2.499 -> 2
    2.500 - 3.499 -> 3
    3.500 - 4.499 -> 4
    4.500 - 5.499 -> 5
    5.500 - 6.499 -> 6
 
Now all numbers are evenly distributed.

Thanks for the help Joe!

### 2001-06-26

Fixed some more bugs so that the server is more
stable. I also added a FAQ list. There is a link to it on the front page.
You might have noticed that some new faces have been added to the
interface. I didn't create these, actually someone who uses the site sent
them to me, and I think they're great! If you would like to add more faces
to the collection, all you need to do is make a 40 by 40 pixel gif with a
transparent background and email it to me. Then you'll be able to choose
the face that you created when you play, and other players will get to
appreciate your skill as an artist. ;)

### 2001-06-21

As promised, I've updated the negotiation code
for the 'bots. They will now only make offers that they think another
player will take. This cuts down the number of offers they make, and their
offers make a bit more sense. Also, they will only try to make offers to
players that they think have what they want. Sometimes they will make an
offer to you even if you don't have what they're asking for. This is
because they will loose track of what you have if you discard cards, or
get robbed. One more thing, just because a 'bot rejects your first offer
that doesn't necessarily mean that they don't have what you want. Try
making the deal better and they might take it.

### 2001-06-17

I'm trying out a new addition to the interface. Now when an offer is presented, you will have three options: Accept, Reject, and Counter. The Counter button allows you to easily make a counter offer. If you have any comments either good or bad about the new button, please let me know.

Note: I'm still running the old 'bot code which doesn't react to counter offers, so I would NOT recommend making counter offers to 'bots. Very soon I will have new 'bots that will make and consider counter offers. 

### 2001-06-13 (later that day)

I need to fix some timing bugs that only showed up when I
had a bunch of people using the system. In the mean time I'll run the old
faster 'bots.

### 2001-06-13

Modified the trading algorithm so that robots will make counter offers rather than just saying "That deal isn't good for me.". The result is that the robots reach an agreement or an impass faster with fewer offers made. Also, the experiment is over and all robots are using the same code now. 

### 2001-05-31

Ok, this is a big update. I fixed a bunch of bugs and I changed the trading algorithm in a major way. Now the 'bots will make multiple offers if it thinks that you want them. The way you signal to the 'bots that you're willing to sell but are waiting for a better deal is to say something like, "Gimme a better deal." before you hit the Reject button. Actually, you can say anything as long as it has the word "deal" in it, and you'll have conveyed the message. If the 'bot has other deals in mind, it will put them up. You can also make counter offers to the 'bots for a similar effect. Another addition is that the 'bots will give some feedback as to why they rejected an offer. This may get annoying, so I might change it in the future. Now on the to-do list is to have some way to communicate better with the 'bots so that they don't just rattle off a series of offers. Oh, I'm also doing an experiment, so some of the bots have the new trading stuff, and other's do not. It should be obvious which is which when you play against them. 


### 2001-05-08

Fixed some bugs. 

### 2001-05-06

I've updated the strategy component for the 'bots one last time. I think they play better than they did before, but because their trading algorithm is so dumb, it's hard to tell. Next step is to improve the trading algorithm. Then after that, I'll write my dissertation and be in the home stretch. 

### 2001-04-25

Changed the strategy for the bots again, but now they play worse. I'll fix it as soon as I get some time to program again. Also, still haven't figured out what is causing the bots to stop 

### 2001-04-06

Changed the strategy for the robots in a major way. It's not quite finished yet, but I thought I would
let it loose on the public anyway because it plays very differently. I
also added some code to bring back dead robots, so there should be fewer
times when you can't get a robot to play.

### 2001-03-21

Did some minor cosmetic stuff. The robber is drawn a little to the right, so you can read the number on the hex. Also the server will tell you what dice were rolled instead of just the sum. 

### 2001-03-11

I fixed a bunch of bugs including (hopefully) the longest road bug. Big thanks to all the people who sent me bug reports! 

### 2001-03-06
Trying something new in the networking code. Removed the game status code for now. 

### 2001-03-05

I've cleaned up some of the code that the 'bots use to decide where to build. This should make them hang less often. Also,
I'm looking for a bug in the code that calculates Longest Road. If you are
playing a game and the wrong player has longest road, please open your
Java Console, clip 10 to 20 lines from the output and email it to me along
with a description of what happened in the game just before the bug
happend. If you can send a screen shot too that would be helpful. I know
there's a bug somewhere, but I haven't been able to reproduce it, so maybe
I can find it with your help. Thanks! I've also put the game status code
back in to see if it will crash the server again. I'm thinking that it
will, but it will also help me find any stray deadlock problems that are
left in the network code.

### 2001-02-21

Wow, traffic on the server is increasing and therefore it's crashing more
often. I'm trying an experiment by removing the game status feature that
was next to the name of the game. I think this might help because I was
broadcasting the game info across all of the connections a lot, and by
removing it I hope to cut the ammount of message traffic that the server
has to deal with. I really like that feature though, so I might work on a
way to get the same functionality without using broadcast. Hopefuly this
change will allow the server to stay up longer because I can't see where
the problem is. It's not running out of memory, and I can't find any
deadlock conditions. Hmm...

### 2001-02-19 (later that day)

Ok, I just fixed a bug where if a game was started and a player sat and then left, no one could sit in that spot. The game server has been running for over 5 hours now without locking up, so I'm taking that as a good sign. 

### 2001-02-19

I'm still trying to fix the latest major bug in the server, and therefore the server will be going down a lot. Thank you for your patience during this rough period. 

### 2001-02-16

Hello Settlers playin' folks! I'm sorry the server has been down for a bit. I found a nasty deadlock condition and needed a day to work out the problem. I think I got it, but the real test is running it for a day with lots of people connecting, so here we go (cross your fingers). 

### 2001-02-12

In response to your feedback, I've modified the game list to display whether or not robots are playing in a particular game. A '#' next to a score means that a robot is in that seat. A 'o' next to a number means a person is in that seat.

Another change I've made is with how the robots trade. Now thay will only make offers to players that they think can actually give them what they want. So now you won't see robots offering to trade with players that have no resources. For those that are curious about how much information the robots have, they rely on the exact same information that human players use. They watch the dice and can see the resources being handed out. They can't see things like what resources were discarded after a roll of a 7, or what resource was stolen from another player. So they may not offer to trade with you even though you have what they want. If this happens, try making a counter offer. They'll probably take it as long as they don't think you're about to win.

One more thing. I've made it so that you can't boot a robot in the middle of its turn. This should cut down on the number of hung games. 

### 2001-02-09

Added a new feature to the game list. Next to each game you will see something like this: [2 2 3 4]. This is a list of the scores for the players in that game. If you see a "-", that means that a seat is open. I'm hoping this will help poeple find games with empty seats easier. Also you can see how far a game has progressed in case you want to join a game that has just started. 

### 2001-02-07

Fixed some bugs and modified the robots to have a stronger end-game strategy.

### 2001-01-29

The computer players can now trade. The algorithm they use to decide what offers to make and accept isn't very
sophisticated at all. I just wanted to make sure that the trading
mechanism worked first before making it "smart". If the 'bots reject your
offer, it's because either they don't have what you want, they don't want
to give up what you want, or they think you're winning. You can tell when
they think you're winning when they stop offering trades to you. Also, all
of the code for the game server and computer players is running on new
faster hardware. In the queue is improving the players end-game strategy,
improving the initial settlement placement, adding the ability to boot
human players, and of course improving the trading algorithms.

### 2001-01-16

Fixed some more bugs and improved the algorithm that trades with the bank.

### 2001-01-12
Whew! It's been a long time since the last update. This update is just bug fixes. The 'bot's should be a little faster and smarter, but not much. Next I'll be improving the 'bot strategy and adding trading.

### 2000-09-21

Mostly new code for the bots. Features include:

- Threat detection
- Ability to estimate how close a player is to winning
- Faster execution
- A simple notion of longest road potential
- Seperate strategies for the beginning, middle, and end game.

Right now, the end game strategy isn't implemented, so the bot's may give up near the end. Also, things like the initial settlement placement and robber code haven't been updated to use the new code, so the bot's play may be a little strange.

Things to do:

- End game strategy
- Use new information to make better decisions on initial placement
- Add code to watch the lead player and look for ways to slow him down
- Do a first pass at negotiation
- Maybe have it make comments

### 2000-08-04

Computer doesn't discard at random anymore. Now it throws away resources it can't use right away, and after that, resources that are easier for it to get. 

Here is current to-do list for the computer players:

- Have them trade with other players. This includes:
	- Initiating as well as taking offers
	- Evaluating the worth of the trade (is it as good for me as it is for the other guy)
	- Using the chat window as well as the trading tool
- More sophisticated planning:
	- Evaluate moves that are multipurpose, like building a road that gives you longest road and sets you up for a good settlement
	- Pay attention to where there is a "race" with another player
	- Consider building extra roads or knights to secure longest road or largest army
	- Do things to thwart or delay other player's plans
- Use the chat window:
	- Trash talking
	- Commentary
	- Convincing players to do things like cut off a longest road, etc

### 2000-08-02

The computer players can now use Development cards. Also, they play a little slower because I
expanded their planning code, so please be patient.

### 2000-06-12

The computer uses some strategy when moving the robber, rather than just moving it randomly.

### 2000-06-08

- The computer does a better job of deciding where to build.
- Added a seperate window for chat messages.
- Fixed a scrolling problem with Netscape browsers.
- Fixed a problem where people watching the game couldn't see trade offers.
- You can now access the server multiple times from the same computer.
  You're on your honor not to abuse this for cheating.

### 2000-05-15
The computer players will now trade with the bank and any ports that they might be on. Also sped up the algorithm that determines who has the longest road. 

### 2000-05-10
Computer players do a better job of building. Still working on getting the computer to trade with the bank and ports. 

### 2000-05-03
Now the computer players can build. It's not great, but its a start. I'm currently working on better algorithms for where to build, and getting the computer to trade with the bank and ports.  
