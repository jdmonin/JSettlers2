Message Sequences for Game Actions

# Overview

This is a list of the basic actions players can take in a game (roll dice, build settlements, etc)
and the network message sequences that convey them, for reference.

Doesn't include some scenario-specific actions such as placing a "gift port" in the Forgotten Tribe.

These messages assume server and client version 2.5.00 or newer.
That version updated and reorganized many sequences to be more efficient
and easier for bots and other automated readers to recognize.
Since the server and built-in robots are packaged together,
the bots also use these updated message sequences.
The server uses the latest version format to record the game event sequences
it sends, even when sending other more-compatible messages to an older client.

If you're curious about older versions, see the code and comments in
server classes like `SOCGameMessageHandler` and `SOCGameHandler` which communicate with clients.

Sequences are tested for consistency during unit tests and release testing:
- `/src/test/java/soctest/server/TestRecorder.java` `testLoadAndBasicSequences()` runs through some basic game actions
- `/src/extraTest/java/soctest/server/TestActionsMessages.java` runs through the rest of them
- `/src/test/resources/resources/gameevent/all-basic-actions.soclog` has all of these sequences and some non-sequence messages
  (debug commands, a client joins the game, etc).

For sample code which recognizes and extracts game actions from these sequences,
see [GameActionExtractor.md](extra/GameActionExtractor.md).


# Game actions and their message sequences

This list isn't exhaustive; some sequences like Roll Dice
vary depending on game options and scenarios. All sequences
document their starting and ending messages, which can be relied on
regardless of game options or scenarios in use.

All `SOCGameServerText` should be considered optional or ignored,
since server may not localize and send text to bots.

The format used here is from `soc.extra.server.GameEventLog.QueueEntry.toString()`.
It shows the origin of each message from a client player (`f3:`)
or the audience of each message from server (`all:`, `p3:`, etc).
See toString() javadoc for details. In this doc we use "//" for comments about a message.

Player number 3 is current in most of these example sequences.

## Roll dice

Many different actions and game states can happen when the dice are rolled, especially when playing a scenario.
The overall structure of the server's response to a player's `SOCRollDice` request is:

- SOCDiceResult with the dice amount
- If not 7, SOCDiceResultResources and SOCPlayerElement
- Sometimes other various messages to entire game and/or some players
- SOCGameState is always the last message in sequence sent to entire game
- Sometimes new state text for human clients
- Sometimes prompts for action sent to individual players (discard, pick free resource from gold hex, move robber, etc)

See `soc.message.SOCDiceResult` javadoc for more details.

### Roll 7

The text at the end and to-client prompts vary by game rules/options.
Players can ignore messages after `SOCGameState` unless sent specifically to them
as a prompt or request.

Some examples:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=7
- all:SOCGameState:game=test|state=50
- all:SOCGameServerText:game=test|text=p2 needs to discard.
- p2:SOCDiscardRequest:game=test|numDiscards=4

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=7
- all:SOCGameState:game=test|state=33
- all:SOCGameServerText:game=test|text=p3 will move the robber.

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=7
- all:SOCGameState:game=test|state=54
- all:SOCGameServerText:game=test|text=p3 must choose to move the robber or the pirate.

### Roll other than 7

Some examples:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=9
- all:SOCDiceResultResources:game=test|p=2|p=2|p=4|p=1|p=4|p=0|p=3|p=7|p=1|p=4
- p2:SOCPlayerElements:game=test|playerNum=2|actionType=SET|e1=2,e2=0,e3=0,e4=1,e5=1
- p3:SOCPlayerElements:game=test|playerNum=3|actionType=SET|e1=0,e2=2,e3=1,e4=3,e5=1
- all:SOCGameState:game=test|state=20

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=2
- all:SOCGameServerText:game=test|text=No player gets anything.
- all:SOCGameState:game=test|state=20

Or:

