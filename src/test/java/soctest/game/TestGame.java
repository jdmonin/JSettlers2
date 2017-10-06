package soctest.game;

import org.junit.Test;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceSet;
import soc.message.SOCStartGame;
import soc.server.SOCServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestGame
{
    private static SOCGame newGame()
    {
        return new SOCGame("game");
    }

    @Test
    public void playerWithResources_canDiscard()
    {
        SOCGame game = newGame();
        SOCResourceSet aLotOfResources = new SOCResourceSet(3,3,3,3,3,0);
        SOCPlayer p1 = game.getPlayer(1);
        p1.getResources().add(aLotOfResources);
        SOCStartGame startGame = new SOCStartGame(game.getName());
//        SOCServer
//        assertEquals(p1.getPlayerNumber(), game.getCurrentPlayerNumber());
    }

}
