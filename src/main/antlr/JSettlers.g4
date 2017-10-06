/*

This grammar describes jsettlers and gives you a language to script a game.
This script can be used for a log, a specflow/
JSettlers has blocks and inline elements. Blocks are ended by a newline, like
the declaration of a turn or a set of turns:
``` jsettlers
turns
    turn 1
``` 
Inline elements are enclosing in brackets, like all possible 
resources [🌾🌲🐑⚌⛰] to choose from when a player produces gold.

Syntax is currently whitespace-sensitive. So it will break on irregular indents
and extra spaces at the end of line, double spaces between words, etc.

Emoji characters are always optional and normal ascii characters can be used instead.
Writing jsettlers can use unicode characters or use strategies (unicode,
terse ascii, full english/$language, ...). Both examples express the same:
e.g. p1 🔨🏠 6•7•12
e.g. player1 builds settlement at 6*7*12

Why use a dsl for jsettlers?
- fun!
- defines domain language tersely
- produces parsers for SpecFlow steps
- human-readable game log
- decoupling grammatical scriptModel and domain scriptModel allows for changes in
  grammatical scriptModel without affecting domain scriptModel
- each game played ever can serve as a test (annotated or not). For this to work
  we need output to log and a test runner.

- it seems to define messagedata fairly well, so translate to protobuf?
- replacement/supplement for SpecFlow?
- use as serialization format for e.g. a MapEditor?
- use as human readable format for board generator? (maybe include legend)

Why emoji? Because it looks cool! Besides that, it *is* cool to design a language
 with useful emoji.

Logo with settlement:
https://vectr.com/tmp/aSmmiFFlI/a12XAzu0Dr.svg?width=640&height=640&select=a12XAzu0Drpage0
// TODO: make subtle rounded corners?
<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" preserveAspectRatio="xMidYMid meet" viewBox="0 0 640 640" width="640" height="640"><defs><path d="M407.97 264.12L347.83 299.42L347.83 370.03L407.97 405.33L468.1 370.03L468.1 299.42L407.97 264.12Z" id="b1i3I9sF4f"></path><path d="" id="c5oYT9HjyT"></path><path d="" id="btLq6u6cP"></path><path d="M426.06 358.06L426.72 324.87L407.97 306.6L389.21 324.87L389.21 358.06L426.06 358.06Z" id="j2yOuoZnrL"></path></defs><g><g><use xlink:href="#b1i3I9sF4f" opacity="1" fill="#ffffff" fill-opacity="1"></use><g><use xlink:href="#b1i3I9sF4f" opacity="1" fill-opacity="0" stroke="#000000" stroke-width="10" stroke-opacity="1"></use></g></g><g><g><use xlink:href="#c5oYT9HjyT" opacity="1" fill-opacity="0" stroke="#000000" stroke-width="8" stroke-opacity="1"></use></g></g><g><g><use xlink:href="#btLq6u6cP" opacity="1" fill-opacity="0" stroke="#000000" stroke-width="8" stroke-opacity="1"></use></g></g><g><g><use xlink:href="#j2yOuoZnrL" opacity="1" fill-opacity="0" stroke="#000000" stroke-width="8" stroke-opacity="1"></use></g></g></g></svg>
it can be varianted, so we have a different icon in the hex per extension set.
 
ideas: 
🛡 -> 1f6e1, shield for C&K
⛵ -> ship, seafarers

Useful unicode characters:
→ ↑ ← ↓
'\u{a74f}' ꝏ
'\u{a562}'

Naming convention:
- U_XXX: unicode representation of XXX. e.g. U_WHEAT

What's better, generating java test classes or building a test runner?
`$name`: name of parse element

```
isOnTurn call -> ``` java
SOCPlayer player = game.getPlayer(`player.NUMBER`);
Assert.isTrue(player.isOnTurn) // or whatever the SOC impl may be
```

 */
grammar JSettlers;

@header {
package soc.syntax;
}

// Lexer
NUMBER: '-'? [0-9]+;
NL: '\r'? '\n'; // newline
INDENT: '    ' | '\t';
SPACE: ' ';