- f2:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=4
- all:SOCDiceResultResources:game=test|p=1|p=2|p=4|p=1|p=2
- p2:SOCPlayerElements:game=test|playerNum=2|actionType=SET|e1=0,e2=1,e3=0,e4=3,e5=0
- all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1  // NUM_PICK_GOLD_HEX_RESOURCES
- p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0
- all:SOCGameState:game=test|state=56

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=8
- all:SOCDiceResultResources:game=test|p=2|p=2|p=8|p=1|p=2|p=2|p=4|p=0|p=3|p=4|p=2|p=2
- p2:SOCPlayerElements:game=test|playerNum=2|actionType=SET|e1=0,e2=3,e3=2,e4=3,e5=0
- p3:SOCPlayerElements:game=test|playerNum=3|actionType=SET|e1=0,e2=3,e3=0,e4=0,e5=1
- all:SOCPieceValue:game=test|pieceType=5|coord=2057|pv1=1|pv2=0
- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=106|amount=5  // SCENARIO_CLOTH_COUNT
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=106|amount=7
- all:SOCGameServerText:game=test|text=robot 5 and p3 each received cloth from the villages.
- all:SOCGameState:game=test|state=20

## Buy and build piece

### Initial Placement

The placement message sequence is different during initial placement. Server assumes client knows the rules,
and pieces have no cost, so it may send SOCTurn instead of SOCGameState and won't send SOCPlayerElements.

Example with player 2's first initial road, second initial settlement:

- f2:SOCPutPiece:game=test|playerNumber=2|pieceType=0|coord=907
- all:SOCGameServerText:game=test|text=p2 built a road.
- all:SOCPutPiece:game=test|playerNumber=2|pieceType=0|coord=907
- all:SOCGameServerText:game=test|text=It's p2's turn to build a settlement.
- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=19|amount=0
- all:SOCTurn:game=test|playerNumber=2|gameState=10

(p2 decides on a location to build)

- f2:SOCPutPiece:game=test|playerNumber=2|pieceType=1|coord=809
- all:SOCGameServerText:game=test|text=p2 built a settlement.
- all:SOCPutPiece:game=test|playerNumber=2|pieceType=1|coord=809
- all:SOCGameState:game=test|state=11
- all:SOCGameServerText:game=test|text=It's p2's turn to build a road or ship.

### Settlement

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=804
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1
- all:SOCGameServerText:game=test|text=p3 built a settlement.
- Occasionally extra messages here, depending on game options/scenario. Example:
    - all:SOCSVPTextMessage:game=test|pn=3|svp=2|desc=settling a new island
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=104|amount=4  // SCENARIO_SVP_LANDAREAS_BITMASK
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=102|amount=2  // SCENARIO_SVP
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=60a
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING

Or if client starts with build request:

- f3:SOCBuildRequest:game=test|pieceType=1
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1
- all:SOCGameState:game=test|state=31
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=67
- all:SOCGameServerText:game=test|text=p3 built a settlement.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=67
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING

### City

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=a08
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=3,e4=2
- all:SOCGameServerText:game=test|text=p3 built a city.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=a08
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING

Or if client starts with build request:

- f3:SOCBuildRequest:game=test|pieceType=2
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=3,e4=2
- all:SOCGameState:game=test|state=32
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=67
- all:SOCGameServerText:game=test|text=p3 built a city.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=67
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING

### Road (may set Longest Route)

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=809
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e5=1
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=2312|hexType=4|diceNum=12
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=809
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=4|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 wheat by revealing the fog hex.
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING or (gold fog hex revealed) 56 WAITING_FOR_PICK_GOLD_RESOURCE
- If revealing a fog hex as gold:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

Or if client sends build request:

- f3:SOCBuildRequest:game=test|pieceType=0
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e5=1
- all:SOCGameState:game=test|state=30
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=56
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=1288|hexType=5|diceNum=10
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=56
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=5|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 wood by revealing the fog hex.
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING
- If revealing a fog hex as gold:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

### Ship (may set Longest Route)

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=80a
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e3=1,e5=1
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=1801|hexType=7|diceNum=9
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=80a
- If gaining Longest Route: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- If revealing a fog hex as non-gold: SOCPlayerElement and SOCGameServerText (see Road above for details)
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING or (gold fog hex revealed) 56 WAITING_FOR_PICK_GOLD_RESOURCE
- If revealing a fog hex as gold:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

