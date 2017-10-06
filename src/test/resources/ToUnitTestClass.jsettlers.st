/* comment it out since this does not yet produce compilable code
package syntax;

import org.junit.Test;

public class <scriptModel.name>Test
    SOCGame game;
    SOCServer server;

<[script.game.players:{p|
    SOCPlayer player<i>;
}]>
<[script.game.players:{p|
    SOCPlayer serverPlayer<i>;
}]>
<[script.game.players:{p|
    SOCPlayer clientPlayer<i>;
}]>
    public void setupGame() {
        game  = new SOCGame();
        server = new SOCServer(game);
    }

<[script.turns.turn:{turn|
    @Test
    public void test<turn><turn.NUMBER>() {
<[turn.action:{a|
        <a.buildRoad> <a.buildRoad> = new <a.buildRoad>();
}]>
    \}
}]>
}

*/