U_BUILD: '\u{1f528}'; // 🔨,
U_DICE: '\u{1f3b2}'; // 🎲
U_PORT: '\u{2693}'; // ⚓
U_CHECK: '\u{2714}'; // ✔
U_FLAT: '\u{2394}'; // ⎔
U_POINTY: '\u{2B21}'; // ⬡

in: 'in';
at: 'at' | '@'; // EN-specific location prefix

// A file or a string snippet expressing a full playable game script
script: (game placements turns) EOF?;

game: 'game' NL
    (gameOptions NL)?
    (board NL)?
    (players NL)+;

    gameOptions: INDENT 'options' NL
        (INDENT INDENT gameOption NL)*;

        gameOption: roads | ships | cities | settlements |
                    pirate | board | robber | placementSequence;
        roads: 'roads:' SPACE NUMBER;
        ships: 'ships:' SPACE NUMBER;
        cities: 'cities:' SPACE NUMBER;
        settlements: 'settlements:' SPACE NUMBER;
         // not specified? no robber
         // specified but no location? put somewhere random?
         // specified with location -> put at location
        robber: 'robber' (SPACE at)? (SPACE location)?;
        pirate: 'pirate' (SPACE at)? (SPACE location)?;
        placementSequence: 'standard' | 'standardWithCities' | 'seaFarers';
        //boardName: NAME;

    board: INDENT 'board' NL
        (INDENT INDENT boardOption NL)*;

        boardOption: layout2D | hexSetup | locationSetup | chitSetup |
                     portsSetup;

        // The syntax enables expressing a board in 2D text layout.
        // As such, we need to define the interpretation of the conversion
        // from hexagon to 2D table.
        // 2D Hexagonal board coordinate system
        // Assumes rows * columns tabular layout of hexes
        // pointy odd    aka "odd-r" horizontal layout
        // pointy even   aka "even-r" horizontal layout
        // flat odd      aka "odd-r" vertical layout
        // flat even     aka "even-r" vertical layout
        layout2D: 'system2D:' SPACE orientation SPACE oddEven;

        /* a location on a hex board
        https://www.redblobgames.com/grids/hexagons/
        */
        location: locationXyz | location2D | locationId;

            // 3-axis hexagonal system
            x: NUMBER; y: NUMBER; z: NUMBER;
            locationXyz: x ',' y ',' z; // example: 2,2,-4

            // 2D system
            row: NUMBER; column: NUMBER;
            location2D: row ',' column; // example: 3,4

            // numbered system: each hex gets fixed id
            // this is useful for the old board layout, but also
            // to more tersely reference a location. Referencing a vertex
            // becomes just 3 numbers, instead of 9 in case of locationXyx:
            locationId: NUMBER; // example: 26

            // TODO: think about a human-readable form of identifying hexes?
            // When we allow swapping of numbers these become time-bound.
            // e.g. "6 ore"
            // e.g. "5 wheat next to 2 clay"
            // e.g. "sea with brick port"

            orientation: pointy | flat;
            // pointy orientation means rows are aligned
            // Red Blob calls this "horizontal layout"
            pointy: U_POINTY | 'pointy'; // ⬡
            // flat orientation means columns are aligned
            // Red Blob calls this "vertical layout"
            flat: U_FLAT | 'flat'; // ⎔

            odd: 'odd';
            even: 'even';
            oddEven: odd | even;

            // one of the 6 sides of a hexagon
            edge: location '|' location;

            // intersection between 3 hexagons
            // e.g. 0•1•2 or 0,1•1,1•2,-3
            // e.g. 0*1*2 or 0,1*1,1*2,-3
            VERTEXDOT: '*' | '\u{2022}'; // •
            vertex: location VERTEXDOT location VERTEXDOT location;

        hexSetup: 'hexes' NL
            (INDENT INDENT INDENT hexRow NL)*;
            
            /* A mapping from 2D text-based tabular format to hex types and
               locations.
            Must see: https://www.redblobgames.com/grids/hexagons/
            Note: Edges and vertices can be visually inspected using
            below notation.
            E.g. either topleft S*S*F form a valid vertex.
            E.g. either topleft S|F pairs form a valid edge.

            pointy orientation ⬡              flat orientation ⎔
            oddRow         | evenRow          30° rotated versions of left
            . . S S S S    |  . S S S S       oddColumn | evenColumn
             . S F F F S   | . S F F F S      .         |  . S
            . S F O O O S  |  S F O O O S      . S      | . S S
             S D T T T T S | S D T T T T S    . S S     |  S F S
            . S R R R P S  |  S R R R P S      S F S    | S F O S
             . S P P P S   | . S P P P S      S F O S   |  F O T
            . . S S S S    |  . S S S S        F O T    | S O T S
                                              S O T S   |  F T P
                   P pasture                   F T P    | S T R S
                   S sea                      S T R S   |  D R P
                   F field                     D R P    | S R P S
                   T timber                   S R P S   |  S P S
                   D desert                    S P S    |   S S
                   R river                    . S S     |    S
                   O ore                         S      |
             */
            hexRow: evenHexRow | oddHexRow;
            evenHexRow: hex (SPACE hex)+;
            oddHexRow: (SPACE hex)+;

        locationSetup: 'locationIds' NL
                           (INDENT INDENT INDENT locationRow NL)*;
            /*
            Each location is padded to 4 characters. This allows an
            outlined table with up to 1000 hexes. One space after id required.

            Standard 1-based numbering    Standard SOCBoard
            ..  ..  1   2   3   4         ..  ..  17  39  5B  7D
              ..  5   6   7   8   9         ..  15  37  59  7B  9D
            ..  10  11  12  13  14  15    ..  13  35  57  79  9B  BD
              16  17  18  19  20  21  22    11  33  55  77  99  BB  DD
            ..  23  24  25  26  27  28    ..  31  53  75  97  B9  DB
              ..  29  30  31  32  33        ..  51  73  95  B7  D9
            ..  ..  34  35  36  37        ..  ..  71  93  B5  D7
            */
            locationRow: evenLocationRow | oddLocationRow;
            noLocation: '..';
            locationAssignment: NUMBER | noLocation; // no hex here
            evenLocationRow: SPACE SPACE (locationAssignment SPACE?
                             SPACE? SPACE?)+;
            oddLocationRow: locationAssignment (SPACE? SPACE? SPACE?
                            locationAssignment)+;

        chitSetup: 'chits' NL
                    (INDENT INDENT INDENT chitRow NL)*;
            chitRow: evenChitRow | oddChitRow;
            noChit: '..';
            chitAssignment: NUMBER | noChit;
            evenChitRow: SPACE SPACE (chitAssignment SPACE? SPACE? SPACE?)+;
            oddChitRow: chitAssignment (SPACE? SPACE? SPACE? chitAssignment)+;

        portsSetup: 'ports'NL
                    (INDENT INDENT INDENT portAtEdge NL)*;
            portAtEdge: port SPACE at SPACE edge; // e.g. ⚓3:1 at 1|6



    // Specify player info, handcards, stock, devCards for starting gamestate
    players: INDENT player NL
                     (INDENT INDENT setupPlayerOption NL)*;

        // For unit testing purposes, you want to refer to the different
        // instances of a player available during runtime of a test.
        // example: assume the client plays with p1
        //      v/ server.player2 has [wheat brick timber]
        //      p2 builds road at 0|1
        //      v/ server.player2 has [wheat]
        //      v/ client.player2 has [unknown]
        player: (server | client)? ('player' | 'p') NUMBER;
        // A player instance living in the game af the server
        server: ('server' | 's') '.'; // e.g. server.p1, s.p1, server.player1
        // A player instance living in the game af a client
        // TODO: do we want to test with multi client instances? e.g. c2.p1
        client: ('client' | 'c') '.'; // e.g. c.p1, client.player1
        // If no server or client specified, it is referred to in general
        // e.g. p1, player2, s.p1, server.player2, client.p1, c.player4

        setupPlayerOption: hand | stock | devCards | ports;
        hand: 'hand' SPACE resourceSet; // resourceSet provides brackets
        stock: 'stock' SPACE '[' (piece SPACE?)* ']';
        devCards: 'devCards' SPACE '[' (devCard SPACE?)* ']';
        ports: 'ports' SPACE '[' (port SPACE?)* ']';