Or if client sends build request:

- f3:SOCBuildRequest:game=test|pieceType=3
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e3=1,e5=1
- all:SOCGameState:game=test|state=35
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=602
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=1539|hexType=1|diceNum=5
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=602
- If gaining Longest Route: all:SOCGameElements:game=test|e6=3
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=1|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 clay by revealing the fog hex.
- all:SOCGameState:game=test|state=20  // or others as noted above
- If revealing a fog hex as gold:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

## Move piece (move ship)

- f3:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=3078|toCoord=3846
- all:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=3078|toCoord=3846
- If gaining Longest Route: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER

## Buy dev card

- f3:SOCBuyDevCardRequest:game=test
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=1,e3=1,e4=1
- all:SOCGameElements:game=test|e2=22  // DEV_CARD_COUNT; amount varies
- p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=5 // type varies
- !p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=0
- all:SOCSimpleAction:game=test|pn=3|actType=1|v1=22|v2=0  // v1 amount same as in SOCGameElements(e2)
- all:SOCGameState:game=test|state=20  // or 100 SPECIAL_BUILDING

## Use/Play each dev card type

### Road Building

- f3:SOCPlayDevCardRequest:game=test|devCard=1
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=1
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCGameServerText:game=test|text=p3 played a Road Building card.
- If player has only 1 remaining road/ship, skips this section:
- all:SOCGameState:game=test|state=40
- p3:SOCGameServerText:game=test|text=You may place 2 roads/ships.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704  // or pieceType=3 for ship for any/all SOCPutPiece in this sequence
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704
- If gains Longest Route after 1st placement: all:SOCGameElements:game=test|e6=3
- If player has only 1 remaining, skips to gamestate(41) and omits the above section
- all:SOCGameState:game=test|state=41
- If player has 1 remaining: p3:SOCGameServerText:game=test|text=You may place your 1 remaining road.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
- If gains Longest Route after 2nd placement: all:SOCGameElements:game=test|e6=3
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD) if played before dice roll

### Year of Plenty/Discovery (see also "Gold hex gains" sequence)

- f3:SOCPlayDevCardRequest:game=test|devCard=2
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=2
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1  // PLAYED_DEV_CARD_FLAG
- all:SOCGameServerText:game=test|text=p3 played a Year of Plenty card.
- all:SOCGameState:game=test|state=52
- f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0
- all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|pn=3|reason=2
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)

### Monopoly

- f3:SOCPlayDevCardRequest:game=test|devCard=3
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=3
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCGameServerText:game=test|text=p3 played a Monopoly card.
- all:SOCGameState:game=test|state=53
- f3:SOCPickResourceType:game=test|resType=3
- From the victim players, if any:
- all:SOCPlayerElement:game=test|playerNum=1|actionType=SET|elementType=3|amount=0|news=Y
- all:SOCResourceCount:game=test|playerNum=1|count=7
- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=3|amount=0|news=Y
- all:SOCResourceCount:game=test|playerNum=2|count=2
- To the current player:
- all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=3|amount=6  // or amount=0 if none gained
- all:SOCSimpleAction:game=test|pn=3|actType=3|v1=6|v2=3  // RSRC_TYPE_MONOPOLIZED
- p1:SOCGameServerText:game=test|text=p3's Monopoly took your 5 sheep.
- p2:SOCGameServerText:game=test|text=p3's Monopoly took your 1 sheep.
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)

### Soldier (may set Largest Army)

- f3:SOCPlayDevCardRequest:game=test|devCard=9
- all:SOCGameServerText:game=test|text=p3 played a Soldier card.
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=9
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=15|amount=1  // NUMKNIGHTS
- If Largest Army player changing: all:SOCGameElements:game=test|e5=3  // LARGEST_ARMY_PLAYER
- all:SOCGameState:game=test|state=33  // or 34 (PLACING_PIRATE), 54 (WAITING_FOR_ROBBER_OR_PIRATE), or other states

