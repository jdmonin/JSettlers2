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
it sends, even when it actually sent different messages to an older client to be compatible.

If you're curious about older versions, see the code and comments in message classes like `SOCPutPiece`
and server classes like `SOCGameMessageHandler` and `SOCGameHandler` which communicate with clients.

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

The format used here is from `soc.extra.server.GameEventLog.EventEntry.toString()`.
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
- all:SOCGameState:game=test|state=50  // WAITING_FOR_DISCARDS
- all:SOCGameServerText:game=test|text=p2 needs to discard.
- p2:SOCDiscardRequest:game=test|numDiscards=4

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=7
- all:SOCGameState:game=test|state=33  // PLACING_ROBBER
- all:SOCGameServerText:game=test|text=p3 will move the robber.

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=7
- all:SOCGameState:game=test|state=54  // WAITING_FOR_ROBBER_OR_PIRATE
- all:SOCGameServerText:game=test|text=p3 must choose to move the robber or the pirate.

### Roll other than 7

Some examples:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=9
- all:SOCDiceResultResources:game=test|p=2|p=2|p=4|p=1|p=4|p=0|p=3|p=7|p=1|p=4
- p2:SOCPlayerElements:game=test|playerNum=2|actionType=SET|e1=2,e2=0,e3=0,e4=1,e5=1
- p3:SOCPlayerElements:game=test|playerNum=3|actionType=SET|e1=0,e2=2,e3=1,e4=3,e5=1
- all:SOCGameState:game=test|state=20  // PLAY1

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
- all:SOCGameState:game=test|state=56  // WAITING_FOR_PICK_GOLD_RESOURCE

Or:

- f3:SOCRollDice:game=test
- all:SOCDiceResult:game=test|param=8
- all:SOCDiceResultResources:game=test|p=2|p=2|p=8|p=1|p=2|p=2|p=4|p=0|p=3|p=4|p=2|p=2
- p2:SOCPlayerElements:game=test|playerNum=2|actionType=SET|e1=0,e2=3,e3=2,e4=3,e5=0
- p3:SOCPlayerElements:game=test|playerNum=3|actionType=SET|e1=0,e2=3,e3=0,e4=0,e5=1
- all:SOCPieceValue:game=test|pieceType=5|coord=809|pv1=1|pv2=0
- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=106|amount=5  // SCENARIO_CLOTH_COUNT
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=106|amount=7
- all:SOCGameServerText:game=test|text=robot 5 and p3 each received cloth from the villages.
- all:SOCGameState:game=test|state=20

## Buy and build piece

Client requests build by sending SOCPutPiece or SOCBuildRequest.
If the request isn't possible, server replies with SOCDeclinePlayerRequest
instead of the sequences shown here.

### Initial Placement

The placement message sequence is different during initial placement. Server assumes client knows the rules,
and pieces have no cost, so it may send SOCTurn instead of SOCGameState and won't send cost SOCPlayerElements.

#### Basic example with player 2's first initial road, second initial settlement:

- f2:SOCPutPiece:game=test|playerNumber=2|pieceType=0|coord=907
- all:SOCGameServerText:game=test|text=p2 built a road.
- all:SOCPutPiece:game=test|playerNumber=2|pieceType=0|coord=907
- all:SOCGameServerText:game=test|text=It's p2's turn to build a settlement.
- all:SOCTurn:game=test|playerNumber=2|gameState=10  // START2A

(p2 decides on a location to build)

- f2:SOCPutPiece:game=test|playerNumber=2|pieceType=1|coord=809
- all:SOCGameServerText:game=test|text=p2 built a settlement.
- all:SOCPutPiece:game=test|playerNumber=2|pieceType=1|coord=809
- all:SOCGameState:game=test|state=11  // START2B
- all:SOCGameServerText:game=test|text=It's p2's turn to build a road or ship.

#### Cancel and re-place initial settlement location

- all:SOCGameServerText:game=test|text=It's p3's turn to build a settlement.
- all:SOCTurn:game=test|playerNumber=3|gameState=10
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=58
- all:SOCGameServerText:game=test|text=p3 built a settlement.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=58
- all:SOCGameState:game=test|state=11
- all:SOCGameServerText:game=test|text=It's p3's turn to build a road.
- f3:SOCCancelBuildRequest:game=test|pieceType=1
- all:SOCCancelBuildRequest:game=test|pieceType=1
- all:SOCGameServerText:game=test|text=p3 cancelled this settlement placement.
- all:SOCGameState:game=test|state=10
- all:SOCGameServerText:game=test|text=It's p3's turn to build a settlement.