placements: 'placement' NL
                ((INDENT buildAction NL)
                 (INDENT check NL)*)*; //checks are optional after action

    // TODO: specify this more towards vertex & edge (e.g. edgeBuildAction?)
    // TODO: gainResources, pick gold
    buildAction: (buildCity | buildShip | buildRoad | buildSettlement)
                 (SPACE 'and gains' SPACE resourceSet)?;

// Game phase where players take turns to do actions
turns: 'turns' NL
           (INDENT turn NL)+;

    turn: 'turn' SPACE NUMBER NL
              ((INDENT INDENT action NL)
               (INDENT INDENT check NL)*)*; //checks are optional after action

        // Equivalent to SOCMessage
        // Many elements have shorthand unicodes, but are stil able
        // to be expressed in English.
        // e.g. player1 🔨⛪ 0,1|0,2|1,1 vs
        //      player1 builds city at 0,1|0,2|1,1
        action: buildRoad | buildSettlement | buildShip | buildCity |
                endTurn | moveRobber | rollDice | discardResources | moveShip |
                acceptOffer;

            build: 'builds' | 'build' | U_BUILD; // careful: significant order
            endTurn: player SPACE 'ends turn';

            // e.g. player2 builds ship at -2,-2,4|3,3,-6
            // e.g. player1 builds settlement at -1,-2,-3|0,0,0|0,0,0
            // e.g. p1 🔨⛵ 0|1
            buildCity: player SPACE build SPACE? city SPACE (at SPACE)? vertex;
            buildShip: player SPACE build SPACE? ship SPACE (at SPACE)? edge;
            buildRoad: player SPACE build SPACE? road SPACE (at SPACE)? edge;
            buildSettlement: player SPACE build SPACE? settlement
                             SPACE (at SPACE)? vertex;
            moveRobber: player SPACE 'moves' SPACE robber SPACE 'to'
                        SPACE location;
            rollDice: player SPACE roll SPACE production;
                // DICENUMBER: '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' |
                // '10' | '11' | '12'; // TODO 2-12
                dice: U_DICE | ('rolls' SPACE); // e.g. 🎲 or 'rolls'
                roll: dice NUMBER; // e.g. 🎲8 or 🎲12
                // e.g. player1 🎲8 [player1 [🌾⚌⛰], player2 [🌾🐑]]
                playerProduction: player SPACE resourceSet;
                production: '[' (playerProduction (',' playerProduction)*)? ']';
            discardResources: player SPACE 'discards' SPACE resourceSet;
            moveShip: player SPACE 'moves ship from' SPACE edge SPACE 'to'
                      SPACE edge;
            acceptOffer: player SPACE 'accepts offer from' SPACE player;

        // Equivalent to a unit test assertion
        // Possibly name-parity can be reached on IValidator<T>
        // implementations, such that unit test assertion code can
        // be generated from a check grammar rule. For example:
        //
        // isOnTurn call -> ``` java
        // SOCPlayer player = game.getPlayer(`player.NUMBER`);
        // `$name.java` iot = new `$name.java`(); // new IsOnTurnValidator()
        // Assert.isTrue(iot.isValid(player));
        // ```
        //
        // would be a template to generate a snippet of java, used as
        // part of a generated java unit test class.
        // After all, these checks can (and should be?) reused as
        // validation of incoming messages as well.
        check: (U_CHECK | 'v/' | 'check') SPACE (playerHasResources |
                hasXRoadsInStock | isOnTurn | isNotOnTurn | hasRoadAt |
                hasXRoads | hasStockRoadAmount | hasSettlementAt);
            playerHasResources: player SPACE 'has' SPACE resourceSet;
            hasXRoadsInStock: player SPACE 'has' SPACE NUMBER
                              SPACE 'roads in stock';
            isNotOnTurn: player SPACE 'is not on turn';
            isOnTurn: player SPACE 'is on turn';
            hasRoadAt: player SPACE 'has road at' SPACE edge;
            hasSettlementAt: player SPACE 'has settlement' (SPACE at)?
                             SPACE vertex;
            hasXRoads: player 'hasXRoads:' NUMBER 'roads';
            hasStockRoadAmount: player SPACE 'has' SPACE NUMBER SPACE 'roads';
            // etc etc etc