## Actions which happen because of other game actions

### Discard

- f2:SOCDiscard:game=test|resources=clay=0|ore=0|sheep=2|wheat=0|wood=3|unknown=0
- p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=3|amount=2
- p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=5|amount=3
- !p2:SOCPlayerElement:game=test|playerNum=2|actionType=LOSE|elementType=6|amount=5|news=Y
- all:SOCGameServerText:game=test|text=p2 discarded 5 resources.
- all:SOCGameState:game=test|state=33  // or other: choose robber or pirate, etc
- all:SOCGameServerText:game=test|text=p2 will move the robber.  // optional, varies based on new game state

Or if other players still need to discard:

- f3:SOCDiscard:game=test|resources=clay=0|ore=0|sheep=5|wheat=0|wood=1|unknown=0
- p3:SOCPlayerElement:game=test|playerNum=3|actionType=LOSE|elementType=3|amount=5
- p3:SOCPlayerElement:game=test|playerNum=3|actionType=LOSE|elementType=5|amount=1
- !p3:SOCPlayerElement:game=test|playerNum=3|actionType=LOSE|elementType=6|amount=6|news=Y
- all:SOCGameServerText:game=test|text=p3 discarded 6 resources.
- all:SOCGameServerText:game=test|text=p2 needs to discard.
- // No final SOCGameState message, since state is still 50 (WAITING_FOR_DISCARDS)

### Choose free resources (Gold hex gains; see also "Year of Plenty/Discovery" sequence)

- f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0|pn=3|reason=3
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=0  // NUM_PICK_GOLD_HEX_RESOURCES
- all:SOCGameState:game=test|state=20  // or another state, like 56 if another player must also choose

### Choose to move robber or pirate

In gameState 54 (WAITING_FOR_ROBBER_OR_PIRATE):

- all:SOCGameServerText:game=test|text=p3 must choose to move the robber or the pirate.
- f3:SOCChoosePlayer:game=test|choice=-2  // or -3 (CHOICE_MOVE_PIRATE)
- all:SOCGameState:game=test|state=33  // or 34 (PLACING_PIRATE) or other states

### Move robber

In gameState 33 (PLACING_ROBBER):

- f3:SOCMoveRobber:game=test|playerNumber=3|coord=504
- all:SOCMoveRobber:game=test|playerNumber=3|coord=504
- all:SOCGameServerText:game=test|text=p3 moved the robber.
- If any choices to be made:
- all:SOCGameState:game=test|state=20  // or choose-player, choose-resource-or-cloth, etc
- Otherwise next message is SOCReportRobbery from server, which isn't part of this sequence

### Move pirate

In gameState 34 (PLACING_PIRATE):

- f3:SOCMoveRobber:game=test|playerNumber=3|coord=-90c
- all:SOCMoveRobber:game=test|playerNumber=3|coord=-90c
- all:SOCGameServerText:game=test|text=p3 moved the pirate.
- all:SOCGameState:game=test|state=20  // or another state, same as Move robber

### Choose player to rob from

Occurs after moving robber or pirate.

In gameState 51 (WAITING_FOR_ROB_CHOOSE_PLAYER):

- p3:SOCChoosePlayerRequest:game=test|choices=[true, false, true, false]
- f3:SOCChoosePlayer:game=test|choice=2

### Choose whether to steal cloth or a resource (Cloth Trade scenario)

In gameState 55 (WAITING_FOR_ROB_CLOTH_OR_RESOURCE):

- p3:SOCChoosePlayer:game=test|choice=2  // 2 = victim pn, as a prompt and reminder
- f3:SOCChoosePlayer:game=test|choice=-3  // negative pn -> rob cloth, not resource

### Rob a player of a resource

Occurs after moving robber or pirate, then possibly choosing a victim,
and (Cloth Trade scenario) choosing whether to rob cloth or resources.