#### Building ship (or settlement) reveals non-gold hex from fog

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=a06
- all:SOCRevealFogHex:game=test|hexCoord=908|hexType=3|diceNum=4  // can be multiple if settlement
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=a06
- all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=3|amount=1|news=Y
- all:SOCGameServerText:game=test|text=p3 gets 1 sheep by revealing the fog hex.
- That SOCPlayerElement and text will repeat if multiple hexes were revealed by placing a settlement.
- all:SOCGameServerText:game=test|text=It's p2's turn to build a settlement.
- all:SOCTurn:game=test|playerNumber=2|gameState=10  // START2A

#### Building ship (or settlement) reveals gold hex from fog

- all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=805
- all:SOCGameState:game=test|state=6  // START1B
- all:SOCGameServerText:game=test|text=It's p3's turn to build a road or ship.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=805
- all:SOCRevealFogHex:game=test|hexCoord=707|hexType=7|diceNum=12
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=805
- all:SOCGameState:game=test|state=14  // STARTS_WAITING_FOR_PICK_GOLD_RESOURCE
- all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
- p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

(p3 picks a free resource from the revealed gold hex)

- f3:SOCPickResources:game=test|resources=clay=0|ore=0|sheep=0|wheat=0|wood=1|unknown=0
- all:SOCPickResources:game=test|resources=clay=0|ore=0|sheep=0|wheat=0|wood=1|unknown=0|pn=3|reason=3
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=0
- all:SOCGameState:game=test|state=10  // START2A
- all:SOCGameServerText:game=test|text=It's p3's turn to build a settlement.


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
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)
    - Or if won game:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)

Or if client starts with build request:

- f3:SOCBuildRequest:game=test|pieceType=1
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e3=1,e4=1,e5=1
- all:SOCGameState:game=test|state=31  // PLACING_SETTLEMENT
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=67
- all:SOCGameServerText:game=test|text=p3 built a settlement.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=1|coord=67
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)

### City

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=a08
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=3,e4=2
- all:SOCGameServerText:game=test|text=p3 built a city.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=a08
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)
    - Or if won game:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)

Or if client starts with build request:

- f3:SOCBuildRequest:game=test|pieceType=2
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=3,e4=2
- all:SOCGameState:game=test|state=32
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=67
- all:SOCGameServerText:game=test|text=p3 built a city.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=2|coord=67
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)

### Road (may set Longest Route)

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=809
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e5=1
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=908|hexType=4|diceNum=12
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=809
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=4|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 wheat by revealing the fog hex.
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING) or (gold fog hex revealed) 56 (WAITING_FOR_PICK_GOLD_RESOURCE)
    - Or if won game with that Longest Route:
    - all:SOCGameElements:game=test|e4=3
    - all:SOCGameState:game=test|state=1000  // OVER
- If revealing a fog hex as gold, and didn't just win game:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

Or if client sends build request:

- f3:SOCBuildRequest:game=test|pieceType=0
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e1=1,e5=1
- all:SOCGameState:game=test|state=30
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=56
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=508|hexType=5|diceNum=10
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=56
- If Longest Route player changes: all:SOCGameElements:game=test|e6=3
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=5|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 wood by revealing the fog hex.
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)
- If revealing a fog hex as gold:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

### Ship (may set Longest Route)

- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=80a
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e3=1,e5=1
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=709|hexType=7|diceNum=9
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=80a
- If gaining Longest Route: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- If revealing a fog hex as non-gold: SOCPlayerElement and SOCGameServerText (see Road above for details)
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING) or (gold fog hex revealed) 56 (WAITING_FOR_PICK_GOLD_RESOURCE)
    - Or if won game with that Longest Route:
    - all:SOCGameElements:game=test|e4=3
    - all:SOCGameState:game=test|state=1000  // OVER
- If revealing a fog hex as gold, and didn't just win game:
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0

Or if client sends build request:

- f3:SOCBuildRequest:game=test|pieceType=3
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e3=1,e5=1
- all:SOCGameState:game=test|state=35
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=602
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=603|hexType=1|diceNum=5
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