// It would be nice if every resource has its own single-ascii-character
// shorthand. However, JSettlers has ambiguous WOOD and WHEAT. Proposal is to
// rename to TIMBER to remove ambiguity. While heavy change, I think it's worth
// the while because it makes expressing a ResourceSet nice and terse.
resourceSet : '[' SPACE* (resource SPACE?)* SPACE* ']';
resource : sheep | wheat | timber | ore | brick | unknown;
    sheep : '\u{1f411}' | 'sheep' | 's'; // 🐑
    timber: '\u{1f332}' | 'timber' | 't'; // 🌲
    wheat: '\u{1f33e}' | 'wheat' | 'w'; // 🌾
    ore: '\u{26f0}' | 'ore' | 'o'; // ⛰ // TODO: better one?
    brick: '\u{268c}' | 'brick' | 'b'; // ⚌ // TODO: better one?
    unknown: '?' | 'unknown';

piece: city | settlement | ship | road;
    settlement: '\u{1f3e0}' | 'settlement'; // 🏠
    city: '\u{26ea}' | 'city'; // ⛪
    ship: '\u{26f5}' | 'ship'; // ⛵
    road: '\u{1f6e3}' | 'road'; // 🛣

devCard: soldier | monopoly | roadBuilding | victoryPoint | yearOfPlenty;
    soldier: 's' | 'soldier';
    monopoly: 'm' | 'monopoly';
    roadBuilding: 'rb' | 'roadBuilding';
    victoryPoint: 'vp' | 'victoryPoint';
    yearOfPlenty: 'yop' | 'yearOfPlenty';