- p3:SOCReportRobbery:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
- p2:SOCReportRobbery:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
- !p[3, 2]:SOCReportRobbery:game=test|perp=3|victim=2|resType=6|amount=1|isGainLose=true
- all:SOCGameState:game=test|state=20  // or 15 if hasn't rolled yet

### Rob a player of cloth

- all:SOCReportRobbery:game=test|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=4|isGainLose=false|victimAmount=3  // rob 1 cloth; gives new total amounts for perpetrator and victim
- all:SOCGameState:game=test|state=20  // or 15

## Trade with bank and players

### Bank trade

- f3:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0
- all:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|pn=3

### Undo bank trade

Give 1 resource, get 2 or 3 or 4; otherwise same as usual bank trade sequence.

- f3:SOCBankTrade:game=test|give=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCBankTrade:game=test|give=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|pn=3

### Player trade: Make offer or counteroffer

- f2:SOCMakeOffer:game=test|offer=game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCMakeOffer:game=test|offer=game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCClearTradeMsg:game=test|playerNumber=-1

### Player trade: Reject

- f3:SOCRejectOffer:game=test|playerNumber=0
- all:SOCRejectOffer:game=test|playerNumber=3

### Player trade: Accept

- f2:SOCAcceptOffer:game=test|accepting=0|offering=3
- all:SOCAcceptOffer:game=test|accepting=2|offering=3|toAccepting=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|toOffering=clay=0|ore=0|sheep=0|wheat=0|wood=1|unknown=0
- all:SOCClearOffer:game=test|playerNumber=-1

## Ask Special Building during another player's turn

- f3:SOCBuildRequest:game=test|pieceType=-1  // or a defined piece type
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=16|amount=1  // ASK_SPECIAL_BUILD

## End turn or Special Building, start of next player's turn or SBP

### First turn after initial placement

Special case: Since the last player to place is the first player to roll,
server doesn't send the usual SOCTurn sequence listed in "Next player's usual turn begins",
only SOCGameState and the roll prompt.

- all:SOCGameServerText:game=test|text=It's p3's turn to build a road or ship.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=d05
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=d05
- all:SOCGameState:game=test|state=15  // ROLL_OR_CARD
- all:SOCRollDicePrompt:game=test|playerNumber=3

For scenarios where there's a 3rd initial settlement, like `SC_CLVI` Cloth Trade, the
last initial placement is followed by the usual "Next player's usual turn begins" sequence
because the current player changes.

### End usual turn

- f3:SOCEndTurn:game=test
- all:SOCClearOffer:game=test|playerNumber=-1

### End special building "turn"

- f3:SOCEndTurn:game=test
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=16|amount=0  // ASK_SPECIAL_BUILD
- all:SOCClearOffer:game=test|playerNumber=-1

### Next player's usual turn begins

- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=19|amount=0  // PLAYED_DEV_CARD_FLAG
- all:SOCTurn:game=test|playerNumber=2|gameState=15
- all:SOCRollDicePrompt:game=test|playerNumber=2

### Next player's SBP begins

- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=19|amount=0
- all:SOCTurn:game=test|playerNumber=2|gameState=100
- all:SOCGameServerText:game=test|text=Special building phase: p2's turn to place.

## Game over

- all:SOCGameElements:game=test|e4=3  // CURRENT_PLAYER
- all:SOCGameState:game=test|state=1000
- all:SOCGameServerText:game=test|text=>>> p3 has won the game with 10 points.
- all:SOCDevCardAction:game=test|playerNum=2|actionType=ADD_OLD|cardType=6
- all:SOCDevCardAction:game=test|playerNum=3|actionType=ADD_OLD|cardTypes=[5, 4]
- all:SOCGameStats:game=test|0|0|3|10|false|false|false|false
- all:SOCGameServerText:game=test|text=This game was 12 rounds, and took 11 minutes 29 seconds.
- p2:SOCPlayerStats:game=test|p=1|p=0|p=2|p=4|p=1|p=5  // sent to each still-connected player client; might be none if observing a robot-only game
- p3:SOCPlayerStats:game=test|p=1|p=2|p=6|p=0|p=5|p=1