- f3:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=c06|toCoord=f06
- If revealing a fog hex: all:SOCRevealFogHex:game=test|hexCoord=d0e|hexType=7|diceNum=6
- all:SOCMovePiece:game=test|pn=3|pieceType=3|fromCoord=c06|toCoord=f06
- If gaining Longest Route: all:SOCGameElements:game=test|e6=3  // LONGEST_ROAD_PLAYER
- If won game with that Longest Route:
    - all:SOCGameElements:game=test|e4=3
    - all:SOCGameState:game=test|state=1000
- If revealing a fog hex as gold, and didn't just win game:
    - all:SOCGameState:game=test|state=56
    - all:SOCGameServerText:game=test|text=p3 needs to pick resources from the gold hex.
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=1
    - p3:SOCSimpleRequest:game=test|pn=3|reqType=1|v1=1|v2=0
- If revealing a fog hex as non-gold:
    - all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=2|amount=1|news=Y
    - all:SOCGameServerText:game=test|text=p3 gets 1 ore by revealing the fog hex.

## Buy dev card

- f3:SOCBuyDevCardRequest:game=test
- all:SOCPlayerElements:game=test|playerNum=3|actionType=LOSE|e2=1,e3=1,e4=1
- p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=5 // type varies
- !p3:SOCDevCardAction:game=test|playerNum=3|actionType=DRAW|cardType=0
- all:SOCSimpleAction:game=test|pn=3|actType=1|v1=22|v2=0  // DEVCARD_BOUGHT; v1 = remaining amount of unbought cards
- all:SOCGameState:game=test|state=20  // or 100 (SPECIAL_BUILDING)

Or if player can't buy now: Server responds to client's SOCBuyDevCardRequest with SOCDeclinePlayerRequest
and if client is robot, also SOCCancelBuildRequest(SOCPossiblePiece.CARD).

## Use/Play each dev card type

If player can't play the requested card at this time, server responds to client's SOCPlayDevCardRequest:

- To bots:
    - un:SOCDevCardAction:game=test|playerNum=-1|actionType=CANNOT_PLAY|cardType=9
- To human clients, sometimes with a specific reason:
    - un:SOCDeclinePlayerRequest:game=test|reason=2|detail1=0|detail2=0|text=You can't play a Road Building card now.  
      // reason code varies, text is optional

### Road Building

- f3:SOCPlayDevCardRequest:game=test|devCard=1
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=1
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1  // PLAYED_DEV_CARD_FLAG
- all:SOCGameServerText:game=test|text=p3 played a Road Building card.
- If player has only 1 remaining road/ship, skips this section:
- all:SOCGameState:game=test|state=40  // PLACING_FREE_ROAD1
- p3:SOCGameServerText:game=test|text=You may place 2 roads/ships.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704  // or pieceType=3 for ship for any/all SOCPutPiece in this sequence
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=704
- If gains Longest Route after 1st placement: all:SOCGameElements:game=test|e6=3
- If player has only 1 remaining, skips to gamestate(41) and omits the above section
- all:SOCGameState:game=test|state=41  // PLACING_FREE_ROAD2
    - Or if won game by gaining Longest Route:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)
- If player has 1 remaining: p3:SOCGameServerText:game=test|text=You may place your 1 remaining road.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
- all:SOCGameServerText:game=test|text=p3 built a road.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=0|coord=804
- If gains Longest Route after 2nd placement: all:SOCGameElements:game=test|e6=3
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD) if played before dice roll
    - Or if won game by gaining Longest Route:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)
- If played before dice roll:
- all:SOCRollDicePrompt:game=test|playerNumber=3

#### Cancelling free road building

This card can be cancelled before placing the first or second free road or ship.
If cancelled before placing first one, dev card is returned to player's inventory
and their PLAYED_DEV_CARD_FLAG flag is cleared so they can still play a card.

If cancelled by clicking Cancel, turn continues with state PLAY1:

- f3:SOCPlayDevCardRequest:game=g|devCard=1
- all:SOCDevCardAction:game=g|playerNum=3|actionType=PLAY|cardType=1
- all:SOCPlayerElement:game=g|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCGameServerText:game=g|text=p3 played a Road Building card.
- all:SOCGameState:game=g|state=40
- p3:SOCGameServerText:game=g|text=You may place 2 roads.
- f3:SOCPutPiece:game=g|playerNumber=3|pieceType=0|coord=66
- all:SOCGameServerText:game=g|text=p3 built a road.
- all:SOCPutPiece:game=g|playerNumber=3|pieceType=0|coord=66
- all:SOCGameState:game=g|state=41
- f3:SOCCancelBuildRequest:game=g|pieceType=0
- all:SOCGameServerText:game=g|text=p3 skipped placing the second road.
- all:SOCGameState:game=g|state=20  // PLAY1

If cancelled by clicking End Turn, goes directly from placement gameState to next player's turn:

- f3:SOCPlayDevCardRequest:game=g|devCard=1
- all:SOCDevCardAction:game=g|playerNum=3|actionType=PLAY|cardType=1
- all:SOCPlayerElement:game=g|playerNum=3|actionType=SET|elementType=19|amount=1  // PLAYED_DEV_CARD_FLAG
- all:SOCGameServerText:game=g|text=p3 played a Road Building card.
- all:SOCGameState:game=g|state=40
- p3:SOCGameServerText:game=g|text=You may place 2 roads.
- f3:SOCEndTurn:game=g
- all:SOCGameServerText:game=g|text=p3 cancelled the Road Building card.
- all:SOCDevCardAction:game=test|playerNum=3|actionType=ADD_OLD|cardType=1  // only if hasn't placed 1st road/ship yet
- all:SOCPlayerElement:game=g|playerNum=3|actionType=SET|elementType=19|amount=0  // only if hasn't placed 1st one yet
- all:SOCClearOffer:game=g|playerNumber=-1
- all:SOCTurn:game=g|playerNumber=2|gameState=15

### Year of Plenty/Discovery (see also "Gold hex gains" sequence)

- f3:SOCPlayDevCardRequest:game=test|devCard=2
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=2
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1  // PLAYED_DEV_CARD_FLAG
- all:SOCGameServerText:game=test|text=p3 played a Year of Plenty card.
- all:SOCGameState:game=test|state=52  // WAITING_FOR_DISCOVERY
- f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0
- all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=1|wood=0|unknown=0|pn=3|reason=2
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)
- If played before dice roll:
- all:SOCRollDicePrompt:game=test|playerNumber=3

### Monopoly

- f3:SOCPlayDevCardRequest:game=test|devCard=3
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=3
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCGameServerText:game=test|text=p3 played a Monopoly card.
- all:SOCGameState:game=test|state=53  // WAITING_FOR_MONOPOLY
- f3:SOCPickResourceType:game=test|resType=3
- Announce resources taken from the victim players, if any:
- all:SOCPlayerElement:game=test|playerNum=1|actionType=SET|elementType=3|amount=0|news=Y
- all:SOCResourceCount:game=test|playerNum=1|count=7
- all:SOCPlayerElement:game=test|playerNum=2|actionType=SET|elementType=3|amount=0|news=Y
- all:SOCResourceCount:game=test|playerNum=2|count=2
- And taken by the current player:
- all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=3|amount=6  // or amount=0 if none gained
- all:SOCSimpleAction:game=test|pn=3|actType=3|v1=6|v2=3  // RSRC_TYPE_MONOPOLIZED
- p1:SOCGameServerText:game=test|text=p3's Monopoly took your 5 sheep.
- p2:SOCGameServerText:game=test|text=p3's Monopoly took your 1 sheep.
- all:SOCGameState:game=test|state=20  // or 15 (ROLL_OR_CARD)
- If played before dice roll:
- all:SOCRollDicePrompt:game=test|playerNumber=3

### Soldier (may set Largest Army)

- f3:SOCPlayDevCardRequest:game=test|devCard=9
- all:SOCGameServerText:game=test|text=p3 played a Soldier card.
- all:SOCDevCardAction:game=test|playerNum=3|actionType=PLAY|cardType=9
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=19|amount=1
- all:SOCPlayerElement:game=test|playerNum=3|actionType=GAIN|elementType=15|amount=1  // NUMKNIGHTS
- If Largest Army player changing: all:SOCGameElements:game=test|e5=3  // LARGEST_ARMY_PLAYER
    - If player wins game by gaining Largest Army, that will be announced after moving/robbing.
- all:SOCGameState:game=test|state=33  // PLACING_ROBBER, or 34 (PLACING_PIRATE), 54 (WAITING_FOR_ROBBER_OR_PIRATE), or other states