port: port31 | port41 | brickPort | wheatPort | timberPort | orePort |
      sheepPort;
    portPrefix: U_PORT | 'port';
    port31: 'port31' | portPrefix '3:1'; // ⚓3:1 or port4:1
    port41: 'port41' | portPrefix '4:1'; // ⚓4:1 or port3:1
    brickPort: 'brickPort' | portPrefix brick; // ⚓⚌
    wheatPort: 'wheatPort' | portPrefix wheat; // ⚓🌾
    timberPort: 'timberPort' | portPrefix timber; // ⚓🌲
    orePort: 'orePort' | portPrefix ore; // ⚓⛰
    sheepPort: 'sheepPort' | portPrefix sheep; // ⚓🐑

hex: pasture | forest | mountain | river | field | sea | none | desert;
    pasture: 'pasture' | 'P'; // produces sheep
    forest: 'timber' | 'T'; //produces timber
    mountain: 'mountain' | 'M'; // produces ore
    river: 'river' | 'R'; // produces brick
    field: 'field' | 'F'; // produces wheat
    sea: 'sea' | 'S';
    none: 'none' | '.';
    desert: 'desert' | 'D';

/*

Embedded MarkDown syntax extensions. Inline is `done using backticks` and
multiline is

```
done using 3 backticks for start and
```

end.

Right after the backticks, a language with optional element can be specified:
`js:ports [⚓4:1 ⚓3:1 ⚓🌲]` or multiline

``` js:hexes
hexes
            . . S S S S
             . S F D F S
            . S M R P F S
             S M R P F P S
            . S M F P M S
             . S F D F S
            . . S S S S
```

Specifying the language enables the MarkDown parser to:
- find an appropriate parser
- parse the contents
- generate html from contents
provided somehow the parsers and generators are registered.

`js:resourceSet[w t b o s]`:
ResourceSet
    Wheat
    Timber
    Brick
    Ore
    Sheep

``` jsettlers:gameOptions
    options
        roads: 15
        cities: 4
        settlements: 5
        robber at 17
```











*/