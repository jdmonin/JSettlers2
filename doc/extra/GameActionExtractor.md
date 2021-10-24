soc.extra.robot.GameActionExtractor

Recognizes and extracts basic higher-level actions in a game from its message sequences
in a `soc.extra.server.GameEventLog`.

Can be used by bots or any other code which wants to examine a game's logs and actions.

## Approach

Based on [Message-Sequences-for-Game-Actions.md](../Message-Sequences-for-Game-Actions.md)'s
list of the message sequences for all the recognized game actions.
Analyze to find common starting messages and build a decision tree from sequences to game actions.
Read through the game's event messages looking for a starting message that's among the starting points of that tree.
If a sequence doesn't match any known sequence, scan forward until a starting message is shown,
and group the unknown messages before it for possible later analysis.

More info and some consistency testing of those message sequences:
- `/src/test/java/soctest/server/TestRecorder.java` `testLoadAndBasicSequences()` runs through some basic game actions
- `/src/extraTest/java/soctest/server/TestActionsMessages.java` runs through the rest of them
- `/src/test/resources/resources/gameevent/all-basic-actions.soclog` has all of these sequences and some non-sequence messages

## Analysis: Decision tree to recognize sequences as game actions

Server and client version 2.5.00 or newer.
Analysis ignores all `SOCGameServerText`, since server may not localize and send text to bots.
Tree is used to determine the game action based on sequence seen.

Will want to track current player, current game state.

Message sequence beginnings, roughly in same order as in [Message-Sequences-for-Game-Actions.md](../Message-Sequences-for-Game-Actions.md):

- all:SOCTurn
    - SOCTurn's gameState=15 -> Begin regular turn
    - SOCTurn's gameState=100 -> Begin SBP
- f3:SOCRollDice -> Roll Dice
- f3:SOCPutPiece -> Build piece
- f3:SOCBuildRequest
    - Own turn -> Build piece
    - Another player's turn -> Ask Special Building
- f3:SOCMovePiece -> Move ship
- f3:SOCBuyDevCardRequest -> Buy dev card
- f3:SOCPlayDevCardRequest -> Play dev card
- f3:SOCDiscard -> Discard
- f3:SOCPickResources, all:SOCPickResources, all:SOCPlayerElement(NUM_PICK_GOLD_HEX_RESOURCES = 0) -> Choose free resources (Gold hex)
- f3:SOCChoosePlayer
    - gameState 54 -> Choose to move robber or pirate
    - gameState 55 -> Choose to steal cloth or a resource
- f3:SOCMoveRobber
    - gameState 33 -> Move robber
    - gameState 34 -> Move pirate
- p3:SOCChoosePlayerRequest -> Choose player to rob from
- p3:SOCReportRobbery -> Rob a player
- f3:SOCBankTrade -> Bank trade (usual or undo previous)
- f3:SOCMakeOffer -> Make trade offer
- f3:SOCRejectOffer -> Reject trade offer
- f3:SOCAcceptOffer -> Accept trade offer
- f3:SOCEndTurn -> End turn (usual or Special Building)
- all:SOCGameElements(CURRENT_PLAYER), all:SOCGameState(state=1000) -> Game Over