## Actions which happen because of other game actions

### Discard

In gameState WAITING_FOR_DISCARDS:

If this is the only player who needed to discard:

- f2:SOCDiscard:game=test|resources=clay=0|ore=0|sheep=2|wheat=0|wood=3|unknown=0
- p2:SOCDiscard:game=test|playerNum=2|resources=clay=0|ore=0|sheep=2|wheat=0|wood=3|unknown=0  // to `all:` if game is Fully Observable
- !p2:SOCDiscard:game=test|playerNum=2|resources=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=5  // not sent if Fully Observable
- all:SOCGameState:game=test|state=33  // or other: choose robber or pirate, etc
- all:SOCGameServerText:game=test|text=p2 will move the robber.  // optional, varies based on new game state

Or if still waiting for other players to discard:

- f3:SOCDiscard:game=test|resources=clay=0|ore=0|sheep=7|wheat=0|wood=0|unknown=0
- p3:SOCDiscard:game=test|playerNum=3|resources=clay=0|ore=0|sheep=7|wheat=0|wood=0|unknown=0  // to `all:` if game is Fully Observable
- !p3:SOCDiscard:game=test|playerNum=3|resources=clay=0|ore=0|sheep=0|wheat=0|wood=0|unknown=7  // not sent if Fully Observable
- all:SOCGameState:game=test|state=50  // still WAITING_FOR_DISCARDS
- all:SOCGameServerText:game=test|text=p2 needs to discard.

### Choose free resources (Gold hex gains; see also "Year of Plenty/Discovery" sequence)

- f3:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCPickResources:game=test|resources=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0|pn=3|reason=3
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=101|amount=0  // NUM_PICK_GOLD_HEX_RESOURCES
- all:SOCGameState:game=test|state=20  // or another state, like 56 if another player must also choose
- Or during initial placement, instead of all:SOCGameState, can be all:SOCTurn which begins next sequence

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
- If no possible victims, or if any choices to be made:
    - all:SOCGameState:game=test|state=20  // or choose-player, choose-resource-or-cloth, etc
- Else, if there's no possible victim to rob from, and player just won by gaining largest army:
    - all:SOCGameElements:game=test|e4=3  // CURRENT_PLAYER
    - all:SOCGameState:game=test|state=1000
- Else:
    - Next message is SOCRobberyResult from server, which will be start of next sequence

### Move pirate

In gameState 34 (PLACING_PIRATE):

- f3:SOCMoveRobber:game=test|playerNumber=3|coord=-90c
- all:SOCMoveRobber:game=test|playerNumber=3|coord=-90c
- all:SOCGameServerText:game=test|text=p3 moved the pirate.
- all:SOCGameState:game=test|state=20  // or another state or "game over" message pair, same as Move robber

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

- p3:SOCRobberyResult:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
- p2:SOCRobberyResult:game=test|perp=3|victim=2|resType=5|amount=1|isGainLose=true
- !p[3, 2]:SOCRobberyResult:game=test|perp=3|victim=2|resType=6|amount=1|isGainLose=true
- all:SOCGameState:game=test|state=20  // or 15 + all:SOCRollDicePrompt if hasn't rolled yet
    - Or if player won game by gaining Largest Army with this soldier:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)

### Rob a player of cloth

- all:SOCRobberyResult:game=test|perp=3|victim=2|peType=SCENARIO_CLOTH_COUNT|amount=4|isGainLose=false|victimAmount=3  // rob 1 cloth; gives new total amounts for perpetrator and victim
- all:SOCGameState:game=test|state=20  // or 15
    - Or if player won game by gaining VP from the robbed cloth:  
      all:SOCGameElements:game=test|e4=3 , all:SOCGameState 1000 (OVER)


## Trade with bank and players

### Bank trade

- f3:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0
- all:SOCBankTrade:game=test|give=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=0|sheep=1|wheat=0|wood=0|unknown=0|pn=3

### Undo bank trade

Give 1 resource, get 2 or 3 or 4; otherwise same as usual bank trade sequence.

- f3:SOCBankTrade:game=test|give=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCBankTrade:game=test|give=clay=1|ore=0|sheep=0|wheat=0|wood=0|unknown=0|get=clay=0|ore=3|sheep=0|wheat=0|wood=0|unknown=0|pn=3

### Player trade: Make offer or counteroffer

- f2:SOCMakeOffer:game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCMakeOffer:game=test|from=2|to=false,false,false,true|give=clay=0|ore=0|sheep=0|wheat=1|wood=0|unknown=0|get=clay=0|ore=1|sheep=0|wheat=0|wood=0|unknown=0
- all:SOCClearTradeMsg:game=test|playerNumber=-1

### Player trade: Clear/cancel own offer

- f3:SOCClearOffer:game=test|playerNumber=0
- all:SOCClearOffer:game=test|playerNumber=3
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

### Start of first turn after initial placement

The last player to place is the first player to roll, unless there are 3 initial placements (Cloth Trade scenario, etc).
Even though the current player isn't changing, server still sends the usual SOCTurn sequence
listed in "Next player's usual turn begins":

- all:SOCGameServerText:game=test|text=It's p3's turn to build a road or ship.
- f3:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=d05
- all:SOCGameServerText:game=test|text=p3 built a ship.
- all:SOCPutPiece:game=test|playerNumber=3|pieceType=3|coord=d05
- all:SOCTurn:game=test|playerNumber=3|gameState=15  // ROLL_OR_CARD
- all:SOCRollDicePrompt:game=test|playerNumber=3

### End usual turn

In gameState PLAY1, PLACING_FREE_ROAD1, or PLACING_FREE_ROAD2:

- f3:SOCEndTurn:game=test
- all:SOCClearOffer:game=test|playerNumber=-1

### End special building "turn"

In gameState SPECIAL_BUILDING:

- f3:SOCEndTurn:game=test
- all:SOCPlayerElement:game=test|playerNum=3|actionType=SET|elementType=16|amount=0  // ASK_SPECIAL_BUILD
- all:SOCClearOffer:game=test|playerNumber=-1

### Next player's usual turn begins

- all:SOCTurn:game=test|playerNumber=2|gameState=15  // ROLL_OR_CARD, or 1000 (OVER) if they just won
- If new turn's gameState not OVER: all:SOCRollDicePrompt:game=test|playerNumber=2

### Next player's SBP begins

- all:SOCTurn:game=test|playerNumber=2|gameState=100  // SPECIAL_BUILDING

## Game over

Preceding messages are:

- If winning points gained during the player's turn:
    - all:SOCGameElements:game=test|e4=3  // CURRENT_PLAYER
    - all:SOCGameState:game=test|state=1000  // OVER
- If gained during another player's turn, and winning at start of player's own turn:
    - all:SOCTurn:game=test|playerNumber=3|gameState=1000

Those are part of the previous sequence if it typically ends with a SOCGameState.

- all:SOCGameServerText:game=test|text=>>> p3 has won the game with 10 points.
- all:SOCDevCardAction:game=test|playerNum=2|actionType=ADD_OLD|cardType=6
- all:SOCDevCardAction:game=test|playerNum=3|actionType=ADD_OLD|cardTypes=[5, 4]
- all:SOCGameStats:game=test|0|0|3|10|false|false|false|false
- all:SOCGameServerText:game=test|text=This game was 12 rounds, and took 11 minutes 29 seconds.
- p2:SOCPlayerStats:game=test|p=1|p=0|p=2|p=4|p=1|p=5  // sent to each still-connected player client; might be none if observing a robot-only game
- p3:SOCPlayerStats:game=test|p=1|p=2|p=6|p=0|p=5|p=1

### Winning at Start of your Turn

This happens when the winning player gains enough VP to win during another player's turn.
Maybe they became longest-route player because another player broke a longer route,
or gained VP from cloth during another player's roll in the Cloth Trade scenario.

The previous player's "end turn" is followed by a "turn begins" sequence,
then the "game over" sequence:

- f2:SOCEndTurn:game=test
- all:SOCClearOffer:game=test|playerNumber=-1
- all:SOCTurn:game=test|playerNumber=3|gameState=1000
- all:SOCGameServerText:game=test|text=>>> p3 has won the game with 10 points.
- all:SOCDevCardAction:game=test|playerNum=3|actionType=ADD_OLD|cardType=4
- all:SOCGameStats:game=test|0|2|2|10|false|true|true|false
- p3:SOCPlayerStats:game=test|p=1|p=0|p=0|p=5|p=2|p=0

